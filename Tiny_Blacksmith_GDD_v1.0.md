# TINY BLACKSMITH
## Complete Game Design Document — v1.0

**Date:** 2026-10-08  
**Status:** All nine design rounds complete; detailed balancing remains provisional  
**Genre:** Endless fantasy blacksmith simulator / indirect autobattler / roguelite  
**Platform:** Android; portrait  
**Technology:** Kotlin + Jetpack Compose, pure Kotlin simulation core  
**Monetization:** €1.99 premium; no ads, microtransactions, paid DLC, or subscriptions  
**Connectivity:** Fully offline at launch; preserve option for future cloud/achievements  
**Development method:** Simulation-first; custom detailed fantasy pixel art after mechanics are validated

> **Status conventions:** **LOCKED** means explicitly decided in design rounds 1–9. **PROPOSED** means a starting value or implementation design awaiting testing. **DEFERRED** means outside version 1. **ACCEPTANCE** means an objectively testable condition. Never silently replace a LOCKED decision with a proposed alternative.

---

# 1. Executive Summary

## 1.1 Elevator pitch

*Tiny Blacksmith* is a menu-driven fantasy game about forging weapons and selling them to autonomous adventurers in a living town. Players never move a character or command combat. They choose materials, experiment with combinations, manipulate forging risk, place weapons on shelves, set prices, and advance days deliberately. Adventurers with classes, ambitions, wealth, preferences and histories buy equipment and independently explore, fight, grow, retire, found guilds, mentor successors and die. Their successes shape the town's safety, economy and the eventual defense of the player's forge. Factions grow and attack on predictable siege days; after the forge falls, permanent expertise, famous weapons, and selected family histories persist into another run. There is no final wave or completion point.

**Core fantasy:** *You are not the warrior. You are the smith whose weapons make warriors legendary.*

## 1.2 Design pillars (LOCKED)

1. **Discovery first:** Meaningful material combinations, hidden signature recipes, experimental clues and surprising RNG effects drive curiosity.
2. **Indirect agency:** Player decisions are entirely blacksmith/shop actions; no direct hero orders, combat controls, or movable character.
3. **Living consequences:** Named autonomous heroes shop and act in the world; equipment fit changes their success and town survival.
4. **Short, repeatable, endless runs:** Typical early run 15–25 in-game days; world can continue indefinitely if it survives.
5. **Run diversity and strong permanent progression:** Random world conditions, temporary blessings, permanently unlocked expertise and substantial upgrades.
6. **Accessible premium experience:** Portrait, menus, close-up animated forge, instant craft/reveal, optional battle animations, offline, one payment.
7. **Relaxed economy, meaningful danger:** Gold is not a constant source of punishment; growing threats and equipment decisions are important.

## 1.3 Intended emotional moment

The player forges an unusual Stormglass sword. A particular ranger buys it because it suits her preferences. The newspaper reports her victory. Several days later she becomes a champion; when she dies, the sword vanishes. In a later era a descendant finds that named artifact. This story results from deterministic game systems, not an arbitrary script masquerading as simulation.

# 2. Design Decision Register — All Nine Rounds

| Round | Domain | LOCKED decision |
|---|---|---|
| 1 | Primary fun | Discovering combinations |
| 1 | Weapon use | Equip heroes; they fight independently |
| 1 | Run loop | Endless survival |
| 1 | Art | Detailed fantasy pixel art |
| 1 | Sessions | Brief casual play plus optional longer sessions |
| 2 | Recipes | Hybrid modular forging, hidden recipes and rare transformations |
| 2 | Champions | Three central town defenders, later refined to dynamic champions |
| 2 | Combat | Fully automatic |
| 2 | Variation | Weapon synergies plus temporary blessings |
| 2 | Defeat | Forge destruction |
| 3 | Market | Browsing customers plus commissions |
| 3 | Crafting randomness | Layered RNG |
| 3 | Resource sources | Suppliers, hero expeditions and events |
| 3 | Time | Player-controlled End Day |
| 3 | Persistence | Knowledge, upgrades and weapon legacy |
| 3 | Economy | Relaxed |
| 3 | Optional mechanics | Cursed/sentient weapons, named histories, random crises, loyalty/reputation, returning legendary weapons |
| 3 | Presentation | Close-up forge, menu UI, no moving character |
| 4 | Crafting modes | Quick and Advanced |
| 4 | Risk | Safe, Balanced and Reckless forging choices |
| 4 | Discovery | Mystery first; refined to hybrid clues/journal in Round 5 |
| 4 | Production cap | Energy rather than fixed item slots |
| 4 | Town champions | Dynamically selected strongest available three |
| 4 | Battles | Optional animation or instant reports |
| 5 | Discovery | Experiments plus occasional world clues |
| 5 | Rarity | Traditional tiers plus emergent historical legendary status |
| 5 | Threat | Living factions and threat system |
| 5 | Defense | Three champions plus town security support |
| 5 | Legacy | Balanced retention of knowledge, upgrades, legendary records |
| 5 | Run variance | Random world conditions and blessings |
| 5 | Energy | Overwork with trade-offs |
| 6 | Hero identity | Distinct class, 2–3 traits, ambition and preferences |
| 6 | Purchasing | Complex preferences and tastes |
| 6 | Sales control | Shop shelves, manual pricing, commissions |
| 6 | Hero growth | Generational: retirement, guilds, mentoring, successors |
| 6 | Weapon mechanics | Combat-focused synergies |
| 6 | Ownership | Living artifact cycle (recovery, theft, loss, inheritance, returns) |
| 6 | Curses/sentience | Predictable modifiers; no hidden arbitrary personalities |
| 6 | Story presentation | Daily fantasy newspaper |
| 7 | Screen | Portrait |
| 7 | Navigation | Single workshop interface and panels/overlays |
| 7 | Forge feel | Instant crafting + reveal animation |
| 7 | Risk implementation | Choose risk level before forging |
| 7 | Pricing | Player manually prices individual items |
| 7 | Animation | Animate significant battles only, optionally |
| 7 | Pace | Flexible daily session length |
| 7 | Discovery UX | Live affinity hints and detailed Experiment Journal |
| 8 | Price | €1.99 up-front premium |
| 8 | Extras | None; no additional payments |
| 8 | Launch library | 16 materials, 6 weapon families, 5 hero classes, 3 factions, 25 events |
| 8 | Art creation | Custom asset pipeline |
| 8 | Stack | Kotlin and Jetpack Compose |
| 8 | Online | Offline launch; allow connected features later |
| 8 | Typical early run | 15–25 days; game remains endless |
| 8 | Production | Simulation first |
| 9 | Daily energy | 10 energy |
| 9 | Overwork | Next-day exhaustion, not immediate forging penalty |
| 9 | Failure | Always creates a usable, potentially flawed weapon |
| 9 | Invasions | Predictable major invasion intervals |
| 9 | Recovery | Hero-driven (not gold-funded direct repair loop) |
| 9 | Generations | Hybrid: within-run mentorship/retirement + across-run lineages |
| 9 | Legacy | Strong permanent upgrades |
| 9 | Disclosure | Descriptive indicators, not explicit probabilities/formulas in UI |
| 9 | Death rewards | Baseline legacy rewards plus achievements/milestones |

