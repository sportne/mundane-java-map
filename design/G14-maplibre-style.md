# G14 — MapLibre Style design

Project index: [DESIGN.md](../DESIGN.md).

## Purpose and specification baseline

G14 reads a bounded MapLibre Style Specification document and applies supported layers to explicitly
registered feature sources. The baseline is Style Specification version 8:

- <https://maplibre.org/maplibre-style-spec/>
- <https://maplibre.org/maplibre-style-spec/layers/>
- <https://maplibre.org/maplibre-style-spec/expressions/>

MapLibre is a community-maintained de facto specification rather than an OGC or ISO standard. Its
JSON layer, filter, expression, icon, and label model is valuable interoperability, but its complete
web renderer—including remote sources, sprites, glyph services, terrain, and 3D—is outside this
small Swing map library.

## Module and dependency boundary

The working module is `mundane-map-io-maplibre-style-jackson`, an optional Level 2 adapter using the
already approved and locked Jackson Core line. It depends on `mundane-map-api`, `mundane-map-core`,
and Jackson Core only. It is AWT-free, constructs its JSON factory directly, disables unsupported
JSON extensions, bounds stream constraints, and does not use databind, service discovery, or Jackson
types in public signatures.

The adapter produces immutable source/layer descriptors and the standards-neutral ordered portrayal
plan established by G13. It does not open data sources or render. An application or example binds
source IDs through an explicit immutable registry and attaches the resulting portrayals to ordinary
map bindings.

## Supported document profile

The root requires `version: 8` and a bounded nonempty `layers` array. `name` and JSON-safe `metadata`
may be retained within strict limits. Camera defaults may be retained as metadata but are not
silently applied by the style reader. `sprite`, `glyphs`, `terrain`, `sky`, `light`, `projection`,
`transition`, imports, global state, and unknown rendering-affecting root members are rejected or
reported according to the G14-001 matrix.

Source handling is detached and explicit:

- A layer's `source` is an exact registry key supplied by the application.
- The reader never opens URLs, files, tiles, GeoJSON, or sockets.
- Root `sources` may retain bounded IDs/type metadata for validation, but connection details are not
  executed.
- `source-layer` is rejected in the first profile because vector-tile feature-layer selection is not
  yet a project source contract.
- Every required source and symbol catalog entry is preflighted before a map binding is changed.

Supported layer types are `circle`, `line`, `fill`, and a bounded `symbol` profile. Layer order is
paint order. `minzoom`, `maxzoom`, `visibility`, and supported filters participate in evaluation.
Background, raster, heatmap, fill-extrusion, hillshade, color-relief, custom layers, and 3D behavior
are outside the first profile.

## Paint, layout, and expressions

Literal paint/layout support maps directly to existing symbols:

- circle color, radius, stroke, opacity, translate, and translate anchor;
- line color, width, opacity, cap/join where representable, and bounded offset;
- fill color, opacity, outline color, and caller-catalog pattern where exactly representable;
- symbol icon name, size, rotation, opacity, anchor, offset, overlap/placement priority, and one
  ordinary point-label field/style profile compatible with G11.

Unsupported properties fail or warn according to the profile matrix; they never silently alter
meaning. Transitions and animation are rejected.

The expression subset is closed and typed: literals; `get`, `has`, `geometry-type`, and `zoom`;
comparisons; `all`, `any`, and `!`; `match`, `case`, `step`; linear `interpolate`; and the minimum
color/number/string conversion operations needed by supported properties. Expressions are immutable
packed trees with explicit node/depth/operand/string limits. No `feature-state`, global state,
formatted rich text, locale, image expression, dynamic object/array construction, collator, runtime
code, or extension function is accepted.

G14 extends the G13 closed expression algebra only for a demonstrated supported property. Each
extension must define types, null/missing behavior, numeric finiteness, interpolation, required
attributes, stable failure, and equivalent JVM/native tests. There is no generic JSON expression
evaluator.

## Binding and lifecycle

`MapLibreStyles.read(...)` returns one detached immutable style plan. A separate explicit bind
operation takes a source registry, symbol catalog, and current renderer registry; it validates every
layer transactionally and returns ordered ordinary map bindings. The adapter borrows registries and
sources during preflight but does not close them. Resulting bindings follow existing source/view
ownership rules.

Zoom is derived deterministically from the current Web Mercator viewport only. A style using zoom-
dependent behavior in another CRS is rejected at attachment; there is no guessed zoom/scale
conversion. Non-zoom literal styles may bind to any CRS already supported by the underlying source
and view.

Icon names resolve only through the caller's immutable `NamedSymbolCatalog`. The first profile does
not fetch or decode sprite sheets. Text uses the fixed G11 logical `SansSerif` metric profile; font
stack and glyph URLs are rejected rather than substituted invisibly.

## Limits, diagnostics, and evidence

Limits cover input bytes, JSON depth/tokens/string length, sources, layers, filter/expression nodes,
expression depth, stops/categories, catalog references, and produced symbol/rule counts. Duplicate
JSON object keys are rejected. Non-finite values, invalid colors, invalid enum strings, ambiguous
coercions, and unsupported source/layer/property combinations have stable diagnostics.

Representative codes include:

- `MAPLIBRE_VERSION_UNSUPPORTED`
- `MAPLIBRE_SOURCE_UNRESOLVED`
- `MAPLIBRE_LAYER_UNSUPPORTED`
- `MAPLIBRE_PROPERTY_UNSUPPORTED`
- `MAPLIBRE_EXPRESSION_UNSUPPORTED`
- `MAPLIBRE_EXPRESSION_TYPE`
- `MAPLIBRE_ICON_UNRESOLVED`
- `MAPLIBRE_ZOOM_CONTEXT_UNSUPPORTED`
- `MAPLIBRE_LIMIT_EXCEEDED`

Fixtures include hand-built documents, legally reusable public MapLibre examples reduced to the
approved profile with provenance, hostile JSON, and deterministic bounded mutation. A runnable
gallery demonstrates circle, line, fill, icon, label, filter, category, and zoom cases. Rendering
tests assert ordering, geometry/bounds, representative colors, and visibility rather than exact
pixels. Final publication/consumer and Linux Native Image scenarios use direct Jackson construction
and no service/resource discovery.

## Task sequence

1. G14-001 approves the exact root/source/layer/property/expression matrix.
2. G14-002 creates the optional module with literal circle/line/fill parsing.
3. G14-003 binds explicit sources and implements layer/filter/zoom ordering.
4. G14-004 implements the bounded typed expression subset.
5. G14-005 adds explicit catalog icons and bounded point labels.
6. G14-006 hardens input and approves interoperability fixtures and the gallery.
7. G14-007 closes publication, consumer, and Linux Native Image evidence.
