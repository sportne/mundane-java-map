package io.github.mundanej.map.workspace;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One closed primary suffix and its finite exact replacement-sidecar suffixes.
 *
 * @param primarySuffix ASCII-case-insensitive primary suffix
 * @param replacementSuffixes exact suffixes derived beside the primary
 */
public record WorkspaceLocalPathBranch(String primarySuffix, List<String> replacementSuffixes) {
    private static final Pattern SUFFIX = Pattern.compile("\\.[A-Za-z0-9]{1,15}");

    /** Validates and defensively copies the closed suffix branch. */
    public WorkspaceLocalPathBranch {
        requireSuffix(primarySuffix, "primarySuffix");
        replacementSuffixes = List.copyOf(replacementSuffixes);
        if (replacementSuffixes.size() > 16) {
            throw new IllegalArgumentException("replacementSuffixes has more than 16 entries");
        }
        Set<String> unique = new HashSet<>();
        for (String suffix : replacementSuffixes) {
            requireSuffix(suffix, "replacementSuffix");
            if (!unique.add(suffix)) {
                throw new IllegalArgumentException("replacement suffixes must be exact-unique");
            }
        }
    }

    static void requireSuffix(String value, String name) {
        if (value == null || !SUFFIX.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    name
                            + " must be a dot-prefixed ASCII alphanumeric suffix of at most 16 characters");
        }
    }
}
