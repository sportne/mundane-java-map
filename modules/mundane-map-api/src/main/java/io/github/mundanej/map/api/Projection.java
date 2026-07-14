package io.github.mundanej.map.api;

/** Strict immutable conversion between two recognized coordinate reference systems. */
public interface Projection {
    /** Returns the recognized source definition. */
    CrsDefinition sourceCrs();

    /** Returns the recognized target definition. */
    CrsDefinition targetCrs();

    /** Returns the complete accepted forward-operation domain. */
    Envelope sourceDomain();

    /** Returns the complete accepted inverse-operation domain. */
    Envelope targetDomain();

    /** Projects a source coordinate into world coordinates. */
    Coordinate project(Coordinate source);

    /** Converts projected world coordinates back to source coordinates. */
    Coordinate unproject(Coordinate projected);

    /** Strictly projects a source envelope. */
    Envelope projectEnvelope(Envelope source);

    /** Strictly unprojects a target envelope. */
    Envelope unprojectEnvelope(Envelope target);
}
