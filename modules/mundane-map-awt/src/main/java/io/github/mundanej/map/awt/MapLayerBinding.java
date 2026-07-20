package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.FeatureEditListener;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FillSymbol;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineSymbol;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.core.FeatureEditSession;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicit host binding for an eager layer snapshot or one synchronous source.
 *
 * <p>A borrowed source binding never closes its source. An owned binding closes the source exactly
 * once when removed from its view or when the binding is closed while unattached. A binding may be
 * attached to at most one {@link MapView} and is permanently unusable after close.
 */
public final class MapLayerBinding implements AutoCloseable {
    enum Kind {
        SNAPSHOT,
        FEATURE,
        EDITABLE,
        RASTER,
        ELEVATION
    }

    private final Kind kind;
    private final String id;
    private final String name;
    private final Layer layer;
    private final FeatureSource source;
    private final FeatureEditSession editSession;
    private final RasterSource rasterSource;
    private final ElevationSource elevationSource;
    private final MarkerSymbol marker;
    private final LineSymbol line;
    private final FillSymbol fill;
    private final FeaturePortrayalResolver portrayalResolver;
    private final RasterRenderOptions rasterOptions;
    private final ElevationRasterStyle elevationStyle;
    private final RasterRequestLimits rasterLimits;
    private final boolean owned;
    private final AtomicReference<Operation> operation = new AtomicReference<>();
    private Object owner;
    private FeatureEditListener editListener;
    private boolean closed;

    private MapLayerBinding(Layer layer) {
        this.kind = Kind.SNAPSHOT;
        this.layer = Objects.requireNonNull(layer, "layer");
        this.id = requireText(layer.id(), "layer.id");
        this.name = requireText(layer.name(), "layer.name");
        this.source = null;
        this.editSession = null;
        this.rasterSource = null;
        this.elevationSource = null;
        this.marker = null;
        this.line = null;
        this.fill = null;
        this.portrayalResolver = null;
        this.rasterOptions = null;
        this.elevationStyle = null;
        this.rasterLimits = null;
        this.owned = false;
    }

    private MapLayerBinding(Layer layer, FeaturePortrayal portrayal) {
        this.kind = Kind.SNAPSHOT;
        this.layer = Objects.requireNonNull(layer, "layer");
        this.id = requireText(layer.id(), "layer.id");
        this.name = requireText(layer.name(), "layer.name");
        this.source = null;
        this.editSession = null;
        this.rasterSource = null;
        this.elevationSource = null;
        this.marker = null;
        this.line = null;
        this.fill = null;
        this.portrayalResolver =
                FeaturePortrayalResolver.compile(Objects.requireNonNull(portrayal, "portrayal"));
        this.rasterOptions = null;
        this.elevationStyle = null;
        this.rasterLimits = null;
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
        this.editSession = null;
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        this.marker = requireRole(marker, SymbolRole.MARKER, "marker");
        this.line = requireRole(line, SymbolRole.LINE, "line");
        this.fill = requireRole(fill, SymbolRole.FILL, "fill");
        this.portrayalResolver =
                FeaturePortrayalResolver.compile(
                        FeaturePortrayal.fixed(marker, line, fill)
                                .withPointLabel(PointLabelProfile.compatibility()));
        this.rasterOptions = null;
        this.layer = null;
        this.rasterSource = null;
        this.elevationSource = null;
        this.elevationStyle = null;
        this.rasterLimits = null;
        this.owned = owned;
    }

    private MapLayerBinding(
            String id,
            String name,
            FeatureSource source,
            FeaturePortrayal portrayal,
            boolean owned) {
        this.kind = Kind.FEATURE;
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.source = Objects.requireNonNull(source, "source");
        this.editSession = null;
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        this.marker = null;
        this.line = null;
        this.fill = null;
        this.portrayalResolver =
                FeaturePortrayalResolver.compile(Objects.requireNonNull(portrayal, "portrayal"));
        this.rasterOptions = null;
        this.layer = null;
        this.rasterSource = null;
        this.elevationSource = null;
        this.elevationStyle = null;
        this.rasterLimits = null;
        this.owned = owned;
    }

    private MapLayerBinding(
            String id,
            String name,
            FeatureEditSession editSession,
            MarkerSymbol marker,
            LineSymbol line,
            FillSymbol fill) {
        this.kind = Kind.EDITABLE;
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.editSession = Objects.requireNonNull(editSession, "editSession");
        this.marker = requireRole(marker, SymbolRole.MARKER, "marker");
        this.line = requireRole(line, SymbolRole.LINE, "line");
        this.fill = requireRole(fill, SymbolRole.FILL, "fill");
        this.portrayalResolver =
                FeaturePortrayalResolver.compile(
                        FeaturePortrayal.fixed(marker, line, fill)
                                .withPointLabel(PointLabelProfile.compatibility()));
        this.layer = null;
        this.source = null;
        this.rasterSource = null;
        this.elevationSource = null;
        this.rasterOptions = null;
        this.elevationStyle = null;
        this.rasterLimits = null;
        this.owned = false;
    }

