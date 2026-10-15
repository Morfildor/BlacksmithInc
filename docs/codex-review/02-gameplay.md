# Tiny Blacksmith: independent gameplay and fun audit

15 October 2026. Implementation reviewed: **local working tree at `406f26ce2d446d8984e86da0af3846a18ebfedd2`**, rules 7 / balance 16 / content 6. Initially clean tracked tree; pre-existing untracked `build-output/`. No branch operations or gameplay changes. The only authored deliverable is this report; execution evidence is in ignored `core/build/codex-review/`.

**Verdict:** there are interesting problems here, especially building a guild company and preparing for a known siege. Understanding the game materially beats passivity, buying the biggest number, and repeating one recipe. However, the daily loop asks players to administer more systems than it gives them competing objectives. Classic play increasingly becomes production followed by observation; guild play offers stronger agency but funnels it toward preparation, sabotage and safe income. The next improvement should be better conflicts between existing systems, not more content.

Evidence labels: **V** = verified code behavior; **M** = fresh measurement; **H** = design interpretation or proposal, requiring player validation. No human playtest or Android screen session was performed. “Fun” is a hypothesis, not a simulator output. Source references below are relative to `core/src/main/kotlin/com/tinyblacksmith/core/`, unless otherwise stated. Line numbers refer to the audited tree.

## 1. What players actually decide over a run

| Days | Classic | Guild | Decision quality |
|---|---|---|---|
| 1–5 | Spend a limited kit, choose customers/commissions to serve, price versus generosity, tools versus ingredients, overwork versus tomorrow, prepare for siege one. | Charter/opening, hire versus forge, loan versus sell, party composition **and action order**, posture, first sabotage/opportunity, keep defenders home. | Strongest learning period. A Bronze counterattack needs Guard; Silver needs a Charge spender. Higher numbers alone are insufficient. Avoiding a two-day departure across siege night is often the obvious answer. |
| 6–10 | Pay for immediate deep-road access or wait; spend on costly ingredients versus cheaper reliable work; read the next besieger. | Deep Road competes with the siege's limited mission window; relic additions, repairs, wounds, recruitment and the first regional laws affect the plan. | Real opportunity costs. Deep Road is acceleration, not an obligatory permanent unlock: siege two opens it anyway. |
| 11–20 | All supplier stages are available. Replace worn weapons, target siege demand, pursue signatures or fill stock. | Sustain a working build, spoil or counter preparations, rescue/recover, choose specialities, decide whether a deeper stage is worth exposure, approach the charter warlord. | More possible combinations, fewer new objectives. Classic shelf effectiveness falls sharply. Guild's recurring five-day schedule starts to dominate the board. |
| 20+ | Repeat preparation against growing raids; knowledge/legends persist when the forge eventually falls. | Secure the charter, retire successfully or continue; optionally raise rank. | Retirement is a useful stopping decision. Continuing mostly extends the established engine; ranks add enemy stats, an extra siege unit and shorter offer life, rather than a new economic or tactical objective. |

**V:** `engine/GameEngine.kt:409` (`endDay`); `engine/DeepRoad.kt:25,48,85`; `guild/Missions.kt:76,181,218`; `guild/Stories.kt:80,102,116,128`; `content/GuildContent.kt:485`. Party order is explicitly chosen in `app/src/main/java/com/example/blacksmithproject/ui/ContractSheet.kt:89`.

The most promising risks already exist: let the enemy preparation stand to take Heartfire work; deliberately crack a weapon to trigger the Salvage Bell; turn overhealing into protection; return from a delve with the first stage banked; rescue a named member before the deadline. Preserve these. Routine restocking/listing, automatically repeating the current strongest recipe, and paying an opening's initial tax are weaker decisions once understood.

## 2. Fresh strategy measurements

Exploration used 24 seeds first. The ambiguous sabotage result justified expanding to **192 paired seeds, 810001–810192**; charter variants, continuation, recovery and specialist-upgrade checks used the first 96 seeds. Accounts start without upgrades except explicit legacy comparisons. Default guild horizon is 30 resolved days; its next-morning output can say day 31.

### Classic: same 192 seeds per policy, horizon 400

