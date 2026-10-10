# Tiny Blacksmith — thorough repository and gameplay review

**Reviewed:** 9 October 2026. **Repository:** [Morfildor/BlacksmithInc](https://github.com/Morfildor/BlacksmithInc). **Snapshot:** [`b39ad76a07c3c48059ad81e50deacef981d7f8f1`](https://github.com/Morfildor/BlacksmithInc/commit/b39ad76a07c3c48059ad81e50deacef981d7f8f1), `main`, app 0.5.0, balance configuration 4, rules version 1.

**User direction:** Review the GDD checklist and code thoroughly. The current game feels uninteresting, its boxy UI feels amateur, and the player cannot readily understand what is happening. End Day should deliver a shop experience. The broad visual redesign remains a later milestone.

## 1. Conclusion and next milestone

The repository contains a substantial, coherent simulation and a playable Android shell. Forging, purchasing, combat, sieges, legacy, discoveries and much of the launch content are genuinely implemented. The main product weakness is that the game presents a large amount of simulation information without making the player experience the business or understand the consequences of their decisions.

Today the player configures a weapon, lists it, advances time, and receives a paper full of outcomes. The customer encounter—the payoff for making and pricing the weapon—never becomes a scene. Heroes and weapons have histories in the engine, but the player mainly encounters them as names inside lists. That makes a functioning simulation feel distant and repetitive.

**The highest-value next milestone is a complete, readable day: prepare the shelf, experience customers visiting the shop, see a few consequences of their equipment, and receive a clear reason to prepare tomorrow.** Establish this flow before commissioning the full visual redesign. A prettier set of the current panels would retain the same communication problem.

This recommendation preserves the GDD's autonomous heroes, manual prices, instant forging, player-controlled time and offline operation. Shopping can be a presentation of the already-committed day resolution, like the existing combat replay. It does not require real-time shop simulation, live-generated dialogue, manual equipment or combat controls.

### What deserves credit

- Pure JVM core separated from Android; typed commands; immutable command boundaries; separate seeded RNG streams.
- Run and legacy written together through a Room transaction before accepted gameplay is shown.
- One authoritative weapon location rather than independent inventory/equipment ownership lists.
- Real event records drive news; predictable siege cadence; no gameplay RNG in decorative sprite rendering.
- Launch catalog, signature recipes, techniques, affix effects, trade-ins, wear, fame, generations, legacy and world events have meaningful source implementations.
- Extensive core test source and documented balance work. These are useful foundations to retain.

### What is not established yet

Engaging play, a clearly understood shop loop, safe Android operation under concurrent input and storage failure, current 10,000-seed v4 balance, full device accessibility/performance, and production readiness remain unproven or incomplete.

## 2. Evidence and verification limits

I cloned the repository, verified the exact HEAD, read the attached GDD and the repository GDD, and compared them: they are identical. I inspected the checklist, progress/decision/phase documents, production engine and Android interaction paths, test structure, saved fixture, build configuration and asset integration.

The snapshot has **27 core production Kotlin files / 4,607 lines**, **19 Android production Kotlin files / 2,671 lines**, **144 core `@Test` annotations**, one Android-module local template test, and **six instrumented tests**. These are source counts, not coverage percentages. The checklist has **61 checked entries and 29 open entries**. A checked-entry percentage would be a misleading completion metric because the entries differ greatly in scope.

The remote advanced during the review. I fetched and inspected both new commits, including all nine changed files, and updated this report to the final snapshot above. They add terminal-weapon pruning, reopening an unread report after process death, and an instrumented End Day timing assertion. The feature checklist has not yet caught up: it still lists report reopening as open and still reports 140 JVM / five instrumented tests.

**Independent checks completed:** repository/GDD comparison; source-level control-flow tracing; test/checklist counts; JSON parsing of the checked-in save; comparison of its report with its archived day; analytic examination of capped customer selection.

**Execution attempted:** `./gradlew :core:test --no-daemon`. It failed before compilation because this environment could not download the Gradle 9.5 distribution (`Network is unreachable`). A separate HTTP attempt followed the distribution redirect and timed out. No current core test, APK build, simulator sweep or Android instrumentation result was independently produced here. The repository's recorded 140/140 passes and device passes are prior developer evidence, clearly distinguished below.

The GitHub Actions runs endpoint returned zero workflow runs at review time, and the snapshot has no workflow files. There is no visible automated CI result corroborating this commit. No emulator or current running app was inspected; visual assessments below are grounded in Compose structure and the user's playtest feedback, not a claimed screenshot review.

No source code, repository checklist or release metadata was edited, and no commit or remote branch was created or changed. The local review checkout was advanced to the latest remote commit. This report is a separate deliverable.

## 3. Why the existing game feels hard to follow

| Current behavior | Effect on the player | Needed behavior |
|---|---|---|
| End Day calculates shop visits, adventures and siege outcomes in one command, then opens the Gazette | The sale is information rather than a satisfying encounter | Let the player watch customers browse and transact before showing the world aftermath |
| Home is several text surfaces describing today, threats, stock, requests, heroes, yesterday and materials | The player must assemble priorities from many equally styled blocks | Lead with one immediate opportunity and one reason it matters |
| Market puts shelves and potentially long storage lists before yesterday's customers, commissions and supplies | Business feedback is buried beneath stock management | Give customers and outstanding requests prominent space |
| Town lists traits, health, gold, fame and ambitions, but omits displayed elemental taste and detailed history | A named NPC is difficult to remember or intentionally supply | Offer a short identity, current need, equipment and notable history |
| Forge asks for a recipe without showing how it serves current demand | Experimentation often becomes changing ingredients without a purpose | Connect a recipe to available customers, requests, threats or a journal clue |
| Affix descriptions are available in the reveal, but inventory rows mainly retain affix names | A player who dismissed the reveal loses easy access to what properties do | Make item details and the weapon's story reopenable |
| Newspaper groups actual records but remains a substantial reading task | Causality and the player's contribution compete with routine NPC events | Lead with one or two meaningful consequences; leave routine records in the archive |

The existing event and item models already support much of this. The missing element is the path through that information and the data necessary to explain each decision precisely.

## 4. Proposed End Day shop experience

### 4.1 A complete day

| Step | Player-facing experience | Authoritative source |
|---|---|---|
| Prepare | Forge, inspect the result, set individual prices, list stock, accept a request | Existing planning commands |
| Close preparation | End Day commits the deterministic simulation and save | Existing engine and Room boundary |
| Shop visitors | A customer appears at the counter; portrait, class, current equipment and a short relevant need are visible | Persisted visit-time customer snapshot |
| Browse and decide | The customer considers a particular blade, buys or declines, with a clear factual reason | Stored evaluation explanation and selected/considered item snapshots |
| Transact | Show price, cash received, trade-in, item leaving the shelf and a regular's recognition | Recorded purchase/commission/collector transaction |
| World aftermath | Show up to a few meaningful hero/weapon/town changes, with optional significant replay | Already-calculated events and replays |
| Tomorrow | Show remaining shelf stock, one actionable need and the next known threat or request | Committed new-day state |
| Gazette | Keep the complete newspaper available as a readable record | Unified completed-day edition |

Present the actual visitors; do not manufacture a purchase when the simulation produced none. Quiet days should be brief and explicit. A customer who cannot afford a blade is still useful feedback when the encounter makes the reason and the relevant item clear.

For the first prototype, use existing portraits, weapon sprites and a simple counter scene. One focused encounter at a time is more important than elaborate walking animations. Aim initially for roughly 10–25 seconds of optional presentation on an ordinary day; this is a proposed playtest value. Support tap-to-advance, a speed choice, skip-all and reduced motion. Watching it must never be necessary to claim money, preserve the save or advance correctly.

### 4.2 Example of the intended payoff

Illustrative wording, only when supported by the recorded facts:

> Mira, a Ranger, studies your Stormglass bow. “This suits my fighting style.” She buys it for 108 gold and leaves her old bow as part payment. Later, she wins an expedition while carrying that same bow and brings an ingredient back. Tomorrow she is a familiar customer rather than another name in a report.

The satisfaction comes from the continuity of **your item → recognizable buyer → visible transaction → actual consequence**. Dialogue should use authored templates filled from genuine facts. Do not invent a mission, taste or decisive combat contribution that the engine did not record.

### 4.3 Data needed for a reliable presentation

`MarketVisit` currently stores only hero ID, hero name, purchased weapon ID and a reason string ([model], line 176). It does not preserve the candidate considered, price, old gear, buyer budget, class/taste snapshot or the factors explaining the choice.

Add a compact, versioned shop-presentation DTO to the saved day result. Suggested fields: visit sequence; hero identity/portrait key; relevant trait/taste facts; current-equipment summary; considered and selected weapon snapshots; listed price; trade-in credit; cash paid; sale bonus; typed explanation factors; outcome; associated transaction/event IDs. Record commission collection and collector purchases as distinct transaction kinds.

Snapshots matter: by the time the shopping presentation opens, a customer may have died, retired or changed gear later that same day. Rendering the encounter from the final state can show the wrong weapon or condition. Stable IDs should link to details, while snapshots preserve what was true at the counter.

Persist a separate presentation acknowledgement/cursor. After process death, resume or offer to skip the committed sequence without recalculating the day. Disable planning mutations while presenting it. Prices remain the prices chosen during preparation; changing a committed transaction halfway through the replay would require a different gameplay design.

### 4.4 Minimum acceptance gate

1. A fresh player can describe who bought their first weapon and why after two or three days.
2. The encounter clearly distinguishes cash, trade-in credit and any bonus.
3. At least one genuine consequence links back to a customer and weapon the player recognizes.
4. The player can identify one sensible next action without opening every panel.
5. Skip, reduced motion, restart and interruption preserve identical gameplay state and RNG.
6. A day with no purchases still gives an honest, useful outcome and a clear way back to preparation.

Use local playtests and observed comprehension for this gate. More survival simulations cannot establish that a person understands or enjoys the experience.

## 5. High-priority correctness and reliability findings

Severity here refers to practical player impact. **P1** means fix before wider playtesting of the new loop or before release; **P2** means an important follow-up. Findings distinguish confirmed stored evidence, confirmed source behavior and risks that still need a runtime reproduction.

### F01 — P1: legacy purchases and starting the next era can overwrite one another

**Source-confirmed unsafe concurrency; runtime frequency not measured.** `buyUpgrade` captures `UiState.RunEnded` before launching a coroutine, computes from `current.legacy`, then saves that entire result. Unlike `dispatch`, reward/upgrade/new-run methods have no shared busy guard or command serialization. The upgrade and Begin Era controls remain available while saves run ([VM], 133–167; [run-end]).

Two different upgrades tapped before the first save completes can both calculate from the same profile. Their results each contain only their own purchase; whichever writes last loses the other update. An in-flight upgrade can also call `saveAtomically(null, ...)` after a new run has been created, clearing that newly saved run. Room makes each pair of writes atomic; it does not make these separate read/compute/write operations mutually exclusive.

**Fix:** serialize every authoritative operation through one ViewModel/repository command boundary; take the latest state inside that boundary; provide busy state for title and run-end operations; prevent stale operations from publishing across run transitions. Keep legacy claim idempotence. Do not describe this as an already-proven double-reward exploit—the pure claim function guards repeat claims.

**Regression:** hold an injected repository save behind a barrier; request two distinct upgrades and upgrade→Begin Era; release writes in both orders; verify all legitimate purchases and the new run survive.

### F02 — P1: storage/decode failures have no recoverable UI path

**Confirmed source gap.** ViewModel initialization, dispatch saves, reward saves and new-run saves have no exception handling ([VM], 65–84, 109–124, 133–160). Decode failures can occur for corrupt or newer-schema saves; write failures can occur independently of gameplay correctness. The app has no typed load/save failure state or safe retry flow. A failed accepted-command save is not acknowledged, but the exception is unhandled and the busy state is not restored.

**Fix:** add explicit loading/recovery and save-failure states, keep the last committed gameplay state, preserve the existing save bytes, and offer retry. Reset operation state in a controlled failure path. Never silently replace a failed save with a fresh game.

**Regression:** decode error, unsupported schema, save exception and cancellation around commit; verify no acknowledged unsaved result and no loss of the previous committed state.

### F03 — P1: the immediate day report omits the player's preparation events

**Confirmed in both source and checked-in save.** Each `ResolutionContext` begins with existing events in `events` and an empty `newEvents`. Forge/list/hone/tool commands have already stored their events before End Day. End Day builds `DayResolution.events` from `ctx.newEvents`, so those preparation events do not enter the dialog ([engine], 275–301; [context]). The archive reads `state.eventsForDay(day)` and consequently has a different edition ([panels], `GazettePanel`).

In `v1_forced_seed4242_day61.json`, Day 60 has **20 archived events but only 15 report events**. The missing five are all `WEAPON_FORGED`. This was independently verified by parsing the fixture, not inferred from a test name.

**Fix:** build the completed-day edition from all authoritative events for the resolved day, before compaction. Distinguish “events emitted by this command” from “the whole day's report” if both are needed. Use the same completed-day source for dialog, Home and archive.

**Regression:** forge, list, hone and buy a tool, then End Day; compare report and archive accounting and ensure preparation events appear once.

### F04 — P1: a commission's displayed quality requirement differs from its actual rule

**Confirmed source behavior.** The UI converts `minQuality` to a broad label via `Labels.quality`, while fulfillment checks the exact integer. A request with minimum quality 59 displays “Fine”; a quality-52 weapon also displays “fine” but will not complete it ([market UI], 78–89; [market], 161–175, 194; [labels]).

This directly creates the impression that the rules are opaque or malfunctioning.

**Fix:** either define commissions using the same visible quality-band boundaries or expose an exact inspectable requirement alongside the band. Show a read-only eligibility status for each candidate and a precise reason when it fails. The GDD restriction is on hidden probability/formula disclosure; it does not require concealing whether an objective commission condition has been met.

**Regression:** adjacent qualities inside one visible band; family/element mismatches; exact deadline-day fulfillment.

### F05 — P1 product / P2 engine: capped customer selection is biased by hero ID order

**Confirmed selection structure; actual balance impact needs measurement.** `aliveHeroes()` sorts string IDs and `resolveShelfVisits` scans that order until the customer cap is reached ([context]; [market], 25–29). It does not first sample/shuffle the customer pool. String ordering becomes `h1, h10, h11, h12, h2, ...`, so ID formatting affects business opportunity.

An analytic example demonstrates the problem: with eight equally eligible heroes, each having a 90% independent visit chance and a cap of four, the first four positions each visit with 90% probability; the eighth gets approximately **0.246%**. This example is not a measured claim that all current heroes have a 90% chance. At lower probabilities the bias is smaller, but reputation, loyalty and the Signboard raise chances and amplify the saturation problem.

**Fix:** sample the complete eligible pool in a deterministic fair order, or use a seeded weighted selection with an explicit repeat-customer policy. Keep loyalty meaningful without allowing incidental ID order to dominate. Re-run balance after changing RNG consumption.

**Regression/metrics:** equal heroes differing only in ID, mixed veteran/newcomer populations, distinct classes and high-reputation accounts; measure visit share and the gap between eligible customers and served customers.

### F06 — P2: commission stock can sell before the commission is resolved

**Confirmed ordering; intended policy needs explicit treatment.** Ordinary shelf visits run before commissions. The UI says matching stock “in storage or on the shelf” will be delivered at End Day, but a sole qualifying listed weapon may sell first ([engine], 279–280; [market UI], 89).

Reserve candidate stock for accepted commissions, provide an explicit reservation action, or clearly tell the player to keep the item in storage and show a pre-End-Day readiness check. This is a shop-policy clarification, not a need for manual hero equipping. An ordinary buyer and a commission patron should not compete invisibly for a weapon the player reasonably thought was committed.

### F07 — P2: shop and expedition tallies can disagree with actual outcomes

**Confirmed arithmetic/event gaps.** Gazette's `sold` includes ordinary sales plus commission completion but labels that number as “of N visitors bought”; commission patrons are not necessarily ordinary visitors. The ratio can be misleading or exceed the ordinary visit count. Cash excludes Merchant's Favor bonus, because purchase events do not carry it. Collector purchases are world events and are omitted from the sales/cash tally. Fatal failed expeditions emit a death without `EXPEDITION_LOST`, so the expedition-loss tally misses them ([gazette], 95–113; [market], `purchase`; [events], `collector`; [battle], `resolveExpedition`).

**Fix:** record typed transaction amounts and typed encounter outcomes. Count browsing purchases from `MarketVisit` outcomes, commissions and collectors separately. Use recorded cash deltas rather than reconstructing only selected types. Include fatal losses in the encounter result.

**Regression:** sale with bonus and trade-in, commission-only day with unrelated visitors, collector purchase, and fatal expedition. Verify cash against actual state change attributable to shop transactions, keeping material/tool spending separate from revenue.

### F08 — P2: strongest-champion selection omits warlord matchup effects

**Confirmed source mismatch; a ranking example should be added to tests.** Champion ranking calls `Power.defensePower` with its default `elite=false`. `Battle.outlook` then values the selected champions with `elite=warlord` ([battle], 117–148). Giant Slayer multiplies elite/warlord power, so the best three for the actual siege can differ from the three selected using ordinary power.

**Fix:** use the same foe context when selecting champions and computing their contributions. Test four comparable heroes where a Giant Slayer bearer moves into the best three only for a warlord siege.

### F09 — P2: simulation/version compatibility is recorded incompletely and not enforced

**Confirmed source gap, also admitted in the checklist.** Save schema 1 is still reasonable for compatible fields with defaults. However, `RULES_VERSION` remains 1 through meaningful rule/RNG changes; `GameState` contains content/rules versions but no balance version; `handle` does not validate or route using the stored versions ([engine], 27–38, 82; [model]; [codec]). Loading an old save therefore continues it under the current engine/config without an explicit compatibility policy.

**Fix:** establish a run compatibility policy before production: pin supported rules/content/balance or perform an explicit tested conversion; preserve historical events; reject unsupported versions through the recovery UI. Schema changes and simulation changes need separate treatment. Simply bumping a number without enforcing it is insufficient.

The migration scaffold and old fixture are useful; a no-op v1 round-trip does not demonstrate a real semantic migration.

### F10 — P2: save growth is broader than the checklist's weapon-map item

**Confirmed source structure, partially addressed by the newest commit.** `WeaponPruning` now removes old destroyed weapons and weapons permanently donated or sold to a collector, while preserving signatures, famous blades and recoverable losses ([weapon pruning]). This is a useful, conservative improvement; this report does not flag terminal-weapon pruning as missing.

Unsold storage, recoverable lost weapons, retired/dead heroes, completed commissions and processed-day IDs still accumulate. Many event types are retained forever. Noncombat weapon histories—including repeated trade-ins, sales and honing—are not capped ([model]; [event compaction]; [history compaction]). Every accepted command copies the state, asserts over collections, encodes a complete JSON document and saves it.

The new documented 400-day active-smith pair reduces weapons from 1,727 to 857 and JSON size from 1,745,453 to 1,012,913 bytes (42%). Dedicated tests compare the pruned and unpruned simulations' other state and surviving items. The same investigation reports 2,932 stored weapons out of 3,140 in a 1,000-day fair-smith run; **unsold player property, not terminal losses, is that policy's dominant remaining growth**. These are repository measurements, not my own execution ([decisions], 1032 onward).

The 5,000-day soak additionally trims *all* events to a 30-day window in the simulator and caps forging at one per day. That helps its intended arithmetic stress test, but it does not validate production's kept-forever history behavior or normal full-energy stock growth ([soak]; [simulator], `eventRetentionDays`). Its performance test only asserts p95 is greater than zero, not below the proposed target.

**Fix:** retain the new terminal pruning; define hot-state versus archived history, compact finished commission/hero records where safe, and retain bounded artifact/lineage summaries. Do not silently delete unsold player property or recoverable lost weapons. Consider storage organization, bulk stock actions and an archive before making any inventory-cap decision. Add a production-shaped soak with normal forging, engine compaction only, save bytes, encode/decode/commit time, UI stock rendering and memory measurements.

### F11 — P2: exact event-state determinism depends on the device locale

**Confirmed source behavior.** Expedition event data formats win probability using `"%.2f".format(...)`, which uses the default locale ([battle], `resolveExpedition`). Different device locales can produce decimal dots or commas in the otherwise same event payload.

Gameplay calculations remain the same, but this defeats the broad claim of equal serialized state/events across platforms. Store structured numeric data or format internal payloads with an explicit locale. Add a two-locale determinism test.

## 6. Gameplay depth: implemented mechanics that still need better decisions

### 6.1 Demand is more important than the number of possible weapons

Customers do use class fit, elemental taste, traits, loyalty, fame, affordability and price. However, candidate selection first requires strictly positive truncated power improvement. This gate can exclude a desirable sidegrade before taste/fame/collector utility matters. It also ignores affix combat multipliers, faction-specific effect and fame power when measuring equipment improvement ([market], 54–85).

This is a deliberate simplifying rule rather than proof every purchase is wrong. It limits the GDD fantasy of materially different tastes and specialized equipment. Refine purchase reasons and test useful sidegrades before increasing catalog size. A collector or threatened defender should occasionally value a different property for an intelligible reason, while ordinary adventurers should still behave sensibly.

Add a read-only current-demand view: current hero needs, relevant equipment gaps, actual request conditions and likely affordability bands. Do not promise that a particular hero will visit tomorrow unless a visitor schedule has actually been committed. A generic “Rangers favour bows” line is a useful introduction but cannot explain why today's named customers declined today's stock.

### 6.2 The Signboard and Guild Patronage need visible, useful effects

The Signboard raises visit probability while ordinary customer capacity remains four. The repository's v3 sweep already found roughly 3.9 visits/day and a negligible benefit; removing the Signboard improved the bot's result by freeing money for other tools ([decisions], 527–565). This is acknowledged in the open checklist.

Guild Patronage uses the same probability lever and should be checked for the same saturation problem. A blessing described as sending more customers should produce a perceptible difference. Customer diversity, extra capacity or a clear different demand profile are possible levers. Change one at a time, fix selection fairness, then measure the resulting economy.

### 6.3 Permanent progression has substantial unevenness

Recorded merged-v4 results at 1,000 seeds show these **mean survival changes for a maxed individual upgrade under BALANCED_ACTIVE** ([decisions], 1018–1024):

| Upgrade | Mean days added, recorded v4 |
|---|---:|
| Stalwart Walls | +6.1 |
| Well-Stocked Cellar | +3.4 |
| Forge Mastery | +2.1 |
| Thrifty Hands | +1.3 |
| Lucky Hammer | +0.6 |
| Tireless Smith | +0.3 |
| Family Savings | +0.1 |
| Known Name | −0.5 |

These are recorded bot results, not my new measurements. Survival is not the only useful upgrade metric: energy/gold can improve experimentation or convenience without lengthening a run. Nevertheless, the game promises strong permanent progression, and some equal-cost choices offer little measured payoff. Reprice/rework them or demonstrate their benefit through another meaningful metric. Do not tune every upgrade solely to extend survival.

Measure time to desired recipe, experiments completed, first useful higher-tier sale, customer reach and first-siege equipment. Show the player a concrete next-run change after buying an upgrade.

### 6.4 The current harness leaves Advanced Forge and several rewards underexplored

The policy driver always creates `ForgeMode.QUICK` commands, chooses the first offered blessing, and accepts commissions without a commission-targeted crafting strategy ([simulator], `playRun` and `chooseForge`). Thus the “GDD policy set” is useful for baseline economics/survival but does not establish Quick-versus-Advanced viability, technique trade-offs, catalyst value, deliberate signature pursuit or commission-led play.

Add Advanced/catalyst/technique policies, a request-driven smith, discovered-signature pursuit, different blessing strategies, and several naturally played legacy eras. `signatureDiscoveries` currently counts transformed weapons, including repeats, rather than unique first discoveries. Keep both statistics with accurate names. Artifact recovery needs its own metric. No need to add more content until these existing decisions are shown to work.

### 6.5 Signature clues do not sufficiently distinguish catalyst choices

The persistent signature hint maps every required catalyst to “a steadying hand.” After the first clue, later missing-condition feedback is suppressed; a discovered signature shows name/flavor rather than a reusable recipe summary ([journal], 54–67, 111–120). Four catalysts receive the same generic forge bonuses outside exact signature conditions ([forge]).

Use authored, catalyst-specific clues; progressively improve a clue after a relevant experiment; after discovery, allow recalling the known recipe into the draft. Preserve mystery and avoid displaying transformation odds. Make catalysts' intended identities visible or explicitly treat their differentiation as signature-related rather than implying four mechanically different general-purpose bonuses.

### 6.6 Heroes and weapons need inspectable continuity

Retirement, mentoring, inheritance, theft, loss and return exist. Town rows do not show guild/mentor details, and the UI offers no full live weapon history view. The Legend Board is a text summary. The first release acceptance test should include a player following one blade across a sale, several fights and an ownership change—not just the existence of fields in a save.

Add shared hero/item detail surfaces opened from shop encounters, shelves, news and Town. Surface first buyer, current holder, relevant fights, condition, earned title and significant ownership changes. Keep ordinary stories brief. Distinctive names/sprites and repeated recognizable faces should help, rather than producing ever-longer affix prefixes.

Returned legends currently lose affixes, flaws, catalyst and signature identity because `LegendEntry` does not store them and the return event constructs empty affix/flaw lists. Owner summaries only read SOLD/COMMISSION, omitting heirs and other ownership transfers ([legacy]; [model], 202–214; [events], 302–318). A blade may return under an affix-bearing name without the matching property. Define which traits are dormant versus preserved, use durable artifact/lineage identities, and test that the displayed story remains truthful across several eras.

### 6.7 “No softlocks” is supported only for tested bot scenarios

The current counter increments only when the policy cannot forge and there are no listed weapons; a player can still have listed but unwanted stock and no affordable legal forge. Zero counters under selected bots do not prove recovery from every reachable economy state ([simulator], `couldForge` / `hardLocks`).

Test depleted cash/materials, repeatedly failed sales, free listings, stock shortages and inexperienced recipe choices. Distinguish a temporary drought, an intentional decision to rest, and a persistent inability to perform a useful smith action. Keep reliable basic supply, but remember stock availability alone does not provide buying power.

## 7. Balance interpretation

The latest merged-v4 evidence is **1,000 seeds per policy**, not a fresh 10,000-seed review. Earlier 10,000-seed v3 results cannot certify the combined wear/fame/warlord/tool changes.

| Policy | Recorded v4 median days | Recorded v4 mean days | What it suggests |
|---|---:|---:|---|
| BALANCED_FAIR | 20 | 20.5 | Fits the provisional early-run band |
| BALANCED_ACTIVE | 25 | 26.4 | Extra shop actions help, but the advantage narrowed after wear |
| SYNERGY | 35 | 34.0 | Matching factions/material affinities has meaningful leverage |
| BALANCED_INVEST | 30 | 29.5 | Material purchasing can improve survival |
| BALANCED_CHEAP | 25 | 24.0 | Lower prices improve access to equipment |
| BALANCED_EXPENSIVE | 10 | 12.4 | Inexperienced pricing can be very punishing |
| PASSIVE | 10 | 10.0 | The smith's work matters |
| Maxed upgrades, ACTIVE | 40 | 37.7 | Combined legacy has substantial impact |

Source: [decisions], merged-v4 table. These comparisons indicate real strategic agency, but the player needs to see why the better policies work. Bot competence is not a substitute for onboarding.

Sales in the recorded fair/active runs remain around a quarter of visits. Repeated refusals can still support an engaging shop when they reveal an actionable mismatch; they are discouraging when summarized as unexplained names leaving. Improving the presentation should be followed by fair customer selection and measured demand tuning, rather than simply forcing all visitors to buy.

No recommendation here changes the locked 10 base energy, guarantees immortality, adds a final wave, or converts the game into live idle production. The target 15–25 days is a design calibration goal, not a completion condition.

## 8. UI and accessibility review, with redesign deferred

The boxy appearance has a structural cause: Home is a sequence of rounded surfaces, Market is long stock rows plus cards/forms, and Town uses more surfaces/lists. Seven bottom destinations compete for width. The actual forge scene is just **52 dp high** in the active forging screen; its artwork has little opportunity to establish a place ([forge UI], `ForgePanel`; [workshop], 59–67).

A later visual redesign should make the workshop/counter/shelves the dominant setting and keep inventory management as contextual panels. Replacing rectangles with textured rectangles will not by itself create a shop atmosphere. Preserve native readable text and accessible controls over the artwork.

### Functional changes worth doing before the art pass

- Make the first-run Home offer a clear starting action; its panel currently has no onboarding tip.
- Make preparation, shopping and aftermath distinct moments within the same day.
- Explain failed requests and failed purchases precisely; add relevant hero/item details.
- Put commissions and business feedback before an indefinitely growing storage list.
- Offer recipe recall/repeat for learned combinations and useful shortcuts from a request to a draft.
- Show a short, factual tomorrow objective rather than another general dashboard of all state.

### Claims in the accessibility checkbox are too broad

The nav text explicitly shrinks to **8 sp** to fit seven items. Shrinking large-font labels avoids clipping but does not demonstrate readable scalable navigation. The recorded 1.5 font-scale pass chiefly describes Town, with the nav itself noted as tight. Fixed widths in the market price editor and forge summary need a small-screen/large-font pass. Long shelves, Town populations and Gazette histories are composed in ordinary scrolling Columns, so all entries are composed rather than virtualized.

Reduced motion, many minimum-height controls, semantic descriptions, portrait orientation and rarity text are present. However, no automated screenshot/accessibility suite or completed 720×1280/low-memory pass is evidenced at this snapshot. Audio/haptics settings are absent. Settings are embedded in Legacy.

The player-facing efficiency and exceptional-roll upgrade descriptions disclose explicit chance percentages, despite the GDD's descriptive probability rule ([launch], 221, 223). Deterministic amounts such as price, energy or a quality requirement are different; they can remain clear numeric values. Align stochastic descriptions with the chosen disclosure policy.

## 9. Checklist assessment

The checklist is useful and unusually candid about several remaining items. It should be treated as a **source implementation inventory plus prior verification notes**, rather than a launch certification.

| GDD area | Review status | Main correction or remaining gate |
|---|---|---|
| Complete forge→sale→world→defeat→legacy loop | Implemented at source / prior device demonstration | Add customer experience and safe run-end operation |
| Portrait, offline, autonomous combat | Source-consistent | Retain these boundaries |
| Full launch counts | Source-present with dedicated tests | Count does not establish reachability or good play |
| Forge, risks, energy and overwork | Substantial implementation | Validate Advanced/technique economics and explain outcomes |
| Journal/signatures | Implemented, discovery UX partial | Specific clues and reuse of learned recipes |
| Shelf prices and autonomous purchases | Implemented, selection/feedback issues | Fair visitor selection; precise reasons; useful demand |
| Commissions | Implemented, usability rule mismatch | Visible criteria and reservation/readiness policy |
| Named heroes/generations | Engine present, player story surface partial | Inspectable needs, guild/mentor links and continuity |
| Living artifacts | Engine present, cross-era fidelity partial | Identity/property/history preservation |
| Sieges and dynamic champions | Implemented, special-case ranking issue | Consistent warlord context; measured forecast clarity |
| Legacy progression | Mechanically implemented, impact uneven | Upgrade race protection and benefits the player can perceive |
| Gazette/replays | Implemented, reporting incomplete | Unified day events, truthful accounting, persistent viewing state |
| Persistence | Atomic pair writes exist; lifecycle reliability incomplete | Serialized operations, failure recovery, compatibility policy |
| History compaction / terminal pruning | Partial; latest commit improves terminal items | Unsold stock and full hot-state/save growth remain unbounded |
| Tests and balance | Strong source suite / documented prior passes | Fresh execution and production-shaped/device gates remain |
| Pixel art | Integrated assets and pipeline present | Art coverage is not a final composition/design pass |
| Accessibility/onboarding | Partial | First-run path, navigation, small screens, actual device validation |
| Premium release | Open | Application identity, signing/build artifact, listing and legal assets |

### Checked entries that should be narrowed or split

1. **“Saved atomically before the report”** is true for normal dispatch, but not enough to claim safe concurrent upgrade/run transitions.
2. **“Daily paper from real event records”** is true, while “complete and consistent paper” is not: immediate and archived events differ and tallies have gaps.
3. **“Resume after process death”** should distinguish committed world restoration from report/reveal/draft/panel/presentation restoration. World and unread-report restoration are now implemented; reveal/draft/panel recovery and a future shop-sequence cursor need separate treatment. The report-dismissal marker is written asynchronously, so interruption can still reopen an already-closed report; this is a presentation repeat, not a repeat gameplay resolution.
4. **“Three dynamic champions”** needs the warlord ranking context test.
5. **“No softlock”** should be scoped to the recorded policies/counter; adversarial recovery remains open.
6. **“48 dp targets, font scale 1.5 pass, screen-reader descriptions”** should be split into implemented semantics, recorded inspected screens and still-open device/a11y validation.
7. **“140 JVM tests”** describes an older test inventory and prior developer results. The latest source has 144 core tests; neither number is an independent execution or a coverage guarantee. Instrumented inventory is now six.
8. **“5,000-day soak”** is an arithmetic/simulation stress check with additional simulator trimming; production memory/save bounds remain open.
9. **“Every locked v1 system has a UI surface”** should be qualified: some mechanics are mentioned in rows or journal text but are not meaningfully inspectable, and several accessibility/presentation release requirements remain open.
10. **“Top strip: integrity and next siege”** does not match the active top-strip implementation, which shows day, gold and energy/debt. Threat information exists elsewhere. The detailed GDD top-strip layout is proposed, so this is a checklist accuracy correction rather than a locked-layout violation.

### Open entries affected by the newest commits

- **Unread day report after process death:** implemented in initialization using the saved resolution and a DataStore dismissal ID. The decision document records an emulator kill/relaunch check, including closing the report; the checklist should reflect that evidence while leaving full interruption testing distinct.
- **Weapon-map pruning / 1,000-day saves:** terminal-item pruning is implemented; broad bounded-save behavior is still partial because stock and recoverable/history records remain. Replace the overly broad item with separate terminal pruning and remaining growth gates.
- **Day simulation p95 under 200 ms on mid-range Android:** an instrumented assertion now exists. The latest commit reports p50 4.4 ms, p95 8.5 ms and max 35.5 ms over 120 forced-survival days on a Pixel 10 Pro AVD, API 37/x86_64. This supplies Android-runtime emulator evidence; it does not complete a physical mid-range-device gate or include persistence/rendering. Preserve that distinction.

### Open items that should not silently become extra launch scope

The GDD labels the detailed permanent-upgrade categories, exact merchant-resale death branch and scored daily utility implementation as proposed examples or implementation designs. Missing each proposed example is not automatically a breach of every locked decision. Keep them in the backlog where useful, but prioritize the already-locked fantasy: real autonomous buyers, meaningful material choices, strong legacy and memorable artifact/hero consequences.

The locked counts of 16 materials, six families, five classes, three factions and 25 events do matter. The 25-event implementation uses 23 pooled events plus two generational rules; that deviation from “25 pooled events” is explicitly documented and still represents the named systemic content. Validate its reachability and story meaning rather than duplicating the generational rules merely to change a count.

## 10. Recommended work order

| Milestone | Concrete scope | Exit gate |
|---|---|---|
| A — Trustworthy state and reports | Serialize authoritative operations; storage recovery; report/archive event coherence; exact commission readiness; correct cash/encounter accounting | Targeted race/failure/report regressions pass; no lost next-era save |
| B — Shop day prototype | Persist encounter snapshots; counter sequence with real visitors, browse/decline/buy/trade-in/commission; skip and resume | A player remembers a buyer and understands a refusal; presentation cannot change outcomes |
| C — Understandable decisions | Current demand, hero/item details, causal aftermath, learned-recipe recall, one tomorrow opportunity | Player can explain how one material/price choice changed the day |
| D — Meaningful progression | Visitor fairness, Signboard/Guild Patronage, weak-upgrade impact, Advanced/signature/commission policies, cross-era fidelity | Current 10,000-seed review plus ordinary human playtests support the choices |
| E — Full visual redesign | Workshop-led composition; counter and shelves; contextual navigation/panels; cohesive custom art/audio | Main loop feels like a place and stays usable at large fonts/small screens |
| F — Production gates | Compatibility policy, bounded saves, CI, device interruptions/performance/a11y, release identity/artifact/listing | GDD section 19 acceptance demonstrated on the intended devices |

This sequence keeps engineering changes reviewable and avoids spending the art budget on a flow the player still cannot understand. The next delivery should contain a playable shop loop, not simply another large set of gameplay additions or an additional dashboard tab.

## 11. Targeted verification plan

| Test or measurement | Why it is necessary |
|---|---|
| Upgrade A + upgrade B with delayed/reordered saves | Catch stale-profile overwrite |
| Upgrade + Begin Era with delayed saves | Catch clearing/reverting a new run |
| Load/decode/write failure recovery | Preserve the last committed game and show an actionable error |
| Preparation events vs completed report vs archive | Prevent incomplete or conflicting day narratives |
| Commission quality band boundary and reservation case | Match visible promises to fulfillment |
| Transaction accounting with bonus/trade-in/commission/collector | Make the shop's money understandable and truthful |
| Encounter win/loss/death accounting | Keep world results complete |
| Equal-eligibility visitor distribution under capped high demand | Remove ID-order starvation |
| Elite/warlord champion ranking | Select the actual strongest available defenders |
| Locale-switched deterministic state/events | Keep internal event payloads stable |
| Quick/Advanced/technique/signature/request policies | Validate existing decision paths omitted by the current bots |
| Several genuine legacy eras with returning artifacts | Test continuity rather than isolated mocked fixtures |
| 1,000+ days using production compaction and ordinary forge volume | Measure save size, encode/decode/commit time and hot-state growth |
| Process death at forge save, End Day save and presentation acknowledgement | Prove committed outcomes and presentation resume are distinct |
| 720×1280, large font, TalkBack and reduced motion over the complete loop | Validate usability beyond a normal-size Town screenshot |
| Three-to-five short first-time playtests | Establish comprehension, memorable customers and willingness to play another day |

Proposed human-playtest questions: “Who bought that weapon?”, “Why did the other customer leave?”, “What changed because of your weapon?”, “What will you make or change tomorrow?”, “What does your upgrade do next era?” Observe their actions before explaining the rules. Difficulty answering these is actionable product evidence.

## 12. Additional engineering and release notes

- Invariants cover useful bounds and ownership, but do not validate every loaded content reference, champion uniqueness/health availability, hero health/quality bounds or all model relationships. Validate loaded saves and include the relevant extra checks as the model grows; do not turn a corrupt save into an unhandled invariant crash.
- `BalanceConfig` centralizes many numbers, but class/trait/faction/affix values remain in content and several hero/commission/world-event constants are inline. Keep configurable content values in their appropriate definitions; move truly tunable resolver constants out of logic. The repository's “every number in BalanceConfig” statement is broader than the implementation.
- Engine command evaluation currently runs on the ViewModel's main dispatcher. Existing desktop timings are encouraging, but measure actual simulation plus save/presentation latency on Android. Move substantial pure computation off the UI thread through the same serialized operation boundary if measurements justify it.
- The six instrumented tests are one Room round-trip test, a title screen test, two forge-hint tests, an End Day timing assertion and a template application-context test. They do not cover ViewModel concurrency, storage failure, process death, screenshot/a11y or the entire end-of-day shop loop. The local Android-module test is also a template addition test. The manual report-recovery check is documented separately.
- A minimal core-test CI workflow, and later app build/lint checks, would provide reproducible commit-level verification. The current GitHub snapshot exposes no such run.
- Package/namespace remain `com.example.blacksmithproject`; release optimization is disabled; no production signing/AAB configuration is evidenced; launcher resources still reflect project scaffolding. The player-facing app name is already **Tiny Blacksmith**. These are release tasks, not the most valuable immediate gameplay work.
- No network permission, ads/billing/analytics dependency or required backend appears in the inspected app configuration. This is consistent with the offline premium brief. Keep production listing promises aligned with demonstrated gameplay and the GDD's one-time €1.99 model.

## 13. Evidence index

Links below are pinned to the reviewed commit, so later changes do not silently change the basis of this report. Line numbers cited in the text refer to that snapshot.

[VM]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/GameViewModel.kt
[run-end]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/ui/RunEndScreen.kt
[engine]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/engine/GameEngine.kt
[context]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/engine/ResolutionContext.kt
[model]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/model/Model.kt
[market]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/market/Market.kt
[market UI]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/ui/MarketPanel.kt
[labels]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/ui/Labels.kt
[panels]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/ui/InfoPanels.kt
[gazette]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/gazette/Gazette.kt
[battle]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/battle/Battle.kt
[events]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/engine/WorldEvents.kt
[codec]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/persistence/SaveCodec.kt
[event compaction]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/persistence/EventCompaction.kt
[weapon pruning]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/persistence/WeaponPruning.kt
[history compaction]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/persistence/WeaponHistoryCompaction.kt
[soak]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/test/kotlin/com/tinyblacksmith/core/SoakTest.kt
[simulator]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/sim/Simulator.kt
[decisions]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/docs/DECISIONS.md
[journal]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/crafting/Journal.kt
[forge]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/crafting/Forge.kt
[legacy]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/legacy/Legacy.kt
[launch]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/main/kotlin/com/tinyblacksmith/core/content/LaunchContent.kt
[forge UI]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/ui/ForgePanel.kt
[workshop]: https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/ui/WorkshopScreen.kt

- [Repository GDD](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/Tiny_Blacksmith_GDD_v1.0.md)
- [Feature checklist](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/docs/GDD_CHECKLIST.md)
- [Progress and prior validation evidence](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/docs/PROGRESS.md)
- [Checked-in save fixture](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/core/src/test/resources/saves/v1_forced_seed4242_day61.json)
- [Room save implementation](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/data/SaveStore.kt)
- [Day report / item result UI](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/ui/Dialogs.kt)
- [Home dashboard](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/ui/HomePanel.kt)
- [End Day Android performance test](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/androidTest/java/com/example/blacksmithproject/EndDayPerfTest.kt)
- [Latest performance evidence, recorded in commit](https://github.com/Morfildor/BlacksmithInc/commit/b39ad76a07c3c48059ad81e50deacef981d7f8f1)
- [Report-dismissal settings](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/src/main/java/com/example/blacksmithproject/data/SettingsStore.kt)
- [App build configuration](https://github.com/Morfildor/BlacksmithInc/blob/b39ad76a07c3c48059ad81e50deacef981d7f8f1/app/build.gradle.kts)

