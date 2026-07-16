package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Package-private immutable packed STR-16 index for one in-memory source snapshot. */
final class PackedFeatureSpatialIndex {
    static final int FANOUT = 16;
    static final int MAXIMUM_PACKED_RECORDS = Integer.MAX_VALUE - 8;

    private final String sourceId;
    private final int[] recordOrdinals;
    private final int leafCount;
    private final int rootNode;
    private final int height;
    private final double[] nodeMinX;
    private final double[] nodeMinY;
    private final double[] nodeMaxX;
    private final double[] nodeMaxY;
    private final int[] nodeFirst;
    private final byte[] nodeCount;
    private final int[] childRefs;
    private final long retainedBytes;
    private final long queryBytes;
    private final int stackCapacity;

    private PackedFeatureSpatialIndex(
            String sourceId,
            int[] recordOrdinals,
            int leafCount,
            int rootNode,
            int height,
            double[] nodeMinX,
            double[] nodeMinY,
            double[] nodeMaxX,
            double[] nodeMaxY,
            int[] nodeFirst,
            byte[] nodeCount,
            int[] childRefs,
            Layout layout) {
        this.sourceId = sourceId;
        this.recordOrdinals = recordOrdinals;
        this.leafCount = leafCount;
        this.rootNode = rootNode;
        this.height = height;
        this.nodeMinX = nodeMinX;
        this.nodeMinY = nodeMinY;
        this.nodeMaxX = nodeMaxX;
        this.nodeMaxY = nodeMaxY;
        this.nodeFirst = nodeFirst;
        this.nodeCount = nodeCount;
        this.childRefs = childRefs;
        retainedBytes = layout.retainedBytes();
        queryBytes = layout.queryBytes();
        stackCapacity = layout.stackCapacity();
    }

    static PackedFeatureSpatialIndex build(
            String sourceId, List<FeatureRecord> records, FeatureIndexLimits limits) {
        requireWithin(sourceId, "records", records.size(), limits.maximumRecords());
        requireAddressable(sourceId, records.size());
        Layout layout = Layout.forRecords(records.size());
        requireWithin(
                sourceId, "retainedBytes", layout.retainedBytes(), limits.maximumRetainedBytes());
        requireWithin(sourceId, "buildBytes", layout.buildBytes(), limits.maximumBuildBytes());
        requireWithin(sourceId, "queryBytes", layout.queryBytes(), limits.maximumQueryBytes());
        if (records.isEmpty()) {
            return new PackedFeatureSpatialIndex(
                    sourceId,
                    new int[0],
                    0,
                    -1,
                    0,
                    new double[0],
                    new double[0],
                    new double[0],
                    new double[0],
                    new int[0],
                    new byte[0],
                    new int[0],
                    layout);
        }

        int size = records.size();
        int[] order = new int[size];
        int[] scratch = new int[size];
        for (int ordinal = 0; ordinal < size; ordinal++) {
            order[ordinal] = ordinal;
        }
        int[] recordOrdinals = new int[size];
        double[] nodeMinX = new double[layout.nodeCount()];
        double[] nodeMinY = new double[layout.nodeCount()];
        double[] nodeMaxX = new double[layout.nodeCount()];
        double[] nodeMaxY = new double[layout.nodeCount()];
        int[] nodeFirst = new int[layout.nodeCount()];
        byte[] nodeCount = new byte[layout.nodeCount()];
        int[] childRefs = new int[layout.edgeCount()];

        Builder builder =
                new Builder(
                        records,
                        order,
                        scratch,
                        recordOrdinals,
                        nodeMinX,
                        nodeMinY,
                        nodeMaxX,
                        nodeMaxY,
                        nodeFirst,
                        nodeCount,
                        childRefs);
        builder.build();
        return new PackedFeatureSpatialIndex(
                sourceId,
                recordOrdinals,
                layout.leafCount(),
                builder.rootNode,
                builder.height,
                nodeMinX,
                nodeMinY,
                nodeMaxX,
                nodeMaxY,
                nodeFirst,
                nodeCount,
                childRefs,
                layout);
    }

