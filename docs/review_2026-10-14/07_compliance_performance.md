# 07 — Release compliance and runtime performance (Android app)

Reviewer lens: Play Store compliance, privacy/security posture, runtime performance of `:app`. Tree: `main` at a2e0392.
Skills applied: `android-review:play-store`, `privacy-audit`, `security-audit`, `performance` (their checks are folded
into the brief's one-line format; the coverage table is in section 7). Builds run today: `:app:assembleRelease`
and `:app:bundleRelease` (both green).

## Summary (10 lines)
1. Nothing in the app phones home: no `INTERNET`, no permission at all, no third-party SDK, no identifiers, no logging in `app/src/main`. The GDD's "no backend, telemetry, ads, billing, AI, timers" holds in the shipped manifest and dependency graph.
2. Three store blockers are all identity, not code: the `com.example` application ID, no release signing (the trial switch signs release with the debug key), and the Android Studio template launcher icon (which is also the Android 12+ splash icon).
3. One data-loss defect of high severity, not yet reproduced on a device: the save row crosses SQLite's 2 MB cursor window somewhere between day 350 and 500 of an endless run; Room reads the whole row, so the next cold start cannot load the save and "Start over" cannot clear it either.
4. Every accepted command re-encodes the whole run as JSON twice (payload, then escaped again into the envelope: 13 % of the save is escaping) and upserts one row; at day 1,000 that is 3.7–4.7 MB per tap.
5. targetSdk 37 meets the 2026 target-API requirement; 16 KB page alignment verified with `zipalign -P 16`; R8 and resource shrinking are on with resource-name obfuscation; release APK 5.07 MB, AAB 8.9 MB (per-device download close to the APK).
6. Portrait lock is ignored on large screens from Android 16; the app has no landscape or tablet layout (lint `DiscouragedApi`, `ConfigurationScreenWidthHeight` ×6).
7. Cold start runs catalog build and `content.validate()` on the main thread inside the ViewModel factory; nothing is measured (`am start -W` never recorded); no app baseline profile, only the library profiles (8 KB).
8. Bitmaps: no app cache; Compose's `imageResource` remembers per call site, every sprite is 16–80 px (336 of 598 are 56×56), so residency is bounded by what is composed and decodes are sub-millisecond. Not a problem.
9. Room and DataStore never touch the main thread; all 14 `items()` call sites carry keys; the immutable `GameState` copies are cheap next to the JSON encode (End Day compute p95 1.7–6.7 ms on the JVM, 21 ms on the emulator at day 120).
10. Play Console work for a zero-collection game is small: Data Safety all "No", a one-page privacy policy on a public URL (required for every app), IARC with fantasy violence and no gambling, paid-app merchant setup.

## 1. Release blockers (must fix before any store build)

| # | Severity | Defect | Action | Evidence |
|---|---|---|---|---|
| B1 | blocker | `applicationId` and `namespace` are the Studio template `com.example.blacksmithproject`; Play refuses `com.example.*` and the ID can never change after the first upload | Owner picks `<APP_ID>`; run the rehearsed rename (full or applicationId-only); tell testers their saves do not follow | `app/build.gradle.kts:8`, `:14`; `docs/RELEASE_RUNBOOK.md` §2, §2.5 (rehearsed), §2.7 |
| B2 | blocker | No release `signingConfig`; the `-PtrialSuffix` block signs a release build with the debug key, so a debug-signed "release" can be produced by accident | Create the upload keystore outside the repository, wire `signingConfigs.release` from `local.properties`/env, delete the trial block, enrol in Play App Signing | `app/build.gradle.kts:31-37`; `app/build/outputs/apk/release/app-release-unsigned.apk`; runbook §3.1, §4 |
| B3 | blocker | Launcher icon is the Android Studio template (green `#3DDC84` grid background, white robot foreground), unchanged since the first commit; on Android 12+ the system splash shows the same icon | Commission the icon with the art pack (adaptive foreground/background/monochrome + 512×512 store PNG + 1024×500 feature graphic); `docs/ART_BRIEF.md` has no icon entry | `app/src/main/res/drawable/ic_launcher_background.xml:8`, `ic_launcher_foreground.xml`; `git log` → `047e058` only |
| B4 | high | Save row larger than the 2 MB SQLite `CursorWindow` cannot be read back: the run envelope grows 3.3–4.2 KB a day without bound and crosses 2,048 KB around day 350–490; Room reads `SELECT *` through the framework cursor; the write succeeds, the next `load()` throws `SQLiteBlobTooBigException` → `SaveFailure.Io` → recovery screen; "Start over" calls `repo.load()` again and fails the same way, so the player is locked out until app data is cleared | Confirm with an instrumented test that commits a 2.5 MB payload and calls `load()`; then either store the payload outside the row (file, Room keeps the pointer), split the run into bounded rows (state / history / events), or move to Room's bundled SQLite driver (note: that changes the `KeepDamagedFile` corruption path). Endless play is LOCKED, so this is a defect, not a tuning question | `app/src/main/java/com/example/blacksmithproject/data/SaveStore.kt:38-39` (whole-row query), `:130-132` (framework open helper); `app/src/main/java/com/example/blacksmithproject/GameSession.kt:110`, `:120-121` (only `FileDamaged` is quarantined; `Io` returns `Failed`); `docs/DECISIONS.md:2998-3005` (Save KB 1,081 at day 200 → 2,060 at day 500), `:3039-3046` (1,316 → 2,724); largest save ever loaded on a device: 375,712 bytes (`app/src/debug/assets/scenarios/long_storage.json`); no `CursorWindow` mention anywhere in `app/src`, `core/src`, `docs` (hypothesis until the test runs) |
| B5 | high | `versionCode 8` / `versionName 0.8.0` while `CHANGELOG.md` holds 47 lines under `[Unreleased]`; a store build must carry a new code, and the R8 `mapping.txt` of that exact build must be uploaded or Android vitals traces are unreadable | Bump per CLAUDE.md on the release commit; add "upload `app/build/outputs/mapping/release/mapping.txt`" to the runbook's upload step | `app/build.gradle.kts:17-18`; `CHANGELOG.md:8-55`; `app/build/outputs/mapping/release/mapping.txt` (46 MB, produced) |

Console-side items (Data Safety, privacy policy URL, IARC, merchant account) are not code defects; they are in section 5 as NEEDS_CONFIRMATION.

## 2. Ranked findings (all lenses)

| # | Severity | Finding | Action | Evidence |
|---|---|---|---|---|
| B1–B5 | see above | | | |
| F1 | medium | Every accepted command (price, list, buy, forge, End Day) encodes the full `GameState` to JSON, then encodes that string again inside `SaveEnvelope` (escaped), then upserts one row: 716–822 KB at day 100, 3.7–4.7 MB at day 1,000; "Envelope overhead" is 97–1,129 KB of pure escaping | Store the envelope fields as columns (`SaveEntity.schemaVersion` already exists) and the payload once; compact or cap `events`/`history` growth; both also shrink B4 | `GameSession.kt:239`, `:258`; `core/.../persistence/SaveCodec.kt:35`; `SaveStore.kt:85-88`; `docs/DECISIONS.md:3001-3008` (Encode 9.3 → 43.2 ms on the JVM; "Envelope overhead KB" column = `bytes(envelope) - bytes(payload)`, `core/src/test/kotlin/com/tinyblacksmith/core/ProductionSoakTest.kt:70`, `:81`) |
| F2 | medium | Portrait lock (`screenOrientation="portrait"`) is ignored on displays ≥ 600 dp from Android 16, and the app targets 37, so tablets and unfolded foldables will show the portrait layouts in landscape; six places size by `Configuration.screenWidthDp/HeightDp` | Owner decision (section 8): either accept and test a landscape pass at 600–840 dp, or build width-class layouts; replace the six `LocalConfiguration` reads with `LocalWindowInfo.containerSize` | `app/src/main/AndroidManifest.xml:17`; `app/build/reports/lint-results-debug.xml` → `DiscouragedApi` at manifest:17, `LockedOrientationActivity` ×3, `ConfigurationScreenWidthHeight` at `Dialogs.kt:105,227`, `ForgePanel.kt:205,311`, `ShopPanel.kt:97`, `WeaponStatCard.kt:149` |
| F3 | medium | Cold start is unmeasured. On the path: `GameEngine()` builds `LaunchContent.catalog` and runs `content.validate()` on the main thread inside the ViewModel factory; the first frame is a spinner until `session.load()` decodes the save on `Dispatchers.Default` (decode 6–43 ms on the JVM at day 100–200, more on a phone); no `reportFullyDrawn()` | Measure `adb shell am start -W -n <id>/.MainActivity` on the emulator and one mid-range phone, record TotalTime in PROGRESS; if validate() shows up, run it only in debug builds and tests (content is static) | `MainActivity.kt:13`, `:20-23`; `GameViewModel.kt:868-872`; `core/.../engine/GameEngine.kt:52`; `GameViewModel.kt:259`; `docs/DECISIONS.md:3042` (Decode ms); `docs/UI_UX_REVIEW_2026-10-10.md:125` (spinner first frame) — hypothesis on magnitude |
| F4 | medium | Backup is fully off (`allowBackup="false"`), so a new phone or a factory reset loses the permanent legacy the GDD calls "strong"; the two rule files are the untouched template with `TODO` comments and are inert | Owner decision (section 8). If backup stays off, delete `fullBackupContent`/`dataExtractionRules` and the two files; if it is enabled, write real rules (include `databases/`, exclude DataStore) and note that WAL files are copied beside the DB. Restore across versions is safe: schemas 1..7 migrate, a newer schema opens the recovery screen | `AndroidManifest.xml:6-8`; `res/xml/backup_rules.xml:2-7`, `data_extraction_rules.xml:8`; `SaveCodec.kt:26`, `:180-185`; `GameSession.kt:184`; `SaveStore.kt:59` (Room version 1) |
| F5 | low | No app baseline profile; the 8 KB `baseline.prof` in the APK is the libraries' (Compose, lifecycle); `profileinstaller` arrives transitively, no `:baselineprofile` module | After B1 (the ID changes the profile's package), add a `baselineprofile` module with the macrobenchmark journey: title → Shop → Forge → End Day → shop-day cards | `app-release-unsigned.apk` → `assets/dexopt/baseline.prof` 8,084 B; merged manifest `ProfileInstallerInitializer`; `app/build.gradle.kts` (no module) |
| F6 | low | `assets/art/manifest.json` (184 KB, 3.6 % of the release APK) is packaged but no main or debug source opens it | Move it out of `src/main/assets` (it is a build artefact of `generate_assets.py`) | `app/src/main/assets/art/manifest.json`; `grep -rn "assets.open" app/src` → only `scenarios/` in `ScenarioMenu.kt:66,107` |
| F7 | low | Compose stability: `:core` is not compiled by the Compose compiler and no `stabilityConfigurationFile` names its classes, so the 7 composables taking `GameState`/`Weapon`/`Hero` directly see unstable parameters; strong skipping (default in Kotlin 2.2) makes this instance-equality skipping, so the cost is small | Add `compose_stability.conf` listing `com.tinyblacksmith.core.model.**` (all immutable data classes) and the `reportsDestination` flag once, to confirm | `app/build.gradle.kts` (no `composeCompiler {}` block); 219 `@Composable` in `app/src/main`, 7 with core-type parameters |
| F8 | low | Bitmaps are decoded during composition on the main thread (`ImageBitmap.imageResource`), and two sites decode a drawable only to read its height | Fine for 56×56 art; keep the decode-for-height out of the item lambda (store heights in `WeaponArt`/`Sprites` tables) | `ui/Sprites.kt:243`, `:269`, `:294-306` (ForgeScene decodes 10 bitmaps per site); `ui/shopday/DayCards.kt:77`, `CounterScene.kt:273`; size histogram: 336×56², 98×64², 61×16², 58×48², largest 270×150 |
| F9 | low | Stale version pins under a 2026 BOM: `activity-compose 1.8.0`, `core-ktx 1.10.1`, `lifecycle 2.9.4`; transitives already raise them, so the pins only mislead | Align the catalog to the versions Gradle actually resolves (`:app:dependencies`) | `gradle/libs.versions.toml:3`, `:8`; lint `NewerVersionAvailable` ×7, `GradleDependency` ×6 |
| F10 | low | No `StrictMode` in debug builds; nothing found on the main thread by reading, but there is no guard | Enable `StrictMode.ThreadPolicy` with `penaltyLog` in a debug-only `Application` class | `grep StrictMode app/src` → none; no `Application` class |
| F11 | info | Lint `UnusedResources` ×19 and 41 of 598 PNGs dropped by the shrinker: harmless in release, noise in debug | Leave; the importer owns the set | `lint-results-debug.xml`; release APK holds 557 PNG |

