package io.github.mundanej.map.nativeimage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded, hash-verified filesystem workspace for the fixed native resources. */
final class NativeShapefileWorkspace implements AutoCloseable {
    private final WorkspaceFiles files;
    private final Path directory;
    private final List<Path> ownedPaths = new ArrayList<>();
    private final Map<String, Path> paths = new HashMap<>();
    private boolean closed;

    private NativeShapefileWorkspace(WorkspaceFiles files, Path directory) {
        this.files = files;
        this.directory = directory;
    }

    static NativeShapefileWorkspace open() {
        return open(
                NativeShapefileResources.INVENTORY,
                NativeSmokeMain.class::getResourceAsStream,
                new JdkWorkspaceFiles());
    }

    static NativeShapefileWorkspace open(
            List<NativeShapefileResources.Entry> inventory,
            ResourceReader resources,
            WorkspaceFiles files) {
        List<NativeShapefileResources.Entry> entries =
                List.copyOf(Objects.requireNonNull(inventory, "inventory"));
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(files, "files");
        requireUniqueNames(entries);
        Path directory;
        try {
            directory = files.createTemporaryDirectory();
        } catch (IOException failure) {
            throw workspaceFailure("unable to create temporary directory", failure);
        }
        NativeShapefileWorkspace workspace = new NativeShapefileWorkspace(files, directory);
        try {
            for (NativeShapefileResources.Entry entry : entries) {
                workspace.copy(entry, resources);
            }
            return workspace;
        } catch (RuntimeException | Error failure) {
            try {
                workspace.close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    Path path(NativeShapefileResources.Entry entry) {
        Objects.requireNonNull(entry, "entry");
        if (closed) {
            throw new IllegalStateException("shapefile-workspace: workspace is closed");
        }
        Path path = paths.get(entry.localName());
        if (path == null) {
            throw new IllegalArgumentException("Resource is not in this workspace");
        }
        return path;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        paths.clear();
        Throwable failure = null;
        for (int index = ownedPaths.size() - 1; index >= 0; index--) {
            failure = delete(ownedPaths.get(index), failure);
        }
        ownedPaths.clear();
        failure = delete(directory, failure);
        if (failure != null) {
            throw workspaceFailure("unable to remove temporary workspace", failure);
        }
    }

    private void copy(NativeShapefileResources.Entry entry, ResourceReader resources) {
        byte[] bytes = read(entry, resources);
        Path target = directory.resolve(entry.localName());
        OutputStream output;
        try {
            output = files.openNew(target);
        } catch (IOException openFailure) {
            try {
                files.deleteIfExists(target);
            } catch (IOException cleanup) {
                openFailure.addSuppressed(cleanup);
            }
            throw workspaceFailure("unable to create " + entry.localName(), openFailure);
        }
        ownedPaths.add(target);
        try (output) {
            output.write(bytes);
        } catch (IOException failure) {
            throw workspaceFailure("unable to write " + entry.localName(), failure);
        }
        paths.put(entry.localName(), target);
    }

    private static byte[] read(NativeShapefileResources.Entry entry, ResourceReader resources) {
        try (InputStream input = resources.open(entry.resourceName())) {
            if (input == null) {
                throw workspaceFailure("missing resource " + entry.localName(), null);
            }
            byte[] bytes = input.readNBytes(entry.length() + 1);
            if (bytes.length != entry.length()) {
                throw workspaceFailure("length mismatch for " + entry.localName(), null);
            }
            if (!hex(sha256().digest(bytes)).equals(entry.sha256())) {
                throw workspaceFailure("hash mismatch for " + entry.localName(), null);
            }
            return bytes;
        } catch (IOException failure) {
            throw workspaceFailure("unable to read " + entry.localName(), failure);
        }
    }

    private Throwable delete(Path path, Throwable primary) {
        try {
            files.deleteIfExists(path);
            return primary;
        } catch (IOException failure) {
            if (primary == null) {
                return failure;
            }
            primary.addSuppressed(failure);
            return primary;
        }
    }

    private static void requireUniqueNames(List<NativeShapefileResources.Entry> entries) {
        java.util.HashSet<String> names = new java.util.HashSet<>();
        for (NativeShapefileResources.Entry entry : entries) {
            if (!names.add(Objects.requireNonNull(entry, "entry").localName())) {
                throw new IllegalArgumentException("Duplicate native Shapefile local name");
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static IllegalStateException workspaceFailure(String message, Throwable cause) {
        return new IllegalStateException("shapefile-workspace: " + message, cause);
    }

    @FunctionalInterface
    interface ResourceReader {
        InputStream open(String resourceName) throws IOException;
    }

    interface WorkspaceFiles {
        Path createTemporaryDirectory() throws IOException;

        OutputStream openNew(Path path) throws IOException;

        boolean deleteIfExists(Path path) throws IOException;
    }

    private static final class JdkWorkspaceFiles implements WorkspaceFiles {
        @Override
        public Path createTemporaryDirectory() throws IOException {
            return Files.createTempDirectory("mundane-map-shapefile-native-");
        }

        @Override
        public OutputStream openNew(Path path) throws IOException {
            return Files.newOutputStream(
                    path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }

        @Override
        public boolean deleteIfExists(Path path) throws IOException {
            return Files.deleteIfExists(path);
        }
    }
}
