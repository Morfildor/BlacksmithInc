# Visual pass ledger (2026-10-17)

The implementation of the external review of `docs/VISUAL_UX_AUDIT_2026-10-17.md`. Sources:
`docs/visual_audit_2026-10-17/External review/` (combined review, implementation prompt). Plan:
`docs/superpowers/plans/2026-10-17-visual-quality-pass.md`. Baseline `ad32de6` (0.9.0 and layered weapons); the art
session's redrawn backgrounds, faction portraits and six bestiary portraits (`77903c4`, `dcc68fe`) landed during the pass
and are included.

Status words: **done** (implemented and seen on device unless marked *code*), **unchanged** (checked, no change needed),
**superseded**, **deferred** (optional, with the reason), **open** (needs an owner call).

Evidence: `docs/visual_audit_2026-10-17/pass/` (before and after pairs at a third of full size; `before_*` is the unchanged
`ad32de6` build, `after_*` this pass's final build; `after_x_*` have no before shot). Evidence names in the table without
a prefix exist as both. Device: private read-only Pixel_10_Pro AVD (API 36) on emulator-5562, gesture
navigation unless stated. Sizes: `c360` 1080x2400 @480, `c393` 1080x2340 @440, `c412` 1080x2400 @420 (font 1.0);
`f13/f15/f20` 360 dp at font 1.3/1.5/2.0; `d420/d440/d480` 360 dp wide at 420/440/480 dpi. States: `guild_siege_eve`
(constructed, livingRevision 0) for the workshop matrix; `ShopDayPreviewActivity` (preview) for the day cards; the
"Living heroes: members decide" scenario played to its siege (constructed start, natural days 3 to 5) for the new-era
replay; `guild_charter_eve` for retirement. The full device pass (13 surfaces, about 150 shots) is in the session
scratchpad, summarised below.

## Findings

| ID | Status | What changed / why not | Evidence |
|---|---|---|---|
| VA-01 | done (C2) | Header and End Day footer decided by measured width; the old 50–80 dp figure is not used. At 2.0 the Shop shows the countdown and defenders in the header, the rest in the siege block | `f20_shop` |
| VA-02 | done | Forge bar is two groups that wrap; "2 new" stays one line | `f15_forge`, `f20_forge` |
| VA-03 | done | Notebook tabs per row from the widest word (2 at 1.0, 1 at 2.0), no word broken. The pairing rows put "Use" under the text when squeezed (*code*, fixed after the device pass) | `after_c360_notebook`, `after_f20_notebook` |
| VA-04 | done | Records tabs one row when they fit, else 2×2; `Role.Tab`/selected kept. Guild tabs the same (no sideways scroll) | `f15_notebook`, `f20_notebook`, `f20_roster` |
| VA-05 | done | Shelf, Storage, Supplies, Wall rows stack by measured room (`roomBeside`); label and value joined by a no-break space. Storage rows stack at every phone width because "List at N" is wide. Storage filter chips wrap (`FlowRow`, *code*: not re-shot with chips showing) | device pass (storage/supplies 360/412, 1.0/2.0) |
| VA-06 | done (C3) | Slots wrap into a grid (no sideways scroll); option art kept at large text | `f15_forge`, `f20_forge` |
| VA-07 | done (C4) | Blessing confirm pinned as the primary, disabled until a mark; Decide later secondary; Back not shown on this card. At large text the icon sits beside the name. On the living-era save, marking Forgefire made it "Take Forgefire" and taking it moved to the next day | `f10_blessing_ending`, `f20_blessing_ending`, `after_x_living_blessing_marked` |
| VA-08 | done | Faces through `HeroFace` at a whole device-pixel scale (rows, inspect, sheet, party, kits, report, death card) | `d420/d440/d480_roster` |
| VA-09 | done | Inspect head sized from its art; hero sheet face 88 dp hint; party faces 64 dp | device pass (hero sheet, contract) |
| VA-10 | done | Replay faces nearest-neighbour at whole scale in one box size; the downed grey filter left where it was | device pass (replays) |
| VA-11 | done (C7) | Stage banners capped at 2 dp per art px and fitted by width; anvil at a whole scale; painterly art unchanged | `f20_fallen`, `c360_forge` |
| VA-12 | done | "Siege tonight" block leads the Shop on siege day with the army (living era) or besieger, weak/resists, defenders; End Day label unchanged | `c360_shop`, `f20_shop` |
| VA-13 | done | Hero sheet head: face, Health bar (a member's fight health, the same number as "In a fight"; others their 0–100 health), the weapon as art that opens it | `after_x_hero_sheet_f10`, `after_x_hero_sheet_f20` (guild_stormwell) |
| VA-14 | done | Shop-day counter: painted room skipped below 88 dp or above 1.3×, the customer's face moves into the name plate (48 dp target) | `c360/c393/c412_visit` |
| VA-15 | done | Forge links wrap; Practice fight also in the ⋯ menu whenever the link is hidden (*code*); the Notebook link under its heading at large text (*code*) | device pass `bench_f10` |
| VA-16 | done (C3) | Tray: whole tiles when they fit, else a 40 % peek with an end fade; selected tile scrolled into view (*code*); no tile narrower than its longest word; a metal's name no longer repeats on its tile (*code*) | `after_c360_forge`, `after_x_f10_metal_tray`, `after_x_f20_catalyst_tray` |
| VA-17 | done | Pixel gear (`icon_settings`) on the title and in the header | `c360_shop` |
| VA-18 | unchanged | The ringed "i" is a clear inspect control | |
| VA-19 | done (partly) | Kit chooser expander uses the drawn chevron; meaningful ✓ and +/− signs kept | |
| VA-20 | done (*code* for the second round) | Reveal leads with the weapon (art, name, rarity badge inline), then the guide; an effect named as a property is said once (seen in the device pass); rules top-aligned and a gap before the price were fixed after the device pass and are not re-shot | device pass `reveal_360` (before the second round) |
| VA-21 | unchanged | Reduced motion: the reveal is static and the notice appears at once (device pass `rm_*`); no transition defect reproduced | device pass |
| VA-22 | done | Notice sits in the layout above the page (pushes it down, no slide with reduced motion), never over the workbench | device pass `rm_list_*` |
| VA-23 | done | One stacked header at every size; numbers have a fixed minimum width | `c360_shop` |
| VA-24 | deferred | The guild icon question needs art the owner approves; not changed | |
| VA-25 | done | A rule under the guide strip; "Nothing urgent today" only on the default guild tab; Done still a deliberate tap | `c360_roster` |
| VA-26 | unchanged | Section headers already look alike; Gazette and moment typography kept | |
| VA-27 | unchanged (C6) | Tabs, radio choices and actions keep their different looks | |
| VA-28 | done | See VA-58 | |
| VA-29 | done (C8) | Shop-day cards pad 12 dp across below 400 dp; other frames unchanged | `c360_visit` |
| VA-30 | done | Settings is one panel with a heading; switch colours unchanged | |
| VA-31 | unchanged | Price field keeps its semantics, steppers and keyboard behaviour | |
| VA-32 | unchanged (C8) | Menus already use the panel colour | |
| VA-33 | deferred | Setup backdrop optional; setup reads clearly | |
| VA-34 | done | Every kit card shows what it brings and costs; first-days expander 48 dp | |
| VA-35 | done | Lead story `titleLarge`, others briefs, Fira body (no serif fallback); neighbouring same-heading clues share a headline | device pass `gz_*` |
| VA-36 | done | Night banners framed on the lit doorway of the new painting; the day's bar keeps one width | `f10_blessing_ending` |
| VA-37 | done | A sale's reason without the money; one receipt row when nothing was added or taken; reasons for and against as bullets (heard as the recorded sentences) | `*_visit` |
| VA-38 | done | Risk is a chip on the quest's title; the line is reward, away/back day, fee, send by | device pass (quests) |
| VA-39 | done | "Sold out today"; a material not on the road shows only the road note | device pass supplies |
| VA-40 | done | Unavailable visitor options look unavailable (separate from busy) and say why under the name | device pass `visitor_f10` |
| VA-41 | done (R-01) | Wall enemy lines show the enemy's picture or nothing | device pass `wall_f10_*` |
| VA-42 | done | Shared faction art is the declared fallback; a plate fills its tile and holds the whole name in two lines ("Hobgoblin Shieldbearer"), with no second copy under it | `after_x_living_replay_mid` |
| VA-43 | done (C1) | Cause rejected (already rounds); nav icons drawn at native scale per density | `d420/d440/d480_shop` |
| VA-44 | superseded (C5) | Revalidated as R-02 | |
| VA-45 | unchanged | Source check: no figure is cut; `factionAlt` has no callers. The art session has since redrawn the six figures | |
| VA-46 | done | KDoc only: the small portrait maps are empty by design | |
| VA-47 | done | Guild tabs, kit relic rows, the counter face and new controls at least 48 dp | |
| VA-48 | deferred | Splash mark optional, after the functional work | |
| VA-49 | unchanged | The title painting is approved (since redrawn by the art session) | |
| VA-50 | unchanged | Unused art is not wired for its own sake; the six bestiary portraits the owner asked for are wired (R-01) | |
| VA-51 | done | Newest action stays above the controls in one line; lanes measured from the record; Replay and Skip are text buttons; both sides, names and health visible at 360 dp without scrolling | `after_x_living_replay_mid` |
| VA-52 | done | Run end by kind: fallen forge band or the night workshop, title cream or gold; Begin era says which charter | device pass `runend_*` |
| VA-53 | done | Fallen card says the day once, cause in cream, room for the foot | `f20_fallen` |
| VA-54 | done (C6) | Death card leads with the hero's face and name (no recolouring), the weapon's fate under it | `d4x0_death` |
| VA-55 | done | Report names the attackers, leads with the recorded contribution and notes; no MVP | device pass `living_report_*` |
| VA-56 | deferred | Bench visualisation optional | |
| VA-57 | done | Party rows: order, face, name, up/down buttons (48 dp) | device pass `ct_*` |
| VA-58 | done (C8) | Ending an era, dismissing a hero and melting: red confirm; retiring the guild, taking a tier, keeping home: gold primary | |

## Review corrections and additions

| ID | Applied |
|---|---|
| C1 | No floor-to-round task; the nav size is set per density with `nativeScaleDp` |
| C2 | Viewport figure dropped; Shop footer only on Shop |
| C3 | Scrolling kept where deliberate; discoverability fixed (peek, fade, scroll to selection) |
| C4 | Initial disabled confirm kept; hierarchy fixed |
| C5 | Weapon findings revalidated on the layered art (R-02) |
| C6 | Scroll restoration, approved faces, End Day wording and role-specific selection cues kept; no desaturation |
| C7 | One vocabulary (native px, whole device-pixel scale, effective dp) in `nearestWholeScale` and `SpriteScaleTest` |
| C8 | Switch, menu, padding and confirmation changes limited to demonstrated issues |
| C9 | No coverage percentage claimed |
| R-01 | `enemyFaction`/`enemyFace` read the bestiary's own faction; the six owner-requested portraits (three engines, hill giant, bone colossus, ash dragon) are drawn; skeleton soldiers, archers, the wraith and the lich share their faction's figure; every other unit has a name plate, never an initial. `EnemyFaceTest` pins it |
| R-02 | Code check: no completed weapon draws only its base; weapons draw at whole scale (44 dp sites so 420/440 dpi keep scale 2). Device: Forge, reveal, trays, Storage, Wall, shelf show metal, infusion and charm |
| R-03 | Covered: three densities, 1.0–2.0, three-button navigation, reduced motion, a new-era bestiary siege, legacy replay, retirement, claim and Begin era, fallen card. Not covered: loading/recovery/save-error screens, End Day preview dialog, Heartfire confirmation, loan picker, relic replacement, resume/quiet/tally/contract cards, replay inspector, TalkBack session, keyboard-open price editing, the real legacy screen after a defeat (the preview stops at a stand-in) |

## Open owner calls

- Storage rows stack at every phone width (the "List at N" button is wide); a shorter label would let them sit beside.
- The siege report's "Worth knowing" often repeats the recorded contribution in other words (`FightAccounting.observations` repeats the tier-1 standout); a core filter is a separate change.
- Debug scenarios that share a run id become unplayable after one of them is claimed ("The forge has fallen. This era is over."), seen with `guild_charter_eve` then `guild_siege_eve`; pre-existing.
- The run-end screen showed "Then begin era 2" before Claim and "Begin era 3" after on a constructed save whose account already had a claimed era; the number comes from `maxOf(legacy.nextEra, end.era + 1)` (pre-existing).
- The art session's new blessing icons read flatter than the old ones at row size.

## Checks

- Baseline `ad32de6`: `:app:testDebugUnitTest` 377/377.
- After the pass: `:app:connectedDebugAndroidTest` 105 on emulator-5562: 99 passed, the 6 failures fixed in their tests (`ConsistencyPassTest`, `M4ScreensTest` and `LayoutMatrixTest.siegeScreens…` also failed on untouched HEAD; `ForgeComparisonDeviceTest` and `StorageSheetTest` needed a scroll now that tiles and rows are taller; the new `LayoutMatrixTest.recordsAndNotebookTabs…` with a corrected overflow check), then 6/6 and the five classes 18/18.
- After the pass: `:app:testDebugUnitTest` 402/402 (new: `FitTest`, `SpriteScaleTest`, `EnemyFaceTest`, `SheetModelsTest`, cases in `ShopDayUiTest`, `GuildUiTest`, `GuildInspectTest`, `ResultsInspectTest`, `SiegeContributionUiTest`, `SiegeHeaderTest`, `ThreatAndRequestsModelTest`, `V2ShellTest`); `:core:test` 742/742.
