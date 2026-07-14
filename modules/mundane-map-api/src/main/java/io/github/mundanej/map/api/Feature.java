package io.github.mundanej.map.api;

import java.util.Map;
import java.util.Objects;

/** A named geometry with stable identity, attributes, and a role-compatible symbol. */
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

    /** Returns the immutable insertion-ordered owned attributes. */
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
        if (geometry instanceof PointGeometry) {
            return SymbolRole.MARKER;
        }
        if (geometry instanceof LineStringGeometry) {
            return SymbolRole.LINE;
        }
        if (geometry instanceof PolygonGeometry) {
            return SymbolRole.FILL;
        }
        if (geometry instanceof MultiPointGeometry) {
            return SymbolRole.MARKER;
        }
        if (geometry instanceof MultiLineStringGeometry) {
            return SymbolRole.LINE;
        }
        if (geometry instanceof MultiPolygonGeometry) {
            return SymbolRole.FILL;
        }
        throw new IllegalArgumentException("Unsupported geometry type");
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
        if (geometry instanceof PointGeometry) {
            return "POINT";
        }
        if (geometry instanceof LineStringGeometry) {
            return "LINE_STRING";
        }
        if (geometry instanceof PolygonGeometry) {
            return "POLYGON";
        }
        if (geometry instanceof MultiPointGeometry) {
            return "MULTI_POINT";
        }
        if (geometry instanceof MultiLineStringGeometry) {
            return "MULTI_LINE_STRING";
        }
        if (geometry instanceof MultiPolygonGeometry) {
            return "MULTI_POLYGON";
        }
        return "UNSUPPORTED";
    }
}
