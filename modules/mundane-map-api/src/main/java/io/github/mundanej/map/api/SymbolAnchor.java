package io.github.mundanej.map.api;

/** The point of a marker's nominal rectangle placed at its feature anchor. */
public enum SymbolAnchor {
    /** Rectangle center. */
    CENTER,
    /** Top center. */
    NORTH,
    /** Top right. */
    NORTH_EAST,
    /** Right center. */
    EAST,
    /** Bottom right. */
    SOUTH_EAST,
    /** Bottom center. */
    SOUTH,
    /** Bottom left. */
    SOUTH_WEST,
    /** Left center. */
    WEST,
    /** Top left. */
    NORTH_WEST
}
