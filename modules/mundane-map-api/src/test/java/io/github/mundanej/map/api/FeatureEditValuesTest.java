package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeatureEditValuesTest {
    private static final CrsDefinition CRS =
            new CrsDefinition(
                    "LOCAL:EDIT",
                    CrsKind.PROJECTED,
                    new CrsAxis(CrsAxisMeaning.EASTING, CrsUnit.METRE),
                    new CrsAxis(CrsAxisMeaning.NORTHING, CrsUnit.METRE),
                    new Envelope(-100, -100, 100, 100));

    @Test
    void snapshotAndTransactionDefensivelyOwnOrderedUniqueValues() {
        List<FeatureRecord> records = new ArrayList<>(List.of(record("a", 1)));
        FeatureEditSnapshot snapshot = new FeatureEditSnapshot(4, CRS, records);
        records.clear();
        assertEquals(List.of(record("a", 1)), snapshot.records());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.records().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeatureEditSnapshot(0, CRS, List.of(record("a", 1), record("a", 2))));

        List<FeatureEditCommand> commands =
                new ArrayList<>(List.of(new CreateFeature(record("b", 2))));
        FeatureEditTransaction transaction = new FeatureEditTransaction(4, "create b", commands);
        commands.clear();
        assertEquals(1, transaction.commands().size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FeatureEditTransaction(
                                0,
                                "duplicate",
                                List.of(
                                        new ReplaceFeature("a", record("a", 2)),
                                        new DeleteFeature("a"))));
        assertThrows(IllegalArgumentException.class, () -> new ReplaceFeature("a", record("b", 2)));
    }

    @Test
    void resultEventProblemAndLimitsEnforceClosedVariantsAndBounds() {
        FeatureEditSnapshot previous = new FeatureEditSnapshot(0, CRS, List.of(record("a", 1)));
        FeatureEditSnapshot current = new FeatureEditSnapshot(1, CRS, List.of(record("a", 2)));
        LinkedHashMap<String, String> mutable = new LinkedHashMap<>();
        mutable.put("maximum", "1");
        mutable.put("actual", "2");
        FeatureEditProblem problem =
                new FeatureEditProblem("EDIT_TEST", "bounded problem", mutable);
        mutable.clear();
        assertEquals(List.of("actual", "maximum"), List.copyOf(problem.context().keySet()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeatureEditProblem("EDIT_TEST", "problem", Map.of("rawId", "x")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FeatureEditResult(
                                FeatureEditStatus.APPLIED, current, Optional.of(problem)));
        FeatureEditResult rejected = FeatureEditResult.rejected(previous, problem);
        assertEquals(previous, rejected.snapshot());
        assertEquals(
                current,
                new FeatureEditEvent(FeatureEditCause.COMMIT, previous, current, "replace")
                        .current());
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeatureEditEvent(FeatureEditCause.COMMIT, previous, previous, "same"));

        FeatureEditLimits limits =
                FeatureEditLimits.DEFAULT
                        .withMaximumFeatures(3)
                        .withMaximumCommandsPerTransaction(2)
                        .withMaximumSnapshotBytes(100);
        assertEquals(new FeatureEditLimits(3, 2, 100), limits);
        assertThrows(
                IllegalArgumentException.class,
                () -> new FeatureEditLimits(0, limits.maximumCommandsPerTransaction(), 100));
        FeatureEditConfigurationException exception =
                new FeatureEditConfigurationException(problem);
        assertNotSame(problem, exception);
        assertSame(problem, exception.problem());
    }

    private static FeatureRecord record(String id, double x) {
        return new FeatureRecord(
                id, id, new PointGeometry(new Coordinate(x, 0)), Map.of("value", (long) x));
    }
}
