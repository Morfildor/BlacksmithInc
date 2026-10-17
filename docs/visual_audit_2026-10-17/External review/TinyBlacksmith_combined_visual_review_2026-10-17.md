# Tiny Blacksmith: review of the visual audit and consolidated findings

Reviewed 17 October 2026. This document evaluates the supplied audit against the latest verified repository. It is a review and implementation handoff; no production files were changed.

## Verdict

The audit is useful and substantially right about compact layouts, text hierarchy, hero visibility, and the weak presentation of major game moments. Its evidence and source references make it a good starting point. **Accept its problem areas, but do not implement its recommendations wholesale.** Several root causes are incorrect, some numbers overstate the defects, some deliberate behavior is described as a bug, and the weapon findings predate a substantial asset change.

The implementation order should begin with usable layouts and reachable actions. The audit's impact-ranked Top 15 puts siege staging first, but that should not take precedence over broken text and controls. Preserve the title, approved hero faces, existing palette and fonts, clear numerical weapon stats, and the current layered weapons.

## 1. Repository and evidence baseline

| Item | Verified state |
|---|---|
| Repository | `C:\Users\tuncb\Desktop\Blacksmith Project`, remote `https://github.com/Morfildor/BlacksmithInc.git` |
| Local branch and HEAD | `living-heroes`, `ad32de6ba393cdaf9f77680b21a1cb5d302aa26c` |
| Remote verification | Read-only remote lookup returned that same SHA for remote HEAD, `main`, and `living-heroes` |
| Audited baseline | `e869c7d`, release 0.9.0, rules 8 / content 7 / balance 17 |
| Change since audit | One commit: `ad32de6`, layered weapons showing metal, infusion and catalyst, plus redrawn material icons |
| Tracked working files | No tracked modifications at inspection. Untracked: `GDD.md`, `build-output/`, the audit Markdown and its evidence directory |
| App | Kotlin / Jetpack Compose, portrait Android, offline. Current app version remains 0.9.0 |
| This review's verification | Read the supplied audit, brief, supporting inventories/scans; inspected cited screenshots/contact sheets and full-resolution examples; compared current source and assets; inspected repository design documents and Git history |
| Not performed | No fresh app build, installation, device interaction, accessibility session, or test-suite run. Historical screenshots establish the audited build's rendering, not fresh rendering of `ad32de6` |

The archive contains 1,066 files. It preserves the old APK and source, so it is a valuable historical baseline. The current UI changes mainly pass `WeaponLook` through existing surfaces; they do not resolve the layout problems. A normalized comparison, ignoring text line endings, confirms the relevant layout implementations remain in place.

The archive's instructions are evidence of the original assignment, not instructions executed during this review. Its “audit only” wording describes that historical assignment. The separate implementation prompt is for a future coding session.

### Reference documents

The bundle accompanying this report includes these original documents, unchanged:

| Ref | Document | Use |
|---|---|---|
| D1 | [Original visual audit](references/original/audit/VISUAL_UX_AUDIT_2026-10-17.md) | VA-01–VA-58, screenshots, original recommendations, gaps and roadmap |
| D2 | [Original owner brief](references/original/BRIEF.md) | Intended quality bar, severity definitions, visual verification and gameplay boundaries |
| D3 | [Package README](references/original/README.md) | Snapshot provenance, reproduction notes and reported owner constraints |
| D4 | [Token audit](references/original/agent_data/tokens/ui_audit_report.md) | Code-derived spacing, controls, typography and inset observations |
| D5 | [Screen inventory](references/original/agent_data/screen_inventory.md) | Surface/state discovery, routes and test tags |
| D6 | [Asset summary](references/original/agent_data/assets/summary.txt) and [raw drawable scan](references/original/agent_data/assets/drawables.csv) | Historical geometry measurements; not current asset totals |
| D7 | Current repository documents, copied into `references/current/` | Art direction, writing, current feature state and newer decisions |

