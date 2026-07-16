package io.github.mundanej.map.api;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Immutable packed-coordinate snapshot of one measurement. */
public final class MeasurementState {
    private final MeasurementPhase phase;
    private final double[] packedVertices;
    private final Optional<Coordinate> preview;
    private final DistanceResult committedDistance;
    private final Optional<DistanceResult> lastCommittedSegmentDistance;
    private final Optional<DistanceResult> previewSegmentDistance;
    private final DistanceResult displayedDistance;

    /**
     * Creates and validates a complete immutable measurement snapshot.
     *
     * @param phase lifecycle phase
     * @param packedVertices alternating finite map-coordinate x/y pairs, defensively copied
     * @param preview optional transient map-coordinate endpoint
     * @param committedDistance total committed distance in metres
     * @param lastCommittedSegmentDistance optional last committed segment distance in metres
     * @param previewSegmentDistance optional transient segment distance in metres
     */
    public MeasurementState(
            MeasurementPhase phase,
            double[] packedVertices,
            Optional<Coordinate> preview,
            DistanceResult committedDistance,
            Optional<DistanceResult> lastCommittedSegmentDistance,
            Optional<DistanceResult> previewSegmentDistance) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.packedVertices = Objects.requireNonNull(packedVertices, "packedVertices").clone();
        this.preview = Objects.requireNonNull(preview, "preview");
        this.committedDistance = Objects.requireNonNull(committedDistance, "committedDistance");
        this.lastCommittedSegmentDistance =
                Objects.requireNonNull(
                        lastCommittedSegmentDistance, "lastCommittedSegmentDistance");
        this.previewSegmentDistance =
                Objects.requireNonNull(previewSegmentDistance, "previewSegmentDistance");
        validate();
        this.displayedDistance =
                previewSegmentDistance.map(committedDistance::plus).orElse(committedDistance);
    }

    /**
     * Returns the canonical empty measurement.
     *
     * @return immutable empty state
     */
    public static MeasurementState empty() {
        return new MeasurementState(
                MeasurementPhase.EMPTY,
                new double[0],
                Optional.empty(),
                DistanceResult.ZERO,
                Optional.empty(),
                Optional.empty());
    }

    /**
     * Returns the lifecycle phase.
     *
     * @return measurement phase
     */
    public MeasurementPhase phase() {
        return phase;
    }

    /**
     * Returns the number of packed vertices.
     *
     * @return committed vertex count
     */
    public int vertexCount() {
        return packedVertices.length / 2;
    }

    /**
     * Returns one vertex by zero-based index.
     *
     * @param index zero-based committed-vertex index
     * @return immutable map coordinate
     */
    public Coordinate vertex(int index) {
        Objects.checkIndex(index, vertexCount());
        return new Coordinate(packedVertices[index * 2], packedVertices[index * 2 + 1]);
    }

    /**
     * Returns a defensive copy of packed x/y coordinate pairs.
     *
     * @return newly allocated alternating x/y ordinates
     */
    public double[] packedVertices() {
        return packedVertices.clone();
    }

    /**
     * Returns the transient preview endpoint.
     *
     * @return optional map-coordinate endpoint
     */
    public Optional<Coordinate> preview() {
        return preview;
    }

    /**
     * Returns the committed path distance.
     *
     * @return total committed distance in metres
     */
    public DistanceResult committedDistance() {
        return committedDistance;
    }

    /**
     * Returns the last committed segment distance when one exists.
     *
     * @return optional segment distance in metres
     */
    public Optional<DistanceResult> lastCommittedSegmentDistance() {
        return lastCommittedSegmentDistance;
    }

    /**
     * Returns the transient preview segment distance when one exists.
     *
     * @return optional preview distance in metres
     */
    public Optional<DistanceResult> previewSegmentDistance() {
        return previewSegmentDistance;
    }

    /**
     * Returns committed distance plus any preview segment.
     *
     * @return displayed distance in metres
     */
    public DistanceResult displayedDistance() {
        return displayedDistance;
    }

    private void validate() {
        if ((packedVertices.length & 1) != 0) {
            throw new IllegalArgumentException("packedVertices must contain x/y pairs");
        }
        for (double ordinate : packedVertices) {
            if (!Double.isFinite(ordinate)) {
                throw new IllegalArgumentException("packedVertices must be finite");
            }
        }
        int count = vertexCount();
        boolean previewPair = preview.isPresent() == previewSegmentDistance.isPresent();
        boolean lastExpected = count >= 2;
        if (!previewPair || lastCommittedSegmentDistance.isPresent() != lastExpected) {
            throw new IllegalArgumentException("segment distances do not match measurement state");
        }
        switch (phase) {
            case EMPTY -> {
                if (count != 0 || preview.isPresent() || committedDistance.metres() != 0.0) {
                    throw new IllegalArgumentException("EMPTY measurement must contain no data");
                }
            }
            case MEASURING -> {
                if (count < 1) {
                    throw new IllegalArgumentException("MEASURING requires a vertex");
                }
            }
            case COMPLETE -> {
                if (count < 2 || preview.isPresent()) {
                    throw new IllegalArgumentException(
                            "COMPLETE requires two vertices and no preview");
                }
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MeasurementState state
                && phase == state.phase
                && Arrays.equals(packedVertices, state.packedVertices)
                && preview.equals(state.preview)
                && committedDistance.equals(state.committedDistance)
                && lastCommittedSegmentDistance.equals(state.lastCommittedSegmentDistance)
                && previewSegmentDistance.equals(state.previewSegmentDistance);
    }

    @Override
    public int hashCode() {
        int result =
                Objects.hash(
                        phase,
                        preview,
                        committedDistance,
                        lastCommittedSegmentDistance,
                        previewSegmentDistance);
        return 31 * result + Arrays.hashCode(packedVertices);
    }

    @Override
    public String toString() {
        return "MeasurementState[phase="
                + phase
                + ", vertexCount="
                + vertexCount()
                + ", preview="
                + preview
                + ", displayedDistance="
                + displayedDistance
                + "]";
    }
}
