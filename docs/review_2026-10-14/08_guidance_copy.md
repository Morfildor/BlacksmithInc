# 08 Guidance and copy: player guidance, onboarding, clarity of words

Reviewer lens: can a new player learn the game from the screens, and does every word mean one thing. Evidence is source
only (no device in this brief). Line numbers are on `main` a2e0392. Proposed text follows the house style the
`ProseGuardTest` enforces (no dash, no semicolon chain, "weapon" never "blade") and the DECISIONS.md 4138 ruling
(hidden odds in words, deterministic rules in numbers).

## Summary (10 lines)

1. The guided first day teaches forge, list, End Day. It never says heroes go out to fight or can die, and the one tip that said so (`Tips.END_DAY`) is suppressed while the guidance runs and marked seen when it ends (`WorkshopScreen.kt:159`). The fifth GDD lesson is unreachable for a guided player.
2. Class fit is absent at the one decision point that needs it: the weapon tray has no "favoured by" line (`ForgeUi.kt:261`, hint "What are you making?"); `favoured()` exists but only on Storage rows after the forge.
3. Rarity is shown as a word and a glyph but what it buys (property slots, `Forge.kt:172`) is a deterministic rule stated nowhere.
4. Request, commission and order are still three words for one thing (75 string hits); the 10-10 glossary task 1.13 is open. "Visitor" now means two things (shop browser, morning encounter). "Temper" means two things (a technique, a clue rung about risk). "Renown" labels fame on the card and "Deeds" labels renown.
5. A first guild run gets no guild guidance: the same five shop steps run after the opening is chosen, and the opening chooser's note defines neither charter nor opening.
6. The charter milestone and retirement read as an ending only ("The run ends here as a success"); nothing says continuing is a choice, or that ranks pay legacy points.
7. Loan versus sale is explained on the item sheet (`ItemDetailSheet.kt:391`) but not on the forge result card where the player first meets the Loan button (`Dialogs.kt:202`).
8. Six developer strings can reach the player (an item ID, a milestone key, a relic ID, a hero ID, an enum name, a blank property); `EngineFault` and seven empty states offer no next action; `Failures.kt:179` renders "actionwas".
9. Numbers where words were promised: Town "defense 60 vs raid 195" (open since 10-10), Reckless "About 15% come out flawed", Heartfire "Chance to break through: 12%". Words where numbers were promised: rank descriptions say "a sixth" for 15 %, "half again" for 45 %, and champions need "at least 50 health" while every hero row shows only a word ("bruised" is 50 and up, said nowhere).
10. The T7.2 playtest is still unrun (`docs/PROGRESS.md:35`); every finding here is a reading, not an observation.

## Ranked table

