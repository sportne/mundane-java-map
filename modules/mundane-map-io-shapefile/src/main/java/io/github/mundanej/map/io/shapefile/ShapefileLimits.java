package io.github.mundanej.map.io.shapefile;

import java.util.Objects;

/**
 * Immutable ceilings for bounded Level 1 Shapefile parsing.
 *
 * <p>Each limit must be positive. Allocation limits count parser-owned materialization and are
 * applied independently of the format-neutral feature-source limits.
 */
public final class ShapefileLimits {
    private static final ShapefileLimits DEFAULTS =
            new ShapefileLimits(
                    1_073_741_824L,
                    1_000_000L,
                    67_108_864L,
                    100_000L,
                    1_000_000L,
                    10_000_000L,
                    255L,
                    254L,
                    256L,
                    65_536L,
                    16_777_216L,
                    268_435_456L);
    private final long maximumComponentBytes, maximumPhysicalRecords, maximumRecordBytes;
    private final long maximumParts, maximumPoints, maximumTopologyComparisons;
    private final long maximumDbfFields, maximumDbfFieldWidth, maximumCpgBytes, maximumPrjBytes;
    private final long maximumDecodedTextCharacters, maximumParserAllocationBytes;

    private ShapefileLimits(
            long component,
            long records,
            long recordBytes,
            long parts,
            long points,
            long topology,
            long fields,
            long fieldWidth,
            long cpg,
            long prj,
            long text,
            long allocation) {
        maximumComponentBytes = positive(component, "maximumComponentBytes");
        maximumPhysicalRecords = positive(records, "maximumPhysicalRecords");
        maximumRecordBytes = positive(recordBytes, "maximumRecordBytes");
        maximumParts = positive(parts, "maximumParts");
        maximumPoints = positive(points, "maximumPoints");
        maximumTopologyComparisons = positive(topology, "maximumTopologyComparisons");
        maximumDbfFields = positive(fields, "maximumDbfFields");
        maximumDbfFieldWidth = positive(fieldWidth, "maximumDbfFieldWidth");
        maximumCpgBytes = positive(cpg, "maximumCpgBytes");
        maximumPrjBytes = positive(prj, "maximumPrjBytes");
        maximumDecodedTextCharacters = positive(text, "maximumDecodedTextCharacters");
        maximumParserAllocationBytes = positive(allocation, "maximumParserAllocationBytes");
    }

    /**
     * Returns the shared Level 1 parser limits.
     *
     * @return default Shapefile parser limits
     */
    public static ShapefileLimits defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the maximum encoded size of an individual Shapefile component.
     *
     * @return maximum component size in bytes
     */
    public long maximumComponentBytes() {
        return maximumComponentBytes;
    }

    /**
     * Returns the maximum number of physical SHP records examined by a cursor.
     *
     * @return maximum physical record count
     */
    public long maximumPhysicalRecords() {
        return maximumPhysicalRecords;
    }

    /**
     * Returns the maximum encoded content size of one SHP record.
     *
     * @return maximum record content size in bytes
     */
    public long maximumRecordBytes() {
        return maximumRecordBytes;
    }

    /**
     * Returns the maximum number of parts in a multipart geometry.
     *
     * <p>This input begins being consumed by the G5-004 polyline slice.
     *
     * @return maximum parts per record
     */
    public long maximumParts() {
        return maximumParts;
    }

    /**
     * Returns the maximum number of points in one SHP record.
     *
     * @return maximum points per record
     */
    public long maximumPoints() {
        return maximumPoints;
    }

    /**
     * Returns the maximum number of topology comparisons allowed for a record.
     *
     * <p>This input begins being consumed by the G5-005 polygon slice.
     *
     * @return maximum topology comparison count
     */
    public long maximumTopologyComparisons() {
        return maximumTopologyComparisons;
    }

    /**
     * Returns the maximum number of fields in a DBF schema.
     *
     * <p>This input begins being consumed by the G5-006 attribute slice.
     *
     * @return maximum DBF field count
     */
    public long maximumDbfFields() {
        return maximumDbfFields;
    }

