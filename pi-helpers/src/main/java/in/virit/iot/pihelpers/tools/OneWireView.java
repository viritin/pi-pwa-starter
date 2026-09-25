package in.virit.iot.pihelpers.tools;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.OneWirePanel;
import in.virit.iot.pihelpers.OneWireService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Proto Tools route for the 1-Wire sensor list; registered by {@link ProtoToolsRegistrar}. */
// Not registered at startup: ProtoToolsRegistrar adds it when enabled. Being a @Route still makes
// Vaadin bundle the components it uses, which a route registered only at runtime would miss.
@Route(registerAtStartup = false)
@MenuItem(title = "1-Wire", icon = VaadinIcon.FIRE, order = 4, parent = ProtoTools.class)
@PageTitle("1-Wire")
@Dependent
@Unremovable
public class OneWireView extends OneWirePanel {

    @Inject
    public OneWireView(OneWireService oneWireService) {
        super(oneWireService);
    }
}
