package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Explicit Vaadin binding for one synchronous feature source and one supported built-in portrayal.
 *
 * <p>A borrowed binding never closes its source. An owned binding transfers exclusive source
 * ownership and closes it exactly once when removed, when its component closes, or when the
 * unattached binding is closed. A binding can be attached to at most one {@link MundaneMap}.
 */
public final class FeatureSourceBinding implements AutoCloseable {
    private static final Object SOURCE_CLAIM_LOCK = new Object();
    private static final IdentityHashMap<FeatureSource, FeatureSourceBinding> SOURCE_CLAIMS =
            new IdentityHashMap<>();

    private final String id;
    private final String name;
    private final FeatureSource source;
    private final FeaturePortrayalResolver portrayal;
    private final Set<RasterIconSymbol> authorizedIcons;
    private final AttributeSelection attributes;
    private final Optional<FeatureQueryLimits> tighterLimits;
    private final boolean owned;
    private MundaneMap owner;
    private boolean closed;

    private FeatureSourceBinding(
            String id,
            String name,
            FeatureSource source,
            Symbol marker,
            Symbol line,
            Symbol fill,
            AttributeSelection attributes,
            Optional<FeatureQueryLimits> tighterLimits,
            boolean owned) {
        this(
                id,
                name,
                source,
                fixedResolver(marker, line, fill),
                attributes,
                NamedSymbolCatalog.of(List.of()),
                tighterLimits,
                owned);
    }

