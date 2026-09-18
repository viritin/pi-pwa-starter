package in.virit.iot.bme280;

import com.pi4j.drivers.sensor.environment.bmx280.Bmx280Driver;
import com.pi4j.io.i2c.I2C;
import com.vaadin.flow.shared.Registration;
import in.virit.iot.pihelpers.Pi4JContext;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * Reads a Bosch BME280 (or BMP280) every few seconds from the moment the
 * application starts and keeps the last day in memory, so the climate view can
 * draw a curve as soon as it is opened. An example of a sensor service to copy
 * for your own hardware: it owns the device through the shared
 * {@link Pi4JContext}, reconnects when the sensor comes and goes, tells the UI
 * about new readings through a listener, and has a simulated mode.
 * <p>
 * The driver comes from Pi4J's {@code pi4j-drivers} library and handles both
 * chips; a BMP280 has no humidity, so that value is null for it.
 */
@ApplicationScoped
public class Bme280Service {

    private static final Logger LOG = Logger.getLogger(Bme280Service.class);
    /** How far back the history goes. */
    public static final Duration WINDOW = Duration.ofHours(24);
    static final Duration INTERVAL = Duration.ofSeconds(5);
    private static final int[] ADDRESSES = {Bmx280Driver.ADDRESS_BME_280_PRIMARY, Bmx280Driver.ADDRESS_BME_280_SECONDARY};

    /**
     * @param humidity relative humidity in percent, null for a BMP280
     * @param pressure hPa
     */
    public record Reading(Instant at, double temperature, Double humidity, double pressure) {
    }

    @Inject
    Pi4JContext pi4j;

    @ConfigProperty(name = "starter.bme280.bus", defaultValue = "1")
    int bus;

    private final Deque<Reading> history = new ArrayDeque<>();
    private final List<Consumer<Reading>> listeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService executor;
    private Bmx280Driver driver;
    private I2C i2c;
    private volatile String model;
    private volatile String status = "Starting";

    void onStart(@Observes StartupEvent event) {
        if (pi4j.isSimulated()) {
            seedSimulatedHistory();
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "bme280-sampler");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::sample, 0, INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        closeDriver();
    }

    public boolean isSimulated() {
        return pi4j.isSimulated();
    }

    /** "BME280" or "BMP280" once a sensor has answered, otherwise null. */
    public String model() {
        return model;
    }

    /** One line about the connection, for the view's status text. */
    public String status() {
        return status;
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

    private void sample() {
        try {
            Reading reading = isSimulated() ? simulate(Instant.now()) : readSensor();
            if (reading == null) {
                return;
            }
            synchronized (history) {
                history.addLast(reading);
                Instant oldest = reading.at().minus(WINDOW);
                while (!history.isEmpty() && history.peekFirst().at().isBefore(oldest)) {
                    history.removeFirst();
                }
            }
            for (var listener : listeners) {
                try {
                    listener.accept(reading);
                } catch (RuntimeException e) {
                    LOG.debug("BME280 listener failed", e);
                }
            }
        } catch (Exception e) {
            // Never let the scheduled task die; the next tick retries
            LOG.debugf(e, "BME280 sampling failed");
            status = "Reading failed: " + e.getMessage();
            closeDriver();
        }
    }

    private Reading readSensor() {
        if (!Files.exists(Path.of("/dev/i2c-" + bus))) {
            status = "No /dev/i2c-" + bus + ". Enable I²C on the Pi, or set starter.hardware.simulated=true on a development machine.";
            return null;
        }
        if (driver == null && !connect()) {
            return null;
        }
        var measurement = driver.readMeasurement();
        Double humidity = driver.getModel() == Bmx280Driver.Model.BME280 ? (double) measurement.getHumidity() : null;
        return new Reading(Instant.now(), measurement.getTemperature(), humidity, measurement.getPressure() / 100.0);
    }

    private boolean connect() {
        var context = pi4j.context();
        for (int address : ADDRESSES) {
            I2C candidate = null;
            try {
                candidate = context.create(I2C.newConfigBuilder(context)
                        .id("starter-bme280-" + address).name("BME280")
                        .bus(bus).device(address).provider("ffm-i2c").build());
                driver = new Bmx280Driver(candidate);
                i2c = candidate;
                model = driver.getModel().name();
                status = "%s at 0x%02X on i2c-%d".formatted(model, address, bus);
                LOG.infof("Found %s", status);
                return true;
            } catch (RuntimeException notHere) {
                LOG.debugf(notHere, "No BMx280 at 0x%02X", address);
                pi4j.release(candidate);
            }
        }
        status = "No BME280/BMP280 found at 0x76 or 0x77 on i2c-" + bus + ". Retrying every " + INTERVAL.toSeconds() + " s.";
        return false;
    }

    private synchronized void closeDriver() {
        pi4j.release(i2c);
        driver = null;
        i2c = null;
    }

    // A living room afternoon: slow swings plus a little sensor noise, so the curve has something to show.
    private Reading simulate(Instant at) {
        double t = at.getEpochSecond() / 600.0;
        double noise = (Math.random() - 0.5) * 0.06;
        double temperature = 22.4 + 1.8 * Math.sin(t) + 0.2 * Math.sin(t * 6.3) + noise;
        double humidity = 44 + 6 * Math.cos(t / 1.7) + noise * 8;
        double pressure = 1012.3 + 2.5 * Math.sin(t / 9) + noise * 2;
        model = "BME280";
        status = "Simulation · BME280";
        return new Reading(at, round(temperature, 2), round(humidity, 1), round(pressure, 1));
    }

    private void seedSimulatedHistory() {
        Instant now = Instant.now();
        for (long seconds = Duration.ofHours(3).toSeconds(); seconds > 0; seconds -= INTERVAL.toSeconds()) {
            history.addLast(simulate(now.minusSeconds(seconds)));
        }
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }
}
