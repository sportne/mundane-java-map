package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CreateFeature;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.FeatureEditCause;
import io.github.mundanej.map.api.FeatureEditCommand;
import io.github.mundanej.map.api.FeatureEditConfigurationException;
import io.github.mundanej.map.api.FeatureEditEvent;
import io.github.mundanej.map.api.FeatureEditHistoryLimits;
import io.github.mundanej.map.api.FeatureEditLimits;
import io.github.mundanej.map.api.FeatureEditListener;
import io.github.mundanej.map.api.FeatureEditNotificationException;
import io.github.mundanej.map.api.FeatureEditProblem;
import io.github.mundanej.map.api.FeatureEditResult;
import io.github.mundanej.map.api.FeatureEditSnapshot;
import io.github.mundanej.map.api.FeatureEditTransaction;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.ReplaceFeature;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Owner-thread in-memory feature-edit session with atomic immutable snapshot publication.
 *
 * <p>The session owns ordinary bounded state only. It does not own a source, binding, path,
 * executor, or closeable resource.
 */
public final class FeatureEditSession {
    private final Thread ownerThread;
    private final FeatureEditLimits limits;
    private final FeatureEditHistoryLimits historyLimits;
    private final List<FeatureEditListener> listeners = new ArrayList<>();
    private List<HistoryEntry> undoEntries = List.of();
    private List<HistoryEntry> redoEntries = List.of();
    private FeatureEditSnapshot snapshot;
    private boolean delivering;

    private FeatureEditSession(
            FeatureEditSnapshot initial,
            FeatureEditLimits limits,
            FeatureEditHistoryLimits historyLimits) {
        this.ownerThread = Thread.currentThread();
        this.limits = Objects.requireNonNull(limits, "limits");
        this.historyLimits = Objects.requireNonNull(historyLimits, "historyLimits");
        validateCandidate(initial, true);
        this.snapshot = initial;
    }

    /**
     * Opens a bounded session from an immutable snapshot on the current owner thread.
     *
     * @param initial complete initial snapshot
     * @param limits immutable session limits
     * @return new owner-thread session with empty listener state
     * @throws FeatureEditConfigurationException if the initial snapshot exceeds a limit
     */
    public static FeatureEditSession open(FeatureEditSnapshot initial, FeatureEditLimits limits) {
        return open(initial, limits, FeatureEditHistoryLimits.DEFAULT);
    }

    /**
     * Opens a bounded session with explicit current-snapshot and history ceilings.
     *
     * @param initial complete initial snapshot
     * @param limits immutable session limits
     * @param historyLimits immutable combined undo/redo limits
     * @return new owner-thread session with empty history and listener state
     * @throws FeatureEditConfigurationException if the initial snapshot exceeds a limit
     */
    public static FeatureEditSession open(
            FeatureEditSnapshot initial,
            FeatureEditLimits limits,
            FeatureEditHistoryLimits historyLimits) {
        return new FeatureEditSession(
                Objects.requireNonNull(initial, "initial"),
                Objects.requireNonNull(limits, "limits"),
                Objects.requireNonNull(historyLimits, "historyLimits"));
    }

    /**
     * Opens a revision-zero session using default limits.
     *
     * @param crs exact CRS for every record
     * @param records ordered immutable records
     * @return new owner-thread session
     */
    public static FeatureEditSession open(CrsDefinition crs, List<FeatureRecord> records) {
        return open(new FeatureEditSnapshot(0, crs, records), FeatureEditLimits.DEFAULT);
    }

    /**
     * Returns the authoritative immutable snapshot.
     *
     * @return current snapshot
     * @throws IllegalStateException when called from another thread
     */
    public FeatureEditSnapshot snapshot() {
        requireOwner();
        return snapshot;
    }

    /**
     * Returns the fixed session limits.
     *
     * @return immutable limits
     * @throws IllegalStateException when called from another thread
     */
    public FeatureEditLimits limits() {
        requireOwner();
        return limits;
    }

    /**
     * Returns the fixed combined undo/redo limits.
     *
     * @return immutable history limits
     * @throws IllegalStateException when called from another thread
     */
    public FeatureEditHistoryLimits historyLimits() {
        requireOwner();
        return historyLimits;
    }

    /**
     * Returns whether the most recent retained transaction can be undone.
     *
     * @return {@code true} when undo history is non-empty
     * @throws IllegalStateException when called from another thread
     */
    public boolean canUndo() {
        requireOwner();
        return !undoEntries.isEmpty();
    }

