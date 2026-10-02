package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.AppInfo;
import jakarta.inject.Inject;
import org.vaadin.firitin.layouts.HorizontalFloatLayout;
import org.vaadin.firitin.util.ResizeObserver;
import org.vaadin.firitin.util.style.LumoProps;
import org.vaadin.firitin.util.style.VaadinCssProps;

import java.util.List;
import java.util.Set;

/**
 * Administration of passkey users: who exists, handing out registration links for
 * new people, and removing accounts. Only meaningful with
 * {@code starter.auth.enabled=true}; the application's gate keeps it for admins.
 */
@Route("users")
@Menu(title = "Users", icon = "vaadin:users", order = 5)
@PageTitle("Users")
public class UsersView extends VerticalLayout {

    private final UserStore users;
    private final String appName;
    private final UserGrid grid = new UserGrid();

    @Inject
    public UsersView(UserStore users, AuthConfig auth, AppInfo appInfo) {
        this.users = users;
        this.appName = appInfo.name();
        setSizeFull();

        add(new H1("Users"));
        if (auth.bootstrapMode()) {
            add(new StatusBanner("No passkey has been registered yet. The first person to sign in "
                    + "becomes the administrator."));
        }
        add(grid, new AddUserForm(), new LoginView.SecureContextHint());
        grid.refresh();
    }

    /**
     * The users, one per row with their invite and delete actions. On a narrow
     * screen, a phone, the details fold into a single column, so a row fits
     * without scrolling sideways.
     */
    class UserGrid extends Grid<User> {
        /** Below this width the grid shows the compact columns. */
        private static final int COMPACT_BELOW_PX = 600;

        private final List<Column<User>> detailColumns = List.of(
                addColumn(User::username).setHeader("Username").setAutoWidth(true),
                addColumn(User::displayName).setHeader("Display name").setAutoWidth(true),
                addColumn(UserGrid::roles).setHeader("Roles").setAutoWidth(true),
                addColumn(UserGrid::passkeys).setHeader("Passkeys").setAutoWidth(true));
        private final Column<User> summaryColumn = addComponentColumn(UserSummary::new).setHeader("User");

        UserGrid() {
            super(User.class, false);
            summaryColumn.setVisible(false);
            addComponentColumn(RowActions::new).setAutoWidth(true).setFlexGrow(0);
            ResizeObserver.get().observe(this, size -> setCompact(size.width() < COMPACT_BELOW_PX));
        }

        void refresh() {
            setItems(users.all());
        }

        private void setCompact(boolean compact) {
            detailColumns.forEach(column -> column.setVisible(!compact));
            summaryColumn.setVisible(compact);
        }

        static String roles(User user) {
            return String.join(", ", user.roles());
        }

        static int passkeys(User user) {
            return user.credentials().size();
        }

        /** The details of the compact column: the names, with roles and passkeys under them. */
        static class UserSummary extends Div {
            UserSummary(User user) {
                var displayName = user.displayName();
                var name = new Span(displayName == null || displayName.isBlank() || displayName.equals(user.username())
                        ? user.username() : displayName + " (" + user.username() + ")");
                name.getStyle().setFontWeight(Style.FontWeight.BOLD);
                var details = new Span(roles(user) + " · " + passkeys(user)
                        + (passkeys(user) == 1 ? " passkey" : " passkeys"));
                details.getStyle().setDisplay(Style.Display.BLOCK)
                        .setFontSize("0.875em")
                        .setColor(VaadinCssProps.TEXT_COLOR_SECONDARY.var());
                add(name, details);
                getStyle().setWhiteSpace(Style.WhiteSpace.NORMAL);
            }
        }

        /** Sending the user's registration link, and removing the user. */
        class RowActions extends HorizontalLayout {
            RowActions(User user) {
                add(new InviteButton(user, users, appName), new DeleteButton(user));
            }
        }

        class DeleteButton extends Button {
            DeleteButton(User user) {
                super(VaadinIcon.TRASH.create(), e -> {
                    users.delete(user.username());
                    refresh();
                });
                addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
                setAriaLabel("Delete " + user.username());
            }
        }
    }

    /** A short explanatory strip above the grid. */
    static class StatusBanner extends Paragraph {
        StatusBanner(String text) {
            super(text);
            getStyle()
                    .setPadding(LumoProps.SPACE_S.var() + " " + LumoProps.SPACE_M.var())
                    .setBackground(LumoProps.CONTRAST_5PCT.var())
                    .setBorderRadius(LumoProps.BORDER_RADIUS_M.var())
                    .setMargin("0");
        }
    }

    /** Create a new person and immediately offer their registration link. */
    class AddUserForm extends Card {
        AddUserForm() {
            setWidthFull();
            var username = new TextField("Username");
            username.setPattern("[a-zA-Z0-9._-]+");
            username.setRequired(true);
            var displayName = new TextField("Display name");
            var admin = new Checkbox("Administrator");

            var create = new Button("Create user & invite link", e -> {
                String name = username.getValue() == null ? "" : username.getValue().trim();
                if (name.isBlank() || username.isInvalid()) {
                    username.setInvalid(true);
                    return;
                }
                if (users.find(name).isPresent()) {
                    Notification.show("A user named " + name + " already exists.");
                    return;
                }
                Set<String> roles = admin.getValue() ? Set.of("admin", "user") : Set.of("user");
                users.ensureUser(name, displayName.getValue(), roles);
                username.clear();
                // A required field just emptied for the next person is not an error yet
                username.setInvalid(false);
                displayName.clear();
                admin.clear();
                grid.refresh();
                InviteButton.offer(users.find(name).orElseThrow(), users, InviteButton.DEFAULT_VALIDITY, appName);
            });
            create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            add(new HorizontalFloatLayout(username, displayName, admin, create));
        }
    }
}
