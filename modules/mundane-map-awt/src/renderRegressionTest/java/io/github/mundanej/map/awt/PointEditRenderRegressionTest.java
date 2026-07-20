package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.PointFeatureDraft;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PointEditRenderRegressionTest {
    @Test
    void transientPreviewHasBoundedPortableColorEvidenceAndDoesNotCommit() throws Exception {
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        AtomicReference<BufferedImage> snappedImage = new AtomicReference<>();
        AtomicReference<Long> revision = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
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
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857);
                    view.setSize(200, 120);
                    view.setViewport(new MapViewport(200, 120, 0, 0, 1));
                    var line =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(30, 90, 190),
                                            new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                                    1);
                    MapLayerBinding binding =
                            MapLayerBinding.editableFeature(
                                    "editable",
                                    "Editable",
                                    session,
                                    BuiltInMarkers.filledScreen(
                                            BuiltInMarker.SQUARE, Rgba.rgb(30, 90, 190), 12, 1),
                                    line,
                                    SolidFillSymbol.of(Rgba.rgb(30, 90, 190), 1));
                    view.setLayerBindings(List.of(binding));
                    PointEditController controller = new PointEditController(view, binding);
                    view.setActiveTool(controller);
                    view.setSelection(new FeatureSelection("editable", "seed"));
                    controller.create(new PointFeatureDraft("pending", "Pending", Map.of()));
                    move(view, 100, 60);
                    snappedImage.set(paint(view));
                    move(view, 145, 60);
                    image.set(paint(view));
                    revision.set(session.snapshot().revision());
                    view.close();
                });

        assertEquals(0L, revision.get());
        assertColorWithin(snappedImage.get(), 100, 60, 10, new Color(35, 155, 75), 18);
        assertColorWithin(snappedImage.get(), 100, 60, 10, new Color(0, 102, 204), 18);
        assertColorWithin(snappedImage.get(), 107, 60, 1, new Color(0, 102, 204), 30);
        assertColorWithin(image.get(), 145, 60, 10, new Color(225, 125, 25), 18);
    }

    private static void move(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseEvent(
                        view, MouseEvent.MOUSE_MOVED, 1L, 0, x, y, 0, false, MouseEvent.NOBUTTON));
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage rendered = new BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = rendered.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rendered.getWidth(), rendered.getHeight());
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return rendered;
    }

    private static void assertColorWithin(
            BufferedImage image,
            int centerX,
            int centerY,
            int radius,
            Color expected,
            int tolerance) {
        for (int y = centerY - radius; y <= centerY + radius; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                if (Math.abs(actual.getRed() - expected.getRed()) <= tolerance
                        && Math.abs(actual.getGreen() - expected.getGreen()) <= tolerance
                        && Math.abs(actual.getBlue() - expected.getBlue()) <= tolerance) {
                    return;
                }
            }
        }
        assertTrue(false, "expected edit-preview color was absent from its bounded region");
    }
}
