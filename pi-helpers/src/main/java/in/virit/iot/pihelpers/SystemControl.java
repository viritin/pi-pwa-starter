package in.virit.iot.pihelpers;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Executes host-level power actions. Requires the service user to have
 * passwordless sudo rights for reboot/shutdown on the Raspberry Pi.
 */
@ApplicationScoped
public class SystemControl {

    private static final Logger LOG = Logger.getLogger(SystemControl.class);

    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "starter.power-actions.enabled", defaultValue = "true")
    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void reboot() {
        run("sudo", "-n", "reboot");
    }

    public void shutdown() {
        run("sudo", "-n", "shutdown", "-h", "now");
    }

    private void run(String... command) {
        if (!enabled) {
            throw new IllegalStateException("Host power actions are disabled");
        }
        LOG.infof("Executing system command: %s", String.join(" ", command));
        try {
            Process process = new ProcessBuilder(command).inheritIO().start();
            try {
                if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Power command timed out");
                }
                if (process.exitValue() != 0) {
                    throw new IllegalStateException("Power command failed with exit code " + process.exitValue());
                }
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Power command interrupted", e);
        } catch (Exception e) {
            LOG.error("Failed to execute system command", e);
            throw new RuntimeException("Failed to execute: " + String.join(" ", command), e);
        }
    }
}
