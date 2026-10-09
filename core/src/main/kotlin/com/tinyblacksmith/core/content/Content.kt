package com.tinyblacksmith.core.content

import com.tinyblacksmith.core.model.*

enum class Element { FIRE, FROST, STORM, GRAVE, VERDANT, SUN }
enum class MaterialCategory { CORE, AUGMENT, CATALYST }
enum class AffixKind { BENEFICIAL, FLAW }

data class WeaponFamilyDef(
    val id: WeaponFamilyId,
    val name: String,
    val basePower: Int,
    /** Multiplier applied when a hero of this class wields the family (GDD 4.1 synergy). Missing = offFamilyFit. */
    val classFit: Map<HeroClassId, Double>,
    /** Relative contribution to town defense when used by a champion. */
    val defensiveWeight: Double,
)

data class MaterialDef(
    val id: MaterialId,
    val name: String,
    val category: MaterialCategory,
    val tier: Int,
    val price: Int,
    val element: Element? = null,
    /** null = supplier reliably stocks it every day without limit (GDD 5: basic materials always available). */
    val dailySupplierStock: Int? = null,
    val flavor: String = "",
)

data class AffixDef(
    val id: AffixId,
    val name: String,
    val kind: AffixKind,
    val power: Int,
    val element: Element? = null,
    val description: String,
    /** Multiplier on hero attack power in combat (1.0 = neutral). */
    val attackMultiplier: Double = 1.0,
    /** Multiplier on hero defensive power when defending the town. */
    val defenseMultiplier: Double = 1.0,
    /** Extra multiplier (attack and defense) against one faction, e.g. Undead Bane against the Hollowbound. */
    val baneFaction: FactionId? = null,
    val baneMultiplier: Double = 1.0,
    /** Extra multiplier against elite encounters and warlord-led sieges. */
    val eliteMultiplier: Double = 1.0,
    /** Health the wielder regains after a won expedition. */
    val healOnWin: Int = 0,
    /** Health the weapon takes from its wielder after a won expedition (never lethal). */
    val selfDamageOnWin: Int = 0,
    /** Added to the chance that a won expedition brings a material back. */
    val lootChanceBonus: Double = 0.0,
    /** A material the wielder brings back comes from the scarce pool elites carry (catalysts, tier 3 and up). */
    val scarceLoot: Boolean = false,
    /** Multiplier on the wound a lost expedition deals to the wielder. */
    val damageTakenMultiplier: Double = 1.0,
    /** Chance that the weapon shatters when its wielder loses an expedition. */
    val breakChanceOnLoss: Double = 0.0,
)

data class HeroClassDef(
    val id: HeroClassId,
    val name: String,
    val basePower: Int,
    val powerPerLevel: Int,
    val preferredFamilies: List<WeaponFamilyId>,
    val preferredElement: Element?,
    val startingGoldMin: Int,
    val startingGoldMax: Int,
)

data class TraitDef(
    val id: TraitId,
    val name: String,
    val expeditionWeight: Double = 0.0,
    val patrolWeight: Double = 0.0,
    val restWeight: Double = 0.0,
    val shopWeight: Double = 0.0,
    /** Added to the weight of a day at the guild hall (HeroActivity.GUILD). */
    val guildWeight: Double = 0.0,
    /** Scales the price penalty when evaluating shelf items (>1 = stingier). */
    val priceSensitivity: Double = 1.0,
    /** Added to element taste for any elemental weapon. */
    val noveltyTaste: Double = 0.0,
    val loyaltyGain: Double = 1.0,
    val combatModifier: Double = 1.0,
)

data class FactionDef(
    val id: FactionId,
    val name: String,
    val weakTo: Element?,
    val resists: Element?,
    val dailyGrowth: Int,
    val encounterNames: List<String>,
    val siegeName: String,
    /** Elite expedition encounters (GDD 8 elite variants); empty = this faction fields none. */
    val eliteNames: List<String> = emptyList(),
    /** Leads the siege when the faction's pressure is high (GDD 8 boss variant); null = never. */
    val warlordName: String? = null,
)

