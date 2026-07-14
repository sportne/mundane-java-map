package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RasterRequestAccountingTest {
    @Test
    void strictWindowAndOutputValidationUseStableFailures() {
        RasterSourceMetadata metadata =
                new RasterSourceMetadata(
                        new SourceIdentity("raster", "Raster"),
                        4,
                        3,
                        Optional.empty(),
                        Optional.empty());
        RasterRequestAccounting accounting = accounting(RasterRequestLimits.LEVEL_1);
        accounting.validateWindow(metadata, new RasterWindow(0, 0, 4, 3));
        SourceException outside =
                assertThrows(
                        SourceException.class,
                        () -> accounting.validateWindow(metadata, new RasterWindow(3, 2, 2, 2)));
        assertEquals("RASTER_WINDOW_OUT_OF_RANGE", outside.terminal().code());
        assertEquals("4", outside.terminal().context().get("rasterWidth"));
        assertEquals(12, accounting.validateOutput(4, 3));
    }

    @Test
    void everyCumulativeCeilingAcceptsEqualityAndRejectsOneOver() {
        RasterRequestLimits limits = new RasterRequestLimits(2, 4, 4, 2, 2, 1);
        RasterRequestAccounting source = accounting(limits);
        source.chargeSourcePixels(1);
        assertEquals(3, accounting(limits).validateOutput(3, 1));
        source.chargeSourcePixels(1);
        assertLimit(() -> source.chargeSourcePixels(1), "sourceWindowPixels", "3");
        RasterRequestAccounting intermediate = accounting(limits);
        intermediate.chargeIntermediateBytes(1);
        intermediate.chargeIntermediateBytes(1);
        assertLimit(() -> intermediate.chargeIntermediateBytes(1), "decodedIntermediateBytes", "3");
        assertDoesNotThrow(() -> accounting(limits).chargeIntermediateBytes(2));
        RasterRequestAccounting published = accounting(limits);
        published.chargePublishedBytes(1);
        published.chargePublishedBytes(1);
        assertLimit(() -> published.chargePublishedBytes(1), "ownedPayloadBytes", "3");
        assertDoesNotThrow(() -> accounting(limits).chargePublishedBytes(2));
        assertEquals(4, accounting(limits).validateOutput(2, 2));
        assertEquals(4, accounting(limits).validateOutput(4, 1));
        assertLimit(() -> accounting(limits).validateOutput(3, 2), "outputPixels", "6");
        assertLimit(() -> accounting(limits).validateOutput(5, 1), "outputWidth", "5");
    }

    @Test
    void cumulativeOverflowSaturatesAndFails() {
        RasterRequestLimits limits =
                new RasterRequestLimits(
                        Long.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Integer.MAX_VALUE,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        1);
        RasterRequestAccounting accounting = accounting(limits);
        accounting.chargeIntermediateBytes(Long.MAX_VALUE);
        assertLimit(
                () -> accounting.chargeIntermediateBytes(1),
                "decodedIntermediateBytes",
                Long.toString(Long.MAX_VALUE));
    }

    @Test
    void cancellationAndInvalidChargesAreDistinguished() {
        CancellationSource cancellation = new CancellationSource();
        RasterRequestAccounting accounting =
                new RasterRequestAccounting(
                        "raster", RasterRequestLimits.LEVEL_1, cancellation.token());
        cancellation.cancel();
        SourceException failure = assertThrows(SourceException.class, accounting::checkpoint);
        assertEquals("SOURCE_CANCELLED", failure.terminal().code());
        assertThrows(IllegalArgumentException.class, () -> accounting.chargeSourcePixels(-1));
        assertThrows(IllegalArgumentException.class, () -> accounting.validateOutput(0, 1));
    }

    private static RasterRequestAccounting accounting(RasterRequestLimits limits) {
        return new RasterRequestAccounting("raster", limits, CancellationToken.none());
    }

    private static void assertLimit(Runnable operation, String limit, String requested) {
        SourceException failure = assertThrows(SourceException.class, operation::run);
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
        assertEquals(requested, failure.terminal().context().get("requested"));
    }
}
