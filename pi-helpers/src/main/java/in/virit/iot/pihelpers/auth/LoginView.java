package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;

/**
 * Passwordless sign-in. Normally a single "Sign in with a passkey" button that
 * lets the browser offer any passkey registered for this device (discoverable
 * credentials). Before the first passkey exists it doubles as the one-time setup
 * screen for enrolling the first administrator.
 */
@Route(value = "login", autoLayout = false)
@Menu(title = "Sign in", icon = "vaadin:sign-in", order = 90)
@PageTitle("Sign in")
public class LoginView extends VerticalLayout {

    @Inject
    public LoginView(AuthConfig auth) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        add(auth.bootstrapMode() ? new FirstAdminCard() : new SignInCard());
    }

    /** Reload from the server so the WebAuthn cookie set by the ceremony is picked up. */
    private static void goHome() {
        UI.getCurrent().getPage().setLocation("/");
    }

    private static void afterCeremony(boolean ok, String failureMessage) {
        UI ui = UI.getCurrent();
        ui.access(() -> {
            if (ok) {
                goHome();
            } else {
                Notification.show(failureMessage, 6000, Notification.Position.MIDDLE);
            }
        });
    }

    /** The ordinary case: sign in with an existing passkey. */
    static class SignInCard extends Card {
        SignInCard() {
            setWidth("22rem");
            var sub = new Paragraph("Use the passkey you registered on this device.");
            sub.getStyle().setMarginTop("0");
            var button = new Button("Sign in with a passkey",
                    e -> PasskeyClient.login().whenComplete((ok, err) ->
                            afterCeremony(Boolean.TRUE.equals(ok), "Sign-in failed or was cancelled.")));
            button.setId("sign-in-passkey");
            button.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);
            var content = new VerticalLayout(new H1("Welcome back"), sub, button, new SecureContextHint());
            content.setPadding(false);
            add(content);
        }
    }

    /** Shown until the first passkey exists: enroll the initial administrator. */
    static class FirstAdminCard extends Card {
        FirstAdminCard() {
            setWidth("24rem");
            var username = new TextField("Administrator username");
            username.setId("first-admin-username");
            username.setHelperText("Letters, numbers, dot, dash and underscore.");
            username.setPattern("[a-zA-Z0-9._-]+");
            username.setRequired(true);
            username.setWidthFull();
            var displayName = new TextField("Display name");
            displayName.setWidthFull();

            var create = new Button("Create administrator passkey", e -> {
                String user = username.getValue() == null ? "" : username.getValue().trim();
                if (user.isBlank() || username.isInvalid()) {
                    username.setInvalid(true);
                    return;
                }
                PasskeyClient.register(PasskeyRoutes.OPTIONS_PATH, PasskeyRoutes.REGISTER_PATH, user, displayName.getValue())
                        .whenComplete((ok, err) ->
                                afterCeremony(Boolean.TRUE.equals(ok), "Could not create the administrator passkey."));
            });
            create.setId("first-admin-create");
            create.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);

            var content = new VerticalLayout(new H1("Set up sign-in"),
                    new Paragraph("Authentication is on but no one is registered yet. "
                            + "Create the first administrator by enrolling a passkey."),
                    username, displayName, create, new SecureContextHint());
            content.setPadding(false);
            add(content);
        }
    }

    /** A quiet reminder that passkeys need HTTPS (or localhost during development). */
    static class SecureContextHint extends Paragraph {
        SecureContextHint() {
            super("Passkeys require a secure connection: HTTPS, or localhost while developing.");
            getStyle().setFontSize(".8rem").setColor("var(--lumo-secondary-text-color)").setMarginBottom("0");
        }
    }
}
