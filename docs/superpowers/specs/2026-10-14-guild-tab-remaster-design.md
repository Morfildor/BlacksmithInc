# Guild tab remaster and siege header: design and checklist

Date: 2026-10-14 · Branch `guild-tab-remaster` (worktree `.claude/worktrees/guild-remaster`, from `117a3a2`) · Not merged.

## Why

The Guild destination is one long scroll of about ten fully expanded sections (party, wall, yesterday, board, roster,
candidates, region, factions, townsfolk, fallen). Nothing at the top says what needs the smith today, and what opens
what (contracts by day, the charter, ranks) is invisible. Across the workshop the day and the siege are weak: a small
"Day N" stat, then separate countdown strips on Shop and Forge and a siege-day card on Shop.

Success: on any morning the player sees the day, the siege countdown and the one to three things that need them,
reaches each control in one tap, and can read what is still to come without reading prose.

## Boundaries

- UI layer only (`:app`). No change to `:core`, content, balance, siege scheduling or combat. Every fact shown is read
  from the save, the content catalog or the engine's existing reads (`wallForecast`, `memberUnavailable`,
  `Threats.of`, `siegeForecast`, `openBranches`). Nothing is simulated.
- Classic runs keep their mechanics and route siege preparation to Town. No Guild-only control appears in them.
- Existing test tags stay. New ones are added; the device scripts gain a sub-tab tap before what they check.
- Coordination: another agent is adding the deep road (rules 7) in the main checkout, including one line in
  `GuildUi.rewardWords` and edits in `ShopPanel`, `ShopUi`, `ForgePanel` and `WorkshopScreen`. This branch does not
  touch `rewardWords`, and keeps its edits in those files to separate regions. The deep road becomes one entry of
  "What comes next" after their work lands (a one-line follow-up, recorded under Pending).

## 1. Workshop header (all four destinations, planning only)

```
 DAY 14                         170g  ⚡10  ⚙      Alegreya Bold day; gold, energy, Settings as a compact group
 Siege tomorrow · Day 15 · Ashclaw Raiders    ›   tappable siege strip
 [12][13](14)[15⚔]                                 the current cycle's day markers
 2 of 3 defender places filled                     eve and siege day only
```

- Day: the strongest text in the header (`HeadingFamily`, Alegreya Bold). Gold, energy and Settings sit to its right as
  one smaller group; at large text the group wraps under the day instead of shrinking it.
- Siege strip, from `state.day` and `town.nextSiegeDay` only (never modulo): "Siege in N days · Day D",
  "Siege tomorrow · Day D", "SIEGE TONIGHT · Day D". The besieger's name follows only when it is known under the
  existing reveal rules (guild: `wall.committed`; classic: the warned threat).
- Tone: ordinary days calm (cream on the panel); tomorrow amber/gold emphasis; tonight a restrained ember background
  and a stronger border. No flashing or looping animation.
- Cycle markers: the days from `max(1, nextSiegeDay - siegeInterval + 1)` (never later than today) to `nextSiegeDay`,
  each labelled with its day number. Today outlined, past days muted, the siege day carries the existing siege-wall
  sprite. The countdown text never depends on them; at font scale ≥ 1.3 or a short screen they are hidden first.
- Defender line on siege eve and siege day: guild, "N of 3 defender places filled" from the wall forecast (members
  and eligible townsfolk alike); classic, the same words from the siege forecast's champions and the configured
  champion count. Never "you will win"; no reserved-member count here.
- Tapping the strip: guild run → Guild with the Wall sub-tab (an explicit navigation, so it overrides the remembered
  sub-tab); classic run → Town.
- Height: roughly 96 to 112 dp at normal text; it may grow with text size, and the markers go before the day or the
  countdown would be cut.
- During trading and the siege replay the header is not shown: the shop-day screen has its own day strip that names the
  day being watched (`model.day`) and no countdown, so the next siege is never shown while the current one plays.
  After the day is acknowledged the header reads the advanced save.
- Removed as redundant: the countdown-only `ThreatStrip` on Shop and Forge and the Shop's siege-day card. What only
  they said is kept: the matchup (besieger · Weak to · Resists) and the classic outlook move to one slim line on Shop
  and Forge, shown only once the siege is warned; the buyers note ("Buyers now consider…") stays on the Shop on its
  first warned day; forge health stays on the Wall / Town.
- End Day on siege day: the button keeps its name, and its supporting line is
  "Trading first. The siege follows tonight." Existing blockers (opening, blessing) still come first; the guild's own
  End Day lines still follow. No new dialog.

## 2. Guild destination

Pinned at the top, under the header: **Today** (at most three rows, only actionable ones, no filler) and the four
sub-tabs **Contracts · Company · Wall · Charter**, each with a dot when something in it needs the smith.

Today rows, in priority order, first three kept. Each button names its action and goes to the control, never copies it:

| When (from the save) | Row | Button → goes to |
|---|---|---|
| Party at a checkpoint with no word sent | "The party waits at the deeper door" | Decide → Contracts, party card |
| Siege tonight or tomorrow and a defender place empty | "A place on the wall is empty" | Assign defender → Wall, assign sheet |
| A member held with a rescue on the board | "Maren is held · 2 days left" | See rescue → Contracts, that offer |
| Party home, nothing planned, a contract open | "No contract planned today" | Choose contract → Contracts |
| A member has an open branch | "Crispin can choose a branch" | Choose branch → their hero sheet |
| Charter secured today or yesterday | "Charter secured" | Retire or rank → Charter |

