# Temperature alerts with Web Push

The Climate view's **Temperature alerts** card sends a notification to a phone or
desktop when the temperature leaves a range: below a minimum, above a maximum, or
either. Each browser sets its own range. It uses the standard Web Push protocol
through Vaadin's `flow-webpush`, so there is no app store, no third-party
notification service and no database.

## How it works

- The browser subscribes with the browser vendor's push service (Google, Mozilla,
  Apple). The server stores that subscription and later posts encrypted messages to
  it, signed with the server's **VAPID** key.
- `push/TemperatureAlerts` listens to `Bme280Service`. It notifies **once** when a
  reading leaves the range, and only re-arms when the reading is back inside by
  0.5 °C, so a value hovering at a limit does not flood the phone. Coming back to
  normal is not notified.
- `push/WebPushSender` delivers on a thread of its own and forgets a subscription
  the push service reports as gone (HTTP 404/410).
- `TemperatureAlertsCard` is the UI. A random id in the browser's localStorage tells
  browsers apart, so a phone and a laptop can have different ranges. With passkey
  sign-in on ([AUTH.md](AUTH.md)), the signed-in user is stored with the
  subscription too.

## Where the data lives

Plain files under `starter.push.dir` (default `~/.pipwa/push`, next to the passkey
data):

- `subscriptions/<id>.json`: one per browser, with its endpoint, keys and range.
- `vapid.json`: the server's key pair, generated on first start and readable by
  its owner only. **Keep it**: browsers bind their subscriptions to the public key,
  so a new pair silently ends every subscription.

## Configuration

```properties
starter.push.dir=${user.home}/.pipwa/push
# A contact the push services can reach; Apple's rejects pushes without a real one.
starter.push.subject=mailto:you@example.org
# Optional: bring your own VAPID pair (base64url) instead of the generated one.
#starter.push.vapid-public-key=
#starter.push.vapid-private-key=
```

Set `starter.push.subject` to a real `mailto:` (or `https:`) address before relying
on alerts; the default `mailto:admin@example.com` is a placeholder.

## Requirements on the device

- **A secure context:** HTTPS, or `localhost` while developing. See
  [HTTPS.md](HTTPS.md); over plain LAN HTTP the card explains that notifications
  are unavailable.
- **iPhone and iPad** (iOS 16.4+): add the app to the home screen first and open
  it from there; Safari tabs cannot receive pushes.
- **Safari on macOS:** add the app to the Dock.
- Chrome, Edge and Firefox on the desktop and Android work in a normal tab. Private
  and incognito windows have no push service.
- The service worker that delivers pushes is Vaadin's own; it needs
  `@PWA(offline = true)` in `AppShell`, as this starter already has.

## Trying it

Run on localhost (`./mvnw -Psimulation quarkus:dev`), open Climate, tick
**Notifications on this device** and allow notifications. **Send a test
notification** checks delivery. The simulated sensor moves between about 20.5 and
24.5 °C, so a max of 23 °C or a min of 22 °C soon produces a real alert.

The tests cover the alert rules, the subscription files, the VAPID pair and
delivery against a stand-in push service (`WebPushSenderTest`); delivery through a
real browser's push service is something to try by hand.
