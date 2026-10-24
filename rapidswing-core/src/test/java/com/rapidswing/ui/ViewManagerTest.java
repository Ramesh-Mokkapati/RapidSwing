package com.rapidswing.ui;

import com.rapidswing.property.Property;
import com.rapidswing.util.Edt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewManagerTest {

    private static final class TestView extends AbstractView {
        private final List<String> log;
        private boolean canLeave = true;

        TestView(String title, List<String> log) {
            super(title);
            this.log = log;
        }

        @Override
        public void onShow() {
            log.add("show " + getTitle());
        }

        @Override
        public void onHide() {
            log.add("hide " + getTitle());
        }

        @Override
        public boolean canLeave() {
            return canLeave;
        }
    }

    @Test
    void navigatesWithLifecycleAndHistory() {
        Edt.runAndWait(() -> {
            List<String> log = new ArrayList<>();
            AtomicInteger created = new AtomicInteger();
            ViewManager views = new ViewManager()
                    .register("a", () -> {
                        created.incrementAndGet();
                        return new TestView("A", log);
                    })
                    .register("b", () -> new TestView("B", log));

            assertTrue(views.show("a"));
            assertFalse(views.canGoBack().get());
            assertTrue(views.show("b"));
            assertTrue(views.canGoBack().get());
            assertTrue(views.back());

            assertEquals("a", views.currentId().get());
            assertFalse(views.canGoBack().get());
            assertEquals(1, created.get());
            assertEquals(Arrays.asList("show A", "hide A", "show B", "hide B", "show A"), log);
        });
    }

    @Test
    void reloadRecreatesViewsAndKeepsCurrent() {
        Edt.runAndWait(() -> {
            List<String> log = new ArrayList<>();
            AtomicInteger created = new AtomicInteger();
            ViewManager views = new ViewManager()
                    .register("a", () -> new TestView("A" + created.incrementAndGet(), log));

            views.show("a");
            View first = views.currentView().get();
            views.reload();

            assertEquals("a", views.currentId().get());
            assertEquals("A2", views.currentView().get().getTitle());
            assertTrue(first != views.currentView().get());
            assertEquals(1, views.getComponent().getComponentCount());
            assertEquals(Arrays.asList("show A1", "hide A1", "show A2"), log);
        });
    }

    @Test
    void restrictedViewsAreNotCreatedOrShownWithoutAccess() {
        Edt.runAndWait(() -> {
            List<String> log = new ArrayList<>();
            List<String> denied = new ArrayList<>();
            Property<Boolean> isAdmin = Property.of(false);
            AtomicInteger created = new AtomicInteger();
            ViewManager views = new ViewManager()
                    .register("home", () -> new TestView("Home", log))
                    .register("admin", () -> {
                        created.incrementAndGet();
                        return new TestView("Admin", log);
                    })
                    .restrict("admin", isAdmin);
            views.onAccessDenied(denied::add);

            views.show("home");
            assertFalse(views.show("admin"));
            assertEquals("home", views.currentId().get());
            assertEquals(0, created.get());
            assertEquals(Arrays.asList("admin"), denied);

            isAdmin.set(true);
            assertTrue(views.show("admin"));

            // Losing access, then reloading (e.g. after a role change), shows nothing rather than the view.
            isAdmin.set(false);
            views.reload();
            assertEquals(null, views.currentView().get());
        });
    }

    @Test
    void resetDiscardsViewsAndHistory() {
        Edt.runAndWait(() -> {
            List<String> log = new ArrayList<>();
            ViewManager views = new ViewManager()
                    .register("a", () -> new TestView("A", log))
                    .register("b", () -> new TestView("B", log));
            views.show("a");
            views.show("b");

            views.reset();

            assertEquals(null, views.currentId().get());
            assertFalse(views.canGoBack().get());
            assertEquals(0, views.getComponent().getComponentCount());
        });
    }

    @Test
    void viewCanBlockNavigation() {
        Edt.runAndWait(() -> {
            List<String> log = new ArrayList<>();
            TestView a = new TestView("A", log);
            ViewManager views = new ViewManager()
                    .register("a", () -> a)
                    .register("b", () -> new TestView("B", log));

            views.show("a");
            a.canLeave = false;

            assertFalse(views.show("b"));
            assertEquals("a", views.currentId().get());
        });
    }

    @Test
    void rejectsUnknownAndDuplicateIds() {
        Edt.runAndWait(() -> {
            ViewManager views = new ViewManager().register("a", () -> new TestView("A", new ArrayList<>()));

            assertThrows(IllegalArgumentException.class, () -> views.show("missing"));
            assertThrows(IllegalArgumentException.class, () -> views.register("a", () -> null));
        });
    }

    @Test
    void navigationOffEdtFails() {
        ViewManager views = new ViewManager().register("a", () -> new TestView("A", new ArrayList<>()));

        assertThrows(IllegalStateException.class, () -> views.show("a"));
    }
}
