# G11-002 — Thematic styling and label placement

Status: Proposed
Depends on: G8-004
Gate: G11
Type: HITL

## Goal

Choose a bounded first profile for categorical/graduated thematic styling and deterministic,
collision-aware label placement.

## Context

Level 1 establishes immutable symbols, catalogs, rendering, viewport behavior, and caching. Thematic
rules and labels can multiply complexity through expression languages, text metrics, collision order,
and platform-dependent rendering.

## Scope

Decide supported attribute value types, categorical matching, graduated interval semantics, missing or
invalid values, symbol selection, rule ordering, label text source, priority, anchors, offsets,
collision boxes, clipping, zoom visibility, stable ordering, cache invalidation, and diagnostic policy.
Define tolerant cross-platform visual review criteria and decompose styling and labeling into working
slices.

## Out of scope

Production APIs, arbitrary expression languages, scripting, locale-heavy formatting, advanced line or
curved labels, automated cartographic optimization, font bundling, 3D labels, and pixel-perfect golden
images.

## Acceptance criteria

- A maintainer approves categorical equality, graduated interval boundaries, missing-value behavior,
  and deterministic rule precedence.
- The initial label strategy defines candidate order, collision/tie handling, viewport clipping,
  supported geometry anchors, and platform-tolerant verification.
- Public values remain immutable and toolkit-neutral; text measurement and drawing remain in AWT.
- Follow-up tasks separately deliver an end-to-end thematic slice and a collision-aware label slice,
  with later refinements left in the backlog.

## Required tests

No production tests. Define later rule-boundary/property tests, stable ordering, missing-value,
collision/tie, zoom/clipping, cache invalidation, and tolerant rendering-regression scenarios.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer approves both bounded profiles and visual acceptance examples before
implementation tasks begin.
