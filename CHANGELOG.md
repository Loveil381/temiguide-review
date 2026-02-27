# Changelog

## [Unreleased] - 2026-02-27

### Fixed
- NavigateTool now sets `navigationHandler.isNavigating = true` before `goTo()`, fixing arrival callbacks being ignored
- AskUserTool no longer calls `robot.askQuestion()` (removed duplicate TTS)
- Unified ASR path to `wakeup()` + `onConversationStatusChanged(status=2)` only
- Fixed duplicate ASR processing via `TemiSttProvider.markAsHandled()`
- Added navigation state guard in `speakAndListen()` to prevent `wakeup()` aborting `goTo()`
- PatrolManager coroutine scope now tied to Activity lifecycle (`destroy()` / `recreate()`)

### Removed
- `ActionQueue.kt` (unused since ReAct migration)
- `AiProviderType.OPENAI`, `DEEPSEEK`, `LOCAL` (no implementations)
- `DialogActionHandler.executeAction()`, `executeTemiActions()`, `executeQueueItem()` (legacy pipeline)
- `isQueueAskMode` branch in `ConversationHandler.onAsrResult()`
- Duplicate `SCREEN_*` constants from `MainActivity` and `AppState` (unified to `ScreenManager`)

### Changed
- Hardcoded Japanese strings moved to `strings.xml`
- README.md and ARCHITECTURE.md fully rewritten to match current codebase
- SDK_REFERENCE.md updated: `askQuestion()` marked as deprecated in this project