| # | Sev | Defect | Action | Evidence |
|---|---|---|---|---|
| 1 | high | Guided day never says heroes fight and die; the tip that did is suppressed during and after the guidance | Add it to END_DAY's sentence and why (text in Q4) | `Intro.kt:155-156`, `WorkshopScreen.kt:159`, `:356` |
| 2 | high | Class fit not shown where the weapon type is chosen | Family tiles get a detail line from `classFit` (text in Q1) | `ForgeUi.kt:26`, `:261`, `ShopUi.kt:178` |
| 3 | high | First guild run: no guidance for charter, opening, Guild tab, loan, contract | Rewrite the opening note, add one guild step or a Guild tip (Q4) | `IntroStrip.kt:149`, `Intro.kt:46`, `CharterPicker.kt:73` |
| 4 | high | Three words for a commission | One word, "commission", across the 12 lines listed in Q2 | `CommissionBoard.kt:100,111`, `Lines.kt:242`, `ShopDayUi.kt:206` |
| 5 | high | Rarity's effect (property slots) never stated | One line on the result card under the rarity word (Q1) | `WeaponStatCard.kt:132`, `Forge.kt:172` |
| 6 | medium | Retirement reads as the only ending; ranks hide their legacy reward | Rewrite the region card lines and the confirm (Q3) | `GuildPanel.kt:113`, `GuildUi.kt:316`, `GuildContent.kt:469-471` |
| 7 | medium | Loan button on the result card has no explanation | One line above the button (Q3) | `Dialogs.kt:202`, `ItemDetailSheet.kt:391` |
| 8 | medium | "Temper" is a technique and a clue rung about Risk | Rename the rung "Risk" | `RecordsUi.kt:43`, `Labels.kt:68`, `Journal.kt:127-131` |
| 9 | medium | "Renown" shows fame, "Deeds" shows renown | Label the card's number "Fame", keep "Renown n of 3" for the legend count | `ItemDetailSheet.kt:245`, `:175`, `WeaponStatCard.kt:137` |
| 10 | medium | Contract danger is a muted secondary line, so "Lethal" and "Safe" look alike | Danger leads the stage in the sign and colour of its kind | `GuildPanel.kt:325`, `GuildUi.kt:130-134` |
| 11 | medium | Town prints raw defense and raid numbers (10-10 item 9, open) | Keep the outlook word and the bar, drop the two numbers and the "champions 17 · militia 5" line | `InfoPanels.kt:148`, `:166` |
| 12 | medium | Odds shown as percentages (Reckless flaws, Heartfire breakthrough) against GDD "descriptive probabilities" | Words, with the numeric thresholds kept (Q6). Owner decision if the ledger meant the % to be shown | `ForgeUi.kt:113`, `:139-141` |
| 13 | medium | Rank descriptions misstate their own numbers | "15 % stronger", "30 % stronger", "45 % stronger" | `GuildContent.kt:469-471` |
| 14 | low | Champion threshold is a number (50), hero rows are words, and the band edges are never stated | "needs to be bruised or better (50 health)" on both lines | `InfoPanels.kt:191,193`, `Labels.kt:79-85`, `BalanceConfig.kt:84` |
| 15 | medium | Developer strings can reach the player | Replace with words (Q5 list) | `GameViewModel.kt:794-795`, `RunEndScreen.kt:130`, `OpeningChooser.kt:70` |
| 16 | medium | `EngineFault` and seven empty states give no next action | Add one (Q5) | `GameViewModel.kt:450`, `InfoPanels.kt:342,446,463`, `Dialogs.kt:422` |
| 17 | low | "Couldn't confirm whether that last actionwas saved" | Add the space | `Failures.kt:179` |
| 18 | low | "Visitor" means shop browsers and the morning encounter | Encounter becomes "A caller at the forge" | `EncounterSheet.kt:69,103`, `DayCards.kt:89` |
| 19 | low | Guided step 4 says what but not why | Why: compare to learn the pairing (Q4) | `Intro.kt:145-147` |
| 20 | low | `ShopOpenCard` coaching line every day (10-10 item 0.11, open) | Show once with `Tips.COUNTER` or drop | `DayCards.kt:91-96` |
| 21 | low | ErrorDialog title and button: "The forge says" / "Alright" | "Not possible" / "Close" and the reason as the text | `Dialogs.kt:446-448` |
| 22 | low | ProseGuard misses literals inside `${...}`; TalkBack hears "3 blades" | "weapon" in `DayCards.kt:326-327`; scan nested literals too | `ProseGuardTest.kt:18`, `DayCards.kt:326` |
| 23 | low | "No charter" explained as "The shop alone, as before" to a player with no before | "The shop alone: forge, price, sell. No guild." | `CharterPicker.kt:73` |
| 24 | low | Step 3 sentence "It is in Storage, on the Shop" | "Open Storage on the Shop and tap List." | `Intro.kt:138` |

## Q1. The five things the GDD says matter

