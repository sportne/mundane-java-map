package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Borrowed source view that admits only singular-point records for one symbol layer. */
final class SingularPointFeatureSource implements FeatureSource {
    private final FeatureSource delegate;
    private final FeatureSourceMetadata metadata;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<FilteringCursor> cursor = new AtomicReference<>();

    SingularPointFeatureSource(FeatureSource delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        FeatureSourceMetadata source = delegate.metadata();
        SourceIdentity identity =
                new SourceIdentity(
                        "maplibre-symbol-" + digest(source.identity().id()),
                        displayName(source.identity().displayName()));
        metadata =
                new FeatureSourceMetadata(
                        identity,
                        source.extent(),
                        OptionalLong.empty(),
                        source.schema(),
                        source.crs());
    }

    @Override
    public FeatureSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public FeatureSourceLimits limits() {
        return delegate.limits();
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return diagnostics(delegate.openingDiagnostics());
    }

    @Override
    public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
        requireOpen();
        FeatureCursor opened = delegate.openCursor(query, cancellation);
        FilteringCursor filtering = new FilteringCursor(opened);
        if (!cursor.compareAndSet(null, filtering)) {
            opened.close();
            throw new IllegalStateException("feature source already has a live cursor");
        }
        if (isClosed()) {
            filtering.close();
            throw new IllegalStateException("feature source is closed");
        }
        return filtering;
    }

    @Override
    public boolean isClosed() {
        return closed.get() || delegate.isClosed();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            FilteringCursor active = cursor.get();
            if (active != null) {
                active.close();
            }
        }
    }

    private void requireOpen() {
        if (isClosed()) {
            throw new IllegalStateException("feature source is closed");
        }
    }

    private final class FilteringCursor implements FeatureCursor {
        private final FeatureCursor delegateCursor;
        private final AtomicBoolean cursorClosed = new AtomicBoolean();

        private FilteringCursor(FeatureCursor delegateCursor) {
            this.delegateCursor = delegateCursor;
        }

        @Override
        public boolean advance() {
            requireCursorOpen();
            while (delegateCursor.advance()) {
                if (delegateCursor.current().geometry() instanceof PointGeometry) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public FeatureRecord current() {
            requireCursorOpen();
            FeatureRecord current = delegateCursor.current();
            if (!(current.geometry() instanceof PointGeometry)) {
                throw new IllegalStateException("cursor has no current singular point");
            }
            return current;
        }

        @Override
        public DiagnosticReport diagnostics() {
            return SingularPointFeatureSource.this.diagnostics(delegateCursor.diagnostics());
        }

        @Override
        public boolean isClosed() {
            return cursorClosed.get() || delegateCursor.isClosed();
        }

        @Override
        public void close() {
            if (cursorClosed.compareAndSet(false, true)) {
                delegateCursor.close();
                cursor.compareAndSet(this, null);
            }
        }

        private void requireCursorOpen() {
            if (isClosed()) {
                throw new IllegalStateException("cursor is closed");
            }
        }
    }

    private DiagnosticReport diagnostics(DiagnosticReport report) {
        return new DiagnosticReport(
                report.entries().stream()
                        .map(
                                entry ->
                                        new SourceDiagnostic(
                                                entry.code(),
                                                entry.severity(),
                                                metadata.identity().id(),
                                                entry.location(),
                                                entry.message(),
                                                entry.context()))
                        .toList(),
                report.omittedWarningCount());
    }

    private static String displayName(String source) {
        String suffix = " (MapLibre singular points)";
        int retained = Math.min(source.length(), 256 - suffix.length());
        return source.substring(0, retained) + suffix;
    }

    private static String digest(String value) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
