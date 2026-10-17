# Tiny Blacksmith: Living Heroes and Siege Variety

## Final implementation design, version 2.0

**Date:** 17 October 2026  
**Status:** Final design handoff for implementation, with explicit integration rulings and staged acceptance gates.  
**Companion content source:** `docs/Tiny_Blacksmith_Peoples_Names_and_Siege_Bestiary.md`  
**Implementation prompt:** `docs/Tiny_Blacksmith_Opus_Implementation_Prompt.md`  
**Supersedes:** `Tiny_Blacksmith_Living_Heroes_Design_Review.md` as the implementation specification. Keep the earlier draft and Claude's review as historical records.

> The smith creates the conditions for success. Heroes decide what to become, what they value, and which weapon they prefer. Personal situations compete with siege preparation for the same time, stock, money, and party. A memorable era comes from those decisions and their consequences.

## 1. Evidence and document authority

### 1.1 Sources examined for this final design

- The owner's explicit direction throughout this conversation, including autonomous development, equipment replacement, intelligent initiative, balanced randomness, and authored content.
- `LIVING_HEROES_REVIEW_2026-10-17.md`, supplied by the owner. Claude reports auditing branch `ui-polish-post-terms` at `a81e43b`, rules 7, content 6, balance 16, schema 7.
- The supplied GDD v2.0, which describes an earlier rules 6 / balance 15 snapshot and folds in the 10 October guild revision.
- `GDD_REVISION_2026-10-10.md`, the historical authorization for guild management alongside classic play.
- `WRITING_GUIDE.md`, including the owner-approved 17 October terminology overhaul.
- Both documents authored earlier in this conversation, including the full Content Bible.

**Evidence limit:** this finalization evaluates the supplied review and documents. It is not an independent code audit. Repository paths and behaviours below are attributed to Claude's review unless directly stated in the supplied GDD. The implementer must verify the working branch before editing. A path, version, line number, or audit claim is a discovery starting point, not permission to assume unchanged code.

### 1.2 Precedence

1. The owner's explicit requirements recorded in Section 3.
2. This final design's integration rulings, especially Sections 3, 12, 13, and Appendix A.
3. The Content Bible's authored identities, name pools, visual directions, motivations, and compatible rules.
4. Current approved game contracts, GDD, and Writing Guide where this final design does not explicitly revise them.
5. Historical proposals and review recommendations.

Section 20 records the owner's additional visual and UX requirement and applies to every milestone.

This precedence resolves actual conflicts. It is not permission to ignore existing invariants. Update the GDD and decision ledger with the explicit changes before implementing the corresponding behaviour. Do not ask the owner to reconfirm autonomous Speciality choice or member shopping: those were already requested directly. Distinguish those requirements from designer defaults chosen here, such as warning duration and shopping capacity.

For player-facing vocabulary, `WRITING_GUIDE.md` remains authoritative. Add any genuinely new implemented term there before shipping it. Keep save IDs and enum keys stable even when their display labels change.

## 2. Review verdict and corrections

Claude correctly identified the need to extend existing systems and make the smith's decisions the organizing principle. The first proposal overestimated missing infrastructure and the Content Bible defined combat semantics before the actual engine grammar was available. This document corrects both.

| Review finding | Final ruling |
| --- | --- |
| Much of hero generation, personality, shopping, ambitions, bonds, and ownership already exists | Accept. Extend the existing resolvers and state models. No parallel hero AI framework |
| Every major system should create or sharpen a smith decision | Accept, while allowing inexpensive identity and diagnostic support |
| Sponsored quests and visitors should use existing surfaces | Accept. No additional inbox, screen, or daily modal chain |
| Hero chooses Speciality after an announced lean | Accept. This is an owner requirement with a readable preparation window |
| Members should buy only from the smith using real personal gold | Accept as the first implementation boundary |
| A member may never return a loan without the smith's agreement | Reject. An indefinite veto contradicts the requested equipment autonomy. Use an announced, commitment-safe autonomous return |
| Three ignored asks, any oath break, or a kin death should trigger departure | Revise. Only persistent, actionable, clearly disclosed concerns can escalate. No automatic punishment for unavoidable bereavement |
| Hooks should replace the third personality list | Accept. Keep Personality, member Trait, Ambition, and one Hook distinct |
| Replace the current roster generator with a large validator | Reject. Preserve existing coverage and recruitment recovery. Add targeted constraints only |
| Grudge should occupy the same two bond slots as member friendships | Reject. Enemy/faction grudges belong in Hooks. Keep the sparse member-to-member bond cap |
| Rivalry should stop heroes retreating first | Reject. Personality does not secretly override the selected Retreat rule |
| Influence must exceed 50% of all decisions | Revise. Measure causal influence, but do not force arbitrary decision volatility to pass a percentage |
| Drop swarms because four fighters per side cannot support them | Reject for this bible. A swarm is explicitly one actor with one Health pool |
| Add attack speed to make Drummers different | Reject. Fixed action order remains. Drummers get a bounded Attack effect |
| Interrupt and return-once deserve explicit engine scopes | Accept. Poison also needs a small, explicit scope for the authored venom roster |
| Classic needs the same tactical systems | Reject. Keep its scalar resolver and hands-off contract |

## 3. Product decisions and explicit contract changes

### 3.1 Confirmed owner requirements

- Five existing base classes. No new class hierarchy is required.
- Heroes vary in race-appropriate identity, personality, build interests, and personal situation.
- Heroes choose earned Specialities themselves. The smith influences experience rather than clicking an upgrade.
- Guild members may buy weapons and may replace equipment previously provided by the smith.
- Heroes act coherently, have their own motives, and can propose opportunities.
- Forge, shop, people management, and guild preparation remain one game. Player decisions must matter.
- Different eras produce meaningfully different casts, development paths, and siege problems.
- Randomness must not decide victory or defeat at creation.
- Use the authored Content Bible instead of delegating its creative roster to the implementing agent.
- Automatic combat, offline operation, and player-controlled days continue.

### 3.2 Record these changes in the decision ledger

| Existing contract | Revised contract | Basis |
| --- | --- | --- |
| Guild members never shop | Eligible members at home can visit the smith's counter using their own purse | Explicit owner direction |
| Smith selects a member's Speciality | Hero selects after an announced lean, using actual evidence | Explicit owner direction |
| Loan always outranks personal equipment until manually recalled | Loan outranks personal equipment while active. Hero can end it after notice and safely equip a suitable owned alternative | Necessary implementation of owner equipment autonomy |
| A first name never repeats in an era and unrelated surnames never repeat | Prefer unused given names and reserve surnames for lineages. Hard guarantee is unique living full names, with explicit family sharing | Reconciliation of finite culture pools, kin, and endless eras |
| Classic versus guild control split | Preserved. New direct quest and loan controls remain guild-only | Existing owner contract |
| Four fighters per side, party order, and existing statuses | Preserved, with only narrowly specified additions | Engine-aware integration ruling |

The following are **designer defaults finalized here**, not invented historical approvals: one member counter visit per day, one full morning's loan-return notice, three planning days' departure warning, at most one active departure concern per roster, five initial Hook types, and the combat translations below. Store tunable numbers in `BalanceConfig` and adjust from evidence without changing the product contract silently.

### 3.3 What the smith decides

| Situation | Decision | Opportunity cost |
| --- | --- | --- |
| A member wants a different weapon | Forge a better fit, sell an owned alternative, offer a new loan, or accept the return | Materials, production energy, sale versus retained equipment |
| A Speciality lean is announced | Give relevant experience or equipment, or accept the developing role | Quest and defender availability |
| A sponsored quest appears | Support that person's goal or prioritize siege work | One active party and expiring board slots |
| A debt or concern becomes urgent | Help, choose a supported alternative, or let the member leave | Gold, stock, party time, replacement recruitment |
| A siege field is forecast | Forge counters, scout/sabotage, assign defenders, prepare recovery | Limited days and resources |
| A candidate has useful talents and a difficult Hook | Recruit, wait, or choose someone else | Fee, roster capacity, future obligations |

Names, portraits, and short histories make these decisions personal. They do not need artificial economic penalties merely to justify their existence.

## 4. Scope, modes, and compatibility

### 4.1 Guild era

Full member shopping, autonomous equipment returns, Speciality choice, Hooks, sponsored quests, bounded concerns/departures, relationship extensions, and tactical siege content. Keep one active party, up to three party members, up to six roster members, three defender places, and the current reservation system.

### 4.2 Classic era

New eras can use new identities, cultures, backgrounds, and Hook flavour through existing customer/visitor/Gazette systems. No member shopping lane, Speciality picker, party orders, defender reservation, or interaction-engine conversion. Do not attach guild-only action costs or departure warnings to classic townsfolk.

A classic Hook must have a supported classic consequence or remain a visible background fact. Do not show an unfulfillable guild quest request in classic play. Residents' ordinary expeditions remain scalar in both modes.

### 4.3 Existing saves

Use a small **era feature revision** to distinguish legacy eras from new eras. Default missing revision to legacy behaviour. New eras use the new revision. Do not switch an ongoing classic era's random sequence, identities, or resolver under the player.

Ongoing guild eras also default to legacy behaviour for the first delivery. They load and complete unchanged, including any pending manual Speciality choice. New guild eras use autonomous Speciality and shopping. If later opting existing guild eras into the feature is desired, design that migration separately. This avoids maintaining two generalized AI engines: only gate the changed command/phase behaviour and preserve legacy paths already present.

Global content versions must remain compatible with these saves using the repository's real compatibility mechanism. Merely adding a feature flag does not solve a loader that rejects an old content version. Inspect and test that mechanism explicitly.

