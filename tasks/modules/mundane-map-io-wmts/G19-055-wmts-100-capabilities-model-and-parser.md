# G19-055 — WMTS 1.0.0 capabilities model and parser

Status: Proposed
Depends on: G19-014, G19-050
Gate: G19
Type: HITL

## Goal

Add a JDK-only WMTS adapter with a bounded immutable model and hardened parser for WMTS 1.0.0
capabilities documents.

## Context

WMTS capabilities combine OWS Common service/operation metadata with layers, styles, formats,
dimensions, links, resource templates, and tile-matrix definitions. They need a dedicated adapter;
placing XML semantics in the HTTP transport would mix protocol and transport concerns.

## Scope

- Add a non-empty `mundane-map-io-wmts` module depending on the core tile-matrix and JDK-only HTTP
  modules, with its capability intent recorded locally.
- Pin OGC 07-057r7 Web Map Tile Service Implementation Standard 1.0.0, its schemas, and applicable OWS
  Common 1.1.0 requirements.
- Parse caller-supplied bytes/streams and one caller-authorized `GetCapabilities` response using
  directly constructed hardened JDK StAX with DTD/external entities/schema retrieval disabled.
- Model applicable service identification/provider, operations metadata, contents, themes, layers,
  styles/defaults, formats, info formats, dimensions/default/current/values, bounding boxes,
  matrix-set links/limits, resource URLs, service metadata, and tile matrices.
- Validate namespace/version, required content, identifier uniqueness, references, numeric domains,
  URI/templates, CRS text, and version negotiation before exposing a model.
- Bound document bytes, XML depth/elements/attributes/namespaces/text, links, layers, styles, formats,
  dimensions/values, matrices/limits, templates, identifiers, retained metadata, and allocations.

## Out of scope

- Tile retrieval, selection, GetFeatureInfo, SOAP, server behavior, XML signature, schema downloads,
  arbitrary extension interpretation, and XML library discovery.

## Acceptance criteria

- Official schemas/examples and independent service capabilities produce the expected immutable
  versioned model without network access during parsing.
- Invalid required content/references/numbers/templates fail atomically with stable value-safe
  diagnostics; bounded unknown extensions are skipped under a documented policy.
- Architecture checks prove the module is JDK-only and uses no ambient XML/provider/network discovery.

## Required tests

- Official and cross-vendor capabilities fixtures covering OWS metadata, layers, styles, dimensions,
  formats, info formats, KVP operations, REST resources, matrices, limits, themes, and bounding boxes.
- XXE/entity/DTD/schema, namespace/version confusion, duplicate IDs, dangling references, hostile
  XML/URI/numbers/templates, deep/wide/long documents, truncation, cancellation, and allocation tests.

## Validation

Run the new WMTS module's `check`, dependency verification, and approved capabilities corpus lane,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the pinned WMTS/OWS editions, XML/extension policy, limits,
corpus provenance, and cross-vendor capabilities evidence before completion.
