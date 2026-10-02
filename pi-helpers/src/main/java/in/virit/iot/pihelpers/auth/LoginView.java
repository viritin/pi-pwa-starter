package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;
import org.vaadin.firitin.util.style.LumoProps;

/**
 * Passwordless sign-in. Normally a single "Sign in with a passkey" button that
 * lets the browser offer any passkey registered for this device (discoverable
 * credentials). Before the first passkey exists it doubles as the one-time setup
 * screen for enrolling the first administrator.
 */
@Route(value = "login", autoLayout = false)
@Menu(title = "Sign in", icon = "vaadin:sign-in", order = 90)
@PageTitle("Sign in")
public class LoginView extends StandalonePage {

    @Inject
    public LoginView(AuthConfig auth) {
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
            add(new ContentColumn(new H1("Welcome back"),
                    new Lead("Use the passkey you registered on this device."),
                    new SignInButton(),
                    new SecureContextHint()));
        }

        /** The line under the heading, close to it. */
        static class Lead extends Paragraph {
            Lead(String text) {
                super(text);
                getStyle().setMarginTop("0");
            }
        }

        static class SignInButton extends Button {
            SignInButton() {
                super("Sign in with a passkey", e -> PasskeyClient.login().whenComplete((ok, err) ->
                        afterCeremony(Boolean.TRUE.equals(ok), "Sign-in failed or was cancelled.")));
                setId("sign-in-passkey");
                addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
        }
    }

    /** Shown until the first passkey exists: enroll the initial administrator. */
    static class FirstAdminCard extends Card {
        private final UsernameField username = new UsernameField();
        private final TextField displayName = new TextField("Display name");

        FirstAdminCard() {
            setWidth("24rem");
            displayName.setWidthFull();
            add(new ContentColumn(new H1("Set up sign-in"),
                    new Paragraph("Authentication is on but no one is registered yet. "
                            + "Create the first administrator by enrolling a passkey."),
                    username, displayName, new CreateButton(), new SecureContextHint()));
        }

        private void create() {
            String user = username.getValue() == null ? "" : username.getValue().trim();
            if (user.isBlank() || username.isInvalid()) {
                username.setInvalid(true);
                return;
            }
            PasskeyClient.register(PasskeyRoutes.OPTIONS_PATH, PasskeyRoutes.REGISTER_PATH, user, displayName.getValue())
                    .whenComplete((ok, err) ->
                            afterCeremony(Boolean.TRUE.equals(ok), "Could not create the administrator passkey."));
        }

        static class UsernameField extends TextField {
            UsernameField() {
                super("Administrator username");
                setId("first-admin-username");
                setHelperText("Letters, numbers, dot, dash and underscore.");
                setPattern("[a-zA-Z0-9._-]+");
                setRequired(true);
                setWidthFull();
            }
        }

        class CreateButton extends Button {
            CreateButton() {
                super("Create administrator passkey", e -> create());
                setId("first-admin-create");
                addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
        }
    }

    /** A quiet reminder that passkeys need HTTPS (or localhost during development). */
    static class SecureContextHint extends Paragraph {
        SecureContextHint() {
            super("Passkeys require a secure connection: HTTPS, or localhost while developing.");
            getStyle().setFontSize(".8rem").setColor(LumoProps.SECONDARY_TEXT_COLOR.var()).setMarginBottom("0");
        }
    }
}
