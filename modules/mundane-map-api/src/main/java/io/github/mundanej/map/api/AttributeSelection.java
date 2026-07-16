package io.github.mundanej.map.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable feature-query attribute projection. */
public final class AttributeSelection {
    /** All attributes in source order. */
    public static final AttributeSelection ALL = new AttributeSelection(Kind.ALL, List.of());

    /** No attributes. */
    public static final AttributeSelection NONE = new AttributeSelection(Kind.NONE, List.of());

    private final Kind kind;
    private final List<String> orderedNames;

    private AttributeSelection(Kind kind, List<String> orderedNames) {
        this.kind = kind;
        this.orderedNames = orderedNames;
    }

    /**
     * Creates a non-empty ordered unique selection.
     *
     * @param names exact field names in requested order
     * @return immutable named-field selection
     */
    public static AttributeSelection only(List<String> names) {
        List<String> copy = List.copyOf(Objects.requireNonNull(names, "names"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("ONLY requires at least one field");
        }
        Set<String> unique = new HashSet<>();
        for (String name : copy) {
            if (!unique.add(AttributeValues.requireName(name))) {
                throw new IllegalArgumentException("Duplicate selected field: " + name);
            }
        }
        return new AttributeSelection(Kind.ONLY, copy);
    }

    /**
     * Returns ordered selected names, empty for ALL and NONE.
     *
     * @return immutable selected names
     */
    public List<String> orderedNames() {
        return List.copyOf(orderedNames);
    }

    /**
     * Returns whether this selection requests named fields only.
     *
     * @return whether this is an {@code ONLY} selection
     */
    public boolean isOnly() {
        return kind == Kind.ONLY;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AttributeSelection selection
                && kind == selection.kind
                && orderedNames.equals(selection.orderedNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, orderedNames);
    }

    @Override
    public String toString() {
        return kind == Kind.ONLY ? "ONLY" + orderedNames : kind.name();
    }

    private enum Kind {
        ALL,
        NONE,
        ONLY
    }
}
