package io.github.mundanej.map.io.geopackage;

import java.util.List;
import java.util.Objects;

record GeoPackageTableProfile(
        GeoPackageFeatureTable table, List<GeoPackageAttributeColumn> attributes) {
    GeoPackageTableProfile {
        Objects.requireNonNull(table, "table");
        attributes = List.copyOf(attributes);
    }
}
