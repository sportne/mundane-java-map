package io.github.mundanej.map.io.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileHardeningIntegrationTest {
    private static final SourceIdentity IDENTITY = new SourceIdentity("hardening", "Hardening");

    @TempDir Path temporaryDirectory;

    @Test
    void repeatedZeroProgressReadsBecomeStableIoFailuresAndReleaseTheChannel() {
        FakeAccess access = accessWithPoint();
        access.components.get("shp").zeroProgress = true;

        SourceException failure = assertThrows(SourceException.class, () -> open(access));

        assertEquals("SHAPEFILE_IO_FAILED", failure.terminal().code());
        assertEquals("read", failure.terminal().context().get("operation"));
        assertEquals(1, access.components.get("shp").closeCount);
    }

    @Test
    void raisedSidecarLimitsStillRespectTheFixedLevelOneProfileBeforeAllocation() {
        FakeAccess cpg = accessWithPointAndDbf();
        cpg.components.put("cpg", new FakeChannel(new byte[] {'U'}));
        cpg.components.get("cpg").reportedSize = CpgReader.MAXIMUM_PROFILE_BYTES + 1L;
        ShapefileLimits cpgLimits =
                ShapefileLimits.defaults()
                        .withMaximumCpgBytes(Long.MAX_VALUE)
                        .withMaximumComponentBytes(Long.MAX_VALUE);
        SourceException cpgFailure =
                assertThrows(SourceException.class, () -> open(cpg, cpgLimits));
        assertLimit(cpgFailure, "cpg", "cpgBytes", CpgReader.MAXIMUM_PROFILE_BYTES + 1L);
        assertEquals(1, cpg.components.get("cpg").closeCount);
        assertEquals(1, cpg.components.get("dbf").closeCount);
        assertEquals(1, cpg.components.get("shp").closeCount);

        FakeAccess prj = accessWithPoint();
        prj.components.put("prj", new FakeChannel(new byte[] {'A'}));
        prj.components.get("prj").reportedSize = PrjReader.MAXIMUM_PROFILE_BYTES + 1L;
        ShapefileLimits prjLimits =
                ShapefileLimits.defaults()
                        .withMaximumPrjBytes(Long.MAX_VALUE)
                        .withMaximumComponentBytes(Long.MAX_VALUE);
        SourceException prjFailure =
                assertThrows(SourceException.class, () -> open(prj, prjLimits));
        assertLimit(prjFailure, "prj", "prjBytes", PrjReader.MAXIMUM_PROFILE_BYTES + 1L);
        assertEquals(1, prj.components.get("prj").closeCount);
        assertEquals(1, prj.components.get("shp").closeCount);
    }

    @Test
    void negativeCapturedSidecarSizeIsClassifiedAndDoesNotLeak() {
        FakeAccess access = accessWithPoint();
        access.components.put("prj", new FakeChannel(new byte[] {'A'}));
        access.components.get("prj").reportedSize = -1;

        SourceException failure = assertThrows(SourceException.class, () -> open(access));

        assertEquals("SHAPEFILE_IO_FAILED", failure.terminal().code());
        assertEquals("prj", failure.terminal().location().orElseThrow().component().orElseThrow());
        assertEquals("size", failure.terminal().context().get("operation"));
        assertEquals(1, access.components.get("prj").closeCount);
        assertEquals(1, access.components.get("shp").closeCount);
    }

    @Test
    void fixedHeaderCutsAndEndianMutationHaveExactFirstFailures() throws Exception {
        byte[] point = ShpFixtures.point(1, 2);
        byte[] valid = ShpFixtures.file(1, 1, 2, 1, 2, point);
        int[] cuts = {0, 1, 23, 24, 27, 28, 31, 32, 35, 36, 99};
        for (int cut : cuts) {
            Path path =
                    write("cut-" + cut + ".shp", ShapefileAdversarialFixtures.truncate(valid, cut));
            SourceException failure = assertThrows(SourceException.class, () -> open(path));
            assertEquals("SHAPEFILE_HEADER_INVALID", failure.terminal().code(), "cut " + cut);
            assertEquals(0, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        }

        byte[] headerOnly = ShapefileAdversarialFixtures.truncate(valid, 100);
        SourceException length =
                assertThrows(
                        SourceException.class, () -> open(write("header-only.shp", headerOnly)));
        assertEquals("SHAPEFILE_FILE_LENGTH_MISMATCH", length.terminal().code());
        assertEquals(24, length.terminal().location().orElseThrow().byteOffset().orElseThrow());

        byte[] endian = ShapefileAdversarialFixtures.copy(valid);
        ShapefileAdversarialFixtures.reverseInt(endian, 24);
        SourceException swapped =
                assertThrows(SourceException.class, () -> open(write("endian.shp", endian)));
        assertEquals("SHAPEFILE_FILE_LENGTH_MISMATCH", swapped.terminal().code());
    }

    @Test
    void warningsRetainCrossComponentEncounterOrderBeforeLaterSuccess() throws Exception {
        byte[] point = ShpFixtures.point(1, 2);
        Path shp = write("ordered.shp", ShpFixtures.file(1, 1, 2, 1, 2, point));
        Files.write(temporaryDirectory.resolve("ordered.shx"), new byte[] {0});
        Files.write(
                temporaryDirectory.resolve("ordered.cpg"), new byte[] {'U', 'T', 'F', '-', '8'});
        Files.write(temporaryDirectory.resolve("ordered.prj"), new byte[] {' ', '\n'});

        try (FeatureSource source = open(shp)) {
            assertEquals(
                    List.of(
                            "SHAPEFILE_SHX_IGNORED",
                            "SHAPEFILE_DBF_MISSING",
                            "SHAPEFILE_CPG_WITHOUT_DBF",
                            "SHAPEFILE_PRJ_BLANK"),
                    source.openingDiagnostics().entries().stream()
                            .map(diagnostic -> diagnostic.code())
                            .toList());
        }

        Path clean = write("clean.shp", ShpFixtures.file(1, 1, 2, 1, 2, point));
        try (FeatureSource source = open(clean)) {
            assertEquals(
                    List.of("SHAPEFILE_SHX_MISSING", "SHAPEFILE_DBF_MISSING"),
                    source.openingDiagnostics().entries().stream()
                            .map(diagnostic -> diagnostic.code())
                            .toList());
        }
    }

    private static void assertLimit(
            SourceException failure, String component, String limit, long requested) {
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
        assertEquals(Long.toString(requested), failure.terminal().context().get("requested"));
        assertEquals(
                component, failure.terminal().location().orElseThrow().component().orElseThrow());
    }

    private FeatureSource open(Path path) {
        return Shapefiles.open(IDENTITY, path, ShapefileOpenOptions.defaults());
    }

    private FeatureSource open(FakeAccess access) {
        return open(access, ShapefileLimits.defaults());
    }

    private FeatureSource open(FakeAccess access, ShapefileLimits limits) {
        return Shapefiles.open(
                IDENTITY,
                Path.of("case.shp"),
                ShapefileOpenOptions.defaults().withShapefileLimits(limits),
                CancellationToken.none(),
                access);
    }

    private Path write(String name, byte[] bytes) throws Exception {
        Path path = temporaryDirectory.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private static FakeAccess accessWithPoint() {
        byte[] point = ShpFixtures.point(1, 2);
        byte[] shp = ShpFixtures.file(1, 1, 2, 1, 2, point);
        FakeAccess access = new FakeAccess();
        access.components.put("shp", new FakeChannel(shp));
        return access;
    }

    private static FakeAccess accessWithPointAndDbf() {
        FakeAccess access = accessWithPoint();
        DbfFixtures.Field[] fields = {DbfFixtures.field("name", 'C', 1, 0)};
        access.components.put(
                "dbf",
                new FakeChannel(
                        DbfFixtures.dbf(0x03, 0, fields, DbfFixtures.row(' ', fields, "a"))));
        return access;
    }

    private static final class FakeAccess implements ShapefileFileAccess {
        private final Map<String, FakeChannel> components = new LinkedHashMap<>();

        @Override
        public boolean exists(Path path) {
            String extension = extension(path);
            return extension.equals(extension.toLowerCase(java.util.Locale.ROOT))
                    && components.containsKey(extension);
        }

        @Override
        public boolean isSameFile(Path first, Path second) {
            return false;
        }

        @Override
        public Channel open(Path path) throws IOException {
            FakeChannel channel = components.get(extension(path));
            if (channel == null) {
                throw new IOException("missing fake component");
            }
            return channel;
        }

        private static String extension(Path path) {
            String value = Objects.requireNonNull(path.getFileName()).toString();
            return value.substring(value.lastIndexOf('.') + 1);
        }
    }

    private static final class FakeChannel implements ShapefileFileAccess.Channel {
        private final byte[] bytes;
        private long reportedSize;
        private boolean zeroProgress;
        private int closeCount;

        private FakeChannel(byte[] bytes) {
            this.bytes = bytes.clone();
            reportedSize = bytes.length;
        }

        @Override
        public long size() {
            return reportedSize;
        }

        @Override
        public int read(ByteBuffer target, long position) {
            if (zeroProgress) {
                return 0;
            }
            if (position >= bytes.length) {
                return -1;
            }
            int count = Math.min(target.remaining(), bytes.length - Math.toIntExact(position));
            target.put(bytes, Math.toIntExact(position), count);
            return count;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
