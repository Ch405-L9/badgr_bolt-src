# Command: play-store-legal-update

## Description
BADGR Bolt — Google Play Store legal docs and Data Safety compliance update workflow.
Run this whenever a new feature adds, removes, or changes what data the app collects,
transmits, or stores. Keeps privacy policy, ToS, Data Safety form guide, and GitHub Pages
in sync with the app's actual behavior.

## When to Use
- Adding/removing a data collection feature (AI processing, new SDK, analytics, etc.)
- Changing what data is transmitted off-device
- Changing data retention behavior
- Bumping a version that adds permissions or billing changes
- Preparing a Play Store release

## Trigger
`/play-store-legal-update`

Optional argument: `--version 3.x.x` to set the version being released.

## Workflow

### Step 1 — Read current state
```
Read: docs/privacy_policy.html
Read: docs/terms_of_service.html
Read: docs/DATA_SAFETY.md
Read: docs/DATA_SAFETY_FORM_GUIDE.md
Read: app/src/main/AndroidManifest.xml (permissions)
Read: app/build.gradle.kts (versionName, targetSdk)
```

### Step 2 — Audit data flows
Scan for new or changed data transmissions since last legal update:
```bash
git log --oneline docs/DATA_SAFETY.md   # when was DATA_SAFETY last updated?
git log --oneline --since="<last update>" app/src/main/java -- "*.kt" | head -20
grep -rn "ApiClient\|Retrofit\|OkHttp\|Firestore\|Firebase\|Log\." app/src/main/java --include="*.kt"
```

### Step 3 — Identify what changed
Compare code behavior to current privacy_policy.html and DATA_SAFETY.md:
- New endpoints called? Add to Data We Collect
- New data stored? Add to Data We Collect
- New SDK added? Add to Third-Party Services
- Data previously collected now removed? Update accordingly
- Version number changed? Update footer in both HTML files

### Step 4 — Update legal docs
Apply changes to:
- `docs/privacy_policy.html`:
  - Update "Last updated" date
  - Update footer version string
  - Add/remove data rows in "Data We Collect" section
  - Update "Data We Do Not Collect" section
  - Add/update "How We Use Your Data" cards
  - Update Third-Party Services table
- `docs/terms_of_service.html`:
  - Update "Last updated" date
  - Update footer version string
  - Add/update feature-specific sections (billing, AI, accounts, etc.)
- `docs/DATA_SAFETY.md`:
  - Update Data Collected table
  - Update AI Feature Data Handling section if applicable
- `docs/DATA_SAFETY_FORM_GUIDE.md`:
  - Update or add data type entries for Play Console form

### Step 5 — Verify build still passes
```bash
./gradlew assembleRelease 2>&1 | tail -5
```
(Confirms no code changes broke the build during audit)

### Step 6 — Stage and commit (DO NOT push yet)
```bash
git add docs/privacy_policy.html docs/terms_of_service.html docs/DATA_SAFETY.md docs/DATA_SAFETY_FORM_GUIDE.md
git commit -m "docs: update legal docs for vX.X.X — <brief description of change>"
```

### Step 7 — Present to user for review
Show:
1. Summary of changes made to each file
2. Live URLs after push: `https://ch405-l9.github.io/badgr_bolt/privacy_policy.html`
3. Play Console Data Safety form action items
4. Any manual steps required in Play Console

### Step 8 — Push on user confirmation
```bash
git push origin DEV-BADGR_Bolt-V_0.4.11
```
GitHub Pages rebuilds automatically from `docs/` folder on `DEV-BADGR_Bolt-V_0.4.11` branch.
Deployment takes 1-3 minutes. URL: `https://ch405-l9.github.io/badgr_bolt/`

## Key Files

| File | Purpose |
|------|---------|
| `docs/privacy_policy.html` | Hosted at GitHub Pages — linked in Play Console |
| `docs/terms_of_service.html` | Hosted at GitHub Pages |
| `docs/delete_account.html` | Required by Google Play for account deletion URL |
| `docs/DATA_SAFETY.md` | Internal reference — not deployed |
| `docs/DATA_SAFETY_FORM_GUIDE.md` | Step-by-step Play Console Data Safety form fill guide |

## GitHub Pages Config
- URL: `https://ch405-l9.github.io/badgr_bolt/`
- Branch: `DEV-BADGR_Bolt-V_0.4.11`
- Source path: `/docs`
- Custom domain: **not configured** (CNAME: null)
- HTTPS enforced: yes

## Play Console Links to Update After Push
- Privacy Policy URL: `https://ch405-l9.github.io/badgr_bolt/privacy_policy.html`
- Account deletion URL: `https://ch405-l9.github.io/badgr_bolt/delete_account.html`
- Data Safety form: Play Console → App content → Data safety

## Corporate Standards Checklist
Before considering a legal doc update complete:
- [ ] "Last updated" date is current
- [ ] Version string in footer matches current `versionName` in build.gradle.kts
- [ ] Every data type in the app has a corresponding entry in privacy_policy.html
- [ ] Every data type in the app has a corresponding entry in DATA_SAFETY_FORM_GUIDE.md
- [ ] No data type in "Data We Do Not Collect" is actually collected (even temporarily)
- [ ] AI-generated content disclaimer present if app has AI features
- [ ] Third-party services section lists every SDK that receives data
- [ ] Contact email addresses are real and monitored
- [ ] Delete account flow is functional and linked
- [ ] Build passes: `assembleRelease` → BUILD SUCCESSFUL
