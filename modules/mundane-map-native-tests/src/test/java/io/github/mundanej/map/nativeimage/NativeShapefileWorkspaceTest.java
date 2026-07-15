package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeShapefileWorkspaceTest {
    private static final NativeShapefileResources.Entry ABC =
            new NativeShapefileResources.Entry(
                    "/io/github/mundanej/map/nativeimage/shapefile/abc.shp",
                    "abc.shp",
                    3,
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

    @TempDir Path temporaryDirectory;

    @Test
    void actualWorkspaceVerifiesResourcesAndDeletesEveryKnownPath() {
        NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openShapefile();
        NativeShapefilePaths paths = workspace.shapefilePaths();
        Path directory = paths.shp().getParent();
        assertTrue(Files.isRegularFile(paths.prj()));
        workspace.close();

        assertFalse(Files.exists(directory));
        workspace.close();
        assertThrows(IllegalStateException.class, workspace::shapefilePaths);
    }

    @Test
    void shapefileAndRasterInventoriesUseIndependentTypedWorkspaces() {
        Path shapefileDirectory;
        Path rasterDirectory;
        try (NativeFixtureWorkspace shapefile = NativeFixtureWorkspace.openShapefile();
                NativeFixtureWorkspace raster = NativeFixtureWorkspace.openRaster()) {
            shapefileDirectory = shapefile.shapefilePaths().shp().getParent();
            rasterDirectory = raster.rasterPaths().png().getParent();
            assertFalse(shapefileDirectory.equals(rasterDirectory));
            assertThrows(IllegalStateException.class, shapefile::rasterPaths);
            assertThrows(IllegalStateException.class, raster::shapefilePaths);
        }
        assertFalse(Files.exists(shapefileDirectory));
        assertFalse(Files.exists(rasterDirectory));

        assertThrows(
                IllegalStateException.class,
                () ->
                        NativeFixtureWorkspace.open(
                                NativeRasterResources.INVENTORY,
                                ignored -> null,
                                new CapturingFiles(temporaryDirectory.resolve("missing-raster"))));
        try (NativeFixtureWorkspace shapefile = NativeFixtureWorkspace.openShapefile()) {
            assertTrue(Files.isRegularFile(shapefile.shapefilePaths().shp()));
        }
    }

    @Test
    void missingShortOverlongAndHashMismatchAllCleanTheWorkspace() {
        assertCopyFailure(name -> null);
        assertCopyFailure(name -> new ByteArrayInputStream(new byte[] {'a', 'b'}));
        assertCopyFailure(name -> new ByteArrayInputStream(new byte[] {'a', 'b', 'c', 'd'}));
        assertCopyFailure(name -> new ByteArrayInputStream(new byte[] {'x', 'y', 'z'}));
    }

    @Test
    void duplicateLocalNamesAreRejectedBeforeCreatingADirectory() {
        CapturingFiles files = new CapturingFiles(temporaryDirectory.resolve("duplicate"));
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeFixtureWorkspace.open(List.of(ABC, ABC), bytes("abc"), files));
        assertFalse(Files.exists(files.directory));
    }

    @Test
    void openFailureAfterCreationAndPartialWriteFailureStillCleanEverything() {
        CapturingFiles openFailure = new CapturingFiles(temporaryDirectory.resolve("open"));
        openFailure.failOpenAfterCreate = true;
        assertThrows(
                IllegalStateException.class,
                () -> NativeFixtureWorkspace.open(List.of(ABC), bytes("abc"), openFailure));
        assertFalse(Files.exists(openFailure.directory));

        CapturingFiles writeFailure = new CapturingFiles(temporaryDirectory.resolve("write"));
        writeFailure.failWriteAfterFirstByte = true;
        assertThrows(
                IllegalStateException.class,
                () -> NativeFixtureWorkspace.open(List.of(ABC), bytes("abc"), writeFailure));
        assertFalse(Files.exists(writeFailure.directory));
    }

    @Test
    void operationFailureRemainsPrimaryAndCleanupFailureIsSuppressed() {
        CapturingFiles files = new CapturingFiles(temporaryDirectory.resolve("suppressed"));
        files.failWriteAfterFirstByte = true;
        files.reportOneDeleteFailureAfterDeleting = true;

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> NativeFixtureWorkspace.open(List.of(ABC), bytes("abc"), files));

        assertTrue(failure.getCause().getMessage().contains("partial write"));
        assertTrue(failure.getSuppressed().length == 1);
        assertTrue(failure.getSuppressed()[0].getMessage().startsWith("fixture-workspace:"));
        assertFalse(Files.exists(files.directory));
    }

    @Test
    void cleanCloseReportsDeletionFailureAfterCompletingKnownPathCleanup() {
        CapturingFiles files = new CapturingFiles(temporaryDirectory.resolve("delete"));
        files.reportOneDeleteFailureAfterDeleting = true;
        NativeFixtureWorkspace workspace =
                NativeFixtureWorkspace.open(List.of(ABC), bytes("abc"), files);

        IllegalStateException failure = assertThrows(IllegalStateException.class, workspace::close);
        assertTrue(failure.getMessage().startsWith("fixture-workspace:"));
        assertFalse(Files.exists(files.directory));
        workspace.close();
    }

    private void assertCopyFailure(NativeFixtureWorkspace.ResourceReader reader) {
        Path directory = temporaryDirectory.resolve("copy-" + System.nanoTime());
        CapturingFiles files = new CapturingFiles(directory);
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> NativeFixtureWorkspace.open(List.of(ABC), reader, files));
        assertTrue(failure.getMessage().startsWith("fixture-workspace:"));
        assertFalse(Files.exists(directory));
    }

    private static NativeFixtureWorkspace.ResourceReader bytes(String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return ignored -> new ByteArrayInputStream(bytes);
    }

    private static final class CapturingFiles implements NativeFixtureWorkspace.WorkspaceFiles {
        private final Path directory;
        private boolean failOpenAfterCreate;
        private boolean failWriteAfterFirstByte;
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
            OutputStream delegate =
                    Files.newOutputStream(
                            path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if (failOpenAfterCreate) {
                delegate.close();
                throw new IOException("open failure after create");
            }
            if (!failWriteAfterFirstByte) {
                return delegate;
            }
            return new FilterOutputStream(delegate) {
                private boolean wrote;

                @Override
                public void write(int value) throws IOException {
                    if (wrote) {
                        throw new IOException("partial write");
                    }
                    super.write(value);
                    wrote = true;
                }

                @Override
                public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (length > 0) {
                        write(bytes[offset]);
                    }
                    throw new IOException("partial write");
                }
            };
        }

        @Override
        public boolean deleteIfExists(Path path) throws IOException {
            boolean deleted = Files.deleteIfExists(path);
            if (reportOneDeleteFailureAfterDeleting && !path.equals(directory)) {
                reportOneDeleteFailureAfterDeleting = false;
                throw new IOException("reported deletion failure");
            }
            return deleted;
        }
    }
}
