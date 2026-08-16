# G19-122 — Exhaustive symbol catalog and search

Status: Proposed
Depends on: G19-120, G19-121
Gate: G19
Type: AFK

## Goal

Expose complete immutable current/legacy catalog metadata and bounded deterministic discovery for point symbols and tactical graphics.

## Context

The public catalog has a tiny exact table and does not expose the standard hierarchy, applicability, geometry kind,
control-point rules, replacements, or edition relationships needed by palettes, editors, and translation.

## Scope

- Model edition, hierarchy/path, code, official name/description, point/tactical kind, geometry family, valid identities/statuses,
  fields/modifiers/amplifiers, control-point/parameter rules, deprecation/replacement, and provenance reference.
- Expose exact code/identifier lookup plus normalized name/keyword, hierarchy, edition, symbol set, kind, applicability, and
  replacement queries with deterministic locale-independent matching and ordering.
- Provide lazy/paged/limited immutable results and reproducible catalog snapshots without leaking internal generated storage.
- Bound query text/tokens/results/work/allocations and batch enumeration; reject regex, fuzzy explosions, and ambient locale changes.
- Keep edition differences explicit and prevent display names from becoming canonical identity.

## Out of scope

- Tactical recommendation/search ranking, online catalog updates, and full-text search frameworks.

## Acceptance criteria

- Every generated inventory entry is reachable exactly once through its canonical key and classified correctly.
- Searches are deterministic, bounded, edition-aware, and return stable identities/provenance.
- Reserved/unrenderable/deprecated entries remain inspectable without being falsely advertised as renderable.

## Required tests

- Exhaustive catalog-to-source/code cross-checks and lookup/filter/search/order/paging/applicability/replacement matrices.
- Unicode/locale/long-query/large-result/work limits, deterministic snapshots, concurrency, and API documentation.

## Validation

Run `./gradlew :modules:mundane-map-symbology-milstd2525:check --console=plain`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

None.
