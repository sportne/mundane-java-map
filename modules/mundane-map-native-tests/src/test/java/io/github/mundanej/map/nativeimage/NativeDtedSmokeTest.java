package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.ElevationUnit;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NativeDtedSmokeTest {
    @Test
    void sharedScenarioReadsQueriesRendersDiagnosesAndCloses() {
        Path directory;
        NativeDtedSmokeScenario.Result result;
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openDted()) {
            NativeDtedPaths paths = workspace.dtedPaths();
            directory = paths.valid().getParent();
            result = NativeDtedSmokeScenario.run(paths);
            assertTrue(Files.isRegularFile(paths.valid()));
            assertTrue(Files.isRegularFile(paths.truncated()));
        }

        assertFalse(Files.exists(directory));
        assertEquals(21, result.metadata().columnCount());
        assertEquals(121, result.metadata().rowCount());
        assertEquals(1_000, result.nearest().value());
        assertEquals(1_250.5, result.bilinear().value());
        assertEquals(ElevationUnit.METRE, result.bilinear().unit());
        assertTrue(result.sourceClosed());
        assertTrue(result.rendering().unshadedNonWhite() >= 13_000);
        assertEquals(result.rendering().unshadedNonWhite(), result.rendering().shadedNonWhite());
        assertTrue(
                result.rendering().shadedLuminance() * 100
                        <= result.rendering().unshadedLuminance() * 95);
        assertEquals("DTED_FILE_LENGTH_MISMATCH", result.malformed().entries().getLast().code());
    }

    @Test
    void renderAndDiagnosticMutationControlsReachSharedAssertions() {
        BufferedImage blank =
                new BufferedImage(
                        NativeDtedSmokeScenario.IMAGE_SIZE,
                        NativeDtedSmokeScenario.IMAGE_SIZE,
                        BufferedImage.TYPE_INT_ARGB);
        var graphics = blank.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, blank.getWidth(), blank.getHeight());
        graphics.dispose();
        IllegalStateException rendering =
                assertThrows(
                        IllegalStateException.class,
                        () -> NativeDtedSmokeScenario.assertUnshaded(blank));
        assertTrue(rendering.getMessage().startsWith("dted-render:"));

        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openDted()) {
            IllegalStateException diagnostic =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    NativeDtedSmokeScenario.assertMalformed(
                                            workspace.dtedPaths().valid()));
            assertTrue(diagnostic.getMessage().startsWith("dted-diagnostic:"));
        }
    }
}
