# G14-007 — MapLibre native and publication closeout

Status: Proposed
Depends on: G14-006
Gate: G14
Type: HITL

## Goal

Publish and consume the bounded MapLibre style adapter and prove direct-Jackson parse, expression,
binding, icon/label, rendering, and diagnostic paths under Linux Native Image.

## Context

G14-006 approves complete JVM behavior and fixtures. G10-025 provides the Jackson service-exclusion
pattern; G13-006 provides the shared portrayal/native boundary.

## Scope

Complete Javadocs/support tables/notices, dependency-lock and shaded/service audits, staged
publication/offline consumer, aggregate native smoke with a literal style and malformed case, exact
resource/service inventories, and holistic G12–G14 symbology simplicity review.

## Out of scope

Remote publication, Mapbox compatibility claims, Windows/macOS Native claims, network/sprite/glyph
support, additional expressions/layers, or a second JSON/portrayal implementation.

## Acceptance criteria

- Staged artifacts contain expected docs/notices/locks and no AWT or Jackson leakage into public core.
- A clean Java 21 offline consumer parses and renders supported source, expression, icon, and label
  behavior from staged artifacts only.
- Linux Native Image proves direct parse/evaluate/bind/render and one exact malformed/unsupported
  diagnostic without service discovery or reflection metadata.
- Documentation states the exact v8 subset, resource policy, dependency, and platform limitations.
- Closeout confirms all three standards reuse existing symbols and one portrayal pipeline.

## Required tests

Module/Javadocs, dependency/license/service audits, artifact/POM/source inspection, offline consumer,
shared JVM/native probes, architecture inventories, and manual support-wording review.

## Validation

```bash
./gradlew :modules:mundane-map-io-maplibre-style-jackson:check --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G14 Linux Native Image, publication, and G12–G14 symbology closeout**. Broader
platform or full-spec claims require separate evidence and tasks.
