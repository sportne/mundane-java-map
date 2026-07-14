package io.github.mundanej.map.api;

import java.util.Optional;

/** Callback-scoped view services exposed to toolkit-neutral map tools. */
public interface MapToolContext {
    /** Returns the exact map-coordinate CRS. */
    CrsDefinition mapCrs();

    /** Returns the exact viewport/display CRS. */
    CrsDefinition displayCrs();

    /** Converts a map coordinate to screen space when representable. */
    Optional<Coordinate> mapToScreen(Coordinate coordinate);

    /** Converts a finite screen sample to map space when representable. */
    Optional<Coordinate> screenToMap(double screenX, double screenY);

    /** Requests an ordinary coalescing repaint. */
    void requestRepaint();
}
