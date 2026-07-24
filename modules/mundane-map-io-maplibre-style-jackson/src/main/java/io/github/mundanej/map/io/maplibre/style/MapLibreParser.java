package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

final class MapLibreParser {
    private final JsonParser parser;
    private final MapLibreReadOptions options;
    private final MapLibreReadLimits limits;
    private int members;
    private int characters;
    private int metadataEntries;
    private int expressionNodes;
    private int stops;
    private int categories;
    private int producedRules;
    private int catalogReferences;
    private long ownedBytes;

    MapLibreParser(JsonParser parser, MapLibreReadOptions options, int inputBytes) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.options = Objects.requireNonNull(options, "options");
        limits = options.limits();
        ownedBytes = inputBytes;
        if (ownedBytes > limits.maximumOwnedBytes()) {
            throw limit("/", "ownedBytes", ownedBytes, limits.maximumOwnedBytes());
        }
    }

    MapLibreStyle parse() throws JacksonException {
        JsonToken first = parser.nextToken();
        if (first != JsonToken.START_OBJECT) {
            throw invalid("/", "rootKind");
        }
        Map<String, Object> root = readObject("/", 1);
        if (parser.nextToken() != null) {
            throw invalid("/", "trailingContent");
        }
        requireMembers(
                root,
                Set.of(
                        "version",
                        "sources",
                        "layers",
                        "name",
                        "metadata",
                        "center",
                        "zoom",
                        "bearing",
                        "pitch"),
                "MAPLIBRE_ROOT_UNSUPPORTED",
                "/");
        int version = integer(root.get("version"), "/version");
        if (version != 8) {
            throw failure("MAPLIBRE_VERSION_UNSUPPORTED", "/version", "version");
        }
        Object sourcesValue = require(root, "sources", "/sources");
        Object layersValue = require(root, "layers", "/layers");
        Optional<String> name =
                root.containsKey("name")
                        ? Optional.of(retainedText(root.get("name"), "/name"))
                        : Optional.empty();
        Map<String, Object> metadata = metadata(root.get("metadata"), "/metadata");
        MapLibreCamera camera = camera(root);
        List<MapLibreSourceDescriptor> sources = sources(sourcesValue);
        List<MapLibreLayer> layers = layers(layersValue);
        return new MapLibreStyle(name, metadata, camera, sources, layers);
    }

    private MapLibreCamera camera(Map<String, Object> root) {
        OptionalDouble longitude = OptionalDouble.empty();
        OptionalDouble latitude = OptionalDouble.empty();
        if (root.containsKey("center")) {
            List<Object> center = array(root.get("center"), "/center");
            if (center.size() != 2) {
                throw value("/center", "size");
            }
            longitude = OptionalDouble.of(number(center.get(0), "/center/0", -180, 180));
            latitude = OptionalDouble.of(number(center.get(1), "/center/1", -90, 90));
        }
        return new MapLibreCamera(
                longitude,
                latitude,
                optionalNumber(root, "zoom", 0, 24),
                optionalFinite(root, "bearing"),
                optionalNumber(root, "pitch", 0, 180));
    }

    private List<MapLibreSourceDescriptor> sources(Object value) {
        Map<String, Object> object = object(value, "/sources");
        if (object.size() > limits.maximumSources()) {
            throw limit("/sources", "sources", object.size(), limits.maximumSources());
        }
        ArrayList<MapLibreSourceDescriptor> result = new ArrayList<>(object.size());
        int index = 0;
        for (Map.Entry<String, Object> entry : object.entrySet()) {
            String location = "/sources/" + index++;
            Map<String, Object> source = object(entry.getValue(), location);
            requireMembers(
                    source,
                    Set.of("type", "data", "attribution"),
                    "MAPLIBRE_SOURCE_UNSUPPORTED",
                    location);
            if (!"geojson".equals(text(require(source, "type", location), location + "/type"))) {
                throw failure("MAPLIBRE_SOURCE_UNSUPPORTED", location + "/type", "type");
            }
            String id = identifier(entry.getKey(), location + "/id");
            Optional<String> data =
                    source.containsKey("data")
                            ? Optional.of(retainedText(source.get("data"), location + "/data"))
                            : Optional.empty();
            Optional<String> attribution =
                    source.containsKey("attribution")
                            ? Optional.of(
                                    retainedText(
                                            source.get("attribution"), location + "/attribution"))
                            : Optional.empty();
            reserve(160, location);
            result.add(new MapLibreSourceDescriptor(id, data, attribution));
        }
        return List.copyOf(result);
    }

    private List<MapLibreLayer> layers(Object value) {
        List<Object> array = array(value, "/layers");
        if (array.isEmpty()) {
            throw value("/layers", "empty");
        }
        if (array.size() > limits.maximumLayers()) {
            throw limit("/layers", "layers", array.size(), limits.maximumLayers());
        }
        ArrayList<MapLibreLayer> result = new ArrayList<>(array.size());
        HashSet<String> ids = new HashSet<>();
        for (int index = 0; index < array.size(); index++) {
            String location = "/layers/" + index;
            MapLibreLayer layer = layer(object(array.get(index), location), location);
            if (!ids.add(layer.id())) {
                throw value(location + "/id", "duplicate");
            }
            reserve(256, location);
            result.add(layer);
        }
        return List.copyOf(result);
    }

    private MapLibreLayer layer(Map<String, Object> object, String location) {
        requireMembers(
                object,
                Set.of(
                        "id",
                        "type",
                        "source",
                        "filter",
                        "minzoom",
                        "maxzoom",
                        "layout",
                        "paint",
                        "metadata"),
                "MAPLIBRE_LAYER_UNSUPPORTED",
                location);
        String id = text(require(object, "id", location), location + "/id");
        String source = text(require(object, "source", location), location + "/source");
        String typeText = text(require(object, "type", location), location + "/type");
        MapLibreLayerType type =
                switch (typeText) {
                    case "circle" -> MapLibreLayerType.CIRCLE;
                    case "line" -> MapLibreLayerType.LINE;
                    case "fill" -> MapLibreLayerType.FILL;
                    case "symbol" -> MapLibreLayerType.SYMBOL;
                    default ->
                            throw failure("MAPLIBRE_LAYER_UNSUPPORTED", location + "/type", "type");
                };
        double min =
                object.containsKey("minzoom")
                        ? number(object.get("minzoom"), location + "/minzoom", 0, 24)
                        : 0;
        double max =
                object.containsKey("maxzoom")
                        ? number(object.get("maxzoom"), location + "/maxzoom", 0, 24)
                        : 24;
        if (min >= max) {
            throw value(location, "zoomRange");
        }
        Map<String, Object> layout =
                object.containsKey("layout")
                        ? object(object.get("layout"), location + "/layout")
                        : Map.of();
        boolean visible = visibility(layout, type, location + "/layout");
        Map<String, Object> paint =
                object.containsKey("paint")
                        ? object(object.get("paint"), location + "/paint")
                        : Map.of();
        MapLibreExpressionAccounting.Counts propertyExpressions =
                MapLibreExpressionAccounting.count(
                        layout, paint, limits, options.cancellation(), location);
        reserve(propertyExpressions.ownedBytes(), location);
        expressionNodes =
                aggregate(
                        expressionNodes,
                        propertyExpressions.nodes(),
                        limits.maximumExpressionNodes(),
                        location,
                        "expressionNodes");
        stops =
                aggregate(
                        stops,
                        propertyExpressions.stops(),
                        limits.maximumStops(),
                        location,
                        "stops");
        categories =
                aggregate(
                        categories,
                        propertyExpressions.categories(),
                        limits.maximumCategories(),
                        location,
                        "categories");
        producedRules =
                aggregate(
                        producedRules,
                        propertyExpressions.rules(),
                        limits.maximumProducedRules(),
                        location,
                        "producedRules");
        MapLibreSymbolSpec symbolSpec =
                type == MapLibreLayerType.SYMBOL
                        ? MapLibreSymbolParser.parse(
                                layout, paint, limits, options.cancellation(), location)
                        : null;
        if (symbolSpec != null) {
            catalogReferences =
                    aggregate(
                            catalogReferences,
                            symbolSpec.catalogReferences(),
                            limits.maximumCatalogReferences(),
                            location + "/layout/icon-image",
                            "catalogReferences");
        }
        Optional<FeaturePortrayal> validated =
                type == MapLibreLayerType.SYMBOL
                        ? visible ? Optional.of(deferredPortrayal(symbolSpec)) : Optional.empty()
                        : MapLibreSymbols.literal(
                                type,
                                layout,
                                paint,
                                location,
                                visible,
                                limits,
                                options.cancellation());
        if (object.containsKey("filter")) {
            MapLibreFilters.CompiledFilter filter =
                    MapLibreFilters.compileLayerFilter(
                            object.get("filter"),
                            limits,
                            expressionNodes,
                            options.cancellation(),
                            location + "/filter");
            expressionNodes =
                    aggregate(
                            expressionNodes,
                            filter.nodes(),
                            limits.maximumExpressionNodes(),
                            location + "/filter",
                            "expressionNodes");
            if (validated.isPresent()) {
                producedRules =
                        aggregate(
                                producedRules,
                                1,
                                limits.maximumProducedRules(),
                                location + "/filter",
                                "producedRules");
            }
            if (symbolSpec == null) {
                validated = MapLibreFilters.apply(validated, filter.predicate());
            } else {
                symbolSpec = symbolSpec.withFilter(filter.predicate());
                validated = visible ? Optional.of(deferredPortrayal(symbolSpec)) : Optional.empty();
            }
        }
        Optional<FeaturePortrayal> portrayal = visible ? validated : Optional.empty();
        return new MapLibreLayer(
                id,
                source,
                type,
                visible,
                min,
                max,
                metadata(object.get("metadata"), location + "/metadata"),
                portrayal);
    }

    private static FeaturePortrayal deferredPortrayal(MapLibreSymbolSpec spec) {
        return FeaturePortrayal.markers(new FixedSymbolSelector(new MapLibreDeferredSymbol(spec)));
    }

    private boolean visibility(
            Map<String, Object> layout, MapLibreLayerType type, String location) {
        Set<String> accepted =
                switch (type) {
                    case CIRCLE, FILL -> Set.of("visibility");
                    case LINE -> Set.of("visibility", "line-cap", "line-join");
                    case SYMBOL -> layout.keySet();
                };
        requireMembers(layout, accepted, "MAPLIBRE_PROPERTY_UNSUPPORTED", location);
        if (!layout.containsKey("visibility")) {
            return true;
        }
        return switch (text(layout.get("visibility"), location + "/visibility")) {
            case "visible" -> true;
            case "none" -> false;
            default -> throw value(location + "/visibility", "enum");
        };
    }

    private Map<String, Object> metadata(Object value, String location) {
        if (value == null) {
            return Map.of();
        }
        Map<String, Object> metadata = object(value, location);
        if (metadata.size() > limits.maximumMetadataEntries()) {
            throw limit(
                    location, "metadataEntries", metadata.size(), limits.maximumMetadataEntries());
        }
        metadataEntries = Math.addExact(metadataEntries, metadata.size());
        if (metadataEntries > limits.maximumMetadataEntries()) {
            throw limit(
                    location, "metadataEntries", metadataEntries, limits.maximumMetadataEntries());
        }
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Object item = entry.getValue();
            if (!(item instanceof String
                    || item instanceof BigDecimal
                    || item instanceof Boolean
                    || item == AttributeNull.INSTANCE)) {
                throw value(location, "metadataValue");
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    private Map<String, Object> readObject(String location, int depth) throws JacksonException {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        JsonToken token;
        while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
            cancelled();
            if (token != JsonToken.PROPERTY_NAME) {
                throw invalid(location, "member");
            }
            if (++members > limits.maximumObjectMembers()) {
                throw limit(location, "objectMembers", members, limits.maximumObjectMembers());
            }
            String name = stringToken(location);
            JsonToken value = parser.nextToken();
            if (value == null) {
                throw invalid(location, "missingValue");
            }
            reserve(96, location);
            result.put(name, readValue(value, location + "/value", depth + 1));
        }
        return Collections.unmodifiableMap(result);
    }

    private List<Object> readArray(String location, int depth) throws JacksonException {
        ArrayList<Object> result = new ArrayList<>();
        JsonToken token;
        int index = 0;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            if (token == null) {
                throw invalid(location, "arrayEnd");
            }
            reserve(32, location);
            result.add(readValue(token, location + '/' + index++, depth + 1));
        }
        return List.copyOf(result);
    }

    private Object readValue(JsonToken token, String location, int depth) throws JacksonException {
        cancelled();
        if (depth > limits.maximumNestingDepth()) {
            throw limit(location, "nestingDepth", depth, limits.maximumNestingDepth());
        }
        return switch (token) {
            case START_OBJECT -> readObject(location, depth);
            case START_ARRAY -> readArray(location, depth);
            case VALUE_STRING -> stringToken(location);
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> decimal(location);
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NULL -> AttributeNull.INSTANCE;
            default -> throw invalid(location, "valueKind");
        };
    }

    private String stringToken(String location) throws JacksonException {
        String value = parser.getString();
        characters = Math.addExact(characters, value.length());
        if (value.length() > limits.maximumStringCharacters()) {
            throw limit(
                    location, "stringCharacters", value.length(), limits.maximumStringCharacters());
        }
        if (characters > limits.maximumAggregateCharacters()) {
            throw limit(
                    location,
                    "aggregateCharacters",
                    characters,
                    limits.maximumAggregateCharacters());
        }
        reserve(Math.addExact(40L, Math.multiplyExact(2L, value.length())), location);
        return value;
    }

    private BigDecimal decimal(String location) throws JacksonException {
        try {
            BigDecimal value = new BigDecimal(parser.getString());
            reserve(64, location);
            double finite = value.doubleValue();
            if (!Double.isFinite(finite)) {
                throw value(location, "nonFinite");
            }
            return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        } catch (NumberFormatException failure) {
            throw MapLibreStyles.failure(
                    "MAPLIBRE_VALUE_INVALID", location, Map.of("reason", "number"), failure);
        }
    }

    private void requireMembers(
            Map<String, Object> object, Set<String> accepted, String code, String location) {
        for (String member : object.keySet()) {
            if (!accepted.contains(member)) {
                throw MapLibreStyles.failure(
                        code, location + "/member", Map.of("reason", "member"));
            }
        }
    }

    private Object require(Map<String, Object> object, String member, String location) {
        if (!object.containsKey(member)) {
            throw value(location, "missing");
        }
        return object.get(member);
    }

    private int integer(Object value, String location) {
        if (!(value instanceof BigDecimal decimal)) {
            throw value(location, "kind");
        }
        try {
            return decimal.intValueExact();
        } catch (ArithmeticException failure) {
            throw value(location, "integer");
        }
    }

    private OptionalDouble optionalNumber(
            Map<String, Object> object, String member, double minimum, double maximum) {
        return object.containsKey(member)
                ? OptionalDouble.of(number(object.get(member), '/' + member, minimum, maximum))
                : OptionalDouble.empty();
    }

    private OptionalDouble optionalFinite(Map<String, Object> object, String member) {
        return object.containsKey(member)
                ? OptionalDouble.of(
                        number(
                                object.get(member),
                                '/' + member,
                                -Double.MAX_VALUE,
                                Double.MAX_VALUE))
                : OptionalDouble.empty();
    }

    private double number(Object value, String location, double minimum, double maximum) {
        if (!(value instanceof BigDecimal decimal)) {
            throw value(location, "kind");
        }
        double result = decimal.doubleValue();
        if (!Double.isFinite(result) || result < minimum || result > maximum) {
            throw value(location, "range");
        }
        return result == 0.0 ? 0.0 : result;
    }

    private String text(Object value, String location) {
        return identifier(value, location);
    }

    private String identifier(Object value, String location) {
        if (!(value instanceof String text) || text.isBlank() || !text.equals(text.strip())) {
            throw value(location, "text");
        }
        return text;
    }

    private String retainedText(Object value, String location) {
        if (!(value instanceof String text)) {
            throw value(location, "string");
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value, String location) {
        if (!(value instanceof Map<?, ?>)) {
            throw value(location, "object");
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> array(Object value, String location) {
        if (!(value instanceof List<?>)) {
            throw value(location, "array");
        }
        return (List<Object>) value;
    }

    private void cancelled() {
        if (options.cancellation().isCancellationRequested()) {
            throw MapLibreStyles.failure("MAPLIBRE_CANCELLED", "/", Map.of());
        }
    }

    private void reserve(long bytes, String location) {
        try {
            ownedBytes = Math.addExact(ownedBytes, bytes);
        } catch (ArithmeticException failure) {
            throw limit(location, "ownedBytes", Long.MAX_VALUE, limits.maximumOwnedBytes());
        }
        if (ownedBytes > limits.maximumOwnedBytes()) {
            throw limit(location, "ownedBytes", ownedBytes, limits.maximumOwnedBytes());
        }
    }

    private int aggregate(int current, int increment, int maximum, String location, String name) {
        int result;
        try {
            result = Math.addExact(current, increment);
        } catch (ArithmeticException failure) {
            throw limit(location, name, Long.MAX_VALUE, maximum);
        }
        if (result > maximum) {
            throw limit(location, name, result, maximum);
        }
        return result;
    }

    private MapLibreReadException invalid(String location, String reason) {
        return MapLibreStyles.failure("MAPLIBRE_JSON_INVALID", location, Map.of("reason", reason));
    }

    private MapLibreReadException value(String location, String reason) {
        return MapLibreStyles.failure("MAPLIBRE_VALUE_INVALID", location, Map.of("reason", reason));
    }

    private MapLibreReadException failure(String code, String location, String reason) {
        return MapLibreStyles.failure(code, location, Map.of("reason", reason));
    }

    private MapLibreReadException limit(String location, String limit, long actual, long maximum) {
        return MapLibreStyles.failure(
                "MAPLIBRE_LIMIT_EXCEEDED",
                location,
                Map.of(
                        "limit", limit,
                        "actual", Long.toString(actual),
                        "maximum", Long.toString(maximum)));
    }
}