## 5. Reuse map and minimum state extensions

Claude's audit reports these extension points under `core/src/main/kotlin/com/tinyblacksmith/core/`:

| Existing area | Extend with | Do not build |
| --- | --- | --- |
| `model/Model.kt Hero` | Race/culture references, background ID, one Hook, bounded experience evidence | A second independent hero record |
| `GuildMember` | Pending lean, loan-return intent, bounded concern/departure state | General-purpose planner state |
| `heroes/Heroes`, `heroes/Names.kt`, `heroes/Appearance` | Culture-aware generation and compatible appearance selection | Another generator next to `Heroes.generate` |
| `content/Content.kt TraitDef` | Reuse existing nine Personalities | A third temperament list |
| `content/GuildContent.kt` | Existing twelve Traits and six Specialities | Hidden new base classes |
| `market/Market.evaluate` | Attachment, switching friction, owned-item suitability, member budget context | Independent equipment power formulas |
| `guild/Stories` | Hook transitions, sponsored cards, automatic Speciality resolution | Duplicate `PersonalGoal` lifecycle |
| `Missions`, `Encounters`, `Consequences` | Sponsored quests and visitors with actual references | A third opportunity inbox |
| `GuildOps`, `SiegeFight` | Safe loan returns and new authored enemy definitions | New combat calculation inside UI |
| `combat/Effects.kt`, `Fight.kt` | Typed additions in Section 13 | A replacement round/initiative engine |
| `gazette`, `shopday`, `text` | Recorded decision reasons, concise notices | Fictional post-hoc motives |
| `SaveCodec`, invariants, simulators | Versioned defaults, migration, bounded evidence, paired tests | Unbounded decision and relationship histories |

A focused helper is acceptable when it removes real duplication. The rejection is of ten speculative service interfaces before verifying an integration need, not of ordinary modular code.

### 5.1 Suggested conceptual state

Names are illustrative and must adapt to existing serialization:

- `Hero`: `raceId`, `cultureId`, `backgroundId`, `hook`, bounded deed/evidence summary.
- `GuildMember`: pending Speciality decision with announced day/resolve-not-before day, loan-return intent with item references and notice day, one concern with due day and actionable remedy.
- `LineageAnchor`: culture and ancestry provenance when known.
- Run: era feature revision, any new deterministic stream state, generation reservations, bounded opportunity cooldown keys.

Keep the newest ten significant reason records per hero, plus compact aggregate counters required by mechanics. A Hook has one owner and one target reference set. The linked Ambition stores a Hook reference rather than a second conflicting target/progress counter.

## 6. Identity, races, names, and art

The Content Bible defines twelve races with 100 given names and 50 surnames each. Import those exact pools, validated for per-pool duplicates. Keep the race recruitment weights as initial weights, normalized over enabled content.

Race adds no combat bonus in this version. Every race may use every existing class. The name lists are content, not additional hero power. Personality, class, Trait, equipment, and experience create mechanical differences.

### 6.1 Names and families

Use a stable `raceId` and separate `cultureId`. Default to the matching culture. Persist chosen strings. Prefer a given name not used earlier in that culture during this era, then choose an available legitimate pair. When that finite pool is exhausted, allow reused given names with a distinct living full name. Do not invent numeric suffixes or accidental new names.

Reserve surnames for known lineages and founder records under existing conventions. Kin may explicitly share a surname and family ID. Cross-culture identical surnames do not establish kin. Avoid unrelated same-surname collisions when practical, but do not make a finite surname pool an infinite-era failure condition.

Hard invariant: no two active living heroes share a full display name. Use bounded retries and an authored byname fallback. If all legitimate combinations eventually exhaust, retire unused reservations for dead nonlegacy identities rather than looping forever. Preserve historically notable names and lineage associations.

### 6.2 Existing heroes and lineage

Do not infer ancestry from an old first name, surname, class, or untagged portrait. Existing records keep their exact names and appearance. Use an explicit legacy/unknown ancestry when necessary. Known descendants inherit culture and surname coherently. Do not rewrite historical titles or saved prose.

### 6.3 Missing art

The audit identifies class-tagged portraits, not verified race-tagged ones. Introduce appearance compatibility metadata. For newly generated races lacking matching art, use an intentionally neutral class silhouette or neutral placeholder with the correct race text, not an obviously mismatched face. No final art generation is required for implementation.

Keep existing imported portraits and upgrade variants intact. Once the owner supplies race-tagged art, enable matching selection without rerolling names or other identity. Record missing assets in the coverage ledger. The original race-gating rule is relaxed for this development milestone specifically so the full name/race content can be exercised with honest placeholders.

## 7. Personality, Traits, backgrounds, ambitions, and Hooks

### 7.1 Preserve distinct layers

| Layer | Role | Initial implementation |
| --- | --- | --- |
| Race/culture | Identity and naming | Twelve authored cultures |
| Background | Origin and opportunity evidence | Twenty authored background IDs, no free combat bonuses |
| Personality | Observable choice preferences | Existing nine, keeping their effects |
| Trait | Defining guild fight/quest effect | Existing twelve, keeping current approved names |
| Ambition | One measurable objective | Existing four plus four defined below |
| Hook | One personal situation producing opportunities | Five types below |
| Bond | Sparse member-to-member connection | Existing Comrades/Protector, optional Rivalry later |

The Content Bible's sixteen personality concepts are a creative backlog. Do not add all sixteen or translate them into invisible duplicate Traits. Map existing matching concepts first. New personalities need distinct tested behaviour before enabling them. Display the existing 2–3 Personalities in the optional hero sheet, not an invented single combined adjective.

### 7.2 Eight ambitions

Preserve Slayer, Defender, Collector, and Fortune under existing completion rules. Add:

| ID | Display | Completion | Failure/changed circumstances |
| --- | --- | --- | --- |
| amb_avenger | Avenger | Participate in a won sponsored Hunt against the Hook's actual enemy or faction | Named target dies elsewhere: offer one faction-linked continuation with notice |
| amb_heirloom | Heirloom | Recover the Hook's tracked family weapon through a valid ownership transition | Weapon destroyed: resolve the loss and offer a Grudge transition, not a recreated copy |
| amb_captain | Captain | Be first in the player's chosen party order for three won quests with nobody killed or captured | Failure does not erase earlier valid wins or change party order automatically |
| amb_name | Make a Name | Wield a tracked weapon when it earns a real Deed/title through existing rules | If the item is permanently unavailable, select one replacement goal openly or resolve the ambition |

No single event completes both a newly generated goal and an invented retrospective milestone. Existing deed evidence may legitimately open a Speciality, but a new ambition's progress starts at its actual activation unless explicitly migrated.

### 7.3 Five Hooks, concrete definitions

**Grudge.** Store an actual enemy/faction target, origin event, and last offered/accepted days. Generate a sponsored Hunt using the existing quest machinery. A fictional prior-life faction grievance may be authored at creation, but must be labelled background history and cannot falsely claim a recorded combat. Completion resolves the Hook to a deed. If no opportunity can be offered for ten days, resolve or change the grievance with a factual note. Do not convert an unavailable target into endless new vendettas.

**Debt.** A generated contractual debt of 40 gold to a named external creditor or actual NPC account, due no earlier than after the first siege. It is one obligation, not interest-bearing daily taxation. The member can pay from their own gold before discretionary shopping when due. The smith can pay it through an existing visitor choice. The ledger records the payment sink exactly once. No imaginary creditor steals unrelated quest cards. Failure can create an actionable concern with a repayment remedy, not instant disappearance.

**Lost Weapon.** A real weapon record in `Lost`, with a valid holder/recovery lead and Hook reference. Use existing nemesis/Recovery patterns. The item cannot simultaneously remain in storage or an inventory. At generation, either instantiate a bounded ordinary heirloom through a valid content path or select another Hook. Do not grant a powerful free weapon or create historical player ownership that never existed. A returned player loan returns to the player. A genuine hero family weapon belongs to the hero.

**Oath.** One explicit equipment preference constraint shown before recruitment: `plain_work` accepts Common/Uncommon weapons, `no_grave` avoids Grave-element weapons, or `favoured_family` uses a disclosed class-compatible family. Generate only when starting owned equipment and ordinary supply make the oath viable. No oath overrides combat Retreat rules or forces a hero to stay in a fatal fight. Incompatible loans are rejected before committing with a clear reason. They do not silently equip and punish the player later. Oaths create crafting/recruitment constraints, not a new hidden loyalty trap.

**Kin.** Two actual living heroes share a family ID, surname, and coherent background. Store valid IDs, not just names. The second may be a townsperson or candidate, but cannot be promised recruitment on a day when no slot permits it. A recorded loss may change motives and produce a memorial/recovery opportunity. Bereavement alone never schedules departure. Do not force family bonds into both existing bond slots or make nepotism a major stat bonus.

### 7.4 Generation and fairness

Preserve twelve townsfolk, existing class coverage, charter/opening composition, fixed initial Traits, and missing-class recruitment bias. Randomize identities and personal situations around those constraints, not every locked starting number independently.

The two charter members have different Hooks. Neither may begin with an actionable departure deadline before the first siege. Validate real references, affordable baseline equipment, oath viability, and recruit recovery routes. Do not repeatedly reroll the roster until an ideal Guardian/Warden/Damage party appears. Targeted invalid-state rejection is required, but a second sprawling roster optimization system is not.

## 8. Intelligent equipment and member shopping

### 8.1 Counter access

