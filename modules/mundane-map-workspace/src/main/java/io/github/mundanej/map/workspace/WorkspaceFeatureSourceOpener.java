package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.file.Path;

/** Trusted application glue for one fixed feature-source opening policy. */
@FunctionalInterface
public interface WorkspaceFeatureSourceOpener {
    /**
     * Opens one owned feature source from a guarded primary path.
     *
     * @param identity exact persisted source identity
     * @param primary guarded existing primary file
     * @param cancellation cross-thread cancellation signal
     * @return newly opened source whose ownership transfers to the workspace session transaction
     */
    FeatureSource open(SourceIdentity identity, Path primary, CancellationToken cancellation);
}
