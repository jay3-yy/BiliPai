# BiliPai Project Guide

This repository is a multi-module Android app built with Kotlin, Jetpack Compose, and Gradle Kotlin DSL.
Use this file as the project-specific overlay on top of the global Codex/OMX guidance.

## Project shape

- `app/`: main Android application, feature UI, player, navigation, ViewModels, policies, tests.
- `settings-core/`: reusable settings and store logic shared by app features.
- `network-core/`: network policy and lower-level networking support.
- `baselineprofile/`: macrobenchmark and baseline profile generation for startup and frame timing work.

## Working defaults

- Prefer small, targeted changes over broad rewrites.
- Preserve the existing app visual language unless the task explicitly asks for redesign.
- Reuse the repository's existing `Policy`, `UseCase`, `ViewModel`, and feature package patterns before creating new abstractions.
- Avoid adding business logic to [`MainActivity.kt`](/Users/yiyang/Desktop/BiliPai/app/src/main/java/com/android/purebilibili/MainActivity.kt) unless the behavior truly belongs to app shell, deep link routing, or top-level playback orchestration.
- Do not add new dependencies unless the user explicitly asks for one.
- Do not run full package, APK packaging, bundle, install, or release-smoke verification paths unless the user explicitly asks for them.
- After each meaningful completed slice, commit and push the changes so progress is easy to roll back and resume.
- Do not add `Co-Authored-By: Cursor`, `Co-Authored-By: Claude`, `Co-Authored-By: Codex`, `Made-with: Cursor`, or similar AI tool attribution to commit messages or PR descriptions.

## Android and Compose conventions

- Prefer state hoisting: screen composables consume immutable UI state and lambda events; do not pass ViewModels deep into leaf composables.
- Keep UI behavior testable in plain Kotlin where possible by extracting layout, visual, or routing decisions into small policy classes.
- Follow existing Material 3 and adaptive layout patterns already used in the app.
- For UI changes, check dark theme, tablet or large-screen behavior, and minimum 48dp touch targets.
- Avoid expensive work during composition. Use `remember`, `derivedStateOf`, and stable state models where it reduces recomposition churn.
- For animations, haze, blur, player overlay, or scrolling changes, prefer the lightest effect that preserves smoothness on real devices.

## Module boundaries

- Put reusable settings or preference-domain logic in `settings-core` when it is not app-screen-specific.
- Put shared network or fallback behavior in `network-core`, not scattered through feature UI code.
- Keep screen-specific orchestration in feature packages under `app/src/main/java/com/android/purebilibili/feature/...`.
- When a change affects startup, feed rendering, video detail performance, or scroll smoothness, consider whether `baselineprofile/` needs updates or verification.

## UI efficiency workflow

- For UI and UX tasks, inspect the nearest existing screen, component, and policy tests before changing code.
- Prefer updating an existing component or policy over introducing a parallel UI path.
- If the change is primarily visual, add or update focused policy tests first when there is already a nearby test pattern.
- If the change is interaction-heavy, verify both gesture behavior and fallback behavior for narrow screens or disabled states.

## Verification ladder

Pick the smallest command set that proves the change:

- Targeted unit tests for the touched feature:
  `./gradlew :app:testDebugUnitTest --tests '<ExactTestName>'`
- Lightweight compile validation for touched Kotlin/Compose code:
  `./gradlew :app:compileDebugKotlin`
- Broad local regression for app logic and UI policies:
  `./gradlew :app:testDebugUnitTest`
- Static checks for Android resources and code:
  `./gradlew :app:lintDebug`
- Build validation for packaging and manifest/resource regressions:
  `./gradlew :app:assembleDebug`
- Do not run full package/build validation such as `./gradlew :app:assembleDebug`, `assemble*`, `bundle*`, `install*`, APK packaging, or release smoke gates unless the user explicitly asks for it. Prefer targeted unit tests, strategy/policy tests, `:app:compileDebugKotlin`, lint, or narrow device checks that match the changed surface.

Use extra verification when the task touches these areas:

- App startup, feed scroll, frame pacing, or rendering:
  `./gradlew :baselineprofile:connectedReleaseAndroidTest`
  or project perf scripts in `scripts/`
- Device smoke or release safety:
  `scripts/release_smoke_gate.sh`
- On-device perf snapshots:
  `scripts/mobile_perf_collect.sh`
  or `scripts/tablet_perf_collect.sh`

## Verification hygiene

- Treat Kotlin daemon, incremental compilation backup, temp-file, or configuration-cache infrastructure errors as build-environment failures first, not product regressions.
- If a Gradle/Kotlin task hits daemon or incremental-compilation file-state errors, stop passive polling immediately and switch to a deterministic fallback such as `--no-daemon` or another clean one-shot verification path.
- Do not keep spinning on long terminal polls once the failure mode is clearly infrastructure-related; report that distinction explicitly and choose the next verification step with the lowest ambiguity.

## ADB and local device flow

- Confirm a device first: `adb devices`
- Install the debug app: `./gradlew :app:installDebug`
- Launch from shell when needed:
  `adb shell am start -n com.android.purebilibili.debug/com.android.purebilibili.MainActivity`
- If debug test APKs are required, install them explicitly:
  `./gradlew :app:installDebugAndroidTest`

## Change-specific expectations

- Settings UI changes should preserve searchability, icon clarity, and tablet layout behavior.
- Video or player UI changes should consider overlay layering, gesture conflicts, lifecycle resume/pause behavior, and PIP or mini-player side effects.
- Home/feed UI changes should consider frame timing, placeholder behavior, and list stability.
- Build-file changes should keep AGP, Kotlin, Compose, and test dependency versions aligned with the current project direction.

## Skills that are especially useful here

- Use `android-native-dev` for Android build, Gradle, Material 3, accessibility, and troubleshooting guidance.
- Use `android-jetpack-compose-expert` for Compose architecture, state management, navigation, and recomposition/performance guidance.
- Use OMX `explore`, `debugger`, `architect`, and `verifier` roles for large Android tasks before making sweeping edits.

## Token-aware OMX defaults

- Do not call or rely on oh-my-codex or OMX workflows, modes, agents, or skills by default in this repository.
- Only use oh-my-codex or OMX when the user explicitly asks for it in the current conversation.
- When multiple valid workflows exist, prefer the lowest-overhead path that still preserves correctness.
- For simple repository questions, local code lookup, and small targeted edits, prefer direct execution or lightweight read-only exploration before broader orchestration.
- Treat `ralph`, `team`, `ultrawork`, `plan`, `deep-interview`, and other multi-phase workflow skills as high-overhead options. Use them only when the user explicitly asks for them or when the task truly needs planning, persistent retries, or parallel lanes.
- Prefer domain-specific helpers such as `android-native-dev`, `android-jetpack-compose-expert`, `debugger`, `explore`, and `verifier` before generic orchestration skills.
- For OMX or skill usage questions, answer directly unless a workflow skill is clearly necessary to complete a concrete configuration or implementation task.
