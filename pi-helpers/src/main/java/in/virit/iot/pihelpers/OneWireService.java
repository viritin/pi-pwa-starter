package in.virit.iot.pihelpers;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads 1-Wire devices from the kernel's {@code /sys/bus/w1} tree, most
 * commonly DS18B20 temperature probes. Needs {@code dtoverlay=w1-gpio}
 * (data on GPIO 4 by default, with a 4.7 kΩ pull-up to 3V3). No Pi4J needed.
 * In simulated mode two probes report slowly drifting temperatures.
 */
@ApplicationScoped
public class OneWireService {

    private static final Path DEVICES = Path.of("/sys/bus/w1/devices");

    /** @param celsius null for devices that are not thermometers or failed to read */
    public record Sensor(String id, String family, Double celsius, String error) {
    }

    @ConfigProperty(name = "starter.hardware.simulated", defaultValue = "false")
    boolean simulated;

    private volatile int phantoms;

    public boolean isSimulated() {
        return simulated;
    }

    /**
     * How many entries the last {@link #read()} left out as noise. With the driver
     * enabled but the data line floating (nothing attached, or no pull-up), the
     * kernel's bus search turns noise into "00-…" devices; family 0x00 does not
     * exist, so they are dropped rather than shown as sensors.
     */
    public int phantoms() {
        return phantoms;
    }

    public boolean isBusPresent() {
        return simulated || !InterfaceStatus.list(DEVICES, "w1_bus_master").isEmpty();
    }

    public List<Sensor> read() {
        if (simulated) {
            double t = System.currentTimeMillis() / 60000.0;
            return List.of(
                    new Sensor("28-000005e2fdc3", family("28"), round(21.4 + 1.5 * Math.sin(t)), null),
                    new Sensor("28-0316a279b3ff", family("28"), round(4.1 + 0.6 * Math.cos(t / 3)), null));
        }
        var sensors = new ArrayList<Sensor>();
        int noise = 0;
        for (String id : InterfaceStatus.list(DEVICES, "")) {
            if (id.startsWith("w1_bus_master")) {
                continue;
            }
            if (id.startsWith("00-")) {
                noise++;
                continue;
            }
            sensors.add(readSensor(id));
        }
        phantoms = noise;
        return sensors;
    }

    private Sensor readSensor(String id) {
        String family = family(id.length() >= 2 ? id.substring(0, 2) : id);
        Path dir = DEVICES.resolve(id);
        Path temperature = dir.resolve("temperature");
        try {
            if (Files.exists(temperature)) {
                String raw = Files.readString(temperature).trim();
                if (raw.isEmpty()) {
                    return new Sensor(id, family, null, "no reading");
                }
                return new Sensor(id, family, round(Long.parseLong(raw) / 1000.0), null);
            }
            Path slave = dir.resolve("w1_slave");
            if (Files.exists(slave)) {
                String text = Files.readString(slave);
                if (!text.contains("YES")) {
                    return new Sensor(id, family, null, "CRC error");
                }
                int idx = text.lastIndexOf("t=");
                if (idx >= 0) {
                    return new Sensor(id, family, round(Long.parseLong(text.substring(idx + 2).trim()) / 1000.0), null);
                }
            }
            return new Sensor(id, family, null, null);
        } catch (IOException | NumberFormatException e) {
            return new Sensor(id, family, null, "read failed");
        }
    }

    static String family(String code) {
        return switch (code.toLowerCase()) {
            case "28" -> "DS18B20";
            case "10" -> "DS18S20";
            case "22" -> "DS1822";
            case "3b" -> "DS1825 / MAX31850";
            case "42" -> "DS28EA00";
            case "26" -> "DS2438";
            case "12" -> "DS2406";
            case "29" -> "DS2408";
            case "2d" -> "DS2431 EEPROM";
            default -> "family 0x" + code.toUpperCase();
        };
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
