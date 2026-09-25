package in.virit.iot.bme280;

import com.pi4j.drivers.sensor.environment.bmx280.Bmx280Driver;
import com.pi4j.io.i2c.I2C;
import in.virit.iot.pihelpers.Pi4JContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** The production measurement source. The Pi4J driver owns the sensor protocol. */
@ApplicationScoped
public class Pi4jBme280Sensor implements Bme280Sensor {

    private static final Logger LOG = Logger.getLogger(Pi4jBme280Sensor.class);
    private static final int[] ADDRESSES = {
            Bmx280Driver.ADDRESS_BME_280_PRIMARY, Bmx280Driver.ADDRESS_BME_280_SECONDARY};

    @Inject
    Pi4JContext pi4j;

    @ConfigProperty(name = "starter.bme280.bus", defaultValue = "1")
    int bus;

    private Bmx280Driver driver;
    private I2C i2c;
    private volatile String model;
    private volatile String status = "Starting";

    @Override
    public Bme280Service.Reading read() {
        if (!Files.exists(Path.of("/dev/i2c-" + bus))) {
            status = "No /dev/i2c-" + bus + ". Enable I²C on the Pi.";
            return null;
        }
        if (driver == null && !connect()) {
            return null;
        }
        var measurement = driver.readMeasurement();
        Double humidity = driver.getModel() == Bmx280Driver.Model.BME280
                ? (double) measurement.getHumidity() : null;
        return new Bme280Service.Reading(Instant.now(), measurement.getTemperature(), humidity,
                measurement.getPressure() / 100.0);
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
        status = "No BME280/BMP280 found at 0x76 or 0x77 on i2c-" + bus
                + ". Retrying every " + Bme280Service.INTERVAL.toSeconds() + " s.";
        return false;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public void close() {
        pi4j.release(i2c);
        driver = null;
        i2c = null;
    }
}
