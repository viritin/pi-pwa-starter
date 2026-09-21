package in.virit.iot.pihelpers;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H4;
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
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * i2cdetect in the browser, plus a register dump and a single register write
 * for the selected device. No route or menu entry; the application adds those.
 */
@StyleSheet("styles/pi-helpers-i2c.css")
public class I2cPanel extends VerticalLayout {

    private static final Logger LOG = Logger.getLogger(I2cPanel.class);

    private final I2cService service;
    private final Select<Integer> bus = new Select<>();
    private final AddressGrid grid = new AddressGrid();
    private final DeviceTools tools = new DeviceTools();
    private final Paragraph status = new Paragraph();
    private final SetupHint setup = PiSetup.i2c();
    private final SimulationBanner simulation = new SimulationBanner(
            "This bus and the devices on it are made up; register reads and writes go to an in-memory copy.");
    private final WiringHint wiring = new WiringHint();
    private Set<Integer> found = Set.of();

    public I2cPanel(I2cService service) {
        this.service = service;
        addClassName("i2c-panel");
        status.setId("i2c-status");
        bus.setLabel("Bus");
        bus.setItemLabelGenerator(n -> "i2c-" + n);
        var buses = service.buses();
        bus.setItems(buses);
        bus.addValueChangeListener(e -> wiring.show(e.getValue(), service.isSimulated()));
        if (!buses.isEmpty()) {
            bus.setValue(buses.get(0));
        }
        var scan = new Button("Scan bus", e -> scan());
        scan.setId("i2c-scan");
        scan.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        scan.setEnabled(!buses.isEmpty());
        var toolbar = new HorizontalLayout(bus, scan);
        toolbar.setAlignItems(Alignment.BASELINE);

        add(new H1("I²C"),
                new Paragraph("Find what answers on the bus, then tap an address to read its registers and check "
                        + "the wiring and the datasheet before writing any driver code. A PCF8574 port expander "
                        + "(0x20–0x27, 0x38–0x3F) gets pin toggles instead, so its LEDs, relays and buttons can be "
                        + "tried without code."),
                simulation, toolbar, wiring, grid, status, tools, setup);
        simulation.setVisible(service.isSimulated());
        tools.setVisible(false);
        if (buses.isEmpty()) {
            status.setText("No /dev/i2c-* device found. The bus is not enabled on this host; the steps below fix that.");
            setup.setOpened(true);
        } else {
            status.setText((service.isSimulated() ? "Simulation · " : "") + "Not scanned yet");
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
            String summary = (service.isSimulated() ? "Simulation · " : "")
                    + found.size() + (found.size() == 1 ? " device" : " devices") + " on i2c-" + selected;
            if (!scan.inUse().isEmpty()) {
                summary += " · " + scan.inUse().stream().map(I2cService::hex).collect(java.util.stream.Collectors.joining(", "))
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
        WiringHint() {
            setId("i2c-wiring");
            addClassName("i2c-wiring");
        }

        void show(Integer number, boolean simulated) {
            if (number == null) {
                setText("");
                return;
            }
            var pins = simulated ? new I2cPins.Bus(number, 2, 3, false) : I2cPins.describe(number);
            var text = new StringBuilder();
            if (pins.known()) {
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
            } else {
                text.append("i2c-").append(number).append(" is an extra bus: its pins come from the dtoverlay line in "
                        + "/boot/firmware/config.txt (for example i2c5,pins_12_13). pinctrl (sudo apt install raspi-utils) "
                        + "would show which GPIOs it is on.");
            }
            text.append(" A device that stays silent is usually SDA and SCL swapped, VCC not connected, or a 5 V-only module.");
            setText(text.toString());
        }
    }

    class AddressGrid extends Div {
        private final Map<Integer, Span> cells = new HashMap<>();

        AddressGrid() {
            addClassName("i2c-grid");
            for (int row = 0; row < 8; row++) {
                var label = new Span("%02X".formatted(row * 16));
                label.addClassName("i2c-row");
                add(label);
                for (int col = 0; col < 16; col++) {
                    int address = row * 16 + col;
                    var cell = new Span(address < I2cService.FIRST_ADDRESS || address > I2cService.LAST_ADDRESS
                            ? "" : "%02X".formatted(address));
                    cell.addClassName("i2c-cell");
                    cell.setId("i2c-" + I2cService.hex(address));
                    cell.addClickListener(e -> {
                        if (found.contains(address)) {
                            select(address);
                        }
                    });
                    cells.put(address, cell);
                    add(cell);
                }
            }
        }

        void update(Set<Integer> found) {
            cells.forEach((address, cell) -> {
                boolean present = found.contains(address);
                cell.setClassName("found", present);
                cell.getElement().setAttribute("role", present ? "button" : "cell");
            });
        }
    }

    class DeviceTools extends Div {
        private final H4 title = new H4();
        private final TextField startRegister = new TextField("Start register (hex)", "00", "");
        private final IntegerField count = new IntegerField("Bytes");
        private final Pre dump = new Pre();
        private final TextField writeRegister = new TextField("Register (hex)", "00", "");
        private final TextField writeValue = new TextField("Value (hex)", "00", "");
        private final PortExpander expander = new PortExpander();
        private Integer bus;
        private int address;

        DeviceTools() {
            addClassName("panel");
            count.setValue(16);
            count.setMin(1);
            count.setMax(256);
            count.setStepButtonsVisible(true);
            startRegister.setWidth("9em");
            count.setWidth("7em");
            writeRegister.setWidth("9em");
            writeValue.setWidth("9em");
            var read = new Button("Read registers", e -> read(true));
            read.setId("i2c-read");
            read.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            var raw = new Button("Read without register", e -> read(false));
            raw.addThemeVariants(ButtonVariant.LUMO_SMALL);
            var write = new Button("Write", e -> confirmWrite());
            write.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            dump.addClassName("hexdump");
            dump.setId("i2c-dump");
            var readRow = new FlexLayout(startRegister, count, read, raw);
            readRow.addClassName("i2c-row-fields");
            var writeRow = new FlexLayout(writeRegister, writeValue, write);
            writeRow.addClassName("i2c-row-fields");
            add(title, expander,
                    new Paragraph("Register dumps assume the device auto-increments its register pointer, "
                            + "as most sensors do. Writes change the device state; keep the datasheet open."),
                    readRow, dump, new H4("Write one register"), writeRow);
        }

        void show(Integer bus, int address) {
            this.bus = bus;
            this.address = address;
            var hint = I2cService.hint(address);
            title.setText(I2cService.hex(address) + (hint != null ? " · " + hint + "?" : ""));
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
                int register = parseHex(writeRegister.getValue(), 0xFF, "Register");
                int value = parseHex(writeValue.getValue(), 0xFF, "Value");
                var dialog = new ConfirmDialog("Write to " + I2cService.hex(address) + "?",
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
                dialog.setCancelable(true);
                dialog.setConfirmButtonTheme("error primary");
                dialog.open();
            } catch (IllegalArgumentException invalid) {
                Notification.show(invalid.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        }
    }

    /**
     * Pin toggles for a PCF8574-style expander, which has no registers: a read
     * returns its eight pins and a written byte lands on them. Shown for the
     * addresses those chips can have; the register tools below still work but
     * their register byte is driven onto the pins too.
     */
    class PortExpander extends Div {
        private final List<Checkbox> pins = new java.util.ArrayList<>();
        private final Span value = new Span();
        private Integer bus;
        private int address;

        PortExpander() {
            addClassName("i2c-expander");
            var row = new FlexLayout();
            row.addClassName("i2c-row-fields");
            for (int i = 0; i < 8; i++) {
                var pin = new Checkbox("P" + i);
                pin.setId("i2c-pin-" + i);
                pins.add(pin);
                row.add(pin);
            }
            value.addClassName("i2c-expander-value");
            var read = new Button("Read pins", e -> perform(() -> show(service.read(bus, address, 1)[0] & 0xFF)));
            read.setId("i2c-expander-read");
            read.addThemeVariants(ButtonVariant.LUMO_SMALL);
            var write = new Button("Write pins", e -> perform(() -> {
                service.write(bus, address, composed());
                show(composed());
            }));
            write.setId("i2c-expander-write");
            write.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            var buttons = new FlexLayout(read, write, value);
            buttons.addClassName("i2c-row-fields");
            add(new H4("Pins"),
                    new Paragraph("A PCF8574 has no registers: a read returns its eight pins and a written byte "
                            + "sets them, high by default. Ticked is high. A LED between 3.3 V and a pin lights when "
                            + "the pin is low, as with the Pioneer600's second LED on P4; its buzzer on P7 sounds "
                            + "while P7 is low, so leave P7 ticked."),
                    row, buttons);
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
            for (int i = 0; i < 8; i++) {
                pins.get(i).setValue((pinsByte & (1 << i)) != 0);
            }
            value.setText("= " + I2cService.hex(pinsByte));
        }

        private int composed() {
            int byteValue = 0;
            for (int i = 0; i < 8; i++) {
                if (Boolean.TRUE.equals(pins.get(i).getValue())) {
                    byteValue |= 1 << i;
                }
            }
            return byteValue;
        }

        private void perform(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException failure) {
                LOG.warn("I2C expander operation failed", failure);
                Notification.show(failure.getMessage(), 6000, Notification.Position.MIDDLE);
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
