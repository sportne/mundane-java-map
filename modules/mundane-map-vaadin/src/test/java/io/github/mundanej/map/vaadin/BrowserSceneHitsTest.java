package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.MarkerTransform;
import io.github.mundanej.map.core.SymbolTransforms;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class BrowserSceneHitsTest {
    private static final MapViewport VIEWPORT = new MapViewport(100, 100, 0, 0, 1);
    private static final SymbolStroke STROKE =
            new SymbolStroke(Rgba.rgb(10, 20, 30), new SymbolLength(4, SymbolUnit.SCREEN_PIXEL));

    @Test
    void usesReversePaintOrderAndExactVectorAndRasterMarkerFootprints() {
        Feature bottom = point("bottom", 0, 0, marker());
        Feature top = point("top", 0, 0, marker());
        Feature transparentRaster =
                point(
                        "raster",
                        20,
                        0,
                        RasterIconSymbol.nativeScreenSize(
                                2,
                                1,
                                new int[] {0x01020300, 0x010203ff},
                                RasterInterpolation.NEAREST,
                                1));
        InMemoryLayer layer =
                new InMemoryLayer("layer", "Layer", List.of(bottom, top, transparentRaster));

        assertEquals(
                List.of(new MapHit("layer", "top"), new MapHit("layer", "bottom")),
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 50, 50, 0).hits());
        assertFalse(
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 69.25, 50, 0)
                        .topmost()
                        .isPresent());
        assertEquals(
                "raster",
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 70.25, 50, 0)
                        .topmost()
                        .orElseThrow()
                        .featureId());
        assertThrows(
                IllegalArgumentException.class,
                () -> BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 0, 0, -1));
        assertTrue(BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, -1, 0, 1).hits().isEmpty());
    }

    @Test
    void hitsLineWidthsEndpointMarkersAndMultipartOnlyOnce() {
        SolidLineSymbol endpoints =
                SolidLineSymbol.of(STROKE, Optional.of(marker()), Optional.of(marker()), 1);
        Feature line =
                new Feature(
                        "line",
                        "Line",
                        new LineStringGeometry(CoordinateSequence.of(-20, 0, 20, 0)),
                        Map.of(),
                        endpoints);
        Feature multipart =
                new Feature(
                        "multi",
                        "Multi",
                        MultiLineStringGeometry.ofParts(
                                List.of(
                                        CoordinateSequence.of(-20, 10, 20, 10),
                                        CoordinateSequence.of(-20, 12, 20, 12))),
                        Map.of(),
                        CompositeSymbol.of(List.of(SolidLineSymbol.of(STROKE, 1)), 1));
        InMemoryLayer layer = new InMemoryLayer("lines", "Lines", List.of(line, multipart));

        assertEquals(
                "line",
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 50, 52, 0)
                        .topmost()
                        .orElseThrow()
                        .featureId());
        assertEquals(
                "line",
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 28, 50, 0)
                        .topmost()
                        .orElseThrow()
                        .featureId());
        assertEquals(
                List.of(new MapHit("lines", "multi")),
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 50, 39, 2).hits());
    }

    @Test
    void hitsSolidHatchPolygonHolesAndMultiparts() {
        PolygonGeometry polygon =
                new PolygonGeometry(square(-20, -20, 40), List.of(square(-4, -4, 8)));
        Feature solid =
                new Feature(
                        "solid",
                        "Solid",
                        polygon,
                        Map.of(),
                        SolidFillSymbol.of(
                                Rgba.rgb(100, 110, 120),
                                Optional.of(SolidLineSymbol.of(STROKE, 1)),
                                1));
        HatchFillSymbol hatch =
                HatchFillSymbol.of(
                        HatchPattern.CROSS_DIAGONAL,
                        STROKE,
                        new SymbolLength(8, SymbolUnit.SCREEN_PIXEL),
                        SymbolRotationMode.MAP_RELATIVE,
                        1);
        Feature multi =
                new Feature(
                        "multi",
                        "Multi",
                        MultiPolygonGeometry.ofPolygons(
                                List.of(new PolygonGeometry(square(10, 10, 12), List.of()))),
                        Map.of(),
                        CompositeSymbol.of(List.of(hatch), 1));
        InMemoryLayer layer = new InMemoryLayer("areas", "Areas", List.of(solid, multi));

        assertEquals(
                "solid",
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 35, 65, 0)
                        .topmost()
                        .orElseThrow()
                        .featureId());
        assertFalse(
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 50, 50, 0)
                        .topmost()
                        .isPresent());
        assertEquals(
                "multi",
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 60, 40, 3)
                        .topmost()
                        .orElseThrow()
                        .featureId());
    }

    @Test
    void hitsMultiPointAndInvisibleSymbolsRemainEmpty() {
        Feature points =
                new Feature(
                        "points",
                        "Points",
                        new MultiPointGeometry(CoordinateSequence.of(-10, 0, 10, 0)),
                        Map.of(),
                        CompositeSymbol.of(List.of(marker()), 1));
        Feature invisible =
                point(
                        "invisible",
                        0,
                        0,
                        VectorMarkerSymbol.filledScreen(
                                marker().path(), marker().viewBox(), Rgba.TRANSPARENT, 10, 0));
        InMemoryLayer layer = new InMemoryLayer("points", "Points", List.of(points, invisible));

        assertEquals(
                "points",
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 40, 50, 0)
                        .topmost()
                        .orElseThrow()
                        .featureId());
        assertFalse(
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 50, 50, 0)
                        .topmost()
                        .isPresent());
    }

    @Test
    void adaptivelyFlattensLargeCurvesAtScreenPixelAccuracy() {
        VectorPath path = VectorPath.builder().moveTo(-1, 0).quadraticTo(0, -1, 1, 0).build();
        MarkerPlacement placement =
                new MarkerPlacement(
                        SymbolSize.square(16_000, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        VectorMarkerSymbol marker =
                VectorMarkerSymbol.of(
                        path,
                        new Envelope(-1, -1, 1, 1),
                        Rgba.TRANSPARENT,
                        Optional.of(
                                new SymbolStroke(
                                        Rgba.rgb(10, 20, 30),
                                        new SymbolLength(0.5, SymbolUnit.SCREEN_PIXEL))),
                        placement,
                        1);
        double t = 0.5 + 1.0 / 64.0;
        Coordinate curvePoint = new Coordinate(2 * t - 1, -2 * t * (1 - t));
        MarkerTransform atOrigin =
                SymbolTransforms.marker(
                        marker.viewBox(),
                        placement,
                        new Coordinate(0, 0),
                        MapScreenBasis.of(new Coordinate(1, 0), new Coordinate(0, -1)));
        double baseX =
                atOrigin.m00() * curvePoint.x() + atOrigin.m01() * curvePoint.y() + atOrigin.m02();
        double baseY =
                atOrigin.m10() * curvePoint.x() + atOrigin.m11() * curvePoint.y() + atOrigin.m12();
        Coordinate featureCoordinate = VIEWPORT.screenToWorld(50 - baseX, 50 - baseY);
        Feature curve = point("curve", featureCoordinate.x(), featureCoordinate.y(), marker);

        assertEquals(
                "curve",
                BrowserSceneHits.hitTest(
                                List.of(new InMemoryLayer("curves", "Curves", List.of(curve))),
                                VIEWPORT,
                                50,
                                50,
                                0)
                        .topmost()
                        .orElseThrow()
                        .featureId());
    }

    @Test
    void testsEveryExactEdgeWithoutInflatingMixedPathLineSegments() {
        MarkerPlacement placement =
                new MarkerPlacement(
                        SymbolSize.square(20, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        SymbolStroke hairline =
                new SymbolStroke(
                        Rgba.rgb(10, 20, 30), new SymbolLength(0.5, SymbolUnit.SCREEN_PIXEL));
        VectorMarkerSymbol open =
                VectorMarkerSymbol.of(
                        VectorPath.builder().moveTo(-1, 0).lineTo(0, 0).lineTo(1, 0).build(),
                        new Envelope(-1, -1, 1, 1),
                        Rgba.TRANSPARENT,
                        Optional.of(hairline),
                        placement,
                        1);
        VectorMarkerSymbol closed =
                VectorMarkerSymbol.of(
                        VectorPath.builder()
                                .moveTo(-1, -1)
                                .lineTo(1, -1)
                                .lineTo(1, 1)
                                .close()
                                .build(),
                        new Envelope(-1, -1, 1, 1),
                        Rgba.TRANSPARENT,
                        Optional.of(hairline),
                        placement,
                        1);
        VectorMarkerSymbol mixed =
                VectorMarkerSymbol.of(
                        VectorPath.builder()
                                .moveTo(-1, 0)
                                .lineTo(0, 0)
                                .quadraticTo(0.5, -1, 1, 0)
                                .build(),
                        new Envelope(-1, -1, 1, 1),
                        Rgba.TRANSPARENT,
                        Optional.of(hairline),
                        placement,
                        1);
        InMemoryLayer layer =
                new InMemoryLayer(
                        "paths",
                        "Paths",
                        List.of(
                                point("open", 0, 0, open),
                                point("closed", 30, 0, closed),
                                point("mixed", -30, 0, mixed)));

        assertEquals(
                "open",
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 55, 50, 0)
                        .topmost()
                        .orElseThrow()
                        .featureId());
        assertEquals(
                "closed",
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 80, 50, 0)
                        .topmost()
                        .orElseThrow()
                        .featureId());
        assertFalse(
                BrowserSceneHits.hitTest(List.of(layer), VIEWPORT, 15, 50.3, 0)
                        .topmost()
                        .isPresent());
    }

    @Test
    void preservesCollinearQuadraticAndCubicOvershoot() {
        SymbolStroke stroke =
                new SymbolStroke(
                        Rgba.rgb(10, 20, 30), new SymbolLength(1, SymbolUnit.SCREEN_PIXEL));
        MarkerPlacement quadraticPlacement =
                new MarkerPlacement(
                        new SymbolSize(100, 2, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        VectorMarkerSymbol quadratic =
                VectorMarkerSymbol.of(
                        VectorPath.builder().moveTo(0, 0).quadraticTo(100, 0, 1, 0).build(),
                        new Envelope(0, -1, 100, 1),
                        Rgba.TRANSPARENT,
                        Optional.of(stroke),
                        quadraticPlacement,
                        1);
        MarkerPlacement cubicPlacement =
                new MarkerPlacement(
                        new SymbolSize(200, 2, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        VectorMarkerSymbol cubic =
                VectorMarkerSymbol.of(
                        VectorPath.builder().moveTo(0, 0).cubicTo(100, 0, -100, 0, 1, 0).build(),
                        new Envelope(-100, -1, 100, 1),
                        Rgba.TRANSPARENT,
                        Optional.of(stroke),
                        cubicPlacement,
                        1);
        InMemoryLayer quadraticLayer =
                new InMemoryLayer("curves", "Curves", List.of(point("quadratic", 0, 0, quadratic)));
        InMemoryLayer cubicLayer =
                new InMemoryLayer("curves", "Curves", List.of(point("cubic", 0, 0, cubic)));

        assertEquals(
                "quadratic",
                BrowserSceneHits.hitTest(List.of(quadraticLayer), VIEWPORT, 50.25, 50, 0)
                        .topmost()
                        .orElseThrow()
                        .featureId());
        assertEquals(
                "cubic",
                BrowserSceneHits.hitTest(List.of(cubicLayer), VIEWPORT, 78.14, 50, 0.1)
                        .topmost()
                        .orElseThrow()
                        .featureId());
    }

    private static Feature point(
            String id, double x, double y, io.github.mundanej.map.api.Symbol symbol) {
        return new Feature(id, id, new PointGeometry(new Coordinate(x, y)), Map.of(), symbol);
    }

    private static VectorMarkerSymbol marker() {
        VectorPath path =
                VectorPath.builder()
                        .moveTo(-1, -1)
                        .quadraticTo(0, -2, 1, -1)
                        .cubicTo(2, 0, 1, 1, 0, 1)
                        .lineTo(-1, 1)
                        .close()
                        .build();
        return VectorMarkerSymbol.filledScreen(
                path, new Envelope(-2, -2, 2, 2), Rgba.rgb(200, 20, 30), 10, 1);
    }

    private static CoordinateSequence square(double minimumX, double minimumY, double size) {
        return CoordinateSequence.of(
                minimumX,
                minimumY,
                minimumX + size,
                minimumY,
                minimumX + size,
                minimumY + size,
                minimumX,
                minimumY + size,
                minimumX,
                minimumY);
    }
}
