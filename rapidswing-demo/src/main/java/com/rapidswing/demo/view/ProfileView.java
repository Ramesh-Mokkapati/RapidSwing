package com.rapidswing.demo.view;

import com.rapidswing.app.AppContext;
import com.rapidswing.demo.model.ProfileModel;
import com.rapidswing.forms.Form;
import com.rapidswing.forms.Validators;
import com.rapidswing.i18n.I18n;
import com.rapidswing.ui.AbstractView;
import com.rapidswing.ui.AppAction;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Objects;

/** A complete form built with the fluent form builder: bound fields, rules and cross-field checks. */
public class ProfileView extends AbstractView {

    private final AppContext ctx;
    private final ProfileModel model = new ProfileModel();
    private final Form form;

    public ProfileView(AppContext ctx) {
        super(ctx.i18n().get("profile.title"));
        this.ctx = ctx;
        I18n i18n = ctx.i18n();

        form = Form.builder()
                .section(i18n.get("profile.personal"))
                .text(i18n.get("profile.name"), model.name()).required().validate(Validators.maxLength(50))
                .text(i18n.get("profile.email"), model.email()).required().validate(Validators.email())
                .integer(i18n.get("profile.age"), model.age()).validate(Validators.range(18, 120))
                    .warn(age -> age != null && age > 95 ? i18n.get("profile.ageUnusual") : null)
                .date(i18n.get("profile.birthday"), model.birthday()).validate(Validators.past())
                .combo(i18n.get("profile.country"), model.country(), "India", "Japan", "Germany", "Canada", "Spain")
                .section(i18n.get("profile.preferences"))
                .spinner(i18n.get("profile.satisfaction"), model.satisfaction(), 0, 10)
                .checkbox(i18n.get("profile.newsletter"), model.newsletter())
                .textArea(i18n.get("profile.notes"), model.notes(), 3).validate(Validators.maxLength(200))
                .section(i18n.get("profile.account"))
                .password(i18n.get("profile.password"), model.password()).required().validate(Validators.minLength(8))
                .password(i18n.get("profile.confirmPassword"), model.confirmPassword()).required()
                    .check(() -> Objects.equals(model.password().get(), model.confirmPassword().get())
                            ? null : i18n.get("profile.passwordsDiffer"), model.password())
                .buttons(AppAction.of(i18n.get("profile.reset"), this::reset),
                         AppAction.of(i18n.get("profile.loadSample"), this::loadSample),
                         AppAction.of(i18n.get("profile.save"), this::save))
                .build();

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(16, 24, 24, 24));
        content.add(form.getComponent(), BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    private void save() {
        I18n i18n = ctx.i18n();
        if (form.validate()) {
            ctx.dialogs().info(i18n.get("profile.saved"), model.summary());
        } else {
            ctx.mainFrame().statusBar().setMessage(i18n.get("profile.needsAttention", form.errors().size()));
        }
    }

    private void loadSample() {
        model.loadSample();
        form.refresh();
    }

    private void reset() {
        model.reset();
        form.refresh();
    }
}
