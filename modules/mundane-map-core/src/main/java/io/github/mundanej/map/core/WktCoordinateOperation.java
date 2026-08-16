package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsEllipsoid;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsProblem;
import io.github.mundanej.map.api.WktCrsAxis;
import io.github.mundanej.map.api.WktCrsDefinition;
import io.github.mundanej.map.api.WktCrsKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Axis-aware bounded coordinate operation for the supported pure-Java WKT2 methods. */
public final class WktCoordinateOperation {
    /** Exact supported EPSG method name for ellipsoidal Mercator variant A. */
    public static final String MERCATOR_VARIANT_A = "Mercator (variant A)";

    /** Exact supported EPSG method name for ellipsoidal Transverse Mercator. */
    public static final String TRANSVERSE_MERCATOR = "Transverse Mercator";

    /** Maximum coordinates accepted by one atomic batch. */
    public static final int MAXIMUM_BATCH_COORDINATES = 1_000_000;

    private final WktCrsDefinition source;
    private final WktCrsDefinition target;
    private final WktCrsDefinition geographic;
    private final WktCrsDefinition projected;
    private final boolean inverse;
    private final boolean identity;
    private final Method method;

    private WktCoordinateOperation(
            WktCrsDefinition source,
            WktCrsDefinition target,
            WktCrsDefinition geographic,
            WktCrsDefinition projected,
            boolean inverse,
            boolean identity,
            Method method) {
        this.source = source;
        this.target = target;
        this.geographic = geographic;
        this.projected = projected;
        this.inverse = inverse;
        this.identity = identity;
        this.method = method;
    }

    /**
     * Resolves a strict identity or direct geographic/projected operation.
     *
     * <p>Native WKT axis order, direction, and units are honored independently from the library's
     * internal longitude/latitude and easting/northing presentation convention. A projected base
     * identifier and ellipsoid must exactly match the geographic endpoint. Vertical, compound,
     * grid-based, datum-changing, and unrecognized methods fail before any coordinate is
     * transformed.
     *
     * @param source source WKT definition
     * @param target target WKT definition
     * @return immutable operation
     * @throws CrsException with a stable context-free code when no exact operation is supported
     */
    public static WktCoordinateOperation between(WktCrsDefinition source, WktCrsDefinition target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        requireTwoAxes(source);
        requireTwoAxes(target);
        if (source.equals(target)) {
            return new WktCoordinateOperation(
                    source, target, source, source, false, true, Method.IDENTITY);
        }
        boolean inverse;
        WktCrsDefinition geographic;
        WktCrsDefinition projected;
        if (source.kind() == WktCrsKind.GEOGRAPHIC && target.kind() == WktCrsKind.PROJECTED) {
            inverse = false;
            geographic = source;
            projected = target;
        } else if (source.kind() == WktCrsKind.PROJECTED
                && target.kind() == WktCrsKind.GEOGRAPHIC) {
            inverse = true;
            geographic = target;
            projected = source;
        } else {
            throw unsupported();
        }
        if (geographic.identifier().isEmpty()
                || !geographic.identifier().equals(projected.baseIdentifier())
                || geographic.ellipsoid().isEmpty()
                || !geographic.ellipsoid().equals(projected.ellipsoid())
                || !geographic.datumName().equals(projected.datumName())) {
            throw unsupported();
        }
        Method method = method(projected.operationMethod().orElseThrow());
        Parameters.require(projected.parameters(), method);
        return new WktCoordinateOperation(
                source, target, geographic, projected, inverse, false, method);
    }

    /**
     * Returns the exact source definition.
     *
     * @return exact source definition
     */
    public WktCrsDefinition source() {
        return source;
    }

    /**
     * Returns the exact target definition.
     *
     * @return exact target definition
     */
    public WktCrsDefinition target() {
        return target;
    }

