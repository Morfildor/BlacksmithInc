# Terminology audit A: combat (Phase 1, read-only)

Scope: `core/.../combat/*`, `content/CombatContent.kt`, the fight-relevant parts of `content/GuildContent.kt` (traits,
specialities, bonds, oath, laws, preparations, modifiers), `guild/Loadout.kt`, `guild/GuildOps.kt` (fighter),
`guild/SiegeFight.kt`, `crafting/WeaponRules.kt`, and the app screens `ui/replay/*`, `ui/BladeRulesUi.kt`,
`ui/InspectUi.kt`, `ui/GuildInspect.kt`, `ui/FightReport.kt`, `ui/detail/WeaponStatCard.kt`, `ui/detail/HeroDetailSheet.kt`,
`ui/GuildUi.kt` (member card). Every mechanic below was read from the implementation, not from names or docs.

Paths are abbreviated: `Fight.kt`, `Model.kt`, `Effects.kt`, `Replay.kt`, `Account.kt`, `Highlights.kt` live in
`core/src/main/kotlin/com/tinyblacksmith/core/combat/`. `CombatContent.kt`, `GuildContent.kt`, `LaunchContent.kt` live in
`core/.../content/`. App files live in `app/src/main/java/com/example/blacksmithproject/ui/`.

Numbers: the status numbers are the `FightRules` defaults (`Model.kt:74-100`), which are what the game runs on
(`config/GuildConfig.kt:10` `val fight: FightRules = FightRules()`). Metal numbers are the `CoreNumbers` defaults
(`CombatContent.kt:202`). Arithmetic is integer, a percentage is `(value * percent + 50) / 100` (`Fight.kt:23`), so
"half" rounds .5 up.

Where the fight engine runs (important for "where shown"): guild quests (`guild/Missions.kt:276,293`), the guild-run
siege (`battle/Battle.kt:289` hands off to `guild/SiegeFight.kt:213`) and the Forge's practice fight
(`crafting/Bench.kt:137-139`). A classic-run siege and every townsperson's outing are resolved by the power formula
(`battle/Power.kt`, `battle/Battle.kt`), which has none of these statuses. The "Effects in a fight" card section is shown
only in a guild run (`BladeRulesUi.kt:47`).

Tags used below: **[name]** = a flavourful proper name (identity, keep or rename freely). **[label]** = a mechanical
label the player must learn (should be the most familiar accurate term).

---

## A. Stats and damage words

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Health [label] | `Model.kt:108-109`; hero scale `guild/Loadout.kt:33,53`; class base `CombatContent.kt:166-182` | Fight-scale hit points. Hero max = class base + per level (Guardian 40/+3, Warden 32/+2, Battlemage 26/+2, Ranger 28/+2, Duelist 30/+2). A hero enters with `ceil(max * storedHealth/100)`, at least 1 (`Loadout.kt:53`). Speciality % and Scarred cap (75%) apply (`guild/GuildOps.kt:63-65,72`). 0 = down, no more actions (`Fight.kt:449-454`). Stored back on the 0..100 scale (`Loadout.kt:59`). | "Health" (`GuildUi.kt:756`); "health" in log "Brann 21/40 health" (`FightReport.kt:39`); "Health 12 of 26" (`replay/SiegeReplay.kt:405`); stored hero health also "Health" with a band word (`detail/HeroDetailSheet.kt:118`) | Member card, hero tip, enemy tip, replay portraits, fight report sides | HP / Hit Points (D&D, every RPG). Caveat: two scales share the word: the stored 0..100 "health" of a hero and the fight-scale "Health 31/40". |
| Strike [label] | `Model.kt:110-111`; `Loadout.kt:29-31,40-41`; `Effects.kt:46-47` (`Amount.Strike`); roll `Fight.kt:150` | The damage of one damage step of the actor's own move at 100%. Hero: class base + per level + weapon share (weapon power x condition x class fit x affix attack multipliers x fame / 5), times family% x metal% (`WeaponRules.kt:85`), times trait modifier, times speciality %. Heroes have one fixed value (min = max), enemies a range rolled once per action, the only random draw in a fight (`Fight.kt:20,150`). `Amount.Strike(p)` = p% of the strike rolled for the current action. A Strike-based amount carries the weapon's element (`Fight.kt:338`). | "Strike" stat (`GuildInspect.kt:79`); "strike damage" (`GuildContent.kt:464` Vanguard, `:485` ranks); "Strike 120%" family (`InspectUi.kt:178`); verb "strikes back" (`CombatContent.kt:147`); "attacks for 60% of its strike" (`CombatContent.kt:171`) | Member card, hero tip, enemy tip ("12 to 14"), weapon-type tip, effect texts | Attack / Damage / Weapon damage (Diablo, Torchlight "Damage"); D&D "damage roll" (enemy min-max is close to a damage die). Caveat: "Strike" is used both as the stat noun and as a verb, and "strike damage" appears beside "Strike". |
| Support [label] | `Model.kt:112-113`; `Loadout.kt:42`; `Effects.kt:48-49` | The amount the kit's own supporting step gives: Guardian's Guard (`CombatContent.kt:144`), Warden's heal (`:150`), Gravecaller's heal (`:421`). Class base + per level x family support% x metal support% x speciality %. Battlemage, Ranger and Duelist have support 2 that no move reads (`:173-180`). | "Support" (`GuildUi.kt:756`); "Support 130%" (`InspectUi.kt:179`); "+20% support (the Guard and healing their own move gives)" (`GuildContent.kt:469`) | Member card, hero tip, weapon-type tip | Healing power / Ward power / "Spell power". Caveat: one number means Guard for one class and healing for another, and nothing for three classes. |
| Damage [label] | `Fight.kt:417-463` | Lowers health after, in this order: element x130%/x70% (`:424-428`), +2 if storm on Wet (`:429-432`), +3 if Mark (`:433-436`), then Guard absorbs (`:438-447`), the rest lowers health (`:448-455`). A DAMAGE event exists only for an amount above 0 after Guard (`Model.kt:19-21`). | "damage", "takes N damage from Burn", "hits X for N" (`Fight.kt:450`); pop "-18" (`Replay.kt:186`) | Log, replay pops, report totals | Damage. Caveat: "extra damage" bonuses are added before Guard, so Guard can absorb them. |
| Direct hit [label] | `Effects.kt:22-23` (`Condition.NotTick`); Mark/Wet/weakness gate `Fight.kt:423` | Any non-tick damage from the other side: a move step or an effect's damage (Riposte, Arc, Thorns return, Bronze...). Burn and Bleed ticks are not direct hits. | "direct hit" (`GuildContent.kt:427` Methodical); "hit" (`CombatContent.kt:363` Nightletter); "the next hit on it" (`CombatContent.kt:177`) | Effect texts, statMeaning Mark | Hit (generic). Caveat: "hit" alone is used both for "any direct hit" and "own attack" (see Inconsistencies). |
| Own attack / "On hit" [label] | `CombatContent.kt:135-136` (`onOwnHit` + `FromAction`); `Effects.kt:20-21` | DAMAGE event whose source is the holder and whose via is ACTION: a damage step of the holder's own move that got at least 1 point past Guard. A hit Guard took whole does not count. Extra hits from effects (Swift, Second arrow, Riposte) never count. | "When the wielder's attack damages an enemy" (`CombatContent.kt:265`); short label "On hit" (`InspectUi.kt:105`) | Weapon card ability rows, material tip | On-hit (Diablo, PoE). Caveat: in those games on-hit includes every hit, here only the own move's hits that deal damage past Guard. |
| Ignores Guard / pierce [label] | `Effects.kt:83-84` (`pierce`); `Fight.kt:438` | Damage with `pierce` skips the Guard step entirely, so it neither lowers nor breaks Guard. Used by Reach, Steam lance, Bleed ticks (`Fight.kt:143`). | "ignoring Guard" (`CombatContent.kt:190`); "through Guard" (`CombatContent.kt:234`); "past Guard" (`replay/ReplaySupport.kt:75`); "Reaches past Guard" (`CombatContent.kt:190`) | Effect texts, status keyword | "Bypasses Guard" / armor penetration. Caveat: "piercing" is a D&D damage type, do not use. |
| Extra / bonus damage [label] | Mark `Fight.kt:433-436`; Wet `:429-432`; separate damage actions e.g. `CombatContent.kt:216-217` | Two kinds: a flat bonus folded into the same blow before Guard (Mark +3, Wet +2), or a separate damage action (Starsteel 3, Giant Slayer 4) that is its own direct hit and goes through Guard on its own. | "3 extra damage" (`Fight.kt:434`); "deals 3 more damage" (`CombatContent.kt:217`); "4 more sun damage" (`:283`) | Log, effect texts | Bonus damage. Caveat: "more damage" from an effect is a second hit (can consume a Mark, meets Guard again), not a modifier on the first. |
| Healing [label] | `Fight.kt:404-414`; `Effects.kt:85` | Raises health up to max. The part past max is an OVERHEAL event (no health change). A HEALED event exists only for effective healing (`Model.kt:19-21`). | "heals" (`Fight.kt:410`); "mends" (`replay/ReplaySupport.kt:76`); "recovers 6 health" (`CombatContent.kt:280`); "health mended" (`replay/SiegeReport.kt:108`) | Log, pops "+4", report "Healed" | Heal. Caveat: four verbs for one mechanic. |
| Overheal [label] | `Model.kt:19-21`; `Fight.kt:412-413` | Healing past full health, recorded so effects can answer it (Overflow Basin, Overhealer). Includes Regeneration ticks at full health. | "healed past full health" (`CombatContent.kt:369`); pop "Full health" (`Replay.kt:200`); opening "Overheal" (`GuildContent.kt:509`) | Effect texts, replay | Overheal (WoW, generic). Accurate. |
| Health paid [label] | `Effects.kt:40-41`; `Fight.kt:266-271` | A cost, not damage: health drops by up to N but never below a floor. No DAMAGE event, so nothing that reacts to damage fires. If the holder cannot pay at least 1, the effect does not fire at all and its limit is not used. | "pays up to 4 health (never going below 12)" (`CombatContent.kt:298`); pop "Paid" (`Replay.kt:217`) | Effect texts, replay | Life cost / "Blood price" (PoE Blood Magic). Accurate. |
| Move [label] | `Effects.kt:130-134` (`Move`, `KitDef`); choice `Fight.kt:149` | An actor's one action per round: the first move of its kit whose cadence fits (Always, Opener, Every N, WhileHolding, HealthAtMost). | "Moves" section (`GuildInspect.kt:144`); "its own move" (`CombatContent.kt:284`); "healing move" (`GuildContent.kt:437`); log "Brann: Hold the line." (`Fight.kt:151`) | Enemy tip, effect texts, log | Action / Skill / Ability (D&D "Action"). Caveat: also called "attack" where the move only deals damage. |
| "Acts" / before / after acting [label] | ACTION event `Fight.kt:151-152`; ACTED `Fight.kt:162`; `Model.kt:25-26` | Order inside a turn: Bleed tick (`:139-148`), then ACTION event (answered "before the wielder acts", e.g. Bloodbound), then the move's steps, then ACTED (answered "after the wielder acts", e.g. Second arrow, Swift), only if still standing. | "Before the wielder acts" (`CombatContent.kt:298`); "After the wielder acts" (`:193`) | Effect texts | "At the start / end of the wielder's turn". Caveat: "start of turn" would wrongly suggest it comes before Bleed. |
| Attack target [label] | `Effects.kt:67-71` (`Aim.Foe`); `Fight.kt:320-322`; phrase `CombatContent.kt:130` | Party: first marked enemy in line order, else the first standing enemy. Enemy: first party member tagged `intercept` (Guardian) with Guard above 0, else the first standing member. | "the marked enemy, or the front enemy if none is marked" (`CombatContent.kt:130`); "the enemy the party is attacking" (`CombatContent.kt:234`) | Effect texts, glossary | Target / focus target. Accurate but long. |
| Strike / attack / hit / blow / cut (words) | see right | In code: `strike` = the stat and `Amount.Strike`; "attack" = own move damage (text) and `Aim.Foe` target (comment); "hit" = a DAMAGE or direct hit; "blow" appears only in prose; "cut" = Duelist and Dagger hits. | "Its next strike is 35% weaker" (`replay/ReplaySupport.kt:77`); "next attack deals 35% less" (`CombatContent.kt:227`); "Blows hit Guard before health." (`replay/ReplaySupport.kt:73`); "Off-hand cut" (`:195`) | Everywhere | Pick one: "attack" for the own move's damage step, "hit" for any direct damage. Caveat: Chill text says "strike" and "attack" for the same thing. |

