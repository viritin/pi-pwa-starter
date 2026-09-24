package in.virit.iot;

import com.vaadin.browserless.ComponentTesterPackages;
import com.vaadin.browserless.internal.MockVaadin;
import com.vaadin.browserless.locator.Locators;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Switch;
import in.virit.iot.testing.SwitchTester;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Base for the browserless view tests: the real views, the real CDI services in
 * simulated hardware mode, a mocked Vaadin environment and no browser. Each
 * test navigates like a user would and inspects the resulting component tree.
 * Subclasses carry {@code @QuarkusTest} themselves: Quarkus registers only
 * directly annotated classes as test beans.
 */
@ComponentTesterPackages("in.virit.iot.testing")
abstract class ViewTest extends QuarkusBrowserlessTest implements Locators {

    private static final Duration PUSH_TIMEOUT = Duration.ofSeconds(10);

    /** Locators need a live UI; the Quarkus base sets one up before each test. */
    @Override
    public void activateLocatorContext() {
        if (UI.getCurrent() == null) {
            throw new IllegalStateException("The Vaadin test environment is not initialized");
        }
    }

    @Override
    protected Set<String> scanPackages() {
        return Set.of(TopLayout.class.getPackageName());
    }

    /** The text of the paragraph with the given id, or a message when it is missing. */
    protected String paragraph(String id) {
        return findParagraph().withId(id).component().getText();
    }

    /** Flips a Switch as a user would; there is no built-in tester for it yet. */
    protected void toggle(Switch component) {
        test(SwitchTester.class, component).toggle();
    }

    /**
     * Waits until background work has pushed to the UI. Panels update from
     * their own threads through {@code ui.access}; those tasks queue up until the
     * mocked client makes a round trip, which is what this drives.
     */
    protected void awaitPush(BooleanSupplier condition, String description) {
        Instant deadline = Instant.now().plus(PUSH_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            MockVaadin.clientRoundtrip();
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    /** True when a Card with a title containing the text is on screen. */
    protected boolean cardTitled(String text) {
        return find(com.vaadin.flow.component.card.Card.class).all().stream()
                .anyMatch(card -> card.getTitleAsText() != null && card.getTitleAsText().contains(text));
    }

    protected static String textOf(Component component) {
        return component.getElement().getTextRecursively();
    }

}