Town residents retain their existing fair seats and selection. Add **one member counter visit per day**, after ordinary residents within the existing step 6 market phase. This is an additional bounded guild visit, not an extra town-resident slot. Never add members back into `residents()` globally: that could also make them wander, retire, or receive resident activities.

Eligible member: alive, home, not captured, not in the departing planned party, not reserved for tonight's wall, and not otherwise activity-locked. The member chooses a visit only with a plausible need or standing want. Rotate among eligible interested members by least recent opportunity, then stable rotation. No daily guarantee of a purchase.

Spending uses the member's real gold, including actual quest shares, and transfers the sale price to the smith through existing market accounting. Preserve current trade-in at 40% where it already applies. Trade-in credits an owned weapon only, never a loan. Do not give extra gold, duplicate trade-in credit, or permit the same weapon to be both sold and equipped.

### 8.2 Shared fit and attachment

Extend `Market.evaluate`. Use actual class fit, weapon rules, condition, price, known threat, Personality, existing want, and current expertise. Add a bounded attachment term derived from actual shared milestones. Suggested initial components: +1 for a siege held with the weapon, capped at 3, +2 for recovering it, and a bounded existing Fame contribution. Calibrate these onto the real utility scale after inspecting it, not by dropping these numbers into an unrelated scale.

Switching friction is a score term, not a blind cooldown. A dangerous mismatch or large improvement can overcome attachment. High rarity or price alone is not sufficient. Do not count ten round-level procs as ten experiences. Hard legality and oath compatibility filter before weights and bounded noise.

### 8.3 Owned weapon versus loan

An owned weapon bought while a loan is active may become a spare without silently changing the loaned loadout. A member who owns a clearly preferable compatible alternative creates a **loan-return intent** with the specific owned weapon and principal reason. The earliest return is after one complete visible planning morning.

The smith can offer a better loan, sell a suitable alternative, recall immediately, or do nothing. Doing nothing does not indefinitely force the hero to wield the loan. Re-evaluate the intent at resolution: if its reason is gone, cancel it with a concise note. If still valid and safe, atomically return the loan to storage and equip the owned alternative. The member may not sell the loan or reclaim it from storage without a new loan command.

### 8.4 Commitment protection

A pending return cannot alter a frozen quest loadout or a defender reserved for an unresolved siege. It waits until the member is home and the protected commitment has resolved. Returning from a journey does not retroactively affect its final fight. A member with no owned legal alternative cannot automatically disarm themselves through this rule.

Resolve autonomous returns at existing reconciliation step 13, after activities and the siege, so the next morning's roster and forecast show the actual equipment. Intent notice is generated in an earlier step 13 and displayed on the following morning. Example: announce on morning 7, consider return during the End Day for day 7 after that day's commitments. No morning 7 surprise swap before an already planned departure.

### 8.5 Explainability

“Would rather use her own Frost spear against the coming siege. Your loan will return after today's commitments.”

Only use the element/threat explanation when the evaluator used that real fact. Avoid “her blade” when the weapon is not bladed. Keep one roster notice and optional detail, not repeated confirmation dialogs. A returned-loan event should be visible in the shop-day/Gazette record even if the roster notification was not opened.

## 9. Autonomous Speciality choice

### 9.1 Existing branches first

Use the six existing Specialities and actual effects: Vanguard/Striker, Unbroken/Careful hand, Wallwarden/Rallier. Do not invent numerical effects for them without reading their definitions. Preserve the existing deed-based opening conditions. Do not add class restrictions that make a previously valid earned choice impossible.

The earlier specialist concepts remain a later authored backlog. The Content Bible does not supply complete per-class Speciality pairs, so implementation must not pretend it does. The first full release of this feature can be complete using the existing six.

### 9.2 Announce, influence, choose

When a branch opens, persist eligible options, available evidence, provisional lean, notice day, and the earliest resolution day. Announce it on the next morning. Resolve at the following End Day, after one full planning period. If opened during day 5 resolution, the player sees it on morning 6 and it resolves during day 6 End Day.

Use the appropriate existing evidence: won quests, times downed, held sieges, fulfilled ambitions, role of the actually wielded weapon, relevant supported lessons, and Personality. Count at most one contribution of a given evidence family per quest/siege. Cap cumulative evidence so an old path is not permanent destiny. Do not use UI visits, repeated loan/unloan commands, or previewing equipment as progress.

Choose among eligible plausible options with persistent personal inclination and bounded deterministic sampling. A strong evidence lead should be reliable. A close choice may surprise. A new appropriate quest or weapon can change a close lean, but the smith never receives a direct selection control.

Resolve only for a still-living eligible member. If unavailable during an activity, apply the learned effect only to later fights, never to a frozen or already resolved battle. Postpone if the actual progression contract requires home availability. Persist the final choice and factual reason.

### 9.3 Commands and legacy compatibility

New-revision eras reject direct `ChooseSpeciality` with a meaningful typed message and remove the selection UI. Legacy eras retain their old command path and pending manual choices. Never leave a hidden working command that can override a new autonomous decision.

Townsfolk remain on the scalar resolver. Candidate cards may show a nonbinding build inclination inferred from their actual history, labelled as an inclination. Do not award or apply an invisible guild-only Speciality to all classic residents. Actual autonomous choice begins when the supported guild progression applies.

## 10. Opportunities, concerns, and departure

### 10.1 Use the existing board and visitor

One party is the scarce resource. A sponsored Hunt, a Recovery, Sabotage, and a rare supply opportunity compete for it. Retain existing board capacity, card lifetimes, guaranteed work/supply routes, and the one-morning-visitor channel. Preserve the special charter's extra slot.

A personal offer uses an existing quest card with sponsor/Hook metadata or a visitor choice with a real face. Do not displace a guaranteed recovery route or fill every slot with personal requests. Where no discretionary slot exists, queue at most one candidate internally and try later. Queueing is not an ignored request.

Revalidate initiator, target, ownership, resources, commitments, and expiry at posting, acceptance, and execution. An unavailable target resolves with explanation or one justified continuation, not endless mutation. Default five-day cooldown per hero/offer family. A declined offer does not instantly reappear under a new ID.

### 10.2 Opportunity content

Implement the Content Bible's eighteen definitions through existing systems. Classify each as a supported quest, supported visitor, or narrative-only milestone. The creative names and intended situations remain authored. Do not give unsupported opportunities a functional-looking button.

Initial functional set: powder-route scouting, troll preparation commission, family-weapon recovery, a missing caravan, a supported mentor lesson, and a reputation deed. Add the rest after their real dependencies work. Full delivery requires all eighteen to have an honest coverage status. Narrative acknowledgements need no fabricated resource reward.

### 10.3 What counts as ignored

An actionable concern can accumulate a missed response only when all are true: the player saw or had a persistent visible offer for its full valid window, a legal feasible remedy existed, the member was available enough to pursue it, and the offer genuinely expired or was explicitly declined. Count at most one miss per offer ID. Calendar days without a slot, an absent sponsor, or unavailable ingredients do not count.

Three distinct missed actionable offers may escalate a persistent concern. One rejected loan-return preference does not trigger departure: the member can already end that loan autonomously. A kin death alone does not trigger it either. Personality affects which concerns matter, not whether the system invents avoidable blame.

### 10.4 Departure notice

At most one active departure notice per roster. Announce a specific concern, remedy, and departure date at least three complete planning mornings ahead, and never before the first siege. Use `noticeDay`, `earliestDepartureDay`, `remedyId`, `originOfferIds`, and a reference to any already accepted protected commitment.

Existing quests/reservations are honoured. If the member is away, injured under a condition that prevents departure, captured, or committed to the pending siege, wait until that actual commitment resolves. Do not repeatedly let new unrelated assignments postpone departure forever. Reject or warn before confirming a new assignment that cannot finish before departure, unless it is the remedy itself. The warning must show dates and alternatives clearly.

If the remedy succeeds, cancel the notice and record the actual change. Otherwise the member leaves while at home after reconciliation, returns every recoverable player loan atomically, and keeps personally owned items and their purse. No severance penalty or hidden inventory deletion.

Retain an alive departed historical hero record with valid ownership references or map possessions to the existing external-holder model. Do not mark the person dead just to reuse death cleanup. They no longer shop, train, defend, or occupy a roster slot. Lost/captured player property continues through existing fate rules and cannot be returned magically.

### 10.5 End the story coherently

A failed Heirloom can become a Grudge once. A dead Avenger target can redirect to its faction once. A debt can be settled. An unavailable opportunity may end with a clear explanation. Fail-forward is useful, but must not mean every failure generates a permanent replacement task. Keep one active Ambition and one Hook.

## 11. Relationships and memory

Keep the existing two directional member bond slots and existing Comrades/Protector effects. Hooks do not consume those slots. Preserve lineage and mentorship records without a town-wide relationship matrix.

Optional later `Rivalry`: after real competing achievements or a shared quest, eligible members can gain a visible rivalry. Proposed combat effect, if enabled after testing: each gains +1 Attack in a party containing the other. No stacking, no altered Retreat rule, no loss of player party ordering. Replacing an old bond requires the existing documented policy, not silent overwriting. Rivalry is not required for the first slice.

Memory consists of compact evidence counters plus up to ten significant reason records. Store references to events and actual items, not full daily prose copies. Keep historical relationships meaningful after a death or departure without keeping every routine fight log forever.
## 12. Content Bible reconciliation: binding overrides

The Content Bible remains the source of creative content. Its draft combat framework is **not** an instruction to replace the actual engine. This section and Appendix A explicitly supersede conflicting mechanical passages while preserving all 48 authored identities and 36 army concepts.

