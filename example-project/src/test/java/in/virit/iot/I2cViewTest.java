package in.virit.iot;

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
        assertEquals("Simulation · 4 devices on i2c-1", paragraph("i2c-status"));

        findSpan().withId("i2c-0x76").click();
        assertTrue(findH4().withTextContaining("0x76 · BME280").exists(), "the device gets a hint");
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
    void simulatedHardwareIsAnnouncedAboveTheData() {
        navigate(I2cView.class);
        var banner = find(SimulationBanner.class).first();
        assertTrue(banner.isVisible(), "the tests run against simulated hardware, so the banner shows");
        assertTrue(textOf(banner).startsWith("Simulation"), "the word is the badge");
    }

    @Test
    void setupStepsStayFoldedWhileTheBusExists() {
        navigate(I2cView.class);
        var setup = find(Details.class)
                .withCondition(d -> "Enabling I²C on the Pi".equals(d.getSummaryText())).first();
        assertFalse(setup.isOpened(), "the simulated bus is present, so the steps are only a click away");
        assertTrue(textOf(setup).contains("sudo raspi-config nonint do_i2c 0"), "the steps are runnable commands");
        assertTrue(textOf(setup).contains("sudo usermod -aG i2c " + System.getProperty("user.name")),
                "the group command names the account the application runs as");
    }
}
