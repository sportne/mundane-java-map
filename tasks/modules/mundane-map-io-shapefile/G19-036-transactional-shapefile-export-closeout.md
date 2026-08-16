# G19-036 — Transactional Shapefile export closeout

Status: Proposed
Depends on: G19-034, G19-035
Gate: G19
Type: HITL

## Goal

Publish a complete new Shapefile component set transactionally and close the reader/exporter
capability profile with independent interoperability evidence.

## Context

SHP, SHX, DBF, CPG, and PRJ are one logical dataset but ordinary filesystems do not provide a native
multi-file atomic rename. A useful exporter must define preflight, temporary ownership, replacement,
recovery, failure, and cleanup behavior rather than exposing a partially updated basename.

## Scope

- Add the public create-new export facade, immutable options/result values, explicit replacement
  policy, cancellation contract, stable diagnostics, limits, and Javadocs.
- Write the complete component set to a private same-filesystem temporary location, flush it, reopen
  it through the reader for structural verification, and commit through a documented recoverable
  protocol.
- Preserve an existing target set on failure; reject ambiguous extra target components rather than
  mixing old/new files; clean temporary/backup files across every ordinary exceptional path.
- Define exact behavior when atomic moves, directory synchronization, replacement, cleanup, or crash
  recovery guarantees are unavailable on a filesystem.
- Update package/root documentation and obtain independent expert review of the final standards and
  supported/unsupported matrices.

## Out of scope

- In-place record mutation, append/update APIs, automatic heterogeneous splitting, proprietary
  spatial indexes, memo emission, or silent lossy conversion.

## Acceptance criteria

- Success exposes one coherent independently readable dataset; handled failure/cancellation exposes
  either the intact prior dataset or no new dataset, never a mixed component generation.
- Repeated export/replacement/close cycles leak no file handles, temporary sets, backups, workers, or
  source ownership under supported filesystem behavior.
- Public documentation describes a reader plus create-new exporter and names the precise exclusions;
  an external Shapefile reviewer records no untracked gap in that declared profile.

## Required tests

- Fresh export, explicit replacement, reader-reopen, two independent GIS readers, deterministic
  corpus, and reader/exporter capability-matrix tests.
- Failure injection at every create/write/flush/reopen/move/replace/cleanup boundary, cancellation,
  concurrent target conflict, unsupported-filesystem, stale-temp recovery, permission, disk-full,
  ownership, and process-restart recovery tests.

## Validation

Run `./gradlew :modules:mundane-map-io-shapefile:check --console=plain`, its approved publication and
corpus lanes, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer and independent Shapefile reviewer approve the commit/recovery model,
filesystem claims, interoperability corpus, capability matrix, and final support wording.
