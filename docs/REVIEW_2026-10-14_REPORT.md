# Review 2026-10-14: final report (branch `review-2026-10-14`, not merged)

Three columns per item: what was seen working on an emulator, what only the JVM tests cover, what was not run. The cloud
session (handoff items 1 to 8) had no Android SDK: its network policy blocks Google's Maven host, so no `:app` Gradle
task ran there. It compiled the app's Kotlin against desktop Compose 1.6.11 with stubs for the Android-only APIs, ran the
app's JVM unit tests that way, and type-checked the instrumented tests it touched. Final counts on the branch head:
`:core:test` 636 / 0, app unit tests 255 / 0 (through that shim), core simulators as recorded in DECISIONS. After the
stabilisation pass: 637 / 0 and 260 / 0.

| Item | Emulator-verified | Unit-tested only | Not run |
|---|---|---|---|
| Phase 1 items 1 to 5, decisions H, D, G, I, C (before the cloud session) | Title, run setup and opening chooser at font 1.0 and 2.0 (emulator-5556) | `:app:testDebugUnitTest` 246 / 0 at 99354a8 | Instrumented suite, `tools/emulator` scripts |
| Core fixes rows 21, 22/B, 31 to 34, 36a to c, M | | `:core:test`; guildsim and simulate numbers in DECISIONS | |
| 1. Shop: lead first, shelf once, siege in proportion, End Day footer, one voice on day 1 | | `ThreatAndRequestsModelTest` (urgency, one-time note); app 249 / 0 | `ShopPanelTest`, `ShopDayScreenTest` (updated, type-checked); `smoke.sh` (updated); screens at 1.0 and 2.0 |
| 2. Forge: K, L, A, slot labels, guide placement, Heartfire confirm | | `ForgeResultTest`, `RunVarietyUiTest`; app 250 / 0 | `ForgeResultCardTest` (rewritten, type-checked); `runvariety.sh` (taps the confirm); `forge.sh` |
| 3. Dialogs on `ForgeDialog`; relic, loan, visitor on `ChoiceRow` | | App 250 / 0 (compiles; no screen test for these dialogs) | Every dialog on a device; `scenarios.sh` relic and visitor checks |
| 4. Guild board and roster; J | | `GuildUiTest` (board line, roster lines, J's line); app 251 / 0 | `guild.sh`, `journey.sh`, `runvariety.sh` board read |
| 5. Gameplay batch (offers, two traits, warlord Heartfire, lesson, NO_SABOTAGE, synergy evidence, militia line) | | `OffersTest`, `EncountersTest`, golden re-recorded, `:core:test` 636 / 0; simulate and guildsim runs in DECISIONS | Scenario saves rewritten, not loaded on a device |
| 6. Oversized save | | `SaveStoreSizeTest` (in-memory DAO with the 2 MB window), `GameSessionFailureTest`; app 255 / 0 | `SaveStoreTest.aRowPastTheCursorWindowIsSplitInsideSqliteAndLoads` (real Room, 2.5 MB); a day-1,000 soak save loaded on a device |
| 7. Device batch | | The eight stale instrumented assertions updated and type-checked | The whole instrumented suite, every `tools/emulator` script, the 600 dp screen (D), before/after captures |
| 8. Evidence archive (E), docs | | `tools/archive_evidence.py` trial: 581 images archived and verified against git, nothing removed | The removal itself (owner's machine) |

| Stabilisation: init cycle (was decision 10) | | `ContentInitOrderTest` (each content object first, own class loader); `:core:test` 637 / 0, golden unchanged | |
| Stabilisation: SaveStore (one-transaction quarantine, broken split rows reported, parts cut between characters) | | `SaveStoreSizeTest` +4, `GameSessionFailureTest` +1; app 260 / 0 through the shim | Three new real-Room tests in `SaveStoreTest` (not run, not type-checked here); a day-1,000 save on a device |

The stabilisation pass on 885b406 (commits aa152ad, edd6dc7) changed no balance, content, rules or golden output. The
Android gates stay open: none of `:app:testDebugUnitTest`, `:app:assembleDebug`, the instrumented suite or the device
batch ran; the shim results above do not close them. The local checklist is in `docs/HANDOFF_CLOUD.md`.

What the owner decides next is in DECISIONS, "Open owner decisions, consolidated".
