package in.virit.iot;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Layout;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MobileMainLayout;
import org.vaadin.firitin.appframework.NavigationItem;
import in.virit.iot.pihelpers.auth.AuthConfig;
import in.virit.iot.pihelpers.auth.CurrentUser;
import in.virit.iot.pihelpers.auth.CurrentUser.AuthUser;
import in.virit.iot.pihelpers.auth.LoginView;
import in.virit.iot.pihelpers.auth.LogoutView;
import in.virit.iot.pihelpers.auth.RegisterView;
import in.virit.iot.pihelpers.auth.UsersView;
import in.virit.iot.pihelpers.AppInfo;
import in.virit.iot.pihelpers.diagnostics.IncidentOverlay;
import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.Set;

/**
 * The application's frame; {@code @Layout} makes it every route's layout except
 * the standalone sign-in pages ({@code autoLayout = false}). Because the router
 * always routes protected views through this layout, it is also where the
 * optional passkey gate lives: when {@code starter.auth.enabled} is on it
 * captures the signed-in identity and diverts anonymous visitors to the login
 * page. With auth off, both hooks are no-ops and the app is unchanged.
 */
@Layout
public class TopLayout extends MobileMainLayout implements BeforeEnterObserver {

    private static final Logger LOG = Logger.getLogger(TopLayout.class);

    private final AuthConfig auth;
    private final String appName;

    @Inject
    public TopLayout(IncidentOverlay incidentOverlay, AuthConfig auth, AppInfo appInfo) {
        this.auth = auth;
        this.appName = appInfo.name();
        setBodyScrolling(true);
        // Each view carries its own heading
        setViewTitleVisible(false);
        addNavbarHelper(incidentOverlay);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!auth.authEnabled()) {
            if (event.getNavigationTarget() == UsersView.class) {
                event.rerouteTo(AboutView.class); // no users to manage without sign-in
            }
            return;
        }
        captureIdentity();
        Class<?> target = event.getNavigationTarget();
        if (target == AboutView.class) {
            return; // the landing page stays public
        }
        Optional<AuthUser> user = CurrentUser.get();
        if (user.isEmpty()) {
            event.rerouteTo(LoginView.class);
        } else if (target == UsersView.class && !user.get().isAdmin()) {
            event.rerouteTo(AboutView.class);
        }
    }

    /**
     * Reads the WebAuthn identity into the session. It is only resolvable on a
     * real HTTP request (the initial load, and the reload after sign-in/out);
     * over the {@code @Push} websocket there is no request identity, so we keep
     * whatever the session already holds.
     */
    private void captureIdentity() {
        try {
            SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
            if (identity == null || identity.isAnonymous()) {
                CurrentUser.clear();
            } else {
                CurrentUser.set(new AuthUser(identity.getPrincipal().getName(), Set.copyOf(identity.getRoles())));
            }
        } catch (RuntimeException e) {
            LOG.debug("No request-bound security identity available; keeping the session's user", e);
        }
    }

    /**
     * Shapes the menu around the auth state: Register is never an entry; Sign in /
     * Sign out appear only when relevant; and while signed out only About is
     * offered. With auth off the menu is as without authentication: no Users,
     * sign-in or sign-out entries.
     */
    @Override
    protected boolean checkAccess(NavigationItem item) {
        Class<?> target = item.getNavigationTarget();
        if (target == RegisterView.class) {
            return false;
        }
        boolean loggedIn = CurrentUser.get().isPresent();
        if (target == LoginView.class) {
            return auth.authEnabled() && !loggedIn;
        }
        if (target == LogoutView.class) {
            return auth.authEnabled() && loggedIn;
        }
        if (!auth.authEnabled()) {
            return target != UsersView.class;
        }
        if (!loggedIn) {
            return target == AboutView.class;
        }
        return target != UsersView.class || CurrentUser.get().get().isAdmin();
    }

    @Override
    protected Object getDrawerHeader() {
        return new Brand(appName);
    }

    /** Application name and tagline at the top of the drawer. */
    static class Brand extends Div {
        Brand(String appName) {
            getStyle().setDisplay(Style.Display.GRID).setGap(".4rem").setPadding("2rem 1rem").setTextAlign(Style.TextAlign.CENTER);
            add(new AppName(appName), new Span("Small device. Your ideas."));
        }

        static class AppName extends Span {
            AppName(String name) {
                super(name);
                getStyle().setFontSize("1.5rem").setFontWeight(Style.FontWeight.BOLD);
            }
        }
    }
}
