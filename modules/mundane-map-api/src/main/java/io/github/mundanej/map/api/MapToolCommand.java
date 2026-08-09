package io.github.mundanej.map.api;

/** Bounded semantic command routed to an active map tool. */
public enum MapToolCommand {
    /** Removes the most recently committed item when supported. */
    DELETE_BACKWARD,
    /** Undoes the newest retained edit when supported. */
    UNDO,
    /** Redoes the newest retained undone edit when supported. */
    REDO
}
