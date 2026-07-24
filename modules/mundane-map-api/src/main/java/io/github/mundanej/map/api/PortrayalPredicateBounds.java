package io.github.mundanej.map.api;

/** Shared structural validation for every public predicate owner. */
final class PortrayalPredicateBounds {
    static final int MAXIMUM_NODES = 131_072;
    static final int MAXIMUM_DEPTH = 64;

    private PortrayalPredicateBounds() {}

    static int validate(PortrayalPredicate predicate) {
        return validate(predicate, 1);
    }

    private static int validate(PortrayalPredicate predicate, int depth) {
        if (depth > MAXIMUM_DEPTH) {
            throw new IllegalArgumentException("predicate depth exceeds its limit");
        }
        if (!(predicate instanceof PortrayalPredicate.Logical logical)) {
            return 1;
        }
        int count = 1;
        for (PortrayalPredicate child : logical.children()) {
            count += validate(child, depth + 1);
            if (count > MAXIMUM_NODES) {
                return count;
            }
        }
        return count;
    }
}
