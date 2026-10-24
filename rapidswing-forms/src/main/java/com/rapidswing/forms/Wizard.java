package com.rapidswing.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.rapidswing.i18n.I18n;
import com.rapidswing.task.ProgressCallable;
import com.rapidswing.task.ProgressModel;
import com.rapidswing.task.Task;
import com.rapidswing.task.TaskService;
import com.rapidswing.util.Edt;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A multi-page dialog with Back, Next, Finish and Cancel: each page can check its input,
 * run a background step with progress when the user presses Next, and decide where to go with an
 * {@link Outcome}: the next page, another page, an error, or finish.
 * <pre>
 * Wizard wizard = Wizard.builder("Import customers", tasks)
 *         .page("file", "Choose a file", "A CSV file with one customer per line", filePanel)
 *             .validate(() -&gt; file == null ? "Please choose a file" : null)
 *             .onNext(progress -&gt; { rows = parser.read(file, progress); return Outcome.next(); })
 *         .page("review", "Review", "These customers will be imported", reviewGrid)
 *             .onEnter(() -&gt; reviewGrid.setRows(rows))
 *         .onFinish(progress -&gt; { importer.save(rows, progress); return Outcome.next(); })
 *         .build();
 * boolean imported = wizard.showDialog(frame);
 * </pre>
 * Steps run on a background thread; everything else on the EDT. Back returns to the previously shown page, also
 * after a jump.
 */
public class Wizard extends JPanel {

    /** One page: what is shown, how it is checked, and what Next does. */
    private static final class Page {
        final String id;
        final String title;
        final String description;
        final JComponent component;
        Supplier<String> validation = () -> null;
        ProgressCallable<Outcome> onNext;
        Runnable onEnter = () -> { };

        Page(String id, String title, String description, JComponent component) {
            this.id = Objects.requireNonNull(id, "id");
            this.title = title;
            this.description = description;
            this.component = Objects.requireNonNull(component, "component");
        }
    }

    private final String title;
    private final TaskService tasks;
    private final List<Page> pages;
    private final ProgressCallable<Outcome> onFinish;
    private final CardLayout cards = new CardLayout();
    private final JPanel pageArea = new JPanel(cards);
    private final JLabel heading = new JLabel();
    private final JLabel description = new JLabel();
    private final JLabel stepLabel = new JLabel();
    private final JLabel message = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton backButton = new JButton();
    private final JButton nextButton = new JButton();
    private final JButton finishButton = new JButton();
    private final JButton cancelButton = new JButton();
    private final Deque<Integer> history = new ArrayDeque<>();

    private int current = -1;
    private Task<Outcome> running;
    private ProgressModel runningProgress;
    private Runnable onClose = () -> { };
    private boolean finished;
    private boolean closed;