    private MapLayerBinding(
            String id, String name, FeatureEditSession editSession, FeaturePortrayal portrayal) {
        this.kind = Kind.EDITABLE;
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.editSession = Objects.requireNonNull(editSession, "editSession");
        this.marker = null;
        this.line = null;
        this.fill = null;
        this.portrayalResolver =
                FeaturePortrayalResolver.compile(Objects.requireNonNull(portrayal, "portrayal"));
        this.layer = null;
        this.source = null;
        this.rasterSource = null;
        this.elevationSource = null;
        this.rasterOptions = null;
        this.elevationStyle = null;
        this.rasterLimits = null;
        this.owned = false;
    }

    private MapLayerBinding(
            String id,
            String name,
            RasterSource source,
            RasterRenderOptions rasterOptions,
            boolean owned) {
        this.kind = Kind.RASTER;
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.rasterSource = Objects.requireNonNull(source, "source");
        this.editSession = null;
        this.elevationSource = null;
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        this.layer = null;
        this.source = null;
        this.marker = null;
        this.line = null;
        this.fill = null;
        this.portrayalResolver = null;
        this.rasterOptions = Objects.requireNonNull(rasterOptions, "rasterOptions");
        this.elevationStyle = null;
        this.rasterLimits = null;
        this.owned = owned;
    }

    private MapLayerBinding(
            String id,
            String name,
            ElevationSource source,
            ElevationRasterStyle style,
            RasterRenderOptions rasterOptions,
            RasterRequestLimits rasterLimits,
            boolean owned) {
        this.kind = Kind.ELEVATION;
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.elevationSource = Objects.requireNonNull(source, "source");
        this.editSession = null;
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        this.elevationStyle = requireUnit(style, source);
        this.rasterOptions = Objects.requireNonNull(rasterOptions, "rasterOptions");
        this.rasterLimits = Objects.requireNonNull(rasterLimits, "rasterLimits");
        this.layer = null;
        this.source = null;
        this.rasterSource = null;
        this.marker = null;
        this.line = null;
        this.fill = null;
        this.portrayalResolver = null;
        this.owned = owned;
    }

    /**
     * Creates a compatibility binding around an eager immutable layer snapshot.
     *
     * @param layer non-null eager layer snapshot
     * @return new unattached binding that never owns external resources
     * @throws NullPointerException if {@code layer} is {@code null}
     * @throws IllegalArgumentException if the layer identifier or name is blank
     */
    public static MapLayerBinding snapshot(Layer layer) {
        return new MapLayerBinding(layer);
    }

    /**
     * Creates an eager snapshot whose feature records are resolved through one binding portrayal.
     *
     * @param layer non-null eager layer snapshot supplying geometry, identity, and attributes
     * @param portrayal immutable binding-level portrayal replacing feature-owned symbols
     * @return new unattached portrayed snapshot binding
     */
    public static MapLayerBinding portrayedSnapshot(Layer layer, FeaturePortrayal portrayal) {
        return new MapLayerBinding(layer, portrayal);
    }

    /**
     * Creates a feature binding whose source remains caller-owned.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source non-null open caller-owned source
     * @param marker marker-role symbol used for point and multipoint features
     * @param line line-role symbol used for line and polygon boundaries
     * @param fill fill-role symbol used for polygon interiors
     * @return new unattached borrowed binding
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if text is blank or a symbol has the wrong role
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding borrowedFeature(
            String id,
            String name,
            FeatureSource source,
            MarkerSymbol marker,
            LineSymbol line,
            FillSymbol fill) {
        return new MapLayerBinding(id, name, source, marker, line, fill, false);
    }

    /**
     * Creates a caller-owned feature-source binding with one immutable portrayal.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source non-null open caller-owned source
     * @param portrayal immutable selectors for the rendered geometry roles
     * @return new unattached borrowed binding
     */
    public static MapLayerBinding borrowedFeature(
            String id, String name, FeatureSource source, FeaturePortrayal portrayal) {
        return new MapLayerBinding(id, name, source, portrayal, false);
    }

