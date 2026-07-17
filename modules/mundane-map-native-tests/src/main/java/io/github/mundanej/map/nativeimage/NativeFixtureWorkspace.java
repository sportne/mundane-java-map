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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded, hash-verified filesystem workspace for one fixed native fixture inventory. */
final class NativeFixtureWorkspace implements AutoCloseable {
    private final WorkspaceFiles files;
    private final Path directory;
    private final String openInvariant;
    private final String cleanupInvariant;
    private final List<Path> ownedPaths = new ArrayList<>();
    private final Map<String, Path> paths = new HashMap<>();
    private boolean closed;

    private NativeFixtureWorkspace(
            WorkspaceFiles files, Path directory, String openInvariant, String cleanupInvariant) {
        this.files = files;
        this.directory = directory;
        this.openInvariant = openInvariant;
        this.cleanupInvariant = cleanupInvariant;
    }

    static NativeFixtureWorkspace openShapefile() {
        return open(
                NativeShapefileResources.INVENTORY,
                NativeSmokeMain.class::getResourceAsStream,
                new JdkWorkspaceFiles("mundane-map-shapefile-native-"));
    }

    static NativeFixtureWorkspace openRaster() {
        return open(
                NativeRasterResources.INVENTORY,
                NativeSmokeMain.class::getResourceAsStream,
                new JdkWorkspaceFiles("mundane-map-raster-native-"));
    }

    static NativeFixtureWorkspace openDted() {
        return openDted(
                NativeSmokeMain.class::getResourceAsStream,
                new JdkWorkspaceFiles("mundane-map-dted-native-"));
    }

    static NativeFixtureWorkspace openDted(ResourceReader resources, WorkspaceFiles files) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(files, "files");
        NativeFixtureWorkspace workspace = create(files, "dted-resource", "dted-cleanup");
        try {
            byte[] bytes = read(NativeDtedResources.LEVEL_ZERO, resources, "dted-resource");
            workspace.write(NativeDtedResources.LEVEL_ZERO.localName(), bytes);
            workspace.write(
                    NativeDtedResources.TRUNCATED_LOCAL_NAME,
                    Arrays.copyOf(bytes, bytes.length - 1));
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

    static NativeFixtureWorkspace open(
            List<? extends NativeFixtureResource> inventory,
            ResourceReader resources,
            WorkspaceFiles files) {
        List<? extends NativeFixtureResource> entries =
                List.copyOf(Objects.requireNonNull(inventory, "inventory"));
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(files, "files");
        requireUniqueNames(entries);
        NativeFixtureWorkspace workspace = create(files, "fixture-workspace", "fixture-workspace");
        try {
            for (NativeFixtureResource entry : entries) {
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

    private static NativeFixtureWorkspace create(
            WorkspaceFiles files, String openInvariant, String cleanupInvariant) {
        Path directory;
        try {
            directory = files.createTemporaryDirectory();
        } catch (IOException failure) {
            throw workspaceFailure(openInvariant, "unable to create temporary directory", failure);
        }
        return new NativeFixtureWorkspace(files, directory, openInvariant, cleanupInvariant);
    }

    NativeShapefilePaths shapefilePaths() {
        requireOpen();
        return new NativeShapefilePaths(
                required(NativeShapefileResources.SHP),
                required(NativeShapefileResources.SHX),
                required(NativeShapefileResources.DBF),
                required(NativeShapefileResources.CPG),
                required(NativeShapefileResources.PRJ),
                required(NativeShapefileResources.MALFORMED));
    }

    NativeRasterPaths rasterPaths() {
        requireOpen();
        return new NativeRasterPaths(
                required(NativeRasterResources.PNG),
                required(NativeRasterResources.PNG_WORLD),
                required(NativeRasterResources.JPEG),
                required(NativeRasterResources.JPEG_WORLD),
                required(NativeRasterResources.MALFORMED));
    }

    NativeDtedPaths dtedPaths() {
        requireOpen();
        return new NativeDtedPaths(
                required(NativeDtedResources.LEVEL_ZERO),
                required(NativeDtedResources.TRUNCATED_LOCAL_NAME));
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
            throw workspaceFailure(
                    cleanupInvariant, "unable to remove temporary workspace", failure);
        }
    }

    private Path required(NativeFixtureResource entry) {
        return required(entry.localName());
    }

    private Path required(String localName) {
        Path path = paths.get(localName);
        if (path == null) {
            throw new IllegalStateException("fixture-workspace: wrong fixed inventory requested");
        }
        return path;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("fixture-workspace: workspace is closed");
        }
    }

    private void copy(NativeFixtureResource entry, ResourceReader resources) {
        write(entry.localName(), read(entry, resources, openInvariant));
    }

    private void write(String localName, byte[] bytes) {
        Path target = directory.resolve(localName);
        OutputStream output;
        try {
            output = files.openNew(target);
        } catch (IOException openFailure) {
            try {
                files.deleteIfExists(target);
            } catch (IOException cleanup) {
                openFailure.addSuppressed(cleanup);
            }
            throw workspaceFailure(openInvariant, "unable to create " + localName, openFailure);
        }
        ownedPaths.add(target);
        try (output) {
            output.write(bytes);
        } catch (IOException failure) {
            throw workspaceFailure(openInvariant, "unable to write " + localName, failure);
        }
        paths.put(localName, target);
    }

    private static byte[] read(
            NativeFixtureResource entry, ResourceReader resources, String invariant) {
        try (InputStream input = resources.open(entry.resourceName())) {
            if (input == null) {
                throw workspaceFailure(invariant, "missing resource " + entry.localName(), null);
            }
            byte[] bytes = input.readNBytes(entry.length() + 1);
            if (bytes.length != entry.length()) {
                throw workspaceFailure(invariant, "length mismatch for " + entry.localName(), null);
            }
            if (!hex(sha256().digest(bytes)).equals(entry.sha256())) {
                throw workspaceFailure(invariant, "hash mismatch for " + entry.localName(), null);
            }
            return bytes;
        } catch (IOException failure) {
            throw workspaceFailure(invariant, "unable to read " + entry.localName(), failure);
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

    private static void requireUniqueNames(List<? extends NativeFixtureResource> entries) {
        HashSet<String> names = new HashSet<>();
        for (NativeFixtureResource entry : entries) {
            if (!names.add(Objects.requireNonNull(entry, "entry").localName())) {
                throw new IllegalArgumentException("Duplicate native fixture local name");
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

    private static IllegalStateException workspaceFailure(
            String invariant, String message, Throwable cause) {
        return new IllegalStateException(invariant + ": " + message, cause);
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

    private record JdkWorkspaceFiles(String prefix) implements WorkspaceFiles {
        private JdkWorkspaceFiles {
            Objects.requireNonNull(prefix, "prefix");
        }

        @Override
        public Path createTemporaryDirectory() throws IOException {
            return Files.createTempDirectory(prefix);
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
