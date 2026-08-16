# GeoPackage adapter capability intent

`mundane-map-io-geopackage-xerial` is the project's optional Xerial SQLite adapter for bounded direct
use of OGC GeoPackage 1.4.0 files. Completion includes immutable inspection and reading, builder-driven
creation with safe defaults, and transactional editing of existing packages. The high-level API exposes
typed GeoPackage operations, not arbitrary SQL.

The normative baseline is OGC 12-128r19, GeoPackage 1.4.0. Official extensions are part of the
GeoPackage conformance profile. Selected vector-tile and styling extensions are implemented only as
separately named community compatibility profiles and never counted as GeoPackage 1.4 conformance.

Unknown vendor extensions are inventoried and preserved when unrelated content is edited. A mutation
that could affect an unknown extension's package/table/column scope is blocked unless an explicit,
caller-registered codec owns that extension. No extension definition or database content grants SQL,
filesystem, network, native-code, or handler-discovery authority.

The root README describes released behavior. Target rows below become release claims only as their G19
cards close.

## Standards and profile boundary

| Standard/profile | Approved use | Claim boundary |
| --- | --- | --- |
| OGC GeoPackage 1.4.0, OGC 12-128r19 | Complete core plus features, tiles, attributes, and registered-extension read/write | Exact applicable requirements/conformance classes; SQLite format 3 and GeoPackage application/user version |
| GeoPackage CRS WKT Extension 1.1 | Complete WKT2 CRS read/write and epoch handling | Explicit CRS registry/operation compatibility; no ambient EPSG lookup |
| Tiled Gridded Coverage Data 1.1 | Complete coverage/elevation read/write | Standard ancillary, scale/offset/null/data semantics; no undeclared terrain blob inference |
| Related Tables 1.0 | Complete base/related/mapping relationship read/write | All standardized relation types plus registered safe media codecs |
| GeoPackage Vector Tiles Pilot profiles | Separately enabled vector-tile/MVT 2.1/GeoJSON/attribute-correlation compatibility | Community/pilot, not GeoPackage 1.4 conformance; exact declarations required |
| Releasable Basemap Tiles-compatible vector conventions | Separately enabled where compatible with the frozen pilot profile | Evidence reported independently; no broad RBT certification claim without its own conformance review |
| GeoPackage Styling and Symbology community extension | Separately enabled typed style/resource associations | Community profile; SE/SLD, MapLibre, SVG, raster codecs; no QGIS-project claim |

## Core container and content matrix

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| SQLite container | Read-only header/configuration subset | Validate/create/update SQLite format 3, `application_id`, `user_version`, required pragmas, integrity and transactional settings | G19-150, G19-157, G19-158 |
| Spatial reference systems | Core rows and limited recognized IDs | Complete `gpkg_spatial_ref_sys`, undefined/cartesian rows, organization/definition rules, WKT2 extension, epochs, caller registry mapping | G19-150 |
| Contents | Feature/tile discovery subset | Complete features/tiles/attributes/extension content, extents/timestamps/identifiers/descriptions, cross-table integrity | G19-150 |
| Attributes | Unsupported | Typed non-spatial user tables, schemas, cursors, queries, CRUD and relations | G19-150, G19-158 |
| Metadata | Unsupported | `gpkg_metadata` and reference scopes, MIME/URI semantics, hierarchy, CRUD and bounded registered codecs | G19-151 |
| Schema constraints | Unsupported | `gpkg_data_columns`, range/enum/glob constraints, descriptions/MIME, validation and schema-safe updates | G19-151 |
| Extension declarations | Extension-free only | Complete official registry plus scoped unknown-extension preservation and explicit codec registry | G19-151 |
| Related tables | Unsupported | Standard relationships, mapping tables, media/simple attributes/features/tiles and referential lifecycle | G19-151 |

## Geometry and feature contract

