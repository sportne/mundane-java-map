# MapLibre Style adapter capability intent

`mundane-map-io-maplibre-style-jackson` is the project's optional Jackson Core adapter for bounded,
detached MapLibre Style document interchange and compilation into toolkit-neutral 2D portrayal. The
approved completion baseline is `@maplibre/maplibre-gl-style-spec` **v26.2.1**, release commit
`7a2420b`; documents still carry style `version: 8`.

Completion includes deterministic reading and writing of the entire pinned document vocabulary. The
2D renderer implements every applicable 2D layer/source behavior. Terrain, fill extrusion, sky/fog,
models, and non-2D projection state are parsed, validated, retained, and written, but binding them to
MundaneJ's 2D renderers fails with an explicit non-renderable-capability diagnostic rather than a
misleading approximation.

Detached parsing and writing perform no I/O beyond caller-owned streams. Binding resolves resources
only through an explicit caller policy or offline catalog. A locator in a style never grants network,
filesystem, credential, redirect, font, codec, or execution authority.

The root README describes released behavior. Target rows below become release claims only as their G19
cards close.

## Frozen standards boundary

| Surface | Approved target | Claim boundary |
| --- | --- | --- |
| MapLibre Style Specification | Package v26.2.1 / style document version 8 | Exact generated inventory from the tagged `v8.json`; later additions require a reviewed profile update |
| JSON document | Strict UTF-8, duplicate-key rejection, immutable bounded values | Semantic preservation, not source whitespace/member-order/number-spelling preservation |
| Legacy v8 filters/functions | Read and deterministically migrate with diagnostics | Canonical writer emits only current expression syntax; ambiguous/mixed forms reject |
| Rendering | Complete applicable 2D behavior | No claim of GPU pixel identity, 3D terrain/extrusion/model rendering, or MapLibre application runtime parity |
| Resources | Caller-authorized online resolver or explicit offline catalog | No ambient fetch, credential discovery, font discovery, or locator-derived authority |
| Writing | Complete deterministic v26.2.1 style JSON | No output of deprecated syntax and no silent loss of preserved non-renderable constructs |

## Pinned document inventory

The v26.2.1 reference contains 19 root members, six source types, ten layer types, 87 expression
operators, and the property inventories generated from the release artifact. Verification must fail
when the vendored inventory and this matrix drift.

### Root and global state

| Surface | Completion behavior | Card |
| --- | --- | --- |
| `version`, `name`, `metadata` | Complete typed immutable values; version exactly 8 | G19-140 |
| Camera | `center`, `centerAltitude`, `zoom`, `bearing`, `pitch`, and `roll`, with exact ranges/default application policy | G19-140 |
| Resources | single/multiple `sprite`, `glyphs`, and `font-faces`, retained detached and resolved only by policy | G19-144 |
| Runtime/default state | `transition` and `state`, including typed global-state expression inputs and bounded transition clock | G19-140, G19-141 |
| Global visual state | `light`, `terrain`, `sky`, and `projection` modeled and written; only applicable 2D projection behavior binds | G19-140 |
| `sources`, `layers` | Complete ordered/identified model and cross-reference validation | G19-140 through G19-147 |
| Unknown/future members | Rejected by the pinned strict profile unless a registered extension codec explicitly owns the member | G19-140 |

Mapbox-only or later MapLibre constructs absent from the v26.2.1 reference are not implicitly accepted.
They require a future version-profile decision rather than being treated as harmless foreign members.

### Sources

| Type | Completion behavior | Card |
| --- | --- | --- |
| `geojson` | Inline or authorized external complete GeoJSON; clustering, filters, IDs, line metrics, bounds, buffering, and update semantics | G19-142 |
| `vector` | URL/inline TileJSON, tile templates, source layers, schemes, bounds, zooms, attribution, volatility, promote-ID, and pinned vector-tile decoding | G19-142 |
| `raster` | TileJSON/templates, tiles, bounds/scheme/zoom/tile size, attribution, volatile/cache behavior, and explicit raster decoders | G19-143 |
| `raster-dem` | Mapbox, Terrarium, and custom encodings with dimensional/unit/no-data policy into neutral elevation | G19-143 |
| `image` | Authorized image plus four-corner georeferencing, updates, resampling, and lifecycle | G19-143 |
| `video` | Complete locator/corner model and host-neutral time-indexed decoded-frame provider; timing/playback/cancellation without a built-in codec | G19-143 |

Resource retrieval uses exact allowlists for origins/paths, redirects, schemes, headers/credentials,
media types, encoded/decoded sizes, dimensions, timeouts, concurrency, retries, caches, and lifetime.
Offline catalogs can satisfy every resource reference without network access. Style-relative resolution
does not escape the base authority and never converts metadata into executable code.

### Layers and 2D binding

| Layer | Document support | 2D rendering target | Card |
| --- | --- | --- | --- |
| `background` | Complete | Color/pattern, opacity, transition, pitch/viewport semantics where applicable | G19-145 |
| `circle` | Complete | All layout/paint/data-driven properties with exact visible geometry and ordering | G19-145 |
| `fill` | Complete | Fill/outline/pattern/translate/sort/elevation-reference behavior applicable in 2D | G19-145 |
| `line` | Complete | Caps/joins/gap/offset/dash/pattern/gradient/trim/translate/sort behavior applicable in 2D | G19-145 |
| `symbol` | Complete | Icon/text shaping, placement, collision, ordering, overlap, variable anchors, formatted content, and transitions | G19-146 |
| `heatmap` | Complete | Bounded kernel accumulation, color ramp, opacity/radius/weight/intensity, zoom and ordering | G19-147 |
| `raster` | Complete | Opacity/color controls, contrast/saturation/hue/brightness, fade/resampling and placement | G19-147 |
| `hillshade` | Complete | DEM-derived illumination, accent/shadow/highlight, exaggeration/resampling and edge policy | G19-147 |
| `color-relief` | Complete | DEM color ramp/resampling/opacity with bounded interpolation | G19-147 |
| `fill-extrusion` | Complete model/write | Explicitly non-renderable by the 2D binder; no footprint approximation masquerading as extrusion | G19-140 |

