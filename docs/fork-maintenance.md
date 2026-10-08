# Fork maintenance

This fork keeps Voice's upstream architecture and adds two local features:

- Desktop widgets retain the original layout, typography, artwork and controls. Size-based and single-chapter visibility conditions are removed, so the full existing widget is always used. On Android 12 and newer, cells shorter than 80 dp use compact text and explicitly sized controls within that same layout, preventing launcher density differences from squeezing the playback icons. Cells at least 80 dp tall keep the original dimensions. This rule has no manufacturer or model checks.
- Global playback speed, silence skipping and gain live in Settings. Each book can override each option independently, including neutral values, or return to the global value. Changes affect the loaded player without resetting its position.

## Data compatibility

Room migration 60 to 61 adds three inheritance flags to `content2`. Old non-default values become overrides; old default values inherit. Global defaults initially match the previous defaults. Progress, books, bookmarks and sleep timer preferences are preserved.

Keep rollback copies of both the installed APKs and app data before upgrading an existing installation. An APK downgrade alone does not undo a database migration.

## Follow upstream

Use `origin` for this maintained fork and `upstream` for `PaulWoitaschek/Voice`. Integrate upstream on a temporary branch first:

```sh
git fetch upstream
git switch -c update/upstream origin/main
git merge upstream/main
./gradlew formatKotlin
./gradlew voiceUnitTest lintKotlin :app:assembleFreeDebug
```

Review conflicts in `BookContent`, Room migrations/schema, `BookRepositoryImpl`, `VoicePlayer`, the settings/playback screens and widget layouts. If upstream also increments Room beyond 60, reconcile the migration paths and schema before shipping; never discard an existing user database to resolve the conflict.

On Windows, point `ANDROID_HOME` to the installed Android SDK. The wrapper provisions the configured Java toolchain. Migration tests use absolute database paths to work with Windows separators.

After tests pass, verify upgrading an existing library, global/individual/reset behavior, active playback, small widgets, and both screen orientations on devices. Merge into the fork's maintained branch and push only to `origin`. This fork does not require an upstream pull request.
