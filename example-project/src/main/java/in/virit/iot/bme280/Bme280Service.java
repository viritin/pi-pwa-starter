package in.virit.iot.bme280;

import com.vaadin.flow.shared.Registration;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Samples a climate sensor, keeps the last day in memory and notifies the UI
 * and MQTT publisher. The injected {@link Bme280Sensor} owns hardware access;
 * the same sampling and history logic runs with any measurement source.
 */
@ApplicationScoped
public class Bme280Service {

    private static final Logger LOG = Logger.getLogger(Bme280Service.class);
    /** How far back the history goes. */
    public static final Duration WINDOW = Duration.ofHours(24);
    static final Duration INTERVAL = Duration.ofSeconds(5);

    /**
     * @param humidity relative humidity in percent, null for a BMP280
     * @param pressure hPa
     */
    public record Reading(Instant at, double temperature, Double humidity, double pressure) {
    }

    @Inject
    Bme280Sensor sensor;

    private final Deque<Reading> history = new ArrayDeque<>();
    private final List<Consumer<Reading>> listeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService executor;
    private volatile String status = "Starting";

    void onStart(@Observes StartupEvent event) {
        sensor.initialHistory().forEach(this::record);
        status = sensor.status();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "bme280-sampler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::sample, 0, INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        sensor.close();
    }

    /** "BME280" or "BMP280" once a sensor has answered, otherwise null. */
    public String model() {
        return sensor.model();
    }

    /** One line about the connection, for the view's status text. */
    public String status() {
        return status;
    }

    public boolean isSimulated() {
        return sensor.isSimulated();
    }

    public Optional<Reading> latest() {
        synchronized (history) {
            return Optional.ofNullable(history.peekLast());
        }
    }

    /** Readings within the last {@code window}, oldest first. */
    public List<Reading> history(Duration window) {
        Instant oldest = Instant.now().minus(window);
        synchronized (history) {
            var result = new ArrayList<Reading>();
            for (Reading reading : history) {
                if (!reading.at().isBefore(oldest)) {
                    result.add(reading);
                }
            }
            return result;
        }
    }

    /** Listener is called from the sampling thread; wrap UI updates in ui.access. */
    public Registration addListener(Consumer<Reading> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    synchronized void sample() {
        try {
            Reading reading = sensor.read();
            status = sensor.status();
            if (reading == null) {
                return;
            }
            record(reading);
            for (var listener : listeners) {
                try {
                    listener.accept(reading);
                } catch (RuntimeException e) {
                    LOG.debug("BME280 listener failed", e);
                }
            }
        } catch (Exception e) {
            // Never let the scheduled task die; the next tick retries
            if (sensor.model() != null) {
                LOG.warnf(e, "BME280 reading failed; reconnecting");
            } else {
                LOG.debugf(e, "BME280 sampling failed");
            }
            status = "Reading failed: " + e.getMessage();
            sensor.close();
        }
    }

    private void record(Reading reading) {
        synchronized (history) {
            history.addLast(reading);
            Instant oldest = reading.at().minus(WINDOW);
            while (!history.isEmpty() && history.peekFirst().at().isBefore(oldest)) {
                history.removeFirst();
            }
        }
    }
}
