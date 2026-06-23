# UX Polish Plan — BADGR Bolt v3.1.4
## BADGRTechnologies LLC | Audit Date: 2026-06-23

This document captures UX/UI observations from code review. Items marked ✅ are already done. Items marked ⚠️ are improvement opportunities for a future polish pass. Items marked 🔧 have a clear implementation path.

---

## First-Launch Experience

| Item | Status | Notes |
|------|--------|-------|
| Walkthrough/onboarding tour present | ✅ | `WalkthroughComponent.kt` — multi-step overlay |
| Walkthrough triggers correctly on fresh install | ✅ | `LaunchedEffect` reads `hasSeenOnboarding` from DataStore |
| Walkthrough replay via Settings → Help | ✅ | `SettingsViewModel.resetHelpSeen()` |
| Welcome guide book pre-populated on empty library | ✅ | `OrbReaderApp.kt` — atomic write on first launch |
| Splash screen | ⚠️ | No dedicated splash screen. Android 12+ shows an automatic `windowSplashScreenAnimatedIcon` — confirm icon is set in `res/values/styles.xml` or `themes.xml`. If not, app shows a plain white splash. |

**Splash fix (if needed):**
In `res/values/themes.xml`, add to the launch theme:
```xml
<item name="android:windowSplashScreenBackground">@color/black</item>
<item name="android:windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
```
Install `androidx.core:core-splashscreen` and call `installSplashScreen()` in `MainActivity.onCreate()` before `super.onCreate()`.

---

## Navigation

| Item | Status | Notes |
|------|--------|-------|
| Bottom navigation bar present | ✅ | Library / Reader / Stats / Settings / Account |
| Back navigation consistent | ✅ | Jetpack Navigation Compose handles back stack |
| Stats screen gated on isPro | ✅ | Shows upgrade prompt for free users |
| No dead-end navigation states found | ✅ | |
| Reader → Library back returns to correct state | ✅ | Progress saved on back via BackHandler |
| Deep links or notification navigation | N/A | No notifications or deep links implemented |

---

## Empty States

| Item | Status | Notes |
|------|--------|-------|
| Library empty state (no books) | ✅ | Welcome guide pre-populated; never shows true empty state |
| Stats empty state (no sessions) | ⚠️ | Verify `StatsScreen.kt` shows a graceful message when `readingSessions` list is empty |
| SRS deck empty state (no cards due) | ⚠️ | Verify `SrsScreen.kt` shows a "No reviews due" state rather than a blank screen |
| Quiz empty state (quiz generation fails) | ⚠️ | If `/quiz` backend returns an error, the screen should show a retry option, not a silent failure |
| Summary empty state (no summary yet) | ⚠️ | If summary is null and hasn't been generated, the UI should prompt the user to generate, not silently show nothing |

---

## Error and Loading States

| Item | Status | Notes |
|------|--------|-------|
| Book import loading indicator | ✅ | Progress/spinner shown during upload |
| Import error shown to user | ✅ | Snackbar/dialog on failure |
| Backend cold-start latency (Render.com ~50s) | ⚠️ | The first API call after inactivity hits a 50s cold-start. User sees a spinner for ~50s with no indication of why. Recommend adding explanatory text: "First import after inactivity may take up to a minute — the server is waking up." |
| AI summary loading state | ⚠️ | Review `BookDetailScreen` or wherever summary is triggered — ensure there's a visible progress indicator while waiting for the /summarize response |
| Quiz generation loading state | ⚠️ | Same as above — /quiz can take several seconds |
| Network error on AI features | ⚠️ | Timeout on /summarize or /quiz (120s read timeout) should show a user-friendly error with retry, not a generic crash or silent failure |
| Purchase error messages | ✅ | `InAppPurchaseManager` surfaces errors via `onPurchaseError` callback |
| Billing not connected error | ✅ | "Billing client not ready. Please try again." |

---

## Account and Pro State

