# Implementation prompt: Tiny Blacksmith visual quality pass

Implement the reconciled visual and UX improvements for Tiny Blacksmith, a premium offline portrait Android fantasy blacksmith/guild game built with Kotlin and Jetpack Compose. Deliver a readable, coherent game UI with recognizable heroes, legible weapons and meaningful presentation of forging, sieges and run endings. Complete the supported work and verify the rendered result.

## Repository and baseline

Repository: `C:\Users\tuncb\Desktop\Blacksmith Project`.

Review baseline: branch `living-heroes`, HEAD `ad32de6ba393cdaf9f77680b21a1cb5d302aa26c`. At review time, remote HEAD, `main` and `living-heroes` all matched that commit. Recheck the current branch, HEAD, working changes and subsequent commits before editing. Preserve unrelated work. The review found no tracked changes; its audit files and build outputs were untracked.

The supplied visual audit describes `e869c7d` (0.9.0, rules 8 / content 7 / balance 17). The next commit, `ad32de6`, introduces layered weapons and redrawn material icons. Historical screenshots are evidence of the earlier build. They do not establish how the latest assets render.

## Documents and precedence

Handoff directory: `C:\Users\tuncb\Documents\Codex\2026-10-17\maj\outputs`. If this handoff has been moved, resolve the same filenames beside this prompt. The accompanying ZIP preserves its relative reference paths.

Read these handoff documents:

1. [Combined review](TinyBlacksmith_combined_visual_review_2026-10-17.md), especially corrections **C1–C9**, additions **R-01–R-03**, the complete VA disposition table and revised priorities. It reconciles the original review for this implementation.
2. [Original visual audit](references/original/audit/VISUAL_UX_AUDIT_2026-10-17.md), §§2–5 and §7, for the original **VA-01–VA-58** observations and linked screenshots. Its recommendations are proposals, qualified by the combined review.
3. [Original audit brief](references/original/BRIEF.md) and [package README](references/original/README.md), for quality goals, evidence provenance and original constraints. Their historical “audit only” role describes the previous task; this prompt requests implementation.
4. [Screen inventory](references/original/agent_data/screen_inventory.md), [token audit](references/original/agent_data/tokens/ui_audit_report.md), and [asset summary](references/original/agent_data/assets/summary.txt). These are historical discovery aids, not proof of current rendering or current totals.

Read the live repository's `CLAUDE.md` and any applicable local rules. Read the relevant current documents in `docs/`: `WRITING_GUIDE.md`, `GDD_REVISION_2026-10-14.md`, `UI_ART_REVAMP.md`, `HERO_FACES_V2_PLAN.md`, `ART_BRIEF.md`, `ART_NEEDED_LIVING_HEROES.md`, `ART_NEEDED_BESTIARY.md`, `LIVING_HEROES_LEDGER.md`, and the relevant sections of `DECISIONS.md` and `PROGRESS.md`. Consult root `Tiny_Blacksmith_GDD.md`, `Tiny_Blacksmith_Living_Heroes_Final_Design.md`, and `Tiny_Blacksmith_Peoples_Names_and_Siege_Bestiary.md` for gameplay/identity boundaries. Copies at the review baseline are in `references/current/`.

Current code establishes what is implemented. Explicit newer owner decisions govern intended behavior. The combined review qualifies the old audit. Neither old review prose nor an outdated descriptive paragraph authorizes gameplay changes. Keep older UI reviews as historical context, especially the deliberate per-destination scroll preservation. Do not revive replaced screens or old navigation.

## Constraints

- Keep gameplay, balance, classic/guild control boundaries, deterministic outcomes, stored combat records, RNG streams and save compatibility intact. UI observes state and submits operations through the existing `GameSession`/ViewModel boundary. A visual pass must not create new engine behavior or save migrations incidentally.
- Preserve approved hero identities, people/class associations, base/upgraded distinctions and the painterly v2 style, including accepted backgrounds. Current coverage is 66 identities / 132 tiles. Do not regenerate, recrop or recolour portraits merely to enforce the audit's density proposals. Preserve intentional old-save silhouettes and stable appearance keys.
- Preserve the new `WeaponLook` model and all intended metal, infusion and catalyst layers. Respect current levels, recipe differences and historical snapshots. Completed weapons must not silently lose layers during layout changes.
- Preserve the warm charcoal/cream/gold/bronze identity and intended Alegreya/Fira typography. Function, hierarchy and readability take priority over ornament. Use approved existing art where it serves the screen. Missing bestiary art is a separate backlog, not authorization to generate a new art set or spend generation credits.
- Follow the Writing Guide for player-facing text. Keep costs, risks, timing, consequences and disabled reasons truthful and available before a decision. Do not shorten text into misleading fragments or remove facts to make a screenshot fit.
- Retain semantic labels, selection state, non-colour cues, readable navigation, useful focus order and minimum touch areas. Do not automatically hide navigation labels, remove inspect access, replace controls with unexplained icons, or rely on colour alone.
- Preserve intentional scroll restoration and selection/confirmation boundaries. Selection is not commitment. Keep End Day accurate to its full resolution of trading, expeditions and any siege.
- Do not commit, push or publish unless separately requested. Follow current repository documentation/changelog requirements for implemented user-visible changes.

