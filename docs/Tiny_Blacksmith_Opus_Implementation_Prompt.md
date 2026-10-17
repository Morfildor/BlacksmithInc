# Opus 5.5 implementation prompt

Implement the Living Heroes and Siege Variety design in this repository. This is an implementation session. The design has already been proposed, reviewed against the code, and finalized. Inspect the current code, then carry the authorized work through the staged milestones. Do not stop at another review or a plan.

## Required sources

Read these before editing:

1. `AGENTS.md` and applicable repository instructions.
2. `docs/Tiny_Blacksmith_Living_Heroes_Final_Design.md` in full, including Section 20 and Appendix A.
3. `docs/Tiny_Blacksmith_Peoples_Names_and_Siege_Bestiary.md` in full.
4. `docs/LIVING_HEROES_REVIEW_2026-10-17.md` for code-discovery references and the reasoning behind the final design.
5. `docs/WRITING_GUIDE.md`, the current `Tiny_Blacksmith_GDD.md` at its actual repository path, `docs/DECISIONS.md`, and relevant current feature/version/migration records.

Locate an exact named file if it was placed at a different path. Do not substitute the older GDD v1.0 for the current GDD. The earlier `Tiny_Blacksmith_Living_Heroes_Design_Review.md` is historical and is not the final specification.

The supplied review describes `ui-polish-post-terms` at `a81e43b`. Treat that as the review baseline, not an assertion about the current working tree. Check the actual branch, changes, and versions. Preserve unrelated work. Use an appropriate feature branch under the repository workflow.

## Authority and decisions already made

The final design wins over conflicting mechanics in the Content Bible and recommendations in the review. The Content Bible wins for authored names, appearances, creative identities, and compatible content. The Writing Guide governs player-facing vocabulary.

The owner has explicitly requested:

- Procedurally varied, race-appropriately named heroes within the existing five classes.
- Heroes choosing their earned Specialities autonomously.
- Guild members shopping and replacing equipment previously provided by the smith.
- Logical personal motives, initiative, and opportunities.
- A strong forge/shop/guild management game with different decisions each era.
- The authored content catalogue implemented, rather than creatively replaced by you.
- Gorgeous visual design, extraordinary polish, and excellent UX throughout implementation.

Do not ask for reconfirmation of these requirements. Record the explicit existing-contract changes from final-design Section 3 in the decision ledger. Routine technical choices and tuning belong to you within the spec. Escalate only a genuine unresolved product contradiction or a destructive action outside scope, with a concrete recommendation.

## What to build

Extend the existing `Hero`, `GuildMember`, generation, market, Ambition, Stories, quest/visitor, bond, combat, and persistence systems. Verify actual paths and symbols before changing them. Do not build a parallel hero simulation, generic planner, third personality list, new inbox, or new combat clock.

Deliver the following through the final design's milestones:

1. Twelve race/culture definitions, all 1,200 given names and 600 surnames, twenty backgrounds, coherent lineage, bounded uniqueness/fallbacks, and honest art placeholders.
2. Five Hooks and eight ambitions using real targets and finite outcomes. Preserve existing Personality/Trait meaning.
3. One additional bounded member counter visit, shared equipment evaluation, real purse accounting, attachment, and switching friction.
4. Announced autonomous loan returns with an owned alternative, one complete planning window, atomic ownership transitions, and frozen quest/siege commitments. The smith can influence the choice but does not have an indefinite veto.
5. Announced Speciality leans and autonomous earned selection using the existing six Specialities and capped real evidence. No direct selection control in new-revision eras.
6. Sponsored board cards and visitors, all eighteen authored opportunity definitions accounted for, actionable bounded concerns, and three-planning-day departure notices under the final rules. No punishment for impossible offers or kin death alone.
7. All 48 authored enemy definitions and all 36 army templates using Appendix A's exact integration rulings. Preserve charter warlords, three faction IDs, four actors per side, and committed forecasts.
8. Narrow typed Channel/Interrupt, Poison, return-once/survival, source-condition, and structural-hit additions. Add the two specified normal-slot weapon Bonuses, Disrupting and Purifying, under the final versioning/content rules.
9. Real event-derived explanations, updated UI states, replay cues, save compatibility, tests, simulation evidence, and documentation.

Milestones are an implementation sequence, not requests for permission. A small working slice proves integration, but it is not the final scope. Continue into the remaining content and checks. Do not declare completion after loading most enemies as disabled records.

## Combat reconciliation is mandatory

The original Content Bible used a prototype combat vocabulary. Do not transplant it wholesale:

- Armor absorbs points until used up. It is not percentage physical mitigation.
- Preserve current Burn, Chill, Bleed, and other existing status rules and fixed action order.
- No initiative, attack-speed system, general Silence, Taunt, or Exposed status.
- Swarms are one actor. No fifth actor or appended automatic leader.
- Use the actual existing Aims. Verify `WeakestFoe` semantics and describe them accurately.
- Apply the final profile conversion once and calibrate through existing combat/siege scaling. Do not double scale.
- New unit-specific weaknesses do not silently rewrite classic faction affinities.
- Channels, returns, and structural hits must have recorded timing, finite uses, cancellation, capacity, and replay-safe consequences.

