package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.core.CrsRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit all-or-nothing local workspace opening facade. */
public final class WorkspaceOpener {
    private WorkspaceOpener() {}

    /**
     * Opens every layer through explicit registries and returns one owning session.
     *
     * @param file immutable document and real local base
     * @param context explicit CRS, source, and symbol registries
     * @param cancellation cross-thread cancellation signal
     * @return owning all-or-nothing workspace session
     * @throws WorkspaceException for stable preflight, path, opener, identity, or cancellation
     *     failures
     */
    public static WorkspaceSession open(
            WorkspaceFile file, WorkspaceOpenContext context, CancellationToken cancellation) {
        return open(file, context, cancellation, WorkspacePathAccess.JDK);
    }

    static WorkspaceSession open(
            WorkspaceFile file,
            WorkspaceOpenContext context,
            CancellationToken cancellation,
            WorkspacePathAccess paths) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(paths, "paths");
        cancelled(cancellation, "preflight", null);
        CrsDefinition mapCrs =
                resolveCrs(context.crsRegistry(), file.document().view().mapCrsKey(), "mapCrs");
        CrsDefinition displayCrs =
                resolveCrs(
                        context.crsRegistry(),
                        file.document().view().displayCrsKey(),
                        "displayCrs");
        cancelled(cancellation, "preflight", null);

        List<PreparedLayer> prepared = new ArrayList<>();
        for (int index = 0; index < file.document().layers().size(); index++) {
            cancelled(cancellation, "preflight", index);
            WorkspaceLayerDefinition layer = file.document().layers().get(index);
            WorkspaceSourceKind expected = kind(layer);
            WorkspaceSourceRegistry.Registration registration =
                    context.sources().find(layer.source().openerId());
            if (registration == null) {
                throw WorkspaceFailures.openerUnregistered(expected, index);
            }
            if (registration.kind() != expected) {
                throw WorkspaceFailures.kindMismatch(expected, registration.kind(), index);
            }
            WorkspaceLocalPathBranch branch =
                    registration.profile().requireBranch(layer.source().path().value(), index);
            ResolvedSymbols symbols =
                    layer instanceof WorkspaceFeatureLayer feature
                            ? resolveSymbols(feature, context.catalogs(), index)
                            : null;
            prepared.add(new PreparedLayer(index, layer, registration, branch, symbols, null));
            cancelled(cancellation, "preflight", index);
        }

        List<PreparedLayer> guarded = new ArrayList<>(prepared.size());
        for (PreparedLayer layer : prepared) {
            cancelled(cancellation, "path", layer.index());
            Path primary = guardPaths(file.baseDirectory(), layer, paths);
            guarded.add(layer.withPrimary(primary));
            cancelled(cancellation, "path", layer.index());
        }