/** In-run workshop improvements bought with gold; they reset with the run (GDD 9: shop state does not persist). */
enum class ToolEffect { EXTRA_ENERGY, QUALITY_BONUS, EXTRA_CUSTOMERS, SHELF_SLOTS }

data class ToolDef(
    val id: String,
    val name: String,
    val effect: ToolEffect,
    val magnitudePerLevel: Int,
    val costPerLevel: List<Int>,
    val description: String,
) {
    val maxLevel: Int get() = costPerLevel.size
}

/**
 * DISCOVERY_BONUS = extra journal experiment progress per forge (Journal.recordExperiment); EXCEPTIONAL_CHANCE =
 * percentage points added to the exceptional roll (Forge.apply); HERO_VISIT_CHANCE = percentage points added to hero
 * visit chance (Market.resolveShelfVisits).
 */
enum class BlessingEffect { QUALITY_BONUS, EXTRA_ENERGY, SALE_GOLD_BONUS, INTEGRITY_RECOVERY, HERO_POWER, DISCOVERY_BONUS, EXCEPTIONAL_CHANCE, HERO_VISIT_CHANCE }

data class BlessingDef(
    val id: BlessingId,
    val name: String,
    val effect: BlessingEffect,
    val magnitude: Int,
    val durationDays: Int,
    val description: String,
)

/**
 * MATERIAL_EFFICIENCY = percent chance per level that a forge does not consume its augment (Forge.apply);
 * STARTING_MATERIALS = extra units per level of every material in the starting kit (GameEngine.newRun);
 * EXCEPTIONAL_CHANCE = percentage points per level added to the exceptional roll (Forge.apply).
 * The v5 tracks count levels (magnitudePerLevel = 1) and take their numbers from BalanceConfig:
 * CATALOG_ACCESS = extra daily supplier stock of every limited material (GameEngine.restockedSupplier);
 * RECIPE_ODDS = signature transformation chance (Forge.apply); LEGACY_ARTIFACTS = weight and wholeness of a returning
 * Legend Board blade (WorldEvents).
 */
enum class UpgradeEffect {
    STARTING_ENERGY, STARTING_GOLD, QUALITY_BONUS, STARTING_INTEGRITY, STARTING_REPUTATION, MATERIAL_EFFICIENCY, STARTING_MATERIALS, EXCEPTIONAL_CHANCE,
    CATALOG_ACCESS, RECIPE_ODDS, LEGACY_ARTIFACTS,
}

data class UpgradeDef(
    val id: UpgradeId,
    val name: String,
    val effect: UpgradeEffect,
    val magnitudePerLevel: Int,
    val maxLevel: Int,
    val costPerLevel: List<Int>,
    val description: String,
)