## B. Statuses (every `Stat` and every fighter state)

`Stat` enum: GUARD, CHARGE, MARK, BURN, REGEN, CHILL, WET, BLEED, SCRAP, THORNS, STEAM (`Model.kt:16`). Caps `Model.kt:86-89`.
Nothing outlives a fight (`Model.kt:14`). Three separate display maps exist: `Fight.kt:372-375`, `Replay.kt:107-110`
("Regen"), `InspectUi.kt:73-76`.

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Guard [label, locked] | absorb `Fight.kt:438-447`; cap `Model.kt:87` (14); gain `Fight.kt:393-399` | A pool that absorbs damage before health, 1 point per point. Cap 14. Never decays: it stays across rounds until used up. Burn ticks are absorbed (`Fight.kt:180` pierce=false). Bleed ticks and `pierce` damage skip it. Mark/Wet/weakness bonuses are added before Guard, so Guard absorbs them too. Also decides enemy targeting with the Guardian (see Taunt row). | "Guard" (`Fight.kt:373`); pops "Guard +4", "Blocked 5" (`Replay.kt:191,194`); "Blows hit Guard before health." (`replay/ReplaySupport.kt:73`); report "Blocked" (`replay/SiegeReport.kt:162`) | Everywhere | D&D "Temporary Hit Points" (absorb first, do not decay; caveat: D&D temp HP do not stack, Guard stacks to 14); Slay the Spire "Block" (caveat: Block expires each turn); Diablo IV "Barrier". Never "Armor" (per-hit reduction) and never "Block" in the Diablo/WoW sense (a chance). |
| Guard breaks [label] | `Fight.kt:445` | A GUARD_BROKEN event when a blow absorbed at least 1 and left Guard at exactly 0. A Burn tick can break Guard (it has no source), and then every rule that needs "an enemy breaks" (`Condition.SourceIsFoe`) stays silent. Pierce damage never breaks Guard. A fighter with 0 Guard cannot have it broken. | "Guard breaks" (`Fight.kt:445`); pop "Guard broken" (`Replay.kt:193`); trigger label "Enemy breaks Guard" (`InspectUi.kt:109`) | Log, replay, ability rows | "Shield broken" (generic). Caveat: it is "Guard runs out", not a stagger or a status. |
| Mark [label, locked] | cap 1 `Model.kt:87`; consume `Fight.kt:433-436`; targeting `Fight.kt:321,323` | At most 1 per fighter. The party's attack target is the first marked enemy. The next direct hit from the other side on the marked fighter adds 3 and removes the Mark, whoever deals it (a Thorns return, an Arc, a Bronze answer all count). The +3 is added before Guard. Enemies do not prefer marked party members (no content marks party members). | "Mark" (`Fight.kt:373`); pop "Mark struck" (`Replay.kt:209`); log "hits the Mark on X for 3 extra damage" (`Fight.kt:434`); "consumes the Mark" (`CombatContent.kt:255`) | Everywhere | Darkest Dungeon "Mark" (focus target, bonus damage); Slay the Spire "Vulnerable" (caveat: % for turns). D&D "Hunter's Mark" is a trap (lasts, +1d6 every hit). |
| Chill [label, locked] | `Fight.kt:154-160`; cap 3 `Model.kt:87`; number `Model.kt:78-79` (35) | When the chilled actor's own move reaches its first Damage step, it loses 1 Chill and that whole step deals 35% less (to every target of the step). Other steps of the move, heals, Guard steps and every effect's damage are untouched. A move with no Damage step does not use it. No decay. No speed effect of any kind. Chill given to a burning enemy by an enemy of it cancels into Steam (`Fight.kt:385-391`). | "Chill" (`Fight.kt:373`); "slowed" (`CombatContent.kt:291`); pop "Chilled · weaker" (`Replay.kt:211`); "Its next strike is 35% weaker" (`replay/ReplaySupport.kt:77`) | Everywhere | Slay the Spire "Weak" (outgoing damage down; caveat: there it lasts turns, here 1 attack per stack). Diablo/PoE "Chill" means slow: a trap, and "slowed" in our texts already misdescribes it. |
| Burn [label, locked] | `Fight.kt:176-183`; cap 6 | At the end of each round, damage equal to the Burn stacks (min 1 after tick resistance), Guard absorbs it, then Burn drops by 1. A tick is not a direct hit (no weakness, no Mark). Embermaw units take 50% (`CombatContent.kt:431-441` `tickResist`). Burn given to a Chilled or Wet enemy cancels one for one into Steam first (`Fight.kt:385-391`). | "Burn" (`Fight.kt:373`); pop "Burn applied" (`Replay.kt:203`); "Burn ticks" (`Account.kt:155`); "At the end of each round: loses 1 health per stack" (`replay/ReplaySupport.kt:74`) | Everywhere | "Burning" (Diablo, BG3). Mechanically a decaying damage-over-time like Slay the Spire Poison, but blocked by Guard. |
| Bleed [label, locked] | `Fight.kt:139-148`; cap 5 | Before the bleeding actor acts (before the ACTION event), damage equal to Bleed (min 1 after tick resistance) that ignores Guard, then Bleed drops by 1. Not a direct hit. Hollowbound take 50% (`CombatContent.kt:416`). Can down the actor before its move. | "Bleed" (`Fight.kt:374`); "Before acting: loses 1 health per stack, past Guard" (`replay/ReplaySupport.kt:75`) | Everywhere | "Bleeding" (BG3, Diablo). Mechanically identical to Slay the Spire "Poison" (start of turn, ignores Block, decays by 1). No "Poison" exists in the game. |
| Regeneration [label, locked] | `Fight.kt:184-191`; heal `Model.kt:80-81` (3); cap 4 | At the end of each round, the fighter heals a flat 3 (not 3 per stack), then loses 1 Regeneration. Stacks are a duration counter, unlike Burn and Bleed where stacks are both strength and duration. Overflow at full health is an OVERHEAL. | "Regeneration" (`Fight.kt:373`); "Regen" chips and pops (`Replay.kt:108`); "mends 3" (`replay/ReplaySupport.kt:76`) | Everywhere | D&D "Regeneration" (fixed heal each turn), "heal over time". Caveat: players will read "Regeneration 4" as 4 per round. |
| Wet [label, locked] | `Fight.kt:192-193,429-432`; cap 2 | A storm-element direct hit from the other side on a Wet fighter adds 2 and removes 1 Wet. Wet drops by 1 at each round end. Burn given to a Wet enemy cancels Wet into Steam. Only source: the Storm front law (both sides start with 2, `GuildContent.kt:478-479`), and the Battlemage's Echo can add 1. Never present at a siege. | "Wet" (`Fight.kt:374`); pops "Wet struck", "Wet dries" (`Replay.kt:210,167`) | Quests under Storm front, replay | BG3 / Divinity "Wet". Caveat: there it is a vulnerability (x2 lightning/cold), here +2 flat per stack. |
| Steam [label, locked] | `Fight.kt:384-391`; cap 4 | Created when a fighter gives Burn to an enemy that has Chill or Wet, or Chill to an enemy that has Burn: the two cancel one for one and the giver gains that much Steam. Only Steam lance and Steam veil spend it. Same-side gives never cancel. | "Steam" (`Fight.kt:374`); pop "Chill boiled off" (`Replay.kt:208`); "cancels 1 Chill on X into Steam" (`Fight.kt:388`); "Boiling point" path (`CombatContent.kt:243`) | Log, replay, Notebook path | Original (Divinity has steam clouds, different). Keep, explain. |
| Thorns [label, locked] | `Fight.kt:456-462`; cap 8 | When an enemy's own move damage step hits the holder (via ACTION, even if Guard absorbed all of it, even if the holder went down), all Thorns are removed and the attacker takes that much damage. The return is effect damage: the attacker's Guard absorbs it, it can consume the attacker's Mark (+3), and it never triggers Thorns itself. Effects and ticks never set it off. | "Thorns" (`Fight.kt:374`); "The next blow it takes hurts the attacker for this much, once." (`replay/ReplaySupport.kt:80`); report "Thorns" share (`Account.kt:152`) | Effect texts, replay, report | Diablo "Thorns" (caveat: there it is permanent and per hit; here a one-shot pool). D&D "Armor of Agathys" is closer (temp HP that hurt the attacker) but a different name. |
| Charge [label, locked] | cap 5 `Model.kt:87`; burst `CombatContent.kt:139-140` | A plain resource. Only effects read it. The shared Charge burst spends it (see E). | "Charge" (`Fight.kt:373`); "Stored for a rule that spends it." (`replay/ReplaySupport.kt:81`) | Effect texts, replay | Charges (Diablo, generic). Accurate. |
| Scrap [label, locked] | `Fight.kt:352-356`; cap 4 | Given (2) when a weapon cracks. Only Salvage Bell spends it. | "Scrap" (`Fight.kt:374`) | Effect texts, replay | Original. Accurate. |
| Cracked (weapon) [label] | `Effects.kt:92-93` (`Fracture`); `Fight.kt:352-356`; wear `config/GuildConfig.kt:50-51` | At most once per fight per fighter: the weapon keeps its full effect in the fight, its wielder gains 2 Scrap, a FRACTURED event fires (Rust Savant, Salvage Bell, Rune of answer wait for it). After the fight the weapon loses 25 extra condition (`guild/Missions.kt:367`, `guild/SiegeFight.kt:265`). | "cracks" (`Fight.kt:354`); pop "Weapon cracked" (`Replay.kt:218`); "weapon cracked" (`FightReport.kt:39`); "mended" (`GuildContent.kt:515`) | Log, report facts, replay | "Damaged" (durability). Diablo "Broken" means unusable: a trap. |
| Down [label] | `Fight.kt:451-454` | Health 0: the fighter takes no more turns, cannot be healed or given states (`Fight.kt:383,405,418`). A Second Wind does not save a fighter who is already at 0. | "is down" (`Fight.kt:453`); "Down" (`Replay.kt:188`); "went down" (`replay/SiegeReport.kt:161`); "felled" (`Account.kt:241`); "downs" (`CombatContent.kt:280`) | Everywhere | Downed (BG3), knocked out. Accurate. |
| Tick resistance [label] | `Model.kt:120-121`; `Fight.kt:199` | Per unit: share of a Burn or Bleed tick ignored (Embermaw 50% Burn, Hollowbound 50% Bleed), at most 90%, never below 1 damage. Shown as "Resisted" on the tick pop. | "Takes 50% less damage from Bleed." (`GuildInspect.kt:145`); "take only half damage from Burn" (`CombatContent.kt:226`); pop note "Burn · resisted" (`Replay.kt:180`) | Enemy tip, notes, replay | Resistance (generic). Caveat: separate from element "resists" (30%). Same word, two numbers. |
| Scarred (member) [label] | `guild/GuildOps.kt:63-65`; `config/GuildConfig.kt:45-47` | For 5 days the member enters fights with at most 75% of their fight health. | "Scarred" (glossary, `docs/WRITING_GUIDE.md:93`) | Member card, quest sheet | Lingering injury (Darkest Dungeon). Outside the fight engine. |
| Starting stacks [label] | trait `guild/GuildOps.kt:70`; prep `guild/SiegeFight.kt:87,139,210`; law/modifier `guild/Missions.kt:263` | Stacks an actor already holds at round 1 (Cowardly 4 Guard, Storm-Touched 1 Charge, Storm front 2 Wet, Eclipse Mark/Bleed, Shields up 4 Guard, Tired sentries 1 Chill, preparations). Recorded as nobody's in the report (`Account.kt:106-107`). | "starts every fight with 4 Guard" (`GuildContent.kt:423`) | Trait, law, prep, modifier texts | Starting buff/debuff. Accurate. |