## Required outcomes

### A. Responsive usability and action access

Relevant findings: **VA-01–VA-07, VA-15–VA-16, VA-23, VA-25, VA-29, VA-47**; corrections **C2–C4, C8**.

At compact phone widths and larger text, players must be able to read and reach their content, identify destinations/pages, choose recipes, inspect items and confirm actions. Notebook/Records labels must not split inside words, overlap or silently disappear. Stock, Storage, Supplies and defender rows must keep names, facts, quantities and actions intelligible. Layout decisions must account for the component's actual available width and enlarged content.

The Forge commission count/action must remain understandable at 2.0×. All recipe slots and options must be discoverable and reachable; large-text alternatives must retain sufficient identity cues. Partial scrolling tiles must read as deliberate navigation, not accidental cropping. The Practice fight action must fit.

Blessing confirmation must be prominent and reachable after an explicit choice, with deferral available as a secondary path. Preserve the correct initial disabled confirmation when nothing is selected. Avoid required actions stranded beyond unreadable or inaccessible content.

Validate actual interaction before labelling intentional horizontal scrolling or viewport fades as defects. The old 50–80 dp Shop viewport claim is incorrect; do not use it as a metric. End Day's pinned footer applies to Shop, not every destination.

### B. Hero, weapon and icon presentation

Relevant findings: **VA-08–VA-11, VA-13, VA-17–VA-19, VA-24, VA-43–VA-46, VA-50, VA-57**; **C1, C5–C7**, **R-02**.

Heroes should be recognizable between roster, selection, inspect, detail, reports and daily events. Information should make identity, health/condition and equipment easy to assess, with detailed abilities and history still accessible. Selected party members need identity and a clear order. Keep approved portrait content and accessible reorder operations.

Define and verify coherent effective image sizes and framing by context, including density variations. Avoid fractional pixel enlargement artifacts where relevant, accidental blurring, intrusive badges and unintentional clipping. A larger image must not consume the space needed for decisions. The audit's fixed 2×/3×/6× tiers and 128 dp examples are not a binding specification.

Current icon helper, `ui/Sprites.kt:258–260`, already uses `roundToInt()`. `DestinationBar` requests 30 dp, or 24 dp at large text; 32 px art quantizes differently at 420/440/480 dpi. Correct the optical-size problem if demonstrated without implementing a nonexistent floor-to-round fix.

Weapons now use `WeaponLook(base, effect, charm)` and 768 aligned 56×56 layers. All relevant contexts must preserve visible metal, infusion and catalyst identity and correct layer registration. Revalidate the source shapes, glows and current composites before editing the asset pipeline. Edge contact alone is not proof of clipping. Inspect bow/staff cases, high levels, all catalysts, dark/bright elements, and the documented subtle metal differences on bow levels 2–4. The old 175/336 asset count and old weapon resource IDs are obsolete.

Use consistent icon/control language where it improves recognition. Keep text or shape cues for rarity, state and selection. Unused art does not need to be wired merely because it exists.

### C. Current-era siege identity, preparation, replay and report

Relevant findings: **VA-12, VA-41–VA-42, VA-51, VA-55** and **R-01**.

Siege timing and preparation should be immediately recognizable while the workshop retains usable content space. Distinguish siege day visually without suggesting that End Day bypasses ordinary resolutions. Army information must communicate actual enemy identities and useful preparation facts.

Address the current enemy lookup gap: `ui/replay/ReplaySupport.kt:31–46` derives faction pictures only from legacy roster/preparation IDs. `GuildContent.kt:639–643` lists those old IDs, while `CombatContent.kt:485` also includes the new `Bestiary.units`. Units such as `pp_zombie`, `pp_skeleton_guard` and `cap_lich` have no entry in that legacy lookup and can fall through to initials. This is code-inferred and must be rendered/reproduced. Dedicated art for the 48 bestiary units is not shipped; the generator does not currently provide the placeholders claimed by `ART_NEEDED_BESTIARY.md`. Keep correct identity and truthful, coherent fallback behavior using the actual available assets, and document remaining art needs separately.

Replay actors, health, statuses, current/recent events and playback/report actions must remain readable and accessible. Scene treatment must support combat information. Reports should lead with the result, losses and useful recorded contributions. Do not invent an MVP metric or facts that the fight record does not contain. Replaying, skipping, inspecting or leaving must not change outcomes, rewards or gameplay RNG.

### D. Forge reward, notices and shared controls

Relevant findings: **VA-20–VA-22, VA-26–VA-32, VA-58**; **C5, C8**.

The forge result should give the created weapon and its useful stats clear priority, with an obvious primary action and accessible alternatives. Preserve current layered art, pricing, loan eligibility, coaching and all outcomes. Remove redundant presentation only when it conveys no distinct information. Validate transition/scrim timing and reduced motion with live playback, not a single screenshot.

Action feedback must be visible without obscuring a decision or essential content. Equivalent controls should have consistent hierarchy and alignment. Preserve meaningful differences among tabs, radio choices, list actions and primary confirmations. Avoid rigid heights that clip enlarged text. Preserve safe frame interiors and consistent gutters; do not automatically erase padding from every framed surface.