**Conflict resolutions:** Three champions are *dynamic* rather than fixed roster slots. Mystery discovery is supported by hints/journal. A *living* faction system determines *which/how strong* attacks are, while major siege timing stays predictable. Short runs coexist with generational impact through cross-run lineages. Affixes and risk are represented descriptively to players even though exact calculations exist internally.

# 3. Player Loop, Game Phases and End Conditions

## 3.1 Run creation

Each run initializes a seed, content/simulation version, era identifier, randomized starting faction pressures/world modifiers, starter town with 100 forge integrity (**PROPOSED**), a small pool of procedurally named adventurers, available materials, modest capital, and legacy unlocks. No hard final level exists. Do not replace the game with a fixed-length 20-day campaign.

## 3.2 Day flow (LOCKED except ordering)

**Planning phase:** Show yesterday's newspaper and present shop, world forecast, available heroes, inventory, suppliers and commissions. Player crafts any affordable weapons, chooses risk, manually sets shelf prices, and optionally accepts commissions. Nothing advances without End Day.

**Resolution phase (PROPOSED fixed order):**
1. Validate command/idempotency; lock day planning and snapshot RNG state.
2. Resolve customer shelf visits, purchases and completed commissions.
3. Update equipment and hero finances.
4. Select autonomous hero expeditions, patrols, rests, guild actions and ambitions.
5. Resolve encounters using weapon synergy; apply kills, injuries, deaths, XP, loot and ownership changes.
6. Advance factions, environmental conditions and queued random events.
7. Resolve scheduled invasion if applicable; select three strongest available champions and apply town/forge results.
8. Apply hero-driven recovery, retirements, guild changes and fame.
9. Update weapon/hero history, permanent achievements and temporary blessing offers.
10. Emit event log and prioritized Gazette; persist the new world atomically.
11. Present optional significant-battle replays (read-only) and newspaper; begin next planning phase or defeat flow.

**End of run:** Forge integrity <= 0. Show summary and claim-once legacy points. Start next era with player knowledge/upgrades, selected artifact histories and notable lineages. Player never manually commands combat.

## 3.3 UX pacing

Quick crafting and End Day should allow a day to take 1–3 real minutes; extensive inventory/advanced recipe review may make a day significantly longer. No countdown clock, real-world energy replenishment, offline idle production or real-time opponent pressure. Important screens are dismissible and recoverable after app process death.

# 4. Materials, Forging and Discovery

## 4.1 Launch weapon families (LOCKED 6; PROPOSED identities)

| Family | Stats/play identity | Broad synergy |
|---|---|---|
| Sword | Balanced damage and guard | Guardian, Duelist, Battlemage |
| Axe | Heavy bursts, slower attacks | Guardian, Warden |
| Spear | Piercing, guard/reach | Guardian, Warden |
| Bow | Range, accuracy, crit | Ranger |
| Dagger | Speed, crit, status | Duelist, Ranger |
| Staff | Magic, element/affix potency | Battlemage, Warden |

## 4.2 Material catalog (PROPOSED names, LOCKED count 16)

