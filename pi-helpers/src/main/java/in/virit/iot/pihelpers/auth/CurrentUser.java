package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.server.VaadinSession;

import java.io.Serializable;
import java.util.Optional;
import java.util.Set;

/**
 * The signed-in user for the current browser session, cached in the
 * {@link VaadinSession}. It is captured once, on a real HTTP request, by
 * {@link AuthGate}; keeping it in the session means the navigation gate works the
 * same over the websocket that {@code @Push} uses, where the request-scoped
 * Quarkus identity is not available.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public record AuthUser(String username, Set<String> roles) implements Serializable {
        public boolean isAdmin() {
            return roles.contains("admin");
        }
    }

    public static void set(AuthUser user) {
        VaadinSession.getCurrent().setAttribute(AuthUser.class, user);
    }

    public static Optional<AuthUser> get() {
        VaadinSession session = VaadinSession.getCurrent();
        return session == null ? Optional.empty() : Optional.ofNullable(session.getAttribute(AuthUser.class));
    }

    public static void clear() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(AuthUser.class, null);
        }
    }
}
