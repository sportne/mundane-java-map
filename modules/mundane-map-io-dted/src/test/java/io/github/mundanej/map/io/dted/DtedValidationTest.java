package io.github.mundanej.map.io.dted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class DtedValidationTest {
    private static final long MUTATION_SEED = 0x4454454447393034L;

    @TempDir Path temporaryDirectory;

    @Test
    void validatesEveryFixedHeaderFieldClassInPhysicalSections() {
        byte[] valid = DtedFixtures.headers(0, 80, false, false, 0);
        List<HeaderCase> cases =
                List.of(
                        new HeaderCase(
                                28,
                                (byte) 'X',
                                "DTED_UHL_INVALID",
                                "absoluteVerticalAccuracy",
                                "grammar"),
                        new HeaderCase(
                                32, (byte) 'X', "DTED_UHL_INVALID", "securityCode", "grammar"),
                        new HeaderCase(
                                35, (byte) 1, "DTED_UHL_INVALID", "uniqueReference", "grammar"),
                        new HeaderCase(56, (byte) 'X', "DTED_UHL_INVALID", "reserved", "reserved"),
                        new HeaderCase(
                                83,
                                (byte) 'X',
                                "DTED_DSI_INVALID",
                                "securityClassification",
                                "grammar"),
                        new HeaderCase(
                                84,
                                (byte) 1,
                                "DTED_DSI_INVALID",
                                "securityControlRelease",
                                "grammar"),
                        new HeaderCase(113, (byte) 'X', "DTED_DSI_INVALID", "reserved", "reserved"),
                        new HeaderCase(168, (byte) '0', "DTED_DSI_INVALID", "edition", "range"),
                        new HeaderCase(
                                169,
                                (byte) '1',
                                "DTED_DSI_INVALID",
                                "matchMergeVersion",
                                "grammar"),
                        new HeaderCase(
                                172, (byte) '2', "DTED_DSI_INVALID", "maintenanceDate", "range"),
                        new HeaderCase(
                                178,
                                (byte) 'x',
                                "DTED_DSI_INVALID",
                                "maintenanceDescription",
                                "grammar"),
                        new HeaderCase(
                                182, (byte) 'x', "DTED_DSI_INVALID", "producerCode", "grammar"),
                        new HeaderCase(
                                206,
                                (byte) 'x',
                                "DTED_DSI_INVALID",
                                "productSpecification",
                                "grammar"),
                        new HeaderCase(
                                215, (byte) 'X', "DTED_DSI_INVALID", "amendmentChange", "grammar"),
                        new HeaderCase(
                                221, (byte) 'm', "DTED_DSI_INVALID", "verticalDatum", "grammar"),
                        new HeaderCase(
                                229, (byte) 1, "DTED_DSI_INVALID", "collectionSystem", "grammar"),
                        new HeaderCase(371, (byte) 1, "DTED_DSI_INVALID", "nimaUse", "grammar"),
                        new HeaderCase(
                                472, (byte) 1, "DTED_DSI_INVALID", "producingNationUse", "grammar"),
                        new HeaderCase(572, (byte) 1, "DTED_DSI_INVALID", "comments", "grammar"),
                        new HeaderCase(
                                731,
                                (byte) 'X',
                                "DTED_ACC_INVALID",
                                "absoluteHorizontalAccuracy",
                                "grammar"),
                        new HeaderCase(747, (byte) 'X', "DTED_ACC_INVALID", "reserved", "reserved"),
                        new HeaderCase(
                                752, (byte) 'Y', "DTED_ACC_INVALID", "srtmMarker", "grammar"),
                        new HeaderCase(753, (byte) 'X', "DTED_ACC_INVALID", "reserved", "reserved"),
                        new HeaderCase(
                                785,
                                (byte) 'X',
                                "DTED_ACC_INVALID",
                                "subregionData",
                                "unexpectedSubregionData"));
        cases =
                java.util.stream.Stream.concat(
                                cases.stream(),
                                List.of(
                                        new HeaderCase(
                                                0,
                                                (byte) 'X',
                                                "DTED_UHL_INVALID",
                                                "sentinel",
                                                "literal"),
                                        new HeaderCase(
                                                4,
                                                (byte) 'X',
                                                "DTED_UHL_INVALID",
                                                "longitudeOrigin",
                                                "grammar"),
                                        new HeaderCase(
                                                12,
                                                (byte) 'X',
                                                "DTED_UHL_INVALID",
                                                "latitudeOrigin",
                                                "grammar"),
                                        new HeaderCase(
                                                20,
                                                (byte) 'X',
                                                "DTED_UHL_INVALID",
                                                "longitudeInterval",
                                                "grammar"),
                                        new HeaderCase(
                                                24,
                                                (byte) 'X',
                                                "DTED_UHL_INVALID",
                                                "latitudeInterval",
                                                "grammar"),
                                        new HeaderCase(
                                                47,
                                                (byte) 'X',
                                                "DTED_UHL_INVALID",
                                                "longitudeCount",
                                                "grammar"),
                                        new HeaderCase(
                                                51,
                                                (byte) 'X',
                                                "DTED_UHL_INVALID",
                                                "latitudeCount",
                                                "grammar"),
                                        new HeaderCase(
                                                55,
                                                (byte) 'X',
                                                "DTED_UHL_INVALID",
                                                "multipleAccuracy",
                                                "grammar"),
                                        new HeaderCase(
                                                80,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "sentinel",
                                                "literal"),
                                        new HeaderCase(
                                                86,
                                                (byte) 1,
                                                "DTED_DSI_INVALID",
                                                "securityHandling",
                                                "grammar"),
                                        new HeaderCase(
                                                139,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "series",
                                                "literal"),
                                        new HeaderCase(
                                                143,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "series",
                                                "grammar"),
                                        new HeaderCase(
                                                144,
                                                (byte) 1,
                                                "DTED_DSI_INVALID",
                                                "uniqueReference",
                                                "grammar"),
                                        new HeaderCase(
                                                159,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "reserved",
                                                "reserved"),
                                        new HeaderCase(
                                                190,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "reserved",
                                                "reserved"),
                                        new HeaderCase(
                                                174,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "matchMergeDate",
                                                "grammar"),
                                        new HeaderCase(
                                                217,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "specificationDate",
                                                "grammar"),
                                        new HeaderCase(
                                                224,
                                                (byte) 'w',
                                                "DTED_DSI_INVALID",
                                                "horizontalDatum",
                                                "grammar"),
                                        new HeaderCase(
                                                239,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "compilationDate",
                                                "grammar"),
                                        new HeaderCase(
                                                243,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "reserved",
                                                "reserved"),
                                        new HeaderCase(
                                                265,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "origin",
                                                "grammar"),
                                        new HeaderCase(
                                                284,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "southWestCorner",
                                                "grammar"),
                                        new HeaderCase(
                                                299,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "northWestCorner",
                                                "grammar"),
                                        new HeaderCase(
                                                314,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "northEastCorner",
                                                "grammar"),
                                        new HeaderCase(
                                                329,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "southEastCorner",
                                                "grammar"),
                                        new HeaderCase(
                                                351,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "orientation",
                                                "grammar"),
                                        new HeaderCase(
                                                353,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "latitudeInterval",
                                                "grammar"),
                                        new HeaderCase(
                                                357,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "longitudeInterval",
                                                "grammar"),
                                        new HeaderCase(
                                                361,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "latitudeCount",
                                                "grammar"),
                                        new HeaderCase(
                                                365,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "longitudeCount",
                                                "grammar"),
                                        new HeaderCase(
                                                369,
                                                (byte) 'X',
                                                "DTED_DSI_INVALID",
                                                "partialCell",
                                                "grammar"),
                                        new HeaderCase(
                                                728,
                                                (byte) 'X',
                                                "DTED_ACC_INVALID",
                                                "sentinel",
                                                "literal"),
                                        new HeaderCase(
                                                735,
                                                (byte) 'X',
                                                "DTED_ACC_INVALID",
                                                "absoluteVerticalAccuracy",
                                                "grammar"),
                                        new HeaderCase(
                                                739,
                                                (byte) 'X',
                                                "DTED_ACC_INVALID",
                                                "relativeHorizontalAccuracy",
                                                "grammar"),
                                        new HeaderCase(
                                                743,
                                                (byte) 'X',
                                                "DTED_ACC_INVALID",
                                                "relativeVerticalAccuracy",
                                                "grammar"),
                                        new HeaderCase(
                                                783,
                                                (byte) 'X',
                                                "DTED_ACC_INVALID",
                                                "multipleAccuracy",
                                                "grammar"))
                                        .stream())
                        .toList();
        for (HeaderCase testCase : cases) {
            byte[] changed = valid.clone();
            changed[testCase.offset] = testCase.replacement;
            SourceException failure = failure(changed);
            String component =
                    testCase.code.equals("DTED_UHL_INVALID")
                            ? "uhl"
                            : testCase.code.equals("DTED_DSI_INVALID") ? "dsi" : "acc";
            assertDiagnostic(
                    failure,
                    testCase.code,
                    component,
                    null,
                    testCase.fieldStart(),
                    Map.of("field", testCase.field, "reason", testCase.reason));
        }
    }

    @Test
    void distinguishesUnsupportedProfilesAndDuplicateDeclarations() {
        byte[] unsupportedLevel = DtedFixtures.headers(0, 80, false, false, 0);
        unsupportedLevel[143] = '9';
        assertProfile(unsupportedLevel, "level");

        byte[] unsupportedProduct = DtedFixtures.headers(0, 80, false, false, 0);
        unsupportedProduct[206] = 'A';
        assertProfile(unsupportedProduct, "productSpecification");

        byte[] unsupportedDatum = DtedFixtures.headers(0, 80, false, false, 0);
        unsupportedDatum[221] = 'E';
        assertProfile(unsupportedDatum, "datum");

        byte[] unsupportedOrientation = DtedFixtures.headers(0, 80, false, false, 0);
        unsupportedOrientation[352] = '1';
        assertProfile(unsupportedOrientation, "orientation");

        byte[] unsupportedGrid = DtedFixtures.headers(0, 80, false, false, 0);
        put(unsupportedGrid, 80 + 204, "790000N");
        assertProfile(unsupportedGrid, "grid");

        byte[] unsupportedPartial = DtedFixtures.headers(0, 80, false, false, 0);
        put(unsupportedPartial, 80 + 289, "25");
        assertProfile(unsupportedPartial, "partialCell");

        byte[] unsupportedAccuracy = DtedFixtures.headers(0, 80, false, true, 2);
        unsupportedAccuracy[785] = 'X';
        assertProfile(unsupportedAccuracy, "accuracySubregions");

        byte[] originMismatch = DtedFixtures.headers(0, 80, false, false, 0);
        put(originMismatch, 80 + 185, "790000.0N");
        assertMismatch(originMismatch, "origin", "uhl", "dsi");

        byte[] intervalMismatch = DtedFixtures.headers(0, 80, false, false, 0);
        put(intervalMismatch, 80 + 273, "1300");
        assertMismatch(intervalMismatch, "interval", "uhl", "dsi");

        byte[] rowMismatch = DtedFixtures.headers(0, 80, false, false, 0);
        put(rowMismatch, 80 + 281, "1121");
        assertMismatch(rowMismatch, "rows", "uhl", "dsi");

        byte[] columnMismatch = DtedFixtures.headers(0, 80, false, false, 0);
        put(columnMismatch, 80 + 285, "1021");
        assertMismatch(columnMismatch, "columns", "uhl", "dsi");

        byte[] accuracyMismatch = DtedFixtures.headers(0, 80, false, true, 0);
        assertMismatch(accuracyMismatch, "accuracyOutline", "uhl", "acc");

        byte[] producerMismatch = DtedFixtures.headers(0, 80, false, false, 0);
        producerMismatch[752] = 'X';
        assertMismatch(producerMismatch, "producerProfile", "dsi", "acc");

        byte[] inverseProducerMismatch = DtedFixtures.headers(0, 80, false, false, 0);
        put(inverseProducerMismatch, 80 + 149, "SRTM      ");
        assertMismatch(inverseProducerMismatch, "producerProfile", "dsi", "acc");

        byte[] srtm = DtedFixtures.headers(0, 80, false, false, 0);
        put(srtm, 80 + 149, "SRTM      ");
        srtm[752] = 'X';
        assertEquals("DTED_FILE_LENGTH_MISMATCH", failure(srtm).terminal().code());
    }

    @Test
    void formatLimitsAcceptEqualityRejectPlusOneAndPrecedeElevationLimits() throws Exception {
        Path path = temporaryDirectory.resolve("limits.dt0");
        DtedFixtures.Fixture fixture = DtedFixtures.write(path, 0);
        long total = (long) fixture.columns() * fixture.rows();
        int profileBytes = 12 + 2 * fixture.rows();
        long words = 1L + (total - 1L) / 64L;
        long parserBytes = 2_700L + profileBytes + 2L * (8L * total + 8L * words);

        DtedLimits exact =
                DtedLimits.defaults()
                        .withMaximumFileBytes(fixture.bytes())
                        .withMaximumProfiles(fixture.columns())
                        .withMaximumSamplesPerProfile(fixture.rows())
                        .withMaximumTotalSamples(total)
                        .withMaximumProfileBytes(profileBytes)
                        .withMaximumParserAllocationBytes(parserBytes);
        try (ElevationSource source =
                DtedFiles.open(
                        new SourceIdentity("limit-equality", "Limit equality"),
                        path,
                        DtedOpenOptions.defaults().withDtedLimits(exact))) {
            assertEquals(fixture.columns(), source.metadata().columnCount());
        }

        assertLimit(
                path,
                exact.withMaximumFileBytes(fixture.bytes() - 1),
                "fileBytes",
                fixture.bytes(),
                fixture.bytes() - 1);
        assertLimit(
                path,
                exact.withMaximumProfiles(fixture.columns() - 1),
                "profiles",
                fixture.columns(),
                fixture.columns() - 1L);
        assertLimit(
                path,
                exact.withMaximumSamplesPerProfile(fixture.rows() - 1),
                "samplesPerProfile",
                fixture.rows(),
                fixture.rows() - 1L);
        assertLimit(
                path, exact.withMaximumTotalSamples(total - 1), "totalSamples", total, total - 1L);
        assertLimit(
                path,
                exact.withMaximumProfileBytes(profileBytes - 1),
                "profileBytes",
                profileBytes,
                profileBytes - 1L);
        assertLimit(
                path,
                exact.withMaximumParserAllocationBytes(parserBytes - 1),
                "parserAllocationBytes",
                parserBytes,
                parserBytes - 1L);

        ElevationSourceLimits elevation = new ElevationSourceLimits(20, 120, 1, 1, 1);
        SourceException precedence =
                assertThrows(
                        SourceException.class,
                        () ->
                                DtedFiles.open(
                                        new SourceIdentity("limit-order", "Limit order"),
                                        path,
                                        DtedOpenOptions.defaults()
                                                .withDtedLimits(
                                                        exact.withMaximumProfiles(
                                                                fixture.columns() - 1))
                                                .withElevationSourceLimits(elevation)));
        assertDiagnosticShape(
                precedence,
                "SOURCE_LIMIT_EXCEEDED",
                "limit-order",
                null,
                null,
                null,
                Map.of(
                        "limit",
                        "profiles",
                        "maximum",
                        Long.toString(fixture.columns() - 1L),
                        "requested",
                        Long.toString(fixture.columns()),
                        "scope",
                        "dtedOpen"),
                "Elevation source limit exceeded");

        byte[] bytes = Files.readAllBytes(path);
        MutableAccess beforeScratch = new MutableAccess(bytes, bytes.length, bytes.length);
        SourceException scratchFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                DtedReader.open(
                                        new SourceIdentity("scratch-limit", "Scratch limit"),
                                        DtedOpenOptions.defaults()
                                                .withDtedLimits(
                                                        exact.withMaximumParserAllocationBytes(
                                                                2_699)),
                                        CancellationToken.none(),
                                        beforeScratch));
        assertEquals("parserAllocationBytes", scratchFailure.terminal().context().get("limit"));
        assertEquals(0, beforeScratch.readCalls);

        MutableAccess beforeProfile = new MutableAccess(bytes, bytes.length, bytes.length);
        SourceException profileFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                DtedReader.open(
                                        new SourceIdentity("profile-limit", "Profile limit"),
                                        DtedOpenOptions.defaults()
                                                .withDtedLimits(
                                                        exact.withMaximumParserAllocationBytes(
                                                                2_700)),
                                        CancellationToken.none(),
                                        beforeProfile));
        assertEquals("parserAllocationBytes", profileFailure.terminal().context().get("limit"));
        assertEquals(3, beforeProfile.readCalls);
        assertEquals(1, beforeProfile.closeCalls);
    }

    @Test
    void checkedArithmeticSaturatesBeforeLimitComparison() {
        assertEquals(42, DtedReader.checkedAdd(20, 22));
        assertEquals(42, DtedReader.checkedMultiply(6, 7));
        assertEquals(Long.MAX_VALUE, DtedReader.checkedAdd(Long.MAX_VALUE, 1));
        assertEquals(Long.MAX_VALUE, DtedReader.checkedMultiply(Long.MAX_VALUE, 2));
    }

    @Test
    void recordPrecedenceChecksumRangesAndVoidDiagnosticsAreExact() throws Exception {
        Path path = temporaryDirectory.resolve("records.dt0");
        DtedFixtures.write(path, 0);
        byte[] valid = Files.readAllBytes(path);
        int checksumOffset = DtedFixtures.HEADER_BYTES + 12 + 2 * 121 - 4;

        byte[] checksum = valid.clone();
        checksum[checksumOffset + 3] ^= 1;
        SourceException checksumFailure = failure(checksum);
        assertDiagnostic(
                checksumFailure,
                "DTED_CHECKSUM_MISMATCH",
                "data",
                1L,
                (long) checksumOffset,
                Map.of("actual", "31835", "expected", "31834"));

        byte[] preambleWins = checksum.clone();
        preambleWins[DtedFixtures.HEADER_BYTES] = 0;
        assertDiagnostic(
                failure(preambleWins),
                "DTED_DATA_RECORD_INVALID",
                "data",
                1L,
                (long) DtedFixtures.HEADER_BYTES,
                Map.of("field", "sentinel", "reason", "literal"));

        assertRange(valid, 9_001, false, "positive", "9001");
        assertRange(valid, 12_001, true, "negative", "12001");
        assertAcceptedRange(valid, 9_000, false, 9_000.0);
        assertAcceptedRange(valid, 12_000, true, -12_000.0);

        byte[] voidInComplete = valid.clone();
        putWord(voidInComplete, 0xffff);
        repairFirstChecksum(voidInComplete, 121);
        SourceException voidFailure = failure(voidInComplete);
        assertDiagnostic(
                voidFailure,
                "DTED_DATA_RECORD_INVALID",
                "data",
                1L,
                (long) DtedFixtures.HEADER_BYTES + 8L,
                Map.of("field", "sample", "reason", "voidInComplete", "sampleIndex", "0"));
    }

    @Test
    void partialLevelTwoMayContainNoVoidSamples() throws Exception {
        Path path = temporaryDirectory.resolve("partial-without-voids.dt2");
        DtedFixtures.Fixture fixture = DtedFixtures.write(path, 2);
        byte[] bytes = Files.readAllBytes(path);
        int[][] voids = {
            {0, 0},
            {fixture.columns() / 2, fixture.rows() / 2},
            {fixture.columns() - 1, fixture.rows() - 1}
        };
        for (int[] position : voids) {
            putWord(bytes, fixture.rows(), position[0], position[1], 0);
            repairChecksum(bytes, fixture.rows(), position[0]);
        }
        try (ElevationSource source =
                DtedReader.open(
                        new SourceIdentity("partial-no-void", "Partial no void"),
                        DtedOpenOptions.defaults(),
                        CancellationToken.none(),
                        new MutableAccess(bytes, bytes.length, bytes.length))) {
            assertTrue(source.sample(0, fixture.rows() - 1).isPresent());
            assertTrue(source.sample(fixture.columns() / 2, fixture.rows() / 2).isPresent());
            assertTrue(source.sample(fixture.columns() - 1, 0).isPresent());
        }
    }

    @Test
    void sectionAndRecordTruncationAndFinalSizeRaceHaveStableOwnership() throws Exception {
        byte[] headers = DtedFixtures.headers(0, 80, false, false, 0);
        for (int length : new int[] {0, 1, 79, 80, 81, 727, 728, 729, 3_427}) {
            SourceException failure = failure(Arrays.copyOf(headers, length));
            String expected =
                    length < 80
                            ? "DTED_UHL_INVALID"
                            : length < 728 ? "DTED_DSI_INVALID" : "DTED_ACC_INVALID";
            String component = length < 80 ? "uhl" : length < 728 ? "dsi" : "acc";
            int sectionStart = length < 80 ? 0 : length < 728 ? 80 : 728;
            int sectionBytes = length < 80 ? 80 : length < 728 ? 648 : 2_700;
            assertDiagnostic(
                    failure,
                    expected,
                    component,
                    null,
                    (long) sectionStart,
                    Map.of(
                            "actualBytes", Integer.toString(length - sectionStart),
                            "expectedBytes", Integer.toString(sectionBytes),
                            "reason", "truncated"));
        }
        int[] fieldBoundaries = {
            0, 4, 12, 20, 24, 28, 32, 35, 47, 51, 55, 56, 79, 80, 83, 84, 86, 113, 139, 144, 159,
            167, 169, 170, 174, 178, 182, 190, 206, 215, 217, 221, 224, 229, 239, 243, 265, 284,
            299, 314, 329, 344, 353, 357, 361, 365, 369, 371, 472, 572, 727, 728, 731, 735, 739,
            743, 747, 752, 753, 783, 785, 3_427
        };
        for (int length : fieldBoundaries) {
            SourceException failure = failure(Arrays.copyOf(headers, length));
            String code =
                    length < 80
                            ? "DTED_UHL_INVALID"
                            : length < 728 ? "DTED_DSI_INVALID" : "DTED_ACC_INVALID";
            int sectionStart = length < 80 ? 0 : length < 728 ? 80 : 728;
            int sectionBytes = length < 80 ? 80 : length < 728 ? 648 : 2_700;
            String component = length < 80 ? "uhl" : length < 728 ? "dsi" : "acc";
            assertDiagnostic(
                    failure,
                    code,
                    component,
                    null,
                    (long) sectionStart,
                    Map.of(
                            "actualBytes", Integer.toString(length - sectionStart),
                            "expectedBytes", Integer.toString(sectionBytes),
                            "reason", "truncated"));
        }

        Path path = temporaryDirectory.resolve("truncated-record.dt0");
        DtedFixtures.write(path, 0);
        byte[] full = Files.readAllBytes(path);
        for (int available : new int[] {0, 1, 7, 8, 9, 250, 253}) {
            byte[] prefix = Arrays.copyOf(full, DtedFixtures.HEADER_BYTES + available);
            MutableAccess access = new MutableAccess(prefix, full.length, full.length);
            SourceException failure = directFailure(access);
            assertDiagnostic(
                    failure,
                    "DTED_DATA_RECORD_INVALID",
                    "data",
                    1L,
                    (long) DtedFixtures.HEADER_BYTES + available,
                    Map.of(
                            "actualBytes", Integer.toString(available),
                            "expectedBytes", "254",
                            "field", "frame",
                            "reason", "truncated"));
            assertEquals(1, access.closeCalls);
        }

        int recordBytes = 12 + 2 * 121;
        for (int profile = 0; profile < 21; profile++) {
            int available = profile * recordBytes;
            byte[] prefix = Arrays.copyOf(full, DtedFixtures.HEADER_BYTES + available);
            MutableAccess access = new MutableAccess(prefix, full.length, full.length);
            SourceException failure = directFailure(access);
            assertDiagnostic(
                    failure,
                    "DTED_DATA_RECORD_INVALID",
                    "data",
                    profile + 1L,
                    (long) DtedFixtures.HEADER_BYTES + available,
                    Map.of(
                            "actualBytes", "0",
                            "expectedBytes", "254",
                            "field", "frame",
                            "reason", "truncated"));
        }
        for (int available : new int[] {9, 130, 249, 250, 251, 253}) {
            byte[] prefix = Arrays.copyOf(full, DtedFixtures.HEADER_BYTES + available);
            SourceException failure =
                    directFailure(new MutableAccess(prefix, full.length, full.length));
            assertDiagnostic(
                    failure,
                    "DTED_DATA_RECORD_INVALID",
                    "data",
                    1L,
                    (long) DtedFixtures.HEADER_BYTES + available,
                    Map.of(
                            "actualBytes", Integer.toString(available),
                            "expectedBytes", "254",
                            "field", "frame",
                            "reason", "truncated"));
        }

        MutableAccess changedSize = new MutableAccess(full, full.length, full.length + 1L);
        SourceException race = directFailure(changedSize);
        assertDiagnostic(
                race,
                "DTED_FILE_LENGTH_MISMATCH",
                "dted",
                null,
                null,
                Map.of(
                        "actualBytes", Long.toString(full.length + 1L),
                        "expectedBytes", Long.toString(full.length)));
        assertEquals(1, changedSize.closeCalls);
    }

    @Test
    @Timeout(30)
    void publicFacadeMutationSequenceIsBoundedRepeatableAndExactlySixtyFourCases()
            throws Exception {
        Path basePath = temporaryDirectory.resolve("mutation-base.dt0");
        DtedFixtures.write(basePath, 0);
        byte[] base = Files.readAllBytes(basePath);
        List<byte[]> cases = mutationCases(base);
        assertEquals(64, cases.size());
        assertTrue(cases.stream().allMatch(value -> value.length <= 65_536));

        List<String> first = runMutationSequence(temporaryDirectory.resolve("mutation-a"), cases);
        List<String> second = runMutationSequence(temporaryDirectory.resolve("mutation-b"), cases);
        assertEquals(first, second);
        assertEquals(64, first.size());
    }

    @Test
    void publicArgumentsAndIoCauseKindsAreStableAndPathFree() {
        assertEquals(
                java.util.Set.of(
                        "DTED_IO_FAILED",
                        "DTED_UHL_INVALID",
                        "DTED_DSI_INVALID",
                        "DTED_ACC_INVALID",
                        "DTED_PROFILE_UNSUPPORTED",
                        "DTED_HEADER_INCONSISTENT",
                        "DTED_FILE_LENGTH_MISMATCH",
                        "DTED_DATA_RECORD_INVALID",
                        "DTED_CHECKSUM_MISMATCH",
                        "DTED_ELEVATION_OUT_OF_RANGE"),
                DtedFailures.terminalCodes());
        SourceIdentity identity = new SourceIdentity("io", "I/O");
        Path absent = temporaryDirectory.resolve("missing.dt0");
        assertThrows(
                NullPointerException.class,
                () -> DtedFiles.open(null, absent, DtedOpenOptions.defaults()));
        assertThrows(
                NullPointerException.class,
                () -> DtedFiles.open(identity, null, DtedOpenOptions.defaults()));
        assertThrows(NullPointerException.class, () -> DtedFiles.open(identity, absent, null));
        assertThrows(
                NullPointerException.class,
                () -> DtedFiles.open(identity, absent, DtedOpenOptions.defaults(), null));

        SourceException notFound =
                assertThrows(
                        SourceException.class,
                        () -> DtedFiles.open(identity, absent, DtedOpenOptions.defaults()));
        assertDiagnosticShape(
                notFound,
                "DTED_IO_FAILED",
                "io",
                "dted",
                null,
                null,
                Map.of("causeKind", "notFound", "operation", "open"),
                "DTED file operation failed");
        assertFalse(notFound.terminal().message().contains(absent.toString()));

        assertIoKind(new AccessDeniedException("secret"), "accessDenied");
        assertIoKind(new ClosedChannelException(), "closed");
        assertIoKind(new IOException("localized secret"), "other");
    }

    private List<String> runMutationSequence(Path directory, List<byte[]> cases) throws Exception {
        Files.createDirectory(directory);
        DtedLimits limits =
                DtedLimits.defaults()
                        .withMaximumFileBytes(65_536)
                        .withMaximumProfiles(21)
                        .withMaximumSamplesPerProfile(121)
                        .withMaximumTotalSamples(2_541)
                        .withMaximumProfileBytes(254)
                        .withMaximumParserAllocationBytes(2L * 1_024L * 1_024L);
        DtedOpenOptions options = DtedOpenOptions.defaults().withDtedLimits(limits);
        List<String> outcomes = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            Path path = directory.resolve(String.format(java.util.Locale.ROOT, "%02d.dt0", index));
            Files.write(path, cases.get(index));
            ElevationSource source;
            try {
                source =
                        DtedFiles.open(
                                new SourceIdentity("mutation-" + index, "Mutation " + index),
                                path,
                                options);
            } catch (SourceException failure) {
                assertEquals(1, failure.report().entries().size());
                assertTrue(
                        DtedFailures.terminalCodes().contains(failure.terminal().code())
                                || failure.terminal().code().equals("SOURCE_LIMIT_EXCEEDED"));
                assertFalse(failure.terminal().message().contains(directory.toString()));
                outcomes.add(
                        failure.terminal().code()
                                + ":"
                                + failure.terminal().context()
                                + ":"
                                + failure.terminal().location());
                continue;
            }
            try {
                assertEquals(21, source.metadata().columnCount());
                assertEquals(121, source.metadata().rowCount());
                assertEquals(new Envelope(0, 80, 1, 81), source.metadata().sampleBounds());
                assertEquals(
                        CrsKind.GEOGRAPHIC,
                        source.metadata().crs().definition().orElseThrow().kind());
                assertEquals(ElevationUnit.METRE, source.metadata().elevationUnit());
                assertTrue(source.openingDiagnostics().entries().isEmpty());
                assertEquals(-456, source.sample(0, 0).orElseThrow());
                assertEquals(123, source.sample(0, 120).orElseThrow());
                outcomes.add(
                        "OK:"
                                + source.metadata().columnCount()
                                + ":"
                                + source.metadata().rowCount()
                                + ":"
                                + source.sample(0, 0));
                source.close();
                source.close();
                assertTrue(source.isClosed());
            } finally {
                if (!source.isClosed()) {
                    source.close();
                }
            }
        }
        return outcomes;
    }

    private static List<byte[]> mutationCases(byte[] base) {
        Random random = new Random(MUTATION_SEED);
        List<byte[]> result = new ArrayList<>(64);
        for (int index = 0; index < 64; index++) {
            byte[] changed = base.clone();
            switch (index % 7) {
                case 0 ->
                        changed[random.nextInt(changed.length)] ^= (byte) (1 << random.nextInt(8));
                case 1 -> {
                    int[][] slices = {{0, 4}, {139, 5}, {206, 9}, {728, 3}};
                    int[] slice = slices[random.nextInt(slices.length)];
                    Arrays.fill(changed, slice[0], slice[0] + slice[1], (byte) 'X');
                }
                case 2 ->
                        changed[DtedFixtures.HEADER_BYTES + 1 + random.nextInt(7)] =
                                (byte) random.nextInt(256);
                case 3 ->
                        changed[DtedFixtures.HEADER_BYTES + 8 + 2 * random.nextInt(121)] =
                                (byte) random.nextInt(256);
                case 4 ->
                        changed[DtedFixtures.HEADER_BYTES + 250 + random.nextInt(4)] =
                                (byte) random.nextInt(256);
                case 5 -> changed = Arrays.copyOf(changed, random.nextInt(changed.length));
                case 6 -> changed = Arrays.copyOf(changed, changed.length + 1 + random.nextInt(16));
                default -> throw new AssertionError();
            }
            result.add(changed);
        }
        return result;
    }

    private static void assertProfile(byte[] bytes, String profile) {
        SourceException failure = failure(bytes);
        String component = profile.equals("accuracySubregions") ? "acc" : "dsi";
        long offset =
                switch (profile) {
                    case "level" -> 139;
                    case "productSpecification" -> 206;
                    case "datum" -> 221;
                    case "orientation" -> 344;
                    case "grid" -> 284;
                    case "partialCell" -> 369;
                    case "accuracySubregions" -> 783;
                    default -> throw new AssertionError(profile);
                };
        assertDiagnostic(
                failure,
                "DTED_PROFILE_UNSUPPORTED",
                component,
                null,
                offset,
                Map.of("profile", profile));
    }

    private static void assertMismatch(byte[] bytes, String field, String first, String second) {
        SourceException failure = failure(bytes);
        long offset =
                switch (field) {
                    case "origin" -> 265;
                    case "interval" -> 353;
                    case "rows" -> 361;
                    case "columns" -> 365;
                    case "accuracyOutline" -> 783;
                    case "producerProfile" -> 752;
                    default -> throw new AssertionError(field);
                };
        assertDiagnostic(
                failure,
                "DTED_HEADER_INCONSISTENT",
                second,
                null,
                offset,
                Map.of("field", field, "first", first, "second", second));
    }

    private static void assertLimit(
            Path path, DtedLimits limits, String token, long requested, long maximum) {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                DtedFiles.open(
                                        new SourceIdentity("limit-" + token, "Limit"),
                                        path,
                                        DtedOpenOptions.defaults().withDtedLimits(limits)));
        assertDiagnosticShape(
                failure,
                "SOURCE_LIMIT_EXCEEDED",
                "limit-" + token,
                null,
                null,
                null,
                Map.of(
                        "limit",
                        token,
                        "maximum",
                        Long.toString(maximum),
                        "requested",
                        Long.toString(requested),
                        "scope",
                        "dtedOpen"),
                "Elevation source limit exceeded");
    }

    private static void assertRange(
            byte[] valid,
            int magnitude,
            boolean negative,
            String direction,
            String expectedMagnitude) {
        byte[] changed = valid.clone();
        putWord(changed, (negative ? 0x8000 : 0) | magnitude);
        repairFirstChecksum(changed, 121);
        SourceException failure = failure(changed);
        assertDiagnostic(
                failure,
                "DTED_ELEVATION_OUT_OF_RANGE",
                "data",
                1L,
                (long) DtedFixtures.HEADER_BYTES + 8L,
                Map.of("direction", direction, "magnitude", expectedMagnitude, "sampleIndex", "0"));
    }

    private static void assertAcceptedRange(
            byte[] valid, int magnitude, boolean negative, double expected) {
        byte[] changed = valid.clone();
        putWord(changed, (negative ? 0x8000 : 0) | magnitude);
        repairFirstChecksum(changed, 121);
        try (ElevationSource source =
                DtedReader.open(
                        new SourceIdentity("range-boundary", "Range boundary"),
                        DtedOpenOptions.defaults(),
                        CancellationToken.none(),
                        new MutableAccess(changed, changed.length, changed.length))) {
            assertEquals(expected, source.sample(0, 120).orElseThrow());
        }
    }

    private static void assertIoKind(IOException cause, String kind) {
        SourceException failure = DtedFailures.io("io", "read", "data", 17, cause);
        assertDiagnosticShape(
                failure,
                "DTED_IO_FAILED",
                "io",
                "data",
                null,
                17L,
                Map.of("causeKind", kind, "operation", "read"),
                "DTED file operation failed");
        if (cause.getMessage() != null) {
            assertFalse(failure.terminal().message().contains(cause.getMessage()));
        }
    }

    private static void assertDiagnostic(
            SourceException failure,
            String code,
            String component,
            Long recordNumber,
            Long byteOffset,
            Map<String, String> context) {
        assertDiagnosticShape(
                failure,
                code,
                "validation",
                component,
                recordNumber,
                byteOffset,
                context,
                failure.terminal().message());
    }

    private static void assertDiagnosticShape(
            SourceException failure,
            String code,
            String sourceId,
            String component,
            Long recordNumber,
            Long byteOffset,
            Map<String, String> context,
            String message) {
        assertEquals(1, failure.report().entries().size());
        assertEquals(failure.terminal(), failure.report().entries().getFirst());
        assertEquals(code, failure.terminal().code());
        assertEquals(DiagnosticSeverity.ERROR, failure.terminal().severity());
        assertEquals(sourceId, failure.terminal().sourceId());
        assertEquals(context, failure.terminal().context());
        List<String> actualKeys = new ArrayList<>(failure.terminal().context().keySet());
        List<String> expectedKeys = context.keySet().stream().sorted().toList();
        assertEquals(expectedKeys, actualKeys);
        assertEquals(message, failure.terminal().message());
        assertTrue(failure.terminal().message().length() <= 160);
        assertFalse(failure.terminal().message().contains("validation.dt0"));
        if (component == null) {
            assertTrue(failure.terminal().location().isEmpty());
            return;
        }
        var location = failure.terminal().location().orElseThrow();
        assertEquals(component, location.component().orElseThrow());
        assertEquals(
                recordNumber == null
                        ? java.util.OptionalLong.empty()
                        : java.util.OptionalLong.of(recordNumber),
                location.recordNumber());
        assertTrue(location.partIndex().isEmpty());
        assertTrue(location.fieldIndex().isEmpty());
        assertTrue(location.fieldName().isEmpty());
        assertEquals(
                byteOffset == null
                        ? java.util.OptionalLong.empty()
                        : java.util.OptionalLong.of(byteOffset),
                location.byteOffset());
    }

    private static void putWord(byte[] bytes, int word) {
        putWord(bytes, 121, 0, 0, word);
    }

    private static void putWord(byte[] bytes, int rows, int profile, int fileSample, int word) {
        int recordBytes = 12 + 2 * rows;
        int offset =
                Math.toIntExact(
                        DtedFixtures.HEADER_BYTES
                                + (long) profile * recordBytes
                                + 8L
                                + 2L * fileSample);
        ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.BIG_ENDIAN).putShort((short) word);
    }

    private static void repairFirstChecksum(byte[] bytes, int rows) {
        repairChecksum(bytes, rows, 0);
    }

    private static void repairChecksum(byte[] bytes, int rows, int profile) {
        int recordBytes = 12 + 2 * rows;
        int recordStart = Math.toIntExact(DtedFixtures.HEADER_BYTES + (long) profile * recordBytes);
        long checksum = 0;
        for (int index = recordStart; index < recordStart + recordBytes - 4; index++) {
            checksum += bytes[index] & 0xffL;
        }
        ByteBuffer.wrap(bytes, recordStart + recordBytes - 4, 4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt((int) checksum);
    }

    private static SourceException failure(byte[] bytes) {
        return directFailure(new MutableAccess(bytes, bytes.length, bytes.length));
    }

    private static SourceException directFailure(MutableAccess access) {
        return assertThrows(
                SourceException.class,
                () ->
                        DtedReader.open(
                                new SourceIdentity("validation", "Validation"),
                                DtedOpenOptions.defaults(),
                                CancellationToken.none(),
                                access));
    }

    private static void put(byte[] target, int offset, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }

    private record HeaderCase(
            int offset, byte replacement, String code, String field, String reason) {
        long fieldStart() {
            return switch (field) {
                case "absoluteVerticalAccuracy" -> offset < 80 ? 28 : 735;
                case "securityCode" -> 32;
                case "uniqueReference" -> offset < 80 ? 35 : 144;
                case "reserved" -> offset;
                case "securityClassification" -> 83;
                case "securityControlRelease" -> 84;
                case "series" -> 139;
                case "edition" -> 167;
                case "matchMergeVersion" -> 169;
                case "maintenanceDate" -> 170;
                case "maintenanceDescription" -> 178;
                case "producerCode" -> 182;
                case "productSpecification" -> 206;
                case "amendmentChange" -> 215;
                case "verticalDatum" -> 221;
                case "collectionSystem" -> 229;
                case "orientation" -> 344;
                case "nimaUse" -> 371;
                case "producingNationUse" -> 472;
                case "comments" -> 572;
                case "absoluteHorizontalAccuracy" -> 731;
                case "srtmMarker" -> 752;
                case "subregionData" -> 785;
                default -> offset;
            };
        }
    }

    private static final class MutableAccess implements DtedFileAccess {
        private final byte[] bytes;
        private final long initialSize;
        private final long finalSize;
        private int sizeCalls;
        private int readCalls;
        private int closeCalls;

        private MutableAccess(byte[] bytes, long initialSize, long finalSize) {
            this.bytes = bytes;
            this.initialSize = initialSize;
            this.finalSize = finalSize;
        }

        @Override
        public long size() {
            return sizeCalls++ == 0 ? initialSize : finalSize;
        }

        @Override
        public int read(ByteBuffer destination, long position) {
            readCalls++;
            if (position >= bytes.length) {
                return -1;
            }
            int count = Math.min(destination.remaining(), bytes.length - Math.toIntExact(position));
            destination.put(bytes, Math.toIntExact(position), count);
            return count;
        }

        @Override
        public void close() throws IOException {
            closeCalls++;
        }
    }
}
