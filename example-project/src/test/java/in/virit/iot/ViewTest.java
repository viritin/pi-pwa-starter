package in.virit.iot;

import java.util.Set;

/**
 * Base for the browserless view tests: pi-helpers' test base, routing this
 * application's views, the Proto Tools included. Subclasses carry
 * {@code @QuarkusTest} themselves: Quarkus registers only directly annotated
 * classes as test beans.
 */
abstract class ViewTest extends in.virit.iot.pihelpers.testing.ViewTest {

    @Override
    protected Set<String> scanPackages() {
        return Set.of(TopLayout.class.getPackageName());
    }
}
