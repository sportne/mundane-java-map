# G19-223 — OGC API generic tile retrieval and raster source

Status: Proposed
Depends on: G19-044, G19-051, G19-052, G19-222
Gate: G19
Type: HITL

## Goal

Retrieve explicitly selected OGC API tiles as bounded detached media-typed bytes and construct raster
sources when an explicitly registered decoder supports the representation.

## Context

OGC API Tiles is media-neutral and defines conformance classes for PNG, JPEG, TIFF, NetCDF, GeoJSON,
and Mapbox Vector Tiles. Protocol completeness should not force every encoding into this adapter or
silently restrict retrieval to raster images.

## Scope

- Construct exact tile requests from the selected templated link, matrix identifier, row, column,
  collections, datetime/subset, and approved representation parameters.
- Return an immutable detached result with normalized advertised/received media type, bounded bytes,
  safe response metadata, resource identity, validators, and actual/closest datetime information.
- Apply the HTTP authority, redirect, credentials, cache, retry, timeout, cancellation, body, and
  secret-redaction contracts to discovery and tile operations under one lifecycle.
- Validate status, content negotiation, advertised-versus-received media/profile, OGC problem/error
  responses, matrix limits, tile coordinates, length, and representation-specific declared rules.
- Construct raster sources only through explicitly registered PNG/JPEG/TIFF or future raster decoders;
  delegate GeoJSON/MVT/other vector interpretation only through explicit caller-selected adapters.
- Aggregate requests, retries, redirects, bytes, tiles, fan-out, decoded pixels, cache entries, workers,
  retained raw results, and cleanup prospectively.

## Out of scope

- Universal tile-payload parsing, decoder discovery, arbitrary content sniffing, implicit media
  fallback, trusted markup execution, reprojection, tile generation, or server behavior.

## Acceptance criteria

- Every explicitly selected advertised representation can be retrieved as detached bounded bytes;
  supported registered raster formats additionally yield exact raster envelopes/pixels.
- Media/profile mismatch, unsupported raster construction, problem responses, invalid coordinates,
  and excessive fan-out fail without partial publication or a hidden alternate-format request.
- Cancellation, cache/retry failure, replacement, and close leak no bodies, files, bytes, workers,
  credentials, registrations, source claims, or decoded state.

## Required tests

- Scripted PNG/JPEG/TIFF/GeoJSON/MVT/unknown-media raw retrieval, content negotiation, problem detail,
  validators, cache/retry, temporal headers, matrix variables/limits, and raster construction fixtures.
- Wrong/missing/conflicting media, content sniffing resistance, oversized/truncated body, unauthorized
  link, fan-out, cancellation, concurrent close, failure aggregation, cleanup, and redaction tests.

## Validation

Run the OGC API Tiles, HTTP, image/registered raster, and tile-matrix checks plus scripted tile
protocol corpus, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the generic raw-result contract, media/decoder boundary,
request semantics, lifecycle/security review, and cross-format/provider evidence.
