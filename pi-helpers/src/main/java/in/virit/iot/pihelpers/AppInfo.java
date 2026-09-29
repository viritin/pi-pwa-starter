package in.virit.iot.pihelpers;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The application's human-readable name, from {@code starter.app.name}, so it is
 * set once in configuration instead of being repeated across the code. Used for
 * things shown to a person at runtime (the drawer brand, share text, …); the
 * build-time PWA name in the app's {@code AppShell} is separate.
 */
@ApplicationScoped
public class AppInfo {

    @ConfigProperty(name = "starter.app.name", defaultValue = "Pi App")
    String name;

    public String name() {
        return name;
    }
}
