package io.github.mundanej.map.workspace;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class WorkspaceFailures {
    private WorkspaceFailures() {}

    static WorkspaceException io(String reason, Throwable cause) {
        return failure("WORKSPACE_IO_FAILED", ordered("phase", "input", "reason", reason), cause);
    }

    static WorkspaceException encoding(String reason, Throwable cause) {
        return failure("WORKSPACE_ENCODING_INVALID", ordered("reason", reason), cause);
    }

    static WorkspaceException xml(String reason, Throwable cause) {
        return failure("WORKSPACE_XML_INVALID", ordered("reason", reason), cause);
    }

    static WorkspaceException version(String reason) {
        return failure("WORKSPACE_VERSION_UNSUPPORTED", ordered("reason", reason), null);
    }

    static WorkspaceException unknown(String field, Integer layerIndex) {
        return failure(
                "WORKSPACE_FIELD_UNKNOWN", indexed(ordered("field", field), layerIndex), null);
    }

    static WorkspaceException structure(String reason, Integer layerIndex) {
        return failure(
                "WORKSPACE_STRUCTURE_INVALID",
                indexed(ordered("reason", reason), layerIndex),
                null);
    }

    static WorkspaceException value(String field, String reason, Integer layerIndex) {
        return failure(
                "WORKSPACE_VALUE_INVALID",
                indexed(ordered("field", field, "reason", reason), layerIndex),
                null);
    }

    static WorkspaceException limit(String limit, long requested, long maximum) {
        return failure(
                "WORKSPACE_LIMIT_EXCEEDED",
                ordered(
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)),
                null);
    }

    static WorkspaceException write(String phase, String reason, Throwable cause) {
        return failure("WORKSPACE_WRITE_FAILED", ordered("phase", phase, "reason", reason), cause);
    }

    static WorkspaceException atomicMove(Throwable cause) {
        return failure("WORKSPACE_ATOMIC_MOVE_UNSUPPORTED", Map.of(), cause);
    }

    static WorkspaceException path(String reason, int layerIndex) {
        return failure(
                "WORKSPACE_PATH_INVALID", indexed(ordered("reason", reason), layerIndex), null);
    }

    static WorkspaceException resourceMissing(int layerIndex) {
        return failure(
                "WORKSPACE_RESOURCE_MISSING",
                indexed(ordered("kind", "primary"), layerIndex),
                null);
    }

    static WorkspaceException openerUnregistered(WorkspaceSourceKind kind, int layerIndex) {
        return failure(
                "WORKSPACE_SOURCE_OPENER_UNREGISTERED",
                indexed(ordered("kind", kind.name()), layerIndex),
                null);
    }

    static WorkspaceException kindMismatch(
            WorkspaceSourceKind expected, WorkspaceSourceKind actual, int layerIndex) {
        return failure(
                "WORKSPACE_SOURCE_KIND_MISMATCH",
                indexed(ordered("expected", expected.name(), "actual", actual.name()), layerIndex),
                null);
    }

    static WorkspaceException identityMismatch(int layerIndex) {
        return failure(
                "WORKSPACE_SOURCE_IDENTITY_MISMATCH",
                indexed(new LinkedHashMap<>(), layerIndex),
                null);
    }

    static WorkspaceException catalogUnregistered(int layerIndex) {
        return failure(
                "WORKSPACE_SYMBOL_CATALOG_UNREGISTERED",
                indexed(new LinkedHashMap<>(), layerIndex),
                null);
    }

    static WorkspaceException symbolMissing(String role, int layerIndex) {
        return failure(
                "WORKSPACE_SYMBOL_NOT_FOUND", indexed(ordered("role", role), layerIndex), null);
    }

    static WorkspaceException symbolRole(String role, int layerIndex) {
        return failure(
                "WORKSPACE_SYMBOL_ROLE_MISMATCH", indexed(ordered("role", role), layerIndex), null);
    }

    static WorkspaceException crsUnregistered(String field) {
        return failure("WORKSPACE_CRS_UNREGISTERED", ordered("field", field), null);
    }

    static WorkspaceException sourceOpen(
            WorkspaceSourceKind kind,
            int layerIndex,
            io.github.mundanej.map.api.DiagnosticReport report,
            Throwable cause) {
        return failure(
                "WORKSPACE_SOURCE_OPEN_FAILED",
                indexed(ordered("kind", kind.name()), layerIndex),
                Optional.of(report),
                cause);
    }

    static WorkspaceException cancelled(String phase, Integer layerIndex, Throwable cause) {
        return failure("WORKSPACE_CANCELLED", indexed(ordered("phase", phase), layerIndex), cause);
    }

    static WorkspaceException cancelled(
            String phase,
            Integer layerIndex,
            io.github.mundanej.map.api.DiagnosticReport report,
            Throwable cause) {
        return failure(
                "WORKSPACE_CANCELLED",
                indexed(ordered("phase", phase), layerIndex),
                Optional.of(report),
                cause);
    }

    private static WorkspaceException failure(
            String code, Map<String, String> context, Throwable cause) {
        return failure(code, context, Optional.empty(), cause);
    }

    private static WorkspaceException failure(
            String code,
            Map<String, String> context,
            Optional<io.github.mundanej.map.api.DiagnosticReport> sourceReport,
            Throwable cause) {
        return new WorkspaceException(new WorkspaceProblem(code, context), sourceReport, cause);
    }

    private static Map<String, String> indexed(Map<String, String> initial, Integer layerIndex) {
        if (layerIndex != null) {
            initial.put("layerIndex", Integer.toString(layerIndex));
        }
        return initial;
    }

    private static Map<String, String> ordered(String... pairs) {
        var result = new LinkedHashMap<String, String>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put(pairs[index], pairs[index + 1]);
        }
        return result;
    }
}
