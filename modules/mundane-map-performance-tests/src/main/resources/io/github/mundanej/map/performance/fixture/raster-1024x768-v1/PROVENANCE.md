# Raster performance fixture provenance

These repository-authored fixtures were generated on Java 21 by the checked
`AuthorRasterResources` support helper. They contain no third-party data.

- `evidence.png`: 1,024 by 768 RGBA; pixel `(x,y)` is
  `(x&255,y&255,(x^y)&255,((x+y)%5==0?128:255))`.
- `evidence.jpg`: 1,024 by 768 RGB; each 64-pixel tile is
  `((17*tileX+3*tileY)&255,(5*tileX+19*tileY)&255,(11*tileX+7*tileY)&255)`.
- World files contain exactly `2, 0.25, 0.5, -2, 1000, 2000`, one value per LF line.

Authoritative Java 21 output:

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `evidence.png` | 1,178,082 | `1d7a32d6c8901637d684f6061e1e0563243a118e4b813f94fc7985460a4050a2` |
| `evidence.jpg` | 14,106 | `5afcf4a3fbaf2c4b03f2a12929afc4a3c96b7faee378778e3b4a4dd013d7c731` |