| Policy | Median / mean days | Siege 4 held, if reached | Forged / sold per run |
|---|---:|---:|---:|
| PASSIVE | 10 / 10.0 | — | 0 / 0 |
| NOVICE | 30 / 27.2 | 14.4% | 56.5 / 42.9 |
| BALANCED_FAIR | 25 / 24.0 | 0.6% | 114.2 / 39.7 |
| BALANCED_EXPENSIVE | 25 / 23.8 | 1.6% | 114.2 / 38.5 |
| BALANCED_ACTIVE | 35 / 31.9 | 41.1% | 127.0 / 65.4 |
| SIEGE_PREP | 40 / 39.6 | 90.1% | 169.0 / 72.2 |
| EXPERT | 45 / 42.8 | 95.8% | 177.5 / 97.7 |
| ADVANCED_SMITH | 25 / 25.6 | 4.8% | 75.9 / 43.2 |
| SIGNATURE_PURSUIT | 30 / 28.2 | 12.7% | 102.8 / 53.3 |
| RECKLESS_FAIR | 30 / 27.1 | 8.9% | 131.2 / 46.6 |
| FREE_LISTINGS | 25 / 24.6 | 0.5% | 87.4 / 37.5 |

**M:** Preparation is a substantial lever; the old “classic is an unavoidable arithmetic slide” finding is too broad. Premium offers have removed the historical expensive-pricing survival cliff. Even the novice's two-forges policy outlives indiscriminate high-throughput production here. Policies are bundles, not isolated interventions; this does not establish that forging less alone causes longer survival. CLI summaries do not provide paired classic standard errors.

### Guild: Town's Last Hope, 192 seeds per policy

| Policy | Charter by horizon | Forge fallen | Captured / member deaths per run |
|---|---:|---:|---:|
| PASSIVE | 0% | 100% | 0 / 1.31 |
| NOVICE | 12% | 46% | 0 / 0.72 |
| ADAPTIVE | 54% (103/192) | 5% | 0.55 / 0.46 |
| SYNERGY | 62% | 10% | 0.22 / 0.16 |
| NO_SABOTAGE | 39% (74/192) | 16% | 0.30 / 0.59 |
| IGNORE_COUNTER | 1% | 55% | 0.34 / 1.97 |
| REPEAT_RECIPE | 0% | 89% | 0.71 / 2.51 |
| HIGHEST_STAT | 10% | 71% | 5.14 / 4.24 |
| GREEDY_DIVER | 9% | 68% | 7.90 / 7.29 |
| RECOVERY_AWARE | 12% | 36% | 0 / 0.80 |

ADAPTIVE versus NO_SABOTAGE: **51 seeds only Adaptive wins, 22 only No-sabotage wins, 52 both, 67 neither**. Charter difference +15.1 percentage points; approximate paired 95% interval **+6.6 to +23.6**. No-sabotage takes **17.8 more forge damage across the first four sieges, SE 2.3**. This supports a strong advantage, **not mandatory sabotage**. IGNORE_COUNTER also drops scout-based equipment/wall preparation; its much worse result must not be attributed to sabotage alone (`sim/GuildSim.kt:100,267,390`).

On 96 seeds, Adaptive / No-sabotage charter rates were **42/32% Licensed Bad Ideas** and **47/28% Honest Business**. The Licensed difference has only 25 versus 16 discordant wins and remains uncertain; Honest has 29 versus 11. Other Last Hope policies, n=96: Defence-first 34%, Legend-seeker 39%, Supply-farmer 39%, Shop-focused 6%. Several routes work, but all are weaker than Adaptive's 58% on that subset; these policies also differ in forging, hiring and risk.

### Recovery, openings and continuation

- **Recovery:** raising only `wallWorkExtraRecovery` from 1 to 3, n=96 per policy, left Recovery-aware charter wins at **9/96**, while forge falls dropped **38 → 20**. Adaptive stayed **56/96** charter wins; falls **4 → 2**. More repair buys time; this small change did not create another way to beat the warlord.
- **Openings:** Last Hope Adaptive following versus ignoring the opening plan, n=192: **54% versus 48%**. Discordant totals 47 versus 36; the aggregate difference is inconclusive at 95%. Stormwell was 42% either way, n=36. Upfront-drawback ablations across all three charters sampled all eight packages; individual cells were only n=10–45 and changed in both directions. Do not call a package dominant from these cells. Crucially, `noDrawback` removes only gold/integrity/militia deltas, **not** Brittle or other continuing build costs (`sim/GuildSim.kt:562`).
- **Continue to day 60, no retirement**, n=96: Adaptive 42% fallen, Synergy 32%, No-sabotage 55%, Greedy-diver 100%; respective mean durations 52.3 / 53.8 / 48.1 / 29.2 days. Charter-earned shares were 59/69/45/5%; earning the charter and subsequently falling can overlap. These bots **never raise rank**, so this is evidence about ordinary continuation, not higher-rank balance. Many successful policies remain alive at the horizon; their full lifetimes are censored.

