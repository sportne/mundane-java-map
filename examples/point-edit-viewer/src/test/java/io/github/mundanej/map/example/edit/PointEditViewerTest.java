package io.github.mundanej.map.example.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.SnapQueryStatus;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PointEditViewerTest {
    @Test
    void buildsAndPaintsTheProgrammaticEditSequence() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    try {
                        PointEditViewer.ViewerState state = PointEditViewer.createState();
                        state.view().setSize(900, 600);
                        BufferedImage image =
                                new BufferedImage(900, 600, BufferedImage.TYPE_INT_ARGB);
                        paint(state, image);
                        assertEquals(0, state.session().snapshot().revision());
                        assertEquals("alpha", state.session().snapshot().records().getFirst().id());
                        assertFalse(state.view().hitTest(250, 300, 2).hits().isEmpty());

                        assertTrue(state.applyNext());
                        paint(state, image);
                        assertEquals(1, state.session().snapshot().revision());
                        assertFalse(state.view().hitTest(650, 300, 2).hits().isEmpty());

                        assertTrue(state.applyNext());
                        paint(state, image);
                        assertEquals(2, state.session().snapshot().revision());
                        assertFalse(state.view().hitTest(350, 200, 2).hits().isEmpty());

                        assertTrue(state.applyNext());
                        paint(state, image);
                        assertEquals(3, state.session().snapshot().revision());
                        assertEquals("alpha", state.session().snapshot().records().getFirst().id());
                        assertEquals(false, state.applyNext());

                        assertEquals(
                                "delete beta", state.session().undoDescription().orElseThrow());
                        assertEquals(4, state.undo(3).snapshot().revision());
                        assertEquals(2, state.session().snapshot().records().size());
                        assertEquals(
                                "delete beta", state.session().redoDescription().orElseThrow());
                        assertEquals(
                                "EDIT_REVISION_CONFLICT",
                                state.undo(3).problem().orElseThrow().code());
                        assertEquals(5, state.redo(4).snapshot().revision());
                        assertEquals(
                                "EDIT_NOTHING_TO_REDO",
                                state.redo(5).problem().orElseThrow().code());

                        PointEditViewer.PreviewScenario preview = state.previewScenario();
                        assertEquals(SnapQueryStatus.SNAPPED, preview.snapped().status());
                        assertEquals(SnapQueryStatus.UNSNAPPED, preview.unsnapped().status());
                        assertEquals(
                                -250_000,
                                preview.snapped().result().orElseThrow().coordinate().y());
                        JLayer<io.github.mundanej.map.awt.MapView> previewLayer =
                                PointEditViewer.createPreviewLayer(state);
                        previewLayer.setSize(900, 600);
                        previewLayer.doLayout();
                        paint(previewLayer, image);
                        assertColorWithin(image, 450, 350, 7, new Color(30, 160, 75), 12);
                        assertColorWithin(image, 700, 100, 7, new Color(220, 115, 20), 12);
                        state.view().close();

                        PointEditViewer.ViewerState controlled = PointEditViewer.createState();
                        controlled.applyNext();
                        JLabel status = new JLabel();
                        PointEditViewer.HistoryControls controls =
                                PointEditViewer.createHistoryControls(controlled, status);
                        controls.refresh().run();
                        assertEquals("Undo create beta", controls.undo().getText());
                        assertFalse(controls.undo().isEnabled());
                        controls.undo().doClick();
                        assertEquals(1, controlled.session().snapshot().revision());
                        assertTrue(controlled.applyNext());
                        assertTrue(controlled.applyNext());
                        controls.refresh().run();
                        assertTrue(controlled.scriptComplete());
                        assertTrue(controls.undo().isEnabled());
                        controls.undo().doClick();
                        assertTrue(status.getText().contains("revision 4"));
                        assertEquals("Redo delete beta", controls.redo().getText());
                        controls.redo().doClick();
                        assertTrue(status.getText().contains("revision 5"));
                        controlled.view().close();
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                    }
                });
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static void paint(PointEditViewer.ViewerState state, BufferedImage image) {
        paint(state.view(), image);
    }

    private static void paint(javax.swing.JComponent component, BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    private static void assertColorWithin(
            BufferedImage image,
            int centerX,
            int centerY,
            int radius,
            Color expected,
            int tolerance) {
        boolean found = false;
        for (int y = centerY - radius; y <= centerY + radius && !found; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                if (Math.abs(actual.getRed() - expected.getRed()) <= tolerance
                        && Math.abs(actual.getGreen() - expected.getGreen()) <= tolerance
                        && Math.abs(actual.getBlue() - expected.getBlue()) <= tolerance) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "expected preview color was absent from its bounded region");
    }
}