| Lesson | Taught (first sight) | Reinforced | Missing |
|---|---|---|---|
| Price vs purse | Guided step 3 why: "none can pay more than they carry" (`Intro.kt:164`); `PriceEditor` "3 of 8 heroes in town can afford this price" (`PriceEditor.kt:63`) | Visit headline "38 gold short of the cheapest weapon" (`Lines.kt:97`), lead "Review your prices" (`:251`), Demand rows (`ShopUi.kt:256`), hero sheet "Purse" | Nothing. This is the best-taught lesson. |
| Class fit | Nowhere at the forge. Storage row "Rangers favour the bow" (`ShopUi.kt:180`) after the forge | Visit factor "doesn't suit their class" (`Lines.kt:42`), decision "X doesn't suit a Ranger" (`:116`), Demand "No suitable weapon for Rangers" (`ShopUi.kt:259`), board wants | The weapon tray (`ForgeUi.kt:261`) and the guided step 1. |
| Element vs besieger | Siege warning story "Ashclaw Raiders, weak to frost" (`Digest.kt:171`), Shop note (`ShopUi.kt:90`) | Tray mark "+ Against the siege: Effective against the Ashclaw Raiders" (`ForgePanel.kt:347`), stock rows, Town chips, lead "Arm the defenders · weak to frost" (`Lines.kt:255-257`) | Well covered once the warning is out. Before it, the Town card's "Weak to Frost" chip is the only hint. Acceptable. |
| Quality bands and rarity | Result card "Rare ◆ · Frost spear" and "Fine (52)" (`WeaponStatCard.kt:132`, `ItemDetailSheet.kt:230`); step 3 result line "Quality is your recipe plus this forge's roll" (`Intro.kt:140`) | Supplies "Holds Epic work." (`SuppliesSheet.kt:132`), Heartfire rule (`ForgeUi.kt:148`), commission "fine frost Spear (quality 50+)" (`Commissions.kt:66`) | What rarity does (`Forge.kt:172`: Rare 1 property, Epic 2, Legendary 3) and the band ladder (crude, decent, fine, superb, masterwork) are never listed. "Roll" is jargon. |
| Why heroes die | Not in the guided day. `Tips.END_DAY` "sends the heroes out" (`WorkshopScreen.kt:356`) is hidden from guided players (`:159`) | "Out in the field" card "5 heroes fought without weapons. All had to retreat." (`Lines.kt:199`), aftermath "Their old X would have lost this fight" (`:220`), Gazette "X died. They fell to..." (`Digest.kt:160`), Town "Each needs at least 50 health" (`InfoPanels.kt:191`) | The link from "my weapon" to "their survival" is only ever in hindsight. One sentence at End Day fixes it. |

Proposed text for the gaps:

- `ForgeUi.kt:261` weapon tiles, `detail`: "Favoured by Guardians, Duelists and Battlemages" (from `WeaponFamilyDef.classFit`). The tray already prints `detail` under the chosen tile (`ForgePanel.kt:363`).
- `ForgeUi.kt:26` hint: current "What are you making?" → "Each weapon type suits some classes better. The tile says which."
- `WeaponStatCard.kt:132` under the rarity line, one `Secondary`: "Rarity sets how many properties it can carry: Rare one, Epic two, Legendary three." (deterministic, `Forge.kt:172`).
- `Intro.kt:140` result line: current "Quality is your recipe plus this forge's roll. It sets power and the suggested price." → "Quality is the recipe plus luck. It sets power, rarity and the suggested price."

## Q2. Vocabulary

Terms the 10-10 review listed:

| Pair | One word now? | Evidence |
|---|---|---|
| Shop / market / counter / storeroom | Yes: Shop, shelf, Storage, Supplies. "market" survives only in a tip id and an icon name | `WorkshopScreen.kt:357` `Tip("market", ...)`, `destIcon` `icon_nav_market` |
| requests / commissions / orders | No. Board heading "Commissions" (`CommissionBoard.kt:99`), "Accept an order" (`:100`), "No unmet requests" (`:111`), door "Commissions & customers" (`ShopPanel.kt:139`), lead "Answer X's request" (`Lines.kt:242,246`), income "Requests" (`ShopDayUi.kt:206`), "request paid" (`:343`), Gazette "1 request delivered" (`Gazette.kt:175`), "X's order expired" (`Digest.kt:222`), "Kept for X's order" (`ShopUi.kt:117`), error "That request is no longer open" (`GameViewModel.kt:805`), endDayNote "1 unaccepted commission" (`WorkshopScreen.kt:411`) vs "A new commission needs an answer" (`:426`) | Pick "commission". A hero's standing want stays "want" (already distinct on the board). |
| heroes / adventurers / customers / visitors | Mostly "hero". "Customer" for a hero at the counter is fine. "Visitor" collides: "4 visitors today" (`DayCards.kt:89`) and "A VISITOR AT THE FORGE" (`EncounterSheet.kt:69,103`, `ShopPanel.kt` `visitorNote`). Town heading is "Heroes" in a classic run and "Townsfolk" in a guild run (`InfoPanels.kt:93`, `GuildPanel.kt:126`) | Encounter → "A caller at the forge". Town heading → "Heroes" in both runs. |
| News / Gazette / Journal / Notebook | Yes: Gazette, Notebook, Legacy under Records. "News" only in comments and a key | `WorkshopScreen.kt:458` |
| blade / weapon | Yes, test-enforced, except nested literals (`DayCards.kt:326-327`, TalkBack "3 blades") | `ProseGuardTest.kt:18` |

