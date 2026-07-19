# G12-001 — MIL-STD-2525 profile and legal decision

Status: Proposed
Depends on: G2-007
Gate: G12
Type: HITL

## Goal

Approve one exact MIL-STD-2525E Change 1 icon-based point-symbol profile, its authoritative code
tables, redistribution posture, diagnostics, and honest conformance wording.

## Context

The G12 design proposes 30-position hexadecimal SIDCs and a finite Land Unit, Land Equipment, and
Activities inventory rendered through existing symbols. The active standard is the 2025-03-02
Revision E Change 1 document listed by DoD ASSIST.

## Scope

Review `design/G12-milstd2525-symbology.md`; freeze the supported SIDC fields, standard identities,
statuses, symbol sets, entity inventory, graphical modifiers, palettes, degraded-display policy,
limits, diagnostic precedence, reference-fixture provenance, module name, and support statement.

## Out of scope

Production code or module creation; complete 2525 conformance; APP-6 conversion; multipoint tactical
graphics; METOC; text amplifiers; remote catalog updates; or embedding standard figures without
explicit redistribution rights.

## Acceptance criteria

- The profile names the exact standard revision and every supported and rejected symbol family.
- Every SIDC field has parse, retain, support-classification, and failure behavior.
- The finite entity/modifier inventory and light/dark palettes have authoritative references.
- Fixture and generated-table provenance is redistributable and independently reviewable.
- Public wording says “supported MIL-STD-2525E profile,” not complete conformance.
- G12-002 through G12-006 remain reviewable one-to-five-day vertical slices.

## Required tests

No production tests. Review representative valid, unsupported, degraded, malformed, and palette
cases against the authoritative standard and audit the proposed dependency/module boundary.

## Validation

```bash
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G12 MIL-STD-2525E profile, legal, and conformance-wording approval**. A maintainer
must approve the exact tables and redistribution record before G12-002 begins.
