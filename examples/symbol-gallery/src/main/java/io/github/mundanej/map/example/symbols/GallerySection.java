package io.github.mundanej.map.example.symbols;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable ordered gallery section. */
record GallerySection(String id, String title, List<GalleryCase> cases) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]*");

    GallerySection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(cases, "cases");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id must match [a-z][a-z0-9-]*");
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        cases = List.copyOf(cases);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("cases must not be empty");
        }
        Set<String> identifiers = new HashSet<>();
        for (GalleryCase galleryCase : cases) {
            if (!identifiers.add(galleryCase.id())) {
                throw new IllegalArgumentException(
                        "duplicate gallery case id: " + galleryCase.id());
            }
        }
    }
}
