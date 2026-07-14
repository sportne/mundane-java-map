package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.mundanej.map.api.Envelope;
import org.junit.jupiter.api.Test;

class SymbolRenderResultTest {
    @Test
    void presenceUnionPrefersPresentThenUnknownThenEmpty() {
        SymbolRenderResult empty = SymbolRenderResult.none(AwtLogicalPaintPresence.EMPTY);
        SymbolRenderResult unknown = SymbolRenderResult.none();
        SymbolRenderResult present =
                SymbolRenderResult.markerBounds(
                        new Envelope(0.0, 0.0, 1.0, 1.0), AwtLogicalPaintPresence.PRESENT);

        assertEquals(AwtLogicalPaintPresence.UNKNOWN, empty.union(unknown).paintPresence());
        assertEquals(AwtLogicalPaintPresence.PRESENT, unknown.union(present).paintPresence());
        assertEquals(AwtLogicalPaintPresence.EMPTY, empty.union(empty).paintPresence());
        assertEquals(
                new Envelope(0.0, 0.0, 1.0, 1.0),
                empty.union(present).nominalMarkerBounds().orElseThrow());
    }
}
