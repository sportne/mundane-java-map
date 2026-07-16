package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.PointGeometry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fixed, reproducible fixture definitions for the G7-002 comparison evidence. */
final class IndexComparisonFixture {
    static final List<Integer> SIZES = List.of(32, 128, 512, 2_048, 8_192, 32_768, 131_072);
    static final FeatureSourceLimits SOURCE_LIMITS =
            new FeatureSourceLimits(
                    new FeatureQueryLimits(131_072, 131_072, 131_072, 1, 2_097_152, 8_388_608, 1));

    private IndexComparisonFixture() {}

    static List<FeatureRecord> records(int size) {
        Dimensions dimensions = dimensions(size);
        List<FeatureRecord> result = new ArrayList<>(size);
        for (int ordinal = 0; ordinal < size; ordinal++) {
            result.add(
                    new FeatureRecord(
                            String.format(Locale.ROOT, "index:%06d", ordinal),
                            "",
                            new PointGeometry(
                                    new Coordinate(
                                            Math.floorMod(ordinal, dimensions.columns()) * 1_000.0,
                                            Math.floorDiv(ordinal, dimensions.columns())
                                                    * 1_000.0)),
                            Map.of()));
        }
        return List.copyOf(result);
    }

    static List<Envelope> viewports(int size, EvidenceConfiguration.Profile profile) {
        Dimensions dimensions = dimensions(size);
        int count = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 24;
        List<Envelope> result = new ArrayList<>(count);
        for (int ordinal = 0; ordinal < count; ordinal++) {
            result.add(viewport(dimensions, ordinal));
        }
        return List.copyOf(result);
    }

    static long expectedRecords(int size, EvidenceConfiguration.Profile profile) {
        int count = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 24;
        long result = 0;
        for (int ordinal = 0; ordinal < count; ordinal++) {
            result = Math.addExact(result, expectedRecords(size, ordinal));
        }
        return result;
    }

    static int[] selectedOrdinals(int size, EvidenceConfiguration.Profile profile) {
        Dimensions dimensions = dimensions(size);
        int count = profile == EvidenceConfiguration.Profile.BASELINE ? 256 : 24;
        int[] result = new int[Math.toIntExact(expectedRecords(size, profile))];
        int write = 0;
        for (int ordinal = 0; ordinal < count; ordinal++) {
            int kind = Math.floorMod(ordinal, 6);
            if (kind == 0) {
                continue;
            }
            if (kind == 1) {
                int column = Math.floorMod(37 * ordinal, dimensions.columns());
                int row = Math.floorMod(53 * ordinal, dimensions.rows());
                result[write++] = row * dimensions.columns() + column;
                continue;
            }
            if (kind == 5) {
                for (int selected = 0; selected < size; selected++) {
                    result[write++] = selected;
                }
                continue;
            }
            int selected = expectedRecords(size, ordinal);
            int exponent = Integer.numberOfTrailingZeros(selected);
            int width = 1 << Math.floorDiv(exponent, 2);
            int height = Math.floorDiv(selected, width);
            int originColumn = Math.floorMod(37 * ordinal, dimensions.columns() - width + 1);
            int originRow = Math.floorMod(53 * ordinal, dimensions.rows() - height + 1);
            for (int row = originRow; row < originRow + height; row++) {
                for (int column = originColumn; column < originColumn + width; column++) {
                    result[write++] = row * dimensions.columns() + column;
                }
            }
        }
        if (write != result.length) {
            throw new IllegalStateException("Comparison selection cardinality changed");
        }
        return result;
    }

