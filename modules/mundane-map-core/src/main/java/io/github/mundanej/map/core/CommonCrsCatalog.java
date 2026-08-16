package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsAxis;
import io.github.mundanej.map.api.CrsAxisDirection;
import io.github.mundanej.map.api.CrsAxisMeaning;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsEllipsoid;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.CrsProblem;
import io.github.mundanej.map.api.CrsUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.api.WktCrsAxis;
import io.github.mundanej.map.api.WktCrsDefinition;
import io.github.mundanej.map.api.WktCrsKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit immutable common-CRS catalog generated from the pinned G19-010 profile. */
public final class CommonCrsCatalog {
    /** SHA-256 of the reviewed tabular source used to reproduce this catalog. */
    public static final String SOURCE_SHA256 =
            "f91b37010154184f80b845f101839f71780d248311d112a27ae7fb5d8a38afe9";

    /** WGS 84 / World Mercator. */
    public static final CrsDefinition EPSG_3395 =
            projected(
                    "EPSG:3395",
                    new Envelope(
                            -20_037_508.342789244,
                            -15_496_570.739723718,
                            20_037_508.342789244,
                            15_496_570.739723718));

    /** WGS 84 / UTM zone 18N. */
    public static final CrsDefinition EPSG_32618 =
            projected("EPSG:32618", new Envelope(0, 0, 1_000_000, 10_000_000));

    /** WGS 84 / UTM zone 33N. */
    public static final CrsDefinition EPSG_32633 =
            projected("EPSG:32633", new Envelope(0, 0, 1_000_000, 10_000_000));

    /** NAD83 geographic longitude/latitude presentation definition. */
    public static final CrsDefinition EPSG_4269 =
            geographic("EPSG:4269", new Envelope(-180, -90, 180, 90));

    /** NAD83 / UTM zone 15N. */
    public static final CrsDefinition EPSG_26915 =
            projected("EPSG:26915", new Envelope(0, 0, 1_000_000, 10_000_000));

    /** OSGB36 geographic longitude/latitude presentation definition. */
    public static final CrsDefinition EPSG_4277 =
            geographic("EPSG:4277", new Envelope(-9.01, 49.75, 2.01, 61.01));

    /** OSGB36 / British National Grid. */
    public static final CrsDefinition EPSG_27700 =
            projected("EPSG:27700", new Envelope(-238_375, -100_000, 900_000, 1_375_000));

    private static final CrsEllipsoid WGS84 =
            new CrsEllipsoid("WGS 84", 6_378_137.0, 298.257223563);
    private static final CrsEllipsoid GRS80 =
            new CrsEllipsoid("GRS 1980", 6_378_137.0, 298.257222101);
    private static final CrsEllipsoid AIRY1830 =
            new CrsEllipsoid("Airy 1830", 6_377_563.396, 299.3249646);
    private static final double DEGREE = Math.PI / 180.0;
    private static final Map<String, WktCrsDefinition> WKT_DEFINITIONS = definitions();

    private CommonCrsCatalog() {}

    /**
     * Returns catalog identifiers in deterministic source order.
     *
     * @return immutable identifiers
     */
    public static List<String> identifiers() {
        return List.copyOf(WKT_DEFINITIONS.keySet());
    }

    /**
     * Resolves retained WKT2 metadata for an exact catalog identifier.
     *
     * @param identifier exact identifier
     * @return immutable WKT2 semantic definition
     * @throws CrsException when the identifier is outside the pinned catalog
     */
    public static WktCrsDefinition wktDefinition(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        WktCrsDefinition definition = WKT_DEFINITIONS.get(identifier);
        if (definition == null) {
            throw new CrsException(
                    new CrsProblem(
                            "CRS_REGISTRY_KEY_UNKNOWN",
                            "No common CRS metadata is registered for the exact key",
                            Map.of()));
        }
        return definition;
    }

