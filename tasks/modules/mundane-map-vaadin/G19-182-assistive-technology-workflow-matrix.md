# G19-182 — Assistive-technology workflow matrix

Status: Proposed
Depends on: G19-181
Gate: G19
Type: HITL

## Goal

Verify essential map workflows with the approved focused desktop/mobile assistive-technology combinations.

## Context

Automated accessibility and browser-engine tests do not establish behavior of real screen-reader/browser products.

## Scope

- Test current NVDA with Chrome and Firefox ESR, current JAWS with Chrome, Narrator with Edge, VoiceOver with Safari on
  macOS and iOS/iPadOS, and TalkBack with Chrome on Android.
- Verify discovery, name/role/value/state, browse/forms mode, focus, instructions, status/error announcements, spatial
  inspection alternatives, navigation, selection, measurement, editing and recovery/lifecycle workflows.
- Define announcement deduplication/order/priority, user-controlled verbosity and bounded semantic result paging.
- Archive versioned scripts, expected observations, deviations, environment details and evidence without capturing secrets.
- State all combinations outside the matrix as unclaimed rather than extrapolating support.

## Out of scope

- Every screen-reader/browser Cartesian combination, accessibility certification services, or host-page conformance.

## Acceptance criteria

- Every matrix combination completes the same named essential workflows with no unexplained blocker or state divergence.
- JAWS evidence is produced under an approved licensed human-review lane; emulation is never substituted for device evidence.
- Findings update implementation, tests, the WCAG matrix and exact public support wording before closure.

## Required tests

- Version-stamped manual/assisted product scripts plus automation that validates the semantic state supplied to each workflow.
- Rapid status, errors, paging, locale/RTL, reconnect, resize, detach/reattach, tool cancellation and resource-failure cases.

## Validation

Run the accessibility automation and complete approved product matrix, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: accessibility reviewers approve all product evidence, JAWS licensing, deviations and support wording.