D7 includes `HERO_FACES_V2_PLAN.md`, `UI_ART_REVAMP.md`, `ART_BRIEF.md`, `ART_NEEDED_LIVING_HEROES.md`, `ART_NEEDED_BESTIARY.md`, `WRITING_GUIDE.md`, `GDD_REVISION_2026-10-14.md`, `LIVING_HEROES_LEDGER.md`, `PROGRESS.md`, `DECISIONS.md`, `Tiny_Blacksmith_Living_Heroes_Final_Design.md`, `Tiny_Blacksmith_Peoples_Names_and_Siege_Bestiary.md`, `UI_UX_REVIEW_2026-10-10.md`, root `CLAUDE.md`, and `Tiny_Blacksmith_GDD.md`. Current source and explicit newer decisions resolve stale descriptions in older documents. For example, the older art brief still describes 20 identities, while the current face plan and content have 66 identities and 132 base/upgraded tiles.

## 2. Corrections to the review

### C1. VA-43's root cause and preferred fix are wrong

Both the archived source and current source already round to the nearest whole multiple. Current `ui/Sprites.kt:258–260` contains:

```kotlin
return with(density) {
    (maxOf(1, (hint.toPx() / artPx).roundToInt()) * artPx).toDp()
}
```

`PixelIcon` calls this helper. It does not floor. Do not create a “change floor to round” task.

There is still a real size discontinuity. `DestinationBar` requests **30 dp**, or 24 dp above font scale 1.3, for 32 px art (`WorkshopScreen.kt:320–350`). At 480 dpi, `30 × 3 / 32 = 2.8125`, rounded to 3, giving 32 dp. At 420 dpi, `30 × 2.625 / 32 = 2.4609`, rounded to 2, giving about 24.38 dp. At 440 dpi the same request gives about 34.91 dp. This is quantization around the requested size, together with an explicit smaller large-text size. Keep the symptom, correct the explanation, and evaluate the desired optical size across densities.

### C2. VA-01's viewport measurements are overstated

In the supplied full-resolution `f20_shop.png` (1080×2400 at 480 dpi), the content region is approximately y=650–1459, about **810 physical pixels / 3 = 270 dp**. The header is approximately y=156–646 and the footer starts near y=1460. The audit's quoted 50–80 dp is not the scroll viewport shown in that image. It may describe the useful remainder after individual content blocks, but it is not labelled that way.

The large header and footer still dominate, and enlarged content is laborious to read. Keep this as a high-priority accessibility/usability problem, but do not repeat the 50–80 dp figure or assert that all workshop destinations are unusable. Source also shows End Day's pinned footer only on Shop (`WorkshopScreen.kt:143–150`). The Forge has separate controls. Under D2's definition, demonstrated unreadable controls can remain P0; VA-01 alone needs functional reproduction before a blanket P0 claim.

### C3. VA-06 and VA-16 confuse scrolling with inaccessible clipping

At large text, `SlotRow` deliberately becomes a horizontal scroll container with 156 dp slots (`ForgePanel.kt:489–494`). The option tray is also deliberately horizontally scrollable. An off-screen Infusion slot or partial third tile is not proof that a choice cannot be reached. However, weak scroll discoverability, poor fit, and disappearing option art are valid concerns. Source explicitly passes `null` for option art above 1.3× (`ForgePanel.kt:552`).

Require all slots/options to be discoverable and usable, with clear labels and useful identity cues. Do not require three large-text slots on one line: that could introduce a new failure. Test the scrolling interaction before assigning blocker severity.

### C4. VA-07 identifies the wrong action hierarchy, not a broken initial disabled state

The screenshot shows “Choose a blessing” disabled because no choice has been selected. `BlessingChoices` correctly enables it only after selection (`DayCards.kt:375` onward). The real concern is that the persistent gold action is “Decide later” while confirmation sits inside the scrollable card. Preserve deliberate selection and confirmation; avoid making a blessing apply merely by selecting it.

