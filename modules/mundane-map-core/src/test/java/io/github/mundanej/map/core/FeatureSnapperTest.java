package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsAxis;
import io.github.mundanej.map.api.CrsAxisMeaning;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.CrsUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.api.SnapFeature;
import io.github.mundanej.map.api.SnapLimits;
import io.github.mundanej.map.api.SnapQueryResult;
import io.github.mundanej.map.api.SnapQueryStatus;
import io.github.mundanej.map.api.SnapReferenceLayer;
import io.github.mundanej.map.api.SnapReferenceSet;
import io.github.mundanej.map.api.SnapResult;
import io.github.mundanej.map.api.SnapTargetType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FeatureSnapperTest {
    private static final MapViewport VIEWPORT = new MapViewport(100, 100, 0, 0, 1);
    private static final CrsOperation IDENTITY =
            CrsRegistry.level1().operation(CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
    private static final CrsDefinition LOCAL_SOURCE = localCrs("LOCAL:SNAP-SOURCE");
    private static final CrsDefinition LOCAL_DISPLAY = localCrs("LOCAL:SNAP-DISPLAY");

    @Test
    void resolvesVerticesAndSegmentsAcrossEveryGeometryFamily() {
        assertWinner(point(2, 3), 52, 47, SnapTargetType.VERTEX, 0, 0, 0);
        assertWinner(
                new MultiPointGeometry(sequence(-5, 0, 4, 2)),
                54,
                48,
                SnapTargetType.VERTEX,
                1,
                0,
                0);
        assertWinner(line(-10, 0, 10, 0), 50, 52, SnapTargetType.SEGMENT, 0, 0, 0);
        assertWinner(
                MultiLineStringGeometry.ofParts(
                        List.of(sequence(-10, -10, -5, -10), sequence(-4, 4, 4, 4))),
                50,
                44,
                SnapTargetType.SEGMENT,
                1,
                0,
                0);

        PolygonGeometry polygon =
                new PolygonGeometry(
                        ring(-10, -10, 10, -10, 10, 10, -10, 10),
                        List.of(ring(-3, -3, 3, -3, 3, 3, -3, 3)));
        assertWinner(polygon, 50, 52, SnapTargetType.SEGMENT, 0, 1, 0);
        MultiPolygonGeometry polygons =
                MultiPolygonGeometry.ofPolygons(
                        List.of(
                                new PolygonGeometry(ring(-20, -20, -15, -20, -15, -15, -20, -15)),
                                polygon));
        assertWinner(polygons, 50, 52, SnapTargetType.SEGMENT, 1, 1, 0);
    }

    @Test
    void skipsRepeatedSegmentsAndUsesClosedRingSemanticIndexes() {
        LineStringGeometry repeated = line(-10, 0, -10, 0, 10, 0);
        SnapResult lineWinner = snapped(query(references(repeated), 50, 52, 8));
        assertEquals(SnapTargetType.SEGMENT, lineWinner.targetType());
        assertEquals(1, lineWinner.elementIndex());

        PolygonGeometry polygon = new PolygonGeometry(ring(-5, -5, 5, -5, 5, 5, -5, 5));
        SnapResult closing = snapped(query(references(polygon), 44, 50, 2));
        assertEquals(SnapTargetType.SEGMENT, closing.targetType());
        assertEquals(3, closing.elementIndex());

        MultiLineStringGeometry separated =
                MultiLineStringGeometry.ofParts(
                        List.of(sequence(-10, 0, -5, 0), sequence(5, 0, 10, 0)));
        assertEquals(
                SnapQueryStatus.UNSNAPPED, find(query(references(separated), 50, 50, 4)).status());
    }

    @Test
    void exactToleranceAndTieRulesAreDeterministic() {
        SnapReferenceSet tolerance = references(point(8, 0));
        assertEquals(SnapQueryStatus.SNAPPED, find(query(tolerance, 50, 50, 8)).status());
        assertEquals(SnapQueryStatus.UNSNAPPED, find(query(tolerance, 50, 50, 7.99)).status());
        assertEquals(
                SnapQueryStatus.UNSNAPPED,
                find(query(new SnapReferenceSet(CrsDefinitions.EPSG_3857, List.of()), 50, 50, 8))
                        .status());

        SnapResult vertexBeforeSegment = snapped(query(references(line(0, 0, 10, 0)), 50, 50, 8));
        assertEquals(SnapTargetType.VERTEX, vertexBeforeSegment.targetType());

        SnapReferenceLayer lower =
                new SnapReferenceLayer("lower", List.of(new SnapFeature("one", point(0, 0))));
        SnapReferenceLayer higher =
                new SnapReferenceLayer(
                        "higher",
                        List.of(
                                new SnapFeature("first", point(0, 0)),
                                new SnapFeature("last", point(0, 0))));
        SnapResult priority =
                snapped(
                        query(
                                new SnapReferenceSet(
                                        CrsDefinitions.EPSG_3857, List.of(lower, higher)),
                                50,
                                50,
                                1));
        assertEquals("higher", priority.layerId());
        assertEquals("last", priority.featureId());

        SnapResult ascendingIndex =
                snapped(query(references(new MultiPointGeometry(sequence(0, 0, 0, 0))), 50, 50, 1));
        assertEquals(0, ascendingIndex.componentIndex());

        PolygonGeometry duplicateHoles =
                new PolygonGeometry(
                        ring(-20, -20, 20, -20, 20, 20, -20, 20),
                        List.of(
                                ring(-2, -2, 2, -2, 2, 2, -2, 2),
                                ring(-2, -2, 2, -2, 2, 2, -2, 2)));
        SnapResult partTie = snapped(query(references(duplicateHoles), 50, 52, 1));
        assertEquals(1, partTie.partIndex());
        assertEquals(0, partTie.elementIndex());

        SnapResult elementTie = snapped(query(references(line(0, 0, 1, 0, 0, 0)), 50, 50, 1));
        assertEquals(0, elementTie.elementIndex());
    }

    @Test
    void exclusionsAreCopiedAndOmittedFromCountsAndCandidates() {
        SnapReferenceSet references =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new SnapReferenceLayer(
                                        "layer",
                                        List.of(
                                                new SnapFeature("excluded", point(0, 0)),
                                                new SnapFeature("kept", point(2, 0))))));
        Set<FeatureSelection> exclusions =
                new HashSet<>(List.of(new FeatureSelection("layer", "excluded")));
        SnapQuery query =
                query(
                        references,
                        50,
                        50,
                        8,
                        SnapLimits.DEFAULT,
                        exclusions,
                        CancellationToken.none());
        exclusions.clear();

        SnapResult result = snapped(query);
        assertEquals("kept", result.featureId());
        assertEquals(2, result.distancePixels());
    }

    @Test
    void limitsCancelAndTransformFailuresRejectWithoutPartialWinner() {
        SnapReferenceSet twoLayers =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new SnapReferenceLayer(
                                        "one", List.of(new SnapFeature("a", point(0, 0)))),
                                new SnapReferenceLayer(
                                        "two", List.of(new SnapFeature("b", point(0, 0))))));
        assertRejected(
                query(
                        twoLayers,
                        50,
                        50,
                        8,
                        SnapLimits.DEFAULT.withMaximumLayers(1),
                        Set.of(),
                        CancellationToken.none()),
                "EDIT_SNAP_LIMIT_EXCEEDED");
        SnapReferenceSet twoFeatures =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new SnapReferenceLayer(
                                        "one",
                                        List.of(
                                                new SnapFeature("a", point(0, 0)),
                                                new SnapFeature("b", point(1, 0))))));
        assertRejected(
                query(
                        twoFeatures,
                        50,
                        50,
                        8,
                        SnapLimits.DEFAULT.withMaximumFeatures(1),
                        Set.of(),
                        CancellationToken.none()),
                "EDIT_SNAP_LIMIT_EXCEEDED");
        assertRejected(
                query(
                        references(new MultiPointGeometry(sequence(0, 0, 1, 0))),
                        50,
                        50,
                        8,
                        SnapLimits.DEFAULT.withMaximumCoordinates(1),
                        Set.of(),
                        CancellationToken.none()),
                "EDIT_SNAP_LIMIT_EXCEEDED");
        assertRejected(
                query(
                        references(line(-2, 0, 0, 0, 2, 0)),
                        50,
                        51,
                        8,
                        SnapLimits.DEFAULT.withMaximumSegments(1),
                        Set.of(),
                        CancellationToken.none()),
                "EDIT_SNAP_LIMIT_EXCEEDED");
        assertEquals(
                SnapQueryStatus.SNAPPED,
                find(query(
                                references(line(-1, 0, 1, 0)),
                                50,
                                51,
                                8,
                                new SnapLimits(1, 1, 2, 1),
                                Set.of(),
                                CancellationToken.none()))
                        .status());

        assertRejected(
                query(references(point(0, 0)), 50, 50, 8, SnapLimits.DEFAULT, Set.of(), () -> true),
                "EDIT_SNAP_CANCELLED");
        AtomicInteger polls = new AtomicInteger();
        assertRejected(
                query(
                        references(new MultiPointGeometry(sequence(0, 0, 1, 0, 2, 0))),
                        50,
                        50,
                        8,
                        SnapLimits.DEFAULT,
                        Set.of(),
                        () -> polls.incrementAndGet() >= 4),
                "EDIT_SNAP_CANCELLED");
        assertTrue(polls.get() >= 4);
        SnapReferenceSet excludedReferences =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new SnapReferenceLayer(
                                        "excluded",
                                        List.of(
                                                new SnapFeature("a", point(0, 0)),
                                                new SnapFeature("b", point(1, 0))))));
        AtomicInteger excludedPolls = new AtomicInteger();
        assertRejected(
                query(
                        excludedReferences,
                        50,
                        50,
                        8,
                        SnapLimits.DEFAULT,
                        Set.of(
                                new FeatureSelection("excluded", "a"),
                                new FeatureSelection("excluded", "b")),
                        () -> excludedPolls.incrementAndGet() >= 4),
                "EDIT_SNAP_CANCELLED");
        assertEquals(4, excludedPolls.get());
        IllegalStateException tokenFailure = new IllegalStateException("token");
        IllegalStateException propagated =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                find(
                                        query(
                                                references(point(0, 0)),
                                                50,
                                                50,
                                                8,
                                                SnapLimits.DEFAULT,
                                                Set.of(),
                                                () -> {
                                                    throw tokenFailure;
                                                })));
        assertEquals(tokenFailure, propagated);

        CrsOperation geographicIdentity =
                CrsRegistry.level1().operation(CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_4326);
        SnapReferenceSet outside =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_4326,
                        List.of(
                                new SnapReferenceLayer(
                                        "layer", List.of(new SnapFeature("bad", point(200, 0))))));
        SnapQuery outsideQuery =
                new SnapQuery(
                        50,
                        50,
                        8,
                        geographicIdentity,
                        geographicIdentity,
                        VIEWPORT,
                        outside,
                        Set.of(),
                        SnapLimits.DEFAULT,
                        CancellationToken.none());
        assertRejected(outsideQuery, "EDIT_SNAP_COORDINATE_UNREPRESENTABLE");

        SnapReferenceSet segmentPrecedence =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_4326,
                        List.of(
                                new SnapReferenceLayer(
                                        "layer",
                                        List.of(
                                                new SnapFeature(
                                                        "line", line(0, 0, 1, 0, 200, 0))))));
        assertRejected(
                new SnapQuery(
                        50,
                        50,
                        8,
                        geographicIdentity,
                        geographicIdentity,
                        VIEWPORT,
                        segmentPrecedence,
                        Set.of(),
                        SnapLimits.DEFAULT.withMaximumSegments(1),
                        CancellationToken.none()),
                "EDIT_SNAP_LIMIT_EXCEEDED");
        assertRejected(reverseFailureQuery(), "EDIT_SNAP_COORDINATE_UNREPRESENTABLE");
        assertEquals(
                "EDIT_SNAP_CANCELLED",
                find(query(
                                twoLayers,
                                50,
                                50,
                                8,
                                SnapLimits.DEFAULT.withMaximumLayers(1),
                                Set.of(),
                                () -> true))
                        .problem()
                        .orElseThrow()
                        .code());
    }

    @Test
    void queryRejectsMismatchedEndpointsAndInvalidTolerance() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SnapQuery(
                                50,
                                50,
                                8,
                                IDENTITY,
                                IDENTITY,
                                VIEWPORT,
                                new SnapReferenceSet(
                                        CrsDefinitions.EPSG_4326,
                                        List.of(
                                                new SnapReferenceLayer(
                                                        "layer",
                                                        List.of(
                                                                new SnapFeature(
                                                                        "a", point(0, 0)))))),
                                Set.of(),
                                SnapLimits.DEFAULT,
                                CancellationToken.none()));
        assertThrows(
                IllegalArgumentException.class, () -> query(references(point(0, 0)), 50, 50, 0));

        CrsOperation forward =
                CrsRegistry.level1().operation(CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SnapQuery(
                                50,
                                50,
                                8,
                                forward,
                                IDENTITY,
                                VIEWPORT,
                                new SnapReferenceSet(CrsDefinitions.EPSG_4326, List.of()),
                                Set.of(),
                                SnapLimits.DEFAULT,
                                CancellationToken.none()));
    }

    @Test
    void largeFiniteScreenCoordinatesRemainNumericallyStable() {
        MapViewport tinyScale = new MapViewport(100, 100, 0, 0, 1.0e-300);
        SnapQuery query =
                new SnapQuery(
                        1.5e300,
                        50,
                        8,
                        IDENTITY,
                        IDENTITY,
                        tinyScale,
                        references(line(1, 0, 2, 0)),
                        Set.of(),
                        SnapLimits.DEFAULT,
                        CancellationToken.none());

        SnapResult result = snapped(query);
        assertEquals(SnapTargetType.SEGMENT, result.targetType());
        assertEquals(1.5, result.coordinate().x(), 1.0e-12);
        assertEquals(0, result.distancePixels());
    }

    private static void assertWinner(
            Geometry geometry,
            double screenX,
            double screenY,
            SnapTargetType type,
            int component,
            int part,
            int element) {
        SnapResult result = snapped(query(references(geometry), screenX, screenY, 8));
        assertEquals(type, result.targetType());
        assertEquals(component, result.componentIndex());
        assertEquals(part, result.partIndex());
        assertEquals(element, result.elementIndex());
    }

    private static void assertRejected(SnapQuery query, String code) {
        assertEquals(code, find(query).problem().orElseThrow().code());
    }

    private static SnapResult snapped(SnapQuery query) {
        return find(query).result().orElseThrow();
    }

    private static SnapQueryResult find(SnapQuery query) {
        return new FeatureSnapper().find(query);
    }

    private static SnapQuery query(
            SnapReferenceSet references, double screenX, double screenY, double tolerance) {
        return query(
                references,
                screenX,
                screenY,
                tolerance,
                SnapLimits.DEFAULT,
                Set.of(),
                CancellationToken.none());
    }

    private static SnapQuery query(
            SnapReferenceSet references,
            double screenX,
            double screenY,
            double tolerance,
            SnapLimits limits,
            Set<FeatureSelection> exclusions,
            CancellationToken cancellation) {
        return new SnapQuery(
                screenX,
                screenY,
                tolerance,
                IDENTITY,
                IDENTITY,
                VIEWPORT,
                references,
                exclusions,
                limits,
                cancellation);
    }

    private static SnapReferenceSet references(Geometry geometry) {
        return new SnapReferenceSet(
                CrsDefinitions.EPSG_3857,
                List.of(
                        new SnapReferenceLayer(
                                "layer", List.of(new SnapFeature("feature", geometry)))));
    }

    private static SnapQuery reverseFailureQuery() {
        Projection projection =
                new Projection() {
                    @Override
                    public CrsDefinition sourceCrs() {
                        return LOCAL_SOURCE;
                    }

                    @Override
                    public CrsDefinition targetCrs() {
                        return LOCAL_DISPLAY;
                    }

                    @Override
                    public Envelope sourceDomain() {
                        return LOCAL_SOURCE.coordinateDomain();
                    }

                    @Override
                    public Envelope targetDomain() {
                        return LOCAL_DISPLAY.coordinateDomain();
                    }

                    @Override
                    public Coordinate project(Coordinate source) {
                        return source;
                    }

                    @Override
                    public Coordinate unproject(Coordinate projected) {
                        return Double.compare(projected.x(), 0) == 0 ? null : projected;
                    }

                    @Override
                    public Envelope projectEnvelope(Envelope source) {
                        return source;
                    }

                    @Override
                    public Envelope unprojectEnvelope(Envelope target) {
                        return target;
                    }
                };
        CrsRegistry registry =
                CrsRegistry.builder()
                        .registerDefinition(LOCAL_SOURCE, List.of())
                        .registerDefinition(LOCAL_DISPLAY, List.of())
                        .registerProjection(projection)
                        .build();
        return new SnapQuery(
                50,
                52,
                8,
                registry.operation(LOCAL_SOURCE, LOCAL_DISPLAY),
                registry.operation(LOCAL_DISPLAY, LOCAL_SOURCE),
                VIEWPORT,
                new SnapReferenceSet(
                        LOCAL_SOURCE,
                        List.of(
                                new SnapReferenceLayer(
                                        "layer",
                                        List.of(new SnapFeature("line", line(-10, 0, 10, 0)))))),
                Set.of(),
                SnapLimits.DEFAULT,
                CancellationToken.none());
    }

    private static CrsDefinition localCrs(String identifier) {
        return new CrsDefinition(
                identifier,
                CrsKind.PROJECTED,
                new CrsAxis(CrsAxisMeaning.EASTING, CrsUnit.METRE),
                new CrsAxis(CrsAxisMeaning.NORTHING, CrsUnit.METRE),
                new Envelope(-100, -100, 100, 100));
    }

    private static PointGeometry point(double x, double y) {
        return new PointGeometry(new Coordinate(x, y));
    }

    private static LineStringGeometry line(double... ordinates) {
        return new LineStringGeometry(sequence(ordinates));
    }

    private static CoordinateSequence ring(double... ordinates) {
        double[] closed = new double[ordinates.length + 2];
        System.arraycopy(ordinates, 0, closed, 0, ordinates.length);
        closed[closed.length - 2] = ordinates[0];
        closed[closed.length - 1] = ordinates[1];
        return sequence(closed);
    }

    private static CoordinateSequence sequence(double... ordinates) {
        return CoordinateSequence.of(ordinates);
    }
}
