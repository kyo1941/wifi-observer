# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## Notes for Claude Code

- Run a single unit test class/method with the Gradle test filter, e.g. `./gradlew testDebugUnitTest --tests "com.example.wifi_observer.NetworkUseCaseTest"` or append `.methodName` to target one case.
- Before finishing a change, run `./gradlew ktlintCheck testDebugUnitTest`. If ktlint reports formatting issues, fix them with `./gradlew ktlintFormat` rather than hand-editing whitespace.
- Gradle tasks can be slow to start (daemon warm-up); prefer batching verification into a single invocation and allow a generous timeout.
- This repo uses JDK-based Gradle without an emulator in most sessions — `connectedDebugAndroidTest` and `lintDebug` need a connected device/emulator and may not be runnable; rely on local unit tests for verification.
