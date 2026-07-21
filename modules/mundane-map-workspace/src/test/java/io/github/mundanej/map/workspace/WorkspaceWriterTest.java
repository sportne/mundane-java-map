package io.github.mundanej.map.workspace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceWriterTest {
    private static final String CANONICAL =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <workspace xmlns="urn:mundanej:map:workspace" version="1">
              <view map-crs="EPSG:4326" display-crs="EPSG:3857"
                    center-x="0.0" center-y="0.0" units-per-pixel="1000.0"/>
              <layers>
                <feature-layer id="roads" name="Roads">
                  <source opener="application.shapefile.v1" id="roads-source"
                          name="Road data" path="data/roads.shp"/>
                  <symbols catalog="application.default" marker="point" line="road" fill="area"/>
                </feature-layer>
                <raster-layer id="image" name="Image" interpolation="BILINEAR" opacity="1.0">
                  <source opener="application.image.v1" id="image-source"
                          name="Image data" path="data/image.png"/>
                </raster-layer>
              </layers>
            </workspace>
            """;

    @TempDir Path temporary;

    @Test
    void writesExactCanonicalBytesAndRoundTripsIndependentlyOfAmbientFormatting()
            throws IOException {
        Path first = temporary.resolve("first.mmap.xml");
        Path second = temporary.resolve("second.mmap.xml");
        Locale previousLocale = Locale.getDefault();
        String previousSeparator = System.getProperty("line.separator");
        try {
            Locale.setDefault(Locale.FRANCE);
            System.setProperty("line.separator", "\r\n");
            WorkspaceFiles.write(first, canonicalDocument(), WorkspaceLimits.DEFAULT);
        } finally {
            Locale.setDefault(previousLocale);
            System.setProperty("line.separator", previousSeparator);
        }

        assertArrayEquals(CANONICAL.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(first));
        WorkspaceFile read = WorkspaceFiles.read(first, WorkspaceLimits.DEFAULT);
        WorkspaceFiles.write(second, read.document(), WorkspaceLimits.DEFAULT);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));

        Files.writeString(first, "old", StandardCharsets.UTF_8);
        WorkspaceFiles.write(first, canonicalDocument(), WorkspaceLimits.DEFAULT);
        assertEquals(CANONICAL, Files.readString(first, StandardCharsets.UTF_8));
    }

    @Test
    void escapesEveryApprovedAttributeCharacterAndUsesCanonicalNumbersAndEmptyLayers() {
        WorkspaceSourceReference source =
                new WorkspaceSourceReference(
                        "application.source.v1",
                        new SourceIdentity("id", "A&<>\"\t\r\né"),
                        new WorkspaceRelativePath("data/a&<>\"\t\r\né.shp"));
        WorkspaceFeatureLayer feature =
                new WorkspaceFeatureLayer(
                        "f",
                        "N&<>\"\t\r\né",
                        source,
                        new WorkspaceSymbolReferences(
                                "application.default", "p&<>\"é", "line", "fill"));
        WorkspaceDocument document =
                new WorkspaceDocument(
                        new WorkspaceViewState(
                                "EPSG:4326", "EPSG:3857", -0.0, Double.MIN_VALUE, Double.MAX_VALUE),
                        List.of(feature));

        String xml =
                new String(
                        WorkspaceXmlWriter.encode(document, WorkspaceLimits.DEFAULT),
                        StandardCharsets.UTF_8);

        assertTrue(xml.contains("name=\"N&amp;&lt;&gt;&quot;&#9;&#13;&#10;é\""));
        assertTrue(xml.contains("name=\"A&amp;&lt;&gt;&quot;&#9;&#13;&#10;é\""));
        assertTrue(xml.contains("path=\"data/a&amp;&lt;&gt;&quot;&#9;&#13;&#10;é.shp\""));
        assertTrue(xml.contains("center-x=\"0.0\" center-y=\"4.9E-324\""));
        assertTrue(xml.contains("units-per-pixel=\"1.7976931348623157E308\""));
        assertEquals(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <workspace xmlns="urn:mundanej:map:workspace" version="1">
                  <view map-crs="EPSG:4326" display-crs="EPSG:3857"
                        center-x="0.0" center-y="0.0" units-per-pixel="1.0"/>
                  <layers/>
                </workspace>
                """,
                new String(
                        WorkspaceXmlWriter.encode(
                                new WorkspaceDocument(
                                        new WorkspaceViewState("EPSG:4326", "EPSG:3857", 0, 0, 1),
                                        List.of()),
                                WorkspaceLimits.DEFAULT),
                        StandardCharsets.UTF_8));
    }

    @Test
    void enforcesExactOutputAndOperationLimitsBeforeTouchingTheTarget() throws IOException {
        WorkspaceDocument document = canonicalDocument();
        byte[] encoded = WorkspaceXmlWriter.encode(document, WorkspaceLimits.DEFAULT);
        long modelBytes = WorkspaceDocument.logicalModelBytes(document.view(), document.layers());
        long operationBytes = modelBytes + encoded.length;
        Path target = temporary.resolve("bounded.mmap.xml");

        WorkspaceLimits exact =
                WorkspaceLimits.DEFAULT
                        .withInputOutputBytes(encoded.length)
                        .withOperationBytes(operationBytes);
        WorkspaceFiles.write(target, document, exact);
        assertArrayEquals(encoded, Files.readAllBytes(target));

        Files.writeString(target, "preserve", StandardCharsets.UTF_8);
        assertLimit(
                "outputBytes",
                encoded.length,
                encoded.length - 1L,
                target,
                document,
                WorkspaceLimits.DEFAULT.withInputOutputBytes(encoded.length - 1L));
        assertEquals("preserve", Files.readString(target));
        assertNoTemporaryFiles();

        assertLimit(
                "operationBytes",
                operationBytes,
                operationBytes - 1,
                target,
                document,
                WorkspaceLimits.DEFAULT
                        .withInputOutputBytes(encoded.length)
                        .withOperationBytes(operationBytes - 1));
        assertEquals("preserve", Files.readString(target));
        assertNoTemporaryFiles();
    }

    @Test
    void rejectsInvalidTargetsBeforeCreatingTemporaryFiles() throws IOException {
        WorkspaceDocument document = canonicalDocument();
        assertWriteProblem(
                "validate",
                "target",
                temporary.resolve("wrong.xml"),
                document,
                WorkspaceOutputAccess.JDK);
        assertWriteProblem(
                "validate",
                "target",
                temporary.resolve("missing/target.mmap.xml"),
                document,
                WorkspaceOutputAccess.JDK);
        Path directory = temporary.resolve("directory.mmap.xml");
        Files.createDirectory(directory);
        assertWriteProblem("validate", "target", directory, document, WorkspaceOutputAccess.JDK);
        Path regular = temporary.resolve("regular.mmap.xml");
        Files.writeString(regular, "preserve");
        Path link = temporary.resolve("link.mmap.xml");
        Files.createSymbolicLink(link, regular.getFileName());
        assertWriteProblem("validate", "target", link, document, WorkspaceOutputAccess.JDK);
        assertEquals("preserve", Files.readString(regular));
        assertNoTemporaryFiles();
    }

    @Test
    void mapsEveryFileStageAndPreservesPrimaryCleanupOrdering() throws IOException {
        WorkspaceDocument document = canonicalDocument();
        Path target = temporary.resolve("stages.mmap.xml");
        Files.writeString(target, "preserve");

        InstrumentedAccess create = new InstrumentedAccess();
        create.failCreate = true;
        assertWriteProblem("temporary", "io", target, document, create);

        InstrumentedAccess partialCreate = new InstrumentedAccess();
        partialCreate.failCreateCleanup = true;
        WorkspaceException partialFailure =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceFiles.write(
                                        target, document, WorkspaceLimits.DEFAULT, partialCreate));
        assertEquals("temporary", partialFailure.problem().context().get("phase"));
        assertEquals(2, partialFailure.getSuppressed().length);
        assertTrue(
                java.util.Arrays.stream(partialFailure.getSuppressed())
                        .map(WorkspaceException.class::cast)
                        .allMatch(
                                failure ->
                                        failure.problem()
                                                .context()
                                                .get("phase")
                                                .equals("cleanup")));

        InstrumentedAccess write = new InstrumentedAccess();
        write.failWrite = true;
        assertWriteProblem("write", "io", target, document, write);
        assertNoTemporaryFiles();

        InstrumentedAccess force = new InstrumentedAccess();
        force.failForce = true;
        assertWriteProblem("force", "io", target, document, force);
        assertNoTemporaryFiles();

        InstrumentedAccess close = new InstrumentedAccess();
        close.failClose = true;
        assertWriteProblem("write", "io", target, document, close);
        assertNoTemporaryFiles();

        InstrumentedAccess move = new InstrumentedAccess();
        move.failMove = true;
        assertWriteProblem("move", "io", target, document, move);
        assertNoTemporaryFiles();

        InstrumentedAccess atomic = new InstrumentedAccess();
        atomic.unsupportedAtomicMove = true;
        WorkspaceException unsupported =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceFiles.write(
                                        target, document, WorkspaceLimits.DEFAULT, atomic));
        assertEquals("WORKSPACE_ATOMIC_MOVE_UNSUPPORTED", unsupported.problem().code());
        assertEquals(java.util.Map.of(), unsupported.problem().context());
        assertNoTemporaryFiles();

        InstrumentedAccess cleanup = new InstrumentedAccess();
        cleanup.failWrite = true;
        cleanup.failClose = true;
        cleanup.failCleanup = true;
        WorkspaceException combined =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceFiles.write(
                                        target, document, WorkspaceLimits.DEFAULT, cleanup));
        assertEquals("write", combined.problem().context().get("phase"));
        assertEquals(2, combined.getSuppressed().length);
        assertEquals(
                "write",
                ((WorkspaceException) combined.getSuppressed()[0])
                        .problem()
                        .context()
                        .get("phase"));
        assertEquals(
                "cleanup",
                ((WorkspaceException) combined.getSuppressed()[1])
                        .problem()
                        .context()
                        .get("phase"));
        WorkspaceThrowableAssertions.assertOmits(
                combined, "injected", target.toAbsolutePath().toString());
        Files.deleteIfExists(cleanup.createdTemporary);

        assertEquals("preserve", Files.readString(target));
    }

    @Test
    void detectsTemporaryOrTargetReplacementAndForcesMetadataBeforeAtomicMove() throws IOException {
        WorkspaceDocument document = canonicalDocument();
        Path target = temporary.resolve("transaction.mmap.xml");
        Files.writeString(target, "preserve");

        InstrumentedAccess preloaded = new InstrumentedAccess();
        preloaded.preloadTemporary = true;
        assertWriteProblem("move", "changed", target, document, preloaded);
        assertEquals("preserve", Files.readString(target));
        assertNoTemporaryFiles();

        InstrumentedAccess replacedTemporary = new InstrumentedAccess();
        replacedTemporary.replaceTemporaryOnRecheck = true;
        assertWriteProblem("move", "changed", target, document, replacedTemporary);
        assertEquals("preserve", Files.readString(target));
        assertNoTemporaryFiles();

        InstrumentedAccess changed = new InstrumentedAccess();
        changed.mutateTargetOnRecheck = target.toRealPath();
        assertWriteProblem("move", "changed", target, document, changed);
        assertEquals("changed", Files.readString(target));
        assertNoTemporaryFiles();

        Files.writeString(target, "preserve");
        InstrumentedAccess nullIdentity = new InstrumentedAccess();
        nullIdentity.replaceTargetWithNullIdentity = target.toRealPath();
        assertWriteProblem("move", "changed", target, document, nullIdentity);
        assertEquals("preserve", Files.readString(target));
        assertNoTemporaryFiles();

        InstrumentedAccess success = new InstrumentedAccess();
        WorkspaceFiles.write(target, document, WorkspaceLimits.DEFAULT, success);
        assertTrue(success.forced);
        assertTrue(success.forcedMetadata);
        assertTrue(success.movedAfterForce);
        assertEquals(CANONICAL, Files.readString(target));
        assertNoTemporaryFiles();
    }

    private WorkspaceDocument canonicalDocument() {
        WorkspaceFeatureLayer feature =
                new WorkspaceFeatureLayer(
                        "roads",
                        "Roads",
                        source(
                                "application.shapefile.v1",
                                "roads-source",
                                "Road data",
                                "data/roads.shp"),
                        new WorkspaceSymbolReferences(
                                "application.default", "point", "road", "area"));
        WorkspaceRasterLayer raster =
                new WorkspaceRasterLayer(
                        "image",
                        "Image",
                        source(
                                "application.image.v1",
                                "image-source",
                                "Image data",
                                "data/image.png"),
                        RasterInterpolation.BILINEAR,
                        1.0);
        return new WorkspaceDocument(
                new WorkspaceViewState("EPSG:4326", "EPSG:3857", 0, -0.0, 1000),
                List.of(feature, raster));
    }

    private static WorkspaceSourceReference source(
            String opener, String id, String name, String path) {
        return new WorkspaceSourceReference(
                opener, new SourceIdentity(id, name), new WorkspaceRelativePath(path));
    }

    private void assertLimit(
            String limit,
            long requested,
            long maximum,
            Path target,
            WorkspaceDocument document,
            WorkspaceLimits limits) {
        WorkspaceException failure =
                assertThrows(
                        WorkspaceException.class,
                        () -> WorkspaceFiles.write(target, document, limits));
        assertEquals("WORKSPACE_LIMIT_EXCEEDED", failure.problem().code());
        assertEquals(
                List.of("limit", "requested", "maximum"),
                List.copyOf(failure.problem().context().keySet()));
        assertEquals(limit, failure.problem().context().get("limit"));
        assertEquals(Long.toString(requested), failure.problem().context().get("requested"));
        assertEquals(Long.toString(maximum), failure.problem().context().get("maximum"));
    }

    private void assertWriteProblem(
            String phase,
            String reason,
            Path target,
            WorkspaceDocument document,
            WorkspaceOutputAccess access) {
        WorkspaceException failure =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceFiles.write(
                                        target, document, WorkspaceLimits.DEFAULT, access));
        assertEquals("WORKSPACE_WRITE_FAILED", failure.problem().code());
        assertEquals(
                java.util.Map.of("phase", phase, "reason", reason), failure.problem().context());
        WorkspaceThrowableAssertions.assertOmits(failure, target.toString(), "injected");
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (var paths = Files.list(temporary)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith(".mmap-")));
        }
    }

    private static class DelegatingOutputAccess implements WorkspaceOutputAccess {
        @Override
        public Path realParent(Path parent) throws IOException {
            return WorkspaceOutputAccess.JDK.realParent(parent);
        }

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            return WorkspaceOutputAccess.JDK.attributes(path);
        }

        @Override
        public WorkspaceTemporary createTemporary(Path parent) throws IOException {
            return WorkspaceOutputAccess.JDK.createTemporary(parent);
        }

        @Override
        public void moveAtomic(Path temporary, Path target) throws IOException {
            WorkspaceOutputAccess.JDK.moveAtomic(temporary, target);
        }

        @Override
        public void deleteTemporary(Path temporary) throws IOException {
            WorkspaceOutputAccess.JDK.deleteTemporary(temporary);
        }
    }

    private static final class InstrumentedAccess extends DelegatingOutputAccess {
        private boolean failCreate;
        private boolean failCreateCleanup;
        private boolean failWrite;
        private boolean failForce;
        private boolean failClose;
        private boolean failMove;
        private boolean failCleanup;
        private boolean unsupportedAtomicMove;
        private boolean forced;
        private boolean forcedMetadata;
        private boolean movedAfterForce;
        private boolean preloadTemporary;
        private boolean replaceTemporaryOnRecheck;
        private Path mutateTargetOnRecheck;
        private Path replaceTargetWithNullIdentity;
        private Path createdTemporary;
        private int targetAttributeReads;
        private int nullIdentityTargetReads;

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            if (replaceTemporaryOnRecheck && path.equals(createdTemporary)) {
                byte[] bytes = Files.readAllBytes(path);
                FileTime modified = Files.getLastModifiedTime(path);
                Path replacement =
                        Files.createTempFile(
                                java.util.Objects.requireNonNull(path.getParent()),
                                "replacement-",
                                ".tmp");
                Files.write(replacement, bytes);
                Files.setLastModifiedTime(replacement, modified);
                Files.move(replacement, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (mutateTargetOnRecheck != null && path.equals(mutateTargetOnRecheck)) {
                targetAttributeReads++;
                if (targetAttributeReads == 2) {
                    Files.writeString(path, "changed", StandardCharsets.UTF_8);
                }
            }
            if (replaceTargetWithNullIdentity != null
                    && path.equals(replaceTargetWithNullIdentity)) {
                nullIdentityTargetReads++;
                if (nullIdentityTargetReads == 2) {
                    byte[] bytes = Files.readAllBytes(path);
                    FileTime modified = Files.getLastModifiedTime(path);
                    Files.delete(path);
                    Files.write(path, bytes);
                    Files.setLastModifiedTime(path, modified);
                }
                BasicFileAttributes actual = super.attributes(path);
                return new NullFileKeyAttributes(actual);
            }
            return super.attributes(path);
        }

        @Override
        public WorkspaceTemporary createTemporary(Path parent) throws IOException {
            if (failCreate) {
                throw new IOException("injected temporary failure");
            }
            if (failCreateCleanup) {
                IOException failure = new IOException("injected identity-read failure");
                failure.addSuppressed(new IOException("injected close failure"));
                failure.addSuppressed(new IOException("injected delete failure"));
                throw failure;
            }
            WorkspaceTemporary created = super.createTemporary(parent);
            createdTemporary = created.path();
            if (preloadTemporary) {
                Files.write(createdTemporary, new byte[16_384]);
            }
            WorkspaceOutputChannel delegate = created.channel();
            WorkspaceOutputChannel instrumented =
                    new WorkspaceOutputChannel() {
                        @Override
                        public int write(ByteBuffer source) throws IOException {
                            if (failWrite) {
                                throw new IOException("injected write failure");
                            }
                            return delegate.write(source);
                        }

                        @Override
                        public void force(boolean metadata) throws IOException {
                            if (failForce) {
                                throw new IOException("injected force failure");
                            }
                            delegate.force(metadata);
                            forced = true;
                            forcedMetadata = metadata;
                        }

                        @Override
                        public void close() throws IOException {
                            IOException primary = null;
                            try {
                                delegate.close();
                            } catch (IOException failure) {
                                primary = failure;
                            }
                            if (failClose) {
                                IOException injected = new IOException("injected close failure");
                                if (primary != null) {
                                    injected.addSuppressed(primary);
                                }
                                throw injected;
                            }
                            if (primary != null) {
                                throw primary;
                            }
                        }
                    };
            return new WorkspaceTemporary(created.path(), instrumented, created.fileKey());
        }

        @Override
        public void moveAtomic(Path temporary, Path target) throws IOException {
            movedAfterForce = forced;
            if (unsupportedAtomicMove) {
                throw new AtomicMoveNotSupportedException(
                        temporary.toString(), target.toString(), "injected");
            }
            if (failMove) {
                throw new IOException("injected move failure");
            }
            super.moveAtomic(temporary, target);
        }

        @Override
        public void deleteTemporary(Path temporary) throws IOException {
            if (failCleanup) {
                throw new IOException("injected cleanup failure");
            }
            super.deleteTemporary(temporary);
        }
    }

    private record NullFileKeyAttributes(BasicFileAttributes delegate)
            implements BasicFileAttributes {
        @Override
        public FileTime lastModifiedTime() {
            return delegate.lastModifiedTime();
        }

        @Override
        public FileTime lastAccessTime() {
            return delegate.lastAccessTime();
        }

        @Override
        public FileTime creationTime() {
            return delegate.creationTime();
        }

        @Override
        public boolean isRegularFile() {
            return delegate.isRegularFile();
        }

        @Override
        public boolean isDirectory() {
            return delegate.isDirectory();
        }

        @Override
        public boolean isSymbolicLink() {
            return delegate.isSymbolicLink();
        }

        @Override
        public boolean isOther() {
            return delegate.isOther();
        }

        @Override
        public long size() {
            return delegate.size();
        }

        @Override
        public Object fileKey() {
            return null;
        }
    }
}
