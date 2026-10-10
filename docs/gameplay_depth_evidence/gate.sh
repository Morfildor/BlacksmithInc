#!/usr/bin/env bash
# The balance-10 gate: 10,000 seeds from base seed 1, with the new systems and without them (--noDepth) on the same seeds.
cd "$(dirname "$0")/../.." || exit 1
G="${1:-core/build/gate}"
mkdir -p "$G"
UP="catalog_access=3,forge_mastery=3,legacy_artifacts=3,lucky_hammer=3,material_efficiency=3,recipe_odds=3,shop_reputation=3,stalwart_walls=3,starting_energy=3,starting_gold=3,starting_stock=3"
FIVE="EXPERT,EXPERT_ACTIVE,BALANCED_FAIR,BALANCED_ACTIVE,SYNERGY"
run() { name="$1"; shift; echo "== $name $(date +%H:%M:%S)"; ./gradlew :core:simulate --console=plain -q --args="$* --json $G/$name.json" > "$G/$name.txt" 2>&1; echo "   exit $?"; }
run classic_depth   --runs 10000 --seed 1 --policy all --customers --yardsticks --noImpact --depth
run classic_nodepth --runs 10000 --seed 1 --policy all --customers --yardsticks --noImpact --noDepth
run bots_depth      --runs 10000 --seed 1 --policy bots --customers --yardsticks --noImpact --depth
run bots_nodepth    --runs 10000 --seed 1 --policy bots --customers --yardsticks --noImpact --noDepth
run maxed_depth     --runs 10000 --seed 1 --policy $FIVE --customers --yardsticks --noImpact --depth --upgrades $UP
run maxed_nodepth   --runs 10000 --seed 1 --policy $FIVE --customers --yardsticks --noImpact --noDepth --upgrades $UP
for e in decline first cash defense adaptive; do run expert_enc_$e --runs 10000 --seed 1 --policy EXPERT,BALANCED_ACTIVE --noImpact --depth --encounters $e; done
for r in none first adaptive salvagers_crucible tempering_ledger collectors_seal ashen_bellows; do run expert_relic_$r --runs 10000 --seed 1 --policy EXPERT,BALANCED_ACTIVE --noImpact --depth --relic $r; done
echo "== probe $(date +%H:%M:%S)"; ./gradlew :core:simulate --console=plain -q --args="--probe 1000 --seed 1" > "$G/probe.txt" 2>&1; echo "   exit $?"
echo GATE_DONE
