# Tiny Blacksmith: UI/UX review and redesign plan (2026-10-10)

Reviewed: build 0.7.0 (`main` at `ea01669`), on the Pixel_10_Pro emulator at 1080x1920, density 420, font scale 1.0, and every file under `app/src/main/java/com/example/blacksmithproject/ui/`, `GameViewModel.kt`, the sentence templates in `core/.../shopday/Lines.kt` and `Advice.kt`, the GDD sections 1.2, 3.2, 3.3, 11, 12 and 14, the external review of 2026-10-09, the Android experience design (`docs/major_update_evidence/03_android_experience.md`, section B) and the art packs under `Pixel art assets/`. Owner's brief: a deep review of the visuals, menus and workflow; the game "feels quirky to play"; pixel art arrives later; review and plan only, nothing implemented.

Where this sits: it is the start of the milestone the external review called E ("full visual redesign") and the checklist's open line "Final UI design (owner will redesign)". The 0.7.0 navigation model is kept: four destinations, the lead card, sheets as reading surfaces, the shop day as its own screen. This document says what that model got right, what the redesign changes, and in what order, so that the artist's pack can land as a file drop.

Two independent assessments were made and then reconciled: review A (design-director critique with heuristic scores) and review B (workflow and game-feel diagnosis with tap counts). A third, a component inventory and accessibility audit, was started and will be added to the evidence folder if it completes. The two are in `docs/ui_review_2026-10-10/` as appendices; where they agree it is said so, where they differ the choice is stated.

---

## 1. One page

**Verdict.** The interface is built with unusual care (semantics everywhere, 48 dp targets, a reason on every disabled button, deterministic cards from records) and its bespoke layer (bronze plates, the gold action plate, the counter scene, the paper Gazette) is credible. It feels quirky for structural reasons, not for lack of polish: the loop's three verbs (forge, price, sell) live on three surfaces that never see each other; most screens show two gold primaries; the chrome takes a quarter of the screen and the pinned End Day sits over pages where it has no job; stock Material components show through at the seams (orange pill, check-marked tabs, four kinds of secondary button, alert dialogs for rewards and errors); the peak moments (the reveal, the first sale) are drawn with the same plate as the Settings sheet; the game repeats every fact on three surfaces; and a first-time player who follows the game's own suggestion ("List at 128") ends day 1 with zero sales and no warning. Review A scores the build 24 of 40 on Nielsen's heuristics ("fair"); the deficit is concentrated in consistency, recognition and minimalism, not in fundamentals.

**The single biggest opportunity** is to give the one decision the player makes (the price) and the one reward the loop pays (the reveal) their own place and register: show how many heroes can pay a price wherever a price is set, and make the forge result land inline on the anvil with the shelf in view, so a blade is seen going from anvil to shelf to a customer's hand.

