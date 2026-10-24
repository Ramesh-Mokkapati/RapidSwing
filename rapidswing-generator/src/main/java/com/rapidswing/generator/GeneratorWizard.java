package com.rapidswing.generator;

import com.rapidswing.task.TaskService;
import com.rapidswing.ui.Themes;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** The application wizard window. */
final class GeneratorWizard {

    private GeneratorWizard() {
    }

    /** Opens the wizard. Call on the EDT. */
    static JFrame open() {
        Themes.install(Themes.LIGHT);
        JFrame frame = new JFrame("RapidSwing Application Wizard");
        TaskService tasks = new TaskService();
        frame.setContentPane(new WizardPanel(tasks, result -> { }, () -> {
            tasks.shutdown();
            frame.dispose();
        }));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(860, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }
}
