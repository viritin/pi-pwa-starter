package in.virit.iot;

import com.vaadin.flow.component.html.Div;

/** A line of supporting text on its own row, in the theme's secondary color. */
class SecondaryLine extends Div {

    SecondaryLine() {
        getStyle().setColor("var(--vaadin-text-color-secondary)");
    }
}
