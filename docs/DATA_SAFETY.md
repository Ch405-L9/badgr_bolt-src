# Data Safety — BADGR Bolt
## BADGRTechnologies LLC — Internal Reference for Google Play Data Safety Form

### Data Collected

| Data Type          | Purpose                        | Encrypted | User Can Delete | Shared With     |
|--------------------|-------------------------------|-----------|-----------------|-----------------|
| Email address      | Firebase Auth account creation | Yes (Firebase) | Yes — delete account | Firebase/Google |
| Reading progress   | Cloud sync for Pro users       | Yes (Firestore) | Yes — delete account | Firebase/Google |
| Book metadata      | Cloud sync for Pro users       | Yes (Firestore) | Yes — delete account | Firebase/Google |
| Crash logs         | Crashlytics stability reporting | Yes | No — anonymized | Firebase/Google |
| App analytics      | Usage patterns (anonymous)     | Yes | No — anonymized | Firebase/Google |
| Book text content  | AI summary and quiz generation (Pro) | Yes (HTTPS) | Not stored server-side | BADGRTechnologies backend (Render.com) |

### Data NOT Collected
- Device identifiers beyond Firebase installation ID
- Location data
- Payment information (handled entirely by Google Play)
- Personal/health/financial data

### AI Feature Data Handling (v3.1.4+)
- **AI Book Summary**: Book text is transmitted over HTTPS to `badgr-text-service.onrender.com/summarize` for extractive NLP processing. The backend does **not** store book content after processing. The generated summary is stored locally in the Room database.
- **Comprehension Quiz**: Book text is transmitted over HTTPS to `badgr-text-service.onrender.com/quiz` to generate multiple-choice questions. The backend does **not** store book content after processing. Generated quiz cards are stored locally.
- **Spaced Repetition (SRS)**: All SM-2 scheduling is computed entirely on-device. No data leaves the device for SRS operations.

### Notes
- Free users with no account: zero data leaves the device (no AI features, no sync)
- Pro users: book text may be transmitted to the backend for AI features (see above)
- Cover images: stored locally only, never transmitted
- The Play Console Data Safety form must be updated to reflect book content transmission for AI features
- Update this file at every MINOR release that adds a new SDK or permission
