package in.virit.iot;

import com.vaadin.flow.component.checkbox.Switch;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The header map: tap a pin, make it an output, drive it, release everything. */
@QuarkusTest
class GpioViewTest extends ViewTest {

    @Test
    void configuresAndDrivesAnOutput() {
        navigate(GpioView.class);
        findButton().withText("Release all pins").click();
        test(find(ConfirmDialog.class).first()).confirm();
        assertEquals("Simulation · 0 pins configured", paragraph("gpio-status"));
        assertTrue(paragraph("gpio-board").contains("40-pin"), "the map names the board and the header it shows");
        var setup = find(com.vaadin.flow.component.details.Details.class)
                .withCondition(d -> "Letting the application drive GPIO".equals(d.getSummaryText())).first();
        assertFalse(setup.isOpened(), "nothing has failed, so the steps stay folded");
        assertTrue(textOf(setup).contains("usermod -aG gpio,i2c,spi " + System.getProperty("user.name")));

        findDiv().withId("pin-11").click();
        test(find(RadioButtonGroup.class).withLabel("Mode").first()).selectItem("Output");
        assertEquals("Level: LOW", paragraph("pin-level"), "outputs start low");

        toggle(find(Switch.class).withLabel("Drive HIGH (3.3 V)").first());
        assertEquals("Level: HIGH", paragraph("pin-level"));

        findButton().withText("Close").click();
        assertEquals("Simulation · 1 pin configured", paragraph("gpio-status"));
        assertTrue(textOf(findDiv().withId("pin-11").component()).contains("OUT · H"),
                "the header map shows the pin as a high output");
    }

    @Test
    void simulatedInputsCanBeForced() {
        navigate(GpioView.class);
        findDiv().withId("pin-13").click();
        test(find(RadioButtonGroup.class).withLabel("Mode").first()).selectItem("Input");
        test(find(RadioButtonGroup.class).withLabel("Pull resistor").first()).selectItem("Pull-up");
        assertEquals("Level: HIGH", paragraph("pin-level"), "a pull-up idles high");
        findButton().withText("Simulate LOW").click();
        assertEquals("Level: LOW", paragraph("pin-level"));
        findButton().withText("Close").click();
        assertTrue(textOf(findDiv().withId("pin-13").component()).contains("IN · L"));
    }
}
