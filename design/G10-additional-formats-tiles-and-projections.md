# G10 — Additional formats, tiles, and projections design

Project index: [DESIGN.md](../DESIGN.md).

## Secure SVG import profile and first slice (G10-001)

### Profile approval and module boundary

G10-001 imports marker artwork, not feature geometry, documents, maps, or renderer plugins. The named
HITL checkpoint **G10 secure SVG profile approval** must approve the tables, limits, diagnostics, and
representability rules below before a module is created. A rejected or materially changed profile
returns to design review; implementation does not quietly widen it.

The working slice creates published `mundane-map-io-svg` only when secure parse-to-render behavior and
tests land together. It is AWT-free and depends only on `mundane-map-api` plus JDK module `java.xml`.
It uses no core implementation, Java2D, external XML library, DOM, XPath, Transformer, URL/network
API, provider lookup, reflection, service discovery, or mutable global parser. XML and SVG types do
not enter `mundane-map-api`.

The complete public surface is two final utility/value types:

```text
SvgSymbols
  read(SourceIdentity, Path, MarkerPlacement, SvgImportLimits,
       CancellationToken) -> Symbol
  parse(SourceIdentity, byte[], MarkerPlacement, SvgImportLimits,
        CancellationToken) -> Symbol
  convenience overloads -> defaults and CancellationToken.none()

SvgImportLimits
  defaults()
  typed accessors and complete withers
```

There is no `SvgDocument`, scene graph, element model, renderer key, registry, catalog, warning report,
or auto-detected format. The result is one ordinary `VectorMarkerSymbol`, or an ordered
`CompositeSymbol` at opacity one when more than one painted leaf survives. The root return type is
therefore `Symbol`, not `MarkerSymbol`; the importer constructs only a `MARKER`-role result and
verifies that invariant immediately before publication. Every leaf uses the existing vector-marker
renderer key and supplied placement. Applications may explicitly add the result to a
`NamedSymbolCatalog`; import never mutates or invents a catalog.

The path overload accepts a regular local file only, captures and bounds its size, reads at most
`maximumInputBytes + 1` through a closed JDK stream, and retains no path or handle. The byte overload
checks the limit and defensively copies before decoding. Public arguments and an already-cancelled
token fail before I/O/copy. An I/O or cancellation failure closes owned input once; the original
failure stays primary and a later mapped close failure is suppressed. Neither overload accepts a URI,
URL, stream, reader, charset, resolver, factory, or callback.

### Exact XML and root profile

Input is UTF-8 XML 1.0 only. A strict decoder reports malformed/unmappable bytes; a UTF-8 BOM is
outside this first profile. An XML declaration may be absent or declare exactly version `1.0` and
absent/ASCII-case-insensitive `UTF-8` encoding; its standalone value may be absent, `yes`, or `no`.
There is exactly one root `svg` in namespace `http://www.w3.org/2000/svg`, provided as the default
namespace. Prefixed element/attribute names, extra namespace declarations, nested `svg`, and foreign
namespaces are rejected.

The root requires `viewBox="minX minY width height"` with four finite unitless SVG numbers and finite
strictly positive width/height. It may have `version="1.1|2.0"`,
`preserveAspectRatio="xMidYMid meet|none"`, and the presentation attributes below. Absent
`preserveAspectRatio` means `xMidYMid meet`. Root `width`, `height`, transform, opacity, ID, language,
base URI, event, reference, and other attributes are unsupported because `MarkerPlacement` already
owns size/placement and the profile has no reference model.

