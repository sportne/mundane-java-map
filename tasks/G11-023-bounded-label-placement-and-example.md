# G11-023 — Bounded label placement and example

Status: Proposed
Depends on: G11-021, G11-022
Gate: G11
Type: HITL

## Goal

Place point labels with a bounded deterministic collision policy and demonstrate thematic portrayal
and collision-aware labels through a runnable map example.

## Context

G11-021 completes role portrayal and G11-022 supplies label requests/metrics. G11-002 fixes the
priority/topmost greedy order, declared-position fallback, half-open clipping, fixed limits, stable
failures, and tolerant visual evidence.

## Scope

Implement `GreedyPointLabelPlacement` and bounded operation inputs/results in `mundane-map-core`;
integrate all-or-nothing collection/placement/drawing in `mundane-map-awt`; create
`examples/styling-label-viewer`; add tolerant `renderRegression` scenarios.

## Out of scope

Spatially indexed placement, retained label caches, dynamic decluttering, geometry-reserved space,
label hit testing, arbitrary fonts/text layout, pixel hashes, or cross-platform glyph identity.

## Acceptance criteria

- Requests are admitted by descending priority then topmost paint ordinal, choose the first declared
  in-bounds non-colliding position, and return accepted labels in ordinary paint order.
- Edge/corner touching does not collide; positive-area overlap does; request, candidate, code-point,
  text-budget, and comparison ceilings fail before a partial label pass.
- Stable `LABEL_*` problems contain only the approved bounded contexts and never label text or source
  diagnostics.
- The example and tolerant regression prove selector decisions, label count/order, broad ink regions,
  non-overlap, clipping, and final overlay order on one named desktop.

## Required tests

Core ordering/collision/clipping/limit/overflow/failure tests with deterministic metric stubs; AWT
full-stack source/editable/snapshot tests; rendering regression and manual example review.

## Validation

```bash
./gradlew :modules:mundane-map-core:check :modules:mundane-map-awt:check :examples:styling-label-viewer:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 styling and label gallery visual review**. The maintainer reviews one named
desktop run for readable broad placement behavior without approving pixel-identical glyph output.
