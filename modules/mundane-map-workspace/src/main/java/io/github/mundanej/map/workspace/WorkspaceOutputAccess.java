package io.github.mundanej.map.workspace;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;

interface WorkspaceOutputAccess {
    /** Production JDK file-access implementation. */
    WorkspaceOutputAccess JDK =
            new WorkspaceOutputAccess() {
                @Override
                public Path realParent(Path parent) throws IOException {
                    return parent.toRealPath();
                }

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
                public WorkspaceTemporary createTemporary(Path parent) throws IOException {
                    FileAttribute<?>[] attributes = {};
                    if (Files.getFileAttributeView(parent, PosixFileAttributeView.class) != null) {
                        attributes =
                                new FileAttribute<?>[] {
                                    PosixFilePermissions.asFileAttribute(
                                            PosixFilePermissions.fromString("rw-------"))
                                };
                    }
                    for (int attempt = 0; attempt < 100; attempt++) {
                        Path candidate = parent.resolve(".mmap-" + UUID.randomUUID() + ".tmp");
                        FileChannel channel;
                        try {
                            channel =
                                    FileChannel.open(
                                            candidate,
                                            Set.of(
                                                    StandardOpenOption.CREATE_NEW,
                                                    StandardOpenOption.WRITE,
                                                    LinkOption.NOFOLLOW_LINKS),
                                            attributes);
                        } catch (FileAlreadyExistsException failure) {
                            continue;
                        }
                        try {
                            BasicFileAttributes created =
                                    Files.readAttributes(
                                            candidate,
                                            BasicFileAttributes.class,
                                            LinkOption.NOFOLLOW_LINKS);
                            if (!created.isRegularFile()
                                    || created.isSymbolicLink()
                                    || created.fileKey() == null) {
                                throw new IOException("temporary file identity is unavailable");
                            }
                            return new WorkspaceTemporary(
                                    candidate, outputChannel(channel), created.fileKey());
                        } catch (IOException failure) {
                            cleanupFailedCreation(candidate, channel, failure);
                            throw failure;
                        }
                    }
                    throw new IOException("unable to allocate a unique temporary file");
                }

                @Override
                public void moveAtomic(Path temporary, Path target) throws IOException {
                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                }

                @Override
                public void deleteTemporary(Path temporary) throws IOException {
                    Files.deleteIfExists(temporary);
                }
            };

    /** Resolves an existing parent directory without creating it. */
    Path realParent(Path parent) throws IOException;

    /** Reads no-follow target attributes or returns {@code null} when missing. */
    BasicFileAttributes attributes(Path path) throws IOException;

    /** Creates one private temporary regular file in the selected directory. */
    WorkspaceTemporary createTemporary(Path parent) throws IOException;

    /** Atomically replaces the target with the temporary file. */
    void moveAtomic(Path temporary, Path target) throws IOException;

    /** Deletes a remaining temporary file when present. */
    void deleteTemporary(Path temporary) throws IOException;

    private static WorkspaceOutputChannel outputChannel(FileChannel channel) {
        return new WorkspaceOutputChannel() {
            @Override
            public int write(java.nio.ByteBuffer source) throws IOException {
                return channel.write(source);
            }

            @Override
            public void force(boolean metadata) throws IOException {
                channel.force(metadata);
            }

            @Override
            public void close() throws IOException {
                channel.close();
            }
        };
    }

    private static void cleanupFailedCreation(
            Path candidate, FileChannel channel, IOException primary) {
        try {
            channel.close();
        } catch (IOException failure) {
            primary.addSuppressed(failure);
        }
        try {
            Files.deleteIfExists(candidate);
        } catch (IOException failure) {
            primary.addSuppressed(failure);
        }
    }
}
