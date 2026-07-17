package io.github.mundanej.map.io.dted;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.PackedElevationGrid;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;

/** One strict eager DTED transaction. */
final class DtedReader {
    private static final int UHL_BYTES = 80;
    private static final int DSI_BYTES = 648;
    private static final int ACC_BYTES = 2_700;
    private static final int HEADER_BYTES = UHL_BYTES + DSI_BYTES + ACC_BYTES;
    private static final int CHECKPOINT_MASK = 4095;

    private DtedReader() {}

    static ElevationSource open(
            SourceIdentity identity,
            Path path,
            DtedOpenOptions options,
            CancellationToken cancellation) {
        checkpoint(identity.id(), cancellation);
        DtedFileAccess access;
        try {
            access = JdkDtedFileAccess.open(path);
        } catch (IOException failure) {
            throw DtedFailures.io(identity.id(), "open", failure);
        }
        return open(identity, options, cancellation, access);
    }

    static ElevationSource open(
            SourceIdentity identity,
            DtedOpenOptions options,
            CancellationToken cancellation,
            DtedFileAccess access) {
        String sourceId = identity.id();
        DtedFileAccess transaction = access;
        try {
            checkpoint(sourceId, cancellation);
            long initialSize = size(sourceId, transaction, cancellation);
            DtedLimits dtedLimits = options.dtedLimits();
            requireDtedLimit(sourceId, "fileBytes", initialSize, dtedLimits.maximumFileBytes());
            AllocationBudget allocations =
                    new AllocationBudget(sourceId, dtedLimits.maximumParserAllocationBytes());
            allocations.reserve(ACC_BYTES);
            byte[] scratch = new byte[ACC_BYTES];
            HeaderPlan plan = readHeaders(identity, transaction, scratch, cancellation);
            Layout layout = preflightDtedLimits(identity, plan, dtedLimits);
            preflightLimits(identity, plan, options.elevationSourceLimits());
            if (initialSize != layout.expectedFileBytes) {
                throw DtedFailures.fileLength(sourceId, initialSize, layout.expectedFileBytes);
            }
            allocations.reserve(layout.remainingAllocationBytes);
            checkpoint(sourceId, cancellation);
            int sampleCount = Math.toIntExact(layout.totalSamples);
            double[] samples = new double[sampleCount];
            BitSet noData = new BitSet(sampleCount);
            readProfiles(
                    sourceId,
                    transaction,
                    plan,
                    layout.profileBytes,
                    samples,
                    noData,
                    cancellation);
            checkpoint(sourceId, cancellation);
            long finalSize = size(sourceId, transaction, cancellation);
            if (finalSize != initialSize) {
                throw DtedFailures.fileLength(sourceId, finalSize, initialSize);
            }
            DtedFileAccess closing = transaction;
            transaction = null;
            close(sourceId, closing);
            checkpoint(sourceId, cancellation);
            ElevationSourceMetadata metadata =
                    new ElevationSourceMetadata(
                            identity,
                            plan.columns,
                            plan.rows,
                            new Envelope(
                                    plan.longitudeTenths / 36_000.0,
                                    plan.latitudeTenths / 36_000.0,
                                    plan.longitudeTenths / 36_000.0 + 1.0,
                                    plan.latitudeTenths / 36_000.0 + 1.0),
                            CrsMetadata.recognized(
                                    CrsDefinitions.EPSG_4326,
                                    Optional.of("EPSG:4326"),
                                    Optional.empty()),
                            ElevationUnit.METRE);
            PackedElevationGrid published =
                    PackedElevationGrid.copyOf(
                            metadata,
                            samples,
                            noData,
                            options.elevationSourceLimits(),
                            DiagnosticReport.empty());
            if (cancellation.isCancellationRequested()) {
                published.close();
                throw DtedFailures.cancelled(sourceId);
            }
            return published;
        } catch (RuntimeException | Error failure) {
            if (transaction != null) {
                try {
                    transaction.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(DtedFailures.io(sourceId, "close", closeFailure));
                }
            }
            throw failure;
        }
    }

    private static long size(
            String sourceId, DtedFileAccess access, CancellationToken cancellation) {
        checkpoint(sourceId, cancellation);
        try {
            long size = access.size();
            checkpoint(sourceId, cancellation);
            return size;
        } catch (IOException failure) {
            throw DtedFailures.io(sourceId, "size", failure);
        }
    }

    private static HeaderPlan readHeaders(
            SourceIdentity identity,
            DtedFileAccess access,
            byte[] scratch,
            CancellationToken cancellation) {
        String sourceId = identity.id();
        readSection(
                sourceId,
                access,
                scratch,
                0,
                UHL_BYTES,
                "uhl",
                "DTED_UHL_INVALID",
                0,
                cancellation);
        Uhl uhl = parseUhl(sourceId, scratch);
        readSection(
                sourceId,
                access,
                scratch,
                UHL_BYTES,
                DSI_BYTES,
                "dsi",
                "DTED_DSI_INVALID",
                0,
                cancellation);
        Dsi dsi = parseDsi(sourceId, scratch);
        readSection(
                sourceId,
                access,
                scratch,
                UHL_BYTES + DSI_BYTES,
                ACC_BYTES,
                "acc",
                "DTED_ACC_INVALID",
                0,
                cancellation);
        Acc acc = parseAccPrefix(sourceId, scratch);
        reconcileAccuracyAndProducer(sourceId, uhl, dsi, acc);
        if (acc.subregions > 0) {
            throw DtedFailures.unsupported(sourceId, "acc", 783, "accuracySubregions");
        }
        validateAccTail(sourceId, scratch);
        return reconcile(sourceId, uhl, dsi);
    }