### C5. VA-44 is obsolete as a task description, but clipping is not proved fixed

`ad32de6` replaces 336 flat weapon drawables with **768 aligned 56×56 layers**: 288 metal bases, 288 infusion effects and 192 catalyst charms. `Sprites.weapon` returns `WeaponLook(base, effect, charm)`; `PixelImage` and `PixelSprite` stack it. `WeaponSnapshot` now carries catalyst identity. Several UI model fields no longer hold a single resource integer.

The old file IDs and “175 of 336” total must not drive current changes. A fresh geometry check found 412 of 768 layers touching at least one canvas edge: 120 bases, 126 effects and 166 charms. **These are edge contacts, not 412 proven clipping defects.** Charm placement intentionally searches for space within the canvas, including its boundary. Compare visible silhouettes and glows with their source cells, then inspect actual composites. Re-slicing with an “inset” alone cannot restore pixels already missing from source art.

The 16 current material icons are all 48×48 and have no nontransparent pixels touching the canvas edge. Old observations about their silhouettes or detail require fresh screenshots. Preserve metal/infusion/catalyst layering and the current level rule.

### C6. Several recommendations would reverse existing decisions

- **Shop scroll restoration:** D1 §7 item 10 proposes resetting scroll on return. `WorkshopScreen.kt:132,152` intentionally saves each destination's position; the previous UI review explicitly requested that behavior. An old position is not inherently a defect. Preserve it unless a reproducible navigation/context problem establishes the need for a change.
- **Portrait style:** `HERO_FACES_V2_PLAN.md` explicitly approves painterly 64×64 portraits with softly blurred settings. Do not treat that softness as accidental asset damage or regenerate faces to force them into the icon style. Enlarge/frame appropriately without discarding identity or the approved backdrop.
- **Death treatment:** D1 VA-54 and Top 15 item 8 propose desaturation, while D3 reports an owner rule against recolouring faces. Keep the memorial goal, not that treatment. Existing replay down-state filtering is a separate current behavior to evaluate, not permission to extend recolouring elsewhere.
- **End Day wording:** “Face the siege” may imply skipping trade and expeditions, although End Day resolves the whole day. Keep semantic accuracy and the current Writing Guide. Distinct siege presentation does not require that label.
- **Selection cues:** Tabs, radio choices and action buttons have different roles. A universal gold-border treatment would remove useful distinctions. Preserve visible non-colour cues and accessibility selection state.

### C7. The proposed size policy has inconsistent units

D1 §4.4 alternates between “2 art px per dp” and “2 dp per pixel”; these are inverse ratios. It also recommends a 128 dp reward weapon elsewhere, but says 56 px weapon art at 2× in the reward. At 480 dpi, 56×2 physical pixels is **37.33 dp**, not 128 dp. The portrait tiers 2×/3×/6× produce 42.67/64/128 dp at 480 dpi, but about 48.76/73.14/146.29 dp at 420 dpi.

Use one explicit vocabulary: native art pixels, whole device-pixel scale, and effective displayed dp. The objective is consistent optical size and quality within the available layout. The audit's numeric recipes are examples requiring reconciliation, not a ready specification. `PortraitTile` also adds a frame and padding, so its total footprint exceeds the image size; it is not a drop-in replacement for every current `PixelImage` call.

### C8. Do not mistake standard controls or purposeful spacing for defects

