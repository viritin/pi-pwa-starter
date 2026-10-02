package in.virit.iot.pihelpers;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Switch;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.slider.IntegerSlider;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.card.Card;
import in.virit.iot.pihelpers.PwmService.Channel;
import in.virit.iot.pihelpers.PwmService.State;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Drive a hardware PWM channel either as a servo (pulse width in µs at 50 Hz)
 * or as a plain duty cycle for dimming and motor speed. No route or menu
 * entry; the application adds those.
 */
@StyleSheet("styles/pi-helpers-pwm.css")
public class PwmPanel extends VerticalLayout {

    private static final Logger LOG = Logger.getLogger(PwmPanel.class);

    enum Mode { SERVO, DUTY }

    private final PwmService service;
    private final ChannelSelect channel = new ChannelSelect();
    private final ModeSelect mode = new ModeSelect();
    private final ServoControls servo = new ServoControls();
    private final DutyControls duty = new DutyControls();
    private final EnabledSwitch enabled = new EnabledSwitch();
    private final Paragraph status = new Paragraph("Looking for PWM channels…");
    private final SetupHint setup = PiSetup.pwm();
    private final SimulationBanner simulation = new SimulationBanner(
            "This PWM chip is a fake: the numbers below are what a real one would be told, but no pin moves.");
    private final PinReport pins = new PinReport();
    private boolean updating;

    /**
     * Only the frame is built here. The channels and where they are routed come
     * from sysfs and pinctrl, a process of its own, so they are read in the
     * background once the panel is attached and arrive over push.
     */
    public PwmPanel(PwmService service) {
        this.service = service;
        addClassName("pwm-panel");
        status.setId("pwm-status");
        duty.setVisible(false);
        enabled.setEnabled(false);
        add(new H1("PWM & servo"),
                new Paragraph("Position a hobby servo or dim an LED with a hardware PWM channel. "
                        + "Servos take the signal wire (orange or yellow) from the PWM pin, red to 5 V and brown or "
                        + "black to GND; one small servo such as an SG90 can run from the Pi's 5 V pin, anything bigger "
                        + "wants its own supply with a shared GND. The servo needs no pull-up or resistor, and 3.3 V "
                        + "on the signal wire is fine. A servo uses one channel; the second channel of pwm-2chan is "
                        + "only for a second device."),
                simulation, new OutputCard(), setup);
        simulation.setVisible(Simulated.is(service));
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        var ui = attachEvent.getUI();
        Thread.ofVirtual().name("pwm-panel-probe").start(() -> {
            List<Channel> all;
            PwmPins.Report report;
            try {
                all = service.channels();
                report = service.pins();
            } catch (RuntimeException failure) {
                LOG.warn("Reading the PWM channels failed", failure);
                all = List.of();
                report = null;
            }
            var channels = all;
            var routing = report;
            ui.access(() -> {
                if (isAttached()) {
                    showChannels(channels, routing);
                }
            });
        });
    }

    private void showChannels(List<Channel> all, PwmPins.Report report) {
        // Only channels that reach a pin are offered when the host can tell; the rest would drive nothing
        var routed = all.stream().filter(c -> c.gpio() != null).toList();
        var channels = routed.isEmpty() ? all : routed;
        channel.setItems(channels);
        if (report != null) {
            pins.show(report, all.size() - channels.size());
        }
        if (channels.isEmpty()) {
            enabled.setEnabled(false);
            status.setText("No PWM chip found under /sys/class/pwm. Hardware PWM is not enabled on this host; "
                    + "the steps below fix that.");
            setup.setOpened(true);
        } else {
            enabled.setEnabled(true);
            channel.setValue(channels.get(0));
            showState();
        }
    }

    /** The channel, where it is routed, the signal and the switch that sends it. */
    class OutputCard extends Card {
        OutputCard() {
            setTitle("Output");
            setWidthFull();
            add(channel, pins, mode, servo, duty, enabled, status);
        }
    }

    class ChannelSelect extends Select<Channel> {
        ChannelSelect() {
            setLabel("PWM channel");
            setItemLabelGenerator(Channel::label);
            addValueChangeListener(e -> showState());
        }
    }

