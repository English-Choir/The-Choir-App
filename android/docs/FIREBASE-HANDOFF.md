# Firebase operations — The Choir App

## Live project

- Owner account used for every setup/deployment action: **englishchoir00@gmail.com**.
- Project: **The Choir App** (`the-choir-app-39f0a`), number `243157577573`, Spark plan.
- Android app: `com.phantominnovation.choir`, Firebase app ID `1:243157577573:android:9e6e6414c793961f853b8c`.
- Firestore: `(default)`, Standard edition, `asia-south1` (Mumbai).
- Auth: email/password. Host email: **englishchoir00@gmail.com**. Host UID: `SU7hE7TOWZcq1lYtfdLs64BPmd32`.
- Additional app host: **roshandicruce@gmail.com**, UID `BxKgJ9CDsSck6md3ClDD8wCYWel2`. Active role added on 2026-10-19; the user must set an app password through the app's password-reset flow. This grants app editing access, not Google/Firebase project administration.
- Console: https://console.firebase.google.com/u/1/project/the-choir-app-39f0a/overview (verify the English Choir account if browser account order changes).

The owner sets their own app password through Firebase. It is separate from the Google account password. Use the app's lock icon to sign in; “Set or reset app password” sends Firebase's password reset email. No password or service-account key belongs in this repository. Firebase's Android client configuration is an app identifier, not an administrative credential.

## Permissions and data

`hosts/{uid}` contains `active: true` for an authorised host. Only project administrators can write roles. An authenticated user may read their own role; the app cannot list roles, create a host, or grant itself permission. Signing in alone never permits editing. The role is read from a confirmed server snapshot, and every write is checked again by Firestore Security Rules. Revoking `active` closes the editing UI and blocks writes without waiting for a token refresh.

`registrations/{stableId}` contains id, sectionId, name, style, tempo, transpose, links, deleted, revision, updatedBy and updatedAt. Public reads support guest browsing. Rules validate the field allowlist, known sections, integer tempo 5–500, transpose −12–12, HTTPS Drive/Docs links (up to 10), identity, revision increments and server timestamps. No voice fields are uploaded. Role assignment follows Firebase's [server-managed role document pattern](https://firebase.google.com/docs/firestore/solutions/role-based-access).

The full reviewed 458-row catalog was seeded. The seed script uses create-only writes, preserving subsequent edits and deletion markers. Stable IDs survive renaming and moving. Removed registrations retain a tombstone; restoring one is an administrator operation (`deleted: false` and a new revision). Transactions require connectivity and check for concurrent updates. Stale editors are rejected instead of overwriting newer data. No changes are queued while offline.

The original catalog stays bundled for first launch/offline browsing, and Firestore caches cloud changes. Existing local preference files are retained, with cloud rows taking precedence. The installed production app had no legacy preferences to migrate at deployment. A future BUP refresh or a device with old local overrides should be reconciled explicitly before rerunning the source importer.

## Deploy and verify

From the repository's `firebase` directory, with Node installed:

```powershell
npm.cmd ci
.\node_modules\.bin\firebase.cmd login:add englishchoir00@gmail.com --interactive
.\node_modules\.bin\firebase.cmd deploy --only auth,firestore:rules --project the-choir-app-39f0a --account englishchoir00@gmail.com
node seed-catalog.mjs
node verify-catalog.mjs
```

Do not authenticate or deploy using another Google account. `admin-session.mjs` selects only the stated account, verifies the OAuth identity with Google, and refuses a mismatch. It uses the CLI's authorised session without exporting tokens or creating service-account keys. The user owns the project and credentials.

`verify-catalog.mjs` compares all original fields with the seed, checks the active host role and confirms roles are not publicly readable. After deliberate host edits, exact source comparison will correctly report a difference; do not overwrite edits merely to make it pass.

## Custom sections (1.7)

