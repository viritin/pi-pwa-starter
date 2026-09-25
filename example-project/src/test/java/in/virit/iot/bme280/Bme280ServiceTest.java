package in.virit.iot.bme280;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Bme280ServiceTest {

    @Test
    void keepsLastReadingOnFailureAndResumesPublishingAfterReconnect() {
        var source = new TestSensor();
        var service = new Bme280Service();
        service.sensor = source;
        var published = new ArrayList<Bme280Service.Reading>();
        var registration = service.addListener(published::add);

        service.sample();
        var first = service.latest().orElseThrow();
        source.failure = new IllegalStateException("Sensor disconnected");
        service.sample();

        assertEquals(first, service.latest().orElseThrow());
        assertEquals(List.of(first), published);
        assertEquals("Reading failed: Sensor disconnected", service.status());
        assertEquals(1, source.closed);

        source.failure = null;
        source.next = new Bme280Service.Reading(Instant.now(), 24.0, 50.0, 1015.0);
        service.sample();

        assertEquals(source.next, service.latest().orElseThrow());
        assertEquals(List.of(first, source.next), published);
        assertEquals("Connected", service.status());
        registration.remove();
        service.sample();
        assertEquals(2, published.size());
        service.stop();
        assertEquals(2, source.closed);
    }

    @Test
    void missingSensorDoesNotPublishAndBadListenerDoesNotStopHistoryOrOtherListeners() {
        var source = new TestSensor();
        var service = new Bme280Service();
        service.sensor = source;
        var published = new ArrayList<Bme280Service.Reading>();
        service.addListener(reading -> { throw new IllegalStateException("UI detached"); });
        service.addListener(published::add);

        source.next = null;
        service.sample();
        assertTrue(service.latest().isEmpty());
        assertTrue(published.isEmpty());

        source.next = new Bme280Service.Reading(Instant.now().minusSeconds(90_000), 20.0, null, 1000.0);
        service.sample();
        source.next = new Bme280Service.Reading(Instant.now(), 21.0, null, 1001.0);
        service.sample();

        assertEquals(2, published.size());
        assertEquals(List.of(source.next), service.history(Bme280Service.WINDOW));
        assertNull(service.latest().orElseThrow().humidity(), "BMP280 has no humidity measurement");
        service.stop();
    }

    private static class TestSensor implements Bme280Sensor {
        Bme280Service.Reading next = new Bme280Service.Reading(Instant.now(), 22.0, 45.0, 1012.0);
        RuntimeException failure;
        int closed;

        @Override
        public Bme280Service.Reading read() {
            if (failure != null) {
                throw failure;
            }
            return next;
        }

        @Override
        public String model() {
            return "BME280";
        }

        @Override
        public String status() {
            return "Connected";
        }

        @Override
        public void close() {
            closed++;
        }
    }
}
