package in.virit.iot.pihelpers;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What each GPIO is doing right now, asked from Raspberry Pi OS's {@code pinctrl}
 * (older images: {@code raspi-gpio}). The device tree overlays in config.txt
 * decide which pins carry I²C, PWM or SPI, and this is the truth after boot;
 * the panels use it to tell a person which header pin to wire to.
 */
public final class Pinctrl {

    private static final Pattern PINCTRL_LINE = Pattern.compile("GPIO(\\d+)\\s*=\\s*(\\S+)");
    private static final Pattern RASPI_GPIO_LINE = Pattern.compile("GPIO (\\d+):.*\\bfunc=(\\S+)");

    private Pinctrl() {
    }

    /**
     * @param byGpio gpio → function as the tool prints it ("SDA1", "PWM0_0", "input")
     * @param tool   "pinctrl" or "raspi-gpio", or null when neither could be run
     */
    public record Functions(Map<Integer, String> byGpio, String tool) {
        public static final Functions UNAVAILABLE = new Functions(Map.of(), null);

        public boolean available() {
            return tool != null;
        }

        public String of(int gpio) {
            return byGpio.get(gpio);
        }
    }

    /** Asks the host about the given GPIOs; never throws, a missing tool gives {@link Functions#UNAVAILABLE}. */
    public static Functions probe(Collection<Integer> gpios) {
        String pins = gpios.stream().map(String::valueOf).collect(Collectors.joining(","));
        String output = run("pinctrl", "get", pins);
        if (output != null) {
            return new Functions(parsePinctrl(output), "pinctrl");
        }
        output = run("raspi-gpio", "get", pins);
        if (output != null) {
            return new Functions(parseRaspiGpio(output), "raspi-gpio");
        }
        return Functions.UNAVAILABLE;
    }

    /** Lines like {@code 18: a5    pd | lo // GPIO18 = PWM0_0}. */
    public static Map<Integer, String> parsePinctrl(String output) {
        return parse(output, PINCTRL_LINE);
    }

    /** Lines like {@code GPIO 18: level=0 fsel=2 alt=5 func=PWM0}. */
    public static Map<Integer, String> parseRaspiGpio(String output) {
        return parse(output, RASPI_GPIO_LINE);
    }

    private static Map<Integer, String> parse(String output, Pattern pattern) {
        var functions = new LinkedHashMap<Integer, String>();
        for (String line : output.split("\\R")) {
            var m = pattern.matcher(line);
            if (m.find()) {
                functions.put(Integer.parseInt(m.group(1)), m.group(2));
            }
        }
        return functions;
    }

    private static String run(String... command) {
        try {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes());
            return process.exitValue() == 0 ? output : null;
        } catch (IOException e) {
            return null; // tool not installed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