Common layer behavior includes exact IDs/order, source/source-layer, zoom bounds, metadata, visibility,
filters, min/max zoom, transitions, feature/global state, elevation references, and source compatibility.
The binder either compiles a complete immutable scene plan or publishes nothing.

## Expression and filter contract

- Implement all 87 operators from v26.2.1 with the reference type system, overload resolution,
  evaluation errors, short circuiting, scopes, `let`/`var`, assertions/coercions, arrays/objects,
  feature properties/ID/state, global state, geometry, elevation, zoom/line/heatmap/accumulation context,
  math, strings, locale/collation, formatting/images, colors, interpolation, and spatial predicates.
- Each layout/paint/source property admits only the expression parameters declared by the pinned
  reference, including data-driven, camera, composite, feature-state, and global-state restrictions.
- Legacy filters/property functions are parsed in a separate closed grammar, normalized to current
  expressions, and reported. Mixed or semantically ambiguous syntax rejects; canonical output never
  emits legacy forms.
- Evaluation is deterministic for an explicit immutable context. Locale, Unicode, clock, state,
  resources, geometry, and camera inputs are supplied rather than discovered from the process.
- AST depth/nodes, literals, strings/code points, arrays/objects, variables, branches/stops, formatted
  spans, collator work, geometry predicates, evaluations, transition samples, owned bytes, and total
  compile/render work are charged prospectively.

## Sprite, glyph, font, and symbol contract

- Support single and multiple sprite definitions, pixel ratios, sprite metadata, atlas bounds, SDF
  semantics, patterns, icon expressions, missing-image behavior, and caller-registered runtime images.
- Support glyph range resources and `font-faces` through explicit catalogs/resolvers. No OS font scan,
  fallback guess, or implicit web-font download occurs. Fonts and glyphs carry provenance/licensing.
- Shape Unicode text with explicit language/locale/script/direction, bidi, line breaking, letter spacing,
  formatted sections, writing modes, wrapping, justification, anchors, offsets, line/polygon placement,
  collision groups, padding, optionality, and repeat-distance rules.
- AWT, Vaadin, hit testing, labels, and SVG export consume one neutral shaped-placement result and state
  documented rasterization tolerances. Missing or unsupported shaping fails or follows an explicit
  caller fallback; it never silently substitutes a platform font.

## Video-source contract

- The adapter does not decode media containers or codecs. A caller-provided immutable frame provider
  exposes bounded decoded frames, timestamps/duration, seek/play/pause state, cancellation, and close.
- Four geographic corners define the projective 2D placement. Frame selection, update ordering,
  resampling, alpha/color space, end/loop behavior, scene invalidation, and failure recovery are explicit.
- URI authorization and byte retrieval remain separate from decoding; providing a locator never installs
  a codec or executes embedded media metadata.

## Deterministic writer and migration contract

- Write all pinned root/source/layer/property/expression values, including retained non-renderable 3D
  constructs, in frozen member order followed by explicitly registered extension members.
- Use stable UTF-8, escaping, finite-number/color/value formatting, expression normalization, source and
  layer order, and no insignificant whitespace. Identical semantics/options produce identical bytes.
- Current expressions are the only output grammar. Legacy input emits migration observations and writes
  its typed current equivalent; a lossy or ambiguous migration is rejected before output.
- Preflight references, types, expressions, resources, representability, extensions, and exact limits.
  Files use atomic replacement; stream sinks document the committed-byte boundary and aggregate cleanup.
- Deterministic project output is not a claim of RFC 8785 JSON canonicalization or preservation of source
  formatting/member order/numeric spelling.

## Deliberate exclusions

- Built-in video/container/audio codecs, FFmpeg/JNI, shader execution, custom WebGL layers, JavaScript,
  arbitrary code expressions, ambient network/files/font discovery, DRM, and credential inference.
- 3D terrain mesh, fill-extrusion, model, sky/fog, globe/vertical-perspective rendering, GPU/pixel identity,
  and application UI/camera runtime parity. These constructs remain valid document-interchange data.
- Mapbox proprietary URL/authentication conventions and any member/operator added after v26.2.1 until a
  separately reviewed version update.

## Completion evidence

- Generate exact root/source/layer/property/expression inventories from the pinned release and fail CI on
  unexplained drift. Exercise every row as supported, preserved/non-renderable, migrated, or excluded.
- Use official examples/reference tests where licensing permits plus provenance-recorded independent style,
  TileJSON, tile, sprite, glyph, font, image, video-frame, and DEM fixtures.
- Compare document validation/writing with the reference package and 2D visual/placement behavior with
  MapLibre GL JS using declared tolerances; test AWT/Vaadin/SVG consistency.
- Cover hostile JSON/resources, SSRF/redirect/path/credential cases, malformed fonts/images/tiles, limits,
  cancellation, cache/ownership cleanup, fuzzing, native/offline/publication, and public documentation.
- G19-149 closes the module only when this matrix, implementation, evidence, examples, diagnostics,
  dependencies, and support wording agree.
