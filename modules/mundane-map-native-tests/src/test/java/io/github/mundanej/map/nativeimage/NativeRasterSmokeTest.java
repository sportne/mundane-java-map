package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.ImagePlacement;
import io.github.mundanej.map.io.image.RasterImages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

class NativeRasterSmokeTest {
    @Test
    void sharedScenarioExercisesBothCodecsAndDeletesItsWorkspace() {
        Path directory;
        NativeRasterSmokeScenario.Result result;
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openRaster()) {
            NativeRasterPaths paths = workspace.rasterPaths();
            directory = paths.png().getParent();
            result = NativeRasterSmokeScenario.run(paths);
            assertTrue(Files.isDirectory(directory));
        }

        assertEquals(NativeRasterSmokeScenario.REGISTRY_ORDER, result.registryOrder());
        assertTrue(result.pngNonWhitePixels() >= 100 && result.pngNonWhitePixels() <= 25_000);
        assertTrue(result.jpegNonWhitePixels() >= 100 && result.jpegNonWhitePixels() <= 25_000);
        assertEquals(
                "IMAGE_CONTAINER_INVALID", result.malformedReport().entries().getLast().code());
        assertFalse(Files.exists(directory));
    }

    @Test
    void fixedResourcesMatchLiteralLengthsHashesMetadataAndProvenance() throws Exception {
        for (NativeRasterResources.Entry entry : NativeRasterResources.INVENTORY) {
            byte[] bytes = resource(entry.resourceName());
            assertEquals(entry.length(), bytes.length);
            assertEquals(entry.sha256(), sha256(bytes));
        }
        assertArrayEquals(
                "20\n5\n4\n-18\n1000\n2000\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                resource(NativeRasterResources.PNG_WORLD.resourceName()));
        assertArrayEquals(
                "12\n2\n-3\n-10\n3000\n1000\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                resource(NativeRasterResources.JPEG_WORLD.resourceName()));

        byte[] malformed = resource(NativeRasterResources.MALFORMED.resourceName());
        assertEquals(70, malformed.length);
        assertEquals('I', malformed[37]);
        assertEquals('D', malformed[38]);
        assertEquals('A', malformed[39]);
        assertEquals('T', malformed[40]);
        CRC32 crc = new CRC32();
        crc.update(malformed, 37, 17);
        long actual = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(malformed, 54, 4).getInt());
        assertEquals(1, Long.bitCount(actual ^ crc.getValue()));

        String provenance =
                new String(
                        resource("/io/github/mundanej/map/nativeimage/raster/PROVENANCE.md"),
                        java.nio.charset.StandardCharsets.UTF_8);
        for (NativeRasterResources.Entry entry : NativeRasterResources.INVENTORY) {
            assertTrue(provenance.contains(entry.localName()));
            assertTrue(provenance.contains(Integer.toString(entry.length())));
            assertTrue(provenance.contains(entry.sha256()));
        }
        assertTrue(provenance.contains("BSD-3-Clause"));
        assertTrue(provenance.contains("Python 3.12.3"));
        assertTrue(provenance.contains("ImageMagick 6.9.12-98"));
    }

    @Test
    void semanticNegativeControlsFailTheirBoundedInvariantTokens() {
        CrsMetadata crs =
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty());
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openRaster();
                RasterSource source =
                        RasterImages.open(
                                workspace.rasterPaths().png(),
                                new SourceIdentity("negative-png", "negative"),
                                ImageOpenOptions.defaults()
                                        .withPlacement(ImagePlacement.worldFile(crs)),
                                AwtRasterDecoders.level1())) {
            IllegalStateException coefficient =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    NativeRasterSmokeScenario.assertMetadata(
                                            source.metadata().width(),
                                            source.metadata().height(),
                                            source.metadata().mapBounds().orElseThrow(),
                                            source.metadata()
                                                    .gridPlacement()
                                                    .orElseThrow()
                                                    .affineTransform()
                                                    .orElseThrow(),
                                            source.metadata().crs().orElseThrow(),
                                            4,
                                            4,
                                            NativeRasterSmokeScenario.PNG_ENVELOPE,
                                            RasterAffineTransform.of(21, 5, 4, -18, 1000, 2000),
                                            "raster-png-metadata"));
            assertTrue(coefficient.getMessage().startsWith("raster-png-metadata:"));
        }

        RasterRead read =
                new RasterRead(
                        new RasterWindow(0, 0, 2, 2),
                        RgbaPixelBuffer.copyOf(
                                2, 2, new int[] {0xdc2828ff, 0x1eb450ff, 0x285adcff, 0xf0be1e80}),
                        DiagnosticReport.empty());
        IllegalStateException sample =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                NativeRasterSmokeScenario.assertSamples(
                                        read,
                                        new int[] {0, 0x1eb450ff, 0x285adcff, 0xf0be1e80},
                                        0,
                                        "raster-png-bilinear"));
        assertTrue(sample.getMessage().startsWith("raster-png-bilinear:"));

        IllegalStateException opacity =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                NativeRasterSmokeScenario.assertColorInvariant(
                                        0xffee9494, 0xff000000, 0, "raster-png-opacity"));
        assertTrue(opacity.getMessage().startsWith("raster-png-opacity:"));
    }

    @Test
    void malformedDiagnosticNegativeControlFailsTheDiagnosticInvariant() {
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openRaster()) {
            Path path = workspace.rasterPaths().malformed();
            SourceException actual =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    RasterImages.open(
                                            path,
                                            new SourceIdentity(
                                                    NativeRasterSmokeScenario.MALFORMED_SOURCE_ID,
                                                    "malformed"),
                                            ImageOpenOptions.defaults(),
                                            AwtRasterDecoders.level1()));
            SourceDiagnostic terminal = actual.terminal();
            SourceDiagnostic warning =
                    new SourceDiagnostic(
                            "IMAGE_WARNING",
                            DiagnosticSeverity.WARNING,
                            terminal.sourceId(),
                            terminal.location(),
                            "Preceding warning",
                            Map.of("format", "PNG"));
            assertMalformedMutationRejected(
                    new SourceException(
                            new DiagnosticReport(List.of(warning, terminal), 0), terminal),
                    path);
            assertMalformedMutationRejected(
                    new SourceException(new DiagnosticReport(List.of(terminal), 1), terminal),
                    path);
            assertMalformedMutationRejected(
                    mutation(
                            terminal,
                            terminal.message(),
                            Map.of("format", "PNG", "reason", "changed")),
                    path);
            SourceDiagnostic changedLocation =
                    new SourceDiagnostic(
                            terminal.code(),
                            terminal.severity(),
                            terminal.sourceId(),
                            Optional.of(
                                    new DiagnosticLocation(
                                            Optional.of("image"),
                                            OptionalLong.of(1),
                                            OptionalInt.empty(),
                                            OptionalInt.empty(),
                                            Optional.empty(),
                                            OptionalLong.of(54))),
                            terminal.message(),
                            terminal.context());
            assertMalformedMutationRejected(
                    new SourceException(
                            new DiagnosticReport(List.of(changedLocation), 0), changedLocation),
                    path);
            assertMalformedMutationRejected(
                    mutation(terminal, "Encoded image CRC is invalid", terminal.context()), path);
            for (String leaked :
                    List.of(
                            path.toAbsolutePath().toString(),
                            "provider javax.imageio",
                            "ImageReader failed",
                            "com.sun.imageio.plugins.png.PNGImageReader",
                            "89 50 4e 47 0d 0a 1a 0a")) {
                assertMalformedMutationRejected(
                        mutation(terminal, leaked, terminal.context()),
                        path,
                        "leaked implementation detail");
            }
        }
    }

    private static SourceException mutation(
            SourceDiagnostic basis, String message, Map<String, String> context) {
        SourceDiagnostic changed =
                new SourceDiagnostic(
                        basis.code(),
                        basis.severity(),
                        basis.sourceId(),
                        basis.location(),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(changed), 0), changed);
    }

    private static void assertMalformedMutationRejected(SourceException mutation, Path path) {
        assertMalformedMutationRejected(mutation, path, "raster-diagnostic:");
    }

    private static void assertMalformedMutationRejected(
            SourceException mutation, Path path, String expectedMessage) {
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> NativeRasterSmokeScenario.assertMalformedDiagnostic(mutation, path));
        assertTrue(failure.getMessage().contains(expectedMessage));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = NativeRasterSmokeTest.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing fixed resource " + name);
            }
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }
}
