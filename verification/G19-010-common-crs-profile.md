# G19-010 common CRS and WKT2 profile

Reviewed: 2026-08-16

## Frozen profile

- Syntax edition: WKT2:2019. Accepted roots are `GEOGCRS`/`GEODCRS`, `PROJCRS`, `VERTCRS`, and
  `COMPOUNDCRS`; the public Javadocs enumerate the accepted semantic nodes.
- Retained metadata: authority identifier, datum, ellipsoid, axes, explicit order/direction,
  angular/linear units, projected base identifier, conversion method/parameters, and ordered
  compound components.
- Executable methods: ellipsoidal **Mercator (variant A)** and **Transverse Mercator**. Direct
  operations require equal datum/ellipsoid metadata and an exact projected base identifier.
- Metadata-only definitions include EPSG:3857 Pseudo-Mercator, EPSG:5703 NAVD88 height, EPSG:4979
  three-dimensional WGS 84, and the reviewed NAD83 + NAVD88 compound profile.
- Unsupported methods, grids, datum shifts, vertical/3D/compound operations, and implicit operation
  chains fail explicitly. The implementation does not download data or invoke JNI/PROJ.

## Catalog provenance and reproduction

The reviewed subset is derived from the IOGP EPSG Geodetic Parameter Dataset v12.054, released
2026-03-18. `modules/mundane-map-core/src/catalog/common-crs-catalog.tsv` retains the source URL,
terms URL, attribution notice, selected identifiers, and semantic role. The catalog source SHA-256
is `f91b37010154184f80b845f101839f71780d248311d112a27ae7fb5d8a38afe9`.

`./gradlew :modules:mundane-map-core:verifyCommonCrsCatalog --console=plain` checks the reviewed
source byte-for-byte. `CommonCrsCatalog.identifiers()` is tested against the source order and every
semantic definition must pass canonical `Wkt2.write`/`Wkt2.parse` equality. Catalog changes require
updating the attributed source, checksum, explicit Java construction, control points, and review in
one change. Runtime database/network discovery is forbidden.

The subset is redistributed under the EPSG Dataset Terms of Use. The retained notice states that it
contains an attributed, modified subset, is supplied as-is, and does not imply IOGP endorsement.

## Evidence and tolerances

- The dedicated `crsCorpus` lane parses reviewed PRJ, GeoTIFF, and GeoPackage-style WKT fixtures,
  canonical-round-trips every catalog entry, and exercises malformed, unsupported, overlong, and
  over-deep input.
- Control points cover World Mercator, WGS 84 UTM, NAD83 UTM, and British National Grid. Projected
  metre comparisons use a maximum `0.02 m` tolerance for the independent non-origin UTM control;
  exact-origin checks use `1e-6 m` or tighter. Inverse angular comparisons use `1e-7 degree` or
  tighter.
- Axis tests vary tuple order, positive direction, degrees, metres, and feet independently from the
  library's longitude/latitude presentation convention.
- Parser limits are 16,384 characters, depth 32, and 4,096 values. Atomic transform batches are
  capped at 1,000,000 coordinate tuples.
