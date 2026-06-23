# Play Console Verification Checklist — BADGR Bolt v3.1.4
## BADGRTechnologies LLC | Build 19 | Date: 2026-06-23

All items marked ⚠️ require action in Play Console before publishing build 19.

---

## A. App Build & Target API

- [ ] Upload new AAB (not APK) generated from `./gradlew bundleRelease`
- [ ] Confirm `targetSdk = 36` is accepted by Play Console for this app type
- [ ] Confirm `minSdk = 26` matches Play Console minimum requirement setting
- [ ] Confirm `versionCode = 19` is higher than the currently live production versionCode
- [ ] Confirm `versionName = "3.1.4"` matches release notes
- [ ] Verify Play Console shows no "target API level" policy warnings after upload

---

## B. Data Safety Form ⚠️ ACTION REQUIRED

The Data Safety form **must be updated** before publishing v3.1.4 due to AI features added in this release.

- [ ] ⚠️ Add "App activity — Other actions" → Book text transmitted to backend for AI processing (summarize, quiz)
- [ ] ⚠️ Confirm data is encrypted in transit (HTTPS — yes)
- [ ] ⚠️ Confirm data is NOT stored server-side after processing (yes — backend is stateless)
- [ ] ⚠️ Confirm disclosure says data is optional / only for Pro users who trigger AI features
- [ ] Verify existing entries still accurate:
  - Email address — Firebase Auth ✅
  - Crash logs — Crashlytics ✅
  - App analytics — Firebase Analytics ✅
  - Reading progress — Firestore (Pro) ✅
- [ ] Confirm "User can request data deletion" → Yes (in-app delete account) ✅

---

## C. Privacy Policy

- [ ] Confirm privacy policy URL is set in Play Console and resolves correctly
- [ ] Confirm `docs/privacy_policy.html` content is accessible (hosted or linked)
- [ ] Confirm privacy policy mentions AI/ML data processing as of v3.1.4
- [ ] Confirm delete account URL (`docs/delete_account.html`) is correctly linked in Play Console under "Account deletion"

---

## D. App Content Declarations

- [ ] Content rating: Confirm questionnaire is complete — no violence, no adult content, no gambling
- [ ] Target audience: Confirm 18+ or general (not designed for children — no COPPA/Families concerns)
- [ ] Ads declaration: Confirm "No ads" — app has no ad SDK
- [ ] News app: Confirm not declared as news app
- [ ] Health app: Confirm not declared as health app
- [ ] Financial app: Confirm not declared as financial app
- [ ] User-generated content: Book imports are user's own files — confirm no UGC moderation concern
- [ ] AI-generated content: App generates AI summaries and quiz questions from user's own book content. Confirm Play's AI-generated content policy is addressed if applicable.

---

## E. App Access Instructions

- [ ] If app has a paywall, provide test account credentials in Play Console "App access" section so reviewers can access all features
- [ ] Provide: a free tier test account (email + password) AND a Pro tier test account if possible
- [ ] Note: Pro features require Google Play Billing; use license testing accounts for reviewer access

---

## F. Pricing & Distribution

- [ ] Confirm app is free to download
- [ ] Confirm in-app products are listed correctly:
  - `badgr_bolt_pro_monthly` — monthly subscription
  - `badgr_bolt_pro_lifetime` — one-time lifetime purchase
- [ ] Confirm pricing is set in all target markets
- [ ] Confirm subscription terms match what is shown in-app
- [ ] Confirm country availability is configured correctly
- [ ] License testing: Add tester Gmail accounts to license testing list in Play Console

---

## G. Release Track & Notes

- [ ] Confirm release is targeting the correct track (Internal → Closed Testing → Production)
- [ ] Write release notes for v3.1.4 in all supported languages (minimum: English):

Suggested release notes (English):
```
v3.1.4 — AI Reading Features
• AI Book Summary — get an instant summary of any book in your library
• Comprehension Quiz — test your understanding with auto-generated questions
• Spaced Repetition Review — smart flashcard deck with SM-2 scheduling
• Bug fixes and performance improvements
```

- [ ] Confirm release notes do not mention beta/test features
- [ ] Confirm rollout percentage is set appropriately (recommend 20% staged rollout for new features)

---

## H. Pre-Launch Report

- [ ] Upload AAB and wait for pre-launch report to complete in Play Console
- [ ] Review pre-launch report for:
  - [ ] Crash rate on robo-test devices
  - [ ] ANR (Application Not Responding) events
  - [ ] Accessibility issues flagged by Google's scanner
  - [ ] Security warnings
  - [ ] Performance warnings
- [ ] Adaptive launcher icon renders correctly on all device shapes in pre-launch screenshots

---

## I. Android Vitals

- [ ] Check Android Vitals dashboard for any regressions vs prior version
- [ ] Confirm crash rate < 1% (Google's threshold for policy concern)
- [ ] Confirm ANR rate < 0.47%
- [ ] Review Crashlytics dashboard (Firebase) for any NEW crash signatures in v3.1.4 debug build

---

## J. App Signing

- [ ] Confirm app is enrolled in Play App Signing (Google manages the upload key)
- [ ] Confirm upload keystore (`badgr_release.jks`) is stored securely and backed up
- [ ] Do NOT upload the private keystore to any source control or cloud storage
- [ ] Confirm signing fingerprint in Play Console matches the key used for this build

---

## K. Policy Status

- [ ] Confirm no active policy violations in Play Console
- [ ] Confirm no pending policy warning emails for this app
- [ ] Review "Policy status" tab in Play Console before publishing
- [ ] Confirm app does not trigger Restricted Permissions concerns (only INTERNET permission declared)

---

## L. SDK Index

- [ ] Check "SDK index" warnings in Play Console after AAB upload
- [ ] Firebase BOM `33.7.0` — confirm no policy warnings
- [ ] Google Play Billing `7.0.0` — confirm no warnings
- [ ] Note: no third-party ad, analytics, or tracking SDKs beyond Firebase
