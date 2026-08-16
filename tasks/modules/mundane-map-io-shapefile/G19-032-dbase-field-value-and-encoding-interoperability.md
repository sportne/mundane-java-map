# G19-032 — dBASE field, value, memo, and encoding interoperability

Status: Proposed
Depends on: G19-002
Gate: G19
Type: HITL

## Goal

Complete the bounded common dBASE III/IV read profile used by interoperable Shapefile datasets.

## Context

The current reader accepts a small version and `C`/`N`/`F`/`L`/`D` scalar subset with several
explicit encodings. Real datasets exercise additional scalar/date-time representations, memo DBT
sidecars, deletion/null conventions, language drivers, and conflicting CPG declarations.

## Scope

- Pin exact dBASE III/IV header/version, field-type, deletion, terminator, null, date/time, numeric,
  and memo-file profiles in `modules/mundane-map-io-shapefile/CAPABILITIES.md`.
- Decode the approved common scalar and memo types into bounded structured attributes without
  evaluating or interpreting memo content.
- Implement bounded DBT block/header/chain validation for the approved memo variants.
- Complete a fixed code-page inventory and deterministic CPG-versus-language-driver precedence.
- Define duplicate field-name, field-name normalization, deleted-row, blank/null, numeric overflow,
  scale, invalid date/time, and unmappable-text behavior.

## Out of scope

- A general dBASE/FoxPro engine, indexes, relations, expressions, arbitrary vendor field types, or
  memo emission by the Shapefile exporter.

## Acceptance criteria

- The pinned field/version/encoding/memo matrix interoperates with at least two independent
  Shapefile producers and readers.
- Field widths, row counts, decoded characters, memo blocks/chains, metadata, and allocations are
  charged prospectively under one aggregate query/open budget.
- Ambiguous encodings, duplicate sidecars, malformed values, and conflicting declarations produce
  deterministic value-safe diagnostics without guessing.

## Required tests

- Cross-producer version/field/null/deleted-row/date-time/numeric/logical/text/code-page/memo corpus.
- Malformed memo headers/chains/cycles, hostile widths/counts, duplicate names/sidecars, conflicting
  declarations, invalid text, truncation, cancellation, allocation, and stable-diagnostic tests.

## Validation

Run `./gradlew :modules:mundane-map-io-shapefile:check --console=plain`, its approved DBF corpus lane,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact dBASE/memo/code-page profile, corpus provenance,
and compatibility evidence before completion.
