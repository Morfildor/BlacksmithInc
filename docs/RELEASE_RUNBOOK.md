# Release runbook (T7.4)

Status: ready to run once the owner supplies the application ID. Nothing here has been run against the real ID; the
rename script in section 2 was rehearsed on a throwaway copy with a dummy ID (section 2.5). The ID in the repository is
still the Android Studio template value `com.example.blacksmithproject` (`app/build.gradle.kts:8` namespace, `:14`
applicationId). Do not guess an ID: it cannot be changed after the first Play upload.

Scope: release identity, R8, signing and store text. No gameplay, no UI.

## 1. What the owner decides first

| Input | Used in |
|---|---|
| `<APP_ID>`: lower-case reverse-DNS, dots only, each segment starts with a letter (for example `tld.name.tinyblacksmith`; this line is a format example, not a suggestion) | section 2 |
| Store listing wording for the art | section 5 |
| Where the release keystore lives and who holds the passwords | section 4 |

## 2. Rename to `<APP_ID>`

Run alone, on a clean tree, on its own branch, with no other agent editing `app/`, `tools/` or `docs/`. The rename touches
about 60 files and every Kotlin file in `app/`.

Two ways to do it. This runbook gives the full one (namespace, applicationId, package directories), which is what plan
T7.4 asks for.

- Full rename (below): the Kotlin package, the manifest namespace and the applicationId all become `<APP_ID>`. Scripts
  that say `am start -n $PKG/.MainActivity` keep working.
- Lighter alternative, applicationId only: change `app/build.gradle.kts:14` and nothing else. The Kotlin package and
  `namespace` stay `com.example.blacksmithproject`; Play only sees the applicationId. It has no source churn and no save
  risk, but the template package stays in stack traces and every `.MainActivity` shortcut in `tools/emulator/*.sh` and
  in the debug-manifest comments must then use the full class name
  (`$PKG/com.example.blacksmithproject.MainActivity`), because a relative `.MainActivity` resolves against the
  applicationId. That failure was hit during the R8 device check (section 3.4).

### 2.1 Pre-flight

```bash
git status --short                 # must print nothing
git switch -c release-identity
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug   # green before you start
```

### 2.2 The rename (Git Bash, from the repository root)

Type `bash` first to start a throwaway subshell (the block uses `set -eu`, which would otherwise close your terminal on
the first error), paste the whole block, and stay in that subshell for 2.6, which reuses its variables.

```bash
set -eu
APP_ID=<APP_ID>                              # for example: tld.name.tinyblacksmith
OLD=com.example.blacksmithproject
OLD_RE=${OLD//./\\.}
OLD_DIR=${OLD//.//}
NEW_DIR=${APP_ID//.//}

# 1. Package directories of the four source sets that have Kotlin (main, test, androidTest, debug).
for set in main test androidTest debug; do
  src=app/src/$set/java/$OLD_DIR
  [ -d "$src" ] || continue
  mkdir -p "app/src/$set/java/$(dirname "$NEW_DIR")"
  git mv "$src" "app/src/$set/java/$NEW_DIR"
done
find app/src -type d -empty -delete          # leftover com/example folders

# 2. Room schema export directory (it is named after the database class, so it moves with the package).
git mv "app/schemas/$OLD.data.SaveDatabase" "app/schemas/$APP_ID.data.SaveDatabase"

# 3. Every literal: package/import lines, namespace, applicationId, the androidTest assertion
#    (ExampleInstrumentedTest.kt:22), the debug-manifest and DebugSqlActivity comments, the two emulator
#    scripts (PKG= at line 8), and the pixel-art generator's package strings and output paths.
grep -rlI "$OLD_RE" app/src app/build.gradle.kts tools/emulator tools/pixelart | xargs sed -i "s/$OLD_RE/$APP_ID/g"
grep -rlI "$OLD_DIR" tools/pixelart | xargs sed -i "s#$OLD_DIR#$NEW_DIR#g"

# 4. Nothing may remain outside historical notes.
grep -rnI "$OLD_RE\|$OLD_DIR" --exclude-dir=build --exclude-dir=.git --exclude-dir=.gradle . | grep -v "^./docs/"
```

The last command must print nothing. If it prints a line, fix that file by hand.

