# 10 — Adversarial second opinion (2026-10-14)

Lens: an adversarial review from a different model, over (a) correctness and determinism of the engine, (b) the Compose
UI for a first-time player, (c) game design against GDD sections 1–3 and the 2026-10-10 revision. Tree: `main` at a2e0392.

**What ran, plainly.** The `adversarial-review` skill was invoked. Its `brain/principles.md` does not exist in
`~/.claude/skills/adversarial-review` (only `references/reviewer-lenses.md`); the task's three lenses were used instead of
the skill's Skeptic/Architect/Minimalist. Codex CLI 0.137.0 is installed and logged in, but: the configured default model
`gpt-6-astra` is refused ("requires a newer version of Codex"), so `-m gpt-5.5` was used; the configured Windows sandbox
(`[windows] sandbox = "elevated"`) cannot spawn any shell command ("windows sandbox: spawn setup refresh", 12 failures per
reviewer, 0 successes), so the first three reviewers ran blind and were discarded. A second attempt with
`-s danger-full-access` (read-only instructions in the prompt; `git status` clean afterwards) let Codex read the tree.
Of those three: lens (a) returned 5 findings and lens (b) 6 (`scratchpad/review/lens_a.out.md`, `lens_b.out.md`);
lens (c) died at 244,838 tokens when Codex's remote compaction endpoint answered 404 (no output). **The engine and
design lenses below are my own adversarial pass, with Codex's (a) and (b) findings folded in and marked**; the three
engine defects were confirmed by probe tests run in a scratch git worktree (never in this tree, removed afterwards):
all three passed, i.e. the defects are real.

## Summary (10 lines)
1. No blocker. The engine is sound where it is tested; the three real defects found are in the guild layer's edges.
2. **Dismissing a member who is in today's planned party silently drops the plan and keeps the fee** (`GuildOps.forget`), with no event and no way to cancel for the refund. Probe 1 passes.
3. **Rank 3's "contracts stay on the board a day less" never applies**: `Missions.refreshBoard`'s local `post()` always passes its own expiry, so `generate`'s `offerLifeDelta` default is dead. Probe 2 passes; no test covers it.
4. **A sale at 0 gold earns the same reputation and loyalty as any sale** (`Market.kt:334`); `ToggleShelf` accepts price 0. The FREE_LISTINGS bot already outlives the fair-price smith by 2.5 days. Probe 3 passes. Owner decision, not a bug.
5. The "champion dies in the final siege → invariant softlock" hypothesis is **refuted**: `Battle.kill` strips the dead hero from `championIds` (Battle.kt:191).
6. End Day order matches GDD 3.2 with three documented, sensible reorderings; End Day is idempotent by a deterministic ID (`runId:dayN`), the save codec's two migration tables are complete for 1..6, save/load changes no draw.
7. UI: the title's charter stepper says "Charter 4 of 4 · No charter · The shop alone, as before" to a player for whom there is no "before"; the ended-run menu button says "Continue run".
8. UI: the owner's "thin text" is `bodySmall` 14 sp Regular in muted cream used for every description line (`Secondary`); nothing is Light/Thin. The debug scenario list is 39 flat rows of title + 73–283-character description in that style, with file I/O inside composition.
9. Design: the loop's decisions are real but front-loaded; a guild run adds eleven decisions of which four are default-correct; the day-20 charter goal is one line in the Guild region card, not visible on day 1 outside it.
10. Prior reviews: F01–F11 are fixed or addressed in the tree (details below); UI/UX causes 2 and 4 fixed, 5 and 9 still open, 10 partly.

## Ranked findings

