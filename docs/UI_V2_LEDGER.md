# UI/UX V2 ledger

Started 2026-10-16 on `main` at `bd6939b` (untracked `build-output/` preserved). Brief: the owner's application-wide V2
redesign ("the app is text heavy everywhere"). Not committed (the brief withholds commits). This file is the resume point.

## 1. Diagnosis (baseline captures, emulator-5560, Pixel_10_Pro read-only AVD, API 37, 411 dp, font 1.0)

Evidence: `scratchpad/cap/before/` of the 2026-10-16 session, copied to `app/build/v2-evidence/before/` (ignored).

1. **Chrome eats the work area.** On the Forge with the guided day, header (≈100 dp) + guide strip (≈130 dp) + forge foot
   (≈90 dp) + nav bar (≈72 dp) leave about a third of the screen to the recipe. The header spends a full row on the
   countdown and another on the cycle track.
2. **Actions carry paragraphs.** End Day's sublabel grows with every consequence: on a Guild siege day it is seven lines
   (visitor, siege, reserved members, automatic substitutes, town substitutes) and fills a third of the Shop. The party
   sheet's pinned foot holds six lines above "Plan departure".
3. **Everything is said at full length at once.** Quest rows are 8 to 10 lines each (objective, fee, return, danger,
   every stage reward, optional stage, siege warning, last day, action). The hero sheet lists every kit rule in full;
   the weapon card does the same with interaction notes. The Charter lists every milestone in full prose.
4. **Repetition.** The besieger and matchup appear in the header, the Shop's matchup line, the Today card and the Wall.
   "Keeping members home is a valid plan" appears on Today, on the Wall and in the party sheet.
5. **Equal emphasis.** Every block is a bordered plate of the same weight; leads, door rows and reports look alike.
   Door rows spend 56 to 80 dp on "Open ›" with an empty-state sentence ("No commissions · no customer wants").
6. **Choice surfaces without a frame.** New game: a classic run is the default primary button and the charters sit
   above it as cards, so "classic or guild" is never asked. Storage: three rows of filter chips before the first weapon.
7. **Launch.** The splash shows the stock Android robot.

Already good and retained on purpose: the title screen painting and its single action; the shop-day card sequence
(outcome first, Back/Next foot, Skip to evening); the siege replay; the forge result's stat bars; `ChoiceRow`;
the theme's palette, Fira/Alegreya pairing and 48 dp floors.

## 2. Direction: "a quiet workshop, loud decisions"

One rule for every surface (given verbatim to every implementer):

> **Situation (one line) → what matters now (at most one card) → actions (one primary, at most one secondary) →
> details behind a disclosure.** Cost, deadline, danger, leaves/returns, missed siege and irreversibility stay visible;
> mechanics prose collapses. Never buy density with font size, clipping or unexplained icons.

- **Chrome budget.** Header is one row (day, countdown in its tone, gold, energy, settings) plus a 3 dp cycle track:
  ≤ 64 dp at 1.0. The guided day is one line with "Why?"; tips are one line. Nav bar 64 dp. A pinned action carries
  its label and at most one short consequence line; the rest is said where the decision is (Today card, preview sheet).
- **Three surface weights.** *Lead* (the one thing that matters now: raised ground, gold edge, one action), *Card*
  (a thing of its own: panel ground, hairline), *Row* (list items and doors: no frame, divider between). Not every
  group needs a container: section headings and spacing group lists.
- **Facts as a line, not a paragraph.** `FactLine`: short label/value pairs ("Fee 20 gold · Back day 7 · Lethal"),
  wrapping as words do. Warnings that change a decision keep their own line in ember with a "−" sign.
- **Disclosure.** `Disclosure(title, summary)`: a 48 dp row with a chevron, `Role.Button`, expanded/collapsed state
  for TalkBack, `rememberSaveable` key. Mechanics, lore, full rules, full logs and secondary reports live inside.
