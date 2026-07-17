package io.github.mundanej.map.nativeimage;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable paths materialized from the fixed native DTED inventory. */
record NativeDtedPaths(Path valid, Path truncated) {
    NativeDtedPaths {
        Objects.requireNonNull(valid, "valid");
        Objects.requireNonNull(truncated, "truncated");
    }
}
