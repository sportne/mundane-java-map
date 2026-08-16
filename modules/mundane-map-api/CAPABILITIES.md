# Public API capability profile

This module owns toolkit-neutral public contracts. It defines representable values and lifecycle
semantics; algorithms, rendering, storage formats, network protocols, and toolkit adaptation belong
to downstream modules.

## Capability matrix

| Area | Current profile | G19 target | Explicit boundary |
| --- | --- | --- | --- |
| Geometry families | Immutable non-empty XY Point, MultiPoint, LineString, MultiLineString, Polygon, and MultiPolygon | Empty standard geometries, Z, M, ZM positions, and bounded heterogeneous GeometryCollection values while preserving packed primitive storage | No solids, volumes, curved geometry primitives, arbitrary object graphs, or implicit ordinate loss |
| Dimensional semantics | XY envelope/equality/iteration | Frozen emptiness, dimensionality, equality, envelope, visitor, nesting, limits, and explicit XY down-projection policy | Consumers must accept, explicitly approximate under policy, or reject; never silently truncate |
| Attributes | Flat immutable scalar values | Bounded immutable structured values sufficient for neutral nested properties and format mappings | No reflection/property-bean access, executable expressions, or format-specific unknown-node classes |
| Expressions | Small neutral portrayal inputs | Deterministic bounded neutral expression inputs/results needed by the supported SE and MapLibre profiles | No JavaScript, arbitrary code, network lookup, or format AST in the API |
| Vector portrayal | Existing point/line/fill/composite marker profile | Cap/join/dash/offset, graphic stroke/fill, advanced text placement/halo, explicit ordering/units/opacity/fallback | No promise of pixel identity between engines; equivalence uses declared tolerances |
| Raster portrayal | Raster/elevation source and basic elevation-style contracts | Neutral band selection, color map, channel/composite, opacity, nodata/mask portrayal concepts required by supported adapters | No codec, Java2D image, GPU texture, or format-library type in public contracts |
| Interaction | Toolkit-neutral pointer/tool/selection/edit/measurement contracts | Extended only as required to consume new geometry/portrayal without toolkit leakage | No browser, Swing, native-event, or transport-specific public model |
| Sources and ownership | Bounded feature/raster/elevation source and cursor/read lifecycles | Preserve exact close/cancel/borrowed/owned semantics across added dimensional values | No implicit provider discovery, background authority, or unbounded materialization |
| Diagnostics | Stable structured codes/severity/location/ordered bounded context | Stable failures for dimensional, structured-value, expression, and portrayal limits/conversion | Human message/cause text remains non-contractual and source values are not leaked |

## Cross-cutting invariants

- Public values remain immutable, defensively copy mutable inputs, use explicit limits, and have
  deterministic equality/order/serialization-facing semantics.
- New families remain exhaustively visitable under the strict enum/sealed compatibility policy; no
  fallback silently converts an unknown subtype.
- Existing ergonomic XY/scalar/simple-symbol factories and behavior remain governed by the project
  compatibility policy.
- The module remains JDK-only and free of AWT, format, database, HTTP-client implementation, browser,
  reflection, scanning, serialization, JNI, `Unsafe`, and internal-JDK dependencies.

## Completion rule

G19-001 and G19-002 complete this matrix only after every target value is publicly documented,
prospectively bounded, hostile-input tested, native-compatible, and consumed by renderers/adapters
through explicit accept/approximate/reject behavior.
