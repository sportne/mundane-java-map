package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.core.FeatureQueryAccounting;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GeoPackageFeatureCursor implements FeatureCursor {
    private final GeoPackageFeatureSource owner;
    private final GeoPackageSession session;
    private final GeoPackageFeatureTable table;
    private final FeatureQuery query;
    private final List<GeoPackageAttributeColumn> projection;
    private final CancellationToken cancellation;
    private final FeatureQueryAccounting accounting;
    private final String sourceId;
    private final PreparedStatement statement;
    private final ResultSet rows;
    private final List<SourceDiagnostic> warnings = new ArrayList<>();
    private final int maximumWarnings;
    private long omittedWarnings;
    private long physicalRow;
    private long decodedTextCharacters;
    private long decodedOwnedBytes;
    private FeatureRecord current;
    private boolean closed;

    GeoPackageFeatureCursor(
            GeoPackageFeatureSource owner,
            GeoPackageSession session,
            GeoPackageFeatureTable table,
            List<GeoPackageAttributeColumn> projection,
            FeatureQuery query,
            CancellationToken cancellation,
            FeatureQueryLimits limits,
            String sourceId) {
        this.owner = owner;
        this.session = session;
        this.table = table;
        this.projection = List.copyOf(projection);
        this.query = query;
        this.cancellation = cancellation;
        this.sourceId = sourceId;
        maximumWarnings = limits.retainedWarnings();
        accounting = new FeatureQueryAccounting(sourceId, limits);
        session.beforeOperation(cancellation, "cursor");
        PreparedStatement prepared = null;
        ResultSet result = null;
        try {
            StringBuilder sql =
                    new StringBuilder("SELECT ")
                            .append(GeoPackageCatalogReader.quote(table.primaryKey()))
                            .append(',')
                            .append(GeoPackageCatalogReader.quote(table.geometryColumnName()));
            String quotedGeometry = GeoPackageCatalogReader.quote(table.geometryColumnName());
            sql.append(",typeof(")
                    .append(quotedGeometry)
                    .append("),length(")
                    .append(quotedGeometry)
                    .append(')');
            for (GeoPackageAttributeColumn attribute : this.projection) {
                String quoted = GeoPackageCatalogReader.quote(attribute.name());
                sql.append(',')
                        .append(quoted)
                        .append(",typeof(")
                        .append(quoted)
                        .append("),length(CAST(")
                        .append(quoted)
                        .append(" AS BLOB))");
            }
            sql.append(" FROM ")
                    .append(GeoPackageCatalogReader.quote(table.tableName()))
                    .append(" ORDER BY ")
                    .append(GeoPackageCatalogReader.quote(table.primaryKey()));
            prepared = session.connection().prepareStatement(sql.toString());
            prepared.setFetchSize(256);
            result = prepared.executeQuery();
            statement = prepared;
            rows = result;
        } catch (SQLException exception) {
            io.github.mundanej.map.api.SourceException primary = queryFailure(exception);
            closeJdbc(result, prepared, primary);
            session.suppressOperationCleanup(primary, cancellation, "cursor");
            throw primary;
        }
    }

    @Override
    public boolean advance() {
        if (closed) {
            throw new IllegalStateException("GeoPackage cursor is closed");
        }
        current = null;
        try {
            while (rows.next()) {
                physicalRow++;
                session.verifyBeforePublication(cancellation, "cursor");
                GeoPackageFailures.checkpoint(
                        sourceId, cancellation::isCancellationRequested, "feature-query");
                accounting.recordExamined();
                long id = rows.getLong(1);
                if (rows.wasNull()) {
                    throw record("id", "null");
                }
                String geometryStorage = rows.getString(3);
                long geometryBytes = rows.getLong(4);
                boolean geometryLengthNull = rows.wasNull();
                if ("null".equals(geometryStorage) || geometryLengthNull) {
                    throw record("geometry", "null");
                }
                if (!"blob".equals(geometryStorage)) {
                    throw record("geometry", "storageClass");
                }
                if (geometryBytes <= 0) {
                    throw record("geometry", "range");
                }
                if (geometryBytes > session.limits().maximumBlobBytes()) {
                    throw limit("blobBytes", geometryBytes, session.limits().maximumBlobBytes());
                }
                byte[] bytes = rows.getBytes(2);
                if (bytes == null || bytes.length != geometryBytes) {
                    throw record("geometry", "value");
                }
                GeoPackageGeometryDecoder.DecodedGeometry decoded;
                try {
                    decoded =
                            GeoPackageGeometryDecoder.decode(
                                    sourceId,
                                    bytes,
                                    table.srsId(),
                                    table.geometryType(),
                                    session.limits(),
                                    cancellation,
                                    query.sourceBounds());
                } catch (io.github.mundanej.map.api.SourceException failure) {
                    throw GeoPackageFailures.atRecord(failure, physicalRow);
                }
                if (decoded.filtered()) {
                    continue;
                }
                if (decoded.isEmpty()) {
                    recordEmptyWarning(decoded.emptyType());
                    continue;
                }
                Geometry geometry = decoded.geometry();
                if (query.sourceBounds().isPresent()
                        && !intersects(query.sourceBounds().orElseThrow(), geometry.envelope())) {
                    continue;
                }
                Map<String, Object> attributes = decodeAttributes();
                FeatureRecord record =
                        new FeatureRecord(Long.toString(id), "", geometry, attributes);
                accounting.recordReturned(record, 0, cancellation);
                session.verifyBeforePublication(cancellation, "publish");
                current = record;
                return true;
            }
            close();
            return false;
        } catch (SQLException exception) {
            io.github.mundanej.map.api.SourceException primary = queryFailure(exception);
            closeAfterFailure(primary);
            throw primary;
        } catch (RuntimeException failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    @Override
    public FeatureRecord current() {
        if (closed || current == null) {
            throw new IllegalStateException("GeoPackage cursor has no current record");
        }
        return current;
    }

    @Override
    public DiagnosticReport diagnostics() {
        return new DiagnosticReport(warnings, omittedWarnings);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closeInternal(true);
    }

    void closeFromSource() {
        closeInternal(false);
    }

    private void closeInternal(boolean verify) {
        if (closed) {
            return;
        }
        closed = true;
        Throwable failure = null;
        try {
            rows.close();
        } catch (SQLException exception) {
            failure = exception;
        }
        try {
            statement.close();
        } catch (SQLException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        try {
            owner.release(this);
        } catch (RuntimeException exception) {
            failure = suppress(failure, exception);
        }
        if (verify) {
            try {
                session.afterOperation(cancellation, "cursor");
            } catch (RuntimeException exception) {
                failure = suppress(failure, exception);
            }
        }
        if (failure != null) {
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw queryFailure((SQLException) failure);
        }
    }

    private io.github.mundanej.map.api.SourceException queryFailure(SQLException cause) {
        return session.queryFailure(cause, "feature");
    }

    private io.github.mundanej.map.api.SourceException record(String field, String reason) {
        return GeoPackageFailures.atRecord(
                GeoPackageFailures.failure(
                        sourceId,
                        "GEOPACKAGE_RECORD_INVALID",
                        "GeoPackage feature record is invalid",
                        Map.of("field", field, "reason", reason)),
                physicalRow);
    }

    private Map<String, Object> decodeAttributes() throws SQLException {
        if (projection.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        long rowText = 0;
        long rowOwned = 0;
        for (int index = 0; index < projection.size(); index++) {
            GeoPackageFailures.checkpoint(
                    sourceId, cancellation::isCancellationRequested, "feature-query");
            GeoPackageAttributeColumn column = projection.get(index);
            int valueIndex = 5 + index * 3;
            GeoPackageAttributeDecoder.DecodedAttribute decoded;
            try {
                decoded =
                        GeoPackageAttributeDecoder.decode(
                                sourceId,
                                rows,
                                valueIndex,
                                valueIndex + 1,
                                valueIndex + 2,
                                column,
                                session.limits());
            } catch (io.github.mundanej.map.api.SourceException failure) {
                throw GeoPackageFailures.atRecord(failure, physicalRow);
            }
            rowText = Math.addExact(rowText, decoded.textCharacters());
            rowOwned = Math.addExact(rowOwned, decoded.ownedBytes());
            attributes.put(column.name(), decoded.value());
        }
        long nextText = Math.addExact(decodedTextCharacters, rowText);
        long nextOwned = Math.addExact(decodedOwnedBytes, rowOwned);
        if (nextText > session.limits().maximumTextCharacters()) {
            throw limit("textCharacters", nextText, session.limits().maximumTextCharacters());
        }
        if (nextOwned > session.limits().maximumOwnedBytes()) {
            throw limit("ownedBytes", nextOwned, session.limits().maximumOwnedBytes());
        }
        decodedTextCharacters = nextText;
        decodedOwnedBytes = nextOwned;
        return java.util.Collections.unmodifiableMap(attributes);
    }

    private io.github.mundanej.map.api.SourceException limit(
            String name, long requested, long maximum) {
        return GeoPackageFailures.atRecord(
                GeoPackageFailures.failure(
                        sourceId,
                        "SOURCE_LIMIT_EXCEEDED",
                        "GeoPackage operation limit exceeded",
                        Map.of(
                                "scope",
                                "geopackageCursor",
                                "limit",
                                name,
                                "requested",
                                Long.toString(requested),
                                "maximum",
                                Long.toString(maximum))),
                physicalRow);
    }

    private static boolean intersects(
            io.github.mundanej.map.api.Envelope first, io.github.mundanej.map.api.Envelope second) {
        return first.maxX() >= second.minX()
                && first.minX() <= second.maxX()
                && first.maxY() >= second.minY()
                && first.minY() <= second.maxY();
    }

    private void closeAfterFailure(Throwable primary) {
        if (closed) {
            return;
        }
        closed = true;
        closeJdbc(rows, statement, primary);
        try {
            owner.release(this);
        } catch (RuntimeException cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
        session.suppressOperationCleanup(primary, cancellation, "cursor");
    }

    private static void closeJdbc(ResultSet result, PreparedStatement prepared, Throwable primary) {
        if (result != null) {
            try {
                result.close();
            } catch (SQLException cleanupFailure) {
                primary.addSuppressed(cleanupFailure);
            }
        }
        if (prepared != null) {
            try {
                prepared.close();
            } catch (SQLException cleanupFailure) {
                primary.addSuppressed(cleanupFailure);
            }
        }
    }

    private static Throwable suppress(Throwable primary, Throwable next) {
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }

    private void recordEmptyWarning(String geometryType) {
        if (warnings.size() >= maximumWarnings) {
            omittedWarnings++;
            return;
        }
        warnings.add(
                new SourceDiagnostic(
                        "GEOPACKAGE_GEOMETRY_EMPTY",
                        DiagnosticSeverity.WARNING,
                        sourceId,
                        Optional.of(GeoPackageFailures.recordLocation(physicalRow)),
                        "GeoPackage empty geometry was skipped",
                        Map.of("geometryType", geometryType)));
    }
}