    private static void readSection(
            String sourceId,
            DtedFileAccess access,
            byte[] scratch,
            long position,
            int length,
            String component,
            String code,
            long recordNumber,
            CancellationToken cancellation) {
        ByteBuffer target = ByteBuffer.wrap(scratch, 0, length);
        long cursor = position;
        int zeroReads = 0;
        while (target.hasRemaining()) {
            checkpoint(sourceId, cancellation);
            int count;
            try {
                count = access.read(target, cursor);
            } catch (IOException failure) {
                throw DtedFailures.io(sourceId, "read", component, cursor, failure);
            }
            checkpoint(sourceId, cancellation);
            if (count < 0) {
                if (recordNumber > 0) {
                    throw DtedFailures.failure(
                            sourceId,
                            code,
                            component,
                            recordNumber,
                            position + target.position(),
                            "DTED fixed frame is truncated",
                            Map.of(
                                    "actualBytes",
                                    Integer.toString(target.position()),
                                    "expectedBytes",
                                    Integer.toString(length),
                                    "field",
                                    "frame",
                                    "reason",
                                    "truncated"),
                            null);
                }
                throw DtedFailures.failure(
                        sourceId,
                        code,
                        component,
                        recordNumber,
                        position,
                        "DTED fixed section is truncated",
                        Map.of(
                                "actualBytes", Integer.toString(target.position()),
                                "expectedBytes", Integer.toString(length),
                                "reason", "truncated"),
                        null);
            }
            if (count == 0) {
                if (++zeroReads > 16) {
                    throw DtedFailures.io(sourceId, "read", component, cursor, null);
                }
            } else {
                zeroReads = 0;
                cursor += count;
            }
        }
    }

    private static Uhl parseUhl(String sourceId, byte[] bytes) {
        literal(sourceId, "DTED_UHL_INVALID", "uhl", 0, bytes, 0, "UHL1", "sentinel");
        int longitude =
                coordinate(
                        sourceId,
                        "DTED_UHL_INVALID",
                        "uhl",
                        4,
                        bytes,
                        4,
                        3,
                        false,
                        "longitudeOrigin");
        int latitude =
                coordinate(
                        sourceId,
                        "DTED_UHL_INVALID",
                        "uhl",
                        12,
                        bytes,
                        12,
                        3,
                        true,
                        "latitudeOrigin");
        int longitudeInterval =
                digits(sourceId, "DTED_UHL_INVALID", "uhl", 20, bytes, 20, 4, "longitudeInterval");
        positiveField(
                sourceId, "DTED_UHL_INVALID", "uhl", 20, "longitudeInterval", longitudeInterval);
        int latitudeInterval =
                digits(sourceId, "DTED_UHL_INVALID", "uhl", 24, bytes, 24, 4, "latitudeInterval");
        positiveField(
                sourceId, "DTED_UHL_INVALID", "uhl", 24, "latitudeInterval", latitudeInterval);
        accuracy(sourceId, "DTED_UHL_INVALID", "uhl", 28, bytes, 28, "absoluteVerticalAccuracy");
        securityCode(sourceId, "DTED_UHL_INVALID", "uhl", 32, bytes, 32, 3, "securityCode");
        printable(sourceId, "DTED_UHL_INVALID", "uhl", 35, bytes, 35, 12, "uniqueReference");
        int columns =
                digits(sourceId, "DTED_UHL_INVALID", "uhl", 47, bytes, 47, 4, "longitudeCount");
        int rows = digits(sourceId, "DTED_UHL_INVALID", "uhl", 51, bytes, 51, 4, "latitudeCount");
        if (bytes[55] != '0' && bytes[55] != '1') {
            throw DtedFailures.field(
                    sourceId, "DTED_UHL_INVALID", "uhl", 55, "multipleAccuracy", "grammar");
        }
        requiredBlank(
                sourceId, "DTED_UHL_INVALID", "uhl", 56, bytes, 56, 24, "reserved", "reserved");
        return new Uhl(
                longitude,
                latitude,
                longitudeInterval,
                latitudeInterval,
                columns,
                rows,
                bytes[55] == '1');
    }

