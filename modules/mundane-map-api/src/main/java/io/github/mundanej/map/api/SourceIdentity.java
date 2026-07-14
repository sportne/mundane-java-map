package io.github.mundanej.map.api;

import java.util.Objects;

/** Bounded logical source identity that contains no implicit locator. */
public record SourceIdentity(String id, String displayName) {
    /** Validates bounded identity text. */
    public SourceIdentity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (id.isBlank() || id.length() > 256) {
            throw new IllegalArgumentException("id must be non-blank and at most 256 characters");
        }
        if (displayName.length() > 256) {
            throw new IllegalArgumentException("displayName must be at most 256 characters");
        }
    }
}
