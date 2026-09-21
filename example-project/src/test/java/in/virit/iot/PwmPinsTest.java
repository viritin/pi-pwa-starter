package in.virit.iot;

import in.virit.iot.pihelpers.PwmPins;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading pin functions the way pinctrl and raspi-gpio print them, without a Pi. */
class PwmPinsTest {

    @Test
    void readsPi4PinctrlOutput() {
        var report = new PwmPins.Report(Map.of(
                12, "input", 13, "input", 18, "PWM0_0", 19, "PWM0_1"), List.of("dtoverlay=pwm-2chan"), "pinctrl");
        assertEquals(18, report.gpioOf(0, 0));
        assertEquals(19, report.gpioOf(0, 1));
        assertNull(report.gpioOf(0, 2));
        assertNull(report.gpioOf(1, 0), "block 1 has no pins");
        assertFalse(report.nothingRouted());
    }

    @Test
    void readsPi5PinctrlOutputWhereBlockAndChannelFollowThePin() {
        var report = new PwmPins.Report(Map.of(
                12, "PWM0_CHAN0", 13, "input", 18, "PWM0_CHAN2", 19, "input", 15, "PWM1_CHAN3"), List.of(), "pinctrl");
        assertEquals(12, report.gpioOf(0, 0));
        assertEquals(18, report.gpioOf(0, 2));
        assertNull(report.gpioOf(1, 2), "GPIO18 is block 0's channel 2, not block 1's");
        assertEquals(15, report.gpioOf(1, 3));
    }

    @Test
    void readsRaspiGpioOutput() {
        var report = new PwmPins.Report(Map.of(12, "PWM0", 13, "INPUT", 18, "INPUT", 19, "PWM1"), List.of(), "raspi-gpio");
        assertEquals(12, report.gpioOf(0, 0));
        assertEquals(19, report.gpioOf(0, 1));
    }

    @Test
    void knowsWhenNothingIsRoutedAndWhenItCannotTell() {
        assertTrue(new PwmPins.Report(Map.of(12, "input", 18, "input"), List.of(), "pinctrl").nothingRouted());
        assertFalse(new PwmPins.Report(Map.of(), List.of(), null).nothingRouted(), "no tool, no verdict");
    }
}
