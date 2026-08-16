# GPX adapter capability intent

`mundane-map-io-gpx` is the project's JDK-only adapter for secure, bounded GPX 1.1 interchange. The
approved target includes complete GPX 1.1 reading, immutable domain values, feature-source projection,
and deterministic schema-valid writing. GPX is an interchange format for waypoints, routes, and tracks;
the module is not a live GPS protocol, navigation/routing engine, activity-analysis service, or device API.

Unknown vendor content inside standard `<extensions>` containers is preserved as a bounded XML infoset.
Callers may explicitly register typed codecs for chosen namespaces/QNames. The core module does not
embed Garmin or other vendor-specific schemas, discover codecs dynamically, execute extension content,
or access extension resources.

The root README describes released behavior. Target rows below become release claims only as their G19
cards close.

## Standards and processing boundary

| Standard/profile | Approved use | Claim boundary |
| --- | --- | --- |
| [GPX 1.1](https://www.topografix.com/gpx/1/1/), namespace `http://www.topografix.com/GPX/1/1` | Complete standard document/domain reader and canonical writer | Version 1.1 only; GPX 1.0 requires explicit future version handling rather than namespace guessing |
| XML Schema datatypes used by GPX 1.1 | Coordinates, measurements, dates, URIs, identifiers, and restricted enumerations | Implement GPX-required lexical/value spaces directly; no runtime schema download or general XSD engine |
| XML 1.0 and Namespaces in XML | Secure parsing and deterministic serialization | Hardened StAX; no DTD, entities, XInclude, external schema resolution, or ambient resource lookup |
| WGS 84 and metric-unit convention declared by GPX 1.1 | Longitude/latitude, elevation/geoid height, distance/error values | GPX storage remains WGS 84/metric; conversions are explicit at neutral geometry/application boundaries |

## GPX 1.1 document matrix

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Root/version | GPX 1.1 root, version, creator validation | Complete ordered root model for metadata, waypoints, routes, tracks, and root extensions with deterministic namespace/version behavior | G19-100 |
| Metadata | Parsed for structure but most values ignored | Name, description, author/person/email/link, copyright/year/license, repeated links, time, keywords, bounds, and extensions | G19-100 |
| Waypoints | Coordinates plus selected display/time/elevation fields | Complete `wptType`: elevation, time, magnetic variation, geoid height, name/comment/description/source, links, symbol/type, fix, satellites, dilution values, DGPS age/station, and extensions | G19-101 |
| Routes | Rejected | Complete route metadata, ordered route points using full waypoint semantics, route identity, and extensions | G19-102 |
| Tracks | Track/segments/coordinates plus selected fields | Complete track metadata, ordered segments, full track-point semantics, empty/degenerate rules, identity, and extensions | G19-102 |
| Bounds | Structural validation only | Immutable validated metadata bounds with antimeridian/empty/derived-extent policy | G19-100, G19-104 |
| Extensions | Validated then discarded with warnings | Bounded namespace-aware opaque infoset preservation plus explicit typed codec registry | G19-103 |
| Feature source | Waypoints and tracks flattened to records | Explicit stable waypoint/route/track/segment mapping retaining dimensions, standard metadata, identity, and access to the complete GPX domain document | G19-104 |
| Writer | None | Canonical deterministic schema-valid GPX 1.1 with strict representability and transactional output | G19-105 |

## Domain and feature-projection contract

- Public immutable values represent the root, metadata, person/email/copyright/link/bounds, waypoint,
  route, track, segment, fix enumeration, and extension containers without routing all information through
  a string attribute map.
- Standard optionality, order, repeated links/points/segments, numeric value spaces, and date-time offsets
  are retained semantically. The canonical writer may normalize numeric/time lexical forms, namespace
  prefixes, and insignificant whitespace.
- Longitude/latitude are WGS 84; GPX elevations and error/distance measurements remain metric. Neutral
  geometry projection retains Z/time/segment identity where the approved geometry/attribute contracts can
  express them and exposes the domain document for information that a feature schema cannot represent.
- Feature IDs are stable, bounded, deterministic document-local identities. They do not expose source
  paths or treat mutable names as identity.
- Queries remain bounded and cancellation-aware. Parsing/projecting one valid file never performs network,
  schema, device, map-matching, route-solving, elevation-service, or reverse-geocoding I/O.

## Extension contract

- Unknown children of a standard `<extensions>` container are preserved by expanded element/attribute
  names, namespace bindings needed for their values, text, and ordered child structure. Comments,
  processing instructions, source prefix choices, and incidental whitespace are not a fidelity promise.
- Opaque trees are immutable data, never reparsed as executable markup, and are bounded prospectively by
  elements, attributes, namespaces, depth, text/code points, owned bytes, and aggregate document work.
- Typed codecs are registered explicitly by namespace/QName and immutable registry configuration. There
  is no reflection, service loading, classpath scanning, schema downloading, or first-provider-wins behavior.
- A codec consumes and produces the safe extension infoset rather than the live XML reader/writer. Its
  errors and cost are isolated and translated to stable diagnostics. Unknown content remains available even
  when no codec exists.
- Writer policy chooses typed re-encoding when lossless and approved, otherwise canonical opaque emission.
  Namespace conflicts, non-representable XML values, or exceeded limits fail before output.

## Canonical writer contract

- A builder requires an explicit creator and document/domain value, supplies safe encoding/formatting
  defaults, and offers bounded options for metadata, extension registry/policy, output, cancellation, and
  schema-location emission.
- Preflight the entire root, standard values, extension trees, namespaces, and output estimate. Invalid or
  non-representable input fails with stable path-aware diagnostics before bytes are committed.
- Emit GPX 1.1 schema order, deterministic namespace declarations/prefixes, attributes, numeric/time/URI
  lexical forms, escaping, line endings, and UTF-8 encoding. Identical inputs/options produce identical bytes.
- Write atomically to files and transactionally to bounded sinks. Cancellation/failure preserves prior
  targets and closes every staged resource.
- Do not invent vendor extensions to retain neutral data. Lossy export is rejected rather than silently
  dropping Z/time/metadata/extension values; explicit application transformations happen before the writer.

## Deliberate exclusions

- GPX 1.0 compatibility, NMEA/device communication, live tracking, routing/navigation/map matching, fitness
  analytics, geocoding, elevation lookup, and arbitrary XML/XSD processing.
- Built-in Garmin, Strava, geocaching, heart-rate, cadence, power, temperature, or other vendor semantics.
  Such support belongs in separately approved typed adapters/codecs.
- Lexical preservation of source whitespace, prefixes, comments, processing instructions, numeric spelling,
  or date-time spelling. Semantic extension infoset and GPX value preservation is the contract.

## Completion evidence

- Validate generated documents against a pinned local copy/hash of the official schema outside production
  parsing, and test official-schema-derived plus multiple independent producer/consumer fixtures.
- Cover every GPX field/type/cardinality/order/value boundary, unknown/typed extensions, deterministic
  read-write-read semantics, malformed/hostile XML, cancellation, cleanup, and exact limits.
- Record independent application interoperability for waypoints, routes, tracks, metadata, dimensions, and
  extensions without claiming incidental formatting fidelity.
- G19-106 closes the module only when this matrix, implementation, package/root documentation, diagnostics,
  examples, native/publication evidence, and external observations agree.
