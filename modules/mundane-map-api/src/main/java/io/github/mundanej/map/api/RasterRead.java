package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable successful raster read.
 *
 * @param sourceWindow exact source-grid window represented by the read
 * @param pixels immutable owned output pixels in request dimensions
 * @param diagnostics successful bounded diagnostic report with no error entry
 */
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
