package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PointLabelValuesTest {
    private static final LabelTextStyle STYLE =
            new LabelTextStyle(Rgba.rgb(20, 30, 40), LabelWeight.NORMAL, 12);

    @Test
    void profileDefensivelyCopiesOrderedPositionsAndCompatibilityIsExact() {
        ArrayList<PointLabelPosition> positions =
                new ArrayList<>(List.of(PointLabelPosition.NE, PointLabelPosition.SW));
        PointLabelProfile profile =
                new PointLabelProfile(
                        new TextAttribute("label"),
                        STYLE,
                        positions,
                        4,
                        -2,
                        3,
                        1,
                        -10,
                        new ResolutionRange(0.5, 2));
        positions.clear();

        assertEquals(List.of(PointLabelPosition.NE, PointLabelPosition.SW), profile.positions());
        PointLabelProfile compatibility = PointLabelProfile.compatibility();
        assertEquals(FeatureName.INSTANCE, compatibility.textSource());
        assertEquals(List.of(PointLabelPosition.NE), compatibility.positions());
        assertEquals(Rgba.rgb(32, 32, 32), compatibility.style().color());
        assertEquals(LabelWeight.NORMAL, compatibility.style().weight());
        assertEquals(12, compatibility.style().sizePixels());
        assertEquals(4, compatibility.gapPixels());
        assertEquals(1, compatibility.collisionPaddingPixels());
        assertEquals(ResolutionRange.ALL, compatibility.visibleResolution());
    }

    @Test
    void profileRejectsEveryInvalidCollectionAndNumericBoundary() {
        assertThrows(NullPointerException.class, () -> profile(null, 0, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PointLabelProfile(
                                FeatureName.INSTANCE,
                                STYLE,
                                List.of(),
                                0,
                                0,
                                0,
                                0,
                                0,
                                ResolutionRange.ALL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PointLabelProfile(
                                FeatureName.INSTANCE,
                                STYLE,
                                List.of(PointLabelPosition.N, PointLabelPosition.N),
                                0,
                                0,
                                0,
                                0,
                                0,
                                ResolutionRange.ALL));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile(List.of(PointLabelPosition.N), -1, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile(List.of(PointLabelPosition.N), 65, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile(List.of(PointLabelPosition.N), 0, -257, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile(List.of(PointLabelPosition.N), 0, 257, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile(List.of(PointLabelPosition.N), 0, 0, -257, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile(List.of(PointLabelPosition.N), 0, 0, 257, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile(List.of(PointLabelPosition.N), 0, 0, 0, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile(List.of(PointLabelPosition.N), 0, 0, 0, 65));
        assertThrows(
                IllegalArgumentException.class,
                () -> profile(List.of(PointLabelPosition.N), Double.NaN, 0, 0, 0));

        PointLabelProfile exact = profile(List.of(PointLabelPosition.N), 64, -256, 256, 64);
        assertEquals(64, exact.gapPixels());
    }

    @Test
    void textStyleResolutionAndScreenBoxEnforceExactPublicBounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabelTextStyle(Rgba.TRANSPARENT, LabelWeight.NORMAL, 12));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabelTextStyle(Rgba.rgb(0, 0, 0), LabelWeight.NORMAL, 5.99));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabelTextStyle(Rgba.rgb(0, 0, 0), LabelWeight.BOLD, 72.01));
        assertEquals(6, new LabelTextStyle(Rgba.rgb(0, 0, 0), LabelWeight.NORMAL, 6).sizePixels());
        assertEquals(72, new LabelTextStyle(Rgba.rgb(0, 0, 0), LabelWeight.BOLD, 72).sizePixels());

        ResolutionRange range = new ResolutionRange(0.5, 2);
        assertTrue(range.includes(0.5));
        assertTrue(range.includes(2));
        assertFalse(range.includes(0.49));
        assertThrows(IllegalArgumentException.class, () -> new ResolutionRange(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ResolutionRange(2, 1));
        assertThrows(IllegalArgumentException.class, () -> range.includes(Double.NaN));

        ScreenBox box = new ScreenBox(1, 2, 3, 4);
        assertEquals(new ScreenBox(2, 1, 4, 3), box.translated(1, -1));
        assertEquals(new ScreenBox(0, 1, 4, 5), box.expanded(1));
        assertThrows(IllegalArgumentException.class, () -> new ScreenBox(2, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> assertEquals(box, box.expanded(-1)));
    }

    @Test
    void portrayalRequiresMarkerForLabelAndRetainsLabelInValueIdentity() {
        Symbol marker = new TestSymbol(SymbolRole.MARKER);
        PointLabelProfile label = PointLabelProfile.compatibility();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FeaturePortrayal(
                                Optional.empty(),
                                Optional.of(
                                        new FixedSymbolSelector(new TestSymbol(SymbolRole.LINE))),
                                Optional.empty(),
                                Optional.of(label)));

        FeaturePortrayal plain = FeaturePortrayal.markers(new FixedSymbolSelector(marker));
        FeaturePortrayal labeled = plain.withPointLabel(label);
        assertTrue(labeled.pointLabel().isPresent());
        assertFalse(plain.equals(labeled));
        assertEquals(labeled, plain.withPointLabel(label));
    }

    @Test
    void placedValuesAndProblemsRetainBoundedToolkitNeutralData() {
        ScreenBox visual = new ScreenBox(1, 2, 3, 4);
        PlacedPointLabel label =
                new PlacedPointLabel("layer", "feature", "text", STYLE, 1, 2, 3, visual, visual, 4);
        assertEquals(4, label.ordinaryPaintOrdinal());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlacedPointLabel(
                                "layer", "feature", "text", STYLE, 1, 2, -1, visual, visual, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlacedPointLabel(
                                "layer", "feature", " ", STYLE, 1, 2, 3, visual, visual, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlacedPointLabel(
                                "layer",
                                "feature",
                                "first\nsecond",
                                STYLE,
                                1,
                                2,
                                3,
                                visual,
                                visual,
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PlacedPointLabel(
                                "layer",
                                "feature",
                                "x".repeat(257),
                                STYLE,
                                1,
                                2,
                                3,
                                visual,
                                visual,
                                0));
        assertEquals(1, PointLabelTexts.requireSupported("\uD83D\uDE80"));

        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("layerIndex", "1");
        context.put("featureIndex", "2");
        LabelPlacementProblem problem =
                new LabelPlacementProblem("LABEL_METRICS_NON_FINITE", "metrics failed", context);
        context.clear();
        assertEquals(
                List.of("layerIndex", "featureIndex"), List.copyOf(problem.context().keySet()));
        assertEquals(problem, new LabelPlacementException(problem).problem());
        assertThrows(
                IllegalArgumentException.class,
                () -> new LabelPlacementProblem("UNKNOWN", "bad", Map.of()));
    }

    private static PointLabelProfile profile(
            List<PointLabelPosition> positions,
            double gap,
            double offsetX,
            double offsetY,
            double padding) {
        return new PointLabelProfile(
                FeatureName.INSTANCE,
                STYLE,
                positions,
                gap,
                offsetX,
                offsetY,
                padding,
                0,
                ResolutionRange.ALL);
    }

    private record TestSymbol(SymbolRole role, SymbolRendererKey rendererKey) implements Symbol {
        private TestSymbol(SymbolRole role) {
            this(role, new SymbolRendererKey("test.label"));
        }

        @Override
        public double opacity() {
            return 1;
        }
    }
}
