package in.virit.iot;

import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;

/**
 * The cards of the Climate view: outlined, a step above the page, and sized to
 * sit side by side on a wide screen and one per row on a phone.
 */
class ClimateCard extends Card {

    ClimateCard(String title) {
        setTitle(title);
        addThemeVariants(CardVariant.OUTLINED);
        getStyle()
                .setFlexGrow("1")
                .setFlexShrink("1")
                .setFlexBasis("20rem")
                .setMinWidth("0")
                .setMaxWidth("30rem")
                // Aura has no variant for this; the custom property lifts the surface a little further
                .set("--aura-surface-level", "4");
    }
}
