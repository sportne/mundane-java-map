package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MapTool;
import io.github.mundanej.map.api.MapToolResult;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.HatchLayouts;
import io.github.mundanej.map.core.HatchSegments;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class MapViewHitTest {
    private static final int SIZE = 100;

    @Test
    void everyBuiltInMarkerUsesItsVisibleTransformedFootprint() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    for (BuiltInMarker marker : BuiltInMarker.values()) {
                        MapView view = view(feature(marker.name(), point(), marker(marker, 20.0)));
                        assertEquals(
                                marker.name(),
                                view.hitTest(50.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                        assertTrue(view.hitTest(20.0, 20.0, 0.0).topmost().isEmpty());
                    }

                    MarkerPlacement shifted =
                            new MarkerPlacement(
                                    SymbolSize.square(18.0, SymbolUnit.SCREEN_PIXEL),
                                    SymbolAnchor.CENTER,
                                    14.0,
                                    -8.0,
                                    45.0,
                                    SymbolRotationMode.SCREEN_RELATIVE);
                    VectorMarkerSymbol transformed =
                            VectorMarkerSymbol.of(
                                    BuiltInMarkers.path(BuiltInMarker.SQUARE),
                                    BuiltInMarkers.viewBox(),
                                    Rgba.rgb(20, 40, 60),
                                    Optional.empty(),
                                    shifted,
                                    1.0);
                    MapView shiftedView = view(feature("shifted", point(), transformed));
                    assertEquals(
                            "shifted",
                            shiftedView
                                    .hitTest(64.0, 42.0, 0.0)
                                    .topmost()
                                    .orElseThrow()
                                    .featureId());
                    assertTrue(shiftedView.hitTest(50.0, 50.0, 0.0).topmost().isEmpty());
                });
    }

    @Test
    void lineWidthPolygonHolesAndVisibleHoleOutlineMatchPaintedGeometry() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    SolidLineSymbol lineSymbol =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(20, 40, 60),
                                            new SymbolLength(6.0, SymbolUnit.SCREEN_PIXEL)),
                                    1.0);
                    MapView line =
                            view(
                                    feature(
                                            "line",
                                            new LineStringGeometry(
                                                    CoordinateSequence.of(-30.0, 0.0, 30.0, 0.0)),
                                            lineSymbol));
                    assertFalse(line.hitTest(50.0, 56.0, 3.0).topmost().isEmpty());
                    assertTrue(line.hitTest(50.0, 56.1, 3.0).topmost().isEmpty());

                    PolygonGeometry polygon = polygonWithHole();
                    MapView fillOnly =
                            view(
                                    feature(
                                            "fill",
                                            polygon,
                                            SolidFillSymbol.of(Rgba.rgb(30, 90, 160), 1.0)));
                    assertEquals(
                            "fill",
                            fillOnly.hitTest(35.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                    assertTrue(fillOnly.hitTest(50.0, 50.0, 0.0).topmost().isEmpty());

                    SolidLineSymbol outline =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(20, 20, 20),
                                            new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                                    1.0);
                    MapView outlined =
                            view(
                                    feature(
                                            "outlined",
                                            polygon,
                                            SolidFillSymbol.of(
                                                    Rgba.TRANSPARENT, Optional.of(outline), 1.0)));
                    assertEquals(
                            "outlined",
                            outlined.hitTest(45.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                    assertTrue(outlined.hitTest(50.0, 50.0, 0.0).topmost().isEmpty());
                });
    }

    @Test
    void rasterAlphaCompositeOpacityAndReversePaintOrderAreRespected() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    int opaque = 0x224466ff;
                    RasterIconSymbol icon =
                            RasterIconSymbol.screenWidth(
                                    3,
                                    1,
                                    new int[] {0, opaque, 0},
                                    30.0,
                                    RasterInterpolation.NEAREST,
                                    1.0);
                    MapView raster = view(feature("raster", point(), icon));
                    assertEquals(
                            "raster",
                            raster.hitTest(50.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                    assertTrue(raster.hitTest(36.0, 50.0, 0.0).topmost().isEmpty());

                    Symbol invisibleTop =
                            CompositeSymbol.of(List.of(marker(BuiltInMarker.SQUARE, 24.0)), 0.0);
                    Feature bottom = feature("bottom", point(), marker(BuiltInMarker.CIRCLE, 24.0));
                    Feature transparent = feature("transparent", point(), invisibleTop);
                    Feature top = feature("top", point(), marker(BuiltInMarker.DIAMOND, 18.0));
                    MapView ordered =
                            view(List.of(layer("lower", bottom), layer("upper", transparent, top)));

                    assertEquals(
                            List.of(new MapHit("upper", "top"), new MapHit("lower", "bottom")),
                            ordered.hitTest(50.0, 50.0, 0.0).hits());
                });
    }

    @Test
    void clickSelectionUpdatesBeforeObserversAndIgnoresModifiedOrMultipleClicks() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(feature("selected", point(), marker(BuiltInMarker.CIRCLE, 20.0)));
                    AtomicReference<Optional<FeatureSelection>> observed = new AtomicReference<>();
                    view.addMapPointerListener(event -> observed.set(view.selection()));

                    click(view, 50, 50, 1, 0);
                    FeatureSelection expected = new FeatureSelection("layer", "selected");
                    assertEquals(Optional.of(expected), view.selection());
                    assertEquals(Optional.of(expected), observed.get());

                    click(view, 10, 10, 1, InputEvent.SHIFT_DOWN_MASK);
                    click(view, 10, 10, 2, 0);
                    assertEquals(Optional.of(expected), view.selection());

                    click(view, 10, 10, 1, 0);
                    assertTrue(view.selection().isEmpty());
                });
    }

    @Test
    void programmaticSelectionValidatesCurrentContentAndRemovalClearsIt() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature selected =
                            feature("selected", point(), marker(BuiltInMarker.CIRCLE, 20.0));
                    MapView view = view(selected);
                    FeatureSelection identity = new FeatureSelection("layer", "selected");
                    view.setSelection(identity);
                    assertEquals(Optional.of(identity), view.selection());

                    assertThrows(
                            IllegalArgumentException.class,
                            () -> view.setSelection(new FeatureSelection("layer", "missing")));
                    view.setLayers(
                            List.of(
                                    layer(
                                            "replacement",
                                            feature(
                                                    "other",
                                                    point(),
                                                    marker(BuiltInMarker.CIRCLE, 20.0)))));
                    assertTrue(view.selection().isEmpty());
                    view.clearSelection();
                });
    }

    @Test
    void hitQueryRejectsInvalidValuesAndClipsToTheComponent() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(
                                    feature(
                                            "point",
                                            point(),
                                            FeatureStyle.point(Rgba.rgb(20, 30, 40), 20.0)));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> view.hitTest(Double.NaN, 1.0, 1.0));
                    assertThrows(
                            IllegalArgumentException.class, () -> view.hitTest(1.0, 1.0, -1.0));
                    assertTrue(view.hitTest(-0.1, 50.0, 100.0).topmost().isEmpty());
                    assertTrue(view.hitTest(100.0, 50.0, 100.0).topmost().isEmpty());
                });
    }

    @Test
    void allCoincidentLinesRemainUnpaintedAndEndpointMarkersRemainHittable() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CoordinateSequence coincident =
                            CoordinateSequence.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                    SolidLineSymbol solid =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(20, 40, 60),
                                            new SymbolLength(8.0, SymbolUnit.SCREEN_PIXEL)),
                                    1.0);
                    assertTrue(
                            view(feature("solid", new LineStringGeometry(coincident), solid))
                                    .hitTest(50.0, 50.0, 20.0)
                                    .topmost()
                                    .isEmpty());
                    assertTrue(
                            view(feature(
                                            "legacy",
                                            new LineStringGeometry(coincident),
                                            FeatureStyle.line(Rgba.rgb(20, 40, 60), 8.0)))
                                    .hitTest(50.0, 50.0, 20.0)
                                    .topmost()
                                    .isEmpty());
                    BufferedImage legacyPaint =
                            paint(
                                    view(
                                            feature(
                                                    "legacy-paint",
                                                    new LineStringGeometry(coincident),
                                                    FeatureStyle.line(Rgba.rgb(20, 40, 60), 8.0))));
                    assertEquals(0xffffffff, legacyPaint.getRGB(50, 50));

                    SolidLineSymbol endpointOnly =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.TRANSPARENT,
                                            new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                                    Optional.empty(),
                                    Optional.of(marker(BuiltInMarker.SQUARE, 12.0)),
                                    1.0);
                    MapView endpoint =
                            view(
                                    feature(
                                            "endpoint",
                                            new LineStringGeometry(
                                                    CoordinateSequence.of(-20.0, 0.0, 20.0, 0.0)),
                                            endpointOnly));
                    assertEquals(
                            "endpoint",
                            endpoint.hitTest(70.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                    assertTrue(endpoint.hitTest(50.0, 50.0, 0.0).topmost().isEmpty());
                });
    }

    @Test
    void rasterZeroToleranceHonorsNearestHalfOpenAndBilinearOpenSupports() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    int[] pixels = {0, 0x224466ff, 0};
                    MapView nearest =
                            view(
                                    feature(
                                            "nearest",
                                            point(),
                                            RasterIconSymbol.screenWidth(
                                                    3,
                                                    1,
                                                    pixels,
                                                    30.0,
                                                    RasterInterpolation.NEAREST,
                                                    1.0)));
                    assertFalse(nearest.hitTest(45.0, 50.0, 0.0).topmost().isEmpty());
                    assertTrue(nearest.hitTest(55.0, 50.0, 0.0).topmost().isEmpty());

                    MapView bilinear =
                            view(
                                    feature(
                                            "bilinear",
                                            point(),
                                            RasterIconSymbol.screenWidth(
                                                    3,
                                                    1,
                                                    pixels,
                                                    30.0,
                                                    RasterInterpolation.BILINEAR,
                                                    1.0)));
                    assertTrue(bilinear.hitTest(40.0, 50.0, 0.0).topmost().isEmpty());
                    assertFalse(bilinear.hitTest(40.0001, 50.0, 0.0).topmost().isEmpty());
                    assertTrue(bilinear.hitTest(60.0, 50.0, 0.0).topmost().isEmpty());
                    assertFalse(bilinear.hitTest(39.0, 50.0, 1.0).topmost().isEmpty());
                });
    }

    @Test
    void exactVectorAndCustomFootprintBoundariesAndTangenciesAreInclusive() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView square =
                            view(feature("square", point(), marker(BuiltInMarker.SQUARE, 20.0)));
                    assertFalse(square.hitTest(40.0, 50.0, 0.0).topmost().isEmpty());
                    assertFalse(square.hitTest(35.0, 50.0, 5.0).topmost().isEmpty());

                    SymbolRendererKey key = new SymbolRendererKey("test.boundary-hit");
                    SymbolRendererRegistry registry =
                            SymbolRendererRegistry.builderWithBuiltIns()
                                    .register(SymbolRole.MARKER, key, markerRenderer(true, null))
                                    .build();
                    MapView custom =
                            configuredView(
                                    registry,
                                    List.of(
                                            layer(
                                                    "layer",
                                                    feature(
                                                            "custom",
                                                            point(),
                                                            new TestMarker(key)))));
                    assertFalse(custom.hitTest(45.0, 50.0, 0.0).topmost().isEmpty());
                    assertFalse(custom.hitTest(40.0, 50.0, 5.0).topmost().isEmpty());
                });
    }

    @Test
    void quadraticAndCubicMarkerBoundariesIncludeExactPointsAndTangencies() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    VectorPath quadratic =
                            VectorPath.builder()
                                    .moveTo(0.0, 0.0)
                                    .quadraticTo(5.0, 10.0, 10.0, 0.0)
                                    .lineTo(10.0, 10.0)
                                    .lineTo(0.0, 10.0)
                                    .close()
                                    .build();
                    MapView quadraticView = view(feature("quadratic", point(), curved(quadratic)));
                    double t = 0.37;
                    double quadraticX = 40.0 + 20.0 * t;
                    double quadraticY = 40.0 + 40.0 * t * (1.0 - t);
                    double quadraticDx = 20.0;
                    double quadraticDy = 2.0 * (20.0 - 40.0 * t);
                    double quadraticLength = Math.hypot(quadraticDx, quadraticDy);
                    assertFalse(
                            quadraticView.hitTest(quadraticX, quadraticY, 0.0).topmost().isEmpty());
                    assertFalse(
                            quadraticView
                                    .hitTest(
                                            quadraticX + 5.0 * quadraticDy / quadraticLength,
                                            quadraticY - 5.0 * quadraticDx / quadraticLength,
                                            5.0)
                                    .topmost()
                                    .isEmpty());

                    VectorPath cubic =
                            VectorPath.builder()
                                    .moveTo(0.0, 0.0)
                                    .cubicTo(0.0, 10.0, 10.0, 10.0, 10.0, 0.0)
                                    .lineTo(10.0, 10.0)
                                    .lineTo(0.0, 10.0)
                                    .close()
                                    .build();
                    MapView cubicView = view(feature("cubic", point(), curved(cubic)));
                    double cubicX = 40.0 + 20.0 * (3.0 * t * t - 2.0 * t * t * t);
                    double cubicY = 40.0 + 60.0 * t * (1.0 - t);
                    double cubicDx = 120.0 * t * (1.0 - t);
                    double cubicDy = 60.0 * (1.0 - 2.0 * t);
                    double cubicLength = Math.hypot(cubicDx, cubicDy);
                    assertFalse(cubicView.hitTest(cubicX, cubicY, 0.0).topmost().isEmpty());
                    assertFalse(
                            cubicView
                                    .hitTest(
                                            cubicX + 5.0 * cubicDy / cubicLength,
                                            cubicY - 5.0 * cubicDx / cubicLength,
                                            5.0)
                                    .topmost()
                                    .isEmpty());
                });
    }

    @Test
    void everyPlacementAnchorUnitAndRotationModeKeepsHitAlignedWithPaint() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    for (SymbolAnchor anchor : SymbolAnchor.values()) {
                        for (SymbolUnit unit : SymbolUnit.values()) {
                            for (SymbolRotationMode rotationMode : SymbolRotationMode.values()) {
                                MarkerPlacement placement =
                                        new MarkerPlacement(
                                                SymbolSize.square(14.0, unit),
                                                anchor,
                                                3.0,
                                                -2.0,
                                                27.0,
                                                rotationMode);
                                VectorMarkerSymbol marker =
                                        VectorMarkerSymbol.of(
                                                BuiltInMarkers.path(BuiltInMarker.SQUARE),
                                                BuiltInMarkers.viewBox(),
                                                Rgba.rgb(30, 90, 160),
                                                Optional.empty(),
                                                placement,
                                                1.0);
                                MapView view = view(feature("placed", point(), marker));
                                int[] painted = firstNonWhite(paint(view));
                                assertFalse(
                                        view.hitTest(painted[0] + 0.5, painted[1] + 0.5, 1.0)
                                                .topmost()
                                                .isEmpty(),
                                        () -> anchor + "/" + unit + "/" + rotationMode);
                            }
                        }
                    }
                });
    }

    @Test
    void casedLineAndFillCompositesUseTheUnionOfVisibleChildren() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    SolidLineSymbol casing =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(20, 20, 20),
                                            new SymbolLength(10.0, SymbolUnit.SCREEN_PIXEL)),
                                    1.0);
                    SolidLineSymbol center =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(240, 240, 240),
                                            new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                                    1.0);
                    MapView cased =
                            view(
                                    feature(
                                            "cased",
                                            new LineStringGeometry(
                                                    CoordinateSequence.of(-30.0, 0.0, 30.0, 0.0)),
                                            CompositeSymbol.of(List.of(casing, center), 1.0)));
                    assertFalse(cased.hitTest(50.0, 54.5, 0.0).topmost().isEmpty());

                    MapView fill =
                            view(
                                    feature(
                                            "fill-composite",
                                            polygonWithHole(),
                                            CompositeSymbol.of(
                                                    List.of(
                                                            SolidFillSymbol.of(
                                                                    Rgba.rgb(30, 90, 160), 1.0),
                                                            SolidFillSymbol.of(
                                                                    Rgba.TRANSPARENT, 1.0)),
                                                    1.0)));
                    assertFalse(fill.hitTest(35.0, 50.0, 0.0).topmost().isEmpty());
                    assertTrue(fill.hitTest(50.0, 50.0, 0.0).topmost().isEmpty());
                });
    }

    @Test
    void componentClipRetainsPartiallyVisibleMarkerLineAndFillFootprints() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature marker =
                            feature(
                                    "marker",
                                    new PointGeometry(new Coordinate(-55.0, 0.0)),
                                    marker(BuiltInMarker.SQUARE, 20.0));
                    Feature line =
                            feature(
                                    "line",
                                    new LineStringGeometry(
                                            CoordinateSequence.of(-50.0, -20.0, -50.0, 20.0)),
                                    SolidLineSymbol.of(
                                            new SymbolStroke(
                                                    Rgba.rgb(20, 40, 60),
                                                    new SymbolLength(4.0, SymbolUnit.SCREEN_PIXEL)),
                                            1.0));
                    Feature fill =
                            feature(
                                    "fill",
                                    new PolygonGeometry(
                                            CoordinateSequence.of(
                                                    -60.0, -20.0, -40.0, -20.0, -40.0, 20.0, -60.0,
                                                    20.0, -60.0, -20.0)),
                                    SolidFillSymbol.of(Rgba.rgb(30, 90, 160), 1.0));
                    MapView view =
                            view(
                                    List.of(
                                            layer("marker-layer", marker),
                                            layer("line-layer", line),
                                            layer("fill-layer", fill)));

                    assertEquals(
                            List.of(
                                    new MapHit("fill-layer", "fill"),
                                    new MapHit("line-layer", "line"),
                                    new MapHit("marker-layer", "marker")),
                            view.hitTest(0.0, 50.0, 0.0).hits());
                });
    }

    @Test
    void hatchVariantsExposeOnlyGeneratedPaintAndOutline() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    for (HatchPattern pattern : HatchPattern.values()) {
                        HatchFillSymbol hatch =
                                HatchFillSymbol.of(
                                        pattern,
                                        new SymbolStroke(
                                                Rgba.rgb(80, 30, 120),
                                                new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                                        new SymbolLength(8.0, SymbolUnit.SCREEN_PIXEL),
                                        SymbolRotationMode.SCREEN_RELATIVE,
                                        Optional.empty(),
                                        1.0,
                                        128);
                        MapView view =
                                view(
                                        feature(
                                                pattern.name(),
                                                new PolygonGeometry(
                                                        CoordinateSequence.of(
                                                                -30.0, -30.0, 30.0, -30.0, 30.0,
                                                                30.0, -30.0, 30.0, -30.0, -30.0)),
                                                hatch));
                        boolean found = false;
                        for (int y = 25; y <= 75 && !found; y++) {
                            for (int x = 25; x <= 75; x++) {
                                if (!view.hitTest(x, y, 0.0).topmost().isEmpty()) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                        assertTrue(found, pattern::name);

                        HatchSegments segments =
                                HatchLayouts.cover(
                                        pattern,
                                        new Envelope(20.0, 20.0, 80.0, 80.0),
                                        new Coordinate(0.0, 0.0),
                                        0.0,
                                        8.0,
                                        128,
                                        pattern.name());
                        double dx = segments.x2(0) - segments.x1(0);
                        double dy = segments.y2(0) - segments.y1(0);
                        double length = Math.hypot(dx, dy);
                        double boundaryX = (segments.x1(0) + segments.x2(0)) / 2.0 - dy / length;
                        double boundaryY = (segments.y1(0) + segments.y2(0)) / 2.0 + dx / length;
                        assertFalse(
                                view.hitTest(boundaryX, boundaryY, 0.0).topmost().isEmpty(),
                                pattern::name);
                    }
                });
    }

    @Test
    void customRenderersDefaultToNoHitAndMayExplicitlyOptIn() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    SymbolRendererKey defaultKey = new SymbolRendererKey("test.default-hit");
                    SymbolRendererKey optInKey = new SymbolRendererKey("test.opt-in-hit");
                    AwtSymbolRenderer defaultRenderer = markerRenderer(false, null);
                    AwtSymbolRenderer optInRenderer = markerRenderer(true, null);
                    SymbolRendererRegistry registry =
                            SymbolRendererRegistry.builderWithBuiltIns()
                                    .register(SymbolRole.MARKER, defaultKey, defaultRenderer)
                                    .register(SymbolRole.MARKER, optInKey, optInRenderer)
                                    .build();
                    Feature bottom = feature("default", point(), new TestMarker(defaultKey));
                    Feature top = feature("opt-in", point(), new TestMarker(optInKey));
                    MapView view = configuredView(registry, List.of(layer("layer", bottom, top)));

                    assertEquals(
                            List.of(new MapHit("layer", "opt-in")),
                            view.hitTest(50.0, 50.0, 0.0).hits());
                });
    }

    @Test
    void consumedClicksDoNotChangeSelection() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(feature("selected", point(), marker(BuiltInMarker.CIRCLE, 20.0)));
                    FeatureSelection expected = new FeatureSelection("layer", "selected");
                    view.setSelection(expected);
                    MapTool consuming = (event, context) -> MapToolResult.CONSUME;
                    view.setActiveTool(consuming);

                    click(view, 10, 10, 1, 0);

                    assertEquals(Optional.of(expected), view.selection());
                });
    }

    @Test
    void activeToolPassedClicksStillApplyDefaultSelection() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            view(feature("selected", point(), marker(BuiltInMarker.CIRCLE, 20.0)));
                    view.setActiveTool((event, context) -> MapToolResult.PASS);

                    click(view, 50, 50, 1, 0);

                    assertEquals(
                            Optional.of(new FeatureSelection("layer", "selected")),
                            view.selection());
                });
    }

    @Test
    void paintUsesOneViewportSnapshotEvenWhenACustomRendererMutatesTheView() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    SymbolRendererKey key = new SymbolRendererKey("test.viewport-snapshot");
                    AtomicReference<MapView> owner = new AtomicReference<>();
                    List<MapViewport> observed = new ArrayList<>();
                    AwtSymbolRenderer renderer =
                            markerRenderer(
                                    false,
                                    (value, context) -> {
                                        observed.add(context.viewport());
                                        if (observed.size() == 1) {
                                            owner.get()
                                                    .setViewport(
                                                            new MapViewport(
                                                                    SIZE, SIZE, 20.0, 20.0, 2.0));
                                        }
                                    });
                    SymbolRendererRegistry registry =
                            SymbolRendererRegistry.builderWithBuiltIns()
                                    .register(SymbolRole.MARKER, key, renderer)
                                    .build();
                    MapView view =
                            configuredView(
                                    registry,
                                    List.of(
                                            layer(
                                                    "layer",
                                                    feature("one", point(), new TestMarker(key)),
                                                    feature("two", point(), new TestMarker(key)))));
                    owner.set(view);

                    paint(view);

                    assertEquals(2, observed.size());
                    assertEquals(observed.get(0), observed.get(1));
                    assertEquals(0.0, observed.get(0).centerX());
                });
    }

    @Test
    void contentCaptureIsTransactionalAndOutsideHitsStillReconcileSelection() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature original =
                            feature("selected", point(), marker(BuiltInMarker.CIRCLE, 20.0));
                    MutableLayer changing = new MutableLayer("layer", List.of(original));
                    MapView view =
                            configuredView(SymbolRendererRegistry.builtIn(), List.of(changing));
                    FeatureSelection identity = new FeatureSelection("layer", "selected");
                    view.setSelection(identity);
                    changing.features = List.of();

                    assertTrue(view.hitTest(-1.0, 50.0, 0.0).topmost().isEmpty());
                    assertTrue(view.selection().isEmpty());

                    view.setLayers(List.of(layer("valid", original)));
                    List<Layer> before = view.layers();
                    Layer duplicate = new MutableLayer("valid", List.of(original, original));
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> view.setLayers(List.of(duplicate)));
                    assertEquals(before, view.layers());

                    MutableLayer temporarilyBroken = new MutableLayer("valid", List.of(original));
                    view.setLayers(List.of(temporarilyBroken));
                    view.setSelection(new FeatureSelection("valid", "selected"));
                    temporarilyBroken.throwOnFeatures = true;
                    view.clearSelection();
                    temporarilyBroken.throwOnFeatures = false;
                    assertTrue(view.selection().isEmpty());
                });
    }

    @Test
    void everyContentOperationReadsEachLayerIdentityAndFeatureSnapshotOnce() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature feature =
                            feature("feature", point(), marker(BuiltInMarker.CIRCLE, 20.0));
                    MutableLayer layer = new MutableLayer("layer", List.of(feature));
                    MapView view = configuredView(SymbolRendererRegistry.builtIn(), List.of(layer));
                    FeatureSelection selection = new FeatureSelection("layer", "feature");

                    layer.resetCounts();
                    view.selection();
                    layer.assertReadOnce();

                    layer.resetCounts();
                    view.setSelection(selection);
                    layer.assertReadOnce();

                    layer.resetCounts();
                    view.hitTest(50.0, 50.0, 0.0);
                    layer.assertReadOnce();

                    layer.resetCounts();
                    paint(view);
                    layer.assertReadOnce();

                    layer.resetCounts();
                    click(view, 50, 50, 1, 0);
                    layer.assertReadOnce();

                    MutableLayer candidate = new MutableLayer("candidate", List.of(feature));
                    view.setLayers(List.of(candidate));
                    candidate.assertReadOnce();
                });
    }

    private static AwtSymbolRenderer markerRenderer(
            boolean hitEnabled, RenderObservation observation) {
        if (hitEnabled) {
            return new AwtSymbolRenderer() {
                @Override
                public boolean supports(Symbol value) {
                    return value instanceof TestMarker;
                }

                @Override
                public SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context) {
                    observe(observation, value, context);
                    return markerResult(context);
                }

                @Override
                public boolean hitTest(Symbol value, AwtSymbolHitContext context) {
                    Coordinate anchor = context.markerAnchorScreen().orElseThrow();
                    return context.visibleShapeHit(
                            new Rectangle2D.Double(anchor.x() - 5.0, anchor.y() - 5.0, 10.0, 10.0));
                }
            };
        }
        return new AwtSymbolRenderer() {
            @Override
            public boolean supports(Symbol value) {
                return value instanceof TestMarker;
            }

            @Override
            public SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context) {
                observe(observation, value, context);
                return markerResult(context);
            }
        };
    }

    private static void observe(
            RenderObservation observation, Symbol value, AwtSymbolRenderContext context) {
        if (observation != null) {
            observation.observe(value, context);
        }
    }

    private static SymbolRenderResult markerResult(AwtSymbolRenderContext context) {
        Coordinate anchor = context.markerAnchorScreen().orElseThrow();
        return SymbolRenderResult.markerBounds(
                new Envelope(
                        anchor.x() - 5.0, anchor.y() - 5.0, anchor.x() + 5.0, anchor.y() + 5.0));
    }

    private static MapView configuredView(
            SymbolRendererRegistry registry, List<? extends Layer> layers) {
        MapView view = TestMapViews.identity(registry);
        view.setSize(SIZE, SIZE);
        view.setViewport(new MapViewport(SIZE, SIZE, 0.0, 0.0, 1.0));
        view.setLayers(List.copyOf(layers));
        return view;
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static int[] firstNonWhite(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != 0xffffffff) {
                    return new int[] {x, y};
                }
            }
        }
        throw new AssertionError("Expected at least one painted pixel");
    }

    private static MapView view(Feature feature) {
        return view(List.of(layer("layer", feature)));
    }

    private static MapView view(List<InMemoryLayer> layers) {
        return configuredView(SymbolRendererRegistry.builtIn(), layers);
    }

    private static InMemoryLayer layer(String id, Feature... features) {
        return new InMemoryLayer(id, id, List.of(features));
    }

    private static Feature feature(
            String id, io.github.mundanej.map.api.Geometry geometry, Symbol symbol) {
        return new Feature(id, "", geometry, Map.of(), symbol);
    }

    private static PointGeometry point() {
        return new PointGeometry(new Coordinate(0.0, 0.0));
    }

    private static VectorMarkerSymbol marker(BuiltInMarker marker, double size) {
        return BuiltInMarkers.filledScreen(marker, Rgba.rgb(30, 90, 160), size, 1.0);
    }

    private static VectorMarkerSymbol curved(VectorPath path) {
        return VectorMarkerSymbol.of(
                path,
                new Envelope(0.0, 0.0, 10.0, 10.0),
                Rgba.rgb(30, 90, 160),
                Optional.empty(),
                MarkerPlacement.centeredScreen(20.0),
                1.0);
    }

    private static PolygonGeometry polygonWithHole() {
        return new PolygonGeometry(
                CoordinateSequence.of(
                        -20.0, -20.0, 20.0, -20.0, 20.0, 20.0, -20.0, 20.0, -20.0, -20.0),
                List.of(
                        CoordinateSequence.of(
                                -5.0, -5.0, 5.0, -5.0, 5.0, 5.0, -5.0, 5.0, -5.0, -5.0)));
    }

    private static void click(MapView view, int x, int y, int count, int modifiers) {
        view.dispatchEvent(
                new MouseEvent(
                        view,
                        MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(),
                        modifiers,
                        x,
                        y,
                        count,
                        false,
                        MouseEvent.BUTTON1));
    }

    @FunctionalInterface
    private interface RenderObservation {
        void observe(Symbol value, AwtSymbolRenderContext context);
    }

    private record TestMarker(SymbolRendererKey rendererKey) implements MarkerSymbol {
        @Override
        public double opacity() {
            return 1.0;
        }
    }

    private static final class MutableLayer implements Layer {
        private final String id;
        private List<Feature> features;
        private boolean throwOnFeatures;
        private int idReads;
        private int featureReads;

        private MutableLayer(String id, List<Feature> features) {
            this.id = id;
            this.features = List.copyOf(features);
        }

        @Override
        public String id() {
            idReads++;
            return id;
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public List<Feature> features() {
            featureReads++;
            if (throwOnFeatures) {
                throw new IllegalStateException("features");
            }
            return features;
        }

        @Override
        public Optional<Envelope> envelope() {
            return Optional.empty();
        }

        private void resetCounts() {
            idReads = 0;
            featureReads = 0;
        }

        private void assertReadOnce() {
            assertEquals(1, idReads, "id reads");
            assertEquals(1, featureReads, "feature reads");
        }
    }
}