The `sections` collection holds host-created sections with `id`, `title`, `other: true`, `createdBy`, and `createdAt`. Clients may create only IDs matching `section-` plus 64 hexadecimal characters; existing sections cannot be overwritten or deleted. The app hashes the normalized, lower-case title to prevent concurrent duplicate creation. Built-in section IDs cannot be shadowed. Registration rules now accept either a built-in section or an existing custom section. The catalog listens to section and registration snapshots, caches both, and handles independently arriving snapshots without dropping registrations. Guests can read but cannot create sections.

The chord sync reads these section definitions and maps each to a separate, sanitized tab name with a stable ID suffix. It creates a missing tab on the first enabled chord request, then includes it in subsequent sheet snapshots. `firebase/releases/maintenance-1.7.mjs deploy-sync` updates the existing authorized Apps Script project while checking the previously deployed source and preserving its manifest. No new permissions or triggers are needed.

## Section rename/removal and web companion (1.8)

`sectionSettings/{id}` overlays `sections` and built-in Other folders with `title`, `removed`, `moveTo`, `revision`, `updatedBy` and `updatedAt`. Active hosts may rename or remove only Other sections. Removal requires an active destination and creates a permanent redirect; clients resolve registrations and chord snapshots through it. Stable registration IDs keep list choices, Drive links and chord overrides intact. Original section definitions and Sheet tabs remain for the existing Sheets sync.

Rules reject main-section changes, invalid destinations, cycles (including batched cycles), stale revisions, writes into removed sections, and changes to a completed removal. Registration edits after a move write the resolved destination. The old raw records remain readable. Android versions below 1.8 should be updated to display these overrides.

Web: https://the-choir-library.bonnyloom256.chatgpt.site, source in `../../web`. Firebase web app ID `1:243157577573:web:48ff235eaa4ced2d853b8c`; the domain is in Firebase Auth's authorized domains. Public browsing uses the existing rules. Guest contributions use anonymous Auth; hosts use the same email/password identities and server membership checks as Android. The service worker caches static assets and browser storage retains only public snapshots for offline reads. All writes require connectivity.

The web build contains only public Firebase config, never admin credentials. Hosting uses Sites; Firebase and Google Drive administration continue exclusively through the verified English Choir session.

## Shared hymn lists

`hymnLists/1`, `hymnLists/2` and `hymnLists/3` are the three shared slots. Fields are `title`, `text`, `choices` (one-based hymn position to registration ID, or empty string for explicitly unavailable), `revision`, `updatedBy` and `updatedAt`. Since 1.6, all users can append hymns and change registration choices. A guest's first save silently creates a Firebase anonymous identity; no host login is needed. Anonymous authentication is enabled in the production project's Auth sign-in configuration. HostAccess treats anonymous identities as guests and keeps the host sign-in screen available.

All saves use transactions and an expected revision. Both client and rules require non-host text changes to preserve the exact existing text plus a newline boundary, and preserve the title. This prevents erasing or rewriting existing hymns, including through a direct API call. Guest additions preserve existing manual choices, including explicit unavailable choices. Hosts retain full editing and confirmed clearing; clearing writes an empty list with the next revision. Client deletion, additional slots, unexpected fields, spoofed authors and stale revisions remain denied. Clients validate up to 200 hymns, a title up to 80 characters and text up to 20,000 characters. Registration editing, chord controls, role management and release publishing retain their separate host/admin restrictions.

All app users can read these lists without authentication. Firestore listeners sync updates and persist the last received data for offline browsing. Saves require internet access; offline writes are not queued. The Android integration test uses independent host and guest Firebase clients against the local emulators to verify anonymous sign-in, guest additions and choices, denied guest clears/replacements, stale edits, remote reads, offline reads, host clearing and reconnection. No live hymn list is used as a test fixture.

## Android release publication

