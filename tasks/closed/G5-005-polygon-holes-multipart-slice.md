# G5-005 — Polygon holes and multipart slice

Status: Complete
Depends on: G5-004
Gate: G5
Type: AFK

## Goal

Read and render shapefile polygons with disjoint shells, holes, and multipart records while rejecting
violations of the approved bounded structural profile and making its non-goals explicit.

## Context

G5-001 fixes the bounded structural polygon profile and diagnostic vocabulary. G5-004 supplies the
two-phase common multipart reader and packed payload. G4 supplies singular and packed multipart
polygon values plus component-local even-odd rendering. This task makes shape code 5 current without
widening any public surface.

## Scope

- Package-private `PolygonDecoder` beside the existing common multipart reader in
  `modules/mundane-map-io-shapefile`
- Shape-code 5 dispatch, bounded ring classification, singular/packed multipart mapping, and exact
  diagnostics
- Existing `examples/shapefile-viewer` fill rendering for temporary polygon fixtures
- Hand-built fixtures for shells, holes, disjoint parts, and invalid rings

## Out of scope

- General polygon repair, topology editing, overlay operations, and Z/M values
- General within-ring simplicity plus shell-shell/hole-hole overlap validation beyond the approved
  hole-association checks
- New public API, a topology framework, a polygon subpackage, an object-per-ring classifier model, or
  format recovery/record skipping
- Scale-dependent simplification or clipping

## Acceptance criteria

- Header and non-null record shape code 5 are current; other record types, Z/M, and MultiPatch retain
  the approved mismatch/unsupported behavior.
- The G5-004 scalar-plan, complete count-derived allocation reservation, and common materialization
  sequence is reused with a four-coordinate ring minimum. Full part/coordinate/ring validation
  precedes query filtering and geometry publication.
- Exact canonical closure, fixed-width packed binary64 signed-area/predicate accumulation, clockwise
  shell/counterclockwise hole orientation, and no-repair behavior implement the G5-001 profile without
  epsilon or magnitude-loss scaling.
- Multiple disjoint shells and holes within the same record remain distinct, preserve source record
  identity, and render with holes unfilled.
- Shells retain source order; each hole is assigned to the smallest strictly containing shell and
  retains source order within it. Orphan, any non-disjoint hole-shell edge/vertex contact or crossing,
  equal-innermost, open, short, zero-area, or otherwise undecidable associations reject the whole
  record; consecutive duplicate vertices are accepted when the whole ring remains nonzero-area.
- Every containment/segment comparison is prospectively charged against the approved topology-work
  limit, cumulative for the cursor. Cancellation is checked at most every 4,096 high-level
  comparisons and controlled exact-limb/edge operations before a quadratic hostile case can run
  unbounded.
- Structurally valid self-crossing rings or overlapping shell-shell/hole-hole peers outside the stated
  validity boundary are preserved/rendered under even-odd rules without a simple-polygon validity
  claim; this acceptance is pinned by tests rather than mistaken for unimplemented repair.
- Before common materialization, a checked reservation derived only from part/point counts covers
  common payload, packed classifier state, repacking, singular per-ring coordinate copies/reference
  slots, and multipart packed defensive copies; it is never refunded.
- One shell maps to `PolygonGeometry`; several shells map to `MultiPolygonGeometry`. Shell order and
  hole order are preserved through a linear packed grouping pass with no parser ring/candidate
  carrier objects.
- Open, short, and zero-area rings use exact `SHAPEFILE_RING_INVALID` reasons; orphan, contact, and
  equal-innermost holes use exact `SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS` reasons. The whole record and
  cursor fail; no malformed ring or record is skipped.
- Offscreen rendering verifies topology using sampled interior/exterior regions or path winding,
  including component-local even-odd behavior, not a full-image golden comparison.

## Required tests

- Hand-built fixtures for a simple shell, shell with hole, multiple shells/holes, nested clockwise
  islands, source-disordered shells/holes, accepted consecutive duplicates, and every ambiguous
  containment/topology-limit rejection.
- Boundary fixtures for signed-zero closure plus deliberately accepted self-crossing/peer-overlap
  cases outside the structural validity profile, extreme normal/subnormal exact-area/predicate signs,
  and equal absolute-area comparisons.
- Negative tests for open/short/empty rings, invalid part indices, non-finite coordinates, ambiguous
  holes, bound-touch and first-point-outside crossings, truncated data, count/capacity/allocation/
  topology limits, cancellation inside area/predicate limb loops, and cursor cleanup/reuse.
- Ordinary fixtures cover achievable topology minus/equal/plus-one boundaries; a direct unit test of
  `PolygonDecoder`'s package-private checked increment pins `Long.MAX_VALUE` saturation/reporting.
- Source/query tests proving malformed off-query polygons still fail and singular/multipart packed
  order and `record:<ordinal>` identity remain stable.
- Source-to-render integration tests proving holes remain transparent, disjoint shells render, gaps
  remain clear, nested islands paint, and independently declared components are not unioned.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not add a general-purpose topology engine. If the bounded algorithm cannot classify a record
under the approved profile, terminate that cursor with the stable diagnostic rather than guessing,
repairing, or skipping it. Corpus, render-regression, fuzz, native, and performance lanes remain with
their later owning tasks.

Completed on 2026-07-14 with a package-private polygon decoder that reuses the bounded multipart
reader, fixed packed exact-binary arithmetic, and cumulative topology accounting. Type-5 records now
map source-stable clockwise shells and counterclockwise holes to singular or packed multipart API
geometry; ambiguous associations fail rather than being repaired. Byte-authored, fault-injected, and
offscreen evidence covers exact ring diagnostics, hostile limits and cancellation, SHX equivalence,
accepted structural non-goals, transparent holes, disjoint shells, and nested islands.