    /**
     * Returns the maximum encoded width of one DBF field.
     *
     * <p>This input begins being consumed by the G5-006 attribute slice.
     *
     * @return maximum DBF field width in bytes
     */
    public long maximumDbfFieldWidth() {
        return maximumDbfFieldWidth;
    }

    /**
     * Returns the maximum encoded size of a CPG sidecar.
     *
     * <p>This input begins being consumed by the G5-006 attribute slice.
     *
     * @return maximum CPG size in bytes
     */
    public long maximumCpgBytes() {
        return maximumCpgBytes;
    }

    /**
     * Returns the maximum encoded size of a PRJ sidecar.
     *
     * <p>This input begins being consumed by the G5-007 CRS slice.
     *
     * @return maximum PRJ size in bytes
     */
    public long maximumPrjBytes() {
        return maximumPrjBytes;
    }

    /**
     * Returns the maximum number of decoded text characters produced by the parser.
     *
     * <p>This input begins being consumed by the G5-006 attribute slice.
     *
     * @return maximum decoded character count
     */
    public long maximumDecodedTextCharacters() {
        return maximumDecodedTextCharacters;
    }

    /**
     * Returns the maximum cumulative parser-owned allocation charged to one operation.
     *
     * @return maximum cumulative parser allocation in bytes
     */
    public long maximumParserAllocationBytes() {
        return maximumParserAllocationBytes;
    }

