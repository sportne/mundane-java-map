# G19-220 — OGC API Tiles versioned document model

Status: Proposed
Depends on: G19-014, G19-050
Gate: G19
Type: HITL

## Goal

Add a fully supported optional Jackson adapter with immutable bounded models and parsers for the
approved OGC API Tiles, Common, and TileMatrixSet metadata representations.

## Context

OGC API Tiles uses linked JSON resources rather than one capabilities document and optionally defines
XML tileset metadata. The JDK HTTP transport must remain independent of JSON, while the adapter must
not retain arbitrary attacker-controlled JSON/XML trees or discover parsers implicitly.

## Scope

- Add a non-empty `mundane-map-io-ogc-api-tiles-jackson` module using pinned Jackson Core streaming,
  hardened directly constructed JDK StAX for the approved XML representation, and the JDK-only HTTP
  and core tile-matrix modules.
- Pin OGC API - Tiles - Part 1: Core 1.0.0 (OGC 20-057), OGC API - Common - Part 1: Core 1.0.0
  (OGC 19-072), and Two Dimensional Tile Matrix Set and Tile Set Metadata 2.0 (OGC 17-083r4).
- Model version/conformance-aware landing pages, links, conformance declarations, collections,
  extents, tileset lists, tilesets, data types, media links, access constraints, matrix-set links and
  embedded definitions, limits, styles, layers, and relevant temporal metadata.
- Parse the standard JSON representations and the approved XML Tileset Metadata conformance class;
  choose a requested representation explicitly and never scrape HTML.
- Enforce exact required/optional/default, URI/link-relation, identifier, media type, CRS, numeric,
  bbox, temporal, matrix, reference, duplicate-member, unknown-member, and extension policies.
- Bound bytes, nesting, tokens/elements, attributes/members, strings/text, links, collections,
  tilesets, matrices/limits, styles/layers, temporal values, retained unknown data, and allocations.

## Out of scope

- Network traversal, tile retrieval, HTML scraping, OpenAPI parsing, server/authoring behavior,
  arbitrary XML extensions, Jackson Databind, parser discovery, and tile-payload interpretation.

## Acceptance criteria

- Official examples/schemas and independent JSON/XML service documents produce equivalent immutable
  standard models under the declared conformance classes.
- Malformed/duplicate/unknown/invalid required data and dangling references fail atomically with
  stable value-safe diagnostics and exact resource ceilings.
- Dependency checks keep Jackson out of the HTTP transport and prevent ambient JSON/XML discovery.

## Required tests

- Official and cross-provider landing, conformance, collection, tileset-list, tileset, TMS 2.0,
  limits, links, extents, temporal, data-type, JSON, and XML representation fixtures.
- Duplicate/unknown/wrong-type members, XXE/DTD/entity/schema attacks, hostile Unicode/numbers/URIs/
  links/namespaces, deep/wide/long documents, truncation, cancellation, allocation, and dependency tests.

## Validation

Run the new adapter's `check` and dependency-verification tasks plus approved OGC document corpus,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact OGC editions/conformance classes, JSON/XML and
unknown-extension policies, dependency inventory, corpus provenance, and model surface.