### 12.1 Keep the existing combat contract

- Four actors per side, including summons, engines, swarms, and leaders.
- Party members act in the player's existing chosen order, then enemies in their existing order. No initiative or attack-speed scheduler.
- Existing eight-round limit, twelve under Long Assault, unchanged unless a separate measured balance change is recorded.
- Armor is an absorption pool, not physical percentage reduction. Use current cap and damage ordering from `FightRules` and the Writing Guide.
- Retain existing default targets and Guardian interception. A creature may use an existing explicit target Aim where its card calls for it. Do not globally switch default targeting to lowest Health percentage.
- `Aim.WeakestFoe` reportedly uses raw Health and bypasses the normal Guardian intercept. Verify and show that behaviour honestly on those enemy cards. Do not describe it as lowest Health percentage.
- Front and back mean first and last living actor in the actual ordered formation, not new protected ranks or a new front-line UI.
- Existing Burn, Bleed, Chill, Regeneration, Mark, Charge, Wet, Thorns, Steam, and Scrap keep their actual rules. Do not overwrite their numbers, timings, or caps from the bible.
- Existing element names: Fire, Frost, Storm, Grave, Verdant, Sun. Translate cold to Frost, lightning to Storm, and shadow/spirit to the existing supported Grave/magic representation. Do not add a seventh damage type merely for flavour.

### 12.2 Stat and rule translation

| Bible prototype | Implementation ruling |
| --- | --- |
| Health and Attack on a 100-Health hero scale | Initial fixture scale: Health ×0.4, Attack ×0.4, rounded to nearest integer with minimum 1. Use actual class kit baselines to verify. Store live values in content and calibrate before release |
| Fixed enemy Attack | Use the existing enemy Attack-range contract. Initial range is 90–110% of the converted value, minimum 1, unless an existing equivalent already has measured tuning |
| Armor percentage | Starting Armor points: 0→0, 5→1, 10→2, 15→3, 20→4, 25→5, 30→6. This is a one-time translation, not percentage mitigation |
| Initiative / Slow / speed | Drop initiative. Slow becomes 1 Chill at the stated cadence. This explicitly changes the draft card's mechanism while keeping its weakening role |
| Guard | Armor with the current cap, until used up. No new expiry pool |
| Exposed | Remove up to 4 Armor from the target. No persistent percentage-weakness status |
| Weakened | Apply 1 Chill. Existing Chill rules determine its effect |
| Mortal Wound | Apply 2 Poison under Section 13.2 |
| Silence | Interrupt a pending channel if present. Otherwise apply 1 Chill. Do not disable class kits generally |
| Taunt | Do not add a Taunt status. Black Knight raises Armor and attacks. Existing interception/Mark rules remain |
| Draft Poison | New Poison in Section 13.2, not the bible's separate timed damage model |
| Cleanse requests | Refer to actual removal effects. Ordinary healing reduces Poison under the new rule. Never display “Cleanse” for a heal that removes nothing |
| Ally protection redirect | First-batch Shieldbearer grants Armor to a selected ally. True damage redirection is deferred, not falsely reported as implemented |
| Fire/element resistance and weakness | Use current engine values, reportedly ±30%, and current damage order. Unit-specific data for new enemies overrides faction fallback rather than double stacking |
| Impact weakness | For selected skeletal/stone units, +30% direct Attack damage from the existing Axe family as the initial heavy-impact counter. Label literally as “Weak to Axe attacks.” Does not change Axe's base kit or every physical effect |
| Silver weakness/recovery suppression | Test actual Silver-metal provenance from authoritative weapon setup. Do not infer from artwork. Details in Appendix A |
| Five-actor army cap | Four. The authored templates already fit four actors before summons. Validate all rather than adding an extra leader |
| Swarm bodies | One actor, one Health pool, one action. Keep the Rat Swarm |
| Raw enemy cost budget | Retain as authoring metadata. Current difficulty scaling is still applied exactly once and must be calibrated. Cost is not XP, gold, or Challenge tier |
| New percent-stat variants | Translate to current stats/caps, retain restrictions, and test after base content. Swift uses no speed rule and remains disabled rather than pretending to act faster |

The ×0.4 conversion is an initial test fixture, not a final balance guarantee. Do not globally change hero stats to make the draft scale fit. Do not apply both prototype scaling and the live siege scaling twice. Existing measured enemies are comparison anchors.

### 12.3 Factions, lore, and charters

Keep the three existing faction IDs and established names. Integrate new groups as authored hosts within them:

| Content Bible host | Existing faction | Interpretation |
| --- | --- | --- |
| Iron Banner | Ashclaw Raiders | A contracted military host within the raiders |
| Pale Procession | Hollowbound | A particular necromantic procession |
| Thorn Covenant | Embermaw Brood | A pact-bound group of callers, beasts, and bound natural creatures operating in the brood's territory |

The Thorn Covenant is not a fourth faction. Its varied bound allies explain why a fire-associated regional threat can field wolves, roots, and stone. Ordinary peoples and independent creatures do not share an automatic allegiance.

Keep existing warlords, charter milestones, and historical names. The six new captains are additional encounter identities, not unannounced renames of Krag or the other established warlords. At a mandatory charter siege, the existing charter warlord remains the leader. Use a compatible escort profile and capacity validation rather than adding a fifth actor to a four-unit template.

New unit-specific weaknesses/resistances are explicit typed-combat data. Preserve current classic faction affinities and scalar calculations. Do not use this content expansion to silently rebalance two whole factions merely because both currently resist Fire and are weak to Frost.

### 12.4 Army templates and forecast

All 36 authored templates remain in the coverage ledger: 24 standard, six captain, three engine, three major monster. Their membership includes the entire enemy field. Never append an extra automatic leader. The strongest suitable member can carry the existing leader tag where a standard template requires one, without a free stat bonus. Charter-forced leadership uses a validated explicit alternate template.

Order actors using each template's intended screen/support relationship, then express front/back through actual list order. Preserve listed members, with clearly recorded alternate ordering where needed for the current Aims. Unit-specific targeting is data, not an unbounded tactical AI.

Guild forecasts commit the actual field and preparation four days ahead using the existing system. Sabotage retains its current role. Do not post a specific sapper warning and then reroll a different main threat. Standard early fields teach one role. Captains and major monsters respect minimum siege indexes and charter requirements.

## 13. Explicit engine additions and bounded player counters

### 13.1 Channel and Interrupt

Add a small typed pending-channel representation. Store source actor, ability ID, scheduled completion action/round, target references if fixed, and remaining use budget. Beginning and completing a channel uses existing actor actions. Never grant an extra action for preparation.

A channel announces when it begins. During the next party action window, eligible attacks may cancel it. On the actor's scheduled completion, an interrupted channel performs the card's ordinary fallback attack instead, unless the engine card explicitly specifies an inactive engine. Retarget only according to the card. Source downing cancels all its pending channels.

`Interrupt` only affects a pending channel. It does not stun, delete the next arbitrary action, or suppress every passive. Each effect retains its per-round/per-fight limit. The card states whether interruption spends the one attempt permanently or only the current cycle.

**New optional property `Disrupting`:** after the wielder's own attack deals at least 1 damage to a currently channeling target, Interrupt it. Once per fight. Add through the existing Bonus/property catalogue for Rare-or-better newly forged weapons in new-revision guild eras, within the normal property-slot budget. Do not retroactively rewrite old weapons or add a free extra slot. The property does nothing when no channel exists and must say so plainly.

Every channel encounter also remains answerable by killing the channeler, preventing its role with existing Sabotage, surviving its bounded consequence, or a defined alternative. Interrupt is useful, not mandatory universal gear.

### 13.2 Poison

Add **one** new status, distinct from existing Bleed and Burn:

- Maximum 3 Poison.
- At round end, take 1 damage per Poison, ignoring Armor, then lose 1 Poison.
- Healing received is reduced by 20% per Poison before rounding, up to 60%.
- When a positive healing attempt is applied, resolve its reduced healing, then remove 1 additional Poison. This uses up no extra actor action and works for Warden healing and Regeneration.
- All Poison ends with the fight. It never changes max Health or creates an overworld infection.
- Poison damage does not count as a hit and triggers no hit/retaliation chains.

New venom attacks apply the count in Appendix A. New skeletons, engines, Fire Elemental, and other explicitly excluded units do not receive Poison. Existing enemies gain no retrospective immunity unless explicitly updated and tested.

**New optional property `Purifying`:** after the wielder acts, remove 1 Poison from the weakest ally who has Poison. Once per round. If none qualifies, do nothing. Add as a Rare-or-better normal-slot Bonus in new-revision guild forging. Do not describe ordinary healing as a full removal of every condition.

If current rounding would turn every small heal to zero, keep that arithmetic explicit and test the removal still occurs after the healing attempt. Do not invent healing numbers in UI strings.

### 13.3 Return once and survival

Support a single bounded enemy return through the existing event grammar if it proves correct. Otherwise add the smallest explicit return action. Never claim `DOWNED → Summon` is equivalent until checking trigger order, slot release, entity identity, initiative, and replay semantics.

Necromancer/Bellkeeper return the actual eligible enemy once, at the card's reduced Health fraction, with the same encounter identity and spent one-use flags. No immediate action on return. Returned actors wait until the next round and count within four actors. A returned unit cannot be returned again by another source. Captains, engines, spirits, and major monsters are ineligible. Pending targets that become invalid do not magically change to a stronger unit.

