package io.github.mundanej.map.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SqliteAdapterWorkflowPolicyTest {
    private static final Path WORKFLOW =
            Path.of(System.getProperty("map.architecture.sqliteAdapterWorkflow"));

    @Test
    void workflowPinsSupportedAndRejectedPlatformsAndRetainsExactShaEvidence() throws IOException {
        String workflow = Files.readString(WORKFLOW);

        requireOnce(workflow, "ubuntu-22.04");
        assertEquals(2, occurrences(workflow, "ubuntu-24.04"));
        requireOnce(workflow, "alpine:3.20");
        assertEquals(2, occurrences(workflow, "java-version: '21'"));
        requireOnce(workflow, "glibc: 'glibc 2.35'");
        requireOnce(workflow, "glibc: 'glibc 2.39'");
        assertEquals(2, occurrences(workflow, "getconf GNU_LIBC_VERSION"));
        requireOnce(workflow, "ldd --version");
        requireOnce(workflow, ":modules:mundane-map-io-geopackage-xerial:check");
        requireOnce(workflow, ":modules:mundane-map-io-mbtiles-xerial:check");
        assertEquals(4, occurrences(workflow, "stageDeploymentEvidence"));
        assertTrue(
                workflow.contains(
                        "io.github.mundanej.map.io.geopackage.GeoPackageDeploymentProbe"));
        assertTrue(workflow.contains("io.github.mundanej.map.io.mbtiles.MbTilesDeploymentProbe"));
        requireOnce(workflow, "SQLITE_ADAPTER_UNAVAILABLE|nativeLoad");
        requireOnce(workflow, "SQLITE_ADAPTER_UNAVAILABLE|temporaryDirectory");
        assertEquals(3, occurrences(workflow, "SQLITE_ADAPTER_UNAVAILABLE|unsupportedPlatform"));
        requireOnce(workflow, "-Dos.name='Not Linux'");
        requireOnce(workflow, "-Dos.arch=not-x86");
        assertTrue(occurrences(workflow, "${{ github.sha }}") >= 3);
        assertEquals(2, occurrences(workflow, "actions/upload-artifact@v4"));
        assertEquals(2, occurrences(workflow, "if: always()"));
    }

    private static void requireOnce(String workflow, String expected) {
        assertEquals(1, occurrences(workflow, expected), expected);
    }

    private static int occurrences(String value, String token) {
        int result = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            result++;
            offset += token.length();
        }
        return result;
    }
}
