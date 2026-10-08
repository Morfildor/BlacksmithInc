package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.AffixId
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.WeaponFamilyId

/**
 * Hidden signature recipe (GDD 4.5). Data only: qualifying family + core + augment, optional catalyst and risk
 * conditions, a quality floor, and what the transformation grants. Names are PROPOSED.
 * `catalystId == null` and `risk == null` mean "no condition".
 */
data class SignatureDef(
    val id: String,
    val name: String,
    val familyId: WeaponFamilyId,
    val coreId: MaterialId,
    val augmentId: MaterialId,
    val catalystId: MaterialId?,
    val risk: Risk?,
    val minQuality: Int,
    val grantedAffixes: List<AffixId>,
    val bonusPower: Int,
    val flavor: String,
) {
    val journalKey: String get() = "sig:$id"

    fun matchesBase(cmd: Command.Forge): Boolean =
        cmd.familyId == familyId && cmd.coreId == coreId && cmd.augmentId == augmentId

    /** Which condition the attempt misses, or null when the recipe is exact. */
    fun missing(cmd: Command.Forge, quality: Int): Miss? = when {
        catalystId != null && cmd.catalystId != catalystId -> Miss.CATALYST
        risk != null && cmd.risk != risk -> Miss.RISK
        quality < minQuality -> Miss.QUALITY
        else -> null
    }

    enum class Miss { CATALYST, RISK, QUALITY }
}

/** Slice signature table: 4 per family (GDD 4.5 PROPOSED 24 at launch). Moves into ContentCatalog in P6. */
object SignatureCatalog {
    private val SALT = SliceContent.BINDING_SALT

    val all: List<SignatureDef> = listOf(
        // Swords
        SignatureDef("dawnbrand", "Dawnbrand", SliceContent.SWORD, SliceContent.IRON, SliceContent.EMBER_RESIN, SALT, null, 50,
            listOf(SliceContent.FLAMING, SliceContent.KEEN), 4, "A soldier's blade that keeps the morning's heat."),
        SignatureDef("winterwake", "Winterwake", SliceContent.SWORD, SliceContent.BRONZE, SliceContent.FROST_BLOOM, null, Risk.SAFE, 60,
            listOf(SliceContent.FROSTBOUND, SliceContent.GUARDIANS), 5, "Cooled with patience; it remembers the first frost."),
        SignatureDef("tempest_edge", "Tempest Edge", SliceContent.SWORD, SliceContent.SILVER, SliceContent.STORMGLASS, SALT, Risk.RECKLESS, 70,
            listOf(SliceContent.STORMCHARGED, SliceContent.KEEN), 8, "Only a reckless hand can bind a storm in silver."),
        SignatureDef("ashen_vow", "Ashen Vow", SliceContent.SWORD, SliceContent.SILVER, SliceContent.EMBER_RESIN, SALT, null, 55,
            listOf(SliceContent.FLAMING, SliceContent.REINFORCED), 6, "The silver fought the resin until the salt made them agree."),
        // Axes
        SignatureDef("hearthcleaver", "Hearthcleaver", SliceContent.AXE, SliceContent.IRON, SliceContent.EMBER_RESIN, null, Risk.RECKLESS, 55,
            listOf(SliceContent.FLAMING, SliceContent.REINFORCED), 5, "Struck hot and fast; it splits shields like kindling."),
        SignatureDef("glacier_maul", "Glacier Maul", SliceContent.AXE, SliceContent.BRONZE, SliceContent.FROST_BLOOM, SALT, null, 60,
            listOf(SliceContent.FROSTBOUND, SliceContent.REINFORCED), 6, "Heavy as packed ice and twice as patient."),
        SignatureDef("thunderhead", "Thunderhead", SliceContent.AXE, SliceContent.SILVER, SliceContent.STORMGLASS, SALT, Risk.RECKLESS, 70,
            listOf(SliceContent.STORMCHARGED, SliceContent.GUARDIANS), 8, "The storm did not want an axe. It got one anyway."),
        SignatureDef("emberfall", "Emberfall", SliceContent.AXE, SliceContent.BRONZE, SliceContent.EMBER_RESIN, SALT, Risk.SAFE, 50,
            listOf(SliceContent.FLAMING, SliceContent.GUARDIANS), 4, "Bronze warmed slowly until the resin settled into the grain."),
        // Bows
        SignatureDef("stormsong", "Stormsong", SliceContent.BOW, SliceContent.SILVER, SliceContent.STORMGLASS, SALT, null, 65,
            listOf(SliceContent.STORMCHARGED, SliceContent.KEEN), 8, "The string hums before the arrow leaves it."),
        SignatureDef("frostwhisper", "Frostwhisper", SliceContent.BOW, SliceContent.IRON, SliceContent.FROST_BLOOM, SALT, Risk.SAFE, 50,
            listOf(SliceContent.FROSTBOUND, SliceContent.KEEN), 4, "Arrows from it land without a sound."),
        SignatureDef("cinder_arc", "Cinder Arc", SliceContent.BOW, SliceContent.BRONZE, SliceContent.EMBER_RESIN, null, Risk.RECKLESS, 55,
            listOf(SliceContent.FLAMING, SliceContent.KEEN), 5, "Bent in the fire and never quite cooled."),
        SignatureDef("galewood", "Galewood", SliceContent.BOW, SliceContent.BRONZE, SliceContent.STORMGLASS, SALT, null, 60,
            listOf(SliceContent.STORMCHARGED, SliceContent.REINFORCED), 6, "Salt-bound stormglass set in warm bronze; it never warps."),
    )

    val byId: Map<String, SignatureDef> = all.associateBy { it.id }

    /** The signature whose base recipe (family + core + augment) this attempt uses, if any. */
    fun forRecipe(cmd: Command.Forge): SignatureDef? = all.firstOrNull { it.matchesBase(cmd) }

    /** The signature this attempt fully qualifies for (exact recipe, conditions met, quality floor reached). */
    fun eligible(cmd: Command.Forge, quality: Int): SignatureDef? = forRecipe(cmd)?.takeIf { it.missing(cmd, quality) == null }
}