    /**
     * Transforms one native-axis tuple.
     *
     * @param coordinate tuple expressed in the source's native axis order and units
     * @return tuple expressed in the target's native axis order and units
     * @throws CrsException for a stable out-of-domain or non-finite failure
     */
    public Coordinate transform(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        if (identity) {
            return coordinate;
        }
        Parameters parameters = new Parameters(projected.parameters());
        Coordinate result;
        if (inverse) {
            Coordinate eastNorth = toEastNorth(source.axes(), coordinate);
            Coordinate lonLat =
                    inverse(eastNorth, parameters, geographic.ellipsoid().orElseThrow());
            result = fromLongitudeLatitude(target.axes(), lonLat);
        } else {
            Coordinate lonLat = toLongitudeLatitude(source.axes(), coordinate);
            Coordinate eastNorth =
                    forward(lonLat, parameters, geographic.ellipsoid().orElseThrow());
            result = fromEastNorth(target.axes(), eastNorth);
        }
        if (!Double.isFinite(result.x()) || !Double.isFinite(result.y())) {
            throw failure(
                    "CRS_TRANSFORM_NON_FINITE",
                    "Coordinate operation produced a non-finite result");
        }
        return result;
    }

    /**
     * Atomically transforms a bounded coordinate batch.
     *
     * @param coordinates source tuples, defensively copied before work
     * @return immutable complete target tuples
     * @throws CrsException before publication when the batch is too large or one tuple fails
     */
    public List<Coordinate> transformAll(List<Coordinate> coordinates) {
        Objects.requireNonNull(coordinates, "coordinates");
        if (coordinates.size() > MAXIMUM_BATCH_COORDINATES) {
            throw failure("CRS_TRANSFORM_LIMIT", "Coordinate batch exceeds its fixed limit");
        }
        List<Coordinate> sourceCopy = List.copyOf(coordinates);
        List<Coordinate> result = new ArrayList<>(sourceCopy.size());
        for (Coordinate coordinate : sourceCopy) {
            result.add(transform(Objects.requireNonNull(coordinate, "coordinate")));
        }
        return List.copyOf(result);
    }

    private Coordinate forward(Coordinate lonLat, Parameters parameters, CrsEllipsoid ellipsoid) {
        return switch (method) {
            case MERCATOR -> mercatorForward(lonLat, parameters, ellipsoid);
            case TRANSVERSE_MERCATOR -> transverseMercatorForward(lonLat, parameters, ellipsoid);
            case IDENTITY -> throw new IllegalStateException("Identity handled before projection");
        };
    }

    private Coordinate inverse(
            Coordinate eastNorth, Parameters parameters, CrsEllipsoid ellipsoid) {
        return switch (method) {
            case MERCATOR -> mercatorInverse(eastNorth, parameters, ellipsoid);
            case TRANSVERSE_MERCATOR -> transverseMercatorInverse(eastNorth, parameters, ellipsoid);
            case IDENTITY -> throw new IllegalStateException("Identity handled before projection");
        };
    }

    private static Coordinate mercatorForward(
            Coordinate lonLat, Parameters parameters, CrsEllipsoid ellipsoid) {
        requireLatitude(lonLat.y(), Math.toRadians(89.0));
        double eccentricity = eccentricity(ellipsoid);
        double sinLatitude = Math.sin(lonLat.y());
        double ratio = (1.0 - eccentricity * sinLatitude) / (1.0 + eccentricity * sinLatitude);
        double x =
                parameters.falseEasting
                        + ellipsoid.semiMajorAxis()
                                * parameters.scale
                                * normalizeLongitude(lonLat.x() - parameters.longitudeOrigin);
        double y =
                parameters.falseNorthing
                        + ellipsoid.semiMajorAxis()
                                * parameters.scale
                                * (Math.log(Math.tan(Math.PI / 4.0 + lonLat.y() / 2.0))
                                        + eccentricity / 2.0 * Math.log(ratio));
        return finite(x, y);
    }

    private static Coordinate mercatorInverse(
            Coordinate eastNorth, Parameters parameters, CrsEllipsoid ellipsoid) {
        double eccentricity = eccentricity(ellipsoid);
        double longitude =
                parameters.longitudeOrigin
                        + (eastNorth.x() - parameters.falseEasting)
                                / (ellipsoid.semiMajorAxis() * parameters.scale);
        double isometric =
                (eastNorth.y() - parameters.falseNorthing)
                        / (ellipsoid.semiMajorAxis() * parameters.scale);
        double latitude = 2.0 * Math.atan(Math.exp(isometric)) - Math.PI / 2.0;
        for (int iteration = 0; iteration < 12; iteration++) {
            double eccentricSin = eccentricity * Math.sin(latitude);
            double next =
                    2.0
                                    * Math.atan(
                                            Math.exp(isometric)
                                                    * Math.pow(
                                                            (1.0 + eccentricSin)
                                                                    / (1.0 - eccentricSin),
                                                            eccentricity / 2.0))
                            - Math.PI / 2.0;
            if (Math.abs(next - latitude) <= 1.0e-14) {
                latitude = next;
                break;
            }
            latitude = next;
        }
        requireLatitude(latitude, Math.toRadians(89.0));
        return finite(normalizeLongitude(longitude), latitude);
    }

