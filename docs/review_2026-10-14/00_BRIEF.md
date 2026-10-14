# Review 2026-10-14: common brief for every reviewer

Repository: `C:\Users\tuncb\Desktop\Blacksmith Project` (git `main` at a2e0392, clean). Premium offline portrait Android
game, Kotlin. Read `CLAUDE.md` first: it states the LOCKED rules, the architecture boundaries and the commands.
Design source of truth: `Tiny_Blacksmith_GDD_v1.0.md` as revised by `docs/GDD_REVISION_2026-10-10.md`.

## What is already known (do not repeat it, confirm or refute it)
- `docs/Tiny_Blacksmith_Thorough_Review_2026-10-09.md`: external review of app 0.5.0 (the shop day, data needed, findings F01..F10, A01.., G01..).
- `docs/UI_UX_REVIEW_2026-10-10.md`: UI/UX review of 0.7.0 with a ranked table of 12 causes and a 5-phase plan.
- Since then: 108 commits (`git log ea01669..HEAD`): guild evolution (0.8.0), gameplay depth, run variety (rules 6),
  deep forging, a guided first day. `docs/PROGRESS.md` (top sections), `docs/DECISIONS.md` (last sections, from line 3102:
  "Known issues left open", "Open owner decisions"), `docs/RUN_VARIETY_LEDGER.md` (open questions), `docs/DEEP_FORGING_LEDGER.md`.
- Ground truth on this tree, run today: `:core:test` 622 tests 0 failures, `:app:testDebugUnitTest` 246 tests 0 failures,
  `:app:assembleDebug` built (21.4 MB APK). PROGRESS lists 9 failing instrumented tests and 3 failing `scenarios.sh` checks.

## Owner's words for this review
"Few implementations are done. Do a very deep review of everything to improve for the next patch: ui design, user
experience, user clarity, game guidance, code review, architecture and efficiency, gameplay fun, gameplay alignment,
content increase." And: "Some things are not clear. UI needs improvement. Like scenario selection etc looks very poor.
Bad looking thin text. Everything should be concise and clear there."

## Rules for your findings
1. Every finding carries evidence: `path:line` (clickable), or a command and its output, or "seen on the emulator (file)".
   Mark anything else as a hypothesis.
2. Rank by value to the player or to shipping the next patch, most valuable first. Severity words: blocker, high, medium, low.
3. One line of defect, one line of action, one evidence pointer. No essays. Tables where items are parallel.
4. Nothing may propose moving a LOCKED rule (CLAUDE.md "Non-negotiables"). Where you would want to, write it under a
   heading "Owner decisions" as a question with the trade-off in one line.
5. Content counts are LOCKED; content proposals go where the revision opened room (charters, openings, relics, contract
   modifiers, visitors, laws, preparations, siege traits) or are marked "owner decision".
6. Prior findings: say which of the earlier reviews' items you checked are now fixed, still open, or regressed.
7. Do not edit any source, test, doc or asset outside your own appendix file. Do not run `gradlew --stop`. Do not touch
   the emulator on port 5556 unless your brief gives you a device. Gradle runs are fine (they queue on the daemon).
8. Write your appendix to `docs/review_2026-10-14/<NN>_<lens>.md` (the number and name are in your brief). Start it
   with a 10-line summary, then the ranked table, then details. Keep it under about 400 lines.
9. Your final message to the coordinator: at most 300 words, the top five findings with their evidence pointers and
   the path of your appendix.
