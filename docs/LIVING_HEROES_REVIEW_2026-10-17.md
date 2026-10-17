# Review: "Living Heroes, Autonomous Agendas, and Run Variety" (v1.0 draft)

**Reviewer:** Claude (Fable 5.1), 17 October 2026, against branch `ui-polish-post-terms` (last commit a81e43b), GDD v2.0,
rules 7 / content 6 / balance 16 / schema 7.
**For:** the owner, and the ChatGPT session that will fold this into the final design document.
**Perspective asked for:** game designer, RPG systems, dungeon master, game director. The goal under review is
"the game is interesting on every run".
**Evidence:** GDD v2.0 read in full for §1–4, §6–12, §21; two code audits of `core/` (hero model, market, guild,
combat, content, persistence); five claims spot-checked by hand (`Model.kt:449 residents()`, `Commands.kt:69
ChooseSpeciality`, `Effects.kt:75 Aim.WeakestFoe`, `CombatContent.kt:382 DOWNED → Summon`, the `Hero` field list).
Everything else cited as "per audit of <path>".
**Out of scope here:** playable races, race name pools and the enemy roster. The owner is writing those in a separate
content bible. Section 9 lists what that bible needs to know about the engine so its content is buildable.

---

## 1. Executive verdict

**Keep the direction. Rewrite the document around the smith's decisions, not the heroes' intelligence.**

The proposal is a good *simulation* document and a thin *game* document. It spends 25 sections on how a hero thinks and
about one paragraph (§4.3) on what the player does differently because of it. "Interesting on every run" is not produced by
heroes having more fields. It is produced by the player facing different decisions with different stakes. A hero who
intelligently buys the right weapon, trains the right speciality and proposes the right quest without needing the smith
is a very good NPC in a game the smith is watching.

The test every system in the final document must pass: **name the smith decision it creates or sharpens.** If it creates
none, it is spectator content and belongs in the Gazette, not in the engine. Section 2 applies that test to the proposal.

Second finding: the proposal was written against the v1.0 GDD and says so. Against the game as built, roughly half of
Stages 1–3 already exist in some form (generated heroes with name pools and portraits, 9 personalities with observable
weights, 4 finite ambitions, a shared purchase evaluator with stored reason codes and trade-in, one authoritative weapon
location, loans, 12 member traits with fight effects, deed-opened specialities, bonds, a nemesis that carries a seized
weapon into a recovery quest and to the wall, lineages). Section 3 maps it. The final document should be written as an
*extension* of these, or the implementation session will build a second hero system next to the first.