    static CrsRegistry.Builder registerInto(CrsRegistry.Builder builder) {
        builder.registerDefinition(EPSG_3395, aliases("3395"));
        builder.registerDefinition(EPSG_32618, aliases("32618"));
        builder.registerDefinition(EPSG_32633, aliases("32633"));
        builder.registerDefinition(EPSG_4269, aliases("4269"));
        builder.registerDefinition(EPSG_26915, aliases("26915"));
        builder.registerDefinition(EPSG_4277, aliases("4277"));
        builder.registerDefinition(EPSG_27700, aliases("27700"));
        builder.registerProjection(
                projection(
                        CrsDefinitions.EPSG_4326,
                        EPSG_3395,
                        new Envelope(-180, -80, 180, 80),
                        WKT_DEFINITIONS.get("EPSG:4326"),
                        WKT_DEFINITIONS.get("EPSG:3395")));
        builder.registerProjection(
                projection(
                        CrsDefinitions.EPSG_4326,
                        EPSG_32618,
                        new Envelope(-78.000001, 0, -71.999999, 84),
                        WKT_DEFINITIONS.get("EPSG:4326"),
                        WKT_DEFINITIONS.get("EPSG:32618")));
        builder.registerProjection(
                projection(
                        CrsDefinitions.EPSG_4326,
                        EPSG_32633,
                        new Envelope(11.999999, 0, 18.000001, 84),
                        WKT_DEFINITIONS.get("EPSG:4326"),
                        WKT_DEFINITIONS.get("EPSG:32633")));
        builder.registerProjection(
                projection(
                        EPSG_4269,
                        EPSG_26915,
                        new Envelope(-96.000001, 0, -89.999999, 84),
                        WKT_DEFINITIONS.get("EPSG:4269"),
                        WKT_DEFINITIONS.get("EPSG:26915")));
        builder.registerProjection(
                projection(
                        EPSG_4277,
                        EPSG_27700,
                        EPSG_4277.coordinateDomain(),
                        WKT_DEFINITIONS.get("EPSG:4277"),
                        WKT_DEFINITIONS.get("EPSG:27700")));
        return builder;
    }

    private static Projection projection(
            CrsDefinition source,
            CrsDefinition target,
            Envelope sourceDomain,
            WktCrsDefinition sourceWkt,
            WktCrsDefinition targetWkt) {
        return new CatalogProjection(source, target, sourceDomain, sourceWkt, targetWkt);
    }

