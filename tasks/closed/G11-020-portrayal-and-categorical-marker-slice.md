# G11-020 — Portrayal and categorical marker slice

Status: Complete
Depends on: G11-002, G11-010
Gate: G11
Type: AFK

## Goal

Choose fixed or categorical point-marker symbols from immutable feature attributes consistently in
snapshot, source, and editable bindings.

## Context

G11-002 approves one binding-owned closed `FeaturePortrayal`, exact thematic values, immutable
selector rules, explicit renderer preflight, and no expression or callback language. G11-010 provides
the editable binding consumed here.

## Scope

Add portrayal, fixed/categorical selector, thematic-value, and categorical-rule values to
`mundane-map-api`; implement immutable resolution in `mundane-map-core`; add portrayal overloads and
marker-role integration to `mundane-map-awt` for snapshot, source, and editable bindings.

## Out of scope

Graduated selectors, line/fill roles, point labels, mutable style setters, expressions, callbacks,
viewport-dependent rules, label hit testing, or a new styling module.

## Acceptance criteria

- Text, logical, normalized numeric, date, and null categories have exact approved equality; missing,
  unsupported, and unmatched attributes use only the declared fallback or omission.
- Construction rejects duplicate normalized categories, wrong roles, invalid fields, or excessive
  rules before attachment.
- Source queries request exactly the required marker attribute, and paint/hit/hover/click selection
  resolve the same marker from one captured portrayal and record.
- Attachment recursively preflights every reachable symbol renderer and value compatibility.

## Required tests

Value normalization/equality, copy/count/role invariants, fallback/omission, known/dynamic schema,
exact attribute projection, renderer preflight, snapshot/source/editable binding, interaction
agreement, and architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

This is a closed portrayal value, not a styling language or renderer registry. Changing portrayal
continues to mean transactionally replacing the binding.
