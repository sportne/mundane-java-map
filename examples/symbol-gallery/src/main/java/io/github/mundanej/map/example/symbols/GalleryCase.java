package io.github.mundanej.map.example.symbols;

import io.github.mundanej.map.api.Feature;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable named gallery case containing real renderable features. */
record GalleryCase(String id, String title, List<Feature> features, GalleryCoverage coverage) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]*");

    GalleryCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(coverage, "coverage");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id must match [a-z][a-z0-9-]*");
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        features = List.copyOf(features);
        if (features.isEmpty()) {
            throw new IllegalArgumentException("features must not be empty");
        }
    }
}
