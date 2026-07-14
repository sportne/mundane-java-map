package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Polygon-specific exact ring validation, bounded classification, and geometry construction. */
final class PolygonDecoder {
    static final int POLYGON_EXACT_LIMBS = 133;
    private static final int PRODUCT_EXPONENT_ORIGIN = -2148;
    private static final long WORD_MASK = 0xffff_ffffL;
    private static final int CHECKPOINT_MASK = 4095;

    private final SourceIdentity identity;
    private final CancellationToken cancellation;
    private final ShapefileAccounting accounting;
    private final long maximumTopologyComparisons;
    private final ShpMultipartReader reader;
    private long topologyComparisons;
    private long primitiveWork;

    PolygonDecoder(
            SourceIdentity identity,
            ShapefileFileAccess.Channel channel,
            CancellationToken cancellation,
            ShapefileAccounting accounting,
            long maximumTopologyComparisons,
            Optional<Envelope> fileBox,
            ByteBuffer prefix,
            ByteBuffer scalar) {
        this.identity = identity;
        this.cancellation = cancellation;
        this.accounting = accounting;
        this.maximumTopologyComparisons = maximumTopologyComparisons;
        reader =
                new ShpMultipartReader(
                        identity.id(), channel, cancellation, accounting, fileBox, prefix, scalar);
    }

    Geometry decode(long record, long recordStart, long contentBytes) {
        ShpMultipartPlan plan =
                reader.preflight(
                        record,
                        recordStart,
                        contentBytes,
                        4,
                        POLYGON_EXACT_LIMBS,
                        "SHAPEFILE_RING_INVALID",
                        "tooShort",
                        "SHAPEFILE_RING_INVALID",
                        "tooShort");
        checkpoint();
        accounting.allocate(reservedBytes(plan), OptionalLong.of(record), recordStart + 44);
        checkpoint();
        ShpMultipartPayload payload = reader.materialize(plan);
        int partCount = plan.partCount();
        checkpoint();
        double[] bounds = new double[Math.multiplyExact(partCount, 4)];
        checkpoint();
        int[] areas = new int[Math.multiplyExact(partCount, POLYGON_EXACT_LIMBS)];
        checkpoint();
        int[] states = new int[partCount];
        checkpoint();
        int[] counts = new int[partCount];
        checkpoint();
        int[] order = new int[partCount];
        checkpoint();
        int[] scratch = new int[POLYGON_EXACT_LIMBS];
        validateRings(plan, payload, bounds, areas, states);
        classifyHoles(plan, payload, bounds, areas, states, scratch);
        checkpoint();
        return buildGeometry(plan, payload, states, counts, order);
    }

    static long checkTopologyIncrement(
            SourceIdentity identity, DiagnosticLocation location, long current, long maximum) {
        long requested;
        if (current == Long.MAX_VALUE) {
            requested = Long.MAX_VALUE;
        } else {
            requested = current + 1;
        }
        if (current == Long.MAX_VALUE || requested > maximum) {
            throw ShapefileFailures.failure(
                    identity.id(),
                    "SOURCE_LIMIT_EXCEEDED",
                    location.component().orElse("shp"),
                    location.recordNumber(),
                    location.partIndex(),
                    location.byteOffset().orElse(-1),
                    "Shapefile limit exceeded",
                    Map.of(
                            "scope",
                            "shapefileCursor",
                            "limit",
                            "topologyComparisons",
                            "requested",
                            Long.toString(requested),
                            "maximum",
                            Long.toString(maximum)));
        }
        return requested;
    }

    private long reservedBytes(ShpMultipartPlan plan) {
        long parts = plan.partCount();
        long points = plan.pointCount();
        long fences = Math.multiplyExact(Math.addExact(parts, 1), Integer.BYTES);
        long coordinates = Math.multiplyExact(points, 2L * Double.BYTES);
        long common = Math.addExact(fences, coordinates);
        long bounds = Math.multiplyExact(parts, 4L * Double.BYTES);
        long exactAreas = Math.multiplyExact(parts, (long) POLYGON_EXACT_LIMBS * Integer.BYTES);
        long classifierArrays = Math.multiplyExact(parts, 3L * Integer.BYTES);
        long predicateScratch = (long) POLYGON_EXACT_LIMBS * Integer.BYTES;
        long classifier =
                Math.addExact(
                        Math.addExact(bounds, exactAreas),
                        Math.addExact(classifierArrays, predicateScratch));
        long outputCoordinates = Math.multiplyExact(points, 4L * Double.BYTES);
        long outputFences = Math.multiplyExact(Math.addExact(parts, 1), 4L * Integer.BYTES);
        return Math.addExact(
                Math.addExact(common, classifier), Math.addExact(outputCoordinates, outputFences));
    }

