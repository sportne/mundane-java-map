# MapLibre interoperability fixtures

Retrieved: 2026-07-24

These are reduced, offline fixtures derived from examples in the MapLibre documentation. They are
not complete upstream styles and are not evidence of general MapLibre compatibility.
The unmodified upstream `LICENSE.txt` retrieved from
<https://github.com/maplibre/maplibre-style-spec/blob/main/LICENSE.txt> is retained as
`LICENSE-maplibre-style-spec.txt`, including its copyright notices and conditions.

| File | Origin | Upstream license | Modifications | Expected result |
| --- | --- | --- | --- | --- |
| `camera-interpolation-supported.json` | <https://maplibre.org/maplibre-style-spec/expressions/#camera-expressions> | BSD-3-Clause | Wrapped the documented radius expression in the smallest detached v8 style, replaced remote data with a descriptive caller-owned locator, and selected a literal profile color. | Supported; one circle layer. |
| `remote-resources-rejected.json` | <https://maplibre.org/maplibre-gl-js/docs/> and <https://maplibre.org/maplibre-style-spec/root/> | BSD-3-Clause | Reduced a conventional web style to only remote sprite/glyph and vector-source requirements; replaced live hosts with reserved `.invalid` URLs. | Rejected with `MAPLIBRE_ROOT_UNSUPPORTED` at `/sprite`. |

Fixture and retained-license SHA-256 values are recorded in `manifest.properties` and verified by
`MapLibreFixtureTest`. No test performs network I/O.
