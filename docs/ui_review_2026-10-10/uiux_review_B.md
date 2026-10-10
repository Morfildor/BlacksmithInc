# Tiny Blacksmith: player workflow review (B)

Reviewer brief: diagnose "the game feels quirky to play" from flow, feedback, pacing and structure. Art is out of scope. LOCKED rules (no movable character, no combat control, no equipping, 10 energy, every valid forge yields a weapon, predictable sieges, no timers or idle production, animations never gate outcomes) are respected in every proposal below.

Sources: the screenshots in `scratchpad/uiux_shots/`, every file under `app/src/main/java/com/example/blacksmithproject/ui/` (incl. `theme/`, `detail/`, `shopday/`), `GameViewModel.kt`, `ShopDayPosition.kt`, `core/.../shopday/Lines.kt` and `Advice.kt`, GDD lines 110 to 140 and 354 to 367.

Evidence hygiene. I opened every file and judged it by content, not by name. Usable: 01 (loading), 02 to 09 (Shop day 1, Forge steps, forge result, Shop with a blade), 10 to 14 (Town, Records), 20_day_card_02 to 04 (customer, tally, till; first capture run), 25 (a "The shop opens" card with three blades priced 80, 112 and 132 and five customers; its name says forge result, its content is a day card), 24, 28, 28b and 29 (all four show the main menu of a saved run, "Era 2 · Day 2", with "Continue run" and a "Scenarios (debug)" button; none shows a Forge or a blade sheet), 32 (the loading spinner, not the menu), and 40 to 43 and 50 to 52 (all seven show the Resume prompt "Day 1 is done and saved. The shop took 324 gold.", the 50s at 720x1280; none shows the Shop, the Forge or a day card at font scale 1.3). Unusable: 09b, 20_day_card_01, 22, 23, 26, 27, 30, 30b and 31 are the Android launcher. So Storage, Supplies, the blade sheet, the hero sheet, Settings, the day-2 Shop, the Tomorrow card, the Aftermath and Blessing cards, and every font-scale or small-screen view except the Resume prompt are judged from code only, and I say so where it matters. For the End Day cards I rely on `ui/shopday` plus 20_02 to 04 and 25.

Two side findings from the broken run. First, the launcher captures are consistent with the code's own rule that system Back exits the app from the Shop destination, from the first shop-day card and from the Resume prompt (B.5); consistent, not proof. Second, every relaunch after 12:40 landed on the Resume prompt, which means the run had an unwatched day each time; the prompt is a real screen players will meet after any kill or a phone restart and it is reviewed in C.

Capture time stamps also show separate runs (the besieger is "Ashclaw Raiders" at 12:12, "Hollowbound" at 12:24, and the menu says "Era 2 · Day 2" at 12:39), which is why card sets do not match across files. One more caveat: the "Scenarios (debug)" button in 24, 28, 28b and 29 does not exist anywhere in `app/src` (main or debug source set), so the installed APK is not the same commit as the source reviewed. Everything else on screen (Shop, Forge, result dialog, Town, Records, day cards, Resume prompt) matched the source line for line, so I treat the difference as a debug-only addition.

---

## A. Task walkthroughs

Counting rules: a tap is any press (chips, buttons, rows, Back); a switch is a bottom-bar destination change caused by the player or by code (`vm.selectDest`, `forgeFor`, `forgeFamily`, `selectRecords`); a scroll is a vertical gesture needed to bring the control into view on a 1080x1920 phone at font scale 1.0; modals are dialogs and `ModalBottomSheet`s opened; "carried in the head" is what the player must remember across a switch or a modal boundary because the screen they land on does not show it.

