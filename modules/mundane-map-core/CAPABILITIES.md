# Core algorithm capability profile

This module owns JDK-only implementations of toolkit-neutral algorithms. It does not parse storage
formats, perform network discovery, render with AWT/browser APIs, or acquire external CRS resources.

## Capability matrix

| Area | Current profile | G19 target | Explicit boundary |
| --- | --- | --- | --- |
| CRS registry | Explicit EPSG:4326/EPSG:3857 definitions and operations | Reproducibly generated, provenance-pinned common geographic/projected/vertical/compound CRS catalog | Not the complete live EPSG database; no runtime scanning/database/network lookup |
| CRS syntax/operations | Narrow identifier/operation profile | Pinned bounded WKT2 grammar, axes, units, datums and named pure-Java projection methods with preserved unsupported vertical/compound metadata | No network grid downloads, JNI PROJ, or approximate unsupported datum operations |
| Geometry dimensions | Packed transforms and envelope clipping preserve Z/M and typed empty/collection structure; snapping and screen hits use x/y without mutating source ordinates; editing retains geometry exactly; seam splitting accepts empty/packed XY and stably rejects Z/M or collections | Complete for the current bounded transform/clip/snap/edit/hit/query profile | XY-only results and seam splitting require the documented conversion boundary; no silent ordinate loss |
| Validity/topology | Bounded x/y Simple Features validity, boundary-inclusive intersection, axis-aligned envelope overlay, deterministic first-failure diagnostics, and explicit canonical duplicate/orientation repair | Complete for current render, query, ingestion, and editing workflows | Exact arithmetic profile; no arbitrary overlay suite, tolerance-based near-point merging, or heuristic automatic repair |
| Raster reprojection | Identity/narrow affine placement | Inverse-mapped bounded window/tile warping with nearest, bilinear, and one frozen higher-quality resampler for imagery/elevation | No GPU/JNI acceleration, implicit grid acquisition, or partial publication on failure |
| Labels | Deterministic point-label candidates/collision | Line-following/repeated/upright labels and polygon/interior/multipart placement with wrap and collision parity | Text shaping remains an injected renderer capability, not a core font engine |
| Tile matrices | Web-Mercator XYZ convenience algorithms | OGC TileMatrixSet 2.0-independent value/algorithm profile: axes/origins, scale, non-square tiles, bottom/top conventions, variable widths | No WMTS/OGC API document parsing or service discovery |
| Spatial/query/edit services | Packed indexes, bounded query accounting, snapping, edits, measurement and wrap | Preserve bounds and deterministic semantics for dimensional/topological/profile expansion | No implicit threads, providers, storage ownership, or toolkit event model |

## Algorithm contract

- Parsing, transformation, candidate enumeration, overlay, warping, and tile coverage prospectively
  bound work and intermediate storage and fail atomically with stable diagnostics.
- Numeric tolerances, domain/seam behavior, nodata/mask interpolation, dimensional propagation, and
  deterministic output ordering are part of each owning algorithm's documented contract.
- Authoritative control points/corpora and independent reference results establish conformance;
  performance measurements are informational and cannot weaken correctness or limits.
- The module remains dependent only on `mundane-map-api`, uses packed primitive storage where
  appropriate, and contains no AWT, external-library types, discovery, reflection, JNI, `Unsafe`, or
  internal JDK APIs.

## Geometry topology profile

- `GeometryValidity` checks line distinctness, ring closure/distinctness/area/self-intersection,
  shell-hole containment and contact, hole contact/nesting, and multipolygon interior overlap. It
  reports the deterministic first issue with a stable reason, geometry path, and representative x/y
  location. Z/M never changes the topological answer; typed empties are valid.
- `GeometryPredicates.intersects` is the boundary-inclusive predicate needed by current query and
  hit workflows. `GeometryEnvelopeClipper` is the current overlay profile: clipping against a closed
  axis-aligned envelope, with linear Z/M interpolation for inserted vertices and deterministic part
  order. It does not claim arbitrary polygon/polygon overlay.
- `GeometryCanonicalRepair` runs only when called explicitly with the frozen duplicate-ring-position
  and/or ring-orientation defect names. Parsers, renderers, predicates, and validity checks never call
  it implicitly.
- `GeometryTopologyLimits.DEFAULT` prospectively caps one operation at 1,000,000 input positions,
  4,000,000 position/segment comparisons, and 2,000,000 output positions. A stable exception is
  raised before a partial result is observable. The independent integer-coordinate reference corpus
  uses exact x/y comparisons; the profile intentionally has no hidden epsilon.

## Completion rule

G19-010 and G19-012 through G19-014 complete this matrix only after the exact WKT2/CRS/projection,
resampling, label-placement, and TileMatrixSet profiles are frozen and
covered by authoritative, hostile, boundary, cancellation, differential, and cross-adapter evidence.
