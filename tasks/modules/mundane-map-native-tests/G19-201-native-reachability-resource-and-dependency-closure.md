# G19-201 — Native reachability, resource, and dependency closure

Status: Proposed
Depends on: G19-200
Gate: G19
Type: AFK

## Goal

Make the closed-world inputs of every advertised native-targeted module explicit, bounded, reviewable, and mechanically complete.

## Context

The current aggregate smoke contains hand-maintained reachability and resource configuration, but it does not yet prove that every `nativeTarget: true` module is exercised or that excluded adapters and implicit discovery cannot enter unnoticed.

## Scope

- Generate and verify a registry-to-native-scenario inventory for every production module marked `nativeTarget: true`.
- Inventory reachable entry points, services, charsets, XML providers, image codecs, resources, bundles, and initialization policy.
- Require fixed resource names, lengths, hashes, provenance, and aggregate ceilings; reject implicit scanning or wildcard registration.
- Mechanically exclude reflection/classpath discovery, Java serialization, JNI, `Unsafe`, internal JDK APIs, and non-native Jackson, SQLite, Vaadin, or other adapter graphs.
- Diff and review reachability metadata and embedded-resource changes as release inputs.

## Out of scope

- Granting native support to an excluded adapter or introducing runtime metadata generation.

## Acceptance criteria

- No native-targeted production module lacks an assertion-bearing scenario or explicit evidence mapping.
- Missing and surplus reachability/resource entries fail with stable bounded reports before image publication.
- Adding an excluded dependency, reflective access path, implicit service, or unregistered resource fails a negative fixture.

## Required tests

- Registry completeness, reachability/resource manifest, aggregate-bound, forbidden-API/dependency, service-provider, and initialization-policy tests.
- Synthetic missing-resource, surplus-resource, reflective-access, and excluded-adapter fixtures.

## Validation

Run native configuration tests, `./gradlew nativeSmoke --console=plain`, `./gradlew qualityGate --console=plain`, and `git diff --check`.
