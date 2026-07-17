package io.github.mundanej.map.core;

import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Eager packed-primitive elevation source for synthetic data and validated format readers.
 *
 * <p>The source owns one row-major {@code double[]} and one fixed packed no-data mask. Callers must
 * externally serialize sampling and close.
 */
public final class PackedElevationGrid implements ElevationSource {
    private final ElevationSourceMetadata metadata;
    private final ElevationSourceLimits limits;
    private final DiagnosticReport openingDiagnostics;
    private double[] elevations;
    private long[] noDataWords;
    private boolean closed;

    private PackedElevationGrid(
            ElevationSourceMetadata metadata,
            ElevationSourceLimits limits,
            DiagnosticReport openingDiagnostics,
            double[] elevations,
            long[] noDataWords) {
        this.metadata = metadata;
        this.limits = limits;
        this.openingDiagnostics = openingDiagnostics;
        this.elevations = elevations;
        this.noDataWords = noDataWords;
    }

    /**
     * Copies a grid using the default eager-grid limits and no opening warnings.
     *
     * @param metadata immutable positioned-grid metadata
     * @param rowMajorElevations exact-length row-major sample payloads
     * @param noDataCells sample indexes whose numeric payloads must be ignored
     * @return caller-owned open elevation source
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if storage, sample, mask, or report values are invalid
     * @throws SourceException if a default resource ceiling is exceeded
     */
    public static PackedElevationGrid copyOf(
            ElevationSourceMetadata metadata, double[] rowMajorElevations, BitSet noDataCells) {
        Objects.requireNonNull(metadata, "metadata");
        return copyOf(
                metadata,
                rowMajorElevations,
                noDataCells,
                ElevationSourceLimits.DEFAULTS,
                DiagnosticReport.empty());
    }

    /**
     * Copies a fully described eager grid after resource preflight.
     *
     * <p>Masked payloads and signed zero are stored as positive zero. Every unmasked sample must be
     * finite. The source and both mutable inputs are independent after this method returns.
     *
     * @param metadata immutable positioned-grid metadata
     * @param rowMajorElevations exact-length row-major sample payloads
     * @param noDataCells sample indexes whose numeric payloads must be ignored
     * @param limits effective resource and warning ceilings
     * @param openingDiagnostics successful warning-only report for the metadata source identity
     * @return caller-owned open elevation source
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if storage, sample, mask, or report values are invalid
     * @throws SourceException if an effective resource ceiling is exceeded
     */
    public static PackedElevationGrid copyOf(
            ElevationSourceMetadata metadata,
            double[] rowMajorElevations,
            BitSet noDataCells,
            ElevationSourceLimits limits,
            DiagnosticReport openingDiagnostics) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(rowMajorElevations, "rowMajorElevations");
        Objects.requireNonNull(noDataCells, "noDataCells");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(openingDiagnostics, "openingDiagnostics");

        long sampleCount = preflight(metadata, limits);
        if (rowMajorElevations.length != sampleCount) {
            throw new IllegalArgumentException(
                    "rowMajorElevations length must equal metadata sampleCount");
        }
        if (noDataCells.length() > sampleCount) {
            throw new IllegalArgumentException("noDataCells contains an out-of-range sample");
        }
        validateReport(metadata, limits, openingDiagnostics);

