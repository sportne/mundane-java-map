package io.github.mundanej.map.io.http.tiles;

/** Approved decoded-tile cache profiles. */
public enum HttpTileCachePolicy {
    /** Retain no client-side tile cache. */
    DISABLED,
    /** Retain successful decoded tiles in a bounded in-memory cache. */
    MEMORY
}
