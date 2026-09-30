# HTTPS for the installed PWA

## Do you actually need HTTPS?

Often not. Plain HTTP over the LAN is genuinely fine for many Pi projects: the
live views, toggling a GPIO or reading a sensor all work over
`http://raspberrypi.local:8080` or the Pi's IP, and `localhost` always counts as
a secure context during development. You only need HTTPS once you want a browser
feature that requires a **secure context**:

- **Passkey / WebAuthn sign-in** — the optional login in this starter (see
  [AUTH.md](AUTH.md)); the passkey prompt does not appear over plain LAN HTTP.
- **Push notifications** (Web Push) and other install-grade PWA capabilities.
- A fully installable PWA on some platforms.

If none of those apply, you can skip the rest of this document and just use the
Pi's LAN address.

When you do need it, **[Cloudflare Tunnel](#remote-access-cloudflare-tunnel) (or
a similar tunnel such as `ngrok`) is usually the handiest option**: it gives a
browser-trusted HTTPS address with no certificate to install on any device, and
it reaches the app from outside your home even behind NAT/CGNAT, without router
port forwarding. The alternative is a stable HTTPS address on your own domain
with DNS-01 certificate validation, which keeps traffic on the LAN. Both provide
browser-trusted certificates without installing a private CA on client devices;
the sections below cover each.

## Remote access: Cloudflare Tunnel

[Cloudflare Tunnel](https://developers.cloudflare.com/tunnel/) is a convenient
option for reaching a home automation app away from home. Run `cloudflared`
on the Pi and map a hostname in your Cloudflare-managed domain, such as
`home.example.com`, to `http://localhost:8080`. The connector establishes an
outbound tunnel, so you do not need router port forwarding. Cloudflare serves
the public HTTPS endpoint and manages its edge certificate.

Follow Cloudflare's [self-hosted application guide](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/self-hosted-public-app/).
Use a persistent tunnel and hostname for an installed PWA. Configure an Access
application with an allow policy for your users and enable **Protect with
Access** before making the route available: this starter has hardware controls
and power actions but no application login. HTTPS alone does not restrict users.

The HTTP origin above assumes the connector and app run on the same host and
network namespace; in containers, use the appropriate internal service address.
The browser connection goes through Cloudflare and requires Internet access.

## LAN only: your own domain, local DNS and Caddy

A publicly trusted certificate does **not** require a publicly reachable app.
[Let's Encrypt DNS-01 validation](https://letsencrypt.org/docs/challenge-types/#dns-01-challenge)
proves domain control through a TXT record in public DNS, without connecting to
the web server. This is the recommended option for phones and other devices
where installing a private root certificate would be inconvenient.

For example, with a domain you own and a reserved LAN address for the Pi:

1. Configure your router's DNS, Pi-hole or another local DNS server to resolve
   `pwatest.example.com` to `192.168.1.50`. Have client devices use that DNS server.
   The app hostname needs no public A or AAAA record; its parent domain must
   have working public authoritative DNS for the ACME challenge.
2. Run Caddy on the Pi, proxying HTTPS requests to the app on port 8080.
3. Give Caddy access to the DNS provider's API so it can create and remove
   `_acme-challenge.pwatest.example.com` TXT records and renew the certificate.
4. Keep the app and proxy reachable only from the LAN: no public tunnel or
   router port forwarding, and no Internet ingress through IPv6 either.
5. Open `https://pwatest.example.com` and install the PWA from that address.

With Cloudflare hosting your domain's DNS, an example Caddyfile is:

```caddyfile
pwatest.example.com {
    tls {
        issuer acme {
            dir https://acme-v02.api.letsencrypt.org/directory
            dns cloudflare {env.CF_API_TOKEN}
            resolvers 1.1.1.1
        }
    }
    reverse_proxy 127.0.0.1:8080
}
```

Replace the hostname and address with your own. This requires a Caddy build
including [`dns.providers.cloudflare`](https://github.com/caddy-dns/cloudflare);
the standard binary does not bundle that provider. Supply `CF_API_TOKEN` to
the Caddy service environment, with `Zone:Zone:Read` and `Zone:DNS:Edit`
permissions scoped to your zone. Keep the token out of source control and
persist Caddy's data directory for its certificates and account state.
Other DNS providers work with their corresponding Caddy modules.

Cloudflare is only the DNS provider in this setup; application traffic travels
directly over the LAN. Internet access is needed for certificate issuance and
renewal, but local access continues through an Internet outage while the
certificate remains valid and local DNS works. Publicly trusted certificates
are recorded in public Certificate Transparency logs, so the hostname is not
a secret. Local DNS is not an access-control boundary; the firewall is.

If the hostname fails to resolve on a phone, check whether secure DNS or a VPN
bypasses your LAN resolver. DNS rebinding protection can also require a narrow
exception for your chosen hostname. Always use the certified hostname in the
browser, rather than the Pi's IP address or `.local` alias.

## No domain: a private CA for `.local`

`pwatest.local` cannot receive a publicly trusted certificate. You can instead
use [Caddy's internal CA](https://caddyserver.com/docs/automatic-https#local-https):

```caddyfile
pwatest.local {
    tls internal
    reverse_proxy 127.0.0.1:8080
}
```

Arrange for `pwatest.local` to resolve to the Pi (typically through mDNS), then
install and trust Caddy's root certificate on **every client device/browser**.
Installing it on the server alone does not establish trust on phones. Distribute
only the public root certificate, never its private key. Once trusted, the
connection can work without certificate warnings; clicking through a warning
is not a substitute for the trusted HTTPS context a PWA needs.

This suits a small set of devices you administer and can operate without public
DNS or an Internet connection. For a template used with arbitrary phones,
the owned-domain DNS-01 approach is usually easier for end users.

### boot2vm and installing the root on phones

[boot2vm](https://github.com/mstahv/boot2vm) is a small jbang tool that provisions
a Raspberry Pi (or a local test VM) with Caddy and deploys the packaged app. With
internal HTTPS it also serves Caddy's root certificate for you to install on
clients. See its repository for the current commands; in outline you run its
`init` once to provision the host and write the Caddy site, choosing internal
HTTPS, and a `root-cert` command to fetch the CA certificate onto a phone.

`init` provisions the server and rewrites the Caddy site; it is not just a local
config edit. Internal TLS is preserved during blue-green updates too. No Quarkus
code changes are needed. Preserve Caddy's data directory so redeploying does not
replace the CA already trusted on clients.

On **iPhone/iPad**, transfer the certificate using a trusted channel such as
AirDrop, install the downloaded profile in Settings → General → VPN & Device
Management, then explicitly enable its full trust in Settings → General → About
→ Certificate Trust Settings. Profile installation alone does not enable TLS
trust. See [Apple's instructions](https://support.apple.com/102390).

On **Android**, save the file to Downloads, then open Settings → Security &
privacy → More security settings → Encryption & credentials → Install a
certificate → **CA certificate**. Confirm the warning and device PIN, and select
the file. Labels vary by manufacturer; searching Settings for “CA certificate”
is often easiest. Do not select Wi-Fi or VPN/client certificate. See
[Android's certificate settings](https://support.google.com/pixelphone/answer/2844832).
Native apps may use a separate trust policy; test the PWA in Safari/Chrome.

The root is a device-wide trust decision, not an exception for one hostname.
Only install your own CA, transfer only its public certificate, and remove it
when no longer used. Reopen the HTTPS address without warnings before installing
the PWA. These phone steps have been checked against documentation, not tested
on physical devices as part of this template change.
