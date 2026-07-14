package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Graphics2D;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Paint-call-scoped facade supplied to explicitly registered AWT symbol renderers.
 *
 * <p>The context and every graphics copy created from it are valid only for the current renderer
 * call. Renderers must not retain either after returning.
 */
public final class AwtSymbolRenderContext {
    private final Graphics2D graphics;
    private final SymbolRole role;
    private final String featureId;
    private final Geometry featureGeometry;
    private final Geometry renderGeometry;
    private final CrsOperation mapToDisplay;
    private final MapViewport viewport;
    private final double inheritedOpacity;
    private final boolean closedRing;
    private final OptionalDouble endpointBearingDegrees;
    private final Optional<Coordinate> markerAnchorScreen;
    private final MapScreenBasis basis;
    private final MapView owner;

    AwtSymbolRenderContext(
            Graphics2D graphics,
            SymbolRole role,
            String featureId,
            Geometry featureGeometry,
            Geometry renderGeometry,
            CrsOperation mapToDisplay,
            MapViewport viewport,
            double inheritedOpacity,
            boolean closedRing,
            OptionalDouble endpointBearingDegrees,
            Optional<Coordinate> markerAnchorScreen,
            MapScreenBasis basis,
            MapView owner) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        this.role = Objects.requireNonNull(role, "role");
        this.featureId = Objects.requireNonNull(featureId, "featureId");
        this.featureGeometry = Objects.requireNonNull(featureGeometry, "featureGeometry");
        this.renderGeometry = Objects.requireNonNull(renderGeometry, "renderGeometry");
        this.mapToDisplay = Objects.requireNonNull(mapToDisplay, "mapToDisplay");
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.inheritedOpacity = inheritedOpacity;
        this.closedRing = closedRing;
        this.endpointBearingDegrees =
                Objects.requireNonNull(endpointBearingDegrees, "endpointBearingDegrees");
        this.markerAnchorScreen = Objects.requireNonNull(markerAnchorScreen, "markerAnchorScreen");
        this.basis = Objects.requireNonNull(basis, "basis");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /** Returns the looked-up role. */
    public SymbolRole role() {
        return role;
    }

    /** Returns the feature identifier. */
    public String featureId() {
        return featureId;
    }

    /** Returns the original feature geometry. */
    public Geometry featureGeometry() {
        return featureGeometry;
    }

    /** Returns the geometry for this renderer invocation. */
    public Geometry renderGeometry() {
        return renderGeometry;
    }

    /** Returns the resolved map-to-display operation snapshot. */
    public CrsOperation mapToDisplayOperation() {
        return mapToDisplay;
    }

    /** Returns the viewport snapshot. */
    public MapViewport viewport() {
        return viewport;
    }

    /** Returns inherited opacity before the current symbol applies its opacity. */
    public double inheritedOpacity() {
        return inheritedOpacity;
    }

    /** Returns whether line endpoints are suppressed for a closed ring. */
    public boolean closedRing() {
        return closedRing;
    }

    /** Returns an outward endpoint bearing when auto-orienting a marker. */
    public OptionalDouble endpointBearingDegrees() {
        return endpointBearingDegrees;
    }

    /** Returns the already projected marker anchor when rendering a marker. */
    public Optional<Coordinate> markerAnchorScreen() {
        return markerAnchorScreen;
    }

    /** Returns the validated map-to-screen similarity basis. */
    public MapScreenBasis mapScreenBasis() {
        return basis;
    }

    /**
     * Creates a child graphics context that the caller must dispose before returning from render.
     */
    public Graphics2D createGraphics() {
        return (Graphics2D) graphics.create();
    }

    /** Converts one source coordinate into screen coordinates. */
    public Coordinate sourceToScreen(Coordinate source) {
        return viewport.worldToScreen(
                mapToDisplay.transform(Objects.requireNonNull(source, "source")));
    }

    /**
     * Explicitly renders a same-role child with an inherited-opacity multiplier.
     *
     * <p>The child renderer receives the current inherited opacity multiplied by the supplied value
     * and remains responsible for its own symbol opacity. Cross-role children fail with the stable
     * symbol-role-mismatch diagnostic before registry lookup.
     */
    public SymbolRenderResult renderChild(Symbol child, double opacityMultiplier) {
        Objects.requireNonNull(child, "child");
        if (child.role() != role) {
            LinkedHashMap<String, String> diagnostic = new LinkedHashMap<>();
            diagnostic.put("contextRole", role.name());
            diagnostic.put("childRole", child.role() == null ? "null" : child.role().name());
            throw new SymbolException(
                    SymbolException.ROLE_MISMATCH,
                    "Child symbol role does not match renderer context",
                    diagnostic);
        }
        if (!Double.isFinite(opacityMultiplier)
                || opacityMultiplier < 0.0
                || opacityMultiplier > 1.0) {
            throw new IllegalArgumentException(
                    "opacityMultiplier must be finite and between zero and one");
        }
        return owner.renderChild(child, this, opacityMultiplier);
    }

    SymbolRenderResult renderBuiltIn(Symbol value) {
        return owner.renderBuiltIn(value, this);
    }

    Graphics2D parentGraphics() {
        return graphics;
    }
}