## 3. Compliance detail (play-store, privacy-audit, security-audit)

Clean results, stated so the questions are closed:
- Manifest: zero `<uses-permission>`; the merged release manifest adds only core-ktx's signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, Room's unexported `MultiInstanceInvalidationService`, and the `androidx.startup` provider (EmojiCompat, ProcessLifecycle, ProfileInstaller). Debug-only exported activities (`ArtGallery`, `ShopDayPreview`, `DebugSql`) are absent from the release manifest. `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml:12-85`.
- Network: no `INTERNET`, no HTTP client, no WebView, no cleartext config needed. `grep -rn INTERNET|OkHttp|HttpURLConnection|WebView app core` → none.
- Dependencies: AndroidX (activity, compose, lifecycle, room, datastore), kotlinx (coroutines, serialization), Kotlin stdlib only. `app/build/outputs/sdk-dependencies/release/sdkDependencies.txt`. No Play services, Firebase, Crashlytics, analytics, ads or billing.
- Identifiers and sensitive APIs: the code-audit grep set (ANDROID_ID, advertising ID, IMEI, telephony, installed packages, DexClassLoader, exec, accessibility, device admin, settings writes, MediaProjection, SMS, contacts, location) hits nothing in `app/src/main` or `core/src/main`.
- Logging: no `android.util.Log` or `printStackTrace` in `app/src/main`; the `println` calls live in `core/sim`, which R8 removes (`app/build/outputs/mapping/release/usage.txt` lists 172 `com.tinyblacksmith.core.sim` classes as removed).
- Secrets: none in source or Gradle; no `storePassword`/`keyPassword` anywhere.
- Storage: saves are game state in app-private Room (`tiny_blacksmith.db`), settings in DataStore; no PII, so plaintext is correct. `allowBackup="false"`.
- Release hardening: `optimization { enable = true }` (R8 full), `isShrinkResources = true`, `-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute` only (`app/src/main/keepRules/rules.keep`); resource names obfuscated (`res/y5.png`); `extractNativeLibs="false"`; single `classes.dex`, 19,457 references (`apkanalyzer dex references`).
- 16 KB page size: the only native code is AndroidX's `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` (4 ABIs, 130 KB total); `zipalign -c -P 16 -v 4 app-release-unsigned.apk` → all eight `(OK)`, `Verification successful`.
- Target API: `compileSdk`/`targetSdk` 37, `minSdk` 24 (`app/build.gradle.kts:9-16`); the 2026 deadline (31 Aug 2026) is met with margin. Edge-to-edge is explicit (`MainActivity.kt:17`).
- Sizes: debug APK 21,403,363 B (19.4 MB of unminified dex + 4.1 MB debug scenario assets); release APK 5,073,135 B today (4,641,612 on 10 Oct); AAB 8,947,211 B (all ABIs and densities; Play delivers one ABI, so expect a download near 4.5–5 MB).
- Timers / idle production: no `Timer`, `Handler.postDelayed`, `AlarmManager`, `WorkManager`, no wall clock in gameplay; `System.currentTimeMillis()` only stamps `savedAt` and quarantine names (`SaveStore.kt:86,91,94,139`).

