# Changelog

All notable changes to Tiny Blacksmith are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [SemVer](https://semver.org/)
with `0.y.z` during early development (minor = new feature set, patch = fixes/tuning). The app version lives in
`app/build.gradle.kts` (`versionName`, `versionCode` increments on every store-facing build).

## [Unreleased]

### Added
- A save that cannot be opened no longer crashes the game. A recovery screen says what happened and what is safe, and
  offers Try again or, for a damaged or incompatible run, Start over (the unreadable run is kept as a backup on the
  device and your legacy stays).
- When a save fails while you play, a dialog says nothing has changed and offers Try again or Keep working.
- Back returns to Home from any other panel.

### Changed
- After you claim a fallen era's legacy, the run-end screen survives closing the game: it reopens claimed, with your
  points and upgrades still there to spend, until you begin the next era.
- The Back button and a tap outside the day's report no longer close it; only its "Begin day" button does.
- The forge panel you were on, the recipe you were drafting and an open forge result come back if the system closes
  the game in the background.
- Android's automatic cloud backup of the save is off until restoring one is tested.

### Fixed
- Two quick taps on the run-end screen (two upgrades, or an upgrade and Begin era) can no longer lose a purchase or
  write over the new era.
- The day's report now tells the whole day, including the blades you forged, listed or honed and the tools you bought
  while planning; before, those lines appeared only in the Gazette archive.

## [0.6.0] - 2026-10-09
Heroes with lives of their own, fight replays, the fates of fallen blades and three new legacy tracks (balance v5).

### Added
- The day's report reopens if the app is closed or killed before it was read, and stays closed once dismissed.
- Guild halls: heroes now spend some of their days training at a guild hall. A day there teaches a little and mends a
  little, and pays nothing. When a higher-level guildmate trains the same day, the lower-level hero is taught by them
  (once a day) and the mentor's name stays on their record. Patient, loyal and curious heroes go most often; greedy
  and restless ones rarely.
- Guilds form during a run, not only when a hero retires: the first hero with a name (fame 3) can found the town's
  company, and others join it by training there.
- Heroes pursue their ambitions on purpose, until the ambition is fulfilled: a slayer hunts an elite foe, a sworn
  defender drills the town watch (more militia than a patrol, nothing else), a collector or a fortune seeker takes paid
  guard work for gold.
- The Gazette tells these days: one shared "At the guild hall:" line beside "On the walls:" and "Resting:", and a
  founding, a joining, a lesson or an ambition day in the hero's own sentence.
- Notable fights are told round by round in the day report. An expedition against an elite foe, won or lost, and any
  expedition a hero does not come back from get a short text replay under "From the field", titled with the hero and
  the foe and folded behind an "N rounds" button like the siege's. At most three a day, the gravest first; the siege
  still comes first and alone has the diorama. Skipping them changes nothing.
- A fallen hero's blade has more ways to go. Comrades may still bring it back to the forge and the enemy may still
  seize it; now it may also be gone from the field and turn up two days later with a travelling merchant, who offers
  it for three days to the hero who wants it most and can pay in full (the gold goes to the merchant, not the shop)
  and otherwise leaves Emberfall with it for good. A storied blade is likelier to surface, never certain to. The
  Gazette tells each step: gone from the field, the merchant's arrival, the sale or the departure.
- A living guildmate may inherit a fallen member's blade instead of the forge, wielding it only if it beats their
  own.
- Three new permanent upgrades on the run-end screen and in the Legacy panel, three levels each for 8 / 20 / 45 legacy
  points, one for each upgrade category the design document names that had none:
  - **Caravan Ties** (catalog access): the supplier keeps one more of every rare material in stock each day, per
    level, so a smith with gold can forge the same rare recipe several times a day.
  - **Anvil Lore** (recipe odds): a correct signature recipe transforms more often with each level, still under the
    same ceiling, so a signature is never certain.
  - **Homing Steel** (legacy artifacts): blades on the Legend Board return more often (still at most once a run) and
    less dormant, never whole. With an empty Legend Board it does nothing.
- Headless simulator: the upgrade impact table is followed by a second table of what each upgrade buys besides days
  (town defense at the first siege and how often it held, weapons forged and sold and tools owned by then, the first
  sale of a tier-4+ weapon, rare supplier units bought, signature weapons, legends returned). New flags:
  `--upgrades id=level,...` (play the policy rows with that legacy account), `--yardsticks` (the same table for the
  policy rows), `--legends` (give the maxed and impact runs a veteran's Legend Board) and `--knownNameGold N`.

### Changed
- The Painted Signboard now lets one more customer into the shop each day per level (it used to nudge the chance
  of a visit, which the daily customer limit swallowed). An active smith sells about four more weapons a run.
- Affixes and flaws bite harder: Giant Slayer hits elites and warlords for x1.5 (was x1.25); Reinforced and Swift
  cut the wound of a lost fight to x0.7 and x0.6 (were x0.85 and x0.8); Heavy raises it to x1.5 (was x1.2); Cursed
  and Bloodbound cost their wielder 15 and 20 health per win (were 4 and 7, still never lethal). A Lucky blade now
  brings back what elites carry (a catalyst or a rare core) instead of a common material.
- Long runs keep smaller saves: a blade that was salvaged, shattered, given to the watch or sold to a collector
  leaves the save 30 days later, unless it is a signature weapon or famous enough for the Legend Board. Play is
  unchanged (the same seed gives the same run); a 400-day save of an active smith shrinks by about a third.
- An unfulfilled ambition no longer nudges a hero toward ordinary expeditions or patrols; it is a kind of day of its own.
- A hero driven back from an expedition yesterday is more likely to rest or go to the hall today, and an unarmed hero
  with little gold is more likely to take the town's patrol pay.
- Simulator: the report shows how heroes spend their days (share of hero-days per activity, with wounded rest apart),
  level-ups, lessons at the hall and guilds per run.
- Where and how a hero fell now sets the odds for the blade: an elite foe keeps its trophy more often (seized 42 %
  of the time, was 25 %) and gives it up less often (recovered 40 %, was 50 %); a fall on the road is unchanged. A
  fall on the walls would favour recovery (70 %), but no champion can die on the walls with the current siege numbers.
- Simulator: per-run counters for the fate of fallen heroes' blades (recovered, inherited, resold, seized, lost) and
  an artifact recovery rate, in the text and JSON reports; `--noFates` replays the earlier rules for comparison.
- Known Name: besides its starting reputation, one starting hero per level is now already a regular of the shop and
  arrives with coin saved for a blade; the first day's news names them. On its own, starting reputation made runs
  slightly shorter in the simulator; with the regulars the first siege is held more often.

## [0.5.0] - 2026-10-09
Home dashboard, a readable Gazette, weapon wear and weapon fame (balance v4).

### Added
- Home panel: the first tab and the panel the workshop opens on (and returns to after the day report or a deferred
  blessing). One block per concern, each tapping through to the panel where the action happens: today's day, era,
  energy, gold, forge integrity and reputation; the next siege with the Town outlook and armory; the shelf with
  yesterday's visitors and why they left; open commissions with reward and deadline; the champions, their weapons
  and the wounded; yesterday's lede and tally; a pending blessing; and the materials that have run out. A siege within two
  days or against the odds, then commissions waiting for an answer, come first.
- Weapon fame now matters, within limits: a storied blade fights a little better (up to +5 % at ten fame), heroes
  want it more on the shelf (collectors most of all), its suggested price carries a small premium (up to +5 %), a
  legend returning from an earlier era keeps its fame, and the shelf line says "storied", "famed" or "renowned".
- Weapon wear: a hero's blade loses condition in every expedition (6 on a win, 10 on a rout) and every siege it
  defends (15). Worn power counts in battle (down to three quarters of the weapon's power at condition 0), at the
  shelf (a hero weighs listings against the worn power of their own weapon and is keen to replace one below half
  condition) and in the shop's suggested price and trade-in credit. The weapon summary and the Town hero lines say
  "worn" or "battered"; the day report names a replacement purchase ("their own blade was worn out"). Older saves
  load with every blade keen.

### Changed
- The end-of-day report and the Gazette archive are laid out as an edition: the biggest news as the lede, a one-line
  tally (gold taken, visitors who bought, expeditions won and lost), then Shop (sales, commissions, who left and why),
  Heroes (one sentence per hero, the quiet ones on one line), Town and Forge (the smith at the anvil in one line, then
  journal discoveries). Milestones already told by the news fold away; siege rounds fold behind the outcome. The
  archive opens the latest day and shows older days as a lede until tapped.
- Simulator: catalog sweeps (`--noTool`, `--toolCost`, `--noAffixEffect`), `--noImpact`, and per-run counters for
  elites slain, shattered weapons, warlord sieges, tool purchases and affix occurrence; the 10,000-seed v3 review and
  the per-tool / per-affix sweeps are recorded in `docs/DECISIONS.md` (recommendations only, no balance change).
- Hone always restores a weapon's condition to full; the +6 quality bonus still applies once per weapon, so a worn
  trade-in can be re-honed ("Re-hone") for the same energy and core.
- Headless simulator: `BALANCED_ACTIVE` also re-hones worn trade-ins; the visits report gains a `WORN_OUT` outcome.
- Balance v4: the Master Whetstone costs 120/300 (was 200/500), so a thrifty smith takes it before the signboard;
  a faction's warlord leads the siege from pressure 50 (was 70) and no longer strengthens the raid (the pressure
  already does), so a warlord is seen in about half of first-era runs instead of one in fifty. Balance config version 4.

## [0.4.0] - 2026-10-09
Gameplay depth (balance v3): shop actions, workshop tools, elite foes and warlords, hero ambitions, trade-ins.

### Added
- Shop actions on finished weapons: Salvage (1 energy, returns the core material), Hone (2 energy and one unit of the
  core for +6 quality, once per weapon) and Arm the watch (a fifth of the weapon's power joins the town's defense, up
  to 30; half wears away each siege).
- Workshop tools bought with gold, lasting for the run: Great Bellows (+1 daily energy), Master Whetstone (+3 forged
  quality), Painted Signboard (more visitors), Display Case (+2 shelf slots).
- Elite foes on expeditions (stronger, richer, a weapon title and catalysts for the victor) and named warlords who
  lead a siege when a faction's pressure reaches 70; beating one pays the smith 120 gold in tribute.
- Hero ambitions: every hero pursues a slayer's vow, a defender's oath, a prized weapon or a fortune; ambitions tilt
  what heroes do and buy, and fulfilling one makes the Gazette and raises the shop's reputation.
- Commissions can ask for an element (the patron's taste or what the looming faction fears) and pay half again more.
- Siege outlook in Town: the town's defense against the expected raid as things stand, the warlord if one leads, and
  the armory.
- Trade-ins: a hero replacing a weapon hands the old one back to the shop as part payment (40 % of its fair price);
  it returns to storage to be resold, honed, salvaged or given to the watch. The town also pays heroes for patrols.
  Heroes can afford far more of what is forged (sales up about a third).
- Simulator policy `BALANCED_ACTIVE`, which uses the new shop actions, and shop visits per run by outcome in the
  report.

### Changed
- Affixes do more than add power: Undead Bane bites only the Hollowbound, Giant Slayer only elites and warlords,
  Vampiric mends its wielder, Lucky finds more loot, Swift and Reinforced soften a rout, Heavy worsens it, Brittle
  weapons can shatter, Cursed and Bloodbound weapons hurt the hand that wins with them.
- Siege warnings name the warlord and the element the attackers fear.
- Raids grow by 6 power a day instead of 5, offsetting the better-armed heroes (balance v3).
- Legacy base points 5 -> 6, so even the shortest first run affords the cheapest upgrade (balance v3).
- Run-end screen: the Begin era action stays pinned under the scrolling summary instead of sitting below the
  eight upgrade cards.

## [0.3.0] - 2026-10-09
Reputation and loyalty economy, bounded weapon histories, simulator purchasing and pricing policies, UI wave 2.

### Added
- Shop reputation and hero loyalty now raise the price heroes treat as fair (bounded at +25 % each), regulars return
  with commission requests, and the Gazette records premium sales and names the shop's regulars.

### Changed
- Weapon histories are bounded at End Day: the newest 10 combat entries (victories, sieges) per weapon are kept,
  ownership entries (forged, sold, commissioned, inherited, lost, seized, recovered, returned) forever; gameplay,
  legends and the Gazette are unchanged, and existing saves compact on their next End Day (save schema still v1).
- Headless simulator: new `BALANCED_INVEST` policy (buys the best core and augment within `gold - reserve`,
  `--reserve N`) and `BALANCED_REPUTED` policy (lists at the reputation-raised fair price), `--impactPolicy` also
  drives the maxed-legacy run, and `--json` reports the reserve; 10,000-seed review of every policy recorded in
  DECISIONS.md.
- Title and run-end screens on the spacing tokens with one primary action at a time (Continue leads when a run is
  saved; Claim, then Begin era); Town lists every faction under the leader with its pressure and weakness; the Forge
  threat line names the leading faction only; recipe steps scroll so a tall step shows its header first; disabled
  Forge/upgrade buttons and the reduced-motion row carry screen-reader descriptions.

## [0.2.0] - 2026-10-08
Launch content by default, balance v2, all 24 signatures and 25 world events, bounded saves, decluttered UI.

### Added
- The remaining 12 signature recipes (spear, dagger, staff) for launch content, so all 24 exist; every core,
  element and catalyst now appears in at least one recipe.
- World event "Strange Weapon Fragment": reveals one unknown signature recipe in the journal (observed, with its
  descriptive wants) and leaves one of its core and augment at the forge; the 25th scripted event.

### Changed
- Workshop UI decluttered: a three-stat top bar (day, gold, energy); forge integrity and the siege countdown live on
  the Forge panel's threat line and the Town header; one End Day button with a contextual sublabel; a 64dp nav bar.
  The Forge panel pins the weapon preview, recipe, cost and Forge button above collapsible steps (mode, family,
  core, augment, catalyst/technique when advanced, risk) that auto-advance; out-of-stock materials are disabled with
  the engine's own reason. Market lists shelf weapons as rows with a tap-to-edit price and folds empty slots into
  one line; commissions and the supplier (grouped by category) are separate sections. Tips are one slim banner per
  panel, dismissed once. The day report is a full-width paper sheet (diorama, headlines, replay, one action).
  Typography collapsed to four working styles with spacing tokens (`ui/theme/Spacing.kt`); muted ink darkened.
- Launch content is the engine default (`GameEngine()` now builds on `LaunchContent`; `SliceContent` stays for
  slice-specific tests and `--content slice` in the simulator).
- Balance v2: quality formula `25 + 6*coreTier + 3*augmentTier + ...` (was `35 + 4*coreTier`), so the six launch core
  tiers spread common -> epic instead of saturating; siege modifier 2.0 (was 2.75) with forge damage
  `24 + 50*(ratio-1)` (was `12 + 25*(ratio-1)`); launch faction growth 4/3/2 per day (was 6/5/4). Heroes now act
  against the most pressing faction instead of the first by ID.
- Simulator: `--content launch|slice`, `--rarityTable [N]` (rarity per core x augment x risk through the real forge
  path), `--impactPolicy`, forge-damage overrides; the RANDOM policy only picks materials it can afford.
- Saves stay bounded over long runs: End Day now compacts the event log, keeping full records for the last
  30 days (`BalanceConfig.eventRetentionDays`) and history-grade events (sieges, deaths, retirements, guilds,
  signatures, milestones, world events, weapon fates) for the whole run. Gameplay, Gazette headlines for the
  window and the save schema are unchanged; older Gazette days show only the retained headlines.
- Weapons are drawn from the artist's weapon master sheet: every family has an element row and eight visual levels
  (core tier plus epic/legendary bonus); the forge preview shows the plain base weapon until an augment is chosen.
  The old per-core recolours and element auras are gone.

## [0.1.0] - 2026-10-08
First tracked build (vertical slice content, launch content data-complete but not default).

### Added
- `:core` deterministic engine: SplitMix64 per-subsystem RNG, immutable `GameState`, typed commands and errors,
  fixed End Day order, invariants, versioned `BalanceConfig` (v1), versioned save codec with migration scaffold.
- Content: `SliceContent` (default) and `LaunchContent` (6 families, 16 materials, 5 classes, 3 factions,
  12 affixes, 6 flaws, 8 blessings, 8 upgrades), 12 signature recipes, techniques (temper, quench, etch),
  23 world events, retirements, guilds, mentoring, seizure and inheritance.
- Headless simulator with the GDD policy set, upgrade-impact sweep, perf timing and JSON reports.
- `:app` Compose UI: title, workshop (forge, market, town, gazette, journal, legacy), day report with stepped
  battle replay and animated siege diorama, run-end screen, settings (reduced motion), Room save with atomic writes.
- Pixel art pipeline: concept sheets, loose PNGs and the artist's 1x pack merged by `import_assets.py`;
  placeholders by `generate_assets.py`; 194 hand-made sprites in use.
- Tests: core JVM suite, instrumented save/UI tests, scripted emulator smoke loop.