Settings switches already have gold/bronze colours, dropdowns already use the panel surface, and destructive actions already have confirmations. Restyle only where it improves coherence or clarity. Preserve text-field semantics, numeric validation, steppers, keyboard behavior and focus visibility. Consequential confirmation actions need clear effects and hierarchy, without duplicate dialogs or arbitrary rules about what background art a modal covers.

### E. Scanning and daily storytelling

Relevant findings: **VA-14, VA-25, VA-33–VA-40, VA-53–VA-54, VA-57**; **C6–C8**.

Startup, charter and kit selection should feel coherent with the title while keeping tradeoffs visible. Quest/party surfaces should make rewards, fees, risk, departure/return timing and order easy to scan. Supplies/visitor choices should clearly distinguish unavailable options and say why, retaining a concise reason before the player acts.

Shop-day visits, receipts and summaries should emphasize outcomes without repeated numbers or run-on explanations. Counter art must be intentionally composed at compact and large-text sizes. Guidance should help the current decision without excessive repeated chrome; preserve deliberate Done/skip/replay behavior and the player's recorded onboarding progress.

Gazette hierarchy should reflect the importance of actual recorded stories while preserving details and replays. Death and defeat should have distinct emotional weight without recolouring approved faces or inventing narrative facts. Required content must remain reachable when daily cards scroll; a bottom-edge cut with more content below is not itself a permanent clipping defect.

### F. Run endings and finishing work

Relevant findings: **VA-48–VA-50, VA-52–VA-56** as qualified in the combined review.

Distinguish defeat, voluntary ending and successful guild retirement. Keep claim-once behavior, legacy reward, perks, legends and Begin era clear and accurate. Premium presentation must not make a successful retirement look like a failure.

Splash refinement, faction-asset corrections, optional bench visualization and other finishing work follow the high-priority usability/presentation work. Preserve the successful title unless a specific defect justifies change. Confirm actual faction-art truncation against source before changing all assets. Keep the practice fight explicitly a simulation that grants no history or rewards.

## Evidence and acceptance

Maintain a concise finding ledger mapping every **VA-01–VA-58** to implemented, verified unchanged/acceptable, superseded, optional/deferred with reason, or blocked with reason. Track **R-01–R-03** and the review's corrections. Do not imply every original recommendation is approved or every historical observation remains current. Choose the implementation architecture from the current code; the required outcomes above govern acceptance.

Build and run the changed app. Capture comparable before/after states and inspect the images at phone size as well as full resolution where sharpness matters. Record app commit/build, logical size, density, font scale, navigation mode, state/scenario and whether it was natural, constructed or preview.

| Coverage | Required evidence |
|---|---|
| Sizes | 360×800, approximately 393×852 and 412×915 dp at 1.0×; compact width at 1.3×, 1.5× and 2.0× |
| Density | 420, 440 and 480 dpi for representative portrait/icon/weapon surfaces; distinguish density from logical screen width |
| Core surfaces | Shop, Forge/choices/reveal, Records/Notebook, Storage, Supplies, hero inspect/detail, party/Wall, visit/blessing cards, replay/report, run end |
| Interaction | Scroll reachability, long content/names, target/focus bounds, TalkBack labels/selection/reading order, keyboard-open pricing, gestures and representative three-button navigation |
| Motion | Ordinary and reduced-motion forging, notices, daily cards and replay; no essential information depends on animation |
| New and legacy eras | A natural new living-era guild journey through a new bestiary siege, plus legacy scenarios/replays; `guild_siege_eve`, `guild_stormwell` and `guild_charter_eve` are livingRevision 0 |
| Gaps | Loading/recovery/save errors, End Day preview, advanced Forge/Heartfire, loans/relic replacement, resume/quiet/tally/contract cards, replay inspector, personal offers, return/departure warnings, death/defeat/retirement, Claim and Begin era |

Use current repository checks appropriate to the changes: app unit tests, debug build, and targeted device tests for changed user flows; core tests when models/content/replay integration are touched. The normal task names are `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:connectedDebugAndroidTest` and `:core:test`. Test existing behavior meaningfully; avoid tests that only repeat implementation details. Existing relevant files include `WeaponArtTest`, `ForgeResultCardTest`, `DetailSheetTest`, `ShopPanelTest`, `ShopDayScreenTest`, `ShopDayPersistenceTest`, `IntroStripTest` and the Forge/Guild UI model tests.

Do not carry the old audit's ShopPanel failure forward as a current fact. Reestablish the baseline. Existing `tools/emulator/runend.sh` still searches for “Begin a classic run”; update stale automation if used. Preserve stable test tags where practical and explain necessary changes. Record failures, environment limitations and untested states separately from passing results.

Update the live repository's required progress/ledger and `[Unreleased]` changelog entries for implemented work. Completion means the supported findings are addressed with current rendered evidence, or explicitly accounted for with concrete limitations. Return the changed behavior, finding dispositions, validation results, evidence locations and any remaining art/owner decisions. Do not claim complete visual verification from code inspection alone.
