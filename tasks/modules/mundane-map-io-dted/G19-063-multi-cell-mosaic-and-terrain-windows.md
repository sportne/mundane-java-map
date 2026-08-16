# G19-063 — Multi-cell DTED mosaics and terrain windows

Status: Proposed
Depends on: G19-012, G19-062
Gate: G19
Type: AFK

## Goal

Serve bounded seam-aware elevation windows and mosaics across catalogued cell and level boundaries.

## Context

The catalog locates cells but expert consumers need one deterministic regional terrain result with
controlled output registration, resolution, reprojection, interpolation, void, and missing coverage.

## Scope

- Plan prospective source windows and output dimensions before opening or allocating source data.
- Implement exact-post assembly and explicit nearest/bilinear resampling through shared core raster-
  warping primitives rather than private projection math.
- Freeze shared-edge ownership, mixed-level precedence/resampling, void propagation, missing-cell
  fill/mask behavior, and partial-result policy.
- Stream/chunk large results with cancellation, source-window/cache reuse, and aggregate source/read/
  output/work/allocation limits.
- Preserve source-cell provenance and diagnostic reports in the resulting terrain metadata.

## Out of scope

- Surface reconstruction, void filling/interpolation beyond the declared sample policy, and an
  unbounded virtual worldwide grid.

## Acceptance criteria

- Cross-cell windows have no duplicated/dropped boundary posts and follow deterministic mixed-level
  and void semantics.
- Requests are either complete according to the chosen missing-data policy or fail atomically; no
  staging failure publishes a partial unintended mosaic.
- Large regions use bounded windows/chunks and release all cell/cache resources on cancellation.

## Required tests

- Two/four-cell seams, mixed levels, high-latitude spacing zones, dateline, missing/partial/void cells,
  exact and interpolated requests, reprojection, cancellation, overflow, aggregate limits, and cache
  lifecycle.
- Threshold-free regional read/allocation evidence in the performance lane.

## Validation

Run `./gradlew :modules:mundane-map-io-dted:check --console=plain`, terrain corpus/performance lanes,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

No additional human checkpoint is required beyond normal code review.
