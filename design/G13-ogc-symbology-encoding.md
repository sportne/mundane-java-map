# G13 — OGC Symbology Encoding design

Project index: [DESIGN.md](../DESIGN.md).

## Purpose and standard baseline

G13 adds a bounded read-only OGC Symbology Encoding 1.1.0 adapter after the MIL-STD-2525 gate. The
authoritative standard is OGC 05-077r4:

- <https://www.ogc.org/standards/se/>
- <https://docs.ogc.org/is/05-077r4/05-077r4.pdf>

SE defines XML `FeatureTypeStyle` and `CoverageStyle` roots, ordered rules, scale selection, feature
filters, and symbolizers. The first project profile accepts `FeatureTypeStyle` only. It is not an SLD
or WMS implementation, and it does not claim the complete SE conformance classes.

## Module and dependency boundary

The working module is `mundane-map-io-se`. It is JDK-only, AWT-free, and uses directly constructed,
hardened JDK StAX from `java.xml`. It depends on `mundane-map-api` and `mundane-map-core`; XML, OGC,
StAX, and adapter-specific types do not leak into those modules.

The adapter translates supported SE content into ordinary immutable symbols plus a standards-neutral
ordered portrayal plan. G13-001 may extend the G11 portrayal contracts only with the smallest closed
rule and evaluation-context values needed by both SE and the demonstrated future MapLibre consumer.
It may not add callbacks, scripting, arbitrary functions, a DOM, or an alternate renderer.

The adapter's intended public surface is `SeStyles.read(Path, NamedSymbolCatalog, SeReadOptions)` and
`read(String sourceName, byte[], NamedSymbolCatalog, SeReadOptions)`, returning immutable
`SeFeatureStyle` metadata plus its ordinary `FeaturePortrayal`. `SeReadOptions` contains only
`SeReadLimits` and a `CancellationToken`. Failures are `SeReadException` values carrying one
immutable `SeReadProblem`; no warning collector or partially usable result is returned. Package
implementation types, StAX types, XML names, and bridge compilation details remain private.

## Approved SE profile

The only accepted document root is `{http://www.opengis.net/se}FeatureTypeStyle`. Prefix spelling is
irrelevant, but every SE and Filter element must have its exact namespace. The root `version`
attribute may be absent or exactly `1.1.0`. Namespace declarations and an inert
`xsi:schemaLocation` are accepted but never resolved; every other root attribute is rejected.
XML 1.0 UTF-8 is the only accepted encoding. A byte-order mark is accepted and removed by the
bounded snapshot reader.

The following matrix is exhaustive. Child order is the SE schema order, duplicates beyond the
listed cardinality reject the document, and non-whitespace mixed content is never accepted.

| Parent | Accepted children and attributes |
| --- | --- |
| `FeatureTypeStyle` | `Name?`, `Description?`, `FeatureTypeName?`, `SemanticTypeIdentifier*`, `Rule+`; `version?` only |
| `Description` | `Title?`, `Abstract?`; no attributes |
| `Rule` | `Name?`, `Description?`, exactly one of `ogc:Filter?` or `ElseFilter?`, `MinScaleDenominator?`, `MaxScaleDenominator?`, then one or more supported symbolizers; no attributes |
| `PointSymbolizer` | `Name?`, `Description?`, exactly one `Graphic`; absent or exact pixel `uom` only |
| `Graphic` | exactly one `Mark` or `ExternalGraphic`, then `Opacity?`, `Size?`, `Rotation?`, `AnchorPoint?`, `Displacement?`; all numeric values are literal text |
| `Mark` | `WellKnownName?`, `Fill?`, `Stroke?`; no external mark or `MarkIndex` |
| `LineSymbolizer` | `Name?`, `Description?`, exactly one `Stroke`; absent or exact pixel `uom` only |
| `PolygonSymbolizer` | `Name?`, `Description?`, `Fill?`, `Stroke?`, at least one present; absent or exact pixel `uom` only |
| `Fill` | `SvgParameter` names `fill` and `fill-opacity`, each at most once |
| `Stroke` | `SvgParameter` names `stroke`, `stroke-opacity`, and `stroke-width`, each at most once |
| `AnchorPoint` | exactly one `AnchorPointX` followed by one `AnchorPointY` |
| `Displacement` | exactly one `DisplacementX` followed by one `DisplacementY` |
| `ExternalGraphic` | exactly one `OnlineResource` followed by `Format`; see the catalog policy below |