**Why it feels quirky, ranked** (full evidence in section 4; "no art" means it can be done before the artist's pack lands):

| # | Cause | Needs art? |
|---|---|---|
| 1 | The loop is cut across a tab, a dialog and two stacked sheets; the shelf is never visible while forging and the forge never while pricing | no |
| 2 | Two gold primaries on most screens; End Day pinned at full weight on Town and Records, 4 dp from the tabs | no |
| 3 | Pricing is a keyboard inside a modal and the reveal offers only "Store" or "List at the suggested price", with no purse fact in sight | no |
| 4 | Every tab switch resets scroll and the open forge step; the Forge auto-scrolls its onboarding and its Supplies door out of view | no |
| 5 | Stock Material 3 at the seams: four secondary-button styles, check marks on tabs, the orange pill, system alerts for a blessing and for errors, a spinner as the first frame | no |
| 6 | The game repeats itself: the threat line on four surfaces, the lead on two, the shelf twice on the Shop, the coaching line twice, four doors to the blessing | no |
| 7 | A sale and a refusal weigh the same; the purse never moves on screen; the day is dealt as equal text cards with five controls for a one-way sequence | no |
| 8 | Chrome budget: top bar, End Day and the bar take 26 % of the screen; with the art strip and the plate, the Shop's lead button starts half way down | no |
| 9 | Vocabulary drift (Shop/market/counter/storeroom, requests/commissions, heroes/adventurers/customers, News/Gazette) and numbers where the GDD asks for words ("defense 60 vs raid 195") | no |
| 10 | Back means four different things and exits the app from the Shop, the first day card and the Resume prompt without asking | no |
| 11 | Two pixel scales on one screen: the painted strip is an HD concept image shrunk, the icons and sprites are 16 px art at whole-number scale; the main menu draws a third style (the tile scene) | art |
| 12 | Nothing strikes, rings or clinks: no strike animation, no blade travelling to the shelf, no coin, no sound | art and audio |

**Plan at a glance** (details in section 6; effort is one developer, rough):

| Phase | What | Art needed | Effort | Gate |
|---|---|---|---|---|
| 0 | Quick wins: consistency, copy, chrome weight, the price fact, the forge scroll, the loading frame | none | 1 week | seen on the emulator at font 1.0, 1.3 and 2.0 and at 360 dp; device tests and `smoke.sh` pass |
| 1 | Workflow: inline forge result with the shelf in view, inline price stepper, saved state per destination, one primary per screen, the Back model, the shop-day control set and the sale register, one home per fact | none (designed so art can land in it) | 3 weeks | the same, plus the fresh-player protocol (plan 9.7) on the forge-price-sell loop |
| 2 | The forge kit: a component system with a nine-slice renderer, tokens, one vocabulary replacing the Material seams, specified against the V3 UI pack's IDs so the final art is a drop-in | the V3 pack as stand-in | 2 weeks | every screen uses only kit components; screenshot set re-taken |
| 3 | Art integration when the pack arrives: backdrops at fixed heights, counter foreground, strike and reaction frames, coin, nav and status icons, title and menu art | yes | 2 weeks after delivery | one pixel scale per screen; the brief's UI addendum fully consumed |
| 4 | Validation: large text, 360 dp, TalkBack, a scripted screenshot run for every screen, playtests | none | 1 week plus players | checklist 12 lines closed with evidence |

**Decisions needed from the owner** (section 7): the four open UI decisions in DECISIONS (numbers on cards, the gold-button hierarchy, the card timings, the onboarding wording) each get a recommendation here, plus six new ones (Back on the Shop, the Shop's duplicate stock rows, the Resume prompt's spoiler, the debug menu entry, the main-menu art style, sound).

---

## 2. Evidence basis

The project's verification words are used throughout: "seen on the emulator (file)" or "judged from source (file)".

| Screen | Basis |
|---|---|
| Loading frame, Shop day 1 (top and scrolled), Forge (empty, core step, ready, after listing), forge result, Shop with a blade, Town (top and scrolled), Records (News, Journal, Legacy) | seen on the emulator, files 01 to 14 in `docs/ui_review_2026-10-10/` |
| Shop-day cards: the open card, a customer refusal ("Could afford nothing on the shelf"), the tally, the till | seen on the emulator at 12:15 to 12:16 (run A, day 1, one blade at 128, three customers, zero sales); the files were lost when the capture folder was rewritten, the observations are recorded in section 5 and in review A |
| The open card with three blades at 80, 112 and 132 and five customers | seen on the emulator, file 25 (from a different build on the same emulator, see below) |
| The Resume prompt at 1080x1920 and at 720x1280 | seen on the emulator (files 40 and 50); the same different build |
| Main menu | seen on the emulator but from a different build ("Scenarios (debug)", no Abandon run); the 0.7.0 menu is judged from `App.kt` `MainMenu` |
| Tomorrow, Aftermath, Blessing and Fallen cards, the Gazette dialog, Storage, Supplies, the blade sheet and its price editor, the hero sheet, Settings, run end, load failure, the Shop, Forge and day cards at font scale 1.3 and 2.0 and at 360x640 dp | judged from source only |

Two facts about the capture. First, the emulator was being driven by another process during the session: the app was uninstalled at 12:17 (both the app and its test package, which is what a connected test run does), a different build was installed later, and the uiautomator dump tool collided with a second driver from 12:28 on. That is why the capture is partial, and it may be the owner's or another agent's test run. Second, the capture script written for this review (`scratchpad/uiux_capture.sh`, not committed) drives a fresh run through forge, list, reprice, sheets, End Day, menu, large text and small screen; it is a candidate for the missing T2.9 tooling once the dump helper is made robust against a busy emulator.

Guardrails kept: no balance, mechanics or sprite-quality verdicts (the external review owns gameplay depth; the artist owns the sprites); nothing proposed below moves a LOCKED rule; the four-destination model is kept because neither review found evidence that it fails, only that the paths between the destinations are too long.

---

## 3. Design health

Review A's heuristic scoring (0 to 4 each), endorsed here without change; review B's independent tap counts and the source reading in sections 4 and 5 support the same three weak points.

| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of system status | 3 | Day, gold, energy and the End Day note are always present; forge integrity is an unnamed "Forge 100"; a working save is only greyed buttons; the first frame is a spinner |
| 2 | Match with the real world | 3 | Shop, forge, till, shelf, purse and the Gazette prose are strong; "Seats 6", "Unsung (0)", "hale", "Restless", "Dearest" and the rarity glyphs are not |
| 3 | User control and freedom | 3 | Back steps a card, Skip day is one tap, Store or List is a real choice; Decline on a request is irreversible with no confirmation; End Day sits 4 dp above the tabs with no guard |
| 4 | Consistency and standards | 1 | Four secondary-button vocabularies plus stock pills; gold and ember both used as "primary"; three names for the Supplies sheet; two coaching lines that disagree |
| 5 | Error prevention | 2 | The forge button and out-of-stock chips explain themselves; the reveal's default lists a blade above every purse; "List at" can raise a shelf-full error dialog |
| 6 | Recognition rather than recall | 2 | The anvil plate carries the recipe and the chips carry the besieger mark (good); the purse facts live on the Shop, not where the price is set; the plate says "Nothing on the anvil" after a family is chosen |
| 7 | Flexibility and efficiency | 3 | Quick and Advanced, "Forge this", one-tap "List at", bulk Storage, "Use this recipe", Skip day; the reveal fade and the Gazette's stepping cannot be skipped except by the reduced-motion setting |
| 8 | Aesthetic and minimalist design | 2 | The Town header is a six-fact dump; the day screen says "Day 1" twice and "The shop opens" twice; "Looked at" prints a comma-spliced factor list |
| 9 | Error recovery | 3 | The load-failure and save-failure screens are exemplary; the in-play error dialog wraps a developer string in "The forge says" and "Alright" |
| 10 | Help and documentation | 2 | Three one-shot banners and a coach line, none re-readable; the Journal's knowledge states are a glyph and a one-word label; "the forge falls" is never explained |
| | **Total** | **24 / 40** | fair: a solid pre-release build whose deficit is consistency, recognition, minimalism and help |

Cognitive load (review A's checklist, 8 items): 5 fail (single focus, chunking, visual hierarchy, minimal choices, working memory), 3 pass (grouping, one thing at a time, progressive disclosure). The failures are concentrated on the Shop and the Forge; the shop-day cards pass single focus.

Tap counts (review B, current build, font 1.0): forge a blade and list it at a chosen price for the first time, 12 to 13 taps across 2 tab switches and 3 modals; the familiar recipe again at the suggested price, 3 taps; change a listed price, 4 taps plus the number inside a modal; answer a request and forge for it, 5 to 6 taps with the quality threshold and the handover rule carried in the head; buy a material that ran out mid-forge, 5 taps plus one per unit; find a hero's taste and purse before forging, 4 taps across two tab switches; salvage three stored blades, 8 taps and two modals. The repeat forge is the one path that meets GDD 12's "minimal taps for familiar combinations"; every other path crosses a tab, a dialog or a sheet stacked on a sheet.

---

## 4. Why it feels quirky: the twelve causes with evidence

Each cause names the evidence, the player-visible symptom and the fix; the fix is scheduled in section 6.

**1. The loop is cut into three surfaces that cannot see each other.** Evidence: `WorkshopScreen.kt` switches `when (s.dest)`; the result is `ForgeResultDialog` in `Dialogs.kt`, a `Dialog` over the Forge offering only "Store" and "List at N"; listing and pricing happen in `ItemDetailSheet` over the Shop or stacked over `StorageSheet`; the lead's gold "Go to the forge" is a tab jump dressed as an action (`LeadCard.kt`), and `forgeFor`, `forgeFamily` and `selectRecords` in `GameViewModel.kt` jump tabs from inside cards. Symptom: the blade is never seen arriving on the shelf; the player is teleported four ways by buttons that do not look like navigation (reviews A and B agree; seen on the emulator, files 02, 06, 07, 09). Fix: the result lands inline in the anvil plate with the shelf band drawn under it on the Forge; "List at [stepper]" moves the blade visibly into the band; "Go to the forge" stays but is styled as a door, not a plate (section 6, phase 1).

**2. Two gold primaries on most screens, and End Day at full weight where it has no job.** Evidence: `PrimaryActionButton` is used for "Go to the forge", "Forge weapon", "Accept", "Choose a blessing" and for the pinned `EndDayButton` at the same time (files 02, 06, 09, 14; `WorkshopScreen.kt` builds `Scaffold(bottomBar = Column { EndDayButton; DestinationBar })`). The plate is `Space.xs` (4 dp) above the bar. Symptom: the eye bounces between two plates; on Town and Records "End Day" sits under hero lists and upgrade tracks; the most consequential and irreversible tap in the game is the closest to the most frequent ones. Fix: one gold plate per screen; End Day gold on the Shop only and a bronze outline with the same note elsewhere, 16 dp above the bar (owner decision 7, section 7).

**3. The price decision is made where the demand facts are not, and the reveal offers no price.** Evidence: the reveal (file 07) says "Suggested price 128 gold. Set your own in the Shop." and lists at 128 by default; `StockEditor` in `ItemDetailSheet.kt` is an `OutlinedTextField` with a floating label inside a modal sheet with no `imePadding`; the facts exist in `Demand.summary` and are rendered as "Can afford the cheapest blade (128 gold): N of 12" on the Shop only after a blade is listed (`ShopUi.kt`). Symptom (seen on the emulator, run A): the first customer, a warden, "Could pay up to 90 gold; the cheapest blade is 128 gold"; the till card read "Taken today 0 gold · 0 sales · 3 left without buying"; the player did exactly what the game suggested. Fix: one recorded-fact line wherever a price is set ("4 of 12 heroes in town can pay 128"), a stepper instead of a keyboard, and the `PRICES_TOO_HIGH` lead firing the day a blade is listed above every purse, not after a day of refusals. These are counts of saved purses, not predictions, so they respect the GDD's disclosure rule exactly as "Who is buying" already does.

**4. Every tab switch resets position, and the Forge scrolls its own onboarding away.** Evidence: no `SaveableStateHolder` or `movableContentOf` anywhere in `app/`; `ForgePanel.kt` keeps `remember { ScrollState(0) }` and `ShopPanel.kt` a default `LazyColumn` state; `Step` in `ForgePanel.kt` calls `bringIntoView` whenever a step is open, including the first layout, so the Forge opens on "Mode" with the tip banner and the Supplies and Journal buttons above the fold (file 04), and choosing a core scrolls Family away while the plate still says "Nothing on the anvil" (file 05). Symptom: "Go to the forge", Back, top of the Shop again; the first-run tip is never seen on the screen it explains; "Out of stock: Obsidian, Starsteel, Moonsteel. Buy more in Supplies." points at a button that is off screen. Fix: a `SaveableStateProvider` per destination, `bringIntoView` only for a step the player tapped, the plate title "Sword" after one pick, Supplies and Journal reachable without scrolling up.

**5. Stock Material 3 at the seams.** Evidence (review A and the source reading agree; seen on the emulator, files 12 to 14 and the Gazette dialog): the Gazette's `Button` with no shape override renders as the M3 full pill in ember orange on parchment, the only orange pill in the game, also used by `ErrorDialog` ("Alright") and both "Try again" buttons in `Failures.kt`; `SegmentedButton` with its default icon gives "✓ News" on tabs and the same check on Mode, Risk and the speed row; raw `OutlinedButton`s with M3 default colours (Store, Back, Close, Unlist, Watch the fight, the blessing choices, Skip to tomorrow, Abandon run, Main menu, the upgrade buy buttons) sit beside the bronze `SecondaryActionButton` and beside ember `TextButton`s (Got it, Read the Gazette, Decide later, Salvage, Hone, Arm the watch, Skip day); the town's blessing is an `AlertDialog`; the first frame is a `CircularProgressIndicator`. Symptom: "an app wearing a game's clothes" at exactly the moments that should feel like the game (the newspaper, a reward, an error). Fix: one vocabulary (phase 2), with the cheapest substitutions in phase 0.

**6. The game repeats itself.** Evidence: `Lines.threat` is printed on the Shop plate, the Forge plate, the Town header and beside every elemental chip and stock row; `Advice.lead` on the Tomorrow card and on the Shop's lead card seconds later; "Yesterday at the counter" on the Shop after the player just watched yesterday; the shelf twice on the Shop (`ShelfBand` at the top and `StockRow`s under "On the shelf"); "Forge this" in `RequestCard`, `WantRow` and the Forge's `Wanted`; the blessing reachable from a non-dismissable dialog, a card, the Legacy page and the lead; the coaching line in `ShopOpenCard` (every day, unconditionally) and in `Tips.COUNTER` with different wording. Symptom: nothing feels authoritative; screens read long. Fix: one home per fact (section 6, phase 1, task 1.8).

**7. A sale and a refusal weigh the same; the purse never moves.** Evidence: `VisitCard.kt` uses the same gold `CardTitle` for "Found a blade that suits" and "Could afford nothing on the shelf"; a sale adds a small chip, receipt rows and a `SegmentTick` haptic; the shop day has no gold readout until the till card; `BeatLength` sums Arrive, Browse, Decide and Transact for motion that does not exist, while the card is "whole on its first frame" (`ShopDayScreen.kt` doc), so at 1x the player stares at a finished card for 3.4 to 5 seconds; five controls (tap anywhere, Next, Back, Skip day, the "Tap" speed chip) for a one-way sequence; `SettingsSheet.SpeedRow` exists but no caller passes it. Symptom: the day reads as a ledger dealt one card at a time; "Tap" beside "Skip day" reads as an instruction. Fix: a sale register (gold readout counting up, a banner, receipt rows sliding in) and a reduced control set; the beat lengths revisited when motion exists (owner decision 8).

**8. The chrome budget.** Evidence (review A measured file 02; the dp figures are from the layout code): top bar 127 px, End Day 150 px, destination bar 185 px, together 462 of the 1,760 px between the status bar and the gesture bar (26 %); the painted strip and the counter plate take another 448 px, so the lead card's frame begins 35 % of the way down and its button at 53 %; on the Forge (file 06) the choice chips scroll inside about 28 % of the height. Symptom: reading screens lose a third of their height to a button nobody presses there; the Forge's working area is a quarter of the screen. Fix: End Day outline on non-Shop pages, a 48 dp top bar, the Shop strip at 64 dp while the shelf is empty, and in phase 3 backdrops drawn for the real strip heights.

**9. Vocabulary drift and numbers where words were promised.** Evidence (review B's list, section E of its report): Shop / market / counter / shelf / storage / storeroom; Requests / commissions; blade / weapon; Records / News / Gazette / Journal; heroes / adventurers / customers / buyers / visitors; "Outlook: grave danger · defense 60 vs raid 195", "Forge 100 · militia 5 · sieges held 0", "Fame 0 · 0 victories · 0 kills", "Fine frost Spear (quality 50+)" against the GDD's descriptive rule and the `Labels` doc comment "Descriptive, never numeric". Symptom: the player is never sure whether two words are one thing; the Town header reads like a debug overlay. Fix: a one-page glossary applied to `Lines`, `Labels`, `GameViewModel.describe`, `Tips` and `destName`; the outlook as a word and a bar (owner decision 6 covers which numbers stay).

**10. Back is a different key on every surface.** Evidence: `WorkshopScreen.kt` enables `BackHandler` only when `dest != SHOP`, so Back on the Shop finishes the activity; `UiState.ShopDay.backIsConsumed` is false on the first card and on the Resume prompt, so Back there also exits; inside the day Back steps one card; on the Blessing card it means "Decide later"; a tap outside the Gazette calls `vm.back()`; `dismissBlessingOffer` sets `dest = SHOP` whatever tab the dialog appeared on. Symptom: an accidental Back during a run leaves the game with no confirmation. Fix: Back on the Shop opens the main menu, Back on the first card and the Resume prompt is consumed, "Decide later" stays on the current tab (owner decision 9).

**11. Two pixel scales on one screen, and a third on the main menu.** Evidence: `CounterScene.kt` and `ForgePanel.kt` draw `bg_counter_forge` through `Backdrop`, which scales by `ceil(width / 540)` nearest-neighbour; the source is a concept image (the Style F board in `Pixel art assets/New folder/concept_references/`) reduced to 540 px, so its detail is painterly while the nav icons, the 16 px sprites and the 64 px portraits are true integer-grid art at 2x or 3x; `App.kt` `MainMenu` draws `ForgeScene`, the tile scene, inside a `FramedPanel`, a third style. Seen on the emulator (files 02, 04, 10, and the menu from the other build). Symptom: the strip is the brightest element on every screen and carries no information, and it does not agree with the sprites in front of it. Fix: in phase 3, backdrops drawn at 1x for fixed dp heights (the brief already demands a strict integer grid); until then, dim the strip under the plate and keep it off reading pages.

**12. Nothing strikes, rings or clinks.** Evidence: on "Forge weapon" the button greys, the dialog fades in over 600 ms and the haptic fires when the dialog composes, not on the press (`Dialogs.kt`); nothing on the Forge moves; "List at" closes the dialog and the Forge is unchanged; accepting a request, buying a material and setting a price re-render a row with no motion; there is no sound by decision (plan 10.3). Symptom: the game's whole fantasy is "strike, ring, coin" and the build is silent and still at each of those moments. Fix: phase 1 gives each moment a place (the inline result, the band, the readout); phase 3 gives it frames; audio is an owner decision outside this plan (section 7).

---

## 5. What to keep, and notes by screen

**Keep (both reviews agree):** the accordion forge (`ForgePanel.kt` `Step` and `ForgeSummary`: a familiar recipe is three taps and the draft survives a forge); the counter scene and the shelf band (`CounterScene.kt`: the bust behind the plank counter, the name plate, blades that lift when looked at and dim when sold; the one place the game is a place); the Gazette as paper (fixed ink colours, serif body, the best writing in the game); one lead and one End Day with a contextual note ("A commission is waiting" is the best line of chrome in the build); the accessibility groundwork (merged semantics with spoken deltas, 48 dp targets including the sheet handle, never colour alone, font-scale branches in `StatRow`, `FactRow`, `StockRow` and `WeaponStatCard`, live regions on the day card, reasons on disabled buttons); the load-failure and save-failure copy in `Failures.kt`.

**Loading frame (file 01).** A Material spinner on the dark ground. Replace with the title word over the scene, or nothing.

**Main menu (judged from `App.kt`; layout seen from the other build).** Title, a framed tile scene, status line, one gold plate, an outlined button, a gear. Sound structure, one gold plate alone; the outlined button is the Material default and the tile scene is a different art style from every in-game strip.

**Shop, day 1 (files 02, 03).** Top bar, an 88 dp strip, "Seats 6 · shelf 0 of 8" with the siege sentence, the lead card (a second gold plate beside End Day), "Who is buying" as a three-row census, the shelf section with the tip banner and "Nothing on display.", two door rows with "Open ›". The order is right (lead first); the eye goes picture, plate, End Day, lead. The empty shelf is said twice; the day's first words are jargon and a siege.

**Shop with a blade (file 09).** The band appears with the sword at 128; the lead becomes "Forge for today's buyers"; nothing warns that 128 is above every purse; "Who is buying" is below the fold.

**Forge (files 04 to 06, 08).** The anvil plate over the accordion is the best-designed surface in the build. Faults: the auto-scroll (cause 4), "Nothing on the anvil" after one pick, "Quick · balanced" without labels, "Iron + Ember Resin: Unknown" over "unknown" (the state said twice, the meaning never), and Supplies and Journal between the plate and the steps.

**Forge result (file 07).** A readable, dense, static stat card: name in rarity colour, a two-by-two cell grid, two bars, buffs with sign plates, two footer lines, Store and "List at N". A 600 ms fade is not a reveal; "Renown Unsung (0)" on a blade forged ten seconds ago reads as a flaw; on a plain result ("No buffs.") the moment deflates. Review A's two-beat reveal (sprite and name first, then the card, buffs appearing one by one, rarity tinting the corner studs) is adopted in phase 1.

**Town (files 10, 11).** The adventurer rows are good. The header stacks "Restless · siege on day 5, in 4 days", "Weak to frost", "Outlook: grave danger · defense 60 vs raid 195", "Forge 100 · militia 5 · sieges held 0", "World: Restless Roads" as grey lines, burying the gravest fact in line four; three "No hero stands here yet." plates sit above twelve "hale" heroes with no cause given.

**Records (files 12 to 14).** Check-marked tabs; the Gazette masthead runs into its own "Close" affordance; "Era 1 begins in Emberfall under a restless roads." is a grammar slip in the core template; the Journal prints a four-rung ladder with three "not yet known" after one forge; Legacy's "Next era: 11 starting energy instead of 10" previews are exactly the right disclosure.

**Shop day (seen on the emulator, run A; files lost; plus file 25).** The open card says "DAY 1 / The shop opens" in the top bar, "The shop opens" on the plate, "DAY 1" as the overline and "The shop is open" as the title, then "Tap anywhere to go on. Skip day jumps to tomorrow." inside the card. The customer card is the best composition in the game (bust, plate "Kazimir Holmfirth / Warden. Carries no weapon.", the lifted sword) and the card under it carries "NO SALE / Could afford nothing on the shelf / Could pay up to 90 gold; the cheapest blade is 128 gold." followed by a "LOOKED AT" factor list ("not their kind of weapon, elemental, which they like, they carry nothing, beyond their purse") clipped by the coach line; the top bar read "Customer 1 of 1" after the open card said three customers came. The tally and the till are clean and honest; the till has no pointer forward. File 25 (three blades at 80, 112 and 132, five customers) shows what the open card looks like when there is something to watch for, and it is the better version.

**Resume prompt (files 40, 50; `DayCards.kt` `ResumePrompt`).** Calm, two right choices, fits at 360 dp. It says the day's take ("The shop took 324 gold.") before the day is watched, which spoils the till card; "Skip to tomorrow" lands on the Tomorrow card, not on tomorrow; and it is a third entry into the sequence with its own buttons.

**Sheets and dialogs (judged from source).** Storage: rich (filters, orders, select mode, bulk bar) and a lot of control for a sheet; three horizontally scrolling chip rows inside a vertically scrolling sheet. Supplies: clear; three names for one sheet. Blade sheet: the right order (card, price, lore); the price editor mixes five component types and the number keyboard can cover the actions. Hero sheet: good. Settings: a plate inside a sheet. Run end: one primary at a time handled correctly; outlined buy buttons in the Material default. Load failure: careful copy, pill buttons.

**Large text and small screens (judged from source).** `StatRow`, `FactRow` and `StockRow` stack above 1.3; `WeaponStatCard` goes one cell per line above 1.0; backdrops shrink above 1.15 and below 700 dp; the destination bar keeps a fixed 64 dp with labels that never shrink, which clips "Records" at 2.0 (known); the day screen's progress label ellipsises early beside "Skip day"; at 360x640 dp the chrome is about 180 dp of 640, leaving roughly 230 dp for the Forge steps under the plate, enough for one open step and not for the tip above it. These remain to be captured on device (phase 4).

The full screen-by-screen notes, the forty-six microcopy items and the persona walkthroughs are in review A; the task traces, the navigation critique and the comparison with Reigns, Slay the Spire, Dicey Dungeons, Potion Craft, Moonlighter and Cultist Simulator are in review B.

---

## 6. Design direction and plan

### 6.1 Direction (the rules the redesign follows)

1. **The counter is the stage.** One scene (backdrop, counter, shelf band) is the top of the Shop, the top of the Forge and the top of the day; the blade goes from the anvil to the band to a customer's hand on the same stage. Everything else is a plate under it.
2. **One gold per screen.** The gold plate means "the way forward here" and appears once: the lead's action on the Shop, "Forge weapon" on the Forge, End Day only on the Shop, the wide Next on the day. Everything else is a bronze outline or a gold text action.
3. **Inline over modal.** Anything that changes the save from where the player stands happens in place: the forge result, pricing, listing, accepting, buying one unit. Modals are for reading (hero and blade sheets, the Gazette, a replay), for bulk (Storage, Supplies) and for the two irreversible confirmations (Abandon run, a bulk salvage). Dialogs otherwise only for save failure.
4. **One home per fact.** The threat lives on the Town header and as marks on chips and rows; the lead lives on the Shop (the Tomorrow card hands over, it does not repeat); the shelf is the band; yesterday is the Gazette; the coaching line is said once.
5. **Words first, numbers where the player acts on them.** Bands and words on rows and cards; exact numbers where a decision is made (a price, a purse, a quality threshold on a request) and on the sheets. The siege outlook is a word and a bar, never "60 vs 195" on a planning screen.
6. **Peaks get their own register.** The reveal and a sale are the two rewards of the loop; they get motion, a haptic on the press, a readout that moves, and a frame tint. Nothing else animates for decoration.
7. **One vocabulary.** A small kit of components drawn once (`Frames.kt` grows into it) replaces every stock Material surface the player can see; the kit is specified against the V3 UI pack's nine-slice IDs so the artist's final pack is a drop-in and no screen changes shape when it lands.
8. **One pixel scale per screen.** Backdrops, sprites, icons and frames share an integer grid and a whole-number scale; the strip heights are fixed in dp before the artist starts so the art is drawn for them.
9. **Chrome under a fifth.** Top bar 48 dp, the bar 64 dp, End Day only where it is the next thing; the rest is the game.

### 6.2 Phase 0: quick wins (no art, about one week)

All under about fifty lines each; none changes a save, a rule or a test's meaning. Done when seen on the emulator at font 1.0, 1.3 and 2.0 and at 360 dp, with the device tests and `smoke.sh` passing.

| # | Change | Where |
|---|---|---|
| 0.1 | End Day only on Shop and Forge (gold on Shop, bronze outline with the same note on Forge); 16 dp above the bar; horizontal inset so it is narrower than the bar | `WorkshopScreen.kt` `EndDayButton`, `Scaffold` bottom bar |
| 0.2 | Every `SegmentedButton` gets `icon = {}` (no check marks); every raw `Button` gets the forge shape or becomes `PrimaryActionButton` (Gazette Close, "Alright", both "Try again") | `WorkshopScreen.kt` `SegmentRow`, `ForgePanel.kt`, `SettingsSheet.kt`, `Dialogs.kt`, `Failures.kt` |
| 0.3 | Every raw `OutlinedButton` becomes `SecondaryActionButton`; add a `TextAction` (gold, labelLarge, 48 dp) in `Frames.kt` and use it for every `TextButton` the player sees; `Overline(strong)` becomes gold; Ember stays for warmth only (fire, the sold chip) | `Frames.kt`, `Dialogs.kt`, `DayCards.kt`, `ShopDayControls.kt`, `ItemDetailSheet.kt`, `HeroDetailSheet.kt`, `App.kt`, `SettingsSheet.kt`, `RunEndScreen.kt`, `VisitCard.kt` |
| 0.4 | The purse fact wherever a price is set: "N of 12 heroes in town can pay this" under the suggested price in the reveal and live under the price in the editor; expose `GameEngine.canAfford(state, price)` beside `shopUi`; let `Advice.lead` fire `PRICES_TOO_HIGH` the same day a blade is listed above every purse | `ShopUi.kt`, `Dialogs.kt`, `ItemDetailSheet.kt`, core `Advice.kt` (one condition) |
| 0.5 | Forge scroll: `bringIntoView` only for a step the player tapped; the plate title reads "Sword" after the family and "Iron Sword" after the core; the recipe line includes the family and labels mode and risk ("Quick forge · balanced risk") | `ForgePanel.kt` `Step`, `ForgeSummary` |
| 0.6 | Supplies and Journal as two `TextAction`s inside the anvil plate, so they are reachable without scrolling; a disabled out-of-stock chip opens Supplies | `ForgePanel.kt` |
| 0.7 | The loading frame: the title word in gold over the dark ground, no spinner | `App.kt` |
| 0.8 | Top bar to 48 dp; the Shop strip to 64 dp while the shelf is empty; 24 dp bottom padding under the reveal card and a 70 % scrim so gold does not sit on gold | `WorkshopScreen.kt` `TopBar`, `ShopPanel.kt`, `Dialogs.kt` |
| 0.9 | Hide "Renown" on a blade with fame 0 (the stock rows already filter it); hide the signature ladder until two rungs are known | `WeaponStatCard.kt`, `InfoPanels.kt` `SignatureLadder` |
| 0.10 | `imePadding()` on the blade sheet's column so the keyboard never covers "List at" | `ItemDetailSheet.kt` `ItemDetailContent` |
| 0.11 | Microcopy pass, first tranche: "Serves 6 a day · Shelf 0 of 8"; "Forge integrity 100"; "Requests · 1 open, 1 slot free"; one name "Supplies" for the door, the button and the sheet; the speed chip labelled "Speed: Tap"; one coaching line (`Tips.COUNTER`) and the unconditional line in `ShopOpenCard` removed; the Gazette masthead on two lines with a chevron action and spacing; the core lede's "under a restless roads" | `ShopPanel.kt`, `ForgePanel.kt`, `ShopDayControls.kt`, `DayCards.kt`, `WorkshopScreen.kt` `Tips`, `InfoPanels.kt` `GazettePanel`, core `Gazette` |
| 0.12 | Town header in three tiers: faction and siege date as the title block; the outlook as a word with a `StatBar` (defense against raid) coloured by `EffectKind`; "Weak to frost" as an `EffectRow`; integrity, militia, sieges held and the world name moved to a "Town" fact block under the champions; the empty champion slot says the rule (confirm it in the `Power` resolver before wording it) | `InfoPanels.kt` `TownThreat` |
| 0.13 | A confirmation on Decline (one line, "Keep the offer" / "Decline"), since it is irreversible and sits beside Accept | `ShopPanel.kt` `RequestCard` |
| 0.14 | The shelf-full case mirrored in the reveal (disabled "List at" with the note), as `ForgeSummary` already mirrors a missing material | `Dialogs.kt` `ForgeResultDialog` |

Implementer's duties for every phase: a line under `[Unreleased]` in `CHANGELOG.md` per user-visible change, a ruling in `DECISIONS.md` where a default is chosen, the checklist's section 12 updated with the evidence, and `PROGRESS.md` before the session ends.

### 6.3 Phase 1: the workflow (no art, about three weeks)

Designed so that the art of phase 3 lands inside it without reshaping a screen. Done when the phase 0 gate holds and the fresh-player protocol (plan 9.7) is run on the forge, price, sell, read loop with the five questions of the external review ("Who bought that weapon?", "Why did the other customer leave?", "What changed because of your weapon?", "What will you make tomorrow?", "What does your upgrade do next era?").

| # | Task | Design | Files |
|---|---|---|---|
| 1.1 | Saved state per destination | Wrap the four panels in `rememberSaveableStateHolder().SaveableStateProvider(dest)`; hoist the Forge's scroll and open step into the ViewModel's `Local` if the holder does not cover them; Records keeps its segment and scroll | `WorkshopScreen.kt`, `GameViewModel.kt` |
| 1.2 | The inline forge result | `ForgeSummary` becomes an `AnimatedContent` with two states, draft and result. On "Forge weapon" the haptic fires on the press; the plate flips to beat one (the slot, the sprite at 2x, the name in its rarity colour, the kind line); a tap or 700 ms brings beat two (the card body: bars, buffs one per 120 ms, instant under reduced motion); the frame's corner studs take the rarity colour. Under the plate the shelf band (`ShelfBand`) is drawn on the Forge too. Actions in the plate: "List at [stepper]" (gold), "Store" (outline), "Forge again" (text). With a request pinned (`draft.commissionId`), the gold action is "Set aside for X" and the readiness line is shown. `ForgeResultDialog` is removed; `revealWeaponId` drives the plate state so process death still restores it | `ForgePanel.kt`, `Dialogs.kt`, `GameViewModel.kt`, `CounterScene.kt` (`ShelfBand` reuse) |
| 1.3 | The price stepper | One component (`Frames.kt` `PriceStepper`): minus 10, the number at titleLarge, plus 10, a tap on the number to type; the purse fact under it. Used in the result plate, in the blade sheet (replacing `StockEditor`'s field and buttons), and in a small popover that opens from a `WeaponSlot` on the band (price, Set, Unlist, Details). Stock actions in the sheet become three `TextAction`s under one heading | `Frames.kt`, `ItemDetailSheet.kt`, `CounterScene.kt` `WeaponSlot`, `ShopPanel.kt` |
| 1.4 | One primary per screen | Audit every `PrimaryActionButton` call; the lead's action on the Shop becomes a door-styled row with a chevron when it navigates and stays a plate only when it acts in place (Choose a blessing, Forge this that pre-fills); Accept on a request is a plate only while no other plate is on screen, otherwise a bronze outline | `LeadCard.kt`, `ShopPanel.kt`, `InfoPanels.kt` |
| 1.5 | The Back model | Back on the Shop opens the main menu (`menuOpen = true`); Back on the first day card and on the Resume prompt is consumed (opens Skip); "Decide later" stays on the current destination; a tap outside the Gazette closes the Gazette only | `GameViewModel.kt` `back`, `App.kt`, `ShopDayHost.kt` |
| 1.6 | The shop-day control set | Tap anywhere plus one wide Next; "Skip day" and an "Auto" toggle in the top bar (the multiplier lives in Settings, where `SpeedRow` already exists and is wired); Back becomes a text action in the top bar for re-reading, not a plate beside Next; the coaching line once; the plate under the scene shows the customer's name on visit beats and the shelf count otherwise, never the beat name twice; one day number per screen (the top bar says "Day 1 · evening" on the Tomorrow card) | `ShopDayScreen.kt`, `ShopDayControls.kt`, `CounterScene.kt`, `DayCards.kt`, `SettingsSheet.kt`, `WorkshopScreen.kt` |
| 1.7 | The sale register and the refusal line | On a sale: a gold readout in the day's top bar counts up, "SOLD" becomes a banner across the card, the receipt rows slide in, the slot dims as now. On a refusal: the muted chip, the typed reason, and one actionable line ("38 gold short of the cheapest blade"); the factor list moves to the hero sheet's "With your shop". The progress string counts all customers ("Customer 1 of 3") or says "First at the counter" | `ShopDayTopBar`, `VisitCard.kt`, `ShopDayUi.kt`, `HeroDetailSheet.kt`, core `Lines.considered` (one sentence form) |
| 1.8 | One home per fact | Remove the Shop's `StockRow`s under "On the shelf" (the band and Storage remain; owner decision 10); the Tomorrow card becomes a short hand-off (the day number, the take, "Begin day N") and the Shop's header becomes the morning ("Day 2 · 8 energy · took 128 gold yesterday" with the lead); "Yesterday at the counter" shrinks to one line and the Gazette link; the threat sentence stays on Town and as marks, and leaves the Shop and Forge plates (the siege date stays); one "Forge this" per request, shown once it is accepted; the blessing's entry points reduce to the card and the Shop lead (the `BlessingDialog` goes) | `ShopPanel.kt`, `ShopUi.kt`, `DayCards.kt` `TomorrowCard`, `ForgePanel.kt` `ThreatLine`, `Wanted`, `Dialogs.kt` |
| 1.9 | Day-1 stakes in the sequence | When the field report is non-empty and there is no aftermath card, emit one summary beat ("Beyond the door: 5 went out unarmed; 5 were driven back") with person chips; the Gazette's tally line on the Tomorrow card | `ShopDayUi.kt` `toUi`, `DayCards.kt` (new `FieldSummaryCard`) |
| 1.10 | Preventable errors inline | Every error the screen can know before the tap is a disabled control with its note; `ErrorDialog` is kept only for what the engine alone can refuse, retitled by context ("Not enough gold" over "The supplier says") with a bronze close | `GameViewModel.kt` `describe`, `Dialogs.kt`, `SuppliesSheet.kt`, `Dialogs.kt` |
| 1.11 | The request flow | Accept or Decline first; "Forge this" appears once accepted; the result plate knows the request (task 1.2); the readiness line is on the request card and in the plate | `ShopPanel.kt` `RequestCard`, `ForgePanel.kt` |
| 1.12 | The Resume prompt | Becomes the first card of the day with "Skip day" in its top bar; the take is not said before the till | `DayCards.kt` `ResumePrompt`, `ShopDayHost.kt` |
| 1.13 | Glossary pass, second tranche | Shop, shelf, storage, request, blade, hero, Gazette fixed across `Lines`, `Labels`, `describe`, `Tips`, `destName`, the nav icon names; the middle dot as the one separator (the risk labels and the masthead drop the dash); "hale / bruised / wounded / grave" revisited because "grave" collides with the Grave element | core `Lines.kt`, `Labels.kt`, `GameViewModel.kt`, `WorkshopScreen.kt`, core `Gazette` |

### 6.4 Phase 2: the forge kit (component system, about two weeks)

The kit replaces every stock Material surface the player can see with components drawn once, and is specified against the V3 UI pack (`Pixel art assets/Tiny_Blacksmith_UI_Backgrounds_v3/`: 24x24 nine-slice frames with 8 px caps for forge, market, town, journal, gazette, legacy, weapon result, day report, run end, blessing, card and warning; primary, secondary, disabled and warning button plates; empty and selected slots; fire, town and legacy dividers; soot, parchment, wood and iron tiles; six 40 px nav icons and six 24 px status icons; per-destination backdrops). The pack is a stand-in with the right IDs and sizes, so the artist's final pack lands as a file drop.

| # | Task | Notes |
|---|---|---|
| 2.1 | A nine-slice renderer | One `Modifier.nineSlice(resId, capPx = 8, scale)` drawing corners fixed, edges and centre stretched, at a whole-number scale; the frames are not Android `.9.png` binaries |
| 2.2 | Tokens | Colour roles reduced to: ground, panel, raised, slot, ink, cream, cream muted, gold (accent and headings), bronze and bronze deep (frames), ember (warmth and the sold chip only), buff, flaw, and the paper set; raw `Color(0x...)` literals outside `theme/Color.kt` moved into it (they sit in `Frames.kt`, `WeaponStatCard.kt`, `CounterScene.kt` and `Theme.kt`); `Space` gains `xl` for section rhythm; the type scale stays (serif titles are the game's voice; the product register allows a system sans for everything else) and is checked at 1.3 and 2.0 |
| 2.3 | Components | `Frame(variant)` (replaces `FramedPanel` and `forgeRow`, with variants per the pack's frame set), `PrimaryPlate`, `SecondaryPlate`, `TextAction`, `Slot`, `Divider(kind)`, `Tabs` (replaces `SegmentRow` and the Mode, Risk and speed rows), `Chip` (wraps `FilterChip` with the kit's shape and a sprite slot), `PriceStepper`, `StatBar`, `EffectRow`, `Sheet` (a `ModalBottomSheet` scaffold with the kit's handle, heading and close), `Confirm` (replaces `AlertDialog` for Abandon run and bulk salvage), `Notice` (replaces `ErrorDialog` and `TipBanner`), `Banner` (the stage banner), `Plate` (the counter's name plate) |
| 2.4 | Replace at the seams | Gazette Close, the blessing choices, the error and confirm dialogs, every outlined and text button, the tabs, the price field, the Settings plate-in-a-sheet, the run-end upgrade rows, the load-failure buttons |
| 2.5 | Screenshot set | The capture script re-run for every screen at 1.0 and 1.3 and at 360 dp; the set committed under `docs/ui_review_<date>/` as the before-and-after for the art phase |

Gate: no `Button`, `OutlinedButton`, `TextButton`, `AlertDialog`, `SegmentedButton` or `OutlinedTextField` call remains outside the kit; every screen seen on the emulator.

### 6.5 Phase 3: art integration (when the pack arrives, about two weeks after delivery)

`docs/ART_BRIEF.md` covers sprites, portraits, materials and the forge scene; it has no UI chrome section. The addendum below is what the artist needs for the chrome, with IDs that match the V3 stand-ins so nothing is rewired:

| Asset | ID | Size and anchor | Notes |
|---|---|---|---|
| Nine-slice frames | `ui_frame_card`, `ui_frame_forge`, `ui_frame_market`, `ui_frame_town`, `ui_frame_journal`, `ui_frame_gazette`, `ui_frame_legacy`, `ui_frame_weapon_result`, `ui_frame_day_report`, `ui_frame_run_end`, `ui_frame_blessing`, `ui_frame_warning` | 24x24, 8 px caps | corners carry the detail, edges stay calm so text over them reads |
| Button plates | `ui_button_primary`, `ui_button_secondary`, `ui_button_disabled`, `ui_button_warning`, plus `_pressed` states | 24x24 nine-slice, 8 px caps | the primary is the gold plate; one pressed state each |
| Slots | `ui_slot_empty`, `ui_slot_selected`, `ui_slot_sold` | 24x24 nine-slice | the band's slot, the anvil slot, the portrait tile |
| Dividers and rules | `ui_divider_fire`, `ui_divider_town`, `ui_divider_legacy`, `ui_rule_plain` | 96x8, centre-anchored, tileable ends | section rules and the paper's double rule |
| Tiles | `ui_tile_soot`, `ui_tile_parchment`, `ui_tile_wood`, `ui_tile_iron` | 16x16 seamless | panel grounds, the Gazette paper, the counter planks |
| Nav icons | `icon_nav_market` (Shop), `icon_nav_forge`, `icon_nav_town`, `icon_nav_journal` (Records), selected and unselected | 40x40 | the Records icon should read as records, not a journal |
| Status icons | `icon_day`, `icon_gold`, `icon_energy`, `icon_integrity`, `icon_militia`, `icon_reputation` | 24x24 | drawn on a 24 grid so they sit at 1x in a 48 dp bar |
| Backdrops | `bg_counter_shop`, `bg_counter_forge`, `bg_town`, `bg_records`, `bg_title`, `bg_run_end`, `bg_night` (the day's banner) | drawn at 1x for the fixed strip heights: Shop 88 dp (64 dp empty), Forge 120 dp (72 dp short), day 140 dp (104 dp short), banner 112 dp; width 180 px at 3x covers 540 dp | the brief's integer-grid rule applies; the counter foreground (`counter_front`, 180x24, bottom) is drawn separately so the bust can stand behind it |
| The strike | `fx_strike_0..3` (sparks over the anvil slot), `fx_coin_0..3` (the till), `fx_sold` (the band's sold tag) | 24x24, 120 ms | decorative; the kit already has a place for each |
| Customer reaction | `react_look`, `react_nod`, `react_shrug` | 16x16 overlay on the portrait tile | two frames each; carries what the card narrates |
| Title and menu | `title_wordmark`, the menu scene in the same style as the strips | wordmark 160x40 | replaces the tile `ForgeScene` on the menu and the loading frame |

Gate: one pixel scale per screen; the tile `ForgeScene` and the concept strip retired; the addendum fully consumed; the screenshot set re-taken.

### 6.6 Phase 4: validation (about one week, plus players)

The checklist's open lines in section 12: a scripted capture of every screen at font 1.0, 1.3 and 2.0 and at 360x640 dp (the review's capture script, hardened); a TalkBack pass over the loop (the tap-anywhere gesture and the live region on the day card are the two risks the source reading names); the 720x1280 pass; haptic feel on a phone; the fresh-player protocol with five players; then a golden-image screenshot suite, which the plan deferred until the visuals settle and which can start after phase 3.

---

## 7. Decisions for the owner

The four UI decisions already open in `DECISIONS.md` ("Major update: rulings and open decisions"), with a recommendation each, then six new ones.

1. **Decision 6, numbers on cards and rows.** Recommend: words on rows and cards (rarity, quality band, condition band, "+ Flaming", "− Brittle"), the exact numbers on the sheet and wherever a decision is made (price, purse, a request's threshold); the bars keep their "62/100" on the sheet and the card, drop it on rows. Rationale: the GDD's descriptive rule and cause 9; the player acts on prices and purses, not on power 32.
2. **Decision 7, the gold hierarchy.** Recommend: one gold plate per screen; End Day gold on the Shop only and a bronze outline with its note on the Forge, hidden on Town and Records; Accept and "Choose a blessing" gold only when alone. Rationale: cause 2; both reviews independently name it first or second.
3. **Decision 8, card timings.** Recommend: keep Tap as the default; replace the "Tap / 1x / 2x" chip with an Auto toggle and keep the multiplier in Settings; cut the per-beat lengths by about 40 % until motion exists, since a finished card is being shown for the length of motion that is not drawn; revisit when phase 3 lands.
4. **Decision 14, the onboarding line.** Recommend: one line, shown once under the first customer: "Tap anywhere to go on · Skip day jumps to the evening", and no unconditional line on the open card. "The evening" is right because Skip lands on the Tomorrow card, not on tomorrow.
5. **New: Back on the Shop.** Today it leaves the app without asking. Recommend: it opens the main menu; a second Back there leaves. Also consume Back on the first day card and the Resume prompt.
6. **New: the Shop's duplicate stock rows.** The band at the top and the rows under "On the shelf" show the same blades. Recommend: keep the band (with the slot popover for pricing) and Storage; remove the rows. This is a scope change to a screen the device tests cover.
7. **New: the Resume prompt's take.** "The shop took 324 gold." before the day is watched spoils the till. Recommend: say "Day 1 is done and saved" only, and make the prompt the first card.
8. **New: the debug menu entry.** A build on the emulator shows "Scenarios (debug)" on the player's first menu. Recommend: gate it behind the debuggable flag the way the seeded run already is, so it cannot ship.
9. **New: the main menu's art.** The menu draws the tile scene; every in-game strip is the concept strip; the artist's pack will be a third. Recommend: the menu uses the same backdrop family as the strips from phase 3, and until then the tile scene stays (it is at least true pixel art).
10. **New: sound.** Plan 10.3 deferred audio because no asset exists. Review B rates silence as a larger share of "quirky" than any one screen. Recommend: commission four sounds with the art (strike, reveal chime, coin, the day's close) and the two toggles the GDD lists; it is outside this plan's engineering.

---

## 8. Appendices and evidence index

- `docs/ui_review_2026-10-10/uiux_review_A.md`: design-director critique (heuristic scores, anti-pattern verdict, twelve priority issues, personas, screen notes, forty-six microcopy items, five questions).
- `docs/ui_review_2026-10-10/uiux_review_B.md`: workflow and game-feel review (seven task traces with tap counts, the navigation critique, the End Day sequence against six reference games, feedback and feel, copy and labels, twelve ranked causes, the target workflow with a tap budget).
- `docs/ui_review_2026-10-10/uiux_review_C.md`: component inventory and accessibility audit, pending; it will be added if the audit completes.
- `docs/ui_review_2026-10-10/*.png`: the reliable emulator captures (loading, Shop day 1, Forge steps, forge result, Shop with a blade, Town, Records, the open card with three blades, the Resume prompt at two sizes). The run A day cards were seen and are described in section 5; their files were lost.
- `Pixel art assets/New folder/concept_references/approved_hybrid_direction.png`: the approved Style F direction the strips derive from.
- `Pixel art assets/Tiny_Blacksmith_UI_Backgrounds_v3/previews/`: the unused UI pack this plan specifies the kit against.
- `scratchpad/uiux_capture.sh` (not committed): the capture script; candidate for T2.9 once hardened.

Nothing outside `docs/` was changed by this session, and no source file: this report, its evidence folder, and a pointer in `docs/PROGRESS.md`.
