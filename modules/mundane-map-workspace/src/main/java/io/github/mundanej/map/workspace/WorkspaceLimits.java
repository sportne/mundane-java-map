package io.github.mundanej.map.workspace;

/**
 * Immutable bounded workspace read/write limits.
 *
 * @param inputOutputBytes input or canonical output byte ceiling
 * @param operationBytes peak workspace-owned logical operation bytes
 * @param depth XML element-depth ceiling
 * @param elements XML element-count ceiling
 * @param attributes XML attribute-count ceiling
 * @param layers workspace layer-count ceiling
 * @param valueChars per attribute/comment UTF-16 character ceiling
 * @param aggregateChars aggregate attribute/comment character ceiling
 */
public record WorkspaceLimits(
        long inputOutputBytes,
        long operationBytes,
        int depth,
        int elements,
        int attributes,
        int layers,
        int valueChars,
        long aggregateChars) {
    /** Default conservative local workspace limits. */
    public static final WorkspaceLimits DEFAULT =
            new WorkspaceLimits(
                    4_194_304L, 16_777_216L, 8, 8_192, 32_768, 1_024, 4_096, 1_048_576L);

    /** Validates positive mutually consistent limits through their hard maxima. */
    public WorkspaceLimits {
        positive(inputOutputBytes, 16_777_216L, "inputOutputBytes");
        positive(operationBytes, 67_108_864L, "operationBytes");
        positive(depth, 16, "depth");
        positive(elements, 32_768, "elements");
        positive(attributes, 131_072, "attributes");
        positive(layers, WorkspaceText.MAX_LAYERS, "layers");
        positive(valueChars, 16_384, "valueChars");
        positive(aggregateChars, 4_194_304L, "aggregateChars");
        if (layers > elements || valueChars > aggregateChars || inputOutputBytes > operationBytes) {
            throw new IllegalArgumentException("workspace limits are mutually inconsistent");
        }
    }

    /**
     * Returns a copy with the input/output byte ceiling replaced.
     *
     * @param value new ceiling
     * @return replaced copy
     */
    public WorkspaceLimits withInputOutputBytes(long value) {
        return new WorkspaceLimits(
                value,
                operationBytes,
                depth,
                elements,
                attributes,
                layers,
                valueChars,
                aggregateChars);
    }

    /**
     * Returns a copy with the operation byte ceiling replaced.
     *
     * @param value new ceiling
     * @return replaced copy
     */
    public WorkspaceLimits withOperationBytes(long value) {
        return new WorkspaceLimits(
                inputOutputBytes,
                value,
                depth,
                elements,
                attributes,
                layers,
                valueChars,
                aggregateChars);
    }

    /**
     * Returns a copy with the depth ceiling replaced.
     *
     * @param value new ceiling
     * @return replaced copy
     */
    public WorkspaceLimits withDepth(int value) {
        return new WorkspaceLimits(
                inputOutputBytes,
                operationBytes,
                value,
                elements,
                attributes,
                layers,
                valueChars,
                aggregateChars);
    }

    /**
     * Returns a copy with the element ceiling replaced.
     *
     * @param value new ceiling
     * @return replaced copy
     */
    public WorkspaceLimits withElements(int value) {
        return new WorkspaceLimits(
                inputOutputBytes,
                operationBytes,
                depth,
                value,
                attributes,
                layers,
                valueChars,
                aggregateChars);
    }

    /**
     * Returns a copy with the attribute ceiling replaced.
     *
     * @param value new ceiling
     * @return replaced copy
     */
    public WorkspaceLimits withAttributes(int value) {
        return new WorkspaceLimits(
                inputOutputBytes,
                operationBytes,
                depth,
                elements,
                value,
                layers,
                valueChars,
                aggregateChars);
    }

    /**
     * Returns a copy with the layer ceiling replaced.
     *
     * @param value new ceiling
     * @return replaced copy
     */
    public WorkspaceLimits withLayers(int value) {
        return new WorkspaceLimits(
                inputOutputBytes,
                operationBytes,
                depth,
                elements,
                attributes,
                value,
                valueChars,
                aggregateChars);
    }

    /**
     * Returns a copy with the per-value character ceiling replaced.
     *
     * @param value new ceiling
     * @return replaced copy
     */
    public WorkspaceLimits withValueChars(int value) {
        return new WorkspaceLimits(
                inputOutputBytes,
                operationBytes,
                depth,
                elements,
                attributes,
                layers,
                value,
                aggregateChars);
    }

    /**
     * Returns a copy with the aggregate character ceiling replaced.
     *
     * @param value new ceiling
     * @return replaced copy
     */
    public WorkspaceLimits withAggregateChars(long value) {
        return new WorkspaceLimits(
                inputOutputBytes,
                operationBytes,
                depth,
                elements,
                attributes,
                layers,
                valueChars,
                value);
    }

    private static void positive(long value, long maximum, String name) {
        if (value <= 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [1," + maximum + "]");
        }
    }
}