Zombie's one-time survival at 1 Health is a separate damage-resolution effect. It is not a full return and does not reset after a real return. It does not trigger against the final design's Fire/Sun finishing counters. Enemy return does not authorize hero resurrection or relax the invariant that a dead hero never acts.

### 13.4 Structural attacks

Use the existing forge-health consequence layer. No separate wall-health simulation is introduced. A completed Sapper/Ogre/engine/giant channel records a bounded structural hit in the fight result. Apply all such hit consequences exactly once after the battle, together with the existing siege result:

`final siege forge loss = min(existing maximum siege loss, ordinary siege loss + completed structural hits)`.

Do not also damage a second wall pool. Do not persist the channel event and charge it again during replay or load. Cap completed structural-hit additions at 12 forge health per siege for this content version. A held battle with structural loss uses “Held at a cost.” Sabotage and killing/interruption must have recorded effects on the structural hits.

Values from the bible are already on a normalized 100 forge-health scale: Sapper 4 once, Ogre 5 once, Ram 4 up to three strokes, Catapult 3 once, Hill Giant 6 once. Keep them separate from the actor Health ×0.4 conversion. All attacker cards disclose their exact potential structural loss.

An engine that spends its final shot/stroke becomes neutralized, stops acting, and no longer prevents a field victory. Record a neutralization event rather than crediting a hero with an unearned kill. Engines do not count as leaders. Ordinary support troops must still be defeated or the objective otherwise resolved under current rules.

### 13.5 Minimal new targeting/condition support

Reuse existing Aims first. Add only typed predicates/capability metadata genuinely required: source weapon family, source metal, pending channel, returned-before, and specified unit tag. Compile those facts from authoritative weapon rules into fight setup. Combat must not query `GameState` or recreate crafting logic.

Do not introduce free-form strategy scripts, arbitrary expressions in content text, an initiative system, or a general-purpose AI planner.

## 14. Day resolution insertion points

Preserve the real current order, reported as GDD v2.0 Section 4.3. Reconfirm the code before edits.

| Existing step | New insertion | Protected property |
| --- | --- | --- |
| 2: planned party leaves | Freeze existing loadout and relics as now | Later purchases/choices cannot rewrite that quest |
| 6: shelf visits | Existing residents, then at most one eligible member visit | Residents are not replaced by members in unrelated systems |
| 8–10: activities | Collect bounded actual deed/equipment evidence | No per-hit advancement farming |
| 12: warning/siege | Use committed compatible content and structural-hit result | No field reroll or double forge damage |
| 13: ambitions/reconciliation | Hook progress, earned lean creation/resolution, eligible loan returns, resolved departures after existing retirement handling | No retroactive fight changes or duplicate cleanup |
| 15: record/pruning | Store reasons and bound history | Gazette reflects real outcomes |
| 16: new morning | Sponsored offers through existing board/visitor generation, fresh notices | One set of existing surfaces and capacity rules |

A new lean/loan notice created in step 13 is visible on the next morning and cannot resolve in that same step 13. Evaluate notice deadlines against the planning day just completed, not a prematurely incremented day.

If the real order differs, document the exact mapping before changing it. Do not reorder the entire day to match this table. Refresh the golden-state expectations only for new-revision scenarios and preserve the legacy-revision compatibility fixtures.

## 15. Presentation and terminology

No new screens. Use the existing morning roster line, member sheet, recruit card, quest board, visitor, shop-day replay, siege forecast, and Gazette. Add detail only where the player can take a related action.

Priority: siege timing and absent defenders, a departure deadline, an active personal decision, Speciality lean, loan return, then routine history. A maximum of three actionable lines in the existing daily surface is a presentation budget, not permission to suppress critical notices. Group or link lower-priority material rather than duplicating it.

Player copy follows the supplied Writing Guide: Armor, Health, Attack, Speciality, Personality, Trait, Quest, Member, Era, Infusion, Preparation. Use “weapon” generically. No HP/ATK abbreviations, semicolon chains, em/en dashes, or invented mechanical synonyms on screen. Technical IDs may retain old names.

New terms Poison and Interrupt must be added with exact implemented rules. “Channel” may be shown as “Preparing {ability}” rather than adding an unnecessary player glossary word. Do not display Taunt, Exposed, Silence, Slow, physical armor percentage, or initiative because those are not mechanics in this version.

Every choice has actor, target, cost, timing, consequence, and expiry where applicable. Every reason comes from a recorded contributing fact. Do not claim “after protecting allies” when the only evidence was weapon type. Show an inclination as uncertain and a committed equipment return as scheduled. Keep saved historical prose unchanged under the existing display-only legacy vocabulary policy.

## 16. Determinism, serialization, and boundaries

Keep `GameEngine.handle` and `GameSession` as authoritative serialized mutation boundaries. Accepted changes save atomically before presentation. No simulation decisions in Compose, preview code, image loading, or replay rendering.

Use existing random streams where the feature belongs. For new identity/Hook/progression variation, prefer a small number of versioned independent substreams or stable event-keyed draws so changing a name pool does not reroll crafting or combat. Do not use platform hashes or unseeded randomness. A new stored stream needs an actual migration entry with deterministic initialization.

Serializable optional fields may use defaults only after proving the actual codec accepts old saves and references remain valid. Behavioural backfills, stream initialization, version compatibility, or changed invariants can require an explicit schema step even when Kotlin defaults compile. Claude's “no schema step” recommendation is not a blanket migration guarantee.

Preserve IDs, ownership, names, current Specialities, item history, old forecasts, and rewards. New content can register globally while legacy eras use old behaviour. Never relabel an old weapon with new properties on load.

Bound Hooks, opportunity candidates, cooldown keys, member reasons, departed records, and saved fight logs under the existing compaction policy. The review reports long-save size as an existing risk; measure it again after these additions. Do not fix unrelated storage architecture as a hidden prerequisite unless tests demonstrate necessity.

## 17. Vertical slice and complete delivery plan

### Milestone 0: Reconcile and record

Verify branch, current docs, versions, and the review's extension points. Write the decision-ledger entries from Section 3, a full content coverage ledger, and a small implementation checklist. No second broad design pass is needed. Resolve routine code-shape choices autonomously.

**Gate:** actual paths and behaviour are verified, legacy/new revision boundaries are explicit, and no unrelated work is overwritten.

### Milestone 1: First playable people loop

In a new guild era: generate culture-aware identities and different starter Hooks, post a sponsored Hunt/Recovery through the existing board, allow one member counter visit, announce and safely execute a loan return, and resolve an earned Speciality after a visible lean. Include one bounded concern/departure scenario in fixtures, not a guaranteed early-game complaint.

Use existing enemies or only simple compatible new roles here. No new screen or complete combat rewrite.

**Gate:** one end-to-end scenario demonstrates player influence, hero initiative, owned/loaned item correctness, stable save/load, and commitment protection. New eras differ in people and priorities.

### Milestone 2: Full personal content and safe consequences

Complete five Hooks, eight ambitions, supported visitor/quest opportunities, the eighteen-entry opportunity ledger, candidate cards, coherent lineage naming, bounded departure, and market attachment. Implement exact no-duplicate/no-impossible-goal behaviour.

**Gate:** autonomous decisions remain legible and actionable without flooding the board or starving guaranteed supply/recovery routes.

### Milestone 3: Basic tactical bestiary

Import the complete 48-entry catalogue as data with honest implementation/asset status. Enable simple Armor, Chill, target-priority, Thorns, healing, and recovery roles using existing grammar. Implement compatible standard templates and forecasts. Preserve old enemies where required by classic and legacy eras.

**Gate:** at least one meaningful new role per faction changes a crafting/preparation decision. No unsupported ability is reported as working.

### Milestone 4: Named mechanical additions

Implement/test Channel and Interrupt, Poison and counters, return-once/survival, and structural-hit accounting separately. Enable their dependent cards and the two new Bonus definitions. Each extension has replay events, literal descriptions, finite uses, and focused fixtures.

**Gate:** loop limits, effect ordering, actor capacity, frozen loadouts, and idempotent structural consequences hold.

### Milestone 5: Complete authored armies and headline threats

Complete all 48 enemy definitions and all 36 templates under Appendix A, including captains, engines, and major monsters. Reconcile variant profiles. Preserve charter warlord requirements. Provide placeholders and exact art-needed manifest.

**Gate:** every authored ID is implemented or mapped to the explicitly approved adaptation in this final design. A dependency may delay a milestone, but is not a reason to declare full delivery with most content disabled.

### Milestone 6: Balance, UI, compatibility, and handoff

Run the existing core, combat, simulation, migration, prose, and appropriate app checks. Run paired interventions and report diversity/influence evidence. Update docs, coverage status, and device-only items. Keep commits coherent and reviewable.

**Gate:** full-scope results are documented, limitations are explicit, old saves remain supported, and no new UI clutter or performance regression remains unexplained.

These are implementation milestones, not six separate permission gates. Continue through the authorized scope while dependencies and checks permit. If an environment limitation blocks Android validation, complete JVM and other feasible work and list the exact unrun checks. Do not claim a stub/desktop compile is an Android build.

## 18. Verification and balance evidence

### 18.1 Invariants

Stable identity and valid content references. One authoritative weapon location. No loan trade-in or duplicate credit. No dead/departed member actions. No incompatible activity overlap. No retroactive fight mutation. No UI/replay RNG. Same inputs and versions produce the same results. Bounded offers and evidence. Old fields default safely. Accepted commands and End Day retries do not duplicate purchases, choices, returns, departures, raises, or forge loss.

### 18.2 Required behavioural fixtures