    private void validateRings(
            ShpMultipartPlan plan,
            ShpMultipartPayload payload,
            double[] bounds,
            int[] areas,
            int[] states) {
        double[] packed = payload.packedCoordinates();
        int[] fences = payload.fenceposts();
        for (int ring = 0; ring < plan.partCount(); ring++) {
            checkpointPrimitive();
            int start = fences[ring];
            int end = fences[ring + 1];
            int last = end - 1;
            if (packed[start * 2] != packed[last * 2]
                    || packed[start * 2 + 1] != packed[last * 2 + 1]) {
                throw ringFailure(
                        plan,
                        ring,
                        plan.coordinateStart() + (long) last * 16,
                        "SHAPEFILE_RING_INVALID",
                        "open");
            }
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            int sign = 0;
            int areaOffset = ring * POLYGON_EXACT_LIMBS;
            for (int point = start; point < end; point++) {
                checkpointPrimitive();
                double x = packed[point * 2];
                double y = packed[point * 2 + 1];
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                if (point < last) {
                    int next = point + 1;
                    sign = accumulateProduct(areas, areaOffset, sign, x, packed[next * 2 + 1], 1);
                    sign = accumulateProduct(areas, areaOffset, sign, packed[next * 2], y, -1);
                }
            }
            if (sign == 0) {
                throw ringFailure(
                        plan,
                        ring,
                        plan.partTableStart() + (long) ring * Integer.BYTES,
                        "SHAPEFILE_RING_INVALID",
                        "zeroArea");
            }
            int boundOffset = ring * 4;
            bounds[boundOffset] = minX;
            bounds[boundOffset + 1] = minY;
            bounds[boundOffset + 2] = maxX;
            bounds[boundOffset + 3] = maxY;
            states[ring] = sign;
        }
        checkpoint();
    }

    private void classifyHoles(
            ShpMultipartPlan plan,
            ShpMultipartPayload payload,
            double[] bounds,
            int[] areas,
            int[] states,
            int[] scratch) {
        for (int hole = 0; hole < plan.partCount(); hole++) {
            checkpointPrimitive();
            if (states[hole] < 0) {
                continue;
            }
            int winner = -1;
            boolean tie = false;
            for (int shell = 0; shell < plan.partCount(); shell++) {
                checkpointComparison();
                if (states[shell] >= 0) {
                    continue;
                }
                topologyComparisons =
                        checkTopologyIncrement(
                                identity,
                                ringLocation(plan, hole),
                                topologyComparisons,
                                maximumTopologyComparisons);
                int relation = envelopeRelation(bounds, hole, shell);
                if (relation == 0) {
                    continue;
                }
                boolean inside = false;
                if (relation == 2) {
                    inside = pointInside(plan, payload, hole, shell, scratch);
                }
                if (ringsContact(plan, payload, hole, shell, scratch)) {
                    throw topologyFailure(plan, hole, "contact");
                }
                if (relation == 2 && inside) {
                    if (winner < 0) {
                        winner = shell;
                        tie = false;
                    } else {
                        int comparison = compareArea(areas, shell, winner);
                        if (comparison < 0) {
                            winner = shell;
                            tie = false;
                        } else if (comparison == 0) {
                            tie = true;
                        }
                    }
                }
            }
            if (winner < 0) {
                throw topologyFailure(plan, hole, "orphan");
            }
            if (tie) {
                throw topologyFailure(plan, hole, "equalInnermost");
            }
            states[hole] = winner;
        }
        checkpoint();
    }

