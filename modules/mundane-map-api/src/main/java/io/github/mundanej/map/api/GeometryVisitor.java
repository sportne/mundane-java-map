package io.github.mundanej.map.api;

/** Visitor called in deterministic depth-first encounter order. */
@FunctionalInterface
public interface GeometryVisitor {
    /**
     * Visits one geometry, including collection nodes before their children.
     *
     * @param geometry immutable visited value
     * @param depth zero for the root and one greater for each collection edge
     */
    void visit(Geometry geometry, int depth);
}
