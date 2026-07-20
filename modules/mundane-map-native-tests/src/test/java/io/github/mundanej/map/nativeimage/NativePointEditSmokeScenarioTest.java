package io.github.mundanej.map.nativeimage;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class NativePointEditSmokeScenarioTest {
    @Test
    void completesResourceFreePointEditPath() throws Exception {
        SwingUtilities.invokeAndWait(NativePointEditSmokeScenario::run);
    }
}
