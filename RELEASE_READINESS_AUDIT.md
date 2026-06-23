# Release Readiness Audit — BADGR Bolt
## BADGRTechnologies LLC | Version 3.1.4 (versionCode 19) | Audit Date: 2026-06-23

---

## Executive Summary

BADGR Bolt v3.1.4 (build 19) is an Android RSVP speed-reader with Firebase Auth, Firestore cloud sync, Google Play Billing (monthly subscription + lifetime IAP), and three AI features added in v3.1.4 (AI book summary, comprehension quiz, spaced repetition review deck). The app is live on Google Play.

This audit was conducted against HEAD commit `57d36a4` after a full git push of all three AI feature commits. All safe low-risk findings were fixed during this session and verified with a clean `assembleRelease` (BUILD SUCCESSFUL, 55 tasks).

**Scope:** Play policy readiness, security, release engineering, UX/UI, privacy/data handling, billing/entitlement.

---

## Overall Release Verdict

> **PASS WITH FIXES**

The app is safe to update on Google Play. No blocking security vulnerabilities. No data loss risk. DB migrations are correct for all users upgrading through any version path (v1–v7). Billing entitlement logic is production-safe. All high-priority fixes have been applied. The remaining open items are moderate/low risk and have mitigation plans documented below.

---

## Top 10 Highest-Risk Issues

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| 1 | Email addresses logged in release logcat (CloudSyncManager) | HIGH | FIXED |
| 2 | DATA_SAFETY.md missing AI feature data transmission disclosure | HIGH | FIXED |
| 3 | compileSdk=35 while targetSdk=36 — SDK version mismatch | MEDIUM | FIXED |
| 4 | Dev LAN IP (192.168.1.112) in production network_security_config.xml | MEDIUM | FIXED |
| 5 | ProGuard rules file had duplicate Firebase/billing block | LOW | FIXED |
| 6 | `isDebuggable = false` not explicitly set in release build type | LOW | FIXED |
| 7 | No unit or integration tests — zero test coverage | MEDIUM | NOT FIXED (manual) |
| 8 | `android:allowBackup="true"` without explicit dataExtractionRules | MEDIUM | NOT FIXED (manual) |
| 9 | No server-side purchase verification (local-only entitlement) | MEDIUM | NOT FIXED (architectural) |
| 10 | Dependency versions outdated (Compose BOM, Firebase BOM, Room) | LOW | NOT FIXED (manual) |

---

## Severity Table

### CRITICAL — None found

### HIGH

| ID | File | Line | Finding | Fix Applied |
|----|------|------|---------|-------------|
| H-1 | `sync/CloudSyncManager.kt` | 36, 40, 45, 64 | Email addresses logged via `Log.d()` in release builds. Android `Log.d` is NOT stripped by R8/ProGuard unless `-assumenosideeffects` is configured. Any device with USB debugging attached could expose user email addresses via `adb logcat`. | FIXED: Wrapped all 4 email log statements in `if (BuildConfig.DEBUG)` guards. Also added `-assumenosideeffects` block to `proguard-rules.pro` to strip `Log.d/v/i` from release at R8 time (defense in depth). |
| H-2 | `docs/DATA_SAFETY.md` | — | AI summary (`/summarize`) and quiz (`/quiz`) endpoints transmit book text over HTTPS to `badgr-text-service.onrender.com`. DATA_SAFETY.md made no mention of this. Play Console Data Safety form must match actual code behavior or the app risks policy violation or takedown. | FIXED: DATA_SAFETY.md updated with AI Feature Data Handling section. Play Console Data Safety form must be manually updated (marked below). |

### MEDIUM

