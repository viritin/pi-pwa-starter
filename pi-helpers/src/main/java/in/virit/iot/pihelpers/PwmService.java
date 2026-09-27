package in.virit.iot.pihelpers;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hardware PWM through the Linux sysfs interface ({@code /sys/class/pwm}),
 * with nanosecond resolution so a hobby servo can be positioned precisely.
 * This is the same interface Pi4J's FFM provider uses; it is driven directly
 * here because the released Pi4J API only accepts whole-percent duty cycles.
 * <p>
 * Hardware PWM must be enabled with a device tree overlay, for example
 * {@code dtoverlay=pwm-2chan} in {@code /boot/firmware/config.txt}. On a
 * Pi 4 and older that gives {@code pwmchip0} channels 0 and 1 on GPIO 18 and
 * 19; a Pi 5 exposes {@code pwmchip2} with GPIO 12, 13, 18 and 19 as channels
 * 0 to 3.
 */
@ApplicationScoped
public class PwmService {

    private static final Logger LOG = Logger.getLogger(PwmService.class);
    private static final Path SYSFS = Path.of("/sys/class/pwm");
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    /**
     * @param gpio the GPIO this channel currently comes out on, when the host could tell; else null
     * @param hint what to show next to the sysfs name: the GPIO, a guess, or "no GPIO routed"
     */
    public record Channel(int chip, int channel, Integer gpio, String hint) {
        public String key() {
            return "pwmchip" + chip + "/pwm" + channel;
        }

        public String label() {
            return key() + (hint == null ? "" : " · " + hint);
        }
    }

    public record State(boolean enabled, long periodNanos, long dutyNanos) {
        public static final State OFF = new State(false, 0, 0);

        public int frequencyHz() {
            return periodNanos <= 0 ? 0 : (int) Math.round((double) NANOS_PER_SECOND / periodNanos);
        }

        public double dutyPercent() {
            return periodNanos <= 0 ? 0 : 100.0 * dutyNanos / periodNanos;
        }

        public int pulseMicros() {
            return (int) Math.round(dutyNanos / 1000.0);
        }
    }

    private final Map<String, State> states = new java.util.HashMap<>();


    /** Which GPIOs the channels are routed to right now, from pinctrl and config.txt; see {@link PwmPins}. */
    public PwmPins.Report pins() {
        return PwmPins.probe();
    }

    /**
     * Channels of every PWM chip on the host, each labelled with the GPIO that
     * currently carries it when the host can tell, else with the possible pins.
     * The chips are the SoC's PWM blocks; pinctrl names them PWM0 and PWM1, and
     * the kernel numbers them in an order that has changed between releases
     * (a Pi 5 had pwmchip2 for PWM0 on 6.6 and pwmchip0 on 6.12), so the block a
     * chip is comes from the order of the devices' addresses, PWM0 being the lower.
     */
    public List<Channel> channels() {
        var channels = new ArrayList<Channel>();
        boolean pi5 = BoardInfo.detect().isPi5();
        var pins = pins();
        var chips = new ArrayList<>(InterfaceStatus.list(SYSFS, "pwmchip"));
        chips.sort(java.util.Comparator.comparing(PwmService::deviceAddress));
        for (int block = 0; block < chips.size(); block++) {
            String name = chips.get(block);
            int chip;
            int count;
            try {
                chip = Integer.parseInt(name.substring("pwmchip".length()));
                count = Integer.parseInt(Files.readString(SYSFS.resolve(name).resolve("npwm")).trim());
            } catch (IOException | NumberFormatException e) {
                continue;
            }
            for (int channel = 0; channel < count; channel++) {
                Integer gpio = pins.tool() == null ? null : pins.gpioOf(block, channel);
                String hint = gpio != null ? "GPIO" + gpio
                        : pins.tool() != null ? "no GPIO routed" : hint(pi5, chip, channel);
                channels.add(new Channel(chip, channel, gpio, hint));
            }
        }
        return channels;
    }

    /** The platform device behind a chip, e.g. "1f00098000.pwm"; sorting these puts PWM0 before PWM1. */
    private static String deviceAddress(String chipName) {
        try {
            return Files.readSymbolicLink(SYSFS.resolve(chipName).resolve("device")).getFileName().toString();
        } catch (IOException | RuntimeException e) {
            return "~" + chipName; // unknown last, in chip order
        }
    }

    private static String hint(boolean pi5, int chip, int channel) {
        if (pi5 && chip == 2) {
            return switch (channel) {
                case 0 -> "GPIO12";
                case 1 -> "GPIO13";
                case 2 -> "GPIO18";
                case 3 -> "GPIO19";
                default -> null;
            };
        }
        if (!pi5 && chip == 0) {
            return channel == 0 ? "GPIO18 (or 12)" : channel == 1 ? "GPIO19 (or 13)" : null;
        }
        return null;
    }

