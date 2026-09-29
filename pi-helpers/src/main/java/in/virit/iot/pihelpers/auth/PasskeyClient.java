package in.virit.iot.pihelpers.auth;

import org.vaadin.firitin.util.JsPromise;

import java.util.concurrent.CompletableFuture;

/**
 * Runs the browser half of the WebAuthn ceremony from a Vaadin view. It loads the
 * extension's {@code webauthn.js} (a UMD script, so it is added as a plain
 * {@code <script>} to expose the global), then calls {@code navigator.credentials}
 * through that helper and reports back whether it succeeded. The passkey prompt
 * only appears in a secure context (HTTPS, or {@code localhost} in development).
 */
final class PasskeyClient {

    private PasskeyClient() {
    }

    private static final String LOADER = """
            await new Promise((resolve, reject) => {
                if (window.WebAuthn) { resolve(); return; }
                const s = document.createElement('script');
                s.src = '/q/webauthn/webauthn.js';
                s.onload = () => resolve();
                s.onerror = () => reject('Could not load webauthn.js');
                document.head.appendChild(s);
            });
            """;

    /** Discoverable (usernameless) sign-in via the extension's built-in login endpoint. */
    static CompletableFuture<Boolean> login() {
        return JsPromise.computeBoolean(LOADER + """
                const w = new WebAuthn();
                try { await w.login({}); return true; }
                catch (e) { console.warn('passkey login failed', e); return false; }
                """);
    }

    /** Enrolls a passkey against our invite/bootstrap-gated endpoints. */
    static CompletableFuture<Boolean> register(String optionsPath, String registerPath,
                                               String username, String displayName) {
        return JsPromise.computeBoolean(LOADER + """
                const w = new WebAuthn({ registerOptionsChallengePath: $0, registerPath: $1 });
                try { await w.register({ username: $2, displayName: $3 }); return true; }
                catch (e) { console.warn('passkey registration failed', e); return false; }
                """, optionsPath, registerPath, username, displayName);
    }
}
