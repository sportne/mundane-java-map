package io.github.mundanej.map.workspace;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stable bounded workspace failure code and non-sensitive ordered context.
 *
 * @param code stable uppercase workspace code
 * @param context immutable ordered bounded context
 */
public record WorkspaceProblem(String code, Map<String, String> context) {
    private static final Set<String> IO_REASONS =
            Set.of("missing", "symlink", "wrongKind", "open", "size", "read", "changed", "close");
    private static final Set<String> XML_REASONS =
            Set.of("security", "declaration", "wellFormed", "content");
    private static final Set<String> STRUCTURE_REASONS =
            Set.of("order", "missing", "duplicate", "cardinality", "text");
    private static final Set<String> VALUE_FIELDS =
            Set.of(
                    "mapCrs",
                    "displayCrs",
                    "centerX",
                    "centerY",
                    "unitsPerPixel",
                    "layerId",
                    "layerName",
                    "sourceOpener",
                    "sourceId",
                    "sourceName",
                    "sourcePath",
                    "catalogId",
                    "markerName",
                    "lineName",
                    "fillName",
                    "interpolation",
                    "opacity",
                    "pathProfile");
    private static final Set<String> VALUE_REASONS =
            Set.of(
                    "grammar",
                    "duplicate",
                    "nonCanonical",
                    "definitionMismatch",
                    "range",
                    "xmlCharacter");
    private static final Set<String> LIMITS =
            Set.of(
                    "inputBytes",
                    "outputBytes",
                    "operationBytes",
                    "depth",
                    "elements",
                    "attributes",
                    "layers",
                    "valueChars",
                    "aggregateChars");