    CandidatePlan plan(Envelope query, CancellationToken cancellation) {
        checkCancellation(cancellation);
        long[] candidates = new long[(recordOrdinals.length + 63) >>> 6];
        if (rootNode < 0) {
            checkCancellation(cancellation);
            return new CandidatePlan(sourceId, candidates);
        }
        int[] stack = new int[stackCapacity];
        int stackSize = 0;
        stack[stackSize++] = rootNode;
        int units = 0;
        while (stackSize > 0) {
            if ((units++ & 4095) == 0) {
                checkCancellation(cancellation);
            }
            int node = stack[--stackSize];
            if (!intersects(node, query)) {
                continue;
            }
            int count = Byte.toUnsignedInt(nodeCount[node]);
            int first = nodeFirst[node];
            if (node < leafCount) {
                for (int index = 0; index < count; index++) {
                    if ((units++ & 4095) == 0) {
                        checkCancellation(cancellation);
                    }
                    int ordinal = recordOrdinals[first + index];
                    candidates[ordinal >>> 6] |= 1L << ordinal;
                }
            } else {
                for (int index = count - 1; index >= 0; index--) {
                    if ((units++ & 4095) == 0) {
                        checkCancellation(cancellation);
                    }
                    stack[stackSize++] = childRefs[first + index];
                }
            }
        }
        checkCancellation(cancellation);
        return new CandidatePlan(sourceId, candidates);
    }

    int recordCount() {
        return recordOrdinals.length;
    }

    int nodeCount() {
        return nodeCount.length;
    }

    int leafCount() {
        return leafCount;
    }

    int height() {
        return height;
    }

    int rootNode() {
        return rootNode;
    }

    int edgeCount() {
        return childRefs.length;
    }

    long retainedBytes() {
        return retainedBytes;
    }

    long queryBytes() {
        return queryBytes;
    }

    int[] recordOrdinalsCopy() {
        return recordOrdinals.clone();
    }

    static int compareEnvelopeItems(
            Envelope one, int oneKey, Envelope two, int twoKey, boolean xFirst) {
        int result;
        if (xFirst) {
            result = compare(centerX(one), centerX(two));
            if (result == 0) {
                result = compare(centerY(one), centerY(two));
            }
            if (result == 0) {
                result = compare(one.minX(), two.minX());
            }
            if (result == 0) {
                result = compare(one.minY(), two.minY());
            }
            if (result == 0) {
                result = compare(one.maxX(), two.maxX());
            }
            if (result == 0) {
                result = compare(one.maxY(), two.maxY());
            }
        } else {
            result = compare(centerY(one), centerY(two));
            if (result == 0) {
                result = compare(centerX(one), centerX(two));
            }
            if (result == 0) {
                result = compare(one.minY(), two.minY());
            }
            if (result == 0) {
                result = compare(one.minX(), two.minX());
            }
            if (result == 0) {
                result = compare(one.maxY(), two.maxY());
            }
            if (result == 0) {
                result = compare(one.maxX(), two.maxX());
            }
        }
        return result != 0 ? result : Integer.compare(oneKey, twoKey);
    }

    private static double centerX(Envelope envelope) {
        return envelope.minX() + envelope.width() / 2.0;
    }

    private static double centerY(Envelope envelope) {
        return envelope.minY() + envelope.height() / 2.0;
    }

    private static int compare(double first, double second) {
        return Double.compare(first, second);
    }

    static int boundedEnd(int start, int increment, int end) {
        if (start < 0 || increment <= 0 || end < start) {
            throw new IllegalArgumentException("Invalid bounded range");
        }
        return (int) Math.min((long) end, (long) start + increment);
    }

    private boolean intersects(int node, Envelope query) {
        return nodeMaxX[node] >= query.minX()
                && nodeMinX[node] <= query.maxX()
                && nodeMaxY[node] >= query.minY()
                && nodeMinY[node] <= query.maxY();
    }

