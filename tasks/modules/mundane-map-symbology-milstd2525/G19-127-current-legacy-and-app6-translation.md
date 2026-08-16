# G19-127 — Current, legacy, and APP-06 translation

Status: Proposed
Depends on: G19-124, G19-126
Gate: G19
Type: HITL

## Goal

Provide exhaustive directional, loss-audited translation among 2525E C1, APP-06E, 2525D C1, APP-06D, and 2525C.

## Context

Operational datasets span SIDC generations and NATO/U.S. variants. Treating identifiers as equivalent hides renamed,
split, merged, absent, differently portrayed, or differently controlled symbols.

## Scope

- Add generated directional mappings for point/tactical entries and every field: identity/status, entity/function,
  amplifiers/modifiers, country/order-of-battle, geometry/control points/parameters, and portrayal differences.
- Return exact, canonical-normalization, conditional, lossy, ambiguous, or unmapped outcomes with stable reasons,
  candidates, source provenance, and complete loss audit.
- Require explicit policy for conditional/lossy/ambiguous mapping; preserve original edition/identifier/graphic for audit.
- Guarantee exact semantic round trips and lossless-only legacy formatting; never truncate/clear/substitute silently.
- Bound mapping candidates, audit records, batch work, retained source values, and diagnostics; avoid sensitive value echo.

## Out of scope

- 2525A/B, unofficial national/vendor extensions, and claiming visual/semantic equivalence where standards differ.

## Acceptance criteria

- Every source inventory row and valid field combination has one deterministic mapping classification/reason.
- Exact mappings round-trip; every non-exact mapping requires policy and exposes all losses/ambiguities before output.
- Translated point/tactical portrayals match the selected target edition, not the source edition with a changed SIDC.

## Required tests

- Exhaustive directional coverage, exact round trips, conditional/lossy/ambiguous/unmapped policy, and legacy formatting.
- Cross-edition point/tactical goldens, generated-table provenance/conflicts, batch/diagnostic limits, and audit hygiene.

## Validation

Run module translation/rendering/corpus lanes, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves all mapping sources, conflict dispositions, loss taxonomy, and representative visuals.
