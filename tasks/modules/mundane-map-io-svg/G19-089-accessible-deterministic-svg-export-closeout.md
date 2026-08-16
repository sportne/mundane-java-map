# G19-089 — Accessible deterministic SVG export and capability closeout

Status: Proposed
Depends on: G19-020, G19-088
Gate: G19
Type: HITL

## Goal

Generate accessible, deterministic, self-contained static SVG for the complete approved G19 map
portrayal profile and close the module with import round-trip and independent-renderer evidence.

## Context

Current canonical export covers the existing project snapshot but lacks the settled static SVG
profile, complete advanced portrayal/resources, precise accessibility contract, and semantic
round-trip proof.

## Scope

- Pin generated SVG 2 restricted-profile syntax, namespaces, language/direction, metadata, WAI-ARIA
  Graphics/accessibility behavior, decorative content, and privacy-safe feature identity hooks.
- Export all approved vector, text, raster, gradient/pattern, marker, clip/mask, compositing, and
  common-filter portrayal with deterministic IDs, definition deduplication, order, numeric formatting,
  color, and resource encoding.
- Embed approved font/raster resources only under explicit options and byte ceilings; never copy
  source paths, URLs, credentials, or private attributes implicitly.
- Preserve byte-identical output for identical snapshots/options and transactional filesystem output
  with cancellation/failure cleanup.
- Add export-import semantic comparisons, independent renderer/validator/accessibility evidence, and
  reconcile package/root support wording, examples, limits, diagnostics, and capability matrix.

## Out of scope

- Scripted/animated output, browser application export, ambient external resources, and claiming a
  complete dynamic SVG authoring tool.

## Acceptance criteria

- Generated documents validate against the declared restricted static profile and expose correct
  accessible names/descriptions/roles without leaking caller values.
- Repeated exports are byte-identical; import round-trip preserves supported semantics and
  independent renderers meet structural/numeric/text/filter/pixel tolerances.
- Failure/cancellation preserves previous targets and releases all staged/embedded resources.

## Required tests

- Golden documents for every portrayal/resource/accessibility construct, deterministic bytes,
  Unicode/hostile text, privacy, embedded-resource limits, transactional rollback, import round-trip,
  W3C/independent validation/rendering/accessibility, native/publication/offline, and documentation
  inventory.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, SVG/rendering/accessibility/native/
publication lanes, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the generated-profile/accessibility statement, independent
renderer/validator observations, embedded-resource/privacy policy, and exact public wording.
