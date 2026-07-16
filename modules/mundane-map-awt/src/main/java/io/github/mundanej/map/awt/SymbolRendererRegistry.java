package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable explicit AWT symbol renderer registry without runtime discovery or global mutation.
 *
 * <p>Lookup uses the exact {@link SymbolRole}/{@link SymbolRendererKey} pair. Each registry owns an
 * immutable copy of its registrations; duplicate slots and application use of reserved built-in
 * keys fail with stable {@link SymbolException} diagnostics.
 */
@SuppressWarnings("deprecation")
public final class SymbolRendererRegistry {
    private static final String RESERVED_PREFIX = "io.github.mundanej.map.symbol.";
    private final Map<Slot, AwtSymbolRenderer> renderers;

    private SymbolRendererRegistry(Map<Slot, AwtSymbolRenderer> renderers) {
        this.renderers = Map.copyOf(renderers);
    }

    /**
     * Returns an empty single-use builder.
     *
     * @return new application-registration builder
     */
    public static Builder builder() {
        return new Builder(false);
    }

    /**
     * Returns a single-use builder preloaded with the source-listed built-ins.
     *
     * @return new builder containing all Level 1 built-in renderer slots
     */
    public static Builder builderWithBuiltIns() {
        return new Builder(true);
    }

    /**
     * Returns a new immutable registry containing the source-listed built-ins.
     *
     * @return independent immutable built-in registry
     */
    public static SymbolRendererRegistry builtIn() {
        return builderWithBuiltIns().build();
    }

    AwtSymbolRenderer find(SymbolRole role, SymbolRendererKey key) {
        return renderers.get(new Slot(role, key));
    }

    /** Single-use explicit registry builder without automatic discovery. */
    public static final class Builder {
        private final LinkedHashMap<Slot, AwtSymbolRenderer> entries = new LinkedHashMap<>();
        private boolean consumed;

        private Builder(boolean builtIns) {
            if (builtIns) {
                registerBuiltIns();
            }
        }

        /**
         * Registers one application-owned role/key renderer slot.
         *
         * @param role non-legacy symbol role
         * @param key non-reserved application renderer key
         * @param renderer non-null renderer instance owned by the resulting registry
         * @return this builder
         * @throws NullPointerException if an argument is {@code null}
         * @throws IllegalArgumentException if {@code role} is the legacy role
         * @throws SymbolException if the key is reserved or the exact role/key slot is duplicated
         * @throws IllegalStateException if this builder has already been consumed
         */
        public Builder register(
                SymbolRole role, SymbolRendererKey key, AwtSymbolRenderer renderer) {
            requireOpen();
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(renderer, "renderer");
            if (role == SymbolRole.LEGACY_GEOMETRY) {
                throw new IllegalArgumentException("role must be marker, line, or fill");
            }
            if (key.value().startsWith(RESERVED_PREFIX)) {
                throw failure(SymbolException.RENDERER_RESERVED_KEY, role, key);
            }
            put(role, key, renderer);
            return this;
        }

        /**
         * Builds an immutable registry and consumes this builder.
         *
         * @return independent immutable registry
         * @throws IllegalStateException if this builder has already been consumed
         */
        public SymbolRendererRegistry build() {
            requireOpen();
            consumed = true;
            return new SymbolRendererRegistry(entries);
        }

        private void registerBuiltIns() {
            putBuiltIn(
                    SymbolRole.LEGACY_GEOMETRY,
                    FeatureStyle.RENDERER_KEY,
                    value -> value instanceof FeatureStyle);
            putBuiltIn(
                    SymbolRole.MARKER,
                    VectorMarkerSymbol.RENDERER_KEY,
                    value -> value instanceof VectorMarkerSymbol);
            putBuiltIn(
                    SymbolRole.MARKER,
                    RasterIconSymbol.RENDERER_KEY,
                    value -> value instanceof RasterIconSymbol);
            putBuiltIn(
                    SymbolRole.LINE,
                    SolidLineSymbol.RENDERER_KEY,
                    value -> value instanceof SolidLineSymbol);
            putBuiltIn(
                    SymbolRole.FILL,
                    SolidFillSymbol.RENDERER_KEY,
                    value -> value instanceof SolidFillSymbol);
            putBuiltIn(
                    SymbolRole.FILL,
                    HatchFillSymbol.RENDERER_KEY,
                    value -> value instanceof HatchFillSymbol);
            putBuiltIn(
                    SymbolRole.MARKER,
                    CompositeSymbol.RENDERER_KEY,
                    value -> value instanceof CompositeSymbol);
            putBuiltIn(
                    SymbolRole.LINE,
                    CompositeSymbol.RENDERER_KEY,
                    value -> value instanceof CompositeSymbol);
            putBuiltIn(
                    SymbolRole.FILL,
                    CompositeSymbol.RENDERER_KEY,
                    value -> value instanceof CompositeSymbol);
        }

        private void putBuiltIn(
                SymbolRole role,
                SymbolRendererKey key,
                java.util.function.Predicate<Symbol> supports) {
            put(
                    role,
                    key,
                    new AwtSymbolRenderer() {
                        @Override
                        public boolean supports(Symbol value) {
                            return supports.test(value);
                        }

                        @Override
                        public SymbolRenderResult render(
                                Symbol value, AwtSymbolRenderContext context) {
                            return context.renderBuiltIn(value);
                        }

                        @Override
                        public boolean hitTest(Symbol value, AwtSymbolHitContext context) {
                            return context.hitBuiltIn(value);
                        }
                    });
        }

        private void put(SymbolRole role, SymbolRendererKey key, AwtSymbolRenderer renderer) {
            Slot slot = new Slot(role, key);
            if (entries.putIfAbsent(slot, renderer) != null) {
                throw failure(SymbolException.RENDERER_DUPLICATE, role, key);
            }
        }

        private void requireOpen() {
            if (consumed) {
                throw new IllegalStateException("builder is already consumed");
            }
        }

        private static SymbolException failure(
                String code, SymbolRole role, SymbolRendererKey key) {
            LinkedHashMap<String, String> context = new LinkedHashMap<>();
            context.put("role", role.name());
            context.put("key", key.value());
            return new SymbolException(code, "Symbol renderer registration rejected", context);
        }
    }

    private record Slot(SymbolRole role, SymbolRendererKey key) {}
}
