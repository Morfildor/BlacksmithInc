# Changelog

All notable changes to Tiny Blacksmith are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [SemVer](https://semver.org/)
with `0.y.z` during early development (minor = new feature set, patch = fixes/tuning). The app version lives in
`app/build.gradle.kts` (`versionName`, `versionCode` increments on every store-facing build).

## [Unreleased]
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

### Changed
- The end-of-day report and the Gazette archive are laid out as an edition: the biggest news as the lede, a one-line
  tally (gold taken, visitors who bought, expeditions won and lost), then Shop (sales, commissions, who left and why),
  Heroes (one sentence per hero, the quiet ones on one line), Town and Forge (the smith at the anvil in one line, then
  journal discoveries). Milestones already told by the news fold away; siege rounds fold behind the outcome. The
  archive opens the latest day and shows older days as a lede until tapped.
- Simulator: catalog sweeps (`--noTool`, `--toolCost`, `--noAffixEffect`), `--noImpact`, and per-run counters for
  elites slain, shattered weapons, warlord sieges, tool purchases and affix occurrence; the 10,000-seed v3 review and
  the per-tool / per-affix sweeps are recorded in `docs/DECISIONS.md` (recommendations only, no balance change).


### Added
- Weapon wear: a hero's blade loses condition in every expedition (6 on a win, 10 on a rout) and every siege it
  defends (15). Worn power counts in battle (down to three quarters of the weapon's power at condition 0), at the
  shelf (a hero weighs listings against the worn power of their own weapon and is keen to replace one below half
  condition) and in the shop's suggested price and trade-in credit. The weapon summary and the Town hero lines say
  "worn" or "battered"; the day report names a replacement purchase ("their own blade was worn out"). Older saves
  load with every blade keen.

### Changed
- Hone always restores a weapon's condition to full; the +6 quality bonus still applies once per weapon, so a worn
  trade-in can be re-honed ("Re-hone") for the same energy and core.
- Headless simulator: `BALANCED_ACTIVE` also re-hones worn trade-ins; the visits report gains a `WORN_OUT` outcome.

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
