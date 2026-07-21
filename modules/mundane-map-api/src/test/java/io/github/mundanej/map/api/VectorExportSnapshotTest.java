package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    }

    @Test
    void rejectsUnsupportedDescendantsAndIllegalXmlScalarsWithStableContext() {
        SolidLineSymbol endpointLine =
                SolidLineSymbol.of(stroke(), Optional.empty(), Optional.of(MARKER), 1);
        VectorExportSnapshotException unsupported =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                snapshot(
                                        List.of(
                                                new VectorExportSnapshot.Primitive(
                                                        0,
                                                        2,
                                                        new LineStringGeometry(
                                                                CoordinateSequence.of(0, 0, 1, 1)),
                                                        endpointLine)),
                                        List.of(),
                                        VectorExportSnapshotLimits.defaults()));
        assertEquals("VECTOR_EXPORT_SYMBOL_UNSUPPORTED", unsupported.problem().code());
        assertEquals(
                List.of("layerIndex", "featureIndex", "symbolOrdinal", "kind"),
                unsupported.problem().context().keySet().stream().toList());

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
