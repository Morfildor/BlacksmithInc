# 05 UI design, visual hierarchy, typography, clarity (judged from source, 2026-10-14)

Lens: design, hierarchy, type and clarity of every screen, judged from `app/src/main/java/com/example/blacksmithproject/ui/`
on `main` at a2e0392. Captures cited are earlier emulator runs kept under `docs/` (build of 2026-10-11 to 2026-10-14, Pixel
1080x1920, font 1.0); the device reviewer owns today's screens. Skills applied: ui-craft, mobile-app-ui-design, impeccable
(critique), material-3 (audit), android-review compose-ui. The project has no `DESIGN.md`; `ui/theme/` and `ui/Frames.kt`
were read as the token authority.

## Summary (10 lines)
1. "Thin text" is a weight problem, not a contrast problem: every body style is Roboto **Regular** (`theme/Type.kt:23-25`), and the most used style in the app is the smallest and lightest one (14 sp Regular in CreamMuted: `Secondary()` 148 calls plus 93 direct `bodySmall`). CreamMuted already reads 9.6:1 on a plate.
2. `Type.kt` leaves `headlineSmall` and the three display slots undefined, so the 20 unstyled `AlertDialog` titles render in the M3 default (24 sp Roboto Regular): the largest, thinnest text in the app, on the irreversible choices (opening confirm, relic, blessing, abandon, load scenario). One line fixes it.
3. 23 lines that carry the consequence of a choice (fee, "If it fails", posture rule, "No answer by End Day means", a branch's description) are set in `Secondary()`, the weakest style; the flavour above them is in Cream. The hierarchy is inverted on exactly the surfaces the owner called unclear.
4. `forgeRow()` draws its border in BronzeDeep at 1.6:1 on the panel (`Frames.kt:136`), on 32 rows, most of them tappable. That is why lists of choices (scenario list, charter picker, contract offers, visitor answers) look like unframed grey text on black.
5. The "choose one of N" surfaces use seven different patterns. The visitor sheet (`EncounterSheet.kt:119-142`: radio rows with label, cost, effect, blocked reason, the default stated, one Commit) is the right one; the opening chooser, charter picker, relic dialog, loan picker and the debug scenario list each do it differently and worse.
6. The opening chooser shows three gold plates on one scroll with each card taller than a screen, so the three packages cannot be compared; the charter picker hides three of four options behind arrows and the cost behind a disclosure.
7. The debug build's two "(debug)" outlines sit in the thumb zone directly under "New game" as peers of it (`ScenarioMenu.kt:55,60`); this is most likely the "scenario selection" the owner saw.
8. Of the 12 causes of 2026-10-10: 4 fixed, 4 partly (one regressed on the opening chooser), 2 open in code, 2 open for art. Batch 4's "every plain button goes through one of three components" has four regressions since.
9. Tokens-only edits (Type.kt slot coverage and weight, three Color.kt tokens, the 23 promoted lines, a dialog wrapper) ship without new layout work and change every screen; layout edits are the two choosers and the debug entries; art stays art.
10. No LOCKED rule is touched by anything below. Owner decisions: bundling a font family, and where the debug entries live.

## Ranked findings

| # | Sev | Defect | Action | Evidence |
|---|---|---|---|---|
| 1 | high | Dialog titles fall to the M3 default (24 sp Roboto Regular): `headlineSmall` is not in the theme | Define `headlineSmall` = Serif Bold 22/28 (and the display slots) in `Type.kt`; see the scale below | `theme/Type.kt:17-29` (no slot); 20 unstyled titles (grep `title = {` across `ui/`); `docs/run_variety_evidence/screens2/guild/04c_opening_confirm.png`, `docs/deep_forging_evidence/scenarios/relic_full_workshop_1_offer.png` |
| 2 | high | Body text is Regular (400) on dark plates; light-on-dark halation makes 400 read thinner than on paper | `bodyLarge/Medium/Small` and `labelMedium` to `FontWeight.Medium`; re-run `LayoutMatrixTest` and the 360 dp captures for wraps (Medium is a little wider) | `theme/Type.kt:23-25,27`; usage counts above; `docs/run_variety_evidence/screens2/scenarios/00_scenario_list.png` |
| 3 | high | Consequences set as secondary text, flavour as primary | Promote the 23 lines (list in detail C) to Body in Cream; keep `Secondary()` for provenance and status only | `GuildPanel.kt:321,328-329,331`, `ContractSheet.kt:114,132-133`, `EncounterSheet.kt:137`, `OpeningChooser.kt:420`, `HeroDetailSheet.kt:251`, `Dialogs.kt:165` |
| 4 | high | Row border BronzeDeep 1.6:1 on ForgePanel; tappable rows have no visible boundary (WCAG 1.4.11 asks 3:1 for a control's only boundary) | New token `BronzeLine = 0xFF6E5629` (3.0:1 on ForgePanel) for `forgeRow()` and option tiles; BronzeDeep stays for hairlines inside frames | `Frames.kt:136-138`; contrast table in detail B; 32 `forgeRow()` uses |
| 5 | high | Opening chooser: three `PrimaryActionButton`s on one scroll, each card over a screen tall, consequence said on the card and again in the confirm | Collapsed cards (name, build in plain words, two signers as faces, relic, one green line, one red line), one "Begin with …" plate pinned at the foot for the selected card; drop the confirm or make it the only place the drawback is read | `OpeningChooser.kt:399-423,425-434`; `docs/run_variety_evidence/screens2/guild/04_opening_chooser.png` |
| 6 | high | Charter picker: a carousel hides 3 of 4 options, "Charter 1 of 4" counts "No charter" as a charter, cost behind "About this charter", "Plays as: Protect, strike back, rebuild." and "The shop alone, as before." decide nothing | Four radio rows (EncounterSheet pattern): name, one line of what you get, one line of what it costs, "No charter" first for a new account; no disclosure | `CharterPicker.kt:274-298`; `docs/run_variety_evidence/screens2/guild/01_title_charter.png` |
| 7 | medium | Debug entries are peers of "New game" in the thumb zone; "Scenarios (debug)" is a stock `OutlinedButton` | Move both behind the gear (Settings sheet, debug section) or a long press on the version line; if they stay, one `InlineActionButton` under the menu, not two outlines | `app/src/debug/.../ScenarioMenu.kt:55,60`; `App.kt:112,195` |
| 8 | medium | Choice dialogs diverge: `RelicChoice` is a raw `OutlinedButton`, `LoanPicker` rows are bare clickable Columns, `StepButton` is a raw `OutlinedButton`, `SpeedRow` is the stock segmented control with its check icon (and no caller passes it) | `RelicChoice` → `BlessingOption`; `LoanPicker` rows → `forgeRow()` with a chevron; `StepButton` → `SecondaryActionButton` colours; delete `SpeedRow` or style it as `SegmentRow` | `EncounterSheet.kt:222-232`, `Dialogs.kt:423-432`, `detail/PriceEditor.kt:123-131`, `SettingsSheet.kt:100-113`, callers `App.kt:118`, `WorkshopScreen.kt:205` (regressed since batch 4, DECISIONS 3820) |
| 9 | medium | Explicit thinning: four `FontWeight.Normal` overrides on headings, the workbench title the only Serif Regular in the app | Remove the four overrides; the tray heading becomes Subheading in Cream | `ForgePanel.kt:234,327,402,403`; `docs/deep_forging_evidence/device_1.0/02_metal_tray.png` ("Choose metal") |
| 10 | medium | A kept branch choice fires from an inline button with no confirm, while a rank (also kept) gets a dialog | Same confirm as `RegionCard` ranks, or state "kept" on the button's detail line | `detail/HeroDetailSheet.kt:245-253` vs `GuildPanel.kt:390` |
| 11 | medium | Overline is built four ways (`Overline()`, inline uppercase label, `PaperSection` 1.5 sp tracking, `ShopDayTopBar` "DAY n") | One `Caps` role in `Type.kt` (`labelSmall`: Bold 12/16, tracking 1 sp); callers stop calling `uppercase()` + `letterSpacing` | `shopday/VisitCard.kt:42-47`, `EncounterSheet.kt:69,103`, `Dialogs.kt:324,368`, `shopday/ShopDayControls.kt:63` |
| 12 | medium | Six raw plate colours in `Frames.kt`, rarity and element colours in `WeaponStatCard.kt`, a scene cream in `CounterScene.kt`, `Sprites.signatureRing` duplicating `SceneGold` | Name them in `Color.kt` (detail B); no hue change | `Frames.kt:238,242,245,250`, `detail/WeaponStatCard.kt:57-71`, `shopday/CounterScene.kt:178`, `Sprites.kt:215` |
| 13 | medium | Chrome on the Shop: top bar + threat strip + picture strip + plate + shelf band push the lead card to about half way down; the empty shelf is drawn as six empty slots | Threat strip to one line when no siege is near; picture strip 64 dp while the shelf is empty (cause 8 of the earlier review, regressed since the strip) | `smoke/02_shop.png` (`docs/run_variety_evidence/screens2/smoke/`); `ForgePanel.kt:177-190` |
| 14 | low | 119 `Text(` calls with no style fall to `bodyLarge` 17 Regular; `bodyLarge` has 5 deliberate uses | Make `bodyLarge` = Body (16 Medium) so the unstyled ones land on the body role | grep `Text(` without `style =` across `ui/` |
| 15 | low | Risk is the one choice behind a `DropdownMenu` (hidden list, 240 dp items) beside a visible Quick/Advanced toggle | Three radio chips in a row, or the same segmented plate as Mode | `ForgePanel.kt:506-531` |
| 16 | low | `12.dp` appears 60 times and `Space` has no 12 | Add `Space.m12` or snap to `sm`/`md` | `theme/Spacing.kt:6-11` |
| 17 | low | Upgrade buy buttons read "N pts" (the cost, not the verb) | "Buy · N pts" with the cost as the detail line | `RunEndScreen.kt:92-99` |
| 18 | low | First frame is a stock spinner | The title word on the ground, or nothing | `App.kt:110` (cause 5 of the earlier review, still open) |

## A. Every "choose one of N" surface

| Surface | File:line | N | What must be read to decide | Consequence in one line? | Control weight | Verdict |
|---|---|---|---|---|---|---|
| Main menu: New game / Continue, Abandon | `App.kt:191-196` | 2 | status plate | Abandon: yes, in the confirm | one gold plate, one outline | good |
| Charter picker | `CharterPicker.kt:274-298` | 4 | one option at a time; cost behind a disclosure | no ("Plays as: …") | arrows + text link | poor: recognition fails, cost hidden |
| Opening chooser | `OpeningChooser.kt:399-423` | 3 | about 170 words a card, over a screen each | twice (card and confirm) | three gold plates | poor: no comparison, three primaries |
| Opening confirm | `OpeningChooser.kt:425-434` | 2 | 30 words | yes | two inline | thin title (finding 1) |
| Blessing dialog | `Dialogs.kt:387-407` | 2-3 + later | name + one line each | yes | `BlessingOption` outlines | fair; stock `AlertDialog` frame |
| Blessing on the day card | `shopday/DayCards.kt:343-355` | same | same | yes | same | good |
| Relic offer (two steps) | `EncounterSheet.kt:191-220` | 1-3, then held | name + one line | yes | raw `OutlinedButton` | fair; not the blessing's component |
| Visitor sheet | `EncounterSheet.kt:119-142` | 2-4 | label, cost, effect, blocked reason | yes, and the default | radio rows + one Commit | **the pattern to copy** |
| Contract: party | `ContractSheet.kt:84-103` | up to N | name, class, blade, blocked reason | order shown as a number | checkbox rows | good |
| Contract: posture | `ContractSheet.kt:105-117` | 3 | name + rule | yes | selectable rows, "· chosen" | good; rule line is secondary (finding 3) |
| Checkpoint Push / Return | `GuildPanel.kt:286-289` | 2 | three lines | yes | two equal outlines; the chosen one is the disabled one | fair: "chosen" shown only by disabling |
| Loan picker | `Dialogs.kt:414-437` | N members | name, role, carrying, reason | partly | bare rows, no affordance | poor |
| Loan from the hero sheet | `detail/HeroDetailSheet.kt:276-287` | N blades | name + summary | no ("Loan") | inline buttons | fair |
| Member branch | `detail/HeroDetailSheet.kt:245-253` | 2 | name + description | "A choice is kept" above, not on the button | inline "Choose", no confirm | fair (finding 10) |
| Ranks | `GuildPanel.kt:383-394` | 1-3 | name + description | yes, in the confirm | inline + confirm | good |
| Forge tray tiles | `ForgePanel.kt:319-396` | 3-9 | tile name, stock, mark; rule under the grid | yes (fight rule, siege mark) | tiles with a gold edge when chosen | good; heading thinned (finding 9) |
| Forge mode / Risk | `ForgePanel.kt:486-531` | 2 / 3 | label | Risk: in the dropdown only | segmented plate / dropdown | fair (finding 15) |
| Records pages | `WorkshopScreen.kt:325-340` | 3 | label | n/a | themed segmented | good |
| Settings speed | `SettingsSheet.kt:100-113` | 3 | label | yes | stock segmented, check icon; no caller | dead code or regression |
| Storage filters | `StorageSheet.kt:240-256` | many | chip label | n/a | stock `FilterChip` | fair: M3 default colours |
| Request Accept / Decline / Forge this | `ShopPanel.kt:198-222` | 3 | asks, why, terms, readiness | yes | one gold, two outlines | good |
| Forge result Store / List | `Dialogs.kt:177-198` | 2 | price block, afford line | yes | one gold, one outline | good |
| Resume prompt | `shopday/DayCards.kt:357-366` | 2 | two lines | yes | gold + outline | good |
| Scenario list (debug) | `ScenarioMenu.kt:72-88` | 39 | title + description a row | yes, in the confirm | `forgeRow` at 1.6:1, no chevron | poor-looking, debug only |

Three observations across the table. First, every surface that reads well uses the same three things: a row that is visibly a
row, the cost or rule in the same colour as the name, and one plate for the way forward. Second, the two surfaces a new player
meets first (charter, opening) are the two that break all three. Third, the consequence line is present almost everywhere
in the text; the fault is its style, not its absence.

## B. Typography and colour

**What is in use.** 12 M3 slots are referenced (grep `MaterialTheme.typography.`): bodySmall 93, bodyMedium 81, titleSmall 74,
titleLarge 35, labelMedium 31, titleMedium 29, labelLarge 27, labelSmall 16, headlineMedium 5, bodyLarge 5, headlineSmall 2,
headlineLarge 1. Overrides outside `Type.kt`: 22 `fontWeight =`, 2 `letterSpacing =`, 1 `.copy(shadow)`, serif set by hand on
the Gazette paper (`Dialogs.kt:266,294,309-311,327,341,357,373-374`), 4 `FontWeight.Normal` thinnings (finding 9). Two
families: `FontFamily.Default` (Roboto) and `FontFamily.Serif` (whatever the OEM ships: Noto Serif on Pixel). No `res/font`
folder, no font dependency in `app/build.gradle.kts`.

**Contrast on the plates (WCAG ratios computed from `Color.kt`; text needs 4.5:1, a control boundary 3:1).**

| Foreground | ForgeNight | ForgePanel | ForgePanelRaised | BronzeContainer | ForgeSlot |
|---|---|---|---|---|---|
| Cream F2E8D0 | 15.7 | 14.3 | 12.9 | 9.5 | 16.1 |
| CreamMuted C9BFA8 | 10.5 | 9.6 | 8.6 | 6.3 | 10.8 |
| Gold E8BC5A | 10.8 | 9.8 | 8.8 | 6.5 | 11.0 |
| Ember FF9A5C | 9.2 | 8.3 | 7.5 | 5.5 | 9.4 |
| Bronze A8803C (frame) | 5.3 | 4.8 | 4.4 | 3.2 | 5.5 |
| BronzeDeep 4A3A1E (row border) | 1.7 | **1.6** | **1.4** | 1.1 | 1.8 |
| Cream at alpha 0.5 (LoanPicker disabled) | 4.9 | 4.5 | 4.0 | **3.0** | 5.1 |
| disabled ink 9A927E on 2A2E38 plate | | 4.4 | | | |

So contrast is not the complaint: every text pair clears AA with room. Weight is: 400 on a dark ground loses apparent stroke
to halation, and the project put its most frequent text at 14 sp 400. The one real contrast failure is the row border.

**Proposed scale: six roles, every M3 slot assigned (replace `theme/Type.kt:17-29`).**

```kotlin
/** Six roles. Every slot is set so nothing falls to the Material default (dialog titles did). */
private val Sans = FontFamily.Default   // tier 2: FontFamily(Font(R.font.<family>_medium, FontWeight.Medium), Font(R.font.<family>_bold, FontWeight.Bold))
private val Serif = FontFamily.Serif    // tier 2: FontFamily(Font(R.font.<serif>_bold, FontWeight.Bold))
val Typography = Typography(
    // DISPLAY: the title screen and the run's end only
    displayLarge = TextStyle(Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    // HEADING: plate titles, sheet titles, card titles, dialog titles (headlineSmall is what AlertDialog reads)
    headlineSmall = TextStyle(Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    // STRONG: row titles, key numbers, the gold plate's label
    titleMedium = TextStyle(Sans, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    // SUBHEADING: a choice's name, a tray heading, a stat value
    titleSmall = TextStyle(Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    // BODY: everything a player reads to decide; bodyLarge is the same so unstyled Text lands here
    bodyLarge = TextStyle(Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    // SECONDARY: provenance, status, hints; always CreamMuted; never a cost or a rule
    bodySmall = TextStyle(Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    // BUTTON and CAPS
    labelLarge = TextStyle(Sans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(Sans, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.sp),  // CAPS: callers pass the text already uppercased
    labelSmall = TextStyle(Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)
```

Side effects to accept: the nav bar labels (`labelMedium`, `WorkshopScreen.kt:318`) and the "Charter 1 of 4" line become
heavier; `Overline()` and the two `PaperSection` labels drop their own `fontWeight`/`letterSpacing` arguments; the Gazette's
hand-set serif stays (paper is its own register). Risk: Medium is slightly wider than Regular; re-run `LayoutMatrixTest`
(`app/src/androidTest/.../LayoutMatrixTest.kt`) and the 360 dp / font 1.3 captures for wraps in pinned buttons (`ForgeAction`,
`ShopDayControls`).

**Colour and weight on a plate (the four rows every screen should follow).**

| Role | Colour | Weight | Use |
|---|---|---|---|
| Heading | Gold | Serif Bold | plate, sheet, card and dialog titles; one per surface |
| Name / strong | Cream | Sans Bold | a choice's name, a number, a row title |
| Body / consequence | Cream | Sans Medium | what it does, what it costs, what End Day will do |
| Secondary | CreamMuted | Sans Medium 14 | who said it, when, status words, hints |

Buff / flaw lines keep BuffGreen / FlawRed with their sign; Ember stays the warning colour (missesSiege, blocked).

**`Color.kt` edits (consolidation; no hue changes except the one border).**

```kotlin
val BronzeLine = Color(0xFF6E5629)       // row and tile borders; 3.0:1 on ForgePanel (BronzeDeep 1.6:1 stays for hairlines inside frames)
val TextDisabled = Color(0xFF8A8373)     // 4.5:1 on ForgePanel; replaces Cream.copy(alpha = 0.5f) (LoanPicker) and 9A927E (PrimaryActionButton)
// The gold plate (from Frames.kt:238-250)
val PlateTop = Color(0xFFF3CC6E); val PlateBottom = Color(0xFFD29F36); val PlateEdge = Color(0xFF5A3C0A); val PlateInk = Color(0xFF2A1A04)
val PlateDisabled = Color(0xFF2A2E38)
// Rarity and element (from WeaponStatCard.kt:57-71), SceneCreamSoft 0xFFE6D9B8 (CounterScene.kt:178); Sprites.signatureRing = SceneGold
```
Then `Frames.kt:136` `forgeRow()` → `border(1.dp, BronzeLine, RowShape)`; `ForgePanel.kt:287,372` tiles → `BronzeLine`.

**Pixel or slab font?** Not for body: GDD 12 asks for scalable, readable text and the app branches at font 1.3 and 2.0; a
pixel face breaks at every non-integer scale and has no 500 weight. The order of value is: (1) weight 500, zero assets,
ships now; (2) a bundled family in `res/font` only to pin the faces, because `FontFamily.Serif` is whatever the OEM ships and
`FontFamily.Default` differs on Samsung; (3) a pixel face for the title word and the top-bar numerals only, as art. Do not use
`ui-text-google-fonts`: it downloads through Play services at runtime, which the offline rule forbids. Candidates for (2) are
an owner decision (hypothesis, licences not checked from here): one humanist sans with 500 and 700 for body and buttons and
one bookish serif bold for headings, four files, under 1.5 MB on a 21.4 MB APK.

## C. The 23 consequence lines set as `Secondary()` (promote to Body, Cream)
`GuildPanel.kt:321` fee, `:328` "Bring", `:329` "If it fails", `:331` expiry, `:288` checkpoint note, `:363` candidate sub, `:391`
rank description; `ContractSheet.kt:114` posture rule, `:132` "Bring", `:133` "If it fails"; `EncounterSheet.kt:73` "Leaves at
End Day.", `:137` "No answer by End Day means …", `:168` relic status; `OpeningChooser.kt:420` the plan, `:110` signer class and
level; `detail/HeroDetailSheet.kt:241` share, `:251` branch description, `:269` loan summary, `:282` blade summary;
`Dialogs.kt:165` request terms; `InfoPanels.kt:179` counsel; `CharterPicker.kt:282,295-296` "Plays as", "You get", "You give
up" (bodySmall CreamMuted by hand). What stays secondary: `r.status`, `o.expiry`'s "posted …" half, "Leaves at End Day" if
the sheet's default line is promoted.

## D. The 12 causes of `docs/UI_UX_REVIEW_2026-10-10.md` section 1, today

| # | Cause | Status | Evidence today |
|---|---|---|---|
| 1 | Loop cut across surfaces | partly | price and afford line on the result (`Dialogs.kt:186`), stepper (`PriceEditor.kt`); the result is still a `Dialog` over the Forge, the shelf never in view while forging |
| 2 | Two gold primaries | partly, **regressed** on the opening chooser | End Day gold on Shop only (`WorkshopScreen.kt:386-396`); three gold plates in `OpeningChooser.kt:421`; Shop with a request and a visitor shows Accept + "Hear them out" + End Day (`ShopPanel.kt:211`, `EncounterSheet.kt:74`) |
| 3 | Price set without the purse fact | fixed | `PriceEditor.kt:103-111` afford line, red only when nobody can pay |
| 4 | Scroll reset, Forge auto-scroll | fixed | `App.kt` `SaveableStateProvider`; `ForgePanel.kt:323` `bringIntoView` only on `follow` |
| 5 | Stock M3 at the seams | open | 12 `AlertDialog`s with default titles (finding 1); spinner (`App.kt:110`); `FilterChip`, `StepButton`, `RelicChoice`, `SpeedRow` (finding 8) |
| 6 | Repetition | partly | one lead, threat on the strip only; still: the strip on Shop and Forge, "In a fight" rules in every tray plus field notes, drawback on card and confirm |
| 7 | Sale and refusal weigh the same | fixed | batch 3 (DECISIONS 3686): headline by reason, "Earned today" row |
| 8 | Chrome budget | partly, **regressed** by the threat strip | `smoke/02_shop.png`: lead card begins about half way down; strip is two lines at all times (`ForgePanel.kt:183-189`) |
| 9 | Vocabulary drift | partly | "Forge health", "Notebook", "Gazette", "visitors" settled; "Commissions" vs "request" still open (DECISIONS 3728 ruling), "Build: Storm and Charge", "Plays as:", "Unsung" |
| 10 | Back exits the app | fixed | batch 2 (DECISIONS 3665) |
| 11 | Two pixel scales | open, art | title art is now 1x pixel (`App.kt:162-163`); the Shop strip is still the HD concept image (`smoke/02_shop.png`) |
| 12 | Nothing strikes or rings | open, art and audio | reveal is a 700 ms hold and a 250 ms fade (`Dialogs.kt:157-159`) |

## E. Changes for the next patch, in order of value

**Copy and tokens only (no new layout; ship without new device captures beyond the font-scale matrix).**
1. `theme/Type.kt:17-29`: the scale above; every slot set, body at Medium, `headlineSmall` = Heading.
2. `theme/Color.kt`: `BronzeLine`, `TextDisabled`, the plate and rarity tokens; `Frames.kt:136` and `ForgePanel.kt:287,372` on `BronzeLine`.
3. The 23 lines in C from `Secondary()` to `Text(..., style = bodyMedium)`.
4. `ForgePanel.kt:234,327,402,403`: delete the `FontWeight.Normal` arguments.
5. `shopday/VisitCard.kt:44`, `EncounterSheet.kt:69,103`, `Dialogs.kt:324,368`, `ShopDayControls.kt:63`: use the Caps role, drop the per-call weight and tracking.
6. `Dialogs.kt:424-428` `LoanPicker` rows, `Dialogs.kt:428` and `Frames.kt:250`: `TextDisabled` instead of alpha.
7. `CharterPicker.kt:280` "Charter 1 of 4" → "Start 1 of 4"; `:282` "Plays as: …" → the advantage line; `:283` "The shop alone, as before." → "The shop alone: forge, price, sell. No guild."
8. `RunEndScreen.kt:94` "N pts" → "Buy" with the cost as `detail`.
9. `EncounterSheet.kt:224-231` `RelicChoice` → `BlessingOption`; `SettingsSheet.kt:100-113` delete `SpeedRow` (no caller) or restyle it as `SegmentRow`.
10. `detail/HeroDetailSheet.kt:251`: confirm dialog as `GuildPanel.kt:390`, text "A branch is kept for good."

**Layout.**
11. `OpeningChooser.kt:399-423`: collapsed cards (name, build in plain words, faces, relic, one + line, one − line, "The first days" behind a disclosure), one pinned "Begin with …" plate for the selected card, the confirm dropped.
12. `CharterPicker.kt:268-300`: four selectable rows in the visitor sheet's pattern; "No charter" first on a new account (the guidance's ruling, DECISIONS 4328); no "About" disclosure.
13. `app/src/debug/.../ScenarioMenu.kt:55-62`: the two entries out of the thumb zone (owner decision); the list rows with a chevron and the `built` word as a Caps tag.
14. `ForgePanel.kt:177-190`: one-line threat strip until the siege is within two days; `ShopPanel` picture strip at 64 dp while the shelf is empty (cause 8).
15. `ForgePanel.kt:506-531`: Risk as three chips beside Mode, no dropdown.
16. `App.kt:110`: the title word instead of the spinner.
17. `theme/Spacing.kt`: a 12 dp step (60 uses).

**Needs art.**
18. Cause 11: Shop and Forge backdrops at 1x for the real strip heights (`docs/ART_BRIEF.md`).
19. Cause 12: strike and reveal frames; audio is the owner's decision outside this plan.
20. Optional: a pixel face for the title word and the three top-bar numerals only.

## Owner decisions
- Bundle a font family in `res/font` (one sans at 500/700, one serif bold; OFL-licensed; about 1.5 MB), or stay on the device's Roboto at Medium? Trade-off: pinned faces on every OEM against four binary assets and a licence file.
- Where do the debug entries live: under the gear, or on the title as now? Trade-off: one tap more for testers against a release-looking title in every debug capture.
- The opening confirm dialog: keep it (one more read of the drawback) or drop it once the card states the drawback in Body? Trade-off: an extra tap on an irreversible choice against reading the same line twice.

## Prior findings checked
Fixed: causes 3, 4, 7, 10; batch 4's segmented Records row and button components. Still open: cause 5 (dialog titles, spinner, chips), 11, 12; DECISIONS 3228 decision 14 (button hierarchy) and the "Commissions / request" word. Regressed: cause 2 on the opening chooser, cause 8 by the threat strip, batch 4's "every plain button through one component" (`RelicChoice`, `LoanPicker`, `StepButton`, `SpeedRow`).
