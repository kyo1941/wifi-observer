# Repository Guidelines

## Project Structure & Module Organization

This is an Android Kotlin project using Jetpack Compose. The app module lives in `app/`.

- `app/src/main/java/com/example/wifi_observer/`: production Kotlin code, including Compose UI, view models, use cases, platform implementations, and dependency wiring.
- `app/src/main/res/`: Android resources such as strings, themes, icons, and XML configuration.
- `app/src/test/java/com/example/wifi_observer/`: local JVM unit tests and fakes.
- `app/src/androidTest/java/com/example/wifi_observer/`: instrumented Android tests.
- `docs/`: design and migration notes for background monitoring, notification flow, and KMP migration.
- `gradle/libs.versions.toml`: dependency and plugin version catalog.

## Architecture Overview

The app detects WiFi→Mobile network switches and notifies the user, continuing to monitor even after the app is task-killed via a Foreground Service. The code is deliberately layered for a future Kotlin Multiplatform (KMP) migration to iOS; see `docs/kmp-migration-guide.md` and `docs/background-monitoring-design.md` for the full design and class diagram.

Core flow and the boundary that matters:

- `NetworkUseCase.observe(notificationPresenter, statusPresenter)` is the platform-agnostic core. It collects `NetworkConnectivity.observeNetworkStatus()` and pushes results outward through two Presenter interfaces — it never returns state or launches its own coroutine or `Job`. State-detection variables (`lastConnectedType`, `disconnectedTime`) live as coroutine-local variables, not instance fields.
- `NetworkMonitor` is the Facade: it implements both `NetworkNotificationPresenter` and `NetworkStatusPresenter`, exposes a `StateFlow<NetworkMonitoringStatus?>`, and delegates background start/stop to `BackgroundMonitoringService`.
- `ForegroundMonitoringService` (Android) owns the monitoring coroutine's `Job`. It guards against duplicate registration across `onStartCommand` redelivery via `observeJob?.isActive`. `ForegroundMonitoringServiceController` is the thin `BackgroundMonitoringService` implementation.
- `NetworkViewModel` observes `NetworkMonitor.status` (not the UseCase directly), converts it to UI state, and implements `NotificationPermissionPresenter`. Permission-dialog results flow back in as `NetworkUiEffect` / `NotificationPermissionRequestResult`.
- DI is manual: `AppContainer` (constructed in `WifiObserverApplication.onCreate`) wires concrete platform implementations into the common-logic classes via constructor injection. There is no DI framework.

Key invariant: anything intended for `commonMain` (UseCases, `NetworkMonitor`, model, `platform/interfaces`, viewmodel) must not reference Android APIs. Android-specific code lives in `platform/*Impl` and `ForegroundMonitoringService`. Time is injected via `kotlin.time.TimeSource` rather than `System.currentTimeMillis` to stay common-compatible and testable.

The Wifi→Mobile detection tolerates a transient `NotConnected` between Wifi and Mobile (Android's `NetworkCallback` emits `onLost` mid-switch) using a 5-second grace period keyed on `disconnectedTime`. The transition/notification table is in `docs/background-monitoring-design.md`; update it when changing notification logic.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root.

- `./gradlew assembleDebug`: build a debug APK.
- `./gradlew testDebugUnitTest`: run local unit tests.
- `./gradlew connectedDebugAndroidTest`: run instrumented tests on a connected emulator or device.
- `./gradlew ktlintCheck`: check Kotlin formatting.
- `./gradlew ktlintFormat`: apply ktlint formatting fixes.
- `./gradlew lintDebug`: run Android lint for the debug variant.

## Coding Style & Naming Conventions

Kotlin and Gradle Kotlin DSL files use 4-space indentation and a 120-character maximum line length, as defined in `.editorconfig`. Keep files UTF-8, LF-terminated, and free of trailing whitespace.

Follow existing package organization: UI components in `components/`, platform adapters in `platform/`, models in `model/`, and presentation state in `viewmodel/`. Use PascalCase for classes, Compose functions, and enum entries; use camelCase for properties, functions, and test helpers. Prefer small, explicit interfaces for platform behavior, matching the existing `platform/interfaces` pattern.

## Testing Guidelines

Local tests use JUnit 4 and `kotlinx-coroutines-test`. Place unit tests beside the relevant package under `app/src/test/...`, and name test classes after the subject, for example `NetworkUseCaseTest`. Use fakes from `app/src/test/java/com/example/wifi_observer/fake/` or add focused new fakes there when behavior depends on platform services.

Run `./gradlew testDebugUnitTest ktlintCheck` before opening a PR. Add or update tests for notification decisions, monitoring state transitions, and coroutine timing changes.

## Commit & Pull Request Guidelines

Recent commits use concise Conventional Commit-style prefixes such as `fix:`, `docs:`, `test:`, `build:`, `style:`, and `refactor:`. Keep commit subjects imperative and scoped to one change.

Pull requests should include a short problem summary, the implemented approach, and verification commands run. Link related issues when applicable. Include screenshots or short recordings for visible Compose UI changes, and update `docs/` when behavior or architecture changes.

## Security & Configuration Tips

Do not commit `local.properties`, keystores, generated APKs, or machine-specific SDK paths. Keep permission and foreground-service behavior aligned with `AndroidManifest.xml` and the design notes in `docs/`.
