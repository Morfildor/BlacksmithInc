# Visual quality pass (external review 2026-10-17) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the reconciled visual/UX findings (VA-01..58 as qualified by C1–C9, plus R-01..R-03) so the game reads, fits and presents its moments at 360–412 dp and font 1.0–2.0, with rendered evidence.

**Architecture:** UI-only changes in `:app` (`ui/`), one small additive helper in `:core` (`shopday/Lines.kt`). Layout decisions move from `fontScale`/`screenWidthDp` guesses to measured available width (`BoxWithConstraints` + pure functions in a new `ui/Fit.kt`). Pixel art goes through one whole-scale helper (`nearestWholeScale`) and one face composable (`HeroFace`). Work is split by file ownership so six agents can edit in parallel on one tree; only the orchestrator runs Gradle (RAM is too tight for parallel builds).

**Tech Stack:** Kotlin 2.2.21, Jetpack Compose (BOM 2026.02.01), JUnit (app JVM tests are pure models, no Robolectric), androidTest with `createComposeRule` + `DeviceConfigurationOverride.ForcedSize/FontScale` (example `app/src/androidTest/java/com/example/blacksmithproject/LayoutMatrixTest.kt`).

**Spec:** `docs/visual_audit_2026-10-17/External review/TinyBlacksmith_implementation_prompt_2026-10-17.md` and `.../TinyBlacksmith_combined_visual_review_2026-10-17.md` (disposition table §4 governs), original findings in `docs/VISUAL_UX_AUDIT_2026-10-17.md`. Paths below are under `app/src/main/java/com/example/blacksmithproject/ui/` unless absolute.

## Global Constraints

- No gameplay, balance, RNG stream, save schema, stored combat record, appearance ID or content identity changes. UI observes; operations go through `GameViewModel`/`GameSession`.
- Hero faces: never recoloured, regenerated or recropped; approved painterly 64×64 art with backdrops stays. Do not extend the replay's downed-face desaturation elsewhere.
- Weapons: completed weapons always draw the full `WeaponLook(base, effect, charm)`; `.base` only where a weapon *type* is meant (ForgePanel.kt:479/517, InspectUi.kt:186, Notebook.kt:190).
- Pixel art: whole device-pixel scale (nearest-neighbour when enlarging), about 1 art px per dp for interface icons; painterly art keeps its approved treatment.
- Navigation labels never hidden; tabs keep `Role.Tab` + `selected`; inspect access kept; no icon-only controls; non-colour cues for rarity/state/selection; touch targets ≥ 48 dp.
- Per-destination scroll restoration in `WorkshopScreen.kt` (`SaveableStateProvider`) is deliberate: keep it.
- End Day keeps its label and semantics (no "Face the siege"). Selection is not commitment (blessing, contract, relic).
- Player text follows `docs/WRITING_GUIDE.md`; `ProseGuardTest` forbids dashes and semicolons in strings. Never drop a cost, risk, timing or disabled reason to make a layout fit.
- Keep existing testTags; add new ones rather than renaming. If a tag must move, say so in the report.
- Agents do **not** run Gradle, adb or the emulator. They edit only the files their task owns, then report: files changed, decisions taken, tests added/updated, anything they could not do. The orchestrator compiles, runs tests and sends errors back.
- Do not commit or push.

## Review Focus

- Font scale 2.0 at 360 dp (and 1.3 at 412): every changed row/tab/bar must still fit without per-letter wrapping; tests in Task 1 (`FitTest`) and the androidTest `LayoutMatrixTest.noOverflow` rerun cover it.
- Density 420/440/480 dpi with the same logical width: whole-scale results differ (k=2 vs 3); `SpriteScaleTest` pins the table, device shots at all three densities check it.
- New living-era sieges (bestiary units): every enemy actor must resolve to a picture or a declared name plate, never an initial; `EnemyFaceTest` (Task 7) pins it.
- Long names (bestiary "Sir Carrow, the Unburied", long hero names, long blessing names) in two-line plates, pinned buttons and stacked rows; Task 4 and Task 7 tests use the longest real names.
- Busy state (`enabled = false` while saving) must not restyle rows as unavailable (Task 1 `ChoiceRow(unavailable)` is a separate flag).

---

### Task 1: Shared foundation (sequential, before all others)

