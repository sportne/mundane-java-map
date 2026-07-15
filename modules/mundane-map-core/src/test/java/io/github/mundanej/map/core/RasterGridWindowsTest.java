package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RasterGridWindowsTest {
    @Test
    void exactEdgesPartialCellsAndRowsProduceMinimalWindows() {
        RasterSourceMetadata metadata = metadata(4, 4, new Envelope(0, 0, 4, 4));
        assertEquals(
                new RasterWindow(0, 0, 4, 4),
                RasterGridWindows.visibleWindow(metadata, new Envelope(0, 0, 4, 4)).orElseThrow());
        RasterWindow exact =
                RasterGridWindows.visibleWindow(metadata, new Envelope(1, 1, 3, 3)).orElseThrow();
        assertEquals(new RasterWindow(1, 1, 2, 2), exact);
        assertEquals(new Envelope(1, 1, 3, 3), RasterGridWindows.mapBounds(metadata, exact));
        assertEquals(
                new RasterWindow(0, 0, 2, 2),
                RasterGridWindows.visibleWindow(metadata, new Envelope(.5, 2.5, 1.5, 3.5))
                        .orElseThrow());
    }

    @Test
    void outsideAndTouchingOnlyIntersectionsAreEmpty() {
        RasterSourceMetadata metadata = metadata(1, 1, new Envelope(0, 0, 10, 10));
        assertTrue(
                RasterGridWindows.visibleWindow(metadata, new Envelope(10, 0, 20, 10)).isEmpty());
        assertTrue(RasterGridWindows.visibleWindow(metadata, new Envelope(2, 10, 3, 11)).isEmpty());
        assertEquals(
                new RasterWindow(0, 0, 1, 1),
                RasterGridWindows.visibleWindow(metadata, new Envelope(2, 2, 3, 3)).orElseThrow());
    }

    @Test
    void immediateNeighborsOfAnExactEdgeSelectTheCorrectCell() {
        RasterSourceMetadata metadata = metadata(4, 1, new Envelope(0, 0, 4, 1));
        assertEquals(
                new RasterWindow(1, 0, 1, 1),
                RasterGridWindows.visibleWindow(metadata, new Envelope(1, 0, 2, 1)).orElseThrow());
        assertEquals(
                new RasterWindow(0, 0, 2, 1),
                RasterGridWindows.visibleWindow(metadata, new Envelope(Math.nextDown(1.0), 0, 2, 1))
                        .orElseThrow());
        assertEquals(
                new RasterWindow(1, 0, 2, 1),
                RasterGridWindows.visibleWindow(metadata, new Envelope(1, 0, Math.nextUp(2.0), 1))
                        .orElseThrow());
    }

    @Test
    void missingBoundsAndUncontainedWindowsAreProgrammerErrors() {
        RasterSourceMetadata missing =
                new RasterSourceMetadata(
                        new SourceIdentity("raster", "Raster"),
                        2,
                        2,
                        Optional.empty(),
                        Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> RasterGridWindows.visibleWindow(missing, new Envelope(0, 0, 1, 1)));
        RasterSourceMetadata metadata = metadata(2, 2, new Envelope(0, 0, 2, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> RasterGridWindows.mapBounds(metadata, new RasterWindow(1, 1, 2, 1)));
        assertThrows(
                NullPointerException.class,
                () -> RasterGridWindows.visibleWindow(null, new Envelope(0, 0, 1, 1)));
    }

    @Test
    void wideFiniteBoundsAndHugeDimensionsRetainExactOuterEdges() {
        Envelope wide = new Envelope(-8.0e307, -1, 8.0e307, 1);
        RasterSourceMetadata metadata = metadata(2, 1, wide);
        RasterWindow full = RasterGridWindows.visibleWindow(metadata, wide).orElseThrow();
        assertEquals(new RasterWindow(0, 0, 2, 1), full);
        assertEquals(wide, RasterGridWindows.mapBounds(metadata, full));

        RasterSourceMetadata huge = metadata(Integer.MAX_VALUE, 1, new Envelope(0, 0, 1, 1));
        assertEquals(
                new RasterWindow(0, 0, Integer.MAX_VALUE, 1),
                RasterGridWindows.visibleWindow(huge, huge.mapBounds().orElseThrow())
                        .orElseThrow());
    }

    @Test
    void collapsedOuterCellsAreTrimmedDeterministically() {
        RasterSourceMetadata metadata = metadata(4, 1, new Envelope(1.0e16, 0, 1.0e16 + 4.0, 1));
        int firstPositive = -1;
        int lastPositive = -1;
        for (int column = 0; column < 4; column++) {
            Envelope cell =
                    RasterGridWindows.mapBounds(metadata, new RasterWindow(column, 0, 1, 1));
            if (cell.width() > 0) {
                if (firstPositive < 0) {
                    firstPositive = column;
                }
                lastPositive = column;
            }
        }
        RasterWindow visible =
                RasterGridWindows.visibleWindow(metadata, metadata.mapBounds().orElseThrow())
                        .orElseThrow();
        assertEquals(firstPositive, visible.column());
        assertEquals(lastPositive - firstPositive + 1, visible.width());
    }

    @Test
    void hugeCollapsedGridPlanningRemainsDeterministic() {
        RasterSourceMetadata metadata =
                metadata(Integer.MAX_VALUE, 1, new Envelope(1.0e16, 0, 1.0e16 + 4.0, 1));
        Envelope fullBounds = metadata.mapBounds().orElseThrow();
        RasterWindow first = RasterGridWindows.visibleWindow(metadata, fullBounds).orElseThrow();
        RasterWindow second = RasterGridWindows.visibleWindow(metadata, fullBounds).orElseThrow();
        assertEquals(first, second);
        assertTrue(first.column() > 0);
        assertTrue(first.endColumn() < Integer.MAX_VALUE);
        assertTrue(first.width() > 0);
        assertEquals(fullBounds, RasterGridWindows.mapBounds(metadata, first));
    }

    @Test
    void affineFullPartialAndDisjointViewsAreConservative() {
        RasterSourceMetadata metadata =
                affineMetadata(4, 4, RasterAffineTransform.of(1, 0, 0, 1, 0, 0));
        Envelope fullBounds = new Envelope(-0.5, -0.5, 3.5, 3.5);
        assertEquals(fullBounds, metadata.mapBounds().orElseThrow());
        assertEquals(
                new RasterWindow(0, 0, 4, 4),
                RasterGridWindows.visibleWindow(metadata, fullBounds).orElseThrow());
        assertEquals(
                new RasterWindow(0, 0, 4, 4),
                RasterGridWindows.visibleWindow(metadata, new Envelope(.5, .5, 2.5, 2.5))
                        .orElseThrow());
        assertTrue(
                RasterGridWindows.visibleWindow(metadata, new Envelope(3.5, 0, 4.5, 1)).isEmpty());
        assertEquals(
                new Envelope(.5, .5, 2.5, 2.5),
                RasterGridWindows.mapBounds(metadata, new RasterWindow(1, 1, 2, 2)));
    }

    @Test
    void affineRotationAndShearClipEnvelopeCornersOutsideFootprint() {
        RasterAffineTransform rotation = RasterAffineTransform.of(1, 1, -1, 1, 0, 0);
        RasterSourceMetadata metadata = affineMetadata(4, 4, rotation);
        Envelope envelope = metadata.mapBounds().orElseThrow();
        assertTrue(
                RasterGridWindows.visibleWindow(
                                metadata,
                                new Envelope(
                                        envelope.minX(),
                                        envelope.maxY() - .25,
                                        envelope.minX() + .25,
                                        envelope.maxY()))
                        .isEmpty());
        RasterWindow center =
                RasterGridWindows.visibleWindow(metadata, new Envelope(-.25, 2.75, .25, 3.25))
                        .orElseThrow();
        assertTrue(center.width() <= 3);
        assertTrue(center.height() <= 3);

        RasterSourceMetadata sheared =
                affineMetadata(3, 2, RasterAffineTransform.of(2, .25, .75, -1, 10, 20));
        assertEquals(
                new RasterWindow(0, 0, 3, 2),
                RasterGridWindows.visibleWindow(sheared, sheared.mapBounds().orElseThrow())
                        .orElseThrow());
    }

    @Test
    void affineTouchingAndCornerOnlyContactAreEmpty() {
        RasterSourceMetadata metadata =
                affineMetadata(1, 1, RasterAffineTransform.of(1, 1, -1, 1, 0, 0));
        assertTrue(
                RasterGridWindows.visibleWindow(metadata, new Envelope(0, 1, .25, 1.25)).isEmpty());
        assertTrue(RasterGridWindows.visibleWindow(metadata, new Envelope(.5, .5, 1, 1)).isEmpty());
        assertEquals(
                new RasterWindow(0, 0, 1, 1),
                RasterGridWindows.visibleWindow(metadata, new Envelope(-.1, -.1, .1, .1))
                        .orElseThrow());
    }

    @Test
    void affineNegativeDeterminantAndOneCellRasterRetainEitherWinding() {
        RasterSourceMetadata metadata =
                affineMetadata(1, 1, RasterAffineTransform.of(-2, 0, 0, 3, 10, 20));
        assertEquals(
                new RasterWindow(0, 0, 1, 1),
                RasterGridWindows.visibleWindow(metadata, metadata.mapBounds().orElseThrow())
                        .orElseThrow());
        assertEquals(
                new Envelope(9, 18.5, 11, 21.5),
                RasterGridWindows.mapBounds(metadata, new RasterWindow(0, 0, 1, 1)));
    }

    @Test
    void affineHugeDimensionsAndThinPositiveAreasRemainRepresentable() {
        RasterSourceMetadata huge =
                affineMetadata(Integer.MAX_VALUE, 1, RasterAffineTransform.of(1, 0, 0, 1, 0, 0));
        assertEquals(
                new RasterWindow(0, 0, Integer.MAX_VALUE, 1),
                RasterGridWindows.visibleWindow(huge, huge.mapBounds().orElseThrow())
                        .orElseThrow());

        RasterSourceMetadata thin =
                affineMetadata(2, 2, RasterAffineTransform.of(1, 0, .5, 1, 0, 0));
        RasterWindow thinWindow =
                RasterGridWindows.visibleWindow(
                                thin, new Envelope(.749999999999, .499999999999, .75, .5))
                        .orElseThrow();
        assertTrue(thinWindow.width() > 0);
        assertTrue(thinWindow.height() > 0);
    }

    @Test
    void affineMapBoundsRejectsUncontainedWindows() {
        RasterSourceMetadata metadata =
                affineMetadata(2, 2, RasterAffineTransform.of(1, 0, 0, 1, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> RasterGridWindows.mapBounds(metadata, new RasterWindow(1, 1, 2, 1)));
    }

    @Test
    void affineEdgeCrossingWithoutContainedViewCornersRemainsVisible() {
        RasterSourceMetadata metadata =
                affineMetadata(4, 4, RasterAffineTransform.of(1, 1, -1, 1, 0, 0));
        Envelope narrowCrossingBand = new Envelope(-4, 2.9, 4, 3.1);
        assertEquals(
                new RasterWindow(0, 0, 4, 4),
                RasterGridWindows.visibleWindow(metadata, narrowCrossingBand).orElseThrow());
    }

    @Test
    void affineNegativeCoefficientsProduceConservativeWindowAndExactEnvelope() {
        RasterAffineTransform transform = RasterAffineTransform.of(-2, -.5, -.25, 3, 10, 20);
        RasterSourceMetadata metadata = affineMetadata(3, 2, transform);
        assertEquals(
                metadata.mapBounds().orElseThrow(),
                RasterGridWindows.mapBounds(metadata, new RasterWindow(0, 0, 3, 2)));
        assertEquals(
                new RasterWindow(0, 0, 3, 2),
                RasterGridWindows.visibleWindow(metadata, metadata.mapBounds().orElseThrow())
                        .orElseThrow());
    }

    @Test
    void affineOutwardRoundingAddsNoMoreThanOneFringeCellPerEdge() {
        RasterSourceMetadata metadata =
                affineMetadata(6, 6, RasterAffineTransform.of(1, 0, 0, 1, 0, 0));
        RasterWindow conservative =
                RasterGridWindows.visibleWindow(metadata, new Envelope(1.5, 1.5, 3.5, 3.5))
                        .orElseThrow();
        assertEquals(new RasterWindow(1, 1, 4, 4), conservative);
    }

    @Test
    void affinePowerOfTwoNormalizationHandlesLargeInverseVertices() {
        double tiny = 1.0e-100;
        RasterAffineTransform transform = RasterAffineTransform.of(tiny, 2 * tiny, 1, 1, .5, .5);
        RasterSourceMetadata metadata = affineMetadata(4, 4, transform);
        assertEquals(
                new RasterWindow(0, 0, 4, 4),
                RasterGridWindows.visibleWindow(metadata, metadata.mapBounds().orElseThrow())
                        .orElseThrow());
    }

    @Test
    void affineInverseArithmeticFailureIsNotReportedAsEmpty() {
        double tiny = 1.0e-308;
        RasterAffineTransform transform = RasterAffineTransform.of(tiny, 2 * tiny, 1, 1, .5, .5);
        RasterSourceMetadata metadata = affineMetadata(4, 4, transform);
        assertThrows(
                ArithmeticException.class,
                () ->
                        RasterGridWindows.visibleWindow(
                                metadata, metadata.mapBounds().orElseThrow()));
    }

    @Test
    void outputDensityDownsamplesAxisAndAffineBasesButCapsHugeZoomIn() {
        RasterSourceMetadata axis = metadata(100, 80, new Envelope(0, 0, 100, 80));
        assertEquals(
                new RasterGridWindows.OutputSize(50, 40),
                RasterGridWindows.outputSize(
                        axis,
                        new RasterWindow(0, 0, 100, 80),
                        new MapViewport(100, 80, 50, 40, 2)));
        assertEquals(
                new RasterGridWindows.OutputSize(100, 80),
                RasterGridWindows.outputSize(
                        axis,
                        new RasterWindow(0, 0, 100, 80),
                        new MapViewport(100, 80, 50, 40, Double.MIN_VALUE)));

        RasterSourceMetadata affine =
                affineMetadata(10, 10, RasterAffineTransform.of(3, 4, 0, 2, 0, 0));
        assertEquals(
                new RasterGridWindows.OutputSize(5, 2),
                RasterGridWindows.outputSize(
                        affine,
                        new RasterWindow(0, 0, 10, 10),
                        new MapViewport(100, 100, 0, 0, 10)));
    }

    private static RasterSourceMetadata metadata(int width, int height, Envelope bounds) {
        return new RasterSourceMetadata(
                new SourceIdentity("raster", "Raster"),
                width,
                height,
                Optional.of(bounds),
                Optional.empty());
    }

    private static RasterSourceMetadata affineMetadata(
            int width, int height, RasterAffineTransform transform) {
        return RasterSourceMetadata.withPlacement(
                new SourceIdentity("raster", "Raster"),
                width,
                height,
                RasterGridPlacement.affine(transform),
                Optional.empty());
    }
}