1. Member spends personal gold at the smith without becoming a resident.
2. Loan purchase evaluation never credits a player-owned loan as trade-in.
3. Announced loan return waits a full planning window and preserves a quest/wall commitment.
4. A genuinely better owned alternative is equipped without needing indefinite smith consent.
5. A new loan that removes the reason cancels the return intent.
6. A pending lean can respond to relevant experience and resolves autonomously once.
7. Repeated previews and loan toggles cannot farm evidence.
8. New eras reject manual Speciality selection; legacy pending choices remain valid.
9. Debt/Grudge/Recovery offers point to real resources and targets.
10. Board capacity or unavailable ingredients cannot count as ignored opportunities.
11. A departure remedy works; an unresolved notice honours prior commitments and returns player property once.
12. Kin death alone never triggers a punishment deadline.
13. Name pool exhaustion, cross-culture overlap, and sibling surnames resolve without hangs or duplicate living full names.
14. Unmatched race art uses an honest placeholder and does not reroll identity.
15. Poison respects caps, Armor bypass, healing reduction, removal, and fight-end cleanup.
16. Interrupt cancels only a real pending channel and observes the card's use budget.
17. Returned enemies retain identity/spent flags and cannot act twice or return twice.
18. Engine completion and neutralization yield one structural consequence with a bounded final siege loss.
19. All templates fit four actors including leaders and returned units.
20. Existing classic/legacy golden fixtures retain their promised behaviour and old historical strings remain unchanged.

Do not write hundreds of tests that simply mirror data constants. Use one content validator plus focused causal fixtures and existing simulation tools.

### 18.3 Influence and diversity

Use the existing guild simulator and paired mode, with the review's suggested feature-off baseline where practical. Prefer event-keyed random draws so removing one action does not replace every unrelated future roll. A broad counterfactual trajectory difference is useful evidence, but does not prove that one named decision changed for the intended reason.

Measure member purchases, useful equipment swaps, Speciality distribution, Hooks offered/resolved, offers blocked by capacity, departure frequency, class/weapon use, first-siege outcomes, survival, gold flow, forge damage, and repeated army/story sequences.

For influence, construct matched scenarios with a specific intervention: change one relevant weapon, quest, price, or supported personal choice. Record the eligible decision, direct reason change, and result. Report the share changed and confidence/sample limitations. Do not impose a universal >50% target over all natural decisions; a competent hero should sometimes make the same sensible choice despite an irrelevant smith action.

For memorability, report heroes with three distinct meaningful event categories per 20 days, separating completed eras from early losses. Suggested diagnostic goal: at least two in a typical 20-day era. Repeated trivial proc records do not count as distinct deeds.

Use existing documented tuning/holdout seed blocks. Do not tune on every test seed or pick a few favourable examples. Compare first-siege viability and survival to the existing charter/policy bands, and report deliberate deviations. Preserve practical counterplay without requiring a single rare material or new property.

### 18.4 Runtime and UI

Compare End Day timing, actor counts, save size at long durations, and history growth before/after. Use the actual existing performance targets rather than inventing a new desktop threshold. Verify small-screen wrapping, enlarged text, notifications, returned-loan traces, and new status labels. Device checks remain honestly unverified when unavailable.

## 19. Completion and documentation

The final implementation report must include changed behaviour, tested evidence, missing assets, limitations, and per-milestone status. The content ledger must cover twelve races/cultures, twenty backgrounds, five Hooks, eight ambitions, six current Specialities, eighteen opportunities, 48 enemies, and 36 templates. Personality backlog and future per-class Specialities are not falsely counted as implemented.

Update GDD mode differences, explicit contract changes, vocabulary, persistence notes, counts, and decision history. Update the Content Bible with a short top notice linking to this final integration design and its approved mechanical overrides, preserving its authored name lists and creative descriptions. Do not overwrite saved historical prose or erase the draft/review audit trail.

No automatic release, signing, merge to main, production deployment, or asset generation is part of this handoff. Implement and validate in a coherent feature branch according to repository instructions. Ordinary local commits are appropriate. Push only when authorized by the owner's environment/session or repository workflow, and never claim a push occurred without doing it.

## 20. Visual direction and UX quality contract

**Owner direction added during finalization:** the interface must be gorgeous, highly polished, and excellent to use throughout the work. Visual quality is a product requirement at every milestone, not an optional finishing pass. Functionally correct data displayed in cramped debug cards is not a completed player experience.

### 20.1 Art direction: a lived-in fantasy guild and forge

The desired impression is a carefully art-directed premium fantasy game: warm workshop light, confident typography, character presence, restrained metal accents, and generous readable space. Keep the existing charcoal, ivory, brass, ember, Alegreya, and Fira Sans identity. Use the existing pixel art as atmosphere and focal imagery, with crisp modern text and controls.

Avoid the previously disliked box-heavy dashboard appearance. Do not solve added complexity by placing every fact inside another rounded rectangle. Prefer an open section, a portrait-led row, one purposeful inset, a divider, or a subtle tonal change. Borders and ornamental frames should communicate selection or a significant object, not surround every paragraph. Do not turn every control gold, every panel into parchment, or every label into a badge.

Preserve successful existing screens. Apply the design language consistently to touched surfaces and shared components rather than starting an unrelated full-app redesign. New mechanics must not visually regress the current title, Forge, Shop, Guild, or battle presentation.

### 20.2 Shared visual system

Extend existing tokens/components before introducing new ones. Where current approved tokens differ, keep the coherent existing system rather than creating competing local values. The following are concrete starting targets:

| Element | Design requirement |
| --- | --- |
| Typography | Alegreya Bold for titles/headings. Fira Sans for body, labels, values, and actions. No third font |
| Body text | Normally 16 sp. Secondary explanatory text 14 sp. Never shrink important sentences to fit an overcrowded card |
| Headings | Clear scale, approximately 22–28 sp for a section/primary state. Avoid repeated equally large headings |
| Labels | Approximately 12–14 sp, with adequate contrast. No all-caps paragraphs or long small-print rules |
| Spacing | Consistent rhythm using the existing 4/8/12/16/24/32 dp family. Usually 16–20 dp outer gutters. Do not improvise a different padding per row |
| Actions | At least 48 dp touch targets, 52 dp primary targets. One dominant action in the current decision region |
| Colour | Ivory for main text, quiet neutral secondary text, brass for structure. Gold for the main action/selected state. Red for actual danger or cost, not all ordinary copy |
| Surfaces | Two or three clear tonal levels. No stacks of translucent panels, noisy textures, or gratuitous gradients behind text |
| Icons | One coherent existing icon family and consistent weight/size. No emoji as substitutes for production UI icons |
| Numbers | Aligned values with tabular digits if available. Show units and timing. Consistent signs and formats |
| Selection | Whole valid row/card tappable, explicit selected mark, stable dimensions. Never rely only on a border colour |
| Disabled state | Explain the relevant unmet condition near the action or through the existing inspect pattern. Avoid unexplained grey controls |
| Text length | Wrap naturally. No clipped names, ellipsized critical consequences, or horizontally scrolling rules |

All target dimensions must be checked against font scaling and existing accessibility rules. “Looks cleaner” does not justify lower contrast or tiny text.

### 20.3 Composition and information hierarchy

Every touched screen has a clear first read: what is happening, what matters now, and what action is available. A player should identify the current day and next siege without searching through the roster.

Keep the threat strip prominent and consistent. On the eve of a siege and the siege day, emphasize the countdown and readiness through scale and placement as well as colour. Do not flash the entire screen or use a full-time alarming red theme. The siege field, preparation, and absent defenders must remain reachable from that summary.

Use whitespace to separate narrative, mechanics, and actions. Show a short rule summary and reveal the full effect through the existing detail/inspect view. Do not display the same explanation in the title, subtitle, body, tooltip, and button area.

Limit each compact hero row to identity, condition/role, one current actionable state, and its relevant action. Rich history belongs in the hero sheet. Do not add ten badges because the simulation now stores ten facts. Two high-value status indicators plus an accessible detail affordance are usually enough in a compact row.

### 20.4 Character-led member and recruit presentation

Portraits give the cast emotional weight. Keep them large enough to recognize faces, with consistent cropping and clear silhouettes. A member row should feel like a person with a current situation, not a spreadsheet record with an avatar attached.

The hero sheet should open with portrait, readable full name, class/race, current condition, and equipment. Follow with the current personal situation and actionable development. Place historical records below the immediate decisions. Distinguish owned weapon and player loan plainly without a dense ownership table.

A Speciality lean should look like a brief personal development moment: the existing portrait, the leaning choice, one factual reason, and when the choice will resolve. No mandatory selection dialog and no oversized celebration that hides the player's next task.

A loan-return notice should show the current loan, preferred owned alternative, reason, and safe timing together. Relevant actions are “Offer another loan,” “Recall the loan,” and ordinary navigation to the member/equipment view. Do not imply that ignoring the notice grants a permanent veto.

A departure notice needs a calm, unmistakable date, actual concern, and remedy. It is a management decision, not a guilt-inducing modal. Its warning styling should be more prominent than ordinary flavour but subordinate to an immediate siege or failed save.

Recruit cards show the useful upside and personal complication before payment. Do not bury an equipment oath or imminent concern behind a second sheet. Missing race art uses the coherent neutral placeholder defined in Section 6, never a broken-image icon or mismatched face.

### 20.5 Quest board, Shop, and Forge flows

Keep the one-party constraint visible on personal offers. A sponsored quest has a recognizable sponsor, clear objective, departure/return timing, cost, reward, risk, and the relevant conflict with siege preparation. Avoid inserting a paragraph of backstory before the decision facts.