Read every row of Appendix A. Its adaptations are intentional. Do not silently revert to the bible's old rules or invent different mechanics for convenience.

## Visual and UX quality is a requirement at every milestone

Apply final-design Section 20 throughout, not only at the end. Inspect the actual current UI and reuse coherent shared components/tokens.

The desired result is a premium, character-led fantasy game with strong composition, readable typography, atmospheric art, restrained accents, and effortless decisions. Keep Alegreya headings and Fira Sans body text. Preserve the established charcoal/ivory/brass/ember language. Avoid box-heavy dashboards, nested cards, excessive badges, tiny text, generic stock controls dropped into otherwise polished screens, and repeated explanations.

No new screens for this feature. Improve the existing roster, hero sheet, recruit card, board, visitor, counter, Forge guidance, siege forecast, and fight report as necessary. Keep day/siege urgency conspicuous. Show owned versus loaned equipment, notice dates, Speciality resolution timing, quest return timing, and meaningful actions clearly.

Design normal, selected, unavailable, empty, long-name, pending, and error states. Preserve scroll position, selected sub-tabs, and Back behaviour. Respect 48 dp minimum/52 dp primary touch targets, system text scaling, contrast, TalkBack, reduced motion, and no colour-only meaning.

For every touched milestone, render representative real Compose screens when the environment permits, inspect them, and fix the visible defects. Check approximately 360 dp and a larger phone width, 1.0×/1.3× text, and a 2.0× stress check for critical dates/names/actions. Use the state matrix in Section 20.8. Include long names, multiple conditions, missing-art placeholders, urgent siege/concern states, channel/Poison replay, and competing personal/siege quests.

A passing JVM test does not prove UI quality. A web mockup does not prove Android layout. Do not claim rendered or device verification when it did not run. If Android is unavailable, complete feasible implementation, add previews/fixtures, record the exact blocked visual checks, and continue other work.

Do not generate final portraits or enemy art. The owner will supply it. Use coherent neutral placeholders with stable asset IDs and produce an exact art-needed list.

## Safety of state, saves, and existing gameplay

Preserve one authoritative weapon location, real gold flow, no dead/departed actions, no conflicting commitments, no duplicate rewards, and deterministic seeded outcomes. Never trade in a loan. Never change a frozen quest loadout retroactively. UI and replay rendering cannot consume gameplay RNG.

Implement the legacy/new-era revision boundary exactly as specified. Old ongoing eras remain loadable and retain their previous behaviour and pending manual choices. New eras receive the feature. Verify loader compatibility, not just Kotlin default values. New streams or behavioural initialization may require migrations. Do not rewrite stored names, historical prose, current equipment, old Specialities, or a committed old forecast.

Keep state and history bounded. Use actual version bumps appropriate to the current branch. Put tuning in the existing configuration/content system. Do not hardcode game numbers in UI or invent next version numbers without reading the current ones.

## Working method and evidence

Start by confirming the implementation map and writing a compact milestone checklist and content coverage ledger. Then implement. Keep coherent commits under repository conventions. Track completed/current/next work in durable project notes so a context reset can resume without redesigning or repeating finished work.

Run the repository's actual supported commands. Expected areas include core tests, content validation, combat fixtures/lab, guild simulation and paired interventions, migration/determinism tests, prose checks, appropriate app checks, and visual/device checks where possible. Read their current invocation before running them.

Use final-design Section 18 for required causal scenarios. Do not write tests that merely repeat every data constant. Use one catalogue validator and meaningful behaviour/ownership/timing fixtures. Preserve legacy golden-state guarantees, and add separate expectations for new-revision eras.

Measure purchases/swaps, Speciality diversity, useful Hooks, board pressure, departures, first-siege viability, survival, gold flow, army repetition, runtime, and save growth. Use documented tuning/holdout seed blocks. Run matched interventions to show the smith's actions matter. Do not force arbitrary >50% decision volatility or present a handful of examples as balance proof.

Do not claim an Android build from stubbed or desktop compilation. Report exactly which checks ran, what passed, what failed, and what the environment blocked. Fix failures attributable to this change. Preserve unrelated working-tree changes.

## Completion report

Return:

- Concrete implemented behaviour and milestone status.
- The complete content coverage ledger, including explicit approved mappings and deferred items.
- Tests, simulations, and causal evidence, with honest limitations.
- UI/UX changes and actual visual/accessibility verification status, including unresolved defects.
- Save/version compatibility and remaining risks.
- Missing art manifest and precise next manual/device checks.
- Branch and commit identifiers, and whether anything was actually pushed.

Update the current GDD, decision history, Writing Guide, progress/checklist records, and a brief integration notice at the top of the Content Bible linking to the final design. Preserve its creative source material and the historical review.

Do not merge to main, sign, publish, release, or deploy as part of this task. Follow existing authorization for any push. Carry the implementation through the stated scope instead of stopping after a plan, the first slice, or a mechanically complete but visibly unfinished interface.
