# The Choir — Android

A native Android registration library for the Yamaha PSR-SX900, with a pitch-black background and blue accents. Requires Android 8.0 or newer.

## Version 1.9 — Three resource groups inside each hymn list

At the end of **each** shared hymn list, the app shows:

1. **With Offline Chords** — exact chord-sheet names, with any host-enabled entries waiting for sheet sync identified.
2. **Without Offline Chords or Drive links** — also includes unresolved hymns, marked as not available or requiring a registration choice.
3. **Without Offline Chords, with Drive links** — collect only these attachments into one PDF.

The third subsection offers **Create combined PDF**. Public PDFs download directly from Google; native Google Docs export as PDF. PdfBox-Android merges on the phone, preserves hymn/file order and includes repeated Drive IDs once. **Share combined PDF** opens Android's share chooser using a read-only FileProvider URI. The web app performs the same merge in a browser worker and offers a download. No file is uploaded to Firebase or an extra conversion service.

Private, download-disabled, unsupported, encrypted or broken files are listed individually. The app offers **Share partial PDF** only when the result is explicitly incomplete. Limits: 60 distinct files, 20 MB each, 50 MB total input, 300 pages. Cancellation removes temporary inputs. Shared exports live in private app cache; exports older than a day are removed on the next collection. Actual copies saved or shared by the user are retained.

