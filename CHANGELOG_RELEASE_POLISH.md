# Changelog — Release Polish Pass
## BADGR Bolt v3.1.4 (build 19) | 2026-06-23
## Applied by: Production readiness audit, Claude Code

---

## Summary

7 low-risk fixes applied during the production readiness audit. All changes verified with `./gradlew assembleRelease` → **BUILD SUCCESSFUL** (55 tasks, 27 executed).

---

## Files Changed

### 1. `app/src/main/java/com/badgr/orbreader/sync/CloudSyncManager.kt`
**Why changed:** Email addresses were being logged via `Log.d()` in release builds. `Log.d` is not stripped by R8 unless `-assumenosideeffects` is configured. User email addresses (`signUp`, `signIn`, `resendVerification`) would appear in `adb logcat` on any connected device.

**Change:** Added `import com.badgr.orbreader.BuildConfig`. Wrapped 4 `Log.d` calls that interpolate email addresses or user email in `if (BuildConfig.DEBUG)` guards. Non-PII logs (`Log.d(TAG, "Signing out")`, `Log.d(TAG, "Synced N books for uid=...")`, `Log.d(TAG, "Deleted account and cloud data for uid=...")`) left as-is — these have no PII and are also covered by the ProGuard `-assumenosideeffects` rule below.

**Risk:** Low. Source-only guard addition. No behavioral change in debug builds. Release builds: email no longer logged.

**Lines changed:** 3, 36, 40, 45, 64

---

### 2. `app/proguard-rules.pro`
**Why changed:** Two separate issues:
1. A duplicate 28-line Firebase + Play Billing block was present (lines 64–91 were an exact repeat of lines 37–58). Duplicate ProGuard rules are harmless but create false-confidence that different configuration applies.
2. No `-assumenosideeffects` rule for `android.util.Log` — verbose log calls (`Log.d/v/i`) were present in release builds.

**Change:**
- Added `-assumenosideeffects` block at the top of the file stripping `Log.isLoggable`, `Log.v`, `Log.d`, `Log.i` from release builds at R8 time. `Log.e` and `Log.w` are preserved for production error diagnostics.
- Removed duplicate Firebase + Billing rule block (second copy of 28 lines).

**Risk:** Low. `-assumenosideeffects` is standard practice. Duplicate removal is cosmetic. No functional change to kept classes.

---

### 3. `app/build.gradle.kts`
**Why changed:** Two issues:
1. `compileSdk = 35` while `targetSdk = 36` — the Android build system requires `compileSdk >= targetSdk`. This was a misconfiguration; Android SDK 36 is installed.
2. `isDebuggable = false` was not explicitly set in the `release` build type (relied on AGP default).

**Change:**
- `compileSdk = 35` → `compileSdk = 36`
- Added `isDebuggable = false` as first line of `release` build type block

**Risk:** Low. compileSdk bump uses already-installed SDK 36. Release build verified clean. No API 36-specific calls in codebase so no compatibility concerns. `isDebuggable` change has no effect (was already false by default).

---

### 4. `app/src/main/res/xml/network_security_config.xml`
**Why changed:** Developer machine LAN IP `192.168.1.112` was present in the `cleartextTrafficPermitted` domain config. This IP cannot be reached from user devices — it's a private LAN address that only resolves to the developer's machine on the same network. No active security risk, but it should not appear in a production config. Also updated comment to be more accurate.

**Change:** Removed `<domain includeSubdomains="false">192.168.1.112</domain>`. Updated XML comment.

**Risk:** None. Only removed an unreachable development host from cleartext allowlist. All production traffic already uses HTTPS. Emulator/localhost entries retained for development use.

---

### 5. `app/src/main/AndroidManifest.xml`
**Why changed:** INTERNET permission comment said "Required for HTTP traffic to the local backend during development" — misleading on two counts: the app's production traffic uses HTTPS, and INTERNET is needed for production (Firebase, Play Billing, backend) not just development.

**Change:** Updated comment to accurately state: "Required for HTTPS network traffic (Firebase, backend API, Play Billing)"

**Risk:** None. Comment-only change.

---

### 6. `docs/DATA_SAFETY.md`
**Why changed:** v3.1.4 added AI Book Summary (`/summarize` endpoint) and Comprehension Quiz (`/quiz` endpoint) features. Both transmit book text to the FastAPI backend over HTTPS for NLP processing. The prior DATA_SAFETY.md made no mention of this data flow. Play Console Data Safety form must accurately reflect what data is transmitted and to whom.

**Change:** Added "Book text content" row to the Data Collected table. Added "AI Feature Data Handling (v3.1.4+)" section explaining what each AI endpoint does, that transmission is HTTPS, that the backend does not store content after processing, and that SRS (spaced repetition) is entirely on-device. Added note that the Play Console Data Safety form must be manually updated.

**Risk:** None. Documentation-only. Does not change app behavior. Required for Play Console compliance.

---

## Tests Run

| Command | Result |
|---------|--------|
| `./gradlew assembleDebug` | BUILD SUCCESSFUL |
| `./gradlew assembleRelease` (pre-fixes) | BUILD SUCCESSFUL |
| `./gradlew lint` | BUILD SUCCESSFUL — no issues |
| `./gradlew test` | BUILD SUCCESSFUL — NO-SOURCE (no unit tests written) |
| `./gradlew assembleRelease` (post-fixes) | BUILD SUCCESSFUL — 55 tasks, 27 executed |

---

## Tests Not Run

| Test Type | Reason |
|-----------|--------|
| `./gradlew connectedAndroidTest` | Requires connected device/emulator — not run |
| `./gradlew bundleRelease` | Not run (AAB production bundle). Run this before uploading to Play Console. |
| Manual UI testing | Not performed this session — no device connected |
| DB migration v5→v6→v7 (upgrade from production) | Not automated — requires manual test on device with existing data |
| Billing flow (purchase, restore, subscription expiry) | Requires Google Play test environment — not run |
| TalkBack / accessibility | Not performed |
| AI features (summary, quiz, SRS) end-to-end | Not performed — no device connected |

---

## What Still Requires Manual Play Console Action

1. **Data Safety form** — Update to reflect AI feature book text transmission (see `PLAY_CONSOLE_CHECKLIST.md` section B)
2. **Release notes** — Write v3.1.4 release notes in Play Console
3. **AAB upload** — Run `./gradlew bundleRelease` and upload to Play Console internal track
4. **Pre-launch report** — Review after upload for crashes and accessibility warnings
5. **Delete account URL** — Confirm `docs/delete_account.html` public URL is set in Play Console
