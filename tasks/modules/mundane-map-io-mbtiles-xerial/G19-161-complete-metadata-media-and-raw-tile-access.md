# G19-161 — Complete metadata, media, and raw tile access

Status: Proposed
Depends on: G19-002, G19-160
Gate: G19
Type: AFK

## Goal

Model every MBTiles 1.3 metadata rule and expose bounded raw tiles for every valid declared format.

## Context

MBTiles is a container and may hold payloads an implementation cannot render. Rejecting unknown valid
IETF media types makes the container reader incomplete; guessing their meaning would be unsafe.

## Scope

- Parse and retain required `name`/`format`, recommended bounds/center/minzoom/maxzoom, optional
  attribution/description/type/version, vector `json`, UTFGrid rows, and bounded unknown metadata.
- Define duplicate/order/UTF-8/numeric/domain/cross-row rules and semantic immutable structured JSON
  using explicitly constructed pinned Jackson Core with no dependency-type leakage.
- Accept standard `pbf`/`jpg`/`png`/`webp` and syntactically valid IETF media types; expose raw immutable
  tile bytes plus declared/sniffed compression/signature facts without implicit decoding.
- Validate tile uniqueness, coordinates/zooms/ranges, format consistency, gzip/signature prefixes,
  bounds/center/populated summaries, sparse/missing behavior, cache identity, limits, and lifecycle.

## Out of scope

- Rendering arbitrary formats, trusting attribution as HTML, executing metadata, TileJSON authoring,
  and retaining duplicate JSON members or unbounded unknown values.

## Acceptance criteria

- Every standard metadata row and valid raw payload is inspectable without being misrepresented as
  renderable; unknown metadata/media round-trip semantically through later writers.
- Contradictory declarations, duplicate governed metadata, invalid domains, corrupt compression, and
  over-budget content fail before cache/source publication.
- Jackson remains explicit and isolated to the optional adapter.

## Required tests

- Metadata required/recommended/optional/unknown/duplicate/UTF-8/number/bounds/center/zoom/type/version/
  attribution/vector/UTFGrid matrix and raw standard/custom media fixtures.
- JSON hostile inputs, media/signature/gzip conflicts, duplicate tiles, huge blobs/rows/strings/nodes,
  cancellation/cache/lifecycle, independent producers, dependency/offline and API-leak tests.

## Validation

Run MBTiles metadata/raw checks and dependency verification, then qualityGate and `git diff --check`.

## Notes

None.
