package io.github.mundanej.map.api;

/** Listener for ordered source-report transitions. */
@FunctionalInterface
public interface MapSourceReportListener {
    /** Observes a source report transition. */
    void onMapSourceReportChanged(MapSourceReportEvent event);
}