    private static Map<String, WktCrsDefinition> definitions() {
        LinkedHashMap<String, WktCrsDefinition> values = new LinkedHashMap<>();
        WktCrsDefinition wgs84 =
                geographicWkt("WGS 84", "EPSG:4326", "World Geodetic System 1984", WGS84);
        WktCrsDefinition nad83 =
                geographicWkt("NAD83", "EPSG:4269", "North American Datum 1983", GRS80);
        WktCrsDefinition osgb36 = geographicWkt("OSGB36", "EPSG:4277", "OSGB36", AIRY1830);
        values.put("EPSG:4326", wgs84);
        values.put("EPSG:3857", pseudoMercatorWkt(wgs84));
        values.put("EPSG:3395", mercator("WGS 84 / World Mercator", "EPSG:3395", wgs84));
        values.put("EPSG:32618", utm("WGS 84 / UTM zone 18N", "EPSG:32618", wgs84, -75));
        values.put("EPSG:32633", utm("WGS 84 / UTM zone 33N", "EPSG:32633", wgs84, 15));
        values.put("EPSG:4269", nad83);
        values.put("EPSG:26915", utm("NAD83 / UTM zone 15N", "EPSG:26915", nad83, -93));
        values.put("EPSG:4277", osgb36);
        values.put("EPSG:27700", britishNationalGrid(osgb36));
        WktCrsDefinition navd88 = verticalWkt();
        values.put("EPSG:5703", navd88);
        values.put("EPSG:4979", geographic3dWkt());
        values.put(
                "PROFILE:NAD83+NAVD88",
                new WktCrsDefinition(
                        "NAD83 + NAVD88 height",
                        WktCrsKind.COMPOUND,
                        Optional.of("PROFILE:NAD83+NAVD88"),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Map.of(),
                        List.of(nad83, navd88)));
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static WktCrsDefinition geographicWkt(
            String name, String id, String datum, CrsEllipsoid ellipsoid) {
        return new WktCrsDefinition(
                name,
                WktCrsKind.GEOGRAPHIC,
                Optional.of(id),
                Optional.of(datum),
                Optional.of(ellipsoid),
                List.of(
                        new WktCrsAxis(
                                "Geodetic latitude (Lat)",
                                "Lat",
                                CrsAxisDirection.NORTH,
                                1,
                                "degree",
                                DEGREE),
                        new WktCrsAxis(
                                "Geodetic longitude (Lon)",
                                "Lon",
                                CrsAxisDirection.EAST,
                                2,
                                "degree",
                                DEGREE)),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of());
    }

    private static WktCrsDefinition geographic3dWkt() {
        return new WktCrsDefinition(
                "WGS 84",
                WktCrsKind.GEOGRAPHIC,
                Optional.of("EPSG:4979"),
                Optional.of("World Geodetic System 1984"),
                Optional.of(WGS84),
                List.of(
                        new WktCrsAxis(
                                "Geodetic latitude (Lat)",
                                "Lat",
                                CrsAxisDirection.NORTH,
                                1,
                                "degree",
                                DEGREE),
                        new WktCrsAxis(
                                "Geodetic longitude (Lon)",
                                "Lon",
                                CrsAxisDirection.EAST,
                                2,
                                "degree",
                                DEGREE),
                        new WktCrsAxis(
                                "Ellipsoidal height (h)", "h", CrsAxisDirection.UP, 3, "metre", 1)),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of());
    }

    private static WktCrsDefinition projectedWkt(
            String name,
            String id,
            WktCrsDefinition base,
            String method,
            Map<String, Double> parameters) {
        return new WktCrsDefinition(
                name,
                WktCrsKind.PROJECTED,
                Optional.of(id),
                base.datumName(),
                base.ellipsoid(),
                List.of(
                        new WktCrsAxis("Easting (E)", "E", CrsAxisDirection.EAST, 1, "metre", 1),
                        new WktCrsAxis("Northing (N)", "N", CrsAxisDirection.NORTH, 2, "metre", 1)),
                base.identifier(),
                Optional.of(method),
                parameters,
                List.of());
    }

    private static WktCrsDefinition mercator(String name, String id, WktCrsDefinition base) {
        return projectedWkt(
                name,
                id,
                base,
                WktCoordinateOperation.MERCATOR_VARIANT_A,
                parameters(0, 0, 1, 0, 0));
    }

    private static WktCrsDefinition utm(
            String name, String id, WktCrsDefinition base, double centralMeridianDegrees) {
        return projectedWkt(
                name,
                id,
                base,
                WktCoordinateOperation.TRANSVERSE_MERCATOR,
                parameters(0, centralMeridianDegrees, 0.9996, 500_000, 0));
    }

    private static WktCrsDefinition britishNationalGrid(WktCrsDefinition base) {
        return projectedWkt(
                "OSGB36 / British National Grid",
                "EPSG:27700",
                base,
                WktCoordinateOperation.TRANSVERSE_MERCATOR,
                parameters(49, -2, 0.9996012717, 400_000, -100_000));
    }

    private static WktCrsDefinition pseudoMercatorWkt(WktCrsDefinition base) {
        return projectedWkt(
                "WGS 84 / Pseudo-Mercator",
                "EPSG:3857",
                base,
                "Popular Visualisation Pseudo Mercator",
                parameters(0, 0, 1, 0, 0));
    }

    private static WktCrsDefinition verticalWkt() {
        return new WktCrsDefinition(
                "NAVD88 height",
                WktCrsKind.VERTICAL,
                Optional.of("EPSG:5703"),
                Optional.of("North American Vertical Datum 1988"),
                Optional.empty(),
                List.of(
                        new WktCrsAxis(
                                "Gravity-related height (H)",
                                "H",
                                CrsAxisDirection.UP,
                                1,
                                "metre",
                                1)),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of());
    }

    private static Map<String, Double> parameters(
            double latitudeDegrees,
            double longitudeDegrees,
            double scale,
            double falseEasting,
            double falseNorthing) {
        return Map.of(
                "Latitude of natural origin", Math.toRadians(latitudeDegrees),
                "Longitude of natural origin", Math.toRadians(longitudeDegrees),
                "Scale factor at natural origin", scale,
                "False easting", falseEasting,
                "False northing", falseNorthing);
    }

    private static List<String> aliases(String code) {
        return List.of(
                "urn:ogc:def:crs:EPSG::" + code, "http://www.opengis.net/def/crs/EPSG/0/" + code);
    }

    private static CrsDefinition geographic(String id, Envelope domain) {
        return new CrsDefinition(
                id,
                CrsKind.GEOGRAPHIC,
                new CrsAxis(CrsAxisMeaning.LONGITUDE, CrsUnit.DEGREE),
                new CrsAxis(CrsAxisMeaning.LATITUDE, CrsUnit.DEGREE),
                domain);
    }

    private static CrsDefinition projected(String id, Envelope domain) {
        return new CrsDefinition(
                id,
                CrsKind.PROJECTED,
                new CrsAxis(CrsAxisMeaning.EASTING, CrsUnit.METRE),
                new CrsAxis(CrsAxisMeaning.NORTHING, CrsUnit.METRE),
                domain);
    }

    private static final class CatalogProjection implements Projection {
        private final CrsDefinition source;
        private final CrsDefinition target;
        private final Envelope sourceDomain;
        private final WktCoordinateOperation forward;
        private final WktCoordinateOperation inverse;

        private CatalogProjection(
                CrsDefinition source,
                CrsDefinition target,
                Envelope sourceDomain,
                WktCrsDefinition sourceWkt,
                WktCrsDefinition targetWkt) {
            this.source = source;
            this.target = target;
            this.sourceDomain = sourceDomain;
            this.forward = WktCoordinateOperation.between(sourceWkt, targetWkt);
            this.inverse = WktCoordinateOperation.between(targetWkt, sourceWkt);
        }

        @Override
        public CrsDefinition sourceCrs() {
            return source;
        }

        @Override
        public CrsDefinition targetCrs() {
            return target;
        }

        @Override
        public Envelope sourceDomain() {
            return sourceDomain;
        }

        @Override
        public Envelope targetDomain() {
            return target.coordinateDomain();
        }

        @Override
        public Coordinate project(Coordinate sourceCoordinate) {
            Objects.requireNonNull(sourceCoordinate, "source");
            requireContains(sourceDomain, sourceCoordinate);
            Coordinate nativeSource = new Coordinate(sourceCoordinate.y(), sourceCoordinate.x());
            return forward.transform(nativeSource);
        }

        @Override
        public Coordinate unproject(Coordinate projectedCoordinate) {
            Objects.requireNonNull(projectedCoordinate, "projected");
            requireContains(targetDomain(), projectedCoordinate);
            Coordinate nativeResult = inverse.transform(projectedCoordinate);
            return new Coordinate(nativeResult.y(), nativeResult.x());
        }

        @Override
        public Envelope projectEnvelope(Envelope sourceEnvelope) {
            return envelope(sourceEnvelope, true);
        }

        @Override
        public Envelope unprojectEnvelope(Envelope targetEnvelope) {
            return envelope(targetEnvelope, false);
        }

        private Envelope envelope(Envelope input, boolean project) {
            Objects.requireNonNull(input, "input");
            Envelope domain = project ? sourceDomain : targetDomain();
            requireContains(domain, new Coordinate(input.minX(), input.minY()));
            requireContains(domain, new Coordinate(input.maxX(), input.maxY()));
            List<Coordinate> samples = new ArrayList<>();
            for (double x : new double[] {input.minX(), input.center().x(), input.maxX()}) {
                for (double y : new double[] {input.minY(), input.center().y(), input.maxY()}) {
                    samples.add(
                            project
                                    ? project(new Coordinate(x, y))
                                    : unproject(new Coordinate(x, y)));
                }
            }
            double minX = samples.stream().mapToDouble(Coordinate::x).min().orElseThrow();
            double minY = samples.stream().mapToDouble(Coordinate::y).min().orElseThrow();
            double maxX = samples.stream().mapToDouble(Coordinate::x).max().orElseThrow();
            double maxY = samples.stream().mapToDouble(Coordinate::y).max().orElseThrow();
            return new Envelope(minX, minY, maxX, maxY);
        }

        private static void requireContains(Envelope domain, Coordinate coordinate) {
            if (!domain.contains(coordinate)) {
                throw new CrsException(
                        new CrsProblem(
                                "CRS_COORDINATE_OUT_OF_DOMAIN",
                                "Coordinate is outside the catalog operation domain",
                                Map.of()));
            }
        }
    }
}
