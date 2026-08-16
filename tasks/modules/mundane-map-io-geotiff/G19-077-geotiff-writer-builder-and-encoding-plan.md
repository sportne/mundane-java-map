# G19-077 — GeoTIFF writer builder and encoding plan

Status: Proposed
Depends on: G19-072, G19-073, G19-074
Gate: G19
Type: HITL

## Goal

Define an immutable builder and fully preflighted encoding plan for conventional tiled GeoTIFF and
COG geospatial imagery, raw raster bands, and elevation.

## Context

The module has no writer. A safe writer must distinguish lossless format choices from transformations
that alter samples, color, placement, CRS, no-data, or vertical meaning.

## Scope

- Add one builder for one primary geospatial dataset plus associated mask/overview IFDs.
- Require exact raster/band source, sample model, CRS/user-defined definition, PixelIsArea/Point
  placement, no-data/mask policy, and vertical semantics where applicable.
- Provide deterministic defaults for byte order, tile size, Deflate, predictor, software metadata,
  checked classic-versus-BigTIFF selection, and overview policy; expose explicit safe overrides.
- Support conventional tiled GeoTIFF and COG layout modes with separate conformance claims.
- Prospectively plan all tags/keys, IFDs, blocks, offsets, codecs, output bytes, work, allocation,
  cancellation checkpoints, and verification comparison before destination creation.
- Reject implicit reprojection/resampling, color conversion, quantization, no-data invention, or
  vertical interpretation; document use of core warping as an explicit preceding operation.

## Out of scope

- General multipage TIFF authoring, JPEG encoding, in-place updates, and arbitrary private tags.

## Acceptance criteria

- Minimal builders produce reproducible lossless plans with no invented geospatial/sample meaning.
- Every unsupported or lossy request fails during preflight before a destination is created.
- Classic/BigTIFF and conventional/COG decisions are explainable from immutable plan values.

## Required tests

- Default/override builder snapshots for imagery/raw/elevation, classic/BigTIFF boundary, all declared
  sample/CRS/placement/mask/no-data/vertical plans, incompatible/lossy inputs, overflow/work limits,
  immutability/equality, and public Javadocs.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, then `./gradlew qualityGate
--console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact builder required/default/override table and the
conventional-versus-COG conformance wording before completion.