    static int expectedRecords(int size, int viewportOrdinal) {
        return switch (Math.floorMod(viewportOrdinal, 6)) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> Math.max(1, size / 1_024);
            case 3 -> Math.max(1, size / 128);
            case 4 -> Math.max(1, size / 8);
            case 5 -> size;
            default -> throw new AssertionError("unreachable");
        };
    }

    static Dimensions dimensions(int size) {
        int index = SIZES.indexOf(size);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown comparison size: " + size);
        }
        return new Dimensions(8 << index, 4 << index);
    }

    static Layout layout(int records) {
        int leaves = ceilDiv(records, 16);
        int nodes = leaves;
        int level = leaves;
        int height = 1;
        while (level > 1) {
            level = ceilDiv(level, 16);
            nodes = Math.addExact(nodes, level);
            height++;
        }
        int edges = nodes - 1;
        long retained =
                Math.addExact(Math.multiplyExact(4L, records), Math.multiplyExact(37L, nodes));
        retained = Math.addExact(retained, Math.multiplyExact(4L, edges));
        return new Layout(leaves, nodes, height, retained);
    }

    /**
     * Computes exact STR-16 candidate work with an independent object-tree reference.
     *
     * <p>This intentionally does not open an indexed production source or use production layout or
     * query-plan helpers. It is an evidence definition, not an implementation metric probe.
     */
    static long referenceCandidateTotal(int size, EvidenceConfiguration.Profile profile) {
        ReferenceNode root = referenceTree(size);
        long total = 0;
        for (Envelope viewport : viewports(size, profile)) {
            total = Math.addExact(total, referenceCandidates(root, viewport));
        }
        return total;
    }

    static int referenceCandidateCount(int size, Envelope viewport) {
        return referenceCandidates(referenceTree(size), viewport);
    }

    private static ReferenceNode referenceTree(int size) {
        List<ReferenceItem> level = new ArrayList<>(size);
        for (FeatureRecord record : records(size)) {
            level.add(new ReferenceItem(record.geometry().envelope(), level.size(), null));
        }
        int nextKey = size;
        boolean leaves = true;
        while (true) {
            int groups = ceilDiv(level.size(), 16);
            int slices = ceilSqrt(groups);
            level.sort(referenceComparator(true));
            int groupsPerSlice = ceilDiv(groups, slices);
            int itemsPerSlice = Math.multiplyExact(groupsPerSlice, 16);
            List<ReferenceItem> parents = new ArrayList<>(groups);
            for (int start = 0; start < level.size(); start += itemsPerSlice) {
                int end = Math.min(level.size(), start + itemsPerSlice);
                List<ReferenceItem> slice = new ArrayList<>(level.subList(start, end));
                slice.sort(referenceComparator(false));
                for (int first = 0; first < slice.size(); first += 16) {
                    int last = Math.min(slice.size(), first + 16);
                    List<ReferenceItem> children = List.copyOf(slice.subList(first, last));
                    Envelope union = children.getFirst().bounds();
                    for (int child = 1; child < children.size(); child++) {
                        union = union.union(children.get(child).bounds());
                    }
                    parents.add(
                            new ReferenceItem(
                                    union, nextKey++, new ReferenceNode(union, leaves, children)));
                }
            }
            if (parents.size() == 1) {
                return parents.getFirst().node();
            }
            level = parents;
            leaves = false;
        }
    }

    private static int referenceCandidates(ReferenceNode node, Envelope query) {
        Envelope bounds = node.bounds();
        if (bounds.maxX() < query.minX()
                || bounds.minX() > query.maxX()
                || bounds.maxY() < query.minY()
                || bounds.minY() > query.maxY()) {
            return 0;
        }
        if (node.leaf()) {
            return node.children().size();
        }
        int result = 0;
        for (ReferenceItem child : node.children()) {
            result = Math.addExact(result, referenceCandidates(child.node(), query));
        }
        return result;
    }

    private static Comparator<ReferenceItem> referenceComparator(boolean xFirst) {
        return (one, two) -> {
            int result = compareReference(one.bounds(), two.bounds(), xFirst);
            return result != 0 ? result : Integer.compare(one.key(), two.key());
        };
    }

    private static int compareReference(Envelope one, Envelope two, boolean xFirst) {
        double onePrimary = xFirst ? centerX(one) : centerY(one);
        double twoPrimary = xFirst ? centerX(two) : centerY(two);
        int result = Double.compare(onePrimary, twoPrimary);
        if (result == 0) {
            result =
                    Double.compare(
                            xFirst ? centerY(one) : centerX(one),
                            xFirst ? centerY(two) : centerX(two));
        }
        double[] oneTies =
                xFirst
                        ? new double[] {one.minX(), one.minY(), one.maxX(), one.maxY()}
                        : new double[] {one.minY(), one.minX(), one.maxY(), one.maxX()};
        double[] twoTies =
                xFirst
                        ? new double[] {two.minX(), two.minY(), two.maxX(), two.maxY()}
                        : new double[] {two.minY(), two.minX(), two.maxY(), two.maxX()};
        for (int index = 0; result == 0 && index < oneTies.length; index++) {
            result = Double.compare(oneTies[index], twoTies[index]);
        }
        return result;
    }

    private static double centerX(Envelope envelope) {
        return envelope.minX() + envelope.width() / 2.0;
    }

    private static double centerY(Envelope envelope) {
        return envelope.minY() + envelope.height() / 2.0;
    }

    private static int ceilSqrt(int value) {
        int result = 1;
        while ((long) result * result < value) {
            result++;
        }
        return result;
    }

    private static Envelope viewport(Dimensions dimensions, int ordinal) {
        double maxX = (dimensions.columns() - 1) * 1_000.0;
        double maxY = (dimensions.rows() - 1) * 1_000.0;
        int kind = Math.floorMod(ordinal, 6);
        if (kind == 0) {
            return new Envelope(maxX + 500, maxY + 500, maxX + 1_500, maxY + 1_500);
        }
        if (kind == 1) {
            int column = Math.floorMod(37 * ordinal, dimensions.columns());
            int row = Math.floorMod(53 * ordinal, dimensions.rows());
            double x = column * 1_000.0;
            double y = row * 1_000.0;
            return new Envelope(x - 500, y - 500, x, y);
        }
        if (kind == 5) {
            return new Envelope(-500, -500, maxX + 500, maxY + 500);
        }
        int size = Math.multiplyExact(dimensions.columns(), dimensions.rows());
        int selected = expectedRecords(size, ordinal);
        int exponent = Integer.numberOfTrailingZeros(selected);
        int width = 1 << Math.floorDiv(exponent, 2);
        int height = Math.floorDiv(selected, width);
        int originColumn = Math.floorMod(37 * ordinal, dimensions.columns() - width + 1);
        int originRow = Math.floorMod(53 * ordinal, dimensions.rows() - height + 1);
        return new Envelope(
                originColumn * 1_000.0 - 500,
                originRow * 1_000.0 - 500,
                (originColumn + width - 1) * 1_000.0 + 500,
                (originRow + height - 1) * 1_000.0 + 500);
    }

    private static int ceilDiv(int value, int divisor) {
        return Math.floorDiv(value - 1, divisor) + 1;
    }

    record Dimensions(int columns, int rows) {}

    record Layout(int leaves, int nodes, int height, long retainedBytes) {}

    private record ReferenceItem(Envelope bounds, int key, ReferenceNode node) {}

    private record ReferenceNode(Envelope bounds, boolean leaf, List<ReferenceItem> children) {}
}
