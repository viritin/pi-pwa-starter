package in.virit.iot.pihelpers;

/** Optional development/test capability for forcing GPIO input levels. */
public interface GpioInputSimulator extends Simulated {
    void simulateInput(int bcm, boolean high);
}
