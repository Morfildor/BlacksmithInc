# Writing guide and glossary (player-facing English)

The game's text is read by adults, many of whom read English as a second language. Every description, report and
choice must be understood on the first read. The guild keeps its personality: a friendly, experienced guildmaster
with occasional dry humour. This file is the reference for every string a player can see. The clarity pass that
introduced it is recorded at the end (coverage and open items).

## 1. Style

- Natural, complete sentences in familiar words. One main idea per sentence, without making the prose choppy.
- Name the actor and the target whenever a pronoun could point at two people ("the wielder", "that enemy",
  "the Guardian"). "It" for a weapon only when no fighter is in the same sentence.
- Explain mechanics literally: "deals 5 damage", "applies 1 Burn", "consumes the Mark", "triggers", "gains 2 Guard".
- Numbers as numerals, shares as percentages: "+10% strike damage", not "a tenth harder". "Half" is fine when it is
  exactly 50% and reads better ("deals half of the blocked damage").
- Conditions, timing, duration, costs and limits are explicit: "Once per round.", "Once per fight.", "until day 12",
  "at the end of each round", "before the wielder acts".
- No em or en dashes, no semicolons between clauses (`ProseGuardTest` enforces both), no stacked " · " separators in
  sentences (a separator is fine between two short facts on a stat line), no cryptic fragments.
- "Weapon", never a generic "blade" (`ProseGuardTest`). "Blade" only for a weapon that has one, in flavour text.
- Remove repetition and filler, never information a decision needs. Keep text that already works.
- Personality lives in dialogue, visitors, flavour lines and names. Instructions, warnings and numbers stay direct.

### Rules and flavour

An ability or item effect reads as: **When [trigger], [actor] does [effect] to [target]. [Limit or duration].**
This is a clarity check, not a template. Leave out a field that does not apply. Take numbers from the data.

> When an enemy breaks the wielder's Guard, the weapon deals 5 damage to that enemy. Once per round.

Flavour stays separate (an item's `flavor`, a visitor's first line) and never carries a rule.

### Quests, choices and reports

- A quest or a visitor choice: the situation in one or two sentences, then objective, reward, cost, timing and risk as
  separate rows. Departure and return days are explicit. An irreversible result or a missed siege is shown before
  the player confirms.
- Action labels say what the player chooses: "Sell the sword for 120 gold", "Send the party", not "OK".
- Daily and combat reports lead with the outcome, then losses, gains and what the player must do. Secondary events
  and the full combat log stay behind the existing disclosures. A fact appears once: not in the heading, the summary
  and the details.

## 2. Glossary

Player-facing name, then its plain meaning as the code implements it. IDs, enums and save keys keep their old names
(a guild quest is still `Mission`/`contract` in code).

### The shop

| Term | Meaning |
|---|---|
| Energy | 10 points a day for forging and other work. Working past 0 is overwork: you start the next day exhausted, with less energy. |
| End Day | Closes the shop for the day. Customers buy, heroes fight, the night's events happen, and the next morning begins. |
| Commission | A paid order from a customer at the forge: a weapon of a given type and minimum quality by a deadline. Never used for guild work. |
| Customer wants | What customers in town are looking for today. Not a promise to buy. |
| Quality | A weapon's craft score from 0 to 100, also shown as a band word (crude, decent, fine, superb, masterwork). |
| Rarity | Common, Uncommon, Rare, Epic, Legendary. Legendary needs Heartfire or great deeds. Epic needs a fine metal. |
| Property | A named bonus rolled on a weapon (Flaming, Swift), code: affix. Shown under "Buffs". A **flaw** is a negative property (Brittle, Heavy). "Trait" is kept for guild members. |
| Condition | A weapon's wear from 0 to 100. Fights lower it. |
| Upgrade, Repair | One forge action (code: hone, 2 energy and 1 of the weapon's metal). The first time it is an **upgrade**: +6 quality (at most 100) and condition back to 100. Every later time it is a **repair**: condition back to 100 only. It also restores a returned legend's dormant properties. |
| Heartfire | A rare resource that lets a forge reach Legendary rarity. |
| Recipe clue / pairing | Notebook knowledge. A pairing is two ingredients tried together. A clue points toward a signature weapon. |
| Signature weapon | A named weapon with a fixed recipe, revealed rung by rung through clues. |
| Forge health | The forge's integrity (the GDD's term). A lost siege damages it. At 0 the run is over. Player text says "forge health". |
| Siege | A major attack on the town on a known day. Three defenders stand on the wall. |
| Defenders | The three heroes chosen for the wall: automatically the strongest available heroes. In a guild run you can reserve members for it. |
| Militia, watch, armory | The town's own defence. They weaken the attackers before the defenders fight. |
| Faction pressure | How strong a faction is growing. The faction with the most pressure is likely to lead the next siege. |
| Era, legacy | A run is an era. Legacy (knowledge, upgrades, legends, lineages) carries over to the next era. Claim it once per run. |
| Gazette | The daily newspaper: what happened in town yesterday, built from the real event record. |
| Visitor | A morning encounter at the shop that offers a choice. |
| Workshop relic | A relic kept at the forge. Some also give the guild's parties a rule in every fight. |