## 3. Ten biggest weaknesses, ranked

Each entry separates the observation from the interpretation. Ranking is expected enjoyment impact, not defect severity.

1. **The mission board has many cards but a narrow strategic centre. [M,V → H]** Adaptive's contracts were 36% Sabotage, 32% Supply; Synergy's were 33% Sabotage, 30% Hunt. Adaptive took no Delve; a deliberate Legend-seeker took 4% Delve, winning 46% of those attempts. **Cause:** one party, a short pre-siege window, and sabotage deleting the announced preparation give prevention unusually broad value (`guild/Missions.kt:76`; `guild/SiegeFight.kt:90`). Mission frequencies partly reflect hard-coded bot priorities, not proof that ignored jobs are worthless. The weakness is insufficiently demonstrated competing plans, not lack of jobs.

2. **Reactive repair is good at delaying defeat, poor at changing the problem. [M,V → H]** Recovery-aware repaired safely but secured few charters; extra recovery sharply reduced falls without increasing wins. **Cause:** contract repair shares the daily allowance, adds only one extra by default, and grants before the siege; patrol repair then gets the remainder (`guild/Missions.kt:412`; `engine/GameEngine.kt:441,489`; `engine/ResolutionContext.kt:125`). It repairs an attrition meter without answering the enemy composition. A recovery-themed strategy can become maintenance work rather than a comeback.

3. **Classic production outgrows worthwhile demand. [M,V → H]** Fair-policy conversion fell **46.3 → 25.5 → 16.5 → 13.9%** across the four day bands; 53.1% of visits ended NOT_BETTER. Only 39.7 of 114.2 forged weapons sold. **Cause:** customers mostly need an improvement; sidegrade motives are bounded, while the smith can keep producing (`market/Market.kt:231,238`). Demand-directed Expert improves conversion, so this is not a universal market failure. The default rhythm nevertheless rewards repeated low-information attempts and leaves the player sorting surplus.

4. **Classic's apparent crafting depth exceeds its actual combat vocabulary. [V,M → H]** Core-metal triggers, Charge loops and catalyst interaction rules belong to the guild interaction engine; classic uses scalar power, affix multipliers and elemental matchup (`battle/Power.kt:12`; `crafting/WeaponRules.kt:54`; `crafting/Interactions.kt:36`). Techniques, catalysts and scalar affixes still matter. **Cause:** two payoff models share the same recipe vocabulary. An inventive classic smith cannot express the guild forge's tactical ideas. Advanced alone is insufficient: untargeted Advanced found **0.365 first discoveries/run versus 2.526** for pursuit (35% versus 89% of runs found one).

5. **A few broadly useful ingredient answers overshadow expensive conditional ones. [M,V → H]** The lab's Frost Bloom was within five score points of the best augment in **69%** of groups, Sun Ash 57%, Grave Dust 24%, Ember Resin 20%. Frost+Oil reached the top tenth in 42%, but a cheaper same-family recipe was within five points in **96%**, and beat it in 69%. Starsteel's own rule fired in only **44% of prepared early cases**; Moonsteel was prepared in just 18% of cases. **Cause:** action/trigger order and prerequisites limit expensive rules, while Chill directly suppresses enemy damage. Two factions share Frost weakness/Fire resistance (`content/LaunchContent.kt:194,208`; `content/CombatContent.kt:202`; `combat/Fight.kt:87,138,203`). These are synthetic coverage measures, not human pick rates or proof of universal dominance; Sun Ash clearly wins a different backline niche.

6. **Discovery eventually becomes retrying a solved recipe. [V,M → H]** Every near miss earns a missing clue, but after all rungs are held, an eligible first discovery still rolls a base 15% chance; there is no failure-progress guarantee. At an unchanged 15%, ten eligible attempts still have a **19.7%** chance of no success. Known signatures improve to 75% (`crafting/Forge.kt:196,238`; `crafting/Journal.kt:79,99`; `config/BalanceConfig.kt:151,682`). **Cause:** knowledge completion and item acquisition are separate gates, but the latter can cease to ask a new question. The pursuit bot's success disproves “signatures unavailable”; it does not make a long unlucky tail enjoyable.

