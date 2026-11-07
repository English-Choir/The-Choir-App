# The Choir — web

Responsive web companion to The Choir Android app. Pitch-black background, blue controls and amber Offline Chords indicators. The public site uses the existing **The Choir App** Firebase project (`the-choir-app-39f0a`), managed only as `englishchoir00@gmail.com`.

Production URL: https://the-choir-library.bonnyloom256.chatgpt.site

## Access and features

Version 1.10 makes **Edit registration** explicit on each selected hymn-list registration for hosts. It opens the same shared editor used by Sections and stays in the current list after saving. Guests can still choose registrations, but cannot edit their underlying data.

Everyone can browse the registrations, open their Drive files, see chord availability, and add hymns or choose registrations in the three shared lists. Existing Firebase email/password hosts can add, edit and remove registrations, control chords, manage Other registrations sections, and edit or clear lists. The web app never grants host membership.

Sections, registrations, chord snapshots, overrides and hymn lists use the same Firestore documents as Android. Updates appear across devices without separate imports. The bundled reviewed catalog is a first-load fallback. Last server snapshots of public library data are cached in browser storage; the service worker caches the application shell. Host membership is always checked against Firebase and is never granted by the public cache. Saving requires connectivity and server transactions. External Drive files are downloaded only when a user creates a PDF collection.

iPhone: open the URL in Safari → Share → Add to Home Screen. PC: bookmark the page or use the browser's Install app option.

## Hymn resources and local PDF collection (1.9)

Each of the three shared hymn lists ends with **three resource subsections**: available Offline Chords (exact sheet names, plus any host-enabled items awaiting sync), hymns without Offline Chords or Drive links (including unresolved hymns), and hymns without Offline Chords that have Drive links. Host overrides determine availability. The PDF button only collects the third subsection. Repeated links are included once, in first-use hymn order; multiple attachments keep their registration order.

The website uses a dedicated worker and pdf-lib to merge downloaded PDFs locally. A website-restricted public Google API key permits only Drive Get/Export requests; it grants no private file access and sends no Google or Firebase host credentials. Metadata determines whether to fetch a stored PDF or export a native Google Doc as PDF. No proxy or conversion server is involved. Files must already allow public reading and downloads; restricted, unsupported, corrupt or encrypted files are reported individually. An incomplete collection offers an explicitly labeled **Download partial PDF** option.

Limits are 60 distinct files, 20 MB per file, 50 MB total input and 300 pages. Cancel terminates the worker. The generated Blob is released when the dialog closes. No downloaded chord file or combined PDF is uploaded to Firebase or added to the service-worker cache.

`firebase/releases/setup-public-pdfs.mjs` creates/reuses the restricted public download key through the English Choir admin session. Keep its key restrictions and Google file sharing unchanged. `src/drive-config.json` is public website configuration. Third-party license texts are included at `/third-party-notices.txt`.

## Section management

The original 13 main sections remain fixed. Hosts may rename or remove any section under Other registrations, including the seven original folders. Removal requires a destination and writes a durable `sectionSettings` redirect. Every registration keeps its stable ID, settings and links; old and offline rows are resolved into the destination. Chord-sheet matches are resolved through the same redirects. Original Google Sheet tabs and their rows are retained. Removed sections cannot be restored or redirected again by app clients. The rules prevent cycles and new registrations targeting removed sections.

`sections/{id}` remains the immutable original definition used by the existing Sheets sync. `sectionSettings/{id}` stores title, removed, moveTo, revision, updatedBy and updatedAt. Older Android versions remain readable but need version 1.8 to display renamed/removed sections correctly.

## Development and tests

`npm ci`, then `npm run dev` serves the app at localhost. `npm run build` creates `dist/` and a versioned service worker. `npm test` checks the catalog, matching, retrieval and section moves.

Browser tests use only the **demo-the-choir** Auth/Firestore emulators. Start the Vite dev server, then from `../firebase` run:

```text
firebase emulators:exec --config firebase.test.json --project demo-the-choir --only auth,firestore "node test/run-web-tests.mjs"
```

The tests cover desktop Chromium and iPhone WebKit, guest contributions, host CRUD, section rename/removal, permissions, and a second browser receiving updates. Emulator mode is compiled out of production.

Firebase's web config in `src/firebase-config.json` is public application configuration, not an administrator credential. No service-account keys, host passwords, OAuth tokens or administrator code are included in the web source.

Publishing uses the Sites workflow and the existing `.openai/hosting.json` identity. Preserve its public audience. Google/Firebase administration must use the verified English Choir session; the Sites publishing identity is separate.