New guild-era terms, whether the screen that first shows them says what they are:

| Term | First sight | Explained? | Line to add or fix |
|---|---|---|---|
| charter | Title picker "Charter 1 of 4", "Plays as: Protect, strike back, rebuild." (`CharterPicker.kt:71-73`) | No. Says what it changes, never that a charter means a guild run (recruit, loan, send parties, hold the wall) | Under the name: "A charter starts a guild run: recruit members, lend them weapons, send parties out. Without one, the shop alone." |
| opening / package | Chooser "How the guild begins" (`OpeningChooser.kt:95`); the words opening and package never appear | Partly: "Choose one of these. It stays for the whole run" | Fine as is. Keep "opening" out of the UI. |
| contract | Board (`GuildPanel.kt:86`), card "Choose a party ›" (`:332`) | Partly, by the card's parts | Board subtitle: "Work for a party of members. Pick who goes and how they fight. They leave at End Day." |
| mission / deployment | Not shown to the player | n/a | Keep out. |
| posture | Sheet "Posture" with three rules (`ContractSheet.kt:104-116`) | Yes | None |
| reserve | "Keep a member for the wall: they stand first, and no party takes them." (`GuildPanel.kt:249`) | Yes | Button says "Reserve" while the sentence says "Keep": use one. "Keep for the wall" / "Release". |
| loan | Result card button "Loan to a member" (`Dialogs.kt:202`) | No there. Yes on the item sheet (`ItemDetailSheet.kt:391`) | See Q3. |
| bond | Member sheet "Comrades with X since day 12. reason" (`GuildUi.kt:554`) | By content | None |
| law | "Law of the season" block with description and days (`GuildPanel.kt:397`) | Yes | None |
| nemesis | Region block "Nemesis: X of the Ashclaw. Carries Y, a weapon of this forge, since day 9." (`GuildUi.kt:328-330`) | No. Does not say they march with the besieger or that a hunt wins the weapon back | Add: "They march with the next siege. A hunt contract can win the weapon back." |
| rival | "The other guild": wants, next move day, taken list (`GuildUi.kt:325`) | Yes | None |
| preparation / sabotage | Wall "They prepare" with answers and "Or send a party to spoil it." (`GuildPanel.kt:210-215`) | Yes. "Spoil" is the one word, "sabotage" only in code | None |
| Heartfire | Forge row: rule and source, always shown (`ForgeUi.kt:148-149`) | Yes, at length | None |
| deeds / renown | Card "Deeds: 2 of 3" (`WeaponStatCard.kt:137`) and stat "Renown: Unsung (0)" (`ItemDetailSheet.kt:245`, which is `fame`) | No. Two words, two numbers, neither defined | Stat label "Fame". Deeds line: "Renown 2 of 3. Three renown from deeds makes it Legendary." What counts as a deed is still unsaid: one clause from `Legends.kt`. |
| pact | Not in any player string | n/a | None |
| relic | Dialog "A relic for the workshop" with descriptions (`EncounterSheet.kt:200`) | Yes | None |
| catalyst / augment | Slot hints (`ForgeUi.kt:28-29`) | Yes | None |
| temper | Technique "Temper: Fewer flaws..." (`Labels.kt:68,74`) and clue rung "Temper: use Safe forging" (`RecordsUi.kt:43`, `Journal.kt:127-131`) | Collision | Rung label "Risk". |

## Q3. Guild run

**Day-20 milestone, retirement, continuing.** Region card: "The charter warlord leads the first siege on or after day 20." (`GuildUi.kt:316`), then "Charter secured on day 21." with "Retire the chapter" and the ranks (`GuildPanel.kt:383-395`). Confirm: "The run ends here as a success: the forge stands, the charter is secured, and its legacy can be claimed. This cannot be taken back." (`:113`). Nothing says the alternative. Ranks say only what they cost ("Every enemy... a sixth stronger") and not what they pay (`legacyPoints` 2, 4, 6 at `GuildContent.kt:469-471`).