`SvgParameter` has exactly one unqualified `name` attribute. `OnlineResource` has exactly
`xlink:type` and `xlink:href`; `xlink:type` is `simple`. Every Filter predicate, operand, graphical
value, metadata value, and structural element has no attributes unless the matrix explicitly says
otherwise. Namespace declaration attributes are ignored for accounting only after their namespace
URI is validated. Comments are ignored; processing instructions, CDATA, DTDs, entity references,
and non-whitespace text outside value elements reject the document.

`Name`, `Title`, `Abstract`, `FeatureTypeName`, and `SemanticTypeIdentifier` retain stripped,
non-blank bounded text. Names are not required to be unique and have no lookup effect.
`FeatureTypeName` and semantic identifiers are metadata for caller validation only; they never select
or discover a source. `Geometry` is rejected on every symbolizer because the map binding already
supplies the one feature geometry and G13 does not implement geometry expressions.

The exact `WellKnownName` set is case-sensitive `square`, `circle`, `triangle`, `star`, `cross`, and
`x`, mapped to the existing G2 built-ins. Missing `WellKnownName` means `square`. Colors are exactly
`#RRGGBB`; opacity is a finite decimal in `[0,1]`; positive size and stroke width are finite decimal
logical-screen pixels. Graphic size defaults to `6`, opacity and rotation to `1` and `0`,
respectively, anchor to `(0.5,0.5)`, and displacement to `(0,0)`. Anchor ordinates must each be
exactly `0`, `0.5`, or `1` and map to the nine existing `SymbolAnchor` positions. Positive X
displacement is right; positive Y follows SE and is up, so the adapter negates it for the existing
screen-down marker offset. Rotation is clockwise and screen-relative. Mark fill defaults to opaque
mid-gray and stroke to absent; a present empty `Fill` uses that default. A present empty `Stroke`
uses opaque black at width `1`. Line strokes require a color and default to opacity `1`, width `1`.
Polygon fill and outline use the same component defaults.

Only absent `uom` or `http://www.opengeospatial.org/se/units/pixel` is accepted. Metre, foot, and
other units are deferred rather than silently converted. `SvgParameter` values, graphical values,
and resource names are literals: nested Filter expressions, `se:Function`, arithmetic, environment
lookups, and dynamic symbol parameters are rejected.

`TextSymbolizer`, `RasterSymbolizer`, `CoverageStyle`, SLD wrappers, graphic strokes/fills, dash
arrays, line joins/caps, perpendicular offsets, color maps, functions, geometry expressions, vendor
options, legend graphics, inline content, external marks, remote resources, and style/rule
`OnlineResource` inclusion are rejected. This is a named project subset, not an SE conformance-class,
SLD, or WMS claim.

## Rule, predicate, and scale semantics

Rules retain document order and symbolizers retain document order. Scale filtering happens before
predicate evaluation. `MinScaleDenominator` is inclusive, `MaxScaleDenominator` is exclusive, and
both are finite nonnegative decimals with `min < max` when both exist. An unconstrained rule is
active at every supplied scale.

An ordinary rule contains no filter (always true) or exactly one bounded Filter 1.1 predicate. The
accepted Filter grammar is exhaustive:

- `PropertyIsEqualTo`, `PropertyIsNotEqualTo`, `PropertyIsLessThan`,
  `PropertyIsLessThanOrEqualTo`, `PropertyIsGreaterThan`, and
  `PropertyIsGreaterThanOrEqualTo`, each with exactly two `PropertyName`/`Literal` operands and at
  least one property operand;
- `PropertyIsBetween`, with one property expression plus literal/property lower and upper
  boundaries, all inclusive;
- `PropertyIsNull`, true only for a present `AttributeNull.INSTANCE` and false for a missing key;
- `And` and `Or` with two or more predicates, and `Not` with exactly one.

`PropertyName` is an exact non-blank canonical attribute key; XPath, QName expansion, and nested
paths are not supported. A missing attribute makes its comparison false. Present binary/unsupported
attribute values make it false. Equality requires equal canonical kinds, except all numeric forms
compare as normalized `BigDecimal`; explicit null equals explicit null. Ordered comparison is
defined only for numeric values, exact text using `String.compareTo`, and `LocalDate`; boolean, null,
or unlike kinds return false. Literal text is converted against the other operand's kind using the
strict canonical boolean, finite decimal, or ISO local-date grammar; for a text operand it remains
exact text. A conversion failure is an ordinary false result, not an evaluation diagnostic.
Property-to-property comparison uses their canonical values directly.

