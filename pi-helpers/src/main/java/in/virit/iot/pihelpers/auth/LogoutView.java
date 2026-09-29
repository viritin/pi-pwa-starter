package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * A menu entry, shown only while signed in, that ends the session: it clears the
 * cached user and hands off to the extension's logout endpoint, which drops the
 * WebAuthn cookie and returns to the front page.
 */
@Route(value = "logout", autoLayout = false)
@Menu(title = "Sign out", icon = "vaadin:sign-out", order = 91)
@PageTitle("Sign out")
public class LogoutView extends Div implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        CurrentUser.clear();
        event.getUI().getPage().setLocation("/q/webauthn/logout");
    }
}
