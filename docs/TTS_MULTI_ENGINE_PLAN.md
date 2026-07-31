# TTS Multi-Engine Robustness — Design Plan (v3.4.1 target)

Status: **approved design, not yet implemented.** Created 2026-07-30.
Confidence: **HIGH** on architecture (see rating at end).

## The real problem (and why it's mostly already solved)

Concern: "I can only test Google TTS; other engines (Samsung, Vocalizer, RHVoice,
eSpeak, SVOX…) may behave differently." True that the default engine varies by
device and users can switch engines in system settings.

**But the app does not need per-engine code.** The load-bearing safety net already
shipped in v3.4.0 is engine-agnostic:

- Engine reports word timing (`onRangeStart`) → precise word sync.
- Engine does not → **timer-paced fallback** advances the display from narration
  speed, resynced at each sentence boundary.

That detect-and-degrade path is the "auto-detect" being asked for. The guiding
principle for everything below is **detect-and-degrade, never enumerate-and-hardcode.**
We do not special-case Samsung/Vocalizer/etc.

## What actually needs work (smallest → largest)

### 1. Keep system-default engine as the default — NOT a gap
`TextToSpeech(context)` uses the engine the user chose at the OS level. That is
correct behavior; keep it. No change.

### 2. Deep-link to system TTS settings (highest leverage, lowest risk)
Add a "TTS / read-aloud settings" button that fires
`Intent(TextToSpeech.Engine.ACTION_TTS_SETTINGS)`. The OS screen already handles
engine install, engine switch, voice download, speech-rate, and language data —
for **every** engine, with almost no code and zero per-engine testing. This is the
"wide array via settings" ask, delivered by the platform.

### 3. Surface language/voice availability (real multi-engine gap)
Today `setLanguage()`'s `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` result is only
logged. On a non-Google engine that inits fine but lacks the language, read-aloud
would silently do nothing. Fix:
- Call `isLanguageAvailable(locale)` before the first utterance.
- On missing/unsupported → set a `ttsLanguageUnavailable` state, show a clear
  message ("This voice engine doesn't have your language installed"), and offer the
  item-2 deep-link to install voice data. Disable the speaker toggle with the reason
  visible rather than failing silently.

### 4. Optional in-app engine picker (Phase 2 / declinable)
Enumerate `tts.engines` (authoritative — trust over any `pm list packages` grep),
let the user pick, persist the package name, and re-init via the 3-arg
`TextToSpeech(context, listener, enginePackageName)` constructor. Nice-to-have;
item 2 already lets users switch engines through the OS. Only build if wanted.

## Load-bearing implementation risk

Switching engines (item 4) requires tearing down and recreating the `TextToSpeech`
instance, which collides with `ensureTts()` lazy-init, `shutdown()`, and the
`isReady` / `rangesObserved` reset. **Verify a clean re-init cycle empirically
before committing to the picker.** If fiddly, ship items 2–3 (which need no
re-init) and treat the picker as a separate follow-up.

## Validation without a second device

- `tts.engines` authoritatively lists installed engines (the earlier
  `pm list packages` grep caught `com.albustami…speechtexter`, likely a dictation
  app, not a TTS engine — don't trust that).
- To exercise the **no-ranges fallback on real hardware**: on the same Samsung
  device, install a free engine from Play (or use Samsung's built-in), switch the
  **system default** TTS engine in Android Settings → General management → Text-to-speech,
  and replay read-aloud. If the engine omits word ranges, logcat prints
  "TTS word-range fallback engaged" and the display should still track sentences.
- This retires the residual risk cheaply — no second device needed.

## Scope recommendation

Ship the **minimal set first**: item 1 (no-op) + item 2 (settings deep-link) +
item 3 (language check + graceful message). Item 4 (in-app picker) as an optional
follow-up gated on the re-init verification.

## Confidence rating

**HIGH** on the architecture: this is the documented Android pattern, and the
safety net (detect + degrade) is already shipped and device-confirmed on the Google
path. Residual uncertainty is only (a) the engine re-init lifecycle for the optional
picker and (b) the fallback being unverified on non-Google hardware — both retired
by the same-device engine-switch test above. Recommend minimal set first, picker
optional.
