package in.virit.iot.pihelpers;

/**
 * Marks a service that fakes its hardware, for development and tests. Panels show a
 * {@link SimulationBanner} and start their status line with "Simulation" when their
 * service carries it. The alternatives in pi-helpers-simulation implement it; so should an
 * application's own simulated services.
 */
public interface Simulated {

    /** Whether the service, as injected, is a simulation. Works through CDI client proxies. */
    static boolean is(Object service) {
        return service instanceof Simulated;
    }

    /** "Simulation · " for a simulated service, else nothing: the start of a panel's status line. */
    static String prefix(Object service) {
        return is(service) ? "Simulation · " : "";
    }
}
