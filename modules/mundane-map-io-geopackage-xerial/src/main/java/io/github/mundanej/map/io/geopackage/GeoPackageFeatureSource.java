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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GeoPackageFeatureSource implements FeatureSource {
    private final GeoPackageSession session;
    private final GeoPackageTableProfile profile;
    private final GeoPackageFeatureOptions options;
    private final FeatureSourceMetadata metadata;
    private GeoPackageFeatureCursor cursor;
    private boolean closed;

    GeoPackageFeatureSource(
            SourceIdentity identity,
            GeoPackageSession session,
            GeoPackageTableProfile profile,
            GeoPackageFeatureOptions options) {
        this.session = session;
        this.profile = profile;
        this.options = options;
        GeoPackageFeatureTable table = profile.table();
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
        GeoPackageFailures.checkpoint(
                metadata.identity().id(), cancellation::isCancellationRequested, "feature-query");
        List<GeoPackageAttributeColumn> projection = projection(query);
        FeatureQueryLimits limits =
                query.tighterLimits().orElse(options.featureSourceLimits().queryLimits());
        if (!limits.tightens(options.featureSourceLimits().queryLimits())) {
            throw new IllegalArgumentException("Query limits may only tighten source limits");
        }
        cursor =
                new GeoPackageFeatureCursor(
                        this,
                        session,
                        profile.table(),
                        projection,
                        query,
                        cancellation,
                        limits,
                        metadata.identity().id());
        return cursor;
    }

    private List<GeoPackageAttributeColumn> projection(FeatureQuery query) {
        if (query.attributes().equals(io.github.mundanej.map.api.AttributeSelection.NONE)) {
            return List.of();
        }
        if (!query.attributes().isOnly()) {
            return profile.attributes();
        }
        List<GeoPackageAttributeColumn> selected = new ArrayList<>();
        for (String name : query.attributes().orderedNames()) {
            GeoPackageAttributeColumn column =
                    profile.attributes().stream()
                            .filter(candidate -> candidate.name().equals(name))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            GeoPackageFailures.failure(
                                                    metadata.identity().id(),
                                                    "SOURCE_QUERY_ATTRIBUTE_UNKNOWN",
                                                    "Query requested an unknown attribute",
                                                    Map.of("field", name)));
            selected.add(column);
        }
        return List.copyOf(selected);
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
