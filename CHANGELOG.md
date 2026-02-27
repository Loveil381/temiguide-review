# Changelog

## [Unreleased] - 2026-02-27

### Fixed (Phase 3)
- `AutonomyHandler.goToLocation` now routes through `navigateTo()` which sets `navigationHandler.isNavigating = true`
- `AutonomyHandler` lifecycle managed in `onStart()`/`onStop()` (`recreate()`/`stopAutonomyLoop()`)
- `NavigationHandler.handleArrival` no longer references deleted `actionQueue` (replaced with `@Suppress` null check)
- `CallStaffTool` now triggers `AppState.StaffCall` transition and `handleCallStaff()` UI
- `ConversationHandler.processUserQuery` SCREEN_IDLE reference fixed to `ScreenManager.SCREEN_IDLE`

### Fixed (Phase 2)
- NavigateTool now sets `navigationHandler.isNavigating = true` before `goTo()`, fixing arrival callbacks being ignored
- AskUserTool no longer calls `robot.askQuestion()` (removed duplicate TTS)
- Unified ASR path to `wakeup()` + `onConversationStatusChanged(status=2)` only
- Fixed duplicate ASR processing via `TemiSttProvider.markAsHandled()`
- Added navigation state guard in `speakAndListen()` to prevent `wakeup()` aborting `goTo()`
- PatrolManager coroutine scope now tied to Activity lifecycle (`destroy()` / `recreate()`)

### Removed (Phase 3)
- `GeminiProvider.SYSTEM_INSTRUCTION_TEMPLATE` (consolidated into `PersonaPromptBuilder`)
- `DialogActionHandler`: legacy `speakAndListen`, `onSaveMemoryInternal`, `onMoveToZoneInternal`, `onNavStartInternal` fields
- `DialogActionHandler.handleCallStaff` `ttsLang` parameter

### Removed (Phase 2)
- `ActionQueue.kt` (unused since ReAct migration)
- `AiProviderType.OPENAI`, `DEEPSEEK`, `LOCAL` (no implementations)
- `DialogActionHandler.executeAction()`, `executeTemiActions()`, `executeQueueItem()` (legacy pipeline)
- `isQueueAskMode` branch in `ConversationHandler.onAsrResult()`
- Duplicate `SCREEN_*` constants from `MainActivity` and `AppState` (unified to `ScreenManager`)

### Added (Phase 3)
- Multi-point navigation rules and 50-char speech limit in `PersonaPromptBuilder`
- `@Deprecated` stubs for `isNavigating`, `isQueueAskMode`, `actionQueue` in `DialogActionHandler`
- Unit tests: `StateManagerTest`, `AppStateTest`, `NavigationAwaiterTest`
- `kotlinx-coroutines-test` dependency

### Changed
- Hardcoded Japanese strings moved to `strings.xml`
- README.md and ARCHITECTURE.md fully rewritten to match current codebase
- SDK_REFERENCE.md updated: `askQuestion()` marked as deprecated in this project
