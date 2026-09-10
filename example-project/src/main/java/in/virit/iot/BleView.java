package in.virit.iot;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.BlePanel;
import in.virit.iot.pihelpers.BleScanService;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Application-specific route and navigation for the reusable Bluetooth LE scanner. */
@Route(value = "ble", layout = TopLayout.class)
@MenuItem(title = "Bluetooth LE", icon = VaadinIcon.SIGNAL, order = 5, parent = ProtoTools.class)
@PageTitle("Bluetooth LE | Pi Starter")
public class BleView extends BlePanel {

    @Inject
    public BleView(BleScanService bleScanService) {
        super(bleScanService);
        addClassName("page");
    }
}