Do not rename these (they do not depend on the package): the theme `Theme.BlacksmithProject`
(`app/src/main/res/values/themes.xml:4`), `BlacksmithProjectTheme`, and `app_name` ("Tiny Blacksmith",
`strings.xml:2`). `:core` keeps its own package `com.tinyblacksmith.core`; it is not part of this rename (and must not
be, see section 2.4).

### 2.3 What the script covers, file by file

| Place | Lines | Effect |
|---|---|---|
| `app/build.gradle.kts` | 8 (`namespace`), 14 (`applicationId`) | both become `<APP_ID>` |
| `app/src/{main,test,androidTest,debug}/java/...` | every `package` and `import` | moved and rewritten; this includes the generated `ui/WeaponArt.kt` and `ui/PortraitArt.kt` |
| `app/src/main/AndroidManifest.xml` | `.MainActivity` is relative to the namespace | no text change needed; resolves against the new namespace |
| `app/src/debug/AndroidManifest.xml` | 6 (comment with the `am start -n` line) | rewritten; activities are `.ArtGalleryActivity`, `.DebugSqlActivity`, also relative |
| `app/src/debug/java/.../DebugSqlActivity.kt` | 10-12 (comment), 19 | comment rewritten; line 19 names the database file, which does not change (2.4) |
| `app/src/androidTest/java/.../ExampleInstrumentedTest.kt` | 22 | `assertEquals("<APP_ID>", appContext.packageName)`; fails until rewritten |
| `tools/pixelart/import_assets.py` | 60, 104 (output paths), 414, 546 (generated `package` and `import ...R` lines) | rewritten, so the next importer run emits the new package. The generated files `ui/WeaponArt.kt` and `ui/PortraitArt.kt` are already rewritten by the `app/src` pass |
| `tools/emulator/smoke.sh`, `tools/emulator/runend.sh` | 8 (`PKG=`) | rewritten |
| `app/schemas/<pkg>.data.SaveDatabase/1.json` | directory name only | moved; the file content is unchanged (see 2.4) |
| `CLAUDE.md`, `.github/workflows/ci.yml` | none | neither contains the package or the ID (checked by grep); nothing to change |

Not touched by the script, edit by hand: the notes that say the rename is pending. `docs/DECISIONS.md:28`,
`docs/GDD_CHECKLIST.md:130` (tick it once 2.6 is green), `docs/IMPLEMENTATION_PLAN.md:64` (tick), `docs/PROGRESS.md`
(the lines "Package name is still ..." and "Package rename from ..."). Leave `docs/MAJOR_UPDATE_PLAN.md` as it is; it is a
historical record.

After the script, regenerate the pixel art once as a consistency check; the generated Kotlin files must not change:

```bash
python tools/pixelart/import_assets.py
git diff --stat app/src/main/java/<NEW_DIR>/ui/WeaponArt.kt app/src/main/java/<NEW_DIR>/ui/PortraitArt.kt
```

(substitute the real path for `<NEW_DIR>`, which is `<APP_ID>` with dots turned into slashes.) The importer needs Pillow
and numpy and the source folder `Pixel art assets/`. If you skip this step the sed pass has already produced the same
generated files.

### 2.4 What must NOT change, or existing saves stop loading

Verified from the code at base commit `6fd4df6`. These strings are the on-disk contract; none contains the package.

