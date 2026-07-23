# G13-005 — SE fixtures, gallery, and hardening

Status: Proposed
Depends on: G13-004
Gate: G13
Type: HITL

## Goal

Harden the bounded SE reader and approve its interoperability fixtures and point/line/polygon rule
gallery against the documented profile.

## Context

G13-004 completes supported behavior. OGC examples and third-party styles often contain valid SE
features deliberately excluded from this profile, which must be diagnosed rather than ignored.

## Scope

Add provenance/checksum manifests for project-authored fixtures cross-referenced to reviewed OGC
examples and clauses, hostile XML and deterministic mutation, complete limit/diagnostic precedence
tests, a runnable SE gallery, and tolerant render-regression scenarios covering rules, scales,
filters, catalogs, and all three roles.

## Out of scope

Expanding the profile to make fixtures pass, complete OGC certification, SLD, network tests,
pixel-perfect images, Text/RasterSymbolizer, or new performance machinery.

## Acceptance criteria

- Project-authored fixtures have recorded license/provenance, OGC clause cross-references, and exact
  supported or diagnostic outcomes; OGC example XML is not copied.
- Every parser/evaluator limit and unsupported-profile branch has stable bounded evidence.
- Deterministic mutation cannot escape as raw XML/runtime exceptions or partial style output.
- The gallery visibly demonstrates order, filters, scales, external catalog markers, lines, and fills.
- Maintainer review records the supported-profile and visual disposition.

## Required tests

Fixture manifest verification, public parser oracles, XML security, mutation, cancellation/cleanup,
diagnostic precedence, gallery EDT/construction, tolerant rendering, and manual interoperability
review.

## Validation

```bash
./gradlew :modules:mundane-map-io-se:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G13 OGC SE interoperability-profile and gallery approval**. Unsupported valid SE
is an expected stable result, not a reason to widen scope during hardening.
