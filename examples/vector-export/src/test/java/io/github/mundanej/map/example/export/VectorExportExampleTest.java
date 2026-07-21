package io.github.mundanej.map.example.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.awt.MapView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VectorExportExampleTest {
    private static final String EXPECTED_EXPORT =
            "/io/github/mundanej/map/example/export/vector-export-example.svg";

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
        String actual = Files.readString(target, StandardCharsets.UTF_8);
        String expected;
        try (var input = VectorExportExampleTest.class.getResourceAsStream(EXPECTED_EXPORT)) {
            if (input == null) {
                throw new AssertionError("checked-in live-example SVG is missing");
            }
            expected = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(beforeLabel(expected), beforeLabel(actual));
        assertEquals(afterLabel(expected), afterLabel(actual));
        assertTrue(actual.contains("fill=\"#5ba36a\" fill-rule=\"evenodd\""));
        assertTrue(actual.contains("stroke=\"#d04b3d\""));
        assertTrue(actual.contains("fill=\"#236eaf\""));
        assertTrue(
                actual.contains(
                        "fill=\"#23374b\" font-family=\"sans-serif\" font-size=\"14.0\""
                                + " font-weight=\"bold\""));
        LabelEnvelope label = labelEnvelope(actual);
        assertEquals("TRACK 7", label.text());
        assertTrue(label.x() >= 0 && label.x() + label.advance() <= 720);
        assertTrue(label.baseline() >= 0 && label.baseline() <= 480);
        assertTrue(label.advance() > 0 && label.advance() <= 100);
        assertTrue(actual.endsWith("</svg>\n"));
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

    private static String beforeLabel(String document) {
        int start = document.indexOf("    <text ");
        if (start < 0) {
            throw new AssertionError("exported label is missing");
        }
        return document.substring(0, start);
    }

    private static String afterLabel(String document) {
        int start = document.indexOf("    <text ");
        int end = document.indexOf("</text>", start);
        if (start < 0 || end < 0) {
            throw new AssertionError("exported label is missing");
        }
        return document.substring(end + "</text>".length());
    }

    private static LabelEnvelope labelEnvelope(String document) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        var reader = factory.createXMLStreamReader(new java.io.StringReader(document));
        try {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT
                        && reader.getLocalName().equals("text")) {
                    double x = Double.parseDouble(reader.getAttributeValue(null, "x"));
                    double baseline = Double.parseDouble(reader.getAttributeValue(null, "y"));
                    double advance =
                            Double.parseDouble(reader.getAttributeValue(null, "textLength"));
                    assertTrue(Double.isFinite(x));
                    assertTrue(Double.isFinite(baseline));
                    assertTrue(Double.isFinite(advance));
                    return new LabelEnvelope(x, baseline, advance, reader.getElementText());
                }
            }
        } finally {
            reader.close();
        }
        throw new AssertionError("exported label is missing");
    }

    private record LabelEnvelope(double x, double baseline, double advance, String text) {}
}
