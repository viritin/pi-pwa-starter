package in.virit.iot.pihelpers;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.dependency.StyleSheet;

/** Label-and-value tiles in a responsive grid; the look lives in pi-helpers-stat.css. */
@StyleSheet("styles/pi-helpers-stat.css")
class StatGrid extends Div {
    StatGrid(Component... children) {
        addClassName("stat-grid");
        add(children);
    }
}
