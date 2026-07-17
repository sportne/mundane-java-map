package io.github.mundanej.map.io.dted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.LongFunction;
import org.junit.jupiter.api.Test;

class DtedLimitsTest {
    @Test
    void defaultsAndEveryWitherHaveValueSemantics() {
        DtedLimits defaults = DtedLimits.defaults();
        assertSame(defaults, DtedLimits.defaults());
        assertEquals(33_554_432L, defaults.maximumFileBytes());
        assertEquals(4_096, defaults.maximumProfiles());
        assertEquals(4_096, defaults.maximumSamplesPerProfile());
        assertEquals(16_777_216L, defaults.maximumTotalSamples());
        assertEquals(8_192, defaults.maximumProfileBytes());
        assertEquals(268_435_456L, defaults.maximumParserAllocationBytes());

        List<DtedLimits> changed =
                List.of(
                        defaults.withMaximumFileBytes(1),
                        defaults.withMaximumProfiles(1),
                        defaults.withMaximumSamplesPerProfile(1),
                        defaults.withMaximumTotalSamples(1),
                        defaults.withMaximumProfileBytes(1),
                        defaults.withMaximumParserAllocationBytes(1));
        for (DtedLimits value : changed) {
            assertNotEquals(defaults, value);
            assertTrue(value.toString().startsWith("DtedLimits["));
        }
        assertEquals(defaults.withMaximumProfiles(7), defaults.withMaximumProfiles(7));
        assertEquals(
                defaults.withMaximumProfiles(7).hashCode(),
                defaults.withMaximumProfiles(7).hashCode());
    }

    @Test
    void everyCeilingMustBePositive() {
        List<NamedWither> withers =
                List.of(
                        new NamedWither(
                                "maximumFileBytes",
                                value -> DtedLimits.defaults().withMaximumFileBytes(value)),
                        new NamedWither(
                                "maximumProfiles",
                                value ->
                                        DtedLimits.defaults()
                                                .withMaximumProfiles(Math.toIntExact(value))),
                        new NamedWither(
                                "maximumSamplesPerProfile",
                                value ->
                                        DtedLimits.defaults()
                                                .withMaximumSamplesPerProfile(
                                                        Math.toIntExact(value))),
                        new NamedWither(
                                "maximumTotalSamples",
                                value -> DtedLimits.defaults().withMaximumTotalSamples(value)),
                        new NamedWither(
                                "maximumProfileBytes",
                                value ->
                                        DtedLimits.defaults()
                                                .withMaximumProfileBytes(Math.toIntExact(value))),
                        new NamedWither(
                                "maximumParserAllocationBytes",
                                value ->
                                        DtedLimits.defaults()
                                                .withMaximumParserAllocationBytes(value)));
        for (NamedWither wither : withers) {
            IllegalArgumentException zero =
                    assertThrows(IllegalArgumentException.class, () -> wither.function.apply(0));
            assertTrue(zero.getMessage().contains(wither.parameter));
            IllegalArgumentException negative =
                    assertThrows(IllegalArgumentException.class, () -> wither.function.apply(-1));
            assertTrue(negative.getMessage().contains(wither.parameter));
        }
    }

    private record NamedWither(String parameter, LongFunction<DtedLimits> function) {}
}