Privacy-audit note: the skill's step 2 asks the user to pick a privacy-policy source; none exists yet (no URL in code, no in-app link; `SettingsSheet.kt` shows only the version), so the three-way comparison collapses to "code collects nothing" — the policy to write is section 5.3.

Security-audit (MASVS) result: STORAGE/CRYPTO/AUTH/NETWORK/PLATFORM not applicable (no credentials, no network, no exported components beyond the launcher); CODE passes except F10; RESILIENCE deliberately absent (no root/tamper detection; appropriate for an offline single-player game); PRIVACY passes by construction.

## 4. Performance detail

Measured on this tree or recorded in docs:
- End Day compute (JVM, forced survival): p95 1.7 ms days 1–1,000, 3.2–6.7 ms days 1,001–2,000 (`docs/DECISIONS.md:3007`, `:3048`). Emulator (`EndDayPerfTest`, Pixel 10 Pro AVD, day 120, 367 weapons): p50 8.3 / p95 21.3 / max 47.1 ms (`docs/PROGRESS.md:1110`). Against the 200 ms budget: fine. The immutable copies per command are not the cost; the serialisation is (F1).
- Save size and (de)serialisation, JVM: day 100 716–822 KB, encode 9–18 ms, decode 6–43 ms; day 1,000 3,661–4,716 KB, encode 23–31 ms, decode 28–37 ms; day 2,000 6.8–8.6 MB, encode 43–116 ms (`DECISIONS.md:3001-3006`, `:3042-3047`). A phone is typically 3–5× slower than the desktop JVM (hypothesis): a listing tap at day 1,000 would hold the UI in `Status.Working` for 100–200 ms plus the SQLite write of 4 MB.
- Threading: `GameSession` computes and encodes on `Dispatchers.Default` (`GameSession.kt:39`, `:196`), Room suspend DAOs run on Room's executor (`SaveStore.kt:36-49`), DataStore on its own IO; the ViewModel builds the shop day and the Shop plan on `Default` (`GameViewModel.kt:312`, `:329`). No `runBlocking`, no `allowMainThreadQueries`, no `commit()`.
- Lists: all 14 `items(` call sites use `key =` (`CommissionBoard.kt:103,119`, `GuildPanel.kt:91`, `Notebook.kt:126,127,160`, `CounterScene.kt:247,258`, `ShopPanel.kt:134`, `StorageSheet.kt:186,198`, `SuppliesSheet.kt:116,153`).
- Memory: `GameViewModel` holds no `Context` (factory uses the `Application` once); caches are per-run pairs (`script`, `shop`, `closed`) replaced on each snapshot. Worst-case bitmap residency if every sprite were on screen at once: 598 PNG, mostly 56×56 ARGB_8888 (12.5 KB each) ≈ 7 MB; a real screen composes well under 1 MB. 4.8 MB of PNG on disk, in `drawable-nodpi`, so no density rescale at decode.
- Not measured anywhere: cold start, frame times on a phone, memory on a phone. Add to PROGRESS once: `am start -W` TotalTime (cold, after `force-stop`), `dumpsys gfxinfo <id>` over the shop day, `dumpsys meminfo <id>` after the Records screens.

