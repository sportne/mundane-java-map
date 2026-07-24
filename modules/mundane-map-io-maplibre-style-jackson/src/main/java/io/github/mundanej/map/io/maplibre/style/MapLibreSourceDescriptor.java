package io.github.mundanej.map.io.maplibre.style;

import java.util.Objects;
import java.util.Optional;

/**
 * Detached metadata for one declared GeoJSON source.
 *
 * @param id exact style source identifier
 * @param dataLocator retained descriptive locator that is never dereferenced
 * @param attribution optional retained attribution
 */
public record MapLibreSourceDescriptor(
        String id, Optional<String> dataLocator, Optional<String> attribution) {
    /** Validates retained source metadata. */
    public MapLibreSourceDescriptor {
        id = requireIdentifier(id, "id");
        dataLocator = copyRetained(dataLocator, "dataLocator");
        attribution = copyRetained(attribution, "attribution");
    }

    private static Optional<String> copyRetained(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(text -> requireRetained(text, name));
    }

    private static String requireIdentifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || !value.equals(value.strip()) || value.length() > 1_048_576) {
            throw new IllegalArgumentException(name + " is outside the bounded profile");
        }
        return value;
    }

    private static String requireRetained(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() > 1_048_576) {
            throw new IllegalArgumentException(name + " is outside the bounded profile");
        }
        return value;
    }
}
