package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class LineFillSymbolTest {
    private static final SymbolStroke STROKE =
            new SymbolStroke(Rgba.rgb(10, 20, 30), new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL));
    private static final VectorMarkerSymbol MARKER =
            VectorMarkerSymbol.filledScreen(
                    VectorPath.builder()
                            .moveTo(-1.0, -1.0)
                            .lineTo(1.0, 0.0)
                            .lineTo(-1.0, 1.0)
                            .close()
                            .build(),
                    new Envelope(-1.0, -1.0, 1.0, 1.0),
                    Rgba.rgb(40, 50, 60),
                    8.0,
                    1.0);

    @Test
    void solidLineOwnsValidatedOptionalMarkersAndValueSemantics() {
        CompositeSymbol markerComposite = CompositeSymbol.of(List.of(MARKER), 0.5);
        SolidLineSymbol line =
                SolidLineSymbol.of(STROKE, Optional.of(MARKER), Optional.of(markerComposite), 0.75);

        assertEquals(STROKE, line.stroke());
        assertEquals(Optional.of(MARKER), line.startMarker());
        assertEquals(Optional.of(markerComposite), line.endMarker());
        assertEquals(SymbolRole.LINE, line.role());
        assertEquals(SolidLineSymbol.RENDERER_KEY, line.rendererKey());
        assertEquals(
                line,
                SolidLineSymbol.of(
                        STROKE, Optional.of(MARKER), Optional.of(markerComposite), 0.75));
        assertNotEquals(line, SolidLineSymbol.of(STROKE, 0.75));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SolidLineSymbol.of(
                                STROKE,
                                Optional.of(SolidLineSymbol.of(STROKE, 1.0)),
                                Optional.empty(),
                                1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SolidLineSymbol.of(
                                STROKE,
                                Optional.of(FeatureStyle.point(Rgba.rgb(0, 0, 0), 2.0)),
                                Optional.empty(),
                                1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SolidLineSymbol.of(STROKE, Optional.empty(), Optional.empty(), 1.1));
        assertThrows(
                NullPointerException.class,
                () -> SolidLineSymbol.of(STROKE, null, Optional.empty(), 1.0));
    }

    @Test
    void solidAndHatchFillsValidateOutlinesDefaultsAndLimits() {
        SolidLineSymbol outline = SolidLineSymbol.of(STROKE, 0.8);
        SolidFillSymbol solid = SolidFillSymbol.of(Rgba.rgb(70, 80, 90), Optional.of(outline), 0.6);
        HatchFillSymbol hatch =
                HatchFillSymbol.of(
                        HatchPattern.CROSS_DIAGONAL,
                        STROKE,
                        new SymbolLength(6.0, SymbolUnit.MAP_UNIT),
                        SymbolRotationMode.MAP_RELATIVE,
                        Optional.of(outline),
                        0.5,
                        17);

        assertEquals(SymbolRole.FILL, solid.role());
        assertEquals(Optional.of(outline), solid.outline());
        assertEquals(HatchPattern.CROSS_DIAGONAL, hatch.pattern());
        assertEquals(SymbolUnit.MAP_UNIT, hatch.spacing().unit());
        assertEquals(SymbolRotationMode.MAP_RELATIVE, hatch.rotationMode());
        assertEquals(Optional.of(outline), hatch.outline());
        assertEquals(17, hatch.maxSegments());
        assertEquals(
                HatchFillSymbol.DEFAULT_MAX_SEGMENTS,
                HatchFillSymbol.of(
                                HatchPattern.FORWARD_DIAGONAL,
                                STROKE,
                                new SymbolLength(4.0, SymbolUnit.SCREEN_PIXEL),
                                SymbolRotationMode.SCREEN_RELATIVE,
                                1.0)
                        .maxSegments());
        assertTrue(hatch.toString().contains("CROSS_DIAGONAL"));

        assertThrows(
                IllegalArgumentException.class,
                () -> SolidFillSymbol.of(Rgba.rgb(0, 0, 0), Optional.of(MARKER), 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        HatchFillSymbol.of(
                                HatchPattern.FORWARD_DIAGONAL,
                                STROKE,
                                new SymbolLength(4.0, SymbolUnit.SCREEN_PIXEL),
                                SymbolRotationMode.SCREEN_RELATIVE,
                                Optional.empty(),
                                1.0,
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SolidFillSymbol.of(Rgba.rgb(0, 0, 0), Double.NaN));
    }
}
