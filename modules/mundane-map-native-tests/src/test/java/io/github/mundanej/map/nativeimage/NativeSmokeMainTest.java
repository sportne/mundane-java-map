package io.github.mundanej.map.nativeimage;

import org.junit.jupiter.api.Test;

class NativeSmokeMainTest {
    @Test
    void rendersThroughTheSmokePathOnTheJvm() {
        NativeSmokeMain.runSmoke();
    }
}