Version 1.9 adds local PDF collection and three resource subsections **within each existing hymn list**. No Firestore schema/rule changes or data migration were necessary. The Android app uses public Google download/export endpoints without account credentials. The web app uses a separate website-restricted public key permitting only `google.apps.drive.v3.DriveFiles.Get` and `google.apps.drive.v3.DriveFiles.Export`, configured by `firebase/releases/setup-public-pdfs.mjs`. It grants no private-file access. Google Drive API is enabled in the same English Choir-owned project. Do not broaden the existing Firebase key or distribute an OAuth/admin token.

The `appReleases/android` document is publicly readable by exact document get. Client listing and all client writes are denied, even for hosts. Publish it only through the administrator session verified as English Choir. Required app fields: `enabled: true`, `packageName: "com.phantominnovation.choir"`, integer `versionCode`, string `versionName`, and a verified `https://drive.google.com/file/d/FILE_ID/view` `downloadUrl`. Additional audit fields can record APK size, SHA-256 and publication time.

For future updates, increment the Gradle version code/name, build `:app:assembleRelease`, run relevant unit/lint/device/rules tests, and verify the signing certificate matches the previously distributed APK. Upload the APK into **The Choir App releases** in English Choir's Drive. Grant anyone-with-link Reader access to the APK only and verify the downloaded bytes. Then update the release document with a concurrency precondition. The app never installs software silently. Existing versions below 1.4 do not contain the Update feature and need one manual installation.

The release build deliberately uses the existing local debug signing configuration for compatibility with copies already distributed. It is non-debuggable. Preserve that keystore; do not rotate it for an in-place APK update. A future Play Store rollout requires a separate signing/distribution plan.

Current release: **1.9 / code 10**, [Drive APK](https://drive.google.com/file/d/1Q_tNJSL54zJu2k_1MzZ1NomQmQmMMDb6/view), published 2026-10-20. APK SHA-256: `f0866c98310998c7f5afd977c0f835d4ba7f154b972f388de453d0100b63f000`. Signing certificate SHA-256 remains `48ebaf84d97351caeaa5115b7b6a86d6bed60f3349a898624ec291b9a0df6dd4`. The APK alone has anyone-with-link Reader access; the release folder remains private. Public download bytes and release reads are verified. Existing registration, chord and hymn-list data are preserved. `firebase/releases/publish-1.9.mjs` records the guarded upload and release publication. Current retention is 1.9 and 1.8.

## Tests

Rules: `npm.cmd test` in `firebase`, with JDK 21 on PATH. Tests run only against `demo-the-choir` and cover guests, viewers, revoked hosts, valid host CRUD, role escalation, schema validation and all 458 seed records.

Android: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedQaAndroidTest` in `android`. QA has its own package (`com.phantominnovation.choir.qa`) and cannot erase the installed user app or connect to the live project by default.

For the real SDK integration test, start `firebase emulators:start --project demo-the-choir --only auth,firestore` (JDK 21), then install `app-qa.apk` and `app-qa-androidTest.apk` and run:

```powershell
adb -s emulator-5580 shell am instrument -w -r -e firebaseEmulators true com.phantominnovation.choir.qa.test/androidx.test.runner.AndroidJUnitRunner
```

The integration test signs in through the Auth emulator, verifies role checks, creates/edits/moves/links/removes a registration through Firestore transactions, reopens the library to check tombstones, rejects stale and offline writes, and verifies revocation/sign-out. All fixture identities and passwords exist only in test sources and local emulators.

For a repeatable single integration run, build `:app:assembleQa :app:assembleQaAndroidTest`, then run `firebase emulators:exec --project demo-the-choir --only auth,firestore "node test/run-android-integration.mjs"` from `firebase`. The script installs only the QA package on `emulator-5580` (or `CHOIR_TEST_DEVICE`) and fails on instrumentation failures. Shared-list offline testing uses the same persistent cache as production; the unique test cache is removed afterward.

The direct-distribution APK uses the existing signing certificate as described above. Play Store distribution has not been configured.