| # | Sev | Lens | Defect | Action | Evidence |
|---|---|---|---|---|---|
| 1 | medium | a | Dismiss a member who is in the planned party: plan dropped, fee kept, no message, Cancel plan then rejected (narrow path, silent gold loss) | Refund in `forget` when it drops a plan (or reject `DismissHero` while planned); emit a line | `core/.../guild/GuildOps.kt:224-231`, `:277-289`; `Missions.kt:182-187`; probe 1 |
| 2 | medium | a | Rank 3 `offerLifeDelta` (−1 day) never reaches `expiresDay`; the rank's description promises it | Pass `expires = null` from `post()` and let `generate` compute it, or drop the field and the sentence; add a test | `Missions.kt:55` vs `:138`; `content/GuildContent.kt:471`; probe 2 |
| 3 | medium | c | Free listings (price 0) give full reputation/loyalty; FREE_LISTINGS outlives BALANCED_FAIR by +2.5 mean days | Owner decision: scale `saleReputation` by price band, or accept giveaways as a legitimate play | `Market.kt:334`; `GameEngine.kt:222`; FREE_LISTINGS = `priceFactor 0.0` (`sim/Simulator.kt:99`), `docs/DECISIONS.md` bots table; probe 3 |
| 4 | medium | b | Charter picker: "Charter 4 of 4", "No charter", "The shop alone, as before" — opaque on a fresh install | Two plain choices: "Classic shop" / "Guild charter", one line on what a guild adds | `ui/CharterPicker.kt:71-73`; `GameViewModel.kt:390-399` |
| 5 | medium | b | Ended run's main-menu primary reads "Continue run" | "See how the era ended" / "Claim legacy" | `ui/App.kt:115` |
| 6 | medium | b | The Forge's Supplies, Notebook and End day hide behind a "⋯" stock `DropdownMenu`; Risk is a stock dropdown too | Promote Supplies to a visible door; keep End Day's note visible | `ui/ForgePanel.kt:198-206`, `:505-528` |
| 7 | medium | b | A fourth engine rule restated in the app: `bellowsBlocked` re-implements `Relics.bellowsError` | Expose the engine's reason through the workbench model | `ui/ForgePanel.kt:619-628` vs `engine/Relics.kt:111-117`; DECISIONS "three rules restated" |
| 8 | medium | c | Day-20 charter milestone is one `progress` line inside the Guild "region" card; not on the Shop lead, not on day 1 | A standing "Charter: warlord on day 20" line on the threat strip of a guild run | `ui/GuildUi.kt:316`; `GuildPanel.kt:376-382` |
| 9 | low | b | Debug scenario picker: 39 flat rows, long muted descriptions, stock M3 buttons/dialog, JSON read in composition | Group by purpose, title + one 60-char line, day/charter chips; load the index off the main thread | `app/src/debug/.../ScenarioMenu.kt:55-115`; `assets/scenarios/index.json` (39 rows, desc 73–283 chars) |
| 10 | low | a | Siege jobs (sabotage + opportunity) count as ordinary offers, so on those mornings only `offersOnBoard − 2` regular contracts are drawn | State it on the board note, or draw them on top | `Missions.kt:90-98` |
| 11 | low | a | A refund of a plan that could not leave is booked as CONTRACT income | Use a refund kind or add gold directly | `Missions.kt:220-224` |
| 12 | low | b | Party-pick rows: the selected state is a gold digit vs "-", no checkbox glyph; "Choose a party" on the card, "Send" on the sheet | One verb ("Plan"), a visible check | `ui/ContractSheet.kt:85-94`, `:136-138`; `GuildPanel.kt:307,332` |
| 13 | low | a | `GuildOps.recruit` has a no-op `championIds` copy; `GuildMember.completed` map mixes archetype counts, "pair:" bonds and a `siegeJobs` day | Delete the line; split the map or namespace the keys | `GuildOps.kt:218`; `Missions.kt:74,107,324`; `Stories.kt:60-62` |
| 14 | low | a (Codex) | An ignored blessing offer never expires and is silently replaced by the next won siege's offer | Expire it with an event at the next siege, or say "replaced" | `GameEngine.kt:390-399,444`; `Battle.kt:373-379` |
| 15 | low | a (Codex) | Forge damage after a breach/time-out ignores enemy summons still standing (`!it.summoned`); the choice is not recorded anywhere | Record the ruling in DECISIONS, or count summons at a fraction | `SiegeFight.kt:219-221,300`; `CombatContent.kt:414,420` |
| 16 | low | a (Codex) | Downed non-member residents on a guild wall get `downedHealth` but no wound, event or field line | Add a FieldResult line for a downed resident | `SiegeFight.kt:281-292` |

## Details

### (a) Engine correctness and determinism

