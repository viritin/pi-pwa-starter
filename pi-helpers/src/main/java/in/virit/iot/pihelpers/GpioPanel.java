package in.virit.iot.pihelpers;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Switch;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.shared.Registration;
import in.virit.iot.pihelpers.GpioService.Mode;
import in.virit.iot.pihelpers.GpioService.Pin;
import in.virit.iot.pihelpers.GpioService.Pull;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The 40-pin header as a tappable map. Tap a GPIO to make it an input or an
 * output, drive outputs, and watch inputs change live. No route or menu entry;
 * the application adds those.
 */
@StyleSheet("styles/pi-helpers-gpio.css")
public class GpioPanel extends VerticalLayout {

    private static final Logger LOG = Logger.getLogger(GpioPanel.class);

    enum Kind { POWER_3V3, POWER_5V, GROUND, GPIO }

    /** One physical header pin. {@code bcm} is null for power and ground. */
    record HeaderPin(int physical, Kind kind, Integer bcm, String function) {
        String label() {
            return switch (kind) {
                case POWER_3V3 -> "3V3";
                case POWER_5V -> "5V";
                case GROUND -> "GND";
                case GPIO -> "GPIO" + bcm + (function == null ? "" : " · " + function);
            };
        }
    }

    /** Raspberry Pi 40-pin J8 header in physical order: odd pins left, even pins right. */
    static final List<HeaderPin> HEADER = List.of(
            power3v3(1), power5v(2),
            gpio(3, 2, "SDA1"), power5v(4),
            gpio(5, 3, "SCL1"), ground(6),
            gpio(7, 4, "GPCLK0"), gpio(8, 14, "TXD"),
            ground(9), gpio(10, 15, "RXD"),
            gpio(11, 17, null), gpio(12, 18, "PWM0"),
            gpio(13, 27, null), ground(14),
            gpio(15, 22, null), gpio(16, 23, null),
            power3v3(17), gpio(18, 24, null),
            gpio(19, 10, "MOSI"), ground(20),
            gpio(21, 9, "MISO"), gpio(22, 25, null),
            gpio(23, 11, "SCLK"), gpio(24, 8, "CE0"),
            ground(25), gpio(26, 7, "CE1"),
            gpio(27, 0, "ID_SD"), gpio(28, 1, "ID_SC"),
            gpio(29, 5, null), ground(30),
            gpio(31, 6, null), gpio(32, 12, "PWM0"),
            gpio(33, 13, "PWM1"), ground(34),
            gpio(35, 19, "PWM1"), gpio(36, 16, null),
            gpio(37, 26, null), gpio(38, 20, "PCM_DIN"),
            ground(39), gpio(40, 21, "PCM_DOUT"));

    private static HeaderPin gpio(int physical, int bcm, String function) {
        return new HeaderPin(physical, Kind.GPIO, bcm, function);
    }

    private static HeaderPin ground(int physical) {
        return new HeaderPin(physical, Kind.GROUND, null, null);
    }

    private static HeaderPin power3v3(int physical) {
        return new HeaderPin(physical, Kind.POWER_3V3, null, null);
    }

    private static HeaderPin power5v(int physical) {
        return new HeaderPin(physical, Kind.POWER_5V, null, null);
    }

    static int physicalPin(int bcm) {
        return HEADER.stream().filter(p -> p.bcm() != null && p.bcm() == bcm)
                .findFirst().map(HeaderPin::physical).orElse(-1);
    }

    private final GpioService service;
    private final HeaderMap header = new HeaderMap();
    private final Paragraph status = new Paragraph();
    private Registration listener;
    private PinDialog openDialog;

    public GpioPanel(GpioService service) {
        this.service = service;
        addClassName("gpio-panel");
        status.setId("gpio-status");
        add(new H1("GPIO"),
                new Paragraph("Tap a GPIO to read it as an input or drive it as an output. "
                        + "Pins are BCM numbers; the small number is the physical header pin, "
                        + "pin 1 at the top left with the SD card slot facing up."),
                new Paragraph("Outputs give 3.3 V and a few milliamps at most. Never connect 5 V to a GPIO."),
                header, status, new Actions());
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();
        listener = service.addListener(pin -> ui.access(() -> {
            if (isAttached()) {
                refresh();
            }
        }));
        refresh();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    private void refresh() {
        var pins = service.pins();
        header.update(pins);
        long configured = pins.stream().filter(Pin::configured).count();
        status.setText((service.isSimulated() ? "Simulation · " : "")
                + configured + (configured == 1 ? " pin configured" : " pins configured"));
        if (openDialog != null && openDialog.isOpened()) {
            openDialog.update(service.pin(openDialog.bcm));
        }
    }

    private void perform(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            LOG.warn("GPIO operation failed", failure);
            Notification.show(failure.getMessage() != null ? failure.getMessage()
                    : "GPIO operation failed. Check the device and permissions.", 5000, Notification.Position.MIDDLE);
        }
        refresh();
    }

    class HeaderMap extends Div {
        private final Map<Integer, PinCell> cells = new HashMap<>();

        HeaderMap() {
            addClassName("gpio-header");
            for (var pin : HEADER) {
                var cell = new PinCell(pin);
                if (pin.bcm() != null) {
                    cells.put(pin.bcm(), cell);
                }
                add(cell);
            }
        }

