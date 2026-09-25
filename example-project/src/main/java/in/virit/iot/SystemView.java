package in.virit.iot;

import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;
import in.virit.iot.pihelpers.SystemControl;
import in.virit.iot.pihelpers.SystemPanel;

/** Application-specific route and navigation for the reusable system panel. */
@Route
@Menu(title = "System", icon = "vaadin:cogs", order = 2)
@PageTitle("System | Pi Starter")
public class SystemView extends SystemPanel {

    @Inject
    public SystemView(SystemControl systemControl) {
        super(systemControl);
        addClassName("page");
    }
}
