# G14 — MapLibre Style design

Project index: [DESIGN.md](../DESIGN.md).

## Purpose and compatibility statement

G14 reads a deliberately bounded subset of MapLibre Style Specification version 8 and converts it
to the project's existing symbols, predicates, labels, and ordered portrayal plans. It is an
interchange adapter, not a web-map renderer. The normative references for the selected profile are:

- <https://maplibre.org/maplibre-style-spec/>
- <https://maplibre.org/maplibre-style-spec/root/>
- <https://maplibre.org/maplibre-style-spec/sources/>
- <https://maplibre.org/maplibre-style-spec/layers/>
- <https://maplibre.org/maplibre-style-spec/expressions/>

The support statement is “the mundane-java-map MapLibre v8 vector-style profile”. It must never be
shortened to “MapLibre compatible”. A valid MapLibre document may be rejected when it needs remote
resources, vector tiles, sprites, glyphs, rendering properties, expressions, or layer types outside
this profile.

## Architecture and module boundary

The working module is the optional, published
`mundane-map-io-maplibre-style-jackson`. It has exactly these production dependencies:

```text
mundane-map-io-maplibre-style-jackson
 ├─ mundane-map-api
 ├─ mundane-map-core
 └─ tools.jackson.core:jackson-core:3.1.5
```

It is AWT-free. Jackson is an implementation detail: no Jackson type appears in a public or
protected signature, diagnostic context, or serialized fixture oracle. The module constructs a
`JsonFactory` directly with strict duplicate detection and bounded stream constraints. It does not
use databind, service loading, classpath scanning, reflection, or Jackson's optional service
providers.

The Jackson jar is pinned to SHA-256
`9431b7fa2673bbb618c11d865fe15e13222fd182a214ff998cb7e56afd8f35d2`. The Maven Central POM
and the deterministic offline POM use the already approved G10-025 checksum allowlist. The adapter
ships the Apache-2.0 Jackson notice plus the upstream MIT notices for FastDoubleParser and Schubfach
and uses dependency locking
and the same Native Image service-resource exclusions as the GeoJSON adapter. No MapLibre code is
linked into production; the specification and reduced examples are documentation/test inputs under
their recorded licenses.

The adapter has two phases:

1. `MapLibreStyles.read(...)` parses one byte array into a detached immutable `MapLibreStyle`.
2. `MapLibreStyleBinder.bind(...)` preflights caller-owned sources, catalogs, CRS, and renderer
   support, then returns immutable ordered bindings/portrayals.

Reading never opens a URI, path, source, socket, sprite, glyph, or font. Binding uses only registries
passed explicitly by the caller and is all-or-nothing. A failed bind does not publish a partial
binding and does not close borrowed sources or registries. Successful bindings follow the existing
explicit map/source ownership contract.

## Root matrix

Unknown members are rejected unless this table explicitly retains them. Retained values are
immutable defensive copies and have no rendering effect.

| Root member | Disposition | Exact rule |
| --- | --- | --- |
| `version` | Supported | Required integer exactly `8`; booleans, strings, fractions, and other versions fail. |
| `layers` | Supported | Required nonempty array in paint order; bounded by the layer limit. |
| `sources` | Supported metadata | Required object, which may be empty; only the source profile below is accepted. |
| `name` | Retained | Optional bounded string. |
| `metadata` | Retained | Optional bounded object containing only null, boolean, finite number, and string leaves; nested arrays/objects are rejected. |
| `center`, `zoom`, `bearing`, `pitch` | Retained | `center` is exactly two finite numbers `[longitude,latitude]` in `[-180,180] × [-90,90]`; `zoom` is finite in `[0,24]`; `bearing` is any finite degrees value; `pitch` is finite in `[0,180]`. They are never applied automatically. |
| `sprite`, `glyphs` | Rejected | No resource fetching or implicit catalog/font substitution. |
| `terrain`, `sky`, `light`, `fog`, `projection` | Rejected | Outside the 2D vector profile. |
| `transition` | Rejected | No animation or transition semantics. |
| `imports`, `global-state`, `featuresets`, `schema`, `models` | Rejected | No style composition, runtime state, or extension model. |
| Any other member | Rejected | Stable unsupported-root diagnostic; it is never silently ignored. |

