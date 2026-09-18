package in.virit.iot;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.theme.aura.Aura;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MobileMainLayout;
import in.virit.iot.diagnostics.IncidentOverlay;

@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles/starter.css")
public class TopLayout extends MobileMainLayout {

    @Inject
    public TopLayout(IncidentOverlay incidentOverlay) {
        addClassName("starter-layout");
        addNavbarHelper(incidentOverlay);
    }

    @Override
    protected Object getDrawerHeader() {
        setBodyScrolling(true);
        setViewTitleVisible(false);
        var title = new Span("Pi Starter");
        title.addClassName("brand-title");
        var subtitle = new Span("Small device. Your ideas.");
        var header = new Div(title, subtitle);
        header.addClassName("brand");
        return header;
    }
}
