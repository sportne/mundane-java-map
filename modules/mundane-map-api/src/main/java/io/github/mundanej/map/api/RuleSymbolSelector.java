package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * One role view of a shared immutable rule portrayal plan.
 *
 * @param plan shared rule plan
 * @param role marker, line, or fill role
 */
public record RuleSymbolSelector(RulePortrayalPlan plan, SymbolRole role)
        implements SymbolSelector {
    /** Validates the plan and role. */
    public RuleSymbolSelector {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(role, "role");
        if (role == SymbolRole.LEGACY_GEOMETRY) {
            throw new IllegalArgumentException("legacy role is unsupported");
        }
    }
}
