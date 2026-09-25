package in.virit.iot.pihelpers.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentReporterTest {
    @Test
    void keepsAnIncidentAndNotifiesListenersWithoutAUi() {
        var reporter = new IncidentReporter();
        var observed = new AtomicReference<>();
        var registration = reporter.addListener(observed::set);

        reporter.report(new IllegalStateException("sensor disappeared"), "background-test");

        assertEquals(1, reporter.recent().size());
        assertEquals("background-test", reporter.recent().getFirst().source());
        assertTrue(reporter.recent().getFirst().details().contains("sensor disappeared"));
        assertEquals(reporter.recent().getFirst(), observed.get());
        registration.remove();
    }
}
