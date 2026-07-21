package io.github.mundanej.map.api;

/**
 * Immutable hard-bounded limits for a detached vector-export snapshot.
 *
 * @param maximumPageAxis maximum width or height in logical pixels
 * @param maximumLayers maximum represented layers
 * @param maximumFeatures maximum feature primitives
 * @param maximumCoordinates maximum aggregate coordinate pairs
 * @param maximumCompositeDepth maximum symbol-tree depth
 * @param maximumSymbolNodes maximum aggregate symbol nodes
 * @param maximumLabels maximum placed labels
 * @param maximumLabelCodePoints maximum aggregate label Unicode code points
 * @param maximumOwnedBytes maximum deterministic snapshot-owned-byte inventory
 */
public record VectorExportSnapshotLimits(
        int maximumPageAxis,
        int maximumLayers,
        int maximumFeatures,
        int maximumCoordinates,
        int maximumCompositeDepth,
        int maximumSymbolNodes,
        int maximumLabels,
        int maximumLabelCodePoints,
        long maximumOwnedBytes) {
    /** Hard maximum for either page axis. */
    public static final int PAGE_AXIS_HARD_MAXIMUM = 16_384;

    /** Hard maximum for layer count. */
    public static final int LAYERS_HARD_MAXIMUM = 1_024;

    /** Hard maximum for feature primitives. */
    public static final int FEATURES_HARD_MAXIMUM = 100_000;

    /** Hard maximum for coordinate pairs. */
    public static final int COORDINATES_HARD_MAXIMUM = 10_000_000;

    /** Hard maximum for symbol nesting depth. */
    public static final int COMPOSITE_DEPTH_HARD_MAXIMUM = 64;

    /** Hard maximum for aggregate symbol nodes. */
    public static final int SYMBOL_NODES_HARD_MAXIMUM = 1_000_000;

    /** Hard maximum for labels. */
    public static final int LABELS_HARD_MAXIMUM = 4_096;

    /** Hard maximum for aggregate label code points. */
    public static final int LABEL_CODE_POINTS_HARD_MAXIMUM = 262_144;

    /** Hard maximum for the deterministic owned-byte inventory. */
    public static final long OWNED_BYTES_HARD_MAXIMUM = 268_435_456L;

    /** Validates every positive limit against its hard maximum. */
    public VectorExportSnapshotLimits {
        requireLimit(maximumPageAxis, PAGE_AXIS_HARD_MAXIMUM, "maximumPageAxis");
        requireLimit(maximumLayers, LAYERS_HARD_MAXIMUM, "maximumLayers");
        requireLimit(maximumFeatures, FEATURES_HARD_MAXIMUM, "maximumFeatures");
        requireLimit(maximumCoordinates, COORDINATES_HARD_MAXIMUM, "maximumCoordinates");
        requireLimit(maximumCompositeDepth, COMPOSITE_DEPTH_HARD_MAXIMUM, "maximumCompositeDepth");
        requireLimit(maximumSymbolNodes, SYMBOL_NODES_HARD_MAXIMUM, "maximumSymbolNodes");
        requireLimit(maximumLabels, LABELS_HARD_MAXIMUM, "maximumLabels");
        requireLimit(
                maximumLabelCodePoints, LABEL_CODE_POINTS_HARD_MAXIMUM, "maximumLabelCodePoints");
        if (maximumOwnedBytes <= 0 || maximumOwnedBytes > OWNED_BYTES_HARD_MAXIMUM) {
            throw new IllegalArgumentException(
                    "maximumOwnedBytes must be positive and at most " + OWNED_BYTES_HARD_MAXIMUM);
        }
    }

    /**
     * Returns the Level 1 defaults, which are also the hard maxima.
     *
     * @return immutable default limits
     */
    public static VectorExportSnapshotLimits defaults() {
        return new VectorExportSnapshotLimits(
                PAGE_AXIS_HARD_MAXIMUM,
                LAYERS_HARD_MAXIMUM,
                FEATURES_HARD_MAXIMUM,
                COORDINATES_HARD_MAXIMUM,
                COMPOSITE_DEPTH_HARD_MAXIMUM,
                SYMBOL_NODES_HARD_MAXIMUM,
                LABELS_HARD_MAXIMUM,
                LABEL_CODE_POINTS_HARD_MAXIMUM,
                OWNED_BYTES_HARD_MAXIMUM);
    }

    /**
     * Returns a copy with a different page-axis maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public VectorExportSnapshotLimits withMaximumPageAxis(int value) {
        return copy(
                value,
                maximumLayers,
                maximumFeatures,
                maximumCoordinates,
                maximumCompositeDepth,
                maximumSymbolNodes,
                maximumLabels,
                maximumLabelCodePoints,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different layer maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public VectorExportSnapshotLimits withMaximumLayers(int value) {
        return copy(
                maximumPageAxis,
                value,
                maximumFeatures,
                maximumCoordinates,
                maximumCompositeDepth,
                maximumSymbolNodes,
                maximumLabels,
                maximumLabelCodePoints,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different feature maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public VectorExportSnapshotLimits withMaximumFeatures(int value) {
        return copy(
                maximumPageAxis,
                maximumLayers,
                value,
                maximumCoordinates,
                maximumCompositeDepth,
                maximumSymbolNodes,
                maximumLabels,
                maximumLabelCodePoints,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different coordinate maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public VectorExportSnapshotLimits withMaximumCoordinates(int value) {
        return copy(
                maximumPageAxis,
                maximumLayers,
                maximumFeatures,
                value,
                maximumCompositeDepth,
                maximumSymbolNodes,
                maximumLabels,
                maximumLabelCodePoints,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different composite-depth maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public VectorExportSnapshotLimits withMaximumCompositeDepth(int value) {
        return copy(
                maximumPageAxis,
                maximumLayers,
                maximumFeatures,
                maximumCoordinates,
                value,
                maximumSymbolNodes,
                maximumLabels,
                maximumLabelCodePoints,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different symbol-node maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public VectorExportSnapshotLimits withMaximumSymbolNodes(int value) {
        return copy(
                maximumPageAxis,
                maximumLayers,
                maximumFeatures,
                maximumCoordinates,
                maximumCompositeDepth,
                value,
                maximumLabels,
                maximumLabelCodePoints,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different label maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public VectorExportSnapshotLimits withMaximumLabels(int value) {
        return copy(
                maximumPageAxis,
                maximumLayers,
                maximumFeatures,
                maximumCoordinates,
                maximumCompositeDepth,
                maximumSymbolNodes,
                value,
                maximumLabelCodePoints,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different label-code-point maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public VectorExportSnapshotLimits withMaximumLabelCodePoints(int value) {
        return copy(
                maximumPageAxis,
                maximumLayers,
                maximumFeatures,
                maximumCoordinates,
                maximumCompositeDepth,
                maximumSymbolNodes,
                maximumLabels,
                value,
                maximumOwnedBytes);
    }

    /**
     * Returns a copy with a different deterministic owned-byte maximum.
     *
     * @param value positive maximum at or below the hard maximum
     * @return updated immutable limits
     */
    public VectorExportSnapshotLimits withMaximumOwnedBytes(long value) {
        return copy(
                maximumPageAxis,
                maximumLayers,
                maximumFeatures,
                maximumCoordinates,
                maximumCompositeDepth,
                maximumSymbolNodes,
                maximumLabels,
                maximumLabelCodePoints,
                value);
    }

    private VectorExportSnapshotLimits copy(
            int pageAxis,
            int layers,
            int features,
            int coordinates,
            int depth,
            int symbols,
            int labels,
            int codePoints,
            long bytes) {
        return new VectorExportSnapshotLimits(
                pageAxis, layers, features, coordinates, depth, symbols, labels, codePoints, bytes);
    }

    private static void requireLimit(int value, int hardMaximum, String name) {
        if (value <= 0 || value > hardMaximum) {
            throw new IllegalArgumentException(
                    name + " must be positive and at most " + hardMaximum);
        }
    }
}