Duplicate object keys at every depth and trailing JSON content are rejected. JSON must be UTF-8
without an external encoding declaration. One leading UTF-8 BOM is accepted and counted against the
input limit; UTF-16/32 BOMs are rejected.

## Source matrix and explicit binding

The only accepted root source descriptor is a detached GeoJSON descriptor:

```json
{
  "type": "geojson",
  "data": "descriptive-locator-only",
  "attribution": "optional display metadata"
}
```

`type` is required and must be `geojson`. `data` is an optional bounded string retained for
provenance/display only; it is never dereferenced. Inline GeoJSON objects and arrays are rejected so
the style reader cannot become a second GeoJSON parser. `attribution` is optional bounded text.
`promoteId`, `generateId`, `cluster`, `clusterProperties`, `clusterMaxZoom`, `clusterRadius`,
`lineMetrics`, `filter`, and unknown members are rejected because they change feature identity,
content, or rendering.

`vector`, `raster`, `raster-dem`, `image`, `video`, and `canvas` source types are rejected. A layer
may refer to a source ID not declared under root `sources`; bind still succeeds when the exact key is
present in the caller registry. A declared descriptor does not grant access to a source. Every
visible layer's `source` must resolve in the immutable caller registry before any output is returned.
Two input registry entries with the same normalized Java map key are impossible; null keys/values
and blank IDs are rejected when the registry is copied.

`source-layer` is always rejected in this profile. Vector-tile sublayer selection has no current
`FeatureSource` equivalent.

## Common layer matrix

Each layer is an object with these common members. Layer order is JSON array order and later layers
paint above earlier layers.

| Member | Disposition | Exact rule |
| --- | --- | --- |
| `id` | Supported | Required nonblank bounded string, unique by exact code-point equality. |
| `type` | Supported | Required: `circle`, `line`, `fill`, or bounded `symbol`. |
| `source` | Supported | Required nonblank exact registry key. |
| `source-layer` | Rejected | No vector-tile layer contract. |
| `minzoom` | Supported | Optional finite number in `[0, 24]`, inclusive lower bound; default `0`. |
| `maxzoom` | Supported | Optional finite number in `(0, 24]`, exclusive upper bound; default `24`; must exceed `minzoom`. |
| `filter` | Supported | Expression-form filter from the closed operator table; legacy filter syntax is rejected. |
| `layout` | Supported subset | Object; absent means property defaults. |
| `paint` | Supported subset | Object; absent means property defaults. |
| `metadata` | Retained | Same bounded scalar-leaf object profile as root metadata. |
| Any other member | Rejected | Includes `slot`; stable layer-member diagnostic. |

`layout.visibility` accepts `visible` (default) and `none`. A `none` layer remains in the detached
model but produces no binding and its source need not resolve. Property transition keys ending in
`-transition` are rejected.

Geometry roles are exact: circle accepts point and multipoint; line accepts line and multiline; fill
accepts polygon and multipolygon; symbol accepts singular points in the first profile. An
incompatible geometry does not match that layer. It is not an error and cannot be restyled into a
different role.

### Per-property expression matrix

Literal values are accepted for every supported property. Expressions are additionally accepted
only as follows:

| Property group | Additional accepted expression shapes |
| --- | --- |
| Layer `filter` | Boolean/comparison algebra in the operator table; `get`, `has`, and `geometry-type`; constants are folded. |
| Circle radius/color/opacity/stroke width/stroke color/stroke opacity | `case`, `match`, or `step` with literal results; direct-input linear `interpolate`. |
| Line color/width/opacity | Same closed shapes as circle paint. |
| Fill color/opacity/outline color | Same closed shapes as circle paint. |
| `icon-image` | Direct `get`; `case` or `match` with literal catalog-name results; `to-string(get)`. |
| `text-field` | Direct `get` or `to-string(get)` only. This is the only direct property-result form. |
| Every other supported layout/paint property | Literal only. |

