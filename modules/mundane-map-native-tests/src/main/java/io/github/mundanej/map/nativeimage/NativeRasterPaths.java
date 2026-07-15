package io.github.mundanej.map.nativeimage;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable paths materialized from the fixed native raster inventory. */
record NativeRasterPaths(Path png, Path pngWorld, Path jpeg, Path jpegWorld, Path malformed) {
    NativeRasterPaths {
        Objects.requireNonNull(png, "png");
        Objects.requireNonNull(pngWorld, "pngWorld");
        Objects.requireNonNull(jpeg, "jpeg");
        Objects.requireNonNull(jpegWorld, "jpegWorld");
        Objects.requireNonNull(malformed, "malformed");
    }
}
