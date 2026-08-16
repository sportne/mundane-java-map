package io.github.mundanej.map.api;

import java.util.Map;
import java.util.Objects;

/**
 * A named geometry with stable identity, attributes, and a role-compatible symbol.
 *
 * @param id non-blank stable feature identity
 * @param name non-null display name, which may be empty
 * @param geometry immutable feature geometry
 * @param attributes attributes defensively canonicalized into insertion order
 * @param symbol symbol compatible with the geometry role
 */
public record Feature(
        String id, String name, Geometry geometry, Map<String, Object> attributes, Symbol symbol) {
    /** Creates a feature and defensively copies its attributes. */
    public Feature {
        id = requireText(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(geometry, "geometry");
        attributes = AttributeValues.canonicalize(attributes);
        Objects.requireNonNull(symbol, "symbol");
        validateSymbolRole(id, geometry, symbol);
    }

    /**
     * Returns the immutable insertion-ordered owned attributes.
     *
     * @return immutable canonical attributes
     */
    @Override
    public Map<String, Object> attributes() {
        return java.util.Collections.unmodifiableMap(attributes);
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @SuppressWarnings("deprecation")
    private static void validateSymbolRole(String id, Geometry geometry, Symbol symbol) {
        if (symbol instanceof FeatureStyle) {
            return;
        }
        SymbolRole actualRole = symbol.role();
        SymbolRole expectedRole = expectedRole(geometry);
        int interfaceCount =
                (symbol instanceof MarkerSymbol ? 1 : 0)
                        + (symbol instanceof LineSymbol ? 1 : 0)
                        + (symbol instanceof FillSymbol ? 1 : 0);
        boolean supportedShape = symbol instanceof CompositeSymbol || interfaceCount == 1;
        if (!supportedShape || actualRole != expectedRole) {
            throw roleMismatch(id, geometry, actualRole);
        }
        Objects.requireNonNull(symbol.rendererKey(), "symbol.rendererKey");
        double opacity = symbol.opacity();
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException(
                    "symbol opacity must be finite and between zero and one");
        }
    }

    private static SymbolRole expectedRole(Geometry geometry) {
        return switch (geometry.kind()) {
            case POINT, MULTI_POINT -> SymbolRole.MARKER;
            case LINE_STRING, MULTI_LINE_STRING -> SymbolRole.LINE;
            case POLYGON, MULTI_POLYGON -> SymbolRole.FILL;
            case GEOMETRY_COLLECTION ->
                    throw new GeometryException(
                            GeometryException.KIND_UNSUPPORTED,
                            "A heterogeneous collection requires per-member portrayal",
                            Map.of("consumer", "Feature", "kind", geometry.kind().name()));
        };
    }

    private static SymbolException roleMismatch(
            String id, Geometry geometry, SymbolRole actualRole) {
        Map<String, String> context = new java.util.LinkedHashMap<>();
        context.put("featureId", id);
        context.put("geometryKind", geometryKind(geometry));
        context.put("symbolRole", actualRole == null ? "null" : actualRole.name());
        return new SymbolException(
                SymbolException.ROLE_MISMATCH,
                "Symbol role does not match feature geometry",
                context);
    }

    private static String geometryKind(Geometry geometry) {
        return geometry.kind().name();
    }
}