- **Rules as names first.** A weapon's or hero's rules show as a list of names with one-line gists; tap a name to read
  the rule in full (the writing guide's sentence).
- **Actions.** One gold `PrimaryActionButton` per screen region; `SecondaryActionButton` outline; `InlineActionButton`
  for "Details", "Open". Labels say what happens ("Plan departure", "List at 80").
- **Type.** Unchanged scale (bodyMedium 16 sp floor). Hierarchy by size and colour, never above Medium weight.
- **Motion.** Unchanged; reduced motion honoured.

## 3. Milestones

| # | Milestone | Owner | State |
|---|---|---|---|
| M0 | Baseline captures, test baseline, this ledger | main session | done |
| M1 | Shell and shared components (header, nav, End Day, intro strip, tips, notice, `Disclosure`, `FactLine`, splash) | main session | implemented, device-checked at 411 dp / 1.0 |
| M2 | Forge flagship (workbench, outlook, trays, result, bench, weapon card) | agent (worktree `bs-v2-forge`), merged | implemented; Forge, tray, outlook device-checked at 411 dp / 1.0; result, bench, weapon sheet unit-tested only |
| M3 | Shop and customer work (leads, visitor, commissions, returned, board, storage, supplies) | main session | implemented, device-checked at 411 dp / 1.0 |
| M4 | Guild (Today, board, party sheet, company, hero sheet, wall, charter, assign, loans) | agent (worktree `bs-v2-guild`), merged | implemented; Wall, Quests, party sheet, Company, hero sheet, Charter device-checked at 411 dp / 1.0 |
| M5 | Entry and records (run setup, charter, opening, intro, records, town, notebook, gazette, legacy, settings, dialogs, run end, failures) | main session | run setup, Town, run end implemented; opening chooser, Records, Settings, failures retained |
| M5b | Typography: role-based weight (SemiBold names and numbers), small-caps section headings, tabular figures, one drawn chevron | main session | implemented, device-checked at 411 dp / 1.0 |
| M6 | Matrix verification (320/360/411 dp × 1.0/1.3/1.5/2.0), TalkBack semantics, tests, docs, after captures | | open |
| M7 | V3 inspect pass (owner follow-up "Diablo-inspired UI and tooltip redesign" and its annotated review): one inspect system, Forge recomposition, Guild, Shop/weapons, Results/Siege/Records | main session (shared system, Forge) + three agents (worktrees `bs-v3-guild`, `bs-v3-shop`, `bs-v3-results`), merged | implemented; see §7 |

## 4. Surface ledger

I = inspected (before capture), R = redesigned (direction set), Im = implemented, V = verified on device.
"Retain" = kept intentionally, checked against the rule.

| Surface | File(s) | I | R | Im | V | Notes |
|---|---|---|---|---|---|---|
| Splash / launcher | `res/`, theme | ✓ | ✓ | ✓ | 411 | adaptive icon + Android 12 splash from the existing anvil art; owner may replace |
| Title / main menu | `App.kt` | ✓ | retain | | | |
| New game: mode + charter | `RunSetup.kt`, `CharterPicker.kt` | ✓ | ✓ | ✓ | 411 | "Classic run" / "Guild run" rows first; charters only for a guild run |
| Opening chooser | `OpeningChooser.kt` | ✓ | retain | | 411 | one irreversible comparison: full text is decision-critical |
| Guided first day | `Intro.kt`, `IntroStrip.kt` | ✓ | ✓ | ✓ | 411 | objective + short pointer; long sentence behind "Why?" |
| Workshop header | `SiegeHeader.kt` | ✓ | ✓ | ✓ | 411 | one band + hairline cycle track; large text keeps two rows |
| Nav bar, Back | `WorkshopScreen.kt` | ✓ | ✓ | ✓ | 411 | 64 dp (88 at large text), 30 dp icons |
| End Day + note | `WorkshopScreen.kt` | ✓ | ✓ | ✓ | 411 | one line on the button; "What happens" opens every consequence, read-only. Forge foot: headline only until M2 lands (then add "What happens" there) |
| Tips, notice | `WorkshopScreen.kt`, `Frames.kt` | ✓ | | | | |
| Shop | `ShopPanel.kt`, `ShopUi.kt`, `LeadCard.kt` | ✓ | ✓ | ✓ | 411 | doors as hairline rows; "Who is buying" folded; settled lead first sentence + "More" |
| Visitor card / sheet | `ShopPanel.kt`, `EncounterSheet.kt` | ✓ | ✓ | ✓ | 411 | the gold action names the answer ("Walk away"); relic slots one line |
| Commission board | `CommissionBoard.kt` | ✓ | ✓ | ✓ | | section notes shortened, wants called "not orders" |
| Returned weapons | `ShopPanel.kt` | | | | | |
| Storage | `StorageSheet.kt` | ✓ | ✓ | ✓ | 411 | one row of order chips led by "Filter"; filter rows on demand |
| Supplies | `SuppliesSheet.kt` | ✓ | ✓ | ✓ | 411 | facts per row ("Owned 0 · 10 gold · Up to Rare"); fight rules by name |
| Forge workbench | `ForgePanel.kt`, `ForgeUi.kt`, `ForgePurposeUi.kt` | ✓ | ✓ | ✓ | 411 | brief as facts + stock behind a row; tray comparison as facts; notes and Heartfire one row when idle; "What happens at End Day" in ⋯ |
| Forge outlook | `ForgePanel.kt` | ✓ | ✓ | ✓ | 411 | role, likely quality, suits; "Always" vs "By chance" kept apart; the rest in Details |
| Forge result | `Dialogs.kt` | ✓ | ✓ | ✓ | | identity, purpose, price; rarity rule folded after the first; no empty Buffs |
| Bench / practice | `BenchSheet.kt`, `BladeRulesUi.kt` | ✓ | ✓ | ✓ | | "This is a practice fight" plate; effects by name, chains on tap |
| Weapon sheet | `detail/ItemDetailSheet.kt`, `WeaponStatCard.kt`, `PriceEditor.kt` | ✓ | ✓ | ✓ | | identity, price, rules by name; story and history 5 lines then fold |
| Hero sheet | `detail/HeroDetailSheet.kt` | ✓ | ✓ | ✓ | 411 | kit and traits as `RuleList`; stats as facts |
| Guild Today | `GuildPanel.kt`, `GuildPlanUi.kt` | ✓ | ✓ | ✓ | 411 | short reminders; "Optional" beside the action; tab "Contracts" now "Quests" |
| Quest board | `GuildParts.kt`, `GuildUi.kt` | ✓ | ✓ | ✓ | 411 | title, objective, one fact line, missed-siege warning, stages behind a row |
| Party sheet | `ContractSheet.kt` | ✓ | ✓ | ✓ | 411 | foot = facts + siege warning + "Plan departure"; retreat rules behind a row |
| Company / recruits / loans | `GuildParts.kt` | ✓ | ✓ | ✓ | 411 | one-line note |
| Wall / assign | `GuildParts.kt` | ✓ | ✓ | ✓ | 411 | defenders first, forge and watch, enemy, then disclosures. Decision: preparation fold is closed before the eve (opens on eve and day) |
| Charter | `GuildParts.kt` | ✓ | ✓ | ✓ | 411 | milestones as rows with state chip, rule on tap |
| Town (classic) | `InfoPanels.kt`, `TownUi.kt` | ✓ | ✓ | ✓ | 411 | defense parts as facts; trait counsel behind "What helps"; champions before other threats |
| Records: Gazette | `InfoPanels.kt` | ✓ | retain | | 411 | paper styling and "All details" fold already fit |
| Records: Notebook | `Notebook.kt` | ✓ | retain | | 411 | |
| Records: Legacy | `RecordsPanel.kt`, `RecordsUi.kt` | ✓ | retain | | 411 | |
| Shop day cards | `ui/shopday/` | ✓ | retain | ✓ | | blessing card now marks then takes, as the workshop dialog does |
| Siege report / replay | `ui/replay/`, `FightReport.kt` | ✓ | ✓ | ✓ | | fight report leads with outcome + facts; sides and log folded; replay retained |
| Settings | `SettingsSheet.kt` | ✓ | retain | | 411 | |
| Blessing / relic / error dialogs | `Dialogs.kt`, `EncounterSheet.kt` | ✓ | ✓ | ✓ | | blessing as choice rows + "Take X"; relic already choice rows |
| Run end | `RunEndScreen.kt` | | ✓ | ✓ | | total first, breakdown folded, upgrades as rows, Claim then Begin era pinned |
| Load / save failures | `Failures.kt` | code | retain | | | already situation, safety, retry, confirmed start-over |
| Debug scenarios, previews | `src/debug/` | ✓ | retain | | | dev only |

## 5. Verification record

Baseline suites before any edit: `:core:test` 685 passed, `:app:testDebugUnitTest` 320 passed, 0 failures.
After M1: app 320 passed. After M3 + run setup + Town: app 320 passed, `compileDebugAndroidTestKotlin` passed.
Device: emulator-5560 (Pixel_10_Pro AVD, read-only, API 37, 1080x1920 at 420 dpi = 411 dp), font 1.0 only so far.
Evidence: `app/build/v2-evidence/{before,m1,m3,m5}/` (ignored). Width and font matrix: M6.

## 6. Log

- 2026-10-16: M0. Before captures of title, setup, guided day, Shop (classic day 1, long storage, guild siege eve),
  Forge (quick/advanced, guild), result, relic offer, Guild (Wall, Contracts, Company, Charter), party sheet, hero
  sheet, visitor, Town, Records (3 pages), Storage, weapon sheet, Supplies, board, Settings, shop-day cards.
- 2026-10-16: M1 shell (header, nav, End Day + "What happens", guided strip, `Parts.kt`: `FactLine`, `Disclosure`,
  `RuleList`; launcher icon and splash). M3 Shop, Storage, Supplies, board, visitor. M5 part: run setup, Town.
  Tests/scripts touched: `ShopPanelTest` (settled lead), `IntroStripTest` ("Classic run"), `guild.sh`
  (`setup_mode_guild`), `runvariety.sh` (supplies wording). Agents running for M2 (Forge) and M4 (Guild).
- 2026-10-16: merged M4 (Guild) and M2 (Forge) from the agents' worktrees (partitions respected, patches applied clean).
  Forge "What happens at End Day" in the ⋯ menu; shop-day blessing mark-then-take; `BlessingOption` removed (unused).
  M5: run end. M5b typography (owner, mid-session: "selective bold, font, modern and pro"). App tests 327 passed.
  Open owner question: a different body sans (Inter or Source Sans 3) or keep Fira Sans.

## 7. V3: glance, inspect, details (M7)

The main surface is for playing, a card is for understanding, a sheet is for studying; never all three at once.

- **Shared system** (`ui/Inspect.kt`, `ui/InspectUi.kt`): one `TipUi` card for every thing that can be inspected (art,
  name in its rarity colour, type line, owned, stat rows, ability rows, + gains, − costs, ◇ conditions, ? chances
  "never certain", status keywords once, at most two quiet links). One card open at a time over the whole app
  (`ProvideInspector` in `App.kt`); anchored by what was inspected when it fits, docked at the foot otherwise; Back,
  outside tap or Close dismiss it. A tap on a tile still chooses; the ringed "i" (`InfoButton`, 48 dp, "Inspect X")
  or a long press (`Modifier.inspectable`, TalkBack action "Inspect") opens the card.
- **Ability rows from typed data**: when a rule fires is its typed trigger in a few words (`triggerWords`: On hit,
  Fight start, Each round, Round end, Enemy breaks Guard), only where trigger and conditions say exactly that;
  otherwise the rule's own "When ..." clause is kept whole. How often is its typed `Limit` (`limitWords`), left out when
  the sentence already says it. The effect line is the catalog sentence's main clause (`ruleParts` splits, never cuts).
  Statuses a rule names come from its definition (`EffectDef.statuses()`), explained once from `statMeaning`.
- **Forge**: the anvil and the weapon's identity side by side (name and element, role, the rules every forge of it
  carries by name, likely quality), the mode and the board on one line, then the slots. "Effects" opens the recipe card
  (quality, suits, ability rows, needs, trade-off, the chances by name); "Full details" the recipe sheet (each
  ingredient and its cost, every rule with its source, how they combine, each chance in full). Short screens and large
  text: the scene shrinks first (72 dp; a 40 dp sprite from font 1.8), "Effects" becomes the "i" beside the name,
  "Practice fight" moves to the card and More, the siege matchup moves into the augment tray, the risk choice into the
  scroll with "Risk: X" on the pinned action. Comparison and Put back unchanged. The header keeps only the countdown on
  a short screen with large text.
