package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SnapValuesTest {
    private static final CrsDefinition CRS =
            new CrsDefinition(
                    "LOCAL:SNAP",
                    CrsKind.PROJECTED,
                    new CrsAxis(CrsAxisMeaning.EASTING, CrsUnit.METRE),
                    new CrsAxis(CrsAxisMeaning.NORTHING, CrsUnit.METRE),
                    new Envelope(-100, -100, 100, 100));

    @Test
    void referenceValuesDefensivelyCopyAndRejectAmbiguousIdentities() {
        List<SnapFeature> mutable = new ArrayList<>(List.of(new SnapFeature("a", point(1, 2))));
        SnapReferenceLayer layer = new SnapReferenceLayer("layer", mutable);
        mutable.clear();
        SnapReferenceSet references = new SnapReferenceSet(CRS, List.of(layer));

        assertEquals(1, references.layers().getFirst().features().size());
        assertThrows(UnsupportedOperationException.class, () -> layer.features().clear());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SnapReferenceLayer(
                                "layer",
                                List.of(
                                        new SnapFeature("a", point(0, 0)),
                                        new SnapFeature("a", point(1, 1)))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SnapReferenceSet(CRS, List.of(layer, layer)));
        assertEquals(List.of(), new SnapReferenceSet(CRS, List.of()).layers());
    }

    @Test
    void limitsResultsAndWinnersEnforceClosedVariants() {
        SnapLimits limits =
                SnapLimits.DEFAULT
                        .withMaximumLayers(1)
                        .withMaximumFeatures(2)
                        .withMaximumCoordinates(3)
                        .withMaximumSegments(4);
        assertEquals(new SnapLimits(1, 2, 3, 4), limits);
        assertThrows(IllegalArgumentException.class, () -> new SnapLimits(0, 1, 1, 1));

        SnapResult winner =
                new SnapResult(
                        new Coordinate(1, 2),
                        3,
                        SnapTargetType.VERTEX,
                        "layer",
                        "feature",
                        0,
                        0,
                        0);
        assertEquals(winner, SnapQueryResult.snapped(winner).result().orElseThrow());
        assertEquals(SnapQueryStatus.UNSNAPPED, SnapQueryResult.unsnapped().status());
        FeatureEditProblem problem =
                new FeatureEditProblem("EDIT_SNAP_CANCELLED", "cancelled", Map.of());
        assertEquals(problem, SnapQueryResult.rejected(problem).problem().orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SnapQueryResult(
                                SnapQueryStatus.SNAPPED, Optional.empty(), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SnapResult(
                                new Coordinate(0, 0),
                                -1,
                                SnapTargetType.SEGMENT,
                                "layer",
                                "feature",
                                0,
                                0,
                                0));
    }

    private static PointGeometry point(double x, double y) {
        return new PointGeometry(new Coordinate(x, y));
    }
}
