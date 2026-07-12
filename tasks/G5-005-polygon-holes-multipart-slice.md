# G5-005 — Polygon holes and multipart slice

Status: Proposed
Depends on: G5-004
Gate: G5
Type: AFK

## Goal

Read and render shapefile polygons with disjoint shells, holes, and multipart records without losing
topology or accepting malformed rings silently.

## Context

Polygon records share SHP multipart framing with G5-004 but require ring closure, orientation, and
containment interpretation. The existing API polygon behavior and G4 multipart geometry contracts
are the output boundary.

## Scope

- Polygon record decoding and ring classification in `modules/mundane-map-io-shapefile`
- Multipart polygon mapping and shapefile-viewer rendering
- Hand-built fixtures for shells, holes, disjoint parts, and invalid rings

## Out of scope

- General polygon repair, topology editing, overlay operations, and Z/M values
- Scale-dependent simplification or clipping

## Acceptance criteria

- Part tables, coordinate payloads, ring closure, minimum ring size, finite ordinates, and configured
  limits are checked before geometry publication.
- The G5-001 orientation policy is implemented; ring orientation and containment are used
  deterministically to associate holes with shells.
- Multiple disjoint shells and holes within the same record remain distinct, preserve source record
  identity, and render with holes unfilled.
- Ambiguous containment, orphan holes, repeated closing points, empty parts, and self-evidently
  invalid rings follow documented reject/skip behavior with stable record-and-part diagnostics.
- Checked arithmetic prevents count/byte-size overflow, and packed coordinates avoid per-point
  object allocation.
- Offscreen rendering verifies topology using sampled interior/exterior regions or path winding,
  not a full-image golden comparison.

## Required tests

- Hand-built fixtures for a simple shell, shell with hole, multiple shells, multiple holes, and
  nested/disordered rings allowed by the profile.
- Negative tests for open/short/empty rings, invalid part indices, non-finite coordinates, ambiguous
  holes, truncated data, and allocation limits.
- Source-to-render integration tests proving holes remain transparent and disjoint shells render.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not add a general-purpose topology engine. If the bounded algorithm cannot classify a record
under the approved profile, fail or skip it with a diagnostic rather than guessing.