**Files:**
- Create: `ui/Fit.kt`, `ui/HeroFace.kt`
- Modify: `ui/Sprites.kt` (helpers ~258-300, KDoc 124-125), `ui/shopday/CounterScene.kt:73` (only `wholeScale` delegation), `ui/Frames.kt` (additive params/composable), `ui/Parts.kt:119`, `ui/replay/ReplaySupport.kt:31-46` (extract lookup only)
- Test: create `app/src/test/java/com/example/blacksmithproject/FitTest.kt`, `SpriteScaleTest.kt`; modify `V2ShellTest.kt` (`factsAreOneLineWithBoundSeparatorsAndSignedTones`)

**Interfaces (produced, used by Tasks 2–7):**
- `fun tabsPerRow(availablePx: Float, widestWordPx: Float, count: Int, gapPx: Float, padPx: Float): Int` — largest n in {count, 2, 1} (and any divisor-friendly value ≤ count) where `(available − gap·(n−1))/n − 2·pad ≥ widestWord`; never < 1.
- `fun roomBeside(rowWidthPx: Float, fixedPx: Float, tradePx: Float, minTextPx: Float): Boolean` — true when `rowWidth − fixed − trade ≥ minText`. Callers pass `minText = 150.dp × fontScale` converted to px.
- `fun nearestWholeScale(artPx: Int, hintPx: Float, maxPx: Float = Float.POSITIVE_INFINITY): Int` — `round(hint/art)`, stepped down while `art·k > maxPx`, floor 1. `wholePixelDp` and `CounterScene.wholeScale` delegate to it (no behaviour change).
- `@Composable fun nativeScaleDp(artPx: Int): Dp` — `artPx × max(1, round(density)) / density` dp (rule of `frameScale()` at Sprites.kt:339).
- `WeaponSprite` and `PixelImage(look: WeaponLook, size: Dp)` draw `PixelSprite(look, nearestWholeScale(56, sizePx, maxPx = sizePx))` centred in a `size` box (footprint unchanged, no fractional scale).
- `@Composable fun HeroFace(res: Int, hint: Dp, modifier: Modifier = Modifier, marker: Int? = null)` — ForgeSlot ground, 1 dp BronzeDeep border, `PixelSprite(res, k)` with k from the bitmap's own width via `nearestWholeScale`; optional marker drawn at the frame corner (shape cue, not over the face centre). Footprint = effective dp + 2 dp.
- `Frames.kt`: `@Composable fun DestructiveActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)` (OutlinedButton, 1 dp `FlawRed` border, `FlawRed` content, `heightIn(min = 48.dp)`); `ChoiceRow(..., unavailable: Boolean = false)` (transparent ground, 1 dp `BronzeDeep.copy(alpha = 0.5f)` border; independent of `enabled`); `EffectBullets(..., muted: Boolean = false)` (CreamMuted text, dimmed signs).
- `Parts.kt` `factText`: label and value joined with ` ` (no break inside "Quality 47").
- `replay/ReplaySupport.kt` (pure refactor, legacy behaviour only): extract the existing `factionOf` lookup into `fun enemyFaction(unitId: String, content: ContentCatalog): FactionId?` and `fun enemyFace(unitId: String, content: ContentCatalog): Int?` (built once per catalog, e.g. a lazy map keyed by catalog identity); `replayFaces` calls `enemyFace`. Task 7 extends both with the bestiary; Task 5 consumes `enemyFace`.

- [ ] Step 1: Write `FitTest` — `tabsPerRow` at 288 dp available / widest word 79 dp / 4 tabs / 1.0 → 2; same words ×2.0 → 1; four short words that fit → 4. `roomBeside`: 328 dp row, 122 fixed, 64 trade, 150 min → false; 380 dp row → true.
- [ ] Step 2: Write `SpriteScaleTest` with one assertion per row: 64@44dp → 2/2/2 at density 2.625/2.75/3.0; 64@54 → 2/2/3; 56@48 → 2/2/3; 56@72 → 3/4/4; 32@30 → 2/3/3; 32@24 → 2/2/2; `maxPx` step-down (56@44 with max 44dp·3 → 2); floor (8@1 → 1).
- [ ] Step 3: Implement the interfaces above. Update `V2ShellTest` to expect NBSP and assert no plain space between a fact label and its value.
- [ ] Step 4: Orchestrator runs `./gradlew :app:testDebugUnitTest --tests '*FitTest' --tests '*SpriteScaleTest' --tests '*V2ShellTest'` → PASS, then `:app:compileDebugKotlin`.

