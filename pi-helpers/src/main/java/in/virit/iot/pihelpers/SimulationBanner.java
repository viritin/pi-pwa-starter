package in.virit.iot.pihelpers;

import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Span;

/**
 * Tells the user, above the data, that a panel is showing simulated hardware so
 * nobody mistakes a fabricated reading for a real one. Panels add it always and
 * show it only when their service simulates; the status line keeps its
 * "Simulation" prefix as a second reminder next to the numbers. A card like the
 * panel's others, tinted orange.
 */
@StyleSheet("styles/pi-helpers-simulation.css")
public class SimulationBanner extends Card {

    public static final String HOW_TO_LEAVE = "Run without the simulation profile to use the Pi's real devices.";

    /** @param what one sentence on which part of this screen is made up */
    public SimulationBanner(String what) {
        addClassNames("simulation-banner", "aura-accent-orange", "aura-accent-surface");
        setTitle("Simulated hardware");
        setWidthFull();
        add(new Span(what + " " + HOW_TO_LEAVE));
    }
}
