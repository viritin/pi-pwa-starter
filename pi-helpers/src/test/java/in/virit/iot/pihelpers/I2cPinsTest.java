package in.virit.iot.pihelpers;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Finding a bus's pins in pinctrl output, and the default when there is none. */
class I2cPinsTest {

    @Test
    void readsBusPinsFromPinctrlOutput() {
        var functions = Pinctrl.parsePinctrl("""
                 2: a0    pu | hi // GPIO2 = SDA1
                 3: a0    pu | hi // GPIO3 = SCL1
                12: a0    pu | hi // GPIO12 = SDA5
                13: a0    pu | hi // GPIO13 = SCL5
                18: ip    pd | lo // GPIO18 = input
                """);
        var bus1 = I2cPins.describe(1, new Pinctrl.Functions(functions, "pinctrl"));
        assertEquals(2, bus1.sda());
        assertEquals(3, bus1.scl());
        assertTrue(bus1.detected());
        var bus5 = I2cPins.describe(5, new Pinctrl.Functions(functions, "pinctrl"));
        assertEquals(12, bus5.sda());
        assertEquals(13, bus5.scl());
    }

    @Test
    void understandsTheOtherFunctionNaming() {
        var functions = Map.of(4, "I2C2_SDA", 5, "I2C2_SCL");
        var bus2 = I2cPins.describe(2, new Pinctrl.Functions(functions, "pinctrl"));
        assertEquals(4, bus2.sda());
        assertEquals(5, bus2.scl());
    }

    @Test
    void fallsBackToTheStandardPinsForBus1Only() {
        var bus1 = I2cPins.describe(1, Pinctrl.Functions.UNAVAILABLE);
        assertEquals(2, bus1.sda());
        assertFalse(bus1.detected());
        var bus5 = I2cPins.describe(5, Pinctrl.Functions.UNAVAILABLE);
        assertNull(bus5.sda());
        assertFalse(bus5.known());
    }

    @Test
    void knowsTheBusesThatNeverReachTheHeader() {
        var functions = new Pinctrl.Functions(Map.of(2, "input", 3, "input"), "pinctrl");
        var bus13 = I2cPins.describe(13, functions);
        assertTrue(bus13.internal());
        assertFalse(bus13.known());
        assertTrue(bus13.note().contains("camera"), bus13.note());
        assertTrue(I2cPins.describe(0, functions).internal(), "the HAT EEPROM bus");
        assertFalse(I2cPins.describe(1, functions).internal());
    }

    @Test
    void readsRaspiGpioOutputToo() {
        var functions = Pinctrl.parseRaspiGpio("GPIO 2: level=1 fsel=4 alt=0 func=SDA1\nGPIO 3: level=1 fsel=4 alt=0 func=SCL1\n");
        assertEquals(Map.of(2, "SDA1", 3, "SCL1"), functions);
    }
}
