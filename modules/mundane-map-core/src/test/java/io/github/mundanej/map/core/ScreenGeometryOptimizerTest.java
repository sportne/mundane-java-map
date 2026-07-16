package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ScreenGeometryOptimizerTest {
    private static final Envelope CLIP = new Envelope(0.0, 0.0, 10.0, 10.0);
    private static final ScreenGeometryOptimizationLimits LIMITS =
            ScreenGeometryOptimizationLimits.defaults();

    @Test
    void clipsEveryRectangleEdgeAndPreservesCornerIntersections() {
        assertLine(line(-2, 5, 12, 5), 0, 5, 10, 5);
        assertLine(line(5, -2, 5, 12), 5, 0, 5, 10);
        assertLine(line(-2, -2, 12, 12), 0, 0, 10, 10);
        assertLine(line(12, -2, -2, 12), 10, 0, 0, 10);
    }

    @Test
    void splitsAReenteringLineAndMapsFragmentsToSourceParts() {
        MultiLineStringGeometry source =
                MultiLineStringGeometry.ofParts(
                        List.of(
                                coordinates(1, 1, 12, 1, 12, 9, 1, 9),
                                coordinates(-5, -5, -1, -1)));
        ScreenGeometryOptimization result = optimize(source, 0.0);

        assertEquals(ScreenGeometryOptimizationOutcome.OPTIMIZED, result.outcome());
        assertArrayEquals(new int[] {0, 2, 2}, result.renderComponentOffsets());
        MultiLineStringGeometry rendered =
                (MultiLineStringGeometry) result.renderingGeometry().orElseThrow();
        assertEquals(2, rendered.partCount());
        assertArrayEquals(new double[] {1, 1, 10, 1}, part(rendered, 0));
        assertArrayEquals(new double[] {10, 9, 1, 9}, part(rendered, 1));
    }

    @Test
    void removesRepeatedPointsAndUsesToleranceEqualityAsRemovable() {
        ScreenGeometryOptimization result = optimize(line(1, 1, 1, 1, 5, 1.25, 9, 1), 0.25);

        assertEquals(ScreenGeometryOptimizationOutcome.OPTIMIZED, result.outcome());
        assertArrayEquals(
                new double[] {1, 1, 9, 1},
                ((LineStringGeometry) result.renderingGeometry().orElseThrow())
                        .coordinates()
                        .toArray());
    }

    @Test
    void cullsDegenerateAndDisjointLinesWithoutInventingAPath() {
        for (LineStringGeometry source : List.of(line(2, 2, 2, 2), line(-4, -4, -2, -2))) {
            ScreenGeometryOptimization result = optimize(source, 0.25);
            assertEquals(ScreenGeometryOptimizationOutcome.PATH_CULLED, result.outcome());
            assertTrue(result.renderingGeometry().isEmpty());
            assertArrayEquals(new int[] {0, 0}, result.renderComponentOffsets());
        }
    }

    @Test
    void returnsAuthoritativeGeometryForConservativeLimitFallback() {
        LineStringGeometry source = line(1, 1, 9, 9);
        ScreenGeometryOptimization result =
                ScreenGeometryOptimizer.optimize(
                        source,
                        CLIP,
                        0.25,
                        LIMITS.withMaximumOutputCoordinates(1).withMaximumBuildBytes(1));

        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, result.outcome());
        assertSame(source, result.authoritativeGeometry());
        assertSame(source, result.renderingGeometry().orElseThrow());
        assertArrayEquals(new int[] {0, 1}, result.renderComponentOffsets());
        assertEquals(1, result.sourceComponentCount());
        assertEquals(1, result.renderComponentCount());
        assertEquals(1, result.renderComponentOffset(1));

        int[] copy = result.renderComponentOffsets();
        copy[1] = 99;
        assertArrayEquals(new int[] {0, 1}, result.renderComponentOffsets());

        ScreenGeometryOptimization repeated =
                ScreenGeometryOptimizer.optimize(
                        source,
                        CLIP,
                        0.25,
                        LIMITS.withMaximumOutputCoordinates(1).withMaximumBuildBytes(1));
        assertEquals(result, repeated);
        assertEquals(result.hashCode(), repeated.hashCode());
    }

    @Test
    void acceptsExactWorkLimitEqualityAndRejectsTheNextSmallerBudget() {
        LineStringGeometry source = line(1, 1, 9, 9);
        ScreenGeometryOptimizationLimits exact = new ScreenGeometryOptimizationLimits(2, 272, 1);

        assertEquals(
                ScreenGeometryOptimizationOutcome.UNCHANGED,
                ScreenGeometryOptimizer.optimize(source, CLIP, 0.0, exact).outcome());
        assertEquals(
                ScreenGeometryOptimizationOutcome.FALLBACK,
                ScreenGeometryOptimizer.optimize(
                                source, CLIP, 0.0, exact.withMaximumBuildBytes(271))
                        .outcome());
    }

    @Test
    void keepsExactBoundarySegmentsAndOmitsCornerOnlyAndZeroLengthContacts() {
        assertRenderedLine(line(0, 0, 10, 0), 0, 0, 10, 0);
        assertRenderedLine(line(10, 0, 10, 10), 10, 0, 10, 10);
        assertRenderedLine(line(10, 10, 0, 10), 10, 10, 0, 10);
        assertRenderedLine(line(0, 10, 0, 0), 0, 10, 0, 0);

        for (LineStringGeometry source :
                List.of(line(-1, -1, 0, 0, -1, 1), line(0, 0, 0, 0, -1, -1))) {
            ScreenGeometryOptimization result = optimize(source, 0.0);
            assertEquals(ScreenGeometryOptimizationOutcome.PATH_CULLED, result.outcome());
            assertTrue(result.renderingGeometry().isEmpty());
        }
    }

    @Test
    void closedRingAnchorAndEqualFarthestTieAreStable() {
        PolygonGeometry source = polygon(1, 1, 3, 1, 1, 3, 1, 1);

        ScreenGeometryOptimization first = optimize(source, 0.5);
        ScreenGeometryOptimization second = optimize(source, 0.5);

        assertEquals(first, second);
        assertEquals(ScreenGeometryOptimizationOutcome.UNCHANGED, first.outcome());
        assertSame(source, first.renderingGeometry().orElseThrow());
    }

    @Test
    void ignoresConsecutiveZeroLengthEdgesButRejectsNonAdjacentRepeats() {
        PolygonGeometry zeroLength = polygon(1, 1, 1, 1, 9, 1, 9, 9, 1, 9, 1, 1);
        ScreenGeometryOptimization simplified = optimize(zeroLength, 0.0);
        assertEquals(ScreenGeometryOptimizationOutcome.OPTIMIZED, simplified.outcome());
        assertArrayEquals(
                new double[] {1, 1, 9, 1, 9, 9, 1, 9, 1, 1},
                ((PolygonGeometry) simplified.renderingGeometry().orElseThrow())
                        .exterior()
                        .toArray());

        PolygonGeometry repeated = polygon(1, 1, 9, 1, 9, 9, 1, 1, 1, 9, 1, 1);
        ScreenGeometryOptimization fallback = optimize(repeated, 0.0);
        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, fallback.outcome());
        assertSame(repeated, fallback.renderingGeometry().orElseThrow());
    }

    @Test
    void rejectsNestedAndTouchingHolesWithoutGuessingContainment() {
        CoordinateSequence shell = coordinates(0, 0, 10, 0, 10, 10, 0, 10, 0, 0);
        CoordinateSequence outerHole = coordinates(2, 2, 2, 8, 8, 8, 8, 2, 2, 2);
        CoordinateSequence nestedHole = coordinates(3, 3, 3, 4, 4, 4, 4, 3, 3, 3);
        CoordinateSequence touchingHole = coordinates(8, 2, 8, 4, 9, 4, 9, 2, 8, 2);

        for (PolygonGeometry source :
                List.of(
                        new PolygonGeometry(shell, List.of(outerHole, nestedHole)),
                        new PolygonGeometry(shell, List.of(outerHole, touchingHole)))) {
            ScreenGeometryOptimization result = optimize(source, 0.0);
            assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, result.outcome());
            assertSame(source, result.renderingGeometry().orElseThrow());
        }
    }

    @Test
    void ambiguousAreaAndCrossingPredicatesFallBackConservatively() {
        double epsilon = Math.ulp(1.0);
        PolygonGeometry ambiguousArea = polygon(1, 1, 9, 1, 9, 1 + epsilon, 1, 1 + epsilon, 1, 1);
        PolygonGeometry ambiguousCrossing = polygon(1, 1, 9, 9, 1, 9 + epsilon, 9, 1, 1, 1);

        for (PolygonGeometry source : List.of(ambiguousArea, ambiguousCrossing)) {
            ScreenGeometryOptimization result = optimize(source, 0.0);
            assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, result.outcome());
            assertSame(source, result.renderingGeometry().orElseThrow());
        }
    }

    @Test
    void aLateTopologyBudgetExhaustionAliasesTheWholeMultipolygon() {
        MultiPolygonGeometry source =
                MultiPolygonGeometry.ofPolygons(
                        List.of(
                                polygon(-2, 1, 4, 1, 4, 4, -2, 4, -2, 1),
                                polygon(-2, 6, 4, 6, 4, 9, -2, 9, -2, 6)));
        long firstPassingBudget = firstTopologyBudgetProducingOwnedResult(source);

        ScreenGeometryOptimization exact =
                ScreenGeometryOptimizer.optimize(
                        source,
                        CLIP,
                        0.0,
                        LIMITS.withMaximumTopologyComparisons(firstPassingBudget));
        ScreenGeometryOptimization oneLess =
                ScreenGeometryOptimizer.optimize(
                        source,
                        CLIP,
                        0.0,
                        LIMITS.withMaximumTopologyComparisons(firstPassingBudget - 1));

        assertEquals(ScreenGeometryOptimizationOutcome.OPTIMIZED, exact.outcome());
        assertTrue(exact.renderingGeometry().orElseThrow() != source);
        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, oneLess.outcome());
        assertSame(source, oneLess.renderingGeometry().orElseThrow());
    }

    @Test
    void fixedSeedLinePlansAreRepeatableBoundedAndRemainInsideTheClip() {
        SplittableRandom random = new SplittableRandom(0x5EED_7003L);
        for (int sample = 0; sample < 100; sample++) {
            double[] values = new double[64];
            for (int index = 0; index < values.length; index++) {
                values[index] = random.nextDouble(-20.0, 30.0);
            }
            LineStringGeometry source = line(values);
            ScreenGeometryOptimization first = optimize(source, 0.25);
            ScreenGeometryOptimization second = optimize(source, 0.25);
            assertEquals(first, second);
            first.renderingGeometry()
                    .ifPresent(
                            geometry -> {
                                CoordinateSequence coordinates =
                                        geometry instanceof LineStringGeometry line
                                                ? line.coordinates()
                                                : ((MultiLineStringGeometry) geometry)
                                                        .coordinates();
                                for (int index = 0; index < coordinates.size(); index++) {
                                    assertTrue(coordinates.x(index) >= CLIP.minX());
                                    assertTrue(coordinates.x(index) <= CLIP.maxX());
                                    assertTrue(coordinates.y(index) >= CLIP.minY());
                                    assertTrue(coordinates.y(index) <= CLIP.maxY());
                                }
                            });
        }
    }

    @Test
    void preflightsWorstCaseLineFragmentsAndFallsBackOnNumericOverflow() {
        LineStringGeometry oscillating =
                line(-1, 1, 11, 1, -1, 2, 11, 2, -1, 3, 11, 3, -1, 4, 11, 4, -1, 5, 11, 5);
        ScreenGeometryOptimization bounded =
                ScreenGeometryOptimizer.optimize(
                        oscillating, CLIP, 0.0, LIMITS.withMaximumOutputCoordinates(14));
        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, bounded.outcome());
        assertSame(oscillating, bounded.renderingGeometry().orElseThrow());

        LineStringGeometry overflowing = line(0, 0, 1.3e308, 1.3e308, 0, 0);
        ScreenGeometryOptimization numeric =
                ScreenGeometryOptimizer.optimize(
                        overflowing, new Envelope(0, 0, 1.3e308, 1.3e308), 0.25, LIMITS);
        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, numeric.outcome());
        assertSame(overflowing, numeric.renderingGeometry().orElseThrow());
    }

    @Test
    void preflightsTheCompleteMultilineResultBeforeWorkWithExactEquality() {
        MultiLineStringGeometry source =
                MultiLineStringGeometry.ofParts(
                        List.of(coordinates(-10, -10, -5, -5), coordinates(15, 15, 20, 20)));
        ScreenGeometryOptimization exact =
                ScreenGeometryOptimizer.optimize(
                        source, CLIP, 0.0, new ScreenGeometryOptimizationLimits(4, 176, 1));
        ScreenGeometryOptimization oneLess =
                ScreenGeometryOptimizer.optimize(
                        source, CLIP, 0.0, new ScreenGeometryOptimizationLimits(4, 175, 1));

        assertEquals(ScreenGeometryOptimizationOutcome.PATH_CULLED, exact.outcome());
        assertTrue(exact.renderingGeometry().isEmpty());
        assertArrayEquals(new int[] {0, 0, 0}, exact.renderComponentOffsets());
        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, oneLess.outcome());
        assertSame(source, oneLess.renderingGeometry().orElseThrow());
        assertArrayEquals(new int[] {0, 1, 2}, oneLess.renderComponentOffsets());
    }

    @Test
    void preflightsTheCompleteMultipolygonResultBeforeWorkWithExactEquality() {
        MultiPolygonGeometry source =
                MultiPolygonGeometry.ofPolygons(
                        List.of(
                                polygon(-20, -20, -15, -20, -15, -15, -20, -15, -20, -20),
                                polygon(15, 15, 20, 15, 20, 20, 15, 20, 15, 15)));
        ScreenGeometryOptimization exact =
                ScreenGeometryOptimizer.optimize(
                        source, CLIP, 0.0, new ScreenGeometryOptimizationLimits(18, 648, 1));
        ScreenGeometryOptimization oneLess =
                ScreenGeometryOptimizer.optimize(
                        source, CLIP, 0.0, new ScreenGeometryOptimizationLimits(18, 647, 1));

        assertEquals(ScreenGeometryOptimizationOutcome.PATH_CULLED, exact.outcome());
        assertTrue(exact.renderingGeometry().isEmpty());
        assertArrayEquals(new int[] {0, 0, 0}, exact.renderComponentOffsets());
        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, oneLess.outcome());
        assertSame(source, oneLess.renderingGeometry().orElseThrow());
        assertArrayEquals(new int[] {0, 1, 2}, oneLess.renderComponentOffsets());
    }

    @Test
    void clipsAConvexPolygonAndRetainsClosureAndOrientation() {
        PolygonGeometry source = polygon(-2, 2, 8, 2, 8, 8, -2, 8, -2, 2);
        ScreenGeometryOptimization result = optimize(source, 0.0);

        assertEquals(ScreenGeometryOptimizationOutcome.OPTIMIZED, result.outcome());
        PolygonGeometry rendered = (PolygonGeometry) result.renderingGeometry().orElseThrow();
        assertTrue(rendered.exterior().isClosed());
        assertEquals(new Envelope(0, 2, 8, 8), rendered.exterior().envelope());
        assertEquals(Math.signum(area(source.exterior())), Math.signum(area(rendered.exterior())));
    }

    @Test
    void fallsBackForPartialConcavePolygonAndBoundaryCrossingHole() {
        PolygonGeometry concave = polygon(-2, 1, 6, 1, 3, 4, 6, 8, -2, 8, -2, 1);
        PolygonGeometry boundaryHole =
                new PolygonGeometry(
                        coordinates(-2, -2, 12, -2, 12, 12, -2, 12, -2, -2),
                        List.of(coordinates(-1, 3, 2, 3, 2, 6, -1, 6, -1, 3)));

        for (PolygonGeometry source : List.of(concave, boundaryHole)) {
            ScreenGeometryOptimization result = optimize(source, 0.25);
            assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, result.outcome());
            assertSame(source, result.renderingGeometry().orElseThrow());
        }
    }

    @Test
    void dropsOnlyAProvenOutsideHoleAndRejectsTouchingHoles() {
        CoordinateSequence shell = coordinates(-8, -2, 12, -2, 12, 12, -8, 12, -8, -2);
        CoordinateSequence outside = coordinates(-5, 2, -5, 4, -3, 4, -3, 2, -5, 2);
        ScreenGeometryOptimization safe =
                optimize(new PolygonGeometry(shell, List.of(outside)), 0.0);
        assertEquals(ScreenGeometryOptimizationOutcome.OPTIMIZED, safe.outcome());
        assertEquals(0, ((PolygonGeometry) safe.renderingGeometry().orElseThrow()).holes().size());

        CoordinateSequence touching = coordinates(0, 2, 0, 4, 2, 4, 2, 2, 0, 2);
        ScreenGeometryOptimization unsafe =
                optimize(
                        new PolygonGeometry(
                                coordinates(0, 0, 10, 0, 10, 10, 0, 10, 0, 0), List.of(touching)),
                        0.0);
        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, unsafe.outcome());
    }

    @Test
    void reportsMixedMultipolygonFallbackAndPreservesComponentOrder() {
        PolygonGeometry clipped = polygon(-2, 2, 3, 2, 3, 4, -2, 4, -2, 2);
        PolygonGeometry unsafe = polygon(2, 2, 8, 8, 2, 8, 8, 2, 2, 2);
        ScreenGeometryOptimization result =
                optimize(MultiPolygonGeometry.ofPolygons(List.of(clipped, unsafe)), 0.0);

        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, result.outcome());
        assertArrayEquals(new int[] {0, 1, 2}, result.renderComponentOffsets());
        assertEquals(
                2,
                ((MultiPolygonGeometry) result.renderingGeometry().orElseThrow()).polygonCount());
    }

    @Test
    void aliasesAnEntireMultipolygonWhenEveryVisibleComponentFallsBack() {
        MultiPolygonGeometry source =
                MultiPolygonGeometry.ofPolygons(
                        List.of(
                                polygon(2, 2, 8, 8, 2, 8, 8, 2, 2, 2),
                                polygon(1, 1, 9, 9, 1, 9, 9, 1, 1, 1)));

        ScreenGeometryOptimization result = optimize(source, 0.25);

        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, result.outcome());
        assertSame(source, result.renderingGeometry().orElseThrow());
        assertArrayEquals(new int[] {0, 1, 2}, result.renderComponentOffsets());
    }

    @Test
    void topologyComparisonExhaustionFallsBackWithoutPublishingAPartialCandidate() {
        PolygonGeometry source = polygon(-2, 2, 8, 2, 8, 8, -2, 8, -2, 2);

        ScreenGeometryOptimization result =
                ScreenGeometryOptimizer.optimize(
                        source, CLIP, 0.0, LIMITS.withMaximumTopologyComparisons(1));

        assertEquals(ScreenGeometryOptimizationOutcome.FALLBACK, result.outcome());
        assertSame(source, result.renderingGeometry().orElseThrow());
    }

    @Test
    void isDeterministicAndDefensivelyCopiesOffsets() {
        MultiLineStringGeometry source =
                MultiLineStringGeometry.ofParts(
                        List.of(coordinates(-2, 5, 12, 5), coordinates(1, 1, 5, 1.1, 9, 1)));
        ScreenGeometryOptimization first = optimize(source, 0.25);
        ScreenGeometryOptimization second = optimize(source, 0.25);

        assertEquals(first, second);
        int[] offsets = first.renderComponentOffsets();
        offsets[1] = 99;
        assertArrayEquals(new int[] {0, 1, 2}, first.renderComponentOffsets());
    }

    @Test
    void rejectsPointsAndInvalidToleranceAndValidatesLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> optimize(new PointGeometry(new Coordinate(1, 1)), 0.25));
        assertThrows(IllegalArgumentException.class, () -> optimize(line(1, 1, 2, 2), Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ScreenGeometryOptimizationLimits(0, 1, 1));
    }

    private static ScreenGeometryOptimization optimize(
            io.github.mundanej.map.api.Geometry geometry, double tolerance) {
        return ScreenGeometryOptimizer.optimize(geometry, CLIP, tolerance, LIMITS);
    }

    private static long firstTopologyBudgetProducingOwnedResult(MultiPolygonGeometry source) {
        for (long budget = 1; budget <= 10_000; budget++) {
            ScreenGeometryOptimization result =
                    ScreenGeometryOptimizer.optimize(
                            source, CLIP, 0.0, LIMITS.withMaximumTopologyComparisons(budget));
            if (result.outcome() == ScreenGeometryOptimizationOutcome.OPTIMIZED) {
                return budget;
            }
        }
        throw new AssertionError("No bounded topology budget produced the expected result");
    }

    private static void assertLine(LineStringGeometry source, double... expected) {
        ScreenGeometryOptimization result = optimize(source, 0.0);
        assertEquals(ScreenGeometryOptimizationOutcome.OPTIMIZED, result.outcome());
        assertArrayEquals(
                expected,
                ((LineStringGeometry) result.renderingGeometry().orElseThrow())
                        .coordinates()
                        .toArray());
    }

    private static void assertRenderedLine(LineStringGeometry source, double... expected) {
        ScreenGeometryOptimization result = optimize(source, 0.0);
        assertArrayEquals(
                expected,
                ((LineStringGeometry) result.renderingGeometry().orElseThrow())
                        .coordinates()
                        .toArray());
    }

    private static LineStringGeometry line(double... values) {
        return new LineStringGeometry(coordinates(values));
    }

    private static PolygonGeometry polygon(double... values) {
        return new PolygonGeometry(coordinates(values));
    }

    private static CoordinateSequence coordinates(double... values) {
        return CoordinateSequence.of(values);
    }

    private static double[] part(MultiLineStringGeometry geometry, int part) {
        int first = geometry.partOffset(part);
        int last = geometry.partOffset(part + 1);
        double[] result = new double[(last - first) * 2];
        for (int index = first; index < last; index++) {
            result[(index - first) * 2] = geometry.coordinates().x(index);
            result[(index - first) * 2 + 1] = geometry.coordinates().y(index);
        }
        return result;
    }

    private static double area(CoordinateSequence ring) {
        double result = 0.0;
        for (int index = 0; index < ring.size() - 1; index++) {
            result += ring.x(index) * ring.y(index + 1) - ring.y(index) * ring.x(index + 1);
        }
        return result;
    }
}
