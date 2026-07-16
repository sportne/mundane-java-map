package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Geometry;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Immutable operation-local authoritative/rendering geometry pair and component mapping. */
public final class ScreenGeometryOptimization {
    private final Geometry authoritativeGeometry;
    private final Geometry renderingGeometry;
    private final ScreenGeometryOptimizationOutcome outcome;
    private final int[] componentOffsets;
    private final int implicitIdentityComponentCount;

    ScreenGeometryOptimization(
            Geometry authoritativeGeometry,
            Geometry renderingGeometry,
            ScreenGeometryOptimizationOutcome outcome,
            int[] componentOffsets) {
        this.authoritativeGeometry =
                Objects.requireNonNull(authoritativeGeometry, "authoritativeGeometry");
        this.renderingGeometry = renderingGeometry;
        this.outcome = Objects.requireNonNull(outcome, "outcome");
        this.componentOffsets = componentOffsets.clone();
        implicitIdentityComponentCount = -1;
        if (this.componentOffsets.length < 2
                || this.componentOffsets[0] != 0
                || (outcome == ScreenGeometryOptimizationOutcome.PATH_CULLED)
                        != (renderingGeometry == null)) {
            throw new IllegalArgumentException("Invalid screen optimization result");
        }
        for (int index = 1; index < this.componentOffsets.length; index++) {
            if (this.componentOffsets[index] < this.componentOffsets[index - 1]) {
                throw new IllegalArgumentException("Component offsets must be monotonic");
            }
        }
    }

    ScreenGeometryOptimization(Geometry authoritativeGeometry, int identityComponentCount) {
        this.authoritativeGeometry =
                Objects.requireNonNull(authoritativeGeometry, "authoritativeGeometry");
        if (identityComponentCount <= 0) {
            throw new IllegalArgumentException("Identity component count must be positive");
        }
        renderingGeometry = authoritativeGeometry;
        outcome = ScreenGeometryOptimizationOutcome.FALLBACK;
        componentOffsets = null;
        implicitIdentityComponentCount = identityComponentCount;
    }

    /** Returns the authoritative immutable screen geometry by reference. */
    public Geometry authoritativeGeometry() {
        return authoritativeGeometry;
    }

    /** Returns rendering geometry, absent only for a culled path. */
    public Optional<Geometry> renderingGeometry() {
        return Optional.ofNullable(renderingGeometry);
    }

    /** Returns the stable aggregate outcome. */
    public ScreenGeometryOptimizationOutcome outcome() {
        return outcome;
    }

    /** Returns the authoritative source component count. */
    public int sourceComponentCount() {
        return componentOffsets == null
                ? implicitIdentityComponentCount
                : componentOffsets.length - 1;
    }

    /** Returns the rendering component count. */
    public int renderComponentCount() {
        return componentOffsets == null
                ? implicitIdentityComponentCount
                : componentOffsets[componentOffsets.length - 1];
    }

    /** Returns one rendering-component fencepost for an authoritative component. */
    public int renderComponentOffset(int sourceComponentFenceIndex) {
        if (sourceComponentFenceIndex < 0 || sourceComponentFenceIndex > sourceComponentCount()) {
            throw new IndexOutOfBoundsException(sourceComponentFenceIndex);
        }
        return componentOffsets == null
                ? sourceComponentFenceIndex
                : componentOffsets[sourceComponentFenceIndex];
    }

    /** Returns a defensive component-offset copy. */
    public int[] renderComponentOffsets() {
        if (componentOffsets == null) {
            int[] identity = new int[implicitIdentityComponentCount + 1];
            for (int index = 1; index < identity.length; index++) {
                identity[index] = index;
            }
            return identity;
        }
        return componentOffsets.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ScreenGeometryOptimization value
                && authoritativeGeometry.equals(value.authoritativeGeometry)
                && Objects.equals(renderingGeometry, value.renderingGeometry)
                && outcome == value.outcome
                && sourceComponentCount() == value.sourceComponentCount()
                && mappingEquals(value);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * authoritativeGeometry.hashCode() + Objects.hashCode(renderingGeometry))
                + 31 * outcome.hashCode()
                + mappingHashCode();
    }

    @Override
    public String toString() {
        return "ScreenGeometryOptimization[authoritativeGeometry="
                + authoritativeGeometry
                + ", renderingGeometry="
                + renderingGeometry
                + ", outcome="
                + outcome
                + ", componentOffsets="
                + Arrays.toString(renderComponentOffsets())
                + "]";
    }

    private boolean mappingEquals(ScreenGeometryOptimization other) {
        if (componentOffsets != null && other.componentOffsets != null) {
            return Arrays.equals(componentOffsets, other.componentOffsets);
        }
        if (renderComponentCount() != other.renderComponentCount()) {
            return false;
        }
        for (int index = 0; index <= sourceComponentCount(); index++) {
            if (renderComponentOffset(index) != other.renderComponentOffset(index)) {
                return false;
            }
        }
        return true;
    }

    private int mappingHashCode() {
        if (componentOffsets != null) {
            return Arrays.hashCode(componentOffsets);
        }
        int result = 1;
        for (int index = 0; index <= implicitIdentityComponentCount; index++) {
            result = 31 * result + index;
        }
        return result;
    }
}
