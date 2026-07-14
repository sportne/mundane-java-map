package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Envelope;
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

    private static RasterSourceMetadata metadata(int width, int height, Envelope bounds) {
        return new RasterSourceMetadata(
                new SourceIdentity("raster", "Raster"),
                width,
                height,
                Optional.of(bounds),
                Optional.empty());
    }
}
