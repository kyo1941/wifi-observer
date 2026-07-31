# Repository Guidelines

## Project Structure & Module Organization

This is a Kotlin Multiplatform project. The Android app module lives in `app/`, and the platform-agnostic core plus its iOS implementations live in `shared/`.

- `shared/src/commonMain/kotlin/com/example/wifi_observer/domain/`: platform-agnostic core — `model/`, `usecase/`, and `gateway/` (the interfaces the core uses to reach the outside world).
- `shared/src/commonTest/kotlin/com/example/wifi_observer/`: unit tests for the core, plus the shared fakes in `fake/`.
- `shared/src/iosMain/kotlin/com/example/wifi_observer/platform/`: iOS implementations of the gateway interfaces (NWPathMonitor, BGTaskScheduler, UNUserNotificationCenter).
- `shared/src/iosTest/kotlin/com/example/wifi_observer/`: tests for the iOS implementations.
- `app/src/main/java/com/example/wifi_observer/`: Android app code — Compose UI, view models, the `NetworkMonitor` facade, Android platform implementations, and dependency wiring. No domain code lives here.
- `app/src/main/res/`: Android resources such as strings, themes, icons, and XML configuration.
- `app/src/androidTest/java/com/example/wifi_observer/`: instrumented Android tests.
- `docs/`: design and migration notes for background monitoring, notification flow, and KMP migration.
- `gradle/libs.versions.toml`: dependency and plugin version catalog.

The Swift/Xcode side of the iOS app does not exist yet; see the phase 4 checklist in `docs/kmp-migration-guide.md`.

## Architecture Overview

The app detects WiFi→Mobile network switches and notifies the user, continuing to monitor even after the app is task-killed via a Foreground Service. The core is shared with iOS through KMP; see `docs/kmp-migration-guide.md` and `docs/background-monitoring-design.md` for the full design and class diagram.

Core flow and the boundary that matters:

- `NetworkUseCase.observe(notificationPresenter, statusPresenter)` is the platform-agnostic core. It collects `NetworkConnectivity.observeNetworkStatus()` and pushes results outward through two Presenter interfaces — it never returns state or launches its own coroutine or `Job`. State-detection variables (`lastConnectedType`, `disconnectedTime`) live as coroutine-local variables, not instance fields.
- `NetworkMonitor` is the Facade: it implements both `NetworkNotificationPresenter` and `NetworkStatusPresenter`, exposes a `StateFlow<NetworkMonitoringStatus?>`, and delegates background start/stop to `BackgroundMonitoringService`.
- `ForegroundMonitoringService` (Android) owns the monitoring coroutine's `Job`. It guards against duplicate registration across `onStartCommand` redelivery via `observeJob?.isActive`. `ForegroundMonitoringServiceController` is the thin `BackgroundMonitoringService` implementation.
- `NetworkViewModel` observes `NetworkMonitor.status` (not the UseCase directly), converts it to UI state, and implements `NotificationPermissionPresenter`. Permission-dialog results flow back in as `NetworkUiEffect` / `NotificationPermissionRequestResult`.
- DI is manual: `AppContainer` (constructed in `WifiObserverApplication.onCreate`) wires concrete platform implementations into the common-logic classes via constructor injection. There is no DI framework.
- On iOS there is no `NetworkMonitor` equivalent. `BackgroundMonitoringServiceImpl` (iosMain) implements `NetworkNotificationPresenter` itself, because a `BGTaskScheduler` launch has no UI scene and therefore no ViewModel. The foreground path will have the Swift ViewModel implement both Presenters directly.

Key invariant: anything in `shared/src/commonMain` (model, usecase, gateway) must not reference Android or iOS APIs. Platform-specific code lives in `app/.../platform/` for Android and `shared/src/iosMain/.../platform/` for iOS. Time is injected via `kotlin.time.TimeSource` rather than `System.currentTimeMillis` to stay common-compatible and testable.

The Wifi→Mobile detection tolerates a transient `NotConnected` between Wifi and Mobile (Android's `NetworkCallback` emits `onLost` mid-switch) using a 5-second grace period keyed on `disconnectedTime`. The transition/notification table is in `docs/background-monitoring-design.md`; update it when changing notification logic.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root.

- `./gradlew assembleDebug`: build a debug APK.
- `./gradlew :shared:testAndroidHostTest`: run the core unit tests (`commonTest`) on the JVM.
- `./gradlew :shared:iosSimulatorArm64Test`: run `commonTest` plus `iosTest` against the iOS simulator target. Requires macOS with Xcode.
- `./gradlew connectedDebugAndroidTest`: run instrumented tests on a connected emulator or device.
- `./gradlew ktlintCheck`: check Kotlin formatting.
- `./gradlew ktlintFormat`: apply ktlint formatting fixes.
- `./gradlew lintDebug`: run Android lint for the debug variant.

`:app:testDebugUnitTest` exists but has no sources — the unit tests moved to `shared/` during the KMP migration.

## Coding Style & Naming Conventions

Kotlin and Gradle Kotlin DSL files use 4-space indentation and a 120-character maximum line length, as defined in `.editorconfig`. Keep files UTF-8, LF-terminated, and free of trailing whitespace.

Follow existing package organization: UI components in `components/`, platform adapters in `platform/`, models in `model/`, and presentation state in `viewmodel/`. Use PascalCase for classes, Compose functions, and enum entries; use camelCase for properties, functions, and test helpers. Prefer small, explicit interfaces for platform behavior, matching the existing `platform/interfaces` pattern.

## Testing Guidelines

Tests use `kotlin.test` and `kotlinx-coroutines-test`. Place tests for the core under `shared/src/commonTest/...` and tests for iOS implementations under `shared/src/iosTest/...`, naming test classes after the subject, for example `NetworkUseCaseTest`. Use fakes from `shared/src/commonTest/kotlin/com/example/wifi_observer/fake/` or add focused new fakes there when behavior depends on platform services. Test function names are backtick-quoted Japanese sentences describing the behavior.

Run `./gradlew ktlintCheck :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` before opening a PR. Add or update tests for notification decisions, monitoring state transitions, and coroutine timing changes.

Some iOS behavior cannot be tested from the Kotlin/Native test host, which runs outside an app bundle: `BGTaskScheduler.submitTaskRequest` always fails there, so no task request is ever created. Do not write assertions that would pass vacuously as a result; record the reason instead, as `BackgroundMonitoringServiceImplTest` does.

## Commit & Pull Request Guidelines

Recent commits use concise Conventional Commit-style prefixes such as `fix:`, `docs:`, `test:`, `build:`, `style:`, and `refactor:`. Keep commit subjects imperative and scoped to one change.

Pull requests should include a short problem summary, the implemented approach, and verification commands run. Link related issues when applicable. Include screenshots or short recordings for visible Compose UI changes, and update `docs/` when behavior or architecture changes.

## Security & Configuration Tips

Do not commit `local.properties`, keystores, generated APKs, or machine-specific SDK paths. Keep permission and foreground-service behavior aligned with `AndroidManifest.xml` and the design notes in `docs/`.
