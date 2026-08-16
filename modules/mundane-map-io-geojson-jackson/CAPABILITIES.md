# GeoJSON adapter capability intent

`mundane-map-io-geojson-jackson` is the project's optional Jackson Core adapter for secure, bounded
RFC 7946 GeoJSON document/feature-source reading and deterministic writing, plus incremental RFC 8142
GeoJSON Text Sequences. Strict RFC 7946 is the default and only normal output profile.

An explicitly selected legacy-input profile accepts the obsolete pre-RFC `crs` member, resolves only
caller-registered CRS identifiers, and reprojects through the core CRS engine while retaining original
CRS metadata for audit. It never fetches a linked CRS and does not emit legacy `crs` output.

Unknown foreign members are retained as immutable bounded JSON values. Typed interpretation is available
only through explicitly registered member codecs; duplicate object keys are rejected. Canonical writing
preserves JSON semantics but not source member order, whitespace, escaping choices, or numeric spelling.

The root README describes released behavior. Target rows below become release claims only as their G19
cards close.

## Standards and profile boundary

| Standard/profile | Approved use | Claim boundary |
| --- | --- | --- |
| [RFC 7946](https://www.rfc-editor.org/rfc/rfc7946) | Complete GeoJSON geometry/feature/collection reader and canonical writer | Strict WGS 84 longitude/latitude output and RFC interoperability recommendations; no standard `crs` member |
| [RFC 8142](https://www.rfc-editor.org/rfc/rfc8142) | Incremental GeoJSON Text Sequence reader/writer using RS framing and `application/geo+json-seq` | Records are complete RFC 7946 GeoJSON objects; not newline-delimited JSON or an arbitrary JSON sequence |
| GeoJSON 2008 legacy CRS shape | Explicit opt-in input compatibility for named/linked `crs` objects | Registered identifiers and bounded reprojection only; no fetch and no legacy output claim |
| JSON as constrained by RFC 7946 and Jackson Core | Structured properties/foreign members and deterministic serialization | Strict duplicate-key rejection, finite bounded numbers, no comments/non-standard tokens, no public Jackson-tree dependency |

## GeoJSON object and geometry matrix

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Root objects | Geometry, Feature, FeatureCollection subset | All RFC 7946 GeoJSON object roots with immutable document values and feature-source projections | G19-132 |
| Geometry | Point, MultiPoint, LineString, MultiLineString, Polygon, MultiPolygon; non-empty XY | All seven geometry types including GeometryCollection, schema-valid empty coordinate arrays, nested collections, and null Feature geometry | G19-130 |
| Positions | Exactly two ordinates | Longitude/latitude, optional altitude as Z, and bounded preservation of further numeric elements as uninterpreted position-tail ordinates | G19-130 |
| Polygon rules | Closed rings, limited validity | RFC ring cardinality/closure, right-hand-rule writer normalization, compatible reader acceptance/reporting of opposite winding, and explicit topology policy | G19-131 |
| Bounding boxes | Validated but discarded/limited | Immutable 2D/N-dimensional bbox on every allowed object, dimension consistency, antimeridian-spanning and pole semantics, validation/derivation policy | G19-131 |
| Features | String/number IDs and scalar properties projected | Typed string/number IDs, null geometry/properties, complete structured properties, bbox, foreign members, and stable document order/identity | G19-132 |
| Collections | FeatureCollection subset | Empty/large collections, collection bbox/foreign members, stable order, incremental projection, and aggregate accounting | G19-132 |
| Foreign members | Ignored after bounded validation | Semantic immutable JSON tree at every GeoJSON object, collision policy, explicit typed codecs, and canonical round trip | G19-132 |
| Legacy CRS | Rejected | Explicit legacy-input option, caller registry, audit metadata, bounded reprojection, and strict-mode rejection | G19-133 |
| Sequences | Unsupported | RFC 8142 pull/cursor reader and streaming writer with record-local diagnostics/recovery and stream budgets | G19-134 |
| Writer | Deterministic six-family FeatureCollection file writer | Complete RFC 7946 document/geometry/value/foreign-member writer plus RFC 8142 sequence writer | G19-135 |

## Coordinate, bbox, and validity contract

- Strict RFC 7946 positions use longitude then latitude in decimal degrees on WGS 84. Longitude and
  latitude ranges are validated; values are not silently swapped, wrapped, clamped, or normalized.
- A third element is retained as altitude in metres relative to the WGS 84 reference ellipsoid. Additional
  finite elements are preserved as bounded uninterpreted ordinates; the toolkit does not invent M/time meaning.
- Geometry dimension is internally consistent according to a frozen profile. Conversion to neutral geometry
  preserves all supported ordinates and collection membership and reports non-representable projections.
- Reader accepts non-right-hand-rule polygon winding for compatibility as RFC 7946 recommends, records the
  normalization condition, and preserves topology. Canonical output emits the required right-hand rule.
- Bboxes contain twice the coordinate dimensions, retain object scope, and may cross the antimeridian with
  northeast longitude less than southwest longitude. Pole/3D/further-dimension and derived-bbox rules are explicit.
- Empty geometries use the exact RFC-compatible empty coordinate structures. Invalid rings, mixed dimensions,
  non-finite/huge numeric tokens, or topology/profile failures produce stable path/index diagnostics.

## Structured JSON and foreign-member contract

- Public immutable JSON values cover null, boolean, string, exact bounded number, array, and object without
  exposing Jackson node types. Object keys are unique and retained semantically; array order is exact.
- Standard members remain typed and cannot be shadowed by foreign-member storage. A member whose name is
  standard for another GeoJSON object type remains foreign only according to an explicit per-type table.
- Foreign members are retained at Geometry, GeometryCollection, Feature, and FeatureCollection scopes and
  round-trip semantically. Canonical output uses deterministic object-member ordering and number formatting.
- Optional typed codecs are explicitly registered by object kind/member name, immutable, collision-checked,
  cost-bounded, and operate on safe JSON values. There is no reflection, service loading, or arbitrary code discovery.
- Depth, members, keys, strings/code points, number digits/exponents, array entries, value nodes, owned bytes,
  codec work, and total document/record work are charged prospectively.

## Legacy CRS input contract

- Strict mode rejects `crs` consistently at every object. Legacy mode accepts only the documented `name` and
  `link` object shapes from older GeoJSON practice and requires one explicit caller registry resolution.
- A linked form is an identifier, not fetch authority. No URL, file, schema, registry, or WKT is downloaded or
  interpreted implicitly. Unknown/ambiguous identifiers fail before geometry publication.
- Coordinates and bboxes are transformed to RFC 7946 WGS 84 with axis/unit/domain/topology/precision/antimeridian
  policy from core. Original CRS object/identifier and transformation result remain audit metadata.
- Legacy input can be rewritten only as strict RFC 7946 after successful reprojection. The writer never emits `crs`.

## RFC 8142 streaming contract

- Each record starts with ASCII RS (`0x1E`), contains exactly one RFC 7946 GeoJSON object encoded as UTF-8,
  and uses the RFC-recommended line feed after the JSON text in canonical output.
- Pull/cursor APIs process records incrementally with explicit ownership, cancellation, close, record index, per-record
  and aggregate limits, and no whole-stream retention.
- Recovery after malformed/truncated records is opt-in and bounded to the next RS within an exact scan-byte ceiling.
  A record error never silently splices JSON or changes subsequent record indices.
- Writer preflights each record, writes one complete bounded frame at a time, and defines committed-record semantics
  for stream sinks. Transactional file output stages the complete sequence and atomically replaces the target.

## Canonical writer contract

- Emit standard member order per object followed by foreign members in deterministic code-point order, with stable
  UTF-8, escaping, finite numeric formatting, coordinates/bboxes, right-hand-rule rings, and no insignificant whitespace.
- Preflight the entire document or one sequence record for geometry validity/dimensions, WGS 84 coordinates,
  properties, IDs, foreign-member collisions/codecs, representability, and exact limits before committed bytes.
- Identical semantic values/options produce identical bytes. This is a project deterministic GeoJSON profile, not
  a claim of RFC 8785 JSON Canonicalization Scheme unless a future task explicitly adds it.
- Filesystem output is atomic; cancellation/failure preserves prior targets and aggregates cursor/sink cleanup failures.

## Deliberate exclusions

- Legacy CRS output, automatic CRS inference, linked-CRS fetch, longitude/latitude repair, arbitrary semantics for
  fourth/further ordinates, TopoJSON, JSON-FG, NDJSON, comments/non-standard JSON, and public Jackson tree exposure.
- Lossless lexical preservation of source member order, whitespace, escapes, exponent/decimal spelling, negative zero,
  or duplicate keys. Duplicate keys are invalid rather than preserved.

## Completion evidence

- Cover all RFC 7946 normative requirements/recommendations selected by the profile, RFC examples/errata, RFC 8142
  framing/recovery, legacy CRS fixtures, and multiple independent producers/consumers.
- Test every object/member/value/geometry/empty/dimension/bbox/winding/antimeridian/foreign/CRS/sequence path,
  malformed/fuzz inputs, exact limits, cancellation/cleanup, native/publication/offline, and API documentation.
- G19-136 closes the module only when this matrix, implementation, package/root docs, diagnostics, examples,
  dependency profile, and external interoperability evidence agree.
