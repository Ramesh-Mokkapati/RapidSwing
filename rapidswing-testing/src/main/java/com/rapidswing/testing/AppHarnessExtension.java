package com.rapidswing.testing;

import com.rapidswing.app.AppConfig;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Creates a fresh {@link AppHarness} before each test and closes it afterwards. Without a display the test
 * is skipped cleanly, with nothing left for {@code @AfterEach} methods to trip over.
 * <pre>
 * &#64;RegisterExtension
 * final AppHarnessExtension harness = new AppHarnessExtension(new AppConfig().set("app.bundle", "messages"),
 *         app -&gt; app.service(CustomerService.class, new FakeCustomerService()));
 *
 * &#64;Test void showsCustomers() {
 *     harness.get().signIn(User.of("ada", "Ada", "admin")).show("customers", CustomersScreen::create);
 * }
 * </pre>
 */
public final class AppHarnessExtension implements BeforeEachCallback, AfterEachCallback {

    private final AppConfig config;
    private final Consumer<AppHarness> setup;
    private AppHarness harness;

    public AppHarnessExtension() {
        this(new AppConfig(), app -> { });
    }

    /** @param setup runs before each test, e.g. to register fake services or choose a language */
    public AppHarnessExtension(AppConfig config, Consumer<AppHarness> setup) {
        this.config = Objects.requireNonNull(config, "config");
        this.setup = Objects.requireNonNull(setup, "setup");
    }

    /** The current test's harness. */
    public AppHarness get() {
        if (harness == null) {
            throw new IllegalStateException("The harness exists only while a test runs");
        }
        return harness;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        harness = AppHarness.create(config);
        setup.accept(harness);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (harness != null) {
            harness.close();
            harness = null;
        }
    }
}
