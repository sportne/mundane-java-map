# OGC style adapter capability intent

`mundane-map-io-se` is the project's JDK-only adapter for secure, bounded interchange of OGC
Symbology Encoding 1.1 styles and Styled Layer Descriptor 1.1 documents. The approved target includes
both reading and canonical writing. A writer accepts only values that can be represented losslessly
by the selected SE/SLD profile; it never silently simplifies toolkit portrayal or emits private
extensions to preserve unsupported behavior.

SLD is treated as a document wrapper that binds SE styles to named or user-defined layers. This
module is not a WMS client/server, style repository, catalog service, or remote style-management API.
The root README describes released behavior. Target rows below become release claims only as their
G19 cards close.

## Standards and version boundary

| Standard | Approved use | Claim boundary |
| --- | --- | --- |
| [OGC Symbology Encoding 1.1.0](https://www.ogc.org/standards/se/), OGC 05-077r4 | Feature/coverage styles, rules, expressions, and standard symbolizers | SE 1.1 only; later OGC API Styles and vendor dialects are not implied |
| [OGC Styled Layer Descriptor 1.1.0](https://www.ogc.org/standards/sld/), OGC 05-078r4 | Read/write `StyledLayerDescriptor` documents and bind named/user styles to layers | Document interchange only; no WMS requests, server-side catalog, authorization, or style deployment |
| OGC Filter Encoding 1.1.0/Corrigendum, OGC 04-095 | The expression and predicate language referenced by SE 1.1 | Comparison, logical, feature-ID, and spatial operators; no FES 2.0 temporal operators, stored queries, sorting, or capabilities |
| GML 3.1.1 simple geometry profile used by Filter 1.1 | Bounded geometry operands and spatial literals | Only geometry/CRS forms needed by the approved filter profile; not general GML document support |
| XML 1.0, Namespaces in XML, and XLink | Deterministic secure XML parsing/serialization and explicit resource identifiers | Hardened StAX; no DTD, entities, XInclude, external schema resolution, or ambient resource lookup |

Filter Encoding 2.0, CQL2, OGC API Styles, and vendor-specific filter functions are distinct protocol
decisions. Temporal filtering is therefore a deliberate exclusion from this SE 1.1 completion plan,
not an omitted SE conformance feature.

## Document and resource profile

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| SE documents | Root `FeatureTypeStyle` only | Complete bounded `FeatureTypeStyle`, `CoverageStyle`, rules, metadata, descriptions, semantic types, and geometry selection | G19-090, G19-094 |
| SLD documents | Rejected | SLD 1.1 `StyledLayerDescriptor`, named/user layers, named/user styles, feature constraints, and SE style embedding/binding | G19-090 |
| XML security | Secure StAX subset | Preserve hardened parsing while adding prospective byte, element, depth, text, attribute, namespace, AST, rule, and resource limits | G19-090 |
| Resources | Narrow pre-registered graphics | Typed closed caller catalog for images, SVG, fonts, and inline bytes with media/identity validation and bounded reference graphs | G19-090, G19-095, G19-096 |
| Remote lookup | Rejected | Continue rejecting ambient URL/file/classpath/schema/font lookup; an `OnlineResource` resolves only through explicit caller authorization | G19-090 |
| Extensions | Rejected or unsupported | Standard SE/SLD only by default; unknown/vendor elements and `VendorOption` fail unless a future explicit typed extension registry is approved | G19-090, G19-099 |

## Filter Encoding 1.1 matrix

| Area | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Expressions | Property names and literals | Property/value references, typed literals, add/subtract/multiply/divide, nested expressions, and a closed standard function catalog | G19-091 |
| Comparison | Equal/not-equal/order/between/null subset | All FE 1.1 comparison forms, match-case behavior, null/nil/type conversion, deterministic numeric/text ordering, and explicit unsupported diagnostics | G19-092 |
| Logical and identity | `And`, `Or`, `Not` | Complete bounded logical composition plus feature-ID predicates and identity mapping | G19-092 |
| Spatial | Rejected | BBOX, equals, disjoint, intersects, touches, crosses, within, contains, overlaps, DWithin, and Beyond over approved geometry/CRS/unit profiles | G19-093 |
| Temporal | Rejected | Deliberately excluded because SE 1.1 references Filter Encoding 1.1, which does not define the FES 2.0 temporal operator family | — |
| Evaluation | Small direct evaluator | Immutable compiled AST, explicit three-valued/null/coercion rules, stable function registry, prospective cost limits, and deterministic evaluation | G19-091–G19-093 |

## Symbology Encoding matrix

| Area | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Rule orchestration | Ordered rules, else, min/max scale | Complete feature/coverage style ordering, semantic type identifiers, rule filtering, scale boundaries, else behavior, and omission semantics | G19-094 |
| Point symbolizers | Basic built-in mark/external graphic subset | Graphic ordering/fallback, opacity/size/rotation/anchor/displacement expressions, standard marks, external marks/graphics, and explicit resources | G19-095 |
| Line symbolizers | Solid stroke/opacity/width | Standard cap/join/dash/dash-offset, perpendicular offset, graphic stroke, initial/gap behavior, and unit conversion | G19-095 |
| Polygon symbolizers | Solid fill/stroke | Graphic fill, displacement, perpendicular offset, complete approved stroke/fill parameters, and hole semantics | G19-095 |
| Text symbolizers | Rejected | Label expression, registered font families/fallback, point/line placement, anchors/displacement/rotation, halo, fill, priority/grouping, and deterministic shaping | G19-096 |
| Raster symbolizers | Rejected | Opacity, channel selection, contrast enhancement, color maps, overlap behavior, shaded relief, and approved geometry/coverage mapping | G19-097 |
| Units | Pixel only | SE pixel, metre, and foot URIs with explicit map/display conversion, scale denominator, and CRS-axis policy | G19-095–G19-097 |
| Geometry selection | Default feature geometry | Standard `Geometry` expression/property selection with type checking and stable omission/failure behavior | G19-094 |

## Canonical writer contract

- Emit either a standalone SE 1.1 feature/coverage style or an SLD 1.1 wrapper selected explicitly by
  the caller. WMS requests or remote installation are never side effects of serialization.
- Preflight the entire style, expression tree, symbol graph, metadata, and resource plan before any
  output. Reject non-representable toolkit behavior with a path-specific stable diagnostic; do not
  approximate it, drop it, or hide it in a vendor option.
- Use deterministic namespace prefixes, element/attribute order, IDs, numeric formatting, character
  encoding, whitespace, resource names, and schema/version declarations. Identical inputs and options
  produce byte-identical output.
- Write atomically to files and transactionally to bounded byte/output sinks. Cancellation or failure
  preserves the prior target and closes every staged resource.
- Inline or reference a catalog resource only under explicit policy and byte/media limits. Never copy
  source paths, remote URLs, credentials, private attributes, or arbitrary caller XML implicitly.
- Reader/writer semantic round trips compare the neutral compiled style rather than incidental source
  whitespace or namespace prefixes.

## Conformance and completion evidence

- Pin the exact OGC schemas and applicable abstract-test/conformance classes without enabling network
  schema resolution at runtime.
- Test official-derived and independently produced SE/SLD documents, schema validation, deterministic
  writing, read-write-read semantics, malformed/hostile XML, reference and AST bombs, and resource
  failures.
- Compare feature, text, and raster portrayal across the AWT and Vaadin renderers within declared
  geometry, placement, color, alpha, text, and raster tolerances.
- Record every deliberate exclusion—especially FES 2.0 temporal behavior, vendor functions/options,
  ambient resources, and WMS operations—in public package/capability documentation.
- G19-099 closes the module only when this matrix, package/root support wording, implementation,
  diagnostics, examples, native/publication evidence, and external interoperability evidence agree.
