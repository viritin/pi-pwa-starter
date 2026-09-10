package in.virit.iot;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.OneWirePanel;
import in.virit.iot.pihelpers.OneWireService;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Application-specific route and navigation for the reusable 1-Wire panel. */
@Route(value = "onewire", layout = TopLayout.class)
@MenuItem(title = "1-Wire", icon = VaadinIcon.FIRE, order = 4, parent = ProtoTools.class)
@PageTitle("1-Wire | Pi Starter")
public class OneWireView extends OneWirePanel {

    @Inject
    public OneWireView(OneWireService oneWireService) {
        super(oneWireService);
        addClassName("page");
    }
}
