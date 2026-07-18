package io.github.mundanej.map.io.geojson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.core.json.JsonReadFeature;

class GeoJsonFactoriesTest {
    @Test
    void factoryIsDirectStrictAndBounded() {
        GeoJsonLimits limits = GeoJsonLimits.defaults();
        var factory = GeoJsonFactories.reader(limits);
        assertTrue(factory.isEnabled(TokenStreamFactory.Feature.CHARSET_DETECTION));
        assertTrue(factory.isEnabled(TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES));
        assertFalse(factory.isEnabled(TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES));
        assertFalse(factory.isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION));
        assertFalse(factory.isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER));
        assertFalse(factory.isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER));
        assertTrue(factory.isEnabled(StreamReadFeature.STRICT_DUPLICATE_DETECTION));
        for (JsonReadFeature feature : JsonReadFeature.values()) {
            assertFalse(factory.isEnabled(feature), feature.name());
        }
        assertEquals(
                limits.maximumInputBytes(), factory.streamReadConstraints().getMaxDocumentLength());
        assertEquals(limits.maximumTokens(), factory.streamReadConstraints().getMaxTokenCount());
        assertEquals(
                limits.maximumNestingDepth(), factory.streamReadConstraints().getMaxNestingDepth());
        assertEquals(
                limits.maximumNumberCharacters(),
                factory.streamReadConstraints().getMaxNumberLength());
        assertEquals(
                limits.maximumScalarCharacters(),
                factory.streamReadConstraints().getMaxStringLength());
        assertEquals(
                limits.maximumMemberNameCharacters(),
                factory.streamReadConstraints().getMaxNameLength());
    }
}