## C. Triggers, timing, targeting, limits

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Round and turn order [label] | `Fight.kt:79-95,61-62`; max `Model.kt:75` | Each round: ROUND_START, then every party member in slot order (the order the smith picked), then every enemy in line order, each taking one move. Round end: per actor in slot order, Burn ticks, Regeneration ticks, Wet dries (`Fight.kt:173-197`). No initiative or speed stat. Summons are inserted after their side's last actor (`Fight.kt:364`). Max 8 rounds (12 at a Long Assault siege, `guild/SiegeFight.kt:211`, not stated in `content/Depth.kt:97`). | "Round 3." (`Fight.kt:85`); "Round 3 of 8" (`replay/SiegeReplay.kt:200`) | Log, replay, glossary | Round / turn (D&D). Accurate. Caveat: party always acts first. |
| Outcome [label] | `Model.kt:71`; `Fight.kt:96-103,116-136` | WON (all enemies down, or a HOLD objective reached), LOST (no non-summoned member standing, or the protected unit down), RETREATED (retreat rule at round end), TIMED_OUT. A quest counts TIMED_OUT as a retreat (`guild/Missions.kt:299`). The siege fights with the Balanced rule (`guild/SiegeFight.kt:213`): a retreat there is "Defenses broken", a time-out is "Held at a cost" (`:222-226`). | "Victory / Defeat / Pulled back / Undecided" (`Replay.kt:281-286`); "Won in / Retreated after / Lost after / No winner after" (`FightReport.kt:27-35`); "The party retreats." (`Fight.kt:100`); "breaks off" (`GuildUi.kt:215`) | Replay banner, fight report, log | Victory / Defeat / Retreat. Caveat: "Pulled back" contradicts the locked term Retreat. |
| Triggers (EventKind) [label] | `Model.kt:22-27`; `Effects.kt:15` | ROUND_START, ACTION (before the move, also the root of a tick), DAMAGE, GUARD_ABSORBED, GUARD_BROKEN, GUARD_GAINED, HEALED, OVERHEAL, STAT_GAINED, STAT_SPENT, HEALTH_PAID, FRACTURED, DOWNED, SUMMONED, RETREATED, OBJECTIVE, ROUND_END, ACTED. Effects answer events in record order, holders in slot order, then party rules (`Fight.kt:203-220`). An effect never answers its own product, an echo never answers an echo (`:249-250`). | short labels "On hit", "Fight start", "Each round", "Round end", "Enemy breaks Guard" (`InspectUi.kt:105-109`); else the sentence's own "When ..." clause | Ability rows on weapon, member and enemy cards | "On hit", "At the start of combat", "At the start of each round", "At the end of each round", "When your Guard breaks". Caveat: "Fight start" is ROUND_START of round 1 with a per-fight limit, so it happens before anyone acts. |
| Who (whose event) [label] | `Effects.kt:12` | SELF_TARGET / SELF_SOURCE / ALLY_TARGET / ALLY_SOURCE / FOE_TARGET / FOE_SOURCE / ANY. "Ally" includes the holder. | "a party member (this one included)" (`GuildContent.kt:420`); "any fighter, the wielder included" (`CombatContent.kt:313`) | Effect texts | Self / ally / enemy. Caveat: "ally" includes self everywhere, said only sometimes. |
| Conditions [label] | `Effects.kt:17-35`; `Fight.kt:282-296` | SourceIsFoe, FromAction (own move), NotTick (direct hit), HolderHas/Lacks, TargetHas, StatIn, HolderHealthAtMost, TargetHealthAtMost (target alive), AmountAtLeast, Happened (this side, this fight), HolderTagged, TargetTagged. | "that already has Chill" (`CombatContent.kt:220`); "Once a weapon in the party has cracked in this fight" (`:311`) | Effect texts | Plain conditions. |
| Limits [label] | `Effects.kt:98-99`; counters `Fight.kt:251-253,274-275` | Per round and/or per fight, counted per effect ID per holder. A party rule counts once for the party unless `perMember` (`Fight.kt:217`). A cost that cannot be paid stops the effect before the counter moves. | "Once per round." (`CombatContent.kt:140`); "Once per round, at most twice per fight." (`:311`); "Once per round for each member." (`:369`); limit words "Twice per fight" (`InspectUi.kt:115-119`) | Ability rows | D&D "once per turn" / "once per round". Accurate. |
| Front / back enemy [label] | `Fight.kt:320-326`; `Effects.kt:76-77` | Front = first standing in line order, back = last standing. A summoned shambler joins at the back of the enemy line, the spectral helper at the back of the party. With one enemy left, front and back are the same enemy (Steam lance then hits it twice). | "the front enemy", "the back enemy" (`CombatContent.kt:193`); "the back member of the party" (`CombatContent.kt:404`); "the last defender in the line" (`GuildContent.kt:554`) | Effect texts | Front line / back line (generic, Darkest Dungeon ranks). Accurate. |
| Priority target [label] | `Fight.kt:327`; `Effects.kt:78-79`; phrase `CombatContent.kt:131` | First enemy tagged `anchor` (Gravecaller, Hollow King), else tagged `leader`, else the enemy with most health (ties: later slot). | "the priority target (an enemy summoner first, then the enemy leader, then the enemy with the most health)" (`CombatContent.kt:131`); "the summoner first, else the leader" (`GuildContent.kt:491`) | Effect texts | Focus target. Caveat: "summoner" is the code tag `anchor`, never shown on an enemy card (`GuildInspect.kt:113-114` shows only Leader and Elite). |
| Weakest ally [label] | `Fight.kt:316`; phrase `CombatContent.kt:132` | Living ally (holder included) with lowest health/max; ties by slot. | "the weakest ally (lowest health percentage)" (`CombatContent.kt:132`); "its most wounded ally" (`CombatContent.kt:421`) | Effect texts | Lowest-health ally. Accurate. Note `Aim.WeakestFoe` (`Fight.kt:325`) uses absolute health, but no content uses it. |
| "That enemy" (event source/target) [label] | `Effects.kt:59-60`; `Fight.kt:314-315` | The fighter the triggering event came from (EventSource) or landed on (EventTarget). | "that enemy" (`CombatContent.kt:209`) | Effect texts | Accurate. |
| Marked enemy only [label] | `Effects.kt:72-73`; `Fight.kt:323` | `Aim.MarkedFoe`: nobody when no enemy is marked. No content uses it. | none | none | n/a |
| Intercept (Guardian) [label, hidden] | tag `CombatContent.kt:100,143`; `Fight.kt:322` | Enemy single-target attacks (`Aim.Foe`) go to the first party member tagged `intercept` that has Guard. Attacks on the whole party (Fire breath, Inferno), on the back member (Cut and run, the second part of Reave) and Burn from Nip follow their own aim (Nip gives Burn to `Aim.Foe`, so it does go to the Guardian). | "While the Guardian has Guard, enemies attack it instead of the front member." (`CombatContent.kt:168`); "past any Guardian" (`CombatContent.kt:404`) | Class role line | Taunt (WoW), "Guard" (Darkest Dungeon), "Protect". Caveat: conditional on Guard and limited to single-target attacks. |
| Hidden tags [label, hidden] | `CombatContent.kt:100-107`; `GuildContent.kt` `TAG_NOVICE_PARTY` | `intercept` (Guardian), `charge` (has a Charge burst: weapon `Loadout.kt:48` or Storm-Touched `GuildContent.kt:431`), `leader`, `anchor`, `elite`, `undead`, `common_gear` (Common/Uncommon weapon or none, `Loadout.kt:47`), `summoned`, `novice_party`. Only Leader and Elite get a card label. | "an enemy summoner" (`CombatContent.kt:131`); "one of the Hollowbound" (`:283`); "a Common or Uncommon weapon (or no weapon)" (`:381`) | Effect texts only | Keyword tags (Elite, Boss, Undead, Summoner). |
| Objective [label] | `Model.kt:62-69`; `Fight.kt:116-124` | DEFEAT: every enemy down. HOLD: at the end of round N, somebody of the party still stands. `protect`: that unit going down loses the fight. | "it wins if at least 1 member is still standing after 5 rounds" (`GuildContent.kt:576`) | Quest card, stage rows | Survive N rounds / Escort. Accurate. |
| Retreat rule [label, locked] | `Fight.kt:126-136`; numbers `Model.kt:90-94` | Checked at each round end. Cautious: any member down, or party health below 40% of entering health. Balanced: 2 members down, or the last of 2+ members below 30% of max. Reckless: never. | "Retreat rule" (`ContractSheet.kt:120`) | Quest sheet | Locked. |

## D. Elements

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Element of a hit [label] | `Fight.kt:337-338`; `Loadout.kt:53` | A weapon's element rides on every Strike-based damage of its wielder (moves, Riposte, Second arrow, Off-hand cut, Swift, Impatient, Loyal). A fixed-amount effect has only the element it names (Charge burst, Arc, Stormcaller: storm; Undead Bane: sun; Nightletter: grave). Enemies never have an element (`Loadout.kt:62-65` passes none), so "Fire breath", "Inferno", "Grave weapon" deal plain damage. | "deals 8 storm damage" (`CombatContent.kt:140`); "A weapon's strikes deal its element's damage." (`docs/WRITING_GUIDE.md:117`) | Effect texts, replay "Weak"/"Resisted" | Damage type (D&D fire/cold/lightning/necrotic/radiant). |
| Weak to / resists [label] | `Fight.kt:423-428`; numbers `Model.kt:84-85` | One direction only: a direct hit with an element from the other side deals x130% if the target is weak to it, x70% if it resists it, applied first (before Wet, Mark, Guard). Heroes have no weakness or resistance (`Loadout.kt:50-55`). Ticks ignore it. Ashclaw and Embermaw: weak Frost, resist Fire. Hollowbound: weak Sun, resist Grave. | "Weak to" / "Resists" (`GuildInspect.kt:140-141`); pops "Weak", "Resisted" (`Replay.kt:181-182`); "struck a weakness" (`Account.kt:236`) | Enemy tip, replay, report observations | Weakness / Resistance (generic). D&D "Vulnerability" (x2) and "Resistance" (x0.5) overstate: a trap. |
| Fire / Frost / Storm / Grave / Verdant / Sun [label] | `content/Element` enum; effects `CombatContent.kt:264-271` | Fire puts Burn, Frost puts Chill, Storm adds 2 storm damage to the back enemy, Grave puts Bleed, Verdant gives the wielder Regeneration, Sun puts a Mark (see E). Storm also interacts with Wet. | element names straight from the enum (see section 8) | Weapon card "Element", material tip, enemy tip | Fire, Cold/Frost, Lightning, Necrotic, Nature/Poison, Radiant/Holy (D&D/Diablo). Caveat: "Grave" applies Bleed, not a necrotic drain, and "Verdant" heals, it is not Poison. |

## E. Named effects (every one)

Each row is one EffectDef or Move. "Own attack" = DAMAGE via the holder's own move (`FromAction`), past Guard. All
limits are per holder unless marked.

