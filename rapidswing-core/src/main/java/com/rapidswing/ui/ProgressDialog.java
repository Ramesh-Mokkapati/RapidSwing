package com.rapidswing.ui;

import com.rapidswing.i18n.I18n;
import com.rapidswing.task.ProgressCallable;
import com.rapidswing.task.ProgressModel;
import com.rapidswing.task.Task;
import com.rapidswing.task.TaskService;
import com.rapidswing.util.Edt;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Runs work in the background behind a modal progress dialog with a Cancel button.
 * The dialog appears only if the work takes longer than half a second.
 * <pre>
 * ProgressDialog.run(frame, "Importing", tasks, progress -&gt; importer.importAll(files, progress))
 *         .onSuccess(count -&gt; dialogs.info(count + " records imported"))
 *         .start();
 * </pre>
 * Cancel marks the {@link com.rapidswing.task.Progress} as cancelled and then cancels the task;
 * {@code onSuccess} and {@code onError} do not run for a cancelled task.
 */
public final class ProgressDialog {

    static final int SHOW_DELAY_MILLIS = 500;

    private ProgressDialog() {
    }

    /** Returns the task, not yet started: add callbacks, then call {@code start()}. */
    public static <T> Task<T> run(Window owner, String title, TaskService tasks, ProgressCallable<T> work) {
        Edt.checkEdt();
        ProgressModel progress = new ProgressModel();
        Task<T> task = tasks.callWithProgress(progress, work);
        JDialog dialog = create(owner, title, progress, task);
        Timer showLater = new Timer(SHOW_DELAY_MILLIS, e -> {
            if (!task.isDone() && !task.isCancelled()) {
                dialog.setVisible(true);
            }
        });
        showLater.setRepeats(false);
        task.alsoFinally(() -> {
            showLater.stop();
            dialog.dispose();
        });
        showLater.start();
        return task;
    }

    static JDialog create(Window owner, String title, ProgressModel progress, Task<?> task) {
        I18n i18n = I18n.global();
        JDialog dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        JLabel message = new JLabel(" ");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setStringPainted(false);
        progress.message().onChange(text -> message.setText(text == null || text.isEmpty() ? " " : text));
        Runnable updateBar = () -> {
            int total = progress.total().get();
            bar.setIndeterminate(total <= 0);
            if (total > 0) {
                bar.setMaximum(total);
                bar.setValue(progress.done().get());
                bar.setStringPainted(true);
            }
        };
        progress.total().onChange(value -> updateBar.run());
        progress.done().onChange(value -> updateBar.run());

        Runnable cancel = () -> {
            progress.cancel();
            message.setText(i18n.get("progress.cancelling"));
            task.cancel();
        };
        JButton cancelButton = new JButton(i18n.get("progress.cancel"));
        cancelButton.addActionListener(e -> cancel.run());
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel.run();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(cancelButton);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        content.add(message, BorderLayout.NORTH);
        content.add(bar, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setSize(420, dialog.getPreferredSize().height + 10);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(owner);
        return dialog;
    }
}
