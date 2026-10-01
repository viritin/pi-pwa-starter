# Developer notes

Notes for working *on* this starter, not for using it. None of this is needed to
build, run or adopt the app — it collects the build-time quirks and maintenance
details that would only distract someone starting a project from it.

## Vaadin's cached production bundle

Vaadin keeps a pre-compiled frontend bundle in
`pi-starter/src/main/bundles/prod.bundle` (git-ignored) and reuses it when a
build needs no new frontend imports. With Vaadin 25.3.0 that check ignores the
per-route chunk keys, so a *new view that only uses components other views
already use* gets no chunk in the reused bundle and renders as empty elements
when opened directly. If a new view looks blank after a build, delete
`src/main/bundles` (or build with `-Dvaadin.force.production.build=true`). The
`PwaSmokeIT` smoke test opens every route in a fresh browser to catch this.

## Pi4J 4.0.2 workarounds

The helpers carry three workarounds for Pi4J 4.0.2. Each names its upstream fix;
drop it when `pi4j.version` reaches a release that contains the fix. The
`pi4j-local` profile builds against a locally installed Pi4J
(`./mvnw -DskipTests install` in a checkout of the fork's `my-main`, version
`5.0.0-mstahv-SNAPSHOT`) so the removal can be tried before that release:
`./mvnw -Ppi4j-local test` from `pi-helpers` or `pi-starter`.

| Where | Workaround | Upstream |
|---|---|---|
| `Pi4JContext.release` | `Context.shutdown(id)` instead of `io.close()` | Fixed on `main` by PRs #678 and #726, unreleased |
| `I2cService.scan` | ignores the value `read()` returns, only whether it throws | `I2CDirect.read()` sign-extends; fix on the fork's `fix/i2c-read-unsigned`, PR pending |
| `Pi4JContext.context` | throws a clear message when the FFM plugin loaded no provider | By design upstream (issue #508); report about the misleading exception pending |

PWM is driven through `/sys/class/pwm` directly rather than Pi4J. The released
Pi4J API (4.0.x) only accepts whole-percent duty cycles, too coarse for a servo;
the fix (fractional duty cycles) is in Pi4J's main branch and will arrive in the
next release ([Pi4J/pi4j#613](https://github.com/Pi4J/pi4j/issues/613)). Switch
`PwmService` over then if you prefer one API.

Running the module without Pi4J on the classpath while its Pi4J-backed beans are
present has not been verified; if Quarkus' build-time bean processing complains,
add the two Pi4J artifacts anyway.

## Local reference projects

Some sources were adapted from the maintainer's other projects, kept as local
checkouts (git-ignored) under `related-projects-and-examples/`:
`heisala-jetty/heisala-jetty-server` and `screwcloud/server`. Not needed to build
this repo.
