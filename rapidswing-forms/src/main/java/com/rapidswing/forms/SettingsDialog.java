package com.rapidswing.forms;

import com.rapidswing.i18n.I18n;
import com.rapidswing.property.Property;
import com.rapidswing.ui.ErrorMessages;
import com.rapidswing.util.Edt;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Edits {@link Settings} with one tab per category. Changes are applied with OK and discarded with Cancel;
 * Import, Export and Restore Defaults act on the values shown in the dialog.
 * <pre>
 * SettingsDialog.show(frame, settings);
 * </pre>
 */
public class SettingsDialog extends JPanel {

    private final Settings settings;
    private final Map<String, Property<Object>> working = new LinkedHashMap<>();
    private final List<Form> forms = new ArrayList<>();
    private final JTabbedPane tabs = new JTabbedPane();

    public SettingsDialog(Settings settings) {
        super(new BorderLayout(0, 8));
        Edt.checkEdt();
        this.settings = settings;
        for (String category : settings.categories()) {
            FormBuilder builder = Form.builder();
            for (Settings.Setting<?> setting : settings.settingsIn(category)) {
                addField(builder, setting);
            }
            Form form = builder.build();
            forms.add(form);
            JPanel page = new JPanel(new BorderLayout());
            page.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            page.add(form.getComponent(), BorderLayout.NORTH);
            tabs.addTab(Settings.text(category), page);
        }
        add(settings.categories().size() == 1 ? (JComponent) tabs.getComponentAt(0) : tabs, BorderLayout.CENTER);
    }

    /** Shows the dialog; returns true if the user pressed OK. */
    public static boolean show(Window owner, Settings settings) {
        I18n i18n = I18n.global();
        SettingsDialog panel = new SettingsDialog(settings);
        JDialog dialog = new JDialog(owner, i18n.get("settings.title"), JDialog.ModalityType.APPLICATION_MODAL);
        boolean[] applied = new boolean[1];

        JButton ok = new JButton(i18n.get("settings.ok"));
        ok.addActionListener(e -> {
            if (panel.apply()) {
                applied[0] = true;
                dialog.dispose();
            }
        });
        JButton cancel = new JButton(i18n.get("settings.cancel"));
        cancel.addActionListener(e -> dialog.dispose());
        JButton importButton = new JButton(i18n.get("settings.import"));
        importButton.addActionListener(e -> panel.chooseAndImport());
        JButton exportButton = new JButton(i18n.get("settings.export"));
        exportButton.addActionListener(e -> panel.chooseAndExport());
        JButton reset = new JButton(i18n.get("settings.reset"));
        reset.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(dialog, i18n.get("settings.confirmReset"), i18n.get("dialog.confirm"),
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                panel.resetToDefaults();
            }
        });

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(importButton);
        left.add(exportButton);
        left.add(reset);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(cancel);
        right.add(ok);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(panel, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 560), dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return applied[0];
    }

    /** Validates the shown values and stores them in the settings. Returns false if a value is invalid. */
    public boolean apply() {
        for (int i = 0; i < forms.size(); i++) {
            if (!forms.get(i).validate()) {
                if (tabs.getTabCount() > i) {
                    tabs.setSelectedIndex(i);
                }
                return false;
            }
        }
        working.forEach((key, value) -> settings.get(key).set(value.get()));
        return true;
    }

    /** Shows the defaults; OK stores them. */
    public void resetToDefaults() {
        for (String category : settings.categories()) {
            for (Settings.Setting<?> setting : settings.settingsIn(category)) {
                working.get(setting.key).set(setting.defaultValue);
            }
        }
    }

    /** Shows the values from a settings file; OK stores them. Returns how many were read. */
    public int importValues(InputStream in) throws IOException {
        Properties file = new Properties();
        file.load(in);
        int count = 0;
        for (String category : settings.categories()) {
            for (Settings.Setting<?> setting : settings.settingsIn(category)) {
                String text = file.getProperty(setting.key);
                Object value = text == null ? null : setting.parseValid(text);
                if (value != null) {
                    working.get(setting.key).set(value);
                    count++;
                }
            }
        }
        return count;
    }

    /** Writes the values shown in the dialog, in the format {@link Settings#importFrom} reads. */
    public void exportValues(OutputStream out) throws IOException {
        Properties file = new Properties();
        for (String category : settings.categories()) {
            for (Settings.Setting<?> setting : settings.settingsIn(category)) {
                file.setProperty(setting.key, setting.text(working.get(setting.key).get()));
            }
        }
        file.store(out, "RapidSwing settings");
    }

    /** The editing component of a setting, e.g. for tests. */
    public JComponent component(String key) {
        Settings.Setting<?> setting = find(key);
        for (Form form : forms) {
            try {
                return form.component(Settings.text(setting.label));
            } catch (IllegalArgumentException ignored) {
                // Not on this tab.
            }
        }
        throw new IllegalArgumentException("No setting '" + key + "'");
    }

    /** The shown (not yet applied) value of a setting. */
    public Object shownValue(String key) {
        return working.get(key).get();
    }

    private void chooseAndImport() {
        JFileChooser chooser = chooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (InputStream in = Files.newInputStream(chooser.getSelectedFile().toPath())) {
            int count = importValues(in);
            JOptionPane.showMessageDialog(this, I18n.global().get("settings.imported", count));
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(this, I18n.global().get("settings.importFailed", ErrorMessages.messageFor(e)),
                    I18n.global().get("dialog.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void chooseAndExport() {
        JFileChooser chooser = chooser();
        chooser.setSelectedFile(new File("settings.properties"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try (OutputStream out = Files.newOutputStream(chooser.getSelectedFile().toPath())) {
            exportValues(out);
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(this, I18n.global().get("settings.exportFailed", ErrorMessages.messageFor(e)),
                    I18n.global().get("dialog.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JFileChooser chooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.global().get("settings.fileType"), "properties"));
        return chooser;
    }

    private Settings.Setting<?> find(String key) {
        for (String category : settings.categories()) {
            for (Settings.Setting<?> setting : settings.settingsIn(category)) {
                if (setting.key.equals(key)) {
                    return setting;
                }
            }
        }
        throw new IllegalArgumentException("No setting '" + key + "'");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void addField(FormBuilder builder, Settings.Setting<?> setting) {
        Property<Object> value = new Property<>(setting.property.get());
        working.put(setting.key, value);
        String label = Settings.text(setting.label);
        if (setting.type == Integer.class) {
            builder.spinner(label, (Property<Integer>) (Property<?>) value, setting.min, setting.max);
        } else if (setting.type == Boolean.class) {
            builder.checkbox(label, (Property<Boolean>) (Property<?>) value);
        } else if (setting.type.isEnum()) {
            builder.combo(label, (Property) value, (Object[]) setting.choices);
        } else {
            builder.text(label, (Property<String>) (Property<?>) value);
        }
    }
}
