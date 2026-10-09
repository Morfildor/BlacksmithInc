# Major update ledger: tasks and evidence

Single source of truth for progress on `docs/MAJOR_UPDATE_PLAN.md`. One row per task; a row is only as true as its
evidence cell. Status: `todo`, `contract`, `in worktree <name>`, `merged`, `verified`, `blocked: <reason>`.
A session starts by reading this file and re-running the baseline commands of plan section 9.1, and ends by
updating it.

Baseline: release **v0.6.0**, `main` at `0ad888a` (balance v5, content v2, rules 1, schema 1).

## Requirement status against the current code (rechecked 2026-10-09)

Checked in the source at `0d5ff65`; `git diff --stat 0d5ff65 0ad888a` touches no source file, so each row holds for
0.6.0. Details and `path:line` are in plan section 2.4.

| ID | Status at 0.6.0 | Owning task |
|---|---|---|
| F01 concurrent legacy purchases / Begin era | confirmed, not fixed | T1.1, T1.3 |
| F02 storage and decode failure | confirmed, worse (launch crash loop) | T1.2 |
| F03 day report omits preparation records | confirmed (fixture: 15 of 20) | T1.4 |
| F04 commission band vs exact rule | confirmed, wider (Home, Gazette, noble, COLLECTOR) | T1.8 |
| F05 ID-order visitor starvation | confirmed, measured (position 8 served 2.4x less than 1-4) | T3.1 |
| F06 commission stock can sell first | confirmed, wider (patron takes the best blade) | T1.8 |
| F07 tallies and money | confirmed, wider (tribute counted, spending unrecorded) | T1.6, T2.1 |
| F08 champion ranking without warlord context | confirmed | T1.7 |
| F09 versions recorded, not enforced | confirmed, worse (v5 shipped under rules 1) | T1.5a, T1.5b |
| F10 save growth | partial (terminal pruning present; stock and records still grow) | T6.3a, T6.3b, T6.3c |
| F11 locale-dependent payload | confirmed, two sites | T1.6 |
| Signboard (review 6.2) | **fixed in 0.6.0**: one seat per level | kept |
| Guild Patronage | open: still visit chance under the cap | T3.6 |
| 10,000-seed review | **done for balance v5 in 0.6.0**; one more after this update | T5.5 |
| Known Name | **reworked in 0.6.0** (+1.6 mean days) | T5.3 measures again after v6 |
| Traveling Ore Merchant stock overwritten at morning | open | T1.9 |
| Wall-death weapon fates unreachable | open | T3.8 |
| Lessons, inheritance, merchant resale never seen on device | open | T2.9 |
| Bots for Advanced Forge, signatures, commissions, scarce recipes, multi-era returns | open | T0.7 |

## Evidence

| Date | Commit | Check | Command | Result |
|---|---|---|---|---|
| 2026-10-09 | `0ad888a` | core tests, app unit tests, debug build, lint | `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` | BUILD SUCCESSFUL; core 186 / 186 in 26 classes; app unit 1 / 1 (template); APK built; lint 0 errors, 38 warnings |
| 2026-10-09 | `0d5ff65` (same sources) | policy sweep, 1,000 seeds | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --policy all --noImpact --json <file>"` | BALANCED_FAIR 20 (15/25) mean 20.5; BALANCED_ACTIVE 25 (20/35) mean 27.4; SYNERGY 35 (25/40) 34.7; EXPENSIVE 10, 12.9; PASSIVE 10; 0 hard-locks. JSON: `docs/major_update_evidence/measure/sim_head_0d5ff65_1k_seed1.json` |
| 2026-10-09 | `0ad888a` | balance v5 at 10,000 seeds (session 8, not re-run) | see `docs/DECISIONS.md`, "Balance v5 review at 10,000 seeds" | BALANCED_FAIR 20 (15/25) 20.7; BALANCED_ACTIVE 30 (20/35) 27.4; SYNERGY 35 (25/40) 34.8; all upgrades FAIR 35 (30/40) 36.5, ACTIVE 45 (35/50) 42.3, longest 55; every run ends |
| 2026-10-09 | `0d5ff65` (same sources) | customer and identity baseline (scratch harness, 500 seeds) | `docs/major_update_evidence/measure/` | 8.2 heroes alive; 3.56 served a day; at the cap 69 % of days; position 8 served 23 % of days against 53-54 % for positions 1-4; shared first name 67 % of days, shared face 52 % |
| 2026-10-09 | `0d5ff65` (same sources) | what-if: 12 heroes, 6 seats (300 seeds) | same | mean 26.0 days (21.1 today); 5.66 served a day; 1.46 purchases a day |
| not run | | instrumented tests, device loop | `./gradlew :app:connectedDebugAndroidTest`; `tools/emulator/smoke.sh` | **not run by the planning session**; session 8 recorded 6 / 6 and `SMOKE_DONE` for 0.6.0 |
| blocked | | physical device checks | | no device attached |

