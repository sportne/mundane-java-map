# G19-013 — Line and polygon cartographic labels

Status: Proposed
Depends on: G19-002, G19-011
Gate: G19
Type: AFK

## Goal

Extend deterministic label layout from point labels to line-following and polygon/interior labels.

## Context

Point-only placement is insufficient for road, river, administrative area, and advanced SE/MapLibre
text portrayal.

## Scope

- Define line repetition, curvature, uprightness, offset, and grapheme placement rules.
- Add polygon interior-point, multi-part priority, fit, and fallback placement.
- Integrate collision, priority, wrap copies, stable IDs, and bounded candidate generation.
- Keep text measurement injectable and renderer-neutral.
- Document deterministic ordering and tolerance contracts.

## Out of scope

- A full international shaping engine in core; shaping remains an explicit adapter capability.

## Acceptance criteria

- Layout is deterministic across threads and viewport-equivalent inputs.
- Candidate generation and collision work are prospectively bounded.
- World-wrap labels do not duplicate or jump contrary to the frozen placement policy.
- AWT and Vaadin consume the same accepted layout decisions.

## Required tests

- Straight/curved/reversed line and concave/holed/multipart polygon fixtures.
- Collision, wrap, Unicode/grapheme, tiny/huge geometry, and limit tests.
- Cross-renderer image/operation parity evidence.

## Validation

Run `./gradlew :modules:mundane-map-core:check --console=plain`, rendering-regression tests, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
