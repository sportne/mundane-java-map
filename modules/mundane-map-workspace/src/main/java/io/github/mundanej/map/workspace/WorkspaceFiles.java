package io.github.mundanej.map.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/** Bounded secure local workspace file operations. */
public final class WorkspaceFiles {
    private WorkspaceFiles() {}

    /**
     * Reads one strict version 1 local workspace snapshot.
     *
     * @param path existing non-symlink {@code .mmap.xml} file
     * @param limits bounded read limits
     * @return immutable document paired with its real parent directory
     * @throws WorkspaceException for a stable input, encoding, XML, grammar, value, or limit
     *     failure
     */
    public static WorkspaceFile read(Path path, WorkspaceLimits limits) {
        return read(path, limits, WorkspaceInputAccess.JDK);
    }

    static WorkspaceFile read(Path path, WorkspaceLimits limits, WorkspaceInputAccess access) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(access, "access");
        Path absolute = path.toAbsolutePath().normalize();
        Path fileName = absolute.getFileName();
        if (fileName == null || !fileName.toString().endsWith(WorkspaceText.SUFFIX)) {
            throw WorkspaceFailures.io("wrongKind", null);
        }
        BasicFileAttributes before = attributes(absolute, access);
        if (before.isSymbolicLink()) {
            throw WorkspaceFailures.io("symlink", null);
        }
        if (!before.isRegularFile()) {
            throw WorkspaceFailures.io("wrongKind", null);
        }
        if (before.size() > limits.inputOutputBytes()) {
            throw WorkspaceFailures.limit("inputBytes", before.size(), limits.inputOutputBytes());
        }
        byte[] bytes = snapshot(absolute, limits, access);
        BasicFileAttributes after = attributes(absolute, access);
        if (changed(before, after, bytes.length)) {
            throw WorkspaceFailures.io("changed", null);
        }
        String xml = decode(bytes);
        long decodedBytes;
        try {
            decodedBytes = Math.multiplyExact((long) xml.length(), 2L);
        } catch (ArithmeticException failure) {
            throw WorkspaceFailures.limit(
                    "operationBytes", Long.MAX_VALUE, limits.operationBytes());
        }
        operationBytes(bytes.length, decodedBytes, 0L, limits);
        WorkspaceDocument document = WorkspaceXmlReader.read(xml, limits);
        operationBytes(
                bytes.length,
                decodedBytes,
                WorkspaceDocument.logicalModelBytes(document.view(), document.layers()),
                limits);
        Path base;
        try {
            base = access.realParent(absolute);
        } catch (IOException failure) {
            throw WorkspaceFailures.io("open", failure);
        }
        return new WorkspaceFile(document, base);
    }

    private static void operationBytes(
            long input, long decoded, long model, WorkspaceLimits limits) {
        long requested;
        try {
            requested = Math.addExact(Math.addExact(input, decoded), model);
        } catch (ArithmeticException failure) {
            requested = Long.MAX_VALUE;
        }
        if (requested > limits.operationBytes()) {
            throw WorkspaceFailures.limit("operationBytes", requested, limits.operationBytes());
        }
    }

    private static byte[] snapshot(Path path, WorkspaceLimits limits, WorkspaceInputAccess access) {
        SeekableByteChannel channel;
        try {
            channel = access.open(path);
        } catch (IOException failure) {
            throw WorkspaceFailures.io("open", failure);
        }
        WorkspaceException primary = null;
        byte[] result = null;
        try {
            var sink = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(8_192);
            while (true) {
                long remaining = limits.inputOutputBytes() - sink.size();
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), remaining + 1L));
                int count;
                try {
                    count = channel.read(buffer);
                } catch (IOException failure) {
                    throw WorkspaceFailures.io("read", failure);
                }
                if (count < 0) {
                    result = sink.toByteArray();
                    break;
                }
                long requested = (long) sink.size() + count;
                if (requested > limits.inputOutputBytes()) {
                    throw WorkspaceFailures.limit(
                            "inputBytes", requested, limits.inputOutputBytes());
                }
                sink.write(buffer.array(), 0, count);
            }
        } catch (WorkspaceException failure) {
            primary = failure;
        }
        try {
            channel.close();
        } catch (IOException failure) {
            WorkspaceException close = WorkspaceFailures.io("close", failure);
            if (primary == null) {
                primary = close;
            } else {
                primary.addSuppressed(close);
            }
        }
        if (primary != null) {
            throw primary;
        }
        return Objects.requireNonNull(result, "snapshot bytes");
    }

    private static BasicFileAttributes attributes(Path path, WorkspaceInputAccess access) {
        try {
            return access.attributes(path);
        } catch (java.nio.file.NoSuchFileException failure) {
            throw WorkspaceFailures.io("missing", failure);
        } catch (IOException failure) {
            throw WorkspaceFailures.io("size", failure);
        }
    }

    private static boolean changed(
            BasicFileAttributes before, BasicFileAttributes after, int byteCount) {
        return !after.isRegularFile()
                || after.isSymbolicLink()
                || before.size() != after.size()
                || after.size() != byteCount
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey());
    }

    private static String decode(byte[] input) {
        int offset = 0;
        if (startsWith(input, 0xEF, 0xBB, 0xBF)) {
            offset = 3;
        } else if (startsWith(input, 0xFE, 0xFF)
                || startsWith(input, 0xFF, 0xFE)
                || startsWith(input, 0x00, 0x00, 0xFE, 0xFF)
                || startsWith(input, 0xFF, 0xFE, 0x00, 0x00)) {
            throw WorkspaceFailures.encoding("bom", null);
        }
        var decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        String result;
        try {
            result =
                    decoder.decode(ByteBuffer.wrap(input, offset, input.length - offset))
                            .toString();
        } catch (CharacterCodingException failure) {
            throw WorkspaceFailures.encoding("malformed", failure);
        }
        try {
            WorkspaceText.requireXml(result, "workspace XML");
        } catch (IllegalArgumentException failure) {
            throw WorkspaceFailures.encoding("xmlCharacter", failure);
        }
        return result;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (Byte.toUnsignedInt(bytes[index]) != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
