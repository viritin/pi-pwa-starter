package in.virit.iot.pihelpers.auth;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableContext.ContextState;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.webauthn.WebAuthnAuthenticatorStorage;
import io.quarkus.security.webauthn.WebAuthnSecurity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Our own registration endpoints, so a passkey can only be enrolled with a valid
 * invitation (or, once, by the first administrator in {@link AuthConfig#bootstrapMode()}).
 * The extension's built-in register endpoint stays disabled because it would let
 * anyone register any username. Login needs no such gate, so that one uses the
 * built-in {@code /q/webauthn/login} endpoint.
 * <p>
 * The username is always taken from the server-side invite, never from the
 * request, and — mirroring {@code WebAuthnController} — this code performs the
 * persistence ({@code storage.create}) and sets the login cookie ({@code rememberUser})
 * itself, since {@link WebAuthnSecurity#register} only verifies.
 */
@ApplicationScoped
public class PasskeyRoutes {

    public static final String OPTIONS_PATH = "/passkey/register-options";
    public static final String REGISTER_PATH = "/passkey/register";

    private static final Logger LOG = Logger.getLogger(PasskeyRoutes.class);

    @Inject
    WebAuthnSecurity security;
    @Inject
    WebAuthnAuthenticatorStorage storage;
    @Inject
    UserStore users;
    @Inject
    AuthConfig auth;

    public void routes(@Observes Router router) {
        // The invite token travels as a path segment because the bundled webauthn.js
        // always appends its own "?username=..." query string.
        router.get(OPTIONS_PATH).handler(this::registerOptions);
        router.get(OPTIONS_PATH + "/:token").handler(this::registerOptions);
        router.post(REGISTER_PATH).handler(BodyHandler.create()).handler(this::register);
        router.post(REGISTER_PATH + "/:token").handler(BodyHandler.create()).handler(this::register);
    }

    private void registerOptions(RoutingContext ctx) {
        withRequestContext(() ->
                blocking(() -> target(ctx))
                        .flatMap(t -> security.getRegisterChallenge(t.username(), t.displayName(), ctx))
                        .map(security::toJsonString))
                .subscribe().with(json -> okJson(ctx, json), err -> fail(ctx, err));
    }

    private void register(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        withRequestContext(() ->
                blocking(() -> target(ctx)).flatMap(t ->
                        security.register(t.username(), body, ctx)
                                .flatMap(record -> blocking(() -> {
                                    users.ensureUser(t.username(), t.displayName(), t.roles());
                                    return record;
                                }))
                                .call(record -> storage.create(record))
                                .call(record -> blocking(() -> {
                                    if (t.token() != null) {
                                        users.consumeInvite(t.token());
                                    }
                                    return record;
                                }))
                                .replaceWith(t)))
                .subscribe().with(t -> {
                    security.rememberUser(t.username(), ctx);
                    ok(ctx);
                }, err -> fail(ctx, err));
    }

    /** Resolves who may register from this request, or throws if it is not allowed. */
    private Target target(RoutingContext ctx) {
        String token = ctx.pathParam("token");
        if (token != null && !token.isBlank()) {
            InviteToken invite = users.findInvite(token).filter(InviteToken::valid)
                    .orElseThrow(() -> new IllegalArgumentException("This registration link is invalid or has expired."));
            return new Target(invite.username(), invite.displayName(), invite.roles(), token);
        }
        if (auth.bootstrapMode()) {
            String username = require(ctx.queryParams().get("username"), "A username is required.");
            String displayName = ctx.queryParams().get("displayName");
            return new Target(username.trim(),
                    displayName == null || displayName.isBlank() ? username.trim() : displayName.trim(),
                    Set.of("admin", "user"), null);
        }
        throw new IllegalArgumentException("Registration is only possible through an invitation link.");
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private <T> Uni<T> blocking(Supplier<T> supplier) {
        return Uni.createFrom().item(supplier).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    /** Activate a CDI request context around the reactive pipeline, like WebAuthnController does. */
    private <T> Uni<T> withRequestContext(Supplier<Uni<T>> body) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        ContextState state = requestContext.getState();
        return body.get().eventually(() -> requestContext.destroy(state));
    }

    private static void okJson(RoutingContext ctx, String json) {
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(json);
    }

    private static void ok(RoutingContext ctx) {
        ctx.response().setStatusCode(204).end();
    }

    private static void fail(RoutingContext ctx, Throwable error) {
        LOG.debugf(error, "Passkey registration rejected");
        String message = error.getMessage() == null ? "Registration failed." : error.getMessage();
        ctx.response().setStatusCode(400).putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(message);
    }

    private record Target(String username, String displayName, Set<String> roles, String token) {
    }
}
