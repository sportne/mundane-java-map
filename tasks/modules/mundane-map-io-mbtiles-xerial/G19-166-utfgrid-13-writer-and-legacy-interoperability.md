# G19-166 — UTFGrid 1.3 writer and legacy interoperability

Status: Proposed
Depends on: G19-165
Gate: G19
Type: AFK

## Goal

Write deterministic UTFGrid 1.3 JSON/gzip and MBTiles grid storage for legacy round-trip compatibility.

## Context

Read-only support cannot preserve or create complete interactive archives during MBTiles rewrite and update.
The writer must be deterministic and bounded without encouraging UTFGrid as a new application model.

## Scope

- Build immutable bounded grids/keys/data and deterministically assign IDs, encode Unicode code points,
  rows, semantic JSON, gzip headers/trailers, and optional embedded data.
- Write/reconcile exact `grids` and `grid_data` rows, TMS coordinates, JSON objects, uniqueness, lookup
  equivalence, metadata rows, and unknown unrelated content.
- Support direct grid authoring and explicit bounded sampling from caller-provided immutable key/data
  cells; do not rasterize arbitrary geometry or infer tooltip semantics.
- Enforce prospective compressed/inflated/output/database/transaction limits, cancellation and atomic sinks.

## Out of scope

- HTML generation, JavaScript APIs, network serving/fetching, geometry-to-grid rasterization, and modern
  interaction integration beyond explicit read/write/lookup APIs.

## Acceptance criteria

- All representable UTFGrid 1.3 values write/reopen deterministically and preserve lookup semantics.
- Independent legacy consumers accept generated grids and database storage.
- Invalid/unrepresentable/over-budget values fail before changing a file or exposing partial output.

## Required tests

- Read/write/reopen and independent-consumer goldens across dimensions, resolutions, code-point/key/data boundaries.
- Determinism, maximum-key, escaping/Unicode/JSON number, no-data, database ordering, cancellation, sink failure,
  huge output, rollback and cleanup tests.

## Validation

Run MBTiles UTFGrid writer/consumer checks, then qualityGate and `git diff --check`.

## Notes

None.
