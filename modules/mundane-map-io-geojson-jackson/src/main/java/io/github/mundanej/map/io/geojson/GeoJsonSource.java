package io.github.mundanej.map.io.geojson;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeatureQueryAccounting;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

final class GeoJsonSource implements FeatureSource {
    private final SourceIdentity identity;
    private final GeoJsonLimits formatLimits;
    private final FeatureSourceLimits sourceLimits;
    private final FeatureSourceMetadata metadata;
    private final DiagnosticReport openingDiagnostics;
    private byte[] bytes;
    private List<GeoJsonReader.Entry> entries;
    private Cursor liveCursor;
    private boolean closed;

    GeoJsonSource(
            byte[] bytes,
            SourceIdentity identity,
            GeoJsonOpenOptions options,
            GeoJsonReader.Opening opening) {
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.identity = Objects.requireNonNull(identity, "identity");
        formatLimits = options.formatLimits();
        sourceLimits = options.sourceLimits();
        entries = opening.entries();
        openingDiagnostics = opening.warnings();
        metadata =
                new FeatureSourceMetadata(
                        identity,
                        opening.extent(),
                        OptionalLong.of(opening.emittedFeatureCount()),
                        Optional.empty(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsRegistry.level1().resolve("EPSG:4326"),
                                        Optional.empty(),
                                        Optional.empty())));
    }

    @Override
    public FeatureSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public FeatureSourceLimits limits() {
        return sourceLimits;
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return openingDiagnostics;
    }

    @Override
    public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
        if (closed) {
            throw new IllegalStateException("Source is closed");
        }
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(cancellation, "cancellation");
        if (liveCursor != null) {
            throw new IllegalStateException("A cursor is already open");
        }
        FeatureQueryLimits effective = query.tighterLimits().orElse(sourceLimits.queryLimits());
        if (!effective.tightens(sourceLimits.queryLimits())) {
            throw new IllegalArgumentException("Query limits may only tighten source limits");
        }
        if (cancellation.isCancellationRequested()) {
            throw cancelled();
        }
        liveCursor = new Cursor(query, cancellation, effective);
        return liveCursor;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (liveCursor != null) {
            liveCursor.closeFromSource();
        }
        bytes = null;
        entries = null;
    }

    private SourceException cancelled() {
        return GeoJsonReader.failure(
                identity.id(),
                "SOURCE_CANCELLED",
                "Source query was cancelled",
                Map.of("operation", "feature-query"));
    }

    private final class Cursor implements FeatureCursor {
        private final FeatureQuery query;
        private final CancellationToken cancellation;
        private final FeatureQueryAccounting accounting;
        private int nextIndex;
        private FeatureRecord current;
        private State state = State.NEW;

        private Cursor(
                FeatureQuery query, CancellationToken cancellation, FeatureQueryLimits limits) {
            this.query = query;
            this.cancellation = cancellation;
            accounting = new FeatureQueryAccounting(identity.id(), limits);
        }

        @Override
        public boolean advance() {
            requireUsable();
            if (state == State.EXHAUSTED) {
                return false;
            }
            current = null;
            try {
                while (nextIndex < entries.size()) {
                    if (cancellation.isCancellationRequested()) {
                        throw cancelled();
                    }
                    GeoJsonReader.Entry entry = entries.get(nextIndex++);
                    accounting.recordExamined();
                    if (!entry.emitted()) {
                        continue;
                    }
                    if (query.sourceBounds().isPresent()
                            && !intersects(entry.envelope(), query.sourceBounds().orElseThrow())) {
                        continue;
                    }
                    FeatureRecord parsed =
                            GeoJsonReader.readEntry(
                                    bytes, entry, identity, formatLimits, cancellation);
                    FeatureRecord projected = project(parsed, query.attributes());
                    accounting.recordReturned(projected, 0, cancellation);
                    current = projected;
                    state = State.CURRENT;
                    return true;
                }
                state = State.EXHAUSTED;
                release();
                return false;
            } catch (RuntimeException | Error failure) {
                state =
                        failure instanceof SourceException sourceFailure
                                        && sourceFailure
                                                .terminal()
                                                .code()
                                                .equals("SOURCE_CANCELLED")
                                ? State.CANCELLED
                                : State.FAILED;
                release();
                throw failure;
            }
        }

        @Override
        public FeatureRecord current() {
            if (state != State.CURRENT) {
                throw new IllegalStateException("Cursor has no current record");
            }
            return current;
        }

        @Override
        public DiagnosticReport diagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public boolean isClosed() {
            return state == State.CLOSED;
        }

        @Override
        public void close() {
            if (state == State.CLOSED) {
                return;
            }
            state = State.CLOSED;
            current = null;
            release();
        }

        private void closeFromSource() {
            if (state != State.CLOSED) {
                state = State.CLOSED;
                current = null;
                release();
            }
        }

        private void requireUsable() {
            if (closed
                    || state == State.CLOSED
                    || state == State.FAILED
                    || state == State.CANCELLED) {
                throw new IllegalStateException("Cursor is not usable");
            }
        }

        private void release() {
            if (liveCursor == this) {
                liveCursor = null;
            }
        }

        private FeatureRecord project(FeatureRecord record, AttributeSelection selection) {
            if (selection.equals(AttributeSelection.ALL)) {
                return record;
            }
            if (selection.equals(AttributeSelection.NONE)) {
                return new FeatureRecord(record.id(), record.name(), record.geometry(), Map.of());
            }
            LinkedHashMap<String, Object> selected = new LinkedHashMap<>();
            for (String name : selection.orderedNames()) {
                if (cancellation.isCancellationRequested()) {
                    throw cancelled();
                }
                if (record.attributes().containsKey(name)) {
                    selected.put(name, record.attributes().get(name));
                }
            }
            return new FeatureRecord(
                    record.id(),
                    record.name(),
                    record.geometry(),
                    Collections.unmodifiableMap(selected));
        }
    }

    private static boolean intersects(Envelope first, Envelope second) {
        return first.maxX() >= second.minX()
                && first.minX() <= second.maxX()
                && first.maxY() >= second.minY()
                && first.minY() <= second.maxY();
    }

    private enum State {
        NEW,
        CURRENT,
        EXHAUSTED,
        FAILED,
        CANCELLED,
        CLOSED
    }
}
