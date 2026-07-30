## [3.3.0] — 2026-07-30

### Added
- Read Aloud (TTS): synchronized text-to-speech playback — speaker toggle in the reader top bar. Speech acts as the playback clock: each RSVP chunk is one utterance and the display advances only when the engine finishes speaking it, so audio and display can't drift. Free for all users
- Speech rate follows the WPM dial (rate = WPM/175, clamped to the engine's intelligible 0.5–3.0 range, ~525 WPM effective ceiling with a UI hint above it)
- Audio focus handling: playback pauses automatically on phone calls or when another app takes audio
- Graceful degradation: if the device TTS engine fails to initialize or errors mid-book, playback falls back to visual-only mode and the toggle disables — never blocks reading
- New `audio/TextToSpeechManager.kt`; `ttsEnabled` preference in DataStore; manifest `<queries>` entry for TTS engine visibility on API 30+. No new permissions, no data collected

### Changed
- Google Play Billing Library 8.0.0 → 9.1.0 (Google's recommended version for latest monetization features; no code changes needed in `InAppPurchaseManager`, zero deprecation warnings)
- Toolchain: Kotlin 2.0.21 → 2.2.21, KSP → 2.2.21-2.0.5 (KSP2), Room 2.6.1 → 2.8.4 (KSP2 compatibility), AGP 8.7.3 → 8.13.2, Gradle wrapper 8.9 → 8.13 — required chain for Billing 9's Kotlin 2.3 metadata; R8 metadata warnings resolved

## [3.2.2] — 2026-07-30

### Changed
- Google Play Billing Library upgraded 7.0.0 → 8.0.0 (`billing-ktx`) — required by Play policy for all app updates from Aug 31, 2026. No code changes needed: `InAppPurchaseManager` already on modern APIs (`PendingPurchasesParams`, `queryPurchasesAsync`, `queryProductDetails`); none of the APIs removed in 8.0 were in use
- versionCode 21 → 29 (jumps past bundles 26/28 previously uploaded to non-production tracks), versionName 3.2.2

### Infrastructure
- Legal pages hosting restored: source repo renamed to `badgr_bolt-src` (private); new public docs-only `badgr_bolt` repo serves GitHub Pages at `ch405-l9.github.io/badgr_bolt/` (privacy policy, terms, account deletion) — fixes Play Console "invalid account deletion link" Data safety violation (URL was 404 because Pages cannot serve from a private repo)
- Repo hygiene: `.gitignore` patterns added for plaintext credential stores

## [3.2.1] — 2026-06-24

### Fixed
- AccountViewModel: Email input sanitization strengthened — `sanitizedEmail()` now strips non-printable bytes (filters to U+0020–U+007E range) in addition to trimming whitespace; applied to sign-in, sign-up, and password reset flows
- Theme: Removed deprecated `window.statusBarColor` API call and its `toArgb` import — eliminates lint warning and aligns with Edge-to-Edge behavior on Android 15+

### Security
- Release source set: `network_security_config.xml` added — enforces `cleartextTrafficPermitted="false"` for all release builds, preventing accidental plaintext traffic in production
- Repo hygiene: `google-services.json` removed from git tracking (Firebase client config; embedded in APK anyway; not a secret, but wrong to store in public repo per own .gitignore policy)
- Repo hygiene: `.idea/` IDE files untracked — were polluting public repo with local machine state

## [3.2.0] — 2026-06-23

### Added
- AccountScreen: Privacy Policy and Terms of Service footer links — opens `badgrtech.com/privacy/badgr-bolt` and `badgrtech.com/terms` in browser via `LocalUriHandler`
- BookDetailScreen / LibraryScreen: AI book summary — extractive NLP via `/summarize` endpoint; Pro feature; result cached in Room (DB v6)
- QuizScreen: Comprehension quiz — 3-question MCQ generated from book text via `/quiz` endpoint; Pro feature (DB v7)
- SpacedRepetitionScreen: SM-2 spaced repetition review deck on quiz questions — scheduling persisted in Room (DB v8)
- LibraryScreen: Book categorization for Pro — auto-classifies library into genre sections on import; DB v8

### Fixed
- CategoryChip: Touch target expanded to 48dp minimum — meets WCAG 2.5.5 accessibility requirement
- AndroidManifest: Explicit `android:dataExtractionRules` backup rules — book database excluded from Android Backup and cloud sync; user credentials not backed up

### Security / Compliance
- Production readiness audit pass — 5 audit documents added to `docs/`
- Legal docs updated — Data Safety form guide for AI features

## [3.1.4] — 2026-06-19

### Fixed
- ReaderScreen: Landscape orientation now shows word display — in portrait the layout is unchanged; in landscape switches to a side-by-side Row with the word display filling the left and a scrollable controls panel (340dp) on the right
- ReaderViewModel: WPM changes made on the reader screen now persist to DataStore — settings page immediately reflects the updated value (was only updating local state, never writing to preferences)

## [3.1.3] — 2026-06-19

### Added
- ReaderScreen: Seekable `Slider` replaces `LinearProgressIndicator` — tap anywhere to jump position
- ReaderScreen: WPM buttons now show `−25` / `+25` text labels, visually distinct from word-skip controls
- ReaderScreen: Time-remaining estimate below WPM label (`~N min left` / `~Xh Ym left` / `< 1 min`)
- LibraryScreen: IMAGE format shows "OCR — Coming soon" badge and is disabled; prevents broken /convert call
- ProGate: `largeFileImport` gate — `true` for Pro, controls 20 MB vs 100 MB upload cap
- AccountScreen: Password visibility eye icon on sign-in, sign-up, and delete-account password fields
- AccountScreen: Verification banner updated — now reads "inbox and spam folder"
- AccountScreen: "I've verified — refresh" button calls `user.reload()` immediately
- AccountScreen: Background poll every 5 seconds auto-refreshes verification state — no log out/in required
- AccountViewModel: `refreshVerificationStatus()` calls `user.reload().await()` to bypass Firebase cache

### Changed
- BookRepository: `MAX_IMPORT_BYTES_FREE` = 20 MB; `MAX_IMPORT_BYTES_PRO` = 100 MB; upsell shown to free users on oversized file
- ApiClient: `readTimeout` raised 60 s → 120 s (Render cold-start ~50 s + processing headroom)
- ApiClient: `writeTimeout` raised 60 s → 300 s (100 MB upload on 4G ~80 s; 300 s covers slow connections)
- ApiClient: `HttpLoggingInterceptor` level gated on `BuildConfig.DEBUG` — `BODY` in debug, `BASIC` in release

### Fixed
- InAppPurchaseManager: `ITEM_ALREADY_OWNED` (code 7) now calls `queryExistingPurchases()` to restore Pro entitlement instead of showing "Purchase failed" error
- ReaderViewModel: `onCleared()` uses `GlobalScope.launch(Dispatchers.IO + NonCancellable)` — guarantees Room progress write after `viewModelScope` cancels; prevents data loss on swipe-dismiss
- BookRepository: Word file write is now atomic — writes to `.tmp` first, then `renameTo(target)`; prevents corrupted JSON on mid-write crash
- ApiClient: `HttpLoggingInterceptor.Level.BODY` in release builds caused OOM on large upload + response payloads; fixed by gating on `BuildConfig.DEBUG`

### Backend (badgr-text-service)
- Raised `MAX_FILE_BYTES` 20 MB → 100 MB
- Replaced `await file.read()` with 64 KB chunked streaming to temp file — eliminates RAM exhaustion on large uploads
- Type validation (MIME check) now executes before reading any file bytes
- Added `except HTTPException: raise` guard before broad `except Exception` to prevent swallowing 413 responses

## [2.6.0] — 2026-03-14

### Added
- Terms of Service: docs/terms_of_service.html published to GitHub Pages
- AccountScreen: Pro status card distinguishes Lifetime Members from Monthly subscribers
- AccountScreen: Lifetime Members see permanent access confirmation and thank-you message
- AccountScreen: Monthly subscribers see active subscription copy and Google Play manage link
- AccountScreen: "No account required" info card on sign-in screen explains offline-first
  free tier and what an account enables
- AccountScreen: "Forgot password?" button triggers Firebase password reset email
- AccountScreen: Snackbar feedback on password reset and verification email resend
- InAppPurchaseManager: activeSku StateFlow exposes active product ID to UI layer
- AccountViewModel: activeSku, isEmailVerified, resendStatus StateFlows
- AccountViewModel: resetPassword(), resendVerificationEmail(), clearResendStatus()
- Upgrade buttons on Settings and Stats screens now navigate to Account tab
- MainActivity: onNavigateToAccount lambda passed to SettingsScreen and StatsScreen

### Changed
- CloudSyncManager: all sync operations gate on isVerifiedForSync (signed in + email verified)
  Resolves TD-007. Account creation and sign-in remain open without verification.
- CloudSyncManager: resendVerificationEmail() suspend function added
- AccountScreen: scrollable layout on both signed-in and signed-out states
- ProGate: PRIVATE_ROLLOUT_ALL_OPEN = false — billing gate live, entitlement enforced

### Fixed
- TD-007: Email verification now enforced for cloud sync operations
- Settings Unlock button: was onClick no-op, now navigates to Account tab
- Stats Upgrade button: was onClick no-op, now navigates to Account tab

### Known Issues
- TD-004: Deprecated statusBarColor in Theme.kt (deferred to 2.5.6)
- TD-006: No unit or instrumentation tests (pre-launch)

### Next Milestone
- 2.6.1: Signed AAB upload to Play Console, closed beta (5 testers)

## [2.6.0] — 2026-03-14
### Added
- docs/terms_of_service.html: Terms of Service published to GitHub Pages
- AccountScreen: email verification reminder banner shown to unverified signed-in users
- AccountScreen: "Resend verification email" button
- AccountViewModel: isEmailVerified StateFlow; resendVerificationEmail() function
### Changed
- CloudSyncManager: all sync operations (syncBooks, pushBook, pushProgress, fetchRemoteBooks,
  fetchProgress) now check isVerifiedForSync (signed in + email verified) — resolves TD-007
- CloudSyncManager: resendVerificationEmail() suspend function added
- Account creation and sign-in remain open without email verification
### Fixed
- TD-007: Email verification now enforced for cloud sync. Free/offline reading unaffected.
### Known Issues
- TD-004: Deprecated statusBarColor in Theme.kt (deferred post-launch)
- TD-006: No unit or instrumentation tests
### Next Milestone
- 2.6.1: Signed release AAB, closed beta invites (5 testers)

## [2.3.6] — 2026-03-14
### Changed
- ProGate.kt: PRIVATE_ROLLOUT_ALL_OPEN flipped to false
- Pro entitlement now enforced via verified Google Play purchase only
- Free users see upgrade prompts on Pro-gated features
### Release gate
- Regression tested on Play Console license tester before tag
- This commit marks billing going live
### Next Milestone
- 2.5.2: Punctuation pauses

## [2.5.2] — 2026-03-14
### Added
- Punctuation pause system: reader automatically slows at sentence boundaries
- OrpEngine: hasSentenceEndingPunctuation() and hasClausePunctuation() helpers
- UserPreferences: sentencePauseMultiplier (default 2.0x) and clausePauseMultiplier (default 1.5x)
- UserPreferencesRepository: setSentencePauseMultiplier() and setClausePauseMultiplier()
- SettingsViewModel: expose punctuation pause multiplier setters
- SettingsScreen: Punctuation Pauses section with dual sliders (1.0x–3.0x range)
  - Sentence endings (. ? !) slider with live multiplier display
  - Clause separators (, ; :) slider with live multiplier display
- ReaderViewModel: sentencePauseMultiplier and clausePauseMultiplier StateFlows from DataStore
- ReaderViewModel: playback logic detects punctuation in last word of chunk, applies multiplier to delay
### Changed
- Reading experience now respects natural language rhythm
- Sentence endings pause 2.0x by default for comprehension
- Clause separators pause 1.5x by default for natural pacing
- Chunk reading applies pause to final word in each chunk
### Fixed
- gradle.properties: added android.useAndroidX=true and android.enableJetifier=true (build configuration)
### Notes
- Punctuation detection works seamlessly with 1-4 word chunk sizes
- User-configurable multipliers persist to DataStore
- Commits: 9cbd5aa, 87f5afd

## [2.5.1] — 2026-03-14
### Changed
- CloudSyncManager: removed unused requirePro() method
- LibraryViewModel: added TAG constant for consistent logging
- CloudSyncManager: extracted Firestore collection names to constants (COLLECTION_USERS, COLLECTION_BOOKS, COLLECTION_PROGRESS)
### Notes
- Code maintainability improvements with no user-facing changes
- Commit: 97c6096

## [2.5.0] — 2026-03-14
### Added
- ChunkWordDisplay.kt: composable for 1-4 word chunk reading
  - Single word: full ORP display (unchanged)
  - Multi-word: first word gets ORP focal treatment, context words dimmed at 85% size
- ReaderViewModel: chunkSize StateFlow from DataStore, adjustChunkSize(delta) function
  - Playback delay scales with chunk size: showing N words takes N word-intervals
  - Skip seconds accounts for chunk size when calculating words to jump
- ReaderScreen: live chunk size controls (- / count / +) below WPM row
- SettingsScreen: Default Words at a Time section (1/2/3/4 buttons) with description
- UserPreferences: chunkSize field (default 1)
- UserPreferencesRepository: setChunkSize(), coerced 1-4
- SettingsViewModel: setChunkSize()
### Changed
- SettingsScreen: version string updated to v2.5.0 (build 6)
- SettingsScreen: removed typographic special characters to prevent Kotlin compile issues
### Next Milestone
- 2.5.2: Punctuation pauses (smart slowing at . , ? !)
- 2.5.3: Bookmarks and notes

## [2.4.4] — 2026-03-14
### Added
- ReaderFonts.kt: 6-font registry combining community favourites and neurologically
  optimised fonts — System Mono, JetBrains Mono, Literata, Merriweather,
  Atkinson Hyperlegible, Open Sans
- Font picker in Settings: each option shown in its own typeface with label,
  subtitle, and MONO badge for fixed-width fonts
- fontIndex persisted to DataStore via UserPreferencesRepository
- ReaderViewModel: fontIndex StateFlow sourced from DataStore
- ReaderScreen: currentFontFamily derived from fontIndex, passed to OrpWordDisplay
- Google Fonts downloadable font XML declarations for all 5 non-system fonts
- font_certs.xml: Google Fonts provider certificate array
### Changed
- UserPreferences: added fontIndex field (default 0 = System Mono)
- SettingsViewModel: added setFontIndex()
### Notes
- Mono fonts (index 0, 1) labelled with MONO badge — best ORP focal stability
- Variable fonts (index 2–5) more comfortable for long sessions, slight ORP shift
- Custom font upload planned post-3.0.0
### Next Milestone
- 2.4.5: Wire open_book achievement on import; default WPM from Settings

## [2.4.3] — 2026-03-14
### Fixed
- ORP color selection now correctly applied in reader: ReaderViewModel exposes
  orpColorIndex StateFlow from DataStore; ReaderScreen maps index to Color and
  passes it to OrpWordDisplay, guide line Canvas, progress bar, and play FAB
- Delete confirmation dialog added to BookRow: tapping the trash icon now shows
  an AlertDialog with book title, Cancel and Delete (red) buttons before removal
### Changed
- ReaderViewModel: showOrpColor and orpColorIndex both sourced from DataStore
  via UserPreferencesRepository — changes in Settings reflected immediately in reader
- ReaderScreen: progress bar and play FAB now use currentOrpColor for visual consistency
### Next Milestone
- 2.4.4: Wire open_book achievement on import

## [2.4.2] — 2026-03-14
### Added
- Red (#E53935) added to ORP color palette as option 4
- DOCX and IMAGE import restored to LibraryScreen via FAB + ModalBottomSheet format picker
- Library empty state: descriptive placeholder with emoji instead of plain text
- Settings: System / Light / Dark theme mode selector — persisted to DataStore
- MainActivity: observes themeMode preference, overrides system dark/light accordingly
### Changed
- LibraryScreen: three inline import buttons replaced with single cyan FAB (cleaner UX)
- FAB opens a bottom sheet listing all 5 formats with emoji, label, and subtitle
- UserPreferences: added themeMode field (default = 0, system)
- UserPreferencesRepository: added setThemeMode(), coerced 0–2
- SettingsScreen: version string updated to v2.4.2
### Known Issues
- TD-004: Deprecated statusBarColor in Theme.kt (deferred to 2.5.x)
- TD-006: No unit tests
- TD-007: Email verification not enforced
### Next Milestone
- 2.4.3: Wire open_book achievement on import; consider WPM chart in Stats

## [2.4.1] — 2026-03-14
### Added
- AchievementToast.kt: auto-dismissing slide-in banner (3s) shows emoji, title,
  and description when achievements unlock during a reading session
- ReaderScreen: AchievementToastHost overlay wired to newAchievements StateFlow
- StatsScreen: achievement chips now tappable — ModalBottomSheet shows full
  description, category, unlock condition, and locked/unlocked status
- StatsScreen: newly unlocked achievements (last 10 seconds) pulse with
  InfiniteTransition scale animation until user navigates away
### Next Milestone
- 2.4.2: Wire open_book achievement on import; consider Firestore achievement sync for Pro

## [2.4.0] — 2026-03-14
### Added
- AchievementEntity.kt: Room entity for persisting unlocked achievements
- AchievementDao.kt: DAO for achievement unlock and query operations
- AchievementDefinitions.kt: 20 achievement definitions across 5 categories
- BoltRank enum: SPARK / BOLT / FLASH / STORM / THUNDER — dynamic rank based on effective WPM
- AchievementsEngine.kt: Pure evaluation engine — takes stats snapshot + session context, returns newly unlocked IDs
- BookDatabase migration 4→5: adds rewindCount to reading_sessions, creates achievements table
- ReadingSessionRepository: streak computation, baseline vs recent WPM improvement, consistency check, Bolt Rank, achievement checking on recordSession
- ReaderViewModel: active reading time tracking (excludes pauses), rewind counter, session recording on saveProgress, newAchievements StateFlow
- StatsViewModel: exposes unlockedAchievements StateFlow from AchievementDao
- StatsScreen: Bolt Rank card, 4-column achievement grid (locked/unlocked states), streak card
### Changed
- ReadingSessionEntity: added rewindCount field (default 0)
- ReadingSessionDao: added getFirstFive/LastFive/LastTen/RankSessions and getQualifyingDays queries
- BookDao: added bookCount() query
- BookDatabase: version 4→5, achievementDao() abstract method added
- StatsScreen: ProGate removed — stats and achievements visible to all users
### Known Issues
- TD-004: Deprecated statusBarColor in Theme.kt (deferred to 2.5.x)
- TD-006: No unit or instrumentation tests
- TD-007: Email verification not enforced for app access
### Next Milestone
- 2.4.1: Wire achievement unlock notification in ReaderScreen (Snackbar on session end)

## [2.3.5] — 2026-03-13
### Changed
- InAppPurchaseManager: queryExistingPurchases() visibility changed from private to public to support on-resume restoration
- MainActivity: added onResume() override; calls purchaseManager.queryExistingPurchases() via lifecycleScope if billing client is connected — restores entitlement after app returns from background or device wake
### Next
- 2.3.6: Release commit — flip PRIVATE_ROLLOUT_ALL_OPEN=false, regression check, tag

## [2.3.4] — 2026-03-13
### Changed
- InAppPurchaseManager: entitlement now granted only after verified acknowledgement; already-acknowledged purchases grant immediately (safe); unacknowledged purchases must ack successfully before _isPro emits true — withheld on failure
- acknowledgePurchase() refactored to return Boolean; true = ack OK and entitlement granted, false = ack failed and error surfaced
- Fixed: enablePendingPurchases() updated to PendingPurchasesParams.newBuilder().enableOneTimeProducts().build() — resolves Billing v7 deprecation warning
- onPurchasesUpdated: handlePurchaseList call moved into scope.launch to enforce suspend context
### Fixed
- Race condition: _isPro could be set true before acknowledgement confirmed — purchase could be revoked by Google within 3 days if ack failed silently
### Next
- 2.3.5: Purchase restoration — queryPurchasesAsync on app resume

## [2.3.3] — 2026-03-13
### Added
- UserPreferencesRepository: added IS_PRO key (booleanPreferencesKey); added setIsPro(Boolean) suspend function; isPro field added to UserPreferences data class (default false)
- OrbReaderApp: added userPreferencesRepository singleton; on startup reads persisted isPro from DataStore and restores ProGate before billing reconnects; collector now writes isPro to DataStore on every emission in addition to updating ProGate
### Changed
- Pro entitlement now survives app restart, process death, and device reboot
### Next
- 2.3.4: Wire ProGate.setProEntitlement() to verified purchase acknowledgement

## [2.3.2] — 2026-03-13
### Added
- AccountViewModel: upgraded from ViewModel to AndroidViewModel; added isPro StateFlow sourced from ProGate.isProFlow; added launchSubscription(activity) and launchLifetime(activity) passthroughs to InAppPurchaseManager
- AccountScreen: SignedIn branch now shows Pro status badge (AssistChip) when entitlement is active; shows Monthly and Lifetime purchase buttons when not Pro; Activity sourced from LocalContext
### Next
- 2.3.3: Persist Pro entitlement to DataStore — survive restart and process death

## [2.3.1] — 2026-03-13
### Changed
- ProGate.kt: upgraded isPro from plain Boolean to MutableStateFlow; exposed isProFlow: StateFlow<Boolean> for UI observation and isPro: Boolean for sync access; setProEntitlement() and revokeEntitlement() now update the flow and respect PRIVATE_ROLLOUT_ALL_OPEN flag
- OrbReaderApp.kt: added applicationScope (SupervisorJob + Dispatchers.Main.immediate); collector wires purchaseManager.isPro StateFlow to ProGate.setProEntitlement() on every emission; applicationScope cancelled in onTerminate()
### Fixed
- TD-003: ProGate was not observing InAppPurchaseManager.isPro — entitlement changes during a session were not reflected in feature gates
### Known Issues
- TD-004: Deprecated statusBarColor in Theme.kt (deferred to 2.5.x)
- TD-006: No unit or instrumentation tests
- TD-007: Email verification not enforced for app access
### Next
- 2.3.2: Implement purchase flow — launchBillingFlow, PurchasesUpdatedListener handling

# Changelog
All notable changes to BADGR Bolt are documented here.
Format: [VERSION] — YYYY-MM-DD

---

## [2.2.5] — 2026-03-03

### Added
- OrbReaderApp registered in AndroidManifest.xml (TD-005 resolved)
- ProGuard/R8 enabled for release builds with full keep rules
- Firebase, Crashlytics, Billing, CloudSyncManager, ProGate ProGuard rules
- CHANGELOG.md, THIRD_PARTY_NOTICES.md, docs/DATA_SAFETY.md scaffolded
- Firebase Auth with email/password and email verification
- Firestore cloud sync: books and progress wired to LibraryViewModel and ReaderViewModel
- Firestore security rules: user-scoped production mode
- AccountScreen.kt and AccountViewModel.kt: full auth UI
- CloudSyncManager.kt: Auth and Firestore singleton
- ProGate.kt restructured with PRIVATE_ROLLOUT_ALL_OPEN toggle

### Changed
- fallbackToDestructiveMigration() removed from BookDatabase (TD-002 resolved)
- Backend /convert endpoint restricted to documents only
- Backend /upload-image endpoint added for image files

### Fixed
- Unresolved reference errors in MainActivity from missing account package files
- CloudSyncManager duplicate property declarations from Gemini session

### Known Issues
- TD-003: Google Play Billing not implemented — PRIVATE_ROLLOUT_ALL_OPEN=true
- TD-004: Deprecated statusBarColor warning in Theme.kt
- TD-006: No unit or instrumentation tests exist
- TD-007: Email verification sent but not enforced for app access

### Next Milestone
- 2.3.6: Google Play Billing and entitlement enforcement
