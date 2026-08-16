# G19-142 — GeoJSON, vector, and tiled feature sources

Status: Proposed
Depends on: G19-014, G19-054, G19-136, G19-141, G19-163
Gate: G19
Type: AFK

## Goal

Implement complete pinned GeoJSON and vector source models and bind them to bounded neutral feature sources.

## Context

Only detached string-locator GeoJSON descriptors currently bind. Inline/external GeoJSON, vector TileJSON,
tile templates, source layers, clustering, identity, filters, schemes, and vector-tile content are unavailable.

## Scope

- Parse every v26.2.1 GeoJSON/vector source property, default, range, reference, promote-ID, generate-ID,
  line-metric, cluster/filter, attribution, bounds, scheme, zoom, volatility, encoding, and update behavior.
- Delegate complete inline/external GeoJSON to the strict GeoJSON adapter under shared limits and resource policy.
- Resolve vector TileJSON/templates and decode the approved vector-tile profile into stable source-layer features.
- Implement source-layer selection, feature identity/state, tiling/buffering/clustering, wrap, deduplication,
  query ordering, cancellation, cache, update generation, and all-or-nothing publication.
- Bound resources/tiles/features/coordinates/properties/clusters/state/cache/owned bytes and aggregate work.

## Out of scope

- Proprietary Mapbox authentication/protocols and unapproved vector-tile encodings.

## Acceptance criteria

- Official/independent GeoJSON and vector styles bind every pinned property with stable identity/order.
- Updates, clustering, source layers, wrap, and feature state agree with the documented 2D semantics.
- Unauthorized/malformed/stale/over-budget sources leave the previous complete binding intact.

## Required tests

- Full source-property/default/type matrix; inline/external/TileJSON/template/source-layer/cluster/state fixtures.
- SSRF/redirect/path/media, malformed tiles, identity collisions, wrap, cancellation/cache/update, and exact limits.

## Validation

Run module plus GeoJSON/vector-tile/HTTP integration checks, qualityGate, and `git diff --check`.

## Notes

None.