    private int envelopeRelation(double[] bounds, int hole, int shell) {
        int h = hole * 4;
        int s = shell * 4;
        if (bounds[h + 2] < bounds[s]
                || bounds[h] > bounds[s + 2]
                || bounds[h + 3] < bounds[s + 1]
                || bounds[h + 1] > bounds[s + 3]) {
            return 0;
        }
        return bounds[h] > bounds[s]
                        && bounds[h + 1] > bounds[s + 1]
                        && bounds[h + 2] < bounds[s + 2]
                        && bounds[h + 3] < bounds[s + 3]
                ? 2
                : 1;
    }

    private boolean pointInside(
            ShpMultipartPlan plan,
            ShpMultipartPayload payload,
            int hole,
            int shell,
            int[] scratch) {
        double[] packed = payload.packedCoordinates();
        int[] fences = payload.fenceposts();
        double px = packed[fences[hole] * 2];
        double py = packed[fences[hole] * 2 + 1];
        boolean inside = false;
        int end = fences[shell + 1] - 1;
        for (int edge = fences[shell]; edge < end; edge++) {
            checkpointComparison();
            topologyComparisons =
                    checkTopologyIncrement(
                            identity,
                            ringLocation(plan, hole),
                            topologyComparisons,
                            maximumTopologyComparisons);
            int next = edge + 1;
            double ax = packed[edge * 2];
            double ay = packed[edge * 2 + 1];
            double bx = packed[next * 2];
            double by = packed[next * 2 + 1];
            int orientation = orient(ax, ay, bx, by, px, py, scratch);
            if (orientation == 0 && onClosedSegment(ax, ay, bx, by, px, py)) {
                throw topologyFailure(plan, hole, "contact");
            }
            if ((ay <= py && py < by && orientation > 0)
                    || (by <= py && py < ay && orientation < 0)) {
                inside = !inside;
            }
        }
        return inside;
    }

