package in.virit.iot.pihelpers;

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
    private final Select<Channel> channel = new Select<>();
    private final RadioButtonGroup<Mode> mode = new RadioButtonGroup<>("Signal", List.of(Mode.values()));
    private final ServoControls servo = new ServoControls();
    private final DutyControls duty = new DutyControls();
    private final Switch enabled = new Switch("Output enabled");
    private final Paragraph status = new Paragraph();
    private final SetupHint setup = PiSetup.pwm();
    private final SimulationBanner simulation = new SimulationBanner(
            "This PWM chip is a fake: the numbers below are what a real one would be told, but no pin moves.");
    private final PinReport pins = new PinReport();
    private boolean updating;

    public PwmPanel(PwmService service) {
        this.service = service;
        addClassName("pwm-panel");
        status.setId("pwm-status");
        channel.setLabel("PWM channel");
        channel.setItemLabelGenerator(Channel::label);
        var all = service.channels();
        // Only channels that reach a pin are offered when the host can tell; the rest would drive nothing
        var routed = all.stream().filter(c -> c.gpio() != null).toList();
        var channels = routed.isEmpty() ? all : routed;
        channel.setItems(channels);
        if (!channels.isEmpty()) {
            channel.setValue(channels.get(0));
        }
        channel.addValueChangeListener(e -> showState());
        mode.setValue(Mode.SERVO);
        mode.setItemLabelGenerator(m -> m == Mode.SERVO ? "Servo pulse" : "Duty cycle");
        mode.addValueChangeListener(e -> {
            servo.setVisible(e.getValue() == Mode.SERVO);
            duty.setVisible(e.getValue() == Mode.DUTY);
            if (e.isFromClient() && enabled.getValue()) {
                apply();
            }
        });
        duty.setVisible(false);
        enabled.addValueChangeListener(e -> {
            if (!updating && e.isFromClient()) {
                if (e.getValue()) {
                    apply();
                } else {
                    perform(() -> service.disable(channel.getValue()));
                }
            }
        });

        pins.show(service.pins(), all.size() - channels.size());
        var controls = new Card();
        controls.setWidthFull();
        controls.add(channel, pins, mode, servo, duty, enabled, status);
        add(new H1("PWM & servo"),
                new Paragraph("Position a hobby servo or dim an LED with a hardware PWM channel. "
                        + "Servos take the signal wire (orange or yellow) from the PWM pin, red to 5 V and brown or "
                        + "black to GND; one small servo such as an SG90 can run from the Pi's 5 V pin, anything bigger "
                        + "wants its own supply with a shared GND. The servo needs no pull-up or resistor, and 3.3 V "
                        + "on the signal wire is fine. A servo uses one channel; the second channel of pwm-2chan is "
                        + "only for a second device."),
                simulation, controls, setup);
        simulation.setVisible(service.isSimulated());
        if (channels.isEmpty()) {
            enabled.setEnabled(false);
            status.setText("No PWM chip found under /sys/class/pwm. Hardware PWM is not enabled on this host; "
                    + "the steps below fix that.");
            setup.setOpened(true);
        } else {
            showState();
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
                var parts = new java.util.ArrayList<String>();
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
        status.setText((service.isSimulated() ? "Simulation · " : "") + selected.key() + " · "
                + (state.enabled() ? "on · " : "off · ") + signal);
    }

    class ServoControls extends VerticalLayout {
        private final IntegerSlider angle = new IntegerSlider("Angle", 0, 180);
        private final IntegerField pulse = new IntegerField("Pulse width (µs)");
        private final IntegerField minPulse = new IntegerField("0° pulse (µs)");
        private final IntegerField maxPulse = new IntegerField("180° pulse (µs)");
        private boolean syncing;

        ServoControls() {
            setPadding(false);
            angle.setValue(90);
            angle.setWidthFull();
            angle.setId("servo-angle");
            pulse.setValue(1500);
            pulse.setMin(0);
            pulse.setMax(20000);
            pulse.setStep(10);
            pulse.setStepButtonsVisible(true);
            pulse.setWidth("9em");
            minPulse.setValue(500);
            maxPulse.setValue(2500);
            minPulse.setMin(0);
            maxPulse.setMin(0);
            minPulse.setMax(20000);
            maxPulse.setMax(20000);
            minPulse.setWidth("8em");
            maxPulse.setWidth("8em");
            minPulse.setHelperText("Common: 500–1000");
            maxPulse.setHelperText("Common: 2000–2500");
            angle.addValueChangeListener(e -> {
                if (!syncing) {
                    syncing = true;
                    pulse.setValue(pulseFor(e.getValue()));
                    syncing = false;
                    changed(e.isFromClient());
                }
            });
            pulse.addValueChangeListener(e -> {
                if (!syncing && e.getValue() != null) {
                    syncing = true;
                    angle.setValue(angleFor(e.getValue()));
                    syncing = false;
                    changed(e.isFromClient());
                }
            });
            minPulse.addValueChangeListener(e -> changed(e.isFromClient()));
            maxPulse.addValueChangeListener(e -> changed(e.isFromClient()));

            var presets = new HorizontalLayout();
            presets.addClassName("pwm-presets");
            for (int preset : new int[]{0, 45, 90, 135, 180}) {
                var button = new Button(preset + "°", e -> {
                    angle.setValue(preset);
                    changed(true);
                });
                button.setId("servo-" + preset);
                button.addThemeVariants(ButtonVariant.LUMO_SMALL);
                presets.add(button);
            }
            var calibration = new FlexLayout(pulse, minPulse, maxPulse);
            calibration.addClassName("pwm-fields");
            add(angle, presets, calibration,
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
    }

    class DutyControls extends VerticalLayout {
        private final IntegerSlider percent = new IntegerSlider("Duty cycle (%)", 0, 100);
        private final IntegerField frequency = new IntegerField("Frequency (Hz)");

        DutyControls() {
            setPadding(false);
            percent.setValue(50);
            percent.setWidthFull();
            percent.setId("pwm-duty");
            frequency.setValue(1000);
            frequency.setMin(1);
            frequency.setMax(10_000_000);
            frequency.setWidth("9em");
            frequency.setHelperText("LED dimming: 1 kHz or more; DC motor drivers: see their datasheet.");
            percent.addValueChangeListener(e -> changed(e.isFromClient()));
            frequency.addValueChangeListener(e -> changed(e.isFromClient()));
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
    }
}
