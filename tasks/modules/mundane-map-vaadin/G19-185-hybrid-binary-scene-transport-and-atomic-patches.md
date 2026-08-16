# G19-185 — Hybrid binary scene transport and atomic patches

Status: Proposed
Depends on: G19-184
Gate: G19
Type: AFK

## Goal

Replace full JSON-only transfer with bounded JSON control, exact packed resources, atomic generation patches and full recovery.

## Context

Full JSON replacement multiplies allocation and transfer for large G19 geometry, labels and renderer buffers. A private binary
lane must not weaken closed validation, atomic scene acceptance or simple resynchronization.

## Scope

- Version closed JSON manifests and project-owned binary layouts for coordinates, indexes, glyph/layout data, images and
  backend buffers with fixed byte order/types/alignment/count/offset and exact media/length/digest profiles.
- Serve binary data only through same-origin session/component/generation-owned expiring resources; keep all remote protocols,
  credentials, redirects, caching and decoder authority on Java/server lanes.
- Define stable add/remove/replace/reorder patch operations from exactly one acknowledged base, prospective complete-candidate
  validation, old/new resource ownership transfer and one visible/hit/interaction commit.
- Select a full snapshot for generation gaps, reconnect, unsupported versions, missing/failed resources, excessive chains,
  failed validation or uneconomic patches; never ask the client to infer a base.
- Bound manifests/resources/operations/identities/bytes/allocation/decompression/validation/concurrency/retained generations and
  report logical/control/binary/full/patch/transferred/retained costs separately.

## Out of scope

- Java serialization, a public binary/geospatial protocol, arbitrary compression/codecs, direct remote URLs or partial paint.

## Acceptance criteria

- Full and patched publication yield identical accepted scenes, hits and resources; failures preserve the prior generation.
- Any lost/reordered/duplicated/stale/corrupt message converges through one bounded full snapshot without leaked resources.
- Patch selection demonstrates material transfer/allocation benefit under a frozen policy and never increases unbounded work.

## Required tests

- Golden byte-layout and cross-language decoder fixtures; full/patch equivalence, economic-selection and recovery tests.
- Truncated/oversized/overlapping offsets, hostile counts/digests/media, stale/reordered/gapped messages, load abort, registrar/
  unregister failures, reconnect/detach/session close and repeated replacement soak tests.

## Validation

Run Vaadin Java/frontend protocol, resource, browser and performance evidence lanes, then qualityGate and `git diff --check`.

## Notes

None.
