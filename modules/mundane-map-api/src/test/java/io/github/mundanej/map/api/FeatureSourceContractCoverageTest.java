package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeatureSourceContractCoverageTest {
    @Test
    void canonicalAttributesCoverEveryLevelOneValueAndRejection() {
        byte[] bytes = {4};
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("text", "value");
        input.put("logical", true);
        input.put("byte", (byte) 1);
        input.put("short", (short) 2);
        input.put("integer", 3);
        input.put("long", 4L);
        input.put("float", 5.5F);
        input.put("double", 6.5D);
        input.put("bigInteger", BigInteger.TEN);
        input.put("decimal", BigDecimal.ONE);
        input.put("date", LocalDate.of(2020, 1, 2));
        input.put("null", AttributeNull.INSTANCE);
        input.put("bytes", bytes);
        Map<String, Object> values = AttributeValues.canonicalize(input);
        assertEquals(1L, values.get("byte"));
        assertEquals(5.5D, values.get("float"));
        assertEquals(BigDecimal.TEN, values.get("bigInteger"));
        bytes[0] = 9;
        assertEquals(4, ((AttributeBytes) values.get("bytes")).byteAt(0));
        assertThrows(
                NullPointerException.class,
                () -> AttributeValues.canonicalize(java.util.Collections.singletonMap("x", null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValues.canonicalize(Map.of("x", Double.NaN)));
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributeValues.canonicalize(Map.of("x", new int[] {1})));
    }

    @Test
    void multipartFactoriesPreserveOrderEqualityOffsetsAndCompleteEnvelope() {
        MultiPointGeometry points =
                new MultiPointGeometry(CoordinateSequence.of(4, 5, -2, 8, 3, -1));
        assertEquals(new Envelope(-2, -1, 4, 8), points.envelope());
        MultiLineStringGeometry lines =
                MultiLineStringGeometry.ofParts(
                        List.of(
                                CoordinateSequence.of(0, 0, 1, 1),
                                CoordinateSequence.of(8, 2, 9, 3, 10, 4)));
        assertEquals(2, lines.partCount());
        assertEquals(2, lines.partOffset(1));
        assertEquals(lines, MultiLineStringGeometry.of(lines.coordinates(), lines.partOffsets()));
        assertNotEquals(
                lines, MultiLineStringGeometry.ofParts(List.of(CoordinateSequence.of(0, 0, 1, 1))));

        PolygonGeometry first =
                new PolygonGeometry(
                        CoordinateSequence.of(0, 0, 2, 0, 2, 2, 0, 0),
                        List.of(CoordinateSequence.of(10, 10, 11, 10, 11, 11, 10, 10)));
        PolygonGeometry second =
                new PolygonGeometry(CoordinateSequence.of(-4, -3, -2, -3, -2, -1, -4, -3));
        MultiPolygonGeometry polygons = MultiPolygonGeometry.ofPolygons(List.of(first, second));
        assertEquals(2, polygons.polygonCount());
        assertEquals(3, polygons.ringCount());
        assertEquals(new Envelope(-4, -3, 11, 11), polygons.envelope());
        int[] ringOffsets = polygons.ringOffsets();
        int[] polygonOffsets = polygons.polygonRingOffsets();
        ringOffsets[1] = 1;
        polygonOffsets[1] = 0;
        assertArrayEquals(new int[] {0, 4, 8, 12}, polygons.ringOffsets());
        assertArrayEquals(new int[] {0, 2, 3}, polygons.polygonRingOffsets());
        assertEquals(
                polygons,
                MultiPolygonGeometry.of(
                        polygons.coordinates(),
                        polygons.ringOffsets(),
                        polygons.polygonRingOffsets()));
    }

    @Test
    void multipartOffsetCardinalityAndClosureFailuresAreRejected() {
        CoordinateSequence line = CoordinateSequence.of(0, 0, 1, 1, 2, 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiLineStringGeometry.of(line, new int[] {1, 3}));
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiLineStringGeometry.of(line, new int[] {0, 1, 3}));
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiLineStringGeometry.of(line, new int[] {0, 2}));
        CoordinateSequence ring = CoordinateSequence.of(0, 0, 1, 0, 1, 1, 0, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiPolygonGeometry.of(ring, new int[] {0, 4}, new int[] {1, 1}));
        assertThrows(
                IllegalArgumentException.class,
                () -> MultiPolygonGeometry.of(ring, new int[] {0, 4}, new int[] {0, 0, 1}));
    }

    @Test
    void schemasQueriesDiagnosticsAndLimitsAreBoundedAndOrdered() {
        AttributeSchema schema =
                new AttributeSchema(
                        List.of(
                                new AttributeField("name", AttributeType.TEXT, false),
                                new AttributeField("count", AttributeType.INTEGER, true)));
        assertTrue(schema.field("name").orElseThrow().accepts("value"));
        assertTrue(schema.field("count").orElseThrow().accepts(AttributeNull.INSTANCE));
        assertFalse(schema.field("count").orElseThrow().accepts("wrong"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AttributeSchema(
                                List.of(
                                        new AttributeField("name", AttributeType.TEXT, false),
                                        new AttributeField("name", AttributeType.TEXT, true))));

        FeatureQuery query =
                new FeatureQuery(
                        Optional.of(new Envelope(0, 0, 1, 1)),
                        AttributeSelection.only(List.of("count", "name")),
                        Optional.empty());
        assertEquals(List.of("count", "name"), query.attributes().orderedNames());
        assertEquals(AttributeSelection.ALL, FeatureQuery.all().attributes());
        FeatureQueryLimits tight = new FeatureQueryLimits(1, 2, 3, 4, 5, 6, 7);
        assertTrue(tight.tightens(new FeatureQueryLimits(2, 3, 4, 5, 6, 7, 8)));
        assertFalse(
                new FeatureQueryLimits(3, 2, 3, 4, 5, 6, 7)
                        .tightens(new FeatureQueryLimits(2, 3, 4, 5, 6, 7, 8)));

        SourceDiagnostic warning =
                new SourceDiagnostic(
                        "SOURCE_WARNING",
                        DiagnosticSeverity.WARNING,
                        "source",
                        Optional.empty(),
                        "warning",
                        Map.of("z", "last", "a", "first"));
        assertEquals(List.of("a", "z"), List.copyOf(warning.context().keySet()));
        assertEquals(List.of(warning), new DiagnosticReport(List.of(warning), 2).entries());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SourceDiagnostic(
                                "bad-code",
                                DiagnosticSeverity.ERROR,
                                "source",
                                Optional.empty(),
                                "bad",
                                Map.of()));
    }

    @Test
    void schemaOwnsOrderedFieldsAndHasValueSemantics() {
        List<AttributeField> input = new ArrayList<>();
        input.add(new AttributeField("a", AttributeType.TEXT, false));
        input.add(new AttributeField("b", AttributeType.INTEGER, true));
        AttributeSchema schema = new AttributeSchema(input);
        input.clear();
        assertEquals(
                List.of("a", "b"), schema.fields().stream().map(AttributeField::name).toList());
        assertThrows(
                UnsupportedOperationException.class,
                () -> schema.fields().add(new AttributeField("c", AttributeType.DATE, true)));
        AttributeSchema equal =
                new AttributeSchema(
                        List.of(
                                new AttributeField("a", AttributeType.TEXT, false),
                                new AttributeField("b", AttributeType.INTEGER, true)));
        assertEquals(schema, equal);
        assertEquals(schema.hashCode(), equal.hashCode());
        assertEquals("b", schema.field("b").orElseThrow().name());
        assertTrue(schema.toString().contains("AttributeSchema"));
    }
}