`zoom` is admitted only as the direct input of outer `step` or linear `interpolate` for the paint
properties listed above. Composite data-and-camera expressions are rejected. `case`/`match`/`step`
results must be literals of the property's required type. This intentionally smaller matrix avoids
a second general styling runtime while leaving category, threshold, and ramp behavior useful.
`to-number` is accepted only as the direct input of `match`, `step`, or `interpolate`; an arbitrary
direct data-driven paint value is not supported. `to-color` is rejected because this profile admits
only literal branch/stop colors and has no faithful arbitrary data-color selector.

## Literal paint and layout matrices

Colors accept `#RRGGBB` and `#RRGGBBAA` only. Components use sRGB bytes and alpha multiplies the
separate opacity. Named colors, CSS functions, HSL, lab, and interpolation color spaces are
rejected. Numeric values must be finite. Lengths are screen pixels unless stated otherwise.

### Circle

| Property | Support and default |
| --- | --- |
| `circle-radius` | Number `[0, 1_024]`; default `5`. |
| `circle-color` | Color; default `#000000`. |
| `circle-opacity` | Number `[0, 1]`; default `1`. |
| `circle-stroke-width` | Number `[0, 1_024]`; default `0`. |
| `circle-stroke-color` | Color; default `#000000`. |
| `circle-stroke-opacity` | Number `[0, 1]`; default `1`. |
| `circle-translate` | Two-number array, each in `[-65_536, 65_536]`; default `[0,0]`. |
| `circle-translate-anchor` | `viewport` only; default `map` is accepted only when translate is zero. |

`circle-blur`, `circle-pitch-alignment`, `circle-pitch-scale`, `circle-emissive-strength`,
`circle-elevation-reference`, sort keys, and unknown circle properties are rejected.

Circle construction preserves MapLibre's outside-stroke geometry. A visible stroke is one
fill-only annulus with an outer radius of `radius + stroke-width` and inner radius `radius`, followed
by an independently composited fill-only disk of radius `radius`. The toolkit-neutral annulus uses
oppositely wound closed vector subpaths, so translucent fill never exposes stroke beneath the
interior. The accepted marker's full screen bounds are `2 × (radius + stroke-width)`. Zero-width or
transparent strokes omit the annulus. It is not mapped to a centered Java2D stroke.
Radius zero omits the interior disk; a positive stroke width may still produce the specified outer
disk. Radius and stroke width both zero omit the marker.

### Line

| Property | Support and default |
| --- | --- |
| `line-color` | Color; default `#000000`. |
| `line-width` | Number `[0, 1_024]`; default `1`. |
| `line-opacity` | Number `[0, 1]`; default `1`. |
| `line-cap` | Layout value `round` only. It must be explicit for a visible line because the project cannot reproduce MapLibre's default `butt` cap. |
| `line-join` | Layout value `round` only. It must be explicit because the project cannot reproduce the default `miter` join exactly. |
| `line-offset` | Only numeric zero. |

`line-gap-width`, `line-blur`, `line-dasharray`, `line-pattern`, `line-gradient`,
`line-translate`, `line-trim-offset`, elevation, sort, miter/round limits, layer opacity, and
unknown line properties are rejected.
Line width zero omits the line instead of constructing a degenerate `SymbolStroke`.

### Fill

| Property | Support and default |
| --- | --- |
| `fill-color` | Color; default `#000000`. |
| `fill-opacity` | Number `[0, 1]`; default `1`. |
| `fill-outline-color` | Optional color; absent inherits the evaluated fill RGB with intrinsic alpha `1`, matching MapLibre's implicit outline behavior. The project renders it as its existing one-screen-pixel solid polygon outline; `fill-opacity` still multiplies both fill and outline. |
| `fill-antialias` | Literal `true` only; default `true`. |
| `fill-translate` | Only literal `[0,0]`. |
| `fill-translate-anchor` | Accepted only with zero translate. |

`fill-pattern`, sort keys, layer opacity, elevation, and unknown fill properties are rejected.
MapLibre patterns are not mapped to the project's hatch catalog because their repeat, sprite, and
coordinate semantics differ.

