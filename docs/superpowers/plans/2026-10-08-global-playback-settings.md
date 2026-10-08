# Global Playback Settings Implementation Plan

**Goal:** Keep widget details visible at all sizes and support global playback defaults with per-book, per-setting overrides.

**Approved behavior:** Always show book and chapter titles, including single-chapter books. Preserve the original widget layout and styling exactly; remove only the size-based and single-chapter hiding conditions. Add global speed, silence skipping and gain settings. Books inherit each setting unless explicitly overridden, including explicit neutral values. Provide individual inheritance controls and reset all. Apply changes to active playback. Migrate old non-default values as overrides; old default values inherit. Keep sleep timer preferences global.

**Architecture:** `core:data:api` owns serializable `PlaybackSettings` and three inheritance flags on `BookContent`. `core:data:impl` supplies DataStore and Room migration, projects effective settings on repository reads while keeping updates on raw stored content. Playback observes effective settings for the loaded book without converting inherited values into overrides. Feature modules present global and book settings without depending on each other.

**Tech stack:** Kotlin, Room, DataStore, Metro, Compose, Media3, RemoteViews.

## Tasks

- [x] Add inheritance/resolution regression tests in `core/data/api/src/test/kotlin/voice/core/data/PlaybackSettingsTest.kt`; run `:core:data:api:testDebugUnitTest`.
- [x] Add `PlaybackSettings.kt`, inheritance flags on `BookContent.kt`, `PlaybackSettingsStore`, provider in `StoreModule.kt`, Room 60-to-61 migration and migration tests. Preserve flags in `ProgressCarryOver.kt`.
- [x] Update `BookRepositoryImpl.kt` to resolve settings consistently for single/list and flow/snapshot reads. Test reactive global updates and raw write preservation.
- [x] Update `VoicePlayer.kt` to apply effective values to the loaded book, observe changes and mark only explicit edits as overrides. Test book switches, reset and inherited updates.
- [x] Add global controls in `features/settings` and per-book inheritance/reset controls in `features/playbackScreen`. Reuse existing styles and add English/Chinese strings. Test changed view model behavior.
- [x] Update only `features/widget` visibility logic to retain the original full layout at every size and for single-chapter books; leave XML, dimensions, colors, icons and resizing behavior unchanged.
- [x] Review specification compliance and code quality. Global settings passed the full unit test suite and Kotlin checks. After restoring the exact original widget style, build and install directly as requested, without rerunning tests. Both devices report successful installation of 26.10.8-wayne.2 (5406003).

## Verification boundaries

Do not modify AGENTS.md or open an upstream PR. After validation, preserve rollback artifacts and install over the existing app on both connected devices for user 0. Use gh to create the user fork and push the verified implementation there; retain upstream for future updates. Keep changes on `feat/global-playback-settings`. Preserve current library/progress during migration. An old explicitly chosen neutral value is indistinguishable from an untouched default and follows the approved default-value migration rule.
