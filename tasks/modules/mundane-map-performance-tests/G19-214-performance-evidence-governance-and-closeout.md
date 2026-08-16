# G19-214 — Performance evidence governance and closeout

Status: Proposed
Depends on: G19-213
Gate: G19
Type: HITL

## Goal

Publish reproducible, accurately interpreted performance evidence without maintaining timing baselines or making scores release-blocking.

## Context

The project has deliberately rejected dedicated benchmark hardware, portable timing thresholds, and statistical performance-regression gates. Performance work remains useful for engineering investigation only when the measured work is correct, bounded, reproducible, and clearly labeled informational.

## Scope

- Run bounded PR smoke for harness configuration, scenario/parameter coverage, semantic oracles, work consumption, limits, result schemas, and exact resource closure.
- Run full JMH and integration evidence manually or on a schedule to inform optimization and capacity decisions.
- Record raw samples, summary statistics, allocations/GC, logical work, complete environment metadata, and fixture/toolchain provenance.
- Reject missing, malformed, incomparable, or misleading evidence, while never failing a gate merely because a timing/allocation/memory score changed.
- Keep different hardware, JVM, browser, native, filesystem, and virtualization results in separate named profiles and explicitly informational.
- Complete an independent methodology, coverage, reproducibility, dependency, documentation, and interpretation audit.

## Out of scope

- Dedicated baseline hardware, timing/allocation/memory regression thresholds, statistical release gates, baseline rebasing, performance waivers, service-level objectives, or marketing comparisons.

## Acceptance criteria

- CI detects broken/skipped/misconfigured benchmarks, incorrect or optimized-away work, unbounded scenarios, invalid evidence, and resource leaks without comparing performance scores to pass/fail thresholds.
- Every result states that it is environment-specific informational evidence and cannot be compared across incompatible profiles.
- Documentation contains no implied throughput, latency, allocation, memory, or scalability guarantee.

## Required tests

- Synthetic skipped/misconfigured/incorrect/elided/unbounded/leaking benchmark and invalid/incomparable result-bundle failures.
- Clean connected/offline evidence runs plus independent source/configuration/result/documentation review.

## Validation

Run the JMH smoke and full informational suites, `./gradlew performanceEvidence offlineRepositoryVerification --console=plain`, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: approve the methodology, coverage, evidence provenance, and non-normative performance wording.
