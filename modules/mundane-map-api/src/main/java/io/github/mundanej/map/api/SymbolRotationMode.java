package io.github.mundanej.map.api;

/** The coordinate frame used for a marker's clockwise rotation. */
public enum SymbolRotationMode {
    /** Zero degrees points right on screen regardless of map orientation. */
    SCREEN_RELATIVE,
    /** Zero degrees follows the projected map-positive x axis. */
    MAP_RELATIVE
}