## Planning record

| Date | What | Result |
|---|---|---|
| 2026-10-09 | plan written from four evidence reports (`docs/major_update_evidence/01`-`04`) | `docs/MAJOR_UPDATE_PLAN.md` |
| 2026-10-09 | reconciled with release v0.6.0 (`0ad888a`) | F01-F11 rechecked; five gaps added (ore merchant, Guild Patronage, wall deaths, device scenarios, bots) |
| 2026-10-09 | independent adversarial review (`docs/major_update_evidence/05_adversarial_review.md`) | 2 blockers, 12 major, 22 minor: all dispositioned in plan section 11 |

Gates that need the owner during execution: the art gate of T2.4 (how a customer is drawn; how many reference faces
passed), the tripwire of plan 4.3 if it fires, the playtest gates, and the four inputs of plan 10.4.

## Tasks

| Task | Title | Owner | Status | Commit | Evidence | Review |
|---|---|---|---|---|---|---|
| T0.1 | Ledger and re-baseline | INT | merged (baseline rows above; CI not run) | 74b2137 | core suite green on the branch after each merge by the task agents | |
| T0.2 | CI | INT | todo | | | |
| T0.3 | Checklist and wording accuracy | INT | merged | acd3318 | documents only; ten-row table in scratch report T0.3; CLAUDE.md and tooling label left to T2.4/INT | |
| T0.4 | Simulator metrics and overrides | SIM | merged | a6a3c4b | core 190/190; output byte-identical without flags; seed-1 table reproduced (FAIR 20.50, ACTIVE 27.39, SYNERGY 34.66); served/day 3.55, at cap 69 %, position 8 22.2 % vs 53-54 % | |
| T0.5 | App seams | AB | merged | 4afba98 | app unit 2/2, lint 0 errors 38 warnings, emulator smoke SMOKE_DONE all ok with id taps; seed extra shown on device; release guard not built | |
| T0.6 | Golden gameplay projection | INT | merged | dc81778 | core 187/187; fails when an RNG stream or an outcome hash is altered (both shown), passes twice unrecorded | |
| T0.7 | Bots (Advanced, techniques, commissions, signatures, scarce recipes, multi-era, shock, EXPERT) | SIM | todo | | | |
| T1.1 | `GameSession` | INT | todo | | | |
| T1.2 | Failure and recovery | INT + AB | todo | | | |
| T1.3 | ViewModel on the session | AB | todo | | | |
| T1.4 | Complete day edition | CB | merged | 6c88919 | core 191/191; golden unchanged; v1 fixture day 60 returns 20 of 20 records; app build ok; device not run | |
| T1.5a | Versions, part 1 (neutral, first) | INT | merged | fcbad57 | core 197/197; golden unchanged; simulator output identical; WeaponLocation strings pinned against the unmodified model first | |
| T1.5b | Versions, part 2 (after T1.2: rules 2, schema 2) | INT | todo | | | |
| T1.6 | Typed money, field results, locale | CA + CB | todo | | | |
| T1.7 | Champion context | CB | todo | | | |
| T1.8 | Commissions | CA + CON + AB | todo | | | |
| T1.9 | Small engine corrections (faction ties, ore merchant) | CB | todo | | | |
| T2.1 | Visit record | CA | todo | | | |
| T2.2 | Script and lead | CB | todo | | | |
| T2.3 | Cursor | INT | todo | | | |
| T2.4 | Assets for the scene | ART | todo | | | |
| T2.5 | Counter screen | AA | todo | | | |
| T2.6 | Aftermath, Tomorrow, endings | AA | todo | | | |
| T2.7 | Hero and item sheets | AA | todo | | | |
| T2.8a | Four destinations | AB | todo | | | |
| T2.8b | The Shop destination | AB | todo | | | |
| T2.8c | Forge, Town, Supplies | AB | todo | | | |
| T2.9 | Device scripts and scenario saves | INT | todo | | | |
| T2.10 | Onboarding | AB | todo | | | |
| T3.1 | Fair selection | CA | todo | | | |
| T3.2 | Names and lineage IDs | CON + CA | todo | | | |
| T3.3 | Appearance | ART + CA + AA | todo | | | |
| T3.4 | Population, seats, compensation | CA + SIM | todo | | | |
| T3.5 | Recognition | CB + CON + AA | todo | | | |
| T3.6 | Guild Patronage | CA + SIM | todo | | | |
| T3.7 | The counter and the Town at the new scale | AA + AB | todo | | | |
| T3.8 | Wall deaths: reachable or removed | CB + SIM | todo | | | |
| T4.1 | Standing wants | CA + CB + AB | todo | | | |
| T4.2 | Sidegrade gate and siege demand | CA | todo | | | |
| T4.3 | Clue ladder, rumours, recall | CB + CON + AB | todo | | | |
| T4.4 | (withdrawn: catalyst identity ships through T4.3; four mechanical jobs deferred) | | withdrawn | | | |
| T4.5 | Artifact fidelity and the ledger | CB + AA | todo | | | |
| T4.6 | Commission situations | CA + AB | todo | | | |
| T5.2 | Recovery and reachability | SIM | todo | | | |
| T5.3 | Upgrades | CB + CON + AB | todo | | | |
| T5.4 | Resolver constants | CA + CB | todo | | | |
| T5.5 | 10,000-seed review after the update | SIM + INT | todo | | | |
| T6.1 | Layout matrix | AB | todo | | | |
| T6.2 | TalkBack and semantics | AA | todo | | | |
| T6.3a | Bounded growth rules | CB | todo | | | |
| T6.3b | Production soak and device measurement | SIM + AB | todo | | | |
| T6.3c | Storage tools | AB | todo | | | |
| T6.4 | Lifecycle tests | AB | todo | | | |
| T6.5 | Main thread | AB | todo | | | |
| T6.7 | Haptics with a toggle; audio controls with real audio | AB | todo | | | |
| T6.6 | Physical device | INT | blocked: no device attached | | | |
| T7.1 | Documents and version | INT | todo | | | |
| T7.2 | Fresh-player sessions | owner + INT | blocked: needs five first-time players | | | |
| T7.3 | GDD 19 walk-through | INT | todo | | | |
| T7.4 | Release identity runbook | INT | todo (the rename itself is blocked: application ID) | | | |

