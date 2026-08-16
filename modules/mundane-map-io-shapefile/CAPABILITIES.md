# Shapefile adapter capability intent

`mundane-map-io-shapefile` is a bounded toolkit-neutral reader and, as its approved G19 target, a
create-new-dataset exporter for common ESRI Shapefile interchange. The exporter exists so callers
can deliver project features to established desktop, field, and government GIS workflows.

The module is not intended to be a general dBASE database engine or an in-place Shapefile editor.
Export creates one new coordinated dataset and either commits every required component or publishes
nothing. Silent geometry, ordinate, attribute, encoding, or CRS loss is not permitted.

The root README describes released behavior. The target columns below become release claims only as
their task cards close.

## Standards and role

| Surface | Normative/interchange baseline | Released role | Approved target | Deliberate exclusions |
| --- | --- | --- | --- | --- |
| SHP/SHX | ESRI Shapefile Technical Description, July 1998 | Bounded 2D reader | Complete standard shape-code reader plus deterministic new-dataset encoder | In-place record mutation, proprietary spatial-index sidecars, and files beyond the declared compatibility ceiling |
| DBF | Shapefile Technical Description plus the pinned common dBASE III/IV profiles | Bounded common scalar reader | Broader bounded reader plus portable new-dataset DBF encoder | General database operations, executable memo content, arbitrary FoxPro extensions, and memo emission |
| CPG | Common Shapefile code-page sidecar convention | Explicit bounded encoding selection | Deterministic read precedence and an emitted encoding declaration | Encoding guesses and silent replacement of unmappable text |
| PRJ | Pinned OGC/ESRI WKT 1 dialects | Retain definitions and recognize a narrow CRS pair | Common WKT 1 parsing and deterministic representable CRS export | Heuristic CRS selection and lossy export of an unrepresentable CRS |
| Dataset publication | JDK filesystem contracts | Read only | Temp-set construction, validation, coordinated commit, rollback, cancellation, and cleanup | Partial publication and an in-place update API |

## Geometry matrix

| Shape family/code | Released reader | Target reader | Target exporter | Card |
| --- | --- | --- | --- | --- |
| Null Shape (0) | Supported | Supported in otherwise homogeneous datasets | Supported for null geometry records | G19-034 |
| Point (1), PolyLine (3), Polygon (5), MultiPoint (8) | Supported | Complete record, bounds, parts, and ring semantics | Supported for one homogeneous non-null family per dataset | G19-031, G19-034 |
| PointZ (11), PolyLineZ (13), PolygonZ (15), MultiPointZ (18) | Rejected | Preserve required Z and optional M arrays/ranges | Supported when the neutral geometry retains required ordinates | G19-030, G19-034 |
| PointM (21), PolyLineM (23), PolygonM (25), MultiPointM (28) | Rejected | Preserve M values and the standard no-data sentinel semantics | Supported when the export ordinate policy is satisfied | G19-030, G19-034 |
| MultiPatch (31) | Rejected | Preserve part types, Z, optional M, and explicit surface semantics | Supported only through an approved lossless neutral representation | G19-031, G19-034 |

The file header and every non-null record must use the dataset's one declared shape family. The
exporter rejects heterogeneous input rather than silently splitting or coercing it.

## Attribute and sidecar matrix

| Area | Released reader | Approved target | Export policy | Card |
| --- | --- | --- | --- | --- |
| DBF structure | Versions `0x03`, `0x04`, and `0x05`; bounded descriptors/rows | Pin common dBASE III/IV header, deletion, terminator, width, decimal, and date rules | Emit one portable pinned DBF version with one row per SHP record | G19-032, G19-035 |
| Scalar fields | `C`, `N`, `F`, `L`, `D` | Complete approved common scalar/date-time/numeric matrix with explicit null semantics | Strict schema mapping; reject names, widths, scales, types, or values that cannot be represented | G19-032, G19-035 |
| Memo fields | Unsupported | Bounded read support for the approved DBT memo variants | Deliberately not emitted by the portable exporter | G19-032 |
| Text encoding | Selected CPG/language-driver subset | Pinned CPG/language-driver precedence and common code-page inventory | Emit a CPG declaration; reject unmappable values rather than replacing them | G19-032, G19-035 |
| CRS | Retained PRJ with narrow EPSG recognition | Bounded common OGC/ESRI WKT 1 trees integrated with the core CRS catalog | Emit the selected pinned WKT 1 dialect only for a losslessly representable CRS | G19-033, G19-035 |
| Optional indexes/metadata | SHX only | SHX remains required for exported datasets | Do not emit `.sbn/.sbx`, `.qix`, `.ain/.aih`, or vendor XML metadata | G19-036 |

## Export contract

- Export creates a new basename set: `.shp`, `.shx`, `.dbf`, and, when applicable, `.cpg` and
  `.prj`. It never modifies individual records in an existing set.
- A preflight validates geometry homogeneity, dimensionality, schema mapping, encodability, CRS
  representability, record counts, 16-bit-word offsets, and the project compatibility byte ceiling.
- Files are written to a private temporary set, flushed, reopened through the reader for structural
  verification, and committed as one recoverable operation where the filesystem permits.
- Existing targets require an explicit replacement policy. Any failure or cancellation leaves the
  previous dataset intact and removes the temporary set.
- Round-trip claims cover declared Shapefile semantics, not information the format cannot represent.
  Lossy conversion requires a separate future design decision rather than an implicit option.

G19-036 closes the module only after an external Shapefile implementation reads exported corpora and
the supported/unsupported matrix agrees with package Javadocs and the project support documentation.
