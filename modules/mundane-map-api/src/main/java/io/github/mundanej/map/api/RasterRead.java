package io.github.mundanej.map.api;

import java.util.Objects;

/** Immutable successful raster read. */
public record RasterRead(
        RasterWindow sourceWindow, RgbaPixelBuffer pixels, DiagnosticReport diagnostics) {
    /** Validates retained immutable values. */
    public RasterRead {
        Objects.requireNonNull(sourceWindow, "sourceWindow");
        Objects.requireNonNull(pixels, "pixels");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (diagnostics.entries().stream()
                .anyMatch(entry -> entry.severity() == DiagnosticSeverity.ERROR)) {
            throw new IllegalArgumentException("A successful raster read cannot contain errors");
        }
    }
}
