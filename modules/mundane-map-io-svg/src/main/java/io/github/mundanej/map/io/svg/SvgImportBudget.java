package io.github.mundanej.map.io.svg;

final class SvgImportBudget {
    private final String sourceId;
    private final SvgImportLimits limits;
    private long ownedBytes;
    private int commands;
    private int segments;

    SvgImportBudget(String sourceId, SvgImportLimits limits, long initialOwnedBytes) {
        this.sourceId = sourceId;
        this.limits = limits;
        ownedBytes = initialOwnedBytes;
    }

    void chargeOwned(long bytes) {
        long requested;
        try {
            requested = Math.addExact(ownedBytes, bytes);
        } catch (ArithmeticException exception) {
            requested = Long.MAX_VALUE;
        }
        if (bytes < 0 || requested > limits.maximumOwnedBytes()) {
            throw SvgFailures.limit(sourceId, "ownedBytes", requested, limits.maximumOwnedBytes());
        }
        ownedBytes = requested;
    }

    void addCommands(int count) {
        int requested;
        try {
            requested = Math.addExact(commands, count);
        } catch (ArithmeticException exception) {
            requested = Integer.MAX_VALUE;
        }
        if (count < 0 || requested > limits.maximumExpandedCommands()) {
            throw SvgFailures.limit(
                    sourceId, "expandedCommands", requested, limits.maximumExpandedCommands());
        }
        commands = requested;
    }

    void addSegments(int count) {
        int requested;
        try {
            requested = Math.addExact(segments, count);
        } catch (ArithmeticException exception) {
            requested = Integer.MAX_VALUE;
        }
        if (count < 0 || requested > limits.maximumDrawingSegments()) {
            throw SvgFailures.limit(
                    sourceId, "drawingSegments", requested, limits.maximumDrawingSegments());
        }
        segments = requested;
    }
}