    private void checkCancellation(CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw failure(
                    sourceId,
                    "SOURCE_CANCELLED",
                    "Source query was cancelled",
                    Map.of("operation", "feature-query"));
        }
    }

    private static void requireWithin(String sourceId, String name, long requested, long maximum) {
        if (requested > maximum) {
            throw failure(
                    sourceId,
                    "SOURCE_LIMIT_EXCEEDED",
                    "Source index limit exceeded",
                    Map.of(
                            "scope",
                            "spatialIndexBuild",
                            "limit",
                            name,
                            "requested",
                            Long.toString(requested),
                            "maximum",
                            Long.toString(maximum)));
        }
    }

    static void requireAddressable(String sourceId, int records) {
        if (records > MAXIMUM_PACKED_RECORDS) {
            throw failure(
                    sourceId,
                    "SOURCE_LIMIT_EXCEEDED",
                    "Source index array addressability exceeded",
                    Map.of(
                            "scope",
                            "spatialIndexBuild",
                            "limit",
                            "arrayAddressability",
                            "requested",
                            Integer.toString(records),
                            "maximum",
                            Integer.toString(MAXIMUM_PACKED_RECORDS)));
        }
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

    static final class CandidatePlan {
        private final String sourceId;
        private final long[] candidates;

        CandidatePlan(String sourceId, long[] candidates) {
            this.sourceId = sourceId;
            this.candidates = candidates;
        }

        boolean contains(int sourceOrdinal) {
            return (candidates[sourceOrdinal >>> 6] & (1L << sourceOrdinal)) != 0;
        }

        int nextCandidate(int fromSourceOrdinal, CancellationToken cancellation) {
            int wordIndex = fromSourceOrdinal >>> 6;
            if (wordIndex >= candidates.length) {
                checkCancellation(sourceId, cancellation);
                return -1;
            }
            long word = candidates[wordIndex] & (-1L << fromSourceOrdinal);
            int units = 0;
            while (true) {
                if ((units++ & 4095) == 0) {
                    checkCancellation(sourceId, cancellation);
                }
                if (word != 0) {
                    checkCancellation(sourceId, cancellation);
                    return (wordIndex << 6) + Long.numberOfTrailingZeros(word);
                }
                if (++wordIndex >= candidates.length) {
                    checkCancellation(sourceId, cancellation);
                    return -1;
                }
                word = candidates[wordIndex];
            }
        }

        long candidateCount() {
            long result = 0;
            for (long word : candidates) {
                result += Long.bitCount(word);
            }
            return result;
        }

        long[] wordsCopy() {
            return candidates.clone();
        }

        private static void checkCancellation(String sourceId, CancellationToken cancellation) {
            if (cancellation.isCancellationRequested()) {
                throw failure(
                        sourceId,
                        "SOURCE_CANCELLED",
                        "Source query was cancelled",
                        Map.of("operation", "feature-query"));
            }
        }
    }

    record Layout(
            int recordCount,
            int leafCount,
            int nodeCount,
            int edgeCount,
            int height,
            int stackCapacity,
            long retainedBytes,
            long buildBytes,
            long queryBytes) {
        static Layout forRecords(int records) {
            if (records < 0) {
                throw new IllegalArgumentException("records must be non-negative");
            }
            if (records == 0) {
                return new Layout(0, 0, 0, 0, 0, 0, 0, 0, 0);
            }
            int leaves = ceilDiv(records, FANOUT);
            int nodes = leaves;
            int level = leaves;
            int height = 1;
            while (level > 1) {
                level = ceilDiv(level, FANOUT);
                nodes = Math.addExact(nodes, level);
                height = Math.addExact(height, 1);
            }
            int edges = nodes - 1;
            long retained = Math.multiplyExact(4L, records);
            retained = Math.addExact(retained, Math.multiplyExact(37L, nodes));
            retained = Math.addExact(retained, Math.multiplyExact(4L, edges));
            long build = Math.addExact(retained, Math.multiplyExact(8L, records));
            int stack = Math.addExact(1, Math.multiplyExact(15, height - 1));
            long words = ((long) records + 63L) >>> 6;
            long query =
                    Math.addExact(Math.multiplyExact(8L, words), Math.multiplyExact(4L, stack));
            return new Layout(records, leaves, nodes, edges, height, stack, retained, build, query);
        }

        private static int ceilDiv(int value, int divisor) {
            return 1 + (value - 1) / divisor;
        }
    }

    private static final class Builder {
        private final List<FeatureRecord> records;
        private final int[] order;
        private final int[] scratch;
        private final int[] recordOrdinals;
        private final double[] nodeMinX;
        private final double[] nodeMinY;
        private final double[] nodeMaxX;
        private final double[] nodeMaxY;
        private final int[] nodeFirst;
        private final byte[] nodeCount;
        private final int[] childRefs;
        private int nodeWrite;
        private int recordWrite;
        private int edgeWrite;
        private int rootNode;
        private int height;

        Builder(
                List<FeatureRecord> records,
                int[] order,
                int[] scratch,
                int[] recordOrdinals,
                double[] nodeMinX,
                double[] nodeMinY,
                double[] nodeMaxX,
                double[] nodeMaxY,
                int[] nodeFirst,
                byte[] nodeCount,
                int[] childRefs) {
            this.records = records;
            this.order = order;
            this.scratch = scratch;
            this.recordOrdinals = recordOrdinals;
            this.nodeMinX = nodeMinX;
            this.nodeMinY = nodeMinY;
            this.nodeMaxX = nodeMaxX;
            this.nodeMaxY = nodeMaxY;
            this.nodeFirst = nodeFirst;
            this.nodeCount = nodeCount;
            this.childRefs = childRefs;
        }

        void build() {
            int itemCount = records.size();
            boolean recordLevel = true;
            while (true) {
                int parentCount = packLevel(itemCount, recordLevel);
                height++;
                System.arraycopy(scratch, 0, order, 0, parentCount);
                if (parentCount == 1) {
                    rootNode = order[0];
                    return;
                }
                itemCount = parentCount;
                recordLevel = false;
            }
        }

        private int packLevel(int itemCount, boolean recordLevel) {
            int groups = Layout.ceilDiv(itemCount, FANOUT);
            int slices = ceilSqrt(groups);
            stableSort(0, itemCount, true, recordLevel);
            int groupsPerSlice = Layout.ceilDiv(groups, slices);
            int itemsPerSlice = Math.multiplyExact(groupsPerSlice, FANOUT);
            int parents = 0;
            for (int sliceStart = 0; sliceStart < itemCount; ) {
                int sliceEnd = boundedEnd(sliceStart, itemsPerSlice, itemCount);
                stableSort(sliceStart, sliceEnd, false, recordLevel);
                for (int first = sliceStart; first < sliceEnd; ) {
                    int last = boundedEnd(first, FANOUT, sliceEnd);
                    int node = appendNode(first, last, recordLevel);
                    scratch[parents++] = node;
                    first = last;
                }
                sliceStart = sliceEnd;
            }
            return parents;
        }

        private int appendNode(int first, int last, boolean recordLevel) {
            int node = nodeWrite++;
            int count = last - first;
            nodeCount[node] = (byte) count;
            nodeFirst[node] = recordLevel ? recordWrite : edgeWrite;
            Envelope union = null;
            for (int index = first; index < last; index++) {
                int item = order[index];
                Envelope envelope;
                if (recordLevel) {
                    recordOrdinals[recordWrite++] = item;
                    envelope = records.get(item).geometry().envelope();
                } else {
                    childRefs[edgeWrite++] = item;
                    envelope = nodeEnvelope(item);
                }
                union = union == null ? envelope : union.union(envelope);
            }
            Envelope bounds = java.util.Objects.requireNonNull(union);
            nodeMinX[node] = bounds.minX();
            nodeMinY[node] = bounds.minY();
            nodeMaxX[node] = bounds.maxX();
            nodeMaxY[node] = bounds.maxY();
            return node;
        }

        private void stableSort(int from, int to, boolean xFirst, boolean recordLevel) {
            int length = to - from;
            for (int width = 1; width < length; width = nextWidth(width, length)) {
                for (int start = from; start < to; ) {
                    int middle = boundedEnd(start, width, to);
                    int end = boundedEnd(middle, width, to);
                    merge(start, middle, end, xFirst, recordLevel);
                    start = end;
                }
                System.arraycopy(scratch, from, order, from, length);
            }
        }

        private void merge(int start, int middle, int end, boolean xFirst, boolean recordLevel) {
            int left = start;
            int right = middle;
            int output = start;
            while (left < middle || right < end) {
                if (right >= end
                        || (left < middle
                                && compare(order[left], order[right], xFirst, recordLevel) <= 0)) {
                    scratch[output++] = order[left++];
                } else {
                    scratch[output++] = order[right++];
                }
            }
        }

        private int compare(int first, int second, boolean xFirst, boolean recordLevel) {
            Envelope one =
                    recordLevel ? records.get(first).geometry().envelope() : nodeEnvelope(first);
            Envelope two =
                    recordLevel ? records.get(second).geometry().envelope() : nodeEnvelope(second);
            return compareEnvelopeItems(one, first, two, second, xFirst);
        }

        private Envelope nodeEnvelope(int node) {
            return new Envelope(nodeMinX[node], nodeMinY[node], nodeMaxX[node], nodeMaxY[node]);
        }

        private static int nextWidth(int width, int length) {
            return width > length / 2 ? length : width * 2;
        }

        private static int ceilSqrt(int value) {
            int result = 1;
            while ((long) result * result < value) {
                result++;
            }
            return result;
        }
    }
}
