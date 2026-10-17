# Visual and UX audit: release-readiness review (2026-10-17)

Branch `living-heroes` at `e869c7d` (release 0.9.0), debug build, rules 8 / content 7 / balance 17. This is an audit
only: no production code, art or content was changed. Evidence is in `docs/visual_audit_2026-10-17/` (screenshots
halved to 540 px wide; `crops/` at full device resolution; `sheets/` are contact sheets).

**How the evidence was gathered.** Every screen marked *rendered* was captured on a private read-only `Pixel_10_Pro`
emulator (API 36, port 5562; the other session's 5560 was not touched) and looked at by eye. The play-through was
natural play from a cleared install: the title, the guided first day of a guild run, two forgings, an End Day and
day 2. Deeper states came from the debug build's constructed scenario saves (`app/src/debug/assets/scenarios/`) and
the debug `ShopDayPreviewActivity`; those are labelled *constructed* or *preview* below, because they show real
screens but not necessarily states a player reaches that way. Three read-only agents read the code alongside: an asset
geometry scan of all 686 drawables (Pillow), a spacing and type token scan, and a screen inventory. Findings from
them are marked **code** or **asset** and were not seen on a device unless a screenshot is cited.

Configurations: 360×800 dp (1080×2400 at 480 dpi) at font 1.0, 1.3, 1.5 and 2.0; 393×851 dp (1080×2340, 440 dpi);
411×914 dp (1080×2400, 420 dpi). Gesture navigation, portrait only (the app's only orientation).

---

## 1. Executive assessment

Tiny Blacksmith already has a recognisable look: a warm charcoal ground, ivory text, a gold primary action, Alegreya
headings over Fira Sans, bronze frames and pixel art that comes from one Retro Diffusion family. The title screen is
release grade, and the newest interface icons and 64 px hero faces are crisp and full of character. Where the game
falls short of a commercial indie RPG is not taste but **staging and discipline**:

1. **The big moments are filed like paperwork.** Forging a weapon, a siege day, a hero's death, the forge falling
   and the guild's retirement all reuse the same card or list as a sale or a settings page. The siege, the game's
   recurring climax, looks almost the same as any other day.
2. **The art runs at four pixel densities.** The screen mixes 1 dp per art pixel (icons, faces), 2 dp per pixel (the
   counter scene), about 4 dp per pixel (the evening, siege and fallen banners) and painterly bilinear art (the title,
   the splash, the forge-preview anvil), and on top of that the weapon sprites and hero faces are drawn at
   fractional nearest-neighbour scales. This, more than any single asset, is what makes some screens look cheap
   next to the title.
3. **Heroes are too small to be characters.** The 132 new faces mostly show at 40–44 dp, where the face itself (about
   half the tile) is roughly 20 dp across. The hero sheet leads with a 72 dp tile and then four screens of text.
4. **Rows break on ordinary phones.** At 360 dp and font 1.0, the stock, storage, supplies and wall-defender rows
   squeeze their text into six to nine lines beside a right-hand price or button. At font 1.5 and 2.0 the fixed
   header, End Day bar and nav bar leave the Shop list almost no room.
5. **Components have drifted.** Six section-heading styles, four ways to show "chosen", buttons of 48 and 52 dp, three
   chevron styles, Material leftovers (settings gear, outlined "i", text field, dropdown) beside pixel art.

None of this needs new systems. Most of it is a density policy, three portrait sizes, one row layout rule, a
collapsed header at large text, and a handful of staged moments that reuse art the project already has.

### Scores (based on 58 of about 75 surfaces rendered; see section 5)

| Dimension | Score | Why |
|---|---|---|
| Art direction and identity | 6.5 / 10 | Coherent palette and type, a strong title, one RD family; diluted by density mixing and text-first screens |
| Overall polish | 5.5 / 10 | Few outright bugs at 1.0, but clipped labels, mid-word breaks, layout jitter and Material leftovers add up |
| Visual consistency | 5 / 10 | Token use is good (702 uses of `Space`), but heading, selection, button and icon treatments vary by screen |
| Readability | 6 / 10 at 1.0, 3.5 / 10 at 1.5–2.0 | Generous type and contrast; long prose blocks and squeezed rows; large text loses the content area |
| Asset integration | 5 / 10 | Good icons and faces undermined by size, fractional scaling, crops and four densities |
| Responsive layout | 4.5 / 10 | 393 and 412 dp fine; 360 dp rows break; P0 at 2.0×; nav icons change size with screen density |
| Staging of key moments | 3.5 / 10 | Forge result, siege, death, fall, retirement and run end are under-presented |

**Strengths to keep:** the title screen; the colour tokens and their contrast (cream 14.3:1, muted 10.6:1 on the
panel); the gold primary as the one call to action; the segmented quality and condition bars on the blade sheet; the
customer bust in the counter scene; the Gazette's paper and nine-slice frame; the replay's pops (−14, Charge +1,
Mark applied) and health bars; the 1 px-per-dp interface icons; edge-to-edge insets (the list never scrolls under the
bottom bar, **code**); End Day as a fixed, obvious primary.

**Biggest weaknesses:** the siege day and siege presentation; hero face size and scaling; density mixing; row layouts
at 360 dp; the large-text collapse of the workshop; text-only startup steps after a beautiful title.

---

## 2. Screen-by-screen findings

Severity in brackets refers to the register in section 3. "360@1.0" means 360×800 dp at font scale 1.0.

### 2.1 Splash and title (rendered, natural)
- **Strengths.** The title painting, the gold Alegreya wordmark and the single gold "New game" are release grade.
  The guided-day line ("Your next new game starts with a guided first day · Skip it") is quiet and correct.
- **Problems.** The Android 12 splash shows a soft, small anvil (62% × 41% of its canvas, a soft-edged painting; at
  480 dpi it is drawn at 0.9×, so the softness is the asset, not upscaling) [VA-48]. The settings gear is a white
  Material vector on the brightest part of the sky, not the pixel `icon_settings` the RD set includes [VA-17]. With a
  run saved, "End era" sits right under "Continue" at the same visual weight [VA-58].
- **Recommendation.** A dedicated pixel-art splash mark at a whole scale (or `windowSplashScreenIconBackgroundColor` with
  the launcher's anvil); the pixel gear on a 40 dp dark disc; "End era" as a text button under the primary.
- **Files.** `res/values-v31/themes.xml`; `ui/App.kt:138-186` (gear at :174). **Evidence.**
  `visual_audit_2026-10-17/c360_00_splash.png`, `c360_01_title.png`, `sheets/sheet_records.jpg` (first panel).

### 2.2 Run setup, charter picker, starting kit (rendered, natural)
- **Strengths.** Clear choices, one primary per step, the buff/drawback lists with + and −.
- **Problems.** After the title the next screen is a plain black page with two text cards, a Material check badge and
  about 40% of the screen empty [VA-33]. Charters and kits carry no emblem, no art and no faces for the heroes they
  name, so "Guardian and Warden join on day 1" is read rather than seen. The kit cards are walls of 16 sp prose:
  one and a third kits fit on a screen, the expanded kit fills a whole screen, and the expanders differ (a drawn
  chevron for the relic, a "▾" character for "The first days") [VA-34, VA-19]. The joining heroes' faces are 44 dp
  at a fractional scale [VA-08].
- **Recommendation.** One emblem per charter (the RD set's banner style), the two joining heroes as 64 dp faces on
  each charter card, kit cards collapsed to name + one line + a weapon sprite + the +/− list with "Details" for the
  prose, and the run-setup page given the title painting under a dark scrim so the first three screens feel like one
  sequence.
- **Files.** `ui/RunSetup.kt:34`, `ui/CharterPicker.kt:45`, `ui/OpeningChooser.kt:116-230`. **Evidence.**
  `c360_02_after_new.png`, `c360_03_setup_guild.png`, `c360_04_after_begin.png`, `c360_06_kit_scrolled.png`,
  `crops/crop_kit_faces.png`.

### 2.3 Workshop frame: header, siege strip, guided strip, nav bar (rendered, natural + constructed)
- **Strengths.** Day in serif, gold and energy with good icons and tabular numbers, the five-segment countdown with a
  red last segment, a sticky End Day.
- **Problems.**
  - The header grows from two lines (day 1) to three (day 2, "Siege in 3 days · / Hollowbound") to four (siege day,
    "… · 3 of 3 defenders"), and the gold/energy cluster and the countdown bar move sideways when a digit count
    changes (10 → 8 energy) [VA-23].
  - At 360@1.0 the fixed chrome takes about 290 of 800 dp: header about 75, guidance strip about 75, End Day about
    70, nav about 65. At 1.5 and 2.0 the content area shrinks to about 100 dp and 50–80 dp [VA-01].
  - Content scrolls under the sticky guidance strip with a hard cut; the "FIRST DAY · Done" box stays at the top of
    every Guild tab on day 2 [VA-25].
  - The nav bar's Guild tab wears the walled-town icon; on day 1 the selected tab (bronze fill) and the guided
    highlight (gold outline with a corner flag) compete [VA-24].
  - At 420 and 440 dpi the nav icons render at about 24 dp, against 32 dp at 480 dpi, because `PixelIcon` floors to a
    whole device-pixel multiple [VA-43]. At 1.5× and above the icons also shrink while the labels grow.
- **Recommendation.** A one-line header (day · countdown · gold · energy · gear) with the besieger and defenders moved
  into the siege strip; fixed-width numeric slots; the guidance strip folding to one line once read; nav labels
  dropped above 1.3× (icon + TalkBack label). See section 4 for the nav-icon decision.
- **Files.** `ui/SiegeHeader.kt:110-246`, `ui/IntroStrip.kt:54`, `ui/WorkshopScreen.kt:146, 318-460`, `ui/Sprites.kt`
  (`PixelIcon`, `wholePixelDp`). **Evidence.** `c360_07_first_day.png`, `c360_15_after_list.png`, `c360_23_day2.png`,
  `sc_01_siege_eve_shop.png`, `crops/crop_nav.png`, `f15_shop.png`, `f20_shop.png`, `l412_shop.png`.

### 2.4 Shop (rendered, natural + constructed)
- **Strengths.** The counter scene with the shelf plate is the right idea; the commissions, storage and supplies rows
  have good icons; the lead card ("Worth doing first") is a clear next step.
- **Problems.**
  - The counter scene is a horizontal crop of `bg_counter_forge`: on day 1 a figure is cut off at the right edge, its
    2 dp-per-pixel blocks sit beside 1 dp-per-pixel icons, and at 1.5× it shrinks to a 25 dp sliver with a 20 dp
    face [VA-14, VA-11].
  - Stock rows wrap badly at 360@1.0: "Iron Sword / Uncommon ◈ · / Power 22 · Quality / 47", with values split from
    their labels and a rarity glyph that repeats the badge [VA-05, VA-19].
  - Returning to the Shop keeps the old scroll position, so it reopens mid-row (`c360_17_shop_listed.png`).
  - Four callout styles on one day-2 screen (guidance strip, "OPTIONAL TODAY" card, "A VISITOR AT THE FORGE" row,
    commissions row) with three label colours (gold, orange, cream) [VA-26].
  - "Who is buying" (gold title case) and "WORKSHOP RELICS · 1 OF 3" (small caps) are two heading styles in one list.
  - The relic pedestal art is dark and muddy at 40 dp, and an outlined Material "i" sits beside it [VA-18].
- **Recommendation.** A fixed 2:1 counter banner with a set focal point (anvil left, customer centre, shelf plate
  over the planks), hidden rather than shrunk at large text; the stock row rule from VA-05; one callout style.
- **Files.** `ui/ShopPanel.kt:67-346`, `ui/WeaponInspect.kt:99-154` (stack threshold at :110),
  `ui/shopday/CounterScene.kt:101-284`, `ui/LeadCard.kt:53-89`, `ui/EncounterSheet.kt:67-200`. **Evidence.**
  `c360_07_first_day.png`, `c360_08_shop_scrolled.png`, `c360_18_shop_top.png`, `c360_23_day2.png`,
  `crops/crop_counter.png`.

### 2.5 Forge workbench (rendered, natural)
- **Strengths.** Three slots (weapon, metal, infusion) are an instantly readable recipe; the preview panel with role,
  element lines and "Likely quality 29 to 55" is excellent information design; Quick/Advanced is clear.
- **Problems.**
  - "Practice fight ›": label and chevron clipped by the button edge at 360@1.0 [VA-15].
  - The option tray's third tile (Spear, Stormglass) is cut at the content gutter, which reads as a bug, not a
    scroll cue; "Stormglass" is truncated [VA-16].
  - Empty slots are mostly blank space around a tiny "+"; weapon sprites are about 28 dp in 112 dp tiles.
  - Two anvil pictures in two media: a painterly bilinear "Empty anvil" thumbnail and the pixel scene; the preview
    image is a painted anvil with a glowing sword [VA-11].
  - A literal "?" stands in for an icon ("? Untried combinations"); "Buy" is cream in one place and gold in another.
  - The "⋯" and Risk menus are stock Material dropdowns [VA-32]. The forge uses its own 10 dp corners (the theme's
    largest is 8, the rest of the app 4) and 12–14 sp tile labels (**code**, ForgePanel.kt:114).
  - At 1.5× the Infusion slot falls off the right edge and the options lose their sprites; at 2.0× "2 new" wraps one
    letter per line [VA-06, VA-02].
- **Recommendation.** Fit three tiles to the width or let the tray bleed to the screen edge with a 24 dp peek; give
  empty slots a ghosted outline of the expected item; use the pixel art for every anvil; the house button for
  "Practice fight"; theme radii.
- **Files.** `ui/ForgePanel.kt:114-968` (ForgeBar :265, ForgeLink :361-371, SlotRow :487, Tray :529-570),
  `ui/ForgeUi.kt`. **Evidence.** `c360_09_forge.png`, `c360_11_forge_iron.png`, `c360_12_forge_ready.png`,
  `l412_forge.png`, `f15_forge.png`, `f20_forge.png`, `sheets/sheet_gap.jpg` (the ⋯ menu).

### 2.6 Forge result, "Fresh from the forge" (rendered, natural)
- **Strengths.** The frame, the rarity-coloured serif name, Power and the 10-segment Quality bar with "Fine", the
  suggested price with "6 of 12 heroes can afford" and a gold "List at 108" are all right.
- **Problems.** This is the game's most repeated reward, and it reads as a form. On day 1 the coach text takes the
  top third and pushes the blade down. The sword is about 73 dp in a black square with the rarity badge laid over its
  corner; the type line orphans "sword"; "Flaming" appears twice (+ Flaming, ◆ Flaming); four actions compete (Store,
  List, Set price, Loan). The dialog fades in over the forge's text before the scrim lands [VA-20, VA-21].
- **Recommendation.** See Top 15 #5: a reveal layout led by a 128 dp blade on a lit slot, rarity as a coloured ribbon
  under the name, coach text below the actions.
- **Files.** `ui/Dialogs.kt:104-350` (ForgeResultCard :188), `ui/detail/WeaponStatCard.kt`. **Evidence.**
  `c360_13_forging_anim.png`, `c360_14_forge_result.png`.

### 2.7 Shop day: the End Day sequence (rendered, natural + preview)
- **Strengths.** The card-by-card day is the right structure; the customer bust in the scene, the SOLD bar with
  "+88 gold" and "0 → 88 gold ▲", the till card's scroll-and-coins art and "sold" shelf tiles all work.
- **Problems.**
  - The opening, after-closing and tomorrow cards leave 35–45% of the screen empty; "3 visitors today" is an ornate
    frame around one line; "Beyond the door" is an empty night-sky crop [VA-36].
  - The top bar wraps to two lines on longer titles ("After closing · 1 of 3"), so the scene jumps between cards; the
    "Manual" speed toggle reads as a label.
  - The visit card's reasons are run-on prose ("Reasons to buy. Has the elemental effect… Reasons to pass. Doesn't
    suit their class."); its LOOKED AT thumbnail is centred on the paragraph, not the title; the receipt repeats the
    same number three or four times [VA-37].
  - The customer bust is a square painting with its own backdrop pasted over the scene and cut at the bottom by the
    plate, so it reads as a framed picture on the wall (`crops/crop_customer.png`).
  - The evening, siege and fallen banners are drawn at about 4 dp per art pixel (`crops/crop_evening.png`), far
    coarser than anything around them [VA-11].
  - The Blessing card makes "Decide later" the gold primary while the card's own "Choose a blessing" is disabled
    and scrolled away [VA-07]. The death card uses the sale layout with a 36 dp face [VA-54]. The fallen card is a
    small ruin sprite over an empty lower half [VA-53].
  - A vertical swipe on the siege card advanced to the next card once. Check whether a swipe gesture is intended, and
    whether it can fire while the card still has unread content below (not confirmed as a defect).
- **Files.** `ui/shopday/ShopDayScreen.kt:75-204`, `ShopDayControls.kt:45-98`, `DayCards.kt:90-404`,
  `VisitCard.kt:127-167`, `CounterScene.kt:138-284`. **Evidence.** `c360_19_endday_1.png`, `c360_20_endday_2.png`,
  `c360_20_endday_3.png`, `c360_20_endday_5.png`, `c360_20_endday_6.png`, `c360_20_endday_7.png`,
  `sheets/sheet_endday_567.jpg`, `pv_blessing.png`, `pv_death.png`, `pv_fallen.png` (preview).

### 2.8 Gazette and Records (rendered, natural)
- **Strengths.** The paper, the nine-slice frame and the masthead give the Gazette a real voice; the Legacy page's perk
  list is clear; Townsfolk is a tidy face list.
- **Problems.** Every story has the same weight, so nothing leads; two "A recipe clue" stories sit back to back;
  "Battle replays" is clipped under Close; story text uses the system serif (`FontFamily.Serif`) beside the Alegreya
  masthead (**code**) [VA-35]. The Notebook tabs break words in the middle at 360@1.0 and 412@1.0
  ("combinat / ions", "combinatio / ns") [VA-03]. The Records tabs clip at 1.5× ("Noteboc") and run together at 2.0×
  [VA-04]. Legacy level dots ("○○○") are tiny.
- **Files.** `ui/Dialogs.kt:350-500`, `ui/InfoPanels.kt:351-537`, `ui/Notebook.kt:76-244` (tabs :99-108),
  `ui/WorkshopScreen.kt:359-373`. **Evidence.** `c360_21_gazette.png`, `c360_51_records.png`, `c360_52_notebook.png`,
  `crops/crop_nbtabs.png`, `l412_notebook.png`, `f15_notebook.png`, `f20_notebook.png`.

### 2.9 Guild: Quests, Roster, Wall, Charter (rendered, natural + constructed)
- **Strengths.** Sticky tabs; "Fit / Armed / Unarmed / Injured · day 7" status chips; the forge-health bar on the Wall;
  "Ways to answer it" for the army is excellent guidance.
- **Problems.**
  - Quest rows are a single metadata sentence ("Reward up to 105 gold and 2 more · – Dangerous · Away 2 days, back
    day 4 · Fee 10 gold · Send by day 3"), with risk as an inline red word after a stray "–" and no region art
    [VA-38].
  - The same "Nothing urgent today / Known preparation / Quests are on the board" panel sits at the top of all four
    tabs [VA-25].
  - Roster and defender faces are 36–44 dp at fractional scales [VA-08].
  - The defender row with "Release" wraps to six lines at 360@1.0 and seven at 1.3; "Unarmed" shows as both a chip and
    a right-hand label [VA-05].
  - The enemy army is text ("The Pale Knight · Health 96") although the bestiary sprites exist and the classic Town
    and the replay show them [VA-41].
  - Charter text is whole paragraphs in green and red, which is hard to read as running text.
- **Files.** `ui/GuildPanel.kt:104-339` (tabs :194, Face :232), `ui/GuildParts.kt:61-511` (OfferCard :132, MemberRow
  :245, wallRows :295, Places :390), `ui/GuildUi.kt`. **Evidence.** `sheets/sheet_guildA.jpg`, `sheets/sheet_guildB.jpg`,
  `c360_29_roster.png`, `c360_31_wall.png`, `c360_33_charter.png`, `sc_02_wall.png`, `sc_04_wall3.png`, `f13_wall.png`,
  `crops/crop_rosterface.png`.

### 2.10 Contract (party) sheet and Assign sheet (rendered, constructed `guild_stormwell`)
- **Strengths.** The retreat rules as three cards with the chosen one filled; "Works together" in green; a sticky
  summary over a gold "Plan departure".
- **Problems.** The selected party is shown as bordered text boxes without faces, while the unpicked roster below
  has them; the ↑/↓ reorder buttons are 48 dp outlined boxes that dominate each member; the Assign sheet opens on a
  paragraph of rules [VA-57].
- **Files.** `ui/ContractSheet.kt:55-205`, `ui/GuildPanel.kt:297`. **Evidence.** `gp_02_contract.png`,
  `gp_03_contract_picked.png`, `gp_04_contract2.png`, `gp_06_assign.png`.

### 2.11 Hero inspect card and hero sheet (rendered, natural)
- **Strengths.** Abilities named with a kind tag (Ability, Passive, Trait); story lines (Grudge, Ambition, Background)
  give heroes a life.
- **Problems.** The inspect card leads with a 40 dp face and plain "Health 40/40 (100%) · Attack 5 · Support 4 Armor
  each round". The full sheet gives a face declared at 72 dp (the face itself about half of it) and then four
  screens of text: no health bar, no stat icons, the
  weapon slot as the word "Unarmed", and "Dismiss from the guild" mid-sheet in the same outlined style as "Close"
  [VA-13].
- **Files.** `ui/detail/HeroDetailSheet.kt:285-508` (face :366), `ui/GuildInspect.kt`, `ui/Inspect.kt:201-300`.
  **Evidence.** `c360_34_hero.png`, `c360_38_herosheet.png`, `sheets/sheet_hero.jpg`, `sheets/sheet_herosheet.jpg`.

### 2.12 Storage, blade sheet, price editor (rendered, natural)
- **Strengths.** The blade sheet's Power / Fame / Element / Quality / Condition block with segmented bars is the best
  stat presentation in the game.
- **Problems.** The storage row wraps to six lines ("Iron Axe / Uncommon / ◈ · Power 21 · / Quality 48 / – /
  Bloodbound") beside "In storage" and "List at 84" [VA-05]. The price field is a stock Material OutlinedTextField with
  a floating label, the only one in the app [VA-31]. The sheet's four actions use three styles: a small gold "List at
  84", indented text links (Salvage, Upgrade, Donate) and an outlined "Loan to…" [VA-28].
- **Files.** `ui/StorageSheet.kt:98-277`, `ui/detail/ItemDetailSheet.kt:350-508`, `ui/detail/PriceEditor.kt:75`.
  **Evidence.** `c360_42_storage.png`, `c360_43_item.png`, `sheets/sheet_item.jpg`.

### 2.13 Supplies, commission board, visitor sheet, relic offer, settings (rendered)
- **Supplies (natural).** Rows run to nine lines at 360@1.0, squeezed between the icon, an "i" and Buy; "– 0 left today"
  puts a red minus in front of a count; locked metals carry long explanations inline [VA-05, VA-39].
- **Commission board (natural).** Clean; fine as is.
- **Visitor sheet (natural).** No picture of the visitor; an unaffordable option looks like an available one with red
  text [VA-40].
- **Relic offer (constructed `relic_offer`).** Relic art floats in an 80 dp left column that narrows the text; "Take"
  greyed at the bottom right with two text links beside it.
- **Settings (natural).** A full gold frame inside a bottom sheet with its own drag handle (a double container), and
  Material switches [VA-30].
- **Files.** `ui/SuppliesSheet.kt:113-238`, `ui/CommissionBoard.kt:79`, `ui/EncounterSheet.kt:96-259`,
  `ui/SettingsSheet.kt:45-116`. **Evidence.** `c360_46_supplies.png`, `c360_48_board.png`, `c360_24_visitor.png`,
  `sc_16_relic_offer.png`, `c360_49_settings.png`, `sheets/sheet_sup.jpg`.

### 2.14 Siege day, siege replay, siege report (rendered, constructed `guild_siege_eve`)
- **What already changes on a siege day.** "Siege tonight" turns orange, the last countdown segment glows, the Guild
  tab opens on the Wall at one day out, the lead card says "Keep your plan", and End Day's subtitle names the
  besieger.
- **Why it is not enough.** The Shop is otherwise identical to an ordinary day: no banner, no enemy, no change of
  light, and the header grows to four lines. The siege outcome card reuses the gate banner at 4 dp per pixel, and
  "ON THE WALL" is cut at the card's bottom [VA-12].
- **Replay.** It works: attackers and defenders with health bars, pops ("−14", "Charge +1 Stormcharged", "Mark
  applied · Sun"), a gold Pause. But it plays on plain black with no wall or field; a large empty band separates
  the two lines; "RECENT" is clipped by the controls; five buttons in two rows; and in this save the Bone warden and
  the Hollowbound shambler share one sprite while "Hollowbound s…" truncates [VA-51, VA-42]. Faces in the replay are
  drawn bilinear at about 2.5× (**code**, SiegeReplay.kt:301-303) [VA-10].
- **Report.** A text document: outcome, four facts, defender rows, "Worth knowing", and a small left-aligned gold
  Close. No standout defender, no wall art [VA-55].
- **Files.** `ui/SiegeHeader.kt`, `ui/shopday/DayCards.kt:230-312`, `ui/replay/SiegeReplay.kt:121-429`,
  `ui/replay/SiegeReport.kt:65-198`. **Evidence.** `sc_01_siege_eve_shop.png`, `sheets/sheet_siegeeve.jpg`,
  `sc_05_siege_card.png`, `sc_08_replay_mid.png`, `crops/crop_replay.png`, `sc_10_report.png`,
  `sheets/sheet_siege.jpg`, `sheets/sheet_report.jpg`.

### 2.15 Classic Town (rendered, constructed `siege_long_assault`)
- **Strengths.** The best siege preparation screen in the game: besieger art, matchup chips (− Falling short, + Weak
  to Frost), the Town defense against Raid bar (195 vs 212), the modifier with its hourglass icon.
- **Problems.** The "town holds if…" paragraph and the defence breakdown ("Defenders 142 · Militia 29 · Armory 24 ·
  Militia 29 · Sieges held 1 · Armory 24/30 · World Calm Season") are dense and repeat terms; in this save the defender
  rows show grey class silhouettes. The silhouette is deliberate for an older save (`ui/Sprites.kt:120`; re-stamping old
  heroes is open as design 6.3), so it is not listed as a defect, but a new run should be checked.
- **Files.** `ui/InfoPanels.kt:85-300`. **Evidence.** `sc_13_town.png`, `sc_15_town3.png`, `sheets/sheet_town.jpg`.

### 2.16 Run end, retirement and End era (rendered, constructed `guild_charter_eve` + natural)
- **Problems.** The guild retiring with the charter secured, the best outcome in the game, shows as a black page of
  text: a serif title, "Legacy reward 38 points", Hall of Legends as plain lines, and a perks list that looks like
  Settings with greyed "8 points" buttons. `bg_run_end_fallen_forge` exists and is never drawn (**asset**) [VA-52].
  The End era and Retire confirmations are framed dialogs whose destructive choice is a text link styled like the
  cancel ("Keep playing / End era", "Not now / Retire the guild"), and the End era dialog sits over the wordmark
  [VA-58].
- **Files.** `ui/RunEndScreen.kt:42-130`, `ui/App.kt:177`, `ui/GuildPanel.kt:147`. **Evidence.** `sc_22_runend.png`,
  `sheets/sheet_runend2.jpg`, `sc_17_abandon_confirm.png`, `sc_21_retire_confirm.png`.

### 2.17 Practice fight bench (rendered, constructed `guild_stormwell`)
- **Strengths.** The "Try it differently" chips (Carried by, Company) are tidy and 48 dp.
- **Problems.** The result is a text log ("Round 1: Bench Guardian gains 3 Armor…") with no fighter art, though the
  replay stage exists [VA-56].
- **Files.** `ui/BenchSheet.kt:105-189`. **Evidence.** `gp_07_bench.png`, `sheets/sheet_bench.jpg`.

---

## 3. Issue register

Severity is the defect class: P0 broken or unreadable, P1 a major problem, P2 inconsistency or sizing, P3 an
enhancement. Opportunities (an unstaged moment, a missing picture) are P3 here even where they rank high in the
Top 15 (section 7), which orders by impact. Paths are under `app/src/main/java/com/example/blacksmithproject/ui/`;
evidence under `docs/visual_audit_2026-10-17/`. "Seen" means seen on the device, "code" or "asset" means not seen.

| ID | Sev | Screen / component | Problem | Recommended fix | Evidence | File reference |
|---|---|---|---|---|---|---|
| VA-01 | P0 at 2.0×, P1 at 1.5× | Workshop frame (all tabs) | Header (4 lines on a siege day), stacked End Day and 88 dp nav leave the Shop list about 50–80 dp at 2.0× and about 100 dp at 1.5× | Above 1.3×: one-line header (day, countdown, gold, energy, gear; besieger and defenders into the siege strip), End Day one button with "What happens" in its own row only when there is room, nav icon-only with TalkBack labels | Seen: `f20_shop.png`, `f15_shop.png`, `sheets/sheet_mf20.jpg` | `SiegeHeader.kt:110-215`, `WorkshopScreen.kt:146, 318-460` |
| VA-02 | P0 at 2.0× | Forge bar | The commissions chip "2 new" wraps one letter per line | Count badge on the scroll icon (no word), `maxLines = 1, softWrap = false` | Seen: `f20_forge.png` | `ForgePanel.kt:265-290` |
| VA-03 | P1 | Notebook tabs | Words broken mid-word at 360@1.0 and 412@1.0 ("combinat / ions"); `perRow` only drops to 2 when `fontScale > 1`, so it depends on width, not scale | Pick `perRow` from the measured width (`BoxWithConstraints`: under 4 × 96 dp → 2 per row); never let a label break inside a word | Seen: `crops/crop_nbtabs.png`, `l412_notebook.png` | `Notebook.kt:97-108` |
| VA-04 | P1 | Records page tabs | Labels clip at 1.5× ("Noteboc") and overlap at 2.0×; one line, no overflow, no scroll | Same scrollable tab row as the Guild tabs, or 2 × 2 above 1.3× | Seen: `f15_notebook.png`, `f20_notebook.png` | `WorkshopScreen.kt:359-373` |
| VA-05 | P1 | Stock, storage, supplies, wall-defender rows | At 360@1.0 text squeezes to 6–9 lines beside a right-hand price, button or "Release"; values split from labels ("Quality / 47") | One row rule: below 400 dp width (not 340) the trade column goes under the text; facts on one line as a compact strip ("Rare · Power 27 · Quality 51") that may wrap only between facts (non-breaking spaces inside a fact) | Seen: `c360_18_shop_top.png`, `c360_42_storage.png`, `c360_46_supplies.png`, `sc_02_wall.png`, `f13_wall.png` | `WeaponInspect.kt:110-152`, `SuppliesSheet.kt:125-238`, `GuildParts.kt:390-450` |
| VA-06 | P1 at 1.5× | Forge slots and tray | The Infusion slot falls off the right edge; options lose their sprites | Three equal-weight slots at every scale; at 1.5× and above stack the tray as a one-column list with sprites kept | Seen: `f15_forge.png` | `ForgePanel.kt:487-570` |
| VA-07 | P1 | Blessing card (shop day) | The bottom bar's gold primary is "Decide later"; the real choice is disabled and scrolled away inside the card | The bottom primary becomes "Take <blessing>" (disabled until one is chosen); "Decide later" becomes the secondary | Seen (preview): `pv_blessing.png` | `shopday/DayCards.kt:375-404`, `ShopDayScreen.kt:195`, `ShopDayControls.kt:93-98` |
| VA-08 | P1 | Hero faces in rows and cards | Nearest scaling at fractional factors (1.64–2.25× at 480 dpi) gives uneven pixel columns; seven sizes for one role (40, 44, 48, ~54, 72, 85–110 dp, plus 32–49 dp density-dependent) | Route every face through the whole-scale `PortraitTile` that `VisitCard` already uses; three sizes (section 4.4) | Seen: `crops/crop_rosterface.png`; asset scan | `GuildPanel.kt:232`, `GuildParts.kt:146, 249, 277, 411`, `ContractSheet.kt:107`, `OpeningChooser.kt:152, 166`, `InfoPanels.kt:208, 307`, `replay/SiegeReport.kt:156`, `detail/HeroDetailSheet.kt:366` |
| VA-09 | P1 | Hero identity | The face is about half its tile, so 44 dp rows show a ~20 dp face; heroes are not recognisable between screens | Card size (64 dp) wherever a hero is chosen (party, defenders, kits, recruits); hero banner (128 dp) on the sheet | Seen: `c360_29_roster.png`, `gp_03_contract_picked.png` | as VA-08 |
| VA-10 | P1 (code) | Siege replay faces | `BitmapPainter(FilterQuality.Low)` enlarges 64 px faces about 2.5× bilinear: blurry | `PixelSprite` at a whole scale | Code only | `replay/SiegeReplay.kt:301-303` |
| VA-11 | P1 | Art density across the game | Four densities on screen: 1 dp/px (icons, faces), 2 dp/px (counter scene), ~4 dp/px (evening, siege, fallen banners), painterly bilinear (title, splash, preview anvil, "Empty anvil"), plus fractional weapons | A declared density per layer (section 4.4); the banner art shown at 2 dp/px or redrawn; paintings only full-bleed behind a scrim | Seen: `crops/crop_evening.png`, `crops/crop_counter.png`, `c360_12_forge_ready.png` | `shopday/CounterScene.kt:101-138, 281`, `ForgePanel.kt:400-410` |
| VA-12 | P1 | Siege day (Shop) | Apart from orange header text, the lead card and the End Day subtitle, the day looks ordinary; the header grows to four lines | A siege-day state: the counter banner replaced by the gate banner with the besieger's sprite; one red-gold "Siege tonight · Hollowbound" strip; End Day restyled in ember as "Face the siege" (copy per Writing Guide) | Seen (constructed): `sc_01_siege_eve_shop.png` | `ShopPanel.kt:67-200`, `SiegeHeader.kt:169-246`, `WorkshopScreen.kt:426` |
| VA-13 | P1 | Hero sheet | Face declared at 72 dp (about half of it is face), then four screens of text; no stat bars; weapon slot as text; "Dismiss from the guild" mid-sheet in the "Close" style | Banner (face 128 dp, name, people and class, health bar, condition chip); stat strip with icons; equipment slot with the weapon sprite; abilities folded to name + kind with a disclosure; destructive action last, in FlawRed text, with confirmation | Seen: `c360_38_herosheet.png`, `sheets/sheet_herosheet.jpg` | `detail/HeroDetailSheet.kt:304-508` |
| VA-14 | P1 | Counter scene on the Shop | A strip crop with a figure cut at the right; at 1.5× a 25 dp sliver with a 20 dp face | Fixed 2:1 banner with a focal point; hidden, not shrunk, above 1.3× | Seen: `crops/crop_counter.png`, `f15_shop.png`, `sheets/sheet_mf15.jpg` (visit card) | `shopday/CounterScene.kt:101-226` |
| VA-15 | P2 | Forge preview actions | "Practice fight ›" label and chevron clipped at 360@1.0 | House secondary button, chevron dropped or inside the padding | Seen: `c360_12_forge_ready.png` | `ForgePanel.kt:361-380` |
| VA-16 | P2 | Forge option tray | The third tile is cut at the content gutter ("Stormglass" truncated) | Fit three tiles, or bleed the row to the screen edge with a deliberate 24 dp peek of the fourth | Seen: `c360_09_forge.png`, `c360_11_forge_iron.png` | `ForgePanel.kt:529-570` |
| VA-17 | P2 | Settings gear | Material vector (white on the title, gold in the header); the RD `icon_settings` is unused | `PixelIcon(icon_settings)` in both places, on a dark disc over the title art | Seen: `c360_01_title.png`; asset | `App.kt:174`, `SiegeHeader.kt:117` |
| VA-18 | P2 | Info buttons | An outlined Material "i" beside pixel icons on most rows | A 24 px pixel seal "i" from the RD set, or no button where a tap or long-press on the row already opens the card | Seen: most screens | `Inspect.kt` (`InfoButton`) |
| VA-19 | P2 | Text glyphs as icons | "?", "✓", "◆/◈" rarity glyphs, "▾", "›" next to drawn chevrons | One drawn chevron and expander; rarity by badge and colour only; pixel check in the notice | Seen: `c360_12_forge_ready.png`, `c360_15_after_list.png`, `c360_06_kit_scrolled.png` | `LeadCard.kt:84-86`, `OpeningChooser.kt:218`, `detail/DetailCommon.kt:72, 80`, `ForgePanel.kt:669`, `Frames.kt:258` |
| VA-20 | P2 | Forge result dialog | Coach text above the blade; small sprite with the badge over it; orphaned "sword"; "Flaming" twice; four actions | Reveal layout (Top 15 #5) | Seen: `c360_14_forge_result.png` | `Dialogs.kt:188-350` |
| VA-21 | P2 | Forge result transition | The card fades in over the forge's text before the scrim | Scrim first (or together, opaque card) | Seen: `c360_13_forging_anim.png` | `Dialogs.kt:104-187` |
| VA-22 | P2 | Notice banner | "Listed … for 108 gold" lands on top of the forge preview | Its own slot under the header, or a snackbar above the bottom bar | Seen: `c360_15_after_list.png` | `Frames.kt:258` |
| VA-23 | P2 | Header | Height 2 → 3 → 4 lines across days; the gold/energy cluster and countdown shift with digit counts | Fixed numeric slots (`tnum` already set; give a min width); one-line siege text; countdown full width | Seen: `c360_07_first_day.png` vs `c360_15_after_list.png` vs `sc_01_siege_eve_shop.png` | `SiegeHeader.kt:110-215` |
| VA-24 | P2 | Nav bar | Guild tab uses the walled-town icon; selected tab and the guided highlight compete on day 1 | `icon_nav_legacy` banner or a guild-hall icon for Guild; the guided highlight as a small pulse dot, not a second frame | Seen: `crops/crop_nav.png` | `WorkshopScreen.kt:318-358` |
| VA-25 | P2 | Guidance and Today panels | Content scrolls under the sticky strip with a hard cut; "FIRST DAY · Done" stays on day 2; the Today panel repeats on all four Guild tabs | Fade edge under sticky bars; the done strip collapses to a one-line chip; Today only on the first tab | Seen: `c360_08_shop_scrolled.png`, `sheets/sheet_guildA.jpg` | `IntroStrip.kt:54-112`, `GuildPanel.kt:104-200` |
| VA-26 | P2 | Section headings | Six styles: gold small caps, gold title case (titleMedium), orange small caps, cream small caps, cream tracked caps in the shop day, the Gazette's | Two styles (section 4.2); one helper instead of `SectionTitle` and `SectionHeader` | Seen: `c360_08_shop_scrolled.png`, `c360_23_day2.png`, `sc_04_wall3.png` | `WorkshopScreen.kt:544`, `Frames.kt:322`, `shopday/VisitCard.kt:43` |
| VA-27 | P2 | "Chosen" state | Four treatments: raised panel + gold border + check badge; bronze fill + 1.5 dp border; bronze fill + 1 dp border; gold underline | One: bronze fill + 1.5 dp gold border; underline only for tabs | Seen: `c360_03_setup_guild.png`, `c360_11_forge_iron.png`, `c360_52_notebook.png`, `gp_07_bench.png` | `Frames.kt:165`, `ForgePanel.kt`, `Notebook.kt:105`, `BenchSheet.kt:91` |
| VA-28 | P2 | Buttons | 48 vs 52 dp heights (one 52 and four 48 in one replay row); Accept secondary on the Shop but primary on the board; "Buy" in two colours; small left-aligned primaries on the blade sheet and the report | One height (52 dp); one rule for primary placement (full width in bars, never left-aligned alone) | Seen: `c360_43_item.png`, `sc_10_report.png`, `sc_08_replay_mid.png`; code | `Frames.kt:225-312`, `ShopPanel.kt:231-232, 295-296` |
| VA-29 | P2 | Card and dialog padding | The shop-day frame pads 16 dp inside a 16 dp column (296 dp of text at 360 dp); dialogs stack the same way | One padding owner: the frame pads 16, the column 0 | Code; visible as narrow text in `c360_20_endday_2.png` | `shopday/ShopDayScreen.kt:158-160`, `Frames.kt:126, 204`, `Dialogs.kt:128, 299` |
| VA-30 | P2 | Settings sheet | A full frame inside a bottom sheet (double container, 32 dp to text); Material switches | Plain sheet like the others; theme the switch thumb and track as a brass toggle | Seen: `c360_49_settings.png` | `SettingsSheet.kt:61` |
| VA-31 | P2 | Price editor | Stock Material OutlinedTextField with a floating label | A slot-styled number field (ForgeSlot ground, bronze border, gold digits) between the −10 / +10 steppers | Seen: `c360_43_item.png` | `detail/PriceEditor.kt:75-130` |
| VA-32 | P2 | Forge ⋯ and Risk menus | Default Material DropdownMenu (rounded, elevation, no frame) | The panel surface with a bronze hairline; or a small framed sheet | Seen: `sheets/sheet_gap.jpg` | `ForgePanel.kt:265-290` |
| VA-33 | P2 | Run setup and charter picker | Plain page after the title, Material check badge, ~40% empty, text-only charters | Title painting under a scrim, charter emblem, faces of the joining heroes | Seen: `c360_02_after_new.png`, `c360_03_setup_guild.png` | `RunSetup.kt:34-55`, `CharterPicker.kt:45-96`, `Frames.kt:165` |
| VA-34 | P2 | Starting kit cards | 16 sp prose walls (1.3 kits per screen); two expander styles; relic row touch target 40 dp (code) | Collapsed card: name, one line, weapon sprite, +/− list; prose behind "Details" | Seen: `c360_04_after_begin.png`, `c360_06_kit_scrolled.png` | `OpeningChooser.kt:116-230` (relic row :197) |
| VA-35 | P2 | Gazette | Every story at one weight; duplicate headlines adjacent; "Battle replays" under Close; system serif body beside Alegreya (code) | A lead story (headline in Alegreya 22 sp), the rest as briefs; merge same-kind stories; bundle Alegreya Regular (OFL) or set the body in Fira | Seen: `c360_21_gazette.png` | `Dialogs.kt:350-500`, `InfoPanels.kt:401` |
| VA-36 | P2 | Shop-day cards | 35–45% empty on open, after-closing and tomorrow cards; top bar wraps on long titles; "Manual" reads as a label | Card content vertically centred in the free space or the scene enlarged on short cards; a fixed one-line top bar (title ellipsised, speed as an icon toggle) | Seen: `c360_19_endday_1.png`, `sheets/sheet_endday_567.jpg`, `sc_05_siege_card.png` | `shopday/ShopDayControls.kt:45-92`, `DayCards.kt:90, 342` |
| VA-37 | P2 | Visit card | Reasons as run-on prose; LOOKED AT thumbnail misaligned; receipt repeats one number 3–4 times | Reasons as the +/− list used elsewhere; thumbnail aligned to the title line; one receipt line when price = total | Seen: `c360_20_endday_2.png`, `c360_20_endday_3.png` | `shopday/VisitCard.kt:127-167` |
| VA-38 | P2 | Quest board rows | One metadata sentence; risk as an inline red word after a stray "–"; no picture | Title + risk chip on line 1; a stat strip (reward, away, fee, send by) with icons on line 2; region art at 40 dp | Seen: `sheets/sheet_guildA.jpg` | `GuildParts.kt:132-172` |
| VA-39 | P2 | Supplies | "– 0 left today" in red with a minus; locked items explain at length inline | "Sold out today" in muted text; the lock reason behind the "i" | Seen: `c360_46_supplies.png` | `SuppliesSheet.kt:125-238` |
| VA-40 | P2 | Visitor sheet | An unaffordable option looks available; no visitor picture | Dimmed card with a lock line; the visitor's face or the door icon at 64 dp in the head | Seen: `c360_24_visitor.png` | `EncounterSheet.kt:96-160` |
| VA-41 | P2 | Guild Wall | Enemies listed as text though the sprites exist | The army as a row of 48 dp sprites with health, as the replay shows them | Seen (constructed): `sc_04_wall3.png` | `GuildParts.kt:295-390` |
| VA-42 | P2 | Replay | Two different enemies share one sprite in this save; names truncate ("Hollowbound s…") | Check the bestiary mapping for the shambler (art is WANTED in `docs/ART_NEEDED_BESTIARY.md` batches); two-line names | Seen: `crops/crop_replay.png` | `replay/SiegeReplay.kt:157-300`, `Sprites.kt` |
| VA-43 | P2 (owner decision) | Nav and status icons | `PixelIcon` floors to whole device-pixel multiples: nav icons 24 dp at 420 dpi vs 32 dp at 480 dpi | Decision (section 4.3): accept, round to the nearest multiple, or exempt the nav bar | Seen: `l412_shop.png` vs `c360_07_first_day.png` | `Sprites.kt` (`wholePixelDp`) |
| VA-44 | P2 (asset) | Weapon sprites | 175 of 336 touch a cell edge; bow tips and glow halos cut (worst `weapon_bow_storm_8`, `bow_frost_8`, `axe_sun_8`) | Re-slice the master sheet with a 2 px inset, or regenerate only the 32 worst | Asset scan | `tools/pixelart/import_assets.py`, `WeaponArt.kt` |
| VA-45 | P2 (asset) | Faction sprites | All 12 `faction_*` touch the left and right edges | Re-slice with an inset | Asset scan | `tools/pixelart/import_assets.py` |
| VA-46 | P2 (code) | Portrait "small" variant | `small = true` does nothing (empty maps) at 9 call sites | Remove the flag or supply the tighter crops; the whole-scale fix (VA-08) makes it unnecessary | Code | `PortraitArt.kt:217-222`, `Sprites.kt:114-125` |
| VA-47 | P2 (code) | Touch targets | Relic row 40 dp; guild tabs 44 dp | 48 dp minimum | Code | `OpeningChooser.kt:197`, `GuildPanel.kt:194` |
| VA-48 | P3 | Splash | Small, soft anvil (a painting at 62% × 41% of the canvas) | A pixel splash mark at a whole scale or an icon background colour | Seen: `c360_00_splash.png` | `res/values-v31/themes.xml` |
| VA-49 | P3 | Title background | A painting enlarged 1.43× bilinear, a little soft on 1080 px wide screens | Acceptable; a 1440 px wide export if one exists | Seen: `c360_01_title.png` | `App.kt:145` |
| VA-50 | P3 (asset) | Unused art | `bg_run_end_fallen_forge`, `icon_settings`, `icon_day`, `icon_militia`, `icon_reputation`, `icon_nav_gazette/home/legacy`, `icon_action_hone/donate/price/scrap/legacy`, the never-called `ForgeScene` props | Wire where named in this report (run end, gear, header, blade sheet actions); do not delete | Asset scan | `ui/Sprites.kt:350` |
| VA-51 | P3 | Replay stage | Plays on black; a big empty band between the lines; "RECENT" clipped; five buttons in two rows | The wall as a ground plane (attackers above a parapet, defenders on it), events in the band, controls as one row of icon buttons with Pause primary | Seen: `sc_08_replay_mid.png` | `replay/SiegeReplay.kt:157-429` |
| VA-52 | P3 | Run end / retirement | A black page of text for the best and worst outcomes | Full-bleed `bg_run_end_fallen_forge` (fallen) or the title painting at dawn (retired) under a scrim; legends as plaques; perks as a grid | Seen (constructed): `sc_22_runend.png` | `RunEndScreen.kt:42-130` |
| VA-53 | P3 | Fallen card | A small ruin sprite over an empty lower half | The ruin at 2 dp/px filling the banner; the cause and the day as the headline | Seen (preview): `pv_fallen.png` | `shopday/DayCards.kt:314-341` |
| VA-54 | P3 | Death aftermath | The sale layout with a 36 dp face | A memorial treatment: 64 dp face desaturated with `marker_dead`, the blade's fate on its own line, no gold | Seen (preview): `pv_death.png` | `shopday/DayCards.kt:185-229` |
| VA-55 | P3 | Siege report | A text page; no standout defender | The defender who did most at card size, their blade beside them; facts as a stat strip | Seen: `sc_10_report.png` | `replay/SiegeReport.kt:65-198` |
| VA-56 | P3 | Practice bench | A text log only | Reuse the replay stage in miniature | Seen: `gp_07_bench.png` | `BenchSheet.kt:105-189` |
| VA-57 | P3 | Contract sheet | The chosen party has no faces; ↑/↓ boxes dominate | Faces at card size in the party list; reorder as a drag handle or small icon buttons | Seen (constructed): `gp_03_contract_picked.png` | `ContractSheet.kt:55-205` |
| VA-58 | P2 | Confirmations | Destructive choices styled like cancel ("End era", "Retire the guild", "Dismiss from the guild"); the End era dialog covers the wordmark | A destructive style (FlawRed text button, confirm on the right); dialogs centred in the free area | Seen: `sc_17_abandon_confirm.png`, `sc_21_retire_confirm.png`, `c360_38_herosheet.png` | `App.kt:177`, `GuildPanel.kt:147`, `detail/HeroDetailSheet.kt` |

---

## 4. Design system recommendations

These build on what exists (`ui/theme/*`, `Frames.kt`, `Sprites.kt`). Nothing here asks for a new font, palette or
art style, and each item replaces several one-off fixes.

### 4.1 Spacing
- Keep `Space.xs 4 / sm 8 / md 16 / lg 24`. Adopt the unofficial 12 dp as `Space.smd = 12.dp` for icon-to-text gaps
  (19 uses already, **code**) instead of fighting it, and fold the 2/3/6/10/14 values into it or the others; 2 dp
  stays for hairline insets only.
- One padding owner per container: a framed plate pads 16 dp and its content pads 0 (VA-29). Screen gutters 16 dp
  everywhere (today 16, except Settings at 32).
- Rows: 64 dp minimum, 12 dp icon gap, 8 dp vertical padding, a hairline between rows; no row runs three columns
  below 400 dp (VA-05).

### 4.2 Typography
- Keep the four working styles of `Type.kt`. Reduce headings inside screens to two:
  1. **Section**: `SectionLabel` small caps, gold, over a hairline (most lists already use it).
  2. **Group** (collapsible groups such as "Who is buying", "For hire", "Unavailable defenders"): `titleSmall`
     SemiBold cream with the drawn chevron, no colour change.
  Orange and cream small caps go; the shop day's tracked caps ("LOOKED AT", "THE TILL") become `SectionLabel`.
- Names and key numbers SemiBold at 16 sp or more (the existing rule); the forge's 12–14 sp SemiBold labels move to
  `labelLarge` (**code**, ForgePanel.kt:498-507, 631).
- Prose over three lines inside a card goes behind a disclosure; cards lead with a number, a chip or a +/− list.
- The Gazette body joins the type system (VA-35).

### 4.3 Icons
- Art sizes stay the RD set's: 24 px (status), 32 px (actions, nav), 40–48 px (headings, items). Every call site
  through `PixelIcon` (the owner's rule, kept).
- The density side effect (VA-43) needs an owner decision:
  - (a) accept that the same icon is about 25% smaller on 420 dpi phones;
  - (b) let `wholePixelDp` round to the nearest whole multiple instead of the floor (32 px nav art becomes 36.6 dp at
    420 dpi, still whole pixels);
  - (c) exempt the nav bar and the header only.
  Recommendation: (b). It keeps whole pixels and stops the visible size swing.
- Replace the Material leftovers: the gear (VA-17), the outlined "i" (VA-18), text glyphs (VA-19), the check badge
  (VA-27).
- Touch area 48 dp regardless of the art's size (most already do).

### 4.4 Art density, portraits and framing
- **A density per layer:**
  - interface (icons, badges, faces, weapons, materials): 1 art px per dp, enlarged by whole multiples;
  - scenes and banners (counter, door, evening, siege, fallen): 2 art px per dp, by whole multiples, at a fixed aspect;
  - paintings (title, splash, run end): only full-bleed behind a scrim, never inside a card.
  The ~4 dp-per-pixel banners (VA-11) are the outliers. Show them at 2 dp/px in a smaller box, or redraw them at
  twice the pixel count.
- **Three face sizes, all whole-scale through `PortraitTile`** (64 px faces):
  - Row: 2× = 42.7 dp at 480 dpi (48.8 dp at 420 dpi). Lists and inline mentions.
  - Card: 3× = 64 dp at 480 dpi. Anywhere a hero is chosen or judged: party, defenders, kits, recruits, death card,
    inspect card.
  - Hero: 6× = 128 dp at 480 dpi. The hero sheet banner, the report's standout defender, the counter bust.
  One frame for all three: ForgeSlot ground, 1 dp bronze border, no inner padding (the faces are opaque tiles with
  painted backdrops, **asset**). Rarity-coloured or state rings only on the Card and Hero sizes.
- Weapons: 56 px art at 1× in tight rows, 2× in tiles and the blade sheet, 2× on a lit slot in the forge result.
  Fractional sizes (32, 36, 40, 44, 72 dp) go.

### 4.5 Surfaces and cards
Three surfaces, used by role:
- **Plate**: the nine-slice gold frame. Moments only: the forge result, the day's cards, the Gazette, dialogs.
  Never inside a sheet (VA-30).
- **Panel**: a raised fill (`ForgePanelRaised`) without a border, 4 dp corners. Grouped facts and callouts. One
  callout style with a gold left rule, replacing today's four (VA-25, VA-26).
- **Row**: no fill, a hairline below. Lists.

Corners stay at the theme's 2/4/6/8; the forge's 10 dp and the 3 and 7 dp one-offs go (**code**).

### 4.6 Colour and contrast
- Gold fill: one primary per screen. Gold text: interactive links and section labels only.
- Ember/red-gold: the siege. The header strip, the siege-day End Day and the replay frame (VA-12).
- FlawRed: flaws, losses and destructive actions, always with a sign or word. BuffGreen: gains, always with "+".
- Secondary text is CreamMuted everywhere (today 49 of 116 `bodySmall` uses are full cream, **code**).
- Text faded below about 60% opacity (unusable forge options at 50%, **code**) gets a lock line instead of the fade.

### 4.7 Buttons and touch targets
- One height, 52 dp, for primary, secondary and inline buttons (**code**: today 52 / 48 / 48 with eight hand
  patches).
- **Primary**: gold fill, full width in bottom bars, never alone left-aligned in content (VA-28).
- **Secondary**: bronze outline.
- **Tertiary**: gold text.
- **Destructive**: FlawRed text, placed last, always confirmed (VA-58).
- The same action keeps the same style on every screen. Accept is secondary, Decline tertiary, on both the Shop and
  the board.
- Expanders: one drawn chevron, rotating; no "▾" or "›" characters.

### 4.8 Dialogs, sheets and overlays
- Dialogs: scrim first, then the card (VA-21); centred in the free area, not over the wordmark; one 20 dp padding.
- Bottom sheets: drag handle, a title row with a close icon, then rows; no frame inside. The primary action sits in
  the sheet's own bottom bar when the sheet scrolls.
- Notices: a fixed slot above the bottom bar (VA-22).
- Material components (DropdownMenu, OutlinedTextField, Switch, FilterChip) get the panel surface and bronze outline
  or are replaced by house components (VA-30 to VA-32).

---

## 5. Visual evidence and verification gaps

### 5.1 Rendered on the device (58 surfaces)
At 360×800 @1.0 unless noted. Source: natural play, a constructed scenario save, or the debug shop-day preview.
- **Startup (natural):** the system splash, the title (no run, run saved), run setup (classic, guild with charters),
  the starting-kit chooser (collapsed, expanded) with its intro note.
- **Workshop frame:** header and siege strip (day 1, day 2, siege day), the guided strip (steps 1, 2, 4, 5, Done),
  the nav bar.
- **Shop:** day 1 (empty, listed), day 2 (lead card, visitor row, commissions), siege day (constructed), workshop
  relics.
- **Forge:** empty, weapon tray, metal tray, infusion tray, ready, the ⋯ menu (constructed).
- **Reveal and notice:** the forge result (transition and settled), the listing notice.
- **Shop day (natural, day 1):** open, two visits (no sale, sold), close/till, after-closing field summary, tomorrow.
  Siege outcome (constructed). Death, Fallen, Blessing and siege outcome (preview `aftermath_death`, `fallen`,
  `blessing`, `living_siege`).
- **Sheets and dialogs:** the Gazette (day dialog), the visitor sheet, the hero inspect card, the hero sheet, storage,
  the blade sheet with the price editor, supplies, the commission board, settings.
- **Guild:** Quests, Roster, Wall, Charter (natural day 2; Wall also constructed siege eve); the contract sheet and
  the assign sheet (constructed `guild_stormwell`); the practice-fight bench (constructed).
- **Records:** Gazette archive, Notebook, Legacy, Townsfolk.
- **Siege (constructed `guild_siege_eve`):** the replay, the siege report.
- **Classic Town** (constructed `siege_long_assault`) and the relic offer (constructed `relic_offer`).
- **Run end:** the End era confirmation (natural), the retire confirmation and run-end screen (constructed
  `guild_charter_eve`).

**Configurations checked on a subset** (Shop, Forge, Wall, Roster, Notebook, a visit card):
- 393×851 @1.0 (440 dpi) and 411×914 @1.0 (420 dpi);
- 360×800 at 1.3, 1.5 and 2.0 (`sheets/sheet_m393.jpg`, `sheet_m412.jpg`, `sheet_mf13.jpg`, `sheet_mf15.jpg`,
  `sheet_mf20.jpg`).

Every other screen was seen at 360 @1.0 only.

### 5.2 Not rendered: findings for these are code-inferred or absent
- **Loading and failure:** the loading spinner, the load-failed and recovery screen, the save-failure dialog, the
  "The forge says" error dialog.
- **Forge, partly or not seen:** the End Day preview dialog (no "What happens" in the states visited), the tip banner
  ("Got it"), the Heartfire row and its confirmation, the Bellows and Ledger rows, the "forging for" brief, the field
  notes (partly seen), the recipe details sheet.
- **Guild and planning dialogs:** the loan picker, the planning Blessing dialog, the relic replace step, the guild
  challenge-tier confirmation.
- **Shop day:** the resume prompt, the quiet-day card, the tally card, the guild contract card, the classic fight
  round list.
- **Replay and run end:** the replay inspector, Claim and Begin era after claiming.
- **First day:** the "Why?" expansion of the guided strip.
- **Shop:** the returned-blades card.
- **Debug only:** the scenario list and the combat sandbox.
- **Configurations:** 1.3, 1.5 and 2.0 were checked only on the subset above. Three-button navigation was not tested
  (gesture navigation only). Landscape is not supported. Dark and light system modes look the same (the theme is
  fixed), so they were not compared. A physical device was not used: colours and sharpness are as the emulator
  composites them.

### 5.3 Code- or asset-only findings
VA-10, VA-29 (verified only indirectly), VA-44 to VA-47, VA-50, and the token counts in section 4 come from reading
code or scanning the PNGs, not from screenshots. The agents' raw tables (all 686 drawables with fill, edges and
colour counts) were kept in the session scratchpad and are not committed.

---

## 6. Implementation roadmap

Each pass ends with the device check of pass 5 for the screens it touched. Effort: S = under half a day, M = one to
two days, L = several days, or new art.

### Pass 1: critical defects and blockers (M)
- **What:** VA-01 to VA-07 (large text collapse, the "2 new" chip, Notebook and Records tabs, the row rule, forge
  slots at 1.5×, the Blessing primary), VA-15 and VA-16 (forge clipping).
- **Shared opportunity:** a `WorkshopHeader` compact mode and one `TradeRow` layout reused by stock, storage, supplies
  and defender rows.
- **Risks:**
  - App tests that read texts and tags: the ShopPanel, Storage and ShopDay tests; one ShopPanel test already fails on
    `main`.
  - The emulator scripts that match labels: `scenarios.sh`, `forge.sh`, `journey.sh`.
  - `runend.sh` already looks stale ("Begin a classic run"). Keep testTags unchanged.

### Pass 2: global consistency and tokens (M)
- **What:**
  - the face rule and three sizes (VA-08, VA-09, VA-10, VA-46);
  - the icon fixes (VA-17, VA-18, VA-19, VA-24, the VA-43 decision);
  - headings, the chosen state, buttons, padding, surfaces (VA-26 to VA-32, VA-58);
  - `Space.smd`, theme radii, the destructive style.
- **Dependencies:** sections 4.1 to 4.8; the owner's VA-43 decision.
- **Risks:** the visual change spans every screen; do it as one change with a full screenshot pass. The whole-scale
  faces change sizes slightly by density, so check at 420 and 480 dpi.

### Pass 3: screen-specific composition (L)
- **What:** the hero sheet (VA-13), the forge result reveal (VA-20, VA-21), the Shop and counter banner (VA-12,
  VA-14, VA-22, VA-23, VA-25), the quest board and contract sheet (VA-38, VA-57), the shop-day cards and visit card
  (VA-36, VA-37), startup (VA-33, VA-34), the Gazette (VA-35), supplies and visitor (VA-39, VA-40), the Wall army
  (VA-41).
- **Dependencies:** pass 2's components.
- **Risks:** copy changes must follow `docs/WRITING_GUIDE.md` and the 2026-10-14 terms; a new label such as "Face the
  siege" is an owner decision.

### Pass 4: premium polish and staged moments (L, partly art)
- **What:** siege day and replay stage (VA-12 art side, VA-51), siege report (VA-55), death and fallen cards (VA-53,
  VA-54), run end and retirement (VA-52), bench in miniature (VA-56), splash (VA-48), the weapon and faction re-slice
  (VA-44, VA-45), the shared shambler sprite (VA-42).
- **Art:** reuse first (`bg_run_end_fallen_forge`, the gate banner, the bestiary sprites, the title painting). New
  pieces (a wall ground for the replay, a 2 dp-per-pixel redraw of the banners, a splash mark) go through
  `docs/ART_BRIEF.md` and the Retro Diffusion workflow. Faces are not recoloured (owner rule).
- **Motion:** only where it carries meaning. A short shake and flash on the forge result's rarity, the siege banner
  sliding in on the day, health bars ticking in the replay (already). Reduced motion turns all of it off.

### Pass 5: device verification (S per pass)
- **Matrix:** 360×800, 393×851 and 412×915 at font 1.0, plus 360×800 at 1.3, 1.5 and 2.0 (the owner also asks for
  2.0×), at both 420 and 480 dpi, for Shop, Forge, the forge result, the hero sheet, a visit card, the Wall, the
  replay and the run end.
- **How:** the drive helpers used for this audit, or extend `tools/emulator/forge.sh` and `scenarios.sh`. Check the
  spacing in the screenshots before reporting (owner's padding rule).

---

## 7. Top 15 visual improvements, ranked by expected impact

1. **Stage the siege.**
   - On the siege day, the Shop's counter banner becomes the gate with the besieger's sprite.
   - The header strip turns ember with one line ("Siege tonight · Hollowbound · 3 of 3 defenders").
   - End Day takes the siege styling.
   - The replay plays on a wall: attackers above the parapet, defenders on it.
   - The report opens with the standout defender at Hero size.
   - *Strategy:* one `SiegeDay` flag drives the Shop banner, header and End Day; the replay's ground is a static
     2 dp-per-pixel layer behind the existing stage. Art: one wall ground (RD), the rest is reuse. VA-12, VA-51,
     VA-55.
2. **One art density per layer.**
   - Interface at 1 dp per pixel, scenes at 2, paintings full-bleed only.
   - Fix the ~4 dp-per-pixel banners and the painterly anvils inside cards.
   - *Strategy:* a `Density` enum on the sprite helpers that computes the whole scale; audit each `PixelImage` call
     site against it; replace the "Empty anvil" and preview paintings with the pixel anvil. VA-11.
3. **Make heroes visible.**
   - Three whole-scale face sizes (42.7 / 64 / 128 dp at 480 dpi).
   - Card size wherever a hero is chosen; a hero banner on the sheet.
   - *Strategy:* one `HeroFace(hero, size)` composable over `PortraitTile`; replace the nine call sites in VA-08.
     VA-08, VA-09, VA-10.
4. **Large-text and compact layouts.**
   - Collapse the header above 1.3×.
   - One row rule below 400 dp.
   - Width-based tabs.
   - *Strategy:* pass 1. These are the only P0s, and they hit every screen. VA-01 to VA-06.
5. **The forge result as a reveal.**
   - The blade at 2× on a lit slot, the rarity colour as a ribbon under the name, Power and Quality as the existing
     bars.
   - One primary (List at N) with Store as secondary; Set price and Loan as tertiary.
   - The coach text moved under the actions.
   - *Strategy:* restructure `ForgeResultCard`; scrim-first transition; a one-frame rarity flash (off with reduced
     motion). VA-20, VA-21.
6. **Rebuild the hero sheet.**
   - Banner (face, name, people and class, health bar), a stat strip with icons, an equipment slot with the sprite.
   - Abilities as name + kind with a disclosure; the story in two lines; destructive action last.
   - *Strategy:* reuse `WeaponStatCard`'s bar style for health; pass 3. VA-13.
7. **Stage the end of a run.**
   - Fallen: `bg_run_end_fallen_forge` full-bleed under a scrim.
   - Retired: the title painting.
   - Legends as plaques with the blade sprite; perks as a grid of icons with levels.
   - *Strategy:* `RunEndScreen` gets a background slot and a legends row; the art exists. VA-52, VA-53.
8. **Treat deaths as memorials.**
   - The death card with a Card-size face desaturated under `marker_dead`, the cause as the headline, the blade's fate
     on its own line, no gold accents.
   - *Strategy:* an `AftermathCard` variant for DEATH; the same treatment in the Gazette's lead story. VA-54.
9. **Consolidate the components.**
   - One button height, one chosen state, two heading styles, one chevron, one callout style.
   - One padding owner per container; theme radii.
   - *Strategy:* pass 2, done once in `Frames.kt` and the theme; then delete the hand patches. VA-19, VA-26 to VA-30,
     VA-58.
10. **Compose the Shop.**
    - A fixed 2:1 counter banner that shows the listed blades on the counter.
    - A one-line siege header; the guidance strip folding to a chip once read.
    - One callout style; scroll position reset to the top on returning.
    - *Strategy:* `CounterScene` gets a fixed aspect and a focal point; hidden above 1.3×. VA-14, VA-23, VA-25.
11. **Continue the title into setup.**
    - The run setup, charter and kit screens on the title painting under a scrim.
    - Charter emblems; the joining heroes' faces; kit cards collapsed to name, one line, a weapon sprite and the +/−
      list.
    - *Strategy:* `RunSetup` and `OpeningChooser` take a background slot; the prose moves behind "Details".
      VA-33, VA-34.
12. **Scannable quests and parties.**
    - Quest rows as title + risk chip, then a stat strip with icons.
    - The party list with faces.
    - The Wall's army as sprites with health.
    - *Strategy:* one `StatStrip` component (also used by the report and the hero sheet). VA-38, VA-41, VA-57.
13. **Fill the shop-day cards.**
    - A fixed one-line top bar.
    - Short cards centred, or the scene enlarged.
    - The visit card's reasons as +/− lists; one receipt line.
    - The customer bust framed as standing at the counter, not hung on the wall: a wood-frame slot that bleeds into
      the plate.
    - *Strategy:* pass 3 on `ShopDayScreen`, `VisitCard` and `CounterScene`. VA-36, VA-37.
14. **A front page for the Gazette.**
    - A lead story in Alegreya, the rest as briefs.
    - Same-kind stories merged; the body in the project's fonts.
    - *Strategy:* the `DayReportDialog` and archive layout; bundle Alegreya Regular (OFL, same family) or use Fira.
      VA-35.
15. **Retire the Material leftovers.**
    - The pixel gear; a pixel "i" (or none); a slot-styled price field.
    - Themed menus and switches; no text glyphs as icons.
    - *Strategy:* pass 2; mostly drop-in replacements with the RD icons that are already imported and unused.
      VA-17, VA-18, VA-19, VA-30, VA-31, VA-32, VA-50.

