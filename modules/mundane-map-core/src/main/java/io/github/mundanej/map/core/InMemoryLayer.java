package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.Layer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** An immutable in-memory layer snapshot. */
public final class InMemoryLayer implements Layer {
    private final String id;
    private final String name;
    private final List<Feature> features;
    private final Optional<Envelope> envelope;

    /**
     * Creates a layer by defensively copying the ordered features.
     *
     * @param id stable non-blank layer identifier
     * @param name non-blank display name
     * @param features ordered immutable feature values
     */
    public InMemoryLayer(String id, String name, List<Feature> features) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.features = List.copyOf(Objects.requireNonNull(features, "features"));

        Envelope accumulated = null;
        for (Feature feature : this.features) {
            Objects.requireNonNull(feature, "feature");
            accumulated =
                    accumulated == null
                            ? feature.geometry().envelope()
                            : accumulated.union(feature.geometry().envelope());
        }
        envelope = Optional.ofNullable(accumulated);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Feature> features() {
        return features;
    }

    @Override
    public Optional<Envelope> envelope() {
        return envelope;
    }

    private static String requireText(String value, String role) {
        Objects.requireNonNull(value, role);
        if (value.isBlank()) {
            throw new IllegalArgumentException(role + " must not be blank");
        }
        return value;
    }
}