        List<OpenedWorkspaceLayer> opened = new ArrayList<>();
        try {
            for (PreparedLayer layer : guarded) {
                cancelled(cancellation, "sourceOpen", layer.index());
                OpenedWorkspaceLayer result = openLayer(layer, cancellation);
                opened.add(result);
            }
            cancelled(cancellation, "publish", null);
            return new WorkspaceSession(file.document(), mapCrs, displayCrs, opened);
        } catch (RuntimeException | Error failure) {
            closeReverse(opened, failure);
            throw failure;
        }
    }

    static void close(OpenedWorkspaceLayer layer) {
        if (layer instanceof OpenedWorkspaceFeatureLayer feature) {
            feature.source().close();
        } else if (layer instanceof OpenedWorkspaceRasterLayer raster) {
            raster.source().close();
        } else {
            throw new AssertionError("unreachable opened workspace layer variant");
        }
    }

    private static CrsDefinition resolveCrs(CrsRegistry registry, String key, String field) {
        CrsDefinition resolved;
        try {
            resolved = registry.resolve(key);
        } catch (CrsException failure) {
            throw WorkspaceFailures.crsUnregistered(field);
        }
        CrsDefinition expected = CrsRegistry.level1().resolve(key);
        if (!resolved.equals(expected)) {
            throw WorkspaceFailures.value(field, "definitionMismatch", null);
        }
        return resolved;
    }

    private static ResolvedSymbols resolveSymbols(
            WorkspaceFeatureLayer layer, WorkspaceSymbolCatalogRegistry registry, int index) {
        WorkspaceSymbolReferences references = layer.symbols();
        NamedSymbolCatalog catalog = registry.find(references.catalogId());
        if (catalog == null) {
            throw WorkspaceFailures.catalogUnregistered(index);
        }
        return new ResolvedSymbols(
                symbol(catalog, references.markerName(), "marker", SymbolRole.MARKER, index),
                symbol(catalog, references.lineName(), "line", SymbolRole.LINE, index),
                symbol(catalog, references.fillName(), "fill", SymbolRole.FILL, index));
    }

    private static Symbol symbol(
            NamedSymbolCatalog catalog, String name, String role, SymbolRole expected, int index) {
        Symbol symbol =
                catalog.find(name).orElseThrow(() -> WorkspaceFailures.symbolMissing(role, index));
        if (symbol.role() != expected) {
            throw WorkspaceFailures.symbolRole(role, index);
        }
        return symbol;
    }

    private static Path guardPaths(Path base, PreparedLayer layer, WorkspacePathAccess paths) {
        String relative = layer.layer().source().path().value();
        Path primaryLexical = base.resolve(relative).normalize();
        if (!primaryLexical.startsWith(base)) {
            throw WorkspaceFailures.path("grammar", layer.index());
        }
        Path primary = guard(primaryLexical, base, true, layer.index(), paths);
        int stemLength = relative.length() - layer.branch().primarySuffix().length();
        String stem = relative.substring(0, stemLength);
        for (String suffix : layer.branch().replacementSuffixes()) {
            Path candidate = base.resolve(stem + suffix).normalize();
            BasicFileAttributes attributes = attributes(candidate, layer.index(), paths);
            if (attributes != null) {
                guard(candidate, base, false, layer.index(), paths);
            }
        }
        return primary;
    }

    private static Path guard(
            Path lexical, Path base, boolean primary, int index, WorkspacePathAccess paths) {
        BasicFileAttributes before = attributes(lexical, index, paths);
        if (before == null) {
            if (primary) {
                throw WorkspaceFailures.resourceMissing(index);
            }
            return lexical;
        }
        if (before.isSymbolicLink()) {
            throw WorkspaceFailures.path("symlink", index);
        }
        if (!before.isRegularFile()) {
            throw WorkspaceFailures.path("wrongKind", index);
        }
        Path real;
        try {
            real = paths.realPath(lexical);
        } catch (IOException failure) {
            throw WorkspaceFailures.path("identity", index);
        }
        if (!real.startsWith(base)) {
            throw WorkspaceFailures.path("escape", index);
        }
        BasicFileAttributes after = attributes(real, index, paths);
        if (after == null
                || !after.isRegularFile()
                || after.isSymbolicLink()
                || before.fileKey() == null
                || after.fileKey() == null
                || !before.fileKey().equals(after.fileKey())) {
            throw WorkspaceFailures.path("identity", index);
        }
        return real;
    }

    private static BasicFileAttributes attributes(Path path, int index, WorkspacePathAccess paths) {
        try {
            return paths.attributes(path);
        } catch (IOException failure) {
            throw WorkspaceFailures.path("identity", index);
        }
    }

    private static OpenedWorkspaceLayer openLayer(
            PreparedLayer layer, CancellationToken cancellation) {
        if (layer.registration() instanceof WorkspaceSourceRegistry.FeatureRegistration feature) {
            FeatureSource source;
            try {
                source =
                        Objects.requireNonNull(
                                feature.opener()
                                        .open(
                                                layer.layer().source().identity(),
                                                layer.primary(),
                                                cancellation),
                                "feature opener result");
            } catch (SourceException failure) {
                throw mappedSourceFailure(WorkspaceSourceKind.FEATURE, layer.index(), failure);
            }
            try {
                requireIdentity(source.metadata().identity(), layer);
                if (cancellation.isCancellationRequested()) {
                    throw WorkspaceFailures.cancelled("sourceOpen", layer.index(), null);
                }
                WorkspaceFeatureLayer definition = (WorkspaceFeatureLayer) layer.layer();
                ResolvedSymbols symbols = layer.symbols();
                return new OpenedWorkspaceFeatureLayer(
                        definition, source, symbols.marker(), symbols.line(), symbols.fill());
            } catch (RuntimeException | Error failure) {
                closeCurrent(source, failure);
                throw failure;
            }
        }
        WorkspaceSourceRegistry.RasterRegistration raster =
                (WorkspaceSourceRegistry.RasterRegistration) layer.registration();
        RasterSource source;
        try {
            source =
                    Objects.requireNonNull(
                            raster.opener()
                                    .open(
                                            layer.layer().source().identity(),
                                            layer.primary(),
                                            cancellation),
                            "raster opener result");
        } catch (SourceException failure) {
            throw mappedSourceFailure(WorkspaceSourceKind.RASTER, layer.index(), failure);
        }
        try {
            requireIdentity(source.metadata().identity(), layer);
            if (cancellation.isCancellationRequested()) {
                throw WorkspaceFailures.cancelled("sourceOpen", layer.index(), null);
            }
            return new OpenedWorkspaceRasterLayer((WorkspaceRasterLayer) layer.layer(), source);
        } catch (RuntimeException | Error failure) {
            closeCurrent(source, failure);
            throw failure;
        }
    }

    private static void requireIdentity(
            io.github.mundanej.map.api.SourceIdentity actual, PreparedLayer layer) {
        if (!layer.layer().source().identity().equals(actual)) {
            throw WorkspaceFailures.identityMismatch(layer.index());
        }
    }

    private static WorkspaceException mappedSourceFailure(
            WorkspaceSourceKind kind, int index, SourceException failure) {
        if (failure.terminal().code().equals("SOURCE_CANCELLED")) {
            return WorkspaceFailures.cancelled("sourceOpen", index, failure.report(), failure);
        }
        return WorkspaceFailures.sourceOpen(kind, index, failure.report(), failure);
    }

    private static void cancelled(CancellationToken cancellation, String phase, Integer index) {
        if (cancellation.isCancellationRequested()) {
            throw WorkspaceFailures.cancelled(phase, index, null);
        }
    }

    private static WorkspaceSourceKind kind(WorkspaceLayerDefinition layer) {
        return layer instanceof WorkspaceFeatureLayer
                ? WorkspaceSourceKind.FEATURE
                : WorkspaceSourceKind.RASTER;
    }

    private static void closeCurrent(FeatureSource source, Throwable primary) {
        try {
            source.close();
        } catch (RuntimeException | Error failure) {
            suppressCleanup(primary, failure);
        }
    }

    private static void closeCurrent(RasterSource source, Throwable primary) {
        try {
            source.close();
        } catch (RuntimeException | Error failure) {
            suppressCleanup(primary, failure);
        }
    }

    private static void closeReverse(List<OpenedWorkspaceLayer> opened, Throwable primary) {
        for (int index = opened.size() - 1; index >= 0; index--) {
            try {
                close(opened.get(index));
            } catch (RuntimeException | Error failure) {
                suppressCleanup(primary, failure);
            }
        }
    }

    static void suppressCleanup(Throwable primary, Throwable failure) {
        if (primary != failure) {
            primary.addSuppressed(failure);
        }
    }

    private record ResolvedSymbols(Symbol marker, Symbol line, Symbol fill) {}

    private record PreparedLayer(
            int index,
            WorkspaceLayerDefinition layer,
            WorkspaceSourceRegistry.Registration registration,
            WorkspaceLocalPathBranch branch,
            ResolvedSymbols symbols,
            Path primary) {
        private PreparedLayer withPrimary(Path value) {
            return new PreparedLayer(index, layer, registration, branch, symbols, value);
        }
    }
}
