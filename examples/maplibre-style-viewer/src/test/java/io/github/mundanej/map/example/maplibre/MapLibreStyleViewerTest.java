package io.github.mundanej.map.example.maplibre;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.example.maplibre.MapLibreStyleViewer.GallerySession;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapLibreStyleViewerTest {
    @Test
    void galleryBuildsAndPaintsOnTheEventDispatchThread() throws Exception {
        AtomicBoolean ranOnEdt = new AtomicBoolean();
        SwingUtilities.invokeAndWait(
                () -> {
                    ranOnEdt.set(SwingUtilities.isEventDispatchThread());
                    GallerySession session = MapLibreStyleViewer.createSession();
                    MapView view = session.view();
                    try (session) {
                        assertEquals(11, view.layerBindings().size());
                        BufferedImage image =
                                new BufferedImage(800, 500, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            assertDoesNotThrow(() -> view.paint(graphics));
                        } finally {
                            graphics.dispose();
                        }
                        assertFalse(view.hitTest(400, 250, 1).hits().isEmpty());
                    }
                    assertThrows(IllegalStateException.class, session::view);
                });
        assertTrue(ranOnEdt.get());
    }
}
