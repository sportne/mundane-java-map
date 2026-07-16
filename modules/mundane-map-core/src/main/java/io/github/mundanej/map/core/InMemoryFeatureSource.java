package io.github.mundanej.map.core;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Immutable explicitly linear or packed-indexed feature source for application use.
 *
 * <p>One source permits one live cursor. Callers must externally serialize cursor creation and
 * traversal; concurrent cursors from the same source are unsupported. Immutable indexed storage may
 * still be queried concurrently through separate source instances.
 */
public final class InMemoryFeatureSource implements FeatureSource {
    private final List<FeatureRecord> records;
    private final FeatureSourceMetadata metadata;
    private final FeatureSourceLimits limits;
    private final PackedFeatureSpatialIndex spatialIndex;
    private Cursor liveCursor;
    private boolean closed;

    private InMemoryFeatureSource(
            SourceIdentity identity,
            List<FeatureRecord> records,
            Optional<AttributeSchema> schema,
            Optional<CrsMetadata> crs,
            FeatureSourceLimits limits,
            FeatureIndexLimits indexLimits) {
        this.records = List.copyOf(records);
        this.limits = Objects.requireNonNull(limits, "limits");
        validateRecords(identity.id(), this.records, schema);
        Envelope extent = null;
        for (FeatureRecord record : this.records) {
            extent =
                    extent == null
                            ? record.geometry().envelope()
                            : extent.union(record.geometry().envelope());
        }
        this.metadata =
                new FeatureSourceMetadata(
                        identity,
                        Optional.ofNullable(extent),
                        OptionalLong.of(this.records.size()),
                        schema,
                        crs);
        spatialIndex =
                indexLimits == null
                        ? null
                        : PackedFeatureSpatialIndex.build(identity.id(), this.records, indexLimits);
    }

    /**
     * Opens a source with Level 1 limits and absent schema/CRS.
     *
     * @param identity immutable source identity
     * @param records ordered records, defensively copied with their immutable values
     * @return open linear in-memory source owned by the caller
     */
    public static InMemoryFeatureSource open(SourceIdentity identity, List<FeatureRecord> records) {
        return open(
                identity, records, Optional.empty(), Optional.empty(), FeatureSourceLimits.LEVEL_1);
    }

