# G19-079 — COG writer, conformance, and GeoTIFF capability closeout

Status: Proposed
Depends on: G19-076, G19-078
Gate: G19
Type: HITL

## Goal

Emit range-efficient OGC COG 1.0 files and close the declared GeoTIFF reader/writer/COG capability
with independent interoperability and conformance evidence.

## Context

A readable tiled GeoTIFF is not automatically a COG, and self-round-trip tests cannot establish
external interoperability or justify an OGC conformance statement.

## Scope

- Extend the encoder with optimized IFD, overview, mask, and tile ordering that satisfies the
  declared OGC COG 1.0 GeoTIFF Tiles, Overviews, Keys, and Optimized GeoTIFF classes.
- Run every applicable OGC GeoTIFF 1.1 reader/writer and COG file test, keeping classic-TIFF,
  BigTIFF-interoperability, and informative vertical/3D claims distinct.
- Have approved independent tools read emitted classic/BigTIFF and conventional/COG files; read
  independent files across every declared container/codec/sample/CRS/overview/mask profile.
- Expand provenance/hash/license and hostile mutation corpora plus local/HTTP/writer lifecycle,
  bounded-work, native, publication, and offline evidence.
- Reconcile package Javadocs, root support wording, capability matrix, diagnostics, examples, and
  every deliberate exclusion.

## Out of scope

- Claiming OGC HTTP Range server conformance, general TIFF completeness, or undeclared codecs.

## Acceptance criteria

- Emitted COGs pass the declared OGC file classes and demonstrate bounded low-range window access.
- Every broader support statement is backed by applicable OGC tests and independent data/tool
  evidence; BigTIFF and vertical interoperability are not mislabeled as GeoTIFF 1.1 requirements.
- Success, rejection, cancellation, and injected failure release all local/HTTP/cache/staging
  resources exactly once.

## Required tests

- Full OGC ATS, independent read/write matrix, COG layout/range-request observations, corpus
  provenance, hostile mutations, rollback/lifecycle, native/publication/offline compatibility, and
  threshold-free performance/allocation evidence.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, affected OGC/corpus/network/
performance/native/publication lanes, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the external-tool observations, OGC conformance statements,
fixture licenses/hashes, capability matrix, and exact public support wording before completion.
