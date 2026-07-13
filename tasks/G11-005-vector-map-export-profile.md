# G11-005 — Vector map export profile

Status: Proposed
Depends on: G10-001, G11-002
Gate: G11
Type: HITL

## Goal

Select one bounded vector map export format and define deterministic viewport-to-document behavior
before implementing export.

## Context

G10-001 establishes a secure static SVG import subset. Level 1 supplies toolkit-neutral paths,
symbols, raster icons, hatches, one compatibility point label, and map rendering; G11-002 defines the
general placed-point-label handoff. Export needs an explicit target profile and fallback policy;
rendering to a screen is not itself a portable vector document contract.

## Scope

Compare a static SVG-first profile with other justified targets and select one. Define viewport/page
bounds, coordinate precision, background, clipping, layer order, vector path/symbol mapping, placed-
label/text/font handling, raster-icon embedding or rejection, opacity/hatch behavior, metadata,
deterministic ordering, numeric formatting, unsupported-effect diagnostics, limits, and
reproducibility. Decompose the chosen writer into working export and verification tasks.

## Out of scope

Production export, arbitrary SVG round trips, PDF without an approved dependency strategy, interactive
documents, animation, remote resources, font embedding without license review, format data export,
printing, and silent rasterization of unsupported effects.

## Acceptance criteria

- A maintainer approves one export target, supported/rejected effect matrix, text/font policy, and
  raster fallback policy.
- The profile defines deterministic layer order, IDs where present, numeric formatting, metadata,
  clipping, and repeatable output for identical immutable inputs.
- Unsupported symbols/effects fail or degrade only according to an explicit diagnostic policy.
- Follow-up tasks cover a real viewport-to-file slice, structural assertions, tolerant render-back
  comparison, limits, malformed configuration, and Native Image expectations before creating a module.

## Required tests

No production tests. Define later structural/determinism tests, geometry/symbol/text cases, unsupported-
effect diagnostics, bounded output, and render-back comparisons that avoid pixel-perfect assumptions.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer selects the format and approves the effect, text/font, determinism, and
fallback policies before implementation tasks are created.
