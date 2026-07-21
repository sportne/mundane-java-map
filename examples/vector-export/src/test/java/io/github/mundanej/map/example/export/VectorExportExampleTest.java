package io.github.mundanej.map.example.export;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.awt.MapView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VectorExportExampleTest {
    @TempDir Path temporaryDirectory;

    @Test
    void exportsTheDisplayedViewportWithoutOpeningABrowser() throws Exception {
        Path target = temporaryDirectory.resolve("visible.svg");
        SwingUtilities.invokeAndWait(
                () -> {
                    JPanel panel = VectorExportExample.createPanel(target);
                    JButton button = findButton(panel);
                    button.doClick();
                });

        assertTrue(Files.exists(target));
        assertTrue(Files.readString(target, StandardCharsets.UTF_8).endsWith("</svg>\n"));
    }

    @Test
    void reportsStructuredCaptureFailure() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    JPanel panel =
                            VectorExportExample.createPanel(
                                    temporaryDirectory.resolve("unused.svg"));
                    MapView map = find(panel, MapView.class, "vector-export-map");
                    map.setOpaque(false);
                    findButton(panel).doClick();
                    JLabel status = find(panel, JLabel.class, "export-status");
                    assertTrue(status.getText().contains("VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID"));
                    assertTrue(status.getText().contains("componentBackground"));
                });
    }

    private static JButton findButton(java.awt.Container root) {
        for (java.awt.Component component : root.getComponents()) {
            if (component instanceof JButton button && "export-button".equals(button.getName())) {
                return button;
            }
            if (component instanceof java.awt.Container child) {
                JButton result = findButtonOrNull(child);
                if (result != null) {
                    return result;
                }
            }
        }
        throw new AssertionError("export button missing");
    }

    private static JButton findButtonOrNull(java.awt.Container root) {
        for (java.awt.Component component : root.getComponents()) {
            if (component instanceof JButton button && "export-button".equals(button.getName())) {
                return button;
            }
            if (component instanceof java.awt.Container child) {
                JButton result = findButtonOrNull(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static <T extends java.awt.Component> T find(
            java.awt.Container root, Class<T> type, String name) {
        for (java.awt.Component component : root.getComponents()) {
            if (type.isInstance(component) && name.equals(component.getName())) {
                return type.cast(component);
            }
            if (component instanceof java.awt.Container child) {
                T result = findOrNull(child, type, name);
                if (result != null) {
                    return result;
                }
            }
        }
        throw new AssertionError(name + " missing");
    }

    private static <T extends java.awt.Component> T findOrNull(
            java.awt.Container root, Class<T> type, String name) {
        for (java.awt.Component component : root.getComponents()) {
            if (type.isInstance(component) && name.equals(component.getName())) {
                return type.cast(component);
            }
            if (component instanceof java.awt.Container child) {
                T result = findOrNull(child, type, name);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
