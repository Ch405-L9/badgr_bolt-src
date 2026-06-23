# Play Console Data Safety Form — BADGR Bolt v3.1.4
## Fill-in Guide for Developer | Updated: 2026-06-23

Open Play Console → BADGR Bolt → Policy → Data Safety.
Follow each section below. Items marked ⚠️ require update from any prior submission.

---

## Section 1 — Data Collection and Security

**Does your app collect or share any of the required user data types?**
→ **Yes**

**Is all of the user data collected by your app encrypted in transit?**
→ **Yes**
*(All transmissions use HTTPS/TLS: Firebase, BADGR backend, Google Play Billing)*

**Do you provide a way for users to request that their data be deleted?**
→ **Yes**
*(In-app: Account screen → Delete Account. Web: https://ch405-l9.github.io/badgr_bolt/delete_account.html)*

---

## Section 2 — Data Types

For each data type below, click "Add" and fill in as specified.

---

### DATA TYPE 1 — Email address

**Category:** Personal info → Email address

**Collection:**
- Is this data collected? **Yes**
- Is this data shared? **No**

**Usage:**
- Purpose: **Account management**
- Is this data required, or can users choose whether it's collected? **Users can choose** (no account = no email)
- Is this data processed ephemerally? **No** (stored in Firebase Auth for duration of account)

**Security:**
- Is this data encrypted in transit? **Yes**
- Can users request deletion of this data? **Yes**

---

### DATA TYPE 2 — Crash logs

**Category:** App info and performance → Crash logs

**Collection:**
- Is this data collected? **Yes**
- Is this data shared? **No** *(Firebase Crashlytics is a service provider, not a data buyer)*

**Usage:**
- Purpose: **Analytics** → sub-purpose: **App functionality** (crash diagnosis and stability)
- Is this data required? **Yes** (collected automatically, cannot be opted out)
- Is this data processed ephemerally? **No** (retained by Crashlytics)

**Security:**
- Encrypted in transit? **Yes**
- Can users request deletion? **No** (anonymized — cannot be individually attributed)

---

### DATA TYPE 3 — App interactions (usage analytics)

**Category:** App activity → App interactions

**Collection:**
- Is this data collected? **Yes**
- Is this data shared? **No**

**Usage:**
- Purpose: **Analytics** → **App functionality**
- Required or optional? **Required** (collected automatically, anonymized)
- Processed ephemerally? **No**

**Security:**
- Encrypted in transit? **Yes**
- Deletion? **No** (anonymized aggregate data)

---

### DATA TYPE 4 — Files and documents ⚠️ NEW — add this

**Category:** Files and docs → Other files

**Why this is needed:** As of v3.1.4, Pro subscribers can transmit book text to the BADGR backend for AI Book Summary and Comprehension Quiz generation. The text content of the book is temporarily transmitted but **not stored** after processing.

**Collection:**
- Is this data collected? **Yes** (transmitted to our servers for processing)
- Is this data shared? **No** (processed on our own backend infrastructure; not sold or shared with third parties)

**Usage:**
- Purpose: **App functionality** (generating AI summaries and quiz questions at user request)
- Required or optional? **Optional** (user must explicitly tap "Generate Summary" or "Generate Quiz" — no automatic transmission)
- Is this data processed ephemerally? **Yes** ← Important: processed in memory, not retained after response is sent

**Security:**
- Encrypted in transit? **Yes** (HTTPS)
- Can users request deletion? **Not applicable** (data is not stored — nothing to delete)

---

### DATA TYPE 5 — Reading history / bookmarks (optional disclosure)

*Note: Reading progress (word index) is synced to Firestore for Pro users. Play Console may categorize this under "App activity → Other user-generated content" or "App activity → Other actions."*

**Category:** App activity → Other actions

**Collection:**
- Collected? **Yes** (for signed-in Pro users only)
- Shared? **No**

**Usage:**
- Purpose: **App functionality** (cross-device sync of reading position)
- Required or optional? **Optional** (only for Pro subscribers who are signed in)
- Ephemeral? **No** (persisted in Firestore)

**Security:**
- Encrypted in transit? **Yes**
- Can users request deletion? **Yes** (Delete Account flow removes all Firestore data)

---

## Section 3 — Summary Verification Checklist

Before submitting the form, confirm:

- [ ] Email address row → Account management → Optional → Encrypted → Deletion: Yes
- [ ] Crash logs row → Analytics → Required → Encrypted → Deletion: No
- [ ] App interactions row → Analytics → Required → Encrypted → Deletion: No
- [ ] Files and docs row → App functionality → **Optional** → **Ephemeral: Yes** → Encrypted → Deletion: N/A
- [ ] Reading history/progress row → App functionality → Optional → Encrypted → Deletion: Yes
- [ ] Review the auto-generated "Data Safety" section preview at the bottom of the form
- [ ] Confirm no data types are checked that the app does not actually collect
- [ ] Save and submit for review

---

## Section 4 — Links to Include in Play Console

**Privacy Policy URL:**
`https://ch405-l9.github.io/badgr_bolt/privacy_policy.html`

**Account deletion URL** (required by Google for apps with account creation):
`https://ch405-l9.github.io/badgr_bolt/delete_account.html`

---

## Section 5 — Note on Custom Domain

GitHub Pages is currently served from `ch405-l9.github.io/badgr_bolt/` (no custom domain configured). If you set up a custom domain (e.g., `legal.badgrtechnologies.com`), update:
1. CNAME file in `docs/` folder
2. GitHub Pages settings in repo → Settings → Pages → Custom domain
3. DNS record pointing to GitHub Pages IPs at your domain registrar
4. Privacy Policy URL and Account deletion URL in Play Console
5. Privacy policy link inside the app (if hardcoded)
