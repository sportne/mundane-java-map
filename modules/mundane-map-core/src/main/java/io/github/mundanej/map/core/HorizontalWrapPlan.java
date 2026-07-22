package io.github.mundanej.map.core;

import java.util.List;
import java.util.Objects;

/**
 * Immutable canonical-query and visible-copy plan for one display interval.
 *
 * @param canonicalIntervals one or two half-open canonical query intervals
 * @param minimumVisibleCopyIndex inclusive lowest visual copy index
 * @param maximumVisibleCopyIndex inclusive highest visual copy index
 * @param fullWorld whether the canonical query collapses to the complete period
 */
public record HorizontalWrapPlan(
        List<HorizontalInterval> canonicalIntervals,
        long minimumVisibleCopyIndex,
        long maximumVisibleCopyIndex,
        boolean fullWorld) {
    /** Validates and defensively copies the bounded plan. */
    public HorizontalWrapPlan {
        Objects.requireNonNull(canonicalIntervals, "canonicalIntervals");
        canonicalIntervals = List.copyOf(canonicalIntervals);
        long copyCount;
        try {
            copyCount =
                    Math.addExact(
                            Math.subtractExact(maximumVisibleCopyIndex, minimumVisibleCopyIndex),
                            1L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid horizontal-wrap copy range", exception);
        }
        if (canonicalIntervals.isEmpty()
                || canonicalIntervals.size() > 2
                || minimumVisibleCopyIndex > maximumVisibleCopyIndex
                || copyCount > HorizontalWrap.VISIBLE_COPIES_HARD_MAXIMUM
                || (fullWorld && canonicalIntervals.size() != 1)) {
            throw new IllegalArgumentException("Invalid horizontal-wrap plan");
        }
    }

    /**
     * Returns the number of contiguous visual world copies in this plan.
     *
     * @return positive bounded visual-copy count
     */
    public int visibleCopyCount() {
        return Math.toIntExact(maximumVisibleCopyIndex - minimumVisibleCopyIndex + 1L);
    }
}