    /**
     * Opens a fully described immutable source.
     *
     * @param identity immutable source identity
     * @param records ordered records, defensively copied with their immutable values
     * @param schema optional attribute schema enforced while opening
     * @param crs optional source CRS metadata
     * @param limits query ceilings retained by the source
     * @return open linear in-memory source owned by the caller
     */
    public static InMemoryFeatureSource open(
            SourceIdentity identity,
            List<FeatureRecord> records,
            Optional<AttributeSchema> schema,
            Optional<CrsMetadata> crs,
            FeatureSourceLimits limits) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(crs, "crs");
        return new InMemoryFeatureSource(identity, records, schema, crs, limits, null);
    }

    /**
     * Opens an explicitly packed-indexed source with Level 1 source and index limits.
     *
     * <p>The source retains the same one-live-cursor lifecycle as {@link #open(SourceIdentity,
     * List)}. Index selection is explicit and never falls back to a linear source.
     *
     * @param identity immutable source identity
     * @param records ordered immutable record snapshot input
     * @return the explicitly indexed source
     * @throws SourceException when a structured index construction limit is exceeded
     */
    public static InMemoryFeatureSource openIndexed(
            SourceIdentity identity, List<FeatureRecord> records) {
        return openIndexed(
                identity,
                records,
                Optional.empty(),
                Optional.empty(),
                FeatureSourceLimits.LEVEL_1,
                FeatureIndexLimits.LEVEL_1);
    }

    /**
     * Opens a fully described explicitly packed-indexed source.
     *
     * <p>Index capacity failure is terminal and structured; format adapters are not involved and no
     * linear fallback is selected.
     *
     * @param identity immutable source identity
     * @param records ordered immutable record snapshot input
     * @param schema optional attribute schema enforced while opening
     * @param crs optional source CRS metadata
     * @param sourceLimits query ceilings retained by the source
     * @param indexLimits construction and query-plan ceilings for the packed index
     * @return the explicitly indexed source
     * @throws SourceException when a structured index construction limit is exceeded
     */
    public static InMemoryFeatureSource openIndexed(
            SourceIdentity identity,
            List<FeatureRecord> records,
            Optional<AttributeSchema> schema,
            Optional<CrsMetadata> crs,
            FeatureSourceLimits sourceLimits,
            FeatureIndexLimits indexLimits) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(crs, "crs");
        Objects.requireNonNull(sourceLimits, "sourceLimits");
        Objects.requireNonNull(indexLimits, "indexLimits");
        if (records.size() > indexLimits.maximumRecords()) {
            throw failure(
                    identity.id(),
                    "SOURCE_LIMIT_EXCEEDED",
                    "Source index limit exceeded",
                    Map.of(
                            "scope",
                            "spatialIndexBuild",
                            "limit",
                            "records",
                            "requested",
                            Integer.toString(records.size()),
                            "maximum",
                            Integer.toString(indexLimits.maximumRecords())));
        }
        PackedFeatureSpatialIndex.requireAddressable(identity.id(), records.size());
        return new InMemoryFeatureSource(identity, records, schema, crs, sourceLimits, indexLimits);
    }

    @Override
    public FeatureSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public FeatureSourceLimits limits() {
        return limits;
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return DiagnosticReport.empty();
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
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(cancellation, "cancellation");
        if (liveCursor != null) {
            throw new IllegalStateException("A cursor is already open");
        }
        checkCancellation(cancellation);
        FeatureQueryLimits effective = query.tighterLimits().orElse(limits.queryLimits());
        if (!effective.tightens(limits.queryLimits())) {
            throw new IllegalArgumentException("Query limits may only tighten source limits");
        }
        checkSelectedFields(query.attributes(), cancellation);
        Cursor cursor = new Cursor(query, cancellation, effective);
        liveCursor = cursor;
        return cursor;
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
    }

    PackedFeatureSpatialIndex spatialIndex() {
        return spatialIndex;
    }

    private void checkSelectedFields(AttributeSelection selection, CancellationToken cancellation) {
        if (!selection.isOnly() || metadata.schema().isEmpty()) {
            return;
        }
        AttributeSchema schema = metadata.schema().orElseThrow();
        int checked = 0;
        for (String field : selection.orderedNames()) {
            if ((checked++ & 4095) == 0) {
                checkCancellation(cancellation);
            }
            if (schema.field(field).isEmpty()) {
                throw failure(
                        "SOURCE_QUERY_ATTRIBUTE_UNKNOWN",
                        "Query requested an unknown attribute",
                        Map.of("field", field));
            }
        }
    }

    private void checkCancellation(CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw failure(
                    "SOURCE_CANCELLED",
                    "Source query was cancelled",
                    Map.of("operation", "feature-query"));
        }
    }

    private static void validateRecords(
            String sourceId, List<FeatureRecord> records, Optional<AttributeSchema> schema) {
        Map<String, Integer> firstIndexes = new LinkedHashMap<>();
        for (int index = 0; index < records.size(); index++) {
            FeatureRecord record = Objects.requireNonNull(records.get(index), "record");
            Integer first = firstIndexes.putIfAbsent(record.id(), index);
            if (first != null) {
                throw failure(
                        sourceId,
                        "SOURCE_DUPLICATE_FEATURE_ID",
                        "Source contains a duplicate feature ID",
                        Map.of(
                                "firstIndex",
                                first.toString(),
                                "duplicateIndex",
                                Integer.toString(index)));
            }
            schema.ifPresent(value -> validateRecordSchema(record, value));
        }
    }

    private static void validateRecordSchema(FeatureRecord record, AttributeSchema schema) {
        Map<String, Object> attributes = record.attributes();
        for (String name : attributes.keySet()) {
            if (schema.field(name).isEmpty()) {
                throw new IllegalArgumentException("Record contains undeclared attribute: " + name);
            }
        }
        for (AttributeField field : schema.fields()) {
            Object value = attributes.get(field.name());
            if (value == null || !field.accepts(value)) {
                throw new IllegalArgumentException(
                        "Record attribute does not conform to schema: " + field.name());
            }
        }
    }

    private SourceException failure(String code, String message, Map<String, String> context) {
        return failure(metadata.identity().id(), code, message, context);
    }

    private static SourceException failure(
            String sourceId, String code, String message, Map<String, String> context) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private static boolean intersects(Envelope first, Envelope second) {
        return first.maxX() >= second.minX()
                && first.minX() <= second.maxX()
                && first.maxY() >= second.minY()
                && first.minY() <= second.maxY();
    }

    private final class Cursor implements FeatureCursor {
        private final FeatureQuery query;
        private final CancellationToken cancellation;
        private final FeatureQueryAccounting accounting;
        private State state = State.NEW;
        private int nextIndex;
        private FeatureRecord current;
        private PackedFeatureSpatialIndex.CandidatePlan candidatePlan;

        private Cursor(
                FeatureQuery query, CancellationToken cancellation, FeatureQueryLimits limits) {
            this.query = query;
            this.cancellation = cancellation;
            accounting = new FeatureQueryAccounting(metadata.identity().id(), limits);
        }

        @Override
        public boolean advance() {
            requireUsable();
            if (state == State.EXHAUSTED) {
                return false;
            }
            current = null;
            try {
                if (spatialIndex != null
                        && query.sourceBounds().isPresent()
                        && candidatePlan == null) {
                    candidatePlan =
                            spatialIndex.plan(query.sourceBounds().orElseThrow(), cancellation);
                }
                while (nextIndex < records.size()) {
                    if (cancellation.isCancellationRequested()) {
                        throw failure(
                                "SOURCE_CANCELLED",
                                "Source query was cancelled",
                                Map.of("operation", "feature-query"));
                    }
                    int candidateIndex;
                    if (candidatePlan == null) {
                        candidateIndex = nextIndex++;
                    } else {
                        candidateIndex = candidatePlan.nextCandidate(nextIndex, cancellation);
                        if (candidateIndex < 0) {
                            nextIndex = records.size();
                            break;
                        }
                        nextIndex = candidateIndex + 1;
                        if (cancellation.isCancellationRequested()) {
                            throw failure(
                                    "SOURCE_CANCELLED",
                                    "Source query was cancelled",
                                    Map.of("operation", "feature-query"));
                        }
                    }
                    FeatureRecord candidate = records.get(candidateIndex);
                    accounting.recordExamined();
                    if (query.sourceBounds().isPresent()
                            && !intersects(
                                    candidate.geometry().envelope(),
                                    query.sourceBounds().orElseThrow())) {
                        continue;
                    }
                    FeatureRecord projected = project(candidate, query.attributes(), cancellation);
                    accounting.recordReturned(projected, 0, cancellation);
                    current = projected;
                    state = State.CURRENT;
                    return true;
                }
                state = State.EXHAUSTED;
                releaseAfterOperation();
                return false;
            } catch (RuntimeException | Error failure) {
                state =
                        failure instanceof SourceException exception
                                        && exception.terminal().code().equals("SOURCE_CANCELLED")
                                ? State.CANCELLED
                                : State.FAILED;
                releaseAfterOperation();
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
            releaseBeforePlanCleanup();
        }

        private void closeFromSource() {
            if (state != State.CLOSED) {
                state = State.CLOSED;
                current = null;
                releaseBeforePlanCleanup();
            }
        }

        private void requireUsable() {
            if (state == State.CLOSED
                    || state == State.FAILED
                    || state == State.CANCELLED
                    || closed) {
                throw new IllegalStateException("Cursor is not usable");
            }
        }

        private void releaseAfterOperation() {
            candidatePlan = null;
            releaseSlot();
        }

        private void releaseBeforePlanCleanup() {
            releaseSlot();
            candidatePlan = null;
        }

        private void releaseSlot() {
            if (liveCursor == this) {
                liveCursor = null;
            }
        }
    }

    private FeatureRecord project(
            FeatureRecord record, AttributeSelection selection, CancellationToken cancellation) {
        if (selection.equals(AttributeSelection.ALL)) {
            return record;
        }
        if (selection.equals(AttributeSelection.NONE)) {
            return new FeatureRecord(record.id(), record.name(), record.geometry(), Map.of());
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        Map<String, Object> sourceAttributes = record.attributes();
        int checked = 0;
        for (String name : selection.orderedNames()) {
            if ((checked++ & 4095) == 0) {
                checkCancellation(cancellation);
            }
            if (sourceAttributes.containsKey(name)) {
                values.put(name, sourceAttributes.get(name));
            }
        }
        checkCancellation(cancellation);
        return new FeatureRecord(
                record.id(), record.name(), record.geometry(), Collections.unmodifiableMap(values));
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
