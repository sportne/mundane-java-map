package io.github.mundanej.map.example.edit;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CreateFeature;
import io.github.mundanej.map.api.DeleteFeature;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureEditResult;
import io.github.mundanej.map.api.FeatureEditStatus;
import io.github.mundanej.map.api.FeatureEditTransaction;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointFeatureDraft;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.ReplaceFeature;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SnapFeature;
import io.github.mundanej.map.api.SnapLimits;
import io.github.mundanej.map.api.SnapQueryResult;
import io.github.mundanej.map.api.SnapReferenceLayer;
import io.github.mundanej.map.api.SnapReferenceSet;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.PointEditController;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.FeatureSnapper;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.SnapQuery;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.plaf.LayerUI;

/** Demonstrates interactive point create, move, delete, snapping, undo, and redo. */
public final class PointEditViewer {
    private PointEditViewer() {}

    /**
     * Launches the viewer on the Swing event-dispatch thread.
     *
     * @param arguments ignored command-line arguments
     */
    public static void main(String[] arguments) {
        SwingUtilities.invokeLater(PointEditViewer::show);
    }

    static ViewerState createState() {
        FeatureEditSession session =
                FeatureEditSession.open(
                        CrsDefinitions.EPSG_3857, List.of(record("alpha", -1_000_000, 0)));
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setPreferredSize(new Dimension(900, 600));
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, Rgba.rgb(25, 115, 210), 14, 1);
        var line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(25, 115, 210),
                                new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1);
        var fill = SolidFillSymbol.of(Rgba.rgb(25, 115, 210), 1);
        LineStringGeometry guideGeometry =
                new LineStringGeometry(
                        io.github.mundanej.map.api.CoordinateSequence.of(
                                -1_500_000, -750_000, 1_500_000, -750_000));
        var guideLine =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(80, 150, 90),
                                new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        0.75);
        MapLayerBinding guide =
                MapLayerBinding.snapshot(
                        new InMemoryLayer(
                                "reference",
                                "Visible snapping guide",
                                List.of(
                                        new Feature(
                                                "guide",
                                                "Snap guide",
                                                guideGeometry,
                                                Map.of(),
                                                guideLine))));
        MapLayerBinding editable =
                MapLayerBinding.editableFeature(
                        "editable", "Editable points", session, marker, line, fill);
        view.setLayerBindings(List.of(guide, editable));
        view.setViewport(new io.github.mundanej.map.core.MapViewport(900, 600, 0, 0, 5_000));
        view.setSelection(new FeatureSelection("editable", "alpha"));
        SnapReferenceSet references =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new SnapReferenceLayer(
                                        "reference",
                                        List.of(new SnapFeature("guide", guideGeometry)))));
        PointEditController controller =
                new PointEditController(
                        view,
                        editable,
                        references,
                        SnapLimits.DEFAULT,
                        PointEditController.DEFAULT_SNAP_TOLERANCE_PIXELS);
        view.setActiveTool(controller);
        return new ViewerState(view, session, controller, 0);
    }

    private static void show() {
        ViewerState state = createState();
        JFrame frame = new JFrame("Mundane Map — Immutable Point Edit Session");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(state.view(), BorderLayout.CENTER);
        JLabel status = new JLabel("Selected alpha; choose Create, Move, or Navigate");
        frame.add(status, BorderLayout.SOUTH);
        frame.add(createInteractiveControls(state, status), BorderLayout.NORTH);
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        state.view().close();
                    }
                });
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    static JPanel createInteractiveControls(ViewerState state, JLabel status) {
        JToggleButton create = new JToggleButton("Create Point");
        JToggleButton move = new JToggleButton("Move Selected (drag)");
        JButton delete = new JButton("Delete Selected");
        JButton undo = new JButton("Undo");
        JButton redo = new JButton("Redo");
        JToggleButton navigate = new JToggleButton("Navigate", true);
        ButtonGroup modes = new ButtonGroup();
        modes.add(create);
        modes.add(move);
        modes.add(navigate);
        JPanel controls = new JPanel();
        controls.add(create);
        controls.add(move);
        controls.add(delete);
        controls.add(undo);
        controls.add(redo);
        controls.add(navigate);
        create.addActionListener(
                event -> {
                    String id = state.nextInteractiveId();
                    state.controller()
                            .create(
                                    new PointFeatureDraft(
                                            id, "Point " + id, Map.of("created", true)));
                    status.setText(
                            "CREATE active — click open map space to place "
                                    + id
                                    + " (green guide snaps)");
                });
        move.addActionListener(
                event -> {
                    state.controller().moveSelected();
                    status.setText(
                            selectedText(state)
                                    + " — drag that selected point; green preview means snapped");
                });
        delete.addActionListener(event -> state.controller().deleteSelected());
        undo.addActionListener(event -> state.controller().undo());
        redo.addActionListener(event -> state.controller().redo());
        navigate.addActionListener(
                event -> {
                    state.controller().clearMode();
                    status.setText(
                            "NAVIGATE active — click a point to select it, drag blank space to pan, or use the wheel");
                });
        Runnable refresh =
                () -> {
                    boolean selectedEditable = selectedEditablePoint(state);
                    move.setEnabled(selectedEditable);
                    delete.setEnabled(selectedEditable);
                    undo.setEnabled(state.session().canUndo());
                    redo.setEnabled(state.session().canRedo());
                    undo.setText(
                            state.session()
                                    .undoDescription()
                                    .map(value -> "Undo " + value)
                                    .orElse("Undo"));
                    redo.setText(
                            state.session()
                                    .redoDescription()
                                    .map(value -> "Redo " + value)
                                    .orElse("Redo"));
                };
        state.view()
                .addMapSelectionListener(
                        event -> {
                            refresh.run();
                            if (state.controller().mode() == PointEditController.Mode.NONE) {
                                status.setText(
                                        event.current()
                                                .map(
                                                        selection ->
                                                                "Selected "
                                                                        + selection.featureId()
                                                                        + "; choose Move to drag or Delete")
                                                .orElse(
                                                        "No point selected; click one in Navigate mode"));
                            }
                        });
        state.controller()
                .addResultListener(
                        result -> {
                            refresh.run();
                            String outcome =
                                    result.problem()
                                            .map(
                                                    problem ->
                                                            problem.code()
                                                                    + ": "
                                                                    + problem.message())
                                            .orElse(result.status().toString());
                            status.setText(
                                    outcome
                                            + "; revision "
                                            + result.snapshot().revision()
                                            + "; points "
                                            + result.snapshot().records().size()
                                            + "; "
                                            + selectedText(state));
                        });
        refresh.run();
        return controls;
    }

    private static boolean selectedEditablePoint(ViewerState state) {
        return state.view()
                .selection()
                .filter(selection -> selection.layerId().equals("editable"))
                .flatMap(
                        selection ->
                                state.session().snapshot().records().stream()
                                        .filter(record -> record.id().equals(selection.featureId()))
                                        .findFirst())
                .filter(record -> record.geometry() instanceof PointGeometry)
                .isPresent();
    }

    private static String selectedText(ViewerState state) {
        return state.view()
                .selection()
                .filter(selection -> selection.layerId().equals("editable"))
                .map(selection -> "selected " + selection.featureId())
                .orElse("no editable point selected");
    }

    static HistoryControls createHistoryControls(ViewerState state, JLabel status) {
        JButton undo = new JButton("Undo");
        JButton redo = new JButton("Redo");
        JPanel controls = new JPanel();
        controls.add(undo);
        controls.add(redo);
        Runnable refreshHistory =
                () -> {
                    undo.setEnabled(state.scriptComplete() && state.session().canUndo());
                    redo.setEnabled(state.scriptComplete() && state.session().canRedo());
                    undo.setText(
                            state.session()
                                    .undoDescription()
                                    .map(value -> "Undo " + value)
                                    .orElse("Undo"));
                    redo.setText(
                            state.session()
                                    .redoDescription()
                                    .map(value -> "Redo " + value)
                                    .orElse("Redo"));
                };
        undo.addActionListener(
                event -> {
                    FeatureEditResult result = state.undo(state.session().snapshot().revision());
                    status.setText(state.statusText(result));
                    refreshHistory.run();
                });
        redo.addActionListener(
                event -> {
                    FeatureEditResult result = state.redo(state.session().snapshot().revision());
                    status.setText(state.statusText(result));
                    refreshHistory.run();
                });
        return new HistoryControls(controls, undo, redo, refreshHistory);
    }

    static JLayer<MapView> createPreviewLayer(ViewerState state) {
        return new JLayer<>(state.view(), new PreviewLayerUi(state));
    }

    private static FeatureRecord record(String id, double x, double y) {
        return new FeatureRecord(id, id, new PointGeometry(new Coordinate(x, y)), Map.of());
    }

    static final class ViewerState {
        private final MapView view;
        private final FeatureEditSession session;
        private final PointEditController controller;
        private int nextStep;
        private int nextInteractive;
        private String lastDescription = "initial snapshot";

        private ViewerState(
                MapView view,
                FeatureEditSession session,
                PointEditController controller,
                int nextStep) {
            this.view = view;
            this.session = session;
            this.controller = controller;
            this.nextStep = nextStep;
        }

        MapView view() {
            return view;
        }

        FeatureEditSession session() {
            return session;
        }

        PointEditController controller() {
            return controller;
        }

        String nextInteractiveId() {
            return "point-" + ++nextInteractive;
        }

        String lastDescription() {
            return lastDescription;
        }

        boolean applyNext() {
            long revision = session.snapshot().revision();
            FeatureEditTransaction transaction =
                    switch (nextStep) {
                        case 0 ->
                                new FeatureEditTransaction(
                                        revision,
                                        "create beta",
                                        List.of(new CreateFeature(record("beta", 1_000_000, 0))));
                        case 1 ->
                                new FeatureEditTransaction(
                                        revision,
                                        "move alpha",
                                        List.of(
                                                new ReplaceFeature(
                                                        "alpha",
                                                        record("alpha", -500_000, 500_000))));
                        case 2 ->
                                new FeatureEditTransaction(
                                        revision,
                                        "delete beta",
                                        List.of(new DeleteFeature("beta")));
                        default -> null;
                    };
            if (transaction == null) {
                return false;
            }
            FeatureEditResult result = session.apply(transaction);
            if (result.status() == FeatureEditStatus.REJECTED) {
                lastDescription = result.problem().orElseThrow().code();
                return false;
            }
            nextStep++;
            lastDescription = transaction.description();
            return true;
        }

        boolean scriptComplete() {
            return nextStep >= 3;
        }

        FeatureEditResult undo(long expectedRevision) {
            FeatureEditResult result = session.undo(expectedRevision);
            lastDescription = session.redoDescription().orElse("nothing to undo");
            view.repaint();
            return result;
        }

        FeatureEditResult redo(long expectedRevision) {
            FeatureEditResult result = session.redo(expectedRevision);
            lastDescription = session.undoDescription().orElse("nothing to redo");
            view.repaint();
            return result;
        }

        String statusText(FeatureEditResult result) {
            return result.problem()
                    .map(problem -> problem.code() + "; revision " + result.snapshot().revision())
                    .orElse(lastDescription + "; revision " + result.snapshot().revision());
        }

        PreviewScenario previewScenario() {
            var operation =
                    CrsRegistry.level1()
                            .operation(CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
            SnapReferenceSet references =
                    new SnapReferenceSet(
                            CrsDefinitions.EPSG_3857,
                            List.of(
                                    new SnapReferenceLayer(
                                            "preview-reference",
                                            List.of(
                                                    new SnapFeature(
                                                            "preview-line",
                                                            new LineStringGeometry(
                                                                    io.github.mundanej.map.api
                                                                            .CoordinateSequence.of(
                                                                            -500_000, -250_000,
                                                                            500_000,
                                                                            -250_000)))))));
            SnapQueryResult snapped =
                    new FeatureSnapper()
                            .find(
                                    new SnapQuery(
                                            450,
                                            346,
                                            8,
                                            operation,
                                            operation,
                                            view.viewport(),
                                            references,
                                            java.util.Set.of(),
                                            SnapLimits.DEFAULT,
                                            io.github.mundanej.map.api.CancellationToken.none()));
            SnapQueryResult unsnapped =
                    new FeatureSnapper()
                            .find(
                                    new SnapQuery(
                                            700,
                                            100,
                                            8,
                                            operation,
                                            operation,
                                            view.viewport(),
                                            references,
                                            java.util.Set.of(),
                                            SnapLimits.DEFAULT,
                                            io.github.mundanej.map.api.CancellationToken.none()));
            return new PreviewScenario(450, 346, snapped, 700, 100, unsnapped);
        }
    }

    record HistoryControls(JPanel panel, JButton undo, JButton redo, Runnable refresh) {}

    record PreviewScenario(
            double snappedPointerX,
            double snappedPointerY,
            SnapQueryResult snapped,
            double unsnappedPointerX,
            double unsnappedPointerY,
            SnapQueryResult unsnapped) {}

    @SuppressWarnings("serial")
    private static final class PreviewLayerUi extends LayerUI<MapView> {
        private final ViewerState state;

        private PreviewLayerUi(ViewerState state) {
            this.state = state;
        }

        @Override
        public void paint(Graphics graphics, javax.swing.JComponent component) {
            super.paint(graphics, component);
            PreviewScenario scenario = state.previewScenario();
            Graphics2D overlay = (Graphics2D) graphics.create();
            try {
                overlay.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                overlay.setStroke(new BasicStroke(2));
                scenario.snapped()
                        .result()
                        .ifPresent(
                                result -> {
                                    Coordinate screen =
                                            state.view()
                                                    .viewport()
                                                    .worldToScreen(result.coordinate());
                                    overlay.setColor(new Color(30, 160, 75));
                                    overlay.drawLine(
                                            (int) scenario.snappedPointerX(),
                                            (int) scenario.snappedPointerY(),
                                            (int) Math.round(screen.x()),
                                            (int) Math.round(screen.y()));
                                    overlay.fillOval(
                                            (int) Math.round(screen.x()) - 5,
                                            (int) Math.round(screen.y()) - 5,
                                            10,
                                            10);
                                });
                overlay.setColor(new Color(220, 115, 20));
                int unsnappedX = (int) scenario.unsnappedPointerX();
                int unsnappedY = (int) scenario.unsnappedPointerY();
                overlay.drawLine(unsnappedX - 5, unsnappedY - 5, unsnappedX + 5, unsnappedY + 5);
                overlay.drawLine(unsnappedX - 5, unsnappedY + 5, unsnappedX + 5, unsnappedY - 5);
            } finally {
                overlay.dispose();
            }
        }
    }
}
