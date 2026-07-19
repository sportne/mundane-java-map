package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

final class GeoTiffSnapshots {
    private GeoTiffSnapshots() {}

    static byte[] read(
            SourceIdentity identity,
            Path path,
            GeoTiffLimits limits,
            CancellationToken cancellation) {
        return read(identity, path, limits, cancellation, GeoTiffFileAccess.SYSTEM);
    }

    static byte[] read(
            SourceIdentity identity,
            Path path,
            GeoTiffLimits limits,
            CancellationToken cancellation,
            GeoTiffFileAccess access) {
        long maximum = limits.maximumInputBytes();
        long statedSize;
        try {
            statedSize = access.size(path);
        } catch (IOException failure) {
            throw io(identity.id(), "size", failure);
        }
        GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
        GeoTiffFailures.limit(identity.id(), "geoTiffOpen", "inputBytes", statedSize, maximum);
        if (statedSize > Integer.MAX_VALUE) {
            GeoTiffFailures.limit(
                    identity.id(), "geoTiffOpen", "inputBytes", statedSize, Integer.MAX_VALUE);
        }
        byte[] snapshot = new byte[Math.toIntExact(statedSize)];
        GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
        InputStream input;
        try {
            input = access.open(path);
        } catch (IOException failure) {
            throw io(identity.id(), "open", failure);
        }
        try {
            GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
            fill(identity.id(), input, snapshot, maximum, cancellation);
        } catch (RuntimeException | Error failure) {
            closeSuppressed(identity.id(), input, failure);
            throw failure;
        }
        close(identity.id(), input);
        GeoTiffFailures.checkpoint(identity.id(), cancellation, "geoTiffOpen");
        return snapshot;
    }

    private static void fill(
            String sourceId,
            InputStream input,
            byte[] snapshot,
            long maximum,
            CancellationToken cancellation) {
        int count = 0;
        while (count < snapshot.length) {
            GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            int read;
            try {
                read = input.read(snapshot, count, snapshot.length - count);
            } catch (IOException failure) {
                throw io(sourceId, "read", failure);
            }
            GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
            if (read < 0) {
                throw changed(sourceId);
            }
            if (read == 0) {
                throw ioOther(sourceId, "read");
            }
            count += read;
        }
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        int extra;
        try {
            extra = input.read();
        } catch (IOException failure) {
            throw io(sourceId, "read", failure);
        }
        GeoTiffFailures.checkpoint(sourceId, cancellation, "geoTiffOpen");
        if (extra >= 0) {
            if (snapshot.length == maximum) {
                GeoTiffFailures.limit(sourceId, "geoTiffOpen", "inputBytes", maximum + 1, maximum);
            }
            throw changed(sourceId);
        }
    }

    private static SourceException changed(String sourceId) {
        return GeoTiffFailures.failure(
                sourceId,
                "GEOTIFF_IO_FAILED",
                "GeoTIFF changed during snapshot",
                Map.of("operation", "read", "reason", "changed"));
    }

    private static void close(String sourceId, InputStream input) {
        try {
            input.close();
        } catch (IOException failure) {
            throw io(sourceId, "close", failure);
        }
    }

    private static void closeSuppressed(String sourceId, InputStream input, Throwable primary) {
        try {
            input.close();
        } catch (IOException failure) {
            primary.addSuppressed(io(sourceId, "close", failure));
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

    private static RuntimeException ioOther(String sourceId, String operation) {
        return GeoTiffFailures.failure(
                sourceId,
                "GEOTIFF_IO_FAILED",
                "GeoTIFF snapshot failed",
                Map.of("operation", operation, "reason", "other"));
    }
}
