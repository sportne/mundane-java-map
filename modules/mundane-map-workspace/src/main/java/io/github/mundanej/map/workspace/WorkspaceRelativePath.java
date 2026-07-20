package io.github.mundanej.map.workspace;

/**
 * Portable guarded local relative path represented with forward-slash separators.
 *
 * @param value validated portable path text
 */
public record WorkspaceRelativePath(String value) {
    /** Validates the closed portable relative-path grammar. */
    public WorkspaceRelativePath {
        WorkspaceText.text(value, "value", 4_096, true);
        if (value.startsWith("/")
                || value.endsWith("/")
                || value.indexOf('\\') >= 0
                || value.indexOf(':') >= 0) {
            throw new IllegalArgumentException("workspace path must be a portable relative path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("workspace path contains a forbidden segment");
            }
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
