# G12-002 — Canonical SIDC model and parser

Status: Proposed
Depends on: G12-001
Gate: G12
Type: AFK

## Goal

Create a working JDK-only MIL-STD-2525 module that parses, canonicalizes, and classifies approved
30-position SIDCs without creating rendering behavior prematurely.

## Context

G12-001 fixes the exact Revision E Change 1 fields and support tables. Parsing must preserve
syntactically valid unsupported values so malformed syntax and unsupported profile content remain
different outcomes.

## Scope

Create `modules/mundane-map-symbology-milstd2525`; add immutable identifier/profile/exception/problem
values, fixed-offset packed parsing, canonical uppercase output, support classification, Javadocs,
architecture rules, publication inventory, and hand-built field-boundary fixtures.

## Out of scope

Vector rendering, palettes, feature portrayal, legacy identifiers, APP-6 translation, files or
network input, mutable tables, reflection, or runtime resource discovery.

## Acceptance criteria

- Every approved field round-trips through one exact canonical 30-position identifier.
- Lowercase input follows the approved normalization policy; invalid length/characters fail stably.
- Valid unsupported version, set, entity, and modifier values remain inspectable and classify
  without being silently remapped.
- The new published module is AWT-free, JDK-only, immutable, explicitly inventoried, and tested.

## Required tests

Field offset/value tests, canonicalization/equality/hash tests, malformed and unsupported matrices,
defensive-copy tests where applicable, Javadocs, architecture tests, and publication metadata tests.

## Validation

```bash
./gradlew :modules:mundane-map-symbology-milstd2525:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Module creation is justified by working public SIDC parse/classification behavior and tests; this is
not an empty symbol catalog scaffold.