Third: two of the owner-confirmed points collide with shipped, deliberate rules (members never shop; the smith chooses a
member's speciality). Both are legitimate owner decisions. They must be made explicitly (GDD §22.3), with the
mitigations in §5.1 and §5.5, not inherited from a conversation.

**Strongest parts of the proposal:** §3 (the eight qualities of a "smart" hero, especially Limited knowledge and
Fallibility), §4.2 (commitments are reliable), §13.2 (switching friction), §15.1 (the four questions an opportunity must
answer), §22.1 (reasons from evidence, modest wording when evidence is mixed), §26.3's counterfactual test, §28's "a
polished slice beats dormant definitions".

**Serious flaws:** no player-decision model (§1 above); duplicate systems against the shipped game (§3); a generic
utility pipeline and ten new interfaces where the repository already has the resolvers (§9, §23); personality as
adjective lists rather than hooks that generate situations (§5.2); roster validation that risks flattening the variety
it is meant to protect (§5.6); nothing that gives personality teeth (§5.8); enemy mechanics listed without the engine's
grammar, so half the table is engine work disguised as content (§5.11).

---

## 2. The knife: which smith decision does each system create?

| Proposed system | Smith decision it creates or sharpens | Verdict |
|---|---|---|
| Generated identities, cultures, names, portraits | None directly. Makes heroes recognisable, which makes every other decision feel personal | Keep, cheap, but it is seasoning |
| Personality traits as utility weights | None new. Shipped already (9 personalities, 12 member traits) | Do not add a third list (§5.2) |
| Hooks: a debt, a grudge, a lost blade, an oath (my proposal, §5.2) | Which member's situation do I spend the one party on this week? | **Add.** This is the generator of situations |
| Autonomous equipment replacement | Do I lend fine steel or sell it? Do I accept the returned loan or counter-offer? | Keep, but as an *ask*, not a silent swap (§5.1) |
| Autonomous speciality | Removes one (ChooseSpeciality). Restores influence only if the lean is announced a day ahead | Keep with the lean (§5.5) |
| Personal goals (multi-day) | Which goal do I back, and at the cost of which siege job? | Keep by extending Ambition (§5.3) |
| Hero-created opportunities | A board card I can take, postpone or let lapse against the siege jobs | Keep, as board cards and visitors only (§5.4) |
| Relationships (trust, rivalry, mentorship) | Who do I send together? Who do I not? | Keep small: bonds exist, add grudge and rivalry (§5.7) |
| Memory and decision records | None. Diagnostics and Gazette material | Keep bounded, invisible by default |
| Roster validation | None. Protects against a dead start | Reduce to two guards (§5.6) |
| Enemy roles and siege strategies | Which weapon do I forge for Tuesday, and which member stands | **Add.** This is where preparation decisions live (§5.11) |
| Departure forecast (my proposal, §5.8) | Do I act on this member's complaint in three days or let them go? | **Add** to the slice; the proposal defers it |

Five rows create decisions. Those five are the game. The rest are support.

---

## 3. What already exists (per audit of `core/src/main/kotlin/com/tinyblacksmith/core/`)

| Proposal item | Today | Classification |
|---|---|---|
| §6 identity, foundation, disposition, preferences | `model/Model.kt Hero`: id, name, surname, classId, appearance, level, xp, gold, health, loyalty, fame, kills, victories, `traits: List<TraitId>`, `elementTaste`, `ambition`, lineage fields, shop memory (visits, purchases, wants, reason codes). No race, no relationship field, no stat block (power is class + level) | Extend |
| §7 generation and name pools | `heroes/Heroes.generate`, `heroes/Names.kt`: 120 first names, 96 surnames in one pool, a first name never repeats in an era, no living hero shares a surname, surnames reserved for lineages and founders. `heroes/Appearance.assign`: 20 class-tagged portraits, fewest-worn first | Extend with cultures; keep the uniqueness rules |
| §11 personality | 9 town personalities with weights in `content/Content.kt TraitDef` (activity, shop, price sensitivity, novelty, loyalty gain, combat ×0.97–1.05); 12 member traits in `content/GuildContent.kt` each with a fight effect or a quest number (Cowardly 34 % on retreat, Gold-Hungry 15 % share, Avenger, Second Wind, …) | **Two lists already.** Do not add a third |
| §12 expertise | Guild only: `guild/Stories.openBranches` opens a pair by deeds (3 quest wins → Vanguard/Striker; downed twice → Unbroken/Careful hand; Defender ambition → Wallwarden/Rallier). `Command.ChooseSpeciality` is the smith's choice. Townsfolk have none. Level adds numbers only | Extend to per-class pairs; move the choice to the hero |
| §13 equipment intelligence | `market/Market.evaluate` is already the shared evaluator (gain, class fit, taste, novelty, loyalty, collector, fame, worn, counters-threat, noise, price × personality). Reused by the merchant, the guild heir, demand and the simulator. Reason codes stored per visit (`VisitReason`, 16 values). Trade-in at 40 % is the only replacement. Heroes never sell, discard or trade among themselves | Reuse. Add attachment and switching friction as terms |
| §13.3 ownership | `WeaponLocation`: Storage, Shelf, Owned(hero, equipped), Loaned(member), Lost, Destroyed. One location at all times (LOCKED). A loan outranks the member's own blade (`GuildOps.weaponOf`) | Reuse unchanged |
| §14 goals | `Ambition` (Slayer, Defender, Collector, Fortune) with finite conditions, daily "ambition work", fame/loyalty/reputation on fulfilment. `Want` (3-day buying wish) | Extend Ambition. Do not add `PersonalGoal` |
| §15 opportunities | Board cards posted "for a reason": Rescue (a captive), Recovery (the nemesis's blade), siege jobs. Visitors with a hero behind them: a blade on credit, the debt repaid, a cracked heirloom, the coward returns, the sword files a complaint | Two channels exist. Add hooks as a source; no third inbox |
| §16 relationships | `GuildMember.bonds` (≤ 2): Comrades after 3 quests won together, Protector after a rescue, with fight effects. Town: `mentorName`, shared `guildId`, lineage | Extend bonds with Grudge and Rivalry |
| §16 memory | Counters on `Hero` and `GuildMember` (missions, wins, timesDowned, drivenBackOnDay), weapon history lines (bounded), reason codes per shop visit | Extend; keep bounds |
| §17 store loop | Shipped for townsfolk. Members never shop by rule (`Model.kt:449` excludes them from `residents()`) | Owner decision (§5.1) |
| §20 death and succession | `Battle.kill` fates (recovered / seized / heir 60 % / merchant / lost), nemesis on a seized loan, mentee on retirement, lineage anchor at era end | Reuse |
| §21 enemies | 3 factions, 15 units, typed effects, 11 statuses, summons, enemy healing, Thorns, Armor and intercept, per-effect limits, two preparations per faction, 4 fighters a side. No interrupt, no return from down, no ally-protects-ally redirect, no attack-speed buff, no poison | Half content, half engine (§5.11) |
| §23 technical organisation | Resolvers `Heroes`, `Market`, `GuildOps`, `Stories`, `Missions`, `Encounters`, `Consequences`, `SiegeFight`; streams `HEROES`, `PURCHASES`, `GUILD`, `ENCOUNTERS`, `COMBAT` | Do not add the ten interfaces. Extend these |
| §24 day order | GDD §4.3, pinned by the golden-state test | Insert, do not reorder |
| §25 persistence | Whole `GameState` as JSON, schema 7, defaults need no step, a new stream needs a migration entry (steps 4 and 5 did this) | As stated |

---

## 4. Rules marked LOCKED that this direction touches

Replace the proposal's §1.2 authority order with this table. The GDD (§22.3) requires an explicit owner decision for each
LOCKED change; "the owner's latest decisions in the conversation" is not a record anyone can audit later.

| LOCKED rule (GDD v2.0) | Touched by | Change? | Recommended ruling |
|---|---|---|---|
| Classic era: shop actions only, heroes fully autonomous (§3, §12.13) | Owner point 6 "send heroes on quests and provide weapons" | **No.** Point 6 *is* the guild era as built | Classic gets identities and hooks (visible in the shop and Gazette), nothing else |
| Guild members never shop (§7.2, §12.1) | §13, §17 "replace through the shop" | **Yes, if the owner wants it** | Members buy only at the smith's counter, with their share gold (§5.1) |
| The smith chooses a member's speciality (`ChooseSpeciality`, 2026-10-10 revision) | §12 autonomous expertise | **Yes** (owner-confirmed) | Hero chooses; the lean is announced the day before (§5.5) |
| Three defenders by automatic recommendation, reservable in a guild era | — | No | — |
| Dead heroes never act or own equipment | §20 | No | Keep; the proposal agrees |
| One authoritative weapon location | §13.3 | No | Keep; the proposal's table matches it |
| Gazette and replays from real records | §22 | No | Decision records feed them |
| No runtime AI, timers, idle production | §3 | No | The proposal agrees |

---

## 5. Design critique

### 5.1 Autonomy versus agency: the loan is the smith's lever

In the guild era the loan is the strongest thing the smith does for a member, and "a loan outranks the member's own
blade" is what makes it matter. If members shop freely and swap silently, three things go wrong at once: the loan loses
its meaning, the smith's armoury competes with the member's purse, and a weapon the smith was counting on at the wall is
gone on the morning of the siege.

Recommended rule, if the owner opens member shopping at all:

1. **Members buy only at the smith's counter.** Their share gold (10 %, 15 % Gold-Hungry) finally has a sink, and every
   purchase is a sale. The town's shelves and seats are unchanged.
2. **A member who owns a better fit than their loan asks to return it.** The ask is a line in the morning roster and an
   entry on the hero sheet ("Would rather fight with her own axe: Frost against Ashclaw"). The smith can recall the loan
   (agree), counter-offer a different loan, or leave it (the member keeps fighting with the loan; loyalty −1 after three
   ignored asks). Nothing swaps without the smith seeing it. This gives the proposal's "Likely to replace" read for free.
3. **Switching friction is a term in `Market.evaluate`, not a cooldown.** Attachment: +fame of the weapon, +1 per siege
   held with it, +a recovered Lost blade. The proposal's "minimum evaluation interval with exceptions" will produce exactly the
   obviously-wrong refusals it warns against.
4. **Loadouts are frozen when a party leaves** (already the rule). Keep it; it answers §13.4.

For townsfolk nothing changes. They already trade in, keep a spare and leave a want.

### 5.2 Personality: one system, built from hooks, not adjectives

A D&D table does not remember a character for being "Cautious, Loyal". It remembers the paladin who owes a debt to the
thieves' guild. Character is a want that conflicts with something, plus a hook the DM can pull. The proposal's twelve
trait rows are weights with guardrails: better evaluators, same spreadsheet.

The game already has two lists: 9 town personalities (observable weights) and 12 member traits (a fight effect each).
The member trait is the right model, because a player can read it as a rule: "Cowardly: keeps 34 % on a retreat". Do not
add a third list. Instead:

| Layer | Today | Final document |
|---|---|---|
| Personality (temperament) | 9, weights on activity, shopping, price | Keep. Shows on the sheet in one word |
| Trait (fight effect) | 12, members only | Keep. Extend per class if the content bible wants |
| Drive | 4 ambitions with finite conditions | Extend to 6–8 (§5.3) |
| **Hook** | none | **New.** One per hero: a situation that generates board cards and visitors |

A first hook set that maps onto systems the game already has:

| Hook | What it is | Opportunity it generates | How it resolves |
|---|---|---|---|
| **Grudge** | Downed, captured or orphaned by a named enemy or faction | A Hunt card against that faction with the member as sponsor; the elite is named | Won once: the grudge becomes a deed. Ignored for 10 days: fades to a personality note |
| **Debt** | Owes gold to a named townsperson (or the rival company) | A visitor: pay it for them (loyalty), or ignore (the creditor takes a board card, like the rival) | Paid or defaulted; a default forecasts departure |
| **Lost blade** | A family weapon is Lost (a real `WeaponLocation.Lost` with a known holder) | A Recovery card (exists for the nemesis); or a Cracked Heirloom visitor (exists) | Recovered: the member equips it with attachment. Destroyed: a grudge |
| **Oath** | Will not wield X / will only wield Y / will not retreat while an ally stands | A constraint on loans and retreat rules, shown on the recruit card | Broken by the smith: loyalty hit; kept to a siege: a deed |
| **Kin** | A sibling or parent among the townsfolk or candidates, generated as a pair with one surname (the one stated exception to "no two living heroes share a surname") | Recruiting one makes the other eager; losing one forecasts departure | Both alive at the charter siege: a bond |

Five hooks, each already has a surface (board card, visitor, loan rule, recruit card). This is the "why this hero, why
now" the proposal asks for in §15.1, without a planner.

### 5.3 Goals: extend Ambition; fail forward

The proposal's `PersonalGoal` with a six-state lifecycle duplicates `Ambition`, which already has a start (generation),
finite completion conditions, daily pursuit and a payoff. Extend it:

- Add 2–4 ambitions with stakes tied to guild play: *Avenger* (defeat the elite that downed you), *Heirloom* (recover
  the family blade), *Captain* (lead three parties that lose nobody), *Name* (a weapon you wield gains a title).
- Ambitions can **fail forward**: Heirloom's blade destroyed becomes a Grudge; a dead target makes Avenger retarget the
  faction. Never "abandon after an interval" silently. The DM rule is that a failed quest changes the story, it does not
  delete it.
- Keep the one-ambition-per-hero cap. The proposal's own §6.1 says so and then describes eight goal families.

### 5.4 Opportunities: board cards and visitors, nothing else

The game has two intake channels the player already reads: the quest board (posted for a reason: Rescue, Recovery, siege
jobs) and the morning visitor. A hero-created opportunity is **a board card with a sponsor** or **a visitor with a face**.
Do not add a third inbox or a request list. The proposal's §15.4 budget ("one prominent offer on many days, at most a few
unresolved") is then automatically enforced by the board's 3 slots and 3-day life, and by the one-visitor-a-morning rule.

The cheapest stakes in the whole design are already there: **there is one party.** A member's Grudge hunt competes with
the Sabotage job and the Starfall site. Say this in the final document; it is the answer to "does the player lose
anything by saying yes".

Keep from the proposal: revalidate on acceptance (the sponsor may be dead or away), cooldown by hero and family, a
declined card does not reappear under a new ID.

### 5.5 Speciality: hero chooses, announces the lean a day ahead

Owner-confirmed, so keep it, but say plainly what it costs: a shipped player choice is removed from a game that is light
on them. The mitigation that preserves influence without a selection dialog:

- When a branch pair opens (deeds, as today), the roster line says **"Maren is leaning to Vanguard: she has held the
  line in four quests."** The choice is made at the *next* End Day.
- The evidence is the smith's doing: which blade was loaned, which quests, which retreat rule. A Striker loan and a Hunt that
  day can swing a close lean. The reason line names what swung it.
- Evidence contributions are capped (the proposal's §12.2 is right): one point per quest, not per round.
- Members first. Townsfolk fight on the scalar resolver, where a speciality is a multiplier nobody sees. A townsperson's
  speciality can appear as **what they would bring if recruited** on the candidate card; that makes recruiting a decision
  about people rather than levels.
- Per-class pairs can come later from the content bible. Three pairs exist; the proposal's Appendix C names need engine
  checks (Spellbreaker needs interrupt, which does not exist).

### 5.6 Fair randomness: two guards, not a validator

The start is already protected: 12 townsfolk with one of each class, two charter members, candidates every 3 days that
favour a missing class, a day-5 first siege with a plain besieger. The proposal's roster validator (defence, damage,
equipment access, first-siege families, bounded attempts, fallback template) would be a second system doing the same job,
and it would quietly erase the odd starts that make a run memorable: the era with no Warden and a Battlemage who had to
learn to hold the line.

Keep two guards on generation (the charter members' traits are already content, named by the starting kit): the two
charter members get different hooks, and neither hook may forecast departure before the first siege. Then let recruitment be the recovery route, as it is now. Fairness is a *route* to a viable roster,
not a viable roster on day one.

### 5.7 Relationships: extend bonds

`GuildMember.bonds` already holds ≤ 2 directional links with effects (Comrades, Protector). Add **Grudge** (hero → enemy
or faction, from hooks and from being downed) and **Rivalry** (member ↔ member, from competing for the same speciality or
the same loan; a small effect: both +Attack when in the same party, neither retreats first). Keep the cap at 2. Do not
build a relationship store for townsfolk; `mentorName` and lineage are enough for the Gazette.

### 5.8 Give personality teeth: the departure forecast

The proposal defers refusal and desertion. Without any of them, personality is cosmetic: a member who is ignored forever
fights forever. The cheapest version with teeth, and the one a DM would use, is the **forecast departure**: a member whose
hook has been ignored three times, whose oath the smith broke, or whose kin died announces on the roster that they will
leave in three days unless something changes. Never mid-quest, never before a committed siege, always announced. The smith
can act (pay the debt, take the hunt, return the blade) or let them go with their own weapon; a loan they held returns
to storage the morning they leave (one location at all times).

This is one state (`leavingOnDay`), one roster line, one End Day step, and it makes every hook matter. Put it in the slice.

### 5.9 The classic era: identities and hooks, nothing more

Classic is LOCKED hands-off and fights on the scalar resolver. Give it the new identities and hooks (they show in the
shop, the Gazette and the lineage), nothing else. Every system built for both resolvers is built twice.

### 5.10 Presentation: no new surfaces

The owner judged the first UI cluttered. The game already has the surfaces this design needs: the morning roster line
(decision J), the hero sheet (`ui/detail/`), the recruit card, the quest board, the morning visitor, the shop-day cards
and the Gazette. Decision records feed those. The proposal's §18 "hero summary" is the hero sheet; §22.2's budget is
right. The final document should promise **zero new screens** for the slice.

### 5.11 Enemies: content where the grammar allows, engine work where it does not

Per audit of `combat/Effects.kt`, `combat/Fight.kt`, `content/CombatContent.kt`: effects are trigger (an event kind,
who) + conditions + cost + actions + limit; aims are fixed rules (Self, event source/target, weakest ally, all allies,
front/back/marked/priority/weakest enemy, all enemies); actions are Damage, Heal, Armor, Give/Take a status, Echo,
Fracture, Summon; statuses are Armor, Charge, Mark, Burn, Regeneration, Chill, Wet, Bleed, Scrap, Thorns, Steam; at most
4 fighters a side; every effect has a per-round or per-fight limit; abilities have cadences (always, opener, every n,
while holding, health at most).

| Proposed enemy | Buildable now | Needs engine work |
|---|---|---|
| Shieldbearer | Yes: Armor to all allies each n rounds (the Warchief's War cry does this) | A true "protects one ally" redirect does not exist; the Guardian's intercept is party-side only |
| War Drummer | Approximately: Give Charge or Mark to allies | No attack-speed or extra-action concept |
| Bloodhound | **Yes, and cheap:** `Aim.WeakestFoe` exists in the engine and no content uses it. Note the consequence: it picks by raw health and bypasses the Guardian's intercept, so this enemy ignores the tank by design | — |
| Powder Sapper | Partly: a unit whose survival adds forge damage (damage already scales with enemies left) | A per-round wall objective is new |
| Hookblade | Yes: Take Armor from the front member | — |
| Bone Legionary | Probably: `DOWNED` on self → Summon a weaker "risen" unit, once per fight. The grammar allows the trigger and the action; whether a downed actor's own effects still fire needs one check in `Fight.Run` | True return-from-down (clearing `downed`) is new |
| Grave Cantor | Exists (Gravecaller) | — |
| Rotbearer | Yes: Bleed on hit | No poison status |
| Lantern Wraith | Approximately: Armor every other round | No "vulnerable state" flag |
| Ossuary Giant | Yes: Armor + a heavy ability every 2 rounds | — |
| Briar Brute | Yes: Thorns | — |
| Sporecaller | Yes: heal weakest ally + Regeneration | — |
| Glasswing Swarm | **No:** 4 a side. A summoner that refills is the nearest | Raising the cap changes the siege's maths and the replay |
| Mire Leech | Yes: heal self on hit (Vampiric pattern) | — |
| Rootbinder | Approximately: Chill on the first member to hit it each round (`DAMAGE`, foe source, from an action, once per round). There is no "acted twice" condition | A count condition is new |
| Interrupt (several rows) | **No** | A new action that cancels the target's next ability; the first real engine addition worth making |

So a first batch of 6–8 roles needs no engine change if the bible is written in the engine's vocabulary. Two additions
are worth scoping separately: *interrupt* and *return once*. *Swarm* and *attack speed* should be dropped or re-expressed.

Two further points for the bible: GDD §21.4 leaves open that Ashclaw and Embermaw share Frost weakness and Fire
resistance; roles are the way to separate them without touching elements. And the current forecast in a guild era is
"besieger, field and preparation committed 4 days ahead" with the counters named; a new preparation per role pair fits
that exactly, so "Siege in 4 days: shieldbearers will cover the sappers" is content, not UI.

### 5.12 Measurement: two metrics the proposal lacks

`:core:guildsim` exists with 15 policies and a `--pair` mode. Add to the proposal's §26.3:

- **Influence:** the share of hero decisions (purchase, loan return, speciality, departure) where a smith action in the
  previous 3 days changed the outcome against the same seed with the action removed. Target: above half. If it is low,
  the game is a spectator game whatever the diversity numbers say.
- **Memorability proxy:** heroes per era with 3 or more distinct recorded deeds (titles, hooks resolved, bonds, siege
  holds). Target: 2 or more per 20 days.

Keep the proposal's counterfactual gate and its "top repeated event sequences".

---

## 6. Decision audit of the proposal, section by section

| § | Verdict | Reason |
|---|---|---|
| 1.2 Authority order | **Reject** | Replace with the LOCKED-change table (§4 here) |
| 2 Owner direction | Keep | Point 6 is the guild era; say so |
| 3 Smart heroes | Keep | The best section |
| 4.1–4.2 Contract, commitments | Keep | Loadouts already freeze |
| 4.3 Keep the player influential | **Revise** | Make it the organising principle (§2 here) |
| 5 Boundaries | Keep | Add "zero new screens for the slice" |
| 6 State model | **Revise** | Map onto `Hero`/`GuildMember`; drop Disposition sliders; add Hook |
| 6.1 Complexity cap | Keep | One ambition, one hook, ≤ 2 bonds, 2–3 personalities |
| 7.1 Generation pipeline | Keep | Extend `Heroes.generate`; portraits are class-tagged, not race-tagged |
| 7.2 Name data | Defer to the bible | Preserve the uniqueness rules; `LineageAnchor` needs a culture (§9) |
| 8 Fair randomness | **Revise** | Two guards and a recruitment route (§5.6) |
| 9 Decision architecture | **Revise** | `Heroes.activityWeights` and `Market.evaluate` are this pipeline; add terms, not a planner |
| 9.4 Contention | Keep | Seats and rotating priority already exist for the shop |
| 10 Knowledge | Keep | Public forecast plus own experience; already true for siege demand |
| 11 Personality table | **Replace** | Hooks (§5.2) |
| 12 Expertise | Keep with revision | Hero chooses after an announced lean; members first (§5.5) |
| 12.3 Expertise directions | Defer to the bible | Each needs an engine check |
| 13.1–13.2 Fit and replacement | Keep | `Market.evaluate` + attachment term; no cooldowns |
| 13.3 Ownership | Keep | Matches the shipped lifecycle |
| 14 Goals | **Revise** | Extend `Ambition`; fail forward (§5.3) |
| 15 Opportunities | Keep with revision | Board cards and visitors only (§5.4) |
| 16 Relationships | Keep small | Extend bonds (§5.7) |
| 17 Store loop | Owner decision | Members at the counter only (§5.1) |
| 18 Observation | Keep | The hero sheet is the surface |
| 19 Run identity | **Cut the theme layer** | Charter + starting kit + world condition already do this; the cast carries the theme |
| 20 Death and succession | Keep | Add the departure forecast (§5.8) |
| 21 Enemies | Keep with the mapping | §5.11 |
| 22 Explainability | Keep | Extend `VisitReason`-style codes; no `DecisionReason` type of its own |
| 23 Technical organisation | **Reject the ten interfaces** | Extend the existing resolvers |
| 24 Day order | Keep | Insert into GDD §4.3 steps 9, 10, 13, 16 |
| 25 Persistence | Keep | Save size past day 350 is already OPEN; bound everything |
| 26 Verification | Keep, add two metrics | §5.12 |
| 28 Stages | **Reorder** | Slice first (§7 here), then the stages |
| 29 Content scope | Revise | 5 hooks, 6–8 ambitions, 6–8 enemy roles, 0 new screens |
| App. A, B | Keep | Good traces; rewrite Maren's day 5 with the lean |
| App. C | Defer to the bible | — |
| App. E | Revise | Add the LOCKED-change rows |

---

## 7. Minimal vertical slice (guild era only)

One End Day shows all of it. Nothing here needs a new screen.

1. **Hooks:** each generated hero gets one of Grudge, Debt, Lost blade, Oath, Kin. Shown on the recruit card and the
   hero sheet. Classic era gets them too, as text.
2. **Sponsored cards:** a Grudge posts a Hunt against its faction with the member named; a Lost blade posts a Recovery
   (the nemesis pattern). Board slots and life unchanged, so they compete with siege jobs.
3. **Loan asks:** a member who owns a better fit asks to return the loan in the roster line; recall, counter-offer or
   ignore.
4. **Lean and choice:** branch pairs open as today; the lean is announced; the hero chooses next End Day with a reason.
5. **Departure forecast:** three ignored asks or a broken oath → "leaves in 3 days" → leaves with their own weapon.
6. **Enemy roles:** Bloodhound (weakest enemy), Hookblade (take Armor), Briar Brute (Thorns), Sporecaller (heal), Mire
   Leech (heal on hit), Rootbinder (Chill on the first to hit it), two per faction, plus one new preparation each that
   the forecast names.
7. **Measurement:** guildsim with the influence and memorability metrics; the counterfactual gate on hooks.

Deferred from the slice: member shopping (needs the owner's ruling), cultures (bible), interrupt and return-once (engine),
townsfolk specialities, rivalry.

---

## 8. Owner decisions needed (six)

1. **Do guild members shop?** Recommended: yes, at the smith's counter only, with their share gold; loans still outrank
   their own blade unless they ask and the smith agrees.
2. **The hero chooses the speciality** (removes `ChooseSpeciality`). Recommended: yes, with the announced lean.
3. **Classic era scope.** Recommended: identities and hooks only.
4. **Departure forecast in the slice.** Recommended: yes, bounded as in §5.8.
5. **Races and strength.** The proposal's §5 and §7.1 say race must not dominate strength or restrict class. If the bible
   gives races strengths and weaknesses, recommended: at most one small fight effect each (the size of a member trait),
   counted by the starting guards, never a multiplier on Attack.
6. **Engine additions.** Recommended: scope *interrupt* and *return once*; drop *swarm* and *attack speed*.

Everything else in the proposal can take a reversible default.

---

## 9. What the content bible needs from the engine

- **An enemy is:** health, an attack range, tags (leader, elite, anchor, undead, summoned), 1–3 abilities with a cadence
  (always, opener, every n rounds from round m, while holding a status, at health ≤ n %), passives (trigger + conditions +
  actions + limit), weakness and resistance by element, status resistances. Aims are fixed rules, never a free choice.
- **It can:** damage one or all, heal self or the weakest ally, give or take any of the 11 statuses, summon (to 4 a side),
  answer events (an ally downed, Armor broken, a hit taken, an attack made).
- **It cannot yet:** interrupt, come back from down, protect a specific ally, act twice, poison, exceed 4 a side.
- **Rosters:** siege leader, elite, 3 grunt slots per faction; 2 grunts + 1 per 3 sieges, capped at 3; preparations are
  content with a named counter.
- **Portraits** are class-tagged (20, with an upgraded variant). Race-appropriate faces need art before race is visible;
  until then race is a naming convention.
- **Names:** a first name never repeats in an era, no two living heroes share a surname, surnames are reserved for
  lineages and company founders. A culture must keep those rules. **Lineage descendants inherit the surname**, so
  `LineageAnchor` must carry the culture.
- **Terms** follow `docs/WRITING_GUIDE.md`: Armor (not Guard), Ability, Effect, Quest, Member, Speciality, Personality,
  Trait (a member's fight effect), Era, Challenge tier.

---

## 10. Technical notes for the implementation brief

- New fields on `Hero` (hook, culture) and `GuildMember` (lean, leavingOnDay, loan ask) take defaults; no schema step. A
  new RNG stream for hooks and opportunities needs a migration entry like steps 4 and 5.
- Day order: hooks and sponsored cards at GDD §4.3 step 16 (board), loan asks and leans at step 13 (reconciliation),
  departures at step 13 after retirements. Nothing moves.
- Bound decision records to the newest 10 per hero; save size past day ~350 is already an open item.
- Guildsim: a `--hooks off` baseline and the influence metric before any number is tuned; tuning seeds per the seed-block
  rule in DECISIONS.

---

## 11. Readiness and amendment log for the final document

**Ready to write into the final document:** §2 (the knife), §3 (reuse map), §4 (LOCKED table), §5.1–5.4, §5.8, §5.10,
§7 (slice), §9 (bible constraints).
**Waiting on the owner:** the six decisions in §8.
**Waiting on the bible:** cultures, name pools, races, the enemy roster written in the §9 vocabulary, per-class speciality
pairs.
**Waiting on one engine check:** whether a downed actor's own `DOWNED` passive fires (for "return once").

Amendments this review asks the final document to carry:

1. Replace §1.2 with the LOCKED-change table.
2. Insert "the smith decision test" as §4.3's first paragraph and apply it to every system.
3. Replace §11 with Hooks; fold the personality list into "two lists exist".
4. Rewrite §12 around the announced lean and members-first.
5. Rewrite §14 as an extension of Ambition with fail-forward.
6. Rewrite §15 as sponsored board cards and visitors.
7. Add the departure forecast to §20 and the slice.
8. Replace §21.1's table with the §5.11 mapping; move the roster to the bible.
9. Replace §23 with "extend these resolvers".
10. Add the influence and memorability metrics to §26.3.
11. Move the slice (§7 here) ahead of the stages.
