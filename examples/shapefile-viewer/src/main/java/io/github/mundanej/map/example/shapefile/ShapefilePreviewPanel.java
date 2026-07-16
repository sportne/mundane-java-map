package io.github.mundanej.map.example.shapefile;

import io.github.mundanej.map.api.AttributeBytes;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.MapSourceReportEvent;
import io.github.mundanej.map.api.MapSourceReportListener;
import io.github.mundanej.map.api.SourceDiagnostic;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/** Bounded toolkit-side metadata, attribute, and diagnostic preview. */
@SuppressWarnings("serial")
final class ShapefilePreviewPanel extends JPanel implements MapSourceReportListener {
    private static final long serialVersionUID = 1L;
    private static final int MAXIMUM_VALUE_CHARACTERS = 160;

    private final ShapefileViewer.LoadedDataset loaded;
    private final JList<String> records;
    private final JTextArea details = new JTextArea();
    private DiagnosticReport live = DiagnosticReport.empty();
    private boolean ignoredOpeningTransition;
    private int reportTransitionCount;

    ShapefilePreviewPanel(ShapefileViewer.LoadedDataset loaded) {
        super(new BorderLayout());
        this.loaded = Objects.requireNonNull(loaded, "loaded");
        DefaultListModel<String> model = new DefaultListModel<>();
        for (FeatureRecord record : loaded.preview()) {
            model.addElement(record.id());
        }
        if (loaded.truncated()) {
            model.addElement("preview truncated");
        }
        records = new JList<>(model);
        records.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        records.addListSelectionListener(ignored -> refresh());
        details.setEditable(false);
        details.setLineWrap(false);
        JSplitPane split =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        new JScrollPane(records),
                        new JScrollPane(details));
        split.setResizeWeight(0.25);
        add(split, BorderLayout.CENTER);
        setPreferredSize(new Dimension(360, 600));
        refresh();
    }

    void selectFirstPreview() {
        if (!loaded.preview().isEmpty()) {
            records.setSelectedIndex(0);
        } else {
            refresh();
        }
    }

    List<String> presentationStrings() {
        java.util.ArrayList<String> values =
                new java.util.ArrayList<>(records.getModel().getSize() + 1);
        for (int index = 0; index < records.getModel().getSize(); index++) {
            values.add(records.getModel().getElementAt(index));
        }
        values.add(details.getText());
        return List.copyOf(values);
    }

    int reportTransitionCount() {
        return reportTransitionCount;
    }

    @Override
    public void onMapSourceReportChanged(MapSourceReportEvent event) {
        reportTransitionCount++;
        Optional<DiagnosticReport> current = event.current();
        if (!ignoredOpeningTransition
                && current.isPresent()
                && current.orElseThrow().equals(loaded.opening())) {
            ignoredOpeningTransition = true;
            return;
        }
        live = current.orElseGet(DiagnosticReport::empty);
        refresh();
    }

    private void refresh() {
        StringBuilder text = new StringBuilder(1024);
        text.append("Source: ").append(loaded.metadata().identity().displayName()).append('\n');
        text.append("Extent: ").append(loaded.metadata().extent().orElse(null)).append('\n');
        text.append("CRS: ");
        loaded.metadata()
                .crs()
                .ifPresentOrElse(
                        crs ->
                                text.append(
                                        crs.definition()
                                                .map(definition -> definition.canonicalIdentifier())
                                                .orElse("unknown")),
                        () -> text.append("missing"));
        text.append("\nSchema:");
        loaded.metadata()
                .schema()
                .ifPresentOrElse(
                        schema -> {
                            for (var field : schema.fields()) {
                                text.append("\n  ")
                                        .append(field.name())
                                        .append(": ")
                                        .append(field.type());
                            }
                        },
                        () -> text.append(" none"));
        int selected = records.getSelectedIndex();
        if (selected >= 0 && selected < loaded.preview().size()) {
            FeatureRecord record = loaded.preview().get(selected);
            text.append("\nRecord: ").append(record.id());
            for (Map.Entry<String, Object> attribute : record.attributes().entrySet()) {
                text.append("\n  ")
                        .append(attribute.getKey())
                        .append(" = ")
                        .append(displayValue(attribute.getValue()));
            }
        }
        appendReport(text, "Opening diagnostics", loaded.opening());
        appendReport(text, "Preview diagnostics", loaded.query());
        appendReport(text, "Latest map diagnostics", live);
        details.setText(text.toString());
        details.setCaretPosition(0);
    }

    private static String displayValue(Object value) {
        if (value == AttributeNull.INSTANCE) {
            return "null";
        }
        if (value instanceof AttributeBytes bytes) {
            return "<binary " + bytes.length() + " bytes>";
        }
        String text = String.valueOf(value);
        return text.length() <= MAXIMUM_VALUE_CHARACTERS
                ? text
                : text.substring(0, MAXIMUM_VALUE_CHARACTERS) + "…";
    }

    private static void appendReport(StringBuilder text, String heading, DiagnosticReport report) {
        text.append('\n').append(heading).append(':');
        if (report.entries().isEmpty()) {
            text.append(" none");
        }
        for (SourceDiagnostic diagnostic : report.entries()) {
            text.append("\n  ").append(diagnostic.code());
            diagnostic.location().ifPresent(location -> appendLocation(text, location));
            if (!diagnostic.context().isEmpty()) {
                text.append(' ').append(diagnostic.context());
            }
        }
        if (report.omittedWarningCount() > 0) {
            text.append("\n  omitted=").append(report.omittedWarningCount());
        }
    }

    private static void appendLocation(StringBuilder text, DiagnosticLocation location) {
        List<String> values =
                java.util.stream.Stream.of(
                                location.component().map(value -> "component=" + value),
                                location.recordNumber().isPresent()
                                        ? Optional.of(
                                                "record=" + location.recordNumber().getAsLong())
                                        : Optional.<String>empty(),
                                location.fieldName().map(value -> "field=" + value),
                                location.byteOffset().isPresent()
                                        ? Optional.of("offset=" + location.byteOffset().getAsLong())
                                        : Optional.<String>empty())
                        .flatMap(Optional::stream)
                        .toList();
        if (!values.isEmpty()) {
            text.append(" [").append(String.join(", ", values)).append(']');
        }
    }
}
