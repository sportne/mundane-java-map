package io.github.mundanej.map.io.maplibre.style;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.util.JsonRecyclerPools;

final class MapLibreJacksonFactory {
    private MapLibreJacksonFactory() {}

    static JsonFactory create(MapLibreReadLimits limits) {
        JsonFactory factory =
                JsonFactory.builder()
                        .disable(TokenStreamFactory.Feature.CHARSET_DETECTION)
                        .enable(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
                        .disable(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES)
                        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                        .disable(StreamReadFeature.USE_FAST_DOUBLE_PARSER)
                        .disable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER)
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .disable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                        .disable(JsonReadFeature.ALLOW_YAML_COMMENTS)
                        .disable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
                        .disable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                        .disable(JsonReadFeature.ALLOW_RS_CONTROL_CHAR)
                        .disable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                        .disable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
                        .disable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
                        .disable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
                        .disable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
                        .disable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                        .disable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
                        .disable(JsonReadFeature.ALLOW_MISSING_VALUES)
                        .disable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                        .recyclerPool(JsonRecyclerPools.nonRecyclingPool())
                        .streamReadConstraints(
                                StreamReadConstraints.builder()
                                        .maxNestingDepth(limits.maximumNestingDepth())
                                        .maxDocumentLength(limits.maximumInputBytes())
                                        .maxTokenCount(limits.maximumTokens())
                                        .maxStringLength(limits.maximumStringCharacters())
                                        .maxNameLength(limits.maximumStringCharacters())
                                        .maxNumberLength(128)
                                        .build())
                        .build();
        if (!factory.isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                || factory.isEnabled(TokenStreamFactory.Feature.CHARSET_DETECTION)
                || factory.isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)) {
            throw new IllegalStateException("Jackson factory policy mismatch");
        }
        return factory;
    }
}
