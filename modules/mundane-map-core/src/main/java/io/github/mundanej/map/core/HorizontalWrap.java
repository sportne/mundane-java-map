package io.github.mundanej.map.core;

import java.util.List;
import java.util.Map;

/**
 * Immutable bounded horizontal display-repetition profile and planner.
 *
 * @param canonicalMinimumX finite inclusive canonical minimum
 * @param canonicalMaximumX finite exclusive canonical maximum
 * @param maximumVisibleCopies positive visual-copy ceiling, at most 64
 * @param maximumAbsoluteCopyIndex positive absolute copy-index ceiling
 */
public record HorizontalWrap(
        double canonicalMinimumX,
        double canonicalMaximumX,
        int maximumVisibleCopies,
        long maximumAbsoluteCopyIndex) {
    /** Hard ceiling for configured visible world copies. */
    public static final int VISIBLE_COPIES_HARD_MAXIMUM = 64;

    /** Hard ceiling for the absolute visual world-copy index. */
    public static final long COPY_INDEX_HARD_MAXIMUM = 1_048_576L;

    /** Validates the immutable profile. */
    public HorizontalWrap {
        if (!Double.isFinite(canonicalMinimumX)
                || !Double.isFinite(canonicalMaximumX)
                || canonicalMinimumX >= canonicalMaximumX
                || !Double.isFinite(canonicalMaximumX - canonicalMinimumX)) {
            throw new IllegalArgumentException("Canonical wrap bounds must be finite and ordered");
        }
        if (maximumVisibleCopies <= 0 || maximumVisibleCopies > VISIBLE_COPIES_HARD_MAXIMUM) {
            throw new IllegalArgumentException("Visible-copy limit is outside its profile");
        }
        if (maximumAbsoluteCopyIndex <= 0L || maximumAbsoluteCopyIndex > COPY_INDEX_HARD_MAXIMUM) {
            throw new IllegalArgumentException("Copy-index limit is outside its profile");
        }
    }

    /**
     * Returns the approved Web Mercator horizontal-wrap defaults.
     *
     * @return immutable Web Mercator profile
     */
    public static HorizontalWrap webMercator() {
        return new HorizontalWrap(
                -WebMercatorProjection.WORLD_LIMIT,
                WebMercatorProjection.WORLD_LIMIT,
                8,
                COPY_INDEX_HARD_MAXIMUM);
    }

    /**
     * Returns the positive canonical period.
     *
     * @return canonical maximum minus canonical minimum
     */
    public double period() {
        return canonicalMaximumX - canonicalMinimumX;
    }

    /**
     * Canonicalizes one finite display ordinate into the half-open base interval.
     *
     * @param displayX finite continuous display ordinate
     * @return canonical ordinate and checked visual copy index
     */
    public WrappedX canonicalize(double displayX) {
        requireFinite(displayX, "displayX");
        long copyIndex = copyIndex(displayX);
        double canonical = displayX - copyIndex * period();
        if (canonical < canonicalMinimumX) {
            canonical += period();
            copyIndex = checkedDecrement(copyIndex);
        } else if (canonical >= canonicalMaximumX) {
            canonical -= period();
            copyIndex = checkedIncrement(copyIndex);
        }
        requireCopyIndex(copyIndex);
        return new WrappedX(canonical, copyIndex);
    }

    /**
     * Translates one canonical ordinate into the requested visual copy.
     *
     * @param canonicalX finite ordinate in this profile's half-open canonical interval
     * @param copyIndex checked visual copy index
     * @return finite continuous display ordinate
     */
    public double translate(double canonicalX, long copyIndex) {
        requireFinite(canonicalX, "canonicalX");
        if (canonicalX < canonicalMinimumX || canonicalX >= canonicalMaximumX) {
            throw new IllegalArgumentException("Horizontal ordinate is not canonical");
        }
        requireCopyIndex(copyIndex);
        double displayX = canonicalX + copyIndex * period();
        if (!Double.isFinite(displayX)) {
            throw precision(copyIndex);
        }
        return displayX;
    }

    /**
     * Plans canonical intervals and visual copies for a finite non-empty display interval.
     *
     * @param displayMinimumX finite inclusive display minimum
     * @param displayMaximumX finite exclusive display maximum
     * @param worldUnitsPerPixel finite positive display scale used by the precision check
     * @return immutable bounded query/copy plan
     */
    public HorizontalWrapPlan plan(
            double displayMinimumX, double displayMaximumX, double worldUnitsPerPixel) {
        requireFinite(displayMinimumX, "displayMinimumX");
        requireFinite(displayMaximumX, "displayMaximumX");
        requireFinite(worldUnitsPerPixel, "worldUnitsPerPixel");
        if (displayMinimumX >= displayMaximumX || worldUnitsPerPixel <= 0.0) {
            throw new IllegalArgumentException("Display interval and scale must be positive");
        }

        WrappedX first = canonicalize(displayMinimumX);
        WrappedX last = canonicalize(Math.nextDown(displayMaximumX));
        requirePrecision(displayMinimumX, worldUnitsPerPixel, first.copyIndex());
        requirePrecision(displayMaximumX, worldUnitsPerPixel, last.copyIndex());
        long copies = last.copyIndex() - first.copyIndex() + 1L;
        if (copies <= 0L || copies > maximumVisibleCopies) {
            throw new HorizontalWrapException(
                    new HorizontalWrapProblem(
                            "WORLD_WRAP_COPY_LIMIT_EXCEEDED",
                            Map.of(
                                    "maximum",
                                    Integer.toString(maximumVisibleCopies),
                                    "requested",
                                    Long.toString(copies))));
        }
        boolean fullWorld = displayMaximumX - displayMinimumX >= period();
        List<HorizontalInterval> intervals;
        if (fullWorld) {
            intervals = List.of(new HorizontalInterval(canonicalMinimumX, canonicalMaximumX));
        } else {
            double end = canonicalExclusiveMaximum(displayMaximumX, last.copyIndex());
            if (first.copyIndex() == last.copyIndex()) {
                intervals =
                        List.of(
                                new HorizontalInterval(
                                        first.canonicalX(),
                                        end == canonicalMinimumX ? canonicalMaximumX : end));
            } else {
                intervals =
                        end == canonicalMinimumX
                                ? List.of(
                                        new HorizontalInterval(
                                                first.canonicalX(), canonicalMaximumX))
                                : List.of(
                                        new HorizontalInterval(
                                                first.canonicalX(), canonicalMaximumX),
                                        new HorizontalInterval(canonicalMinimumX, end));
            }
        }
        return new HorizontalWrapPlan(intervals, first.copyIndex(), last.copyIndex(), fullWorld);
    }

    private double canonicalExclusiveMaximum(double displayMaximumX, long lastCopyIndex) {
        double maximum = displayMaximumX - lastCopyIndex * period();
        if (maximum < canonicalMinimumX) {
            maximum += period();
        } else if (maximum > canonicalMaximumX) {
            maximum -= period();
        }
        if (maximum <= canonicalMinimumX || maximum > canonicalMaximumX) {
            throw precision(lastCopyIndex);
        }
        return maximum;
    }

    /**
     * Maps any display tile column into a non-negative canonical matrix column.
     *
     * @param displayColumn signed continuous-world tile column
     * @param matrixWidth positive number of canonical columns
     * @return canonical column in {@code [0, matrixWidth)}
     */
    public long canonicalTileColumn(long displayColumn, long matrixWidth) {
        if (matrixWidth <= 0L) {
            throw new IllegalArgumentException("Matrix width must be positive");
        }
        return Math.floorMod(displayColumn, matrixWidth);
    }

    private long copyIndex(double displayX) {
        double quotient = StrictMath.floor((displayX - canonicalMinimumX) / period());
        if (!Double.isFinite(quotient)
                || quotient < -maximumAbsoluteCopyIndex
                || quotient > maximumAbsoluteCopyIndex) {
            long reported =
                    quotient < 0.0 ? -maximumAbsoluteCopyIndex - 1L : maximumAbsoluteCopyIndex + 1L;
            throw precision(reported);
        }
        return (long) quotient;
    }

    private long checkedIncrement(long copyIndex) {
        long incremented = Math.incrementExact(copyIndex);
        requireCopyIndex(incremented);
        return incremented;
    }

    private long checkedDecrement(long copyIndex) {
        long decremented = Math.decrementExact(copyIndex);
        requireCopyIndex(decremented);
        return decremented;
    }

    private void requireCopyIndex(long copyIndex) {
        if (copyIndex < -maximumAbsoluteCopyIndex || copyIndex > maximumAbsoluteCopyIndex) {
            throw precision(copyIndex);
        }
    }

    private void requirePrecision(double displayX, double worldUnitsPerPixel, long copyIndex) {
        if (Math.ulp(displayX) > worldUnitsPerPixel / 4.0) {
            throw precision(copyIndex);
        }
    }

    private HorizontalWrapException precision(long copyIndex) {
        return new HorizontalWrapException(
                new HorizontalWrapProblem(
                        "WORLD_WRAP_PRECISION_EXCEEDED",
                        Map.of(
                                "copyIndex",
                                Long.toString(copyIndex),
                                "maximum",
                                Long.toString(maximumAbsoluteCopyIndex))));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
