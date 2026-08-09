package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureEditConfigurationException;
import io.github.mundanej.map.api.FeatureEditHistoryLimits;
import io.github.mundanej.map.api.FeatureEditLimits;
import io.github.mundanej.map.api.FeatureEditListener;
import io.github.mundanej.map.api.FeatureEditNotificationException;
import io.github.mundanej.map.api.FeatureEditProblem;
import io.github.mundanej.map.api.FeatureEditResult;
import io.github.mundanej.map.api.FeatureEditSnapshot;
import io.github.mundanej.map.api.FeatureEditStatus;
import io.github.mundanej.map.api.FeatureEditTransaction;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import io.github.mundanej.map.core.InMemoryLayer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Vaadin binding that owns one fixed-lane bounded feature-edit session and closed portrayal.
 *
 * <p>The private owner lane preserves {@link FeatureEditSession}'s strict thread-affinity contract
 * across Flow requests and asynchronous UI access. A binding may be installed in at most one {@link
 * MundaneMap}; committed revisions are synchronously republished before mutation returns.
 */
public final class FeatureEditBinding implements AutoCloseable {
    private final String id;
    private final String name;
    private final ExecutorService editExecutor;
    private volatile Thread editThread;
    private final FeatureEditSession session;
    private final FeaturePortrayalResolver portrayal;
    private final Set<RasterIconSymbol> authorizedIcons;
    private volatile MundaneMap owner;
    private volatile long publishedRevision = -1;
    private volatile boolean closed;

    /**
     * Opens an adapter-owned editable binding with default edit and history limits.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param initial complete immutable initial snapshot
     * @param portrayal closed server-side portrayal
     * @return new unattached binding
     */
    public static FeatureEditBinding open(
            String id, String name, FeatureEditSnapshot initial, FeaturePortrayal portrayal) {
        return open(
                id,
                name,
                initial,
                FeatureEditLimits.DEFAULT,
                FeatureEditHistoryLimits.DEFAULT,
                portrayal,
                NamedSymbolCatalog.of(List.of()));
    }

    /**
     * Opens an adapter-owned editable binding with explicit bounded limits and icon catalog.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param initial complete immutable initial snapshot
     * @param limits current-snapshot and transaction limits
     * @param historyLimits combined undo/redo history limits
     * @param portrayal closed server-side portrayal
     * @param catalog exact raster-icon instances authorized for browser publication
     * @return new unattached binding
     */
    public static FeatureEditBinding open(
            String id,
            String name,
            FeatureEditSnapshot initial,
            FeatureEditLimits limits,
            FeatureEditHistoryLimits historyLimits,
            FeaturePortrayal portrayal,
            NamedSymbolCatalog catalog) {
        return new FeatureEditBinding(id, name, initial, limits, historyLimits, portrayal, catalog);
    }

