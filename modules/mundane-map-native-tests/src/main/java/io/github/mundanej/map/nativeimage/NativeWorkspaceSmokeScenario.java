package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer;
import io.github.mundanej.map.workspace.WorkspaceDocument;
import io.github.mundanej.map.workspace.WorkspaceException;
import io.github.mundanej.map.workspace.WorkspaceFeatureLayer;
import io.github.mundanej.map.workspace.WorkspaceFile;
import io.github.mundanej.map.workspace.WorkspaceFiles;
import io.github.mundanej.map.workspace.WorkspaceLimits;
import io.github.mundanej.map.workspace.WorkspaceLocalPathBranch;
import io.github.mundanej.map.workspace.WorkspaceLocalPathProfile;
import io.github.mundanej.map.workspace.WorkspaceOpenContext;
import io.github.mundanej.map.workspace.WorkspaceOpener;
import io.github.mundanej.map.workspace.WorkspaceRelativePath;
import io.github.mundanej.map.workspace.WorkspaceSession;
import io.github.mundanej.map.workspace.WorkspaceSourceReference;
import io.github.mundanej.map.workspace.WorkspaceSourceRegistry;
import io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry;
import io.github.mundanej.map.workspace.WorkspaceSymbolReferences;
import io.github.mundanej.map.workspace.WorkspaceViewState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Workspace read/write/open/close path shared by JVM and Native Image smoke execution. */
final class NativeWorkspaceSmokeScenario {
    private static final String OPENER_ID = "native.workspace.feature.v1";
    private static final String CATALOG_ID = "native.workspace.catalog";

    private NativeWorkspaceSmokeScenario() {}

    static void run() {
        run(ignored -> {}, NativeWorkspaceSmokeScenario::deleteTree);
    }

