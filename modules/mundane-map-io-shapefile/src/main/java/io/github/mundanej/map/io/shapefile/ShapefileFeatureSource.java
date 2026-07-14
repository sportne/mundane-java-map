package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

final class ShapefileFeatureSource implements FeatureSource {
    private final ShapefileFileAccess.Channel channel;
    private final long capturedSize;
    private final ShpHeader header;
    private final ShapefileOpenOptions options;
    private final FeatureSourceMetadata metadata;
    private final ShxIndex index;
    private final DbfTable dbfTable;
    private final DiagnosticReport openingDiagnostics;
    private ShapefileCursor cursor;
    private boolean closed;

    ShapefileFeatureSource(
            SourceIdentity identity,
            ShapefileFileAccess.Channel channel,
            long size,
            ShpHeader header,
            Optional<CrsMetadata> crs,
            ShapefileOpenOptions options,
            ShxIndex index,
            DbfTable dbfTable,
            DiagnosticReport openingDiagnostics) {
        this.channel = channel;
        capturedSize = size;
        this.header = header;
        this.options = options;
        this.index = index;
        this.dbfTable = dbfTable;
        this.openingDiagnostics = openingDiagnostics;
        metadata =
                new FeatureSourceMetadata(
                        identity,
                        header.extent(),
                        OptionalLong.empty(),
                        Optional.of(
                                dbfTable == null
                                        ? new AttributeSchema(List.of())
                                        : dbfTable.schema()),
                        crs);
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
        return openingDiagnostics;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
        if (closed) {
            throw new IllegalStateException("Source is closed");
        }
        java.util.Objects.requireNonNull(query, "query");
        java.util.Objects.requireNonNull(cancellation, "cancellation");
        if (cursor != null) {
            throw new IllegalStateException("A cursor is already open");
        }
        Shapefiles.checkpoint(metadata.identity().id(), cancellation);
        FeatureQueryLimits effective =
                query.tighterLimits().orElse(options.featureSourceLimits().queryLimits());
        if (!effective.tightens(options.featureSourceLimits().queryLimits())) {
            throw new IllegalArgumentException("Query limits may only tighten source limits");
        }
        if (dbfTable != null) {
            dbfTable.validateSelection(query.attributes());
        } else if (query.attributes().isOnly()) {
            String field = query.attributes().orderedNames().get(0);
            throw ShapefileFailures.failure(
                    metadata.identity().id(),
                    "SOURCE_QUERY_ATTRIBUTE_UNKNOWN",
                    "dbf",
                    OptionalLong.empty(),
                    -1,
                    "Query requested an unknown attribute",
                    Map.of("field", field));
        }
        try {
            Shapefiles.checkpoint(metadata.identity().id(), cancellation);
            long actual = channel.size();
            Shapefiles.checkpoint(metadata.identity().id(), cancellation);
            if (actual != capturedSize) {
                throw lengthMismatch(actual);
            }
        } catch (IOException e) {
            throw ShapefileFailures.io(metadata.identity().id(), "shp", "size", -1, e);
        }
        if (dbfTable != null) {
            dbfTable.checkSize(cancellation);
        }
        cursor =
                new ShapefileCursor(
                        this,
                        channel,
                        capturedSize,
                        header,
                        query,
                        cancellation,
                        effective,
                        options.shapefileLimits(),
                        index,
                        dbfTable);
        return cursor;
    }

    void release(ShapefileCursor candidate) {
        if (cursor == candidate) {
            cursor = null;
        }
    }

    SourceException lengthMismatch(long actual) {
        return ShapefileFailures.failure(
                metadata.identity().id(),
                "SHAPEFILE_FILE_LENGTH_MISMATCH",
                "shp",
                OptionalLong.empty(),
                24,
                "Shapefile size changed",
                Map.of(
                        "declaredBytes",
                        Long.toString(capturedSize),
                        "actualBytes",
                        Long.toString(actual)));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (cursor != null) {
            cursor.closeFromSource();
        }
        SourceException first = null;
        if (dbfTable != null) {
            try {
                dbfTable.close();
            } catch (IOException exception) {
                first = closeFailure("dbf", exception);
            }
        }
        try {
            channel.close();
        } catch (IOException exception) {
            SourceException failure = closeFailure("shp", exception);
            if (first == null) {
                first = failure;
            } else {
                first.addSuppressed(failure);
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private SourceException closeFailure(String component, IOException cause) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "SOURCE_CLOSE_FAILED",
                        DiagnosticSeverity.ERROR,
                        metadata.identity().id(),
                        Optional.of(
                                new DiagnosticLocation(
                                        Optional.of(component),
                                        OptionalLong.empty(),
                                        OptionalInt.empty(),
                                        OptionalInt.empty(),
                                        Optional.empty(),
                                        OptionalLong.empty())),
                        "Shapefile source close failed",
                        Map.of());
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal, cause);
    }
}
