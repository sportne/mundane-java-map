package io.github.mundanej.map.example.symbols;

import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Runnable visual inventory of the Level 1 symbol model. */
public final class SymbolGallery {
    private static final int MAP_WIDTH = 760;
    private static final int MAP_HEIGHT = 520;

    private SymbolGallery() {}

    /** Launches the gallery on the Swing event-dispatch thread. */
    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(SymbolGallery::showWindow);
    }

    /** Creates the complete gallery without opening a top-level window. */
    public static JPanel createGalleryPanel() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("gallery construction must run on the EDT");
        }
        GalleryDocument document = GalleryDocument.create();
        JTabbedPane tabs = new JTabbedPane();
        for (GallerySection section : document.sections()) {
            tabs.addTab(section.title(), sectionPanel(section));
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel sectionPanel(GallerySection section) {
        List<Feature> features =
                section.cases().stream().flatMap(value -> value.features().stream()).toList();
        MapView map =
                new MapView(
                        new WebMercatorProjection(),
                        SymbolRendererRegistry.builderWithBuiltIns().build());
        map.setName("gallery-map-" + section.id());
        map.setLayers(
                List.of(new InMemoryLayer("gallery-" + section.id(), section.title(), features)));
        map.setPreferredSize(new Dimension(MAP_WIDTH, MAP_HEIGHT));
        map.setSize(MAP_WIDTH, MAP_HEIGHT);
        map.fitToData(48);

        Box legend = new Box(BoxLayout.Y_AXIS);
        legend.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        legend.add(new JLabel("Pan: drag • Zoom: wheel"));
        legend.add(Box.createVerticalStrut(8));
        for (GalleryCase galleryCase : section.cases()) {
            JLabel label = new JLabel(galleryCase.title());
            label.setName("gallery-case-" + galleryCase.id());
            legend.add(label);
        }
        JScrollPane legendScroll = new JScrollPane(legend);
        legendScroll.setPreferredSize(new Dimension(230, MAP_HEIGHT));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(map, BorderLayout.CENTER);
        panel.add(legendScroll, BorderLayout.EAST);
        return panel;
    }

    private static void showWindow() {
        JFrame frame = new JFrame("mundane-java-map — symbol gallery");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(createGalleryPanel());
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }
}