    /**
     * Creates a feature binding that assumes exclusive responsibility for closing its source.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source non-null open source whose ownership is transferred to this binding
     * @param marker marker-role symbol used for point and multipoint features
     * @param line line-role symbol used for line and polygon boundaries
     * @param fill fill-role symbol used for polygon interiors
     * @return new unattached owned binding
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if text is blank or a symbol has the wrong role
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding ownedFeature(
            String id,
            String name,
            FeatureSource source,
            MarkerSymbol marker,
            LineSymbol line,
            FillSymbol fill) {
        return new MapLayerBinding(id, name, source, marker, line, fill, true);
    }

    /**
     * Creates an exclusively owned feature-source binding with one immutable portrayal.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source open source transferred to the binding
     * @param portrayal immutable selectors for the rendered geometry roles
     * @return new unattached owned binding
     */
    public static MapLayerBinding ownedFeature(
            String id, String name, FeatureSource source, FeaturePortrayal portrayal) {
        return new MapLayerBinding(id, name, source, portrayal, true);
    }

    /**
     * Creates a borrowed editable binding around an owner-thread feature-edit session.
     *
     * <p>The binding observes immutable session snapshots and never closes or mutates the session.
     * Attachment requires the session owner thread and an exact view map-CRS match.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param session caller-owned feature-edit session
     * @param marker marker-role symbol for point and multipoint records
     * @param line line-role symbol for line and polygon boundaries
     * @param fill fill-role symbol for polygon interiors
     * @return new unattached borrowed editable binding
     */
    public static MapLayerBinding editableFeature(
            String id,
            String name,
            FeatureEditSession session,
            MarkerSymbol marker,
            LineSymbol line,
            FillSymbol fill) {
        return new MapLayerBinding(id, name, session, marker, line, fill);
    }

    /**
     * Creates a borrowed editable binding with one immutable portrayal.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param session caller-owned feature-edit session
     * @param portrayal immutable selectors for the rendered geometry roles
     * @return new unattached borrowed editable binding
     */
    public static MapLayerBinding editableFeature(
            String id, String name, FeatureEditSession session, FeaturePortrayal portrayal) {
        return new MapLayerBinding(id, name, session, portrayal);
    }

    /**
     * Creates a raster binding whose source remains caller-owned and uses default presentation.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source non-null open caller-owned raster source
     * @return new unattached borrowed raster binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if text is blank
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding borrowedRaster(String id, String name, RasterSource source) {
        return borrowedRaster(id, name, source, RasterRenderOptions.defaults());
    }

    /**
     * Creates a caller-owned raster binding with initial immutable presentation options.
     *
     * @param id stable layer identifier
     * @param name display name
     * @param source caller-owned raster source
     * @param options initial immutable presentation options
     * @return unattached borrowed raster binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if text is blank
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding borrowedRaster(
            String id, String name, RasterSource source, RasterRenderOptions options) {
        return new MapLayerBinding(id, name, source, options, false);
    }

    /**
     * Creates an owned raster binding using default presentation options.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source non-null open source whose ownership is transferred to this binding
     * @return new unattached owned raster binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if text is blank
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding ownedRaster(String id, String name, RasterSource source) {
        return ownedRaster(id, name, source, RasterRenderOptions.defaults());
    }

    /**
     * Creates an exclusively owned raster binding with initial presentation options.
     *
     * @param id stable layer identifier
     * @param name display name
     * @param source raster source transferred to the binding
     * @param options initial immutable presentation options
     * @return unattached owned raster binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if text is blank
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding ownedRaster(
            String id, String name, RasterSource source, RasterRenderOptions options) {
        return new MapLayerBinding(id, name, source, options, true);
    }

    /**
     * Creates a caller-owned elevation binding with default raster presentation and limits.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source open caller-owned elevation source
     * @param style immutable style whose unit matches the source
     * @return unattached borrowed elevation binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if text is blank or the style unit differs
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding borrowedElevation(
            String id, String name, ElevationSource source, ElevationRasterStyle style) {
        return borrowedElevation(
                id,
                name,
                source,
                style,
                RasterRenderOptions.defaults(),
                RasterRequestLimits.LEVEL_1);
    }

    /**
     * Creates a caller-owned elevation binding with explicit presentation and request limits.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source open caller-owned elevation source
     * @param style immutable style whose unit matches the source
     * @param options immutable rendered-color interpolation and opacity
     * @param limits complete operation limits
     * @return unattached borrowed elevation binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if text is blank or the style unit differs
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding borrowedElevation(
            String id,
            String name,
            ElevationSource source,
            ElevationRasterStyle style,
            RasterRenderOptions options,
            RasterRequestLimits limits) {
        return new MapLayerBinding(id, name, source, style, options, limits, false);
    }

    /**
     * Creates an owned elevation binding with default raster presentation and limits.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source open elevation source transferred to the binding
     * @param style immutable style whose unit matches the source
     * @return unattached owned elevation binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if text is blank or the style unit differs
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding ownedElevation(
            String id, String name, ElevationSource source, ElevationRasterStyle style) {
        return ownedElevation(
                id,
                name,
                source,
                style,
                RasterRenderOptions.defaults(),
                RasterRequestLimits.LEVEL_1);
    }

    /**
     * Creates an owned elevation binding with explicit presentation and request limits.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param source open elevation source transferred to the binding
     * @param style immutable style whose unit matches the source
     * @param options immutable rendered-color interpolation and opacity
     * @param limits complete operation limits
     * @return unattached owned elevation binding
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if text is blank or the style unit differs
     * @throws IllegalStateException if {@code source} is already closed
     */
    public static MapLayerBinding ownedElevation(
            String id,
            String name,
            ElevationSource source,
            ElevationRasterStyle style,
            RasterRenderOptions options,
            RasterRequestLimits limits) {
        return new MapLayerBinding(id, name, source, style, options, limits, true);
    }

