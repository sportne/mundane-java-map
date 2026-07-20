package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CreateFeature;
import io.github.mundanej.map.api.DeleteFeature;
import io.github.mundanej.map.api.FeatureEditCause;
import io.github.mundanej.map.api.FeatureEditConfigurationException;
import io.github.mundanej.map.api.FeatureEditHistoryLimits;
import io.github.mundanej.map.api.FeatureEditLimits;
import io.github.mundanej.map.api.FeatureEditListener;
import io.github.mundanej.map.api.FeatureEditNotificationException;
import io.github.mundanej.map.api.FeatureEditResult;
import io.github.mundanej.map.api.FeatureEditSnapshot;
import io.github.mundanej.map.api.FeatureEditStatus;
import io.github.mundanej.map.api.FeatureEditTransaction;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.ReplaceFeature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FeatureEditSessionTest {
    @Test
    void appliesMixedTransactionsAtomicallyAndPreservesOrder() {
        FeatureEditSession session =
                FeatureEditSession.open(
                        CrsDefinitions.EPSG_3857,
                        List.of(record("a", 1), record("b", 2), record("c", 3)));
        List<Long> observed = new ArrayList<>();
        session.addFeatureEditListener(event -> observed.add(event.current().revision()));

        FeatureEditResult result =
                session.apply(
                        transaction(
                                0,
                                "mixed",
                                new ReplaceFeature("b", record("b", 20)),
                                new DeleteFeature("a"),
                                new CreateFeature(record("d", 4))));

        assertEquals(FeatureEditStatus.APPLIED, result.status());
        assertEquals(1, result.snapshot().revision());
        assertEquals(List.of("b", "c", "d"), ids(result.snapshot()));
        assertEquals(20, result.snapshot().records().getFirst().geometry().envelope().minX());
        assertEquals(List.of(1L), observed);
    }

    @Test
    void undoAndRedoMixedTransactionRestoreExactOrderWithMonotonicRevisions() {
        FeatureEditSession session =
                FeatureEditSession.open(
                        CrsDefinitions.EPSG_3857,
                        List.of(record("a", 1), record("b", 2), record("c", 3)));
        List<FeatureEditCause> causes = new ArrayList<>();
        session.addFeatureEditListener(event -> causes.add(event.cause()));
        session.apply(
                transaction(
                        0,
                        "mixed",
                        new ReplaceFeature("b", record("b", 20)),
                        new DeleteFeature("a"),
                        new CreateFeature(record("d", 4))));

        assertTrue(session.canUndo());
        assertFalse(session.canRedo());
        assertEquals("mixed", session.undoDescription().orElseThrow());
        FeatureEditResult undone = session.undo(1);
        assertEquals(2, undone.snapshot().revision());
        assertEquals(List.of("a", "b", "c"), ids(undone.snapshot()));
        assertEquals(2, undone.snapshot().records().get(1).geometry().envelope().minX());
        assertEquals("mixed", session.redoDescription().orElseThrow());

        FeatureEditResult redone = session.redo(2);
        assertEquals(3, redone.snapshot().revision());
        assertEquals(List.of("b", "c", "d"), ids(redone.snapshot()));
        assertEquals(
                List.of(FeatureEditCause.COMMIT, FeatureEditCause.UNDO, FeatureEditCause.REDO),
                causes);
    }

    @Test
    void historyAccountingIsExactAndOversizedEntryRejectsBeforeCommit() {
        FeatureEditSnapshot initial =
                new FeatureEditSnapshot(0, CrsDefinitions.EPSG_3857, List.of(record("a", 1)));
        FeatureEditHistoryLimits exact = new FeatureEditHistoryLimits(1, 95);
        FeatureEditSession accepted =
                FeatureEditSession.open(initial, FeatureEditLimits.DEFAULT, exact);
        assertEquals(
                FeatureEditStatus.APPLIED,
                accepted.apply(transaction(0, "r", new ReplaceFeature("a", record("a", 2))))
                        .status());
        assertTrue(accepted.canUndo());

        FeatureEditSession rejected =
                FeatureEditSession.open(
                        initial, FeatureEditLimits.DEFAULT, exact.withMaximumBytes(94));
        FeatureEditResult result =
                rejected.apply(transaction(0, "r", new ReplaceFeature("a", record("a", 2))));
        assertEquals("EDIT_HISTORY_ENTRY_LIMIT_EXCEEDED", result.problem().orElseThrow().code());
        assertSame(initial, rejected.snapshot());
        assertFalse(rejected.canUndo());
    }

    @Test
    void oldestWholeEntriesAreEvictedAndNewCommitClearsRedoBranch() {
        FeatureEditSession session =
                FeatureEditSession.open(
                        new FeatureEditSnapshot(
                                0, CrsDefinitions.EPSG_3857, List.of(record("a", 1))),
                        FeatureEditLimits.DEFAULT,
                        FeatureEditHistoryLimits.DEFAULT.withMaximumEntries(2));
        session.apply(transaction(0, "to two", new ReplaceFeature("a", record("a", 2))));
        session.apply(transaction(1, "to three", new ReplaceFeature("a", record("a", 3))));
        session.apply(transaction(2, "to four", new ReplaceFeature("a", record("a", 4))));

        session.undo(3);
        session.undo(4);
        assertEquals(2, session.snapshot().records().getFirst().geometry().envelope().minX());
        assertEquals("EDIT_NOTHING_TO_UNDO", session.undo(5).problem().orElseThrow().code());
        assertEquals("to three", session.redoDescription().orElseThrow());

        session.apply(transaction(5, "branch", new ReplaceFeature("a", record("a", 8))));
        assertFalse(session.canRedo());
        assertEquals("EDIT_NOTHING_TO_REDO", session.redo(6).problem().orElseThrow().code());
        assertEquals("branch", session.undoDescription().orElseThrow());
    }

    @Test
    void payloadCeilingEvictsOnlyOldestCompleteEntry() {
        FeatureEditSession session =
                FeatureEditSession.open(
                        new FeatureEditSnapshot(
                                0, CrsDefinitions.EPSG_3857, List.of(record("a", 1))),
                        FeatureEditLimits.DEFAULT,
                        new FeatureEditHistoryLimits(10, 190));
        session.apply(transaction(0, "a", new ReplaceFeature("a", record("a", 2))));
        session.apply(transaction(1, "b", new ReplaceFeature("a", record("a", 3))));
        session.apply(transaction(2, "c", new ReplaceFeature("a", record("a", 4))));

        assertEquals("c", session.undoDescription().orElseThrow());
        session.undo(3);
        assertEquals("b", session.undoDescription().orElseThrow());
        session.undo(4);
        assertFalse(session.canUndo());
        assertEquals(2, session.snapshot().records().getFirst().geometry().envelope().minX());
    }

    @Test
    void historyConflictAndRevisionExhaustionPreserveBothStacks() {
        FeatureEditSession session =
                FeatureEditSession.open(
                        new FeatureEditSnapshot(
                                Long.MAX_VALUE - 1,
                                CrsDefinitions.EPSG_3857,
                                List.of(record("a", 1))),
                        FeatureEditLimits.DEFAULT,
                        FeatureEditHistoryLimits.DEFAULT);
        session.apply(
                transaction(Long.MAX_VALUE - 1, "last", new ReplaceFeature("a", record("a", 2))));
        FeatureEditSnapshot atMaximum = session.snapshot();

        assertEquals(
                "EDIT_REVISION_CONFLICT",
                session.undo(Long.MAX_VALUE - 1).problem().orElseThrow().code());
        assertEquals(
                "EDIT_REVISION_EXHAUSTED",
                session.undo(Long.MAX_VALUE).problem().orElseThrow().code());
        assertSame(atMaximum, session.snapshot());
        assertTrue(session.canUndo());
        assertFalse(session.canRedo());
    }

    @Test
    void rejectionAndUnchangedDoNotPublishOrMutate() {
        FeatureEditSession session =
                FeatureEditSession.open(CrsDefinitions.EPSG_3857, List.of(record("a", 1)));
        List<Long> observed = new ArrayList<>();
        session.addFeatureEditListener(event -> observed.add(event.current().revision()));
        FeatureEditSnapshot initial = session.snapshot();

        FeatureEditResult stale =
                session.apply(transaction(1, "stale", new ReplaceFeature("a", record("a", 2))));
        assertEquals("EDIT_REVISION_CONFLICT", stale.problem().orElseThrow().code());
        FeatureEditResult missing =
                session.apply(transaction(0, "missing", new DeleteFeature("missing")));
        assertEquals("EDIT_FEATURE_NOT_FOUND", missing.problem().orElseThrow().code());
        FeatureEditResult unchanged =
                session.apply(transaction(0, "same", new ReplaceFeature("a", record("a", 1))));
        assertEquals(FeatureEditStatus.UNCHANGED, unchanged.status());
        assertSame(initial, session.snapshot());
        assertTrue(observed.isEmpty());
    }

    @Test
    void limitsAndRevisionExhaustionRejectBeforePublication() {
        FeatureEditLimits oneFeature =
                FeatureEditLimits.DEFAULT
                        .withMaximumFeatures(1)
                        .withMaximumCommandsPerTransaction(1);
        FeatureEditSession session =
                FeatureEditSession.open(
                        new FeatureEditSnapshot(
                                0, CrsDefinitions.EPSG_3857, List.of(record("a", 1))),
                        oneFeature);
        FeatureEditResult featureLimit =
                session.apply(transaction(0, "create", new CreateFeature(record("b", 2))));
        assertEquals("EDIT_FEATURE_LIMIT_EXCEEDED", featureLimit.problem().orElseThrow().code());
        assertEquals(0, session.snapshot().revision());

        FeatureEditSession commandLimit =
                FeatureEditSession.open(
                        new FeatureEditSnapshot(
                                0, CrsDefinitions.EPSG_3857, List.of(record("a", 1))),
                        oneFeature);
        FeatureEditResult commands =
                commandLimit.apply(
                        transaction(
                                0,
                                "two",
                                new ReplaceFeature("a", record("a", 2)),
                                new CreateFeature(record("b", 2))));
        assertEquals("EDIT_COMMAND_LIMIT_EXCEEDED", commands.problem().orElseThrow().code());

        FeatureEditSession exhausted =
                FeatureEditSession.open(
                        new FeatureEditSnapshot(
                                Long.MAX_VALUE, CrsDefinitions.EPSG_3857, List.of(record("a", 1))),
                        FeatureEditLimits.DEFAULT);
        FeatureEditResult revision =
                exhausted.apply(
                        transaction(
                                Long.MAX_VALUE,
                                "replace",
                                new ReplaceFeature("a", record("a", 2))));
        assertEquals("EDIT_REVISION_EXHAUSTED", revision.problem().orElseThrow().code());

        assertThrows(
                FeatureEditConfigurationException.class,
                () ->
                        FeatureEditSession.open(
                                new FeatureEditSnapshot(
                                        0,
                                        CrsDefinitions.EPSG_3857,
                                        List.of(record("a", 1), record("b", 2))),
                                oneFeature));
    }

    @Test
    void snapshotByteLimitIsExactAndRejectsGrowthWithoutPublication() {
        FeatureEditSnapshot initial =
                new FeatureEditSnapshot(0, CrsDefinitions.EPSG_3857, List.of(record("a", 1)));
        FeatureEditLimits exact = FeatureEditLimits.DEFAULT.withMaximumSnapshotBytes(52);

        assertThrows(
                FeatureEditConfigurationException.class,
                () ->
                        FeatureEditSession.open(
                                initial, FeatureEditLimits.DEFAULT.withMaximumSnapshotBytes(51)));
        FeatureEditSession session = FeatureEditSession.open(initial, exact);
        FeatureEditResult above =
                session.apply(
                        transaction(
                                0,
                                "grow name",
                                new ReplaceFeature(
                                        "a",
                                        new FeatureRecord(
                                                "a",
                                                "aa",
                                                new PointGeometry(new Coordinate(1, 0)),
                                                Map.of()))));

        assertEquals("EDIT_SNAPSHOT_LIMIT_EXCEEDED", above.problem().orElseThrow().code());
        assertSame(initial, session.snapshot());
    }

    @Test
    void laterRejectedCommandRollsBackEveryEarlierStagedCommand() {
        FeatureEditSession session =
                FeatureEditSession.open(CrsDefinitions.EPSG_3857, List.of(record("a", 1)));
        FeatureEditSnapshot initial = session.snapshot();

        FeatureEditResult result =
                session.apply(
                        transaction(
                                0,
                                "partially valid",
                                new ReplaceFeature("a", record("a", 2)),
                                new DeleteFeature("missing")));

        assertEquals(FeatureEditStatus.REJECTED, result.status());
        assertSame(initial, result.snapshot());
        assertSame(initial, session.snapshot());
    }

    @Test
    void listenerSnapshotMutationAndFailuresFollowCommittedSemantics() {
        FeatureEditSession session =
                FeatureEditSession.open(CrsDefinitions.EPSG_3857, List.of(record("a", 1)));
        RuntimeException first = new IllegalStateException("first");
        RuntimeException second = new IllegalArgumentException("second");
        FeatureEditListener addedLater = event -> {};
        session.addFeatureEditListener(
                event -> {
                    session.addFeatureEditListener(addedLater);
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    session.apply(
                                            transaction(
                                                    event.current().revision(),
                                                    "reentrant",
                                                    new ReplaceFeature("a", record("a", 8)))));
                    throw first;
                });
        session.addFeatureEditListener(
                event -> {
                    throw second;
                });

        FeatureEditNotificationException failure =
                assertThrows(
                        FeatureEditNotificationException.class,
                        () ->
                                session.apply(
                                        transaction(
                                                0,
                                                "replace",
                                                new ReplaceFeature("a", record("a", 2)))));
        assertEquals(1, session.snapshot().revision());
        assertSame(first, failure.getCause());
        assertEquals(List.of(second), List.of(failure.getSuppressed()));
        assertEquals(FeatureEditStatus.APPLIED, failure.committedResult().status());
    }

    @Test
    void undoNotificationFailureReportsCommittedResultAndMovesHistory() {
        FeatureEditSession session =
                FeatureEditSession.open(CrsDefinitions.EPSG_3857, List.of(record("a", 1)));
        session.apply(transaction(0, "replace", new ReplaceFeature("a", record("a", 2))));
        RuntimeException listenerFailure = new IllegalStateException("undo listener");
        session.addFeatureEditListener(
                event -> {
                    assertEquals(FeatureEditCause.UNDO, event.cause());
                    assertThrows(IllegalStateException.class, () -> session.redo(2));
                    throw listenerFailure;
                });

        FeatureEditNotificationException failure =
                assertThrows(FeatureEditNotificationException.class, () -> session.undo(1));

        assertSame(listenerFailure, failure.getCause());
        assertEquals(2, failure.committedResult().snapshot().revision());
        assertFalse(session.canUndo());
        assertTrue(session.canRedo());
    }

    @Test
    void duplicateListenerRegistrationsUseIdentityAndRemoveOnlyTheFirst() {
        FeatureEditSession session =
                FeatureEditSession.open(CrsDefinitions.EPSG_3857, List.of(record("a", 1)));
        AtomicInteger calls = new AtomicInteger();
        FeatureEditListener duplicate = event -> calls.incrementAndGet();
        session.addFeatureEditListener(duplicate);
        session.addFeatureEditListener(duplicate);
        session.removeFeatureEditListener(event -> calls.incrementAndGet());
        session.removeFeatureEditListener(duplicate);

        session.apply(transaction(0, "replace", new ReplaceFeature("a", record("a", 2))));

        assertEquals(1, calls.get());
    }

    @Test
    void everySessionOperationEnforcesTheOpeningThread() throws Exception {
        FeatureEditSession session =
                FeatureEditSession.open(CrsDefinitions.EPSG_3857, List.of(record("a", 1)));
        FeatureEditListener listener = event -> {};
        List<Runnable> operations =
                List.of(
                        session::snapshot,
                        session::limits,
                        session::historyLimits,
                        session::canUndo,
                        session::canRedo,
                        session::undoDescription,
                        session::redoDescription,
                        () ->
                                session.apply(
                                        transaction(
                                                0,
                                                "replace",
                                                new ReplaceFeature("a", record("a", 2)))),
                        () -> session.addFeatureEditListener(listener),
                        () -> session.removeFeatureEditListener(listener),
                        () -> session.undo(0),
                        () -> session.redo(0));
        AtomicInteger rejected = new AtomicInteger();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        Thread thread =
                new Thread(
                        () -> {
                            for (Runnable operation : operations) {
                                try {
                                    operation.run();
                                    unexpected.compareAndSet(
                                            null,
                                            new AssertionError(
                                                    "wrong-thread operation was accepted"));
                                } catch (IllegalStateException expected) {
                                    rejected.incrementAndGet();
                                } catch (Throwable thrown) {
                                    unexpected.compareAndSet(null, thrown);
                                }
                            }
                        });
        thread.start();
        thread.join();
        assertEquals(operations.size(), rejected.get());
        assertEquals(null, unexpected.get());
        assertFalse(session.snapshot().records().isEmpty());
    }

    private static FeatureEditTransaction transaction(
            long revision,
            String description,
            io.github.mundanej.map.api.FeatureEditCommand... commands) {
        return new FeatureEditTransaction(revision, description, List.of(commands));
    }

    private static FeatureRecord record(String id, double x) {
        return new FeatureRecord(id, id, new PointGeometry(new Coordinate(x, 0)), Map.of());
    }

    private static List<String> ids(FeatureEditSnapshot snapshot) {
        return snapshot.records().stream().map(FeatureRecord::id).toList();
    }
}
