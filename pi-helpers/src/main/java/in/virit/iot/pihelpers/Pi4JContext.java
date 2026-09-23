package in.virit.iot.pihelpers;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.IO;
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
            var created = Pi4J.newAutoContext();
            if (created.providers().all().isEmpty()) {
                // Pi4J's FFM plugin refuses to initialize when the user lacks the gpio group and logs why
                // at ERROR; without this check the symptom would be a "provider not found" on every pin.
                try {
                    created.shutdown();
                } catch (RuntimeException ignored) {
                }
                throw new IllegalStateException(NO_PROVIDERS);
            }
            context = created;
        }
        return context;
    }

    /** The one reason Pi4J ends up with no providers on a Pi, in words that say what to do. */
    static final String NO_PROVIDERS = "Pi4J loaded no hardware provider: its FFM plugin only starts when "
            + System.getProperty("user.name") + " is in the gpio group (the log above has its exact complaint). "
            + "Run: sudo usermod -aG gpio,i2c,spi " + System.getProperty("user.name")
            + " and restart the application.";

    /**
     * Closes an IO and takes it out of Pi4J's registry, so the same pin or I²C
     * address can be created again later. Always use this instead of
     * {@code io.close()}: in Pi4J 4.0.2 {@code I2CBase.close()} only flips a flag
     * and never unregisters, so the next create at that address fails with
     * "IO instance already exists". {@code Context.shutdown(id)} unregisters and
     * closes in one go; when the IO is unknown to the registry, plain close is
     * all that is left.
     */
    public synchronized void release(IO<?, ?, ?> io) {
        if (io == null) {
            return;
        }
        if (context != null && context.registry().exists(io.id())) {
            try {
                context.shutdown(io.id());
                return;
            } catch (RuntimeException e) {
                // Pi4J 4.0.2 unregisters only after a successful shutdown, so a close that throws would keep
                // the address reserved for good. The IO has marked itself closed by now, so a second shutdown
                // skips the close and just unregisters.
                LOG.warnf(e, "Pi4J could not close %s cleanly; unregistering it anyway", io.id());
            }
            try {
                context.shutdown(io.id());
            } catch (RuntimeException e) {
                LOG.errorf(e, "%s stays registered in Pi4J; its pin or address cannot be opened again until restart", io.id());
            }
            return;
        }
        try {
            io.close();
        } catch (RuntimeException e) {
            LOG.debugf(e, "Closing %s failed", io.id());
        }
    }

    /** The id of the I²C device this application has open at the address, if any. */
    public synchronized java.util.Optional<String> i2cHolder(int bus, int address) {
        if (context == null) {
            return java.util.Optional.empty();
        }
        return context.registry().allByType(com.pi4j.io.i2c.I2C.class).values().stream()
                .filter(i2c -> i2c.bus() == bus && i2c.device() == address)
                .map(IO::id)
                .findFirst();
    }

    /**
     * Releases every registered IO whose id starts with the prefix. For tools that
     * never keep a device open between requests: anything of theirs still registered
     * is a leftover of an earlier failure, and removing it lets the next request work.
     *
     * @return how many were released
     */
    public synchronized int releaseLeftovers(String idPrefix) {
        if (context == null) {
            return 0;
        }
        var leftovers = context.registry().all().values().stream()
                .filter(io -> io.id().startsWith(idPrefix))
                .map(io -> (IO<?, ?, ?>) io)
                .toList();
        leftovers.forEach(this::release);
        if (!leftovers.isEmpty()) {
            LOG.warnf("Released %d Pi4J leftovers of %s*", leftovers.size(), idPrefix);
        }
        return leftovers.size();
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