For `none`, all leaf symbols retain the declared view box and G2 performs the explicitly requested
possibly nonuniform placement. For `xMidYMid meet`, import derives one centered expanded view box whose
aspect ratio equals the supplied placement's nominal width/height: expand height to `viewWidth /
placementAspect` when the source is wider, or width to `viewHeight * placementAspect` when it is
narrower. Checked midpoint arithmetic keeps the original box centered and contained. This makes the
later G2 mapping a uniform meet transform without another wrapper symbol or clip.

Namespace declarations are not ordinary attributes. Every other attribute is counted and must appear
in the exact element allowlist. Attribute order has no semantic effect. Duplicate expanded names are
invalid XML; when several unsupported names exist, the diagnostic selects their lexically smallest
ASCII local name rather than depending on StAX delivery order.

### Groups, shapes, and path grammar

Supported elements and local geometry attributes are exact:

| Element | Geometry attributes and rule |
| --- | --- |
| `g` | no geometry; presentation inheritance and optional `transform` |
| `path` | required nonblank `d` |
| `rect` | optional `x=0`,`y=0`; required positive `width`,`height`; no `rx`/`ry` |
| `circle` | optional `cx=0`,`cy=0`; required positive `r` |
| `ellipse` | optional `cx=0`,`cy=0`; required positive `rx`,`ry` |
| `line` | required `x1`,`y1`,`x2`,`y2` |
| `polyline` | required `points` containing at least two positions |
| `polygon` | required `points` containing at least three positions; import emits one close |

Every shape also permits optional `transform`, the presentation attributes, and shape-only `opacity`.
Groups may nest to the approved depth; they emit no symbol and are flattened after style and transform
resolution. Empty groups are allowed, but the completed document must produce at least one painted
leaf. Document order is paint order.

`path d` supports absolute and relative `M L H V Q T C S Z` and their lowercase forms. It implements
SVG number separators, sign-separated values, exponent notation, repeated coordinate groups, and
implicit line groups following `M`. `H`/`V` expand to lines; `T`/`S` reflect the prior compatible
control or use the current point exactly as SVG 2 specifies. Every subpath starts with `M`, contains at
least one drawing segment, closes at most once, and after `Z` permits only a new `M`. Arc `A`, unknown
letters, unit suffixes, `NaN`, infinity, missing/extra values, a move-only subpath, or a command after
close is terminal.

Polyline/polygon points use the same finite number grammar. Polygon conversion appends the first point
only when needed, then emits exactly one close; duplicate consecutive coordinates are retained unless
they make the G2 path invalid. A line or open polyline must have effective `fill=none`. A path with any
open subpath likewise requires no fill, avoiding SVG's implicit fill closure that the Level 1 path
contract does not represent. Filled paths and polygons require effective `fill-rule=evenodd`; simple
rect/circle/ellipse contours may use the SVG default because their generated contour has no winding
ambiguity.

Circles and ellipses become four cubic segments plus close using the approved constant
`4 * (StrictMath.sqrt(2) - 1) / 3`. Tests pin the constant, cardinal points, controls, closure,
symmetry, and conservative radial-error bound. Adding an arc opcode to `VectorPath` for one importer
would be a larger and less ergonomic design.

### Transform and paint representability

Transform lists support finite unitless forms only:

```text
matrix(a b c d e f)
translate(tx [ty=0])
scale(sx [sy=sx])
rotate(angle [cx cy])
skewX(angle)
skewY(angle)
```

Parsing uses column vectors, post-multiplies functions in source order, and combines
`parent * local`. Every function, multiplication, determinant, transformed endpoint/control, and
final view-box calculation is checked. A singular or non-finite matrix is rejected. Filled geometry
may use any finite invertible affine transform because import bakes it into immutable path coordinates.
All transformed endpoints and controls, plus the conservative curve-control envelope, must remain in
the effective view box; this profile never clips overflow.

Presentation attributes allowed on root, group, and shape are:

```text
fill stroke stroke-width fill-opacity stroke-opacity
fill-rule stroke-linecap stroke-linejoin
```

`opacity` is allowed only on a shape. Root/group opacity is rejected because SVG applies it after
children composite, while `CompositeSymbol` opacity multiplication would differ for overlaps. Local
presentation values override inherited values. Defaults are black fill, no stroke, width `1`, opacity
one, nonzero fill rule, butt cap, and miter join.

Paint colors are exactly `none` or `#RRGGBB` with ASCII-case-insensitive hexadecimal. Opacities are
finite decimal values in `[0,1]`; stroke width is finite, unitless, and positive whenever stroke is
not none. CSS/style/class, named colors, short/alpha hex, `currentColor`, URLs, gradients, patterns,
dashes, vector effects, and all units are unsupported.