- Settings switches are already explicitly coloured gold/bronze in `SettingsSheet.kt`, not unthemed defaults. The nested frame is visible, but replacing the switch is an optional style choice.
- Forge dropdowns already set `containerColor = ForgePanel` (`ForgePanel.kt:286,780`). Shape/elevation may still differ; “default Material surface” is too broad.
- A 16 dp screen gutter plus 16 dp inside a frame is not automatically double padding. D1 VA-29 is a useful width-budget concern, not proof that all frame interiors should lose their padding.
- 48 and 52 dp minimum button heights can be appropriate for different roles. Equivalent adjacent actions should align; a global exact 52 dp height would risk clipping at 2.0×. Treat heights as minimums.
- “The dialog covers the wordmark” is not a defect by itself; a modal necessarily covers background content. Confirmations already exist, including hero dismissal (`HeroDetailSheet.kt:432,496–503`). Improve consequence clarity and action hierarchy without adding duplicate confirmations.
- Continue is already a gold primary and End era a secondary on the title (`App.kt:168–169`). The confirmation actions are less differentiated, but D1's claim of equal title-button weight is inaccurate.
- Disabled visitor options already say “Not possible” and give the cost shortfall. Their appearance can be clearer; do not imply they execute or lack an explanation.

### C9. The audit's coverage count and scores are approximate

D1 says “58 of about 75 surfaces,” while D5 contains 88 inventory rows, including debug tools and overlapping states. Neither is a clear coverage denominator. Its quality scores are editorial judgments, not a reproducible release metric. Keep the screen/state inventory and the disclosed gaps; do not present 58/75 as a measured coverage percentage.

## 3. Important additions and gaps

### R-01. New bestiary replay identities are not covered by the legacy mapping (high priority, code-inferred)

`ui/replay/ReplaySupport.kt:31–46` builds enemy-to-faction lookup only from legacy siege leaders, elites, grunts and preparation units. `GuildContent.kt:639–643` still lists IDs such as `bone_warden`, `hollow_shambler` and `pale_knight` there, alongside the separate new Bestiary. `CombatContent.kt:485` includes `Bestiary.units`, with IDs such as `pp_zombie`, `pp_skeleton_guard` and `cap_lich`.

Those new IDs have no entry in `factionOf`; the lookup can return a null picture and the replay displays an initial. This is a source-confirmed mapping gap, not a freshly observed rendering defect. It is broader than VA-42's shared legacy shambler picture.

The deeply exercised scenarios `guild_siege_eve`, `guild_stormwell` and `guild_charter_eve` all decode to **livingRevision=0**. Therefore the siege/party/run-end screenshots do not establish the corresponding new-era experience. `siege_long_assault` is revision 1, but covers classic Town, not a new-era guild replay.

The 48 dedicated bestiary images are still an art backlog. Their exact asset IDs are absent from shipped drawables, and `generate_assets.py` does not enumerate them. `ART_NEEDED_BESTIARY.md` says placeholders are generated, but that statement does not match current assets/tooling. Require meaningful enemy identification with the available approved art and truthful fallbacks; separate missing artwork from lookup behavior. Do not promise 48 finished images or invent enemy identities.

### R-02. Revalidate the new layered weapons in real presentation contexts (high priority, verification)

The latest commit was not covered by the original screenshots. Its `WeaponArtTest` verifies resource presence and lookup/layer combinations, not visual registration, edge integrity, meaningful metal differences at phone size, or overlay alignment. `PROGRESS.md` explicitly notes weak silver/moonsteel differentiation on bow levels 2–4.

Check representative base/effect/charm combinations in the Forge, reveal, inspect card, stock, Storage, equipment, Notebook and shop-day shelf. Include bows/staffs, higher levels, dark and bright infusions, and all four catalysts. Check historical snapshots too. Do not regress composite art to `.base` in contexts meant to show an actual completed weapon.

### R-03. Expand verification beyond the screenshots already supplied (high priority, coverage)

Carry forward D1 §5.2's unrendered states: loading/recovery/save errors; End Day preview; Heartfire confirmation and other advanced Forge states; loan/replace dialogs; resume/quiet/tally/contract beats; replay inspector; Claim and Begin era. Add new-era personal offers, loan-return/departure warnings, autonomous speciality information and a new bestiary siege. These are validation targets, not requests for new gameplay.