    /** Defensively copies and validates bounded diagnostic text. */
    public WorkspaceProblem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(context, "context");
        WorkspaceText.text(code, "code", 64, true);
        if (!code.startsWith("WORKSPACE_") || !code.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("code must be a stable WORKSPACE_ token");
        }
        var copy = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String key = WorkspaceText.text(entry.getKey(), "context key", 32, true);
            String value = WorkspaceText.text(entry.getValue(), "context value", 32, false);
            copy.put(key, value);
        }
        validateShape(code, copy);
        context = Collections.unmodifiableMap(copy);
    }

    /**
     * Returns the immutable insertion-ordered context.
     *
     * @return exact code-specific context
     */
    @Override
    public Map<String, String> context() {
        return context;
    }

    private static void validateShape(String code, Map<String, String> context) {
        switch (code) {
            case "WORKSPACE_IO_FAILED" -> {
                exact(context, List.of("phase", "reason"), false);
                value(context, "phase", Set.of("input"));
                value(context, "reason", IO_REASONS);
            }
            case "WORKSPACE_ENCODING_INVALID" -> {
                exact(context, List.of("reason"), false);
                value(context, "reason", Set.of("bom", "malformed", "xmlCharacter"));
            }
            case "WORKSPACE_XML_INVALID" -> {
                exact(context, List.of("reason"), false);
                value(context, "reason", XML_REASONS);
            }
            case "WORKSPACE_VERSION_UNSUPPORTED" -> {
                exact(context, List.of("reason"), false);
                value(context, "reason", Set.of("namespace", "version"));
            }
            case "WORKSPACE_FIELD_UNKNOWN" -> {
                exact(context, List.of("field"), true);
                value(context, "field", Set.of("element", "attribute", "namespace"));
            }
            case "WORKSPACE_STRUCTURE_INVALID" -> {
                exact(context, List.of("reason"), true);
                value(context, "reason", STRUCTURE_REASONS);
            }
            case "WORKSPACE_VALUE_INVALID" -> {
                exact(context, List.of("field", "reason"), true);
                value(context, "field", VALUE_FIELDS);
                value(context, "reason", VALUE_REASONS);
            }
            case "WORKSPACE_LIMIT_EXCEEDED" -> {
                exact(context, List.of("limit", "requested", "maximum"), false);
                value(context, "limit", LIMITS);
                nonNegative(context, "requested");
                nonNegative(context, "maximum");
            }
            case "WORKSPACE_PATH_INVALID" -> {
                exact(context, List.of("reason"), true, true);
                value(
                        context,
                        "reason",
                        Set.of("grammar", "suffix", "escape", "symlink", "wrongKind", "identity"));
            }
            case "WORKSPACE_RESOURCE_MISSING" -> {
                exact(context, List.of("kind"), true, true);
                value(context, "kind", Set.of("primary"));
            }
            case "WORKSPACE_SOURCE_OPENER_UNREGISTERED" -> sourceKind(context);
            case "WORKSPACE_SOURCE_KIND_MISMATCH" -> {
                exact(context, List.of("expected", "actual"), true, true);
                value(context, "expected", Set.of("FEATURE", "RASTER"));
                value(context, "actual", Set.of("FEATURE", "RASTER"));
            }
            case "WORKSPACE_SOURCE_IDENTITY_MISMATCH", "WORKSPACE_SYMBOL_CATALOG_UNREGISTERED" ->
                    exact(context, List.of(), true, true);
            case "WORKSPACE_SYMBOL_NOT_FOUND", "WORKSPACE_SYMBOL_ROLE_MISMATCH" -> {
                exact(context, List.of("role"), true, true);
                value(context, "role", Set.of("marker", "line", "fill"));
            }
            case "WORKSPACE_CRS_UNREGISTERED" -> {
                exact(context, List.of("field"), false);
                value(context, "field", Set.of("mapCrs", "displayCrs"));
            }
            case "WORKSPACE_SOURCE_OPEN_FAILED" -> sourceKind(context);
            case "WORKSPACE_CANCELLED" -> {
                value(context, "phase", Set.of("preflight", "path", "sourceOpen", "publish"));
                switch (context.get("phase")) {
                    case "preflight" -> exact(context, List.of("phase"), true);
                    case "path", "sourceOpen" -> exact(context, List.of("phase"), true, true);
                    case "publish" -> exact(context, List.of("phase"), false);
                    default ->
                            throw new IllegalArgumentException(
                                    "workspace cancellation phase is invalid");
                }
            }
            case "WORKSPACE_ATOMIC_MOVE_UNSUPPORTED" -> exact(context, List.of(), false);
            case "WORKSPACE_WRITE_FAILED" -> {
                exact(context, List.of("phase", "reason"), false);
                value(
                        context,
                        "phase",
                        Set.of(
                                "validate",
                                "encode",
                                "temporary",
                                "write",
                                "force",
                                "move",
                                "cleanup"));
                value(context, "reason", Set.of("io", "target", "changed"));
            }
            default -> throw new IllegalArgumentException("unsupported workspace problem code");
        }
    }

    private static void sourceKind(Map<String, String> context) {
        exact(context, List.of("kind"), true, true);
        value(context, "kind", Set.of("FEATURE", "RASTER"));
    }

    private static void exact(
            Map<String, String> context, List<String> required, boolean optionalLayerIndex) {
        exact(context, required, optionalLayerIndex, false);
    }

    private static void exact(
            Map<String, String> context,
            List<String> required,
            boolean optionalLayerIndex,
            boolean requiredLayerIndex) {
        List<String> keys = List.copyOf(context.keySet());
        var expected = new java.util.ArrayList<>(required);
        if (requiredLayerIndex || (optionalLayerIndex && context.containsKey("layerIndex"))) {
            expected.add("layerIndex");
        }
        if (!keys.equals(expected)) {
            throw new IllegalArgumentException("workspace problem context shape is invalid");
        }
        if (requiredLayerIndex || context.containsKey("layerIndex")) {
            nonNegative(context, "layerIndex");
        }
    }

    private static void value(Map<String, String> context, String key, Set<String> allowed) {
        if (!allowed.contains(context.get(key))) {
            throw new IllegalArgumentException("workspace problem context value is invalid");
        }
    }

    private static void nonNegative(Map<String, String> context, String key) {
        String token = context.get(key);
        try {
            long parsed = Long.parseLong(token);
            if (parsed < 0 || !Long.toString(parsed).equals(token)) {
                throw new IllegalArgumentException("workspace problem count is not canonical");
            }
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("workspace problem count is not canonical", failure);
        }
    }
}
