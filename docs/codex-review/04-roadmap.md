# Tiny Blacksmith: evolution roadmap

15 October 2026. Planning only. Read both [gameplay](02-gameplay.md) and [UX](03-ux.md) audits completely, then selectively checked implementation, tests, project guidance and the main-menu specification.

**Baseline:** local `main`, HEAD `406f26ce2d446d8984e86da0af3846a18ebfedd2`, identical to the audited revision. No newer commits or tracked working-tree changes were present. Pre-existing untracked paths were `build-output/` and `docs/codex-review/`. Code confirms rules 7, balance 16 and save schema 7. No branches changed, builds, tests or simulations run for this roadmap. The gameplay audit's 662 passing tests and the UX audit's APK/emulator checks are previous evidence, not fresh verification here. Only this file is authored.

Source shorthand below: **C/** = `core/src/main/kotlin/com/tinyblacksmith/core/`, **U/** = `app/src/main/java/com/example/blacksmithproject/ui/`. Test names refer to the existing core or app suites. Main-menu work in `docs/superpowers/*/2026-10-14-main-menu-redesign*` is excluded, including title, account destinations and legacy-menu restructuring.

## 1. Executive diagnosis

### Three fundamental weaknesses

1. **Activity is more visible than opportunity cost.** Classic production exceeds meaningful demand. Guild's one party and short siege window favor broad prevention. The UI reinforces this by promoting an empty shelf or unplanned quest as unfinished work. Players need to choose what to accomplish this cycle, including keeping people home, rather than clear every prompt.
2. **Forge choices are separated from conditions and purpose.** Comparison requires repeated edits and memory. Guild effects depend on enablers/action order, while Classic uses another combat model. Named recommendations can lose their recipient en route to Forge. Experimentation has an unclear question and creates more stock to administer.
3. **Consequences do not reliably close the learning loop.** Siege receipts repeat damage before useful contributions. Discovery becomes eligible retries after learning stops. Post-charter pressure rises without reliably changing the plan. Weak feedback encourages recipe repetition and makes the next cycle less interesting.

**Direction:** clarify the existing loop, then diversify siege responses and bound solved research. First deliver: recognize the threat, forge for a purpose, observe a contribution. Balance-first would change choices players still struggle to compare. Content-first would increase administration.

Evidence supports preparation and several working strategies. It does not establish mandatory sabotage, universally useless ingredients, or player enjoyment. Adaptive beat No-sabotage by 15.1 percentage points on 192 paired seeds, with an approximate paired interval of +6.6 to +23.6. Repair expansion reduced losses without improving charter wins in its 96-seed comparison. Policy bundles, survivor bias, small opening cells and the synthetic forge lab limit stronger conclusions. Ordinary continuation bots never raised rank. These findings justify focused experiments, not blanket nerfs.

## 2. Evaluation of previous recommendations

| Proposal | Decision | Rationale, dependency and downside |
|---|---|---|
| Keep ingredient alternatives open and show recipe differences | **ACCEPT** | Directly removes working-memory cost. Compare known facts against the draft on opening the tray. No best-recipe score, hidden recipe leakage or outcome prediction. |
| Expose conditional effects and shorten bench explanations | **MODIFY** | Lead with the changed prerequisite and one recorded chain. Capabilities establish possible support, not execution order. Bench remains synthetic. Do not build an actual-company siege predictor. |
| Carry named purpose and matching stock through Forge | **ACCEPT** | Reuse `forgeFor`, commission fit/pick and existing briefs. Wants retain recipient and budget as context only. Availability is rechecked, not reserved by presentation. |
| Merge ordinary wants into accepted commissions | **DEFER** | A want checks budget/power, while a commission checks quality, deadline and settlement. Conversion could create guaranteed demand, duplicate jobs and alter pricing. Consolidate presentation now. Revisit only if stock-first guidance fails in human testing. |
| Give non-sabotage quests preparation-specific value | **MODIFY** | First tailor existing material rewards toward a useful counter, leaving the preparation intact. Prefer this to another field-removal mechanic that becomes disguised sabotage. Validate full-run costs and actual weapon use. |
| Make wall work a strategic reserve | **MODIFY** | Prototype automatic, nonstacking protection for one siege, replacing its direct repair exception. No second claim button or repair currency. This changes owner decision B and requires approval plus evidence of offensive recovery, not merely longer survival. |
| Bound first-signature frustration after complete clues | **MODIFY** | Count only post-quality eligible failures after knowledge was already complete. Keep normal early discovery and known-recipe variance. Persist progress with knowledge. Exact bound and Anvil Lore treatment require a decision. |
| Distinguish complete clues, an eligible miss and discovery | **ACCEPT** | UI can do this before changing odds. A draft can match known inputs while quality is still uncertain. Journal differences alone cannot establish eligibility. |
| Rank-based operating constraints | **DEFER** | Conditional Wave 3 pilot, after strategic alternatives work. Test one existing law pattern with rank-using policies. Do not extend the current stat ladder or author a challenge catalog now. |
| One siege outcome, receipt and useful contribution | **ACCEPT** | Reuse typed accounting and observations. Recorded contribution is not counterfactual proof. Preserve losses, raw narration and legacy-record fallback. |
| Bring Guild readiness/counters forward and align offers | **ACCEPT** | Extend existing tabs and siege header. Compare cost, return, risk and reward in a common order. Filled places are not a victory forecast. |
| Party order controls, equipment access and pinned confirmation | **MODIFY** | Add explicit reordering first. Equipment links ship only with preserved party draft and revalidation after loans/reservations. No expanded roster dashboard. |
| Contextual daily priorities and optional-job wording | **MODIFY** | Change the shared advice policy deliberately, including Tomorrow. Use Guild readiness facts in Guild, Classic outlook in Classic. Resolve real deadlines before optional administration, without making siege preparation compulsory. |
| Mode before slots, risk by action, responsive chrome | **ACCEPT** | Local hierarchy changes, preserving Quick/Advanced semantics, overwork, stock and Heartfire confirmation. Reduce art before truncating decisions. |
| Gazette links, consequential histories, readable paused replay | **ACCEPT, later** | Small extensions to existing records and disclosures. Missing historical subjects retain readable snapshots. No new story generator, summary screen or replay engine. |
| Remove niche metals/catalysts or equalize legacy by survival | **REJECT** | Their specialist value is demonstrated or insufficiently measured. Keep Anvil Lore and Homing Steel identities. More throughput is not necessarily useful demand. |
| Rebalance broad ingredients, premium pricing and opening costs now | **DEFER** | Lab coverage is not human choice or whole-run dominance. Opening samples are inconclusive. Evaluate targeted niches and premium-versus-relationship policies after the clearer loop. Do not restore the historical pricing cliff or add recurring opening taxes. |
| Unify Classic with Guild combat or add more jobs/resources | **REJECT for this roadmap** | High regression scope and feature growth. Explain the two payoff models honestly and improve the systems already present. |

## 3. Five highest-impact changes

These are the complete investment priorities. Rank experimentation is a conditional extension of change 4, not a sixth major initiative.

### 1. A purposeful workbench that supports comparison

**Experience/benefit:** check fitting stock for a named purpose, then compare materials in place. Classic shows cost, known affinities, quality/rarity constraints and elemental fit. Guild adds rules/prerequisites. Free experimentation remains. This reduces purposeless replacement crafts and gives experiments a question.

**Reuse:** workbench, commission fit/pick and capabilities in T2's files below, plus `C/crafting/Bench.kt`.

**Tradeoff:** comparison baseline/purpose state replaces reopening and repeated teaching. Medium effort/UI-state risk, no rules dependency. **Acceptance:** stable free A/B/A comparison, restored purpose, truthful stock fit and blockers. **Human test:** explain a tradeoff, find stock and abandon an unneeded craft comfortably.

### 2. Siege planning in the existing daily and Guild views

**Experience/benefit:** urgent readiness/deadlines precede optional jobs. Preparation and availability gaps precede the roster. Comparable offers and explicit party order make staying home, earning supplies and prevention easier to weigh.

**Reuse:** existing forecasts, advice, Guild tabs and party sheet in T1/T4's files.

**Tradeoff:** priority cases/draft continuity replace navigation and obligation-like prompts. Small-to-medium effort, medium timing/state risk, no balance dependency. **Acceptance:** mode-correct advice, exact returns, readable urgent actions, displayed order equals submitted order. **Human test:** explain departure's sacrifice and deliberately choose no quest.

### 3. One truthful payoff connecting the weapon to the siege

**Experience/benefit:** outcome, one contribution, one receipt, then replay/detail. Named damage, effective healing, Guard absorption or state application closes the forge's learning/attachment loop.

**Reuse:** `C/combat/Account.kt`, replay/timeline and T3's day/report files.

**Tradeoff:** stable observation selection replaces repeated narration. Small-to-medium effort, medium fidelity risk, existing records suffice. **Acceptance:** exact accounting, shared attribution, distinct downed/dead, old-record fallback and RNG-free viewing. **Human test:** recall a contribution without treating it as proof of victory.

### 4. Three credible siege responses with different sacrifices

**Experience/benefit:** prevent broadly through sabotage, equip a counter while accepting the preparation, or sacrifice income to protect the forge. Recovery must preserve a route to beating the warlord, creating strategy from current resources.

**Reuse:** existing rewards, loans, wall work and siege damage in T5/T6's files and `C/sim/GuildSim.kt`.

**Tradeoff:** reward mapping and approved expiring protection replace the special wall-repair rule. Medium effort, high balance/save risk. Depends on planning/payoff and isolated simulations. **Acceptance:** situational policy advantages, no universal job, recovery improves charter success and survival. **Human test:** distinct, understandable responses, including unused insurance.

### 5. Research that stops asking a solved question indefinitely

**Experience/benefit:** distinguish incomplete knowledge, matching inputs, eligible misses and discovery. Fully informed research completes within an approved bound, retaining experimentation without an unlucky solved-recipe tail.

**Reuse:** Forge, Journal and `SignatureCatalog` in `C/crafting/Signatures.kt`, legacy merging and Notebook in T7's files.

**Tradeoff:** persistent progress/merging replaces unbounded retries. Medium effort, medium-to-high save/economy risk. UI states precede rules. **Acceptance:** eligibility enforced, bound holds, early luck/known-recipe variance preserved, no reload/claim duplication. **Human test:** earned completion and continuing Anvil Lore value.

## 4. Integrated siege and forging experience

### The five-day cycle

| Moment | Decision and feedback |
|---|---|
| **Morning after a siege / four days before the next** | Keep the shared header as the clock. Guild's committed field and preparation appear with existing counters. Classic retains its later faction-warning schedule. The aftermath names injuries, deaths, weapon wear and changed access. No information is revealed before its existing reveal point. |
| **Early preparation days** | Choose prevention, counter-equipment or recovery according to the company and forge health. Compare available quests by fee, return morning, danger and reward. A material reward is useful only if it arrives before a remaining forging opportunity. Deep Road acceleration still competes with preparation and still opens automatically after siege two. |
| **Last useful departure day** | Show the actual return morning and absence from siege night. Returning the following morning is too late, even if the last stage resolves before the siege. Display wound/health uncertainty separately from nominal return. Preserve banked stage rewards and default Return at delve checkpoints. |
| **Eve and siege day** | Lead with unresolved availability or preparation gaps. Keep full defenders/battlefield expandable nearby. Reserving, loans, party order and resting are the existing controls. In Classic, the player supplies autonomous customers, not assigned defenders. No quest planned can be an intentional completed plan. End Day remains available with specific consequences visible. |
| **Resolution and playback** | The engine still advances quests and autonomous activity before the siege, then performs ordinary recovery afterward. Watch/outer approach precedes Guild combat. Replay reads the stored fight only. Short/full playback, pause, actor/event inspection and reduced motion remain. Full names/rule chains are readable on inspection, rather than forced into every animation label. |
| **Victory, costly hold or breach** | Show the stored verdict, one recorded contribution, forge damage and current health separately, then actual injuries/deaths. A hold is not automatically undamaged, and going down is not automatically death. Protection, if implemented, changes forge damage, not the fight verdict or a rout casualty. At zero health the run ends before later ordinary repair can rescue it. |
| **Next morning** | Acknowledge the day before opening a live Wall/hero/weapon action. Reservations clear as today. Repair/replace a worn loan, keep an injured defender home or address an actual rescue/recovery opportunity. Gazette, bonds, nemeses and weapon provenance carry the story. Charter victory offers legitimate retirement or continuation. No automatic revenge objective. |

### Forging within that cycle

1. **Choose a question.** An accepted commission carries its ID and requirements into Forge. A want carries the named budget/power context with no purchase promise. An optional Guild recipient can be a drafting context, with loans still explicit. Check current matching stock before spending materials. Never auto-accept, auto-loan or reserve ordinary stock.
2. **Compare known differences.** Quick/Advanced precedes slots. Opening a tray fixes its baseline. Selecting a usable alternative updates the draft and the comparison in place. Done closes the tray. Blocked alternatives remain inspectable without selecting an impossible command. Compare material usage, energy/overwork, known affinities and changed rules or requirements. An incomplete recipe shows only the facts it can support. No universal score or numeric hidden odds.
3. **Test an interaction.** Guild outlook distinguishes a guaranteed weapon rule from a conditional trigger and a rolled possibility. Practice bench shows its synthetic setup and one prerequisite → trigger → measured-effect chain. An available Mark provider does not guarantee this wielder consumes the Mark. Other chains and full text remain disclosed. Neither bench nor comparison writes learned/witnessed evidence or spends gameplay RNG.
4. **Interpret the forge.** Before crafting, say that known inputs match while quality remains rolled. Afterward, inspect the actual command and weapon quality against the known recipe conditions. Separate a new clue, an ineligible result, an eligible miss and a discovery. Do not infer the latter states just because the journal did not change. Hidden recipes remain hidden. Use existing verbal chance bands until the bounded rule ships.
5. **Observe real use.** The result prioritizes the new finding and destination. A Guild loan is a real player choice. A Classic sale or fulfilled commission does not guarantee equipping or attendance. Later reporting names actual wielders and recorded use, including truthful non-use. Notebook distinguishes learned, suggested and witnessed. A disappointing outcome should suggest a testable question, not automatically another identical craft.

## 5. Simplification decisions

| Treatment | Concrete decision |
|---|---|
| **Remove** | Duplicate board doorway when the visible header already opens it, repeated expanded siege damage/Charge narration, full rarity teaching on every result, decorative frames around routine facts and duty-like wording for optional quests. Preserve raw records behind disclosure. |
| **Consolidate** | Named purpose, requirements and existing fitting stock in the current Forge/board flow. Align offer facts. Share outcome/contribution selection between the day card and report. Keep wants and accepted obligations semantically distinct. |
| **Hide until relevant** | Full ingredient rule lists, secondary bench chains, exhaustive histories, yesterday's routine Guild results and expanded Today text. Never hide costs, deadlines, uncertainty, absence, overwork or irreversible consequences before confirmation. |
| **Automate safely** | Refresh comparisons, calculate return warnings and select a stable recorded contribution. Approved protection consumes/expires automatically. Preserve existing automatic defender fallback, home rest/training and commission handover. No auto-buy, auto-forge, auto-price, auto-accept or auto-deploy. |
| **Shorten** | Keep ingredient tray open, pin party confirmation with its fee/timing, add explicit reorder and contextual equipment access with draft restoration. Reduce art/chrome before reducing text size. |

Do not add currencies, daily checklists, screens, missions, materials or recipe catalogs. Do not add bulk cleanup: storage already has it. Do not remove all surplus through guaranteed buyers. Free experimentation is part of the game.

## 6. Three-wave roadmap

### Priority and sequencing

Ratings are design judgments, not estimated percentage gains. Task order favors a playable loop and low dependency risk.

| Order | Task | Player benefit / depth / load removed | Effort / regression risk | Dependency |
|---|---|---|---|---|
| 1 | T1 Contextual readiness | High / exposes existing depth / high | Small / low–medium | Existing forecasts |
| 2 | T2 Purposeful comparison | Very high / exposes existing depth / high | Medium / medium | Typed purpose and draft model |
| 3 | T3 Siege payoff | High / improves learning / high | Small–medium / medium | Existing stored accounting |
| 4 | T4 Complete Guild comparison flow | High / exposes existing depth / high | Medium / medium | T1; sheet restoration |
| 5 | T5 Counter-equipment alternative | High / high / moderate | Medium / high | T1–T4; balance candidate |
| 6 | T7 Bounded research | High for experimenters / moderate / high | Medium / medium–high | T2; owner decision |
| 7 | T6 Wall protection experiment | Potentially high / high if viable / moderate | Medium / high | T5 measured separately; owner B |
| 8 | T9 Record polish | Moderate / learning / moderate | Small–medium / low–medium | T3 |
| 9 | T8 One rank challenge pilot | Uncertain / potentially high / little | Medium–high / high | Improved core loop; rank policies |

**Common verification contract for future implementation:** UI work must leave command outcomes, RNG and save semantics unchanged. Run focused app/core tests named below, then `:core:test`, `:app:testDebugUnitTest` and `:app:assembleDebug` once for the integrated wave. Use targeted instrumentation for navigation, persistence and layout. Device checks cover Classic/Guild, narrow portrait and 600 dp, font 1.0/1.5/2.0, reduced motion and TalkBack for changed controls. Verify deadlines, CTAs and important reminders remain fully accessible. No main-menu edits.

Rule changes keep immutable state and commands in core, numbers in nested `BalanceConfig`, stable IDs/content validation, ownership invariants, claim-once rewards and classic/guild separation. Decide rules/balance/content version changes explicitly. Add defaulted save fields or migrations as appropriate. A schema bump requires both run and legacy migration tables. Do not rewrite historical fights or upgrade old weapon compositions silently. Save/reload, duplicate-command and old-fixture tests are mandatory where semantics change.

**Simulation gate for Wave 2:** reuse the audit as baseline. Screen only plausible candidates with 32 paired seeds, then freeze policies/configuration and confirm on at least 256 held-out paired seeds. Re-run the unchanged control on the same seeds/build. Separate charter/opening/account state and report discordant wins, paired intervals, deaths/captures, forge health, charter success, cost and actual equipment use. Same initial seeds do not guarantee identical downstream draws. Do not pool mission attempts as independent runs. Report null results. Bots validate mechanics and strategic viability, never enjoyment.

### Wave 1: understand, prepare, forge, see the result

**T1. Contextual readiness and honest daily priorities.** Change `C/shopday/Advice.kt`, `U/ShopUi.kt`, `GuildPlanUi.kt`, `GuildPanel.kt` and the Wall portion of `GuildParts.kt`. Preserve first-day/blessing gates. Rank same-day irreversible deadlines and concrete siege gaps above optional offers/restocking. Accepted work due now remains visible beside a competing siege action. Use existing Guild forecast facts and Classic outlook separately. Reflow Today and move counter/readiness content ahead of repeated identity/full roster. No owner rule decision. Risk: shared Shop/Tomorrow disagreement, reveal leaks and accidental Guild scalar odds. Gates: `AdviceTest`, `ShopModelTest`, `GuildPlanUiTest`, `GuildUiTest`, `SiegeHeaderTest`, relevant `LayoutMatrixTest` cases. **Done:** each priority fixture has an actionable truthful target, optional work is optional, large text has no truncated urgent reminder and same command sequences produce unchanged states.

**T2. Purposeful, in-place Forge comparison.** Change `U/ForgePanel.kt`, `ForgeUi.kt`, `ShopUi.kt`, `CommissionBoard.kt`, `WorkshopScreen.kt` and `GameViewModel.kt`, reading existing `C/market/Commissions.kt` and forge outlook/capabilities. Preserve commission IDs through leads, retain named want context, surface fitting stock, fix comparison baseline, move mode/risk and distinguish knowledge from actual eligibility in results. If eligibility needs a richer UI result, derive it from accepted forge command/weapon facts, not an extra roll or parsed prose. No conversion of wants into orders. Risk: stale targets, SavedState compatibility, Quick/Advanced residue and knowledge leaks. Gates: `ForgeShortcutTest`, `ForgeBrowsingTest`, `ForgeWorkbenchModelTest`, `ForgeLearningTest`, `WantsModelTest`, `SavedStateTest`, targeted `ForgeHintTest` and navigation/process-recreation checks. **Done:** section 3 change 1 criteria pass in both modes, including expired targets, empty stock, incomplete drafts and fully clued but undiscovered signatures.

**T3. Outcome and recorded contribution before transcript.** Change `U/shopday/ShopDayUi.kt`, `shopday/DayCards.kt` and `replay/SiegeReport.kt`. Read `C/combat/Account.kt`; do not redesign accounting. Select one existing observation or truthful typed weapon share with stable tie-breaking, preserving support contributions. Give typed fields ownership of outcome, costs and casualties. Fold original narrative/notes rather than string-deduplicating them. Classic uses its actual scalar evidence, not Guild rule shares. Risk: double counts, survivor/death confusion and present-state leakage into old records. Gates: `SiegeOutcomeTest`, `ShopDayUiTest`, `FightReplayTest`, `ShopDayScreenTest`, `ShopDayPersistenceTest`. Device: held, costly hold, breach, rout, defeat and unsupported old fight. **Done:** one expanded receipt, correct contribution when available, honest fallback when absent, and unchanged saved replay/RNG before and after viewing.

**T4. Finish Guild quest comparison and party continuity.** Change `U/GuildUi.kt`, `GuildParts.kt`, `ContractSheet.kt`, existing detail-sheet routing and the ViewModel's UI state. Align fee, return morning, danger, rewards and siege absence on offers. Pin confirmation and timing while roster/details scroll. Add reorder controls. Reuse hero/loan sheets only after draft restoration works. Depends on T1 and existing plan/refund commands, no new gameplay decision. Risk: sheet replacement loses draft or invalid members survive equipment/reservation changes. Gates: `GuildUiTest`, `GuildPlanUiTest`, targeted `NavigationFlowTest` plus plan/change/cancel and fee-refund assertions in `GuildRunTest`. Device: long roster, reordered party, equipment round trip and large text. **Done:** submitted order equals displayed order, no duplicate fee, invalid selections are explained, no extra destination.

**Wave exit:** play T1 → T2 → siege → T3 before broad polish; then evaluate T4. In a small 6–8-player formative comparison, record task completion, taps/backtracking and explanation accuracy. A provisional gate is at least three quarters independently identifying a purpose, one recipe tradeoff, siege availability and one recorded contribution. Check deliberate inactivity and autonomous Classic buyers. A small sample guides revision; it cannot prove retention or fun.

### Wave 2: different responses and dependable research

**T5. One counter-equipment reward prototype.** Start with one existing scouted preparation and its already-posted opportunity, not several quest families. For example, test a Sun Ash material reward against Grave mist, where Sun is an existing counter. Replace an equivalent portion of the material payout rather than add free total value. Keep the preparation and sabotage unchanged. Use `C/content/GuildContent.kt`, `guild/Missions.kt`, `model/Guild.kt` as needed for stored offer rewards, `config/BalanceConfig.kt`, `sim/GuildSim.kt` and `U/GuildUi.kt`. Owner approves the mapping and supplier-access exception, if any. Risk: bypassing Deep Road, universally profitable farming or changing an offer after acceptance. Gates: stored offer survives reload unchanged, no duplicate stage reward, no extra RNG from UI, materials arrive with time to forge, and `GuildRunTest`/`DeepRoadTest`/content validation pass. Compare fixed sabotage, counter-equipment and existing income policies under the simulation contract. **Done:** counter-equipment has a credible full-run advantage in its declared circumstance and a cost in another, without weakening meaningful sabotage. Expand to a second existing preparation only after that result. Human test whether the counter is understandable and actually chosen.

**T6. Replace wall-work repair with one-cycle protection, conditionally.** After T5's results are isolated, prototype the existing always-successful wall job earning one nonstacking reserve tied to the next siege day. Repetition cannot increase it. It automatically absorbs up to the configured amount of that siege's forge damage, before the zero-health check, then expires even if unused. Preserve the militia reward and ordinary hero-driven recovery cap. Remove the job's direct integrity grant and extra recovery exception in this prototype. No post-breach rebuild or resurrection. Use `C/guild/Missions.kt`, `SiegeFight.kt`, `engine/GameEngine.kt`, `ResolutionContext.kt`, `model/Guild.kt`, `model/Model.kt`, `persistence/SaveCodec.kt` and matching UI projections. Owner decision B must be revised first. Risk: compulsory insurance, altered fatal ordering, stacking/reload exploits and cosmetic verdict changes. Gates: `ReviewFixesTest`, `SiegeWallTest`, `GuildRunTest`, migration/compatibility fixtures; canceled plans grant nothing, completed work grants once, expiry/absorption/cap and raw-versus-absorbed-versus-net reporting are exact. No device-only battle logic. **Done:** recovery improves charter prospects as well as survival on held-out comparisons, sacrifices real income/progression, and is not optimal in every scenario. Reject the prototype if it merely postpones defeat. Device: zero damage, costly hold, fatal breach with/without protection and reload on both sides of siege night.

**T7. Bound fully informed first discovery.** Recommended prototype: after knowledge was complete before the craft, the third eligible attempt succeeds at the latest. Eligible means exact required inputs/risk and actual rolled quality meeting the floor. Prior eligible failures count; an ineligible attempt neither advances nor erases them. Preserve the normal discovery roll on each eligible attempt, including the guaranteed attempt, so guarantee logic adds no new random draw. Success clears first-discovery progress; known signatures keep their existing path. Proposed scope: per signature, persistent with legacy knowledge, merged by maximum rather than sum. Discovered state overrides retry counters during merge; existing claim-once behavior prevents duplicate claims. Use `C/crafting/Forge.kt`, `Journal.kt`, `model/Model.kt`, `legacy/Legacy.kt`, `persistence/SaveCodec.kt`, `engine/Compatibility.kt`, `config/BalanceConfig.kt` and Notebook/Forge UI. Requires bound/persistence approval. Risk: quality rerolls mistaken for eligible attempts, lost progress on era claim, Anvil Lore devaluation and increased high-value supply. Gates: `SignatureAndTechniqueTest`, `DeepForgingTest`, `LegacyTracksTest`, `SaveFixtureTest`, `CompatibilityTest`, app learning tests and paired Signature-pursuit economy comparisons. **Done:** bounded outcomes hold across save/reload/era transition, old saves default safely to no retry credit, invalid crafts never count, and Anvil Lore's ingredient/attempt savings are reported. Device: complete clues without discovery, low-quality miss, eligible miss and first versus known success. Human test the sense of earned progress before finalizing tuning.

### Wave 3: earned continuation and selective polish

**T8. One rank challenge pilot, only if continuation still repeats.** Add rank-using policies first. Test rank 1 applying one existing quest-law pattern as a disclosed operating constraint, in exchange for some current flat scaling, rather than adding difficulty on top. Keep its quest-only scope explicit; do not silently apply quest laws to siege combat. Define precedence with ordinary laws so the two do not stack accidentally. One rank, one pattern, existing Charter surface, no challenge chooser/catalog. Use `C/content/GuildContent.kt`, `guild/Stories.kt`, `Missions.kt`, `GuildOps.kt`, `sim/GuildSim.kt` and `U/GuildParts.kt`. Requires owner choice after Wave 2; not a committed feature. Risk: irreversible rank traps, stacked laws and retroactive mid-mission changes. Gates: `GuildStoriesTest`, `GuildMigrationTest`, rank simulation with explicit rank uptake, save/load and retirement/claim tests. **Done:** by the next siege the constraint produces more than one viable plan and observable changes to at least two existing choices, without extra daily chores. Preview scope/effective timing on device, preserve active mission/fight snapshots. If it only adds punishment, defer shipping and retain successful retirement/endless play.

**T9. Make existing stories and replay detail easier to revisit.** In `U/Dialogs.kt`, `InfoPanels.kt`, `detail/HeroDetailSheet.kt`, `detail/ItemDetailSheet.kt` and `replay/SiegeReplay.kt`, link valid Gazette subjects, lead with the newest consequential history and expose full paused actor/event text. Read existing `C/gazette/Digest.kt` subject data, preserve its ranking/cap. Depends on T3; no balance or menu dependency. Risk: missing subjects, historical/live-state confusion and unreadable focus order. Gates: extend existing replay/detail/Gazette navigation coverage with removed/pruned subjects and old records. Device: TalkBack, reduced motion, large text and paused replay. **Done:** one existing subject route opens from a real event, absent subjects retain truthful text, histories remain accessible and playback consumes no gameplay RNG. Human test whether a remembered hero/weapon story can be found without another summary screen.

## 7. First three implementation-ready tasks

Hand these to a coding agent **in order**, reviewing each independently. Re-read HEAD/status first. These briefs authorize no implementation in the present planning session.

### First: T1, contextual siege readiness

- **Scope:** shared daily advice priorities, optional Guild opportunity wording, readable Today and readiness/counters before the Wall roster. Preserve header, tabs, direct actions and daily tab memory.
- **Exclude:** quest rewards, reserve rules, rank, new forecasts/screens, menus and mandatory-sabotage advice.
- **Required tests:** `AdviceTest`, `ShopModelTest`, `GuildPlanUiTest`, `GuildUiTest`, `SiegeHeaderTest`; targeted enlarged-layout/device checks and unchanged-state comparison for the same commands.
- **Acceptance:** Classic dire siege outranks optional stock/offers; Guild uses availability/preparation facts; true same-day commitments remain visible; no urgent reminder truncates; staying home is a valid choice. No owner rule approval needed.

### Second: T2, named-purpose ingredient comparison

- **Scope:** preserve IDs/context through the current lead/board paths, stock-first brief, open tray with stable baseline and focused changes, mode/risk hierarchy and truthful research states. Reuse workbench/outlook/capabilities and synthetic bench.
- **Exclude:** want-to-order conversion, recipe ranking, actual-encounter simulation, new discovery odds and automatic purchases/loans.
- **Required tests:** shortcut/browsing/workbench/learning/wants/SavedState suites; targeted Forge/navigation instrumentation, A/B/A, blocked stock, Classic/Guild and process recreation.
- **Acceptance:** purpose survives round trips or is explicitly invalidated, stock fit does not promise purchase, comparison costs nothing, hidden knowledge stays hidden, actual eligible misses are distinct from missing prerequisites. No owner rule approval needed.

### Third: T3, one recorded contribution beside the siege result

- **Scope:** stable typed observation/share selection, one expanded consequence receipt, folded original narrative and unchanged report/replay access.
- **Exclude:** new attribution algorithms, predicted prevented damage, battle recalculation, new animations and invented hero narratives.
- **Required tests:** outcome/day UI, replay and shop-day persistence suites; held/costly/breached/rout/old-record device fixtures, accounting totals and RNG/state equality after viewing.
- **Acceptance:** one useful factual contribution when available, no duplicated expanded damage receipt, no inferred deaths, no false weapon-use claim, full records retained and next action opens current state only after acknowledgement.

Together these deliver the first evaluable vertical slice. Do not batch Wave 2 rules into it.

## 8. Open owner decisions, maximum five

1. **Counter-reward scope:** approve a single preparation/opportunity mapping first. Recommend replacing equivalent material value, with no supplier-stage bypass unless explicitly approved. Decide the mapping and amount before T5.
2. **Wall-work identity:** approve replacing owner decision B's direct repair exception with automatic one-siege protection, preserving ordinary recovery and militia. Decide protection strength before T6; reject if charter viability does not improve.
3. **Research guarantee:** approve or amend the recommended third-eligible-attempt bound, persistent per signature across eras. Decide whether Anvil Lore keeps its current early-success benefit initially or needs a separate measured adjustment. Do not reset knowledge or expose hidden numeric odds.
4. **Service obligations:** recommend no mechanical want/commission merger this cycle. Authorize conversion only if Wave 1 testing still shows confusion/surplus and a later specification resolves budget/power versus quality, duplicates, deadlines and settlement.
5. **Continuation investment:** after Wave 2, choose whether to fund the one-rank law pilot. Recommend one disclosed existing law pattern replacing part of scaling, with no effect on an active mission, and retirement remaining complete success.
