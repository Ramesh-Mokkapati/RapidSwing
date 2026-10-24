package com.rapidswing.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.binding.Binding;
import com.rapidswing.binding.Bindings;
import com.rapidswing.binding.Converter;
import com.rapidswing.binding.Converters;
import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.ui.Formats;
import com.rapidswing.util.Edt;
import net.miginfocom.swing.MigLayout;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.JTextComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link Form} row by row: a label column and a field column, laid out with MigLayout.
 * Each field method returns a {@link FieldBuilder} for configuring that field (rules, tooltip, ...)
 * which can also add the next field, so a whole form is one chain:
 * <pre>
 * Form form = Form.builder()
 *         .section("Personal")
 *         .text("Name", person.name()).required().validate(Validators.maxLength(50))
 *         .integer("Age", person.age()).validate(Validators.range(18, 120))
 *         .date("Birthday", person.birthday()).validate(Validators.past())
 *         .checkbox("Subscribe to newsletter", person.newsletter())
 *         .build();
 * </pre>
 * Must be used on the EDT.
 */
public class FormBuilder {

    /** State shared by a form builder and all of its field builders. */
    static final class State {
        final JPanel panel = new JPanel(new MigLayout("insets 0, hidemode 3, fillx", "[right][grow,fill]", ""));
        final List<FormField<?>> fields = new ArrayList<>();
        final Map<String, JComponent> components = new LinkedHashMap<>();
        boolean hasRows;
        boolean built;
    }

    final State state;

    FormBuilder(State state) {
        this.state = state;
    }

    // ---- Text fields ---------------------------------------------------------------------------------

    /** A text field. Leading and trailing whitespace is trimmed. */
    public FieldBuilder<String> text(String label, Property<String> property) {
        return text(label, property, Converters.trimmed());
    }

    public <T> FieldBuilder<T> text(String label, Property<T> property, Converter<T> converter) {
        return textField(label, new JTextField(20), property, converter);
    }

    public FieldBuilder<Integer> integer(String label, Property<Integer> property) {
        return text(label, property, Converters.integer());
    }

    public FieldBuilder<BigDecimal> decimal(String label, Property<BigDecimal> property) {
        return text(label, property, Converters.decimal());
    }

    /** An ISO date field ({@code yyyy-mm-dd}), with a translated format hint as placeholder. */
    public FieldBuilder<LocalDate> date(String label, Property<LocalDate> property) {
        FieldBuilder<LocalDate> field = text(label, property, Converters.localDate());
        return field.placeholder(I18n.global().get("form.datePlaceholder"));
    }

    /** A password field. The text is not trimmed. */
    public FieldBuilder<String> password(String label, Property<String> property) {
        return textField(label, new JPasswordField(20), property, Converters.string());
    }

    public FieldBuilder<String> textArea(String label, Property<String> property, int rows) {
        checkCanAdd(label);
        JTextArea area = new JTextArea(rows, 20);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(area);
        Binding binding = Bindings.text(area, property, Converters.string(), false);
        return addField(label, scroll, area, property, binding, "top");
    }

    // ---- Choice fields -------------------------------------------------------------------------------

    /** A check box. The label is shown as the check box text. */
    public FieldBuilder<Boolean> checkbox(String label, Property<Boolean> property) {
        checkCanAdd(label);
        JCheckBox box = new JCheckBox(label);
        Binding binding = Bindings.selected(box, property);
        FormField<Boolean> field = new FormField<>(label, null, box, box, property, binding);
        state.panel.add(box, "skip, wrap");
        return register(field);
    }