7. **Continuing a successful company mostly asks for more of the same. [V,M → H]** The supply tree finishes opening after siege two. Rank one changes enemy numbers; ranks two/three add a unit and shorter deadlines (`engine/DeepRoad.kt:25`; `content/GuildContent.kt:485`). There are real later bonds, specialities, laws and nemeses, but rank escalation does not itself require a new operating plan. **Cause:** continuation increases pressure more reliably than it changes the objective. Long rank-zero survival in the horizon-60 sample shows that this repetition can last; it does not prove immortality or high-rank triviality.

8. **Several opening costs are admission prices rather than ongoing dilemmas. [V,M → H]** Overflow/Stormwell principally charge 40 starting gold; other packages charge starting health/gold (`content/GuildContent.kt:508`). Their combinations genuinely differ, and Salvage retains repair costs; they are not reskins. **Cause:** persistent benefits often meet a one-time drawback that stops shaping choices once recovered. Plan-following and upfront-drawback samples did not establish clear per-package outcome effects. That uncertainty is a reason to measure the operating behavior, not to force a larger punishment or declare all drawbacks harmless.

9. **Permanent progression mixes survival, throughput and specialist benefits without equally clear identities. [M,V → H]** On Fair, maxed Walls added **5.8 mean days**, Cellar 3.3, all upgrades 9.8; every other single track moved mean survival by under one day. On Signature-pursuit, Anvil Lore raised signature-bearing weapons at run end **5.78 → 9.19/run**; Homing Steel raised returning legends **0.23 → 0.68**, with almost no survival change. **Cause:** identical 8/20/45 price ladders sell benefits dependent on activity and saturation (`content/LaunchContent.kt:227`; `legacy/Legacy.kt:154`). “Nine upgrades do nothing” is false. The weaker opportunity costs are among economy tracks once more production no longer finds buyers.

10. **Premium offers risk replacing a pricing cliff with a pricing default. [M,V → H]** Expensive listings had the same median life as Fair and median income **3,757 versus 3,423**; final median cash 785 versus 428. **Cause:** eligible premium listings automatically settle at `min(listed, funds, ceiling)`, subject to the asking-price floor and offer cap; the cost is lost sale reputation (`market/Market.kt:263,350`; `core/src/test/kotlin/com/tinyblacksmith/core/OffersTest.kt`). A high listing delegates much of price search to the engine. Global dominance is unproven: long-run reputation and customer composition still matter. Test premium versus relationship strategies rather than restoring the old cliff.

## 4. Forging, replayability and emotional payoff

**What already works:** Forge outlooks, typed prerequisite checks, synthetic bench reports, learned-versus-witnessed Notebook entries, saved fight timelines and per-rule damage/block/heal accounting already exist. Recommendations must build on them, not request them again (`crafting/Capabilities.kt:93,339`; `crafting/Bench.kt:122`; `crafting/Interactions.kt:45`; `combat/Account.kt:55`). The lab measured real tactical niches: Bronze's rule changed early win share by +8.6 points when prepared and zero without; cheap metals therefore retain purpose. Quality caps and road access now create genuine spending constraints. Heartfire and deeds are both live Legendary routes.

The remaining explanation challenge is **conditional causality**, not missing descriptions: an “available” Mark interaction can lose its opportunity when an earlier ally spends the Mark. The code correctly avoids promising an outcome, but whether players can predict it is untested. Bench scenarios do not reproduce their actual company or encounter.

**Emotional engagement:** the strongest stories connect a choice to a named consequence. In fresh seed 810001, Overflow Adaptive armed Hakon/Corvin, spoiled Shield Wall on day 3, held day 5 through overheal-to-Guard, opened Deep Road on day 8 and secured the charter on day 25. Greedy-diver on the same seed lost/captured successive named recruits and fell on day 20. Rescue, inherited weapons, a nemesis carrying one's loan, earned weapon titles and successful retirement provide strong material for attachment.

Classic agency is less reliable: selection of visits, autonomous activity and equipping sit between forging for somebody and seeing that weapon matter. Even Expert's delivered siege-preparation weapons were wielded in the relevant field result only **49.5%** of the time. The buyer can retain a better weapon; the player does not directly equip or command them (`market/Market.kt:378`; `heroes/Heroes.kt:39`). Existing field comparisons and truthful histories help, but “I made this for you” is not always a completed emotional arc. Hypothesis: fewer explicit named projects would beat more headlines. No claim that players failed to notice current reports has been tested.