| Item | Where | Why |
|---|---|---|
| Room database file name `tiny_blacksmith.db` | `app/src/main/java/com/example/blacksmithproject/data/SaveStore.kt:90`; `app/src/debug/java/.../DebugSqlActivity.kt:19` (debug tool opens the same file) | a different name opens an empty database next to the old one |
| Table name `saves` | `SaveStore.kt:19` | `@Entity(tableName = "saves")` |
| Columns `key`, `schemaVersion`, `payload`, `savedAt` | `SaveStore.kt:20-24` | renaming a field renames the column |
| Room database `version = 1`, `exportSchema = true` | `SaveStore.kt:53` | a changed version needs a migration |
| Row keys `run`, `legacy`, `cursor` | `SaveStore.kt:85-87` | the load path reads these three keys |
| Quarantine key suffix `.bak.<millis>` | `SaveStore.kt:73` | quarantined rows must stay out of the load path |
| Schema identity `83afc97d09f925dced066a27b8112797` | `app/schemas/.../1.json:5` | Room compares this hash with the file's `room_master_table`; it derives from table shape, not from the class or package name, so moving the directory does not change it. If a build after the rename shows a different hash, an entity changed: stop |
| DataStore file name `settings` | `app/src/main/java/.../data/SettingsStore.kt:15` | `preferencesDataStore(name = "settings")` |
| Preference keys `reduced_motion`, `seen_tips`, `dismissed_report` | `SettingsStore.kt:28-30` | |
| Save envelope: `SCHEMA_VERSION = 2`, `classDiscriminator = "type"` | `core/src/main/kotlin/com/tinyblacksmith/core/persistence/SaveCodec.kt:19, 24` | |
| Explicit `@SerialName("com.tinyblacksmith.core.model.WeaponLocation...")` strings | `core/src/main/kotlin/com/tinyblacksmith/core/model/Model.kt:25-36` | polymorphic type names stored inside save payloads; these are `:core` names, which is why `:core` is not renamed |

`grep -rn "Serializable" app/src` finds nothing: no `@Serializable` class lives in `:app`, so the app package never
appears inside a save payload. The format therefore survives the rename. What does not survive is the file's location
(next section).

### 2.5 Rehearsal

The script of 2.2 was run on a throwaway copy of the tree (outside the repository, no git, so `git mv` was plain `mv`)
with the dummy ID `zz.rehearsal.tinyblacksmith`, which exists nowhere in the repository. Result: the final grep printed
only the script file itself; the sources, `namespace`/`applicationId`, the generated `WeaponArt.kt` header, the four
`import_assets.py` strings and the schema directory were rewritten; and
`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease` passed (BUILD SUCCESSFUL, release APK
4,240,652 bytes), KSP wrote the Room schema into the moved directory with the same `identityHash`
`83afc97d09f925dced066a27b8112797`. Not rehearsed: the instrumented tests, the emulator scripts and the importer run.
Re-run section 2.6 on the real tree; the rehearsal does not replace it.

### 2.6 Verification after the rename

```bash
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
./gradlew :app:connectedDebugAndroidTest        # emulator required; take the emulator lock first
grep -rn "$OLD_RE" --exclude-dir=build --exclude-dir=.git . | grep -v "^./docs/"     # prints nothing
```

Then on an emulator with the renamed debug build installed:

1. `ADB=<sdk>/platform-tools/adb bash tools/emulator/smoke.sh <dir>`; expect every `CHECK ... ok` and `SMOKE_DONE`
   (it ends on "resume after process death on day 2").
2. `ADB=<sdk>/platform-tools/adb bash tools/emulator/runend.sh <dir>` (about 2 minutes).
3. `ADB=<sdk>/platform-tools/adb shell am start -n <APP_ID>/.ArtGalleryActivity` shows the portraits (debug build only).
4. `app/schemas/<APP_ID>.data.SaveDatabase/1.json` has the identity hash of 2.4 and `git status` shows no other new schema file.

Check that the merged debug and release manifests name `<APP_ID>` as `package` and still resolve `MainActivity`:
`apkanalyzer manifest print app/build/outputs/apk/release/app-release-unsigned.apk | head -12`.

### 2.7 A new applicationId is a new app: saves do not follow

Android identifies an app by its applicationId. An APK with a different ID does not upgrade the old one; it installs
as a second, unrelated app with its own private storage (`/data/data/<id>/`, including `databases/tiny_blacksmith.db`
and the DataStore file). There is no upgrade install and no migration path from `com.example.blacksmithproject` to
`<APP_ID>`.

- The save format and file names stay compatible (2.4), so the code is ready, but the data is not carried over.
- The manifest has `android:allowBackup="false"` (`app/src/main/AndroidManifest.xml:6`), so Google backup or a device
  transfer is not a route either.
- Every tester starts a new run and an empty legacy profile after installing the `<APP_ID>` build. Tell testers
  before the switch. If the old install matters to someone, they keep it installed and play it side by side; nothing
  copies between the two.
- A separate one-way trap, same cause: a build signed with a different key cannot install over an existing install of
  the same ID either (a release-signed APK cannot replace the debug-signed one without an uninstall, which erases the
  data). The release key therefore must be settled before the first tester receives a release build (section 4).
