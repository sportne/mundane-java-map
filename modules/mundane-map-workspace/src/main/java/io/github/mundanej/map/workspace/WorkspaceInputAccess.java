package io.github.mundanej.map.workspace;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;

interface WorkspaceInputAccess {
    /** Production JDK file-access implementation. */
    WorkspaceInputAccess JDK =
            new WorkspaceInputAccess() {
                @Override
                public BasicFileAttributes attributes(Path path) throws IOException {
                    return Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                }

                @Override
                public SeekableByteChannel open(Path path) throws IOException {
                    return Files.newByteChannel(
                            path,
                            Set.<OpenOption>of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                }

                @Override
                public Path realParent(Path path) throws IOException {
                    return Objects.requireNonNull(path.getParent()).toRealPath();
                }
            };

    /** Reads no-follow basic file attributes. */
    BasicFileAttributes attributes(Path path) throws IOException;

    /** Opens one no-follow readable channel. */
    SeekableByteChannel open(Path path) throws IOException;

    /** Resolves the input's parent to its real path. */
    Path realParent(Path path) throws IOException;
}
