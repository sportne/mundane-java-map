package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.Optional;

final class GeoPackageFeatureSource implements FeatureSource {
    private final GeoPackageSession session;
    private final GeoPackageFeatureTable table;
    private final GeoPackageFeatureOptions options;
    private final FeatureSourceMetadata metadata;
    private GeoPackageFeatureCursor cursor;
    private boolean closed;

    GeoPackageFeatureSource(
            SourceIdentity identity,
            GeoPackageSession session,
            GeoPackageFeatureTable table,
            GeoPackageFeatureOptions options) {
        this.session = session;
        this.table = table;
        this.options = options;
        metadata =
                new FeatureSourceMetadata(
                        identity,
                        table.bounds(),
                        table.featureCount(),
                        Optional.of(table.attributeSchema()),
                        Optional.of(table.crs()));
    }

    @Override
    public FeatureSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public FeatureSourceLimits limits() {
        return options.featureSourceLimits();
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return DiagnosticReport.empty();
    }

    @Override
    public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
        if (closed) {
            throw new IllegalStateException("GeoPackage source is closed");
        }
        java.util.Objects.requireNonNull(query, "query");
        java.util.Objects.requireNonNull(cancellation, "cancellation");
        if (cursor != null) {
            throw new IllegalStateException("A GeoPackage cursor is already open");
        }
        if (query.attributes().isOnly()) {
            throw new IllegalArgumentException("The point-only slice has no attributes");
        }
        FeatureQueryLimits limits =
                query.tighterLimits().orElse(options.featureSourceLimits().queryLimits());
        if (!limits.tightens(options.featureSourceLimits().queryLimits())) {
            throw new IllegalArgumentException("Query limits may only tighten source limits");
        }
        cursor =
                new GeoPackageFeatureCursor(
                        this,
                        session,
                        table,
                        query,
                        cancellation,
                        limits,
                        metadata.identity().id());
        return cursor;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    void release(GeoPackageFeatureCursor candidate) {
        if (cursor == candidate) {
            cursor = null;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        try {
            if (cursor != null) {
                cursor.closeFromSource();
            }
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            session.close();
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
