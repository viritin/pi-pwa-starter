package in.virit.iot.pihelpers.simulation;

import in.virit.iot.pihelpers.PwmPins;
import in.virit.iot.pihelpers.PwmService;
import in.virit.iot.pihelpers.Simulated;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Alternative
@Priority(1)
@ApplicationScoped
public class SimulatedPwmService extends PwmService implements Simulated {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private final Map<String, State> states = new HashMap<>();

    @Override
    public PwmPins.Report pins() {
        return new PwmPins.Report(Map.of(12, "input", 13, "input", 18, "PWM0_0", 19, "PWM0_1"),
                List.of("dtoverlay=pwm-2chan"), "pinctrl");
    }

    @Override
    public List<Channel> channels() {
        return List.of(new Channel(0, 0, 18, "GPIO18"), new Channel(0, 1, 19, "GPIO19"));
    }

    @Override
    public synchronized State state(Channel channel) {
        return states.getOrDefault(channel.key(), State.OFF);
    }

    @Override
    public synchronized State apply(Channel channel, int frequencyHz, long dutyNanos) {
        if (frequencyHz < 1 || frequencyHz > 10_000_000) {
            throw new IllegalArgumentException("Frequency must be 1 Hz to 10 MHz.");
        }
        long periodNanos = NANOS_PER_SECOND / frequencyHz;
        if (dutyNanos < 0 || dutyNanos > periodNanos) {
            throw new IllegalArgumentException("Pulse width must be between 0 and the period ("
                    + periodNanos / 1000 + " µs at " + frequencyHz + " Hz).");
        }
        var state = new State(true, periodNanos, dutyNanos);
        states.put(channel.key(), state);
        return state;
    }

    @Override
    public synchronized State disable(Channel channel) {
        var previous = state(channel);
        var state = new State(false, previous.periodNanos(), previous.dutyNanos());
        states.put(channel.key(), state);
        return state;
    }
}
