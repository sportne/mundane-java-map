# G19-064 — DTED writer builder and metadata defaults

Status: Proposed
Depends on: G19-060
Gate: G19
Type: HITL

## Goal

Define an immutable builder that makes ordinary DTED cell creation practical while preventing
fabricated datum, accuracy, provenance, security, or grid claims.

## Context

DTED fixed records contain extensive metadata. Requiring every field would make a writer unusable,
but guessing material terrain facts would create misleading products. The module capability matrix
records which values are required, derived, defaulted, or optional.

## Scope

- Add a public immutable builder for one new Level 0/1/2 cell from a compatible immutable elevation
  grid or bounded sample provider.
- Require/derive one exact standard southwest origin, level, WGS84 placement, metre units, standard
  zone dimensions, and an explicit supported vertical datum.
- Implement the approved deterministic defaults for unclassified security, initial edition/version,
  product specification, unknown dates/accuracy, producer identity, blank provenance, and derived
  partial/structural fields.
- Add typed overrides for supported metadata with field-width/ASCII/range validation and coherent
  security, accuracy, SRTM, datum, edition, and partial-cell cross-checks.
- Freeze sample conversion, void, non-integral, range, cancellation, and write-work limit policies.
- Publish capability/Javadoc examples that distinguish format conformance from source certification.

## Out of scope

- A raw-header escape hatch, implicit reprojection/resampling/datum conversion, and certified
  production/quality-control claims.

## Acceptance criteria

- The minimal truthful builder requires only terrain/grid facts that cannot safely be invented; all
  resulting default metadata is documented and reproducible.
- No override can create an internally inconsistent fixed-record plan.
- Preflight rejects incompatible input before creating or replacing a destination.

## Required tests

- Minimal/default builder snapshots, every override and boundary, immutability/equality, unclassified
  and explicit marked profiles, absent/unknown semantics, incompatible grids/CRS/units/datum, sample
  range/void rules, work limits, and public Javadocs.

## Validation

Run `./gradlew :modules:mundane-map-io-dted:check --console=plain`, then `./gradlew qualityGate
--console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact required/derived/default/override table and confirms
that the public wording does not imply NGA, NATO, classification, or positional-accuracy approval.
