package io.github.mundanej.map.api;

/** Meaning assigned to one axis in the library's x/y tuple convention. */
public enum CrsAxisMeaning {
    /** Longitude increasing eastward. */
    LONGITUDE,
    /** Latitude increasing northward. */
    LATITUDE,
    /** Projected easting. */
    EASTING,
    /** Projected northing. */
    NORTHING
}
