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
        assertEquals(18, report.gpioOfChannel(0));
        assertEquals(19, report.gpioOfChannel(1));
        assertNull(report.gpioOfChannel(2));
        assertFalse(report.nothingRouted());
    }

    @Test
    void readsPi5PinctrlOutputWhereTheChannelFollowsThePin() {
        var report = new PwmPins.Report(Map.of(
                12, "PWM0_CHAN0", 13, "input", 18, "input", 19, "PWM0_CHAN3"), List.of(), "pinctrl");
        assertEquals(12, report.gpioOfChannel(0));
        assertEquals(19, report.gpioOfChannel(3));
        assertNull(report.gpioOfChannel(2));
    }

    @Test
    void readsRaspiGpioOutput() {
        var report = new PwmPins.Report(Map.of(12, "PWM0", 13, "INPUT", 18, "INPUT", 19, "PWM1"), List.of(), "raspi-gpio");
        assertEquals(12, report.gpioOfChannel(0));
        assertEquals(19, report.gpioOfChannel(1));
    }

    @Test
    void knowsWhenNothingIsRoutedAndWhenItCannotTell() {
        assertTrue(new PwmPins.Report(Map.of(12, "input", 18, "input"), List.of(), "pinctrl").nothingRouted());
        assertFalse(new PwmPins.Report(Map.of(), List.of(), null).nothingRouted(), "no tool, no verdict");
    }
}
