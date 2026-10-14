# Handoff: continuing the 2026-10-14 review in a cloud session

Start here. Branch `review-2026-10-14` holds all of it; nothing is merged into `main` and nothing may be until the owner
says so. Read `CLAUDE.md`, then this file, then the top section of `docs/PROGRESS.md`.

## What the owner asked for
- The whole review (`docs/REVIEW_2026-10-14.md`) implemented under the owner's decisions A to M, recorded at the end of
  `docs/DECISIONS.md` ("Owner decisions on the 2026-10-14 review"). Decision A: hidden random odds in band words, never
  counts. No LOCKED GDD rule may move.
- UI is a main goal: a visibly better, more readable game, not only weights and borders. The owner's latest direction
  (2026-10-14, with screenshots): deliberate hierarchy (Alegreya Bold for headings only; Fira Sans Regular for sentences,
  Medium for labels, SemiBold for values and actions), gold only for the primary action and the chosen mark, red only
  for short costs, charcoal surfaces / ivory text / brass accents / ember highlights, no ornamental frame on every
  section, gains and costs as very clean, concise bullets, no default selection, the whole card tappable, a check on the
  chosen card. Apply this language to Shop, Forge, Guild, visitors, relics and loans, each adapted to its purpose.
- Copy: concise, no em dashes or semicolon chains, "commission" as the one word for a paid order.
- 0.9.0 is held until a real-phone pass and gates G3, G12, G13, G14 have evidence. Store publishing and signing are the owner's.
- Commit per item on the branch, a CHANGELOG `[Unreleased]` line for every user-visible change, PROGRESS kept current.
  Post and re-post a ticked todo list in chat as work progresses.

## Done on the branch (newest last)
- Plan items 6 and 7, decisions H (bundled fonts), D (`appCategory="game"`), G (template backup rules removed),
  I (opening: tap a card, one "Begin with X", no dialog), C (debug entries under Settings), DECISIONS A to M recorded.
- UI round 2 (commit 99354a8): the owner's painting as the full-screen title background
  (`Assets/Implemented/backgrounds/drawable-nodpi/bg_title_workshop_night.png`, imported by `tools/pixelart/import_assets.py`),
  a separate run-setup screen after "New game" (`ui/RunSetup.kt`, tags `run_setup`, `setup_begin`, `setup_back`,
  rows `charter_row_<id|none>`), charter gains and costs as bullets derived from each charter's numbers
  (`charterGains` / `charterCosts` in `ui/CharterPicker.kt`), opening cards with the shared signers shown once, trait per
  signer, relic row that expands on tap (`opening_relic_<id>`), costs as bullets (`OpeningDef.costNote` plus the gold and
  health deltas), `EffectBullets` and the checked `ChoiceRow` in `ui/Frames.kt`, Regular/SemiBold Fira Sans in
  `theme/Type.kt`, charcoal palette in `theme/Color.kt` and `Theme.kt`. Emulator scripts and two device tests follow the
  new setup step. Catalog fingerprint re-pinned for `costNote` as prose (content version unchanged, saves still load).
- Core correctness fixes from a background agent (rows 21, 22/B, 31, 32, 33, 34, 36a, 36b, 36c/M): see `git log` for
  "review row". Balance is 15 after them; scenario saves were rewritten.

Verified: `:core:test` and `:app:testDebugUnitTest` green at 99354a8 (before the agent merge; rerun after it). Title,
run setup and opening chooser seen on an emulator at font scale 1.0 and 2.0. Not run on the branch: the instrumented
suite and the emulator scripts.

## Cloud session result (2026-10-14)

Items 1 to 6 and 8 are done and pushed, one commit each (2a464e1, 6c3ced5, 35ac0f6, 0b5698d, d387888, 1d120af, d0b2900,
47d0bd0). Item 7 is done as far as a machine without an emulator can: the stale tests are fixed and type-checked, nothing
ran on a device. The three-column report is `docs/REVIEW_2026-10-14_REPORT.md`; the open owner decisions are one list
at the top of DECISIONS. Next, on a machine with the Android SDK: `./gradlew :app:testDebugUnitTest :app:assembleDebug`,
then the device batch below (item 7), then decision E's removal.

## Still to do, in order (as handed over; see the result above)
1. Shop: the next useful action first, less clutter and repeated coaching, siege urgency in proportion; the listed
   weapon shown once; End Day never covering the last row.
2. Forge: K ("List at suggested" primary on the result card, "Set price" secondary opening the pricing sheet),
   L (one line from the real reservation state: reserved for which commission, replacing "No weapon is set aside for it."
   in `Dialogs.kt`), A (band words for Reckless flaws and the Heartfire breakthrough), slot labels kept in view when a
   tray opens, guidance never over the result, Heartfire confirmation stating cost and consequence.
3. The 12 `AlertDialog`s (relic, abandon, loan, ...) onto `ForgeDialog`; relic, loan and visitor choices onto `ChoiceRow`
   with `EffectBullets` for gains and costs.
4. Guild board and roster: lead with the danger word and the reward, one line each, detail on the sheet.
   J: one line explaining members at home (automatic recovery), docs clarified.
5. Gameplay batch with the simulator: worthwhile premium pricing, a decision after day 10, accessible advanced forging,
   alternatives to sabotage, the synergy band 40-67 (keep only if it supports build variety), militia cost and
   contribution made clear. Targets are evaluation criteria; record numbers in DECISIONS.
6. Reliability left: reproduce and fix the oversized save (2 MB cursor window) with a migration and a recovery path
   that never deserializes the broken row (delete by id without selecting the blob).
7. Device batch (needs an Android emulator, so it may have to wait for the owner's machine): the stale instrumented
   tests, every script in `tools/emulator`, a 600 dp screen for D, before/after captures of the whole journey.
8. E: archive the bulky review screenshots outside the tracked tree (keep a small reference set and an index, no
   history rewrite). Consolidate DECISIONS and the release docs. Final report in three columns: emulator-verified,
   unit-tested only, not run.

## Notes for a cloud machine
- An Android SDK may be missing there: `:core:test`, `:core:simulate`, `:core:guildsim` need only a JDK; the `:app`
  tasks need the SDK (`local.properties sdk.dir`). Say plainly which checks could not run.
- New gameplay numbers go into `BalanceConfig`'s nested groups (the constructor is at its JVM limit); a semantic balance
  change raises `BalanceConfig.version` and re-pins `VersionFingerprintTest`, then `./gradlew :core:scenarios`.
- Display text in content is hashed by presence only when its field name is in `VersionFingerprintTest.prose`.
