package in.virit.iot.pihelpers;

import java.util.List;

/** Where a BCM GPIO sits on the 40-pin header, and where power and ground are; shared by the wiring texts. */
public final class PiHeader {

    private PiHeader() {
    }

    /** The physical pin number of a GPIO on the 40-pin header, or null when it is not on the header. */
    public static Integer physical(int bcm) {
        for (var pin : GpioPanel.HEADER) {
            if (pin.bcm() != null && pin.bcm() == bcm) {
                return pin.physical();
            }
        }
        return null;
    }

    /** "GPIO2 (pin 3)" or just "GPIO2" when the pin is not on the header. */
    public static String describe(int bcm) {
        Integer pin = physical(bcm);
        return "GPIO" + bcm + (pin == null ? "" : " (pin " + pin + ")");
    }

    public static final List<Integer> PINS_3V3 = List.of(1, 17);
    public static final List<Integer> PINS_GND = List.of(6, 9, 14, 20, 25, 30, 34, 39);
}