- **Guild** (agent): roster rows (face, name, one chip, class, level, health, weapon sprite), hero card, compact Wall
  rows with readiness chips, enemy lines with enemy cards, quest rows as one fact line with the quest card, hero sheet
  headed like the card with open ability rows.
- **Shop and weapons** (agent): weapon rows (sprite, rarity-coloured name, rarity, power, quality, + / − names, price or
  "In storage", the stock action, "i"), weapon card with "Full details", item sheet headed like the card with open
  rules, Supplies rows with material and tool cards, commission rows, relic cards.
- **Results, siege, records** (agent): the forge result as an item reveal, fight and siege reports lead with the
  outcome with "Full report" apart, the bench leads with its result, classic Town with besieger and siege trait cards,
  Records and the run end as rows with cards.
- **Owner review corrections** (annotated zip, 6 images): duplicate effect lines removed (a metal's role note), typed
  ability rows, "Buy more" only when none are owned and as a link, "Highest rarity" became "Rarity limit: Up to Rare",
  the recipe card no longer a report (chances by name, details on the sheet), card bodies fade and clear their links,
  "3 by chance" removed from the identity line, secondary buttons quiet (cream on a dim bronze edge; gold is the
  primary action, selection and new offers), "Last day day 6" became "Send by day 6".
- **Card look, item-tooltip style** (owner: "clean, like Diablo 4"): `TipCard` is a near-black gradient plate with a
  fine frame in the card's colour (`TipUi.accent`: an element for metals, augments and recipes; else the name's rarity
  colour; else bronze), an inset rule and corner jewels; a tinted band behind the name; the type line in the frame
  colour; `TipUi.headline` (Power, likely quality) large and not repeated in the stats; ornamented dividers between
  blocks; ability lines led by a drawn diamond with numbers picked out (`numbered`), statuses the same; actions as
  right-aligned text links. Stat values that help or warn are coloured only (no "− Lethal"). From font 1.5 the head
  scrolls with the body (band included) and Close floats over the corner with a disc behind it once scrolled. The
  weapon type card no longer repeats its strike shares in a note; Heartfire's source sentence is capitalised.

