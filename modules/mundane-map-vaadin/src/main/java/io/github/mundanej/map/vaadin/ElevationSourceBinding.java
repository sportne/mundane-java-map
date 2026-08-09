package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.RasterRequestLimits;
import java.util.IdentityHashMap;
import java.util.Objects;

/** Explicit owned or borrowed browser binding for one elevation source. */
public final class ElevationSourceBinding implements AutoCloseable {
    private static final Object SOURCE_CLAIM_LOCK = new Object();
    private static final IdentityHashMap<ElevationSource, ElevationSourceBinding> SOURCE_CLAIMS =
            new IdentityHashMap<>();

    private final String id;
    private final String name;
    private final ElevationSource source;
    private final ElevationRasterStyle style;
    private final BrowserRasterOptions options;
    private final RasterRequestLimits requestLimits;
    private final boolean owned;
    private MundaneMap owner;
    private boolean closed;

    private ElevationSourceBinding(
            String id,
            String name,
            ElevationSource source,
            ElevationRasterStyle style,
            BrowserRasterOptions options,
            RasterRequestLimits requestLimits,
            boolean owned) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.source = Objects.requireNonNull(source, "source");
        this.style = Objects.requireNonNull(style, "style");
        this.options = Objects.requireNonNull(options, "options");
        this.requestLimits = Objects.requireNonNull(requestLimits, "requestLimits");
        this.owned = owned;
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        if (style.colorRamp().unit() != source.metadata().elevationUnit()) {
            throw new IllegalArgumentException("color-ramp unit must equal source elevation unit");
        }
    }

    /**
     * Creates a caller-owned elevation binding.
     *
     * @param id stable browser layer identity
     * @param name non-blank display name
     * @param source open caller-owned source
     * @param style server-side colorization and hillshade style
     * @param options immutable presentation options
     * @param requestLimits complete browser rasterization ceilings
     * @return new unattached binding
     */
    public static ElevationSourceBinding borrowed(
            String id,
            String name,
            ElevationSource source,
            ElevationRasterStyle style,
            BrowserRasterOptions options,
            RasterRequestLimits requestLimits) {
        return new ElevationSourceBinding(id, name, source, style, options, requestLimits, false);
    }

    /**
     * Creates an exclusively owned elevation binding.
     *
     * @param id stable browser layer identity
     * @param name non-blank display name
     * @param source open source whose ownership is transferred
     * @param style server-side colorization and hillshade style
     * @param options immutable presentation options
     * @param requestLimits complete browser rasterization ceilings
     * @return new unattached binding
     */
    public static ElevationSourceBinding owned(
            String id,
            String name,
            ElevationSource source,
            ElevationRasterStyle style,
            BrowserRasterOptions options,
            RasterRequestLimits requestLimits) {
        return new ElevationSourceBinding(id, name, source, style, options, requestLimits, true);
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
     * Returns the bound elevation source.
     *
     * @return source
     */
    public ElevationSource source() {
        return source;
    }

    /**
     * Returns the server-side colorization and hillshade style.
     *
     * @return style
     */
    public ElevationRasterStyle style() {
        return style;
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
     * Returns the complete browser rasterization ceilings.
     *
     * @return request limits
     */
    public RasterRequestLimits requestLimits() {
        return requestLimits;
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

    synchronized void attach(MundaneMap candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (closed || owner != null) {
            throw new IllegalStateException(closed ? "binding is closed" : "binding is attached");
        }
        synchronized (SOURCE_CLAIM_LOCK) {
            if (source.isClosed()) {
                throw new IllegalStateException("source is closed");
            }
            ElevationSourceBinding existing = SOURCE_CLAIMS.get(source);
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
