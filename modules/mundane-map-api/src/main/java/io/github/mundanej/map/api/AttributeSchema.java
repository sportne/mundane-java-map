package io.github.mundanej.map.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable ordered attribute schema with exact constant-time name lookup. */
public final class AttributeSchema {
    private final List<AttributeField> fields;
    private final Map<String, AttributeField> fieldsByName;

    /** Copies and validates ordered unique fields. */
    public AttributeSchema(List<AttributeField> fields) {
        this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        Map<String, AttributeField> index = new HashMap<>();
        for (AttributeField field : this.fields) {
            Objects.requireNonNull(field, "field");
            if (index.putIfAbsent(field.name(), field) != null) {
                throw new IllegalArgumentException("Duplicate schema field: " + field.name());
            }
        }
        fieldsByName = Map.copyOf(index);
    }

    /** Returns the immutable ordered fields. */
    public List<AttributeField> fields() {
        return fields;
    }

    /** Finds a declared field by exact name in constant time. */
    public Optional<AttributeField> field(String name) {
        return Optional.ofNullable(fieldsByName.get(Objects.requireNonNull(name, "name")));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AttributeSchema schema && fields.equals(schema.fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    @Override
    public String toString() {
        return "AttributeSchema[fields=" + fields + ']';
    }
}
