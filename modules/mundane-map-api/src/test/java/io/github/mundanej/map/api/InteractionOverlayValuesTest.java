package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class InteractionOverlayValuesTest {
    @Test
    void eventsRequireRealTransitionsAndPreserveCompleteKeys() {
        MapHit first = new MapHit("one", "feature");
        MapHit second = new MapHit("two", "feature");
        MapHoverEvent hover = new MapHoverEvent(Optional.of(first), Optional.of(second));
        assertEquals(first, hover.previous().orElseThrow());
        assertEquals(second, hover.current().orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapHoverEvent(Optional.of(first), Optional.of(first)));

        FeatureSelection selection = new FeatureSelection("one", "feature");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MapSelectionEvent(Optional.of(selection), Optional.of(selection)));
        assertTrue(
                new MapSelectionEvent(Optional.empty(), Optional.of(selection))
                        .previous()
                        .isEmpty());
    }

    @Test
    void defaultBundlesHaveExactRolesAndDistinctSourceListedTreatments() {
        FeatureOverlaySymbols hover = FeatureOverlaySymbols.defaultHover();
        FeatureOverlaySymbols selection = FeatureOverlaySymbols.defaultSelection();

        assertEquals(SymbolRole.MARKER, hover.marker().role());
        assertEquals(SymbolRole.LINE, hover.line().role());
        assertEquals(SymbolRole.FILL, hover.fill().role());
        assertEquals(18.0, ((VectorMarkerSymbol) hover.marker()).placement().size().width());
        assertEquals(14.0, ((VectorMarkerSymbol) selection.marker()).placement().size().width());
        assertTrue(!hover.equals(selection));
    }

    @Test
    void bundleRejectsMismatchedRoles() {
        FeatureOverlaySymbols defaults = FeatureOverlaySymbols.defaultHover();
        FillSymbol dishonest =
                new FillSymbol() {
                    @Override
                    public SymbolRole role() {
                        return SymbolRole.LINE;
                    }

                    @Override
                    public SymbolRendererKey rendererKey() {
                        return new SymbolRendererKey("test.dishonest");
                    }

                    @Override
                    public double opacity() {
                        return 1.0;
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeatureOverlaySymbols(defaults.marker(), defaults.line(), dishonest));
    }
}