    class ModeSelect extends RadioButtonGroup<Mode> {
        ModeSelect() {
            super("Signal", List.of(Mode.values()));
            setValue(Mode.SERVO);
            setItemLabelGenerator(m -> m == Mode.SERVO ? "Servo pulse" : "Duty cycle");
            addValueChangeListener(e -> {
                servo.setVisible(e.getValue() == Mode.SERVO);
                duty.setVisible(e.getValue() == Mode.DUTY);
                if (e.isFromClient() && enabled.getValue()) {
                    apply();
                }
            });
        }
    }

    class EnabledSwitch extends Switch {
        EnabledSwitch() {
            super("Output enabled");
            addValueChangeListener(e -> {
                if (!updating && e.isFromClient()) {
                    if (e.getValue()) {
                        apply();
                    } else {
                        perform(() -> service.disable(channel.getValue()));
                    }
                }
            });
        }
    }

    /**
     * Where the PWM channels come out, as pinctrl reports it after boot, next to
     * the config.txt line that asked for it. Saves guessing which header pin the
     * servo wire goes to.
     */
    static class PinReport extends Paragraph {
        PinReport() {
            setId("pwm-pins");
            addClassName("pwm-pins");
        }

        void show(PwmPins.Report report, int hiddenChannels) {
            var text = new StringBuilder();
            if (report.tool() == null) {
                text.append("Pin functions unknown: neither pinctrl nor raspi-gpio is installed (sudo apt install raspi-utils).");
            } else {
                text.append("Pins now (").append(report.tool()).append("): ");
                var parts = new ArrayList<String>();
                report.functions().forEach((gpio, function) -> parts.add("GPIO" + gpio + " = " + function));
                text.append(String.join(", ", parts));
                if (report.nothingRouted()) {
                    text.append(". No GPIO is set to PWM, so the channels reach no pin; check the overlay line.");
                }
            }
            if (!report.overlays().isEmpty()) {
                text.append(" · config.txt: ").append(String.join("; ", report.overlays()));
            } else if (report.tool() != null) {
                text.append(" · config.txt has no dtoverlay=pwm line.");
            }
            if (hiddenChannels > 0) {
                text.append(" · ").append(hiddenChannels).append(hiddenChannels == 1 ? " channel" : " channels")
                        .append(" with no pin not listed.");
            }
            setText(text.toString());
        }
    }

    private void apply() {
        Channel selected = channel.getValue();
        if (selected == null) {
            return;
        }
        perform(() -> {
            if (mode.getValue() == Mode.SERVO) {
                service.apply(selected, 50, servo.pulseMicros() * 1000L);
            } else {
                int frequency = duty.frequencyHz();
                long period = 1_000_000_000L / frequency;
                service.apply(selected, frequency, Math.round(period * duty.percent() / 100.0));
            }
        });
    }

