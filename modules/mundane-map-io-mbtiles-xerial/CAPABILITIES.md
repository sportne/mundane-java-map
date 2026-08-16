# MBTiles adapter capability intent

`mundane-map-io-mbtiles-xerial` is the optional Xerial SQLite adapter for bounded direct use of one
MBTiles tileset per file. The target is complete MBTiles 1.3 read, create, and transactional update,
including raster/raw tiles, Mapbox Vector Tile 2.1 payloads, and the optional UTFGrid 1.3 storage and
interaction profile.

The normative baselines are MBTiles 1.3, Mapbox Vector Tile 2.1, and UTFGrid 1.3. MBTiles defines a
logical SQLite interface, not one required physical schema. Every valid compatible table/view layout is
readable. Mutation is limited to exact recognized writable layouts; another valid layout remains
read-only and can be transactionally rewritten into a canonical project layout on explicit request.

The module uses the repository's pinned Xerial SQLite JDBC graph and directly constructed Jackson Core
3.1.5 parsers/generators for bounded JSON. Neither dependency leaks through public project contracts.
WebP decoding is available only when the separate optional AWT WebP adapter is registered. The root
README describes released behavior; target rows become release claims only as their G19 cards close.

## Standards and schema matrix

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| SQLite container | Read-only SQLite header and narrow schema checks | MBTiles 1.3 SQLite 3/core-only rules, UTF-8 text, optional MBTiles application ID, integrity, immutable inspection, and bounded read/write sessions | G19-160, G19-168 |
| Logical interface | Physical `metadata` and `tiles` tables only | Any safe table/view yielding the exact required logical columns and types; reject side effects, extension requirements, ambiguity, or mutation | G19-160 |
| Physical layouts | Existing flat table | Builder-selected canonical flat layout and canonical normalized map/images layout exposed through a compatible `tiles` interface | G19-167 |
| Metadata | Required raster subset | Every standard required/recommended/optional row, unknown rows, vector JSON, UTFGrid rows, exact duplicates/order/UTF-8/number/domain policy | G19-161 |
| Raw tiles | PNG/JPEG decoded only | Bounded raw retrieval for every declared standard format or IETF media type, with declaration/signature/compression facts | G19-161 |
| Raster tiles | PNG/JPEG | Complete registered PNG/JPEG and optional WebP decode/source behavior; other declared image media remains raw unless a decoder is explicitly registered | G19-162 |
| Vector tiles | Rejected | Complete gzip-wrapped MVT 2.1 read/write and neutral tile-local/map-coordinate projection | G19-163, G19-164 |
| UTFGrid | Rejected | Complete optional UTFGrid 1.3 gzip grid and JSON metadata read/write, lookup, and MBTiles `grids`/`grid_data` storage | G19-165, G19-166 |
| Creation | Unsupported | Builder-driven create-new canonical flat or normalized raster/vector/UTFGrid tileset | G19-167 |
| Update | Unsupported | Transactional CRUD for recognized writable layouts; explicit safe canonical rewrite for other read-compatible layouts | G19-168 |
| Evidence | Project raster fixtures | Standard matrices, independent producer/consumer corpora, hostile databases/payloads, platform/dependency and recovery evidence | G19-169 |

## MBTiles 1.3 container contract

- A file represents one tileset in the global-mercator profile. Stored tile coordinates use TMS row
  order; public APIs may expose an explicit XYZ view but never reinterpret stored rows implicitly.
- `metadata` and `tiles` may be tables or views. Inspection validates names, column count/order/names,
  declared/runtime SQLite types, uniqueness/cardinality, deterministic query behavior, and absence of
  required non-core extensions. Read queries are fixed, prepared, authorizer-guarded, progress-limited,
  query-only, and operate on a caller-authorized local file snapshot/session.
- Required metadata is `name` and `format`. Standard `bounds`, `center`, `minzoom`, `maxzoom`,
  `attribution`, `description`, `type`, `version`, and vector `json` receive exact typed validation.
  Unknown rows are retained as bounded ordered immutable values and round-trip unless they collide with
  governed mutations. Attribution/description/JSON are data, never executable markup or authority.
- `format` accepts `pbf`, `jpg`, `png`, `webp`, or a syntactically valid IETF media type. All tile blobs
  can be retrieved as bounded immutable media-typed bytes. Decoding is explicit and cross-checks the
  declaration, magic/compression, dimensions, and registered decoder; unknown formats are not guessed.
- Tile coordinates, zooms, TMS conversion, coverage, bounds/center, populated ranges, duplicates,
  sparse levels, missing tiles, empty tilesets, and cache/source envelopes are validated prospectively
  through the neutral tile-matrix model.

## MVT 2.1 contract

- Implement the exact MVT 2.1 protobuf wire schema directly and boundedly without generated protobuf
  runtime/discovery: layers, versions, names, extents, key/value dictionaries, all scalar value types,
  feature IDs/types/tags, unknown fields, and packed/unpacked wire forms where the schema permits them.
- Decode and encode point/multipoint, line/multiline, polygon/multipolygon command streams with cursor,
  zigzag, counts, winding, ring assignment, buffer/out-of-extent coordinates, clipping, quantization,
  validity, overflow, and stable tile/map coordinate semantics. MVT geometry collections do not exist.
