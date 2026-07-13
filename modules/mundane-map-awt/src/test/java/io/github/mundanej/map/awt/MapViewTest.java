package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapPointerEvent;
import io.github.mundanej.map.api.MapPointerListener;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapViewport;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class MapViewTest {
    private static final int IMAGE_SIZE = 100;
    private static final double TOLERANCE = 1.0e-9;
    private static final Projection IDENTITY =
            new Projection() {
                @Override
                public String id() {
                    return "identity";
                }

                @Override
                public Coordinate project(Coordinate source) {
                    return source;
                }

                @Override
                public Coordinate unproject(Coordinate projected) {
                    return projected;
                }
            };

    @Test
    void rendersPointFillAndStrokeIndependently() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature point =
                            feature(
                                    "point",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    new FeatureStyle(
                                            Rgba.rgb(220, 30, 30),
                                            Rgba.rgb(20, 80, 210),
                                            2.0,
                                            20.0));

                    BufferedImage image = render(point);

                    assertColorNear(Rgba.rgb(20, 80, 210), image.getRGB(50, 50), 2);
                    assertRegionContainsColor(image, 57, 47, 62, 53, Rgba.rgb(220, 30, 30), 35);
                    assertColorNear(Rgba.rgb(255, 255, 255), image.getRGB(20, 20), 0);
                });
    }

    @Test
    void rendersEveryBuiltInThroughTheVectorMarkerPath() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Rgba fill = Rgba.rgb(35, 105, 205);
                    for (BuiltInMarker marker : BuiltInMarker.values()) {
                        Feature feature =
                                feature(
                                        marker.name(),
                                        new PointGeometry(new Coordinate(0.0, 0.0)),
                                        BuiltInMarkers.filledScreen(marker, fill, 24.0, 1.0));

                        BufferedImage image = render(feature);
                        int[] bounds = paintedBounds(image);

                        assertColorNear(fill, image.getRGB(50, 50), 5);
                        assertTrue(bounds[0] >= 36 && bounds[0] <= 42, marker::name);
                        assertTrue(bounds[1] >= 36 && bounds[1] <= 42, marker::name);
                        assertTrue(bounds[2] >= 58 && bounds[2] <= 63, marker::name);
                        assertTrue(bounds[3] >= 58 && bounds[3] <= 63, marker::name);
                        assertTrue(bounds[2] - bounds[0] >= 17, marker::name);
                        assertTrue(bounds[3] - bounds[1] >= 17, marker::name);
                        assertColorNear(Rgba.rgb(255, 255, 255), image.getRGB(20, 20), 0);
                        assertShapeProbe(marker, image, fill);
                    }
                });
    }

    @Test
    void vectorMarkerComposesColorAlphaAndOpacityAndSkipsZeroOpacity() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    VectorMarkerSymbol translucent =
                            BuiltInMarkers.filledScreen(
                                    BuiltInMarker.SQUARE, new Rgba(200, 20, 40, 128), 20.0, 0.5);
                    VectorMarkerSymbol invisible =
                            BuiltInMarkers.filledScreen(
                                    BuiltInMarker.SQUARE, Rgba.rgb(200, 20, 40), 20.0, 0.0);

                    Color blended =
                            new Color(
                                    render(
                                                    feature(
                                                            "translucent",
                                                            new PointGeometry(
                                                                    new Coordinate(0.0, 0.0)),
                                                            translucent))
                                            .getRGB(50, 50),
                                    true);
                    assertEquals(241, blended.getRed(), 2);
                    assertEquals(196, blended.getGreen(), 2);
                    assertEquals(201, blended.getBlue(), 2);
                    assertEquals(255, blended.getAlpha());
                    assertColorNear(
                            Rgba.rgb(255, 255, 255),
                            render(
                                            feature(
                                                    "invisible",
                                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                                    invisible))
                                    .getRGB(50, 50),
                            0);
                });
    }

    @Test
    void placementControlsAnchorOffsetUnitsZoomAndClockwiseRotation() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MarkerPlacement anchored =
                            new MarkerPlacement(
                                    new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                                    SymbolAnchor.NORTH_WEST,
                                    5.0,
                                    7.0,
                                    0.0,
                                    SymbolRotationMode.SCREEN_RELATIVE);
                    BufferedImage anchoredImage = render(vectorSquare(anchored, null));
                    int[] anchoredBounds = paintedBounds(anchoredImage);
                    assertTrue(anchoredBounds[0] >= 54 && anchoredBounds[0] <= 56);
                    assertTrue(anchoredBounds[1] >= 56 && anchoredBounds[1] <= 58);
                    assertTrue(anchoredBounds[2] >= 74 && anchoredBounds[2] <= 76);
                    assertTrue(anchoredBounds[3] >= 66 && anchoredBounds[3] <= 68);

                    MarkerPlacement rotated =
                            new MarkerPlacement(
                                    new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                                    SymbolAnchor.CENTER,
                                    0.0,
                                    0.0,
                                    90.0,
                                    SymbolRotationMode.SCREEN_RELATIVE);
                    int[] rotatedBounds = paintedBounds(render(vectorSquare(rotated, null)));
                    assertTrue(rotatedBounds[2] - rotatedBounds[0] <= 11);
                    assertTrue(rotatedBounds[3] - rotatedBounds[1] >= 19);

                    MarkerPlacement screenPlacement = MarkerPlacement.centeredScreen(20.0);
                    MarkerPlacement mapPlacement =
                            new MarkerPlacement(
                                    SymbolSize.square(20.0, SymbolUnit.MAP_UNIT),
                                    SymbolAnchor.CENTER,
                                    0.0,
                                    0.0,
                                    0.0,
                                    SymbolRotationMode.MAP_RELATIVE);
                    Feature screenFeature = vectorSquare(screenPlacement, null);
                    Feature mapFeature = vectorSquare(mapPlacement, null);
                    int screenAtOne = paintedWidth(renderAtScale(screenFeature, 1.0));
                    int screenAtTwo = paintedWidth(renderAtScale(screenFeature, 2.0));
                    int mapAtOne = paintedWidth(renderAtScale(mapFeature, 1.0));
                    int mapAtTwo = paintedWidth(renderAtScale(mapFeature, 2.0));
                    assertEquals(screenAtOne, screenAtTwo, 1);
                    assertTrue(mapAtOne >= mapAtTwo * 2 - 2);
                });
    }

    @Test
    void vectorStrokeAndCompositeOrderingUseIndependentChildGraphics() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    SymbolStroke stroke =
                            new SymbolStroke(
                                    Rgba.rgb(190, 30, 30),
                                    new SymbolLength(4.0, SymbolUnit.SCREEN_PIXEL));
                    BufferedImage stroked =
                            render(vectorSquare(MarkerPlacement.centeredScreen(20.0), stroke));
                    assertColorNear(Rgba.rgb(35, 105, 205), stroked.getRGB(50, 50), 4);
                    assertRegionContainsColor(stroked, 38, 47, 42, 53, Rgba.rgb(190, 30, 30), 20);

                    Symbol red = squareSymbol(Rgba.rgb(200, 30, 30), 24.0, 1.0);
                    Symbol blue = squareSymbol(Rgba.rgb(30, 30, 200), 12.0, 1.0);
                    Feature blueOnTop =
                            feature(
                                    "blue-top",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    CompositeSymbol.of(List.of(red, blue), 1.0));
                    Feature redOnTop =
                            feature(
                                    "red-top",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    CompositeSymbol.of(List.of(blue, red), 1.0));
                    assertColorNear(Rgba.rgb(30, 30, 200), render(blueOnTop).getRGB(50, 50), 4);
                    assertColorNear(Rgba.rgb(200, 30, 30), render(redOnTop).getRGB(50, 50), 4);
                    assertColorNear(Rgba.rgb(200, 30, 30), render(blueOnTop).getRGB(40, 50), 4);

                    Symbol nested =
                            CompositeSymbol.of(
                                    List.of(
                                            CompositeSymbol.of(
                                                    List.of(
                                                            squareSymbol(
                                                                    Rgba.rgb(255, 0, 0),
                                                                    16.0,
                                                                    0.5)),
                                                    0.5)),
                                    0.5);
                    Color nestedPixel =
                            new Color(
                                    render(
                                                    feature(
                                                            "nested",
                                                            new PointGeometry(
                                                                    new Coordinate(0.0, 0.0)),
                                                            nested))
                                            .getRGB(50, 50),
                                    true);
                    assertEquals(255, nestedPixel.getRed(), 1);
                    assertEquals(223, nestedPixel.getGreen(), 2);
                    assertEquals(223, nestedPixel.getBlue(), 2);
                });
    }

    @Test
    void awtRendererUsesSyntheticRotatedBasisForMapRelativePlacement() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapScreenBasis rotatedBasis =
                            MapScreenBasis.of(new Coordinate(0.0, 2.0), new Coordinate(2.0, 0.0));
                    MarkerPlacement screenRelative =
                            new MarkerPlacement(
                                    new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                                    SymbolAnchor.CENTER,
                                    0.0,
                                    0.0,
                                    0.0,
                                    SymbolRotationMode.SCREEN_RELATIVE);
                    MarkerPlacement mapRelative =
                            new MarkerPlacement(
                                    new SymbolSize(20.0, 10.0, SymbolUnit.SCREEN_PIXEL),
                                    SymbolAnchor.CENTER,
                                    0.0,
                                    0.0,
                                    0.0,
                                    SymbolRotationMode.MAP_RELATIVE);

                    int[] screenBounds =
                            paintedBounds(
                                    renderMarkerWithBasis(
                                            vectorSquareSymbol(screenRelative, null, 1.0),
                                            rotatedBasis));
                    int[] mapBounds =
                            paintedBounds(
                                    renderMarkerWithBasis(
                                            vectorSquareSymbol(mapRelative, null, 1.0),
                                            rotatedBasis));

                    assertTrue(screenBounds[2] - screenBounds[0] >= 19);
                    assertTrue(screenBounds[3] - screenBounds[1] <= 11);
                    assertTrue(mapBounds[2] - mapBounds[0] <= 11);
                    assertTrue(mapBounds[3] - mapBounds[1] >= 19);
                });
    }

    @Test
    void screenAndMapStrokeUnitsAreConvertedIndependentlyOfMarkerTransform() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapScreenBasis doubleScale =
                            MapScreenBasis.of(new Coordinate(2.0, 0.0), new Coordinate(0.0, -2.0));
                    SymbolStroke screenStroke =
                            new SymbolStroke(
                                    Rgba.rgb(190, 30, 30),
                                    new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL));
                    SymbolStroke mapStroke =
                            new SymbolStroke(
                                    Rgba.rgb(190, 30, 30),
                                    new SymbolLength(2.0, SymbolUnit.MAP_UNIT));

                    int screenWidth =
                            paintedWidth(
                                    renderMarkerWithBasis(
                                            vectorSquareSymbol(
                                                    MarkerPlacement.centeredScreen(20.0),
                                                    screenStroke,
                                                    1.0),
                                            doubleScale));
                    int mapWidth =
                            paintedWidth(
                                    renderMarkerWithBasis(
                                            vectorSquareSymbol(
                                                    MarkerPlacement.centeredScreen(20.0),
                                                    mapStroke,
                                                    1.0),
                                            doubleScale));

                    assertTrue(mapWidth >= screenWidth + 2);
                });
    }

    @Test
    void transparentMarkerStillPositionsItsLabelFromNominalBounds() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MarkerPlacement placement =
                            new MarkerPlacement(
                                    new SymbolSize(20.0, 20.0, SymbolUnit.SCREEN_PIXEL),
                                    SymbolAnchor.NORTH_WEST,
                                    0.0,
                                    0.0,
                                    0.0,
                                    SymbolRotationMode.SCREEN_RELATIVE);
                    Feature transparent =
                            new Feature(
                                    "transparent",
                                    "Label",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    Map.of(),
                                    vectorSquareSymbol(placement, null, 0.0));

                    int[] labelBounds = paintedBounds(render(transparent));

                    assertTrue(labelBounds[0] >= 72);
                    assertTrue(labelBounds[2] > 90);
                    assertTrue(labelBounds[3] <= 50);
                });
    }

    @Test
    void mapPaintingPreservesCallerGraphicsState() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature feature =
                            vectorSquare(
                                    MarkerPlacement.centeredScreen(20.0),
                                    new SymbolStroke(
                                            Rgba.rgb(1, 2, 3),
                                            new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)));
                    MapView view = configuredView(feature);
                    BufferedImage image =
                            new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.translate(3.0, 4.0);
                        graphics.clipRect(0, 0, 80, 70);
                        graphics.setComposite(AlphaComposite.Src);
                        graphics.setColor(Color.MAGENTA);
                        graphics.setStroke(new BasicStroke(7.0f));
                        java.awt.geom.AffineTransform transform = graphics.getTransform();
                        java.awt.Shape clip = graphics.getClip();
                        java.awt.Composite composite = graphics.getComposite();
                        java.awt.Paint paint = graphics.getPaint();
                        java.awt.Stroke currentStroke = graphics.getStroke();

                        view.paint(graphics);

                        assertEquals(transform, graphics.getTransform());
                        assertEquals(clip.getBounds(), graphics.getClip().getBounds());
                        assertEquals(composite, graphics.getComposite());
                        assertEquals(paint, graphics.getPaint());
                        assertEquals(currentStroke, graphics.getStroke());
                    } finally {
                        graphics.dispose();
                    }
                });
    }

    @Test
    void closedDispatcherReportsUnknownAndWrongMarkerValues() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature unknown =
                            feature(
                                    "unknown",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    new TestMarker(new SymbolRendererKey("example.unknown")));
                    SymbolException unregistered =
                            assertThrows(SymbolException.class, () -> render(unknown));
                    assertEquals(SymbolException.RENDERER_NOT_REGISTERED, unregistered.code());
                    assertEquals("MARKER", unregistered.context().get("role"));
                    assertEquals("example.unknown", unregistered.context().get("key"));

                    Feature impostor =
                            feature(
                                    "impostor",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    new TestMarker(VectorMarkerSymbol.RENDERER_KEY));
                    SymbolException mismatch =
                            assertThrows(SymbolException.class, () -> render(impostor));
                    assertEquals(SymbolException.RENDERER_VALUE_MISMATCH, mismatch.code());
                });
    }

    @Test
    void closedDispatcherSnapshotsMutableMarkerIdentityIntoStableDiagnostics() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MutableMarker mutable = new MutableMarker();
                    Feature feature =
                            feature(
                                    "mutable",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    mutable);

                    mutable.role = null;
                    mutable.rendererKey = null;
                    SymbolException roleMismatch =
                            assertThrows(SymbolException.class, () -> render(feature));
                    assertEquals(SymbolException.ROLE_MISMATCH, roleMismatch.code());
                    assertEquals("null", roleMismatch.context().get("symbolRole"));

                    mutable.role = io.github.mundanej.map.api.SymbolRole.MARKER;
                    SymbolException missingRenderer =
                            assertThrows(SymbolException.class, () -> render(feature));
                    assertEquals(SymbolException.RENDERER_NOT_REGISTERED, missingRenderer.code());
                    assertEquals("MARKER", missingRenderer.context().get("role"));
                    assertEquals("null", missingRenderer.context().get("key"));
                });
    }

    @Test
    void pointLabelBaselineUsesNominalMarkerBounds() {
        Point2D baseline =
                MapView.pointLabelBaseline(new Rectangle2D.Double(40.0, 41.0, 20.0, 18.0));

        assertEquals(64.0, baseline.getX());
        assertEquals(39.0, baseline.getY());
        assertThrows(NullPointerException.class, () -> MapView.pointLabelBaseline(null));
    }

    @Test
    void rendersLineStrokeAndSkipsZeroWidthStroke() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    LineStringGeometry line =
                            new LineStringGeometry(CoordinateSequence.of(-20.0, 0.0, 20.0, 0.0));
                    Feature visible =
                            feature("line", line, FeatureStyle.line(Rgba.rgb(180, 40, 40), 3.0));
                    Feature hidden =
                            feature(
                                    "hidden-line",
                                    line,
                                    FeatureStyle.line(Rgba.rgb(180, 40, 40), 0.0));

                    assertColorNear(Rgba.rgb(180, 40, 40), render(visible).getRGB(50, 50), 3);
                    assertColorNear(Rgba.rgb(255, 255, 255), render(hidden).getRGB(50, 50), 0);
                });
    }

    @Test
    void rendersPolygonFillStrokeAndHoleIndependently() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    CoordinateSequence exterior =
                            CoordinateSequence.of(
                                    -20.0, -20.0,
                                    20.0, -20.0,
                                    20.0, 20.0,
                                    -20.0, 20.0,
                                    -20.0, -20.0);
                    CoordinateSequence hole =
                            CoordinateSequence.of(
                                    -5.0, -5.0,
                                    5.0, -5.0,
                                    5.0, 5.0,
                                    -5.0, 5.0,
                                    -5.0, -5.0);
                    Feature polygon =
                            feature(
                                    "polygon",
                                    new PolygonGeometry(exterior, List.of(hole)),
                                    FeatureStyle.polygon(
                                            Rgba.rgb(25, 80, 35), Rgba.rgb(55, 180, 75), 2.0));

                    BufferedImage image = render(polygon);

                    assertColorNear(Rgba.rgb(55, 180, 75), image.getRGB(60, 50), 3);
                    assertRegionContainsColor(image, 28, 46, 32, 54, Rgba.rgb(25, 80, 35), 30);
                    assertColorNear(Rgba.rgb(255, 255, 255), image.getRGB(50, 50), 0);
                    assertColorNear(Rgba.rgb(255, 255, 255), image.getRGB(10, 10), 0);
                });
    }

    @Test
    void eachPaintClearsThePreviousFrame() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature point =
                            feature(
                                    "point",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    FeatureStyle.point(Rgba.rgb(20, 80, 210), 12.0));
                    MapView view = configuredView(point);
                    BufferedImage image =
                            new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
                    paint(view, image);
                    assertColorNear(Rgba.rgb(20, 80, 210), image.getRGB(50, 50), 2);

                    view.setViewport(new MapViewport(IMAGE_SIZE, IMAGE_SIZE, -20.0, 0.0, 1.0));
                    paint(view, image);

                    assertColorNear(Rgba.rgb(255, 255, 255), image.getRGB(50, 50), 0);
                    assertColorNear(Rgba.rgb(20, 80, 210), image.getRGB(70, 50), 2);
                });
    }

    @Test
    void installedMouseListenersPanResizeAndZoomAroundTheCursor() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = new MapView(IDENTITY);
                    view.setSize(200, 160);
                    view.setViewport(new MapViewport(200, 160, 1000.0, 2000.0, 10.0));

                    dispatchMouse(view, MouseEvent.MOUSE_PRESSED, 100, 80, MouseEvent.BUTTON1, 0);
                    dispatchMouse(
                            view,
                            MouseEvent.MOUSE_DRAGGED,
                            120,
                            110,
                            MouseEvent.NOBUTTON,
                            InputEvent.BUTTON1_DOWN_MASK);
                    dispatchMouse(view, MouseEvent.MOUSE_RELEASED, 120, 110, MouseEvent.BUTTON1, 0);

                    assertEquals(800.0, view.viewport().centerX(), TOLERANCE);
                    assertEquals(2300.0, view.viewport().centerY(), TOLERANCE);

                    Coordinate before = view.screenToMap(40.0, 55.0);
                    view.dispatchEvent(
                            new MouseWheelEvent(
                                    view,
                                    MouseEvent.MOUSE_WHEEL,
                                    System.currentTimeMillis(),
                                    0,
                                    40,
                                    55,
                                    0,
                                    false,
                                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                    1,
                                    -1));
                    Coordinate after = view.screenToMap(40.0, 55.0);

                    assertEquals(before.x(), after.x(), TOLERANCE);
                    assertEquals(before.y(), after.y(), TOLERANCE);

                    double scale = view.viewport().worldUnitsPerPixel();
                    double centerX = view.viewport().centerX();
                    double centerY = view.viewport().centerY();
                    view.setSize(320, 240);
                    assertEquals(320, view.viewport().width());
                    assertEquals(240, view.viewport().height());
                    assertEquals(scale, view.viewport().worldUnitsPerPixel(), TOLERANCE);
                    assertEquals(centerX, view.viewport().centerX(), TOLERANCE);
                    assertEquals(centerY, view.viewport().centerY(), TOLERANCE);
                });
    }

    @Test
    void pointerCallbacksCarryScreenAndMapCoordinatesOnTheEdt() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = new MapView(IDENTITY);
                    view.setSize(200, 100);
                    view.setViewport(new MapViewport(200, 100, 10.0, 20.0, 2.0));
                    List<MapPointerEvent> events = new ArrayList<>();
                    view.addMapPointerListener(
                            event -> {
                                assertTrue(SwingUtilities.isEventDispatchThread());
                                events.add(event);
                            });

                    dispatchMouse(view, MouseEvent.MOUSE_MOVED, 120, 40, MouseEvent.NOBUTTON, 0);
                    dispatchMouse(view, MouseEvent.MOUSE_CLICKED, 80, 70, MouseEvent.BUTTON1, 0);

                    assertEquals(
                            List.of(MapPointerEvent.Type.MOVED, MapPointerEvent.Type.CLICKED),
                            events.stream().map(MapPointerEvent::type).toList());
                    assertEquals(120.0, events.get(0).screenX(), TOLERANCE);
                    assertEquals(40.0, events.get(0).screenY(), TOLERANCE);
                    assertEquals(new Coordinate(50.0, 40.0), events.get(0).mapCoordinate());
                    assertEquals(new Coordinate(-30.0, -20.0), events.get(1).mapCoordinate());
                });
    }

    @Test
    void listenerIdentityDuplicatesAndCallbackMutationAreDeterministic() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = new MapView(IDENTITY);
                    view.setSize(100, 100);
                    EqualListener first = new EqualListener();
                    EqualListener equalButDistinct = new EqualListener();
                    view.addMapPointerListener(first);
                    view.addMapPointerListener(first);
                    view.addMapPointerListener(equalButDistinct);

                    view.removeMapPointerListener(equalButDistinct);
                    dispatchMouse(view, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0);
                    assertEquals(2, first.count());
                    assertEquals(0, equalButDistinct.count());

                    view.removeMapPointerListener(first);
                    dispatchMouse(view, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0);
                    assertEquals(3, first.count());

                    MapView mutationView = new MapView(IDENTITY);
                    mutationView.setSize(100, 100);
                    List<String> calls = new ArrayList<>();
                    MapPointerListener added = event -> calls.add("added");
                    MapPointerListener[] removed = new MapPointerListener[1];
                    MapPointerListener mutating =
                            event -> {
                                calls.add("mutating");
                                mutationView.removeMapPointerListener(removed[0]);
                                mutationView.addMapPointerListener(added);
                            };
                    removed[0] = event -> calls.add("removed");
                    mutationView.addMapPointerListener(mutating);
                    mutationView.addMapPointerListener(removed[0]);

                    dispatchMouse(
                            mutationView, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0);
                    assertEquals(List.of("mutating", "removed"), calls);
                    calls.clear();
                    dispatchMouse(
                            mutationView, MouseEvent.MOUSE_MOVED, 50, 50, MouseEvent.NOBUTTON, 0);
                    assertEquals(List.of("mutating", "added"), calls);
                });
    }

    @Test
    void fitHandlesEmptyPointAndLineLayers() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = new MapView(IDENTITY);
                    view.setSize(200, 100);
                    MapViewport initial = new MapViewport(200, 100, 12.0, 34.0, 5.0);
                    view.setViewport(initial);
                    view.setLayers(List.of(new InMemoryLayer("empty", "Empty", List.of())));

                    view.fitToData(10.0);
                    assertEquals(initial, view.viewport());

                    view.setLayers(
                            List.of(
                                    layer(
                                            feature(
                                                    "point",
                                                    new PointGeometry(new Coordinate(3.0, 4.0)),
                                                    FeatureStyle.point(
                                                            Rgba.rgb(20, 40, 60), 8.0)))));
                    view.fitToData(10.0);
                    assertEquals(3.0, view.viewport().centerX(), TOLERANCE);
                    assertEquals(4.0, view.viewport().centerY(), TOLERANCE);
                    assertEquals(1.0e-9, view.viewport().worldUnitsPerPixel(), 1.0e-18);

                    view.setLayers(
                            List.of(
                                    layer(
                                            feature(
                                                    "line",
                                                    new LineStringGeometry(
                                                            CoordinateSequence.of(
                                                                    -10.0, -5.0, 10.0, 5.0)),
                                                    FeatureStyle.line(
                                                            Rgba.rgb(20, 40, 60), 2.0)))));
                    view.fitToData(10.0);
                    assertEquals(0.0, view.viewport().centerX(), TOLERANCE);
                    assertEquals(0.0, view.viewport().centerY(), TOLERANCE);
                    assertEquals(0.125, view.viewport().worldUnitsPerPixel(), TOLERANCE);
                });
    }

    private static BufferedImage render(Feature feature) {
        MapView view = configuredView(feature);
        BufferedImage image =
                new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        paint(view, image);
        return image;
    }

    private static BufferedImage renderAtScale(Feature feature, double worldUnitsPerPixel) {
        MapView view = configuredView(feature);
        view.setViewport(new MapViewport(IMAGE_SIZE, IMAGE_SIZE, 0.0, 0.0, worldUnitsPerPixel));
        BufferedImage image =
                new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        paint(view, image);
        return image;
    }

    private static Feature vectorSquare(MarkerPlacement placement, SymbolStroke stroke) {
        return feature(
                "placed-square",
                new PointGeometry(new Coordinate(0.0, 0.0)),
                vectorSquareSymbol(placement, stroke, 1.0));
    }

    private static VectorMarkerSymbol vectorSquareSymbol(
            MarkerPlacement placement, SymbolStroke stroke, double opacity) {
        return VectorMarkerSymbol.of(
                BuiltInMarkers.path(BuiltInMarker.SQUARE),
                BuiltInMarkers.viewBox(),
                Rgba.rgb(35, 105, 205),
                Optional.ofNullable(stroke),
                placement,
                opacity);
    }

    private static BufferedImage renderMarkerWithBasis(
            VectorMarkerSymbol symbol, MapScreenBasis basis) {
        MapView view = new MapView(IDENTITY);
        BufferedImage image =
                new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
            graphics.setRenderingHint(
                    java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            view.renderMarkerSymbol(graphics, symbol, new Coordinate(50.0, 50.0), basis);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static Symbol squareSymbol(Rgba fill, double size, double opacity) {
        return BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, fill, size, opacity);
    }

    private static int paintedWidth(BufferedImage image) {
        int[] bounds = paintedBounds(image);
        return bounds[2] - bounds[0] + 1;
    }

    private static MapView configuredView(Feature feature) {
        MapView view = new MapView(IDENTITY);
        view.setSize(IMAGE_SIZE, IMAGE_SIZE);
        view.setViewport(new MapViewport(IMAGE_SIZE, IMAGE_SIZE, 0.0, 0.0, 1.0));
        view.setLayers(List.of(layer(feature)));
        return view;
    }

    private static void paint(MapView view, BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static InMemoryLayer layer(Feature feature) {
        return new InMemoryLayer("layer", "Layer", List.of(feature));
    }

    private static Feature feature(
            String id, io.github.mundanej.map.api.Geometry geometry, FeatureStyle style) {
        return new Feature(id, "", geometry, Map.of(), style);
    }

    private static Feature feature(
            String id, io.github.mundanej.map.api.Geometry geometry, Symbol symbol) {
        return new Feature(id, "", geometry, Map.of(), symbol);
    }

    private static void dispatchMouse(
            MapView view, int id, int x, int y, int button, int modifiers) {
        view.dispatchEvent(
                new MouseEvent(
                        view, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    private static void assertColorNear(Rgba expected, int actualArgb, int tolerance) {
        Color actual = new Color(actualArgb, true);
        assertTrue(Math.abs(expected.red() - actual.getRed()) <= tolerance, actual::toString);
        assertTrue(Math.abs(expected.green() - actual.getGreen()) <= tolerance, actual::toString);
        assertTrue(Math.abs(expected.blue() - actual.getBlue()) <= tolerance, actual::toString);
        assertTrue(Math.abs(expected.alpha() - actual.getAlpha()) <= tolerance, actual::toString);
    }

    private static void assertRegionContainsColor(
            BufferedImage image,
            int minimumX,
            int minimumY,
            int maximumX,
            int maximumY,
            Rgba expected,
            int tolerance) {
        for (int y = minimumY; y <= maximumY; y++) {
            for (int x = minimumX; x <= maximumX; x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                if (Math.abs(expected.red() - actual.getRed()) <= tolerance
                        && Math.abs(expected.green() - actual.getGreen()) <= tolerance
                        && Math.abs(expected.blue() - actual.getBlue()) <= tolerance
                        && Math.abs(expected.alpha() - actual.getAlpha()) <= tolerance) {
                    return;
                }
            }
        }
        throw new AssertionError("Expected color was absent from rendering assertion region");
    }

    private static int[] paintedBounds(BufferedImage image) {
        int minimumX = image.getWidth();
        int minimumY = image.getHeight();
        int maximumX = -1;
        int maximumY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() < 250 || color.getGreen() < 250 || color.getBlue() < 250) {
                    minimumX = Math.min(minimumX, x);
                    minimumY = Math.min(minimumY, y);
                    maximumX = Math.max(maximumX, x);
                    maximumY = Math.max(maximumY, y);
                }
            }
        }
        return new int[] {minimumX, minimumY, maximumX, maximumY};
    }

    private static void assertShapeProbe(BuiltInMarker marker, BufferedImage image, Rgba fill) {
        switch (marker) {
            case CIRCLE, DIAMOND, TRIANGLE, STAR ->
                    assertColorNear(Rgba.rgb(255, 255, 255), image.getRGB(40, 40), 2);
            case SQUARE -> assertColorNear(fill, image.getRGB(40, 40), 5);
            case CROSS -> {
                assertColorNear(fill, image.getRGB(50, 40), 5);
                assertColorNear(Rgba.rgb(255, 255, 255), image.getRGB(40, 40), 2);
            }
            case X -> {
                assertColorNear(fill, image.getRGB(43, 43), 12);
                assertColorNear(Rgba.rgb(255, 255, 255), image.getRGB(50, 40), 2);
            }
            case ARROW -> {
                assertColorNear(fill, image.getRGB(59, 50), 12);
                assertColorNear(Rgba.rgb(255, 255, 255), image.getRGB(42, 42), 2);
            }
        }
    }

    private static final class EqualListener implements MapPointerListener {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void onMapPointerEvent(MapPointerEvent event) {
            calls.incrementAndGet();
        }

        int count() {
            return calls.get();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualListener;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private record TestMarker(SymbolRendererKey rendererKey) implements MarkerSymbol {
        @Override
        public double opacity() {
            return 1.0;
        }
    }

    private static final class MutableMarker implements MarkerSymbol {
        private io.github.mundanej.map.api.SymbolRole role =
                io.github.mundanej.map.api.SymbolRole.MARKER;
        private SymbolRendererKey rendererKey = new SymbolRendererKey("example.mutable");

        @Override
        public io.github.mundanej.map.api.SymbolRole role() {
            return role;
        }

        @Override
        public SymbolRendererKey rendererKey() {
            return rendererKey;
        }

        @Override
        public double opacity() {
            return 1.0;
        }
    }
}
