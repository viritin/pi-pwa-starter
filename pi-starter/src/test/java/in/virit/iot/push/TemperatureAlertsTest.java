package in.virit.iot.push;

import in.virit.iot.push.TemperatureAlerts.Zone;
import org.junit.jupiter.api.Test;

import static in.virit.iot.push.TemperatureAlerts.evaluate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a reading leaves the range, and when the alert may sound again. */
class TemperatureAlertsTest {

    @Test
    void leavingTheRangeIsAnnouncedOnce() {
        var first = evaluate(Zone.OK, 25.1, 18.0, 25.0);
        assertEquals(Zone.TOO_WARM, first.zone());
        assertTrue(first.announce());
        var again = evaluate(first.zone(), 26.0, 18.0, 25.0);
        assertFalse(again.announce(), "still too warm is not news");
    }

    @Test
    void reArmsOnlyWellInsideTheRange() {
        var hovering = evaluate(Zone.TOO_WARM, 24.8, null, 25.0);
        assertEquals(Zone.TOO_WARM, hovering.zone(), "within the re-arm margin the alert stays raised");
        var back = evaluate(hovering.zone(), 24.4, null, 25.0);
        assertEquals(Zone.OK, back.zone());
        assertFalse(back.announce(), "returning to normal is not notified");
        assertTrue(evaluate(back.zone(), 25.2, null, 25.0).announce(), "re-armed, so the next crossing is news");
    }

    @Test
    void tooColdWorksTheSameWay() {
        var cold = evaluate(Zone.OK, 17.9, 18.0, null);
        assertEquals(Zone.TOO_COLD, cold.zone());
        assertTrue(cold.announce());
        assertEquals(Zone.TOO_COLD, evaluate(cold.zone(), 18.3, 18.0, null).zone());
        assertEquals(Zone.OK, evaluate(cold.zone(), 18.6, 18.0, null).zone());
    }

    @Test
    void crossingStraightToTheOtherSideIsNews() {
        assertTrue(evaluate(Zone.TOO_COLD, 31.0, 18.0, 25.0).announce());
    }

    @Test
    void withoutLimitsNothingHappens() {
        assertFalse(evaluate(Zone.OK, 99.0, null, null).announce());
    }

    @Test
    void formatsTemperatures() {
        assertEquals("25 °C", TemperatureAlerts.celsius(25.0));
        assertEquals("24.3 °C", TemperatureAlerts.celsius(24.31));
    }
}
