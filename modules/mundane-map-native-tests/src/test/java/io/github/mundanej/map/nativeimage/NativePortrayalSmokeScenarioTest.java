package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativePortrayalSmokeScenarioTest {
    @Test
    void resourceFreePortrayalAndPlacementPathRunsOnTheJvm() {
        NativePortrayalSmokeScenario.Result result = NativePortrayalSmokeScenario.run();
        assertTrue(result.bluePixels() >= 40);
        assertTrue(result.placedLabels() == 1);
    }
}