## 5. Play Store submission checklist for this app

### 5.1 Build and Console (do in this order)
- [ ] B1 application ID chosen and renamed; `ExampleInstrumentedTest`, emulator scripts and importer updated (runbook §2.3).
- [ ] B2 upload keystore created and backed up off the machine; `signingConfigs.release` reads credentials from `local.properties`/environment, never the repo; trial block deleted; Play App Signing accepted on first upload.
- [ ] B3 icon set replaces the template; store icon 512×512 PNG (no alpha), feature graphic 1024×500, 2–8 phone screenshots of the real UI (9:16).
- [ ] B5 `versionCode` 9+, `versionName` per SemVer; `[Unreleased]` renamed; `mapping.txt` of that build uploaded with the AAB.
- [ ] Upload the AAB (`./gradlew :app:bundleRelease`), not the APK.
- [ ] Pricing: paid app, €1.99 → Google Payments merchant profile, tax and bank details; price per country; "contains ads" = No; "in-app purchases" = No. A paid app can later become free but not the reverse.
- [ ] Category: Game → Simulation (or Strategy); target audience: 13+ (not designed for children, so no Families policy work).
- [ ] Internal testing track first (no Data Safety needed there), then closed testing with the form completed.
- [ ] B4 fixed or at least confirmed before production: a player who reaches day ~400 must not lose the run.

