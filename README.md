# The Choir App

A registration library and hymn-planning app for the **Yamaha PSR-SX900** keyboard, built for an English choir. Manages 458 keyboard registrations across 20 sections, links them to Google Drive chord/music files, and supports shared hymn-planning lists with combined PDF export.

> **Both the Android app and the Web app live in this repository and share the same Firebase backend.**

---

## Apps

### 📱 Android App — [`android/`](./android)

Native Android app built with **Kotlin + Jetpack Compose**.

- Browse & search 458 registrations across 13 Mass sections + custom folders
- Host login (Firebase email/password) for full CRUD on registrations & sections
- Shared hymn lists (3 slots) — anyone can add hymns, hosts manage & clear
- Offline Chords indicator driven by a live Google Sheet sync
- Combined PDF creation from Drive chord files (PdfBox-Android)
- Auto-checks for new APK releases

**Current release:** v1.10 · [Download APK](https://drive.google.com/file/d/1Q_tNJSL54zJu2k_1MzZ1NomQmQmMMDb6/view) · Requires Android 8.0+

### 🌐 Web App — [`web/`](./web)

Progressive Web App built with **Vanilla JS + Vite**.

- Same Firebase data as Android — works on iPhone, PC and any browser
- Full hymn-list browsing, contributing and PDF export in-browser
- Install as PWA: on iPhone open in Safari → Share → Add to Home Screen
- Offline-capable via service worker

**Production URL:** https://the-choir-library.bonnyloom256.chatgpt.site

---

## Project Structure

```
The CHOIR App/
├── android/          # Native Android app (Kotlin / Jetpack Compose)
├── web/              # PWA (Vanilla JS / Vite)
├── firebase/         # Firestore rules, seed scripts, chord sync, release scripts
├── extraction_work/  # One-time data pipeline (BUP → Drive matching → catalog JSON)
└── outputs/          # APK releases and release evidence
```

---

## Firebase Backend

**Project:** `the-choir-app-39f0a` · Managed as `englishchoir00@gmail.com`

| Collection | Contents |
|------------|---------|
| `registrations/{id}` | 458 keyboard registrations |
| `sections/{id}` | Named sections (13 fixed + custom) |
| `hymnPlans/{slot}` | 3 shared hymn-list slots |
| `chordCatalog/current` | Live snapshot from Google Sheet |
| `chordOverrides/{id}` | Host chord enable/disable per registration |
| `appReleases/android` | Latest APK version metadata |

---

## Development

### Android

Open the `android/` directory in Android Studio, let Gradle sync, then run:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedQaAndroidTest   # device tests (emulator/USB)
.\gradlew.bat :app:assembleRelease :app:lintRelease
```

**Toolchain:** AGP 8.9.2 · Gradle 8.11.1 · Kotlin/Compose 2.3.21 · Firebase BOM 34.19.0 · compile SDK 35

### Web

```powershell
cd web
npm ci
npm run dev      # dev server at localhost
npm run build    # production build → dist/
npm test         # 11 unit tests
npx playwright test  # 6 browser tests (Chromium + WebKit)
```

### Firebase Rules & Tests

```powershell
cd firebase
# Deploy rules
.\node_modules\.bin\firebase.cmd deploy --only firestore:rules --account englishchoir00@gmail.com

# Run rule tests against emulator
.\node_modules\.bin\firebase.cmd emulators:exec --config firebase.test.json --project demo-the-choir --only auth,firestore "node test/firestore.test.mjs"
```

---

## Version History

| Version | Date | Key Feature |
|---------|------|------------|
| 1.10 | Oct 2026 | Edit registrations directly from hymn list |
| 1.9 | Oct 2026 | 3 resource groups + combined PDF creation & sharing |
| 1.8 | Oct 2026 | Rename/remove custom sections · Web app launch |
| 1.7 | — | Custom "Other" sections · Offline Chords filters |
| 1.6 | — | Guest hymn-list contributions |
| 1.5 | — | 3-slot shared hymn lists |
| 1.4 | — | In-app APK update checker |
| 1.3 | — | Offline Chords indicator + Google Sheet sync |
| 1.2 | — | Firebase host authentication + registration CRUD |

---

## Notes

- The bundled `registrations.json` is extracted from `PSR-SX900(06-09-2026).bup` — the original keyboard backup. All 458 registrations and 292 Drive file associations were manually reviewed.
- The signing keystore for APK distribution is **not** in this repository. Keep it safe — switching certificates breaks in-place updates.
- Firebase credentials in `web/src/firebase-config.json` and `android/app/google-services.json` are public app configuration, not administrator credentials.
- Production Firebase administration uses `englishchoir00@gmail.com` only.

