package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/** The content of a card or dialog, stacked; the card or dialog pads it already. */
class ContentColumn extends VerticalLayout {
    ContentColumn(Component... children) {
        super(children);
        setPadding(false);
    }
}