    private Wizard(Builder builder) {
        super(new BorderLayout());
        this.title = builder.title;
        this.tasks = builder.tasks;
        this.pages = new ArrayList<>(builder.pages);
        this.onFinish = builder.onFinish;
        I18n i18n = I18n.global();

        heading.putClientProperty(FlatClientProperties.STYLE_CLASS, "h3");
        description.setEnabled(false);
        stepLabel.setEnabled(false);
        JPanel header = new JPanel(new BorderLayout(0, 2));
        JPanel titles = new JPanel(new BorderLayout(0, 2));
        titles.setOpaque(false);
        titles.add(heading, BorderLayout.NORTH);
        titles.add(description, BorderLayout.CENTER);
        header.add(titles, BorderLayout.CENTER);
        header.add(stepLabel, BorderLayout.EAST);
        header.setBorder(BorderFactory.createEmptyBorder(14, 18, 10, 18));

        for (Page page : pages) {
            JPanel holder = new JPanel(new BorderLayout());
            holder.setBorder(BorderFactory.createEmptyBorder(12, 18, 8, 18));
            holder.add(page.component, BorderLayout.CENTER);
            pageArea.add(holder, page.id);
        }

        backButton.setText(i18n.get("wizard.back"));
        nextButton.setText(i18n.get("wizard.next"));
        finishButton.setText(i18n.get("wizard.finish"));
        cancelButton.setText(i18n.get("wizard.cancel"));
        backButton.addActionListener(e -> back());
        nextButton.addActionListener(e -> next());
        finishButton.addActionListener(e -> finish());
        cancelButton.addActionListener(e -> cancel());
        progressBar.setVisible(false);
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        message.setForeground(error != null ? error : new Color(0xCC3333));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(backButton);
        buttons.add(nextButton);
        buttons.add(finishButton);
        buttons.add(javax.swing.Box.createHorizontalStrut(12));
        buttons.add(cancelButton);
        JPanel status = new JPanel(new BorderLayout(10, 0));
        status.add(message, BorderLayout.CENTER);
        status.add(progressBar, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.add(new JSeparator(), BorderLayout.NORTH);
        south.add(status, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        south.setBorder(BorderFactory.createEmptyBorder(0, 18, 14, 18));

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.CENTER);
        top.add(new JSeparator(), BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(pageArea, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "rapidswing.wizard.cancel");
        getActionMap().put("rapidswing.wizard.cancel", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        });
        show(0, false);
    }

    public static Builder builder(String title, TaskService tasks) {
        Edt.checkEdt();
        return new Builder(title, tasks);
    }

    // ---- Navigation ----------------------------------------------------------------------------------

    /** Checks the page, runs its step (if any) and goes where the outcome says. */
    public void next() {
        if (running != null) {
            return;
        }
        if (current == pages.size() - 1) {
            finish();
            return;
        }
        Page page = pages.get(current);
        if (!check(page)) {
            return;
        }
        if (page.onNext == null) {
            go(Outcome.next());
        } else {
            runStep(page.onNext, this::go);
        }
    }

    /** Goes back to the page shown before this one. */
    public void back() {
        if (running != null || history.isEmpty()) {
            return;
        }
        show(history.pop(), false);
    }

    /** Checks the current page, runs its step, then the finish step, and closes the wizard as finished. */
    public void finish() {
        if (running != null || !finishButton.isEnabled()) {
            return;
        }
        Page page = pages.get(current);
        if (!check(page)) {
            return;
        }
        if (page.onNext == null) {
            finishNow();
        } else {
            runStep(page.onNext, outcome -> {
                if (outcome.kind == Outcome.Kind.JUMP || outcome.kind == Outcome.Kind.ERROR) {
                    go(outcome);
                } else {
                    finishNow();
                }
            });
        }
    }

    /** Runs the finish step, if any, and closes the wizard unless it reports an error. */
    private void finishNow() {
        if (onFinish == null) {
            close(true);
            return;
        }
        runStep(onFinish, outcome -> {
            if (outcome.kind == Outcome.Kind.ERROR) {
                showMessage(outcome.detail);
            } else {
                close(true);
            }
        });
    }

    /** Stops a running step, or closes the wizard as cancelled. */
    public void cancel() {
        if (running != null) {
            runningProgress.cancel();
            running.cancel();
            return;
        }
        close(false);
    }

    public String currentPageId() {
        return pages.get(current).id;
    }

    /** The error shown, or an empty string. */
    public String getMessage() {
        return message.getText().trim();
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Whether a background step is running. */
    public boolean isBusy() {
        return running != null;
    }

    public JButton getNextButton() {
        return nextButton;
    }

    public JButton getBackButton() {
        return backButton;
    }

    public JButton getFinishButton() {
        return finishButton;
    }

    /** Called once when the wizard finishes or is cancelled. */
    public Wizard onClose(Runnable handler) {
        this.onClose = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /** Shows the wizard in a modal dialog and returns whether it finished (rather than being cancelled). */
    public boolean showDialog(Window owner) {
        Edt.checkEdt();
        JDialog dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancel();
            }
        });
        Runnable previous = onClose;
        onClose = () -> {
            previous.run();
            dialog.dispose();
        };
        dialog.setContentPane(this);
        dialog.getRootPane().setDefaultButton(nextButton);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 620), Math.max(dialog.getHeight(), 440));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return finished;
    }

    // ---- Internals -----------------------------------------------------------------------------------

    private boolean check(Page page) {
        String problem = page.validation.get();
        if (problem != null) {
            showMessage(problem);
            return false;
        }
        showMessage("");
        return true;
    }

    private void go(Outcome outcome) {
        switch (outcome.kind) {
            case ERROR:
                showMessage(outcome.detail);
                break;
            case FINISH:
                finishNow();
                break;
            case JUMP:
                show(indexOf(outcome.detail), true);
                break;
            default:
                if (current < pages.size() - 1) {
                    show(current + 1, true);
                } else {
                    finishNow();
                }
                break;
        }
    }

    private void runStep(ProgressCallable<Outcome> step, java.util.function.Consumer<Outcome> then) {
        runningProgress = new ProgressModel();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);
        ProgressModel progress = runningProgress;
        progress.message().onChange(text -> message.setText(text == null || text.isEmpty() ? " " : text));
        progress.total().onChange(total -> {
            progressBar.setIndeterminate(total <= 0);
            progressBar.setMaximum(Math.max(1, total));
        });
        progress.done().onChange(progressBar::setValue);
        message.setForeground(UIManager.getColor("Label.foreground"));
        running = tasks.callWithProgress(progress, step);
        updateButtons();
        running.onSuccess(outcome -> {
            resetStatus();
            then.accept(outcome == null ? Outcome.next() : outcome);
        }).onError(error -> {
            resetStatus();
            showMessage(I18n.global().get("wizard.failed", com.rapidswing.ui.ErrorMessages.messageFor(error)));
        }).onFinally(() -> {
            if (running != null && running.isCancelled()) {
                resetStatus();
            }
            running = null;
            runningProgress = null;
            progressBar.setVisible(false);
            updateButtons();
        }).start();
    }

    private void resetStatus() {
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        message.setForeground(error != null ? error : new Color(0xCC3333));
        message.setText(" ");
    }

    private void show(int index, boolean forward) {
        if (forward && current >= 0) {
            history.push(current);
        }
        current = index;
        Page page = pages.get(index);
        cards.show(pageArea, page.id);
        heading.setText(page.title);
        description.setText(page.description == null ? " " : page.description);
        stepLabel.setText(I18n.global().get("wizard.step", index + 1, pages.size()));
        showMessage("");
        page.onEnter.run();
        updateButtons();
    }

    private void updateButtons() {
        boolean idle = running == null;
        boolean last = current == pages.size() - 1;
        backButton.setEnabled(idle && !history.isEmpty());
        nextButton.setEnabled(idle && !last);
        finishButton.setEnabled(idle && last);
        cancelButton.setEnabled(!closed);
        if (getRootPane() != null && getRootPane().getDefaultButton() != null) {
            getRootPane().setDefaultButton(last ? finishButton : nextButton);
        }
    }

    private void showMessage(String text) {
        message.setText(text == null || text.isEmpty() ? " " : text);
    }

    private int indexOf(String pageId) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).id.equals(pageId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No wizard page '" + pageId + "'");
    }

    private void close(boolean asFinished) {
        if (closed) {
            return;
        }
        closed = true;
        finished = asFinished;
        updateButtons();
        onClose.run();
    }

    /** Configures a {@link Wizard}: pages in order, each optionally with a check, a step and an enter action. */
    public static final class Builder {
        private final String title;
        private final TaskService tasks;
        private final List<Page> pages = new ArrayList<>();
        private ProgressCallable<Outcome> onFinish;

        private Builder(String title, TaskService tasks) {
            this.title = Objects.requireNonNull(title, "title");
            this.tasks = Objects.requireNonNull(tasks, "tasks");
        }

        /** Adds a page; the following calls configure it. Titles and descriptions are shown as given. */
        public Builder page(String id, String pageTitle, String pageDescription, JComponent component) {
            for (Page page : pages) {
                if (page.id.equals(id)) {
                    throw new IllegalArgumentException("Duplicate wizard page '" + id + "'");
                }
            }
            pages.add(new Page(id, pageTitle, pageDescription, component));
            return this;
        }

        /** A page whose content is a {@link Form}: Next and Finish validate it first. */
        public Builder page(String id, String pageTitle, String pageDescription, Form form) {
            page(id, pageTitle, pageDescription, form.getComponent());
            return validate(() -> form.validate() ? null : I18n.global().get("wizard.fixFields"));
        }

        /** Checked on Next and Finish: return an error message to stay on the page, or {@code null}. */
        public Builder validate(Supplier<String> check) {
            last().validation = Objects.requireNonNull(check, "check");
            return this;
        }

        /** Background work when leaving the page with Next (or Finish on the last page), with progress. */
        public Builder onNext(ProgressCallable<Outcome> step) {
            last().onNext = Objects.requireNonNull(step, "step");
            return this;
        }

        /** Runs whenever the page is shown, e.g. to fill it from earlier pages. */
        public Builder onEnter(Runnable action) {
            last().onEnter = Objects.requireNonNull(action, "action");
            return this;
        }

        /** Background work when the wizard finishes; an error outcome keeps it open. */
        public Builder onFinish(ProgressCallable<Outcome> step) {
            this.onFinish = Objects.requireNonNull(step, "step");
            return this;
        }

        public Wizard build() {
            if (pages.isEmpty()) {
                throw new IllegalStateException("A wizard needs at least one page");
            }
            return new Wizard(this);
        }

        private Page last() {
            if (pages.isEmpty()) {
                throw new IllegalStateException("Add a page first");
            }
            return pages.get(pages.size() - 1);
        }
    }
}