## Filters, expressions, and portrayal representation

The adapter parses a closed typed algebra, not arbitrary JSON expressions. The supported operators
are:

| Family | Operators and restrictions |
| --- | --- |
| Values | scalar `literal`; `get` and `has` with one literal attribute name; `geometry-type`; `zoom` |
| Comparison | `==`, `!=`, `<`, `<=`, `>`, `>=`; two operands; no collator |
| Boolean | `!`, `all`, `any`; `all`/`any` contain at least one operand |
| Branching | `match`, `case`, `step` with bounded branches/stops and one statically compatible result type |
| Interpolation | `interpolate` with `["linear"]`, numeric/zoom input, strictly increasing finite stops, and numeric or color outputs |
| Conversion | One-input `to-string`; one through eight ordered inputs for `to-number` |

Arrays are expressions only when their first item is a supported operator string. Other arrays,
including expression-valued property arrays, require `["literal", value]`; only the fixed property
array shapes named above are accepted. `let`, `var`, feature/global state, `id`, `properties`,
`at`, arithmetic, string operations, `coalesce`, `format`, `image`, locale/collator operations,
runtime object creation, `to-color`, and every unlisted operator are rejected even when unreachable.

The standards-neutral representation remains one portrayal path:

- filters compile to `PortrayalPredicate`. G14-003 adds the narrowly standards-neutral
  `Exists(property)`, `GeometryTypeIs(types)`, and `Constant(boolean)` variants needed for `has`,
  `geometry-type`, and constant-folded filters. `IsNull` remains explicit-null only; `get` equality
  to null compiles to `NOT Exists OR IsNull` so missing and null retain MapLibre behavior.
  `PortrayalEvaluationContext` gains an optional closed `PortrayalGeometryType` input set by the
  resolver from the current geometry before predicate evaluation. `PortrayalOperand` gains typed
  null/boolean/finite-decimal/string literals, retaining `"5"` and `5` as different values;
- fixed results use `FixedSymbolSelector`;
- `match` uses `CategoricalSymbolSelector` when it has one direct `get` input;
- `step` uses `GraduatedSymbolSelector` when it has one direct numeric `get` input;
- other `case`/`match`/`step` forms compile to bounded ordered `PortrayalRule` instances;
- a layer filter wraps the selected role in one closed `FilteredSymbolSelector` guard. The guard
  contains only a bounded `PortrayalPredicate`, preserves the delegate role, is not nestable, and
  is tested before its delegate is evaluated;
- G14-004 adds one project-owned, Jackson-free `InterpolatedSymbolSelector` only for a direct numeric
  attribute or Web Mercator zoom and numeric/color symbol properties that cannot be represented by
  existing selectors.

That selector stores normalized finite stops and already constructed endpoint symbols. Core performs
linear component interpolation. It is sealed into `SymbolSelector`, capped at 64 stops, supports
marker/line/fill roles, and exposes no general expression or adapter type. G14-003 also extends
`PortrayalEvaluationContext` with optional finite `zoomLevel`, preserving the existing unscaled and
scale-only factories, and adds `atScaleAndZoom`. AWT derives a new immutable context when viewport
resolution changes; `FeaturePortrayalResolver.resolveAll` passes the same context to rules and
selectors. Resolver construction indexes the new selector explicitly rather than relying on a
fallback cast. The portrayal cache key includes zoom for zoom-dependent plans and ignores it for
data-only plans; viewport zoom invalidates only dependent captured portrayals. One layer may contain
at most one data- or zoom-dependent paint expression. All other
paint/layout values must be literal. The adapter never forms a cross-product between categorical,
stepped, conditional, or interpolated properties; a second dynamic property fails read-time
validation with `MAPLIBRE_EXPRESSION_UNSUPPORTED`. This is the
only value-producing selector extension required by the profile; the closed filter guard supplies
composition rather than another expression result model. The predicate, evaluation-context, and point-label
extensions named above are the complete remaining standards-neutral API changes; the adapter adds
no generic style/expression API.