    private static Dsi parseDsi(String sourceId, byte[] bytes) {
        literal(sourceId, "DTED_DSI_INVALID", "dsi", 80, bytes, 0, "DSI", "sentinel");
        enumByte(
                sourceId,
                "DTED_DSI_INVALID",
                "dsi",
                83,
                bytes[3],
                "SCUR",
                "securityClassification");
        printable(sourceId, "DTED_DSI_INVALID", "dsi", 84, bytes, 4, 2, "securityControlRelease");
        printable(sourceId, "DTED_DSI_INVALID", "dsi", 86, bytes, 6, 27, "securityHandling");
        requiredBlank(
                sourceId, "DTED_DSI_INVALID", "dsi", 113, bytes, 33, 26, "reserved", "reserved");
        literal(sourceId, "DTED_DSI_INVALID", "dsi", 139, bytes, 59, "DTED", "series");
        int level = digit(sourceId, "DTED_DSI_INVALID", "dsi", 139, bytes[63], "series");
        printable(sourceId, "DTED_DSI_INVALID", "dsi", 144, bytes, 64, 15, "uniqueReference");
        requiredBlank(
                sourceId, "DTED_DSI_INVALID", "dsi", 159, bytes, 79, 8, "reserved", "reserved");
        int edition = digits(sourceId, "DTED_DSI_INVALID", "dsi", 167, bytes, 87, 2, "edition");
        if (edition == 0) {
            throw DtedFailures.field(sourceId, "DTED_DSI_INVALID", "dsi", 167, "edition", "range");
        }
        upperLetter(sourceId, "DTED_DSI_INVALID", "dsi", 169, bytes[89], "matchMergeVersion");
        date(sourceId, "DTED_DSI_INVALID", "dsi", 170, bytes, 90, "maintenanceDate");
        date(sourceId, "DTED_DSI_INVALID", "dsi", 174, bytes, 94, "matchMergeDate");
        maintenanceDescription(sourceId, bytes);
        upperAlphanumericOrSpace(
                sourceId, "DTED_DSI_INVALID", "dsi", 182, bytes, 102, 8, "producerCode");
        requiredBlank(
                sourceId, "DTED_DSI_INVALID", "dsi", 190, bytes, 110, 16, "reserved", "reserved");
        upperAlphanumeric(
                sourceId, "DTED_DSI_INVALID", "dsi", 206, bytes, 126, 9, "productSpecification");
        boolean supportedProduct = matches(bytes, 126, "PRF89020B");
        digits(sourceId, "DTED_DSI_INVALID", "dsi", 215, bytes, 135, 2, "amendmentChange");
        date(sourceId, "DTED_DSI_INVALID", "dsi", 217, bytes, 137, "specificationDate");
        upperAlphanumeric(sourceId, "DTED_DSI_INVALID", "dsi", 221, bytes, 141, 3, "verticalDatum");
        upperAlphanumeric(
                sourceId, "DTED_DSI_INVALID", "dsi", 224, bytes, 144, 5, "horizontalDatum");
        boolean supportedDatum =
                (matches(bytes, 141, "MSL") || matches(bytes, 141, "E96"))
                        && matches(bytes, 144, "WGS84");
        printable(sourceId, "DTED_DSI_INVALID", "dsi", 229, bytes, 149, 10, "collectionSystem");
        boolean srtmCollection = matches(bytes, 149, "SRTM      ");
        date(sourceId, "DTED_DSI_INVALID", "dsi", 239, bytes, 159, "compilationDate");
        requiredBlank(
                sourceId, "DTED_DSI_INVALID", "dsi", 243, bytes, 163, 22, "reserved", "reserved");
        int latitude = decimalCoordinate(sourceId, bytes, 185, 2, true);
        int longitude = decimalCoordinate(sourceId, bytes, 194, 3, false);
        int southWestLatitude =
                coordinate(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        284,
                        bytes,
                        204,
                        2,
                        true,
                        "southWestCorner");
        int southWestLongitude =
                coordinate(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        291,
                        bytes,
                        211,
                        3,
                        false,
                        "southWestCorner");
        int northWestLatitude =
                coordinate(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        299,
                        bytes,
                        219,
                        2,
                        true,
                        "northWestCorner");
        int northWestLongitude =
                coordinate(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        306,
                        bytes,
                        226,
                        3,
                        false,
                        "northWestCorner");
        int northEastLatitude =
                coordinate(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        314,
                        bytes,
                        234,
                        2,
                        true,
                        "northEastCorner");
        int northEastLongitude =
                coordinate(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        321,
                        bytes,
                        241,
                        3,
                        false,
                        "northEastCorner");
        int southEastLatitude =
                coordinate(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        329,
                        bytes,
                        249,
                        2,
                        true,
                        "southEastCorner");
        int southEastLongitude =
                coordinate(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        336,
                        bytes,
                        256,
                        3,
                        false,
                        "southEastCorner");
        int orientation =
                decimalDigits(
                        sourceId, "DTED_DSI_INVALID", "dsi", 344, bytes, 264, 9, "orientation");
        int latitudeInterval =
                digits(sourceId, "DTED_DSI_INVALID", "dsi", 353, bytes, 273, 4, "latitudeInterval");
        int longitudeInterval =
                digits(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        357,
                        bytes,
                        277,
                        4,
                        "longitudeInterval");
        int rows = digits(sourceId, "DTED_DSI_INVALID", "dsi", 361, bytes, 281, 4, "latitudeCount");
        int columns =
                digits(sourceId, "DTED_DSI_INVALID", "dsi", 365, bytes, 285, 4, "longitudeCount");
        int partial =
                digits(sourceId, "DTED_DSI_INVALID", "dsi", 369, bytes, 289, 2, "partialCell");
        printable(sourceId, "DTED_DSI_INVALID", "dsi", 371, bytes, 291, 101, "nimaUse");
        printable(sourceId, "DTED_DSI_INVALID", "dsi", 472, bytes, 392, 100, "producingNationUse");
        printable(sourceId, "DTED_DSI_INVALID", "dsi", 572, bytes, 492, 156, "comments");
        return new Dsi(
                level,
                supportedProduct,
                supportedDatum,
                srtmCollection,
                longitude,
                latitude,
                southWestLongitude,
                southWestLatitude,
                northWestLongitude,
                northWestLatitude,
                northEastLongitude,
                northEastLatitude,
                southEastLongitude,
                southEastLatitude,
                orientation,
                longitudeInterval,
                latitudeInterval,
                columns,
                rows,
                partial);
    }

