# Run variety and consequences: evidence report (2026-10-11)

Branch `run-variety`, not pushed. Rules 6, save schema 7, content 6, balance 12. Design decisions and gates:
`docs/RUN_VARIETY_LEDGER.md`. All tables are bots, not people. Whether it is fun is the owner's to judge.

## How it was measured
- Tuning used seeds 1 to 300 and 5001 to 5200. Everything below is from seeds the tuning never saw (100001 on).
- "Before" is `main` `5b7e1db` (balance 11), 300 seeds: `before_guildsim_300.txt`.
- "After": `after_tlh_1000.txt` (Town's Last Hope, new account, 1,000 seeds, 13 policies), `after_licensed_300.txt`,
  `after_honest_300.txt`, `after_tlh_maxed_300.txt`, `after_tlh_playon_300.txt` (plays on to day 40 instead of
  retiring), `after_classic_300.txt` (runs without a charter).
- A run that reaches the horizon with neither a charter nor a fallen forge is censored. It is not a win.
- Every bot sends only engine commands and reads only what a player can see. No command was refused in any run.
- Reproduce: `./gradlew :core:guildsim --args="--runs 1000 --seed 100001 --byOpening x --pair ADAPTIVE,IGNORE_COUNTER"`.

## Legendary weapons
| | Before (300 seeds) | After (1,000 seeds) |
|---|---|---|
| Legendary forged from quality alone, a run | Synergy 6.2, Adaptive 5.2, Defence first 4.0, Highest stat 7.6 | 0 in every policy, charter and account (13,000 guild runs, 1,200 classic runs) |
| Runs with a Legendary weapon | 97 to 99 % (competent policies) | Synergy 70 %, Adaptive 57 %, Defence first 44 % |
| First Legendary, mean day | 4.8 to 6.4 | 21.3 to 22.6 (competent), 13.9 (Legend seeker) |
| A run: median, p90 | 4 to 8, 7 to 11 | 0 to 1, 1 (competent). Legend seeker 1, 3 |

- Where they come from now (a run, Town's Last Hope): Synergy deeds 0.73, Heartfire 0.01. Adaptive deeds 0.57,
  Heartfire 0.04. Legend seeker deeds 0.47, Heartfire 0.82 from 1.97 forgings (about two in five break through).
- Going after Heartfire costs something: Legend seeker secures the charter in 34 % of runs against Adaptive's 50 %.
- A maxed account earns more but not for free: Legend seeker mean 1.90, p90 4, first on day 11.5. The competent
  policies stay at median 1, p90 1.
- Rarity of what is forged (Synergy): Common 9 %, Uncommon 33 %, Rare 41 %, Epic 17 %, Legendary 0 %.
- Classic runs to day 40: 0 forged from quality, 0.16 to 0.21 a run by deeds (15 to 18 % of runs), 0.29 to 0.33 on a
  maxed account. Classic bots do not spend Heartfire, so Heartfire forging in a classic run is unmeasured.
- Honing an Epic past quality 85 no longer makes it Legendary (`RunVarietyTest`).

## Difficulty (charter secured / forge fallen / neither by day 30, percent)
| Policy | Before, Last Hope | After, Last Hope (1,000) | 95 % interval | Licensed (300) | Honest (300) | Maxed, Last Hope (300) |
|---|---|---|---|---|---|---|
| Synergy | 81 / 0 / 18 | 68 / 3 / 30 | 65 to 70 | 48 / 6 / 46 | 65 / 4 / 30 | 80 / 0 / 20 |
| Adaptive | 76 / 0 / 24 | 50 / 2 / 49 | 47 to 53 | 23 / 10 / 67 | 48 / 2 / 50 | 63 / 0 / 37 |
| Defence first | 79 / 0 / 21 | 38 / 6 / 55 | 35 to 41 | 18 / 20 / 62 | 44 / 7 / 50 | 55 / 0 / 45 |
| Takes the first of everything | not measured | 24 / 31 / 45 | 21 to 27 | 11 / 47 / 42 | 15 / 37 / 47 | 39 / 0 / 61 |
| Novice (supply runs only) | 20 / 17 / 63 | 13 / 47 / 40 | 11 to 15 | 3 / 79 / 18 | 12 / 50 / 38 | 46 / 1 / 53 |
| Ignores the scouts | not measured | 3 / 19 / 79 | 2 to 4 | 1 / 35 / 64 | 5 / 22 / 73 | 7 / 0 / 92 |
| One recipe only | not measured | 0 / 83 / 17 | 0 to 0 | | | |

- The three competent policies average 52 % under Town's Last Hope (band asked for: 40 to 65). Synergy is 3 points
  above the band and Defence first 2 below. Licensed Bad Ideas is harder: 30 % on average, under the band.
- Each siege in turn, Adaptive: held outright 81 / 66 / 75 / 17 %, forge damage 1.3 / 3.6 / 2.6 / 16.5. Novice:
  45 / 33 / 37 / 3 %, damage 8.1 / 12.4 / 12.3 / 32.0. Before, every active policy left the first siege at 94 to 100.
  A prepared wall still usually takes nothing on day 5. A neglected one is breached about a third of the time.
- Played on past the charter to day 40: Synergy 74 / 16 / 11, Adaptive 52 / 14 / 34. The forge does fall afterwards.

## Choices that matter
- **Reading the scouts.** Same 1,000 seeds, Adaptive against the same smith who ignores preparations, weaknesses and
  the sabotage: charter only for Adaptive on 475 seeds, only for the other on 11, both 20. The one who ignores them
  takes 41.0 more forge damage over the first four sieges (standard error 1.1).
- **One preparation, one night.** `RunVarietyTest`: over 40 seeds the same night is fought with the preparation and
  with it spoiled. The unspoiled one is worse more than twice as often as better.
- **One recipe.** An iron sword with ember resin for everybody: 0 % charters, the forge falls in 83 % of runs.
- **Supply farming.** Novice: 13 % charters, 47 % defeated, contract gold 200 a run (527 before). Its end gold is
  still high (2,465) because it sells what it forges and spends little. Wealth it cannot turn into a wall.
- **Recommended choices.** Taking the first of everything: 24 %. Playable, well behind adapting.

## Openings (Town's Last Hope, 1,000 seeds, charter rate by the package the run drew)
| Opening | Synergy | Adaptive | Defence first |
|---|---|---|---|
| Paid in Blood | 76 | 67 | 58 |
| The Overflowing Cup | 70 | 57 | 47 |
| The Plain Oath | 68 | 39 | 25 |
| Break It on Purpose | 62 | 46 | 38 |
| The Stormwell | 63 | 42 | 28 |

- Pooled over the three, every package is between 0.86 and 1.31 times the mean. For Defence first alone Paid in
  Blood is 1.5 times its mean and The Plain Oath 0.66: that bot forges little, so a free weapon helps it most.
- What members carry on day 5 differs by package for the bots that follow a package's plan. Synergy, most common
  loans: Plain Oath axe/frost 382 of 912, Overflowing Cup sword/frost and sword/storm with staves for the Warden,
  Paid in Blood axe/fire first (the package's own axe).
- Limit of this evidence: the bots know two packages' plans well (Plain Oath, Overflowing Cup) and the rest only
  through what a weapon is worth to them. Counts of different weapons are not proof of different strategies. The
  three packages only the other two charters offer (Borrowed Fire, The Marked Card, Careful Books) have charter rates
  in `after_licensed_300.txt` and `after_honest_300.txt` but no plan-following bot.

## Setbacks and recovery
- Rescue is won 70 to 89 % of the time by the competent policies (57 to 79 % before). Hunts are won 94 to 98 %.
  So a rescue is still harder than the hunt that caused it. Gate G7 is not met.
- Days with nobody fit to send: Adaptive 0.41 a run, Synergy 0.05.
- Captured and brought out, a run: Adaptive 0.68 and 0.39.

## Three played runs
From `transcripts_raw.txt`. Reproduce with
`./gradlew :core:guildsim --args="--seed N --policy P --charter C --transcript x"`. These are bot runs.

**1. Seed 100003, Honest Business, Adaptive, The Plain Oath. A build of plain weapons.**
- Day 1: begins 60 gold short. Scouts: a gravecaller behind the line on day 5.
- Day 3: the party spoils it. Day 4: nobody went with the supplier, who brings less until day 9.
- Day 5: carried a common Iron Axe (Ranger, Oathkeeper), a common Iron Axe (Guardian), an uncommon Bronze Sword.
  Held at a cost, 3 damage.
- Day 10: repelled outright. Day 15: held at a cost, 3 damage.
- Day 20: Broodmother Vyrash breaks through, 21 damage. By now the party carries Epic frost axes: the oath was dropped.
- Day 23: Maren is taken alive at a sabotage with her sword. Day 27: brought out. Still standing on day 31, no charter.

**2. Seed 100012, Town's Last Hope, Legend seeker, The Overflowing Cup. A costly forging, a capture and a rescue.**
- Day 2 to 3: takes the supplier's road instead of the sabotage. The supplier brings more until day 8.
- Day 5: the second drake nobody spoiled comes. Breached, 14 damage.
- Day 9: the starfall site, one Heartfire. Day 10: a Heartfire forging, Undead Bane Starsteel Sword, quality 82,
  Legendary. That night Warlord Krag breaks through, 15 damage: the gold went into the sword, not the wall.
- Day 12: Onora the Warden is taken alive in a delve with her Moonsteel staff. Day 14: brought out.
- Day 21: taken again on a recovery, out on day 24. Standing on day 31 with no charter and three breaches behind it.

**3. Seed 100021, Licensed Bad Ideas, Greedy diver, Careful Books. Greed that does not pay.**
- Day 1: the forge starts 15 health down. A member is taken alive in a delve on the first night.
- The forge falls on day 15.

## Tests and builds as run on the final tree
- `./gradlew :core:test`: 581 tests, 0 failures (561 before). New: `RunVarietyTest`, 20 tests.
- `./gradlew :app:testDebugUnitTest`: 217 tests, 0 failures (208 before). New: `RunVarietyUiTest`, 9 tests.
- `:app:assembleDebug` built. `:app:compileDebugAndroidTestKotlin` built on the app branch before the merge.
- `./gradlew :core:scenarios`: 33 saves rewritten for rules 6.
- Classic golden state: no RNG stream moved, and with the three new record kinds left out every one of 300 lines
  equals the rules-5 file. That golden plays balanced iron swords only: it does not cover Safe, Reckless or Heartfire.
- Six 0.8.0 guild saves load, keep every weapon unchanged, and play on (`RunVarietyTest`).

## Device (emulator, Pixel_10_Pro AVD, read-only, 1080x1920, 420 dpi)
Seen, screenshots in `screens/`:
- The opening chooser with all three packages, the confirm, the first morning after it.
- Process killed on the chooser: it comes back on the chooser.
- Day 2: the two-day sabotage with the preparation's own second day, the Starfall site marked Opportunity with
  "One party cannot do this and the sabotage", the supply card saying the road was worked.
- The board at font scale 1.3.
- The Heartfire row on the workbench at 0 of 3 with its two explanation lines.
- `tools/emulator/guild.sh` to the next morning, with its two older script misses (roster row, last evening button).

Not seen on a device:
- The Heartfire switch turned on and a Heartfire forging made. No scenario save holds Heartfire.
- The wall card with a preparation (the screenshots start below it), a siege night's cause lines, the supplier's
  mood in Supplies, a weapon sheet with a legend line, the opening chooser at a large font.
- Nothing on a physical phone. No TalkBack. `connectedDebugAndroidTest`, `smoke.sh`, `runend.sh`, `scenarios.sh` not run.

## Not done or only partly done
- G7: rescue is easier than before but still harder than a hunt.
- Charter rates: Synergy a little over the band, Licensed Bad Ideas under it.
- Mission modifiers are three, and none changes an objective. Enemy rosters vary only by faction, modifier and preparation.
- The supplier is left alone in most bot runs, so its "fewer stock" state is on about half the time. Whether that
  reads as a consequence or as a tax needs a person.
- Classic runs gain Heartfire and deeds but none of the openings, preparations or opportunities.
- "Two midrun chances to specialise or pivot" is the relic draft after sieges 1, 2 and 3 (a party rule, a forge
  tool, a pact). There is no separate pivot event.
- Version name is still 0.8.0. The changelog entry is under Unreleased.
