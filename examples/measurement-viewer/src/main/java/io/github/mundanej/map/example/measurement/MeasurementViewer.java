package io.github.mundanej.map.example.measurement;

import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.MeasurementTool;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.DistanceStrategies;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.MapViewport;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Runnable planar-metre and EPSG:4326 great-circle measurement example. */
public final class MeasurementViewer {
    private MeasurementViewer() {}

    /** Launches the viewer on the Swing event-dispatch thread. */
    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(MeasurementViewer::showWindow);
    }

    /** Creates both configured example tabs without opening a window. */
    public static JTabbedPane createContent() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Planar metres", tab(planarView(), planarExplanation()));
        tabs.addTab("EPSG:4326 great circle", tab(geographicView(), geographicExplanation()));
        return tabs;
    }

    private static MapView planarView() {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        configure(
                view,
                new MeasurementTool(DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857)));
        view.setViewport(new MapViewport(800, 520, 0, 0, 2_000));
        return view;
    }

    private static MapView geographicView() {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
        view.setHorizontalWrap(HorizontalWrap.webMercator());
        configure(
                view,
                new MeasurementTool(
                        DistanceStrategies.epsg4326GreatCircle(CrsDefinitions.EPSG_4326)));
        view.setViewport(new MapViewport(800, 520, 0, 0, 50_000));
        return view;
    }

    private static void configure(MapView view, MeasurementTool tool) {
        view.setPreferredSize(new Dimension(800, 520));
        view.setActiveTool(tool);
    }

    private static JPanel tab(MapView view, String explanation) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(view, BorderLayout.CENTER);
        panel.add(new JLabel(explanation, SwingConstants.CENTER), BorderLayout.SOUTH);
        return panel;
    }

    private static String planarExplanation() {
        return "Clicks measure projected coordinate metres; Web Mercator ground scale is not corrected.";
    }

    private static String geographicExplanation() {
        return "Clicks store EPSG:4326 positions and draw the shortest path continuously across repeated datelines.";
    }

    private static void showWindow() {
        JFrame frame = new JFrame("mundane-java-map — measurement viewer");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Component content = createContent();
        frame.add(content);
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }
}
