package io.github.mundanej.map.workspace;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Successfully read portable document paired with its non-serialized real base directory.
 *
 * @param document immutable workspace document
 * @param baseDirectory real absolute workspace parent directory
 */
public record WorkspaceFile(WorkspaceDocument document, Path baseDirectory) {
    /** Validates the immutable document and normalized absolute base. */
    public WorkspaceFile {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(baseDirectory, "baseDirectory");
        if (!baseDirectory.isAbsolute() || !baseDirectory.equals(baseDirectory.normalize())) {
            throw new IllegalArgumentException("baseDirectory must be absolute and normalized");
        }
    }
}
