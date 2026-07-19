package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CreateFeature;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.FeatureEditCause;
import io.github.mundanej.map.api.FeatureEditCommand;
import io.github.mundanej.map.api.FeatureEditConfigurationException;
import io.github.mundanej.map.api.FeatureEditEvent;
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

/**
 * Owner-thread in-memory feature-edit session with atomic immutable snapshot publication.
 *
 * <p>The session owns ordinary bounded state only. It does not own a source, binding, path,
 * executor, or closeable resource.
 */
public final class FeatureEditSession {
    private final Thread ownerThread;
    private final FeatureEditLimits limits;
    private final List<FeatureEditListener> listeners = new ArrayList<>();
    private FeatureEditSnapshot snapshot;
    private boolean delivering;

    private FeatureEditSession(FeatureEditSnapshot initial, FeatureEditLimits limits) {
        this.ownerThread = Thread.currentThread();
        this.limits = Objects.requireNonNull(limits, "limits");
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
        return new FeatureEditSession(
                Objects.requireNonNull(initial, "initial"),
                Objects.requireNonNull(limits, "limits"));
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
        Map<String, Integer> indexes = indexes(candidate);
        for (int commandIndex = 0; commandIndex < transaction.commands().size(); commandIndex++) {
            FeatureEditCommand command = transaction.commands().get(commandIndex);
            FeatureEditResult failure = stage(command, commandIndex, candidate, indexes);
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

        FeatureEditSnapshot previous = snapshot;
        FeatureEditSnapshot current =
                new FeatureEditSnapshot(previous.revision() + 1, previous.crs(), candidate);
        snapshot = current;
        FeatureEditResult result = FeatureEditResult.applied(current);
        notifyListeners(
                new FeatureEditEvent(
                        FeatureEditCause.COMMIT, previous, current, transaction.description()),
                result);
        return result;
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
            Map<String, Integer> indexes) {
        Integer existing = indexes.get(command.featureId());
        if (command instanceof CreateFeature create) {
            if (existing != null) {
                return commandRejected(
                        "EDIT_FEATURE_ALREADY_EXISTS",
                        "Created feature already exists",
                        commandIndex);
            }
            indexes.put(create.featureId(), candidate.size());
            candidate.add(create.feature());
            return null;
        }
        if (existing == null) {
            return commandRejected(
                    "EDIT_FEATURE_NOT_FOUND", "Edited feature does not exist", commandIndex);
        }
        if (command instanceof ReplaceFeature replace) {
            candidate.set(existing, replace.replacement());
            return null;
        }
        candidate.remove(existing.intValue());
        indexes.remove(command.featureId());
        for (int index = existing; index < candidate.size(); index++) {
            indexes.put(candidate.get(index).id(), index);
        }
        return null;
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
}
