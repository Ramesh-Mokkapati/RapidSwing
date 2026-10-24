package com.rapidswing.app;

import com.rapidswing.event.EventBus;
import com.rapidswing.i18n.I18n;
import com.rapidswing.security.Session;
import com.rapidswing.service.ServiceRegistry;
import com.rapidswing.task.TaskService;
import com.rapidswing.ui.Dialogs;
import com.rapidswing.ui.MainFrame;
import com.rapidswing.ui.ViewManager;
import com.rapidswing.ui.WindowStateStore;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Everything an application needs, in one place. Pass it to views and controllers
 * instead of relying on static singletons.
 */
public class AppContext {

    private final AppConfig config;
    private final MainFrame mainFrame;
    private final ServiceRegistry services = new ServiceRegistry();
    private final EventBus events = new EventBus();
    private final TaskService tasks;
    private final Session session = new Session();
    private final Dialogs dialogs;
    private final WindowStateStore windowState;
    private final Preferences preferences;

    public AppContext(AppConfig config, MainFrame mainFrame, String appId) {
        this(config, mainFrame, preferencesFor(appId), new TaskService(), new Dialogs(() -> mainFrame));
    }

    /**
     * Full control over the context's collaborators, e.g. for tests: in-memory preferences, tasks that run
     * immediately and scripted dialogs (see the {@code rapidswing-testing} module).
     */
    public AppContext(AppConfig config, MainFrame mainFrame, Preferences preferences, TaskService tasks,
                      Dialogs dialogs) {
        this.config = Objects.requireNonNull(config, "config");
        this.mainFrame = Objects.requireNonNull(mainFrame, "mainFrame");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.windowState = new WindowStateStore(preferences);

        services.register(AppConfig.class, config)
                .register(EventBus.class, events)
                .register(TaskService.class, tasks)
                .register(Dialogs.class, dialogs)
                .register(I18n.class, I18n.global())
                .register(Session.class, session);
    }

    /** The per-user settings node of an application, e.g. for remembered choices. */
    public static Preferences preferencesFor(String appId) {
        return Preferences.userRoot().node("rapidswing").node(appId.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    public AppConfig config() {
        return config;
    }

    public MainFrame mainFrame() {
        return mainFrame;
    }

    public ViewManager views() {
        return mainFrame.views();
    }

    public ServiceRegistry services() {
        return services;
    }

    /** Shorthand for {@code services().get(type)}. */
    public <T> T service(Class<T> type) {
        return services.get(type);
    }

    public EventBus events() {
        return events;
    }

    public TaskService tasks() {
        return tasks;
    }

    public Dialogs dialogs() {
        return dialogs;
    }

    /** The signed-in user and their roles. */
    public Session session() {
        return session;
    }

    /** Translated texts; the same instance as {@link I18n#global()}. */
    public I18n i18n() {
        return I18n.global();
    }

    /** Per-user settings that survive restarts. */
    public Preferences preferences() {
        return preferences;
    }

    public WindowStateStore windowState() {
        return windowState;
    }
}