    public synchronized State state(Channel channel) {
        var remembered = states.get(channel.key());
        if (remembered != null) {
            return remembered;
        }
        Path dir = channelDir(channel);
        if (!Files.isDirectory(dir)) {
            return State.OFF;
        }
        try {
            return new State("1".equals(readTrimmed(dir.resolve("enable"))),
                    Long.parseLong(readTrimmed(dir.resolve("period"))),
                    Long.parseLong(readTrimmed(dir.resolve("duty_cycle"))));
        } catch (IOException | NumberFormatException e) {
            return State.OFF;
        }
    }

    /** Sets frequency and pulse width and enables the output. */
    public synchronized State apply(Channel channel, int frequencyHz, long dutyNanos) {
        if (frequencyHz < 1 || frequencyHz > 10_000_000) {
            throw new IllegalArgumentException("Frequency must be 1 Hz to 10 MHz.");
        }
        long periodNanos = NANOS_PER_SECOND / frequencyHz;
        if (dutyNanos < 0 || dutyNanos > periodNanos) {
            throw new IllegalArgumentException("Pulse width must be between 0 and the period ("
                    + periodNanos / 1000 + " µs at " + frequencyHz + " Hz).");
        }
        var state = new State(true, periodNanos, dutyNanos);
        Path dir = export(channel);
        try {
            // The kernel rejects a duty cycle longer than the period, so shrink first, grow after.
            long currentDuty = parseLong(readTrimmed(dir.resolve("duty_cycle")));
            if (currentDuty > periodNanos) {
                write(dir.resolve("duty_cycle"), "0");
            }
            write(dir.resolve("period"), Long.toString(periodNanos));
            write(dir.resolve("duty_cycle"), Long.toString(dutyNanos));
            write(dir.resolve("enable"), "1");
        } catch (IOException e) {
            throw new IllegalStateException("Could not program " + channel.key() + ": " + e.getMessage()
                    + ". Check that the user may write to /sys/class/pwm (gpio group / udev rule).", e);
        }
        states.put(channel.key(), state);
        LOG.debugf("PWM %s: %d Hz, %d ns high", channel.key(), frequencyHz, dutyNanos);
        return state;
    }

    /** Stops the output; the channel stays exported so it can be re-enabled quickly. */
    public synchronized State disable(Channel channel) {
        var previous = state(channel);
        var state = new State(false, previous.periodNanos(), previous.dutyNanos());
        Path dir = channelDir(channel);
        if (Files.isDirectory(dir)) {
            try {
                write(dir.resolve("enable"), "0");
            } catch (IOException e) {
                throw new IllegalStateException("Could not disable " + channel.key() + ": " + e.getMessage(), e);
            }
        }
        states.put(channel.key(), state);
        return state;
    }

    private Path export(Channel channel) {
        Path chipDir = SYSFS.resolve("pwmchip" + channel.chip());
        Path dir = channelDir(channel);
        if (!Files.isDirectory(chipDir)) {
            throw new IllegalStateException(chipDir + " does not exist. Enable hardware PWM with dtoverlay=pwm-2chan and reboot.");
        }
        if (!Files.isDirectory(dir)) {
            try {
                write(chipDir.resolve("export"), Integer.toString(channel.channel()));
            } catch (IOException e) {
                throw new IllegalStateException("Could not export " + channel.key() + ": " + e.getMessage(), e);
            }
        }
        // udev applies group permissions a moment after the channel appears
        Path enable = dir.resolve("enable");
        long deadline = System.currentTimeMillis() + 2000;
        while (!Files.isWritable(enable) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!Files.isWritable(enable)) {
            throw new IllegalStateException(enable + " is not writable. Add the user to the gpio group or a udev rule for pwm.");
        }
        return dir;
    }

    private static Path channelDir(Channel channel) {
        return SYSFS.resolve("pwmchip" + channel.chip()).resolve("pwm" + channel.channel());
    }

    private static String readTrimmed(Path path) throws IOException {
        return Files.readString(path).trim();
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void write(Path path, String value) throws IOException {
        Files.writeString(path, value);
    }

    @PreDestroy
    synchronized void shutdown() {
        for (var channel : channels()) {
            var state = states.get(channel.key());
            if (state != null && state.enabled()) {
                try {
                    disable(channel);
                } catch (RuntimeException e) {
                    LOG.warnf("Disabling %s on shutdown failed: %s", channel.key(), e.getMessage());
                }
            }
        }
    }
}
