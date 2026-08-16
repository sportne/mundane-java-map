# Core algorithm capability profile

This module owns JDK-only implementations of toolkit-neutral algorithms. It does not parse storage
formats, perform network discovery, render with AWT/browser APIs, or acquire external CRS resources.

## Capability matrix

| Area | Current profile | G19 target | Explicit boundary |
| --- | --- | --- | --- |
| CRS registry | Explicit EPSG:4326/EPSG:3857 definitions and operations | Reproducibly generated, provenance-pinned common geographic/projected/vertical/compound CRS catalog | Not the complete live EPSG database; no runtime scanning/database/network lookup |
| CRS syntax/operations | Narrow identifier/operation profile | Pinned bounded WKT2 grammar, axes, units, datums and named pure-Java projection methods with preserved unsupported vertical/compound metadata | No network grid downloads, JNI PROJ, or approximate unsupported datum operations |
| Geometry dimensions | Algorithms assume non-empty homogeneous XY values | Explicit empty/Z/M/ZM/collection propagation for transform, split, clip, snap, edit, hit and query paths | XY-only outputs require a declared conversion; no silent ordinate loss |
| Validity/topology | Purpose-built envelope, clipping, hit, containment and edit helpers | Bounded OGC Simple Features validity, required predicates/overlay, deterministic ordering, and explicit opt-in repair for a frozen defect set | No unbounded general computational-geometry suite or heuristic automatic repair |
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

## Completion rule

G19-010 through G19-014 complete this matrix only after the exact WKT2/CRS/projection, Simple
Features operation/repair, resampling, label-placement, and TileMatrixSet profiles are frozen and
covered by authoritative, hostile, boundary, cancellation, differential, and cross-adapter evidence.
