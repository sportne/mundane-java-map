package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.SnapLimits;
import io.github.mundanej.map.api.SnapReferenceSet;
import java.util.Objects;
import java.util.Set;

/** Immutable operation-local input for one bounded same-CRS snap query. */
public final class SnapQuery {
    private final double screenX;
    private final double screenY;
    private final double tolerancePixels;
    private final CrsOperation coordinatesToDisplay;
    private final CrsOperation displayToCoordinates;
    private final MapViewport viewport;
    private final SnapReferenceSet references;
    private final Set<FeatureSelection> exclusions;
    private final SnapLimits limits;
    private final CancellationToken cancellation;

    /**
     * Creates a structurally valid query from exact opposite CRS operations.
     *
     * @param screenX finite logical-screen pointer x
     * @param screenY finite logical-screen pointer y
     * @param tolerancePixels inclusive tolerance in {@code (0, 256]}
     * @param coordinatesToDisplay reference-coordinate to display-world operation
     * @param displayToCoordinates exact opposite operation
     * @param viewport captured display-world viewport
     * @param references immutable same-CRS reference snapshot
     * @param exclusions exact feature keys omitted from traversal
     * @param limits immutable operation ceilings
     * @param cancellation read-only cancellation signal
     */
    public SnapQuery(
            double screenX,
            double screenY,
            double tolerancePixels,
            CrsOperation coordinatesToDisplay,
            CrsOperation displayToCoordinates,
            MapViewport viewport,
            SnapReferenceSet references,
            Set<FeatureSelection> exclusions,
            SnapLimits limits,
            CancellationToken cancellation) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
            throw new IllegalArgumentException("snap pointer coordinates must be finite");
        }
        if (!Double.isFinite(tolerancePixels) || tolerancePixels <= 0 || tolerancePixels > 256) {
            throw new IllegalArgumentException("snap tolerance must be in (0, 256]");
        }
        this.coordinatesToDisplay =
                Objects.requireNonNull(coordinatesToDisplay, "coordinatesToDisplay");
        this.displayToCoordinates =
                Objects.requireNonNull(displayToCoordinates, "displayToCoordinates");
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        this.references = Objects.requireNonNull(references, "references");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.exclusions = Set.copyOf(Objects.requireNonNull(exclusions, "exclusions"));
        if (this.exclusions.size() > limits.maximumFeatures()) {
            throw new IllegalArgumentException("snap exclusions exceed the feature limit");
        }
        if (!references.crs().equals(coordinatesToDisplay.sourceCrs())
                || !references.crs().equals(displayToCoordinates.targetCrs())
                || !coordinatesToDisplay.targetCrs().equals(displayToCoordinates.sourceCrs())) {
            throw new IllegalArgumentException(
                    "snap operations do not match reference CRS endpoints");
        }
        this.screenX = screenX;
        this.screenY = screenY;
        this.tolerancePixels = tolerancePixels;
    }

    /**
     * Returns the logical-screen pointer x ordinate.
     *
     * @return finite screen x
     */
    public double screenX() {
        return screenX;
    }

    /**
     * Returns the logical-screen pointer y ordinate.
     *
     * @return finite screen y
     */
    public double screenY() {
        return screenY;
    }

    /**
     * Returns the inclusive logical-pixel tolerance.
     *
     * @return tolerance in {@code (0, 256]}
     */
    public double tolerancePixels() {
        return tolerancePixels;
    }

    /**
     * Returns the exact reference-to-display operation.
     *
     * @return immutable forward operation
     */
    public CrsOperation coordinatesToDisplay() {
        return coordinatesToDisplay;
    }

    /**
     * Returns the exact opposite display-to-reference operation.
     *
     * @return immutable reverse operation
     */
    public CrsOperation displayToCoordinates() {
        return displayToCoordinates;
    }

    /**
     * Returns the captured immutable viewport.
     *
     * @return display-world viewport
     */
    public MapViewport viewport() {
        return viewport;
    }

    /**
     * Returns the immutable reference snapshot.
     *
     * @return same-CRS references
     */
    public SnapReferenceSet references() {
        return references;
    }

    /**
     * Returns the defensively copied exclusion keys.
     *
     * @return immutable exact keys
     */
    public Set<FeatureSelection> exclusions() {
        return exclusions;
    }

    /**
     * Returns the fixed query limits.
     *
     * @return immutable limits
     */
    public SnapLimits limits() {
        return limits;
    }

    /**
     * Returns the cancellation signal.
     *
     * @return read-only signal
     */
    public CancellationToken cancellation() {
        return cancellation;
    }
}
