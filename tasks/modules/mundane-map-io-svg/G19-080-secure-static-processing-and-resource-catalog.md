# G19-080 — Secure static SVG processing and resource catalog

Status: Proposed
Depends on: G19-002
Gate: G19
Type: HITL

## Goal

Freeze the SVG 2 restricted static processing mode and add a closed, typed, caller-supplied resource
catalog without enabling scripting, dynamic behavior, or ambient I/O.

## Context

The current hardened importer has no reference/resource model and rejects most document structure.
Later definitions, CSS, fonts, images, and filters need one secure bounded resolution foundation.

## Scope

- Pin the SVG 2/SVG 1.1, XML, namespace, static processing, ignored-versus-error, metadata, language,
  conditional-processing, and unsupported-feature matrix.
- Keep hardened StAX with DTD/entities/XInclude/external schema disabled; validate encoding, namespace,
  character, qualified-name, depth, and aggregate text/attribute limits.
- Add an immutable catalog for explicitly registered typed stylesheets, fonts, raster images, and SVG
  fragments with normalized identifiers, exact media types, ownership, and byte/work ceilings.
- Resolve same-document, embedded `data:`, and exact catalog references only; reject ambient file,
  network, classpath, system-font, MIME-sniffing, redirect, and traversal behavior.
- Implement bounded two-phase ID/reference indexing with cycle, depth, fan-out, and aggregate expansion
  accounting shared by later slices.
- Reject script, events, animation, dynamic DOM behavior, and `foreignObject` before execution or
  resource resolution.

## Out of scope

- Browser DOM integration, active content, network fetches, and operating-system resource lookup.

## Acceptance criteria

- Every resource byte is embedded in the document or supplied by the caller's closed catalog.
- Forward references work while cycles/exponential expansion fail prospectively and diagnostically.
- Excluded active constructs cannot trigger code, I/O, environment lookup, or retained partial state.

## Required tests

- XML declaration/encoding/namespaces/metadata/conditional processing, forward/cyclic references,
  catalog type/identity/ownership, data URI, traversal/scheme/redirect/MIME attacks, XXE/entity/XInclude,
  script/events/animation/foreignObject, cancellation, and all aggregate limits.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, the SVG security corpus, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact restricted processing mode, external-resource
policy, stable ignored/error rules, and any licensed corpus before completion.
