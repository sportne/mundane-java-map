# G19-167 — Canonical flat and normalized MBTiles builders

Status: Proposed
Depends on: G19-162, G19-164, G19-166
Gate: G19
Type: AFK

## Goal

Create new MBTiles 1.3 raster, vector, and UTFGrid tilesets through safe builder-driven canonical layouts.

## Context

The writer decision requires ergonomic creation without exposing SQL or forcing callers to populate metadata
that the adapter can derive. Both simple flat storage and deduplicated normalized storage are useful.

## Scope

- Provide immutable builders for one new tileset using either a canonical flat `tiles` table or canonical
  normalized map/images storage with a compatible `tiles` view and exact approved indexes.
- Derive application ID, schema, format, bounds/center/zoom summaries, vector metadata, UTFGrid tables,
  timestamps/version defaults and safe SQLite settings; require only non-derivable identity/payload facts.
- Accept bounded raw validated tiles, decoded raster capability inputs, MVT domain tiles, and UTFGrid values
  without implicit media transcoding, reprojection, retiling, generalization, or data fetching.
- Preflight paths, existing-target policy, metadata/payload homogeneity, counts/bytes/work and write to a
  private temp file with integrity verification, fsync and atomic installation.

## Out of scope

- Multiple tilesets, arbitrary schemas/SQL, custom trigger generation, implicit image encoding, and overwriting
  an existing file without an explicit transactional replacement policy.

## Acceptance criteria

- Both canonical layouts are valid logical MBTiles 1.3 interfaces and open in independent consumers.
- Defaults are deterministic, standards-valid and completely documented; callers can override only safe typed fields.
- Any failure/cancellation leaves no published target or ambiguous recoverable temp state.

## Required tests

- Flat/normalized, raster/vector/UTFGrid/raw, sparse/empty, metadata default/override, dedup/index and reopen matrix.
- Existing path/symlink, invalid payload/metadata, duplicate coordinate, format conflict, count/byte/work boundaries,
  disk-full/I/O/cancellation/fsync/rename/crash simulations, permissions and exact cleanup tests.

## Validation

Run MBTiles builder, independent consumer and filesystem fault lanes, then qualityGate and `git diff --check`.

## Notes

None.
