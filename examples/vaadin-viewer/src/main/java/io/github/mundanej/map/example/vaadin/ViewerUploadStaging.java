package io.github.mundanej.map.example.vaadin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Per-viewer bounded upload staging with fresh server identities and deterministic cleanup. */
final class ViewerUploadStaging implements AutoCloseable {
    static final int MAXIMUM_FILES_PER_BATCH = 8;
    static final long MAXIMUM_FILE_BYTES = 16L * 1024 * 1024;
    static final long MAXIMUM_BATCH_BYTES = 32L * 1024 * 1024;
    static final int MAXIMUM_SESSION_FILES = 32;
    static final long MAXIMUM_SESSION_BYTES = 64L * 1024 * 1024;
    private static final int MAXIMUM_NAME_CHARACTERS = 255;
    private static final Set<String> SHAPEFILE_EXTENSIONS =
            Set.of("shp", "shx", "dbf", "prj", "cpg");

    private final Path root;
    private final Runnable inputRegistered;
    private final List<Path> committed = new ArrayList<>();
    private final Set<Batch> activeBatches = Collections.newSetFromMap(new IdentityHashMap<>());
    private long generation;
    private long sessionBytes;
    private int sessionFiles;
    private long reservedBytes;
    private int reservedFiles;
    private boolean closed;

    ViewerUploadStaging() {
        this(createRoot());
    }

    ViewerUploadStaging(Path root) {
        this(root, () -> {});
    }

    ViewerUploadStaging(Path root, Runnable inputRegistered) {
        this.root = requireFreshDirectory(root);
        this.inputRegistered = Objects.requireNonNull(inputRegistered, "inputRegistered");
    }

    synchronized Batch begin(UploadKind kind) {
        requireOpen();
        Objects.requireNonNull(kind, "kind");
        int availableFiles = MAXIMUM_SESSION_FILES - sessionFiles - reservedFiles;
        long availableBytes = MAXIMUM_SESSION_BYTES - sessionBytes - reservedBytes;
        if (availableFiles <= 0 || availableBytes <= 0) {
            throw failure("UPLOAD_SESSION_LIMIT_EXCEEDED", null);
        }
        int fileReservation = Math.min(MAXIMUM_FILES_PER_BATCH, availableFiles);
        long byteReservation = Math.min(MAXIMUM_BATCH_BYTES, availableBytes);
        try {
            Batch batch =
                    new Batch(
                            kind,
                            generation,
                            Files.createTempDirectory(root, "batch-"),
                            fileReservation,
                            byteReservation);
            activeBatches.add(batch);
            reservedFiles += fileReservation;
            reservedBytes += byteReservation;
            return batch;
        } catch (IOException failure) {
            throw failure("UPLOAD_STAGING_UNAVAILABLE", failure);
        }
    }

    void cancel() {
        List<Batch> active;
        synchronized (this) {
            if (closed) {
                return;
            }
            generation++;
            active = List.copyOf(activeBatches);
        }
        for (Batch batch : active) {
            batch.abort();
        }
    }

    synchronized Path root() {
        return root;
    }

