# TINY BLACKSMITH
## Game Design Document — v2.0

**Date:** 2026-10-14
**Describes:** the game as built at rules 6, content 6, balance 15, save schema 7 (app 0.8.0 plus `[Unreleased]`, aimed at 0.9.0)
**Supersedes:** GDD v1.0 (2026-10-08, nine design rounds; in git history as `Tiny_Blacksmith_GDD_v1.0.md`) and folds in
the addendum `docs/GDD_REVISION_2026-10-10.md` (guild runs), the run-variety rulings (rules 6), the deep-forging rulings
and the owner's decisions A–M of 2026-10-14.
**Genre:** Fantasy forge, town and adventurers' guild simulator / indirect autobattler / endless roguelite
**Platform:** Android, portrait phone · **Technology:** Kotlin + Jetpack Compose, pure Kotlin simulation core
**Monetization:** €1.99 premium; no ads, microtransactions, paid DLC, subscriptions or accounts · **Connectivity:** fully offline

> **Status conventions.** **LOCKED** is a requirement the owner decided; it changes only by an explicit owner decision,
> recorded in §2. **PROPOSED** is a current value or design open to tuning; the number in this document is a snapshot,
> `core/.../config/BalanceConfig.kt` (and the content files) are authoritative. **DEFERRED** is outside the current
> version; do not build it. **OPEN** is a question waiting for the owner (§21). **NOT YET BUILT** marks a decided rule
> the code does not follow yet. Never silently replace a LOCKED decision with a proposed alternative.
>
> **Where detail lives.** This document is the design view. Tuning evidence and every numbered decision:
> `docs/DECISIONS.md`. Feature ledgers: `docs/MAJOR_UPDATE_LEDGER.md`, `docs/GAMEPLAY_DEPTH_PLAN.md`,
> `docs/GUILD_EVOLUTION_SPEC.md` / `_PLAN.md`, `docs/RUN_VARIETY_LEDGER.md`, `docs/DEEP_FORGING_LEDGER.md`. Built vs left:
> `docs/GDD_CHECKLIST.md`. How to change this document: §22.

---

# 1. Executive summary

## 1.1 Elevator pitch

*Tiny Blacksmith* is a menu-driven fantasy game about the smith whose weapons decide a town's fate. The player forges
weapons from metals, augments and catalysts, prices them and puts them on the shelf; a town of named, autonomous
adventurers comes to the counter, buys what suits them, goes out to fight, grows, retires, founds companies, mentors
successors and dies. Three monster factions grow in a living world and besiege the forge on a predictable calendar.
When the forge falls, knowledge, upgrades, famous weapons and family lines carry into the next era. There is no final
wave.

Since v1.0 the game has grown from a shop into a **forge, town and adventurers' guild simulator**. A run now begins
with a choice:

- **Classic run** (no charter): the shop alone. The player only forges, prices, sells and answers the town; heroes are
  fully autonomous.
- **Guild run** (one of three charters): the forge also runs a small adventurers' company. The smith recruits members,
  lends them weapons, picks a party, a contract and a posture, and reserves defenders for the wall. Fights stay fully
  automatic, resolved by a typed interaction engine, and ordinary townsfolk stay autonomous. A charter milestone on day
  20 offers an honourable retirement; continuing is a choice.

Around both run kinds sit morning visitors with priced choices, workshop relics, siege traits and preparations,
Heartfire and weapon deeds, a card-by-card replay of each shop day, and a Gazette written only from real records.

**Core fantasy (LOCKED):** *You are not the warrior. You are the smith whose weapons make warriors legendary.* In a guild
run: *you are the smith who also keeps the company's ledger and armoury, never its sword arm.*

## 1.2 Design pillars

1. **Discovery first (LOCKED).** Material pairings, hidden signature recipes, clues and readable fight rules drive
   curiosity. Knowledge is control: a recipe once discovered is reliable.
2. **The smith, not the warrior (LOCKED, revised 2026-10-10).** No movable character, no combat controls, no targeting,
   turns or timing. Classic: shop actions only. Guild: company management (recruit, lend, choose party, contract,
   posture, defenders), never a fight command.
3. **Living consequences (LOCKED).** Named autonomous heroes shop and act; weapon fit changes their fate and the town's.
   Weapons have lives: they are worn, earn fame and deeds, change hands, are taken, recovered and remembered.
4. **Short, repeatable, endless runs (LOCKED).** A first era typically ends around day 20–25; the world can continue
   indefinitely. A guild run adds a day-20 milestone, not an end.
5. **Run diversity and strong permanent progression (LOCKED).** World conditions, blessings, visitors, relics, siege
   traits, charters and opening packages vary runs; permanent knowledge, upgrades, legends and lineages carry over.
6. **Accessible premium experience (LOCKED).** Portrait menus, readable type, instant forging with a short reveal,
   skippable presentation, offline, one payment.
7. **Affordable crafting, meaningful scarcity and danger (revised).** v1.0's "relaxed economy" holds for ordinary
   crafting; pressure sits on scarce stock, fine metals, Heartfire, fees, roster availability and opportunity cost, and
   on factions that grow.
8. **Honest systems (LOCKED, made explicit).** Every story, headline, card and replay comes from a real event record.
   Deterministic rules, thresholds, costs and timings are shown exactly; hidden random odds are shown only in words.
   Animations never consume gameplay randomness.

## 1.3 Intended emotional moments

The player forges an unusual Stormglass bow. A ranger with a taste for storm buys it. The shop-day card shows her
weighing it against her old blade; the Gazette reports her elite kill and the bow takes a title. She falls at the
wall; the bow is recovered and passes to a guildmate. In a later era her descendant walks into the shop, and the bow
returns, dormant, through "A Famous Weapon Returns".

In a guild run: the smith lends a Moonsteel spear to a Guardian, sends a party to sabotage the besieger's shield wall,
and watches the fight report show the spear's rule turning the night. The charter siege on day 20 is held; the smith
may retire with honour, or raise the company's rank and go on.

These stories come from deterministic systems, never from a script dressed up as simulation.

---

# 2. Decision register

## 2.1 The nine original rounds (2026-10-08), with their status now

| Round | Domain | Decision (LOCKED in v1.0) | Status now |
|---|---|---|---|
| 1 | Primary fun | Discovering combinations | Kept |
| 1 | Weapon use | Equip heroes; they fight independently | Kept; heroes equip themselves. Guild runs add loans to members |
| 1 | Run loop | Endless survival | Kept; guild adds an optional day-20 retirement |
| 1 | Art | Detailed fantasy pixel art | Kept as intent; sources are mixed (§19) |
| 1 | Sessions | Brief casual play plus optional longer sessions | Kept |
| 2 | Recipes | Hybrid modular forging, hidden recipes, rare transformations | Kept |
| 2 | Champions | Three central town defenders, dynamic | Kept; guild runs may reserve members for the places |
| 2 | Combat | Fully automatic | Kept (two resolvers, §10) |
| 2 | Variation | Weapon synergies plus temporary blessings | Kept; plus visitors, relics, traits, charters, openings |
| 2 | Defeat | Forge destruction | Kept |
| 3 | Market | Browsing customers plus commissions | Kept |
| 3 | Crafting randomness | Layered RNG | Kept |
| 3 | Resources | Suppliers, hero expeditions and events | Kept; plus visitors and guild contracts |
| 3 | Time | Player-controlled End Day | Kept |
| 3 | Persistence | Knowledge, upgrades and weapon legacy | Kept |
| 3 | Economy | Relaxed | Revised: crafting affordable, scarcity is the pressure (pillar 7) |
| 3 | Optional mechanics | Cursed/sentient weapons, named histories, crises, loyalty/reputation, returning legends | All built |
| 3 | Presentation | Close-up forge, menu UI, no moving character | Kept; forge became a workbench (§17) |
| 4 | Crafting modes | Quick and Advanced | Kept; plus Heartfire forging (an Advanced variant) |
| 4 | Risk | Safe / Balanced / Reckless before forging | Kept |
| 4 | Discovery | Mystery first, clues and journal | Kept; journal is the Notebook, with a clue ladder |
| 4 | Production cap | Energy, not item slots | Kept |
| 4 | Town champions | Strongest available three | Kept |
| 4 | Battles | Optional animation or instant reports | Kept; plus the card-by-card shop day |
| 5 | Discovery | Experiments plus world clues | Kept; plus rumours and visitor lessons |
| 5 | Rarity | Traditional tiers plus emergent historical fame | **Revised (rules 6):** quality gives at most Epic, Epic only in fine metal, Legendary only by Heartfire or deeds |
| 5 | Threat | Living factions | Kept |
| 5 | Defense | Three champions plus town security | Kept (militia, watch armoury) |
| 5 | Legacy | Knowledge, upgrades, legendary records | Kept |
| 5 | Run variance | World conditions and blessings | Kept, widened |
| 5 | Energy | Overwork with trade-offs | Kept |
| 6 | Hero identity | Class, 2–3 traits, ambition, preferences | Kept |
| 6 | Purchasing | Complex preferences | Kept, deepened (wants, trade-ins, siege demand) |
| 6 | Sales control | Shelves, manual pricing, commissions | Kept |
| 6 | Hero growth | Retirement, guilds, mentoring, successors | Kept |
| 6 | Weapon mechanics | Combat-focused synergies | Kept; guild runs give every part a typed fight rule |
| 6 | Ownership | Living artifact cycle | Kept; plus loans, cracks, taken weapons, recovery |
| 6 | Curses/sentience | Predictable modifiers | Kept |
| 6 | Story presentation | Daily fantasy newspaper | Kept; plus a typed briefing and the shop-day cards |
| 7 | Screen | Portrait | Kept |
| 7 | Navigation | Single workshop with panels | **Revised:** four destinations (Shop, Forge, Town/Guild, Records) |
| 7 | Forge feel | Instant crafting plus reveal | Kept |
| 7 | Pricing | Manual per item | Kept; suggested price by default (decision K) |
| 7 | Animation | Significant battles only, optional | Kept |
| 7 | Pace | Flexible session length | Kept |
| 7 | Discovery UX | Live affinity hints, Experiment Journal | Kept (Notebook) |
| 8 | Price / extras | €1.99, nothing else | Kept |
| 8 | Launch library | 16 materials, 6 families, 5 classes, 3 factions, 25 events | Kept (§16) |
| 8 | Art creation | Custom asset pipeline | Kept as a pipeline (§19) |
| 8 | Stack | Kotlin and Compose | Kept |
| 8 | Online | Offline launch | Kept |
| 8 | Typical early run | 15–25 days, endless | Kept as a target; measured at the top edge (§20) |
| 8 | Production | Simulation first | Kept |
| 9 | Daily energy | 10 | Kept |
| 9 | Overwork | Next-day exhaustion | Kept |
| 9 | Failure | Always a usable, possibly flawed weapon | Kept |
| 9 | Invasions | Predictable major siege intervals | Kept |
| 9 | Recovery | Hero-driven, no gold repair | Kept (guild wall work is hero-driven too) |
| 9 | Generations | Within-run mentoring + cross-run lineages | Kept |
| 9 | Legacy | Strong permanent upgrades | Kept |
| 9 | Disclosure | Descriptive indicators, not probabilities/formulas | **Revised 2026-10-10, sharpened 2026-10-14:** hidden random odds in words; deterministic rules, thresholds and costs exact |
| 9 | Death rewards | Baseline legacy plus milestones | Kept |

