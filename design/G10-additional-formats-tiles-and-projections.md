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

## GeoTIFF raster and elevation profile decision (G10-003)

### One strict JDK reader with caller-selected semantics

The **G10 GeoTIFF profile and routing approval** checkpoint selects one future JDK-only,
AWT-free `mundane-map-io-geotiff` module. It is a published Level 2 runtime module that depends on
API and only the checked binary, raster-resampling, affine, CRS, and packed-elevation algorithms it
uses from core. It is not an Optional adapter, does not use `ImageIO`, and has no external library,
native codec, service provider, reflection, discovery, memory mapping, or network path.

The public surface remains three immutable format values plus one facade:

```text
GeoTiffFiles
  openRaster(SourceIdentity identity, Path path,
             GeoTiffRasterOptions options,
             CancellationToken cancellation) -> RasterSource
  openRaster(SourceIdentity identity, byte[] encoded,
             GeoTiffRasterOptions options,
             CancellationToken cancellation) -> RasterSource
  openElevation(SourceIdentity identity, Path path,
                GeoTiffElevationOptions options,
                CancellationToken cancellation) -> ElevationSource
  openElevation(SourceIdentity identity, byte[] encoded,
                GeoTiffElevationOptions options,
                CancellationToken cancellation) -> ElevationSource

GeoTiffRasterOptions(GeoTiffLimits formatLimits,
                     RasterSourceLimits requestLimits)
  defaults()
  withFormatLimits(...)
  withRequestLimits(...)

GeoTiffElevationOptions(ElevationUnit elevationUnit,
                        GeoTiffLimits formatLimits,
                        ElevationSourceLimits sourceLimits)
  of(ElevationUnit elevationUnit)
  withFormatLimits(...)
  withSourceLimits(...)

GeoTiffLimits
  defaults()
  complete typed accessors and withers for the ceilings below
```

Convenience overloads without a token delegate to `CancellationToken.none()`. The caller selects
the semantic route. `openRaster` requires cell-area metadata and returns `RasterSource`;
`openElevation` requires sample-post metadata and returns `ElevationSource`. Sample values, band
names, photometric tags, filename suffixes, or no-data metadata never guess a route. A route/profile
mismatch is terminal before sample allocation. The two options types avoid an inactive raster or
elevation limit inside one generic bag; no TIFF header, tag, GeoKey, segment, sample-type, decoder, or
external-library type is public. Elevation has no parameterless defaults: `of(unit)` combines the
explicit caller declaration with default format/source limits, and the immutable unit has no
secondary default that can be selected accidentally during open.

