# G10-062 — HTTP tile hardening and JVM closeout

Status: Proposed
Depends on: G10-061
Gate: G10
Type: AFK

## Goal

Close the HTTP XYZ adapter's hostile-response, resource, concurrency, cancellation, and rollback
matrices and document its exact JVM-only support boundary.

## Context

G10-061 completes useful region acquisition and caching. G10-006 defines the complete status/header/
body profile, conservative allocation accounting, bounded drain/poison behavior, deadlines,
interruption, close arbitration, diagnostic precedence, and explicit absence of a Native Image claim.

## Scope

Enforce every template, region, concurrency, header, body, output, owned-byte, warning, cache, and
duration limit; complete status/media/encoding/length validation and custom back-pressured body
handling; and close request/operation deadline, token cancellation, interruption, close-in-flight,
failed-batch drain, poisoning, cleanup, image-error translation, and cache rollback behavior. Document
the bounded Java 21 JVM-only support statement, record the exact tested environments as evidence, and
audit architecture/prohibited mechanisms without narrowing the public statement to one OS.

## Out of scope

Native Image support, public-network or latency claims, retries, redirects, authentication, proxies,
custom headers, cookies, compression, disk caches, offline synchronization, vector tiles, WMS/WMTS,
and external HTTP libraries.

## Acceptance criteria

- Exact limits succeed and one-over/overflow fail before request, allocation, cache mutation, or source
  publication; conservative reservations are released on every terminal path.
- Every status/header/body/decode/deadline/cancellation/interruption/close outcome has the approved
  stable nonsensitive code, context, precedence, and bounded cleanup behavior.
- A failed fetch never commits pixels, warnings, or cache mutations, and a batch that cannot drain
  within the bound permanently closes the client without overlapping later work.
- Documentation and tests make the Java 21 JVM-only support boundary explicit and add no Native Image,
  public-service, security, or performance claim not supported by evidence.

## Required tests

All status families; repeated/oversized/malformed headers; chunked exact/over/truncated bodies;
malformed/wrong-size images; exact/one-over limits and checked overflow; request/operation deadlines;
token cancellation at each stage; thread interruption; close races; drain poisoning; cache rollback;
diagnostic redaction/precedence; lifecycle; and architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-http-tiles:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The project does not claim Native Image support for JDK HTTP/TLS/executor behavior in this task. A
separate evidence task is required before adding this module to the shared native executable.