## Execution record (started 2026-10-09)

Work happens on local branches `shop-day/m<N>` in the worktree `.claude/worktrees/shop-day`; this file there is the
live copy and is copied back to the main checkout at each milestone boundary.

- Ruling: local commits on `shop-day/*` branches only, never on `main`, never pushed; `docs/major_update_evidence/`, the review document and the atlas PNG are never staged. Why: the owner forbade commits to public history but asked for isolated worktrees, and review needs commit ranges. Cost if wrong: the branches are deleted and the work is re-applied as a patch.
- Ruling: plan section 11 and the "Who edits what" tables stand in for a new pre-flight conflict scan. Why: the owner said not to restart broad planning. Cost if wrong: a file conflict surfaces at merge and is fixed there.
- Ruling: tasks with disjoint file owners run in parallel worktrees; the app chain T1.1 to T1.3 runs serially. Why: plan 8.1. Cost if wrong: merge conflicts.
- Scope added by the execution instruction: **T6.7 Haptics and audio controls** (haptic feedback on meaningful moments with a working toggle; audio controls only with real audio: no sound file exists in `app/src/main/res` at `0ad888a`, so audio stays an explicit GDD 19 gap unless assets appear). T0.3 also corrects "hand-made" in `CLAUDE.md` and `docs/ART_BRIEF.md`. T2.4: the import script only reads sources and writes new files; no source image is re-saved (that would strip its content credentials).
