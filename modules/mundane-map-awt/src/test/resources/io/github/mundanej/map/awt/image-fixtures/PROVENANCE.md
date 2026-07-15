# G6-001 image fixtures

These repository-authored fixtures are distributed under the project BSD-3-Clause license.

| Encoded fixture | Profile | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| `rgba-2x2.png.b64` | 2x2, 8-bit RGBA PNG | 79 | `b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe` |
| `rgb-regions-32x16.jpeg.b64` | 32x16, 8-bit baseline RGB JPEG | 642 | `c24dac6ae511de2680b0b66a83b003058dc3b0e150cb1fc46873243854752990` |

The Base64 payloads were generated with the OpenJDK 21 `java.desktop` PNG/JPEG writers from the
same exact pixel construction documented in `ImageIoRasterDecoderTest`: four RGBA cells for PNG and
equal red/blue rectangles for JPEG. Base64 is retained as plain source-control text; tests decode it
to a temporary file, verify the exact byte length and SHA-256, and exercise the production reader.

The decoder tests also create three deterministic derivative fixtures from those checked-in bytes.
The PNG chunks are inserted immediately after `IHDR` with a recomputed chunk CRC, and the JPEG is
formed by concatenating the checked-in payload twice:

| Generated fixture | Construction | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| ordinary-metadata PNG | `tEXt` keyword `Comment` plus 65,536 ASCII `A` bytes | 65,635 | `663833c257d714d39d115614b69e5abd9355b8524cd6fe841c9c44e4bcc7a6fd` |
| compressed-metadata PNG | `zTXt` keyword `Comment` plus one MiB of deflated ASCII `B` bytes | 1,140 | `e6231599ff14b19ac25542ab384719a3899cca4bc18396e6c50746c2391dad92` |
| APP/comment JPEG | 60,000-byte APP1 payload of ASCII `C` plus 50,000-byte COM payload of ASCII `D` | 110,650 | `7dd8446150dfeb06fbd43887a7a9e8234c6c01221251ca181228b4afeac26fb0` |
| concatenated JPEG | baseline JPEG payload repeated twice | 1,284 | `08b25bac4382e59884b14bb010d5ebcb697309bb0d1b6e422fc577bdf5acd9e7` |

These cover ordinary and compressed ancillary metadata without promising arbitrary metadata
expansion. Level 1 intentionally makes no rejection claim for trailing or concatenated JPEG data;
the test records that the explicitly selected JDK reader decodes the first image predictably.
