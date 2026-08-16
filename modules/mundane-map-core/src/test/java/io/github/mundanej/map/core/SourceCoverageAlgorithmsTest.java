package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeBytes;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeValueCandidate;
import io.github.mundanej.map.api.AttributeValueConversion;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DimensionalGeometry;
import io.github.mundanej.map.api.EmptyGeometry;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.GeometryCollection;
import io.github.mundanej.map.api.GeometryDimension;
import io.github.mundanej.map.api.GeometryKind;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SourceCoverageAlgorithmsTest {
    private static final SymbolStroke RED_SCREEN =
            new SymbolStroke(Rgba.rgb(255, 0, 0), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL));
    private static final SymbolStroke BLUE_SCREEN =
            new SymbolStroke(Rgba.rgb(0, 0, 255), new SymbolLength(4, SymbolUnit.SCREEN_PIXEL));

    @Test
    void closedAttributeConversionsMatchEcmaNumberAndStringSemantics() {
        assertEquals(
                ThematicValue.text("true"),
                convert(true, AttributeValueConversion.TO_STRING).orElseThrow());
        assertEquals(
                ThematicValue.numeric(1),
                convert(true, AttributeValueConversion.TO_NUMBER).orElseThrow());
        assertEquals(
                ThematicValue.numeric(0),
                convert(AttributeNull.INSTANCE, AttributeValueConversion.TO_NUMBER).orElseThrow());
        assertEquals(
                ThematicValue.numeric(0),
                convert("\u00a0\u3000", AttributeValueConversion.TO_NUMBER).orElseThrow());
        assertEquals(
                ThematicValue.numeric(255),
                convert(" 0xFf ", AttributeValueConversion.TO_NUMBER).orElseThrow());
        assertEquals(
                ThematicValue.numeric(5),
                convert("0b101", AttributeValueConversion.TO_NUMBER).orElseThrow());
        assertEquals(
                ThematicValue.numeric(8),
                convert("0o10", AttributeValueConversion.TO_NUMBER).orElseThrow());
        assertEquals(
                ThematicValue.numeric(new BigDecimal("1.25")),
                convert("\ufeff1.25\u2029", AttributeValueConversion.TO_NUMBER).orElseThrow());
        assertTrue(convert("not-a-number", AttributeValueConversion.TO_NUMBER).isEmpty());
        assertTrue(
                convert(new AttributeBytes(new byte[0]), AttributeValueConversion.TO_NUMBER)
                        .isEmpty());
        assertEquals(
                ThematicValue.text("kept"),
                convert("kept", AttributeValueConversion.IDENTITY).orElseThrow());

        AttributeValueConversion candidates =
                AttributeValueConversion.toNumber(
                        List.of(
                                new AttributeValueCandidate.Attribute("missing"),
                                new AttributeValueCandidate.Literal(ThematicValue.text("12"))));
        assertEquals(
                ThematicValue.numeric(0),
                AttributeValueConversions.convert("ignored", candidates, Map.of()).orElseThrow());
        AttributeValueConversion fallbackCandidate =
                AttributeValueConversion.toNumber(
                        List.of(
                                new AttributeValueCandidate.Literal(ThematicValue.text("bad")),
                                new AttributeValueCandidate.Literal(ThematicValue.numeric(7))));
        assertEquals(
                ThematicValue.numeric(7),
                AttributeValueConversions.convert("ignored", fallbackCandidate, Map.of())
                        .orElseThrow());
    }

    @Test
    void labelStringificationUsesCanonicalPlainAndScientificForms() {
        assertEquals("", LabelTextValues.stringify(AttributeNull.INSTANCE));
        assertEquals("text", LabelTextValues.stringify("text"));
        assertEquals("false", LabelTextValues.stringify(false));
        assertEquals("-4", LabelTextValues.stringify(-4L));
        assertEquals("1.25", LabelTextValues.stringify(1.25));
        assertEquals("0", LabelTextValues.stringify(new BigDecimal("-0.000")));
        assertEquals("1e+21", LabelTextValues.stringify(new BigDecimal("1e21")));
        assertEquals("-1.2e-7", LabelTextValues.stringify(new BigDecimal("-0.00000012")));
        assertEquals("", LabelTextValues.stringify(Double.NaN));
        assertEquals("", LabelTextValues.stringify(LocalDate.of(2026, 7, 26)));
    }

    @Test
    void logicalRecordSizeChargesEveryGeometryAndCanonicalAttributeKind() {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("text", "ab");
        attributes.put("bytes", new AttributeBytes(new byte[] {1, 2}));
        attributes.put("null", AttributeNull.INSTANCE);
        attributes.put("logical", true);
        attributes.put("long", 1L);
        attributes.put("double", 1.5);
        attributes.put("date", LocalDate.of(2026, 7, 26));
        attributes.put("decimal", new BigDecimal("-257"));
        AtomicInteger checkpoints = new AtomicInteger();
        for (int index = 0; index < 4_088; index++) {
            attributes.put("padding" + index, 1L);
        }
        assertTrue(
                FeatureRecordLogicalSize.bytes(
                                record(new PointGeometry(new Coordinate(0, 0)), attributes),
                                2,
                                checkpoints::incrementAndGet)
                        > 0);
        assertEquals(1, checkpoints.get());

        CoordinateSequence line = CoordinateSequence.of(0, 0, 1, 1);
        PolygonGeometry polygon =
                new PolygonGeometry(
                        CoordinateSequence.of(0, 0, 2, 0, 0, 2, 0, 0),
                        List.of(CoordinateSequence.of(0, 0, 1, 0, 0, 1, 0, 0)));
        List<io.github.mundanej.map.api.Geometry> geometries =
                List.of(
                        new io.github.mundanej.map.api.LineStringGeometry(line),
                        polygon,
                        new MultiPointGeometry(line),
                        MultiLineStringGeometry.ofParts(List.of(line)),
                        MultiPolygonGeometry.ofPolygons(List.of(polygon)),
                        DimensionalGeometry.multiLineString(
                                CoordinateSequence.of(GeometryDimension.XYZ, 0, 0, 1, 1, 1, 2),
                                new int[] {0, 2}),
                        DimensionalGeometry.multiPolygon(
                                CoordinateSequence.of(
                                        GeometryDimension.XYM, 0, 0, 1, 2, 0, 2, 0, 2, 3, 0, 0, 1),
                                new int[] {0, 4},
                                new int[] {0, 1},
                                io.github.mundanej.map.api.GeometryLimits.DEFAULT),
                        GeometryCollection.of(
                                List.of(
                                        new EmptyGeometry(
                                                GeometryKind.POINT, GeometryDimension.XYZM),
                                        DimensionalGeometry.point(
                                                CoordinateSequence.of(
                                                        GeometryDimension.XYZM, 1, 2, 3, 4)),
                                        polygon)));
        for (var geometry : geometries) {
            assertTrue(FeatureRecordLogicalSize.bytes(record(geometry, Map.of()), 0) > 0);
        }
    }

    @Test
    void symbolInterpolationCoversTheClosedVectorFamilyAndStructuralFailures() {
        SolidLineSymbol red = SolidLineSymbol.of(RED_SCREEN, 0.5);
        SolidLineSymbol blue = SolidLineSymbol.of(BLUE_SCREEN, 1);
        SolidLineSymbol line = (SolidLineSymbol) SymbolInterpolation.interpolate(red, blue, 0.5);
        assertEquals(Rgba.rgb(128, 0, 128), line.stroke().color());
        assertEquals(3, line.stroke().width().value());
        assertEquals(0.75, line.opacity());

        SolidFillSymbol fill =
                (SolidFillSymbol)
                        SymbolInterpolation.interpolate(
                                SolidFillSymbol.of(Rgba.rgb(0, 0, 0), Optional.of(red), 0.25),
                                SolidFillSymbol.of(Rgba.rgb(200, 100, 50), Optional.of(blue), 0.75),
                                0.5);
        assertEquals(new Rgba(100, 50, 25, 255), fill.fill());
        assertTrue(fill.outline().isPresent());

        VectorMarkerSymbol lower = marker(0, RED_SCREEN);
        VectorMarkerSymbol upper = marker(2, BLUE_SCREEN);
        VectorMarkerSymbol marker =
                (VectorMarkerSymbol) SymbolInterpolation.interpolate(lower, upper, 0.5);
        assertEquals(1, marker.path().ordinateAt(0));
        assertEquals(3, marker.placement().size().width());
        assertEquals(Rgba.rgb(128, 0, 128), marker.stroke().orElseThrow().color());

        CompositeSymbol composite =
                (CompositeSymbol)
                        SymbolInterpolation.interpolate(
                                CompositeSymbol.of(List.of(red), 0.5),
                                CompositeSymbol.of(List.of(blue), 1),
                                0.5);
        assertEquals(1, composite.children().size());
        assertEquals(0.75, composite.opacity());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SymbolInterpolation.interpolate(
                                red,
                                SolidLineSymbol.of(
                                        RED_SCREEN, Optional.of(lower), Optional.empty(), 1),
                                0.5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SymbolInterpolation.interpolate(
                                SolidFillSymbol.of(Rgba.rgb(0, 0, 0), 1),
                                SolidFillSymbol.of(Rgba.rgb(0, 0, 0), Optional.of(blue), 1),
                                0.5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SymbolInterpolation.interpolate(
                                red,
                                SolidLineSymbol.of(
                                        new SymbolStroke(
                                                Rgba.rgb(0, 0, 0),
                                                new SymbolLength(1, SymbolUnit.MAP_UNIT)),
                                        1),
                                0.5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SymbolInterpolation.interpolate(
                                CompositeSymbol.of(List.of(red), 1),
                                CompositeSymbol.of(List.of(blue, blue), 1),
                                0.5));
        VectorMarkerSymbol differentPath =
                VectorMarkerSymbol.of(
                        VectorPath.builder().moveTo(0, 0).cubicTo(0, 0, 1, 1, 2, 0).close().build(),
                        new Envelope(0, 0, 2, 2),
                        Rgba.rgb(0, 0, 0),
                        Optional.of(RED_SCREEN),
                        MarkerPlacement.centeredScreen(2),
                        1);
        assertThrows(
                IllegalArgumentException.class,
                () -> SymbolInterpolation.interpolate(lower, differentPath, 0.5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SymbolInterpolation.interpolate(
                                lower,
                                VectorMarkerSymbol.of(
                                        lower.path(),
                                        lower.viewBox(),
                                        lower.fill(),
                                        lower.stroke(),
                                        new MarkerPlacement(
                                                new SymbolSize(2, 2, SymbolUnit.SCREEN_PIXEL),
                                                SymbolAnchor.NORTH_WEST,
                                                0,
                                                0,
                                                0,
                                                SymbolRotationMode.SCREEN_RELATIVE),
                                        1),
                                0.5));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SymbolInterpolation.interpolate(
                                red,
                                io.github.mundanej.map.api.HatchFillSymbol.of(
                                        HatchPattern.FORWARD_DIAGONAL,
                                        RED_SCREEN,
                                        new SymbolLength(2, SymbolUnit.SCREEN_PIXEL),
                                        SymbolRotationMode.SCREEN_RELATIVE,
                                        1),
                                0.5));
    }

    private static Optional<ThematicValue> convert(
            Object value, AttributeValueConversion conversion) {
        return AttributeValueConversions.convert(value, conversion, Map.of());
    }

    private static FeatureRecord record(
            io.github.mundanej.map.api.Geometry geometry, Map<String, Object> attributes) {
        return new FeatureRecord("id", "name", geometry, attributes);
    }

    private static VectorMarkerSymbol marker(double shift, SymbolStroke stroke) {
        VectorPath path =
                VectorPath.builder()
                        .moveTo(shift, shift)
                        .lineTo(shift + 1, shift)
                        .lineTo(shift, shift + 1)
                        .close()
                        .build();
        return VectorMarkerSymbol.of(
                path,
                new Envelope(shift, shift, shift + 2, shift + 2),
                Rgba.rgb((int) (shift * 50), 0, 0),
                Optional.of(stroke),
                new MarkerPlacement(
                        new SymbolSize(2 + shift, 2 + shift, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        shift,
                        shift,
                        shift,
                        SymbolRotationMode.SCREEN_RELATIVE),
                0.5 + shift / 4);
    }
}