- Support core Geometry, Point, LineString, Polygon, MultiPoint, MultiLineString, MultiPolygon, and
  GeometryCollection plus registered CircularString, CompoundCurve, CurvePolygon, MultiCurve,
  MultiSurface, Curve, and Surface types. User-defined/deprecated geometry extensions are not inferred.
- Parse and write GeoPackageBinary headers, versions, flags, endian choices, SRS IDs, empty flags,
  XY/XYZ/XYM/XYZM envelopes, ISO WKB/SQL-MM type codes, empties, nested collections, and assignment rules.
- Preserve exact Z and M ordinates. Curves/surfaces remain canonical domain values; rendering uses an
  explicit bounded linearization/tessellation plan and never silently replaces the stored type.
- Feature tables support one declared geometry column, typed attributes/defaults/nullability/primary keys,
  prepared projection/filter/order/window queries, stable identity, cursors, and transactional CRUD.
- Geometry/table/schema/envelope/SRS constraints validate before commit. Depth, parts, rings, coordinates,
  attributes, blobs, linearization, topology, owned bytes, query output, and work are prospective limits.

## RTree and query contract

- Implement the registered `gpkg_rtree_index` virtual tables, exact trigger definitions, SQL geometry
  helper functions, create/rebuild/drop/validate, and query planning with a bounded full-scan fallback.
- Indexed and non-indexed queries return the same logical records and documented order at envelope
  boundaries, including empty/null/Z/M/curved geometries and CRS transformations.
- Treat missing, stale, corrupt, shadowed, or malicious RTree objects as stable integrity failures or an
  explicitly selected safe rebuild; never trust index candidates without the declared exact predicate.
- SQL is fixed/prepared. Identifiers are parsed, normalized, quoted, length-bounded, catalog-validated,
  and never concatenated from unvalidated database or caller strings.

## Raster tile pyramid contract

- Implement complete tile matrix set/table schemas, matrix dimensions, pixel/tile sizes, bounding boxes,
  row/column/zoom domains, factor-two and registered other-interval zooms, empty pyramids, partial coverage,
  and deterministic tile-window selection through the neutral OGC tile matrix model.
- PNG and JPEG are standard encodings; registered WebP decoding is supported through the separately optional
  AWT adapter. Tile writing accepts only already encoded blobs through an explicit validating tile-codec
  capability; this profile does not silently add PNG/JPEG/WebP encoders. Encodings are declared/sniffed
  consistently per table/tile and never guessed from arbitrary blobs.
- Support read/window/cache plus transactional insert/update/delete, matrix maintenance, bulk loading,
  resampling policy, wrap, no-data/alpha/color-space behavior, and atomic renderer publication.
- Bound matrices/levels/tiles/blobs/decoded pixels/cache/requests/concurrency/output/owned bytes and work.

## Tiled gridded coverage contract

- Implement Tiled Gridded Coverage Data 1.1 package/table/tile ancillary records, datatype, scale, offset,
  precision, null, grid-cell encoding, bounds, units and producer metadata.
- Decode/write supported integer and floating coverage tiles through neutral raster/elevation values while
  retaining raw/sample semantics and distinguishing null from valid extrema/NaN policy.
- Validate coverage/tile matrix relationships, ancillary cardinality, tile codecs, endian/sample layout,
  seam/edge behavior, pyramids, statistics, and transactional updates before publication/commit.
- Do not treat generic image tiles or private terrain encodings as standard coverage without declarations.

## Official extension and unknown-extension contract

Built-in read/write support covers the GeoPackage 1.4 registered extension inventory:

- non-linear geometry types;
- RTree spatial indexes;
- zoom other intervals;
- WebP tile encoding;
- metadata;
- schema/data-column constraints;
- WKT for coordinate reference systems;
- Tiled Gridded Coverage Data; and
- Related Tables.

Deprecated geometry/SRS triggers and user-defined geometry extensions are recognized for diagnostics and
preservation but are not emitted. Unknown extensions retain their declarations and untouched schema/data.
Package/table/column mutations are checked prospectively against extension scope. Explicit extension codecs
are immutable, name/version/scope/media-checked, work-bounded, and registered directly—never discovered.

