package io.github.mundanej.map.example.edit;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CreateFeature;
import io.github.mundanej.map.api.DeleteFeature;
import io.github.mundanej.map.api.FeatureEditResult;
import io.github.mundanej.map.api.FeatureEditStatus;
import io.github.mundanej.map.api.FeatureEditTransaction;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.ReplaceFeature;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeatureEditSession;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/** Demonstrates immutable atomic point edits with bounded undo and redo history. */
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
        view.setLayerBindings(
                List.of(
                        MapLayerBinding.editableFeature(
                                "editable", "Editable points", session, marker, line, fill)));
        view.setViewport(new io.github.mundanej.map.core.MapViewport(900, 600, 0, 0, 5_000));
        return new ViewerState(view, session, 0);
    }

    private static void show() {
        ViewerState state = createState();
        JFrame frame = new JFrame("Mundane Map — Immutable Point Edit Session");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.add(state.view(), BorderLayout.CENTER);
        JLabel status = new JLabel("Initial snapshot; revision 0");
        frame.add(status, BorderLayout.SOUTH);
        HistoryControls historyControls = createHistoryControls(state, status);
        frame.add(historyControls.panel(), BorderLayout.NORTH);
        historyControls.refresh().run();
        Timer timer =
                new Timer(
                        900,
                        event -> {
                            if (!state.applyNext()) {
                                ((Timer) event.getSource()).stop();
                                return;
                            }
                            status.setText(
                                    "Applied "
                                            + state.lastDescription()
                                            + "; revision "
                                            + state.session().snapshot().revision());
                            historyControls.refresh().run();
                            state.view().repaint();
                        });
        timer.setInitialDelay(900);
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        timer.stop();
                        state.view().close();
                    }
                });
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
        timer.start();
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

    private static FeatureRecord record(String id, double x, double y) {
        return new FeatureRecord(id, id, new PointGeometry(new Coordinate(x, y)), Map.of());
    }

    static final class ViewerState {
        private final MapView view;
        private final FeatureEditSession session;
        private int nextStep;
        private String lastDescription = "initial snapshot";

        private ViewerState(MapView view, FeatureEditSession session, int nextStep) {
            this.view = view;
            this.session = session;
            this.nextStep = nextStep;
        }

        MapView view() {
            return view;
        }

        FeatureEditSession session() {
            return session;
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
    }

    record HistoryControls(JPanel panel, JButton undo, JButton redo, Runnable refresh) {}
}
