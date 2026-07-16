package io.github.mundanej.map.example.elevation;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationHillshade;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.RasterRenderOptions;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.PackedElevationGrid;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.WindowConstants;

/** Runnable synthetic elevation demonstration; it performs no DTED input. */
public final class ElevationViewer {
    /** Explicit capability label shown by the example. */
    public static final String CAPABILITY_LABEL = "synthetic elevation — no DTED input";

    private ElevationViewer() {}

    /** Creates the analytic source off EDT and schedules the Swing window. */
    public static void main(String[] arguments) {
        ElevationSource source = createSource();
        EventQueue.invokeLater(() -> show(source));
    }

    /** Creates one deterministic analytic EPSG:4326 metre grid away from poles and antimeridian. */
    public static ElevationSource createSource() {
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Elevation source creation must run off the EDT");
        }
        int columns = 81;
        int rows = 61;
        double[] samples = new double[columns * rows];
        BitSet noData = new BitSet(samples.length);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                double x = (column - (columns - 1) / 2.0) / 10.0;
                double y = (row - (rows - 1) / 2.0) / 9.0;
                samples[row * columns + column] =
                        650.0
                                + 1_100.0 * StrictMath.exp(-(x * x + y * y))
                                + 180.0 * StrictMath.sin(column / 7.0);
                if (column >= 8 && column <= 15 && row >= 9 && row <= 18) {
                    noData.set(row * columns + column);
                }
            }
        }
        ElevationSourceMetadata metadata =
                new ElevationSourceMetadata(
                        new SourceIdentity("synthetic-elevation", "Synthetic elevation"),
                        columns,
                        rows,
                        new Envelope(-3, 40, 3, 44),
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_4326,
                                Optional.of("EPSG:4326"),
                                Optional.empty()),
                        ElevationUnit.METRE);
        return PackedElevationGrid.copyOf(metadata, samples, noData);
    }

    /** Creates a real owned elevation map view on the EDT. */
    public static MapView createView(ElevationSource source) {
        Objects.requireNonNull(source, "source");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Elevation view creation must run on the EDT");
        }
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_4326);
        view.setSize(800, 560);
        view.putClientProperty("elevation-capability", CAPABILITY_LABEL);
        MapLayerBinding binding = null;
        try {
            binding =
                    MapLayerBinding.ownedElevation(
                            "terrain", "Synthetic terrain", source, defaultStyle());
            view.setLayerBindings(List.of(binding));
            binding = null;
            view.fitToData(20);
            return view;
        } catch (RuntimeException | Error failure) {
            if (binding != null) {
                binding.close();
            } else {
                view.close();
            }
            throw failure;
        }
    }

    /** Creates hillshade, rendered-color interpolation, and opacity controls on the EDT. */
    public static JPanel createControls(MapView view, JLabel status) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(status, "status");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Elevation controls must be created on the EDT");
        }
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JCheckBox hillshade = new JCheckBox("Hillshade", true);
        JComboBox<RasterInterpolation> interpolation =
                new JComboBox<>(RasterInterpolation.values());
        JSlider opacity = new JSlider(0, 100, 100);
        Runnable update =
                () -> {
                    ElevationRasterStyle style = defaultStyle();
                    if (hillshade.isSelected()) {
                        style = style.withHillshade(ElevationHillshade.defaults());
                    }
                    view.setElevationRasterStyle("terrain", style);
                    RasterInterpolation selected =
                            Objects.requireNonNull(
                                    (RasterInterpolation) interpolation.getSelectedItem(),
                                    "interpolation");
                    view.setRasterRenderOptions(
                            "terrain",
                            new RasterRenderOptions(selected, opacity.getValue() / 100.0));
                    status.setText(
                            CAPABILITY_LABEL
                                    + " — "
                                    + selected
                                    + " — hillshade "
                                    + (hillshade.isSelected() ? "on" : "off")
                                    + " — opacity "
                                    + opacity.getValue()
                                    + "%");
                };
        hillshade.addActionListener(event -> update.run());
        interpolation.addActionListener(event -> update.run());
        opacity.addChangeListener(event -> update.run());
        controls.add(hillshade);
        controls.add(new JLabel("Interpolation"));
        controls.add(interpolation);
        controls.add(new JLabel("Opacity"));
        controls.add(opacity);
        update.run();
        return controls;
    }

    private static ElevationRasterStyle defaultStyle() {
        return ElevationRasterStyle.of(
                        new ElevationColorRamp(
                                ElevationUnit.METRE,
                                List.of(
                                        new ElevationColorStop(400, Rgba.rgb(35, 90, 55)),
                                        new ElevationColorStop(900, Rgba.rgb(120, 145, 70)),
                                        new ElevationColorStop(1_400, Rgba.rgb(165, 120, 70)),
                                        new ElevationColorStop(2_000, Rgba.rgb(245, 245, 240)))))
                .withNoDataColor(new Rgba(45, 70, 110, 180));
    }

    private static void show(ElevationSource source) {
        MapView view = createView(source);
        JFrame frame = new JFrame("mundane-java-map — synthetic elevation");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JLabel status = new JLabel();
        frame.add(createControls(view, status), BorderLayout.NORTH);
        frame.add(view, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        view.close();
                    }
                });
        frame.setSize(900, 650);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }
}
