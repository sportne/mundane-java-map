package io.github.mundanej.map.io.gpx;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import java.util.Objects;

final class GpxSource implements FeatureSource {
    private final FeatureSource delegate;
    private final DiagnosticReport openingDiagnostics;

    GpxSource(FeatureSource delegate, DiagnosticReport openingDiagnostics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.openingDiagnostics = Objects.requireNonNull(openingDiagnostics, "openingDiagnostics");
    }

    @Override
    public FeatureSourceMetadata metadata() {
        return delegate.metadata();
    }

    @Override
    public FeatureSourceLimits limits() {
        return delegate.limits();
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return openingDiagnostics;
    }

    @Override
    public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
        return delegate.openCursor(query, cancellation);
    }

    @Override
    public boolean isClosed() {
        return delegate.isClosed();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