G2 has fixed round cap/join. Therefore an effective stroke is accepted only when both computed
`stroke-linecap` and `stroke-linejoin` are explicitly `round`; SVG's butt/miter defaults are not
silently changed. Its complete element-to-placement linear transform must also be a uniform
similarity within G2's `1e-12` relative tolerance. Import converts SVG width through that one scale
into a `SymbolLength` using the placement size unit. Skew, anisotropic transform, or `none` placement
that makes stroke width direction-dependent remains valid for fill-only leaves but is rejected for a
painted stroke. The conservative half-stroke envelope must fit the effective view box.

Fill/stroke opacity is folded into the corresponding `Rgba` alpha. Nontrivial shape opacity is exactly
representable as leaf-symbol opacity only when at most one of fill and stroke has nonzero effective
alpha. When both paints remain visible, shape opacity must be exactly zero or one: zero omits the leaf
and one preserves G2's fill-then-stroke result. Any other value is `SVG_VALUE_INVALID` with
`field=opacity` and `reason=paintComposition`, because SVG composites a shape's fill and stroke before
applying shape opacity while G2 multiplies opacity into each paint. A stroke-only leaf uses
transparent-black fill. A shape with neither effective paint, or whose effective paints are all zero
alpha, is omitted while preserving remaining order. One shape becomes one `VectorMarkerSymbol`;
multiple leaves become one non-flattened `CompositeSymbol`. There is no SVG-specific grouping state
or hidden offscreen renderer.

### Secure StAX, limits, cancellation, and diagnostics

Each import creates a fresh `XMLInputFactory.newDefaultFactory()` and reader. Before use it sets and
reads back all required Java 21 controls:

```text
XMLInputFactory.SUPPORT_DTD=false
XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES=false
XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES=false
XMLInputFactory.IS_NAMESPACE_AWARE=true
XMLInputFactory.IS_VALIDATING=false
XMLConstants.ACCESS_EXTERNAL_DTD=""
XMLConstants.USE_CATALOG=false
throwing XMLResolver
```

An unsupported or ineffective control is an internal `IllegalStateException`; the implementation
never falls back to a weaker parser. Before StAX, the decoded input is scanned for U+0026 and rejects
it anywhere in the first profile, including inside a comment; predefined/numeric references and raw
comment ampersands therefore share `SVG_XML_INVALID reason=reference`. Comments containing no
ampersand and whitespace-only character events are ignored. DTD, entity, processing-instruction, and
unexpected events are rejected again if reported; non-whitespace text/CDATA is invalid. No XSD,
catalog, XInclude, URI resolution, or external access exists.

`SvgImportLimits` is a final immutable value. Every ceiling except owned bytes is an `int`;
`maximumOwnedBytes` is a `long`. Its public constructor and every complete wither validate the whole
candidate before publication. Values are inclusive and in `[1, hardMaximum]`; there is no unlimited
sentinel. The cross-field invariants are `maximumAttributeCharacters <=
maximumAggregateAttributeCharacters`, `maximumTransformAncestorDepth <= maximumElementDepth`,
`maximumPaintedOutputPaths <= maximumElements`, and `maximumDrawingSegments <
maximumExpandedCommands`. A wither that would violate an invariant fails without changing the source
value. Independent byte and owned-byte ceilings intentionally have no ordering invariant: either may
be the effective first bound.

Defaults and non-configurable profile hard maxima are:

| Ceiling | Default | Hard maximum |
| --- | ---: | ---: |
| Encoded input bytes | 1,048,576 | 16,777,216 |
| Elements, including root/groups | 2,048 | 1,048,576 |
| Element nesting depth, root = 1 | 32 | 128 |
| Total ordinary attributes | 16,384 | 1,048,576 |
| UTF-16 characters in one attribute | 65,536 | 1,048,576 |
| Aggregate attribute UTF-16 characters | 1,048,576 | 16,777,216 |
| Characters in one number token | 128 | 256 |
| Expanded path commands/opcodes | 131,072 | 1,048,576 |
| Drawing segments, including close edges | 65,536 | 1,048,575 |
| Transform functions | 4,096 | 65,536 |
| Transform-bearing ancestor depth | 16 | 128 |
| Painted output paths | 2,048 | 65,536 |
| Conservatively owned bytes | 16,777,216 | 268,435,456 |