### 5.2 Data Safety form (all answers follow from zero collection)
- "Does your app collect or share any of the required user data types?" → **No**. Nothing leaves the device: no network permission, no SDK that transmits.
- "Is all of the user data collected by your app encrypted in transit?" → not asked when nothing is collected.
- "Do you provide a way for users to request that their data is deleted?" → not asked; Android's "Clear storage" removes everything, which the privacy policy can say.
- Independent security review → No.
- Reconfirm the form whenever a dependency is added; `sdkDependencies.txt` is the audit artefact.

### 5.3 Privacy policy (required for every app, including zero-collection ones)
- Host one page on a public, non-editable URL (not a PDF), enter it in the Console field, and add a link in Settings (the reference lists "link accessible within the app itself"; `SettingsSheet.kt` currently has only the version line).
- Content: developer name and contact matching the Console account; "the app collects, stores off-device or shares no personal or sensitive data; all game data stays in the app's private storage on the device and is removed by uninstalling or clearing storage"; no third-party SDKs that collect; retention = on device only; children: not targeted; date.
- Reference: `data-privacy.md` §7.1–7.2 (policy source https://support.google.com/googleplay/android-developer/answer/10144311).

### 5.4 Content rating (IARC questionnaire)
- Violence: yes, fantasy violence against fantasy creatures in pixel art; characters die and the text says so (hero deaths, sieges); no blood shown in sprites that I could find (16 px fight frames); check the 7 "blood" text hits (mostly the name "Bloodbound") before answering "depictions of blood".
- Gambling: the "smith's wager" visitor stakes the player's gold on forging a blade of a given quality by a deadline (`EncounterCatalog.kt:179`, `:305`) — a skill challenge, not simulated gambling; answer "No" to gambling with real or virtual currency of chance, and keep the wording of the visitor non-casino.
- Language, sexual content, controlled substances, nudity: none found in content strings.
- User interaction, user-generated content, location sharing, personal information: none.
- Expected outcome: PEGI 7 / ESRB E10+ / USK 6 range (fantasy violence).

### 5.5 After upload
- Pre-launch report: review crashes on the Pixel devices it uses; the three debug activities are not in release, so its crawler only reaches the game.
- Android vitals is Google's crash/ANR collection on the OS side, needs nothing in the app, and does not break the no-telemetry rule; it only reads with the mapping uploaded.
- Staged rollout 10–20 %; watch "Row too big" (B4) and `SaveFailure` recovery screens in the vitals traces.

## 6. Prior findings in this lens (brief rule 6)
| Earlier item | Status | Evidence |
|---|---|---|
| Thorough review 2026-10-09 line 124: unhandled load/save failures, no retry | fixed | `GameSession.kt:126-146`, `:194-224` (typed `Result.Failed`, confirmation read, `retry()`); `ui/Failures.kt` |
| Thorough review line 28: "production readiness remains unproven" | still open | B1–B3, B5 |
| Thorough review line 20/22: Room transaction before display, no RNG in sprites | confirmed | `SaveDao.saveBoth` `@Transaction` (`SaveStore.kt:52-56`); `Sprites.kt` animations use `rememberInfiniteTransition` only |
| UI/UX review 2026-10-10 cause 10 (first frame a spinner) | still open; now also a cold-start measurement gap (F3) | `GameViewModel.kt:252` (`UiState.Loading`) |
| Runbook §2.7 (saves do not follow the rename) | confirmed, still pending | `docs/RELEASE_RUNBOOK.md` §2.7 |

## 7. Skill coverage

| Reference (play-store) | Applies | Result |
|---|---|---|
| build-and-signing | yes | B2, B5; targetSdk/R8/shrink pass |
| permissions | yes | zero permissions; pass |
| financial-declaration | no — not a financial app, no payments in-app | N/A |
| data-privacy | yes | 5.2, 5.3 (NEEDS_CONFIRMATION: form and URL not yet created) |
| store-listing | yes | B3; assets list in 5.1 |
| deceptive-behavior | yes | no hidden functionality, no dynamic code; pass |
| spyware-policy | no — no data access or transmission | N/A |
| device-abuse | yes | no settings writes, accessibility, device admin; pass |
| consent-flow | no — nothing to consent to | N/A |
| loan-harassment | no | N/A |
| deployment | yes | 5.1, 5.5 (developer verification is Console-side: NEEDS_CONFIRMATION) |
| code-audit | yes | grep set run over `app/src/main` and `core/src/main`; clean |
| intellectual-property | partly | own art and names; the template icon is Google's (B3) |
| restricted-content | yes | vocabulary scan: fantasy violence only (5.4) |

security-audit: 8 MASVS categories reviewed (section 3). privacy-audit: steps 1 and 4 done, step 2 blocked (no policy exists). performance: 7 categories triaged — Startup (F3), Recomposition (F7), Memory (clean), ANR (clean), Pagination (clean), Baseline profiles (F5), Battery/Network (N/A).

## 8. Owner decisions
1. Backup (F4): keep `allowBackup="false"` (legacy dies with the phone; no Google Drive involvement, purest reading of "no backend") or enable Android Auto Backup for `databases/` (legacy survives a phone change; Google's infrastructure, not the app's; needs real rule files and a restore test)?
2. Large screens (F2): accept landscape rendering of the portrait layouts on tablets/foldables for the first release (test at 600–840 dp so nothing is cut off) or schedule a width-class layout before the store build?
3. Save growth (B4/F1): bound the per-run history/events the save keeps (a content/presentation cap, not a rule change) or re-architect storage to keep everything forever at the cost of multi-MB writes per tap?
4. In-app privacy link: a Settings line "Privacy" opening the policy URL, or rely on the Console field alone (the reference lists an in-app link; a zero-collection game is the mildest case)?