| # | Task | Taps | Switches | Scrolls | Modals | Carried in the head | Trace (composables) |
|---|------|------|----------|---------|--------|---------------------|---------------------|
| 1a | Forge a blade, list it at a price of my choosing (first time) | 12 to 13 | 2 | 2 | 3 | The suggested price (dialog closes before the shelf is seen); that listing lives in Storage, not on the Shop; that the blade sheet keeps itself open after "List at" | `LeadCard` "Go to the forge" (switch 1) > `ForgePanel` `Step` Family chip > Core chip > Augment chip (steps auto-advance, `pick`) > `ForgeSummary` "Forge weapon" > `ForgeResultDialog` offers only "Store" or "List at 128" > "Store" > `DestinationBar` Shop (switch 2) > scroll to `DoorRow` "Storage" > `StorageSheet` > `StockRow` tap > `ItemDetailSheet` stacked over Storage > `StockEditor` type a number or +/-10 (keyboard opens) > "List at N" (sheet stays open: `WorkshopScreen.DetailSheet.onStock` only dispatches) > "Close" > dismiss Storage. Alternative: "List at 128" in the dialog, Shop, tap the `ShelfBand` slot, +/-10, "Set price", Close = 10 taps, 1 switch, 2 modals, and the player still re-prices in a modal reached from another tab. |
| 1b | Forge the familiar recipe again and list at the suggested price | 3 | 1 | 0 | 1 | Nothing | Forge tab > "Forge weapon" (draft persists in `SavedStateHandle`, every `Step` collapsed because `opened` resolves to `NO_STEP`) > "List at N". This is the one task that meets GDD 12 ("minimal taps when using familiar combinations"). |
| 2 | Change the price of a listed blade | 4 plus N | 0 | 0 (slot) or 1 (row) | 1 | Nothing, but the only price control in the game is a text field inside a modal | `ShelfBand` `WeaponSlot` tap (or scroll to `StockRow`) > `ItemDetailSheet` > `StockEditor` +/-10 or type > "Set price" > sheet stays open > "Close" |
| 3 | Answer a request and forge for it | 5 to 6 | 2 | 0 to 1 | 1 | The quality threshold (not a forge input); which stored blade End Day will pick (`Commissions.pick` is invisible); whether listing the blade risks a browser buying it before handover | `RequestCard` shows "Accept", "Decline" and "Forge this" at once while `offered` > "Accept" > "Forge this" (`forgeFor`, switch 1, sets family and an augment, pins "For X: ..." in `ForgeSummary`) > Core chip > "Forge weapon" > `ForgeResultDialog` has no `commissionId` awareness and still leads with "List at" > "Store" > Shop (switch 2) to read `Labels.readiness` "Ready: X will be handed over at End Day". |
| 4 | Buy a material that ran out mid-forge | 5 plus N buys | 0 | 1 to 2 | 1 | Which material was missing (the note says it; the sheet does not highlight it) | Disabled `FilterChip` in `MaterialChips` is not a door; `ForgeSummary` note "You are out of Iron. Pick another or buy more in Supplies." has no button; scroll up to the "Supplies" `SecondaryActionButton` (top of the `ForgePanel` scroll, above `Wanted`) > `SuppliesSheet` > find the row (scroll) > "Buy" once per unit ("Buy one at a time", `SuppliesList`) > dismiss > "Forge weapon". Also reachable from the Shop's second `DoorRow`. |
| 5 | End Day and understand why a customer left without buying | 1 plus one tap per card (typically 6 to 12) | 1 (code returns to Shop on `acknowledge`) | 0 to 1 per card | 0 (plus the Gazette dialog if opened) | Nothing for a featured visitor: `VisitCard` gives the typed reason and the numbers ("Could pay up to 90 gold; the cheapest blade is 128 gold"). For tallied visitors only `Lines.reason` ("2 could afford nothing on the shelf"). | `EndDayButton` > `ShopDayScreen`: `ShopOpenCard` > `VisitCard` x featured > `TallyCard` > `ShopCloseCard` > `AftermathCard` x N > `TomorrowCard` "Begin day N" > Shop, where "Yesterday at the counter" repeats the same lines and `LeadCard` only turns to "Lower a price" after two price refusals (`Advice.lead`). |
| 6 | Find a hero's taste and purse before forging for them | 4 | 2 | 1 to 2 | 1 | Two facts (element taste, purse) plus a third the sheet does not give: which families the hero's class favours (that line lives on `StockRow.favoured`, under a blade, not on the hero) | `DemandLine` rows in "Who is buying" are not tappable; `WantRow` is, but only for a standing want. Otherwise: Town (switch 1) > scroll `HeroRow` > `HeroDetailSheet` "Element taste" / "Purse" > Close > Forge (switch 2). Nothing on the Forge shows either fact except `Wanted` lines. |
| 7 | Salvage three stored blades | 8 | 0 | 1 | 2 | The energy cost (3 x `salvageEnergy`) only appears in the confirmation text | Shop > scroll > "Storage" `DoorRow` > `StorageSheet` "Select blades" > 3 rows > "Salvage 3" > `AlertDialog` "Salvage" > "Done" or dismiss. The single-blade path (`StockEditor` "Salvage (...)") asks nothing and closes the sheet by side effect (the weapon leaves the save, `detail == null`, `closeSheet`). |

Reading of the table: the repeat forge (1b) is excellent and shows the team knows how to make a path short. Every other task crosses at least one of the three seams (tab, dialog, sheet-on-sheet), and four of seven ask the player to carry a number across that seam.

---

## B. Navigation model critique