    private static Acc parseAccPrefix(String sourceId, byte[] bytes) {
        literal(sourceId, "DTED_ACC_INVALID", "acc", 728, bytes, 0, "ACC", "sentinel");
        accuracy(sourceId, "DTED_ACC_INVALID", "acc", 731, bytes, 3, "absoluteHorizontalAccuracy");
        accuracy(sourceId, "DTED_ACC_INVALID", "acc", 735, bytes, 7, "absoluteVerticalAccuracy");
        accuracy(sourceId, "DTED_ACC_INVALID", "acc", 739, bytes, 11, "relativeHorizontalAccuracy");
        accuracy(sourceId, "DTED_ACC_INVALID", "acc", 743, bytes, 15, "relativeVerticalAccuracy");
        requiredBlank(
                sourceId, "DTED_ACC_INVALID", "acc", 747, bytes, 19, 5, "reserved", "reserved");
        byte marker = bytes[24];
        if (marker != ' ' && marker != 'X') {
            throw DtedFailures.field(
                    sourceId, "DTED_ACC_INVALID", "acc", 752, "srtmMarker", "grammar");
        }
        requiredBlank(
                sourceId, "DTED_ACC_INVALID", "acc", 753, bytes, 25, 30, "reserved", "reserved");
        int subregions =
                digits(sourceId, "DTED_ACC_INVALID", "acc", 783, bytes, 55, 2, "multipleAccuracy");
        if (subregions == 1 || subregions > 9) {
            throw DtedFailures.field(
                    sourceId, "DTED_ACC_INVALID", "acc", 783, "multipleAccuracy", "range");
        }
        return new Acc(subregions, marker == 'X');
    }

    private static void reconcileAccuracyAndProducer(String sourceId, Uhl uhl, Dsi dsi, Acc acc) {
        boolean accHasSubregions = acc.subregions > 0;
        if (uhl.multipleAccuracy != accHasSubregions) {
            throw DtedFailures.headerMismatch(
                    sourceId, "acc", 783, "accuracyOutline", "uhl", "acc");
        }
        if (dsi.srtmCollection != acc.srtmMarker) {
            throw DtedFailures.headerMismatch(
                    sourceId, "acc", 752, "producerProfile", "dsi", "acc");
        }
    }

    private static void validateAccTail(String sourceId, byte[] bytes) {
        requiredBlank(
                sourceId,
                "DTED_ACC_INVALID",
                "acc",
                785,
                bytes,
                57,
                2_643,
                "subregionData",
                "unexpectedSubregionData");
    }

