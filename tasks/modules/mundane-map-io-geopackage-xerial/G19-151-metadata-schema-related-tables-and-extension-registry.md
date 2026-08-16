# G19-151 — Metadata, schema, related tables, and extension registry

Status: Proposed
Depends on: G19-150
Gate: G19
Type: HITL

## Goal

Implement official metadata, schema/data-column constraints, Related Tables, and scoped extension management.

## Context

The released extension-free reader neither exposes these standard tables nor safely distinguishes supported,
unknown, required, deprecated, and unrelated extension state during updates.

## Scope

- Implement `gpkg_metadata` and references, scopes, MIME/URI, parent relations and bounded typed codecs.
- Implement `gpkg_data_columns` plus range/enum/glob constraints and exact validation/default/null semantics.
- Implement Related Tables base/related/mapping tables for media, simple attributes, features, tiles and user relations.
- Build a frozen official-extension registry with name/definition/scope/table/column/cardinality/version validation.
- Preserve unknown extension declarations and untouched objects; block governed mutations absent an explicit codec.
- Recognize deprecated extensions for diagnostics/preservation but never emit them.
- Bound metadata/constraints/relations/media/codecs/schema inspection/owned bytes and aggregate work.

## Out of scope

- Dynamic handler discovery, package-supplied SQL, automatic extension migration, and unsupported community semantics.

## Acceptance criteria

- Official extension/catalog/reference requirements and independent related-table datasets pass.
- Unknown unrelated objects survive edits; package/table/column-scoped unsafe mutations fail before transaction start.
- Metadata, constraints and relations remain referentially consistent under create/update/delete preparation.

## Required tests

- Metadata scope/reference/MIME, constraint kind/value, relation/cardinality/cascade and registry matrices.
- Unknown/deprecated/malformed/conflicting extensions, hostile schema/media, codecs, limits and preservation tests.

## Validation

Run module/official-extension/related-table checks, qualityGate, and `git diff --check`.

## Notes

HITL checkpoint: approve official/unknown/deprecated extension policy and the explicit codec contract.