    /**
     * Returns a copy with a different component-size ceiling.
     *
     * @param maximumComponentBytes maximum encoded component size in bytes
     * @return a copy containing {@code maximumComponentBytes}
     * @throws IllegalArgumentException if {@code maximumComponentBytes} is not positive
     */
    public ShapefileLimits withMaximumComponentBytes(long maximumComponentBytes) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different physical-record ceiling.
     *
     * @param maximumPhysicalRecords maximum physical SHP records examined by a cursor
     * @return a copy containing {@code maximumPhysicalRecords}
     * @throws IllegalArgumentException if {@code maximumPhysicalRecords} is not positive
     */
    public ShapefileLimits withMaximumPhysicalRecords(long maximumPhysicalRecords) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different record-content ceiling.
     *
     * @param maximumRecordBytes maximum encoded record content size in bytes
     * @return a copy containing {@code maximumRecordBytes}
     * @throws IllegalArgumentException if {@code maximumRecordBytes} is not positive
     */
    public ShapefileLimits withMaximumRecordBytes(long maximumRecordBytes) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different multipart-geometry part ceiling.
     *
     * @param maximumParts maximum parts in one geometry record
     * @return a copy containing {@code maximumParts}
     * @throws IllegalArgumentException if {@code maximumParts} is not positive
     */
    public ShapefileLimits withMaximumParts(long maximumParts) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different per-record point ceiling.
     *
     * @param maximumPoints maximum points in one SHP record
     * @return a copy containing {@code maximumPoints}
     * @throws IllegalArgumentException if {@code maximumPoints} is not positive
     */
    public ShapefileLimits withMaximumPoints(long maximumPoints) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different topology-comparison ceiling.
     *
     * @param maximumTopologyComparisons maximum topology comparisons for one record
     * @return a copy containing {@code maximumTopologyComparisons}
     * @throws IllegalArgumentException if {@code maximumTopologyComparisons} is not positive
     */
    public ShapefileLimits withMaximumTopologyComparisons(long maximumTopologyComparisons) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different DBF-field ceiling.
     *
     * @param maximumDbfFields maximum fields in a DBF schema
     * @return a copy containing {@code maximumDbfFields}
     * @throws IllegalArgumentException if {@code maximumDbfFields} is not positive
     */
    public ShapefileLimits withMaximumDbfFields(long maximumDbfFields) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different DBF-field-width ceiling.
     *
     * @param maximumDbfFieldWidth maximum encoded field width in bytes
     * @return a copy containing {@code maximumDbfFieldWidth}
     * @throws IllegalArgumentException if {@code maximumDbfFieldWidth} is not positive
     */
    public ShapefileLimits withMaximumDbfFieldWidth(long maximumDbfFieldWidth) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different CPG-size ceiling.
     *
     * @param maximumCpgBytes maximum encoded CPG size in bytes
     * @return a copy containing {@code maximumCpgBytes}
     * @throws IllegalArgumentException if {@code maximumCpgBytes} is not positive
     */
    public ShapefileLimits withMaximumCpgBytes(long maximumCpgBytes) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different PRJ-size ceiling.
     *
     * @param maximumPrjBytes maximum encoded PRJ size in bytes
     * @return a copy containing {@code maximumPrjBytes}
     * @throws IllegalArgumentException if {@code maximumPrjBytes} is not positive
     */
    public ShapefileLimits withMaximumPrjBytes(long maximumPrjBytes) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different decoded-text ceiling.
     *
     * @param maximumDecodedTextCharacters maximum characters produced by text decoding
     * @return a copy containing {@code maximumDecodedTextCharacters}
     * @throws IllegalArgumentException if {@code maximumDecodedTextCharacters} is not positive
     */
    public ShapefileLimits withMaximumDecodedTextCharacters(long maximumDecodedTextCharacters) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    /**
     * Returns a copy with a different cumulative parser-allocation ceiling.
     *
     * @param maximumParserAllocationBytes maximum parser-owned allocation in bytes
     * @return a copy containing {@code maximumParserAllocationBytes}
     * @throws IllegalArgumentException if {@code maximumParserAllocationBytes} is not positive
     */
    public ShapefileLimits withMaximumParserAllocationBytes(long maximumParserAllocationBytes) {
        return copy(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    private ShapefileLimits copy(
            long a,
            long b,
            long c,
            long d,
            long e,
            long f,
            long g,
            long h,
            long i,
            long j,
            long k,
            long l) {
        return new ShapefileLimits(a, b, c, d, e, f, g, h, i, j, k, l);
    }

    private static long positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShapefileLimits v
                && maximumComponentBytes == v.maximumComponentBytes
                && maximumPhysicalRecords == v.maximumPhysicalRecords
                && maximumRecordBytes == v.maximumRecordBytes
                && maximumParts == v.maximumParts
                && maximumPoints == v.maximumPoints
                && maximumTopologyComparisons == v.maximumTopologyComparisons
                && maximumDbfFields == v.maximumDbfFields
                && maximumDbfFieldWidth == v.maximumDbfFieldWidth
                && maximumCpgBytes == v.maximumCpgBytes
                && maximumPrjBytes == v.maximumPrjBytes
                && maximumDecodedTextCharacters == v.maximumDecodedTextCharacters
                && maximumParserAllocationBytes == v.maximumParserAllocationBytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                maximumComponentBytes,
                maximumPhysicalRecords,
                maximumRecordBytes,
                maximumParts,
                maximumPoints,
                maximumTopologyComparisons,
                maximumDbfFields,
                maximumDbfFieldWidth,
                maximumCpgBytes,
                maximumPrjBytes,
                maximumDecodedTextCharacters,
                maximumParserAllocationBytes);
    }

    @Override
    public String toString() {
        return "ShapefileLimits[componentBytes="
                + maximumComponentBytes
                + ", physicalRecords="
                + maximumPhysicalRecords
                + ", recordBytes="
                + maximumRecordBytes
                + ", parts="
                + maximumParts
                + ", points="
                + maximumPoints
                + ", topologyComparisons="
                + maximumTopologyComparisons
                + ", dbfFields="
                + maximumDbfFields
                + ", dbfFieldWidth="
                + maximumDbfFieldWidth
                + ", cpgBytes="
                + maximumCpgBytes
                + ", prjBytes="
                + maximumPrjBytes
                + ", decodedTextCharacters="
                + maximumDecodedTextCharacters
                + ", parserAllocationBytes="
                + maximumParserAllocationBytes
                + "]";
    }
}
