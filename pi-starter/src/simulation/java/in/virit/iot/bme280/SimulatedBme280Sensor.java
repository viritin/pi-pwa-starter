package in.virit.iot.bme280;

import in.virit.iot.pihelpers.Simulated;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Local UI development and test data. This source tree is not part of a normal
 * production build. Edit the waveforms here; Quarkus dev mode reloads the source.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class SimulatedBme280Sensor implements Bme280Sensor, Simulated {

    @Override
    public Bme280Service.Reading read() {
        return readingAt(Instant.now());
    }

    @Override
    public String model() {
        return "BME280";
    }

    @Override
    public String status() {
        return "Simulation · BME280";
    }

    @Override
    public List<Bme280Service.Reading> initialHistory() {
        Instant now = Instant.now();
        var readings = new ArrayList<Bme280Service.Reading>();
        for (long seconds = Duration.ofHours(3).toSeconds(); seconds > 0;
             seconds -= Bme280Service.INTERVAL.toSeconds()) {
            readings.add(readingAt(now.minusSeconds(seconds)));
        }
        return readings;
    }

    // Deterministic for a given instant, so history and live samples form the same curve.
    private Bme280Service.Reading readingAt(Instant at) {
        double t = at.getEpochSecond() / 600.0;
        double temperature = 22.4 + 1.8 * Math.sin(t) + 0.2 * Math.sin(t * 6.3);
        double humidity = 44 + 6 * Math.cos(t / 1.7);
        double pressure = 1012.3 + 2.5 * Math.sin(t / 9);
        return new Bme280Service.Reading(at, round(temperature, 2), round(humidity, 1), round(pressure, 1));
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }

    @Override
    public void close() {
        // No device connection.
    }
}
