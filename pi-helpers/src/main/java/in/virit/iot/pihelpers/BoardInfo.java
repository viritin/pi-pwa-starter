package in.virit.iot.pihelpers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * What this application is running on, read once from the files Linux keeps
 * for it: the device-tree model string a Raspberry Pi firmware writes
 * ("Raspberry Pi 5 Model B Rev 1.0"), the distribution's pretty name and the
 * kernel release. A development machine has no device-tree model, which the
 * panels say instead of pretending.
 */
public final class BoardInfo {

    private static final Path MODEL = Path.of("/proc/device-tree/model");
    private static final Path OS_RELEASE = Path.of("/etc/os-release");
    private static final Path KERNEL = Path.of("/proc/sys/kernel/osrelease");

    /**
     * @param model  the device-tree model, or null when the host has none
     * @param os     the distribution's PRETTY_NAME, or null
     * @param kernel the kernel release, or null
     */
    public record Board(String model, String os, String kernel, String arch) {

        public boolean isRaspberryPi() {
            return model != null && model.startsWith("Raspberry Pi");
        }

        public boolean isPi5() {
            return model != null && model.contains("Raspberry Pi 5");
        }

        /** "Raspberry Pi 5 Model B Rev 1.0" or "Not a Raspberry Pi (Linux 7.0.12-linuxkit, aarch64)". */
        public String describe() {
            if (model != null) {
                return model;
            }
            return "Not a Raspberry Pi (" + (kernel != null ? "Linux " + kernel : "unknown kernel")
                    + (arch != null ? ", " + arch : "") + ")";
        }

        /** "Debian GNU/Linux 12 (bookworm) · 6.12.34+rpt-rpi-2712" with whatever is known. */
        public String describeOs() {
            if (os == null && kernel == null) {
                return "N/A";
            }
            return (os != null ? os : "Linux") + (kernel != null ? " · " + kernel : "");
        }
    }

    private static volatile Board cached;

    private BoardInfo() {
    }

    /** The host, read on first use; the answer does not change while the application runs. */
    public static Board detect() {
        Board board = cached;
        if (board == null) {
            board = new Board(read(MODEL).map(s -> s.replace("\0", "").trim()).filter(s -> !s.isEmpty()).orElse(null),
                    prettyName().orElse(null),
                    read(KERNEL).map(String::trim).orElse(null),
                    System.getProperty("os.arch"));
            cached = board;
        }
        return board;
    }

    private static Optional<String> prettyName() {
        return read(OS_RELEASE).flatMap(text -> text.lines()
                .filter(line -> line.startsWith("PRETTY_NAME="))
                .map(line -> line.substring("PRETTY_NAME=".length()).replace("\"", "").trim())
                .findFirst());
    }

    private static Optional<String> read(Path path) {
        try {
            return Files.isReadable(path) ? Optional.of(Files.readString(path)) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
