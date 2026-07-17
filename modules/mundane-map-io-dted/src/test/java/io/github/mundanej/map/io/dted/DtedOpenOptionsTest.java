package io.github.mundanej.map.io.dted;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.ElevationSourceLimits;
import org.junit.jupiter.api.Test;

class DtedOpenOptionsTest {
    @Test
    void defaultsAndWitherAreImmutableValues() {
        DtedOpenOptions defaults = DtedOpenOptions.defaults();
        assertSame(ElevationSourceLimits.DEFAULTS, defaults.elevationSourceLimits());
        assertSame(DtedLimits.defaults(), defaults.dtedLimits());
        ElevationSourceLimits limits =
                new ElevationSourceLimits(601, 3_601, 3_000_000, 30_000_000, 1);
        DtedOpenOptions changed = defaults.withElevationSourceLimits(limits);
        assertEquals(limits, changed.elevationSourceLimits());
        assertEquals(changed, defaults.withElevationSourceLimits(limits));
        assertEquals(changed.hashCode(), defaults.withElevationSourceLimits(limits).hashCode());
        assertNotEquals(defaults, changed);
        assertTrue(changed.toString().contains("elevationSourceLimits="));
        DtedLimits dtedLimits = DtedLimits.defaults().withMaximumProfiles(21);
        DtedOpenOptions changedDted = defaults.withDtedLimits(dtedLimits);
        assertEquals(dtedLimits, changedDted.dtedLimits());
        assertEquals(changedDted, defaults.withDtedLimits(dtedLimits));
        assertTrue(changedDted.toString().contains("dtedLimits="));
        NullPointerException elevationFailure =
                assertThrows(
                        NullPointerException.class,
                        () -> assertEquals(defaults, defaults.withElevationSourceLimits(null)));
        assertEquals("limits", elevationFailure.getMessage());
        NullPointerException dtedFailure =
                assertThrows(
                        NullPointerException.class,
                        () -> assertEquals(defaults, defaults.withDtedLimits(null)));
        assertEquals("limits", dtedFailure.getMessage());
    }
}
