# G19-140 — Pinned document model and global state

Status: Proposed
Depends on: G19-001, G19-002
Gate: G19
Type: HITL

## Goal

Model and validate the complete MapLibre Style Specification v26.2.1 document, root, common layer,
global state, and preserved non-renderable 3D surface.

## Context

The current adapter exposes a narrow vector-style subset and rejects most root/global members. The
upstream document still declares version 8 while its independently versioned vocabulary evolves.

## Scope

- Pin release v26.2.1/commit `7a2420b` and generate checked root/source/layer/property/operator inventories.
- Add immutable bounded JSON/style values for every root, common layer member, metadata, camera default,
  transition, state declaration, light, terrain, sky, projection, reference, and cross-reference.
- Validate complete type/range/default/required/dependency/mutual-exclusion and ordered-layer semantics.
- Preserve fill-extrusion and non-2D global constructs for deterministic writing; make the 2D binder return
  stable non-renderable-capability results without partial scene publication.
- Define registered extension ownership and reject unknown/future constructs by default.
- Bound JSON depth/nodes/members/strings/numbers, styles/sources/layers/properties, references, and owned work.

## Out of scope

- 3D terrain/extrusion/model/sky/fog/globe rendering and accepting post-v26.2.1 vocabulary implicitly.

## Acceptance criteria

- Generated inventory accounts for every pinned root/common/global construct and fails on unexplained drift.
- Complete documents round-trip semantically, including preserved non-renderable constructs.
- Invalid references/types/limits and 3D bind attempts fail atomically with stable diagnostics.

## Required tests

- Generated root/common/global/type/default/reference matrices and official reference examples.
- Unknown/future/extension collisions, non-renderable combinations, deep/wide/large JSON, and exact limits.

## Validation

Run the module generated-inventory/corpus checks, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: approve the exact v26.2.1 inventory artifact, license/provenance, and 2D/3D claim boundary.
