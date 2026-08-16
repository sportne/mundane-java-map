# G19-144 — Authorized resources, sprites, glyphs, and fonts

Status: Proposed
Depends on: G19-044, G19-050, G19-051, G19-140
Gate: G19
Type: HITL

## Goal

Resolve all MapLibre external resources through one explicit guarded online policy or complete offline catalog.

## Context

Sprites, glyphs, fonts, tiles, images, and source documents are currently rejected or descriptive only.
Their locators must not silently grant I/O, credentials, platform fonts, or code-execution authority.

## Scope

- Define immutable resource requests and caller policies for bases/origins/paths/schemes, redirects, headers,
  credentials, media, encoded/decoded sizes, deadlines, retry/cache/concurrency, cancellation, and ownership.
- Support relative resolution without base escape and a network-free catalog with identical response semantics.
- Implement single/multiple sprite definitions, ratios, JSON metadata, atlases, SDF/pattern/runtime images and updates.
- Implement glyph range and `font-faces` resources with explicit registered fonts, provenance/license metadata,
  format validation, cache isolation, fallback policy, and no OS/classpath/resource scanning.
- Share authorization with all source/resource cards while keeping detached read/write I/O-free.

## Out of scope

- Ambient URL fetching, platform font discovery, secret discovery, executable font features, and proprietary protocols.

## Acceptance criteria

- Every authorized resource is reproducible online and offline; unauthorized inputs perform no ambient I/O.
- Redirect/base/credential/cache isolation and replacement/close behavior are failure-safe and leak-free.
- Sprite/glyph/font outputs are validated and bounded before they enter portrayal or caches.

## Required tests

- Online/offline/base/origin/redirect/header/credential/media/cache/cancellation/resource-type matrices.
- SSRF/DNS/path/symlink/decompression/font/sprite attacks, exact limits, races, failures, and ownership cleanup.

## Validation

Run module/HTTP/resource security tests, qualityGate, relevant offline lane, and `git diff --check`.

## Notes

HITL checkpoint: approve the exact authority, credential, cache, font, and offline-catalog contracts.
