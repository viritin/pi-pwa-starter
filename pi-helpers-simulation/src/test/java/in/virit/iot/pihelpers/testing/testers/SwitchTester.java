package in.virit.iot.pihelpers.testing.testers;

import com.vaadin.browserless.ComponentTester;
import com.vaadin.browserless.Tests;
import com.vaadin.flow.component.checkbox.Switch;

/**
 * Browserless tester for Vaadin's Switch, which has no built-in tester yet.
 * {@link #toggle()} flips the value the way a user would, so listeners see a
 * client-originated change.
 */
@Tests(Switch.class)
public class SwitchTester extends ComponentTester<Switch> {

    public SwitchTester(Switch component) {
        super(component);
    }

    public void toggle() {
        ensureComponentIsUsable();
        setValueAsUser(!Boolean.TRUE.equals(getComponent().getValue()));
    }
}
