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

## GeoJSON feature-source profile decision (G10-002)

### One optional adapter, not two parser stacks

The named HITL checkpoint is **G10 GeoJSON profile approval**. It approves the exact RFC 7946 subset,
the external-parser boundary, limits, diagnostics, and follow-up graph below before any GeoJSON module
is created. G10-002 itself changes design and task records only.

The approved implementation strategy is one explicitly optional adapter,
`mundane-map-io-geojson-jackson`. Its first working slice pins
`tools.jackson.core:jackson-core:3.1.5`, selected on 2026-07-13 as the latest Maven Central patch on the
3.1 LTS line rather than the newer non-LTS 3.2 line. The approved JAR SHA-256 is
`9431b7fa2673bbb618c11d865fe15e13222fd182a214ff998cb7e56afd8f35d2`. Dependency locking,
artifact/POM provenance, Apache-2.0 license/notice review, and an exact resolved runtime graph are
mandatory. The implementation checkpoint rechecks security advisories and artifact availability
without using a version range or silently changing the approved version. It uses Jackson Core's token
stream only—no databind, annotations, tree model, object mapper, polymorphic type handling, provider
lookup, or application serialization.

The 3.1.5 artifact shades FastDoubleParser/Schubfach classes and contains
`META-INF/services/tools.jackson.core.TokenStreamFactory`. Approval records the bundled upstream
versions/notices/licenses as artifact content even though the adapter disables both fast-number
features and never invokes service discovery. The service descriptor is not copied into a MundaneJ
artifact or native resource configuration. Direct `JsonFactory` construction and architecture tests
prove it is operationally irrelevant; G10-024 must separately prove that Native Image uses no service
registration or metadata-repository fallback, inventory any statically reachable shaded classes, and
prove the disabled fast paths are not executed. If the dependency cannot satisfy that bounded native
path, the adapter remains explicitly JVM-only rather than weakening the project's discovery rules.

This external dependency is justified by correct JSON tokenization, Unicode escape handling, numeric
lexing, byte locations, and maintained malformed-input behavior. Reimplementing those concerns in a
map library would create a security-sensitive generic JSON parser larger than the GeoJSON adapter.
The rejected existing MundaneJ JSON-binding parser materializes an input-backed character model,
exposes unsuitable character offsets, and lacks the parser-work, nesting, duplicate-member, and
allocation controls required here. If the maintainer rejects Jackson at the checkpoint, this design
must be revised to one bounded JDK-only tokenizer before implementation; the project will not ship
parallel Jackson and home-grown GeoJSON modules or a speculative parser SPI.

The optional adapter depends only on `mundane-map-api`, the exact G4 accounting/query utilities in
`mundane-map-core`, Jackson Core, and `java.base`. It is AWT-free and named as an implementation
choice rather than occupying a generic `mundane-map-io-geojson` artifact. Public signatures contain
only JDK and MundaneJ values; Jackson types, factories, tokens, constraints, locations, and exceptions
remain private. Construction directly creates the pinned `JsonFactory`; there is no `ServiceLoader`,
classpath scan, reflection, static mutable factory, automatic module discovery, or JSON renderer.
`mundane-map-api` remains unchanged.

The implementation module is classified as a Published Level 2 **Optional adapter**, not a JDK-only
runtime module and never part of the Level 1 graph. Its publication and consumer additions follow the
append-only inventory rule established by G9/G10-001. Native Image remains unclaimed until the final
follow-up proves the exact parser/source/render path; successful JVM parsing alone does not change
that policy.

Every complete opening pass creates one private immutable factory, and every live cursor creates one
more; neither the source, a static field, nor a thread-local retains a factory after that operation or
cursor ends. The exact Jackson 3.1.5 builder recipe is:

