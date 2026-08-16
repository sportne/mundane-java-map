# G19-118 — Canonical KML 2.3 and KMZ writer

Status: Proposed
Depends on: G19-114, G19-115, G19-116, G19-117
Gate: G19
Type: AFK

## Goal

Write deterministic schema-valid KML 2.3 and path-confined KMZ from every losslessly representable approved
document, resource, network declaration, update, and tour value.

## Context

The module is read-only. Writing enables portable Google Earth/earth-browser interchange, styled export,
packaged resources, version review, generated network documents, and read-modify-write workflows.

## Scope

- Add immutable writer/options builders for KML/KMZ, version/schema, encoding, IDs, extensions, markup,
  resource/catalog/package policy, compression, output, cancellation, and exact limits.
- Preflight complete object/type/version assertions, IDs/target IDs, style/reference graphs, extensions, markup,
  resources/archive paths, NetworkLink/Update/Tour declarations, representability, and output estimates.
- Emit deterministic namespaces/prefixes, generated IDs, schema/version attributes, order, numeric/date/color/
  coordinate lexical forms, escaping, UTF-8/line endings/whitespace, and byte-identical KML.
- Emit deterministic KMZ entry paths/order/timestamps/method/metadata/compression settings, `doc.kml`, deduplicated
  explicit resources, rewritten confined hrefs, and byte-identical archives where the chosen compressor permits.
- Implement bounded transactional sinks, atomic filesystem replacement, cancellation, rollback, cleanup aggregation,
  and no network/resource fetch or remote mutation as a serialization side effect.

## Out of scope

- Lossy approximation, server deployment, remote update transmission, COLLADA generation, panorama generation,
  arbitrary HTML execution, and source formatting fidelity.

## Acceptance criteria

- Every representable approved value writes valid deterministic KML/KMZ and reads back to equal semantic state.
- Non-representable/invalid/over-budget input fails in preflight; target and resource ownership remain unchanged.
- Independent KML applications open generated documents/packages without unexplained semantic/resource loss.

## Required tests

- Golden documents/packages spanning every object/style/geometry/overlay/model/link/update/tour/extension/resource mode.
- Schema/read-back/determinism, representability, Unicode/numeric/path/archive/output limits, cancellation/rollback/cleanup.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, OGC/schema/corpus/publication lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
