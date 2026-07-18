# G10 — Additional formats, tiles, and projections design

Project index: [DESIGN.md](../DESIGN.md).

## Secure SVG import profile and first slice (G10-001)

### Approval and implementation record

The maintainer approved every dependency-free HITL task in scope on 2026-07-17, including the named
**G10 secure SVG profile approval** checkpoint. The approval covers the supported/rejected tables,
four-cubic circle approximation, view-box and paint/stroke representability rules, secure StAX
controls, limits, diagnostics, literal native scenario, and publication/consumer extension below.

Implementation retains the profile's stroke-only open geometry honestly. `VectorMarkerSymbol` now
accepts an open path only when its fill is fully transparent and a stroke is present; filled and
fill-only marker paths still require every subpath to close. This is the narrow owning-API change
needed for supported SVG `line`, `polyline`, and open `path` leaves and does not widen filled marker
behavior.

### Profile approval and module boundary

G10-001 imports marker artwork, not feature geometry, documents, maps, or renderer plugins. The named
HITL checkpoint **G10 secure SVG profile approval** must approve the tables, limits, diagnostics, and
representability rules below before a module is created. A rejected or materially changed profile
returns to design review; implementation does not quietly widen it.

The working import slice creates published `mundane-map-io-svg` only when secure parse-to-render
behavior and tests land together. At G10-001 it is AWT-free and depends only on `mundane-map-api`
plus JDK module `java.xml`. G11-005 later approves adding exactly `mundane-map-core` when canonical
map export first reuses its symbol-transform, endpoint-tangent, and hatch-layout algorithms; that
approved edge does not weaken the AWT-free boundary or exist before working export behavior. The
module uses no Java2D, external XML library, DOM, XPath, Transformer, URL/network API, provider
lookup, reflection, service discovery, or mutable global parser. XML and SVG types do not enter
`mundane-map-api`.

The complete G10-001 import surface is two final utility/value types:

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

G11-005 separately approves four export-only types in this same artifact—`SvgMapExports`,
`SvgExportLimits`, `SvgExportException`, and `SvgExportProblem`—and one API-owned detached
`VectorExportSnapshot` family. They add no shared SVG document model and do not widen this import
grammar.

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
does not compare a whole image across platforms. At G10-001, architecture tests prove the module has
only API and `java.xml`, is AWT/network/discovery-free, and contains no DOM/XPath/Transformer or
prohibited native mechanism. After G11-040, the exact allowlist becomes API plus core and `java.xml`;
tests still reject every other module and mechanism.

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

On 2026-07-13 the approved resolvable dependency is `org.xerial:sqlite-jdbc:3.53.2.0`, released on
2026-06-04 and present as the current split release in Maven Central. The exact artifacts, independently
downloaded and hashed during G11-004 design review, are:

| Artifact | SHA-256 | Use |
| --- | --- | --- |
| `sqlite-jdbc-3.53.2.0-without-natives.jar` | `ccfd2bc6b289be6ed599b92c6036610cb73fe64c43ffd7b54f46b6e412afc34d` | Private JDBC implementation classes |
| `sqlite-jdbc-3.53.2.0-natives-linux.jar` | `1c25f9fa2c0cb8e0af6eba32121fa71823c9ebcc9ba83024b1955e11e43e99f5` | Upstream Linux SQLite JNI binaries |
| `sqlite-jdbc-3.53.2.0.pom` | `63858f3bb9c9161cc41f848b9df59a58d5811b4a13b0663470b7aae655466c5e` | Dependency and license provenance |

The ordinary `3.53.2.0` all-platform JAR, SHA-256
`dc320e4102884c135ccc30c3c6fc3fb190b750e1586a100e3aba3be783cf33a9`, is rejected. The Linux
classifier still contains upstream binaries for several Linux architectures and libc variants because
Xerial publishes no narrower classifier. A private platform preflight runs before connection/native
initialization: `os.name` must be exactly `Linux`, `os.arch` must be `amd64` or `x86_64`, and Xerial's
`OSInfo.isMusl()` must be false; every other result is `SQLITE_ADAPTER_UNAVAILABLE` with
`reason=unsupportedPlatform`. The first support claim is a Java 21 JVM on that x86-64 Linux path with
glibc 2.35 or newer. Pinned Ubuntu 22.04/glibc 2.35 is the minimum evidence lane and Ubuntu
24.04/glibc 2.39 is the second lane; each records the exact image, JDK, kernel, and `getconf` result.
The pinned glibc native's symbol table requires at most `GLIBC_2.3`, but that is artifact inventory,
not a project support claim; glibc below 2.35 remains unverified. The unused classifier entries are
inventoried, not repackaged into a MundaneJ JAR, and do not widen support. macOS, Windows, musl, other
architectures, Android, a system-SQLite mode, and caller-supplied native libraries require separate
packaging and evidence decisions.

