# GeoPackage fixture provenance

The G10-040 and G10-041 tests generate their small feature databases locally with the pinned
`org.xerial:sqlite-jdbc:3.53.2.0` test runtime. The generator is the `fixture` method in
`GeoPackagesTest`; it creates only synthetic coordinates and schemas written specifically for this
project. No third-party geographic data is included.

The generated strict baseline declares GeoPackage 1.4.0, uses application ID `GPKG`, contains the
mandatory `-1`, `0`, and `4326` spatial-reference rows, and has Point and MultiPoint tables. Individual
tests deterministically mutate copies to exercise header, schema, CRS, geometry, cancellation, limit,
fingerprint, and lifecycle failures. This source-form fixture is legally redistributable under the
project license and avoids opaque binary provenance.

G10-042 adds `independent-tiles.gpkg.gz.b64`, a gzip/base64 transport of a 9,728-byte GeoPackage
created independently with CPython 3.12.3's `sqlite3` module (SQLite 3.40.1), not Xerial. It contains
one EPSG:3857, 2-by-1, zoom-1 tile matrix, one synthetic green 256-by-256 RGBA PNG, and one
intentionally missing tile. The source coordinates and pixels are project-authored and
redistributable under the repository's BSD-3-Clause license.

| Uncompressed fixture | Bytes | SHA-256 |
| --- | ---: | --- |
| `independent-tiles.gpkg` | 9,728 | `c594671796cf80a361de2be38045c12658b359bd2ededc8230649a9882dbe69a` |

The embedded PNG is 666 bytes with SHA-256
`c40787d84c643bcce0e26b1e8625ad40ea683ae54debaa9c958eccb77b07f50c`. The fixture test decodes the
transport, verifies the exact database length and digest before opening, then catalogs, reads, and
renders it through the production adapter. The reproduction recipe used Python's standard
`sqlite3`, `struct`, `zlib`, and `binascii` modules, set page size 512, wrote the strict schema and
synthetic PNG, committed, and ran `VACUUM`; it requires no network data or external geographic
content.
