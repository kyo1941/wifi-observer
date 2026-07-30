# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## Notes for Claude Code

- Run a single test class with the Gradle test filter, e.g. `./gradlew :shared:testAndroidHostTest --tests "com.example.wifi_observer.NetworkUseCaseTest"`, or `--tests "*BackgroundMonitoringServiceImplTest*"` against `:shared:iosSimulatorArm64Test` for the iOS ones.
- Before finishing a change, run `./gradlew ktlintCheck :shared:testAndroidHostTest :shared:iosSimulatorArm64Test`. If ktlint reports formatting issues, fix them with `./gradlew ktlintFormat` rather than hand-editing whitespace.
- Gradle tasks can be slow to start (daemon warm-up); prefer batching verification into a single invocation and allow a generous timeout.
- ktlint reports are per source set. When a check fails early, later source sets are skipped and their report files are left over from a previous run — delete `*/build/reports/ktlint` or pass `--continue` before trusting them.
- This repo uses JDK-based Gradle without an emulator in most sessions — `connectedDebugAndroidTest` and `lintDebug` need a connected device/emulator and may not be runnable; rely on local unit tests for verification.
- iOS tests compile and run against the simulator target without an Xcode project, but anything requiring a real app bundle (`BGTaskScheduler` scheduling, notification delivery) cannot be verified this way.
