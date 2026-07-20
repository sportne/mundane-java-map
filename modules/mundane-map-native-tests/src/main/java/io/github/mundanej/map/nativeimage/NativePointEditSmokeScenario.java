package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.PointFeatureDraft;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.PointEditController;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.MapViewport;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

/** Resource-free edit/session path shared by JVM and Native Image smoke execution. */
final class NativePointEditSmokeScenario {
    private NativePointEditSmokeScenario() {}

    static void run() {
        FeatureEditSession session =
                FeatureEditSession.open(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new FeatureRecord(
                                        "seed",
                                        "Seed",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of())));
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setSize(100, 100);
        view.setViewport(new MapViewport(100, 100, 0, 0, 1));
        MapLayerBinding binding =
                MapLayerBinding.editableFeature(
                        "editable",
                        "Editable",
                        session,
                        BuiltInMarkers.filledScreen(
                                BuiltInMarker.DIAMOND, Rgba.rgb(30, 100, 200), 10, 1),
                        SolidLineSymbol.of(
                                new SymbolStroke(
                                        Rgba.rgb(30, 100, 200),
                                        new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                                1),
                        SolidFillSymbol.of(Rgba.rgb(30, 100, 200), 1));
        view.setLayerBindings(List.of(binding));
        PointEditController controller = new PointEditController(view, binding);
        view.setActiveTool(controller);
        controller.create(new PointFeatureDraft("created", "Created", Map.of("native", true)));
        click(view, 70, 50);
        assertState(session.snapshot().revision() == 1, "create revision");
        assertState(
                view.selection().orElseThrow().featureId().equals("created"), "create selection");
        controller.deleteSelected();
        assertState(session.snapshot().records().size() == 1, "delete");
        controller.undo();
        assertState(session.snapshot().records().size() == 2, "undo");
        controller.redo();
        assertState(session.snapshot().records().size() == 1, "redo");
        view.close();
    }

    private static void click(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseEvent(
                        view, MouseEvent.MOUSE_CLICKED, 1L, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private static void assertState(boolean condition, String stage) {
        if (!condition) {
            throw new IllegalStateException("point-edit smoke failed: " + stage);
        }
    }
}