Member purchases use the existing counter/shop-day presentation language. A purchase or returned loan should be visually understandable as an item changing hands. Distinguish the hero's purse from the smith's money in transactions without adding a second permanent currency dashboard.

Forge guidance should connect a requested effect or upcoming threat to the current recipe without adding another permanent panel. Prefer a short contextual line or existing inspect link. Keep the anvil, chosen parts, result, and main Forge action visually central. Personal quests must not displace the core crafting experience.

Preserve scroll position and selection when returning from a hero/weapon sheet. Avoid bouncing the player to the top of a long roster or resetting the selected guild sub-tab after a routine action. Back should close the current layer first under the app's existing navigation contract.

### 20.6 Enemy inspect and fight readability

New enemies need a readable identity, battlefield job, defining effect, and practical counter. The artwork and name lead. Exact mechanics follow in concise supported prose. Do not present an internal authoring stat table as the player-facing bestiary.

The fight should show which actor is acting, who is targeted, what effect occurred, and why the result matters. Use the existing portrait animation system when supported, modest emphasis, clear numbers, and short causal highlights. Channels must have a visible preparation cue. Poison and healing must look different using both text/icon and colour.

Prevent damage, heal, Armor, and status popups from overlapping the same face. Sequence or aggregate related events, preserve critical events, and keep a bounded number on screen. A slower readable replay is preferable to a shower of tiny values. Reduced motion removes positional effects and autoplay according to current settings while retaining readable state changes.

Replays remain derived from saved events. Do not invent combat motion, a hit, or an effect that did not occur. Art placeholders must keep the same layout, framing, and contrast standards as final art.

### 20.7 Interaction states and accessibility

Design normal, selected, disabled, unavailable, empty, long-content, and failure states with the same care as the ideal filled state. A member unavailable for a quest should say why. An empty opportunity slot should support the existing board rhythm rather than show a barren error panel. New content should not cause the Guild screen to jump unpredictably after every day.

Use existing transition and haptic conventions. Typical small state transitions may be around 150–250 ms, but do not add unnecessary delays or expensive layered animations. No animation may delay saving, consume RNG, or block an urgent action. Honour reduced motion and system font scale. Provide meaningful TalkBack descriptions, logical focus order, and status announcements for important changes without reading every decorative badge.

At larger font sizes, restack content and let the page grow. Do not shrink fonts or remove a cost/departure date. Avoid fixed-height text containers that work only for the shortest human name. Check names from every culture, full titles, two-digit levels, multiple conditions, and long weapon names.

### 20.8 Visual evidence at every milestone

Before implementing a touched flow, inspect its current components and capture a baseline when a runnable Android environment is available. At each milestone, render representative screens, inspect the results, fix visible defects, and then continue. Do not wait until all simulation work is complete to discover that the roster no longer fits.

Required evidence set across the implementation:

| Surface/state | What to verify |
| --- | --- |
| Guild today, ordinary day | Clear day, current commitments, restrained density |
| Guild eve/day of siege | Countdown and readiness dominate appropriately, absent defenders understandable |
| Roster with mixed races and long names | Portrait consistency, wrapping, aligned actions, no badge clutter |
| Member sheet | Identity, equipment ownership, Hook, and current action easy to scan |
| Speciality lean and resolved choice | Autonomous choice and timing clear, no misleading selection control |
| Loan-return notice and completed return | Alternative, reason, timing, and property ownership clear |
| Departure notice and remedy | Exact date and feasible action visible without opening multiple sheets |
| Candidate with an oath | Constraint and fee disclosed before recruitment |
| Sponsored quest versus siege quest | One-party trade-off and return timing readable |
| Member counter purchase | Money and item transitions comprehensible |
| Enemy/channel/Poison replay | Actor/target/effect readable, no popup overlap |
| Empty/disabled/unavailable states | Clear explanation and useful next step |

Inspect at approximately 360 dp width and a larger phone width, at 1.0× and at least 1.3× text scale, plus a 2.0× stress check on critical dates, names, and actions. Use the app's real target environment and accessibility guidance if more demanding. Screenshots can support layout evidence, but gestures, focus order, and scroll restoration require interaction checks.

A rendered Compose screenshot is useful evidence. A web mockup is not proof the Android app looks correct. If the cloud environment cannot render or run the app, continue all feasible work, add the appropriate previews/fixtures, and mark visual/device verification explicitly blocked. Do not claim extraordinary polish has been verified from source inspection alone.

### 20.9 Acceptance bar

A feature is not UI-complete if it has clipped content, unreadable secondary text, inconsistent type/spacing, accidental nested boxes, competing primary actions, lost scroll state, ambiguous ownership, hidden deadlines, or new mandatory reading unrelated to a decision.

For each milestone, record: mechanic status, UX flow status, rendered visual evidence, accessibility checks, and unresolved visual issues. A passed core test does not waive visual defects. The final report must distinguish implemented polish from actual rendered/device verification. The owner should receive a game that feels deliberately designed throughout, with the art pipeline free to enrich it later.


## Appendix A. Complete enemy adaptation register

All IDs below match the Content Bible. Preserve its appearance, name, role, forecast, and stated cadence unless this table changes them. Use current FightRules for every existing status. A numerical amount written here is live-scale prototype tuning, not an additional ×0.4 conversion. “Converted Attack” means the once-converted base stat.

The table is intentionally comprehensive. It prevents the implementer from silently dropping the distinctive roles while claiming that all content is integrated.

### A1. Standard enemies

| ID | Live-engine implementation | Explicit departure from bible prototype |
| --- | --- | --- |
| ib_goblin_skirmisher | Starts with 2 Armor, then ordinary attacks | Replaces first-hit percentage dodge. No miss roll |
| ib_goblin_sapper | Starts charge on action 2, completes on 3 for 4 structural loss once. Interrupt/downing spends/cancels it | Uses one forge-loss result, no wall pool |
| ib_wolf_rider | Actions 1 and 4 use `Aim.WeakestFoe`, otherwise normal attack | Uses actual raw-Health Aim, not lowest percentage or new ranks |
| ib_hobgoblin_shield | Starts with translated Armor. On actions 1 and 3 grants 3 Armor to the lowest-Health-percentage other living allied specialist, falling back to another ally | Bounded Armor protection instead of 30% damage redirection. No self target if no ally exists |
| ib_hobgoblin_archer | On actions 2 and 5 attacks the back party member, otherwise normal target | Back Aim replaces new least-Guard targeting rule |
| ib_war_drummer | On actions 2 and 5 grants 2 Armor to each living ally, then performs its ordinary attack at 50% Attack | Replaces speed and Attack aura. Uses existing Armor/attack grammar, not a new stat-modifier action |
| ib_orc_breaker | Actions 2 and 5 remove up to 4 Armor from front member, then ordinary attack | Armor removal replaces Exposed |
| ib_orc_shaman | Actions 2 and 5 target the back member. Interrupt a pending channel if present, else apply 1 Chill, then deal 50% converted Attack as the supported magic element | No general Silence or hidden spell-damage leaderboard |
| ib_bugbear | First action attacks back member for 125% converted Attack, later ordinary | No new target-ranking engine |
| ib_ogre | Gate Rush starts action 2, completes action 3 for 5 structural loss once, other actions ordinary. Interrupt cancels | Forge consequence only |
| ib_troll | Before own action, heal 2 unless it received Fire or supported acid-tag damage since previous action. Dies normally at zero | Uses live-scale heal and existing effect events, no permanent undead-like survival |
| ib_deserter | Every second action ignores up to 4 Armor using existing penetration action semantics, otherwise ordinary | Points of bypass, not percent reduction. Does not erase all Armor as a side effect |
| pp_skeleton_guard | Axe attacks deal +30% direct damage. Poison/Bleed exclusions as authored, ordinary attacks | Literal weapon-family counter replaces undefined impact damage type |
| pp_skeleton_archer | Same Axe counter/exclusions, ordinary ranged/back-capable ability under existing Aims | No initiative/rank layer |
| pp_zombie | First otherwise-lethal direct hit leaves 1 Health unless Fire or Sun, once per fight preserved across return. Poison excluded | Sun is the actual anti-undead finishing counter. No generic undeclared holy damage type |
| pp_ghoul | Actions 2 and 5 ordinary attack plus 2 Poison to the attack target | Poison replaces Mortal Wound/paralysis |
| pp_wight | Actions 2 and 5 ordinary attack, then heal 40% actual Health damage from that action, maximum 3 | No permanent max-Health loss. Armor-absorbed damage provides no heal |
| pp_necromancer | Starts one-action delayed raise at first eligible action from action 2 onward. Return one standard Skeleton/Zombie at 35% maximum Health, once | Actual return identity and capacity rules, not unverified summon imitation |
| pp_acolyte | Actions 2 and 5 heal weakest eligible nonspirit undead ally for 4, otherwise ordinary Grave attack | Existing heal Aim with a narrow eligible-tag filter, no new recovery subsystem |
| pp_ogre_zombie | Actions 3 and 6 deal 70% converted Attack to each of at most two front surviving members, otherwise ordinary. Poison excluded | Exact fixed two-target Aim if not already present. No additional zombie survival |
| pp_banshee | Wail starts action 2, completes action 3 once for 50% converted Attack as Grave to all party members and 1 Chill each. Interrupt cancels permanently | Chill replaces Weakened, no death/charm effect |
| pp_wraith | Actions 1 and 4 Grave attack the back member, otherwise ordinary Grave. Frost resistance uses current amount | No physical immunity or intangible untargetability |
| pp_black_knight | Actions 2 and 5 gain 5 Armor, then ordinary attack. Poison/Bleed excluded | No new Taunt. Current Guardian/Mark rules unchanged |
| pp_grave_rats | One actor. Ordinary action uses `Aim.WeakestFoe`. Area direct damage gains +30% against it | Uses a typed multi-target-source predicate if needed. No body-per-rat actor count |
| tc_dire_wolf | Ordinary action. Once per round, if another allied beast has already damaged the same target this round, deal +20% direct Attack damage | A bounded event-evidence condition, no extra action or future knowledge |
| tc_giant_spider | Actions 2 and 5 apply 1 Chill to back member, other actions ordinary. Fire weakness at current amount | No initiative-based Slow |
| tc_harpy | Actions 2 and 5 hit back member for 50% converted Attack and remove up to 4 Armor. Other actions ordinary | No exposed-duration status or prolonged charm |
| tc_minotaur | Charge starts action 1, completes action 2 for 150% converted Attack to front member, then remove up to 4 Armor. One charge, interruptible | No automatic wall hit or Exposed status |
| tc_manticore | Actions 2 and 5 deal 65% converted Attack to each of at most two party members, otherwise back-targeting ordinary attack | No flight immunity or new ammunition entities |
| tc_wyvern | Actions 2 and 5 attack weakest member by existing Aim and apply 2 Poison, otherwise ordinary | Actual Poison status, not bible damage-over-time rules |
| tc_treant | On the first enemy attack to hit it each round, gain/apply 3 Thorns early enough for that attack's existing Thorns resolution, or a verified equivalent direct reaction. Fire weakness | Use one verified event path. No double retaliation and no ongoing % physical reduction |
| tc_earth_elemental | Translated starting Armor. Actions 2 and 5 gain 6 Armor, then ordinary attack. Axe attacks +30% | No Slow immunity field because Slow does not exist |
| tc_fire_elemental | Fire direct attacks. Actions 2 and 5 add 2 Burn. Fire resistance/Frost weakness, Poison/Bleed excluded | Uses existing Burn and elements |
| tc_giant_serpent | Actions 2 and 5 attack plus 1 Chill to front member, otherwise ordinary | Chill replaces Weakened. No removed/swallowed member |
| tc_werewolf | Before action heal 2 unless hit by Fire or an actual Silver weapon since prior action. Silver-weapon own attacks deal +30% direct damage | Source-metal predicate, no immunity to ordinary weapons or infection |
| tc_beastcaller | On actions 2 and 5 grant 3 Armor to weakest allied beast/pack-eligible member, then attack weakest party member at 50% Attack. Other actions ordinary | Replaces unsupported persistent target-order/Attack aura with concrete support and pressure. No speed or extra turns |

