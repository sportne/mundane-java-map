package io.github.mundanej.map.api;

/** Strict immutable conversion between two recognized coordinate reference systems. */
public interface Projection {
    /**
     * Returns the recognized source definition.
     *
     * @return exact source definition
     */
    CrsDefinition sourceCrs();

    /**
     * Returns the recognized target definition.
     *
     * @return exact target definition
     */
    CrsDefinition targetCrs();

    /**
     * Returns the complete accepted forward-operation domain.
     *
     * @return valid source envelope
     */
    Envelope sourceDomain();

    /**
     * Returns the complete accepted inverse-operation domain.
     *
     * @return valid target envelope
     */
    Envelope targetDomain();

    /**
     * Projects a source coordinate into world coordinates.
     *
     * @param source coordinate in {@link #sourceCrs()}
     * @return coordinate in {@link #targetCrs()}
     */
    Coordinate project(Coordinate source);

    /**
     * Converts projected world coordinates back to source coordinates.
     *
     * @param projected coordinate in {@link #targetCrs()}
     * @return coordinate in {@link #sourceCrs()}
     */
    Coordinate unproject(Coordinate projected);

    /**
     * Strictly projects a source envelope.
     *
     * @param source envelope in {@link #sourceCrs()}
     * @return conservative envelope in {@link #targetCrs()}
     */
    Envelope projectEnvelope(Envelope source);

    /**
     * Strictly unprojects a target envelope.
     *
     * @param target envelope in {@link #targetCrs()}
     * @return conservative envelope in {@link #sourceCrs()}
     */
    Envelope unprojectEnvelope(Envelope target);
}