    private static Coordinate transverseMercatorForward(
            Coordinate lonLat, Parameters parameters, CrsEllipsoid ellipsoid) {
        requireLatitude(lonLat.y(), Math.toRadians(84.0));
        double deltaLongitude = normalizeLongitude(lonLat.x() - parameters.longitudeOrigin);
        if (Math.abs(deltaLongitude) > Math.toRadians(30.0)) {
            throw outOfDomain();
        }
        double e2 = eccentricitySquared(ellipsoid);
        double ep2 = e2 / (1.0 - e2);
        double sin = Math.sin(lonLat.y());
        double cos = Math.cos(lonLat.y());
        double tan = Math.tan(lonLat.y());
        double n = ellipsoid.semiMajorAxis() / Math.sqrt(1.0 - e2 * sin * sin);
        double t = tan * tan;
        double c = ep2 * cos * cos;
        double a = cos * deltaLongitude;
        double m = meridionalArc(lonLat.y(), ellipsoid.semiMajorAxis(), e2);
        double m0 = meridionalArc(parameters.latitudeOrigin, ellipsoid.semiMajorAxis(), e2);
        double x =
                parameters.falseEasting
                        + parameters.scale
                                * n
                                * (a
                                        + (1.0 - t + c) * cube(a) / 6.0
                                        + (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ep2)
                                                * fifth(a)
                                                / 120.0);
        double y =
                parameters.falseNorthing
                        + parameters.scale
                                * (m
                                        - m0
                                        + n
                                                * tan
                                                * (a * a / 2.0
                                                        + (5.0 - t + 9.0 * c + 4.0 * c * c)
                                                                * fourth(a)
                                                                / 24.0
                                                        + (61.0
                                                                        - 58.0 * t
                                                                        + t * t
                                                                        + 600.0 * c
                                                                        - 330.0 * ep2)
                                                                * sixth(a)
                                                                / 720.0));
        return finite(x, y);
    }

    private static Coordinate transverseMercatorInverse(
            Coordinate eastNorth, Parameters parameters, CrsEllipsoid ellipsoid) {
        double e2 = eccentricitySquared(ellipsoid);
        double ep2 = e2 / (1.0 - e2);
        double m0 = meridionalArc(parameters.latitudeOrigin, ellipsoid.semiMajorAxis(), e2);
        double m = m0 + (eastNorth.y() - parameters.falseNorthing) / parameters.scale;
        double mu =
                m
                        / (ellipsoid.semiMajorAxis()
                                * (1.0 - e2 / 4.0 - 3.0 * e2 * e2 / 64.0 - 5.0 * cube(e2) / 256.0));
        double e1 = (1.0 - Math.sqrt(1.0 - e2)) / (1.0 + Math.sqrt(1.0 - e2));
        double phi1 =
                mu
                        + (3.0 * e1 / 2.0 - 27.0 * cube(e1) / 32.0) * Math.sin(2.0 * mu)
                        + (21.0 * e1 * e1 / 16.0 - 55.0 * fourth(e1) / 32.0) * Math.sin(4.0 * mu)
                        + 151.0 * cube(e1) / 96.0 * Math.sin(6.0 * mu)
                        + 1097.0 * fourth(e1) / 512.0 * Math.sin(8.0 * mu);
        double sin = Math.sin(phi1);
        double cos = Math.cos(phi1);
        double tan = Math.tan(phi1);
        double c1 = ep2 * cos * cos;
        double t1 = tan * tan;
        double n1 = ellipsoid.semiMajorAxis() / Math.sqrt(1.0 - e2 * sin * sin);
        double r1 = ellipsoid.semiMajorAxis() * (1.0 - e2) / Math.pow(1.0 - e2 * sin * sin, 1.5);
        double d = (eastNorth.x() - parameters.falseEasting) / (n1 * parameters.scale);
        double latitude =
                phi1
                        - n1
                                * tan
                                / r1
                                * (d * d / 2.0
                                        - (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ep2)
                                                * fourth(d)
                                                / 24.0
                                        + (61.0
                                                        + 90.0 * t1
                                                        + 298.0 * c1
                                                        + 45.0 * t1 * t1
                                                        - 252.0 * ep2
                                                        - 3.0 * c1 * c1)
                                                * sixth(d)
                                                / 720.0);
        double longitude =
                parameters.longitudeOrigin
                        + (d
                                        - (1.0 + 2.0 * t1 + c1) * cube(d) / 6.0
                                        + (5.0
                                                        - 2.0 * c1
                                                        + 28.0 * t1
                                                        - 3.0 * c1 * c1
                                                        + 8.0 * ep2
                                                        + 24.0 * t1 * t1)
                                                * fifth(d)
                                                / 120.0)
                                / cos;
        requireLatitude(latitude, Math.toRadians(84.0));
        return finite(normalizeLongitude(longitude), latitude);
    }

