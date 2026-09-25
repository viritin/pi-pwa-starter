# Pi Java PWA Starter

A small Quarkus + Vaadin IoT application, split into independently buildable
Maven projects:

- **example-project**: runnable PWA, theme, navigation, browserless view tests and a browser smoke test.
- **pi-helpers**: reusable prototyping panels: System (host diagnostics, interfaces,
  power), GPIO, I²C, PWM/servo and 1-Wire, plus the shared Pi4J context.

Requires JDK 25. Build both modules, run the browserless view tests and the Playwright smoke test:

```sh
./mvnw verify
```

Start the example directly from its own directory with simulated BME280 data
and the other hardware helpers in simulation mode:

```sh
cd example-project
./mvnw -Psimulation quarkus:dev
```

The BME280 simulation lives in `example-project/src/simulation/java` and is
only added by the `simulation` Maven profile (or to test sources for tests).
It is a CDI alternative for the measurement source, so the regular sampling,
history and view code still runs. Its deterministic values and history can be
edited without changing production code. The profile uses its own build output
directory and disables host power actions.

The simulation currently covers the climate sensor. Other helper panels have
their existing simulated behavior, enabled by the profile for local
development.

The root POM only aggregates both projects. Build everything with
`./mvnw verify`, or install just the reusable library with
`./mvnw -pl pi-helpers install`. The example has its own POM and wrapper and
resolves `pi-helpers` as a normal versioned dependency, so it can be opened and
run on its own after that library version is available locally or from Maven
Central.

See [the example README](example-project/README.md) for application details and
[Pi Helpers](pi-helpers/README.md) for the reusable component.
