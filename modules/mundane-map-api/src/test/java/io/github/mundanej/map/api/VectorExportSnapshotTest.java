package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class VectorExportSnapshotTest {
    private static final VectorMarkerSymbol MARKER =
            VectorMarkerSymbol.filledScreen(
                    VectorPath.builder().moveTo(0, 0).lineTo(2, 0).lineTo(1, 2).close().build(),
                    new Envelope(0, 0, 2, 2),
                    Rgba.rgb(20, 30, 40),
                    10,
                    1);
    private static final VectorExportSnapshot.ViewFrame FRAME =
            new VectorExportSnapshot.ViewFrame(2, 360, new Coordinate(-0.0, 0));

    @Test
    void ownsOrderedValuesAndHasValueSemantics() {
        List<VectorExportSnapshot.Primitive> input = new ArrayList<>();
        input.add(
                new VectorExportSnapshot.Primitive(
                        0, 0, new PointGeometry(new Coordinate(3, 4)), MARKER));
        VectorExportSnapshot.Label label =
                new VectorExportSnapshot.Label(
                        " Alpha ",
                        new LabelTextStyle(Rgba.rgb(1, 2, 3), LabelWeight.BOLD, 12),
                        -0.0,
                        8,
                        30,
                        7);

        VectorExportSnapshot snapshot =
                VectorExportSnapshot.of(
                        100, 80, Rgba.rgb(255, 255, 255), FRAME, 1, input, List.of(label));
        input.clear();
        VectorExportSnapshot equal =
                VectorExportSnapshot.of(
                        100,
                        80,
                        Rgba.rgb(255, 255, 255),
                        new VectorExportSnapshot.ViewFrame(2, 0, new Coordinate(0, 0)),
                        1,
                        snapshot.primitives(),
                        List.of(label));

        assertEquals(1, snapshot.primitives().size());
        assertEquals(100, snapshot.widthPixels());
        assertEquals(80, snapshot.heightPixels());
        assertEquals(Rgba.rgb(255, 255, 255), snapshot.background());
        assertEquals(FRAME, snapshot.viewFrame());
        assertEquals(1, snapshot.layerCount());
        assertEquals(List.of(label), snapshot.labels());
        assertEquals(0.0, snapshot.viewFrame().mapXAxisScreenBearingDegrees());
        assertEquals(0.0, snapshot.labels().getFirst().baselineX());
        assertEquals(snapshot, equal);
        assertEquals(snapshot.hashCode(), equal.hashCode());
        assertNotSame(input, snapshot.primitives());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.primitives().clear());
    }

    @Test
    void enforcesOrderRolesLimitsAndCancellationBeforePublication() {
        VectorExportSnapshot.Primitive first =
                new VectorExportSnapshot.Primitive(
                        0, 1, new PointGeometry(new Coordinate(0, 0)), MARKER);
        VectorExportSnapshot.Primitive duplicate =
                new VectorExportSnapshot.Primitive(
                        0, 1, new PointGeometry(new Coordinate(1, 1)), MARKER);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        snapshot(
                                List.of(first, duplicate),
                                List.of(),
                                VectorExportSnapshotLimits.defaults()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        snapshot(
                                List.of(
                                        new VectorExportSnapshot.Primitive(
                                                0,
                                                0,
                                                new PointGeometry(new Coordinate(0, 0)),
                                                SolidLineSymbol.of(stroke(), 1))),
                                List.of(),
                                VectorExportSnapshotLimits.defaults()));

        VectorExportSnapshotException limited =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                VectorExportSnapshot.of(
                                        11,
                                        10,
                                        Rgba.TRANSPARENT,
                                        FRAME,
                                        0,
                                        List.of(),
                                        List.of(),
                                        VectorExportSnapshotLimits.defaults()
                                                .withMaximumPageAxis(10)));
        assertEquals("VECTOR_EXPORT_SNAPSHOT_LIMIT_EXCEEDED", limited.problem().code());
        assertEquals(
                List.of("limit", "maximum", "requested"),
                limited.problem().context().keySet().stream().toList());

        VectorExportSnapshotException cancelled =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                VectorExportSnapshot.of(
                                        10,
                                        10,
                                        Rgba.TRANSPARENT,
                                        FRAME,
                                        0,
                                        List.of(),
                                        List.of(),
                                        VectorExportSnapshotLimits.defaults(),
                                        () -> true));
        assertEquals("VECTOR_EXPORT_SNAPSHOT_CANCELLED", cancelled.problem().code());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorExportSnapshot.of(
                                10, 10, Rgba.TRANSPARENT, FRAME, -1, List.of(), List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VectorExportSnapshot.of(
                                10, 10, Rgba.TRANSPARENT, FRAME, 0, List.of(first), List.of()));
        VectorExportSnapshot.Label firstLabel =
                new VectorExportSnapshot.Label(
                        "a",
                        new LabelTextStyle(Rgba.rgb(1, 2, 3), LabelWeight.NORMAL, 10),
                        1,
                        2,
                        0,
                        1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        snapshot(
                                List.of(),
                                List.of(firstLabel, firstLabel),
                                VectorExportSnapshotLimits.defaults()));
    }

    @Test
    void acceptsCompleteVectorTreesAndRejectsUnsupportedLeavesWithStableContext() {
        SolidLineSymbol endpointLine =
                SolidLineSymbol.of(stroke(), Optional.empty(), Optional.of(MARKER), 1);
        VectorExportSnapshot complete =
                snapshot(
                        List.of(
                                new VectorExportSnapshot.Primitive(
                                        0,
                                        2,
                                        MultiLineStringGeometry.of(
                                                CoordinateSequence.of(0, 0, 1, 1, 2, 2, 3, 3),
                                                new int[] {0, 2, 4}),
                                        CompositeSymbol.of(List.of(endpointLine), 0.75))),
                        List.of(),
                        VectorExportSnapshotLimits.defaults());
        assertEquals(1, complete.primitives().size());

        RasterIconSymbol raster =
                RasterIconSymbol.nativeScreenSize(
                        1, 1, new int[] {0xffff0000}, RasterInterpolation.NEAREST, 1);
        VectorExportSnapshotException unsupported =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                snapshot(
                                        List.of(
                                                new VectorExportSnapshot.Primitive(
                                                        0,
                                                        2,
                                                        new PointGeometry(new Coordinate(0, 0)),
                                                        raster)),
                                        List.of(),
                                        VectorExportSnapshotLimits.defaults()));
        assertEquals("VECTOR_EXPORT_SYMBOL_UNSUPPORTED", unsupported.problem().code());
        assertEquals("rasterIcon", unsupported.problem().context().get("kind"));
        assertEquals(
                List.of("layerIndex", "featureIndex", "symbolOrdinal", "kind"),
                unsupported.problem().context().keySet().stream().toList());
    }

    @Test
    void rejectsIllegalXmlScalarsWithStableContext() {
        VectorExportSnapshot.Label invalid =
                new VectorExportSnapshot.Label(
                        "bad\nlabel",
                        new LabelTextStyle(Rgba.rgb(1, 2, 3), LabelWeight.NORMAL, 12),
                        1,
                        2,
                        3,
                        4);
        VectorExportSnapshotException failure =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                snapshot(
                                        List.of(),
                                        List.of(invalid),
                                        VectorExportSnapshotLimits.defaults()));
        assertEquals("VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID", failure.problem().code());
        assertEquals("labelText", failure.problem().context().get("field"));
    }

    @Test
    void enforcesExactSemanticOwnedByteInventory() {
        assertOwnedBoundary(List.of(), List.of(), 128);
        assertOwnedBoundary(
                List.of(
                        new VectorExportSnapshot.Primitive(
                                0, 0, new PointGeometry(new Coordinate(0, 0)), MARKER)),
                List.of(),
                428);
        assertOwnedBoundary(
                List.of(
                        new VectorExportSnapshot.Primitive(
                                0,
                                0,
                                MultiLineStringGeometry.of(
                                        CoordinateSequence.of(0, 0, 1, 1, 2, 2, 3, 3),
                                        new int[] {0, 2, 4}),
                                SolidLineSymbol.of(stroke(), 1))),
                List.of(),
                436);
        assertOwnedBoundary(
                List.of(
                        new VectorExportSnapshot.Primitive(
                                0,
                                0,
                                new PointGeometry(new Coordinate(0, 0)),
                                CompositeSymbol.of(List.of(MARKER), 1))),
                List.of(),
                500);
        assertOwnedBoundary(
                List.of(),
                List.of(
                        new VectorExportSnapshot.Label(
                                "x",
                                new LabelTextStyle(Rgba.rgb(0, 0, 0), LabelWeight.NORMAL, 10),
                                1,
                                2,
                                3,
                                0)),
                202);
    }

    @Test
    void stopsSymbolTraversalAtTheFirstLimitCrossingAndPollsCancellation() {
        CompositeSymbol composite = CompositeSymbol.of(List.of(MARKER, MARKER), 1);
        VectorExportSnapshot.Primitive primitive =
                new VectorExportSnapshot.Primitive(
                        0, 0, new PointGeometry(new Coordinate(0, 0)), composite);
        VectorExportSnapshotException limited =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                snapshot(
                                        List.of(primitive),
                                        List.of(),
                                        VectorExportSnapshotLimits.defaults()
                                                .withMaximumSymbolNodes(2)));
        assertEquals("symbolNodes", limited.problem().context().get("limit"));
        assertEquals("3", limited.problem().context().get("requested"));

        AtomicInteger polls = new AtomicInteger();
        VectorExportSnapshotException cancelled =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                VectorExportSnapshot.of(
                                        10,
                                        10,
                                        Rgba.TRANSPARENT,
                                        FRAME,
                                        1,
                                        List.of(primitive),
                                        List.of(),
                                        VectorExportSnapshotLimits.defaults(),
                                        () -> polls.incrementAndGet() == 7));
        assertEquals("VECTOR_EXPORT_SNAPSHOT_CANCELLED", cancelled.problem().code());
    }

    @Test
    void normalizesAndInventoriesEverySupportedGeometryAndNestedOutline() {
        CoordinateSequence lineCoordinates = CoordinateSequence.of(-0.0, 0, 1, 1);
        CoordinateSequence ring = CoordinateSequence.of(-0.0, 0, 2, 0, 0, 2, -0.0, 0);
        LineStringGeometry line = new LineStringGeometry(lineCoordinates);
        PolygonGeometry polygon = new PolygonGeometry(ring);
        SolidLineSymbol outline = SolidLineSymbol.of(stroke(), 1);
        SolidFillSymbol solid = SolidFillSymbol.of(Rgba.rgb(1, 2, 3), Optional.of(outline), 1);
        HatchFillSymbol hatch =
                HatchFillSymbol.of(
                        HatchPattern.FORWARD_DIAGONAL,
                        stroke(),
                        new SymbolLength(4, SymbolUnit.SCREEN_PIXEL),
                        SymbolRotationMode.SCREEN_RELATIVE,
                        Optional.of(outline),
                        1,
                        10);
        List<VectorExportSnapshot.Primitive> primitives =
                List.of(
                        new VectorExportSnapshot.Primitive(
                                0, 0, new PointGeometry(new Coordinate(-0.0, -0.0)), MARKER),
                        new VectorExportSnapshot.Primitive(
                                0,
                                1,
                                new MultiPointGeometry(CoordinateSequence.of(-0.0, 0, 1, 1)),
                                MARKER),
                        new VectorExportSnapshot.Primitive(0, 2, line, outline),
                        new VectorExportSnapshot.Primitive(
                                0,
                                3,
                                MultiLineStringGeometry.ofParts(List.of(lineCoordinates)),
                                outline),
                        new VectorExportSnapshot.Primitive(0, 4, polygon, solid),
                        new VectorExportSnapshot.Primitive(
                                0, 5, MultiPolygonGeometry.ofPolygons(List.of(polygon)), hatch));

        VectorExportSnapshot snapshot =
                VectorExportSnapshot.of(
                        20,
                        20,
                        Rgba.TRANSPARENT,
                        new VectorExportSnapshot.ViewFrame(1, -90, new Coordinate(-0.0, -0.0)),
                        1,
                        primitives,
                        List.of());

        assertEquals(6, snapshot.primitives().size());
        assertEquals(270, snapshot.viewFrame().mapXAxisScreenBearingDegrees());
        assertEquals(
                0L,
                Double.doubleToRawLongBits(
                        ((PointGeometry) snapshot.primitives().getFirst().screenGeometry())
                                .coordinate()
                                .x()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VectorExportSnapshot.ViewFrame(0, 0, new Coordinate(0, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VectorExportSnapshot.ViewFrame(1, Double.NaN, new Coordinate(0, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new VectorExportSnapshot.Primitive(-1, 0, line, outline));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new VectorExportSnapshot.Label(
                                "x",
                                new LabelTextStyle(Rgba.rgb(0, 0, 0), LabelWeight.NORMAL, 10),
                                Double.NaN,
                                0,
                                0,
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new VectorExportSnapshot.Label(
                                "x",
                                new LabelTextStyle(Rgba.rgb(0, 0, 0), LabelWeight.NORMAL, 10),
                                0,
                                0,
                                0,
                                -1));
    }

    @Test
    void limitCopiesExerciseEveryIndependentHardFence() {
        VectorExportSnapshotLimits defaults = VectorExportSnapshotLimits.defaults();
        assertEquals(10, defaults.withMaximumPageAxis(10).maximumPageAxis());
        assertEquals(10, defaults.withMaximumLayers(10).maximumLayers());
        assertEquals(10, defaults.withMaximumFeatures(10).maximumFeatures());
        assertEquals(10, defaults.withMaximumCoordinates(10).maximumCoordinates());
        assertEquals(10, defaults.withMaximumCompositeDepth(10).maximumCompositeDepth());
        assertEquals(10, defaults.withMaximumSymbolNodes(10).maximumSymbolNodes());
        assertEquals(10, defaults.withMaximumLabels(10).maximumLabels());
        assertEquals(10, defaults.withMaximumLabelCodePoints(10).maximumLabelCodePoints());
        assertEquals(10, defaults.withMaximumOwnedBytes(10).maximumOwnedBytes());

        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumPageAxis(0)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertNotNull(
                                defaults.withMaximumLayers(
                                        VectorExportSnapshotLimits.LAYERS_HARD_MAXIMUM + 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumFeatures(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumCoordinates(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumCompositeDepth(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumSymbolNodes(0)));
        assertThrows(
                IllegalArgumentException.class, () -> assertNotNull(defaults.withMaximumLabels(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumLabelCodePoints(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumOwnedBytes(0)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertNotNull(
                                defaults.withMaximumOwnedBytes(
                                        VectorExportSnapshotLimits.OWNED_BYTES_HARD_MAXIMUM + 1)));
    }

    private static void assertOwnedBoundary(
            List<VectorExportSnapshot.Primitive> primitives,
            List<VectorExportSnapshot.Label> labels,
            long exactBytes) {
        snapshot(
                primitives,
                labels,
                VectorExportSnapshotLimits.defaults().withMaximumOwnedBytes(exactBytes));
        VectorExportSnapshotException failure =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                snapshot(
                                        primitives,
                                        labels,
                                        VectorExportSnapshotLimits.defaults()
                                                .withMaximumOwnedBytes(exactBytes - 1)));
        assertEquals("ownedBytes", failure.problem().context().get("limit"));
        assertEquals(Long.toString(exactBytes), failure.problem().context().get("requested"));
    }

    private static VectorExportSnapshot snapshot(
            List<VectorExportSnapshot.Primitive> primitives,
            List<VectorExportSnapshot.Label> labels,
            VectorExportSnapshotLimits limits) {
        return VectorExportSnapshot.of(
                10, 10, Rgba.TRANSPARENT, FRAME, 1, primitives, labels, limits);
    }

    private static SymbolStroke stroke() {
        return new SymbolStroke(Rgba.rgb(4, 5, 6), new SymbolLength(1, SymbolUnit.SCREEN_PIXEL));
    }
}
