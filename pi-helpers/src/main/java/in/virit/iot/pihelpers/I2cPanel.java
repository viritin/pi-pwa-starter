package in.virit.iot.pihelpers;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.card.Card;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * i2cdetect in the browser, plus a register dump and a single register write
 * for the selected device. No route or menu entry; the application adds those.
 */
@StyleSheet("styles/pi-helpers-i2c.css")
public class I2cPanel extends VerticalLayout {

    private static final Logger LOG = Logger.getLogger(I2cPanel.class);

    private final I2cService service;
    private final AddressGrid grid = new AddressGrid();
    private final DeviceTools tools = new DeviceTools();
    private final Paragraph status = new Paragraph();
    private final SetupHint setup = PiSetup.i2c();
    private final SimulationBanner simulation = new SimulationBanner(
            "This bus and the devices on it are made up; register reads and writes go to an in-memory copy.");
    private final WiringHint wiring = new WiringHint();
    private final BusSelect bus;
    private Set<Integer> found = Set.of();

    public I2cPanel(I2cService service) {
        this.service = service;
        addClassName("i2c-panel");
        status.setId("i2c-status");
        var buses = service.buses();
        bus = new BusSelect(buses);

        add(new H1("I²C"),
                new Paragraph("Find what answers on the bus, then tap an address to read its registers and check "
                        + "the wiring and the datasheet before writing any driver code. A PCF8574 port expander "
                        + "(0x20–0x27, 0x38–0x3F) gets pin toggles instead, so its LEDs, relays and buttons can be "
                        + "tried without code."),
                new Paragraph("The scan works like i2cdetect -y: it reads one byte from each address 0x03–0x77 and "
                        + "lists those that acknowledge. A device that only accepts writes, or holds the bus while "
                        + "busy, can stay invisible here as it would there; its datasheet address is the one to try."),
                simulation, new Toolbar(!buses.isEmpty()), wiring, grid, status, tools, setup);
        simulation.setVisible(Simulated.is(service));
        tools.setVisible(false);
        if (buses.isEmpty()) {
            status.setText("No /dev/i2c-* device found. The bus is not enabled on this host; the steps below fix that.");
            setup.setOpened(true);
        } else if (!Simulated.is(service) && !buses.contains(1)) {
            status.setText("Only internal buses found; the header's bus i2c-1 is not enabled. The steps below turn it on.");
            setup.setOpened(true);
        } else {
            status.setText(Simulated.prefix(service) + "Not scanned yet");
        }
    }

    /** The buses the kernel has, the header's bus 1 preselected; choosing one explains its wiring. */
    class BusSelect extends Select<Integer> {
        BusSelect(List<Integer> buses) {
            setLabel("Bus");
            setItemLabelGenerator(n -> "i2c-" + n);
            setItems(buses);
            addValueChangeListener(e -> wiring.show(e.getValue(), Simulated.is(service)));
            if (!buses.isEmpty()) {
                // the header bus first when it exists; otherwise whatever the kernel has, and the wiring line explains
                setValue(buses.contains(1) ? 1 : buses.get(0));
            }
        }
    }

    class Toolbar extends HorizontalLayout {
        Toolbar(boolean busesFound) {
            add(bus, new Button("Scan bus", e -> scan()) {{
                setId("i2c-scan");
                addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                setEnabled(busesFound);
            }});
            setAlignItems(Alignment.BASELINE);
        }
    }

    private void scan() {
        Integer selected = bus.getValue();
        if (selected == null) {
            return;
        }
        try {
            var scan = service.scan(selected);
            found = new TreeSet<>(scan.found());
            grid.update(found);
            String summary = Simulated.prefix(service)
                    + found.size() + (found.size() == 1 ? " device" : " devices") + " on i2c-" + selected;
            if (!scan.inUse().isEmpty()) {
                summary += " · " + scan.inUse().stream().map(I2cService::hex).collect(Collectors.joining(", "))
                        + " held by this application";
            }
            if (scan.problem() != null) {
                summary += " · the bus could not be used: " + scan.problem();
                Notification.show("Nothing answered because the bus could not be used at all: " + scan.problem(),
                        8000, Notification.Position.MIDDLE);
                setup.setOpened(true);
            }
            status.setText(summary);
        } catch (RuntimeException failure) {
            LOG.warn("I2C scan failed", failure);
            Notification.show("Scan failed: " + failure.getMessage(), 5000, Notification.Position.MIDDLE);
        }
    }

