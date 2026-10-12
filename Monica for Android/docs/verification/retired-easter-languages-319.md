# Retired Easter egg languages and icon

Date: 2026-10-12. Ordinary 1.0.319/versionCode 12 and F-Droid 1.0.319/versionCode 26 remain unreleased.

The Nya (zh-NY) and Snow Leopard (zh-XB) language packs, their selection labels and Snow Leopard launcher resources have been removed from active Android sources. The original files are archived under the repository-root `彩蛋语言备份/` directory, outside Android source sets. Existing generator files, documentation and dedicated tests are archived where they existed. Removed labels from shared resource files are preserved separately under `entry-labels/`.

SHA-256 checks verified all 55 ordinary and 52 F-Droid original files against both the local archives and staged Git blobs. Archive-only Git attributes preserve original bytes and historical whitespace across checkout platforms. The archived PNG is the original launcher asset, not a developer screenshot.

The startup cache and existing DataStore migration normalize old NYA/SNOW_LEOPARD selections to Simplified Chinese. Unsupported values are parsed safely. The old launcher component name remains as a disabled compatibility alias using the normal icon, so an installation with that alias enabled still has a launchable entry during upgrade. Existing repair logic enables the chosen normal/blue-star entry before disabling the retired alias; icon and label preferences are preserved.

Validation:

- Ordinary targeted JVM tests: 8 passed, zero failures/errors. Covers supported language enumeration, startup parsing, and real DataStore file migration/reopening while preserving unrelated preferences.
- Ordinary debug and instrumentation APK builds passed in 7 minutes 25 seconds. F-Droid debug and instrumentation APK builds passed in 6 minutes 53 seconds.
- Ordinary API 32 emulator: 4 tests passed in 74.385 seconds. Covers cold startup cache rewriting, normal Chinese resources, migration from the retired alias as the only enabled launcher, preserved icon/label choices, and the language picker without the two retired options.
- F-Droid API 32 emulator: 3 tests passed in 35.955 seconds, covering the startup cache, retired-only launcher repair and language picker.
- AAPT configuration dumps and ZIP inspection of both debug APKs confirmed that retired locale configurations, Snow Leopard image files and the archive directory are absent.

The existing public emulator was reused without wiping it. No screenshots, raw device logs or APKs are committed. Unrelated F-Droid working-tree changes are preserved. This change does not create a release or tag.
