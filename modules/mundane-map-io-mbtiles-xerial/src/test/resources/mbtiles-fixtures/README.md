# MBTiles fixture provenance

The ordinary adapter tests generate small synthetic MBTiles databases locally with the pinned
`org.xerial:sqlite-jdbc:3.53.2.0` test runtime. Their schemas, metadata, pixels, and coordinates were
written specifically for this project and are redistributable under the repository's
BSD-3-Clause license.

`independent.mbtiles.gz.b64` is a deterministic gzip/base64 transport of a 2,048-byte MBTiles
database created independently with CPython 3.12.3's standard `sqlite3` module (SQLite 3.40.1), not
Xerial. It uses 512-byte SQLite pages, the optional `MPBX` application ID, strict real `metadata`
and `tiles` tables, zoom 2, and one project-authored green 256-by-256 RGBA PNG at TMS coordinate
`(0,2)`. No third-party geographic data is included.

| Uncompressed fixture | Bytes | SHA-256 |
| --- | ---: | --- |
| `independent.mbtiles` | 2,048 | `de23b7b1a132fbfdd9ada38cb9aaa92366adb2d468aad7fdddd9597b8b4f4979` |

The embedded PNG is 666 bytes with SHA-256
`a8c8402738cedf28b11baddfdc5645b1882c52f72f0b782aae06e3210201e3ed`. The fixture test decodes the
transport and verifies both the exact database length and digest before opening it.

The reproduction recipe used Python's standard `sqlite3`, `struct`, `zlib`, and `binascii`
modules. It set `PRAGMA page_size=512` and `PRAGMA application_id=1297105496`, created the two
strict tables, inserted the listed project-authored metadata and PNG, committed, and ran `VACUUM`.
It requires no network data or external geographic content.
