package io.github.mundanej.map.io.geojson;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonFactoryBuilder;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.util.JsonRecyclerPools;

final class GeoJsonFactories {
    private GeoJsonFactories() {}

    static JsonFactory reader(GeoJsonLimits limits) {
        StreamReadConstraints constraints =
                StreamReadConstraints.builder()
                        .maxNestingDepth(limits.maximumNestingDepth())
                        .maxDocumentLength(limits.maximumInputBytes())
                        .maxTokenCount(limits.maximumTokens())
                        .maxNumberLength(limits.maximumNumberCharacters())
                        .maxStringLength(limits.maximumScalarCharacters())
                        .maxNameLength(limits.maximumMemberNameCharacters())
                        .build();
        JsonFactoryBuilder builder =
                JsonFactory.builder()
                        .enable(TokenStreamFactory.Feature.CHARSET_DETECTION)
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
                        .streamReadConstraints(constraints);
        JsonFactory factory = builder.build();
        verify(factory, limits);
        return factory;
    }

    static JsonFactory writer(GeoJsonWriteLimits limits) {
        StreamWriteConstraints constraints =
                StreamWriteConstraints.builder()
                        .maxNestingDepth(limits.maximumNestingDepth())
                        .build();
        return JsonFactory.builder()
                .disable(StreamWriteFeature.AUTO_CLOSE_CONTENT)
                .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .disable(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)
                .disable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .disable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER)
                .recyclerPool(JsonRecyclerPools.nonRecyclingPool())
                .streamWriteConstraints(constraints)
                .build();
    }

    private static void verify(JsonFactory factory, GeoJsonLimits limits) {
        if (!factory.isEnabled(TokenStreamFactory.Feature.CHARSET_DETECTION)
                || !factory.isEnabled(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES)
                || factory.isEnabled(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES)
                || factory.isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                || factory.isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER)
                || factory.isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER)
                || !factory.isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                || factory.streamReadConstraints().getMaxNestingDepth()
                        != limits.maximumNestingDepth()
                || factory.streamReadConstraints().getMaxTokenCount() != limits.maximumTokens()
                || factory.streamReadConstraints().getMaxDocumentLength()
                        != limits.maximumInputBytes()) {
            throw new IllegalStateException("Jackson factory policy mismatch");
        }
        for (JsonReadFeature feature : JsonReadFeature.values()) {
            if (factory.isEnabled(feature)) {
                throw new IllegalStateException("Permissive Jackson read feature enabled");
            }
        }
    }
}
