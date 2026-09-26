package in.virit.iot;

import in.virit.iot.pihelpers.tools.I2cView;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import in.virit.iot.pihelpers.SimulationBanner;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bus scanner and register tools against the simulated devices. */
@QuarkusTest
class I2cViewTest extends ViewTest {

    @Test
    void scansReadsAndWrites() {
        navigate(I2cView.class);
        findButton().withText("Scan bus").click();
        assertEquals("Simulation · 5 devices on i2c-1", paragraph("i2c-status"));

        findSpan().withId("i2c-0x76").click();
        assertTrue(cardTitled("0x76 · BME280"), "the device gets a hint");
        test(findTextField().withLabel("Start register (hex)").component()).setValue("D0");
        findButton().withText("Read registers").click();
        assertTrue(findPre().withId("i2c-dump").component().getText().startsWith("D0: 60"),
                "the BME280 chip id is 0x60");

        test(findTextField().withLabel("Register (hex)").component()).setValue("F4");
        test(findTextField().withLabel("Value (hex)").component()).setValue("B7");
        findButton().withText("Write").click();
        test(find(ConfirmDialog.class).first()).confirm();
        test(findTextField().withLabel("Start register (hex)").component()).setValue("F4");
        findButton().withText("Read registers").click();
        assertTrue(findPre().withId("i2c-dump").component().getText().startsWith("F4: B7"),
                "the write reaches the (simulated) device");
    }

    @Test
    void tellsWhereToWireTheSelectedBus() {
        navigate(I2cView.class);
        var wiring = paragraph("i2c-wiring");
        assertTrue(wiring.contains("SDA → GPIO2 (pin 3)"), wiring);
        assertTrue(wiring.contains("SCL → GPIO3 (pin 5)"), wiring);
    }

    @Test
    void togglesThePinsOfAPortExpander() {
        navigate(I2cView.class);
        findButton().withText("Scan bus").click();
        findSpan().withId("i2c-0x20").click();
        assertTrue(findH3().withText("Pins").exists(), "a PCF8574 address gets pin toggles");
        var p4 = find(com.vaadin.flow.component.checkbox.Checkbox.class).withId("i2c-pin-4").first();
        assertTrue(Boolean.TRUE.equals(p4.getValue()), "pins start high");
        test(p4).click();
        findButton().withId("i2c-expander-write").click();
        test(p4).click(); // tick it back locally, then read what the (simulated) chip really has
        findButton().withId("i2c-expander-read").click();
        assertFalse(Boolean.TRUE.equals(p4.getValue()), "the chip kept P4 low after the write");
        assertTrue(findSpan().withText("= 0xEF").exists());
    }

    @Test
    void simulatedHardwareIsAnnouncedAboveTheData() {
        navigate(I2cView.class);
        var banner = find(SimulationBanner.class).first();
        assertTrue(banner.isVisible(), "the tests run against simulated hardware, so the banner shows");
        assertEquals("Simulated hardware", banner.getTitleAsText());
    }

    @Test
    void setupStepsStayFoldedWhileTheBusExists() {
        navigate(I2cView.class);
        var setup = find(Details.class)
                .withCondition(d -> "Enabling I²C on the Pi".equals(d.getSummaryText())).first();
        assertFalse(setup.isOpened(), "the simulated bus is present, so the steps are only a click away");
        assertTrue(textOf(setup).contains("sudo raspi-config nonint do_i2c 0"), "the steps are runnable commands");
        assertTrue(textOf(setup).contains("sudo usermod -aG gpio,i2c " + System.getProperty("user.name")),
                "the group command names the account the application runs as");
    }
}