- Developers who need to carry a save over (for example to reproduce a bug) can copy the database out of a debug build
  with `adb shell run-as <old-id> cat databases/tiny_blacksmith.db`; this only works for debuggable builds.

## 3. R8 trial build

State at this commit: minification, obfuscation and resource shrinking are on for the `release` build type.

### 3.1 What was changed

`app/build.gradle.kts`, `buildTypes.release`:

- `optimization { enable = true }` (AGP 9 built-in optimisation; it applies `proguard-android-optimize.txt` itself).
- `isShrinkResources = true`.
- A trial switch, off by default: `./gradlew :app:assembleRelease -PtrialSuffix=1` adds
  `applicationIdSuffix = ".r8trial"` and signs with the debug key, so the build installs next to a debug build on a
  device. **Replace before release:** delete the whole `if (providers.gradleProperty("trialSuffix")...)` block when
  the real signing config is added (section 4), so that a debug-signed release build cannot be produced by accident.
  Without the property the release APK stays unsigned (`app-release-unsigned.apk`) and the ID is unchanged.

### 3.2 Keep rules

The task text proposed `app/proguard-rules.pro`. AGP 9.3.3 rejects the usual wiring: `optimization.keepRules.files` is
deprecated ("Use keepRules source folder instead") and `files += ...` does not compile. The rules therefore live in the
folder the template already created, `app/src/main/keepRules/rules.keep`, which AGP reads on its own.

That file contains exactly two directives:

```
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

Reason: readable, retraceable crash traces from the Play Console. Nothing else is kept, because nothing else was
needed. The libraries ship consumer rules that AGP merged (see `app/build/outputs/mapping/release/configuration.txt`,
sections "The proguard configuration file for the following section is ..."):

| Concern | Satisfied by | `configuration.txt` line |
|---|---|---|
| Room: `SaveDatabase_Impl` is found by reflection from `Room.databaseBuilder` | `room-runtime-android:2.8.5` and `room-ktx:2.8.5` consumer rules | 93, 99 |
| kotlinx.serialization for the `:core` save model | `kotlinx-serialization-core-jvm:1.9.0` consumer rules (generic `@Serializable` companion and `$serializer` rules, both sections) | 756, 808 |
| DataStore | `datastore-core-android`, `datastore-preferences-*` consumer rules | 668-697 |
| Compose, lifecycle, coroutines, startup | the libraries' own consumer rules | 103-663, 701-755, 859 |
| The activity and the manifest components | derived from the merged manifest by AGP | n/a |

Why the save format is obfuscation-safe: kotlinx.serialization writes property names from compile-time descriptor
strings, not from class or field names, and the only polymorphic hierarchy that the codec stores (`WeaponLocation`)
carries explicit `@SerialName` strings (`Model.kt:25-36`). No code in `app/src/main` or `core/src/main` uses
`getIdentifier`, `Class.forName` or reflection on game classes (grep), and `resources.txt` shows every drawable
reachable through an `R` field, so resource shrinking removed no sprite (the screenshots in 3.4 show sprites on every
screen).

Do not add speculative keep rules. Add one only with a failing release-build symptom and write its reason here.

Per release: keep `app/build/outputs/mapping/release/mapping.txt` (and upload it to the Play Console, where it
deobfuscates crash traces). Losing it makes every release crash trace unreadable. Owner action at upload time.

### 3.3 Sizes, merged manifest, debug-only code

APK sizes at base commit `6fd4df6`, `:app:assembleRelease`:

| Build | Bytes |
|---|---|
| Before: R8 off, unsigned (`app-release-unsigned.apk`) | 12,440,598 |
| After: R8 and resource shrinking, debug-signed trial (`-PtrialSuffix=1`, `app-release.apk`) | 4,248,856 (download size 3,913,639 by `apkanalyzer apk download-size`) |
| After: R8 and resource shrinking, unsigned (no property, the default) | 4,240,656 |
| For reference: debug APK | 17,683,816 |

Merged release manifest (`apkanalyzer manifest print`, merger log at
`app/build/outputs/logs/manifest-merger-release-report.txt`), everything that is declared:

- Permissions: exactly one `<permission>` and one matching `<uses-permission>`,
  `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (protection level signature, added by androidx core for
  unexported dynamic receivers). No other `<uses-permission>`. No INTERNET.
