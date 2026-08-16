# G19-058 — WMTS FeatureInfo and conformance closeout

Status: Proposed
Depends on: G19-056, G19-057
Gate: G19
Type: HITL

## Goal

Add bounded WMTS `GetFeatureInfo` transport and close the complete declared read-only WMTS 1.0.0
client profile with conformance and lifecycle evidence.

## Context

`GetFeatureInfo` is optional and its response formats are service-defined. The approved boundary is
to issue exact authorized KVP/REST requests and return detached media-typed bytes, not to claim a
universal feature parser. SOAP and server behavior remain deliberate exclusions.

## Scope

- Discover advertised `InfoFormat` and KVP/REST FeatureInfo bindings and add explicit selection.
- Validate tile matrix/row/column plus pixel `I`/`J` against the selected tile dimensions and matrix
  limits before network work.
- Construct exact KVP and RESTful FeatureInfo requests with the same authority, credentials, cache,
  retry, timeout, cancellation, response, and secret-redaction policies as tile retrieval.
- Return an immutable detached result containing a normalized approved media type, bounded bytes,
  source/request identity, and safe metadata; do not parse the payload implicitly.
- Define service-exception, missing/unsupported info format, empty/no-content, character/binary,
  cache, ownership, and disposal behavior.
- Complete official schema/example, CITE where available, cross-vendor, accessibility-facing metadata,
  lifecycle, performance-bound, architecture, and external-review evidence.
- Publish the module's local capability matrix and align package/root documentation with the precise
  read-only KVP/REST/GetFeatureInfo and no-SOAP/no-server boundary.

## Out of scope

- Interpreting arbitrary GML/HTML/JSON/images, executing markup, automatic format-adapter discovery,
  SOAP bindings, transactions, tile publishing, capabilities serving, or any WMTS server API.

## Acceptance criteria

- KVP/REST FeatureInfo requests and returned detached bytes/media match official and independent
  services for every declared boundary case.
- Unsupported media are still retrievable only under the explicit bounded raw-result policy and are
  never evaluated; callers choose any subsequent parser explicitly.
- The declared WMTS profile passes applicable conformance evidence, leaks no resources/secrets, and
  has no untracked common read-client gap according to an external WMTS reviewer.

## Required tests

- KVP/REST FeatureInfo parameter/template, matrix/tile/pixel bounds, dimensions/styles, info-format,
  binary/text/empty response, exception, cache/retry, detached-result, and cross-vendor fixtures.
- Markup non-execution, content-type confusion, oversized/truncated body, unauthorized endpoint,
  cancellation, concurrent close, cleanup/failure aggregation, conformance, and capability-doc tests.

## Validation

Run the WMTS/HTTP module checks, approved WMTS protocol/conformance corpus, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer and independent WMTS reviewer approve the FeatureInfo raw-result
contract, KVP/REST profile, conformance evidence, exclusions, and final support wording.