### Task 2: Workshop shell (header, Records, Notebook, notices, title)

**Owns:** `SiegeHeader.kt`, `WorkshopScreen.kt`, `ShopUi.kt`, `ShopPanel.kt`, `IntroStrip.kt`, `App.kt`, `Notebook.kt`. Tests: `SiegeHeaderTest`, `ThreatAndRequestsModelTest`, `NavigationBarTest`, `TitleScreenTest`, new androidTest cases in `LayoutMatrixTest` only for its own surfaces.

**Findings and decisions:**
- **VA-23/VA-01 (C2):** use the stacked layout at every size: Row(day … stats), then the full-width clickable `SiegeStrip`, then `CycleTrack`. `brief` is chosen by measuring the full siege line (`rememberTextMeasurer`, labelLarge, no wrap) against `maxWidth − 40.dp`, not by `screenHeightDp < 700`. `Stat` values get `widthIn(min = measured width of "8" × max(2, len))`, end-aligned, so 10→8 energy does not shift layout. Keep tags `header_day`, `siege_strip`, `siege_countdown`, `siege_defenders`, `siege_marks`, `workshop_header`, `nav_settings`. Do not quote the old 50–80 dp figure anywhere.
- **EndDayButton (WorkshopScreen.kt:434):** Row vs Column chosen by measured width (headline + "What happens" + 40 dp ≤ maxWidth), not `fontScale ≥ 1.3`.
- **VA-04:** Records page tabs: measure labels; if they fit keep one row, else a 2×2 grid (`chunked(2)`, weights) with the same underline, `Role.Tab`, `selected`, `page_*` tags. Use `tabsPerRow`.
- **VA-03:** Notebook tabs (Notebook.kt:99): `perRow = tabsPerRow(...)` measured on the widest single word of all labels inside `BoxWithConstraints`. Keep `book_tab_*`.
- **VA-43 (C1):** DestinationBar normal branch draws icons at `nativeScaleDp(32)` (32 px at round(density): 36.6/34.9/32 dp at 420/440/480). Large-text branch (24 dp) unchanged. Labels must not clip (`NavigationBarTest`).
- **VA-22:** move the notice out of the overlay into the flow between IntroStrip and the destination Box (option A): `AnimatedVisibility`/`SizeTransform`, no animation when reduced motion (`LocalReducedMotion` or the existing motion setting — find what the project uses). Keep slide-in and swipe-up-to-dismiss, 4 s timeout, `NoticeBanner` unchanged.
- **VA-25a:** a 1 dp `BronzeDeep` rule under the IntroStrip. Done stays a deliberate tap.
- **VA-12 / VA-41 header part:** add `army: String?` to `ThreatUi` (`content.guild.army(state.siege?.armyId)?.name` when the run is a living era and the army is committed; otherwise null). Defenders stay in the header strip on every destination (the measured `brief` handles overflow; do not remove the fact). On siege day (`threat.today`) the Shop shows `SiegeMatchup` as a lead block at the top of the scroll: ember left rule, "Siege tonight", army (or faction) name, weak/resists, defenders (repeating the header is fine). End Day label unchanged.
- **VA-17:** `App.kt:174` and `SiegeHeader.kt:117`: `PixelIcon(R.drawable.icon_settings, 28.dp)` instead of the vector gear; on the title, on a `ForgeNight.copy(alpha = 0.6f)` circle. Keep 48 dp target, "Settings" description, tags.
- **VA-58 (title):** End era confirm uses `DestructiveActionButton`; Keep playing stays inline. Tag `menu_abandon_confirm` kept. Extend `TitleScreenTest`: cancel does nothing, confirm ends.
- **VA-14 (Shop strip, `ShopPanel.kt:124`):** not in this task (deferred to after wave 2; Task 4 owns `Backdrop`).

- [ ] Steps: implement; extend `SiegeHeaderTest`/`ThreatAndRequestsModelTest` (`army` set only when living + committed); extend `TitleScreenTest`; report.

