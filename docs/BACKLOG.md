# BACKLOG: BADGR Bolt

Phase 2 & 3 Development Tasks

---

## FEATURE: ADVANCED FILE SUPPORT
- [ ] TASK-201: IMAGE OCR Integration
  - What: Wire the image/* picker to the backend /convert endpoint for Tesseract-based text extraction.
  - Acceptance: Done when an uploaded photo of text loads as an RSVP stream in the reader.
- [ ] TASK-202: PDF/EPUB Cover Extraction
  - What: Automatically extract the first page of a PDF or the EPUB manifest image for the library thumbnail.
  - Acceptance: Done when imported documents show a unique cover instead of the default icon.

---

## FEATURE: ACCOUNTS & CLOUD SYNC
- [ ] TASK-301: Firebase Auth Setup
  - What: Implement Email/Password sign-up and sign-in screens.
  - Acceptance: Done when a user can create an account and log in.
- [ ] TASK-302: Firestore Sync Logic
  - What: Implement CloudSyncManager.kt to push library items and reading progress to Firestore users/{uid}/library/.
  - Acceptance: Done when progress saved on one "device" (emulator) appears after sign-in on another.

---

## FEATURE: TEXT-TO-SPEECH
- [x] TASK-401: TTS Synchronized Playback
  - What: Implement TextToSpeechManager.kt using Android TTS to speak each word in sync with the RSVP display.
  - Acceptance: Done when enabling TTS causes the app to read aloud at the speed matching current WPM.
  - DONE in v3.3.0 (chunk-clock, WPM-driven rate). Superseded by TASK-402 for natural voice.
- [ ] TASK-402: TTS Natural Voice (v3.4.0)
  - What: Decouple narration speed from RSVP WPM (default 1.0×≈175 wpm), sentence-level
    utterances for prosody, A1/A2 display toggle, on-device voice picker.
  - Design: see docs/TTS_NATURAL_VOICE_PLAN.md (approved 2026-07-30).
  - Acceptance: Done when read-aloud sounds natural (punctuation pauses present) and raising
    WPM no longer speeds the voice; default follows at ~175–200 wpm.
- [x] TASK-402: TTS Natural Voice — DONE v3.4.0 (versionCode 31). Decoupled narration
  speed, sentence prosody, dual display (Focus word / Flowing), voice picker.
- [ ] TASK-404: TTS multi-engine robustness (v3.4.1)
  - What: settings deep-link (ACTION_TTS_SETTINGS), pre-speak isLanguageAvailable check
    + graceful message, optional in-app engine picker (tts.engines + 3-arg constructor).
  - Principle: detect-and-degrade, not enumerate-and-hardcode. Design: docs/TTS_MULTI_ENGINE_PLAN.md.
  - Acceptance: on an engine lacking the language, user sees a clear message + settings
    link instead of silent no-op; fallback verified on a non-Google engine via same-device
    default-engine switch.
- [ ] TASK-403: Custom branded voice (Phase 2, cloud neural — B. Lawson samples)
  - Requires backend TTS + Data safety update. Separate initiative.

---

## FEATURE: UX/UI POLISH
- [ ] TASK-501: Light/Dark Dynamic Mode
  - What: Refactor ReaderColors.kt and MainActivity.kt to support a full-screen high-contrast light mode.
  - Acceptance: Done when toggling system theme flips the Library and Settings to a light-friendly palette.
- [ ] TASK-502: Accessibility Audit
  - What: Add contentDescription to all interactive icons and verify 48dp minimum touch targets.
  - Acceptance: Done when Android Accessibility Scanner returns 0 critical warnings.

---

## FEATURE: LEGAL & RELEASE
- [ ] TASK-601: Privacy Policy & Data Safety
  - What: Finalize the in-app Privacy Policy link and Play Console Data Safety form.
  - Acceptance: Done when the app is compliant with Google Play's 2024 policy updates.
