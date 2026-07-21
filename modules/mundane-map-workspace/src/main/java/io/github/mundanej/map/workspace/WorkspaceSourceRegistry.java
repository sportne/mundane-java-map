package io.github.mundanej.map.workspace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Instance-owned immutable registry of explicit versioned local source openers. */
public final class WorkspaceSourceRegistry {
    private final Map<String, Registration> registrations;

    private WorkspaceSourceRegistry(Map<String, Registration> registrations) {
        this.registrations = Map.copyOf(registrations);
    }

    /**
     * Returns a new single-use explicit registry builder.
     *
     * @return empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    Registration find(String id) {
        return registrations.get(id);
    }

    sealed interface Registration permits FeatureRegistration, RasterRegistration {
        WorkspaceSourceKind kind();

        WorkspaceLocalPathProfile profile();
    }

    record FeatureRegistration(
            WorkspaceLocalPathProfile profile, WorkspaceFeatureSourceOpener opener)
            implements Registration {
        @Override
        public WorkspaceSourceKind kind() {
            return WorkspaceSourceKind.FEATURE;
        }
    }

    record RasterRegistration(WorkspaceLocalPathProfile profile, WorkspaceRasterSourceOpener opener)
            implements Registration {
        @Override
        public WorkspaceSourceKind kind() {
            return WorkspaceSourceKind.RASTER;
        }
    }

    /** Single-use source-opener registry builder. */
    public static final class Builder {
        private final Map<String, Registration> registrations = new LinkedHashMap<>();
        private boolean consumed;

        private Builder() {}

        /**
         * Registers one exact feature opener.
         *
         * @param id exact dotted lowercase versioned opener ID
         * @param profile closed guarded local path profile
         * @param opener trusted application opener
         * @return this builder
         */
        public Builder registerFeature(
                String id, WorkspaceLocalPathProfile profile, WorkspaceFeatureSourceOpener opener) {
            register(
                    id,
                    new FeatureRegistration(
                            Objects.requireNonNull(profile, "profile"),
                            Objects.requireNonNull(opener, "opener")));
            return this;
        }

        /**
         * Registers one exact raster opener.
         *
         * @param id exact dotted lowercase versioned opener ID
         * @param profile closed guarded local path profile
         * @param opener trusted application opener
         * @return this builder
         */
        public Builder registerRaster(
                String id, WorkspaceLocalPathProfile profile, WorkspaceRasterSourceOpener opener) {
            register(
                    id,
                    new RasterRegistration(
                            Objects.requireNonNull(profile, "profile"),
                            Objects.requireNonNull(opener, "opener")));
            return this;
        }

        /**
         * Builds the immutable registry and consumes this builder.
         *
         * @return immutable registry
         */
        public WorkspaceSourceRegistry build() {
            requireUsable();
            consumed = true;
            return new WorkspaceSourceRegistry(registrations);
        }

        private void register(String id, Registration registration) {
            requireUsable();
            String checked = WorkspaceText.openerId(id);
            if (registrations.putIfAbsent(checked, registration) != null) {
                throw new IllegalArgumentException("source opener ID is already registered");
            }
        }

        private void requireUsable() {
            if (consumed) {
                throw new IllegalStateException("source registry builder is already consumed");
            }
        }
    }
}
