package io.github.mundanej.map.example.workspace;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.RasterRenderOptions;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.ImagePlacement;
import io.github.mundanej.map.io.image.RasterImages;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer;
import io.github.mundanej.map.workspace.OpenedWorkspaceLayer;
import io.github.mundanej.map.workspace.OpenedWorkspaceRasterLayer;
import io.github.mundanej.map.workspace.WorkspaceException;
import io.github.mundanej.map.workspace.WorkspaceFeatureLayer;
import io.github.mundanej.map.workspace.WorkspaceFile;
import io.github.mundanej.map.workspace.WorkspaceFiles;
import io.github.mundanej.map.workspace.WorkspaceLimits;
import io.github.mundanej.map.workspace.WorkspaceLocalPathBranch;
import io.github.mundanej.map.workspace.WorkspaceLocalPathProfile;
import io.github.mundanej.map.workspace.WorkspaceOpenContext;
import io.github.mundanej.map.workspace.WorkspaceOpener;
import io.github.mundanej.map.workspace.WorkspaceRasterLayer;
import io.github.mundanej.map.workspace.WorkspaceSession;
import io.github.mundanej.map.workspace.WorkspaceSourceRegistry;
import io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/** Runnable explicit restore of one bounded local shapefile/raster workspace. */
public final class WorkspaceViewer {
    static final int VIEW_WIDTH = 900;
    static final int VIEW_HEIGHT = 600;
    static final Path DEFAULT_WORKSPACE =
            Path.of("examples/workspace-viewer/build/workspace-fixture/example.mmap.xml");
    private static final String SHAPEFILE_OPENER = "application.shapefile.v1";
    private static final String IMAGE_OPENER = "application.image-world-file-epsg3857.v1";
    private static final String CATALOG_ID = "application.default";

    private WorkspaceViewer() {}

    /**
     * Opens the optional caller-selected workspace and displays its restored local layers.
     *
     * @param arguments zero arguments for the checked fixture or one explicit {@code .mmap.xml}
     *     path
     */
    public static void main(String[] arguments) {
        runMain(arguments, System.err::println, WorkspaceViewer::installWindow);
    }

    static boolean runMain(
            String[] arguments, Consumer<String> failureSink, Consumer<ViewerSession> presenter) {
        Objects.requireNonNull(failureSink, "failureSink");
        Objects.requireNonNull(presenter, "presenter");
        try {
            Path workspace = parseArguments(arguments);
            ViewerSession viewer = openView(workspace, VIEW_WIDTH, VIEW_HEIGHT);
            try {
                onEdt(
                        () -> {
                            presenter.accept(viewer);
                            return null;
                        });
            } catch (RuntimeException | Error failure) {
                closeSuppressing(viewer, failure);
                throw failure;
            }
            return true;
        } catch (RuntimeException failure) {
            failureSink.accept(summary(failure));
            return false;
        }
    }

    static Path parseArguments(String[] arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (arguments.length == 0) {
            return DEFAULT_WORKSPACE;
        }
        if (arguments.length == 1) {
            return Path.of(Objects.requireNonNull(arguments[0], "arguments[0]"));
        }
        throw new IllegalArgumentException("Usage: workspace-viewer [workspace.mmap.xml]");
    }