The model on paper: four destinations (`Dest.SHOP, FORGE, TOWN, RECORDS`), a pinned `EndDayButton` above the bar on all four, a three-stat `TopBar` with a gear, modal sheets (`StorageSheet`, `SuppliesSheet`, `SettingsSheet`, `HeroDetailSheet`, `ItemDetailSheet`), dialogs (`ForgeResultDialog`, `BlessingDialog`, `ErrorDialog`, `DayReportDialog`, `ReplayOverlay`), and a separate full-screen `UiState.ShopDay`.

Where it breaks:

1. **The loop's three verbs live on three surfaces that never see each other.** Forge on the Forge tab; the result in a `Dialog` over the Forge; listing and pricing in a sheet (over the Shop) or a sheet stacked on a sheet (Storage then Item). The shelf is never visible while forging, the forge is never visible while pricing. `LeadCard`'s gold button "Go to the forge" is a tab jump dressed as an action; `forgeFor` and `forgeFamily` jump tabs from inside a card; `vm.selectRecords(JOURNAL)` jumps from the Forge's "Journal" button. The player is teleported four different ways by buttons that do not look like navigation.

2. **Every tab switch resets scroll and open step.** `WorkshopScreen` switches `when (s.dest)` with no `SaveableStateHolder` or `movableContentOf` (none anywhere in `app/`). `ShopPanel`'s `LazyColumn` state and `ForgePanel`'s `remember { ScrollState(0) }` leave composition and come back at the top; `ForgePanel.opened` is `rememberSaveable` but positional state inside a disposed branch is not restored on re-entry (only on process or configuration restore). Player-visible symptom: "Go to the forge", Back, and you are at the top of the Shop again with the lead card, not where you were.

3. **Two gold primaries on most screens.** `PrimaryActionButton` is used for "Go to the forge" / "Forge this" (Shop), "Forge weapon" (Forge), "Choose a blessing" (Legacy page), and for the pinned "End Day" at the same time (screenshots 02, 06, 09, 14). The gold plate was designed as "the one way forward" (`Frames.kt` comment); the screen shows two. On Town and Records, "End Day" sits under hero lists and upgrade tracks where it has no context (10, 12 to 14).

4. **The Forge's working area is under a third of the screen.** Measured from screenshot 06: top bar 45 dp, backdrop plus threat plate 115 dp, `ForgeSummary` plate 151 dp, steps viewport about 196 dp, End Day 55 dp, nav 64 dp. The choice chips scroll inside 28 percent of the height, under a decorative backdrop that repeats the Shop's.

5. **Back behaviour.** `WorkshopScreen` enables `BackHandler` only when `dest != SHOP`; on the Shop, Back finishes the activity with no confirmation. `UiState.ShopDay.backIsConsumed` is false on the first card and on the Resume prompt, so Back on "The shop opens" and on "Day 1 is done and saved." (screenshots 40 to 43, 50 to 52) also exits the app. Inside the day, Back steps one card back (never closes the day); on the Blessing card it means "Decide later". A tap outside the Gazette calls `vm.back()` (closes it). Four different meanings for one key. The launcher screenshots at 12:17 to 12:19 and 12:41 to 12:45 are consistent with this, not proof of it.

6. **Sheet stacking and the sheet that will not close.** `ItemDetailSheet` opens over `StorageSheet` (two `ModalBottomSheet`s, two drag handles, two "Close" buttons). After "List at", "Set price", "Unlist", "Hone" the item sheet stays open and re-renders from the save (`DetailSheet.onStock` dispatches, never `closeSheet`); after "Salvage" it closes itself by side effect. The player cannot predict which actions dismiss.

7. **Same thing, many doors.** Supplies: a `DoorRow` on the Shop and a button on the Forge. "Forge this": `RequestCard`, `WantRow`, and `Wanted` on the Forge. Blessing: non-dismissable `BlessingDialog` on the workshop, `Beat.Blessing` card, "Choose a blessing" on the Legacy page, and `LeadKind.CHOOSE_BLESSING` on the Shop lead. The Gazette: `DayReportDialog` over the day, `GazettePanel` under Records > News, and "Read the Gazette" links on the Tomorrow card, the Aftermath card, the Fallen card and the Shop's "Yesterday" block. The same threat sentence from `Lines.threat` is printed on the Shop plate, the Forge plate, the Town header and beside every elemental chip and stock row.

8. **"Decide later" teleports.** `dismissBlessingOffer` sets `dest = Dest.SHOP`; whatever tab the dialog appeared on, the player lands on the Shop.

