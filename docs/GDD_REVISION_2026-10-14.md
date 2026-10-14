# GDD revision, 2026-10-14: player-facing language

Record of the language decisions of 2026-10-14. Unlike the earlier addenda, these were written into
`Tiny_Blacksmith_GDD_v1.0.md` itself (decision register round 9 "Disclosure", its conflict resolutions, and section 12),
on the owner's instruction that their request overrides the GDD. Where this file and `docs/GDD_REVISION_2026-10-10.md`
disagree about what a player reads, this file holds. It changes no rule, number, reward or outcome. The full style guide
and glossary are `docs/WRITING_GUIDE.md`.

**Status.** Requested by the owner on 2026-10-14 (clarity and writing pass, "use upgrade or similar RPG language for
Hone"). Names remain PROPOSED as the GDD says; the terms below are the ones the game uses until the owner changes them.

## 1. Disclosure (revises the GDD 2 decision register, round 9, "Descriptive indicators, not explicit probabilities/formulas in UI")

The 2026-10-10 revision made deterministic effects exact in a guild run. That now holds in every run:

| Kind of information | How it is shown |
|---|---|
| A deterministic effect, cost, threshold, duration or limit | Exactly, with numerals and percentages: "+6% attack in fights away from town", "Once per round", "for 5 days" |
| A random chance (risk, quality spread, flaw rolls, break chances, recipe odds, siege outcome) | Descriptive band words, never a number (owner decision A of the 2026-10-14 review). `LaunchContentTest` guards catalog text against a percentage beside chance wording |
| Formulas (quality, power, pressure) | Not shown. Their results are shown as values and band words |

Open item for the owner: the legacy upgrade previews still show odds as counts ("2 in 100", `legacy/Legacy.kt`), a
design that predates decision A. They were left as they were.

## 2. Terminology

| Player-facing term | Replaces | Meaning |
|---|---|---|
| **Quest**, quest board | contract, mission (guild work) | Work a guild party is sent on. Code keeps `Mission`, `contract`, `IncomeKind.CONTRACT` |
| **Commission** | (unchanged) | A paid order for a weapon at the shop. Never used for a quest |
| **Retreat rule** (Cautious, Balanced, Reckless) | posture | When a party retreats from a fight. Code keeps `Posture` |
| **Retreat** | pull back, driven back | A party or hero leaving a fight alive, with no reward |
| **Upgrade** (first time) / **Repair** (afterwards) | hone | One action at the forge (`Command.Hone`). The first time it raises quality by `honeQualityBonus` (6) and restores condition to 100. Every later time it only restores condition. The button names the one that will happen. Legacy purchases stay "legacy upgrades" |
| **Effect** (weapon effect, party effect) | rule | What a weapon, trait or relic does in a fight. "Discovered weapon effects" in the Notebook |
| **Enemy**, priority target | foe, "the foe that matters" | Priority target = an enemy summoner first, then the enemy leader, then the enemy with the most health (`Aim.PriorityFoe`) |
| **Practice fight** | the bench, rehearsal | The forge's made-up test fight |
| **Forge health** | forge integrity (in player text) | The GDD's forge integrity. At 0 the run ends. Weapon "Repair" never restores it: GDD 8's "no direct gold-funded guaranteed repair button" stands |
| Once per round / Once per fight | once a round, twice a fight | Effect limits |

Original mechanics keep their names and are explained where they appear: Guard, Charge, Mark, Chill, Burn, Bleed,
Regeneration, Wet, Steam, Thorns, Scrap, Heartfire. They are not renamed to tabletop RPG mechanics with other behaviour.

## 3. Reports and choices

- Daily and fight reports lead with the outcome, then losses, gains and required actions. The full combat log stays
  behind its disclosure ("Show the full combat log").
- A quest shows its situation, then objective, reward, fee, timing (leaves at End Day today, home on the morning of
  day N) and danger as separate rows. A missed siege is shown on the board card and again above "Send the party".
- A visitor's options name what the player chooses and state any permanent result ("The weapon leaves town for good.").