## 2.2 Decisions since v1.0

| Date | Round | What was decided | Record |
|---|---|---|---|
| 2026-10-08/09 | Balance v2–v5 | Launch content default; quality and siege formulas retuned; elites, warlords, ambitions, affix effects, workshop tools, trade-ins, weapon wear and fame, hero daily life, weapon fates, 11 legacy tracks | DECISIONS "Balance v2" … "Balance v5, part 5" |
| 2026-10-09/10 | Major update | A larger, fairer town (12 residents, 6 seats, fair seating); standing wants, sidegrades, siege demand; commission situations; clue ladder and rumours; recognition lines; the card-by-card shop day; four destinations; bounded save growth | `docs/MAJOR_UPDATE_PLAN.md`, DECISIONS "Major update: rulings and open decisions" |
| 2026-10-10 | Gameplay depth | Morning visitors, workshop relics, siege traits, besieger committed at the first warning (rules 4) | `docs/GAMEPLAY_DEPTH_PLAN.md` |
| 2026-10-10 | Guild revision (owner) | Guild runs: recruit, loan, party, contract, posture, reserved defenders; day-20 charter and optional retirement; exact display of deterministic rules; Town becomes Guild. Classic runs keep the original contract | `docs/GDD_REVISION_2026-10-10.md`, `docs/GUILD_EVOLUTION_SPEC.md` |
| 2026-10-11 | Run variety (rules 6) | Legendary only by Heartfire or deeds; Epic only in Starsteel/Moonsteel, sold from days 6 and 11; opening packages with drawbacks; siege preparations; two-day siege jobs; contract modifiers; the watch on a rescue | `docs/RUN_VARIETY_LEDGER.md` (D1–D26, R1–R3) |
| 2026-10-11 | Deep forging | One composer for a weapon's fight rules; metals have rules; weapons keep the composition they were forged with; the bench never counts as evidence | `docs/DEEP_FORGING_LEDGER.md` (F1–F9) |
| 2026-10-11 | Presentation | Copy remaster, Gazette briefing, guided first day | DECISIONS "Copy remaster…", "Guided first day" |
| 2026-10-14 | Owner decisions A–M | §2.3 | DECISIONS "Owner decisions on the 2026-10-14 review" |

## 2.3 Owner decisions of 2026-10-14

| | Decision | Status |
|---|---|---|
| A | Hidden random odds (Reckless flaw chance, Heartfire breakthrough chance) are shown in band words, never as percentages or counts ("3 in 10" states exact odds). Thresholds, costs and energy stay numbers. Applies to previews and tooltips too, including the upgrade preview (ruling of 2026-10-14, §21.4) | Built 2026-10-14; app tests written, not yet run (§21.3) |
| B | Guild wall work draws on the daily recovery allowance; any extra for assigned heroes is a `BalanceConfig` number (`wallWorkExtraRecovery` = 1), never a bypass | Built (balance 15); the value 1 vs 3 is OPEN |
| C | Debug entries (scenarios, combat sandbox) live under Settings in debug builds | Built |
| D | `appCategory="game"`; portrait stays; checked on a 600 dp screen; landscape DEFERRED | Built |
| E | Bulky evidence archived outside the tracked tree; no history rewrite | In progress |
| F | Next version 0.9.0, held until a physical-phone pass and gates G3, G12, G13, G14 have evidence | Held |
| G | `allowBackup="false"`; legacy-only backup DEFERRED | Built |
| H | Bundled licensed fonts: bold serif headings, sans body and labels | Built (Alegreya, Fira Sans) |
| I | Opening: tap a card, one primary action names it, no confirm dialog; the drawback is on the card | Built |
| J | Guild members at home train and pursue ambitions automatically; one line explains it | Built 2026-10-14; app tests written, not yet run (§21.3) |
| K | Forge result: "List at suggested" primary, "Set price" secondary | Built 2026-10-14; app tests written, not yet run (§21.3) |
| L | Forge result: one line saying whether the weapon is reserved and for whose commission | Built 2026-10-14; app tests written, not yet run (§21.3) |
| M | Giving a weapon away stays valid but earns a bounded generosity benefit instead of full sale reputation and loyalty | Built (balance 15, §7.4) |

Also ruled that day: standing drawbacks are approved where they make a playstyle (visible before choosing, inspectable
during the run); militia as a paid wall unit is approved in direction (not built); an irreversible Heartfire spend asks
for a confirmation stating cost and consequence (built 2026-10-14); "Renown" stays the word for deeds.

---

# 3. The two kinds of run

A run's mode is fixed when it starts and never changes (a save from before the guild update stays classic).

| | Classic run (no charter) | Guild run (charter) |
|---|---|---|
| Player actions | Forge, price, list, buy, commissions, visitors, relics, blessings, hone/salvage/scrap/donate, End Day | All classic actions, plus recruit, dismiss, lend, recall, plan a deployment (party, contract, posture), choose a checkpoint, reserve defenders, choose specialities, opening, rank, retirement |
| Heroes | All autonomous | Townsfolk autonomous; contracted members never shop, wander or take commissions |
| Fights | Scalar resolver (§10.2) | Contracts and the siege on the interaction engine (§10.3); residents' own expeditions stay scalar |
| Weapons in a fight | Multipliers (attack, defence, element) | Every part carries a typed fight rule (§5.9) |
| Siege warning | 2 evenings ahead | Besieger, field and preparation committed 4 days ahead |
| Defenders | Three strongest available, automatic | Reserved members first, recommendation fills the rest |
| Relics | Workshop relics: day-1 draft, after sieges 2 and 4 | Opening package relic; drafts after sieges 1, 2, 3 offering a party rule, a forge tool and a pact |
| Heartfire | A siege won from the 4th on | Starfall site, deep hall, recovery, a warlord broken on the wall |
| Milestone | None; endless | Charter siege on or after day 20; optional retirement; optional ranks |
| Disclosure | Hidden odds in words; costs exact | Same; plus every fight rule, threshold, fee and return morning exact |

Shared by both (LOCKED): automatic combat; no avatar; no reaction minigame; 10 base energy and overwork; every valid
forge yields a usable weapon; End Day controlled by the player; predictable sieges with living faction pressure; forge
integrity 0 ends the run; dead heroes never act or own equipment; claim once per run; Gazette and replays from real
records; the rules-6 forge (§5.5).

---

# 4. Player loop, day flow and end conditions

## 4.1 Run creation

**Title → New game → Run setup ("How era N begins").** Every charter and "No charter" is a card with its gains and costs
as bullets; one primary action "Begin under X" / "Begin a classic run". A new account starts on "No charter"; the last
choice is remembered (settings, not the save).

`GameEngine.newRun` (PROPOSED values unless noted):

| Item | Value |
|---|---|
| Identity | `RunId = era<N>-seed<S>`, stamped with rules, content and balance versions |
| Gold | 250 (+100 per Family Savings level); guild: charter and opening deltas |
| Energy | 10 (LOCKED) + Tireless Smith |
| Forge integrity | 100 (+20 per Stalwart Walls level); some guild openings start lower |
| Reputation | 0 (+5 per Known Name level, each level also makes one hero a regular) |
| Starting stock | Iron 6, Bronze 3, Silver 1, Ember Resin 4, Frost Bloom 3, Stormglass 1, Binding Salt 1 (+2 of each per Well-Stocked Cellar level) |
| Shelves | 8 (+2 with the Display Case tool) |
| Town | 12 residents, the first five one of each class; slot 0 is the newest lineage's descendant on a veteran account |
| Militia | 5 (a charter may add) |
| World condition | One of Calm Season (raid ×1.0, prices ×1.0), Restless Roads (×1.1, ×1.1), Lean Harvest (×0.95, ×0.9) |
| Factions | Pressure 20–45 each |
| First siege | Day 5 (every 5 days) |
| First relic | Classic: a draft of 3 workshop relics on day 1. Guild: the opening package's relic |
| Guild run | Charter applied; on the first morning 3 opening packages are offered and End Day waits for one (§12.3) |

There is no fixed-length campaign. Do not turn the game into a 20-day campaign; the guild's day 20 is a milestone.

## 4.2 Phases and planning

The run has two phases: **PLANNING** and **ENDED** (forge destroyed, or a guild retirement). End Day is one atomic
command; "resolving" and "reporting" are not stored phases. During planning every command is validated whole or
rejected with a typed error, and invariants are asserted after each accepted command. Nothing advances without End Day.

## 4.3 End Day order (PROPOSED order; fixed in code, pinned by the golden-state test)

