# G18-052 — Vaadin viewer upload and export closeout

Status: Complete
Depends on: G18-051
Gate: G18
Type: HITL

## Goal

Complete the example with guarded browser uploads, existing SVG export/download, production build
instructions, and the final viewer usability/security review.

## Scope

- Add bounded multi-file upload staging with sanitized server identities, exact sidecar grouping,
  guarded paths, cancellation, per-UI ownership, and deterministic cleanup.
- Add SVG export/download through the existing vector snapshot and SVG encoder.
- Document production build/run, server-local versus uploaded files, security/session ownership,
  upload and deployment limits, and unsupported content.

## Out of scope

Accounts/authorization, virus scanning, production quota policy, remote object storage, databases,
cloud deployment automation, or browser-side SVG generation.

## Acceptance criteria

- Upload names and client paths cannot escape the fresh staging root or trigger implicit scanning;
  sidecar, byte/count, cancellation, failure, expiry, and cleanup behavior is deterministic.
- SVG download retains accepted export behavior and clearly reports non-representable content.
- The README makes no production security, scalability, pixel, or map-data claim not supported by
  G18 evidence.
- Responsive layout, keyboard focus, route removal, and session expiry leak no uploads, resources,
  sources, listeners, or temporary files.

## Required tests

Upload path/name/sidecar/limit/cancellation/cleanup cases; export success and representability;
download/resource expiry; production build configuration; detach and session close.

## Validation

```bash
./gradlew :examples:vaadin-viewer:check --console=plain
./gradlew :examples:vaadin-viewer:bootRun
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **complete Vaadin viewer upload security, SVG export, deployment wording, responsive
layout, keyboard access, visual fidelity, and cleanup review**. Stop `bootRun` after review.

## Completion record

- Browser multipart requests stream into a fresh route-owned root under closed 8-file/16-MiB-file/
  32-MiB-request and 32-file/64-MiB-session ceilings. Leaf-name validation, exact byte lengths,
  same-stem shapefile sidecars, server-generated identities, prospective commit, cancellation, and
  failure cleanup precede the existing asynchronous source-opening boundary.
- The existing acknowledged vector capture and canonical SVG encoder feed a route-scoped five-minute
  download with exact length, `nosniff`, sandbox, and private `no-store` headers. Pending,
  non-representable, expired, detached, and closed cases publish no partial document and retain the
  existing stable diagnostics.
- Focused tests cover hostile names/paths, sidecars, duplicate/type/byte/count/session limits,
  cancellation, real uploaded shapefile opening, response value hygiene, canonical export bytes,
  defensive ownership, expiry, headers, detach/session/application cleanup, and production profile
  wiring. The viewer README records launch/build commands, exact limits, ownership, unsupported
  content, and deployment exclusions without a security, scale, or pixel-identity claim.
