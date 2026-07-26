package io.github.mundanej.map.io.geopackage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.AccessDeniedException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

class GeoPackageInfrastructureCoverageTest {
    @Test
    void tileCachePolicyDistinguishesDisabledAndExactEnabledLimits() {
        GeoPackageTileCachePolicy disabled = GeoPackageTileCachePolicy.disabled();
        assertFalse(disabled.enabled());
        assertEquals(OptionalInt.empty(), disabled.maximumEntries());
        assertEquals(OptionalLong.empty(), disabled.maximumPixelBytes());
        assertEquals("GeoPackageTileCachePolicy[disabled]", disabled.toString());

        GeoPackageTileCachePolicy enabled = GeoPackageTileCachePolicy.bounded(2, 524_288);
        assertTrue(enabled.enabled());
        assertEquals(OptionalInt.of(2), enabled.maximumEntries());
        assertEquals(OptionalLong.of(524_288), enabled.maximumPixelBytes());
        assertEquals(enabled, GeoPackageTileCachePolicy.bounded(2, 524_288));
        assertEquals(enabled.hashCode(), GeoPackageTileCachePolicy.bounded(2, 524_288).hashCode());
        assertTrue(enabled.toString().contains("maximumEntries=2"));
        assertThrows(
                IllegalArgumentException.class,
                () -> GeoPackageTileCachePolicy.bounded(0, 524_288));
        assertThrows(
                IllegalArgumentException.class,
                () -> GeoPackageTileCachePolicy.bounded(1, 262_143));
    }

    @Test
    void catalogSnapshotAndTileProfileCopyAndResolveExactTablesAndZooms() {
        CrsMetadata crs = CrsMetadata.unknown(Optional.of("UNKNOWN:1"), Optional.empty());
        GeoPackageFeatureTable featureTable =
                new GeoPackageFeatureTable(
                        "features",
                        "geometry",
                        GeoPackageGeometryType.POINT,
                        "id",
                        new AttributeSchema(List.of()),
                        1,
                        crs,
                        Optional.empty(),
                        OptionalLong.of(0));
        GeoPackageTableProfile feature = new GeoPackageTableProfile(featureTable, List.of());
        GeoPackageTileTable tileTable =
                new GeoPackageTileTable("tiles", new Envelope(0, 0, 10, 10), crs, List.of(0, 1));
        GeoPackageTileMatrix matrix = new GeoPackageTileMatrix(1, 1, 1, 10, 10);
        ArrayList<GeoPackageTileMatrix> mutableMatrices = new ArrayList<>(List.of(matrix));
        GeoPackageTileProfile tile = new GeoPackageTileProfile(tileTable, mutableMatrices);
        mutableMatrices.clear();
        GeoPackageCatalog catalog =
                new GeoPackageCatalog(
                        List.of(featureTable), List.of(tileTable), DiagnosticReport.empty());
        ArrayList<GeoPackageTableProfile> mutableFeatures = new ArrayList<>(List.of(feature));
        ArrayList<GeoPackageTileProfile> mutableTiles = new ArrayList<>(List.of(tile));
        GeoPackageCatalogSnapshot snapshot =
                new GeoPackageCatalogSnapshot(catalog, mutableFeatures, mutableTiles);
        mutableFeatures.clear();
        mutableTiles.clear();

        assertEquals(feature, snapshot.requireFeature("features", "source"));
        assertEquals(tile, snapshot.requireTile("tiles", "source"));
        assertEquals(matrix, tile.matrix("source", 1));
        assertThrows(SourceException.class, () -> snapshot.requireFeature("missing", "source"));
        assertThrows(SourceException.class, () -> snapshot.requireTile("missing", "source"));
        SourceException zoom = assertThrows(SourceException.class, () -> tile.matrix("source", 2));
        assertEquals("GEOPACKAGE_PROFILE_UNSUPPORTED", zoom.terminal().code());
        assertThrows(
                NullPointerException.class,
                () -> new GeoPackageCatalogSnapshot(null, List.of(), List.of()));
    }

