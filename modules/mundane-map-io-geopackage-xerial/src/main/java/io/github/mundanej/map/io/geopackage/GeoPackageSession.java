package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceException;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.sqlite.ProgressHandler;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.sqlite.SQLiteLimits;
import org.sqlite.jdbc4.JDBC4Connection;

final class GeoPackageSession implements AutoCloseable {
    private static final int PROGRESS_INTERVAL = 1_000;
    private final String sourceId;
    private final GeoPackageLimits limits;
    private final GeoPackageFile.Fingerprint fingerprint;
    private final JDBC4Connection connection;
    private ProgressState progressState;
    private SourceException permanentFailure;
    private boolean closed;

    private GeoPackageSession(
            String sourceId,
            GeoPackageLimits limits,
            GeoPackageFile.Fingerprint fingerprint,
            JDBC4Connection connection) {
        this.sourceId = sourceId;
        this.limits = limits;
        this.fingerprint = fingerprint;
        this.connection = connection;
    }

    static GeoPackageSession open(
            String sourceId,
            GeoPackageFile.Fingerprint fingerprint,
            GeoPackageLimits limits,
            CancellationToken cancellation) {
        GeoPackageFailures.checkpoint(sourceId, cancellation::isCancellationRequested, "open");
        Properties properties = new Properties();
        properties.setProperty("open_mode", "262209");
        properties.setProperty("shared_cache", "false");
        properties.setProperty("enable_load_extension", "false");
        properties.setProperty("busy_timeout", "0");
        String fileUri = immutableUri(fingerprint.path());
        preflightTemporaryDirectory(sourceId);
        JDBC4Connection connection = null;
        try {
            connection = new JDBC4Connection("jdbc:sqlite:" + fileUri, fileUri, properties);
            applyLimits(connection, limits);
            applyPolicy(connection);
            GeoPackageSession session =
                    new GeoPackageSession(sourceId, limits, fingerprint, connection);
            session.installProgress(cancellation);
            try {
                session.validateCore();
            } catch (SQLException exception) {
                throw session.queryFailure(exception, "catalog");
            }
            session.clearProgress();
            return session;
        } catch (Throwable failure) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (failure instanceof SourceException sourceFailure) {
                throw sourceFailure;
            }
            SourceException unavailable = adapterUnavailable(sourceId, failure);
            if (unavailable != null) {
                throw unavailable;
            }
            if (!(failure instanceof SQLException exception)) {
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(
                        "Unexpected checked SQLite startup failure", failure);
            }
            throw GeoPackageFailures.failure(
                    sourceId,
                    "SQLITE_OPEN_FAILED",
                    "GeoPackage SQLite session could not be established",
                    Map.of("phase", connection == null ? "connect" : "policy"),
                    exception);
        }
    }

    JDBC4Connection connection() {
        if (closed) {
            throw new IllegalStateException("GeoPackage session is closed");
        }
        return connection;
    }

    GeoPackageLimits limits() {
        return limits;
    }

    void beforeOperation(CancellationToken cancellation, String phase) {
        if (closed) {
            throw new IllegalStateException("GeoPackage session is closed");
        }
        throwIfPermanentlyFailed();
        verifyFingerprint(phase);
        GeoPackageFailures.checkpoint(sourceId, cancellation::isCancellationRequested, phase);
        installProgress(cancellation);
    }

    void afterOperation(CancellationToken cancellation, String phase) {
        clearProgress();
        GeoPackageFailures.checkpoint(sourceId, cancellation::isCancellationRequested, phase);
        verifyFingerprint(phase);
    }

    void verifyBeforePublication(CancellationToken cancellation, String phase) {
        throwIfPermanentlyFailed();
        GeoPackageFailures.checkpoint(sourceId, cancellation::isCancellationRequested, phase);
        verifyFingerprint(phase);
    }

    SourceException queryFailure(SQLException cause, String operation) {
        ProgressStop stop = progressState == null ? null : progressState.stop().get();
        if (stop == ProgressStop.CANCELLED) {
            return GeoPackageFailures.failure(
                    sourceId,
                    "SOURCE_CANCELLED",
                    "GeoPackage operation was cancelled",
                    Map.of("operation", operation),
                    cause);
        }
        if (stop == ProgressStop.VM_LIMIT) {
            return GeoPackageFailures.failure(
                    sourceId,
                    "SOURCE_LIMIT_EXCEEDED",
                    "GeoPackage operation limit exceeded",
                    Map.of(
                            "scope",
                            "sqliteQuery",
                            "limit",
                            "vmOpcodes",
                            "requested",
                            Long.toString(limits.maximumVmOpcodes() + PROGRESS_INTERVAL),
                            "maximum",
                            Long.toString(limits.maximumVmOpcodes())),
                    cause);
        }
        return GeoPackageFailures.failure(
                sourceId,
                "SQLITE_QUERY_FAILED",
                "GeoPackage query failed",
                Map.of("operation", operation, "reason", sqliteReason(cause)),
                cause);
    }

    void suppressOperationCleanup(Throwable primary, CancellationToken cancellation, String phase) {
        try {
            clearProgress();
        } catch (RuntimeException cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
        try {
            GeoPackageFailures.checkpoint(sourceId, cancellation::isCancellationRequested, phase);
            verifyFingerprint(phase);
        } catch (RuntimeException cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            ProgressHandler.clearHandler(connection);
        } catch (SQLException ignored) {
            // Connection close remains the authoritative cleanup.
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            throw GeoPackageFailures.failure(
                    sourceId,
                    "SOURCE_CLOSE_FAILED",
                    "GeoPackage source close failed",
                    Map.of(),
                    exception);
        }
    }

    private void validateCore() throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet integrity = statement.executeQuery("PRAGMA integrity_check")) {
            if (!integrity.next() || !"ok".equals(integrity.getString(1)) || integrity.next()) {
                throw new SQLException("integrity check failed");
            }
        }
        try (Statement statement = connection.createStatement();
                ResultSet foreignKeys = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (foreignKeys.next()) {
                throw new SQLException("foreign key check failed");
            }
        }
    }

    private void installProgress(CancellationToken cancellation) {
        ProgressState state =
                new ProgressState(
                        new AtomicLong(limits.maximumVmOpcodes()), new AtomicReference<>());
        progressState = state;
        try {
            ProgressHandler.setHandler(
                    connection,
                    PROGRESS_INTERVAL,
                    new ProgressHandler() {
                        @Override
                        protected int progress() {
                            if (cancellation.isCancellationRequested()) {
                                state.stop().compareAndSet(null, ProgressStop.CANCELLED);
                                return 1;
                            }
                            long previous = state.remaining().getAndAdd(-PROGRESS_INTERVAL);
                            if (previous < PROGRESS_INTERVAL) {
                                state.stop().compareAndSet(null, ProgressStop.VM_LIMIT);
                                return 1;
                            }
                            return 0;
                        }
                    });
        } catch (SQLException exception) {
            throw GeoPackageFailures.failure(
                    sourceId,
                    "SQLITE_OPEN_FAILED",
                    "GeoPackage progress policy could not be installed",
                    Map.of("phase", "policy"),
                    exception);
        }
    }

    private void clearProgress() {
        try {
            ProgressHandler.clearHandler(connection);
            progressState = null;
        } catch (SQLException exception) {
            throw GeoPackageFailures.failure(
                    sourceId,
                    "SQLITE_QUERY_FAILED",
                    "GeoPackage query cleanup failed",
                    Map.of("operation", "catalog", "reason", "other"),
                    exception);
        }
    }

    private static String immutableUri(java.nio.file.Path path) {
        URI base = path.toUri();
        return base.toASCIIString() + "?mode=ro&immutable=1";
    }

    private static void applyLimits(JDBC4Connection connection, GeoPackageLimits limits)
            throws SQLException {
        int rowLengthLimit =
                Math.addExact(
                        Math.max(limits.maximumBlobBytes(), limits.maximumTextValueCharacters()),
                        4_096);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_LENGTH, rowLengthLimit);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_SQL_LENGTH, 32_768);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_COLUMN, 512);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_EXPR_DEPTH, 64);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_COMPOUND_SELECT, 8);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_FUNCTION_ARG, 32);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_ATTACHED, 0);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_LIKE_PATTERN_LENGTH, 256);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_VARIABLE_NUMBER, 32);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_TRIGGER_DEPTH, 0);
        connection.setLimit(SQLiteLimits.SQLITE_LIMIT_WORKER_THREADS, 0);
    }

    private static void applyPolicy(JDBC4Connection connection) throws SQLException {
        String[][] policies = {
            {"query_only", "ON", "1"},
            {"trusted_schema", "OFF", "0"},
            {"foreign_keys", "ON", "1"},
            {"cell_size_check", "ON", "1"},
            {"temp_store", "MEMORY", "2"},
            {"mmap_size", "0", "0"},
            {"automatic_index", "OFF", "0"},
            {"cache_size", "-8192", "-8192"}
        };
        try (Statement statement = connection.createStatement()) {
            for (String[] policy : policies) {
                statement.execute("PRAGMA " + policy[0] + "=" + policy[1]);
                try (ResultSet result = statement.executeQuery("PRAGMA " + policy[0])) {
                    if (!result.next() || !policy[2].equals(result.getString(1)) || result.next()) {
                        throw new SQLException("SQLite policy readback failed");
                    }
                }
            }
        }
    }

    private static SourceException adapterUnavailable(String sourceId, Throwable failure) {
        boolean temporary = false;
        boolean nativeLoad = failure instanceof LinkageError;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String name = current.getClass().getName();
            nativeLoad |=
                    name.equals("org.sqlite.NativeLibraryNotFoundException")
                            || current instanceof UnsatisfiedLinkError;
            temporary |=
                    current instanceof java.nio.file.AccessDeniedException
                            || current instanceof java.nio.file.NotDirectoryException;
            if (current instanceof SQLiteException sqlite) {
                SQLiteErrorCode code = sqlite.getResultCode();
                temporary |=
                        code == SQLiteErrorCode.SQLITE_CANTOPEN_NOTEMPDIR
                                || code == SQLiteErrorCode.SQLITE_IOERR_GETTEMPPATH;
            }
        }
        if (!temporary && !nativeLoad) {
            return null;
        }
        return GeoPackageFailures.failure(
                sourceId,
                "SQLITE_ADAPTER_UNAVAILABLE",
                "The SQLite adapter could not initialize",
                Map.of("reason", temporary ? "temporaryDirectory" : "nativeLoad"),
                failure);
    }

    private static void preflightTemporaryDirectory(String sourceId) {
        String configured = System.getProperty("org.sqlite.tmpdir");
        String value = configured == null ? System.getProperty("java.io.tmpdir") : configured;
        try {
            if (value == null) {
                throw new java.io.IOException("Temporary directory is not configured");
            }
            java.nio.file.Path path = java.nio.file.Path.of(value).toAbsolutePath().normalize();
            if (!java.nio.file.Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || java.nio.file.Files.isSymbolicLink(path)
                    || !java.nio.file.Files.isWritable(path)) {
                throw new java.io.IOException("Temporary directory is unavailable");
            }
        } catch (java.io.IOException | java.nio.file.InvalidPathException exception) {
            throw GeoPackageFailures.failure(
                    sourceId,
                    "SQLITE_ADAPTER_UNAVAILABLE",
                    "The SQLite adapter temporary directory is unavailable",
                    Map.of("reason", "temporaryDirectory"),
                    exception);
        }
    }

    private static String sqliteReason(SQLException failure) {
        if (failure instanceof SQLiteException sqlite) {
            String name = sqlite.getResultCode().name();
            if (name.startsWith("SQLITE_CORRUPT") || name.equals("SQLITE_NOTADB")) {
                return "corrupt";
            }
            if (name.startsWith("SQLITE_IOERR") || name.startsWith("SQLITE_CANTOPEN")) {
                return "io";
            }
            if (name.equals("SQLITE_INTERRUPT")) {
                return "interrupt";
            }
        }
        return "other";
    }

    private void verifyFingerprint(String phase) {
        try {
            GeoPackageFile.verify(sourceId, fingerprint, phase);
        } catch (SourceException failure) {
            if ("SQLITE_INPUT_CHANGED".equals(failure.terminal().code())) {
                permanentFailure = failure;
            }
            throw failure;
        }
    }

    private void throwIfPermanentlyFailed() {
        if (permanentFailure != null) {
            throw permanentFailure;
        }
    }

    private enum ProgressStop {
        CANCELLED,
        VM_LIMIT
    }

    private record ProgressState(AtomicLong remaining, AtomicReference<ProgressStop> stop) {}
}