- One activity: `MainActivity` (exported, launcher intent, portrait).
- Services: `androidx.room.MultiInstanceInvalidationService` (not exported).
- Provider: `androidx.startup.InitializationProvider` (not exported), initialisers for emoji2, lifecycle-process and
  profileinstaller.
- Receiver: `androidx.profileinstaller.ProfileInstallReceiver`, exported, guarded by the attribute
  `android:permission="android.permission.DUMP"` (an attribute on the receiver, not a `<uses-permission>`).
- Two `<uses-library ... required="false">` entries for `androidx.window.extensions` and `androidx.window.sidecar`.

`ManifestTest` checks only the source manifest; the merged check above is the one that covers libraries.

The two debug activities (`ArtGalleryActivity`, `DebugSqlActivity`) are absent: they live in `app/src/debug`, `apkanalyzer
dex packages --defined-only` on the release APK lists no `ArtGallery`, `DebugSql`, `tooling` or `ui.test` class, and the
manifest above has no entry for them. Confirm again with:

```bash
apkanalyzer dex packages --defined-only app/build/outputs/apk/release/app-release-unsigned.apk | grep -i "ArtGallery\|DebugSql\|tooling"
```

### 3.4 Device check of the minified build

Run on the shared emulator under the emulator lock (owner T7.4), install of `com.example.blacksmithproject.r8trial`
only. A copy of `tools/emulator/smoke.sh` with two edits was used: `PKG=com.example.blacksmithproject.r8trial`, and the
two `am start -n` lines changed from `$PKG/.MainActivity` to `$PKG/com.example.blacksmithproject.MainActivity`
(`.MainActivity` resolves against the suffixed ID and fails with "Activity class ... does not exist"). The repository
scripts were not edited.

Result: every check printed `ok` (shelf listed, town panel, records segments, legacy segment, settings sheet, back to
Shop) and the final check "resume after process death on day 2" passed: new game, forge one blade, list it, End Day,
`force-stop`, relaunch, the run loads on Day 2 with the blade on the shelf. That round trip (encode, Room write, Room
read, decode, on the minified build) is the evidence for 3.2. Screenshots: scratchpad `exec/m7-shots/r8/`.

Limits, stated plainly:

- Only encode then decode within the minified build was tested. A save written by the debug build was not loaded by
  the R8 build: different application ID, separate storage, so it cannot be done on one device without a manual copy.
- One day of play exercises the common serializers only. Before release run `tools/emulator/runend.sh` against the
  trial package (about 2 minutes, passive run to defeat, claim and next era) to cover siege, death and legacy
  serializers. Not run in T7.4.
- On the emulator in use, no other Tiny Blacksmith build was installed when the trial APK went on, so "both builds
  coexist" was enabled by the different ID but not demonstrated on that device.
- Debug builds of the shared emulator were not touched; only `com.example.blacksmithproject.r8trial` was uninstalled.

## 4. Signing and Play upload (owner actions)

Nothing in this section has been done and no key exists. None of it may be committed.

1. Before creating anything, add to `.gitignore`: `*.jks`, `*.keystore`, `keystore.properties`. The current
   `.gitignore` ignores none of them.
2. Create the upload keystore on the owner's machine (the command prompts for the passwords; do not put them in a
   script or in chat):
   `keytool -genkeypair -v -keystore <path>/tinyblacksmith-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 9125`
   Store the file and both passwords in the owner's password manager and one offline backup. With Play App Signing
   (recommended) Google holds the app signing key and this is only the upload key, which Play can reset; without it,
   losing the key ends updates for good.
3. Add a `signingConfigs.release` block to `app/build.gradle.kts` that reads the store path and passwords from
   `~/.gradle/gradle.properties` or environment variables (never from a tracked file), attach it to
   `buildTypes.release`, and delete the `trialSuffix` block of section 3.1.
4. Raise `versionCode` (currently 6) and `versionName` (currently 0.6.0, `app/build.gradle.kts`), following the
   CHANGELOG rule in `CLAUDE.md`.
5. Build the bundle: `./gradlew :app:bundleRelease` (output `app/build/outputs/bundle/release/app-release.aab`). Play
   takes an AAB, not an APK, for new apps. Also keep `app/build/outputs/mapping/release/mapping.txt` for that
   versionCode.
