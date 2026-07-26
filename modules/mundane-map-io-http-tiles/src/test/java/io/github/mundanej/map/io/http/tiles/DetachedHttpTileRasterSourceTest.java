package io.github.mundanej.map.io.http.tiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DetachedHttpTileRasterSourceTest {
    @Test
    void detachedSourceExposesStateAndSupportsBothSamplingModes() {
        DetachedHttpTileRasterSource source =
                new DetachedHttpTileRasterSource(
                        new SourceIdentity("detached", "Detached"),
                        new Envelope(0, 0, 2, 2),
                        RasterSourceLimits.LEVEL_1,
                        RgbaPixelBuffer.copyOf(
                                2, 2, new int[] {0xff0000ff, 0x00ff00ff, 0x0000ffff, 0xffffffff}));

        assertEquals("detached", source.metadata().identity().id());
        assertEquals(RasterSourceLimits.LEVEL_1, source.limits());
        assertTrue(source.openingDiagnostics().entries().isEmpty());
        assertFalse(source.isClosed());

        assertEquals(
                0xffffffff,
                source.read(
                                new RasterRequest(
                                        new RasterWindow(0, 0, 2, 2),
                                        1,
                                        1,
                                        RasterInterpolation.NEAREST,
                                        Optional.empty()),
                                CancellationToken.none())
                        .pixels()
                        .rgbaAt(0, 0));
        assertEquals(
                0x808080ff,
                source.read(
                                new RasterRequest(
                                        new RasterWindow(0, 0, 2, 2),
                                        1,
                                        1,
                                        RasterInterpolation.BILINEAR,
                                        Optional.empty()),
                                CancellationToken.none())
                        .pixels()
                        .rgbaAt(0, 0));

        RasterRequestLimits looser =
                new RasterRequestLimits(
                        RasterRequestLimits.LEVEL_1.sourceWindowPixels() + 1,
                        RasterRequestLimits.LEVEL_1.outputDimension(),
                        RasterRequestLimits.LEVEL_1.outputPixels(),
                        RasterRequestLimits.LEVEL_1.decodedIntermediateBytes(),
                        RasterRequestLimits.LEVEL_1.ownedPayloadBytes(),
                        RasterRequestLimits.LEVEL_1.retainedWarnings());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        source.read(
                                new RasterRequest(
                                        new RasterWindow(0, 0, 1, 1), 1, 1, Optional.of(looser)),
                                CancellationToken.none()));

        source.close();
        assertTrue(source.isClosed());
        assertThrows(
                IllegalStateException.class,
                () ->
                        source.read(
                                new RasterRequest(
                                        new RasterWindow(0, 0, 1, 1), 1, 1, Optional.empty()),
                                CancellationToken.none()));
    }
}
