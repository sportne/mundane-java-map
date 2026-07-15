package io.github.mundanej.map.io.image;

import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Immutable policy for the one source-owned decoded raster cache.
 *
 * <p>Budgets count canonical RGBA pixel bytes and successful entries per opened source. Disabled,
 * oversized, or request-accounting-constrained results bypass retention without changing decode
 * correctness. The policy creates no shared cache, worker, refresh behavior, or public metrics.
 */
public final class ImageCachePolicy {
    private static final ImageCachePolicy DISABLED = new ImageCachePolicy(0, 0);
    private static final int DEFAULT_ENTRIES = 8;
    private static final long DEFAULT_BYTES = 33_554_432;

    private final int maximumEntries;
    private final long maximumPixelBytes;

    private ImageCachePolicy(int maximumEntries, long maximumPixelBytes) {
        this.maximumEntries = maximumEntries;
        this.maximumPixelBytes = maximumPixelBytes;
    }

    /**
     * Returns a policy which retains no decoded results.
     *
     * @return the shared disabled policy
     */
    public static ImageCachePolicy disabled() {
        return DISABLED;
    }

    /**
     * Creates a bounded policy with positive entry and pixel-byte ceilings.
     *
     * @param maximumEntries maximum retained result count
     * @param maximumPixelBytes maximum retained RGBA pixel bytes
     * @return a bounded policy
     */
    public static ImageCachePolicy bounded(int maximumEntries, long maximumPixelBytes) {
        if (maximumEntries <= 0 || maximumPixelBytes <= 0) {
            throw new IllegalArgumentException("Image cache limits must be positive");
        }
        return new ImageCachePolicy(maximumEntries, maximumPixelBytes);
    }

    /**
     * Returns the Level 1 default policy: eight entries and 32 MiB of RGBA pixels.
     *
     * @return the Level 1 default policy
     */
    public static ImageCachePolicy defaults() {
        return bounded(DEFAULT_ENTRIES, DEFAULT_BYTES);
    }

    /**
     * Returns whether this policy permits retaining results.
     *
     * @return whether retention is enabled
     */
    public boolean enabled() {
        return maximumEntries != 0;
    }

    /**
     * Returns the entry ceiling, or empty when disabled.
     *
     * @return the optional entry ceiling
     */
    public OptionalInt maximumEntries() {
        return enabled() ? OptionalInt.of(maximumEntries) : OptionalInt.empty();
    }

    /**
     * Returns the retained RGBA pixel-byte ceiling, or empty when disabled.
     *
     * @return the optional retained-byte ceiling
     */
    public OptionalLong maximumPixelBytes() {
        return enabled() ? OptionalLong.of(maximumPixelBytes) : OptionalLong.empty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ImageCachePolicy policy
                && maximumEntries == policy.maximumEntries
                && maximumPixelBytes == policy.maximumPixelBytes;
    }

    @Override
    public int hashCode() {
        return 31 * maximumEntries + Long.hashCode(maximumPixelBytes);
    }

    @Override
    public String toString() {
        return enabled()
                ? "ImageCachePolicy[maximumEntries="
                        + maximumEntries
                        + ", maximumPixelBytes="
                        + maximumPixelBytes
                        + ']'
                : "ImageCachePolicy[disabled]";
    }
}
