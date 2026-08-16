# G19-165 — UTFGrid 1.3 read, lookup, and MBTiles storage

Status: Proposed
Depends on: G19-002, G19-161
Gate: G19
Type: HITL

## Goal

Read complete bounded UTFGrid 1.3 payloads and MBTiles `grids`/`grid_data` interaction storage.

## Context

UTFGrid is optional and legacy, but it is the standardized MBTiles interactivity companion. Complete
archive interoperability requires exact lookup and metadata behavior without adopting it as a modern UI model.

## Scope

- Parse gzip-compressed UTFGrid JSON with directly constructed pinned Jackson: square power-of-two
  grid rows, Unicode code-point ID escaping, ordered keys, optional data and empty-key semantics.
- Implement exact top-left tile-pixel lookup, integer resolution/factor mapping, code-point decoding,
  the 65,501-key boundary, no-data behavior, and bounded lookup batches.
- Validate/read compatible `grids` and `grid_data` tables/views, TMS tile identity, gzip, key/value JSON
  objects, duplicates, missing values, and embedded-data/database-data reconciliation.
- Expose immutable structured values with no HTML/script/resource semantics; bound all compressed,
  inflated, grid, code-point, key, JSON, row, owned-byte, output, and work dimensions.

## Out of scope

- Network lookup for missing keys, UI tooltip rendering, trusted HTML, vector hit testing, and using
  UTFGrid as the project interaction/selection architecture.

## Acceptance criteria

- Official and independent UTFGrid/MBTiles fixtures return exact keys/data at every coordinate boundary.
- Empty/missing/conflicting data follows the frozen profile without fetching or executing anything.
- Malformed gzip/JSON/grid/code points/storage and over-budget work fail atomically and diagnostically.

## Required tests

- Grid dimension/resolution/row/code-point/key/empty/data/lookup/boundary and `grids`/`grid_data` schema matrices.
- Official 65,501-key fixture, producer databases, gzip/JSON bombs, malformed Unicode/IDs/rows, duplicates,
  conflicts, huge batches, cancellation, thread/close, mutation and stable-diagnostic tests.

## Validation

Run MBTiles UTFGrid reader and corpus checks, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve UTFGrid 1.3 legacy support wording, data reconciliation policy, limits, and corpus.
