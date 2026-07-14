package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsProblem;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Projection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable directional operation resolved from an explicit {@link CrsRegistry}. */
public final class CrsOperation {
    private final CrsDefinition sourceCrs;
    private final CrsDefinition targetCrs;
    private final Envelope sourceDomain;
    private final Envelope targetDomain;
    private final Projection projection;
    private final boolean inverse;
    private final boolean identity;

    private CrsOperation(
            CrsDefinition sourceCrs,
            CrsDefinition targetCrs,
            Envelope sourceDomain,
            Envelope targetDomain,
            Projection projection,
            boolean inverse,
            boolean identity) {
        this.sourceCrs = sourceCrs;
        this.targetCrs = targetCrs;
        this.sourceDomain = sourceDomain;
        this.targetDomain = targetDomain;
        this.projection = projection;
        this.inverse = inverse;
        this.identity = identity;
    }

    static CrsOperation identity(CrsDefinition definition) {
        return new CrsOperation(
                definition,
                definition,
                definition.coordinateDomain(),
                definition.coordinateDomain(),
                null,
                false,
                true);
    }

    static CrsOperation forward(Projection projection) {
        return new CrsOperation(
                projection.sourceCrs(),
                projection.targetCrs(),
                projection.sourceDomain(),
                projection.targetDomain(),
                projection,
                false,
                false);
    }

    static CrsOperation inverse(Projection projection) {
        return new CrsOperation(
                projection.targetCrs(),
                projection.sourceCrs(),
                projection.targetDomain(),
                projection.sourceDomain(),
                projection,
                true,
                false);
    }

    /** Returns the operation source definition. */
    public CrsDefinition sourceCrs() {
        return sourceCrs;
    }

    /** Returns the operation target definition. */
    public CrsDefinition targetCrs() {
        return targetCrs;
    }

    /** Returns the accepted source domain. */
    public Envelope sourceDomain() {
        return sourceDomain;
    }

    /** Returns the resulting target domain. */
    public Envelope targetDomain() {
        return targetDomain;
    }

