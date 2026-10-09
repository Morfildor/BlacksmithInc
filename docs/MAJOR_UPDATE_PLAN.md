# Tiny Blacksmith: Major Update Plan, "The Shop Day"

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or
> `superpowers:executing-plans` to implement this plan task by task, under the protocol of section 8. The first slice
> has checkbox steps in the handoff at the end; later slices take their step lists from the task rows of section 7 and
> the contracts of section 6.

**Goal:** turn End Day into a shop the player watches (real customers, real transactions, real consequences, one lead
for tomorrow), make the town larger, fairer and recognisable, and close every finding of the 2026-10-09 review.

**Architecture:** the pure JVM core keeps resolving a whole day once per End Day command and now records what
happened at the counter as visit-time snapshots inside the existing `DayResolution`. The app saves that result through
one serialized `GameSession`, then replays it from a script the core derives; a small cursor row beside the save says
how far the player has watched. Nothing in the presentation can issue a command or draw gameplay RNG.

**Tech stack:** Kotlin 2.2.21, pure-JVM `:core` (kotlinx.serialization 1.9.0), `:app` with Jetpack Compose
(BOM 2026.02.01, Material 3), Room 2.8.5, DataStore 1.2.1, coroutines 1.10.2, Gradle 9.5, AGP 9.3.3, minSdk 24,
targetSdk 37; Python 3.11 with Pillow and numpy for the art importer.

**Spec:** `Tiny_Blacksmith_GDD_v1.0.md` (LOCKED / PROPOSED / DEFERRED), `docs/Tiny_Blacksmith_Thorough_Review_2026-10-09.md`
(pinned to `b39ad76`), and the owner's major-update brief (more shoppers, names and appearances; a compelling shop
day; every review recommendation). The four evidence reports written during planning, the measured art cells and the
contact sheets are in `docs/major_update_evidence/`; section 2 summarises them and this plan is the authority.

**Status:** plan complete, reviewed independently (section 11), nothing implemented. Written 2026-10-09 and
reconciled with release **v0.6.0** (`main` at `0ad888a`). Code-level checks were made at `0d5ff65`; the release
commit changes documents and the version number only (`git diff --stat 0d5ff65 0ad888a`: no source file).

## Global Constraints

- Offline premium game: no backend, telemetry, ads, billing SDK, runtime AI, timers or idle production. No network permission.
- The player never moves a character, commands combat or equips heroes. Shop actions only: forge, price, list, commissions, End Day.
- 10 base daily energy; forging is instant; every valid forge yields a usable (possibly flawed) weapon.
- Major sieges on a predictable cadence (days 5, 10, 15, ...); three dynamic champions (0-2 handled); forge integrity 0 ends the run; survival is endless; legacy is strong and claimed once per run.
- `:core` stays pure Kotlin JVM with zero Android dependencies; `GameEngine` is the only mutator; the UI never computes an outcome.
- News, replays and the counter scene come from recorded outcomes only. Presentation never consumes gameplay RNG and can never grant a reward twice.
- Player-facing text shows no probabilities and no formulas. Deterministic amounts (a price, a purse, a quality requirement) may be shown.
- Every new gameplay number lives in a **nested group** of `BalanceConfig` (the constructor is at the JVM limit of 255 parameter slots: a new flat field compiles and then fails at class load). Bump `BalanceConfig.version` on a semantic change and record the simulator evidence in `docs/DECISIONS.md`.
- New content goes through `ContentCatalog.validate()`. Counts are LOCKED: 16 materials, 6 families, 5 classes, 3 factions, 25 events.
- Art enters only through `tools/pixelart/import_assets.py`; PNGs are never hand-edited; no blanket `--pack-all`; all text is native Compose text, never baked into a sprite.
- Appearance and the art's "race" labels are cosmetic. No rule may read them.
- Preserve existing work: other sessions commit to `main`. Never stash, reset or check out over someone else's changes.
- Tests pass before a slice is called done; every user-visible change gets a `CHANGELOG.md` line; `docs/PROGRESS.md` is updated before a session ends.

## Review Focus

Five conditions the spec implies and a person will meet, each pinned to a test in the task that owns the code:

1. **A save from 0.5.x opened mid-run with an unread report** (schema 1, visits without snapshots, no cursor row, heroes without an appearance key). Expected: it loads, every hero keeps today's face, the old day opens as a plain tally or goes straight to Tomorrow. Tests: `CompatibilityTest.theV1FixtureIsAdmittedWithoutTouchingHistory` (T1.5a), `ShopDayScriptTest.aDayRecordedBeforeSnapshotsIsATallyNotACrash` (T2.2).
2. **A day with nobody to serve**: no living heroes, or an empty shelf, or nothing listed and nothing forged. Expected: one honest card and one tap. Tests: `ShopDayScriptTest.aDayWithNoLivingHeroesIsOneQuietCard`, `...anEmptyShelfNamesWhoLookedIn` (T2.2).
3. **A customer who buys in the morning and dies, retires or loses the blade that afternoon**, and a blade later pruned from the save. Expected: the counter shows them as they were; the detail sheet shows the snapshot first and "now" below; a pruned blade still opens from its snapshot. Tests: `ShopRecordTest.snapshotsShowTheCounterNotTheEvening` (T2.1), `DetailSheetTest.aBladeNoLongerInTheSaveOpensFromItsSnapshot` (T2.7).
4. **Absurd prices**: a blade listed at 0 or at 999,999 gold, and the collector. Expected: no overflow, the ledger balances, the collector cannot mint gold. Tests: `ShopLedgerTest.aFreeBladeIsASaleOfZeroCoinAndTheLedgerBalances`, `...theCollectorNeverPaysAboveTheGoingRateTimesItsMultiplier` (T1.6).
5. **Impatient hands**: End Day tapped twice, back pressed while saving, back on the Tomorrow card, the app killed at any beat. Expected: one commit, back steps a beat and never acknowledges, a kill resumes or skips without re-simulating. Tests: `GameSessionTest.doubleEndDayCommitsOnce` (T1.1), `ShopDayScreenTest.backStepsABeatAndNeverAcknowledges` (T2.5), `tools/emulator/shopday_kill.sh` (T2.9).

---

## Requirement ledger (IDs used throughout)

Review findings: F01 F02 F03 F04 F05 F06 F07 F08 F09 F10 F11.

Review section 3 (why the game is hard to follow): U01 End Day as a scene · U02 Home leads with one opportunity ·
U03 customers and requests before storage · U04 hero identity, need, taste and history · U05 forge tied to demand ·
U06 reopenable item properties and story · U07 Gazette leads with consequences.

Review section 4 (shop experience): S01 day sequence · S02 real visitors, brief quiet days · S03 tap, speed, skip,
reduced motion, 10-25 s · S04 versioned visit-time snapshot record · S05 commission and collector as distinct
transactions · S06 persisted acknowledgement and cursor, planning locked while presenting · S07 authored templates
from recorded facts · S08 the six acceptance gates of 4.4.

Review section 6 (depth): G01 sidegrade gate · G02 current-demand view · G03 Signboard and Guild Patronage ·
G04 weak permanent upgrades and a visible next-run change · G05 Advanced / technique / catalyst / request / signature /
blessing / multi-era bots · G06 unique vs repeated signature discoveries, artifact-recovery metric · G07 catalyst
clues, clue progression, recipe recall, catalyst identity · G08 hero and item detail surfaces, guild and mentor links,
weapon history · G09 cross-era artifact fidelity · G10 economy recovery beyond the hard-lock counter.

Review section 7: B01 fresh 10,000-seed review · B02 refusals made actionable, demand tuned after fair selection.

Review section 8 (UI and accessibility): A01 first-run Home action · A02 request-to-forge shortcut and recipe repeat ·
A03 tomorrow objective · A04 navigation (seven destinations, 8 sp) · A05 fixed widths, small screen, large font ·
A06 virtualised lists · A07 screenshot / a11y suite, 720x1280, low memory · A08 Settings surface, audio and haptics ·
A09 chance percentages in upgrade text · A10 art given room (52 dp forge scene).

Review section 9 (checklist): C01-C10 the ten checked entries to narrow or split · C11 report recovery scope ·
C12 pruning split from remaining growth · C13 emulator vs physical p95 · C14 PROPOSED GDD examples stay backlog ·
C15 25 events as 23 pooled + 2 rules: prove reachability.

Review section 12 (engineering): E01 invariants and loaded-save validation · E02 inline resolver constants ·
E03 engine off the main thread · E04 instrumented coverage · E05 CI · E06 release identity, R8, signing, launcher art ·
E07 stay offline.

Review section 11 (verification rows): V01-V16, mapped in section 9.

Owner's new requirements: N01 more visitors served per day · N02 larger active population · N03 name pools ·
N04 stable, varied appearances · N05 memorable regulars · N06 customer metrics and thresholds · N07 asset-use table
and a counter scene from real art, all packs · N08 a small set of new gameplay loops · N09 worked example from real
fields · N10 fresh-player protocol · N11 execution protocol, ledger, file ownership.

Found during planning (not in the review): X01 launch crash loop on a bad save, with Auto Backup on · X02 back or an
outside tap on the report acts as "Begin day"; no `BackHandler` anywhere · X03 the ended run is deleted at claim, so
run end cannot be resumed and upgrades become unreachable; the Continue button is dead code · X04 the collector pays
1.5x any listed price with no ceiling · X05 warlord tribute is counted as shop takings; material and tool spending
carry no amount · X06 a returned legend re-enters the Legend Board ownerless and can multiply · X07 lineages and the
siege stage join heroes by name string · X08 `WeaponLocation` discriminators are unpinned class names · X09
`BalanceConfig` is at the 255-slot limit · X10 one migration table serves both save documents · X11 faction ties
resolve by map order in three places · X12 `siegeForecast` builds a full `ResolutionContext` per recomposition ·
X13 the device scripts anchor on visible text · X14 refusal codes describe the whole shelf, not the declined blade;
an empty-shelf visitor uses a seat · X15 15 of the 25 live portraits carry slicing defects · X16 every art source is
AI-generated or script-drawn while the docs say "hand-made"; no licence text exists · X17 the cheapest recipe forges
fire, which two of three factions resist, and nothing says so · X18 the Traveling Ore Merchant's +2 supplier stock
is overwritten by the next morning's restock, so the player never sees it · X19 no champion can die on the walls
(champions need health 50, a lost siege costs 40), so the wall-death weapon fates are reachable only in unit tests ·
X20 lessons, inheritance and merchant resale have never been seen on a device and need reproducible scenarios, with
constructed cases kept apart from natural reachability · X21 the three 0.6.0 legacy tracks and Lucky read zero for
every bot because none forges Advanced, hunts signatures, spends scarce stock or plays several eras.

---

## 1. Recommended product direction and the improved day

### 1.1 Direction

Tiny Blacksmith already simulates a town; the player never meets it. Today a day is: configure a weapon, list it,
press End Day, read a paper. The baseline in section 2.3 shows why that feels dull: a plain fair-price smith forges
about five weapons a day and sells about one; half of all visits end "not better than what I carry"; two living heroes
share a first name on 67 % of days and a face on 52 % of them; the hero scanned eighth by the engine visits
less than half as often as the first four.

**The update turns End Day into the shop opening.** Outcomes are still computed and saved once by the pure core. What
changes is that the core records what happened at the counter, and the app plays it back as a place: the workshop
behind, your stock on the shelf, a recognisable customer at the counter, the blade they picked up, the coin they paid
or the plain reason they left. After the counter come at most three consequences that involve blades you made, then
one concrete lead for tomorrow. The Gazette stays as the complete record instead of being the only view.

Around that spine the town becomes worth meeting: twelve residents instead of eight and a counter that serves six to
eight a day with fair turns, 11,520 name combinations with no shared names among the living, a face saved per hero from
25 clean portraits (50 if the second set passes its device check, section 5.4) instead of five a class picked by hash, regulars the game recognises from recorded facts, wants the player can answer on
purpose, clues that sharpen with experiments, and blades whose stories can be followed across owners and eras.

Scope line for visuals: this update delivers the counter scene, four readable destinations, a lead card, shared hero
and item sheets and purposeful use of the art that exists. Replacing the whole visual system (bespoke chrome, final
art for every panel, audio, haptics) remains the later redesign and is listed in section 10.3.

### 1.2 The improved day, in order

| # | Moment | What the player sees and does | Source of truth |
|---|---|---|---|
| 1 | Morning | The Shop destination leads with ONE lead and its reason ("Arm the defenders: Hollowbound in 2 days, weak to sun" or "Lower a price: 3 customers could afford nothing; the cheapest blade is 108 gold"), then requests with a readiness line, then yesterday at the counter | `Advice.lead(state)` (core, pure) |
| 2 | Prepare | Forge (a lead or a request pre-fills the draft; a known recipe can be recalled), price, list, accept or decline. Each request says which blade will be handed over, or exactly what is missing | existing planning commands; `Commissions.fit` |
| 3 | Open the shop | End Day. The core resolves the whole day once and the app saves it before anything is shown | `GameEngine.endDay` through `GameSession` |
| 4 | Counter | One customer at a time: a saved face, name, class, what they carry, a factual recognition line for regulars; the blades they looked at; they buy (listed price, trade-in credit, coin to the till, bonus; the blade leaves the shelf) or leave with a typed reason and its numbers. Commission patrons and the collector are their own kind of visit. Up to three visits are featured; the rest appear as a tally with faces and names. Tap to advance, 1x / 2x, Skip day | `DayResolution.visits` with visit-time snapshots |
| 5 | Beyond the door | Up to three cards about blades you made: who carried what into which fight and what happened; a siege leads, with the existing replay as an opt-in overlay | `ShopDayScript.aftermath`, from typed field results |
| 6 | Tomorrow | Coin taken by kind, shelf left, the top lead for the next day, the blessing choice if a siege was held, "Read the Gazette" | `DayResolution.ledger`, `Advice.lead`, `Gazette.edition` |
| 7 | Defeat | On the day the forge falls the counter and the aftermath still play (the customers did come), then "The forge has fallen" and the run-end screen | `DayResolution.defeated` |

A quiet day is short by construction: no visitors is one card and one tap; an empty shelf is one card naming who
looked in; visitors without a sale feature one refusal per distinct reason and tally the rest.

### 1.3 Worked example on real fields (N09)

What the player does on day 3 and what the engine holds. Numbers are illustrative; field names are real.

| Step | Player-visible | Recorded today (`0d5ff65`) | Added by this update |
|---|---|---|---|
| Forge | Quick, bow, bronze + stormglass, balanced | `Command.Forge`; `Weapon(id = w7, familyId = bow, coreId = bronze, augmentId = stormglass, quality, rarity, power, element = STORM, affixes, condition = 100, forgedDay = 3)`; event `WEAPON_FORGED` | nothing |
| List | 108 gold | `WeaponLocation.Shelf(108)`; event `WEAPON_LISTED` | nothing |
| Counter | "Mira Ashwood, ranger, a regular. Carries a worn Iron Bow." She looks at the Stormcharged Bronze Bow. "It suits a ranger, it is storm-touched as she likes, and her own bow is worn." She buys: listed 108, trade-in 23, coin 85 | `MarketVisit(h4, "Mira Ashwood", w7, "WORN_OUT")`; `WEAPON_SOLD` with `price`, `tradeIn`, `tradedWeapon`; `WEAPON_EQUIPPED`; history `SOLD`, `EQUIPPED`, and `TRADED_IN` on the old bow, which returns to storage | `MarketVisit.customer` (purse 96, loyalty 3, `regular`, `equipped` = the Iron Bow at condition 44), `considered = [Considered(w7, 108, [SUITS_CLASS, ELEMENT_TASTE, STRONGER_THAN_OWN, OWN_BLADE_WORN, CAN_AFFORD])]`, `sale = Sale(listedPrice 108, tradeInCredit 23, cashPaid 85, saleBonus 0)`, `recognition` |
| Beyond the door | "Mira routed Hollowbound scouts with the bow she bought this morning." | `EXPEDITION_WON` with subjects `[h4, w7]`; history `VICTORY`; visits resolve before activities in the same End Day, so "this morning" is true | `FieldResult(h4, WON, foe, weapon = snapshot of w7, lostWithOldBlade, lostBareHanded, matchupHelped, gold)` |
| The honest claim | For a blade bought that morning, only when `lostWithOldBlade` is true: "With her old Iron Bow the same fight was lost." For any other blade, only when `lostBareHanded` is true, and worded as exactly that. Otherwise the card says she carried it and stops there; no card ever says a blade "decided" a fight | nothing: today the event only says she won "using" the bow | two booleans computed from the recorded roll: the fight was won, and the same roll loses under the same formula with the old blade (or with no weapon) |
| Tomorrow | "Hone Mira's old Iron Bow and put it back on the shelf: Wren Kestrel could afford nothing yesterday (purse about 70; the cheapest blade was 108)." | the traded-in bow in storage; Wren's `TOO_EXPENSIVE` string | `Considered.shortBy`, the lead from `Advice.lead`; from M4 also `Hero.want` |

The line never claims the bow caused the victory unless the stored calculation says so, and never invents a taste, a
mission or a quote: every clause is a field, and a missing field drops its clause.

### 1.4 Where the 0.6.0 mechanics appear in the shop day

Release 0.6.0 added mechanics whose value is today a line of text. Each gets a place in the day instead of a new
screen:

| 0.6.0 mechanic | Where the player meets it | Record it comes from | Task |
|---|---|---|---|
| Signboard: one more seat per level | "Seats 6" on the Shop header becomes 7 and 8; the closing till says "2 found the shop full" when seats ran out, which is the reason to buy it | `ToolEffect.EXTRA_CUSTOMERS`; `DayResolution.turnedAway` | T2.8b, T3.1 |
| Stronger affixes | the item sheet states each affix and flaw with its description; an aftermath card names the affix when the stored result says it counted (`matchupHelped`, Giant Slayer on an elite) | `Weapon.affixes`, `FieldResult` | T2.7, T2.2 |
| Lucky's scarce loot | aftermath card "brought back {scarce material}" with the material icon; the Supplies sheet shows the new stock | `FieldResult.materialId` | T2.6 |
| Guild hall days and lessons | aftermath card when both heroes are customers of the shop ("Wren learned from Aldric at the hall"); guild and mentor on the hero sheet and the Town row | `GUILD_TRAINED` and lesson records; `FieldResult.withHeroId` | T2.6, T2.7 |
| Ambition days | the hero sheet shows the ambition and its progress; fulfilled with one of your blades is a milestone recognition line | `Hero.ambition`, `AMBITION_PURSUED`, `AMBITION_FULFILLED` | T2.7, T3.5 |
| Expedition fight replays | "Watch the fight" on the aftermath card of an elite kill or a death, not only on sieges | `CombatReplay(kind = EXPEDITION, eventId)` | T2.6 |
| Guild inheritance | aftermath card "{blade} passed to {guildmate}"; the next time the heir visits: "carries {dead hero}'s {blade}"; a line in the blade's story | `WEAPON_INHERITED`, history `INHERITED` | T2.6, T3.5, T4.5 |
| Merchant resale | aftermath card, clearly not a shop sale (no coin reaches the till); the blade's story gains the new owner | `WEAPON_RESOLD`; never a `MarketVisit` | T2.1, T2.6 |
| Known Name regulars | on day 1 the counter introduces them as regulars ("already favours your shop"), and the run-end row says "Next era: one more starting regular" | the Known Name start record; `Market.isRegular` | T3.5, T5.3 |
| Caravan Ties | the Supplies sheet marks the extra rare stock; the SCARCE_RECIPE bot measures it | supplier stock from `legacyTracks` | T2.8c, T0.7 |
| Anvil Lore | the clue ladder: a signature that "answers the hammer more readily" is told in words on the journal entry, never as odds | `Journal`, `legacyTracks` | T4.3 |
| Homing Steel | a returned legend arrives as what it was, with its story in the item sheet; multi-era bots count genuine returns | `LegendEntry`, `ARTIFACT_RETURNED` | T4.5, T0.7 |
| Report recovery, pruning, timing check | kept: the unread-day recovery becomes the Resume prompt; pruning rules are extended, never replaced; `EndDayPerfTest` stays and gains the long-save cases | existing code | T2.3, T6.3a, T6.3b |

---

## 2. Current-state evidence, baseline, already-fixed findings, uncertainties

### 2.1 Repository state (it moved during planning)

Another session was landing session-8 work on `main` while this plan was written. Everything it had in flight is
now merged, so the update starts from a clean head.

| Commit | What | Relation to the review |
|---|---|---|
| `b39ad76` | review snapshot: app 0.5.0, balance v4, rules 1, 144 core tests | every review line number refers to this |
| `4ecbac0` | docs only | none |
| `d7b3283` | balance v5 part 1: Signboard = `ToolEffect.EXTRA_CUSTOMERS` (+1 customer a day per level); stronger affix magnitudes | fixes the Signboard half of G03; F05 untouched |
| `26c66a0`, `77919fe` | hero daily life: `HeroActivity.GUILD` and `AMBITION` as scored activities, `BalanceConfig.heroLife` | changes the activity mix; shop visits unchanged |
| `3643335`, `8cc133b` | fight replays for elites and deaths (`ReplayKind`, `Battle.dayReplays`), weapon fates (guild inheritance, `Market.resolveMerchant`), an artifact-recovery metric | partly answers G06; adds a second purchase path the record must not count as a shop sale |
| `eac17dc` | Lucky brings back scarce materials; simulator counts affixes at forge time | none |
| `16799b0`, `0d5ff65` | legacy tracks (Caravan Ties, Anvil Lore, Homing Steel), Known Name starts heroes as regulars, simulator "yardsticks" | starts G04; the review's upgrade table is now two versions old |

| `0ad888a` (tag `v0.6.0`) | release 0.6.0: balance v5 reviewed at 10,000 seeds; documents and version only | closes the review's "no current 10,000-seed review" for v5; **no source change**, so none of F01-F11 is touched |

Head for this plan: **`0ad888a`, app 0.6.0** (versionCode 6), balance v5, content v2, `RULES_VERSION` 1, schema 1,
186 core tests in 26 classes. Sources are identical to `0d5ff65`, where F01-F11 were each rechecked against the
code (2.4): the release fixed none of them, which `docs/PROGRESS.md` of session 8 also states. Line numbers below
are from `0d5ff65` = `0ad888a` unless marked; tasks name symbols so they survive drift.

What 0.6.0 shipped and this update builds on, not over: the Signboard's extra seat per level; stronger affixes and
Lucky's scarce-material loot; guild hall days, lessons from a higher-level guildmate and ambition days as scored
activities; replays for significant expeditions, guild inheritance and merchant resale of fallen heroes' blades;
Caravan Ties, Anvil Lore, Homing Steel and Known Name regulars; the unread report reopening after process death,
terminal-weapon pruning and the instrumented End Day timing check. Section 1.4 says where each of them appears
in the shop day.

### 2.2 Toolchain and devices (checked, not assumed)

| Item | Result |
|---|---|
| JDK / Gradle | JDK 21 launcher, JDK 25 daemon toolchain; the wrapper runs from the local cache |
| Android SDK | `C:\atools\sdk` (platform-tools, emulator, build-tools) |
| Device | `emulator-5554` attached; AVDs `Pixel_10_Pro`, `Tablet`, `Tablet_2`, `Tablet_7` and three unrelated ones. **No physical device**: every hardware gate here is a blocked check until one is attached |
| Python | 3.11.9, Pillow 12.3.0, numpy 2.4.6 |
| CI | no `.github/` directory; no workflow has ever run (E05) |
| Connectors | the GitHub and Playwright MCP servers failed to connect during planning; neither is needed by this plan |

### 2.3 Baseline produced in the planning session

| Check | Command | Result | Caveat |
|---|---|---|---|
| Core tests | `./gradlew :core:test` | **186 tests, 26 classes, 0 failures** | run once |
| **Balance baseline: v5 at 10,000 seeds** (release 0.6.0, recorded in `docs/DECISIONS.md`, "Balance v5 review at 10,000 seeds"; not re-run here) | `./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE --perf --json <out>"` | BALANCED_FAIR 20 (15/25) mean 20.7, sold 19.3; BALANCED_ACTIVE 30 (20/35) mean 27.4, sold 31.3; BALANCED_INVEST 30 (20/40) 30.3; SYNERGY 35 (25/40) 34.8; BALANCED_EXPENSIVE 10 (10/15) 12.8; PASSIVE 10; all upgrades: FAIR 35 (30/40) 36.5, ACTIVE **45 (35/50) 42.3, longest 55**; every run ends; 0 hard-locks; artifact recovery 49-55 %; desktop End Day p95 1.71 ms | this is the baseline every later comparison uses |
| Reproduction at 1,000 seeds (this session) | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --noImpact --json <file>"` | BALANCED_FAIR 20 (15/25) mean 20.5, forged 102.7, sold 19.3 (19 %); BALANCED_ACTIVE 25 (20/35) mean 27.4, sold 31.5 (28 %); SYNERGY 35 (25/40) mean 34.7; BALANCED_EXPENSIVE 10 mean 12.9; PASSIVE 10; 0 hard-locks | agrees with the 10,000-seed means within 0.2 days; the ACTIVE median sits on the siege boundary and reads 25 or 30 |
| Visit outcomes at the head (same 1,000-seed run) | same | BALANCED_FAIR: 72.8 visits a run = 3.5 a day; bought **23.1 %**; NOT_BETTER 54.1 %, TOO_EXPENSIVE 20.9 %, NOT_SUITED 1.1 %, UNDECIDED 0.8 %. BALANCED_ACTIVE: 130.5 visits = 4.8 a day; bought **21.7 %**; NOT_BETTER 50.6 %, TOO_EXPENSIVE 26.2 % | from the `shop visits/run` line. "19 %" and "28 %" in the rows above are sell rates (sold / forged), not conversion (bought / visits) |
| Core tests, app unit tests, debug build, lint at `0ad888a` | `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` | BUILD SUCCESSFUL: core 186 / 186; app unit 1 / 1 (the template test only); `app-debug.apk` built (16.8 MB); lint 0 errors, 38 warnings | run once at the end of planning, on a clean tree |
| Instrumented tests and the device loop | `./gradlew :app:connectedDebugAndroidTest`; `tools/emulator/smoke.sh` | **not run by this session.** Recorded by session 8 for 0.6.0: instrumented 6/6, `SMOKE_DONE`, `EndDayPerfTest` p50 8.3 / p95 21.3 / max 47.1 ms with 367 weapons at day 120 on the `Pixel_10_Pro` AVD | the emulator belonged to the other session during planning; not exercised on device per `docs/PROGRESS.md`: a hall day, a lesson, the merchant lines, an inherited blade |

Customer and identity baseline. Measured with a scratch harness kept outside the repository, compiled against a
frozen jar of the head (a plain fair-price smith, 500 seeds, 10,535 simulated days; its median of 20 days matches
BALANCED_FAIR). T0.4 moves these metrics into the simulator so they become reproducible project evidence; until then
they are the planning baseline, not a certified result.

| Metric | Today (8 start / floor 5 / cap 12 heroes, 4 seats) |
|---|---|
| Living heroes per day | 8.2 |
| Visitors served per day | mean 3.56; at the cap on 69 % of days |
| Purchases per day | 1.02 (28.5 % of visits) |
| Visit rate by position in the engine's ID-sorted scan | positions 1-4: 53-54 %; 5: 49 %; 6: 41 %; 7: 31 %; 8: 23 %; 9: 13 %; 10: 8 %; 12: 4 % |
| Unique visitors per run | 9.2 of 9.5 heroes ever in town; 3.6 % never visit |
| Wait from arrival to first visit | median 1 day, p90 4, max 21 |
| Classes served per day | 2.7 of 4.2 present |
| Return-visit share | 88 % |
| Two living heroes share a first name | 67 % of days (30 first names) |
| Two living heroes share a surname | 74 % of days (24 surnames) |
| A full name repeats inside a run | 7 % of runs |
| Two living heroes share a face | 52 % of days (5 faces per class by `id.hashCode() mod 5`); worst case 3 on one face |

F05 is confirmed with a measured effect at ordinary demand: the hero scanned eighth visits 2.4 times less often than
the first four. The order is by ID **string**, so `h10`-`h19` (later arrivals) queue ahead of `h2`-`h9` (the starting
cast), and `h7`, `h8`, `h9` hold the starved seats.

Directional what-if on the same harness (300 seeds each; selection still unfair; rows 2-9 on the `d7b3283` jar, the
last row repeated on the head):

| Heroes start / floor / cap | Seats | Other | Survival median / mean | Visitors / day | Purchases / day | Shared first name | Shared face |
|---|---|---|---|---|---|---|---|
| 8 / 5 / 12 | 4 | today | 20 / 21.0 | 3.51 | 0.98 | 64 % of days | 49 % |
| 10 / 7 / 14 | 6 | | 25 / 24.1 | 5.16 | 1.31 | | |
| 12 / 9 / 16 | 4 | | 25 / 23.9 | 3.95 | 1.20 | 92 % | 90 % |
| 12 / 9 / 16 | 6 | | 25 / 25.4 | 5.62 | 1.47 | 91 % | 90 % |
| 12 / 9 / 16 | 8 | | 25 / 25.9 | 6.77 | 1.60 | 92 % | 90 % |
| 14 / 10 / 18 | 8 | | 30 / 27.8 | 7.39 | 1.75 | 97 % | 95 % |
| 12 / 9 / 16 | 6 | `raidPerDay` 6.5 | 25 / 24.1 | 5.60 | 1.50 | | |
| 12 / 9 / 16 | 6 | `raidPerDay` 7.0 | 25 / 22.6 | 5.57 | 1.52 | | |
| 12 / 9 / 16 | 6 | `expeditionSuppression` 2 | 25 / 24.1 | 5.58 | 1.43 | | |
| 12 / 9 / 16 | 6 | `raidPerDay` 7.0 and suppression 2 | 20 / 21.4 | 5.53 | 1.49 | | |
| 12 / 9 / 16 (head) | 6 | | 25 / 26.0 | 5.66 | 1.46 | 92 % | 90 % |

What follows from it:

- **Seats, not residents, make visitors.** Twelve heroes at four seats serve the same four. Twelve heroes at six
  seats serve 5.6 a day (+59 %), at eight seats 6.8 (+92 %).
- **Residents make the town safer.** About +1.2 mean days per extra resident at fixed rules (8 to 12 heroes: 21.0 to
  25.4-26.0). Population cannot rise without a threat lever; `raidPerDay` 7.0 with `expeditionSuppression` 2 returns
  the 12-hero town to today's survival, which is where the sweep of section 4.3 starts.
- **Purses bind sales.** Visitors rise 59 % and purchases 50 %; conversion slips from 28 % to 26 %. Extra seats for
  the same purses mostly add refusals, which is why the refusals must become informative (section 4.6, E1).
- **Names and faces are a precondition of the bigger town.** With today's 30 x 24 names and five faces a class, a
  12-hero town shows a shared first name or a shared face on nine days in ten.

### 2.4 Status of every review finding (checked at `0d5ff65`, identical sources to 0.6.0)

| ID | Status | Current evidence |
|---|---|---|
| F01 | **Confirmed** | `GameViewModel.dispatch` alone has a busy guard (`:111`); `buyUpgrade` computes from the UI snapshot taken at tap (`:151-153`); `newRun` and `claimLegacy` re-read the store (`:80`, `:136`); claim and upgrades write `saveAtomically(null, ...)`, which deletes the run row (`SaveStore.kt:40`). Race frequency not measured |
| F02 | **Confirmed, worse than stated** | no `try` or `CoroutineExceptionHandler` in `app/`: a decode error in `init` (`:65-77`) kills the process on every launch (X01); a failed save kills it before the UI update |
| F03 | **Confirmed**, re-verified on the fixture (20 archived records against 15 in the report, the five missing all `WEAPON_FORGED`) | `GameEngine.endDay`: `val dayEvents = ctx.newEvents.toList()` (`:307`); the archive reads `state.eventsForDay(day)`; also missing: signature discoveries, forge milestones, blessing choice, day-1 arrivals |
| F04 | **Confirmed, wider** | `Labels.quality` bands 35 / 50 / 70 / 85 against `quality >= minQuality` (`Market.resolveCommissions`); the band word is also on Home, the Gazette offer line always says "fine", noble commissions (60-75) and the COLLECTOR ambition (60) sit mid-band |
| F05 | **Confirmed, measured** (2.3) | `ResolutionContext.aliveHeroes()` sorts by ID string; `Market.resolveShelfVisits` breaks at the cap (`:25-26`) |
| F06 | **Confirmed, wider** | visits before commissions (`GameEngine.kt:287-288`); the patron takes the highest-quality match including off a priced shelf (`Market.kt:197`); `resolveMerchant` now runs third |
| F07 | **Confirmed, wider** | `Market.purchase` never records `bonus`; the collector's payment is an untyped world-event variable; a fatal expedition emits `HERO_DIED` only; the tally counts warlord tribute as shop takings (X05); the simulator's `goldEarned` has the same holes |
| F08 | **Confirmed** | `Battle.selectChampions` ranks with `elite = false` (default of `Power.defensePower`); `Battle.outlook` values with `elite = warlord`; the un-contexted ranking also feeds `town.championIds` |
| F09 | **Confirmed, worse** | balance v5 (five parts) and new RNG draw patterns shipped under `RULES_VERSION = 1`, content 2; `GameState` has no balance version; `handle` checks nothing |
| F10 | **Partial** (as the review says) | terminal pruning present and extended to the merchant reason; unsold storage dominates growth (196 of 235 weapons, 151 KB of 286 KB in the fixture); closed commissions, processed command IDs, kept-forever events and non-combat history still grow |
| F11 | **Confirmed, narrowed to two sites** | `"%.2f".format(winProbability)` at `Battle.kt:56` and `:60`. Kotlin's `lowercase()` / `uppercase()` are locale-invariant and are not findings |
| U01-U07 | **Confirmed**, all seven rows | Android evidence: one dialog after End Day; eight equally styled Home blocks; Market order shelves, storage, customers, commissions; `HeroRow` never reads taste, loyalty, guild or mentor; Forge shows no weakness or request; affix descriptions only in the reveal; a sentence per hero in the paper |
| G03 | **Half fixed** | Signboard adds a seat per level since `d7b3283` (measured +0.4 days, +3.5 sales for the active smith). Guild Patronage is still +15 points of visit chance under the cap |
| G04 | **Partly addressed in 0.6.0** | Known Name was reworked (starting regulars) and now measures +1.6 mean days (was -0.4); per-track impact at 10,000 seeds under BALANCED_ACTIVE, level 3 against none (mean days): Walls +6.2, Cellar +3.5, Mastery +2.8, Known Name +1.6, Thrifty +1.2, Lucky +0.6, Tireless +0.4, Savings +0.2, Caravan Ties / Anvil Lore / Homing Steel 0.0 (no bot hunts signatures, repeats a rare recipe or owns a Legend Board). Second yardsticks exist: Known Name holds the first siege in 93 % of runs (86 % without), Tireless Smith forges 147 a run (114), Family Savings owns 4.5 tool levels by day 5 (3.6) |
| G05, G06 | **Confirmed**; G06 partly answered | bots are Quick-only, take the first blessing and never forge for a request; `signatureDiscoveries` counts transformed weapons; an artifact-recovery metric now exists |
| G07 | **Confirmed** | `Journal.recordSignatureClue` returns unless the entry is `UNKNOWN`; every catalyst reads "a steadying hand"; all four catalysts share one bonus (`Forge.kt`) |
| G08, G09 | **Confirmed** | nothing in `app/` reads `Weapon.history`; `LegendEntry` stores no affixes, flaws, catalyst or signature; owners come from `SOLD` / `COMMISSION` only; X06 |
| G10 | **Confirmed** | `hardLocks` tests yesterday's leftover shelf before the day's listing step |
| A01-A10 | **Confirmed** | 52 dp forge strip (`ForgePanel.kt:85`); seven destinations with 8-12 sp auto-size; `width(112.dp)` and `width(116.dp)`; non-lazy columns; Settings inside Legacy; no audio or haptics; two percentages in upgrade text (`LaunchContent`); no Home tip |
| C01-C15 | **Mostly still to apply** | per item in 3.4: C10 and C13 were applied by 0.6.0; C11 is correct; C05 and C07 are partly applied; C12 is one ticked line where the review asked for a separate open line for the remaining growth; C01-C04, C06, C08, C09 are untouched |
| E01-E06 | **Confirmed** | invariants check no content reference; inline constants in `Market`, `Heroes`, `Battle`, `WorldEvents`; the engine runs on Main; six instrumented tests, none on the ViewModel; no CI; template launcher icon, R8 off, no signing |
| E07 | **Holds** | no permission, no network, ads, billing or analytics dependency |