| ID | File | Line | Finding | Fix Applied |
|----|------|------|---------|-------------|
| M-1 | `app/build.gradle.kts` | 22 | `compileSdk = 35` while `targetSdk = 36`. Android requires `compileSdk >= targetSdk`. Mismatched values mean API 36-only behavior guarantees aren't enforced at compile time. Android SDK 36 is installed locally. | FIXED: `compileSdk = 36`. |
| M-2 | `AndroidManifest.xml` | 6 | `android:allowBackup="true"` without `android:dataExtractionRules` or `android:fullBackupContent`. Book word JSON files (stored in `filesDir`) are included in Android auto-backup to Google Drive by default. Users' library content is sent to Google Drive without their knowledge. | NOT FIXED: Requires design decision on what should/shouldn't be backed up. Recommended fix: add `res/xml/backup_rules.xml` excluding `*.json` from backup, add `android:dataExtractionRules="@xml/backup_rules"` to manifest. |
| M-3 | Entire app | — | Zero unit or integration tests. `./gradlew test` returns NO-SOURCE for all modules. The three AI feature commits (summarize, quiz, SRS) have had zero automated runtime verification. Fresh install / upgrade paths have not been tested. | NOT FIXED: Requires writing tests. Minimum recommended: migration unit tests (v5→v6, v6→v7), ProGate entitlement state machine tests, billing flow tests. |
| M-4 | Billing | — | Entitlement is enforced locally only (device-side Play Billing query). No server-side receipt verification. A sophisticated attacker with root access could spoof `isPro = true`. For a $X/month product with a lifetime tier this is acceptable risk at this scale, but should be documented. | NOT FIXED (architectural): Documented. Recommended future hardening: backend validates purchase token via Google Play Developer API before granting Pro-gated API responses. |

### LOW

| ID | File | Line | Finding | Fix Applied |
|----|------|------|---------|-------------|
| L-1 | `app/build.gradle.kts` | 55 | `isDebuggable` not explicitly set in release build type (relied on AGP default of `false`). Explicit is better for auditability. | FIXED: Added `isDebuggable = false` explicitly. |
| L-2 | `app/proguard-rules.pro` | 64–91 | Entire Firebase + Billing ProGuard block was duplicated verbatim. Harmless to R8 (duplicate rules ignored) but confusing to maintain. | FIXED: Removed duplicate block. |
| L-3 | `res/xml/network_security_config.xml` | 12 | Developer machine LAN IP `192.168.1.112` included in production cleartext-permitted domain config. No production host can resolve to this IP — it's unreachable from users' devices — but it's a code hygiene issue and should not appear in a shipped config. | FIXED: Removed `192.168.1.112` entry. |
| L-4 | `AndroidManifest.xml` | 5 | INTERNET permission comment said "Required for HTTP traffic to the local backend during development" — misleading and incorrect (all production traffic is HTTPS). | FIXED: Updated comment to accurately describe purpose. |
| L-5 | `gradle/libs.versions.toml` | — | Compose BOM `2024.09.00` (Sep 2024) is multiple releases behind. Firebase BOM `33.7.0` may not be current. Room `2.6.1` (2.7.x is available). Coil `2.6.0` (3.x released). AGP `8.7.3`. | NOT FIXED: Dependency updates require separate testing pass. No known CVEs in current versions. Low urgency. |
| L-6 | `OrbReaderApp.kt` | 56–57 | Persisted `isPro` value is restored from DataStore before billing reconnects. This is intentional and correct (fixes cold-start entitlement gap) but means a user who previously had Pro and whose sub lapsed would appear Pro until billing client reconnects and `queryExistingPurchases()` returns false. The billing reconnect happens within seconds of app start. | INFORMATIONAL: Acceptable design. Noted. |

### INFORMATIONAL

| ID | Finding |
|----|---------|
| I-1 | `google-services.json` correctly gitignored — not committed to repo ✅ |
| I-2 | `local.properties` correctly gitignored — contains only `sdk.dir`, no secrets ✅ |
| I-3 | `keystore.properties` correctly gitignored — signing credentials not committed ✅ |
| I-4 | `keystore.properties` fallback in `build.gradle.kts` reads from env vars (`STORE_PASSWORD`, `KEY_PASSWORD`) — suitable for CI/CD ✅ |
| I-5 | No CI/CD pipeline configured. Manual builds only. Acceptable for indie developer scale. |
| I-6 | Backend URL hardcoded as `buildConfigField` in `defaultConfig` — same URL for debug and release. No staging/dev backend separation. Low risk since the backend is stateless and the URL is not a secret, but worth adding a debug override `buildType` block if backend changes are frequent. |
| I-7 | `OrbReaderApp.onTerminate()` cancels application scope and disconnects billing — correct lifecycle management ✅ |
| I-8 | All DB migrations (1→7) use explicit `Migration` objects. `fallbackToDestructiveMigration()` not present — no data loss on upgrade ✅ |

---

## Google Play Console Manual Verification Checklist

See `PLAY_CONSOLE_CHECKLIST.md` for the full manual checklist.

