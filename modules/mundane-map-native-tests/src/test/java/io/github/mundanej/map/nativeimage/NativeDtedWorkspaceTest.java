package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeDtedWorkspaceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void actualResourceIsPinnedAndDerivativeDeletesWithItsWorkspace() throws Exception {
        NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openDted();
        NativeDtedPaths paths = workspace.dtedPaths();
        Path directory = paths.valid().getParent();
        byte[] valid = Files.readAllBytes(paths.valid());
        byte[] truncated = Files.readAllBytes(paths.truncated());
        assertEquals(8_762, valid.length);
        assertEquals(8_761, truncated.length);
        assertEquals(
                "9b0f2d2d0b1fdeefb2e551fee98c4fac2da88141dc0fd02e712840fc9508c802",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valid)));
        assertArrayEquals(Arrays.copyOf(valid, 8_761), truncated);
        assertThrows(IllegalStateException.class, workspace::rasterPaths);
        workspace.close();
        workspace.close();
        assertFalse(Files.exists(directory));
        assertThrows(IllegalStateException.class, workspace::dtedPaths);
    }

    @Test
    void missingShortLongAndWrongHashResourcesCleanWithoutAWorkspaceLeak() {
        assertResourceFailure(null);
        assertResourceFailure(new byte[8_761]);
        assertResourceFailure(new byte[8_763]);
        assertResourceFailure(new byte[8_762]);
    }

    @Test
    void derivedOpenFailureDeletesTheValidFileAndDirectory() {
        byte[] approved = approvedResource();
        CapturingFiles files = new CapturingFiles(temporaryDirectory.resolve("derived-open"));
        files.failSecondOpenAfterCreate = true;
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                NativeFixtureWorkspace.openDted(
                                        ignored -> new ByteArrayInputStream(approved), files));
        assertTrue(failure.getMessage().startsWith("dted-resource:"));
        assertFalse(Files.exists(files.directory));
    }

    @Test
    void cleanupFailureUsesTheDtedCleanupInvariantAfterDeletingEverything() {
        byte[] approved = approvedResource();
        CapturingFiles files = new CapturingFiles(temporaryDirectory.resolve("cleanup"));
        NativeFixtureWorkspace workspace =
                NativeFixtureWorkspace.openDted(
                        ignored -> new ByteArrayInputStream(approved), files);
        files.reportOneDeleteFailureAfterDeleting = true;
        IllegalStateException failure = assertThrows(IllegalStateException.class, workspace::close);
        assertTrue(failure.getMessage().startsWith("dted-cleanup:"));
        assertFalse(Files.exists(files.directory));
    }

    private void assertResourceFailure(byte[] bytes) {
        Path directory = temporaryDirectory.resolve("resource-" + System.nanoTime());
        CapturingFiles files = new CapturingFiles(directory);
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                NativeFixtureWorkspace.openDted(
                                        ignored ->
                                                bytes == null
                                                        ? null
                                                        : new ByteArrayInputStream(bytes),
                                        files));
        assertTrue(failure.getMessage().startsWith("dted-resource:"));
        assertFalse(Files.exists(directory));
    }

    private static byte[] approvedResource() {
        try (var input =
                NativeDtedWorkspaceTest.class.getResourceAsStream(
                        NativeDtedResources.LEVEL_ZERO.resourceName())) {
            if (input == null) {
                throw new AssertionError("DTED test resource is absent");
            }
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class CapturingFiles implements NativeFixtureWorkspace.WorkspaceFiles {
        private final Path directory;
        private int opens;
        private boolean failSecondOpenAfterCreate;
        private boolean reportOneDeleteFailureAfterDeleting;

        private CapturingFiles(Path directory) {
            this.directory = directory;
        }

        @Override
        public Path createTemporaryDirectory() throws IOException {
            return Files.createDirectory(directory);
        }

        @Override
        public OutputStream openNew(Path path) throws IOException {
            opens++;
            OutputStream output =
                    Files.newOutputStream(
                            path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if (failSecondOpenAfterCreate && opens == 2) {
                output.close();
                throw new IOException("derived open failure");
            }
            return output;
        }

        @Override
        public boolean deleteIfExists(Path path) throws IOException {
            boolean deleted = Files.deleteIfExists(path);
            if (reportOneDeleteFailureAfterDeleting && !path.equals(directory)) {
                reportOneDeleteFailureAfterDeleting = false;
                throw new IOException("reported DTED cleanup failure");
            }
            return deleted;
        }
    }
}
