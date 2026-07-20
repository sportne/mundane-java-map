package io.github.mundanej.map.workspace;

final class WorkspaceReadBudget {
    private final WorkspaceLimits limits;
    private int depth;
    private int elements;
    private int attributes;
    private int layers;
    private long aggregateChars;

    WorkspaceReadBudget(WorkspaceLimits limits) {
        this.limits = limits;
    }

    void enterElement() {
        depth = prospective("depth", depth, limits.depth());
        elements = prospective("elements", elements, limits.elements());
    }

    void leaveElement() {
        depth--;
    }

    void attributes(int count) {
        long requested = (long) attributes + count;
        if (requested > limits.attributes()) {
            throw WorkspaceFailures.limit("attributes", requested, limits.attributes());
        }
        attributes = (int) requested;
    }

    void value(String value) {
        if (value.length() > limits.valueChars()) {
            throw WorkspaceFailures.limit("valueChars", value.length(), limits.valueChars());
        }
        long requested;
        try {
            requested = Math.addExact(aggregateChars, value.length());
        } catch (ArithmeticException failure) {
            throw WorkspaceFailures.limit(
                    "aggregateChars", Long.MAX_VALUE, limits.aggregateChars());
        }
        if (requested > limits.aggregateChars()) {
            throw WorkspaceFailures.limit("aggregateChars", requested, limits.aggregateChars());
        }
        aggregateChars = requested;
    }

    int nextLayer() {
        layers = prospective("layers", layers, limits.layers());
        return layers - 1;
    }

    private static int prospective(String name, int current, int maximum) {
        int requested = current + 1;
        if (requested > maximum) {
            throw WorkspaceFailures.limit(name, requested, maximum);
        }
        return requested;
    }
}
