# KML adapter capability intent

`mundane-map-io-kml` is the project's JDK-only adapter for secure, bounded OGC KML 2.3/KMZ
interchange and 2D map presentation. The approved target includes complete standard document values,
canonical writing, controlled `NetworkLink` retrieval, transactional in-memory `Update`, and bounded
tour execution. It does not turn this 2D map toolkit into a general 3D earth browser, web browser,
media player, or remote KML editing service.

Unknown standard extension-point content is preserved as a bounded XML infoset. Typed interpretation is
available only through explicitly registered codecs. KML description/balloon HTML remains bounded data in
the JDK-only module; the separately planned `mundane-map-io-kml-html-jsoup` adapter provides an explicitly
sanitized static HTML presentation profile.

The root README describes released behavior. Target rows below become release claims only as their G19
cards close.

## Standards and conformance boundary

| Standard/profile | Approved use | Claim boundary |
| --- | --- | --- |
| [OGC KML 2.3](https://docs.ogc.org/is/12-007r2/12-007r2.html), OGC 12-007r2 | Complete KML document/interchange model, standard assertions, dynamic declarations, and canonical writing | Namespace remains `http://www.opengis.net/kml/2.2` with the KML 2.3 version/assertion rules; no generic Google-extension claim |
| [OGC KML 2.3 Abstract Test Suite](https://docs.ogc.org/ts/14-068r2/14-068r2.html), OGC 14-068r2 | Conformance inventory and external evidence | Claim only the classes demonstrated by the final implementation and recorded profile |
| OGC KML 2.2 | Backward-compatible input/interchange evidence | Support according to explicit version dispatch and constructs also represented by the completed model |
| Atom author/link and xAL address content referenced by KML | Typed feature metadata | Only the KML-referenced subset; not general Atom feed or xAL document support |
| XML 1.0, Namespaces in XML, XML Schema datatypes, and ZIP | Secure KML/KMZ parsing and deterministic output | No DTD, entities, XInclude, runtime schema lookup, ambient resource lookup, or general archive API claim |
| Static sanitized HTML profile in planned Jsoup adapter | Description and balloon presentation | Formatting subset only; no scripts, forms, frames, active CSS, browser execution, or ambient resources |

## Document and feature matrix

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Root/version | KML 2.2 geometry subset | KML 2.2/2.3 root, version assertions, `NetworkLinkControl`, root feature, standard extension groups, and deterministic dispatch | G19-110 |
| Feature hierarchy | Document/Folder/Placemark flattened | Complete common feature data plus Document, Folder, Placemark, NetworkLink, overlays, and Tour as immutable ordered objects with stable IDs/target IDs | G19-110 |
| Metadata/data | Name/description/visibility subset | Atom/xAL subset, address/phone/snippet/descriptions, views/time/style/region links, Schema, ExtendedData, Data/SchemaData/SimpleData/SimpleArrayData, units, and display names | G19-110 |
| Extensions | Mostly rejected | Bounded foreign attributes/simple/object extension infosets plus explicit typed codec registry; standard KML 2.3 constructs stay typed | G19-110 |
| Feature source | Placemark geometry/attributes | Document-preserving and stable query projections for supported geographic features, overlays, models, tracks, and dynamic snapshots | G19-111–G19-117 |

## Geometry, view, time, and presentation matrix

| Area | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Geometry | XY Point/LineString/Polygon and homogeneous MultiGeometry subset | Point, LineString, LinearRing, Polygon, heterogeneous MultiGeometry, Track, MultiTrack, complete coordinate tuples, Z/time/angles, and geometry extension points | G19-111 |
| Altitude | Rejected/ignored | Altitude/sea-floor modes, altitude offset, extrude, tessellate, datum/terrain availability, and explicit 2D fallback semantics | G19-111 |
| Views/time | Rejected | Camera, LookAt, viewer options, TimeStamp, TimeSpan, horizons/field of view, altitude modes, and exact date/time precision | G19-112 |
| Regions | Rejected | Region, LatLonAltBox, Lod, fade/extents, view/scale activation, nested visibility, and bounded query scheduling | G19-112 |
| Styles | Rejected | Shared/inline Style and StyleMap, normal/highlight resolution, Icon/Label/Line/Poly/Balloon/List styles, color/random behavior, hotspots, and resource fallback | G19-113 |
| Descriptions/balloons | Retained or ignored as text | Bounded raw markup data plus escaped plain text in core; optional sanitized static HTML through G19-225/G19-226 | G19-113, G19-225, G19-226 |
| GroundOverlay | Rejected | Full map-aligned/quadrilateral raster placement, altitude, draw order, color, rotation, time/region, and bounded resources | G19-114 |
| ScreenOverlay | Rejected | Viewport-fixed overlay units, anchors, size/rotation/order, accessibility, interaction, and bounded resources | G19-114 |
| PhotoOverlay | Rejected | Complete interchange plus geographic location/footprint and optional authorized thumbnail on the 2D map; no panorama viewer | G19-114 |
| Model | Rejected | Complete Model/Location/Orientation/Scale/Link/ResourceMap/Alias interchange plus deterministic 2D anchor/footprint representation; no COLLADA parser/3D renderer | G19-114 |
| KMZ | Rejected | Path-confined deterministic archives, `doc.kml`, explicit resource catalog, media validation, deduplication, and archive-bomb defenses | G19-114 |

## Dynamic behavior matrix

| Area | Approved target | Safety/authority boundary | Card |
| --- | --- | --- | --- |
| `NetworkLink` and `Link` | Fetch KML/KMZ with on-change, interval, expire, and view-refresh behavior, regions, templates, and validators | Disabled without explicit client policy; authorized origins, redirects, URI templates/parameters, depth/fan-out, bytes, refresh rates, sessions, cache, and cancellation are bounded | G19-115 |
| `NetworkLinkControl` | Respect minimum refresh, maximum session, expiry, view, visibility/fly-to, and safe message/link metadata | Cookies/query additions are inert unless explicitly authorized; no credential or secret propagation |
| `Update` | Transactional `Create`, `Change`, and `Delete` against explicitly loaded document identities/generations | In-memory representation only; no remote file/service modification; stale/ambiguous/cyclic/over-limit updates reject atomically | G19-116 |
| `Tour` | Host-neutral deterministic playback of FlyTo, Wait, TourControl, AnimatedUpdate, and SoundCue timeline | No autoplay; explicit start/pause/resume/cancel; bounded durations/events/resources; sound delivered only to registered handler; no ambient media/network | G19-117 |

## Resource and HTML policy

- Local KML references resolve only through an explicit caller catalog. KMZ-relative resources resolve only
  inside the normalized archive namespace; absolute paths, traversal, aliases, duplicate normalized names,
  symlink semantics, and archive/resource bombs are rejected.
- Network resources use the same explicit G19 HTTP authority/cache policy as `NetworkLink`; parsing an href
  never grants fetch authority. Credentials, cookies, referrers, URL query secrets, redirects, and cache scope
  have closed caller-controlled policies.
- Image/SVG/font/media bytes have declared media types, exact limits, and explicit decoders/handlers. COLLADA
  and panorama bytes can be preserved and repackaged but are not parsed/rendered by the core profile.
- Core descriptions/balloons preserve bounded text/markup and can render escaped plain text. The optional
  Jsoup adapter parses/sanitizes only its named static subset and resolves images through the same catalog.
- Foreign KML extension infosets follow the GPX-style immutable expanded-name/attribute/text/child-order
  contract, not source prefix/whitespace/comment/processing-instruction fidelity.

## Canonical writer contract

- A builder emits deterministic schema-valid KML 2.3 or a path-confined deterministic KMZ package, with
  explicit version, resource, extension, markup, dynamic-declaration, and output policies.
- Preflight the complete object graph, IDs/target IDs, references, styles, extensions, resources, update/tour
  declarations, archive paths, and output estimate. Reject non-representable toolkit behavior rather than
  silently dropping it or inventing vendor extensions.
- Use deterministic namespace prefixes, IDs when generated, schema/version attributes, element/attribute
  order, numeric/date/color/coordinate lexical forms, escaping, whitespace, UTF-8, archive entry order,
  timestamps, compression settings, and resource names. Identical inputs/options produce identical bytes.
- File output is atomic and bounded; cancellation/failure preserves prior targets and closes all staged
  archives/resources. Writing a `NetworkLink` or `Update` declaration never fetches or modifies its target.

## Deliberate exclusions

- General 3D/COLLADA rendering, panorama viewing, arbitrary HTML/browser execution, autoplay, platform media,
  remote mutation, server-side KML hosting, ambient resource/network access, and general Atom/xAL/XML/ZIP APIs.
- Automatic semantic support for every Google/vendor extension. KML 2.3 standard elements are typed;
  additional foreign content remains opaque unless an explicit codec is separately approved.
- Exact source XML prefix/whitespace/comment/processing-instruction fidelity. The writer guarantees semantic
  model/extension preservation and canonical output.

## Completion evidence

- Cover the applicable KML 2.3 abstract tests, pinned local schema/hash, independent KML/KMZ producers and
  consumers, deterministic writer/read-back, and AWT/Vaadin presentation within declared 2D tolerances.
- Test every object/field/assertion, geometry/style/resource/dynamic path, hostile XML/ZIP/HTML/URI/reference
  graph, concurrency/generation boundary, cancellation/cleanup, and exact limit.
- G19-119 closes the core module only when this matrix, implementation, public docs, diagnostics, examples,
  native/publication/offline evidence, and external observations agree. G19-226 separately closes the optional
  sanitized HTML adapter.
