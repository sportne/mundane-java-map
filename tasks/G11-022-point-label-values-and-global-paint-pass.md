# G11-022 — Point-label values and global paint pass

Status: Proposed
Depends on: G11-020
Gate: G11
Type: AFK

## Goal

Measure and paint one toolkit-neutral label for eligible singular points in a global label pass using
one deterministic Java2D metric profile.

## Context

G11-002 fixes the label text/style/position/resolution values, compatibility-label migration,
annotation-only hit policy, fixed `SansSerif` metrics, and paint-stack location.

## Scope

Add point-label, text-source, style, resolution, screen-box, placed-label, and failure values to
`mundane-map-api`; implement text extraction and candidate geometry in `mundane-map-core`; add AWT
metric collection, compatibility migration, and a global post-geometry label paint pass.

## Out of scope

Collision admission, label caching, multiline/wrapped/rich/curved text, non-point labels, alternate
fonts, glyph export, label hit testing, backgrounds, halos, or renderer-dependent metrics.

## Acceptance criteria

- Public values enforce the approved text, size, color, position, offset, padding, priority, and
  inclusive resolution bounds with immutable ordered copies.
- Eligible labels use feature name or one exact text attribute; ordinary missing/blank/wrong-type or
  out-of-resolution cases omit without diagnostics.
- AWT measures through the fixed identity `FontRenderContext`, anchors from final marker bounds, and
  draws only after all geometry without changing hit footprints.
- Compatibility factories use the one approved internal profile rather than retaining the G1 label
  path.

## Required tests

API invariant tests, text extraction, all eight candidate alignments, inclusive resolution edges,
metric finite/failure behavior, exact source projection, compatibility migration, marker-bound
requirements, paint order, graphics-state isolation, and annotation-only hit tests.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

This slice establishes the one metric and paint path later consumed by placement and export. It must
not retain `TextLayout`, AWT values, or a last-layout cache outside the operation.
