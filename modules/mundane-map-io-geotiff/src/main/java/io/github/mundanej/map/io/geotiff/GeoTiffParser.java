package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterPlacementException;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.PackedElevationGrid;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class GeoTiffParser {
    private static final int TYPE_ASCII = 2;
    private static final int TYPE_SHORT = 3;
    private static final int TYPE_LONG = 4;
    private static final int TYPE_DOUBLE = 12;
    private static final Map<Integer, Boolean> TAGS = supportedTags();

    private final String sourceId;
    private byte[] snapshot;
    private final GeoTiffLimits limits;
    private final CancellationToken cancellation;
    private ByteBuffer data;
    private long ifdOffset;
    private long ifdLength;
    private long workingBytes;

    private GeoTiffParser(
            SourceIdentity identity,
            byte[] snapshot,
            GeoTiffLimits limits,
            CancellationToken cancellation) {
        sourceId = identity.id();
        this.snapshot = snapshot;
        this.limits = limits;
        this.cancellation = cancellation;
        ByteOrder byteOrder =
                snapshot.length >= 2 && snapshot[0] == 'M' && snapshot[1] == 'M'
                        ? ByteOrder.BIG_ENDIAN
                        : ByteOrder.LITTLE_ENDIAN;
        data = ByteBuffer.wrap(snapshot).order(byteOrder);
    }

    static RasterSource open(
            SourceIdentity identity,
            byte[] snapshot,
            GeoTiffRasterOptions options,
            CancellationToken cancellation) {
        return (RasterSource)
                new GeoTiffParser(identity, snapshot, options.formatLimits(), cancellation)
                        .parse(identity, options, null);
    }

    static ElevationSource openElevation(
            SourceIdentity identity,
            byte[] snapshot,
            GeoTiffElevationOptions options,
            CancellationToken cancellation) {
        return (ElevationSource)
                new GeoTiffParser(identity, snapshot, options.formatLimits(), cancellation)
                        .parse(identity, null, options);
    }

    private Object parse(
            SourceIdentity identity,
            GeoTiffRasterOptions rasterOptions,
            GeoTiffElevationOptions elevationOptions) {
        boolean elevation = elevationOptions != null;
        if (snapshot.length < 8) {
            throw GeoTiffFailures.header(sourceId, "ifdOffset", "range");
        }
        boolean littleEndian = snapshot[0] == 'I' && snapshot[1] == 'I';
        boolean bigEndian = snapshot[0] == 'M' && snapshot[1] == 'M';
        if (!littleEndian && !bigEndian) {
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
            Entry entry =
                    new Entry(
                            tag,
                            ushort(offset + 2),
                            uint(offset + 4),
                            uint(offset + 8),
                            offset + 8);
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
        int compression = optionalCode(entries, 259);
        if (compression == -1) {
            compression = 1;
        }
        if (compression != 1 && compression != 8 && compression != 32773) {
            throw GeoTiffFailures.unsupportedCompression(sourceId, compression);
        }
        requireOptionalCode(entries, 274, 1, "orientation");
        int samplesPerPixel = optionalCode(entries, 277);
        if (samplesPerPixel == -1) {
            samplesPerPixel = 1;
        }
        requireOptionalCode(entries, 284, 1, "sampleOrganization");
        requireOptionalCode(entries, 317, 1, "predictor");
        CrsMetadata crs = geoKeys(entries, elevation ? 2 : 1);
        int bytesPerCell;
        NumericSampleProfile elevationProfile = null;
        NoDataPolicy noData = NoDataPolicy.none();
        ColorProfile color;
        if (elevation) {
            elevationProfile = elevationProfile(entries, samplesPerPixel);
            bytesPerCell = elevationProfile.sampleBytes();
            noData = noDataPolicy(entries, elevationProfile);
            color = null;
        } else {
            if (entries.containsKey(42113)) {
                throw GeoTiffFailures.unsupported(sourceId, "rasterNoData");
            }
            bytesPerCell = samplesPerPixel;
            color = colorProfile(entries, samplesPerPixel);
        }

        SegmentLayout layout = segmentLayout(entries, width, height);
        int segmentCount = layout.segmentCount();
        GeoTiffFailures.limit(
                sourceId, "geoTiffOpen", "segments", segmentCount, limits.maximumSegments());
        long[] offsets = vector(entries, layout.offsetTag(), segmentCount);
        long[] counts = vector(entries, layout.countTag(), segmentCount);
        chargeWorking(Math.multiplyExact(8L, segmentCount));
        long[] decodedCounts = new long[segmentCount];
        for (int segment = 0; segment < segmentCount; segment++) {
            GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            long decoded = decodedBytes(layout, segment, width, height, bytesPerCell);
            decodedCounts[segment] = decoded;
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
            if (counts[segment] == 0) {
                throw GeoTiffFailures.segment(sourceId, segment, "encodedLength");
            }
            if (compression == 1 && counts[segment] != decoded) {
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

        if (elevation) {
            ElevationSourceMetadata metadata =
                    elevationMetadata(identity, entries, width, height, crs, elevationOptions);
            return decodeElevation(
                    metadata,
                    elevationOptions,
                    width,
                    height,
                    elevationProfile,
                    noData,
                    layout,
                    compression,
                    offsets,
                    counts,
                    decodedCounts);
        }
        GeoReference reference = georeference(entries, width, height);
        RasterSourceMetadata metadata = metadata(identity, width, height, reference, crs);
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        return new GeoTiffRasterSource(
                snapshot,
                metadata,
                rasterOptions.requestLimits(),
                limits,
                layout.tiled(),
                layout.segmentWidth(),
                layout.segmentHeight(),
                layout.segmentsAcross(),
                samplesPerPixel,
                color,
                compression,
                offsets,
                counts,
                decodedCounts);
    }

    private GeoReference georeference(Map<Integer, Entry> entries, int width, int height) {
        boolean hasScale = entries.containsKey(33550);
        boolean hasTie = entries.containsKey(33922);
        boolean transformation = entries.containsKey(34264);
        if (transformation && (hasScale || hasTie)) {
            throw GeoTiffFailures.georeference(sourceId, "conflict");
        }
        if (transformation) {
            return transformation(entries.get(34264), width, height);
        }
        if (!hasScale || !hasTie) {
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
        return new GeoReference(
                RasterGridPlacement.axisAligned(new Envelope(west, south, east, north)));
    }

    private GeoReference transformation(Entry entry, int width, int height) {
        double[] matrix = doubles(entry, 16);
        for (double value : matrix) {
            if (!Double.isFinite(value)) {
                throw GeoTiffFailures.georeference(sourceId, "nonFinite");
            }
        }
        if (matrix[12] != 0 || matrix[13] != 0 || matrix[14] != 0 || matrix[15] != 1) {
            throw GeoTiffFailures.unsupported(sourceId, "georeference");
        }
        if (matrix[2] != 0
                || matrix[6] != 0
                || matrix[8] != 0
                || matrix[9] != 0
                || matrix[10] != 1
                || matrix[11] != 0) {
            throw GeoTiffFailures.unsupported(sourceId, "georeference");
        }
        double centerX = Math.fma(0.5, matrix[0], Math.fma(0.5, matrix[1], matrix[3]));
        double centerY = Math.fma(0.5, matrix[4], Math.fma(0.5, matrix[5], matrix[7]));
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)) {
            throw GeoTiffFailures.georeference(sourceId, "nonFinite");
        }
        RasterAffineTransform affine;
        try {
            affine =
                    RasterAffineTransform.of(
                            matrix[0], matrix[4], matrix[1], matrix[5], centerX, centerY);
        } catch (RasterPlacementException failure) {
            throw placementFailure(failure);
        }
        if (matrix[0] > 0 && matrix[1] == 0 && matrix[4] == 0 && matrix[5] < 0) {
            double west = matrix[3];
            double east = Math.fma(width, matrix[0], matrix[3]);
            double north = matrix[7];
            double south = Math.fma(height, matrix[5], matrix[7]);
            if (!Double.isFinite(east) || !Double.isFinite(south)) {
                throw GeoTiffFailures.georeference(sourceId, "nonFinite");
            }
            if (!(east > west) || !(north > south)) {
                throw GeoTiffFailures.georeference(sourceId, "collapsed");
            }
            return new GeoReference(
                    RasterGridPlacement.axisAligned(new Envelope(west, south, east, north)));
        }
        return new GeoReference(RasterGridPlacement.affine(affine));
    }

    private RasterSourceMetadata metadata(
            SourceIdentity identity,
            int width,
            int height,
            GeoReference reference,
            CrsMetadata crs) {
        RasterSourceMetadata metadata;
        try {
            metadata =
                    RasterSourceMetadata.withPlacement(
                            identity, width, height, reference.placement(), Optional.of(crs));
        } catch (RasterPlacementException failure) {
            throw placementFailure(failure);
        }
        Envelope bounds = metadata.mapBounds().orElseThrow();
        Envelope domain = crs.definition().orElseThrow().coordinateDomain();
        if (bounds.minX() < domain.minX()
                || bounds.minY() < domain.minY()
                || bounds.maxX() > domain.maxX()
                || bounds.maxY() > domain.maxY()) {
            throw GeoTiffFailures.georeference(sourceId, "orientation");
        }
        return metadata;
    }

    private RuntimeException placementFailure(RasterPlacementException failure) {
        return switch (failure.reason()) {
            case SINGULAR -> GeoTiffFailures.georeference(sourceId, "singular");
            case ENVELOPE_NON_POSITIVE -> GeoTiffFailures.georeference(sourceId, "collapsed");
            case INVERSE_NON_FINITE, CORNER_NON_FINITE, ENVELOPE_NON_FINITE ->
                    GeoTiffFailures.georeference(sourceId, "nonFinite");
        };
    }

    private CrsMetadata geoKeys(Map<Integer, Entry> entries, int expectedRasterType) {
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
        int projected = -1;
        boolean angularUnits = false;
        boolean linearUnits = false;
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
                    angularUnits = true;
                    if (value != 9102) {
                        throw GeoTiffFailures.geokey(sourceId, key, "value");
                    }
                }
                case 3072 -> projected = value;
                case 3076 -> {
                    linearUnits = true;
                    if (value != 9001) {
                        throw GeoTiffFailures.geokey(sourceId, key, "value");
                    }
                }
                default ->
                        throw GeoTiffFailures.unsupported(
                                sourceId, key >= 4096 && key <= 4099 ? "verticalCrs" : "geoKey");
            }
        }
        if (raster != expectedRasterType) {
            throw GeoTiffFailures.unsupported(sourceId, "route");
        }
        if (model == 2 && geographic == 4326 && projected == -1 && !linearUnits) {
            return CrsMetadata.recognized(
                    CrsDefinitions.EPSG_4326, Optional.empty(), Optional.empty());
        }
        if (model == 1
                && projected == 3857
                && (geographic == -1 || geographic == 4326)
                && !angularUnits) {
            return CrsMetadata.recognized(
                    CrsDefinitions.EPSG_3857, Optional.empty(), Optional.empty());
        }
        throw GeoTiffFailures.unsupported(sourceId, "horizontalCrs");
    }

    private NumericSampleProfile elevationProfile(
            Map<Integer, Entry> entries, int samplesPerPixel) {
        if (samplesPerPixel != 1 || code(entries, 262) != 1 || entries.containsKey(338)) {
            throw GeoTiffFailures.unsupported(sourceId, "photometric");
        }
        Entry bits = entries.get(258);
        if (bits == null) {
            throw GeoTiffFailures.tag(sourceId, 258, "missing");
        }
        if (bits.type != TYPE_SHORT || bits.count != 1) {
            throw GeoTiffFailures.tag(sourceId, 258, bits.type != TYPE_SHORT ? "type" : "count");
        }
        Entry format = entries.get(339);
        if (format == null) {
            throw GeoTiffFailures.tag(sourceId, 339, "missing");
        }
        if (format.type != TYPE_SHORT || format.count != 1) {
            throw GeoTiffFailures.tag(sourceId, 339, format.type != TYPE_SHORT ? "type" : "count");
        }
        long bitCount = bits.unsignedAt(0);
        long sampleFormat = format.unsignedAt(0);
        SampleKind kind =
                sampleFormat == 2 && bitCount == 16
                        ? SampleKind.INT16
                        : sampleFormat == 2 && bitCount == 32
                                ? SampleKind.INT32
                                : sampleFormat == 3 && bitCount == 32
                                        ? SampleKind.FLOAT32
                                        : sampleFormat == 3 && bitCount == 64
                                                ? SampleKind.FLOAT64
                                                : null;
        if (kind == null) {
            throw GeoTiffFailures.unsupported(sourceId, "sampleType");
        }
        return new NumericSampleProfile(Math.toIntExact(bitCount / Byte.SIZE), kind);
    }

    private NoDataPolicy noDataPolicy(Map<Integer, Entry> entries, NumericSampleProfile profile) {
        Entry entry = entries.get(42113);
        if (entry == null) {
            return NoDataPolicy.none();
        }
        if (entry.type != TYPE_ASCII) {
            throw GeoTiffFailures.tag(sourceId, 42113, "type");
        }
        if (entry.count < 2 || entry.count > Integer.MAX_VALUE) {
            throw GeoTiffFailures.tag(sourceId, 42113, "count");
        }
        GeoTiffFailures.limit(
                sourceId, "geoTiffOpen", "noDataBytes", entry.count, limits.maximumNoDataBytes());
        int length = Math.toIntExact(entry.count);
        chargeWorking(Math.multiplyExact(2L, length - 1L));
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        if (entry.byteAt(length - 1) != 0) {
            throw GeoTiffFailures.tag(sourceId, 42113, "encoding");
        }
        StringBuilder token = new StringBuilder(length - 1);
        for (int index = 0; index < length - 1; index++) {
            int value = entry.byteAt(index);
            if (value == 0 || value > 0x7f) {
                throw GeoTiffFailures.tag(sourceId, 42113, "encoding");
            }
            token.append((char) value);
        }
        String text = token.toString();
        if (text.equals("nan") && profile.kind().floating()) {
            return NoDataPolicy.nan();
        }
        if (!decimalToken(text, profile.kind().floating())) {
            throw GeoTiffFailures.tag(sourceId, 42113, "value");
        }
        try {
            return NoDataPolicy.finite(profile.kind().parseSentinel(text));
        } catch (NumberFormatException failure) {
            throw GeoTiffFailures.tag(sourceId, 42113, "value");
        }
    }

    private static boolean decimalToken(String text, boolean floating) {
        if (text.isEmpty()) {
            return false;
        }
        int index = text.charAt(0) == '+' || text.charAt(0) == '-' ? 1 : 0;
        if (index == text.length()) {
            return false;
        }
        boolean digit = false;
        boolean point = false;
        boolean exponent = false;
        for (; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value >= '0' && value <= '9') {
                digit = true;
                continue;
            }
            if (floating && value == '.' && !point && !exponent) {
                point = true;
                continue;
            }
            if (floating && (value == 'e' || value == 'E') && digit && !exponent) {
                exponent = true;
                digit = false;
                if (index + 1 < text.length()
                        && (text.charAt(index + 1) == '+' || text.charAt(index + 1) == '-')) {
                    index++;
                }
                continue;
            }
            return false;
        }
        return digit;
    }

    private ElevationSourceMetadata elevationMetadata(
            SourceIdentity identity,
            Map<Integer, Entry> entries,
            int width,
            int height,
            CrsMetadata crs,
            GeoTiffElevationOptions options) {
        if (entries.containsKey(34264)) {
            throw GeoTiffFailures.unsupported(sourceId, "georeference");
        }
        Entry scaleEntry = entries.get(33550);
        Entry tieEntry = entries.get(33922);
        if (scaleEntry == null || tieEntry == null) {
            throw GeoTiffFailures.georeference(sourceId, "missing");
        }
        double[] scale = doubles(scaleEntry, 3);
        double[] tie = doubles(tieEntry, 6);
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
        if (!(scale[0] > 0.0)
                || !(scale[1] > 0.0)
                || scale[2] != 0.0
                || tie[2] != 0.0
                || tie[5] != 0.0) {
            throw GeoTiffFailures.georeference(sourceId, "orientation");
        }
        double west = Math.fma(-tie[0], scale[0], tie[3]);
        double east = Math.fma(width - 1.0 - tie[0], scale[0], tie[3]);
        double north = Math.fma(tie[1], scale[1], tie[4]);
        double south = Math.fma(tie[1] - (height - 1.0), scale[1], tie[4]);
        if (!Double.isFinite(west)
                || !Double.isFinite(east)
                || !Double.isFinite(north)
                || !Double.isFinite(south)) {
            throw GeoTiffFailures.georeference(sourceId, "nonFinite");
        }
        ElevationSourceMetadata metadata;
        try {
            metadata =
                    new ElevationSourceMetadata(
                            identity,
                            width,
                            height,
                            new Envelope(west, south, east, north),
                            crs,
                            options.elevationUnit());
        } catch (IllegalArgumentException failure) {
            throw GeoTiffFailures.georeference(sourceId, "collapsed");
        }
        Envelope domain = crs.definition().orElseThrow().coordinateDomain();
        Envelope bounds = metadata.sampleBounds();
        if (bounds.minX() < domain.minX()
                || bounds.minY() < domain.minY()
                || bounds.maxX() > domain.maxX()
                || bounds.maxY() > domain.maxY()) {
            throw GeoTiffFailures.georeference(sourceId, "orientation");
        }
        return metadata;
    }

    private ElevationSource decodeElevation(
            ElevationSourceMetadata metadata,
            GeoTiffElevationOptions options,
            int width,
            int height,
            NumericSampleProfile profile,
            NoDataPolicy noDataPolicy,
            SegmentLayout layout,
            int compression,
            long[] offsets,
            long[] counts,
            long[] decodedCounts) {
        long sampleCount = metadata.sampleCount();
        ElevationSourceLimits sourceLimits = options.sourceLimits();
        GeoTiffFailures.limit(
                sourceId, "elevationOpen", "columns", width, sourceLimits.maximumColumns());
        GeoTiffFailures.limit(
                sourceId, "elevationOpen", "rows", height, sourceLimits.maximumRows());
        GeoTiffFailures.limit(
                sourceId,
                "elevationOpen",
                "samples",
                sampleCount,
                Math.min(sourceLimits.maximumSamples(), Integer.MAX_VALUE));
        long retained =
                Math.addExact(
                        Math.multiplyExact(sampleCount, Double.BYTES),
                        Math.multiplyExact((sampleCount + 63L) / 64L, Long.BYTES));
        GeoTiffFailures.limit(
                sourceId,
                "elevationOpen",
                "retainedSampleBytes",
                retained,
                sourceLimits.maximumRetainedSampleBytes());
        int maximumDecoded = 0;
        for (long count : decodedCounts) {
            maximumDecoded = Math.max(maximumDecoded, Math.toIntExact(count));
        }
        long maskBytes =
                noDataPolicy.present() ? Math.multiplyExact((sampleCount + 63) / 64, 8) : 0;
        chargeWorking(
                Math.addExact(
                        Math.addExact(Math.multiplyExact(sampleCount, Double.BYTES), maskBytes),
                        maximumDecoded));
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        double[] samples = new double[Math.toIntExact(sampleCount)];
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        BitSet noDataCells =
                noDataPolicy.present() ? new BitSet(Math.toIntExact(sampleCount)) : new BitSet();
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        byte[] decoded = new byte[maximumDecoded];
        ByteOrder sampleOrder = data.order();
        for (int segment = 0; segment < offsets.length; segment++) {
            GeoTiffSegmentDecoder.decode(
                    sourceId,
                    segment,
                    compression,
                    snapshot,
                    Math.toIntExact(offsets[segment]),
                    Math.toIntExact(counts[segment]),
                    decoded,
                    Math.toIntExact(decodedCounts[segment]),
                    "geoTiffOpen",
                    cancellation);
            ByteBuffer values = ByteBuffer.wrap(decoded).order(sampleOrder);
            int originX =
                    layout.tiled()
                            ? (segment % layout.segmentsAcross()) * layout.segmentWidth()
                            : 0;
            int originY =
                    layout.tiled()
                            ? (segment / layout.segmentsAcross()) * layout.segmentHeight()
                            : segment * layout.segmentHeight();
            int rows = Math.min(layout.segmentHeight(), height - originY);
            int columns = Math.min(layout.segmentWidth(), width - originX);
            for (int row = 0; row < rows; row++) {
                GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
                for (int column = 0; column < columns; column++) {
                    if ((column & 4095) == 0) {
                        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
                    }
                    int encodedIndex =
                            (row * layout.segmentWidth() + column) * profile.sampleBytes();
                    int destination = (originY + row) * width + originX + column;
                    double value = profile.kind().decode(values, encodedIndex);
                    if (noDataPolicy.matches(value)) {
                        noDataCells.set(destination);
                        samples[destination] = 0.0;
                    } else if (!Double.isFinite(value)) {
                        throw GeoTiffFailures.sample(sourceId, segment, "nonFinite");
                    } else {
                        samples[destination] = value == 0.0 ? 0.0 : value;
                    }
                }
            }
        }
        snapshot = null;
        data = null;
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        PackedElevationGrid published =
                PackedElevationGrid.copyOf(
                        metadata, samples, noDataCells, sourceLimits, DiagnosticReport.empty());
        boolean cancelledAfterCopy = cancellation.isCancellationRequested();
        if (cancelledAfterCopy) {
            published.close();
            throw GeoTiffFailures.failure(
                    sourceId,
                    "SOURCE_CANCELLED",
                    "GeoTIFF operation was cancelled",
                    Map.of("operation", "geoTiffOpen"));
        }
        return published;
    }

    private ColorProfile colorProfile(Map<Integer, Entry> entries, int samplesPerPixel) {
        int photometric = code(entries, 262);
        boolean supportedSamples =
                (photometric == 0 || photometric == 1)
                        ? samplesPerPixel == 1 || samplesPerPixel == 2
                        : photometric == 2 && (samplesPerPixel == 3 || samplesPerPixel == 4);
        if (!supportedSamples) {
            throw GeoTiffFailures.unsupported(sourceId, "photometric");
        }
        Entry bits = entries.get(258);
        requireShortArray(bits, 258, samplesPerPixel, 8, "sampleType");
        Entry sampleFormat = entries.get(339);
        if (sampleFormat != null) {
            requireShortArray(sampleFormat, 339, samplesPerPixel, 1, "sampleType");
        }
        boolean alpha = samplesPerPixel == 2 || samplesPerPixel == 4;
        Entry extra = entries.get(338);
        if (alpha) {
            if (extra == null) {
                throw GeoTiffFailures.unsupported(sourceId, "alpha");
            }
            if (extra.type != TYPE_SHORT) {
                throw GeoTiffFailures.tag(sourceId, 338, "type");
            }
            if (extra.count != 1) {
                throw GeoTiffFailures.tag(sourceId, 338, "count");
            }
            if (extra.unsignedAt(0) != 2) {
                throw GeoTiffFailures.unsupported(sourceId, "alpha");
            }
        } else if (extra != null) {
            throw GeoTiffFailures.unsupported(sourceId, "alpha");
        }
        return switch (photometric) {
            case 0 ->
                    switch (samplesPerPixel) {
                        case 1 -> ColorProfile.WHITE_GRAY;
                        case 2 -> ColorProfile.WHITE_GRAY_ALPHA;
                        default -> throw GeoTiffFailures.unsupported(sourceId, "photometric");
                    };
            case 1 ->
                    switch (samplesPerPixel) {
                        case 1 -> ColorProfile.BLACK_GRAY;
                        case 2 -> ColorProfile.BLACK_GRAY_ALPHA;
                        default -> throw GeoTiffFailures.unsupported(sourceId, "photometric");
                    };
            case 2 ->
                    switch (samplesPerPixel) {
                        case 3 -> ColorProfile.RGB;
                        case 4 -> ColorProfile.RGBA;
                        default -> throw GeoTiffFailures.unsupported(sourceId, "photometric");
                    };
            default -> throw GeoTiffFailures.unsupported(sourceId, "photometric");
        };
    }

    private void requireShortArray(
            Entry entry, int tag, int count, int expected, String construct) {
        if (entry == null) {
            throw GeoTiffFailures.tag(sourceId, tag, "missing");
        }
        if (entry.type != TYPE_SHORT) {
            throw GeoTiffFailures.tag(sourceId, tag, "type");
        }
        if (entry.count != count) {
            throw GeoTiffFailures.tag(sourceId, tag, "count");
        }
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        for (int index = 0; index < count; index++) {
            if ((index & 4095) == 0) {
                GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            }
            if (entry.unsignedAt(index) != expected) {
                throw GeoTiffFailures.unsupported(sourceId, construct);
            }
        }
    }

    private SegmentLayout segmentLayout(Map<Integer, Entry> entries, int width, int height) {
        boolean anyStrip =
                entries.containsKey(273) || entries.containsKey(278) || entries.containsKey(279);
        boolean allStrip =
                entries.containsKey(273) && entries.containsKey(278) && entries.containsKey(279);
        boolean anyTile =
                entries.containsKey(322)
                        || entries.containsKey(323)
                        || entries.containsKey(324)
                        || entries.containsKey(325);
        boolean allTile =
                entries.containsKey(322)
                        && entries.containsKey(323)
                        && entries.containsKey(324)
                        && entries.containsKey(325);
        if ((anyStrip && anyTile) || (!allStrip && !allTile)) {
            throw GeoTiffFailures.unsupported(sourceId, "sampleOrganization");
        }
        if (allStrip) {
            int rowsPerStrip = scalar(entries, 278, true);
            int segments = Math.toIntExact((height + (long) rowsPerStrip - 1) / rowsPerStrip);
            return new SegmentLayout(false, width, rowsPerStrip, 1, segments, 273, 279);
        }
        int tileWidth = scalar(entries, 322, true);
        int tileHeight = scalar(entries, 323, true);
        if ((tileWidth & 15) != 0 || (tileHeight & 15) != 0) {
            throw GeoTiffFailures.unsupported(sourceId, "sampleOrganization");
        }
        Entry tileOffsets = entries.get(324);
        if (tileOffsets.type != TYPE_LONG) {
            throw GeoTiffFailures.tag(sourceId, 324, "type");
        }
        int across = Math.toIntExact((width + (long) tileWidth - 1) / tileWidth);
        int down = Math.toIntExact((height + (long) tileHeight - 1) / tileHeight);
        int segments = Math.multiplyExact(across, down);
        return new SegmentLayout(true, tileWidth, tileHeight, across, segments, 324, 325);
    }

    private long decodedBytes(
            SegmentLayout layout,
            int segment,
            int imageWidth,
            int imageHeight,
            int samplesPerPixel) {
        try {
            return layout.decodedBytes(segment, imageWidth, imageHeight, samplesPerPixel);
        } catch (ArithmeticException failure) {
            throw GeoTiffFailures.segment(sourceId, segment, "decodedLength");
        }
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
        if (entry.type != TYPE_SHORT && entry.type != TYPE_LONG) {
            throw GeoTiffFailures.tag(sourceId, tag, "type");
        }
        long value = entry.unsignedAt(0);
        if (value <= 0 || value > Integer.MAX_VALUE) {
            throw GeoTiffFailures.tag(sourceId, tag, "value");
        }
        return (int) value;
    }

    private int code(Map<Integer, Entry> entries, int tag) {
        Entry entry = entries.get(tag);
        if (entry == null) {
            throw GeoTiffFailures.tag(sourceId, tag, "missing");
        }
        if (entry.type != TYPE_SHORT || entry.count != 1) {
            throw GeoTiffFailures.tag(sourceId, tag, entry.type != TYPE_SHORT ? "type" : "count");
        }
        return Math.toIntExact(entry.unsignedAt(0));
    }

    private int optionalCode(Map<Integer, Entry> entries, int tag) {
        return entries.containsKey(tag) ? code(entries, tag) : -1;
    }

    private void requireOptionalCode(
            Map<Integer, Entry> entries, int tag, int expected, String construct) {
        int actual = optionalCode(entries, tag);
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
                    338, 339, 33550, 33922, 34264, 34735, 42113
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
        private final int inlineValueOffset;

        private Entry(int tag, int type, long count, long rawValue, int inlineValueOffset) {
            this.tag = tag;
            this.type = type;
            this.count = count;
            this.rawValue = rawValue;
            this.inlineValueOffset = inlineValueOffset;
        }

        private void validateRange() {
            int size =
                    type == TYPE_ASCII
                            ? 1
                            : type == TYPE_SHORT
                                    ? 2
                                    : type == TYPE_LONG ? 4 : type == TYPE_DOUBLE ? 8 : -1;
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
            int size = type == TYPE_ASCII ? 1 : type == TYPE_SHORT ? 2 : type == TYPE_LONG ? 4 : 8;
            return Math.multiplyExact(count, size);
        }

        private boolean outOfLine() {
            return payloadBytes() > 4;
        }

        private int valueOffset() {
            long bytes =
                    Math.multiplyExact(
                            count,
                            type == TYPE_ASCII
                                    ? 1
                                    : type == TYPE_SHORT ? 2 : type == TYPE_LONG ? 4 : 8);
            return bytes <= 4 ? -1 : Math.toIntExact(rawValue);
        }

        private int byteAt(int index) {
            if (type != TYPE_ASCII) {
                throw GeoTiffFailures.tag(sourceId, tag, "type");
            }
            if (index < 0 || index >= count) {
                throw new IndexOutOfBoundsException("TIFF value index is outside its entry");
            }
            int payloadOffset = valueOffset();
            return Byte.toUnsignedInt(
                    data.get((payloadOffset < 0 ? inlineValueOffset : payloadOffset) + index));
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
                if (type == TYPE_LONG) {
                    return rawValue;
                }
                int shift = data.order() == ByteOrder.LITTLE_ENDIAN ? index * 16 : (1 - index) * 16;
                return (rawValue >>> shift) & 0xffffL;
            }
            return type == TYPE_SHORT ? ushort(offset + index * size) : uint(offset + index * size);
        }
    }

    enum ColorProfile {
        WHITE_GRAY,
        BLACK_GRAY,
        WHITE_GRAY_ALPHA,
        BLACK_GRAY_ALPHA,
        RGB,
        RGBA
    }

    private enum SampleKind {
        INT16,
        INT32,
        FLOAT32,
        FLOAT64;

        private boolean floating() {
            return this == FLOAT32 || this == FLOAT64;
        }

        private double decode(ByteBuffer values, int offset) {
            return switch (this) {
                case INT16 -> values.getShort(offset);
                case INT32 -> values.getInt(offset);
                case FLOAT32 -> values.getFloat(offset);
                case FLOAT64 -> values.getDouble(offset);
            };
        }

        private double parseSentinel(String token) {
            double value =
                    switch (this) {
                        case INT16 -> Short.parseShort(token);
                        case INT32 -> Integer.parseInt(token);
                        case FLOAT32 -> Float.parseFloat(token);
                        case FLOAT64 -> Double.parseDouble(token);
                    };
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("non-finite no-data sentinel");
            }
            if (value == 0.0 && hasNonZeroSignificand(token)) {
                throw new NumberFormatException("no-data sentinel underflow");
            }
            return value == 0.0 ? 0.0 : value;
        }

        private static boolean hasNonZeroSignificand(String token) {
            boolean nonZero = false;
            for (int index = 0; index < token.length(); index++) {
                char value = token.charAt(index);
                if (value == 'e' || value == 'E') {
                    return nonZero;
                }
                if (value >= '1' && value <= '9') {
                    nonZero = true;
                }
            }
            return nonZero;
        }
    }

    private record NumericSampleProfile(int sampleBytes, SampleKind kind) {}

    private record NoDataPolicy(Kind kind, double sentinel) {
        private static NoDataPolicy none() {
            return new NoDataPolicy(Kind.NONE, 0.0);
        }

        private static NoDataPolicy nan() {
            return new NoDataPolicy(Kind.NAN, 0.0);
        }

        private static NoDataPolicy finite(double value) {
            return new NoDataPolicy(Kind.FINITE, value);
        }

        private boolean present() {
            return kind != Kind.NONE;
        }

        private boolean matches(double value) {
            return kind == Kind.NAN
                    ? Double.isNaN(value)
                    : kind == Kind.FINITE && value == sentinel;
        }

        private enum Kind {
            NONE,
            NAN,
            FINITE
        }
    }

    private record SegmentLayout(
            boolean tiled,
            int segmentWidth,
            int segmentHeight,
            int segmentsAcross,
            int segmentCount,
            int offsetTag,
            int countTag) {
        private long decodedBytes(
                int segment, int imageWidth, int imageHeight, int samplesPerPixel) {
            if (tiled) {
                return Math.multiplyExact(
                        Math.multiplyExact((long) segmentWidth, segmentHeight), samplesPerPixel);
            }
            int rows = Math.min(segmentHeight, imageHeight - segment * segmentHeight);
            return Math.multiplyExact(Math.multiplyExact((long) imageWidth, rows), samplesPerPixel);
        }
    }

    private record GeoReference(RasterGridPlacement placement) {}
}
