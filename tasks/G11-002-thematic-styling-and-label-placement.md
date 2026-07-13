# G11-002 — Thematic styling and label placement

Status: Proposed
Depends on: G8-004
Gate: G11
Type: HITL

## Goal

Approve one implementation-ready, bounded portrayal profile for fixed, categorical, and graduated
symbols plus deterministic collision-aware singular-point labels.

## Context

Level 1 establishes immutable symbols, canonical scalar attributes, source query projection,
symbol-aware interaction, rendering order, and evidence-qualified private caches. G11-001 adds
editable immutable records. The detailed architecture is in
`design/G11-editing-styling-persistence-adapters-export.md`; G2, G3, G4, and G7 define the boundaries it
must preserve.

## Scope

Define immutable binding-owned portrayal values; exact categorical numeric normalization and fallback;
lower-inclusive graduated steps; role omission; required-attribute queries; singular-point text,
font, resolution, anchor, offset, priority, and candidate values; one bounded global greedy placement
pass; interaction and paint order; stable failures; cache policy; tolerant verification; and later
G11-020 through G11-024 vertical slices. Correct G11-005 so export depends on the placed-label contract.

## Out of scope

Production code or modules; task files for later slices; callbacks or arbitrary expression/rule
languages; binary categories; locale formatting; multipoint, line, polygon, curved, repeated, wrapped,
or rich labels; font selection/bundling; glyph hit testing; cartographic optimization; label caches;
and pixel-perfect golden images.

## Acceptance criteria

- `FeaturePortrayal` has closed fixed/categorical/graduated role selectors, exact normalized-numeric
  equality, greatest-lower-inclusive graduated selection, and explicit fallback/omission semantics.
- Source bindings request only the operation-required ordered selector/visible-label attribute union;
  every possible symbol renderer is preflighted; portrayal omission and
  existing ID selection, fit, hit, overlay, and diagnostic behavior are unambiguous.
- Labels are name- or text-attribute-derived singular-point annotations using logical `SansSerif`,
  normal/bold bounded sizes, positive-alpha color, eight ordered positions, inclusive resolution
  visibility, and no glyph hit behavior.
- The existing convenience label maps to the exact dark-gray, normal 12-pixel, north-east, four-pixel
  gap, one-pixel-padding, priority-zero, all-resolution profile; G2's mandatory marker bounds remain
  the sole anchor contract.
- Priority/topmost admission, declared-position fallback, positive-area collision, full-viewport
  containment, ordinary paint order, compatibility-label migration, request/candidate/comparison/text
  limits, and stable bounded-index failures are exact.
- Toolkit-neutral placement output can be consumed by later AWT-free export; `Font` and `TextLayout`
  remain confined to AWT, and no label/result cache is introduced without evidence.
- The detailed design defines G11-020 through G11-024 as working slices, including the G11-010
  editable-binding prerequisite and exact branch/join edges, and receives the named HITL approval
  before implementation tasks are created.

## Required tests

No production tests in this design task. Specify later API/core tests for normalization, boundaries,
fallbacks, ordering, placement, clipping, touching/overlap, limits, and failures; AWT integration tests
for query projection, paint/hit agreement, fonts, compatibility migration, and pass order; tolerant
`renderRegression`, example, performance, consumer, and representative Linux Native Image evidence.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 thematic and point-label profile approval**. The maintainer approves selector
matching/fallback, the singular-point text/font/position profile, global placement/paint order,
compatibility-label migration, annotation-only interaction policy, limits/failures, visual examples,
and the five-slice decomposition before G11-020 is created.
