package io.github.mundanej.map.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable ordered application-owned feature snapshot.
 *
 * @param revision non-negative monotonically increasing session revision
 * @param crs exact coordinate reference system of every record
 * @param records ordered immutable feature records
 */
public record FeatureEditSnapshot(long revision, CrsDefinition crs, List<FeatureRecord> records) {
    /** Validates identity uniqueness and defensively copies the records. */
    public FeatureEditSnapshot {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        Objects.requireNonNull(crs, "crs");
        records = List.copyOf(Objects.requireNonNull(records, "records"));
        Set<String> identities = new HashSet<>();
        for (FeatureRecord record : records) {
            Objects.requireNonNull(record, "record");
            if (!identities.add(record.id())) {
                throw new IllegalArgumentException("feature ids must be unique");
            }
        }
    }
}
