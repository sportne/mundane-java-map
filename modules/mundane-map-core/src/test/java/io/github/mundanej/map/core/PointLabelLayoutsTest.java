package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.LabelPlacementException;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.ScreenBox;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PointLabelLayoutsTest {
    private static final LabelTextStyle STYLE =
            new LabelTextStyle(Rgba.rgb(10, 20, 30), LabelWeight.BOLD, 12);
    private static final ScreenBox MARKER = new ScreenBox(40, 40, 60, 60);
    private static final ScreenBox VISUAL_AT_ZERO = new ScreenBox(0, -8, 10, 2);

    @Test
    void placesAllEightCompassCandidatesFromTranslatedMarkerAndVisualBounds() {
        assertVisual(PointLabelPosition.N, new ScreenBox(47, 23, 57, 33));
        assertVisual(PointLabelPosition.NE, new ScreenBox(66, 23, 76, 33));
        assertVisual(PointLabelPosition.E, new ScreenBox(66, 42, 76, 52));
        assertVisual(PointLabelPosition.SE, new ScreenBox(66, 61, 76, 71));
        assertVisual(PointLabelPosition.S, new ScreenBox(47, 61, 57, 71));
        assertVisual(PointLabelPosition.SW, new ScreenBox(28, 61, 38, 71));
        assertVisual(PointLabelPosition.W, new ScreenBox(28, 42, 38, 52));
        assertVisual(PointLabelPosition.NW, new ScreenBox(28, 23, 38, 33));
    }

    @Test
    void placementRetainsIdentityMetricsStyleOrderAndCollisionPadding() {
        PlacedPointLabel placed = place(PointLabelPosition.NE);

        assertEquals("layer", placed.layerId());
        assertEquals("feature", placed.featureId());
        assertEquals("label", placed.text());
        assertEquals(STYLE, placed.style());
        assertEquals(66, placed.baselineX());
        assertEquals(31, placed.baselineY());
        assertEquals(11, placed.advance());
        assertEquals(new ScreenBox(65, 22, 77, 34), placed.collisionBounds());
        assertEquals(7, placed.ordinaryPaintOrdinal());
    }

    @Test
    void nonFiniteCandidateArithmeticUsesStableIndexedProblem() {
        PointLabelProfile profile =
                new PointLabelProfile(
                        FeatureName.INSTANCE,
                        STYLE,
                        List.of(PointLabelPosition.N),
                        64,
                        256,
                        0,
                        0,
                        0,
                        ResolutionRange.ALL);

        LabelPlacementException failure =
                assertThrows(
                        LabelPlacementException.class,
                        () ->
                                PointLabelLayouts.place(
                                        "layer",
                                        "feature",
                                        "label",
                                        STYLE,
                                        new ScreenBox(-Double.MAX_VALUE, 0, Double.MAX_VALUE, 1),
                                        VISUAL_AT_ZERO,
                                        11,
                                        profile,
                                        PointLabelPosition.N,
                                        2,
                                        3,
                                        7));

        assertEquals("LABEL_LAYOUT_NON_FINITE", failure.problem().code());
        assertEquals(
                List.of("layerIndex", "featureIndex", "position", "quantity"),
                List.copyOf(failure.problem().context().keySet()));
        assertEquals("2", failure.problem().context().get("layerIndex"));
        assertEquals("3", failure.problem().context().get("featureIndex"));
        assertEquals("N", failure.problem().context().get("position"));
        assertEquals("candidateBounds", failure.problem().context().get("quantity"));
    }

    @Test
    void invalidCallerArgumentsRemainProgrammingErrors() {
        PointLabelProfile profile =
                new PointLabelProfile(
                        FeatureName.INSTANCE,
                        STYLE,
                        List.of(PointLabelPosition.N),
                        64,
                        256,
                        0,
                        0,
                        0,
                        ResolutionRange.ALL);

        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                PointLabelLayouts.place(
                                        " ",
                                        "feature",
                                        "label",
                                        STYLE,
                                        MARKER,
                                        VISUAL_AT_ZERO,
                                        11,
                                        profile,
                                        PointLabelPosition.N,
                                        2,
                                        3,
                                        7));

        assertEquals("layerId must be non-blank", failure.getMessage());
    }

    private static void assertVisual(PointLabelPosition position, ScreenBox expected) {
        assertEquals(expected, place(position).visualBounds());
    }

    private static PlacedPointLabel place(PointLabelPosition position) {
        PointLabelProfile profile =
                new PointLabelProfile(
                        FeatureName.INSTANCE,
                        STYLE,
                        Arrays.asList(PointLabelPosition.values()),
                        4,
                        2,
                        -3,
                        1,
                        0,
                        ResolutionRange.ALL);
        return PointLabelLayouts.place(
                "layer",
                "feature",
                "label",
                STYLE,
                MARKER,
                VISUAL_AT_ZERO,
                11,
                profile,
                position,
                0,
                0,
                7);
    }
}
