# G13-006 — SE native and publication closeout

Status: Complete
Depends on: G13-005
Gate: G13
Type: HITL

## Goal

Publish and consume the bounded OGC SE adapter and prove secure parse, rule evaluation, catalog
resolution, and rendering in the required Linux Native Image lane.

## Context

G13-005 approves the complete JVM behavior and fixture profile. The adapter is JDK-only and must
remain directly constructed without XML provider discovery or implicit resources.

## Scope

Complete Javadocs/support tables, staged publication and offline consumer scenarios, aggregate native
smoke with a literal SE resource and malformed case, exact resource/module inventories, and G13
holistic review of the shared portrayal bridge.

## Out of scope

Remote publication, OGC certification, SLD/WMS, Windows/macOS Native claims, additional symbolizers,
schema bundles, or a second XML/style model.

## Acceptance criteria

- Staged API/core/SE artifacts and documentation expose no StAX types outside the adapter.
- A clean Java 21 offline consumer reads and applies one rule-based style using a supplied catalog.
- Linux Native Image proves supported parse/evaluate/render and one exact XML/security diagnostic.
- Documentation states the precise SE 1.1 subset and exclusions.
- G13 closeout confirms MapLibre can reuse the bridge without SE dependencies or concepts.

## Required tests

Module/Javadoc checks, artifact/POM/source inspection, offline consumer, resource inventory,
shared JVM/native probes, architecture boundaries, and manual support-wording review.

## Validation

```bash
./gradlew :modules:mundane-map-io-se:check --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G13 Linux Native Image and support-statement closeout**. G14 begins only after this
task so its JSON adapter consumes a proven standards-neutral portrayal boundary.
