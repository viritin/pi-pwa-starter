package in.virit.iot;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
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
        assertTrue(findH4().withText("Interfaces").exists());
        assertTrue(findSpan().withText("I²C").exists(), "the interface badges are labelled");
        findButton().withText("Run GC").click();
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
