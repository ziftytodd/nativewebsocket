# native-websocket

A Capacitor 8 plugin providing a native websocket client for iOS and Android, with a browser fallback for local development.
Its only consumer is the Treads driver app, which installs it directly from this GitHub repo pinned to a tag or commit SHA.
Treat every public surface as a live contract with an app that is already in drivers' hands.

## Layout

| Path | What it is |
|---|---|
| `src/definitions.ts` | The public TypeScript interface. This IS the plugin's contract. |
| `src/web.ts` | Browser fallback implementation (dev only; Treads always runs native). |
| `src/index.ts` | Plugin registration. |
| `ios/Plugin/` | Swift implementation (Starscream-based). iOS deployment target 15. |
| `android/src/main/` | Java implementation (Java-WebSocket). minSdk 24. |
| `dist/` | Committed build output. Never hand-edit. |
| `README.md` | The docgen-delimited sections are generated from `src/definitions.ts` JSDoc; never hand-edit inside those markers. The surrounding prose (title, install, compatibility) is hand-maintained. |

## Build and verify

Local setup and script details are in `CONTRIBUTING.md`.
The commands that matter:

- `npm run build` - compiles TS, regenerates `dist/` and the docgen sections of `README.md`. Any `src/` change requires rerunning this and committing the regenerated output.
- `npm run lint` - ESLint + Prettier + SwiftLint.
- `npm run verify:ios` - pod install + xcodebuild.
- `npm run verify:android` - gradle build + test (the script self-locates JDK 21; do not work around a missing JDK).
- `npm run verify:web` - alias for the full `npm run build`.

Automated coverage is narrow but real where it exists.
`android/src/test/java/com/zifty/plugins/nativewebsocket/HandshakeStatusTest.java` is a genuine regression suite for the handshake-status parser: it drives real rejected upgrade responses through Java-WebSocket's own parser rather than asserting on a transcription of its wording, so a dependency bump that reworded the exception fails the build instead of silently dropping `httpStatus`. Run it with `npm run verify:android`.
Everything else is untested: the plugin classes are bridge-coupled, and the iOS test file is still a template stub.
Honest verification for a PR is therefore: the builds and lint above, that suite, plus review.
State in the PR exactly which verify commands ran; never claim runtime testing that did not happen.

## The public API is a live contract

- The Treads app string-matches the `connect()` result values `"Already Connected"`, `"Already trying to connect"`, and `"Connection Starting"`. Never change them.
- Event names (`connected`, `disconnected`, `message`) and their existing payload fields are load-bearing. Changes must be additive while any in-field Treads version depends on the old shape.
- The web implementation should keep behavioral parity with the natives where cheap, but native behavior is what ships.

## Dependency pins

- Consumer integration is **SPM-only**; there is no podspec and CocoaPods consumption is unsupported (owner decision, issue #5). The Treads app consumes this plugin through Capacitor SPM and `Package.swift`.
- The `ziftytodd/Starscream` fork revision appears in THREE committed files: `Package.swift` (SPM), `ios/Podfile` (internal only - what `verify:ios` builds against), and `Package.resolved` (the SPM lockfile). Move all of them together.
- Java-WebSocket is pinned in `android/build.gradle`.
- Verify library behavior against the pinned source, not against upstream HEAD or documentation from memory.

## Versioning and release

- Bump `version` in `package.json` in the PR that changes shipped behavior.
- After merge to `main`, a tag named exactly the package version is cut on `main` - bare `X.Y.Z` with no `v` prefix (the repo's established convention).
- Treads must always pin this plugin to a tag or SHA on `main`, never a branch. A Treads PR may point at a plugin PR branch for local testing but must not merge in that state.

## Ways of working

- Every change happens on a branch and lands via a PR. No direct commits to `main`, no self-merge.
- Cross-repo efforts (plugin + Treads, plugin + server) are contract-first, and every PR in such an effort links the governing contract in its description.
- The contract files and fleet orchestration live in the Zifty ops workspace; this is deliberately workstation-specific (`/Users/dev/zifty-server-ops` on the dev Mac Mini, where all agent work on this repo runs) and does not apply to other checkouts or CI.
- Never point tests or scratch scripts at production endpoints, and never send real traffic from a dev machine.