**1. Dismissing a planned member loses the fee (medium; the top finding because it is a silent loss of gold on a legitimate action).** `Missions.plan` takes the fee at planning (`Missions.kt:185`,
"replacing a plan never costs twice"), and `cancelPlan` returns it. But `GuildOps.dismiss` → `release` → `forget`
(`GuildOps.kt:286-289`) sets `planned = g.planned?.takeIf { heroId !in it.heroIds }`: the plan vanishes, nothing is
refunded, no event is emitted, and `CancelPlannedDeployment` now answers `NoDeploymentPlanned`. Probe 1 (seeded guild
run, three days in, a fee-bearing offer, two members planned, one dismissed) confirms: gold stays `before − fee`,
`planned == null`, cancel rejected. The same path runs from `reconcile` on a death, but a death can only happen after
`commit` has already consumed the plan, so Dismiss is the only reachable case. Fix is four lines in `forget` (refund
`offers.first { it.id == planned.offerId }.fee` when the plan is dropped) plus a test.

**2. Rank 3's offer life is dead content (medium).** `GuildRankDef(3, "The Last Company", "... contracts stay on the
board a day less", offerLifeDelta = -1)` (`GuildContent.kt:471`). The only reader is the default argument of
`Missions.generate` (`Missions.kt:138`). `refreshBoard`'s local `post()` (`:55`) declares its own default
`expires = ctx.day + def.lifeDays - 1` and always forwards it, so `generate`'s default is never evaluated. Probe 2 sets
`guild.rank = 3`, ends a day, and asserts every drawn offer's `expiresDay == postedDay + lifeDays - 1`: passes. There is
no test naming `offerLifeDelta` in `core/src/test`. The rank's other two effects (`enemyPercent` via `GuildOps.ranked`,
`extraSiegeUnit` in `SiegeFight.plan`) do apply.

**3. Refuted: the final-siege champion softlock.** `SiegeFight.resolve` writes `championIds = wall` (`:238`) before the
rout kill (`:276`) and `GameEngine.recover` returns early once the phase is ENDED (`GameEngine.kt:485`), so I expected a
dead champion to remain listed and `Invariants.check` to throw on the run's last End Day. It does not: `Battle.kill`
removes the hero from `championIds` itself (`Battle.kt:191`). Recorded so nobody chases it again.

**4. End Day order vs GDD 3.2.** `GameEngine.endDay` (`:405-473`): commit party → expire visitor → commissions →
consequences → shelf visits → recognitions → merchant → party stage → autonomous activities → members' home day →
factions → world events → commission offer → siege warning → siege → ambitions → retirements → reconcile → recovery →
blessing expiry → SHOP_DAY record → resolution → compaction/pruning → new morning. Deviations from the GDD's list, all
reasoned in comments: commissions before browsers (so a promised blade is not sold first), the guild party before the
autonomous heroes, recovery after retirements. None changes anything a player could notice as wrong. Idempotence: the
ViewModel builds the ID as `runId:dayN` (`GameViewModel.kt:754`), a retry returns the stored resolution (`:406-410`),
an older ID returns the unchanged state which `GameSession.plan` does not write (`GameSession.kt:238`).
`ResolveEncounter` is idempotent by the stored command ID (`GameEngine.kt:196`).

**5. Save codec.** Schema 7; `runMigrations` has steps 1..6 and `legacyMigrations` has 1..6 (`SaveCodec.kt:59-91`), so
`migrate` cannot hit "No migration from schema". Maps round-trip in insertion order, and the only `HashMap` in the
guild path (`drawMaterials`) is sorted before storage (`Missions.kt:135`). `Rng.nextInt(from, to)` is inclusive
(`Rng.kt:67-70`), so `heartfireOutlook`'s low/high and the contract gold range are honest. Every read-only function
named in the task (`forecast`, `outlook`, `view`, `fieldPlan`, `heartfireOutlook`, `missionSetup`) draws nothing; the
one `ctx.rng` inside `SiegeFight.plan` runs only on commitment, and `sabotage` calls `plan` first so the field is the
same for forecast, sabotage and the night (`SiegeFight.kt:68`).