    @Test
    void sqliteFailureClassificationAndTemporaryDirectoryPreflightRemainStable() {
        assertEquals(
                null,
                invokePrivate(
                        GeoPackageSession.class,
                        "adapterUnavailable",
                        new Class<?>[] {String.class, Throwable.class},
                        "source",
                        new IllegalArgumentException("other")));
        SourceException nativeFailure =
                (SourceException)
                        invokePrivate(
                                GeoPackageSession.class,
                                "adapterUnavailable",
                                new Class<?>[] {String.class, Throwable.class},
                                "source",
                                new UnsatisfiedLinkError("native"));
        assertEquals("SQLITE_ADAPTER_UNAVAILABLE", nativeFailure.terminal().code());
        assertEquals("nativeLoad", nativeFailure.terminal().context().get("reason"));
        SourceException temporaryFailure =
                (SourceException)
                        invokePrivate(
                                GeoPackageSession.class,
                                "adapterUnavailable",
                                new Class<?>[] {String.class, Throwable.class},
                                "source",
                                new RuntimeException(
                                        "wrapper", new AccessDeniedException("temporary")));
        assertEquals("temporaryDirectory", temporaryFailure.terminal().context().get("reason"));
        assertEquals(
                "corrupt",
                invokePrivate(
                        GeoPackageSession.class,
                        "sqliteReason",
                        new Class<?>[] {SQLException.class},
                        new SQLiteException("corrupt", SQLiteErrorCode.SQLITE_CORRUPT)));
        assertEquals(
                "io",
                invokePrivate(
                        GeoPackageSession.class,
                        "sqliteReason",
                        new Class<?>[] {SQLException.class},
                        new SQLiteException("io", SQLiteErrorCode.SQLITE_IOERR)));
        assertEquals(
                "interrupt",
                invokePrivate(
                        GeoPackageSession.class,
                        "sqliteReason",
                        new Class<?>[] {SQLException.class},
                        new SQLiteException("interrupt", SQLiteErrorCode.SQLITE_INTERRUPT)));
        assertEquals(
                "other",
                invokePrivate(
                        GeoPackageSession.class,
                        "sqliteReason",
                        new Class<?>[] {SQLException.class},
                        new SQLException("other")));

        String previous = System.getProperty("org.sqlite.tmpdir");
        try {
            System.setProperty("org.sqlite.tmpdir", temporaryDirectoryMissingPath());
            SourceException failure =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    invokePrivate(
                                            GeoPackageSession.class,
                                            "preflightTemporaryDirectory",
                                            new Class<?>[] {String.class},
                                            "source"));
            assertEquals("temporaryDirectory", failure.terminal().context().get("reason"));
        } finally {
            if (previous == null) {
                System.clearProperty("org.sqlite.tmpdir");
            } else {
                System.setProperty("org.sqlite.tmpdir", previous);
            }
        }
    }

    @Test
    void cursorCleanupCombinesJdbcAndOwnerFailuresWithoutLosingThePrimary() {
        SQLException rowFailure = new SQLException("rows");
        SQLException statementFailure = new SQLException("statement");
        ResultSet rows =
                (ResultSet)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {ResultSet.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("close")) {
                                        throw rowFailure;
                                    }
                                    return primitiveDefault(method.getReturnType());
                                });
        PreparedStatement statement =
                (PreparedStatement)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {PreparedStatement.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("close")) {
                                        throw statementFailure;
                                    }
                                    return primitiveDefault(method.getReturnType());
                                });
        RuntimeException primary = new RuntimeException("primary");
        invokePrivate(
                GeoPackageFeatureCursor.class,
                "closeJdbc",
                new Class<?>[] {ResultSet.class, PreparedStatement.class, Throwable.class},
                rows,
                statement,
                primary);
        assertEquals(List.of(rowFailure, statementFailure), List.of(primary.getSuppressed()));

        RuntimeException next = new RuntimeException("next");
        assertEquals(
                next,
                invokePrivate(
                        GeoPackageFeatureCursor.class,
                        "suppress",
                        new Class<?>[] {Throwable.class, Throwable.class},
                        null,
                        next));
        assertEquals(
                primary,
                invokePrivate(
                        GeoPackageFeatureCursor.class,
                        "suppress",
                        new Class<?>[] {Throwable.class, Throwable.class},
                        primary,
                        next));
        assertTrue(
                (boolean)
                        invokePrivate(
                                GeoPackageFeatureCursor.class,
                                "intersects",
                                new Class<?>[] {Envelope.class, Envelope.class},
                                new Envelope(0, 0, 2, 2),
                                new Envelope(2, 2, 3, 3)));
        assertFalse(
                (boolean)
                        invokePrivate(
                                GeoPackageFeatureCursor.class,
                                "intersects",
                                new Class<?>[] {Envelope.class, Envelope.class},
                                new Envelope(0, 0, 1, 1),
                                new Envelope(2, 2, 3, 3)));
        invokePrivate(
                GeoPackageFeatureCursor.class,
                "closeJdbc",
                new Class<?>[] {ResultSet.class, PreparedStatement.class, Throwable.class},
                null,
                null,
                primary);
    }

    private static String temporaryDirectoryMissingPath() {
        return System.getProperty("java.io.tmpdir") + "/missing-g17-005";
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    private static Object invokePrivate(
            Class<?> owner, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new AssertionError(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
        }
    }
}