    private void select(int address) {
        tools.show(bus.getValue(), address);
    }

    /**
     * Which header pins the selected bus is on, so a breakout can be wired
     * without looking anything up: pinctrl says where an overlay put an extra
     * bus, and bus 1 is on GPIO2/3 on every model.
     */
    static class WiringHint extends Paragraph {
        private int requests;

        WiringHint() {
            setId("i2c-wiring");
            addClassName("i2c-wiring");
        }

        /** Asks pinctrl and reads config.txt on a thread of its own: spawning a process takes a moment on a Pi. */
        void show(Integer number, boolean simulated) {
            int request = ++requests;
            if (number == null) {
                setText("");
                return;
            }
            var ui = UI.getCurrent();
            Thread.ofVirtual().start(() -> {
                var text = describe(number, simulated);
                ui.access(() -> {
                    // a later choice of bus wins over a slower answer for an earlier one
                    if (request == requests) {
                        setText(text);
                    }
                });
            });
        }

        static String describe(int number, boolean simulated) {
            var pins = simulated ? new I2cPins.Bus(number, 2, 3, false, null, false) : I2cPins.describe(number);
            var text = new StringBuilder();
            if (pins.internal()) {
                text.append("i2c-").append(number).append(" is ").append(pins.note())
                        .append(". For a breakout board use bus 1 on ").append(PiHeader.describe(2)).append(" and ")
                        .append(PiHeader.describe(3)).append("; it appears as i2c-1 once I²C is enabled (sudo raspi-config "
                        + "nonint do_i2c 0, then reboot).");
            } else if (pins.known()) {
                text.append("Wire a device to i2c-").append(number).append(": SDA → ").append(PiHeader.describe(pins.sda()))
                        .append(", SCL → ").append(PiHeader.describe(pins.scl()))
                        .append(", VCC → 3V3 (pin ").append(PiHeader.PINS_3V3.get(0)).append(" or ").append(PiHeader.PINS_3V3.get(1))
                        .append("), GND → pin ").append(PiHeader.PINS_GND.get(0)).append(" (or any other GND). Then Scan bus.");
                if (number == 1) {
                    text.append(" Bus 1 has pull-ups on the Pi and most breakouts add their own; fine for a few devices.");
                }
                if (!pins.detected()) {
                    text.append(simulated ? "" : " These are the standard pins; pinctrl is not installed to confirm them.");
                }
            } else if (pins.probed()) {
                text.append("i2c-").append(number).append(" is an extra bus, but pinctrl shows no header GPIO set to it "
                        + "right now; its dtoverlay line in /boot/firmware/config.txt decides the pins.");
            } else {
                text.append("i2c-").append(number).append(" is an extra bus: its pins come from the dtoverlay line in "
                        + "/boot/firmware/config.txt (for example i2c5,pins_12_13). pinctrl (sudo apt install raspi-utils) "
                        + "would show which GPIOs it is on.");
            }
            if (!simulated && ConfigTxt.present()) {
                var lines = I2cPins.configLines();
                text.append(lines.isEmpty() ? " config.txt has no i2c line, so the header bus is off."
                        : " config.txt: " + String.join("; ", lines) + ".");
            }
            if (!pins.internal()) {
                text.append(" A device that stays silent is usually SDA and SCL swapped, VCC not connected, or a 5 V-only module.");
            }
            return text.toString();
        }
    }

    /** The i2cdetect table: a row label per 16 addresses, then the addresses, the found ones tappable. */
    class AddressGrid extends Div {
        private final Map<Integer, AddressCell> cells = new HashMap<>();

        AddressGrid() {
            addClassName("i2c-grid");
            for (int row = 0; row < 8; row++) {
                add(new Span("%02X".formatted(row * 16)) {{
                    addClassName("i2c-row");
                }});
                for (int col = 0; col < 16; col++) {
                    var cell = new AddressCell(row * 16 + col);
                    cells.put(cell.address, cell);
                    add(cell);
                }
            }
        }

        void update(Set<Integer> found) {
            cells.values().forEach(cell -> cell.update(found.contains(cell.address)));
        }