    private FeatureSourceBinding(
            String id,
            String name,
            FeatureSource source,
            FeaturePortrayalResolver portrayal,
            AttributeSelection attributes,
            NamedSymbolCatalog catalog,
            Optional<FeatureQueryLimits> tighterLimits,
            boolean owned) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.source = Objects.requireNonNull(source, "source");
        this.portrayal = Objects.requireNonNull(portrayal, "portrayal");
        Objects.requireNonNull(catalog, "catalog");
        if (portrayal.pointLabel().isPresent()) {
            throw SceneProtocol.unsupportedBindingValue("point label");
        }
        Set<RasterIconSymbol> catalogIcons = identityIconSet(catalog);
        for (Symbol symbol : portrayal.reachableSymbols()) {
            SceneProtocol.requirePortrayalSymbol(
                    symbol, symbol.role(), catalogIcons::contains, "binding");
        }
        Set<RasterIconSymbol> selectedIcons = identityIconSet(portrayal.reachableSymbols());
        selectedIcons.retainAll(catalogIcons);
        this.authorizedIcons = Collections.unmodifiableSet(selectedIcons);
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        this.tighterLimits = Objects.requireNonNull(tighterLimits, "tighterLimits");
        this.owned = owned;
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        tighterLimits.ifPresent(
                limits -> {
                    if (!limits.tightens(source.limits().queryLimits())) {
                        throw new IllegalArgumentException(
                                "Binding query limits may only tighten source limits");
                    }
                });
    }

    /**
     * Creates a binding whose source remains caller-owned.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param source open caller-owned source
     * @param marker built-in marker-role point and multipoint symbol
     * @param line built-in line-role line and multiline symbol
     * @param fill built-in fill-role polygon and multipolygon symbol
     * @param attributes exact source attributes required by the binding
     * @param tighterLimits optional per-query limits that only tighten the source limits
     * @return new unattached borrowed binding
     */
    public static FeatureSourceBinding borrowed(
            String id,
            String name,
            FeatureSource source,
            Symbol marker,
            Symbol line,
            Symbol fill,
            AttributeSelection attributes,
            Optional<FeatureQueryLimits> tighterLimits) {
        return new FeatureSourceBinding(
                id, name, source, marker, line, fill, attributes, tighterLimits, false);
    }

    /**
     * Creates a binding that assumes exclusive responsibility for closing its source.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param source open source whose ownership is transferred
     * @param marker built-in marker-role point and multipoint symbol
     * @param line built-in line-role line and multiline symbol
     * @param fill built-in fill-role polygon and multipolygon symbol
     * @param attributes exact source attributes required by the binding
     * @param tighterLimits optional per-query limits that only tighten the source limits
     * @return new unattached owned binding
     */
    public static FeatureSourceBinding owned(
            String id,
            String name,
            FeatureSource source,
            Symbol marker,
            Symbol line,
            Symbol fill,
            AttributeSelection attributes,
            Optional<FeatureQueryLimits> tighterLimits) {
        return new FeatureSourceBinding(
                id, name, source, marker, line, fill, attributes, tighterLimits, true);
    }

    /**
     * Creates a caller-owned source binding whose portrayal is evaluated only by the Java core.
     *
     * <p>The source projection is derived exactly from the portrayal. Raster icons are unsupported
     * by this overload; use the catalog overload to authorize them explicitly.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param source open caller-owned source
     * @param portrayal closed server-side portrayal without labels
     * @param tighterLimits optional per-query limits that only tighten the source limits
     * @return new unattached borrowed binding
     */
    public static FeatureSourceBinding borrowed(
            String id,
            String name,
            FeatureSource source,
            FeaturePortrayal portrayal,
            Optional<FeatureQueryLimits> tighterLimits) {
        return borrowed(
                id, name, source, portrayal, NamedSymbolCatalog.of(List.of()), tighterLimits);
    }

    /**
     * Creates a caller-owned source binding with an explicit immutable icon authorization catalog.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param source open caller-owned source
     * @param portrayal closed server-side portrayal without labels
     * @param catalog catalog whose exact raster-icon instances may be published
     * @param tighterLimits optional per-query limits that only tighten the source limits
     * @return new unattached borrowed binding
     */
    public static FeatureSourceBinding borrowed(
            String id,
            String name,
            FeatureSource source,
            FeaturePortrayal portrayal,
            NamedSymbolCatalog catalog,
            Optional<FeatureQueryLimits> tighterLimits) {
        FeaturePortrayalResolver resolver = FeaturePortrayalResolver.compile(portrayal);
        return new FeatureSourceBinding(
                id,
                name,
                source,
                resolver,
                exactAttributes(resolver),
                catalog,
                tighterLimits,
                false);
    }

    /**
     * Creates an exclusively owned source binding whose portrayal is evaluated by the Java core.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param source open source whose ownership is transferred
     * @param portrayal closed server-side portrayal without labels
     * @param tighterLimits optional per-query limits that only tighten the source limits
     * @return new unattached owned binding
     */
    public static FeatureSourceBinding owned(
            String id,
            String name,
            FeatureSource source,
            FeaturePortrayal portrayal,
            Optional<FeatureQueryLimits> tighterLimits) {
        return owned(id, name, source, portrayal, NamedSymbolCatalog.of(List.of()), tighterLimits);
    }

    /**
     * Creates an exclusively owned source binding with an explicit icon authorization catalog.
     *
     * @param id stable non-blank layer identity
     * @param name non-blank display name
     * @param source open source whose ownership is transferred
     * @param portrayal closed server-side portrayal without labels
     * @param catalog catalog whose exact raster-icon instances may be published
     * @param tighterLimits optional per-query limits that only tighten the source limits
     * @return new unattached owned binding
     */
    public static FeatureSourceBinding owned(
            String id,
            String name,
            FeatureSource source,
            FeaturePortrayal portrayal,
            NamedSymbolCatalog catalog,
            Optional<FeatureQueryLimits> tighterLimits) {
        FeaturePortrayalResolver resolver = FeaturePortrayalResolver.compile(portrayal);
        return new FeatureSourceBinding(
                id,
                name,
                source,
                resolver,
                exactAttributes(resolver),
                catalog,
                tighterLimits,
                true);
    }

    /**
     * Returns the stable browser layer identity.
     *
     * @return stable layer identity
     */
    public String id() {
        return id;
    }

    /**
     * Returns the browser layer display name.
     *
     * @return layer display name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the bound source.
     *
     * @return open feature source while the binding is usable
     */
    public FeatureSource source() {
        return source;
    }

    /**
     * Returns the exact requested attribute projection.
     *
     * @return requested attributes
     */
    public AttributeSelection attributes() {
        return attributes;
    }

    /**
     * Returns the optional per-query limit tightening.
     *
     * @return optional tighter query limits
     */
    public Optional<FeatureQueryLimits> tighterLimits() {
        return tighterLimits;
    }

    /**
     * Returns whether this binding owns its source.
     *
     * @return whether source ownership was transferred
     */
    public boolean owned() {
        return owned;
    }

    /**
     * Returns whether this binding has permanently closed.
     *
     * @return whether this binding is closed
     */
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Closes an unattached binding; an owned binding also closes its source exactly once.
     *
     * @throws IllegalStateException if the binding is attached to a component
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (owner != null) {
            throw new IllegalStateException("binding is attached");
        }
        closed = true;
        if (owned) {
            source.close();
        }
    }

    FeaturePortrayalResolver portrayal() {
        return portrayal;
    }

    boolean authorizes(RasterIconSymbol icon) {
        return authorizedIcons.contains(icon);
    }

    synchronized void attach(MundaneMap candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (closed) {
            throw new IllegalStateException("binding is closed");
        }
        if (owner != null) {
            throw new IllegalStateException("binding is already attached");
        }
        synchronized (SOURCE_CLAIM_LOCK) {
            if (source.isClosed()) {
                throw new IllegalStateException("source is closed");
            }
            FeatureSourceBinding existing = SOURCE_CLAIMS.get(source);
            if (existing != null && existing != this) {
                throw new IllegalStateException("source is already attached");
            }
            SOURCE_CLAIMS.put(source, this);
        }
        owner = candidate;
    }

    synchronized void release(MundaneMap candidate) {
        if (owner != candidate) {
            return;
        }
        owner = null;
        try {
            if (owned && !closed) {
                closed = true;
                source.close();
            }
        } finally {
            releaseSourceClaim();
        }
    }

    synchronized void detach(MundaneMap candidate) {
        if (owner == candidate) {
            owner = null;
            releaseSourceClaim();
        }
    }

    private void releaseSourceClaim() {
        synchronized (SOURCE_CLAIM_LOCK) {
            if (SOURCE_CLAIMS.get(source) == this) {
                SOURCE_CLAIMS.remove(source);
            }
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(name + " must be non-blank and at most 256 chars");
        }
        return value;
    }

    private static AttributeSelection exactAttributes(FeaturePortrayalResolver resolver) {
        List<String> required = resolver.requiredSymbolAttributes();
        return required.isEmpty() ? AttributeSelection.NONE : AttributeSelection.only(required);
    }

    private static FeaturePortrayalResolver fixedResolver(Symbol marker, Symbol line, Symbol fill) {
        Symbol checkedMarker =
                SceneProtocol.requireBuiltInSymbol(
                        marker, SymbolRole.MARKER, "binding", "marker symbol");
        Symbol checkedLine =
                SceneProtocol.requireBuiltInSymbol(line, SymbolRole.LINE, "binding", "line symbol");
        Symbol checkedFill =
                SceneProtocol.requireBuiltInSymbol(fill, SymbolRole.FILL, "binding", "fill symbol");
        return FeaturePortrayalResolver.compile(
                FeaturePortrayal.fixed(checkedMarker, checkedLine, checkedFill));
    }

    private static Set<RasterIconSymbol> identityIconSet(NamedSymbolCatalog catalog) {
        return identityIconSet(catalog.entries().stream().map(entry -> entry.symbol()).toList());
    }

    private static Set<RasterIconSymbol> identityIconSet(List<? extends Symbol> symbols) {
        Set<RasterIconSymbol> icons = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Symbol symbol : symbols) {
            collectIcons(symbol, icons, 0);
        }
        return icons;
    }

    private static void collectIcons(Symbol symbol, Set<RasterIconSymbol> icons, int depth) {
        SceneProtocol.requireSymbolDepth(depth);
        if (symbol instanceof RasterIconSymbol icon) {
            icons.add(icon);
            return;
        }
        if (symbol instanceof io.github.mundanej.map.api.CompositeSymbol composite) {
            for (Symbol child : composite.children()) {
                collectIcons(child, icons, depth + 1);
            }
            return;
        }
        if (symbol instanceof io.github.mundanej.map.api.SolidLineSymbol line) {
            line.startMarker().ifPresent(marker -> collectIcons(marker, icons, depth + 1));
            line.endMarker().ifPresent(marker -> collectIcons(marker, icons, depth + 1));
            return;
        }
        if (symbol instanceof io.github.mundanej.map.api.SolidFillSymbol fill) {
            fill.outline().ifPresent(line -> collectIcons(line, icons, depth + 1));
            return;
        }
        if (symbol instanceof io.github.mundanej.map.api.HatchFillSymbol hatch) {
            hatch.outline().ifPresent(line -> collectIcons(line, icons, depth + 1));
        }
    }
}
