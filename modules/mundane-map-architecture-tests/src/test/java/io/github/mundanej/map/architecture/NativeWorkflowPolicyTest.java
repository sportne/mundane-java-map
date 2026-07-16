package io.github.mundanej.map.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NativeWorkflowPolicyTest {
    private static final Path WORKFLOW =
            Path.of(System.getProperty("map.architecture.nativeWorkflow"));

    @Test
    void actualWorkflowIsTheOnePinnedBoundedNativeLane() throws IOException {
        assertTrue(NativeWorkflowPolicy.violations(Files.readString(WORKFLOW)).isEmpty());
    }

    @Test
    void policyRejectsRunnerCommandUploadAndJobMutations() throws IOException {
        String approved = Files.readString(WORKFLOW);
        assertRejected(approved.replace("ubuntu-24.04", "ubuntu-latest"));
        assertRejected(
                approved.replace(
                        "./gradlew nativeSmoke --console=plain",
                        "./gradlew nativeSmoke qualityGate --console=plain"));
        assertRejected(approved.replace("path: build/native-evidence", "path: build"));
        assertRejected(approved.replace("set -o pipefail\n", ""));
        assertRejected(approved.replace("if: always()", "if: success()"));
        assertRejected(approved + "  second-native-job:\n    runs-on: ubuntu-24.04\n");
    }

    private static void assertRejected(String workflow) {
        assertFalse(NativeWorkflowPolicy.violations(workflow).isEmpty());
    }
}
