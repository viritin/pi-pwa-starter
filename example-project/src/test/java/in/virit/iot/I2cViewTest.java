package in.virit.iot;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