```text
JsonFactory.builder()
  disable TokenStreamFactory.Feature.CHARSET_DETECTION
  disable TokenStreamFactory.Feature.CANONICALIZE_PROPERTY_NAMES
  disable TokenStreamFactory.Feature.INTERN_PROPERTY_NAMES
  disable StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION
  disable StreamReadFeature.USE_FAST_DOUBLE_PARSER
  disable StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER
  disable JsonReadFeature.ALLOW_JAVA_COMMENTS
  disable JsonReadFeature.ALLOW_YAML_COMMENTS
  disable JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER
  disable JsonReadFeature.ALLOW_SINGLE_QUOTES
  disable JsonReadFeature.ALLOW_RS_CONTROL_CHAR
  disable JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS
  disable JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES
  disable JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS
  disable JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS
  disable JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS
  disable JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS
  disable JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS
  disable JsonReadFeature.ALLOW_MISSING_VALUES
  disable JsonReadFeature.ALLOW_TRAILING_COMMA
  recyclerPool(JsonRecyclerPools.nonRecyclingPool())
  streamReadConstraints(exact effective depth/name/string/number/document ceilings)
  build
```

Construction tests read every configured feature back and fail closed on a version/API mismatch; a
future dependency upgrade must explicitly classify every newly added read feature before use. The
non-recycling pool creates operation-owned buffers and retains none after parser close. Property-name
canonicalization/interning is off, input content is excluded from locations, and the project parses
raw numeric token text with its own bounded JDK conversions. Parsers are closed once with the first
failure primary. There is no factory cache, recycler cache, shared symbol table, or retained untrusted
name outside the charged operation/source values.

### Exact document and object profile