        class AddressCell extends Span {
            private final int address;

            AddressCell(int address) {
                super(address < I2cService.FIRST_ADDRESS || address > I2cService.LAST_ADDRESS
                        ? "" : "%02X".formatted(address));
                this.address = address;
                addClassName("i2c-cell");
                setId("i2c-" + I2cService.hex(address));
                addClickListener(e -> {
                    if (found.contains(address)) {
                        select(address);
                    }
                });
            }

            void update(boolean present) {
                setClassName("found", present);
                getElement().setAttribute("role", present ? "button" : "cell");
            }
        }
    }

    class DeviceTools extends Card {
        private final HexField startRegister = new HexField("Start register (hex)");
        private final ByteCount count = new ByteCount();
        private final HexDump dump = new HexDump();
        private final HexField writeRegister = new HexField("Register (hex)");
        private final HexField writeValue = new HexField("Value (hex)");
        private final PortExpander expander = new PortExpander();
        private Integer bus;
        private int address;

        DeviceTools() {
            setWidthFull();
            add(expander,
                    new Paragraph("Register dumps assume the device auto-increments its register pointer, "
                            + "as most sensors do. Writes change the device state; keep the datasheet open."),
                    new FieldRow(startRegister, count,
                            new Button("Read registers", e -> read(true)) {{
                                setId("i2c-read");
                                addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                            }},
                            new Button("Read without register", e -> read(false)) {{
                                addThemeVariants(ButtonVariant.LUMO_SMALL);
                            }}),
                    dump,
                    new H3("Write one register"),
                    new FieldRow(writeRegister, writeValue,
                            new Button("Write", e -> confirmWrite()) {{
                                addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                            }}));
        }

        void show(Integer bus, int address) {
            this.bus = bus;
            this.address = address;
            var hint = I2cService.hint(address);
            setTitle(I2cService.hex(address) + (hint != null ? " · " + hint + "?" : ""));
            dump.setText("");
            expander.show(bus, address);
            setVisible(true);
        }

        private void read(boolean withRegister) {
            try {
                int register = parseHex(startRegister.getValue(), 0xFF, "Start register");
                int n = count.getValue() == null ? 16 : count.getValue();
                byte[] data = withRegister
                        ? service.readRegisters(bus, address, register, n)
                        : service.read(bus, address, n);
                dump.setText(format(withRegister ? register : 0, data));
            } catch (RuntimeException failure) {
                LOG.warn("I2C read failed", failure);
                Notification.show("Read failed: " + failure.getMessage(), 5000, Notification.Position.MIDDLE);
            }
        }

