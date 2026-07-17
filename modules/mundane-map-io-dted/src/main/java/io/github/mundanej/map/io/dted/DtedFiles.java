package io.github.mundanej.map.io.dted;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.file.Path;
import java.util.Objects;

/** Synchronous entry point for the strict dependency-free DTED file profile. */
public final class DtedFiles {
    private DtedFiles() {}

    /**
     * Opens one DTED file with cancellation disabled.
     *
     * @param identity caller-selected non-sensitive logical source identity
     * @param path local uncompressed DTED file
     * @param options immutable open options
     * @return open handle-free eager elevation source
     */
    public static ElevationSource open(
            SourceIdentity identity, Path path, DtedOpenOptions options) {
        return open(identity, path, options, CancellationToken.none());
    }

    /**
     * Opens one DTED file through a bounded eager transaction.
     *
     * @param identity caller-selected non-sensitive logical source identity
     * @param path local uncompressed DTED file
     * @param options immutable open options
     * @param cancellation operation-local cooperative cancellation token
     * @return open handle-free eager elevation source
     */
    public static ElevationSource open(
            SourceIdentity identity,
            Path path,
            DtedOpenOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        return DtedReader.open(identity, path, options, cancellation);
    }
}