Verification (2026-10-16): `:app:testDebugUnitTest` 357 passed, 0 failed (new: `GuildInspectTest`, `ShopInspectTest`,
`ResultsInspectTest`); `compileDebugAndroidTestKotlin` and `assembleDebug` pass. Device captures at 411/1.0, 360/1.0,
360/1.3 and 320/2.0 of the Forge, its cards and the recipe sheet; Shop, Storage, Supplies, board, quests, quest card,
company, hero card and sheet, Wall, enemy card, forge result and its card at 411/1.0 (360/1.3 and 320/2.0 runs in
§5 when done). Not yet: instrumented device suite, TalkBack walk, reduced-motion pass.

Card look verification (2026-10-17): `:app:testDebugUnitTest` 357 passed, 0 failed; `assembleDebug` passes. Device
captures of every card kind at 411/1.0 (metal, augment, recipe, weapon type, Heartfire, hero, quest, enemy, weapon),
the recipe, augment, hero and quest cards at 360/1.3, and the recipe, hero and quest cards at 320/2.0 at the top and
scrolled to the foot (last line clears the links).
`connectedDebugAndroidTest` (emulator-5560): 98 tests, 3 failures. `ConsistencyPassTest.townExplainsItsEmptyChampionPlaces`
fails without V3 too (it expects empty champion places on day 1; the Town has shown the forecast's champions since
2026-10-10). `DetailSheetTest.stockActionsAreReadOnlyInTheShopDay` passed in the run before, with nothing it covers
changed (intermittent, not investigated). `M4ScreensTest.theForgeMarksTheElementsTheBesiegerCaresAbout` is open: its
harness does not find `forge_threat` in the augment tray. `IntroStripTest` passes since the forge result's guided line
puts "Why?" and "Skip guidance" above the reason it opens.

