package in.virit.iot;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Layout;
import com.vaadin.flow.theme.aura.Aura;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MobileMainLayout;
import in.virit.iot.diagnostics.IncidentOverlay;

/** The application's frame; {@code @Layout} makes it every route's layout, the Proto Tools ones included. */
@Layout
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
        return new Brand();
    }

    /** Application name and tagline at the top of the drawer. */
    static class Brand extends Div {
        Brand() {
            getStyle().setDisplay(Style.Display.GRID).setGap(".4rem").setPadding("2rem 1rem").setTextAlign(Style.TextAlign.CENTER);
            var name = new Span("Pi Starter");
            name.getStyle().setFontSize("1.5rem").setFontWeight(Style.FontWeight.BOLD);
            add(name, new Span("Small device. Your ideas."));
        }
    }
}
