# Authentication

This app can require **passwordless sign-in with passkeys (WebAuthn)**. The feature
lives in the **pi-helpers** module (package `in.virit.iot.pihelpers.auth`) and is
documented there — see the *Optional passkey authentication* section of
[pi-helpers/README.md](../pi-helpers/README.md). It is **off by default**.

Turn it on:

```properties
starter.auth.enabled=true
```

Then open the app and use **Sign in** in the menu; with no passkey yet it enrolls
the first administrator, who can then add users from **Users**. Passkeys need HTTPS
or `localhost` (see [HTTPS.md](HTTPS.md)).

What is app-specific here: `TopLayout` wires the access gate (which routes are
public, admin-only Users) and `AppShell`/`application.properties` carry the config.
Everything else comes from pi-helpers.

To try the passkey ceremony end to end in a real browser on localhost:

```
./mvnw -Psimulation verify -Dit.test=PasskeyLoginIT
```
