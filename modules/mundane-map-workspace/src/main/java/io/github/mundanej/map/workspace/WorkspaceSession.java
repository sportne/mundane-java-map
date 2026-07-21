package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.CrsDefinition;
import java.util.List;
import java.util.Objects;

/** All-or-nothing owner of every source opened for one immutable workspace document. */
public final class WorkspaceSession implements AutoCloseable {
    private final WorkspaceDocument document;
    private final CrsDefinition mapCrs;
    private final CrsDefinition displayCrs;
    private final List<OpenedWorkspaceLayer> layers;
    private volatile boolean closed;

    WorkspaceSession(
            WorkspaceDocument document,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            List<OpenedWorkspaceLayer> layers) {
        this.document = Objects.requireNonNull(document, "document");
        this.mapCrs = Objects.requireNonNull(mapCrs, "mapCrs");
        this.displayCrs = Objects.requireNonNull(displayCrs, "displayCrs");
        this.layers = List.copyOf(layers);
    }

    /**
     * Returns the immutable persisted document.
     *
     * @return persisted document
     */
    public WorkspaceDocument document() {
        return document;
    }

    /**
     * Returns the exact resolved map-coordinate CRS.
     *
     * @return resolved map CRS
     */
    public CrsDefinition mapCrs() {
        return mapCrs;
    }

    /**
     * Returns the exact resolved display CRS.
     *
     * @return resolved display CRS
     */
    public CrsDefinition displayCrs() {
        return displayCrs;
    }

    /**
     * Returns immutable opened layers in paint order.
     *
     * @return immutable session-owned layers
     */
    public List<OpenedWorkspaceLayer> layers() {
        return layers;
    }

    /**
     * Returns whether this session has closed every source.
     *
     * @return whether close has begun
     */
    public boolean isClosed() {
        return closed;
    }

    /** Closes every owned source exactly once in reverse layer order. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        Throwable primary = null;
        for (int index = layers.size() - 1; index >= 0; index--) {
            try {
                WorkspaceOpener.close(layers.get(index));
            } catch (RuntimeException | Error failure) {
                if (primary == null) {
                    primary = failure;
                } else {
                    WorkspaceOpener.suppressCleanup(primary, failure);
                }
            }
        }
        if (primary != null) {
            if (primary instanceof RuntimeException failure) {
                throw failure;
            }
            throw (Error) primary;
        }
    }
}
