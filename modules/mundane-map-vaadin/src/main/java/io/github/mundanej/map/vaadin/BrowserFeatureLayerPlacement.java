package io.github.mundanej.map.vaadin;

/** Closed browser paint lane for a feature-source binding. */
public enum BrowserFeatureLayerPlacement {
    /** Paint before raster and elevation windows as part of the map background. */
    BASEMAP,

    /** Paint after raster and elevation windows as ordinary vector content. */
    OVERLAY
}
