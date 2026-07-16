package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import java.util.Objects;

/**
 * Immutable projected-world to logical-screen transform and navigation state.
 *
 * @param width positive logical-screen width in pixels
 * @param height positive logical-screen height in pixels
 * @param centerX finite projected-world x coordinate at the screen center
 * @param centerY finite projected-world y coordinate at the screen center
 * @param worldUnitsPerPixel finite positive projected-world units per logical screen pixel
 */
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
        requireFiniteVisibleEdges(width, height, centerX, centerY, worldUnitsPerPixel);
    }

    /**
     * Creates a viewport centered at the projected world origin.
     *
     * @param width positive logical-screen width in pixels
     * @param height positive logical-screen height in pixels
     * @return initial viewport using 100,000 projected-world units per pixel
     */
    public static MapViewport initial(int width, int height) {
        return new MapViewport(width, height, 0.0, 0.0, 100_000.0);
    }

    /**
     * Returns this viewport with updated screen dimensions.
     *
     * @param newWidth positive logical-screen width in pixels
     * @param newHeight positive logical-screen height in pixels
     * @return resized viewport retaining center and scale
     */
    public MapViewport resized(int newWidth, int newHeight) {
        return new MapViewport(newWidth, newHeight, centerX, centerY, worldUnitsPerPixel);
    }

    /**
     * Converts a projected-world coordinate to a screen coordinate.
     *
     * @param world coordinate in this viewport's projected-world CRS
     * @return logical-screen coordinate with positive y downward
     */
    public Coordinate worldToScreen(Coordinate world) {
        Objects.requireNonNull(world, "world");
        return new Coordinate(
                width / 2.0 + (world.x() - centerX) / worldUnitsPerPixel,
                height / 2.0 - (world.y() - centerY) / worldUnitsPerPixel);
    }

    /**
     * Converts a screen coordinate to a projected-world coordinate.
     *
     * @param screenX finite logical-screen x ordinate in pixels
     * @param screenY finite logical-screen y ordinate in pixels
     * @return coordinate in this viewport's projected-world CRS
     */
    public Coordinate screenToWorld(double screenX, double screenY) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
            throw new IllegalArgumentException("Screen coordinates must be finite");
        }
        return new Coordinate(
                centerX + (screenX - width / 2.0) * worldUnitsPerPixel,
                centerY - (screenY - height / 2.0) * worldUnitsPerPixel);
    }

    /**
     * Returns a viewport panned by a screen-pixel drag delta.
     *
     * @param deltaX finite horizontal drag in logical screen pixels
     * @param deltaY finite vertical drag in logical screen pixels
     * @return panned viewport with unchanged dimensions and scale
     */
    public MapViewport panByPixels(double deltaX, double deltaY) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
            throw new IllegalArgumentException("Pan deltas must be finite");
        }
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
     * @param screenX finite logical-screen anchor x ordinate
     * @param screenY finite logical-screen anchor y ordinate
     * @param factor values greater than one zoom in; values below one zoom out
     * @return zoomed viewport retaining the anchor's projected-world coordinate
     */
    public MapViewport zoomAt(double screenX, double screenY, double factor) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
            throw new IllegalArgumentException("Zoom anchor must be finite");
        }
        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new IllegalArgumentException("Zoom factor must be finite and positive");
        }
        Coordinate anchor = screenToWorld(screenX, screenY);
        double nextUnits = worldUnitsPerPixel / factor;
        double nextCenterX = anchor.x() - (screenX - width / 2.0) * nextUnits;
        double nextCenterY = anchor.y() + (screenY - height / 2.0) * nextUnits;
        return new MapViewport(width, height, nextCenterX, nextCenterY, nextUnits);
    }

    /**
     * Returns a viewport fitted to a projected envelope with screen-pixel padding.
     *
     * @param width positive logical-screen width in pixels
     * @param height positive logical-screen height in pixels
     * @param worldEnvelope projected-world bounds to fit
     * @param paddingPixels finite non-negative logical-screen padding on every side
     * @return centered viewport whose visible bounds contain the envelope
     */
    public static MapViewport fit(
            int width, int height, Envelope worldEnvelope, double paddingPixels) {
        Objects.requireNonNull(worldEnvelope, "worldEnvelope");
        if (!Double.isFinite(paddingPixels) || paddingPixels < 0.0) {
            throw new IllegalArgumentException("Padding must be finite and non-negative");
        }
        double doubledPadding = paddingPixels * 2.0;
        if (!Double.isFinite(doubledPadding)) {
            throw new IllegalArgumentException("Doubled viewport padding must be finite");
        }
        double usableWidth = Math.max(1.0, width - doubledPadding);
        double usableHeight = Math.max(1.0, height - doubledPadding);
        double xUnits = worldEnvelope.width() / usableWidth;
        double yUnits = worldEnvelope.height() / usableHeight;
        double units = Math.max(Math.max(xUnits, yUnits), 1.0e-9);
        Coordinate center = worldEnvelope.center();
        return new MapViewport(width, height, center.x(), center.y(), units);
    }

    /**
     * Returns the finite projected-world envelope currently visible on screen.
     *
     * @return immutable visible projected-world bounds
     */
    public Envelope visibleWorldEnvelope() {
        Coordinate topLeft = screenToWorld(0.0, 0.0);
        Coordinate bottomRight = screenToWorld(width, height);
        return new Envelope(topLeft.x(), bottomRight.y(), bottomRight.x(), topLeft.y());
    }

    private static void requireFiniteVisibleEdges(
            int width, int height, double centerX, double centerY, double worldUnitsPerPixel) {
        double halfWidth = (width / 2.0) * worldUnitsPerPixel;
        double halfHeight = (height / 2.0) * worldUnitsPerPixel;
        double visibleWidth = halfWidth * 2.0;
        double visibleHeight = halfHeight * 2.0;
        double minimumX = centerX - halfWidth;
        double maximumX = centerX + halfWidth;
        double minimumY = centerY - halfHeight;
        double maximumY = centerY + halfHeight;
        if (!Double.isFinite(halfWidth)
                || !Double.isFinite(halfHeight)
                || !Double.isFinite(visibleWidth)
                || !Double.isFinite(visibleHeight)
                || !Double.isFinite(minimumX)
                || !Double.isFinite(maximumX)
                || !Double.isFinite(minimumY)
                || !Double.isFinite(maximumY)
                || !Double.isFinite(maximumX - minimumX)
                || !Double.isFinite(maximumY - minimumY)) {
            throw new IllegalArgumentException("Viewport visible world edges must be finite");
        }
    }
}
