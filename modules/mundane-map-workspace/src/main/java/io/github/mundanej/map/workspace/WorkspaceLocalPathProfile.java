package io.github.mundanej.map.workspace;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable finite primary/sidecar path profile owned by one source opener registration.
 *
 * @param branches one through eight primary suffix branches
 */
public record WorkspaceLocalPathProfile(List<WorkspaceLocalPathBranch> branches) {
    /** Validates and defensively copies primary branches. */
    public WorkspaceLocalPathProfile {
        branches = List.copyOf(branches);
        if (branches.isEmpty() || branches.size() > 8) {
            throw new IllegalArgumentException(
                    "branches must contain between one and eight entries");
        }
        Set<String> primarySuffixes = new HashSet<>();
        for (WorkspaceLocalPathBranch branch : branches) {
            String folded = branch.primarySuffix().toLowerCase(Locale.ROOT);
            if (!primarySuffixes.add(folded)) {
                throw new IllegalArgumentException(
                        "primary suffixes must be ASCII-case-insensitively unique");
            }
        }
    }

    WorkspaceLocalPathBranch requireBranch(String path, int layerIndex) {
        for (WorkspaceLocalPathBranch branch : branches) {
            if (endsWithIgnoreAsciiCase(path, branch.primarySuffix())) {
                return branch;
            }
        }
        throw WorkspaceFailures.path("suffix", layerIndex);
    }

    private static boolean endsWithIgnoreAsciiCase(String value, String suffix) {
        int offset = value.length() - suffix.length();
        if (offset < 0) {
            return false;
        }
        for (int index = 0; index < suffix.length(); index++) {
            if (foldAscii(value.charAt(offset + index)) != foldAscii(suffix.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static char foldAscii(char value) {
        return value >= 'A' && value <= 'Z' ? (char) (value + ('a' - 'A')) : value;
    }
}
