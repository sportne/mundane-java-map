package io.github.mundanej.map.io.geotiff;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Package-private file boundary used to verify snapshot transaction outcomes. */
interface GeoTiffFileAccess {
    /** Production file access with no discovery or mutable registration. */
    GeoTiffFileAccess SYSTEM =
            new GeoTiffFileAccess() {
                @Override
                public long size(Path path) throws IOException {
                    return Files.size(path);
                }

                @Override
                public InputStream open(Path path) throws IOException {
                    return Files.newInputStream(path);
                }
            };

    long size(Path path) throws IOException;

    InputStream open(Path path) throws IOException;
}
