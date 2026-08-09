package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;

/** Explicit owned or borrowed browser binding for one already-opened raster source. */
public final class RasterSourceBinding implements AutoCloseable {
    private static final Object SOURCE_CLAIM_LOCK = new Object();
    private static final IdentityHashMap<RasterSource, RasterSourceBinding> SOURCE_CLAIMS =
            new IdentityHashMap<>();

    private final String id;
    private final String name;
    private final RasterSource source;
    private final BrowserRasterOptions options;
    private final Optional<RasterRequestLimits> tighterLimits;
    private final boolean owned;
    private BrowserHorizontalWrapMode horizontalWrapMode = BrowserHorizontalWrapMode.NONE;
    private MundaneMap owner;
    private boolean closed;

    private RasterSourceBinding(
            String id,
            String name,
            RasterSource source,
            BrowserRasterOptions options,
            Optional<RasterRequestLimits> tighterLimits,
            boolean owned) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.source = Objects.requireNonNull(source, "source");
        this.options = Objects.requireNonNull(options, "options");
        this.tighterLimits = Objects.requireNonNull(tighterLimits, "tighterLimits");
        this.owned = owned;
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        tighterLimits.ifPresent(
                limits -> {
                    if (!limits.tightens(source.limits().requestLimits())) {
                        throw new IllegalArgumentException(
                                "Binding request limits may only tighten source limits");
                    }
                });
    }

    /**
     * Creates a caller-owned raster binding with default presentation options.
     *
     * @param id stable browser layer identity
     * @param name non-blank display name
     * @param source open caller-owned source
     * @return new unattached binding
     */
    public static RasterSourceBinding borrowed(String id, String name, RasterSource source) {
        return borrowed(id, name, source, BrowserRasterOptions.defaults(), Optional.empty());
    }

    /**
     * Creates a caller-owned raster binding.
     *
     * @param id stable browser layer identity
     * @param name non-blank display name
     * @param source open caller-owned source
     * @param options immutable presentation options
     * @param tighterLimits optional limits that only tighten the source limits
     * @return new unattached binding
     */
    public static RasterSourceBinding borrowed(
            String id,
            String name,
            RasterSource source,
            BrowserRasterOptions options,
            Optional<RasterRequestLimits> tighterLimits) {
        return new RasterSourceBinding(id, name, source, options, tighterLimits, false);
    }

    /**
     * Creates an exclusively owned raster binding.
     *
     * @param id stable browser layer identity
     * @param name non-blank display name
     * @param source open source whose ownership is transferred
     * @param options immutable presentation options
     * @param tighterLimits optional limits that only tighten the source limits
     * @return new unattached binding
     */
    public static RasterSourceBinding owned(
            String id,
            String name,
            RasterSource source,
            BrowserRasterOptions options,
            Optional<RasterRequestLimits> tighterLimits) {
        return new RasterSourceBinding(id, name, source, options, tighterLimits, true);
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
     * Returns the bound raster source.
     *
     * @return source
     */
    public RasterSource source() {
        return source;
    }

    /**
     * Returns the immutable presentation options.
     *
     * @return options
     */
    public BrowserRasterOptions options() {
        return options;
    }

    /**
     * Returns the optional binding request-limit tightening.
     *
     * @return optional tighter limits
     */
    public Optional<RasterRequestLimits> tighterLimits() {
        return tighterLimits;
    }

    /**
     * Returns whether source ownership was transferred.
     *
     * @return whether owned
     */
    public boolean owned() {
        return owned;
    }

    /**
     * Returns the explicit horizontal display-repetition policy.
     *
     * @return current mode, initially {@link BrowserHorizontalWrapMode#NONE}
     */
    public synchronized BrowserHorizontalWrapMode horizontalWrapMode() {
        return horizontalWrapMode;
    }

    /**
     * Selects horizontal repetition before the binding is attached.
     *
     * @param mode non-null closed policy
     * @throws IllegalStateException if the binding is attached or closed
     */
    public synchronized void setHorizontalWrapMode(BrowserHorizontalWrapMode mode) {
        if (closed || owner != null) {
            throw new IllegalStateException(closed ? "binding is closed" : "binding is attached");
        }
        horizontalWrapMode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * Returns whether the binding is permanently closed.
     *
     * @return whether closed
     */
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Closes an unattached binding and, when owned, its source.
     *
     * @throws IllegalStateException if attached to a component
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

    RasterRequestLimits effectiveLimits() {
        return tighterLimits.orElseGet(() -> source.limits().requestLimits());
    }

    synchronized void attach(MundaneMap candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (closed || owner != null) {
            throw new IllegalStateException(closed ? "binding is closed" : "binding is attached");
        }
        synchronized (SOURCE_CLAIM_LOCK) {
            if (source.isClosed()) {
                throw new IllegalStateException("source is closed");
            }
            RasterSourceBinding existing = SOURCE_CLAIMS.get(source);
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
            releaseClaim();
        }
    }

    synchronized void detach(MundaneMap candidate) {
        if (owner == candidate) {
            owner = null;
            releaseClaim();
        }
    }

    private void releaseClaim() {
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