    /**
     * Returns the stable layer identifier.
     *
     * @return non-blank identifier fixed at construction
     */
    public String id() {
        return id;
    }

    /**
     * Returns the display name.
     *
     * @return non-blank display name fixed at construction
     */
    public String name() {
        return name;
    }

    /**
     * Cancels only the currently active synchronous source operation, if any.
     *
     * <p>Cancellation is cooperative and does not close the source or binding.
     *
     * @return {@code true} only when an active operation first accepted cancellation
     */
    public boolean cancelCurrentOperation() {
        Operation current = operation.get();
        return current != null && current.cancel();
    }

    /**
     * Returns whether this binding has been permanently closed.
     *
     * @return {@code true} after close or removal of an attached owned binding
     */
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Closes an unattached binding idempotently.
     *
     * <p>An owned binding closes its source exactly once. A borrowed or snapshot binding releases
     * no external resource.
     *
     * @throws IllegalStateException if the binding is currently attached to a view
     */
    @Override
    public void close() {
        FeatureSource closeFeatureSource = null;
        RasterSource closeRasterSource = null;
        ElevationSource closeElevationSource = null;
        synchronized (this) {
            if (closed) {
                return;
            }
            if (owner != null) {
                throw new IllegalStateException("An attached binding is closed by its MapView");
            }
            closed = true;
            if (owned) {
                closeFeatureSource = source;
                closeRasterSource = rasterSource;
                closeElevationSource = elevationSource;
            }
        }
        if (closeFeatureSource != null) {
            closeFeatureSource.close();
        } else if (closeRasterSource != null) {
            closeRasterSource.close();
        } else if (closeElevationSource != null) {
            closeElevationSource.close();
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

    FeatureEditSession editSession() {
        return editSession;
    }

    boolean isEditable() {
        return kind == Kind.EDITABLE;
    }

    RasterSource rasterSource() {
        return rasterSource;
    }

    ElevationSource elevationSource() {
        return elevationSource;
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

    FeaturePortrayalResolver portrayalResolver() {
        return portrayalResolver;
    }

    RasterRenderOptions initialRasterOptions() {
        return rasterOptions;
    }

    ElevationRasterStyle initialElevationStyle() {
        return elevationStyle;
    }

    RasterRequestLimits rasterLimits() {
        return rasterLimits;
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
        if (kind == Kind.EDITABLE) {
            MapView view = (MapView) requestedOwner;
            FeatureEditListener listener = event -> view.onEditableFeatureEdit(this, event);
            try {
                editSession.addFeatureEditListener(listener);
                editListener = listener;
            } catch (RuntimeException | Error failure) {
                owner = null;
                throw failure;
            }
        }
    }

    synchronized void release(Object requestedOwner) {
        if (owner != requestedOwner) {
            throw new IllegalStateException("binding is not attached to this MapView");
        }
        if (editListener != null) {
            editSession.removeFeatureEditListener(editListener);
            editListener = null;
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

    synchronized boolean isObservingEditSession() {
        return editListener != null;
    }

    Operation beginOperation() {
        if (kind == Kind.SNAPSHOT || kind == Kind.EDITABLE) {
            throw new IllegalStateException(
                    "Snapshot and editable bindings do not have source operations");
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

    private static ElevationRasterStyle requireUnit(
            ElevationRasterStyle style, ElevationSource source) {
        Objects.requireNonNull(style, "style");
        if (style.colorRamp().unit() != source.metadata().elevationUnit()) {
            throw new IllegalArgumentException("color-ramp unit must equal source elevation unit");
        }
        return style;
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
