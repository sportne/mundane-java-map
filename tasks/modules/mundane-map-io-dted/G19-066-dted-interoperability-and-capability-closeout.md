# G19-066 — DTED interoperability and capability closeout

Status: Proposed
Depends on: G19-063, G19-065
Gate: G19
Type: HITL

## Goal

Close the declared DTED reader, regional-access, metadata, and writer profile with independent
interoperability, hostile-corpus, lifecycle, and documentation evidence.

## Context

Self-round-trip tests cannot prove external interchange, and structural encoding does not certify
the source terrain or product for an operational distribution program.

## Scope

- Expand the provenance-locked corpus across L0/L1/L2, latitude zones, metadata/accuracy profiles,
  SRTM variation, partial cells, and independently produced files.
- Have at least one approved independent implementation read emitted cells and have the production
  reader/catalog read independently emitted cells; compare metadata, dimensions, posts, voids, and
  placement with documented tolerances.
- Add deterministic field/data mutation, truncation, trailing-byte, checksum, limit, cancellation,
  descriptor, and cleanup evidence across eager, windowed, catalog, mosaic, and writer paths.
- Reconcile package Javadocs, root support wording, publication/native policy, capability matrix,
  stable diagnostic inventory, and corpus licenses/hashes.
- Publish a conformance statement that precisely names supported behavior and disclaims product,
  security, source-quality, and positional-accuracy certification.

## Out of scope

- Claiming official NGA/NATO validation or testing with data that cannot be redistributed legally.

## Acceptance criteria

- Independent tools agree on every declared emitted Level/profile and the project agrees on every
  declared independent fixture.
- No capability matrix row is broader than automated/manual evidence and every deliberate exclusion
  is stable and documented.
- Full cleanup and bounded-work evidence covers success, rejection, cancellation, and injected
  failure for every public resource-owning path.

## Required tests

- Provenance/hash/license manifest, independent read/write comparisons, all-profile hostile corpus,
  catalog/mosaic and writer lifecycle, native/publication compatibility, and documentation inventory.
- Fresh corpus, performance, offline/publication, native, and normal quality lanes named by the final
  support statement.

## Validation

Run `./gradlew :modules:mundane-map-io-dted:check --console=plain`, `./gradlew dtedCorpus
--console=plain`, the affected performance/native/publication lanes, `./gradlew qualityGate
--console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer reviews the independent-tool observations, licenses/hashes, generated
product disclaimer, capability matrix, and exact public support wording before completion.
