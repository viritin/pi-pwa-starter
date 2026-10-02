package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/**
 * The page an invited user opens from their registration link
 * ({@code /register?token=...}) to enroll their own passkey. The token is
 * validated server-side here so the invitee sees who the invitation is for, or a
 * clear message when the link is spent or expired.
 */
@Route(value = "register", autoLayout = false)
@PageTitle("Register a passkey")
public class RegisterView extends StandalonePage implements BeforeEnterObserver {

    private final UserStore users;

    @Inject
    public RegisterView(UserStore users) {
        this.users = users;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String token = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("token", List.of()).stream().findFirst().orElse(null);
        Optional<InviteToken> invite = token == null ? Optional.empty()
                : users.findInvite(token).filter(InviteToken::valid);
        add(invite.map(i -> (Card) new InviteCard(token, i)).orElseGet(InvalidCard::new));
    }

    /** A valid invitation: show who it is for and enroll the passkey. */
    static class InviteCard extends Card {
        InviteCard(String token, InviteToken invite) {
            setWidth("24rem");
            var who = invite.displayName() == null || invite.displayName().isBlank()
                    ? invite.username() : invite.displayName() + " (" + invite.username() + ")";
            add(new ContentColumn(new H1("You're invited"),
                    new Paragraph("Set up a passkey for " + who + "."),
                    new RegisterButton(token, invite),
                    new LoginView.SecureContextHint()));
        }

        /** Runs the registration ceremony against the invite-gated endpoints, then signs in. */
        static class RegisterButton extends Button {
            RegisterButton(String token, InviteToken invite) {
                super("Create my passkey", e -> PasskeyClient.register(
                                PasskeyRoutes.OPTIONS_PATH + "/" + token,
                                PasskeyRoutes.REGISTER_PATH + "/" + token,
                                invite.username(), invite.displayName())
                        .whenComplete((ok, err) -> {
                            UI ui = UI.getCurrent();
                            ui.access(() -> {
                                if (Boolean.TRUE.equals(ok)) {
                                    ui.getPage().setLocation("/");
                                } else {
                                    Notification.show("Could not create your passkey. The link may have expired.",
                                            6000, Notification.Position.MIDDLE);
                                }
                            });
                        }));
                addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
        }
    }

    /** A missing, spent or expired link. */
    static class InvalidCard extends Card {
        InvalidCard() {
            setWidth("24rem");
            add(new H1("Link not valid"),
                    new Paragraph("This registration link is invalid, has already been used or has expired. "
                            + "Ask an administrator for a new one."));
        }
    }
}
