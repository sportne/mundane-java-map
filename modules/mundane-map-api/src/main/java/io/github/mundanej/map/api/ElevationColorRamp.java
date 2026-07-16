package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;

/** Immutable numeric elevation-to-color mapping in one declared vertical unit. */
public final class ElevationColorRamp {
    /** Minimum supported color-stop count. */
    public static final int MINIMUM_STOPS = 2;

    /** Maximum supported color-stop count. */
    public static final int MAXIMUM_STOPS = 256;

    private final ElevationUnit unit;
    private final List<ElevationColorStop> stops;

    /**
     * Creates a ramp by defensively copying finite, strictly ordered stops.
     *
     * @param unit exact unit of every threshold
     * @param stops two through 256 strictly increasing stops
     */
    public ElevationColorRamp(ElevationUnit unit, List<ElevationColorStop> stops) {
        this.unit = Objects.requireNonNull(unit, "unit");
        this.stops = List.copyOf(Objects.requireNonNull(stops, "stops"));
        if (this.stops.size() < MINIMUM_STOPS || this.stops.size() > MAXIMUM_STOPS) {
            throw new IllegalArgumentException("stops must contain between 2 and 256 values");
        }
        double previous = this.stops.getFirst().elevation();
        for (int index = 1; index < this.stops.size(); index++) {
            double current = this.stops.get(index).elevation();
            double span = current - previous;
            if (!(span > 0.0) || !Double.isFinite(span)) {
                throw new IllegalArgumentException(
                        "stop elevations must have finite strictly positive spans");
            }
            previous = current;
        }
    }

    /**
     * Returns the exact vertical unit used by the thresholds.
     *
     * @return ramp threshold unit
     */
    public ElevationUnit unit() {
        return unit;
    }

    /**
     * Returns the immutable defensively owned stop list.
     *
     * @return ordered immutable color stops
     */
    public List<ElevationColorStop> stops() {
        return stops;
    }

    /**
     * Maps one finite elevation, clamping outside values and interpolating channels round-half-up.
     *
     * @param elevation finite elevation in {@link #unit()}
     * @return deterministic unpremultiplied sRGB color
     */
    public Rgba colorAt(double elevation) {
        if (!Double.isFinite(elevation)) {
            throw new IllegalArgumentException("elevation must be finite");
        }
        int low = 0;
        int high = stops.size() - 1;
        if (elevation <= stops.get(low).elevation()) {
            return stops.get(low).color();
        }
        if (elevation >= stops.get(high).elevation()) {
            return stops.get(high).color();
        }
        while (low <= high) {
            int middle = (low + high) >>> 1;
            double threshold = stops.get(middle).elevation();
            if (elevation < threshold) {
                high = middle - 1;
            } else if (elevation > threshold) {
                low = middle + 1;
            } else {
                return stops.get(middle).color();
            }
        }
        ElevationColorStop lower = stops.get(high);
        ElevationColorStop upper = stops.get(low);
        double fraction = (elevation - lower.elevation()) / (upper.elevation() - lower.elevation());
        return new Rgba(
                interpolate(lower.color().red(), upper.color().red(), fraction),
                interpolate(lower.color().green(), upper.color().green(), fraction),
                interpolate(lower.color().blue(), upper.color().blue(), fraction),
                interpolate(lower.color().alpha(), upper.color().alpha(), fraction));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ElevationColorRamp ramp
                && unit == ramp.unit
                && stops.equals(ramp.stops);
    }

    @Override
    public int hashCode() {
        return Objects.hash(unit, stops);
    }

    @Override
    public String toString() {
        return "ElevationColorRamp[unit=" + unit + ", stops=" + stops + ']';
    }

    private static int interpolate(int lower, int upper, double fraction) {
        return (int) StrictMath.floor(Math.fma(upper - lower, fraction, lower) + 0.5);
    }
}
