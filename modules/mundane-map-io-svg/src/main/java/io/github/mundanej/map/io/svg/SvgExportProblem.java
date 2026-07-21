package io.github.mundanej.map.io.svg;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A stable structured problem produced by canonical SVG map export.
 *
 * @param code non-blank stable ASCII code
 * @param context insertion-ordered immutable bounded context
 */
public record SvgExportProblem(String code, Map<String, String> context) {
    /** Creates a problem with a stable ASCII code and insertion-ordered context. */
    public SvgExportProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(context, "context");
        if (code.isBlank() || !code.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("code must be non-blank stable ASCII");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        context.forEach(
                (key, value) ->
                        copy.put(
                                Objects.requireNonNull(key, "context key"),
                                Objects.requireNonNull(value, "context value")));
        context = Collections.unmodifiableMap(copy);
    }
}