9. **The main menu is only reachable through Settings** (`SettingsSheet.onMainMenu`), and the app opens on the menu after every cold start (`menuOpen = true`), which is one more "Continue run" tap before the day begins. Screenshots 24, 28, 28b and 29 show that menu for a saved run: "Era 2 · Day 2", "Continue run", and a "Scenarios (debug)" button in the debug build, with no "Abandon run" because `App.kt` passes `onAbandon` only in the `Playing` state, not while a day is unwatched. On a saved run with an unwatched day the sequence is spinner, menu, "Continue run", Resume prompt, "Resume the day", and only then the day: three screens before the game. A run saved mid-planning is spinner, menu, "Continue run", Shop.

Is the pinned End Day on every screen right? Half right. Predictable placement is good and the contextual note (`EndDayButton.note`) is the best piece of chrome in the game. What is wrong is its weight: it is a second gold primary on the two screens that already have one, and the only primary on two screens where ending the day is not what you are doing. Recommendation in G: keep it pinned, make it the only gold plate on the Shop, and render it as the bronze outline (with the same note) on Forge, Town and Records.

---

## C. The End Day sequence

Pacing. `BeatLength` at 1x: Open 1.2 s; a visit is Arrive 1.0 + Browse 0.9 + Decide 1.5 (+ Transact 1.6 if sold) = 3.4 s unsold, 5.0 s sold; Tally 2.0; Close 2.0; Aftermath 2.0 each; Blessing, Tomorrow and Fallen wait. The default speed is `ShopDaySpeed.TAP`, so nothing advances by itself: a day with six featured visits and three aftermath cards is about twelve taps before "Begin day N". Screenshot 25 is a typical early day: three blades on the band, "5 customers came to the counter today", so five visits are split between featured cards and the tally and the player taps through open, visits, tally, till, aftermath and tomorrow. Note that `Beat.Visit.millis` sums four sub-beats but `VisitCard` renders the whole visit on its first frame ("whole on its first frame", `ShopDayScreen` doc): at 1x the player stares at a finished card for 3.4 to 5 s. The timing is for motion that does not exist yet.

Control set. On one screen: tap anywhere (`detectTapGestures` on the outer Column), a wide "Next" / "Continue" / "Begin day N" button, a "Back" outline button, "Skip day", and a speed chip whose resting label is "Tap" (`ShopDayTopBar`). Five controls for a sequence with one direction. The speed chip reads as an instruction next to "Skip day" (screenshot 20_01). `SettingsSheet.SpeedRow` exists but neither caller passes `onShopDaySpeed`, so the chip is the only speed control and the setting is dead code. Tap-anywhere coexists with tappable `PersonChip`, `BladeChip`, `WeaponSlot` and the bust (`CounterScene`): a miss advances, a hit opens a sheet that pauses the timer. "Back" exists only to re-read a card; because the cards are whole on first frame and the Gazette has everything, it mostly serves to make the sequence feel like a slideshow.

