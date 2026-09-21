package in.virit.iot;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The host dashboard: metrics, the interfaces card and confirmed power actions. */
@QuarkusTest
class SystemViewTest extends ViewTest {

    @Test
    void showsMetricsAndInterfaces() {
        navigate(SystemView.class);
        assertTrue(findH4().withText("Host & process").exists());
        assertTrue(findSpan().withText("Board").exists(), "the host's model is a badge");
        assertTrue(findSpan().withTextContaining("Raspberry Pi").exists(), "either the model or the note that this is not a Pi");
        assertTrue(findH4().withText("Interfaces").exists());
        assertTrue(findSpan().withText("I²C").exists(), "the interface badges are labelled");
        findButton().withText("Run GC").click();
    }

    @Test
    void offersSetupStepsForInterfacesAndPowerActions() {
        navigate(SystemView.class);
        var interfaces = find(Details.class)
                .withCondition(d -> "Enabling interfaces on the Pi".equals(d.getSummaryText())).first();
        assertTrue(textOf(interfaces).contains("dtoverlay=pwm-2chan"), "config.txt lines are spelled out");
        var power = find(Details.class)
                .withCondition(d -> d.getSummaryText().startsWith("Allowing reboot and shutdown")).first();
        assertTrue(textOf(power).contains("/etc/sudoers.d/"), "the sudoers rule is given as a command");
    }

    @Test
    void powerActionsAskForConfirmationFirst() {
        navigate(SystemView.class);
        assertFalse(find(ConfirmDialog.class).exists());
        findButton().withText("Reboot").click();
        var dialog = find(ConfirmDialog.class).first();
        assertTrue(dialog.isOpened(), "reboot must be confirmed, never immediate");
        test(dialog).cancel();
        assertFalse(find(ConfirmDialog.class).exists());
    }
}
