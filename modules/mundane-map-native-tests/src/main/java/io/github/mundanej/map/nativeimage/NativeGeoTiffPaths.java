package io.github.mundanej.map.nativeimage;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable paths materialized from the fixed native GeoTIFF inventory. */
record NativeGeoTiffPaths(
        Path rasterNone, Path rasterDeflate, Path elevationPackBits, Path elevationDeflate) {
    NativeGeoTiffPaths {
        Objects.requireNonNull(rasterNone, "rasterNone");
        Objects.requireNonNull(rasterDeflate, "rasterDeflate");
        Objects.requireNonNull(elevationPackBits, "elevationPackBits");
        Objects.requireNonNull(elevationDeflate, "elevationDeflate");
    }
}