### 2.5 Where the review is stale or imprecise (do not schedule these as written)

1. Section 6.2 and the F05 context say the Signboard raises visit probability. Since `d7b3283` it adds seats. Only Guild Patronage is still a probability.
2. The upgrade table in 6.3 is balance v4; Known Name has since been reworked.
3. F02: the process dies; nothing is left "busy". The serious consequence is the launch crash loop.
4. F11 is exactly two format calls; case-mapping is not a locale risk in Kotlin.
5. C11 (report recovery) is already ticked with the right scope; C13 (p95 by runtime) and C10 (top strip) were applied by 0.6.0. Do not redo them. C12 still needs its open line (3.4).
6. The artifact-recovery metric asked for in 6.4 exists since `8cc133b` (49-55 % of fallen heroes' blades come back); T0.4 extends it to cross-era returns instead of creating it.
7. "The latest evidence is 1,000 seeds per policy" (review 7) is no longer true: 0.6.0 reviewed balance v5 at 10,000 seeds. What remains of B01 is the review **after** this update's rule changes.
8. Two stale lines in `docs/PROGRESS.md` ("Known Name is not [applied]", "v4 and v5 are checked at 1,000 seeds only", and the maxed account "to be confirmed at 10,000 seeds") were corrected at the end of this planning session; T0.3 does the full pass.
9. Departures from PROPOSED GDD values, called out here and recorded in `docs/DECISIONS.md` by T0.3: starting population 8 is below the GDD's own 10-14, so 12 moves toward it; "roughly 2-4 customers a day" becomes 6 seats; the GDD 12 panel list (Market, Town, Journal, Gazette, Legacy, Settings) becomes four destinations with sheets. No LOCKED decision changes. One GDD 12 / 19 line stays open after this update: audio and haptic toggles (10.3).

### 2.6 Remaining uncertainties

- How often the F01 races fire in practice (source-derived, not reproduced).
- Whether the reference-board portraits can join the live busts at all: decided by the three-step gate of 5.3 in M2; until then the guaranteed face count is 25.
- The exact compensation numbers for the larger town (section 4.3 decides them; the table above only locates them).
- Platform behaviour on tablets at targetSdk 37 (portrait lock is likely ignored at 600 dp and wider); never seen.
- What Android Auto Backup restores for this app (`allowBackup="true"` with template rules); T1.2 turns it off until a load-failure path exists.
- Whether players enjoy the result. Nothing in this plan's automated evidence can show that; section 9.7 is the gate.

The four evidence reports behind this section, the measured art cells and the scratch harness are kept in
`docs/major_update_evidence/` (`01_core_persistence.md`, `02_economy_customers.md`, `03_android_experience.md`,
`04_assets_content.md`, three `*.json` cell files, `crops/`, `measure/`). They cite `path:line` at `77919fe` /
`8cc133b`; this plan is the authority where they disagree with it.

---

## 3. Traceability matrix

Status is the short form of section 2.4. "Task" refers to section 7; "Check" to section 9 or the task's exit gate.
Rows that duplicate another are marked "= ID" and carry no separate work.

### 3.1 Review findings F01-F11

| ID | Status | Intended behaviour | Task | Needs | Check |
|---|---|---|---|---|---|
| F01 | confirmed | every authoritative operation goes through one serialized `GameSession`; state is read inside the lock; an operation issued against a previous run is dropped; the ended run is kept until Begin era | T1.1, T1.3 | T0.5 | `GameSessionTest` (4 race tests, both release orders) |
| F02 | confirmed, worse | typed `LoadFailed` and save-failure states; the last committed state stays on screen; stored bytes are never replaced on failure; Retry; "start over" quarantines instead of deleting | T1.2 | T1.1 | `GameSessionFailureTest`; `SaveStoreTest.corruptPayloadIsReportedAndTheRowIsUntouched`; corrupt-save screenshot |
| F03 | confirmed | `DayResolution.events` is the whole day, preparation included; report, Shop and archive render one `Gazette.edition` | T1.4 | none | `DayEditionTest` (3 tests incl. the fixture) |
| F04 | confirmed, wider | commissions ask for a band; one function `Commissions.fit` decides delivery and drives the UI; each candidate shows "fits" or the one thing missing | T1.8 | T1.5b (old saves) | `CommissionRulesTest` |
| F05 | confirmed, measured | fair seeded selection: one intent draw per living hero, weighted seats, nobody turned away three willing days running; numeric hero order | T3.1 | T0.4 | `CustomerSelectionTest`; sweep step 1 |
| F06 | confirmed, wider | commissions resolve before browsers; the least sufficient eligible blade is handed over, storage before shelf; the card previews which blade | T1.8 | none | `CommissionRulesTest.aSoleCandidateIsDeliveredBeforeBrowsersArrive`, `...theLeastSufficientBladeIsHandedOver` |
| F07 | confirmed, wider | typed `Sale` on every transaction, `ShopLedger` by income kind, `FieldResult` per hero including fatal losses; tribute on its own line; material and tool spending recorded with amounts | T1.6, T2.1 | T1.4 | `ShopLedgerTest` (ledger balances on every day of 200 runs) |
| F08 | confirmed | `Battle.selectChampions(ctx, faction, elite)` uses the same foe context as the valuation, in `outlook`, the siege and `recover` | T1.7 | T1.5b | `ChampionSelectionTest` |
| F09 | confirmed, worse | four version numbers with four policies; `Compatibility.admit` validates a loaded run; newer or invalid runs reach the recovery screen; fingerprint tests force version bumps | T1.5a, T1.5b | T1.2 | `CompatibilityTest`, `VersionFingerprintTest` |
| F10 | partial | keep terminal pruning; bound closed commissions, processed IDs, routine kept-forever events and non-combat history; never delete stock; measure a production-shaped soak; add storage filters and bulk actions | T6.3a, T6.3b, T6.3c | T0.7 | `ProductionSoakTest`, `LongSaveDeviceTest` |
| F11 | confirmed (2 sites) | no formatted decimal in any event payload; the win probability leaves the payload | T1.6 | none | `LocaleDeterminismTest` (US, German, Turkish) |

### 3.2 Why the game is hard to follow (U) and the shop experience (S)

| ID | Status | Intended behaviour | Task | Needs | Check |
|---|---|---|---|---|---|
| U01 = S01 | confirmed | the day sequence of section 1.2 and the state machine of 6.4 | T2.3, T2.5, T2.6 | T2.1, T2.2 | `ShopDayScreenTest`; device loop; S08 |
| U02 = A03 | confirmed | the Shop destination and the Tomorrow card lead with one lead and one reason from `Advice.lead` | T2.2, T2.8b | T1.8 | `AdviceTest`; playtest Q4 |
| U03 | confirmed | requests and yesterday's counter above the shelf rows; storage behind a sheet | T2.8b | none | screenshot check of the Shop order |
| U04 | confirmed | hero sheet: face, class, health word, element taste, traits, purse, regular, ambition, guild, mentor, weapon, "with your shop", recent events | T2.7 | T2.1 | `DetailSheetTest`; playtest Q1 |
| U05 = G02 | confirmed | the forge shows what is wanted: the lead, open requests with "Forge this", the besieger's weakness, a "Who is buying" summary, from M4 named wants | T2.8b, T2.8c, T4.1 | T2.2 | `ForgeShortcutTest`; playtest Q4 |
| U06 | confirmed | item sheet reopenable from shelf, storage, counter, Town, news: affixes and flaws with descriptions, recipe, history | T2.7 | none | `DetailSheetTest` |
| U07 | confirmed | aftermath shows at most three consequences about the player's blades; the Gazette is the full record behind one tap | T2.2, T2.6 | T1.6 | `ShopDayScriptTest.aftermath*` |
| S02 | n/a | only recorded visitors; quiet-day cards | T2.2, T2.5 | T2.1 | Review Focus 2 tests |
| S03 | n/a | tap, Next, Back, 1x / 2x / tap-only, Skip day; reduced motion merges a visit into one static card; three featured visits keep 6-10 visitors near 25 s | T2.5, T3.7 | T2.2 | `ShopDayScreenTest.reducedMotionNeverAutoAdvances`, timing table in the device loop |
| S04 | confirmed missing | `MarketVisit` extended in place with visit-time snapshots (section 6.2) | T2.1 | T1.6 | `ShopRecordTest` |
| S05 | confirmed missing | `VisitKind.COMMISSION` and `COLLECTOR` visits with their own `Sale` | T2.1 | T1.8 | `ShopLedgerTest.commissionOnlyDay...`, `...collectorPurchaseIsItsOwnKind` |
| S06 | partly present (unread report reopens) | cursor row beside the save; planning refused while the counter or aftermath is unwatched; resume or skip after a kill | T2.3 | T1.1 | `PresentationEqualityTest`; `shopday_kill.sh` |
| S07 | n/a | authored templates over typed reasons and recorded numbers; a missing field drops its clause | T2.2, T3.5 | T2.1 | `ShopDayScriptTest.everyLineMapsToARecordedField` |
| S08 | n/a | the six gates of review 4.4 | T7.2 (gates 1-4, 6), T2.3 (gate 5) | M2 | section 9.7 |

### 3.3 Depth (G) and balance (B)

| ID | Status | Intended behaviour | Task | Needs | Check |
|---|---|---|---|---|---|
| G01 | confirmed | gain kept as a Double; a near-equal blade may be bought for a one-way side reason (taste, prized, storied) and, in a siege warning, for the matchup | T4.2 | T3.4 | `SidegradeTest`; 1-4 sidegrades a run, no hero more than three |
| G02 | = U05 | | | | |
| G03 | Signboard fixed; Patronage open | Signboard = seats (kept). Guild Patronage = guild members always willing plus a recorded guild stipend toward one purchase each; "guests of the guild" is the fallback arm | T3.6 | T3.4 | sweep step 6; patronage metrics |
| G04 | partly addressed in 0.6.0 | a purpose metric per track (four exist, the three new tracks need bots); rework only tracks that fail both gates after balance v6; the run-end screen states the concrete next-run change. No track and no maxed-account number is lowered just because the maxed active median is 45 days | T5.3 | T0.7, T3.4 | upgrade gate table (4.7) |
| G05 | confirmed | new bots and blessing strategies, multi-era play | T0.7 | T0.4 | `SimulatorPolicyTest` additions |
| G06 | partly answered | `signatureFirsts` vs `signatureWeapons`; extend the existing recovery metric to cross-era returns | T0.4 | none | simulator JSON fields present and reproduced |
| G07 | confirmed | a clue ladder per signature (four rungs), one authored phrase per catalyst, clues keep answering after the first miss, "Use this recipe" on a discovered signature; catalyst identity through those phrases and an honest description (four mechanical jobs deferred, 10.3) | T4.3 | T0.7 (SIGNATURE_PURSUIT as acceptance) | `ClueLadderTest` |
| G08 | confirmed | = U04 + U06, plus guild and mentor links in the hero sheet and current holder in the item sheet | T2.7 | | |
| G09 | confirmed | `LegendEntry` keeps affixes, flaws, catalyst, signature and the full owner line; a returned blade keeps what it is named for; a legend cannot re-enter the board ownerless or multiply (X06); its affixes return dormant until honed | T4.5 | T1.5b | `ArtifactFidelityTest` |
| G10 | confirmed | resting / drought / stuck measured separately; shock scenarios; a valve only if stuck streaks appear | T5.2 | T0.4 | `stuckDays` under NOVICE, SPENDTHRIFT, BROKE_START |
| B01 | **done for v5** (0.6.0); open for this update | the 10,000-seed v5 review is the baseline; one more 10,000-seed review of every policy after this update's last rule change, reported as distributions with upgrade yardsticks | T5.5 | M3-M5 | DECISIONS table with noise floors, compared with the v5 table row by row |
| B02 | open | refusals carry their reason and numbers (S04), wants make them actionable (E1), demand is tuned only after fair selection | T2.1, T4.1 | T3.1 | conversion and refusal-mix band (4.3) |

### 3.4 UI and accessibility (A), checklist (C), engineering (E)

| ID | Status | Intended behaviour | Task | Needs | Check |
|---|---|---|---|---|---|
| A01 | confirmed | day 1 opens on the lead "Forge your first blade" with its reason; the first counter visit explains the controls once | T2.10 | T2.8b | playtest minute 0-1 |
| A02 | confirmed | "Forge this" on a request or a want pre-fills the draft; "Use this recipe" on known signatures, "Use" on any understood journal row and "Forge again" on the result card | T2.8c, T4.1, T4.3 | | `ForgeShortcutTest` |
| A03 | = U02 | | | | |
| A04 | confirmed | four destinations (Shop, Forge, Town, Records) with fixed-size labels and a gear; back returns to Shop | T2.8a | T1.3 | `NavigationBarTest.fourDestinationsKeepTheirLabelSize` |
| A05 | confirmed | no fixed-width control; passes at 360x640 and 320x569 dp and font scale up to 2.0 | T2.8c, T6.1 | | `LayoutMatrixTest.noOverflow`, `layout.sh` screenshots |
| A06 | confirmed | lazy lists with keys for shelf, storage, heroes, archive, journal | T2.8a, T2.8b, T2.8c | | `StorageSheetTest.twoHundredFiftyWeaponsComposeOnlyVisibleRows` |
| A07 | confirmed | structural Compose tests and a scripted layout matrix now; golden images wait for the redesign; low-memory interruption covered by the kill script | T6.1, T6.2, T2.9 | | sections 9.5, 9.6 |
| A08 | confirmed | a Settings sheet behind a gear (reduced motion, shop-day speed, version). Audio and haptics and their toggles are deferred together (10.3) | T2.8a | | screenshot |
| A09 | confirmed | upgrade descriptions use descriptive wording for chances; amounts stay numeric | T5.3 | | `LaunchContentTest.noPlayerFacingTextContainsAPercentChance` |
| A10 | confirmed | the counter scene is the largest element of the shop day (180 dp at 360 dp width, never above 200 dp, and no other node on the screen is taller); the Forge destination gets the same backdrop at 120 dp or more; the 52 dp strip goes | T2.4, T2.5, T2.8c | | `ShopDayScreenTest.theSceneIsTheTallestNode` (a bounds assertion); screenshot check 9.5 |
| C01 | to apply | "saved atomically before the report" narrowed to normal dispatch until the session lands, then true for every operation | T0.3 (wording), T1.1 | | ledger table of review-9 items, line before and after |
| C02 | to apply | "daily paper from real records" stays; "complete and consistent" becomes true | T0.3, T1.4, T1.6 | | `DayEditionTest`, `ShopLedgerTest` |
| C03 | to apply | "resume after process death" split: world, unread day, then destination / draft / reveal, then the shop-day cursor | T0.3, T1.3, T2.3 | | `SavedStateTest`, `ProcessDeathTest` |
| C04 | to apply | "three dynamic champions" gains the warlord-context test | T0.3, T1.7 | | `ChampionSelectionTest` |
| C05 | partly applied (the line already says "in the simulator") | "no softlock" scoped to the recorded bots; adversarial recovery open until T5.2 | T0.3, T5.2 | T0.7 | stuck-day report |
| C06 | to apply | split into implemented semantics, recorded screens, and open device / a11y validation | T0.3, T6.1, T6.2 | | layout matrix, a11y tests |
| C07 | partly applied (the count is current: 186) | the test line says what was run and when, not coverage | T0.3 | | |
| C08 | to apply | the 5,000-day soak is named an arithmetic stress check; production-shaped bounds are their own open line | T0.3, T6.3b | | `ProductionSoakTest` |
| C09 | to apply | "every locked system has a UI surface" qualified until taste, loyalty, guild, mentor, weapon history and affix text are inspectable | T0.3, T2.7 | | `DetailSheetTest` |
| C10 | **done in 0.6.0** | none | none | | |
| C11 | already correct | keep | none | | |
| C12 | partly applied (one ticked line) | add a separate open line: "unsold stock, closed commissions, processed IDs and routine kept-forever records still grow; production-shaped soak pending" | T0.3, T6.3a, T6.3b | | |
| C13 | **done in 0.6.0** (the p95 line separates hardware, emulator and desktop) | none; the hardware number itself stays open | T6.6 | a device | |
| C14 | policy | PROPOSED GDD examples stay in the backlog list (10.3) | T0.3 | | |
| C15 | open | every pooled event and both generational rules observed in simulation | T5.2 | | `WorldEventReachabilityTest` |
| E01 | confirmed | `Compatibility.admit` validates content references, RNG streams and extended invariants on load without throwing; `Invariants` gains the missing checks | T1.5a, T1.5b | | `LoadValidationTest` |
| E02 | confirmed | resolver constants move into nested config groups; `CLAUDE.md` states what is true | T3.1 (customer ones), T5.4 (the rest) | | `ConstantsTest.noMagicNumbersInResolvers` (grep-style source test over an allowlist) |
| E03 | confirmed, low cost | engine on `Dispatchers.Default` inside the session; UI models built off the main thread; `siegeForecast` no longer runs per recomposition (X12) | T1.1, T6.5 | | frame-time capture in `LongSaveDeviceTest` |
| E04 | confirmed | JVM session tests, Compose tests on stateless screens, instrumented persistence and lifecycle tests | T1.1-T1.3, T2.5, T6.4 | T0.5 | section 9.2 |
| E05 | open | GitHub Actions: core tests, app unit tests, assemble, lint on every push | T0.2 | | a green run on the commit |
| E06 | open, partly blocked | rename runbook ready; R8 trial build; launcher icon candidate from existing art; signing and AAB stay P8 | T7.4 | owner: application ID | |
| E07 | holds | unchanged; a manifest test guards it | T0.2 | | `ManifestTest.noPermissionsAndNoNetworkLibraries` |

### 3.5 New requirements (N) and findings made during planning (X)

| ID | Status today | Intended behaviour | Task | Needs | Check |
|---|---|---|---|---|---|
| N01 | 4 seats (5 / 6 with the Signboard); 3.5 served a day | 6 seats (7 / 8 with the Signboard, +3 on a festival); mean 5-6 served a day, 6-7.5 for a built-up shop | T3.4 | T3.1, T0.4 | M3 band of 4.3 |
| N02 | 8 residents, refill below 5, cap 12 | 12 at the start with all five classes, refill toward 12, floor 9, event cap 16 | T3.4 | T3.1 | M3 band of 4.3 |
| N03 | 30 x 24 names, no avoidance | 120 x 96, authoring rules in `validate()`, no shared name among the living, no repeated full name in a run, lineage surnames reserved | T3.2 | T3.1 | `NameGenerationTest` |
| N04 | 25 faces by ID hash, nothing saved, 15 mis-sliced | a saved appearance key (asset ID string); 25 clean faces, 50 if the second set passes its check; assignment skips faces in use; old saves keep today's faces | T2.4, T3.3 | T2.5 gate | `AppearanceTest`; shared-face days per 4.3 |
| N05 | "a regular" in two Gazette phrases | at most one recognition line per visit, chosen in core from recorded facts, with cooldowns | T3.5 | T3.1, T2.5 | `RecognitionTest`; playtest Q1 |
| N06 | visits by reason code only | the metric set of 9.3 in the simulator, baselined before any rule changes | T0.4 | none | M0 gate re-issues the band of 4.3 |
| N07 | no asset-use table; the forge strip is 52 dp | section 5 | T2.4, T3.3 | none | contact sheet reviewed; device screenshots |
| N08 | none | five loops, staged (4.6) | T4.1-T4.6 | M3 | per-loop acceptance and the tripwire of 4.3 |
| N09 | n/a | section 1.3 | n/a | | |
| N10 | n/a | section 9.7 | T7.2 | M2 | |
| N11 | n/a | section 8; `docs/MAJOR_UPDATE_LEDGER.md` | T0.1 | none | ledger exists |
| X01 | confirmed: uncaught exception in `init`; `allowBackup="true"` | no crash on a bad save; `allowBackup="false"` until the recovery path and version policy exist | T1.2 | T1.1 | F02 tests; `ManifestTest` |
| X02 | confirmed: `Dialog(onDismissRequest = vm::dismissReport)`; no `BackHandler` | explicit back behaviour in every state; back never acknowledges a day | T1.3, T2.5 | T1.1 | Review Focus 5 |
| X03 | confirmed: `saveAtomically(null, ...)` at claim; `continueRun` unreachable | the ended run is kept after the claim; `claimed` is derived; the dead Continue path is removed | T1.1, T1.3 | T0.5 | `GameSessionTest.runEndSurvivesRecreationAfterClaim` |
| X04 | confirmed: `listedPrice x 1.5`, no ceiling | the collector pays at most `askingPrice x collectorPriceMultiplier` | T1.6 | T1.5b | Review Focus 4 |
| X05 | confirmed | tribute is `IncomeKind.TRIBUTE`; a `MATERIAL_BOUGHT` record and a cost on `TOOL_BOUGHT` | T1.6 | T1.5b | `ShopLedgerTest` |
| X06 | confirmed | = G09 | T4.5 | | |
| X07 | confirmed: three name joins in core, one in `Dialogs.kt` | `LineageAnchor.id`, `Hero.lineageId`; the siege stage joins by hero ID | T3.2 | T3.1 | `NameGenerationTest.descendantsAreMatchedById` |
| X08 | confirmed in the fixture | `@SerialName` pinned to the stored discriminator strings | T1.5a | none | `MigrationTest.weaponLocationDiscriminatorsArePinned` |
| X09 | confirmed: about one slot left | new numbers only in nested groups; a test constructs `BalanceConfig()` | T1.5a, every balance task | none | `BalanceConfigTest.constructs` |
| X10 | confirmed | separate migration tables for the run and the legacy document | T1.5a | none | `MigrationTest.aRunStepIsNotAppliedToTheLegacyDocument` |
| X11 | confirmed: `GameEngine.recover`, `Battle.outlook`, `Battle.warnOfSiege` | faction ties broken by ID everywhere | T1.9 | T1.5b | `DeterminismTest.factionTieIsBrokenById` |
| X12 | confirmed | = E03 | T6.5 | T2.8b | |
| X13 | confirmed | device scripts use resource IDs (`testTagsAsResourceId`) | T0.5, T2.9 | none | scripts pass after the navigation change |
| X14 | confirmed | reasons are recorded per considered blade; an empty-shelf visit does not cost a hero their place | T2.1, T3.1 | T1.6 | `ShopRecordTest.aRefusalNamesTheBladeThatWasTooDear` |
| X15 | confirmed on a device screenshot | portraits re-sliced on measured cells; the importer fails on a portrait with more than one vertical run | T2.4 | none | importer self-check, 25 of 25 |
| X16 | confirmed (C2PA manifests; no licence text) | documents stop calling the art "hand-made"; the owner decides the store wording and confirms usage terms before P8 | T0.3, T7.4 | owner | owner decision recorded |
| X17 | confirmed | the forge and the shelf say which element the besieger resists and fears; a resisted blade is refused with its own reason in the warning window | T4.2 | T4.1 | `SiegeDemandTest` |
| X18 | confirmed: `WorldEvents` adds, `GameEngine.newMorning` overwrites | the ore merchant's extra stock is on sale the morning it is announced for | T1.9 | T1.5b | `WorldEventsAndGenerationsTest.theOreMerchantsStockIsOnSaleTheNextMorning` |
| X19 | confirmed: threshold 50, damage 40, floor 0 | decided by measurement after the M3 band is met: a rout rule that makes wall deaths possible, or removal of the unreachable branch | T3.8 | T3.4 | `SiegeWallTest`; decision recorded |
| X20 | confirmed (`docs/PROGRESS.md`: not exercised on device) | constructed scenario saves per 0.6.0 mechanic, screenshotted through the shop day, with natural frequency reported beside them | T2.9, T0.4 | T2.6, T2.7 | scenario screenshots labelled "constructed" |
| X21 | confirmed (three tracks read 0.0) | bots that make the 0.6.0 tracks and Lucky measurable | T0.7 | T0.4 | every track has a non-zero yardstick under at least one bot |

---

## 4. Gameplay improvements, customer targets and economy experiments

All numbers below are PROPOSED starting values, reversible, and live in one new nested group `CustomerConfig`
(`BalanceConfig.customers`) unless another group is named. Setting them to the "today" column reproduces current
behaviour except for the selection order, which is the bug being fixed. They ship as balance **v6**, content **v3**.

### 4.1 Three levers, kept separate

| Lever | Today | Start value | Bounds in a run | What it buys |
|---|---|---|---|---|
| **1. Active population** | 8 at the start, refill only below 5, events up to 12; 8.2 alive on average | **12** at the start with one hero of each class guaranteed; a newcomer may arrive each day while below 12; guaranteed arrival below **9**; events up to **16** | 9-16, typically 11-13 | more purses, more needs, more faces: the only lever that raises sales without raising refusals |
| **2. Visitors served per day** | 4 seats (+1 / +2 Signboard, +2 festival); 3.56 served | **6** seats (+1 / +2 Signboard = 7 / 8; festival **+3**) | 4-8 served on an ordinary day, up to 11 on a festival | a visibly busier counter; the cap exists so the Signboard and a crowded day mean something |
| **3. Lifetime identity variety** | 720 full names, no avoidance, 25 faces by hash, 9-10 identities an era | 11,520 full names and 50 faces, no shared first name, surname or face among the living; 14-18 identities an era | n/a | recognition. Never a substitute for levers 1 and 2 |

Six seats, not eight, is the base: at twelve heroes about six are willing on an ordinary day, so a base of eight
would almost never bind and the Signboard would be dead again from the other side. Measured on the planning harness,
12 heroes at 6 seats serve 5.6 a day against 3.5 today (+59 %); with the Signboard's two seats, 6.8 (+92 %).

Population rules (`Heroes.generate`, `Heroes.arrivals`, `GameEngine.newRun`):

| Rule | Today | Proposed |
|---|---|---|
| `startingHeroes` | 8 | 12 (inside the GDD's PROPOSED 10-14) |
| `startingClassCoverage` | off: all five classes present in 32 % of runs | on: the first five starting heroes take one class each in a seeded order; a descendant keeps the lineage's class and counts toward coverage |
| `populationTarget`, `arrivalChancePerMissing`, `arrivalChanceMax` | none | 12, 0.15, 0.6: each End Day, if living heroes < target, one newcomer arrives with chance `min(0.6, 0.15 x missing)`; exactly one HEROES draw a day |
| `minHeroPopulation` (guaranteed arrival) | 5 | 9 |
| `maxHeroPopulation` (event arrivals) | 12 | 16 |
| Newcomer gear and purse | unarmed, class gold | unchanged: a newcomer is a customer because they arrive with nothing |

### 4.2 Fair seeded selection (F05). Lands first and alone

```
resolveShelfVisits(ctx):
    rng      = ctx.rng(RngStream.PURCHASES)
    heroes   = ctx.aliveHeroes()                 // IdOrder.numeric; the order only assigns draws
    capacity = cfg.shopCapacity + toolTotal(EXTRA_CUSTOMERS) + (festival ? cfg.festivalExtraSeats : 0)

    // 1. Intent: exactly one draw per living hero, every day, whoever ends up seated.
    willing = heroes.filter { rng.nextDouble() < willingness(it) }
    willing -= heroes who collected a commission today          // one appearance per hero per day (6.2)

    // 2a. Longest waiters first, never by ID. Among heroes at the waiting limit, take whole groups from the
    //     highest streak down; the group that does not fit is drawn by weight, one draw per seat.
    seated = []
    for group in willing.filter { it.turnedAwayStreak >= cfg.maxTurnedAwayDays }.groupBy { it.turnedAwayStreak }.descending():
        pool = group
        while seated.size < capacity && pool.isNotEmpty():
            pick = rng.pickWeighted(pool.map { it to seatWeight(it) }); seated += pick; pool -= pick

    // 2b. Everyone else, one weighted draw per seat, the first seats spread over classes.
    pool = willing - seated - (waiters left over)
    while seated.size < capacity && pool.isNotEmpty():
        candidates = if classesOf(seated).size < cfg.classSeats
                         pool.filter { it.classId !in classesOf(seated) }.ifEmpty { pool } else pool
        pick = rng.pickWeighted(candidates.map { it to seatWeight(it) }); seated += pick; pool -= pick

    turnedAway = willing - seated      // recorded on the day; their streak + 1; a served hero's streak resets to 0

    // 3. Serve in seating order (first seated has first pick of the stock). Evaluation unchanged:
    //    one nextDouble per listed weapon per served visitor.

willingness(h) = clamp(baseVisitChance 0.35 + traitShopWeight(h) * visitTraitScale 0.10
                       + min(h.loyalty, 10) * visitPerLoyalty 0.01 + min(reputation, 50) * visitPerReputation 0.005
                       + festivalVisitBonus 0.20 on a festival
                       + blessingMagnitude(HERO_VISIT_CHANCE) / 100      // kept exactly as today until T3.6 replaces it
                       , visitFloor 0.05, visitCeiling 0.90)
seatWeight(h)  = 1.0 + seatLoyaltyWeight 0.5 * min(h.loyalty, 10) / 10       // a regular is at most 1.5x a stranger
                     + seatNewcomerWeight 1.0 * (h.shopVisits == 0)
                     + seatWaitWeight 0.75 * h.turnedAwayStreak
                     + seatNeedWeight 0.5 * (unarmed or own blade worn)
                 then * seatBrowsedYesterday 0.5 if h was served yesterday and bought nothing
```

Contract (each line is a test in `CustomerSelectionTest`, T3.1):

- Stream PURCHASES only. Per day: N intent draws (N = living heroes), exactly one draw per seat filled (at most
  `capacity`), then the existing evaluation draws. Intent no longer depends on capacity, so two runs of one seed that
  differ only in seats see the same willing heroes until their shelves diverge, which makes the sweeps of 4.3 quieter.
- `Rng.pickWeighted` only (it uses `+ - *` and comparisons). No `pow` or `ln` key tricks, which could differ in the
  last bit between the desktop JVM and ART.
- `heroesDifferingOnlyInIdGetEqualTurns`: 10,000 capped days, chi-square p > 0.01; permuting serials does not change
  the distribution of served shares.
- `loyaltyRaisesAShareWithinItsBound`: loyalty 10 against 0, all else equal: served ratio between 1.2 and 1.6.
- `aNewcomerIsServedWithinTwoDaysInFourSeedsOfFive`: one newcomer among eleven veterans.
- **Waiting bound** (`noStreakExceedsTheWaitingBound`): if the willing heroes at the limit (`maxTurnedAwayDays` 2)
  number at most `capacity`, all of them are seated; otherwise the longest waiters are, and no hero's streak ever
  exceeds `maxTurnedAwayDays + ceil(waiting / capacity)`.
- **Saturation** (`saturationUnderPatronageKeepsServedShareMaxOverMinAtMostTwoAndAHalf`,
  `reputationFiftyAccountKeepsEqualTurns`): with every guild member willing at the ceiling, 16 heroes and 6 seats,
  and separately at reputation 50, served share max / min among equal heroes stays at most 2.5.
- `theFirstSeatsGoToDistinctClasses`: the first `classSeats` (3) ordinary seats, when the willing pool allows.
- **Empty shelves** (`twoEmptyShelfDaysDoNotCreateAnIdOrderedQueue`): a hero seated at an empty shelf is recorded
  as a visit, but `shopVisits` and `turnedAwayStreak` do not change: bad luck with stock neither spends newcomer
  priority nor builds a queue (X14).
- `turnedAway` is stored on the day and shown ("Three more found the shop full"): the player-visible reason to buy
  the Signboard and the simulator's measure of whether seats bind.
- **One ID order everywhere** (m13): a single comparator `IdOrder.numeric` replaces every string sort used as a
  tie-break: `aliveHeroes()`, `resolveCommissions` (today `c10` sorts before `c2`), `Commissions.pick`, the champion
  tie-break in `Battle.selectChampions`, `resolveMerchant`.

State added to `Hero` (all defaulted): `shopVisits`, `shopPurchases`, `lastServedDay`, `lastPurchaseDay`,
`turnedAwayStreak`, `arrivedOnDay`. The inline constants of today's formula (0.1, 0.01, 0.005, 0.05, 0.9) move into
`CustomerConfig` in the same change (E02), with caps on the loyalty and reputation terms that today grow until the
outer clamp.

### 4.3 Economy model and experiments

What more heroes and more seats do, from current numbers (arithmetic from the economy report, with the planning
harness beside it where measured):

| Quantity | Today | 12 heroes, 6 seats, uncompensated | Why |
|---|---|---|---|
| Hero gold at the start / income per day | 756 / about 107 | 1,134 / about 157 | 94.5 a head; about 13 gold per hero-day (expeditions, patrol pay, guard work) |
| Affordability | about 64 % can pay for a first iron blade | same per hero | purses per hero are held fixed, so the share of "too expensive" visits should hold |
| Sales per day | 1.0 (measured) | 1.46 (measured); ceiling about 2.1 from income | bound by purses |
| Supply | 5 Quick forges on 10 energy (7 with full overwork) | unchanged: 10 energy is LOCKED | bots forge 5 and sell 1; a careful player forges 2-3, so at 1.5-2 sales a day stock decisions start to matter without being short every day |
| Class coverage | all five classes in 32 % of runs | 100 % | every family has a buyer from day 1 |
| Champion strength | top 3 of 8 | top 3 of 12, each a little better armed | estimate +3 to +6 % town defense |
| Threat suppression | about 7.2 a day against growth 9 | about 10.6 a day | 3 per won expedition and 1 per patrol, linear in heroes: the main channel |
| Militia, armory, recovery | saturated at their caps | unchanged | do not scale with heroes |
| Run length, plain smith | median 20, mean 21.0 | median 25, mean 25.4-26.0 (measured) | too long for the first-era band |
| Legacy gain | | +0 to +1 point | one point per five days |

Compensating levers, in the order they are tried:

| Lever | Use | Why |
|---|---|---|
| `expeditionSuppression` 3 to 2 | first | the one channel that scales linearly with head count. Measured alone: mean 24.1 |
| `raidPerDay` 6.0 to 6.5 or 7.0 | second | measured alone at 7.0: 22.6; with suppression 2: **21.4, median 20**, i.e. today's survival |
| `populationTarget` 12 to 10 | third | smaller change, smaller payoff |
| Hero purses, expedition gold, patrol pay, `tradeInShare`, `fairGoldPerPower`, newcomer gear | **hold fixed** | lowering purses would rebuild the "too expensive" wall that v3 removed; the price language is what the player is learning |
| Siege strength scaled by head count | **reject** | a hidden rubber band; breaks "living faction pressure decides strength" |
| 10 energy, 5-day sieges, three champions | LOCKED | |

Sweep plan (each step changes one family of numbers; 1,000 seeds at base seeds 1, 10001 and 20001 unless noted):

| Step | Vary | Policies | Passes when |
|---|---|---|---|
| 0 | nothing: metrics and `--set key=value` overrides only (T0.4) | all | the instrumented simulator reproduces the 1,000-seed table of 2.3 exactly at seed 1; **the M0 gate then re-issues the two band tables below with measured baselines** |
| 1 | fair selection alone at 8 heroes, 4 seats (T3.1) | all, both maxed rows | no mean moves more than 0.6 days; served-share max / min at most 2.5; visits per day within 0.1 |
| 2 | starting heroes and target 8 / 10 / 12 / 14, uncompensated | FAIR, ACTIVE, SYNERGY, INVEST, CHEAP, EXPENSIVE, PASSIVE | read the slope per +2 heroes: days, sales, unique visitors, pressure at sieges |
| 3 | seats 4 / 5 / 6 / 8 at 8 and at 12 heroes, uncompensated | same | served and conversion per extra seat; turned-away share |
| 4 | suppression 3 / 2 x raid per day 6.0 / 6.5 / 7.0 at (12, 6) | same, both maxed rows, EXPERT | the M3 band below |
| 4b | transition: 200 balance-v5 states taken at day 8 (8 heroes) and continued under the step-4 numbers against the same states under v5 | FAIR, ACTIVE | survival delta recorded; if a continued run loses more than 2 mean days, admitted runs keep the v5 threat numbers until their next siege |
| 5 | Guild Patronage: stipend 0 / 20 / 30 / 40 and the "guests" variant; festival seats 2 / 3 (T3.6) | ACTIVE and FAIR with `--blessing patronage` against their default blessing | choosing Patronage moves mean days by at least +0.6 or sales per day by at least +0.3 under FAIR or ACTIVE, by no more than +2.0 days under any policy, and stays under 10 % of shop income |
| 6 | need terms off / on, each alone (T4.1); then the sidegrade gate, siege demand and two commissions, each alone (T4.2, T4.6) | FAIR, ACTIVE, ADVANCED_SMITH, REQUEST_DRIVEN, SIEGE_PREP, EXPERT | the M4 band below |
| 6b | the step-4 grid again | same as step 4 | run only if FAIR or ACTIVE moved by more than twice the noise floor during step 6 |
| 7 | frozen values (T5.5) | every policy, both maxed rows, per-upgrade impact, multi-era, shock bots | both bands hold at **10,000 seeds**; any change to rules or numbers after this step re-runs it |

**Thresholds are provisional until the M0 gate.** The survival and sales baselines below are the 10,000-seed v5
numbers of release 0.6.0. The customer baselines come from the simulator's 1,000-seed run where it reports them and
from the planning harness where it does not; the two use different stocking policies (conversion 23.1 % under
BALANCED_FAIR against 28.5 % on the harness), so harness numbers are marked and are replaced at M0, when T0.4 measures
every row with `--customers` for BALANCED_FAIR and BALANCED_ACTIVE and the harness is retired. Relative thresholds are
written against "the M0 value". Accept on the mean and on p10 / p90; report the median, which is quantised to the
five-day siege rhythm and flips on +0.3 mean days.

**Band checked at the M3 gate** (a larger, fairer town; demand rules unchanged):

| Measure | Threshold | Baseline today |
|---|---|---|
| BALANCED_FAIR days | median 20, p10 at least 15, p90 at most 30, mean 19.5-22.5 | 20 (15/25), 20.7 |
| BALANCED_ACTIVE days | median 25-30, mean at most 30.5 | 30 (20/35), 27.4 |
| BALANCED_EXPENSIVE, PASSIVE | median 10-15 and 10: pricing and forging still matter | 10, 10 |
| SYNERGY; all upgrades (FAIR, ACTIVE) | **reported as distributions, not capped.** Gates: every run ends, and a maxed account stays at least 10 mean days above a new one. Whether the maxed active median of 45 should come down is the owner's call, made from upgrade yardsticks after the customer changes, never an automatic nerf | 35 (25/40) 34.8; 35 (30/40) 36.5; 45 (35/50) 42.3, longest 55 |
| Served visitors per day, BALANCED_FAIR (no Signboard) | mean 5.0-6.0; days with two or fewer visitors at most 5 % | 3.5 (72.8 visits over 20.5 days); harness: 12 % of days |
| Served visitors per day, BALANCED_ACTIVE (Signboard) | mean 6.0-7.5 | 4.8 (130.5 over 27.4) |
| Unique heroes served | at least 9 by day 5 and 13 in a run | harness: 9.2 a run |
| Fairness | served share max / min at most 2.5 among heroes alive 10 days or more, also under Patronage and at reputation 50; the waiting bound of 4.2 holds | harness: position 8 served 2.4x less than positions 1-4, position 12 15x less |
| Newcomer wait | median at most 2 days, p90 at most 4 | harness: 1, 4 |
| Sales per day | 1.2-2.0 FAIR, 1.5-2.4 ACTIVE | 0.94; 1.15 |
| Conversion (bought / visits) | not more than 3 points below the M0 value, per policy: more seats for the same purses add refusals, and that is accepted at this gate | FAIR 23.1 %, ACTIVE 21.7 % |
| Refusal mix | reported per policy; no threshold at this gate | FAIR: NOT_BETTER 54 %, TOO_EXPENSIVE 21 %; ACTIVE: 51 %, 26 % |
| Buyers by class | at least 4 of 5 classes have bought by day 10 in 90 % of runs | not measured |
| Hero deaths | per hero-day at most 1.15x the M0 value (twelve heroes are 1.5x the exposure of eight, so the per-run count is not the measure) | 0.8 a run over about 170 hero-days |
| Legacy points, median | within 2 of baseline | 26 FAIR |
| Hard-lock days; stuck days | 0; at most 0.5 % of days | 0; not measured |
| Shared first name among the living | 0 % of days | harness: 67 % |
| Shared surname among the living | 0 % of days, except members of one lineage | harness: 74 % |
| Shared face among the living | 0 % of days while a class has no more living members than it has faces (5 a class, 10 if the second set passes); otherwise reported | harness: 52 % |

**Band added at the M4 gate** (wants, sidegrades, siege demand, commission situations):

| Measure | Threshold |
|---|---|
| Conversion | at least the M3 value + 2 points under BALANCED_FAIR and under the want-answering REQUEST_DRIVEN bot |
| NOT_BETTER share | at least 8 points below the M3 value under BALANCED_FAIR |
| TOO_EXPENSIVE share | not above the M3 value + 2 points |
| New reasons | each code added in M4 (TASTE_MATCH, PRIZED, STORIED, COUNTERS_THREAT, RESISTED) occurs in at least 1 % of visits under one bot and at most 15 % under any |
| First-era survival | the FAIR and ACTIVE rows of the M3 band still hold |
| Per-loop bounds | SIEGE_PREP at most +6 mean days over FAIR; REQUEST_DRIVEN answering wants at most +5 over FAIR; two open commissions at most +2 over the same bot with one |

**Tripwire against stacked easing (not a nerf rule).** Twelve residents, wants, siege demand, sidegrades, a second
commission, a guild stipend and a festival seat all push the same way, on top of the Signboard and Known Name that
already stack. An EXPERT bot (SYNERGY forging, counter-element stock in the warning window, answers wants and
commissions, takes Patronage when offered) is reported for a new account and a maxed one at every gate from M3. Work
stops and the owner is asked before any further easing lands when: the new-account EXPERT mean exceeds the v5 SYNERGY
mean (34.8) by more than 8 days; the maxed EXPERT p90 exceeds 60 days; or any run reaches 100 days or the simulator's
day cap. What to do then is the owner's decision, with the per-lever arms of steps 4-6 as evidence.

Weapon wear is **held fixed**: it is not a lever of this update, and neither it nor maxed-account survival is tuned to
hit a median. Balance is reassessed after the customer and economy changes land, on distributions and on each
upgrade's own yardstick.

Noise: three 1,000-seed runs of BALANCED_ACTIVE gave means 27.5 / 27.4 / 27.7 (`docs/DECISIONS.md`, "Noise floor"),
so a delta in survival is real only above about 0.3 mean days, 0.3 sales, 0.05 deaths. That floor was measured with
eight heroes; step 0 re-measures it, because 50 % more heroes changes the variance. For proportions (conversion,
refusal shares, served shares) the three base seeds give the floor directly: a share counts as moved only when all
three seeds move the same way by more than the largest gap between them at baseline (visits inside a run are not
independent, so a binomial standard error would understate it).

### 4.4 Names (N03)

| | Today | Proposed |
|---|---|---|
| First names x surnames | 30 x 24 = 720 | **120 x 96 = 11,520** |
| Shared first name among 12 living | 92 % of days | 0 % (avoidance) |
| Identical full names among 200 heroes over ten eras, expected pairs | 27.6 | 1.7, and 0 inside an era |

Authoring rules, all checked by `ContentCatalog.validate()` (names are content, hence content v3):

1. Plain ASCII letters, one capital, no apostrophes, hyphens or diacritics. First names 3-8 letters, surnames 4-11; a full name is at most 20 characters so it fits a Town row and a counter caption at large font.
2. Pronounceable at sight, in the register of the existing list (Mira, Halvard, Elspeth, Faolan).
3. Distinct at a glance: no two first names share their first three letters; any two differ by at least two edits (three if they share an initial); at most eight first names per initial, at least fifteen initials used. Surnames: no two share their first four letters; at least two edits apart.
4. No first name shares its first four letters with a surname (no "Rook Rooksbane").
5. Surnames in four kinds, mixed evenly: nature compounds, places, trades, old family names; at most four per ending.
6. No game terms or stems (materials, elements, affixes, families, classes, factions) and nothing that reads as a weapon title. The existing `Ashwood`, `Holloway`, `Mossgrave` and `Rooksbane` leave the pool (stored heroes and lineages keep them); `Nessa` (one letter from `Tessa`) is replaced.
7. One shared pool: no name sub-pools tied to a look.

Samples in the intended style. First names: Anwen, Alba, Brisa, Bertram, Cael, Ceridwen, Dorrin, Davin, Evander,
Elowen, Fenna, Fintan, Gideon, Hedda, Imre, Joss, Keir, Leof, Maud, Niall, Ottilie, Petra, Rowan, Runa, Saskia,
Tobin, Ulla, Varek, Willa, Yorick. Surnames: Aldermoor, Applegarth, Barrowby, Birchall, Cobbett, Crowhurst,
Dovecote, Dray, Eastmere, Fenwick, Foxglove, Gorse, Harrowgate, Ingram, Juniper, Kettleby, Larkspur, Millrace,
Norwood, Orchard, Penhallow, Ravensworth, Sedgewick, Stroud, Tanner, Umberlow, Varley, Wainwright, Whitlock, Yewdale.

Generation, in the one place that creates every hero (`Heroes.generate`), on the stream the caller already passes and
with **the same draw count as today** (one first-name draw always; one surname draw unless the hero is a descendant,
whose surname is forced); only the lists they pick from change:

```
firstPool   = content.firstNames - first names of living heroes - first names used in this run (any fate)
              - the ancestor's first name (descendants)
surnamePool = content.surnames - surnames of living heroes - surnames of this run's guild founders
              - surnames held by a legacy lineage, unless this hero is that lineage's descendant
name = rng.pick(firstPool); surname = descendantOf?.surname ?: rng.pick(surnamePool)
fallback when a pool is empty: the draw is still consumed and picks among the names of the heroes dead longest;
a full name never repeats inside a run
```

Identity never rests on a name: `LineageAnchor` gains `id` (era + hero ID; empty on old profiles) and `Hero` gains
`lineageId`; the three places that match a descendant by name string (`GameEngine.newRun`, two in `WorldEvents`) and
the siege stage's name join move to IDs (X07). Mentees do not take the mentor's surname; kinship is `lineageId`.

Two consequences stated plainly. Avoidance among the living is the hard rule; "not used earlier in this run" is
best effort: a forced-survival run of 1,000 days meets about 115 heroes, so past roughly 120 the fallback is the
normal path and the test covers it. The four existing names that break the new rules (`Ashwood`, `Holloway`,
`Mossgrave`, `Rooksbane`) leave the pool for new heroes; heroes and lineages already carrying them keep them,
because a stored name needs no pool entry.

### 4.5 Regulars and recognition (N05)

One recognition line at most per visit, chosen in core at End Day and stored on the visit, so the counter, the
Gazette and a relaunch show the same line. The choice among eligible templates is a stateless hash of run seed, hero
serial and day: no gameplay stream is touched.

- Milestone lines always show, once per hero per run: first visit, first purchase, became a regular, a descendant's first visit, a champion back from the wall with your blade, an ambition fulfilled with your blade.
- Recurring lines need `shopVisits >= 2`, have a three-day cooldown per hero and never repeat the same template twice running for that hero.
- Frequency targets: days 1-3 at most 20 % of visits carry a line (introductions instead: class and what they look for); from day 6 on 35-50 %; never more than three in the featured visits of one day.

Templates are narration, never invented speech; every clause is a field:

| # | Line | State that justifies it |
|---|---|---|
| 1 | "{hero}'s first time at your counter." | `shopVisits == 0` |
| 2 | "{hero} leaves with {weapon}: a first blade from your forge." | this visit bought; `shopPurchases == 0` before it |
| 3 | "That makes {n} from your forge. {hero} is a regular now." | loyalty crosses `regularLoyaltyThreshold` on this purchase |
| 4 | "{hero}, a regular, last in on day {d}." | `Market.isRegular`; `lastServedDay` |
| 5 | "{hero} still carries {weapon}: {k} victories with it." | equipped weapon bought here; `weapon.victories >= 1` |
| 6 | "{hero} lays {weapon} on the counter. The edge is {worn / battered}." | equipped `condition < wornConditionThreshold` |
| 7 | "{hero} held the wall on day {d} with {weapon}." | weapon history `SIEGE` with this hero |
| 8 | "{hero} brought down {foe} with {weapon}. They call it '{title}' now." | `elitesSlain >= 1`; `weapon.title` |
| 9 | "{hero} kept the vow, {n} foes routed, and came back for more steel." | `ambition == SLAYER && ambitionDone` |
| 10 | "{hero} trained under {mentor} and carries {mentor}'s old {weapon}." | `mentorName`; history `INHERITED` |
| 11 | "{hero}, of the line of {ancestor}, who {deed}." | `lineageId`; `LineageAnchor.deed` |
| 12 | "{hero} could not get in yesterday and is first through the door." | `turnedAwayStreak >= 1` and seated first |

Later loops add: "asked for a {family} two days ago; today there is one" (`Hero.want`), "sent by {guild}; the guild
paid 30" (Guild Patronage), "picks up {weapon}, once {previousOwner}'s" (history `TRADED_IN` or `RECOVERED`).

### 4.6 New gameplay loops (N08): five, each inside the existing day

None adds a navigation destination, a timer, a combat command, manual equipping or generated text.

| | Loop | Trigger | Player decision | World response | Payoff | Varies by | Cost | Balance risk | Acceptance | Stage |
|---|---|---|---|---|---|---|---|---|---|---|
| E1 | **Standing wants** | a served hero leaves without buying; the engine records what would have sold: `Hero.want = (family, minimum gain, budget = purse + trade-in, sinceDay)`, no RNG | forge the bow Wren asked for and price it inside her budget, or serve someone richer | while a listed blade answers the want the hero is +0.30 willing and weighs more for a seat; a want lapses after 3 days or on any purchase | the hero returns and buys; recognition line; loyalty | 5 classes x 6 families x purse x current gear | M | medium: conversion rises; bounded by purses | REQUEST_DRIVEN, extended in T4.1 to read wants, satisfies at least 60 % of them within 3 days (BALANCED_FAIR at most 25 %) and stays at most +5 mean days over FAIR; playtest Q6 of 9.7 (name one hero and what they want by day 3) passes for 4 of 5 | M4, T4.1 |
| E2 | **The town arms for the siege** | the existing siege warning two days ahead, which already names the faction and its weakness | spend scarce Frost Bloom or Sun Ash on counter blades, or sell ordinary stock; the forge and shelf mark matching and resisted elements (X17) | in the warning window heroes value a counter-element blade (`threatUtility` +0.8) and current champions are +0.15 willing; a resisted element is refused with `RESISTED` | champions strike with counter blades in the siege replay; less forge damage; the blessing choice | 3 factions, 2 weaknesses, 2 resistances, warlords | S-M | medium-high: it teaches what the SYNERGY bot does | a SIEGE_PREP bot lands between FAIR and SYNERGY and at most +6 mean days over FAIR; `RESISTED` is 3-10 % of refusals in warning windows | M4, T4.2 |
| E3 | **Rumours and the clue ladder** | real events only: a hero slays an elite with one of your blades; a commission is completed; the fragment, notes and master-smith events. Each grants one rung on one undiscovered signature (EVENTS stream, as the fragment event does) | which rumour to chase: buy the catalyst, spend 4 energy on an Advanced forge, accept the risk | the journal entry gains a rung: base recipe, what kind of catalyst, the temper, how fine | a permanent discovery, a named blade heroes want, a line in the paper, a recallable recipe | 24 signatures, 4 catalysts, 3 tempers; rumours persist across eras | M | low | SIGNATURE_PURSUIT makes at least one unique first discovery by the end of era 2 in 70 % of accounts; no signature is first found without rung 1; 2-4 rumours a run | M4, T4.3 |
| E4 | **Commission situations** | the daily commission roll, with the kind taken from state: REPLACEMENT (the patron's blade broke, was lost or is worn), SIEGE_PREP (a current champion, siege within 4 days), AMBITION (an unfulfilled COLLECTOR), FIRST_BLADE (a guild founder or mentor orders for a named unarmed newcomer). Up to two open at once | accept or decline; which blade to hand over; whether to hold stock back | reward, loyalty, reputation as today, and a consequence the player can see | a transaction with a reason and an afterlife | 6 kinds x family x element x band x deadline | M | low-medium: perhaps 300-400 more commission gold a run | REQUEST_DRIVEN completes at least 70 % of accepted commissions; no kind exceeds 40 % of offers; a SIEGE_PREP blade is wielded at its siege in 60 % of completions; two open commissions add at most +2 mean days over the same bot with one | M4, T4.6, last; REPLACEMENT and SIEGE_PREP first |
| E5 | **Maker's ledger** | a blade of yours earns a history line that matters: first victory, a title, a siege, a change of hands, a return | when a storied blade comes back (trade-in, recovered, returned legend): resell at a premium, re-hone, arm the watch or melt it; all four exist, the item sheet makes the choice visible | the buyer becomes the next line of the blade's story; a legend recorded at run end can return next era as what it was | following one blade across a sale, several fights and an ownership change | earned titles, fates, eras | S in core (G09), the rest is the item sheet | none | every ledger line maps to a stored history entry; a returned legend keeps its signature name only if it keeps the signature | M2 (item sheet, T2.7) and M4 (fidelity, T4.5) |

Why these five: E1 turns the dominant refusal ("not better", half of all visits) into a request the player can
answer; E2 makes the predictable siege a two-day stocking decision and exposes the hidden penalty on the cheapest
recipe; E3 honours a LOCKED sentence the game does not yet implement ("clues from heroes, merchants and events",
GDD 4.6); E5 is the GDD's own final product test. E4 is the least essential and is staged last.

Rejected, with reasons: haggling (a decision inside a committed day; needs a new phase); heroes bringing blades in
for repair (needs a weapon owned by a hero and held by the shop: a new `WeaponLocation`, invariants and a migration;
revisit later); "recommend this blade to this hero" (one step from equipping heroes, LOCKED out); apprentices or
stock that sells while away (idle production, LOCKED out); passing travellers who buy and leave town (more sales, but
the blade leaves the simulation, which dilutes the one fantasy the GDD names); rival smiths, auctions, rentals (large
systems with nothing in state to build on); daily fads not tied to state (noise); reputation tiers that unlock
features (a new screen in practice); a second relationship meter (loyalty exists and is underused).

### 4.7 Policies for the fixes

**Commissions (F04, F06).** One definition of the quality bands in core (`QualityBand`, from the four thresholds
`BalanceConfig` already holds for rarity); `Labels.quality` reads it. A commission's `minQuality` is always a band
floor: standard 35 "decent" (60 %) or 50 "fine" (40 %), noble 70 "superb". The COLLECTOR ambition moves from 60 to 50
so "wants a fine blade" is true. `Commissions.fit(weapon, commission): Fit` (`OK`, `FAMILY`, `ELEMENT`, `QUALITY`)
is the only rule, used by `resolveCommissions` and by the UI. Accepted commissions resolve before shelf visits; the
blade handed over is the least sufficient eligible one (lowest quality, then lowest asking price, then ID), storage
before shelf; the card says before End Day "Ready: Frostbound Bronze Spear will be handed over" or names the one
missing criterion. An explicit `ReserveForCommission` command is not built unless playtests ask for it. Old saves:
an open commission with an off-band `minQuality` is lowered to its band floor on load, in the player's favour.

**Sidegrade gate (G01).** `gain` stays a Double (today both sides are truncated to Int before subtracting, so a 0.9
gain reads as none), and the value compared on both sides becomes what the blade is worth in the hand:
`power x conditionFactor x classFit x affixAttackMultiplier x fameFactor` (both multipliers exist in `Power.kt` and are
bounded), times the faction matchup only while a siege warning is active (E2). Today's comparison ignores affixes
and fame, which the review flags. A candidate is eligible when affordable and either `gain > 0`, or `gain >= -0.05 x value(current)`
with a one-way side reason: TASTE (candidate matches the hero's element taste and the current blade does not),
PRIZED (an unfulfilled COLLECTOR and the candidate reaches the band the current one lacks), STORIED (candidate fame
at or above `legendFameThreshold`, current below). Once a hero holds a blade with the property the reason cannot fire
again, so there is no churn. Bought codes gain `TASTE_MATCH`, `PRIZED`, `STORIED`, `COUNTERS_THREAT`. Acceptance: 1-4
sidegrade purchases a run, no hero more than three, champion defense at the first two sieges not more than 2 % below
baseline.

**Signboard and Guild Patronage (G03).** Signboard: seats (as merged). Guild Patronage: for its five days every
guild member is willing at the ceiling and the guild pays `patronageStipend` 30 gold toward one purchase per member
(added to affordability, paid to the shop, recorded on the `Sale` as its own amount and shown on the receipt). Not
offered while the town has no guild. Fallback arm if minting stipend gold is unwanted: up to two willing guild members
a day are seated outside capacity. Sweep both, ship one.

**Weak upgrades (G04).** No magnitudes change before balance v6 is measured. At 10,000 seeds (0.6.0) Known Name
reads +1.6 mean days since its rework, Tireless Smith +0.4, Family Savings +0.2, and Caravan Ties, Anvil Lore and
Homing Steel 0.0 because no bot uses what they give; more seats and more residents will move all of these again.
The maxed active account at median 45 (35/50) is inside the band the legacy tracks were asked to keep and is not
lowered by this plan.
Gate per track at max level: at least +1.5 mean days under one competent policy, **or** at least +15 % on its own
purpose metric (Tireless Smith: shop actions a day in days 1-10 and Advanced forges a run; Family Savings: tools owned
by day 5 and the day of the first tier-3+ sale; Lucky Hammer: exceptional share; Known Name: sales and unique buyers
by day 5; Thrifty Hands: rare-augment forges a run; for every track also the review's two discovery metrics: days to
the first forge of a chosen recipe and experiments completed by day 10). Rework only what fails both. Whatever the numbers, the run-end
screen states the concrete next-run change of each purchase, and no description shows a chance as a percentage (A09).

**Catalysts and clues (G07).** Catalyst identity is delivered through discovery, which is the review's own second
branch ("or explicitly treat their differentiation as signature-related"): each catalyst has its own authored clue
phrase, and the forge states honestly what a catalyst does today ("steadies the forge; some recipes ask for one
by name"). Giving the four catalysts four different general-purpose effects is **deferred** (10.3): two of the
obvious jobs duplicate techniques (an affix slot is ETCH, fewer defects is TEMPER), catalysts are also signature
conditions so every signature blade's numbers would move, and it would need four more bots. Clue ladder per signature, stored as a bit set in the legacy journal and merged across eras:
rung 1 base recipe ("hides something more"), rung 2 one authored phrase per catalyst ("wants something to bind it",
"wants a word cut into it", "wants a hotter fire", "wants a rule rewritten", or "wants nothing added"), rung 3 the
temper, rung 4 "finer work: at least {band}"; found = name, flavour, the full recipe and "Use this recipe".
`Journal.recordSignatureClue` keeps answering after the first miss, and the hint shows only rungs earned. No odds at
any rung.

**Recovery (G10).** Three states measured separately: resting (could act, chose not to), drought (no possible sale
today but a useful action exists), stuck (no legal forge, no listed blade any living hero could both afford and gain
from, no deliverable commission). Shock scenarios instead of more competent bots: SPENDTHRIFT, NOVICE, BROKE_START.
A true stuck state is reachable by arithmetic today (gold under 8, no augment, everyone better armed than the shelf).
No valve before the scenarios run; if stuck streaks of three days or more appear in more than 1 % of NOVICE runs, add
the smallest one: Salvage also returns the augment of an unworn, never-sold blade. No gold is minted.

### 4.8 Defaults chosen (reversible, recorded in `docs/DECISIONS.md` when they land)

| Decision | Default | Alternative kept in the sweep or the backlog |
|---|---|---|
| Base seats / residents | 6 / 12 | 5 / 10 (safer, less visible); 8 / 14 (rejected: the Signboard dies and the first-era band breaks) |
| Guild Patronage | stipend 30 | guests outside capacity |
| COLLECTOR threshold | 50 (fine) | 70 (superb) |
| Open commissions | 2, from T4.6 | 1 (today) |
| Catalysts | each changes general forging | identity through clues only |
| `RULES_VERSION` | 2, once, for the whole update | n/a |
| Featured visits at the counter | 3, the rest in a tally with faces | 4 if playtests ask for more and the day stays under 30 s |
| Cursor storage | its own row in the save database | `GameState.unacknowledgedDay` with an `AcknowledgeDay` command (6.4) |
| Fourth destination label | "Records" | any word of six letters or fewer |
| "Replay the day" from the Shop | included (free once the script exists) | drop if it confuses playtesters |
| `allowBackup` | false until release hardening | true with tested backup rules at P8 |
| Audio and haptics | deferred with their toggles | n/a |
| How a customer is drawn at the counter | decided at the T2.4 / T2.5 gate by the three-step rule of 5.3; preferred: cut-out busts behind the counter | framed portrait tiles for everyone; or the 25 re-sliced busts only |
| The starter pack's own 16 px portraits (five "ancestries" a class, `hero_variants.csv`) and 12 signature sprites | not used: a pack pixel is four times coarser than the painted art and they cannot share a scene (5.2) | owner's arm: pack portraits as 3x markers in the tally and the Town list, where 16 px art already lives |
| Shop-day speed default | "Tap" (nothing auto-advances); 1x and 2x are opt-in | 1x by default if playtesters ask |
| Version numbers | each slice that changes draw order or End Day order bumps `RULES_VERSION`; each slice whose save an older build must refuse bumps `SCHEMA_VERSION` (6.7) | one bump for the whole update with no build leaving the machine before M5 |
| Catalyst identity | through clue phrases and honest text | four mechanical jobs (deferred, 10.3) |
| Wall deaths (X19) | measured after the M3 band is met; adopt the rout rule only inside the band, else remove the unreachable branch | remove now |

---

## 5. Asset inventory (inspected visually) and scene integration

Every source below was opened and looked at, the large boards as measured crops; the key contact sheets are in
`docs/major_update_evidence/crops/`. Nothing was imported: the importer was not run.

### 5.1 Sources

| # | Source under `Pixel art assets/` | Native | What it is | Imported today |
|---|---|---|---|---|
| 1 | `ChatGPT Image ... -1.png` to `-5.png` | 1448x1086 (sheet 3: 1254x1254), RGBA | five concept sheets: forge pieces; weapons, element tiles, rarity gems; 25 portraits; factions; materials, blessings, panels, icons | 94 slices (12 + 6 + 25 + 12 + 39) |
| 2 | `Weapons master` (PNG without extension) | 1672x941 RGB | 6 families x 7 element rows x 8 levels | 336 sprites at 56x56 and the generated `ui/WeaponArt.kt` |
| 3 | `New folder/drawable-nodpi/` ("V2 production starter pack") | 200 true 1x sprites | script-drawn 16 px set: fixtures, hero and monster frames, weapons, 12 signatures, 25 portraits, icons | 58: hero and monster frames, siege wall, milestone burst, markers |
| 3b | `New folder/concept_references/approved_hybrid_direction.png` | 1448x1086 RGB | "Style F" board: a painted forge panorama, 5 portraits, weapons, monsters | **no** |
| 3c | `New folder/concept_references/v2_concept_reference.png` | 1212x1298 RGBA | a painted forge scene, **30 class portraits, 8 townsfolk tiles**, tiles, objects | **no** |
| 4 | `Tiny_Blacksmith_UI_Backgrounds_v3/` | 51 true 1x sprites | flat 96x48 backdrops, two 270x150 title and run-end pieces, nine-slice frames, buttons, slots, tiles, dividers, icons | **no** (whole pack skipped) |
| 5 | `Tiny Blacksmith RPG Asset Atlas.png` (added by the owner during planning) | 1536x1024 RGB | a labelled concept atlas: seven scenes, weather and map thumbnails, tile sets, props, town NPC chibis, UI panels with baked text, slots, HUD, toasts, a dialogue box, menu icons | **no** |

`app/src/main/res/drawable-nodpi/` holds 493 PNGs (488 from the importer, 5 generated placeholders); 477 are
referenced from Kotlin, 16 are not.

How "pixel" each source is (measured): the atlas has 525,973 distinct colours and no grid; the concept sheets and the
live 64 px portraits are painted and resampled (a 64 px portrait holds about 2,400 colours); the pack and the V3 set
are true pixel art with 9-26 colours. Consequences: the three families cannot share one scene (5.3), and a classic
palette swap is impossible on the painted art (5.4).

### 5.2 Asset-use table

Status: USED (present and drawn), UNUSED-REUSABLE (in `res` or the folders, not drawn), ADAPTABLE (needs an import
step), REFERENCE (cannot ship as it is), MISSING.

| Asset (ID or path) | Intended scene or component | Integration today | Work in this update | Gaps, verification | Status |
|---|---|---|---|---|---|
| `portrait_<class>_0..4` (25, 64x64, sheet 3) | customer bust at the counter; Town rows; hero sheet | `Sprites.portrait` by ID hash; 56 dp and 44 dp in `InfoPanels.kt` | re-slice on measured cells (T2.4); record the content box; select by saved key (T3.3) | **15 of 25 carry a strip of the neighbouring portrait or a cut top, visible on device today** (X15); check the re-slice at 44, 56 and 85 dp | USED, defective |
| `v2_concept_reference.png` class tiles, columns 1-5 (25, 86x84) | second face set, five per class | none | new flat-source entry, boxes in `v2ref_portrait_cells.json`, fitted to 64x64, IDs `portrait_v2_<class>_<1..5>` (T3.3) | darker painterly tiles with their own background: draw every portrait on one shared dark tile and confirm on device that both sets read as one family before committing | ADAPTABLE |
| same file, column 0 of each class (5) | n/a | none | none | class label baked across the top | REFERENCE |
| same file, townsfolk tiles (5 clean: townswoman, hatted dwarf, elder woman, bearded man, goggles kid) | the collector and other non-hero visitors | none | 5 boxes, IDs `portrait_npc_<name>` (T3.3) | three more tiles are animals or carry an artefact | ADAPTABLE (5) |
| atlas dialogue portrait "Master Doran" (53x64 at `(1247,776,1300,840)`) | the smith on the title or run-end screen; not needed by the counter | none | optional crop | more realistic rendering than the hero busts; one pose | ADAPTABLE, optional |
| atlas NPC chibis (7, about 35x51) and prop figures | walk-on figures | none | none | soft, small, only three read as a class; keying eats dark clothing | REFERENCE |
| pack `portrait_*` (25, 16x16; five "ancestry" variants a class, listed in `New folder/hero_variants.csv`: human, dwarf, elf, orc, dragonkin, goblin, satyr, lizardfolk, halfling, tiefling, feline, undead, dryad, beastkin) | n/a at the counter | not imported (`portrait_` is not in `PACK_PREFIXES`) | none by default; the ancestry labels are art variety only and no rule may read them | a pack pixel is four times larger at equal size, so they cannot share a scene with the 64 px set; the owner's alternative arm is in 4.8 | REFERENCE for this update |
| `New folder/hero_variants.csv`, `asset_inventory.csv`, `manifest.json` | the pack's own index: IDs, sizes, anchors, frame durations, ancestry per portrait | the importer copies anchors into `overrides.json`; no runtime code reads them; durations (120 / 180 / 100 ms) differ from the hard-coded runtime values | none; if pack portraits are ever adopted, `hero_variants.csv` is their key table | documentation of the pack, not a mapping the game uses | REFERENCE |
| `New folder/palette.gpl` (48 named colours) | colours for everything Compose draws inside the scene: counter planks `wood2` #8C6239 and `wood1` #6B4B32, slot and name-plate ground `ink` #1A1210 and `deep` #2B2320, price tags `cream` #FFF0A0, the regular pip ring `gold` #D8A030 | not referenced | add the six swatches as named constants in `app/ui/theme/Color.kt` (T2.5) | the painted art is not limited to this palette; these are for drawn elements only | ADAPTABLE |
| `New folder/tools/build_assets.py`, `Tiny_Blacksmith_UI_Backgrounds_v3/build_ui.py` | the regeneration route for the 58 pack sprites in use and for the two V3 pieces adopted | not run by the project (the V3 script writes to `/mnt/data`) | none; recorded in `docs/ART_BRIEF.md` as the source of those sprites | | REFERENCE |
| `New folder/previews/*.png`, V3 `previews/*.png` | evidence | not imported | none | contact sheets only | REFERENCE |
| `portrait_<class>.png` placeholders in `res` (5) | n/a | unreferenced | stop generating; pruned on the next import (T2.4) | dead resources | UNUSED |
| `hero_<class>_{idle,attack}_{0,1}`, `monster_*`, `siege_wall*`, `fx_milestone_*`, `marker_*` (pack) | siege diorama, result burst, Town markers | used (`Sprites.kt`, `Dialogs.kt`, `InfoPanels.kt`) | none | bust-like 16 px actors: stay confined to the diorama, which is internally consistent | USED |
| `weapon_<family>_<row>_<level>` (336, 56x56) | stock on the shelf, gear, forge preview, result card, item sheet | `WeaponArt.kt`, `Sprites.weapon` | none for the scene; draw at integer factors on dark slots | outlines are partly transparent (colour-distance keying), so they thin on light surfaces | USED |
| `badge_{common..legendary}`; `badge_flaw` | rarity pip; flaw pip | rarity used; `badge_flaw` unreferenced | map `badge_flaw` from `Weapon.flaws` in the shelf slot and item sheet (T2.7) | check the flaw shape at 16 dp | USED; UNUSED-REUSABLE |
| pack `weapon_sig_*` (12, 16x16) | signature look | not imported, never mapped | none | 16 px against 56 px weapons; covers 12 of 24 recipes (all swords, axes, bows; no spear, dagger, staff) | REFERENCE |
| signature sprites at 56 px (24) | recognisable legends | none | stopgap in `Sprites.weaponLevel`: a signature draws two levels higher on the master sheet (clamped), keeps the milestone burst and gains a Compose-drawn ring (T2.7) | genuine artist gap | MISSING |
| `material_*` (16), `blessing_*` (8) | supplies, pickers, blessing choice | used | none | | USED |
| `tile_wall`, `tile_floor`, `furnace_*`, `anvil`, `tool_rack`, `shelf`, `ember_0..3` (sheet 1) | today's 52 dp forge strip and the title | `ForgeScene` in `Sprites.kt` | keep for the title; **do not build the counter on it** | the wall tile is not seamless (every tile repeats the same ivy and beam with dark seams), and the concept shelf is already dressed, so there is nowhere to stand stock; given 128-192 dp it reads as wallpaper | USED, not for the counter |
| `approved_hybrid_direction.png` crop `(262,135,802,405)`, 540x270 | **counter and shop-day backdrop; Forge destination header** | none | flat-source entry, opaque 1:1 copy, ID `bg_counter_forge` (T2.4) | furnace, banners, tool wall, anvil, window; caption and logo are outside the box; verify crop edges on device; no heat states, no night | ADAPTABLE, recommended |
| same board, wide strip `(240,140,1225,405)`, 985x265 | title or store banner base | none | optional | | ADAPTABLE, optional |
| `v2_concept_reference.png` forge `(47,79,587,290)`, 540x211 | alternative backdrop with a long workbench | none | fallback if the recommended crop fails on device | | ADAPTABLE |
| atlas forge day `(8,103,264,260)` 256x157; forge night `(270,49,502,260)` 232x211 | lower-resolution backdrop; the only night interior | none | optional `bg_forge_night` for the Tomorrow card | usable at 4x; half the detail of the recommended crop | ADAPTABLE |
| atlas town square `(516,49,682,259)`, market street `(696,49,877,259)`, castle / guild `(890,49,1076,259)`, wilderness, dungeon | headers for Town, the aftermath cards and the Gazette | none | adopt at most two in this update: `bg_town_square` (Town header) and `bg_wilderness` (aftermath), if they pass a device look (T2.4) | 166-210 px wide, so 5-6x on a phone and soft; the last three boxes are measured to about 2 px | ADAPTABLE |
| atlas weather, season and map thumbnails; tile sets; props | n/a | none | none | about 71x91 with labels beneath; tiles are not on a grid; props are small 3/4 views with baked contents | REFERENCE |
| atlas UI panels, HUD, five toasts, dialogue text panel, inventory grid | n/a | none | none | baked text ("Blacksmith", "Enhance Weapon?", "Forge Complete!", "Master Doran", "12,450"): all text must be native | REFERENCE |
| atlas menu icons: home `(1235,897,1272,935)`, purse `(1400,897,1437,935)`, gear `(1483,942,1520,981)` | Shop destination, trade-in, Settings | none | atlas entry, square crop with its own frame, fitted to 40x40 (T2.4) | same framed look as the sheet-5 icons at a quarter of the resolution; boxes good to about 2 px | ADAPTABLE |
| `icon_nav_*`, `icon_{day,gold,energy,integrity}` | navigation, status | used | re-map to four destinations | | USED |
| `icon_militia`, `icon_reputation` | regular pip, champion line | unreferenced | `icon_reputation` as the "regular" pip at the counter and in Town (T3.5) | | UNUSED-REUSABLE |
| `faction_*_alt_{0,1}` (6) | enemy variety in Town and aftermath cards | unreferenced | add to `Sprites.faction` (T2.6) | | UNUSED-REUSABLE |
| `tile_paper` | Gazette | used | none | | USED |
| `panel_gazette`, `panel_journal` | header ornaments | unreferenced | none in this update | not nine-patchable, pseudo-text scribbles | UNUSED |
| V3 `bg_*` (96x48), `bg_title_workshop_night`, `bg_run_end_fallen_forge` (270x150) | title, run end | not imported | allowlist only the two 270x150 pieces for the "forge has fallen" card and the run-end screen (T2.4) | flat true-pixel style: stand-alone screens only, never under painted sprites | UNUSED-REUSABLE (stand-alone) |
| V3 `ui_frame_*`, `ui_button_*`, `ui_slot_*`, `ui_tile_*`, `ui_divider_*`, icons | frames and chrome | not imported | none: belongs to the later redesign | need a nine-slice renderer; 8 px caps make chunky borders; 15 IDs collide with live IDs | REFERENCE for this update |
| launcher icon | app identity | Android Studio template | candidate from sheet 5 (`blessing_forgefire`, the anvil in flames) exported large by the importer (T7.4) | owner decision | MISSING |

### 5.3 Counter scene: functional composition from real assets

| Layer | Asset | Native | Draw | Filtering |
|---|---|---|---|---|
| 0 backdrop | `bg_counter_forge` | 540x270 | integer scale `s = ceil(widthPx / 540)`; source window centred horizontally, anchored to the bottom, cropped to the box. Height: 180 dp on a 360 dp wide phone at density 3.0, never above 200 dp | nearest; never a fractional nearest scale |
| 1 customer | a portrait from the saved appearance key, drawn by the rule below | 64x64 | `2s` (4x at 1080 px = 85 dp), on the counter line at about 62 % of the width; slides in, one-pixel bob; static under reduced motion | nearest, integer |
| 2 counter | Compose-drawn plank band in `wood2` / `wood1` with an `ink` edge | n/a | full width, 8-10 dp lip overlapping the portrait's bottom edge | n/a |
| 3 stock | `weapon_*` sprites in 4-5 `ink` slots under the scene; the considered blade lifts, the sold one leaves its slot | 56x56 | integer factor inside a 56-64 dp slot | nearest, integer |
| 4 text | native Compose only | n/a | name plate on a solid `deep` surface in a scene corner; reason, receipt rows and controls in a card below the shelf band | n/a |

**How the customer is drawn (decided once, at the T2.4 / T2.5 gate, on one emulator screenshot that holds a
sheet-3 face and a reference-board face side by side in each mode):**

1. **Preferred: cut-out busts standing behind the counter**, as in the mock. The 25 re-sliced sheet-3 busts are
   already transparent. The reference-board tiles are opaque with painted backgrounds, so this mode needs the importer
   to key them (border flood-fill, then the existing main-component clean-up). A keyed tile passes when it is a single
   component, has no opaque pixel in its outer 1 px ring, and shows a class sign (helm, hood, feathered hat, pointed
   hat or antlers); tiles that fail are dropped, not repaired.
2. **Otherwise: every customer, from both sets, is a framed `PortraitTile`** (one `ink` tile, one frame) seated on the
   counter band. Uniform by construction; the sheet-3 busts sit on the same tile.
3. **Otherwise: the 25 re-sliced busts only**, with reviewed hue liveries, and the face target stays 25.

The owner signs the chosen mode off on that screenshot; the ledger records the screenshot path and the count of
faces that passed. M2 is built so that either mode works: `CounterScene` takes a `PortraitTile` whose frame is a
parameter.

Type and timing (so "readable native text" and "restrained motion" are checkable):

| Element | Style | Rule |
|---|---|---|
| Name plate | `titleMedium`, one line, ellipsis only above 20 characters (names are capped at 20) | never auto-sized |
| Reason line, tally lines | `bodyLarge` | wraps; never truncated |
| Receipt rows | label `bodyLarge`, value `titleMedium`, right-aligned | four separate rows: listed, trade-in, bonus or stipend, coin to the till |
| Navigation labels | `labelMedium`, fixed | may cap their own scale at 1.5x when the system scale is 2.0 |
| Beat length at 1x (2x halves; "Tap" has none) | open 1.5 s; arrive 1.2; browse 1.2; decide 1.6; transact 1.6; tally 2.5; close 2.0; aftermath card 2.0 | PROPOSED playtest values, constants in `ShopDayUi.kt` |
| Day length at 1x with no taps | median at most 25 s and p90 at most 30 s over the fixture days of T3.7 | measured by a Compose test with a virtual clock |

Why it holds together: at these factors the three painted sources agree on apparent pixel size (about four device
pixels per art pixel). Scene bitmaps decode to about 0.63 MiB (backdrop 583 KB, one portrait 16 KB, five weapons
63 KB); the atlas is never loaded whole (6.3 MB as one bitmap). On a 720 px wide screen the visible source window is
360 px wide, so the furnace and anvil must sit in the centre 360 source pixels of the crop (they do).

Stated limits: the furnace in a painted backdrop cannot change with energy the way today's furnace sprites do; the
recommended crop has no night version; a customer has no animation beyond what Compose can do to a still portrait.
The mock `crops/counter_scene_recommended_C_1080px.png` is a Pillow composite with a sheet-3 bust, not Compose output:
the gate screenshot above is the real check.

Reusable components this produces: `CounterScene` (shop day, and as the static header of the Shop destination with
the live shelf), `PortraitTile` (every face at 44, 56 and 85 dp, framed or cut-out), `WeaponSlot` (sprite, rarity
pip, flaw pip, price tag), `ReceiptRows`.

### 5.4 Appearance pool (N04): what the art honestly supports

| Tier | What | Looks | Condition | Work |
|---|---|---|---|---|
| 0 | today | 25 faces, 15 with slicing defects | n/a | n/a |
| 1 | re-slice sheet 3 on measured cells | **25 clean faces: the guaranteed target** | importer self-check, 25 of 25 | T2.4 |
| 2 | the class tiles of the v2 reference board | **up to 25 more (10 a class)**; several tiles show non-human heads, which is cosmetic, and two battlemage tiles carry no class sign and are expected to be dropped | passes the gate of 5.3 (mode 1 or 2), tile by tile | T2.4 (the gate), T3.3 (the import) |
| 2b | 5 townsfolk tiles for non-hero visitors (the collector) | +5 class-agnostic | same gate | T3.3 |
| 3 | curated import-time hue liveries of the sheet-3 busts (same face, different colours) | up to about 45, only the reviewed ones | built if tier 2 fails or leaves a visible shortage | T3.3 |

Tested and rejected: ramp palette swaps (the art has no ramps), accessory overlays (no layered parts exist), mirrored
faces as new identities (same person, the light flips, heraldry flips). A naive hue window turns duelist faces blue;
a window of 345-6 degrees with saturation at least 0.60 is clean on three of five. Hue variants are import-time
outputs with their own IDs, so "never hand-edit PNGs" holds. The starter pack's own 25 portraits are not part of the
pool (5.2, 4.8); the pack author's note that the reference boards are "not ready-to-import sprites" is why tier 2
is conditional and gated instead of assumed.

With assignment that skips faces in use, a town shows no shared face while a class has no more living members than
it has faces: five with tier 1 (a 12-hero town over five classes averages 2.4 a class, and a sixth member of one
class is the case that repeats a face), ten with tier 2. The stable key scheme is in section 6.6.

### 5.5 Import plan (all in `tools/pixelart/import_assets.py`)

1. **Fix the portrait slice** (`layout_3`): explicit cells from `sheet3_proposed_cells.json` (rows `(31,270) (284,508) (520,736) (742,972) (982,1215)` with per-row column cuts), each run through the existing `main_component_only` clean-up. Keep the 25 IDs and the 64x64 box. Self-check: a portrait whose non-empty rows form more than one run fails the import.
2. **Record the content box** of each portrait in `overrides.json` and the generated lookup, so a bust can stand on the counter line.
3. **Cell files move into the pipeline.** `sheet3_proposed_cells.json`, `v2ref_portrait_cells.json` and `atlas_boxes.json` are copied from `docs/major_update_evidence/` to `tools/pixelart/cells/` and committed in T2.4; the importer reads them from there.
4. **Flat-source table**, matched by file name like the master sheet, each entry `id, box, out size, mode`: `bg_counter_forge` (opaque 1:1 copy, alpha forced to 255, no resample); the v2 portraits and townsfolk from `v2ref_portrait_cells.json`; the adopted atlas crops and three icons from `atlas_boxes.json`. The importer must also look inside `concept_references/`.
5. **No blanket flags.** `--pack-all` would overwrite 83 sheet-derived IDs with 16 px sprites (20 portraits among them) and change `ForgeScene`'s unit; lifting the V3 skip would silently replace `siege_wall` and 12 icons. Replace both with a per-pack allowlist of exact IDs, and make an ID produced twice in one run an error naming both sources.
6. **Keying and alpha**: border flood-fill for flat RGB sources; snap alpha (250 and above to 255, below 16 to 0); backdrops and tiles fully opaque.
7. **Generated lookup** `ui/PortraitArt.kt` (appearance key string to `R.drawable`, plus content box), emitted the way `WeaponArt.kt` is, so Kotlin never hand-lists portrait IDs and a missing asset is a compile error instead of a wrong face.
8. **Reproducibility**: boxes are constants or checked-in JSON; store each source's SHA-256 in `overrides.json` so a re-exported sheet is detected instead of silently re-sliced with stale boxes; pin Pillow and numpy in `tools/pixelart/requirements.txt`.
9. **Filtering rules written into `docs/ART_BRIEF.md`**: true 1x grids at integer nearest only; reduced concept slices nearest at an integer enlargement and bilinear when shrunk; flat backdrops integer nearest with a centred, bottom-anchored window. Inside a scene `Canvas`, compute integer destinations as `ForgeScene` and `SiegeStage` do.
10. **Animation timing**: the pack manifest gives 120 ms hero and monster frames, 180 ms embers, 100 ms burst; runtime values are hard-coded and differ for two of the three. No change in this update; there are no customer frames, that motion is Compose-driven.

### 5.6 Missing art (stated, with the workaround used)

| Need | Exists? | Workaround in this update | Verdict |
|---|---|---|---|
| counter foreground in the painted style | no | Compose-drawn plank band | adequate; artist gap only if the counter should carry character |
| a shelf with an empty surface for stock | no | dark Compose slots | adequate |
| a customer that walks, idles or reacts | no | slide, fade and a one-pixel bob of the bust; off under reduced motion | genuine gap for real animation |
| transaction outcome icons (paid, too dear, not better, trade-in, commission, collector) | only `icon_gold` | native text chips with `icon_gold`; the atlas purse for trade-in | genuine gap: 5-6 icons |
| signature weapons at 56 px | 12 at 16 px, 12 not at all | level bump, burst, ring | genuine gap: 24 sprites |
| heat states and a night version of the counter backdrop | no | the ember loop over the furnace mouth; the atlas night forge on the Tomorrow card | gap if heat must stay visible |
| app icon, feature graphic, store frames | no | candidate from sheet 5; wide strip of the panorama as a base | owner decision; store art is P8 |
| more than 10 faces a class | no | hue liveries | gap only if the population grows well past 16 |

### 5.7 Provenance and usage (owner decision before any store release)

- No licence, usage or attribution text exists anywhere under `Pixel art assets/` or in the repository root.
- All seven top-level source images and both concept references carry an embedded Content Credentials (C2PA) manifest
  naming ChatGPT / OpenAI as the generator; the two packs are script-drawn (their READMEs: "drawn programmatically
  from deliberately authored shapes"; "The `concept_references/` folder contains target art direction, **not
  ready-to-import sprites**": that caveat is about 16 px sprites, and the project already slices boards of the same
  kind at 64 px, but it is the pack author's stated position).
- The project calls this art "hand-made" in `CLAUDE.md`, `docs/PROGRESS.md` and the importer's manifest
  (`"source": "handmade"`). T0.3 changes the wording to "imported concept art" and "script-drawn pack sprites".
  How the art is described on a paid store listing, and confirming the usage terms of the generating account, is an
  owner decision recorded in section 10.4; it blocks P8, not this update.

---

## 6. Architecture contracts, state machines, identity persistence, migrations

These contracts are settled before any UI or content worker starts. One integrator owns the files that carry them
(`Model.kt`, `GameEngine.kt`, `ResolutionContext.kt`, `Commands.kt`, `Compatibility.kt`, `SaveCodec.kt`,
`BalanceConfig.kt`, `Content.kt`, `GameSession.kt`, `GameRepository.kt`). Signatures are the agreed shape; bodies are
the tasks.

### 6.1 One operation boundary (app): F01, F02, E03, X01, X03

New `app/src/main/java/com/example/blacksmithproject/data/GameRepository.kt` and
`app/src/main/java/com/example/blacksmithproject/GameSession.kt`. `SaveStore` implements the interface.
`GameViewModel` keeps only UI state (destination, draft, open sheet) and forwards intents. `GameSession` has no
Android import, so its tests are plain JVM tests in `app/src/test`.

```kotlin
/** The rows exactly as stored. The repository never decodes: decoding, migration and admission happen in the session,
 *  so the production failure mapping is the code the JVM tests exercise, and "bytes equal before and after" is a direct comparison. */
data class StoredRows(val run: String?, val legacy: String?, val cursor: String?)

/** Room implementation: SaveStore. Test implementation: FakeGameRepository. The repository owns its own dispatcher. */
interface GameRepository {
    suspend fun load(): StoredRows                         // one read; never writes or deletes; throws SaveFailure.Io
    suspend fun commit(run: String?, legacy: String)       // one transaction; null run clears the run; throws SaveFailure.Io
    suspend fun saveCursor(cursor: String?)                // its own small row; never inside GameState
    suspend fun quarantine(key: String)                    // moves a row to "<key>.bak.<millis>"; never deletes
}

sealed class SaveFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Corrupt(val key: String, cause: Throwable) : SaveFailure("Save '$key' cannot be read", cause)
    class Newer(val key: String, val found: Int, val supported: Int) : SaveFailure("Save '$key' is from a newer version")
    class Incompatible(val problems: List<String>) : SaveFailure("Save cannot continue: $problems")
    class Io(cause: Throwable) : SaveFailure("Storage failed", cause)
}

class GameSession(
    private val engine: GameEngine,
    private val repo: GameRepository,
    private val compute: CoroutineDispatcher = Dispatchers.Default,   // engine calls and JSON encoding; the session never names Dispatchers.IO
) {
    /** Exactly what is on disk. The UI renders this and nothing newer. */
    data class Snapshot(val run: GameState?, val legacy: LegacyProfile, val cursor: DayCursor?)

    sealed interface Op {
        data class Dispatch(val command: Command, val runId: RunId) : Op
        data class Claim(val runId: RunId) : Op
        data class BuyUpgrade(val upgradeId: UpgradeId, val runId: RunId?) : Op
        data class BeginEra(val seed: Long, val afterRunId: RunId?) : Op
        data class MoveCursor(val cursor: DayCursor) : Op
    }
    sealed interface Result {
        data class Done(val accepted: CommandOutcome.Accepted? = null) : Result
        data class Rejected(val error: GameError) : Result
        data object Stale : Result               // issued against a run that is no longer current: nothing written
        data object DayNotWatched : Result       // a planning command while the counter or aftermath is unwatched
        data class Failed(val failure: SaveFailure) : Result
        data class EngineFault(val message: String) : Result      // the engine threw: nothing written, never retried
    }
    sealed interface Status {
        data object Idle : Status
        data class Working(val op: Op) : Status
        data class Failed(val op: Op, val failure: SaveFailure) : Status
    }

    val snapshot: StateFlow<Snapshot?>      // null until load() succeeds
    val status: StateFlow<Status>
    suspend fun load(): Result              // read rows, SaveCodec decode + migrate, Compatibility.admit, publish; otherwise Failed
    suspend fun run(op: Op): Result
    suspend fun retry(): Result             // re-runs the op held in Status.Failed
    suspend fun startOverKeepingBackup(): Result   // quarantine("run"), then publish Snapshot(null, legacy, null)

    companion object {
        /** Where the presentation of the last resolved day stands. Derived; the cursor row alone is never trusted. */
        fun pending(run: GameState?, legacy: LegacyProfile, cursor: DayCursor?): DayCursor.Stage = when {
            run?.lastResolution == null -> DayCursor.Stage.DONE
            run.isEnded && run.runId.value in legacy.claimedRunIds -> DayCursor.Stage.DONE
            cursor == null || cursor.commandId != run.lastResolution.commandId.value -> DayCursor.Stage.COUNTER
            else -> cursor.stage
        }
    }
}
```

Rules the implementation and `GameSessionTest` enforce:

1. **One `Mutex` for every entry point**: `load`, `run`, `retry` and `startOverKeepingBackup` each hold it across
   read, compute, write and publish. Nothing else writes the repository. The store is read by `load()`, by an explicit
   Retry, and by the confirmation read of rule 5, nowhere else.
2. State is read from `snapshot.value` **inside** the lock. Callers pass intent (a command, an upgrade ID), never state.
3. Every op carries the `runId` it was issued against; a mismatch returns `Stale` and writes nothing. This removes
   "upgrade lands after Begin era", "a queued End Day hits the next era" and the double Begin era.
4. **The ended run is never deleted, and after a claim the legacy row is the only profile that counts.** The run
   embeds a copy of the profile (`GameState.legacy`), so with the ended run kept there are two; these rules keep them
   from fighting:
   - 4a. During a live run a command commits `(out.state, out.state.legacy)`, as today. A `Dispatch` whose accepted
     state equals `snapshot.run` (an End Day replayed by its idempotent command ID) writes nothing and returns `Done`.
   - 4b. A `Dispatch` on a run whose `runId` is in `legacy.claimedRunIds` returns `Rejected(GameError.RunEnded)`
     without calling the engine.
   - 4c. `Claim` returns `Rejected(RunNotEnded)` unless `run?.isEnded == true` and `pending(...)` is `DONE`, and
     `Rejected(AlreadyClaimed)` if the run is already claimed. It commits `(endedRun, newLegacy)`.
   - 4d. `BuyUpgrade` is accepted only when there is no run or the run is ended and claimed, else
     `Rejected(RunNotEnded)`. It commits `(snapshot.run, newLegacy)`.
   - 4e. `BeginEra` is `Rejected` unless there is no run or the run is ended and claimed. Only `BeginEra` replaces the run.
   - 4f. `Claim`, `BuyUpgrade` and `BeginEra` read the legacy **row** (`snapshot.legacy`), never `run.legacy`.
   The run-end screen is rebuilt from the ended run plus the stored profile, and `claimed` is always
   `runId in legacy.claimedRunIds` (X03).
5. A failed commit sets `Status.Failed(op, failure)`. Before the player is offered anything, the session re-reads the
   store once: if the rows already hold the new state (the transaction committed and the failure surfaced afterwards)
   it publishes that state and reports `Done`; otherwise `snapshot` stays as it was and the dialog says "Could not
   save the day. Nothing has changed." with Try again and Keep working. `retry()` recomputes from the same snapshot;
   because RNG state is part of the snapshot, the recomputed result is identical.
6. An exception from the engine (an invariant failure, a `require`) returns `Result.EngineFault(message)`, leaves
   `Status.Idle`, publishes nothing and is never retried: the player sees "The forge cannot do that right now" and the
   last committed state stays.
7. The commit and the publish that follows it run under `NonCancellable`.
8. `MoveCursor` takes the same lock (ordering) but does not raise `Status.Working`: a lost cursor write only repeats
   presentation. The moves to `TOMORROW` and to `DONE` are awaited; if an awaited write throws, the in-memory snapshot
   still moves, so a failing cursor row can cost one Resume prompt on the next launch and can never lock the player
   out of a blessing, of the next day or of the run-end screen.
9. **The planning lock is stated on `pending(...)`, never on the stored cursor's stage** (right after an End Day
   commit the stored cursor still names the previous day). `Dispatch` returns `DayNotWatched` while `pending` is
   `COUNTER` or `AFTERMATH`; while it is `TOMORROW` only `ChooseBlessing` is accepted; everything is accepted at `DONE`.
10. The engine call and JSON encoding run on `compute`; the repository owns its own dispatcher; the main thread only
    collects (E03).

### 6.2 The visit record (core): S04, S05, F07, X14

**Extend `MarketVisit` in place; no parallel record** (two records of one day is how F03 and F07 happened). Every new
field is defaulted and the two retyped fields are wire-compatible (`reason: String` to an enum with the same
spellings; `heroId: HeroId` to nullable), so a 0.5.x day still decodes. New file
`core/src/main/kotlin/com/tinyblacksmith/core/model/ShopRecord.kt` holds the new types so workers do not collide in
`Model.kt`.

```kotlin
enum class VisitKind { BROWSE, COMMISSION, COLLECTOR }

/** The first nine keep the v1 spellings. M4 appends TASTE_MATCH, PRIZED, STORIED, COUNTERS_THREAT, RESISTED. */
enum class VisitReason {
    EMPTY_SHELVES, TOO_EXPENSIVE, NOT_BETTER, OVERPRICED, NOT_SUITED, UNDECIDED,     // left
    WORN_OUT, GREAT_FIT, GOOD_ENOUGH,                                                // bought
    COMMISSION_DELIVERED, COLLECTOR_PURCHASE,
}

/** Facts the counter may state. No utility, weight or chance ever leaves Market (GDD: descriptive only). */
enum class VisitFactor {
    SUITS_CLASS, OFF_CLASS, ELEMENT_TASTE, LIKES_NOVELTY, STRONGER_THAN_OWN, NOT_STRONGER_THAN_OWN, OWN_BLADE_WORN,
    UNARMED, STORIED_BLADE, COLLECTOR_PRIZE, CAN_AFFORD, CANNOT_AFFORD, ABOVE_THEIR_CEILING, REGULAR,
}

@Serializable data class WeaponSnapshot(
    val weaponId: WeaponId, val name: String, val familyId: WeaponFamilyId, val coreId: MaterialId, val augmentId: MaterialId,
    val element: Element? = null, val rarity: Rarity, val quality: Int, val power: Int, val condition: Int,
    val fame: Int = 0, val title: String? = null, val affixes: List<AffixId> = emptyList(),
    val flaws: List<AffixId> = emptyList(), val signatureId: String? = null,
)
@Serializable data class CustomerSnapshot(
    val heroId: HeroId, val name: String, val classId: HeroClassId, val level: Int,
    val appearance: String,                           // resolved appearance key (6.6), so a dead hero still has a face
    val traits: List<TraitId> = emptyList(), val elementTaste: Element? = null, val ambition: Ambition? = null,
    val gold: Int, val loyalty: Int = 0, val regular: Boolean = false, val visitNumber: Int = 1,
    val guildId: String? = null, val mentorName: String? = null,
    val equipped: WeaponSnapshot? = null,             // what they carried when they walked in
)
/** Refers to DayResolution.shopWeapons by ID, so a blade considered by five visitors is stored once. */
@Serializable data class Considered(
    val weaponId: WeaponId, val price: Int, val factors: List<VisitFactor> = emptyList(),
    val shortBy: Int? = null,                         // gold missing after trade-in credit, when CANNOT_AFFORD
)
@Serializable data class Sale(
    val listedPrice: Int? = null,                     // null for a commission blade handed over from storage
    val tradeInCredit: Int = 0, val tradeInWeaponId: WeaponId? = null,
    val cashPaid: Int,                                // coin the customer paid; for a commission, the reward
    val saleBonus: Int = 0, val stipend: Int = 0, val commissionId: CommissionId? = null,
)   // the shop's gold rises by cashPaid + saleBonus + stipend
enum class RecognitionCue { FIRST_VISIT, FIRST_BLADE, BECAME_REGULAR, REGULAR_RETURNS, STILL_CARRIES, BLADE_WORN, HELD_THE_WALL,
                           SLEW_AN_ELITE, KEPT_THE_VOW, MENTORS_BLADE, OF_THE_LINE, WAITED_YESTERDAY }
/** Typed, like everything else on the record: the cue and only the fields its template needs. */
@Serializable data class Recognition(val cue: RecognitionCue, val weaponId: WeaponId? = null, val otherHeroId: HeroId? = null,
                                     val day: Int? = null, val count: Int? = null)

@Serializable
data class MarketVisit(
    val heroId: HeroId?,                              // was HeroId; null only for a COLLECTOR who is not a hero
    val heroName: String,
    val purchasedWeaponId: WeaponId?,
    val reason: VisitReason,                          // was String
    val seq: Int = 0,
    val kind: VisitKind = VisitKind.BROWSE,
    val customer: CustomerSnapshot? = null,           // null for the collector and for a day decoded from a 0.5.x save
    val considered: List<Considered> = emptyList(),   // the chosen blade first, then the best alternatives, at most 3
    val sale: Sale? = null,
    val recognition: Recognition? = null,             // filled from T3.5
    val eventIds: List<String> = emptyList(),
)

enum class IncomeKind { SHELF_SALE, SALE_BONUS, STIPEND, COMMISSION, COLLECTOR, TRIBUTE }   // every way End Day adds gold today; a new source adds a constant
@Serializable data class ShopLedger(
    val goldAtOpen: Int, val goldAtClose: Int, val income: Map<IncomeKind, Int>,
    val tradeInCredit: Int, val spentPreparing: Int,
)   // invariant: goldAtOpen + income.values.sum() == goldAtClose

enum class FieldOutcome { WON, DRIVEN_BACK, DIED, PATROLLED, RESTED, GUILD_TRAINED, GUILD_LESSON, GUILD_TAUGHT, AMBITION_DAY, HELD_THE_WALL, FELL_AT_THE_WALL }
/** One hero's day beyond the door. Replaces string-matching on events for tallies and the aftermath. */
@Serializable data class FieldResult(
    val heroId: HeroId, val heroName: String, val outcome: FieldOutcome,
    val foe: String? = null, val elite: Boolean = false, val factionId: FactionId? = null,
    val weapon: WeaponSnapshot? = null,
    val lostBareHanded: Boolean = false,              // won, and the same recorded roll loses under the same formula with no weapon
    val lostWithOldBlade: Boolean? = null,            // for a blade bought today: the same roll loses with the blade it replaced
    val matchupHelped: Boolean = false,               // the blade's element or bane counted against this foe
    val gold: Int = 0, val materialId: MaterialId? = null,
    val withHeroId: HeroId? = null,                   // the guildmate who taught or was taught (0.6.0 hall lessons)
    val eventIds: List<String> = emptyList(),
)

// DayResolution gains (all defaulted):
val shopWeapons: List<WeaponSnapshot> = emptyList(),  // the opening shelf with nothing sold yet, plus any stored blade a commission took
val shelfPrices: Map<WeaponId, Int> = emptyMap(),
val ledger: ShopLedger? = null,
val field: List<FieldResult> = emptyList(),
val turnedAway: List<HeroId> = emptyList(),           // filled from T3.1
val recordVersion: Int = 0,                           // 0 = a 0.5.x day without snapshots, 1 = this record
```

Capture points, all inside the existing End Day, **adding no `ctx.rng` call** (asserted by `GoldenStateTest`: the
RNG stream states and the gameplay projection of T0.6 are equal before and after; the encoded save is not compared,
because `encodeDefaults = true` changes its bytes whenever a field is added):

| Field | Where the value already exists |
|---|---|
| `shopWeapons`, `shelfPrices` | captured at the **top of `GameEngine.endDay`**, before `Market.resolveCommissions` (T1.8 lets a patron take a shelf blade before browsers arrive), so every blade a visit refers to is in the list; any stored blade a commission takes is appended |
| `customer`, `customer.equipped` | `hero` and `current`, read **before** `purchase` moves the old blade to storage |
| `considered` and factors | the `evaluations` list: `Evaluation(weapon, utility, affordable, improvement, fit, pricePenalty, worn)`; taste, novelty, collector and fame are locals of `evaluate`, so `Evaluation` gains four booleans. Keep the chosen blade plus the best alternatives by the utility already computed |
| `reason` | the existing strings, now per visit **and** explained per considered blade, so "the better one was too dear" is visible (X14) |
| `sale` | locals of `Market.purchase` (`bonus`, `old`, `credit`, `price - credit`); `purchase` returns the `Sale` instead of `Unit` |
| `eventIds` | `ctx.emit` already returns the record |
| commission visit | `buyer`, `candidate`, `c.reward` in `Market.resolveCommissions`; `Sale.listedPrice` is the candidate's shelf price if it had one |
| collector visit | `w` and `price` in the collector world event; `WorldEventOutcome` gains `visit: MarketVisit? = null` and `fire` appends it with the event ID |
| `ledger` | every `ctx.gold +=` inside End Day goes through a new `ctx.earn(kind, amount)`; preparation spending is summed from the day's `MATERIAL_BOUGHT` (new event type) and `TOOL_BOUGHT` (gains a `cost`) records |
| `field` | `Battle.resolveExpedition`, `Heroes.patrol` / `rest` / guild and ambition days, `Battle.resolveSiegeIfDue` |
| `lostBareHanded`, `lostWithOldBlade` | `Battle.resolveExpedition` replaces `rng.chance(p)` with `val roll = rng.nextDouble(); val won = roll < p` (the identical single draw: `Rng.chance` is `nextDouble() < p`) and records `won && roll >= pWith(null)` and, when the hero bought a blade this morning, `won && roll >= pWith(oldBlade)`, where `pWith` is the same formula (same elite flag, bane, blessing and variance) with a different weapon. The simulator reports the share of wins flagged each way; target for `lostWithOldBlade` among same-day purchases: 10-35 % |

`Market.resolveMerchant` (the travelling merchant reselling a fallen hero's blade) is **not** a visit: no gold reaches
the shop. It appears in the aftermath and the Gazette only.

One appearance per hero per day: a patron who collects a commission today is not also seated as a browser today
(`ShopRecordTest.aPatronAppearsOncePerDay`); commissions resolve first, so the browse step simply skips them.

Retention: one day, inside `GameState.lastResolution`, exactly like visits today: a constant, not growth. Cost
estimate with `encodeDefaults`: about 6 KB at four visitors and 10 KB at eight (372 bytes today); T2.1 asserts under
12 KB for a ten-visitor day. For older days, End Day also emits one ordinary `SHOP_DAY` event (counts and coin by
kind) that rides the existing 30-day log, so a past edition's tally equals the day's report by construction.
Snapshots are never archived.

### 6.3 The script and the lead (core, pure, derived, never saved): S02, S03, S07, U02, U07

```kotlin
object ShopDay {
    const val FEATURED_MAX = 3            // a presentation constant, not a balance number
    fun script(resolution: DayResolution, after: GameState, content: ContentCatalog, config: BalanceConfig): ShopDayScript
}
data class ShopDayScript(
    val day: Int,
    val shelf: List<WeaponSnapshot>, val prices: Map<WeaponId, Int>,
    val quiet: QuietDay?,                 // NO_VISITORS, EMPTY_SHELF(names), or null
    val featured: List<MarketVisit>,      // in visit order
    val tally: List<TallyGroup>,          // every other visit, grouped by outcome and reason, with hero IDs and names
    val ledger: ShopLedger?,
    val aftermath: List<AftermathCard>,   // at most 3; siege first
    val moreInGazette: Int,
    val ending: Ending,                   // FALLEN, BLESSING, TOMORROW
    val lead: Lead?,                      // null when FALLEN
)
object Advice { fun lead(state: GameState, content: ContentCatalog, config: BalanceConfig): Lead }
data class Lead(val kind: LeadKind, val heroId: HeroId? = null, val weaponId: WeaponId? = null,
                val commissionId: CommissionId? = null, val factionId: FactionId? = null,
                val gold: Int? = null, val count: Int? = null, val days: Int? = null, val element: Element? = null)
enum class LeadKind { FIRST_BLADE, CHOOSE_BLESSING, ANSWER_REQUEST, FORGE_FOR_REQUEST, LIST_STOCK, FORGE_STOCK,
                      PRICES_TOO_HIGH, ARM_DEFENDERS, ANSWER_WANT /* M4 */, FORGE_FOR_BUYERS }
```

Rules (each a test in `ShopDayScriptTest` / `AdviceTest`; no RNG anywhere):

- **Featured visits.** Always: commission deliveries and collector purchases. Then purchases, ranked: first sale of
  the run, a regular, a trade-in, a bonus or premium, coin paid; ties by visit order. Then refusals, one per distinct
  reason, in the order OVERPRICED, TOO_EXPENSIVE, NOT_SUITED, NOT_BETTER (each maps to a different action tomorrow);
  EMPTY_SHELVES and UNDECIDED are never featured. Fill three slots with at least one purchase when one exists and at
  least one refusal when one exists. Precedence when they collide (two commissions and a collector are possible from
  T4.6): at most one commission and one collector are featured whenever a purchase or a refusal would otherwise be
  squeezed out; the others go to the tally under their own heading. Show the featured visits in visit order.
  Invariant: featured plus tally equals the recorded visits, each exactly once.
- **Aftermath.** The siege result first when there was one; then field results and events whose subjects include a
  blade the smith made or a hero seen at the counter today, ranked: hero death, elite slain, ambition fulfilled,
  title or legend earned, a blade inherited by a guildmate, a blade resold by the travelling merchant, a win with a
  blade bought today, a lesson at the guild hall between two customers of the shop, scarce material brought back
  (Lucky), blade broken / lost / stolen, a loss. At most three; the rest are counted as "N more in the Gazette".
  A card states the counterfactual only when it is recorded, and in those words: "with her old bow the same fight
  was lost" (`lostWithOldBlade`) or "bare-handed the same fight was lost" (`lostBareHanded`). No card says a blade
  "decided" anything. A card whose event has a 0.6.0 fight
  replay (`CombatReplay.kind == EXPEDITION`, joined by `eventId`) offers "Watch the fight".
- **Decision lines** are authored templates over `VisitReason`, `VisitFactor` and the recorded numbers, for example
  TOO_EXPENSIVE: "Could pay up to {gold + trade-in}; the cheapest blade is {min price}." A template whose fields are
  missing falls back to the bare reason label. One vocabulary replaces the two in the app today
  (`MarketPanel` and `Gazette.visitReason`).
- **A day decoded from 0.5.x** (`recordVersion == 0`) has no snapshots: the script is a tally of names and reasons
  and goes straight to the ending (Review Focus 1).
- **The lead** is the first match of a fixed, PROPOSED order: day 1 with nothing forged; a pending blessing; an
  offered commission due today or tomorrow; an accepted commission with nothing eligible and two days left (uses
  `Commissions.fit`); an empty shelf with stock in storage; an empty shelf with none; two or more price refusals
  yesterday; a siege within two days with the odds against the town; from M4 a standing want; otherwise "forge for
  today's buyers" with a demand fact. The Shop destination and the Tomorrow card call the same function, so they
  always agree.

### 6.4 One complete day edition: F03, U07

`DayResolution.events` becomes every record of the resolved day, preparation included:
`ctx.events.filter { it.era == era && it.day == day }`, taken before compaction. `CommandOutcome.Accepted.events`
keeps meaning "emitted by this command". The report overlay, the Shop's "Yesterday" block and the archive all render
`Gazette.edition(events, ...)`; for the latest day they read `lastResolution.events`. The edition's tally line is
built from `ShopLedger` and `List<FieldResult>` (or from the day's `SHOP_DAY` event for older days), not from event
text; a won expedition's second "brought X back" record no longer needs the `winProbability` key to be told apart.

A day resolved by an older build (`recordVersion == 0`) stored only the End Day records in its report, so for such
a day the edition is built from `eventsForDay(day)` instead of `lastResolution.events`
(`DayEditionTest.anOldSavesLastReportIsBuiltFromTheArchive`: the v1 fixture's day 60 shows 20 records, not 15).

### 6.5 Presentation state machine and the cursor: S01, S03, S06, X02

```kotlin
@Serializable data class DayCursor(val commandId: String, val stage: Stage, val index: Int = 0) {
    enum class Stage { COUNTER, AFTERMATH, TOMORROW, DONE }
}
```

Declared in `core/persistence/DayCursor.kt` beside `SaveCodec` (the app module has no serialization plugin, and the
codec round-trip is then a JVM test); it is **not** a field of `GameState`. Stored as its own row
(`key = "cursor"`) in the existing `saves` table through `GameRepository.saveCursor`. `index` is clamped to the
script's length when read, so a cursor can never point past a day.

Why there and not elsewhere. In `GameState` (a field `unacknowledgedDay` plus an `AcknowledgeDay` command, proposed
by the core review): it would make the engine enforce the planning lock and keep everything in one document, at the
cost of one extra full-document write per day, a presentation concept in every bot and about twenty test call sites,
and no place for the encounter index. In DataStore (as `dismissedReport` today): written outside the operation queue
and outside the save's backup unit. A row beside the save keeps gameplay bytes untouched by anything the player
watches, is written through the same serialized boundary, travels with the database, and holds the index. The
planning lock is enforced one layer up, in `GameSession` (rule 9), and tested there. `SettingsStore.dismissedReport`
is read on the first load after the update: when it names the current `lastResolution` and no cursor row exists,
the session writes `(commandId, DONE)` through `saveCursor`, so a report closed in 0.5.x or 0.6.0 stays closed even
if the app is killed before the next End Day. The key is no longer written.

What stands to be presented is `GameSession.pending(run, legacy, cursor)` (6.1): `COUNTER` for a missing cursor or
one that names another day, the cursor's own stage otherwise, `DONE` when there is no resolved day or the run is
ended and claimed. The three endings share one stage: entering Blessing, Forge fallen or Tomorrow moves the cursor
to `TOMORROW` (awaited); leaving them moves it to `DONE` (awaited).

| State | On screen | Controls | Back | Leaves to |
|---|---|---|---|---|
| Planning | four destinations; End Day with its sublabel | all planning commands | to Shop; on Shop leaves the app | Committing |
| Committing | same screen, input locked, "Closing the shop..." after 150 ms | none | consumed | Shop opens / Quiet day; Commit failed on error; Planning with a message on a rejection |
| Commit failed | "Could not save the day. Nothing has changed." | Try again, Keep working | = Keep working | Committing or Planning |
| Resume prompt (cold start with an unwatched day) | "Day N is done and saved. The shop took X gold." | Resume the day, Skip to tomorrow | leaves the app | the stored stage and index, or the skip target |
| Shop opens | counter scene with the shelf as it stood at lock | tap or Next, Skip day, speed | leaves the app | first visit |
| Visit `i`: arrive, browse, decide, transact | face, name, class, "regular", weapon in hand, one line; the considered blade lifts; the typed reason with its numbers; receipt rows (listed, trade-in, bonus, **coin to the till**), the blade leaves its slot | Next, Back, Skip day, speed; tap a face or blade for its sheet | previous beat | next visit, Tally or Shop closes |
| Tally | "5 more came by: 1 bought (Iron Axe, 60) · 3 could afford nothing · 1 found nothing better", with faces and names | Next, Back, Skip; each name opens the hero sheet | previous beat | Shop closes |
| Shop closes | till by kind: "Took 199 gold · 2 sales, 1 commission · 4 left · 2 found the shop full" | Continue, Back | previous beat | Aftermath, or the ending if nothing to show |
| Quiet day (replaces the five rows above) | one card | Continue | leaves the app | Aftermath or the ending |
| Aftermath `k` | up to three cards naming hero and blade; "N more in the Gazette" | Next, Back, Read the Gazette, Watch the fight | previous card | Forge fallen, Blessing or Tomorrow |
| Fight replay overlay | the existing siege diorama or text rounds; the outcome line is visible from the first frame | step, Skip, Close | closes | Aftermath |
| Forge fallen (defeat) | cause, days survived | See the legacy, Read the Gazette | previous card | Run ended (cursor `DONE`, awaited) |
| Blessing (siege held, offer pending) | the offered blessings | one button each, Decide later | = Decide later | Tomorrow (after the save, if chosen) |
| Tomorrow | "Day N+1": shelf and storage counts, **one lead and its reason**, the next threat line | Begin day N+1, Read the Gazette, Back | previous state | Planning on the Shop destination (cursor `DONE`, awaited) |
| Gazette overlay, hero sheet, item sheet | the full edition; snapshot first, "now" below when it differs | Close | closes | the caller |
| Run ended | summary, Claim, upgrades with their next-run change, Begin era; all serialized, busy shown | `Claim`, `BuyUpgrade`, `BeginEra` | leaves the app | the new run |
| Load failed | what failed in plain words | Retry; "Start over (keeps a backup)" for Corrupt, Newer, Incompatible | leaves the app | Planning or title |

Rules:

- Tap anywhere on the scene advances. The first tap during a running motion completes the motion and does not also advance.
- The default speed is **Tap**: nothing advances by itself, which is the GDD's "no time-pressure actions". 1x and 2x
  are opt-in from the speed chip and remembered in settings; beat lengths are the table of 5.3. Auto-advance runs
  only while the activity is resumed, and pauses while a sheet, the Gazette or the replay is open. Timers never run
  under reduced motion or with TalkBack on.
- Reduced motion (the app toggle or a system animator scale of 0) merges a visit's four beats into one static card.
  The result of every beat is on its first frame; no outcome text waits on a timer.
- Skip day never asks for confirmation (nothing is lost) and lands on Forge fallen, Blessing or Tomorrow.
- The cursor is written at each visit boundary (write-behind); the moves to `TOMORROW` and `DONE` are awaited. If
  an awaited write throws, the in-memory snapshot still moves (6.1 rule 8): a failed cursor write may cost one
  Resume prompt on the next launch and can never lock the player out.
- The sequence dispatches no command, with one exception: choosing a blessing, which is a next-day planning command
  that draws no RNG. Skipping past it leaves the offer in `GameState.pendingBlessingOffer`, where the Shop already
  shows it. `PresentationEqualityTest.blessingChosenInTheSequenceEqualsChosenNextMorning` covers the exception.
- The composables receive UI models and callbacks only (`onEvent: (ShopDayEvent) -> Unit`); none receives the
  ViewModel, the engine or a `Random`. A source test asserts there is no `Random` under `ui/`.

Process death: during Planning, the last accepted command and (on task restore) destination, draft and reveal come
back; during Committing before the transaction commits, the player is on day N and End Day again produces the same
day (deterministic command ID on the same state and RNG); after the commit, the Resume prompt; mid-counter, the Resume
prompt at the stored visit, with at most one visit shown twice; on Tomorrow, the Tomorrow card; after `DONE`, planning
on day N+1; on the defeat day, the sequence, then Forge fallen, then Run ended; after a claim, Run ended with upgrades
still available.

### 6.6 Identity: names, appearance, lineage (N03, N04, X07)

```kotlin
// Hero gains (all defaulted, old saves decode):
val appearance: String? = null,        // asset ID string, e.g. "portrait_guardian_0", "portrait_v2_ranger_3"
val lineageId: String? = null,
val arrivedOnDay: Int = 1, val shopVisits: Int = 0, val shopPurchases: Int = 0,
val lastServedDay: Int? = null, val lastPurchaseDay: Int? = null, val turnedAwayStreak: Int = 0,
// LineageAnchor gains: val id: String = "", val appearance: String? = null
// HeroClassDef gains: val appearances: List<String>     (validated: non-empty, unique across classes; always
//                    contains the five legacy IDs portrait_<class>_0..4, so every legacyKey is a valid key)

object Appearance {
    /** Today's formula with the modulus frozen at 5: every pre-update hero keeps the face the player knows. */
    fun legacyKey(hero: Hero): String = "portrait_${hero.classId.value}_${Math.floorMod(hero.id.value.hashCode(), 5)}"
    fun keyOf(hero: Hero): String = hero.appearance ?: legacyKey(hero)
    /**
     * Pure, no RNG stream: order the class pool by a hash of (run seed, hero ID, key); take the first key no living hero wears.
     * [wornByLiving] holds RESOLVED keys (`keyOf(h)` for every living hero), so a pre-update hero whose field is null still
     * blocks the face the player sees on them.
     */
    fun assign(runSeed: Long, heroId: HeroId, classKeys: List<String>, wornByLiving: Set<String>): String
}
```

- **Keys are asset IDs, never indices.** Reordering or extending the art cannot change a saved hero's face. The app
  resolves `PortraitArt[key] ?: PortraitArt[Appearance.legacyKey(hero)] ?: portrait_<class>_0`, so a face of the
  right class always renders even if an asset is removed.
- **No gameplay RNG.** `Heroes.generate` consumes the HEROES stream in a fixed order; one extra draw would shift
  every later hero, every simulator seed and every determinism test. Appearance comes from the hash above. A new
  `RngStream` is also ruled out: `RngState.stateOf` throws on a stream an old save does not carry.
- **Old saves need no migration step for faces**: `appearance` stays null and resolves through `legacyKey`.
  `AppearanceTest.legacyKeysArePinned` pins `h1..h40` to the table of today's buckets.
- A descendant may take the ancestor's key when the anchor stores one and nobody living wears it.
- `CustomerSnapshot.appearance` carries the resolved key, so a replayed visit shows the right face after the hero died.
- Names: section 4.4. Display names never identify anything; every join is by `HeroId` or `lineageId`.

### 6.7 Versions, compatibility and migration: F09, X08, X10

| Number | Where | Changes when | Policy |
|---|---|---|---|
| `SaveCodec.SCHEMA_VERSION` | envelope | a field is renamed, removed or retyped incompatibly; a stored value must be converted; **or a slice adds an enum constant, an event type or a field that an older build must not silently ignore** (the codec has no `coerceInputValues`, so an older build would otherwise read a newer save as corrupt) | stepwise payload migration with **separate tables for the run and the legacy document**; a step may be a no-op that only raises the number |
| `GameState.rulesVersion` / `GameEngine.RULES_VERSION` | run | End Day order, RNG draw order or the stream set changes | bumped by **every slice that changes them**; a run **continues forward** under the installed rules after `Compatibility.admit` stamps it; history, serials and RNG states are never rewritten |
| `GameState.contentVersion` / `ContentCatalog.version` | run | an ID is added, removed or renamed, or a number in a definition changes | a save that references an unknown ID is `Unsupported`, never a crash |
| `GameState.balanceVersion` (new; 0 = written before tracking) / `BalanceConfig.version` | run | any config number changes | stamped forward |

What each slice of this update does to them (each row lands as one commit at the slice's gate, so a build that leaves
the machine after any gate is internally consistent):

| Slice | Rules | Schema | Content | Balance | Why |
|---|---|---|---|---|---|
| today (0.6.0) | 1 | 1 | 2 | 5 | |
| M1 | **2** | **2** (real step: stamps `balanceVersion`; lowers an open off-band commission to its band floor) | 2 | 5 (6 only if the collector cap or the ore-merchant fix moves a recorded table) | champion context, commission order and pick, tie-breaks, ore merchant |
| M2 | 2 | **3** (no-op step: visit enums, `SHOP_DAY` and `MATERIAL_BOUGHT` event types, the visit record) | 2 | unchanged | recording only; no outcome changes |
| M3 | **3** | **4** (no-op step: hero counters, appearance, lineage IDs) | **3** (names, appearance keys) | **6** | selection, population, arrivals, compensation |
| M4 | **4** | **5** (no-op step: new reasons and factors, `Hero.want`, `Commission.kind`, `LegendEntry` fields; legacy table: clue bit sets) | 3, or 4 if template text counts as content | **7** | need terms, sidegrades, siege demand, commission kinds, returned legends |
| M5-M6 | 4, or 5 if T5.2's valve or T6.3a changes an outcome | as needed | as needed | as needed | each change named in the ledger |

`RULES_VERSION` salts every stream seed (`RngState.seeded`), so each bump re-records the seed-pinned tests and the
golden projection file of that rules version (`state_hashes_rules<N>.txt`, one per version, the older ones kept).
That cost is paid by those slices anyway, because they change outcomes on every seed. The 10,000-seed v5 table stays
the comparison baseline by policy and distribution, not by seed.

```kotlin
object Compatibility {
    sealed interface Result {
        data class Admitted(val state: GameState) : Result          // versions stamped to current
        data class Unsupported(val problems: List<String>) : Result
    }
    /** Pure. Never throws. Never touches events, weapon histories, serials or RNG stream states. */
    fun admit(state: GameState, content: ContentCatalog, config: BalanceConfig): Result
}
```

`admit` refuses a run whose rules or content version is newer than the build, one that references an unknown content
ID (family, material, affix, class, trait, blessing, tool, upgrade, faction), one that lacks an RNG stream, or one
that fails the extended `Invariants.check` (which already returns a list). `GameSession.load` maps `Unsupported` to
`SaveFailure.Incompatible`; the recovery screen explains it; the legacy profile is never discarded. `GameEngine.handle`
rejects a state whose `rulesVersion` or `contentVersion` is not current with a typed `GameError.IncompatibleRun`
instead of running it.

**Order matters** (T1.5a before T1.5b): `Compatibility.admit` and its tests land first and change no behaviour. The
check in `handle` and the version bumps land only after `GameSession.load` calls `admit` (T1.2); landing them earlier
would make every command on an existing 0.6.0 save fail, because today's `GameViewModel.init` never admits a run.

A mid-run 0.6.0 save admitted into a later slice meets that slice's numbers at once. Sweep step 4b measures the one
case where that could hurt (eight heroes under the twelve-hero threat numbers) and decides whether an admitted run
keeps the old threat numbers until its next siege.

Two mechanical guards so the numbers cannot be forgotten again: `VersionFingerprintTest` pins a hash of the launch
catalog's IDs and numbers per content version and a hash of `BalanceConfig.DEFAULT` per balance version; either test
fails until the version is raised. `@SerialName` is pinned to the discriminator strings already stored for
`WeaponLocation` and its subclasses (they are fully qualified class names today, so a package move would break every
save).

The v1 fixture (`core/src/test/resources/saves/v1_forced_seed4242_day61.json`) is kept forever; one fixture per
schema version is added at each gate. Two tripwires assert `SCHEMA_VERSION == 1` today and move with it:
`MigrationTest` (line 123) and `EventCompactionTest` (line 135).

Legacy profile: `LegendEntry` gains `affixes`, `flaws`, `catalystId`, `signatureId`, `ownerLine` and `weaponKey` (all
defaulted) in T4.5. Entries written before the update keep empty lists and are shown as "properties lost to time",
which is the honest rendering of what was never recorded.

### 6.8 Configuration groups: X09, E02

New numbers go into nested objects, never flat fields: `CustomerConfig` (`BalanceConfig.customers`: population,
seats, willingness, seat weights, patronage, wants, sidegrade tolerance, threat utility, commission band weights) and
the three groups that exist (`heroLife`, `weaponFates`, `legacyTracks`). T3.1 moves the seven existing flat customer
and population fields into `CustomerConfig`, which frees nine slots. Catalyst numbers are content fields on
`MaterialDef`. `BalanceConfigTest.constructs` instantiates the class so an overfull constructor fails in the suite,
not at launch.

---

## 7. Milestones and implementation tasks

Order comes from dependencies: nothing is presented before it can be saved safely (M1), nothing is drawn before it is
recorded (M2 core before M2 app), the town does not grow before selection is fair and measurable (M0 metrics and bots,
then M3), and demand is tuned only on the fair, larger town (M4). Scope is relative (S / M / L); no calendar is promised.

Owners: **INT** integrator, **CA** core worker A (market, customers), **CB** core worker B (battle, legacy, gazette,
journal, world events), **SIM** simulator worker, **AA** app worker A (shop day, sheets), **AB** app worker B (panels,
navigation, accessibility), **ART** asset worker, **CON** content worker, **REV** independent reviewer. The
"Who edits what" column names the owner of every file a task touches; the INT part of a task is its contract commit
(8.2). Paths are abbreviated: `core/` = `core/src/main/kotlin/com/tinyblacksmith/core/`,
`coreTest/` = `core/src/test/kotlin/com/tinyblacksmith/core/`, `app/` = `app/src/main/java/com/example/blacksmithproject/`,
`appTest/` = `app/src/test/java/com/example/blacksmithproject/`, `appAndroidTest/` = `app/src/androidTest/java/com/example/blacksmithproject/`.

Every milestone block states: objective, before and after for the player, save and migration effect, what changes
outcomes (RNG or economy) against what is presentation or neutral, commands, integration boundary, exit gate, and
fallback. "Neutral" always means: `GoldenStateTest` (RNG stream states and the gameplay projection) is unchanged.

### M0. Landing zone and baseline (S, low uncertainty)

**Objective:** a reproducible starting line, the measuring tools and the seams later slices need.
**Before / after for the player:** none. **Save effect:** none. **Outcome-changing:** nothing; T0.4 and T0.7 are
neutral (the simulator's 1,000-seed table is reproduced exactly; the golden projection is unchanged).
**Commands:** section 9.1; `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --noImpact --customers --json <file>"` at base seeds 1, 10001, 20001.
**Integration boundary:** no contract file changes. T0.4 and T0.7 touch `core/sim/*` only, T0.5 `app/` only, T0.6
tests only, so they run in parallel worktrees and merge in any order.

| Task | Who edits what | Needs | Tests and checks | Done when |
|---|---|---|---|---|
| T0.1 Ledger and re-baseline | INT: `docs/MAJOR_UPDATE_LEDGER.md` (exists since planning), `docs/PROGRESS.md`, `docs/IMPLEMENTATION_PLAN.md` (phase "P7b") | none | every command of 9.1 run on the current head | baseline rows carry the commit hash |
| T0.2 CI | INT: new `.github/workflows/ci.yml` (`actions/setup-java` for JDK 21 and JDK 25, Android SDK platform 37 and build-tools installed, then `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`); new `appTest/ManifestTest.kt` (no permissions; no network library on the runtime classpath) | none | a green run on GitHub for the commit; whether a stock runner provides both JDKs is unverified, so the row is done only when the run is green | E05, E07 closed |
| T0.3 Checklist and wording accuracy | INT: `docs/GDD_CHECKLIST.md` (C01-C09, C12 as in 3.4), `docs/PROGRESS.md` and `docs/IMPLEMENTATION_PLAN.md` (every "Known limitations" and "P7" line re-read against 0.6.0), wording "hand-made" in `CLAUDE.md`, `docs/PROGRESS.md` and the `generate_assets.py` manifest (X16), `docs/DECISIONS.md` note on the GDD departures of 2.5 | none | a ten-row table in the ledger, one per review-9 item, with the checklist line before and after | no document says Known Name is unchanged or the 10,000-seed review is pending; no ticked line claims more than its evidence row |
| T0.4 Simulator metrics and overrides | SIM: `core/sim/Simulator.kt`, new `core/sim/Metrics.kt`; `--customers`; `--set key=value[,key=value]` implemented as a hand-written `when (key)` over an allowlist (no reflection: `:core` has no `kotlin-reflect` and the simulator ships in `main`); the metric set of 9.3 | none | `coreTest/SimulatorMetricsTest.kt`: metrics on a fixed seed equal hand-counted values; `metricsDoNotChangeAnyRun` | the 1,000-seed table of 2.3 is reproduced exactly at seed 1; the M0 baselines of both band tables in 4.3 are measured and written into this plan and `docs/DECISIONS.md` ("Customer baseline before the shop-day update") |
| T0.5 App seams | AB: new `app/data/GameRepository.kt` (the interface and `StoredRows` of 6.1; `SaveStore` implements it by returning its rows), new `appTest/FakeGameRepository.kt`, `app/GameViewModel.kt` (constructor injection, `companion object { val Factory }`), `app/MainActivity.kt`, `app/build.gradle.kts` (`testImplementation(libs.kotlinx.coroutines.test)`, `testImplementation(libs.kotlin.test)`); app tests use `org.junit.Test` with `kotlin.test` assertions; `Modifier.testTag` on navigation items, End Day and dialog buttons with `testTagsAsResourceId` at the root; a debug-only launch extra `seed` | none | `./gradlew :app:testDebugUnitTest` runs one smoke test on the fake; `tools/emulator/smoke.sh` still passes with a `tap_id` helper beside the text anchors | a fixed-seed run can be started by script; no behaviour change |
| T0.6 Golden gameplay projection | INT: new `coreTest/GoldenStateTest.kt`, `core/src/test/resources/golden/state_rules1.txt` (handoff) | none | the test passes on the head, twice | later "changes nothing" claims have something to compare against |
| T0.7 Bots | SIM: new `core/sim/Policies.kt`: ADVANCED_SMITH, TECHNIQUE_TEMPER / QUENCH / ETCH, REQUEST_DRIVEN (commission-led; reads wants from T4.1 on), SIEGE_PREP, SIGNATURE_PURSUIT (makes Anvil Lore measurable), SCARCE_RECIPE (buys and repeats the best rare recipe it has seen: Caravan Ties, Lucky), EXPERT (the tripwire bot of 4.3), SPENDTHRIFT, NOVICE, BROKE_START, FREE_LISTINGS (prices everything at 0); `--blessing <first, energy, quality, sales, patronage or defense>`; `--eras N --buy <cheapest, walls or track=...>` plays eras in sequence with a growing Legend Board and counts genuine cross-era artifact returns (Homing Steel) | T0.4 | `coreTest/SimulatorPolicyTest.kt`: each bot issues only legal commands, uses Advanced Forge, techniques and catalysts as named, and is deterministic; `multiEraReturnsComeFromARealEarlierEra` | G05, X21 closed over the mechanics that exist today: every one of the eleven tracks has a non-zero yardstick under at least one bot |

**Exit gate M0:** ledger and green CI; baseline recorded; both band tables of 4.3 re-issued with measured M0 values;
the EXPERT, REQUEST_DRIVEN and SIGNATURE_PURSUIT rows recorded as the "before" of every later gate.
**Fallback:** every task is additive and can be reverted alone.

### M1. Trustworthy state and reports (M, low to medium uncertainty)

**Objective:** nothing the player does can lose a save, a purchase or a new era; a day's report, archive and tallies
agree and count money truthfully; requests say what they require.
**Before:** tapping two upgrades quickly can lose one; a corrupt save crashes on every launch; after Claim a killed
app forgets the run-end screen; the day report omits the blades you forged that day; "Fine" on a request means
"quality 59 or more"; "2 of 1 visitors bought".
**After:** every purchase survives any tap order; a bad save opens a recovery screen with Retry; run end survives a
kill; the report lists everything that happened that day once; a request says "a fine spear (quality 50+)" and each
blade says "fits" or what is missing; the till shows sales, bonus, commission, collector and tribute separately.
**Save effect:** schema 2 with one real migration step; `balanceVersion`; rules 2; a v2 fixture.
**Outcome-changing:** T1.6 (collector cap), T1.7 (champion choice), T1.8 (commission order and pick), T1.9 (faction
tie-break, ore-merchant stock). **Neutral:** T1.1-T1.3, T1.4 (only the report changes), T1.5a.
**Commands:** `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`; `:app:installDebug`;
`tools/emulator/smoke.sh`, `runend.sh`; `:app:connectedDebugAndroidTest`; the 1,000-seed sweep before and after.
**Integration boundary:** branch `shop-day/m1`. Order: T1.5a, then the app chain T1.1, T1.2, T1.3 alongside the core
work T1.4 and T1.6; T1.5b after T1.2; then T1.7, T1.8, T1.9. CA and CB work in worktrees on disjoint packages; AB only
in `app/`.

| Task | Who edits what | Needs | Tests | Done when |
|---|---|---|---|---|
| T1.5a Versions, part 1 (first; neutral) | INT: `core/model/Model.kt` (`balanceVersion: Int = 0`; `@SerialName` on `WeaponLocation` and its subclasses, pinned to the stored strings), `core/persistence/SaveCodec.kt` (separate `runMigrations` and `legacyMigrations`, still schema 1), new `core/engine/Compatibility.kt` (`admit`, pure, not yet called), `core/engine/Invariants.kt` (returns the extended list: content references, RNG streams present, champion uniqueness and health, hero and weapon bounds, `phase == ENDED` iff integrity 0) | T0.6 | `coreTest/CompatibilityTest.kt`: `theV1FixtureIsAdmittedWithoutTouchingHistory`, `aRunFromNewerRulesIsUnsupported`, `anUnknownContentIdIsUnsupportedNotACrash`, `aMissingRngStreamIsReported`; `coreTest/MigrationTest.kt`: `aRunStepIsNotAppliedToTheLegacyDocument`, `weaponLocationDiscriminatorsArePinned`; `coreTest/VersionFingerprintTest.kt`: `catalogFingerprintMatchesItsVersion`, `balanceFingerprintMatchesItsVersion`; `coreTest/BalanceConfigTest.constructs` | X08, X09, X10 closed; golden projection unchanged; the app behaves exactly as before |
| T1.1 `GameSession` | INT: new `app/GameSession.kt` (6.1), `app/data/GameRepository.kt` (`SaveFailure`, `saveCursor`, `quarantine`), `app/data/SaveStore.kt` (raw rows; SQLite exceptions mapped to `SaveFailure.Io`) | T0.5 | `appTest/GameSessionTest.kt`: `twoUpgradesIssuedTogetherBothSurviveAndOnlyOneCommitIsEverInFlight` (both arrival orders), `upgradeIssuedBeforeBeginEraNeverClearsTheNewRun`, `endDayQueuedAgainstAnOldRunIsStale`, `doubleBeginEraCreatesOneRun`, `doubleEndDayCommitsOnce`, `doubleClaimAwardsOnce`, `runEndSurvivesRecreationAfterClaim`, `nothingIsPublishedUntilTheSaveCompletes`, `anEndDayReplayAfterClaimNeverRewritesTheLegacyRow`, `buyUpgradeDuringALiveRunIsRejected`, `claimOnALiveRunIsRejected`, `beginEraBeforeClaimIsRejected`, `anEngineFaultIsNotRetried`, `aSecondLoadCannotPublishOverANewerSnapshot` | F01, X03 closed at the session level |
| T1.2 Failure and recovery | INT: `GameSession.load` (decode, migrate, `Compatibility.admit`), `retry`, `startOverKeepingBackup`, the confirmation read of rule 5. AB: new `app/ui/Failures.kt` (`LoadFailedScreen`, `SaveFailureDialog`), `app/ui/App.kt`, `app/src/main/AndroidManifest.xml` (`allowBackup="false"`), `app/data/SaveStore.kt` (`exportSchema = true`, schema directory) | T1.1, T1.5a | `appTest/GameSessionFailureTest.kt`: `corruptRunKeepsItsBytesAndReportsCorrupt`, `newerSchemaIsReportedAsNewer`, `incompatibleRunIsReportedWithItsProblems`, `failedCommitLeavesTheLastSnapshotAndRetrySucceeds`, `aCommitThatLandedBeforeFailingIsDetectedByTheConfirmationRead`, `retryGivesTheSameResolutionAsAFirstSuccess`, `cancellationDuringCommitNeverPublishesAnUnsavedState`, `startOverQuarantinesInsteadOfDeleting`; `appAndroidTest/SaveStoreTest.corruptPayloadIsReportedAndTheRowIsUntouched` | F02, X01 closed: a hand-corrupted row on the emulator opens the recovery screen (screenshot) and Retry works after the row is restored |
| T1.3 ViewModel on the session | AB: `app/GameViewModel.kt` (`UiState` gains `LoadFailed` and `op` on `Title` / `Playing` / `RunEnded`; `RunEnded` keeps the ended `GameState`; `SavedStateHandle` for destination, forge draft, reveal ID, open sheet; the dead `continueRun` path removed), `app/ui/RunEndScreen.kt` (Claim, upgrades, Begin era disabled while working; failure shown), `app/ui/WorkshopScreen.kt` (`BackHandler`), `app/ui/Dialogs.kt` (back and outside tap no longer mean "Begin day") | T1.1, T1.2 | `appTest/SavedStateTest.destinationDraftAndRevealSurviveRecreation`; `appTest/GameViewModelTest.backNeverAcknowledgesAReport` | X02 closed; `tools/emulator/runend.sh` passes with a `force-stop` after Claim: run end reopens with upgrades available |
| T1.5b Versions, part 2 (after T1.2) | INT: `core/engine/GameEngine.kt` (`RULES_VERSION = 2`; `handle` rejects a state whose rules or content version is not current with `GameError.IncompatibleRun`), `core/persistence/SaveCodec.kt` (`SCHEMA_VERSION = 2`; run step 1 to 2 stamps `balanceVersion`), the two tripwires (`MigrationTest` line 123, `EventCompactionTest` line 135), `TestSupport` and every test that loads the v1 fixture call `Compatibility.admit` before `handle`, `state_rules2.txt` recorded, seed-pinned tests re-recorded | T1.2, T1.5a | `CompatibilityTest.handleRejectsAnUnadmittedOlderRun`, `admittedFixtureAcceptsAnEndDay`; `MigrationTest.v1VisitReasonsDecode`; a v2 fixture checked in | F09, E01 closed; an existing 0.6.0 save on the emulator loads, is admitted and accepts an End Day |
| T1.4 Complete day edition | INT: `core/engine/GameEngine.kt` `endDay` (one line: `dayEvents` from the day's records, before compaction). CB: `core/gazette/Gazette.kt` if a tally assumes End Day records only. AB: `app/ui/HomePanel.kt`, `InfoPanels.kt` (`GazettePanel`), `Dialogs.kt` read one edition; for a `recordVersion == 0` day they read `eventsForDay` (6.4) | none | `coreTest/DayEditionTest.kt`: `theDayReportCarriesPreparationRecordsExactlyOnce`, `reportAndArchiveAreTheSameEdition`, `aSignatureForgedWhilePlanningLeadsThePaper`, `anOldSavesLastReportIsBuiltFromTheArchive` | F03 closed; golden projection unchanged |
| T1.6 Typed money, field results, locale | INT: `core/model/ShopRecord.kt` (`Sale`, `ShopLedger`, `IncomeKind`, `FieldResult`, `FieldOutcome`), `core/engine/ResolutionContext.kt` (`earn(kind, amount)`, `field`), `core/engine/GameEngine.kt` (`MATERIAL_BOUGHT` record, `cost` on `TOOL_BOUGHT`). CA: `core/market/Market.kt` (`purchase` returns `Sale`). CB: `core/battle/Battle.kt` (`FieldResult` on every outcome including death; the roll kept; no formatted decimal in payloads; tribute through `earn(TRIBUTE)`), `core/heroes/Heroes.kt` (field results for patrol, rest, hall and ambition days), `core/engine/WorldEvents.kt` (collector capped at `askingPrice x collectorPriceMultiplier`, typed payment), `core/gazette/Gazette.kt` (tally from ledger and field results; tribute on its own line). SIM: `core/sim/Simulator.kt` (`goldEarned` from the ledger) | T1.4, T1.5b | `coreTest/ShopLedgerTest.kt`: `saleWithBonusAndTradeInSplitsCoinCreditAndBonus`, `commissionOnlyDayCountsNoBrowserAsBuyer`, `collectorPurchaseIsItsOwnKind`, `theCollectorNeverPaysAboveTheGoingRateTimesItsMultiplier`, `aFreeBladeIsASaleOfZeroCoinAndTheLedgerBalances`, `fatalExpeditionCountsAsALoss`, `tributeIsNotShopTakings`, `ledgerBalancesOnEveryDayOfTwoHundredRuns`; `coreTest/LocaleDeterminismTest.encodedStateIsEqualUnderEnglishGermanAndTurkishLocales` | F07, F11, X04, X05 closed |
| T1.7 Champion context | CB: `core/battle/Battle.kt` `selectChampions(ctx, faction, elite: Boolean = false)`; callers `outlook` and the siege pass the warlord flag. INT: `GameEngine.recover` passes it | T1.5b | `coreTest/ChampionSelectionTest.kt`: `aGiantSlayerBearerEntersTheTopThreeOnlyForAWarlordSiege`, `rankingAndContributionUseTheSameFoeContext`, `townChampionIdsAgreeWithTheForecast` | F08 closed |
| T1.8 Commissions | INT: `core/engine/GameEngine.kt` (commissions before shelf visits), `core/config/BalanceConfig.kt` (`ambitionCollectorQuality` 50; band weights in a nested group), the off-band conversion added to the schema-2 run step. CA: new `core/market/Commissions.kt` (`QualityBand`, `fit`, `pick`), `core/market/Market.kt` (`maybeOfferCommission` draws a band; `resolveCommissions` uses `fit` and the least-sufficient pick, storage first). CB: `core/engine/WorldEvents.kt` (noble = superb). AB: `app/ui/Labels.kt` (reads `QualityBand`), `MarketPanel.kt` and `HomePanel.kt` (readiness line; per blade "fits" or the missing criterion) | T1.5b | `coreTest/CommissionRulesTest.kt`: `aBladeOneQualityShortIsRefusedAndTheMissingCriterionIsNamed`, `everyVisibleBandBoundaryMatchesTheRule`, `deliveryOnTheDeadlineDayCounts`, `aSoleCandidateIsDeliveredBeforeBrowsersArrive`, `theLeastSufficientBladeIsHandedOver`, `storageIsPreferredOverTheShelf`, `familyAndElementMismatchesAreReportedSeparately`, `anOpenOffBandCommissionIsLoweredOnLoad` | F04, F06 closed; a request card on the emulator names the blade it will take (screenshot) |
| T1.9 Small engine corrections | INT: `GameEngine.recover` (faction tie by ID), `GameEngine.newMorning` (applies the ore merchant's day-keyed bonus after the restock). CB: `Battle.outlook`, `Battle.warnOfSiege` (tie by ID), `core/engine/WorldEvents.kt` `ore_merchant` (writes the bonus as a day-keyed entry, as `FLAG_FESTIVAL` does) | T1.5b | `coreTest/DeterminismTest.factionTieIsBrokenById`; `coreTest/WorldEventsAndGenerationsTest.theOreMerchantsStockIsOnSaleTheNextMorning` | X11, X18 closed |

**Exit gate M1:** every test above; the 1,000-seed sweep before and after T1.6-T1.9, with each policy mean within 0.6
days of the pre-M1 run or a `docs/DECISIONS.md` entry naming the task that moved it; device: a kill during End Day and
after Claim both reopen correctly; an existing 0.6.0 save continues; independent review.
**Fallback:** T1.7, T1.8 and T1.9 are each one function and revert without touching T1.1-T1.6. If the schema step
misbehaves it is corrected in place before the gate: no schema-2 save exists outside development until M1 merges.

### M2. The shop day (L, medium to high uncertainty: art on device and comprehension)

**Objective:** End Day opens the shop; the player watches real customers and transactions, sees a few consequences
and one lead, and can open any hero or blade.
**Before:** End Day shows a paper; a sale is one bullet; affix text exists only in the forge reveal; seven tabs.
**After:** section 1.2; every hero and blade opens a sheet from anywhere; four destinations.
**Save effect:** schema 3 (a no-op step, because the visit record adds enum constants and event types an older build
must not misread); a cursor row; defaulted fields only. **Outcome-changing:** nothing. T2.1 is proven neutral by the
golden projection; everything else is presentation.
**Commands:** `python tools/pixelart/import_assets.py` (ART only, T2.4); `./gradlew :core:test :app:testDebugUnitTest
:app:assembleDebug :app:connectedDebugAndroidTest`; `tools/emulator/smoke.sh`, `runend.sh`, `shopday_kill.sh`,
`layout.sh`, `scenarios.sh`.
**Integration boundary:** branch `shop-day/m2`. INT lands the T2.1 model fields, the capture hook in `endDay` and the
T2.2 signatures as the contract commit; ART works independently from the start of M1; AA owns `ui/shopday` and
`ui/detail`, AB the existing panels.

| Task | Who edits what | Needs | Tests | Done when |
|---|---|---|---|---|
| T2.1 Visit record | INT: `core/model/ShopRecord.kt` (6.2), `core/model/Model.kt` (`MarketVisit` and `DayResolution` additions), `core/engine/GameEngine.kt` (`shopWeapons` and `shelfPrices` captured at the top of `endDay`; `SHOP_DAY` record; `recordVersion = 1`), `SaveCodec` schema 3. CA: `core/market/Market.kt` (`Evaluation` gains four booleans; capture in `resolveShelfVisits` and `resolveCommissions`; a patron is not seated as a browser the same day). CB: `core/engine/WorldEvents.kt` (`WorldEventOutcome.visit`), `core/gazette/Gazette.kt` (reads `reason.name`). SIM: `core/sim/Simulator.kt` (reads `reason.name`). AB: the two call sites that stop compiling, `HomePanel.kt` (`Gazette.visitReason(it.reason)`) and `MarketPanel.kt` (`reasonLabel(v.reason)`), in the same merge | T1.6, T1.8 | `coreTest/ShopRecordTest.kt`: `captureLeavesTheRngStreamsAndTheGameplayProjectionUnchanged`, `snapshotsShowTheCounterNotTheEvening`, `aRefusalNamesTheBladeThatWasTooDear`, `theCommissionPatronIsAVisitOfItsOwnKind`, `aShelfBladeTakenByAPatronIsStillInShopWeapons`, `aPatronAppearsOncePerDay`, `aTenVisitorDayEncodesUnderTwelveKilobytes`, `merchantResaleIsNotAShopVisit` | S04, S05, X14 closed |
| T2.2 Script, lead, demand summary | CB: new `core/shopday/ShopDay.kt` (`script`, featured and aftermath rules), `core/shopday/Advice.kt` (`lead`), `core/shopday/Lines.kt` (templates over typed reasons), `core/shopday/Demand.kt` (`summary(state)`: unarmed heroes, worn blades, classes with no fitting listed blade, how many living heroes can afford the cheapest and the median listed blade) | T2.1 | `coreTest/ShopDayScriptTest.kt`: `featuredPlusTallyIsEveryVisitExactlyOnce`, `aCommissionAndACollectorNeverSqueezeOutThePurchaseAndTheRefusal`, `oneRefusalPerDistinctReason`, `tenVisitorsFeatureThree`, `aDayWithNoLivingHeroesIsOneQuietCard`, `anEmptyShelfNamesWhoLookedIn`, `aDayRecordedBeforeSnapshotsIsATallyNotACrash`, `aftermathLeadsWithTheSiege`, `theCounterfactualLineAppearsOnlyWhenRecorded`, `everyLineMapsToARecordedField`, `scriptIsAPureFunctionOfItsInputs`; `coreTest/AdviceTest.kt`: one test per `LeadKind` and `firstMatchWins`; `coreTest/DemandTest.kt` | the script of the fixture's day is identical across runs and JVMs |
| T2.3 Cursor | INT: new `core/persistence/DayCursor.kt`, `app/data/SaveStore.kt` (cursor row), `app/GameSession.kt` (rules 8 and 9, `pending`), `app/GameViewModel.kt` (`UiState.ShopDay(state, script, position, speed, sheet, gazetteOpen, resumed, op)`; persists the old `dismissed_report` key as `DONE` once) | T1.1, T2.2 | `appTest/PresentationEqualityTest.kt`: `watchingSkippingAndRestartingLeaveIdenticalRunAndLegacyBytes`, `noCommandOtherThanChooseBlessingIsIssuedByTheSequence`, `blessingChosenInTheSequenceEqualsChosenNextMorning`, `aLostCursorWriteOnlyRepeatsAVisit`, `planningIsRefusedImmediatelyAfterTheCommitWithAStaleCursor`, `aFailingCursorRowNeverLocksTheBlessingOrTheNextDay`; `appTest/ProcessDeathTest.kt`: `killBeforeCommitThenEndDayAgainGivesTheSameDay`, `killAroundAForgeCommitConsumesMaterialsOnceAndTheBladeIsFoundAfterRelaunch`, `killAfterCommitResumesAtTheFirstBeat`, `killMidCounterResumesAtTheStoredVisit`, `acknowledgedDayOpensPlanningOnTheNextDay`, `defeatDayResumesThenEndsTheRun`; `appTest/CursorTest.kt`: `cursorOfAnotherDayIsIgnored`, `oldDismissedReportKeyIsPersistedAsDone` | S06 closed |
| T2.4 Assets and sprite API for the scene | ART: `tools/pixelart/import_assets.py` (5.5), `tools/pixelart/cells/*.json` (moved from the evidence folder), `tools/pixelart/overrides.json`, generated `app/ui/PortraitArt.kt`, `app/src/main/res/drawable-nodpi/` (`bg_counter_forge`, the 25 re-sliced portraits, three atlas icons, the two V3 270x150 pieces, and for the gate the v2 class tiles both keyed and as tiles), new `tools/pixelart/requirements.txt`, `app/ui/Sprites.kt` (`portrait(appearanceKey, classId)`, signature level bump and ring, `badge_flaw`, the six unused faction `_alt_` sprites), `docs/ART_BRIEF.md`, `docs/ART_MANIFEST.md`, `docs/art_contact_handmade.png` renamed `docs/art_contact.png` | none (may start with M1) | importer self-checks, 25 of 25 portraits: one vertical run, no opaque pixel in the outer 1 px ring, content box recorded; duplicate-ID error; `appTest/PortraitArtTest.everyLegacyKeyResolves` | the contact sheet is reviewed by INT; `git status` shows only generated files and the importer changed; **the gate screenshot of 5.3 exists and the owner's choice of mode and the count of reference tiles that passed are in the ledger** |
| T2.5 Counter screen | AA: new `app/ui/shopday/ShopDayScreen.kt`, `CounterScene.kt`, `VisitCard.kt` (`ReceiptRows`), `DayCards.kt` (`ShopOpenCard`, `TallyCard`, `ShopCloseCard`, `QuietDayCard`), `ShopDayControls.kt`, `ShopDayUi.kt` (immutable UI models, beat lengths, `ShopDayScript.toUi`); all stateless: data in, events out, `modifier` last. AB: the six palette constants in `app/ui/theme/Color.kt` | T2.2, T2.3, T2.4 | `appAndroidTest/ShopDayScreenTest.kt`: `purchaseShowsBuyerReasonAndSeparateReceiptRows`, `refusalShowsTheTypedReasonWithItsNumbers`, `noVisitorsIsOneCardAndOneTap`, `emptyShelfListsEachNameOnce`, `tenVisitorsFeatureThreeAndTheTallyCoversTheRest`, `backStepsABeatAndNeverAcknowledges`, `nothingAdvancesByItselfAtTheDefaultSpeed`, `reducedMotionNeverAutoAdvances`, `autoAdvancePausesWhileASheetIsOpen`, `outcomeIsVisibleOnTheFirstFrameOfABeat`, `skipLandsOnTomorrowBlessingOrFallen`, `theSceneIsTheTallestNode`; `appTest/NoRandomInUiTest` (source scan) | device screenshots of every beat sent to the owner |
| T2.6 Aftermath, Tomorrow, endings | AA: `app/ui/shopday/DayCards.kt` (`AftermathCard` with its kinds: fight, death, title, inheritance, merchant resale, hall lesson, scarce loot, siege; `FallenCard`, `TomorrowCard`, `ResumePrompt`, `BlessingChoices`), "Watch the fight" for 0.6.0 expedition replays (`ReplayKind.EXPEDITION` by `eventId`) as well as the siege, the Gazette as an overlay reusing `EditionBody`. AB: `app/ui/Dialogs.kt` (`DayReportDialog` retired) | T2.5 | `ShopDayScreenTest`: `aftermathNamesHeroAndBlade`, `anInheritedBladeNamesBothGuildmates`, `aMerchantResaleIsNotShownAsAShopSale`, `replayOutcomeIsNeverGatedOnATimer`, `tomorrowShowsOneLeadAndItsReason`, `fallenLeadsToRunEnd` | a siege day and the final day recorded on the emulator |
| T2.7 Hero and item sheets | AA: new `app/ui/detail/HeroDetailSheet.kt`, `ItemDetailSheet.kt` (affixes and flaws with descriptions; recipe; history newest first; current holder; price field weighted, not `width(112.dp)`; stock actions in Planning only) | T2.1, T2.4 | `appAndroidTest/DetailSheetTest.kt`: `heroSheetShowsTasteGuildMentorAndPurse`, `itemSheetShowsAffixDescriptionsAndHistory`, `aBladeNoLongerInTheSaveOpensFromItsSnapshot`, `snapshotFirstThenNowWhenTheyDiffer`, `stockActionsAreReadOnlyInTheShopDay` | U04, U06, G08 closed; E5 has its surface |
| T2.8a Four destinations | AB: `app/GameViewModel.kt` (`Dest { SHOP, FORGE, TOWN, RECORDS }`), `app/ui/WorkshopScreen.kt` (four items, fixed label style, gear, End Day sublabel ladder, back to Shop), new `SettingsSheet.kt` (reduced motion, shop-day speed, version), `RecordsPanel.kt` (News, Journal, Legacy with all eleven tracks, as lazy sections) | T1.3 | `appAndroidTest/NavigationBarTest.fourDestinationsKeepTheirLabelSize`, `RecordsPanelTest.threeSegmentsKeepTheirContent` | A04, A08 closed |
| T2.8b The Shop destination | AB: new `app/ui/ShopPanel.kt` (replaces `HomePanel.kt` and the top of `MarketPanel.kt`, whose row expander goes: counter header with the live shelf and "Seats n", `LeadCard`, requests with readiness, **"Who is buying"** from `Demand.summary`, yesterday at the counter, shelf rows, "Storage · n" row), new `LeadCard.kt`, `StorageSheet.kt` (lazy, keyed); UI models built in the ViewModel on `Dispatchers.Default` | T2.8a, T2.2, T2.7 | `StorageSheetTest.twoHundredFiftyWeaponsComposeOnlyVisibleRows`, `ShopPanelTest.requestsAndYesterdaySitAboveTheShelf`, `ShopPanelTest.theLeadIsTheSameAsTheTomorrowCard`, `ShopPanelTest.demandBlockCountsUnarmedWornAndPurseBands` | U02, U03, U05 (first half), A06 closed |
| T2.8c Forge, Town, Supplies | AB: `ForgePanel.kt` (backdrop header at 120 dp or more, `weight` instead of `width(116.dp)`, Supplies and Journal entry points, "Forge this" context line), new `SuppliesSheet.kt` (supplier with Caravan Ties stock and the ore merchant's extra stock marked; workshop tools), `InfoPanels.kt` (`TownPanel` lazy; rows open the hero sheet; guild, mentor and Known Name regulars on the row) | T2.8a, T2.7 | `ForgeShortcutTest.forgeThisPrefillsTheDraftFromARequest`, `LayoutMatrixTest.noOverflow` (first version: Shop, Forge, item sheet) | A05 (first pass), A10 (Forge header) closed |
| T2.9 Device scripts and scenario saves | INT: `tools/emulator/smoke.sh` (resource-ID anchors; screenshots of open, first visit, receipt, close, aftermath, tomorrow; day 2 with Skip day; day 3 with `am force-stop` on the second beat; day 4 with HOME and `am kill`), `runend.sh` (Skip day, Decide later, Fallen, a kill after Claim), new `shopday_kill.sh` (kills at 0 / 50 / 150 / 400 ms after End Day), `layout.sh` (9.5), `scenarios.sh`; a debug-only launch extra `scenario=<file>`. CB: new `coreTest/ScenarioSaves.kt` writes one save per case to `build/scenarios/*.json`: a hall day with a lesson; a death that leaves a blade to a guildmate; a merchant holding a blade and a buyer for it; a Known Name regular on day 1; Caravan Ties stock; a returned legend; **one blade followed through a sale, three fights and a change of owner** | T2.5, T2.8b | the scripts end with their `*_DONE` markers and every check "ok"; every scenario screenshot is filed as **constructed**, beside the simulator's natural frequency per run (0.6.0 at 10,000 seeds: lessons 2.4-9.0, inheritance 0.06-0.10, merchant resale 0.01-0.02, wall death 0) | X13, X20 closed; gold on the Tomorrow card equals gold on the Shop after "Begin day" |
| T2.10 Onboarding | AB: `ShopPanel` day-1 lead (`LeadKind.FIRST_BLADE`), one coach line on the first counter visit ("Tap to continue · Skip day"), `SettingsStore.seenTips` reused | T2.8b | `ShopPanelTest.dayOneLeadsWithForgeYourFirstBlade`; a fresh-install recording | A01 closed |

**Exit gate M2:** all tests; the device loop of T2.9 at the default size and at 720x1280; the scenario list of 9.5
screenshotted; `PresentationEqualityTest` green; the art gate of T2.4 signed off; a comprehension check with at least
three first-time players (the formal five-person gate is T7.2): at least two of three answer Q1 and Q2 of 9.7
correctly; independent review. If no player is available the check is recorded as blocked, not passed.
**Fallback:** the art gate's own steps 2 and 3 (5.3); backdrop alternative B if the recommended crop fails on device.
If fewer than two of three pass Q1 and Q2, the shop day ships with the Gazette overlay as the default view and the
counter behind "Watch the day" while it is fixed: the overlay alone is a complete, correct report.

### M3. More and varied customers (L, high balance uncertainty)

**Objective:** a town of twelve with fair turns at a six-seat counter, names and faces that do not repeat, and
regulars the game recognises.
**Before:** 3.5 visitors a day from 8 heroes, the same four served most; "Mira Ashwood" and "Mira Vance" in one
town; two heroes with one face. **After:** 5-6 visitors a day (6-7.5 with the Signboard), every willing hero served
within the waiting bound, no shared name among the living, a saved face per hero, "Wren, a regular, last in on day 6".
**Save effect:** rules 3; schema 4 (no-op step); content 3; balance 6; defaulted hero fields.
**Outcome-changing:** T3.1 (every seed), T3.4, T3.6, T3.8. **Neutral for gameplay streams:** T3.2 keeps the name
draw count; T3.3 and T3.5 draw nothing (asserted on the RNG stream states).
**Commands:** `./gradlew :core:test`; the sweep steps 1-5 of 4.3 through `--set`; the device loop.
**Integration boundary:** branch `shop-day/m3`. INT lands `CustomerConfig`, the hero and lineage fields,
`HeroClassDef.appearances` and `IdOrder` first.

| Task | Who edits what | Needs | Tests | Done when |
|---|---|---|---|---|
| T3.1 Fair selection | INT: new `core/model/IdOrder.kt`; `core/engine/ResolutionContext.kt` and `core/model/Model.kt` (`aliveHeroes` in numeric order; hero counters; `DayResolution.turnedAway`); `core/config/BalanceConfig.kt` (new `CustomerConfig` holding the seven former flat customer and population fields at today's values, plus the inline visit constants). CA: `core/market/Market.kt` (`resolveShelfVisits` per 4.2; `resolveCommissions`, `Commissions.pick` and `resolveMerchant` on `IdOrder.numeric`). CB: `core/battle/Battle.kt` (champion tie-break on `IdOrder.numeric`) | T0.4, M1 | `coreTest/CustomerSelectionTest.kt` (every contract line of 4.2); `WorldEventsAndGenerationsTest`'s max-visit assertion and `GameplayDepthTest.theSignboardLetsOneMoreCustomerInPerLevel` rewritten against the new rule | F05 closed: sweep step 1 passes and is recorded |
| T3.2 Names and lineage IDs | INT: `core/content/Content.kt` (`validate()` name rules of 4.4), `core/model/Model.kt` (`LineageAnchor.id`, `Hero.lineageId`), `core/engine/GameEngine.kt` (`newRun` matches the descendant by ID). CON: `core/content/LaunchContent.kt` (120 first names, 96 surnames; version 3). CA: new `core/heroes/Names.kt`, `core/heroes/Heroes.kt` `generate`. CB: `core/engine/WorldEvents.kt` (descendants by ID). AB: `app/ui/Dialogs.kt` siege stage (join by hero ID) | T3.1 | `coreTest/NameGenerationTest.kt`: `noLivingPairSharesAFirstNameOrSurnameOutsideOneLineage` (1,000 seeds x 60 days), `noFullNameRepeatsInARun`, `descendantsCarryTheSurnameAndADifferentFirstName`, `descendantsAreMatchedById`, `sameDrawCountAsToday`, `poolExhaustionStillConsumesItsDrawAndReusesTheLongestDeadName`; `coreTest/LaunchContentTest.namePoolsObeyTheAuthoringRules` | N03, X07 closed |
| T3.3 Appearance | INT: `core/content/Content.kt` (`HeroClassDef.appearances`), `core/model/Model.kt` (`Hero.appearance`, `LineageAnchor.appearance`). CON: `LaunchContent` keys (the five legacy IDs per class always; the reference tiles that passed the gate). CA: new `core/heroes/Appearance.kt` (6.6), `Heroes.generate` assigns a key. ART: the import of the tiles that passed, or reviewed hue liveries. AA: `PortraitTile` used by Town, the counter and the sheets | T2.4 (gate result), T3.2 | `coreTest/AppearanceTest.kt`: `legacyKeysArePinned` (h1..h40), `newHeroesNeverShareAFaceWhileAClassHasNoMoreLivingThanFaces`, `aPreUpdateHeroBlocksTheFaceThePlayerSeesOnThem`, `assignmentLeavesEveryRngStreamUnchanged`, `aMissingAssetFallsBackToTheClass`; `appTest/PortraitArtTest.everyContentKeyResolves` | N04 closed with the face count the gate allowed, stated in the ledger |
| T3.4 Population, seats, compensation | INT: `CustomerConfig` values of 4.1; `expeditionSuppression` and `raidPerDay` per the sweep; balance 6, rules 3, schema 4. CA: `Heroes.generate` (class coverage), `Heroes.arrivals` (target and chance), festival seats. SIM: sweep steps 2, 3, 4, 4b | T3.1, T0.4, T0.7 | `coreTest/PopulationTest.kt`: `everyRunStartsWithAllFiveClasses`, `arrivalsRefillTowardTheTarget`, `oneHeroesDrawPerDayForArrivals` | N01, N02 closed: the M3 band of 4.3 holds at 1,000 seeds x 3 base seeds; the tripwire did not fire or the owner has ruled; tables in `docs/DECISIONS.md` ("Balance v6: a larger, fairer town") |
| T3.5 Recognition | CB: new `core/shopday/Recognition.kt` (eligibility and the stateless choice of 4.5), `MarketVisit.recognition` filled at End Day. CON: template strings. AA: `VisitCard` shows the line; `icon_reputation` as the regular pip | T3.1, T2.5 | `coreTest/RecognitionTest.kt`: `everyCueIsJustifiedByItsFields`, `milestonesShowOncePerHeroPerRun`, `recurringLinesRespectTheCooldown`, `atMostThreeAmongTheFeatured`, `choiceLeavesEveryRngStreamUnchanged`; simulator: share of visits with a line (35-50 % from day 6) | N05 closed |
| T3.6 Guild Patronage | CON: the blessing definition. CA: `core/market/Market.kt` (willingness ceiling for guild members; stipend in affordability and on the `Sale`; the old `HERO_VISIT_CHANCE` term removed here and nowhere earlier). INT: `CustomerConfig.patronageStipend`. SIM: sweep step 5 with `--blessing patronage` | T3.4, T0.7 | `coreTest/PatronageTest.kt`: `guildMembersAreWillingAtTheCeiling`, `oneStipendPerMemberPerBlessing`, `theStipendIsOnTheReceiptAndInTheLedger`, `notOfferedWithoutAGuild`; `CustomerSelectionTest.saturationUnderPatronageKeepsServedShareMaxOverMinAtMostTwoAndAHalf` | G03 closed with the variant chosen by step 5's measured rule |
| T3.7 The counter and the Town at the new scale | AA: `TallyCard` with faces; "n found the shop full". AB: Town list density for 16 heroes (fallen under a collapsed header) | T3.4, T2.5 | `ShopDayScreenTest.dayLengthAtOneSpeedOverTheFixtureDays` (virtual clock: median at most 25 s, p90 at most 30 s at 1x with no taps, on ten recorded days of 6-10 visitors) | one tap still skips any day |
| T3.8 Wall deaths: reachable or removed (last task of M3) | CB: `core/battle/Battle.kt` (`resolveSiegeIfDue`). INT: `BalanceConfig.weaponFates` (`wallsRoutDamage` start 55, `wallsRoutRatio` start 1.5). SIM: sweep 40 / 50 / 55 / 60 | the M3 band met | `coreTest/SiegeWallTest.kt`: `aNarrowLossNeverKillsAHealthyChampion`, `aRoutCanKillAChampionAtTheThreshold`, `theFallenChampionsBladeTakesAWallsFate` | X19 closed either way: the rout rule is adopted only if deaths per hero-day and BALANCED_FAIR stay inside the M3 band; otherwise the unreachable branch and its two odds are deleted and `docs/DECISIONS.md` and the Gazette text say champions are wounded, never killed, at the wall |

**Exit gate M3:** the M3 band of 4.3 at 1,000 seeds x 3 base seeds, row by row beside the M0 values and the
10,000-seed v5 baseline; the EXPERT tripwire reported; fairness and name tests; the X19 decision recorded; device
recordings of a ten-visitor festival day and a quiet day; independent review.
**Fallback:** population and seats are config values: 10 heroes and 5 seats is the recorded safer arm if the band
cannot be met. Names and appearance do not depend on the balance outcome and ship regardless.

### M4. Demand, discovery and stories (L, medium to high uncertainty)

**Objective:** refusals become requests, the siege becomes a stocking decision, experiments leave sharper clues, and
a blade's story survives eras.
**Before:** "not better" from half the visitors; nothing says fire is resisted; a clue says "a steadying hand" for
every catalyst; a returned legend has a name that promises an affix it lacks. **After:** "Wren wants a bow; can spend
about 90"; "Frost bites the Ashclaw" beside matching stock and a `RESISTED` refusal; "wants a hotter fire" after a
second attempt and "Use this recipe" once found; a returned blade is what it was, dormant until honed.
**Save effect:** rules 4; schema 5 (no-op run step; legacy table: clue bit sets default empty); balance 7; defaulted
fields (`Hero.want`, `Commission.kind` and `recipientId`, `Journal.signatureClues`, `LegendEntry` additions).
**Outcome-changing:** T4.1 (need terms), T4.2, T4.5 (what a returned legend is), T4.6. **Neutral:** T4.3's clue
ladder and recall (rumours draw on the EVENTS stream as the fragment event already does, which is outcome-changing
for later events and is listed as such in the ledger).
**Commands:** `./gradlew :core:test`; sweep steps 6 and 6b; `--eras 3 --legends` for T4.5; the scenario scripts.
**Integration boundary:** branch `shop-day/m4`. INT lands the new enum constants, `Hero.want`, the commission and
legend fields and the schema-5 steps first.

| Task | Who edits what | Needs | Tests | Done when |
|---|---|---|---|---|
| T4.1 Standing wants | INT: `core/model/Model.kt` (`Hero.want`), `CustomerConfig` need terms. CA: `core/market/Market.kt` (record a want on a refusal; the need terms in `willingness`). CB: `core/shopday/Advice.kt` (`ANSWER_WANT`). AB: `ShopPanel` "Who is buying" and the Forge header name wants ("Wren wants a bow; can spend about 90"); "Forge this" from a want. SIM: REQUEST_DRIVEN reads wants | M3, T0.7 | `coreTest/WantsTest.kt`: `aRefusalRecordsWhatWouldHaveSold`, `aWantLapsesAfterThreeDaysOrAPurchase`, `anAnsweredWantRaisesWillingness`, `wantsLeaveEveryRngStreamUnchanged`; sweep step 6 | E1, G02, B02 closed: the M4 band rows for conversion and NOT_BETTER hold |
| T4.2 Sidegrade gate and siege demand | INT: new constants in `VisitReason` and `VisitFactor`; `CustomerConfig` (`sidegradeTolerance`, `threatUtility`, `championSiegeWillingness`). CA: `core/market/Market.kt` (`evaluate`: gain as Double over value in the hand; side reasons; `threatUtility`; `RESISTED`). CB: `core/shopday/Lines.kt`. AB: weakness and resistance labels on the Forge and the shelf. SIM: SIEGE_PREP uses the warning window | T4.1, T0.7 | `coreTest/SidegradeTest.kt`: `aNearEqualBladeIsBoughtForTasteOnce`, `noChurn`, `aNineTenthsGainIsAGain`, `affixesAndFameCountOnBothSides`; `coreTest/SiegeDemandTest.kt`: `counterElementIsValuedInTheWarningWindowOnly`, `aResistedBladeIsRefusedWithItsOwnReason` | G01, E2, X17 closed; 1-4 sidegrades a run, no hero more than three; SIEGE_PREP at most +6 mean days over FAIR |
| T4.3 Clue ladder, rumours, recall | INT: `core/model/Model.kt` (`Journal.signatureClues`). CB: `core/crafting/Journal.kt` (`recordSignatureClue` keeps answering; the hint shows earned rungs only), `core/crafting/Signatures.kt` (`missing` per rung), `core/engine/WorldEvents.kt` and `core/battle/Battle.kt` (a rumour on an elite slain with a shop blade and on a completed commission). CON: one phrase per catalyst; the honest catalyst description. AB: Journal entries with "Use this recipe" on a discovered signature and **"Use" on any understood core + augment row**; "Forge again" on the result card. SIM: SIGNATURE_PURSUIT follows clues | M2, T0.7 | `coreTest/ClueLadderTest.kt`: `aSecondMissEarnsTheCatalystRung`, `eachCatalystHasItsOwnPhrase`, `noOddsAtAnyRung`, `cluesMergeAcrossEras`, `noSignatureIsFirstFoundWithoutRungOne`; `appAndroidTest/ForgeShortcutTest.useThisRecipeFillsTheDraft`, `anUnderstoodJournalRowFillsTheDraft` | G07, E3, A02 closed; SIGNATURE_PURSUIT makes a first discovery by the end of era 2 in 70 % of accounts |
| T4.4 (withdrawn) | four mechanical catalyst jobs: deferred, see 4.7 and 10.3 | | | catalyst identity is delivered by T4.3 |
| T4.5 Artifact fidelity, the ledger, weapon names | INT: `core/model/Model.kt` (`LegendEntry` additions; `Weapon.dormantAffixes`). CB: `core/legacy/Legacy.kt` (owners from every ownership kind; one board entry per `weaponKey`; a returned blade nobody carried does not re-enter the board), `core/engine/WorldEvents.kt` (a return keeps name, signature, flaws and catalyst; **its beneficial affixes come back dormant and wake the first time the smith hones it**, which answers "dormant against preserved" and GDD 7's "damaged or dormant"; a blade already returned in an era is not picked again), `core/crafting/Forge.kt` (new blades carry at most one affix prefix in their name; an earned title replaces it; the rest is in the item sheet). AA: item sheet "story" section and a dormant marker. SIM: `--legends` arm | T1.5b, T2.7, T0.7 | `coreTest/ArtifactFidelityTest.kt` (9.2), plus `aReturnedLegendsAffixesAreDormantUntilHoned`, `aNameCarriesAtMostOnePrefix` | G09, X06, E5 closed; returned-legend strength measured over three eras and inside the tripwire |
| T4.6 Commission situations (last) | INT: `core/model/Model.kt` (`Commission.kind`, `recipientId`); `CustomerConfig.maxOpenCommissions`. CA: `core/market/Market.kt` (`maybeOfferCommission` chooses the kind from state; two open at once; REPLACEMENT and SIEGE_PREP first, AMBITION and FIRST_BLADE only if the first two pass). AB: request cards say why. SIM: REQUEST_DRIVEN handles kinds | T1.8, T4.2, T0.7 | `coreTest/CommissionSituationsTest.kt`: `aBrokenBladeProducesAReplacementRequest`, `aChampionAsksBeforeASiege`, `noKindExceedsFortyPercentOfOffers`, `twoOpenAtOnceNeverThree`; `ShopDayScriptTest.aCommissionAndACollectorNeverSqueezeOutThePurchaseAndTheRefusal` on two-commission days | E4 closed: REQUEST_DRIVEN completes at least 70 %; at most +2 mean days over one slot |

**Exit gate M4:** the M4 band of 4.3 and the M3 rows for FAIR and ACTIVE at 1,000 seeds x 3 (balance 7 recorded);
per-loop acceptance of 4.6; the EXPERT tripwire reported; device recordings of a want answered, a warning-window day,
a discovered signature recalled and a returned legend honed; independent review.
**Fallback:** each loop can be switched off by a `CustomerConfig` value (`needWantMet = 0`, `threatUtility = 0`,
`sidegradeTolerance = 0`, `maxOpenCommissions = 1`) without removing code. E4 is the first to cut if scope must shrink.

### M5. Progression and proof (M, medium uncertainty)

**Objective:** the decisions the game already offers are shown to work, weak upgrades are fixed or justified, and the
frozen rules get their 10,000-seed review.
**Before:** an upgrade row says "+2% exceptional forging chance per level"; three tracks measure nothing.
**After:** each row says what changes next era in words and amounts; every track has a yardstick it moves.
**Save effect:** none, unless T5.2's valve is adopted (then rules 5 and a ledger entry). **Outcome-changing:** T5.2's
valve if adopted; T5.3's reworked numbers (balance version bump). **Neutral:** T5.4 (golden projection unchanged).
**Commands:** the shock and multi-era runs; `./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy all
--impactPolicy BALANCED_ACTIVE --customers --perf --json <file>"`.
**Integration boundary:** branch `shop-day/m5`; T5.4 lands alone, after M4, because it touches every resolver.

| Task | Who edits what | Needs | Tests | Done when |
|---|---|---|---|---|
| T5.2 Recovery and reachability | SIM: `core/sim/Metrics.kt` (`stuckDays`, `droughtDays`, `longestStuckStreak`; `hardLocks` kept and named for what it is), new `coreTest/WorldEventReachabilityTest.kt` | T0.7 | `everyPooledEventFiresInTwoThousandRunsAndBothRulesTrigger`; shock-scenario and FREE_LISTINGS report | G10, C15 closed: the valve's trigger (stuck streaks of three days or more in more than 1 % of NOVICE runs) is evaluated and its number written down |
| T5.3 Upgrades | SIM: purpose metrics per track, including days to the first forge of a chosen recipe and experiments by day 10. CON: descriptive chance wording; reworked numbers only for tracks failing both gates of 4.7. CB: `Legacy.preview(upgradeId, level)`. AB: run-end rows show "Next era: 12 starting energy instead of 10" | T3.4, T0.7 | `coreTest/LaunchContentTest.noPlayerFacingTextContainsAPercentChance`; `coreTest/LegacyPreviewTest.everyUpgradeLevelHasAConcretePreview`; the per-track gate table in DECISIONS | G04, A09 closed |
| T5.4 Resolver constants | INT: nested groups (`heroLife`, a new `CombatConfig`). CA: `Heroes` and `Market` literals. CB: `Battle`, `Power`, `WorldEvents` effect literals, `Legacy` milestone points; values unchanged | M4 | golden projection unchanged; `coreTest/ConstantsTest.resolversHoldNoUnlistedLiterals` (source scan against an allowlist) | E02 closed; the `CLAUDE.md` statement corrected |
| T5.5 10,000-seed review after the update (last) | SIM + INT: `docs/DECISIONS.md` ("Balance review at 10,000 seeds: the shop-day update"), row for row beside the v5 table of 0.6.0 | everything that changes rules or numbers | the command above plus the multi-era and shock runs | B01 closed for the update: both bands of 4.3 hold at 10,000 seeds; maxed accounts and every upgrade are reported as distributions with their yardsticks; **any later change to rules or numbers re-runs this task**; any proposal to lower wear or a track is a separate, owner-approved change with its own evidence |

**Exit gate M5:** T5.5 recorded; no open finding in the review's sections 6 and 7.
**Fallback:** T5.3 reworks are content numbers and revert cleanly; T5.4 is behaviour-neutral by construction.

### M6. Accessibility, performance and long saves (M, medium uncertainty)

**Objective:** the new screens work at large fonts and small sizes and with TalkBack, and a long save stays fast and
bounded without deleting anything the player owns.
**Before:** a 2,000-blade storage is one non-lazy column; nothing has been seen at 720x1280 or font scale 2.0; a long
save's cost is unmeasured. **After:** storage filters and bulk actions; every new screen passes the layout matrix;
a 1,000-day save loads and commits inside stated budgets or the gap is a recorded finding with numbers.
**Save effect:** T6.3a changes what a save keeps (closed commissions, old processed IDs, routine records, middle
ownership entries); a schema bump with a no-op step, because an older build must not re-grow or misread them; nothing
the player owns and nothing a rule can still return is removed. **Outcome-changing:** nothing: every T6.3a rule ships
only with an "outcomes identical with and without" pair test, and a rule that cannot pass one is dropped.
**Commands:** `./gradlew :core:soak`; `:app:connectedDebugAndroidTest` (`LongSaveDeviceTest`, `ShopDayA11yTest`,
`LayoutMatrixTest`); `tools/emulator/layout.sh`; `adb shell am send-trim-memory <package> RUNNING_CRITICAL`;
`adb shell settings put global always_finish_activities 1`.
**Integration boundary:** branch `shop-day/m6`; T6.3a is core-only, the rest app-only.

| Task | Who edits what | Needs | Tests | Done when |
|---|---|---|---|---|
| T6.1 Layout matrix | AB: fixes found by `LayoutMatrixTest.noOverflow` (font 1.0 / 1.3 / 1.5 / 2.0 x 411 / 360 / 320 dp via `DeviceConfigurationOverride`) on Shop, Forge, the shop day, both sheets, run end; `tools/emulator/layout.sh` screenshots | M2-M4 | the matrix test; screenshots reviewed | A05, A07 closed on the emulator |
| T6.2 TalkBack and semantics | AA: merged visit node, polite live region per beat, traversal order header, card, Next, Back, Skip, speed; scene hidden from the tree; rarity never spoken as a glyph | M2 | `appAndroidTest/ShopDayA11yTest.kt`: `traversalOrderIsHeaderCardNextBackSkip`, `everyClickableIsAtLeast48dp`, `noClickableNodeLacksALabel`, `receiptRowsReadAsLabelAndValue`, `eachBeatsAnnouncementEqualsItsOnScreenOutcomeText` | emulator half of the accessibility gate; the hardware half is T6.6 |
| T6.3a Bounded growth rules | CB: `core/persistence/` (prune closed commissions older than the event window; keep the last few processed End Day IDs; demote `WORLD_EVENT` and `HERO_ARRIVED` to ordinary retention; `Weapon.ownerIds`, then keep first and last ownership entries of non-combat history). INT: one config number per rule (0 = off); the schema step. **Decisions recorded, not rules:** seized and lost-with-hero blades stay as they are, because a return event can bring any of them home for the rest of the run; dead and retired heroes stay (about 33 bytes a day; four rules read them by ID). Definition written into `docs/DECISIONS.md`: hot state is what a rule can still read (living heroes, stock, open commissions, returnable blades, the event window); archive is what only a screen reads (older editions, closed commissions, full ownership lines) and is what these rules trim | T0.7 | one pair test per rule, in the style of `WeaponPruningTest` | no rule deletes stock or a returnable blade; the checklist gains C12's open line with the measured remainder |
| T6.3b Production soak, device measurement, memory pressure | SIM: new `coreTest/ProductionSoakTest.kt`, a `:core:soak` Gradle task; `SoakTest`'s p95 assertion made real. AB: `appAndroidTest/LongSaveDeviceTest.kt`; the low-memory script steps | T6.3a | `ProductionSoakTest` and `LongSaveDeviceTest` with the budgets of 9.4; relaunch after `send-trim-memory` and with `always_finish_activities` shows the same day, draft and beat; one run on a 2 GB AVD | F10 closed. **Hard budgets** (the task fails otherwise): End Day p95 under 200 ms compute-only; cold load under 2 s at 1,000 days on the emulator; no ANR. **Report-only:** commit time, frame percentiles, heap |
| T6.3c Storage tools | AB: Storage sheet filters (family, rarity, "never listed") and multi-select Salvage / Arm the watch, both confirming | T2.8b | `StorageSheetTest.bulkSalvageAsksOnceAndIssuesOneCommandPerBlade` | a 200-blade storage can be filtered or cleared in under ten taps |
| T6.4 Lifecycle tests | AB: `appAndroidTest/ShopDayPersistenceTest.realRoomSurvivesRecreation` (`ActivityScenario` recreate and `StateRestorationTester`) | M2 | the tests | E04 closed |
| T6.5 Main thread | AB: `siegeForecast` out of composition (computed once per `GameState` in the ViewModel); UI models for all four destinations built on `Dispatchers.Default` | T2.8b | frame-time capture (`dumpsys gfxinfo`) on Shop and Storage with a 1,000-day save | E03, X12 closed |
| T6.6 Physical device | INT: End Day p95, commit and load time, a TalkBack pass on hardware | a device | n/a | **blocked** until a device is attached; recorded as blocked, never as passed |

**Exit gate M6:** the hard budgets of T6.3b met; report-only numbers recorded; the layout matrix and a11y tests green;
T6.6 open and labelled. **Fallback:** each growth rule is behind its own config number, as the existing three are.

### M7. Release preparation for this update (S, low uncertainty plus human gates)

**Objective:** the update is documented, walked through end to end and judged by first-time players.
**Before / after for the player:** none in the game. **Save effect:** none. **Outcome-changing:** nothing.
**Commands:** section 9.1; the three recorded runs of 9.8. **Integration boundary:** documents and scripts only.

| Task | Who edits what | Needs | Tests and checks | Done when |
|---|---|---|---|---|
| T7.1 Documents and version | INT: `CLAUDE.md`, `docs/*`, `CHANGELOG.md`, `app/build.gradle.kts` version | all | section 10.1 walked line by line | checklist ticks match evidence rows in the ledger |
| T7.2 Fresh-player sessions | owner + INT: the protocol of 9.7; a prepared save one day before a fatal siege | M2 at the earliest, M4 for the full gate | the pass counts of 9.7 | the gate is met, or each failed question is filed as a defect against its screen; recorded as blocked while no players are available |
| T7.3 GDD 19 walk-through | INT: the end-to-end acceptance of 9.8, recorded on the emulator three ways (watch, skip, kill at each stage) | M6 | the three saves at the same day are equal | recordings filed; the GDD 19 line "sound/motion controls" is marked open (motion done, sound deferred, 10.3) |
| T7.4 Release identity runbook | INT: a written, ready-to-run rename (namespace, applicationId, package directories; the Room database name unchanged), an R8 trial build with its keep rules, a launcher icon candidate exported by the importer, the art provenance wording | owner: application ID and wording | the trial build installs and passes `smoke.sh` | the runbook exists; the rename itself waits for the ID and is run alone, not alongside UI work |

**Exit gate M7:** T7.1 and T7.3 done; T7.2 passed or its defects filed; T7.4 ready. **Fallback:** none needed; a
human gate that cannot be run is recorded as blocked and the update is not described as validated by players.

### Dependency summary

```
M0: T0.4 -> T0.7;  T0.5;  T0.6;  T0.1-T0.3 any time
M1: T1.5a -> (T1.1 -> T1.2 -> T1.3) and (T1.4, T1.6) -> T1.5b -> T1.7, T1.8, T1.9
M2: T2.1 -> T2.2 -> T2.3;  T2.4 (ART, may start with M1) -> T2.5 -> T2.6;  T2.7;  T2.8a -> T2.8b, T2.8c;  then T2.9, T2.10
M3: T3.1 -> T3.2 -> T3.3;  T3.1 -> T3.4 -> T3.6;  T3.5, T3.7;  T3.8 last
M4: T4.1 -> T4.2 -> T4.6;  T4.3;  T4.5
M5: T5.2, T5.3;  T5.4;  T5.5 last (re-run after any later rule or number change)
M6: T6.1, T6.2;  T6.3a -> T6.3b;  T6.3c;  T6.4, T6.5;  T6.6 blocked
M7: T7.1, T7.3;  T7.2 and T7.4 wait on the owner
M0 -> M1 -> M2 -> M3 -> M4 -> M5;  M6 after M2 (T6.3a after M4);  M7 last
```

---

## 8. Multi-agent, skills and integration protocol

### 8.1 Roles and file ownership

| Role | Owns (writes) | Never touches |
|---|---|---|
| **INT** integrator (the lead session) | the contract files: `core/model/Model.kt`, `core/model/ShopRecord.kt` (types), `core/engine/GameEngine.kt`, `ResolutionContext.kt`, `Commands.kt`, `Compatibility.kt`, `Invariants.kt`, `core/persistence/SaveCodec.kt`, `core/config/BalanceConfig.kt`, `core/content/Content.kt`, `app/GameSession.kt`, `app/data/*`; merges; `docs/*`; `CHANGELOG.md`; `tools/emulator/*`; the emulator | edits worker-owned files only while merging |
| **CA** core worker A | `core/market/*`, `core/heroes/Names.kt`, `core/heroes/Appearance.kt`, `core/heroes/Heroes.kt` (generation and arrivals), their tests | `app/`, the contract files |
| **CB** core worker B | `core/battle/*`, `core/legacy/*`, `core/gazette/*`, `core/crafting/*`, `core/shopday/*`, `core/engine/WorldEvents.kt`, `core/persistence/*Compaction*.kt` and `WeaponPruning.kt`, their tests | `app/`, `core/market/*` |
| **SIM** simulator worker | `core/sim/*`, simulator tests, sweep output under the scratchpad | engine logic |
| **AA** app worker A | `app/ui/shopday/*`, `app/ui/detail/*`, their tests | `GameSession.kt`, existing panels |
| **AB** app worker B | `app/GameViewModel.kt` (UI state only), `app/ui/WorkshopScreen.kt`, `ShopPanel.kt`, `ForgePanel.kt`, `InfoPanels.kt`, `RunEndScreen.kt`, `RecordsPanel.kt`, the sheets, `Labels.kt`, `theme/*`, their tests | `ui/shopday/*`, `ui/detail/*` |
| **ART** asset worker | `tools/pixelart/*`, generated files under `app/src/main/res/drawable-nodpi/`, `app/ui/PortraitArt.kt` and `WeaponArt.kt` (generated), `app/ui/Sprites.kt`, `docs/ART_BRIEF.md`, `docs/ART_MANIFEST.md` | everything else |
| **CON** content worker | `core/content/LaunchContent.kt`, `SliceContent.kt` (data: name pools, appearance keys, template and clue text, upgrade wording), `LaunchContentTest` | engine logic, `Content.kt` |
| **REV** independent reviewer | nothing (read-only), fresh context per slice | all files |

Every task row of section 7 says who edits which file, and those cells are the authority where they are finer
than this table: the INT part of a task (a model field, an engine order change, a config group, a migration step)
is its contract commit; a SIM, CB or AB part inside another worker's task is a separate change on the same
integration branch. No two workers hold the same file at the same time. `Heroes.kt` is the one file two core
workers need (CB for field results in T1.6, CA for generation and arrivals in M3): never in the same slice.
`Sprites.kt` stays with ART, so every sprite mapping the shop day needs is delivered by T2.4, not edited by AA.

### 8.2 Order inside every slice

1. **Contract first, on an integration branch.** Each slice has a branch `shop-day/m<N>`. INT lands the slice's
   types, signatures and failing tests there (section 6); `main` only ever receives green merges, because CI runs on
   every push and other sessions commit to `main`. INT runs
   `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug`, commits. Workers branch only after this commit.
2. **Parallel work in worktrees.** One worktree per worker (`Agent` with `isolation: "worktree"`, or
   `superpowers:using-git-worktrees`). Workers do not commit to `main`, do not run the emulator and do not edit files
   outside their row. A worker that needs a contract change stops and reports it; INT makes it.
3. **Brief each worker deliberately.** A worker sees only its brief. Paste: the task rows from section 7, the
   contract block from section 6, the Global Constraints, the exact test names it must make pass, the commands it may
   run, and the 255-slot rule. Name the skills to load: `superpowers:test-driven-development` for every code task;
   `kotlin-concurrency-and-flow` for T1.1-T1.3 and T2.3; `compose-state-and-effects`, `compose-component-design`,
   `compose-animations`, `compose-performance` and `android-review:compose-ui` for app tasks;
   `compose-ui-testing-patterns` for Compose tests; `superpowers:systematic-debugging` on any failure;
   `superpowers:verification-before-completion` before reporting done. If skills are unavailable to a worker, say so
   in the ledger and proceed.
4. **Merge one branch at a time.** After each merge: `./gradlew :core:test :app:testDebugUnitTest`, then
   `:app:assembleDebug`. Conflicts are expected only in `CHANGELOG.md`, `docs/DECISIONS.md`, `docs/PROGRESS.md` and
   `BalanceConfig`; INT resolves them and bumps `BalanceConfig.version` once per slice that changes numbers.
5. **Look at the running app.** `./gradlew :app:installDebug`, the slice's scripted loop, read the screenshots, send
   the meaningful ones to the owner, fix what is wrong. One emulator session at a time; INT holds it. Only one Gradle
   invocation at a time in the main checkout (worktrees have their own).
6. **Independent review.** A fresh agent receives the merged diff range, the slice's exit gate and the relevant
   review findings, not INT's conclusions (`superpowers:requesting-code-review`, or `/code-review high`). Findings
   are fixed or answered in writing before the gate is called.
7. **Record.** Tick the ledger, add evidence rows to `docs/PROGRESS.md`, tuning evidence to `docs/DECISIONS.md`,
   ticks to `docs/GDD_CHECKLIST.md`, a line under `[Unreleased]` in `CHANGELOG.md`.
8. **Commit and push per verified slice** on `main`, as the owner has authorised for this repository; never
   force-push; version bumps follow `CHANGELOG.md`. The application-ID rename and anything touching release identity
   or publishing wait for the owner (10.4).

Routine engineering choices (a name, a helper, a layout detail inside the agreed composition) are made and recorded
in `docs/DECISIONS.md`; they do not stop work. Work stops only for the blocking decisions of 10.4.

### 8.3 Ledger and handoff between sessions

`docs/MAJOR_UPDATE_LEDGER.md` (created during planning with every task as `todo` and the 0.6.0 baseline; kept from
T0.1 on) is the only progress source of truth: one row per task ID with status
(`todo`, `contract`, `in worktree <name>`, `merged`, `verified`, `blocked: <reason>`), the commit, the evidence
(command and one-line result, screenshot folder) and the reviewer's verdict. A session ends by updating it and the
"Next executable actions" list in `docs/PROGRESS.md`; a session starts by reading both and re-running the commands of
9.1. No claim of "passes", "validated on device" or "players enjoy it" is written without its evidence row.

---

## 9. Evidence plan

A check that could not run is recorded as **blocked** with the reason. It is never recorded as passed.

### 9.1 Baseline commands (first thing in every session; results into the ledger)

```
git status --short && git log --oneline -5
./gradlew :core:test
./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --noImpact --json <scratch>/sim.json"   # add --customers after T0.4
./gradlew :app:testDebugUnitTest                      # after T0.5
./gradlew :app:assembleDebug :app:lintDebug
C:/atools/sdk/platform-tools/adb devices              # device or emulator present?
./gradlew :app:installDebug
ADB=C:/atools/sdk/platform-tools/adb bash tools/emulator/smoke.sh <scratch>/smoke
ADB=C:/atools/sdk/platform-tools/adb bash tools/emulator/runend.sh <scratch>/runend
./gradlew :app:connectedDebugAndroidTest
```

### 9.2 Behaviour regressions (test names are in the task rows of section 7)

| Risk the brief names | Test class (module) | Task | The essential assertion |
|---|---|---|---|
| Reordered concurrent saves | `GameSessionTest` (`appTest`) | T1.1 | with the fake repository holding each commit, two upgrades and upgrade-then-Begin-era in every interleaving leave both purchases and the new run stored; at most one commit is ever in flight |
| Failure recovery | `GameSessionFailureTest`; `SaveStoreTest` (instrumented) | T1.2 | stored bytes equal before and after a failed load or commit; `snapshot` never shows a state the store does not hold; Retry returns to `Idle` |
| Report and archive coherence | `DayEditionTest` (`coreTest`) | T1.4 | forge, list, hone, buy a tool, End Day: the report's record IDs equal the day's archived IDs, each once |
| Commission boundaries and reservation | `CommissionRulesTest` | T1.8 | the visible criterion and the fulfilment rule are one function; a sole candidate is delivered before browsers; the least sufficient blade is taken |
| Transaction accounting | `ShopLedgerTest` | T1.6, T2.1 | `goldAtOpen + income == goldAtClose` on every day of 200 runs; browsers who bought never exceeds browsers; a fatal expedition is a loss |
| Customer fairness | `CustomerSelectionTest` | T3.1 | equal heroes get equal turns regardless of ID; the three-day guarantee; a fixed number of draws per day |
| Warlord ranking | `ChampionSelectionTest` | T1.7 | a Giant Slayer bearer enters the top three only against a warlord; ranking and valuation agree |
| Two-locale determinism | `LocaleDeterminismTest` | T1.6 | 30 days under `Locale.US`, `Locale.GERMANY` and `Locale("tr", "TR")` encode to equal bytes |
| Old-save migration and appearance | `CompatibilityTest`, `MigrationTest`, `AppearanceTest` | T1.5a, T1.5b, T3.3 | events, weapon histories, serials and RNG stream states equal before and after admission; every existing hero keeps today's face; a pre-update hero blocks that face for newcomers |
| Cross-era artifact fidelity | `ArtifactFidelityTest` | T4.5 | `aReturnedLegendKeepsAffixesFlawsCatalystAndSignature`, `theOwnerLineIncludesHeirsAndCommissionPatrons`, `aReturnedNameNeverPromisesAnAffixTheBladeLacks`, `aLegendCannotReEnterTheBoardOwnerless`, `threeNaturalErasKeepOneBladesStoryTruthful`, `entriesFromOlderProfilesRenderAsLostToTime` |
| Presentation skip / resume / restart equality | `PresentationEqualityTest` (`appTest`) | T2.3 | three paths from one committed End Day leave run and legacy bytes equal to the bytes right after the commit; the sequence issues no command other than `ChooseBlessing`, whose own equivalence test covers it |
| Process death around the commit and around the acknowledgement | `ProcessDeathTest` (`appTest`, the session rebuilt from the same fake store), `tools/emulator/shopday_kill.sh`, `ShopDayPersistenceTest` (instrumented) | T2.3, T2.9, T6.4 | a kill before the commit replays the same day; after it, the Resume prompt and no re-simulation; after `DONE`, planning on the next day |
| Recording changes nothing | `ShopRecordTest`, `GoldenStateTest` | T0.6, T2.1 | the RNG stream states and the gameplay projection for 20 seeds x 15 days are equal before and after the record is added (the encoded save is not compared: it changes whenever a field is added) |

Mapping of the review's own verification rows: V01, V02 = T1.1 · V03 = T1.2 · V04 = T1.4 · V05 = T1.8 · V06, V07 =
T1.6 · V08 = T3.1 · V09 = T1.7 · V10 = T1.6 · V11 = T0.7 · V12 = T4.5 with T0.7's multi-era bot · V13 = T6.3a, T6.3b ·
V14 = T2.3, T2.9, T6.4 · V15 = T6.1, T6.2 (emulator) and T6.6 (hardware, blocked) · V16 = T7.2.

### 9.3 Simulation evidence

Baseline: the 10,000-seed balance v5 review of release 0.6.0 (2.3). Every later table is laid out beside it.

Bots (T0.7), all through real commands: ADVANCED_SMITH, three technique bots, four catalyst bots, REQUEST_DRIVEN
(commission-led), SIEGE_PREP, SIGNATURE_PURSUIT, SCARCE_RECIPE, the shock scenarios SPENDTHRIFT, NOVICE and
BROKE_START, a blessing strategy as a parameter of every policy, and multi-era play that claims, buys upgrades by a
fixed rule and carries the journal, the Legend Board and the lineages, so artifact returns are genuine. Existing
policies stay as controls. The 0.6.0 tracks that read zero today (Caravan Ties, Anvil Lore, Homing Steel) and Lucky
are judged only once a bot exists that uses them.

Metrics (T0.4 adds them without changing rules): willing, served and turned away per day as distributions; share of
days with two or fewer visitors and at capacity; unique heroes served by day 5, day 10 and per run; served-share
spread (max, min, ratio; starting cast against later arrivals); class coverage by day 5 and 10; newcomer wait; return
rate and days between visits; conversion overall and by day band; refusal mix by code; sales per day and sell-out
days; hero gold on days 1, 5, 10, 15 and the share who cannot afford the cheapest listed blade; name and face
collisions; expeditions won, lost, fatal; a snapshot per siege (pressure, militia, armory, champion powers, counter
or resisted elements held, raid power, result); stuck, drought and resting days; `signatureFirsts`,
`signatureWeapons`, days to the first signature, clue rungs earned; commissions by kind and where the blade came
from; wants recorded, satisfied, lapsed; sidegrades by reason; patronage share and stipend gold; legacy points;
survival p10 / median / mean / p90.

Adverse arms beyond the shock bots: FREE_LISTINGS (everything priced at 0: does reputation or loyalty run away?),
prices at 70 % and 180 %, a material-poor start, a `Lean Harvest` world, a town that lost half its heroes. The
EXPERT tripwire bot of 4.3 is reported at every gate from M3.

Procedure: one lever at a time; 300-seed directional arms; 1,000-seed arms at base seeds 1, 10001 and 20001 to
measure each number's noise floor; adopt only differences larger than twice that floor; when the rules are frozen,
one 10,000-seed review of every policy (T5.5), reported as distributions with the floors beside them. Adverse states
get their own arms: prices at 70 % and 180 %, a material-poor start, a `Lean Harvest` world, a town that lost half
its heroes, the three shock bots.

A simulator result is evidence about balance and reachability. It is never cited as evidence that the game is fun.

### 9.4 Long-save evidence (F10)

`ProductionSoakTest` (JVM, outside the default suite, run by `./gradlew :core:soak`): 1,000 and 2,000 days of forced
survival with the engine's own compaction only (`SimulationDriver(eventRetentionDays = 0, maxForgesPerDay = null)`),
a smith who uses all ten energy every day and never salvages, plus an active one. Recorded every 100 days: weapons by
location, heroes by fate, commissions by status, processed-ID count, events total and kept-forever, longest and mean
weapon history, `lastResolution` bytes, encoded bytes and envelope overhead, encode / decode / End Day time.
The same saves go to the emulator for `LongSaveDeviceTest`: cold load, a `SetPrice` commit, End Day, Storage list
frame times with 200 and 2,000 stored blades (`dumpsys gfxinfo`), Java heap after load.

| Budget | Kind |
|---|---|
| End Day p95 under 200 ms, compute only (GDD 15.3) | hard: T6.3b fails otherwise |
| cold load under 2 s at 1,000 days on the emulator | hard |
| no ANR in the device loop with the 1,000-day save | hard |
| a `SetPrice` commit under 500 ms at 1,000 days | report-only |
| at least 90 % of frames under 16.7 ms in the Storage fling | report-only |
| bytes per day after day 200, excluding storage stock, flat | report-only; the measured slope goes into the checklist's open line (C12) |
| Java heap after load | report-only |

Emulator numbers are labelled as emulator numbers (0.6.0 already reads End Day p95 21 ms at day 120 with 367
weapons, up from 8.5 ms); the hardware row stays blocked until T6.6.

### 9.5 Visual and interaction matrix (emulator; screenshots, and one screen recording per slice)

Memory pressure (not the same thing as a kill): `adb shell am send-trim-memory <package> RUNNING_CRITICAL` and
`adb shell settings put global always_finish_activities 1` during planning, during a visit and on the Tomorrow
card, then a relaunch: the same day, draft and beat; plus one full loop on a 2 GB AVD.

Sizes: the default `Pixel_10_Pro`; 360x640 dp (`wm size 720x1280`, `wm density 320`); 320x569 dp (`wm density 360`);
one tablet AVD look, recorded as an observation (portrait lock is likely ignored at targetSdk 37). Font scale 1.0,
1.3, 1.5, 2.0. Reduced motion on and off.

Scenarios, each from a scripted save or a fixed seed: empty shelf; visitors and no purchase; every blade
unaffordable; ten visitors on a festival; 200 blades in storage; a commission-only sale; a collector visit; a customer
who buys in the morning and dies or retires that day; a siege day held; a siege day lost with integrity left; the
final day; a save failure (a debug switch on the store in the debug build); a corrupt save; a 0.5.x or 0.6.0 save
with an unread report.

0.6.0 mechanics, never yet seen on a device (`docs/PROGRESS.md`): a guild hall day with a lesson, a blade inherited
by a guildmate, a merchant resale, a Known Name regular on day 1, Caravan Ties stock, a returned legend. Each is
shown from a **constructed** scenario save (T2.9) and is labelled so; it proves the presentation, not that the
situation occurs. **Natural reachability** is reported separately from the simulator as the per-run frequency of
the same event under ordinary bots, and any mechanic that stays at zero (wall deaths today) is a finding, not a
pass. One more constructed scenario answers the review's own acceptance for living artifacts: a single blade
followed through a sale, three fights and a change of owner, opened from the counter, the Town row and the
Gazette, with every line of its story traced to a stored history entry.

For each screenshot INT checks: the scene is the largest element of the shop day and nothing is tiled or stretched
at a fractional nearest scale; text is native and unclipped; one primary action; no control under 48 dp; nothing
under the system bars; no outcome text waiting on a timer.

### 9.6 Accessibility

Automated: `ShopDayA11yTest` (traversal order, 48 dp targets, receipt rows as label and value), `LayoutMatrixTest`
(no overflow at any scale and width), `NavigationBarTest`. Manual on the emulator, recorded: a TalkBack pass over
Shop, the shop day, both sheets and run end; each beat is announced through a polite live region and focus is never
moved programmatically; reduced motion verified by screenshot at each beat. Contrast: text on paper and on scene
overlays at least 4.5:1 measured on rendered pixels. Hardware TalkBack is part of T6.6 (blocked).

### 9.7 Fresh-player protocol (human gate; separate from all automated evidence)

Five people who have not seen the game, fifteen minutes each, fresh install, default settings, their own font
scale, think-aloud, screen recorded, no explanation and no hints before minute 10.

| Minute | Step | Observe |
|---|---|---|
| 0-1 | "Play as you would at home." Start a run | the first tap after the title; do they follow the lead card |
| 1-4 | Day 1: forge, list, End Day, the shop day | do they watch or skip; do they open a hero or a blade |
| 4-5 | **Q1** "Who bought your blade, and why?" (if nothing sold: "Who came, and what happened?") | answered from memory |
| 5-8 | Days 2-3, free play | whether they act on the Tomorrow card |
| 8-9 | **Q2** "Why did that customer leave without buying?" **Q3** "What happened because of a blade you sold?" | |
| 9-10 | **Q4** "What will you do tomorrow, and why?" before they tap anything on the new day | |
| 10-14 | Play on; at minute 12 load the prepared save one day before a fatal siege; finish, claim, reach upgrades | claim and upgrade taps |
| 14-15 | **Q5** "What does that upgrade do for your next era?" | |
| after M4 | **Q6** "Name one hero and what they want." **Q7** "You changed a material or a price today. What did that change?" | asked at the end of day 3 |
| any time | "Was the shop too busy, too quiet, or about right?" and whether they watched or skipped on day 3 | this is how playtests validate the customer numbers of 4.1: the simulator can show six visitors a day, only a player can say whether six is a crowd or a queue |

Pass per participant (Q6 and Q7 join the gate from M4: 4 of 5 for Q6, 3 of 5 for Q7; "about right" or "too quiet" from at
least 4 of 5 on the busyness question, and at most 2 of 5 skipping every day by day 3): Q1 names the buyer and the recorded reason; Q2 gives the recorded reason for one refusal and
an action that addresses it; Q3 links one real consequence to a hero and a blade seen at the counter ("nothing yet"
passes on a day with none); Q4 states one action that matches the lead or a request; Q5 describes the upgrade
without a percentage and without help; behaviour: three days inside ten minutes, uses Skip day or Next untold, never
leaves the app by accident. **Gate:** at least four of five pass Q1, Q2 and Q4, at least three pass Q3 and Q5,
nobody fails the behavioural line, and at least three choose to play another day when told "you can stop here".
A question failed by two or more people is a defect in the screen that should have answered it. Voluntary replay
interest and clear decisions are the product success criteria; simulator numbers are not.

### 9.8 End-to-end acceptance (recorded once per release candidate)

Learn (the day-1 lead) -> craft -> price and list -> open the shop and watch -> read the aftermath -> open one blade's
story and follow it to its holder -> adjust tomorrow
from the lead -> siege -> defeat -> claim -> buy an upgrade and read what it changes -> next era showing retained
knowledge (journal, a recalled recipe) and history (a descendant or a returned blade when one exists). Run from one
fixed seed three ways: watching everything, skipping everything, and with a process kill at each stage; the three
saves at the same day must be equal. Settings (reduced motion, speed) are changed mid-run and survive relaunch.

---

## 10. Documentation and release scope, deferred items, blocking decisions

### 10.1 Documents kept current

| Document | Change | Task |
|---|---|---|
| `docs/MAJOR_UPDATE_LEDGER.md` (created during planning) | requirement status at 0.6.0, baseline evidence, one row per task | T0.1, every task |
| `docs/GDD_CHECKLIST.md` | C01-C10 and C13 applied as split lines; a "Shop day update" block with this plan's open items; ticks only with evidence | T0.3, each slice |
| `docs/DECISIONS.md` | one section per slice: the operation boundary; the visit record and what it deliberately does not store; the cursor and the rejected alternatives; the compatibility policy; the customer baseline; "Balance v6: a larger, fairer town" with every sweep arm, adopted and rejected; names and appearance rules; commission policy; chance-wording policy (A09); upgrade evidence; GDD departures (6 seats, 12 heroes) | each slice |
| `docs/IMPLEMENTATION_PLAN.md` | phase "P7b: the shop day" with M0-M7 gates | T0.1, each slice |
| `docs/PROGRESS.md` | state, checks run with results, next actions; the stale 0.5.x statements ("Known Name is not [applied]", "checked at 1,000 seeds only", "to be confirmed at 10,000 seeds") were corrected when this plan was reconciled with 0.6.0 | every session |
| `docs/ART_BRIEF.md`, `docs/ART_MANIFEST.md` | the reference boards and the atlas as sources, appearance keys, scene layers and filtering rules, the missing-art list of 5.6, provenance wording | T2.4, T3.3 |
| `CLAUDE.md` | architecture paragraph gains `GameSession`, the visit record, `ShopDay.script`, `Compatibility`, the nested config groups and the 255-slot limit; the art paragraph names the real sources; new simulator flags and scripts under Commands; "every number in `BalanceConfig`" corrected to what is true after T5.4 | T1.3, T2.9, T5.4, T7.1 |
| `CHANGELOG.md` | a line under `[Unreleased]` per user-visible change; the shop day ships as the minor version after session 8's 0.6.0 | each slice |

### 10.2 Release scope inside this update

In: CI on every push (T0.2); Room `exportSchema` with a schema directory (T1.2); `allowBackup="false"` (T1.2); the
compatibility policy with a real migration step and fixtures (T1.5a, T1.5b); a ready-to-run rename runbook, an R8 trial
build and a launcher icon candidate (T7.4); the recorded GDD 19 walk-through on the emulator (T7.3).
Out, unchanged from P8: signing, AAB, store listing, privacy and legal assets.

### 10.3 Deferred, with reasons

| Item | Why it waits | Tracked in |
|---|---|---|
| Full visual redesign: bespoke chrome (the V3 frames, the atlas panels), painted backdrops for every destination, walking customers, custom type | scheduled by the owner after this update; this update needs only the counter scene, the hierarchy and readable controls | checklist 12, 14 |
| Audio, haptics and their toggles | nothing emits sound or vibration, no audio assets exist, haptics cannot be verified on the emulator, and a toggle with no effect is dead UI. The Settings sheet is built now so they have a home. This leaves the GDD 12 accessibility item "haptics/audio toggles" and the GDD 19 line "sound/motion controls" open (motion done); T7.3 records it as open | checklist 12 |
| 24 signature sprites at 56 px; transaction icons; a counter foreground; backdrop heat and night states | genuine art gaps (5.6); stopgaps ship | `docs/ART_BRIEF.md` |
| Golden-image screenshot suite | the visuals will be replaced in the redesign and goldens would churn; structural Compose tests and scripted screenshots cover this update | checklist 15 |
| Physical-device p95 and hardware TalkBack | no device attached | T6.6, blocked |
| An inventory cap | F10 says measure and organise first: T6.3b measures and T6.3c adds filters and bulk actions | DECISIONS |
| An explicit `ReserveForCommission` command | the order swap, the least-sufficient pick and the preview remove the trap; built only if playtests ask | DECISIONS |
| Four different general-purpose effects for the four catalysts | two of the obvious jobs duplicate techniques, catalysts are signature conditions so every signature blade would move, and it needs four more bots; identity ships through clue phrases and honest text (T4.3). The design sketch (salt: fewer defects; rune: an affix slot; oil: raw quality at risk; ink: brilliance) is kept in DECISIONS for the redesign of Advanced Forge | DECISIONS |
| Repairs at the shop, haggling, passing travellers, rival smiths | rejected or postponed with reasons in 4.6 | DECISIONS |
| New content (materials, families, classes, factions, events) | counts are LOCKED and the review asks to prove existing decisions first | none |
| PROPOSED GDD examples not built (exact upgrade categories, utility model details) | PROPOSED, not LOCKED (C14) | checklist backlog |
| Tablet and landscape layouts | portrait phone is the GDD target; one tablet look is recorded in 9.5 | checklist 12 |

### 10.4 Decisions that genuinely block something

| Decision or input | Blocks | Does not block |
|---|---|---|
| The release application ID replacing `com.example.blacksmithproject` (already pending with the owner) | the rename in T7.4 | everything else |
| How the art is described for a paid release, and confirmation of the usage terms of the account that generated it (5.7) | the store listing (P8) | this update; T0.3 only corrects internal wording |
| A physical Android device, ideally mid-range | T6.6 | all emulator gates |
| Five first-time playtesters | T7.2, the comprehension and replay-interest gate | all engineering |

Everything else was decided with a reversible default, listed in section 4.8.

---

## 11. Independent review of this plan

A fresh agent with no access to the planners' reasoning was given the plan, the owner's brief, the review, the GDD and
the repository, and was asked to attack it: omitted report items, already-fixed issues scheduled by mistake,
unsupported asset assumptions, state and replay inconsistencies, customer-economy inflation, excessive scope, vague
acceptance, the handoff code and GDD conflicts. It read the plan after the 0.6.0 reconciliation and executed nothing.
It returned 2 blockers, 12 major and 22 minor findings, 13 vague acceptance lines, 16 review recommendations without
an adequate disposition and a list of unmet spec sentences; it also confirmed about forty source, GDD and asset
claims, including every F01-F11 reference and that the handoff tests compile against the real APIs. Its report is
`docs/major_update_evidence/05_adversarial_review.md`. Each finding was checked against the source before it was
accepted; every accepted blocker and major changed the plan's text.

| ID | Severity | Finding in one line | Disposition |
|---|---|---|---|
| B1 | blocker | the golden hash covered the encoded save, which changes whenever a defaulted field is added, so it could not prove neutrality | accepted: T0.6 now compares RNG stream states and an explicit gameplay projection (handoff code rewritten; 6.2, 7, 9.2 reworded) |
| B2 | blocker | the acceptance band mixed sell rate with conversion, used thresholds the baseline already fails, and demanded M4 results at M3 | accepted: 2.3 corrected (conversion 23.1 % and 21.7 %); 4.3 split into an M3 band and an M4 band with relative thresholds; M0's gate re-issues every baseline; the harness is retired then |
| M1 | major | keeping the ended run created two copies of the legacy profile; a replayed End Day could rewrite the claimed profile; Claim / BuyUpgrade / BeginEra were undefined on the wrong run state | accepted: 6.1 rules 4a-4f and 6 (`EngineFault`); five tests added to T1.1 |
| M2 | major | the planning lock read a stale cursor; Blessing and Fallen had no stage; `load` was outside the mutex; "nothing has changed" could be false; the old dismissal key could be lost | accepted: `GameSession.pending`, one mutex for every entry point, the confirmation read of rule 5, `TOMORROW` shared by the three endings and awaited, the old key persisted as `DONE` (6.1, 6.5) |
| M3 | major | T1.5 was both first and after T1.2, and landing it first would reject every command on an existing save | accepted: split into T1.5a (neutral, first) and T1.5b (after T1.2) |
| M4 | major | one rules bump and one schema bump contradicted the plan's own policy and the offer to release at M2 | accepted: a bump per outcome-changing slice and a schema bump whenever an older build must refuse the save (6.7 table per slice) |
| M5 | major | the guaranteed seats were taken in ID order, so fairness broke exactly under saturation; the blessing term vanished three tasks early | accepted: longest waiters first with weighted draws, a stated waiting bound, empty-shelf visits neutral, the blessing term kept until T3.6, three saturation tests (4.2) |
| M6 | major | the opening shelf was captured after commissions could take a shelf blade; `Sale.listedPrice` could not be null | accepted: captured at the top of `endDay`; nullable price; one appearance per hero per day (6.2, T2.1) |
| M7 | major | task rows contradicted the ownership table; red contract commits on `main` | accepted: every row names who edits which file; contract commits go to an integration branch per slice (7, 8.1, 8.2) |
| M8 | major | "50 faces" was unproven for opaque, darker reference tiles; the pack's own metadata had no disposition | accepted: 25 faces is the guaranteed target; the second set is conditional on a three-step gate in M2 with a class-sign check and the owner's sign-off (5.3, 5.4); rows added to 5.2 for `hero_variants.csv`, `asset_inventory.csv`, `manifest.json`, `palette.gpl`, the build scripts and previews; the pack-portrait arm is an owner-visible default in 4.8 |
| M9 | major | nothing checked that the stacked easing does not make survival trivial | accepted as a tripwire, not a cap: the EXPERT bot and three stop-and-ask conditions (4.3); per-loop survival bounds; step 6b; T5.5 re-runs after any later change |
| M10 | major | milestones lacked commands, integration boundary, save effect, outcome separation and fallbacks; 3.5 lacked Status and Needs; C01-C10 was one row | accepted: section 7 rewritten with all fields per milestone; 3.5 regenerated; ten checklist rows with their real status |
| M11 | major | the bots were filed in M5 but needed by M3 and M4 gates | accepted: moved to M0 as T0.7; SIM parts named in T4.1, T4.2, T4.3, T4.6 |
| M12 | major | "returnable blades become terminal" contradicted the no-deletion rule; no decision for dead heroes or hot state | accepted: the sub-rule is dropped; both decisions and the hot-state definition are in T6.3a |
| m1 | minor | `Battle.pressureWord` does not exist; T1.9 changes outcomes | accepted: `Battle.warnOfSiege`; T1.9 listed as outcome-changing |
| m2 | minor | stale statements after the reconciliation; C13 already applied | accepted: 2.3-2.5 and 3.4 corrected |
| m3 | minor | a second schema tripwire in `EventCompactionTest` | accepted: named in T1.5b and 6.7 |
| m4 | minor | the fixture test passed before the fix | accepted: replaced by `anOldSavesLastReportIsBuiltFromTheArchive` and the `recordVersion == 0` rule (6.4) |
| m5 | minor | `DayCursor` was serializable in a module without the plugin | accepted: declared in `core/persistence` |
| m6 | minor | repository and session both claimed the decode | accepted: the repository returns raw rows |
| m7 | minor | the session test would race if the session named `Dispatchers.IO`; `kotlin.test.Test` unverified in the app module | accepted: rule 10; app tests use `org.junit.Test` |
| m8 | minor | `--set` by reflection is not available in `:core` | accepted: a hand-written allowlist |
| m9 | minor | "always featured" collided with three slots | accepted: precedence rule and test (6.3) |
| m10 | minor | "decided" would sit on most armed wins | accepted: two recorded counterfactuals with exact wording; no card says "decided"; a 10-35 % target (6.2, 6.3, 1.3) |
| m11 | minor | string-keyed bags and members without a consumer | accepted: typed `Recognition` and `Lead`; `OTHER` and `notes` removed |
| m12 | minor | "exactly two draws" is wrong for descendants; old names break the new rules | accepted: "same draw count as today"; the four names leave the pool (4.4) |
| m13 | minor | only heroes got numeric order | accepted: `IdOrder.numeric` at every tie-break (4.2, T3.1) |
| m14 | minor | catalyst jobs duplicated techniques | accepted: identity through clues and honest text; the four mechanical jobs are deferred; T4.4 withdrawn |
| m15 | minor | T3.8 adds a lethal rule in the riskiest milestone | partly accepted: it stays, because the owner listed the gap, but as the last task of M3, run only after the band is met, with removal as the default outcome if the band moves |
| m16 | minor | a 0.6.0 run admitted mid-way meets the new threat numbers with eight heroes | accepted: sweep step 4b |
| m17 | minor | no default speed and no beat lengths | accepted: default "Tap"; timing table and a measured day-length budget (5.3, 6.5, T3.7) |
| m18 | minor | a kill is not memory pressure; budgets passed either way | accepted: trim-memory and finish-activities steps, a 2 GB AVD run, hard against report-only budgets (T6.3b, 9.4) |
| m19 | minor | `wielding` against `equipped`; two scene heights; cell files outside the pipeline | accepted: renamed; one height; files move to `tools/pixelart/cells/` in T2.4 |
| m20 | minor | a returned legend with live affixes is stronger than today's | accepted: affixes return dormant until honed; T4.5 listed as outcome-changing with a `--legends` arm |
| m21 | minor | deferring audio leaves a GDD 19 line open; the four-destination change was not recorded as a departure | accepted: 2.5 item 9; T7.3 marks the line open |
| m22 | minor | the CI row assumed the runner's toolchain | accepted: setup steps named; done only when green |
| vague lines | | thirteen acceptance lines were not checkable | accepted: each replaced by a measurable one in its task row (owner sign-off with a screenshot path where the judgement is aesthetic) |
| review items | | sixteen recommendations without an adequate disposition | accepted: affixes and fame in the purchase comparison (4.7, T4.2); "Who is buying" (T2.8b, `Demand.summary`); one affix prefix in a name (T4.5); following one blade (T2.9 scenario, 9.8); days to a chosen recipe and experiments (T5.3); "Use" on understood journal rows (T4.3); FREE_LISTINGS (T0.7); a kill around a forge commit (T2.3); high-reputation fairness (4.2); hot state and hero records (T6.3a); C12's open line; C13 not re-done; memory pressure (T6.3b); playtest questions for a price or material change and for a want (9.7) |
| spec sentences | | unmet or partial sentences of the brief | accepted: playtests now ask about the number of customers (9.7); proportions get a stated noise rule (4.3); palette, type and timing are specified (5.3); two version constants removed (6.3, 6.5) |

Left unverified by the reviewer and still unverified: Gradle behaviour under AGP 9's built-in Kotlin for the
serialization plugin and the `kotlin-test` variant (the plan no longer depends on either); what the CI runner
provides; Android behaviour at targetSdk 37 on tablets and under Auto Backup; which existing tests change when
`DayResolution.events` grows (T1.4 step 4 checks it); the measured art cell coordinates beyond the contact sheets; name-rule
satisfiability for 120 + 96 names (`LaunchContentTest.namePoolsObeyTheAuthoringRules` is the check).

---

## Implementation handoff

**First vertical slice:** M0 and M1 together ("a save you can trust and a report that tells the truth"). It is the
smallest slice that is complete on its own, it unblocks every later one, and its player-visible result can be shown
on the emulator: two fast upgrade taps both stick, a corrupt save opens a recovery screen, run end survives a kill,
the day report lists the blades forged that day, a request says exactly what it needs.

**Ready to start in parallel on day one:** T0.4 then T0.7 (SIM: metrics, overrides, bots), T2.4 (ART: portrait
re-slice, counter backdrop, `PortraitArt.kt`, allowlists, the art gate), T0.5 (AB: seams), T0.6 and T1.5a (INT),
T0.2 and T0.3 (INT). None of them changes behaviour.

**Contracts to land before workers branch** (each on the slice's integration branch, 8.2): 6.7 part 1 (T1.5a, first
and neutral), 6.1 (T0.5 interface, then T1.1), 6.2 types for money and field results (T1.6), then for M2 the visit
record (T2.1), 6.3 signatures (T2.2) and 6.5 (T2.3).

**Exact starting files:** `core/src/main/kotlin/com/tinyblacksmith/core/engine/GameEngine.kt` (`endDay`, `handle`,
`RULES_VERSION`), `core/.../model/Model.kt` (`MarketVisit`, `DayResolution`, `GameState`, `WeaponLocation`),
`core/.../persistence/SaveCodec.kt`, `core/.../market/Market.kt`, `core/.../battle/Battle.kt`,
`core/src/test/kotlin/com/tinyblacksmith/core/TestSupport.kt`, `app/src/main/java/com/example/blacksmithproject/GameViewModel.kt`,
`app/.../data/SaveStore.kt`, `app/.../MainActivity.kt`, `app/build.gradle.kts`, `tools/pixelart/import_assets.py`,
`core/.../sim/Simulator.kt`.

**Runnable baseline checks:** section 9.1. Baseline: release 0.6.0 (`0ad888a`) with its 10,000-seed balance v5
table. Run during planning on the same sources: core tests 186 / 186, app unit tests 1 / 1, the debug build, lint
(0 errors, 38 warnings) and the 1,000-seed reproduction of 2.3. Not run during planning: instrumented tests and the
device loop (recorded by session 8 for 0.6.0).

**Unresolved blockers:** none for M0-M5. The four inputs of 10.4 block only T6.6, T7.2, the rename in T7.4 and the
store listing. Two gates need the owner's eye during execution and are not blockers until reached: the art gate of
T2.4 (how a customer is drawn, how many faces passed) and the tripwire of 4.3 if it fires.

### First steps of the first slice

#### Task T0.6: golden gameplay projection

**Files:** create `core/src/test/kotlin/com/tinyblacksmith/core/GoldenStateTest.kt`; create
`core/src/test/resources/golden/state_rules1.txt`; modify `core/build.gradle.kts` (one line in `tasks.test`).
**Interfaces:** consumes `GameEngine.newRun`, `GameEngine.handle`, `GameState`. Produces the file every later task
means by "neutral": RNG stream states and gameplay outcomes for 20 seeds x 15 days. It deliberately does **not** hash
the encoded save: `SaveCodec` encodes defaults, so the save's bytes change whenever a field is added.

- [ ] **Step 1: write the test**

```kotlin
package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.WeaponLocation
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Gameplay for fixed seeds and a fixed command script, as an explicit projection: adding a field to the save does not
 * change it, changing an outcome or moving an RNG stream does. A task that claims to alter no outcome leaves it equal.
 */
class GoldenStateTest {
    private val engine = TestSupport.engine
    private val resource = "golden/state_rules${GameEngine.RULES_VERSION}.txt"

    /** Records that describe a day without being gameplay. A task that adds such a type names it here on purpose. */
    private val recordOnly = setOf<String>()          // T1.6 adds "MATERIAL_BOUGHT"; T2.1 adds "SHOP_DAY"

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun location(l: WeaponLocation): String = when (l) {
        WeaponLocation.Storage -> "storage"
        is WeaponLocation.Shelf -> "shelf:${l.price}"
        is WeaponLocation.Owned -> "owned:${l.heroId.value}:${l.equipped}"
        is WeaponLocation.Lost -> "lost:${l.day}:${l.reason}"
        is WeaponLocation.Destroyed -> "destroyed:${l.day}"
    }

    private fun streams(s: GameState): String = s.rng.streams.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }

    /** Adding a line here is a deliberate edit; nothing enters the projection because a model class grew a field. */
    private fun project(s: GameState): String = buildString {
        appendLine("day=${s.day} phase=${s.phase} gold=${s.gold} energy=${s.energy} overwork=${s.overworkToday} reputation=${s.reputation}")
        appendLine("materials=" + s.materials.entries.sortedBy { it.key.value }.joinToString(",") { "${it.key.value}:${it.value}" })
        appendLine("supplier=" + s.supplierStock.entries.sortedBy { it.key.value }.joinToString(",") { "${it.key.value}:${it.value}" })
        appendLine("tools=${s.tools.toSortedMap()} flags=${s.worldFlags.toSortedMap()} milestones=${s.milestones.sorted()}")
        appendLine("town=${s.town.integrity}/${s.town.militia}/${s.town.armory}/${s.town.nextSiegeDay}/${s.town.siegesSurvived}/${s.town.siegesLost}" +
            " champions=${s.town.championIds.map { it.value }} guilds=${s.town.guilds.size}")
        appendLine("factions=" + s.factions.values.sortedBy { it.id.value }.joinToString(",") { "${it.id.value}:${it.pressure}" })
        appendLine("blessings=" + s.blessings.joinToString(",") { "${it.id.value}:${it.expiresDay}" } + " offer=${s.pendingBlessingOffer.map { it.value }}")
        appendLine("commissions=" + s.commissions.values.sortedBy { it.id.value }.joinToString(",") { "${it.id.value}:${it.status}:${it.deliveredWeaponId?.value}" })
        for (h in s.heroes.values.sortedBy { it.id.value })
            appendLine("hero ${h.id.value} ${h.classId.value} level=${h.level} xp=${h.xp} gold=${h.gold} health=${h.health} ${h.fate}" +
                " loyalty=${h.loyalty} fame=${h.fame} kills=${h.kills} victories=${h.victories} guild=${h.guildId} ambitionDone=${h.ambitionDone}")
        for (w in s.weapons.values.sortedBy { it.id.value })
            appendLine("weapon ${w.id.value} ${location(w.location)} quality=${w.quality} power=${w.power} condition=${w.condition}" +
                " kills=${w.kills} fame=${w.fame} title=${w.title}")
        appendLine("journal=" + s.legacy.journal.interactions.toSortedMap())
        for (e in s.events) if (e.type.name !in recordOnly) appendLine("event ${e.day} ${e.type} ${e.subjectIds}")
    }

    private fun GameState.tryRun(command: Command): GameState = (engine.handle(this, command) as? CommandOutcome.Accepted)?.state ?: this

    /** Each morning: restock iron and ember resin when out, forge up to three quick swords, list every stored blade, end the day. */
    private fun play(seed: Long, days: Int): List<String> {
        var s = engine.newRun(LegacyProfile(), seed)
        val out = mutableListOf<String>()
        for (n in 1..days) {
            if (s.isEnded) { out += "$seed:$n:ended:ended"; continue }
            repeat(3) {
                if ((s.materials[LaunchContent.IRON] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.IRON))
                if ((s.materials[LaunchContent.EMBER_RESIN] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.EMBER_RESIN))
                s = s.tryRun(quickSword())
            }
            for (w in s.storedWeapons()) s = s.tryRun(Command.ToggleShelf(w.id, listed = true))
            s = (engine.handle(s, Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted).state
            out += "$seed:$n:${sha(streams(s))}:${sha(project(s))}"
        }
        return out
    }

    @Test
    fun rngStreamsAndGameplayMatchTheRecordedProjection() {
        val actual = (1L..20L).flatMap { play(it, 15) }
        if (System.getProperty("golden.record") == "true") {
            File("src/test/resources/$resource").apply { parentFile.mkdirs() }.writeText(actual.joinToString("\n"))
            return   // the classpath copy is stale in the recording run; the next run compares
        }
        val expected = javaClass.classLoader.getResource(resource)?.readText()?.lines()?.filter { it.isNotBlank() }
            ?: error("No golden file $resource: run with -Dgolden.record=true once and commit it")
        fun rngOnly(lines: List<String>) = lines.map { it.split(":").take(3).joinToString(":") }
        assertEquals(rngOnly(expected), rngOnly(actual), "an RNG stream moved: this change draws, or stops drawing, gameplay randomness")
        assertEquals(expected, actual, "RNG streams are equal but an outcome changed")
    }
}
```

- [ ] **Step 2: run it and see it fail for the right reason.** `./gradlew :core:test --tests "*GoldenStateTest*"`.
  Expected: FAIL with "No golden file golden/state_rules1.txt". If the `when` over `WeaponLocation` does not compile,
  a subclass was added since this plan was written: add its branch.
- [ ] **Step 3: record.** Add `systemProperty("golden.record", System.getProperty("golden.record") ?: "false")`
  inside `tasks.test { }` in `core/build.gradle.kts`, then
  `./gradlew :core:test --tests "*GoldenStateTest*" -Dgolden.record=true`. Expected: PASS, and the file appears with
  300 lines (20 seeds x 15 days).
- [ ] **Step 4: run twice more without the flag.** Expected: PASS both times.
- [ ] **Step 5: prove the projection ignores a new field.** Temporarily add `val scratch: Int = 0` to `Hero`, run the
  test (expected: PASS), remove the field.
- [ ] **Step 6: commit** `core/build.gradle.kts`, the test and the resource: "Golden gameplay projection for rules 1".

#### Task T1.4: one complete day edition (F03)

**Files:** create `core/src/test/kotlin/com/tinyblacksmith/core/DayEditionTest.kt`; modify
`core/src/main/kotlin/com/tinyblacksmith/core/engine/GameEngine.kt` (`endDay`, the line
`val dayEvents = ctx.newEvents.toList()`); then (AB) the three readers in `app/.../ui/HomePanel.kt`, `InfoPanels.kt`
(`GazettePanel`) and `Dialogs.kt`.
**Interfaces:** produces `DayResolution.events` = every record of the resolved day; `CommandOutcome.Accepted.events`
is unchanged ("emitted by this command").

- [ ] **Step 1: write the failing test**

```kotlin
package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.LegacyProfile
import kotlin.test.Test
import kotlin.test.assertEquals

/** One edition per day: what the report shows is what the archive keeps (review F03). */
class DayEditionTest {
    private val engine = TestSupport.engine

    @Test
    fun theDayReportCarriesPreparationRecordsExactlyOnce() {
        val start = engine.newRun(LegacyProfile(), 7L).withMaterials()
        val forged = start.forgeAccepted(quickSword())
        val listed = forged.state.run(Command.ToggleShelf(forged.forgedWeaponId!!, listed = true))
        val day = listed.day
        val out = listed.endDayAccepted()
        val report = out.resolution!!.events
        assertEquals(out.state.eventsForDay(day).map { it.id }, report.map { it.id }, "report and archive hold the same records")
        assertEquals(1, report.count { it.type == EventType.WEAPON_FORGED }, "the blade forged today is in today's report")
        assertEquals(report.size, report.map { it.id }.toSet().size, "no record appears twice")
    }
}
```

- [ ] **Step 2: run it.** `./gradlew :core:test --tests "*DayEditionTest*"`. Expected: FAIL on "report and archive
  hold the same records" (the report lacks `WEAPON_FORGED`, `WEAPON_LISTED` and the day-1 `RUN_STARTED` records).
- [ ] **Step 3: minimal change** in `GameEngine.endDay`:

```kotlin
        // 10. Gazette and new morning. The edition is the whole day, preparation included (taken before compaction).
        val dayEvents = ctx.events.filter { it.era == ctx.era && it.day == day }
```

- [ ] **Step 4: run the whole suite.** `./gradlew :core:test`. Expected: `DayEditionTest` and `GoldenStateTest` pass
  (only `lastResolution` changed). Any existing test that counted `resolution.events` now sees the preparation
  records: correct the expectation to the whole day, do not filter them back out. The simulator reads
  `resolution.events` for sales, fates and activities; those types are emitted only at End Day, so its table must be
  unchanged: confirm with the 1,000-seed command of 9.1.
- [ ] **Step 5: add** `reportAndArchiveAreTheSameEdition` (hone and buy a tool as well; compare
  `Gazette.edition(resolution.events, ...)` with the edition of `eventsForDay`), `aSignatureForgedWhilePlanningLeadsThePaper`,
  and `anOldSavesLastReportIsBuiltFromTheArchive`: load the v1 fixture as `SaveFixtureTest` does; its stored
  `lastResolution` holds 15 records for day 60 while `eventsForDay(60)` holds 20; assert the edition helper used by
  the app for a `recordVersion == 0` day returns all 20.
- [ ] **Step 6 (AB): point the three app readers at one edition** (`lastResolution.events` for the latest day when
  its `recordVersion` is at least 1, `eventsForDay(day)` otherwise, both through `Gazette.edition`);
  `./gradlew :app:compileDebugKotlin`.
- [ ] **Step 7: commit** "Day report carries the whole day, preparation included (F03)", with a `CHANGELOG.md` line.

#### Task T0.5 then T1.1: the repository seam and the first race test

**Files:** create `app/src/main/java/com/example/blacksmithproject/data/GameRepository.kt`,
`app/src/test/java/com/example/blacksmithproject/FakeGameRepository.kt`,
`app/src/test/java/com/example/blacksmithproject/GameSessionTest.kt`,
`app/src/main/java/com/example/blacksmithproject/GameSession.kt`; modify `app/build.gradle.kts`
(`testImplementation(libs.kotlinx.coroutines.test)`), `app/.../data/SaveStore.kt` (`class SaveStore(private val dao: SaveDao) : GameRepository`).
**Interfaces:** section 6.1, verbatim. App tests use `org.junit.Test` and `org.junit.Assert`, as the existing app
test does.

- [ ] **Step 1: the fake** (it stores the rows as text, exactly like the real store)

```kotlin
package com.example.blacksmithproject

import com.example.blacksmithproject.data.GameRepository
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.data.StoredRows
import kotlinx.coroutines.CompletableDeferred

/** In-memory rows. Can hold each commit until released and can fail on demand. */
class FakeGameRepository(var run: String? = null, var legacy: String? = null) : GameRepository {
    var cursor: String? = null
    var hold = false
    val gates = ArrayDeque<CompletableDeferred<Unit>>()
    var failNextCommit: Throwable? = null
    var commitCount = 0
    val quarantined = mutableMapOf<String, String>()

    override suspend fun load(): StoredRows = StoredRows(run, legacy, cursor)

    override suspend fun commit(run: String?, legacy: String) {
        if (hold) CompletableDeferred<Unit>().also { gates += it }.await()
        failNextCommit?.let { failNextCommit = null; throw SaveFailure.Io(it) }
        this.run = run; this.legacy = legacy; commitCount++
    }
    override suspend fun saveCursor(cursor: String?) { this.cursor = cursor }
    override suspend fun quarantine(key: String) { if (key == "run") { run?.let { quarantined["run.bak"] = it }; run = null } }
}
```

- [ ] **Step 2: the first failing test**

```kotlin
package com.example.blacksmithproject

import com.example.blacksmithproject.GameSession.Op
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameSessionTest {
    private val engine = GameEngine()

    /** A smith who never forges falls at the second siege: a real ended run without test doubles. */
    private fun endedRun(): GameState {
        var s = engine.newRun(LegacyProfile(), 42L)
        while (!s.isEnded) s = (engine.handle(s, Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted).state
        return s
    }

    @Test
    fun twoUpgradesIssuedTogetherBothSurviveAndOnlyOneCommitIsEverInFlight() = runTest {
        val ended = endedRun()
        val legacy = LegacyProfile(points = 40, claimedRunIds = setOf(ended.runId.value))
        val repo = FakeGameRepository(SaveCodec.encodeRun(ended), SaveCodec.encodeLegacy(legacy))
        val session = GameSession(engine, repo, compute = StandardTestDispatcher(testScheduler))
        session.load()
        repo.hold = true            // from here every commit waits until the test releases it

        val a = async { session.run(Op.BuyUpgrade(LaunchContent.UPG_WALLS, ended.runId)) }
        val b = async { session.run(Op.BuyUpgrade(LaunchContent.UPG_ENERGY, ended.runId)) }
        runCurrent()
        assertEquals("only one commit may be in flight", 1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertEquals("the second commit starts only after the first finished", 1, repo.gates.size)
        repo.gates.removeFirst().complete(Unit); runCurrent()
        assertTrue(a.await() is GameSession.Result.Done && b.await() is GameSession.Result.Done)

        val stored = SaveCodec.decodeLegacy(repo.legacy!!)
        assertEquals(1, stored.upgradeLevel(LaunchContent.UPG_WALLS))
        assertEquals(1, stored.upgradeLevel(LaunchContent.UPG_ENERGY))
        assertEquals("both purchases were paid for exactly once", 40 - 8 - 8, stored.points)
        assertEquals("the ended run is kept, never deleted by an upgrade", ended.runId, SaveCodec.decodeRun(repo.run!!).runId)
        assertEquals("what is shown is what is stored", stored, session.snapshot.value?.legacy)
    }
}
```

- [ ] **Step 3: run it.** `./gradlew :app:testDebugUnitTest --tests "*GameSessionTest*"`. Expected: does not
  compile (`GameSession` does not exist). Create the class with the signatures of 6.1 and bodies that
  `TODO()`; run again; expected: FAIL at `session.load()`.
- [ ] **Step 4: implement `load` and `run` for `BuyUpgrade`** exactly as rules 1-4 and 7 of 6.1 say (one `Mutex`;
  read `snapshot.value` inside it; `Stale` on a `runId` mismatch; rule 4d; `engine.purchaseUpgrade(snapshot.legacy, id)`;
  encode on `compute`; commit `(snapshot.run, newLegacy)`; publish after the commit under `NonCancellable`). The
  session must not name `Dispatchers.IO`, or `runCurrent()` cannot drive the commit in this test. Run: PASS.
  Repeat the test with `b` issued before `a`.
- [ ] **Step 5: add the next tests one at a time, each failing first** (names in the T1.1 row of section 7):
  upgrade against Begin era in both arrival orders; a queued End Day against an old run; double Begin era; double
  End Day; double Claim; run end after recreation (a second `GameSession` on the same repository shows the ended run
  and `claimed`); nothing published before the save completes; then the five rule-4 and rule-6 tests
  (`anEndDayReplayAfterClaimNeverRewritesTheLegacyRow`, `buyUpgradeDuringALiveRunIsRejected`, `claimOnALiveRunIsRejected`,
  `beginEraBeforeClaimIsRejected`, `anEngineFaultIsNotRetried`) and `aSecondLoadCannotPublishOverANewerSnapshot`.
- [ ] **Step 6: wire `GameViewModel` to the session** (T1.3) only after all of them pass; `./gradlew :app:assembleDebug`;
  install; run `tools/emulator/runend.sh` with a `force-stop` after Claim.
- [ ] **Step 7: commit** per task on the `shop-day/m1` branch, each with its `CHANGELOG.md` line where the player can
  see the change; merge to `main` when green.
