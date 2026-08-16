package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DimensionalGeometry;
import io.github.mundanej.map.api.EmptyGeometry;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.GeometryCollection;
import io.github.mundanej.map.api.GeometryDimension;
import io.github.mundanej.map.api.GeometryKind;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GeometryTopologyTest {
    private final Random random = new Random(0x19_011L);

    @Test
    void validityReportsStableOgcStyleReasonsPathsAndLocations() {
        PolygonGeometry bowTie = polygon(0, 0, 4, 4, 0, 4, 4, 0, 0, 0);

        GeometryValidity.Result result = GeometryValidity.check(bowTie);

        assertFalse(result.isValid());
        GeometryValidity.Issue issue = result.issue().orElseThrow();
        assertEquals(GeometryValidity.Reason.RING_SELF_INTERSECTION, issue.reason());
        assertEquals("$/polygon/0/ring/0", issue.geometryPath());
        assertEquals(new Coordinate(2, 2), issue.location().orElseThrow());

        PolygonGeometry outsideHole =
                new PolygonGeometry(
                        sequence(0, 0, 10, 0, 10, 10, 0, 10, 0, 0),
                        List.of(sequence(20, 20, 21, 20, 21, 21, 20, 21, 20, 20)));
        assertEquals(
                GeometryValidity.Reason.HOLE_OUTSIDE_SHELL,
                GeometryValidity.check(outsideHole).issue().orElseThrow().reason());
    }

    @Test
    void validityCoversEmptyDimensionalAndNestedCollectionMatrix() {
        EmptyGeometry empty = new EmptyGeometry(GeometryKind.POLYGON, GeometryDimension.XYZM);
        DimensionalGeometry valid =
                DimensionalGeometry.polygon(
                        CoordinateSequence.of(
                                GeometryDimension.XYM, 0, 0, 1, 2, 0, 2, 2, 2, 3, 0, 2, 3, 0, 0, 1),
                        new int[] {0, 5});
        GeometryCollection collection =
                GeometryCollection.of(
                        List.of(
                                empty,
                                GeometryCollection.of(
                                        List.of(new PointGeometry(new Coordinate(4, 5)), valid))));

        assertTrue(GeometryValidity.check(collection).isValid());

        MultiPolygonGeometry pointTouching =
                MultiPolygonGeometry.ofPolygons(
                        List.of(rectangle(0, 0, 2, 2), rectangle(2, 2, 2, 2)));
        assertTrue(GeometryValidity.check(pointTouching).isValid());
    }

    @Test
    void predicatesCoverBoundaryInteriorHoleMultiAndCollectionSemantics() {
        PolygonGeometry polygon =
                new PolygonGeometry(
                        sequence(0, 0, 10, 0, 10, 10, 0, 10, 0, 0),
                        List.of(sequence(4, 4, 6, 4, 6, 6, 4, 6, 4, 4)));

        assertTrue(GeometryPredicates.intersects(polygon, new PointGeometry(new Coordinate(0, 5))));
        assertTrue(
                GeometryPredicates.intersects(
                        polygon, new LineStringGeometry(sequence(-1, 5, 2, 5))));
        assertFalse(
                GeometryPredicates.intersects(polygon, new PointGeometry(new Coordinate(5, 5))));
        assertFalse(
                GeometryPredicates.intersects(
                        GeometryCollection.of(
                                List.of(
                                        new EmptyGeometry(
                                                GeometryKind.POINT, GeometryDimension.XYZ),
                                        new PointGeometry(new Coordinate(20, 20)))),
                        polygon));
    }

    @Test
    void envelopeClippingInterpolatesZAndMAndPreservesEmptyCollectionOrder() {
        DimensionalGeometry line =
                DimensionalGeometry.lineString(
                        CoordinateSequence.of(GeometryDimension.XYZM, -2, 0, 0, 10, 2, 0, 8, 18));
        EmptyGeometry empty = new EmptyGeometry(GeometryKind.POINT, GeometryDimension.XYM);
        GeometryCollection source = GeometryCollection.of(List.of(empty, line));

        GeometryCollection clipped =
                assertInstanceOf(
                        GeometryCollection.class,
                        GeometryEnvelopeClipper.clip(source, new Envelope(-1, -1, 1, 1)));
        assertSame(empty, clipped.geometries().getFirst());
        DimensionalGeometry result =
                assertInstanceOf(DimensionalGeometry.class, clipped.geometries().get(1));

        assertEquals(GeometryDimension.XYZM, result.dimension());
        assertArrayEquals(new double[] {-1, 0, 2, 12, 1, 0, 6, 16}, result.coordinates().toArray());
    }

    @Test
    void transformPreservesDimensionsTypedEmptinessFencepostsAndCollectionOrder() {
        EmptyGeometry empty = new EmptyGeometry(GeometryKind.LINE_STRING, GeometryDimension.XYM);
        DimensionalGeometry line =
                DimensionalGeometry.multiLineString(
                        CoordinateSequence.of(
                                GeometryDimension.XYZM,
                                0,
                                0,
                                3,
                                4,
                                1,
                                2,
                                5,
                                6,
                                10,
                                10,
                                7,
                                8,
                                12,
                                14,
                                9,
                                10),
                        new int[] {0, 2, 4});
        GeometryCollection source = GeometryCollection.of(List.of(empty, line));

        GeometryCollection transformed =
                assertInstanceOf(
                        GeometryCollection.class,
                        GeometryTransforms.mapXy(
                                source,
                                coordinate ->
                                        new Coordinate(
                                                coordinate.x() + 100, coordinate.y() - 100)));
        assertSame(empty, transformed.geometries().getFirst());
        DimensionalGeometry result =
                assertInstanceOf(DimensionalGeometry.class, transformed.geometries().get(1));

        assertEquals(GeometryDimension.XYZM, result.dimension());
        assertArrayEquals(new int[] {0, 2, 4}, result.partOffsets());
        assertArrayEquals(
                new double[] {
                    100, -100, 3, 4,
                    101, -98, 5, 6,
                    110, -90, 7, 8,
                    112, -86, 9, 10
                },
                result.coordinates().toArray());
    }

    @Test
    void screenHitsUseXyOnlyAndAcceptEmptySequences() {
        CoordinateSequence dimensional =
                CoordinateSequence.of(GeometryDimension.XYM, 0, 0, 50, 10, 0, 60);

        assertTrue(ScreenGeometryHits.polylineWithin(dimensional, false, 5, 0, 0));
        assertFalse(
                ScreenGeometryHits.polylineWithin(
                        CoordinateSequence.empty(GeometryDimension.XYZM), false, 0, 0, 1));
    }

    @Test
    void transformCoversEveryLegacyAndDimensionalFamilyAndBothLimits() {
        PolygonGeometry polygon =
                new PolygonGeometry(
                        sequence(0, 0, 3, 0, 3, 3, 0, 3, 0, 0),
                        List.of(sequence(1, 1, 1, 2, 2, 2, 2, 1, 1, 1)));
        MultiPolygonGeometry multiPolygon = MultiPolygonGeometry.ofPolygons(List.of(polygon));
        GeometryCollection all =
                GeometryCollection.of(
                        List.of(
                                new PointGeometry(new Coordinate(1, 1)),
                                new MultiPointGeometry(sequence(1, 1, 2, 2)),
                                new LineStringGeometry(sequence(0, 0, 1, 1)),
                                MultiLineStringGeometry.ofParts(
                                        List.of(sequence(0, 0, 1, 1), sequence(2, 2, 3, 3))),
                                polygon,
                                multiPolygon,
                                DimensionalGeometry.point(
                                        CoordinateSequence.of(GeometryDimension.XYZ, 1, 2, 3)),
                                DimensionalGeometry.lineString(
                                        CoordinateSequence.of(
                                                GeometryDimension.XYM, 0, 0, 1, 1, 1, 2)),
                                DimensionalGeometry.polygon(
                                        CoordinateSequence.of(
                                                GeometryDimension.XYZ,
                                                0,
                                                0,
                                                1,
                                                2,
                                                0,
                                                2,
                                                2,
                                                2,
                                                3,
                                                0,
                                                2,
                                                4,
                                                0,
                                                0,
                                                1),
                                        new int[] {0, 5}),
                                DimensionalGeometry.multiPoint(
                                        CoordinateSequence.of(
                                                GeometryDimension.XYM, 1, 2, 3, 4, 5, 6)),
                                DimensionalGeometry.multiPolygon(
                                        CoordinateSequence.of(
                                                GeometryDimension.XY, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
                                        new int[] {0, 5},
                                        new int[] {0, 1},
                                        io.github.mundanej.map.api.GeometryLimits.DEFAULT)));

        GeometryCollection transformed =
                assertInstanceOf(
                        GeometryCollection.class,
                        GeometryTransforms.mapXy(
                                all,
                                coordinate -> new Coordinate(-coordinate.x(), coordinate.y())));

        assertEquals(all.geometries().size(), transformed.geometries().size());
        assertInstanceOf(PointGeometry.class, transformed.geometries().get(0));
        assertInstanceOf(MultiPointGeometry.class, transformed.geometries().get(1));
        assertInstanceOf(LineStringGeometry.class, transformed.geometries().get(2));
        assertInstanceOf(MultiLineStringGeometry.class, transformed.geometries().get(3));
        assertInstanceOf(PolygonGeometry.class, transformed.geometries().get(4));
        assertInstanceOf(MultiPolygonGeometry.class, transformed.geometries().get(5));
        for (int index = 6; index < transformed.geometries().size(); index++) {
            assertInstanceOf(DimensionalGeometry.class, transformed.geometries().get(index));
        }

        GeometryTopologyException coordinateLimit =
                assertThrows(
                        GeometryTopologyException.class,
                        () ->
                                GeometryTransforms.mapXy(
                                        polygon,
                                        coordinate -> coordinate,
                                        new GeometryTopologyLimits(4, 10, 10)));
        assertEquals(GeometryTopologyException.COORDINATE_LIMIT, coordinateLimit.code());
        GeometryTopologyException outputLimit =
                assertThrows(
                        GeometryTopologyException.class,
                        () ->
                                GeometryTransforms.mapXy(
                                        polygon,
                                        coordinate -> coordinate,
                                        new GeometryTopologyLimits(20, 10, 4)));
        assertEquals(GeometryTopologyException.OUTPUT_LIMIT, outputLimit.code());
    }

    @Test
    void clipCoversPointsMultipartLinesPolygonsHolesAndEmptyOutputs() {
        Envelope clip = new Envelope(0, 0, 4, 4);
        assertSame(
                GeometryKind.POINT,
                GeometryEnvelopeClipper.clip(new PointGeometry(new Coordinate(1, 1)), clip).kind());
        assertTrue(
                GeometryEnvelopeClipper.clip(new PointGeometry(new Coordinate(9, 9)), clip)
                        .isEmpty());
        MultiPointGeometry points =
                assertInstanceOf(
                        MultiPointGeometry.class,
                        GeometryEnvelopeClipper.clip(
                                new MultiPointGeometry(sequence(-1, -1, 2, 2, 8, 8)), clip));
        assertArrayEquals(new double[] {2, 2}, points.coordinates().toArray());
        DimensionalGeometry dimensionalPoints =
                assertInstanceOf(
                        DimensionalGeometry.class,
                        GeometryEnvelopeClipper.clip(
                                DimensionalGeometry.multiPoint(
                                        CoordinateSequence.of(
                                                GeometryDimension.XYZ, -1, -1, 3, 1, 1, 4)),
                                clip));
        assertEquals(4, dimensionalPoints.coordinates().z(0));

        assertTrue(
                GeometryEnvelopeClipper.clip(new LineStringGeometry(sequence(-2, -2, -1, -1)), clip)
                        .isEmpty());
        MultiLineStringGeometry legacyLines =
                MultiLineStringGeometry.ofParts(
                        List.of(sequence(-1, 1, 2, 1), sequence(2, 3, 5, 3)));
        assertInstanceOf(
                MultiLineStringGeometry.class, GeometryEnvelopeClipper.clip(legacyLines, clip));
        DimensionalGeometry dimensionalLines =
                DimensionalGeometry.multiLineString(
                        CoordinateSequence.of(
                                GeometryDimension.XYM, -1, 1, 0, 2, 1, 3, 2, 3, 4, 5, 3, 6),
                        new int[] {0, 2, 4});
        assertInstanceOf(
                DimensionalGeometry.class, GeometryEnvelopeClipper.clip(dimensionalLines, clip));

        PolygonGeometry withHole =
                new PolygonGeometry(
                        sequence(-1, -1, 5, -1, 5, 5, -1, 5, -1, -1),
                        List.of(sequence(1, 1, 3, 1, 3, 3, 1, 3, 1, 1)));
        PolygonGeometry clippedHole =
                assertInstanceOf(
                        PolygonGeometry.class, GeometryEnvelopeClipper.clip(withHole, clip));
        assertEquals(1, clippedHole.holes().size());
        assertTrue(GeometryEnvelopeClipper.clip(rectangle(10, 10, 2, 2), clip).isEmpty());
        assertInstanceOf(
                MultiPolygonGeometry.class,
                GeometryEnvelopeClipper.clip(
                        MultiPolygonGeometry.ofPolygons(
                                List.of(rectangle(-1, -1, 2, 2), rectangle(2, 2, 4, 4))),
                        clip));
        DimensionalGeometry dimensionalPolygon =
                DimensionalGeometry.polygon(
                        CoordinateSequence.of(
                                GeometryDimension.XYM,
                                -1,
                                -1,
                                0,
                                2,
                                -1,
                                1,
                                2,
                                2,
                                2,
                                -1,
                                2,
                                3,
                                -1,
                                -1,
                                0),
                        new int[] {0, 5});
        assertInstanceOf(
                DimensionalGeometry.class, GeometryEnvelopeClipper.clip(dimensionalPolygon, clip));
    }

    @Test
    void repairCoversLegacyMultiCollectionNoChangeCollapseAndOutputLimit() {
        PolygonGeometry clockwiseWithDuplicate = polygon(0, 0, 0, 3, 0, 3, 3, 3, 3, 0, 0, 0);
        PolygonGeometry repaired =
                assertInstanceOf(
                        PolygonGeometry.class,
                        GeometryCanonicalRepair.repair(
                                clockwiseWithDuplicate,
                                EnumSet.allOf(GeometryCanonicalRepair.Defect.class)));
        assertEquals(5, repaired.exterior().size());
        assertSame(
                repaired,
                GeometryCanonicalRepair.repair(
                        repaired, EnumSet.of(GeometryCanonicalRepair.Defect.RING_ORIENTATION)));

        MultiPolygonGeometry multi =
                MultiPolygonGeometry.ofPolygons(
                        List.of(clockwiseWithDuplicate, clockwiseWithDuplicate));
        assertInstanceOf(
                MultiPolygonGeometry.class,
                GeometryCanonicalRepair.repair(
                        multi, EnumSet.allOf(GeometryCanonicalRepair.Defect.class)));
        GeometryCollection collection =
                GeometryCollection.of(
                        List.of(new PointGeometry(new Coordinate(0, 0)), clockwiseWithDuplicate));
        assertInstanceOf(
                GeometryCollection.class,
                GeometryCanonicalRepair.repair(
                        collection, EnumSet.allOf(GeometryCanonicalRepair.Defect.class)));

        PolygonGeometry collapsed = polygon(0, 0, 0, 0, 0, 0, 0, 0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GeometryCanonicalRepair.repair(
                                collapsed,
                                EnumSet.of(
                                        GeometryCanonicalRepair.Defect.DUPLICATE_RING_POSITIONS)));
        GeometryTopologyException output =
                assertThrows(
                        GeometryTopologyException.class,
                        () ->
                                GeometryCanonicalRepair.repair(
                                        clockwiseWithDuplicate,
                                        EnumSet.allOf(GeometryCanonicalRepair.Defect.class),
                                        new GeometryTopologyLimits(20, 100, 4)));
        assertEquals(GeometryTopologyException.OUTPUT_LIMIT, output.code());
    }

    @Test
    void predicatesCoverAllPrimitivePairDirectionsAndMultiplePolygonComponents() {
        PointGeometry point = new PointGeometry(new Coordinate(1, 1));
        assertTrue(GeometryPredicates.intersects(point, new PointGeometry(new Coordinate(1, 1))));
        assertFalse(GeometryPredicates.intersects(point, new PointGeometry(new Coordinate(2, 2))));
        LineStringGeometry line = new LineStringGeometry(sequence(0, 1, 2, 1));
        assertTrue(GeometryPredicates.intersects(point, line));
        assertFalse(GeometryPredicates.intersects(new PointGeometry(new Coordinate(4, 4)), line));
        PolygonGeometry polygon = rectangle(0, 0, 3, 3);
        assertTrue(GeometryPredicates.intersects(line, polygon));
        assertTrue(GeometryPredicates.intersects(polygon, line));
        assertTrue(GeometryPredicates.intersects(rectangle(-1, -1, 10, 10), rectangle(1, 1, 1, 1)));

        MultiPolygonGeometry first =
                MultiPolygonGeometry.ofPolygons(
                        List.of(rectangle(20, 20, 1, 1), rectangle(0, 0, 4, 4)));
        MultiPolygonGeometry second =
                MultiPolygonGeometry.ofPolygons(
                        List.of(rectangle(30, 30, 1, 1), rectangle(1, 1, 1, 1)));
        assertTrue(GeometryPredicates.intersects(first, second));
    }

    @Test
    void clippingKeepsStableXyLineAndPolygonResults() {
        LineStringGeometry line = new LineStringGeometry(sequence(-2, 0, 0, 0, 2, 0, 0, 2, -2, 2));
        Geometry clippedLine = GeometryEnvelopeClipper.clip(line, new Envelope(-1, -1, 1, 1));
        LineStringGeometry clippedLineString =
                assertInstanceOf(LineStringGeometry.class, clippedLine);
        assertArrayEquals(
                new double[] {-1, 0, 0, 0, 1, 0}, clippedLineString.coordinates().toArray());

        PolygonGeometry polygon = polygon(-2, -2, 2, -2, 2, 2, -2, 2, -2, -2);
        PolygonGeometry clippedPolygon =
                assertInstanceOf(
                        PolygonGeometry.class,
                        GeometryEnvelopeClipper.clip(polygon, new Envelope(-1, -1, 1, 1)));
        assertEquals(new Envelope(-1, -1, 1, 1), clippedPolygon.envelope());
        assertTrue(clippedPolygon.exterior().isClosed());
        assertTrue(GeometryValidity.check(clippedPolygon).isValid());
    }

    @Test
    void repairIsExplicitSelectiveAndDimensionPreserving() {
        DimensionalGeometry polygon =
                DimensionalGeometry.polygon(
                        CoordinateSequence.of(
                                GeometryDimension.XYZM,
                                0,
                                0,
                                10,
                                100,
                                0,
                                2,
                                20,
                                200,
                                0,
                                2,
                                20,
                                200,
                                2,
                                2,
                                30,
                                300,
                                2,
                                0,
                                40,
                                400,
                                0,
                                0,
                                10,
                                100),
                        new int[] {0, 6});

        assertSame(polygon, GeometryCanonicalRepair.repair(polygon, List.of()));
        DimensionalGeometry repaired =
                assertInstanceOf(
                        DimensionalGeometry.class,
                        GeometryCanonicalRepair.repair(
                                polygon,
                                EnumSet.of(
                                        GeometryCanonicalRepair.Defect.DUPLICATE_RING_POSITIONS,
                                        GeometryCanonicalRepair.Defect.RING_ORIENTATION)));

        assertEquals(GeometryDimension.XYZM, repaired.dimension());
        assertEquals(5, repaired.coordinates().size());
        assertArrayEquals(
                new double[] {
                    0, 0, 10, 100,
                    2, 0, 40, 400,
                    2, 2, 30, 300,
                    0, 2, 20, 200,
                    0, 0, 10, 100
                },
                repaired.coordinates().toArray());
    }

    @Test
    void prospectiveLimitsFailAtomicallyWithStableDiagnostics() {
        PolygonGeometry square = polygon(0, 0, 4, 0, 4, 4, 0, 4, 0, 0);

        GeometryTopologyException coordinateFailure =
                assertThrows(
                        GeometryTopologyException.class,
                        () ->
                                GeometryValidity.check(
                                        square, new GeometryTopologyLimits(4, 100, 100)));
        assertEquals(GeometryTopologyException.COORDINATE_LIMIT, coordinateFailure.code());
        assertEquals("maxCoordinates", coordinateFailure.context().get("name"));

        GeometryTopologyException workFailure =
                assertThrows(
                        GeometryTopologyException.class,
                        () ->
                                GeometryPredicates.intersects(
                                        square,
                                        polygon(2, 2, 6, 2, 6, 6, 2, 6, 2, 2),
                                        new GeometryTopologyLimits(20, 1, 100)));
        assertEquals(GeometryTopologyException.COMPARISON_LIMIT, workFailure.code());

        GeometryTopologyException outputFailure =
                assertThrows(
                        GeometryTopologyException.class,
                        () ->
                                GeometryEnvelopeClipper.clip(
                                        square,
                                        new Envelope(0, 0, 4, 4),
                                        new GeometryTopologyLimits(20, 100, 4)));
        assertEquals(GeometryTopologyException.OUTPUT_LIMIT, outputFailure.code());
    }

    @Test
    void independentReferenceCorpusMatchesFrozenExpectedResults() {
        for (String row : referenceRows()) {
            int firstTab = row.indexOf('\t');
            int secondTab = row.indexOf('\t', firstTab + 1);
            String id = row.substring(0, firstTab);
            String operation = row.substring(firstTab + 1, secondTab);
            boolean actual = evaluateReference(id, operation);
            assertEquals(Boolean.parseBoolean(row.substring(secondTab + 1)), actual, id);
        }
    }

    @Test
    void deterministicRectangleFuzzMaintainsValidityAndPredicateSymmetry() {
        for (int iteration = 0; iteration < 250; iteration++) {
            double ax = random.nextInt(100) - 50;
            double ay = random.nextInt(100) - 50;
            double aw = random.nextInt(10) + 1;
            double ah = random.nextInt(10) + 1;
            double bx = random.nextInt(100) - 50;
            double by = random.nextInt(100) - 50;
            double bw = random.nextInt(10) + 1;
            double bh = random.nextInt(10) + 1;
            PolygonGeometry a = rectangle(ax, ay, aw, ah);
            PolygonGeometry b = rectangle(bx, by, bw, bh);

            assertTrue(GeometryValidity.check(a).isValid());
            assertTrue(GeometryValidity.check(b).isValid());
            assertEquals(GeometryPredicates.intersects(a, b), GeometryPredicates.intersects(b, a));
        }
    }

    private static boolean evaluateReference(String id, String operation) {
        Geometry first;
        Geometry second = null;
        switch (id) {
            case "valid-square" -> first = rectangle(0, 0, 4, 4);
            case "bow-tie" -> first = polygon(0, 0, 4, 4, 0, 4, 4, 0, 0, 0);
            case "outside-hole" ->
                    first =
                            new PolygonGeometry(
                                    sequence(0, 0, 5, 0, 5, 5, 0, 5, 0, 0),
                                    List.of(sequence(8, 8, 9, 8, 9, 9, 8, 9, 8, 8)));
            case "overlapping-multipolygon" ->
                    first =
                            MultiPolygonGeometry.ofPolygons(
                                    List.of(rectangle(0, 0, 4, 4), rectangle(2, 2, 4, 4)));
            case "crossing-lines" -> {
                first = new LineStringGeometry(sequence(0, 0, 4, 4));
                second = new LineStringGeometry(sequence(0, 4, 4, 0));
            }
            case "point-in-hole" -> {
                first =
                        new PolygonGeometry(
                                sequence(0, 0, 10, 0, 10, 10, 0, 10, 0, 0),
                                List.of(sequence(4, 4, 6, 4, 6, 6, 4, 6, 4, 4)));
                second = new PointGeometry(new Coordinate(5, 5));
            }
            case "disjoint-polygons" -> {
                first = rectangle(0, 0, 1, 1);
                second = rectangle(2, 2, 1, 1);
            }
            default -> throw new IllegalArgumentException("Unknown reference case " + id);
        }
        return switch (operation) {
            case "validity" -> GeometryValidity.check(first).isValid();
            case "intersects" ->
                    GeometryPredicates.intersects(first, Objects.requireNonNull(second));
            default ->
                    throw new IllegalArgumentException("Unknown reference operation " + operation);
        };
    }

    private static List<String> referenceRows() {
        try (var input =
                GeometryTopologyTest.class.getResourceAsStream("geometry-topology-reference.tsv")) {
            return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static PolygonGeometry rectangle(double x, double y, double width, double height) {
        return polygon(x, y, x + width, y, x + width, y + height, x, y + height, x, y);
    }

    private static PolygonGeometry polygon(double... ordinates) {
        return new PolygonGeometry(sequence(ordinates));
    }

    private static CoordinateSequence sequence(double... ordinates) {
        return CoordinateSequence.of(ordinates);
    }
}