    private static Coordinate toLongitudeLatitude(
            List<WktCrsAxis> axes, Coordinate nativeCoordinate) {
        double longitude = Double.NaN;
        double latitude = Double.NaN;
        for (int index = 0; index < 2; index++) {
            WktCrsAxis axis = axes.get(index);
            double value =
                    (index == 0 ? nativeCoordinate.x() : nativeCoordinate.y()) * axis.unitToSi();
            switch (axis.direction()) {
                case EAST -> longitude = value;
                case WEST -> longitude = -value;
                case NORTH -> latitude = value;
                case SOUTH -> latitude = -value;
                case UP, DOWN -> throw unsupported();
            }
        }
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
            throw unsupported();
        }
        requireLatitude(latitude, Math.PI / 2.0);
        return new Coordinate(normalizeLongitude(longitude), latitude);
    }

    private static Coordinate fromLongitudeLatitude(
            List<WktCrsAxis> axes, Coordinate longitudeLatitude) {
        double[] nativeValues = new double[2];
        for (int index = 0; index < 2; index++) {
            WktCrsAxis axis = axes.get(index);
            nativeValues[index] =
                    switch (axis.direction()) {
                        case EAST -> longitudeLatitude.x() / axis.unitToSi();
                        case WEST -> -longitudeLatitude.x() / axis.unitToSi();
                        case NORTH -> longitudeLatitude.y() / axis.unitToSi();
                        case SOUTH -> -longitudeLatitude.y() / axis.unitToSi();
                        case UP, DOWN -> throw unsupported();
                    };
        }
        return finite(nativeValues[0], nativeValues[1]);
    }

    private static Coordinate toEastNorth(List<WktCrsAxis> axes, Coordinate nativeCoordinate) {
        double easting = Double.NaN;
        double northing = Double.NaN;
        for (int index = 0; index < 2; index++) {
            WktCrsAxis axis = axes.get(index);
            double value =
                    (index == 0 ? nativeCoordinate.x() : nativeCoordinate.y()) * axis.unitToSi();
            switch (axis.direction()) {
                case EAST -> easting = value;
                case WEST -> easting = -value;
                case NORTH -> northing = value;
                case SOUTH -> northing = -value;
                case UP, DOWN -> throw unsupported();
            }
        }
        return finite(easting, northing);
    }

    private static Coordinate fromEastNorth(List<WktCrsAxis> axes, Coordinate eastNorth) {
        double[] nativeValues = new double[2];
        for (int index = 0; index < 2; index++) {
            WktCrsAxis axis = axes.get(index);
            nativeValues[index] =
                    switch (axis.direction()) {
                        case EAST -> eastNorth.x() / axis.unitToSi();
                        case WEST -> -eastNorth.x() / axis.unitToSi();
                        case NORTH -> eastNorth.y() / axis.unitToSi();
                        case SOUTH -> -eastNorth.y() / axis.unitToSi();
                        case UP, DOWN -> throw unsupported();
                    };
        }
        return finite(nativeValues[0], nativeValues[1]);
    }

    private static double meridionalArc(double latitude, double semiMajor, double e2) {
        double e4 = e2 * e2;
        double e6 = e4 * e2;
        return semiMajor
                * ((1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * latitude
                        - (3.0 * e2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0)
                                * Math.sin(2.0 * latitude)
                        + (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * Math.sin(4.0 * latitude)
                        - 35.0 * e6 / 3072.0 * Math.sin(6.0 * latitude));
    }

    private static double eccentricity(CrsEllipsoid ellipsoid) {
        return Math.sqrt(eccentricitySquared(ellipsoid));
    }

    private static double eccentricitySquared(CrsEllipsoid ellipsoid) {
        double flattening = ellipsoid.flattening();
        return flattening * (2.0 - flattening);
    }

    private static double normalizeLongitude(double longitude) {
        return longitude - Math.PI * 2.0 * Math.floor((longitude + Math.PI) / (Math.PI * 2.0));
    }

    private static void requireLatitude(double latitude, double maximumAbsolute) {
        if (!Double.isFinite(latitude) || Math.abs(latitude) > maximumAbsolute) {
            throw outOfDomain();
        }
    }

    private static Coordinate finite(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw failure(
                    "CRS_TRANSFORM_NON_FINITE",
                    "Coordinate operation produced a non-finite result");
        }
        return new Coordinate(x, y);
    }

    private static double cube(double value) {
        return value * value * value;
    }

    private static double fourth(double value) {
        double square = value * value;
        return square * square;
    }

    private static double fifth(double value) {
        return fourth(value) * value;
    }

    private static double sixth(double value) {
        return cube(value) * cube(value);
    }

    private static Method method(String name) {
        return switch (name) {
            case MERCATOR_VARIANT_A -> Method.MERCATOR;
            case TRANSVERSE_MERCATOR -> Method.TRANSVERSE_MERCATOR;
            default -> throw unsupported();
        };
    }

    private static void requireTwoAxes(WktCrsDefinition definition) {
        if (definition.kind() == WktCrsKind.COMPOUND || definition.axes().size() != 2) {
            throw unsupported();
        }
    }

    private static CrsException unsupported() {
        return failure(
                "CRS_OPERATION_UNSUPPORTED",
                "No exact operation exists in the supported WKT2 profile");
    }

    private static CrsException outOfDomain() {
        return failure("CRS_COORDINATE_OUT_OF_DOMAIN", "Coordinate is outside the method domain");
    }

    private static CrsException failure(String code, String message) {
        return new CrsException(new CrsProblem(code, message, Map.of()));
    }

    private enum Method {
        IDENTITY,
        MERCATOR,
        TRANSVERSE_MERCATOR
    }

    private static final class Parameters {
        private static final String LATITUDE = "Latitude of natural origin";
        private static final String LONGITUDE = "Longitude of natural origin";
        private static final String SCALE = "Scale factor at natural origin";
        private static final String FALSE_EASTING = "False easting";
        private static final String FALSE_NORTHING = "False northing";

        private final double latitudeOrigin;
        private final double longitudeOrigin;
        private final double scale;
        private final double falseEasting;
        private final double falseNorthing;

        private Parameters(Map<String, Double> values) {
            latitudeOrigin = values.get(LATITUDE);
            longitudeOrigin = values.get(LONGITUDE);
            scale = values.get(SCALE);
            falseEasting = values.get(FALSE_EASTING);
            falseNorthing = values.get(FALSE_NORTHING);
        }

        private static void require(Map<String, Double> values, Method method) {
            if ((method != Method.MERCATOR && method != Method.TRANSVERSE_MERCATOR)
                    || values.size() != 5
                    || !values.keySet()
                            .equals(
                                    Set.of(
                                            LATITUDE,
                                            LONGITUDE,
                                            SCALE,
                                            FALSE_EASTING,
                                            FALSE_NORTHING))) {
                throw unsupported();
            }
            double scale = values.get(SCALE);
            if (!(scale > 0.0)
                    || !Double.isFinite(scale)
                    || !Double.isFinite(values.get(LATITUDE))
                    || !Double.isFinite(values.get(LONGITUDE))) {
                throw unsupported();
            }
        }
    }
}
