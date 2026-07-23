package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeMilitarySymbolSmokeTest {
    @Test
    void sharedScenarioParsesPortraysRendersDegradesAndDiagnoses() throws Exception {
        NativeMilitarySymbolSmokeScenario.Result[] result =
                new NativeMilitarySymbolSmokeScenario.Result[1];
        javax.swing.SwingUtilities.invokeAndWait(
                () -> result[0] = NativeMilitarySymbolSmokeScenario.run());

        assertEquals(NativeMilitarySymbolSmokeScenario.SUPPORTED, result[0].canonical());
        assertTrue(result[0].coloredPixels() >= 700);
        assertTrue(result[0].degradedColoredPixels() >= 700);
        assertEquals("MIL2525_ENTITY_UNSUPPORTED", result[0].degradedCode());
        assertEquals("MIL2525_SIDC_CHARACTER", result[0].malformedCode());
        assertEquals("MIL2525_CONTEXT_UNSUPPORTED", result[0].unsupportedCode());
    }
}
