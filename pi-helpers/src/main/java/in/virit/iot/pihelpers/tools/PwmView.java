package in.virit.iot.pihelpers.tools;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.PwmPanel;
import in.virit.iot.pihelpers.PwmService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Proto Tools route for the PWM and servo panel; registered by {@link ProtoToolsRegistrar}. */
// Not registered at startup: ProtoToolsRegistrar adds it when enabled. Being a @Route still makes
// Vaadin bundle the components it uses, which a route registered only at runtime would miss.
@Route(registerAtStartup = false)
@MenuItem(title = "PWM", icon = VaadinIcon.SLIDERS, order = 3, parent = ProtoTools.class)
@PageTitle("PWM")
@Dependent
@Unremovable
public class PwmView extends PwmPanel {

    @Inject
    public PwmView(PwmService pwmService) {
        super(pwmService);
    }
}
