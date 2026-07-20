package io.github.mundanej.map.workspace;

/**
 * Exact names resolved from one application-owned immutable symbol catalog.
 *
 * @param catalogId exact catalog key
 * @param markerName marker symbol name
 * @param lineName line symbol name
 * @param fillName fill symbol name
 */
public record WorkspaceSymbolReferences(
        String catalogId, String markerName, String lineName, String fillName) {
    /** Validates bounded exact catalog and symbol names. */
    public WorkspaceSymbolReferences {
        catalogId = WorkspaceText.openerId(catalogId);
        markerName = WorkspaceText.symbolName(markerName, "markerName");
        lineName = WorkspaceText.symbolName(lineName, "lineName");
        fillName = WorkspaceText.symbolName(fillName, "fillName");
    }
}
