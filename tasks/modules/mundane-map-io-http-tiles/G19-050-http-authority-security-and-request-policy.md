# G19-050 — HTTP authority, security, and request policy

Status: Proposed
Depends on: G18-061
Gate: G19
Type: HITL

## Goal

Define a production HTTP request boundary with explicit authorities, credentials, redirects, TLS,
proxy configuration, timeouts, headers, and secret-safe diagnostics.

## Context

The current tile client supports one fixed authority, one request attempt, no redirects, no caller
credentials, and JDK-default TLS/proxy behavior. Service metadata adapters need a shared policy that
does not turn link traversal into ambient network access.

## Scope

- Pin applicable RFC 9110 request, URI, redirect, authentication, and response semantics.
- Replace the single fixed host with an immutable bounded authority allowlist while preserving the
  current fixed-host constructor as the simplest path.
- Add explicit caller-provided scoped credentials, headers, user-agent, TLS policy, and proxy
  configuration without reading browser, desktop, environment, or default authenticator secrets.
- Define same-origin/cross-origin redirect rules, maximum hops, method preservation, downgrade,
  authority changes, header stripping, and loop detection.
- Bound connect/request/body-idle timeouts, headers, URI/template characters, authorities, redirects,
  concurrent requests, and response-body ownership with cancellation-aware cleanup.
- Redact secrets, query values, credentials, and attacker text from closed stable diagnostics.
- Expose an explicit bounded transport session suitable for trusted metadata adapters without
  exposing a raw unrestricted `HttpClient` or allowing adapters to bypass the same policy.

## Out of scope

- HTTP caching/retry policy, assigned to G19-051; ambient proxy/credential discovery; arbitrary
  redirects; cookies; browser session import; or trust-all TLS modes.

## Acceptance criteria

- Every request and redirect remains within caller-approved scheme/authority/TLS/credential policy.
- Sensitive headers are never forwarded to a newly encountered authority or included in diagnostics.
- Cancellation, timeout, redirect, proxy, TLS, authentication, and response failures close every
  request/body/worker resource and remain within exact work ceilings.

## Required tests

- Scripted origin/cross-origin redirect, downgrade, loop, hop, authentication challenge, proxy, TLS,
  timeout, header, cancellation, and connection cleanup tests.
- SSRF, DNS/authority normalization, credential/header leakage, URI confusion, oversized headers,
  duplicate security headers, hostile diagnostics, concurrency, and lifecycle tests.

## Validation

Run `./gradlew :modules:mundane-map-io-http-tiles:check --console=plain`, its scripted network fixture
lane, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer/security reviewer approves the authority, redirect, credential, TLS,
proxy, timeout, and redaction profile before completion.
