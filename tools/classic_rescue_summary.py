"""Summarize the opt-in ClassicRescueMeasurementTest CSVs; stdlib only."""
import csv
import hashlib
import json
import math
import statistics as stats
from collections import Counter
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / "core/build/classic-rescue"


def read(name):
    with (source / name).open(encoding="utf8", newline="") as handle:
        return list(csv.DictReader(handle))


def means(rows, keys):
    return {key: round(stats.mean(float(row[key]) for row in rows), 3) for key in keys} if rows else {}


runs, days, sieges, snapshots, selections = map(read, ["runs.csv", "days.csv", "sieges.csv", "snapshots.csv", "selections.csv"])
seeds = sorted({int(row["seed"]) for row in runs})
summary = {
    "seedRange": [min(seeds), max(seeds)], "horizon": 80, "runs": len(runs),
    "sourceSha256": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(source.glob("*.csv"))},
    "policies": [], "paired": [], "snapshots": [],
}
for variant, policy in dict.fromkeys((row["variant"], row["policy"]) for row in runs):
    selected = [row for row in runs if (row["variant"], row["policy"]) == (variant, policy)]
    result = {
        "variant": variant, "policy": policy, "n": len(selected),
        "medianDays": stats.median(int(row["days"]) for row in selected),
        "means": means(selected, ["days", "materialSpend", "materialUnits", "forged", "sold", "recovered", "damage", "heroDeaths"]),
        "censored": sum(row["ended"] == "false" for row in selected), "sieges": [],
    }
    for number in range(1, 5):
        reached = [row for row in sieges if (row["variant"], row["policy"], int(row["siege"])) == (variant, policy, number)]
        daily = [row for row in days if (row["variant"], row["policy"], int(row["day"])) == (variant, policy, number * 5)]
        result["sieges"].append({
            "siege": number, "reached": len(reached), "held": sum(row["held"] == "true" for row in reached),
            "aliveAfter": sum(int(row["integrity"]) > 0 for row in daily),
            "means": means(reached, ["defense", "raid", "champions", "militia", "watch", "damage", "healthAfterRecovery"]),
            "materialSpend": means(daily, ["materialSpend"]).get("materialSpend"),
        })
    summary["policies"].append(result)
for policy in ["A_FIRE", "A_FROST", "B_INVEST", "SIEGE_PREP"]:
    for number in range(1, 5):
        def holds(name):
            return {int(row["seed"]): int(row["held"] == "true") for row in sieges if (row["variant"], row["policy"], int(row["siege"])) == ("default", name, number)}
        other, expert = holds(policy), holds("C_EXPERT")
        differences = [expert.get(seed, 0) - other.get(seed, 0) for seed in seeds]
        delta = stats.mean(differences)
        se = stats.stdev(differences) / math.sqrt(len(differences)) if len(differences) > 1 else 0
        summary["paired"].append({
            "comparison": "C_EXPERT vs " + policy, "siege": number,
            "COnly": differences.count(1), "otherOnly": differences.count(-1),
            "deltaPercentagePoints": round(delta * 100, 3), "pairedSE": round(se * 100, 3),
            "approx95Interval": [round(100 * (delta - 1.96 * se), 3), round(100 * (delta + 1.96 * se), 3)],
        })
for policy in ["A_FIRE", "A_FROST", "B_INVEST", "C_EXPERT"]:
    for number in range(1, 5):
        selected = [row for row in snapshots if row["policy"] == policy and int(row["siege"]) == number]
        if not selected:
            continue
        summary["snapshots"].append({
            "policy": policy, "siege": number, "n": len(selected),
            "meanDefenseRemoved": {key: round(stats.mean(float(row["defense"]) - float(row[key]) for row in selected), 3) for key in ["noWeapons", "noQualityPower", "noElements", "noFamilyFitOrWeight", "noTraitDefense"]},
            "meanRaidRemovedWithoutTrait": round(stats.mean(float(row["raid"]) - float(row["noTraitRaid"]) for row in selected), 3),
        })
first = [row for row in selections if row["day"] == "1"]
summary["startingSelection"] = {
    "worlds": dict(Counter(row["world"] for row in first)),
    "uniqueStartingKits": len({row["materialInventory"] for row in first}),
    "uniqueClassCompositions": len({row["classes"] for row in first}),
    "allClassesEveryRun": all(all(name + ":" in row["classes"] for name in ["guardian", "ranger", "duelist", "battlemage", "warden"]) for row in first),
    "startingEquipped": dict(Counter(row["equippedFamilies"] for row in first)),
    "visitorsFirstTenDays": dict(Counter(row["visitor"] for row in selections)),
}
target = root / "docs/codex-review/CLASSIC-GAMEPLAY-RESCUE-MEASUREMENTS.json"
target.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf8")
print(f"Wrote {target.name}: {len(runs)} runs, seeds {min(seeds)}..{max(seeds)}")
