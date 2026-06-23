# Security Fix Plan — BADGR Bolt v3.1.4
## BADGRTechnologies LLC | Audit Date: 2026-06-23

---

## Confirmed Security Issues — All Fixed in This Session

### SEC-1 (HIGH) — Email PII in Release Logcat
**File:** `app/src/main/java/com/badgr/orbreader/sync/CloudSyncManager.kt`
**Lines:** 36, 40, 45, 64

**Problem:** `CloudSyncManager` called `Log.d()` with user email addresses as interpolated strings (`"Attempting signUp for $email"`, `"Verification email sent to $email"`, etc.). Android's `Log.d` is NOT automatically stripped by R8/ProGuard in release builds — it requires explicit `-assumenosideeffects` configuration. Any device connected via USB with adb would expose user email in logcat.

**Fix Applied:**
1. Wrapped all 4 email-bearing log statements in `if (BuildConfig.DEBUG)` guards
2. Added `-assumenosideeffects` block to `proguard-rules.pro` stripping `Log.d/v/i` calls at R8 time:
```proguard
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```
`Log.e` and `Log.w` are preserved (valuable for Crashlytics context in production).

**Risk after fix:** Eliminated. Defense in depth: both source guards AND R8 stripping.

---

## Hardening Steps — Applied in This Session

### SEC-2 (LOW) — Explicit `isDebuggable = false`
**File:** `app/build.gradle.kts`, release build type
**Fix:** Added `isDebuggable = false` explicitly. AGP defaults to `false` for release, but explicit declaration makes the intent auditable and protects against future AGP version behavior changes.

### SEC-3 (LOW) — Dev LAN IP in Production Network Security Config
**File:** `app/src/main/res/xml/network_security_config.xml`
**Fix:** Removed `192.168.1.112` (developer machine LAN IP) from the `cleartextTrafficPermitted` domain config. This IP was unreachable from user devices so posed no active risk, but should not appear in a shipped production config.

### SEC-4 (LOW) — ProGuard Rules Deduplication
**File:** `app/proguard-rules.pro`
**Fix:** Removed duplicate Firebase + Play Billing ProGuard block. Two identical 28-line blocks existed. R8 ignores duplicates, but having them created false confidence that different rules applied. Clean rules file now.

---

## Secrets Handling — Current Status

| Item | Status |
|------|--------|
| `google-services.json` | Gitignored ✅ — present locally for Firebase build, never committed |
| `keystore.properties` | Gitignored ✅ — signing credentials never in repo |
| `local.properties` | Gitignored ✅ — contains only `sdk.dir` |
| Keystore file (`*.jks`) | Gitignored ✅ — all `*.jks` / `*.keystore` patterns excluded |
| Backend URL | Hardcoded in `BuildConfig.BACKEND_BASE_URL` via `buildConfigField` — this is the production URL, not a secret, embedded in APK (acceptable) |
| Firebase project config | Embedded in `google-services.json` (gitignored) — Firebase API key is client-side only and restricted by package name + SHA-1 in Firebase Console |
| No hardcoded passwords, tokens, or API keys found in source | ✅ |

**Recommendation:** Firebase API key in `google-services.json` should be restricted in Firebase Console to `com.badgr.orbreader` package + release SHA-1 fingerprint. This limits misuse if the key is ever extracted from the APK.

---

## Network Security

| Item | Status |
|------|--------|
| All production traffic uses HTTPS | ✅ |
| `badgr-text-service.onrender.com` — HTTPS only | ✅ |
| Firebase/Firestore — HTTPS only | ✅ |
| Google Play Billing — HTTPS only | ✅ |
| Cleartext permitted for localhost/emulator only | ✅ FIXED |
| No certificate pinning | ⚠️ See below |
| No unsafe TrustManagers or hostname verifier bypasses | ✅ |
| OkHttp logging BODY level only in debug builds | ✅ |

**Certificate pinning:** Not implemented. For a production app using a Render.com backend, pinning is impractical (Render rotates certificates, CDN/TLS termination varies). Acceptable risk at this scale. Would recommend adding pinning if the backend is ever migrated to a dedicated server with a stable certificate.

---

## Storage and Privacy

| Item | Status |
|------|--------|
| Room DB in internal storage (`filesDir/databases/`) | ✅ |
| Word JSON files in internal storage (`filesDir/words_*.json`) | ✅ |
| Cover images in internal storage | ✅ |
| DataStore preferences in internal storage | ✅ |
| No data written to external storage | ✅ |
| No sensitive data in SharedPreferences | ✅ (uses DataStore) |
| `android:allowBackup="true"` without explicit exclusion rules | ⚠️ See below |

**Backup concern (MEDIUM — not yet fixed):** `android:allowBackup="true"` in the manifest without `android:dataExtractionRules` means Android's auto-backup system will include everything in `filesDir` in Google Drive backups. Book word JSON files (user's book content) will be backed up. For most users this is desirable (preserves library on device restore), but it should be an explicit choice. 

**Recommended fix:**
1. Create `app/src/main/res/xml/backup_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <include domain="database" path="orbreader.db"/>
        <exclude domain="file" path="." />
    </cloud-backup>
    <device-transfer>
        <include domain="database" path="orbreader.db"/>
        <include domain="file" path="." />
    </device-transfer>
</data-extraction-rules>
```
2. Add `android:dataExtractionRules="@xml/backup_rules"` to `<application>` in manifest.

This would back up the DB (reading progress, book metadata) while excluding the potentially large word JSON files from cloud backup (device-to-device transfer keeps them for user convenience).

---

## Release Build Hardening

| Item | Status |
|------|--------|
| `isMinifyEnabled = true` (R8 enabled) | ✅ |
| `isShrinkResources = true` | ✅ |
| `isDebuggable = false` (explicit) | ✅ FIXED |
| NDK debug symbols at `SYMBOL_TABLE` level | ✅ (for Crashlytics mapping) |
| ProGuard rules cover Retrofit/Gson/Room/Firebase/Billing | ✅ |
| `Log.d/v/i` stripped via `-assumenosideeffects` | ✅ FIXED |
| `debugImplementation` only for Compose tooling | ✅ |
| No test/mock code in production paths | ✅ |

---

## Dependency Security Notes

No known CVEs were identified in the current dependency versions. The following are flagged as potentially outdated and should be updated in a maintenance pass:

| Library | Current | Notes |
|---------|---------|-------|
| Compose BOM | 2024.09.00 | Sep 2024 — multiple releases behind |
| Firebase BOM | 33.7.0 | Verify current in Firebase release notes |
| Room | 2.6.1 | 2.7.x available |
| Coil | 2.6.0 | 3.x released with Kotlin-first API |
| AGP | 8.7.3 | Check for 8.8.x/8.9.x |

Run `./gradlew dependencyUpdates` (with versions plugin) or check each library's releases page before next version update.

---

## Future Hardening Recommendations (Not Blocking)

1. **Server-side purchase verification:** Backend validates purchase token via Google Play Developer API before granting Pro-gated AI responses. Prevents root-level spoofing.
2. **Certificate pinning:** Add OkHttp `CertificatePinner` if backend moves to stable dedicated hosting.
3. **ProGuard for model classes:** Review whether `-keep class com.badgr.orbreader.data.remote.**` is too broad — consider keeping only classes annotated with `@SerializedName` instead.
4. **Backup rules:** Implement explicit `dataExtractionRules` (detailed above).
5. **Firebase API key restriction:** Restrict in Firebase Console to release SHA-1 only.