Also test three-button navigation, price input with the keyboard open, long names/content, and reduced motion. A still capture cannot prove transition timing (VA-21), hit bounds (VA-47), viewport reachability or the reported accidental swipe. Distinguish intentional viewport cuts/fades from permanently obscured content. Prioritize preservation of readable navigation labels, selection cues, clear lock reasons and consequences before decoration.

## 4. Disposition of every original finding

**Keep** = supported problem retained. **Revise** = useful concern, but inaccurate cause, scope, severity or prescription. **Revalidate** = needs new rendering/interaction or asset inspection. **Optional** = styling opportunity, not a demonstrated functional defect. Groups retain every original ID without treating duplicates as separate projects.

| ID | Disposition | Consolidated decision |
|---|---|---|
| VA-01 | Revise, high priority | Reduce large-text crowding; correct viewport numbers and distinguish Shop from other tabs. C2 |
| VA-02 | Keep, high priority | “2 new” becomes unreadable at 2.0×; commission access/count must remain understandable |
| VA-03 | Keep, high priority | Notebook labels break mid-word at ordinary sizes; source tests fontScale rather than available width |
| VA-04 | Keep, high priority | Records labels clip/overlap as text grows; preserve page discovery and selection |
| VA-05 | Keep, high priority | Stock/Storage/Supplies/defender rows squeeze labels and values; layout must account for actual container width |
| VA-06 | Revise | Scroll exists; fix discoverability/retained identity cues, verify reachability. C3 |
| VA-07 | Revise, high priority | Fix blessing action prominence/reachability; disabled confirmation before selection is correct. C4 |
| VA-08 | Keep | Fractional enlargement occurs; make face presentation deliberate and verify on phones without replacing approved art |
| VA-09 | Keep as composition | Hero recognition is weak in selection/detail contexts; no universal 128 dp requirement |
| VA-10 | Keep, code-inferred | Replay explicitly uses bilinear enlargement; validate actual sharpness and correct the presentation policy |
| VA-11 | Revise | Coarse banners and mixed treatments are visible; approved painterly faces remain intentional; resolve units. C7 |
| VA-12 | Keep as major composition | Make siege timing/preparation visually distinct without changing End Day semantics |
| VA-13 | Keep | Hero sheet buries health/equipment/identity in prose; preserve accurate effects, terms and history |
| VA-14 | Keep | Counter crop/large-text sliver is visible; choose readable composition within the viewport |
| VA-15 | Keep | Practice-fight link/chevron has poor fit at compact width |
| VA-16 | Revise | Partial scrolling tiles need a clear affordance, not blanket “fit three.” C3 |
| VA-17 | Keep as consistency | Existing pixel settings art can serve a coherent presentation; preserve contrast and target size |
| VA-18 | Optional | Info control restyling is secondary; retain obvious inspect access and semantics |
| VA-19 | Revise | Unify decorative chevrons/duplicate rarity marks where useful; retain meaningful signs, words and non-colour cues |
| VA-20 | Keep | Forge reveal should lead with the reward and useful stats; use current layered art |
| VA-21 | Revalidate | Screenshot suggests overlap; reproduce motion and reduced-motion timing before claiming a transition bug |
| VA-22 | Keep | Notice can obscure the preview; ensure feedback is readable without hiding required content/actions |
| VA-23 | Keep | Header structure and numeric changes cause movement; prioritize stable reading and large-text fit |
| VA-24 | Revise | Guild icon and simultaneous guidance/selection deserve clarity; do not reuse an unrelated icon solely because it exists |
| VA-25 | Revise | Repeated guidance consumes space; Done waits for deliberate completion. Preserve guidance state and user access |
| VA-26 | Keep as consistency | Clarify section/group hierarchy without eliminating legitimate Gazette/moment typography |
| VA-27 | Revise | Consistency by interaction role; avoid a universal treatment that erases tab/radio/action distinctions |
| VA-28 | Revise | Align equivalent actions and primary hierarchy; 48/52 minimums are not inherently defective |
| VA-29 | Revise | Review effective text width, frame safety and gutters; do not delete all inner padding. C8 |
| VA-30 | Revise / optional | Nested Settings framing is a style/space concern; switches already have house colours |
| VA-31 | Optional | Price field visual integration; preserve label, numeric validation, steppers, IME and focus behavior |
| VA-32 | Revise / optional | Menus already use panel colour; shape/elevation integration can be improved |
| VA-33 | Optional composition | Continue startup's visual identity without sacrificing readable selection or adding redundant art |
| VA-34 | Keep | Starting kit information is lengthy; keep costs/tradeoffs explicit while improving scanning |
| VA-35 | Revise | Improve Gazette hierarchy/type consistency if justified; verify apparent bottom clipping by scrolling; retain factual events |
| VA-36 | Revise | Short-card staging can improve; empty space and a wrapping bar alone are not bugs; protect large-text reading space |
| VA-37 | Keep | Visit reasons and receipts repeat information; concise presentation must retain decision-relevant facts |
| VA-38 | Keep | Quest costs, risk, timing and rewards need scanning hierarchy; region art is optional |
| VA-39 | Revise | “Sold out” wording can improve; keep a concise visible reason for locked/unavailable items, not only behind inspect |
| VA-40 | Revise | Unaffordable options already explain why; improve distinction without removing the reason or inventing portraits |
| VA-41 | Revise | Army identity should be visible; 48 specific bestiary sprites do not exist yet. R-01 |
| VA-42 | Revise | Shared legacy faction art is expected fallback, not proven wrong asset mapping; new bestiary mapping gap is R-01 |
| VA-43 | Reject stated cause; retain symptom | Already rounds. Reassess hint/constraint/quantization behavior. C1 |
| VA-44 | Superseded; revalidate | Replace 336-flat-sprite task with inspection of current 768-layer composites. C5 / R-02 |
| VA-45 | Revalidate | All 12 faction assets touch side edges; source comparison is needed to establish actual truncation |
| VA-46 | Keep as low-priority cleanup | Small portrait maps are empty; no-op parameter is not a player blocker or a reason to generate new crops |
| VA-47 | Keep, code-inferred | Declared 40/44 dp minima are below the desired target; verify actual touch bounds and spacing |
| VA-48 | Optional | Dedicated splash mark could improve quality; after functional and major composition work |
| VA-49 | Optional / accept | Slight softness of title painting does not justify regenerating an approved successful screen |
| VA-50 | Revise | Unused assets are candidates, not defects. Wire only art that clarifies a real surface; do not add decoration to clear a list |
| VA-51 | Split | Replay event/control clipping and space need validation; a wall stage is a premium presentation improvement |
| VA-52 | Keep as premium composition | Run end should distinguish defeat from success, with claim/perks/next era still obvious and accurate |
| VA-53 | Keep as premium composition | Fallen state can carry consequence more clearly; no invented loss facts |
| VA-54 | Keep goal; reject recolouring | Memorial hierarchy and identity, using approved faces. C6 |
| VA-55 | Keep as premium composition | Report should prioritize outcome/contributions; no invented MVP or metric unsupported by records |
| VA-56 | Optional | Bench visualization should retain the clear simulation label and never write history or consume gameplay RNG |
| VA-57 | Keep as composition | Keep selected party recognizable and order clear; retain accessible reorder controls |
| VA-58 | Revise | Confirmation styling/consequences can improve; confirmations and title hierarchy already exist. C8 |