For Treant reaction ordering, use a dedicated capped direct retaliation if adding Thorns on the trigger would be too late or would consume unrelated existing Thorns. Do not create a dependency on lucky event ordering. Its displayed effect must match the actual path.

For predicates such as Axe-source, area-source, or already-hit-by-another-beast, add small typed conditions only if no existing condition fits. Do not expand them into a generic expression language. A wolf's pack bonus cannot be caused by its own earlier effect or retaliation.

### A2. Captains

| ID | Live-engine implementation | Explicit adaptation |
| --- | --- | --- |
| cap_iron_marshal | At first Health ≤50%, starts Last Advance. Completes next action, granting 5 Armor to each living ally once. Interrupt/downing cancels | Armor rally instead of temporary +20% Attack to all |
| cap_goblin_chief | Demolition Order starts action 2, completes on 3 once. If a Sapper has a valid unspent charge, advances its preparation so its next scheduled action completes. Otherwise grant 5 Armor to weakest ally | No extra action, no second Sapper charge, no engine alongside it |
| cap_lich | First eligible standard undead death permits Funeral Toll to begin on next action, completes one action later, returning that unit at 40% Health once | Actual typed return. No other recovery support |
| cap_death_knight | Actions 2 and 5 start a channel completing next action for 80% converted Attack as Grave to all members. Interrupted cycle falls back to ordinary attack | Existing Grave element, no unsupported universal silence/taunt |
| cap_elder_treant | Shelter starts action 2, completes action 3 once for 7 Armor to all living allies. Fire weakness. No standard Treant passive inherited | Armor absorption, not percent mitigation or implicit retaliation |
| cap_beast_matriarch | Actions 2 and 5 attack weakest party member, then accompanying wolves' next normal attacks use that same still-living target until the round ends. Once per listed cycle | Prefer a small encounter-local target override scoped to those wolves, not reuse player Mark incorrectly. If extending Aim is necessary, add one typed named-prey Aim. No Beastcaller in her template |

The Matriarch target override is explicitly part of this card's implementation scope. It never changes player party order, grants a free attack, or overrides Retreat. If the marked prey falls before a wolf acts, that wolf uses its ordinary target rule. The visual label should describe the pack's target without falsely using the existing one-hit player Mark status.

### A3. Engines and major monsters

| ID | Live-engine implementation | Explicit adaptation |
| --- | --- | --- |
| eng_battering_ram | Start/complete actions 1/2, 3/4, 5/6. Each completed stroke records 4 structural damage. Interrupt cancels that cycle. Neutralize after third cycle is spent | Four-actor capacity, no separate crew actors, no second wall pool |
| eng_ballista | Start/complete on 1/2, 3/4, 5/6. Each bolt uses converted Attack against front member and bypasses up to 5 Armor. Interrupted cycle spends that shot. Neutralize when three cycles spent | Points of penetration and fixed target instead of highest-%-armour targeting |
| eng_corpse_catapult | Start action 2, complete action 3 once for 3 structural damage and 2 Grave damage per member. Interrupt cancels. Neutralize after completion/cancellation | No infection or spawned extra corpses |
| boss_hill_giant | Boulder start action 2, complete action 3 once for 6 structural damage. Action 5 deals 70% converted Attack to first two members. Other actions ordinary | Named threat, no extra engine/leader |
| boss_bone_colossus | Actions 2 and 5 gain 6 Armor and attack while shell intact. First time below 50% Health remove all current Armor and disable later shell gains. Axe-source attacks +30%, Poison/Bleed excluded | Breakable absorption shell rather than permanent % armour |
| boss_ash_dragon | Breath starts action 2, completes action 3 once for 70% converted Attack as Fire to all members, plus 2 Burn to weakest member. Fire resistance/Frost weakness. Other actions ordinary | Uses actual Burn and existing targeting. No tabletop lair subsystem |

### A4. Variants and completion meaning

Veteran, Armoured, Frenzied, and Scarred can use existing numeric content modifiers with current caps. Armoured gives +2 starting Armor instead of +10 percentage points. Frostbound applies 1 Chill on its first hit and uses current Frost resistance/Fire weakness. Swift remains a deferred variant because fixed turn order is intentionally preserved. Keep its ID in the ledger with that explicit reason rather than inventing speed.

“Complete 48 enemies” means these 48 definitions and their approved adaptations work. It does not require Swift, full damage redirection, initiative, an alternate Armor model, or every future Speciality concept. Those are explicitly outside this final version.

## Appendix B. Worked timing and agency examples

### B1. Loan return

At day 6 reconciliation, Maren's owned Frost spear is found to fit the committed threat materially better than her loan. Morning 7 displays the intended return. The smith has day 7 to offer another useful loan or let her preference stand. If she leaves on a quest that morning, its existing loadout is frozen and the return waits. If she is home and commitments resolve, day 7 reconciliation returns the loan and equips the owned spear. Morning 8 shows the actual equipment. Nobody sells or duplicates the loan.

### B2. Speciality

Day 5's real quest opens Vanguard/Striker. Morning 6 shows Maren's lean and principal evidence. The smith can provide relevant supported work or equipment during day 6. Day 6 reconciliation selects the eligible Speciality once. Its effect begins in subsequent fights. The player cannot reload the morning to choose another draw.

### B3. Departure

A member receives a justified notice on morning 7 after three distinct actionable offers were missed. The displayed departure date is morning 10, allowing complete planning days 7, 8, and 9. At day 9 reconciliation, use next-morning day 10 for the due check. Honour a quest/reservation accepted before the notice. Prevent a new unrelated assignment from extending the date indefinitely. A successful remedy cancels the notice. Otherwise return actual loans and move the living member into the departure history before morning 10. The member does not die or lose personal possessions.

### B4. Siege choice

A day-10 field is committed four days ahead. It contains a Shieldbearer, Sapper, and two climbers, within four actors. A sponsored family-weapon Recovery competes with Sabotage and the supply opportunity for the one party. The smith may prioritize the personal goal, forge a Disrupting weapon, prepare ordinary focused damage, or accept limited structural loss. The Sapper has a visible preparation window and one charge. The result records which action actually mattered.

## Appendix C. What is explicitly deferred

New base classes. A third personality list. Complete per-class Speciality trees. Initiative and attack-speed. True enemy ally-damage redirection. New Armor percentages. Generic Silence/Taunt/Exposed systems. A town-wide relationship matrix. A runtime LLM/planner. Generic oath-driven refusal/desertion. Overworld movement. Separate wall health. Classic interaction-engine conversion. Swift variant. Enabling new-revision mechanics mid-era without a dedicated migration.

The content authoring, member agency, Hook/ambition extension, full approved 48-enemy adaptation, 36 templates, safe progression/equipment changes, and narrow engine additions are in scope. Do not mistake the explicit deferrals above for permission to stop after importing names or loading disabled enemy records.