    @Override
    public void close() {
        List<Batch> active;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            generation++;
            active = List.copyOf(activeBatches);
            for (Batch batch : active) {
                batch.releaseReservation();
            }
            activeBatches.clear();
            committed.clear();
            sessionBytes = 0;
            sessionFiles = 0;
            reservedBytes = 0;
            reservedFiles = 0;
        }
        for (Batch batch : active) {
            batch.abort();
        }
        for (Batch batch : active) {
            batch.awaitIdle();
        }
        try {
            deleteTree(root);
        } catch (IOException failure) {
            throw failure("UPLOAD_CLEANUP_FAILED", failure);
        }
    }

    final class Batch implements AutoCloseable {
        private final UploadKind kind;
        private final long expectedGeneration;
        private final Path directory;
        private final int fileReservation;
        private final long byteReservation;
        private final Map<String, StagedFile> files = new LinkedHashMap<>();
        private String logicalStem;
        private long bytes;
        private boolean finished;
        private boolean reservationReleased;
        private volatile boolean cancelled;
        private volatile InputStream currentInput;
        private volatile OutputStream currentOutput;

        private Batch(
                UploadKind kind,
                long expectedGeneration,
                Path directory,
                int fileReservation,
                long byteReservation) {
            this.kind = kind;
            this.expectedGeneration = expectedGeneration;
            this.directory = directory;
            this.fileReservation = fileReservation;
            this.byteReservation = byteReservation;
        }

        synchronized void add(String clientName, long declaredBytes, InputStream input) {
            Objects.requireNonNull(input, "input");
            ensureActive();
            Name name = validateName(clientName);
            String normalizedStem = name.stem().toLowerCase(Locale.ROOT);
            if (logicalStem == null) {
                logicalStem = normalizedStem;
            } else if (!logicalStem.equals(normalizedStem)) {
                throw failure("UPLOAD_SIDECAR_GROUP_INVALID", null);
            }
            if (declaredBytes < 0 || declaredBytes > MAXIMUM_FILE_BYTES) {
                throw failure("UPLOAD_FILE_LIMIT_EXCEEDED", null);
            }
            if (files.size() >= MAXIMUM_FILES_PER_BATCH) {
                throw failure("UPLOAD_FILE_COUNT_EXCEEDED", null);
            }
            if (files.size() >= fileReservation) {
                throw failure("UPLOAD_SESSION_LIMIT_EXCEEDED", null);
            }
            String key = normalizedStem + '.' + name.extension();
            if (files.containsKey(key)) {
                throw failure("UPLOAD_DUPLICATE_SIDECAR", null);
            }
            Path target = directory.resolve("dataset." + name.extension());
            long written = 0;
            int emptyReads = 0;
            currentInput = input;
            try {
                inputRegistered.run();
                ensureActive();
                try (var output =
                        Files.newOutputStream(
                                target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    currentOutput = output;
                    ensureActive();
                    byte[] buffer = new byte[8192];
                    for (int count; (count = input.read(buffer)) >= 0; ) {
                        ensureActive();
                        if (count == 0) {
                            if (++emptyReads > 16) {
                                throw failure("UPLOAD_READ_STALLED", null);
                            }
                            continue;
                        }
                        emptyReads = 0;
                        written = Math.addExact(written, count);
                        if (written > MAXIMUM_FILE_BYTES
                                || Math.addExact(bytes, written) > MAXIMUM_BATCH_BYTES) {
                            throw failure("UPLOAD_BYTE_LIMIT_EXCEEDED", null);
                        }
                        if (Math.addExact(bytes, written) > byteReservation) {
                            throw failure("UPLOAD_SESSION_LIMIT_EXCEEDED", null);
                        }
                        output.write(buffer, 0, count);
                    }
                }
            } catch (ViewerUploadException failure) {
                deletePartial(target, failure);
                throw failure;
            } catch (ArithmeticException failure) {
                ViewerUploadException bounded = failure("UPLOAD_BYTE_LIMIT_EXCEEDED", failure);
                deletePartial(target, bounded);
                throw bounded;
            } catch (IOException failure) {
                ViewerUploadException io =
                        failure(cancelled ? "UPLOAD_CANCELLED" : "UPLOAD_WRITE_FAILED", failure);
                deletePartial(target, io);
                throw io;
            } finally {
                currentOutput = null;
                currentInput = null;
            }
            if (written != declaredBytes) {
                ViewerUploadException mismatch = failure("UPLOAD_LENGTH_MISMATCH", null);
                deletePartial(target, mismatch);
                throw mismatch;
            }
            bytes += written;
            files.put(key, new StagedFile(name, target, written));
        }

        synchronized UploadSelection commit() {
            ensureActive();
            UploadSelection selection = selection();
            synchronized (ViewerUploadStaging.this) {
                ensureActive();
                long otherReservedBytes = reservedBytes - byteReservation;
                int otherReservedFiles = reservedFiles - fileReservation;
                if (Math.addExact(sessionFiles, Math.addExact(otherReservedFiles, files.size()))
                                > MAXIMUM_SESSION_FILES
                        || Math.addExact(sessionBytes, Math.addExact(otherReservedBytes, bytes))
                                > MAXIMUM_SESSION_BYTES) {
                    throw failure("UPLOAD_SESSION_LIMIT_EXCEEDED", null);
                }
                sessionFiles += files.size();
                sessionBytes += bytes;
                releaseReservation();
                committed.add(directory);
                finished = true;
                activeBatches.remove(this);
            }
            return selection;
        }

        @Override
        public synchronized void close() {
            if (finished) {
                return;
            }
            finished = true;
            synchronized (ViewerUploadStaging.this) {
                activeBatches.remove(this);
                releaseReservation();
            }
            try {
                deleteTree(directory);
            } catch (IOException failure) {
                throw failure("UPLOAD_CLEANUP_FAILED", failure);
            }
        }

        private UploadSelection selection() {
            if (files.isEmpty()) {
                throw failure("UPLOAD_EMPTY", null);
            }
            return switch (kind) {
                case SHAPEFILE -> shapefileSelection();
                case RASTER, ELEVATION -> singleSelection(Set.of("tif", "tiff"));
                case WORKSPACE -> singleSelection(Set.of("xml"));
            };
        }

        private UploadSelection shapefileSelection() {
            String stem = null;
            Map<String, StagedFile> sidecars = new LinkedHashMap<>();
            for (StagedFile file : files.values()) {
                String current = file.name().stem().toLowerCase(Locale.ROOT);
                if (stem == null) {
                    stem = current;
                } else if (!stem.equals(current)) {
                    throw failure("UPLOAD_SIDECAR_GROUP_INVALID", null);
                }
                if (!SHAPEFILE_EXTENSIONS.contains(file.name().extension())) {
                    throw failure("UPLOAD_TYPE_UNSUPPORTED", null);
                }
                sidecars.put(file.name().extension(), file);
            }
            if (!sidecars.keySet().containsAll(Set.of("shp", "shx", "dbf"))) {
                throw failure("UPLOAD_SIDECAR_INCOMPLETE", null);
            }
            return new UploadSelection(kind, sidecars.get("shp").path(), files.size(), bytes);
        }

        private UploadSelection singleSelection(Set<String> extensions) {
            if (files.size() != 1) {
                throw failure("UPLOAD_FILE_COUNT_INVALID", null);
            }
            StagedFile file = files.values().iterator().next();
            if (!extensions.contains(file.name().extension())) {
                throw failure("UPLOAD_TYPE_UNSUPPORTED", null);
            }
            return new UploadSelection(kind, file.path(), 1, bytes);
        }

        private void ensureActive() {
            synchronized (ViewerUploadStaging.this) {
                if (closed || cancelled || finished || generation != expectedGeneration) {
                    throw failure("UPLOAD_CANCELLED", null);
                }
            }
        }

        private void abort() {
            cancelled = true;
            closeQuietly(currentInput);
            closeQuietly(currentOutput);
        }

        private void releaseReservation() {
            synchronized (ViewerUploadStaging.this) {
                if (reservationReleased) {
                    return;
                }
                reservationReleased = true;
                reservedFiles -= fileReservation;
                reservedBytes -= byteReservation;
            }
        }

        private synchronized void awaitIdle() {
            // Acquiring the batch monitor proves add/commit/close has left its critical section.
        }
    }

    enum UploadKind {
        SHAPEFILE,
        RASTER,
        ELEVATION,
        WORKSPACE
    }

    record UploadSelection(UploadKind kind, Path entry, int fileCount, long bytes) {
        UploadSelection {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(entry, "entry");
        }
    }

    @SuppressWarnings("serial")
    static final class ViewerUploadException extends RuntimeException {
        private final String code;

        ViewerUploadException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    private record Name(String stem, String extension) {}

    private record StagedFile(Name name, Path path, long bytes) {}

    private static Name validateName(String clientName) {
        if (clientName == null
                || clientName.isBlank()
                || clientName.length() > MAXIMUM_NAME_CHARACTERS
                || clientName.indexOf('/') >= 0
                || clientName.indexOf('\\') >= 0
                || clientName.indexOf(':') >= 0
                || clientName.equals(".")
                || clientName.equals("..")
                || clientName.chars().anyMatch(value -> Character.isISOControl(value))) {
            throw failure("UPLOAD_NAME_INVALID", null);
        }
        int dot = clientName.lastIndexOf('.');
        if (dot <= 0 || dot == clientName.length() - 1) {
            throw failure("UPLOAD_NAME_INVALID", null);
        }
        String stem = clientName.substring(0, dot);
        String extension = clientName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (stem.equals(".")
                || stem.equals("..")
                || extension.length() > 8
                || !extension.chars().allMatch(value -> value >= 'a' && value <= 'z')) {
            throw failure("UPLOAD_NAME_INVALID", null);
        }
        return new Name(stem, extension);
    }

    private synchronized void requireOpen() {
        if (closed) {
            throw new IllegalStateException("viewer upload staging is closed");
        }
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("mundane-viewer-upload-");
        } catch (IOException failure) {
            throw failure("UPLOAD_STAGING_UNAVAILABLE", failure);
        }
    }

    private static Path requireFreshDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory");
        try {
            if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory)) {
                throw new IllegalArgumentException("upload root must be a real directory");
            }
            try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
                if (children.iterator().hasNext()) {
                    throw new IllegalArgumentException("upload root must be empty");
                }
            }
            return directory.toRealPath();
        } catch (IOException failure) {
            throw failure("UPLOAD_STAGING_UNAVAILABLE", failure);
        }
    }

    private static void deletePartial(Path target, ViewerUploadException primary) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanup) {
            primary.addSuppressed(cleanup);
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // The bounded operation will observe cancellation or its own transport failure.
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        IOException primary = null;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                try {
                    if (Files.isDirectory(child) && !Files.isSymbolicLink(child)) {
                        deleteTree(child);
                    } else {
                        Files.deleteIfExists(child);
                    }
                } catch (IOException failure) {
                    if (primary == null) {
                        primary = failure;
                    } else {
                        primary.addSuppressed(failure);
                    }
                }
            }
        }
        try {
            Files.deleteIfExists(directory);
        } catch (IOException failure) {
            if (primary == null) {
                primary = failure;
            } else {
                primary.addSuppressed(failure);
            }
        }
        if (primary != null) {
            throw primary;
        }
    }

    private static ViewerUploadException failure(String code, Throwable cause) {
        return new ViewerUploadException(code, cause);
    }
}
