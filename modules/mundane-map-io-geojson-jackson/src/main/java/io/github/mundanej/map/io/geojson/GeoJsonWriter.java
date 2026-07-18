package io.github.mundanej.map.io.geojson;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.core.CrsDefinitions;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;

final class GeoJsonWriter {
    private GeoJsonWriter() {}

    static void write(
            Path target,
            FeatureSource source,
            GeoJsonWriteLimits limits,
            CancellationToken cancellation) {
        write(target, source, limits, cancellation, new SystemFileOperations());
    }

    static void write(
            Path target,
            FeatureSource source,
            GeoJsonWriteLimits limits,
            CancellationToken cancellation,
            FileOperations files) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(files, "files");
        cancelled(cancellation, "validate");
        FeatureSourceMetadata metadata = metadata(source, cancellation);
        validateCrs(metadata);
        Path absolute = validateTarget(target);
        FeatureCursor cursor = openCursor(source, cancellation);
        Encoded encoded = null;
        GeoJsonWriteException primary = null;
        try {
            encoded = encode(cursor, limits, cancellation);
        } catch (GeoJsonWriteException failure) {
            primary = failure;
        } finally {
            try {
                cursor.close();
            } catch (SourceException failure) {
                GeoJsonWriteException mapped = sourceFailure("cursorClose", failure);
                if (primary == null) {
                    primary = mapped;
                } else {
                    primary.addSuppressed(mapped);
                }
            } catch (RuntimeException failure) {
                GeoJsonWriteException mapped = sourceFailure("cursorClose", failure);
                if (primary == null) {
                    primary = mapped;
                } else {
                    primary.addSuppressed(mapped);
                }
            }
        }
        if (primary != null) {
            throw primary;
        }
        publish(absolute, Objects.requireNonNull(encoded), cancellation, files);
    }

    private static FeatureSourceMetadata metadata(
            FeatureSource source, CancellationToken cancellation) {
        cancelled(cancellation, "source");
        try {
            FeatureSourceMetadata metadata =
                    Objects.requireNonNull(source.metadata(), "source metadata");
            cancelled(cancellation, "source");
            return metadata;
        } catch (GeoJsonWriteException failure) {
            throw failure;
        } catch (SourceException failure) {
            cancelled(cancellation, "source");
            throw sourceFailure("metadata", failure);
        } catch (RuntimeException failure) {
            cancelled(cancellation, "source");
            throw sourceFailure("metadata", failure);
        }
    }

    private static void validateCrs(FeatureSourceMetadata metadata) {
        if (metadata.crs().isEmpty()) {
            throw failure("GEOJSON_WRITE_CRS_INVALID", "reason", "missing");
        }
        var crs = metadata.crs().orElseThrow();
        if (crs.definition().isEmpty()) {
            throw failure("GEOJSON_WRITE_CRS_INVALID", "reason", "unknown");
        }
        var definition = crs.definition().orElseThrow();
        if (!definition.canonicalIdentifier().equals("EPSG:4326")) {
            throw failure("GEOJSON_WRITE_CRS_INVALID", "reason", "mismatch");
        }
        if (!definition.equals(CrsDefinitions.EPSG_4326)) {
            throw failure("GEOJSON_WRITE_CRS_INVALID", "reason", "definition");
        }
    }

    private static Path validateTarget(Path target) {
        Path absolute = target.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null
                || Files.isSymbolicLink(parent)
                || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("GEOJSON_WRITE_TARGET_INVALID", "reason", "parent");
        }
        if (Files.isSymbolicLink(absolute)) {
            throw failure("GEOJSON_WRITE_TARGET_INVALID", "reason", "symlink");
        }
        if (Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("GEOJSON_WRITE_TARGET_INVALID", "reason", "wrongKind");
        }
        return absolute;
    }

    private static FeatureCursor openCursor(FeatureSource source, CancellationToken cancellation) {
        cancelled(cancellation, "source");
        try {
            FeatureCursor cursor =
                    Objects.requireNonNull(
                            source.openCursor(FeatureQuery.all(), cancellation), "source cursor");
            if (cancellation.isCancellationRequested()) {
                GeoJsonWriteException primary =
                        failure("GEOJSON_WRITE_CANCELLED", "phase", "source");
                try {
                    cursor.close();
                } catch (RuntimeException cleanup) {
                    primary.addSuppressed(sourceFailure("cursorClose", cleanup));
                }
                throw primary;
            }
            return cursor;
        } catch (GeoJsonWriteException failure) {
            throw failure;
        } catch (SourceException failure) {
            cancelled(cancellation, "source");
            throw sourceFailure("cursorOpen", failure);
        } catch (RuntimeException failure) {
            cancelled(cancellation, "source");
            throw sourceFailure("cursorOpen", failure);
        }
    }

    private static Encoded encode(
            FeatureCursor cursor, GeoJsonWriteLimits limits, CancellationToken cancellation) {
        WriteState state = new WriteState(limits, cancellation);
        state.nesting(2);
        BoundedOutput output = new BoundedOutput(state);
        JsonGenerator generator = null;
        GeoJsonWriteException primary = null;
        try {
            generator =
                    GeoJsonFactories.writer(limits)
                            .createGenerator(ObjectWriteContext.empty(), output);
            generator.writeStartObject();
            generator.writeStringProperty("type", "FeatureCollection");
            generator.writeArrayPropertyStart("features");
            while (advance(cursor, cancellation)) {
                FeatureRecord record = current(cursor, cancellation);
                writeFeature(generator, record, state);
            }
            generator.writeEndArray();
            generator.writeEndObject();
            generator.flush();
        } catch (GeoJsonWriteException failure) {
            primary = failure;
        } catch (JacksonException failure) {
            primary =
                    failure(
                            "GEOJSON_WRITE_FAILED",
                            context("phase", "encode", "reason", "io"),
                            failure);
        } finally {
            if (generator != null) {
                try {
                    generator.close();
                } catch (RuntimeException failure) {
                    GeoJsonWriteException mapped =
                            failure(
                                    "GEOJSON_WRITE_FAILED",
                                    context("phase", "encode", "reason", "io"),
                                    failure);
                    if (primary == null) {
                        primary = mapped;
                    } else {
                        primary.addSuppressed(mapped);
                    }
                }
            }
        }
        if (primary != null) {
            throw primary;
        }
        state.poll("encode");
        output.write('\n');
        return output.encoded();
    }

    private static boolean advance(FeatureCursor cursor, CancellationToken cancellation) {
        cancelled(cancellation, "source");
        try {
            boolean advanced = cursor.advance();
            cancelled(cancellation, "source");
            return advanced;
        } catch (GeoJsonWriteException failure) {
            throw failure;
        } catch (SourceException failure) {
            cancelled(cancellation, "source");
            throw sourceFailure("advance", failure);
        } catch (RuntimeException failure) {
            cancelled(cancellation, "source");
            throw sourceFailure("advance", failure);
        }
    }

    private static FeatureRecord current(FeatureCursor cursor, CancellationToken cancellation) {
        cancelled(cancellation, "source");
        try {
            FeatureRecord record = Objects.requireNonNull(cursor.current(), "current record");
            cancelled(cancellation, "source");
            return record;
        } catch (GeoJsonWriteException failure) {
            throw failure;
        } catch (SourceException failure) {
            cancelled(cancellation, "source");
            throw sourceFailure("current", failure);
        } catch (RuntimeException failure) {
            cancelled(cancellation, "source");
            throw sourceFailure("current", failure);
        }
    }

    private static void writeFeature(
            JsonGenerator generator, FeatureRecord record, WriteState state)
            throws JacksonException {
        state.feature();
        state.nesting(4);
        if (!record.name().isEmpty()) {
            unrepresentable("name", "nonEmpty");
        }
        state.scalar(record.id(), 249, "id");
        generator.writeStartObject();
        generator.writeStringProperty("type", "Feature");
        generator.writeStringProperty("id", record.id());
        generator.writeName("geometry");
        writeGeometry(generator, record.geometry(), state);
        generator.writeObjectPropertyStart("properties");
        int featureProperties = 0;
        for (Map.Entry<String, Object> property : record.attributes().entrySet()) {
            state.property(++featureProperties);
            state.propertyName(property.getKey());
            generator.writeName(property.getKey());
            writeProperty(generator, property.getValue(), state);
        }
        generator.writeEndObject();
        generator.writeEndObject();
    }

    private static void writeProperty(JsonGenerator generator, Object value, WriteState state)
            throws JacksonException {
        if (value instanceof String text) {
            state.scalar(text, state.limits.maximumScalarCharacters(), "attribute");
            generator.writeString(text);
        } else if (value instanceof Boolean booleanValue) {
            generator.writeBoolean(booleanValue);
        } else if (value instanceof Long longValue) {
            state.number(Long.toString(longValue));
            generator.writeNumber(longValue);
        } else if (value instanceof Double doubleValue) {
            BigDecimal normalized = normalize(BigDecimal.valueOf(doubleValue), state);
            generator.writeNumber(normalized);
        } else if (value instanceof BigDecimal decimal) {
            generator.writeNumber(normalize(decimal, state));
        } else if (value == AttributeNull.INSTANCE) {
            generator.writeNull();
        } else {
            unrepresentable("attribute", "type");
        }
    }

    private static BigDecimal normalize(BigDecimal value, WriteState state) {
        BigDecimal normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        long adjusted = (long) normalized.precision() - normalized.scale() - 1L;
        if (normalized.precision() > 34 || adjusted < -308 || adjusted > 308) {
            unrepresentable("attribute", "number");
        }
        state.number(normalized.toString());
        return normalized;
    }

    private static void writeGeometry(JsonGenerator generator, Geometry geometry, WriteState state)
            throws JacksonException {
        state.beginGeometry();
        state.nesting(requiredNesting(geometry));
        generator.writeStartObject();
        if (geometry instanceof PointGeometry point) {
            generator.writeStringProperty("type", "Point");
            generator.writeName("coordinates");
            writePosition(generator, point.coordinate(), state);
        } else if (geometry instanceof MultiPointGeometry points) {
            generator.writeStringProperty("type", "MultiPoint");
            generator.writeName("coordinates");
            writeSequence(generator, points.coordinates(), 0, points.coordinates().size(), state);
        } else if (geometry instanceof LineStringGeometry line) {
            generator.writeStringProperty("type", "LineString");
            generator.writeName("coordinates");
            writeSequence(generator, line.coordinates(), 0, line.coordinates().size(), state);
        } else if (geometry instanceof MultiLineStringGeometry lines) {
            generator.writeStringProperty("type", "MultiLineString");
            generator.writeArrayPropertyStart("coordinates");
            for (int part = 0; part < lines.partCount(); part++) {
                state.part();
                writeSequence(
                        generator,
                        lines.coordinates(),
                        lines.partOffset(part),
                        lines.partOffset(part + 1),
                        state);
            }
            generator.writeEndArray();
        } else if (geometry instanceof PolygonGeometry polygon) {
            generator.writeStringProperty("type", "Polygon");
            generator.writeArrayPropertyStart("coordinates");
            state.part();
            writeSequence(generator, polygon.exterior(), 0, polygon.exterior().size(), state);
            for (CoordinateSequence hole : polygon.holes()) {
                state.part();
                writeSequence(generator, hole, 0, hole.size(), state);
            }
            generator.writeEndArray();
        } else if (geometry instanceof MultiPolygonGeometry polygons) {
            generator.writeStringProperty("type", "MultiPolygon");
            generator.writeArrayPropertyStart("coordinates");
            for (int polygon = 0; polygon < polygons.polygonCount(); polygon++) {
                state.part();
                generator.writeStartArray();
                for (int ring = polygons.polygonRingOffset(polygon);
                        ring < polygons.polygonRingOffset(polygon + 1);
                        ring++) {
                    state.part();
                    writeSequence(
                            generator,
                            polygons.coordinates(),
                            polygons.ringOffset(ring),
                            polygons.ringOffset(ring + 1),
                            state);
                }
                generator.writeEndArray();
            }
            generator.writeEndArray();
        } else {
            unrepresentable("geometry", "type");
        }
        generator.writeEndObject();
    }

    private static int requiredNesting(Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return 5;
        }
        if (geometry instanceof MultiPointGeometry || geometry instanceof LineStringGeometry) {
            return 6;
        }
        if (geometry instanceof MultiLineStringGeometry || geometry instanceof PolygonGeometry) {
            return 7;
        }
        if (geometry instanceof MultiPolygonGeometry) {
            return 8;
        }
        unrepresentable("geometry", "type");
        return 0;
    }

    private static void writeSequence(
            JsonGenerator generator,
            CoordinateSequence sequence,
            int start,
            int end,
            WriteState state)
            throws JacksonException {
        generator.writeStartArray();
        for (int index = start; index < end; index++) {
            writePosition(generator, sequence.coordinate(index), state);
        }
        generator.writeEndArray();
    }

    private static void writePosition(
            JsonGenerator generator, Coordinate coordinate, WriteState state)
            throws JacksonException {
        state.coordinate(coordinate);
        double x = coordinate.x() == 0 ? 0 : coordinate.x();
        double y = coordinate.y() == 0 ? 0 : coordinate.y();
        state.number(Double.toString(x));
        state.number(Double.toString(y));
        generator.writeStartArray();
        generator.writeNumber(x);
        generator.writeNumber(y);
        generator.writeEndArray();
    }

    private static void publish(
            Path target, Encoded encoded, CancellationToken cancellation, FileOperations files) {
        Path temporary = null;
        GeoJsonWriteException primary = null;
        try {
            cancelled(cancellation, "temporary");
            temporary = files.createTemporary(target.getParent());
            BasicFileAttributes attributes = files.attributes(temporary);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw failure(
                        "GEOJSON_WRITE_FAILED",
                        context("phase", "temporary", "reason", "changed"),
                        null);
            }
            cancelled(cancellation, "write");
            files.write(temporary, encoded, cancellation);
            cancelled(cancellation, "write");
            files.force(temporary);
            cancelled(cancellation, "move");
            files.move(temporary, target);
            temporary = null;
        } catch (GeoJsonWriteException failure) {
            primary = failure;
        } catch (AtomicMoveNotSupportedException failure) {
            primary = failure("GEOJSON_WRITE_ATOMIC_MOVE_UNSUPPORTED", Map.of(), failure);
        } catch (IOException failure) {
            primary =
                    failure(
                            "GEOJSON_WRITE_FAILED",
                            context(
                                    "phase",
                                    temporary == null ? "temporary" : files.failurePhase(),
                                    "reason",
                                    "io"),
                            failure);
        } finally {
            if (temporary != null) {
                try {
                    files.delete(temporary);
                } catch (IOException cleanup) {
                    GeoJsonWriteException mapped =
                            failure(
                                    "GEOJSON_WRITE_FAILED",
                                    context("phase", "cleanup", "reason", "io"),
                                    cleanup);
                    if (primary == null) {
                        primary = mapped;
                    } else {
                        primary.addSuppressed(mapped);
                    }
                }
            }
        }
        if (primary != null) {
            throw primary;
        }
    }

    private static void cancelled(CancellationToken cancellation, String phase) {
        if (cancellation.isCancellationRequested()) {
            throw failure("GEOJSON_WRITE_CANCELLED", "phase", phase);
        }
    }

    private static void unrepresentable(String field, String reason) {
        throw failure(
                "GEOJSON_WRITE_VALUE_UNREPRESENTABLE",
                context("field", field, "reason", reason),
                null);
    }

    private static GeoJsonWriteException sourceFailure(String phase, Throwable cause) {
        Optional<io.github.mundanej.map.api.DiagnosticReport> report =
                cause instanceof SourceException source
                        ? Optional.of(source.report())
                        : Optional.empty();
        return new GeoJsonWriteException(
                problem("GEOJSON_WRITE_SOURCE_FAILED", Map.of("phase", phase)), report, cause);
    }

    private static GeoJsonWriteException failure(String code, String key, String value) {
        return failure(code, Map.of(key, value), null);
    }

    private static GeoJsonWriteException failure(
            String code, Map<String, String> context, Throwable cause) {
        return new GeoJsonWriteException(problem(code, context), Optional.empty(), cause);
    }

    private static GeoJsonWriteProblem problem(String code, Map<String, String> context) {
        String message =
                switch (code) {
                    case "GEOJSON_WRITE_CRS_INVALID" ->
                            "Source CRS cannot be represented as RFC 7946 coordinates";
                    case "GEOJSON_WRITE_VALUE_UNREPRESENTABLE" ->
                            "Source value is outside the GeoJSON write profile";
                    case "GEOJSON_WRITE_SOURCE_FAILED" -> "GeoJSON source failed while writing";
                    case "GEOJSON_WRITE_LIMIT_EXCEEDED" -> "GeoJSON write limit exceeded";
                    case "GEOJSON_WRITE_CANCELLED" -> "GeoJSON write was cancelled";
                    case "GEOJSON_WRITE_TARGET_INVALID" -> "GeoJSON write target is invalid";
                    case "GEOJSON_WRITE_ATOMIC_MOVE_UNSUPPORTED" ->
                            "Atomic GeoJSON replacement is unsupported";
                    case "GEOJSON_WRITE_FAILED" -> "GeoJSON local write failed";
                    default -> throw new IllegalArgumentException("Unknown GeoJSON write code");
                };
        return new GeoJsonWriteProblem(code, message, context);
    }

    private static Map<String, String> context(String... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("Context entries require key/value pairs");
        }
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            context.put(entries[index], entries[index + 1]);
        }
        return context;
    }

    static final class Encoded {
        private final byte[] bytes;
        private final int length;

        private Encoded(byte[] bytes, int length) {
            this.bytes = bytes;
            this.length = length;
        }

        byte[] bytes() {
            return bytes;
        }

        int length() {
            return length;
        }
    }

    private static final class BoundedOutput extends OutputStream {
        private final WriteState state;
        private final byte[] bytes;
        private int count;

        private BoundedOutput(WriteState state) {
            this.state = state;
            this.bytes = new byte[Math.toIntExact(state.limits.maximumOutputBytes())];
            state.owned(bytes.length);
        }

        @Override
        public void write(int value) {
            require(1);
            bytes[count++] = (byte) value;
        }

        @Override
        public void write(byte[] values, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, values.length);
            require(length);
            System.arraycopy(values, offset, bytes, count, length);
            count += length;
        }

        private void require(int requested) {
            long next = Math.addExact((long) count, requested);
            if (next > bytes.length) {
                throw state.limit("outputBytes", next, bytes.length);
            }
        }

        private Encoded encoded() {
            return new Encoded(bytes, count);
        }
    }

    private static final class WriteState {
        private final GeoJsonWriteLimits limits;
        private final CancellationToken cancellation;
        private long owned;
        private int features;
        private int coordinates;
        private int geometryCoordinates;
        private int parts;
        private int properties;
        private int characters;
        private int pollUnits;

        private WriteState(GeoJsonWriteLimits limits, CancellationToken cancellation) {
            this.limits = limits;
            this.cancellation = cancellation;
        }

        private void feature() {
            if (++features > limits.maximumFeatures()) {
                throw limit("features", features, limits.maximumFeatures());
            }
            owned(8);
            unit();
        }

        private void nesting(int requested) {
            if (requested > limits.maximumNestingDepth()) {
                throw limit("nesting", requested, limits.maximumNestingDepth());
            }
        }

        private void beginGeometry() {
            geometryCoordinates = 0;
        }

        private void coordinate(Coordinate coordinate) {
            if (!Double.isFinite(coordinate.x()) || !Double.isFinite(coordinate.y())) {
                unrepresentable("coordinate", "nonFinite");
            }
            if (coordinate.x() < -180
                    || coordinate.x() > 180
                    || coordinate.y() < -90
                    || coordinate.y() > 90) {
                unrepresentable("coordinate", "range");
            }
            if (++coordinates > limits.maximumTotalCoordinates()) {
                throw limit("coordinates", coordinates, limits.maximumTotalCoordinates());
            }
            if (++geometryCoordinates > limits.maximumCoordinatesPerGeometry()) {
                throw limit(
                        "geometryCoordinates",
                        geometryCoordinates,
                        limits.maximumCoordinatesPerGeometry());
            }
            owned(16);
            unit();
        }

        private void part() {
            if (++parts > limits.maximumParts()) {
                throw limit("parts", parts, limits.maximumParts());
            }
            owned(8);
            unit();
        }

        private void property(int inFeature) {
            if (inFeature > limits.maximumPropertiesPerFeature()) {
                throw limit("featureProperties", inFeature, limits.maximumPropertiesPerFeature());
            }
            if (++properties > limits.maximumTotalProperties()) {
                throw limit("properties", properties, limits.maximumTotalProperties());
            }
            owned(8);
            unit();
        }

        private void propertyName(String value) {
            if (value.length() > 256) {
                unrepresentable("attribute", "length");
            }
            scalar(value, 256, "attribute");
        }

        private void scalar(String value, int profileMaximum, String field) {
            if (!unicode(value)) {
                unrepresentable(field, "unicode");
            }
            int maximum = Math.min(profileMaximum, limits.maximumScalarCharacters());
            if (value.length() > maximum) {
                if (maximum < profileMaximum) {
                    throw limit("scalarCharacters", value.length(), maximum);
                }
                unrepresentable(field, "length");
            }
            characters = Math.addExact(characters, value.length());
            if (characters > limits.maximumAggregateCharacters()) {
                throw limit("aggregateCharacters", characters, limits.maximumAggregateCharacters());
            }
            owned(Math.multiplyExact(2L, value.length()));
            poll(value.length());
        }

        private void number(String value) {
            if (value.length() > limits.maximumNumberCharacters()) {
                throw limit("numberCharacters", value.length(), limits.maximumNumberCharacters());
            }
        }

        private void owned(long bytes) {
            owned = Math.addExact(owned, bytes);
            if (owned > limits.maximumOwnedBytes()) {
                throw limit("ownedBytes", owned, limits.maximumOwnedBytes());
            }
        }

        private void unit() {
            poll(1);
        }

        private void poll(int units) {
            pollUnits = Math.addExact(pollUnits, units);
            if (pollUnits >= 4_096) {
                pollUnits %= 4_096;
                poll("encode");
            }
        }

        private void poll(String phase) {
            cancelled(cancellation, phase);
        }

        private GeoJsonWriteException limit(String name, long requested, long maximum) {
            return failure(
                    "GEOJSON_WRITE_LIMIT_EXCEEDED",
                    context(
                            "limit",
                            name,
                            "requested",
                            Long.toString(requested),
                            "maximum",
                            Long.toString(maximum)),
                    null);
        }

        private static boolean unicode(String value) {
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (Character.isHighSurrogate(current)) {
                    if (++index >= value.length()
                            || !Character.isLowSurrogate(value.charAt(index))) {
                        return false;
                    }
                } else if (Character.isLowSurrogate(current)) {
                    return false;
                }
            }
            return true;
        }
    }

    interface FileOperations {
        Path createTemporary(Path parent) throws IOException;

        BasicFileAttributes attributes(Path path) throws IOException;

        void write(Path path, Encoded encoded, CancellationToken cancellation) throws IOException;

        void force(Path path) throws IOException;

        void move(Path temporary, Path target) throws IOException;

        void delete(Path path) throws IOException;

        String failurePhase();
    }

    private static final class SystemFileOperations implements FileOperations {
        private String phase = "temporary";

        @Override
        public Path createTemporary(Path parent) throws IOException {
            phase = "temporary";
            return Files.createTempFile(parent, ".mundane-map-geojson-", ".tmp");
        }

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            phase = "temporary";
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public void write(Path path, Encoded encoded, CancellationToken cancellation)
                throws IOException {
            phase = "write";
            try (FileChannel channel =
                    FileChannel.open(
                            path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded.bytes(), 0, encoded.length());
                while (buffer.hasRemaining()) {
                    cancelled(cancellation, "write");
                    channel.write(buffer);
                }
            }
        }

        @Override
        public void force(Path path) throws IOException {
            phase = "force";
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        }

        @Override
        public void move(Path temporary, Path target) throws IOException {
            phase = "move";
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void delete(Path path) throws IOException {
            phase = "cleanup";
            Files.deleteIfExists(path);
        }

        @Override
        public String failurePhase() {
            return phase;
        }
    }
}
