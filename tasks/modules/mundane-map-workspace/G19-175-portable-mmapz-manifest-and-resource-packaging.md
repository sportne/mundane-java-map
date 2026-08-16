# G19-175 — Portable `.mmapz` manifest and resource packaging

Status: Proposed
Depends on: G19-174
Gate: G19
Type: HITL

## Goal

Define and implement the custom bounded `.mmapz` ZIP form with one canonical v2 XML manifest and explicit
portable resource sets.

## Context

Standalone XML is readable but external paths are not portable. A package is useful only if its entry grammar,
complete sidecar closure, media declarations, limits and extraction authority are exact and secure.

## Scope

- Freeze package version, media type, required manifest/entry names and order, canonical path grammar,
  ZIP methods/features, timestamps/attributes, platform normalization and deterministic writer output.
- Require explicit per-resource `EMBED` or `REFERENCE`; copy complete registered local file/sidecar sets as opaque
  media-typed bytes and retain guarded references otherwise. Never fetch a remote resource automatically.
- Validate central/local records, flags, sizes/offsets/CRC, names, compression, descriptors/ZIP64 policy, undeclared/
  duplicate/case/Unicode aliases, symlinks/special files and manifest relationships before extraction/open.
- Extract/write only through private confined storage with prospective entry/compressed/inflated/ratio/aggregate/
  temp/owned-byte/I/O/work limits, cancellation and exact cleanup.
- Preserve the same v2 semantic document between `.mmap.xml` and `.mmapz` without package-specific application state.

## Out of scope

- Claiming a standard container, generic ZIP APIs, nested packages, encryption, signatures, automatic downloads,
  transcode/reprojection, sidecar inference and partially embedding an adapter-declared resource set.

## Acceptance criteria

- Explicitly embedded workspaces open portably on supported filesystems and referenced forms retain guarded semantics.
- Equivalent XML/package projects restore the same neutral state and deterministic package inputs yield identical bytes.
- Every hostile/ambiguous/over-budget archive fails before exposing any unverified entry or partial workspace.

## Required tests

- XML/package equivalence, embed/reference, all registered resource-set/sidecar/media relationships, deterministic ZIP,
  sparse/empty/large package and supported OS/filesystem fixtures.
- ZIP slip/bomb/ratio/duplicate/case/Unicode/absolute/backslash/dot/symlink/special/encrypted/flag/CRC/size/offset/
  local-central/descriptor/ZIP64/nesting attacks, limits, cancellation, disk/I/O failure and cleanup tests.

## Validation

Run workspace package/security/platform and representative adapter resource tests, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve the custom package grammar/media type, ZIP feature subset, resource-closure inventory,
limits and portable filesystem matrix before publishing `.mmapz`.
