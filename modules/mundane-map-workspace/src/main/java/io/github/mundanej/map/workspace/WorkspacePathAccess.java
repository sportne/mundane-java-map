package io.github.mundanej.map.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

interface WorkspacePathAccess {
    /** Production no-follow JDK path access. */
    WorkspacePathAccess JDK =
            new WorkspacePathAccess() {
                @Override
                public BasicFileAttributes attributes(Path path) throws IOException {
                    try {
                        return Files.readAttributes(
                                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    } catch (java.nio.file.NoSuchFileException failure) {
                        return null;
                    }
                }

                @Override
                public Path realPath(Path path) throws IOException {
                    return path.toRealPath();
                }
            };

    /** Reads no-follow attributes or returns {@code null} when missing. */
    BasicFileAttributes attributes(Path path) throws IOException;

    /** Resolves one existing path through the provider. */
    Path realPath(Path path) throws IOException;
}