## 5. Consolidated priorities

This is the revised Top 15, ordered for implementation value. Each item is an outcome, not a prescribed component architecture.

1. **Readable compact and large-text UI:** VA-01–06, VA-15–16. Tabs, rows, Forge choices and key actions remain readable and reachable.
2. **Blessing selection with a clear confirmation:** VA-07. Preserve optional deferral and the existing commit boundary.
3. **Current-era enemy identity:** R-01, VA-41–42. Validate new bestiary fights as well as legacy records.
4. **Coherent, recognizable hero presentation:** VA-08–10, VA-13, VA-46. Preserve the approved identity/style and fit larger art to its role.
5. **Correct, legible layered weapons:** R-02, VA-44, relevant part of VA-11. The latest art must survive the UI pass.
6. **Forge reward and feedback:** VA-20–22. Reward first, accurate stats, clear actions and sound transitions.
7. **A stable workshop and meaningful siege state:** VA-12, VA-14, VA-23–25. Avoid solving emphasis by expanding fixed chrome.
8. **Readable replay and useful report:** VA-10, VA-51, VA-55. Information/control fit before scenery.
9. **Shared visual conventions by role:** VA-17–19, VA-26–32, VA-47, VA-58. Preserve semantics and responsive minimum sizes.
10. **Scannable quests and recognizable parties:** VA-38, VA-57. Costs, away/return days and risk remain explicit.
11. **Clear Supplies and visitor choices:** VA-39–40. Show availability and its reason before commitment.
12. **Readable daily storytelling:** VA-36–37, VA-53–54. Differentiate sales, deaths and defeat without blocking reading or adding false facts.
13. **A useful Gazette front page:** VA-35. Editorial hierarchy from recorded events, with existing details available.
14. **Cohesive setup and kits:** VA-33–34. Better continuity and scanning without hiding drawbacks.
15. **Distinct run endings and selective finishing work:** VA-52, then VA-45, VA-48–50, VA-56 as supported. Keep claim, perks and Begin era comprehensible.

