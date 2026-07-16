package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FeatureIndexLimitsTest {
    @Test
    void defaultsAndWithersAreImmutableValues() {
        FeatureIndexLimits defaults = FeatureIndexLimits.defaults();
        assertEquals(1_000_000, defaults.maximumRecords());
        assertEquals(16_777_216, defaults.maximumRetainedBytes());
        assertEquals(33_554_432, defaults.maximumBuildBytes());
        assertEquals(1_048_576, defaults.maximumQueryBytes());
        assertEquals(defaults, FeatureIndexLimits.LEVEL_1);
        assertEquals(defaults.hashCode(), FeatureIndexLimits.LEVEL_1.hashCode());
        assertEquals(7, defaults.withMaximumRecords(7).maximumRecords());
        assertEquals(8, defaults.withMaximumRetainedBytes(8).maximumRetainedBytes());
        assertEquals(9, defaults.withMaximumBuildBytes(9).maximumBuildBytes());
        assertEquals(10, defaults.withMaximumQueryBytes(10).maximumQueryBytes());
        assertNotEquals(defaults, defaults.withMaximumRecords(7));
        assertEquals(
                "FeatureIndexLimits[maximumRecords=1000000, maximumRetainedBytes=16777216, "
                        + "maximumBuildBytes=33554432, maximumQueryBytes=1048576]",
                defaults.toString());
    }

    @Test
    void everyCeilingMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new FeatureIndexLimits(0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new FeatureIndexLimits(1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new FeatureIndexLimits(1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new FeatureIndexLimits(1, 1, 1, 0));
        FeatureIndexLimits limits = new FeatureIndexLimits(1, 1, 1, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> assertEquals(limits, limits.withMaximumRecords(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertEquals(limits, limits.withMaximumRetainedBytes(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertEquals(limits, limits.withMaximumBuildBytes(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertEquals(limits, limits.withMaximumQueryBytes(0)));
        assertEquals(new FeatureIndexLimits(1, 1, 1, 1), new FeatureIndexLimits(1, 1, 1, 1));
    }
}