Literal, `match`, `case`, and `step` results that produce a valid zero-size or fully omitted role use
an internal omission sentinel which the resolver removes before renderer preflight and resolved
output. Linear interpolation endpoints must each materialize a structurally compatible, non-omitted
symbol; an endpoint that would omit the role is rejected at read time with
`MAPLIBRE_EXPRESSION_TYPE`. This narrow restriction avoids introducing degenerate zero-sized public
symbol values solely for one adapter.

### Types and missing values

Static types are null, boolean, number, string, color, geometry-type, and symbol-property result.
There is no implicit string/number/boolean coercion. Numeric comparison compares normalized finite
decimals; string comparison is Unicode code-point order; equality requires the same type. Branches
are lazy.

Internally, missing is distinct from explicit `AttributeNull`. `has` is true for a present null
attribute. `get` returns null for both missing and explicit null, matching the observable MapLibre
value model. In a filter, missing/null/type mismatch evaluates false for ordering and type-strict
non-null comparisons. Equality to null is true and inequality to null is false for either missing
or explicit null; inequality reverses type-strict equality for all non-null values. In a
paint/layout expression, a missing, null, failed
conversion, non-finite value, or type mismatch is an evaluation error and uses that property's
documented MapLibre default. It never omits the geometry merely because one property failed.
Runtime feature-data failures do not create per-feature diagnostics or expose raw values.

`to-string` has exactly one input and implements the specification's null/boolean/finite-number/
color/string conversions. `to-number` has one through eight ordered candidate inputs and returns
the first successful conversion; exhaustion is an evaluation error. `to-number`
uses the specification's null/boolean and ECMAScript-string numeric results but rejects a
non-finite result. There is no adapter-invented conversion fallback operand.

G14-004 admits `to-number` only as the input to numeric `match`, `step`, and `interpolate`
properties. Such a dynamic property must contain at least one direct `get` candidate; an
all-literal conversion is written as the equivalent literal property value instead of retaining an
expression that cannot vary by feature or zoom. `to-string` first has an observable consumer in
G14-005's simple `text-field` profile and is implemented and verified there; G14-004 does not add
an otherwise unused general conversion runtime.

Required attributes are discovered exactly at read time and passed to source queries. One immutable
evaluation result per feature/layer/viewport is captured and reused for paint, hit testing, hover,
selection, and export so those paths cannot disagree.

### Zoom

For Web Mercator only, fractional zoom is derived from horizontal world-units per screen pixel:

```text
zoom = log2((2 × π × 6_378_137) / (512 × resolution))
```

Layer `minzoom` is inclusive and `maxzoom` is exclusive. Layout expressions evaluate `floor(zoom)`;
paint expressions use fractional zoom. Filters do not admit `zoom` in this profile. Values outside
`[0,24]` remain finite and are
tested against the layer range; they are not silently clamped. A style containing any zoom
expression may bind only to the explicit EPSG:3857 CRS registration. Literal styles and layer
min/max ranges can bind elsewhere only when the caller supplies an explicit zoom value in the bind
context; no scale-denominator guess is made.

## Symbol-layer profile

Only point placement is supported. `symbol-placement` must be absent or `point`.

### Icons

`icon-image` is required for every supported symbol layer, including a layer with text, and is
either a literal catalog name, direct `get` string, or a
bounded `match`/`case` choosing literal names. Names resolve only through the caller's immutable
`NamedSymbolCatalog`. Every statically named symbol is preflighted; a runtime name not in the catalog
is an expression evaluation error and uses the property's default (no icon). Catalog entries are
restricted to `VectorMarkerSymbol` and `RasterIconSymbol` with intrinsic screen-pixel placement;
composite, map-unit, and custom marker renderers are
rejected because their intrinsic placement cannot be reconstructed faithfully. Binding defensively
reconstructs the selected built-in marker with the MapLibre size, anchor, offset, rotation,
rotation mode, and opacity while preserving vector path/paint or raster pixels/interpolation.
When `text-field` is present, `icon-image` must be one literal catalog name that resolves during
preflight; data-dependent icon selection is admitted only for icon-only layers. Thus the current
label contract never encounters a selected label after its marker disappears.