Proposed:
- `GuildUi.kt:316` before the milestone: "The charter is secured when the town holds the warlord's siege on or after day 20. Then you can retire the chapter or play on."
- after it: "Charter secured on day 21. Retire now, or play on: the run goes on until the forge falls, and a rank raises the stakes."
- `GuildPanel.kt:113` confirm: "Ends this run as a success. The forge stands and its legacy can be claimed. Playing on instead keeps everything and risks the forge. This cannot be taken back."
- rank descriptions: "Every enemy on a contract and at the wall is 15 % stronger. Worth 2 legacy points at the era's end." (and 30 %, 4 points; 45 %, 6 points).

**First contract's risk before accepting.** The card gives the enemies with their known moves, the danger sentence, "If it fails", the fee and the siege clash (`GuildPanel.kt:305-335`). Readable, with two faults: the danger sentence is a muted `Secondary` joined to the objective (`:325`), so "Lethal: if the whole party goes down, one dies and one is taken" has the weight of a footnote; and no line says how the party stands against this field. The bench exists for a weapon, not for the party. Proposed: danger leads the stage as an `EffectRow` (BUFF for Safe, NEUTRAL for Dangerous, FLAW for Lethal), objective under it. "Watch out" on the sheet (`ContractSheet.kt:119`) already carries the party's gaps. Hypothesis, not source: a "Looks even / outmatched" word for the party is wanted here. It would be a hidden-odds estimate, so words only, from `missionSetup` strength sums. Owner decision.

**Loan versus sale.** Result card: "Store", "List at 128", "Loan to a member" with no sentence (`Dialogs.kt:191-202`). Item sheet has the one good line: "A member carries a loaned weapon instead of their own. It stays the forge's, and can be recalled while they are in town." (`ItemDetailSheet.kt:391`). Proposed: that sentence, shortened, as a `Secondary` above the result card's Loan button: "A loan stays yours. The member carries it and you can recall it. A sale is theirs for good." The LoanPicker title "Loan X to..." (`Dialogs.kt:419`) is fine.

## Q4. The guided first day, step by step

| Step | Objective | Sentence (on the Forge) | Why | Verdict |
|---|---|---|---|---|
| 1 RECIPE `Intro.kt:117-119` | Choose a weapon, a metal and an augment. | The metal sets its strength and the augment its element. How well they pair is learned by forging. | A costlier metal raises quality and power. Every pairing has a fit you cannot see yet. Your field notes fill in as you forge. | Two sentences, ok. "Element" is not introduced before use. Says nothing of who will want the weapon type (class fit). |
| 2 FORGE `:129-131` | Forge it. | It costs 2 of your 10 energy and one of each ingredient. Quality lands near what the recipe gives, sometimes above and sometimes below. | Risk, below, sets how wide that swing is. Every forge gives a usable weapon, even a flawed one. | Good. |
| 3 LIST `:138,140,164` | Put it on the shelf. | Only listed weapons can sell. It is in Storage, on the Shop. | Only listed weapons can sell. Heroes decide at End Day, and none can pay more than they carry. | Sentence repeats the why's first clause. "It is in Storage, on the Shop" is hard to parse. Result-card line uses "roll". |
| 4 SECOND `:145-147` | Forge one more, your way. | Change the metal or the augment, then compare the two results. | Optional. End the day whenever you are ready. | What without why: the why does not say why compare. |
| 5 END_DAY `:154-156` | End the day. | The shop opens and heroes come in on their own. Each decides whether to buy. | A sale is never certain. The day's cards show what each hero did. | Never says heroes then go out to fight, nor that a weapon can decide whether they return. |
| Outcome `:179-190` | Day 1: Iron Sword sold for 96 gold. / nothing was on the shelf, so nothing could sell. / 3 heroes looked and none bought. | Why they left: 2 couldn't afford anything, 1 found no suitable weapon. The Gazette, in Records, names each under "All details". | Each morning, "Worth doing first" on the Shop names a useful next step. | Good. The reasons line is the one place class fit and purse are both named on day 1. |

No step is longer than two sentences on the strip; the why texts run to three, which is fine behind a button. Terms used before introduction: "element" (step 1), "Risk" (step 2 why, but the control is labelled Risk under the step), "roll" (step 3 result card).

Proposed:
- Step 1 sentence: "The metal sets its strength, the augment its element (fire, frost and so on). Each weapon type suits some hero classes." Why, add: "A hero buys what suits their class and their purse."
- Step 3 sentence: "Only listed weapons can sell. Open Storage on the Shop and tap List." (why unchanged)
- Step 4 why: "Optional. Two results with one change between them teach you the pairing. End the day whenever you are ready."
- Step 5 sentence: "The shop opens and heroes come in on their own. Then they go out to fight, with what they carry." Why: "A sale is never certain. A hero without a good weapon may not come back. The day's cards show what each hero did."

