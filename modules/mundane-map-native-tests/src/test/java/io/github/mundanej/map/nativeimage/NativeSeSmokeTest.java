package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeSeSmokeTest {
    @Test
    void sharedScenarioParsesEvaluatesCatalogRendersAndRejectsHostileXml() {
        NativeSeSmokeScenario.Result result = NativeSeSmokeScenario.run();

        assertTrue(result.bluePixels() >= 80);
        assertEquals("SE_XML_SECURITY", result.securityCode());
    }
}
