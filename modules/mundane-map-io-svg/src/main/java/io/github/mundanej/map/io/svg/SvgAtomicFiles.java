package io.github.mundanej.map.io.svg;

import io.github.mundanej.map.api.CancellationToken;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class SvgAtomicFiles {
    private static final int WRITE_SLICE_BYTES = 65_536;

    private SvgAtomicFiles() {}

    static void write(Path target, byte[] bytes, CancellationToken cancellation) {
        write(target, bytes, cancellation, OutputAccess.JDK);
    }

    static void write(
            Path target, byte[] bytes, CancellationToken cancellation, OutputAccess access) {
        checkCancelled(cancellation);
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) {
            throw ioFailure("preflight", "missing", null);
        }
        Path realParent;
        try {
            realParent = access.realParent(parent);
        } catch (IOException exception) {
            SvgExportException failure = ioFailure("preflight", reason(exception), exception);
            suppressCancellation(failure, cancellation);
            throw failure;
        }
        Path realTarget = realParent.resolve(normalized.getFileName());
        BasicFileAttributes targetBefore;
        try {
            targetBefore = preflight(access, realParent, realTarget);
        } catch (SvgExportException failure) {
            suppressCancellation(failure, cancellation);
            throw failure;
        }
        checkCancelled(cancellation);

        Temporary temporary;
        try {
            checkCancelled(cancellation);
            temporary = access.createTemporary(realParent);
        } catch (IOException exception) {
            SvgExportException failure = ioFailure("temporary", reason(exception), exception);
            suppressCancellation(failure, cancellation);
            throw failure;
        }

        SvgExportException failure = writeTemporary(temporary.channel(), bytes, cancellation);
        if (failure == null) {
            failure = verifyTemporary(access, temporary);
            if (failure != null) {
                suppressCancellation(failure, cancellation);
            }
        }
        if (failure == null) {
            try {
                checkCancelled(cancellation);
                BasicFileAttributes targetAfter = access.attributes(realTarget);
                if (!sameTarget(targetBefore, targetAfter)) {
                    failure = ioFailure("preflight", "other", null);
                }
                checkCancelled(cancellation);
            } catch (IOException exception) {
                failure = ioFailure("preflight", reason(exception), exception);
                suppressCancellation(failure, cancellation);
            } catch (SvgExportException exception) {
                failure = exception;
            }
        }
        if (failure == null) {
            try {
                access.moveAtomic(temporary.path(), realTarget);
                temporary = null;
            } catch (AtomicMoveNotSupportedException exception) {
                failure =
                        new SvgExportException(
                                "Atomic SVG replacement is not supported",
                                new SvgExportProblem(
                                        "SVG_EXPORT_ATOMIC_MOVE_UNSUPPORTED", Map.of()),
                                exception);
                suppressCancellation(failure, cancellation);
            } catch (IOException exception) {
                failure = ioFailure("move", reason(exception), exception);
                suppressCancellation(failure, cancellation);
            }
        }
        if (temporary != null) {
            try {
                access.deleteTemporary(temporary.path());
            } catch (IOException exception) {
                SvgExportException cleanup = ioFailure("delete", reason(exception), exception);
                if (failure != null) {
                    failure.addSuppressed(cleanup);
                } else {
                    failure = cleanup;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static SvgExportException writeTemporary(
            OutputChannel channel, byte[] bytes, CancellationToken cancellation) {
        SvgExportException failure = null;
        try {
            checkCancelled(cancellation);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                checkCancelled(cancellation);
                int previousLimit = buffer.limit();
                buffer.limit(Math.min(previousLimit, buffer.position() + WRITE_SLICE_BYTES));
                try {
                    if (channel.write(buffer) <= 0) {
                        throw new IOException("temporary output made no progress");
                    }
                } finally {
                    buffer.limit(previousLimit);
                }
                checkCancelled(cancellation);
            }
        } catch (IOException exception) {
            failure = ioFailure("write", reason(exception), exception);
            suppressCancellation(failure, cancellation);
        } catch (SvgExportException exception) {
            failure = exception;
        }
        if (failure == null) {
            try {
                checkCancelled(cancellation);
                channel.force(true);
                checkCancelled(cancellation);
            } catch (IOException exception) {
                failure = ioFailure("force", reason(exception), exception);
                suppressCancellation(failure, cancellation);
            } catch (SvgExportException exception) {
                failure = exception;
            }
        }
        try {
            channel.close();
        } catch (IOException exception) {
            SvgExportException closeFailure = ioFailure("close", reason(exception), exception);
            if (failure != null) {
                failure.addSuppressed(closeFailure);
            } else {
                failure = closeFailure;
                suppressCancellation(failure, cancellation);
            }
        }
        if (failure == null) {
            try {
                checkCancelled(cancellation);
            } catch (SvgExportException exception) {
                failure = exception;
            }
        } else {
            suppressCancellation(failure, cancellation);
        }
        return failure;
    }

    private static SvgExportException verifyTemporary(OutputAccess access, Temporary temporary) {
        try {
            BasicFileAttributes written = access.attributes(temporary.path());
            if (written == null
                    || !written.isRegularFile()
                    || written.isSymbolicLink()
                    || written.fileKey() == null
                    || !temporary.fileKey().equals(written.fileKey())) {
                return ioFailure("temporary", "other", null);
            }
            return null;
        } catch (IOException exception) {
            return ioFailure("temporary", reason(exception), exception);
        }
    }

    private static BasicFileAttributes preflight(OutputAccess access, Path parent, Path target) {
        try {
            BasicFileAttributes parentAttributes = access.attributes(parent);
            if (parentAttributes == null) {
                throw ioFailure("preflight", "missing", null);
            }
            if (!parentAttributes.isDirectory() || parentAttributes.isSymbolicLink()) {
                throw ioFailure(
                        "preflight",
                        parentAttributes.isSymbolicLink() ? "symlink" : "wrongKind",
                        null);
            }
            BasicFileAttributes targetAttributes = access.attributes(target);
            if (targetAttributes != null
                    && (targetAttributes.isSymbolicLink() || !targetAttributes.isRegularFile())) {
                throw ioFailure(
                        "preflight",
                        targetAttributes.isSymbolicLink() ? "symlink" : "wrongKind",
                        null);
            }
            return targetAttributes;
        } catch (SvgExportException exception) {
            throw exception;
        } catch (IOException exception) {
            throw ioFailure("preflight", reason(exception), exception);
        }
    }

    private static boolean sameTarget(BasicFileAttributes before, BasicFileAttributes after) {
        if (before == null || after == null) {
            return before == after;
        }
        return before.isRegularFile()
                && after.isRegularFile()
                && !before.isSymbolicLink()
                && !after.isSymbolicLink()
                && before.fileKey() != null
                && before.fileKey().equals(after.fileKey())
                && before.size() == after.size()
                && before.lastModifiedTime().equals(after.lastModifiedTime());
    }

    private static void checkCancelled(CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw new SvgExportException(
                    "SVG export was cancelled",
                    new SvgExportProblem("SVG_EXPORT_CANCELLED", Map.of()));
        }
    }

    private static void suppressCancellation(
            SvgExportException primary, CancellationToken cancellation) {
        if (!cancellation.isCancellationRequested() || containsCancellation(primary)) {
            return;
        }
        primary.addSuppressed(
                new SvgExportException(
                        "SVG export was cancelled",
                        new SvgExportProblem("SVG_EXPORT_CANCELLED", Map.of())));
    }

    private static boolean containsCancellation(SvgExportException primary) {
        if (primary.problem().code().equals("SVG_EXPORT_CANCELLED")) {
            return true;
        }
        for (Throwable suppressed : primary.getSuppressed()) {
            if (suppressed instanceof SvgExportException svg
                    && svg.problem().code().equals("SVG_EXPORT_CANCELLED")) {
                return true;
            }
        }
        return false;
    }

    private static SvgExportException ioFailure(String operation, String reason, Throwable cause) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("operation", operation);
        context.put("reason", reason);
        return new SvgExportException(
                "SVG export file operation failed",
                new SvgExportProblem("SVG_EXPORT_IO_FAILED", context),
                cause);
    }

    private static String reason(IOException exception) {
        if (exception instanceof NoSuchFileException) {
            return "missing";
        }
        if (exception instanceof AccessDeniedException) {
            return "accessDenied";
        }
        if (exception instanceof FileAlreadyExistsException) {
            return "alreadyExists";
        }
        if (exception instanceof ClosedChannelException) {
            return "closed";
        }
        return "other";
    }

    record Temporary(Path path, OutputChannel channel, Object fileKey) {
        Temporary {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(fileKey, "fileKey");
        }
    }

    interface OutputChannel {
        int write(ByteBuffer source) throws IOException;

        void force(boolean metadata) throws IOException;

        void close() throws IOException;
    }

    interface OutputAccess {
        /** Production JDK output access. */
        OutputAccess JDK =
                new OutputAccess() {
                    @Override
                    public Path realParent(Path parent) throws IOException {
                        return parent.toRealPath();
                    }

                    @Override
                    public BasicFileAttributes attributes(Path path) throws IOException {
                        try {
                            return Files.readAttributes(
                                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        } catch (NoSuchFileException exception) {
                            return null;
                        }
                    }

                    @Override
                    public Temporary createTemporary(Path parent) throws IOException {
                        FileAttribute<?>[] attributes = {};
                        if (Files.getFileAttributeView(parent, PosixFileAttributeView.class)
                                != null) {
                            attributes =
                                    new FileAttribute<?>[] {
                                        PosixFilePermissions.asFileAttribute(
                                                PosixFilePermissions.fromString("rw-------"))
                                    };
                        }
                        for (int attempt = 0; attempt < 100; attempt++) {
                            Path candidate =
                                    parent.resolve(
                                            ".mundane-map-svg-" + UUID.randomUUID() + ".tmp");
                            FileChannel channel;
                            try {
                                Set<OpenOption> options =
                                        Set.of(
                                                StandardOpenOption.CREATE_NEW,
                                                StandardOpenOption.WRITE,
                                                LinkOption.NOFOLLOW_LINKS);
                                channel = FileChannel.open(candidate, options, attributes);
                            } catch (FileAlreadyExistsException exception) {
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
                                return new Temporary(
                                        candidate, outputChannel(channel), created.fileKey());
                            } catch (IOException exception) {
                                cleanupFailedCreation(candidate, channel, exception);
                                throw exception;
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

        Path realParent(Path parent) throws IOException;

        BasicFileAttributes attributes(Path path) throws IOException;

        Temporary createTemporary(Path parent) throws IOException;

        void moveAtomic(Path temporary, Path target) throws IOException;

        void deleteTemporary(Path temporary) throws IOException;
    }

    private static OutputChannel outputChannel(FileChannel channel) {
        return new OutputChannel() {
            @Override
            public int write(ByteBuffer source) throws IOException {
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
        } catch (IOException exception) {
            primary.addSuppressed(exception);
        }
        try {
            Files.deleteIfExists(candidate);
        } catch (IOException exception) {
            primary.addSuppressed(exception);
        }
    }
}
