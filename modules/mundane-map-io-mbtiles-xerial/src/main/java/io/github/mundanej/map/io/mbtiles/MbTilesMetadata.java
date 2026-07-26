package io.github.mundanej.map.io.mbtiles;

import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.Envelope;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Detached immutable metadata for one strict MBTiles 1.3 raster tileset.
 *
 * @param name required tileset name
 * @param format normalized raster format
 * @param bounds optional descriptive WGS 84 bounds
 * @param center optional descriptive WGS 84 center
 * @param minimumZoom optional declared minimum zoom
 * @param maximumZoom optional declared maximum zoom
 * @param type optional tileset type
 * @param revision optional tileset revision from the metadata {@code version} row
 * @param description optional plain description
 * @param attribution optional plain, unrendered attribution
 * @param zoomLevels actual populated zoom levels
 * @param openingDiagnostics bounded opening warnings
 */
public record MbTilesMetadata(
        String name,
        EncodedRasterFormat format,
        Optional<Envelope> bounds,
        Optional<MbTilesCenter> center,
        OptionalInt minimumZoom,
        OptionalInt maximumZoom,
        Optional<String> type,
        Optional<String> revision,
        Optional<String> description,
        Optional<String> attribution,
        List<Integer> zoomLevels,
        DiagnosticReport openingDiagnostics) {
    /** Defensively copies the metadata graph. */
    public MbTilesMetadata {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(minimumZoom, "minimumZoom");
        Objects.requireNonNull(maximumZoom, "maximumZoom");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(attribution, "attribution");
        zoomLevels = List.copyOf(Objects.requireNonNull(zoomLevels, "zoomLevels"));
        Objects.requireNonNull(openingDiagnostics, "openingDiagnostics");
        if (name.isBlank() || zoomLevels.isEmpty()) {
            throw new IllegalArgumentException("MBTiles metadata requires a name and zoom levels");
        }
        validateDeclaredZoom(minimumZoom, "minimumZoom");
        validateDeclaredZoom(maximumZoom, "maximumZoom");
        if (minimumZoom.isPresent()
                && maximumZoom.isPresent()
                && minimumZoom.getAsInt() > maximumZoom.getAsInt()) {
            throw new IllegalArgumentException("MBTiles minimum zoom exceeds maximum zoom");
        }
        int previous = -1;
        for (Integer boxedZoom : zoomLevels) {
            int zoom = Objects.requireNonNull(boxedZoom, "zoomLevels element");
            if (zoom < 0 || zoom > 22 || zoom <= previous) {
                throw new IllegalArgumentException(
                        "MBTiles zoom levels must be unique, ascending, and from 0 through 22");
            }
            if ((minimumZoom.isPresent() && zoom < minimumZoom.getAsInt())
                    || (maximumZoom.isPresent() && zoom > maximumZoom.getAsInt())) {
                throw new IllegalArgumentException(
                        "MBTiles populated zoom is outside its declared range");
            }
            previous = zoom;
        }
    }

    private static void validateDeclaredZoom(OptionalInt zoom, String name) {
        if (zoom.isPresent() && (zoom.getAsInt() < 0 || zoom.getAsInt() > 22)) {
            throw new IllegalArgumentException(name + " must be from 0 through 22");
        }
    }
}
