package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class GeoTiffParser {
    private static final int TYPE_SHORT = 3;
    private static final int TYPE_LONG = 4;
    private static final int TYPE_DOUBLE = 12;
    private static final Map<Integer, Boolean> TAGS = supportedTags();

    private final String sourceId;
    private final byte[] snapshot;
    private final GeoTiffLimits limits;
    private final CancellationToken cancellation;
    private final ByteBuffer data;
    private long ifdOffset;
    private long ifdLength;
    private long workingBytes;

    private GeoTiffParser(
            SourceIdentity identity,
            byte[] snapshot,
            GeoTiffRasterOptions options,
            CancellationToken cancellation) {
        sourceId = identity.id();
        this.snapshot = snapshot;
        limits = options.formatLimits();
        this.cancellation = cancellation;
        data = ByteBuffer.wrap(snapshot).order(ByteOrder.LITTLE_ENDIAN);
    }

    static RasterSource open(
            SourceIdentity identity,
            byte[] snapshot,
            GeoTiffRasterOptions options,
            CancellationToken cancellation) {
        return new GeoTiffParser(identity, snapshot, options, cancellation)
                .parse(identity, options);
    }

    private RasterSource parse(SourceIdentity identity, GeoTiffRasterOptions options) {
        if (snapshot.length < 8) {
            throw GeoTiffFailures.header(sourceId, "ifdOffset", "range");
        }
        if ((snapshot[0] & 0xff) != 'I' || (snapshot[1] & 0xff) != 'I') {
            if ((snapshot[0] & 0xff) == 'M' && (snapshot[1] & 0xff) == 'M') {
                throw GeoTiffFailures.header(sourceId, "byteOrder", "value");
            }
            throw GeoTiffFailures.header(sourceId, "byteOrder", "value");
        }
        int version = ushort(2);
        if (version == 43) {
            throw GeoTiffFailures.unsupported(sourceId, "bigTiff");
        }
        if (version != 42) {
            throw GeoTiffFailures.header(sourceId, "version", "value");
        }
        ifdOffset = uint(4);
        if (ifdOffset == 0 || (ifdOffset & 1) != 0) {
            throw GeoTiffFailures.header(
                    sourceId, "ifdOffset", ifdOffset == 0 ? "range" : "alignment");
        }
        requireRange(ifdOffset, 2, 0, "range");
        if (overlaps(ifdOffset, 2, 0, 8)) {
            throw GeoTiffFailures.header(sourceId, "ifdOffset", "range");
        }
        int entryCount = ushort((int) ifdOffset);
        GeoTiffFailures.limit(
                sourceId, "geoTiffOpen", "ifdEntries", entryCount, limits.maximumIfdEntries());
        if (entryCount == 0) {
            throw GeoTiffFailures.header(sourceId, "ifdCount", "value");
        }
        ifdLength = Math.addExact(6L, Math.multiplyExact(12L, entryCount));
        requireRange(ifdOffset, ifdLength, 0, "range");
        if (overlaps(ifdOffset, ifdLength, 0, 8)) {
            throw GeoTiffFailures.header(sourceId, "ifdOffset", "range");
        }
        chargeWorking(Math.multiplyExact(64L, entryCount));
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        Map<Integer, Entry> entries = new LinkedHashMap<>();
        int previous = -1;
        for (int index = 0; index < entryCount; index++) {
            GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            int offset = Math.toIntExact(ifdOffset + 2L + index * 12L);
            int tag = ushort(offset);
            if (tag <= previous) {
                throw GeoTiffFailures.tag(sourceId, tag, tag == previous ? "duplicate" : "order");
            }
            previous = tag;
            if (!TAGS.containsKey(tag)) {
                throw GeoTiffFailures.unsupported(sourceId, tag == 330 ? "subIfd" : "tag");
            }
            Entry entry = new Entry(tag, ushort(offset + 2), uint(offset + 4), uint(offset + 8));
            entry.validateRange();
            entries.put(tag, entry);
        }
        validateTagPayloadDisjoint(entries);
        long nextOffset = uint(Math.toIntExact(ifdOffset + 2L + 12L * entryCount));
        validateNextIfd(nextOffset);

        int width = scalar(entries, 256, true);
        int height = scalar(entries, 257, true);
        GeoTiffFailures.limit(
                sourceId, "geoTiffOpen", "dimension", width, limits.maximumDimension());
        GeoTiffFailures.limit(
                sourceId, "geoTiffOpen", "dimension", height, limits.maximumDimension());
        long pixels = Math.multiplyExact((long) width, height);
        GeoTiffFailures.limit(sourceId, "geoTiffOpen", "pixels", pixels, limits.maximumPixels());
        requireScalar(entries, 258, 8, "sampleType");
        requireOptionalScalar(entries, 259, 1, "compression");
        requireScalar(entries, 262, 1, "photometric");
        requireOptionalScalar(entries, 274, 1, "orientation");
        requireOptionalScalar(entries, 277, 1, "sampleOrganization");
        requireOptionalScalar(entries, 284, 1, "sampleOrganization");
        requireOptionalScalar(entries, 317, 1, "predictor");
        if (entries.containsKey(322)
                || entries.containsKey(323)
                || entries.containsKey(324)
                || entries.containsKey(325)) {
            throw GeoTiffFailures.unsupported(sourceId, "sampleOrganization");
        }

        int rowsPerStrip = scalar(entries, 278, true);
        if (rowsPerStrip <= 0) {
            throw GeoTiffFailures.tag(sourceId, 278, "value");
        }
        int segmentCount = Math.toIntExact((height + (long) rowsPerStrip - 1) / rowsPerStrip);
        GeoTiffFailures.limit(
                sourceId, "geoTiffOpen", "segments", segmentCount, limits.maximumSegments());
        long[] offsets = vector(entries, 273, segmentCount);
        long[] counts = vector(entries, 279, segmentCount);
        for (int segment = 0; segment < segmentCount; segment++) {
            GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            int rows = Math.min(rowsPerStrip, height - segment * rowsPerStrip);
            long decoded = Math.multiplyExact((long) width, rows);
            GeoTiffFailures.limit(
                    sourceId,
                    "geoTiffOpen",
                    "decodedSegmentBytes",
                    decoded,
                    limits.maximumDecodedSegmentBytes());
            GeoTiffFailures.limit(
                    sourceId,
                    "geoTiffOpen",
                    "encodedSegmentBytes",
                    counts[segment],
                    limits.maximumEncodedSegmentBytes());
            if (counts[segment] != decoded) {
                throw GeoTiffFailures.segment(sourceId, segment, "decodedLength");
            }
            if ((offsets[segment] & 1) != 0) {
                throw GeoTiffFailures.segment(sourceId, segment, "alignment");
            }
            if (!rangeInside(offsets[segment], counts[segment])) {
                throw GeoTiffFailures.segment(sourceId, segment, "range");
            }
            if (offsets[segment] == 0) {
                throw GeoTiffFailures.segment(sourceId, segment, "range");
            }
            if (overlaps(offsets[segment], counts[segment], 0, 8)
                    || overlaps(offsets[segment], counts[segment], ifdOffset, ifdLength)
                    || overlapsAnyTagPayload(offsets[segment], counts[segment], entries)) {
                throw GeoTiffFailures.segment(sourceId, segment, "overlap");
            }
        }
        validateSegmentDisjoint(offsets, counts);

        GeoReference reference = georeference(entries, width, height);
        CrsMetadata crs = geoKeys(entries);
        RasterSourceMetadata metadata =
                new RasterSourceMetadata(
                        identity, width, height, Optional.of(reference.bounds()), Optional.of(crs));
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        return new GeoTiffRasterSource(
                snapshot, metadata, options.requestLimits(), limits, rowsPerStrip, offsets, counts);
    }

    private GeoReference georeference(Map<Integer, Entry> entries, int width, int height) {
        if (!entries.containsKey(33550) || !entries.containsKey(33922)) {
            throw GeoTiffFailures.georeference(sourceId, "missing");
        }
        double[] scale = doubles(entries.get(33550), 3);
        double[] tie = doubles(entries.get(33922), 6);
        for (double value : scale) {
            if (!Double.isFinite(value)) {
                throw GeoTiffFailures.georeference(sourceId, "nonFinite");
            }
        }
        for (double value : tie) {
            if (!Double.isFinite(value)) {
                throw GeoTiffFailures.georeference(sourceId, "nonFinite");
            }
        }
        if (!(scale[0] > 0) || !(scale[1] > 0) || scale[2] != 0 || tie[2] != 0 || tie[5] != 0) {
            throw GeoTiffFailures.georeference(sourceId, "orientation");
        }
        double west = Math.fma(-tie[0], scale[0], tie[3]);
        double east = Math.fma(width - tie[0], scale[0], tie[3]);
        double north = Math.fma(tie[1], scale[1], tie[4]);
        double south = Math.fma(tie[1] - height, scale[1], tie[4]);
        if (!Double.isFinite(west)
                || !Double.isFinite(east)
                || !Double.isFinite(north)
                || !Double.isFinite(south)) {
            throw GeoTiffFailures.georeference(sourceId, "nonFinite");
        }
        if (!(east > west) || !(north > south)) {
            throw GeoTiffFailures.georeference(sourceId, "collapsed");
        }
        Envelope bounds = new Envelope(west, south, east, north);
        Envelope domain = CrsDefinitions.EPSG_4326.coordinateDomain();
        if (bounds.minX() < domain.minX()
                || bounds.minY() < domain.minY()
                || bounds.maxX() > domain.maxX()
                || bounds.maxY() > domain.maxY()) {
            throw GeoTiffFailures.georeference(sourceId, "orientation");
        }
        return new GeoReference(bounds);
    }

    private CrsMetadata geoKeys(Map<Integer, Entry> entries) {
        Entry entry = entries.get(34735);
        if (entry == null) {
            throw GeoTiffFailures.tag(sourceId, 34735, "missing");
        }
        if (entry.type != TYPE_SHORT) {
            throw GeoTiffFailures.tag(sourceId, 34735, "type");
        }
        if (entry.count < 4) {
            throw GeoTiffFailures.tag(sourceId, 34735, "count");
        }
        if (entry.unsignedAt(0) != 1
                || entry.unsignedAt(1) != 1
                || (entry.unsignedAt(2) != 0 && entry.unsignedAt(2) != 1)) {
            throw GeoTiffFailures.geokey(sourceId, null, "header");
        }
        int keys = Math.toIntExact(entry.unsignedAt(3));
        GeoTiffFailures.limit(sourceId, "geoTiffOpen", "geoKeys", keys, limits.maximumGeoKeys());
        long expectedCount = Math.addExact(4L, Math.multiplyExact(4L, keys));
        if (entry.count != expectedCount) {
            throw GeoTiffFailures.tag(sourceId, 34735, "count");
        }
        int previous = -1;
        int model = -1;
        int raster = -1;
        int geographic = -1;
        for (int index = 0; index < keys; index++) {
            GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            int base = 4 + index * 4;
            int key = Math.toIntExact(entry.unsignedAt(base));
            if (key <= previous) {
                throw GeoTiffFailures.geokey(
                        sourceId, key, key == previous ? "duplicate" : "order");
            }
            previous = key;
            if (entry.unsignedAt(base + 1) != 0 || entry.unsignedAt(base + 2) != 1) {
                throw GeoTiffFailures.geokey(
                        sourceId, key, entry.unsignedAt(base + 1) != 0 ? "location" : "count");
            }
            int value = Math.toIntExact(entry.unsignedAt(base + 3));
            switch (key) {
                case 1024 -> model = value;
                case 1025 -> raster = value;
                case 2048 -> geographic = value;
                case 2054 -> {
                    if (value != 9102) {
                        throw GeoTiffFailures.geokey(sourceId, key, "value");
                    }
                }
                default ->
                        throw GeoTiffFailures.unsupported(
                                sourceId, key >= 4096 && key <= 4099 ? "verticalCrs" : "geoKey");
            }
        }
        if (model != 2 || geographic != 4326) {
            throw GeoTiffFailures.unsupported(sourceId, "horizontalCrs");
        }
        if (raster != 1) {
            throw GeoTiffFailures.unsupported(sourceId, "route");
        }
        return CrsMetadata.recognized(CrsDefinitions.EPSG_4326, Optional.empty(), Optional.empty());
    }

    private int scalar(Map<Integer, Entry> entries, int tag, boolean required) {
        Entry entry = entries.get(tag);
        if (entry == null) {
            if (required) {
                throw GeoTiffFailures.tag(sourceId, tag, "missing");
            }
            return -1;
        }
        if (entry.count != 1) {
            throw GeoTiffFailures.tag(sourceId, tag, "count");
        }
        long value = entry.unsignedAt(0);
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw GeoTiffFailures.tag(sourceId, tag, "value");
        }
        return (int) value;
    }

    private void requireScalar(
            Map<Integer, Entry> entries, int tag, int expected, String construct) {
        int actual = scalar(entries, tag, true);
        if (actual != expected) {
            throw GeoTiffFailures.unsupported(sourceId, construct);
        }
    }

    private void requireOptionalScalar(
            Map<Integer, Entry> entries, int tag, int expected, String construct) {
        int actual = scalar(entries, tag, false);
        if (actual != -1 && actual != expected) {
            throw GeoTiffFailures.unsupported(sourceId, construct);
        }
    }

    private long[] vector(Map<Integer, Entry> entries, int tag, int count) {
        Entry entry = entries.get(tag);
        if (entry == null) {
            throw GeoTiffFailures.tag(sourceId, tag, "missing");
        }
        if (entry.count != count) {
            throw GeoTiffFailures.tag(sourceId, tag, "count");
        }
        chargeWorking(Math.multiplyExact(8L, count));
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        long[] values = new long[count];
        for (int index = 0; index < count; index++) {
            if ((index & 4095) == 0) {
                GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            }
            values[index] = entry.unsignedAt(index);
        }
        return values;
    }

    private double[] doubles(Entry entry, int count) {
        if (entry.type != TYPE_DOUBLE) {
            throw GeoTiffFailures.tag(sourceId, entry.tag, "type");
        }
        if (entry.count != count) {
            throw GeoTiffFailures.tag(sourceId, entry.tag, "count");
        }
        int offset = entry.valueOffset();
        chargeWorking(Math.multiplyExact(8L, count));
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = data.getDouble(offset + i * 8);
        }
        return values;
    }

    private void requireRange(long offset, long length, int tag, String reason) {
        if (!rangeInside(offset, length)) {
            throw tag == 0
                    ? GeoTiffFailures.header(sourceId, "ifdOffset", reason)
                    : GeoTiffFailures.tag(sourceId, tag, reason);
        }
    }

    private boolean rangeInside(long offset, long length) {
        return offset >= 0
                && length >= 0
                && offset <= snapshot.length
                && length <= snapshot.length - offset;
    }

    private void chargeWorking(long bytes) {
        long requested;
        try {
            requested = Math.addExact(workingBytes, bytes);
        } catch (ArithmeticException failure) {
            requested = Long.MAX_VALUE;
        }
        GeoTiffFailures.limit(
                sourceId, "geoTiffOpen", "workingBytes", requested, limits.maximumWorkingBytes());
        workingBytes = requested;
    }

    private void validateNextIfd(long nextOffset) {
        if (nextOffset == 0) {
            return;
        }
        if ((nextOffset & 1) != 0) {
            throw GeoTiffFailures.header(sourceId, "nextIfd", "alignment");
        }
        if (!rangeInside(nextOffset, 2)
                || overlaps(nextOffset, 2, 0, 8)
                || overlaps(nextOffset, 2, ifdOffset, ifdLength)) {
            throw GeoTiffFailures.header(sourceId, "nextIfd", "range");
        }
        throw GeoTiffFailures.unsupported(sourceId, "multipleIfd");
    }

    private void validateTagPayloadDisjoint(Map<Integer, Entry> entries) {
        for (Entry current : entries.values()) {
            GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            if (!current.outOfLine()) {
                continue;
            }
            for (Entry other : entries.values()) {
                GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
                if (other == current) {
                    break;
                }
                if (other.outOfLine()
                        && overlaps(
                                current.rawValue,
                                current.payloadBytes(),
                                other.rawValue,
                                other.payloadBytes())) {
                    throw GeoTiffFailures.tag(sourceId, current.tag, "overlap");
                }
            }
        }
    }

    private boolean overlapsAnyTagPayload(long offset, long length, Map<Integer, Entry> entries) {
        for (Entry entry : entries.values()) {
            GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            if (entry.outOfLine()
                    && overlaps(offset, length, entry.rawValue, entry.payloadBytes())) {
                return true;
            }
        }
        return false;
    }

    private void validateSegmentDisjoint(long[] offsets, long[] counts) {
        chargeWorking(Math.multiplyExact(8L, offsets.length));
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        long[] order = new long[offsets.length];
        for (int segment = 0; segment < offsets.length; segment++) {
            if ((segment & 4095) == 0) {
                GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            }
            order[segment] = (offsets[segment] << 32) | Integer.toUnsignedLong(segment);
        }
        Arrays.sort(order);
        long previousEnd = -1;
        for (int position = 0; position < order.length; position++) {
            if ((position & 4095) == 0) {
                GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            }
            int segment = (int) order[position];
            long start = offsets[segment];
            if (start < previousEnd) {
                throw GeoTiffFailures.segment(sourceId, segment, "overlap");
            }
            previousEnd = Math.addExact(start, counts[segment]);
        }
    }

    private static boolean overlaps(
            long firstOffset, long firstLength, long secondOffset, long secondLength) {
        return firstOffset < secondOffset + secondLength
                && secondOffset < firstOffset + firstLength;
    }

    private int ushort(int offset) {
        return Short.toUnsignedInt(data.getShort(offset));
    }

    private long uint(int offset) {
        return Integer.toUnsignedLong(data.getInt(offset));
    }

    private static Map<Integer, Boolean> supportedTags() {
        Map<Integer, Boolean> tags = new LinkedHashMap<>();
        for (int tag :
                new int[] {
                    256, 257, 258, 259, 262, 273, 274, 277, 278, 279, 284, 317, 322, 323, 324, 325,
                    33550, 33922, 34735
                }) {
            tags.put(tag, true);
        }
        return Map.copyOf(tags);
    }

    private final class Entry {
        private final int tag;
        private final int type;
        private final long count;
        private final long rawValue;

        private Entry(int tag, int type, long count, long rawValue) {
            this.tag = tag;
            this.type = type;
            this.count = count;
            this.rawValue = rawValue;
        }

        private void validateRange() {
            int size =
                    type == TYPE_SHORT ? 2 : type == TYPE_LONG ? 4 : type == TYPE_DOUBLE ? 8 : -1;
            if (size < 0) {
                throw GeoTiffFailures.tag(sourceId, tag, "type");
            }
            long bytes;
            try {
                bytes = Math.multiplyExact(count, size);
            } catch (ArithmeticException failure) {
                throw GeoTiffFailures.tag(sourceId, tag, "range");
            }
            if (count == 0) {
                throw GeoTiffFailures.tag(sourceId, tag, "count");
            }
            if (bytes > 4) {
                GeoTiffFailures.limit(
                        sourceId,
                        "geoTiffOpen",
                        "tagPayloadBytes",
                        bytes,
                        limits.maximumTagPayloadBytes());
                if (rawValue == 0) {
                    throw GeoTiffFailures.tag(sourceId, tag, "range");
                }
                if ((rawValue & 1) != 0) {
                    throw GeoTiffFailures.tag(sourceId, tag, "alignment");
                }
                requireRange(rawValue, bytes, tag, "range");
                if (overlaps(rawValue, bytes, 0, 8)
                        || overlaps(rawValue, bytes, ifdOffset, ifdLength)) {
                    throw GeoTiffFailures.tag(sourceId, tag, "overlap");
                }
            }
        }

        private long payloadBytes() {
            int size = type == TYPE_SHORT ? 2 : type == TYPE_LONG ? 4 : 8;
            return Math.multiplyExact(count, size);
        }

        private boolean outOfLine() {
            return payloadBytes() > 4;
        }

        private int valueOffset() {
            long bytes =
                    Math.multiplyExact(count, type == TYPE_SHORT ? 2 : type == TYPE_LONG ? 4 : 8);
            return bytes <= 4 ? -1 : Math.toIntExact(rawValue);
        }

        private long unsignedAt(int index) {
            if (type != TYPE_SHORT && type != TYPE_LONG) {
                throw GeoTiffFailures.tag(sourceId, tag, "type");
            }
            if (index < 0 || index >= count) {
                throw new IndexOutOfBoundsException("TIFF value index is outside its entry");
            }
            int size = type == TYPE_SHORT ? 2 : 4;
            int offset = valueOffset();
            if (offset < 0) {
                return type == TYPE_SHORT ? (rawValue >>> (index * 16)) & 0xffffL : rawValue;
            }
            return type == TYPE_SHORT ? ushort(offset + index * size) : uint(offset + index * size);
        }
    }

    private record GeoReference(Envelope bounds) {}
}
