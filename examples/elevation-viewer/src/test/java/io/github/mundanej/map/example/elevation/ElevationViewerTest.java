package io.github.mundanej.map.example.elevation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.awt.MapView;
import java.awt.EventQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import org.junit.jupiter.api.Test;

class ElevationViewerTest {
    @Test
    void constructsControlsAndTransfersOwnedSyntheticSource() throws Exception {
        ElevationSource source = ElevationViewer.createSource();
        assertEquals("synthetic-elevation", source.metadata().identity().id());
        AtomicReference<MapView> view = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    MapView created = ElevationViewer.createView(source);
                    JLabel status = new JLabel();
                    JPanel controls = ElevationViewer.createControls(created, status);
                    JCheckBox hillshade = (JCheckBox) controls.getComponent(0);
                    @SuppressWarnings("unchecked")
                    JComboBox<RasterInterpolation> interpolation =
                            (JComboBox<RasterInterpolation>) controls.getComponent(2);
                    JSlider opacity = (JSlider) controls.getComponent(4);
                    hillshade.doClick();
                    interpolation.setSelectedItem(RasterInterpolation.BILINEAR);
                    opacity.setValue(45);
                    assertTrue(status.getText().contains(ElevationViewer.CAPABILITY_LABEL));
                    assertTrue(status.getText().contains("BILINEAR"));
                    assertTrue(status.getText().contains("45%"));
                    assertEquals(1, created.layerBindings().size());
                    view.set(created);
                });
        assertFalse(source.isClosed());
        EventQueue.invokeAndWait(view.get()::close);
        assertTrue(source.isClosed());
    }

    @Test
    void enforcesThreadBoundaries() throws Exception {
        ElevationSource source = ElevationViewer.createSource();
        assertThrows(IllegalStateException.class, () -> ElevationViewer.createView(source));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    try {
                        ElevationViewer.createSource();
                    } catch (Throwable caught) {
                        failure.set(caught);
                    }
                });
        assertTrue(failure.get() instanceof IllegalStateException);
        source.close();
    }
}
