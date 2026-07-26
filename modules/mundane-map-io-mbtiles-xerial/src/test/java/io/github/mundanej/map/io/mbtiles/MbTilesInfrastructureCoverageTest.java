package io.github.mundanej.map.io.mbtiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.SourceException;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class MbTilesInfrastructureCoverageTest {
    @Test
    void failuresRetainStableContextCauseCancellationAndRecordLocation() {
        SourceException basic =
                MbTilesFailures.failure("source", "CODE", "message", Map.of("field", "value"));
        assertEquals("CODE", basic.terminal().code());
        assertEquals("value", basic.terminal().context().get("field"));
        RuntimeException cause = new RuntimeException("cause");
        SourceException caused =
                MbTilesFailures.failure(
                        "source", "CODE", "message", Map.of("field", "value"), cause);
        assertSame(cause, caused.getCause());
        SourceException located = MbTilesFailures.atRecord(caused, 7);
        assertEquals(
                OptionalLong.of(7), located.terminal().location().orElseThrow().recordNumber());
        assertSame(cause, located.getCause());
        assertEquals(OptionalLong.of(8), MbTilesFailures.recordLocation(8).recordNumber());
        MbTilesFailures.checkpoint("source", () -> false, "read");
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () -> MbTilesFailures.checkpoint("source", () -> true, "read"));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertEquals("read", cancelled.terminal().context().get("operation"));
    }

    @Test
    void tileCachePolicyDistinguishesDisabledAndExactEnabledLimits() {
        MbTilesTileCachePolicy disabled = MbTilesTileCachePolicy.disabled();
        assertFalse(disabled.enabled());
        assertEquals(OptionalInt.empty(), disabled.maximumEntries());
        assertEquals(OptionalLong.empty(), disabled.maximumPixelBytes());
        assertEquals("MbTilesTileCachePolicy[disabled]", disabled.toString());

        MbTilesTileCachePolicy enabled = MbTilesTileCachePolicy.bounded(2, 524_288);
        assertTrue(enabled.enabled());
        assertEquals(OptionalInt.of(2), enabled.maximumEntries());
        assertEquals(OptionalLong.of(524_288), enabled.maximumPixelBytes());
        assertEquals(enabled, MbTilesTileCachePolicy.bounded(2, 524_288));
        assertEquals(enabled.hashCode(), MbTilesTileCachePolicy.bounded(2, 524_288).hashCode());
        assertTrue(enabled.toString().contains("maximumEntries=2"));
        assertThrows(
                IllegalArgumentException.class, () -> MbTilesTileCachePolicy.bounded(0, 524_288));
        assertThrows(
                IllegalArgumentException.class, () -> MbTilesTileCachePolicy.bounded(1, 262_143));
    }
}
