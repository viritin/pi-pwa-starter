package in.virit.iot.pihelpers;

import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/** The Pi's boot configuration, read for the lines that decide which buses and pins exist after boot. */
public final class ConfigTxt {

    private static final Logger LOG = Logger.getLogger(ConfigTxt.class);
    private static final List<Path> FILES = List.of(Path.of("/boot/firmware/config.txt"), Path.of("/boot/config.txt"));

    private ConfigTxt() {
    }

    /** The trimmed, non-comment lines matching the test, from the first readable config.txt; empty when none. */
    public static List<String> lines(Predicate<String> test) {
        for (Path file : FILES) {
            if (!Files.isReadable(file)) {
                continue;
            }
            try {
                return Files.readAllLines(file).stream()
                        .map(String::trim)
                        .filter(line -> !line.startsWith("#"))
                        .filter(test)
                        .toList();
            } catch (IOException e) {
                LOG.debugf(e, "Could not read %s", file);
            }
        }
        return List.of();
    }

    /** True when a config.txt was found at all; false on a machine that is not a Pi. */
    public static boolean present() {
        return FILES.stream().anyMatch(Files::isReadable);
    }
}
