package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import java.util.Objects;

/** Immutable projected-world to screen transform and navigation state. */
public record MapViewport(
        int width, int height, double centerX, double centerY, double worldUnitsPerPixel) {
    /** Creates a viewport. */
    public MapViewport {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)) {
            throw new IllegalArgumentException("Viewport center must be finite");
        }
        if (!Double.isFinite(worldUnitsPerPixel) || worldUnitsPerPixel <= 0.0) {
            throw new IllegalArgumentException("World units per pixel must be finite and positive");
        }
    }

    /** Creates a viewport centered at the projected world origin. */
    public static MapViewport initial(int width, int height) {
        return new MapViewport(width, height, 0.0, 0.0, 100_000.0);
    }

    /** Returns this viewport with updated screen dimensions. */
    public MapViewport resized(int newWidth, int newHeight) {
        return new MapViewport(newWidth, newHeight, centerX, centerY, worldUnitsPerPixel);
    }

    /** Converts a projected-world coordinate to a screen coordinate. */
    public Coordinate worldToScreen(Coordinate world) {
        Objects.requireNonNull(world, "world");
        return new Coordinate(
                width / 2.0 + (world.x() - centerX) / worldUnitsPerPixel,
                height / 2.0 - (world.y() - centerY) / worldUnitsPerPixel);
    }

    /** Converts a screen coordinate to a projected-world coordinate. */
    public Coordinate screenToWorld(double screenX, double screenY) {
        return new Coordinate(
                centerX + (screenX - width / 2.0) * worldUnitsPerPixel,
                centerY - (screenY - height / 2.0) * worldUnitsPerPixel);
    }

    /** Returns a viewport panned by a screen-pixel drag delta. */
    public MapViewport panByPixels(double deltaX, double deltaY) {
        return new MapViewport(
                width,
                height,
                centerX - deltaX * worldUnitsPerPixel,
                centerY + deltaY * worldUnitsPerPixel,
                worldUnitsPerPixel);
    }

    /**
     * Returns a viewport zoomed around a screen anchor.
     *
     * @param factor values greater than one zoom in; values below one zoom out
     */
    public MapViewport zoomAt(double screenX, double screenY, double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new IllegalArgumentException("Zoom factor must be finite and positive");
        }
        Coordinate anchor = screenToWorld(screenX, screenY);
        double nextUnits = worldUnitsPerPixel / factor;
        double nextCenterX = anchor.x() - (screenX - width / 2.0) * nextUnits;
        double nextCenterY = anchor.y() + (screenY - height / 2.0) * nextUnits;
        return new MapViewport(width, height, nextCenterX, nextCenterY, nextUnits);
    }

    /** Returns a viewport fitted to a projected envelope with screen-pixel padding. */
    public static MapViewport fit(
            int width, int height, Envelope worldEnvelope, double paddingPixels) {
        Objects.requireNonNull(worldEnvelope, "worldEnvelope");
        if (!Double.isFinite(paddingPixels) || paddingPixels < 0.0) {
            throw new IllegalArgumentException("Padding must be finite and non-negative");
        }
        double usableWidth = Math.max(1.0, width - paddingPixels * 2.0);
        double usableHeight = Math.max(1.0, height - paddingPixels * 2.0);
        double xUnits = worldEnvelope.width() / usableWidth;
        double yUnits = worldEnvelope.height() / usableHeight;
        double units = Math.max(Math.max(xUnits, yUnits), 1.0e-9);
        Coordinate center = worldEnvelope.center();
        return new MapViewport(width, height, center.x(), center.y(), units);
    }
}