The [upstream release](https://github.com/xerial/sqlite-jdbc/releases/tag/3.53.2.0) and
[Maven Central directory](https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.2.0/)
are the recorded provenance. The implementation task must recheck Maven Central presence, release
signatures, current security advisories, the exact resolved runtime graph, and all checksums without
silently changing versions; a version change amends this decision first.
It records Xerial's Apache-2.0 terms, the retained Zentus BSD notice, SQLite's public-domain status,
any bundled notice obligations, and the pinned SQLite compile options required by GeoPackage 1.4
Requirement 9. No version range, rich latest selector, local Maven fallback, or unverified GitHub-
release binary is accepted.

The code classifier physically contains `META-INF/services/java.sql.Driver`, Native Image feature
metadata, resource/URL loading, JNI declarations, native extraction, and process-global loader state.
Direct connection startup also initializes Xerial `LoggerFactory`, whose third-party bytecode calls
`Class.forName("org.slf4j.Logger")`; the exact resolved graph deliberately omits SLF4J, so the caught
absence selects Xerial's JDK logger in every supported test/consumer. Xerial `OSInfo.isMusl()` lists
`/proc/self/map_files` and may read `/etc/os-release`; the external loader's OS translation also runs
`uname -o` and tests `/system/lib/libGLESv1_CM.so` and `/system/lib64/libGLESv1_CM.so` before selecting
the ordinary Linux branch. These
reflection, host-file, resource, JNI, load, and dormant process mechanisms are explicit
external-artifact exceptions, not undiscovered project behavior.

Project bytecode constructs pinned `org.sqlite.jdbc4.JDBC4Connection` directly with private fixed
properties and does not call `DriverManager`, `ServiceLoader`, `SQLiteDataSource`, `Class.forName`,
reflection, resource lookup, process execution, or native loading. The external loader extracts and
loads its selected JNI library and therefore needs a writable temporary directory. These bounded
third-party mechanisms are accepted only inside the Optional adapters under G0 qualification. The
service descriptor and Native Image metadata are not copied into a MundaneJ artifact or explicit
native configuration. External-artifact verification scans the exact descriptor/resource tree,
native entries, symbolic calls/strings for reflection, fixed host paths, process execution, URL/
resource access, temporary properties, and load APIs; the supported-path test expects the fixed probes
above and no other process/file target. Any addition or changed control-path inventory
requires design review rather than an allow-all dependency exemption.

These adapters are JVM-only and have Native Image policy `not-targeted`. Xerial's own reachability
metadata is not a project compatibility claim. Neither adapter enters the shared native executable,
and the Linux JVM evidence below cannot be described as Native Image evidence. Any later claim needs
a new HITL packaging task that proves the exact native library, extraction/static-link policy,
reachability, cleanup, and format behavior without weakening the Level 1 rules.

Embedded database BLOBs establish the first real non-file consumers of G6's complete image profile;
G10-006 adds HTTP response bodies as a third. Shared working card G10-039 therefore adds one
toolkit-neutral synchronous helper to `mundane-map-io-image`, and every tile adapter reuses it:

```text
RasterImages.decode(byte[] encodedBytes, SourceIdentity identity,
                    EncodedRasterDecodeOptions options,
                    EncodedRasterDecoderRegistry decoders,
                    CancellationToken cancellation) -> RgbaPixelBuffer

EncodedRasterDecodeOptions(
    Optional<EncodedRasterFormat> expectedFormat,
    OptionalInt expectedWidth,
    OptionalInt expectedHeight,
    ImageSourceLimits imageLimits,
    RasterRequestLimits decodeLimits)
  defaults()
  expecting(EncodedRasterFormat)
  expectingDimensions(int width, int height)
  withImageLimits(...)
  withDecodeLimits(...)
```

The helper checks cancellation/encoded size, defensively copies the caller array, applies the same
complete PNG/JPEG header/container validation and explicit decoder registry as the file source, and
decodes exactly the full native-size image into one independently owned buffer. An absent expected
format trusts the signature; a present value must match it. Expected width and height are either both
absent or both positive and within the supplied image/request limits. After structural header parsing
but before any complete-decode allocation, a mismatch produces `IMAGE_DIMENSIONS_MISMATCH` with exact
numeric `expectedWidth`, `expectedHeight`, `width`, and `height`. It has no suffix, channel, placement,
world file, cache, source lifecycle, AWT type, or decoder discovery. Accounting includes the caller-
array copy, validation state, opaque decoder reservation, and returned RGBA buffer. For encoded length
`E` and validated native pixel count `P`, its complete primitive-payload charge is exactly
`2 * E + 16 * P`: one defensive encoded copy plus G6's `E + 12 * P` intermediate reservation and
`4 * P` published buffer. Fixed validation/container reference slots are charged separately under G4's
table. The adapter wraps an image diagnostic once with only its stable code; raw BLOBs and nested
messages remain private.

This focused helper is smaller and safer than constructing an unplaced temporary `RasterSource` for
every tile or making private G6 parsers public.

The byte helper's checked failure set is closed. It emits `SOURCE_CANCELLED` unchanged;
`SOURCE_LIMIT_EXCEEDED` with `scope=imageDecode`; `IMAGE_DIMENSIONS_MISMATCH`; or one of
`IMAGE_EXPECTED_FORMAT_MISMATCH`, `IMAGE_HEADER_INVALID`, `IMAGE_CONTAINER_INVALID`,
`IMAGE_PROFILE_UNSUPPORTED`, `IMAGE_DECODER_NOT_REGISTERED`, `IMAGE_DECODE_FAILED`,
`IMAGE_DECODE_MISMATCH`, and `IMAGE_IO_FAILED`. It cannot emit file-mutation, world-file, cache,
interpolation, or source-close outcomes because it owns none of those mechanisms. A custom
`CancellationToken` failure and every other unexpected `RuntimeException`/`Error` propagate unchanged
after helper cleanup. A decoder attempting another checked code is a decoder-contract
`IllegalStateException`, not an extensible diagnostic channel.

The two byte-helper-specific codes do not reuse file-only context:

| Code | Component and exact context |
| --- | --- |
| `IMAGE_EXPECTED_FORMAT_MISMATCH` | `image`; `expectedFormat=PNG|JPEG`, `signature=PNG|JPEG|unknown` |
| `IMAGE_DIMENSIONS_MISMATCH` | `image`; numeric `expectedWidth`, `expectedHeight`, `width`, `height` |

The existing file opener keeps `IMAGE_FORMAT_MISMATCH/extension/signature` unchanged; a detached byte
array never fabricates an extension.

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

GeoPackage calls G10-039 with absent expected format because its profile deliberately permits each
tile BLOB to be independently PNG or JPEG; signature selection is therefore meaningful and no
homogeneity metadata is invented. MBTiles supplies its normalized tileset metadata format. Both supply
exact expected dimensions 256 by 256. Helper `SOURCE_CANCELLED` remains the ordinary G4 cancellation.
Helper `SOURCE_LIMIT_EXCEEDED` maps to the adapter's `geopackageRaster` or `mbtilesRaster` scope with
`limit=imageDecode`, preserving only numeric `requested` and `maximum`. `IMAGE_DIMENSIONS_MISMATCH`
maps to the format tile diagnostic `field=data/reason=size` with its four safe expected/actual numeric
dimensions. Each of the eight other closed helper image codes maps to `field=data/reason=decode` with
only `imageCode`. Unexpected token/decoder runtime failures propagate after rollback, and any other
checked decoder code is a contract `IllegalStateException`.

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
| `GEOPACKAGE_TILE_INVALID` | `field=zoom|x|y|data`, `reason=duplicate|range|null|format|size|decode`; `reason=decode` requires `imageCode`; `field=data/reason=size` requires numeric `expectedWidth`, `expectedHeight`, `width`, `height` |
| `GEOPACKAGE_TILE_MISSING` | `zoom`, `count` |
| `MBTILES_PROFILE_UNSUPPORTED` | `construct=applicationId|view|extension|format|vector|grid|object|zoom` |
| `MBTILES_SCHEMA_INVALID` | `object=metadata|tiles`, `field`, `reason=missing|duplicate|type|nullability|constraint|value|view` |
| `MBTILES_METADATA_INVALID` | `field=name|format|bounds|center|minzoom|maxzoom|type|version|description|attribution`, `reason=missing|duplicate|encoding|syntax|range|order|value` |
| `MBTILES_TILE_INVALID` | `field=zoom|x|y|data`, `reason=duplicate|range|null|format|size|decode`; `reason=decode` requires `imageCode`; `field=data/reason=size` requires numeric `expectedWidth`, `expectedHeight`, `width`, `height` |
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
input identifier. A required `imageCode` is one of G10-039's eight closed non-dimension image codes; it
is a flat context token rather than a nested cause and no nested context or message is retained.
Locations use
`component=geopackage|mbtiles`, public content-table ordinal, physical row ordinal, and optional zero-
based tile/column/part index. They never expose a filesystem path, URI, SQL, identifier text, metadata
value, raw BLOB, native path, driver class/message, or SQLite error text.

Shared limits use `SOURCE_LIMIT_EXCEEDED` with
`scope=sqliteOpen|sqliteQuery|geopackageOpen|geopackageCursor|geopackageRaster|mbtilesOpen|mbtilesRaster`
and `limit=inputBytes|schemaObjects|columns|identifierCharacters|metadataRows|textValueCharacters|
textCharacters|blobBytes|rows|vmOpcodes|ownedBytes|zoomLevels|zoom|matrixAxis|coordinates|parts|
imageDecode|cacheEntries|cacheBytes`. `imageDecode` preserves only the helper limit's numeric
`requested` and `maximum`; `SOURCE_CANCELLED` and `SOURCE_CLOSE_FAILED` retain their G4 shapes.

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
tests prove external-type isolation, direct construction, the exact classifier graph with no SLF4J,
no AWT, the two I/O-codec allowlist edges, absence of project discovery/native-loading calls, and the
closed third-party reflection/host/resource/process/JNI inventory above.

Each created module joins publication staging and the standalone consumer with its exact classified
runtime dependencies, artifact/license/checksum verification, and the pinned Java 21 Ubuntu
22.04/glibc 2.35 and Ubuntu 24.04/glibc 2.39 JVM scenarios. The project repository contains only
MundaneJ artifacts; G8's post-Level-1 Optional-adapter
rule constructs a separate exact build-only mirror containing the approved Xerial POM, code classifier,
and Linux classifier. A fresh offline consumer resolves both adapter artifacts and exactly those two
classified runtime artifacts, rejects the ordinary/all-platform JAR and every other component, and
opens one staged fixture. The two pinned Ubuntu jobs run real read/query/render tests; a pinned
Alpine/musl negative job and non-Linux/non-x86 fresh JVMs prove `unsupportedPlatform` before loading
JNI. Because Xerial's loader state is process-global, each deployment negative uses a separate JVM:
property-controlled non-Linux/non-x86, pinned Alpine for musl, code-classifier-only for `nativeLoad`,
and the exact graph with `org.sqlite.tmpdir` pointing to a controlled non-directory/unwritable fixture
for `temporaryDirectory`. A subsequent success runs in another clean JVM with the exact graph and a
writable private temporary directory. These are test-only process/classpath controls, not a production
loader seam or supported incomplete dependency graph. No Native Image, new
corpus command, public network, benchmark threshold, or Level 1 release record is changed.

After G10-004, create one shared working prerequisite; after the global G11-004 adapter approval,
create five SQLite-format cards:

1. `G10-039` — add the exact G6 encoded-byte helper with PNG/JPEG success, mismatch, malformed,
   cancellation, and limit tests; create no module and publish no format behavior.
2. `G10-040` — pin/classify Xerial, create `mundane-map-io-geopackage-xerial`, enforce the complete
   connection policy, and deliver catalog plus Point/MultiPoint feature query/render, publication, and
   staged-consumer behavior.
3. `G10-041` — complete line/polygon multipart geometry, attributes, CRS handling, query projection,
   viewer behavior, and feature hostile-input coverage.
4. `G10-042` — deliver GeoPackage PNG/JPEG tile matrices, sparse reads, bounded decoded cache,
   tolerant rendering, independent fixtures, and complete container hardening.
5. `G10-043` — create `mundane-map-io-mbtiles-xerial` with metadata, TMS conversion, PNG/JPEG sparse
   raster reads, viewer, publication, and staged-consumer behavior.
6. `G10-044` — close MBTiles limits/diagnostics/cancellation/cache/mutation/corrupt-database cases,
   add independent fixtures, and record the exact Linux JVM support evidence for both adapters.

The exact graph is: G10-039 follows G10-004; G10-040 follows both G10-004 and G11-004; G10-041 follows
G10-040; G10-042 follows G10-041 and G10-039; G10-043 follows G10-039 and G11-004; and G10-044 follows
G10-042 and G10-043. G10-039 is also a prerequisite of G10-060. The format branches are logically
independent after the shared decoder, but dependency
verification, settings/inventory, publication, consumer, task-index, and roadmap changes remain under
one integration owner. No module is created by the profile decision or shared helper card.

## GPX and KML source profiles (G10-005)

### Approval and independent module boundaries

The named checkpoint is **G10 GPX/KML source profile approval**. It independently approves the two
format matrices, their warned omissions, the common security posture, and the later task graph.
G10-005 creates no production module or parser.

The normative inputs are the
[Topografix GPX 1.1 schema](https://www.topografix.com/GPX/1/1/) and
[OGC KML 2.2 standard](https://docs.ogc.org/is/07-147r2/07-147r2.html). The approved modules are:

```text
mundane-map-io-gpx -> mundane-map-api + selected mundane-map-core source/CRS algorithms
mundane-map-io-kml -> mundane-map-api + selected mundane-map-core source/CRS algorithms
```

Both are published Level 2 JDK-only, AWT-free modules. They share no production dependency, parser
facade, XML event model, public base class, extension registry, or combined artifact. Each has a small
private format state machine over the same directly constructed JDK StAX API. This deliberate
duplication is less policy than a generic XML module and prevents GPX extension behavior from becoming
KML behavior accidentally. Public signatures contain only JDK and MundaneJ values.

The eventual surfaces are intentionally parallel but separate:

```text
GpxFiles.open(Path path, SourceIdentity identity,
              GpxOpenOptions options, CancellationToken cancellation) -> FeatureSource

KmlFiles.open(Path path, SourceIdentity identity,
              KmlOpenOptions options, CancellationToken cancellation) -> FeatureSource

GpxOpenOptions(GpxLimits formatLimits, FeatureSourceLimits queryLimits)
KmlOpenOptions(KmlLimits formatLimits, FeatureSourceLimits queryLimits)
```

Each options value is immutable, has `defaults()` plus complete withers, and captures every effective
limit at invocation. There is no public stream/reader/DOM/event overload, URL opener, registry,
schema object, parser selection, style option, coordinate override, lazy flag, or format sniffing.
Package-private byte-array seams exist only for deterministic parser tests; the public path remains
one local-file transaction with unambiguous ownership.

### One bounded UTF-8 and JDK StAX boundary

An open resolves the caller path to a normalized absolute path and, using `NOFOLLOW_LINKS`, requires
one nonempty readable regular file that is not a symbolic link. It snapshots initial basic attributes,
checks the format input ceiling, opens one channel, reads exactly the captured size into one array,
probes one additional byte without allocating `maximum + 1`, closes the channel, and compares size,
last-modified time, and a file key when the provider supplies one. Any mismatch fails the transaction.
No path, channel, or filesystem identity survives successful opening.

The byte snapshot accepts an optional leading UTF-8 BOM and otherwise requires strict shortest-form
UTF-8 with valid Unicode scalar values. UTF-16/UTF-32 BOMs, malformed/overlong UTF-8, isolated
surrogates, characters excluded by XML 1.0, and an XML declaration naming anything except
case-insensitive `UTF-8` are outside the profile. Absence of an encoding declaration means UTF-8.
The implementation validates bytes incrementally without constructing a second full-file string;
StAX performs the later character decoding from the same owned snapshot. XML 1.0 is accepted and XML
1.1 is rejected. The optional BOM records one bounded format-specific warning.

Each module creates its parser with `XMLInputFactory.newDefaultFactory()`, never `newFactory()` or a
named/internal implementation. Before creating a reader it sets and reads back this exact policy:

```text
XMLInputFactory.IS_NAMESPACE_AWARE = true
XMLInputFactory.IS_COALESCING = false
XMLInputFactory.IS_VALIDATING = false
XMLInputFactory.SUPPORT_DTD = false
XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES = false
XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES = false
XMLConstants.ACCESS_EXTERNAL_DTD = ""
XMLConstants.USE_CATALOG = false
```

It also installs an `XMLResolver` that always throws a constant project-owned exception and an
`XMLReporter` that turns any parser report into a constant invalid-XML result. Unsupported or
ineffective property configuration is an internal `IllegalStateException` before input I/O; it never
falls back to weaker settings. No schema is loaded. `xsi:schemaLocation` is inert bounded text, and
XInclude, DTD declarations, entity references, processing that resolves a URI, and external subsets
never run. The implementation references no internal JDK parser class, service loader, reflection,
classpath scan, or resource lookup.

Start/end document, start/end element, comments, processing instructions, DTD, and entity-reference
events count as structural XML events. Parser-dependent characters/CDATA chunk boundaries do not;
their decoded characters are charged as the project state machine combines adjacent chunks into one
bounded logical scalar. Namespace declarations and attributes have their own counters, and every
decoded name/value/comment/instruction contributes to scalar and aggregate-character ceilings.
Comments and processing instructions are ignored after counting. A DTD or entity-reference event is
terminal even if a future JDK implementation emits one despite the configured policy.
Element/attribute matching is by exact namespace URI plus local name, never prefix. Controlled byte,
character, coordinate, and structural-event loops poll cancellation at most every 4,096 primitive
units. Every opaque `hasNext()`/`next()` call is checked immediately before and after, and cancellation
is checked immediately before source publication.

This is explicit profile parsing, not general XML Schema validation. Each reader enforces the order,
cardinality, namespaces, and value rules named below and structurally counts every ignored subtree;
it makes no claim about unneeded portions of the full XSD beyond that closed profile. Raw XML,
namespace prefixes, locations supplied by the parser, element/attribute values, file paths, parser
classes, and exception messages never enter diagnostics.

### Eager immutable source and lifecycle

Opening completely parses the bounded snapshot into immutable `FeatureRecord` values and then drops
the input and parser before returning. A small private format source composes the existing core
`InMemoryFeatureSource` state machine for ordered queries and owns the format opening report; the two
modules duplicate that trivial wrapper rather than add a public report-decorator or shared XML source.
There is no live XML cursor, deferred parse error, background work, executor, cache, retained URI, or
close-time parser I/O.

The opening pass computes an exact exposed feature count, the union of emitted geometry envelopes, a
fixed format schema, recognized EPSG:4326 metadata, and warning report. Source and query order is
document order. Metadata and opening diagnostics survive close; close invalidates the source/live
cursor through the G4 state machine and releases the retained record snapshot. Already returned
immutable records remain valid. One-live-cursor, projection, tighter-limit, cancellation, failure,
and early/double/source-close behavior is exactly G4's in-memory conformance behavior.

The format open-time owned-byte counter charges the input snapshot, parser-owned project buffers,
pending values, warning accumulators, packed coordinates/part offsets, record/list/map slots, and all
retained strings/attribute payloads using G4's logical table before allocation or ownership transfer.
The JDK parser's object headers and transient implementation bookkeeping cannot be measured byte for
byte, but untrusted input, event, depth, attribute, namespace, scalar, aggregate-text, and retained
value ceilings constrain it. This qualification does not permit an unbounded token or subtree.
Query-time returned payload remains independently charged by G4.

Every documented skip produces one warning in encounter order, as required by G4. The report builder
allocates only the first configured number of immutable warning values; later warnings increment the
saturating `omittedWarningCount` directly without allocating a diagnostic. It never deduplicates or
coalesces equal warnings. Earlier retained warnings are materialized before a terminal error, so the
final report preserves encounter order and G4's warning/omission contract under hostile repetition.

### GPX 1.1 waypoint and track profile

`mundane-map-io-gpx` accepts only the exact root namespace
`http://www.topografix.com/GPX/1/1`, local name `gpx`, fixed `version="1.1"`, and one
nonblank bounded `creator`. The core root order is `metadata?`, `wpt*`, `rte*`, `trk*`, then
`extensions?`; duplicate/out-of-order content is invalid. A `rte` is a recognized unsupported route
even when empty. Root, metadata, waypoint, track, segment, and track-point extensions are structurally
skipped and each produces one bounded warning; their namespace-qualified contents are never
interpreted or preserved. A foreign element
outside an `extensions` subtree and an unknown element in the GPX namespace are terminal. Namespace
declarations and `xsi:schemaLocation` are allowed after counting; any other unapproved attribute is
terminal.

One top-level waypoint produces one point record. Each track segment with at least two track points
produces one line record; every empty or one-point segment is a valid-but-unrepresentable input skipped
with its own bounded warning. Track containers themselves do not emit a record. IDs are generated
independently of untrusted text and remain stable for the source lifetime:

```text
waypoint       -> gpx:wpt:<one-based waypoint ordinal>
track segment  -> gpx:trk:<one-based track ordinal>:seg:<one-based segment ordinal>
```

Every waypoint or segment is also assigned one positive physical feature-candidate ordinal in root
encounter order for diagnostics. A track-point error uses its segment's record number and a bounded
zero-based `pointIndex` context. Empty/skipped candidates still consume the physical ordinal and the
format feature-candidate ceiling but do not affect exposed count.

Waypoint and track-point `lat`/`lon` attributes are required exactly once, contain bounded ASCII
decimal tokens, convert to finite `double`, and lie in inclusive latitude `[-90,90]` and longitude
`[-180,180)`, matching GPX's exclusive upper longitude bound. Negative zero is canonicalized to
positive zero. Geometry uses the library x/y
visualization convention `(longitude, latitude)` and records the canonical recognized
`CrsRegistry.level1()` EPSG:4326 metadata. Source extent is computed from emitted coordinates; GPX
metadata bounds are neither trusted nor retained. Dateline-crossing segments remain literal under the
same non-wrapping rule approved for GeoJSON.

The fixed ordered schema is:

| Field | Type/nullability | Waypoint | Track segment |
| --- | --- | --- | --- |
| `gpxKind` | `TEXT`, non-null | `waypoint` | `trackSegment` |
| `trackIndex` | `INTEGER`, nullable | null | one-based track ordinal |
| `segmentIndex` | `INTEGER`, nullable | null | one-based segment ordinal |
| `elevationMetres` | `FLOATING`, nullable | parsed optional `ele` | null |
| `time` | `TEXT`, nullable | canonical optional timestamp | null |
| `comment` | `TEXT`, nullable | optional `cmt` | track `cmt` |
| `description` | `TEXT`, nullable | optional `desc` | track `desc` |
| `source` | `TEXT`, nullable | optional `src` | track `src` |
| `symbol` | `TEXT`, nullable | optional `sym` | null |
| `type` | `TEXT`, nullable | optional `type` | track `type` |
| `trackNumber` | `INTEGER`, nullable | null | optional non-negative `number` |

The GPX `name` becomes `FeatureRecord.name()` and is not duplicated as an attribute. Optional
elevation is a bounded decimal that must convert to a finite `double`; GPX metric units make the
attribute metres. Optional time is the strict RFC 3339 subset
`YYYY-MM-DDTHH:MM:SS[.fraction](Z|+HH:MM|-HH:MM)`, with one through nine digits when the fraction is
present; it must parse to an instant and is retained in canonical `Instant.toString()` UTC form.
Missing nullable fields use `AttributeNull` so every record satisfies the fixed schema.

The reader recognizes GPX's remaining standard scalar/link/quality children only well enough to
respect their parent and structure, counts their complete subtrees, and emits one field-ignored
warning per discarded field. It neither follows a link nor claims full validation of discarded metadata.
Track-point elevation, time, and other non-coordinate children are deliberately not projected onto a
line record because G4 has no per-vertex attribute-series contract; each present category records a
separate data-ignored warning. This visible loss is preferable to parallel arrays hidden in one
attribute, synthetic point records, or a GPX-specific public geometry. Routes, route points,
extension semantics, sensor series, arbitrary waypoint symbols, write-back, and elevation-aware
geometry remain later profiles.

### Static KML 2.2 geometry profile

`mundane-map-io-kml` accepts XML root `kml` in exact namespace
`http://www.opengis.net/kml/2.2` containing exactly one supported root feature: `Document`, `Folder`,
or `Placemark`. Documents and folders may nest those same three feature kinds to the logical feature-
nesting limit; every encountered `Document`, `Folder`, or leaf `Placemark` consumes one level, with the
root feature at level one. Placemarks may not nest features. Placemark preorder is physical/source
order, independent of folder names or IDs. `NetworkLinkControl`, `NetworkLink`, every overlay,
`Model`, `Tour`, `Update`,
`Region`, `TimeSpan`, `TimeStamp`, `Schema`, and any foreign-namespace feature/geometry are recognized
unsupported constructs rather than skipped.

Document, Folder, and Placemark `visibility` may be absent or exact true (`1` or `true`); false is
unsupported because silently rendering hidden content changes semantics. `open`, `LookAt`, `Camera`,
`Snippet`, address/contact fields, `Style`, `StyleMap`, `styleUrl`, and `ExtendedData` are presentation
or unmodeled data: their bounded subtrees are never interpreted, resolved, or exposed and produce
one warning per ignored construct. In particular an href or style URL causes no I/O. KML styles do not
become G2 symbols; G4's source-backed layer continues to receive explicit caller-owned marker, line,
and fill symbols. General KML styling and label placement remain G11-002 work, not an implicit parser
side channel.

One Placemark with exactly one direct supported geometry emits one record. A Placemark with no
geometry is fully counted/validated then skipped with a warning; more than one geometry is invalid.
The exact geometry mapping is:

| KML geometry | Required profile | MundaneJ geometry |
| --- | --- | --- |
| `Point` | exactly one coordinate tuple | `PointGeometry` |
| `LineString` | at least two tuples | `LineStringGeometry` |
| `Polygon` | one outer ring and zero or more inner rings | `PolygonGeometry` |
| homogeneous `MultiGeometry` of Points | one or more direct Points | `MultiPointGeometry` |
| homogeneous `MultiGeometry` of LineStrings | one or more direct LineStrings | `MultiLineStringGeometry` |
| homogeneous `MultiGeometry` of Polygons | one or more direct Polygons | `MultiPolygonGeometry` |

Nested, empty, or mixed `MultiGeometry`, standalone `LinearRing`, and every other geometry are
unsupported. Polygon rings contain at least four tuples and have exact first/last x/y closure; the
first boundary is exterior and later boundaries are holes. Component/ring order is preserved.
Orientation, containment, self-intersection, topology repair, antimeridian splitting, terrain
tessellation, and ring normalization are not performed.

`coordinates` is a bounded whitespace-separated list of comma-separated `longitude,latitude` or
`longitude,latitude,altitude` tuples. Each ordinate uses GPX's bounded finite ASCII decimal grammar,
but KML longitude is inclusive `[-180,180]` while latitude remains inclusive `[-90,90]`; negative
zero is canonicalized. Geometry `altitudeMode` must be
absent or exact `clampToGround`, while `extrude` and `tessellate` must be absent or false (`0` or
`false`). Other modes, `gx:altitudeMode`, or true extrusion/tessellation are unsupported. A finite
third ordinate under clamp-to-ground is counted and discarded with its own bounded altitude warning;
altitude rendering/storage is not invented. Output normalizes `(longitude, latitude)` to canonical
recognized EPSG:4326 and follows the literal non-wrapping dateline rule.

Record IDs are `kml:placemark:<one-based Placemark preorder ordinal>`. A bounded nonblank KML `id`, if
present, is data rather than source identity; no style/update references are resolved. The fixed schema
is:

| Field | Type/nullability | Value |
| --- | --- | --- |
| `kmlId` | `TEXT`, nullable | exact Placemark `id` or null |
| `description` | `TEXT`, nullable | bounded character-only description or null |
| `geometryKind` | `TEXT`, non-null | `point`, `line`, `polygon`, `multipoint`, `multiline`, or `multipolygon` |

Placemark `name` becomes the record name. Description accepts text/CDATA and predefined XML character
references but no nested element/HTML interpretation. Source count and extent cover emitted
Placemarks only. Labels, balloon HTML, shared-style resolution, visibility inheritance beyond the
approved true-only value, temporal filtering, regionation, network refresh, overlays, models, KMZ,
`gx` extensions, ExtendedData mapping, and 3D output require later profile decisions.

### Typed limits and deterministic diagnostics

`GpxLimits` and `KmlLimits` are separate immutable values. They repeat the small common fields rather
than expose a generic XML-security API; `KmlLimits` alone adds logical feature nesting depth. Defaults and
hard maxima are inclusive:

| Open-time ceiling | Default | Hard maximum |
| --- | ---: | ---: |
| Encoded input bytes | 16,777,216 | 268,435,456 |
| XML nesting depth, root = 1 | 64 | 128 |
| XML structural events, excluding text chunks | 4,000,000 | 32,000,000 |
| Elements | 1,000,000 | 8,000,000 |
| Attributes | 1,000,000 | 8,000,000 |
| Namespace declarations | 65,536 | 1,048,576 |
| Logical KML feature nesting depth, root feature = 1 | 32 | 64 |
| Physical feature candidates | 100,000 | 1,000,000 |
| Total coordinate positions | 2,000,000 | 16,000,000 |
| Positions in one geometry or GPX segment | 1,000,000 | 16,000,000 |
| Parts/rings/MultiGeometry components | 250,000 | 2,000,000 |
| Characters in one scalar | 65,536 | 1,048,576 |
| Aggregate decoded scalar characters | 16,777,216 | 134,217,728 |
| Characters in one numeric token | 128 | 256 |
| Conservatively owned open-time bytes | 268,435,456 | 1,073,741,824 |
| Retained opening warnings | 256 | 4,096 |

All count/character/input fields are positive `int`; owned bytes is positive `long`. GPX has no
feature-depth accessor/token. Constructors and withers require total positions at least positions per
geometry, aggregate characters at least one scalar, elements at least physical candidates, and events
at least `2 * elements + 2`, using checked arithmetic. KML additionally requires XML depth at least
logical feature depth plus six, covering `<kml>` and the deepest supported
MultiGeometry/Polygon/boundary/ring/coordinates chain so both ceilings remain independently
reachable. Owned bytes must be at
least the checked sum of input bytes, 16 bytes per total coordinate, four bytes per part fence, eight
bytes per candidate record slot, and two bytes per aggregate character. Input, structural, and query
limits remain independently tighten-able and may intentionally become the first effective ceiling.
Equality is accepted; maximum plus one and overflow fail before allocation/publication.

Counts include ignored and unsupported-to-be-diagnosed subtrees through the point of failure, empty or
skipped candidates, repeated ring closure, namespace/attribute content, pending warning categories,
and all simultaneous defensive copies. Elements/attributes/namespaces are charged prospectively on
their start event; decoded characters and numeric tokens are charged before appending; positions and
parts before packed allocation. The snapshot plus retained output peak is charged even though the
snapshot is dropped before source publication.

The closed GPX outcomes are:

| Code | Severity and exact context |
| --- | --- |
| `GPX_UTF8_BOM_IGNORED` | warning; empty context |
| `GPX_EXTENSION_IGNORED` | warning; `scope=root|metadata|waypoint|track|segment|trackPoint` |
| `GPX_FIELD_IGNORED` | warning; `scope=metadata|waypoint|track` |
| `GPX_TRACK_POINT_DATA_IGNORED` | warning; `field=elevation|time|other` |
| `GPX_TRACK_SEGMENT_SKIPPED` | warning; `reason=empty|singlePoint` |
| `GPX_IO_FAILED` | error; `operation=attributes|open|read|close`, `reason=notFound|accessDenied|changed|other` |
| `GPX_ENCODING_INVALID` | error; `reason=bom|utf8|xmlVersion|declaredEncoding` |
| `GPX_XML_INVALID` | error; `reason=syntax|doctype|entity|namespace|order|cardinality|trailingContent` |
| `GPX_PROFILE_UNSUPPORTED` | error; `construct=route|foreignElement|coreElement|attribute` |
| `GPX_VALUE_INVALID` | error; `field=creator|latitude|longitude|elevation|time|name|comment|description|source|symbol|type|trackNumber|coordinates`, `reason=missing|duplicate|syntax|range|nonFinite|length|cardinality|closure` |

The closed KML outcomes are:

| Code | Severity and exact context |
| --- | --- |
| `KML_UTF8_BOM_IGNORED` | warning; empty context |
| `KML_PRESENTATION_IGNORED` | warning; `construct=open|view|snippet|contact|style|styleUrl|extendedData` |
| `KML_ALTITUDE_IGNORED` | warning; empty context |
| `KML_PLACEMARK_SKIPPED` | warning; `reason=noGeometry` |
| `KML_IO_FAILED` | error; `operation=attributes|open|read|close`, `reason=notFound|accessDenied|changed|other` |
| `KML_ENCODING_INVALID` | error; `reason=bom|utf8|xmlVersion|declaredEncoding` |
| `KML_XML_INVALID` | error; `reason=syntax|doctype|entity|namespace|order|cardinality|trailingContent` |
| `KML_PROFILE_UNSUPPORTED` | error; `construct=network|overlay|model|tour|update|region|time|schema|foreignElement|visibility|geometry|multiGeometry|altitudeMode|extrude|tessellate|attribute` |
| `KML_VALUE_INVALID` | error; `field=id|name|description|coordinates|longitude|latitude|altitude|outerRing|innerRing`, `reason=missing|duplicate|syntax|range|nonFinite|length|cardinality|closure|nestedContent` |

`SOURCE_LIMIT_EXCEEDED` uses `scope=gpxOpen|kmlOpen` and exact limit tokens
`inputBytes|xmlDepth|xmlEvents|elements|attributes|namespaceDeclarations|featureDepth|features|
coordinates|geometryCoordinates|parts|scalarCharacters|textCharacters|numberCharacters|ownedBytes`;
`featureDepth` is KML-only. The retained-warning value caps the report and increments G4's omitted-
warning count rather than terminating with a limit diagnostic. G4 retains `SOURCE_CANCELLED`, query-
limit diagnostics, and `SOURCE_CLOSE_FAILED` unchanged.

Every diagnostic has component `gpx` or `kml`. A feature-local result uses the positive physical
candidate number; exact `pointIndex`, when relevant, is a bounded decimal context value. Other parser
locations are omitted because StAX line/column/offset behavior is implementation-dependent. No path,
creator/name/ID/text/coordinate token, namespace prefix, URI, href, XML excerpt, parser location,
class, or cause message appears in stable output.

Opening precedence for both is public arguments, already-cancelled token, parser-policy configuration,
path/attributes/input size, snapshot/read/close, UTF-8/BOM/XML declaration, structural XML events in encounter
order, prospective structural limits, namespace/root profile, candidate structure/values, packed
allocation, duplicate generated-ID/schema validation, final cancellation, and source publication.
An encountered terminal format error remains primary; pending earlier warnings precede it, and cleanup
failures are suppressed. Source/cursor precedence after publication is the existing G4 in-memory
order.

### Evidence and implementation decomposition

Future tests use hand-built fixtures plus a small legally redistributable real-producer set for each
format, with source/license/provenance and SHA-256 recorded. They cover namespace prefixes, root/order,
every supported record/geometry, fixed schemas, dateline literals, empty sources, BOM, CDATA/entity
escaping, presentation/extension warning retention and omission, all rejected constructs, malformed XML/UTF-8, DTD/XXE/
schema-location/resolver canaries, exact/one-over limits, checked overflow, already/mid-parse
cancellation, snapshot mutation, cleanup precedence, query lifecycle, and tolerant map rendering.
No input canary may access a public network or local file outside its temporary fixture tree.

Architecture tests prove JDK-only/AWT-free modules, no shared XML module, only
`newDefaultFactory()`, exact parser settings/readback, no internal JDK API/reflection/discovery, no
URL/network/schema resolution, and no parser type leakage. Each module joins publication staging and
the ordinary offline Java 21 consumer with no external dependency mirror. Native tests explicitly
register the tiny fixture resources and exercise parser construction, one query/render success, an
ignored-data warning, and one exact malformed result on Linux. They make no broader platform claim
without corresponding evidence and add no new corpus command.

After approval, create eight one-to-five-day working cards:

1. `G10-050` — create `mundane-map-io-gpx` with the hardened snapshot/StAX boundary, fixed source,
   waypoint query/render slice, publication staging, and offline consumer.
2. `G10-051` — add track-segment lines, fixed attributes, warned vertex-data omission, viewer, and
   tolerant render regression.
3. `G10-052` — close GPX grammar, bounded ignored-content reporting, every limit/diagnostic/cancellation/
   mutation/cleanup case, and provenance-recorded fixtures.
4. `G10-053` — extend `nativeSmoke` with explicit GPX success/warning/malformed paths and record the
   bounded Linux evidence.
5. `G10-054` — create `mundane-map-io-kml` with the same independently implemented security boundary,
   container traversal plus Point/LineString query/render, publication staging, and offline consumer.
6. `G10-055` — add Polygon and homogeneous MultiGeometry mappings through viewer and tolerant render
   regression.
7. `G10-056` — close KML presentation warnings, rejected dynamic/network/altitude behavior, every
   limit/diagnostic/cancellation/mutation/cleanup case, and provenance-recorded fixtures.
8. `G10-057` — extend `nativeSmoke` with explicit KML success/warning/malformed paths and close the
   shared security-evidence review without merging the modules.

G10-051 through G10-053 are serial after G10-050; G10-055 through G10-057 are serial after G10-054;
G10-057 additionally waits for G10-053 for the combined closeout. The GPX and KML branches are
logically parallel after G10-005, but their first cards are not path-safe while both change settings,
architecture inventories, publication, consumer, native inventory, task index, and roadmap files.
One integration owner serializes those shared changes. No module is created by this profile card.

## Remote XYZ tile acquisition (G10-006)

### Explicit acquisition, not a live network source

G10-006 is an AFK design decision and creates no code or module. The future JDK-only,
AWT-free `mundane-map-io-http-tiles` module depends on API, selected core raster/CRS algorithms,
`mundane-map-io-image`, and `java.net.http`. G0 allowlists only that exact acyclic I/O-to-codec edge.
The module never depends on AWT; callers pass the explicit G6 decoder registry whose concrete decoder
may come from the AWT application.

A `RasterSource.read` is synchronous and MapView invokes it while rendering. Hiding DNS, TLS, server
latency, retry, or connection lifetime behind that contract would block the EDT and make repaint drive
external effects. The first profile instead has one explicit blocking acquisition client:

```text
HttpXyzTiles.open(SourceIdentity identity,
                  HttpXyzTemplate template,
                  HttpXyzClientOptions options,
                  EncodedRasterDecoderRegistry decoders) -> HttpXyzTileClient throws SourceException

HttpXyzTileClient extends AutoCloseable
  fetch(XyzTileRegion region,
        CancellationToken cancellation) -> RasterSource throws SourceException
  isClosed() -> boolean
  close() throws SourceException

HttpXyzClientOptions(
    HttpSchemePolicy schemePolicy,
    HttpXyzLimits limits,
    RasterSourceLimits snapshotLimits,
    EncodedRasterDecodeOptions decodeOptions,
    HttpTileCachePolicy cachePolicy,
    Duration connectTimeout,
    Duration requestTimeout,
    Duration operationTimeout,
    Duration closeTimeout)

HttpSchemePolicy = HTTPS_ONLY | HTTPS_OR_HTTP
HttpTileCachePolicy = DISABLED | MEMORY
```

The opening source identity belongs to the client and therefore remains available for diagnostics
from fetch, close, and cleanup races. `decodeOptions` carries the G6 decode/allocation limits and
decoder policy but must not already declare an expected format; each successful response derives and
supplies that expectation from its validated media type. It likewise must not declare expected
dimensions; the client supplies exactly 256 by 256. The image/decode limits must admit the HTTP per-
tile encoded maximum and that exact pixel shape, or opening is an argument failure. Cache entry and
byte ceilings live only in
`HttpXyzLimits`, so enabling or disabling retention cannot introduce a second conflicting limit set.
Calling fetch after close is the ordinary lifecycle `IllegalStateException`; a close race after fetch
has started uses the structured `HTTP_TILE_CLIENT_CLOSED` outcome. A failed first close throws one
structured `SourceException` after completing its bounded cleanup attempt.

`fetch` performs all network and decoding synchronously from the caller's perspective. Its JDK client
uses bounded asynchronous requests internally only to achieve the configured concurrency. The public
API has no future, publisher, callback, executor, progress listener, live tile source, or hidden
refresh. Documentation and the eventual viewer require callers to invoke it on an application worker,
never an AWT event/render thread. Cancellation remains the only cross-thread operation during an
ordinary fetch.

Success returns a private in-memory raster-source implementation owning one complete detached RGBA
mosaic, exact Web Mercator placement, the requested G4 read limits, and opening warnings. It retains no
client, request, URI, body, decoder, channel, future, executor, cache entry, or network handle. Closing
the client immediately after success cannot change the source; closing the source drops its mosaic.
Source reads perform only the existing checked G4/G6 nearest/bilinear window work and lifecycle.

### XYZ region and URI-template profile

`XyzTileRegion(zoom, minimumX, minimumY, maximumX, maximumY)` is immutable and uses inclusive XYZ
coordinates with north-origin rows. Zoom is `0..22`; `axis = 1L << zoom`; every x/y lies in
`[0,axis)`, and minima do not exceed maxima. Tile count and output dimensions use checked `long`
before conversion. Each tile is exactly 256 by 256 pixels. No TMS switch, wraparound, metatile,
variable tile size, retina suffix, alternate matrix, or arbitrary CRS is included.

One static helper creates the smallest tile-aligned region covering a positive-area EPSG:3857
`Envelope` at an explicit zoom. It first requires the envelope wholly inside the canonical G4 Web
Mercator domain. With `M = PI * 6_378_137`, column edge `c` is the checked finite interpolation from
`-M` to `M` at `c/axis`, and row edge `r` is the interpolation from `M` to `-M` at `r/axis`. Binary
search over those monotone edges applies half-open cells internally while assigning the exact outer
world edge to the last tile. It does not clamp, wrap, densify, reproject, or infer zoom.

The returned source width and height are the region's tile-axis counts times 256. Its map bounds are
the west/east column edges and south/north row edges of that complete region, with recognized canonical
EPSG:3857 metadata. A request may therefore intentionally fetch a tile-aligned area slightly larger
than a viewport; normal raster-window planning clips presentation.

`HttpXyzTemplate` accepts one bounded ASCII hierarchical URI string. It has exact lowercase `https`,
or lowercase `http` only under explicit `HTTPS_OR_HTTP`; one fixed ASCII host; either no port or one
canonical decimal port in `1..65535`; no empty authority port, userinfo, query, fragment, IPv6 zone
identifier, percent escape, control, non-ASCII character, or relative/opaque form; and an absolute
path containing `{z}`, `{x}`, and `{y}` exactly once each. No other brace/token is accepted. The path
uses only RFC unreserved/sub-delimiter characters, slash, colon, at-sign, period, and the three
placeholders; `.`/`..` segments and backslash are rejected. Placeholders may share a segment with a
fixed suffix such as `{y}.png`.

Substitution uses only canonical unsigned decimal coordinates and reparses the final URI, which must
retain the exact validated scheme/host/port and safe path. There is no subdomain rotation, template
callback, URL encoding, environment/property expansion, token/header substitution, or credential
slot. Invalid templates/options/regions are caller configuration errors (`IllegalArgumentException`),
not hostile-response diagnostics. The adapter never derives source identity or any metadata from the
host or final URI and never copies either into a report, exception message, or `toString`. The caller
must supply a nonsensitive logical source ID under G4's existing identity contract.

### Owned Java 21 HTTP client and deterministic batching

Opening creates one Java 21 `HttpClient` and one fixed-size daemon platform-thread executor owned by
the tile client. Construction is direct and explicit:

```text
HttpClient.newBuilder()
  .executor(ownedFixedExecutor)
  .connectTimeout(connectTimeout)
  .followRedirects(HttpClient.Redirect.NEVER)
  .version(HttpClient.Version.HTTP_1_1)
  .proxy(directOnlyProxySelector)
  .sslContext(noClientCredentialContext)
  .build()
```

Before building, the client creates a new `SSLContext.getInstance("TLS")`, initializes a default-
algorithm `TrustManagerFactory` from a null `KeyStore` to capture the standard trust anchors, and calls
`init` with an empty `KeyManager[]`, those trust managers, and a new `SecureRandom`. This context has no
client key manager and therefore cannot inherit client certificates from the replaceable process-wide
default `SSLContext`; `HttpClient` still performs HTTPS endpoint identification. A platform security-
initialization failure closes the not-yet-published executor and throws `HTTP_TILE_CLIENT_INIT_FAILED`.

The direct proxy selector always returns `Proxy.NO_PROXY`. No authenticator, cookie handler, caller-
supplied SSL context/trust/key manager, system-property header, request body, or caller header hook
exists. Custom trust, client certificates, bearer/query tokens, authorization, cookies, referer,
proxy, and credential lookup require a separate security profile. Cleartext HTTP is a visible caller
opt-in intended primarily for controlled/local services.
The template is trusted application configuration, not an untrusted end-user URL. An application that
accepts a template from another principal must apply its own host and network-range allowlist before
opening the client; this profile does not claim to be a general SSRF boundary.

Every request is an exact `GET` with no body and only fixed project-owned headers:

```text
Accept: image/png, image/jpeg
Accept-Encoding: identity
User-Agent: mundane-java-map
```

The client handles one fetch at a time and requires external serialization. A second fetch is an
ordinary lifecycle `IllegalStateException`. It computes row-major tile order (y, then x), resolves
cache hits, and partitions remaining requests into contiguous row-major batches of at most the
configured concurrency. A batch is submitted together, but its headers, bodies, warnings, decoding,
error precedence, and cache staging are consumed strictly in row-major request order. Completion timing
therefore cannot change the returned pixels, primary diagnostic, or LRU order.

Before submitting a batch, the caller-thread planner reserves one complete per-tile encoded allowance
`B` against the remaining cumulative encoded-byte ceiling. Against the project-owned ceiling it also
reserves, per request, exactly
`R = 4 * B + 16 * 65_536 + 8 * ceil(B / 65_536)`: at most `B` bytes in 64-KiB body segments, their
reference slots, one final array of at most `B`, and G10-039's maximum `2 * B + 16 * pixelCount`
full-image decode charge. Checked arithmetic precedes the reservation. URI/header text, request/future/
subscriber/container slots, the region mosaic, and diagnostics are charged separately before their
corresponding allocation.

The planner reduces the batch below configured concurrency when either reservation requires it and
never submits a request without both full deterministic reservations. Body segments have exact
capacity `min(65_536, remaining per-tile allowance)`; they do not grow or over-allocate. For an actual
successful length `L`, EOF converts the body portion to segment capacity `S = 0` when `L = 0`, otherwise
`min(B, 65_536 * ceil(L / 65_536))`, `8 * ceil(L / 65_536)` segment slots, and the exact `L`-byte array.
It retains a decode reservation of `2 * L + 16 * 65_536` until row-major decode; G10-039 then converts
that reservation to its exact `2 * L + 16 * P` charge using the validated native pixel count `P`. Only
the unallocated difference is released. A missing or error response releases its unused body/decode
reservation. If the remaining budget cannot reserve one complete tile, the fetch fails before another
request. This conservative admission may stop before a smaller server body would have fit, but no
completion race can allocate or claim aggregate capacity out of row-major order.

Each request has the configured per-request timeout and the fetch has one monotonic overall deadline.
Before submitting a batch, the effective request duration is the smaller of the configured request
timeout and remaining overall time. There is exactly one attempt: no redirect, retry, backoff,
conditional request, fallback URL, alternate format, or stale-cache recovery. DNS, connect, TLS, and
JDK protocol steps are opaque operations checked immediately before and after; the design claims no
internal cancellation checkpoint or latency threshold for them.

The supervisory wait uses bounded intervals, checking the operation token/deadline between waits.
Every terminal path after any batch submission uses one common termination protocol: establish the
row-major response, request, limit, decode, deadline, cancellation, interruption, or close outcome as
primary; cancel every remaining future and available body subscription; release reservations; discard
staged bodies, pixels, warnings, and cache mutations; and wait at most the one close/drain timeout for
the batch to settle. No active batch is allowed to overlap a later fetch. If work settles, an ordinary
failure releases the active slot and the still-open client remains reusable. If it does not settle,
the client invokes `HttpClient.shutdownNow()` and executor `shutdownNow()`, permanently enters
`CLOSED`, and suppresses the cleanup outcome under the already established primary diagnostic.
Cancellation retains `SOURCE_CANCELLED`; interruption restores interrupt status and retains the stable
request-interrupted outcome. An unexpected `RuntimeException` or `Error` uses the same bounded cleanup
before propagating unchanged. The operation deadline never extends this bounded cleanup allowance.

`close` may race one fetch. The first call atomically marks the client closed, cancels active futures
and subscriptions, invokes `HttpClient.shutdownNow()` and executor `shutdownNow()`, and awaits both
within one total close timeout; it never calls the potentially unbounded orderly `HttpClient.close()`.
The active fetch uses `HTTP_TILE_CLIENT_CLOSED` only when close wins terminal arbitration. Any already-
established request, response, limit, decode, deadline, cancellation, interruption, or unexpected
failure remains primary. A fetch whose success linearization point already released its active slot
still returns that success. Close is permanent and idempotent; cleanup runs once and is not retried
after a timeout/failure. Returned detached sources remain valid.
Close and fetch claim one cleanup-owner flag while holding client state; the loser never performs a
second shutdown/drain and waits only within the remaining shared close-timeout budget.

### Response, image, and missing-tile behavior

The body handler first evaluates response metadata without reading error bodies. Each header value is
one header for the count ceiling, including every value associated with a repeated name. The repeated
name is charged again when aggregating header characters. Only status 200 is a tile success. Status
404 or 410 is one recoverable missing tile; its subscriber cancels immediately,
the mosaic receives transparent black for that tile, and one `HTTP_TILE_MISSING` warning is offered to
the normal G4 warning cap. Every other status, including 1xx, 204, 3xx, 401/403, 429, and 5xx, is
terminal. Error/missing response bodies are never retained, decoded, logged, or included in a
diagnostic.

A 200 response requires exactly one ASCII `Content-Type` value which, after outer optional whitespace
and case folding, is exactly `image/png` or `image/jpeg` with no parameter. `Content-Encoding` must be
absent or exact case-insensitive `identity`. `Content-Length` may be absent or one non-negative decimal
not exceeding the per-tile limit; duplicates, comma lists, signs, overflow, or mismatch with the final
body are terminal. Header name/value count and character ceilings are checked before project copying.

Success uses a custom back-pressured `HttpResponse.BodySubscriber<byte[]>`, not
`BodySubscribers.ofByteArray`. It requests one delivered buffer list at a time, prospectively checks
the per-body limit, fills the fixed 64-KiB project-owned segments defined above, polls cancellation,
and cancels its `Flow.Subscription` before accepting an over-limit chunk. One final exact array is
created only after EOF/declared-length validation, then segment references are cleared. Received JDK
`ByteBuffer` instances are opaque transport allocations;
the project bounds concurrency, header/body bytes, owned copies, and operation duration but does not
claim byte-perfect accounting for DNS/TLS/socket/internal HTTP buffers.

Completed bodies in a batch are processed row-major. Each EOF has already converted its reservation
to an exact cumulative encoded-response charge before decode. G10-039's
`RasterImages.decode(byte[], ...)` receives a copy of the client's
decode options with the media-derived expected format and exact expected dimensions 256 by 256, the
client's exact source identity, explicit decoder registry, and operation token. The expected-dimension
preflight guarantees `P <= 65_536` before complete-decode allocation, so the reservation above cannot
be exceeded by a larger valid image. `IMAGE_DIMENSIONS_MISMATCH` translates to
`HTTP_TILE_IMAGE_INVALID/reason=dimensions` with its safe numeric actual `width` and `height`; every
`SOURCE_CANCELLED` becomes the one ordinary HTTP fetch `SOURCE_CANCELLED`, and helper
`SOURCE_LIMIT_EXCEEDED` becomes `SOURCE_LIMIT_EXCEEDED/scope=httpTileFetch/limit=imageDecode` while
retaining only its numeric `requested` and `maximum`. Each of the helper's eight closed image codes
becomes `HTTP_TILE_IMAGE_INVALID/reason=decode` with only `imageCode`. An unchecked token/decoder
failure propagates unchanged after the common batch cleanup, and any other checked code is a decoder-
contract `IllegalStateException`. A returned buffer contradicting the helper's expected-size contract
is likewise unexpected, not a second hostile-input outcome. The module does not call ImageIO, inspect
an AWT type, expose encoded bytes, retain a nested image exception, or build a temporary file/raster
source.

An entirely missing region is still a successful transparent raster with warnings. A malformed,
wrong-media, wrong-size, cancelled, timed-out, or other failed tile makes the complete fetch fail;
there is no partial source or substitution. Pixel composition copies decoded tiles into the mosaic in
row-major order and polls at most every 4,096 pixels.

### Transactional decoded cache and detached snapshot

`DISABLED` is a distinct cache policy. `MEMORY` stores only successful immutable decoded
256-by-256 `RgbaPixelBuffer` values under `(zoom,x,y)` for this client's one fixed template. It does not
store encoded bodies, errors, missing responses, headers, validators, credentials, source mosaics, or
disk data. Entry and exact RGBA-byte bounds are both enforced; one tile costs exactly 262,144 bytes.
There is no freshness interval, validator, revalidation, or stale-while-error behavior. A caller that
requires a fresh acquisition uses `DISABLED` or opens a new client rather than silently reusing an
unverifiable memory entry.

A fetch snapshots cache order/content at start. Hits supply pixels but stage promotions; decoded
misses stage admissions. Missing/error/cancelled requests stage nothing. The existing cache is not
mutated until every tile, output allocation, final cancellation/deadline check, and detached source
construction succeeds. One commit then applies row-major hit promotions and admissions, evicting
least-recently-used entries until both ceilings hold. Failed fetches leave membership/order unchanged.

The final `CancellationToken` callback is polled immediately before acquiring client state, never
while holding its lock; a callback that blocks, throws, or reenters therefore cannot prevent close from
acquiring state. One short critical section is then the success linearization point. It reads only
project-owned primitive state, rechecks the monotonic deadline and closed flag, applies every staged
cache mutation, records the detached source as the terminal success payload, and releases the active-
fetch slot atomically. Cancellation requested after the outside-lock checkpoint loses to a success
that reaches this section, matching G4/G6's final-checkpoint publication rule.

If cancellation, deadline, or close wins before this section, no cache mutation occurs and the common
terminal cleanup path runs. If success wins, `fetch` returns that payload after unlocking even when
close begins before the Java method returns; close then observes no active fetch and may clear the
cache without changing the independent source. No lock is held while invoking caller code, submitting
or awaiting network work, draining a subscription, decoding, or awaiting shutdown. Thus no failed
fetch commits cache state and there is one unambiguous success/close/cancellation arbitration point.

The returned private raster source owns an exact row-major `int[]` mosaic and its fixed opening report.
It applies G4 strict-window validation and G6's request interpolation/resampling algorithms, with no
network/cache callback. A successful read allocates an independent output buffer and remains reusable;
failure/cancellation discards only that read. Snapshot close is idempotent and cannot fail.

### Limits, diagnostics, and evidence

`HttpXyzLimits` is immutable; counts/characters/bytes fit positive `int` except cumulative/owned bytes,
which are positive `long`. Durations are separate option values. Defaults and hard maxima are:

| Ceiling | Default | Hard maximum |
| --- | ---: | ---: |
| Template/final URI ASCII characters | 2,048 | 8,192 |
| Zoom | 22 | 22 |
| Tiles in one region | 64 | 1,024 |
| Tiles on either region axis | 32 | 128 |
| Concurrent requests | 4 | 16 |
| Response headers | 64 | 256 |
| Characters in one header name/value | 4,096 | 16,384 |
| Aggregate response-header characters per tile | 16,384 | 65,536 |
| Encoded bytes in one successful tile | 2,097,152 | 16,777,216 |
| Cumulative encoded bytes per fetch | 67,108,864 | 536,870,912 |
| Conservatively project-owned fetch bytes | 268,435,456 | 2,147,483,647 |
| Retained opening warnings | 256 | 4,096 |
| Decoded cache entries | 64 | 1,024 |
| Decoded cache RGBA bytes | 16,777,216 | 268,435,456 |

Duration defaults/hard maxima are connect 10/120 seconds, request 15/300 seconds, operation 60/900
seconds, and close/drain 2/30 seconds. Values have positive millisecond precision and require
`connect <= request <= operation`; close is independent. Configured concurrency cannot exceed the
maximum region-tile ceiling, and an actual smaller region simply submits a smaller batch. Tile count
cannot exceed the checked axis product, and aggregate body bytes are at least one maximum body. An
enabled cache must accept at least one complete tile; its independent byte ceiling may intentionally
bind before its entry ceiling. With `P = 65_536`, configured per-tile encoded maximum `B`, and maximum
region tile count `T`, the project-owned option's validation floor is the checked sum of `4 * P * T`
for the mosaic, `16 * T` for tile/future reference slots, and one complete `R` reservation defined
above. Larger actual batches consume another `R` per submitted request and therefore shrink safely
when the remaining budget cannot support configured concurrency. The helper's returned RGBA charge is
transferred into the fetch counter rather than charged a second time; a persistent cache entry remains
independently bounded by the cache byte ceiling. Output dimensions/pixels and every read remain
separately bounded by the supplied G4 `RasterSourceLimits`.

Each prospective count/product/sum uses checked `long`; equality succeeds and maximum plus one fails
before request/allocation/publication. Request/future/subscriber/list slots, URI strings, project header
copies, body segments/arrays, decoded staging, cache transaction entries, mosaic, diagnostics, and
detached-source containers are charged without identity deduplication. The persistent cache has its own
entry/byte limits; references to existing immutable cache buffers charge operation reference slots but
not their already-owned pixels a second time.

Caller configuration errors are ordinary argument failures. Fetch diagnostics use the client's
supplied source ID, component `httpTile`, row-major positive request ordinal as record number, and
safe numeric `zoom`, `x`, and `y` context only when tile-local. Except for the caller-authored logical
source ID, adapter-derived metadata and context never contain scheme, host, port, URI/template, path,
request/response header text, media/body bytes, TLS/DNS/provider text, thread name, credential,
decoder message, or native/JDK object. The closed outcomes are:

| Code | Severity and exact context |
| --- | --- |
| `HTTP_TILE_CLIENT_INIT_FAILED` | error; `resource=tls|client`, `reason=security|other` |
| `HTTP_TILE_MISSING` | warning; `zoom`, `x`, `y`, `status=404|410` |
| `HTTP_TILE_CLIENT_CLOSED` | error; `phase=fetch|decode|publish` |
| `HTTP_TILE_REQUEST_FAILED` | error; `phase=dns|connect|tls|send|body|wait`, `reason=timeout|interrupted|protocol|io|other` |
| `HTTP_TILE_RESPONSE_INVALID` | error; `field=status|contentType|contentEncoding|contentLength|headers`, `reason=missing|duplicate|syntax|unsupported|range|mismatch`; numeric `status` is required only when `field=status` |
| `HTTP_TILE_IMAGE_INVALID` | error; `reason=decode` requires `imageCode=<one of the eight closed helper image codes>`; `reason=dimensions` requires numeric `width`, `height` |
| `HTTP_TILE_CLOSE_FAILED` | error; `resource=body|client|executor`, `reason=timeout|interrupted|other` |

`SOURCE_LIMIT_EXCEEDED` uses `scope=httpTileFetch|httpTileCache` and
`limit=templateCharacters|uriCharacters|zoom|tiles|tileAxis|concurrency|headers|headerCharacters|
tileBodyBytes|bodyBytes|ownedBytes|imageDecode|cacheEntries|cacheBytes`. `imageDecode` preserves only
the helper limit's numeric `requested` and `maximum`; its nested scope/name is not public HTTP context.
Retained warnings use G4's cap/omission, not a terminal limit. `SOURCE_CANCELLED` retains the G4
code/shape. A nested image code is a flat closed context token, not a chained diagnostic contract.

Precedence is public arguments/lifecycle, already-cancelled token, effective limits/region/output,
cache snapshot, overall deadline, batch URI/request creation, response outcomes in row-major order,
headers, body/per-body limit, cumulative body limit, image decode/dimensions, mosaic/output allocation,
final cancellation/deadline, detached source construction, and the atomic success/cache/publication
linearization point. A response error established before a later cancellation observation remains
primary; every terminal path cancels and boundedly drains its outstanding batch, with cleanup failures
suppressed. Close that wins the state transition before a terminal fetch result uses the client-closed
outcome.

Later tests use only `jdk.httpserver` loopback servers in test/consumer code. Barrier-controlled
handlers prove maximum concurrency and out-of-order completion while assertions retain row-major
diagnostics/cache pixels. Cases cover HTTPS-only rejection and explicit loopback HTTP, exact template/
XYZ/world math, fixed headers/no credential/proxy/redirect, PNG/JPEG success, 404/410, every other
status family, media/encoding/length/header errors, chunked exact/over-limit/truncated bodies,
malformed/wrong-size images, request/operation timeout, token cancellation before/during every stage,
thread interruption, client close in flight, drain poisoning, cache hit/LRU/rollback, detached reads,
tolerant rendering, publication, and a clean offline consumer. No automated or default example ever
contacts a public service.

The module has no Level 2 Native Image claim: JDK HTTP/TLS/executor reachability, DNS, trust-store
resources, and cancellation behavior require their own evidence. It is excluded from the shared native
executable until such a task exists. Project code still uses no reflection, scanning, dynamic proxy,
serialization, JNI, `Unsafe`, internal JDK API, implicit resource lookup, or automatic provider
discovery. No performance threshold is inferred from loopback timing.

After G10-039 supplies the shared encoded-byte helper, create three serial working cards:

1. `G10-060` — create `mundane-map-io-http-tiles` with strict template/client policy, one-tile
   PNG/JPEG acquisition into a detached source, architecture checks, publication, and loopback offline
   consumer.
2. `G10-061` — add exact region math, deterministic concurrent batches, missing tiles, decoded LRU,
   worker-driven viewer, and tolerant render integration.
3. `G10-062` — close every status/header/body/limit/deadline/cancellation/interrupt/close/cache rollback
   case and document the JVM-only support boundary without adding a Native Image claim.

G10-060 depends on G10-039; G10-061 and G10-062 follow serially because they share the client, source,
cache, fixtures, module, and publication files. G10-039 is also the shared prerequisite for G10-042 and
G10-043; a single integration owner lands it before the independent tile-format branches. No module or
network request is created by G10-006 itself.

## Additional-projection evidence decision (G10-007)

### Current decision: DEFER

G10-007 is the named HITL checkpoint **G10 additional-projection evidence decision**. Its current
design outcome is `DEFER`: no third CRS definition, alias, projection, dependency, module, or
implementation task is added. This is an affirmative scope decision, not a `Blocked` task state and
not a claim that EPSG:4326 and EPSG:3857 satisfy every consumer.

The repository provides no demonstrated third-projection workflow:

- the basic, measurement, shapefile, raster, symbol, and performance examples use only the exact G4
  EPSG:4326/EPSG:3857 definitions and direct operation;
- DTED, GPX, KML, and RFC 7946 GeoJSON expose geographic WGS 84 coordinates under their approved
  profiles;
- XYZ and MBTiles use the canonical Web Mercator matrix, while the approved GeoPackage feature/tile
  profiles recognize only the existing EPSG:4326/EPSG:3857 definitions;
- the GeoTIFF profile rejects unapproved EPSG, user-defined, WKT, vertical, and compound CRS constructs
  rather than widening recognition, and no fixture or consumer requires another accepted operation;
  GeoPackage alone retains its well-formed unrecognized CRS rows as unknown metadata; and
- no issue, example, checked-in fixture, release claim, accuracy target, or platform requirement
  identifies UTM, a polar CRS, a national grid, a datum shift, or arbitrary EPSG lookup as the next
  useful capability.

Selecting from CRS popularity would therefore invent axis, datum, domain, envelope, format-recognition,
and deployment requirements. The existing G4 registry remains sufficient developer ergonomics:
applications may explicitly register a reviewed immutable `Projection` against exact definitions, but
that does not become a MundaneJ correctness, interoperability, or support claim.

### Evidence packet required to reopen selection

A future proposal supplies one bounded immutable design record in the task/design text, not a new
runtime API. It must contain all of:

| Evidence | Required content |
| --- | --- |
| Workflow | Named application/owner, observable map operation, and why 4326/3857 or application-owned registration is insufficient. |
| Endpoints | Exact source and target CRS identifiers/definitions, direction(s), x/y tuple normalization, axes, units, datum, and one fixed epoch or explicit timeless semantics. |
| Domain | Real coordinate/envelope ranges, discontinuities, singularities, antimeridian/zone behavior, and expected out-of-domain policy. |
| Accuracy | Use-case-derived horizontal forward/inverse and round-trip tolerances in declared units, not library-chosen numbers. |
| Data path | Concrete vector, raster, elevation, tile, or format workflow plus legally usable representative fixtures. |
| Conformance | Authoritative specification and independently produced vectors with provenance and redistribution terms. |
| Operations | Whether one fixed 2D pair suffices or database lookup, concatenation, or horizontal grid operations are required; any vertical, compound, or per-coordinate-time need is identified separately. |
| Deployment | Java/JVM, OS/architecture, offline/resource, publication, consumer, and Native Image targets. |
| Scale | Coordinate/feature volume and a measured performance target only when performance affects the architectural choice. |

Raster data must state separately whether it needs only metadata recognition or actual reprojection.
Registering another coordinate operation does not relax G4's same-CRS raster boundary: raster warping
requires its own evidenced sampling, nodata, envelope/densification, memory, rendering, and performance
design. Likewise, recognizing a format's CRS spelling is a bounded format task; it is not automatic
permission to add a general WKT/EPSG resolver.

An incomplete packet yields `DEFER` without reserving an ID or module. Evidence links must be stable,
licensed where copied, and precise enough for a reviewer to reproduce the candidate coordinate cases.

Before either implementation outcome is eligible, the proposed operation must fit G4's public model:

- exactly two source and two target ordinates use the x/y visualization convention and geographic or
  projected definitions;
- datum and all operation parameters, including an epoch when relevant, are fixed at construction;
  no coordinate carries time and no mutable ambient epoch is consulted;
- forward and inverse operations are deterministic over explicit domains and meet the evidenced
  tolerances; and
- strict/conservative envelope transformation can be defined without adding a third ordinate or
  silently dropping vertical/time semantics.

A vertical or compound CRS, height correction, per-coordinate epoch, time-dependent coordinate, more-
than-two-dimensional tuple, or non-reversible operation does not become `PROJ_REQUIRED` merely because
PROJ can calculate it. The checkpoint records `DEFER` and requires a separate public-capability design
for that data model, lifecycle, query/render semantics, diagnostics, and compatibility first. Only a
fixed-epoch 2D horizontal profile representable by G4 proceeds through this three-outcome gate.

### Three closed outcomes

The checkpoint records exactly one outcome:

| Outcome | Selection rule | Architectural result |
| --- | --- | --- |
| `DEFER` | Any evidence or G4 compatibility condition is missing; the use case is hypothetical; application-owned registration is sufficient; or the real capability is raster warping, format parsing, vertical/compound coordinates, or per-coordinate time. | Keep G4 unchanged. Create no implementation task/module/dependency. Record what evidence or prerequisite capability is missing. |
| `CORE_DIRECT` | One fixed reversible direct pair has static parameters, no external CRS database/grid/time data, a JDK-only formula of reviewable size, authoritative conformance vectors, and a conservative operation-specific envelope rule. | Add the exact definition/projection in API/core only when its first working vertical slice lands; keep JDK-only and explicit registration. |
| `PROJ_REQUIRED` | A compatible fixed-epoch 2D reversible horizontal pair requires pinned CRS-database selection, concatenation, or horizontal grid shifts that a small direct formula cannot honestly provide. | Use a separately approved optional adapter; no external type, context, database object, or error leaks into API/core. |

The outcomes are mutually exclusive for one proposed profile. There is no core subset with an implicit
PROJ fallback, provider ranking, runtime discovery, classpath scan, network grid download, or heuristic
switch based on input coordinates. PROJ is selected for demonstrated transformation semantics, not as
unmeasured performance acceleration. A custom native performance library remains outside this rubric
and still requires the separate benchmark decision mandated by G7.

### Obligations of a future CORE_DIRECT result

A direct result must name one canonical definition/pair and then specify before implementation:

1. the immutable `CrsDefinition` axes, units, exact closed coordinate domain, canonical identifier,
   and deliberately small exact alias set;
2. forward/inverse operation domains, parameter constants and provenance, positive-zero policy,
   finite/intermediate checks, boundary-ULP behavior, and stable G4 diagnostic mappings;
3. an operation-specific conservative envelope algorithm, including sampling/densification proof when
   extrema are not axis-separable—four-corner projection is never inherited by default;
4. use-case-derived forward/inverse/round-trip tolerances and authoritative plus independent vectors,
   including every edge, singularity, discontinuity, and malformed definition;
5. explicit `CrsRegistry.builderWithLevel1()` registration with no implicit change to `level1()` until
   the support/publication decision intentionally makes it built in; and
6. one real feature query/render path, relevant format recognition, Javadocs, staged consumer, and
   Linux Native Image evidence before making a native support claim.

The candidate may not squeeze a new axis meaning or unit into an existing enum value. If the evidenced
CRS cannot use the current x/y visualization convention with degree/metre and longitude/latitude or
easting/northing semantics, its profile must include an explicit public-contract compatibility review
before it can be classified `CORE_DIRECT`.

The first implementation task must produce a usable coordinate/feature vertical slice, not merely a
definition constant or empty module. If the workflow later requires raster reprojection, that is a
separate dependent capability with its own observable raster slice.

### Obligations of a future PROJ_REQUIRED result

G11-004 owns the general optional-adapter policy. A projection evidence packet that genuinely selects
PROJ must additionally decide and verify:

- one pinned PROJ version, upstream artifacts/license/notices/checksums, supported OS/architecture and
  linkage/package strategy, and whether the required CRS/grid database is embedded, caller supplied,
  or deliberately unsupported;
- explicit adapter construction and lifetime with no global mutable default, environment search,
  network resource download, classpath discovery, or automatic native loading in API/core;
- an explicit resolution of PROJ's native lifetime against G4's immutable, non-closeable `Projection`
  contract: a native handle/context may not be hidden in that value or registered into an ordinary
  `CrsRegistry` unless ownership, concurrency, and deterministic close are first represented by a
  MundaneJ-only reviewed contract; PROJ handles, enums, exceptions, paths, and database text never
  cross the adapter boundary;
- fixed source/target selection, axis normalization, operation choice, accuracy metadata, stable
  bounded diagnostic translation, fixed epoch, both directions, conservative envelope behavior,
  thread ownership, cancellation limits, and deterministic cleanup;
- native/JVM packaging and staged-consumer evidence on every claimed platform, with Native Image kept
  unclaimed until an explicit native task proves reachability and native-library/resource behavior;
  and
- conformance comparisons against the pinned command/library plus malformed/missing resource cases.

No `mundane-map-adapter-proj` module name is reserved by G10-007. A module is named and registered only
in the later task that has working adapter behavior, tests, publication policy, and one demonstrated
consumer.

### Later decomposition rule

When a complete packet changes `DEFER`, first create one new HITL profile card that records the chosen
outcome and exact contract. Only after approval does it create roughly one-to-five-day working slices:
the first coordinate/feature path, relevant format integration, hardening/conformance, and then
publication/consumer/native evidence. Paths touching API/core registry files, format recognition,
architecture inventories, publication, native inventory, index, and roadmap are serialized under one
integration owner. No task identifiers are reserved while the decision remains deferred.

### G10 holistic simplicity closeout

G10 preserves the smallest useful boundaries after reviewing all seven decisions together:

- SVG import produces ordinary Level 1 symbols; it does not create an SVG scene graph or arbitrary
  document engine.
- GeoJSON isolates its one justified Jackson parser while the public source remains dependency-free.
- GeoTIFF keeps bounded image and elevation entry points in one format module without becoming a TIFF,
  CRS, or GDAL framework.
- GeoPackage and MBTiles remain separate optional adapters; their only shared production addition is
  G10-039's encoded-byte image helper, now justified by those two formats and HTTP XYZ.
- GPX and KML keep separate secure StAX state machines instead of a speculative XML/GIS hierarchy.
- HTTP XYZ is explicit acquisition into a detached raster, not network behavior hidden in paint or
  `RasterSource.read`.
- Projection expansion is deferred rather than adding an unused formula, CRS database, native bridge,
  raster warp abstraction, or empty adapter.

Every future module is still created only with working behavior and tests. API/core remain JDK-only;
AWT stays confined; external dependencies stay optional/non-leaking; registries remain explicit; and
Native Image claims remain evidence-specific. Removing any approved boundary would merge incompatible
format/security/lifecycle concerns, while adding a general plug-in, scene, XML, database, network,
warp, or CRS framework would have no demonstrated consumer. G10 is therefore simple enough and no
simpler, and G11 may build on these explicit outputs without reopening their scopes.
