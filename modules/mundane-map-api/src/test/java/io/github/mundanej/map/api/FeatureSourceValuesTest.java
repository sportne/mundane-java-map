package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeatureSourceValuesTest {
    @Test
    void attributesAreCanonicalOrderedAndDefensive() {
        byte[] bytes = {1, 2};
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("integer", 3);
        input.put("bytes", bytes);
        FeatureRecord record =
                new FeatureRecord("id", "", new PointGeometry(new Coordinate(1, 2)), input);
        Map<String, Object> firstAccess = record.attributes();
        input.put("later", "mutation");
        bytes[0] = 9;
        assertEquals(firstAccess, record.attributes());
        assertFalse(record.attributes().containsKey("later"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> record.attributes().put("forbidden", "mutation"));
        assertEquals(List.of("integer", "bytes"), List.copyOf(record.attributes().keySet()));
        assertEquals(3L, record.attributes().get("integer"));
        assertArrayEquals(
                new byte[] {1, 2}, ((AttributeBytes) record.attributes().get("bytes")).toArray());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FeatureRecord(
                                "id",
                                "",
                                new PointGeometry(new Coordinate(1, 2)),
                                Map.of("bad", List.of())));
    }

    @Test
    void packedMultipartValuesOwnOffsetsAndValidateRings() {
        int[] parts = {0, 2, 4};
        MultiLineStringGeometry lines =
                MultiLineStringGeometry.of(CoordinateSequence.of(0, 0, 1, 1, 2, 2, 3, 3), parts);
        parts[1] = 3;
        assertArrayEquals(new int[] {0, 2, 4}, lines.partOffsets());

        PolygonGeometry polygon =
                new PolygonGeometry(CoordinateSequence.of(0, 0, 4, 0, 4, 4, 0, 0));
        MultiPolygonGeometry polygons = MultiPolygonGeometry.ofPolygons(List.of(polygon));
        assertEquals(1, polygons.polygonCount());
        assertEquals(polygon.envelope(), polygons.envelope());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MultiPolygonGeometry.of(
                                CoordinateSequence.of(0, 0, 1, 0, 1, 1, 2, 2),
                                new int[] {0, 4},
                                new int[] {0, 1}));
    }

    @Test
    void selectionAndLimitsRejectAmbiguousValues() {
        assertThrows(IllegalArgumentException.class, () -> AttributeSelection.only(List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> AttributeSelection.only(List.of("a", "a")));
        assertThrows(
                IllegalArgumentException.class, () -> new FeatureQueryLimits(0, 1, 1, 1, 1, 1, 1));
    }
}