- **Core materials (6):** Iron, Bronze, Silver, Obsidian, Starsteel, Moonsteel.
- **Augments (6):** Ember Resin, Frost Bloom, Stormglass, Grave Dust, Verdant Sap, Sun Ash.
- **Catalysts (4):** Binding Salt, Runestone Shard, Dragon Oil, Void Ink.

Data-driven entries include stable ID, material category/tier, price, resource rarity, faction affinities, weapon family compatibility, preferred catalysts, suggested flavor text, and discovered knowledge flags. All names and attributes are initial content, subject to content review; counts are locked.

## 4.3 Crafting modes

**Quick Forge (LOCKED):** Select family, core, augment and Safe/Balanced/Reckless risk, then tap Forge; result appears immediately behind a short noninteractive reveal. **PROPOSED** cost: 2 energy.

**Advanced Forge (LOCKED):** Add optional catalyst and unlocked specialized techniques/affix steering; still instant execution, not a tapping/timing minigame. **PROPOSED** cost: 4 energy. Advanced must offer *different decisions*, not always strictly better expected return.

**Daily energy (LOCKED):** 10 starting energy. **PROPOSED:** Up to 4 energy of voluntary overwork per day, with each overworked point reducing next day's capacity by one; no immediate risk modifier; reset naturally after debt is repaid. Save energy and debt as integers. Reject craft commands if insufficient eligible energy; no negative inventory.

**Forge result rule (LOCKED):** Every *valid* attempt produces a usable item. Low rolls produce flawed/weak equipment, not destroyed scraps; item flaws have rules. Materials/energy consumed exactly once. No retry by interrupting animation.

## 4.4 Layered RNG and item rolls

Use a game-owned versioned PRNG (e.g., SplitMix64 implementation; not platform-default implementation-dependent behavior) and deterministic independent gameplay substreams for crafting, hero selection, purchases, combat, factions, events and legacy. Avoid consuming random draws for animation/UI. Save seed plus generator state/counters; same seed, version and commands -> identical state/events.

**PROPOSED independent baseline chances:**

| Risk | Exceptional roll | Defect roll |
|---|---:|---:|
| Safe | 6% | 2% |
| Balanced | 15% | 8% |
| Reckless | 30% | 20% |

Quality and defect rolls can both succeed. Clamp final probabilities to reasonable bounds (e.g., 0–75%) when catalysts/mastery affect outcomes, not unlimited bonuses. Numbers above are internal; player sees descriptive labels only.

**PROPOSED quality formula:**

`quality = clamp(35 + 4*coreTier + 3*augmentTier + affinityBonus + masteryBonus + roll[-13,+13] + (exceptional?20:0) - (defect?12:0), 1,100)`

**PROPOSED power formula:**

`weaponPower = familyBase + 3*coreTier + floor(quality/5) + affixPower`

A defect also applies an independently meaningful negative gameplay affix. Rolls must be bounded, replayable, visible in telemetry and quantitatively tested.

**Hybrid rarity (LOCKED):** Common, Uncommon, Rare, Epic, Legendary generated tiers plus separate *earned historical fame*. **PROPOSED** generated quality ranges: Common 1–34; Uncommon 35–49; Rare 50–69; Epic 70–84; Legendary 85–100. Famous low-rarity weapons can earn titles through achievement without being arbitrarily converted into maximum-stat items.

**PROPOSED beneficial affixes (12):** Keen, Reinforced, Flaming, Frostbound, Stormcharged, Vampiric, Swift, Giant Slayer, Undead Bane, Guardian's, Lucky, Resonant.

**PROPOSED flaws/traits (6+):** Brittle, Heavy, Unstable, Cursed, Bloodbound, Sentient. Cursed/sentient modifiers have explicit, predictable behavior and known trade-offs; no secret self-willed events required at launch.

## 4.5 Signature recipes and transformations

**LOCKED:** Modular materials plus hidden recipes and unusual rare transformations. **PROPOSED:** 24 crafted signature definitions (4 per family), each with data-defined qualifying combinations, eligibility, named artwork/affixes and drop conditions. Baseline eligible transformation chance 15%, modified by mastery/risk and capped at 50% (tune). An exact recipe must be discoverable through experiments or world clues, not a purely opaque low-probability brute-force search.

## 4.6 Experiment Journal and knowledge states

**LOCKED:** Mystery-first hybrid discovery; live hint labels + persistent journal; clues from heroes, merchants and events.

Stages per interaction: `UNKNOWN -> OBSERVED -> UNDERSTOOD -> SIGNATURE_DISCOVERED` (exact progression may be per interaction or signature). Observing an element affinity records it; repeated meaningful experiments improve hint clarity; named transformation unlock is recorded permanently. Avoid awarding infinite discovery XP by repeating identical known crafts. Descriptive examples: *Unknown*, *Promising match*, *Excellent elemental affinity*. Do not show exact percentages.

**ACCEPTANCE:** A first-time player discovers a meaningful material relationship in their opening days; invalid combinations are explained; repeated identical commands do not farm permanent unlocks; known hints survive death; signature recipes can be found using in-game information.

# 5. Economy, Resource Supply, Shelves and Commissions

**LOCKED:** Relaxed economy with manual prices, automatic sales, complex buyer preferences, suppliers, hero loot and random material events.

