# Pi Java PWA Starter

A small Quarkus + Vaadin IoT application, split into independently buildable
Maven projects:

- **pi-starter**: runnable PWA, theme, navigation, browserless view tests and a browser smoke test.
- **pi-helpers**: reusable prototyping panels: System (host diagnostics, interfaces,
  power), GPIO, I²C, PWM/servo, 1-Wire and Bluetooth LE, plus the shared Pi4J context.
- **pi-helpers-simulation**: in-memory hardware for the pi-helpers services, for
  development and tests, and the browserless tests of the Proto Tools panels.

<p align="center">
  <img src="docs/screenshots/system-monitor-desktop.png" height="380"
       alt="System Monitor in a desktop browser, served from a Raspberry Pi 5 running IBM Semeru (OpenJ9)">
  <img src="docs/screenshots/climate-pwa-iphone.png" height="380"
       alt="Climate view installed as a PWA on an iPhone: BME280 temperature gauge and history">
</p>

▶ [Watch it on an iPhone](https://youtube.com/shorts/877Wl0i_BTY) (1:18): passkey
sign-in, adding the app to the home screen and a Web Push temperature alert from the
`example/web-push-notifications` branch.

▶ [Try the live demo](https://pipwa.virit.in): create an account with a passkey, switch
the LED, set a temperature alert. It runs on a Raspberry Pi sized server in the cloud
(1 GB of memory), so the pins and sensors are simulated; demo accounts are removed
after a week. Its setup is the `demo/pipwa.virit.in` branch.

Requires JDK 25. Build both modules, run the browserless view tests and the Playwright 
smoke test and installs the pi-helpers library to local Maven repository:

```sh
./mvnw install
```

Start the example directly from its own directory with simulated BME280 data
and the other hardware helpers in simulation mode:

```sh
cd pi-starter
./mvnw -Psimulation quarkus:dev
```

The `simulation` Maven profile adds `pi-helpers-simulation` (GPIO, I²C, PWM,
1-Wire and BLE) and the example's own simulations in
`pi-starter/src/simulation/java` (the LED and the BME280). Both are CDI
alternatives for the device-facing services, so the regular sampling, history and
view code still runs, and both implement pi-helpers' `Simulated` marker, which
makes the views say that their data is made up. Tests use the same alternatives.
The profile uses its own build output directory and disables host power actions;
the production jar contains no simulation code.

## Deploy to a Raspberry Pi

With SSH access to the Pi (key based, from Raspberry Pi Imager's settings for
example), [boot2vm](https://github.com/mstahv/boot2vm) installs the JDK, a systemd
service and Caddy, and deploys the app, from the `pi-starter` directory:

```sh
jbang app install https://github.com/mstahv/boot2vm/blob/main/Deploy.java
Deploy init
```

`Deploy init` asks for the host and users and does the first deployment. Near the
end, when it asks about hardware access and JVM options, choose the **Raspberry Pi**
preset: the app's user then gets the GPIO, I²C, SPI, serial and Bluetooth groups and
the JVM the native access Pi4J needs. Some features may still need extra
configuration on the Pi and a reboot, for example turning on I²C or 1-Wire; the
setup hints folded into the app's screens show what to run. Afterwards a plain
`Deploy` pushes updates. Until pi-helpers is on Maven Central, run
`./mvnw -pl pi-helpers install` at the repository root once first. More in
[the example README](pi-starter/README.md#deploy-with-boot2vm).

The root POM only aggregates the projects. Build everything with
`./mvnw verify`, or install just the reusable library with
`./mvnw -pl pi-helpers install`. The example has its own POM and wrapper and
resolves `pi-helpers` as a normal versioned dependency, so it can be opened and
run on its own after that library version is available locally or from Maven
Central.

Two integration examples live on branches of their own, to keep `main` lean:

- **`example/web-push-notifications`**: min/max temperature alerts from the Climate
  view to a phone or desktop with Web Push.
- **`example/home-assistant-integration-via-mqtt`**: the Climate readings published
  to Home Assistant over MQTT discovery.

See [the example README](pi-starter/README.md) for application details and
[Pi Helpers](pi-helpers/README.md) for the reusable component.
