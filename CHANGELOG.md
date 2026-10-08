# Changelog

All notable changes to Tiny Blacksmith are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [SemVer](https://semver.org/)
with `0.y.z` during early development (minor = new feature set, patch = fixes/tuning). The app version lives in
`app/build.gradle.kts` (`versionName`, `versionCode` increments on every store-facing build).

## [Unreleased]

### Changed
- Launch content is the engine default (`GameEngine()` now builds on `LaunchContent`; `SliceContent` stays for
  slice-specific tests and `--content slice` in the simulator).
- Balance v2: quality formula `25 + 6*coreTier + 3*augmentTier + ...` (was `35 + 4*coreTier`), so the six launch core
  tiers spread common -> epic instead of saturating; siege modifier 2.0 (was 2.75) with forge damage
  `24 + 50*(ratio-1)` (was `12 + 25*(ratio-1)`); launch faction growth 4/3/2 per day (was 6/5/4). Heroes now act
  against the most pressing faction instead of the first by ID.
- Simulator: `--content launch|slice`, `--rarityTable [N]` (rarity per core x augment x risk through the real forge
  path), `--impactPolicy`, forge-damage overrides; the RANDOM policy only picks materials it can afford.
- Weapons are drawn from the artist's weapon master sheet: every family has an element row and eight visual levels
  (core tier plus epic/legendary bonus); the forge preview shows the plain base weapon until an augment is chosen.
  The old per-core recolours and element auras are gone.

## [0.1.0] - 2026-10-08
First tracked build (vertical slice content, launch content data-complete but not default).

### Added
- `:core` deterministic engine: SplitMix64 per-subsystem RNG, immutable `GameState`, typed commands and errors,
  fixed End Day order, invariants, versioned `BalanceConfig` (v1), versioned save codec with migration scaffold.
- Content: `SliceContent` (default) and `LaunchContent` (6 families, 16 materials, 5 classes, 3 factions,
  12 affixes, 6 flaws, 8 blessings, 8 upgrades), 12 signature recipes, techniques (temper, quench, etch),
  23 world events, retirements, guilds, mentoring, seizure and inheritance.
- Headless simulator with the GDD policy set, upgrade-impact sweep, perf timing and JSON reports.
- `:app` Compose UI: title, workshop (forge, market, town, gazette, journal, legacy), day report with stepped
  battle replay and animated siege diorama, run-end screen, settings (reduced motion), Room save with atomic writes.
- Pixel art pipeline: concept sheets, loose PNGs and the artist's 1x pack merged by `import_assets.py`;
  placeholders by `generate_assets.py`; 194 hand-made sprites in use.
- Tests: core JVM suite, instrumented save/UI tests, scripted emulator smoke loop.
