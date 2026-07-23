package io.github.mundanej.map.io.se;

import java.util.LinkedHashMap;
import java.util.Map;

final class SeFailures {
    private SeFailures() {}

    static SeReadException failure(String source, String code, String path, String reason) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        if (path != null && !path.isEmpty()) {
            context.put("path", bounded(path));
        }
        if (reason != null && !reason.isEmpty()) {
            context.put("reason", bounded(reason));
        }
        return new SeReadException(new SeReadProblem(code, source, context));
    }

    static SeReadException limit(
            String source, String limit, long observed, long maximum, String path) {
        return limit("SE_LIMIT_EXCEEDED", source, limit, observed, maximum, path);
    }

    static SeReadException inputLimit(String source, long observed, long maximum) {
        return limit("SE_INPUT_LIMIT", source, "inputBytes", observed, maximum, "/");
    }

    private static SeReadException limit(
            String code, String source, String limit, long observed, long maximum, String path) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("limit", limit);
        context.put("maximum", Long.toString(maximum));
        context.put("observed", Long.toString(observed));
        if (path != null && !path.isEmpty()) {
            context.put("path", bounded(path));
        }
        return new SeReadException(new SeReadProblem(code, source, context));
    }

    static SeReadException io(String source, String operation, String reason) {
        return new SeReadException(
                new SeReadProblem(
                        "SE_IO", source, Map.of("operation", operation, "reason", reason)));
    }

    static SeReadException cancelled(String source) {
        return new SeReadException(
                new SeReadProblem("SE_CANCELLED", source, Map.of("operation", "read")));
    }

    private static String bounded(String value) {
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
}