**6. Smaller edges (low).** Siege jobs take two of the three ordinary slots on the mornings they are posted
(`Missions.kt:90-98`); a plan that cannot leave refunds through `ctx.earn(CONTRACT, fee)` so the day's ledger shows
income that is a refund (`:222`); `GuildOps.recruit` copies `championIds` onto itself (`:218`); the `completed` map holds
archetype tallies, `"pair:a:b"` bond counts and `"siegeJobs" → siegeDay` in one namespace (`Missions.kt:107,324`,
`Stories.kt:61`); `Missions.losses` on a LOST fight where nobody was downed captures nobody (lenient, intended by the
contract text). `pendingBlessingOffer` is never expired by End Day and is overwritten by the next won siege
(`Battle.kt:379`): not a defect, but the simulators always choose at once, so deferral is unmeasured.

### (b) UI for a first-time player

Typography (`ui/theme/Type.kt:18-28`): no Light or Thin weight anywhere in `app/src/main`; body 16–17 sp, `bodySmall`
14 sp Regular, `labelSmall` 12 sp Medium. The "thin" look the owner sees is `Secondary` (`WorkshopScreen.kt:482`):
`bodySmall` in `CreamMuted` (9.6:1 on the panel), used for nearly every explanatory line, including all 39 scenario
descriptions. It is legible but low in weight next to bold serif titles; one `labelMedium`/Medium-weight treatment for
description lines would answer the complaint without touching sizes.

Charter picker (`CharterPicker.kt:60-91`): a ‹ › stepper labelled "Charter N of 4" in 12 sp; the classic shop is the
fourth position, named "No charter", described "The shop alone, as before"; a charter's collapsed line is "Plays as:
Protect, strike back, rebuild." `placeCharter` (`GameViewModel.kt:390-399`) does start a new account on the classic
shop, which is right; the words are the problem. Ended run: `App.kt:115` labels the primary "Continue run".

Forge (`ForgePanel.kt`): the workbench, trays, field notes, the outlook card and Heartfire row are strong. Weak points:
Supplies / Notebook / End day behind "⋯" (`:198-206`, stock `DropdownMenu`), Risk as a stock dropdown (`:505-528`),
and `bellowsBlocked` (`:619-628`) restating `Relics.bellowsError` (`Relics.kt:111-117`), which DECISIONS already lists
as a class of drift ("three rules restated in the app").

Shop (`ShopPanel.kt`): one lazy list, the lead card, doors to the board/storage/supplies, demand, yesterday folded:
clear. Vocabulary still drifts: "Commissions & customers" (`:139`) vs "Commissions" (`ForgePanel.kt:196`) vs "the
board" (`GuildPanel.kt:86`).

Guild (`GuildPanel.kt`): the wall card explains the watch, the places, reservations and who is missing (`:189-268`);
the offer card carries fee, stake, stages, "Bring" and "If it fails" (`:305-335`). Party picking (`ContractSheet.kt:85-94`)
marks a chosen member only by a gold digit; the card says "Choose a party", the sheet "Send" with the detail "Leaves at
End Day" (`:136-138`), so the three-word mismatch is softened but still there.

Shop day (`shopday/`): position is cursor-backed and resumes after process death (`GameSession.kt:137,300-307`,
`ShopDayHost.kt:54-72`); skip is one tap. Not re-measured on a device here.

Debug scenario menu (`app/src/debug/.../ScenarioMenu.kt`): `index.json` has 39 rows; titles 16–52 chars (median 30),
descriptions 73–283 chars (median 119) all in `Secondary`; no grouping, day, charter or "replaces legacy" chip on the
row (that fact only appears in the confirm dialog, `:97-100`); `remember { assets.open(...) }` reads and parses the
index on the main thread inside composition (`:65-70`); the two entry buttons are stock `OutlinedButton`s (`:55,60`).
Release builds have an empty `ScenarioMenu`, so this is debug-only and shares only `forgeRow`/`Secondary`.

### (c) Game design against GDD 1–3 and the revision

