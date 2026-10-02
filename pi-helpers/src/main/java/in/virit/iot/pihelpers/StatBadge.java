package in.virit.iot.pihelpers;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/** A label and a separately updated value; shared by the system metrics. */
class StatBadge extends Div {
    // An ellipsis until the first reading arrives
    private final Span value = new Span("…");
    private final String format;

    StatBadge(String title) {
        this(title, null);
    }

    StatBadge(String title, String format) {
        this.format = format;
        addClassName("stat");
        var label = new Span(title);
        label.addClassName("stat-label");
        value.addClassName("stat-value");
        add(label, value);
    }

    void setValue(String text) {
        value.setText(text);
    }

    void setValue(Object... args) {
        setValue(format.formatted(args));
    }

    /** Adds a small visual of the reading under it, e.g. a trend line or a usage bar. */
    StatBadge withVisual(Component visual) {
        add(visual);
        return this;
    }
}
