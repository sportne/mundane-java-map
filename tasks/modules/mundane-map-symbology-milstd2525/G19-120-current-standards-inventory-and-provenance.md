# G19-120 — Current standards inventory and provenance

Status: Proposed
Depends on: None
Gate: G19
Type: HITL

## Goal

Freeze lawful, reproducible, reviewable inventories for MIL-STD-2525E Change 1 and APP-06E before expanding parsers or artwork.

## Context

The current generated profile covers 15 entities and 7 modifiers. The original G19 cards referenced 2525E
and APP-06D broadly, but 2525E Change 1 and APP-06E are now the authoritative editions.

## Scope

- Acquire/record authorized source editions, changes/corrigenda, distribution/licensing, hashes, and review dates.
- Define normalized immutable source tables for identifier fields, symbol sets, entity/type/subtype hierarchies,
  statuses/identities, point/tactical classification, amplifiers/modifiers, control-point rules, and deprecations.
- Build deterministic offline generators with source page/table/row citations, normalization decisions, validation,
  duplicate/conflict detection, generator/output checksums, and reviewed manifests.
- Separate rule-derived project geometry from copied artwork and exclude any source lacking redistribution authority.
- Add inventory completeness/consistency reports and architecture rules preventing runtime PDF/resource scanning.

## Out of scope

- Rendering, interpretation of classified material, and acceptance of unofficial scraped tables as authority.

## Acceptance criteria

- Every target current-edition catalog row has a stable provenance record and deterministic generated representation.
- Conflicts, missing tables, reserved ranges, and edition differences are reported for human disposition.
- A clean checkout regenerates byte-identical approved tables without network access.

## Required tests

- Generator determinism, table/cardinality/range/reference integrity, duplicate/conflict/reserved/deprecated inventories.
- Hash/provenance/license/distribution manifests, clean/offline generation, and tamper/missing-source failure tests.

## Validation

Run the module generator/inventory checks and `:modules:mundane-map-symbology-milstd2525:check`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves standards access, redistribution/provenance, normalization decisions,
generated inventory reports, and every manual conflict disposition.
