# G19-149 — MapLibre interoperability and capability closeout

Status: Proposed
Depends on: G19-148
Gate: G19
Type: HITL

## Goal

Close the v26.2.1 complete-document and complete-2D-rendering profile with external expert evidence.

## Context

Feature completeness requires exact generated inventory coverage, reference validation, independent styles/resources,
visual evidence, security/lifecycle proof, and public wording that distinguishes interchange from 3D rendering.

## Scope

- Produce an item-by-item v26.2.1 root/source/layer/property/expression requirement matrix with evidence or exclusion.
- Run the reference validator/expression fixtures and provenance-recorded independent style/resource corpora.
- Compare complete 2D output against MapLibre GL JS with declared geometry/color/text/placement/raster tolerances.
- Exercise online/offline resources, legacy migration, writing, 3D preservation/non-renderability, updates/transitions,
  AWT/Vaadin/SVG/hit behavior, hostile/fuzz inputs, all limits, cancellation, caches, ownership and cleanup.
- Verify optional Jackson isolation, JDK-only core boundaries, native/offline/publication/dependency/license/JPMS/Javadoc,
  examples, capability docs, diagnostics, and generated upstream-drift detection.

## Out of scope

- Adding post-v26.2.1 features or broadening the approved 2D/3D, media-codec, or authority boundary during closeout.

## Acceptance criteria

- Every `CAPABILITIES.md` row is implemented/tested or deliberately excluded with no accidental ignored construct.
- Independent tools accept deterministic output and reference comparisons support the exact 2D claims.
- Public wording clearly claims complete pinned interchange and 2D rendering, not 3D/GPU/application parity.

## Required tests

- Full generated/reference/corpus/visual/security/lifecycle/limit/fuzz matrix and independent consumer round trips.
- Native Image, offline repository, publication dry run, API/Javadoc, dependency/license, AWT/Vaadin/SVG examples.

## Validation

Run all module/interoperability/render/security lanes, qualityGate, applicable offline/native/publication lanes,
and `git diff --check`.

## Notes

HITL checkpoint: an external MapLibre expert approves the frozen inventory, evidence, deviations, and support wording.