A siege row that would only repeat the header's countdown is never added. With nothing actionable, Today is one short
line ("Nothing needs you in the guild today"). On a short screen or at font scale ≥ 1.3, Today shows its first row and
"N more" which expands in place.

Sub-tab memory: the chosen sub-tab is remembered with the day it was chosen (saved-state, survives process death).
On the first Guild visit of a new day the default applies (Wall when the siege is today or tomorrow, else Contracts).
The day only changes through End Day and the shop-day screen, so the default never switches under the player.

**Contracts.** The party that is out (checkpoint buttons Push deeper / Return as now). Yesterday's contract as one
line, tapping expands its lines. The board: compact cards with title, chips (danger as word and colour, days, fee),
the first day's reward in one line, a special-reward chip when the reward is more than gold and stock (a relic, the
supplier's thanks, spoils the siege's preparation, a member or a blade brought home, Heartfire), and one "Plan party ›"
button (or "Change party ›" when planned). The contract sheet is unchanged. Under the board, collapsed, **What comes
next**, derived from the rules (`Missions.refreshBoard`): drawn contracts whose `minDay` is after today ("Hunt · may be
posted from day 2"; none when the charter's `earlyDanger` opens them), the siege's jobs ("Sabotage and one opportunity ·
posted once the besieger is known"), Rescue ("posted while a member is held"), Recovery ("posted while an enemy carries
a blade of this forge"). Only what is not already on the board is listed.

**Company.** Roster rows: face, name, class and level, health, one status chip (Fit, Wounded until day N, Away until day
N, Held · N days left, Kept for the wall), the blade, a dot for an open branch; a tap opens the hero sheet. The
"at home they rest and train" line (owner decision J) stays as a footnote. **Recruits** collapsed with a count; each
row with "Recruit · N gold" or its blocked reason. Townsfolk and the fallen move to Records (guild runs only).

**Wall.** Header: besieger sprite and name once known, when, Weak to / Resists chips. Forge health bar, the watch in
one line. The three places: each filled place shows the defender (member or townsfolk) and, for a member, Release when
kept; an empty place shows "Assign". Assign (and "Keep a member for the wall" under full places) opens one sheet
listing every guild member: Reserve when the rules allow, otherwise the actual reason inline (away until day N, wounded
until day N, held, in today's planned party, all places already kept), the same checks as `GuildOps.reserve`. Then
collapsible **Siege preparations** (the preparation, its answers and the sabotage, as now), **Battlefield** (the
enemy field) and **Unavailable defenders** (each hero with the real reason and return or recovery day). On siege eve
and day they start open.

**Charter.** The charter, its advantage and constraint. **What opens**, a list, not a path, each item with its state
(done, open now, not yet) and its condition from the rules: Charter secured (beat the charter warlord, who leads the
first siege on or after day 20; held off means he leads the next one too) → opens Retire and Ranks; Ranks I–III (any
higher rank may be taken once the charter is secured, never lowered; each says what it adds). Retire and Take rank
keep their confirmation. Then collapsible Law of the season, The other guild, Nemesis, Chains seen, Pressing on the town.

**Records.** In a guild run a fourth page, Townsfolk: the living by fame, then the fallen and retired, collapsed.

## Files

- `ui/WorkshopScreen.kt`: `TopBar` → header + siege strip + markers; remove the Shop strip argument; End Day note.
- `ui/SiegeHeader.kt` (new): the header's model (pure, tested) and composables.
- `ui/ShopPanel.kt`, `ui/ForgePanel.kt`: drop the countdown strip and the siege-day card; the slim matchup line.
- `ui/ShopUi.kt`: `ThreatUi.besieger` (the known name alone).
- `ui/GuildUi.kt`: additive (Today rows, upcoming contracts, opens list, assign rows, special-reward chips, statuses).
- `ui/GuildPanel.kt` (frame, Today, sub-tabs) and `ui/guild/` (one file per sub-tab, the assign sheet).
- `ui/RecordsPanel.kt`, `GameViewModel.kt`: the Townsfolk page; the Guild sub-tab memory and `openWall()`.
- `ui/Frames.kt`: a chip and a collapsible header, from the existing theme.
- `tools/emulator/guild.sh`, `journey.sh`, `runvariety.sh`: tap the sub-tab first.

## Checklist

- [ ] 1. Spec written and committed
- [ ] 2. Header model + tests (countdown words, cycle markers from timing, defender line, besieger reveal)
- [ ] 3. Header composable in WorkshopScreen; strips and Shop siege card replaced; End Day line on siege day
- [ ] 4. Guild model additions + tests (Today priority and cap, upcoming contracts, opens list, assign reasons, tab default)
- [ ] 5. Guild frame: Today, sub-tabs with dots, tab memory in the ViewModel (saved-state)
- [ ] 6. Contracts, Company, Wall (with assign sheet), Charter sub-tabs
- [ ] 7. Townsfolk page in Records (guild runs)
- [ ] 8. Device scripts updated for sub-tabs
- [ ] 9. `:app:testDebugUnitTest` and `:core:test` pass; `assembleDebug` builds
- [ ] 10. Device pass on a read-only AVD: day 1, siege eve, siege day before End Day, trading/replay, next morning,
      strip → Wall, save/reload, small screen, large text; screenshots sent
- [ ] 11. CHANGELOG `[Unreleased]`, PROGRESS, GUILD_EVOLUTION_PLAN ledger line

## Pending (recorded, not done here)

- The deep road as an entry of "What comes next" and a special-reward chip, once the other agent's `Reward.road` and
  `DeepRoad` land.
- Anything the environment cannot run is marked pending in PROGRESS with the reason.
