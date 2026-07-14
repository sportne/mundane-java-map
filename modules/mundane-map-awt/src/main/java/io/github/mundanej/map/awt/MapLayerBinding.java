package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FillSymbol;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineSymbol;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicit host binding for either an eager layer snapshot or a synchronous feature source.
 *
 * <p>A borrowed feature binding never closes its source. An owned binding closes the source exactly
 * once when removed from its view or when the binding is closed while unattached.
 */
public final class MapLayerBinding implements AutoCloseable {
    enum Kind {
        SNAPSHOT,
        FEATURE
    }

    private final Kind kind;
    private final String id;
    private final String name;
    private final Layer layer;
    private final FeatureSource source;
    private final MarkerSymbol marker;
    private final LineSymbol line;
    private final FillSymbol fill;
    private final boolean owned;
    private final AtomicReference<Operation> operation = new AtomicReference<>();
    private Object owner;
    private boolean closed;

    private MapLayerBinding(Layer layer) {
        this.kind = Kind.SNAPSHOT;
        this.layer = Objects.requireNonNull(layer, "layer");
        this.id = requireText(layer.id(), "layer.id");
        this.name = requireText(layer.name(), "layer.name");
        this.source = null;
        this.marker = null;
        this.line = null;
        this.fill = null;
        this.owned = false;
    }

    private MapLayerBinding(
            String id,
            String name,
            FeatureSource source,
            MarkerSymbol marker,
            LineSymbol line,
            FillSymbol fill,
            boolean owned) {
        this.kind = Kind.FEATURE;
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.source = Objects.requireNonNull(source, "source");
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        this.marker = requireRole(marker, SymbolRole.MARKER, "marker");
        this.line = requireRole(line, SymbolRole.LINE, "line");
        this.fill = requireRole(fill, SymbolRole.FILL, "fill");
        this.layer = null;
        this.owned = owned;
    }

    /** Creates a compatibility binding around an eager layer snapshot. */
    public static MapLayerBinding snapshot(Layer layer) {
        return new MapLayerBinding(layer);
    }

    /** Creates a feature binding whose source remains caller-owned. */
    public static MapLayerBinding borrowedFeature(
            String id,
            String name,
            FeatureSource source,
            MarkerSymbol marker,
            LineSymbol line,
            FillSymbol fill) {
        return new MapLayerBinding(id, name, source, marker, line, fill, false);
    }

    /** Creates a feature binding that assumes exclusive responsibility for closing its source. */
    public static MapLayerBinding ownedFeature(
            String id,
            String name,
            FeatureSource source,
            MarkerSymbol marker,
            LineSymbol line,
            FillSymbol fill) {
        return new MapLayerBinding(id, name, source, marker, line, fill, true);
    }

    /** Returns the stable layer identifier. */
    public String id() {
        return id;
    }

    /** Returns the display name. */
    public String name() {
        return name;
    }

    /** Cancels only the currently active synchronous source operation, if any. */
    public boolean cancelCurrentOperation() {
        Operation current = operation.get();
        return current != null && current.cancel();
    }

    /** Returns whether this binding has been permanently closed. */
    public synchronized boolean isClosed() {
        return closed;
    }

    /** Closes an unattached binding idempotently. */
    @Override
    public void close() {
        FeatureSource closeSource = null;
        synchronized (this) {
            if (closed) {
                return;
            }
            if (owner != null) {
                throw new IllegalStateException("An attached binding is closed by its MapView");
            }
            closed = true;
            if (owned) {
                closeSource = source;
            }
        }
        if (closeSource != null) {
            closeSource.close();
        }
    }

    Kind kind() {
        return kind;
    }

    Layer layer() {
        return layer;
    }

    FeatureSource source() {
        return source;
    }

    MarkerSymbol marker() {
        return marker;
    }

    LineSymbol line() {
        return line;
    }

    FillSymbol fill() {
        return fill;
    }

    boolean owned() {
        return owned;
    }

    synchronized void claim(Object requestedOwner) {
        Objects.requireNonNull(requestedOwner, "requestedOwner");
        if (closed) {
            throw new IllegalStateException("binding is closed");
        }
        if (owner != null && owner != requestedOwner) {
            throw new IllegalStateException("binding is already attached to another MapView");
        }
        owner = requestedOwner;
    }

    synchronized void release(Object requestedOwner) {
        if (owner != requestedOwner) {
            throw new IllegalStateException("binding is not attached to this MapView");
        }
        owner = null;
    }

    synchronized void closeFromOwner(Object requestedOwner) {
        if (owner != requestedOwner) {
            throw new IllegalStateException("binding is not attached to this MapView");
        }
        owner = null;
        close();
    }

    Operation beginOperation() {
        if (kind != Kind.FEATURE) {
            throw new IllegalStateException("Snapshot bindings do not have source operations");
        }
        synchronized (this) {
            if (closed || owner == null) {
                throw new IllegalStateException("binding is not attached to a live MapView");
            }
        }
        Operation created = new Operation();
        if (!operation.compareAndSet(null, created)) {
            throw new IllegalStateException("A source operation is already active");
        }
        return created;
    }

    void endOperation(Operation completed) {
        operation.compareAndSet(completed, null);
    }

    private static String requireText(String value, String role) {
        Objects.requireNonNull(value, role);
        if (value.isBlank()) {
            throw new IllegalArgumentException(role + " must not be blank");
        }
        return value;
    }

    private static <T extends Symbol> T requireRole(T symbol, SymbolRole role, String field) {
        Objects.requireNonNull(symbol, field);
        if (symbol.role() != role) {
            throw new IllegalArgumentException(field + " must have role " + role);
        }
        return symbol;
    }

    static final class Operation {
        private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.ACTIVE);
        private final CancellationToken token = () -> phase.get() == Phase.CANCELLED;

        CancellationToken token() {
            return token;
        }

        boolean cancel() {
            Phase current = phase.get();
            while (current == Phase.ACTIVE) {
                if (phase.compareAndSet(Phase.ACTIVE, Phase.CANCELLED)) {
                    return true;
                }
                current = phase.get();
            }
            return current == Phase.CANCELLED;
        }

        Phase finish(boolean successful) {
            phase.compareAndSet(Phase.ACTIVE, successful ? Phase.SUCCEEDED : Phase.FAILED);
            return phase.get();
        }
    }

    enum Phase {
        ACTIVE,
        CANCELLED,
        SUCCEEDED,
        FAILED
    }
}
