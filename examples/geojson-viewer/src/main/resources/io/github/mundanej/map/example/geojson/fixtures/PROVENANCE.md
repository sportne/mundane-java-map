# GeoJSON fixture provenance

All four fixtures were created for `mundane-java-map` on 2026-07-18 and are distributed under the
repository's Apache-2.0 license.

- `all-geometries.geojson` is a project-authored RFC 7946 profile fixture. It contains the six
  supported geometry families and a polygon hole. SHA-256:
  `cedc5fb33655355aad861f2effbc07460b64fb40b4d239a9febac238eef5f93f`.
- `python-json.geojson` was independently serialized with CPython 3's standard `json` module from a
  project-authored dictionary (`json.dumps(value, separators=(",", ":"))`). SHA-256:
  `b8b2ab35577dd91f6e2ebb9b873aff7c4a19a4073ae5c6a95c9f936133bb1412`.
- `unsupported-geometry-collection.geojson` and `malformed-truncated.geojson` are project-authored
  negative fixtures. Their SHA-256 digests are
  `cc8a4df519362b050e41ef61f55ca6c1a2309a25bf33ad6f6413224cfa374910` and
  `26e977e6b044673c161396ccbed5e3d70dde21889cbc54d6ee819a7f0a0d0aa7`.

The fixtures contain no third-party geographic dataset or personal data. Coordinates are synthetic
except for the illustrative Rome coordinate in `python-json.geojson`.
