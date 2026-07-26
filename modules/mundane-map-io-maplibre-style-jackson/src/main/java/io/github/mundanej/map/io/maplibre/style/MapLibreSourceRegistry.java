package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.FeatureSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable explicit mapping from exact style source identifiers to caller-owned sources. */
public final class MapLibreSourceRegistry {
    private final Map<String, FeatureSource> sources;

    private MapLibreSourceRegistry(Map<String, FeatureSource> sources) {
        this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
    }

    /**
     * Creates an empty duplicate-detecting builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the number of registered sources.
     *
     * @return registry size
     */
    public int size() {
        return sources.size();
    }

    Optional<FeatureSource> find(String identifier) {
        return Optional.ofNullable(sources.get(identifier));
    }

    /** Mutable construction boundary that publishes one immutable registry. */
    public static final class Builder {
        private final LinkedHashMap<String, FeatureSource> sources = new LinkedHashMap<>();
        private boolean built;

        private Builder() {}

        /**
         * Registers one open caller-owned source by exact identifier.
         *
         * @param identifier exact non-blank style source identifier
         * @param source open caller-owned source
         * @return this builder
         * @throws IllegalArgumentException if an argument violates the documented constraints
         * @throws IllegalStateException if the operation is not valid in the current state or
         *     thread
         */
        public Builder register(String identifier, FeatureSource source) {
            requireMutable();
            Objects.requireNonNull(identifier, "identifier");
            Objects.requireNonNull(source, "source");
            if (identifier.isBlank()
                    || !identifier.equals(identifier.strip())
                    || identifier.length() > 1_048_576) {
                throw new IllegalArgumentException("identifier is outside the bounded profile");
            }
            if (source.isClosed()) {
                throw new IllegalStateException("source is closed");
            }
            if (sources.putIfAbsent(identifier, source) != null) {
                throw new IllegalArgumentException("duplicate source identifier");
            }
            return this;
        }

        /**
         * Publishes an immutable registry.
         *
         * @return immutable registry
         */
        public MapLibreSourceRegistry build() {
            requireMutable();
            built = true;
            return new MapLibreSourceRegistry(sources);
        }

        private void requireMutable() {
            if (built) {
                throw new IllegalStateException("builder already used");
            }
        }
    }
}