Supported properties are fixed `icon-size` `(0,128]` (default `1` multiplier), fixed `icon-rotate`
finite degrees (default `0`), fixed `icon-opacity` `[0,1]` (default `1`), the nine fixed MapLibre
`icon-anchor` values (default `center`), fixed two-number `icon-offset` (default `[0,0]`), and fixed
`icon-rotation-alignment` values `map` and `viewport` (default `auto`, mapped to `viewport` for
point placement). Icon offsets are intrinsic icon pixels and are multiplied by `icon-size` before
mapping to screen pixels. A nonzero offset is accepted only when `icon-rotate` is zero, because the
current placement model does not rotate its offset. To match current marker placement,
`icon-allow-overlap` and
`icon-ignore-placement` must both be explicitly literal `true`; their MapLibre defaults are not
silently changed. `icon-optional` must be literal `true` when text is also present.

Sprite URLs/sheets, `image` expressions, padding, sort keys, text-fit, pitch alignment, occlusion,
emissive/elevation properties, and unknown icon properties are rejected.

### Point labels

Text-only symbol layers are rejected by the required `icon-image` rule. `text-field` is either a
literal bounded string or a direct `get`/`to-string(get)` attribute
expression. It maps to the G11 point-label value. Labels use the fixed logical `SansSerif` plain
profile; `text-font` is required and must be exactly `["SansSerif"]`, so the MapLibre default font
stack is never silently substituted.

G14-005 adds `CENTER` to `PointLabelPosition` and a standards-neutral `PointLabelAnchorBasis`
(`MARKER_BOUNDS` or `FEATURE_POINT`) to `PointLabelProfile`; the compatibility constructor/factory
continues to use marker bounds. The AWT label placer interprets the new basis from the projected
feature point, permits the center position, and applies point-relative em offsets before collision
testing. This reuses the existing bounded G11 placement/collision pass without pretending that its
marker-gap semantics are MapLibre semantics.

Supported fixed properties are: `text-size` `[1,512]` pixels (default `16`); `text-color` (default
`#000000`); `text-opacity` `[0,1]` (default `1`); one fixed `text-anchor` (default `center`);
`text-variable-anchor` as one through nine unique anchors, which takes precedence over
`text-anchor`; `text-offset` as a two-number em array (default `[0,0]`); nonnegative
`text-radial-offset` in ems (default `0`, mutually exclusive with nonzero `text-offset`);
`text-padding` `[0,64]` pixels (default `2`); and `symbol-sort-key` as an exactly integral value in
`[-1_000_000_000,1_000_000_000]`. Binding maps it to priority `-sort-key`, so lower MapLibre keys
are admitted first without lost ordering. `symbol-z-order` is required and must be `source`; the
default `auto` and viewport-y ordering are not represented. `text-allow-overlap` and
`text-ignore-placement` must be absent or literal `false`, matching G11 collision placement.
`text-optional` must be explicit literal `true`, ensuring an icon remains valid when the label
collides. Label layer and source-feature order remain deterministic tiebreakers. Requiring an icon
and optional text preserves the current `FeaturePortrayal` invariant; G14-005 therefore changes only
label anchor/basis placement, not label ownership or icon/label coupling.

Line/curved labels, rich/formatted text, font stacks other than the fixed logical font, glyph URLs,
letter spacing, justification, case transformation, writing modes, halos, shadows, text rotation,
pitch alignment, max width/line height, and unknown text properties are rejected. This first profile
is single-line.

## Limits and allocation policy

Public `MapLibreReadLimits` exposes defaults and hard maxima. Values are validated before parsing.

| Limit | Default | Hard maximum |
| --- | ---: | ---: |
| Input bytes | 4 MiB | 64 MiB |
| JSON nesting depth | 64 | 256 |
| JSON tokens | 500,000 | 5,000,000 |
| One string / aggregate string chars | 65,536 / 2 MiB | 1 MiB / 32 MiB |
| Object members | 100,000 | 1,000,000 |
| Sources / layers | 256 / 1,024 | 4,096 / 16,384 |
| Metadata entries | 256 | 4,096 |
| Expression nodes / depth | 8,192 / 32 | 131,072 / 64 |
| Stops or categories per expression | 64 / 256 | 2,048 / 4,096 |
| Catalog references | 1,024 | 16,384 |
| Produced rules | 4,096 | 4,096 |
| Estimated owned bytes | 32 MiB | 512 MiB |

