# SVG adapter capability intent

`mundane-map-io-svg` is the project's JDK-only adapter for bounded static SVG map symbols and
deterministic accessible map export. Its approved target is a named, secure, non-interactive SVG 2
profile—not a browser, DOM implementation, animation engine, or general web-content runtime.

Static documents may contain vector geometry, text, reusable definitions, paint servers, markers,
clipping, masks, embedded raster images, and the approved bounded filter graph. Same-document data
and resources from an explicit caller-supplied closed catalog are permitted. Scripts, event handlers,
animation, `foreignObject`, and ambient filesystem/network loading are always prohibited.

The root README describes released behavior. Target rows below become release claims only when the
corresponding G19 cards close.

## Standards and conformance boundary

| Standard/profile | Approved use | Claim boundary |
| --- | --- | --- |
| [W3C SVG 2](https://www.w3.org/TR/SVG2/), Candidate Recommendation Snapshot 4 October 2018 | Primary element, geometry, coordinate-system, painting, text, linking, rendering, accessibility, and static processing baseline | Claim the documented restricted static processing profile, not a complete dynamic conforming browser/viewer |
| SVG 1.1 Second Edition | Backward-compatible interchange evidence for common static documents | Accept only constructs also covered by the approved static matrix; no deprecated scripting/animation expansion |
| XML 1.0 and Namespaces in XML | Secure SVG XML serialization | Hardened StAX; no DTD, entities, XInclude, external schema, or ambient resolver |
| CSS Syntax/Cascade/Selectors/Color/Values modules pinned by G19-082 | Style attributes, presentation attributes, style sheets, cascade, inheritance, colors, and units | Only the documented SVG presentation-property and selector/value profile, not arbitrary web layout CSS |
| [Filter Effects Module Level 1](https://www.w3.org/TR/filter-effects-1/) | Semantics for the explicitly approved bounded filter primitives | The W3C document remains a Working Draft; support is an interoperability profile, not a Recommendation conformance claim |
| WAI-ARIA Graphics and SVG accessibility guidance pinned by G19-089 | Accessible generated documents | Generator/accessibility evidence only; no claim that the adapter is an assistive-technology user agent |

## Secure processing and resource policy

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| XML/document processing | Root XML SVG with declarations/constructs narrowly rejected | XML declaration/encoding, namespaces, metadata/title/description, conditional static processing, and deterministic unsupported handling under hardened StAX | G19-080 |
| Execution | No scripts/network | Continue rejecting script, event attributes, SMIL/CSS animation, dynamic DOM behavior, `foreignObject`, and browser integration | G19-080 |
| References | None | Same-document fragments plus explicit caller catalog entries with typed bytes/media/identity; bounded cycles/depth/fan-out | G19-080, G19-083 |
| External resources | Rejected | Embedded `data:` resources and caller-catalog images/fonts/stylesheets/SVG fragments only | G19-080, G19-085, G19-086 |
| Ambient I/O | Local top-level file only | Top-level caller file/bytes plus explicit catalog; referenced `file:`, `http:`, classpath, system-font, and other implicit lookup remain prohibited | G19-080 |

Catalog authorization is not URI fetching. A reference resolves only when its normalized identifier
exactly matches a caller-registered typed entry; redirects, relative path traversal, MIME sniffing,
implicit fonts, and fallback to operating-system resources are not permitted.

## Static SVG feature matrix

| Area | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Geometry | `path`, `rect`, `circle`, `ellipse`, `line`, `polyline`, `polygon` | Complete static geometry/path grammar, degenerate rules, geometry properties, vector effects, and marker placement | G19-081, G19-083 |
| Viewports/units/transforms | Required root `viewBox`, one aspect mode, unitless values, group/shape transforms | Root/nested `svg`, `symbol`, `use`, viewport/viewBox/preserveAspectRatio, overflow, supported absolute/font/percentage units, transform origins, and exact coordinate composition | G19-081 |
| CSS/style | Direct inherited fill/stroke subset | Presentation attributes, `style`, embedded/catalog stylesheets, pinned selectors, specificity/order/important, inheritance, `currentColor`, custom properties if approved, and complete supported property/value grammar | G19-082 |
| Paint/reuse | Solid colors only | Solid/current/context paints, linear/radial gradients, patterns, `defs`, `symbol`, `use`, markers, href inheritance, spread/units/transforms, and fallback paint | G19-083 |
| Compositing | Per-shape opacity | Group opacity, paint order, clipping paths, alpha/luminance masks, display/visibility, overflow, and isolated bounded offscreen composition | G19-084 |
| Text | Rejected | `text`, `tspan`, `textPath`, whitespace, anchors, baselines, bidi/writing mode, letter/word spacing, decoration, and deterministic shaping through explicitly registered fonts | G19-085 |
| Raster images | Rejected | Static PNG/default-APNG and common JPEG through the image decoder, embedded or catalog-resolved, with viewport/aspect/opacity/color policy | G19-086 |
| Filters | Rejected | Bounded graphs using Gaussian blur/drop shadow, offset, flood, color matrix, blend, composite, merge, and morphology | G19-087 |
| Metadata/accessibility | Rejected on import; basic export root | Preserve bounded title/description/language/metadata/ARIA/feature identity according to privacy policy; generate accessible deterministic documents | G19-080, G19-089 |

## Filter boundary

The approved filter graph supports `feGaussianBlur`, `feDropShadow`, `feOffset`, `feFlood`,
`feColorMatrix`, `feBlend`, `feComposite`, `feMerge`/`feMergeNode`, and `feMorphology`, including
declared inputs/results, filter/primitive units, regions, color interpolation, edge behavior, and the
specified filter-then-clip/mask/opacity compositing order.

`feTurbulence`, `feDisplacementMap`, diffuse/specular lighting and light sources, convolve matrices,
component-transfer functions, filter images/tiles, custom shaders, and animation of filter values are
deliberate exclusions. Unknown primitives do not silently pass through when that would change the
declared result. Work is bounded prospectively by graph nodes/edges, references, expanded regions,
offscreen pixels/bytes, kernel radius, morphology radius, and aggregate operations.

## Import representation contract

- The importer produces immutable toolkit-neutral static scene/portrayal values capable of retaining
  supported groups, definitions, paints, text, images, clips/masks, filters, metadata, and exact
  document order. It does not flatten away semantics needed for export or cross-renderer parity.
- Unsupported dynamic or excluded static behavior fails with stable diagnostics unless SVG processing
  explicitly defines a harmless ignored construct within the pinned profile.
- CSS/reference resolution is two-phase and bounded; forward references are supported, reference
  cycles and exponential `use`/pattern/mask/filter expansion are rejected prospectively.
- Text and images never consult desktop toolkits, system fonts, environment locale, filesystem, or
  network. Font and raster decoders are explicit registered dependencies/resources.

## Export contract

- Map export emits a self-contained static document for the complete approved G19 portrayal surface,
  with deterministic namespaces, IDs, definition deduplication, order, numeric formatting, metadata,
  and resource encoding.
- Generated files contain accessible names/descriptions, language/direction where known, decorative-
  content behavior, and bounded feature identity hooks without leaking caller values by default.
- Raster/font assets are embedded only under explicit policy and byte ceilings; no secret/source URL
  is copied into output implicitly.
- Repeated export of the same snapshot and options is byte-identical. Import/export semantic round
  trips and independent renderer comparisons use declared structural, numeric, text, color, filter,
  and pixel tolerances.
- G19-089 closes the module only when this matrix, package Javadocs, root support wording, test corpus,
  security policy, and actual import/export behavior agree.
