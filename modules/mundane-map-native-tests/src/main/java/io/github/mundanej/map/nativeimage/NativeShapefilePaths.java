package io.github.mundanej.map.nativeimage;

import java.nio.file.Path;
import java.util.Objects;

/** Immutable paths materialized from the fixed native Shapefile inventory. */
record NativeShapefilePaths(Path shp, Path shx, Path dbf, Path cpg, Path prj, Path malformed) {
    NativeShapefilePaths {
        Objects.requireNonNull(shp, "shp");
        Objects.requireNonNull(shx, "shx");
        Objects.requireNonNull(dbf, "dbf");
        Objects.requireNonNull(cpg, "cpg");
        Objects.requireNonNull(prj, "prj");
        Objects.requireNonNull(malformed, "malformed");
    }
}
