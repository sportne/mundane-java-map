# G19-075 — Local GeoTIFF random access, overviews, and windows

Status: Proposed
Depends on: G19-012, G19-071, G19-072, G19-073, G19-074
Gate: G19
Type: AFK

## Goal

Replace whole-file snapshots with bounded local random access and select deterministic overview/block
windows for imagery, raw bands, and elevation.

## Context

The current facade reads the complete local file before parsing, despite later decoding only
intersecting blocks. Large GeoTIFF and COG workflows require metadata and block range planning.

## Scope

- Add an explicit file/channel random-access abstraction with immutable identity/length validation,
  external serialization, cancellation, and exactly-once ownership.
- Read bounded headers/directories/tag payloads and only blocks intersecting the selected request.
- Select full-resolution or associated overviews deterministically from requested resolution,
  resampling policy, mask availability, and work limits.
- Add bounded encoded/decoded block and metadata caches with mutation invalidation and no whole-file
  retention requirement.
- Integrate orientation, affine placement, raw-band, display, elevation, and core warp windows.

## Out of scope

- HTTP, COG layout validation, and ambient recursive file discovery.

## Acceptance criteria

- A small window from a large local TIFF/BigTIFF reads and allocates only bounded metadata and
  selected blocks.
- Overview choice is deterministic and value/placement parity holds against full-resolution reads
  within the selected resampling tolerance.
- Mutation, cancellation, and failure close descriptors/caches exactly once and never publish mixed
  file versions.

## Required tests

- Stripped/tiled, classic/BigTIFF, planar/chunky, orientation, overview/mask, edge, sparse, affine,
  raw/display/elevation windows; byte-count observations, mutation, short/zero reads, concurrency
  policy, cache eviction, cancellation, descriptor and aggregate limits.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, focused allocation/read
evidence, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
