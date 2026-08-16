# HTTP tile adapter capability intent

`mundane-map-io-http-tiles` is the JDK-only transport, policy, cache, and raster-tile acquisition
module. Directly configured sources remain usable without a JSON or XML dependency. Standards
metadata formats are provided by explicit adapter modules that depend on this transport; they are
never discovered through the classpath.

The root README describes released behavior. Target rows below become release claims only as their
cards close.

## Module and protocol boundaries

| Surface | Released role | Approved target | Owning module/card | Deliberate exclusions |
| --- | --- | --- | --- | --- |
| Direct HTTP XYZ | Fixed-host Web Mercator, 256-pixel PNG/JPEG acquisition | Explicit bounded raster acquisition over approved XYZ/TMS and neutral tile-matrix definitions | `mundane-map-io-http-tiles`, G19-050 through G19-052 | Ambient discovery, browser cookies, and implicit credentials |
| HTTP semantics | One attempt, no redirects, decoded memory cache | Pinned RFC 9110/9111 authority, authentication, redirect, validator, freshness, retry, and durable-cache profile | `mundane-map-io-http-tiles`, G19-050 and G19-051 | Arbitrary redirects, unbounded retries, and ambient proxy/credential discovery |
| TileJSON | Unsupported | Read-only TileJSON metadata consumption and raster-source construction | Planned `mundane-map-io-tilejson-jackson`, G19-053 and G19-054 | TileJSON authoring, serving, and an in-house JSON parser |
| WMTS | Unsupported | Complete declared read-only WMTS 1.0.0 client: capabilities, explicit selection, KVP/REST tiles, and bounded raw FeatureInfo | Planned `mundane-map-io-wmts`, G19-055 through G19-058 | SOAP, server/authoring behavior, transactions, and implicit FeatureInfo payload parsing |
| OGC API Tiles | Unsupported | Full guarded read-only discovery/selection plus generic bounded raw tiles and registered raster construction | Planned `mundane-map-io-ogc-api-tiles-jackson`, G19-220 through G19-224 | Server/authoring behavior, HTML scraping, implicit decoding, and OpenAPI-generated clients |

## HTTP transport matrix

Normative baseline: RFC 9110 HTTP Semantics and RFC 9111 HTTP Caching, with the exact TLS,
authentication, proxy, retry, and filesystem cache profiles pinned by G19-050 and G19-051.

| Area | Released profile | Approved target | Card |
| --- | --- | --- | --- |
| Authority | One fixed host, HTTPS by default, loopback-style HTTP opt-in | Immutable allowlisted authority set with same-origin redirect/header rules and redacted diagnostics | G19-050 |
| Authentication and headers | No credential surface | Explicit caller-owned scoped credentials/headers; never ambient and never forwarded across an unapproved authority boundary | G19-050 |
| Redirects | Rejected | Bounded RFC-aware redirect profile for idempotent requests under the authority policy | G19-050 |
| TLS and proxy | JDK defaults | Explicit caller-supplied TLS/proxy policy without system/desktop credential discovery | G19-050 |
| Validators/freshness | Not implemented | `ETag`, `Last-Modified`, freshness, age, `Vary`, revalidation, negative, and approved stale behavior | G19-051 |
| Retry | One attempt | Bounded cancellation-aware retry/backoff for approved idempotent transient failures and `Retry-After` | G19-051 |
| Cache | Optional decoded memory cache | Coordinated bounded encoded/decoded memory and optional caller-selected disk cache with integrity, eviction, and recovery | G19-051 |
| Matrix/profile | Web Mercator XYZ, zoom 0–22, 256 square pixels | Direct XYZ/TMS row conventions, registered raster decoders, variable/non-square tiles, and approved neutral tile-matrix sets | G19-052 |

## TileJSON adapter decision

The approved TileJSON integration is a separate, fully supported optional artifact named
`mundane-map-io-tilejson-jackson`:

- It uses the repository's pinned Jackson Core streaming parser and depends explicitly on
  `mundane-map-io-http-tiles`; the transport module does not depend on Jackson.
- It consumes and validates TileJSON metadata, then explicitly constructs an HTTP raster tile source.
- Initial completion covers the common published TileJSON 2.x revisions and 3.0.0 with version-
  specific rules. Legacy 1.0.0 is explicitly unsupported unless a later decision adds it.
- It models all standard metadata fields and bounded unknown members, but constructs raster sources
  only for tile media handled by an explicitly registered raster decoder.
- Vector tile payload decoding, UTFGrid fetching, GeoJSON overlay fetching, TileJSON generation, and
  tile serving are separate capabilities and are not implied.
- Attribution is data. The adapter never evaluates its HTML, loads its resources, or renders it as
  trusted markup.

G19-053 owns the document/model boundary; G19-054 owns guarded retrieval and source construction.

## WMTS adapter decision

The approved WMTS integration is a separate JDK-only artifact named `mundane-map-io-wmts`:

- It implements the read-client portions of OGC WMTS 1.0.0 / OGC 07-057r7 and applicable OWS Common
  1.1.0 requirements using directly constructed hardened JDK StAX.
- It accepts caller-supplied capabilities and can retrieve `GetCapabilities` only through the
  explicit G19 HTTP authority/cache policy.
- It requires deterministic layer, style, format, dimensions, tile matrix set, limits, and binding
  selection; it never chooses an arbitrary first compatible-looking layer.
- It retrieves raster `GetTile` responses through both KVP and RESTful resource bindings and only
  decodes media handled by an explicitly registered raster decoder.
- It supports advertised KVP/REST `GetFeatureInfo` as a bounded transport operation returning
  detached media-typed bytes. Payload interpretation is the caller's explicit responsibility.
- SOAP, server capabilities, tile publication, transactions, arbitrary XML extensions, and implicit
  FeatureInfo parsing are deliberate exclusions.

G19-055 through G19-058 own parsing, selection, tile acquisition, FeatureInfo, conformance, and
lifecycle closeout. The production module is created only when G19-055 delivers its tested vertical
slice; no empty future module is added during planning.

## OGC API Tiles adapter decision

The approved OGC API Tiles integration is a separate, fully supported optional artifact named
`mundane-map-io-ogc-api-tiles-jackson`:

- It implements a guarded read-only client for OGC API - Tiles - Part 1: Core 1.0.0, the applicable
  OGC API - Common - Part 1: Core 1.0.0 building blocks, and TileMatrixSet 2.0.
- It starts from one caller-authorized landing page and follows only bounded, typed, approved links
  through conformance declarations, collections, tileset lists, tilesets, matrix definitions, and
  tile resources. Selection remains explicit.
- Pinned Jackson Core parses standard JSON. Hardened JDK StAX parses the approved XML Tileset
  Metadata representation. The HTTP transport remains JDK-only and performs no format discovery.
- It supports the approved Core, Tileset, Tilesets List, Dataset Tilesets, GeoData Tilesets,
  Collections Selection, and DateTime client behaviors when the service declares them.
- It retrieves any selected advertised tile representation as bounded detached media-typed bytes.
  Registered raster decoders may additionally construct raster sources; vector/other payload
  interpretation belongs to an explicit caller-selected format adapter.
- Server/authoring behavior, HTML scraping, OpenAPI-driven client generation, implicit decoder
  discovery, arbitrary link crawling, and unclaimed optional conformance classes are exclusions.

G19-220 through G19-224 own models, guarded discovery, deterministic selection, raw/raster retrieval,
and conformance/lifecycle closeout. No empty production module is created during planning.