This is an interoperability subset of [OGC GeoTIFF 1.1](https://docs.ogc.org/is/19-008r4/19-008r4.html),
not a claim to arbitrary TIFF 6.0 or GeoTIFF conformance. General TIFF metadata/image support belongs
neither in the Level 1 image reader nor behind an opaque Java2D provider. A future GDAL decision must
consume this profile and show a concrete missing capability before accepting JNI; G11-004 therefore
depends on G10-003. GeoTIFF and DTED remain separate format readers and converge only on the G9
`ElevationSource` contract.

The strategy comparison is closed for this profile. A small JDK parser/decoder is selected because
the accepted tag, sample, and compression matrix is finite and can expose every allocation and
diagnostic boundary. JDK `ImageIO` is rejected because TIFF metadata selection, numeric elevation,
provider discovery, and intermediate allocation would sit behind `java.desktop` and an opaque SPI.
GDAL's permissive license does not offset its JNI, platform packaging, process-global driver, PROJ
data, and much broader attack surface; it is deferred to G11-004 rather than added as a second reader.
The selected module therefore adds no third-party license or dependency inventory, and any future
external implementation remains a separately named Optional adapter rather than replacing this
contract silently.

### Closed container, layout, and compression profile

The exact first container matrix is:

| Concern | Accepted | Rejected or deferred |
| --- | --- | --- |
| Header | Classic TIFF version 42; `II` and `MM` byte order | BigTIFF version 43, invalid byte-order aliases |
| Directories | Exactly one nonempty top-level IFD; ascending unique tag IDs; next offset zero | Pages, chained IFDs, SubIFDs, overviews, masks, cycles |
| Value storage | TIFF BYTE/ASCII/SHORT/LONG/RATIONAL/DOUBLE forms required below; inline or one bounded out-of-line range | Signed/64-bit IFD offsets, recursive values, unsupported semantic types |
| Sample organization | Contiguous/chunky samples; exactly strips or exactly tiles | Planar-separate, mixed declarations, sparse zero-offset segments |
| Compression | None `1`, Adobe Deflate `8`, PackBits `32773`; Predictor absent or `1` | LZW, old Deflate `32946`, JPEG, CCITT, Predictor 2/3, LERC, ZSTD, WebP |
| Orientation/fill | Orientation absent or `1`; FillOrder absent or `1` | Flipped/rotated storage orientation, reverse fill order |

Every profile requires ImageWidth, ImageLength, BitsPerSample, PhotometricInterpretation, and exactly
one complete strip/tile family. Compression omission means None; SamplesPerPixel omission means one;
PlanarConfiguration omission means contiguous; Predictor, Orientation, and FillOrder omission mean
one. SampleFormat omission means unsigned only for the raster profiles and is invalid for elevation.
ExtraSamples is absent without alpha and is exactly one SHORT value `2` with alpha. Dimensions,
RowsPerStrip, and strip offsets/counts use only their TIFF-permitted SHORT/LONG forms. TileWidth and
TileLength use SHORT/LONG, TileOffsets uses LONG, and TileByteCounts uses SHORT/LONG. NewSubfileType is
LONG count one. Every other scalar code is SHORT count one; BitsPerSample and SampleFormat are SHORT
arrays, and ExtraSamples is SHORT count one when present. The GeoKey directory uses SHORT, citation
and no-data parameters use ASCII, and the three georeference tags use DOUBLE. No coercion between
signedness, scalar width, ASCII numbers, or floating tag types occurs. BitsPerSample has exactly
`SamplesPerPixel` entries. A present SampleFormat also has exactly that many entries; every entry must
equal the one profile value. These count rules run before comparing the sample values.

The complete recognized semantic tag set is closed:

```text
254 NewSubfileType (absent or zero only)
256 ImageWidth                 257 ImageLength
258 BitsPerSample              259 Compression
262 PhotometricInterpretation  266 FillOrder
273 StripOffsets
274 Orientation                277 SamplesPerPixel
278 RowsPerStrip               279 StripByteCounts
284 PlanarConfiguration        317 Predictor
322 TileWidth                  323 TileLength
324 TileOffsets                325 TileByteCounts
338 ExtraSamples               339 SampleFormat
33550 ModelPixelScale          33922 ModelTiepoint
34264 ModelTransformation      34735 GeoKeyDirectory
34737 GeoAsciiParams           42113 GDAL_NODATA (elevation only)
```

Exactly these benign presentation/provenance tags may also occur and are ignored after structural
validation: DocumentName `269`, ImageDescription `270`, Make `271`, Model `272`, XResolution `282`,
YResolution `283`, ResolutionUnit `296`, Software `305`, DateTime `306`, Artist `315`, HostComputer
`316`, and Copyright `33432`. The text tags are one nonempty 7-bit TIFF ASCII string with a declared
count including exactly one final NUL and no embedded NUL, bounded by `tagPayloadBytes`. X/YResolution
are RATIONAL count one with a nonzero denominator; ResolutionUnit is SHORT count one and value
`1|2|3`. Their values do not affect placement, color, diagnostics, or output. Every other tag ID,
including JPEG tables, ICC/XML/RPC/GDAL metadata, EXIF/GPS pointers, color maps, transfer functions,
and unknown/private tags, is unsupported with numeric tag context. Tag `330` maps to `construct=subIfd`,
tag `34736` to `construct=geoDoubleParams`, and every other unlisted tag to `construct=tag`.
NewSubfileType value `1` maps to `construct=overview`, value `4` to `construct=mask`, and every other
nonzero value to `construct=tag`. There is no open-ended classification or generic ignored-tag map.

The IFD parser treats every offset/count as unsigned and promotes it to checked `long` before
addition or multiplication. Each entry's declared byte count is established before inline-versus-
offset selection. The first IFD offset, every out-of-line value offset, and every selected image-
segment offset must be positive and even; unused inline value bytes are zero. Misalignment is a
header, tag, or segment grammar error respectively. Every TIFF ASCII field's count includes its final
NUL. The checked complete first-IFD range—entry count, all 12-byte entries, and next-offset word—lies
inside the snapshot and is disjoint from header bytes `[0,8)`. Every physical out-of-line tag payload
and image segment also lies inside the snapshot and is disjoint from the header, complete IFD, every
other tag payload, and every image segment. Thus tag payloads and segments are each pairwise disjoint
and mutually disjoint. The sole permitted logical containment is a citation key's validated slice
inside its owning GeoAsciiParams payload. Citation sibling slices are disjoint and, as specified
below, cover that payload before its NUL. No other key/tag alias or nested range is accepted. Ignored
allowlisted tags remain range/type/count/allocation validated and counted but never become a public/
private metadata tree.

Strips require positive `RowsPerStrip`, the exact checked `ceil(height / rowsPerStrip)` offset/count
arrays, and natural north-to-south order. Tiles require positive width and height divisible by 16,
the exact checked `ceil(width/tileWidth) * ceil(height/tileHeight)` arrays, and row-major tile order.
Edge tiles retain their declared full tile byte shape; final strips use only their remaining rows.
Uncompressed byte counts equal the exact decoded shape. A compressed segment has a positive bounded
encoded count and one exact precomputed decoded count.

The private decoder dispatch is a closed switch, not a registry. None copies exactly. PackBits
accepts TIFF's literal, replicated, and `-128` no-op packets but must consume the whole segment and
produce exactly the planned length. Deflate uses a fresh JDK `Inflater` per segment and requires one
zlib stream that finishes at exactly that length with no preset dictionary, truncation, overrun,
concatenated stream, or trailing byte. All codecs use the same output bound and cancellation/accounting
path. Supporting LZW or a predictor is a later profile change with independent fixtures, not a codec
plug-in point.

### Exact raster and elevation sample profiles

`openRaster` accepts only these unsigned eight-bit chunky profiles:

| Photometric interpretation | Samples/pixel | Bits and sample format | RGBA mapping |
| --- | ---: | --- | --- |
| WhiteIsZero `0` | 1 | one `8`, unsigned/omitted | invert gray; alpha 255 |
| BlackIsZero `1` | 1 | one `8`, unsigned/omitted | copy gray; alpha 255 |
| WhiteIsZero or BlackIsZero | 2 | two `8`; unsigned; one ExtraSamples `2` | gray plus unassociated alpha |
| RGB `2` | 3 | three `8`; unsigned/omitted | exact R, G, B; alpha 255 |
| RGB `2` | 4 | four `8`; unsigned; one ExtraSamples `2` | exact R, G, B, unassociated alpha |

Palette, YCbCr, CMYK, Lab, multispectral bands, associated/unspecified alpha, more than one extra
sample, color maps, transfer functions, ICC/calibrated color, and raster no-data are unsupported.
Accepted bytes are treated as straight encoded gray/sRGB component values; there is no implicit
gamma/profile conversion. The output is the existing immutable `0xRRGGBBAA` buffer and uses G6's
exact nearest or bilinear request semantics. Opacity remains AWT presentation state.

`openElevation` accepts exactly one chunky BlackIsZero band:

| SampleFormat | BitsPerSample | Decoded value |
| --- | --- | --- |
| signed integer `2` | `16` or `32` | exact two's-complement value converted to `double` |
| IEEE floating point `3` | `32` or `64` | exact finite float/double value widened as needed |

Unsigned, complex, multiband, palette/color, associated-alpha, per-band scale/offset, and mixed sample
types are unsupported. Unmasked values must be finite and accepted negative zero becomes positive
zero under G9. The optional GDAL private tag `42113` is the only payload compatibility extension and
is documented as non-standard. It is one TIFF ASCII field whose declared count includes exactly one
final NUL and no embedded NUL. The preceding nonempty token may use up to
`GeoTiffLimits.maximumNoDataBytes() - 1` bytes; the complete field, including its terminator, is what
the limit counts. An integer token must be an exact in-range signed decimal. A floating token is
either one finite bounded decimal representable by the declared type or exact lowercase `nan`. A
finite sentinel masks samples equal after declared-type decoding; `nan` masks every NaN. Infinity is
never valid, and any NaN without the `nan` policy is `GEOTIFF_SAMPLE_INVALID`. Imagery rejects the
tag. This makes both the 128-byte default and 1,024-byte hard field ceilings reachable.

### GeoKeys, cell areas, and sample posts

Every accepted file has `GeoKeyDirectoryTag` with directory version `1`, key revision `1`, minor
revision `0` or `1`, an ascending unique key list, and bounded references. Exactly one horizontal
profile is accepted:

| Model | Required keys | Optional consistency keys | CRS metadata |
| --- | --- | --- | --- |
| Geographic | `GTModelType=2`, `GeographicType=4326` | angular units absent or `9102` | canonical EPSG:4326 |
| Projected | `GTModelType=1`, `ProjectedCSType=3857` | linear units absent or `9001`; geographic type absent or `4326` | canonical EPSG:3857 |

The SHORT directory count is exactly `4 + 4 * NumberOfKeys` after checked arithmetic and the configured
key limit. Its four-word header is validated before any entry and therefore has no key ID. Every
recognized numeric key entry has `TIFFTagLocation=0`, `Count=1`, and carries its unsigned SHORT value
in `ValueOffset`. A citation entry alone has `TIFFTagLocation=34737`, a positive count, and a checked
offset into that one ASCII payload. No entry may reference another TIFF tag or use a multi-value
numeric representation.

The only recognized key IDs are GTModelType `1024`, GTRasterType `1025`, optional GTCitation `1026`,
GeographicType `2048`, optional GeogCitation `2049`, optional GeogAngularUnits `2054`,
ProjectedCSType `3072`, optional PCSCitation `3073`, and optional ProjLinearUnits `3076`. A citation
key must reference tag `34737`; its positive slice lies before that tag's mandatory final NUL, ends
in `|`, and contains only printable 7-bit ASCII plus that terminator. GeoAsciiParams is absent when no
citation key exists, and every byte before its final NUL belongs to exactly one declaration-ordered,
non-overlapping citation slice. Keys `4096..4099` use unsupported construct `verticalCrs`; every other
unlisted key uses unsupported construct `geoKey` with its numeric ID.

The adapter normalizes the GeoTIFF longitude/latitude tuple to the library's x/y EPSG:4326 convention
at the format boundary. It resolves the selected definition through `CrsRegistry.level1()` and uses
`CrsMetadata.recognized` with both textual provenance optionals empty: a numeric GeoKey is not
fabricated into an exact input string. Other EPSG codes, user-defined keys, citations used as
definitions, WKT, vertical CRS/datum claims, compound CRS, arbitrary registry lookup, and heuristic
recognition are unsupported in the first profile. Bounded ASCII citation keys may reference one structurally valid
GeoAsciiParams payload and are ignored as provenance; raw citation text is never retained in a
diagnostic. GeoDoubleParams and every unlisted GeoKey follow the exact closed outcomes above.

Raster opening requires `GTRasterTypeGeoKey=1` (`PixelIsArea`). Elevation opening requires
`GTRasterTypeGeoKey=2` (`PixelIsPoint`) and uses only the caller's explicit
`GeoTiffElevationOptions.elevationUnit()`. Every vertical GeoKey `4096..4099`, including an orphan
`VerticalUnitsGeoKey`, is unsupported: G9 can retain a sample unit but cannot honestly retain a
vertical CRS/datum, so the reader neither discards those semantics nor treats one axis-unit key as a
complete definition. The three G9 units remain available through the explicit option and are never
inferred from horizontal CRS, sample magnitude, citation, or filename. Nonzero model Z scale/
tiepoint components and vertical transforms are likewise unsupported.

Georeferencing is exactly one of:

- one three-DOUBLE `ModelPixelScaleTag` plus one six-DOUBLE `ModelTiepointTag`; or
- for raster only, one 16-DOUBLE `ModelTransformationTag` whose homogeneous matrix is a finite,
  invertible 2D affine transform with no perspective, raster/model Z coupling, or non-identity Z row.

The two forms are mutually exclusive. Pixel scale requires finite positive X/Y scales, zero Z scale,
one finite tiepoint, zero raster/model Z, and the standard north-up equation: columns increase east
and rows increase south. The tiepoint need not be `(0,0)`, so translation is derived with checked
arithmetic rather than assumed.

GeoTIFF area coordinates address cell corners. The raster adapter evaluates the approved transform
at `(column + 0.5,row + 0.5)` and constructs G6's center-based `RasterAffineTransform`; a strictly
north-up, non-sheared result uses `RasterGridPlacement.axisAligned`, otherwise it uses the real affine
variant. Its metadata bounds remain the four transformed outer cell corners. Point coordinates
address elevation posts directly with no half-cell shift. Elevation therefore requires the scale/
tiepoint form whose derived coefficients have positive X, zero shear, negative Y row direction, and
produces G9 `sampleBounds` from posts `(0,0)` and `(width-1,height-1)`. Rotated, sheared, reversed,
wrapped, or collapsed elevation grids fail rather than being warped into a regular grid.

The reader validates finite/invertible placement and source metadata but performs no reprojection,
domain clamp, coordinate repair, vertical conversion, or raster warp. G4/G6 still require an equal
recognized display CRS for raster attachment, and G9 query/render operations retain their own
recognized-CRS/domain rules.

### Snapshot, source lifecycle, and bounded work

A successful path open reads and closes one file into an exact immutable snapshot; a byte-array open
defensively copies. The reader probes at most one byte beyond the configured input ceiling without
allocating `maximum + 1`, detects size change/truncation, and retains no path, channel, parser,
`Inflater`, mapped buffer, thread, executor, cache, or cancellation token. One private immutable
`TiffPlan` contains only primitive header facts, placement/CRS values, and packed segment offsets/
counts. Raster sources retain that plan and snapshot. Close marks the source closed, drops those
references, is idempotent, and cannot fail through ordinary array release; metadata, limits, and the
empty opening report survive.

A raster read validates its strict window and output first, determines intersecting segments in
natural order, and charges every complete segment cell that must be decoded as source work. It
allocates one reusable buffer sized to the largest intersecting decoded segment, one strict-window
RGBA staging buffer, and the final output builder only after combined G4/format preflight. It decodes
each segment at most once in that read, copies only requested cells, then reuses `RasterResampling`.
Compressed payload failure terminates that read with no partial value and leaves the open source
reusable. There is no persistent decode/resample cache before G10-037 evidence.

Elevation opening decodes every segment eagerly into one temporary row-major `double[]` plus no-data
mask, closes/releases the encoded transaction state, and calls `PackedElevationGrid.copyOf`. The
opaque defensive copy and temporary/final coexistence are both prospectively charged, just as the
DTED reader does. Immediately after that opaque copy, the opener checkpoints cancellation. If
cancellation won during the copy, it closes the new grid, keeps cancellation primary, suppresses any
unexpected cleanup failure, and publishes nothing; otherwise it releases temporary references and
returns the grid. This final arbitration preserves G9's failure-free random `sample`. The returned
source owns no encoded bytes or format plan.

All controlled byte, entry, key, segment, decompression, pixel, and sample loops checkpoint before
allocation/publication, at least once per row/segment, and within 4,096 primitive units. Sources use
G4/G9 external serialization; no method starts background work. A cancelled open publishes nothing;
a cancelled raster read leaves the source reusable. The first failure remains primary and open-time
resource cleanup is suppressed in encounter order.

`GeoTiffLimits` is immutable, uses positive typed fields and checked withers, and has these inclusive
defaults and hard maxima. The no-data and GeoASCII ceilings are at least two bytes; no-data and
GeoASCII bytes do not exceed one tag payload; encoded-segment and tag-payload bytes do not exceed
input bytes; and decoded-segment bytes do not exceed format working bytes. Every constructor/wither
enforces those reachability relations with checked arithmetic and leaves no partial value.

| Ceiling | Default | Hard maximum |
| --- | ---: | ---: |
| Input snapshot bytes | 268,435,456 | 1,073,741,824 |
| Width or height | 65,536 | 131,072 |
| Declared pixels | 268,435,456 | 2,147,483,647 |
| IFD entries | 512 | 4,096 |
| GeoKeys | 128 | 1,024 |
| Segments | 262,144 | 1,048,576 |
| One encoded segment | 67,108,864 | 268,435,456 |
| One decoded segment | 67,108,864 | 268,435,456 |
| One out-of-line tag payload | 67,108,864 | 268,435,456 |
| GeoASCII bytes | 65,536 | 1,048,576 |
| Complete GDAL no-data field bytes, including NUL | 128 | 1,024 |
| Format working bytes per open/read | 268,435,456 | 1,073,741,824 |

Input storage, G4 published/intermediate buffers, G9 retained sample storage, and format working
storage have independent ceilings; every phase checks its components and their combined sum with
checked arithmetic, but no component is silently substituted for another. Because elevation drops
the snapshot before the final packed-grid copy, its two conservative peaks are input plus format work
and format work plus G9 retained storage, not all three at once. Format working bytes include packed
IFD/GeoKey/segment plans, temporary
sample/mask storage, one decoder scratch, and other project-owned parser arrays, but exclude the input
snapshot and the final G4/G9 payload already charged by their contracts. Every range end, element
count, pixel/sample product, decoded length, array index, and combined peak uses checked `long`
arithmetic before conversion/allocation. Java byte/primitive-array capacity and Classic TIFF's
unsigned 32-bit fields remain lower effective maxima when callers raise limits. Equality passes;
maximum plus one and arithmetic overflow fail before dereference or allocation. The strict profile
has no opening recovery, so opening reports are empty and no redundant format-warning limit exists.

### Diagnostics, evidence, and implementation decomposition

The stable GeoTIFF vocabulary is:

| Code | Closed context | Meaning |
| --- | --- | --- |
| `GEOTIFF_IO_FAILED` | `operation=open|size|read|close`, `reason=notFound|accessDenied|changed|other` | A path snapshot transaction failed. |
| `GEOTIFF_HEADER_INVALID` | `field=byteOrder|version|ifdOffset|ifdCount|nextIfd`, `reason=value|range|alignment|overflow` | The Classic TIFF envelope is malformed. |
| `GEOTIFF_TAG_INVALID` | `tag`, `reason=order|duplicate|missing|type|count|range|alignment|overlap|encoding|value` | An IFD declaration is invalid, including malformed tag `42113`. |
| `GEOTIFF_GEOKEY_INVALID` | `reason=header` with no `key`; otherwise mandatory `key` and `reason=order|duplicate|location|count|range|value` | The GeoKey directory is malformed. |
| `GEOTIFF_PROFILE_UNSUPPORTED` | `construct=bigTiff|multipleIfd|subIfd|overview|mask|tag|geoKey|sampleOrganization|compression|predictor|orientation|photometric|alpha|sampleType|rasterNoData|verticalCrs|horizontalCrs|geoDoubleParams|georeference|route`; optional relevant `tag|key|compression` | Valid TIFF/GeoTIFF content is outside the approved matrix or wrong opener. |
| `GEOTIFF_GEOREFERENCE_INVALID` | `reason=missing|conflict|nonFinite|singular|orientation|collapsed` | Placement cannot represent the selected source contract. |
| `GEOTIFF_SEGMENT_INVALID` | `segment`, `reason=count|range|alignment|overlap|encodedLength|decodedLength` | Strip/tile storage is inconsistent. |
| `GEOTIFF_DECODE_FAILED` | `segment`, `compression`, `reason=truncated|overrun|dictionary|unfinished|trailing|packet` | A selected payload cannot decode exactly. |
| `GEOTIFF_SAMPLE_INVALID` | `segment`, `reason=nonFinite` | One decoded, unmasked elevation sample is non-finite. |

`SOURCE_LIMIT_EXCEEDED` uses `scope=geoTiffOpen|geoTiffRead` and exact limit tokens
`inputBytes|dimension|pixels|ifdEntries|geoKeys|segments|encodedSegmentBytes|decodedSegmentBytes|tagPayloadBytes|geoAsciiBytes|noDataBytes|workingBytes`;
G4/G9 limits retain their existing scopes. `SOURCE_CANCELLED` retains its common shape. Diagnostics
use the caller's source identity and may use a checked TIFF byte location, tag/key number, or zero-
based segment index. They never contain a path, citation, raw bytes, numeric sample/no-data value,
compressed data, JDK exception message, or implementation class.

The GDAL no-data tag's TIFF type/count/NUL/ASCII/token/range grammar is tag-level validation and uses
`GEOTIFF_TAG_INVALID tag=42113`; it has no segment context. Only a payload sample encountered after a
valid policy reaches `GEOTIFF_SAMPLE_INVALID`, so every mandatory context key is always available.

After a valid `II|MM` byte-order marker, version `43` is the recognizable unsupported BigTIFF profile;
every other non-42 version is `GEOTIFF_HEADER_INVALID field=version reason=value`. After a valid first
IFD, next offset zero is accepted, an even in-input offset outside the header/first IFD with room for
an IFD count is recognizable `construct=multipleIfd`, and a nonzero misaligned, overlapping, or out-
of-range value is instead
`GEOTIFF_HEADER_INVALID field=nextIfd reason=alignment|range`. The reader does not parse a second IFD
merely to prove it is unsupported. These mappings precede the generic profile/header alternatives.

Opening precedence is public arguments, already-cancelled token, input bytes and snapshot I/O,
Classic header/IFD structure, tag ranges and semantic matrix, segment plan, GeoKey structure, selected
route/sample profile, georeference/CRS, prospective allocation, payload validation for elevation, and
publication. Raster payload diagnostics occur later in segment order during `read`. Within either
operation the first terminal result is primary; cancellation wins only at an explicit checkpoint
before another terminal result has been established.

No production test or module lands in G10-003. The approval packet maps hand-built fixtures across
both byte orders, inline/out-of-line values, strips/tiles, all sample profiles, three compressions,
both georeference forms, area/point semantics, two CRSs, three elevation units, and finite/NaN no-
data. Negative tables cover offset/count overflow, tag/key order and duplication, aliases/overlap,
segment count/length, compressed bombs/truncation/trailing bytes, wrong opener, unsupported profiles,
conflicting/non-finite transforms, non-finite samples, every exact/one-over ceiling, cancellation,
and deterministic bounded mutation.

After approval create nine working cards, not empty scaffolds:

1. `G10-030` — create the module with a Classic header/IFD and minimal GeoKey parser, one little-
   endian uncompressed stripped BlackIsZero raster, EPSG:4326 scale/tiepoint area placement, strict
   window reads, map rendering, a minimal viewer, publication staging, and an offline consumer.
2. `G10-031` — add big-endian values, uncompressed tiles, WhiteIsZero/RGB/unassociated-alpha raster
   profiles, EPSG:3857, and exact segment/window behavior.
3. `G10-032` — add PackBits and Deflate through the existing raster read/render slice, with exact
   decompression bounds and malformed-stream tests.
4. `G10-033` — add the affine ModelTransformation raster path and complete tolerant
   `renderRegression` placement/CRS evidence.
5. `G10-034` — add eager PixelIsPoint signed Int16/Int32 elevation with explicit units, exact
   position queries, colorization, and one rendered terrain slice; depend on G9-002 and G9-005.
6. `G10-035` — add Float32/Float64 elevation, finite/`nan` no-data, hillshade, and compressed/tiled
   elevation parity.
7. `G10-036` — close every limit, diagnostic, cancellation, cleanup, alias/overlap, and deterministic
   hostile-mutation case across both routes.
8. `G10-037` — add a small legally redistributable independent-writer corpus with pinned generation
   recipe/tool versions/licenses/hashes, complete both viewer modes, and append bounded window/memory
   cases to `performanceEvidence`; add no new corpus command.
9. `G10-038` — extend the shared JVM/Linux Native Image scenario across None/PackBits/Deflate,
   raster/elevation success, and one exact malformed outcome; record the bounded claim without a
   native codec.

The cards are serial because they share one parser, segment decoder, facade, module, publication, and
viewer: each G10-031 through G10-038 depends on its immediate predecessor, while G10-034 additionally
depends on G9-002 and G9-005. This is deliberate path safety, not an architectural coupling between
DTED and GeoTIFF. TIFF writing,
BigTIFF, LZW/JPEG/predictors, palette/YCbCr/CMYK, arbitrary CRS, multiple IFDs/overviews, masks,
COG/range access, persistent caches, GDAL, and format-specific acceleration require evidence and new
tasks.

## SQLite container adapter profiles (G10-004)

### Approval and dependency boundary

The named checkpoint is **G10 SQLite container profile approval**. It independently approves the
GeoPackage and MBTiles profiles below, one external-driver deployment, the bounded JNI qualification,
and the later implementation graph. G10-004 creates no module or production code.

The future implementations are two published Level 2 Optional adapters:

```text
mundane-map-io-geopackage-xerial
  -> mundane-map-api
  -> selected mundane-map-core accounting and geometry algorithms
  -> mundane-map-io-image for embedded PNG/JPEG tiles
  -> JDK java.sql
  -> pinned Xerial SQLite JDBC code and Linux native classifiers

mundane-map-io-mbtiles-xerial
  -> mundane-map-api
  -> selected mundane-map-core raster algorithms
  -> mundane-map-io-image for embedded PNG/JPEG tiles
  -> JDK java.sql
  -> the same pinned Xerial classifiers
```

The format name and implementation choice are both visible in the artifact name. There is no generic
SQLite module, JDBC SPI, database abstraction, shared public container handle, or empty format module.
The small private connection-policy implementation is duplicated deliberately: the two modules share
no production dependency merely to hide several fixed statements and cleanup steps. Public signatures
contain only JDK and MundaneJ values; JDBC, Xerial, SQLite, SQL, statement, result-set, and native
loader types remain private. `mundane-map-api` is unchanged.

Both tile adapters reuse the complete G6 PNG/JPEG profile through `mundane-map-io-image`; they do not
reimplement image validation or call ImageIO. G0's normal ban on arbitrary format-module coupling is
refined to permit an explicitly inventoried, acyclic container/transport-to-codec edge after both
ends provide working behavior. Architecture tests allowlist only the concrete edges. For this task
family those are `geopackage-xerial -> image` and `mbtiles-xerial -> image`; reverse edges, cycles,
transitive toolkit leakage, and an open-ended I/O dependency category remain forbidden.

On 2026-07-13 the approved resolvable dependency is `org.xerial:sqlite-jdbc:3.53.0.0`, the latest
split release present in Maven Central. The exact artifacts are:

| Artifact | SHA-256 | Use |
| --- | --- | --- |
| `sqlite-jdbc-3.53.0.0-without-natives.jar` | `8098b34191dd832a112934e12087e0d430b7e9ae93aee7c155e06f82866b1b2b` | Private JDBC implementation classes |
| `sqlite-jdbc-3.53.0.0-natives-linux.jar` | `b56611404e866e2fc9bf5b5b7d731d650205a275e6bb4b035e03d5f28b89ddd1` | Upstream Linux SQLite JNI binaries |
| `sqlite-jdbc-3.53.0.0.pom` | `7c897bfb3502e81d2ec7ed02c0221789805addb1bdce9641e513bf7b61730b7b` | Dependency and license provenance |

The ordinary all-platform JAR, SHA-256
`303e8150100982f2ed7d1b82d897278ef7744bd494c28ad4e7042b7914591697`, is rejected. The Linux
classifier still contains upstream binaries for several Linux architectures and libc variants because
Xerial publishes no narrower classifier; the first supported and tested runtime is Java 21 on Linux
x86-64 with glibc only. The unused classifier entries are inventoried, not repackaged into a MundaneJ
JAR, and do not widen the support claim. macOS, Windows, musl, other architectures, Android, a system-
SQLite mode, and caller-supplied native libraries require separate packaging and evidence decisions.

The implementation task must recheck Maven Central presence, release signatures, current security
advisories, the exact resolved runtime graph, and all checksums without silently changing versions.
It records Xerial's Apache-2.0 terms, the retained Zentus BSD notice, SQLite's public-domain status,
any bundled notice obligations, and the pinned SQLite compile options required by GeoPackage 1.4
Requirement 9. No version range, rich latest selector, local Maven fallback, or unverified GitHub-
release binary is accepted.

The code classifier physically contains `META-INF/services/java.sql.Driver`, Native Image feature
metadata, dormant resource/URL loading paths, JNI declarations, native extraction, and process-global
loader state. Project bytecode does not use any discovery path: it constructs the pinned
`org.sqlite.jdbc4.JDBC4Connection` directly with private fixed properties. The external loader still
extracts and loads its selected JNI library and therefore needs a writable temporary directory. That
bounded third-party mechanism is accepted only inside these Optional adapters under G0's recorded
external-artifact qualification. The service descriptor and Native Image metadata are not copied into
a MundaneJ artifact or explicit native configuration, and no project code calls `DriverManager`,
`ServiceLoader`, `SQLiteDataSource`, `Class.forName`, reflection, resource lookup, or native loading.

These adapters are JVM-only and have Native Image policy `not-targeted`. Xerial's own reachability
metadata is not a project compatibility claim. Neither adapter enters the shared native executable,
and the Linux JVM evidence below cannot be described as Native Image evidence. Any later claim needs
a new HITL packaging task that proves the exact native library, extraction/static-link policy,
reachability, cleanup, and format behavior without weakening the Level 1 rules.

Embedded database BLOBs establish the first real non-file consumer of G6's complete image profile.
G10-042 therefore adds one toolkit-neutral synchronous helper to `mundane-map-io-image`, and G10-043
reuses it:

```text
RasterImages.decode(byte[] encodedBytes, SourceIdentity identity,
                    EncodedRasterDecodeOptions options,
                    EncodedRasterDecoderRegistry decoders,
                    CancellationToken cancellation) -> RgbaPixelBuffer

EncodedRasterDecodeOptions(
    Optional<EncodedRasterFormat> expectedFormat,
    ImageSourceLimits imageLimits,
    RasterRequestLimits decodeLimits)
  defaults()
  expecting(EncodedRasterFormat)
  withImageLimits(...)
  withDecodeLimits(...)
```

The helper checks cancellation/encoded size, defensively copies the caller array, applies the same
complete PNG/JPEG header/container validation and explicit decoder registry as the file source, and
decodes exactly the full native-size image into one independently owned buffer. An absent expected
format trusts the signature; a present value must match it. It has no suffix, channel, placement,
world file, cache, source lifecycle, AWT type, or decoder discovery. Accounting includes the caller-
array copy, validation state, opaque decoder reservation, and returned RGBA buffer. The adapter wraps
an image diagnostic once with only its stable code; raw BLOBs and nested messages remain private.
This focused helper is smaller and safer than constructing an unplaced temporary `RasterSource` for
every tile or making private G6 parsers public.

### One strict read-only SQLite session policy

Each inspection call or returned source owns one direct connection. There is no pool, shared static
connection, thread-local, connection registry, arbitrary SQL hook, transaction API, or public database
handle. Inspection closes before returning an immutable catalog. A feature source owns its connection
until source close and permits G4's one live cursor. A tile source similarly owns one connection and
performs one serialized raster read at a time. Returned records, pixels, metadata, reports, and catalog
values are detached and remain valid after connection/source close.

Input is one caller-authorized local `Path`. Before JNI loading, the adapter:

1. rejects null, non-absolute-after-normalization, symbolic-link, non-regular, non-readable, empty,
   and over-limit files using `NOFOLLOW_LINKS`;
2. captures a non-null Linux file key, size, and last-modified time;
3. rejects sibling files with the exact `-journal`, `-wal`, or `-shm` suffixes;
4. reads and validates the fixed SQLite header, page-size/file-size relation, and format-specific
   application/user version fields without exposing input bytes; and
5. constructs its own percent-encoded `file:` URI with `mode=ro&immutable=1`—never a user URI,
   `:memory:`, `:resource:`, URL, or classpath name.

The same fingerprint and sidecar absence are checked before and after every public inspection, cursor
advance batch, raster read, and final publication. A mismatch permanently fails the source with a
stable input-changed diagnostic. `immutable=1` is safe only while the caller prevents concurrent
mutation; fingerprint checks detect ordinary changes but are not claimed to isolate a file from a
hostile writer that can preserve all metadata. Applications needing that adversarial boundary must
copy into a separately secured immutable file before opening.

The private connection uses SQLite `READONLY`, `URI`, and private-cache modes and immediately applies
and reads back this connection-local policy before format SQL:

```text
PRAGMA query_only=ON
PRAGMA trusted_schema=OFF
PRAGMA foreign_keys=ON
PRAGMA cell_size_check=ON
PRAGMA temp_store=MEMORY
PRAGMA mmap_size=0
PRAGMA automatic_index=OFF
PRAGMA cache_size=-8192
```

It never changes journal mode, attaches a database, loads an extension, executes schema-provided SQL,
invokes a user function, or writes a temp table. Selected/core objects must be real tables, not views,
virtual tables, shadow tables, or attached-schema objects. Inert triggers count toward schema limits
but are neither parsed nor executed because the adapter never writes. Every statement is a private fixed
`SELECT` or safe PRAGMA. Caller/database identifiers are length checked, contain no NUL, and are
quoted by doubling every double quote; values are bound parameters. SQL text and native exception
messages never enter diagnostics.

Private `SQLiteConnection.setLimit` calls reduce string/blob length, SQL length, columns, expression
depth, compound selects, function arguments, attached databases, LIKE pattern length, bound
variables, trigger depth, and worker threads before schema inspection. A private Xerial
`ProgressHandler` runs every 1,000 virtual-machine opcodes, polls the operation token, and charges the
operation budget prospectively; an interrupt is translated by the atomically recorded reason into
either cancellation or limit exhaustion. Java loops additionally poll before/after every opaque JDBC
call, at most every 4,096 rows/bytes/coordinates, and immediately before publication.

SQLite parsing, B-tree pages, page cache, JNI transitions, native result storage, and the driver's
temporary native extraction are opaque external allocations. The adapter therefore claims bounded
file size, SQLite limits, an 8-MiB connection page-cache request, VM work, returned rows/blobs,
project-owned allocations, and operation lifetime—not prospective byte-perfect native allocation.
That explicit qualification is part of the Optional-adapter approval and is never generalized to a
JDK-only parser. The first terminal format/cancellation/limit result is primary; statement,
progress-handler, connection, and temporary cleanup failures are suppressed in that order. Close is
idempotent and permanent even after cleanup failure.

### GeoPackage 1.4.0 profile

`mundane-map-io-geopackage-xerial` implements a strict, extension-free read-only subset of OGC
GeoPackage 1.4.0. It requires a case-insensitive `.gpkg` filename suffix, application ID `GPKG`,
`user_version=10400`, the required core tables,
and exact declared constraints needed by the selected content. Older/newer versions, extended
GeoPackages, every row in `gpkg_extensions`, related tables, attributes-only contents, metadata,
schema/data-column constraints, RTree, WebP, tiled gridded coverage, vector tiles, 3D, and custom
geometry types are deferred. The object inventory permits required core tables, an optional empty
`gpkg_extensions`, catalogued feature/tile user tables, their ordinary B-tree indexes and inert
triggers, and SQLite's fixed internal objects. An attributes/unknown contents row or any other
application table/view/virtual table is a recognizable unsupported content/extension construct rather
than silently ignored.

The lean public surface is:

```text
GeoPackages.inspect(Path, SourceIdentity, GeoPackageInspectOptions, CancellationToken)
  -> GeoPackageCatalog

GeoPackages.openFeatures(Path, SourceIdentity, String tableName,
                         GeoPackageFeatureOptions, CancellationToken)
  -> FeatureSource

GeoPackages.openTiles(Path, SourceIdentity, String tableName, int zoom,
                      GeoPackageTileOptions, EncodedRasterDecoderRegistry,
                      CancellationToken)
  -> RasterSource

GeoPackageCatalog
  featureTables() -> immutable List<GeoPackageFeatureTable>
  tileTables() -> immutable List<GeoPackageTileTable>
  report() -> DiagnosticReport

GeoPackageInspectOptions / GeoPackageFeatureOptions / GeoPackageTileOptions
  compose GeoPackageLimits with only the relevant G4 source limits/cache policy
```

Catalog descriptors expose bounded immutable table names, geometry kind/schema/CRS metadata, or tile
matrix levels and bounds. They expose no connection or lazy operation. Selection is an exact catalog
name, not a SQL fragment, pattern, ordinal guess, or automatic first table.

The feature profile accepts one ordinary user table per source, one `INTEGER PRIMARY KEY`, and one
2D geometry column declared as Point, MultiPoint, LineString, MultiLineString, Polygon, MultiPolygon,
or the corresponding generic Geometry only when every row is one of those types. IDs are the canonical
decimal primary-key values; rows are emitted in ascending primary-key order. The geometry and primary
key columns are not duplicated as attributes. Remaining columns map in declaration order:

| GeoPackage declared value | G4 value |
| --- | --- |
| BOOLEAN | `LOGICAL`, integer 0/1 only |
| TINYINT/SMALLINT/MEDIUMINT | range-checked 8/16/32-bit value returned as `Long` |
| INT/INTEGER | checked 64-bit `Long` |
| FLOAT | finite, exactly float-representable value returned as `Double` |
| REAL/DOUBLE | finite `Double` |
| TEXT | bounded `String` |
| BLOB | bounded `AttributeBytes` |
| DATE | strict ISO `LocalDate` |
| DATETIME | strict RFC 3339 text retained as `TEXT` |

Declared type names are ASCII case-insensitive. `TEXT` and `BLOB` alone may have one parenthesized
positive decimal maximum within the effective format ceiling; the declared maximum is enforced and
never used to truncate. Every other parameter, type alias, generated/hidden column, or extended type
is unsupported. SQLite storage-class disagreement, non-finite numbers, numeric overflow, invalid
UTF-8/text, invalid exact `YYYY-MM-DD` DATE, invalid exact UTC
`YYYY-MM-DDTHH:MM:SS.SSSZ` DATETIME, duplicate/blank/over-256-character attribute name, and
unsupported declared type are terminal schema or record diagnostics. SQL NULL maps to
`AttributeNull` only for nullable columns.

The selected `gpkg_geometry_columns` row requires `z=0` and `m=0`. Geometry BLOBs require the
standard `GP` header, version zero, reserved flags zero,
StandardGeoPackageBinary type, either byte order, SRS ID matching the selected table, and envelope
indicator zero or XY. XYZ/XYM/XYZM envelopes, extended binary, SQL/MM offsets, Z/M coordinates,
GeometryCollection, circular/curve/surface types, and trailing bytes are unsupported. WKB types one
through six map directly to G4 packed singular/multipart values. Counts, offsets, rings, closure,
finite coordinates, component homogeneity, declared type, and an optional XY header envelope are
fully validated; the envelope must contain the decoded geometry. Null or standard empty geometry is
skipped with one bounded stable warning and still charges records examined. There is no repair,
orientation inference, topology operation, or automatic spatial index.

Feature queries scan in primary-key order through one prepared statement. A valid header envelope may
reject a row before WKB construction; a missing envelope requires bounded decode. Final inclusive
intersection uses the decoded geometry envelope. Attribute projection is pushed into the fixed
selected-column statement, while G4 query accounting remains authoritative for returned values.

Before catalog publication, full `PRAGMA integrity_check` must yield exactly `ok` and
`PRAGMA foreign_key_check` must yield no row under the same VM/row/cancellation budgets. The three
required spatial-reference rows `-1`, `0`, and `4326` are present with their mandated numeric
organization relationships. `gpkg_spatial_ref_sys` is structurally retained. A row whose
case-insensitive organization is EPSG and
whose `srs_id` and `organization_coordsys_id` are both 4326 or both 3857 resolves through
`CrsRegistry.level1()` and retains the bounded definition text as source provenance. The definition is
not parsed, used as a competing authority, or heuristically matched; this mirrors G10-003's numeric
GeoKey trust boundary. Every other well-formed row becomes retained-unknown CRS metadata. A geometry/
header/table SRS mismatch is terminal. Raster rendering still follows G4's no-cross-CRS-warp rule.

The tile profile accepts one core `tiles` content/table, one matrix set, and unique matrix rows for
zooms `0..22`. Bounds and pixel sizes are finite; matrix/tile dimensions are positive; tile width and
height are exactly 256; declared pixel size must match the matrix-set extent divided by matrix pixels
within eight ULPs of the computed finite value. Tile columns/rows are zero-based from the upper-left,
inside the declared matrix, and logically unique. A caller opens one explicit zoom as a windowed
`RasterSource` over the complete declared matrix and matrix-set bounds. Missing tile rows are
transparent pixels with one coalesced warning per read, not an I/O retry or permanent cached value.

Every selected tile is exactly one Level 1 PNG or JPEG BLOB, signature/profile checked through the
explicit G6 decoder registry, and decodes to 256 by 256. The source has one optional successful-access
decoded-tile LRU, disabled by default, bounded by entries and exact RGBA bytes. Failed/cancelled reads
commit no cache promotions/admissions. There is no raw tile API, encoded-byte escape, overview
selection, automatic zoom choice, reprojection, resampling beyond G4/G6, background prefetch, or disk
cache.

### MBTiles 1.3 raster profile

`mundane-map-io-mbtiles-xerial` supports a single MBTiles 1.3 raster tileset. MBTiles has no stored
specification-version field: the optional `metadata.version` is a tileset revision and is never used
to infer conformance. SQLite application ID zero or the assigned `0x4d504258` (`MPBX`) is accepted;
another nonzero application ID is unsupported, and `user_version` has no MBTiles meaning. The adapter
recognizes the profile structurally and requires real `metadata`
and `tiles` tables. Although MBTiles permits compatible views, the first security profile rejects
views, virtual tables, extensions, and alternative schemas. Inert triggers are bounded and ignored;
no adapter statement can fire one. The object inventory otherwise permits ordinary indexes and fixed
SQLite internal objects only; grids, grid data, or any other application table are unsupported rather
than silently queried.

The public surface is:

```text
MbTiles.inspect(Path, SourceIdentity, MbTilesInspectOptions, CancellationToken)
  -> MbTilesMetadata

MbTiles.open(Path, SourceIdentity, int zoom, MbTilesOpenOptions,
             EncodedRasterDecoderRegistry, CancellationToken)
  -> RasterSource
```

`MbTilesMetadata` is a detached immutable value containing the bounded required `name` and normalized
format, optional WGS 84 bounds/center/minimum zoom/maximum zoom/type/revision/description/attribution,
the actual zoom set, and an opening report. Attribution remains plain unrendered text; HTML is not
interpreted. Metadata names are unique. Unknown rows are bounded and ignored with one warning; grids,
grid data, JSON/vector-layer metadata, UTFGrid, and arbitrary metadata exposure are outside the first
profile.

Accepted format values are exact `png`, `jpg`, `image/png`, or `image/jpeg`, normalized to the two G6
formats. `pbf`, WebP, gzip, mixed image formats, and any other media type are unsupported. The tiles
schema has exactly the four required compatible columns; every row uses integer zoom/column/row and a
non-null BLOB. Logical `(zoom,x,tmsY)` coordinates are unique even when no database index declares
that fact.

A caller selects one existing zoom in `0..22`. TMS rows convert exactly to north-origin XYZ with
`xyzY = (1L << zoom) - 1 - tmsY`; every coordinate is range checked before conversion. The source
extent is the smallest tile-aligned rectangle containing actual rows at that zoom, placed against the
canonical EPSG:3857 world from `CrsRegistry.level1()`. Optional WGS 84 `bounds` and `center` are
validated and retained as descriptive metadata but do not distort tile placement. Missing positions
inside the rectangle are transparent with one coalesced warning per read. An empty selected zoom is a
parameter/profile failure, not a zero-sized source.

Tile reads use one range statement ordered by XYZ row then column, reject duplicates deterministically,
decode exact 256-by-256 images through the explicit G6 registry, and use the same disabled-by-default
transactional decoded LRU as GeoPackage. A `MapView` renders only detached raster-read pixels; JDBC and
native types never cross the source boundary. No vector-tile parser, tile writer/server, TMS public
mode, remote fetch, multiple tilesets, UTFGrid, attribution renderer, or implicit zoom selection is
included.

### Limits, diagnostics, and evidence

`GeoPackageLimits` and `MbTilesLimits` are separate immutable typed values. They repeat the small
common SQLite fields rather than publishing a generic SQL limit contract. Defaults and hard maxima
are inclusive:

| Ceiling | Default | Hard maximum |
| --- | ---: | ---: |
| Input file bytes | 1,073,741,824 | 17,179,869,184 |
| Schema objects inspected | 512 | 4,096 |
| Columns in one selected table | 128 | 512 |
| One identifier UTF-16 characters | 256 | 256 |
| Metadata/core rows inspected | 4,096 | 65,536 |
| One decoded text value characters | 1,048,576 | 16,777,216 |
| Aggregate decoded text characters per operation | 4,194,304 | 67,108,864 |
| One geometry/tile/attribute BLOB bytes | 33,554,432 | 268,435,456 |
| Rows examined per operation | 2,000,000 | 100,000,000 |
| SQLite virtual-machine opcodes per operation | 50,000,000 | 500,000,000 |
| Project-owned bytes per operation | 536,870,912 | 2,147,483,647 |
| Zoom levels | 23 | 23 |
| Zoom | 22 | 22 |
| Matrix/populated tiles on either axis | 4,194,304 | 8,388,607 |
| Coordinates in one geometry | 1,000,000 | 10,000,000 |
| Parts/rings in one geometry | 100,000 | 1,000,000 |
| Decoded tile-cache entries | 256 | 4,096 |
| Decoded tile-cache RGBA bytes | 67,108,864 | 536,870,912 |

Disabled cache uses no zero sentinel; it is a distinct policy value. Enabled entry/byte ceilings are
positive and mutually reachable. Text aggregate is at least one text value, project-owned bytes are
at least two maximum BLOBs plus one decoded tile and the configured G4 output/intermediate allowance,
schema rows cover required core objects, zoom-level count is reachable under `0..22`, and every
product/sum uses prospective checked `long`. Matrix width/height multiplied by 256 must fit a positive
Java `int`, so the hard tile-axis ceiling is `floor(Integer.MAX_VALUE / 256)`. G4 feature/raster limits
independently bound returned records, source pixels, output pixels, warnings, and consumer-owned
payload.

The closed shared adapter diagnostics are:

| Code | Exact context | Meaning |
| --- | --- | --- |
| `SQLITE_ADAPTER_UNAVAILABLE` | `reason=unsupportedPlatform|nativeLoad|temporaryDirectory` | The approved native runtime cannot start. |
| `SQLITE_INPUT_INVALID` | `reason=path|type|sidecar|header|pageLayout` | The local container preflight failed. |
| `SQLITE_INPUT_CHANGED` | `phase=inspect|cursor|read|publish` | The immutable-input fingerprint changed. |
| `SQLITE_OPEN_FAILED` | `phase=load|connect|policy` | A bounded connection phase failed. |
| `SQLITE_QUERY_FAILED` | `operation=catalog|feature|tile|metadata`, `reason=corrupt|interrupt|io|other` | Fixed read SQL failed after translation. |

Format diagnostics are closed here:

| Code | Exact context |
| --- | --- |
| `GEOPACKAGE_PROFILE_UNSUPPORTED` | `construct=suffix|applicationId|version|extension|contentType|geometryType|dimension|tileFormat|tileSize|zoom` |
| `GEOPACKAGE_SCHEMA_INVALID` | `object=spatialRefSys|contents|geometryColumns|tileMatrixSet|tileMatrix|selectedTable`, `field`, `reason=missing|duplicate|type|nullability|constraint|reference|value|view` |
| `GEOPACKAGE_RECORD_INVALID` | `field=id|geometry|attribute`, `reason=null|storageClass|encoding|range|value` |
| `GEOPACKAGE_GEOMETRY_EMPTY` | `geometryType=point|multipoint|line|multiline|polygon|multipolygon` |
| `GEOPACKAGE_TILE_INVALID` | `field=zoom|x|y|data`, `reason=duplicate|range|null|format|size|decode`; `imageCode` is required only for `reason=decode` |
| `GEOPACKAGE_TILE_MISSING` | `zoom`, `count` |
| `MBTILES_PROFILE_UNSUPPORTED` | `construct=applicationId|view|extension|format|vector|grid|object|zoom` |
| `MBTILES_SCHEMA_INVALID` | `object=metadata|tiles`, `field`, `reason=missing|duplicate|type|nullability|constraint|value|view` |
| `MBTILES_METADATA_INVALID` | `field=name|format|bounds|center|minzoom|maxzoom|type|version|description|attribution`, `reason=missing|duplicate|encoding|syntax|range|order|value` |
| `MBTILES_TILE_INVALID` | `field=zoom|x|y|data`, `reason=duplicate|range|null|format|size|decode`; `imageCode` is required only for `reason=decode` |
| `MBTILES_TILE_MISSING` | `zoom`, `count` |
| `MBTILES_METADATA_IGNORED` | `count` |

Schema-role fields are exactly:

```text
spatialRefSys  -> srsId | name | organization | organizationCode | definition
contents       -> tableName | dataType | identifier | description | lastChange |
                  minX | minY | maxX | maxY | srsId
geometryColumns -> tableName | columnName | geometryType | srsId | z | m
tileMatrixSet  -> tableName | srsId | minX | minY | maxX | maxY
tileMatrix     -> tableName | zoom | matrixWidth | matrixHeight | tileWidth | tileHeight |
                  pixelXSize | pixelYSize
selectedTable  -> kind | primaryKey | geometry | columns | rowOrder
metadata       -> name | value
tiles          -> zoom | x | y | data
```

Every `field` value in a schema or tile diagnostic is therefore a closed schema-role token, never an
input identifier. A required `imageCode` is the exact code from G6's closed image-diagnostic table;
it is a flat context token rather than a nested cause and no nested context or message is retained.
Locations use
`component=geopackage|mbtiles`, public content-table ordinal, physical row ordinal, and optional zero-
based tile/column/part index. They never expose a filesystem path, URI, SQL, identifier text, metadata
value, raw BLOB, native path, driver class/message, or SQLite error text.

Shared limits use `SOURCE_LIMIT_EXCEEDED` with
`scope=sqliteOpen|sqliteQuery|geopackageOpen|geopackageCursor|geopackageRaster|mbtilesOpen|mbtilesRaster`
and `limit=inputBytes|schemaObjects|columns|identifierCharacters|metadataRows|textValueCharacters|
textCharacters|blobBytes|rows|vmOpcodes|ownedBytes|zoomLevels|zoom|matrixAxis|coordinates|parts|
cacheEntries|cacheBytes`. `SOURCE_CANCELLED` and `SOURCE_CLOSE_FAILED` retain their G4 shapes.

Opening precedence is public arguments, already-cancelled token, platform, path/file/header preflight,
native load/connection policy, core schema, selected profile/schema, CRS/matrix/metadata, operation
allocation, final fingerprint/cancellation, and publication. Cursor/read precedence is lifecycle,
fingerprint, plan/limits, fixed query, rows in declared order, BLOB decoding, output accounting, final
fingerprint/cancellation, cache commit, and publication. A native corrupt/I/O result already
established before the next cancellation checkpoint remains primary.

Future tests use small checked-in, legally redistributable databases with manifests, generator source,
tool/driver versions, licenses, and SHA-256 values plus independently generated real-producer samples
where redistribution is explicit. They cover both byte orders in geometry BLOBs, all six geometry
types, attributes, both recognized and retained-unknown CRSs, sparse PNG/JPEG tiles, GeoPackage matrix
math, MBTiles TMS conversion, tolerant rendering, exact/one-over limits, malformed schemas/geometry/
images, corrupt/truncated databases, URI/path/sidecar canaries, mutation, cancellation through the
progress handler, lifecycle/cleanup, cache rollback/LRU, and deterministic diagnostics. Architecture
tests prove external-type isolation, direct construction, exact classifier graph, no AWT, the two
I/O-codec allowlist edges, and absence of project discovery/native-loading calls.

Each created module joins publication staging and the standalone consumer with its exact classified
runtime dependencies, artifact/license/checksum verification, and a Java 21 Linux x86-64/glibc JVM
scenario. The project repository contains only MundaneJ artifacts; G8's post-Level-1 Optional-adapter
rule constructs a separate exact build-only mirror containing the approved Xerial POM, code classifier,
and Linux classifier. A fresh offline consumer resolves both adapter artifacts and exactly those two
classified runtime artifacts, rejects the ordinary/all-platform JAR and every other component, and
opens one staged fixture. Normal Ubuntu CI must run the real read/query/render tests; unsupported-
platform tests prove the stable unavailable diagnostic without loading JNI. No Native Image, new
corpus command, public network, benchmark threshold, or Level 1 release record is changed.

After G10-004 and the global G11-004 adapter approval, create five working cards:

1. `G10-040` — pin/classify Xerial, create `mundane-map-io-geopackage-xerial`, enforce the complete
   connection policy, and deliver catalog plus Point/MultiPoint feature query/render, publication, and
   staged-consumer behavior.
2. `G10-041` — complete line/polygon multipart geometry, attributes, CRS handling, query projection,
   viewer behavior, and feature hostile-input coverage.
3. `G10-042` — deliver GeoPackage PNG/JPEG tile matrices, sparse reads, bounded decoded cache,
   tolerant rendering, independent fixtures, and complete container hardening.
4. `G10-043` — create `mundane-map-io-mbtiles-xerial` with metadata, TMS conversion, PNG/JPEG sparse
   raster reads, viewer, publication, and staged-consumer behavior.
5. `G10-044` — close MBTiles limits/diagnostics/cancellation/cache/mutation/corrupt-database cases,
   add independent fixtures, and record the exact Linux JVM support evidence for both adapters.

G10-041 and G10-042 are serial after G10-040. G10-043 depends on G10-042 so its MBTiles source reuses
the already working byte-array decoder rather than racing or duplicating that shared G6 change.
G10-044 follows G10-043. The two format branches are logically independent after the shared decoder,
but the task graph also serializes dependency verification, settings/inventory, publication, consumer,
task-index, and roadmap changes under one integration owner. No module is created by the profile
decision.
