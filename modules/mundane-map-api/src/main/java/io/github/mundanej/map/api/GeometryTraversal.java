package io.github.mundanej.map.api;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Deterministic, bounded, non-recursive geometry-tree traversal. */
public final class GeometryTraversal {
    private GeometryTraversal() {}

    /**
     * Visits a complete geometry tree in depth-first encounter order.
     *
     * @param root immutable root
     * @param visitor visitor invoked for each node
     * @param limits safety limits
     * @throws GeometryException when traversal exceeds a configured limit
     */
    public static void visit(Geometry root, GeometryVisitor visitor, GeometryLimits limits) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(visitor, "visitor");
        Objects.requireNonNull(limits, "limits");
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(new Node(root, 0));
        long elements = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (node.depth() > limits.maxDepth()) {
                throw GeometryException.limit("maxDepth", node.depth(), limits.maxDepth());
            }
            visitor.visit(node.geometry(), node.depth());
            if (node.geometry() instanceof GeometryCollection collection) {
                elements = Math.addExact(elements, collection.geometries().size());
                if (elements > limits.maxCollectionElements()) {
                    throw GeometryException.limit(
                            "maxCollectionElements", elements, limits.maxCollectionElements());
                }
                for (int index = collection.geometries().size() - 1; index >= 0; index--) {
                    pending.push(new Node(collection.geometries().get(index), node.depth() + 1));
                }
            }
        }
    }

    private record Node(Geometry geometry, int depth) {}
}
