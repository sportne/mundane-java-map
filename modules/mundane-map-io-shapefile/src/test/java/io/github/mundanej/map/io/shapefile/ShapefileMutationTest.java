package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class ShapefileMutationTest {
    @TempDir Path temporaryDirectory;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void allCommittedMutationCasesRepeatThroughThePublicSourcePath() throws Exception {
        int cases = 0;
        ShapefileMutationHarness.NormalizedOutcome sentinel =
                ShapefileMutationHarness.sentinel(
                        temporaryDirectory, ShapefileMutationHarness.Family.SHP);
        for (ShapefileMutationHarness.Family family : ShapefileMutationHarness.Family.values()) {
            for (int index = 0; index < family.cases(); index++) {
                ShapefileMutationHarness.ReplayDescriptor descriptor =
                        ShapefileMutationHarness.replay(family.name(), index);
                var first = ShapefileMutationHarness.run(temporaryDirectory, descriptor, 0);
                var second = ShapefileMutationHarness.run(temporaryDirectory, descriptor, 1);
                assertEquals(first, second, descriptor.toString());
                cases++;
            }
            var clean = ShapefileMutationHarness.sentinel(temporaryDirectory, family);
            assertEquals(sentinel, clean, family.name());
        }
        assertEquals(256, cases);
        assertThrows(
                IllegalArgumentException.class,
                () -> ShapefileMutationHarness.replay("UNKNOWN", 0));
        assertThrows(
                IllegalArgumentException.class, () -> ShapefileMutationHarness.replay("SHP", 52));
    }
}