**PROPOSED starting stock/economy:** 250 gold; enough initial ingredients for multiple Quick attempts and an Advanced attempt after unlock; 8 shelf positions; roughly 2–4 customers/day; basic common materials reliably stocked. Never permanently softlock player out of any forgeable item after a bad market day. Profit maximization is optional; experimenting remains affordable.

Player can list/unlist items, enter a price for each weapon and accept/refuse special commissions. Market visitors independently compare affordable displayed weapons to their current gear according to class, existing stats, elemental preference, traits, current wealth, planned activities, shop reputation/loyalty, material tastes and relative price.

**PROPOSED purchase algorithm:** For each visitor, filter affordable compatible listed items; calculate normalized utility = `equipmentImprovement + classFit + elementTaste + individualPreference + loyalty - pricePenalty`; choose highest acceptable item with slight seeded preference noise. One ordinary purchase per visitor/day. Store reason codes for why a purchase occurred or was rejected; UI shows *great fit*, *overpriced*, *not suited* instead of exact weights.

**PROPOSED commissions:** Specify buyer ID, weapon family, min quality, desirable effect, price/reward, deadline. A matching weapon delivered in time closes the commission and awards reputation/loyalty. Penalties for expiry are small. No player-controlled hero outfitting or combat commands.

**Reputation:** Grows through sales, commissions, renowned blades and hero success; affects visitors and willingness to pay. In-run reputation resets on death, modified by starting legacy perks. Individual loyalty influences repeat customers and story continuity.

# 6. Autonomous Adventurers, Champions and Generations

**LOCKED:** Heroes are autonomous named individuals with class, 2–3 traits, ambitions, wealth, equipment tastes, history, guild or family links; hero purchases and fights are simulated. There are **three strongest available champions**, selected dynamically, not a fixed player-controlled party.

**PROPOSED initial hero population:** 10–14, with 5 launch classes: Guardian, Ranger, Duelist, Battlemage and Warden. Traits include Brave, Cautious, Greedy, Ambitious, Loyal, Curious, Patient, etc. Each trait must affect an observable decision weight. Use deterministic generated names and portraits, IDs stable throughout an era.

**PROPOSED hero daily utility model:** Evaluate Rest/Recover, Shop, Expedition, Patrol/Defense, Guild/Mentor, Personal Ambition. Score viable activities using traits, wounds, money, quest availability, faction pressure and prior history. Weighted seeded choice with constraints. Injured heroes cannot perform invalid roles; dead/retired heroes cannot shop/fight.

**PROPOSED combat power:**

`effectiveHeroPower = (heroBasePower + weaponPower) * classFit * condition * enemyMatchup * traitModifier`

Keep individual factors bounded and test relative impact of weapons. A suitable weapon must statistically improve hero success. Important fights may create short precomputed animated replays; skipping them changes nothing.

**Generational hybrid (LOCKED):** During a run, heroes level, get injured, become famous, retire, join/found guilds, mentor newcomers or leave successors. Between runs a *selected small number of major lineages* and legendary weapon records survive into new eras; descendants can appear with narrative associations and modest starting traits. Avoid simulating real-time decades in a 15–25-day run; each new run is another era. Preserve important lineage events, not every NPC forever.

**ACCEPTANCE:** Traits measurably affect choices, customers with the same budget can choose different weapons for clear reasons, champions update automatically and are always alive/available, cross-run descendants refer to real stored ancestors, all actions are consistent with histories.

# 7. Living Weapon and Artifact System

**LOCKED:** Items have identity and lives beyond the shelf; ownership may transfer, be lost, returned, inherited, stolen by monsters, or recovered centuries later. Named legendary weapons can reappear across runs.

Weapon lifecycle: `FORGED -> IN_STORAGE / LISTED -> OWNED -> EQUIPPED -> TRANSFERRED / RECOVERED / LOST / DESTROYED / ARCHIVED`, with strict ownership invariants. Significant events append owner/weapon references and timestamps. Stats record important victories, owners, champion service, recovered artifacts and notable kills. Weapon titles derive from actual records. Persist a bounded Legend Board across runs; a famous blade returning in a later era may be damaged/dormant so it does not erase progression difficulty.

**PROPOSED on hero death:** Context-driven seeded recovery: surviving comrades, guild inheritance, merchant resale, monster seizure or permanent loss. Famous artifacts increase event eligibility but are not guaranteed to return. Weapon fame can grant a limited mechanical effect, with caps against runaway snowballing. Cursed/sentient traits do not secretly rewrite rules or force unchosen moral outcomes.

# 8. Town, Factions, Expeditions, Combat and Sieges

**LOCKED:** Three monster factions, an evolving threat model, three dynamic champions, layered defense, hero-driven restoration, and predictable siege intervals. The forge loses integrity when defenses fail; at zero, the run ends. No direct gold-funded guaranteed repair button.

**PROPOSED factions:** Ashclaw Raiders (swarms/physical), Hollowbound (undead/curses, specialized counters), Embermaw Brood (fire elites, frost counters). Each has pressure 0–100, strengths/weaknesses, encounter deck, elite/boss variants, adaptive threat influence.

**PROPOSED faction pressure step:**

`pressureNext = clamp(pressure + factionGrowth + eventEffect - heroSuppression, 0, 100)`