    private static HeaderPlan reconcile(String sourceId, Uhl uhl, Dsi dsi) {
        if (uhl.longitude != dsi.longitude || uhl.latitude != dsi.latitude) {
            throw DtedFailures.headerMismatch(sourceId, "dsi", 265, "origin", "uhl", "dsi");
        }
        if (uhl.longitudeInterval != dsi.longitudeInterval
                || uhl.latitudeInterval != dsi.latitudeInterval) {
            throw DtedFailures.headerMismatch(sourceId, "dsi", 353, "interval", "uhl", "dsi");
        }
        if (uhl.rows != dsi.rows) {
            throw DtedFailures.headerMismatch(sourceId, "dsi", 361, "rows", "uhl", "dsi");
        }
        if (uhl.columns != dsi.columns) {
            throw DtedFailures.headerMismatch(sourceId, "dsi", 365, "columns", "uhl", "dsi");
        }
        if (dsi.level > 2) {
            throw DtedFailures.unsupported(sourceId, "dsi", 139, "level");
        }
        if (!dsi.supportedProduct) {
            throw DtedFailures.unsupported(sourceId, "dsi", 206, "productSpecification");
        }
        if (!dsi.supportedDatum) {
            throw DtedFailures.unsupported(sourceId, "dsi", 221, "datum");
        }
        if (dsi.orientation != 0) {
            throw DtedFailures.unsupported(sourceId, "dsi", 344, "orientation");
        }
        if (dsi.longitude < -180 * 36_000
                || dsi.longitude > 179 * 36_000
                || dsi.latitude < -90 * 36_000
                || dsi.latitude > 89 * 36_000
                || dsi.longitude % 36_000 != 0
                || dsi.latitude % 36_000 != 0) {
            throw DtedFailures.unsupported(sourceId, "dsi", 265, "grid");
        }
        int oneDegree = 36_000;
        if (dsi.southWestLongitude != dsi.longitude
                || dsi.southWestLatitude != dsi.latitude
                || dsi.northWestLongitude != dsi.longitude
                || dsi.northWestLatitude != dsi.latitude + oneDegree
                || dsi.northEastLongitude != dsi.longitude + oneDegree
                || dsi.northEastLatitude != dsi.latitude + oneDegree
                || dsi.southEastLongitude != dsi.longitude + oneDegree
                || dsi.southEastLatitude != dsi.latitude) {
            throw DtedFailures.unsupported(sourceId, "dsi", 284, "grid");
        }
        int zone = zone(dsi.latitude);
        int expectedLatitudeInterval =
                switch (dsi.level) {
                    case 0 -> 300;
                    case 1 -> 30;
                    case 2 -> 10;
                    default -> throw new AssertionError();
                };
        int expectedRows =
                switch (dsi.level) {
                    case 0 -> 121;
                    case 1 -> 1_201;
                    case 2 -> 3_601;
                    default -> throw new AssertionError();
                };
        int expectedLongitudeInterval = longitudeInterval(dsi.level, zone);
        int expectedColumns = 36_000 / expectedLongitudeInterval + 1;
        if (dsi.latitudeInterval != expectedLatitudeInterval
                || dsi.longitudeInterval != expectedLongitudeInterval
                || dsi.rows != expectedRows
                || dsi.columns != expectedColumns) {
            throw DtedFailures.unsupported(sourceId, "dsi", 353, "grid");
        }
        if ((dsi.level < 2 && dsi.partial != 0) || dsi.partial < 0 || dsi.partial > 99) {
            throw DtedFailures.unsupported(sourceId, "dsi", 369, "partialCell");
        }
        return new HeaderPlan(
                dsi.level, dsi.longitude, dsi.latitude, dsi.columns, dsi.rows, dsi.partial != 0);
    }

    private static int zone(int southLatitudeTenths) {
        int northLatitudeTenths = Math.addExact(southLatitudeTenths, 36_000);
        int nearest = Math.min(Math.abs(southLatitudeTenths), Math.abs(northLatitudeTenths));
        if (nearest < 50 * 36_000) {
            return 0;
        }
        if (nearest < 70 * 36_000) {
            return 1;
        }
        if (nearest < 75 * 36_000) {
            return 2;
        }
        if (nearest < 80 * 36_000) {
            return 3;
        }
        return 4;
    }

    private static int longitudeInterval(int level, int zone) {
        return switch (level) {
            case 0 ->
                    switch (zone) {
                        case 0 -> 300;
                        case 1 -> 600;
                        case 2 -> 900;
                        case 3 -> 1_200;
                        case 4 -> 1_800;
                        default -> throw new AssertionError();
                    };
            case 1 ->
                    switch (zone) {
                        case 0 -> 30;
                        case 1 -> 60;
                        case 2 -> 90;
                        case 3 -> 120;
                        case 4 -> 180;
                        default -> throw new AssertionError();
                    };
            case 2 ->
                    switch (zone) {
                        case 0 -> 10;
                        case 1 -> 20;
                        case 2 -> 30;
                        case 3 -> 40;
                        case 4 -> 60;
                        default -> throw new AssertionError();
                    };
            default -> throw new AssertionError();
        };
    }

    private static void preflightLimits(
            SourceIdentity identity, HeaderPlan plan, ElevationSourceLimits limits) {
        requireElevationLimit(identity.id(), "columns", plan.columns, limits.maximumColumns());
        requireElevationLimit(identity.id(), "rows", plan.rows, limits.maximumRows());
        long samples = checkedMultiply(plan.columns, plan.rows);
        requireElevationLimit(
                identity.id(),
                "samples",
                samples,
                Math.min(limits.maximumSamples(), (long) Integer.MAX_VALUE));
        long words = samples == Long.MAX_VALUE ? Long.MAX_VALUE : 1L + (samples - 1L) / 64L;
        long bytes = checkedAdd(checkedMultiply(samples, 8L), checkedMultiply(words, 8L));
        requireElevationLimit(
                identity.id(), "retainedSampleBytes", bytes, limits.maximumRetainedSampleBytes());
    }

