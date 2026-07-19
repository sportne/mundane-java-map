# G11-021 — Graduated and complete-role portrayal

Status: Proposed
Depends on: G11-020
Gate: G11
Type: AFK

## Goal

Complete attribute-driven marker, line, and fill portrayal with exact graduated thresholds while
keeping query, rendering, and interaction decisions identical.

## Context

G11-020 establishes fixed/categorical marker selection. G11-002 defines lower-inclusive normalized
numeric steps, role-homogeneous selectors, omission semantics, and authoritative-geometry behavior.

## Scope

Extend `mundane-map-api`, `mundane-map-core`, and `mundane-map-awt` with graduated selectors and all
three portrayal roles; deduplicate required attribute names in marker/line/fill order and integrate
resolution with source projection, paint, hit, hover, click, overlays, and extent behavior.

## Out of scope

Labels, expression evaluation, parsing numeric text, style inheritance, viewport predicates,
geometry filtering, retained selected-symbol caches, and a second dispatch mechanism.

## Acceptance criteria

- Strictly increasing normalized thresholds select the greatest lower bound at or below each finite
  numeric value; below-range, missing, null, and wrong-type values use declared fallback/omission.
- Marker, line, and fill selectors reject cross-role rule/fallback symbols and respect their rule/step
  ceilings.
- Source queries use one ordered unique attribute projection, while fit/extent remains independent of
  portrayal and requests no attributes.
- Paint, hit, hover, click, and selection overlays agree on resolved presence and symbols for every
  geometry role.

## Required tests

Graduated boundary/order/fallback tests, role and count invariants, required-field deduplication,
known/dynamic schemas, all geometry-role binding paths, interaction agreement, omission behavior,
extent independence, and architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

An omitted symbol hides presentation and pointer hit only; it does not remove source identity,
geometry, extent, or a programmatically retained selection.
