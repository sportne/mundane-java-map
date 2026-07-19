package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

final class GeoTiffSnapshots {
    private GeoTiffSnapshots() {}

    static byte[] read(
            SourceIdentity identity,
            Path path,
            GeoTiffRasterOptions options,
            CancellationToken cancellation) {
        long maximum = options.formatLimits().maximumInputBytes();
        long statedSize;
        try {
            statedSize = Files.size(path);
        } catch (IOException failure) {
            throw io(identity.id(), "size", failure);
        }
        GeoTiffFailures.limit(identity.id(), "geoTiffOpen", "inputBytes", statedSize, maximum);
        if (statedSize > Integer.MAX_VALUE) {
            GeoTiffFailures.limit(
                    identity.id(), "geoTiffOpen", "inputBytes", statedSize, Integer.MAX_VALUE);
        }
        try (InputStream input = Files.newInputStream(path);
                ByteArrayOutputStream output = new ByteArrayOutputStream((int) statedSize)) {
            byte[] buffer = new byte[8192];
            long count = 0;
            while (count < maximum) {
                GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
                int request = (int) Math.min(buffer.length, maximum - count);
                int read = input.read(buffer, 0, request);
                if (read < 0) {
                    break;
                }
                output.write(buffer, 0, read);
                count += read;
            }
            if (count == maximum && input.read() >= 0) {
                GeoTiffFailures.limit(
                        identity.id(), "geoTiffOpen", "inputBytes", maximum + 1, maximum);
            }
            if (count != statedSize) {
                throw GeoTiffFailures.failure(
                        identity.id(),
                        "GEOTIFF_IO_FAILED",
                        "GeoTIFF changed during snapshot",
                        Map.of("operation", "read", "reason", "changed"));
            }
            return output.toByteArray();
        } catch (IOException failure) {
            throw io(identity.id(), "read", failure);
        }
    }

    private static RuntimeException io(String sourceId, String operation, IOException failure) {
        String reason =
                failure instanceof NoSuchFileException
                        ? "notFound"
                        : failure instanceof AccessDeniedException ? "accessDenied" : "other";
        return GeoTiffFailures.failure(
                sourceId,
                "GEOTIFF_IO_FAILED",
                "GeoTIFF snapshot failed",
                Map.of("operation", operation, "reason", reason));
    }
}
