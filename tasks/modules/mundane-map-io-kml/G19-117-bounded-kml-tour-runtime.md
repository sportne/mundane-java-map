# G19-117 — Bounded KML Tour runtime

Status: Proposed
Depends on: G19-112, G19-116
Gate: G19
Type: HITL

## Goal

Model and execute KML 2.3 tours through a deterministic host-neutral timeline with explicit application
authority over playback, camera changes, temporary updates, and sound cues.

## Context

Tour is standard KML 2.3 behavior. Data-only parsing would not provide its primary utility, while automatic
playback/media/network behavior would violate application authority and bounded lifecycle requirements.

## Scope

- Model Tour, Playlist, FlyTo/duration/mode/view, Wait, TourControl/pause, AnimatedUpdate/delayed start,
  SoundCue/href/delayed start, and standard extension points/order/assertions.
- Add an explicit start/pause/resume/stop/seek-if-approved coordinator with deterministic clock/scheduler,
  interpolation, temporary update/revert behavior, document/generation coupling, and terminal states.
- Deliver camera/viewport and sound-cue events to registered host handlers; sound resources use explicit catalogs/
  network authority and never autoplay or invoke a platform media implementation implicitly.
- Bound tours, playlist entries, duration/delays, concurrent cues, update work, interpolations/ticks, resources,
  retained snapshots, and callbacks; make cancel/detach/close/failure cleanup exception-safe.
- Define AWT/Vaadin adapter parity, user interruption, active-tool interaction, accessibility/reduced-motion policy.

## Out of scope

- Automatic playback on open, bundled audio decoder/player, arbitrary media, video, or a general animation engine.

## Acceptance criteria

- Approved playlists produce deterministic ordered camera/update/cue events and correct pause/resume/cancel/revert behavior.
- Stale document/viewport generations, handler failures, limits, and teardown cannot leave temporary state or timers active.
- AWT/Vaadin hosts observe equivalent timelines under the same controlled clock and accessibility options.

## Required tests

- Every tour primitive/order/interpolation/delay/update/cue/pause/resume/cancel/revert combination with fake clocks/handlers.
- Huge/negative/non-finite durations, event/resource limits, stale generations, handler failures, lifecycle and parity evidence.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, tour/rendering/accessibility lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves timeline/interpolation, reduced-motion/media policy, and observed playback.
