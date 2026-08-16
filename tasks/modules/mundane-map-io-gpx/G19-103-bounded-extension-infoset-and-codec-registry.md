# G19-103 — Bounded extension infoset and codec registry

Status: Proposed
Depends on: G19-102
Gate: G19
Type: AFK

## Goal

Preserve unknown GPX extension elements semantically and support explicitly registered typed codecs without
embedding vendor schemas or executable discovery in the core module.

## Context

The released reader discards all extension content. Real GPX interchange commonly carries vendor data, but
implementing every vendor dialect would be unbounded and couple the neutral adapter to unstable ecosystems.

## Scope

- Add an immutable safe XML infoset retaining expanded element/attribute names, required namespace bindings,
  text, mixed/ordered children, and standard extension-container scope.
- Canonicalize prefixes and insignificant whitespace while preserving semantic namespace/name/attribute/text/order data.
- Add an immutable explicit registry keyed by namespace/QName for typed codecs operating on the safe infoset;
  define collision, version, error, cost, encode/decode, and unknown-content behavior.
- Preserve unknown content by default; never execute markup, resolve entities/schemas/resources, instantiate handlers
  reflectively, scan classpaths, or register built-in Garmin/vendor semantics.
- Bound extension nodes/depth/attributes/namespaces/text/code points/owned bytes per tree and document, and isolate
  codec work/failure with stable diagnostics.

## Out of scope

- Lexical prefix/whitespace/comment/processing-instruction fidelity, arbitrary XML APIs, schema download, and any
  built-in Garmin, geocaching, fitness, sensor, or device extension model.

## Acceptance criteria

- Unknown extension infosets round-trip semantically through the model and canonical writer under exact limits.
- Registered typed codecs are deterministic, isolated, explicitly selected, and cannot suppress opaque fallback silently.
- Hostile namespace/depth/fan-out/text/codec input fails before partial document or output publication.

## Required tests

- Extension scope, multi-namespace, default/prefixed names, attributes, mixed content, child order, and unknown round trips.
- Registry collision/version/failure, hostile XML, namespace/depth/fan-out/text/byte/cost limits, and diagnostic hygiene.

## Validation

Run `./gradlew :modules:mundane-map-io-gpx:check --console=plain`, XML corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

Vendor-specific semantics may be proposed later as separate explicit adapters/codecs.