        double[] ownedElevations = new double[rowMajorElevations.length];
        int maskWordCount = Math.toIntExact(maskWordCount(sampleCount));
        long[] ownedMask = new long[maskWordCount];
        for (int index = noDataCells.nextSetBit(0);
                index >= 0;
                index = noDataCells.nextSetBit(index + 1)) {
            ownedMask[index >>> 6] |= 1L << (index & 63);
        }
        for (int index = 0; index < ownedElevations.length; index++) {
            if (isMasked(ownedMask, index)) {
                ownedElevations[index] = 0.0;
                continue;
            }
            double value = rowMajorElevations[index];
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Unmasked elevation samples must be finite at index " + index);
            }
            ownedElevations[index] = value == 0.0 ? 0.0 : value;
        }
        return new PackedElevationGrid(
                metadata, limits, openingDiagnostics, ownedElevations, ownedMask);
    }

    @Override
    public ElevationSourceMetadata metadata() {
        return metadata;
    }

    @Override
    public ElevationSourceLimits limits() {
        return limits;
    }

    @Override
    public DiagnosticReport openingDiagnostics() {
        return openingDiagnostics;
    }

    @Override
    public OptionalDouble sample(int column, int row) {
        if (closed) {
            throw new IllegalStateException("Elevation source is closed");
        }
        requireIndex(column, metadata.columnCount(), "column");
        requireIndex(row, metadata.rowCount(), "row");
        int index = Math.toIntExact((long) row * metadata.columnCount() + column);
        if (isMasked(noDataWords, index)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(elevations[index]);
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
        elevations = null;
        noDataWords = null;
    }

    static long retainedSampleBytes(long sampleCount) {
        try {
            long elevationBytes = Math.multiplyExact(sampleCount, Double.BYTES);
            long maskBytes = Math.multiplyExact(maskWordCount(sampleCount), Long.BYTES);
            return Math.addExact(elevationBytes, maskBytes);
        } catch (ArithmeticException failure) {
            return Long.MAX_VALUE;
        }
    }

    private static long preflight(ElevationSourceMetadata metadata, ElevationSourceLimits limits) {
        requireLimit(metadata, "columns", metadata.columnCount(), limits.maximumColumns());
        requireLimit(metadata, "rows", metadata.rowCount(), limits.maximumRows());
        long sampleCount;
        try {
            sampleCount = metadata.sampleCount();
        } catch (ArithmeticException failure) {
            sampleCount = Long.MAX_VALUE;
        }
        long effectiveMaximumSamples = Math.min(limits.maximumSamples(), (long) Integer.MAX_VALUE);
        requireLimit(metadata, "samples", sampleCount, effectiveMaximumSamples);
        requireLimit(
                metadata,
                "retainedSampleBytes",
                retainedSampleBytes(sampleCount),
                limits.maximumRetainedSampleBytes());
        return sampleCount;
    }

    private static void validateReport(
            ElevationSourceMetadata metadata,
            ElevationSourceLimits limits,
            DiagnosticReport report) {
        if (report.entries().size() > limits.maximumRetainedWarnings()) {
            throw new IllegalArgumentException(
                    "openingDiagnostics exceeds maximumRetainedWarnings");
        }
        for (SourceDiagnostic diagnostic : report.entries()) {
            if (!diagnostic.sourceId().equals(metadata.identity().id())) {
                throw new IllegalArgumentException(
                        "openingDiagnostics must use the metadata source ID");
            }
            if (diagnostic.severity() != DiagnosticSeverity.WARNING) {
                throw new IllegalArgumentException("openingDiagnostics must contain warnings only");
            }
        }
    }

    private static void requireLimit(
            ElevationSourceMetadata metadata, String limit, long requested, long maximum) {
        if (requested <= maximum) {
            return;
        }
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        "SOURCE_LIMIT_EXCEEDED",
                        DiagnosticSeverity.ERROR,
                        metadata.identity().id(),
                        Optional.empty(),
                        "Elevation source limit exceeded",
                        Map.of(
                                "scope",
                                "elevationOpen",
                                "limit",
                                limit,
                                "requested",
                                Long.toString(requested),
                                "maximum",
                                Long.toString(maximum)));
        throw new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private static long maskWordCount(long sampleCount) {
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be positive");
        }
        return 1L + (sampleCount - 1L) / Long.SIZE;
    }

    private static boolean isMasked(long[] words, int index) {
        return (words[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    private static void requireIndex(int index, int count, String name) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException(name + " is outside the elevation grid");
        }
    }
}
