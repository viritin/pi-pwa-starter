package in.virit.iot.pihelpers;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Small settings the user changes from the UI and expects to survive a
 * restart, kept as properties files in {@code starter.data-dir}. No database:
 * a Pi project has a handful of these, not a schema.
 */
@ApplicationScoped
public class SettingsStore {

    private static final Logger LOG = Logger.getLogger(SettingsStore.class);

    @ConfigProperty(name = "starter.data-dir", defaultValue = "data")
    String dataDir;

    public Path directory() {
        return Path.of(dataDir);
    }

    /** The stored properties, or empty ones when the file does not exist yet. */
    public Properties load(String name) {
        var properties = new Properties();
        Path file = directory().resolve(name + ".properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                LOG.warnf(e, "Could not read %s", file);
            }
        }
        return properties;
    }

    public void save(String name, Properties properties) {
        Path file = directory().resolve(name + ".properties");
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "Written by the application; edit while it is stopped.");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }
}