The bridge calls this predicate `IsNull`, not `Exists`; missing and explicit null remain observably
different throughout the API. This corrects the draft shorthand that called the operation a
property-existence test.

After scale selection, an `ElseFilter` rule matches exactly when no active ordinary rule's predicate
matches that feature. An unfiltered active ordinary rule therefore prevents an else match. At most
one `ElseFilter` may be active at any scale; overlapping else scale intervals are rejected while
compiling the document. The relative document position of an else rule does not change that
condition. With no match and no eligible else, the feature has no portrayal.

Every matching ordinary rule contributes, as may the one matching else rule. Point and multipoint
features consume point symbolizers, line and multiline features consume line symbolizers, and
polygon/multipolygon features consume polygon symbolizers; an incompatible symbolizer has no
operation for that feature geometry. Because `Geometry` expressions are excluded, exactly one of
those roles is applicable to each feature.

Applicable symbols are gathered in rule then symbolizer order. Each `PolygonSymbolizer` becomes one
atomic fill-role `SolidFillSymbol` whose optional outline is its own stroke, so repeated polygon
symbolizers paint `fill1/outline1`, then `fill2/outline2` rather than regrouping fills and strokes.
One applicable result is returned directly; multiple results become
`CompositeSymbol.of(results, 1.0)`, whose children already paint bottom-to-top. The single accepted
point graphic representation produces one marker rather than treating SE's alternative graphic
representations as layers. This preserves SE painter order without adding a parallel rendering
operation model. No applicable result means that geometry is unstyled.

## Standards-neutral portrayal bridge

G13-003 adds the smallest public bridge to `mundane-map-api` rather than exposing SE types:

- `RulePortrayalPlan` owns the immutable non-empty ordered rules and creates the three
  role-selectors needed by `FeaturePortrayal`;
- `PortrayalRule` owns optional bounded metadata, one `ScaleInterval`, one ordinary predicate or the
  else marker, and immutable ordered marker/line/fill symbol lists;
- sealed `PortrayalPredicate` values cover explicit-null, comparison, between, and boolean
  composition; sealed `PortrayalOperand` values cover a property or retained literal;
- `PortrayalEvaluationContext` carries one optional validated scale denominator;
- `ResolvedFeaturePortrayal` carries the optional marker, line, and fill results of one evaluation;
  and
- `RuleSymbolSelector` is added to the closed `SymbolSelector` permits list and retains a shared plan
  plus one role.

The public constructors enforce structural bounds and defensive copies; adapter-specific read limits
may be tighter. `FeaturePortrayalResolver` compiles the plan once, exposes its stable deduplicated
required-attribute order, and adds
`resolveAll(Map<String,Object>, PortrayalEvaluationContext)`. This evaluates matching rules once and
returns all three role results; MapView paint, hit, selection, and export paths use that operation
rather than making separate role calls. Existing fixed, categorical, and graduated resolution
remains source- and binary-compatible. Existing per-role resolution delegates to the all-role result
when it encounters a rule plan and is a convenience, not the high-throughput integration path.
Calling the legacy context-free overload on a rule plan containing scale constraints throws the
neutral `PORTRAYAL_SCALE_CONTEXT_REQUIRED` problem rather than guessing a scale. A plan with no
scale constraints may use either overload.

The predicate algebra has no callbacks, arbitrary functions, I/O, time, locale, reflection, user
code, or mutation. Its evaluator is JDK-only in core. One `resolveAll` call evaluates a matching rule
once and constructs all role results from that decision; no mutable or cross-frame result cache is
introduced. Required property names are computed at compile time, deduplicated by first expression
occurrence, and unioned with label needs for `FeatureQuery`.

For `MapView`, constrained styles attach only when the display CRS has projected easting/northing
metre axes. The denominator is:

`viewport.worldUnitsPerPixel / 0.00028`