- MBTiles `format=pbf` means gzip-compressed MVT. Gzip headers/trailers, concatenation policy,
  compressed/inflated bytes, protobuf fields, layers/features/dictionaries/commands/coordinates and
  aggregate work are bounded before materialization/publication.
- The required MBTiles `json` metadata models all `vector_layers` fields and optional descriptions/
  zooms plus bounded semantic `tilestats`/unknown members. Declared layers and attribute types are
  reconciled with payloads under a precise strict/inspection policy. Jackson is directly constructed;
  duplicate JSON members are rejected and no mutable/Jackson tree escapes.
- The deterministic writer orders layers/features/dictionaries/fields/commands canonically, chooses
  a documented quantization/clipping policy, emits valid gzip, and fails rather than silently losing
  geometry, identity, values, or metadata.

## UTFGrid 1.3 legacy interoperability contract

- UTFGrid support is optional within MBTiles compliance and is retained for archive/tool
  interoperability. It is not the project's modern hit-test, selection, or interaction architecture.
- Parse and write square power-of-two JSON grids, Unicode code-point ID encoding/escaping, ordered
  `keys`, optional `data`, the empty-key rule, resolution/factor lookup, and the 65,501-key/code-point
  boundary exactly. Coordinates are top-left tile pixels and integer lookup follows UTFGrid 1.3.
- MBTiles `grids.grid` stores gzip-compressed UTFGrid JSON; `grid_data` stores per-tile key names and
  JSON-object values. The adapter reconciles embedded `data` with `grid_data` deterministically,
  rejects conflicts/duplicates, and can return immutable structured values or no-data.
- Writer output is deterministic in key assignment, rows, JSON members/numbers/escaping, gzip headers,
  and `grid_data` ordering. It never evaluates data, fetches missing keys, renders HTML, or grants URL/
  script/resource authority.
- Bound compressed/inflated bytes, dimensions/cells/rows/code points/keys, JSON depth/nodes/members/
  strings/numbers, per-tile data, lookup batches, database rows, output bytes, and work.

## Builder, editing, and rewrite contract

- A builder creates a new file in either a canonical flat `tiles` table layout or a canonical
  normalized map/images layout with a compatible `tiles` view. It derives safe schema, indexes,
  application ID, metadata, zoom/bounds summaries, format, vector metadata, and UTFGrid tables while
  requiring only facts that cannot be derived without guessing.
- Read-write sessions support explicit transactions/savepoints and bounded metadata, raw tile, raster
  tile, MVT, grid, and grid-data insert/update/delete/bulk operations. They maintain unique indexes,
  summaries, dictionaries, cache generations, and integrity atomically.
- Mutation is allowed only when the exact physical schema is a recognized writable project/approved
  producer layout. An arbitrary compatible view is read-only even if its SQL looks simple; the adapter
  does not reverse-engineer triggers or synthesize update SQL.
- Explicit rewrite clones the source safely, replaces only governed MBTiles interfaces with a chosen
  canonical layout, copies validated logical content, verifies the result, and atomically installs it.
  Unknown metadata and unrelated SQLite objects/data remain untouched/preserved; an object that governs
  or depends on mutated MBTiles tables blocks the operation unless an explicit codec/policy owns it.
- Define one writer lane, connection/thread ownership, query authorizer, busy timeout/retry, WAL/journal/
  synchronous policy, cancellation/interrupt, close aggregation, file locking, disk-full/I/O/corruption,
  rollback, backup/temp/fsync/rename and recovery. Existing files are never silently repaired.

## Deliberate exclusions

- Multiple tilesets per file, non-global-mercator MBTiles claims, arbitrary SQL, SQL extensions,
  SpatiaLite, remote SQLite, encryption, synchronization, tile serving, or automatic schema repair.
- Implicit image/vector/JSON/plugin discovery, image transcoding, raster or vector retiling without an
  explicit caller plan, WebP encoding, and treating arbitrary media blobs as renderable.
- Using UTFGrid as a new application interaction foundation or executing/fetching any metadata value.
- Claiming de-facto metadata or canonical normalized layouts as normative MBTiles requirements.

## Completion evidence

- Map every MBTiles 1.3, MVT 2.1, and UTFGrid 1.3 requirement to reader, writer, update, explicit
  non-applicability, and evidence. Use official fixtures plus provenance-recorded GDAL, Tippecanoe,
  MapTiler, mbutil, MapLibre, and other independent producer/consumer databases where lawful.
- Test flat/view/normalized layouts, every metadata/media/vector/grid surface, unknown preservation,
  malformed/corrupt/hostile SQLite and payloads, SQL/schema tricks, limits, cancellation, concurrency,
  locking, disk-full/crash/rollback/recovery, and deterministic reproduction.
- Verify exact Xerial/Jackson/WebP graphs, checksums/licenses, offline repository, publication, staged
  consumers, Javadocs, examples, rendering/browser parity, and the supported JVM/OS/architecture matrix.
- G19-169 closes the module only when this matrix, implementation, evidence, dependencies, diagnostics,
  and public support wording agree.