1. Refuse if the run has ended or (guild) an opening is pending. Snapshot today's shelf.
2. *(Guild)* The planned party leaves; its loadout and relics are frozen.
3. An unanswered morning visitor takes its free default answer.
4. Accepted commissions are delivered (before browsers, so a promised weapon is not sold first).
5. Consequences settle (wagers judged, pledges learn their fate).
6. Shelf visits: willingness, seating, purchases, equipping, purses.
7. Recognition lines (no RNG). Travelling merchant resells a fallen hero's weapon.
8. *(Guild)* Contract fights for parties on the road.
9. Residents' activities: expeditions, patrols, rest, guild hall, ambition work; arrivals.
10. *(Guild)* Members at home rest or train.
11. Faction pressure step; one world event (30 % chance); a commission offer for tomorrow (30 % chance).
12. Siege warning (classic: 2 evenings before; guild: commit 4 days before); the siege if due.
13. Ambitions, retirements, guild reconciliation, recovery (forge repair, militia −1, champions re-picked).
14. Blessings expire; an unanswered blessing offer lapses after 3 mornings.
15. Ledger and day record; Gazette data; compaction and pruning.
16. New morning: day +1, energy = base − yesterday's overwork, supplier restock, wants lapse, guild party home / board /
    candidates / law / rival, relic offer if due, a morning visitor (75 % from day 2).

Then the app saves the result atomically and plays the saved day card by card (§17.6). End Day is idempotent per
command ID (the newest 30 are remembered); a retry returns the stored resolution without simulating.

## 4.4 End of run

**Fall:** forge integrity ≤ 0 → "The forge has fallen". **Retirement (guild):** after the charter is secured the smith
may close the chapter (a successful end). Either way: a run summary, **claim legacy points once**, buy upgrades, choose
the next era's charter (or none) and **Begin era N+1** with knowledge, upgrades, legends and the newest lineage.

**Abandon** (main menu, confirmed): ends the run with no points, legends or lineage; recipe notes made in the run stay
(owner ruling 2026-10-14: abandoning resets the run's resources and progress, never the player's crafting knowledge).

## 4.5 Pacing

A day takes 1–3 real minutes with familiar recipes; longer with the Notebook, storage and the guild board. No countdown
clock, real-time energy, idle production or real-time opponent (LOCKED). Every screen is dismissible and recoverable
after process death; the shop-day position is saved beside the run.

---

# 5. Forging, materials and discovery

## 5.1 Weapon families (LOCKED 6)

| Family | Base power | Defensive weight | Fits (class fit 1.2, others 0.85) | Guild fight delivery |
|---|---|---|---|---|
| Sword | 10 | 1.1 | Guardian, Duelist, Battlemage | 100 % strike, 100 % support; one clean strike |
| Axe | 12 | 1.0 | Guardian, Warden | 120 % strike, 90 % support |
| Spear | 10 | 1.15 | Guardian, Warden | Reach: half of what enemy Guard stops goes through (1/round) |
| Bow | 9 | 0.9 | Ranger | Second arrow: 35 % strike at the last enemy (1/round) |
| Dagger | 8 | 0.8 | Duelist, Ranger | Off-hand cut: 45 % strike (1/round) |
| Staff | 9 | 1.0 | Battlemage, Warden | 80 % strike, 130 % support |

## 5.2 Materials (LOCKED count 16; names PROPOSED)

**Metals (cores).** Tier sets quality and power; each has a guild fight rule on weapons forged at composition 2.

| Metal | Tier | Price | Daily stock | Guild fight rule |
|---|---|---|---|---|
| Iron | 1 | 10 | unlimited | Enters each fight with 3 Guard |
| Bronze | 2 | 22 | unlimited | When a foe breaks the wielder's Guard, strike it for 5 (1/round) |
| Silver | 3 | 45 | 1 | Enters with 3 Charge |
| Obsidian | 4 | 75 | 1 | Strike 110 %; cracks the first time the wielder's Guard breaks |
| Starsteel | 5 | 120 | 1, **from day 6** | A hit spending a Mark deals +3 (1/round) |
| Moonsteel | 6 | 180 | 1, **from day 11** | A hit on a chilled foe deals +2 (1/round) |

Starsteel and Moonsteel are the **fine metals**: the only ones that hold Epic work and take Heartfire. Before their
supplier days they come only from dangerous work (an elite slain, a deep hall, a starfall site, a contract paid in
metal); the mine, ore merchant and Collector's Seal give only what the supplier already carries. This applies to both
run kinds.

**Augments** set the weapon's element.

| Augment | Element | Tier | Price | Daily stock | Guild element rule (first hit each round) |
|---|---|---|---|---|---|
| Ember Resin | Fire | 1 | 8 | unlimited | 1 Burn |
| Frost Bloom | Frost | 2 | 18 | unlimited | 1 Chill |
| Verdant Sap | Verdant | 2 | 16 | unlimited | 1 Regeneration to the wielder |
| Stormglass | Storm | 3 | 40 | 1 | Arc 2 to the last enemy |
| Grave Dust | Grave | 4 | 55 | 1 | 1 Bleed |
| Sun Ash | Sun | 5 | 85 | 1 | Mark |

**Catalysts** (Advanced only). At the forge all four give +4 quality, +5 points exceptional, −5 points defect; they differ
as signature conditions and, in a guild run, by fight rule.

| Catalyst | Price | Daily stock | Guild fight rule |
|---|---|---|---|
| Binding Salt | 15 | 2 | Capacitor: healed → 1 Charge; burst at 3 Charge |
| Runestone Shard | 30 | 1 | After a party weapon cracked, a foe breaking an ally's Guard takes 5 |
| Dragon Oil | 55 | 1 | Spreads Burn to every foe when the wielder's hit burns; steam lance |
| Void Ink | 90 | 1 | Drinks Burn from the faced foe into Guard on the weakest ally |

Limited stock resets each morning. World condition multiplies prices; Caravan Ties adds +1 per level to every limited
material (never bypassing the fine-metal days).

## 5.3 Heartfire (new resource)

A run-only count, held at most 3 (excess is lost and reported). It is not a material (the 16 count is LOCKED).
Sources: classic, each siege **won** from the 4th siege on; guild, the starfall site, a delve's deep hall, a recovery
contract and a warlord broken on the wall. Spent by **Heartfire forging** (§5.4). An irreversible spend asks for
confirmation stating cost and consequence (owner ruling): a question before anything is spent says what it uses, what
is held after, that both are spent either way, the chance in words and what a miss gives; "Not now" spends nothing.

## 5.4 Forge modes, energy and overwork

| Mode | Energy | Inputs |
|---|---|---|
| Quick Forge (LOCKED) | 2 | Family, metal, augment, risk |
| Advanced Forge (LOCKED) | 4 | Plus optional catalyst and one technique |
| Heartfire forging | 4 + 2 = 6, + 1 Heartfire | An Advanced forge in a fine metal |

