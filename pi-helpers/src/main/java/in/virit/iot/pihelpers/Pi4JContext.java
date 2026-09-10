package in.virit.iot.pihelpers;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.IOType;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * The one Pi4J {@link Context} shared by every service in the application.
 * Pi4J refuses to open a GPIO line twice, so all hardware access should go
 * through this context: the helper panels can then see which pins the
 * application's own services hold, and orderly shutdown releases everything.
 * <p>
 * The context is created on first use, never during startup, so a development
 * machine without GPIO can still run the application. With
 * {@code starter.hardware.simulated=true} the context is never created and
 * services keep their state in memory instead.
 */
@ApplicationScoped
public class Pi4JContext {

    private static final Logger LOG = Logger.getLogger(Pi4JContext.class);

    @ConfigProperty(name = "starter.hardware.simulated", defaultValue = "false")
    boolean simulated;

    private Context context;

    public boolean isSimulated() {
        return simulated;
    }

    /** @throws IllegalStateException when hardware access is simulated */
    public synchronized Context context() {
        if (simulated) {
            throw new IllegalStateException("Hardware access is simulated; no Pi4J context is available.");
        }
        if (context == null) {
            LOG.info("Initializing Pi4J context");
            context = Pi4J.newAutoContext();
        }
        return context;
    }

    /** True when some IO in the shared context already uses the given BCM GPIO. */
    public synchronized boolean isGpioInUse(int bcm) {
        if (context == null) {
            return false;
        }
        var registry = context.registry();
        return registry.exists(IOType.DIGITAL_INPUT, bcm) || registry.exists(IOType.DIGITAL_OUTPUT, bcm);
    }

    @PreDestroy
    synchronized void shutdown() {
        if (context != null) {
            LOG.info("Shutting down Pi4J context");
            try {
                context.shutdown();
            } catch (RuntimeException e) {
                LOG.warn("Pi4J shutdown failed", e);
            }
            context = null;
        }
    }
}
