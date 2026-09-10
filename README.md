# Pi Java PWA Starter

A Maven reactor for a small Quarkus + Vaadin IoT application:

- **example-project**: runnable PWA, theme, navigation and browser integration test.
- **pi-helpers**: reusable System panel, host diagnostics and power controls.

Requires JDK 25. Build both modules and run the Playwright integration test:

```sh
./mvnw verify
```

Start development with both modules in the reactor:

```sh
./mvnw -pl example-project -am quarkus:dev
```

To work from the example directory independently, first install the reactor
artifacts with `./mvnw install`, then run `./mvnw quarkus:dev` there.

See [the example README](example-project/README.md) for application details and
[Pi Helpers](pi-helpers/README.md) for the reusable component.
