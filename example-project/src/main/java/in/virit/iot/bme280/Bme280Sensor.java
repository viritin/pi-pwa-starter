package in.virit.iot.bme280;

import java.util.List;

/** The measurement source; sampling, history and listeners belong to {@link Bme280Service}. */
public interface Bme280Sensor extends AutoCloseable {

    /** Returns null when no device is available yet. The next call will retry. */
    Bme280Service.Reading read();

    /** BME280 or BMP280 after identification, otherwise null. */
    String model();

    /** Human-readable source/connection status, also shown in the UI. */
    String status();

    /** Previously collected readings, oldest first, to load when sampling starts. */
    default List<Bme280Service.Reading> initialHistory() {
        return List.of();
    }

    /** Releases the connection. A later read may reconnect. */
    @Override
    void close();
}