**Techniques** (Advanced): **Temper** (defect −6 points, exceptional −6 points), **Quench** (−3 quality; forces the
augment's element affix even without a slot), **Etch** (+1 affix slot, defect +6 points). They are the trade-offs that
keep Advanced from being strictly better (LOCKED requirement).

**Energy (LOCKED 10).** Up to **4** overwork a day; next morning's energy is base − today's overwork; debt never carries
further. Salvage (1) and hone (2) also spend energy. Commands are rejected if energy is insufficient; no negative
inventory.

**Forge result (LOCKED).** Every valid attempt yields a usable weapon; low rolls give flawed or weak weapons, never
scraps. Energy and materials are consumed exactly once; interrupting the reveal never retries.

## 5.5 Rolls, quality, power and rarity (rules 6)

**Risk** (PROPOSED; player sees words only):

| Risk | Exceptional | Defect | Quality roll | Extra |
|---|---|---|---|---|
| Safe | 6 % | 2 % | ±6 | Heartfire breakthrough −10 points |
| Balanced | 15 % | 8 % | ±13 | |
| Reckless | 30 % | 30 % | ±13 | +1 property slot; breakthrough +10 points |

Modifiers clamp to 0–75 %. Exceptional and defect are independent and can both happen.

**Quality** (PROPOSED, clamp 1–100):
`25 + 6·metalTier + 3·augmentTier + affinity + mastery + ledger + roll + (exceptional ? 20 : 0) − (defect ? 12 : 0) − (Quench ? 3 : 0)`.
Affinity is the hidden pairing value (metal+augment −4..+8, augment+family −1..+6); mastery sums Forge Mastery, Master
Whetstone, Forgefire and the catalyst's +4.

**Power:** `familyBase + 3·metalTier + quality/5 + affix power (flaws negative) + signature bonus` (+4 for a
Legendary by deeds).

**Rarity (LOCKED ladder, PROPOSED bands):**

| Quality | From quality | Word |
|---|---|---|
| 1–34 | Common | crude |
| 35–49 | Uncommon | decent |
| 50–69 | Rare | fine |
| 70–84 | Epic in a fine metal, else Rare | superb |
| 85–100 | Epic in a fine metal, else Rare; never Legendary | masterwork |

**Legendary** comes only two ways:
- **Heartfire breakthrough.** No chance below quality 70 or in a lesser metal. From 70: 30 % + 2 points per quality point,
  +10 if the recipe's signature is known, ±10 by risk, capped at 75 %. A miss still gives a weapon with one more
  property slot; Heartfire and energy are spent either way.
- **Deeds.** A weapon gathers **renown** from real deeds (classic: an elite slain 1, a warlord held at the wall 2;
  guild: a warlord felled on the wall 2, the charter leader felled 3, a rescue, recovery or lethal hall won 2). At 3
  renown it becomes Legendary once, with a line in its history.

A weapon's sheet says which way it became Legendary. Effect measured: an Epic by day 5 in a classic run fell from 99 %
of runs to under a quarter.

## 5.6 Affixes and flaws

**Slots:** Common/Uncommon 0, Rare 1, Epic 2, Legendary 3; +1 exceptional, +1 Etch, +1 Reckless, +1 Heartfire miss,
+1 Ashen Bellows. The augment's element affix fills first, then neutral affixes. A defect adds exactly one flaw. Flaws
are never removed by a command (except the Sentient visitor answer in a guild run).

**Beneficial (12, PROPOSED):** Keen, Reinforced, Flaming, Frostbound, Stormcharged, Vampiric, Swift, Giant Slayer,
Undead Bane, Guardian's, Lucky, Resonant. Classic effect: attack/defence multipliers, wound and loot modifiers, bane
bonuses. Guild effect: a typed rule (e.g. Reinforced enters with 4 Guard; Swift adds a 30 % cut; Giant Slayer +4 against
elites and leaders).

**Flaws (6, PROPOSED):** Brittle, Heavy, Unstable, Cursed, Bloodbound, Sentient. Each has a predictable trade-off in both
models (e.g. Brittle: −defence and may shatter on a lost expedition; in a guild fight it cracks when Guard breaks).
Cursed and Sentient never act on their own will (LOCKED).

## 5.7 Signature recipes (24, 4 per family)

Each is family + metal + augment, with optional catalyst and risk conditions and a quality floor; it grants two affixes
and +4..+9 power; six have their own guild fight rule. An undiscovered signature transforms 15 % of the time on its
exact recipe (+1 point per mastery point, +5 per Anvil Lore level, cap 50 %). **A discovered signature transforms 75 % of
the time** ("knowledge is control"). A signature never changes the metal's rarity cap. Recipes are found through
experiments and clues, never brute force (LOCKED).

## 5.8 The Notebook (Experiment Journal) and the clue ladder

Persistent across runs. **Pairings** (metal+augment, augment+family): `UNKNOWN → OBSERVED` (first forge) `→ UNDERSTOOD`
(3 forges; Runic Insight speeds it). Understood pairings stop counting (no farming). Shown as words only ("Seems
promising", "Excellent match", "Slight drawback").

**Signatures** climb a four-rung **clue ladder**: recipe, catalyst, temper, quality. Every miss on the base recipe earns
the lowest missing rung; **rumours** (a customer who slays an elite or collects a commission) earn rungs too (at most 4 a
run, 3 days apart); the Strange Weapon Fragment event marks one observed. Discovery records the name permanently.

**Fight notes** (guild): the rules the smith has forged, and interaction paths marked "Seen in a real fight" only from a
real contract or siege, never from the bench.

## 5.9 Fight rules of a weapon (guild runs)

A weapon's rules are composed in one place in a fixed order: family, metal, element, affixes, flaws (everyday, then
home/road only), catalyst, signature; a rule two parts share is listed once. Weapons keep the composition they were
forged with (older weapons stay at composition 1, without metal rules). The **forge outlook** shows a recipe's certain
rules, possible ones and its trade-off before forging; the **bench** rehearses it against four enemy lines with six
companions on a fixed seed: it costs nothing, records nothing and never counts as evidence. Both exist only in guild
runs; a classic run keeps the multiplier model.

## 5.10 Finished-weapon actions

| Action | Cost | Effect |
|---|---|---|
| Hone | 2 energy + 1 unit of its metal | First time +6 quality; always restores condition; wakes a returned legend's dormant affixes. Never lowers rarity, never past the metal's cap, never Legendary |
| Salvage | 1 energy | Destroys it; 1 metal unit back (Salvager's Crucible: also the augment of a fine weapon, once a day) |
| Scrap (bulk) | 0 energy | Any number of stock weapons; 1 metal unit per 4 of that metal |
| Give to the watch | free | 20 % of its power into the armoury (cap 30, halves each siege); +1 reputation |
| Lend (guild) | free | To a contracted member at home; still the smith's; recall when home |

Storage is unbounded (nothing the player owns is deleted). A weapon promised to an heirloom commission cannot be
listed, melted, scrapped, given or lent.

## 5.11 Workshop tools (run-only, bought with gold)

Great Bellows (+1 energy; 300, 700), Master Whetstone (+3 quality; 120, 300), Painted Signboard (+1 counter seat; 150,
400), Display Case (+2 shelf slots; 200). They exist because gold piled up unspent.

---

# 6. Living weapons

**Lifecycle (LOCKED):** one authoritative location at all times: Storage, Shelf (price), Owned (hero, equipped or
carried), **Loaned** (guild member), Lost (taken by enemies, held by a travelling merchant, given to the watch, lost with
a hero, sold to a collector), Destroyed (salvaged, scrapped, shattered). History lines record forging, owners,
inheritance, recovery, titles, deeds and returns.

**Wear:** condition 100..0. Classic expedition win −6, loss −10, siege −15 (×2 under Long Assault); guild contract −6,
a crack −25 more. Power factor 1.0 at full condition down to 0.75 at 0; below 50 is "worn" and its owner wants a
replacement.

**Fame:** +1 per won expedition or contract, +2 per siege held, +2 per elite. Only the first 10 count: +0.5 % power, +0.05
purchase appeal and +0.5 % suggested price per point. Fame is not renown (§5.5).

**Titles:** the first is permanent and enters the name: "Slayer of <elite>" on an elite kill, "Bane of the <faction>" at
5 victories.

**Fates when a hero dies** (PROPOSED): recovered to the forge or seized by the enemy by place (road 50/50, walls 70/30,
elite 40 recovered / 70 seized); a living guildmate may inherit an unseized weapon (60 %); otherwise a travelling
merchant may surface it 2 days later for 3 days (30 % + 5 % per fame, max 70 %); else it is lost. In a guild run a
member's loaned weapon taken by the enemy gets a named **nemesis** bearer, a recovery contract is posted, and the
nemesis may carry it to the wall.

**Legend Board:** at a run's end up to 3 weapons with fame ≥ 3 are recorded with their make and a 12-line story; the
account keeps the newest 20. **A Famous Weapon Returns** (once a run, more likely with Homing Steel) brings one back at
70–85 % quality, affixes dormant until honed, at most Epic and without its legend origin; it can earn Legendary again by
deeds.

**Bounds:** newest 10 combat lines and 24 everyday lines per weapon; ownership-grade lines kept forever; destroyed or
lost weapons pruned 30 days later unless they are Legend Board candidates or signatures.

---

# 7. Shop, economy and the town's customers

## 7.1 Shelves and pricing

8 shelf slots (+2 Display Case). The player lists and unlists and sets any price ≥ 0 per weapon (LOCKED manual
pricing). The **suggested price** is `power × condition factor × 4`, plus up to 5 % for fame. The forge result's
primary action is "List at suggested", with the price and how many living heroes could pay it shown above it; "Set
price" opens the price editor (−10, +10 or typed, the count following the chosen price) and "Back" there lists nothing;
Store keeps it (decision K).

## 7.2 Who comes to the counter (fair selection)

1. **Willingness:** one draw per living resident: 0.35 + traits + loyalty (to +0.10) + reputation (to +0.25) + festival
   0.2 + a standing want answered 0.3 + a champion in a siege warning 0.15, clamped 0.05–0.9.
2. **Seats:** 6 (+1 per Signboard level, +3 on a festival). Heroes turned away 2 days running sit first; the first 3
   seats spread across classes; then weighted draws (never served, waiting, unarmed or worn, loyal, want answered;
   halved for a hero who browsed yesterday and bought nothing).
3. Served in seating order; turned-away heroes are recorded.

Guild members are not residents and never shop. The town refills toward 12 (certain below 9, events cap at 16).

## 7.3 The purchase decision

v1.0's utility shape is kept: `improvement + class fit + element taste + preference + loyalty − price penalty`, with
slight seeded noise and one purchase per visitor a day, now including wear, a 40 % **trade-in** credit for the replaced
weapon, weapon fame, **sidegrades** (a weapon at most 5 % worse bought once for a taste, a prize or a story), **standing
wants** (a hero who found nothing leaves a want for 3 days) and **siege demand** (+0.8 for the besieger's weakness in
the warning window). Reputation and loyalty raise what a hero will pay (up to +25 % each). Traits move price
sensitivity (Greedy, Patient, Vain). Every visit stores reason codes and facts, never weights; the UI words them
("couldn't afford anything", "thought the price was too high", "found no upgrade").

## 7.4 Reputation, loyalty and gifts

Reputation: +1 per sale, +2 per commission, +2 per fulfilled ambition, +1 per donation, events and visitors; −1 when an
accepted commission expires; no decay; resets each run (Known Name gives a start). Loyalty: +1 per sale (Loyal ×2), +2
per commission and fulfilled ambition; **regular** at 3. **Gifts (decision M):** a sale below 25 % of the asking price is
a gift: no sale reputation, at most +1 reputation a day from gifts, and gift loyalty stops at 2 (a gift never makes a
regular).

## 7.5 Commissions

Up to **2** open at once; a 30 % chance each evening to offer one for tomorrow. Each card states its reason: ordinary,
replacement (worn or lost weapon), siege preparation (the feared element, due on the siege day), ambition (a collector,
fine), first blade (a guild hero ordering for an unarmed one). Noble commissions come from a world event; wall pledges
and heirlooms from morning visitors. Quality is always a band floor (decent 35, fine 50, superb 70). Reward `40 + 2 ×
floor` (110 or 140; elemental ×1.5; noble 540); deadline 4 days. Delivery is automatic at End Day, from storage first,
choosing the least sufficient weapon; nothing is reserved except heirlooms. Declining costs nothing; an accepted order
that expires costs 1 reputation. One **commission board** holds commissions and customer wants and opens from Shop
and Forge.

## 7.6 Supplier and gold

Common metals and augments are unlimited; every rarer material has 1–2 a day (§5.2). Gold sinks: materials, tools,
visitor answers, guild fees. There is no gold-funded repair (LOCKED). Income lines are a strict ledger (gold at open +
income = gold at close): shelf sales, sale bonuses, stipends, commissions, collectors, tributes, guild contracts and
insurance. Bulk scrap returns materials, not gold, so preparing never earns gold.

---

# 8. Heroes and the town

## 8.1 Classes (LOCKED 5)

| Class | Base power (+/level) | Families | Element taste | Purse |
|---|---|---|---|---|
| Guardian | 16 (+2) | Sword, Axe, Spear | Frost | 60–140 |
| Ranger | 14 (+2) | Bow, Dagger | Storm | 50–120 |
| Duelist | 15 (+2) | Sword, Dagger | Grave | 55–130 |
| Battlemage | 13 (+3) | Staff, Sword | Fire | 70–150 |
| Warden | 15 (+2) | Spear, Axe, Staff | Verdant | 50–120 |

Each class also has a guild combat kit (§10.3).

## 8.2 Identity

2–3 of **9 traits** (Brave, Cautious, Greedy, Ambitious, Loyal, Curious, Patient, Vain, Restless); each moves an
observable weight (activity choice, shopping, price sensitivity, loyalty gain, combat ×0.8–1.2). One of **4 ambitions**:
Slayer (4 expedition wins), Defender (stand in a held siege), Collector (wield quality ≥ 50), Fortune (hold 300 gold);
fulfilling one gives fame, loyalty, shop reputation and a legacy milestone. Names from 120 first names and 96 surnames,
never repeated in a run; a face from the owner's 20-hero portrait set, stored on the hero, upgraded after a title or 5
kills.

## 8.3 Daily life (classic, and townsfolk in a guild run)

One weighted choice a day among **Expedition, Patrol, Rest, Guild hall, Ambition work**; shopping is a morning errand
before it. A hero under 50 health must rest. Heroes act against the faction with the highest pressure. Patrols give
militia, suppression, forge recovery and 12 gold; expeditions give gold, XP, fame, loot and suppression, or wounds;
guild days give XP and mentoring.

## 8.4 Growth and generations (LOCKED hybrid)

120 XP a level; retirement at level 8 or after 10 victories by chance. Injuries are health only. A hero dies at 0 health
on a lost expedition or a routed siege; the dead never act or own equipment. A famous hero (fame ≥ 3) founds a company
in life or at retirement; others join; every retiree leaves a mentee who inherits their weapons. At the run's end the
most famous hero becomes a **lineage anchor**; the next era's first hero is their descendant (level 2, Loyal, same class
and surname), and "Descendant of a Champion" brings more.

## 8.5 The three defenders

Three places, filled automatically by the strongest **available** heroes (health ≥ 50) ranked by defence against the
committed besieger; 0–2 when fewer are fit, militia and armoury alone when none (LOCKED). Re-picked every night for the
forecast. In a guild run reserved members take places first (§12.8).

---

# 9. Factions, threat and sieges

## 9.1 Factions (LOCKED 3; PROPOSED numbers)

| Faction | Growth/day | Weak to | Resists | Elites | Warlord |
|---|---|---|---|---|---|
| Ashclaw Raiders | 4 | Frost | Fire | Warchief, Beastmaster | Warlord Krag |
| Hollowbound | 3 | Sun | Grave | Lich, the Pale Knight | the Hollow King |
| Embermaw Brood | 2 | Frost | Fire | Wyrm, Cinder Matriarch | Broodmother Vyrash |

`pressure' = clamp(pressure + growth − suppression, 0, 100)`; pressure events add 10; a held siege takes 25 off the
besieger (40 with a warlord). Pressure is shown as a word (quiet … overwhelming). A warlord leads at pressure ≥ 50.
Weak ×1.25, resisted ×0.8. Two factions share Frost/Fire (OPEN).

## 9.2 Siege calendar (LOCKED predictable)

Every 5 days (5, 10, 15 …), won or lost; never a surprise siege. Classic: the besieger is committed at the first warning,
2 evenings before; the warning names the faction, its pressure word, warlord, weakness and trait. Guild: committed with
its field and preparation 4 days before.

## 9.3 Siege traits

The first siege is plain; from the second, 70 % carry a trait announced when the siege is scheduled: **Long Assault**
(worn weapons count less, double wear; guild: 12 rounds) or **Many Breaches** (watch and militia ×1.75, raid ×1.05).

## 9.4 Classic siege resolution

```
townDefense = Σ top-3 champion defence + (militia + armoury) × watch   (watch 1.75 under Many Breaches, else 1)
raidPower   = (32 + 6.5·day + 0.65·pressure) × 2.0 × world × trait
held        = townDefense ≥ raidPower
forgeDamage = clamp(round(24 + 50·(raid/defense − 1)), 0, 60)     // applied held or not
rout        = lost and raid ≥ 1.5 × defense → each champion −55 health (else −40)
```
Held: champions −10 health, XP and fame, weapons +2 fame and wear, a blessing offer; a warlord beaten pays 120 gold
tribute and renown; from the 4th siege a win gives Heartfire. Forecast words: strong, even, outmatched, dire. A held
siege can still cost up to 24 integrity (a classic card still says "held"; the guild says "held at a cost").

## 9.5 Forge integrity and recovery (LOCKED hero-driven)

Start 100 (+Stalwart Walls). Nightly repair from patrols and expedition wins, capped at **2 a day** (+3 with Stalwart
Town). In a guild run wall-work contracts draw on the same allowance plus 1 (decision B); patrols take what is left. No
gold repair. Integrity 0 ends the run.

---

# 10. Combat

## 10.1 Two resolvers

| | Scalar resolver (`battle/`) | Interaction engine (`combat/`) |
|---|---|---|
| Used by | Every classic fight; residents' expeditions in both run kinds | Guild contracts, the guild siege, the forge bench and outlook |
| Model | Power formulas with multipliers | Typed events and effects, round by round |
| Shows | Replays built from rolled numbers | A full timeline with highlights |

Both are deterministic and seeded; neither reads UI state; no animation recalculates a fight (LOCKED).

## 10.2 Scalar model

```
attack = (classBase + perLevel·(level−1) + weaponPower·condition) × classFit × (0.6 + 0.4·health/100)
         × elementMatchup × traits(0.8..1.2) × affixes × blessing × fame
defence = the same with defence affixes and the family's defensive weight
```
Expeditions: enemy `(18 + 1.4·day + 0.25·pressure) ± 20 %`, ×1.35 for an elite (6 % + 0.2 % per pressure point); win
chance `0.5 + (attack − enemy)/40` within 5–95 %.

## 10.3 Interaction engine

Pure: a fight setup in, a typed timeline out. Up to 4 actors a side, 8 rounds (12 under Long Assault). Effects are
**trigger, conditions, cost, action, limit** (per round / per fight); an effect never answers itself and an echo never
answers an echo. States: Guard, Charge, Mark, Burn, Regeneration, Chill, Wet, Bleed, Scrap, Thorns, Steam. Only a strike
roll draws randomness.

**Class kits:** Guardian (40 health, Guard and intercept, riposte), Warden (heals the weakest), Battlemage (opening
sigil, elemental echo), Ranger (Mark and loose), Duelist (double cut). **Enemies** per faction: grunts, a special,
elites, a warlord (e.g. the Embermaw drake breathes fire on everyone every third round); plus town watch guards,
captives and supply carts.

**Postures:** Cautious leaves when a member is down or health is under 40 %; Balanced when two are down or the last is
under 30 %; Reckless never. **Objectives:** defeat, or hold n rounds, optionally protecting a unit. Outcomes: won,
retreated, lost.

**Highlights:** up to 3 causal chains chosen by weight (a party member downed, an enemy downed, a summon, a crack, rules
that combined), each sentence built only from recorded event text.

---

# 11. Run variety in both kinds of run

## 11.1 World events (LOCKED 25)

23 pooled events plus 2 deterministic rules (guild founding, champion retirement). One roll a day (30 %); each event has
eligibility, weight, run limit, cooldown and a story template; events needing history are ineligible without it. Three
pooled events now arrive as morning visitors under the same limits (Merchant Festival, Wandering Master Smith, The
Collector). Automatic: Traveling Ore Merchant, Trade Caravan Delayed, Abandoned Mine Rediscovered, Noble Commission, New
Adventurers Arrive, Veteran Returns, Heroic Inheritance, Raider Encampment, Restless Graves, Volcanic Tremors, Successful
Patrol, Border Ambush, Ancient Smithing Notes, Mysterious Alloy, Forgotten Shrine, Strange Weapon Fragment, A Famous
Weapon Returns, Descendant of a Champion, Forgotten Guild Banner, Ballad of the Blacksmith. All are reachable in
simulation.

## 11.2 Morning visitors

From day 2, on 75 % of mornings, one visitor with priced answers; no answer means a free default at End Day.

| Visitor | Choice |
|---|---|
| The Last Crate (siege near) | Counter-element augments at 0.8× or cheap metal at 0.7× |
| A Weapon for the Wall | A pledge on credit for a poor defender, or a rich patron's order at 1.25×; follow-up **The Debt Repaid** after the siege (collect, forgive, or speak for the forge) |
| The Master's Afternoon | 4 energy to understand a pairing, or 60 gold for a recipe clue |
| The Collector's Offer | Sell a prized weapon at 1.5× asking |
| A Cracked Heirloom | Restore a fallen hero's weapon for an heir (reserved, 3 days) or sell it |
| The Crooked Merchant (day 4+) | A flawed weapon unseen at 0.55×, or inspect for 15 gold |
| The Smith's Wager (day 3+) | Stake 40 to forge a named family at superb within 3 days for a relic |
| The Festival Contract | A festival stall today, or a watch bounty for donations |

Guild runs add four more (§12.11).

## 11.3 Workshop relics (3 slots)

Salvager's Crucible, Tempering Ledger (+2 quality per different family in a streak, max +6), Collector's Seal (the first
sale of a day worth 100 gold or more earns a seal; three seals earn a scarce material), Ashen Bellows (borrow 2 energy from tomorrow for +1 property slot). Offered as
3 to choose from; a full workshop names the relic it gives up; "decide later" keeps the offer.

## 11.4 Blessings (8)

After a held siege, 3 of 8 are offered; each lasts 5 days: Forgefire (+6 quality), Tireless Hands (+2 energy), Merchant's
Favor (+20 % sale gold), Runic Insight (faster notes), Stalwart Town (+3 recovery), Hunter's Edge (+10 % hero power),
Lucky Alloy (+8 points exceptional), Guild Patronage (town-guild heroes eager to buy, with a 30-gold stipend; offered
only when a guild exists). An unanswered offer lapses after 3 mornings.

---

# 12. The guild run

All names and numbers PROPOSED; the rules in §3 are LOCKED by the 2026-10-10 revision.

## 12.1 What changes

The smith runs a small adventurers' company beside the shop. Members are heroes under contract, not residents: nothing
autonomous sends them shopping, wandering or retiring by level. Townsfolk stay fully autonomous. Combat stays automatic.

## 12.2 Charters (3)

| Charter | Starting members | Gives | Costs | Play |
|---|---|---|---|---|
| Town's Last Hope | Guardian + Warden (Battlemage offered at half fee) | +3 militia, Binding Salt | −40 gold | Protect, strike back, rebuild |
| Licensed Bad Ideas | Duelist + Battlemage | Runestone, Void Ink, 2 Silver; dangerous contracts from day 1 | −50 gold | Invent a build, take the risk |
| Honest Business, Mostly | Ranger + Warden | +2 reputation, +1 contract on the board | The forge keeps 80 % of contract gold | Fund specialists through trade |

## 12.3 Opening packages (8, three offered)

On the first morning 3 of the charter's packages are offered; End Day waits for one. Each names a build direction, the
trait each signer takes, a relic, stock or a weapon, and a **drawback** shown before choosing: The Overflowing Cup
(overheal), Break It on Purpose (breakage, with a Brittle sword), The Stormwell (storm and Charge), Borrowed Fire (Burn
into Guard), The Marked Card (Marks and burst), Paid in Blood (blood price, with a Bloodbound axe), The Plain Oath
(common steel; a Rare weapon ends the oath), Careful Books (take it and run). Drawbacks are gold (−40 to −60) or forge
health (−10 to −15), never militia.

## 12.4 Roster and recruiting

Up to 6 members; parties of up to 3; 3 wall places. Two candidates "would sign today", redrawn every 3 days, favouring a
missing class, each fully disclosed (class, level, defining trait, fee, share). **Fee** 35 + 15 per level above 1, paid
once; **no wages**: each member in a winning party takes 10 % of the stage's gold (Greedy 15 %). **Defining traits
(12):** Protective, Impatient, Cowardly, Greedy, Methodical, Loyal, Storm-Touched, Pain Collector, Rust Savant,
Overhealer, Second Wind, Oathkeeper. Dismissal is free for a member at home who is not in today's planned party.
Members at home rest or train automatically (decision J); the roster says so in one line. **Wounds:** Exhausted (1 day), Injured (2 days), Scarred
(5 days, enters fights at 75 % health). **Specialities:** deeds open a choice between two branches (Vanguard/Striker,
Unbroken/Careful hand, Wallwarden/Rallier).

## 12.5 Loans

The smith lends a weapon from storage or the shelf to a member at home; it stays the smith's. A loan outranks a
member's own weapon in a fight. Loadouts are frozen when a party leaves. A captured member's loan goes with them; a
member killed or routed may have it seized (the nemesis may carry it).

## 12.6 The contract board

Always: **Work on the wall** (no fight, +5 integrity within the day's allowance, +1 militia) and a free **Supply run**.
Drawn, up to 3 slots counting supply and siege jobs (+1 Honest Business): **Hunt** (day 2+), **Escort** (day 3+),
**Relic delve** (day 4+, an optional lethal deep hall with a relic and Heartfire). Posted for a reason: **Rescue** (a
captive, with town watch guards filling empty places) and **Recovery** (the nemesis's weapon, Heartfire). **Siege jobs**,
posted once per siege when the besieger is committed: **Sabotage** (spoils the preparation) and one opportunity,
**Starfall site** (fine metal, Heartfire) or the **Supplier's road** (a grateful supplier: +1 stock for 5 days); both take
two days and there is one party, so taking one lets the other lapse. Cards last 3 days (rank 3: 2).

**Modifiers** (50 % of drawn cards): Shields up, One more of them, Tired sentries, Hold the ford, One of the watch is
held, A narrow way (party of 2, 150 % gold), Paid in metal. **Laws of the season** (from day 8; in force the morning
after they are announced; contracts only): Storm front, Eclipse, Scrap shortage. A **rival guild** from day 6 takes one
open card every 4 days, announced.

## 12.7 Deployment

Plan one contract with 1–3 members in acting order and a posture; the fee is paid when the plan is made and refunded
if it is cancelled or replaced. One party at a time; a party away is not on the wall. A won stage's reward is secured
that evening. Retreat keeps nothing but the Cowardly member's (34 %) or Coward's Medal (50 %) share. **Losses only on
defeat:** safe, nobody; dangerous, the last to fall is taken captive (5 days to rescue); lethal, the first to fall dies
and the last is taken. After a won first hall of a delve the smith chooses Return (default) or Push the next morning.

## 12.8 The wall

Up to 3 members at home can be reserved; reservations clear after each siege. The forecast shows who would stand, who
is missing and why, the watch, the field, the preparation and the nemesis.

## 12.9 The guild siege

Committed 4 days ahead: strength 190 % + 3 % per siege day + 0.5 % per pressure point of the base; the leader is the
faction's warlord at pressure ≥ 50 or on the charter siege (110 %), otherwise an elite; 2 grunts plus 1 per 3 sieges.
**Preparations** (one per siege, announced with its answers): Shield wall, Knives for the back (Ashclaw); A gravecaller
behind the line, Grave mist (Hollowbound); A second drake, Banked fires (Embermaw). The night: the watch and militia
thin the line on the **outer approach**, then the three defenders fight. Verdicts: **held**, **held at a cost**,
**breached**; forge damage scales with the enemy left (max 60); a rout kills the first defender to fall. A warlord beaten
outright pays tribute and Heartfire; the blade that felled it gains renown. The siege card says what decided the night.

## 12.10 Charter milestone, retirement and continuing

The first siege on or after **day 20** is the charter siege, led by the warlord at 110 %. Beating it secures the charter
(150 gold tribute, 6 legacy points) and offers **retirement**: the run ends as a success ("The chapter is closed") and
is claimed like any other. **Continuing** is the same endless run; the smith may raise the **guild rank** (only upward):
Chartered Company (enemies ×1.15, +2 legacy points), Wardens of the March (×1.30, +1 siege unit, +4), The Last Company
(×1.45, cards stay a day less, +6). The charter and rank points count once, however the run ends.

## 12.11 Stories and visitors

**Nemesis** (a named bearer of a taken loan), **rival guild**, **bonds** (Comrades after 3 contracts won together,
Protector for a rescuer). Guild visitors: The Coward Returns Alone, The Sword Files a Complaint, Your Best Sword Is
Across the Counter (buy back at 150 %), Insurance Adjuster from the Underworld; the Festival Contract and Cracked
Heirloom gain guild answers.

## 12.12 Relics and Heartfire in a guild

The opening package brings the first relic; drafts after sieges 1, 2 and 3 (and a deep hall) offer one **party rule**,
one **forge tool** and one **pact**. Party rules: Overflow Basin, Salvage Bell, Storm Ledger, Furnace Lung, Bone Music
Box, Blood Receipt, The Blunt Oath, Coward's Medal. Pacts: Widow's Whetstone, Oathbreaker's Coin. Heartfire sources:
§5.3.

## 12.13 What a guild run replaces from the classic contract

| Classic (LOCKED in v1.0) | Guild run (owner revision 2026-10-10) |
|---|---|
| Only shop actions; never equip or direct heroes | Contracted members receive loans, form a party, take a chosen contract with a posture. Townsfolk stay autonomous |
| Three strongest champions chosen automatically | Three places and the recommendation stay; members may be reserved |
| No end point | Endless play stays; a day-20 charter milestone and optional retirement |
| Upgrades mainly extend survival | Kept; new progression is run choices (charter, opening, rank), not new power |
| All information descriptive | Hidden odds in words; deterministic rules, thresholds, fees, timing and trigger limits exact |
| Relaxed economy | Crafting affordable; pressure on fees, scarce stock, roster and opportunity cost |
| One workshop with panels | Four destinations kept; Town becomes Guild |

---

# 13. Legacy and permanent progression

## 13.1 Legacy points (claim once per run, LOCKED)

`points = 6 + floor(day/5) + min(discoveries, 5) + milestones (+ charter 6 + rank 2/4/6 in a guild run)`.
A discovery is a pairing understood, a signature discovered, or a clue granted by an event or visitor. Milestones (once
a run): first sale 1, siege survived 2, champion armed 2, Epic forged 1, Legendary forged 3, Legendary by deeds 3, hero
level 5 1, a weapon's fifth victory 2, elite slain 1, warlord defeated 3, ambition fulfilled 1 (classic maximum 20).

## 13.2 Upgrades (11 tracks × 3 levels, 8 / 20 / 45 points)

| Track | Per level |
|---|---|
| Tireless Smith | +1 daily energy |
| Family Savings | +100 starting gold |
| Forge Mastery | +4 quality |
| Stalwart Walls | +20 starting forge integrity |
| Thrifty Hands | +10 % chance the augment survives |
| Well-Stocked Cellar | +2 of every starting material |
| Lucky Hammer | +2 points exceptional chance |
| Known Name | +5 starting reputation and one starting regular |
| Caravan Ties | +1 daily stock of every limited material |
| Anvil Lore | +5 points signature chance |
| Homing Steel | Legends return more often and stronger |

Bounded on purpose: all tracks maxed add about +10 to +15 mean days. Most tracks move a run by a day or less; showing
each track's own measure on the claim screen is OPEN. Its chance tracks (Thrifty Hands, Lucky Hammer, Anvil Lore) once read "N forges in 10", which states exact odds; they are
worded instead: "kept now and then … more often than at level 1. It is never certain." (decision A).

## 13.3 Persistence

| State | Across runs? |
|---|---|
| Notebook: pairings, signatures, clue rungs, fight notes | Yes |
| Upgrades, unspent and total legacy points | Yes |
| Legend Board (20), lineage anchors (10), era history | Yes |
| Settings, guided-first-day status, last charter choice | Yes (device settings, not the save) |
| Gold, materials, stock, shelf, tools, relics, Heartfire, blessings | No |
| Heroes, town, militia, factions, day, sieges, world condition | No |
| Reputation | No (Known Name gives a start) |
| Everything of the guild (members, loans, bonds, nemesis, rival, rank, milestone) | No, except its legacy points and the legends and lineage it produced |

All three charters are open from the first era; there are no guild unlocks in the legacy.

## 13.4 Not built

Achievements, leaderboards and Play Games are DEFERRED; "milestones" are the in-run legacy table only.

---

# 14. The Emberfall Gazette, the shop day and fight reports

**LOCKED:** every line the game tells about the world comes from real event records; the paper never claims what did
not happen; animations are optional and change nothing.

- **The shop day** (after every End Day): the saved day played card by card: the guild contract report (if any), the
  shop opening, up to 3 featured visits (the customer, the weapons weighed with factual reasons, the receipt, a
  recognition line), a tally of everyone else, the till, up to 3 aftermath cards (siege first; deaths, elites,
  ambitions, titles, inheritances), then a blessing choice, "Tomorrow" with the one lead of the day, or "Fallen". Manual
  by default; Auto 1×/2×; Skip to evening; position saved.
- **The lead:** one deterministic piece of advice in a fixed priority (first weapon, choose a blessing, answer a
  commission, forge for one, stock the shelf, prices too high, arm the defenders, answer a want, forge for buyers), each
  with one action. The same function feeds the Shop page and the Tomorrow card.
- **Recognition lines:** at most one per visit (first visit, became a regular, held the wall, still carries your
  weapon, its weapon is worn, waited yesterday…), chosen from facts with no randomness.
- **The Gazette:** a typed **briefing** (at most 5 stories and 2 "before tomorrow" notices; era end, siege, deaths and a
  new siege warning are never hidden) over the full edition (top 5 headlines by priority, then Shop, Heroes, Town,
  Forge). An archive of every day in Records.
- **Replays:** a classic siege replay (one round per champion, militia, watch, the raid) and expedition replays only for
  elite fights and deaths (at most 3 a day), built from already-rolled numbers. Guild contracts and sieges show a
  **fight report**: outcome, both sides' ending health, 3 highlights and the whole timeline.

---

# 15. Disclosure (LOCKED as revised)

| Shown exactly | Shown in words |
|---|---|
| Energy, overwork debt, costs, prices, fees, rewards, deadlines, return mornings, siege days | Pairing affinity (Notebook stages) |
| Quality spread of a temper, extra slots, quality needed for Heartfire, the fine-metal rule | Exceptional and flaw chances of each risk (decision A) |
| Every fight rule, its numbers and trigger limits (guild) | Heartfire breakthrough chance (decision A) |
| Stats of a forged weapon (power, quality, condition, renown) | Signature progress (clue rungs; a known one "usually takes") |
| Siege forecast numbers (defence vs raid) | Pressure, siege odds ("dire", "even"), health and wear bands |
| How many heroes can afford a price | Why a customer did or did not buy (reasons, never weights) |

No utility, weight or probability leaves the simulation. Hidden odds are worded by one helper (`core/text/Odds`) on two
scales: how often ("never", "rarely", "now and then", "often", "more often than not") and the chance of one attempt ("no
chance", "a slim chance", "a fair chance", "a good chance", "a strong chance"). No word promises an outcome; a chance of
zero is a rule and is said plainly.

---

# 16. Content library

| Content | Count | Status |
|---|---|---|
| Materials | 16 (6 metals, 6 augments, 4 catalysts) | LOCKED count |
| Weapon families | 6 | LOCKED count |
| Hero classes | 5, each with a combat kit | LOCKED count |
| Factions | 3, each with elites and a warlord | LOCKED count |
| World events | 25 (23 pooled + 2 rules; 3 delivered as visitors) | LOCKED count |
| Signature recipes | 24 (4 per family) | PROPOSED |
| Affixes / flaws | 12 / 6 | PROPOSED |
| Blessings | 8 | PROPOSED |
| Upgrade tracks | 11 | PROPOSED |
| Workshop tools | 4 | PROPOSED |
| Morning visitors | 8 + 1 follow-up classic, 4 guild | PROPOSED |
| Relics | 4 workshop, 8 party rules, 2 pacts | PROPOSED |
| Siege traits | 2 | PROPOSED |
| Hero traits | 9 (+12 guild defining traits) | PROPOSED |
| Guild | 3 charters, 8 openings, 10 contract kinds, 7 modifiers, 6 preparations, 3 laws, 3 ranks, 6 specialities | PROPOSED |
| Names, faces | 120 first names, 96 surnames; 20 portrait heroes (base and upgraded) | PROPOSED |

All names are PROPOSED; counts marked LOCKED are LOCKED. New content enters through stable IDs and
`ContentCatalog.validate()`; a content change raises the content version.

---

# 17. Portrait UX

## 17.1 Navigation (LOCKED: portrait, menus, no character)

Title → run setup → (guild: opening) → the workshop with **four destinations** in a bottom bar: **Shop**, **Forge**,
**Town** (called **Guild** in a guild run) and **Records** (Gazette, Notebook, Legacy). Top bar: day, gold, energy, and
Settings. Forge health and the next siege sit on a threat strip on Shop and Forge and on Town. Sheets: Storage,
Supplies and tools, Settings, hero and weapon sheets, the commission board, visitors, relics, blessings.

## 17.2 Shop

Threat strip, the counter with the shelf, the **lead of the day**, the morning visitor, relics, the shelf, doors to
Commissions & customers, Storage and Supplies, "Who is buying" (counts), and End Day pinned.

## 17.3 Forge (the workbench)

The planned weapon over the anvil; recipe slots **Weapon, Metal, Augment** (Advanced adds Catalyst and Technique); each
slot opens a tray of tiles (out-of-stock dimmed with Restock; in a guild run each tile shows its fight rule; augment
tiles show what works against the coming siege). Field notes from the Notebook in words; Quick/Advanced; risk with its
explanation; Heartfire row; relic lines; in a guild run the forge outlook and "Try this recipe on the bench". One gold
**Forge** action with its energy cost. Choosing is free; only Forge commits. The result card: the weapon (already
saved), stats fading in after a short hold (skippable, none with reduced motion), commission fit, what the Notebook
learned, a commission's reservation line (decision L), "List at suggested", "Set price", Store, Lend (guild).

## 17.4 Town / Guild

**Town:** the besieger with its weakness and the defence-vs-raid forecast, other threats, the three champions, heroes in
town, the fallen. **Guild:** party out, the wall (on the siege day and its eve first), yesterday's contract, the board,
the roster, candidates, the region (charter, rank, retirement, law, rival, nemesis), then factions and townsfolk.

## 17.5 Records

Gazette archive; Notebook (metal pairings, weapon pairings, recipe clues with "Use this recipe", fight notes); Legacy
(upgrades, blessings, Legend Board, lineages).

## 17.6 After End Day

The shop day (§14), then the Gazette modal with the briefing, all details and battle replays.

## 17.7 Run end

"The forge has fallen" or "The chapter is closed"; the points breakdown; **Claim** (once); weapons remembered and the
lineage; upgrades; the charter picker; **Begin era N+1**.

## 17.8 Guided first day

A new installation's first game is led through one real day: choose a recipe, forge, list, forge your own, End Day, read
the outcome. One short step at a time under the top bar, with "Why?" and "Skip guidance". Shown once per installation;
replayable from Settings; never in a game in progress. Not yet playtested with players.

## 17.9 Visual language and copy (owner direction 2026-10-14)

Alegreya Bold for headings only; Fira Sans Regular for sentences, Medium for labels, SemiBold for values and actions.
Charcoal surfaces, ivory text, brass accents, ember highlights; **gold only for the primary action and the chosen
mark**; red only for short costs. No ornamental frame on every section. Gains and costs as clean "+" / "−" bullets. Pick
one: no default selection, the whole card tappable, a check on the chosen card. Copy: concise, no em dashes or semicolon
chains, "commission" as the one word for a paid order, "Renown" for deeds. One dark theme.

## 17.10 Accessibility

48 dp minimum targets (52 dp primaries); system font scale honoured with layouts that restack above 1.3×; contrast set
per colour token; rarity, buffs, flaws and stages never by colour alone; reduced motion stops every animation and
auto-playback; haptics toggle; no time-pressure actions; TalkBack semantics. Portrait only; state survives rotation and
process death. NOT YET DONE: validation on a physical phone; audio (there is none yet, so no sound setting).

## 17.11 Failure handling

A load failure opens a recovery screen (what happened, what is safe, Retry, Start over that sets damaged data aside and
never deletes it). A failed save keeps the last saved state on screen. Rejected commands explain themselves.

---

# 18. Architecture (LOCKED: simulation is platform-independent)

## 18.1 Modules

Two Gradle modules (v1.0 allowed a simpler setup with package boundaries kept):

- **`:core`** — pure Kotlin JVM, no Android. Packages: `rng` (SplitMix64 streams), `content` (catalogs by stable ID,
  validated), `config` (`BalanceConfig`, versioned, nested groups), `model` (immutable `GameState`), `engine` (the only
  mutator `GameEngine`, typed commands and errors, invariants, compatibility, world events, visitors, consequences,
  relics), `crafting`, `market`, `heroes`, `battle` (scalar), `combat` (interaction engine, no `GameState`), `guild`,
  `legacy`, `gazette`, `shopday`, `text`, `persistence`, `sim` (simulators and labs).
- **`:app`** — Compose UI as an observer. `GameViewModel` sends every operation through **`GameSession`**, the one
  serialized boundary, which saves accepted results atomically (Room) before the screen renders them. DataStore holds
  only settings.

## 18.2 Commands and state

`GameEngine.handle(state, command)` returns Accepted (state, events, resolution) or Rejected (typed error).
Shop and forge commands (both runs): Forge, ToggleShelf, SetPrice, BuyMaterial, AcceptCommission, DeclineCommission,
ChooseBlessing, Salvage, Scrap, Hone, DonateWeapon, BuyTool, ResolveEncounter, ChooseRelic, DeclineRelicOffer, EndDay.
Guild only: ChooseOpening, RecruitHero, DismissHero, LoanWeapon, RecallLoan, PlanDeployment, CancelPlannedDeployment,
ReserveDefender, ChooseMissionCheckpoint, ChooseSpeciality, SetGuildRank, RetireAfterMilestone. Outside commands:
`newRun`, `closeRun`, claim, purchase upgrade. App operations: dispatch, claim, buy, Begin era, abandon, the shop-day
position.

## 18.3 Determinism (LOCKED)

Game-owned SplitMix64 with independent streams per subsystem: CRAFTING, HEROES, PURCHASES, COMBAT, FACTIONS, EVENTS,
LEGACY, WORLD, ENCOUNTERS (visitors, relics, siege traits), GUILD. Generator state is saved with the run. Same versions,
seed and commands give the same state and events. UI, previews, the bench and replays draw no gameplay randomness.

## 18.4 Persistence

One save row for the run, one for the legacy, one for the shop-day cursor, in a versioned JSON envelope
(`SaveCodec`, schema 7, with tested migrations). Rules 6 and content 6 are enforced on load; an older save over a lowered
cap is clamped with a line in the day's records. Saves grow by bounded rules (events and closed commissions 30 days,
weapon history caps, pruning of lost weapons). OPEN: a long save (about day 350–490) exceeds SQLite's 2 MB row window.

## 18.5 Offline (LOCKED)

No backend, telemetry, ads, billing SDK, accounts, runtime AI, timers or idle production; no permissions. Backup is
disabled; a legacy-only backup is DEFERRED.

---

# 19. Art and audio

**Intent (LOCKED):** detailed fantasy pixel art, coherent and readable behind Compose text. **Sources now:** AI-generated
concept sheets and weapon master sheet (sliced: 6 families × 7 element rows × 8 levels = 336 weapon sprites),
script-drawn production packs (battle frames, siege wall, milestone burst, markers), the owner's 20-hero portrait set
(base and upgraded faces) and title painting, and generated placeholders for any ID without imported art. All imported
by `tools/pixelart/import_assets.py`; never hand-edited PNGs; brief for new art `docs/ART_BRIEF.md`. Nearest-neighbour
when enlarging, bilinear when shrinking. Sprites are decorative: no essential text, no gameplay reads. Missing:
signature weapon art (a gold ring stands in), visitor portraits, the launcher icon. **Audio:** none yet; 11 effects and
1–2 loops are wanted (`Assets/Audio/WANTED.txt`). How the store listing describes the art's provenance is OPEN.

---

# 20. Testing, balance and targets

## 20.1 Invariants and determinism (ACCEPTANCE, all enforced)

Same inputs give the same state; renders never draw gameplay RNG; one owner or location per weapon; no dead hero acts or
owns; gold, energy and inventory within bounds; no duplicate craft, sale or reward after retry; siege days forecastable;
Gazette lines true; legacy persists, run state resets; content references valid; migrations keep history and RNG.

## 20.2 Tools

`:core:test`; `:core:simulate` (classic policies and bots, multi-era accounts, upgrade impact, customers, depth, probes,
sweeps); `:core:guildsim` (14 guild policies, paired comparisons); `:core:combat` (fixtures, sweep, lab); `:core:soak`
(2,000 days); `:core:scenarios` (debug saves); app unit tests; instrumented tests and emulator scripts.

## 20.3 Targets and where they stand

| Target | Now |
|---|---|
| New-player median first run 15–25 days | Plain smith median 25, mean ~24.6 (over the plan's 22.5 band top since visitors). Kept by owner ruling; pacing is judged by minutes per run and how repetitive the last days feel, not by day count alone |
| Viable paths beyond day 25 | Active play 30–36, expert bots 45–48 days |
| Upgrades materially longer, never immortal | Maxed accounts +10 to +15 mean days; every simulated run ends |
| Economy sustains experiments | Yes; expensive pricing ends runs earlier (median 15) |
| Builds change survival | Yes |
| Guild charter secured in 40–65 % of runs per charter and policy | Within band but one cell (66 %) |
| Epic no earlier than about day 7 in a guild run | Median first Epic day 7; Legendary from quality alone: never |
| End Day p95 < 200 ms (compute) | 2–4 ms desktop JVM, about 21 ms emulator |

v1.0 asked for 10,000 seeds per balance review: done to balance 8 and at the balance-10 gate; balance 12–15 changes were
measured at 300–1,000 seeds. Every balance change bumps `BalanceConfig.version` and is recorded in DECISIONS.

---

# 21. Scope, status and open questions

## 21.1 Phases

P0–P6 done (the application ID rename is left). P7 (balance, reliability, onboarding, accessibility) partial. The major
update, gameplay depth and guild evolution shipped through 0.8.0; run variety, deep forging, the guided first day and the
2026-10-14 review work sit under `[Unreleased]`. **0.9.0 is held** for a physical-phone pass and evidence for gates G3
(charter band), G12 (drawbacks that bite), G13 (classic golden state) and G14 (device). P8 (Play launch: application ID,
signing, listing, icon, privacy) not started; publishing and signing are the owner's.

## 21.2 DEFERRED

Multiplayer, PvP, manual combat, walking around town, real-time idle production, live-generated dialogue, online market,
accounts, cloud saves, achievements and leaderboards, microtransactions, ads, paid DLC, cosmetics, landscape layout,
legacy-only backup. Guild ideas not built: recruit complications beyond the heirloom heir, keepsakes and banners, the
scar's later change, a workshop specialisation slot, guild unlocks in the legacy, rival cooperation, 8 of 12 planned
guild scenes, residents' fights on the interaction engine. Depth ideas not built: more visitors, ventures, a rival smith,
seasons, challenge tiers.

## 21.3 NOT YET BUILT (decided)

Decided and not built: the workbench's Heartfire source line names only guild sources, also in a classic run (where a
siege won from the 4th gives it); the remaining stock dialogs on the house dialog.

Built on 2026-10-14 but not yet verified in the app: decisions A (odds in words on the workbench and in the upgrade
preview), J (the roster's line), K (list at suggested, set price), L (the reservation line) and the Heartfire
confirmation. Their core parts pass on the JVM; their app unit and device tests are written but were not run (the
session that built them could not build `:app`). Run `:app:testDebugUnitTest`, `ForgeResultCardTest` and
`tools/emulator/runvariety.sh` before relying on them.

## 21.4 Owner rulings and OPEN questions

Ruled on 2026-10-14 (DECISIONS "Owner rulings on the GDD v2.0 open questions"):
- **First-era length:** keep the current balance (plain smith median 25). Reassess by run duration in minutes and how
  repetitive the final days feel; day count alone does not establish a pacing problem.
- **Wall work's extra repair:** this document states the implemented value (`wallWorkExtraRecovery` = 1). Whether it
  should be 1 or 3 is a separate balance question, after repair costs, siege damage and survival are checked.
- **Upgrade preview "N forges in 10":** exact odds; decision A applies to previews and tooltips (§21.3).
- **Abandon:** keeps discovered recipe notes (what the code does).
- **Two factions share Frost weakness and Fire resistance:** differentiate their tactical identities; change their
  elemental affinities only if their other mechanics do not already lead to meaningfully different weapon choices.
  Direction only; nothing changed yet.

Still OPEN:
1. Wall work's extra repair, 1 or 3 (above).
2. Guild: charter band one cell over; drawbacks that cost little; militia nearly worthless at a guild wall; rescue
   almost always won; is the fight worth watching; is day 5 sharp enough.
3. Guild Patronage meets no target; visitor answers no bot prefers; the wager is nearly always won.
4. The travelling merchant can sell fine-metal work before day 6/11; a classic expert holds two Legendary by day 40.
5. A held classic siege can cost 24 integrity yet reads "held".
6. Save size past day ~350.
7. Outside the repo: application ID, launcher icon, audio, more art, the art-provenance wording, the game's name.

---

# 22. Keeping this document current

New features arrive regularly. For each one:

1. Decide it in its own ledger or plan (`docs/`), with numbered rulings and evidence.
2. When it ships, update the matching section here: the rule in words, PROPOSED numbers as a snapshot, the classic and
   guild difference, and a row in §2.2 (or §2.3 for owner decisions).
3. A change to anything marked LOCKED needs an explicit owner decision recorded in §2 first.
4. Keep counts in §16, the persistence matrix in §13.3 and the disclosure table in §15 true; tick
   `docs/GDD_CHECKLIST.md`.
5. Numbers drift with balance: when they do, `BalanceConfig` wins; refresh the snapshot line at the top of this file
   with the rules, content, balance and schema versions it describes.

---

# Appendix A — Engineering conventions

- Pure deterministic engine first, Android UI second; the UI never computes outcomes.
- Seeded streams per subsystem; nothing in presentation draws gameplay RNG.
- Stable IDs; exactly one authoritative weapon location.
- Event records are the source for the Gazette, the shop day and replays.
- Commands are typed, validated, phase-restricted; End Day is idempotent per command ID.
- Persist results atomically before any animation.
- Version gameplay (rules), content, balance and saves; write tested migrations.
- New gameplay numbers go into `BalanceConfig` groups, never inline; new content through `ContentCatalog.validate()`.
- Surgical edits; verifiable gates and green tests over progress narration.
- No ads, IAP or network; the player is a smith who never fights.

**Final product test:** a player should care that a particular named adventurer bought, or borrowed, their particular
weapon, and remember that weapon when it changes the town's fate. Every subsystem must support that.