### The guild

| Term | Meaning |
|---|---|
| Guild run, charter | A run started with a charter. The charter gives an advantage and a constraint. The charter warlord leads the first siege on or after day 20. |
| Member | A hero who signed with the guild. Members are not autonomous customers. |
| Recruit | Sign a candidate for a fee. A member keeps a share (usually 10%) of the gold of every quest they win. |
| Loan | Lend a weapon from storage to a member. It comes back when the loan ends. |
| Reserve | Keep a member at home for the wall. A reserved member cannot be sent on a quest. |
| Quest | Work from the quest board for a party (code: contract, mission). Has a fee, a length in days, one or more stages and a reward. |
| Quest board | The list of quests on offer today, each with the day it leaves the board. |
| Party | 1 to 3 members sent on a quest, acting in the order you picked them. |
| Stage | One fight of a quest. A quest of 2 days has 2 stages. The reward of a won stage is secured at once and is never taken back. |
| Leaves / returns | A party leaves at End Day of the day you plan it. It fights one stage at End Day of each day away, and it is home on the morning after the last one. |
| Danger | Safe: nobody dies or is captured. Dangerous: if the whole party goes down, 1 member is captured. Lethal: if the whole party goes down, 1 member dies and 1 is captured. |
| Retreat rule (Cautious, Balanced, Reckless) | When the party retreats from a fight (code: posture). A retreat keeps the members alive but wins no reward. See section 3. |
| Checkpoint | After the first stage of a relic delve, the party waits for your choice: go deeper or return. If you choose nothing, it returns. |
| Injured | A member who went down. Out for 2 days. |
| Exhausted | A member who came home with less than 35 health. Out for 1 day. |
| Scarred | A member freed from captivity. For 5 days they enter fights with at most 75% of their health. |
| Captured | Held by the enemy. A rescue quest appears. If nobody rescues them by the deadline, they die and the enemy keeps any weapon they carried. |
| Trait | A member's personal rule in a fight (Protective, Greedy). |
| Speciality | A permanent improvement a member earns through deeds. |
| Preparation | A siege plan the attackers make (a shield wall, a second drake). A sabotage quest can spoil it. |
| Law of the season | A rule that holds for a few days in every quest fight, not at the siege (Storm front, Eclipse). |
| Rank | After the charter is secured: a harder level for more legacy points. |
| Rival, nemesis | A rival company that takes quests off the board. A nemesis is an enemy who carries one of your weapons. |

### Fights

| Term | Meaning |
|---|---|
| Round | The party acts in its order, then the enemies act in theirs. A fight lasts at most 8 rounds. If neither side has won by then, the party breaks off and it counts as a retreat. |
| Health | Damage lowers it. At 0 a fighter is down and takes no more turns. |
| Strike | A fighter's attack damage. A weapon type changes it (Axe 120%, Dagger 80%). |
| Support | How much Guard or healing a fighter's own move gives (Guardian, Warden). |
| Damage | Lowers health. Guard blocks it first unless the text says it ignores Guard. |
| Direct hit | Damage from a move or an effect. Burn and Bleed damage are not direct hits. |
| Front enemy, back enemy | The first and the last standing enemy in the enemy line. |
| Attack target | The party attacks a marked enemy first, else the front enemy. Enemies attack a Guardian who has Guard, else the front member. |
| Priority target | The enemy summoner (Gravecaller, Hollow King) first, else the enemy leader, else the enemy with the most health. |
| Weakest ally | The ally with the lowest health percentage. |
| Wielder | The hero holding the weapon. |
| Element | Fire, Frost, Storm, Grave, Verdant or Sun. A weapon's strikes deal its element's damage. |
| Weak to / resists | A direct hit of the element an enemy is weak to deals 30% more. One it resists deals 30% less. |
| Status effect | A stack on a fighter, listed below. Every status ends with the fight. |
| Once per round / per fight | The most often an effect can trigger for its holder. A party rule marked "for each member" counts per member. |

### Status effects

