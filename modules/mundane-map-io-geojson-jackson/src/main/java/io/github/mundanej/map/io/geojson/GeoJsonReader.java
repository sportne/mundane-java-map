package io.github.mundanej.map.io.geojson;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.exc.StreamConstraintsException;

final class GeoJsonReader {
    private static final String COMPONENT = "geojson";

    private GeoJsonReader() {}

    static Opening inspect(
            byte[] bytes,
            SourceIdentity identity,
            GeoJsonLimits limits,
            CancellationToken cancellation) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
        checkCancelled(identity.id(), cancellation);
        if (bytes.length > limits.maximumInputBytes()) {
            throw limit(identity.id(), "inputBytes", bytes.length, limits.maximumInputBytes());
        }
        rejectUnsupportedBom(bytes, identity.id());
        validateUtf8(bytes, identity.id());
        WarningCollector warnings = new WarningCollector(identity.id(), limits.retainedWarnings());
        int offset = bomOffset(bytes);
        if (offset != 0) {
            warnings.add("GEOJSON_UTF8_BOM_IGNORED", "A leading UTF-8 BOM was ignored");
        }
        State state =
                new State(bytes, identity.id(), limits, cancellation, offset, true, bytes.length);
        try (JsonParser parser =
                GeoJsonFactories.reader(limits)
                        .createParser(
                                ObjectReadContext.empty(), bytes, offset, bytes.length - offset)) {
            requireToken(parser, state, JsonToken.START_OBJECT, "root", "kind");
            RootData root = parseRoot(bytes, parser, state, warnings);
            if (next(parser, state) != null) {
                throw failure(
                        identity.id(),
                        "GEOJSON_JSON_INVALID",
                        "GeoJSON has trailing content",
                        Map.of("reason", "trailingContent"));
            }
            return finish(root, warnings);
        } catch (SourceException failure) {
            throw failure;
        } catch (StreamConstraintsException failure) {
            throw constraintFailure(identity.id(), limits, failure);
        } catch (JacksonException failure) {
            throw failure(
                    identity.id(),
                    "GEOJSON_JSON_INVALID",
                    "GeoJSON syntax is invalid",
                    Map.of("reason", jacksonReason(failure)),
                    failure);
        }
    }

    static FeatureRecord readEntry(
            byte[] bytes,
            Entry entry,
            SourceIdentity identity,
            GeoJsonLimits limits,
            CancellationToken cancellation) {
        State state =
                new State(
                        bytes,
                        identity.id(),
                        limits,
                        cancellation,
                        entry.startInclusive(),
                        true,
                        0);
        try (JsonParser parser =
                GeoJsonFactories.reader(limits)
                        .createParser(
                                ObjectReadContext.empty(),
                                bytes,
                                entry.startInclusive(),
                                entry.endExclusive() - entry.startInclusive())) {
            requireToken(parser, state, JsonToken.START_OBJECT, "root", "kind");
            FeatureRecord record;
            if (entry.bareGeometry()) {
                GeometryData geometry = parseGeometryBody(parser, state);
                record = new FeatureRecord("geometry:0", "", geometry.geometry(), Map.of());
            } else {
                ParsedFeature feature =
                        parseFeatureBody(parser, state, entry.physicalIndex(), null);
                if (feature.record() == null) {
                    throw new IllegalStateException("Indexed feature no longer emits a record");
                }
                record = feature.record();
            }
            if (next(parser, state) != null) {
                throw new IllegalStateException("Indexed GeoJSON fence contains trailing content");
            }
            return record;
        } catch (SourceException failure) {
            throw failure;
        } catch (StreamConstraintsException failure) {
            throw constraintFailure(identity.id(), limits, failure);
        } catch (JacksonException failure) {
            throw failure(
                    identity.id(),
                    "GEOJSON_JSON_INVALID",
                    "GeoJSON syntax is invalid",
                    Map.of("reason", jacksonReason(failure)),
                    failure);
        }
    }

    private static RootData parseRoot(
            byte[] bytes, JsonParser parser, State state, WarningCollector warnings)
            throws JacksonException {
        int start = state.absoluteOffset(parser.currentTokenLocation().getByteOffset());
        String type = null;
        Map<String, ValueFence> deferred = new LinkedHashMap<>();
        JsonToken token;
        while ((token = next(parser, state)) != JsonToken.END_OBJECT) {
            requireCurrent(token, JsonToken.PROPERTY_NAME, state.sourceId, "root", "kind");
            String name = memberName(parser, state);
            JsonToken value = nextRequired(parser, state, "root", "missing");
            switch (name) {
                case "type" -> type = uniqueString(type, value, parser, state, "type");
                case "bbox" -> parseBbox(value, parser, state);
                case "crs" -> unsupported(state.sourceId, "legacyCrs");
                default -> {
                    int valueStart =
                            state.absoluteOffset(parser.currentTokenLocation().getByteOffset());
                    skipValue(value, parser, state);
                    int valueEnd = state.absoluteOffset(parser.currentLocation().getByteOffset());
                    deferred.put(name, new ValueFence(valueStart, valueEnd));
                }
            }
        }
        int end = state.absoluteOffset(parser.currentLocation().getByteOffset());
        if (type == null) {
            invalid(state.sourceId, "type", "missing");
        }
        List<Entry> entries;
        if (type.equals("FeatureCollection")) {
            ValueFence features = deferred.get("features");
            if (features == null) {
                invalid(state.sourceId, "features", "missing");
            }
            entries = parseDeferredFeatures(bytes, features, state, warnings);
        } else if (type.equals("Feature")) {
            State replay = state.replay(start);
            replay.physicalFeature();
            ParsedFeature parsed = parseDeferredFeature(bytes, start, end, replay, warnings);
            entries = List.of(parsed.withFence(start, end));
        } else if (type.equals("GeometryCollection")) {
            unsupported(state.sourceId, "geometryCollection");
            throw new AssertionError();
        } else if (isGeometryType(type)) {
            GeometryData geometry = parseDeferredGeometry(bytes, start, end, state.replay(start));
            entries = List.of(new Entry(start, end, geometry.geometry().envelope(), 0, true, true));
        } else {
            unsupported(state.sourceId, "root");
            throw new AssertionError();
        }
        return new RootData(entries);
    }

    private static Opening finish(RootData root, WarningCollector warnings) {
        List<Entry> entries = root.entries();
        long emitted = 0;
        Envelope extent = null;
        for (Entry entry : entries) {
            if (entry.emitted()) {
                emitted++;
                extent = extent == null ? entry.envelope() : extent.union(entry.envelope());
            }
        }
        return new Opening(
                List.copyOf(entries), emitted, Optional.ofNullable(extent), warnings.report());
    }

    private static List<Entry> parseDeferredFeatures(
            byte[] bytes, ValueFence fence, State original, WarningCollector warnings)
            throws JacksonException {
        State replay = original.replay(fence.startInclusive());
        List<Entry> entries = new ArrayList<>();
        try (JsonParser parser = parser(bytes, fence, replay.limits)) {
            JsonToken value = nextRequired(parser, replay, "features", "missing");
            parseFeatures(value, parser, replay, warnings, entries);
            requireEnd(parser, replay);
        }
        return entries;
    }

    private static ParsedFeature parseDeferredFeature(
            byte[] bytes, int start, int end, State replay, WarningCollector warnings)
            throws JacksonException {
        try (JsonParser parser = parser(bytes, new ValueFence(start, end), replay.limits)) {
            requireToken(parser, replay, JsonToken.START_OBJECT, "root", "kind");
            ParsedFeature feature = parseFeatureBody(parser, replay, 0, warnings);
            requireEnd(parser, replay);
            return feature;
        }
    }

    private static GeometryData parseDeferredGeometry(
            byte[] bytes, int start, int end, State replay) throws JacksonException {
        try (JsonParser parser = parser(bytes, new ValueFence(start, end), replay.limits)) {
            requireToken(parser, replay, JsonToken.START_OBJECT, "root", "kind");
            GeometryData geometry = parseGeometryBody(parser, replay);
            requireEnd(parser, replay);
            return geometry;
        }
    }

    private static JsonParser parser(byte[] bytes, ValueFence fence, GeoJsonLimits limits)
            throws JacksonException {
        return GeoJsonFactories.reader(limits)
                .createParser(
                        ObjectReadContext.empty(),
                        bytes,
                        fence.startInclusive(),
                        fence.endExclusive() - fence.startInclusive());
    }

    private static void requireEnd(JsonParser parser, State state) throws JacksonException {
        if (next(parser, state) != null) {
            throw new IllegalStateException("Deferred GeoJSON value contains trailing content");
        }
    }

    private static void parseFeatures(
            JsonToken token,
            JsonParser parser,
            State state,
            WarningCollector warnings,
            List<Entry> entries)
            throws JacksonException {
        requireCurrent(token, JsonToken.START_ARRAY, state.sourceId, "features", "kind");
        JsonToken item;
        while ((item = next(parser, state)) != JsonToken.END_ARRAY) {
            requireCurrent(item, JsonToken.START_OBJECT, state.sourceId, "features", "kind");
            int physical = state.physicalFeature();
            int start = state.absoluteOffset(parser.currentTokenLocation().getByteOffset());
            ParsedFeature parsed = parseFeatureBody(parser, state, physical, warnings);
            int end = state.absoluteOffset(parser.currentLocation().getByteOffset());
            entries.add(parsed.withFence(start, end));
        }
    }

    private static ParsedFeature parseFeatureBody(
            JsonParser parser, State state, int physicalIndex, WarningCollector warnings)
            throws JacksonException {
        String type = null;
        FeatureFields fields = new FeatureFields();
        JsonToken token;
        while ((token = next(parser, state)) != JsonToken.END_OBJECT) {
            requireCurrent(token, JsonToken.PROPERTY_NAME, state.sourceId, "geometry", "kind");
            String name = memberName(parser, state);
            JsonToken value = nextRequired(parser, state, name, "missing");
            switch (name) {
                case "type" -> type = uniqueString(type, value, parser, state, "type");
                case "geometry" -> {
                    if (fields.geometrySeen) {
                        duplicate(state.sourceId);
                    }
                    fields.geometrySeen = true;
                    fields.geometry = parseGeometryMember(value, parser, state);
                }
                case "properties" -> {
                    if (fields.propertiesSeen) {
                        duplicate(state.sourceId);
                    }
                    fields.propertiesSeen = true;
                    fields.properties = parseProperties(value, parser, state);
                }
                case "id" -> {
                    if (fields.idSeen) {
                        duplicate(state.sourceId);
                    }
                    fields.idSeen = true;
                    fields.id = parseId(value, parser, state);
                }
                case "bbox" -> parseBbox(value, parser, state);
                case "crs" -> unsupported(state.sourceId, "legacyCrs");
                default -> skipValue(value, parser, state);
            }
        }
        if (!"Feature".equals(type)) {
            invalid(state.sourceId, "type", type == null ? "missing" : "kind");
        }
        return finishFeature(fields, physicalIndex, 0, 0, state, warnings);
    }

    private static ParsedFeature finishFeature(
            FeatureFields fields,
            int physicalIndex,
            int start,
            int end,
            State state,
            WarningCollector warnings) {
        if (!fields.geometrySeen) {
            invalid(state.sourceId, "geometry", "missing");
        }
        if (!fields.propertiesSeen) {
            invalid(state.sourceId, "properties", "missing");
        }
        if (fields.geometry == null) {
            if (warnings != null) {
                warnings.add(
                        "GEOJSON_NULL_GEOMETRY_SKIPPED", "A null-geometry Feature was skipped");
            }
            return new ParsedFeature(
                    null, new Entry(start, end, null, physicalIndex, false, false));
        }
        Geometry geometry = fields.geometry.geometry();
        String id = fields.id == null ? "record:" + physicalIndex : fields.id;
        if (id.length() > 256) {
            invalid(state.sourceId, "id", "length");
        }
        state.uniqueId(id, physicalIndex);
        FeatureRecord record =
                new FeatureRecord(
                        id, "", geometry, fields.properties == null ? Map.of() : fields.properties);
        return new ParsedFeature(
                record, new Entry(start, end, geometry.envelope(), physicalIndex, true, false));
    }

    private static GeometryData parseGeometryMember(JsonToken token, JsonParser parser, State state)
            throws JacksonException {
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        requireCurrent(token, JsonToken.START_OBJECT, state.sourceId, "geometry", "kind");
        return parseGeometryBody(parser, state);
    }

    private static GeometryData parseGeometryBody(JsonParser parser, State state)
            throws JacksonException {
        String type = null;
        GeometryFields fields = new GeometryFields();
        JsonToken token;
        while ((token = next(parser, state)) != JsonToken.END_OBJECT) {
            requireCurrent(token, JsonToken.PROPERTY_NAME, state.sourceId, "geometry", "kind");
            String name = memberName(parser, state);
            JsonToken value = nextRequired(parser, state, name, "missing");
            switch (name) {
                case "type" -> type = uniqueString(type, value, parser, state, "type");
                case "coordinates" -> {
                    if (fields.coordinatesSeen) {
                        duplicate(state.sourceId);
                    }
                    fields.coordinatesSeen = true;
                    int start = state.absoluteOffset(parser.currentTokenLocation().getByteOffset());
                    skipValue(value, parser, state);
                    int end = state.absoluteOffset(parser.currentLocation().getByteOffset());
                    fields.coordinates = new ValueFence(start, end);
                }
                case "bbox" -> parseBbox(value, parser, state);
                case "crs" -> unsupported(state.sourceId, "legacyCrs");
                default -> skipValue(value, parser, state);
            }
        }
        return finishGeometry(type, fields, state);
    }

    private static GeometryData finishGeometry(String type, GeometryFields fields, State state)
            throws JacksonException {
        if (type == null) {
            invalid(state.sourceId, "type", "missing");
        }
        if ("GeometryCollection".equals(type)) {
            unsupported(state.sourceId, "geometryCollection");
        }
        if ("FeatureCollection".equals(type)) {
            unsupported(state.sourceId, "nestedCollection");
        }
        if (!fields.coordinatesSeen) {
            invalid(state.sourceId, "coordinates", "missing");
        }
        if (fields.coordinates == null) {
            invalid(state.sourceId, "coordinates", "null");
        }
        State coordinates = state.replay(fields.coordinates.startInclusive());
        Geometry geometry;
        try (JsonParser parser = parser(state.bytes, fields.coordinates, state.limits)) {
            JsonToken token = nextRequired(parser, coordinates, "coordinates", "missing");
            geometry = parseGeometryCoordinates(type, token, parser, coordinates);
            requireEnd(parser, coordinates);
        }
        state.absorb(coordinates);
        return new GeometryData(geometry);
    }

    private static Geometry parseGeometryCoordinates(
            String type, JsonToken token, JsonParser parser, State state) throws JacksonException {
        return switch (type) {
            case "Point" -> parsePoint(token, parser, state);
            case "MultiPoint" ->
                    new MultiPointGeometry(
                            sequence(parseSequence(token, parser, state, 1, false), state));
            case "LineString" ->
                    new LineStringGeometry(
                            sequence(parseSequence(token, parser, state, 2, false), state));
            case "MultiLineString" -> parseMultiLineString(token, parser, state);
            case "Polygon" -> parsePolygon(token, parser, state);
            case "MultiPolygon" -> parseMultiPolygon(token, parser, state);
            case "GeometryCollection" -> {
                unsupported(state.sourceId, "geometryCollection");
                yield null;
            }
            default -> {
                invalid(state.sourceId, "type", "kind");
                yield null;
            }
        };
    }

    private static PointGeometry parsePoint(JsonToken token, JsonParser parser, State state)
            throws JacksonException {
        requireCurrent(token, JsonToken.START_ARRAY, state.sourceId, "coordinates", "kind");
        double[] position = parsePosition(parser, state, 1);
        state.owned(2L * Double.BYTES);
        return new PointGeometry(new Coordinate(position[0], position[1]));
    }

    private static double[] parseSequence(
            JsonToken token, JsonParser parser, State state, int minimum, boolean closed)
            throws JacksonException {
        DoubleAccumulator values = new DoubleAccumulator(state);
        parseSequenceInto(token, parser, state, minimum, closed, values, new int[] {0});
        return values.toArray();
    }

    private static int parseSequenceInto(
            JsonToken token,
            JsonParser parser,
            State state,
            int minimum,
            boolean closed,
            DoubleAccumulator values,
            int[] geometryPositions)
            throws JacksonException {
        requireCurrent(token, JsonToken.START_ARRAY, state.sourceId, "coordinates", "kind");
        int start = values.positionCount();
        JsonToken item = nextRequired(parser, state, "coordinates", "cardinality");
        while (item != JsonToken.END_ARRAY) {
            requireCurrent(item, JsonToken.START_ARRAY, state.sourceId, "coordinates", "kind");
            parsePositionInto(parser, state, ++geometryPositions[0], values);
            item = nextRequired(parser, state, "coordinates", "cardinality");
        }
        int count = values.positionCount() - start;
        if (count == 0) {
            unsupported(state.sourceId, "emptyGeometry");
        }
        if (count < minimum) {
            invalid(state.sourceId, "coordinates", "cardinality");
        }
        if (closed && !values.isClosed(start, values.positionCount())) {
            invalid(state.sourceId, "coordinates", "closure");
        }
        return count;
    }

    private static double[] parsePosition(JsonParser parser, State state, int geometryPositions)
            throws JacksonException {
        JsonToken xToken = nextRequired(parser, state, "coordinates", "cardinality");
        if (xToken == JsonToken.END_ARRAY) {
            unsupported(state.sourceId, "emptyGeometry");
        }
        if (!xToken.isNumeric()) {
            invalid(state.sourceId, "coordinates", "kind");
        }
        double x = coordinateNumber(parser, state, true);
        JsonToken yToken = nextRequired(parser, state, "coordinates", "cardinality");
        if (!yToken.isNumeric()) {
            invalid(state.sourceId, "coordinates", "cardinality");
        }
        double y = coordinateNumber(parser, state, false);
        finishPosition(parser, state, geometryPositions);
        return new double[] {x, y};
    }

    private static void parsePositionInto(
            JsonParser parser, State state, int geometryPositions, DoubleAccumulator values)
            throws JacksonException {
        JsonToken xToken = nextRequired(parser, state, "coordinates", "cardinality");
        if (xToken == JsonToken.END_ARRAY) {
            unsupported(state.sourceId, "emptyGeometry");
        }
        if (!xToken.isNumeric()) {
            invalid(state.sourceId, "coordinates", "kind");
        }
        double x = coordinateNumber(parser, state, true);
        JsonToken yToken = nextRequired(parser, state, "coordinates", "cardinality");
        if (!yToken.isNumeric()) {
            invalid(state.sourceId, "coordinates", "cardinality");
        }
        double y = coordinateNumber(parser, state, false);
        finishPosition(parser, state, geometryPositions);
        values.add(x, y);
    }

    private static void finishPosition(JsonParser parser, State state, int geometryPositions)
            throws JacksonException {
        if (next(parser, state) != JsonToken.END_ARRAY) {
            unsupported(state.sourceId, "positionArity");
        }
        state.position(geometryPositions);
    }

    private static MultiLineStringGeometry parseMultiLineString(
            JsonToken token, JsonParser parser, State state) throws JacksonException {
        requireCurrent(token, JsonToken.START_ARRAY, state.sourceId, "coordinates", "kind");
        DoubleAccumulator values = new DoubleAccumulator(state);
        IntAccumulator offsets = new IntAccumulator(state);
        offsets.add(0);
        int[] geometryPositions = {0};
        JsonToken part = nextRequired(parser, state, "coordinates", "cardinality");
        while (part != JsonToken.END_ARRAY) {
            state.part();
            parseSequenceInto(part, parser, state, 2, false, values, geometryPositions);
            offsets.add(values.positionCount());
            part = nextRequired(parser, state, "coordinates", "cardinality");
        }
        if (offsets.size() == 1) {
            unsupported(state.sourceId, "emptyGeometry");
        }
        int[] packedOffsets = offsets.toArray();
        state.owned(Math.multiplyExact((long) packedOffsets.length, Integer.BYTES));
        return MultiLineStringGeometry.of(sequence(values.toArray(), state), packedOffsets);
    }

    private static PolygonGeometry parsePolygon(JsonToken token, JsonParser parser, State state)
            throws JacksonException {
        requireCurrent(token, JsonToken.START_ARRAY, state.sourceId, "coordinates", "kind");
        List<CoordinateSequence> rings = new ArrayList<>();
        int[] geometryPositions = {0};
        JsonToken ring = nextRequired(parser, state, "coordinates", "cardinality");
        while (ring != JsonToken.END_ARRAY) {
            state.part();
            DoubleAccumulator values = new DoubleAccumulator(state);
            parseSequenceInto(ring, parser, state, 4, true, values, geometryPositions);
            rings.add(sequence(values.toArray(), state));
            ring = nextRequired(parser, state, "coordinates", "cardinality");
        }
        if (rings.isEmpty()) {
            unsupported(state.sourceId, "emptyGeometry");
        }
        state.owned(Math.multiplyExact((long) rings.size(), 8));
        return new PolygonGeometry(rings.get(0), rings.subList(1, rings.size()));
    }

    private static MultiPolygonGeometry parseMultiPolygon(
            JsonToken token, JsonParser parser, State state) throws JacksonException {
        requireCurrent(token, JsonToken.START_ARRAY, state.sourceId, "coordinates", "kind");
        DoubleAccumulator values = new DoubleAccumulator(state);
        IntAccumulator ringOffsets = new IntAccumulator(state);
        IntAccumulator polygonOffsets = new IntAccumulator(state);
        ringOffsets.add(0);
        polygonOffsets.add(0);
        int[] geometryPositions = {0};
        int rings = 0;
        JsonToken polygon = nextRequired(parser, state, "coordinates", "cardinality");
        while (polygon != JsonToken.END_ARRAY) {
            state.part();
            requireCurrent(polygon, JsonToken.START_ARRAY, state.sourceId, "coordinates", "kind");
            int polygonRings = 0;
            JsonToken ring = nextRequired(parser, state, "coordinates", "cardinality");
            while (ring != JsonToken.END_ARRAY) {
                state.part();
                parseSequenceInto(ring, parser, state, 4, true, values, geometryPositions);
                ringOffsets.add(values.positionCount());
                polygonRings++;
                rings++;
                ring = nextRequired(parser, state, "coordinates", "cardinality");
            }
            if (polygonRings == 0) {
                unsupported(state.sourceId, "emptyGeometry");
            }
            polygonOffsets.add(rings);
            polygon = nextRequired(parser, state, "coordinates", "cardinality");
        }
        if (polygonOffsets.size() == 1) {
            unsupported(state.sourceId, "emptyGeometry");
        }
        int[] packedRingOffsets = ringOffsets.toArray();
        int[] packedPolygonOffsets = polygonOffsets.toArray();
        state.owned(
                Math.multiplyExact(
                        (long) packedRingOffsets.length + packedPolygonOffsets.length,
                        Integer.BYTES));
        return MultiPolygonGeometry.of(
                sequence(values.toArray(), state), packedRingOffsets, packedPolygonOffsets);
    }

    private static CoordinateSequence sequence(double[] packed, State state) {
        state.owned(Math.multiplyExact((long) packed.length, Double.BYTES));
        return CoordinateSequence.of(packed);
    }

    private static Map<String, Object> parseProperties(
            JsonToken token, JsonParser parser, State state) throws JacksonException {
        if (token == JsonToken.VALUE_NULL) {
            return Map.of();
        }
        requireCurrent(token, JsonToken.START_OBJECT, state.sourceId, "properties", "kind");
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        JsonToken next;
        while ((next = next(parser, state)) != JsonToken.END_OBJECT) {
            requireCurrent(next, JsonToken.PROPERTY_NAME, state.sourceId, "properties", "kind");
            String name = memberName(parser, state);
            if (name.isBlank() || name.length() > 256) {
                invalid(state.sourceId, "propertyName", "length");
            }
            if (values.size() >= state.limits.maximumPropertiesPerFeature()) {
                throw limit(
                        state.sourceId,
                        "propertiesPerFeature",
                        values.size() + 1L,
                        state.limits.maximumPropertiesPerFeature());
            }
            state.property();
            JsonToken value = nextRequired(parser, state, "propertyValue", "missing");
            Object scalar =
                    switch (value) {
                        case VALUE_STRING -> readString(parser, state);
                        case VALUE_TRUE -> true;
                        case VALUE_FALSE -> false;
                        case VALUE_NULL -> AttributeNull.INSTANCE;
                        case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> propertyNumber(parser, state);
                        case START_ARRAY, START_OBJECT -> {
                            unsupported(state.sourceId, "nestedProperty");
                            yield null;
                        }
                        default -> {
                            invalid(state.sourceId, "propertyValue", "kind");
                            yield null;
                        }
                    };
            values.put(name, scalar);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String parseId(JsonToken token, JsonParser parser, State state)
            throws JacksonException {
        return switch (token) {
            case VALUE_STRING -> "string:" + readString(parser, state);
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT ->
                    "number:" + normalizedDecimal(parser, state).toString();
            default -> {
                invalid(state.sourceId, "id", "kind");
                yield null;
            }
        };
    }

    private static void parseBbox(JsonToken token, JsonParser parser, State state)
            throws JacksonException {
        requireCurrent(token, JsonToken.START_ARRAY, state.sourceId, "bbox", "kind");
        double[] values = new double[4];
        for (int index = 0; index < values.length; index++) {
            JsonToken number = nextRequired(parser, state, "bbox", "cardinality");
            if (!number.isNumeric()) {
                invalid(state.sourceId, "bbox", "kind");
            }
            values[index] = finiteNumber(parser, state);
        }
        if (next(parser, state) != JsonToken.END_ARRAY) {
            invalid(state.sourceId, "bbox", "cardinality");
        }
        if (values[0] < -180
                || values[0] > 180
                || values[2] < -180
                || values[2] > 180
                || values[1] < -90
                || values[1] > 90
                || values[3] < -90
                || values[3] > 90
                || values[1] > values[3]) {
            invalid(state.sourceId, "bbox", "range");
        }
    }

    private static void skipValue(JsonToken token, JsonParser parser, State state)
            throws JacksonException {
        if (token == JsonToken.VALUE_STRING) {
            readString(parser, state);
            return;
        }
        if (token.isNumeric()) {
            normalizedDecimal(parser, state);
            return;
        }
        if (token != JsonToken.START_ARRAY && token != JsonToken.START_OBJECT) {
            return;
        }
        JsonToken expectedEnd =
                token == JsonToken.START_ARRAY ? JsonToken.END_ARRAY : JsonToken.END_OBJECT;
        JsonToken next;
        while ((next = nextRequired(parser, state, "foreign", "missing")) != expectedEnd) {
            if (next == JsonToken.PROPERTY_NAME) {
                memberName(parser, state);
                next = nextRequired(parser, state, "foreign", "missing");
            }
            skipValue(next, parser, state);
        }
    }

    private static String uniqueString(
            String existing, JsonToken token, JsonParser parser, State state, String field)
            throws JacksonException {
        if (existing != null) {
            duplicate(state.sourceId);
        }
        if (token != JsonToken.VALUE_STRING) {
            invalid(state.sourceId, field, "kind");
        }
        return readString(parser, state);
    }

    private static String memberName(JsonParser parser, State state) throws JacksonException {
        state.member();
        String value = parser.getString();
        validateUnicode(value, state.sourceId);
        if (value.length() > state.limits.maximumMemberNameCharacters()) {
            throw limit(
                    state.sourceId,
                    "memberNameCharacters",
                    value.length(),
                    state.limits.maximumMemberNameCharacters());
        }
        state.characters(value.length());
        return value;
    }

    private static String readString(JsonParser parser, State state) throws JacksonException {
        String value = parser.getString();
        validateUnicode(value, state.sourceId);
        if (value.length() > state.limits.maximumScalarCharacters()) {
            throw limit(
                    state.sourceId,
                    "scalarCharacters",
                    value.length(),
                    state.limits.maximumScalarCharacters());
        }
        state.characters(value.length());
        return value;
    }

    private static Object propertyNumber(JsonParser parser, State state) throws JacksonException {
        BigDecimal value = normalizedDecimal(parser, state);
        try {
            return value.longValueExact();
        } catch (ArithmeticException ignored) {
            return value;
        }
    }

    private static double coordinateNumber(JsonParser parser, State state, boolean longitude)
            throws JacksonException {
        double value = finiteNumber(parser, state);
        if (longitude ? value < -180 || value > 180 : value < -90 || value > 90) {
            invalid(state.sourceId, "coordinates", "range");
        }
        return value == 0 ? 0 : value;
    }

    private static double finiteNumber(JsonParser parser, State state) throws JacksonException {
        BigDecimal decimal = normalizedDecimal(parser, state);
        double value = decimal.doubleValue();
        if (!Double.isFinite(value)) {
            invalid(state.sourceId, "coordinates", "nonFinite");
        }
        return value;
    }

    private static BigDecimal normalizedDecimal(JsonParser parser, State state)
            throws JacksonException {
        String token = parser.getString();
        if (token.length() > state.limits.maximumNumberCharacters()) {
            throw limit(
                    state.sourceId,
                    "numberCharacters",
                    token.length(),
                    state.limits.maximumNumberCharacters());
        }
        BigDecimal value;
        try {
            value = new BigDecimal(token);
        } catch (NumberFormatException failure) {
            invalid(state.sourceId, "propertyValue", "number");
            return null;
        }
        BigDecimal normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        long adjusted = (long) normalized.precision() - normalized.scale() - 1L;
        if (normalized.precision() > 34 || adjusted < -308 || adjusted > 308) {
            invalid(state.sourceId, "propertyValue", "number");
        }
        return normalized;
    }

    private static JsonToken next(JsonParser parser, State state) throws JacksonException {
        state.poll();
        JsonToken token = parser.nextToken();
        state.token(token);
        state.poll();
        return token;
    }

    private static JsonToken nextRequired(
            JsonParser parser, State state, String field, String reason) throws JacksonException {
        JsonToken token = next(parser, state);
        if (token == null) {
            invalid(state.sourceId, field, reason);
        }
        return token;
    }

    private static void requireToken(
            JsonParser parser, State state, JsonToken expected, String field, String reason)
            throws JacksonException {
        requireCurrent(next(parser, state), expected, state.sourceId, field, reason);
    }

    private static void requireCurrent(
            JsonToken actual, JsonToken expected, String sourceId, String field, String reason) {
        if (actual != expected) {
            invalid(sourceId, field, reason);
        }
    }

    private static boolean isGeometryType(String value) {
        return switch (value) {
            case "Point",
                    "MultiPoint",
                    "LineString",
                    "MultiLineString",
                    "Polygon",
                    "MultiPolygon" ->
                    true;
            default -> false;
        };
    }

    private static int bomOffset(byte[] bytes) {
        return bytes.length >= 3
                        && (bytes[0] & 0xff) == 0xef
                        && (bytes[1] & 0xff) == 0xbb
                        && (bytes[2] & 0xff) == 0xbf
                ? 3
                : 0;
    }

    private static void rejectUnsupportedBom(byte[] bytes, String sourceId) {
        boolean utf16 =
                bytes.length >= 2
                        && (((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe)
                                || ((bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff));
        boolean utf32 =
                bytes.length >= 4
                        && (((bytes[0] & 0xff) == 0x00
                                        && (bytes[1] & 0xff) == 0x00
                                        && (bytes[2] & 0xff) == 0xfe
                                        && (bytes[3] & 0xff) == 0xff)
                                || ((bytes[0] & 0xff) == 0xff
                                        && (bytes[1] & 0xff) == 0xfe
                                        && (bytes[2] & 0xff) == 0x00
                                        && (bytes[3] & 0xff) == 0x00));
        if (utf16 || utf32) {
            throw failure(
                    sourceId,
                    "GEOJSON_ENCODING_INVALID",
                    "GeoJSON encoding is unsupported",
                    Map.of("reason", "unsupportedBom"));
        }
    }

    private static void validateUtf8(byte[] bytes, String sourceId) {
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(
                            ByteBuffer.wrap(
                                    bytes, bomOffset(bytes), bytes.length - bomOffset(bytes)));
        } catch (CharacterCodingException failure) {
            throw failure(
                    sourceId,
                    "GEOJSON_ENCODING_INVALID",
                    "GeoJSON UTF-8 is invalid",
                    Map.of("reason", "malformedUtf8"),
                    failure);
        }
    }

    private static void validateUnicode(String value, String sourceId) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    throw failure(
                            sourceId,
                            "GEOJSON_ENCODING_INVALID",
                            "GeoJSON string contains an invalid Unicode scalar",
                            Map.of("reason", "unicodeScalar"));
                }
            } else if (Character.isLowSurrogate(current)) {
                throw failure(
                        sourceId,
                        "GEOJSON_ENCODING_INVALID",
                        "GeoJSON string contains an invalid Unicode scalar",
                        Map.of("reason", "unicodeScalar"));
            }
        }
    }

    private static int checkedOffset(long value, String sourceId) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw failure(
                    sourceId,
                    "GEOJSON_JSON_INVALID",
                    "GeoJSON parser location is invalid",
                    Map.of("reason", "syntax"));
        }
        return (int) value;
    }

    private static String jacksonReason(JacksonException failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName().contains("Dup")
                        || (message != null
                                && message.toLowerCase(java.util.Locale.ROOT).contains("duplicate"))
                ? "duplicateMember"
                : "syntax";
    }

    private static SourceException constraintFailure(
            String sourceId, GeoJsonLimits limits, StreamConstraintsException failure) {
        String message = failure.getMessage();
        String classification = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (classification.contains("token count")) {
            return limit(sourceId, "tokens", limits.maximumTokens() + 1L, limits.maximumTokens());
        }
        if (classification.contains("depth")) {
            return limit(
                    sourceId,
                    "nestingDepth",
                    limits.maximumNestingDepth() + 1L,
                    limits.maximumNestingDepth());
        }
        if (classification.contains("name") && classification.contains("length")) {
            return limit(
                    sourceId,
                    "memberNameCharacters",
                    limits.maximumMemberNameCharacters() + 1L,
                    limits.maximumMemberNameCharacters());
        }
        if (classification.contains("string") && classification.contains("length")) {
            return limit(
                    sourceId,
                    "scalarCharacters",
                    limits.maximumScalarCharacters() + 1L,
                    limits.maximumScalarCharacters());
        }
        if (classification.contains("number") && classification.contains("length")) {
            return limit(
                    sourceId,
                    "numberCharacters",
                    limits.maximumNumberCharacters() + 1L,
                    limits.maximumNumberCharacters());
        }
        return failure(
                sourceId,
                "GEOJSON_JSON_INVALID",
                "GeoJSON syntax is invalid",
                Map.of("reason", "syntax"),
                failure);
    }

    private static void duplicate(String sourceId) {
        throw failure(
                sourceId,
                "GEOJSON_JSON_INVALID",
                "GeoJSON object contains a duplicate member",
                Map.of("reason", "duplicateMember"));
    }

    private static void invalid(String sourceId, String field, String reason) {
        throw failure(
                sourceId,
                "GEOJSON_VALUE_INVALID",
                "GeoJSON value is invalid",
                context("field", field, "reason", reason));
    }

    private static void unsupported(String sourceId, String construct) {
        throw failure(
                sourceId,
                "GEOJSON_PROFILE_UNSUPPORTED",
                "GeoJSON construct is outside the supported profile",
                Map.of("construct", construct));
    }

    static SourceException limit(String sourceId, String limit, long requested, long maximum) {
        return failure(
                sourceId,
                "SOURCE_LIMIT_EXCEEDED",
                "GeoJSON opening limit exceeded",
                context(
                        "scope",
                        "geojsonOpen",
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)));
    }

    private static void checkCancelled(String sourceId, CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw failure(
                    sourceId,
                    "SOURCE_CANCELLED",
                    "GeoJSON operation was cancelled",
                    Map.of("operation", "geojson-open"));
        }
    }

    private static Map<String, String> context(String... entries) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            context.put(entries[index], entries[index + 1]);
        }
        return context;
    }

    static SourceException failure(
            String sourceId, String code, String message, Map<String, String> context) {
        return failure(sourceId, code, message, context, null);
    }

    static SourceException failure(
            String sourceId,
            String code,
            String message,
            Map<String, String> context,
            Throwable cause) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(componentLocation()),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal, cause);
    }

    private static DiagnosticLocation componentLocation() {
        return new DiagnosticLocation(
                Optional.of(COMPONENT),
                OptionalLong.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                OptionalLong.empty());
    }

    record Opening(
            List<Entry> entries,
            long emittedFeatureCount,
            Optional<Envelope> extent,
            DiagnosticReport warnings) {}

    record Entry(
            int startInclusive,
            int endExclusive,
            Envelope envelope,
            int physicalIndex,
            boolean emitted,
            boolean bareGeometry) {}

    private record RootData(List<Entry> entries) {}

    private record ValueFence(int startInclusive, int endExclusive) {}

    private record ParsedFeature(FeatureRecord record, Entry entry) {
        Entry withFence(int start, int end) {
            return new Entry(
                    start, end, entry.envelope(), entry.physicalIndex(), entry.emitted(), false);
        }
    }

    private record GeometryData(Geometry geometry) {}

    private static final class DoubleAccumulator {
        private final State state;
        private double[] values;
        private int size;

        private DoubleAccumulator(State state) {
            this.state = state;
            values = new double[16];
            state.owned(16L * Double.BYTES);
        }

        private void add(double x, double y) {
            if (size > values.length - 2) {
                int nextLength = Math.multiplyExact(values.length, 2);
                state.owned(Math.multiplyExact((long) nextLength, Double.BYTES));
                values = java.util.Arrays.copyOf(values, nextLength);
            }
            values[size++] = x;
            values[size++] = y;
        }

        private int positionCount() {
            return size / 2;
        }

        private boolean isClosed(int startPosition, int endPosition) {
            int first = Math.multiplyExact(startPosition, 2);
            int last = Math.multiplyExact(endPosition - 1, 2);
            return Double.compare(values[first], values[last]) == 0
                    && Double.compare(values[first + 1], values[last + 1]) == 0;
        }

        private double[] toArray() {
            if (size == values.length) {
                return values;
            }
            state.owned(Math.multiplyExact((long) size, Double.BYTES));
            return java.util.Arrays.copyOf(values, size);
        }
    }

    private static final class IntAccumulator {
        private final State state;
        private int[] values;
        private int size;

        private IntAccumulator(State state) {
            this.state = state;
            values = new int[8];
            state.owned(8L * Integer.BYTES);
        }

        private void add(int value) {
            if (size == values.length) {
                int nextLength = Math.multiplyExact(values.length, 2);
                state.owned(Math.multiplyExact((long) nextLength, Integer.BYTES));
                values = java.util.Arrays.copyOf(values, nextLength);
            }
            values[size++] = value;
        }

        private int size() {
            return size;
        }

        private int[] toArray() {
            state.owned(Math.multiplyExact((long) size, Integer.BYTES));
            return java.util.Arrays.copyOf(values, size);
        }
    }

    private static final class FeatureFields {
        private boolean geometrySeen;
        private GeometryData geometry;
        private boolean propertiesSeen;
        private Map<String, Object> properties;
        private boolean idSeen;
        private String id;
    }

    private static final class GeometryFields {
        private boolean coordinatesSeen;
        private ValueFence coordinates;
    }

    private static final class State {
        private final byte[] bytes;
        private final String sourceId;
        private final GeoJsonLimits limits;
        private final CancellationToken cancellation;
        private final int baseOffset;
        private final boolean countLexical;
        private final Map<String, Integer> ids = new java.util.HashMap<>();
        private long ownedBytes;
        private long tokens;
        private int depth;
        private int members;
        private int physicalFeatures;
        private int positions;
        private int parts;
        private int properties;
        private int characters;
        private int polls;

        private State(
                byte[] bytes,
                String sourceId,
                GeoJsonLimits limits,
                CancellationToken cancellation,
                int baseOffset,
                boolean countLexical,
                long initialOwnedBytes) {
            this.bytes = bytes;
            this.sourceId = sourceId;
            this.limits = limits;
            this.cancellation = cancellation;
            this.baseOffset = baseOffset;
            this.countLexical = countLexical;
            owned(initialOwnedBytes);
        }

        private State replay(int offset) {
            State replay =
                    new State(bytes, sourceId, limits, cancellation, offset, false, ownedBytes);
            replay.positions = positions;
            replay.parts = parts;
            return replay;
        }

        private void absorb(State parsed) {
            ownedBytes = parsed.ownedBytes;
            positions = parsed.positions;
            parts = parsed.parts;
        }

        private int absoluteOffset(long relative) {
            return Math.addExact(baseOffset, checkedOffset(relative, sourceId));
        }

        private void poll() {
            if ((polls++ & 4095) == 0) {
                checkCancelled(sourceId, cancellation);
            }
        }

        private void token(JsonToken token) {
            if (!countLexical || token == null) {
                return;
            }
            if (++tokens > limits.maximumTokens()) {
                throw limit(sourceId, "tokens", tokens, limits.maximumTokens());
            }
            if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
                if (++depth > limits.maximumNestingDepth()) {
                    throw limit(sourceId, "nestingDepth", depth, limits.maximumNestingDepth());
                }
            } else if (token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) {
                depth--;
            }
        }

        private void member() {
            if (!countLexical) {
                return;
            }
            if (++members > limits.maximumObjectMembers()) {
                throw limit(sourceId, "objectMembers", members, limits.maximumObjectMembers());
            }
            owned(16);
        }

        private int physicalFeature() {
            if (++physicalFeatures > limits.maximumPhysicalFeatures()) {
                throw limit(
                        sourceId,
                        "physicalFeatures",
                        physicalFeatures,
                        limits.maximumPhysicalFeatures());
            }
            owned(64);
            return physicalFeatures - 1;
        }

        private void position(int geometryPositions) {
            if (++positions > limits.maximumTotalPositions()) {
                throw limit(sourceId, "totalPositions", positions, limits.maximumTotalPositions());
            }
            if (geometryPositions > limits.maximumPositionsPerGeometry()) {
                throw limit(
                        sourceId,
                        "positionsPerGeometry",
                        geometryPositions,
                        limits.maximumPositionsPerGeometry());
            }
            owned(16);
        }

        private void part() {
            if (++parts > limits.maximumParts()) {
                throw limit(sourceId, "parts", parts, limits.maximumParts());
            }
            owned(4);
        }

        private void property() {
            if (++properties > limits.maximumTotalProperties()) {
                throw limit(
                        sourceId, "totalProperties", properties, limits.maximumTotalProperties());
            }
            owned(32);
        }

        private void characters(int count) {
            if (!countLexical) {
                return;
            }
            characters = Math.addExact(characters, count);
            if (characters > limits.maximumAggregateCharacters()) {
                throw limit(
                        sourceId,
                        "aggregateCharacters",
                        characters,
                        limits.maximumAggregateCharacters());
            }
            owned(Math.multiplyExact(2L, count));
        }

        private void uniqueId(String id, int physicalIndex) {
            Integer firstIndex = ids.putIfAbsent(id, physicalIndex);
            if (firstIndex != null) {
                throw failure(
                        sourceId,
                        "SOURCE_DUPLICATE_FEATURE_ID",
                        "Source feature IDs must be unique",
                        context(
                                "firstIndex",
                                Integer.toString(firstIndex),
                                "duplicateIndex",
                                Integer.toString(physicalIndex)));
            }
            owned(32L + Math.multiplyExact(2L, id.length()));
        }

        private void owned(long additional) {
            long requested;
            try {
                requested = Math.addExact(ownedBytes, additional);
            } catch (ArithmeticException failure) {
                throw limit(sourceId, "ownedBytes", Long.MAX_VALUE, limits.maximumOwnedBytes());
            }
            if (requested > limits.maximumOwnedBytes()) {
                throw limit(sourceId, "ownedBytes", requested, limits.maximumOwnedBytes());
            }
            ownedBytes = requested;
        }
    }

    private static final class WarningCollector {
        private final String sourceId;
        private final int maximum;
        private final List<SourceDiagnostic> warnings = new ArrayList<>();
        private long omitted;

        private WarningCollector(String sourceId, int maximum) {
            this.sourceId = sourceId;
            this.maximum = maximum;
        }

        private void add(String code, String message) {
            if (warnings.size() < maximum) {
                warnings.add(
                        new SourceDiagnostic(
                                code,
                                DiagnosticSeverity.WARNING,
                                sourceId,
                                Optional.of(componentLocation()),
                                message,
                                Map.of()));
            } else {
                omitted++;
            }
        }

        private DiagnosticReport report() {
            return new DiagnosticReport(warnings, omitted);
        }
    }
}