The normative format is [RFC 7946](https://www.rfc-editor.org/rfc/rfc7946), with strict JSON syntax
from [RFC 8259](https://www.rfc-editor.org/rfc/rfc8259) and the duplicate-member/Unicode discipline of
[RFC 7493](https://www.rfc-editor.org/rfc/rfc7493). One local file or caller-owned byte array contains
exactly one document and trailing whitespace only. GeoJSON Text Sequences, JSON Lines, streams,
readers, URLs, network retrieval, UTF-16/32, and concatenated documents are outside the profile.

Input is strictly decoded and validated as UTF-8 before semantic parsing. One leading UTF-8 BOM is
accepted, omitted from parser offsets, and records one opening warning `GEOJSON_UTF8_BOM_IGNORED`;
any other BOM or malformed/unmappable sequence is terminal. Escaped or literal strings must represent
Unicode scalar values: unpaired surrogate escapes are rejected. The parser enables no comment,
single-quote, unquoted-name, trailing-comma, missing-value, leading-zero, arbitrary-backslash,
non-numeric-number, or other permissive read feature. Every object, including an ignored foreign
subtree, rejects a duplicate member before later semantic validation.

Member order has no meaning. All byte fences are absolute indexes into the owned original byte array:
the first document token starts at index zero or three after the optional BOM; a value fence is
`[startInclusive,endExclusive)`, from its first non-whitespace byte through exactly one byte after its
closing scalar/string/object/array token. Whitespace following a value is outside its fence. Slice
tests pin root/feature/member fences with ASCII, multibyte UTF-8, escapes, nested foreign values, and
the BOM, rather than trusting a parser location without a base-offset oracle.

Opening is two explicit phases over the same immutable bytes:

1. A structural pass visits every token in the complete document, calls the string accessor for every
   property name and string value, validates Unicode scalars, maintains a charged duplicate-name set
   for every active object, applies every structural counter/cancellation checkpoint, and establishes
   exact fences. It never calls `skipChildren`, including for foreign members.
2. A semantic pass scans each supported object's immediate members into a bounded temporary
   name/value-fence table, then reparses the recognized value slices in the fixed order below. It may
   jump across a foreign fence already proved by phase one, but creates no extension value or generic
   tree. Feature processing retains only the final feature fence/envelope/index entry.

A cursor performs the same bounded immediate-member/semantic scan on one retained Feature or bare-
geometry fence, using a cursor-owned factory. It visits rather than blindly skips foreign/property
content even under `AttributeSelection.NONE`, but avoids constructing unrequested strings/attributes.
Every parser location is converted to an absolute checked array index before use; invalid or
non-monotone locations are an internal invariant failure, never an untrusted index.

Exact, case-sensitive core members are:

| Object | Required | Optional | Outcome |
| --- | --- | --- | --- |
| `FeatureCollection` | `type`, `features` | `bbox`, foreign members | Emits zero or more records. |
| `Feature` | `type`, `geometry`, `properties` | `id`, `bbox`, foreign members | Emits one record unless geometry is null. |
| geometry | `type`, `coordinates` | `bbox`, foreign members | Emits one immutable geometry. |

The document root may be a `FeatureCollection`, one `Feature`, or one of the six supported geometry
objects. A bare geometry becomes one record with ID `geometry:0`, empty name, and empty attributes.
An array, scalar, null, or any other object root is rejected. `FeatureCollection.features` contains
only Feature objects; nested collections are unsupported.

`Feature.geometry=null` is the one recovery rule. The physical feature is fully syntax/profile/limit
validated, records one `GEOJSON_NULL_GEOMETRY_SKIPPED` opening warning, and emits no record. A
collection containing only null geometries is a valid empty source with absent extent. Empty
coordinate arrays and empty geometry components are not converted to null and are rejected.

The exact 2D geometry profile is:

| GeoJSON type | Required shape | MundaneJ value |
| --- | --- | --- |
| `Point` | exactly one two-number position | `PointGeometry` |
| `MultiPoint` | one or more positions | `MultiPointGeometry` |
| `LineString` | at least two positions | `LineStringGeometry` |
| `MultiLineString` | one or more lines, each with at least two positions | `MultiLineStringGeometry` |
| `Polygon` | one or more rings, each at least four positions with exact first/last closure | `PolygonGeometry` |
| `MultiPolygon` | one or more polygons, each satisfying the Polygon rule | `MultiPolygonGeometry` |

Every position has exactly longitude then latitude. A third/fourth ordinate is unsupported rather
than silently discarded. Coordinate tokens must be finite when converted to `double`; longitude is
inclusive `[-180,180]`, latitude inclusive `[-90,90]`, and negative zero is canonicalized to positive
zero. Geometry, part, polygon, and ring declaration order is preserved. The first polygon ring is the
exterior and later rings are holes. Orientation is neither changed nor rejected, matching RFC 7946's
backward-compatibility rule; containment, self-intersection, repair, topology, and antimeridian
wrapping remain out of scope. A segment from positive to negative dateline longitude is exposed
literally under the Level 1 x/y rendering/query convention.

`GeometryCollection` is rejected for the first profile: the sealed API has no heterogeneous geometry
value and splitting it into records would invent IDs and feature semantics. Nested coordinates,
empty components, insufficient line/ring cardinality, open rings, and non-number ordinates terminate
without repair.

An optional `bbox` on any supported GeoJSON object must contain exactly four finite numbers in RFC
order: west, south, east, north. Longitudes and latitudes use the same closed ranges; south must not
exceed north, while east may be less than west for an RFC antimeridian box. It is syntax/range
evidence only: it is not compared with coordinates or retained because G4's one `Envelope` cannot
represent a wrapping longitude interval. Source extent is computed from emitted coordinates.

The obsolete `crs` member is explicitly rejected wherever it occurs on a GeoJSON object; it is not
treated as an ignorable extension. Every other foreign member is structurally parsed, included in all
depth/token/member/string/number/allocation/cancellation counters, and skipped without a warning or
retained extension tree. Unknown core-member spelling is therefore just a foreign member and cannot
satisfy a required exact member.

### CRS, IDs, names, and properties

RFC 7946 coordinates use the OGC CRS84 longitude/latitude tuple. At this format boundary the adapter
normalizes that tuple directly to the library's identical x/y visualization convention and obtains
the canonical definition with `CrsRegistry.level1().resolve("EPSG:4326")`. Metadata is
`CrsMetadata.recognized` with both the declared-identifier and retained-definition optionals empty.
This format-owned normalization is not a new `CRS:84` registry alias, WKT recognition, caller
override, datum operation, or change to EPSG's authority-axis order.

Every emitted source ID is deterministic and collision-separated by origin:

```text
Feature id string  -> string:<exact value>
Feature id number  -> number:<canonical BigDecimal value>
Feature id absent  -> record:<zero-based physical feature index>
bare geometry      -> geometry:0
```

String IDs may be empty, but every prefixed result is governed by this adapter's fixed 256 UTF-16-
character emitted-ID bound; G4 does not otherwise limit feature-ID length. Exceeding it is
`GEOJSON_VALUE_INVALID field=id reason=length` before ID-set allocation. A numeric ID uses a valid
bounded JSON number, maps negative zero to `BigDecimal.ZERO`, strips insignificant trailing zeros,
and uses `BigDecimal.toString()`; `1`, `1.0`, and `1e0` therefore have one identity. Boolean, null,
array, and object IDs are invalid. Duplicate emitted IDs among non-null geometries terminate at open
with G4's `SOURCE_DUPLICATE_FEATURE_ID` and exact zero-based `firstIndex`/`duplicateIndex`; skipped
null geometries do not occupy source identity. Supplied and synthetic prefixes cannot collide.
`FeatureRecord.name` remains empty—the adapter does not privilege a property named `name`—and all
properties remain queryable attributes.

`properties` is either null, meaning an empty attribute map, or one flat object. Member order becomes
attribute order. A property name must satisfy G4's nonblank 256-character key rule. Values map exactly:

| JSON value | Attribute value |
| --- | --- |
| string | `String` |
| true/false | `Boolean` |
| null | `AttributeNull.INSTANCE` |
| integral mathematical value within signed 64-bit range | `Long` |
| other approved number | normalized `BigDecimal` |

There is no date inference, floating `Double` property, raw JSON text, binary encoding, array, nested
object, arbitrary number subclass, or Jackson node. A property array/object is
`GEOJSON_PROFILE_UNSUPPORTED construct=nestedProperty`. JSON numbers are limited to 128 token
characters by default and at most 34 significant decimal digits. The signed lexical exponent is
parsed with checked decimal arithmetic and must be in `[-308,308]` before `BigDecimal` construction;
the normalized value's adjusted exponent must also be in that range. All coordinate, ID, property,
and bbox numbers use that same guard before field-specific conversion. A property number whose exact
mathematical value fits `long` becomes `Long`, regardless of decimal/exponent spelling. Every other
property number becomes `BigDecimal.ZERO` when numerically zero or
`value.stripTrailingZeros()` otherwise; negative scale is retained, so equal approved spellings have
equal attribute values without an unbounded `toPlainString`. Metadata schema is absent because fields
may differ per record. G4 query projection and payload limits still apply when a cursor constructs the
selected attributes.

### Bounded snapshot source and query behavior

The eventual public facade exposes only `GeoJsonSources`, immutable `GeoJsonLimits`, and overloads for
a regular local `Path` or caller byte array with `SourceIdentity`, `FeatureSourceLimits`, and
`CancellationToken`. Exact signatures land with G10-020 after API compile sketches. A path open reads
and closes one bounded file; a byte-array open defensively copies. The successful source owns one exact
UTF-8 byte snapshot, so later filesystem/caller mutation cannot change metadata, offsets, or records.
It owns no path, channel, parser, token, thread, executor, cache, or external handle after open.

Opening performs a complete bounded token/semantic pass and retains compact primitive feature entries:
byte start/end fences, physical index, null/emitted flag, and four coordinate-envelope doubles for an
emitted feature. A temporary ID set proves uniqueness and is discarded. Metadata has exact exposed
feature count, computed optional extent, absent schema, canonical EPSG:4326, effective query limits,
and the BOM/null-geometry warning report. The input and index are charged before source publication.
Close first invalidates the source/live cursor, then drops its input/index references; immutable
metadata, reports, and already yielded records remain valid.

One cursor scans physical entries in document order and calls G4 `recordExamined()` for every Feature,
including null and bounds-filtered entries, and for the one synthetic entry represented by a bare
geometry root. It tests the retained envelope before reparsing. A matching entry is reparsed from its
bounded byte slice, applies `ALL|NONE|ONLY` without exposing a JSON value,
constructs one immutable record, and charges `recordReturned` before publication. `NONE` need not
materialize properties during the cursor pass, but opening has already validated them. This preserves
source order and one-live-cursor/cancellation/lifecycle semantics without materializing the complete
record set or performing network/disk I/O during rendering. No spatial index is added before evidence.

All controlled byte/token/member/string/coordinate/property loops poll cancellation at most every
4,096 primitive units and immediately before source/current publication. A Jackson call is an opaque
bounded token step checked before and after. Opening cancellation publishes no source; cursor
cancellation follows G4 and leaves an open parent reusable. There is no background parsing or
asynchronous callback.

`GeoJsonLimits` is an immutable typed value. Count/character/input fields are positive `int` values;
owned bytes is positive `long`. Constructor and complete withers enforce hard maxima and
`perFeatureProperties <= totalPropertyValues`, `positionsPerGeometry <= totalPositions`, and
`scalarCharacters <= aggregateCharacters`. Let `propertyContainers` be the checked ceiling division
of `totalPropertyValues` by `perFeatureProperties`; it must not exceed `physicalFeatures`. The exact
minimum member budget that can expose the total-property ceiling is `totalPropertyValues + 3` when
one root Feature suffices, otherwise `totalPropertyValues + 3 * propertyContainers + 2` for the
required FeatureCollection and Feature members. That checked result must not exceed `objectMembers`.
The corresponding checked minimum for the physical-feature ceiling is three members for one root
Feature, otherwise `3 * physicalFeatures + 2`; it too must fit `objectMembers`. Finally, using checked
`long` arithmetic, the value requires
`tokens >= 4 * totalPositions + 2 * objectMembers + 32`. These are conservative reachability
invariants: each 2D position has four tokens and each member has at least a name/value pair before
container/root overhead. A failed constructor/wither leaves no value. Input and owned-byte ceilings
remain independently tighten-able and may intentionally become the first effective bound. Defaults
and hard maxima are:

| Open-time ceiling | Default | Hard maximum |
| --- | ---: | ---: |
| Encoded input bytes | 16,777,216 | 268,435,456 |
| JSON nesting depth, root = 1 | 64 | 128 |
| Tokens | 16,000,000 | 134,217,728 |
| Object members | 2,000,000 | 16,000,000 |
| Physical features | 100,000 | 1,000,000 |
| Total coordinate positions | 2,000,000 | 16,000,000 |
| Positions in one geometry | 1,000,000 | 16,000,000 |
| Total parts/rings/polygons | 250,000 | 2,000,000 |
| Properties in one Feature | 256 | 4,096 |
| Total property values | 1,000,000 | 8,000,000 |
| Characters in one member name | 256 | 256 |
| Characters in one scalar string | 65,536 | 1,048,576 |
| Aggregate decoded string characters | 16,777,216 | 134,217,728 |
| Characters in one number token | 128 | 256 |
| Conservatively owned open-time bytes | 268,435,456 | 1,073,741,824 |
| Retained opening warnings | 256 | 4,096 |

Counts include ignored foreign subtrees, null geometries, member names/values, repeated ring closure,
the retained input copy, member-range/object-stack work, temporary duplicate/ID state, packed index,
and simultaneously live defensive copies. Logical charges use G4's primitive/reference table with no
identity deduplication, checked `long` prospective arithmetic, equality acceptance, and maximum-plus-
one rejection. The reader retains at most the input hard maximum and probes one extra byte separately;
it never allocates `maximumInputBytes + 1`. Jackson's `StreamReadConstraints` is configured no looser
than the public values, while project counters remain authoritative and map every overrun to the same
stable public outcome. Constructor tests exercise every individual hard maximum, every cross-field
equality and one-less violation, and checked overflow before implementation tests exercise each
runtime ceiling in isolation, including a total-property fixture that uses the minimum required
Feature containers and members.

### Diagnostics, verification, and decomposition

Every diagnostic uses the caller source ID and component `geojson`; a feature-local result has its
positive physical record number, while other location fields and raw parser byte/character offsets are
absent. No path, member/property name, value, JSON snippet, Jackson class/message/location, or cause
message enters a stable message or context. The exact first-profile outcomes are:

| Code | Severity/context | Meaning |
| --- | --- | --- |
| `GEOJSON_UTF8_BOM_IGNORED` | warning, empty context | One leading UTF-8 BOM was consumed. |
| `GEOJSON_NULL_GEOMETRY_SKIPPED` | warning, empty context | One fully validated Feature emitted no record. |
| `GEOJSON_IO_FAILED` | error; `operation=open|size|read|close`, `reason=notFound|accessDenied|closed|other` | Bounded local I/O failed. |
| `GEOJSON_ENCODING_INVALID` | error; `reason=unsupportedBom|malformedUtf8|unicodeScalar` | Input is outside the UTF-8/Unicode profile. |
| `GEOJSON_JSON_INVALID` | error; `reason=syntax|trailingContent|duplicateMember` | Strict JSON structure is invalid. |
| `GEOJSON_PROFILE_UNSUPPORTED` | error; `construct=root|nestedCollection|geometryCollection|emptyGeometry|positionArity|nestedProperty|legacyCrs` | Well-formed content is outside the supported profile. |
| `GEOJSON_VALUE_INVALID` | error; `field=type|features|geometry|properties|id|bbox|coordinates|propertyName|propertyValue`, `reason=missing|null|kind|cardinality|closure|range|nonFinite|length|number` | A supported member has an invalid value. |

`SOURCE_DUPLICATE_FEATURE_ID`, `SOURCE_LIMIT_EXCEEDED`, `SOURCE_CANCELLED`, and
`SOURCE_CLOSE_FAILED` retain their exact G4 shapes. GeoJSON open limits use `scope=geojsonOpen` and
closed limit tokens matching the table's accessor names; query limits use the existing G4 scope.
Foreign-member skip is intentionally silent, so warning omission counts only BOM/null-geometry
warnings.

Deterministic precedence is public arguments/lifecycle, already-cancelled token, input bytes,
open-time owned bytes, UTF-8/BOM, strict token syntax, prospective structural limits, duplicate member
at its encounter, then object semantics in `type`, prohibited `crs`, required members, `id`,
`geometry/coordinates`, `properties`, `bbox`, and foreign-member order. Coordinate and property tokens
retain document order; output count/ID duplication/final allocation and cancellation precede source
publication. The first terminal error remains primary; cleanup is suppressed.

No production test lands in G10-002. The approval packet contains representative RFC fixtures for all
six geometries, member-order permutations, BOM, null geometry, flat properties/IDs/bbox/foreign
members, plus rejection tables for duplicates, invalid Unicode/JSON/numbers, Z/M, empty/malformed
geometry, nested properties, legacy CRS, exact/one-over limits, and cancellation. It also records the
pinned dependency graph/license and direct-construction/native risk analysis.

After approval, create these serial, one-to-five-day implementation cards; none exists merely to make
an empty module:

1. `G10-020` — create the optional adapter with bounded byte snapshot/index/cursor, Feature and
   FeatureCollection Point/MultiPoint, IDs/properties/query lifecycle, publication staging, and an
   offline consumer.
2. `G10-021` — add line, polygon, and multipart geometry through query and tolerant map rendering.
3. `G10-022` — close every diagnostic/limit/cleanup case and add deterministic bounded mutation
   testing.
4. `G10-023` — add a small provenance-recorded RFC/real-producer fixture set and runnable GeoJSON
   viewer; extend `renderRegression` without a new corpus command.
5. `G10-024` — extend the one native executable with explicit Jackson construction, one valid
   query/render and one exact malformed outcome, then record the bounded Linux claim.

G10-021 depends on G10-020; G10-022 depends on G10-021; G10-023 depends on G10-022; G10-024 depends
on G10-023. Shared publication/native inventories follow the append-only single-owner rule. Broader
properties, GeometryCollection, Z/M, sequences, remote retrieval, writing, alternate parsers, and
format-specific performance optimization require new evidence and tasks.