## 5. Five improvements, in priority order

All are **H**, not implemented proposals. Keep automatic combat and autonomous classic heroes. Cost/risk are relative engineering judgments, not schedules.

| Priority | Change to existing systems | Enjoyment gain | Cost / risk |
|---|---|---|---|
| 1 | **Give existing non-sabotage jobs preparation-specific leverage.** A successful Hunt/Escort/Supplier job should sometimes change a specific part of the coming field or fund the precise counter, while leaving another danger intact. Sabotage remains the broad denial route; a build designed to exploit the remaining field becomes a credible alternative. Counters already exist: improve their strategic return and tradeoffs, not their count. | Very high: more valid answers to the central recurring problem. Addresses 1, 5. | Medium / high balance risk |
| 2 | **Make wall work an opportunity-costed recovery plan.** Replace merely increasing E with a clearly bounded reserve earned by the existing wall job, usable against the next breach, competing with mission income/preparation and expiring after that siege. Define whether it protects integrity or rebuilds afterward; do not silently stack both. Preserve shared ordinary recovery and no gold-repair button. | High: an alternative to prevention and a deliberate comeback, rather than waiting longer to lose. Addresses 2. | Medium / medium risk |
| 3 | **Merge ordinary wants and ordinary commissions into fewer named service decisions.** A voiced want remains free information until explicitly accepted into the existing commission workflow; then its material/price feasibility and named purpose are clear. Retain special commissions, autonomous equipping and the current least-sufficient handover rule. Focus the daily job on meeting an actual need rather than generating shelf surplus; track its existing delivery/field aftermath as one story. | High: less administration, clearer classic agency and emotional stakes. Addresses 3, 4, 10. | Medium / medium risk |
| 4 | **Let completed knowledge bound first-discovery frustration.** After the clue ladder is complete, a small fixed number of eligible failures guarantees the first signature; wrong inputs do not advance it. Retain known-recipe variance and Heartfire's distinct risk. Reuse Notebook/ForgeLearning to distinguish a rule lacking an enabler from an enabled rule that never got its turn; do not build another combat report. Evaluate Anvil Lore against this research role rather than raw survival. | High for crafting-focused players: reasoning produces dependable progress, while ingredients still cost something. Addresses 5, 6, 9. | Medium / medium save/balance risk |
| 5 | **Make taking a higher charter rank choose an operating constraint.** Reuse one existing law/preparation/objective pattern with the rank, advertised before commitment; success should require reconsidering the established company. Keep retirement as a complete success. Prefer this to adding more flat enemy scaling; reassess opening/legacy benefits by the plans they enable. | Medium–high: continuation changes decisions instead of only extending the run. Addresses 7, 8, 9. | Medium / medium–high risk |

### Simplify, combine or retire

- Combine the ordinary want/commission channels as above; preserve extraordinary orders and their consequences.
- Replace the small wall-work exception with one understandable job-specific protection rule; do not add a second repair chore.
- Replace unbounded first-signature retries after complete knowledge; the clue ladder and 75% known-recipe behavior already serve distinct purposes.
- Consider consolidating flat starting-resource upgrades **only after** testing a buyer's intended playstyle. Keep Anvil Lore and Homing Steel: fresh specialist metrics establish separate value. Do not equalize every upgrade by survival days.
- Trade some flat rank escalation for existing rules that change the plan. Keep all six metals and four catalysts; synthetic averages are insufficient grounds to remove their niches.
- Do not add more visitors, relics or mission names to address low usage. Bellows/Crucible non-use by a bot is not evidence that humans dislike them. Existing presets have explicit coverage limitations.

## 6. Three redesigned decisions in play