The Resume prompt (`ResumePrompt`, screenshots 40 to 43 and 50 to 52, the only screen captured at 720x1280 and it fits). It appears on any cold start onto an unwatched day: "Day 1 is done and saved. The shop took 324 gold." with "Resume the day" (gold) and "Skip to tomorrow" (outline). Three observations. It is the only place the day's take is said before the day is watched, so it spoils the till card it leads to. "Skip to tomorrow" lands on the Tomorrow, Blessing or Fallen card (`skipDay` to `position.ending`), not on tomorrow's Shop, so the label over-promises by one card. And it is a third entry into the sequence (after End Day and the main menu's "Continue run") with its own pair of buttons, on a screen that is otherwise empty; the prompt could be the first card of the day with a "Skip" in its top bar, which is what the sequence already has.

Coaching. The first-run line is said twice in two wordings on consecutive cards: `ShopOpenCard` prints "Tap anywhere to go on. Skip day jumps to tomorrow." unconditionally (it never goes away, every day), and `Tips.COUNTER` prints "Tap anywhere to continue · Skip day jumps to the evening" under the first customer (screenshots 20_01, 20_02). "Tomorrow" and "the evening" describe the same landing.

Sale versus refusal. Both get the same gold `CardTitle` from `Lines.reason` ("Could afford nothing on the shelf" / "Found a blade that suits"). A sale adds a small primary-coloured "SOLD" chip, a `ReceiptRows` block, a `SegmentTick` haptic, and the shelf slot dims to 40 percent with a "sold" tag. A refusal adds an outlined "NO SALE" chip and the "Looked at" factor list. Typographically they are the same weight; there is no purse count-up, no coin motion, no change in the top strip (the shop day has no gold readout at all until the till card). The refusal, with its longer explanatory text and factor list, takes more screen than the sale.

The Tomorrow card hands back: the lead (`Advice.lead`, same function as the Shop), Purse, On the shelf, In storage, next siege in text, a Gazette link. Then "Begin day N" lands on the Shop, whose `LeadCard` prints the identical lead seconds later and whose "Yesterday at the counter" block repeats the till and the refusal lines. The card does not say energy, open requests, or what sold versus what remains, so the player reads the Shop anyway. The hand-off is duplicated, not completed.

Does it make a satisfying loop? Not yet. The information is right (reasons with numbers, the shelf band with lifted and dimmed slots is the best idea in the sequence), but the presentation is a text report dealt one card at a time, with equal weight for every card and a flat till line ("Taken today 0 gold · 0 sales · 3 left without buying", 20_04) with no comparison to yesterday and no progress toward anything. Comparisons, one mechanism each:

- Reigns: one card, one gesture, and the consequence is shown as the four stat icons moving the instant the swipe lands. Here the consequence is a paragraph and the stat (gold) does not move on screen.
- Slay the Spire: after a fight, one reward screen with "Proceed"; no Back, no speed, the log is folded. Here five controls and a "Back".
- Dicey Dungeons: the enemy turn plays out on the same board you fought on; you read the outcome from the board, not from narration. The `ShelfBand` already is that board; the card under it narrates what the band could show.
- Potion Craft: the customer stands at your counter and reacts; the sale happens where you set the price. Here the counter scene exists (bust, plate) but the reaction is a heading in a card.
- Moonlighter: the price reaction is a face icon over the item the moment a customer reads the tag, and you fix the price in the same room. Here the reaction arrives next morning as "Lower a price" after two refusals, and the fix is a text field in a sheet.
- Cultist Simulator: a warning more than a model. It shows outcomes inline in the verb slot with one paragraph, and is famous for making players read to find out what happened. The current day sequence is closer to Cultist's reading load than to Reigns's glance.

---

## D. Feedback and feel

What each primary actually does on tap:

- "Forge weapon" (`ForgeSummary`): the button greys while `busy`; when the save returns, `ForgeResultDialog` appears with a 600 ms alpha fade of the whole stat card (`animateFloatAsState`, `tween(600)`) and `Moment.FORGE_STRIKE` (`ContextClick`) fires when the dialog composes, not on the press. Epic or signature: `MilestoneBurst` over the sprite and `LongPress` haptic. Nothing on the Forge itself moves: no hammer, no spark, the anvil plate keeps showing the draft, the top-bar energy drops silently from 10 to 8 (screenshot 06 to 08). The GDD's "short sprite reveal then detailed item card" is collapsed into "item card fades in".
- "List at N" (dialog): dialog closes; the Forge is unchanged; the only trace is the `Secondary` line "1 in storage, waiting..." turning to nothing, and on the Shop the shelf band now has a slot. The player does not see the blade reach the shelf.
- "End Day": `Moment.END_DAY` (`Confirm`) on press, then a hard cut to `ShopDayScreen` after the save. No transition.
- "Accept" / "Decline" / "Buy" / "Set price" / "Salvage": the row re-renders from the new save. No haptic, no motion, no toast.
- Errors: `Moment.REJECTED` plus an `AlertDialog` titled "The forge says" with an "Alright" button, used for every error including "Not enough gold" from the supplier and "That commission is no longer open".
- Shop day: bust slides in 220 ms over 40 dp (`CounterScene`), looked-at slots lift 4 dp in 180 ms, the sold slot dims (`WeaponSlot`), `SegmentTick` on a sale. This is the right vocabulary; it is just small and the card under it does the talking.

Where feedback is missing: the strike; the blade travelling from anvil to shelf; the purse changing (no number ever counts); accepting a request; buying a material (the chip count on the Forge updates after the sheet closes, out of sight); the siege countdown (text in four places, no clock or bar). Where it is late: the forge haptic (on dialog, not on press); the price reaction (next morning). Where it is modal when it should be inline: the forge result (a dialog that must be dismissed to see the forge again); shelf-full and out-of-stock, which are both knowable before the tap (`ForgeSummary` already mirrors `MissingMaterial`; `ForgeResultDialog` does not mirror `ShelfFull`, so "List at" can raise "The forge says"). Where it is redundant: the threat sentence (four surfaces), the lead (card and Shop), the coaching line (two cards), the shelf shown twice on the Shop (`ShelfBand` at the top and `StockRow`s under "On the shelf").

Sound: there is none, by decision (`SettingsSheet` comment, plan 10.3). The GDD asks for haptics and audio toggles. For a game whose whole feel is "strike, ring, coin", silence is a larger share of "quirky" than any single screen, and it does not wait on art.

---

## E. Copy and labels

Jargon on first contact: "Seats 6 · shelf 0 of 8" (the first line under the scene on day 1, before "seat" has been explained; `ShopPanel` plate); "Frost bites the Ashclaw Raiders; fire glances off them." on day 1; "WORTH DOING FIRST"; "Beyond the door"; "The town's thanks"; "Arm the watch"; "Honed"; "Dormant"; "Catalyst" and "Technique" with no gloss until chosen; "Standing: Not a regular yet".

Inconsistent terms (each list is one concept):

- Shop / market / counter / shelf / storage / storeroom: the destination is "Shop", its icon is `icon_nav_market`, the tip is `Tips.MARKET`, the dialog says "Set your own in the Shop", `Lines.lead` says "the storeroom", `StorageSheet` says "storage", the Forge says "wait in storage until you list them in the Shop", the shop-day says "at the counter".
- Requests / commissions: `RequestCard` and the Shop heading say "Requests"; `GameViewModel.describe` says "That commission is no longer open"; `VisitCard`'s chip says "COMMISSION"; the till row says "Commissions"; `EndDayButton` says "A commission is waiting"; `Lines.reason` says "collected a commission".
- Blade / weapon: "Forge weapon" button, "Forge your first blade" lead, "Flaming Iron Sword" sheet, "List weapons here", "blades remembered", `StockRow` "Open ${name}".
- Records / News / Gazette / Journal / Legacy: the tab is "Records", its icon is `icon_nav_journal`, the first segment is "News" but every link says "Read the Gazette", and the second segment "Journal" is the experiment journal while the Forge button "Journal" jumps there.
- Heroes / adventurers / customers / buyers / visitors / people: "12 heroes in Emberfall", "Adventurers (12 alive)", "Customer 1 of 1", "Who is buying", "Forge for today's buyers", "3 customers came to the counter", "Nobody came to the shop".

Numbers where the GDD asks for descriptive words (12: "descriptive probabilities", "readable numbers"; `Labels` doc: "Descriptive, never numeric"): "Outlook: grave danger · defense 60 vs raid 195" (`TownThreat`, screenshot 10); "Forge 100 · militia 5 · sieges held 0"; "Fame 0 · 0 victories · 0 kills" (`HeroDetail.deeds`); "Quality 62/100 Fine" (the bar already says Fine); "Fine frost Spear (quality 50+)" (`Labels.request`); "Not enough energy (need 4, have 2, overwork left 2)."; "Could pay up to 90 gold; the cheapest blade is 128 gold" (this one is fine: it is the exact fact the player needs to act).

Sentences that read oddly:

- "Iron + Ember Resin: Unknown / unknown" (screenshot 06, `AffinityHint` prints the hint and the state word under it when both are "unknown").
- The `Lines.considered` factor list: "not their kind of weapon, elemental, which they like, they carry nothing, beyond their purse; short by 38 gold" (screenshot 20_02) is a comma salad of unordered clauses.
- "Seats 6 · shelf 0 of 8" and "On the shelf · 0 of 8" on the same screen (the second is "shelf" meaning slots).
- The tip under "On the shelf": "List weapons here at a price you like." There is no list control there; listing is in Storage.
- `Labels.risk` joins its two halves with a long dash ("Safe", dash, "steady work, few surprises") and `Gazette.masthead` does the same ("EMBERFALL GAZETTE", dash, "DAY 1", screenshot 12); every other label in the game uses the middle dot. Pick one, and the middle dot is the one already everywhere.
- "Nothing on the anvil" over a sword sprite at 35 percent alpha (04): the picture says sword, the words say nothing.
- "The forge says" as the title of a dialog about gold or commissions.
- "Tomorrow starts 2 energy short" is good; "10 energy unused" as the End Day note on day 1 reads as a reproach before the player has forged anything.

---

## F. Diagnosis: why it feels quirky (ranked)

Needs no art:

1. **The loop is cut into three surfaces that cannot see each other.** Evidence: `WorkshopScreen` `when (s.dest)`, `ForgeResultDialog` as a `Dialog`, `StorageSheet` plus `ItemDetailSheet`; table rows 1a and 2. Symptom: forging, seeing the result, putting it on the shelf and pricing it is a tab, a dialog and two sheets; the blade is never seen arriving on the shelf. Fix: the result renders inline in the anvil plate (replace `ForgeResultDialog` with an `AnimatedContent` state of `ForgeSummary`), the `ShelfBand` is drawn on the Forge under the plate as well as on the Shop, and "List at [stepper]" moves the blade visibly into the band.

2. **Every tab switch resets position and open step.** Evidence: no `SaveableStateHolder` in `app/`; `ForgePanel` `remember { ScrollState(0) }`; `ShopPanel` default `LazyColumn` state. Symptom: "Go to the forge", Back, top of Shop; Forge, Supplies, back, top of Forge. Fix: wrap the four panels in a `rememberSaveableStateHolder().SaveableStateProvider(dest)` and hoist scroll states into the ViewModel's `Local` if needed.

3. **Two gold primaries on one screen, and End Day pinned with full weight where it has no context.** Evidence: screenshots 02, 06, 09, 14; `PrimaryActionButton` used for both. Symptom: the eye bounces between "Forge weapon" and "End Day"; on Records "End Day" sits under upgrade tracks. Fix: one gold plate per screen (the lead's or Forge's); End Day as bronze outline with its note on Forge, Town, Records, gold only on the Shop.

4. **The game repeats itself.** Evidence: `Lines.threat` on four surfaces; `Advice.lead` on the Tomorrow card and `LeadCard`; "Yesterday at the counter" after the player just watched yesterday; the shelf twice on the Shop; "Forge this" in three places; Supplies two doors; Blessing four entry points; coaching twice. Symptom: nothing feels authoritative because everything is said everywhere; screens read as long. Fix: one home per fact (threat on Town header plus the chip marks; lead on the Shop only, the Tomorrow card becomes the morning header; delete the Shop's stock rows and keep the band plus Storage; one "Forge this" per request).

5. **Pricing is a keyboard inside a modal, and the result dialog offers no price at all.** Evidence: `StockEditor` `OutlinedTextField` with `KeyboardType.Number`; `ForgeResultDialog` "Store" / "List at suggested". Symptom: "manual pricing" (LOCKED) is the game's one economic verb and it is the hardest to reach. Fix: a +/- stepper with the number (no keyboard by default, tap the number to type) inline in the result plate and on each `WeaponSlot` of the band (tap slot: a small popover with the stepper and "Set"); the sheet keeps the field for people who want to type.

6. **The shop day has five controls for a one-way sequence, plus two coaching lines, plus tap-anywhere competing with tappable chips.** Evidence: `ShopDayControls`, `ShopDayTopBar`, `ShopOpenCard`, `Tips.COUNTER`; dead `SettingsSheet.SpeedRow`. Symptom: the player reads the chrome instead of the day; "Tap" reads as an instruction. Fix: tap anywhere plus one wide "Next", "Skip day" in the top bar, an "Auto" toggle (on/off) in place of the "Tap/1x/2x" chip with the multiplier wired into Settings, drop "Back" (the Gazette is the re-read), coach once (`Tips.COUNTER`) and remove the unconditional line in `ShopOpenCard`.

7. **A sale and a refusal weigh the same; the purse never moves on screen.** Evidence: `VisitCard` both use `CardTitle`; no gold readout in the shop day; `BeatLength` timings for motion that does not exist. Symptom: no reward beat; the day feels like reading a ledger. Fix: on a sale, a gold readout in `ShopDayTopBar` counts up, the "SOLD" chip becomes a banner across the card, the receipt rows slide in; refusals keep the muted chip and gain a one-line actionable ("38 gold short of the cheapest blade") instead of the factor list, which moves to the hero sheet.

8. **Back is a different key on every surface and exits the app from three places without asking.** Evidence: `WorkshopScreen` `BackHandler(enabled = s.dest != SHOP)`; `UiState.ShopDay.backIsConsumed` (false on the first card and on the Resume prompt); `dismissBlessingOffer` sets `dest = SHOP`. Symptom: an accidental Back during a run leaves the game; the launcher captures at 12:17 and 12:41 to 12:45 are consistent with this. Fix: Back on the Shop opens the main menu (`menuOpen = true`), Back on the first day card and on the Resume prompt is consumed (or opens Skip), "Decide later" stays on the current tab.

9. **Preventable errors are modal.** Evidence: `ForgeResultDialog` "List at" enabled on `isInStorage` only, so a full shelf raises `GameError.ShelfFull` as "The forge says"; `ErrorDialog` for supplier gold. Symptom: "Alright" dialogs for things the screen could have greyed and explained. Fix: mirror `shelfSlots` in the dialog (disable with the note, as `ForgeSummary` already does for materials); errors that remain become a snackbar-style line in the plate, with the dialog reserved for save failures.

10. **Vocabulary drift and numbers where words were promised.** Evidence: section E. Symptom: the player is never sure whether "Requests" and "commissions", "News" and "the Gazette", "heroes" and "adventurers" are the same thing; the Town header reads like a debug overlay. Fix: a one-page glossary (Shop, shelf, storage, request, blade, Gazette, hero) applied to `Lines`, `Labels`, `GameViewModel.describe`, `Tips`, `destName`; replace "defense 60 vs raid 195" with the outlook word and a bar.

11. **The request flow shows three answers at once and forgets the request at the result.** Evidence: `RequestCard` FlowRow with Accept, Decline, Forge this while `offered`; `ForgeResultDialog` ignores `draft.commissionId`. Symptom: players forge for a request and then are told to list the blade for sale. Fix: Accept or Decline first, "Forge this" appears once accepted; the result plate, when `commissionId` is set, leads with "Set aside for X (ready at End Day)" and shows `Labels.readiness`.

12. **Cold start and first minute.** Evidence: spinner (01), main menu every launch (`menuOpen = true`), "Seats 6 · shelf 0 of 8" and a siege sentence as the first words (02), tip banners that need "Got it". Symptom: the game opens on jargon and chrome before the first strike. Fix: on a saved run, open straight into the workshop (menu on Back); day 1 plate says "Your shelf is empty" and nothing else; the first tip becomes the result plate's own copy.

Waits on art (keep the structure above regardless):

13. The strike itself: hammer, sparks, the blade sliding from anvil to band (the inline result in F.1 gives it a place to happen).
14. Customer reactions at the counter: the bust already slides in; a two-frame reaction (looks at tag, nods or shrugs) would carry what the card now narrates.
15. Coin to till and shelf slot art on sale.

---

## G. Proposed target workflow

Ideal day loop (first days, phone, portrait):

1. Open the app: straight into the Shop of the saved run (menu is one Back away).
2. The Shop header is the morning: "Day 2 · 8 energy · took 128 gold yesterday" and the one lead with its reason. This replaces the Tomorrow card's content; the card itself becomes a short "Day 2" hand-off with "Begin" only.
3. The shelf band sits under the header with every slot priced; a slot tap opens a small inline stepper (-10, number, +10, Set, Unlist), long-press or a "Details" link opens the sheet.
4. Requests sit under the band: Accept or Decline; once accepted, one "Forge this" and the readiness line.
5. "Who is buying" rows expand inline to show the few names with taste and purse; no sheet needed to plan.
6. Storage is an expandable section on the Shop (collapsed count, "Show"), with the same slot tap for pricing; bulk select and the full sheet remain for long storerooms.
7. Forge tab: the anvil plate (preview, recipe words, cost) and the three steps; "Forge weapon" is the only gold plate here. An out-of-stock chip offers "Buy x1 (12 g)" inline; the Supplies sheet is for bulk.
8. Strike: the plate animates in place to the result (name, rarity word, buffs, flaws, suggested price), the band under the plate shows the shelf, and the plate offers "List at [stepper]" (gold), "Store" (outline), "Forge again" (text). When a request is pinned: "Set aside for X" (gold) replaces "List at".
9. Forge again: 2 to 3 taps.
10. End Day (gold on the Shop; outline elsewhere) with the same contextual note.
11. Shop day: tap or Next; "Skip day" and "Auto" in the top bar; a gold readout that counts on sales; one coaching line once.
12. Cards: open, visits (sale banner or muted refusal with one actionable line), tally, till (with "yesterday 0" beside today), beyond the door, blessing (the only entry point; "Decide later" leaves a chip on the Shop header), "Day N: Begin".
13. Back to the Shop header, which already carries the lead; nothing is repeated.

Navigation model: keep the four destinations and the pinned End Day; keep the top strip. Modal is for reading (hero sheet, blade sheet, Gazette, replay) and for bulk (Storage sheet, Supplies sheet). Inline is for every action that changes the save from where the player is standing: pricing, listing, accepting, buying one unit, the forge result. Dialogs only for save failure and "Abandon run". Back: on a non-Shop tab, to the Shop; on the Shop, the main menu; in the shop day, consumed on every card (first card: opens Skip).

Tap budget per task after the fix (current in brackets): forge and list at a chosen price, first time 7 [12 to 13]; repeat recipe 2 to 3 [3]; change a price 3 [4 plus N]; answer a request and forge for it 4 [5 to 6], with nothing carried in the head; buy a material that ran out 2 [5 plus N]; End Day and read a refusal, 1 plus one tap per card [same], with the refusal line now actionable; find taste and purse 1 [4]; salvage three 6 [8].

What to do first (two weeks, no art): F.2 state holder, F.3 one primary per screen, F.5 inline stepper in the result and the band, F.8 Back, F.6 shop-day controls and coaching, F.4 delete the duplicates. Those six remove most of "quirky" without touching a sprite; F.1 (inline result) is the one structural change and should be designed together with the art pass so the strike has a place to land.
