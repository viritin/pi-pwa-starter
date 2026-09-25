package in.virit.iot.pihelpers.tools;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.startup.ApplicationRouteRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Adds the Proto Tools screens to the application when
 * {@code starter.proto-tools.enabled=true}, so an application carries no code
 * for them: the routes are registered at startup and Viritin's menu picks them
 * up under "Proto Tools". They get the application's main layout, the class it
 * marks with Vaadin's {@code @Layout}, like any route that names no layout.
 */
@ApplicationScoped
public class ProtoToolsRegistrar {

    private static final Logger LOG = Logger.getLogger(ProtoToolsRegistrar.class);

    /** Registered in this order; the path comes from the class name (GpioView → gpio), the menu entry from @MenuItem. */
    static final List<Class<? extends Component>> VIEWS = List.of(
            GpioView.class, I2cView.class, PwmView.class, OneWireView.class, BleView.class);

    @ConfigProperty(name = "starter.proto-tools.enabled", defaultValue = "false")
    boolean enabled;

    void register(@Observes ServiceInitEvent event) {
        if (!enabled) {
            return;
        }
        var routes = RouteConfiguration.forRegistry(ApplicationRouteRegistry.getInstance(event.getSource().getContext()));
        VIEWS.forEach(routes::setAnnotatedRoute);
        LOG.infof("Proto Tools registered: %s", VIEWS.stream().map(Class::getSimpleName).toList());
    }

    public boolean isEnabled() {
        return enabled;
    }
}
