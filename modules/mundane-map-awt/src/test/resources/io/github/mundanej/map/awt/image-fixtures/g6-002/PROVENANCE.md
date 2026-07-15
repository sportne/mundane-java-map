# G6-002 affine image fixtures

These repository-authored fixtures are distributed under the project's BSD-3-Clause license. The
encoded images reuse the G6-001 Base64 payloads documented in the parent `PROVENANCE.md`; the G6-002
tests decode those exact bytes to the filename paired with each checked-in world file.

| Scenario | Encoded image | Image bytes | Image SHA-256 | World file | World-file bytes | World-file SHA-256 |
| --- | --- | ---: | --- | --- | ---: | --- |
| north-up projected PNG | `rgba-2x2.png.b64` | 79 | `b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe` | `north-up-rgba.pgw` | 19 | `aee88312dc748b7ebb8265649d86020948a80a1d00199ae38c6b8ad2d8d85ac9` |
| rotated/sheared projected PNG | `rgba-2x2.png.b64` | 79 | `b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe` | `affine-rgba.pgw` | 15 | `c79fc38d5bf1062a272505e645fb2cba5c8a59ddefbc38ede7c4e1fb9b81e2d8` |
| rotated/sheared projected JPEG | `rgb-regions-32x16.jpeg.b64` | 642 | `c24dac6ae511de2680b0b66a83b003058dc3b0e150cb1fc46873243854752990` | `projected-regions.jgw` | 21 | `ce0a9158c4e080cd655ff57d7a4e0bb19888cd77651f640321cbd00d6f4be064` |
| in-domain geographic PNG | `rgba-2x2.png.b64` | 79 | `b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe` | `geographic-rgba.pgw` | 22 | `2f04215b9625536b036b768d8bffc03c2045debd66cb34dc0361c5398ffdbbd5` |

The PNG has four distinct cells in row-major order: opaque red, half-alpha green, opaque blue, and
fully transparent. The JPEG has large red and blue half-width regions, encoded at high quality. The
world files are literal strict-US-ASCII coefficient lines in physical `A,D,B,E,C,F` order and were
authored directly; no locale-sensitive generator is involved. Tests verify every listed byte length
and SHA-256 before opening a fixture through the production image and world-file paths.