Pillar 1, discovery: 24 signature recipes (`crafting/Signatures.kt`), base chance 0.15 rising to a 0.50 cap
(`BalanceConfig.kt:149-151`), a known recipe at `knownSignatureChance`. DECISIONS records "98 % of clue-following
accounts find a signature in their first era, which may be too fast": discovery is reliably inside the 15–25-day run.
Pillar 3, consequences: a sale names the reason (`Market.kt` visit reasons), a blade's history carries FORGED, SIGNATURE,
VICTORY, SIEGE, CRACKED, SEIZED, RECOVERED, TITLED lines; the 1.3 moment (ranger, champion, death, descendant finds the
sword) is mechanically reachable (`Legacy` lineages, `Weapon.legendKey`, dormant affixes woken by a hone,
`GameEngine.kt:340-346`). What is missing is not a system but a surface: no one screen shows "this blade → this hero →
these fights" as one thread; the hero sheet and the blade sheet each show half.

Energy (`BalanceConfig.kt:31-34,232-233`): 10 a day, quick 2, advanced 4, hone 2, salvage 1, overwork 4. A day is five
quick forges, or two advanced and a quick, or seven quick on overwork with a short morning after: enough room for one
real choice (advanced on the pairing the notes favour vs. volume for the shelf). The simulator says volume at a fair
price is beaten by forging what customers ask (REQUEST_DRIVEN +9.7 days) and by free listings (+2.5 days): the first is
the feature working, the second is finding 3.

Sieges: every 5 days from day 5 (`siegeInterval`), the first plain, a trait possible from `firstTraitSiege`, a warlord
at pressure ≥ 50. Classic levers: who holds which blade (autonomous), donate to the armory (`GameEngine.kt:353-368`),
siege-prep commissions. They are explained on the threat strip and the Town card; the one lever the player cannot see
is how much the armory actually thins the raid before the wall (guild runs show "Watch" with a sentence,
`GuildPanel.kt:223-224`; classic runs do not).

Guild decisions (revision table): recruit (real: fee vs class gap), loan (real), party (real), mission (real), posture
(real), reserve (real on siege eve, otherwise default), checkpoint (real), speciality (real, once), rank (optional
difficulty), opening (real, once), law (none: it is drawn), retire (real at day 20). Four of twelve are default-correct
most mornings; that is acceptable. The day-20 milestone is a single line in the region card (`GuildUi.kt:316`); a
first-time player who never scrolls past the board does not know the run has a goal.

Pacing (GDD 3.3): the morning's interruptions are the opening (day 1 only, blocking: `WorkshopScreen.kt:123-132`), the
blessing dialog, the relic dialog (one at a time, `:233-238`), the visitor (a card, not a dialog), the checkpoint (a
card). Worst case after a won siege 1–3 in a guild run: blessing + relic + visitor + checkpoint on one morning, two of
them modal. Not a 1–3-minute-day breaker, but the relic offer should wait a morning when a blessing is pending.

Content room the revision opened (counted by constructor calls in `content/`): 3 charters, 8 openings, 5 relics, 7
contract modifiers, 3 laws, 6 siege preparations, 2 siege traits, 13 visitors, 10 mission definitions, 3 ranks. Thinnest
per unit of work: siege traits (2), laws (3), charters (3). Nothing in the tree moves a LOCKED rule; the classic run
still has no equipping and no hero orders (`GameEngine.kt:154-160` rejects every guild command without a guild).

## Prior findings (rule 6)

