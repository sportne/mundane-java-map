package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeMapLibreSmokeTest {
    @Test
    void sharedScenarioParsesEvaluatesBindsResolvesLabelRendersIconAndRejectsUnsupportedRoot() {
        NativeMapLibreSmokeScenario.Result result = NativeMapLibreSmokeScenario.run();

        assertTrue(result.redPixels() >= 100);
        assertTrue(result.bluePixels() >= 40);
        assertEquals(1, result.labelCount());
        assertEquals("MAPLIBRE_ROOT_UNSUPPORTED", result.diagnosticCode());
    }
}