    private void perform(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            LOG.warn("PWM operation failed", failure);
            Notification.show(failure.getMessage() != null ? failure.getMessage() : "PWM operation failed.",
                    6000, Notification.Position.MIDDLE);
        }
        showState();
    }

    private void showState() {
        Channel selected = channel.getValue();
        if (selected == null) {
            return;
        }
        State state = service.state(selected);
        updating = true;
        try {
            enabled.setValue(state.enabled());
        } finally {
            updating = false;
        }
        String signal = state.periodNanos() <= 0 ? "no signal programmed"
                : String.format(Locale.ROOT, "%d Hz · %d µs high (%.1f %%)",
                        state.frequencyHz(), state.pulseMicros(), state.dutyPercent());
        status.setText(Simulated.prefix(service) + selected.key() + " · "
                + (state.enabled() ? "on · " : "off · ") + signal);
    }

    class ServoControls extends VerticalLayout {
        private final AngleSlider angle = new AngleSlider();
        private final PulseField pulse = new PulseField();
        private final EndPointField minPulse = new EndPointField("0° pulse (µs)", 500, "Common: 500–1000");
        private final EndPointField maxPulse = new EndPointField("180° pulse (µs)", 2500, "Common: 2000–2500");
        private boolean syncing;

        ServoControls() {
            setPadding(false);
            add(angle, new Presets(), new Calibration(),
                    new Paragraph("Servos expect a 50 Hz signal and a pulse of roughly 1–2 ms; the end points vary "
                            + "per servo, so adjust them if it buzzes at the extremes."));
        }

        int pulseMicros() {
            return pulse.getValue() == null ? 1500 : pulse.getValue();
        }

        private int pulseFor(int angle) {
            int min = minPulse.getValue() == null ? 500 : minPulse.getValue();
            int max = maxPulse.getValue() == null ? 2500 : maxPulse.getValue();
            return (int) Math.round(min + (max - min) * angle / 180.0);
        }

        private int angleFor(int pulse) {
            int min = minPulse.getValue() == null ? 500 : minPulse.getValue();
            int max = maxPulse.getValue() == null ? 2500 : maxPulse.getValue();
            if (max == min) {
                return 90;
            }
            return (int) Math.max(0, Math.min(180, Math.round(180.0 * (pulse - min) / (max - min))));
        }

        private void changed(boolean fromClient) {
            if (fromClient && enabled.getValue()) {
                apply();
            }
        }

        /** The angle, kept in step with the pulse width it stands for. */
        class AngleSlider extends IntegerSlider {
            AngleSlider() {
                super("Angle", 0, 180);
                setValue(90);
                setWidthFull();
                setId("servo-angle");
                addValueChangeListener(e -> {
                    if (!syncing) {
                        syncing = true;
                        pulse.setValue(pulseFor(e.getValue()));
                        syncing = false;
                        changed(e.isFromClient());
                    }
                });
            }
        }

        /** The pulse width itself, kept in step with the angle. */
        class PulseField extends IntegerField {
            PulseField() {
                super("Pulse width (µs)");
                setValue(1500);
                setMin(0);
                setMax(20000);
                setStep(10);
                setStepButtonsVisible(true);
                setWidth("9em");
                addValueChangeListener(e -> {
                    if (!syncing && e.getValue() != null) {
                        syncing = true;
                        angle.setValue(angleFor(e.getValue()));
                        syncing = false;
                        changed(e.isFromClient());
                    }
                });
            }
        }

        /** The pulse width the servo takes for one end of its travel. */
        class EndPointField extends IntegerField {
            EndPointField(String label, int value, String helperText) {
                super(label);
                setValue(value);
                setMin(0);
                setMax(20000);
                setWidth("8em");
                setHelperText(helperText);
                addValueChangeListener(e -> changed(e.isFromClient()));
            }
        }

        class Presets extends HorizontalLayout {
            Presets() {
                addClassName("pwm-presets");
                for (int preset : new int[]{0, 45, 90, 135, 180}) {
                    add(new PresetButton(preset));
                }
            }

            class PresetButton extends Button {
                PresetButton(int preset) {
                    super(preset + "°", e -> {
                        angle.setValue(preset);
                        changed(true);
                    });
                    setId("servo-" + preset);
                    addThemeVariants(ButtonVariant.LUMO_SMALL);
                }
            }
        }

        class Calibration extends FlexLayout {
            Calibration() {
                super(pulse, minPulse, maxPulse);
                addClassName("pwm-fields");
            }
        }
    }

    class DutyControls extends VerticalLayout {
        private final DutySlider percent = new DutySlider();
        private final FrequencyField frequency = new FrequencyField();

        DutyControls() {
            setPadding(false);
            add(percent, frequency);
        }

        int percent() {
            return percent.getValue() == null ? 0 : percent.getValue();
        }

        int frequencyHz() {
            return frequency.getValue() == null || frequency.getValue() < 1 ? 1000 : frequency.getValue();
        }

        private void changed(boolean fromClient) {
            if (fromClient && enabled.getValue()) {
                apply();
            }
        }

        class DutySlider extends IntegerSlider {
            DutySlider() {
                super("Duty cycle (%)", 0, 100);
                setValue(50);
                setWidthFull();
                setId("pwm-duty");
                addValueChangeListener(e -> changed(e.isFromClient()));
            }
        }

        class FrequencyField extends IntegerField {
            FrequencyField() {
                super("Frequency (Hz)");
                setValue(1000);
                setMin(1);
                setMax(10_000_000);
                setWidth("9em");
                setHelperText("LED dimming: 1 kHz or more; DC motor drivers: see their datasheet.");
                addValueChangeListener(e -> changed(e.isFromClient()));
            }
        }
    }
}
