package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.SourceIdentity;
import java.util.Objects;

/**
 * Immutable reference to an application-owned local source-opening policy.
 *
 * @param openerId exact versioned application opener key
 * @param identity requested source identity
 * @param path guarded portable local path
 */
public record WorkspaceSourceReference(
        String openerId, SourceIdentity identity, WorkspaceRelativePath path) {
    /** Validates the opener key and copied identity text. */
    public WorkspaceSourceReference {
        openerId = WorkspaceText.openerId(openerId);
        Objects.requireNonNull(identity, "identity");
        WorkspaceText.text(identity.id(), "identity.id", 256, true);
        WorkspaceText.text(identity.displayName(), "identity.displayName", 256, false);
        Objects.requireNonNull(path, "path");
    }
}
