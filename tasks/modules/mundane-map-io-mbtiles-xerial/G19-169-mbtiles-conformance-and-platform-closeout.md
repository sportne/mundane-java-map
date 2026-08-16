# G19-169 — MBTiles conformance and platform closeout

Status: Proposed
Depends on: G19-168
Gate: G19
Type: HITL

## Goal

Close the MBTiles adapter only after independent MBTiles 1.3, MVT 2.1, UTFGrid 1.3, transaction, dependency,
and supported-platform evidence agrees with the module capability matrix.

## Context

Container, payload and writer cards can each pass while leaving mismatched limits, metadata, schema, lifecycle,
platform, or support claims. Xerial and JSON/WebP optional graphs also require a fresh deployment audit.

## Scope

- Map every applicable MBTiles 1.3, MVT 2.1 and UTFGrid 1.3 clause to read/write/update/non-applicability evidence.
- Run provenance-recorded official and independent producer/consumer corpora across table/view/flat/normalized,
  raster/raw/vector/grid, create/edit/rewrite and unknown-preservation behavior.
- Reconcile all entry points, limits, diagnostics, cancellation, caches, ownership, transactions, recovery,
  rendering/browser examples, package Javadocs, README and `CAPABILITIES.md`.
- Audit exact Xerial/Jackson/optional WebP artifacts, mechanisms, checksums/licenses, JPMS/public API, offline/
  publication/staged consumer behavior and the supported JVM/OS/architecture matrix.
- Run hostile SQLite/SQL/schema/payload/JSON/protobuf/gzip/image/fuzz, concurrency, locking, disk-full/crash and soak evidence.

## Out of scope

- Claiming MBTiles profiles, payload codecs, SQLite extensions, platforms, serving, or schema mutations outside
  the named matrix and evidence.

## Acceptance criteria

- External MBTiles/vector-tile reviewers find no untracked standard feature or misleading exclusion.
- Independent tools read project output and project code reads their conforming output across all named profiles.
- Full ordinary/specialized/platform evidence is green and public wording exactly matches implemented behavior.

## Required tests

- Re-run every predecessor matrix/corpus/fuzz/fault/recovery/determinism/consumer test from clean inputs.
- Offline repository, publication dry-run, staged consumer, dependency/license, Javadoc, example, AWT/Vaadin render,
  JVM/platform and long transaction/query soak lanes.
- Independent source/API/docs/security review with findings fixed or explicitly resolved in the matrix.

## Validation

Run all predecessor and specialized lanes, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: maintainers and independent MBTiles/MVT reviewers approve the matrices, corpora, dependency/platform
audit, write/recovery policy, evidence report and final support wording.
