package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SyntheticRasterSourceTest {
    @Test
    void fullAndSubwindowReadsExposeAbsoluteDeterministicPattern() {
        SyntheticRasterSource source = source(4, 3);
        RasterRead full =
                source.read(request(new RasterWindow(0, 0, 4, 3), 4, 3), CancellationToken.none());
        assertEquals(new RasterWindow(0, 0, 4, 3), full.sourceWindow());
        assertEquals(pixel(0, 0), full.pixels().rgbaAt(0, 0));
        assertEquals(pixel(3, 2), full.pixels().rgbaAt(3, 2));
        RasterRead sub =
                source.read(request(new RasterWindow(2, 1, 2, 2), 2, 2), CancellationToken.none());
        assertArrayEquals(
                new int[] {pixel(2, 1), pixel(3, 1), pixel(2, 2), pixel(3, 2)},
                sub.pixels().rgba());
        assertTrue(sub.diagnostics().entries().isEmpty());
    }

    @Test
    void nearestSamplingUsesPixelCentersAndRightBottomTies() {
        SyntheticRasterSource source = source(4, 4);
        RasterRead down =
                source.read(request(new RasterWindow(0, 0, 4, 4), 2, 2), CancellationToken.none());
        assertArrayEquals(
                new int[] {pixel(1, 1), pixel(3, 1), pixel(1, 3), pixel(3, 3)},
                down.pixels().rgba());
        RasterRead up =
                source.read(request(new RasterWindow(1, 1, 2, 2), 4, 4), CancellationToken.none());
        assertEquals(pixel(1, 1), up.pixels().rgbaAt(0, 0));
        assertEquals(pixel(2, 1), up.pixels().rgbaAt(2, 0));
        assertEquals(pixel(2, 2), up.pixels().rgbaAt(3, 3));
    }

    @Test
    void bilinearSamplingUsesTheSameWindowLocalCoreOracle() {
        SyntheticRasterSource source = source(4, 4);
        RasterWindow window = new RasterWindow(1, 1, 2, 2);
        RasterRead read =
                source.read(
                        new RasterRequest(
                                window, 4, 4, RasterInterpolation.BILINEAR, Optional.empty()),
                        CancellationToken.none());
        var x = RasterResampling.bilinearAxis(1, 2, 4);
        var y = RasterResampling.bilinearAxis(1, 2, 4);
        assertEquals(
                RasterResampling.bilinearRgba(
                        pixel(1, 1), pixel(2, 1), pixel(1, 2), pixel(2, 2), x, y),
                read.pixels().rgbaAt(1, 1));
        assertEquals(pixel(1, 1), read.pixels().rgbaAt(0, 0));
        assertEquals(pixel(2, 2), read.pixels().rgbaAt(3, 3));
    }

    @Test
    void bothModesMatchSharedMathForOneDimensionalWindows() {
        SyntheticRasterSource source = source(6, 6);
        for (RasterInterpolation interpolation : RasterInterpolation.values()) {
            RasterWindow horizontal = new RasterWindow(1, 3, 4, 1);
            RasterRead horizontalRead =
                    source.read(
                            new RasterRequest(horizontal, 7, 1, interpolation, Optional.empty()),
                            CancellationToken.none());
            assertEquals(pixel(1, 3), horizontalRead.pixels().rgbaAt(0, 0));
            assertEquals(pixel(4, 3), horizontalRead.pixels().rgbaAt(6, 0));

            RasterWindow vertical = new RasterWindow(2, 1, 1, 4);
            RasterRead verticalRead =
                    source.read(
                            new RasterRequest(vertical, 1, 7, interpolation, Optional.empty()),
                            CancellationToken.none());
            assertEquals(pixel(2, 1), verticalRead.pixels().rgbaAt(0, 0));
            assertEquals(pixel(2, 4), verticalRead.pixels().rgbaAt(0, 6));
        }
    }

    @Test
    void bothModesCancelAtEveryGenerationCheckpointAndRemainReusable() {
        for (RasterInterpolation interpolation : RasterInterpolation.values()) {
            for (int poll = 1; poll <= 8; poll++) {
                int cancellationCheckpoint = poll;
                SyntheticRasterSource source = source(4, 4);
                SourceException failure =
                        assertThrows(
                                SourceException.class,
                                () ->
                                        source.read(
                                                new RasterRequest(
                                                        new RasterWindow(0, 0, 4, 4),
                                                        4,
                                                        4,
                                                        interpolation,
                                                        Optional.empty()),
                                                new CountingToken(cancellationCheckpoint)));
                assertEquals("SOURCE_CANCELLED", failure.terminal().code());
                assertEquals(
                        pixel(0, 0),
                        source.read(
                                        request(new RasterWindow(0, 0, 1, 1), 1, 1),
                                        CancellationToken.none())
                                .pixels()
                                .rgbaAt(0, 0));
            }
        }
    }

    @Test
    void strictWindowAndTighterLimitsFailWithoutClosingSource() {
        SyntheticRasterSource source = source(4, 4);
        SourceException outside =
                assertThrows(
                        SourceException.class,
                        () ->
                                source.read(
                                        request(new RasterWindow(3, 3, 2, 1), 2, 1),
                                        CancellationToken.none()));
        assertEquals("RASTER_WINDOW_OUT_OF_RANGE", outside.terminal().code());
        RasterRequestLimits tight = new RasterRequestLimits(16, 1, 1, 4, 4, 1);
        SourceException limited =
                assertThrows(
                        SourceException.class,
                        () ->
                                source.read(
                                        new RasterRequest(
                                                new RasterWindow(0, 0, 2, 2),
                                                2,
                                                2,
                                                Optional.of(tight)),
                                        CancellationToken.none()));
        assertEquals("SOURCE_LIMIT_EXCEEDED", limited.terminal().code());
        assertFalse(source.isClosed());
        source.read(request(new RasterWindow(0, 0, 1, 1), 1, 1), CancellationToken.none());
    }

    @Test
    void preAndMidGenerationCancellationDiscardPixelsAndPermitReuse() {
        SyntheticRasterSource source = source(8, 8);
        CancellationSource pre = new CancellationSource();
        pre.cancel();
        SourceException preFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                source.read(
                                        request(new RasterWindow(0, 0, 8, 8), 8, 8), pre.token()));
        assertEquals("SOURCE_CANCELLED", preFailure.terminal().code());
        SourceException midFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                source.read(
                                        request(new RasterWindow(0, 0, 8, 8), 8, 8),
                                        new CountingToken(5)));
        assertEquals("SOURCE_CANCELLED", midFailure.terminal().code());
        RasterRead retry =
                source.read(request(new RasterWindow(0, 0, 1, 1), 1, 1), CancellationToken.none());
        assertEquals(pixel(0, 0), retry.pixels().rgbaAt(0, 0));
    }

    @Test
    void closeIsIdempotentAndRetainedValuesSurvive() {
        SyntheticRasterSource source = source(2, 2);
        RasterRead retained =
                source.read(request(new RasterWindow(0, 0, 1, 1), 1, 1), CancellationToken.none());
        source.close();
        source.close();
        assertTrue(source.isClosed());
        assertEquals(pixel(0, 0), retained.pixels().rgbaAt(0, 0));
        assertThrows(
                IllegalStateException.class,
                () ->
                        source.read(
                                request(new RasterWindow(0, 0, 1, 1), 1, 1),
                                CancellationToken.none()));
    }

    private static SyntheticRasterSource source(int width, int height) {
        return SyntheticRasterSource.open(
                new SourceIdentity("raster", "Raster"),
                width,
                height,
                Optional.of(new Envelope(0, 0, width, height)),
                Optional.empty(),
                RasterSourceLimits.LEVEL_1);
    }

    private static RasterRequest request(RasterWindow window, int outputWidth, int outputHeight) {
        return new RasterRequest(window, outputWidth, outputHeight, Optional.empty());
    }

    private static int pixel(int column, int row) {
        return ((column & 0xff) << 24)
                | ((row & 0xff) << 16)
                | (((column ^ row) & 0xff) << 8)
                | 0xff;
    }

    private static final class CountingToken implements CancellationToken {
        private final int cancellationPoll;
        private int polls;

        private CountingToken(int cancellationPoll) {
            this.cancellationPoll = cancellationPoll;
        }

        @Override
        public boolean isCancellationRequested() {
            return ++polls == cancellationPoll;
        }
    }
}
