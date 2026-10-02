package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * A page outside the application's layout (sign-in, registration) that centres a
 * card in the viewport. It takes its height from the viewport itself rather than
 * from html/body/#outlet, which the previous layout may have left at
 * {@code height: auto}, and keeps its content clear of the status bar / dynamic
 * island and the home indicator when an installed PWA runs edge to edge.
 */
abstract class StandalonePage extends VerticalLayout {

    StandalonePage() {
        setWidthFull();
        // svh: the small viewport, so the page never overflows while browser chrome shows
        setMinHeight("100svh");
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        getStyle().setPaddingTop("max(1rem, env(safe-area-inset-top))")
                .setPaddingBottom("max(1rem, env(safe-area-inset-bottom))");
    }
}