Scheduled **major sieges on days 5, 10, 15, ...** (*PROPOSED* interval; predictable timing is LOCKED). Player gets explicit warnings and descriptive danger labels. Living pressure decides attacking faction, troop composition and strength; ordinary expeditions and encounters vary between sieges. No surprise full siege outside announced cadence in v1.

**PROPOSED power values for calibration only:**

`townDefense = sum(threeActiveChampionDefensivePower) + militiaSupport`

`raidPower = (32 + 5*day + 0.65*attackingFactionPressure) * siegeModifier * worldModifier`

`forgeDamage = round(max(0, 12 + 25*(raidPower/max(1,townDefense)-1)))`

These are tunable scaffolds, *not demonstrated balanced values*. Battle resolver must produce casualties, injuries, loot, hero/faction effects, weapon achievements and forge outcomes from a single authoritative deterministic event series. No animation recalculates combat.

**Hero-driven restoration (LOCKED):** Successful expeditions, patrols and defenders grant security/recovery. Town can benefit from existing militia/infrastructure, but do not design a gold-spend mechanic that overrides failure to supply decent weapons. **PROPOSED** starting forge integrity 100; recovery and integrity changes bounded each day. Siege power grows indefinitely, with capped/controlled multipliers to avoid overflow and instantaneous untelegraphed failure.

**Short-run tension:** Typical first-era run ends between days 15–25; with permanent upgrades the player should noticeably improve and may survive vastly longer. Never forcibly kill a run at day 25. Progression cannot guarantee immortality.

# 9. Roguelite Variation and Permanent Legacy

**LOCKED:** Dynamic worlds + temporary blessings; strong permanent upgrades; baseline rewards plus bonus rewards; retained discoveries/legendary histories and generational traces. Gold, run inventory, temporary heroes, current faction state, temporary blessings and within-run reputation do not persist (except starting reputation perks).

**PROPOSED blessings:** Select one of three after a survived major siege; Forgefire, Tireless Hands, Merchant's Favor, Runic Insight, Stalwart Town, Hunter's Edge, Lucky Alloy, Guild Patronage. Temporary, bounded, and seed-dependent; different combinations encourage different forging strategies.

**PROPOSED Legacy Points:**

`legacyPoints = 5 + floor(daysSurvived/5) + discoveryBonus + milestoneBonus + achievementBonus`

Claim once per destroyed run. Early upgrade tiers ~8/20/45 points (subject to testing). Strong upgrade categories: starting energy, forge/tool mastery, material efficiency, better starting resources, extra starting gold, enhanced quality/recipe odds, catalog access, starting shop reputation, legacy artifact opportunities. Intentionally noticeable strength growth, with bounded effects and rising threat pressure. Avoid infinite permanent stat compounding that makes the simulation meaningless.

**Persistence matrix:**

| State | Across runs? |
|---|---|
| Experiment Journal / material interactions | Yes |
| Signature recipes | Yes |
| Permanent mastery / purchased upgrades | Yes |
| Legend Board, selected artifact stories | Yes |
| Selected lineage anchors | Yes |
| Unspent legacy currency | Yes |
| Temporary blessings | No |
| Gold, ordinary resources, shop stock | No |
| Current heroes, injuries, town state | No |
| Faction pressures, current day | No |
| Current run reputation | No (starting bonus may be permanent) |

# 10. Content Library and Event Specifications

**LOCKED standard release:** 16 materials, 6 weapon families, 5 hero classes, 3 factions, **25 scripted systemic world events**. **PROPOSED additional content:** 24 hidden named signature recipes, ~12 beneficial affixes, >=6 flaws/odd traits, 8 blessings, several permanent-upgrade tracks, modular portraits/name pools and limited elite variants.

**PROPOSED 25 events** (each must have eligibility, weight, effects, story template, repetition limits and test):

1. Traveling Ore Merchant (material supply)
2. Trade Caravan Delayed (shortage)
3. Merchant Festival (demand)
4. Abandoned Mine Rediscovered (supply)
5. Noble Commission (valuable request)
6. New Adventurers Arrive (population)
7. Veteran Returns (strong customer)
8. Guild Founded (mentoring)
9. Champion Retirement (roster shift)
10. Heroic Inheritance (weapon transfer)
11. Raider Encampment (Ashclaw pressure)
12. Restless Graves (Hollowbound pressure)
13. Volcanic Tremors (Embermaw pressure)
14. Successful Patrol (pressure reduction)
15. Border Ambush (expedition challenge)
16. Ancient Smithing Notes (recipe clue)
17. Mysterious Alloy (material clue)
18. Forgotten Shrine (catalyst)
19. Wandering Master Smith (knowledge)
20. Strange Weapon Fragment (signature clue)
21. A Famous Blade Returns (legacy recovery)
22. Descendant of a Champion (lineage)
23. Forgotten Guild Banner (era history)
24. The Collector Arrives (artifact market)
25. Ballad of the Blacksmith (reputation/recognition)

Maintain data-driven, predictable event execution; do not use arbitrary runtime generative AI, web calls, or paid APIs to create gameplay. Events that require historical artifacts/lineages must be ineligible if none exist; provide alternate events to maintain variety.

# 11. The Emberfall Gazette and Battle Replays

**LOCKED:** Daily newspaper is the principal view into the autonomous world; animations reserved for important encounters and completely optional.

