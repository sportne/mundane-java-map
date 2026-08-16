# G19-090 — SE/SLD document model and resource catalog

Status: Proposed
Depends on: G19-002
Gate: G19
Type: AFK

## Goal

Establish secure bounded document models for SE 1.1 and the SLD 1.1 wrapper, including metadata and an
explicit closed resource catalog.

## Context

The released parser accepts only a root `FeatureTypeStyle` and has no SLD document model. Later
filter, symbolizer, and writer slices need one versioned document/security/resource foundation.

## Scope

- Pin OGC 05-077r4, OGC 05-078r4, XML/XLink rules, namespace/version dispatch, and applicable schemas.
- Model and parse `FeatureTypeStyle`, `CoverageStyle`, `StyledLayerDescriptor`, named/user layers,
  named/user styles, feature constraints, descriptions, metadata, and embedded style ordering.
- Add immutable document values and a typed caller-supplied catalog for approved image, SVG, font, and
  inline resources; normalize identifiers and bound cycles, depth, fan-out, bytes, and media types.
- Preserve hardened StAX processing and add prospective document bytes/elements/depth/text/attribute/
  namespace/rule/resource limits with stable path-aware diagnostics.
- Reject DTD/entities/XInclude/external schemas, ambient URL/file/classpath/font lookup, WMS operations,
  vendor options, and unknown extensions by default.

## Out of scope

- Filter evaluation, portrayal compilation, WMS requests, remote style deployment, and generic GML.

## Acceptance criteria

- Supported SE and SLD roots map losslessly to immutable bounded document values.
- Resource resolution occurs only through exact authorized catalog entries and cannot trigger ambient I/O.
- Malformed, wrong-version, oversized, cyclic, or unsupported documents fail before partial publication.

## Required tests

- SE/SLD root/version/namespace/metadata/layer/style/constraint matrices and independent documents.
- XXE/XInclude/schema/URL/path traversal, catalog identity/media/cycle, and every document limit boundary.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

The catalog authorizes named caller resources; it is not a URL fetcher.
