# G5-005 — Polygon holes and multipart slice

Status: Proposed
Depends on: G5-004
Gate: G5
Type: AFK

## Goal

Read and render shapefile polygons with disjoint shells, holes, and multipart records while rejecting
violations of the approved bounded structural profile and making its non-goals explicit.

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
- General within-ring simplicity plus shell-shell/hole-hole overlap validation beyond the approved
  hole-association checks
- Scale-dependent simplification or clipping

## Acceptance criteria

- Part tables, coordinate payloads, ring closure, minimum ring size, finite ordinates, and configured
  limits are checked before geometry publication.
- The G5-001 orientation policy is implemented; ring orientation and containment are used
  deterministically to associate holes with shells.
- Multiple disjoint shells and holes within the same record remain distinct, preserve source record
  identity, and render with holes unfilled.
- Shells retain source order; each hole is assigned to the smallest strictly containing shell and
  retains source order within it. Orphan, hole-to-candidate-shell edge/vertex touching, equal-
  innermost, open, short, zero-area, or otherwise undecidable associations reject the whole record;
  consecutive duplicate vertices are accepted when the whole ring remains nonzero-area.
- Every containment/segment comparison is prospectively charged against the approved topology-work
  limit and cancellation cadence before a quadratic hostile case can run unbounded.
- Structurally valid self-crossing rings or overlapping peer shells/holes outside the stated validity
  boundary are preserved/rendered under even-odd rules without a simple-polygon validity claim; this
  acceptance is pinned by tests rather than mistaken for unimplemented repair.
- Checked arithmetic prevents count/byte-size overflow, and packed coordinates avoid per-point
  object allocation.
- Offscreen rendering verifies topology using sampled interior/exterior regions or path winding,
  not a full-image golden comparison.

## Required tests

- Hand-built fixtures for a simple shell, shell with hole, multiple shells/holes, nested clockwise
  islands, source-disordered shells/holes, accepted consecutive duplicates, and every ambiguous
  containment/topology-limit rejection.
- Boundary fixtures for signed-zero closure plus deliberately accepted self-crossing/peer-overlap
  cases outside the structural validity profile.
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
