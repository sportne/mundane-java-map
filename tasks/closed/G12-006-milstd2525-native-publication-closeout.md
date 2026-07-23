# G12-006 — MIL-STD-2525 native and publication closeout

Status: Complete
Depends on: G12-005
Gate: G12
Type: HITL

## Goal

Publish and consume the bounded MIL-STD-2525 module and prove representative parse, resolve, portray,
render, and diagnostic behavior under the required Linux Native Image lane.

## Context

G12-005 approves the inventory and visual evidence. This task closes distribution and reachability
without adding resource discovery, reflection metadata, or a broader platform claim.

## Scope

Extend Javadocs/support tables, staged publication and offline consumer scenarios, aggregate native
smoke with literal supported/unsupported SIDCs, exact architecture/artifact inventories, and the G12
holistic simplicity/support review.

## Out of scope

Remote publication, release version changes, Windows/macOS Native Image claims, dynamic catalogs,
new symbol families, or changes to the approved conformance wording.

## Acceptance criteria

- The staged artifact contains expected classes/docs/license metadata and no AWT or hidden resources.
- A clean Java 21 offline consumer parses, resolves, portrays, and renders one supported SIDC.
- Linux Native Image proves supported, approved fallback, and exact malformed/unsupported outcomes.
- Documentation publishes the exact revision/profile/exclusions and no complete-conformance claim.
- G12 closeout confirms that one parser, one explicit inventory, and existing renderers remain enough.

## Required tests

Module checks, Javadocs, artifact/POM/source inspection, offline consumer, architecture inventory,
shared JVM/native semantic probes, and manual support-wording review.

## Validation

```bash
./gradlew :modules:mundane-map-symbology-milstd2525:check --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G12 Linux Native Image and support-statement closeout**. G13 remains dependent on
this task so MIL-STD-2525 is completed first.

The maintainer approved this named checkpoint through the 2026-07-23 execution authorization.
`publicationDryRun consumerSmoke` passed with the isolated Java 21 consumer, and `nativeSmoke`
passed using GraalVM Community Java 21.0.2 on Ubuntu 24.04.1 WSL2 Linux x86-64. No Windows or macOS
Native Image claim is made.
