package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.ScreenGeometryHits;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Callback-scoped logical-paint hit context for an explicitly registered AWT renderer.
 *
 * <p>The context is valid only for the current hit-test callback and must not be retained. Geometry
 * and viewport values are immutable snapshots; the returned component clip is defensively copied.
 * Screen ordinates and tolerances are measured in logical pixels.
 */
public final class AwtSymbolHitContext {
    private static final double CURVE_FLATNESS = 1.0e-9;
    private static final int MAX_CURVE_DEPTH = 32;

    private final SymbolRole role;
    private final String featureId;
    private final Geometry featureGeometry;
    private final Geometry renderGeometry;
    private final CrsOperation sourceToDisplay;
    private final MapViewport viewport;
    private final double inheritedOpacity;
    private final boolean closedRing;
    private final OptionalDouble endpointBearing;
    private final Optional<Coordinate> markerAnchor;
    private final MapScreenBasis screenBasis;
    private final double queryX;
    private final double queryY;
    private final double tolerance;
    private final Rectangle2D componentClip;
    private final MapView owner;

    AwtSymbolHitContext(
            SymbolRole role,
            String featureId,
            Geometry featureGeometry,
            Geometry renderGeometry,
            CrsOperation sourceToDisplay,
            MapViewport viewport,
            double inheritedOpacity,
            boolean closedRing,
            OptionalDouble endpointBearing,
            Optional<Coordinate> markerAnchor,
            MapScreenBasis screenBasis,
            double queryX,
            double queryY,
            double tolerance,
            Rectangle2D componentClip,
            MapView owner) {
        this.role = Objects.requireNonNull(role, "role");
        this.featureId = Objects.requireNonNull(featureId, "featureId");
        this.featureGeometry = Objects.requireNonNull(featureGeometry, "featureGeometry");
        this.renderGeometry = Objects.requireNonNull(renderGeometry, "renderGeometry");
        this.sourceToDisplay = Objects.requireNonNull(sourceToDisplay, "sourceToDisplay");
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.inheritedOpacity = inheritedOpacity;
        this.closedRing = closedRing;
        this.endpointBearing = Objects.requireNonNull(endpointBearing, "endpointBearing");
        this.markerAnchor = Objects.requireNonNull(markerAnchor, "markerAnchor");
        this.screenBasis = Objects.requireNonNull(screenBasis, "screenBasis");
        this.queryX = queryX;
        this.queryY = queryY;
        this.tolerance = tolerance;
        this.componentClip =
                (Rectangle2D) Objects.requireNonNull(componentClip, "componentClip").clone();
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * Returns the symbol role.
     *
     * @return marker, line, fill, or legacy-geometry role used for registry lookup
     */
    public SymbolRole role() {
        return role;
    }

    /**
     * Returns the stable feature identifier.
     *
     * @return non-empty identifier of the feature being tested
     */
    public String featureId() {
        return featureId;
    }

    /**
     * Returns the original feature geometry in its source CRS.
     *
     * @return immutable original feature geometry
     */
    public Geometry featureGeometry() {
        return featureGeometry;
    }

    /**
     * Returns the geometry currently rendered by this symbol in its source CRS.
     *
     * @return immutable role-specific render geometry
     */
    public Geometry renderGeometry() {
        return renderGeometry;
    }

    /**
     * Returns the fixed viewport snapshot.
     *
     * @return immutable display-world-to-screen viewport used by this callback
     */
    public MapViewport viewport() {
        return viewport;
    }

    /**
     * Returns inherited owner/composite opacity.
     *
     * @return finite opacity in {@code [0,1]} before the current symbol applies its opacity
     */
    public double inheritedOpacity() {
        return inheritedOpacity;
    }

    /**
     * Returns whether a line context represents a closed polygon ring.
     *
     * @return {@code true} when endpoint paint must be suppressed for ring closure
     */
    public boolean closedRing() {
        return closedRing;
    }

    /**
     * Returns an endpoint's outward screen bearing when present.
     *
     * @return clockwise logical-screen bearing in degrees, or empty outside an endpoint callback
     */
    public OptionalDouble endpointBearingDegrees() {
        return endpointBearing;
    }

    /**
     * Returns the resolved screen marker anchor when present.
     *
     * @return immutable logical-screen coordinate, or empty outside a marker callback
     */
    public Optional<Coordinate> markerAnchorScreen() {
        return markerAnchor;
    }

    /**
     * Returns the fixed map-to-screen basis.
     *
     * @return validated similarity basis for map-relative symbol placement
     */
    public MapScreenBasis mapScreenBasis() {
        return screenBasis;
    }

    /**
     * Returns the query x ordinate.
     *
     * @return finite logical-screen x ordinate in pixels
     */
    public double queryX() {
        return queryX;
    }

    /**
     * Returns the query y ordinate.
     *
     * @return finite logical-screen y ordinate in pixels
     */
    public double queryY() {
        return queryY;
    }

    /**
     * Returns the non-negative logical-pixel tolerance.
     *
     * @return finite hit tolerance in logical pixels
     */
    public double tolerancePixels() {
        return tolerance;
    }

    /**
     * Returns a defensive component-clip rectangle.
     *
     * @return mutable copy of the component bounds in logical-screen pixels
     */
    public Rectangle2D componentClip() {
        return (Rectangle2D) componentClip.clone();
    }

    /**
     * Converts a source coordinate with the fixed CRS operation and viewport snapshots.
     *
     * @param coordinate non-null coordinate in the feature source CRS
     * @return immutable logical-screen coordinate in pixels
     * @throws NullPointerException if {@code coordinate} is {@code null}
     * @throws io.github.mundanej.map.api.CrsException if the coordinate is outside the supported
     *     transformation domain
     */
    public Coordinate sourceToScreen(Coordinate coordinate) {
        return owner.toScreen(coordinate, sourceToDisplay, viewport);
    }

    CrsOperation sourceToDisplayOperation() {
        return sourceToDisplay;
    }

    /**
     * Tests a same-role child with inherited opacity and the same derived context.
     *
     * @param child non-null child symbol
     * @param opacityMultiplier finite multiplier in {@code [0,1]}
     * @return whether the child reports a hit
     * @throws NullPointerException if {@code child} is {@code null}
     * @throws IllegalArgumentException if the multiplier is invalid
     * @throws io.github.mundanej.map.api.SymbolException if the role or registered renderer is
     *     invalid
     */
    public boolean hitChild(Symbol child, double opacityMultiplier) {
        return owner.hitChild(child, this, opacityMultiplier);
    }

    /**
     * Tests a final logical paint footprint after intersecting the exact component clip.
     *
     * @param logicalPaintFootprint non-null shape in logical-screen coordinates
     * @return whether its interior or boundary falls within the query tolerance
     * @throws NullPointerException if {@code logicalPaintFootprint} is {@code null}
     */
    public boolean visibleShapeHit(Shape logicalPaintFootprint) {
        Area visible =
                new Area(Objects.requireNonNull(logicalPaintFootprint, "logicalPaintFootprint"));
        visible.intersect(new Area(componentClip));
        if (visible.isEmpty()) {
            return false;
        }
        return visible.contains(queryX, queryY) || boundaryWithin(visible);
    }

    private boolean boundaryWithin(Shape visible) {
        PathIterator iterator = visible.getPathIterator(null);
        double[] coordinates = new double[6];
        double firstX = 0.0;
        double firstY = 0.0;
        double previousX = 0.0;
        double previousY = 0.0;
        while (!iterator.isDone()) {
            int type = iterator.currentSegment(coordinates);
            if (type == PathIterator.SEG_MOVETO) {
                firstX = coordinates[0];
                firstY = coordinates[1];
                previousX = firstX;
                previousY = firstY;
            } else if (type == PathIterator.SEG_LINETO) {
                if (segmentWithin(
                        previousX, previousY, coordinates[0], coordinates[1], tolerance)) {
                    return true;
                }
                previousX = coordinates[0];
                previousY = coordinates[1];
            } else if (type == PathIterator.SEG_QUADTO) {
                if (quadraticWithin(
                        previousX,
                        previousY,
                        coordinates[0],
                        coordinates[1],
                        coordinates[2],
                        coordinates[3],
                        0)) {
                    return true;
                }
                previousX = coordinates[2];
                previousY = coordinates[3];
            } else if (type == PathIterator.SEG_CUBICTO) {
                if (cubicWithin(
                        previousX,
                        previousY,
                        coordinates[0],
                        coordinates[1],
                        coordinates[2],
                        coordinates[3],
                        coordinates[4],
                        coordinates[5],
                        0)) {
                    return true;
                }
                previousX = coordinates[4];
                previousY = coordinates[5];
            } else if (type == PathIterator.SEG_CLOSE) {
                if (segmentWithin(previousX, previousY, firstX, firstY, tolerance)) {
                    return true;
                }
                previousX = firstX;
                previousY = firstY;
            }
            iterator.next();
        }
        return false;
    }

    private boolean quadraticWithin(
            double x0, double y0, double cx, double cy, double x1, double y1, int depth) {
        double residual = pointSegmentDistance(cx, cy, x0, y0, x1, y1);
        if (!controlBoundsNear(new double[] {x0, y0, cx, cy, x1, y1}, tolerance + residual)) {
            return false;
        }
        if (residual <= CURVE_FLATNESS || depth >= MAX_CURVE_DEPTH) {
            return segmentWithin(x0, y0, x1, y1, tolerance + residual);
        }
        double x01 = midpoint(x0, cx);
        double y01 = midpoint(y0, cy);
        double x12 = midpoint(cx, x1);
        double y12 = midpoint(cy, y1);
        double x012 = midpoint(x01, x12);
        double y012 = midpoint(y01, y12);
        return quadraticWithin(x0, y0, x01, y01, x012, y012, depth + 1)
                || quadraticWithin(x012, y012, x12, y12, x1, y1, depth + 1);
    }

    private boolean cubicWithin(
            double x0,
            double y0,
            double c1x,
            double c1y,
            double c2x,
            double c2y,
            double x1,
            double y1,
            int depth) {
        double residual =
                Math.max(
                        pointSegmentDistance(c1x, c1y, x0, y0, x1, y1),
                        pointSegmentDistance(c2x, c2y, x0, y0, x1, y1));
        if (!controlBoundsNear(
                new double[] {x0, y0, c1x, c1y, c2x, c2y, x1, y1}, tolerance + residual)) {
            return false;
        }
        if (residual <= CURVE_FLATNESS || depth >= MAX_CURVE_DEPTH) {
            return segmentWithin(x0, y0, x1, y1, tolerance + residual);
        }
        double x01 = midpoint(x0, c1x);
        double y01 = midpoint(y0, c1y);
        double x12 = midpoint(c1x, c2x);
        double y12 = midpoint(c1y, c2y);
        double x23 = midpoint(c2x, x1);
        double y23 = midpoint(c2y, y1);
        double x012 = midpoint(x01, x12);
        double y012 = midpoint(y01, y12);
        double x123 = midpoint(x12, x23);
        double y123 = midpoint(y12, y23);
        double x0123 = midpoint(x012, x123);
        double y0123 = midpoint(y012, y123);
        return cubicWithin(x0, y0, x01, y01, x012, y012, x0123, y0123, depth + 1)
                || cubicWithin(x0123, y0123, x123, y123, x23, y23, x1, y1, depth + 1);
    }

    private boolean controlBoundsNear(double[] xy, double radius) {
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < xy.length; index += 2) {
            minimumX = Math.min(minimumX, xy[index]);
            minimumY = Math.min(minimumY, xy[index + 1]);
            maximumX = Math.max(maximumX, xy[index]);
            maximumY = Math.max(maximumY, xy[index + 1]);
        }
        double dx = Math.max(minimumX - queryX, Math.max(0.0, queryX - maximumX));
        double dy = Math.max(minimumY - queryY, Math.max(0.0, queryY - maximumY));
        return Math.hypot(dx, dy) <= radius;
    }

    private static double pointSegmentDistance(
            double px, double py, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0.0) {
            return Math.hypot(px - x0, py - y0);
        }
        double fraction = ((px - x0) * dx + (py - y0) * dy) / lengthSquared;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        return Math.hypot(px - (x0 + fraction * dx), py - (y0 + fraction * dy));
    }

    private static double midpoint(double first, double second) {
        return first / 2.0 + second / 2.0;
    }

    private boolean segmentWithin(double x1, double y1, double x2, double y2, double radius) {
        return ScreenGeometryHits.polylineWithin(
                CoordinateSequence.of(x1, y1, x2, y2), false, queryX, queryY, radius);
    }

    boolean hitBuiltIn(Symbol value) {
        return owner.hitBuiltIn(value, this);
    }
}