| Item | Status | Notes |
|------|--------|-------|
| Unverified email banner with polling | ✅ | 5-second polling loop + "I've verified" button |
| Spam folder mentioned in verification banner | ✅ | |
| Password visibility toggle | ✅ | Eye icon on password + delete password fields |
| Lifetime Member badge displays after purchase | ✅ | `_activeSku` set immediately on acknowledgement |
| Pro badge reactive to billing state changes | ✅ | Collects `isProFlow` StateFlow |
| Delete account dialog with password confirmation | ✅ | |
| Sign-out does not revoke billing entitlement | ✅ | |
| Paywall / upgrade prompt for free users on Pro features | ✅ | Stats gated; file size gate shows upsell |

---

## Visual Polish

| Item | Status | Notes |
|------|--------|-------|
| Material3 theming with dynamic color | ✅ | `Theme.kt` using Material3 |
| Dark mode support | ✅ | Material3 handles dark mode |
| Typography consistent | ✅ | |
| Launcher icon | ⚠️ | Verify adaptive icon renders correctly on Android 12+ devices (round/squircle). Check in pre-launch report. `ic_launcher` and `ic_launcher_round` should both be present. `fix_existing_launcher_icons.sh` script in root directory suggests this was already worked on. |
| Status bar / navigation bar behavior | ⚠️ | Verify `WindowCompat.setDecorFitsSystemWindows(window, false)` or equivalent is used so the app doesn't fight the system bar insets on edge-to-edge displays |
| Color blindness mode | ✅ | DataStore preference for color-blindness mode present |

---

## Accessibility

| Item | Status | Notes |
|------|--------|-------|
| Content descriptions on icon-only buttons | ⚠️ | Reader control buttons (play/pause, WPM adjust, word skip) should have `contentDescription` set. Icon-only buttons are inaccessible to screen readers without them. |
| Min touch target size (48dp × 48dp) | ⚠️ | Verify reader control buttons meet minimum touch target. Some icon buttons in compact layouts may be smaller. |
| Dynamic font scaling | ⚠️ | Test with system font size at "Largest" — RSVP word display uses fixed `sp` sizes; ensure these scale appropriately |
| Color contrast | ⚠️ | ORP (Optimal Recognition Point) highlight color should have sufficient contrast ratio against the background. Check against WCAG 2.1 AA (4.5:1 for normal text) |
| Screen reader (TalkBack) | ⚠️ | Not tested. Recommend a TalkBack pass of the main flow: library → import → reader → settings |

**Quick accessibility fixes:**
Add `contentDescription` parameters to icon-only `IconButton` composables in `ReaderScreen.kt`:
```kotlin
IconButton(onClick = { ... }, modifier = Modifier.semantics { contentDescription = "Previous word" }) { ... }
```

---

## Offline State

| Item | Status | Notes |
|------|--------|-------|
| Core RSVP reading works offline | ✅ | Words stored locally — no network needed |
| Library browsing works offline | ✅ | Room DB — offline |
| AI features (summary, quiz) fail gracefully offline | ⚠️ | Requires network. If offline, OkHttp will throw `UnknownHostException` or `SocketTimeoutException`. Ensure these are caught and shown as "This feature requires an internet connection" rather than a generic error or crash. |
| Cloud sync fails gracefully offline | ✅ | `ProGate.cloudSync` gate + Firestore SDK handles offline gracefully |
| Billing state persisted for offline | ✅ | DataStore persists `isPro` for cold-start restoration |

---

## Play Store Trust

| Item | Status | Notes |
|------|--------|-------|
| Privacy policy URL in app listing | ⚠️ | Verify `docs/privacy_policy.html` is hosted at a stable public URL and linked in Play Console |
| Delete account URL in Play Console | ⚠️ | Google requires apps with account creation to provide an in-app or web account deletion flow. Confirm `docs/delete_account.html` URL is entered in Play Console settings. |
| App screenshots reflect current UI | ⚠️ | Screenshots in Play Console may predate v3.1.4 AI features. Consider adding screenshots of summary and quiz screens. |
| App description mentions AI features | ⚠️ | Update long description in Play Console to mention AI Book Summary, Comprehension Quiz, and Spaced Repetition Review as new features in v3.1.4 |
| Crashlytics integration active | ✅ | `firebase.crashlytics` plugin + BOM dependency |