    /**
     * Returns whether the most recently undone transaction can be redone.
     *
     * @return {@code true} when redo history is non-empty
     * @throws IllegalStateException when called from another thread
     */
    public boolean canRedo() {
        requireOwner();
        return !redoEntries.isEmpty();
    }

    /**
     * Returns the exact description of the next transaction to undo, if any.
     *
     * @return next undo description, or empty when no retained transaction can be undone
     * @throws IllegalStateException when called from another thread
     */
    public Optional<String> undoDescription() {
        requireOwner();
        return undoEntries.isEmpty()
                ? Optional.empty()
                : Optional.of(undoEntries.getLast().description());
    }

    /**
     * Returns the exact description of the next transaction to redo, if any.
     *
     * @return next redo description, or empty when no transaction can be redone
     * @throws IllegalStateException when called from another thread
     */
    public Optional<String> redoDescription() {
        requireOwner();
        return redoEntries.isEmpty()
                ? Optional.empty()
                : Optional.of(redoEntries.getLast().description());
    }

    /**
     * Applies one atomic transaction.
     *
     * @param transaction immutable transaction
     * @return applied, unchanged, or rejected authoritative result
     * @throws FeatureEditNotificationException after commit when listeners fail
     * @throws IllegalStateException on the wrong thread or during listener delivery
     */
    public FeatureEditResult apply(FeatureEditTransaction transaction) {
        requireOwner();
        if (delivering) {
            throw new IllegalStateException("edit mutation is not allowed during notification");
        }
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.expectedRevision() != snapshot.revision()) {
            return rejected(
                    "EDIT_REVISION_CONFLICT",
                    "Edit transaction revision does not match current state",
                    Map.of(
                            "expectedRevision", Long.toString(transaction.expectedRevision()),
                            "actualRevision", Long.toString(snapshot.revision())));
        }
        if (transaction.commands().size() > limits.maximumCommandsPerTransaction()) {
            return rejected(
                    "EDIT_COMMAND_LIMIT_EXCEEDED",
                    "Edit transaction command limit exceeded",
                    Map.of(
                            "maximum",
                            Integer.toString(limits.maximumCommandsPerTransaction()),
                            "actual",
                            Integer.toString(transaction.commands().size())));
        }

        List<FeatureRecord> candidate = new ArrayList<>(snapshot.records());
        List<HistoryDelta> deltas = new ArrayList<>(transaction.commands().size());
        Map<String, Integer> indexes = indexes(candidate);
        for (int commandIndex = 0; commandIndex < transaction.commands().size(); commandIndex++) {
            FeatureEditCommand command = transaction.commands().get(commandIndex);
            FeatureEditResult failure = stage(command, commandIndex, candidate, indexes, deltas);
            if (failure != null) {
                return failure;
            }
        }
        if (candidate.equals(snapshot.records())) {
            return FeatureEditResult.unchanged(snapshot);
        }
        if (candidate.size() > limits.maximumFeatures()) {
            return rejected(
                    "EDIT_FEATURE_LIMIT_EXCEEDED",
                    "Edit snapshot feature limit exceeded",
                    Map.of(
                            "maximum", Integer.toString(limits.maximumFeatures()),
                            "actual", Integer.toString(candidate.size())));
        }
        long bytes = snapshotBytes(candidate);
        if (bytes > limits.maximumSnapshotBytes()) {
            return rejected(
                    "EDIT_SNAPSHOT_LIMIT_EXCEEDED",
                    "Edit snapshot logical payload limit exceeded",
                    Map.of(
                            "maximum", Long.toString(limits.maximumSnapshotBytes()),
                            "actual", Long.toString(bytes)));
        }
        if (snapshot.revision() == Long.MAX_VALUE) {
            return rejected(
                    "EDIT_REVISION_EXHAUSTED",
                    "Edit session revision is exhausted",
                    Map.of("actualRevision", Long.toString(snapshot.revision())));
        }

        HistoryEntry entry = historyEntry(transaction.description(), deltas);
        if (entry.bytes() > historyLimits.maximumBytes()) {
            return rejected(
                    "EDIT_HISTORY_ENTRY_LIMIT_EXCEEDED",
                    "Edit transaction history entry limit exceeded",
                    Map.of(
                            "maximum", Long.toString(historyLimits.maximumBytes()),
                            "actual", Long.toString(entry.bytes())));
        }
        List<HistoryEntry> retainedUndo = historyAfterCommit(entry);