    private static Layout preflightDtedLimits(
            SourceIdentity identity, HeaderPlan plan, DtedLimits limits) {
        String sourceId = identity.id();
        requireDtedLimit(sourceId, "profiles", plan.columns, limits.maximumProfiles());
        requireDtedLimit(
                sourceId, "samplesPerProfile", plan.rows, limits.maximumSamplesPerProfile());
        long totalSamples = checkedMultiply(plan.columns, plan.rows);
        requireDtedLimit(sourceId, "totalSamples", totalSamples, limits.maximumTotalSamples());
        long profileBytesLong = checkedAdd(12L, checkedMultiply(2L, plan.rows));
        requireDtedLimit(sourceId, "profileBytes", profileBytesLong, limits.maximumProfileBytes());
        int profileBytes = Math.toIntExact(profileBytesLong);
        long expectedFileBytes =
                checkedAdd(HEADER_BYTES, checkedMultiply(plan.columns, profileBytesLong));
        long words =
                totalSamples == Long.MAX_VALUE ? Long.MAX_VALUE : 1L + (totalSamples - 1L) / 64L;
        long oneGridBytes =
                checkedAdd(checkedMultiply(totalSamples, 8L), checkedMultiply(words, 8L));
        long remainingAllocationBytes =
                checkedAdd(profileBytesLong, checkedMultiply(2L, oneGridBytes));
        return new Layout(totalSamples, profileBytes, expectedFileBytes, remainingAllocationBytes);
    }

    private static void requireElevationLimit(
            String sourceId, String limit, long requested, long maximum) {
        if (requested > maximum) {
            throw DtedFailures.elevationLimit(sourceId, limit, requested, maximum);
        }
    }

    private static void requireDtedLimit(
            String sourceId, String limit, long requested, long maximum) {
        if (requested > maximum) {
            throw DtedFailures.dtedLimit(sourceId, limit, requested, maximum);
        }
    }

