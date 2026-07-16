package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/** Native Image entrypoint that exercises the complete Level 1 compatibility path. */
public final class NativeSmokeMain {
    static final int IMAGE_WIDTH = 256;
    static final int IMAGE_HEIGHT = 128;
    static final double[] PROJECTED_X = {-64_000.0, 0.0, 64_000.0};
    static final String ICON_RESOURCE = "/io/github/mundanej/map/nativeimage/symbol-smoke-4x2.rgba";

    private NativeSmokeMain() {}

    /**
     * Runs the smoke application and prints success only after every assertion passes.
     *
     * @param arguments ignored command-line arguments
     */
    public static void main(String[] arguments) {
        System.setProperty("java.awt.headless", "true");
        runSmoke();
        System.out.println("mundane-map native smoke: OK");
    }

    /** Runs the exact aggregate Level 1 scenario used by the native executable. */
    public static void runSmoke() {
        runScenario(NativeSymbolSmokeScenario.standard(loadRasterPixels()));
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openShapefile()) {
            NativeShapefileSmokeScenario.run(workspace.shapefilePaths());
        }
        try (NativeFixtureWorkspace workspace = NativeFixtureWorkspace.openRaster()) {
            NativeRasterSmokeScenario.run(workspace.rasterPaths());
        }
        NativeLevel1SmokeScenario.run();
    }

    static void runScenario(NativeSymbolSmokeScenario scenario) {
        if (SwingUtilities.isEventDispatchThread()) {
            renderAndAssert(scenario);
            return;
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        SwingUtilities.invokeLater(
                () -> {
                    try {
                        renderAndAssert(scenario);
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                    } finally {
                        completed.countDown();
                    }
                });
        try {
            completed.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running native smoke", exception);
        }
        rethrow(failure.get());
    }

    static int[] loadRasterPixels() {
        try (InputStream input = NativeSmokeMain.class.getResourceAsStream(ICON_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("raster-resource: declared icon is missing");
            }
            return decodeRasterPixels(input.readNBytes(33));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "raster-resource: unable to read declared icon", exception);
        }
    }

    static int[] decodeRasterPixels(byte[] bytes) {
        if (bytes.length != 32) {
            throw new IllegalStateException("raster-resource: expected exactly 32 bytes");
        }
        int[] pixels = new int[8];
        for (int index = 0; index < pixels.length; index++) {
            int offset = index * 4;
            pixels[index] =
                    Byte.toUnsignedInt(bytes[offset]) << 24
                            | Byte.toUnsignedInt(bytes[offset + 1]) << 16
                            | Byte.toUnsignedInt(bytes[offset + 2]) << 8
                            | Byte.toUnsignedInt(bytes[offset + 3]);
        }
        return pixels;
    }

    private static void renderAndAssert(NativeSymbolSmokeScenario scenario) {
        NamedSymbolCatalog catalog = scenario.catalog();
        assertMissingDiagnostic(catalog);

        WebMercatorProjection projection = new WebMercatorProjection();
        List<Feature> features =
                List.of(
                        feature("vector", catalog.require("vector"), projection, PROJECTED_X[0]),
                        feature(
                                "composite",
                                catalog.require("composite"),
                                projection,
                                PROJECTED_X[1]),
                        feature("raster", catalog.require("raster"), projection, PROJECTED_X[2]));
        MapView view =
                new MapView(projection, SymbolRendererRegistry.builderWithBuiltIns().build());
        view.setDoubleBuffered(false);
        view.setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        view.setLayers(List.of(new InMemoryLayer("symbols", "Symbols", features)));
        view.fitToData(64.0);

        Coordinate[] anchors = new Coordinate[features.size()];
        for (int index = 0; index < features.size(); index++) {
            PointGeometry point = (PointGeometry) features.get(index).geometry();
            anchors[index] = view.viewport().worldToScreen(projection.project(point.coordinate()));
        }

        BufferedImage image =
                new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        RepaintManager repaintManager = RepaintManager.currentManager(view);
        boolean doubleBufferingEnabled = repaintManager.isDoubleBufferingEnabled();
        repaintManager.setDoubleBufferingEnabled(false);
        try {
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Src);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                Graphics2D paintGraphics = (Graphics2D) graphics.create();
                try {
                    paintGraphics.setComposite(AlphaComposite.SrcOver);
                    view.paint(paintGraphics);
                } finally {
                    paintGraphics.dispose();
                }
            } finally {
                graphics.dispose();
            }
        } finally {
            repaintManager.setDoubleBufferingEnabled(doubleBufferingEnabled);
        }
        NativeSymbolSmokeAssertions.verify(scenario, image, anchors, view.viewport());
    }

    private static Feature feature(
            String name, Symbol symbol, WebMercatorProjection projection, double projectedX) {
        Coordinate source = projection.unproject(new Coordinate(projectedX, 0.0));
        return new Feature(name, "", new PointGeometry(source), Map.of(), symbol);
    }

    private static void assertMissingDiagnostic(NamedSymbolCatalog catalog) {
        try {
            catalog.require("absent");
            throw new IllegalStateException("catalog-diagnostic: missing name was accepted");
        } catch (SymbolException exception) {
            if (!SymbolException.CATALOG_MISSING.equals(exception.code())
                    || !Map.of("name", "absent").equals(exception.context())) {
                throw new IllegalStateException(
                        "catalog-diagnostic: unstable missing-name diagnostic", exception);
            }
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Native smoke failed", failure);
    }
}