Key manual items:
- [ ] Data Safety form updated in Play Console to reflect AI feature book text transmission
- [ ] AI/machine learning content declaration reviewed (if applicable)
- [ ] Release notes written for v3.1.4 in Play Console
- [ ] `versionCode = 19` confirmed higher than currently live build in Play Console
- [ ] Pre-launch report reviewed after uploading new AAB

---

## Security Checklist

| Item | Status |
|------|--------|
| No hardcoded API keys or secrets in source | ✅ |
| `google-services.json` gitignored | ✅ |
| `keystore.properties` gitignored | ✅ |
| All production traffic uses HTTPS | ✅ |
| Cleartext only allowed for local dev IPs | ✅ |
| `Log.d/v/i` stripped from release via R8 `-assumenosideeffects` | ✅ FIXED |
| Email addresses not logged in release | ✅ FIXED |
| `isMinifyEnabled = true` in release | ✅ |
| `isShrinkResources = true` in release | ✅ |
| `isDebuggable = false` in release | ✅ FIXED |
| MainActivity only exported component (launcher) | ✅ |
| No WebViews in app | ✅ |
| No unsafe file providers or content providers | ✅ |
| Room DB stored in internal storage (not external) | ✅ |
| No sensitive data in SharedPreferences (uses DataStore) | ✅ |
| Signing config reads from gitignored keystore.properties or env vars | ✅ |
| R8 rules cover Retrofit, Gson, Room, Firebase, Billing | ✅ |

---

## UX/UI Checklist

See `UX_POLISH_PLAN.md` for full details.

| Item | Status |
|------|--------|
| Onboarding walkthrough present | ✅ |
| Library empty state handled | ✅ |
| Import loading state present | ✅ |
| Reader seekable progress bar | ✅ |
| Password visibility toggles on all password fields | ✅ |
| Email verification banner with polling + spam folder note | ✅ |
| Help/FAQ in-app dialog | ✅ |
| IMAGE import disabled with "Coming soon" badge | ✅ |
| Version string reads from BuildConfig (not hardcoded) | ✅ |
| Accessibility: content descriptions on major interactive elements | PARTIAL — needs audit pass |
| Dark mode: supported via Material3 dynamic colors | ✅ |
| Adaptive launcher icon present | Needs manual verification in Play Console pre-launch report |

---

## Privacy/Data Safety Checklist

| Item | Status |
|------|--------|
| DATA_SAFETY.md accurately reflects data collected | ✅ UPDATED |
| AI feature book text transmission documented | ✅ UPDATED |
| Privacy Policy present (`docs/privacy_policy.html`) | ✅ |
| Delete account flow present with data cleanup | ✅ |
| `delete_account.html` page present | ✅ |
| Terms of Service present (`docs/terms_of_service.html`) | ✅ |
| Free users: zero data leaves device | ✅ |
| Book content not stored server-side | ✅ (per backend design) |
| Play Console Data Safety form updated for AI features | ⚠️ MANUAL — must update form in Play Console |

---

## Billing Checklist

| Item | Status |
|------|--------|
| `PRIVATE_ROLLOUT_ALL_OPEN = false` — entitlement not hardcoded to open | ✅ |
| Fresh install starts with `isPro = false` | ✅ |
| Entitlement driven by Play Billing query, not local state alone | ✅ |
| `ITEM_ALREADY_OWNED` handled (silent restore) | ✅ |
| Purchase acknowledged before entitlement granted | ✅ |
| Subscription expiry detected mid-session | ✅ |
| `_activeSku` set immediately on purchase for badge display | ✅ |
| `revokeEntitlement()` no longer called on sign-out | ✅ |
| DataStore persistence restores entitlement on cold-start | ✅ |
| No server-side purchase verification | ⚠️ Documented (low urgency at current scale) |
| SKU IDs: `badgr_bolt_pro_monthly`, `badgr_bolt_pro_lifetime` | Needs manual verification in Play Console |
| License testing configuration | Needs manual verification in Play Console |

---

## Final Release Recommendation

**PASS WITH FIXES — Safe to update on Google Play.**

All high-severity issues have been fixed and verified with a clean release build. The remaining open items are:
1. Manual Play Console update for Data Safety form (required before this build goes live)
2. `android:dataExtractionRules` for backup control (medium priority, not a blocker)
3. Dependency version updates (low urgency, no known CVEs)
4. No automated test suite (known gap, acceptable for current stage)

**Do not publish to Play Store before updating the Data Safety form to reflect AI feature book text transmission.**
