package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdinateAwareGeometryTest {
    @Test
    void packedSequencesRetainEveryDimensionalModel() {
        for (GeometryDimension dimension : GeometryDimension.values()) {
            double[] values =
                    switch (dimension) {
                        case XY -> new double[] {1, 2, 3, 4};
                        case XYZ -> new double[] {1, 2, 10, 3, 4, 20};
                        case XYM -> new double[] {1, 2, 100, 3, 4, 200};
                        case XYZM -> new double[] {1, 2, 10, 100, 3, 4, 20, 200};
                    };
            CoordinateSequence sequence = CoordinateSequence.of(dimension, values);

            assertEquals(dimension, sequence.dimension());
            assertEquals(2, sequence.size());
            assertArrayEquals(values, sequence.toArray());
            assertEquals(new Envelope(1, 2, 3, 4), sequence.envelope());
            if (dimension.hasZ()) {
                assertEquals(10, sequence.z(0));
            } else {
                assertEquals(
                        GeometryException.ORDINATE_ABSENT,
                        assertThrows(GeometryException.class, () -> sequence.z(0)).code());
            }
            if (dimension.hasM()) {
                assertEquals(100, sequence.m(0));
            } else {
                assertEquals(
                        GeometryException.ORDINATE_ABSENT,
                        assertThrows(GeometryException.class, () -> sequence.m(0)).code());
            }
        }
    }

    @Test
    void sequenceEqualityIncludesDimensionAndStorageRemainsPacked() throws Exception {
        CoordinateSequence xyz = CoordinateSequence.of(GeometryDimension.XYZ, 1, 2, 3, 4, 5, 6);
        CoordinateSequence xym = CoordinateSequence.of(GeometryDimension.XYM, 1, 2, 3, 4, 5, 6);

        assertNotEquals(xyz, xym);
        Field storage = CoordinateSequence.class.getDeclaredField("ordinates");
        assertEquals(double[].class, storage.getType());
        assertFalse(
                List.of(CoordinateSequence.class.getDeclaredFields()).stream()
                        .anyMatch(field -> List.class.isAssignableFrom(field.getType())));
    }

    @Test
    void dimensionalFamiliesValidateFencepostsClosureAndBounds() {
        DimensionalGeometry point =
                DimensionalGeometry.point(
                        CoordinateSequence.of(GeometryDimension.XYZM, 1, 2, 3, 4));
        DimensionalGeometry line =
                DimensionalGeometry.lineString(
                        CoordinateSequence.of(GeometryDimension.XYM, 0, 0, 1, 5, 6, 2));
        DimensionalGeometry polygon =
                DimensionalGeometry.polygon(
                        CoordinateSequence.of(
                                GeometryDimension.XYZ, 0, 0, 1, 2, 0, 1, 2, 2, 1, 0, 0, 1),
                        new int[] {0, 4});

        assertEquals(GeometryKind.POINT, point.kind());
        assertEquals(GeometryKind.LINE_STRING, line.kind());
        assertEquals(GeometryKind.POLYGON, polygon.kind());
        assertEquals(new Envelope(0, 0, 5, 6), line.envelope());
        assertThrows(
                IllegalArgumentException.class,
                () -> DimensionalGeometry.multiLineString(line.coordinates(), new int[] {0, 1, 2}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DimensionalGeometry.polygon(
                                CoordinateSequence.of(
                                        GeometryDimension.XYZ, 0, 0, 1, 2, 0, 1, 2, 2, 1, 0, 0, 9),
                                new int[] {0, 4}));
    }

    @Test
    void dimensionalMultiFactoriesAreImmutableAndValueBased() {
        DimensionalGeometry points =
                DimensionalGeometry.multiPoint(
                        CoordinateSequence.of(GeometryDimension.XYZ, 0, 0, 1, 2, 2, 3));
        int[] lineOffsets = {0, 2, 4};
        CoordinateSequence lineCoordinates =
                CoordinateSequence.of(GeometryDimension.XYM, 0, 0, 1, 1, 1, 2, 3, 2, 4, 5, 3, 6);
        DimensionalGeometry lines =
                DimensionalGeometry.multiLineString(lineCoordinates, lineOffsets);
        int[] rings = {0, 4};
        int[] polygons = {0, 1};
        CoordinateSequence polygonCoordinates =
                CoordinateSequence.of(
                        GeometryDimension.XYZM, 0, 0, 1, 2, 2, 0, 3, 4, 2, 2, 5, 6, 0, 0, 1, 2);
        DimensionalGeometry multiPolygon =
                DimensionalGeometry.multiPolygon(
                        polygonCoordinates, rings, polygons, GeometryLimits.DEFAULT);
        DimensionalGeometry equalMultiPolygon =
                DimensionalGeometry.multiPolygon(
                        polygonCoordinates,
                        new int[] {0, 4},
                        new int[] {0, 1},
                        GeometryLimits.DEFAULT);
        lineOffsets[1] = 1;
        rings[1] = 1;
        polygons[1] = 0;

        assertEquals(GeometryKind.MULTI_POINT, points.kind());
        assertEquals(0, points.partCount());
        assertEquals(GeometryKind.MULTI_LINE_STRING, lines.kind());
        assertArrayEquals(new int[] {0, 2, 4}, lines.partOffsets());
        assertEquals(GeometryKind.MULTI_POLYGON, multiPolygon.kind());
        assertArrayEquals(new int[] {0, 4}, multiPolygon.partOffsets());
        assertArrayEquals(new int[] {0, 1}, multiPolygon.polygonPartOffsets());
        assertEquals(multiPolygon, equalMultiPolygon);
        assertEquals(multiPolygon.hashCode(), equalMultiPolygon.hashCode());
        assertTrue(multiPolygon.toString().contains("MULTI_POLYGON"));
        assertNotEquals(points, lines);
    }

    @Test
    void dimensionalFactoriesEnforceKindsCountsAndLimits() {
        CoordinateSequence single = CoordinateSequence.of(GeometryDimension.XYZ, 0, 0, 1);
        CoordinateSequence line = CoordinateSequence.of(GeometryDimension.XYZ, 0, 0, 1, 1, 1, 2);

        assertThrows(IllegalArgumentException.class, () -> DimensionalGeometry.point(line));
        assertThrows(IllegalArgumentException.class, () -> DimensionalGeometry.lineString(single));
        assertEquals(
                GeometryKind.POINT,
                DimensionalGeometry.point(single, new GeometryLimits(1, 1, 1, 1)).kind());
        assertThrows(
                IllegalArgumentException.class,
                () -> DimensionalGeometry.multiLineString(line, new int[] {1, 2}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DimensionalGeometry.multiPoint(
                                CoordinateSequence.empty(GeometryDimension.XY)));
    }

    @Test
    void typedEmptyValuesHaveExplicitBoundsBehavior() {
        EmptyGeometry empty = new EmptyGeometry(GeometryKind.POLYGON, GeometryDimension.XYM);
        GeometryCollection emptyCollection = GeometryCollection.empty(GeometryDimension.XYZM);

        assertTrue(empty.isEmpty());
        assertTrue(empty.bounds().isEmpty());
        assertEquals(GeometryDimension.XYM, empty.dimension());
        assertEquals(
                GeometryException.EMPTY_ENVELOPE,
                assertThrows(GeometryException.class, empty::envelope).code());
        assertTrue(emptyCollection.isEmpty());
        assertEquals(GeometryDimension.XYZM, emptyCollection.dimension());
        assertEquals(
                GeometryException.EMPTY_ENVELOPE,
                assertThrows(GeometryException.class, emptyCollection::envelope).code());
    }

    @Test
    void mixedCollectionsPreserveOrderDimensionEnvelopeAndTraversal() {
        Geometry point =
                DimensionalGeometry.point(CoordinateSequence.of(GeometryDimension.XYZ, 1, 2, 10));
        Geometry measured =
                DimensionalGeometry.lineString(
                        CoordinateSequence.of(GeometryDimension.XYM, 4, 5, 20, 8, 9, 30));
        Geometry nested =
                GeometryCollection.of(
                        List.of(
                                new EmptyGeometry(GeometryKind.POINT, GeometryDimension.XY),
                                measured));
        GeometryCollection collection = GeometryCollection.of(List.of(point, nested));
        List<String> visits = new ArrayList<>();

        collection.visit((geometry, depth) -> visits.add(depth + ":" + geometry.kind().name()));

        assertEquals(GeometryDimension.XYZM, collection.dimension());
        assertEquals(new Envelope(1, 2, 8, 9), collection.envelope());
        assertEquals(
                List.of(
                        "0:GEOMETRY_COLLECTION",
                        "1:POINT",
                        "1:GEOMETRY_COLLECTION",
                        "2:POINT",
                        "2:LINE_STRING"),
                visits);
        assertThrows(UnsupportedOperationException.class, () -> collection.geometries().add(point));
        GeometryCollection equal = GeometryCollection.of(List.of(point, nested));
        assertEquals(collection, equal);
        assertEquals(collection.hashCode(), equal.hashCode());
        assertTrue(collection.toString().contains("GeometryCollection"));
        assertNotEquals(collection, GeometryCollection.of(List.of(point)));
    }

    @Test
    void collectionConstructionRejectsHostileDepthSizeAndCoordinateCounts() {
        Geometry nested = new EmptyGeometry(GeometryKind.POINT, GeometryDimension.XY);
        for (int depth = 0; depth < 4; depth++) {
            nested = GeometryCollection.of(List.of(nested));
        }
        Geometry finalNested = nested;

        GeometryException depthFailure =
                assertThrows(
                        GeometryException.class,
                        () ->
                                GeometryCollection.of(
                                        List.of(finalNested), new GeometryLimits(10, 10, 10, 3)));
        assertEquals(GeometryException.LIMIT_EXCEEDED, depthFailure.code());
        assertEquals("maxDepth", depthFailure.context().get("limit"));

        GeometryException sizeFailure =
                assertThrows(
                        GeometryException.class,
                        () ->
                                GeometryCollection.of(
                                        List.of(finalNested), new GeometryLimits(10, 10, 3, 10)));
        assertEquals("maxCollectionElements", sizeFailure.context().get("limit"));

        DimensionalGeometry points =
                DimensionalGeometry.multiPoint(CoordinateSequence.of(0, 0, 1, 1, 2, 2));
        GeometryException coordinateFailure =
                assertThrows(
                        GeometryException.class,
                        () ->
                                GeometryCollection.of(
                                        List.of(points), new GeometryLimits(2, 10, 10, 10)));
        assertEquals("maxCoordinates", coordinateFailure.context().get("limit"));

        DimensionalGeometry twoLines =
                DimensionalGeometry.multiLineString(
                        CoordinateSequence.of(0, 0, 1, 1, 2, 2, 3, 3), new int[] {0, 2, 4});
        GeometryException partFailure =
                assertThrows(
                        GeometryException.class,
                        () ->
                                GeometryCollection.of(
                                        List.of(twoLines), new GeometryLimits(10, 1, 10, 10)));
        assertEquals("maxParts", partFailure.context().get("limit"));
    }

    @Test
    void collectionAccountingCoversEveryLegacyPackedFamily() {
        CoordinateSequence lineCoordinates = CoordinateSequence.of(0, 0, 1, 1);
        LineStringGeometry line = new LineStringGeometry(lineCoordinates);
        PolygonGeometry polygon =
                new PolygonGeometry(
                        CoordinateSequence.of(0, 0, 4, 0, 0, 4, 0, 0),
                        List.of(CoordinateSequence.of(1, 1, 2, 1, 1, 2, 1, 1)));
        GeometryCollection collection =
                GeometryCollection.of(
                        List.of(
                                new PointGeometry(new Coordinate(0, 0)),
                                new MultiPointGeometry(lineCoordinates),
                                line,
                                MultiLineStringGeometry.ofParts(List.of(lineCoordinates)),
                                polygon,
                                MultiPolygonGeometry.ofPolygons(List.of(polygon))),
                        new GeometryLimits(100, 100, 100, 2));

        assertFalse(collection.isEmpty());
        assertEquals(new Envelope(0, 0, 4, 4), collection.envelope());
        assertEquals(6, collection.geometries().size());
    }

    @Test
    void geometryKindsDimensionsPoliciesAndPortrayalCategoriesAreClosed() {
        assertEquals(7, GeometryKind.values().length);
        assertEquals(OrdinateLossPolicy.REJECT, OrdinateLossPolicy.valueOf("REJECT"));
        assertEquals(2, OrdinateLossPolicy.values().length);
        assertEquals(GeometryDimension.XYZM, GeometryDimension.XYZ.union(GeometryDimension.XYM));
        assertEquals(GeometryDimension.XYZ, GeometryDimension.XY.union(GeometryDimension.XYZ));
        assertEquals(GeometryDimension.XYM, GeometryDimension.XYM.union(GeometryDimension.XY));
        assertEquals(
                PortrayalGeometryType.POINT,
                PortrayalGeometryType.fromGeometry(
                        DimensionalGeometry.multiPoint(CoordinateSequence.of(0, 0, 1, 1))));
        assertEquals(
                PortrayalGeometryType.LINE_STRING,
                PortrayalGeometryType.fromGeometry(
                        new EmptyGeometry(GeometryKind.MULTI_LINE_STRING, GeometryDimension.XYZ)));
        assertEquals(
                PortrayalGeometryType.POLYGON,
                PortrayalGeometryType.fromGeometry(
                        new EmptyGeometry(GeometryKind.POLYGON, GeometryDimension.XY)));
        GeometryException failure =
                assertThrows(
                        GeometryException.class,
                        () ->
                                PortrayalGeometryType.fromGeometry(
                                        GeometryCollection.empty(GeometryDimension.XY)));
        assertEquals(GeometryException.KIND_UNSUPPORTED, failure.code());
    }
}