**Does it cover a guild run?** It runs in one (`introCanBegin`, `Intro.kt:46`, waits for the opening to be chosen) and then teaches only the shop. The player's first guild sight is the opening chooser: "Build: Overheal", "Relic · Overflow Basin", "Healing that has nowhere to go becomes Guard and Regeneration" (`OpeningChooser.kt:102-117`) with the note "First game? Each of these is a full start. The guided first day begins once you choose. The shop without a guild is "No charter" on the title." (`IntroStrip.kt:149`). Four sentences, and neither "charter" nor the guild is defined. The Guild tab, loans and contracts are never mentioned. On a fresh account the picker defaults to "No charter" (`GameViewModel.kt:398`), so this only bites a player who picks a charter first, but that player is the one who needs it most.

Proposed: `IntroStrip.kt:149`: "First game? This is a guild run: you also recruit heroes, lend them weapons and send them on contracts. Pick any card. The guided day begins after it and covers the shop. The Guild tab explains the rest." And one extra guided line after the outcome, guild runs only: "Your members are on the Guild tab. Loan them a weapon, then take a contract from the board." (as `NEXT_STEP` is appended, `Intro.kt:166`).

Playtest script (`docs/PLAYTEST_FIRST_DAY.md`): sound. Its six questions do not ask about class fit or heroes dying, so the two gaps above would pass the test unseen. Add: "What happens to the heroes after they buy?" and "Who would want the weapon you made?"

## Q5. Error and empty states

Developer strings a player can see:

| Where | Current | Proposed |
|---|---|---|
| `GameViewModel.kt:794` | "Unknown item ${e.id}." | "That item is not in this version's catalog." |
| `GameViewModel.kt:795` | "X is not a ${e.expected.lowercase()}." (enum: "core", "augment", "catalyst") | "X cannot go in that place of the recipe." |
| `GameViewModel.kt:450` | "The forge cannot do that right now." (no next action) | "The forge could not do that. Try once more. If it keeps failing, end the day and try tomorrow." |
| `RunEndScreen.kt:130` | `else -> key` (raw milestone key) | "A milestone" |
| `OpeningChooser.kt:70` | `relic?.name ?: def.relicId` | "A relic" |
| `ItemDetailSheet.kt:222` | `Property(id.value, "")` | "An unknown property" |
| `GuildUi.kt:261` | `?: m.heroId.value` | "A member" |
| `HeroDetailSheet.kt:158,160` | `?: c.classId.value`, `?: it.value` | "Unknown class", "" |
| `Failures.kt:179` | "...whether that last actionwas saved." | add the space |
| `Dialogs.kt:446-448` | title "The forge says", button "Alright" | title "Not possible", button "Close" |

Empty states without a next action (model to copy: `Notebook.kt:56-59`, "Your first forge will add a metal pairing here."):

| Where | Current | Proposed |
|---|---|---|
| `InfoPanels.kt:342` | "No Gazette reports yet." | "The Gazette prints after your first End Day." |
| `InfoPanels.kt:446` | "No legendary histories recorded yet." | "A weapon that earns three renown, or is forged with Heartfire, is written here when its era ends." |
| `InfoPanels.kt:463` | "No family lines recorded yet." | "A famous hero's line is recorded when an era ends. Their descendants can return." |
| `Dialogs.kt:422` | "The guild has no members." | "No members yet. Recruit one on the Guild tab." |
| `EncounterSheet.kt:178` | "Empty slot" | "Empty. Relics are offered after a held siege." |
| `HeroDetailSheet.kt:217,219` | "No visits or purchases recorded yet." / "No recent activity recorded." | "They have not been to the shop yet." / "Nothing in the Gazette about them yet." |
| `ItemDetailSheet.kt:368` | "No history recorded yet." | "Its story begins when a hero carries it." |
| `GuildPanel.kt:236` | "Empty: nobody else in town is fit to stand." | "Empty. A hero with enough health, or a member kept for the wall, would stand here." |

Dialogs that wrap an engine reason verbatim: `GameViewModel.kt:815` (`EncounterOptionBlocked.reason`), `:825` (`MemberUnavailable`: "is exhausted until day 9", from `GuildOps.kt` with an enum name lowercased), `:829` (`PartyInvalid`: "A party for this contract is one to 3 members."). All read as sentences. The wound kind "is scarred until day 9" needs no fix.