| Item | Status | Evidence |
|---|---|---|
| F01 legacy purchase vs next era race | fixed | one mutex, `legacyStep` keeps the run row (`GameSession.kt:75-88,280-283`) |
| F02 no recovery path for storage failures | fixed | `startOverKeepingBackup`, `ui/Failures.kt` (`GameSession.kt:107-124`) |
| F03 day report omits preparation events | fixed | "the edition is the whole day, preparation included" (`GameEngine.kt:445-450`) |
| F04 commission quality band vs exact rule | fixed | "Quality N+" and "needs N" (`ui/ForgeUi.kt:236`, `ui/Labels.kt:29`) |
| F05 customer order biased by hero ID | fixed | rules 3 fair seating (`GameEngine.kt:45-46`) |
| F06 commission stock sold first | fixed | commissions before shelf visits, `promisedTo` guard (`GameEngine.kt:420-423,219`) |
| F07 tallies disagree with outcomes | fixed | ledger balances by construction through `ctx.earn` (`ResolutionContext.kt:95-99`) |
| F08 champions ignore warlord matchup | fixed | `recover` uses the siege's own ranking (`GameEngine.kt:495-497`) |
| F09 compatibility not enforced | fixed | `engine/Compatibility.kt` |
| F10 save growth | addressed, still grows | compaction/pruning (`GameEngine.kt:463-470`); DECISIONS 12 |
| F11 locale-dependent `%.2f` | fixed | no `.format(` left in `core/src/main` |
| UI cause 2 (End Day pinned everywhere) | fixed | End Day only on Shop (`WorkshopScreen.kt:147`) |
| UI cause 4 (tab switch resets state) | fixed | per-destination `SaveableStateHolder` (`WorkshopScreen.kt:134,151`) |
| UI cause 5 (stock M3 at the seams) | open | `DropdownMenu`, `AlertDialog`, `OutlinedButton`, `TextButton` (`ForgePanel.kt:201,519`; `GuildPanel.kt:158`; `ScenarioMenu.kt:55-115`) |
| UI cause 9 (vocabulary drift) | partly open | "Commissions & customers" / "Commissions" / "the board" |
| UI cause 10 (Back model) | partly fixed | `BackHandler { if (!vm.back()) onMainMenu() }` (`WorkshopScreen.kt:120`) |
| UI causes 1, 3, 6, 7, 8, 11, 12 | not re-checked here | need a device pass |

## Owner decisions
- Free listings: should a blade given away earn the same reputation and loyalty as one sold? (Trade-off: a generous-smith
  playstyle vs a measured +2.5-day survival edge over fair pricing.)
- Rank 3's offer life: fix the dead field, or cut the sentence and keep the rank at two effects?
- Default run kind on a fresh account is the classic shop (`placeCharter`); keep that, and name the two choices plainly?
- Relic offer on a morning with a pending blessing: hold it one day? (Trade-off: fewer stacked dialogs vs a relic a day later.)

## Codex reviewers: what came back, what was kept
- **Lens (a), engine**: 5 findings after 63 shell commands (`scratchpad/review/lens_a.out.md`). Kept: rank 3's
  `offerLifeDelta` (its #1 = my #2, found independently; it also confirms the only rank test, `RunVarietyTest.kt:367`,
  covers `enemyPercent` alone); the ignored-blessing-offer lifetime (#14); forge damage ignoring living summons (#15: a
  Hollow Host siege can have shamblers raised every third round, `CombatContent.kt:414,420`, and `SiegeFight.kt:219`
  excludes them from "left" on purpose, unrecorded); downed resident defenders without a record (#16). Rejected: "a
  processed but non-latest End Day ID is accepted with `resolution = null`" as a defect: the app only ever sends
  `runId:dayN` for the current day (`GameViewModel.kt:754`), the no-op is not written (`GameSession.kt:238`), and
  DECISIONS "Open owner decisions" 11 already records the 30-ID window. It did not find the dismiss-fee loss (#1) or the
  free-listing reputation (#3). Its "found sound" list (OpeningPending guarded by the chooser, siege-job timing tested,
  read-only paths draw nothing, loans seized before kill/capture, Heartfire preview matches the rule) agrees with mine.
- **Lens (b), UI**: 6 findings. Kept: the charter picker wording (#4), "Continue run" on an ended run (#5), the party-pick
  rows and "Choose a party"/"Send" (#12, softened: the button's detail line already says "Leaves at End Day"), the debug
  menu (#9). Rejected: "ContractSheet computes its preview in the composable, bypassing the ViewModel" — `engine.contractUi`
  is a read-only view called inside `remember`, the same pattern as `heroDetail`/`itemDetail`/`forgeWorkbench`
  (`WorkshopScreen.kt:253,268`, `Dialogs.kt:98`); CLAUDE.md forbids computing outcomes in the UI, not reading engine views.
  Its "found sound" list (no thin weights, debug-only menu, cursor-backed shop day, visible preconditions) matches mine.
- **Lens (c), design**: no output (context overflow at 244,838 tokens; remote compaction 404). The design section above
  is mine alone.
- Probe tests (scratch worktree only, not committed): `AdversarialReviewProbeTest` — 3 tests, 3 passed, 0.36 s.