Counts include ignored comments only in the already-bounded input, generated basic-shape commands,
implicit/repeated/shorthand expansion, and inherited transform depth. Commands count every emitted
move/line/quadratic/cubic/close; drawing segments exclude move and include close. Owned-byte accounting
uses checked `long` G4 primitive/reference rules and charges the input copy, decoded UTF-16 storage, current
builder capacities, transformed packed path, output path/list references, and simultaneously live
defensive copies before allocation. Equality is accepted; one-over and arithmetic overflow fail.
The path reader never allocates `maximumInputBytes + 1`: it accumulates at most the approved maximum
and then performs one separately buffered byte probe. Every addition is widened and checked before
comparison or narrowing; an accepted byte array is at most the 16 MiB hard maximum.

Cancellation is checked before I/O/copy/allocation, after strict decode, at least every 4,096 XML
events, numeric tokens, and emitted commands, before each opaque defensive symbol construction, and
immediately before publication. Cancellation publishes no partial symbol and returns terminal
`SOURCE_CANCELLED`. The importer has no executor, worker, cache, parser pool, thread-local, or retained
input.

Successful import emits no warning/recovery report. Terminal failures reuse `SourceException` with
the caller identity and exactly one present `DiagnosticLocation`: `component=svg`, with record, part,
field index, field name, and byte offset all absent. No path, StAX/provider message, or fake byte
offset derived from a character location is retained. The format codes and complete context schemas
are deliberately small:

| Code | Exact context | Closed values |
| --- | --- | --- |
| `SVG_IO_FAILED` | `operation`, `reason` | `operation=open|read|close`; `reason=notFound|accessDenied|closed|other` |
| `SVG_ENCODING_INVALID` | `reason` | `bom|malformedUtf8` |
| `SVG_XML_INVALID` | `reason` | `reference|doctype|entity|processingInstruction|malformed|unexpectedEvent` |
| `SVG_PROFILE_UNSUPPORTED` | `construct` | `xmlDeclaration|root|qualifiedName|namespace|nestedSvg|element|attribute|pathCommand|unit|paint|reference|characterData` |
| `SVG_VALUE_INVALID` | `field`, `reason` | `field=viewBox|version|preserveAspectRatio|d|points|x|y|width|height|cx|cy|r|rx|ry|x1|y1|x2|y2|transform|fill|stroke|stroke-width|fill-opacity|stroke-opacity|opacity|fill-rule|stroke-linecap|stroke-linejoin`; `reason=missing|blank|syntax|arity|range|nonFinite|singular|overflow|emptySubpath|commandAfterClose|openFill|fillRule|strokeCap|strokeJoin|strokeTransform|outsideViewBox|paintComposition` |
| `SVG_EMPTY_GRAPHIC` | empty | no context entries |

Shared `SOURCE_LIMIT_EXCEEDED` uses exactly `scope=svgImport`, `limit`, `maximum`, and `requested`;
`limit=inputBytes|ownedBytes|elements|elementDepth|attributes|attributeCharacters|aggregateAttributeCharacters|numberTokenCharacters|expandedCommands|drawingSegments|transformFunctions|transformAncestorDepth|paintedOutputPaths`.
`SOURCE_CANCELLED` retains its shared empty-context shape. No format diagnostic has any additional or
optional context entry. Raw XML/value/name, URL, path, provider class/message, or exception never
appears.

Deterministic precedence is: public argument/lifecycle defects; already-cancelled token; input byte
limit; owned-byte allocation limit; strict UTF-8/BOM/reference scan; XML security and syntax;
element/depth/attribute
limits; namespace/element profile; lexically selected unsupported attribute; fixed required-field
order; style/transform/path/value representability; output command/segment/path/allocation limits;
final cancellation; publication. The first terminal result closes every owned parser/input and later
cleanup failures are suppressed.