1. **Day 12, Shield Wall due on 15.** The company already has spear/Bleed coverage. Sabotage deletes the preparation but uses two days; an existing Escort instead earns the ingredients for the weak backline while leaving shields in place. Choose the escort because this company can exploit that field, then see which rules justified it. A different company still chooses sabotage. The new part is the escort's authored counter-leverage, not spears, Bleed or the choice of mission.
2. **Day 18, 28 integrity, tired Warden.** In a prototype where wall work banks 12 protection, a 35-damage breach leaves 5 integrity instead of ending the run, holding other recovery constant. Hunt buys stronger gear; resting improves the healer. The player chooses insurance over those gains. It is spent once and expires; winning untouched means it was unnecessary. These numbers illustrate the proposal, not the current forecast or tuning.
3. **Day 8, all clues known, two eligible failures, one catalyst left.** Prototype rule: the third eligible attempt guarantees the first signature. The smith chooses between completing that discovery and meeting a named customer's accepted job before its deadline. Reasoning now produces dependable progress, but still competes for scarce materials. The weapon's later fight remains automatic.

## 7. Acceptance criteria and regression scope

These are proposed evaluation gates, not reasons to force every seed into a desired success rate. Start at n=32; expand to at least 256 held-out paired seeds only for plausible candidates. Freeze policies/versions and separate charters, openings, rank and account state. Report intervals and discordant wins, including null results.

| Change | Measurable success | Appropriate regression coverage |
|---|---|---|
| Mission alternatives | At least two distinct non-sabotage approaches are within 10 points of Adaptive charter success on held-out seeds; each beats sabotage in a declared matchup and loses in another. No universal “take this job” replacement. | Extend `GuildRunTest` / `GuildStoriesTest`: selected preparation component changes exactly once, others remain; party/return timing and cancellation fees remain true. Add dedicated alternative policies, not retuned Adaptive under another name. |
| Recovery | A recovery policy improves charter success as well as survival; neither prevention nor recovery dominates every low-integrity scenario. Measure resources/mission rewards sacrificed. | Extend `ReviewFixesTest` and siege tests: reserve earned/spent/expired once, failed job grants nothing, saving/reloading does not duplicate it, no healing above max, ordinary recovery cap unchanged. |
| Named service | In a 6–8-player formative test, at least 75% can predict a job's material cost, recipient and likely use, then name one consequential service decision. Fewer surplus crafts per completed service without reducing viable free experimentation. | Extend `WantsTest`, `CommissionRulesTest`, `MarketAndOwnershipTest`: explicit accept/decline, no double orders, deadline settlement once, least-sufficient blade, autonomous equip remains conditional. Track conversion by band and costs, not just total sales. |
| Research/causality | Eligible failures after all clues are bounded by the chosen cap; at least 75% of testers can explain why one recipe's advertised interaction did or did not fire. Compare trials required and ingredients spent, not just discovery count. | Extend `SignatureAndTechniqueTest`, `DeepForgingTest` and migration fixtures: wrong inputs earn no eligible progress, no duplicated discovery, first versus known recipe paths, real witness versus bench, Mark consumed by an earlier ally, reload parity. |
| Rank/legacy identity | By the first post-charter siege, at least two of loan/party/posture/mission choices differ from the pre-rank plan. A specialist legacy purchase has a declared, measured benefit on its intended policy; additional generic throughput alone is not success. | Extend `GuildStoriesTest` / `LegacyTracksTest`: rank only after charter, its condition persists correctly, retirement remains valid, old saves migrate. Add rank-using bots; current `--playOn` does not test ranks. |

Retain deterministic RNG isolation, ownership invariants, claim-once rewards, valid content, classic/guild separation and saved replay fidelity. Add no UI RNG or manual combat. The current suites already cover many of these; extend them around new behavior rather than mirroring implementation with trivial tests.

## 8. Reproduction and audit limits

All invocations used `./gradlew.bat <task> --console=plain --args="<arguments>"` from the repository root. Seed base was **810001** throughout. Logs are UTF-16 PowerShell redirections under ignored `core/build/codex-review/`; JSON paths supplied to the simulator are relative to the `core` project directory.