    private boolean ringsContact(
            ShpMultipartPlan plan,
            ShpMultipartPayload payload,
            int hole,
            int shell,
            int[] scratch) {
        double[] packed = payload.packedCoordinates();
        int[] fences = payload.fenceposts();
        int holeEnd = fences[hole + 1] - 1;
        int shellEnd = fences[shell + 1] - 1;
        for (int h = fences[hole]; h < holeEnd; h++) {
            for (int s = fences[shell]; s < shellEnd; s++) {
                checkpointComparison();
                topologyComparisons =
                        checkTopologyIncrement(
                                identity,
                                ringLocation(plan, hole),
                                topologyComparisons,
                                maximumTopologyComparisons);
                if (segmentsContact(packed, h, s, scratch)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean segmentsContact(double[] packed, int first, int second, int[] scratch) {
        double ax = packed[first * 2];
        double ay = packed[first * 2 + 1];
        double bx = packed[(first + 1) * 2];
        double by = packed[(first + 1) * 2 + 1];
        double cx = packed[second * 2];
        double cy = packed[second * 2 + 1];
        double dx = packed[(second + 1) * 2];
        double dy = packed[(second + 1) * 2 + 1];
        int abc = orient(ax, ay, bx, by, cx, cy, scratch);
        int abd = orient(ax, ay, bx, by, dx, dy, scratch);
        int cda = orient(cx, cy, dx, dy, ax, ay, scratch);
        int cdb = orient(cx, cy, dx, dy, bx, by, scratch);
        if ((abc == 0 && onClosedSegment(ax, ay, bx, by, cx, cy))
                || (abd == 0 && onClosedSegment(ax, ay, bx, by, dx, dy))
                || (cda == 0 && onClosedSegment(cx, cy, dx, dy, ax, ay))
                || (cdb == 0 && onClosedSegment(cx, cy, dx, dy, bx, by))) {
            return true;
        }
        return abc != 0
                && abd != 0
                && cda != 0
                && cdb != 0
                && Integer.signum(abc) != Integer.signum(abd)
                && Integer.signum(cda) != Integer.signum(cdb);
    }

    private int orient(
            double ax, double ay, double bx, double by, double cx, double cy, int[] scratch) {
        checkpoint();
        clearSegment(scratch, 0);
        int sign = 0;
        sign = accumulateProduct(scratch, 0, sign, bx, cy, 1);
        sign = accumulateProduct(scratch, 0, sign, by, cx, -1);
        sign = accumulateProduct(scratch, 0, sign, ax, cy, -1);
        sign = accumulateProduct(scratch, 0, sign, ay, cx, 1);
        sign = accumulateProduct(scratch, 0, sign, ax, by, 1);
        return accumulateProduct(scratch, 0, sign, ay, bx, -1);
    }

    private int compareArea(int[] areas, int first, int second) {
        int a = first * POLYGON_EXACT_LIMBS;
        int b = second * POLYGON_EXACT_LIMBS;
        for (int limb = POLYGON_EXACT_LIMBS - 1; limb >= 0; limb--) {
            checkpointPrimitive();
            int comparison = Integer.compareUnsigned(areas[a + limb], areas[b + limb]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private int accumulateProduct(
            int[] accumulator,
            int offset,
            int accumulatorSign,
            double first,
            double second,
            int algebraicSign) {
        long firstBits = Double.doubleToRawLongBits(first);
        long secondBits = Double.doubleToRawLongBits(second);
        long firstFraction = firstBits & 0x000f_ffff_ffff_ffffL;
        long secondFraction = secondBits & 0x000f_ffff_ffff_ffffL;
        int firstExponentBits = (int) ((firstBits >>> 52) & 0x7ff);
        int secondExponentBits = (int) ((secondBits >>> 52) & 0x7ff);
        long firstSignificand = firstExponentBits == 0 ? firstFraction : firstFraction | (1L << 52);
        long secondSignificand =
                secondExponentBits == 0 ? secondFraction : secondFraction | (1L << 52);
        if (firstSignificand == 0 || secondSignificand == 0) {
            return accumulatorSign;
        }
        int firstExponent = firstExponentBits == 0 ? -1074 : firstExponentBits - 1023 - 52;
        int secondExponent = secondExponentBits == 0 ? -1074 : secondExponentBits - 1023 - 52;
        int productSign = ((((firstBits ^ secondBits) >>> 63) == 0) ? 1 : -1) * algebraicSign;
        long low = firstSignificand * secondSignificand;
        long high = Math.multiplyHigh(firstSignificand, secondSignificand);
        int bit = Math.addExact(firstExponent + secondExponent, -PRODUCT_EXPONENT_ORIGIN);
        return mergeProduct(accumulator, offset, accumulatorSign, productSign, low, high, bit);
    }

    private int mergeProduct(
            int[] accumulator,
            int offset,
            int accumulatorSign,
            int productSign,
            long low,
            long high,
            int bit) {
        if (accumulatorSign == 0) {
            writeProduct(accumulator, offset, low, high, bit);
            return productSign;
        }
        if (accumulatorSign == productSign) {
            addProduct(accumulator, offset, low, high, bit);
            return accumulatorSign;
        }
        int comparison = compareProduct(accumulator, offset, low, high, bit);
        if (comparison == 0) {
            clearSegment(accumulator, offset);
            return 0;
        }
        if (comparison > 0) {
            subtractProduct(accumulator, offset, low, high, bit);
            return accumulatorSign;
        }
        replaceWithProductDifference(accumulator, offset, low, high, bit);
        return productSign;
    }

    private void writeProduct(int[] accumulator, int offset, long low, long high, int bit) {
        clearSegment(accumulator, offset);
        long carry = 0;
        for (int limb = 0; limb < POLYGON_EXACT_LIMBS; limb++) {
            checkpointPrimitive();
            long value = productWord(low, high, bit, limb) + carry;
            accumulator[offset + limb] = (int) value;
            carry = value >>> 32;
        }
        requireNoCarry(carry);
    }

    private void addProduct(int[] accumulator, int offset, long low, long high, int bit) {
        long carry = 0;
        for (int limb = 0; limb < POLYGON_EXACT_LIMBS; limb++) {
            checkpointPrimitive();
            long sum =
                    (accumulator[offset + limb] & WORD_MASK)
                            + productWord(low, high, bit, limb)
                            + carry;
            accumulator[offset + limb] = (int) sum;
            carry = sum >>> 32;
        }
        requireNoCarry(carry);
    }

    private int compareProduct(int[] accumulator, int offset, long low, long high, int bit) {
        for (int limb = POLYGON_EXACT_LIMBS - 1; limb >= 0; limb--) {
            checkpointPrimitive();
            int comparison =
                    Long.compareUnsigned(
                            accumulator[offset + limb] & WORD_MASK,
                            productWord(low, high, bit, limb));
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private void subtractProduct(int[] accumulator, int offset, long low, long high, int bit) {
        long borrow = 0;
        for (int limb = 0; limb < POLYGON_EXACT_LIMBS; limb++) {
            checkpointPrimitive();
            long difference =
                    (accumulator[offset + limb] & WORD_MASK)
                            - productWord(low, high, bit, limb)
                            - borrow;
            if (difference < 0) {
                accumulator[offset + limb] = (int) (difference + (1L << 32));
                borrow = 1;
            } else {
                accumulator[offset + limb] = (int) difference;
                borrow = 0;
            }
        }
        requireNoCarry(borrow);
    }

    private void replaceWithProductDifference(
            int[] accumulator, int offset, long low, long high, int bit) {
        long borrow = 0;
        for (int limb = 0; limb < POLYGON_EXACT_LIMBS; limb++) {
            checkpointPrimitive();
            long difference =
                    productWord(low, high, bit, limb)
                            - (accumulator[offset + limb] & WORD_MASK)
                            - borrow;
            if (difference < 0) {
                accumulator[offset + limb] = (int) (difference + (1L << 32));
                borrow = 1;
            } else {
                accumulator[offset + limb] = (int) difference;
                borrow = 0;
            }
        }
        requireNoCarry(borrow);
    }

    private long productWord(long low, long high, int bit, int targetLimb) {
        int base = bit >>> 5;
        int shift = bit & 31;
        int source = targetLimb - base;
        long lower = productLimb(low, high, source);
        if (shift == 0) {
            return lower;
        }
        long previous = productLimb(low, high, source - 1);
        return ((lower << shift) & WORD_MASK) | (previous >>> (32 - shift));
    }

    private static long productLimb(long low, long high, int limb) {
        return switch (limb) {
            case 0 -> low & WORD_MASK;
            case 1 -> (low >>> 32) & WORD_MASK;
            case 2 -> high & WORD_MASK;
            case 3 -> (high >>> 32) & WORD_MASK;
            default -> 0;
        };
    }

    private void clearSegment(int[] accumulator, int offset) {
        for (int limb = 0; limb < POLYGON_EXACT_LIMBS; limb++) {
            checkpointPrimitive();
            accumulator[offset + limb] = 0;
        }
    }

    private static void requireNoCarry(long value) {
        if (value != 0) {
            throw new IllegalStateException("Exact polygon accumulator exceeded its fixed bound");
        }
    }

    private Geometry buildGeometry(
            ShpMultipartPlan plan,
            ShpMultipartPayload payload,
            int[] states,
            int[] counts,
            int[] order) {
        int shellCount = 0;
        for (int ring = 0; ring < plan.partCount(); ring++) {
            checkpointPrimitive();
            if (states[ring] < 0) {
                shellCount++;
                states[ring] = -shellCount;
                counts[ring] = 1;
            }
        }
        for (int ring = 0; ring < plan.partCount(); ring++) {
            checkpointPrimitive();
            if (states[ring] >= 0) {
                counts[states[ring]]++;
            }
        }
        if (shellCount == 1) {
            return buildPolygon(plan, payload, states);
        }
        checkpoint();
        int[] polygonOffsets = new int[shellCount + 1];
        int polygon = 0;
        for (int ring = 0; ring < plan.partCount(); ring++) {
            checkpointPrimitive();
            if (states[ring] < 0) {
                polygonOffsets[polygon + 1] = polygonOffsets[polygon] + counts[ring];
                counts[ring] = polygonOffsets[polygon] + 1;
                order[polygonOffsets[polygon]] = ring;
                polygon++;
            }
        }
        for (int ring = 0; ring < plan.partCount(); ring++) {
            checkpointPrimitive();
            if (states[ring] >= 0) {
                int shell = states[ring];
                order[counts[shell]++] = ring;
            }
        }
        return buildMultiPolygon(plan, payload, order, polygonOffsets);
    }

    private PolygonGeometry buildPolygon(
            ShpMultipartPlan plan, ShpMultipartPayload payload, int[] states) {
        int shell = -1;
        for (int ring = 0; ring < plan.partCount(); ring++) {
            checkpointPrimitive();
            if (states[ring] < 0) {
                shell = ring;
                break;
            }
        }
        checkpoint();
        CoordinateSequence exterior = ringSequence(payload, shell);
        checkpoint();
        List<CoordinateSequence> holes = new ArrayList<>(plan.partCount() - 1);
        for (int ring = 0; ring < plan.partCount(); ring++) {
            checkpointPrimitive();
            if (states[ring] >= 0) {
                holes.add(ringSequence(payload, ring));
            }
        }
        checkpoint();
        return new PolygonGeometry(exterior, holes);
    }

    private CoordinateSequence ringSequence(ShpMultipartPayload payload, int ring) {
        checkpoint();
        int[] fences = payload.fenceposts();
        int start = fences[ring] * 2;
        int length = (fences[ring + 1] - fences[ring]) * 2;
        double[] slice = new double[length];
        copyCoordinates(payload.packedCoordinates(), start, slice, 0, length);
        checkpoint();
        return CoordinateSequence.of(slice);
    }

    private MultiPolygonGeometry buildMultiPolygon(
            ShpMultipartPlan plan, ShpMultipartPayload payload, int[] order, int[] polygonOffsets) {
        checkpoint();
        double[] reordered = new double[Math.multiplyExact(plan.pointCount(), 2)];
        checkpoint();
        int[] ringOffsets = new int[plan.partCount() + 1];
        int target = 0;
        int[] sourceFences = payload.fenceposts();
        double[] sourceCoordinates = payload.packedCoordinates();
        for (int position = 0; position < order.length; position++) {
            checkpointPrimitive();
            int ring = order[position];
            int sourceStart = sourceFences[ring] * 2;
            int length = (sourceFences[ring + 1] - sourceFences[ring]) * 2;
            copyCoordinates(sourceCoordinates, sourceStart, reordered, target, length);
            target += length;
            ringOffsets[position + 1] = target >>> 1;
        }
        checkpoint();
        CoordinateSequence coordinates = CoordinateSequence.of(reordered);
        checkpoint();
        return MultiPolygonGeometry.of(coordinates, ringOffsets, polygonOffsets);
    }

    private void copyCoordinates(
            double[] source, int sourceStart, double[] target, int targetStart, int length) {
        int copied = 0;
        while (copied < length) {
            checkpointPrimitive();
            int chunk = Math.min(8192, length - copied);
            System.arraycopy(source, sourceStart + copied, target, targetStart + copied, chunk);
            copied += chunk;
            primitiveWork = Math.addExact(primitiveWork, chunk >>> 1);
            checkpoint();
        }
    }

    private SourceException topologyFailure(ShpMultipartPlan plan, int ring, String reason) {
        return ringFailure(
                plan,
                ring,
                plan.partTableStart() + (long) ring * Integer.BYTES,
                "SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS",
                reason);
    }

    private SourceException ringFailure(
            ShpMultipartPlan plan, int ring, long offset, String code, String reason) {
        return ShapefileFailures.failure(
                identity.id(),
                code,
                "shp",
                OptionalLong.of(plan.record()),
                OptionalInt.of(ring),
                offset,
                "Shapefile polygon ring is invalid",
                Map.of("reason", reason));
    }

    private DiagnosticLocation ringLocation(ShpMultipartPlan plan, int ring) {
        return new DiagnosticLocation(
                Optional.of("shp"),
                OptionalLong.of(plan.record()),
                OptionalInt.of(ring),
                OptionalInt.empty(),
                Optional.empty(),
                OptionalLong.of(plan.partTableStart() + (long) ring * Integer.BYTES));
    }

    private static boolean onClosedSegment(
            double ax, double ay, double bx, double by, double px, double py) {
        return px >= Math.min(ax, bx)
                && px <= Math.max(ax, bx)
                && py >= Math.min(ay, by)
                && py <= Math.max(ay, by);
    }

    private void checkpointComparison() {
        if ((topologyComparisons & CHECKPOINT_MASK) == 0) {
            checkpoint();
        }
    }

    private void checkpointPrimitive() {
        if ((primitiveWork++ & CHECKPOINT_MASK) == 0) {
            checkpoint();
        }
    }

    private void checkpoint() {
        Shapefiles.checkpoint(identity.id(), cancellation);
    }
}
