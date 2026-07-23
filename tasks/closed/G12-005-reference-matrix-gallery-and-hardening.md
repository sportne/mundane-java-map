# G12-005 — Reference matrix, gallery, and hardening

Status: Complete
Depends on: G12-004
Gate: G12
Type: HITL

## Goal

Harden the bounded MIL-STD-2525 profile and visually approve a provenance-backed reference matrix and
runnable symbol gallery.

## Context

G12-004 completes the proposed supported inventory. The standard permits nuanced frame, color,
status, icon, and modifier behavior that requires authoritative manual comparison in addition to
portable rendering assertions.

## Scope

Add checksummed fixture/provenance manifests, table-integrity validation, malformed/unsupported SIDC
matrices, deterministic mutation, allocation-limit tests, a runnable 2525 gallery, and tolerant
render-regression coverage for identities, statuses, palettes, entities, modifiers, and fallbacks.

## Out of scope

Copying standard figures without permission, pixel-perfect screenshots, new symbol sets, tactical
graphics, text amplifiers, performance optimization without evidence, or broad conformance wording.

## Acceptance criteria

- Every approved table row is covered by structural checks and at least one reference family oracle.
- Provenance and redistribution rights are recorded for every non-hand-built fixture.
- Hostile values and table inconsistencies yield stable bounded diagnostics without partial symbols.
- The gallery names the supported profile and visibly demonstrates all major supported variations.
- Portable regression assertions pass and the maintainer records the visual/reference disposition.

## Required tests

Inventory completeness, deterministic mutation, limit/diagnostic precedence, tolerant rendering,
gallery construction/EDT behavior, license/provenance verification, and manual reference comparison.

## Validation

```bash
./gradlew :modules:mundane-map-symbology-milstd2525:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G12 MIL-STD-2525 reference matrix and gallery approval**. Review uses the named
authoritative revision and records deviations or rejected cases before closeout.

The maintainer approved this named checkpoint through the 2026-07-23 execution authorization. The
accepted result is the bounded project-authored reference matrix and tolerant gallery evidence; it
does not claim pixel identity or complete MIL-STD-2525 conformance.