Coverage R-03 applies throughout, not as a final cosmetic pass. Avoid one enormous global refactor: shared changes still need screen-level visual checks. Original S/M/L estimates are approximate and should be reassessed after reproducing the problems.

## 6. Completion evidence required from implementation

- Re-pin HEAD and compare any newer changes before editing. Preserve unrelated work and the newer layered art.
- Use a finding ledger with original IDs, C1–C9 corrections, R-01–R-03, status, affected files, and evidence. Superseded, accepted, rejected and blocked items must be explicit.
- Build and run the changed app. Required baseline screens: Shop, Forge/choices/reveal, Records/Notebook, Storage/Supplies, hero detail, party/Wall, shop-day visit/blessing, replay/report and run end.
- Capture 360×800, approximately 393×852 and 412×915 dp at ordinary text; exercise compact width at 1.3×, 1.5× and 2.0×. Include 420/440/480 dpi where art quantization matters. Cover gestures and three-button navigation on representative cases.
- Check actual scrollability, hit/focus bounds, selected/disabled accessibility state, TalkBack labels/reading order, keyboard/IME, long content and reduced motion. Do not equate scroll viewport boundaries with clipping defects.
- Exercise a natural new living-era guild journey through a real new bestiary siege; retain legacy scenario/replay coverage. Check death, defeat, successful retirement, claim once and Begin era. Current tests and fixtures may require updates, but assertions must remain meaningful.
- Relevant repository checks: `:app:testDebugUnitTest`, `:app:assembleDebug`, targeted instrumentation for affected flows; `:core:test` when shared models/content/replay integration are touched. Treat documented past test results as historical, including the audit's old ShopPanel-failure note. `runend.sh` still matches obsolete “Begin a classic run” text; do not count that script as current evidence without updating it.
- Record current results and screenshots honestly; update the required progress/ledger/changelog documents for actual user-visible implementation. Do not change gameplay, balance, save compatibility, RNG streams, appearance IDs or content identities merely to achieve a visual target.

The companion [implementation prompt](TinyBlacksmith_implementation_prompt_2026-10-17.md) incorporates these decisions and references the source documents. No implementation or fresh device verification is claimed by this report.