## 8. Post-terminology polish (2026-10-17, branch `ui-polish-post-terms` from `6e039cc`)

Owner brief "Post-Terminology UI Polish" (handoff zip with three mid-pass screenshots). Treated as a delta on V3: the
inspect system already had typed rows, tones and keyword glossaries, so only what the current code still showed was
changed. Every claim below was read from the code (`CombatContent`, `Fight`, `Loadout`, `GuildConfig`, `Forge`).

| Brief item | Found in code | Change |
|---|---|---|
| "Hold the line" repeats Riposte | `ClassKitDef.role` ended with the counterattack Riposte owns; the Battlemage's with Echo | both sentences cut from the role (content text, fingerprint unaffected) |
| "Support 4" lacks meaning | Guardian: `Guard(Self, Support())`, exactly Support per round, cap 14; Warden: `Heal(WeakestAlly, Support())` | `supportValue`: "4 Armor each round" / "6 healing each round", only where an ability reads it (card and sheet) |
| Health shown twice | fight health = max × health% (`Loadout`) | one line "40/40 (100%)" |
| 10% share as a bare "−" | `memberSharePercent` 10, real cost; Gold-Hungry `shareDelta` 5 inside a "+" line | "Terms" section (fee, share with its parts); a trait with `shareDelta` gets no "+" |
| "CLASS"/"TRAIT" labels | source labels in tracked small caps | Ability / Passive / Trait / Speciality / Weapon in sentence case; section headings tracked 0.6 sp |
| Iron "weakest metal" as "−" | no penalty in code (Attack 100%), a tier comparison; others are conditions; Obsidian, Ember Resin, Grave Dust, Void Ink are real costs | `ConstraintKind` (DRAWBACK, CONDITION, CONTEXT) on `CoreCombatDef`/`IngredientNote`, carried by `ForgeOutlook`; tone from kind on the material card, recipe card, recipe sheet and comparison. Content 6 re-pinned, display only |
| Quality cap missing | `Forge.qualityCap` (Iron 55 … 100) | "Quality cap" stat on metal cards; "Price 10 gold each" |
| Recipe reads as a crafted weapon | bare title | "Forge preview" label |
| Effects / Practice fight look like text | 48 dp text links | dim bronze outline inside the 48 dp target |
| "None left" dimmed | whole tile at alpha 0.5 | only art and name dim; "None left" full strength, SemiBold |
| Stock badge "7" | bare number | "×7" |
| Five header segments | cycle track, decorative, full width under the purse | under the countdown; TalkBack "Siege cycle: day N of M" |
| Large text | tip banner a one-word column at 2.0 | stacked from font 1.5; card plain lines keep the text column; wrapped forge effect keeps its mark |

Kept on purpose: "Hold the line" still has two sentences (Armor each round, and drawing attacks): both are mechanics.
The "Untried combinations · Forge to learn" row was checked and left as the terminology pass wrote it. Gold and energy
icons keep their TalkBack labels ("Gold 388"); no visible label was added (the brief forbids a new HUD).

Evidence: before/after captures on emulator-5556 (Pixel_10_Pro read-only AVD, 1080×1920) at 420 dpi / font 1.0
(411 dp), 480 / 1.3 (360 dp), 540 / 2.0 (320 dp), scenario `guild_siege_eve`; session scratchpad `before/`, `after/`.
