# G14-006 — MapLibre fixtures, gallery, and hardening

Status: Proposed
Depends on: G14-005
Gate: G14
Type: HITL

## Goal

Harden the bounded MapLibre reader/binder and approve provenance-backed interoperability fixtures and
a runnable vector-style gallery.

## Context

G14-005 completes the supported JVM behavior. Public styles commonly require remote sources,
sprites, glyphs, vector tiles, or unsupported layer/expression types, which must produce honest
bounded outcomes rather than accidental partial rendering.

## Scope

Add checksummed provenance fixtures, reduced legally reusable MapLibre examples, hostile JSON and
deterministic mutation, complete limit/diagnostic/rollback tests, a gallery for literals, filters,
categories, interpolation, zoom, icons, and labels, and tolerant render regression.

## Out of scope

Widening the profile to accommodate fixtures, live network tests, browser/GL pixel parity, full style
validation, new renderer types, or optimization without measured evidence.

## Acceptance criteria

- Every independent fixture records origin/license/modifications and an exact supported/diagnostic
  result.
- Duplicate keys, hostile nesting/strings/numbers, unsupported sources/layers/operators, cancellation,
  and rollback remain bounded and stable.
- Deterministic mutation exposes no raw Jackson/runtime exception or partially attached bindings.
- The gallery visibly demonstrates all supported layer and expression families.
- Maintainer review records interoperability and visual dispositions without full-compatibility claims.

## Required tests

Manifest verification, public-reader/binder oracles, hostile JSON, mutation, cancellation/cleanup/
rollback, diagnostic precedence, gallery EDT behavior, tolerant rendering, and manual comparison.

## Validation

```bash
./gradlew :modules:mundane-map-io-maplibre-style-jackson:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G14 MapLibre interoperability-profile and gallery approval**. Rejected valid
MapLibre features are documented exclusions, not hardening defects.
