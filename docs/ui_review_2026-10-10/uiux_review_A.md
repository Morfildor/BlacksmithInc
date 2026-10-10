# Tiny Blacksmith: UI/UX design review A

Independent review of the 0.7.0 build from emulator screenshots (Pixel, 1080x1920, 420 dpi) and the Compose source under `app/src/main/java/com/example/blacksmithproject/ui/`, `GameViewModel.kt` and `core/.../shopday/Lines.kt`. Sprites and painted backdrops are out of scope (the artist is redrawing them); composition, hierarchy, components, copy and flow are in scope. The locked rules (no character control, no combat control, shop actions only) are taken as given.

## Coverage note (read first)

The capture script lost the app twice, and the folder was rewritten between passes, so several file names do not match their contents. Every image was opened and checked before it was cited.

- Reliable captures and what they show: 01 (loading spinner), 02 and 03 (Shop day 1), 04, 05, 06, 08 (Forge steps), 07 (first forge result), 09 (Shop with a blade), 10 and 11 (Town), 12, 13, 14 (Records), 25 (labelled "forge result 2" but shows run C's "The shop opens" card with three blades at 80, 112 and 132 and "5 customers came to the counter today"), 24, 28, 28b and 29 (all four show the main menu, "Era 2 · Day 2", "Continue run", "Scenarios (debug)"), 32 (labelled "main menu" but shows the loading spinner again), 40, 41, 42 (identical to 41) and 43 (all show the Resume prompt, "Day 1 is done and saved. The shop took 324 gold.", at the normal phone size; whether the 1.3 font scale was applied cannot be told from the frame), 50, 51, 52 (720x1280 frames of the same Resume prompt, so the small-screen Shop, Forge and day card were not captured).
- From the first pass, before the folder was rewritten, these were read and are cited by their original names: run A's "The shop opens" and "Customer 1 of 1" cards (20_day_card_01 and 20_day_card_02 at 12:15), run A's tally and till (20_day_card_03 and 20_day_card_04), run B's quiet-day and Tomorrow cards (20_day_card_01 and 20_day_card_02 at 12:24), the Gazette dialog (21), run B's Shop day 2 (22 and 23 at 12:25), the Quick-mode forge in its ready state (24 and 26 at 12:24, byte-identical) and the second forge result (25 at 12:24). None of those frames exists any more; the names now hold other frames, so every first-pass citation below carries its capture time.
- Not seen at all (launcher wallpaper or app drawer in the current folder): 09b (Shop with three blades), 20_day_card_01 (current), 22 and 23 (current), 26 (Supplies sheet), 27 (Storage sheet), 30 and 30b (hero sheet), 31 (Settings sheet). Also never captured in any pass: the Advanced forge, the blade sheet and its price editor, the hero sheet, Settings, Storage, Supplies, run end, load failure, the day cards at font scale 1.3, and the Shop, Forge and day card at 360x640 dp. Those are judged from source only and are marked "judged from source" wherever cited.
- The build and the source tree are not the same revision: the captured main menu has a "Scenarios (debug)" button that does not exist anywhere in `app/src/main/java`, and no "Abandon run" button, which `App.kt` `MainMenu` draws for a run in progress. Source-only judgments may therefore lag the build slightly.
- Three different runs are mixed: run A (besieger Ashclaw Raiders, a Flaming Iron Sword listed at 128, three customers, zero sales), run B (besieger Hollowbound, an Iron Sword kept in storage, a quiet day with five visitors, zero sales) and run C (three blades listed at 80, 112 and 132, five customers, 324 gold taken, seen only on the open card and the Resume prompt).
- The first pass's 22 and 23 were blank white frames; with a dark `windowBackground` in `res/values/themes.xml` that points at a crash or a lost window rather than a theme flash. The second pass produced real frames at those names and the third pass produced the launcher, so treat it as a capture-script problem unless it recurs on a device.

## 1. Verdict

Tiny Blacksmith's interface is built with unusual care (semantics on everything, 48 dp targets, a reason on every disabled button, deterministic cards built from records) and its bespoke layer, the bronze-framed plates, the gold action plate, the ten-segment bars, the counter scene and the paper Gazette, is credible enough that a player raised on Slay the Spire or Potion Craft would settle in at the Shop and the Forge. The trust breaks at the seams, where stock Material 3 shows through: the Gazette's orange pill button, the check-marked segmented tabs, three different secondary-button styles on one screen, an AlertDialog for the town's blessing and a Material spinner as the first frame of the game. Structurally the chrome is heavy (26% of the usable screen on the Shop before the painted strip, with End Day pinned even over Records and Town) and the two peak moments of the loop, the forge reveal and the customer at the counter, are drawn with the same framed text plate as the settings sheet. In two of the three captured runs day 1 ended with zero sales (run C, with three blades at 80, 112 and 132, took 324 gold), and in run A that happened because the player did exactly what the game suggested ("List at 128") while the fact that nobody at the counter could pay it sat two screens away under a table called "Who is buying". The single biggest opportunity is to give the price decision and the reveal their own register: show "N of 12 can pay this" wherever a price is set, and turn "Fresh from the forge" into a two-beat reveal (sprite and name, then the card) so that the loop's one real decision is legible and its one real reward is felt.

## 2. Heuristic scores (Nielsen, 0 to 4)

| # | Heuristic | Score | Key issue |
|---|-----------|-------|-----------|
| 1 | Visibility of system status | 3 | Day, gold and energy are always on the top bar and End Day says what it costs; but forge integrity is an unnamed "Forge 100" on the Forge threat line (`ForgePanel.kt` `ThreatLine`), a working save is shown only as greyed buttons, and the first frame is a bare spinner (`App.kt` `UiState.Loading`). |
| 2 | Match between system and the real world | 3 | Shop, forge, till, shelf, purse and the Gazette prose are strong; "Seats 6", "Unsung (0)", "hale", "Restless", "Sound", "Dearest" and the rarity glyphs are not. |
| 3 | User control and freedom | 3 | Back steps one card, Skip day is one tap, Store or List is a real choice, Unlist exists; Decline on a request is irreversible with no confirmation (`ShopPanel.kt` `RequestCard`), and End Day sits 4 dp above the tab bar with no guard (`WorkshopScreen.kt` `EndDayButton`). |
| 4 | Consistency and standards | 1 | Three secondary-button vocabularies plus stock pills; "WORTH DOING FIRST" gold on the Shop and ember on the Tomorrow card; "Supplies", "Supplier" and "Supplies and tools" for one sheet; coaching lines that disagree ("jumps to tomorrow" vs "jumps to the evening"). |
| 5 | Error prevention | 2 | The forge button is disabled with a reason and out-of-stock chips are disabled; but the reveal's default action lists a blade at a suggested price no visitor could pay (run A, 20_day_card_02: "Could pay up to 90 gold; the cheapest blade is 128 gold"). |
| 6 | Recognition rather than recall | 2 | The anvil plate carries the recipe, and augment chips carry the besieger's +/- mark (good); but after picking Sword the plate still says "Nothing on the anvil" and the Family step has scrolled away (05), and the purse facts that decide a price live on the Shop, not where the price is set. |
| 7 | Flexibility and efficiency | 3 | Quick and Advanced forge, "Forge this" from a request or a want, one-tap "List at", bulk Storage actions, "Use this recipe", Skip day, 1x and 2x; the 600 ms reveal fade cannot be skipped except by the reduced-motion setting. |
| 8 | Aesthetic and minimalist design | 2 | The Town header is a six-fact dump in secondary text (10); the shop-day screen says "Day 1" twice and "The shop opens" twice before the card says "The shop is open" (run A 20_day_card_01); "LOOKED AT" prints a comma-spliced factor list. |
| 9 | Help users recognise, diagnose and recover from errors | 3 | The load-failure and save-failure screens (`Failures.kt`) are exemplary; the in-play `ErrorDialog` wraps a developer string ("Not enough energy (need 2, have 0, overwork left 0).") in "The forge says" and "Alright". |
| 10 | Help and documentation | 2 | Three one-shot tip banners and a coach line, none re-readable; the Journal's knowledge states are a glyph (?, half moon, dot, star) and a one-word label; "pairing", "fit to stand at the walls" and "the forge falls" are never explained. |
| | **Total** | **24 / 40** | **Band: fair. A solid pre-release build whose deficit is concentrated in consistency, recognition, minimalism and help, not in fundamentals.** |

## 3. Anti-pattern and slop verdict

The bespoke layer passes. `Frames.kt` (`forgeFrame`, `PrimaryActionButton`, `StatBar`, `EffectRow`), `CounterScene.kt` (the bust behind the counter, the plank front, the name plate, the shelf that lifts a looked-at blade), the paper Gazette (`Dialogs.kt` `DayReportDialog` with fixed ink colours) and the custom top bar and lit-plate destination bar (`WorkshopScreen.kt`) are coherent with each other and with the painted strips. A premium-game player would not pause at the Shop, the Forge steps or the counter.

The seams fail the test. These are the exact elements that read as "an app wearing a game's clothes":

1. The Gazette's **Close** button (21): `Button(onClick = vm::closeGazette ...)` in `DayReportDialog` has no shape override, so it ignores `ForgeShapes` and renders as the M3 full-pill in ember orange on top of the parchment. It is the only orange pill in the game and it sits on the most diegetic surface. The same stock pill appears in `ErrorDialog` ("Alright", `Dialogs.kt`), `LoadFailedScreen` ("Try again", `Failures.kt`) and `SaveFailureDialog` ("Try again", `Failures.kt`).
2. **Check-marked segmented tabs** (12, 13, 14): `SegmentRow` in `WorkshopScreen.kt` uses `SegmentedButton` with its default icon, so the selected tab reads "✓ News". The same control is used for Mode and Risk on the Forge and for shop-day speed in `SettingsSheet.kt`. A check mark inside a tab is a form control, not a game tab.
3. **Three secondary-button vocabularies on one screen.** Shot 23 at 12:25 shows `PrimaryActionButton` (Accept, gold plate), `SecondaryActionButton` (Decline, Forge this: gold text, bronze border, `Frames.kt`) and, elsewhere in the same build, raw `OutlinedButton`s with M3's default content colour (Store in 07, Back in every day card, Close in both sheets, Unlist, Abandon run, Main menu, Watch the fight, Skip to tomorrow, the blessing choices). Add the ember `TextButton`s (Got it, Read the Gazette, Decide later, Forge this on the Forge, Salvage, Hone, Arm the watch; Skip day is the same control in cream) and a player sees four ways to say "not the main thing".
4. **Two accent colours for "primary".** M3 `primary` is Ember (orange) and drives every TextButton, the outcome chip, `Overline(strong = true)` and the Gazette pill; the game's own primary is Gold (the plate, headings, "Open ›"). "WORTH DOING FIRST" is gold in `LeadCard.kt` (02) and orange in `VisitCard.kt` `Overline(strong = true)` (20_day_card_02 run B).
5. **The town's blessing as an AlertDialog** (`BlessingDialog`, `Dialogs.kt`): a reward moment rendered as a system alert with a title, outlined buttons and a trailing TextButton. The shop-day version (`BlessingChoices` in `DayCards.kt`) is better because it lives in the card.
6. **Material spinner as the first frame** (01): `CircularProgressIndicator` on `ForgeNight`. The window already has the right background; a spinner tells the player this is an app.
7. **Product-UI phrases**: "Got it", "Open ›", "Decide later", "Alright", "Select blades", "Done". Each is fine in a settings app; together they thin the shop voice that `Lines.kt` works hard to establish.
8. **One register for everything.** `FramedPanel` is the lead card, the anvil plate, the reveal, every shop-day card, the Town threat, the hero sheet header, the run-end summary and the Settings sheet. The reveal and the customer card therefore look like Settings. This is not slop as such, but it is why the peaks feel flat.

Material 3 components that are fine as used: `ModalBottomSheet` (square-ish corners, 48 dp handle), `FilterChip` (takes the 4 dp shape, carries sprites and +/- marks), `Switch` in Settings, `HorizontalDivider`, `NavigationBar` with the custom indicator.

## 4. What is working

1. **The accordion forge** (`ForgePanel.kt` `Step`, `ForgeSummary`): the first unfinished step opens, a pick advances to the next, chosen values collapse into the header in gold, the anvil plate shows the recipe and the one gold button, and the disabled button explains itself ("Choose a family, a core and an augment.", "Costs 2 energy. Ready when you are."). A known recipe is three taps and the draft survives a forge, so the second blade is two.
2. **The counter scene and the shelf band** (`CounterScene.kt`, `ShelfBand`, `WeaponSlot`): a framed bust behind a plank counter with a name plate, blades on a shelf that lift when looked at, dim when sold and carry a price tag. It is the one place the game feels like a place, and it is reused on the Shop so the plan and the day share a stage.
3. **The Gazette as paper** (`DayReportDialog`, `EditionBody`, core `Lines.kt`): fixed ink colours, serif body, sectioned bullets, and sentences like "Hilde Pellam went hunting for a foe worth the vow; was driven back by a Hollowbound lich bare-handed." This is the best writing in the game.
4. **One lead, one End Day** (`LeadCard.kt`, `EndDayButton` in `WorkshopScreen.kt`, `TomorrowCard` in `DayCards.kt`): "WORTH DOING FIRST" with a button that goes where the lead is acted on, End Day with a sublabel that says what it costs ("10 energy unused", "A commission is waiting"), and the same lead repeated on the Tomorrow card so a day closes with a next step.
5. **Accessibility groundwork**: merged semantics with spoken deltas ("was 24, now 31"), 48 dp minimum targets including the sheet handle, never colour alone (every effect has a sign), fontScale branches in `StatRow`, `FactRow`, `StockRow` and `WeaponStatCard`, live regions on the day card, and disabled buttons that carry their reason in `contentDescription`.

## 5. Priority issues

**P0-1. The forge reveal is a stat sheet with a fade, not a moment.**
What: `ForgeResultDialog` (`Dialogs.kt`) shows `WeaponStatCard` under the title "Fresh from the forge" with a 600 ms alpha tween; `MilestoneBurst` fires only for epic or better. Shots 07 and 25 at 12:24 are two columns of numbers, two bars, a Buffs list and two buttons.
Why it matters: this is the one reward the loop pays out every two energy; the first-time player's "ooh" is replaced with reading "Renown Unsung (0)". In 25 at 12:24 (a plain Uncommon with "No buffs.") the moment is actively deflating.
Fix: a two-beat reveal in `ForgeResultDialog`: beat one is the slot, the sprite at 2x, the name in its rarity colour and the kind line on an otherwise empty plate (haptic `FORGE_STRIKE` already fires here); a tap or 700 ms brings beat two, the card, with buffs appearing one per 120 ms (instant under reduced motion). Let the rarity colour tint the frame's corner studs (`forgeFrame` takes a colour parameter). Keep Store and "List at N". Hide "Renown" on a fresh blade (it is already filtered out of `StockRow` via `weaponStats(...).filter { it.max != null || it.value > 0 }`; apply the same filter in `WeaponStatCard` cells).

**P0-2. The price decision is made where the demand facts are not.**
What: the reveal's default action is "List at 128" (07) and the blade sheet's `StockEditor` (`ItemDetailSheet.kt`) offers a text field and a "List at" plate; neither says what buyers can pay. The facts exist: `Demand.summary` yields `canAffordCheapest`, and `ShopUi.kt` renders "Can afford the cheapest blade (128 gold): N of 12" on the Shop only after a blade is listed. In run A all three visitors "could afford nothing on the shelf" at the suggested price.
Why it matters: a first-timer followed the game's suggestion and got zero sales and a till card reading "Taken today 0 gold". That is the strongest negative signal day 1 can send, and the player did nothing wrong.
Fix: add one recorded-fact line wherever a price is set: in `ForgeResultDialog` under the suggested price ("4 of 12 heroes in town can pay 128") and in `StockEditor` live under the field as the number changes. Both are counts of saved purses, not predictions, so they respect the GDD's disclosure rule the same way "Who is buying" already does. Expose a `GameEngine.canAfford(state, price): Pair<Int, Int>` next to `shopUi` in `ShopUi.kt`. Also let `Advice.lead` fire PRICES_TOO_HIGH on the same day a blade is listed above every purse, not only after a day of refusals.

**P1-3. Unify the secondary action vocabulary and kill the stock pills.**
What: see section 3 items 1 to 4.
Why it matters: consistency is the heuristic this build scores worst on, and it is the cheapest to fix.
Fix: one `SecondaryActionButton` (`Frames.kt`) for every "not the way forward" action: Store (`Dialogs.kt`), Back (`ShopDayControls.kt`), Close in `HeroDetailSheet.kt` and `ItemDetailSheet.kt`, Unlist and the step buttons (`ItemDetailSheet.kt`), Watch the fight, the blessing choices and Skip to tomorrow (`DayCards.kt`), Abandon run (`App.kt`), Main menu (`SettingsSheet.kt`), the upgrade buy buttons (`RunEndScreen.kt`). Add a `TextAction` composable in `Frames.kt` (gold, labelLarge, 48 dp) for inline actions and use it for Got it, Read the Gazette, Decide later, Forge this, Salvage, Hone, Arm the watch, Skip day. Give the four `Button` sites `shape = MaterialTheme.shapes.small` or replace them with `PrimaryActionButton` (Gazette Close, Alright, both Try again). Pass `icon = {}` to every `SegmentedButton` so no check marks appear. Decide once that Gold is the accent and Ember is for warmth (fire, the outcome chip) and set `Overline(strong)` to Gold.

**P1-4. Chrome takes a quarter of the screen and the pinned End Day has no job on Town and Records.**
What: on 02 the top bar (127 px), the End Day plate (150 px) and the destination bar (185 px) take 462 of the 1,760 px between the status bar and the gesture bar: 26%. The painted strip and the counter plate take another 448 px, so the lead card's frame begins 35% of the way down and its button at 53%. `WorkshopScreen.kt` builds `Scaffold(bottomBar = Column { EndDayButton; DestinationBar })`, so the same plate sits over the Gazette archive, the Journal and the Legacy page (12, 13, 14) where ending the day is never the next action. The plate is 4 dp (`Space.xs`) above the tab bar, so a thumb aiming for "Forge" can end the day, which is irreversible by design. In 25 at 12:24 the reveal dialog's bottom frame fuses with the plate and "8 energy unused" peeks out beneath it.
Why it matters: reading screens lose a third of their height to a button nobody presses there, and the most consequential tap in the game is the closest one to the most frequent taps.
Fix: in `WorkshopScreen.kt` show `EndDayButton` only when `s.dest == Dest.SHOP || s.dest == Dest.FORGE`; on Town and Records let the content run to the bar. Raise the gap to `Space.md` (16 dp) and give the plate `Modifier.padding(horizontal = Space.lg)` so it is visibly narrower than the bar. On the Shop, cut `backdropHeight` from 88 dp to 64 dp when the shelf is empty. In `ForgeResultDialog`, add 24 dp bottom padding to the card and dim the scrim to 70% so gold does not sit on gold.

**P1-5. The Forge's auto-scroll hides the onboarding and the entry points, and the plate lies after the first pick.**
What: `Step` in `ForgePanel.kt` calls `bringIntoView` whenever a step is open, including on first composition. The Family step (header plus two rows of chips) does not fit under the plate with the FORGE tip banner and the Supplies/Journal row above it, so the column scrolls and shot 04 opens on "Mode" with the tip and both buttons out of sight. After tapping Sword (05) the Core step scrolls to the top, Family is gone, and `ForgeSummary` still says "Nothing on the anvil · Quick · balanced" because the title waits for both family and core.
Why it matters: the first-run tip is never seen on the screen it explains; the Supplies door is hidden on the screen where "Out of stock: Obsidian, Starsteel, Moonsteel. Buy more in Supplies." is printed; and a player who just chose a family gets no visible confirmation.
Fix: only request `bringIntoView` when `opened != null` (a step the player tapped), never for `firstUnfinished` on first layout. Make the title read "Sword" when only the family is chosen and "Iron Sword" when both are; include the family in the recipe line. Move Supplies and Journal into a single row under the steps, or into the anvil plate as two `TextAction`s, so they are reachable without scrolling up.

**P1-6. Day-1 stakes are invisible in the card sequence.**
What: run B's Gazette (21) says "Expeditions: 0 won, 5 lost" and lists five heroes "driven back ... bare-handed"; the card sequence that preceded it (quiet day, then Tomorrow) never mentioned a fight. `ShopDayScript.toUi` (`ShopDayUi.kt`) only emits `Beat.Aftermath` cards for recorded aftermath events, which on day 1 with no shop blade are none; `TomorrowCard` shows purse, shelf, storage and the siege date.
Why it matters: the premise, heroes fight and die and your blades decide it, is the game's hook, and on the day it matters most it is behind a text link. The player who skips the Gazette ends day 1 thinking nothing happened.
Fix: in `toUi`, when `field` is non-empty and `aftermath` is empty, emit one `Beat.Aftermath`-style summary beat ("Beyond the door: 5 went out unarmed; 5 were driven back") with `PersonChip`s, drawn by a new `FieldSummaryCard` in `DayCards.kt`. Put the Gazette's tally line (`edition.tally`) as a `Quiet` line on `TomorrowCard`. Keep "Read the Gazette" as the way to the prose.

**P2-7. The shop-day screen says the same thing three times and counts customers two ways.**
What: run A's open card shows "DAY 1 / The shop opens" in the top bar, "The shop opens" on the counter plate, "DAY 1" as the card overline and "The shop is open" as the card title. Run B's Tomorrow card has "DAY 1" in the top bar, "Day 2" on the banner and "Begin day 2" on the button. The visit card's top bar reads "Customer 1 of 1" after the open card said "3 customers came to the counter today" (`Beat.Visit` progress uses `featured.size`).
Why it matters: repetition reads as filler and the two day numbers on one screen are a genuine ambiguity.
Fix: in `ShopDayScreen.kt`, pass `plate = visit?.face?.name` only and leave the plate empty (or showing the shelf count) on non-visit beats; drop `Overline("Day $day")` from `ShopOpenCard`; in `ShopDayTopBar` show "Day 1 · evening" on the Tomorrow card instead of "DAY 1 / The day is done" under a "Day 2" banner. Make the progress string "Customer 1 of 3" using `all.size`, or "First at the counter" when only one is featured.

**P2-8. The Town header is a number dump and the empty champion slots give no reason.**
What: `TownThreat` (`InfoPanels.kt`) stacks "Restless · siege on day 5, in 4 days", "Weak to frost", "Outlook: grave danger · defense 60 vs raid 195", "Forge 100 · militia 5 · sieges held 0", "World: Restless Roads" as five secondary lines (10). Below, three plates say "No hero stands here yet." while twelve heroes are "hale" one section down (11).
Why it matters: the most consequential forecast in the game (defense 60 vs raid 195: the town will fall) is the fourth line of grey text, and the empty champion slots look like a bug.
Fix: three tiers in `TownThreat`: the faction name and "siege on day 5, in 4 days" as the title block; "Grave danger: defense 60 against a raid of 195" as a `StatRow` pair with `EffectKind.FLAW` colouring; "Weak to frost" as an `EffectRow`. Move forge integrity, militia, sieges held and the world name to a "Town" `FactBlock` under the champions. Replace the empty-slot copy with the rule, for example "A champion needs a blade; no hero in Emberfall carries one yet" (confirm the exact rule in the `Power` resolver before wording it).

**P2-9. A microcopy consistency pass.**
What: section 9 lists 46 items; the ones that affect comprehension are "Seats 6", "Unsung (0)", "Restless", "Customer 1 of 1", "Requests · 1 of 2 open", "Tap" on the speed chip, "Forge 100", the drifting coach lines and the three names for the supplies sheet.
Why it matters: a first-timer meets five of these on the first two screens.
Fix: in `ShopPanel.kt` plate "Serves 6 a day · Shelf 0 of 8"; in `ShopDayControls.kt` label the chip "Speed: Tap" or use a speed glyph; in `ThreatLine` "Forge integrity 100"; "Requests · 1 open, 1 slot free"; one name, "Supplies", for the door row, the Forge button and the sheet title; align `Tips.COUNTER` with `ShopOpenCard`'s line (pick "jumps to the evening", since Skip lands on the Tomorrow card, not on tomorrow).

**P2-10. The Gazette archive heading collides with its own Open/Close affordance.**
What: `GazettePanel` (`InfoPanels.kt`) lays `SectionTitle(masthead, weight(1f))` beside `Secondary("Close")` with no spacing; in 12 the serif masthead runs into "Close".
Why it matters: it is the first thing on the Records tab and it looks broken.
Fix: render the masthead as two lines like the dialog does (name at titleLarge, "Day 1" as a dateline), use a chevron `TextAction` with `Space.sm` spacing, and make the whole row the target (it already is).

**P3-11. The loading frame.**
What: `UiState.Loading` draws `CircularProgressIndicator` centred on black (01).
Fix: in `App.kt`, show the title word in Gold over `ForgeScene(heat = 1f, reducedMotion = true)` (both exist in `MainMenu`) or nothing at all; a dark frame reads as a game starting, a spinner reads as an app.

**P3-12. The price editor mixes five component types and the keyboard can cover the actions (judged from source).**
What: `StockEditor` (`ItemDetailSheet.kt`) combines two `OutlinedButton` steppers, an `OutlinedTextField` with a floating "Price" label, a `PrimaryActionButton`, an `OutlinedButton` Unlist and three `TextButton`s. `ItemDetailContent` has `navigationBarsPadding()` but no `imePadding()`, and the sheet is `skipPartiallyExpanded`, so the number keyboard is likely to hide "List at" and the Set price button.
Fix: one stepper row in the forge vocabulary (a `forgeRow` with -10, the number at titleLarge, +10, and a long-press to type), `Modifier.imePadding()` on the column, and the three stock actions as `TextAction`s under a "Also" `SectionHeader`.

## 6. Cognitive load and emotional journey

### Cognitive load checklist (5 of 8 fail)

1. Single focus: **fail**. The Shop (02) offers the lead card, "Who is buying", the shelf, two door rows and a pinned End Day at once; the lead card tries, the gold End Day competes. The shop-day cards pass.
2. Chunking (4 or fewer per group): **fail**. Six family chips, six core chips (04, 05), five secondary lines in the Town header, up to six "Who is buying" rows, a Legacy page of five upgrades with three lines each.
3. Grouping: pass. `SectionTitle` rules and `FramedPanel`s group well; the one slip is Supplies/Journal sitting between the anvil plate and the recipe steps (`ForgePanel.kt`).
4. Visual hierarchy: **fail**. Two gold plates on 02 (Go to the forge, End Day), three on 23 at 12:25 (Open storage, Accept, End Day), two on every Forge shot; the painted strip is the brightest element on every screen and carries no information.
5. One thing at a time: pass. The forge accordion and the day cards are strictly sequential.
6. Minimal choices (4 or fewer at a decision point): **fail**. Six families, six cores, six augments; the reveal (two choices) and the request card (three) pass.
7. Working memory: **fail**. Pricing needs the purse facts from the Shop; the shop-day "Customer 1 of 1" asks the player to reconcile it with "3 customers" from the previous card; the besieger line must be remembered from the plate when reading "Weak to frost" on Town (the augment chips carry the mark, which is the right pattern and should be the model).
8. Progressive disclosure: pass. Accordion steps, Advanced mode hidden behind Quick, sheets for detail, "Fallen and retired" collapsed, Gazette archive collapsed, bulk actions behind "Select blades".

### Emotional journey

- **Day 1 arrival (02):** competent and a little grim. The eye lands on the painted forge, then on "Seats 6 · shelf 0 of 8", then on "Forge your first blade". The End Day plate already says "10 energy unused", which reads as a reproach before anything has been done. Curiosity is carried entirely by the picture.
- **The forge (04 to 06):** the accordion is satisfying; each pick snaps the next step open and the plate fills in. The low point is "Nothing on the anvil" persisting after Sword (05), and "Iron + Ember Resin: Unknown / unknown" (06), which tells a first-timer they are gambling without saying what the stakes are.
- **The reveal (07, 25 at 12:24):** the name in blue, "Rare", "+ Flaming: Burns foes; raiders fear it" land in run A. In run B the same card says "Uncommon", "No buffs.", "Unsung (0)". A 600 ms fade is not a reveal; this is the flattest peak in the game.
- **The first customer (run A 20_day_card_02):** the bust steps up, the shelf lifts the blade, and then "NO SALE / Could afford nothing on the shelf / Could pay up to 90 gold; the cheapest blade is 128 gold." The screen is well built and the news is bad, and the player had no way to see it coming. The LOOKED AT factor list ("not their kind of weapon, elemental, which they like, they carry nothing, beyond their purse") reads like a log.
- **The tally and the till (20_day_card_03, 20_day_card_04):** "2 more came by / 2 could afford nothing on the shelf" then "THE TILL / Taken today 0 gold / 0 sales · 3 left without buying / Purse: 250 gold". Honest, clean, and the day's low point with no pointer forward on the card itself.
- **The Tomorrow card (run B 20_day_card_02):** "Put a blade on the shelf / The shelf is empty; 1 blade in storage." with Purse, shelf, storage and "Next siege: day 5, in 3 days", under a night banner that says "Day 2", with "Begin day 2". This is the best-shaped card of the day: a problem, a reason, a number and a button. The day ends well enough, but only if the player opens the Gazette do they learn that five heroes went out unarmed and lost.
- **Where the peak is:** intended, the reveal and the first sale; observed, the painted strip and the Gazette's prose. Two of the three captured runs had no sale on day 1, and run C's 324 gold was seen only as one line on the Resume prompt ("The shop took 324 gold."), which is a fine line but not a moment. For many players the first real high is a day away, and the game does not say so.

## 7. Persona red flags

### Jordan (confused first-timer)
- "Seats 6 · shelf 0 of 8" on the counter plate (`ShopPanel.kt`): what is a seat, and why is the shelf on the counter?
- The "Tap" box beside "Skip day" (`ShopDayTopBar`, run A 20_day_card_01): a gold-outlined box labelled with a verb; pressing it changes the label to "1x" with no explanation.
- The FORGE tip banner and the Supplies/Journal buttons scrolled out of view on the first visit (04); the step that explains the forge is never seen on the forge.
- "Nothing on the anvil" after choosing Sword (05, `ForgeSummary`).
- "Iron + Ember Resin: Unknown / unknown" (06, `AffinityHint`): state said twice, meaning said never.
- "Renown Unsung (0)" on a blade forged ten seconds ago (07).
- "Customer 1 of 1" under a card that said three customers came (run A 20_day_card_02, `ShopDayUi.kt`).
- The LOOKED AT factor dump (`Lines.considered`): "not their kind of weapon, elemental, which they like, they carry nothing, beyond their purse; short by 38 gold".
- Three plates reading "No hero stands here yet." with twelve "hale" heroes below them (10, 11).
- "Forge 100" with a shield icon on the Forge threat line (`ThreatLine`): integrity is never named.
- "Expeditions: 0 won, 5 lost" in the Gazette (21) is the first time Jordan learns heroes fight at all.
- The same bust for Kazimir Holmfirth (run A 20_day_card_02), Piet Burdock (20_day_card_03) and Quenna Burdock (run B 20_day_card_01): with a shared face the name plate is the only identity; until the owner's portrait set lands, a per-class frame colour on `PortraitTile` would help without touching faces.

### Casey (distracted, one-handed)
- End Day is a full-width 52 dp plate 4 dp above the destination bar (`EndDayButton`, `Space.xs`); a thumb reaching for Forge or Town can end the day with no confirmation and no undo. Highest-risk tap in the game at the most-tapped edge.
- "Tap anywhere to go on" on a card that needs scrolling (run A 20_day_card_02: LOOKED AT is clipped by the coach line and the controls): a resting-thumb tap advances the card mid-read; Back recovers it, but the scroll position is lost because the card is keyed on `at`.
- The settings gear, the speed chip and Skip day are all top-right 48 dp targets (`TopBar`, `ShopDayTopBar`); Supplies and Journal are at the top of the Forge's scrolling column.
- Storage filters are three horizontally scrolling chip rows (`StorageFilters`, judged from source): thumb-scrolling sideways inside a vertically scrolling sheet.
- The price field raises the number keyboard inside a bottom sheet without `imePadding()` (`ItemDetailContent`, judged from source): the List and Set price plates are likely under the keyboard.
- Gold on gold: when the reveal is open the pinned End Day plate stays bright under the dialog (25 at 12:24); a mis-tap outside the dialog dismisses the reveal, and the blade silently goes to storage.

### Alex (impatient expert)
- Every forge ends in a modal that must be answered (Store or List) before the next forge; five blades is five dialogs. A "list at suggested" default in Settings, or a "Forge and list" plate for a draft that already has a price, would halve the taps.
- The 600 ms reveal fade and the Gazette's 700 ms per-round step (`DayReportDialog`) cannot be skipped except by Skip or the reduced-motion setting.
- "Who is buying" is a table of counts (02) with no purse distribution; Alex wants "can pay 128: 0 of 12" at the top of the Shop and in the reveal, and a sorted list of purses on Town.
- The Journal is sorted by key string (`journalItems`: `sortedBy { it.key }`), so "aug:", "core:" and "sig:" entries interleave by prefix; there is no filter, and the signature ladder prints four "not yet known" rungs after one forge (13).
- "Fit to stand at the walls" and "Restless" are never defined; the siege forecast ("defense 60 vs raid 195") is only on Town, not on the Shop plate that says "Siege in 3 days".
- Skip day lands on the Tomorrow card, which still needs "Begin day N"; the minimum day is End Day, Skip day, Begin day: three taps, which is fine, but the speed chip hidden under "Tap" means most experts will never find 2x.
- Decline on a request is one tap with no confirmation and no way back; Accept and Decline are adjacent (23 at 12:25).

## 8. Screen-by-screen notes

**Title and loading (01):** a Material spinner on black for the load. The window background is already `forge_night`, so the frame is dark, but the spinner is the only thing on it. Replace with the title word and the ember glow, or with nothing.

**Main menu (24, 28, 28b, 29; `App.kt` `MainMenu`):** "Tiny Blacksmith" in gold serif over a `FramedPanel` holding the ivy-and-anvil scene, "Era 2 · Day 2", a gold "Continue run" plate and an outlined "Scenarios (debug)" button; a gear top-right with a third of the screen empty above the title. Sound structure and the one screen where a single gold plate stands alone; the outlined button is the M3 default colour again, and the debug button must not ship. The source draws "Abandon run" here for a run in progress; the build does not, so the only way to abandon a run in the captured build is unverified.

**Shop, day 1 (02, 03):** top bar, 88 dp painted strip, counter plate "Seats 6 · shelf 0 of 8" with the siege line, the lead card (two gold plates on screen with End Day), "Who is buying" as a three-row census, the shelf section with the tip banner ("Got it" in orange) and "Nothing on display.", then two `DoorRow`s ("Storage · 0 / Open ›", "Supplies and tools / Open ›"). The order is right (lead first) but the eye goes picture, plate, End Day, lead. The empty shelf is said twice (plate and section).

**Shop with a blade (09):** the shelf band appears under the plate with the sword and its "128" tag; the lead changes to "Forge for today's buyers / 12 heroes carry no blade." and the shelf count on the plate reads 1 of 8. Nothing on this screen warns that 128 is above every visitor's purse; "Who is buying" is below the fold.

**Shop, day 2 (22 and 23 at 12:25):** the lead is "Put a blade on the shelf / Open storage" (good), then "Requests · 1 of 2 open" with a request card that has three buttons of two styles (Accept gold, Decline and Forge this bronze), then "Who is buying". Three gold plates are visible at once on 23 at 12:25. End Day's sublabel "A commission is waiting" is the best contextual line in the build.

**Forge (04, 05, 06, 08, 24 at 12:24):** the anvil plate (sprite slot, title, recipe line, gold plate, reason line) over an accordion of steps with a right-aligned value in gold. The auto-scroll opens the screen on "Mode" with the tip and the Supplies/Journal row above the fold (04); choosing a core scrolls Family away (05); when complete (06, 24 at 12:24) only Augment, Risk and "Journal says" are visible. "Out of stock: Obsidian, Starsteel, Moonsteel. Buy more in Supplies." points at a button that is off-screen. The "Journal says" row with the "?" disc and "Unknown / unknown" is the weakest line on the screen.

**Forge result (07, 25 at 12:24):** `WeaponStatCard` in a `Dialog`: title with a diamond rule, 88 dp slot with the rarity pip, name in rarity colour, "Rare ◆ · Fire sword", a two-by-two cell grid (Power, Renown, Element, Value), Quality and Condition bars with a band word, Buffs with sign plates, two footer lines, Store and "List at N". Readable, dense, static. In 25 at 12:24 the dialog's gold frame fuses with the End Day plate beneath.

**Town (10, 11):** a `FramedPanel` with the besieger sprite, name, "Restless · siege on day 5, in 4 days", and five grey lines; two more factions as rows; "Champions" with three empty plates; "Adventurers (12 alive)" with portrait, name, "Battlemage 1 · hale · unarmed". The adventurer rows are good; the header buries "grave danger" in line four; the champion empties have no cause.

**Records: News (12):** check-marked segmented tabs, then the archive with the masthead colliding with "Close"; the lede "Era 1 begins in Emberfall under a restless roads." is a grammar bug in the core template; the Forge section repeats the Journal lines already shown on the Journal tab.

**Records: Journal (13):** "Experiment Journal" with a two-line explanation, then three rows with the half-moon disc, "Seems promising / observed", and a signature ladder of four rungs, three "not yet known". After one forge this reads as noise; the ladder should appear once two rungs are known.

**Records: Legacy (14):** "Era 1 / 0 legacy points banked", then "Permanent upgrades" with dot meters and three lines each. Clear, and the "Next era: 11 starting energy instead of 10" previews are exactly the right disclosure. The End Day plate under it has no job here.

**Shop day: open and quiet cards (run A and run B 20_day_card_01, run C in the current 25):** top bar "DAY 1 / The shop opens" with the "Tap" chip and "Skip day", the counter scene with the plate repeating the beat name, the shelf band, then a card that repeats "DAY 1" and says "The shop is open" with the coaching line inside the card. Run C's open card (the current 25) is the best version: three blades on the shelf with their tags (80, 112, 132) and "3 blades on the shelf. 5 customers came to the counter today." give the player something to watch for. Run B's quiet card lists five names in the sentence and then the same five as face chips; the chips are clipped by the controls.

**Resume prompt (40, 41, 43, 50, 51, 52; `ResumePrompt` in `DayCards.kt`):** a lone `FramedPanel` centred on the dark ground: "Day 1 is done and saved.", "The shop took 324 gold.", a gold "Resume the day" and an outlined "Skip to tomorrow". Clear and calm, and the right two choices. Two notes: the panel floats with no top bar, no day art and no title, so a player returning after a day away has no sense of where they are until they tap; and "Skip to tomorrow" here, "Skip day" on the day screen and "Skip" in the Gazette are three labels for one idea. The 324-gold line also shows that a good day's result can be seen first as a line on a resume card rather than at the till.

**Shop day: customer (run A 20_day_card_02):** the bust behind the counter, "Kazimir Holmfirth / Warden. Carries no weapon." on the plate, the shelf lifting the sword; the card's "NO SALE" chip, serif outcome, reason with numbers, LOOKED AT list cut off by the coach line. The scene is the best composition in the game; the card beneath it needs the factor list rewritten as one sentence.

**Shop day: tally and till (20_day_card_03, 20_day_card_04):** "2 more came by / 2 could afford nothing on the shelf" with two face chips; then "THE TILL" as receipt rows. Both clean; the till has a lot of empty plate beneath it and no forward pointer.

**Shop day: Tomorrow (run B 20_day_card_02):** night banner with a "Day 2" plate, the lead in orange overline and gold serif, three receipt rows, the siege line, "Read the Gazette" in orange, Back and "Begin day 2". The best card of the sequence; fix the orange and the two day numbers.

**Gazette dialog (21):** parchment, serif masthead, double rule, the tally line, SHOP and HEROES sections with bullets. Beautiful until the orange pill. The content area scrolls behind the pill with no fade, so the last line is cut mid-glyph.

**Storage sheet (judged from source, `StorageSheet.kt`):** heading "Storage · N", a context line, up to three horizontally scrolling chip rows, "Select blades", then `StockRow`s with a bronze "List at N" on each; a bulk bar with "Salvage N" and "Arm the watch N" in select mode, and `AlertDialog` confirmations. Functionally rich; the chip rows and the select mode are a lot of control for a sheet.

**Supplies sheet (judged from source, `SuppliesSheet.kt`):** "Supplier" heading with "N gold in the purse. Buy one at a time.", Cores, Augments, Catalysts with a bronze "Buy" per row, then "Workshop tools" with "Buy · N g". Clear; the three names for this sheet (Supplies, Supplier, Supplies and tools) should become one.

**Blade sheet (judged from source, `ItemDetailSheet.kt`):** the stat card, optional At the counter / Now fact blocks, "Price" with the mixed-component editor, Recipe, Story, History, an outlined Close. The order (card, price, lore) is right; the editor is the problem (P3-12).

**Hero sheet (judged from source, `HeroDetailSheet.kt`):** a framed header with a 72 dp portrait, name in gold serif, lineage and deeds, the standing want, fact blocks, then "With your shop" and "Recent events" as dated lines, an outlined Close. Good; "Standing: Not a regular yet" and "Element taste: No favourite element" are fine fact lines.

**Settings sheet (judged from source, `SettingsSheet.kt`):** a `FramedPanel` titled "Settings" inside a `ModalBottomSheet` with two `Switch` rows, the speed segmented row, "Main menu" and the version. A plate inside a sheet is a frame inside a frame; the content is right.

**Run end (judged from source, `RunEndScreen.kt`):** "The forge has fallen" headline, cause, a framed reward ledger with Claim, remembered blades and lineage, upgrades as framed rows with "N pts" outlined buttons, and a pinned "Begin era N". One primary at a time is handled correctly; the outlined buy buttons are the M3 default again.

**Load failure (judged from source, `Failures.kt`):** title, what happened, what is safe, Try again (pill), Start over (outlined) with explanatory lines, details last. The copy is the most careful in the project; the buttons need the forge shape.

**Font scale 1.3 (40 to 43 show only the Resume prompt, and the scale cannot be confirmed from them; the rest is judged from source):** on the frames, the prompt's serif title, body line and two buttons fit with room to spare, which is the easy case. In source, `StatRow`, `FactRow` and `StockRow` stack above 1.3; `WeaponStatCard` goes to one cell per line above 1.0; the Shop, Forge and day screens shrink their backdrops above 1.15; the destination bar keeps a fixed 64 dp with labels that never shrink (`maxLines = 1`, `softWrap = false`), which fits at 1.3 but should be verified at 1.5 and 2.0. The `ShopDayTopBar` progress label will ellipsise early beside "Skip day". The Shop, Forge and day cards at 1.3 still need a real capture.

**Small screen 360x640 dp (50, 51, 52 show only the Resume prompt; the rest is judged from source):** at 720x1280 the prompt sits comfortably with 16 dp gutters. In source, `short` is true below 700 dp, so the Shop backdrop drops to 56 dp and the Forge's to 72 dp; the chrome (top bar, End Day, bar) is about 180 dp of 640, leaving roughly 230 dp for the Forge steps under the plate, enough for one open step but not for the tip and the buttons above it, which makes P1-5 worse here. The Shop, Forge and day card at this size still need a real capture.

## 9. Microcopy a first-time player would not understand or would find odd

Each item names the composable or file that owns it; core strings are marked (core).

1. "Seats 6 · shelf 0 of 8" (`ShopPanel.kt` counter plate): "seats" is the daily customer capacity; say "Serves 6 a day".
2. "Quick · balanced" and "Quick · Ember Resin · balanced" (`ForgePanel.kt` `ForgeSummary` recipe line): mode and risk words with no labels and no family.
3. "Nothing on the anvil" after a family is chosen (`ForgeSummary` title).
4. "Choose" with a caret for an unfilled step (`Step`): fine alone, but after auto-scroll it is the first word on the Forge.
5. "Iron + Ember Resin: Unknown" over "unknown" (`AffinityHint`): the state said twice.
6. "Seems promising / observed", "Hides something more", "Recipe / Catalyst / Temper / Finish: not yet known" (`Journal.hint` (core), `SignatureLadder` in `InfoPanels.kt`): after a single forge.
7. "Renown Unsung (0)" (`ItemDetailSheet.kt` `renownWord`, shown in `WeaponStatCard`): a zero on a brand-new blade reads as a flaw.
8. "Rare ◆ · Fire sword", "Uncommon ◈" (`Labels.rarity`): tiny font-dependent glyphs beside a lower-case kind.
9. "Fine", "Decent" as quality bands and "Sound", "Worn", "Battered" as condition bands (`Labels.quality`, `conditionWord`): band words that read as adjectives for the whole blade.
10. "Sword of Iron and Ember Resin. Suggested price 128 gold. Set your own in the Shop." (`ForgeResultDialog`): the price is set on the blade sheet, which also opens from Storage.
11. "Who is buying" over "Heroes in town 12 / Carry no blade 12 / Carry a worn blade 0" (`ShopPanel.kt` `DemandLine`, `ShopUi.kt`): nobody is buying; it is a census. "Buyers in town" or "Demand".
12. "Carry no blade", "Carry a worn blade" (`ShopUi.kt`): verbs with no subject.
13. "Requests · 1 of 2 open" (`ShopPanel.kt`): reads as one of two requests; means one open, two slots.
14. "Decent Dagger (quality 35+)" (`Labels.request` via `Commissions.describe` (core)): a band word as an adjective plus the number it stands for.
15. "110 gold · due day 5, in 3 days" (`ShopUi.kt` `dueWords`): fine; but "due" on a request and "Next siege: day 5, in 3 days" on the Tomorrow card use different shapes for the same date.
16. "Storage · 0 / Open ›", "Supplies and tools / Open ›" (`DoorRow`): "Open ›" is settings-app grammar.
17. "Supplies", "Supplier", "Supplies and tools" (`ForgePanel.kt`, `SuppliesSheet.kt`, `ShopPanel.kt`): three names for one sheet.
18. "Got it" (`TipBanner`, `WorkshopScreen.kt`): product-UI phrase in orange.
19. "Forge 100 · Siege in 4 days · ..." (`ThreatLine`): integrity never named; the shield icon suggests defence, not the building.
20. "Restless · siege on day 5, in 4 days" and "World: Restless Roads" on the same card (`TownThreat`): the same word for a pressure band and a world name.
21. "Weak to frost" (`weakness()`): on Town the clause has no sign; on the Forge chips it has a + mark; make them the same.
22. "Outlook: grave danger · defense 60 vs raid 195" (`TownThreat`): the gravest fact on the screen in secondary text with an abbreviation.
23. "Forge 100 · militia 5 · sieges held 0" (`TownThreat`): three unlabelled scales in one line.
24. "The three strongest heroes fit to stand at the walls." and "No hero stands here yet." (`TownThreat`): "fit" is undefined and the empty state has no cause.
25. "Battlemage 1 · hale · unarmed" (`HeroRow`): a level without the word, an archaic health word.
26. "hale / bruised / wounded / grave" (`Labels.health`): "grave" collides with the Grave element.
27. "Adventurers (12 alive)" (`TownPanel`): a count in brackets with "alive" on day 1 is a spoiler that lands as a threat; fine later, odd first.
28. "✓ News", "✓ Journal", "✓ Legacy" (`SegmentRow`): check marks on tabs.
29. "EMBERFALL GAZETTE, DAY 1" with "Close" flush against it (`GazettePanel`), and the masthead's dash against middle dots everywhere else (`Gazette.masthead` (core)).
30. "Era 1 begins in Emberfall under a restless roads." (core Gazette lede): grammar; the world name is being used as an adjective.
31. "Safe, steady work, few surprises" style risk lines (`Labels.risk`): the dash-separated label is split with `substringBefore` for the chip and shown whole nowhere.
32. "Tap" as the speed chip label beside "Skip day" (`ShopDayTopBar`): a verb on a toggle; "1x" and "2x" are its other faces.
33. "Tap anywhere to go on. Skip day jumps to tomorrow." (`ShopOpenCard`) vs "Tap anywhere to continue · Skip day jumps to the evening" (`Tips.COUNTER`): two coaching lines, two destinations.
34. "DAY 1 / The shop opens" (top bar), "The shop opens" (plate), "DAY 1" (overline), "The shop is open" (title): one fact, four times (`ShopDayScreen.kt`, `CounterScene`, `ShopOpenCard`).
35. "Customer 1 of 1" after "3 customers came to the counter today" (`ShopDayUi.kt` `Beat.Visit` progress).
36. "NO SALE", "Could afford nothing on the shelf", "Could pay up to 90 gold; the cheapest blade is 128 gold." (`VisitCard`, `Lines.reason`, `Lines.decision`): the outcome three times; keep the chip and the numbers line.
37. "LOOKED AT / Flaming Iron Sword, 128 gold / not their kind of weapon, elemental, which they like, they carry nothing, beyond their purse; short by 38 gold" (`Lines.considered`): a comma-spliced factor list; write it as one clause per line or one sentence.
38. "THE TILL / Taken today 0 gold / Purse: 250 gold" (`ShopCloseCard`): "taken" is till slang and "Purse" is the top bar's "gold" under another name.
39. "Hilde Pellam, Jessamy Ingram, Quenna Burdock, Ulla Pargeter and Arvo Kettleby looked in and found the shelves bare." followed by the same five as chips (`QuietDayCard`, `Lines.quiet`): the list twice.
40. "The forge says" / "Alright" around "Not enough energy (need 2, have 0, overwork left 0)." (`ErrorDialog`, `GameViewModel.describe`): a folksy frame around a developer string.
41. "Arm the watch 3", "Salvage 3" (`StorageList` bulk bar): count after the verb; "Dearest" as a sort (`StorageSort`).
42. "Rewards are claimed when the forge falls; they survive every era." and "Knowledge survives the forge's fall." (`legacyItems`, `journalItems`): "the forge falls" is never explained on day 1.
43. "Decide later" (`BlessingDialog`, `BlessingChoices`): fine in meaning, product-UI in voice.
44. "Begin day 2" under a "Day 2" banner under a top bar reading "DAY 1" (`TomorrowCard`, `StageBanner`, `ShopDayTopBar`).
45. "Skip to tomorrow" (`ResumePrompt`), "Skip day" (`ShopDayTopBar`) and "Skip" (`DayReportDialog`): three labels for one idea; "Skip day" lands on the Tomorrow card, not on tomorrow.
46. "Scenarios (debug)" on the captured main menu: a debug entry in the player's first menu; gate it behind the debuggable flag the way `startSeededRun` already is.

## 10. Five provocative questions

1. Why does the one decision the player actually makes, the price, get a text field in a bottom sheet, while the forge, where nothing has consequences until a price is set, gets the pinned plate, the painted strip and the big gold button?
2. If the Gazette is the best-written surface in the game, why is it a modal behind a text link, while the card sequence that repeats each fact three times is the mandatory path?
3. What would the Shop look like if "Who is buying" were the painted strip: twelve faces at the counter with their purses, instead of a table of counts under a picture of an anvil nobody can touch?
4. Should End Day be a plate at all on Town and Records, and should the one irreversible tap in the game sit 4 dp from the tabs most thumbs reach for?
5. Would a player who has never heard of Material 3 notice that the Gazette's Close button, the blessing dialog and the check-marked News/Journal/Legacy tabs come from a different game than the anvil plate? They would; what is the plan for the seams before the artist redraws the sprites?