    @SafeVarargs
    public final <T> FieldBuilder<T> combo(String label, Property<T> property, T... items) {
        checkCanAdd(label);
        JComboBox<T> combo = new JComboBox<>(items);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                   boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                // Enums are translated and dates localized, as in grids; null shows as an empty line.
                setText(value == null ? " " : Formats.format(value));
                return this;
            }
        });
        Binding binding = Bindings.selectedItem(combo, property);
        return addField(label, combo, combo, property, binding, "");
    }

    public FieldBuilder<Integer> spinner(String label, Property<Integer> property, int min, int max) {
        checkCanAdd(label);
        int initial = property.get() != null ? Math.max(min, Math.min(max, property.get())) : min;
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(initial, min, max, 1));
        Binding binding = Bindings.value(spinner, property);
        return addField(label, spinner, spinner, property, binding, "");
    }

    // ---- Layout --------------------------------------------------------------------------------------

    /**
     * Any component, with a label but no binding or validation. An empty label puts the component under the
     * fields, e.g. for a row of buttons or a status line; such rows need not be unique.
     */
    public FormBuilder custom(String label, JComponent component) {
        if (label.isEmpty()) {
            checkNotBuilt();
            Edt.checkEdt();
            state.panel.add(component, "skip, wrap");
            state.hasRows = true;
            return this;
        }
        checkCanAdd(label);
        JLabel labelComponent = new JLabel(label);
        labelComponent.setLabelFor(component);
        state.panel.add(labelComponent);
        state.panel.add(component, "wrap");
        state.components.put(label, component);
        return this;
    }

    /** A bold heading with a line, starting a new group of fields. */
    public FormBuilder section(String title) {
        checkNotBuilt();
        JLabel heading = new JLabel(title);
        heading.putClientProperty(FlatClientProperties.STYLE_CLASS, "h4");
        // Both components of the split cell need the gap, or the line sits above the heading.
        String gap = state.hasRows ? ", gaptop para" : "";
        state.panel.add(heading, "span 2, split 2" + gap);
        state.panel.add(new JSeparator(), "growx, wrap" + gap);
        state.hasRows = true;
        return this;
    }

    /** A right-aligned row of buttons at the end of the form. */
    public FormBuilder buttons(Action... actions) {
        checkNotBuilt();
        for (int i = 0; i < actions.length; i++) {
            String constraints = i == 0 ? "span 2, split " + actions.length + ", align right, gaptop para" : "";
            if (i == actions.length - 1) {
                constraints += constraints.isEmpty() ? "wrap" : ", wrap";
            }
            state.panel.add(new JButton(actions[i]), constraints);
        }
        state.hasRows = true;
        return this;
    }

    public Form build() {
        checkNotBuilt();
        state.built = true;
        return new Form(state.panel, state.fields, state.components);
    }

    // ---- Helpers -------------------------------------------------------------------------------------

    private <T> FieldBuilder<T> textField(String label, JTextComponent field, Property<T> property,
                                          Converter<T> converter) {
        checkCanAdd(label);
        Binding binding = Bindings.text(field, property, converter, false);
        return addField(label, field, field, property, binding, "");
    }

    private <T> FieldBuilder<T> addField(String label, JComponent layoutComponent, JComponent focusComponent,
                                         Property<T> property, Binding binding, String labelConstraints) {
        JLabel labelComponent = new JLabel(label);
        labelComponent.setLabelFor(focusComponent);
        state.panel.add(labelComponent, labelConstraints);
        state.panel.add(layoutComponent, "wrap");
        return register(new FormField<>(label, labelComponent, layoutComponent, focusComponent, property, binding));
    }

    private <T> FieldBuilder<T> register(FormField<T> field) {
        state.panel.add(field.errorLabel, "skip, wrap, gaptop 0");
        state.fields.add(field);
        state.components.put(field.name, field.focusComponent);
        state.hasRows = true;
        return new FieldBuilder<>(state, field);
    }

    private void checkCanAdd(String label) {
        checkNotBuilt();
        Edt.checkEdt();
        if (state.components.containsKey(label)) {
            throw new IllegalArgumentException("The form already has a field labelled '" + label + "'");
        }
    }

    void checkNotBuilt() {
        if (state.built) {
            throw new IllegalStateException("The form has already been built");
        }
    }
}
