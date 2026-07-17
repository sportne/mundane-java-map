package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsProblem;
import io.github.mundanej.map.api.ElevationQueryMode;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationValue;
import io.github.mundanej.map.api.Envelope;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Stateless source-coordinate elevation lookup policies. */
public final class ElevationQueries {
    private ElevationQueries() {}

    /**
     * Queries an open source at a position already expressed in the supplied source CRS.
     *
     * <p>The operation performs no reprojection, coordinate normalization, unit conversion,
     * caching, ownership transfer, or source close. Empty means that the CRS-valid position is
     * outside the inclusive sample-center bounds or that a required positive-weight post is
     * no-data.
     *
     * @param source externally serialized source retained and owned by the caller
     * @param sourceCrs explicit definition of the position and source sample bounds
     * @param position finite source-CRS coordinate
     * @param mode explicit nearest-sample or bilinear policy
     * @return finite elevation in the source's exact declared unit, or empty
     * @throws NullPointerException if an argument is null
     * @throws IllegalStateException if the source is closed or violates its contract
     * @throws CrsException if recognized metadata disagrees or a CRS domain is violated
     */
    public static Optional<ElevationValue> query(
            ElevationSource source,
            CrsDefinition sourceCrs,
            Coordinate position,
            ElevationQueryMode mode) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceCrs, "sourceCrs");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(mode, "mode");
        if (source.isClosed()) {
            throw new IllegalStateException("Elevation source is closed");
        }
        ElevationSourceMetadata metadata = source.metadata();
        if (metadata == null) {
            throw new IllegalStateException("Elevation source returned null metadata");
        }

        metadata.crs().definition().ifPresent(definition -> requireSameCrs(definition, sourceCrs));
        requireEnvelopeDomain(metadata.sampleBounds(), sourceCrs);
        requireCoordinateDomain(position, sourceCrs);
        if (!metadata.sampleBounds().contains(position)) {
            return Optional.empty();
        }

        AxisBracket columns = columns(metadata, position.x());
        AxisBracket rows = rows(metadata, position.y());
        return switch (mode) {
            case NEAREST -> nearest(source, metadata, columns, rows, position);
            case BILINEAR -> bilinear(source, metadata, columns, rows);
        };
    }

    private static Optional<ElevationValue> nearest(
            ElevationSource source,
            ElevationSourceMetadata metadata,
            AxisBracket columns,
            AxisBracket rows,
            Coordinate position) {
        int column =
                columns.exact()
                        ? columns.firstIndex()
                        : scaledDistance(position.x(), columns.firstCoordinate())
                                        <= scaledDistance(position.x(), columns.secondCoordinate())
                                ? columns.firstIndex()
                                : columns.secondIndex();
        int row =
                rows.exact()
                        ? rows.firstIndex()
                        : scaledDistance(position.y(), rows.firstCoordinate())
                                        <= scaledDistance(position.y(), rows.secondCoordinate())
                                ? rows.firstIndex()
                                : rows.secondIndex();
        return value(source, metadata, column, row);
    }

    private static Optional<ElevationValue> bilinear(
            ElevationSource source,
            ElevationSourceMetadata metadata,
            AxisBracket columns,
            AxisBracket rows) {
        OptionalDouble northWest = sample(source, columns.firstIndex(), rows.firstIndex());
        if (northWest.isEmpty()) {
            return Optional.empty();
        }
        if (columns.exact() && rows.exact()) {
            return result(northWest.getAsDouble(), metadata);
        }
        if (rows.exact()) {
            OptionalDouble northEast = sample(source, columns.secondIndex(), rows.firstIndex());
            if (northEast.isEmpty()) {
                return Optional.empty();
            }
            return result(
                    convex(northWest.getAsDouble(), northEast.getAsDouble(), columns.weight()),
                    metadata);
        }
        if (columns.exact()) {
            OptionalDouble southWest = sample(source, columns.firstIndex(), rows.secondIndex());
            if (southWest.isEmpty()) {
                return Optional.empty();
            }
            return result(
                    convex(northWest.getAsDouble(), southWest.getAsDouble(), rows.weight()),
                    metadata);
        }

        OptionalDouble northEast = sample(source, columns.secondIndex(), rows.firstIndex());
        if (northEast.isEmpty()) {
            return Optional.empty();
        }
        OptionalDouble southWest = sample(source, columns.firstIndex(), rows.secondIndex());
        if (southWest.isEmpty()) {
            return Optional.empty();
        }
        OptionalDouble southEast = sample(source, columns.secondIndex(), rows.secondIndex());
        if (southEast.isEmpty()) {
            return Optional.empty();
        }
        double north = convex(northWest.getAsDouble(), northEast.getAsDouble(), columns.weight());
        double south = convex(southWest.getAsDouble(), southEast.getAsDouble(), columns.weight());
        return result(convex(north, south, rows.weight()), metadata);
    }

    private static Optional<ElevationValue> value(
            ElevationSource source, ElevationSourceMetadata metadata, int column, int row) {
        OptionalDouble sample = sample(source, column, row);
        return sample.isEmpty() ? Optional.empty() : result(sample.getAsDouble(), metadata);
    }

    private static Optional<ElevationValue> result(double value, ElevationSourceMetadata metadata) {
        return Optional.of(new ElevationValue(canonicalFinite(value), metadata.elevationUnit()));
    }

    private static OptionalDouble sample(ElevationSource source, int column, int row) {
        OptionalDouble sample = source.sample(column, row);
        if (sample == null) {
            throw new IllegalStateException("Elevation source returned a null sample result");
        }
        if (sample.isPresent()) {
            return OptionalDouble.of(canonicalFinite(sample.getAsDouble()));
        }
        return sample;
    }

    private static double convex(double first, double second, double weight) {
        if (weight == 0.0) {
            return first;
        }
        if (weight == 1.0) {
            return second;
        }
        double result;
        if ((first < 0.0) == (second < 0.0)) {
            result = Math.fma(weight, second - first, first);
        } else {
            result = Math.fma(1.0 - weight, first, weight * second);
        }
        return canonicalFinite(result);
    }

    private static double canonicalFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("Elevation source produced a non-finite sample");
        }
        return value == 0.0 ? 0.0 : value;
    }

    private static AxisBracket columns(ElevationSourceMetadata metadata, double value) {
        int low = 0;
        int high = metadata.columnCount() - 1;
        while (low <= high) {
            int middle = low + ((high - low) >>> 1);
            double coordinate = metadata.sampleCoordinate(middle, 0).x();
            int comparison = Double.compare(coordinate, value);
            if (comparison < 0) {
                low = middle + 1;
            } else if (comparison > 0) {
                high = middle - 1;
            } else {
                return AxisBracket.exact(middle, coordinate);
            }
        }
        int west = low - 1;
        int east = low;
        double westCoordinate = metadata.sampleCoordinate(west, 0).x();
        double eastCoordinate = metadata.sampleCoordinate(east, 0).x();
        return AxisBracket.between(
                west,
                east,
                westCoordinate,
                eastCoordinate,
                weight(value, westCoordinate, eastCoordinate));
    }

    private static AxisBracket rows(ElevationSourceMetadata metadata, double value) {
        int low = 0;
        int high = metadata.rowCount() - 1;
        while (low <= high) {
            int middle = low + ((high - low) >>> 1);
            double coordinate = metadata.sampleCoordinate(0, middle).y();
            int comparison = Double.compare(coordinate, value);
            if (comparison > 0) {
                low = middle + 1;
            } else if (comparison < 0) {
                high = middle - 1;
            } else {
                return AxisBracket.exact(middle, coordinate);
            }
        }
        int north = low - 1;
        int south = low;
        double northCoordinate = metadata.sampleCoordinate(0, north).y();
        double southCoordinate = metadata.sampleCoordinate(0, south).y();
        return AxisBracket.between(
                north,
                south,
                northCoordinate,
                southCoordinate,
                descendingWeight(value, northCoordinate, southCoordinate));
    }

    private static double weight(double value, double first, double second) {
        double numerator = value - first;
        double denominator = second - first;
        double quotient;
        if (Double.isFinite(numerator) && Double.isFinite(denominator)) {
            quotient = numerator / denominator;
        } else {
            quotient = (value * 0.5 - first * 0.5) / (second * 0.5 - first * 0.5);
        }
        return normalizedInteriorWeight(quotient);
    }

    private static double descendingWeight(double value, double north, double south) {
        double numerator = north - value;
        double denominator = north - south;
        double quotient;
        if (Double.isFinite(numerator) && Double.isFinite(denominator)) {
            quotient = numerator / denominator;
        } else {
            quotient = (north * 0.5 - value * 0.5) / (north * 0.5 - south * 0.5);
        }
        return normalizedInteriorWeight(quotient);
    }

    private static double normalizedInteriorWeight(double quotient) {
        if (!(quotient > 0.0)) {
            if (quotient < 0.0 || !Double.isFinite(quotient)) {
                throw new IllegalStateException("Elevation metadata coordinates are not monotone");
            }
            return Double.MIN_VALUE;
        }
        if (!(quotient < 1.0)) {
            if (quotient > 1.0 || !Double.isFinite(quotient)) {
                throw new IllegalStateException("Elevation metadata coordinates are not monotone");
            }
            return Math.nextDown(1.0);
        }
        return quotient;
    }

    private static double scaledDistance(double first, double second) {
        if ((first < 0.0) == (second < 0.0)) {
            return Math.abs(first - second) * 0.5;
        }
        return Math.abs(first * 0.5 - second * 0.5);
    }

    private static void requireSameCrs(CrsDefinition expected, CrsDefinition actual) {
        if (!expected.equals(actual)) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_DEFINITION_MISMATCH",
                            "Elevation query source CRS does not match metadata",
                            Map.of(
                                    "actualCrs",
                                    actual.canonicalIdentifier(),
                                    "expectedCrs",
                                    expected.canonicalIdentifier(),
                                    "operation",
                                    "elevationQuery")));
        }
    }

    private static void requireEnvelopeDomain(Envelope bounds, CrsDefinition sourceCrs) {
        Envelope domain = sourceCrs.coordinateDomain();
        requireDomainOrdinate(bounds.minX(), domain.minX(), domain.maxX(), "x", sourceCrs, true);
        requireDomainOrdinate(bounds.maxX(), domain.minX(), domain.maxX(), "x", sourceCrs, true);
        requireDomainOrdinate(bounds.minY(), domain.minY(), domain.maxY(), "y", sourceCrs, true);
        requireDomainOrdinate(bounds.maxY(), domain.minY(), domain.maxY(), "y", sourceCrs, true);
    }

    private static void requireCoordinateDomain(Coordinate position, CrsDefinition sourceCrs) {
        Envelope domain = sourceCrs.coordinateDomain();
        requireDomainOrdinate(position.x(), domain.minX(), domain.maxX(), "x", sourceCrs, false);
        requireDomainOrdinate(position.y(), domain.minY(), domain.maxY(), "y", sourceCrs, false);
    }

    private static void requireDomainOrdinate(
            double value,
            double minimum,
            double maximum,
            String axis,
            CrsDefinition sourceCrs,
            boolean envelope) {
        if (value < minimum || value > maximum) {
            throw new CrsException(
                    new CrsProblem(
                            envelope
                                    ? "CRS_ENVELOPE_OUT_OF_DOMAIN"
                                    : "CRS_COORDINATE_OUT_OF_DOMAIN",
                            "Elevation query input is outside its CRS domain",
                            Map.of(
                                    "axis",
                                    axis,
                                    "maximum",
                                    Double.toString(maximum),
                                    "minimum",
                                    Double.toString(minimum),
                                    "operation",
                                    "elevationQuery",
                                    "sourceCrs",
                                    sourceCrs.canonicalIdentifier(),
                                    "value",
                                    Double.toString(value))));
        }
    }

    private record AxisBracket(
            int firstIndex,
            int secondIndex,
            double firstCoordinate,
            double secondCoordinate,
            double weight) {
        private static AxisBracket exact(int index, double coordinate) {
            return new AxisBracket(index, index, coordinate, coordinate, 0.0);
        }

        private static AxisBracket between(
                int firstIndex,
                int secondIndex,
                double firstCoordinate,
                double secondCoordinate,
                double weight) {
            return new AxisBracket(
                    firstIndex, secondIndex, firstCoordinate, secondCoordinate, weight);
        }

        private boolean exact() {
            return firstIndex == secondIndex;
        }
    }
}
