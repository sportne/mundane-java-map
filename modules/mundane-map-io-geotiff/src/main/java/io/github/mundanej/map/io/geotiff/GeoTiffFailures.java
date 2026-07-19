package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GeoTiffFailures {
    private GeoTiffFailures() {}

    static SourceException failure(
            String sourceId, String code, String message, Map<String, String> context) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        message,
                        context);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    static void checkpoint(String sourceId, CancellationToken cancellation, String operation) {
        if (cancellation.isCancellationRequested()) {
            throw failure(
                    sourceId,
                    "SOURCE_CANCELLED",
                    "GeoTIFF operation was cancelled",
                    Map.of("operation", operation));
        }
    }

    static void limit(String sourceId, String scope, String limit, long requested, long maximum) {
        if (requested > maximum) {
            throw failure(
                    sourceId,
                    "SOURCE_LIMIT_EXCEEDED",
                    "GeoTIFF resource limit exceeded",
                    Map.of(
                            "scope",
                            scope,
                            "limit",
                            limit,
                            "requested",
                            Long.toString(requested),
                            "maximum",
                            Long.toString(maximum)));
        }
    }

    static SourceException header(String sourceId, String field, String reason) {
        return failure(
                sourceId,
                "GEOTIFF_HEADER_INVALID",
                "GeoTIFF header is invalid",
                Map.of("field", field, "reason", reason));
    }

    static SourceException tag(String sourceId, int tag, String reason) {
        return failure(
                sourceId,
                "GEOTIFF_TAG_INVALID",
                "GeoTIFF tag is invalid",
                Map.of("tag", Integer.toString(tag), "reason", reason));
    }

    static SourceException unsupported(String sourceId, String construct) {
        return failure(
                sourceId,
                "GEOTIFF_PROFILE_UNSUPPORTED",
                "GeoTIFF construct is outside the supported profile",
                Map.of("construct", construct));
    }

    static SourceException unsupportedTag(String sourceId, int tag, String construct) {
        return failure(
                sourceId,
                "GEOTIFF_PROFILE_UNSUPPORTED",
                "GeoTIFF tag is outside the supported profile",
                Map.of("construct", construct, "tag", Integer.toString(tag)));
    }

    static SourceException unsupportedKey(String sourceId, int key, String construct) {
        return failure(
                sourceId,
                "GEOTIFF_PROFILE_UNSUPPORTED",
                "GeoTIFF key is outside the supported profile",
                Map.of("construct", construct, "key", Integer.toString(key)));
    }

    static SourceException unsupportedCompression(String sourceId, int compression) {
        return failure(
                sourceId,
                "GEOTIFF_PROFILE_UNSUPPORTED",
                "GeoTIFF compression is outside the supported profile",
                Map.of("construct", "compression", "compression", Integer.toString(compression)));
    }

    static SourceException geokey(String sourceId, Integer key, String reason) {
        Map<String, String> context =
                key == null
                        ? Map.of("reason", reason)
                        : Map.of("key", Integer.toString(key), "reason", reason);
        return failure(
                sourceId, "GEOTIFF_GEOKEY_INVALID", "GeoTIFF key directory is invalid", context);
    }

    static SourceException segment(String sourceId, int segment, String reason) {
        return failure(
                sourceId,
                "GEOTIFF_SEGMENT_INVALID",
                "GeoTIFF strip is invalid",
                Map.of("segment", Integer.toString(segment), "reason", reason));
    }

    static SourceException decode(String sourceId, int segment, int compression, String reason) {
        return failure(
                sourceId,
                "GEOTIFF_DECODE_FAILED",
                "GeoTIFF segment decoding failed",
                Map.of(
                        "segment", Integer.toString(segment),
                        "compression", Integer.toString(compression),
                        "reason", reason));
    }

    static SourceException georeference(String sourceId, String reason) {
        return failure(
                sourceId,
                "GEOTIFF_GEOREFERENCE_INVALID",
                "GeoTIFF georeference is invalid",
                Map.of("reason", reason));
    }

    static SourceException sample(String sourceId, int segment, String reason) {
        return failure(
                sourceId,
                "GEOTIFF_SAMPLE_INVALID",
                "GeoTIFF elevation sample is invalid",
                Map.of("segment", Integer.toString(segment), "reason", reason));
    }
}
