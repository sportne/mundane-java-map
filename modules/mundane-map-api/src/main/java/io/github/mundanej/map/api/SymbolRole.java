package io.github.mundanej.map.api;

/** The geometry portrayal role fulfilled by a symbol. */
public enum SymbolRole {
    /** A point marker. */
    MARKER,
    /** A line portrayal. */
    LINE,
    /** A polygon fill portrayal. */
    FILL,
    /** The temporary geometry-dependent {@link FeatureStyle} compatibility role. */
    LEGACY_GEOMETRY
}