### Task 3: Forge and Gazette

**Owns:** `ForgePanel.kt`, `Dialogs.kt`, `ResultsInspect.kt`, `InfoPanels.kt`. Tests: `ForgeResultCardTest`, `ResultsInspectTest`, `GazetteDigestTest` (read-only), new androidTest for `DigestBody` if cheap.

- **VA-02:** Forge top bar becomes a `FlowRow(SpaceBetween, itemVerticalAlignment = Center)` with two items: `ModeToggle`, and `Row(commissions, More)`. Count text `maxLines = 1, softWrap = false`. Wording unchanged. Tags `forge_commissions`, `forge_more` kept.
- **VA-06 (C3):** `SlotRow`: `BoxWithConstraints`; if all slots fit at `max(96.dp, widest label + 16.dp)` use equal weights, else a wrapped two-column grid (Spacer for an odd last slot) — every slot visible, no horizontal scroll. Keep a 28–32 dp sprite at large text when the tile is ≥ 120 dp. Tags `forge_slots_*`, `forge_slot_*` kept.
- **VA-16 (C3):** Tray: `k` = tiles of `max(96.dp, widest name word + 16.dp)` that fit. If `options.size ≤ k` equal weights, no scroll; else `tileWidth = (maxWidth − gap·k)/(k + 0.4)` (deliberate 40 % peek) plus a private end-fade while `canScrollForward`. Always pass option art (remove the `null` above 1.3×, ForgePanel.kt:552); at large text 32 dp art above the name; the "i" must not overlap the name. Keep `Tray` signature (positional call in `ForgePurposeDeviceTest:81`).
- **VA-15:** the links row (ForgePanel.kt:359) becomes a `FlowRow` keeping the 116 dp start inset so "Practice fight ›" wraps under "Effects". Tags kept.
- **VA-11 (anvil, ForgePanel.kt:405):** `k = min(nearestWholeScale(109, hint), floor(heightPx/72))`, drawn with `PixelSprite`.
- **VA-32:** leave (already `containerColor = ForgePanel`).
- **VA-20:** forge reveal: weapon identity first (`ResultIdentity` → guide → the rest); sprite at `nearestWholeScale(56, 80.dp)`, slot sized from the sprite + 4 dp; rarity badge inline before the rarity word, not over the art; `detail.kind` bound with NBSP; drop a rule line whose label equals a property label (the weapon card still lists it) — update `ResultsInspectTest:97`. Primary/secondary actions unchanged. New `ForgeResultCardTest` case: blade identity node is above the guide. `thePriceAndBothButtonsAreOnScreenAtEverySize` must still pass.
- **VA-21:** no code change (revalidated on device by the orchestrator).
- **VA-35:** `StoryBlock(story, lead = index == 0)` — lead heading `titleLarge` (Alegreya), others `titleSmall`; remove every `FontFamily.Serif` in DayReportDialog/DigestBody/EditionBody and `InfoPanels.kt:401` (falls back to Fira body). In `DigestBody`, adjacent stories with the same kind and heading share one heading with each body on its own line (no fact dropped). Optional `bottomFade` on the report scroll if the scroll helper exists in Frames.kt (read-only use).
- **VA-08 (InfoPanels.kt:208, 307):** faces through `HeroFace(res, 44.dp)` / `48.dp`.

- [ ] Steps: implement; update/add tests listed; report.

### Task 4: Shop day

**Owns:** `ui/shopday/*` (DayCards, VisitCard, CounterScene except `wholeScale` from Task 1, ShopDayScreen, ShopDayControls, ShopDayUi, ShopDayHost), `core/src/main/kotlin/com/tinyblacksmith/core/shopday/Lines.kt` (additive). Tests: `ShopDayUiTest`, `ShopDayScriptTest` (must stay green), androidTest `ShopDayScreenTest`.

