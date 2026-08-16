# G19-202 — Native semantic and hostile-corpus parity

Status: Proposed
Depends on: G19-201
Gate: G19
Type: HITL

## Goal

Execute equivalent successful, malformed, limit, diagnostic, ownership, and cleanup behavior for every native-targeted capability on every supported host.

## Context

Building an image establishes reachability, not semantic parity. The existing smoke covers valuable selected workflows but does not provide a mechanically complete mapping from each advertised module capability and hostile-input family to native evidence.

## Scope

- Define a versioned shared native corpus and assertion manifest tied to the module capability matrices.
- Cover API/core geometry, CRS, portrayal, querying, editing, rendering, formats, symbology, workspace, and world-wrap behavior that is in the native target set.
- Exercise successful read/write/round-trip behavior where the owning profile includes writing.
- Exercise malformed/truncated/conflicting input, every declared hard limit, stable diagnostics without input leakage, cancellation, close, and exact ownership release.
- Require semantic outcomes to match the JVM oracle while allowing only documented platform representation differences.

## Out of scope

- Replacing an owning module's authoritative conformance suite or claiming unsupported adapters through a smoke fixture.

## Acceptance criteria

- A generated coverage report maps every native-targeted capability to native assertions and reports no unexplained gaps.
- All supported hosts produce equivalent normalized semantic/diagnostic/lifecycle results for the shared corpus.
- Corpus provenance, licenses, checksums, sizes, generators, and platform-specific additions are reviewable and offline reproducible.

## Required tests

- Full success/round-trip and hostile corpus on all five host rows, JVM/native differential assertions, and coverage-manifest failure fixtures.
- Repeated cancellation, failure, and close tests with leak/resource-count evidence.

## Validation

Run the full native corpus matrix, `./gradlew nativeSmoke --console=plain`, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: approve corpus provenance and any explicitly tolerated platform-dependent representation.