data class ContentCatalog(
    val version: Int,
    val families: List<WeaponFamilyDef>,
    val materials: List<MaterialDef>,
    val affixes: List<AffixDef>,
    val classes: List<HeroClassDef>,
    val traits: List<TraitDef>,
    val factions: List<FactionDef>,
    val blessings: List<BlessingDef>,
    val upgrades: List<UpgradeDef>,
    /** Hidden affinity between a core and an augment (positive = good). */
    val coreAugmentAffinity: Map<Pair<MaterialId, MaterialId>, Int>,
    /** Affinity between an augment and a weapon family. */
    val augmentFamilyAffinity: Map<Pair<MaterialId, WeaponFamilyId>, Int>,
    val firstNames: List<String>,
    val surnames: List<String>,
    val tools: List<ToolDef> = emptyList(),
) {
    val familyById: Map<WeaponFamilyId, WeaponFamilyDef> = families.associateBy { it.id }
    val materialById: Map<MaterialId, MaterialDef> = materials.associateBy { it.id }
    val affixById: Map<AffixId, AffixDef> = affixes.associateBy { it.id }
    val classById: Map<HeroClassId, HeroClassDef> = classes.associateBy { it.id }
    val traitById: Map<TraitId, TraitDef> = traits.associateBy { it.id }
    val factionById: Map<FactionId, FactionDef> = factions.associateBy { it.id }
    val blessingById: Map<BlessingId, BlessingDef> = blessings.associateBy { it.id }
    val upgradeById: Map<UpgradeId, UpgradeDef> = upgrades.associateBy { it.id }

    fun family(id: WeaponFamilyId) = familyById[id] ?: error("Unknown family ${id.value}")
    fun material(id: MaterialId) = materialById[id] ?: error("Unknown material ${id.value}")
    fun affix(id: AffixId) = affixById[id] ?: error("Unknown affix ${id.value}")
    fun heroClass(id: HeroClassId) = classById[id] ?: error("Unknown class ${id.value}")
    fun trait(id: TraitId) = traitById[id] ?: error("Unknown trait ${id.value}")
    fun faction(id: FactionId) = factionById[id] ?: error("Unknown faction ${id.value}")
    fun blessing(id: BlessingId) = blessingById[id] ?: error("Unknown blessing ${id.value}")
    fun upgrade(id: UpgradeId) = upgradeById[id] ?: error("Unknown upgrade ${id.value}")
    fun tool(id: String): ToolDef? = tools.firstOrNull { it.id == id }

    fun materials(category: MaterialCategory) = materials.filter { it.category == category }

    /** Validates every cross reference. Empty list means valid. */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        fun <T> dup(name: String, ids: List<T>) {
            val d = ids.groupBy { it }.filterValues { it.size > 1 }.keys
            if (d.isNotEmpty()) problems += "Duplicate $name ids: $d"
        }
        dup("family", families.map { it.id }); dup("material", materials.map { it.id })
        dup("affix", affixes.map { it.id }); dup("class", classes.map { it.id })
        dup("trait", traits.map { it.id }); dup("faction", factions.map { it.id })
        dup("blessing", blessings.map { it.id }); dup("upgrade", upgrades.map { it.id })
        dup("tool", tools.map { it.id })
        affixes.forEach { a -> a.baneFaction?.let { if (it !in factionById) problems += "Affix ${a.id.value} is the bane of unknown faction ${it.value}" } }
        families.forEach { f -> f.classFit.keys.forEach { c -> if (c !in classById) problems += "Family ${f.id.value} fit references unknown class ${c.value}" } }
        classes.forEach { c -> c.preferredFamilies.forEach { f -> if (f !in familyById) problems += "Class ${c.id.value} prefers unknown family ${f.value}" } }
        coreAugmentAffinity.keys.forEach { (c, a) ->
            if (materialById[c]?.category != MaterialCategory.CORE) problems += "Affinity core ${c.value} is not a core"
            if (materialById[a]?.category != MaterialCategory.AUGMENT) problems += "Affinity augment ${a.value} is not an augment"
        }
        augmentFamilyAffinity.keys.forEach { (a, f) ->
            if (materialById[a]?.category != MaterialCategory.AUGMENT) problems += "Affinity augment ${a.value} is not an augment"
            if (f !in familyById) problems += "Affinity family ${f.value} unknown"
        }
        upgrades.forEach { u -> if (u.costPerLevel.size != u.maxLevel) problems += "Upgrade ${u.id.value} cost list must have maxLevel entries" }
        if (materials(MaterialCategory.CORE).isEmpty()) problems += "No core materials"
        if (materials(MaterialCategory.AUGMENT).isEmpty()) problems += "No augment materials"
        if (families.isEmpty()) problems += "No weapon families"
        if (classes.isEmpty()) problems += "No hero classes"
        if (factions.isEmpty()) problems += "No factions"
        if (firstNames.isEmpty() || surnames.isEmpty()) problems += "Name pools empty" else problems += nameProblems()
        // Only elements an augment can actually imbue need an affix (the slice catalog uses three of the six).
        materials.mapNotNull { it.element }.distinct().forEach { e ->
            if (affixes.none { it.kind == AffixKind.BENEFICIAL && it.element == e }) problems += "No beneficial affix for element $e"
        }
        if (affixes.none { it.kind == AffixKind.FLAW }) problems += "No flaw affixes"
        if (blessings.size < 3) problems += "Need at least 3 blessings to offer a choice"
        return problems
    }

    /**
     * The authoring rules of hero names that a machine can check (plan 4.4, rules 1, 3, 4 and 6, and the endings of 5),
     * on whatever pools the catalog holds. What needs an ear (pronounceable, the four kinds of surname, no weapon titles)
     * is the author's; the launch counts are pinned by its test.
     */
    private fun nameProblems(): List<String> {
        val problems = mutableListOf<String>()
        // 8 + 1 + 11: a full name is at most 20 characters, which fits a Town row and a counter caption at large font.
        fun plain(name: String, length: IntRange) = name.length in length && name.first() in 'A'..'Z' && name.drop(1).all { it in 'a'..'z' }
        fun edits(a: String, b: String): Int {
            var row = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val next = IntArray(b.length + 1)
                next[0] = i
                for (j in 1..b.length) next[j] = minOf(row[j] + 1, next[j - 1] + 1, row[j - 1] + if (a[i - 1].equals(b[j - 1], ignoreCase = true)) 0 else 1)
                row = next
            }
            return row[b.length]
        }
        fun pairs(names: List<String>) = names.indices.flatMap { i -> (i + 1 until names.size).map { j -> names[i] to names[j] } }
        firstNames.filterNot { plain(it, 3..8) }.forEach { problems += "First name $it must be 3-8 plain letters with one capital" }
        surnames.filterNot { plain(it, 4..11) }.forEach { problems += "Surname $it must be 4-11 plain letters with one capital" }
        for ((a, b) in pairs(firstNames)) {
            if (a.take(3) == b.take(3)) problems += "First names $a and $b share their first three letters"
            else if (edits(a, b) < if (a.first() == b.first()) 3 else 2) problems += "First names $a and $b are too alike"
        }
        for ((a, b) in pairs(surnames)) {
            if (a.take(4) == b.take(4)) problems += "Surnames $a and $b share their first four letters"
            else if (edits(a, b) < 2) problems += "Surnames $a and $b are too alike"
        }
        val initials = firstNames.groupBy { it.first() }
        initials.filterValues { it.size > 8 }.forEach { (c, names) -> problems += "${names.size} first names begin with $c (at most 8)" }
        if (initials.size < 15) problems += "First names use ${initials.size} initials (at least 15)"
        for (f in firstNames) surnames.filter { it.startsWith(f.take(4)) }.forEach { problems += "First name $f and surname $it share their first four letters" }
        surnames.groupBy { it.takeLast(3) }.filterValues { it.size > 4 }.forEach { (end, names) -> problems += "${names.size} surnames end in -$end (at most 4)" }
        // Game terms: every word of an ID or a display name of a family, material, affix, class or faction, and the elements.
        // A name may not contain one, nor begin with the first five letters of one (its stem: "Holloway" and the Hollowbound).
        val terms = (families.flatMap { listOf(it.id.value, it.name) } + materials.flatMap { listOf(it.id.value, it.name) } + affixes.flatMap { listOf(it.id.value, it.name) } +
            classes.flatMap { listOf(it.id.value, it.name) } + factions.flatMap { listOf(it.id.value, it.name) } + Element.entries.map { it.name })
            .flatMap { it.lowercase().split(Regex("[^a-z]+")) }.filter { it.length >= 3 }.toSet()
        for (name in firstNames + surnames) {
            val n = name.lowercase()
            terms.firstOrNull { it in n || (it.length >= 5 && n.startsWith(it.take(5))) }?.let { problems += "Name $name carries the game term '$it'" }
        }
        return problems
    }
}
