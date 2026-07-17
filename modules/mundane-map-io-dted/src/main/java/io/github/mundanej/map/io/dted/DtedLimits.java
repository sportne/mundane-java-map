package io.github.mundanej.map.io.dted;

/** Immutable resource ceilings for one DTED open transaction. */
public final class DtedLimits {
    private static final DtedLimits DEFAULTS =
            new DtedLimits(33_554_432L, 4_096, 4_096, 16_777_216L, 8_192, 268_435_456L);

    private final long maximumFileBytes;
    private final int maximumProfiles;
    private final int maximumSamplesPerProfile;
    private final long maximumTotalSamples;
    private final int maximumProfileBytes;
    private final long maximumParserAllocationBytes;

    private DtedLimits(
            long maximumFileBytes,
            int maximumProfiles,
            int maximumSamplesPerProfile,
            long maximumTotalSamples,
            int maximumProfileBytes,
            long maximumParserAllocationBytes) {
        this.maximumFileBytes = positive(maximumFileBytes, "maximumFileBytes");
        this.maximumProfiles = positive(maximumProfiles, "maximumProfiles");
        this.maximumSamplesPerProfile =
                positive(maximumSamplesPerProfile, "maximumSamplesPerProfile");
        this.maximumTotalSamples = positive(maximumTotalSamples, "maximumTotalSamples");
        this.maximumProfileBytes = positive(maximumProfileBytes, "maximumProfileBytes");
        this.maximumParserAllocationBytes =
                positive(maximumParserAllocationBytes, "maximumParserAllocationBytes");
    }

    /**
     * Returns the conservative limits that admit every supported one-degree DTED cell.
     *
     * @return shared immutable defaults
     */
    public static DtedLimits defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the maximum captured file size in bytes.
     *
     * @return positive byte ceiling
     */
    public long maximumFileBytes() {
        return maximumFileBytes;
    }

    /**
     * Returns a copy with the requested maximum captured file size.
     *
     * @param maximum positive byte ceiling
     * @return immutable updated limits
     * @throws IllegalArgumentException if {@code maximum} is not positive
     */
    public DtedLimits withMaximumFileBytes(long maximum) {
        return copy(
                maximum,
                maximumProfiles,
                maximumSamplesPerProfile,
                maximumTotalSamples,
                maximumProfileBytes,
                maximumParserAllocationBytes);
    }

    /**
     * Returns the maximum longitude-profile count.
     *
     * @return positive profile ceiling
     */
    public int maximumProfiles() {
        return maximumProfiles;
    }

    /**
     * Returns a copy with the requested maximum longitude-profile count.
     *
     * @param maximum positive profile ceiling
     * @return immutable updated limits
     * @throws IllegalArgumentException if {@code maximum} is not positive
     */
    public DtedLimits withMaximumProfiles(int maximum) {
        return copy(
                maximumFileBytes,
                maximum,
                maximumSamplesPerProfile,
                maximumTotalSamples,
                maximumProfileBytes,
                maximumParserAllocationBytes);
    }

    /**
     * Returns the maximum sample count in one profile.
     *
     * @return positive sample ceiling
     */
    public int maximumSamplesPerProfile() {
        return maximumSamplesPerProfile;
    }

    /**
     * Returns a copy with the requested maximum sample count in one profile.
     *
     * @param maximum positive sample ceiling
     * @return immutable updated limits
     * @throws IllegalArgumentException if {@code maximum} is not positive
     */
    public DtedLimits withMaximumSamplesPerProfile(int maximum) {
        return copy(
                maximumFileBytes,
                maximumProfiles,
                maximum,
                maximumTotalSamples,
                maximumProfileBytes,
                maximumParserAllocationBytes);
    }

    /**
     * Returns the maximum total sample count.
     *
     * @return positive total-sample ceiling
     */
    public long maximumTotalSamples() {
        return maximumTotalSamples;
    }

    /**
     * Returns a copy with the requested maximum total sample count.
     *
     * @param maximum positive total-sample ceiling
     * @return immutable updated limits
     * @throws IllegalArgumentException if {@code maximum} is not positive
     */
    public DtedLimits withMaximumTotalSamples(long maximum) {
        return copy(
                maximumFileBytes,
                maximumProfiles,
                maximumSamplesPerProfile,
                maximum,
                maximumProfileBytes,
                maximumParserAllocationBytes);
    }

    /**
     * Returns the maximum complete data-record size in bytes.
     *
     * @return positive record-byte ceiling
     */
    public int maximumProfileBytes() {
        return maximumProfileBytes;
    }

    /**
     * Returns a copy with the requested maximum complete data-record size.
     *
     * @param maximum positive record-byte ceiling
     * @return immutable updated limits
     * @throws IllegalArgumentException if {@code maximum} is not positive
     */
    public DtedLimits withMaximumProfileBytes(int maximum) {
        return copy(
                maximumFileBytes,
                maximumProfiles,
                maximumSamplesPerProfile,
                maximumTotalSamples,
                maximum,
                maximumParserAllocationBytes);
    }

    /**
     * Returns the maximum cumulative logical primitive allocation in bytes.
     *
     * @return positive allocation-byte ceiling
     */
    public long maximumParserAllocationBytes() {
        return maximumParserAllocationBytes;
    }

    /**
     * Returns a copy with the requested cumulative logical primitive allocation ceiling.
     *
     * @param maximum positive allocation-byte ceiling
     * @return immutable updated limits
     * @throws IllegalArgumentException if {@code maximum} is not positive
     */
    public DtedLimits withMaximumParserAllocationBytes(long maximum) {
        return copy(
                maximumFileBytes,
                maximumProfiles,
                maximumSamplesPerProfile,
                maximumTotalSamples,
                maximumProfileBytes,
                maximum);
    }

    private DtedLimits copy(
            long fileBytes,
            int profiles,
            int samplesPerProfile,
            long totalSamples,
            int profileBytes,
            long parserAllocationBytes) {
        return new DtedLimits(
                fileBytes,
                profiles,
                samplesPerProfile,
                totalSamples,
                profileBytes,
                parserAllocationBytes);
    }

    private static int positive(int value, String parameter) {
        if (value <= 0) {
            throw new IllegalArgumentException(parameter + " must be positive");
        }
        return value;
    }

    private static long positive(long value, String parameter) {
        if (value <= 0) {
            throw new IllegalArgumentException(parameter + " must be positive");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof DtedLimits limits
                        && maximumFileBytes == limits.maximumFileBytes
                        && maximumProfiles == limits.maximumProfiles
                        && maximumSamplesPerProfile == limits.maximumSamplesPerProfile
                        && maximumTotalSamples == limits.maximumTotalSamples
                        && maximumProfileBytes == limits.maximumProfileBytes
                        && maximumParserAllocationBytes == limits.maximumParserAllocationBytes);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(maximumFileBytes);
        result = 31 * result + maximumProfiles;
        result = 31 * result + maximumSamplesPerProfile;
        result = 31 * result + Long.hashCode(maximumTotalSamples);
        result = 31 * result + maximumProfileBytes;
        return 31 * result + Long.hashCode(maximumParserAllocationBytes);
    }

    @Override
    public String toString() {
        return "DtedLimits[maximumFileBytes="
                + maximumFileBytes
                + ", maximumProfiles="
                + maximumProfiles
                + ", maximumSamplesPerProfile="
                + maximumSamplesPerProfile
                + ", maximumTotalSamples="
                + maximumTotalSamples
                + ", maximumProfileBytes="
                + maximumProfileBytes
                + ", maximumParserAllocationBytes="
                + maximumParserAllocationBytes
                + "]";
    }
}
