package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RasterValuesTest {
    @Test
    void metadataWindowsRequestsAndLimitsEnforceStructuralBounds() {
        RasterSourceMetadata metadata =
                new RasterSourceMetadata(
                        new SourceIdentity("raster", "Raster"),
                        4,
                        3,
                        Optional.of(new Envelope(10, 20, 14, 23)),
                        Optional.empty());
        assertEquals(4, metadata.width());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RasterSourceMetadata(
                                metadata.identity(), 0, 1, Optional.empty(), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RasterSourceMetadata(
                                metadata.identity(),
                                1,
                                1,
                                Optional.of(new Envelope(0, 0, 0, 1)),
                                Optional.empty()));

        RasterWindow window = new RasterWindow(Integer.MAX_VALUE, 2, 2, 3);
        assertEquals((long) Integer.MAX_VALUE + 2, window.endColumn());
        assertEquals(5, window.endRow());
        assertThrows(IllegalArgumentException.class, () -> new RasterWindow(-1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RasterWindow(0, 0, 0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterRequest(new RasterWindow(0, 0, 1, 1), 0, 1, Optional.empty()));
        RasterRequest compatible =
                new RasterRequest(new RasterWindow(0, 0, 1, 1), 1, 1, Optional.empty());
        assertEquals(RasterInterpolation.NEAREST, compatible.interpolation());
        RasterRequest bilinear =
                new RasterRequest(
                        compatible.sourceWindow(),
                        1,
                        1,
                        RasterInterpolation.BILINEAR,
                        Optional.empty());
        assertEquals(RasterInterpolation.BILINEAR, bilinear.interpolation());
        assertThrows(
                NullPointerException.class,
                () -> new RasterRequest(compatible.sourceWindow(), 1, 1, null, Optional.empty()));

        RasterRequestLimits tight = new RasterRequestLimits(1, 2, 3, 4, 5, 6);
        assertTrue(tight.tightens(new RasterRequestLimits(2, 3, 4, 5, 6, 7)));
        assertFalse(
                new RasterRequestLimits(3, 2, 3, 4, 5, 6)
                        .tightens(new RasterRequestLimits(2, 3, 4, 5, 6, 7)));
        assertThrows(
                IllegalArgumentException.class, () -> new RasterRequestLimits(0, 1, 1, 1, 1, 1));
    }

    @Test
    void pixelBuffersCopyIndexCompareAndTransferExactlyOnce() {
        int[] pixels = {0x01020304, 0x05060708, 0x11121314, 0x15161718};
        RgbaPixelBuffer copied = RgbaPixelBuffer.copyOf(2, 2, pixels);
        pixels[0] = 0;
        assertEquals(0x01020304, copied.rgbaAt(0, 0));
        int[] returned = copied.rgba();
        returned[1] = 0;
        assertArrayEquals(
                new int[] {0x01020304, 0x05060708, 0x11121314, 0x15161718}, copied.rgba());
        RgbaPixelBuffer.Builder builder = RgbaPixelBuffer.builder(2, 1);
        RgbaPixelBuffer built = builder.setRgba(0, 0, 0x01020304).setRgba(1, 0, 0x05060708).build();
        assertEquals(RgbaPixelBuffer.copyOf(2, 1, new int[] {0x01020304, 0x05060708}), built);
        assertNotEquals(copied, built);
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(IllegalStateException.class, () -> builder.setRgba(0, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> assertEquals(0, copied.rgbaAt(2, 0)));
        assertThrows(
                IllegalArgumentException.class, () -> RgbaPixelBuffer.copyOf(2, 2, new int[] {1}));
    }

    @Test
    void successfulReadRetainsWarningOnlyValues() {
        RasterWindow window = new RasterWindow(1, 2, 1, 1);
        SourceDiagnostic warning =
                new SourceDiagnostic(
                        "SOURCE_WARNING",
                        DiagnosticSeverity.WARNING,
                        "raster",
                        Optional.empty(),
                        "warning",
                        Map.of());
        RasterRead read =
                new RasterRead(
                        window,
                        RgbaPixelBuffer.copyOf(1, 1, new int[] {0x01020304}),
                        new DiagnosticReport(List.of(warning), 0));
        assertEquals(window, read.sourceWindow());
        SourceDiagnostic error =
                new SourceDiagnostic(
                        "SOURCE_FAILED",
                        DiagnosticSeverity.ERROR,
                        "raster",
                        Optional.empty(),
                        "error",
                        Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RasterRead(
                                window, read.pixels(), new DiagnosticReport(List.of(error), 0)));
    }
}