    private FeatureEditBinding(
            String id,
            String name,
            FeatureEditSnapshot initial,
            FeatureEditLimits limits,
            FeatureEditHistoryLimits historyLimits,
            FeaturePortrayal portrayal,
            NamedSymbolCatalog catalog) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(historyLimits, "historyLimits");
        FeaturePortrayalResolver compiledPortrayal =
                FeaturePortrayalResolver.compile(Objects.requireNonNull(portrayal, "portrayal"));
        Objects.requireNonNull(catalog, "catalog");
        Set<RasterIconSymbol> catalogIcons =
                FeatureSourceBinding.identityIconSet(
                        catalog.entries().stream().map(value -> value.symbol()).toList());
        for (Symbol symbol : compiledPortrayal.reachableSymbols()) {
            SceneProtocol.requirePortrayalSymbol(
                    symbol, symbol.role(), catalogIcons::contains, "binding");
        }
        Set<RasterIconSymbol> selected =
                FeatureSourceBinding.identityIconSet(compiledPortrayal.reachableSymbols());
        selected.retainAll(catalogIcons);
        this.portrayal = compiledPortrayal;
        authorizedIcons = Collections.unmodifiableSet(selected);
        editExecutor =
                Executors.newSingleThreadExecutor(
                        action -> {
                            Thread thread = new Thread(action, "mundane-map-edit-" + this.id);
                            thread.setDaemon(true);
                            editThread = thread;
                            return thread;
                        });
        try {
            session = call(() -> FeatureEditSession.open(initial, limits, historyLimits));
        } catch (RuntimeException | Error failure) {
            editExecutor.shutdown();
            throw failure;
        }
    }

    /**
     * Returns the stable browser layer identity.
     *
     * @return stable identity
     */
    public String id() {
        return id;
    }

    /**
     * Returns the browser layer display name.
     *
     * @return display name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the authoritative immutable edit snapshot from the private owner lane.
     *
     * @return current immutable snapshot
     */
    public FeatureEditSnapshot snapshot() {
        requireOpen();
        return call(session::snapshot);
    }

    /**
     * Returns whether this binding has permanently closed.
     *
     * @return whether closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Adds an edit listener invoked synchronously on the binding's private owner lane.
     *
     * @param listener listener to add
     */
    public void addFeatureEditListener(FeatureEditListener listener) {
        requireOpen();
        call(
                () -> {
                    session.addFeatureEditListener(listener);
                    return null;
                });
    }

    /**
     * Removes the first identical edit-listener registration.
     *
     * @param listener listener instance to remove
     */
    public void removeFeatureEditListener(FeatureEditListener listener) {
        requireOpen();
        call(
                () -> {
                    session.removeFeatureEditListener(listener);
                    return null;
                });
    }

    /**
     * Closes an unattached binding and its private owner lane.
     *
     * @throws IllegalStateException if installed in a component
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (owner != null) {
            throw new IllegalStateException("binding is attached");
        }
        closed = true;
        editExecutor.shutdown();
    }

    FeaturePortrayalResolver portrayal() {
        return portrayal;
    }

    boolean authorizes(RasterIconSymbol icon) {
        return authorizedIcons.contains(icon);
    }

    FeatureEditResult apply(FeatureEditTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        return mutate(() -> session.apply(transaction));
    }

    FeatureEditResult undo(long expectedRevision) {
        return mutate(() -> session.undo(expectedRevision));
    }

    FeatureEditResult redo(long expectedRevision) {
        return mutate(() -> session.redo(expectedRevision));
    }

    synchronized void attach(MundaneMap candidate) {
        Objects.requireNonNull(candidate, "candidate");
        requireOpen();
        if (owner != null && owner != candidate) {
            throw new IllegalStateException("binding is attached to another component");
        }
        owner = candidate;
    }

    synchronized void detach(MundaneMap candidate) {
        if (owner == candidate) {
            owner = null;
            publishedRevision = -1;
        }
    }

    void markPublished(MundaneMap candidate) {
        if (owner == candidate) {
            publishedRevision = snapshot().revision();
        }
    }

    boolean isPublishedRevision(long revision) {
        return publishedRevision == revision;
    }

    Layer layer(MundaneMap map) {
        FeatureEditSnapshot current = snapshot();
        PortrayalEvaluationContext context =
                FeatureSourceQueryEngine.portrayalContext(map.viewport(), map.displayCrs());
        List<Feature> features =
                current.records().stream()
                        .map(record -> feature(map, current, record, context))
                        .flatMap(Optional::stream)
                        .toList();
        return new InMemoryLayer(id, name, features);
    }

    private Optional<Feature> feature(
            MundaneMap map,
            FeatureEditSnapshot current,
            FeatureRecord record,
            PortrayalEvaluationContext context) {
        io.github.mundanej.map.api.Geometry displayGeometry =
                FeatureSourceQueryEngine.transformGeometry(
                        record.geometry(),
                        map.crsOperation(current.crs(), map.mapCrs()),
                        map.crsOperation(map.mapCrs(), map.displayCrs()),
                        CancellationToken.none());
        return portrayal
                .resolveAll(
                        record.attributes(),
                        context.withGeometryType(
                                io.github.mundanej.map.api.PortrayalGeometryType.fromGeometry(
                                        displayGeometry)))
                .forRole(FeatureSourceQueryEngine.role(displayGeometry))
                .map(
                        symbol ->
                                new Feature(
                                        record.id(),
                                        record.name(),
                                        displayGeometry,
                                        record.attributes(),
                                        symbol));
    }

    private FeatureEditResult mutate(Callable<FeatureEditResult> operation) {
        requireOpen();
        try {
            FeatureEditResult result = call(operation);
            publishCommitted(result);
            return result;
        } catch (FeatureEditNotificationException failure) {
            publishCommitted(failure.committedResult());
            throw failure;
        }
    }

    private void publishCommitted(FeatureEditResult result) {
        if (result.status() == FeatureEditStatus.APPLIED && owner != null) {
            owner.acceptFeatureEdit(this);
        }
    }

    private <T> T call(Callable<T> operation) {
        if (Thread.currentThread() == editThread) {
            try {
                return operation.call();
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new AssertionError("Unexpected checked failure", failure);
            }
        }
        try {
            return editExecutor.submit(operation).get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for edit owner lane", failure);
        } catch (ExecutionException failure) {
            throwUnchecked(failure.getCause());
            throw new AssertionError("unreachable");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("binding is closed");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(name + " must be non-blank and at most 256 chars");
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(name + " must not contain control characters");
            }
        }
        return value;
    }

    static void requireExactCrs(
            io.github.mundanej.map.api.CrsDefinition expected,
            io.github.mundanej.map.api.CrsDefinition actual) {
        if (!expected.equals(actual)) {
            throw new FeatureEditConfigurationException(
                    new FeatureEditProblem(
                            "EDIT_CRS_MISMATCH",
                            "Point-edit CRS does not match the map component",
                            Map.of(
                                    "expectedCrs",
                                    expected.canonicalIdentifier(),
                                    "actualCrs",
                                    actual.canonicalIdentifier())));
        }
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new AssertionError("Unexpected checked failure", failure);
    }
}
