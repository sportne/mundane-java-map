package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CreateFeature;
import io.github.mundanej.map.api.FeatureEditConfigurationException;
import io.github.mundanej.map.api.FeatureEditTransaction;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.ReplaceFeature;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewEditableFeatureTest {
    @Test
    void editableBindingPaintsHitsFitsAndReconcilesInteraction() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("a", 0, 0)));
                    MapLayerBinding binding = binding("edit", session);
                    MapView view = TestMapViews.identity();
                    view.setSize(100, 100);
                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    view.setLayerBindings(List.of(binding));
                    assertTrue(binding.isObservingEditSession());
                    paint(view);
                    assertEquals("a", view.hitTest(50, 50, 0).topmost().orElseThrow().featureId());
                    move(view, 50, 50);
                    assertEquals("a", view.hover().orElseThrow().featureId());
                    view.setSelection(new FeatureSelection("edit", "a"));

                    List<String> events = new ArrayList<>();
                    view.addMapSelectionListener(event -> events.add("selection"));
                    view.addMapHoverListener(event -> events.add("hover"));

                    session.apply(
                            new FeatureEditTransaction(
                                    0,
                                    "move",
                                    List.of(new ReplaceFeature("a", record("a", 10, 0)))));
                    assertEquals("a", view.selection().orElseThrow().featureId());
                    assertTrue(view.hover().isEmpty());
                    assertEquals(List.of("hover"), events);
                    paint(view);
                    assertEquals("a", view.hitTest(60, 50, 0).topmost().orElseThrow().featureId());

                    session.apply(
                            new FeatureEditTransaction(
                                    1, "create", List.of(new CreateFeature(record("b", 20, 10)))));
                    assertEquals("a", view.selection().orElseThrow().featureId());
                    assertTrue(view.hover().isEmpty());
                    assertTrue(view.hitTest(70, 40, 0).topmost().isPresent());
                    view.fitToData(10);
                    assertTrue(view.viewport().worldUnitsPerPixel() > 0);

                    view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                    paint(view);
                    move(view, 60, 50);
                    assertEquals("a", view.hover().orElseThrow().featureId());
                    events.clear();

                    session.apply(
                            new FeatureEditTransaction(
                                    2,
                                    "delete",
                                    List.of(new io.github.mundanej.map.api.DeleteFeature("a"))));
                    assertTrue(view.selection().isEmpty());
                    assertTrue(view.hover().isEmpty());
                    assertEquals(List.of("selection", "hover"), events);
                    view.setLayerBindings(List.of());
                    assertFalse(binding.isObservingEditSession());
                    assertFalse(binding.isClosed());
                    events.clear();
                    session.apply(
                            new FeatureEditTransaction(
                                    3, "detached", List.of(new CreateFeature(record("c", 0, 0)))));
                    assertTrue(events.isEmpty());
                    assertEquals(4, session.snapshot().revision());
                    view.close();
                });
    }

    @Test
    void attachmentRequiresOwnerThreadExactCrsAndUniqueSession() throws Exception {
        FeatureEditSession sameThreadOffEdt =
                FeatureEditSession.open(
                        CrsDefinitions.EPSG_3857, List.of(record("same-thread", 0, 0)));
        MapView offEdtView = TestMapViews.identity();
        assertThrows(
                IllegalStateException.class,
                () ->
                        offEdtView.setLayerBindings(
                                List.of(binding("same-thread", sameThreadOffEdt))));
        offEdtView.close();

        FeatureEditSession wrongThread =
                FeatureEditSession.open(CrsDefinitions.EPSG_3857, List.of(record("a", 0, 0)));
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = TestMapViews.identity();
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    view.setLayerBindings(
                                            List.of(binding("wrong-thread", wrongThread))));
                    view.close();
                });

        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession wrongCrs =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_4326, List.of(record("a", 0, 0)));
                    MapView view = TestMapViews.identity();
                    FeatureEditConfigurationException mismatch =
                            assertThrows(
                                    FeatureEditConfigurationException.class,
                                    () ->
                                            view.setLayerBindings(
                                                    List.of(binding("wrong-crs", wrongCrs))));
                    assertEquals("EDIT_CRS_MISMATCH", mismatch.problem().code());

                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857, List.of(record("a", 0, 0)));
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    view.setLayerBindings(
                                            List.of(
                                                    binding("one", session),
                                                    binding("two", session))));
                    view.close();
                });
    }

    private static void paint(MapView view) {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static void move(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseEvent(
                        view,
                        MouseEvent.MOUSE_MOVED,
                        System.currentTimeMillis(),
                        0,
                        x,
                        y,
                        0,
                        false,
                        MouseEvent.NOBUTTON));
    }

    private static MapLayerBinding binding(String id, FeatureEditSession session) {
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, Rgba.rgb(20, 90, 180), 10, 1);
        var line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(20, 90, 180),
                                new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1);
        var fill = SolidFillSymbol.of(Rgba.rgb(20, 90, 180), 1);
        return MapLayerBinding.editableFeature(id, id, session, marker, line, fill);
    }

    private static FeatureRecord record(String id, double x, double y) {
        return new FeatureRecord(id, id, new PointGeometry(new Coordinate(x, y)), Map.of());
    }
}
