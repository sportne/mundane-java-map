# G19-121 — Edition-aware SIDC models and codecs

Status: Proposed
Depends on: G19-120
Gate: G19
Type: AFK

## Goal

Parse, validate, model, and deterministically format complete current and selected legacy symbol identifiers without edition ambiguity.

## Context

The released public value accepts a 30-position hexadecimal 2525E identifier but the supported profile rejects most field values;
2525D/C and APP-06E/D have no explicit edition-tagged values or codecs.

## Scope

- Add explicit immutable edition/profile identifiers and typed values/codecs for 2525E C1, APP-06E, 2525D C1,
  APP-06D, and 15-character 2525C SIDCs.
- Decode/validate every field, applicable/reserved/deprecated combination, scheme/context/identity/status, symbol set/
  battle dimension, entity/function, modifiers/amplifiers, frame, country/entity, order-of-battle, and edition differences.
- Require explicit edition where syntax is ambiguous; support strict and assessment modes without guessing by length/content alone.
- Format canonical current identifiers and lossless-only requested legacy identifiers after complete representability preflight.
- Bound input, diagnostics, parsing/catalog lookups, and batch operations; avoid echoing hostile identifiers in diagnostics.

## Out of scope

- Cross-edition semantic translation, rendering, and 2525A/B.

## Acceptance criteria

- Every valid/reserved/deprecated code in each frozen inventory receives the documented deterministic result.
- Parse-format round trips preserve all fields within an edition; invalid/applicability failures identify stable positions/reasons.
- Formatting never clears/truncates/substitutes non-representable fields.

## Required tests

- Exhaustive edition/field/value/applicability/reserved/deprecated parse-format inventory and reference examples.
- Ambiguous syntax, malformed/hostile input, batch/diagnostic limits, no-value-leak, API compatibility, and fuzz tests.

## Validation

Run `./gradlew :modules:mundane-map-symbology-milstd2525:check --console=plain`, corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
