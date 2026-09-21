package in.virit.iot.pihelpers;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Which GPIOs carry an I²C bus. Bus 1 is on GPIO2 and GPIO3 on every model;
 * the extra buses a config.txt overlay enables (i2c3…i2c6 on a Pi 4,
 * i2c0-pi5…i2c3-pi5 on a Pi 5) can sit on several pin pairs, so the pins are
 * read from {@link Pinctrl}, whose function names carry the bus number
 * ("SDA1"/"SCL1", or "I2C1_SDA" style).
 */
public final class I2cPins {

    private static final Pattern SDA = Pattern.compile("^(?:SDA(\\d)|I2C(\\d)_SDA)$");
    private static final Pattern SCL = Pattern.compile("^(?:SCL(\\d)|I2C(\\d)_SCL)$");

    private I2cPins() {
    }

    /**
     * @param sda      the data GPIO, or null when unknown
     * @param scl      the clock GPIO, or null when unknown
     * @param detected true when pinctrl reported the pins; false for the well-known default
     */
    public record Bus(int number, Integer sda, Integer scl, boolean detected) {
        public boolean known() {
            return sda != null && scl != null;
        }
    }

    /** The pins of a bus as the host reports them, else the model-independent default for bus 1. */
    public static Bus describe(int bus) {
        return describe(bus, Pinctrl.probe(IntStream.rangeClosed(0, 27).boxed().toList()));
    }

    /** As {@link #describe(int)}, from functions already read; the tested core. */
    public static Bus describe(int bus, Pinctrl.Functions functions) {
        if (functions.available()) {
            Integer sda = pinWith(functions.byGpio(), SDA, bus);
            Integer scl = pinWith(functions.byGpio(), SCL, bus);
            if (sda != null || scl != null) {
                return new Bus(bus, sda, scl, true);
            }
        }
        return bus == 1 ? new Bus(1, 2, 3, false) : new Bus(bus, null, null, false);
    }

    private static Integer pinWith(Map<Integer, String> functions, Pattern pattern, int bus) {
        for (var entry : functions.entrySet()) {
            var m = pattern.matcher(entry.getValue().toUpperCase());
            if (m.matches()) {
                String number = m.group(1) != null ? m.group(1) : m.group(2);
                if (Integer.parseInt(number) == bus) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