Gazette headline priority: town siege result, named hero deaths, famous weapon developments, expedition milestones, notable sales, faction threats, special events. **PROPOSED:** 3–5 priority headlines/day with expanded event archive. Text generated from actual event record templates; the paper must never claim actions that did not occur. Example:

> **EMBERFALL GAZETTE — DAY 18**  
> Mira Ashwood routed Hollowbound scouts using Stormwhisper.  
> Moonsteel arrives from the Northern Pass.  
> Aldric retires and opens a guild hall.  
> Ashclaw raiders gather for the Day 20 invasion.

Combat animation input = an already-calculated `CombatReplay` DTO; renderer produces no random simulation effects. Skipping or interrupting/reopening replay is semantically free.

# 12. Portrait Workshop UX, Navigation and Accessibility

**LOCKED:** One portrait workshop, detailed close-up forge, menu-driven with panels/drawers/overlays, no movable character; Quick Forge instant result animation; manual market pricing; descriptive probabilities.

**PROPOSED top strip:** Day, gold, energy/debt, forge integrity and next invasion. **Center:** Forge environment (furnace/anvil/weapons) with selection/reveal area. **Primary actions:** Quick Forge, Advanced Forge, Materials, Risk, Forge. **Panels:** Market (shelves/pricing/commissions), Town (heroes/champions/threat), Journal (affinities/signatures), Gazette (daily report/archive), Legacy (records/upgrades), Settings. **Bottom:** End Day with contextual warning if unclaimed choices.

**Forge flow:** Choose mode -> family -> core -> augment -> optional catalyst -> see discovered affinity descriptions -> select risk -> review cost -> Forge -> atomic resolution -> short sprite reveal -> detailed item card with affixes/flaws -> List/Store/Forge Again. It must be possible to forge quickly with minimal taps when using familiar combinations.

**Market flow:** Enter shop panel -> inspect items -> select shelf slot -> type price -> list -> review customers/commissions -> return to forge. No manual hero equip actions, bidding controls or negotiation minigames.

**Town flow:** Three current champions prominently shown with equipment and condition; reveal larger population summaries; invasion ETA and pressure descriptors; major battles optionally playable as replays after day resolution.

**Accessibility:** Large tap targets, scalable text, contrast behind art, distinguish rarity with icon/text not color alone, reduced-motion option, haptics/audio toggles, readable numbers, no time-pressure actions. Restore state after rotation/process loss (orientation remains portrait). No essential outcome depends on watching an animation.

# 13. Kotlin + Compose Architecture

## 13.1 Rule: Simulation is platform-independent

**LOCKED tech:** Native Kotlin + Compose. **PROPOSED module boundaries:**

```text
:app                 navigation, DI/bootstrap, main Activity
:core:simulation     immutable game state, day resolution, factions, AI, combat
:core:crafting       recipes, item generation, material interactions, RNG
:core:content        versioned data definitions and validation
:core:legacy         knowledge, artifacts, lineages, permanent upgrades
:core:persistence    Room entities, migrations, repositories, atomic saves
:feature:workshop    Compose forge and flow
:feature:market      sales, shelves, commissions
:feature:town        champions, danger, hero info
:feature:journal     journal, Gazette, artifact history
:feature:legacy      summary, upgrades, past eras
:ui:pixelart         sprite atlas loading, animation, Canvas rendering
```

For a small repository, begin with simpler Gradle setup if necessary; keep the package boundaries. The simulation and crafting core must build/test on plain JVM without Android emulator, Activity or Compose dependencies.

## 13.2 Commands and authoritative state

```kotlin
newRun(legacy: LegacyProfile, seed: Long, rulesVersion: Int): GameState
forge(state: GameState, command: ForgeCommand): CommandResult
toggleShelf(state: GameState, weaponId: WeaponId, listed: Boolean): CommandResult
setPrice(state: GameState, weaponId: WeaponId, price: Int): CommandResult
acceptCommission(state: GameState, commissionId: CommissionId): CommandResult
endDay(state: GameState, commandId: CommandId): DayResolution
closeRun(state: GameState): RunEndResult
```

All commands validate state and phase, return typed results/errors, and emit domain events. **PROPOSED states:** `PLANNING -> RESOLVING -> REPORTING -> PLANNING` or `ENDED`; choose how persistent transactions handle transient phases. `EndDay` and reward claims must be idempotent. UI is an observer; it never separately calculates game outcomes.

## 13.3 Persistence schemas (logical models)

- **GameRun:** id, seed, generator counters, content/sim version, era, day, phase, gold, energy, overwork debt, integrity, reputation, world modifiers, blessing IDs.
- **Weapon:** stable ID, recipe/material IDs, generation rolls, quality/rarity/affixes/flaws, owner or shelf status, price, fame, kills, title, history references.
- **Hero:** stable ID, name, class/level/XP, gold, health/wounds, traits, tastes, ambition, loyalty, activity, equipment, guild/lineage links, fate.
- **Town:** integrity, militia support, champion IDs, next siege day, status flags.
- **Faction:** ID, pressure, modifiers, encounters, resistance profile.
- **Commission:** request, customer ID, reward, expiration, status.
- **LegacyProfile:** currency, upgrades, known interactions, signature recipes, artifact records and lineage anchors.
- **EventRecord:** stable event ID, day, type, real subject IDs, typed data payload, news priority.
- **ContentDefinition:** IDs, schema version, balancing/config values, asset references.

