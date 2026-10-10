# Major update: planning evidence (2026-10-09)

Supporting material for `docs/MAJOR_UPDATE_PLAN.md`. The plan is the authority; these files are what it was built
from and are not kept current.

| File | What it is |
|---|---|
| `01_core_persistence.md` | review of commands, saves, accounting, versions and long-save growth; `path:line` at `77919fe` |
| `02_economy_customers.md` | customer pipeline, population, names, loops and simulator review; `path:line` at `8cc133b` |
| `03_android_experience.md` | ViewModel, screens, lifecycle, accessibility and the shop-day state machine; app lines at `4ecbac0` |
| `04_assets_content.md` | every art source inspected, portrait and scene findings, import plan |
| `05_adversarial_review.md` | the independent review of the plan (2 blockers, 12 major, 22 minor); every finding is dispositioned in plan section 11 |
| `sheet3_proposed_cells.json`, `v2ref_portrait_cells.json`, `atlas_boxes.json` | measured crop boxes used by task T2.4 and T3.3 (source pixels) |
| `crops/` | eight contact sheets and mocks. The counter-scene images are Pillow composites, not app screenshots |
| `measure/` | the scratch harness behind the customer baseline of plan section 2.3 |

About `measure/`: `Measure.kt` was compiled outside the project against a frozen jar of the core (not committed) with
the Kotlin compiler from the local Gradle cache; `build_and_run.sh` holds machine-specific paths and will not run
elsewhere as it is. It is planning evidence only. Task T0.4 moves these metrics into the simulator, after which this
folder's numbers are superseded by `./gradlew :core:simulate --args="... --customers"`.

Sources did not change between `0d5ff65` (where the code was checked) and release v0.6.0 (`0ad888a`).
