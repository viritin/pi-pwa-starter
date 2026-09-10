package in.virit.iot.pihelpers;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;

class StatGrid extends Div {
    StatGrid(Component... children) {
        addClassName("stat-grid");
        add(children);
    }
}
