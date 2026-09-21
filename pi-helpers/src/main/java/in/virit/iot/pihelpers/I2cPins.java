package in.virit.iot.pihelpers;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Which GPIOs carry an I²C bus. Bus 1 is on GPIO2 and GPIO3 on every model;
 * the extra buses a config.txt overlay enables (i2c3…i2c6 on a Pi 4,
 * i2c0-pi5…i2c3-pi5 on a Pi 5) can sit on several pin pairs, so the pins are
 * read from {@link Pinctrl}, whose function names carry the bus number
 * ("SDA1"/"SCL1", or "I2C1_SDA" style). Some buses exist without ever
 * reaching the 40-pin header: the HAT EEPROM bus, the camera and display
 * connectors' buses, HDMI's DDC. They show up in /dev but are no use for a
 * breakout, and the panel says so.
 */
public final class I2cPins {

    private static final Pattern SDA = Pattern.compile("^(?:SDA(\\d)|I2C(\\d)_SDA)$");
    private static final Pattern SCL = Pattern.compile("^(?:SCL(\\d)|I2C(\\d)_SCL)$");

    /** Buses the kernel creates for connectors other than the 40-pin header. */
    static final Map<Integer, String> INTERNAL = Map.of(
            0, "the HAT ID EEPROM bus on GPIO0/1 (pins 27 and 28); leave it to the firmware",
            10, "a camera or display connector bus",
            11, "a camera or display connector bus",
            13, "a camera or display connector bus (the Pi 5's CSI/DSI ports), not on the 40-pin header",
            14, "a camera or display connector bus (the Pi 5's CSI/DSI ports), not on the 40-pin header",
            20, "an HDMI DDC bus, not on the header",
            21, "an HDMI DDC bus, not on the header",
            22, "an HDMI DDC bus, not on the header");

    private I2cPins() {
    }

    /**
     * @param sda      the data GPIO, or null when the bus is not on the header or unknown
     * @param scl      the clock GPIO, or null likewise
     * @param detected true when pinctrl reported the pins; false for the well-known default
     * @param note     for a bus that is not on the header, what it is; else null
     * @param probed   true when pinctrl (or raspi-gpio) answered at all
     */
    public record Bus(int number, Integer sda, Integer scl, boolean detected, String note, boolean probed) {
        public boolean known() {
            return sda != null && scl != null;
        }

        public boolean internal() {
            return note != null;
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
                return new Bus(bus, sda, scl, true, null, true);
            }
        }
        if (INTERNAL.containsKey(bus)) {
            return new Bus(bus, null, null, false, INTERNAL.get(bus), functions.available());
        }
        return bus == 1 ? new Bus(1, 2, 3, false, null, functions.available())
                : new Bus(bus, null, null, false, null, functions.available());
    }

    /** The i2c lines of config.txt, verbatim: dtparam=i2c_arm=on and any dtoverlay=i2c… */
    public static List<String> configLines() {
        return ConfigTxt.lines(line -> line.startsWith("dtparam=i2c") || line.startsWith("dtoverlay=i2c"));
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
