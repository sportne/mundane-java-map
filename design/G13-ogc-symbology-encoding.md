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

## Supported SE profile

The proposed root is one namespace-correct `se:FeatureTypeStyle` with version absent or exactly
`1.1.0`. Optional name/description metadata is bounded and retained. `FeatureTypeName` and the
reserved generic semantic type identifiers may be retained for caller validation; they never trigger
source discovery.

Supported rule behavior is:

- Document-order painter's model with bounded rule and symbolizer counts.
- Optional `MinScaleDenominator` and `MaxScaleDenominator`, with lower-inclusive/upper-exclusive
  evaluation and exact finite nonnegative validation.
- No filter, `ElseFilter`, or a bounded OGC Filter 1.1 subset: property existence; equality and
  ordered scalar comparisons; `PropertyIsBetween`; and finite `And`, `Or`, and `Not` trees.
- Literal and property-name operands over the project's canonical scalar attribute values.
- Multiple matching rules and symbolizers composed in document order for each geometry role.

Supported symbolizers are:

- `PointSymbolizer` using `Mark`, a finite approved `WellKnownName` mapping, fill/stroke, size,
  opacity, rotation, and anchor/displacement values representable by G2.
- `LineSymbolizer` using solid strokes representable by `SolidLineSymbol`.
- `PolygonSymbolizer` using solid fill and optional solid outline.
- A bounded `ExternalGraphic` profile resolved only through a caller-supplied immutable symbol
  catalog; it never dereferences an `OnlineResource` itself.

`TextSymbolizer`, `RasterSymbolizer`, `CoverageStyle`, graphic strokes/fills, dashes not representable
by current symbols, color maps, functions, geometry expressions, vendor options, legend graphics,
remote resources, and rule/style `OnlineResource` inclusion are rejected with stable diagnostics.
SLD wrapping is a later independent task if a WMS use case appears.

## Standards-neutral portrayal bridge

The bridge is an immutable ordered rule plan owned by the API/core boundary, not by SE. Each rule has
an optional scale interval, one closed feature predicate, and zero or more role-specific symbols.
Evaluation consumes immutable feature attributes plus an explicit scale context and returns ordered
marker/line/fill results. Same-role results become an ordinary `CompositeSymbol`; no match means no
portrayal for that role.

The predicate/value algebra is closed and bounded. G13 supports literal values, property references,
comparisons, boolean composition, and existence checks. Evaluation has no I/O, time, locale,
reflection, user code, mutation, or exception callback. Required attribute names are computed once,
deduplicated in stable order, and projected through `FeatureQuery`.

For `MapView`, scale denominator is derived only for a declared linear-unit display CRS using the SE
standard pixel size and the current viewport scale. A style using scale constraints on an unsupported
unit/CRS fails attachment explicitly. Callers evaluating outside AWT may supply an already validated
scale denominator. This avoids guessed geographic scale or ambient display-DPI dependence.

## Parser, resources, and limits

Input is a bounded UTF-8 byte snapshot from a local `Path` or caller-owned byte array. The path
overload reads and closes one regular local file; neither overload accepts a stream, URL, resolver,
schema location, or parser factory. DTDs, entities, XInclude, schema retrieval, external resources,
and namespace confusion are rejected. XML schema validation is not required at runtime; the parser
implements and tests the approved grammar directly.

Limits cover input bytes, XML depth, elements, attributes, text, rules, predicates, predicate depth,
symbolizers, catalog references, and total vector/composite output. Cancellation is checked while
capturing and parsing. All public outputs are immutable defensive copies.

The adapter receives an explicit `NamedSymbolCatalog` for `ExternalGraphic` names. URI values are
treated only as exact catalog keys under the approved local-name syntax; the adapter performs no
filesystem or network access and never infers SVG, MIME, or image decoders.

## Diagnostics and evidence

Representative stable codes include:

- `SE_XML_SECURITY`
- `SE_ROOT_UNSUPPORTED`
- `SE_VERSION_UNSUPPORTED`
- `SE_ELEMENT_UNSUPPORTED`
- `SE_FILTER_UNSUPPORTED`
- `SE_SYMBOLIZER_UNSUPPORTED`
- `SE_RESOURCE_UNRESOLVED`
- `SE_SCALE_CONTEXT_UNSUPPORTED`
- `SE_LIMIT_EXCEEDED`

Diagnostics identify source, rule ordinal/name, element path, and offending bounded value where
appropriate. Parser syntax, unsupported valid SE, unresolved explicit resources, and portrayal
attachment failures remain distinct.

Fixtures include hand-built profile examples, selected OGC examples whose license/provenance is
recorded, hostile XML, and deterministic bounded mutation. Rendering regression proves ordered
point/line/polygon results without pixel-perfect assumptions. The final native scenario parses one
literal resource, resolves one catalog marker, evaluates rules, and renders the result.

## Task sequence

1. G13-001 approves the exact SE profile and shared closed portrayal bridge.
2. G13-002 creates the module with secure parsing and a literal point-symbolizer slice.
3. G13-003 adds ordered rules, filter predicates, and scale evaluation.
4. G13-004 adds line/polygon symbolizers and explicit catalog graphics.
5. G13-005 closes hostile input, reference fixtures, and the visual example.
6. G13-006 closes publication, consumer, and Linux Native Image evidence.

G14 starts only after G13-006 so MapLibre reuses one proven rule/portrayal bridge.