Room for authoritative saves/history, DataStore for settings. Versioned content assets (JSON or Kotlin definitions), validated with build tests. Store IDs rather than embedding multiple divergent copies of static definitions. Avoid storing unbounded full replay logs in history; compact ordinary events and retain rare milestones.

**Transactional commit sequence:** Validate command -> compute new state/events in pure Kotlin -> assert invariants -> atomic transaction to persist run state + event records + generator state -> acknowledge -> present Gazette/replay. If a reveal is interrupted, recover committed weapon. If End Day is retried, no extra simulation happens.

## 13.4 Future connectivity

Base game functions completely offline. Use modular interfaces to allow cloud backup, achievements and optional leaderboards in later versions. Do not implement accounts, network services, live AI or telemetry as requirements for launch. No backend or API key required.

# 14. Custom Pixel-Art Pipeline

**LOCKED:** Detailed custom fantasy pixel art, not generic asset pack. **PROPOSED production standards:** coherent pixel grid, consistent light source and silhouette scale, nearest-neighbor texture scaling, limited palette families, visual clarity in portrait layout.

Artwork set: main forge background, furnace states, embers, anvil/tool rack, shelves, six weapon silhouettes with material/element overlays, signature weapon variants, modular class portraits, faction silhouettes and elites, newspaper/journal panels, slots and badges, animations for important sieges and hero milestones. Store each asset with stable ID, dimensions, frames/animation duration, anchoring and layering metadata; pack into atlases when warranted. Compose manages UI semantics; Canvas paints decorative pixel art; never draw essential tiny text into immutable bitmaps.

**Production order:** placeholder UI -> simulation-tested vertical slice -> one polished forge screen and limited sprites -> full custom art production. No thousands of bespoke item assets required; procedural combinations of curated layers may create variety. Before using external art or sound, verify licensing and attribution requirements.

# 15. Testing, Balance and Reliability

## 15.1 Determinism and invariants (ACCEPTANCE)

- Same content version, seed, initial state and commands yield byte/semantic-equal state + ordered event results.
- Renders, animations, skipped battles or additional UI visits do not consume gameplay RNG.
- Unique weapons have exactly one valid owner/location; no dead customer shopping or dead champion defending.
- Inventory/gold/energy obey bounds; no duplicate craft/sale/legacy rewards after retry or interruption.
- Siege days are forecastable and correct; newspaper entries reflect true events.
- Legacy knowledge persists, run-only state resets; descendants reference valid stored lineages.
- All configured materials/recipes/events/affixes refer to valid content IDs and are reachable where intended.
- Atomic save/restore and schema migration do not mutate histories or RNG state.

## 15.2 Headless balance harness

Run >=10,000 seeds per balance review, with policies: random legal actions, safe forging, reckless forging, cheap shelves, expensive shelves, synergy optimization, overworking, no overworking, new legacy account, advanced legacy account. Measure survival percentiles, repeated softlocks, material/gold abundance, sale rate, hero deaths/retirements, weapon rarity distribution, signature discovery, artifact recovery, faction win proportions and upgrades' relative impact.

**PROPOSED outcome goals:** New players' median early-run duration 15–25 days; viable paths beyond day 25; upgrade-rich runs materially longer; economy sustains regular experiments; varying builds materially change survival. If actual distributions differ, tune data config rather than hardcoded UI logic.

## 15.3 Engineering checks

Plain JVM unit/property tests for simulation and RNG; Room migration/instrumented tests for persistence; Compose screenshot/accessibility tests for UI; device validation under font scaling, smaller screens and low-memory interruptions; soak simulations over many thousands of in-game days without integer overflow or unbounded memory. **PROPOSED** nominal day sim p95 <200 ms on representative mid-range Android hardware *excluding persistence/rendering*; 60 FPS animation target, but accessibility/readability over effects.

# 16. Vertical Slice, MVP and Launch Gates

## 16.1 Vertical slice (first playable technical milestone)

Build **a small complete loop**, not the full content library:
- 3 weapon families, 6 materials, 2 hero classes, 1 monster faction.
- Quick Forge, RNG quality and flaws, energy, risk.
- Shelf listing and manual pricing, autonomous hero visits/purchases.
- Expedition resolution, champion selection, scheduled siege, forge integrity and death.
- Gazette, save/load, baseline legacy upgrades.
- Minimal Compose workshop and placeholders.

**End-to-end demonstration (ACCEPTANCE):** Fresh run -> craft sword -> list/price sword -> End Day -> autonomous hero buys and equips -> next day hero fights -> weapon influences outcome -> relevant Gazette story -> siege(s) -> forge destroyed -> baseline + milestone legacy reward -> new run shows earned upgrade. No direct combat controls at any step. Entire sequence is deterministic and resumable.

## 16.2 Premium v1 release (LOCKED content scope)

