package io.github.mundanej.map.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable semantic result of the bounded WKT2:2019 profile.
 *
 * @param name bounded CRS name
 * @param kind CRS kind
 * @param identifier optional canonical authority identifier
 * @param datumName optional geodetic or vertical datum name
 * @param ellipsoid optional retained ellipsoid
 * @param axes native axes ordered by their explicit tuple order
 * @param baseIdentifier optional projected base-geographic identifier
 * @param operationMethod optional projected conversion method
 * @param parameters immutable method parameters keyed by WKT name
 * @param components ordered compound CRS components
 */
public record WktCrsDefinition(
        String name,
        WktCrsKind kind,
        Optional<String> identifier,
        Optional<String> datumName,
        Optional<CrsEllipsoid> ellipsoid,
        List<WktCrsAxis> axes,
        Optional<String> baseIdentifier,
        Optional<String> operationMethod,
        Map<String, Double> parameters,
        List<WktCrsDefinition> components) {
    private static final int TEXT_LIMIT = 256;
    private static final int PARAMETER_LIMIT = 32;
    private static final int COMPONENT_LIMIT = 4;

    /** Validates and defensively copies the semantic definition. */
    public WktCrsDefinition {
        name = bounded(name, "name");
        Objects.requireNonNull(kind, "kind");
        identifier = boundedOptional(identifier, "identifier");
        datumName = boundedOptional(datumName, "datumName");
        Objects.requireNonNull(ellipsoid, "ellipsoid");
        axes = List.copyOf(Objects.requireNonNull(axes, "axes"));
        baseIdentifier = boundedOptional(baseIdentifier, "baseIdentifier");
        operationMethod = boundedOptional(operationMethod, "operationMethod");
        Objects.requireNonNull(parameters, "parameters");
        if (parameters.size() > PARAMETER_LIMIT) {
            throw new IllegalArgumentException("WKT CRS has too many operation parameters");
        }
        TreeMap<String, Double> ordered = new TreeMap<>();
        parameters.forEach(
                (parameterName, value) -> {
                    String key = bounded(parameterName, "parameterName");
                    if (value == null || !Double.isFinite(value)) {
                        throw new IllegalArgumentException(
                                "WKT operation parameter must be finite");
                    }
                    ordered.put(key, value);
                });
        parameters = Collections.unmodifiableMap(ordered);
        components = List.copyOf(Objects.requireNonNull(components, "components"));
        if (components.size() > COMPONENT_LIMIT) {
            throw new IllegalArgumentException("WKT compound CRS has too many components");
        }
        validateShape(kind, axes, operationMethod, components);
    }

    private static void validateShape(
            WktCrsKind kind,
            List<WktCrsAxis> axes,
            Optional<String> operationMethod,
            List<WktCrsDefinition> components) {
        if (kind == WktCrsKind.COMPOUND) {
            if (components.size() < 2 || !axes.isEmpty() || operationMethod.isPresent()) {
                throw new IllegalArgumentException("A compound WKT CRS requires only components");
            }
            return;
        }
        if (!components.isEmpty() || axes.isEmpty()) {
            throw new IllegalArgumentException("A non-compound WKT CRS requires axes only");
        }
        boolean[] orders = new boolean[axes.size() + 1];
        for (WktCrsAxis axis : axes) {
            if (axis.order() > axes.size() || orders[axis.order()]) {
                throw new IllegalArgumentException("WKT axis orders must be unique and contiguous");
            }
            orders[axis.order()] = true;
        }
        if ((kind == WktCrsKind.PROJECTED) != operationMethod.isPresent()) {
            throw new IllegalArgumentException(
                    "Only a projected WKT CRS requires an operation method");
        }
    }

    private static Optional<String> boundedOptional(Optional<String> value, String role) {
        Objects.requireNonNull(value, role);
        return value.map(text -> bounded(text, role));
    }

    private static String bounded(String value, String role) {
        Objects.requireNonNull(value, role);
        if (value.isBlank() || value.length() > TEXT_LIMIT) {
            throw new IllegalArgumentException(role + " must be non-blank and bounded");
        }
        return value;
    }
}
