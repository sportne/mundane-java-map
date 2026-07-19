# G12-004 — Land/activity catalog and portrayal

Status: Proposed
Depends on: G12-003, G11-024
Gate: G12
Type: AFK

## Goal

Complete the approved finite Land Unit, Land Equipment, and Activities point-symbol inventory and
select those symbols from one explicit feature SIDC attribute.

## Context

G12-003 proves the native symbol path. G11-024 closes binding-owned portrayals, required-attribute
projection, consistent paint/hit selection, labels, publication, and native foundations.

## Scope

Implement the approved entity/type/subtype and graphical sector-modifier tables; complete resolver
composition; add one SIDC attribute selector/portrayal adapter; integrate snapshot/source/editable
bindings; and add catalog inventory and interaction agreement tests.

## Out of scope

Text modifiers/amplifiers, automatic labels from military fields, non-approved symbol sets,
multipoint graphics, source-format parsing, mutable extension tables, or runtime downloads.

## Acceptance criteria

- Every approved SIDC inventory entry resolves once to the documented frame/icon/modifier layers.
- Duplicate codes, unreachable data, invalid paths, and role mismatches fail during table creation.
- Source queries project exactly the SIDC attribute and paint/hit/hover/select resolve identically.
- Unsupported or missing attribute values follow the approved omission/fallback diagnostic policy.
- Inventory access is immutable, deterministic, and independent of classpath resources.

## Required tests

Complete table enumeration, duplicate/reachability validation, representative path/component tests,
attribute normalization/projection, snapshot/source/editable binding, interaction agreement,
allocation limits, and architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-symbology-milstd2525:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The portrayal adapter remains module-owned and returns project portrayal/symbol values; military
types do not enter `mundane-map-api`.
