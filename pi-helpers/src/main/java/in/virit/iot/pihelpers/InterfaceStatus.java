package in.virit.iot.pihelpers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reports which hardware interfaces the Linux host exposes, by looking for
 * their device nodes. Each field is a short human readable summary such as
 * "on · i2c-1" or "off". The text is for the System panel; it does not
 * try to detect what is wired to the interfaces.
 */
public final class InterfaceStatus {

    public record Status(String gpio, String i2c, String spi, String uart, String oneWire, String pwm) {
        public static final Status UNAVAILABLE = new Status("N/A", "N/A", "N/A", "N/A", "N/A", "N/A");
    }

    private InterfaceStatus() {
    }

    public static Status read() {
        try {
            return new Status(gpio(), i2c(), spi(), uart(), oneWire(), pwm());
        } catch (RuntimeException e) {
            return Status.UNAVAILABLE;
        }
    }

    private static String gpio() {
        var chips = list(Path.of("/dev"), "gpiochip");
        return chips.isEmpty() ? "off" : "on · " + String.join(", ", chips);
    }

    private static String i2c() {
        var buses = list(Path.of("/dev"), "i2c-");
        return buses.isEmpty() ? "off" : "on · " + String.join(", ", buses);
    }

    private static String spi() {
        var devices = list(Path.of("/dev"), "spidev");
        return devices.isEmpty() ? "off" : "on · " + String.join(", ", devices);
    }

    private static String uart() {
        Path serial0 = Path.of("/dev/serial0");
        if (Files.exists(serial0)) {
            try {
                return "on · serial0 → " + Files.readSymbolicLink(serial0).getFileName();
            } catch (IOException | UnsupportedOperationException e) {
                return "on · serial0";
            }
        }
        var ttys = list(Path.of("/dev"), "ttyAMA");
        return ttys.isEmpty() ? "off" : "on · " + String.join(", ", ttys);
    }

    private static String oneWire() {
        Path devices = Path.of("/sys/bus/w1/devices");
        if (!Files.isDirectory(devices)) {
            return "off";
        }
        long sensors = list(devices, "").stream().filter(name -> !name.startsWith("w1_bus_master")).count();
        return "on · " + sensors + (sensors == 1 ? " device" : " devices");
    }

    private static String pwm() {
        var chips = list(Path.of("/sys/class/pwm"), "pwmchip");
        if (chips.isEmpty()) {
            return "off";
        }
        return "on · " + chips.stream()
                .map(chip -> chip + " (" + channels(chip) + " ch)")
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String channels(String chip) {
        try {
            return Files.readString(Path.of("/sys/class/pwm", chip, "npwm")).trim();
        } catch (IOException e) {
            return "?";
        }
    }

    /** Sorted names in {@code dir} starting with {@code prefix}; empty when the directory is missing. */
    static List<String> list(Path dir, String prefix) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(prefix))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