Within one supported start element, validation order is namespace/qualified name, element kind,
sorted unsupported attributes, then required geometry fields in the table's written order. Supported
presentation fields follow the written presentation-attribute order, shape opacity, transform-list
source order, and geometry-token source order. Root validates `viewBox`, `version`, and
`preserveAspectRatio` in that order. A well-formed but forbidden construct maps to
`SVG_PROFILE_UNSUPPORTED`; malformed XML or a forbidden XML event maps to `SVG_XML_INVALID`; a field
inside the supported profile maps to `SVG_VALUE_INVALID`. These rules and the closed tables above,
not provider event order or message text, select the terminal result.

At every file-size, stream-growth, byte-array-copy, and decode-allocation checkpoint, the importer
checks `inputBytes` before the simultaneously calculable `ownedBytes` charge. Thus
`SOURCE_LIMIT_EXCEEDED limit=inputBytes` wins when one input would exceed both ceilings at that
checkpoint; an earlier independent owned-allocation overrun still wins when the input ceiling remains
satisfied.

### Render, native, publication, and approval evidence

Focused tests cover every element, path command/shorthand/relative/repeated form, number separator,
transform kind/order/nesting, inherited paint, opacity, leaf order, view-box mode, defensive ownership,
and hand-calculated coordinates. Negative tables cover open fills, winding, cap/join and non-similarity,
overflow/non-finite values, every exact/one-over limit, malformed UTF-8/XML, BOM, DTD/entities,
namespace/reference/foreign/unsupported constructs, data URL, CSS, text, filters/masks/clips, arc, and
external stylesheet. A temporary-file canary and local HTTP trap prove zero external reads. A small
fixed-seed bounded mutation harness accepts only a valid immutable symbol or stable `SourceException`.

`renderRegression` imports fixed in-memory documents and compares transformed bounds, layer order,
background, broad color regions, and per-channel tolerances with equivalent hand-built symbols; it
does not compare a whole image across platforms. Architecture tests prove the module has only API and
`java.xml`, is AWT/network/discovery-free, and contains no DOM/XPath/Transformer or prohibited native
mechanism.

Because no later SVG native task exists, this slice appends one direct package-private scenario to the
then-current `NativeSmokeMain` immediately before its unchanged sentinel. It parses one literal UTF-8
triangle document, rejects one literal DTD document with the exact shared JVM/native outcome, and
renders the supported symbol at a point through `SymbolRendererRegistry.builtIn()` using tolerant
bounds/color/background assertions. Byte literals add no native resource or reachability entry. The
support project adds one explicit SVG dependency. G9-008 and this task each own one append-only
scenario and required inventory subset, while the task landing second retains the first task's entries
and updates the one authoritative complete manifest. Neither hard-codes the other's absence. The two
tasks are dependency-parallel but not path-safe and must have one native-inventory owner when their
implementation overlaps. Linux evidence supports only this bounded subset, not arbitrary SVG.

As a working Published post-Level-1 module, the task extends the then-current authoritative inventory,
release contract/verifier, staged manifest, license/checksum checks, and standalone offline consumer.
The consumer resolves only staged artifacts, parses an inline supported document, and asserts ordinary
symbol values without importing XML implementation types. It does not retroactively change G8's Level
1 candidate or hard-code a count that a dependency-parallel Level 2 task may already have extended.

The **G10 secure SVG profile approval** record includes reviewer/date/outcome, supported and rejected
tables, four-cubic approximation, view-box/paint/stroke representability, security controls, limits,
diagnostic/recovery policy, native literal scenario, publication/consumer extension, and any blocker.
Focused module/architecture checks run before `renderRegression`, the separate real `nativeSmoke`,
publication/consumer smoke, `qualityGate`, and whitespace. No corpus, performance, or new specialized
lane runs. Two public types, one private streaming importer, existing immutable symbols/renderer, and
one inline native/consumer case are sufficient; every broader SVG feature remains a new task.