        void update(List<Pin> pins) {
            for (var pin : pins) {
                var cell = cells.get(pin.bcm());
                if (cell != null) {
                    cell.update(pin);
                }
            }
        }
    }

    class PinCell extends Div {
        private final Span state = new Span();

        PinCell(HeaderPin pin) {
            addClassName("pin");
            addClassName(switch (pin.kind()) {
                case POWER_3V3 -> "pin-power-3v3";
                case POWER_5V -> "pin-power-5v";
                case GROUND -> "pin-ground";
                case GPIO -> "pin-gpio";
            });
            setId("pin-" + pin.physical());
            var number = new Span(String.valueOf(pin.physical()));
            number.addClassName("pin-number");
            var label = new Span(pin.label());
            label.addClassName("pin-label");
            state.addClassName("pin-state");
            add(number, label, state);
            if (pin.kind() == Kind.GPIO) {
                getElement().setAttribute("role", "button");
                getElement().setAttribute("tabindex", "0");
                getElement().setAttribute("aria-label", "GPIO " + pin.bcm());
                addClickListener(e -> openPin(pin.bcm()));
            }
        }

        void update(Pin pin) {
            setClassName("pin-in", pin.mode() == Mode.INPUT);
            setClassName("pin-out", pin.mode() == Mode.OUTPUT);
            setClassName("pin-high", pin.configured() && pin.high());
            setClassName("pin-external", pin.external());
            state.setText(pin.external() ? "APP"
                    : switch (pin.mode()) {
                        case UNUSED -> "";
                        case INPUT -> "IN · " + (pin.high() ? "H" : "L");
                        case OUTPUT -> "OUT · " + (pin.high() ? "H" : "L");
                    });
            state.setVisible(!state.getText().isEmpty());
        }
    }

    private void openPin(int bcm) {
        openDialog = new PinDialog(bcm);
        openDialog.open();
    }

    class PinDialog extends Dialog {
        final int bcm;
        private final RadioButtonGroup<Mode> mode = new RadioButtonGroup<>("Mode", List.of(Mode.values()));
        private final RadioButtonGroup<Pull> pull = new RadioButtonGroup<>("Pull resistor", List.of(Pull.values()));
        private final Switch drive = new Switch("Drive HIGH (3.3 V)");
        private final HorizontalLayout simulate = new HorizontalLayout();
        private final Paragraph level = new Paragraph();
        private final Paragraph external = new Paragraph("This pin is used by another part of the application, "
                + "for example the LED example. Release it there first.");
        private boolean updating;

        PinDialog(int bcm) {
            this.bcm = bcm;
            setHeaderTitle("GPIO" + bcm + " · header pin " + physicalPin(bcm));
            mode.setItemLabelGenerator(m -> switch (m) {
                case UNUSED -> "Not used";
                case INPUT -> "Input";
                case OUTPUT -> "Output";
            });
            pull.setItemLabelGenerator(p -> switch (p) {
                case OFF -> "None (floating)";
                case UP -> "Pull-up";
                case DOWN -> "Pull-down";
            });
            pull.setHelperText("With a pull-up, a button between the pin and GND reads LOW when pressed.");
            mode.addValueChangeListener(e -> {
                if (!updating && e.isFromClient()) {
                    perform(() -> service.configure(bcm, e.getValue(), pull.getValue()));
                }
            });
            pull.addValueChangeListener(e -> {
                if (!updating && e.isFromClient()) {
                    perform(() -> service.configure(bcm, mode.getValue(), e.getValue()));
                }
            });
            drive.addValueChangeListener(e -> {
                if (!updating && e.isFromClient()) {
                    perform(() -> service.write(bcm, e.getValue()));
                }
            });
            var high = new Button("Simulate HIGH", e -> perform(() -> service.simulateInput(bcm, true)));
            var low = new Button("Simulate LOW", e -> perform(() -> service.simulateInput(bcm, false)));
            high.addThemeVariants(ButtonVariant.LUMO_SMALL);
            low.addThemeVariants(ButtonVariant.LUMO_SMALL);
            simulate.add(high, low);
            level.setId("pin-level");
            add(new VerticalLayout(external, mode, pull, drive, simulate, level));
            var close = new Button("Close", e -> close());
            getFooter().add(close);
            update(service.pin(bcm));
        }

        void update(Pin pin) {
            updating = true;
            try {
                external.setVisible(pin.external());
                mode.setVisible(!pin.external());
                mode.setValue(pin.mode());
                pull.setVisible(pin.mode() == Mode.INPUT);
                pull.setValue(pin.mode() == Mode.INPUT ? pin.pull() : Pull.OFF);
                drive.setVisible(pin.mode() == Mode.OUTPUT);
                drive.setValue(pin.mode() == Mode.OUTPUT && pin.high());
                simulate.setVisible(service.isSimulated() && pin.mode() == Mode.INPUT);
                level.setVisible(pin.configured());
                level.setText("Level: " + (pin.high() ? "HIGH" : "LOW"));
            } finally {
                updating = false;
            }
        }
    }

    class Actions extends HorizontalLayout {
        Actions() {
            var release = new Button("Release all pins", e -> new ConfirmDialog("Release all pins?",
                    "Outputs go LOW and every pin configured on this screen is freed.",
                    "Release", confirm -> perform(service::releaseAll)) {{
                setCancelable(true);
                open();
            }});
            release.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            add(release);
        }
    }
}