    static void run(Consumer<WorkspaceSession> afterOpen, Consumer<Path> cleanup) {
        java.util.Objects.requireNonNull(afterOpen, "afterOpen");
        java.util.Objects.requireNonNull(cleanup, "cleanup");
        Path directory;
        try {
            directory = Files.createTempDirectory("mundane-map-native-workspace-");
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "workspace-native: temporary directory failed", failure);
        }
        Throwable primary = null;
        try {
            runSuccess(directory, afterOpen);
            runStableFailure(directory);
        } catch (RuntimeException | Error failure) {
            primary = failure;
        }
        try {
            cleanup.accept(directory);
        } catch (RuntimeException | Error failure) {
            if (primary == null) {
                primary = failure;
            } else if (failure != primary) {
                primary.addSuppressed(failure);
            }
        }
        rethrow(primary);
    }

    private static void runSuccess(Path directory, Consumer<WorkspaceSession> afterOpen) {
        Path sourcePath = directory.resolve("features.data");
        Path workspacePath = directory.resolve("native.mmap.xml");
        try {
            Files.writeString(sourcePath, "fixed native source\n", StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("workspace-native: source fixture failed", failure);
        }
        WorkspaceDocument document = document();
        WorkspaceFiles.write(workspacePath, document, WorkspaceLimits.DEFAULT);
        WorkspaceFile read = WorkspaceFiles.read(workspacePath, WorkspaceLimits.DEFAULT);
        require(read.document().equals(document), "canonical read changed the document");

        WorkspaceSession session =
                WorkspaceOpener.open(
                        read, context(), io.github.mundanej.map.api.CancellationToken.none());
        OpenedWorkspaceFeatureLayer layer;
        try (session) {
            require(session.layers().size() == 1, "workspace source was not opened");
            layer = (OpenedWorkspaceFeatureLayer) session.layers().getFirst();
            require(!layer.source().isClosed(), "workspace source closed before the session");
            afterOpen.accept(session);
        }
        session.close();
        require(session.isClosed(), "workspace session did not close");
        require(layer.source().isClosed(), "workspace session did not close its source");
    }

    private static void runStableFailure(Path directory) {
        Path hostile = directory.resolve("hostile.mmap.xml");
        try {
            Files.writeString(
                    hostile,
                    "<!DOCTYPE workspace SYSTEM \"file:///workspace-native-secret\">"
                            + "<workspace xmlns=\"urn:mundanej:map:workspace\" version=\"1\"/>",
                    StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("workspace-native: hostile fixture failed", failure);
        }
        try {
            WorkspaceFiles.read(hostile, WorkspaceLimits.DEFAULT);
            throw new IllegalStateException("workspace-native: hostile XML was accepted");
        } catch (WorkspaceException expected) {
            require(
                    expected.problem().code().equals("WORKSPACE_XML_INVALID"),
                    "hostile XML code changed");
            require(
                    expected.problem().context().equals(Map.of("reason", "security")),
                    "hostile XML context changed");
            require(
                    !expected.toString().contains("workspace-native-secret"),
                    "hostile XML leaked input text");
            require(
                    !expected.toString().contains(hostile.toString()),
                    "hostile XML leaked its path");
        }
    }

    private static WorkspaceDocument document() {
        return new WorkspaceDocument(
                new WorkspaceViewState("EPSG:3857", "EPSG:3857", 0, 0, 1),
                List.of(
                        new WorkspaceFeatureLayer(
                                "native-layer",
                                "Native layer",
                                new WorkspaceSourceReference(
                                        OPENER_ID,
                                        new SourceIdentity("native-source", "Native source"),
                                        new WorkspaceRelativePath("features.data")),
                                new WorkspaceSymbolReferences(
                                        CATALOG_ID, "marker", "line", "fill"))));
    }

    private static WorkspaceOpenContext context() {
        WorkspaceLocalPathProfile profile =
                new WorkspaceLocalPathProfile(
                        List.of(new WorkspaceLocalPathBranch(".data", List.of())));
        WorkspaceSourceRegistry sources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                OPENER_ID,
                                profile,
                                (identity, path, cancellation) ->
                                        InMemoryFeatureSource.open(
                                                identity,
                                                List.of(
                                                        new FeatureRecord(
                                                                "native-feature",
                                                                "",
                                                                new PointGeometry(
                                                                        new Coordinate(0, 0)),
                                                                Map.of())),
                                                Optional.empty(),
                                                Optional.of(
                                                        CrsMetadata.recognized(
                                                                CrsDefinitions.EPSG_3857,
                                                                Optional.of("EPSG:3857"),
                                                                Optional.empty())),
                                                FeatureSourceLimits.LEVEL_1))
                        .build();
        WorkspaceSymbolCatalogRegistry catalogs =
                WorkspaceSymbolCatalogRegistry.builder().register(CATALOG_ID, symbols()).build();
        return new WorkspaceOpenContext(CrsRegistry.level1(), sources, catalogs);
    }

    private static NamedSymbolCatalog symbols() {
        SolidLineSymbol line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(20, 80, 160),
                                new SymbolLength(1, SymbolUnit.SCREEN_PIXEL)),
                        1);
        return NamedSymbolCatalog.of(
                List.of(
                        new NamedSymbol(
                                "marker",
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.CIRCLE, Rgba.rgb(20, 80, 160), 8, 1)),
                        new NamedSymbol("line", line),
                        new NamedSymbol(
                                "fill",
                                SolidFillSymbol.of(
                                        new Rgba(20, 80, 160, 80), Optional.of(line), 1))));
    }

    static void deleteTree(Path directory) {
        try {
            Files.deleteIfExists(directory.resolve("hostile.mmap.xml"));
            Files.deleteIfExists(directory.resolve("native.mmap.xml"));
            Files.deleteIfExists(directory.resolve("features.data"));
            Files.delete(directory);
        } catch (IOException failure) {
            throw new IllegalStateException("workspace-native: cleanup failed", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("workspace-native: " + message);
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw (Error) failure;
    }
}
