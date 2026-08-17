package io.github.mundanej.map.core;

/** Order used by encoded TileMatrixSet points relative to library x/y presentation. */
public enum TileMatrixAxisOrder {
    /** First ordinate is x/easting/longitude and second is y/northing/latitude. */
    XY,
    /** First ordinate is y/northing/latitude and second is x/easting/longitude. */
    YX
}