    /** Transforms one coordinate strictly. */
    public Coordinate transform(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        if (identity) {
            requireCoordinateContained(coordinate);
            return coordinate;
        }
        Coordinate result =
                inverse ? projection.unproject(coordinate) : projection.project(coordinate);
        if (result == null) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_TRANSFORM_NON_FINITE",
                            "Registered projection returned no coordinate",
                            Map.of("operation", inverse ? "inverse" : "forward")));
        }
        requireResultOrdinate(
                result.x(),
                targetDomain.minX(),
                targetDomain.maxX(),
                axisName(targetCrs.xAxis().meaning()),
                inverse ? "inverse" : "forward");
        requireResultOrdinate(
                result.y(),
                targetDomain.minY(),
                targetDomain.maxY(),
                axisName(targetCrs.yAxis().meaning()),
                inverse ? "inverse" : "forward");
        return result;
    }

    /** Transforms a complete envelope strictly. */
    public Envelope transformEnvelopeStrict(Envelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        if (identity) {
            requireEnvelopeContained(envelope);
            return envelope;
        }
        Envelope result =
                inverse
                        ? projection.unprojectEnvelope(envelope)
                        : projection.projectEnvelope(envelope);
        if (result == null) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_TRANSFORM_NON_FINITE",
                            "Registered projection returned no envelope",
                            Map.of("operation", inverse ? "inverse" : "forward")));
        }
        String operation = inverse ? "inverse" : "forward";
        String xAxis = axisName(targetCrs.xAxis().meaning());
        String yAxis = axisName(targetCrs.yAxis().meaning());
        requireResultOrdinate(
                result.minX(), targetDomain.minX(), targetDomain.maxX(), xAxis, operation);
        requireResultOrdinate(
                result.maxX(), targetDomain.minX(), targetDomain.maxX(), xAxis, operation);
        requireResultOrdinate(
                result.minY(), targetDomain.minY(), targetDomain.maxY(), yAxis, operation);
        requireResultOrdinate(
                result.maxY(), targetDomain.minY(), targetDomain.maxY(), yAxis, operation);
        return result;
    }

    /** Clips a query envelope to the operation domain and transforms the intersection. */
    public QueryEnvelopeTransform transformQueryEnvelope(Envelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        double minX = Math.max(envelope.minX(), sourceDomain.minX());
        double minY = Math.max(envelope.minY(), sourceDomain.minY());
        double maxX = Math.min(envelope.maxX(), sourceDomain.maxX());
        double maxY = Math.min(envelope.maxY(), sourceDomain.maxY());
        if (minX > maxX || minY > maxY) {
            return new QueryEnvelopeTransform(QueryEnvelopeStatus.OUTSIDE, Optional.empty());
        }
        Envelope intersection = new Envelope(minX, minY, maxX, maxY);
        QueryEnvelopeStatus status =
                intersection.equals(envelope)
                        ? QueryEnvelopeStatus.COMPLETE
                        : QueryEnvelopeStatus.CLIPPED;
        return new QueryEnvelopeTransform(
                status, Optional.of(transformEnvelopeStrict(intersection)));
    }

    private void requireCoordinateContained(Coordinate coordinate) {
        requireOrdinate(
                coordinate.x(),
                sourceDomain.minX(),
                sourceDomain.maxX(),
                axisName(sourceCrs.xAxis().meaning()),
                "CRS_COORDINATE_OUT_OF_DOMAIN");
        requireOrdinate(
                coordinate.y(),
                sourceDomain.minY(),
                sourceDomain.maxY(),
                axisName(sourceCrs.yAxis().meaning()),
                "CRS_COORDINATE_OUT_OF_DOMAIN");
    }

    private void requireEnvelopeContained(Envelope envelope) {
        String xAxis = axisName(sourceCrs.xAxis().meaning());
        String yAxis = axisName(sourceCrs.yAxis().meaning());
        requireOrdinate(
                envelope.minX(),
                sourceDomain.minX(),
                sourceDomain.maxX(),
                xAxis,
                "CRS_ENVELOPE_OUT_OF_DOMAIN");
        requireOrdinate(
                envelope.maxX(),
                sourceDomain.minX(),
                sourceDomain.maxX(),
                xAxis,
                "CRS_ENVELOPE_OUT_OF_DOMAIN");
        requireOrdinate(
                envelope.minY(),
                sourceDomain.minY(),
                sourceDomain.maxY(),
                yAxis,
                "CRS_ENVELOPE_OUT_OF_DOMAIN");
        requireOrdinate(
                envelope.maxY(),
                sourceDomain.minY(),
                sourceDomain.maxY(),
                yAxis,
                "CRS_ENVELOPE_OUT_OF_DOMAIN");
    }

    private static void requireOrdinate(
            double value, double minimum, double maximum, String axis, String code) {
        if (value < minimum || value > maximum) {
            throw new CrsException(
                    new CrsProblem(
                            code,
                            "CRS operation input is outside its domain",
                            Map.of(
                                    "axis",
                                    axis,
                                    "maximum",
                                    Double.toString(maximum),
                                    "minimum",
                                    Double.toString(minimum),
                                    "operation",
                                    "identity",
                                    "value",
                                    Double.toString(value))));
        }
    }

    private static String axisName(io.github.mundanej.map.api.CrsAxisMeaning meaning) {
        return switch (meaning) {
            case LONGITUDE -> "longitude";
            case LATITUDE -> "latitude";
            case EASTING -> "easting";
            case NORTHING -> "northing";
        };
    }

    private static void requireResultOrdinate(
            double value, double minimum, double maximum, String axis, String operation) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_TRANSFORM_NON_FINITE",
                            "Registered projection returned an invalid coordinate",
                            Map.of(
                                    "axis",
                                    axis,
                                    "maximum",
                                    Double.toString(maximum),
                                    "minimum",
                                    Double.toString(minimum),
                                    "operation",
                                    operation,
                                    "value",
                                    Double.toString(value))));
        }
    }
}
