# Changelog

All notable changes to Tiny Blacksmith are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [SemVer](https://semver.org/)
with `0.y.z` during early development (minor = new feature set, patch = fixes/tuning). The app version lives in
`app/build.gradle.kts` (`versionName`, `versionCode` increments on every store-facing build).

## [Unreleased]

### Added
- The shop-day Tally and closing cards carry a painted vignette (a chest of coins, a receipt with seal and coins).
- New owner art for the six backgrounds and wall and floor tiles, the eight workshop props (furnace states, anvil, tool rack, shelf, siege walls) and sixteen icons (rarity and flaw badges, Integrity, Militia, Reputation, Purse, Settings, the Gazette, Home and Legacy tabs, the hero markers).
- New owner art for all eight blessings (the blessing choice, the Town panel and the Shop).
- New owner art for all sixteen materials and seven icons (day, energy, gold and the Forge, Market, Town and Journal tabs).
- New painted vignettes on the shop-day banners: the ruined forge on "The forge has fallen", the dawn at the open door on the "Day N" card and the gatehouse on a held siege (they replace the plain backdrops there).
- The price is chosen on the forge result. "−10" and "+10" step it and the number can be typed; "List at" carries the chosen price and Store is still there. Under the price the suggested price is named apart from your own ("Suggested price 124 gold. Your price is 30 below it."), and a line counted again on every change says how many heroes can pay it ("4 of 12 heroes in town can afford this price."), in red when nobody can, with a note that being able to pay is not a sale. The blade sheet's price has the same lines.
- After "List at" or Store the workshop says where the blade went ("Iron Sword is on the shelf at 90 gold. Shelf 3 of 8." or "Iron Sword is in storage, not for sale. List it from Storage in the Shop."). Closing the result card with Back or a tap outside stores the blade and says so too.
- A blade forged with "Forge this" from a request keeps the request on its result card: who asked for what and on what terms, whether this blade fits, and what End Day will hand over as the shop stands. It says that no blade is set aside.
- Storage and a blade's details are one sheet: a blade opened from Storage shows in the Storage sheet under "‹ Storage", and Back returns to the list with its filters, order and place as they were.
- Every stock change says what it did, in the sheet it was done in or over the workshop: listed, a new price, back in storage, honed (quality and condition, before and after), melted down, given to the watch. After List, Salvage or Arm the watch the blade's sheet closes onto where it was opened from; after Set price, Unlist or Hone it stays on the blade.
- "Open Supplies" stands beside "Out of stock" in the Forge's material steps, and under the anvil plate when a material you chose has run out.
- A bulk salvage or "Arm the watch" in Storage says how many blades it took.
- A day when heroes went out to fight and nothing else beyond the door earned a card now has one short card, "Out in the field", with the counts ("5 heroes went out unarmed and all were driven back."). The Gazette still has every name.
- A sale at the counter wears a gold band with the coin it brought ("SOLD +96 gold"; a request reads "REQUEST PAID"), and under its receipt "Earned today" shows what the day stood at before and after ("96 → 228 gold"). The tally shows the same line when someone in it bought.

### Changed
- Title screen: the workshop at night as a full-width picture with the game's name on it, the era and legacy on a plate, and the actions at the foot of the screen.
- Shop day: the first-run hint reads as a tip; long cards fade out at the controls; "No sale" is a red badge; the evening card shows purse, shelf and storage as tiles with a larger dawn on tall screens.
- Shop: the siege line and the forge's health are one strip at the top, the same as on the Forge; the shelf is drawn with its free places; the day's lead is marked "Worth doing first"; the rows to the board, storage and supplies carry icons; the tip about listing leaves once a blade is listed. Lists fade where they run under a pinned button.
- Forge: a chosen ingredient lands in its place (not with reduced motion); tiles and recipe places in one row share one height; the chosen mode is gold.
- Wording: the worst siege outlook is "Dire odds" (it was "Grave danger", beside the Grave element); the Records tab and the Forge's menu say "Notebook".
- Town's siege card is laid out as facts. The besieger's face, when it comes, its name and how hard it presses; "+ Weak to Frost" and "− Resists Fire" as two marks; Town defense and the raid as two numbers over one bar split in their proportion; Forge health as a bar with its number and its ceiling ("68/100"); militia, sieges held, the armory and the world's season as cells. The other factions and the champions are rows of one shape, each face in the same slot.
- The Gazette in Records is a sheet of the paper: masthead over a double rule, the day as a dateline with "Hide" on the same 48 dp line, the tally as a deck, section labels running into a rule. Older days are archive rows with the day as a date block. The day's own Gazette has the same body and an ink "Close" button.
- The Notebook and Legacy pages have a head plate each. The notebook's three kinds are three tabs of one width; a pairing shows its two ingredients in one slot and its stage apart from the journal's words; an empty kind is a blank plate. Legacy shows the banked points as one number, and every upgrade's name starts at the same edge with its level at the end of the line.
- Supplies: the purse stands beside the title, tools are rows like materials (name, level dots, what it does, "Level 0 of 2 · 300 gold", one "Buy") and the list ends with "Close". The board ends with "Close" and Storage has one beside its title. Settings opens whole and its switches are gold. A blade's sheet lists its numbers one to a line on a narrow phone ("Renown" no longer breaks mid-word).
- The Forge is a workbench. The blade being planned stands over the anvil with its name under it; the recipe is three places side by side (Weapon, Metal, Augment, each with its art and how many you own) and one tray of tiles opens for the place being filled. A material that has run out stays in the tray, apart and dimmed, with "Restock" beside it. Quick or Advanced is a toggle and Risk a compact choice; Advanced adds Catalyst and Technique as two more places. One gold button, "Forge · 2 energy", stays at the foot and says the one thing in its way ("Choose a metal", "No Bronze left", "Overwork: 1 less energy tomorrow"). Still three choices and one tap for a new recipe; nothing is chosen for you.
- "Field notes" sit right under the recipe: "Metal + augment" and "Augment + weapon" side by side, each with what the journal knows ("? Untried · Forge to learn", "Promising match · Understood") and "Open book" to the Journal. The two are never added into one score.
- The Forge no longer lists every customer want and request above the recipe. A request chosen with "Forge this" is one line by the workbench ("Xanthe's commission · 165 gold · Due in 3 days", what it asks with a tick on what the draft already matches, "Not accepted" until it is) with "Clear target".
- Commissions and customer wants are on one board, opened from "Commissions & customers" on the Shop and "Commissions" on the Forge. Commissions (accepted and soonest first, then offers) show what is asked, the reward, when it is due and "Offer", "Accepted", "Ready for End Day" or "Due today"; a tap opens the terms with Accept, Decline and Forge this. Customer wants are grouped by weapon type ("Sword · 3 customers") and open to each hero's own line. The Shop shows one row with the counts and whether an offer waits for an answer.
- End Day is the Shop's button. On the Forge it is under "⋯", and it stands beside the Forge button when no forge is possible today.
- The siege reads the same on the Shop, the Forge and in Town: when ("Siege in 4 days", "Siege approaching · tomorrow", "Siege today, after today's trading"), who and what tells against them ("Ashclaw Raiders · Weak to Frost · Resists Fire") and how the town stands ("Strong position", "Evenly matched", "Outmatched", "Grave danger"). Town leads with these and the forge's health; the numbers follow. On the day of a siege End Day says "A siege follows today's trading".
- A siege has its own card in the shop day: a taller stage named "Siege · day N", "The town held" or "The defenses broke" in the day's largest type, the forge damage the day recorded and the forge's health now as two separate lines, who stood on the wall, and "Watch the siege". This card waits for you at every playback speed. The evening card (and the blessing choice) starts with "Today's siege: The defenses broke · forge damage 31", so skipping the day still says what it cost.
- The forge result says what the forge taught, under the blade: "New observation" or "Pairing understood" with the pairing and what the journal now says of it ("Iron + Frost Bloom · Seems promising"), "Recipe clue earned" or "Signature discovered" for a recipe, "Another experiment recorded. Keep testing to understand this pairing." for a repeat, and "No new discovery. Your notebook already knows these pairings." when nothing is new. A result reopened after the app was closed shows no such line.
- After "List at" or Store the Forge still holds the same recipe, and the notice says so ("Your ingredients stay selected.").
- Records' Journal is a notebook with three tabs: Metal pairings, Weapon pairings and Recipe clues. Each pairing shows its two ingredients, its stage and what the journal may say; "Use" puts it on the workbench, and the pairing on the workbench now is marked. "Try an untried pairing" chooses a metal and an augment you own and have never forged together; it spends nothing and forges nothing.
- Field notes and the notebook colour a pairing by how well it is known: gold while untried, cream once observed, green once understood, always beside the word. An observed pairing that leans neither way reads "Seems neutral" (it was "Seems ordinary").
- The Forge's siege line opens Town.
- The Shop leads with its shelf: the day's lead is one row under the counter, each shelf row is the blade's sprite, name, power, any flaw and its price (the rest is in the blade's sheet), and "Yesterday at the counter" is one row that opens. On the day of a siege a row above the lead says "Siege today, after today's trading". The lead for an unanswered commission opens the board.
- Supplies is titled "Supplies" with "Metals", "Augments" and "Catalysts"; each material is one row ("Owned 3 · 20 gold · 2 left today"). "Restock" on the Forge opens it on the missing material, marked "Needed on the workbench".
- In Records, older Gazette days are archive rows: the day, a headline and "Show", with "⚔ Siege" or "† Death" on the days that had one.
- "Forge health 84" on the Forge and in Town (it was "Forge 84"). End Day's notes read "1 unaccepted commission" and "Overwork: 2 less energy tomorrow".
- The bottom bar icons are larger (about 31 to 40 dp, drawn at a whole-number multiple of their pixels so they stay crisp) and the bar is 72 dp; the Day, Gold and Energy icons in the top bar are larger too.
- Material icons on the Forge chips and in Supplies, and the forge-integrity shield under the banner, are larger and drawn at whole-number pixel multiples.
- Each destination keeps its place: the scroll position of Shop, Forge, Town and each Records segment, and the Forge's open step, are as they were left when you come back, also after a visit to the main menu. A new day opens each at its top. "Forge this" still takes you to the Forge and shows the step to choose next.
- Back closes what is open, then returns to the Shop, and from the Shop opens the main menu (it used to leave the game). During a shop day it steps back a card; on the first card and on the Resume prompt it opens the menu, and "Continue run" returns to the same card with the day still unwatched. Back on the blessing offer means "Decide later", and deciding later no longer sends you to the Shop.
- The Forge no longer scrolls by itself when you arrive, so the tip, Supplies and Journal at its top stay in view; a step scrolls into view when you open it or pick from it.
- End Day is on the Shop and the Forge only (Town and Records have its height back), stands further from the destination bar, and on the Forge is an outline beside the gold "Forge weapon". The Shop's lead button ("Go to the forge  ›", "Open storage  ›") is an outline with an arrow, not a second gold plate.
- The forge result shows the blade first: its sprite, name and rarity stand alone for a moment, then its numbers, buffs and recipe fade in under them. A tap on the card brings them at once; with reduced motion the card is whole at once. The price and both buttons are there from the start, so forging the same recipe again is still three taps.
- A blade fresh from the forge no longer shows "Renown Unsung (0)". The blade's sheet still shows renown, and the card shows it once it is earned.
- A customer who leaves without buying has a plainer card that leads with the recorded reason and its numbers ("38 gold short of the cheapest blade", then "Could pay up to 90 gold; the cheapest blade is 128 gold."). Each blade they looked at says what spoke for it and what against it in sentences ("For it: elemental, which they like. Against it: not their kind of weapon; 38 gold beyond their purse.") instead of a comma list.
- The till's total is "Earned today" (it was "Taken today") and its rows are named like the cards before it: "Requests", "Guild stipends", "Tribute from the town". A request's receipt has a "Request payment" row; a guild's share reads "Of that, paid by their guild" (it is part of the price, not on top of it), so a receipt's rows add up to its total.
- The opening card of a shop day says how many visitors came and how many of them are shown at the counter ("5 visitors today. 3 are shown at the counter; the other 2 are summed up after."), and the strip counts those ("Counter · 1 of 3"). It no longer repeats the day and "the shop is open", and the plate under the scene says what is left on the shelf instead of repeating the strip; the strip over a card from beyond the door reads "After closing".
- The last card of a day reads "Day 1" and "Evening" on the strip and "Tomorrow: day 2" on the banner, above "Begin day 2". Skip is explained one way: Skip day jumps to the evening, and the prompt after a restart offers "Skip to the evening" (it was "Skip to tomorrow"; it always landed on this card).
- The lead on a day's last card speaks of that day as "today" ("3 customers left over the price today"); the next morning's Shop still says "yesterday". A paid request's card says "Collected a requested blade" where it said "commission".
- A day with a bare shelf says how many looked in and shows their faces once, instead of listing the names twice.
- When the shelf is full, "List at" on the forge result and on the blade sheet is greyed and the reason stands beside it ("The shelf is full (8 of 8). ..."), instead of an error after the tap. Store still works.
- The forge result keeps its price and both buttons on screen while the blade's card scrolls above them, and above the keyboard while a price is typed (with text larger than 1.3 the card is one scrolling column). The blade sheet's price and its buttons also stay above the keyboard.
- "List at" on the forge result closes the card once the listing is saved; if the game refuses it, the card stays open under the reason.
- The blade sheet says where the blade is above its price ("On the shelf, asking 90 gold." or "In storage, not for sale."), and that line changes when it is listed or repriced.
- "Who is buying" counts an unspent guild stipend (Guild Patronage) toward what a hero can afford, as the counter itself does, so it always agrees with the new line under a price.
- Buttons look one way for each kind of action: the gold plate for the way forward, a bronze outline with gold lettering for every other button, and gold lettering alone for an action inside a line ("Read the Gazette", "Forge this", "Got it"). The Gazette's Close, "Alright" on a refusal, "Try again" and "Start over" on the save screens, Store, Back, Unlist, the sheets' Close, "Main menu", "Abandon run", the blessing choices and the upgrade buttons after a run all follow it; none is a rounded orange pill any more.
- The three pages of Records are tabs without a check mark: the chosen one is a lit bronze plate with gold lettering. The first is called "Gazette" (it was "News"), as every "Read the Gazette" link already said.
- One name for each thing. The sheet of materials and tools is "Supplies" on the Shop, on the Forge and in its own title (it was also "Supplier" and "Supplies and tools"). A request is a request everywhere the day is told: End Day's note ("A request is waiting for an answer"), the Gazette's summary line ("1 request delivered"). Town's list is "Heroes · 12 in town" (it was "Adventurers (12 alive)").
- During a shop day the playback control says what it does: "Manual" (each card waits for you), "Auto 1x", "Auto 2x" (it was "Tap", "1x", "2x"). The skip is "Skip to evening" on the strip, on the prompt after a restart and in the first-day hint, and all three say where it goes: the day's last card, where "Begin day N" still waits. In the Gazette, the button that shows a fight's remaining rounds is "Show all rounds" (it was "Skip").
- The Champions section says its rule: named at each End Day, the three strongest living heroes who are not wounded (health 50 or more). An empty place says why: none are named before the first End Day, and after it no other living hero was fit.
- Greyed buttons say why to a screen reader in more places: Buy in Supplies (none left today, or the gold it costs against the gold you have), an upgrade after a run (claim first, or the points it costs), and "Begin era" before the claim.

### Fixed
- A blade is called "Worn" below condition 50, the line the game itself uses when a hero looks to replace a blade and when a replacement is requested; the blade's sheet and Town used 70. "Battered" is the lower half of that band (below 25).
- A tap on the strip above the shop day (beside the playback control or the skip) no longer counts as "tap anywhere" and moves the day on.
- The first-day hint is not shown while a screen reader is exploring by touch, where a tap anywhere is not Next.
- During a shop day with text larger than 1.3 the painted scene is lower, so the card under it has room; on a low screen with such text the first-day hint is left out, where its three lines would leave the card none.
- On the strip above the shop day, the playback control and the skip move to their own line when they do not fit beside the day's label, instead of cutting the label short.

## [0.7.0] - 2026-10-10

### Added
- The first time a customer comes to the counter, one line under the card says how the day is watched ("Tap anywhere to continue · Skip day jumps to the evening"). It goes once you move on and does not come back.
- Storage can be narrowed and ordered: chips for the families and rarities that are in it, "Never sold" (blades no hero has carried), and As stored / Strongest / Weakest / Dearest. "Select blades" turns the rows into checkboxes, with "Select all shown"; the chosen blades can be salvaged or given to the town watch in one go. Each asks once and says what it costs; salvaging still costs energy per blade and stops when the day's energy and overwork are spent, and the watch stops taking blades when its armory is full.
- The Legend Board (Records, Legacy) tells each blade in full: its era, victories and fame, what was worked into it, who carried it and its story; an entry from before a blade's make was recorded says its properties are lost to time.
- A signature's row in the Journal shows its clue ladder (recipe, catalyst, temper, finish): each clue earned in the journal's words, each one still missing as "not yet known". A found signature has "Use this recipe", which fills the forge with its family, metals, catalyst and temper.
- A blade's sheet has a "Story": what is worth telling of it, oldest first (its forging, its first victory and first siege, sales, names earned, its return in a later era), above the full History. A legend that came back with its properties asleep shows them under a gold "◆ Dormant" line, apart from its buffs, on its card and on its shelf or storage row, with what wakes them.
- The Shop's counter plate and the Forge's plate say when the siege comes and what tells against the besieger ("Frost bites the Ashclaw Raiders; fire glances off them."), and the Shop says when the siege warning is out and buyers weigh it. An augment chip of an element the besieger is weak to or resists carries a "+" or "−" with the words under the chips, and a blade of such an element says so on its shelf or storage row.
- A request's card says why it was made (a noble's order, a replacement, a blade for the wall before the siege, a collector, a first blade for a newcomer), on the Shop and on the Forge; the Requests heading counts how many are open of the two that can be.
- "Who is buying" lists every hero who left without the blade they came for ("Wren Kestrel wants a bow; can spend about 90 gold."), with "Forge this" (the forge opens on that family) or a mark once a blade on the shelf answers it. The same line is on the hero's sheet and on the Forge, and when it is the day's lead the lead has the "Forge this" button.
- Main menu: the game opens on a menu with New game or Continue run, Abandon run (discards the run after a confirmation; no legacy points, legends or heroes are recorded, journal discoveries stay) and a Settings icon in the corner; Settings in the workshop has a "Main menu" entry to return to it.
- A save that cannot be opened no longer crashes the game. A recovery screen says what happened and what is safe, and
  offers Try again or, for a damaged or incompatible run, Start over (the unreadable run is kept as a backup on the
  device and your legacy stays).
- When a save fails while you play, a dialog says nothing has changed and offers Try again or Keep working.
- The Shop is one page that reads top to bottom as a plan for the day: the counter with the blades on the shelf and how
  many customers it seats, the one thing worth doing first with its reason and a button that goes there (the same lead
  the day before ended on), open requests with the blade that will be handed over or what is missing, "Who is buying"
  (heroes with no blade, with a worn blade, how many can afford your cheapest and your middle price, classes nothing on
  the shelf suits), yesterday at the counter, then the shelf. Storage opens as its own sheet and stays quick with
  hundreds of blades. Home and Market are gone.
- The closing till and yesterday's summary say how many found the shop full.
- "Forge this" on a request (on the Shop and on the Forge) opens the forge with the family asked for and an augment of
  the element asked for; the forge keeps the request in view beside the draft and says what the shop still lacks for it.
- The Forge shows the forge room above the draft, with forge integrity, the next siege and the besieger's weakness on
  it, and has Supplies and Journal buttons. The Forge button no longer has a fixed width.
- Supplies (the supplier and the workshop tools) is a sheet opened from the Shop or the Forge. A rare material says when
  Caravan Ties adds to its daily stock, when the ore merchant is in town and when the caravan is late.
- Town is one fast list; each adventurer's row names their guild, their mentor and whether they are a regular.
- Town rows are shorter so a town of twelve to sixteen can be scanned (traits, purse and ambition are in the hero's
  sheet), and the fallen and retired sit under a "Fallen and retired" header that opens them.
- The seven tabs are now four destinations: Shop, Forge, Town and Records (News, Journal
  and Legacy as three segments). The four labels share one fixed size and do not shrink or clip at larger text sizes.
  Back returns to Shop from any other destination.
- A settings sheet behind a gear in the top bar: reduced motion (moved here from the Legacy page) and the version.
- Haptic feedback: a short, distinct vibration when a blade is revealed (stronger for a signature or epic result), when you
  end the day, when a request is refused and when an era ends; the shop-day counter will use the same for a sale. A
  Haptics switch in settings (on by default) turns all of it off. The game asks for no vibration permission and has no
  sound.
- After End Day the day is shown card by card (the shop opening, each customer, the others who came by, closing, what
  happened beyond the door, then tomorrow) with Next, Back and Skip day.
  The day is saved before the first card shows and the place you reached is saved as you go: closing or killing the
  game mid-way offers "Resume the day" or "Skip to tomorrow" on the next launch, and watching, skipping or restarting
  never changes what happened. A blessing can be chosen inside the day or left for the morning. The Gazette opens over
  the day and closing it never begins the next one.
- The shop remembers its customers. A visit can now carry one line of recognition, chosen from what really happened: a
  first blade from your forge, the day someone became a regular, a regular's return, a blade still carried and its
  victories, a worn edge laid on the counter, a champion back from the wall, a kept vow, a mentor's old blade, a
  descendant of an earlier era, or the hero who could not get in yesterday. Each hero hears a milestone once per era,
  and other lines no more than every third day; the opening three days stay introductions, one line a day at most.
  The line is stored with the day, so it reads the same after a relaunch.
- Hero and blade sheets. Tap a hero in Town to see their face, class, health, element taste, traits, purse, whether
  they are a regular, ambition, guild, mentor, the blade they carry, their dealings with your shop and their recent
  days. Tap a blade on the shelf or in storage to see its rarity and quality in words, every property and flaw with what it
  does, its recipe, who holds it and its history, newest first; pricing, listing, salvage, hone and arming the watch
  now live on that sheet. The two sheets open each other (a hero's blade, a blade's holder, a hero's mentor).
- The shop-day screen: each featured customer comes to the
  counter as a framed portrait with a name plate, the shelf shows what they looked at and what left it, and a card says
  what they did and why with the recorded numbers. A sale lists its price, trade-in, bonus and coin to the till as
  separate rows. The other visitors are tallied with faces and names, the till closes by kind, up to three cards tell
  what happened beyond the door, and the day ends on one lead for tomorrow, the blessing choice or the fall of the
  forge. Nothing moves on by itself unless you pick 1x or 2x; a tap anywhere is Next and Skip day is one tap.
- Standing wants. A hero who is served and leaves with nothing now leaves a want behind: the kind of weapon they would
  have taken, how strong it has to be for them, and what they can spend. While a blade on your shelf answers it they
  come back more readily and are likelier to get a seat; the want lapses after three days, or when they buy anything.
  The day's lead can now be "Forge a bow for Wren Kestrel" with the numbers behind it, and a visit can be recognised as
  the answer to a want.
- The journal keeps answering. A hidden recipe now has four clues to earn: that the base recipe hides something more,
  what kind of catalyst it wants, the temper, and how fine the work must be. Every forge at the base recipe that does
  not take earns the next one (the journal used to speak once and fall silent), and the hint shows exactly the clues
  you hold, never a chance. Each catalyst has its own phrase: "wants something to bind it", "wants a word cut into
  it", "wants a hotter fire", "wants a rule rewritten". Clues stay with your legacy across eras.
- Rumours. A hero who slays an elite foe with one of your blades, or a patron collecting a commission, may bring word
  of a recipe you have not found: one clue for it, a few times an era.
- Requests have reasons. A commission now comes from what is happening in town: a hero whose blade is worn or gone
  wants a replacement; a champion asks, in the days before a siege, for a blade of the element the besieger fears,
  due on the siege day; a collector asks for fine work; a guild member orders a first blade for a newcomer who carries
  nothing, and the newcomer is the one who walks out with it. Ordinary and noble requests remain. The request says why.
- Two requests can be open at once (it was one), never two from the same hero.
- The Forge line of the Gazette says what was spent on materials.
- A commission card lists each blade of the right kind in the shop with "fits" or the one thing it lacks (wrong
  element, or its quality against the quality needed), and an accepted one names the blade that will be handed over at
  End Day. The Shop shows the same line.

### Changed
- Long runs keep a smaller save. A blade's story keeps what it is, how it was lost, its first owner and its newest
  24 everyday lines (sales, trade-ins, hones, hand-overs); the Records pages keep arrivals and world events for 30 days,
  as they keep other daily news; a request that was delivered, declined or lapsed more than 30 days ago is no longer
  stored. Deaths, retirements, sieges, milestones, legends and everything in storage are kept as before. No outcome changes.
- A legend that returns is the blade it was. It keeps its signature, its flaws, its catalyst, its title and its story
  (who bought it, who inherited it, the patron it was made for, the day it first held the wall). Its properties come
  back dormant: they do nothing until you hone the blade once, and then they wake, with the strength they carry. A
  returned blade's name never promises a property the record cannot back, and a legend from before this update, whose
  make was never written down, says "properties lost to time". A returned legend that nobody carries is not put back
  on the Legend Board, and one that is carried again takes the place of its old entry instead of adding a second.
- Shorter weapon names. A new blade carries at most one property in its name ("Flaming Iron Sword", not "Flaming Keen
  Reinforced Iron Sword"); the rest are on its sheet. When a blade earns a title the title takes that place ("Iron
  Sword, Slayer of the Cinder Matriarch") and the day it earned it is written into its history. Blades you already
  own keep their names.
- Catalysts say what they do. All four steady the forge in the same way today; their descriptions now say so, and name
  what a recipe asks for when it asks for that one. (A description used to promise that Runestone Shard "guides an
  affix", which it never did.) The strange weapon fragment now gives the first clue of a recipe instead of a general hint.
- Customers weigh a blade the way it fights. What a blade is worth to a buyer now counts its properties and its fame as
  well as power, wear and class fit, on the blade in hand and on the one on your shelf alike, and a small gain is a gain
  (it used to be rounded away). A blade as good as their own, give or take a little, can be bought once for something
  theirs lacks: their favoured element, a collector's prize, or a name.
- The town arms for the siege. On the eve of a siege and on the siege day, a blade of the element the besieger fears is
  wanted (it counts for more in the hand and sells with its own reason), the town's champions come to the shop more
  readily, and a blade of the element the besieger shrugs off that would have sold on a calm day stays on the shelf,
  and the visit says so. Stronger blades of a resisted element still sell.
- A new look: one dark forge theme in both system modes, bronze-framed panels with gold headings and a gold primary button, and blades shown as an item card with power, quality and condition bars, buffs ("+") and flaws ("−"); a fresh forge is revealed as that card.
- The planning screens wear the same look: the Shop's lead, requests, shelf and storage rows, the Forge's anvil plate, Supplies, Town and Records sit in bronze-framed panels under gold headings; End Day and Forge weapon are gold buttons (Forge weapon now runs the width of the plate), other actions are bronze outlines; the top bar and the destination bar are dark panels with a gold mark on the chosen destination.
- A blade's row on the shelf and in storage shows its name in the rarity's colour and one line of the item card's numbers (power, quality, condition) with each buff ("+") and flaw ("−") by name. With large text the quick "List at" button moves under the blade.
- The app no longer flashes a light screen while it starts.
- The walls can cost a champion. When a siege is lost badly (the raid at one and a half times the town's defense or
  more) it is a rout: the champions take a heavier wound, and one who went up barely recovered can fall there. The
  blade they carried is recovered by comrades more often than on the road, or seized, or lost. A narrow loss still only
  wounds, and a champion at full health survives any siege. The paper says "routed" when it happens.
- Guild Patronage does what its name says. For its five days every member of a guild wants to come to the shop, and the
  guild pays 30 gold toward one blade for each of them: the coin counts toward what the member can afford, shows on the
  sale as the guild's share and in the day's takings as its own line. The town does not offer it while no guild stands.
  (It used to be a small nudge to everyone's wish to visit, which a full shop swallowed.)
- A bigger town. An era now opens with twelve heroes instead of eight, and every class is among them from the first
  morning. When the town thins, newcomers drift in one a day until it is back at twelve (always, once it drops below
  nine); events can still swell it to sixteen.
- A busier counter. The shop seats six customers a day instead of four (seven or eight with the Signboard), and a
  festival brings three more. Expect five or six faces on an ordinary day and almost never an empty counter.
- The raiders keep pace with the larger town: a won expedition pushes a faction back a little less, and siege strength
  grows a little faster by the day. A first era lasts about as long as before; prices, purses and weapon wear are
  unchanged. A run saved before this update continues with the heroes it has and fills up as newcomers arrive.
- Every permanent upgrade now says what its next level does in concrete numbers ("Next era: 11 starting energy instead of 10"), on
  the run-end screen and in Records > Legacy; a maxed track says so. Thrifty Hands and Lucky Hammer no longer describe their
  effect as a percent chance.
- New hero portraits: twenty heroes as framed tiles (five guardians, four rangers, duelists and battlemages, three
  wardens), each with a second, more decorated look that a hero wears after five victories. Every hero, old saves
  included, keeps one face of their class from the new set.
- A hero's face is now stored with the hero and spread evenly over the faces of their class: a newcomer takes a face
  nobody living in their class wears while one is free (before, two heroes of a class looked alike on most days). Heroes
  in an existing save keep the face they have, and a descendant takes their ancestor's face when it is free.
- After you claim a fallen era's legacy, the run-end screen survives closing the game: it reopens claimed, with your
  points and upgrades still there to spend, until you begin the next era.
- The Back button never begins the next day: in the day's cards it steps back one card, and only the last card's
  "Begin day" button moves on.
- The forge panel you were on, the recipe you were drafting and an open forge result come back if the system closes
  the game in the background.
- Android's automatic cloud backup of the save is off until restoring one is tested.
- Yesterday's customers and the shelf on the Shop now also name the patron who collected a
  commission and the collector who bought a storied blade.
- Every living hero now gets an equal turn at the counter. Each hero decides for themselves whether to come, the seats
  are drawn among those who came, and nobody who keeps coming is turned away three days running. Before, the heroes
  who arrived in town first were always served first and the eighth was served less than half as often. Regulars,
  newcomers, heroes without a blade and heroes whose blade is worn are a little more likely to get a seat; the first
  seats go to different classes. A patron who collects a commission does not also browse that day. Seats, town size
  and prices are unchanged. A run in progress continues under the new rule (balance 7, rules 3, save schema 4).
- Heroes are named from 120 first names and 96 surnames (before: 30 and 24). No two living heroes share a first name
  or a surname, and no full name is given twice in a run; a descendant keeps the family surname and never takes the
  ancestor's first name. Before, two heroes of one first name stood in town on two days of three. Heroes already in a
  saved run keep the names they have (content 3).
- A descendant is recognised by the lineage itself, not by the ancestor's name: two famous heroes of one name in
  different eras are two lineages, and each can send a descendant. The siege scene finds its champions the same way.
- Blades and heroes are ordered by their number in every rule of the game (the tenth after the ninth): which lost blade is
  carried home, a retiring hero's hand-down, the guild-hall mentor, the collector's choice and the hero a lineage is
  founded on.
- A collector pays at most one and a half times the going rate for a blade, whatever its shelf price.
- Commissions say exactly what they need. A request is always for a quality the game names (decent 35+, fine 50+; a
  noble patron asks for superb 70+, no longer "masterwork") and is written that way everywhere: "Fine frost Spear
  (quality 50+)". A request left open in an older save is lowered to the quality its word promised.
- An accepted commission is collected before the day's browsers arrive, so nobody buys the promised blade first. The
  patron takes the least blade that fits (from storage before the shelf, then the lowest quality), not your best work.
- A hero who dreams of a fine blade (the collector ambition) is satisfied by quality 50, the start of "fine"; it was 60.
- Balance version 6.

### Fixed
- Town names the same besieger as the Shop, the Forge and the siege itself when two factions press equally hard.
- A save whose legacy record went missing beside a sound run reads the legacy the run carries, instead of starting from an empty one.
- An adventurer whose only blade shattered more than thirty days ago can still ask for a replacement: the broken blade
  stays on record until they carry another.
- The counter still recalls that a customer held the wall with the blade they carry after ten later fights with it: a
  blade's story keeps its carrier's last siege line beside its newest ten fights.
- A day the counter cannot lay out no longer stops the game from opening: that day counts as watched, the Shop opens on
  the next morning and the Gazette still has the day.
- A past day in the Gazette is tallied as its report was: visitors, shop takings and expeditions lost (a hero who died
  out there included) no longer change or vanish once the next day is played. Applies to days played from this version.
- Two quick taps on the run-end screen (two upgrades, or an upgrade and Begin era) can no longer lose a purchase or
  write over the new era.
- A damaged legacy record no longer costs a readable run: the recovery screen offers to rebuild the legacy from the
  copy the run carries, sets only the damaged record aside, and the run goes on. When points, upgrades and legends
  really would be lost, the screen now says so before you confirm.
- A damaged save file is no longer deleted silently on launch. The game says the file is damaged, leaves it untouched,
  and Start over keeps it on the device under a backup name before beginning a new save.
- Storage errors of any kind now reach the recovery screen or the "Could not save" dialog instead of closing the game,
  and that dialog no longer claims nothing changed when the game could not check.
- The three champions are chosen against the foe they will face: when a warlord leads the siege, a blade that bites
  deepest into warlords (Giant Slayer) counts at its full worth in deciding who stands on the wall, the same worth the
  siege forecast already gave it. The champions shown in town are now always the ones the forecast names.
- The Gazette's tally counts only visitors who bought (no more "2 of 1 visitors bought"), shows a delivered commission
  and the town's tribute on their own lines, and counts the sale bonus and a collector's payment in the shop's takings.
- Saved records no longer contain numbers formatted for the device language.
- The traveling ore merchant's extra stock (two more of the material named in the Gazette) is on sale at the supplier
  the next morning. It used to be wiped by the morning restock before it could be bought.
- When two factions press the town equally hard, which one besieges it is decided the same way everywhere (forecast,
  warning, siege and the champions shown in town).
- The day's report now tells the whole day, including the blades you forged, listed or honed and the tools you bought
  while planning; before, those lines appeared only in the Gazette archive.

## [0.6.0] - 2026-10-09
Heroes with lives of their own, fight replays, the fates of fallen blades and three new legacy tracks (balance v5).

### Added
- The day's report reopens if the app is closed or killed before it was read, and stays closed once dismissed.
- Guild halls: heroes now spend some of their days training at a guild hall. A day there teaches a little and mends a
  little, and pays nothing. When a higher-level guildmate trains the same day, the lower-level hero is taught by them
  (once a day) and the mentor's name stays on their record. Patient, loyal and curious heroes go most often; greedy
  and restless ones rarely.
- Guilds form during a run, not only when a hero retires: the first hero with a name (fame 3) can found the town's
  company, and others join it by training there.
- Heroes pursue their ambitions on purpose, until the ambition is fulfilled: a slayer hunts an elite foe, a sworn
  defender drills the town watch (more militia than a patrol, nothing else), a collector or a fortune seeker takes paid
  guard work for gold.
- The Gazette tells these days: one shared "At the guild hall:" line beside "On the walls:" and "Resting:", and a
  founding, a joining, a lesson or an ambition day in the hero's own sentence.
- Notable fights are told round by round in the day report. An expedition against an elite foe, won or lost, and any
  expedition a hero does not come back from get a short text replay under "From the field", titled with the hero and
  the foe and folded behind an "N rounds" button like the siege's. At most three a day, the gravest first; the siege
  still comes first and alone has the diorama. Skipping them changes nothing.
- A fallen hero's blade has more ways to go. Comrades may still bring it back to the forge and the enemy may still
  seize it; now it may also be gone from the field and turn up two days later with a travelling merchant, who offers
  it for three days to the hero who wants it most and can pay in full (the gold goes to the merchant, not the shop)
  and otherwise leaves Emberfall with it for good. A storied blade is likelier to surface, never certain to. The
  Gazette tells each step: gone from the field, the merchant's arrival, the sale or the departure.
- A living guildmate may inherit a fallen member's blade instead of the forge, wielding it only if it beats their
  own.
- Three new permanent upgrades on the run-end screen and in the Legacy panel, three levels each for 8 / 20 / 45 legacy
  points, one for each upgrade category the design document names that had none:
  - **Caravan Ties** (catalog access): the supplier keeps one more of every rare material in stock each day, per
    level, so a smith with gold can forge the same rare recipe several times a day.
  - **Anvil Lore** (recipe odds): a correct signature recipe transforms more often with each level, still under the
    same ceiling, so a signature is never certain.
  - **Homing Steel** (legacy artifacts): blades on the Legend Board return more often (still at most once a run) and
    less dormant, never whole. With an empty Legend Board it does nothing.
- Headless simulator: the upgrade impact table is followed by a second table of what each upgrade buys besides days
  (town defense at the first siege and how often it held, weapons forged and sold and tools owned by then, the first
  sale of a tier-4+ weapon, rare supplier units bought, signature weapons, legends returned). New flags:
  `--upgrades id=level,...` (play the policy rows with that legacy account), `--yardsticks` (the same table for the
  policy rows), `--legends` (give the maxed and impact runs a veteran's Legend Board) and `--knownNameGold N`.

### Changed
- The Painted Signboard now lets one more customer into the shop each day per level (it used to nudge the chance
  of a visit, which the daily customer limit swallowed). An active smith sells about four more weapons a run.
- Affixes and flaws bite harder: Giant Slayer hits elites and warlords for x1.5 (was x1.25); Reinforced and Swift
  cut the wound of a lost fight to x0.7 and x0.6 (were x0.85 and x0.8); Heavy raises it to x1.5 (was x1.2); Cursed
  and Bloodbound cost their wielder 15 and 20 health per win (were 4 and 7, still never lethal). A Lucky blade now
  brings back what elites carry (a catalyst or a rare core) instead of a common material.
- Long runs keep smaller saves: a blade that was salvaged, shattered, given to the watch or sold to a collector
  leaves the save 30 days later, unless it is a signature weapon or famous enough for the Legend Board. Play is
  unchanged (the same seed gives the same run); a 400-day save of an active smith shrinks by about a third.
- An unfulfilled ambition no longer nudges a hero toward ordinary expeditions or patrols; it is a kind of day of its own.
- A hero driven back from an expedition yesterday is more likely to rest or go to the hall today, and an unarmed hero
  with little gold is more likely to take the town's patrol pay.
- Simulator: the report shows how heroes spend their days (share of hero-days per activity, with wounded rest apart),
  level-ups, lessons at the hall and guilds per run.
- Where and how a hero fell now sets the odds for the blade: an elite foe keeps its trophy more often (seized 42 %
  of the time, was 25 %) and gives it up less often (recovered 40 %, was 50 %); a fall on the road is unchanged. A
  fall on the walls would favour recovery (70 %), but no champion can die on the walls with the current siege numbers.
- Simulator: per-run counters for the fate of fallen heroes' blades (recovered, inherited, resold, seized, lost) and
  an artifact recovery rate, in the text and JSON reports; `--noFates` replays the earlier rules for comparison.
- Known Name: besides its starting reputation, one starting hero per level is now already a regular of the shop and
  arrives with coin saved for a blade; the first day's news names them. On its own, starting reputation made runs
  slightly shorter in the simulator; with the regulars the first siege is held more often.

## [0.5.0] - 2026-10-09
Home dashboard, a readable Gazette, weapon wear and weapon fame (balance v4).

### Added
- Home panel: the first tab and the panel the workshop opens on (and returns to after the day report or a deferred
  blessing). One block per concern, each tapping through to the panel where the action happens: today's day, era,
  energy, gold, forge integrity and reputation; the next siege with the Town outlook and armory; the shelf with
  yesterday's visitors and why they left; open commissions with reward and deadline; the champions, their weapons
  and the wounded; yesterday's lede and tally; a pending blessing; and the materials that have run out. A siege within two
  days or against the odds, then commissions waiting for an answer, come first.
- Weapon fame now matters, within limits: a storied blade fights a little better (up to +5 % at ten fame), heroes
  want it more on the shelf (collectors most of all), its suggested price carries a small premium (up to +5 %), a
  legend returning from an earlier era keeps its fame, and the shelf line says "storied", "famed" or "renowned".
- Weapon wear: a hero's blade loses condition in every expedition (6 on a win, 10 on a rout) and every siege it
  defends (15). Worn power counts in battle (down to three quarters of the weapon's power at condition 0), at the
  shelf (a hero weighs listings against the worn power of their own weapon and is keen to replace one below half
  condition) and in the shop's suggested price and trade-in credit. The weapon summary and the Town hero lines say
  "worn" or "battered"; the day report names a replacement purchase ("their own blade was worn out"). Older saves
  load with every blade keen.

### Changed
- The end-of-day report and the Gazette archive are laid out as an edition: the biggest news as the lede, a one-line
  tally (gold taken, visitors who bought, expeditions won and lost), then Shop (sales, commissions, who left and why),
  Heroes (one sentence per hero, the quiet ones on one line), Town and Forge (the smith at the anvil in one line, then
  journal discoveries). Milestones already told by the news fold away; siege rounds fold behind the outcome. The
  archive opens the latest day and shows older days as a lede until tapped.
- Simulator: catalog sweeps (`--noTool`, `--toolCost`, `--noAffixEffect`), `--noImpact`, and per-run counters for
  elites slain, shattered weapons, warlord sieges, tool purchases and affix occurrence; the 10,000-seed v3 review and
  the per-tool / per-affix sweeps are recorded in `docs/DECISIONS.md` (recommendations only, no balance change).
- Hone always restores a weapon's condition to full; the +6 quality bonus still applies once per weapon, so a worn
  trade-in can be re-honed ("Re-hone") for the same energy and core.
- Headless simulator: `BALANCED_ACTIVE` also re-hones worn trade-ins; the visits report gains a `WORN_OUT` outcome.
- Balance v4: the Master Whetstone costs 120/300 (was 200/500), so a thrifty smith takes it before the signboard;
  a faction's warlord leads the siege from pressure 50 (was 70) and no longer strengthens the raid (the pressure
  already does), so a warlord is seen in about half of first-era runs instead of one in fifty. Balance config version 4.

## [0.4.0] - 2026-10-09
Gameplay depth (balance v3): shop actions, workshop tools, elite foes and warlords, hero ambitions, trade-ins.

### Added
- Shop actions on finished weapons: Salvage (1 energy, returns the core material), Hone (2 energy and one unit of the
  core for +6 quality, once per weapon) and Arm the watch (a fifth of the weapon's power joins the town's defense, up
  to 30; half wears away each siege).
- Workshop tools bought with gold, lasting for the run: Great Bellows (+1 daily energy), Master Whetstone (+3 forged
  quality), Painted Signboard (more visitors), Display Case (+2 shelf slots).
- Elite foes on expeditions (stronger, richer, a weapon title and catalysts for the victor) and named warlords who
  lead a siege when a faction's pressure reaches 70; beating one pays the smith 120 gold in tribute.
- Hero ambitions: every hero pursues a slayer's vow, a defender's oath, a prized weapon or a fortune; ambitions tilt
  what heroes do and buy, and fulfilling one makes the Gazette and raises the shop's reputation.
- Commissions can ask for an element (the patron's taste or what the looming faction fears) and pay half again more.
- Siege outlook in Town: the town's defense against the expected raid as things stand, the warlord if one leads, and
  the armory.
- Trade-ins: a hero replacing a weapon hands the old one back to the shop as part payment (40 % of its fair price);
  it returns to storage to be resold, honed, salvaged or given to the watch. The town also pays heroes for patrols.
  Heroes can afford far more of what is forged (sales up about a third).
- Simulator policy `BALANCED_ACTIVE`, which uses the new shop actions, and shop visits per run by outcome in the
  report.

### Changed
- Affixes do more than add power: Undead Bane bites only the Hollowbound, Giant Slayer only elites and warlords,
  Vampiric mends its wielder, Lucky finds more loot, Swift and Reinforced soften a rout, Heavy worsens it, Brittle
  weapons can shatter, Cursed and Bloodbound weapons hurt the hand that wins with them.
- Siege warnings name the warlord and the element the attackers fear.
- Raids grow by 6 power a day instead of 5, offsetting the better-armed heroes (balance v3).
- Legacy base points 5 -> 6, so even the shortest first run affords the cheapest upgrade (balance v3).
- Run-end screen: the Begin era action stays pinned under the scrolling summary instead of sitting below the
  eight upgrade cards.

## [0.3.0] - 2026-10-09
Reputation and loyalty economy, bounded weapon histories, simulator purchasing and pricing policies, UI wave 2.

### Added
- Shop reputation and hero loyalty now raise the price heroes treat as fair (bounded at +25 % each), regulars return
  with commission requests, and the Gazette records premium sales and names the shop's regulars.

### Changed
- Weapon histories are bounded at End Day: the newest 10 combat entries (victories, sieges) per weapon are kept,
  ownership entries (forged, sold, commissioned, inherited, lost, seized, recovered, returned) forever; gameplay,
  legends and the Gazette are unchanged, and existing saves compact on their next End Day (save schema still v1).
- Headless simulator: new `BALANCED_INVEST` policy (buys the best core and augment within `gold - reserve`,
  `--reserve N`) and `BALANCED_REPUTED` policy (lists at the reputation-raised fair price), `--impactPolicy` also
  drives the maxed-legacy run, and `--json` reports the reserve; 10,000-seed review of every policy recorded in
  DECISIONS.md.
- Title and run-end screens on the spacing tokens with one primary action at a time (Continue leads when a run is
  saved; Claim, then Begin era); Town lists every faction under the leader with its pressure and weakness; the Forge
  threat line names the leading faction only; recipe steps scroll so a tall step shows its header first; disabled
  Forge/upgrade buttons and the reduced-motion row carry screen-reader descriptions.

## [0.2.0] - 2026-10-08
Launch content by default, balance v2, all 24 signatures and 25 world events, bounded saves, decluttered UI.

### Added
- The remaining 12 signature recipes (spear, dagger, staff) for launch content, so all 24 exist; every core,
  element and catalyst now appears in at least one recipe.
- World event "Strange Weapon Fragment": reveals one unknown signature recipe in the journal (observed, with its
  descriptive wants) and leaves one of its core and augment at the forge; the 25th scripted event.

### Changed
- Workshop UI decluttered: a three-stat top bar (day, gold, energy); forge integrity and the siege countdown live on
  the Forge panel's threat line and the Town header; one End Day button with a contextual sublabel; a 64dp nav bar.
  The Forge panel pins the weapon preview, recipe, cost and Forge button above collapsible steps (mode, family,
  core, augment, catalyst/technique when advanced, risk) that auto-advance; out-of-stock materials are disabled with
  the engine's own reason. Market lists shelf weapons as rows with a tap-to-edit price and folds empty slots into
  one line; commissions and the supplier (grouped by category) are separate sections. Tips are one slim banner per
  panel, dismissed once. The day report is a full-width paper sheet (diorama, headlines, replay, one action).
  Typography collapsed to four working styles with spacing tokens (`ui/theme/Spacing.kt`); muted ink darkened.
- Launch content is the engine default (`GameEngine()` now builds on `LaunchContent`; `SliceContent` stays for
  slice-specific tests and `--content slice` in the simulator).
- Balance v2: quality formula `25 + 6*coreTier + 3*augmentTier + ...` (was `35 + 4*coreTier`), so the six launch core
  tiers spread common -> epic instead of saturating; siege modifier 2.0 (was 2.75) with forge damage
  `24 + 50*(ratio-1)` (was `12 + 25*(ratio-1)`); launch faction growth 4/3/2 per day (was 6/5/4). Heroes now act
  against the most pressing faction instead of the first by ID.
- Simulator: `--content launch|slice`, `--rarityTable [N]` (rarity per core x augment x risk through the real forge
  path), `--impactPolicy`, forge-damage overrides; the RANDOM policy only picks materials it can afford.
- Saves stay bounded over long runs: End Day now compacts the event log, keeping full records for the last
  30 days (`BalanceConfig.eventRetentionDays`) and history-grade events (sieges, deaths, retirements, guilds,
  signatures, milestones, world events, weapon fates) for the whole run. Gameplay, Gazette headlines for the
  window and the save schema are unchanged; older Gazette days show only the retained headlines.
- Weapons are drawn from the artist's weapon master sheet: every family has an element row and eight visual levels
  (core tier plus epic/legendary bonus); the forge preview shows the plain base weapon until an augment is chosen.
  The old per-core recolours and element auras are gone.

## [0.1.0] - 2026-10-08
First tracked build (vertical slice content, launch content data-complete but not default).

### Added
- `:core` deterministic engine: SplitMix64 per-subsystem RNG, immutable `GameState`, typed commands and errors,
  fixed End Day order, invariants, versioned `BalanceConfig` (v1), versioned save codec with migration scaffold.
- Content: `SliceContent` (default) and `LaunchContent` (6 families, 16 materials, 5 classes, 3 factions,
  12 affixes, 6 flaws, 8 blessings, 8 upgrades), 12 signature recipes, techniques (temper, quench, etch),
  23 world events, retirements, guilds, mentoring, seizure and inheritance.
- Headless simulator with the GDD policy set, upgrade-impact sweep, perf timing and JSON reports.
- `:app` Compose UI: title, workshop (forge, market, town, gazette, journal, legacy), day report with stepped
  battle replay and animated siege diorama, run-end screen, settings (reduced motion), Room save with atomic writes.
- Pixel art pipeline: concept sheets, loose PNGs and the artist's 1x pack merged by `import_assets.py`;
  placeholders by `generate_assets.py`; 194 hand-made sprites in use.
- Tests: core JVM suite, instrumented save/UI tests, scripted emulator smoke loop.
