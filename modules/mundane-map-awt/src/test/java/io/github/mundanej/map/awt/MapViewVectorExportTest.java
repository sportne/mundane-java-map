package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorExportSnapshotException;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.Color;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewVectorExportTest {
    @Test
    void capturesDetachedAuthoritativeScreenGeometryAndPlacedLabelsOnTheEdt() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    var marker =
                            BuiltInMarkers.filledScreen(
                                    BuiltInMarker.DIAMOND, Rgba.rgb(20, 80, 160), 14, 0.8);
                    Feature feature =
                            new Feature(
                                    "point",
                                    "Point label",
                                    new PointGeometry(new Coordinate(0, 0)),
                                    Map.of(),
                                    marker);
                    MapView view = new MapView(new WebMercatorProjection());
                    view.setSize(240, 160);
                    view.setBackground(new Color(240, 241, 242));
                    view.setLayers(List.of(new InMemoryLayer("layer", "Layer", List.of(feature))));
                    view.fitToData(20);

                    VectorExportSnapshot snapshot = view.captureVectorExportSnapshot();

                    assertEquals(240, snapshot.widthPixels());
                    assertEquals(160, snapshot.heightPixels());
                    assertEquals(Rgba.rgb(240, 241, 242), snapshot.background());
                    assertEquals(1, snapshot.layerCount());
                    assertEquals(1, snapshot.primitives().size());
                    PointGeometry screen =
                            assertInstanceOf(
                                    PointGeometry.class,
                                    snapshot.primitives().getFirst().screenGeometry());
                    assertEquals(120.0, screen.coordinate().x(), 1.0e-9);
                    assertEquals(80.0, screen.coordinate().y(), 1.0e-9);
                    assertEquals("Point label", snapshot.labels().getFirst().text());
                    assertTrue(snapshot.viewFrame().screenPixelsPerMapUnit() > 0);
                    assertDetached(snapshot);
                });
    }

    @Test
    void rejectsOffEdtCancellationAndNonOpaqueComponentsDeterministically() throws Exception {
        MapView offEdt = new MapView(new WebMercatorProjection());
        offEdt.setSize(20, 20);
        assertThrows(IllegalStateException.class, offEdt::captureVectorExportSnapshot);

        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = new MapView(new WebMercatorProjection());
                    view.setSize(20, 20);
                    VectorExportSnapshotException cancelled =
                            assertThrows(
                                    VectorExportSnapshotException.class,
                                    () ->
                                            view.captureVectorExportSnapshot(
                                                    io.github.mundanej.map.api
                                                            .VectorExportSnapshotLimits.defaults(),
                                                    () -> true));
                    assertEquals("VECTOR_EXPORT_SNAPSHOT_CANCELLED", cancelled.problem().code());

                    view.setOpaque(false);
                    VectorExportSnapshotException background =
                            assertThrows(
                                    VectorExportSnapshotException.class,
                                    view::captureVectorExportSnapshot);
                    assertEquals(
                            "componentBackground", background.problem().context().get("field"));
                });
    }

    @Test
    void capturesTheCurrentEditableSnapshotWithoutRetainingTheSession() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureEditSession session =
                            FeatureEditSession.open(
                                    CrsDefinitions.EPSG_3857,
                                    List.of(
                                            new FeatureRecord(
                                                    "editable",
                                                    "Editable point",
                                                    new PointGeometry(new Coordinate(5, 6)),
                                                    Map.of())));
                    var marker =
                            BuiltInMarkers.filledScreen(
                                    BuiltInMarker.CIRCLE, Rgba.rgb(10, 90, 170), 10, 1);
                    MapLayerBinding binding =
                            MapLayerBinding.editableFeature(
                                    "edit",
                                    "Edit",
                                    session,
                                    marker,
                                    SolidLineSymbol.of(
                                            new SymbolStroke(
                                                    Rgba.rgb(10, 90, 170),
                                                    new SymbolLength(1, SymbolUnit.SCREEN_PIXEL)),
                                            1),
                                    SolidFillSymbol.of(Rgba.rgb(10, 90, 170), 1));
                    MapView view = TestMapViews.identity();
                    view.setSize(300, 200);
                    view.setLayerBindings(List.of(binding));

                    VectorExportSnapshot snapshot = view.captureVectorExportSnapshot();

                    assertEquals(1, snapshot.primitives().size());
                    assertTrue(
                            snapshot.labels().stream()
                                    .anyMatch(label -> label.text().equals("Editable point")));
                    assertDetached(snapshot);
                });
    }

    @Test
    void rejectsUnsupportedSymbolBeforeAttemptingInvalidLabelLayout() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Feature feature =
                            new Feature(
                                    "unsupported",
                                    "bad\nlabel",
                                    new PointGeometry(new Coordinate(0, 0)),
                                    Map.of(),
                                    RasterIconSymbol.nativeScreenSize(
                                            1,
                                            1,
                                            new int[] {0xff000000},
                                            RasterInterpolation.NEAREST,
                                            1));
                    MapView view = new MapView(new WebMercatorProjection());
                    view.setSize(100, 100);
                    view.setLayers(
                            List.of(
                                    new InMemoryLayer(
                                            "unsupported", "Unsupported", List.of(feature))));

                    VectorExportSnapshotException failure =
                            assertThrows(
                                    VectorExportSnapshotException.class,
                                    view::captureVectorExportSnapshot);

                    assertEquals("VECTOR_EXPORT_SYMBOL_UNSUPPORTED", failure.problem().code());
                });
    }

    private static void assertDetached(VectorExportSnapshot snapshot) {
        for (Field field : snapshot.getClass().getDeclaredFields()) {
            String type = field.getType().getName();
            assertTrue(!type.startsWith("java.awt."));
            assertTrue(!type.startsWith("javax.swing."));
            assertTrue(!type.contains("MapView"));
            assertTrue(!type.contains("FeatureSource"));
        }
    }
}
