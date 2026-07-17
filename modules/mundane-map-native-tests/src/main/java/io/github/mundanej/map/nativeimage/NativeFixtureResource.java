package io.github.mundanej.map.nativeimage;

import java.util.Objects;

/** One compile-time fixed, length- and hash-pinned native fixture resource. */
interface NativeFixtureResource {
    String resourceName();

    String localName();

    int length();

    String sha256();

    static void validate(
            String resourceName,
            String directory,
            String localName,
            int length,
            String sha256,
            String kind) {
        validate(resourceName, directory, localName, localName, length, sha256, kind);
    }

    static void validate(
            String resourceName,
            String directory,
            String resourceLocalName,
            String localName,
            int length,
            String sha256,
            String kind) {
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(resourceLocalName, "resourceLocalName");
        Objects.requireNonNull(localName, "localName");
        Objects.requireNonNull(sha256, "sha256");
        if (!resourceName.equals(directory + resourceLocalName)
                || resourceLocalName.contains("/")
                || resourceLocalName.contains("\\")
                || localName.contains("/")
                || localName.contains("\\")
                || length <= 0
                || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid native " + kind + " resource entry");
        }
    }
}