        private void confirmWrite() {
            try {
                new WriteConfirmation(parseHex(writeRegister.getValue(), 0xFF, "Register"),
                        parseHex(writeValue.getValue(), 0xFF, "Value")).open();
            } catch (IllegalArgumentException invalid) {
                Notification.show(invalid.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        }

        /** A register or value as hex, 00 to start with. */
        static class HexField extends TextField {
            HexField(String label) {
                super(label, "00", "");
                setWidth("9em");
            }
        }

        static class ByteCount extends IntegerField {
            ByteCount() {
                super("Bytes");
                setValue(16);
                setMin(1);
                setMax(256);
                setStepButtonsVisible(true);
                setWidth("7em");
            }
        }

        static class HexDump extends Pre {
            HexDump() {
                addClassName("hexdump");
                setId("i2c-dump");
            }
        }

        /** Writing changes the device, so it is confirmed first, with the register and value spelled out. */
        class WriteConfirmation extends ConfirmDialog {
            WriteConfirmation(int register, int value) {
                super("Write to " + I2cService.hex(address) + "?",
                        "Register " + I2cService.hex(register) + " ← " + I2cService.hex(value)
                                + ". A wrong value can misconfigure or reset the device.",
                        "Write", confirm -> {
                    try {
                        service.writeRegister(bus, address, register, value);
                        Notification.show("Wrote " + I2cService.hex(value) + " to register " + I2cService.hex(register));
                    } catch (RuntimeException failure) {
                        LOG.warn("I2C write failed", failure);
                        Notification.show("Write failed: " + failure.getMessage(), 5000, Notification.Position.MIDDLE);
                    }
                });
                setCancelable(true);
                setConfirmButtonTheme("error primary");
            }
        }
    }

    /** Fields and buttons on one wrapping line. */
    static class FieldRow extends FlexLayout {
        FieldRow(Component... components) {
            super(components);
            addClassName("i2c-row-fields");
        }
    }

    /**
     * Pin toggles for a PCF8574-style expander, which has no registers: a read
     * returns its eight pins and a written byte lands on them. Shown for the
     * addresses those chips can have; the register tools below still work but
     * their register byte is driven onto the pins too.
     */
    class PortExpander extends Div {
        private final PinToggles pins = new PinToggles();
        private final Span value = new Span();
        private Integer bus;
        private int address;

        PortExpander() {
            addClassName("i2c-expander");
            value.addClassName("i2c-expander-value");
            add(new H3("Pins"),
                    new Paragraph("A PCF8574 has no registers: a read returns its eight pins and a written byte "
                            + "sets them, high by default. Ticked is high. A LED between 3.3 V and a pin lights when "
                            + "the pin is low, as with the Pioneer600's second LED on P4; its buzzer on P7 sounds "
                            + "while P7 is low, so leave P7 ticked."),
                    pins,
                    new FieldRow(
                            new Button("Read pins", e -> perform(() -> show(service.read(bus, address, 1)[0] & 0xFF))) {{
                                setId("i2c-expander-read");
                                addThemeVariants(ButtonVariant.LUMO_SMALL);
                            }},
                            new Button("Write pins", e -> perform(() -> {
                                service.write(bus, address, pins.composed());
                                show(pins.composed());
                            })) {{
                                setId("i2c-expander-write");
                                addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                            }},
                            value));
            setVisible(false);
        }

        void show(Integer bus, int address) {
            this.bus = bus;
            this.address = address;
            boolean expander = I2cService.isPortExpander(address);
            setVisible(expander);
            if (expander) {
                show(0xFF);
                value.setText("");
            }
        }

        private void show(int pinsByte) {
            pins.show(pinsByte);
            value.setText("= " + I2cService.hex(pinsByte));
        }

        private void perform(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException failure) {
                LOG.warn("I2C expander operation failed", failure);
                Notification.show(failure.getMessage(), 6000, Notification.Position.MIDDLE);
            }
        }

        /** P0–P7 as checkboxes, ticked for high. */
        static class PinToggles extends FieldRow {
            private final List<Checkbox> pins = new ArrayList<>();

            PinToggles() {
                for (int i = 0; i < 8; i++) {
                    var pin = new Checkbox("P" + i);
                    pin.setId("i2c-pin-" + i);
                    pins.add(pin);
                    add(pin);
                }
            }

            void show(int pinsByte) {
                for (int i = 0; i < 8; i++) {
                    pins.get(i).setValue((pinsByte & (1 << i)) != 0);
                }
            }

            int composed() {
                int byteValue = 0;
                for (int i = 0; i < 8; i++) {
                    if (Boolean.TRUE.equals(pins.get(i).getValue())) {
                        byteValue |= 1 << i;
                    }
                }
                return byteValue;
            }
        }
    }

    static int parseHex(String text, int max, String what) {
        String value = text == null ? "" : text.trim().toLowerCase();
        if (value.startsWith("0x")) {
            value = value.substring(2);
        }
        try {
            int parsed = Integer.parseInt(value, 16);
            if (parsed < 0 || parsed > max) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be hex between 00 and %02X.".formatted(max));
        }
    }

    /** Classic hex dump: offset, 16 bytes in hex, then the printable ASCII. */
    static String format(int startRegister, byte[] data) {
        if (data.length == 0) {
            return "(no data)";
        }
        var out = new StringBuilder();
        for (int row = 0; row < data.length; row += 16) {
            out.append("%02X:".formatted((startRegister + row) & 0xFF));
            var ascii = new StringBuilder();
            for (int i = row; i < row + 16; i++) {
                if (i < data.length) {
                    int b = data[i] & 0xFF;
                    out.append(" %02X".formatted(b));
                    ascii.append(b >= 0x20 && b < 0x7F ? (char) b : '.');
                } else {
                    out.append("   ");
                }
                if (i == row + 7) {
                    out.append(' ');
                }
            }
            out.append("  |").append(ascii).append("|\n");
        }
        return out.toString();
    }
}
