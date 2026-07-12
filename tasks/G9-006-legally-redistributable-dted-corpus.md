# G9-006 — Legally redistributable DTED corpus

Status: Proposed
Depends on: G9-004
Gate: G9
Type: HITL

## Goal

Establish a small automated DTED Level 0/1/2 corpus whose provenance and redistribution terms are safe
for the repository and release artifacts.

## Context

Hand-built fixtures prove parser mechanics but not real producer variation. Corpus files may have
licensing or redistribution restrictions, so maintainers must approve provenance before they enter
history.

## Scope

Curate or generate at least one representative fixture for each DTED level, record source/provenance,
license or public-domain basis, original filename, SHA-256 digest, expected metadata, and expected
sample checks. Add deterministic `mundane-map-io-dted` corpus tests without network access.

## Out of scope

Downloading data during builds, large geographic coverage, paid or access-controlled datasets,
benchmark-scale generated files, parser feature expansion, and publishing a separate corpus artifact.

## Acceptance criteria

- A maintainer approves the recorded redistribution basis and intended repository location before any
  externally sourced binary fixture is added.
- Automated tests cover Level 0, 1, and 2 with checked metadata, representative elevations, void values
  where available, and stable digests.
- Corpus provenance distinguishes generated fixtures from unmodified or reduced external data and
  records every transformation reproducibly.
- The corpus is small enough for normal repository use and tests run entirely offline.
- Ambiguous-license data is excluded rather than committed with a disclaimer.

## Required tests

Corpus integration tests for all three levels, digest verification, metadata/sample assertions, and a
test that the provenance manifest accounts for every corpus file.

## Validation

```bash
./gradlew :modules:mundane-map-io-dted:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer must review and explicitly approve provenance, redistribution rights,
fixture size, and any transformation procedure before corpus files are committed. Do not substitute a
runtime download when redistribution cannot be established.

