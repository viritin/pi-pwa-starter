package in.virit.iot.pihelpers;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Small settings the user changes from the UI and expects to survive a
 * restart, kept as one JSON file per record under {@code starter.data-dir}.
 * No database: a Pi project has a handful of these, not a schema.
 */
@ApplicationScoped
public class SettingsStore {

    private static final Logger LOG = Logger.getLogger(SettingsStore.class);

    @ConfigProperty(name = "starter.data-dir", defaultValue = "data")
    String dataDir;

    public Path directory() {
        return Path.of(dataDir);
    }

    /** The stored record, or empty when there is no file yet or it cannot be read. */
    public <T> Optional<T> load(String name, Class<T> type) {
        Path file = file(name);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Json.read(Files.readString(file), type));
        } catch (IOException | RuntimeException e) {
            LOG.warnf(e, "Could not read %s; starting from defaults", file);
            return Optional.empty();
        }
    }

    public void save(String name, Object settings) {
        Path file = file(name);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(settings));
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }

    private Path file(String name) {
        return directory().resolve(name + ".json");
    }
}