    static long checkedMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException failure) {
            return Long.MAX_VALUE;
        }
    }

    static long checkedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException failure) {
            return Long.MAX_VALUE;
        }
    }

    private static void readProfiles(
            String sourceId,
            DtedFileAccess access,
            HeaderPlan plan,
            int recordBytes,
            double[] samples,
            BitSet noData,
            CancellationToken cancellation) {
        byte[] record = new byte[recordBytes];
        for (int profile = 0; profile < plan.columns; profile++) {
            checkpoint(sourceId, cancellation);
            long position =
                    Math.addExact(HEADER_BYTES, Math.multiplyExact((long) profile, recordBytes));
            readSection(
                    sourceId,
                    access,
                    record,
                    position,
                    recordBytes,
                    "data",
                    "DTED_DATA_RECORD_INVALID",
                    profile + 1L,
                    cancellation);
            if ((record[0] & 0xff) != 0xaa) {
                throw DtedFailures.recordField(
                        sourceId, profile + 1L, position, "sentinel", "literal");
            }
            requireRecordCount(
                    sourceId,
                    profile + 1L,
                    position + 1L,
                    "blockCount",
                    unsigned24(record, 1),
                    profile);
            requireRecordCount(
                    sourceId,
                    profile + 1L,
                    position + 4L,
                    "longitudeCount",
                    unsigned16(record, 4),
                    profile);
            requireRecordCount(
                    sourceId,
                    profile + 1L,
                    position + 6L,
                    "latitudeCount",
                    unsigned16(record, 6),
                    0);
            long expectedChecksum = 0;
            for (int index = 0; index < recordBytes - 4; index++) {
                expectedChecksum = Math.addExact(expectedChecksum, record[index] & 0xffL);
            }
            long actualChecksum = unsigned32(record, recordBytes - 4);
            if (actualChecksum != expectedChecksum) {
                throw DtedFailures.checksum(
                        sourceId,
                        profile + 1L,
                        position + recordBytes - 4L,
                        actualChecksum,
                        expectedChecksum);
            }
            for (int fileSample = 0; fileSample < plan.rows; fileSample++) {
                if ((fileSample & CHECKPOINT_MASK) == 0) {
                    checkpoint(sourceId, cancellation);
                }
                int word = unsigned16(record, 8 + fileSample * 2);
                int row = plan.rows - 1 - fileSample;
                int index = Math.toIntExact((long) row * plan.columns + profile);
                if (word == 0xffff) {
                    if (!plan.partial) {
                        throw DtedFailures.voidInComplete(
                                sourceId,
                                profile + 1L,
                                position + 8L + fileSample * 2L,
                                fileSample);
                    }
                    noData.set(index);
                    samples[index] = 0.0;
                } else {
                    int magnitude = word & 0x7fff;
                    boolean negative = (word & 0x8000) != 0;
                    if ((!negative && magnitude > 9_000) || (negative && magnitude > 12_000)) {
                        throw DtedFailures.elevation(
                                sourceId,
                                profile + 1L,
                                position + 8L + fileSample * 2L,
                                fileSample,
                                magnitude,
                                negative);
                    }
                    samples[index] = !negative || magnitude == 0 ? magnitude : -magnitude;
                }
            }
        }
    }

    private static void requireRecordCount(
            String sourceId,
            long recordNumber,
            long offset,
            String field,
            int actual,
            int expected) {
        if (actual != expected) {
            throw DtedFailures.recordCountMismatch(
                    sourceId, recordNumber, offset, field, actual, expected);
        }
    }

    private static int unsigned16(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xffff;
    }

    private static int unsigned24(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 16)
                | ((bytes[offset + 1] & 0xff) << 8)
                | (bytes[offset + 2] & 0xff);
    }

    private static long unsigned32(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(
                ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt());
    }

    private static void positiveField(
            String sourceId, String code, String component, long offset, String field, int value) {
        if (value == 0) {
            throw DtedFailures.field(sourceId, code, component, offset, field, "range");
        }
    }

    private static void accuracy(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            String field) {
        if (bytes[offset] == 'N'
                && bytes[offset + 1] == 'A'
                && isPadding(bytes[offset + 2])
                && isPadding(bytes[offset + 3])) {
            return;
        }
        for (int index = 0; index < 4; index++) {
            if (bytes[offset + index] < '0' || bytes[offset + index] > '9') {
                throw DtedFailures.field(
                        sourceId, code, component, absoluteOffset, field, "grammar");
            }
        }
    }

    private static void securityCode(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int length,
            String field) {
        enumByte(sourceId, code, component, absoluteOffset, bytes[offset], "SCUR", field);
        for (int index = 1; index < length; index++) {
            if (bytes[offset + index] != ' ') {
                throw DtedFailures.field(
                        sourceId, code, component, absoluteOffset, field, "grammar");
            }
        }
    }

    private static void enumByte(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte value,
            String allowed,
            String field) {
        if (allowed.indexOf(value) < 0) {
            throw DtedFailures.field(sourceId, code, component, absoluteOffset, field, "grammar");
        }
    }

    private static void printable(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int length,
            String field) {
        boolean terminated = false;
        for (int index = 0; index < length; index++) {
            int value = bytes[offset + index] & 0xff;
            if (value == 0) {
                terminated = true;
            } else if ((terminated && value != ' ') || value < 0x20 || value > 0x7e) {
                throw DtedFailures.field(
                        sourceId, code, component, absoluteOffset, field, "grammar");
            }
        }
    }

    private static void requiredBlank(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int length,
            String field,
            String reason) {
        for (int index = 0; index < length; index++) {
            if (!isPadding(bytes[offset + index])) {
                throw DtedFailures.field(sourceId, code, component, absoluteOffset, field, reason);
            }
        }
    }

    private static void upperLetter(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte value,
            String field) {
        if (value < 'A' || value > 'Z') {
            throw DtedFailures.field(sourceId, code, component, absoluteOffset, field, "grammar");
        }
    }

    private static void date(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            String field) {
        int value = digits(sourceId, code, component, absoluteOffset, bytes, offset, 4, field);
        if (value != 0) {
            int month = value % 100;
            if (month < 1 || month > 12) {
                throw DtedFailures.field(sourceId, code, component, absoluteOffset, field, "range");
            }
        }
    }

    private static void maintenanceDescription(String sourceId, byte[] bytes) {
        boolean zero = true;
        for (int index = 98; index < 102; index++) {
            zero &= bytes[index] == '0';
        }
        if (zero) {
            return;
        }
        upperLetter(sourceId, "DTED_DSI_INVALID", "dsi", 178, bytes[98], "maintenanceDescription");
        digits(sourceId, "DTED_DSI_INVALID", "dsi", 178, bytes, 99, 3, "maintenanceDescription");
    }

    private static void upperAlphanumeric(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int length,
            String field) {
        for (int index = 0; index < length; index++) {
            byte value = bytes[offset + index];
            if (!((value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9'))) {
                throw DtedFailures.field(
                        sourceId, code, component, absoluteOffset, field, "grammar");
            }
        }
    }

    private static void upperAlphanumericOrSpace(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int length,
            String field) {
        boolean terminated = false;
        for (int index = 0; index < length; index++) {
            byte value = bytes[offset + index];
            if (value == 0) {
                terminated = true;
            } else if (terminated
                    ? value != ' '
                    : !((value >= 'A' && value <= 'Z')
                            || (value >= '0' && value <= '9')
                            || value == ' ')) {
                throw DtedFailures.field(
                        sourceId, code, component, absoluteOffset, field, "grammar");
            }
        }
    }

    private static boolean isPadding(byte value) {
        return value == 0 || value == ' ';
    }

    private static boolean matches(byte[] bytes, int offset, String expected) {
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int decimalCoordinate(
            String sourceId, byte[] bytes, int offset, int degreeDigits, boolean latitude) {
        int degrees =
                digits(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        80L + offset,
                        bytes,
                        offset,
                        degreeDigits,
                        "origin");
        int minutes =
                digits(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        80L + offset,
                        bytes,
                        offset + degreeDigits,
                        2,
                        "origin");
        int seconds =
                digits(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        80L + offset,
                        bytes,
                        offset + degreeDigits + 2,
                        2,
                        "origin");
        if (bytes[offset + degreeDigits + 4] != '.') {
            throw DtedFailures.field(
                    sourceId, "DTED_DSI_INVALID", "dsi", 80L + offset, "origin", "grammar");
        }
        int tenth =
                digit(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        80L + offset,
                        bytes[offset + degreeDigits + 5],
                        "origin");
        byte hemisphere = bytes[offset + degreeDigits + 6];
        return coordinateValue(
                sourceId,
                "DTED_DSI_INVALID",
                "dsi",
                latitude,
                degrees,
                minutes,
                seconds,
                tenth,
                hemisphere,
                80L + offset,
                "origin");
    }

    private static int coordinate(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int degreeDigits,
            boolean latitude,
            String field) {
        int degrees =
                digits(
                        sourceId,
                        code,
                        component,
                        absoluteOffset,
                        bytes,
                        offset,
                        degreeDigits,
                        field);
        int minutes =
                digits(
                        sourceId,
                        code,
                        component,
                        absoluteOffset,
                        bytes,
                        offset + degreeDigits,
                        2,
                        field);
        int seconds =
                digits(
                        sourceId,
                        code,
                        component,
                        absoluteOffset,
                        bytes,
                        offset + degreeDigits + 2,
                        2,
                        field);
        byte hemisphere = bytes[offset + degreeDigits + 4];
        return coordinateValue(
                sourceId,
                code,
                component,
                latitude,
                degrees,
                minutes,
                seconds,
                0,
                hemisphere,
                absoluteOffset,
                field);
    }

    private static int coordinateValue(
            String sourceId,
            String code,
            String component,
            boolean latitude,
            int degrees,
            int minutes,
            int seconds,
            int tenth,
            byte hemisphere,
            long offset,
            String field) {
        int maximum = latitude ? 90 : 180;
        boolean positive = latitude ? hemisphere == 'N' : hemisphere == 'E';
        boolean negative = latitude ? hemisphere == 'S' : hemisphere == 'W';
        if ((!positive && !negative)
                || degrees > maximum
                || minutes >= 60
                || seconds >= 60
                || (degrees == maximum && (minutes != 0 || seconds != 0 || tenth != 0))) {
            throw DtedFailures.field(sourceId, code, component, offset, field, "range");
        }
        int value =
                Math.addExact(
                        Math.multiplyExact(degrees * 3_600 + minutes * 60 + seconds, 10), tenth);
        return negative ? -value : value;
    }

    private static int decimalDigits(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int length,
            String field) {
        int dot = offset + length - 2;
        if (bytes[dot] != '.') {
            throw DtedFailures.field(sourceId, code, component, absoluteOffset, field, "grammar");
        }
        int whole =
                digits(sourceId, code, component, absoluteOffset, bytes, offset, length - 2, field);
        int tenth =
                digit(
                        sourceId,
                        code,
                        component,
                        absoluteOffset + length - 1,
                        bytes[offset + length - 1],
                        field);
        return Math.addExact(Math.multiplyExact(whole, 10), tenth);
    }

    private static int digits(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int length,
            String field) {
        int value = 0;
        for (int index = 0; index < length; index++) {
            byte current = bytes[offset + index];
            if (current < '0' || current > '9') {
                throw DtedFailures.field(
                        sourceId, code, component, absoluteOffset, field, "grammar");
            }
            value = Math.addExact(Math.multiplyExact(value, 10), current - '0');
        }
        return value;
    }

    private static int digit(
            String sourceId, String code, String component, long offset, byte value, String field) {
        if (value < '0' || value > '9') {
            throw DtedFailures.field(sourceId, code, component, offset, field, "grammar");
        }
        return value - '0';
    }

    private static void literal(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            String expected,
            String field) {
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != expected.charAt(index)) {
                throw DtedFailures.field(
                        sourceId, code, component, absoluteOffset, field, "literal");
            }
        }
    }

    private static void checkpoint(String sourceId, CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw DtedFailures.cancelled(sourceId);
        }
    }

    private static void close(String sourceId, DtedFileAccess access) {
        try {
            access.close();
        } catch (IOException failure) {
            throw DtedFailures.io(sourceId, "close", failure);
        }
    }

    private record Uhl(
            int longitude,
            int latitude,
            int longitudeInterval,
            int latitudeInterval,
            int columns,
            int rows,
            boolean multipleAccuracy) {}

    private record Acc(int subregions, boolean srtmMarker) {}

    private record Dsi(
            int level,
            boolean supportedProduct,
            boolean supportedDatum,
            boolean srtmCollection,
            int longitude,
            int latitude,
            int southWestLongitude,
            int southWestLatitude,
            int northWestLongitude,
            int northWestLatitude,
            int northEastLongitude,
            int northEastLatitude,
            int southEastLongitude,
            int southEastLatitude,
            int orientation,
            int longitudeInterval,
            int latitudeInterval,
            int columns,
            int rows,
            int partial) {}

    private record HeaderPlan(
            int level,
            int longitudeTenths,
            int latitudeTenths,
            int columns,
            int rows,
            boolean partial) {}

    private record Layout(
            long totalSamples,
            int profileBytes,
            long expectedFileBytes,
            long remainingAllocationBytes) {}

    private static final class AllocationBudget {
        private final String sourceId;
        private final long maximum;
        private long reserved;

        private AllocationBudget(String sourceId, long maximum) {
            this.sourceId = sourceId;
            this.maximum = maximum;
        }

        private void reserve(long bytes) {
            long requested = checkedAdd(reserved, bytes);
            requireDtedLimit(sourceId, "parserAllocationBytes", requested, maximum);
            reserved = requested;
        }
    }
}
