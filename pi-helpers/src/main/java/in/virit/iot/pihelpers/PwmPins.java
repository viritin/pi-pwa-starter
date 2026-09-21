package in.virit.iot.pihelpers;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Which GPIOs carry the hardware PWM channels right now. Hardware PWM can sit
 * on GPIO12 or 18 (channel 0) and GPIO13 or 19 (channel 1), on a Pi 5 also 14
 * and 15, and the overlay in config.txt decides; a person wiring a servo needs
 * to know which. Raspberry Pi OS's {@code pinctrl} (older images:
 * {@code raspi-gpio}) prints every pin's current function, which is the truth
 * after boot; the {@code dtoverlay=pwm…} lines in config.txt are shown next to
 * it as the intention.
 */
public final class PwmPins {

    private static final Logger LOG = Logger.getLogger(PwmPins.class);
    /** Every GPIO a hardware PWM channel can be routed to on any Pi model. */
    static final List<Integer> PWM_CAPABLE = List.of(12, 13, 14, 15, 18, 19);
    private static final List<Path> CONFIG_FILES = List.of(Path.of("/boot/firmware/config.txt"), Path.of("/boot/config.txt"));
    /** PWM0_0 and PWM0_CHAN2 name block and channel; raspi-gpio's bare PWM1 is channel 1 of block 0. */
    private static final Pattern BLOCK_AND_CHANNEL = Pattern.compile("^PWM(\\d)_(?:CHAN)?(\\d)$");
    private static final Pattern CHANNEL_ONLY = Pattern.compile("^PWM(\\d)$");

    private PwmPins() {
    }

    /**
     * @param functions gpio → function as the tool prints it ("PWM0_0", "input"), for the PWM-capable pins
     * @param overlays  the dtoverlay=pwm… lines found in config.txt, verbatim
     * @param tool      "pinctrl" or "raspi-gpio", or null when neither could be run
     */
    public record Report(Map<Integer, String> functions, List<String> overlays, String tool) {

        /**
         * The GPIO currently set to the given channel of the given PWM block, or null
         * when no pin is. A Pi 5 has two blocks of four channels; earlier models expose
         * block 0 with two.
         */
        public Integer gpioOf(int block, int channel) {
            for (var entry : functions.entrySet()) {
                var slot = slotOf(entry.getValue());
                if (slot != null && slot.block() == block && slot.channel() == channel) {
                    return entry.getKey();
                }
            }
            return null;
        }

        /** True when the tool ran and no pin is set to PWM at all. */
        public boolean nothingRouted() {
            return tool != null && functions.values().stream().noneMatch(f -> slotOf(f) != null);
        }
    }

    /** One PWM output: which block of the SoC and which channel in it. */
    public record Slot(int block, int channel) {
    }

    /** Asks the host; never throws, a missing tool just leaves {@code tool} null. */
    public static Report probe() {
        var functions = Pinctrl.probe(PWM_CAPABLE);
        return new Report(functions.byGpio(), overlays(), functions.tool());
    }

    /** PWM0_0 → block 0 channel 0, PWM1_CHAN3 → block 1 channel 3, PWM1 → block 0 channel 1; null for anything else. */
    static Slot slotOf(String function) {
        if (function == null) {
            return null;
        }
        var upper = function.toUpperCase();
        var m = BLOCK_AND_CHANNEL.matcher(upper);
        if (m.matches()) {
            return new Slot(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        }
        m = CHANNEL_ONLY.matcher(upper);
        return m.matches() ? new Slot(0, Integer.parseInt(m.group(1))) : null;
    }

    private static List<String> overlays() {
        var lines = new ArrayList<String>();
        for (Path file : CONFIG_FILES) {
            if (!Files.isReadable(file)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("dtoverlay=pwm")) {
                        lines.add(trimmed);
                    }
                }
            } catch (IOException e) {
                LOG.debugf(e, "Could not read %s", file);
            }
            break; // the first readable config.txt is the one in use
        }
        return lines;
    }
}