    static WorkspaceSession open(Path workspace) {
        Objects.requireNonNull(workspace, "workspace");
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Workspace opening must run off the event dispatch thread");
        }
        WorkspaceFile file = WorkspaceFiles.read(workspace, WorkspaceLimits.DEFAULT);
        return WorkspaceOpener.open(file, openContext(), CancellationToken.none());
    }

    static ViewerSession openView(Path workspace, int width, int height) {
        WorkspaceSession session = open(workspace);
        try {
            return onEdt(() -> restore(session, width, height));
        } catch (RuntimeException | Error failure) {
            closeSuppressing(session, failure);
            throw failure;
        }
    }

    static WorkspaceOpenContext openContext() {
        WorkspaceLocalPathProfile shapefile =
                new WorkspaceLocalPathProfile(
                        List.of(
                                new WorkspaceLocalPathBranch(
                                        ".shp",
                                        List.of(
                                                ".shx", ".SHX", ".dbf", ".DBF", ".cpg", ".CPG",
                                                ".prj", ".PRJ"))));
        WorkspaceLocalPathProfile image =
                new WorkspaceLocalPathProfile(
                        List.of(
                                imageBranch(".png", ".pngw", ".pgw"),
                                imageBranch(".jpg", ".jpgw", ".jgw"),
                                imageBranch(".jpeg", ".jpegw", ".jgw")));
        WorkspaceSourceRegistry sources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                SHAPEFILE_OPENER,
                                shapefile,
                                (identity, path, cancellation) ->
                                        Shapefiles.open(
                                                identity,
                                                path,
                                                ShapefileOpenOptions.defaults(),
                                                cancellation))
                        .registerRaster(
                                IMAGE_OPENER,
                                image,
                                (identity, path, cancellation) ->
                                        RasterImages.open(
                                                path,
                                                identity,
                                                imageOptions(),
                                                AwtRasterDecoders.level1(),
                                                cancellation))
                        .build();
        WorkspaceSymbolCatalogRegistry catalogs =
                WorkspaceSymbolCatalogRegistry.builder().register(CATALOG_ID, catalog()).build();
        return new WorkspaceOpenContext(CrsRegistry.level1(), sources, catalogs);
    }

    static ViewerSession restore(WorkspaceSession session, int width, int height) {
        Objects.requireNonNull(session, "session");
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException(
                    "Workspace view restoration must run on the event dispatch thread");
        }
        MapView view = null;
        List<MapLayerBinding> bindings = new ArrayList<>(session.layers().size());
        try {
            view = new MapView(CrsRegistry.level1(), session.mapCrs(), session.displayCrs());
            view.setSize(width, height);
            for (OpenedWorkspaceLayer layer : session.layers()) {
                bindings.add(binding(layer));
            }
            view.setLayerBindings(bindings);
            var persisted = session.document().view();
            view.setViewport(
                    new MapViewport(
                            width,
                            height,
                            persisted.centerX(),
                            persisted.centerY(),
                            persisted.unitsPerPixel()));
            return new ViewerSession(view, session);
        } catch (RuntimeException | Error failure) {
            if (view != null) {
                closeSuppressing(view, failure);
            }
            closeUnattachedReverse(bindings, failure);
            closeSuppressing(session, failure);
            throw failure;
        }
    }

    private static MapLayerBinding binding(OpenedWorkspaceLayer layer) {
        if (layer instanceof OpenedWorkspaceFeatureLayer feature) {
            WorkspaceFeatureLayer definition = feature.definition();
            return MapLayerBinding.borrowedFeature(
                    definition.id(),
                    definition.name(),
                    feature.source(),
                    FeaturePortrayal.fixed(feature.marker(), feature.line(), feature.fill()));
        }
        OpenedWorkspaceRasterLayer raster = (OpenedWorkspaceRasterLayer) layer;
        WorkspaceRasterLayer definition = raster.definition();
        return MapLayerBinding.borrowedRaster(
                definition.id(),
                definition.name(),
                raster.source(),
                new RasterRenderOptions(definition.interpolation(), definition.opacity()));
    }

    private static WorkspaceLocalPathBranch imageBranch(
            String primary, String longSuffix, String shortSuffix) {
        return new WorkspaceLocalPathBranch(
                primary,
                List.of(
                        longSuffix,
                        longSuffix.toUpperCase(java.util.Locale.ROOT),
                        shortSuffix,
                        shortSuffix.toUpperCase(java.util.Locale.ROOT),
                        ".wld",
                        ".WLD"));
    }

    private static ImageOpenOptions imageOptions() {
        CrsMetadata crs =
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty());
        return ImageOpenOptions.defaults().withPlacement(ImagePlacement.worldFile(crs));
    }

    private static NamedSymbolCatalog catalog() {
        SolidLineSymbol boundary =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(25, 70, 135),
                                new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                        1.0);
        return NamedSymbolCatalog.of(
                List.of(
                        new NamedSymbol(
                                "point",
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.DIAMOND, Rgba.rgb(220, 70, 35), 12.0, 1.0)),
                        new NamedSymbol("boundary", boundary),
                        new NamedSymbol(
                                "area",
                                SolidFillSymbol.of(
                                        new Rgba(35, 125, 205, 90), Optional.of(boundary), 1.0))));
    }

    private static void installWindow(ViewerSession viewer) {
        JFrame frame = new JFrame("mundane-java-map — workspace viewer");
        try {
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.add(viewer.view(), BorderLayout.CENTER);
            frame.addWindowListener(
                    new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent event) {
                            try {
                                viewer.close();
                            } finally {
                                frame.dispose();
                            }
                        }
                    });
            frame.setSize(VIEW_WIDTH, VIEW_HEIGHT);
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        } catch (RuntimeException | Error failure) {
            frame.dispose();
            throw failure;
        }
    }

    private static String summary(RuntimeException failure) {
        if (failure instanceof WorkspaceException workspace) {
            return "workspace-viewer: " + workspace.problem().code();
        }
        if (failure instanceof SourceException source) {
            return "workspace-viewer: " + source.terminal().code();
        }
        if (failure instanceof CrsException crs) {
            return "workspace-viewer: " + crs.problem().code();
        }
        if (failure instanceof IllegalArgumentException) {
            return "workspace-viewer: WORKSPACE_VIEWER_ARGUMENT_INVALID";
        }
        return "workspace-viewer: WORKSPACE_VIEWER_STARTUP_FAILED";
    }

    private static <T> T onEdt(Callable<T> work) {
        if (EventQueue.isDispatchThread()) {
            try {
                return work.call();
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("Workspace viewer EDT operation failed", failure);
            }
        }
        FutureTask<T> task = new FutureTask<>(work);
        EventQueue.invokeLater(task);
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return task.get();
                } catch (InterruptedException failure) {
                    interrupted = true;
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException("Workspace viewer EDT operation failed", cause);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void closeUnattachedReverse(List<MapLayerBinding> bindings, Throwable primary) {
        for (int index = bindings.size() - 1; index >= 0; index--) {
            closeSuppressing(bindings.get(index), primary);
        }
    }

    private static void closeSuppressing(AutoCloseable closeable, Throwable primary) {
        try {
            closeable.close();
        } catch (RuntimeException | Error failure) {
            if (failure != primary) {
                primary.addSuppressed(failure);
            }
        } catch (Exception failure) {
            primary.addSuppressed(failure);
        }
    }

    static final class ViewerSession implements AutoCloseable {
        private final MapView view;
        private final WorkspaceSession workspace;
        private boolean closed;

        private ViewerSession(MapView view, WorkspaceSession workspace) {
            this.view = Objects.requireNonNull(view, "view");
            this.workspace = Objects.requireNonNull(workspace, "workspace");
        }

        MapView view() {
            return view;
        }

        WorkspaceSession workspace() {
            return workspace;
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            Throwable primary = null;
            try {
                view.close();
            } catch (RuntimeException | Error failure) {
                primary = failure;
            }
            if (primary == null) {
                workspace.close();
                return;
            }
            closeSuppressing(workspace, primary);
            if (primary instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (Error) primary;
        }
    }
}