| Status | Rule (numbers from `FightRules`) |
|---|---|
| Guard | Blocks damage before health, 1 point per point of damage. **Guard breaks** when a hit reduces it to 0. At most 14. |
| Mark | At most 1 on a fighter. The party attacks a marked enemy first. The next direct hit on the marked fighter consumes the Mark and deals 3 extra damage. |
| Chill | The chilled fighter's next attack deals 35% less damage and consumes 1 Chill. At most 3. Some texts call a chilled fighter "slowed". |
| Burn | At the end of each round the fighter takes damage equal to its Burn, then loses 1 Burn. Guard blocks it. At most 6. |
| Bleed | Before the fighter acts, it takes damage equal to its Bleed, then loses 1 Bleed. Bleed ignores Guard. At most 5. |
| Regeneration | At the end of each round the fighter heals 3, then loses 1 Regeneration. At most 4. |
| Wet | Storm damage to a Wet fighter deals 2 extra and consumes 1 Wet. Loses 1 Wet at the end of each round. At most 2. |
| Charge | Stored power. A Charge burst consumes all Charge at 3 or more and deals 8 storm damage. At most 5. |
| Steam | When a fighter applies Burn to a Chilled or Wet enemy, or Chill to a Burning enemy, the two cancel one for one and the attacker gains that much Steam. At most 4. |
| Thorns | The next enemy whose move (not an effect) hits the fighter takes damage equal to the Thorns, and all Thorns are consumed. At most 8. |
| Scrap | Left by a weapon that cracks (2). Some relics consume it. |
| Cracked | A weapon cracks at most once per fight. It keeps working, but it loses 25 extra condition after a quest and needs repair. |

## 3. Retreat rules

| Rule | The party retreats when |
|---|---|
| Cautious | any member is down, or the party's total health is below 40% of the health it started with |
| Balanced | 2 members are down, or the last member standing is below 30% health |
| Reckless | never |

The rule is checked at the end of each round. A retreat wins no reward. A party with a Cowardly member keeps 34% of the
stage's gold, and the Coward's Medal keeps 50%. Members downed in a fight are carried home injured.

## 4. Clarity pass (2026-10-14): coverage

Decisions that change the GDD's wording rules are in `docs/GDD_REVISION_2026-10-14.md`. Checks run: `docs/PROGRESS.md`.

| Area | Files | State |
|---|---|---|
| Combat effects, classes, metals, ingredients, interaction paths, enemy moves | `content/CombatContent.kt` | Done. Every description checked against trigger, condition, cost, limit and action. Numbers from `FightRules` and `CoreNumbers` |
| Combat log and fight report | `combat/Fight.kt`, `ui/FightReport.kt`, `ui/RecordsUi.kt` | Done. Each log line names actor and target and stands as a sentence |
| Quests, traits, specialities, laws, ranks, modifiers, preparations, guild visitors | `content/GuildContent.kt`, `guild/*.kt` | Done. Six statements corrected against the code (see DECISIONS) |
| Guild screens: board, quest sheet, retreat rules, checkpoint, roster, wall | `ui/GuildUi.kt`, `GuildPanel.kt`, `ContractSheet.kt`, `CharterPicker.kt`, `OpeningChooser.kt`, `RunSetup.kt`, `HeroDetailSheet.kt` | Done. Stages are rows (`StageLines`), the missed siege is repeated above "Send the party" |
| Visitors, relics, siege traits, world events, engine messages | `engine/*.kt`, `content/Depth.kt`, `battle/Battle.kt`, `ui/EncounterSheet.kt`, `ui/Dialogs.kt` | Done |
| Crafting, catalog, market | `crafting/*.kt`, `content/LaunchContent.kt`, `SliceContent.kt`, `market/*.kt` | Done. Affix and blessing numbers stated |
| Forge, Notebook, weapon and storage screens | `ui/ForgeUi.kt`, `ForgePanel.kt`, `Notebook.kt`, `BenchSheet.kt`, `ui/detail/*`, `StorageSheet.kt`, `SuppliesSheet.kt` | Done |
| Gazette, digest, shop day, hero life, legacy previews | `gazette/*.kt`, `shopday/Lines.kt`, `heroes/Heroes.kt`, `legacy/Legacy.kt`, `ui/shopday/*` | Done |
| Guided first day, menus, errors, recovery | `ui/Intro.kt`, `IntroStrip.kt`, `WorkshopScreen.kt`, `InfoPanels.kt`, `Failures.kt`, `GameViewModel.kt` | Done |
| Left as they were (already clear) | `ShopPanel.kt`, `ShopUi.kt`, `App.kt`, `SettingsSheet.kt`, `CommissionBoard.kt`, `Labels.kt`, `Journal.kt`, `Signatures.kt`, `LegacyProse.kt` | Checked |

Open items:
- Legacy upgrade previews show odds as counts ("2 in 100"), which predates owner decision A.
- `combat/Highlights.kt:47`: the five-sentence cap of a highlight sits inside a `//` comment and never runs. Not fixed:
  it would change stored highlight text, which is outside a wording pass.
- "Injured until day N" means fit again on day N. "Back on day N" may read better; the core lines use "until".
- Guild siege preparation counters for Banked Fires (Void Ink only helps once the party burns the enemy) and the
  Gravecaller (its place in the siege line) were not reworded.