## Q6. Numbers versus words

Ruling: hidden odds in words, deterministic rules in numbers (`docs/DECISIONS.md:4138`, GDD line 356 "descriptive probabilities").

Odds shown as numbers (should be words):

| Where | Current | Proposed |
|---|---|---|
| `ForgeUi.kt:113` | "About 15% come out flawed, and a flawed weapon is still usable." | "A fair share come out flawed, and a flawed weapon is still usable." |
| `ForgeUi.kt:139-141` | "Chance to break through: 36% at quality 70, up to 62% at quality 83." | "Can break through from quality 70. The higher the quality, the likelier." (the floor stays a number: it is a threshold). Owner decision: the ledger D3 (`RUN_VARIETY_LEDGER.md:22`) states the formula in percent, but does not say it is shown. |
| `InfoPanels.kt:148,166` | "Town defense 60 · Raid 195" and "Defense is champions 17 · militia 5 · watch 3" | Drop both. The outlook word ("Outmatched") and the bar already say it. 10-10 item 9, still open. |
| `HeroDetailSheet.kt:135` | "Fame 0 · 0 victories · 0 kills" | Victories and kills are records (numbers fine). Fame is a hidden band: "Unsung · 0 victories · 0 kills". |

Deterministic rules shown as words (should be numbers or the exact band):

| Where | Current | Proposed |
|---|---|---|
| `GuildContent.kt:469-471` | "a sixth stronger" (15 %), "a third stronger" (30 %), "half again as strong" (45 %) | "15 % stronger", "30 % stronger", "45 % stronger" |
| `InfoPanels.kt:191,193` | "Each needs at least 50 health." while rows say "bruised" or "wounded" and no screen says where the bands fall | "Each needs to be bruised or better (50 health)." and "No other hero is bruised or better (50 health)." The threshold (`BalanceConfig.kt:84`) already sits on the band edge. |
| `Lines.kt:342` | budget "about 90 gold" | A hero's purse is a stored number. Say it: "Budget 94 gold." The "about" hides nothing random. |
| `ShopUi.kt:180` | "Rangers favour the bow" | Fine as words: the multiplier is internal. |

Correct already: `riskExplanation` SAFE and BALANCED (spread in numbers), posture lines (`GuildUi.kt:139-143`), Heartfire costs (`ForgeUi.kt:134`), the bellows debt (`ForgePanel.kt:644`), quality bands as word plus number, siege day counts, the affordability count under the price.

## Prior findings checked

| Item | Status |
|---|---|
| 10-10 cause 9 vocabulary: News → Gazette, blade → weapon, "hale/grave" health words | fixed ("Gazette" everywhere, `ProseGuardTest`, "critical" and "Dire odds" at `Labels.kt:50,84`) |
| 10-10 cause 9: requests / commissions, Town numbers | open (Q2, Q6) |
| 10-10 task 0.11: `ShopOpenCard` unconditional coaching line | open (`DayCards.kt:91-96`) |
| 10-10 task 0.2: "Alright" as an orange pill | fixed shape (`PrimaryActionButton`), label open |
| 10-10 task 1.13 glossary second tranche | open |
| 10-10 task 2.3: `ErrorDialog` replaced by a `Notice` | open, still an `AlertDialog` for every rejection |
| 10-09 F04 commission quality wording vs rule | fixed: `Labels.request` prints the rule's own number (`Commissions.kt:66`) |
| 10-10 cause 1 (first-time player lists at the suggested price and sells nothing) | mitigated: affordability count under the price (`PriceEditor.kt:63`), day-1 outcome names the reasons (`Intro.kt:188`) |

## Owner decisions

1. Heartfire breakthrough as a percentage or as words? The GDD and DECISIONS 4138 say words. Trade-off: the percent is the one number that makes a Heartfire forge plannable.
2. Keep Town's raw defense and raid numbers for the players who want them (behind "Details"), or drop them as the GDD asks?
3. One word for a hero's standing order: "commission" (formal, already the board's heading) or "order" (plain)? "Request" should go either way.
4. A party-strength word on the contract card ("Evenly matched", "Outmatched") would be a new hidden-odds estimate from `missionSetup`. Worth the risk of being wrong?