## Community vector-tile profile

- The profile is opt-in and named independently from GeoPackage 1.4. It recognizes only the frozen OGC
  Vector Tiles Pilot extension declarations and compatible Releasable Basemap Tiles conventions selected by
  its task; it never infers a profile from `tile_data` bytes.
- Support the generic vector-tile tables/layer/field metadata, MVT 2.1 tiles, GeoJSON tiles, optional deflate,
  and feature/attribute correlation through Related Tables with exact identity and schema semantics.
- Reuse the shared bounded MVT/GeoJSON and tile-matrix implementations. Read/write/interoperability evidence
  and diagnostics are reported separately from the standard conformance matrix.

## Community styling and symbology profile

- The profile is opt-in and named independently. It reads/writes style, symbol, resource, association, MIME,
  default/order and inheritance records required by the frozen community extension revision.
- Typed codecs integrate the project's SE/SLD and MapLibre style values plus SVG and raster symbol resources.
  Unknown media remains bounded opaque data; it is not parsed, executed, or silently selected.
- Table/layer/feature relationships, missing resources, cycles, conflicts, updates and deletion are
  transactional. A stored style does not grant external-resource authority.
- QGIS project/map styling storage, arbitrary QML execution, OWS Context, 3D Tiles, semantic annotations,
  generalized/index packages and other community extensions are not claimed; they remain unknown-extension data.

## Builder and transactional editing contract

- A builder creates a new package, feature/attribute/tile/coverage tables, CRS/content/extension metadata,
  indexes, relationships and optional profiles with safe standards-compliant defaults. Callers override only
  explicit typed fields; required derived metadata is computed consistently.
- Read-write sessions expose explicit transactions and nested savepoints. Feature/attribute/tile/coverage/
  metadata/relation/style CRUD, schema changes, bulk operations and index maintenance are atomic and validated.
- Define one writer lane, connection/thread ownership, busy timeout/retry, read isolation, WAL/journal/synchronous
  policy, cancellation/interrupt, commit/rollback, disk-full/I/O/corruption recovery, close aggregation and locks.
- Existing packages are never silently repaired or migrated. Validation can propose a bounded repair plan;
  applying it is an explicit transactional operation with before/after evidence and backup policy.
- Unknown extension scopes are checked before schema/data mutation. Unrelated unknown objects survive exactly;
  a governed mutation requires a registered codec or fails before opening a write transaction.

## Deliberate exclusions

- Arbitrary high-level SQL, SQL/script execution from package metadata, triggers outside frozen reviewed templates,
  extension/driver discovery, transparent database repair, encryption/SQLCipher, remote SQLite files, and syncing.
- Built-in proprietary codecs, QGIS project extension, OWS Context, 3D Tiles, generalized/index GeoPackages,
  semantic annotations, delta-update profiles, and any community extension not separately approved.
- Claiming community vector/style profiles as GeoPackage 1.4 conformance.

## Completion evidence

- Map every applicable GeoPackage 1.4 requirement and official extension requirement to reader, writer,
  update, validation, or explicit non-applicability evidence. Run the OGC executable test suite where applicable.
- Use provenance-recorded files from GDAL, QGIS, Esri/NGA/community producers and independent consumers,
  including core/options/extensions, big/little endian geometry, mixed tiles, coverage and relation cases.
- Test malformed/corrupt/hostile databases, schema/identifier/SQL injection, limits, cancellation, locking,
  concurrency, disk-full/crash/rollback, unknown preservation, resource ownership and fuzzed binaries.
- Verify the exact pinned Xerial/native binaries, checksums/licenses and supported JDK/OS/architecture matrix,
  plus offline repository, publication, Javadocs, examples, AWT/Vaadin and native deployment constraints.
- G19-159 closes the module only when this matrix, implementation, conformance/community evidence, public
  wording, dependencies and diagnostics agree.
