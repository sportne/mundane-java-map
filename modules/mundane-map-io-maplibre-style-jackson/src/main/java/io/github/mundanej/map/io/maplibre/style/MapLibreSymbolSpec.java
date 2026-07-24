package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Package-private detached symbol-layer declaration materialized during explicit binding. */
record MapLibreSymbolSpec(
        IconExpression icon,
        double size,
        double rotationDegrees,
        double opacity,
        SymbolAnchor anchor,
        double offsetX,
        double offsetY,
        SymbolRotationMode rotationMode,
        Optional<PointLabelProfile> pointLabel,
        Optional<PortrayalPredicate> filter,
        int catalogReferences,
        int maximumCatalogReferences) {
    MapLibreSymbolSpec {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(rotationMode, "rotationMode");
        Objects.requireNonNull(pointLabel, "pointLabel");
        Objects.requireNonNull(filter, "filter");
        if (catalogReferences < 0 || maximumCatalogReferences < 0) {
            throw new IllegalArgumentException("catalog reference counts must be non-negative");
        }
    }

    MapLibreSymbolSpec withFilter(PortrayalPredicate predicate) {
        return new MapLibreSymbolSpec(
                icon,
                size,
                rotationDegrees,
                opacity,
                anchor,
                offsetX,
                offsetY,
                rotationMode,
                pointLabel,
                Optional.of(predicate),
                catalogReferences,
                maximumCatalogReferences);
    }

    sealed interface IconExpression
            permits IconExpression.Literal,
                    IconExpression.Attribute,
                    IconExpression.Match,
                    IconExpression.Case {
        record Literal(String name) implements IconExpression {
            public Literal {
                name = requireName(name);
            }
        }

        record Attribute(String attribute, boolean stringify) implements IconExpression {
            public Attribute {
                attribute = requireName(attribute);
            }
        }

        record Match(String attribute, List<MatchRule> rules, String fallback)
                implements IconExpression {
            public Match {
                attribute = requireName(attribute);
                rules = List.copyOf(rules);
                fallback = requireName(fallback);
            }
        }

        record Case(List<CaseRule> rules, String fallback) implements IconExpression {
            public Case {
                rules = List.copyOf(rules);
                fallback = requireName(fallback);
            }
        }
    }

    record MatchRule(io.github.mundanej.map.api.ThematicValue value, String iconName) {
        MatchRule {
            Objects.requireNonNull(value, "value");
            iconName = requireName(iconName);
        }
    }

    record CaseRule(PortrayalPredicate predicate, String iconName) {
        CaseRule {
            Objects.requireNonNull(predicate, "predicate");
            iconName = requireName(iconName);
        }
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("name must be non-blank without edge whitespace");
        }
        return value;
    }
}