| Task / evidence file | Arguments beyond common seed |
|---|---|
| `:core:guildsim`, `guild-explore.txt` | `--runs 24 --policy PASSIVE,NOVICE,ADAPTIVE,SYNERGY,NO_SABOTAGE,IGNORE_COUNTER,GREEDY_DIVER,REPEAT_RECIPE,HIGHEST_STAT,RECOVERY_AWARE --pair ADAPTIVE,NO_SABOTAGE+ADAPTIVE,IGNORE_COUNTER+ADAPTIVE,REPEAT_RECIPE` |
| `:core:guildsim`, `guild-192.txt` | Same policy list, `--runs 192`; add pair `ADAPTIVE,RECOVERY_AWARE` with `+` |
| `:core:simulate`, `classic-192.txt/.json` | `--runs 192 --policy PASSIVE,NOVICE,BALANCED_FAIR,BALANCED_EXPENSIVE,BALANCED_ACTIVE,SIEGE_PREP,EXPERT,ADVANCED_SMITH,SIGNATURE_PURSUIT,RECKLESS_FAIR,FREE_LISTINGS --customers --depth --noImpact --json build/codex-review/classic-192.json` |
| `:core:guildsim`, `guild-licensed.txt`, `guild-honest.txt` | `--runs 96 --policy ADAPTIVE,SYNERGY,NO_SABOTAGE,RECOVERY_AWARE --charter licensed_bad_ideas` or `honest_business`, `--pair ADAPTIVE,NO_SABOTAGE` |
| `:core:guildsim`, `guild-alternatives.txt` | `--runs 96 --policy ADAPTIVE,DEFENCE_FIRST,LEGEND_SEEKER,SUPPLY_FARMER,SHOP_FOCUSED --pair ADAPTIVE,LEGEND_SEEKER+ADAPTIVE,SUPPLY_FARMER` |
| `:core:guildsim`, `guild-wall1.txt`, `guild-wall3.txt` | `--runs 96 --policy ADAPTIVE,RECOVERY_AWARE --pair ADAPTIVE,RECOVERY_AWARE`; second adds `--set wallWorkExtraRecovery=3` (in-memory override only) |
| `:core:guildsim`, `guild-plans.txt`, `guild-drawbacks.txt` | `--runs 192 --planPair ADAPTIVE` or `--drawbackPair ADAPTIVE` |
| `:core:guildsim`, `drawback-licensed.txt`, `drawback-honest.txt` | `--runs 96 --drawbackPair ADAPTIVE --charter licensed_bad_ideas` or `honest_business` |
| `:core:guildsim`, `guild-playon.txt` | `--runs 96 --policy ADAPTIVE,SYNERGY,NO_SABOTAGE,GREEDY_DIVER --horizon 60 --playOn x --pair ADAPTIVE,NO_SABOTAGE` |
| `:core:simulate`, `legacy-96.txt/.json`, `legacy-signatures.txt/.json` | `--runs 96 --policy P --impactPolicy P --yardsticks --legends --json build/codex-review/FILE.json`; P=`BALANCED_FAIR` then `SIGNATURE_PURSUIT`; FILE=`legacy-96` then `legacy-signatures` |
| `:core:combat`, `forge-lab.txt`, `forge-confirm.txt` | `lab seeds=810001..810001`; then `lab seeds=810002..810003 crafts=2` (no `--seed` option). 622,080 total synthetic fights, including older-composition controls; 466,560 current-composition fights across only three strike seeds. |
| `:core:guildsim`, `transcripts.txt` | `--policy ADAPTIVE,GREEDY_DIVER --transcript x` |
| `:core:test`, `core-test-fresh.txt` | `--rerun-tasks --console=plain`, no `--args` |

The initial n=24 classic computation completed but failed exporting JSON to the wrong relative path; the corrected n=192 rerun completed successfully. Initial `:core:test` was up-to-date. The fresh `--rerun-tasks` completed successfully in 5m 47s: **662 tests, zero failures/errors/skips**, 82 XML suites. No Android tests or generators were run.

Interpretation limits: same seeds do not hold subsequent draws constant when commands differ. Policy comparisons are not pure causal ablations. Run samples are 96/192, not thousands of independent mission attempts. Conditional siege rates have survivor bias. Lab scores weight enemy health removed and party health remaining; they omit whole-run economics. Opening cells are exploratory; non-significance does not establish equivalence. The misleading CLI label `signature discoveries/run` counts signature-bearing weapons remaining in state (`sim/Simulator.kt:431`); the separate `signatures first found/run` metric counts first discoveries. Those quantities are distinguished above.

Documentation was cross-checked against `Tiny_Blacksmith_GDD.md`, `docs/DECISIONS.md`, `docs/PROGRESS.md`, `docs/REVIEW_2026-10-14.md`, its gameplay/tests appendices, `docs/REVIEW_2026-10-14_REPORT.md`, `docs/DEEP_FORGING_LEDGER.md`, `docs/RUN_VARIETY_LEDGER.md` and the Guild evolution specification. Older balance-13/14/15 figures were not reused as current measurements. Menu/siege presentation redesign is separate work and not evaluated as a gameplay intervention here.