- **VA-07 (C4):** `BlessingChoices` becomes stateless (`marked`, `onMark`); its inner button is removed. `ShopDayScreen` holds `var marked by rememberSaveable(at) { mutableStateOf<String?>(null) }`. `ShopDayControls` gains optional secondary action + `nextEnabled`; on `Beat.Blessing`: secondary "Decide later" (tag `shopday_later`, calls `onNext` → host's `decideLater`), primary `taking?.let { "Take ${it.name}" } ?: "Choose a blessing"` (tag `blessing_take`, enabled only with a choice and not busy, calls `onChooseBlessing`). Above 1.3× stack the two (primary first, full width). The bar's Back is not shown on this beat (it currently maps to decideLater). New androidTest `blessingConfirmIsPinnedAndWaitsForAChoice` (initially disabled; tapping a blessing only marks; confirm sends exactly one choice; Decide later advances once), also at 2.0×.
- **VA-14:** `Backdrop` gains `focusX: Float = 0.5f, focusY: Float = 0.5f`. Below ~88 dp, and always above 1.3×, skip the painted room; keep counter front and plate; the customer moves into the plate as a leading `PortraitTile(face, wholeScale(64, 48.dp))` with frame, regular ring, click and `shopday_face`. New tag `shopday_backdrop`. No fixed 2:1 banner.
- **VA-36:** night-workshop banners use `focusY ≈ 0.42`, same height. Stabilise the top bar per day: an invisible (alpha 0, semantics cleared) longest-progress text behind the real one so wrap is the same on every card. No icon-only speed toggle.
- **VA-29:** `ShopDayScreen.kt:157`: when `maxWidth < 400.dp` pass `FramedPanel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = Space.md))`.
- **VA-37:** in `VisitCard` show only "Total received" when the receipt is exactly [Listed price, Total received] with equal values (data unchanged). Core `Lines.why(...)`: the reason clauses only; `decision()` = why + money clauses, output unchanged. Sale beats with a non-empty receipt use `why`. `Lines.weighedParts(item): Pair<List<String>, List<String>>`, `weighed()` built from it; `LookedUi` gains `pros`/`cons` rendered with `EffectBullets(BUFF/FLAW)`. `BladeChip(verticalAlignment = Alignment.Top)` for Looked at. Tests: update `ShopDayUiTest:164`; add pros+cons = recorded factors; plain sale renders one receipt row.
- **VA-53:** Fallen card: overline "The era has ended" when the cause already names the day; cause `headlineSmall` Cream; banner `if (short) 112.dp else 168.dp`; card `heightIn(min = room)` + SpaceBetween. Tag `shopday_fallen_cause` kept.
- **VA-11 (StageBanner, CounterScene.kt:283-286):** `k = min(floor(h/artH), floor(w/artW), 2·round(density))`, centred.
- **VA-54 (C6):** DEATH branch in `AftermathCard`: clickable header with `HeroFace(face, 64.dp)` (optional `marker_dead` corner marker), muted overline "A death", the hero name `titleLarge` Cream (a heading); record sentence unchanged; blade chip on its own line; `AftermathUi.fate` from core `AftermathCard.fate`, labels checked against the Writing Guide (skip the note if unsure). No desaturation.

- [ ] Steps: implement; core tests `ShopDayScriptTest` unchanged and green; update/add app tests; report.

### Task 5: Guild and hero presentation

**Owns:** `GuildPanel.kt`, `GuildParts.kt`, `GuildUi.kt`, `ContractSheet.kt`, `detail/HeroDetailSheet.kt`, `Inspect.kt`, `GuildInspect.kt`. Tests: `GuildUiTest`, `GuildInspectTest`, `DetailSheetTest` (androidTest), `NavigationFlowTest` (tags only).

- **VA-08/VA-09:** `Face(portrait, size)` keeps its signature and calls `HeroFace`. Rows hint 44 dp; hero sheet head 88 dp. Inspect head (Inspect.kt:288-294): the slot sizes from its content; `TipUi.face: Boolean = false` (set in GuildInspect.kt:103) → `HeroFace(44.dp)`; other icons `nearestWholeScale(w, 40dp, maxPx = 46dp)`.
- **VA-13:** `HeroDetail` gains `equipmentLook: WeaponLook?`, `equipmentId`, `health: Int?`. Head: HeroFace 88 dp, health StatBar (word = condition), weapon art + name row that opens the weapon. New tags `sheet_health`, `sheet_equipment_art` above `sheet_story`. Keep order of other sections (comment at line 317). Dismiss uses `DestructiveActionButton` (tag `sheet_dismiss_yes` kept).
- **VA-57:** selected party rows: order numeral → HeroFace 64 dp → name + MemberLines → two 48×48 ↑/↓ buttons in a column (above 1.5× under the name). Keep `contract_up_`, `contract_down_`, `contract_selected_` tags, descriptions and enabled rules.
- **VA-05 (Places, GuildParts.kt:393):** stack decided by `roomBeside` with measured trade width; skip `HeldWeapon` when `weaponArt == null` (chip says "Unarmed"). Weapon at 32 dp sites (GuildParts.kt:214/260) raised to 40 dp where the row has room.
- **VA-25b:** "Nothing urgent today." only on the default tab; actionable today rows stay on every tab; keep the `today` item (anchor scroll).
- **VA-47:** guild tabs `heightIn(min = 48.dp)`.
- **VA-38:** danger leaves `offerFacts` (GuildUi.kt:389) and becomes a chip on the title row (`dangerWord`, FLAW colour unless SAFE; word is the non-colour cue; "Safe" still shown); above 1.3× chips go under the title. Line: Reward · Away N days, back day X · Fee · Send by. Keep `guild_offer_terms_*`. Update `GuildUiTest.aBoardRowIsOneLineOfFactsWithTheDangerAsAWarning` via a pure `offerRisk(o)`.
- **VA-41:** `EnemyUi.face: Int?` filled with `enemyFace(unitId, content)` (Task 7 defines it in `replay/ReplaySupport.kt`); `EnemyLines` draws it whole-scale before the label, or nothing when null.
- **VA-58 (guild):** `GuildPanel.kt:152` confirm becomes `PrimaryActionButton` (retirement is not a loss). Tag `guild_confirm_yes` kept.

- [ ] Steps: implement; tests; report.

### Task 6: Sheets, setup and run end

**Owns:** `WeaponInspect.kt`, `StorageSheet.kt`, `SuppliesSheet.kt`, `EncounterSheet.kt`, `SettingsSheet.kt`, `OpeningChooser.kt`, `RunEndScreen.kt`, `tools/emulator/runend.sh`, `tools/emulator/intro.sh`, `tools/emulator/runvariety.sh`. Tests: `RunVarietyUiTest`, `ForgeBrowsingTest` (wording must stay), new JVM tests.

- **VA-05 (WeaponInspect.kt:110, SuppliesSheet.kt:131):** `stack = !roomBeside(maxWidth, fixed, measuredTrade, 150.dp×fontScale)` inside `BoxWithConstraints` (fixed ≈ 122 dp, +48 with a checkbox; Supplies ≈ 124 dp). Keep row TalkBack text and 64 dp min height.
- **VA-39:** pure `internal fun stockFact(stock: Int?, onTheRoad: Boolean): Fact?` (null when stock null or not on the road yet; 0 → "Sold out today" WARN; else "$stock left today"). Unit test. `runvariety.sh:314` wants "Sold out today".
- **VA-40:** visitor options: `ChoiceRow(unavailable = o.blocked != null)`, "Not possible: …" line directly under the label, `EffectBullets(muted = true)`. Busy state unchanged.
- **VA-30:** Settings: replace the inner `FramedPanel` with a Column headed `Text("Settings", titleLarge, Gold, Modifier.semantics { heading() })`. Switch colours unchanged.
- **VA-31:** no change.
- **VA-58 (storage):** Melt down confirm uses `DestructiveActionButton`.
- **VA-33/34/47 (OpeningChooser.kt):** relic row `heightIn(min = 48.dp)`; "The first days" expander uses `Chevron` + `onClickLabel`; while chosen the relic row is not clickable and has no chevron; `brings` shown on every card; pitch `bodySmall` Cream; portraits through `HeroFace(44.dp)` without the fixed `size` box. VA-33 backdrop: deferred.
- **VA-52:** `internal enum class RunEndKind { FALLEN, RETIRED }`, `internal fun runEndKind(run: GameState): RunEndKind`. Decorative top band (≈144 dp, `clearAndSetSemantics {}`): FALLEN → `Backdrop(R.drawable.bg_run_end_fallen_forge, …, dim = 0.25f)`; RETIRED → `bg_title_workshop_night` crop, both with a bottom gradient. Title Gold when retired, Cream when fallen. Pinned Begin era button gets `detail = chosen?.let { "Under ${it.name}" } ?: "A classic era"` (read `vm.charter` as RunSetup.kt does). Claim once and perks unchanged. JVM test for `runEndKind`.
- **Scripts:** `runend.sh`: tap by id (`tap_id charter_row_none && tap_id setup_begin`), charter check via `charter_row_none` checked state or "Classic era"; `intro.sh:46` same staleness.

- [ ] Steps: implement; tests; report.

### Task 7: Siege replay and report

**Owns:** `ui/replay/ReplaySupport.kt`, `ui/replay/SiegeReplay.kt`, `ui/replay/SiegeReport.kt`. Tests: new `EnemyFaceTest` (JVM), `SiegeContributionUiTest`, replay androidTests (tags only).

**Interfaces:** consumes Task 1's `enemyFaction`/`enemyFace` (same signatures) and extends them: bestiary `profileById[u]?.factionId` first, then legacy rosters/preparations; `enemyFace` returns a picture or null (= name plate).

- **R-01 decision (truthful fallback):** a faction's shared figure (`Sprites.faction(f, elite)`) is used for a bestiary unit only where the figure depicts the same kind of creature (look at the six faction drawables and the bestiary bible `docs/Tiny_Blacksmith_Peoples_Names_and_Siege_Bestiary.md`; declare the rule as an explicit set in code with a comment). Siege engines (`Tags.ENGINE`) and mismatched kinds (e.g. a wolf or spider under a dragon figure) get a name plate. Never initials: `ReplayFace` carries the unit name; the plate shows it on two lines `labelSmall` (`maxLines = 2, minLines = 2` on all actor names, VA-42).
- **VA-10:** private `FaceArt(res, boxPx)`: nearest-neighbour at `k = nearestWholeScale(art, box)` when it fills ≥ 75 % of the box, else bilinear reduction; same box on every tile (`64 × wholeScale(64, 54.dp)` px). Keep the existing downed colour filter where it is (not extended). `SiegeReport.kt:156` DefenderRow face via `HeroFace(40.dp)`.
- **VA-51:** pin the newest recent line above the controls outside the scroll; labelled buttons stay; optional single row (Pause primary, Speed, Highlights) with Replay/Skip inline.
- **VA-55:** after the cost rows show `siege.contribution` (`FightAccounting.standout`) and `siege.notes` when present; army/foe name in the eyebrow; "Seen in this fight" below the defenders. No MVP wording. Extend `SiegeContributionUiTest` (lead line equals `contribution`, absent when null).
- **VA-45:** no change (source check showed no truncation; `factionAlt` has no callers).
- **Tests:** `EnemyFaceTest` — every `Bestiary.profiles` entry: `enemyFaction == profile.factionId`; every army member / roster / preparation unit resolves to a picture or is in the declared name-plate set; `town_watch`, `captive`, `escort_cart` resolve to null. Fixture test: `LivingAgencyFixtures.siegeDay()` replay actors on `Side.ENEMY` all have a picture or a plate name (no initial).

### Task 8: Integration, device evidence and documents (orchestrator)

- [ ] Compile + `:app:testDebugUnitTest` + `:core:test` (Task 4 touches core) → PASS; fix via owners.
- [ ] `:app:assembleDebug`; install on the private emulator (5562); targeted `connectedDebugAndroidTest` classes: LayoutMatrixTest, NavigationBarTest, TitleScreenTest, ShopDayScreenTest, ForgeResultCardTest, DetailSheetTest, NavigationFlowTest, ShopPanelTest.
- [ ] After-shots matching `scratchpad/before/` (360/393/412 at 1.0; 360 at 1.3/1.5/2.0; 420/440/480 dpi), plus: blessing pinned, visit receipt, fallen, death aftermath, forge reveal, Records/Notebook, Storage/Supplies, hero sheet, contract party, a living-era bestiary siege replay ("Living heroes: members decide" scenario, End Day to the siege), run end retired + fallen, three-button navigation case, reduced motion forge/notice.
- [ ] Ledger `docs/VISUAL_PASS_LEDGER_2026-10-17.md` (VA-01..58, C1–C9, R-01..03: status, files, evidence), CHANGELOG `[Unreleased]`, PROGRESS.
- [ ] Final whole-change review agent.
