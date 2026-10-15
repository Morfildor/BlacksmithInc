# T4: Guild quest comparison and party continuity

Implemented locally on 2026-10-15 against HEAD `335d3f2`. Existing uncommitted T1–T3 work was retained. No branch changes, commits, pushes, scenario generation or physical-device use.

## 1. Contract comparison and timing

`GuildUi.kt` reads costs, duration, optional stages and rewards from the stored posting. The board uses consistent rows for name/objective, fee, return morning, danger/modifier, rewards, siege absence and expiry. Multi-stage rewards are separately identified, including optional rewards. Full situation, risk, enemies and stage details remain in the existing sheet. Yesterday follows today's offers.

The return morning remains `day + offer.days`, matching `Missions.commit`. A party returning on siege morning can defend that night if fit. Returning the next morning misses it. The board and sheet distinguish planned route timing from wound-related readiness. No future counter-equipment benefit is advertised. Staying home is explicitly legitimate.

## 2. Party order and confirmation

The selected party is separate from the remaining roster. Named, 48 dp minimum up/down controls move members without changing membership, posture or equipment. Removal and re-addition append the member to the current order. The exact ordered IDs are submitted through the existing `PlanDeployment` command.

The confirmation action stays outside the planning scroller and says “Plan departure”. Its facts include party order, End Day departure, return morning, fee, posture and any siege absence. At extreme text sizes the confirmation facts have their own bounded scroller so the action remains reachable. Canceling an accepted plan retains the existing refund behavior. Closing an unconfirmed draft changes no game state.

## 3. Equipment continuity and revalidation

`PartyDraft.kt` is UI-only intent owned by `GameViewModel.Local`, projected to the screen and stored in `SavedStateHandle` using strings and lists. It holds run/day, offer, ordered members, posture and reviewed equipment identities/condition/quality. No game-save schema fields were added.

Existing hero and weapon sheets replace the party modal. Close/Back returns to the same draft. Existing loan/recall commands are used directly. A changed weapon, loan, condition or quality blocks confirmation until “Use current equipment” is chosen. Unavailable, reserved, removed or missing members remain selected with reasons rather than being silently filtered out. Missing/expired offers and stale mornings are blocked. Confirmation re-reads the session and checks the draft before dispatch; only an accepted plan closes it.

## Files

- Production: `GameViewModel.kt`, new `PartyDraft.kt`, `ui/GuildUi.kt`, `ui/GuildParts.kt`, `ui/GuildPanel.kt`, `ui/ContractSheet.kt`, `ui/WorkshopScreen.kt`, `ui/detail/HeroDetailSheet.kt`, `ui/detail/ItemDetailSheet.kt`.
- Coverage: `GuildUiTest.kt`, `GuildPlanUiTest.kt`, new `PartyDraftTest.kt`, `NavigationFlowTest.kt`, core `GuildRunTest.kt`.
- Documentation: `CHANGELOG.md`, `docs/PROGRESS.md`, this report.

## Verification

- Focused app: `GuildUiTest`, `GuildPlanUiTest`, `PartyDraftTest`, `SavedStateTest`: 29 tests passed.
- Focused core: `GuildRunTest`: 23 tests passed, including ordered plan/change/cancel/refund/departure and save round trip.
- Full `:core:test`: 675 tests, zero failures/errors/skips.
- Full `:app:testDebugUnitTest`: 311 tests, zero failures/errors/skips.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: succeeded.
- `git diff --check`: passed.
- Device navigation: all six `NavigationFlowTest` tests passed on the final APK. `guildPartySurvivesEquipmentAndBackWithExactOrderAndOneFee` exercises A/B/C to C/A/B, Cautious, actual loan, equipment review, hero/weapon modal round trips, plan and cancellation/refund. Existing Shop/Forge/storage/menu/day navigation tests also passed.
- Layout: `NavigationFlowTest.guildPlanningLayoutAtCurrentDeviceConfiguration` passed all nine actual emulator configurations: widths 320, 360 and 600 dp, each at fonts 1.0, 1.5 and 2.0. The fixture has long names, an expanded roster and a siege tonight. It checks the visible confirmation action, scrollable timing/warning, untruncated siege warning and 48 dp reorder control. Board, confirmation and party captures are in `app/build/t4-evidence/final-screenshots/` (27 PNGs). Logs: `final-layout-<width>-<font>.txt` and `final-navigation.txt`.

Unit coverage also checks unavailable members, reservation while drafting, missing/expired quests, party limits, duplicate confirmation, remove/re-add, posture, equipment changes and SavedState restoration while a hero sheet is open. Configuration persistence is verified at the SavedState/ViewModel boundary, not by a device process-kill test.

## Device evidence and limits

Isolated read-only `Pixel_10_Pro` AVD, explicit serial `emulator-5562`; no other device targeted. The emulator was shut down after evidence was collected. Captures and logs live under ignored `app/build/t4-evidence/`. Initial tablet layout assertions exposed an intrinsic text-width overflow; making the siege warning fill its available width resolved it. An initial test fixture tried to decline an absent relic offer and was corrected. These were new verification issues, not baseline failures. There are no outstanding verification failures.

At 320 dp with 2.0 text and deliberately long names, scrolling is necessary within both the planning details and confirmation facts. The confirmation action remains visible. Manual TalkBack, a formative player session and device process-death recreation were not performed. Reorder controls expose member-specific accessibility names and minimum hit areas.

T4 changes no gameplay resolver, reward, combat, balance, difficulty, siege schedule, command type, RNG behavior or game-save schema. The existing core `combat/Account.kt` modification belongs to the preserved T3 work. No Wave 2 work was started.
