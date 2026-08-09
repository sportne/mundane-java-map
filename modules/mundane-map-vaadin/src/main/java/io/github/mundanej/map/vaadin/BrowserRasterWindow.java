package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** One complete detached server-produced RGBA window awaiting scene staging. */
record BrowserRasterWindow(
        String bindingId,
        String bindingName,
        RgbaPixelBuffer pixels,
        Envelope imageMapBounds,
        Envelope clipMapBounds,
        Optional<RasterGridPlacement> placement,
        RasterWindow sourceWindow,
        BrowserRasterOptions options) {
    BrowserRasterWindow {
        Objects.requireNonNull(bindingId, "bindingId");
        Objects.requireNonNull(bindingName, "bindingName");
        Objects.requireNonNull(pixels, "pixels");
        Objects.requireNonNull(imageMapBounds, "imageMapBounds");
        Objects.requireNonNull(clipMapBounds, "clipMapBounds");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(sourceWindow, "sourceWindow");
        Objects.requireNonNull(options, "options");
    }

    long encodedBytes() {
        return Math.addExact(RasterResourceBatch.HEADER_BYTES, pixelBytes());
    }

    long pixelBytes() {
        return Math.multiplyExact(Math.multiplyExact((long) pixels.width(), pixels.height()), 4L);
    }

    Map<String, Object> encode(String resource) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("id", bindingId);
        value.put("name", bindingName);
        value.put("resource", resource);
        value.put("width", pixels.width());
        value.put("height", pixels.height());
        value.put("opacity", options.opacity());
        value.put("interpolation", options.interpolation().name());
        value.put(
                "sourceWindow",
                List.of(
                        sourceWindow.column(),
                        sourceWindow.row(),
                        sourceWindow.width(),
                        sourceWindow.height()));
        value.put("imageMapBounds", bounds(imageMapBounds));
        value.put("clipMapBounds", bounds(clipMapBounds));
        value.put("placement", encodedPlacement());
        return Map.copyOf(value);
    }

    private Map<String, Object> encodedPlacement() {
        if (placement.isEmpty()
                || placement.orElseThrow().kind() == RasterGridPlacement.Kind.AXIS_ALIGNED) {
            return Map.of("kind", "AXIS_ALIGNED", "bounds", bounds(imageMapBounds));
        }
        RasterAffineTransform transform = placement.orElseThrow().affineTransform().orElseThrow();
        return Map.of(
                "kind",
                "AFFINE",
                "transform",
                List.of(
                        transform.a(),
                        transform.d(),
                        transform.b(),
                        transform.e(),
                        transform.c(),
                        transform.f()));
    }

    private static List<Double> bounds(Envelope value) {
        return List.of(value.minX(), value.minY(), value.maxX(), value.maxY());
    }
}
