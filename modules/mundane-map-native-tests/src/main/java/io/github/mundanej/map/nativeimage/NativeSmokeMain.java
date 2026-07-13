package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Native Image entrypoint that exercises the real AWT offscreen rendering path. */
public final class NativeSmokeMain {
    private static final int EXPECTED_RED = 20;
    private static final int EXPECTED_GREEN = 90;
    private static final int EXPECTED_BLUE = 180;
    private static final int COLOR_TOLERANCE = 8;

    private NativeSmokeMain() {}

    /** Runs the smoke application. */
    public static void main(String[] arguments) {
        System.setProperty("java.awt.headless", "true");
        runSmoke();
        System.out.println("mundane-map native smoke: OK");
    }

    /** Renders one feature on the Swing event-dispatch thread. */
    public static void runSmoke() {
        if (SwingUtilities.isEventDispatchThread()) {
            renderSmoke();
            return;
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        SwingUtilities.invokeLater(
                () -> {
                    try {
                        renderSmoke();
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

    private static void renderSmoke() {
        Feature feature =
                new Feature(
                        "origin",
                        "",
                        new PointGeometry(new Coordinate(0.0, 0.0)),
                        Map.of(),
                        FeatureStyle.point(Rgba.rgb(20, 90, 180), 16.0));
        MapView view = new MapView(new WebMercatorProjection());
        view.setSize(128, 128);
        view.setLayers(List.of(new InMemoryLayer("smoke", "Smoke", List.of(feature))));
        view.fitToData(16.0);

        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        verifyRendered(image);
    }

    static void verifyRendered(BufferedImage image) {
        for (int y = 60; y <= 68; y++) {
            for (int x = 60; x <= 68; x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                if (actual.getAlpha() >= 255 - COLOR_TOLERANCE
                        && Math.abs(actual.getRed() - EXPECTED_RED) <= COLOR_TOLERANCE
                        && Math.abs(actual.getGreen() - EXPECTED_GREEN) <= COLOR_TOLERANCE
                        && Math.abs(actual.getBlue() - EXPECTED_BLUE) <= COLOR_TOLERANCE) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Map smoke render did not produce the expected marker");
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