6. Play Console, in this order: create the app with `<APP_ID>` (permanent); enrol in Play App Signing; fill the store
   listing (section 5), content rating questionnaire, target audience, and the Data safety form (the game has no
   network code, no analytics, no ads and no account; the form must say so, and `ManifestTest` guards the permission
   half of that claim); price of 1.99 (EUR) in the countries chosen; check whether a privacy-policy URL is required
   for the declaration; upload the AAB to an internal test track first and the `mapping.txt` with it.
7. Install the internal-track build on a physical device and run the smoke steps of 2.6. Then promote.

Owner-side checks that cannot be automated: Play's current policy on AI-generated content disclosure and the
generating account's terms for commercial use (section 5).

## 5. Art provenance wording for the store listing (DRAFT for the owner)

Status: draft, not approved. The owner decides the wording, confirms the generating account's usage terms, and checks
Play's current disclosure requirements for AI-generated content before submitting (plan X16, 10.4).

Facts it rests on (`docs/ART_BRIEF.md` section 9, `docs/DECISIONS.md` "Art provenance wording"): the concept sheets and
the weapon master sheet are AI-generated (each source file carries an embedded Content Credentials manifest naming
ChatGPT / OpenAI as generator); the artist's 1x production pack is script-drawn from authored shapes; the project
slices and resamples sheets programmatically and recolours (the weapon element rows) with scripts; IDs without imported
art receive programmatic placeholders. No evidence of manual pixel editing exists, and the project never edits PNGs by
hand.

Draft text (English, for the listing's description or "about the art" line):

> Tiny Blacksmith's pixel art was made with AI-assisted tools. The weapons, heroes, monsters and backgrounds start from
> AI-generated concept sheets that were sliced, cleaned and recoloured with scripts; some sprites are drawn
> programmatically from simple shapes, and a few placeholders are generated by code. The game, its rules and its text
> are written by the developer. The game has no ads, no purchases beyond the one-time price, no accounts, and works fully
> offline.

Rules for any wording: never say "hand-made", "hand-drawn" or "painted by an artist" (nothing supports it); say "AI-assisted"
for the sheets, "programmatic" for slicing, recolouring and placeholders; do not name a generator unless the owner has
confirmed the terms. The importer and `generate_assets.py` still print the label "hand-made" for imported art until plan
task T2.4 renames it; read it as "imported art" (ART_BRIEF section 9).

## 6. Launcher icon candidate: NOT DONE

State: the launcher icon is the Android Studio template (`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, adaptive,
foreground `drawable/ic_launcher_foreground.xml`, same drawable as monochrome). Plan 5.2 (asset-use table, row "launcher icon") names a candidate: sheet 5's
`blessing_forgefire` (the anvil in flames).

Why not done in T7.4: `tools/pixelart/import_assets.py` slices that cell only at 48 x 48 (`BLESSINGS` list at line 113,
output `blessing_forgefire.png` in `drawable-nodpi`), too small for a launcher icon, and a large export means new
importer code in a file that another task is editing. The placeholder generator has a separate drawing
(`tools/pixelart/generate_assets.py:473`), also not suited.

Steps for whoever does it (after the importer is free; not part of the rename):

1. In the importer, add one export of the sheet-5 `forgefire` cell at 432 x 432 (adaptive foreground, 108 dp at xxxhdpi)
   with the artwork inside the central 66 dp (264 px) safe zone and transparent margin, nearest-neighbour; also a
   full-bleed square background colour or `ic_launcher_background.xml` as now. Never edit the PNG by hand.
2. Put the foreground into `app/src/main/res/drawable-nodpi/` (or `mipmap-xxxhdpi`) and point
   `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` at it. Keep the `monochrome` entry as a flat
   single-colour version (Android 13 themed icons); the current one reuses the foreground, which will look wrong for
   photographic art.
3. Replace the `ic_launcher.webp` fallbacks for API 24-25 (mdpi through xxxhdpi) with exports of the same art at
   48/72/96/144/192 px.
4. Provide the 512 x 512 Play listing icon from the same source; Play requires it separately.
5. Owner decision: confirm the candidate (plan 5.2, "MISSING" row).