        FeatureEditSnapshot previous = snapshot;
        FeatureEditSnapshot current =
                new FeatureEditSnapshot(previous.revision() + 1, previous.crs(), candidate);
        snapshot = current;
        undoEntries = retainedUndo;
        redoEntries = List.of();
        FeatureEditResult result = FeatureEditResult.applied(current);
        notifyListeners(
                new FeatureEditEvent(
                        FeatureEditCause.COMMIT, previous, current, transaction.description()),
                result);
        return result;
    }

    /**
     * Atomically undoes the most recent retained transaction.
     *
     * @param expectedRevision exact current session revision
     * @return applied result or a stable revision/empty-history rejection
     * @throws FeatureEditNotificationException after commit when listeners fail
     * @throws IllegalStateException on the wrong thread or during listener delivery
     */
    public FeatureEditResult undo(long expectedRevision) {
        return replay(expectedRevision, true);
    }

    /**
     * Atomically reapplies the most recently undone transaction.
     *
     * @param expectedRevision exact current session revision
     * @return applied result or a stable revision/empty-history rejection
     * @throws FeatureEditNotificationException after commit when listeners fail
     * @throws IllegalStateException on the wrong thread or during listener delivery
     */
    public FeatureEditResult redo(long expectedRevision) {
        return replay(expectedRevision, false);
    }

    /**
     * Adds a listener registration; duplicate identities are retained.
     *
     * @param listener non-null synchronous listener
     */
    public void addFeatureEditListener(FeatureEditListener listener) {
        requireOwner();
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Removes the first identical listener registration, if present.
     *
     * @param listener non-null listener identity
     */
    public void removeFeatureEditListener(FeatureEditListener listener) {
        requireOwner();
        Objects.requireNonNull(listener, "listener");
        for (int index = 0; index < listeners.size(); index++) {
            if (listeners.get(index) == listener) {
                listeners.remove(index);
                return;
            }
        }
    }

    private FeatureEditResult stage(
            FeatureEditCommand command,
            int commandIndex,
            List<FeatureRecord> candidate,
            Map<String, Integer> indexes,
            List<HistoryDelta> deltas) {
        Integer existing = indexes.get(command.featureId());
        if (command instanceof CreateFeature create) {
            if (existing != null) {
                return commandRejected(
                        "EDIT_FEATURE_ALREADY_EXISTS",
                        "Created feature already exists",
                        commandIndex);
            }
            deltas.add(
                    new HistoryDelta(DeltaKind.CREATE, candidate.size(), null, create.feature()));
            indexes.put(create.featureId(), candidate.size());
            candidate.add(create.feature());
            return null;
        }
        if (existing == null) {
            return commandRejected(
                    "EDIT_FEATURE_NOT_FOUND", "Edited feature does not exist", commandIndex);
        }
        if (command instanceof ReplaceFeature replace) {
            deltas.add(
                    new HistoryDelta(
                            DeltaKind.REPLACE,
                            existing,
                            candidate.get(existing),
                            replace.replacement()));
            candidate.set(existing, replace.replacement());
            return null;
        }
        deltas.add(new HistoryDelta(DeltaKind.DELETE, existing, candidate.get(existing), null));
        candidate.remove(existing.intValue());
        indexes.remove(command.featureId());
        for (int index = existing; index < candidate.size(); index++) {
            indexes.put(candidate.get(index).id(), index);
        }
        return null;
    }

    private FeatureEditResult replay(long expectedRevision, boolean undo) {
        requireOwner();
        if (delivering) {
            throw new IllegalStateException("edit mutation is not allowed during notification");
        }
        if (expectedRevision != snapshot.revision()) {
            return rejected(
                    "EDIT_REVISION_CONFLICT",
                    "Edit history revision does not match current state",
                    Map.of(
                            "expectedRevision", Long.toString(expectedRevision),
                            "actualRevision", Long.toString(snapshot.revision())));
        }
        List<HistoryEntry> source = undo ? undoEntries : redoEntries;
        if (source.isEmpty()) {
            return rejected(
                    undo ? "EDIT_NOTHING_TO_UNDO" : "EDIT_NOTHING_TO_REDO",
                    undo ? "No retained edit is available to undo" : "No edit is available to redo",
                    Map.of());
        }
        if (snapshot.revision() == Long.MAX_VALUE) {
            return rejected(
                    "EDIT_REVISION_EXHAUSTED",
                    "Edit session revision is exhausted",
                    Map.of("actualRevision", Long.toString(snapshot.revision())));
        }

        HistoryEntry entry = source.getLast();
        List<FeatureRecord> candidate = new ArrayList<>(snapshot.records());
        replay(entry, candidate, undo);
        validateReplayCandidate(candidate);

        List<HistoryEntry> nextSource = withoutLast(source);
        List<HistoryEntry> target = undo ? redoEntries : undoEntries;
        List<HistoryEntry> nextTarget = appended(target, entry);
        FeatureEditSnapshot previous = snapshot;
        FeatureEditSnapshot current =
                new FeatureEditSnapshot(previous.revision() + 1, previous.crs(), candidate);
        snapshot = current;
        if (undo) {
            undoEntries = nextSource;
            redoEntries = nextTarget;
        } else {
            redoEntries = nextSource;
            undoEntries = nextTarget;
        }
        FeatureEditResult result = FeatureEditResult.applied(current);
        notifyListeners(
                new FeatureEditEvent(
                        undo ? FeatureEditCause.UNDO : FeatureEditCause.REDO,
                        previous,
                        current,
                        entry.description()),
                result);
        return result;
    }

    private FeatureEditResult commandRejected(String code, String message, int commandIndex) {
        return rejected(code, message, Map.of("commandIndex", Integer.toString(commandIndex)));
    }

    private FeatureEditResult rejected(String code, String message, Map<String, String> context) {
        return FeatureEditResult.rejected(snapshot, new FeatureEditProblem(code, message, context));
    }

    private void validateCandidate(FeatureEditSnapshot candidate, boolean opening) {
        if (candidate.records().size() > limits.maximumFeatures()) {
            throw configuration(
                    "EDIT_FEATURE_LIMIT_EXCEEDED",
                    "Initial edit snapshot feature limit exceeded",
                    limits.maximumFeatures(),
                    candidate.records().size());
        }
        long bytes = snapshotBytes(candidate.records());
        if (bytes > limits.maximumSnapshotBytes()) {
            throw configuration(
                    "EDIT_SNAPSHOT_LIMIT_EXCEEDED",
                    opening
                            ? "Initial edit snapshot logical payload limit exceeded"
                            : "Edit snapshot logical payload limit exceeded",
                    limits.maximumSnapshotBytes(),
                    bytes);
        }
    }

    private static FeatureEditConfigurationException configuration(
            String code, String message, long maximum, long actual) {
        return new FeatureEditConfigurationException(
                new FeatureEditProblem(
                        code,
                        message,
                        Map.of(
                                "maximum", Long.toString(maximum),
                                "actual", Long.toString(actual))));
    }

    private static Map<String, Integer> indexes(List<FeatureRecord> records) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < records.size(); index++) {
            result.put(records.get(index).id(), index);
        }
        return result;
    }

    private static long snapshotBytes(List<FeatureRecord> records) {
        long total = 24;
        try {
            for (FeatureRecord record : records) {
                total = Math.addExact(total, FeatureRecordLogicalSize.bytes(record, 1));
            }
            return total;
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static HistoryEntry historyEntry(String description, List<HistoryDelta> deltas) {
        long bytes = 24;
        try {
            bytes = Math.addExact(bytes, Math.multiplyExact(description.length(), 2L));
            for (HistoryDelta delta : deltas) {
                bytes = Math.addExact(bytes, 13);
                if (delta.before() != null) {
                    bytes = Math.addExact(bytes, 8);
                    bytes = Math.addExact(bytes, FeatureRecordLogicalSize.bytes(delta.before(), 0));
                }
                if (delta.after() != null) {
                    bytes = Math.addExact(bytes, 8);
                    bytes = Math.addExact(bytes, FeatureRecordLogicalSize.bytes(delta.after(), 0));
                }
            }
        } catch (ArithmeticException ignored) {
            bytes = Long.MAX_VALUE;
        }
        return new HistoryEntry(description, List.copyOf(deltas), bytes);
    }

    private List<HistoryEntry> historyAfterCommit(HistoryEntry entry) {
        List<HistoryEntry> retained = new ArrayList<>(undoEntries);
        long retainedBytes = historyBytes(undoEntries);
        long allowedExistingBytes = historyLimits.maximumBytes() - entry.bytes();
        while (retained.size() >= historyLimits.maximumEntries()
                || retainedBytes > allowedExistingBytes) {
            HistoryEntry evicted = retained.removeFirst();
            retainedBytes -= evicted.bytes();
        }
        retained.add(entry);
        return List.copyOf(retained);
    }

    private static long historyBytes(List<HistoryEntry> entries) {
        long bytes = 0;
        for (HistoryEntry entry : entries) {
            bytes = Math.addExact(bytes, entry.bytes());
        }
        return bytes;
    }

    private static void replay(HistoryEntry entry, List<FeatureRecord> candidate, boolean undo) {
        if (undo) {
            for (int index = entry.deltas().size() - 1; index >= 0; index--) {
                replayDelta(entry.deltas().get(index), candidate, true);
            }
            return;
        }
        for (HistoryDelta delta : entry.deltas()) {
            replayDelta(delta, candidate, false);
        }
    }

    private static void replayDelta(
            HistoryDelta delta, List<FeatureRecord> candidate, boolean undo) {
        FeatureRecord expected = undo ? delta.after() : delta.before();
        FeatureRecord replacement = undo ? delta.before() : delta.after();
        boolean removes =
                undo ? delta.kind() == DeltaKind.CREATE : delta.kind() == DeltaKind.DELETE;
        boolean inserts =
                undo ? delta.kind() == DeltaKind.DELETE : delta.kind() == DeltaKind.CREATE;
        int position = delta.position();
        if (inserts) {
            if (position < 0 || position > candidate.size() || replacement == null) {
                throw new IllegalStateException("invalid retained edit insertion delta");
            }
            candidate.add(position, replacement);
        } else if (removes) {
            if (position < 0
                    || position >= candidate.size()
                    || expected == null
                    || !candidate.get(position).equals(expected)) {
                throw new IllegalStateException("invalid retained edit removal delta");
            }
            candidate.remove(position);
        } else {
            if (position < 0
                    || position >= candidate.size()
                    || expected == null
                    || replacement == null
                    || !candidate.get(position).equals(expected)) {
                throw new IllegalStateException("invalid retained edit replacement delta");
            }
            candidate.set(position, replacement);
        }
    }

    private void validateReplayCandidate(List<FeatureRecord> candidate) {
        try {
            if (candidate.size() > limits.maximumFeatures()
                    || snapshotBytes(candidate) > limits.maximumSnapshotBytes()) {
                throw new IllegalStateException("retained edit replay exceeds session limits");
            }
            new FeatureEditSnapshot(0, snapshot.crs(), candidate);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("retained edit replay is invalid", failure);
        }
    }

    private static List<HistoryEntry> withoutLast(List<HistoryEntry> entries) {
        return List.copyOf(entries.subList(0, entries.size() - 1));
    }

    private static List<HistoryEntry> appended(List<HistoryEntry> entries, HistoryEntry entry) {
        List<HistoryEntry> result = new ArrayList<>(entries.size() + 1);
        result.addAll(entries);
        result.add(entry);
        return List.copyOf(result);
    }

    private void notifyListeners(FeatureEditEvent event, FeatureEditResult result) {
        List<FeatureEditListener> registrations = List.copyOf(listeners);
        List<RuntimeException> failures = new ArrayList<>();
        delivering = true;
        try {
            for (FeatureEditListener listener : registrations) {
                try {
                    listener.onFeatureEdit(event);
                } catch (RuntimeException failure) {
                    failures.add(failure);
                }
            }
        } finally {
            delivering = false;
        }
        if (!failures.isEmpty()) {
            FeatureEditNotificationException notification =
                    new FeatureEditNotificationException(result, failures.getFirst());
            IdentityHashMap<RuntimeException, Boolean> seen = new IdentityHashMap<>();
            seen.put(failures.getFirst(), Boolean.TRUE);
            for (int index = 1; index < failures.size(); index++) {
                RuntimeException failure = failures.get(index);
                if (failure != notification && seen.put(failure, Boolean.TRUE) == null) {
                    notification.addSuppressed(failure);
                }
            }
            throw notification;
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("FeatureEditSession must be used by its owner thread");
        }
    }

    private enum DeltaKind {
        CREATE,
        REPLACE,
        DELETE
    }

    private record HistoryDelta(
            DeltaKind kind, int position, FeatureRecord before, FeatureRecord after) {}

    private record HistoryEntry(String description, List<HistoryDelta> deltas, long bytes) {}
}
