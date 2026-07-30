# TTS Natural Voice — Design Plan (v3.4.0 target)

Status: **approved design, not yet implemented.** Created 2026-07-30.

## Problem (evidence-backed)

v3.3.0 shipped Read Aloud with two defects, confirmed from a device recording
(`$100M Leads`, TTS on at 350 WPM):

1. **Speed coupled to the RSVP dial.** `TextToSpeechManager.setRateForWpm()` uses
   `rate = wpm/175`, so 350 WPM → 2.0× speech, 500 → 2.85×. Robotic, unfollowable.
   User's own calibration: 400 wpm unfollowable, ~200 wpm is their natural ceiling.
2. **No prosody.** `startTtsPlayback()` speaks **one chunk (often one word) per
   utterance**, so the engine never applies sentence intonation or punctuation
   pauses. Audio analysis of the clip: **0 silence gaps >150 ms in 24 s** — a
   continuous run-on. This is the "no natural flow" the user heard.

Root insight (matches Audible/Speechify): **listening speed and speed-reading
speed are different axes and must be decoupled.**

## Decisions (locked)

- **Decouple audio from the RSVP WPM dial.** In TTS mode the voice has its own
  **Narration Speed** control. Default **1.0× ≈ 175 wpm**; range ~0.75×–1.75×
  (≈130–300 wpm-equivalent). RSVP's 60–1200 dial governs visual-only mode
  (TTS off). No hard "cap the dial" hack — full decouple.
- **Sentence-level utterances** for natural rhythm: tokenize the word stream into
  sentences and speak each as one utterance so the engine delivers intonation +
  comma/period pauses.
- **Both display models, user-toggleable** (setting in TTS mode):
  - **A1 — Focus word (ORP):** keep the single-word ORP display; advance it in
    sync with the spoken sentence.
  - **A2 — Flowing text:** sentence/paragraph text with a moving highlight that
    follows the voice (Audible/Speechify style).
- **Voice picker now; custom branded voice later.** A picker among the device's
  installed high-quality TTS voices, with a sensible default. A true "BADGRTech
  voice" from B. Lawson samples **cannot** run on-device (Android TTS can't
  clone) — that is a later cloud-neural phase (ElevenLabs-class) and carries the
  same "book text leaves device" data-safety disclosure precedent set by the AI
  summary feature. Ship the rhythm/decouple fix independently of it.

## Architecture changes

- `data/preferences/UserPreferencesRepository.kt`: add `ttsNarrationSpeed: Float`
  (default 1.0), `ttsDisplayMode: Int` (0 = ORP, 1 = flowing), `ttsVoiceId: String?`.
- `audio/TextToSpeechManager.kt`:
  - Replace `setRateForWpm(wpm)` with `setNarrationSpeed(multiplier)` →
    `rate = multiplier` (baseline 1.0 = engine natural ~175 wpm), clamped 0.5–2.0.
  - Add `availableVoices()` / `setVoice(id)` over `tts.voices` filtered by locale
    and quality; expose to a picker.
  - Add word-boundary callbacks: implement `onRangeStart(utteranceId, start, end)`
    to drive display advance within a sentence.
- `ui/reader/ReaderViewModel.kt`:
  - Sentence tokenizer over `words` (respect existing chapter markers; split on
    sentence-ending punctuation via `OrpEngine.hasSentenceEndingPunctuation`).
  - Rewrite `startTtsPlayback()`: per sentence, speak the full sentence; advance
    the ORP/highlight display by word ranges as `onRangeStart` fires; on utterance
    done, move to next sentence. Narration speed from prefs, **not** WPM.
  - Keep `startTimerPlayback()` unchanged for TTS-off visual mode.
- `ui/reader/ReaderScreen.kt`: in TTS mode, swap the WPM ±25 controls for a
  Narration Speed stepper; add a small display-mode toggle (ORP / Flowing) and a
  voice-picker entry (or route voice picker into Settings).

## Load-bearing risk + fallback

`onRangeStart` reliability is **engine-dependent**. Google's TTS reports word
ranges well; **Samsung's engine (this test device) has historically been spotty.**

- Must verify on-device which engine is active and whether ranges fire. Requires
  the phone re-paired (`adb pair` — wireless port rotates on reconnect).
- **Fallback if ranges are unreliable:** advance the display on **sentence
  boundaries**, timer-pace the words *within* a sentence from the sentence's
  measured spoken duration, resync at each boundary. `onRangeStart` preserves the
  zero-drift invariant; the fallback allows minor intra-sentence drift only.
- A2 (flowing text) tolerates coarser sync than A1, so it degrades more gracefully
  under the fallback.

## Phasing

- **Phase 1 (v3.4.0, ship-now):** decouple + Narration Speed + sentence-level
  prosody + A1/A2 toggle + on-device voice picker. On-device only, no backend,
  no new data collected → Data safety unaffected.
- **Phase 2 (future, separate initiative):** cloud neural custom "BADGRTech
  voice" from B. Lawson samples. Requires backend TTS, streaming/caching, cost
  model, and a Data safety update (book text sent to a TTS processor).

## Verify before/while building

1. Re-pair device; identify active TTS engine (`tts.defaultEngine`); test whether
   `onRangeStart` fires per word.
2. Confirm sentence tokenizer handles abbreviations / decimals without over-splitting.
3. Field-test default 1.0× against the user's "~200 wpm is followable" bar.
