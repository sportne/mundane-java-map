package io.github.mundanej.map.workspace;

import java.nio.file.Path;
import java.util.Objects;

record WorkspaceTemporary(Path path, WorkspaceOutputChannel channel, Object fileKey) {
    WorkspaceTemporary {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(fileKey, "fileKey");
    }
}
