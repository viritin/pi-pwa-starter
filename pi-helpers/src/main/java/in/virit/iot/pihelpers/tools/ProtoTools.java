package in.virit.iot.pihelpers.tools;

import com.vaadin.flow.component.icon.VaadinIcon;
import org.vaadin.firitin.appframework.MenuItem;

/**
 * Navigation group for the prototyping screens. Not a view: Viritin's menu turns
 * a non-component class used as a {@link MenuItem#parent()} into a sub-menu in
 * the drawer and a popover item in the mobile bottom bar.
 */
@MenuItem(title = "Proto Tools", icon = VaadinIcon.TOOLS, order = 5, openByDefault = true)
public final class ProtoTools {
    private ProtoTools() {
    }
}
