package in.virit.iot.pihelpers.tools;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.BlePanel;
import in.virit.iot.pihelpers.BleScanService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Proto Tools route for the Bluetooth LE scanner; registered by {@link ProtoToolsRegistrar}. */
// Not registered at startup: ProtoToolsRegistrar adds it when enabled. Being a @Route still makes
// Vaadin bundle the components it uses, which a route registered only at runtime would miss.
@Route(registerAtStartup = false)
@MenuItem(title = "Bluetooth LE", icon = VaadinIcon.SIGNAL, order = 5, parent = ProtoTools.class)
@PageTitle("Bluetooth LE")
@Dependent
@Unremovable
public class BleView extends BlePanel {

    @Inject
    public BleView(BleScanService bleScanService) {
        super(bleScanService);
    }
}
