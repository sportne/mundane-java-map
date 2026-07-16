package io.github.mundanej.map.api;

import java.util.Optional;

/** Callback-scoped view services exposed to toolkit-neutral map tools. */
public interface MapToolContext {
    /**
     * Returns the exact map-coordinate CRS.
     *
     * @return map CRS
     */
    CrsDefinition mapCrs();

    /**
     * Returns the exact viewport/display CRS.
     *
     * @return display CRS
     */
    CrsDefinition displayCrs();

    /**
     * Converts a map coordinate to screen space when representable.
     *
     * @param coordinate coordinate in {@link #mapCrs()}
     * @return logical-screen coordinate or empty
     */
    Optional<Coordinate> mapToScreen(Coordinate coordinate);

    /**
     * Converts a finite screen sample to map space when representable.
     *
     * @param screenX horizontal logical-screen coordinate
     * @param screenY vertical logical-screen coordinate
     * @return coordinate in {@link #mapCrs()} or empty
     */
    Optional<Coordinate> screenToMap(double screenX, double screenY);

    /** Requests an ordinary coalescing repaint. */
    void requestRepaint();
}
