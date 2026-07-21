# G11-040 — Programmatic SVG map-export slice

Status: Complete
Depends on: G11-005, G11-022
Gate: G11
Type: AFK

## Goal

Encode and atomically write a canonical static SVG 1.1 picture from a detached programmatic vector
snapshot containing background, solid point/line/polygon portrayal, and placed point labels.

## Context

G11-005 approves the API-owned snapshot boundary, strict supported/rejected profile, canonical SVG
grammar, diagnostics, limits, and atomic file policy. G11-022 supplies public label/style values.

## Scope

Add immutable snapshot/view-frame/primitive/label/limit/problem values to `mundane-map-api`; extend
the existing `mundane-map-io-svg` module with `SvgExportLimits`, export problems/exceptions, and
`SvgMapExports`; support canonical background, singular-point vector markers, solid lines, solid
polygon fills/outlines, labels, encode, and atomic write from programmatic snapshots.

## Out of scope

Live AWT capture, composites, endpoint markers, hatches, raster/elevation/custom/raster-icon/legacy
fallback, SVG import round-trip, full hardening/fault injection, PDF/print, or a new export module.

## Acceptance criteria

- Snapshot values are immutable, ordered, role-checked, bounded, AWT-free, and retain only detached
  screen geometry, supported symbols, stripped label values, page/view data, and ordinals.
- Equal valid snapshots emit byte-identical UTF-8 SVG with fixed declaration/order/clips, canonical
  numbers/colors/opacity/XML escapes, no source metadata, and no external or active content.
- Programmatic singular-point/line/polygon and label paint order matches the approved profile; any
  unsupported descendant rejects the complete operation without raster fallback.
- Atomic output materializes valid bytes first and preserves the existing target on pre-move failure.

## Required tests

API immutability/order/role/accounting, programmatic canonical-byte repeatability, solid geometry and
label ordering, XML scalar/escaping/security, unsupported profile, basic limits/cancellation, atomic
write/replacement, and SVG architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-io-svg:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Extend `mundane-map-io-svg`; do not add another module or generic exporter/DOM. Its approved
production dependencies are exactly API, core, and `java.xml`, never AWT.

Completed on 2026-07-20 with immutable detached singular-geometry snapshots, canonical programmatic
SVG encoding, structured limits/diagnostics, cancellation, failure-atomic local replacement, and
API/SVG/architecture coverage. Complete built-in traversal and live AWT capture remain G11-041.