Counters are aggregate across the document. String length is checked before copying where Jackson
permits. The parser checks cancellation at root members, every source/layer, and every 256 tokens or
expression nodes. Limits fail before growing the next collection. Recursion never exceeds the
configured expression/JSON depth. Parsed collections and byte arrays are defensively copied; packed
primitive arrays hold expression operands/stops where useful.

## Diagnostics

`MapLibreReadException` and bind failures carry one stable code, phase (`read` or `bind`), a
specification location made from fixed member names plus source/layer/operand ordinals, and
non-sensitive numeric context. They do not contain raw JSON, layer IDs, source paths/URIs, feature
attributes, Jackson messages/classes, or platform-dependent exception text.

The closed code set is:

- `MAPLIBRE_JSON_INVALID`
- `MAPLIBRE_VERSION_UNSUPPORTED`
- `MAPLIBRE_ROOT_UNSUPPORTED`
- `MAPLIBRE_SOURCE_UNSUPPORTED`
- `MAPLIBRE_LAYER_UNSUPPORTED`
- `MAPLIBRE_PROPERTY_UNSUPPORTED`
- `MAPLIBRE_EXPRESSION_UNSUPPORTED`
- `MAPLIBRE_EXPRESSION_TYPE`
- `MAPLIBRE_VALUE_INVALID`
- `MAPLIBRE_SOURCE_UNRESOLVED`
- `MAPLIBRE_ICON_UNRESOLVED`
- `MAPLIBRE_RENDERER_UNAVAILABLE`
- `MAPLIBRE_ZOOM_CONTEXT_UNSUPPORTED`
- `MAPLIBRE_LIMIT_EXCEEDED`
- `MAPLIBRE_CANCELLED`

Read-time grammar/type/unsupported errors outrank later semantic checks in document order. Bind
preflights sources, catalogs/renderers, then CRS/zoom, all in layer order. Deterministic mutation and
hostile tests assert codes and fixed locations, never raw parser messages.

## Fixtures, rendering, native, and publication evidence

Hand-built fixtures own the complete boundary matrix. Reduced examples copied from official
MapLibre documentation record the source URL, retrieval date, upstream license, SHA-256, exact
modifications, and expected supported or rejected result. No test downloads data. Public showcase
styles that require resources remain negative compatibility fixtures rather than widening the
profile.

The gallery uses in-memory caller sources and catalogs and demonstrates literal circle/line/fill,
filters, category/step/linear interpolation, zoom, icons, point labels, missing data, and ordering.
Rendering regression asserts geometry, bounds, representative colors, collision counts, and
tolerances rather than cross-platform pixel identity.

The final staged consumer uses only published artifacts from the dry-run repository and Java 21.
The Linux Native Image smoke shares the JVM scenario for direct Jackson parse, expression
evaluation, transactional bind, icon/label resolution, render, and one stable rejection. Exact
resource and service inventories prove that no Jackson service discovery or reflection metadata is
needed. Windows/macOS Native behavior is not claimed without separate evidence.

## Vertical task sequence

1. G14-001 freezes this exact profile and adapter boundary without creating a module.
2. G14-002 creates the optional module with real literal circle/line/fill parse-to-render behavior.
3. G14-003 adds explicit source binding, filters, zoom, ordering, and lifecycle.
4. G14-004 adds the closed expression algebra and the narrowly scoped interpolation selector.
5. G14-005 adds explicit-catalog icons and the bounded G11 point-label mapping.
6. G14-006 closes provenance, hostile/mutation hardening, gallery, and tolerant regression evidence.
7. G14-007 closes Javadocs, locks/notices, staged consumer, Linux Native Image, and the holistic
   G12–G14 portrayal review.