[Download 1.9](https://drive.google.com/file/d/1Q_tNJSL54zJu2k_1MzZ1NomQmQmMMDb6/view). Version code 10; 20,633,933 bytes; SHA-256 `f0866c98310998c7f5afd977c0f835d4ba7f154b972f388de453d0100b63f000`. Existing signing certificate and host session are preserved. Current APK retention is **1.9 and 1.8**.

Validation: 20 JVM tests, three hymn-list UI tests and three PDF device tests passed across focused runs; 11 web logic/PDF tests and six desktop/iPhone WebKit browser tests passed. Both clients downloaded and combined a real public PDF and Google Doc; tests cover ordering, deduplication, cancellation, limits, failed files and sharing access. Release lint passed. Production registrations and shared hymn lists were not used as write fixtures.

## Version 1.8 — Manage Other sections and use the web app

Hosts can choose **Rename / remove** on a section under **Other registrations**. Renaming syncs across devices. Removing a section requires choosing a destination for its registrations and confirming; their IDs, style, tempo, transpose, Drive links and chord availability are preserved. The 13 main sections remain fixed. Google Sheet tabs and chord rows are retained.

The [web app](https://the-choir-library.bonnyloom256.chatgpt.site) works on iPhone and PC with the same Firebase data and existing host accounts. Everyone can browse and contribute to the three shared hymn lists; hosts manage the library, chords and sections and can clear lists. Last synced public data is readable offline. On iPhone, open in Safari and choose **Share → Add to Home Screen**.

Download: [The Choir 1.8 APK](https://drive.google.com/file/d/1gB8VxiYDg0ibSlSMxrlN3G2fTpG_j_PE/view), also at `../outputs/choir-android/The-Choir-1.8.apk`. Version code 9; SHA-256 `a0cd1946ea394286a1180b87fa6ffebed097a62d5b2a92cfa5f41e6078e23645`; 12,186,680 bytes. Uses the existing signing certificate. Firebase rules and the update announcement are published.

Validation: 18 JVM tests, 25 distinct UI/device tests across the suite and focused reruns, one Firebase SDK integration test, 16 rules tests and release lint (0 errors). Web validation covers six logic tests, four desktop Chromium/iPhone WebKit functional tests, second-client updates, and live read-only/offline checks. Tests use isolated demo emulators; no production hymn-list or registration fixtures were created.

Current retention policy: keep APK versions **1.8 and 1.7** locally and in Drive. Original backups, extracted data, source, approved matches, real users and live lists are retained. Release evidence is under `../outputs/choir-android/version-1.8-*` and `../outputs/choir-web/`.

## Version 1.7 — Custom sections and Offline Chords filters

Hosts can open **Sections → Other registrations → Add section**, enter a name, and then add registrations in the new section. Sections sync through Firebase and remain available offline after syncing. New sections always belong under Other registrations; the 13 main sections stay fixed. Names are limited to 60 characters, and duplicate names are rejected. Guest accounts cannot create sections. The registration editor includes the new sections for adding or moving hymns.

The Sections search area now shows the number of registrations with Offline Chords. Section detail pages also show their own counts. **All registrations** adds **All hymns / With Offline Chords / Without Offline Chords** filtering, combined with the existing search and Drive-link filters. Counts and filters follow live chord-sheet matches and host enable/disable overrides, exactly like the amber indicator.

The chord sync supports new sections: first host enable creates a separate sheet tab when needed, with a stable section-ID suffix to prevent naming collisions. Existing chord-sheet rows are preserved.

Download: [The Choir 1.7 APK](https://drive.google.com/file/d/1i2OtHHFS09VxuD0hsf-QD-IW7LcJQIjV/view), also at `../outputs/choir-android/The-Choir-1.7.apk`. Version code 8; SHA-256 `1e06cc0dda7077c57a75bda663c272a7aeb47058c5aa7c79186fb4d50f512188`; 12,170,296 bytes. Firebase rules and the existing background chord-sync script are updated.

Validation: 17 JVM tests, 23 UI/device tests across the full suite and focused reruns, one Firebase SDK integration test, 14 rules tests, 10 chord-sync tests, and release lint (0 errors). Public APK bytes and the existing signing certificate were verified. The emulator upgrade from 1.6 preserved all 458 registrations, 292 Drive-linked entries and host access. The live home screen reports 130 registrations with Offline Chords.

Release retention: keep only **1.7 and 1.6** APKs in Drive and locally. Historical APK links below this release history are retired. Cleanup removes generated build caches, old release logs/screenshots and the original redundant Automatic chord override; source code, the keyboard backup, extraction results, approved matches, live registrations, hymn lists and real accounts are retained. The cleanup records are under `../outputs/choir-android/version-1.7-*`.

## Version 1.6 — Everyone can contribute to hymn lists

Everyone can **Add hymns** and **Find registration / Change registration** in all three shared lists, without a host login. Guest additions go at the end and preserve the existing hymn order and manual choices. Hosts retain **Edit list**, renaming, removal and the confirmed **Clear list** control. Firebase rules enforce these limits, including for direct API writes. Registration-library editing and Offline Chords controls still require host access.

Saves require an internet connection and sync across devices. A silent Firebase anonymous identity lets guests contribute without creating a host account. Concurrent saves are checked against the current revision, so an out-of-date screen cannot overwrite someone else's changes. Previously synced lists remain available offline.

Download: [The Choir 1.6 APK](https://drive.google.com/file/d/1QzGhkiqwUxEH5CFjTLpLwqZFZSjrAEYu/view), also saved at `../outputs/choir-android/The-Choir-1.6.apk`. Version code 7; SHA-256 `912bf8a2a94004dccd179f407e0cf447d75ee112714da0896fc39b292a1fdeff`; 12,153,908 bytes. Uses the existing signing certificate for in-place updates. Public download checksum verified. Release evidence is in `../outputs/choir-android/version-1.6-*`.

Validation: 15 JVM tests, 21 UI/device tests plus the Firebase SDK integration test, 13 Firestore rules tests and release lint passed (0 errors). The integration test verifies a second client's anonymous guest additions and choices, denied clearing/replacement, stale revision protection, offline cache, host clear and reconnection. Production list contents were preserved.

## Version 1.5 — Shared hymn lists and automatic update checks

The app checks the published release whenever it opens or returns to the foreground. A newer version opens the download popup automatically, once per app session for that version. Current versions, unavailable releases and connection failures do not interrupt browsing. The **Update** button still supports manual checks and retry.

The **Hymn lists** tab holds exactly three shared slots. Both authorised hosts can name, edit, select registrations for, and clear a list. Everyone can read all three; changes sync through Firebase. Clearing requires confirmation and clears that slot on every connected device without changing registrations or the Google Sheet. Hosts must be online to save. Previously synced lists and chord names remain readable offline; offline devices receive later changes when reconnected.

Tap **Add hymns** or **Edit list**, paste one hymn per line and choose **Find & save**. Numbered/bulleted input works. Section headings or prefixes such as `Entrance: Beautiful Sunday` restrict matching to that section. Song order and repeated hymns are preserved. Unique matches use registration names and reviewed chord-sheet aliases. Multiple exact matches require a host's choice; partial suggestions are never silently selected. Unknown names are marked **Not available**. **Find registration / Change registration** searches the whole library for a manual correction. Stored choices use stable registration IDs, and concurrent host edits are rejected instead of overwriting each other.

Each selected registration shows its section, name, style, tempo, transpose, chord availability and attached Drive files. **Hard-copy chords** at the end lists exact matching names and sections from the Google Sheet in first-use order, with hymn positions. Repeated sheet entries appear once. Host-disabled chords are excluded and identified below the list; enabled entries still waiting for the sheet are identified separately. **Copy full list** and **Copy chord names** make retrieval convenient. No song lyrics or voice data are added.

The bundled `reviewed_chords.json` preserves the approved 147 sheet rows and aliases as a fallback. The original `offline_chords.json` remains the 135-song Keep input used by the sheet-generation tools. Live Firebase/Sheets snapshots take precedence.

The 1.5 APK and its generated release artifacts were retired under the two-version retention policy.

Validation: 14 JVM tests, 21 device tests, the separately run Firebase SDK integration test, 11 Firestore rule tests, and release lint passed. The integration test verifies independent host/guest clients, persistent offline reads, cross-device clear/reconnect, stale edits and permissions. Final UI screenshots were inspected. The live three slots are empty and ready for use; test hymns were confined to QA/emulators. Evidence is in `../outputs/choir-android/version-1.5-*`.

## Version 1.4 — App updates and another host

The header's **Update** button replaces **BUTTON 1** and is available to guests and hosts. It checks the server's published Android release, compares numeric version codes, and offers **Download APK** only for a newer version. The download opens Google Drive; the user completes installation through Android. Offline checks show a retry message instead of claiming the app is current. Users of 1.3 or earlier must install 1.4 once to gain this button.

Release metadata lives at `appReleases/android`, with `enabled`, `packageName`, `versionCode`, `versionName` and `downloadUrl`. Everyone can read that single document; app clients, including hosts, cannot publish or change releases. APK URLs must be HTTPS Drive file-view URLs. See the Firebase handoff for publication details.

**roshandicruce@gmail.com** is an additional active host. To set the initial app password, tap the lock icon, replace the email with Roshan's address, and tap **Set or reset app password**. Roshan completes the emailed password setup himself, then signs in. No password is stored in the project and no setup email was sent during account creation.

The distributed 1.4 APK is a non-debuggable release build signed with the same existing certificate as 1.3 so it can update in place. Keep that keystore for subsequent versions; switching certificates would break installation over existing copies. This is direct APK distribution, not a Play Store signing setup.

Download: [The Choir 1.4 APK](https://drive.google.com/file/d/1keTCR3uupzMsqN6uu8IaQZ4C_blNoB4w/view), also saved at `../outputs/choir-android/The-Choir-1.4.apk`. The file is owned by English Choir, with anyone-with-link Reader access. Verified anonymous download: 12,102,031 bytes, SHA-256 `aa5d2d9d52464fa88c1633231f4d7e1eb2ede34747bdcec8814a08393ea3b5d4`. The folder remains private.

Validation for 1.4: eight JVM tests and Android release lint passed; 18 device tests passed with the opt-in Firebase emulator integration test skipped; all ten Firestore rule tests passed. Rules and release metadata are deployed. Installation over 1.3 succeeded with the existing host session and all 458 registrations retained. The live server check on the installed APK reports version 1.4 is current. Build and publication evidence is in `../outputs/choir-android/version-1.4-*`.

## Version 1.3 — Offline Chords availability

The [Hard copy chord list](https://docs.google.com/spreadsheets/d/1IBbT9Btrdr6Pivlwfp5HI-CSmUJitiPJWnDTx_o-b7M/edit) is owned by **englishchoir00@gmail.com** and stored in that account's ChatGPT folder. Review is complete for 134 of the 135 original songs. Glory / “Glory in the highest” remains unmatched at the user's request. Its 13 tabs now contain 147 rows, including confirmed copies into other sections and the original titles in parentheses where needed. No approximate matches were silently accepted.

Every registration has an **Offline Chords** indicator. Amber glow means the hard-copy chords are available; dim means unavailable. It has no tap action and opens no file. Sign in as a host, then tap **Change** beside the indicator:

- **Automatic** follows the corresponding section in the Google Sheet.
- **Enabled** glows immediately and queues the current registration name for addition to its section's sheet, unless it already matches a song or alias.
- **Disabled** stays dim even if the name is listed. It preserves the sheet entry.

Add songs below column A's heading. Column B accepts exact alternative registration names separated by `|`. Column C is for notes and does not affect matching. Matching ignores case, accents, punctuation and known section labels, but preserves musical variants such as Dm and waltz. Parenthesized song alternatives are recognized. Matching never crosses sections. Keep the 13 tab names and A1:C1 headings intact; invalid structure pauses publication while the app retains its previous list. Enabling a registration from an extra folder creates that folder's sheet when needed.

The [bound Apps Script project](https://script.google.com/u/1/home/projects/1SzWB1SrbB5PZN8kJ-5ZItlR0eAVjJoZp_yTCX9yalmzVHMsib2xXrPZm/edit) contains `firebase/chords/Code.gs` and `appsscript.json`. `installSync` installs an edit trigger and a one-minute reconciliation trigger. It runs only as English Choir, uses Google-managed OAuth, and stores no credentials in source or the APK. **Both triggers are deployed, authorized and verified live.** Normal sheet edits trigger a refresh; host additions are picked up by the scheduled run, usually within about a minute. Idle runs check for host requests and reread the workbook at most once every five minutes; this full refresh also catches API edits and sheet structure changes. Google may delay individual trigger runs.

The Sheets advanced service reads all tabs in one request using the already-authorized spreadsheet scope. Batched reads and fewer idle refreshes reduce usage of the consumer account's [90 minutes of trigger runtime per day](https://developers.google.com/apps-script/guides/services/quotas). Only host additions use SpreadsheetApp writes. Both the source manifest and deployed manifest enable Sheets API v4.

Firestore holds the public chord snapshot at `chordCatalog/current`, host choices at `chordOverrides/{registrationId}`, and sync health at `chordSync/status`. Host writes require server-validated active membership, a current registration and a matching revision. Client writes to the catalog and sync health are denied. Enabling while offline is rejected; cached availability remains readable offline. The script uses a lock and conditional acknowledgements to avoid duplicate additions after retries. A host disable remains effective across later sheet changes. Changes to registrations, musical settings and Drive files are independent.

Validation: 17 Android device tests pass (including the separately rerun landscape reachability test), JVM tests and Android lint pass, nine Firestore rule tests pass, and nine sync failure/retry tests pass. All 135 native Sheet rows were read back and all 13 rendered tabs were inspected. Live checks with Beautiful Sunday verified that removing its sheet entry dims the app, host enable appends the song once and acknowledges it, and disable stays dim despite later sheet changes. A browser edit independently verified the edit trigger. The song's original row and notes were restored and its control returned to Automatic. Run `node --test chords/sync.test.mjs` from `firebase` for the script tests. The 1.3 APK was retired under the two-version retention policy.

## Version 1.2 — Firebase host registration management

Connected to **The Choir App**, Firebase project `the-choir-app-39f0a`, managed only with **englishchoir00@gmail.com**. Firebase Authentication uses email/password. The owner has set an app password for that email. Tap the lock icon and sign in to add, edit or remove registrations. Guests can browse, search and open existing files. There is no local password or hidden unlock switch.

An authenticated, server-authorised host can use:

- **Add registration** in every section and the full library.
- A registration editor for the name, any of the 20 sections/folders, style, tempo, transpose and multiple Drive files.
- Validation for required names/styles, duplicate names within a section, whole-number tempo from 5–500 BPM, transpose from −12 to +12 and HTTPS Google Drive/Docs links. These musical ranges follow the [PSR-SX900 specifications](https://id.yamaha.com/id/musical-instruments/keyboards/products/arranger-workstations/psr-sx900/specs.html).
- **Delete registration**, with a confirmation naming the registration. Deleting only changes the app's library; it does not delete Drive content or modify the keyboard backup.
- Confirmation before discarding unsaved changes. Losing host access closes open editors, and every write is checked again at the storage boundary.

All 458 reviewed registrations are in Firestore, and host changes sync to every installation. Writes require an internet connection and a successful server transaction. Cached registrations remain readable offline. Stable IDs survive renaming and moving. Deletions use cloud tombstones, preventing bundled rows from returning after reinstalling. The original BUP, workbook and bundled catalog remain unchanged. Legacy preference files remain on the device; cloud rows take precedence. No legacy edits were present in the installed app during this upgrade.

Host test identities exist only in `src/androidTest`; they are excluded from the distributed app. See [Firebase operations](docs/FIREBASE-HANDOFF.md) for account, permissions, deployment and testing details.

## Version 1.10 — edit registrations from hymn lists

Hosts can tap **Edit registration** beneath a selected hymn's musical settings. This opens the existing Sections editor for the shared registration (name, section, style, tempo, transpose and Drive links), with the same validation, delete confirmation and host checks. Saving keeps the current hymn list and slot open. Guests and unmatched hymns have no registration edit control. Editing a registration does not rewrite the hymn list or its choices.

## Use the app

- **Sections** follows the requested order: Entrance, LHM, Glory, Response, Alleluia, Offertory, Holy, Proclamation, Our father, LOG, Communion, Thanksgiving, Recessional.
- **Other registrations** keeps Adoration, Ash, Mission Wedding, SOB, Way of the cross, We believe and Z-Grade in separate folders.
- **All registrations** searches names, styles and section names. Search is case-insensitive and ignores accents. Filters show all registrations, those with links, or those needing links.
- Each registration displays its **button 1 style, tempo in BPM and transpose in semitones**. Positive transpose values have a plus sign. Names and settings come from the reviewed backup extraction; voice data is excluded.
- **Open Drive file** opens the associated file using Android's browser/Drive handler. Where two files were confirmed, **View 2 Drive files** lets you choose either one.
- For an authenticated host, **Add Drive link** saves a missing link. The link icon manages existing links, including adding another file, correcting a URL or removing an association. Viewers see **Host adds Drive link** and cannot change files. Links must be HTTPS Google Drive or Google Docs URLs.
- Added/edited links sync through Firebase and survive app reinstalling. They do not change the original backup, workbook or Drive files. Android backup only includes legacy preference files; Firebase credentials and host roles are not copied by the app's backup rules.
- The registration library works offline. Opening Drive content requires whatever connectivity/access the chosen file needs. This app is a reference library; it does not send settings to the keyboard.

## Included data

The bundled catalog contains **458 registrations**, including **419** in the 13 service sections and **39** in the seven other folders. **292 registrations** have reviewed associations with **298 files**; **166** offer an Add Drive link button. Six registrations have two confirmed files.

Source: `../extraction_work/drive_matches.json`, reviewed on 19 October 2026, from `PSR-SX900(06-09-2026).bup`. All musical settings are preserved, including the user's directly verified CountryWaltz registrations. No new fuzzy matches are inferred by the app.

Confirmed exceptions are retained:

- Entrance / As we gather may: no link. The separate As we gather registration retains its file.
- LOG / Peace I give to you: no link. St. Teresa's retains its own file.
- Our father / Stanes: no link.
- Recessional / Irish: the confirmed **HOLY / Irish.pdf** file.

`tools/import_catalog.py` generates `app/src/main/assets/registrations.json` from that reviewed source using an explicit allowlist of fields. It includes no voices or raw backup. Stable IDs derive from the original registration paths, so similarly named registrations in different sections cannot overwrite each other's saved links.

## Open and run

Open this `android` directory in Android Studio, allow Gradle sync to finish, select the **app** configuration and an emulator or USB-connected Android device, then press **Run**. `local.properties` is configured for the Android SDK already on this computer; change `sdk.dir` on another computer.

Toolchain: Android Gradle Plugin 8.9.2, Gradle 8.11.1, Kotlin/Compose compiler 2.3.21, Compose BOM 2025.04.01, Firebase BOM 34.19.0, compile/target SDK 35, Java 17 or 21. Android Studio's bundled JDK works.

Windows command line (with JAVA_HOME pointing to JDK 17 or 21):

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedQaAndroidTest
.\gradlew.bat :app:assembleRelease :app:lintRelease
```

The device tests need an emulator or connected device. Use `:app:connectedQaAndroidTest` (replacing the old `connectedDebugAndroidTest` task). The QA variant installs as `com.phantominnovation.choir.qa`, so running tests cannot uninstall or erase the normal app's saved data. Instrumented tests cover the reviewed catalog, browsing, host CRUD and permissions, legacy-link migration, persistence, revocation, form validation, delete cancellation and unsaved-change handling.

Installable test APK: `app/build/outputs/apk/debug/app-debug.apk`. It is a debug-signed build for testing and personal use; store distribution needs a release signing configuration.

The app requests internet access for Firebase authentication and cloud sync. It does not request storage, contacts or microphone permissions. Drive opens in an external handler. Passwords are sent to Firebase Authentication and are never logged or stored by the app. QA uses the local `demo-the-choir` project and never automatically connects to production.