using SE's standardized 0.28 mm rendering pixel and logical pixels, never physical monitor DPI.
Geographic degrees, unknown units, non-metre projected axes, and non-finite results produce the
standards-neutral `PORTRAYAL_SCALE_CRS_UNSUPPORTED` problem at attachment. Adapter-specific SE
diagnostics never originate in API, core, or AWT. Outside AWT the caller supplies a finite
nonnegative denominator in `PortrayalEvaluationContext`. Query selection, paint, hit testing, and
export all use the same context snapshot.

## Parser, resources, and limits

Input is a bounded UTF-8 byte snapshot from a local `Path` or caller-owned byte array. The path
overload reads and closes one regular local file; neither overload accepts a stream, URL, resolver,
schema location, or parser factory. DTDs, entities, XInclude, schema retrieval, external resources,
and namespace confusion are rejected. XML schema validation is not required at runtime; the parser
implements and tests the approved grammar directly.

Limits cover input bytes, XML depth, elements, attributes, text, rules, predicates, predicate depth,
symbolizers, catalog references, and total vector/composite output. Cancellation is checked while
capturing and parsing. All public outputs are immutable defensive copies.

The adapter receives an explicit `NamedSymbolCatalog` for `ExternalGraphic`. `OnlineResource` must
have exact `xlink:type="simple"` and one `xlink:href` matching
`[A-Za-z0-9][A-Za-z0-9._-]{0,127}`. `Format` must be exactly
`application/vnd.mundane-map.symbol`. The href is the exact case-sensitive catalog key. It is not
resolved as a URI, path, classpath resource, or network location; format sniffing and fallback among
multiple external resources do not occur. The catalog result must have marker role.

`SeReadLimits.defaults()` uses these ceilings: 1 MiB input, depth 32, 4096 elements, 8192
attributes, 256 KiB aggregate text, 4096 characters per metadata value, 256 rules, 1024 predicates,
predicate depth 32, 1024 symbolizers, 1024 graphic children/catalog references, and 2048 total output
symbols/composite children. Callers may provide positive tighter values up to those hard ceilings.
Accounting occurs before the next allocation or append. Cancellation is checked while reading each
64 KiB chunk and at least every 256 XML events and predicate/output nodes.

## Diagnostics and evidence

Stable codes and precedence are:

- `SE_CANCELLED`
- `SE_IO`
- `SE_INPUT_LIMIT`
- `SE_XML_SYNTAX`
- `SE_XML_SECURITY`
- `SE_ROOT_UNSUPPORTED`
- `SE_VERSION_UNSUPPORTED`
- `SE_NAMESPACE_UNSUPPORTED`
- `SE_ELEMENT_UNSUPPORTED`
- `SE_VALUE_INVALID`
- `SE_FILTER_UNSUPPORTED`
- `SE_SYMBOLIZER_UNSUPPORTED`
- `SE_RESOURCE_UNRESOLVED`
- `SE_LIMIT_EXCEEDED`

Precedence is cancellation, local I/O, encoded-byte limit, XML security/well-formedness, root and
version, structural limits, namespace/element support, literal/value validation, filter/symbolizer
support, catalog resolution, and bridge/scale attachment. The first problem aborts the whole read;
there is no partial style. Diagnostics identify the bounded source name, rule ordinal/name, element
path, and offending bounded value where appropriate. Parser syntax, unsupported valid SE,
unresolved explicit resources, and neutral portrayal attachment failures remain distinct.

Checked-in XML fixtures are project-authored BSD-3-Clause examples with a manifest that cites the
corresponding OGC 05-077r4 clauses. The OGC document examples are reviewed as behavioral oracles but
not copied into the repository. Hostile XML and deterministic bounded mutations are also
project-authored. Rendering regression proves ordered point/line/polygon results using bounds,
coverage, topology, and tolerant color samples rather than pixel identity. The final native scenario
parses one literal resource, resolves one catalog marker, evaluates rules, and renders the result.

## Task sequence

1. G13-001 approves the exact SE profile and shared closed portrayal bridge.
2. G13-002 creates the module with secure parsing and a literal point-symbolizer slice.
3. G13-003 adds ordered rules, filter predicates, and scale evaluation.
4. G13-004 adds line/polygon symbolizers and explicit catalog graphics.
5. G13-005 closes hostile input, reference fixtures, and the visual example.
6. G13-006 closes publication, consumer, and Linux Native Image evidence.

G14 starts only after G13-006 so MapLibre reuses one proven rule/portrayal bridge.
