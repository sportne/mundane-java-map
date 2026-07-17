package io.github.mundanej.map.io.dted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DtedFilesTest {
    private static final Map<Integer, Long> EXPECTED_BYTES =
            Map.of(0, 8_762L, 1, 488_642L, 2, 4_339_042L);

    @TempDir Path temporaryDirectory;

    @Test
    void opensIndependentLevelZeroOneAndTwoFixturesWithExactOrientationAndLifecycle()
            throws Exception {
        List<String> hashes = new ArrayList<>();
        for (int level = 0; level <= 2; level++) {
            Path path = temporaryDirectory.resolve("level-" + level + ".bin");
            DtedFixtures.Fixture fixture = DtedFixtures.write(path, level);
            assertEquals(EXPECTED_BYTES.get(level).longValue(), fixture.bytes());
            assertEquals(fixture.bytes(), Files.size(path));
            assertEquals(64, fixture.sha256().length());
            hashes.add(fixture.sha256());

            ElevationSource source =
                    DtedFiles.open(
                            new SourceIdentity("level-" + level, "Level " + level),
                            path,
                            DtedOpenOptions.defaults());
            assertEquals(fixture.columns(), source.metadata().columnCount());
            assertEquals(fixture.rows(), source.metadata().rowCount());
            assertEquals(new Envelope(0, 80, 1, 81), source.metadata().sampleBounds());
            assertEquals(
                    CrsKind.GEOGRAPHIC, source.metadata().crs().definition().orElseThrow().kind());
            assertEquals(
                    "EPSG:4326",
                    source.metadata().crs().definition().orElseThrow().canonicalIdentifier());
            assertEquals(ElevationUnit.METRE, source.metadata().elevationUnit());
            assertTrue(source.openingDiagnostics().entries().isEmpty());
            assertEquals(-456, source.sample(0, 0).orElseThrow());
            assertEquals(0.0, source.sample(1, fixture.rows() - 2).orElseThrow());
            assertEquals(
                    Double.doubleToRawLongBits(0.0),
                    Double.doubleToRawLongBits(source.sample(1, fixture.rows() - 2).orElseThrow()));
            assertEquals(
                    Double.doubleToRawLongBits(0.0),
                    Double.doubleToRawLongBits(
                            source.sample(9, fixture.rows() - 104).orElseThrow()));
            assertEquals(
                    DtedFixtures.value(fixture.columns() - 1, 0, fixture.rows()),
                    source.sample(fixture.columns() - 1, fixture.rows() - 1).orElseThrow());
            assertEquals(
                    DtedFixtures.value(fixture.columns() - 1, fixture.rows() - 2, fixture.rows()),
                    source.sample(fixture.columns() - 1, 1).orElseThrow());
            if (level == 2) {
                assertTrue(source.sample(0, fixture.rows() - 1).isEmpty());
                assertTrue(source.sample(fixture.columns() / 2, fixture.rows() / 2).isEmpty());
                assertTrue(source.sample(fixture.columns() - 1, 0).isEmpty());
            } else {
                assertEquals(123, source.sample(0, fixture.rows() - 1).orElseThrow());
            }
            var metadata = source.metadata();
            source.close();
            source.close();
            assertTrue(source.isClosed());
            assertEquals(metadata, source.metadata());
            assertThrows(IllegalStateException.class, () -> source.sample(0, 0));
        }
        assertEquals(
                List.of(
                        "b7a1323fe7636f767159b19c541325383744582cabdb183415cb6e1a7bff83d7",
                        "8980fef15a479fb0c45fdbe0320c7bed19bd585ce634bf4fbd85d2ebb3c20292",
                        "263b96ef52be130976c90c87c8ad83f757177c10621d5570a71e509df9859a13"),
                hashes);
    }

    @Test
    void generatedRecordsCarryIndependentlyVerifiedUnsignedChecksums() throws Exception {
        for (int level = 0; level <= 2; level++) {
            Path path = temporaryDirectory.resolve("checksum-" + level + ".bin");
            DtedFixtures.Fixture fixture = DtedFixtures.write(path, level);
            byte[] bytes = Files.readAllBytes(path);
            int recordBytes = 12 + 2 * fixture.rows();
            for (int profile : new int[] {0, fixture.columns() - 1}) {
                int start =
                        Math.toIntExact(DtedFixtures.HEADER_BYTES + (long) profile * recordBytes);
                long expected = 0;
                for (int index = start; index < start + recordBytes - 4; index++) {
                    expected += bytes[index] & 0xffL;
                }
                long actual =
                        Integer.toUnsignedLong(
                                ByteBuffer.wrap(bytes, start + recordBytes - 4, 4)
                                        .order(ByteOrder.BIG_ENDIAN)
                                        .getInt());
                assertEquals(expected, actual);
            }
        }
    }

    @Test
    void acceptsEveryLatitudeZoneAtNorthernAndSouthernBoundariesForEveryLevel() {
        int[] origins = {
            -89, -81, -80, -76, -75, -71, -70, -51, -50, -1, 0, 49, 50, 69, 70, 74, 75, 79, 80, 88
        };
        for (int level = 0; level <= 2; level++) {
            for (int origin : origins) {
                SourceException failure =
                        openFailure(DtedFixtures.headers(level, origin, level == 2, false, 0));
                assertEquals(
                        "DTED_FILE_LENGTH_MISMATCH",
                        failure.terminal().code(),
                        "level=" + level + ", origin=" + origin);
            }
        }
    }

    @Test
    void rejectsShiftedOneDegreeCellsAndReconcilesAccuracySubregionFields() {
        byte[] shifted = DtedFixtures.headers(0, 80, false, false, 0);
        put(shifted, 4, "0000001E");
        put(shifted, 80 + 194, "0000001.0E");
        put(shifted, 80 + 211, "0000001E");
        put(shifted, 80 + 226, "0000001E");
        put(shifted, 80 + 241, "0010001E");
        put(shifted, 80 + 256, "0010001E");
        assertEquals("DTED_PROFILE_UNSUPPORTED", openFailure(shifted).terminal().code());

        assertEquals(
                "DTED_PROFILE_UNSUPPORTED",
                openFailure(DtedFixtures.headers(0, 80, false, true, 2)).terminal().code());
        assertEquals(
                "DTED_HEADER_INCONSISTENT",
                openFailure(DtedFixtures.headers(0, 80, false, true, 0)).terminal().code());
        assertEquals(
                "DTED_HEADER_INCONSISTENT",
                openFailure(DtedFixtures.headers(0, 80, false, false, 2)).terminal().code());

        byte[] malformedUhl = DtedFixtures.headers(0, 80, false, false, 0);
        malformedUhl[55] = 'X';
        assertEquals("DTED_UHL_INVALID", openFailure(malformedUhl).terminal().code());
        byte[] malformedAcc = DtedFixtures.headers(0, 80, false, false, 0);
        malformedAcc[728 + 55] = 'X';
        assertEquals("DTED_ACC_INVALID", openFailure(malformedAcc).terminal().code());
        assertEquals(
                "DTED_ACC_INVALID",
                openFailure(DtedFixtures.headers(0, 80, false, true, 1)).terminal().code());
    }

    @Test
    void preflightsLimitsAndCancellationWithoutPublishingPartialState() throws Exception {
        Path path = temporaryDirectory.resolve("limits.dt0");
        DtedFixtures.write(path, 0);
        ElevationSourceLimits limits = new ElevationSourceLimits(20, 121, 10_000, 100_000, 1);
        SourceException limited =
                assertThrows(
                        SourceException.class,
                        () ->
                                DtedFiles.open(
                                        new SourceIdentity("limits", "Limits"),
                                        path,
                                        DtedOpenOptions.defaults()
                                                .withElevationSourceLimits(limits)));
        assertEquals("SOURCE_LIMIT_EXCEEDED", limited.terminal().code());
        assertEquals("columns", limited.terminal().context().get("limit"));

        Path absent = temporaryDirectory.resolve("absent.dt0");
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                DtedFiles.open(
                                        new SourceIdentity("cancel", "Cancel"),
                                        absent,
                                        DtedOpenOptions.defaults(),
                                        () -> true));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertFalse(Files.exists(absent));
    }

    @Test
    void cancellationCoversMidReadPreCopyAndPostCopyBoundaries() throws Exception {
        Path path = temporaryDirectory.resolve("cancel.dt0");
        DtedFixtures.write(path, 0);
        byte[] bytes = Files.readAllBytes(path);
        SourceIdentity identity = new SourceIdentity("cancel-stages", "Cancel stages");

        CountingToken completedToken = new CountingToken(Integer.MAX_VALUE);
        TrackingAccess completedAccess = new TrackingAccess(bytes);
        ElevationSource completed =
                DtedReader.open(
                        identity, DtedOpenOptions.defaults(), completedToken, completedAccess);
        int finalPoll = completedToken.polls.get();
        assertTrue(completedAccess.closed);
        completed.close();

        for (int poll : new int[] {20, finalPoll - 1, finalPoll}) {
            TrackingAccess access = new TrackingAccess(bytes);
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    DtedReader.open(
                                            identity,
                                            DtedOpenOptions.defaults(),
                                            new CountingToken(poll),
                                            access));
            assertEquals("SOURCE_CANCELLED", failure.terminal().code());
            assertTrue(access.closed);
        }

        AtomicBoolean cancelAfterClose = new AtomicBoolean();
        TrackingAccess closeBoundary = new TrackingAccess(bytes, () -> cancelAfterClose.set(true));
        SourceException closeBoundaryFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                DtedReader.open(
                                        identity,
                                        DtedOpenOptions.defaults(),
                                        cancelAfterClose::get,
                                        closeBoundary));
        assertEquals("SOURCE_CANCELLED", closeBoundaryFailure.terminal().code());
        assertEquals(1, closeBoundary.closeCalls);
    }

    @Test
    void readAndCloseFailuresKeepPrimaryAndSuppressedOrdering() throws Exception {
        Path path = temporaryDirectory.resolve("failures.dt0");
        DtedFixtures.write(path, 0);
        byte[] bytes = Files.readAllBytes(path);
        SourceIdentity identity = new SourceIdentity("failures", "Failures");

        TrackingAccess cleanClose = new TrackingAccess(bytes);
        cleanClose.failClose = true;
        SourceException closeFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                DtedReader.open(
                                        identity,
                                        DtedOpenOptions.defaults(),
                                        CancellationToken.none(),
                                        cleanClose));
        assertEquals("DTED_IO_FAILED", closeFailure.terminal().code());
        assertEquals("close", closeFailure.terminal().context().get("operation"));
        assertEquals(1, cleanClose.closeCalls);

        TrackingAccess readAndClose = new TrackingAccess(bytes);
        readAndClose.failRead = true;
        readAndClose.failClose = true;
        SourceException readFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                DtedReader.open(
                                        identity,
                                        DtedOpenOptions.defaults(),
                                        CancellationToken.none(),
                                        readAndClose));
        assertEquals("read", readFailure.terminal().context().get("operation"));
        assertEquals(1, readFailure.getSuppressed().length);
        SourceException suppressed = (SourceException) readFailure.getSuppressed()[0];
        assertEquals("close", suppressed.terminal().context().get("operation"));
        assertEquals(1, readAndClose.closeCalls);
    }

    @Test
    void initialHeaderProfileLengthAndRecordDiagnosticsAreStableAndPathFree() throws Exception {
        Path path = temporaryDirectory.resolve("diagnostics.dt0");
        DtedFixtures.write(path, 0);
        byte[] valid = Files.readAllBytes(path);
        assertCode(valid, 0, (byte) 'X', "DTED_UHL_INVALID");
        assertCode(valid, 80, (byte) 'X', "DTED_DSI_INVALID");
        assertCode(valid, 728, (byte) 'X', "DTED_ACC_INVALID");
        assertCode(valid, 143, (byte) '9', "DTED_PROFILE_UNSUPPORTED");
        byte[] longer = java.util.Arrays.copyOf(valid, valid.length + 1);
        SourceException length = openFailure(longer);
        assertEquals("DTED_FILE_LENGTH_MISMATCH", length.terminal().code());
        byte[] record = valid.clone();
        record[3_428] = 0;
        SourceException data = openFailure(record);
        assertEquals("DTED_DATA_RECORD_INVALID", data.terminal().code());
        assertEquals(1, data.terminal().location().orElseThrow().recordNumber().orElseThrow());
        assertEquals(3_428, data.terminal().location().orElseThrow().byteOffset().orElseThrow());

        assertRecordField(valid, 3_428, (byte) 0, "sentinel", null, null);
        assertRecordField(valid, 3_429, (byte) 1, "blockCount", "65536", "0");
        assertRecordField(valid, 3_432, (byte) 1, "longitudeCount", "256", "0");
        assertRecordField(valid, 3_434, (byte) 1, "latitudeCount", "256", "0");

        byte[] voidInComplete = valid.clone();
        voidInComplete[3_436] = (byte) 0xff;
        voidInComplete[3_437] = (byte) 0xff;
        SourceException voidFailure = openFailure(voidInComplete);
        assertEquals("DTED_DATA_RECORD_INVALID", voidFailure.terminal().code());
        assertEquals("sample", voidFailure.terminal().context().get("field"));
        assertEquals("voidInComplete", voidFailure.terminal().context().get("reason"));
    }

    @Test
    void enforcesLevelSpecificPartialAndVoidPolicy() throws Exception {
        assertEquals(
                "DTED_PROFILE_UNSUPPORTED",
                openFailure(DtedFixtures.headers(0, 80, true, false, 0)).terminal().code());
        assertEquals(
                "DTED_PROFILE_UNSUPPORTED",
                openFailure(DtedFixtures.headers(1, 80, true, false, 0)).terminal().code());

        Path path = temporaryDirectory.resolve("complete-level-2.dt2");
        DtedFixtures.write(path, 2);
        byte[] declaredComplete = Files.readAllBytes(path);
        declaredComplete[80 + 289] = '0';
        declaredComplete[80 + 290] = '0';
        SourceException voidFailure = openFailure(declaredComplete);
        assertEquals("DTED_DATA_RECORD_INVALID", voidFailure.terminal().code());
        assertEquals("sample", voidFailure.terminal().context().get("field"));
        assertEquals("voidInComplete", voidFailure.terminal().context().get("reason"));
    }

    private static void assertRecordField(
            byte[] valid,
            int offset,
            byte replacement,
            String expectedField,
            String expectedActual,
            String expectedExpected) {
        byte[] changed = valid.clone();
        changed[offset] = replacement;
        SourceException failure = openFailure(changed);
        assertEquals("DTED_DATA_RECORD_INVALID", failure.terminal().code());
        assertEquals(expectedField, failure.terminal().context().get("field"));
        assertEquals("mismatch", failure.terminal().context().get("reason"));
        assertEquals(expectedActual, failure.terminal().context().get("actual"));
        assertEquals(expectedExpected, failure.terminal().context().get("expected"));
        assertEquals(1, failure.terminal().location().orElseThrow().recordNumber().orElseThrow());
    }

    private static void put(byte[] target, int offset, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }

    private static void assertCode(byte[] valid, int offset, byte replacement, String code) {
        byte[] changed = valid.clone();
        changed[offset] = replacement;
        SourceException failure = openFailure(changed);
        assertEquals(code, failure.terminal().code());
        assertEquals("diagnostics", failure.terminal().sourceId());
        assertFalse(failure.terminal().context().toString().contains("diagnostics.dt0"));
    }

    private static SourceException openFailure(byte[] bytes) {
        return assertThrows(
                SourceException.class,
                () ->
                        DtedReader.open(
                                new SourceIdentity("diagnostics", "Diagnostics"),
                                DtedOpenOptions.defaults(),
                                CancellationToken.none(),
                                new TrackingAccess(bytes)));
    }

    private static final class CountingToken implements CancellationToken {
        private final int cancelAt;
        private final AtomicInteger polls = new AtomicInteger();

        private CountingToken(int cancelAt) {
            this.cancelAt = cancelAt;
        }

        @Override
        public boolean isCancellationRequested() {
            return polls.incrementAndGet() >= cancelAt;
        }
    }

    private static final class TrackingAccess implements DtedFileAccess {
        private final byte[] bytes;
        private final Runnable onClose;
        private boolean closed;
        private int closeCalls;
        private boolean failRead;
        private boolean failClose;

        private TrackingAccess(byte[] bytes) {
            this(bytes, () -> {});
        }

        private TrackingAccess(byte[] bytes, Runnable onClose) {
            this.bytes = bytes;
            this.onClose = onClose;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public int read(ByteBuffer destination, long position) throws IOException {
            if (failRead) {
                throw new IOException("read");
            }
            if (position >= bytes.length) {
                return -1;
            }
            int count = Math.min(destination.remaining(), bytes.length - Math.toIntExact(position));
            destination.put(bytes, Math.toIntExact(position), count);
            return count;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            closeCalls++;
            onClose.run();
            if (failClose) {
                throw new IOException("close");
            }
        }
    }
}
