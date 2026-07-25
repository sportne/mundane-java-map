# GeoPackage fixture provenance

The G10-040 tests generate their small databases locally with the pinned
`org.xerial:sqlite-jdbc:3.53.2.0` test runtime. The generator is the `fixture` method in
`GeoPackagesTest`; it creates only synthetic coordinates and schemas written specifically for this
project. No third-party geographic data is included.

The generated strict baseline declares GeoPackage 1.4.0, uses application ID `GPKG`, contains the
mandatory `-1`, `0`, and `4326` spatial-reference rows, and has Point and MultiPoint tables. Individual
tests deterministically mutate copies to exercise header, schema, CRS, geometry, cancellation, limit,
fingerprint, and lifecycle failures. This source-form fixture is legally redistributable under the
project license and avoids opaque binary provenance.
