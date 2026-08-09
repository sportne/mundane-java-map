package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;

/**
 * Explicit Vaadin binding for one synchronous feature source and one built-in vector portrayal.
 *
 * <p>A borrowed binding never closes its source. An owned binding transfers exclusive source
 * ownership and closes it exactly once when removed, when its component closes, or when the
 * unattached binding is closed. A binding can be attached to at most one {@link MundaneMap}.
 */
public final class FeatureSourceBinding implements AutoCloseable {
    private static final Object SOURCE_CLAIM_LOCK = new Object();
    private static final IdentityHashMap<FeatureSource, FeatureSourceBinding> SOURCE_CLAIMS =
            new IdentityHashMap<>();

    private final String id;
    private final String name;
    private final FeatureSource source;
    private final Symbol marker;
    private final Symbol line;
    private final Symbol fill;
    private final AttributeSelection attributes;
    private final Optional<FeatureQueryLimits> tighterLimits;
    private final boolean owned;
    private MundaneMap owner;
    private boolean closed;

    private FeatureSourceBinding(
            String id,
            String name,
            FeatureSource source,
            Symbol marker,
            Symbol line,
            Symbol fill,
            AttributeSelection attributes,
            Optional<FeatureQueryLimits> tighterLimits,
            boolean owned) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.source = Objects.requireNonNull(source, "source");
        this.marker =
                SceneProtocol.requireBuiltInSymbol(
                        marker, SymbolRole.MARKER, "binding", "marker symbol");
        this.line =
                SceneProtocol.requireBuiltInSymbol(line, SymbolRole.LINE, "binding", "line symbol");
        this.fill =
                SceneProtocol.requireBuiltInSymbol(fill, SymbolRole.FILL, "binding", "fill symbol");
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        this.tighterLimits = Objects.requireNonNull(tighterLimits, "tighterLimits");
        this.owned = owned;
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        tighterLimits.ifPresent(
                limits -> {
                    if (!limits.tightens(source.limits().queryLimits())) {
                        throw new IllegalArgumentException(
                                "Binding query limits may only tighten source limits");
                    }
                });
    }

    /**
     * Creates a binding whose source remains caller-owned.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param source open caller-owned source
     * @param marker built-in marker-role point and multipoint symbol
     * @param line built-in line-role line and multiline symbol
     * @param fill built-in fill-role polygon and multipolygon symbol
     * @param attributes exact source attributes required by the binding
     * @param tighterLimits optional per-query limits that only tighten the source limits
     * @return new unattached borrowed binding
     */
    public static FeatureSourceBinding borrowed(
            String id,
            String name,
            FeatureSource source,
            Symbol marker,
            Symbol line,
            Symbol fill,
            AttributeSelection attributes,
            Optional<FeatureQueryLimits> tighterLimits) {
        return new FeatureSourceBinding(
                id, name, source, marker, line, fill, attributes, tighterLimits, false);
    }

    /**
     * Creates a binding that assumes exclusive responsibility for closing its source.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param source open source whose ownership is transferred
     * @param marker built-in marker-role point and multipoint symbol
     * @param line built-in line-role line and multiline symbol
     * @param fill built-in fill-role polygon and multipolygon symbol
     * @param attributes exact source attributes required by the binding
     * @param tighterLimits optional per-query limits that only tighten the source limits
     * @return new unattached owned binding
     */
    public static FeatureSourceBinding owned(
            String id,
            String name,
            FeatureSource source,
            Symbol marker,
            Symbol line,
            Symbol fill,
            AttributeSelection attributes,
            Optional<FeatureQueryLimits> tighterLimits) {
        return new FeatureSourceBinding(
                id, name, source, marker, line, fill, attributes, tighterLimits, true);
    }

    /**
     * Returns the stable browser layer identity.
     *
     * @return stable layer identity
     */
    public String id() {
        return id;
    }

    /**
     * Returns the browser layer display name.
     *
     * @return layer display name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the bound source.
     *
     * @return open feature source while the binding is usable
     */
    public FeatureSource source() {
        return source;
    }

    /**
     * Returns the exact requested attribute projection.
     *
     * @return requested attributes
     */
    public AttributeSelection attributes() {
        return attributes;
    }

    /**
     * Returns the optional per-query limit tightening.
     *
     * @return optional tighter query limits
     */
    public Optional<FeatureQueryLimits> tighterLimits() {
        return tighterLimits;
    }

    /**
     * Returns whether this binding owns its source.
     *
     * @return whether source ownership was transferred
     */
    public boolean owned() {
        return owned;
    }

    /**
     * Returns whether this binding has permanently closed.
     *
     * @return whether this binding is closed
     */
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Closes an unattached binding; an owned binding also closes its source exactly once.
     *
     * @throws IllegalStateException if the binding is attached to a component
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
        if (owned) {
            source.close();
        }
    }

    Symbol marker() {
        return marker;
    }

    Symbol line() {
        return line;
    }

    Symbol fill() {
        return fill;
    }

    synchronized void attach(MundaneMap candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (closed) {
            throw new IllegalStateException("binding is closed");
        }
        if (owner != null) {
            throw new IllegalStateException("binding is already attached");
        }
        synchronized (SOURCE_CLAIM_LOCK) {
            if (source.isClosed()) {
                throw new IllegalStateException("source is closed");
            }
            FeatureSourceBinding existing = SOURCE_CLAIMS.get(source);
            if (existing != null && existing != this) {
                throw new IllegalStateException("source is already attached");
            }
            SOURCE_CLAIMS.put(source, this);
        }
        owner = candidate;
    }

    synchronized void release(MundaneMap candidate) {
        if (owner != candidate) {
            return;
        }
        owner = null;
        try {
            if (owned && !closed) {
                closed = true;
                source.close();
            }
        } finally {
            releaseSourceClaim();
        }
    }

    synchronized void detach(MundaneMap candidate) {
        if (owner == candidate) {
            owner = null;
            releaseSourceClaim();
        }
    }

    private void releaseSourceClaim() {
        synchronized (SOURCE_CLAIM_LOCK) {
            if (SOURCE_CLAIMS.get(source) == this) {
                SOURCE_CLAIMS.remove(source);
            }
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(name + " must be non-blank and at most 256 chars");
        }
        return value;
    }
}
