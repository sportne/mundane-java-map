package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.LineSymbol;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class MapViewScreenGeometryOptimizationTest {
    private static final int SIZE = 100;
    private static final SymbolRendererKey CUSTOM_LINE_KEY =
            new SymbolRendererKey("test.screen-geometry.custom-line");

    @Test
    void enabledAndDisabledModesPaintEquivalentLineAndFillRegions() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    SolidLineSymbol lineSymbol = lineSymbol(3.0);
                    double[] values = new double[402];
                    for (int index = 0; index < values.length / 2; index++) {
                        values[index * 2] = -80.0 + index;
                        values[index * 2 + 1] = index % 2 == 0 ? 0.0 : 0.1;
                    }
                    Feature line =
                            feature(
                                    "dense-line",
                                    new LineStringGeometry(CoordinateSequence.of(values)),
                                    lineSymbol);
                    Feature fill =
                            feature(
                                    "partial-fill",
                                    new PolygonGeometry(
                                            CoordinateSequence.of(
                                                    -80, -20, 20, -20, 20, 20, -80, 20, -80, -20)),
                                    SolidFillSymbol.of(
                                            Rgba.rgb(40, 120, 210),
                                            Optional.of(lineSymbol(2.0)),
                                            0.7));

                    BufferedImage disabled =
                            paint(view(ScreenGeometryOptimizationMode.DISABLED, line, fill));
                    BufferedImage enabled =
                            paint(view(ScreenGeometryOptimizationMode.LEVEL1, line, fill));

                    assertImagesSemanticallyEquivalent(disabled, enabled);
                });
    }

    @Test
    void culledCenterlineStillPaintsItsAuthoritativeEndpointMarker() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    SolidLineSymbol symbol =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(20, 40, 80),
                                            new SymbolLength(1.0, SymbolUnit.SCREEN_PIXEL)),
                                    Optional.of(
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.CIRCLE,
                                                    Rgba.rgb(220, 40, 40),
                                                    24.0,
                                                    1.0)),
                                    Optional.empty(),
                                    1.0);
                    Feature feature =
                            feature(
                                    "endpoint",
                                    new LineStringGeometry(
                                            CoordinateSequence.of(-55, -10, -55, 10)),
                                    symbol);
                    MapView view = view(ScreenGeometryOptimizationMode.LEVEL1, feature);

                    BufferedImage image = paint(view);

                    assertTrue(hasNonWhitePixel(image, 0, 38, 8, 62));
                    assertTrue(view.hitTest(0.0, 50.0, 12.0).topmost().isPresent());
                });
    }

    @Test
    void optimizationDoesNotChangeAuthoritativeHitOrderOrGeometry() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    LineStringGeometry geometry =
                            new LineStringGeometry(CoordinateSequence.of(-80, 0, 0, 0.1, 80, 0));
                    Feature bottom = feature("bottom", geometry, lineSymbol(8.0));
                    Feature top = feature("top", geometry, lineSymbol(2.0));
                    MapView disabled = view(ScreenGeometryOptimizationMode.DISABLED, bottom, top);
                    MapView enabled = view(ScreenGeometryOptimizationMode.LEVEL1, bottom, top);

                    assertEquals(
                            disabled.hitTest(50, 50, 1.0).topmost().orElseThrow(),
                            enabled.hitTest(50, 50, 1.0).topmost().orElseThrow());
                    assertEquals(
                            "top",
                            enabled.hitTest(50, 50, 1.0).topmost().orElseThrow().featureId());
                    assertFalse(enabled.hitTest(50, 30, 1.0).topmost().isPresent());
                    dispatchMove(disabled, 50, 50);
                    dispatchMove(enabled, 50, 50);
                    assertEquals(disabled.hover(), enabled.hover());
                    assertEquals("top", enabled.hover().orElseThrow().featureId());
                    assertEquals(geometry, top.geometry());
                });
    }

    @Test
    void compositesAndLegacyStylesUseTheSameTolerantPaintSemantics() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    LineStringGeometry line =
                            new LineStringGeometry(
                                    CoordinateSequence.of(-80, 8, -20, 8.1, 20, 7.9, 80, 8));
                    Feature composite =
                            feature(
                                    "composite",
                                    line,
                                    CompositeSymbol.of(
                                            List.of(lineSymbol(5.0), lineSymbol(1.0)), 0.8));
                    Feature legacy =
                            new Feature(
                                    "legacy",
                                    "",
                                    new PolygonGeometry(
                                            CoordinateSequence.of(
                                                    -80, -20, 20, -20, 20, 0, -80, 0, -80, -20)),
                                    Map.of(),
                                    new FeatureStyle(
                                            Rgba.rgb(20, 90, 180),
                                            Rgba.rgb(60, 160, 80),
                                            2.0,
                                            8.0));

                    BufferedImage disabled =
                            paint(view(ScreenGeometryOptimizationMode.DISABLED, composite, legacy));
                    BufferedImage enabled =
                            paint(view(ScreenGeometryOptimizationMode.LEVEL1, composite, legacy));

                    assertImagesSemanticallyEquivalent(disabled, enabled);
                    assertEquals(disabled.getRGB(50, 50), enabled.getRGB(50, 50));
                });
    }

    @Test
    void customRendererReceivesAuthoritativeGeometryAndPaintPreservesGraphicsState()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    AtomicReference<io.github.mundanej.map.api.Geometry> observed =
                            new AtomicReference<>();
                    AwtSymbolRenderer renderer =
                            new AwtSymbolRenderer() {
                                @Override
                                public boolean supports(io.github.mundanej.map.api.Symbol value) {
                                    return value instanceof CustomLine;
                                }

                                @Override
                                public SymbolRenderResult render(
                                        io.github.mundanej.map.api.Symbol value,
                                        AwtSymbolRenderContext context) {
                                    observed.set(context.renderGeometry());
                                    return SymbolRenderResult.none();
                                }
                            };
                    SymbolRendererRegistry registry =
                            SymbolRendererRegistry.builderWithBuiltIns()
                                    .register(
                                            io.github.mundanej.map.api.SymbolRole.LINE,
                                            CUSTOM_LINE_KEY,
                                            renderer)
                                    .build();
                    LineStringGeometry geometry =
                            new LineStringGeometry(CoordinateSequence.of(-100, 0, 0, 0.1, 100, 0));
                    MapView view = TestMapViews.identity(registry);
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 1));
                    view.setLayers(
                            List.of(
                                    new InMemoryLayer(
                                            "layer",
                                            "Layer",
                                            List.of(
                                                    feature(
                                                            "custom",
                                                            geometry,
                                                            new CustomLine())))));
                    BufferedImage image =
                            new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    BasicStroke stroke = new BasicStroke(7.0f);
                    try {
                        graphics.setStroke(stroke);
                        graphics.setComposite(AlphaComposite.Src);
                        graphics.setColor(Color.MAGENTA);
                        view.paint(graphics);
                        assertEquals(stroke, graphics.getStroke());
                        assertEquals(AlphaComposite.Src, graphics.getComposite());
                        assertEquals(Color.MAGENTA, graphics.getColor());
                    } finally {
                        graphics.dispose();
                    }
                    assertSameGeometry(geometry, observed.get());
                });
    }

    @Test
    void eligibleFillOutlineProjectsEveryRingCoordinateExactlyOnce() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CountingProjection projection = new CountingProjection();
                    MapView view =
                            new MapView(
                                    projection,
                                    SymbolRendererRegistry.builtIn(),
                                    ScreenGeometryOptimizationMode.LEVEL1);
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 5_000));
                    PolygonGeometry polygon =
                            new PolygonGeometry(
                                    CoordinateSequence.of(-1, -1, 1, -1, 1, 1, -1, 1, -1, -1));
                    view.setLayers(
                            List.of(
                                    new InMemoryLayer(
                                            "layer",
                                            "Layer",
                                            List.of(
                                                    feature(
                                                            "polygon",
                                                            polygon,
                                                            SolidFillSymbol.of(
                                                                    Rgba.rgb(40, 120, 210),
                                                                    Optional.of(lineSymbol(2.0)),
                                                                    1.0))))));

                    paint(view);

                    assertEquals(polygon.exterior().size(), projection.projectCalls.get());
                });
    }

    @Test
    void legacyLineUsingSourceToDisplayOperationProjectsEachCoordinateExactlyOnce()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CountingProjection projection = new CountingProjection();
                    MapView view =
                            new MapView(
                                    projection,
                                    SymbolRendererRegistry.builtIn(),
                                    ScreenGeometryOptimizationMode.LEVEL1);
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 5));
                    LineStringGeometry line =
                            new LineStringGeometry(
                                    CoordinateSequence.of(
                                            -0.001, 0, -0.0005, 0, 0.0005, 0, 0.001, 0));
                    view.setLayers(
                            List.of(
                                    new InMemoryLayer(
                                            "legacy-source",
                                            "Legacy source",
                                            List.of(
                                                    new Feature(
                                                            "legacy-line",
                                                            "",
                                                            line,
                                                            Map.of(),
                                                            FeatureStyle.line(
                                                                    Rgba.rgb(180, 40, 40), 3))))));

                    BufferedImage image =
                            new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    ScreenGeometryPaintResult result;
                    try {
                        result = view.paintWithScreenGeometryResult(graphics);
                    } finally {
                        graphics.dispose();
                    }

                    assertEquals(line.coordinates().size(), projection.projectCalls.get());
                    assertEquals(line.coordinates().size(), result.projectedCoordinates());
                    assertTrue(hasNonWhitePixel(image, 25, 48, 75, 52));
                });
    }

    @Test
    void featureSourceUsesItsResolvedOperationForPreflightAndOneOptimizedProjection()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CountingProjection projection = new CountingProjection();
                    CrsRegistry crsRegistry =
                            CrsRegistry.builder()
                                    .registerDefinition(CrsDefinitions.EPSG_4326, List.of())
                                    .registerDefinition(CrsDefinitions.EPSG_3857, List.of())
                                    .registerProjection(projection)
                                    .build();
                    MapView view =
                            new MapView(
                                    crsRegistry,
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857,
                                    SymbolRendererRegistry.builtIn(),
                                    ScreenGeometryOptimizationMode.LEVEL1);
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 5));
                    LineStringGeometry line =
                            new LineStringGeometry(
                                    CoordinateSequence.of(
                                            -0.001, 0, -0.0005, 0, 0.0005, 0, 0.001, 0));
                    InMemoryFeatureSource source =
                            InMemoryFeatureSource.open(
                                    new SourceIdentity("geographic-line", "Geographic line"),
                                    List.of(new FeatureRecord("line", "", line, Map.of())),
                                    Optional.empty(),
                                    Optional.of(
                                            CrsMetadata.recognized(
                                                    CrsDefinitions.EPSG_4326,
                                                    Optional.of("EPSG:4326"),
                                                    Optional.empty())),
                                    FeatureSourceLimits.LEVEL_1);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedFeature(
                                            "source",
                                            "Source",
                                            source,
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.CIRCLE,
                                                    Rgba.rgb(40, 40, 40),
                                                    8,
                                                    1),
                                            lineSymbol(3),
                                            SolidFillSymbol.of(Rgba.rgb(40, 120, 210), 1))));

                    BufferedImage image =
                            new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    ScreenGeometryPaintResult result;
                    try {
                        result = view.paintWithScreenGeometryResult(graphics);
                    } finally {
                        graphics.dispose();
                    }

                    assertEquals(line.coordinates().size() * 2, projection.projectCalls.get());
                    assertEquals(line.coordinates().size(), result.projectedCoordinates());
                    assertTrue(hasNonWhitePixel(image, 25, 48, 75, 52));
                    view.close();
                    source.close();
                });
    }

    @Test
    void selectedOverlayBuildsItsOwnOptimizedPaintWithoutChangingAuthoritativeState()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    LineStringGeometry geometry =
                            new LineStringGeometry(
                                    CoordinateSequence.of(-80, 0, -20, 0.1, 20, -0.1, 80, 0));
                    MapView view =
                            view(
                                    ScreenGeometryOptimizationMode.LEVEL1,
                                    feature("line", geometry, lineSymbol(2.0)));
                    BufferedImage base = paint(view);
                    view.setSelection(new FeatureSelection("layer", "line"));

                    BufferedImage selected = paint(view);

                    assertTrue(pixelDifferenceCount(base, selected) > 0);
                    assertEquals(
                            "line", view.hitTest(50, 50, 1.0).topmost().orElseThrow().featureId());
                    assertEquals(
                            geometry, view.layers().getFirst().features().getFirst().geometry());
                });
    }

    @Test
    void evidenceFactsAreExactOperationLocalAndDoNotChangeOrdinaryPaint() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature line =
                            feature(
                                    "line",
                                    new LineStringGeometry(
                                            CoordinateSequence.of(-30, 0, -10, 0, 10, 0, 30, 0)),
                                    lineSymbol(2.0));
                    MapView disabled = view(ScreenGeometryOptimizationMode.DISABLED, line);
                    MapView enabled = view(ScreenGeometryOptimizationMode.LEVEL1, line);

                    ScreenGeometryPaintResult disabledResult = evidencePaint(disabled);
                    ScreenGeometryPaintResult first = evidencePaint(enabled);
                    paint(enabled);
                    ScreenGeometryPaintResult second = evidencePaint(enabled);

                    assertEquals(
                            new ScreenGeometryPaintResult(
                                    4, 4, 4, 1, 0, 0, 0, RenderCachePaintMetrics.empty()),
                            disabledResult);
                    assertEquals(
                            new ScreenGeometryPaintResult(
                                    4, 4, 2, 1, 0, 0, 32, RenderCachePaintMetrics.empty()),
                            first);
                    assertEquals(first, second);
                });
    }

    @Test
    void multipartCompositeProjectsOnceAndUsesOneWholeFeaturePlan() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CountingProjection projection = new CountingProjection();
                    MapView view =
                            new MapView(
                                    projection,
                                    SymbolRendererRegistry.builtIn(),
                                    ScreenGeometryOptimizationMode.LEVEL1);
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 5_000));
                    MultiLineStringGeometry geometry =
                            MultiLineStringGeometry.ofParts(
                                    List.of(
                                            CoordinateSequence.of(-1, 0, -0.5, 0.01, 0, 0),
                                            CoordinateSequence.of(0, 0.5, 0.5, 0.51, 1, 0.5)));
                    view.setLayers(
                            List.of(
                                    new InMemoryLayer(
                                            "layer",
                                            "Layer",
                                            List.of(
                                                    feature(
                                                            "multipart",
                                                            geometry,
                                                            CompositeSymbol.of(
                                                                    List.of(
                                                                            lineSymbol(6.0),
                                                                            lineSymbol(2.0)),
                                                                    1.0))))));

                    ScreenGeometryPaintResult result = evidencePaint(view);

                    assertEquals(geometry.coordinates().size(), projection.projectCalls.get());
                    assertEquals(geometry.coordinates().size(), result.inputCoordinates());
                    assertEquals(geometry.coordinates().size(), result.projectedCoordinates());
                    assertEquals(2, result.lineFragments());
                });
    }

    @Test
    void customCompositeChildDisablesOptimizationForTheCompleteRoleTree() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    AtomicReference<io.github.mundanej.map.api.Geometry> observed =
                            new AtomicReference<>();
                    AwtSymbolRenderer customRenderer =
                            new AwtSymbolRenderer() {
                                @Override
                                public boolean supports(io.github.mundanej.map.api.Symbol value) {
                                    return value instanceof CustomLine;
                                }

                                @Override
                                public SymbolRenderResult render(
                                        io.github.mundanej.map.api.Symbol value,
                                        AwtSymbolRenderContext context) {
                                    observed.set(context.renderGeometry());
                                    return SymbolRenderResult.none();
                                }
                            };
                    SymbolRendererRegistry registry =
                            SymbolRendererRegistry.builderWithBuiltIns()
                                    .register(
                                            io.github.mundanej.map.api.SymbolRole.LINE,
                                            CUSTOM_LINE_KEY,
                                            customRenderer)
                                    .build();
                    LineStringGeometry geometry =
                            new LineStringGeometry(
                                    CoordinateSequence.of(-100, 0, -20, 0.1, 20, -0.1, 100, 0));
                    MapView view =
                            TestMapViews.identity(registry, ScreenGeometryOptimizationMode.LEVEL1);
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 1));
                    view.setLayers(
                            List.of(
                                    new InMemoryLayer(
                                            "layer",
                                            "Layer",
                                            List.of(
                                                    feature(
                                                            "mixed",
                                                            geometry,
                                                            CompositeSymbol.of(
                                                                    List.of(
                                                                            lineSymbol(2.0),
                                                                            new CustomLine()),
                                                                    1.0))))));

                    ScreenGeometryPaintResult result = evidencePaint(view);

                    assertSameGeometry(geometry, observed.get());
                    assertEquals(new ScreenGeometryPaintResult(0, 0, 0, 0, 0, 0, 0), result);
                });
    }

    @Test
    void hatchWithHoleRetainsEnabledDisabledPaintSemantics() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    PolygonGeometry polygon =
                            new PolygonGeometry(
                                    CoordinateSequence.of(
                                            -70, -40, 70, -40, 70, 40, -70, 40, -70, -40),
                                    List.of(
                                            CoordinateSequence.of(
                                                    -15, -15, -15, 15, 15, 15, 15, -15, -15, -15)));
                    HatchFillSymbol hatch =
                            HatchFillSymbol.of(
                                    HatchPattern.CROSS_DIAGONAL,
                                    new SymbolStroke(
                                            Rgba.rgb(30, 120, 80),
                                            new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                                    new SymbolLength(9, SymbolUnit.SCREEN_PIXEL),
                                    SymbolRotationMode.SCREEN_RELATIVE,
                                    Optional.of(lineSymbol(3.0)),
                                    0.8,
                                    256);

                    BufferedImage disabled =
                            paint(
                                    view(
                                            ScreenGeometryOptimizationMode.DISABLED,
                                            feature("hatch", polygon, hatch)));
                    BufferedImage enabled =
                            paint(
                                    view(
                                            ScreenGeometryOptimizationMode.LEVEL1,
                                            feature("hatch", polygon, hatch)));

                    assertImagesSemanticallyEquivalent(disabled, enabled);
                    assertEquals(Color.WHITE.getRGB(), enabled.getRGB(50, 50));
                });
    }

    @Test
    void selectionOverlayBuildsOneSeparatePlanWithItsOwnStrokeMargin() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CountingProjection projection = new CountingProjection();
                    MapView view =
                            new MapView(
                                    projection,
                                    SymbolRendererRegistry.builtIn(),
                                    ScreenGeometryOptimizationMode.LEVEL1);
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 5_000));
                    LineStringGeometry geometry =
                            new LineStringGeometry(
                                    CoordinateSequence.of(
                                            -1, 0, -0.5, 0.01, 0, 0, 0.5, -0.01, 1, 0));
                    view.setLayers(
                            List.of(
                                    new InMemoryLayer(
                                            "layer",
                                            "Layer",
                                            List.of(feature("line", geometry, lineSymbol(2))))));

                    evidencePaint(view);
                    assertEquals(geometry.coordinates().size(), projection.projectCalls.get());
                    projection.projectCalls.set(0);
                    view.setSelection(new FeatureSelection("layer", "line"));

                    ScreenGeometryPaintResult selected = evidencePaint(view);

                    assertEquals(geometry.coordinates().size() * 2, projection.projectCalls.get());
                    assertEquals(geometry.coordinates().size() * 2, selected.inputCoordinates());
                });
    }

    private static SolidLineSymbol lineSymbol(double width) {
        return SolidLineSymbol.of(
                new SymbolStroke(
                        Rgba.rgb(20, 40, 80), new SymbolLength(width, SymbolUnit.SCREEN_PIXEL)),
                1.0);
    }

    private static Feature feature(
            String id,
            io.github.mundanej.map.api.Geometry geometry,
            io.github.mundanej.map.api.Symbol symbol) {
        return new Feature(id, "", geometry, Map.of(), symbol);
    }

    private static MapView view(ScreenGeometryOptimizationMode mode, Feature... features) {
        MapView view = TestMapViews.identity(mode);
        view.setSize(SIZE, SIZE);
        view.setViewport(new MapViewport(SIZE, SIZE, 0.0, 0.0, 1.0));
        view.setLayers(List.of(new InMemoryLayer("layer", "Layer", List.of(features))));
        return view;
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static ScreenGeometryPaintResult evidencePaint(MapView view) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            return view.paintWithScreenGeometryResult(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static void dispatchMove(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseEvent(
                        view, MouseEvent.MOUSE_MOVED, 1L, 0, x, y, 0, false, MouseEvent.NOBUTTON));
    }

    private static void assertImagesSemanticallyEquivalent(
            BufferedImage disabled, BufferedImage enabled) {
        int differing = 0;
        for (int y = 0; y < disabled.getHeight(); y++) {
            for (int x = 0; x < disabled.getWidth(); x++) {
                int first = disabled.getRGB(x, y);
                int second = enabled.getRGB(x, y);
                if (first != second) {
                    differing++;
                }
            }
        }
        assertTrue(differing < 500, "differing pixels: " + differing);
        int[] disabledBounds = paintedBounds(disabled);
        int[] enabledBounds = paintedBounds(enabled);
        for (int index = 0; index < disabledBounds.length; index++) {
            assertTrue(
                    Math.abs(disabledBounds[index] - enabledBounds[index]) <= 1,
                    "painted bounds differ: "
                            + java.util.Arrays.toString(disabledBounds)
                            + " vs "
                            + java.util.Arrays.toString(enabledBounds));
        }
        int disabledCount = paintedPixelCount(disabled);
        int enabledCount = paintedPixelCount(enabled);
        assertTrue(disabledCount > 0);
        assertTrue(
                Math.abs(disabledCount - enabledCount) <= Math.max(8, disabledCount / 20),
                "painted pixel counts differ: " + disabledCount + " vs " + enabledCount);
        assertEquals(disabled.getRGB(50, 50), enabled.getRGB(50, 50));
    }

    private static int[] paintedBounds(BufferedImage image) {
        int minimumX = image.getWidth();
        int minimumY = image.getHeight();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        return new int[] {minimumX, minimumY, maximumX, maximumY};
    }

    private static int paintedPixelCount(BufferedImage image) {
        int result = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    result++;
                }
            }
        }
        return result;
    }

    private static int pixelDifferenceCount(BufferedImage first, BufferedImage second) {
        int result = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    result++;
                }
            }
        }
        return result;
    }

    private static boolean hasNonWhitePixel(
            BufferedImage image, int minimumX, int minimumY, int maximumX, int maximumY) {
        for (int y = minimumY; y <= maximumY; y++) {
            for (int x = minimumX; x <= maximumX; x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0x00ffffff) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertSameGeometry(
            io.github.mundanej.map.api.Geometry expected,
            io.github.mundanej.map.api.Geometry actual) {
        assertTrue(expected == actual, "custom renderer geometry must retain identity");
    }

    private static final class CustomLine implements LineSymbol {
        @Override
        public double opacity() {
            return 1.0;
        }

        @Override
        public SymbolRendererKey rendererKey() {
            return CUSTOM_LINE_KEY;
        }
    }

    private static final class CountingProjection implements Projection {
        private final WebMercatorProjection delegate = new WebMercatorProjection();
        private final AtomicInteger projectCalls = new AtomicInteger();

        @Override
        public CrsDefinition sourceCrs() {
            return delegate.sourceCrs();
        }

        @Override
        public CrsDefinition targetCrs() {
            return delegate.targetCrs();
        }

        @Override
        public Envelope sourceDomain() {
            return delegate.sourceDomain();
        }

        @Override
        public Envelope targetDomain() {
            return delegate.targetDomain();
        }

        @Override
        public Coordinate project(Coordinate source) {
            projectCalls.incrementAndGet();
            return delegate.project(source);
        }

        @Override
        public Coordinate unproject(Coordinate projected) {
            return delegate.unproject(projected);
        }

        @Override
        public Envelope projectEnvelope(Envelope source) {
            return delegate.projectEnvelope(source);
        }

        @Override
        public Envelope unprojectEnvelope(Envelope target) {
            return delegate.unprojectEnvelope(target);
        }
    }
}
