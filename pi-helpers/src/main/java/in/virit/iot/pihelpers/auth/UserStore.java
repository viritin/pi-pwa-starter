package in.virit.iot.pihelpers.auth;

import in.virit.iot.pihelpers.Json;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Users and their passkeys as one JSON file per record under
 * {@code starter.users-dir} (default {@code ~/.pipwa/users}), in the spirit of
 * {@link in.virit.iot.pihelpers.SettingsStore}: no database, just a handful of
 * files a person can read, back up or delete. Everything is cached in memory so
 * reads (which happen on Vert.x's event loop during the WebAuthn ceremony and on
 * every authenticated request) never touch the disk; only writes do.
 */
@ApplicationScoped
public class UserStore {

    private static final Logger LOG = Logger.getLogger(UserStore.class);

    @ConfigProperty(name = "starter.users-dir", defaultValue = "${user.home}/.pipwa/users")
    String usersDir;

    private final ConcurrentHashMap<String, User> usersByName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userByCredentialId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InviteToken> invitesByToken = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        readAll(usersDir(), User.class).forEach(this::index);
        readAll(invitesDir(), InviteToken.class).forEach(i -> invitesByToken.put(i.token(), i));
        LOG.infof("Loaded %d user(s) and %d invite(s) from %s",
                usersByName.size(), invitesByToken.size(), root());
    }

    // --- users -----------------------------------------------------------

    public Optional<User> find(String username) {
        return Optional.ofNullable(usersByName.get(username));
    }

    public List<User> all() {
        return List.copyOf(usersByName.values());
    }

    public Optional<Located> findByCredentialId(String credentialId) {
        String username = userByCredentialId.get(credentialId);
        if (username == null) {
            return Optional.empty();
        }
        return find(username).flatMap(u -> u.credentials().stream()
                .filter(c -> c.credentialId().equals(credentialId)).findFirst()
                .map(c -> new Located(username, c)));
    }

    public Set<String> rolesFor(String username) {
        return find(username).map(User::roles).orElseGet(Set::of);
    }

    /** True once any passkey exists; drives {@link AuthConfig#bootstrapMode()}. */
    public boolean hasAnyCredential() {
        return !userByCredentialId.isEmpty();
    }

    /** Creates the user if new, or updates the profile of an existing one, keeping any credentials. */
    public synchronized void ensureUser(String username, String displayName, Set<String> roles) {
        User user = usersByName.get(username);
        user = user == null ? User.of(username, displayName, roles) : user.withProfile(displayName, roles);
        save(user);
    }

    public synchronized void addCredential(String username, Credential credential) {
        User user = usersByName.getOrDefault(username, User.of(username, username, Set.of("user")));
        save(user.withAdded(credential));
    }

    public synchronized void updateCounter(String credentialId, long counter) {
        findByCredentialId(credentialId).ifPresent(located -> {
            User user = usersByName.get(located.username());
            List<Credential> updated = user.credentials().stream()
                    .map(c -> c.credentialId().equals(credentialId) ? c.withCounter(counter) : c)
                    .toList();
            save(new User(user.username(), user.displayName(), user.roles(), updated));
        });
    }

    public synchronized void delete(String username) {
        User removed = usersByName.remove(username);
        if (removed != null) {
            removed.credentials().forEach(c -> userByCredentialId.remove(c.credentialId()));
            deleteFile(userFile(username));
        }
    }

    private void save(User user) {
        User previous = usersByName.put(user.username(), user);
        if (previous != null) {
            previous.credentials().forEach(c -> userByCredentialId.remove(c.credentialId()));
        }
        user.credentials().forEach(c -> userByCredentialId.put(c.credentialId(), user.username()));
        write(userFile(user.username()), user);
    }

    private void index(User user) {
        usersByName.put(user.username(), user);
        user.credentials().forEach(c -> userByCredentialId.put(c.credentialId(), user.username()));
    }

    // --- invites ---------------------------------------------------------

    public InviteToken createInvite(String username, String displayName, Set<String> roles, Duration validFor) {
        var invite = new InviteToken(newToken(), username, displayName, roles, Instant.now().plus(validFor), false);
        invitesByToken.put(invite.token(), invite);
        write(inviteFile(invite.token()), invite);
        return invite;
    }

    public Optional<InviteToken> findInvite(String token) {
        return Optional.ofNullable(invitesByToken.get(token));
    }

    public synchronized void consumeInvite(String token) {
        InviteToken invite = invitesByToken.get(token);
        if (invite != null) {
            invite = invite.consumed();
            invitesByToken.put(token, invite);
            write(inviteFile(token), invite);
        }
    }

    // --- paths & IO ------------------------------------------------------

    public Path root() {
        return Path.of(usersDir);
    }

    private Path usersDir() {
        return root().resolve("users");
    }

    private Path invitesDir() {
        return root().resolve("invites");
    }

    private Path userFile(String username) {
        return usersDir().resolve(safe(username) + ".json");
    }

    private Path inviteFile(String token) {
        return invitesDir().resolve(token + ".json");
    }

    private <T> List<T> readAll(Path dir, Class<T> type) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .flatMap(p -> {
                        try {
                            return Stream.of(Json.read(Files.readString(p), type));
                        } catch (IOException | RuntimeException e) {
                            LOG.warnf(e, "Skipping unreadable %s", p);
                            return Stream.empty();
                        }
                    }).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void write(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value));
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }

    private void deleteFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warnf(e, "Could not delete %s", file);
        }
    }

    /** Keep a username usable as a file name; the UI restricts input to this shape too. */
    static String safe(String username) {
        return username.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String newToken() {
        var bytes = new byte[24];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** A credential together with the username that owns it. */
    public record Located(String username, Credential credential) {
    }
}