### E1. Classes (moves and passives) and the shared Charge burst

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Hold the line [name] (Guardian move) | `CombatContent.kt:144` | Every round: Guard = Support to self, then 100% Strike to the attack target. | "Gains Guard every round, then attacks." (`CombatContent.kt:168`) | Member card (`GuildInspect.kt:45` move names as title), log | "Defend and strike". |
| Riposte [name] (Guardian passive) | `CombatContent.kt:146-147` | When an enemy (SourceIsFoe) breaks this Guardian's Guard: 50% of Strike (with the weapon's element) to that enemy, as effect damage. Once per round. | "strikes that enemy back for half its strike" (`CombatContent.kt:147`) | Member card, log | D&D Battle Master "Riposte" answers a miss: a trap (no misses here). "Retaliate" / "Counterattack" (on Guard break). |
| Mend [name] (Warden move) | `CombatContent.kt:150` | Every round: heal Support to the weakest ally (self included), then 60% Strike to the attack target. | "heals the ally with the lowest health percentage, then attacks for 60% of its strike" (`CombatContent.kt:171`) | Member card | Heal. Note "mend" also means weapon repair (`GuildContent.kt:515`). |
| Opening sigil [name] (Battlemage, round 1) | `CombatContent.kt:153` | Round 1 only: 1 Charge to the first living ally tagged `charge` (Battlemage included; from a weapon's burst or Storm-Touched), a Mark on the priority target, then 70% Strike to the attack target. Because the Mark comes first, the attack target is now the first marked enemy, so the Battlemage's own 70% hit normally lands on the priority target and consumes that Mark at once (+3). | role line `CombatContent.kt:174` | Member card | "Opener". |
| Bolt [name] (Battlemage) | `CombatContent.kt:154` | Other rounds: 100% Strike to the attack target. | "Then attacks every round." (`CombatContent.kt:174`) | Member card | Basic attack. |
| Echo [name] (Battlemage passive) | `CombatContent.kt:156-157` | When any enemy gains Burn, Chill or Wet (from anyone), that enemy gets 1 more of the same, given by the Battlemage (so a cancel into Steam goes to the Battlemage). Once per round. An echo never answers an echo. | "When any enemy gains Burn, Chill or Wet, that enemy gains 1 more of the same." (`CombatContent.kt:157`) | Member card | "Amplify" / "Intensify". |
| Mark and loose [name] (Ranger move) | `CombatContent.kt:160` | 100% Strike to the attack target, then a Mark on the priority target (after the hit, so the hit never uses it). | "Attacks, then puts a Mark on the priority target" (`CombatContent.kt:177`) | Member card | "Hunter's Mark" trap (see Mark). |
| Double cut [name] (Duelist move) | `CombatContent.kt:163` | Two separate 60% Strike steps to the attack target. Chill weakens only the first. Each step can trigger on-hit effects (once-per-round limits still apply). | "Attacks twice every round, each hit for 60% of its strike." (`CombatContent.kt:180`) | Member card | "Twin strike" / "Flurry". |
| Charge burst [label] (shared) | `CombatContent.kt:139-140`; on Stormcharged `:279`, Binding Salt `:340`, Storm-Touched `GuildContent.kt:431` | When the holder gains Charge and then has 3+, it spends all Charge and deals 8 storm damage to the attack target (Wet +2, Mark +3, storm weakness apply). Once per round. Listed once per weapon (`WeaponRules.kt:76-82`). | "Charge burst" (`CombatContent.kt:139`); heading "Charge burst · buff, also Binding Salt" (`BladeRulesUi.kt:24`) | Weapon card, member card | "Discharge" / "Overload". |
| forgeHint (class) | `CombatContent.kt:169-181` | Text only. No screen reads `forgeHint` (grep: only its definitions). | n/a | Not shown | Dead text: mention, do not rename. |

### E2. Weapon families (with `pattern` text)

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Sword / Axe / Staff patterns | `CombatContent.kt:186,187,197` | Strike% / Support%: Sword 100/100, Axe 120/90, Staff 80/130. No effect. | "One clean strike. 100% strike and support." (`:186`) | Weapon card note, weapon-type tip | Accurate. |
| Reach [name] (Spear, 95/100) | `CombatContent.kt:188-190` | When an enemy's Guard absorbs part or all of the wielder's own-move damage (GUARD_ABSORBED, FromAction): half of the absorbed amount (rounded up) as pierce damage to that enemy. Once per round. | "Reach" name; pattern "95% strike. Reaches past Guard." (`:190`); prep counter "A spear's hit still deals half of the damage their Guard blocks." (`GuildContent.kt:552`) | Weapon card | Trap: D&D "Reach" is attack range. Closer: "Armor-piercing" / "Penetration" (half of blocked damage). |
| Second arrow [name] (Bow, 90/100) | `CombatContent.kt:191-193` | After the wielder acts (own move): 35% Strike (with element) to the back enemy. Effect damage: no on-hit effects, no Chill, no Thorns. Once per round. | "a second arrow hits the back enemy" (`:193`); id `bow_volley` | Weapon card | "Extra shot" / "Follow-up". D&D "Extra Attack" is a full attack: a trap. |
| Off-hand cut [name] (Dagger, 80/100) | `CombatContent.kt:194-196` | After the wielder acts: 45% Strike to the attack target. Effect damage as above. Once per round. | "cuts the marked enemy ... again for 45% of its strike" (`:196`); pattern "one extra light cut each round" (`:196`) | Weapon card | D&D two-weapon "off-hand attack" fits the flavour. "Follow-up attack". |

### E3. Metals (`CoreNumbers` defaults)

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Iron holds [name] | `CombatContent.kt:205-206` | At the start of round 1: 3 Guard to the wielder. Once per fight. | "The wielder starts each fight with 3 Guard." (`:206`) | Weapon card, material tip | "Starting Guard". Name is a sentence (log reads "gains 3 Guard from Iron holds"). |
| Bronze rings back [name] | `CombatContent.kt:207-209` | When an enemy breaks the wielder's Guard: 5 damage (no element) to that enemy. Once per round. | "the weapon deals 5 damage to that enemy" (`:209`) | Weapon card | "Retaliate". |
| Silver holds a charge [name] | `CombatContent.kt:210-211` | At the start of round 1: 3 Charge. If the wielder has a Charge burst, the burst fires at once, before anyone acts (gain → STAT_GAINED → burst). | "starts every fight with 3 Charge, enough for one Charge burst" (`:210`) | Weapon card | Timing not stated. |
| Obsidian cracks [name] (+10% strike) | `CombatContent.kt:212-214` | Strike x110%. The first time an enemy breaks the wielder's Guard: the weapon cracks (2 Scrap, 25 extra wear after). Once per fight. Same mechanic as Brittle. | "A glass edge. +10% strike." (`:212`) | Weapon card | "Fragile". |
| Starsteel finds the Mark [name] | `CombatContent.kt:215-217` | When the wielder's own move consumes a Mark (STAT_SPENT MARK via ACTION): 3 more damage to that enemy, a separate hit after the Mark is gone. Once per round. Effect hits that consume a Mark do not count. | "When the wielder's own attack consumes a Mark" (`:217`) | Weapon card | Mark payoff. |
| Moonsteel bites the cold [name] | `CombatContent.kt:218-220` | When the wielder's own attack damages an enemy that has Chill: 2 more damage. Chill stays. Once per round. Metal is asked before the element, so its own Frost Chill from the same hit is not seen (`WeaponRules.kt:17-20`). | "The Chill stays." (`:220`) | Weapon card | "Bonus vs Chilled". |

### E4. Element effects (one per element, on the weapon)

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Ember [name] (Fire) | `CombatContent.kt:265` | Own attack damages an enemy: 1 Burn on it. Once per round. | heading "Ember · element" (`BladeRulesUi.kt:24`) | Weapon card | "Ignite". Same word as Ember Resin, Ember guard, Embermaw. |
| Rime [name] (Frost) | `CombatContent.kt:266` | Own attack: 1 Chill on the target. Once per round. | "Rime" | Weapon card | "Frostbite". |
| Arc [name] (Storm) | `CombatContent.kt:267` | Own attack damages an enemy: 2 storm damage to the back enemy (can be the same enemy). Once per round. | "the weapon also deals 2 storm damage to the back enemy" (`:267`); note "The arc is small." (`:229`) | Weapon card | "Chain lightning" / "Arc". Accurate. |
| Grave chill [name] (Grave) | `CombatContent.kt:268` | Own attack: 1 Bleed on the target. Once per round. | "Grave chill" | Weapon card | Trap: the name says Chill, the effect is Bleed. "Grave wound" / "Rend". |
| Sap [name] (Verdant) | `CombatContent.kt:269` | Own attack: 1 Regeneration to the wielder. Once per round. | "Sap" | Weapon card | "Lifebloom" / "Renew". |
| Noon light [name] (Sun) | `CombatContent.kt:270` | Own attack: a Mark on the target. Once per round. | "Noon light" | Weapon card | "Expose" / "Mark". |

### E5. Affixes ("buffs") and flaws in a fight

Each affix also has a second, older rule set in the power system, shown in the card's "Buffs"/"Flaws" rows
(`LaunchContent.kt:149-167`, `detail/WeaponStatCard.kt:145-152`), beside these fight effects (`WeaponStatCard.kt:153`).
See Inconsistencies 1.

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Flaming [name] | `CombatContent.kt:275` | Own attack: 1 Burn on the target. Once per round, counted apart from Ember, so a Fire Flaming weapon puts 2. | "This adds to any Burn from the weapon's element." (`:275`) | Weapon card | "Burning" / "of Fire". |
| Frostbound [name] | `CombatContent.kt:276` | Own attack: 1 Chill. Once per round, apart from Rime. | as above | Weapon card | "Frost". |
| Stormcharged [name] | `CombatContent.kt:277-279` | Own attack: 1 Charge to the wielder. Once per round. Brings the Charge burst. | "the wielder gains 1 Charge" (`:278`) | Weapon card | "Charged". |
| Vampiric [name] | `CombatContent.kt:280` | When the wielder downs an enemy (any damage the wielder is the source of, effects included): heal the wielder 6. Once per round. | "the wielder recovers 6 health" (`:280`); power text "heals 12 health after each won fight away from town" (`LaunchContent.kt:154`) | Weapon card | Trap: "Vampiric" means lifesteal. Accurate: "life on kill" (Diablo/PoE). |
| Swift [name] | `CombatContent.kt:281` | After the wielder acts: 30% Strike to the attack target. Effect damage. Once per round. Stacks with Dagger. | "strikes the marked enemy ... again for 30% of its strike" (`:281`) | Weapon card | "Follow-up attack". Caveat: not a speed effect. |
| Giant Slayer [name] | `CombatContent.kt:282` | Own attack damages an enemy tagged `elite`: 4 more damage. Once per round. Leaders are not checked. Today every leader is also elite (`:408,412,428,440`). | "an elite enemy or an enemy leader" (`:282`); power text "against elites and warlords" (`LaunchContent.kt:156`) | Weapon card | "Slayer" (D&D/Diablo "+damage vs Elites"). |
| Undead Bane [name] | `CombatContent.kt:283` | Own attack damages an `undead` enemy: 4 more sun damage (x130% on Hollowbound, who are weak to Sun, so 5). Once per round. | "one of the Hollowbound" (`:283`) | Weapon card | D&D "Bane". Accurate. |
| Guardian's [name] | `CombatContent.kt:284` | When the wielder gains Guard from its own move (GUARD_GAINED via ACTION): 2 more Guard. Once per round. Only Hold the line gives Guard by a party move, so it works only on a Guardian. | "When the wielder gains Guard from its own move" (`:284`) | Weapon card | "Fortifying". Name collides with the class. |
| Reinforced [name] | `CombatContent.kt:285` | Start of round 1: 4 Guard. Once per fight. | "starts each fight with 4 Guard" (`:285`) | Weapon card | Accurate. |
| Resonant [name] | `CombatContent.kt:286` | When the wielder's own move heals an ally (HEALED, own source, via ACTION, effective healing only): 1 Regeneration to that ally. Once per round. | "that ally also gains 1 Regeneration" (`:286`) | Weapon card | "Lingering heal". |
| Brittle [name, flaw] | `CombatContent.kt:288-289` | First time an enemy breaks the wielder's Guard: crack, 2 Scrap. Once per fight. Identical to Obsidian cracks. | "the weapon cracks" (`:289`); power text "The weapon may break when the wielder loses a fight away from town." (`LaunchContent.kt:162`) | Weapon card | "Fragile". |
| Heavy [name, flaw] + Shatter [name] | `CombatContent.kt:290-293` | Heavy: start of round 1, 1 Chill on the wielder (its first attack -35%, no Steam since same side). Shatter: own attack damages a chilled enemy: remove up to 3 Chill from it, then 6 damage to it. Once per round. | "1 Chill (slowed)" (`:291`); "Shatter" (`:292`) | Weapon card | "Cumbersome" / "Shatter" (Diablo frozen shatter is close). Caveat: "slowed" is wrong. |
| Unstable [name, flaw] | `CombatContent.kt:294-295` | First time the wielder takes any damage (ticks included) and is at 50% health or less: pays 3 health (floor 1), then 5 damage (no element) to every enemy. Once per fight. At 1 health it cannot pay and does not fire. | "the weapon discharges" (`:295`) | Weapon card | "Volatile" / "Nova on low life". |
| Cursed [name, flaw] | `CombatContent.kt:296` | Start of round 1: 2 Bleed on the wielder. Once per fight. | "starts each fight with 2 Bleed" (`:296`) | Weapon card | D&D curse is broader. Accurate as text. |
| Bloodbound [name, flaw] | `CombatContent.kt:297-298` | Before the wielder acts (own action, not a tick): pay up to 4 health (floor 12), then twice the amount paid as damage to the attack target. Once per round. | "pays up to 4 health (never going below 12)" (`:298`) | Weapon card | "Blood price" (PoE Blood Magic). |
| Sentient (at home) [name] | `CombatContent.kt:301` | Siege only: start of round 1, 2 Guard to every ally. Once per fight. | "Only on the town's wall" (`InspectUi.kt:232`); "Only when defending the town" (`WeaponStatCard.kt:170`) | Weapon card | "Rally". |
| Sentient (on the road) [name] | `CombatContent.kt:304` | Quests only: start of round 1, 1 Chill on the wielder. Once per fight. | "1 Chill (slowed)" (`:304`) | Weapon card | Caveat: "slowed" is wrong. |

### E6. Catalysts (with the rewritten rules of weapons forged since rules 2)

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Capacitor [name] (Binding Salt) | `CombatContent.kt:308-309,340` | When the wielder is healed (effective healing from anyone, Regeneration ticks included): 1 Charge. Once per round. Brings the Charge burst. | "When the wielder is healed, the wielder gains 1 Charge." (`:309`) | Weapon card, material tip | "Charge on heal". Modern word for a fantasy item. |
| Rune of answer [name] (Runestone Shard) | `CombatContent.kt:310-311` | Once any party weapon has cracked this fight: when an enemy breaks an ally's Guard (any ally, wielder included), 5 damage to that enemy. Once per round, at most twice per fight. | "Rune of answer" (`:310`); note omits the limit (`:233`) | Weapon card | "Retaliation". |
| Dragon oil (old) [name] | `CombatContent.kt:312-313` | Weapons forged before rules 2: when the wielder gives Burn or Chill (not a tick) to anyone, the wielder included: 1 Burn on every enemy. Once per round. | "Dragon oil" (`:312`) | Weapon card of an old weapon | Same display name as the new rule below. |
| Dragon oil (new) [name] | `CombatContent.kt:320-321` | Weapons forged since: when the wielder gives Burn (not a tick): 1 Burn on every enemy, the first target included. Once per round. | "Dragon oil" (`:320`); material "Dragon Oil" | Weapon card | "Spreading fire". |
| Void ink (old) [name] | `CombatContent.kt:322-323` | Old weapons: at round end (after that round's Burn already ticked), remove up to 3 Burn from the priority target. Once per round. | "Void ink" (`:322`) | Old weapon card | Same display name as the new rule. |
| Void ink (new) [name] | `CombatContent.kt:336-337` | New weapons: after the wielder acts, remove up to 3 Burn from the attack target. Once per round. | "Void ink" (`:336`) | Weapon card | "Consume Burn". |
| Void ink (Guard) [name] | `CombatContent.kt:324-325` | When the wielder removes Burn from an enemy (any STAT_SPENT of Burn the wielder is the source of: Void ink, and also a Chill-on-Burn cancel into Steam): that much Guard to the weakest ally. Once per round. | "Void ink (Guard)" (`:324`) | Weapon card | Burn-to-shield conversion. |
| Steam lance [name] (Dragon Oil) | `CombatContent.kt:327-328` | When the wielder gains Steam: spend 1 Steam, 5 pierce damage to the attack target and 5 pierce to the back enemy (both to the same enemy when only one stands). Once per round. | "Both ignore Guard." (`:328`) | Weapon card | Original. Accurate. |
| Steam veil [name] (Void Ink) | `CombatContent.kt:329-330` | When the wielder gains Steam: spend 1 Steam, 3 Guard to every ally. Once per round. | "every ally gains 3 Guard" (`:330`) | Weapon card | "Barrier". |

### E7. Signatures with a fight effect

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Thornwall [name] | `CombatContent.kt:348-349` | When an enemy breaks the wielder's Guard: Thorns equal to half the Guard that blow removed, rounded up, at most 6 (Thorns cap 8). Once per round. | "gains Thorns equal to half of the broken Guard" (`:349`) | Weapon card | Thorns. |
| Stormcaller [name] | `CombatContent.kt:351-352` | When any ally (wielder included) spends Charge: 3 storm damage to every enemy. Once per round. | "When an ally spends Charge" (`:352`) | Weapon card | "Thunderclap" (AoE). |
| Mourning Rod [name] | `CombatContent.kt:353-354` | When the first enemy goes down: a Spectral helper (10 health, Strike 3, move Haunt) joins at the back of the party if fewer than 4 stand on the party side (`Fight.kt:359`, `Model.kt:95`). The limit is used even when there is no room. Not the fallen enemy. Not on the last enemy (the fight is already won). | "The first enemy to fall rises as a spectral helper" (`:354`) | Weapon card | D&D "Animate Dead" / Diablo "Raise Skeleton" raise the corpse: a trap. "Summon a spirit". |
| Winterwake [name] | `CombatContent.kt:355-356` | Own attack damages a chilled enemy: remove up to 2 Chill from it, 5 Guard to the weakest ally. Once per round. | "Winterwake" | Weapon card | Shatter-to-shield. |
| Dawnbrand + Dawnbrand (Guard) [name] | `CombatContent.kt:358-361` | At round end (after Burn ticked): remove up to 2 Burn from the priority target. When the wielder removes Burn: that much Guard to every ally. Each once per round. | "Dawnbrand (Guard)" (`:360`) | Weapon card | Burn-to-shield. |
| Nightletter [name] | `CombatContent.kt:362-363` | When any direct hit of the wielder (move or effect) consumes a Mark: 5 grave damage to that enemy (x70% on Hollowbound). Once per round. | "When the wielder's hit consumes a Mark" (`:363`) | Weapon card | Mark payoff. |

### E8. Party relics

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Overflow Basin [name] | `CombatContent.kt:368-369` | When an ally is healed past full (Regeneration included): the overflow, at most 4, becomes Guard on that ally. Once per round per member. | "the extra healing becomes Guard" (`:369`) | Relic card, opening | "Overheal shield". |
| Salvage Bell [name] | `CombatContent.kt:370-371` | When an ally's weapon cracks: that ally spends 2 Scrap, every ally gains 3 Guard. Once per fight per member. | "Salvage Bell" | Relic card | Original. |
| Storm Ledger [name] | `CombatContent.kt:372-373` | When an ally spends Charge: 1 Regeneration to the weakest ally. Once per round for the party. | "Storm Ledger" | Relic card | Original. |
| Furnace Lung [name] | `CombatContent.kt:374-375` | When an ally removes Burn from an enemy (Void ink, Dawnbrand, and also a member's Chill landing on a burning enemy, which cancels Burn into Steam): 2 Guard to every ally. Once per round for the party. | "When an ally removes Burn from an enemy" (`:375`) | Relic card | Original. |
| Bone Music Box [name] | `CombatContent.kt:376-377` | Same mechanic as Mourning Rod (first enemy down, Spectral helper). Once per fight for the party. Separate limit, so both together can call 2 helpers. | "rises as a spectral helper" (`:377`) | Relic card | See Mourning Rod. |
| Blood Receipt [name] | `CombatContent.kt:378-379` | Once any party member has paid health this fight: healing an ally (effective healing) also gives that much Guard, at most 4. Once per round per member. | "Blood Receipt" | Relic card | Original. |
| The Blunt Oath [name] | `CombatContent.kt:380-381` | After a member with a Common/Uncommon weapon or none acts: 3 damage to the attack target and 2 Guard to that member. Once per round per member. | "A member with a finer weapon gets nothing from it." (`:381`) | Relic card | Original. |
| Widow's Whetstone [name, pact] | `CombatContent.kt:382-383` | After a member acts: pays up to 2 health (floor 6), 3 damage per health paid to the attack target. Once per round per member. | "A pact." (`:383`) | Relic card | "Blood price". |
| Oathbreaker's Coin [name, pact] | `CombatContent.kt:384-385` | After a member acts: 3 Guard and 1 Bleed to that member. Once per round per member. | "A pact." (`:385`) | Relic card | Original. |
| Coward's Medal [name] | `CombatContent.kt:386-387` | No fight effect. Read by quests: a retreat keeps 50% of the stage gold. | n/a | Relic card | n/a |

### E9. Member traits, specialities, bonds and the oath (guild fights)

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Protective [name] | `GuildContent.kt:419-420` | When an enemy's damage leaves any party member alive at 50% health or less: 3 Guard to that member. Once per round. | "When an enemy's hit leaves a party member (this one included)" (`:420`) | Member card, hero tip | "Guardian angel". |
| Impatient [name] | `GuildContent.kt:421-422` | Start of round 1, before anyone acts: 65% Strike (with element) to the attack target. Once per fight. | "deals 65% of their strike damage" (`:421`) | Member card | "First strike" (MTG) / "Ambush". |
| Cowardly [name] | `GuildContent.kt:423-424` | Starts each fight with 4 Guard. Retreat keeps 34% of stage gold. | "Starts every fight with 4 Guard." (`:423`) | Member card | Accurate. |
| Greedy [name] | `GuildContent.kt:425` | No fight effect (gold only). | n/a | Member card | n/a |
| Methodical [name] | `GuildContent.kt:426-427` | When this member's direct hit (move or effect) consumes a Mark: 3 more damage. Once per round. | "on top of the Mark's own bonus" (`:427`) | Member card | Mark payoff. |
| Loyal [name] | `GuildContent.kt:428-429` | When an enemy downs an ally: 100% Strike (with element) to that enemy. Once per fight. | "deals this member's full strike damage" (`:428`) | Member card | "Avenger" / "Vengeance". |
| Storm-Touched [name] | `GuildContent.kt:430-431` | Starts with 1 Charge, carries the Charge burst whatever the weapon, and is tagged `charge` (the Battlemage's Opening sigil can feed them). | "can use Charge with any weapon" (`:430`) | Member card | Accurate in effect, jargon in words. |
| Pain Collector [name] | `GuildContent.kt:432-433` | When an enemy's direct hit damages this member: 2 Thorns. Once per round. | "they gain 2 Thorns" (`:433`) | Member card | Thorns. |
| Rust Savant [name] | `GuildContent.kt:434-435` | When any party weapon cracks: 6 damage to the attack target. Twice per fight. | "Twice per fight." (`:435`) | Member card | Original. |
| Overhealer [name] | `GuildContent.kt:436-437` | When this member's own move heals an ally past full: 1 Regeneration to that ally. Once per round. | "past full health" (`:437`) | Member card | Accurate. |
| Second Wind [name] | `GuildContent.kt:438-439` | The first time any damage (ticks included) leaves this member alive at 34% or less: heal 10. Once per fight. | "Second Wind" | Member card | D&D Fighter "Second Wind" (self-heal once per rest). A good match. |
| Oathkeeper [name] | `GuildContent.kt:440-441` | At the start of every round, with a Common/Uncommon weapon or none: 2 Guard. | "gains 2 Guard at the start of every round" (`:441`) | Member card | Accurate. |
| Vanguard / Striker / Careful hand [name] | `GuildContent.kt:464,465,469`; applied `guild/GuildOps.kt:64,73-74` | Health%, Strike%, Support% changes (110/95, 90/110, Support 120 Strike 95). | "+10% health. -5% strike damage." (`:464`) | Member card, speciality choice | Accurate. "strike damage" vs "Strike". |
| Unbroken [name] | `GuildContent.kt:467-468` | The first time damage leaves this member alive at 25% or less: 8 Guard. Once per fight. | "Unbroken" | Member card | D&D "Relentless Endurance" prevents dropping to 0: a trap. "Last stand". |
| Wallwarden / Rallier [name] | `GuildContent.kt:471-474` | Start of round 1: 5 Guard to self / 2 Guard to every ally. Once per fight. | "Starts every fight with 5 Guard." (`:472`) | Member card | "Rally" for Rallier. |
| Comrades / Protector (bonds) [name] | `GuildContent.kt:491-493` | Only beside the bonded member: Mark on the priority target at round 1 start / 4 Guard to the first member an enemy leaves at 50% or less. Once per fight. | "When fighting beside a comrade" (`:491`) | Member sheet bonds line | Accurate. |
| Oath of inexperienced company [name] | `GuildContent.kt:315-316`; `guild/GuildOps.kt:67-68` | Sworn weapon, every party member level 3 or lower: every ally starts with 3 Guard and 1 Regeneration. Once per fight. | "inexperienced" (`:316`) | Weapon card | Accurate. |

### E10. Fight-changing laws, preparations and modifiers (starting stacks)

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Storm front / Eclipse (laws) [name] | `GuildContent.kt:478-480` | Quests only, 3 days. Storm front: both sides start with 2 Wet. Eclipse: every enemy starts with a Mark, every member with 1 Bleed. | "Burn applied to a Wet enemy cancels the Wet and gives Steam" (`:478`) | Quest board, quest sheet | "Weather" / "World modifier". |
| Preparations [name] | `GuildContent.kt:551-568` | Shield wall: raiders start with 5 Guard. Knives for the back: grunts are thieves. A gravecaller behind the line: adds a Gravecaller. Grave mist: defenders 2 Bleed, enemies 1 Regeneration. A second drake: adds a drake. Banked fires: defenders 2 Burn, enemies 2 Regeneration. | "Shield wall" (`:551`) also an enemy move name (`CombatContent.kt:417`) | Siege outlook | Accurate. |
| Modifiers [name] | `GuildContent.kt:572,574,576-578` | Shields up: enemies start with 4 Guard. Tired sentries: enemies start with 1 Chill. Hold the ford: HOLD 5 rounds. One of the watch is held: protect unit. | "Every enemy starts with 1 Chill." (`:574`) | Quest card | Accurate. |

## F. Classes and enemies

### F1. Classes

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Guardian [label: class] | `CombatContent.kt:143-148,167-169` | Tank: Hold the line, Riposte, `intercept` tag. HP 40/+3, Strike 4/+1, Support 4/+1. | role `CombatContent.kt:168`; card title "Hold the line" + source "class" (`GuildInspect.kt:45`) | Member card, roster (`GuildUi.kt:537`), hero tip | Fighter/Paladin tank (D&D), "Guardian" (generic). Collides with the affix "Guardian's". |
| Warden [label: class] | `CombatContent.kt:149-151,170-172` | Healer: Mend. HP 32/+2, Strike 4/+1, Support 6/+1. | role `:171` | Same | Cleric/Healer. "Warden" in WoW/LotRO is a tank: a mild trap. Collides with "Bone warden", "Wallwarden", "Wardens of the March". |
| Battlemage [label: class] | `CombatContent.kt:152-158,173-175` | Opening sigil, Bolt, Echo. HP 26/+2, Strike 6/+1, Support 2/+0. | role `:174` | Same | Wizard/Sorcerer. Accurate. |
| Ranger [label: class] | `CombatContent.kt:159-161,176-178` | Mark and loose. HP 28/+2, Strike 6/+1, Support 2/+0. | role `:177` | Same | Ranger (D&D). Accurate. |
| Duelist [label: class] | `CombatContent.kt:162-164,179-181` | Double cut. HP 30/+2, Strike 5/+1, Support 2/+0. | role `:180` | Same | Rogue/Swashbuckler. Accurate. |

### F2. Enemy units and moves (names are identity, keep; labels and move words are audited)

Enemy labels shown on cards: "Enemy · Leader · Elite" only (`GuildInspect.kt:113-114`). Elite portraits for elite or
leader (`replay/ReplaySupport.kt:44`). No enemy has an element (`Loadout.kt:62-65`). Hollowbound and Embermaw tick
resistance as in B. Enemy strike is a range, rolled per action.

| Concept | Defined at (file:line) | Exact mechanic per code | Display names/synonyms in use (one example file:line each) | Where shown (screens/systems) | Familiar RPG candidate(s) and caveat |
|---|---|---|---|---|---|
| Leader / Elite / summoner / undead [label] | tags `CombatContent.kt:102-105`; `Fight.kt:327` | Leader: priority target after a summoner, Rout trigger. Elite: Giant Slayer. Anchor ("summoner"): first priority target. Undead: Undead Bane. | "Leader", "Elite" (`GuildInspect.kt:114`); "summoner" (`CombatContent.kt:131`); "Hollowbound" (`:283`) | Enemy tip | Boss/Champion, Elite, Summoner, Undead (Diablo). |
| Ashclaw scout / brute / thief [name] | `CombatContent.kt:402-405` | Slash, Club: 100% Strike to the attack target. Cut and run: 100% Strike to the back member, ignoring the Guardian. All carry Rout. 12/22/14 HP, 3-5/5-8/4-6. | "Cut and run" (`:404`) | Enemy tip, replay | Trap: "Cut and run" suggests the thief leaves. It never does. |
| Rout [name] (Ashclaw passive) | `CombatContent.kt:394-395` | When an enemy-side ally tagged leader goes down: each holder gains 2 Chill. Once per fight. | "each raider loses heart" (`:395`) | Enemy tip | Trap: "Rout" means they flee. They stay and fight weaker. |
| Ashclaw warchief / Warlord Krag [name] | `CombatContent.kt:406-412` | War cry (round 2, 5, 8): 4 / 5 Guard to every enemy instead of attacking. Cleave: 100% Strike to one target. Reave: 100% Strike to the attack target, then 50% to the back member. Leader, Elite. 36 / 70 HP. | "War cry" (`:407`); "Cleave" (`:408`) | Enemy tip | Trap: "Cleave" means hitting several targets (D&D weapon mastery, Diablo, WoW). Here one target. Diablo "War Cry" is a damage buff: mild trap. |
| Hollowbound shambler / Bone warden [name] | `CombatContent.kt:415-418` | Claw: strike. Shield wall: 3 Guard to self, then strike. | "Shield wall" (`:417`) | Enemy tip | Same name as the preparation with a different rule. |
| Gravecaller [name] | `CombatContent.kt:419-422` | Raise the fallen (round 3, 6): summons a new Hollowbound shambler if fewer than 4 stand. Knit bone: heal Support (5) to its weakest ally, then strike. Anchor, undead. | "raises a Hollowbound shambler" (`:420`) | Enemy tip | "Raise the fallen" implies a corpse: it is a fresh unit. |
| The Pale Knight [name] | `CombatContent.kt:423-424` | Grave weapon: 100% Strike (no element), then 1 Regeneration to self. Elite, undead. | "Grave weapon" (`:423`) | Enemy tip | Trap: suggests grave damage or Bleed. Neither. |
| The Hollow King [name] | `CombatContent.kt:425-428` | Summon the court (round 2, 5, 8): summons a shambler. Crown of ash: strike, then heals self 4. Anchor, leader, elite, undead. 76 HP. | "Crown of ash" (`:427`) | Enemy tip | Accurate. |
| Embermaw whelp / Cinder-knight [name] | `CombatContent.kt:431-433` | Nip: strike, then 1 Burn on the attack target. Ember guard: 4 Guard to self, then strike. | "Ember guard" (`:432`) | Enemy tip | "Ember guard" deals no fire. |
| Embermaw drake [name] | `CombatContent.kt:434-437` | Fire breath (round 3, 6): 130% Strike to every member, no element, no Burn. Bite: 3 Guard to self, then strike. Elite. | "breathes fire on the whole party" (`:435`) | Enemy tip, siege prep | Trap: "Fire breath" applies no Burn and no fire element. |
| Broodmother Vyrash [name] | `CombatContent.kt:438-441` | Inferno (round 3, 6): 150% Strike to every member, then 1 Burn on each. Rend: 4 Guard to self, then strike. Leader, Elite. 84 HP. | "Rend" (`:440`) | Enemy tip | Trap: "Rend" is a bleed in WoW/Diablo. Here Guard + strike. |
| Supply cart / Captive / Watch guard / Spectral helper [name] | `CombatContent.kt:399,444-446` | Cart and captive do nothing (protect objectives). The watch guard strikes (Halberd). The helper: Haunt = Mark on the priority target, then strike. | "Rolls on", "Keeps moving", "Haunt" | Quest card, replay | Accurate. |

## Mechanics absent (item 7)

General evidence: the only random draw in a fight is an enemy's strike inside its own range (`Fight.kt:20,150`), so
nothing can miss, dodge, block by chance or crit. `damage()` has no chance or avoidance step (`Fight.kt:417-463`).
`act()` always performs the first fitting move of a standing actor (`Fight.kt:149-162`), so nothing skips a turn.
`Combatant` has no armor, defense, evasion or speed field (`Model.kt:103-127`). The `Stat` enum is the full list of
statuses (`Model.kt:16`).

| Mechanic | Present? | Evidence | Closest existing mechanic | Trap if the familiar word were used |
|---|---|---|---|---|
| Armor (flat per-hit reduction) | No | no field in `Model.kt:103-127`; Guard is a pool (`Fight.kt:438-447`) | Guard (a depleting pool, not per hit); tick resistance (Burn/Bleed % only) | Calling Guard "Armor" would promise reduction on every hit that never runs out. |
| Block chance | No | no RNG outside the strike roll (`Fight.kt:20`) | Guard absorbs deterministically | "Block" reads as a chance in Diablo/WoW. Slay the Spire "Block" is deterministic but expires each turn, Guard does not. |
| Critical hit | No | as above | Mark (+3 once, `Fight.kt:433-436`); the replay's "big hit" is a display filter only (`Replay.kt:102,262`) | none in text today. |
| Dodge / evasion / miss | No | no avoidance step in `damage()` | Guardian intercept redirects single-target attacks (`Fight.kt:322`) | Riposte (D&D: answers a miss) implies misses exist. |
| Poison | No | not in `Stat` (`Model.kt:16`) | Bleed is mechanically Slay the Spire Poison (`Fight.kt:139-148`); Burn is the Guard-blocked version | "Verdant" heals, it is not the poison element. |
| Stun | No | no skip in `act()` | none. Chill weakens the next attack but never skips it | Frost "freezing" or "stunning" would be false. |
| Freeze | No | as above | Chill | as above. "Winterwake" and "Rime" are flavour only. |
| Weaken (outgoing damage %) | Partly | Chill: -35% on the first damage step of the next move, 1 stack per move (`Fight.kt:154-160`) | Chill | Weak in Slay the Spire lasts N turns. Chill lasts N attacks. "Slowed" in our text is wrong. |
| Vulnerable (incoming damage %) | No (flat only) | Mark +3 once (`Fight.kt:433-436`), Wet +2 storm once (`:429-432`), element weakness x130% permanent (`:425`) | Mark, Wet, weakness | D&D "Vulnerable" is x2. Our weakness is x1.3. |
| Taunt | Partly | `intercept` + Guard redirects `Aim.Foe` only (`Fight.kt:322`) | Guardian intercept | A full taunt would also pull area and back-line attacks. Thief, Reave's second hit, Fire breath and Inferno ignore it. |
| Lifesteal | No | Vampiric heals 6 on a kill (`CombatContent.kt:280`) | "life on kill" | The name "Vampiric" implies lifesteal. |
| Counterattack | Conditional | Riposte, Bronze rings back, Rune of answer: only when Guard breaks (`CombatContent.kt:146,208,310`); Thorns: next enemy move hit (`Fight.kt:457`); Loyal: ally downed (`GuildContent.kt:429`) | those five | No "whenever attacked" counter exists. |
| Additional attack | Yes, as effects | Duelist's two steps (`CombatContent.kt:163`); Second arrow 35%, Off-hand cut 45%, Swift 30% after acting (`:193,196,281`); Impatient 65% before round 1 (`GuildContent.kt:422`) | those | They are effect damage: they carry the element and consume Marks, but do not trigger on-hit effects, are not weakened by Chill and do not set off Thorns. D&D "Extra Attack" is a full attack. |
| Damage reduction % | Only element resist and tick resist | x70% element (`Fight.kt:426`); 50% ticks (`Model.kt:120-121`); Chill reduces the attacker's output | resists, tick resistance | none. |
| Resistance % | Yes, enemies only | `Fight.kt:426`; heroes have none (`Loadout.kt:50-55`) | "Resists" | D&D resistance is x0.5. |
| Defense stat | Not inside a fight | `defenseMultiplier` is read only by the power formula (`battle/Power.kt:66-72`). In a guild run that formula still ranks who stands on the wall (`guild/SiegeFight.kt:127` sorts non-reserved defenders by `Power.defensePower`), but nothing in `Fight.kt` reads it | none in the fight | "+10% defense on the town's wall" (`LaunchContent.kt:150`) changes who is picked for the wall, not how the wall fight goes. Cross-scope: for the crafting audit. |
| Initiative / speed | No | fixed slot order (`Fight.kt:61-62,87-90`) | none | "Swift" and "slowed" both suggest speed. |

## Display names taken from an enum `.name` or a code identifier (item 8)

| Where | Code | What the player sees |
|---|---|---|
| Weapon card "Element" row | `detail/WeaponStatCard.kt:139` `it.name.lowercase().replaceFirstChar` | "Fire", "Verdant"... from `Element` |
| Material, enemy and family tips | `ForgeUi.kt:581` `Element.word() = name.lowercase()...`, used at `InspectUi.kt:141,153`, `GuildInspect.kt:140-141` | "Frost", "Weak to: Sun" |
| Element effect source name | `crafting/WeaponRules.kt:64` `it.name.lowercase()...` | heading "Ember · element" source "Fire" in `alsoFrom` |
| Hero element taste | `detail/HeroDetailSheet.kt:166` `it.name.lowercase()` | "Favors fire weapons" |
| Rarity on cards | `InspectUi.kt:223`; `GuildInspect.kt:39,98` | "Rare", "Uncommon" |
| Wound kind at the siege | `guild/SiegeFight.kt:172,201` `kind.name.lowercase()` | "injured until day 12", "X was exhausted." |
| Rule name fallback to its ID | `Replay.kt:134` `result.rules[it] ?: it`; `replay/SiegeReplay.kt:408`; `Account.kt:156` | "core_iron" if a record lacks the name (normally never) |
| Tick share fallback | `Account.kt:155` `getOrDefault(rule)` | "#ticks:BURN" only if `Stat.valueOf` failed (never today) |
| Statuses | never from `.name`: explicit maps `Fight.kt:372-375`, `Replay.kt:107-110`, `InspectUi.kt:73-76` | three maps, one disagrees ("Regen") |
| Outcomes, pop kinds, step kinds | explicit maps (`Replay.kt:281-286`, `FightReport.kt:27-35`) | n/a |

## Inconsistencies

1. **One affix, two unrelated rule sets on the same card.** The "Buffs"/"Flaws" rows show the power-system text
   (`LaunchContent.kt:149-167`, rendered `detail/WeaponStatCard.kt:145-152`) and "Effects in a fight" shows the engine
   effect (`WeaponStatCard.kt:153`, `CombatContent.kt:274-304`). Examples: Vampiric "heals 12 health after each won fight
   away from town" (`LaunchContent.kt:154`) vs "recovers 6 health" on a kill (`CombatContent.kt:280`). Brittle "may
   break when the wielder loses" (`LaunchContent.kt:162`) vs "cracks the first time an enemy breaks the wielder's Guard"
   (`CombatContent.kt:289`). Also: `Loadout.weaponStrike` applies every affix `attackMultiplier` with no field check
   (`guild/Loadout.kt:29-31`), so "+X% attack in fights away from town" also raises Strike at the guild siege. "Defense" is never read
   inside a fight. It only feeds `Power.defensePower` (`battle/Power.kt:66-72`), which in a guild run ranks who is
   picked for the wall (`guild/SiegeFight.kt:127`). Cross-scope, for the crafting audit.
2. **Same display name, two mechanics (by forge version).** "Void ink": round end, priority target
   (`CombatContent.kt:322`) vs after acting, attack target (`:336`). "Dragon oil": answers Burn or Chill
   (`:312-313`) vs Burn only (`:320-321`). An old and a new weapon show identical names with different rules.
3. **Two names, one mechanic.** Mourning Rod (`CombatContent.kt:353`) and Bone Music Box (`:376`). Obsidian cracks
   (`:213`) and Brittle (`:288`). Iron holds 3 / Reinforced 4 / Wallwarden 5 / Cowardly 4 start Guard
   (`:206,285`, `GuildContent.kt:472,423`). Sentient (at home) and Rallier (`:301`, `GuildContent.kt:474`). Fine as
   items, but the shared mechanic has no shared label ("Starting Guard").
4. **Regeneration vs "Regen".** `Replay.kt:108` shows "Regen" on replay chips and pops; `Fight.kt:373`,
   `InspectUi.kt:74`, `Account.kt:154` say "Regeneration".
5. **Retreat vs "Pulled back" / "breaks off".** Replay end banner "Pulled back" (`Replay.kt:284`) against the locked
   term (`docs/GDD_REVISION_2026-10-14.md:32`), "Retreated after" (`FightReport.kt:31`), "breaks off" (`GuildUi.kt:215`).
6. **Enemy vs "foe(s)" / "Besiegers".** "stacks put on foes" (`Account.kt:188`), "stacks on foes"
   (`replay/SiegeReport.kt:185`), "Besiegers" (`replay/SiegeReplay.kt:207`), "The enemies" (`FightReport.kt:118`).
   "Foe" was retired by the GDD revision (`docs/GDD_REVISION_2026-10-14.md:35`).
7. **Effect vs "rule".** "their weapon's rule fires" (`replay/SiegeReplay.kt:280`), "a rule of this weapon."
   (`replay/SiegeReplay.kt:408`), "Stored for a rule that spends it." (`replay/ReplaySupport.kt:81`). Locked term:
   Effect (`docs/GDD_REVISION_2026-10-14.md:34`).
8. **Status vs "state" vs "stack".** "A state ticks" (`replay/SiegeReplay.kt:361`), "No states on X now."
   (`:414`), "From states or Guard" (`replay/SiegeReport.kt:112`), "one stack fades" (`replay/ReplaySupport.kt:74`),
   glossary "Status effect" (`docs/WRITING_GUIDE.md:119`).
9. **Heal vs mend vs recover.** "mends 3" (`replay/ReplaySupport.kt:76`), "health mended" (`replay/SiegeReport.kt:108`),
   "health the party mended" (`Account.kt:205`), "recovers 6 health" (`CombatContent.kt:280`), the Warden's move "Mend"
   (`:150`), and "mended" for weapon repair (`GuildContent.kt:515`).
10. **Strike as stat, verb and noun; "strike damage".** Stat "Strike" (`GuildInspect.kt:79`); "strikes that enemy back
    for half its strike" (`CombatContent.kt:147`); "A heavier strike" (`:187`); "+10% strike damage"
    (`GuildContent.kt:465`); Chill "next strike" (`replay/ReplaySupport.kt:77`) vs "next attack" (`CombatContent.kt:227`).
11. **"Hit" means two things.** Nightletter "the wielder's hit consumes a Mark" fires on effect hits too (no
    `FromAction`, `CombatContent.kt:362-363`), Starsteel "own attack consumes a Mark" only on move hits (`:216-217`).
    Same payoff, two scopes, near-identical words. The short label "On hit" (`InspectUi.kt:105`) means own move damage
    past Guard only.
12. **"Grave chill" applies Bleed** (`CombatContent.kt:268`), while Chill is a different status. The Grave element's
    effect is named after the Frost status.
13. **Shield wall** is a Bone warden move (3 Guard to self + strike, `CombatContent.kt:417`) and a siege preparation
    (every raider starts with 5 Guard, `GuildContent.kt:551`).
14. **Warden / Guardian collisions.** Class Warden, enemy "Bone warden" (`CombatContent.kt:417`), speciality
    "Wallwarden" (`GuildContent.kt:471`), rank "Wardens of the March" (`:486`). Class Guardian and affix "Guardian's"
    (`CombatContent.kt:284`), which only works on a Guardian anyway.
15. **Ember** names the Fire element effect (`CombatContent.kt:265`), the augment Ember Resin, the Cinder-knight move
    "Ember guard" (Guard + plain strike, `:432`) and the Embermaw faction.
16. **Giant Slayer text vs check.** "an elite enemy or an enemy leader" (`CombatContent.kt:282`) and "An elite or a
    leader among the enemies." (`crafting/Capabilities.kt:402`), but the condition is `TargetTagged(elite)` only.
17. **Weakest ally vs weakest foe.** Weakest ally = lowest health percentage (`Fight.kt:316`), `Aim.WeakestFoe` = lowest
    absolute health (`Fight.kt:325`, unused). Harmless now, a trap if content uses it.
18. **Two resistances.** Element "Resists" = -30% direct hits (`Model.kt:85`); tick resistance = -50% Burn or Bleed
    ticks, shown as "Resisted" on the pop too (`Replay.kt:180`).
19. **Home-only label.** "Only on the town's wall" (`InspectUi.kt:232`) vs "Only when defending the town"
    (`detail/WeaponStatCard.kt:170`) vs effect names "Sentient (at home)" / "(on the road)" (`CombatContent.kt:301,304`).
20. **Source words.** A family's rule shows source "weapon" (`crafting/WeaponRules.kt:31`), the family tip says "Weapon
    type" (`InspectUi.kt:175`); an affix shows source "buff" while the glossary term is "Property"
    (`docs/WRITING_GUIDE.md:58`).
21. **Counting words in the report.** "in 4 activations" (`Account.kt:209`) vs "fired 4 times"
    (`replay/SiegeReport.kt:186`).
22. **Siege vs quest outcomes.** The glossary and the retreat-rule sheet say a time-out "counts as a retreat"
    (`GuildUi.kt:215`); true on quests (`guild/Missions.kt:299`), but at the siege a time-out is "Held at a cost" and a
    retreat is a breach (`guild/SiegeFight.kt:222-226`). The siege uses the Balanced rule (`:213`), which no siege text
    says. A Long Assault siege lasts 12 rounds (`:211`), which its text (`content/Depth.kt:97`) does not say.
23. **Status colours read as buff/debuff but Mark is red** (`replay/ReplaySupport.kt:62`) although Mark only harms the
    holder indirectly. Minor.

## Writing problems (combat text)

| # | file:line | Current text (verbatim) | Problem type | Suggested rewrite (same mechanics, timing, stacking, limits, numbers) |
|---|---|---|---|---|
| 1 | `CombatContent.kt:147` | "When an enemy breaks this Guardian's Guard, the Guardian strikes that enemy back for half its strike. Once per round." | pronoun ambiguity, inconsistent term | "When an enemy breaks the Guardian's Guard, the Guardian deals 50% of the Guardian's Strike as damage to that enemy. Once per round." |
| 2 | `CombatContent.kt:168` | "Gains Guard every round, then attacks. While the Guardian has Guard, enemies attack it instead of the front member. When an enemy breaks its Guard, it strikes back." | unexplained (amount missing), overclaim, pronoun | "Every round, the Guardian gains Guard equal to its Support, then attacks. While the Guardian has Guard, enemy attacks aimed at one member hit the Guardian instead of the front member. Attacks on the whole party or on the back member are not redirected. When an enemy breaks the Guardian's Guard, the Guardian deals 50% of the Guardian's Strike to that enemy. Once per round." |
| 3 | `CombatContent.kt:174` | "In the first round, gives 1 Charge to the first ally whose weapon has a Charge burst, puts a Mark on ${PRIORITY_TARGET} and attacks for 70% of its strike. Then attacks every round. When any enemy gains Burn, Chill or Wet, adds 1 more. Once per round." | compressed, incomplete (Storm-Touched), limit ambiguous | "In round 1, the Battlemage gives 1 Charge to the first ally who has a Charge burst (from a weapon or the Storm-Touched trait), puts a Mark on the priority target, then attacks for 70% of its Strike. That attack usually consumes the new Mark. In later rounds, it attacks for 100% of its Strike. Once per round, when an enemy gains Burn, Chill or Wet, the Battlemage gives that enemy 1 more." |
| 4 | `CombatContent.kt:291` | "The wielder starts each fight with 1 Chill (slowed): its first attack deals 35% less damage." | inaccurate jargon ("slowed"), pronoun, colon fragment | "The wielder starts each fight with 1 Chill, so the wielder's first attack deals 35% less damage." |
| 5 | `CombatContent.kt:304` | "Away from home the weapon balks: on a quest, the wielder starts each fight with 1 Chill (slowed), so its first attack deals 35% less damage." | flavour mixed into a rule, "slowed" | "On a quest, the wielder starts each fight with 1 Chill, so the wielder's first attack deals 35% less damage." (Flavour "The weapon balks away from home." in the item's flavour line.) |
| 6 | `CombatContent.kt:268` | name "Grave chill" (effect: "puts 1 Bleed on that enemy") | inconsistent term | Rename so the name does not say Chill, e.g. "Grave wound". Description unchanged. |
| 7 | `CombatContent.kt:354` | "The first enemy to fall rises as a spectral helper for the party. Once per fight. The helper leaves when the fight ends." | implies a mechanic that does not exist (the enemy itself rising), missing room rule | "When the first enemy goes down, a spectral helper (10 health, 3 Strike) joins the back of the party, if fewer than 4 fighters are standing on the party's side. Each round, the helper puts a Mark on the priority target, then attacks. Once per fight. The helper leaves when the fight ends." |
| 8 | `CombatContent.kt:282` | "When the wielder's attack damages an elite enemy or an enemy leader, the weapon deals 4 more damage to that enemy. Once per round." | text promises more than the check | "When the wielder's attack damages an Elite enemy, the weapon deals 4 more damage to that enemy. Once per round." (Every leader is Elite today.) |
| 9 | `CombatContent.kt:210` | "Holds a charge. The wielder starts every fight with 3 Charge, enough for one Charge burst." | timing unexplained | "The wielder starts every fight with 3 Charge. If the weapon or the wielder has a Charge burst, the burst fires at once, before anyone acts in round 1." |
| 10 | `CombatContent.kt:190` | "95% strike. Reaches past Guard." | compressed, overclaim | "95% Strike. Once per round, when an enemy's Guard blocks the wielder's attack, half of the blocked damage (rounded up) goes through." |
| 11 | `CombatContent.kt:349` | "...The next enemy whose attack hits the wielder takes damage equal to the Thorns. Once per round." | incomplete (all consumed, Guard-blocked hits count) | "...When an enemy's attack next hits the wielder, even if Guard blocks all of it, that enemy takes damage equal to all the wielder's Thorns, and the Thorns are used up. Once per round." |
| 12 | `replay/ReplaySupport.kt:73` | "Blows hit Guard before health." | vague, incomplete | "Blocks damage before health, 1 point per point of damage. Burn is blocked. Bleed and damage that ignores Guard are not. Guard does not fade between rounds. At most 14." |
| 13 | `replay/ReplaySupport.kt:74` | "At the end of each round: loses 1 health per stack, then one stack fades." | inaccurate (Guard blocks Burn), fragment | "At the end of each round, the fighter takes 1 damage for each Burn, then loses 1 Burn. Guard blocks this damage." |
| 14 | `replay/ReplaySupport.kt:76` | "At the end of each round: mends ${rules.regenHeal}, then one stack fades." | ambiguous (per stack?), inconsistent verb | "At the end of each round, the fighter heals 3, then loses 1 Regeneration. More Regeneration makes it last longer, not heal more." |
| 15 | `replay/ReplaySupport.kt:77` | "Its next strike is ${rules.chillPercent}% weaker and uses up one stack." | pronoun, "strike" vs attack, incomplete | "The fighter's next attack deals 35% less damage and removes 1 Chill. Only the first hit of that attack is weakened. Healing and Guard are not." |
| 16 | `replay/ReplaySupport.kt:78` | "The next direct hit on it deals ${rules.markBonus} more and uses the Mark." | pronoun, missing targeting rule | "The party attacks a marked enemy first. The next direct hit on the marked fighter deals 3 extra damage and removes the Mark." |
| 17 | `replay/ReplaySupport.kt:79` | "Storm hits on it deal ${rules.wetStormBonus} more. One stack dries each round." | incomplete (consumed by the hit, Burn cancels) | "A storm hit on the fighter deals 2 extra damage and removes 1 Wet. Burn put on a Wet fighter cancels the Wet into Steam. Loses 1 Wet at the end of each round." |
| 18 | `replay/ReplaySupport.kt:80` | "The next blow it takes hurts the attacker for this much, once." | vague ("blow"), incomplete | "When an enemy's attack next hits this fighter, even if Guard blocks it, that enemy takes damage equal to all the Thorns, and the Thorns are used up. Effects, Burn and Bleed do not set it off." |
| 19 | `replay/ReplaySupport.kt:81` | "Stored for a rule that spends it." | machine-like, "rule" | Charge: "Stored. A Charge burst spends all of it at 3 or more for 8 storm damage." Steam: "Gained when Burn and Chill or Wet cancel. Steam lance and Steam veil spend it." Scrap: "Left by a cracked weapon. The Salvage Bell spends it." |
| 20 | `Replay.kt:284` | "Pulled back" | inconsistent term | "Retreated" |
| 21 | `Replay.kt:208-211` | "Mark struck", "Wet struck", "Chill boiled off", "Chilled · weaker" | jargon, compressed | "Mark +3", "Wet +2", "Chill cancelled into Steam", "Chill: -35%" |
| 22 | `Account.kt:188` | "${m.name}'s ${weapon}: ${s.name} left ${s.applied} of the $put stacks put on foes, in ${s.times} activations." | machine-like, "foes" | "Mira's Ashen Sword: Ember put 4 of the party's 9 status stacks on enemies. It triggered 4 times." |
| 23 | `replay/SiegeReport.kt:112` | "Shared: 12 damage. From states or Guard more than one hand put on, so the record cannot give it to one defender." | machine-like, "states", "hand" | "Shared: 12 damage. It came from Burn, Bleed or Guard that more than one defender gave, so it is not credited to one defender." |
| 24 | `replay/SiegeReport.kt:108` | "Damage dealt, damage their Guard took for the wall, and health mended. Each point counts once." | pronoun ambiguity, inconsistent verb | "Damage dealt to enemies, damage blocked by Guard this defender gave, and health this defender healed. Each point is counted once." |
| 25 | `replay/SiegeReplay.kt:361` and `Fight.kt:450` | "A state ticks"; log "${who} hits ${t.name} for $n." with sentence-shaped names, e.g. "Mira's Bronze rings back hits Ashclaw scout for 5." | vague; machine-like | Banner: "Burn deals damage" (name the status). Log: "Bronze rings back: Mira's weapon deals 5 damage to Ashclaw scout." (or give metal effects noun names) |

Notes on the rewrites: all keep the code's numbers (`FightRules` and `CoreNumbers` defaults), use numerals, no dashes or
semicolons, and name actor and target as `docs/WRITING_GUIDE.md:10-19` asks. Rows 13 to 19 should stay generated from
`FightRules` as today.

## Notes for the proposal (constraints a rename or rewrite will meet)

- **Ability rows are cut from the description text.** `ruleParts` (`InspectUi.kt:33-45`) takes the first sentence's
  opening clause up to the first comma after When / Whenever / While / At the start of / At the end of / Before /
  After / If as the trigger, and a last sentence that starts with Once / Twice / ... and ends in "per round." or
  "per fight." as the limit. A rewritten description must keep its trigger clause free of commas up to its end and
  put the limit last, or the row splits in the wrong place. "Once per round for each member." is not recognised as a
  limit today (it does not end in "per round"), so it stays in the body.
- **Stored fights keep their old words.** Event `text` (`Model.kt:55`), `Highlight.text` (`Model.kt:179`) and the
  rule names in `FightResult.rules` (`Model.kt:192-193`) are written when the fight is resolved and saved. Renamed
  effects and reworded log lines appear only in new fights. "Key moments" (`FightReport.kt:102-106`, built in
  `Highlights.kt:47-49` from the log sentences) and the full timeline will show old wording for old fights. Status
  words on replay chips and pops come live from `FightReplay.word` (`Replay.kt:107-110`), so those change everywhere.
  The replay's RECENT strip (`ReplayTimeline.kt:53-58`) reuses pop texts and notes (row 21 above), not log sentences.
- **Tests gate the wording.** `ProseGuardTest` (no em/en dashes, no semicolons, no generic "blade") and
  `LaunchContentTest` (no percentage beside chance wording) run over catalog text (`docs/WRITING_GUIDE.md:18-20`,
  `docs/GDD_REVISION_2026-10-14.md:19`).
- **IDs stay.** Effect IDs are stored in saves and records (`CombatContent.kt:98`, `Model.kt:172-173`). Rename the
  `name` and `description`, never the `id`.

## Counts

- Concept rows audited: 15 stats and damage words, 17 statuses and fighter states, 15 trigger, timing and targeting
  concepts, 3 element concepts, 5 classes, 1 enemy-label row: 56 concepts.
- Named effects and moves audited with their code: 9 class moves and passives (incl. Charge burst), 3 family effects
  (+ 3 family patterns), 6 metal effects, 6 element effects, 18 affix and flaw effects (incl. Shatter and both
  Sentient rules), 9 catalyst effects (incl. 2 old versions), 7 signature effects, 10 relics, 12 traits, 6
  specialities, 2 bonds, 1 oath, 1 enemy passive (Rout), 23 enemy moves (War cry counted once): 113 named effects.
  Plus 2 laws, 6 siege preparations and 4 quest modifiers that change how a fight starts.
