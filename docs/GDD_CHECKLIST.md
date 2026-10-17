# GDD checklist — built vs. left

One line per feature, under the section numbers of **GDD v2.0** (`Tiny_Blacksmith_GDD.md`, 2026-10-14). `[x]` = built and
verified (evidence in PROGRESS.md, DECISIONS.md or the feature's ledger); `[ ]` = open, with the missing piece named so
ticking it means done. Tick items here when they ship. IMPLEMENTATION_PLAN.md is the phase-gate view; this is the feature
view. DEFERRED scope (GDD v2.0 §21.2) is not listed and is not built.

**State this file describes:** branch tip after GDD v2.0 (2026-10-14): app 0.8.0 plus `[Unreleased]` (target 0.9.0, held by
owner decision F), rules 6, save schema 7, content 6, balance 15. `:core:test` 633 / 0 failures (2026-10-14, cloud session,
core-only build, after decisions A, J, K, L and the Heartfire confirmation). `:app:testDebugUnitTest` 246 / 0 at a2e0392 (review of 2026-10-14); not re-run since (the cloud session
cannot reach Google Maven). Instrumented: 75 run, 8 to 10 failing at the review, all stale assertions from the copy remaster.

State on 2026-10-14: the review's work is on branch `review-2026-10-14`, not merged; what changed and how it was checked is
in PROGRESS (cloud session lines). The counts below are as of their own dates.

**How a line is ticked:** a rule that lives in `:core` is ticked when its JVM tests pass, and the line says "JVM tests";
something the player sees is ticked when it was seen running on an emulator or a device test that draws it passes, and the
line says which. A device test proves a screen draws and says the right thing at that size; it does not prove it looks right.
Nothing in this file was checked on a physical phone, and TalkBack was never run.

**History of this file.** Reviewed 2026-10-09 (app 0.6.0, balance 5, 186 JVM tests; wording corrections C01-C09 and C12 of
`docs/Tiny_Blacksmith_Thorough_Review_2026-10-09.md` §9 applied: a ticked line states only what its evidence shows).
Reviewed 2026-10-10 for the major update (app 0.7.0, rules 3, schema 4, balance 8, content 3; 393 core and 122 app JVM
tests; "final gate" = the device run on 2eca650: 56 of 56 device tests, `smoke.sh`, `runend.sh` and a main-menu walk-through
on the Pixel_10_Pro emulator at 1080x1920, density 420, font 1.0). Re-sectioned to GDD v2.0 on 2026-10-14, with the stale
claims of review 2026-10-14 appendix 01 §3 corrected. Lines that cite "the final gate" refer to that 0.7.0 device run.

## 3 The two kinds of run
- [x] A run begins under a charter (three) or without one (the classic game); the mode never changes; a pre-guild save stays classic (JVM tests: `GuildRunTest`)
- [x] A classic run refuses every guild command and draws as before the guild update (JVM tests)
- [x] Run setup screen: every charter and "No charter" with gains and costs as bullets, one primary action (commit 99354a8; emulator at font 1.0 and 2.0)

## 4 Player loop and day flow
- [x] Run creation: seed, era, random faction pressures and world condition, 100 integrity, 12 residents, legacy unlocks
- [x] Planning: nothing advances without End Day; every command validated whole, invariants after each
- [x] End Day in one fixed order (GDD v2.0 §4.3), idempotent per command ID (30 kept); the order is pinned by the golden state, not asserted step by step
- [x] Every authoritative operation (End Day, legacy purchase, claim, Begin era, abandon) goes through one serialized session, so a run transition cannot race a stale write (review F01; T1.1, T1.3: `GameSessionTest`; on the emulator a force-stop after Claim and around End Day never left a half-written run)
- [x] Run end at integrity 0: summary, claim-once legacy, next era
- [x] Abandon: no points, legends or lineage; recipe notes made in the run stay (owner ruling 2026-10-14: keep them). Main menu walk-through in the final gate
- [x] The shop day has a saved position: closing or killing the game mid-day offers "Resume the day" or "Skip to tomorrow", and watching, skipping or killing never changes the day (T2.3: emulator, seed 42, including ten kills within 400 ms of End Day)
- [x] Resume after the system closes the game: the day and its position over a real save file, the main menu staying closed, the open Storage sheet and its order (T6.4, `ShopDayPersistenceTest`, 3 device tests in the final gate; the smoke script resumes after a process kill on day 2). The forge draft, the forge result and the open destination are covered by JVM tests only (`SavedStateTest`, `LifecycleTest`). Not tested: "Select weapons" mode in Storage after a restore
- [ ] (no longer applies) Home dashboard: replaced by the Shop page in the major update (T2.8b)

## 5 Forging, materials and discovery
- [x] 6 families, 16 materials (6 metals, 6 augments, 4 catalysts), data-driven with hidden affinities
- [x] Quick Forge (2 energy), Advanced Forge (4 energy, catalyst, Temper / Quench / Etch), Heartfire forging (+2 energy, +1 Heartfire)
- [x] 10 energy, up to 4 overwork, next-day exhaustion
- [x] Safe / Balanced / Reckless rolls (Safe spread 6, Reckless flaws 30 % and +1 slot), GDD-shaped quality and power formulas retuned (DECISIONS "Balance v2"), always a usable weapon, a defect attaches a flaw
- [x] Rarity ladder of rules 6: quality gives at most Epic, Epic only in Starsteel / Moonsteel, Legendary only by a Heartfire breakthrough or by deeds (renown 3); 0 Legendary from quality in every table (RUN_VARIETY G1; `RunVarietyTest`, JVM)
- [x] Fine metals: the supplier carries Starsteel from day 6 and Moonsteel from day 11, in both run kinds; earlier only from dangerous work (G10 met: first Epic median day 7 in a guild run; classic Epic by day 5 in 16 to 22 % of runs)
- [x] Heartfire: run-only, held at most 3; classic from the 4th siege won, guild from starfall, deep hall, recovery and a warlord on the wall (JVM tests; both forgings seen on the emulator, G14)
- [x] 12 beneficial affixes and 6 flaws, each with a gameplay effect in both combat models
- [x] 24 signature recipes (4 per family), discoverable through experiments and clues; a known signature transforms 75 % of the time
- [x] Notebook UNKNOWN -> OBSERVED -> UNDERSTOOD -> SIGNATURE_DISCOVERED, live hints in words, no farming, survives death
- [x] Clue ladder (four rungs), rumours and recall (T4.3, JVM tests and simulator). The ladder and "Use this recipe" in Records: device test in the final gate (1080x1920, font 1.0)
- [x] Hone (never promotes past the metal's cap, never Legendary), salvage, bulk scrap, give to the watch
- [x] Workshop tools: Great Bellows, Master Whetstone, Painted Signboard, Display Case
- [x] Guild-run fight rules: one resolver composes a weapon's rules; metals have rules on weapons forged from rules 6 on (`Weapon.craft`); forge outlook; the bench (DEEP_FORGING F1-F9, JVM tests; see the deep-forging section below)
- [ ] Heartfire spend asks for a confirmation stating cost and consequence (owner ruling 2026-10-14). Built 2026-10-14; app tests written, not yet run: tick after `:app:testDebugUnitTest` and the named device test or script pass (`RunVarietyUiTest`, `runvariety.sh`)
- [ ] The workbench's Heartfire source line names the classic source (a siege won from the 4th) in a classic run. Today it lists guild sources only

## 6 Living weapons
- [x] Lifecycle with one authoritative location (storage, shelf, owned, loaned, lost, destroyed), history entries, titles from real records
- [x] On death: recovered by the shop, seized by the enemy, inherited by a guildmate, resold by a merchant, or lost; on retirement: inherited by the mentee
- [x] Bounded Legend Board; a famous weapon returns dormant in a later era, at most Epic and without its legend origin (rules 6 D13)
- [x] Fame as a capped effect (power, desire, price); wear and condition
- [x] A returned legend is the weapon it was (signature, flaws, catalyst, title, story), its properties dormant until honed once (T4.5, `ArtifactFidelityTest`, JVM). Story section and dormant marker: device test in the final gate
- [x] Deeds and renown (rules 6): Legendary by deeds once at renown 3, with a line in its history; the sheet says which way (JVM tests; legend lines seen on the emulator, G14)
- [x] A champion can fall on the walls when a siege is lost badly (the rout rule, T3.8; JVM tests and simulator: 17-19 % of plain first eras). A "fell at the wall" card was not seen on a device

## 7 Shop, economy and customers
- [x] 250 gold, 8 shelves (+2 Display Case), supplier with always-stocked basics; 0 hard-locks for the recorded simulator policies (not shown for every player path)
- [ ] Adversarial recovery: shock scenarios (no gold and no stock, empty shelves, a lost siege) shown to recover (review G10; plan T5.2). Measured at balance 6 (stuck streaks of three days or more in 0.00-0.10 % of novice runs; an over-spending bot 1.9-3.0 %) and at balance 8 over 10,000 runs (worst bot 0.07-0.09 % of days, the plain smith 0). Not run: prices at 70 % and 180 %, a material-poor start, half the town
- [x] List / unlist, manual price per weapon, suggested price, ±10; the price is also chosen on the forge result with a count of the heroes who can afford it (seen on an emulator at 411x731 and 360x640 dp, font 1.0 and 1.3)
- [ ] Forge result: "List at suggested" primary, "Set price" secondary opening the editor (owner decision K). Built 2026-10-14; app tests written, not yet run: tick after `:app:testDebugUnitTest` and the named device test or script pass (`ForgeResultCardTest`)
- [ ] Forge result: one line saying whether the weapon is reserved and for whose commission, from `Weapon.promisedTo` (owner decision L). Built 2026-10-14; app tests written, not yet run: tick after `:app:testDebugUnitTest` and the named device test or script pass (`ForgeResultTest`, `ForgeResultCardTest`)
- [x] Utility purchase (improvement, class fit, element taste, traits, loyalty, price), one purchase a visitor, reasons in words; trade-ins at 40 %
- [x] Fair customer selection: every living hero gets a fair turn at the counter (review F05; T3.1, simulator: served ratio by list position 0.39 to 0.97); 6 seats
- [x] Standing wants, the sidegrade gate and siege demand (T4.1, T4.2; JVM tests and simulator). Their screens: device tests in the final gate
- [x] Reputation and per-hero loyalty; both reset per run except the Known Name perk
- [x] Gifts (owner decision M, balance 15): below 25 % of asking is a gift, at most +1 reputation a day from gifts, gift loyalty capped at 2 (JVM tests; simulator, 1,000 runs)
- [x] Commissions: reason, family, quality band, element, reward, deadline; accept / decline; up to two open; delivered before browsers with the least weapon that fits (T1.8, T4.6; JVM tests and simulator). The "why" line: device test in the final gate
- [x] Signboard tool: +1 customer seat a day per level

## 8 Heroes and the town
- [x] 5 classes, 9 traits each wired to a decision weight, element taste, purse, ambition, deterministic names and stored faces
- [x] Daily choice of expedition, patrol, rest, guild hall, ambition work by trait-weighted seeded utility; wounded rest, dead never act
- [x] Scalar effective-power formula; a suited weapon measurably helps
- [x] Three defenders chosen as the strongest available heroes (health gate), 0-2 handled
- [x] Defender ranking in a warlord siege uses the same foe context as the outlook (review F08; T1.7, JVM tests)
- [x] A town of 12 with room for 16, all five classes from day one; 120 first names and 96 surnames (T3.2-T3.4; JVM tests and simulator at three base seeds)
- [ ] Recognition lines at the counter (T3.5): JVM-tested; one line was seen on the emulator on day 1; the regular's mark was not verified there
- [x] Level-ups, fame, retirement, guild founding in life and at retirement, mentoring, newcomers when the population thins
- [x] Lineage anchors across eras; descendants reference stored ancestors

## 9 Factions, threat and sieges
- [x] 3 factions with pressure 0-100, growth, suppression, weakness / resistance, elites, warlords
- [x] Sieges on days 5 / 10 / 15 ..., warnings ahead in words, the besieger committed at the first warning, forecast in Town
- [x] Raid / defence / forge-damage scaffolds (retuned), militia from patrols, bounded hero-driven recovery (2 a day), no gold repair
- [x] Siege traits: Long Assault and Many Breaches, named when scheduled
- [ ] Owner direction (2026-10-14): differentiate the two Frost-weak, Fire-resistant factions by tactical identity; change affinities only if needed. Not started

## 10 Combat
- [x] Scalar resolver for every classic fight and residents' expeditions; one deterministic event series; read-only replays
- [x] Interaction engine: typed events and limited effects; five class kits; enemy units per faction; three highlights from the real chain (guild evolution, JVM tests)
- [ ] Residents' own expeditions through the interaction engine

## 11 Run variety
- [x] 25 events (23 pooled + 2 generational rules), each with eligibility, weight, limits, cooldown, story; history events ineligible without history; all reachable (`WorldEventReachabilityTest`)
- [x] Morning visitors: 8 plus a follow-up, stored in the save, one explicit answer, a stated default at End Day; three old events converted without a second payout
- [x] Workshop relics: 4, 3 slots, offers on day 1, after sieges 2 and 4 and for a won wager
- [x] 8 blessings, three offered after a held siege; an unanswered offer lapses after 3 mornings (balance 15)
- [x] 10,000-seed gate of the depth update against the matched baseline; exploit probes pass
- [x] Owner ruling (2026-10-14) on the first-era band: keep the current balance (plain smith median 25); reassess by minutes per run and how repetitive the last days feel
- [ ] Owner decision: options no bot prefers (the master's paid lesson, the crate's plain metal) and the wager won 94 to 98 % of the time
- [ ] Owner decision: Guild Patronage meets no target (+0.6 days or +0.3 sales a day) at any setting
- [ ] Visitor portraits and relic / trait icons (wanted lists in `Assets/`)

## 12 The guild run
Per-task status: `docs/GUILD_EVOLUTION_PLAN.md`, `docs/RUN_VARIETY_LEDGER.md`. Ticked = built and JVM-tested; the ledgers say what was seen on a device.
- [x] Contracted members (up to six), recruiting with disclosed class, trait and fee; members are not residents
- [x] Loans: one weapon, one place, one custodian
- [x] Contract board, one party of one to three, posture; one- and two-day contracts with a checkpoint
- [x] Contract modifiers (seven; G11 met) and laws of the season; the rival guild
- [x] Opening packages: eight, three offered from the charter's list, End Day waits for one; no confirm dialog (decision I)
- [ ] Opening drawbacks that bite: every package has a plan follower within 0.83 to 1.20 of its charter's mean, but no drawback costs more than 5 points and three plans do not clearly pay (G12 partly met)
- [x] Siege preparations announced four days ahead; sabotage spoils them; the two-day siege jobs (sabotage and one opportunity)
- [x] Siege fought by the three on the wall through the interaction engine; reserved defenders; "held, at a cost" in words; cause lines on the siege card
- [x] Wall work draws on the daily recovery allowance plus `wallWorkExtraRecovery` 1 (owner decision B, balance 15). Whether 1 or 3: open, to be decided after repair costs, siege damage and survival are checked
- [x] Wounds, capture and rescue (the watch fills empty places), death, a taken weapon, its nemesis and recovery
- [x] Day-20 charter, retire or continue, legacy counted once; optional ranks
- [ ] Charter secured in 40 to 65 % for each competent policy and charter (G3): one cell over (Synergy under Last Hope 66)
- [x] Four guild visitors; bonds, specialities
- [x] Party relics and pacts; drafts after sieges 1, 2 and 3
- [x] Members at home rest or train automatically (owner decision J)
- [ ] One line on the Guild screen explaining that members at home train and pursue ambitions on their own (decision J). Built 2026-10-14; app tests written, not yet run: tick after `:app:testDebugUnitTest` and the named device test or script pass (`GuildUiTest`)
- [x] Schema 6 with 0.7.0 saves as fixtures; schema 7 for rules 6
- [ ] Secondary imprint, mastery stamp, risk that changes the quirk, tag-aware relic drafts, workshop specialisation
- [ ] Event cadence scheduler; the other eight named choice scenes
- [ ] Human playtest of the two guild gates; phone, large font and TalkBack pass of the guild screens (G14 not met: emulator only)

## 13 Legacy
- [x] Legacy points = 6 + days / 5 + discoveries (cap 5) + milestones (+ charter 6, rank 2 / 4 / 6), claimed once per run
- [x] 11 upgrade tracks at 8 / 20 / 45: energy, gold, mastery, walls, efficiency, stock, luck, reputation, supplier depth, signature odds, legend returns
- [x] Persistence matrix (GDD v2.0 §13.3): notebook, upgrades, Legend Board, lineages, unspent points persist; the rest resets; nothing of the guild persists but its points
- [x] Starting gold / energy upgrades measured on second yardsticks (weapons forged and tool levels by the first siege)
- [x] Upgrade preview words chance tracks without exact odds (decision A applies to previews, ruling 2026-10-14): `Legacy.preview` compares with the level below in words (`LegacyPreviewTest`, `OddsTest`, JVM)

## 14 Gazette, shop day and fight reports
- [x] Daily paper from real event records, priority order, top 5 headlines, archive; edition layout lede, tally, Shop / Heroes / Town / Forge
- [x] The briefing: at most 5 stories and 2 "before tomorrow" notices, mandatory stories never hidden (copy remaster, JVM tests)
- [ ] Paper complete and consistent: the report, the shop and the archive show the same day, and the tally accounts for every gold and hero event (review F03, F07; T1.4, T1.6, T2.1; JVM tests: a fixture day returns 20 of 20 records). The three views were not compared on a device
- [x] The shop day after End Day: customers card by card, receipts, the tally, aftermath and tomorrow's lead, with Next, Back, Skip day and 1x / 2x (T2.5, T2.6: emulator; a blessing day, a siege aftermath and the replay overlay seen in a preview only)
- [x] Siege replay: stepped text and an animated diorama fed by the stored replay only; skippable; reduced motion honoured
- [ ] Guild siege replay with portraits and a battle report (2026-10-14): `combat/Replay.kt`, `ReplayTimeline.kt`, `Account.kt` from the stored fight record (record 1), `ui/replay/`. JVM-tested in core (15 tests plus a guild-siege case: consistency with the record, every point counted once, ticks, heals, multi-target, defeat, resisted, highlights, save round trip, older records refused). Not built or seen on a device: this session had no Android SDK
- [x] Replays for significant expeditions: elite fights and deaths, at most three a day
- [x] Guild fight reports: outcome, both sides, three highlights, the whole timeline

## 15 Disclosure
- [x] Deterministic rules, thresholds, costs, fees, timings and fight rules shown exactly; knowledge, pressure, odds of a siege, health and wear in words; rarity by icon and word
- [ ] Hidden random odds in band words everywhere (owner decision A, extended to previews and tooltips on 2026-10-14). `core/text/Odds` and the upgrade preview pass on the JVM; the workbench's Reckless and Heartfire lines app tests written, not yet run: tick after `:app:testDebugUnitTest` and the named device test or script pass (`RunVarietyUiTest`, `runvariety.sh`)

## 16 Content
- [x] 16 materials, 6 families, 5 classes, 3 factions, 25 events, 24 signatures, 12 affixes, 6 flaws, 8 blessings, 11 upgrade tracks, 4 tools, 9 + 4 visitors, 14 relics, 2 siege traits, guild content (`LaunchContentTest`, `GuildContent` validation)
- [x] `ContentCatalog.validate()` over every ID

## 17 Portrait UX
- [x] Title with the owner's painting; New game / Continue / See how the era ended; Abandon with its confirmation (commit 99354a8; emulator)
- [x] Four destinations (Shop, Forge, Town or Guild, Records) with a settings sheet; the bar no longer clips "Records" at font 2.0 (96 dp)
- [x] The Shop page (threat strip, lead, visitor, relics, shelf, doors, "Who is buying"), the Forge workbench and the Town list: device tests and the smoke script pass in the final gate. Never seen by anyone: the Supplies sheet, the "Fallen and retired" header, a Town row with a guild or mentor line
- [x] Forge flow: weapon -> metal -> augment -> (catalyst, technique) -> notes -> risk -> cost -> Forge -> reveal -> result -> List / Store
- [x] Commission board for commissions and customer wants, opened from Shop and Forge
- [x] Storage filters, four orders and multi-select Salvage / Give to the watch / Scrap with one confirmation (T6.3c). Not met: a "never listed" filter (needs a core rule)
- [ ] The dark forge look and the 2026-10-14 visual language (Alegreya headings, Fira Sans body, gold only for the primary action and the chosen mark): applied to the title, run setup and opening chooser (emulator at font 1.0 and 2.0); not yet applied to Shop, Forge, Guild, visitors, relics and loans; 12 stock dialogs not yet on the house `ForgeDialog`
- [x] Implemented: 48 dp minimum targets, 52 dp primaries, screen-reader descriptions on disabled actions, rarity and buffs never by colour alone, reduced motion, portrait lock, `appCategory="game"`
- [ ] Accessibility validated on a real device: no accessibility suite, no completed 720x1280 or low-memory pass (review A05, A07; plan T6.1, T6.2). `LayoutMatrixTest` checks widths at 360 and 320 dp, font 1.0 / 1.3 / 2.0, for Shop, Forge and the blade sheet only. TalkBack never run
- [x] Settings: reduced motion, haptics, shop-day playback, guided first day; debug entries under Settings in debug builds (decision C)
- [ ] Haptic feel on a real motor (needs hardware); audio and audio settings (no sound asset exists)
- [x] Guided first day (2026-10-11): five steps and the outcome on a strip under the top bar; once per installation, skippable everywhere, replay from Settings (16 JVM tests, 3 device tests, `tools/emulator/intro.sh`). Whether a new player understands the game from it has not been observed (playtest T7.2 open)
- [x] Inspectable systems: hero and weapon sheets show taste, purse, regular status, guild and mentor, a weapon's history and each affix (review U04-U06, G08; T2.7: emulator at font 1.0 and 2.0)
- [ ] 720x1280 small-screen pass; 600 dp pass (decision D); low-memory interruption check

## 18 Architecture and persistence
- [x] Pure JVM `:core` (rng, content, config, model, engine, crafting, market, heroes, battle, combat, guild, legacy, gazette, shopday, persistence, sim); `:app` observes through one serialized `GameSession`
- [x] Typed, phase-checked commands and errors; invariants after every command and on load
- [x] Versioned JSON envelope, Room atomic save (run + legacy in one transaction, cursor beside them), DataStore for settings only
- [x] Save schema 7 with migrations and checked-in fixtures from older schemas; rules 6 and content 6 enforced on load; an older save over a lowered cap is clamped with a record line (review row 34). Not run: an upgrade from a real 0.7.0 or 0.8.0 APK
- [x] Bounds on save growth: events and closed commissions 30 days, 30 End Day IDs, weapon history caps, pruning of weapons gone for good (T6.3a)
- [ ] Save size: a long save (about day 350-490) passes SQLite's 2 MB row window; unsold stock still grows by decision (review #2; schema 8 split or compression planned)
- [x] A save that cannot be opened shows a recovery screen; a damaged row or file is kept, never deleted silently (review F02; emulator). Not seen on a device: the "newer version" and "incompatible" screens and the "could not confirm" dialog
- [ ] Known issues from the 0.7.0 final review (DECISIONS "Known issues left open from the final review"): a bulk Storage action stops silently when one save fails; a newer record kind under the same schema reads as "damaged"; three rules restated in the app. A database file cut to 0 bytes still opens as a new game with no message

## 19 Art and audio
- [x] Import pipeline (concept sheets, weapon master sheet 336 sprites, production packs, the owner's 20-hero portrait set and title painting), placeholders for the rest (ART_BRIEF "Art sources and provenance")
- [x] Forge scene, furnace states, weapons by element row, class portraits, faction sprites, materials, blessings, siege diorama, milestone burst
- [ ] Signature weapon variants (a gold ring stands in); visitor portraits; launcher icon
- [ ] Audio (11 effects and 1-2 loops wanted; none delivered)
- [ ] Art-provenance wording for the store listing (owner)

## 20 Testing and balance
- [x] JVM suites: determinism, UI never draws RNG, ownership, bounds, idempotence, siege forecast, Gazette truth, legacy reset, content validation, save round-trip, golden state (633 core tests pass, 2026-10-14)
- [x] Headless simulator (classic policies and bots, multi-era, upgrade impact, customers, depth, probes, sweeps) and guild simulator (14 policies, paired comparisons)
- [x] Balance 2, 3, 5 and 8 reviewed at 10,000 seeds; balance 8 at three base seeds, 990,000 runs: every run ends, longest 65 days, a maxed account leads a new one by at least 10 mean days
- [ ] Balance 12 to 15 reviewed at 10,000 seeds (measured at 300 to 1,000 seeds only)
- [ ] Both run-length bands hold at 10,000 seeds (the plan's M5 exit): not met at balance 8; the first-era part is now ruled (keep, judge by minutes), the maxed ceiling is still open
- [ ] A minutes-per-run measure, so the first-era pacing can be judged as the owner ruled
- [x] Artifact recovery as a simulator metric (51-53 % of fallen heroes' weapons come back at 1,000 seeds)
- [x] `:core:soak`: 2,000 forced-survival days for two smiths; End Day p95 1.7 to 3.9 ms (budget 200 ms)
- [ ] Device half of the soak (cold load, commit, frames, heap, memory pressure)
- [ ] Instrumented suite green again: 75 run, 8 to 10 failing at the 2026-10-14 review (stale assertions from the copy remaster). `smoke.sh` and `runend.sh` ran green after the day-1 relic offer and the run-setup screen (PROGRESS)
- [ ] Compose screenshot / accessibility tests
- [ ] Day-sim p95 < 200 ms on real mid-range hardware (21 ms on the emulator Android runtime)

## 21 Release
- [x] Vertical-slice acceptance on a device: forge -> sell -> hero fights -> Gazette -> siege -> defeat -> legacy -> upgraded next run
- [x] Every locked system is reachable and inspectable from some screen
- [ ] 0.9.0 gates (owner decision F): physical-phone pass; G3, G12, G13 (classic golden state changed by decision), G14
- [ ] Package rename from `com.example.blacksmithproject` (awaiting the application ID)
- [ ] Release signing, AAB
- [ ] Store listing (1.99 EUR, no ads / IAP), privacy and legal assets
- [ ] GDD acceptance walk-through on a real device

## Deep forging foundation (owner's prompt of 2026-10-11; `docs/DEEP_FORGING_LEDGER.md`)
Ticked = built and JVM-tested. What was seen on a device is in the ledger.
- [x] One resolver composes a weapon's fight rules; fight, weapon card, forge outlook, bench and capabilities read it
- [x] A fight rule for each of the six metals, on weapons forged from now on (`Weapon.craft`); older weapons unchanged
- [x] A stated role and limit for each of the 16 ingredients, in content
- [x] Typed capabilities with a pure evaluator
- [x] Seven interaction paths, each with two recipes, a limit, and a fight where it fires and one where it does not
- [x] Forge outlook card and the bench (a rehearsal on the real engine, no save touched)
- [x] Notebook "Fight notes": learned rule, suggested interaction, interaction seen in a real fight (shown on a device with a real contract, kept through a process kill)
- [ ] Commissions that ask for a capability (the evaluator is ready, nothing calls it)
- [ ] Any of this in a classic run (its resolver does not run fight rules)
- [ ] Human playtest: does forming a hypothesis and testing it feel good

## Living heroes and siege variety (owner's final design of 2026-10-17; `docs/LIVING_HEROES_LEDGER.md`)
- [x] Era feature revision: legacy eras unchanged, classic RNG parity (golden test), schema 8 migration
- [x] 12 peoples, 12 cultures (1,200 given / 600 family names), 20 backgrounds, bynames; silhouettes for missing faces
- [x] Hooks (5), ambitions (8), reason records; Hook text before recruiting
- [x] Member counter visit, announced loan returns, Speciality lean then autonomous choice, oaths enforced on loans
- [x] Personal offers, concerns, dated departure notices, departures; creditor, lesson and healer visitors
- [x] Combat grammar: Poison, preparations and Interrupt, return once, survival, recovery suppression, pack prey, attacks on the defences, Disrupting and Purifying
- [x] 48 enemies, 36 armies, committed army planner, structural forge loss once (cap 12), sabotage of the charge
- [x] Opportunities: 16 of 18 routed, 2 deferred (lantern hunt, safe experiment)
- [x] Identity UI and the owner's six UI fixes (screenshots `docs/living_heroes_evidence/ui_identity/`)
- [ ] Agency and siege UI (lean, returns, departures, personal offers, counter purchases, army forecast, enemy traits)
- [ ] Army strength calibrated to the existing bands on tuning seeds, confirmed on validation block 800001-800300
- [ ] Debug scenario saves regenerated; device pass at 360 dp, 1.0x / 1.3x

## Pipeline (owner requests and review findings, beyond the GDD)
- [x] Signboard effect (+1 customer a day per level; +0.4 days, +3.5 sales a run for the active smith)
- [x] Affix magnitude sweep (Giant Slayer, Cursed / Bloodbound, Reinforced / Swift, Heavy)
- [x] Lucky affix: loot something scarce (a catalyst or tier 3+ material) instead of a common material
- [ ] Decide the wear margin (active smith leads the plain one by 5 days, was 10): condition floor 0.8 or less siege wear
- [x] Known Name: one starting regular with savings per level (+1.6 mean days; first siege held in 94 % of runs against 86 %)
- [x] Weapons map pruning of terminal weapons (salvaged, shattered, donated, collected) 30 days after they leave play
- [x] The empty root `GDD.md` removed (unreferenced; review 2026-10-14 row 30)
