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
            long actualSize = size(sourceId, transaction, cancellation);
            byte[] scratch = new byte[ACC_BYTES];
            HeaderPlan plan = readHeaders(identity, transaction, scratch, cancellation);
            preflightLimits(identity, plan, options.elevationSourceLimits());
            long expectedSize = expectedSize(plan);
            if (actualSize != expectedSize) {
                throw DtedFailures.failure(
                        sourceId,
                        "DTED_FILE_LENGTH_MISMATCH",
                        "dted",
                        -1,
                        "DTED file length does not match its fixed profile",
                        Map.of(
                                "actualBytes",
                                Long.toString(actualSize),
                                "expectedBytes",
                                Long.toString(expectedSize)),
                        null);
            }
            checkpoint(sourceId, cancellation);
            int sampleCount = Math.toIntExact(Math.multiplyExact((long) plan.columns, plan.rows));
            double[] samples = new double[sampleCount];
            BitSet noData = new BitSet(sampleCount);
            readProfiles(sourceId, transaction, plan, samples, noData, cancellation);
            checkpoint(sourceId, cancellation);
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
        Acc acc = parseAcc(sourceId, scratch);
        return reconcile(sourceId, uhl, dsi, acc);
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
                throw DtedFailures.io(sourceId, "read", failure);
            }
            checkpoint(sourceId, cancellation);
            if (count < 0) {
                throw DtedFailures.failure(
                        sourceId,
                        code,
                        component,
                        recordNumber,
                        position,
                        "DTED fixed frame is truncated",
                        Map.of(
                                "actualBytes",
                                Integer.toString(target.position()),
                                "expectedBytes",
                                Integer.toString(length),
                                "reason",
                                "truncated"),
                        null);
            }
            if (count == 0) {
                if (++zeroReads > 16) {
                    throw DtedFailures.io(sourceId, "read", null);
                }
            } else {
                zeroReads = 0;
                cursor += count;
            }
        }
    }

    private static Uhl parseUhl(String sourceId, byte[] bytes) {
        literal(sourceId, "DTED_UHL_INVALID", "uhl", 0, bytes, 0, "UHL1", "sentinel");
        int longitude = coordinate(sourceId, "DTED_UHL_INVALID", "uhl", 4, bytes, 4, 3, false);
        int latitude = coordinate(sourceId, "DTED_UHL_INVALID", "uhl", 12, bytes, 12, 3, true);
        int longitudeInterval =
                digits(sourceId, "DTED_UHL_INVALID", "uhl", 20, bytes, 20, 4, "longitudeInterval");
        int latitudeInterval =
                digits(sourceId, "DTED_UHL_INVALID", "uhl", 24, bytes, 24, 4, "latitudeInterval");
        int columns =
                digits(sourceId, "DTED_UHL_INVALID", "uhl", 47, bytes, 47, 4, "longitudeCount");
        int rows = digits(sourceId, "DTED_UHL_INVALID", "uhl", 51, bytes, 51, 4, "latitudeCount");
        if (bytes[55] != '0' && bytes[55] != '1') {
            throw DtedFailures.field(
                    sourceId, "DTED_UHL_INVALID", "uhl", 55, "accuracySubregions", "grammar");
        }
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
        literal(sourceId, "DTED_DSI_INVALID", "dsi", 139, bytes, 59, "DTED", "series");
        int level = digit(sourceId, "DTED_DSI_INVALID", "dsi", 143, bytes[63], "level");
        if (level > 2) {
            throw DtedFailures.field(
                    sourceId, "DTED_PROFILE_UNSUPPORTED", "dsi", 143, "level", "unsupported");
        }
        literal(
                sourceId,
                "DTED_DSI_INVALID",
                "dsi",
                206,
                bytes,
                126,
                "PRF89020B",
                "productSpecification");
        String vertical =
                ascii(sourceId, "DTED_DSI_INVALID", "dsi", 221, bytes, 141, 3, "verticalDatum");
        String horizontal =
                ascii(sourceId, "DTED_DSI_INVALID", "dsi", 224, bytes, 144, 5, "horizontalDatum");
        int latitude = decimalCoordinate(sourceId, bytes, 185, 2, true);
        int longitude = decimalCoordinate(sourceId, bytes, 194, 3, false);
        int southWestLatitude =
                coordinate(sourceId, "DTED_DSI_INVALID", "dsi", 284, bytes, 204, 2, true);
        int southWestLongitude =
                coordinate(sourceId, "DTED_DSI_INVALID", "dsi", 291, bytes, 211, 3, false);
        int northWestLatitude =
                coordinate(sourceId, "DTED_DSI_INVALID", "dsi", 299, bytes, 219, 2, true);
        int northWestLongitude =
                coordinate(sourceId, "DTED_DSI_INVALID", "dsi", 306, bytes, 226, 3, false);
        int northEastLatitude =
                coordinate(sourceId, "DTED_DSI_INVALID", "dsi", 314, bytes, 234, 2, true);
        int northEastLongitude =
                coordinate(sourceId, "DTED_DSI_INVALID", "dsi", 321, bytes, 241, 3, false);
        int southEastLatitude =
                coordinate(sourceId, "DTED_DSI_INVALID", "dsi", 329, bytes, 249, 2, true);
        int southEastLongitude =
                coordinate(sourceId, "DTED_DSI_INVALID", "dsi", 336, bytes, 256, 3, false);
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
        return new Dsi(
                level,
                vertical,
                horizontal,
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

    private static Acc parseAcc(String sourceId, byte[] bytes) {
        literal(sourceId, "DTED_ACC_INVALID", "acc", 728, bytes, 0, "ACC", "sentinel");
        int subregions =
                digits(
                        sourceId,
                        "DTED_ACC_INVALID",
                        "acc",
                        783,
                        bytes,
                        55,
                        2,
                        "accuracySubregions");
        if (subregions == 1 || subregions > 9) {
            throw DtedFailures.field(
                    sourceId, "DTED_ACC_INVALID", "acc", 783, "accuracySubregions", "range");
        }
        return new Acc(subregions);
    }

    private static HeaderPlan reconcile(String sourceId, Uhl uhl, Dsi dsi, Acc acc) {
        if (uhl.longitude != dsi.longitude
                || uhl.latitude != dsi.latitude
                || uhl.longitudeInterval != dsi.longitudeInterval
                || uhl.latitudeInterval != dsi.latitudeInterval
                || uhl.columns != dsi.columns
                || uhl.rows != dsi.rows) {
            throw DtedFailures.field(
                    sourceId, "DTED_HEADER_INCONSISTENT", "dsi", 185, "duplicate", "mismatch");
        }
        boolean accHasSubregions = acc.subregions > 0;
        if (uhl.multipleAccuracy != accHasSubregions) {
            throw DtedFailures.field(
                    sourceId,
                    "DTED_HEADER_INCONSISTENT",
                    "acc",
                    783,
                    "accuracySubregions",
                    "mismatch");
        }
        if (accHasSubregions) {
            throw DtedFailures.field(
                    sourceId,
                    "DTED_PROFILE_UNSUPPORTED",
                    "acc",
                    783,
                    "accuracySubregions",
                    "unsupported");
        }
        if (!(dsi.vertical.equals("MSL") || dsi.vertical.equals("E96"))
                || !dsi.horizontal.equals("WGS84")
                || dsi.orientation != 0) {
            throw DtedFailures.field(
                    sourceId,
                    "DTED_PROFILE_UNSUPPORTED",
                    "dsi",
                    221,
                    "datumOrOrientation",
                    "unsupported");
        }
        if (dsi.longitude < -180 * 36_000
                || dsi.longitude > 179 * 36_000
                || dsi.latitude < -90 * 36_000
                || dsi.latitude > 89 * 36_000
                || dsi.longitude % 36_000 != 0
                || dsi.latitude % 36_000 != 0) {
            throw DtedFailures.field(
                    sourceId, "DTED_PROFILE_UNSUPPORTED", "dsi", 185, "origin", "unsupported");
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
            throw DtedFailures.field(
                    sourceId, "DTED_PROFILE_UNSUPPORTED", "dsi", 284, "grid", "unsupported");
        }
        int[] latitudeIntervals = {300, 30, 10};
        int[] rowCounts = {121, 1_201, 3_601};
        int zone = zone(dsi.latitude);
        int[][] longitudeIntervals = {
            {300, 600, 900, 1_200, 1_800},
            {30, 60, 90, 120, 180},
            {10, 20, 30, 40, 60}
        };
        int expectedLongitudeInterval = longitudeIntervals[dsi.level][zone];
        int expectedColumns = 36_000 / expectedLongitudeInterval + 1;
        if (dsi.latitudeInterval != latitudeIntervals[dsi.level]
                || dsi.longitudeInterval != expectedLongitudeInterval
                || dsi.rows != rowCounts[dsi.level]
                || dsi.columns != expectedColumns) {
            throw DtedFailures.field(
                    sourceId, "DTED_PROFILE_UNSUPPORTED", "dsi", 353, "grid", "unsupported");
        }
        if ((dsi.level < 2 && dsi.partial != 0) || dsi.partial < 0 || dsi.partial > 99) {
            throw DtedFailures.field(
                    sourceId, "DTED_PROFILE_UNSUPPORTED", "dsi", 369, "partialCell", "unsupported");
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

    private static void preflightLimits(
            SourceIdentity identity, HeaderPlan plan, ElevationSourceLimits limits) {
        requireLimit(identity.id(), "columns", plan.columns, limits.maximumColumns());
        requireLimit(identity.id(), "rows", plan.rows, limits.maximumRows());
        long samples;
        try {
            samples = Math.multiplyExact((long) plan.columns, plan.rows);
        } catch (ArithmeticException failure) {
            samples = Long.MAX_VALUE;
        }
        requireLimit(
                identity.id(),
                "samples",
                samples,
                Math.min(limits.maximumSamples(), (long) Integer.MAX_VALUE));
        long words = samples == Long.MAX_VALUE ? Long.MAX_VALUE : 1L + (samples - 1L) / 64L;
        long bytes;
        try {
            bytes = Math.addExact(Math.multiplyExact(samples, 8L), Math.multiplyExact(words, 8L));
        } catch (ArithmeticException failure) {
            bytes = Long.MAX_VALUE;
        }
        requireLimit(
                identity.id(), "retainedSampleBytes", bytes, limits.maximumRetainedSampleBytes());
    }

    private static void requireLimit(String sourceId, String limit, long requested, long maximum) {
        if (requested > maximum) {
            throw DtedFailures.limit(sourceId, limit, requested, maximum);
        }
    }

    private static long expectedSize(HeaderPlan plan) {
        try {
            long recordBytes = Math.addExact(12L, Math.multiplyExact(2L, plan.rows));
            return Math.addExact(HEADER_BYTES, Math.multiplyExact(plan.columns, recordBytes));
        } catch (ArithmeticException failure) {
            throw DtedFailures.field(
                    "dted", "DTED_PROFILE_UNSUPPORTED", "dted", -1, "size", "overflow");
        }
    }

    private static void readProfiles(
            String sourceId,
            DtedFileAccess access,
            HeaderPlan plan,
            double[] samples,
            BitSet noData,
            CancellationToken cancellation) {
        int recordBytes = Math.addExact(12, Math.multiplyExact(2, plan.rows));
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
                        sourceId, profile + 1L, position, "sentinel", "mismatch");
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
            for (int fileSample = 0; fileSample < plan.rows; fileSample++) {
                if ((fileSample & CHECKPOINT_MASK) == 0) {
                    checkpoint(sourceId, cancellation);
                }
                int word = unsigned16(record, 8 + fileSample * 2);
                int row = plan.rows - 1 - fileSample;
                int index = Math.toIntExact((long) row * plan.columns + profile);
                if (word == 0xffff) {
                    if (!plan.partial) {
                        throw DtedFailures.recordField(
                                sourceId,
                                profile + 1L,
                                position + 8L + fileSample * 2L,
                                "sample",
                                "voidInComplete");
                    }
                    noData.set(index);
                    samples[index] = 0.0;
                } else {
                    int magnitude = word & 0x7fff;
                    samples[index] =
                            (word & 0x8000) == 0 || magnitude == 0 ? magnitude : -magnitude;
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
                        80L + offset + degreeDigits,
                        bytes,
                        offset + degreeDigits,
                        2,
                        "origin");
        int seconds =
                digits(
                        sourceId,
                        "DTED_DSI_INVALID",
                        "dsi",
                        80L + offset + degreeDigits + 2,
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
                        80L + offset + degreeDigits + 5,
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
                80L + offset);
    }

    private static int coordinate(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int degreeDigits,
            boolean latitude) {
        int degrees =
                digits(
                        sourceId,
                        code,
                        component,
                        absoluteOffset,
                        bytes,
                        offset,
                        degreeDigits,
                        "coordinate");
        int minutes =
                digits(
                        sourceId,
                        code,
                        component,
                        absoluteOffset + degreeDigits,
                        bytes,
                        offset + degreeDigits,
                        2,
                        "coordinate");
        int seconds =
                digits(
                        sourceId,
                        code,
                        component,
                        absoluteOffset + degreeDigits + 2,
                        bytes,
                        offset + degreeDigits + 2,
                        2,
                        "coordinate");
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
                absoluteOffset);
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
            long offset) {
        int maximum = latitude ? 90 : 180;
        boolean positive = latitude ? hemisphere == 'N' : hemisphere == 'E';
        boolean negative = latitude ? hemisphere == 'S' : hemisphere == 'W';
        if ((!positive && !negative)
                || degrees > maximum
                || minutes >= 60
                || seconds >= 60
                || (degrees == maximum && (minutes != 0 || seconds != 0 || tenth != 0))) {
            throw DtedFailures.field(sourceId, code, component, offset, "coordinate", "range");
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
            value =
                    Math.addExact(
                            Math.multiplyExact(value, 10),
                            digit(
                                    sourceId,
                                    code,
                                    component,
                                    absoluteOffset + index,
                                    bytes[offset + index],
                                    field));
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

    private static String ascii(
            String sourceId,
            String code,
            String component,
            long absoluteOffset,
            byte[] bytes,
            int offset,
            int length,
            String field) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            int current = bytes[offset + index] & 0xff;
            if (current < 0x20 || current > 0x7e) {
                throw DtedFailures.field(
                        sourceId, code, component, absoluteOffset + index, field, "grammar");
            }
            value.append((char) current);
        }
        return value.toString();
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

    private record Acc(int subregions) {}

    private record Dsi(
            int level,
            String vertical,
            String horizontal,
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
}