Expand vertical slice to all 6 families, 16 materials, 5 classes, 3 factions, 25 events; Advanced Forge; hidden signature recipes; real journal/world hints; full preferences/commissions; guild/retirement/lineage mechanics; living artifacts; full legacy choices; animated significant combat; custom sprite UI; onboarding/accessibility; save migration/reliability; automated balance tests. The vertical slice is an intermediate delivery, not a way to drop previously locked release mechanics.

**DEFERRED:** Multiplayer, PvP, manual hero equipment/combat, walking around town, real-time idle production, live-generated dialogue, online market, accounts/cloud saves, achievements/leaderboards, microtransactions, ads, paid DLC, cosmetic shop.

# 17. Phased Development Roadmap

| Phase | Focus | Measurable exit gate |
|---|---|---|
| P0 | Create project, content schema, deterministic seed/state model, test setup | Clean builds/tests; stable seeded replay |
| P1 | Crafting, energy/overwork, rarity, recipes, inventory | Thousands of crafting tests pass; output distribution sensible |
| P2 | Autonomous heroes, preferences, shelves, pricing, commissions | Automated purchases/equipment are correct and reasoned |
| P3 | Factions, encounters, sieges, town health | Thousands of full headless runs reach repeatable defeat states |
| P4 | Blessings, legacy, guilds, lineages, histories | Cross-run persistence and rewards correct |
| P5 | Compose vertical slice in portrait | Complete player loop on emulator/device |
| P6 | Launch content + custom art/audio + major replays | All locked launch systems represented in UI |
| P7 | Balance and reliability, onboarding, UI/device accessibility | Tests, simulation targets, review gates met |
| P8 | Google Play premium launch | Accurate listing, legal/privacy assets, production AAB |

Do not hardcode unknown gameplay numbers scattered throughout code. Maintain a versioned `BalanceConfig` and a locked-versus-proposed decision register. If design questions remain, prefer reversible defaults, note them and continue; only request new product decisions when materially necessary.

# 18. Release Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Crafting devolves into random button presses | Stable material affinities, discoveries, conditional recipes and risk choices |
| RNG dominates skill | Data-driven base stats, bounded rolls and repeatable experiments |
| Hero world feels fake | Every news item and purchase references genuine simulation events |
| Pricing feels arbitrary | Explain demand and mismatch descriptively, show customer motives |
| Generations do not fit 20-day runs | Mentor/retirement intra-run; generational descendants inter-run |
| Big legacy bonuses trivialize run | Threat grows indefinitely; bounded item/progression multipliers |
| Economy is too punishing | Reliable basic resources, no permanent softlock, relaxed pricing |
| Launch scope too ambitious | Test core in headless harness and gate art/content on vertical slice |
| Complex graphics swamp usability | Pixel art behind readable Compose text and accessible controls |
| History storage explodes | Compact mundane logs; retain rare milestones and lineage anchors |
| Save duplication/retroactive RNG | Atomic commands, idempotence, versioned seeded streams |

# 19. Final Release Acceptance Checklist

- [ ] Player can complete the entire forge -> sell -> hero uses -> world changes -> forge falls -> legacy loop.
- [ ] Game remains entirely menu-driven, portrait, offline, and combat-free from the player's perspective.
- [ ] All locked nine-round design decisions are met; deviations explicitly documented/approved.
- [ ] Exactly the intended launch content counts are present and data references validate.
- [ ] Materials, risk and synergies lead to noticeably different play outcomes.
- [ ] Named heroes, champion succession, guild/lineage and artifact stories work and survive appropriately.
- [ ] Faction invasions are predictable in timing and different in threat composition.
- [ ] Early median survival approximates 15–25 days after evidence-based balance work; no hard cap.
- [ ] Strong permanent upgrades improve the experience without removing danger.
- [ ] All critical invariants, deterministic simulation tests, persistence/restore and migration tests pass.
- [ ] Accessibility, tutorial, sound/motion controls and performance on target Android devices pass review.
- [ ] Store listing accurately describes the gameplay and one-time €1.99 purchase; no ads, IAP or subscriptions.

# Appendix A — Proposed Values That Are NOT Locked

Exact starting gold/stock/shelf size; core/augment names; initial hero count; Quick/Advanced energy costs; max overwork; affix catalog/details; quality formula/ranges; risk probabilities; signature recipe count/chance; hero purchase utility weights; commission rewards; faction/hero combat numbers; siege days interval length; town integrity and repair coefficients; temporary blessing catalog; upgrade costs and permanent multiplier limits; art resolution and sprite geometry; performance targets. Keep configurable and tune after playtests.

# Appendix B — Recommended Engineering Conventions

- Clean-room repository design: pure deterministic engine first, Android UI second.
- Seeded gameplay streams per subsystem; UI never consumes simulation randomness.
- Stable entity IDs; exactly one authoritative item owner/location.
- Event records are the source of truth for Gazette and replays.
- Commands are typed, validated, phase-restricted, idempotent where needed.
- Persist end-of-day results and events atomically before replaying any animation.
- Version all gameplay/content data and write tested migrations.
- Replace generic progress narration with verifiable phase exits and green tests.
- Prefer small surgical code edits to whole-file rewrites after initial creation.
- Respect no ads/IAP, fully offline launch and the player's noncombat blacksmith identity.

**Final product test:** A player should care that a particular named adventurer bought their particular sword—and remember that weapon when it changes the town's fate. Every major subsystem must support that outcome.
