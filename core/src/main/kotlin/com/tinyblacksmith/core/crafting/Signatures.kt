package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.content.LaunchContent
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

    /** Every condition the attempt misses, in ladder order; empty when the recipe is exact. */
    fun misses(cmd: Command.Forge, quality: Int): List<Miss> = listOfNotNull(
        Miss.CATALYST.takeIf { catalystId != null && cmd.catalystId != catalystId },
        Miss.RISK.takeIf { risk != null && cmd.risk != risk },
        Miss.QUALITY.takeIf { quality < minQuality },
    )

    /** The first condition the attempt misses, or null when the recipe is exact. */
    fun missing(cmd: Command.Forge, quality: Int): Miss? = misses(cmd, quality).firstOrNull()

    enum class Miss(val rung: ClueRung) { CATALYST(ClueRung.CATALYST), RISK(ClueRung.TEMPER), QUALITY(ClueRung.QUALITY) }
}

/**
 * The clue ladder of one signature (plan 4.7, G07): the base recipe ("hides something more"), what kind of catalyst it
 * wants, the temper, how fine the work must be. Stored as bits of `Journal.signatureClues`; never an odd at any rung.
 */
enum class ClueRung {
    RECIPE, CATALYST, TEMPER, QUALITY;

    val bit: Int get() = 1 shl ordinal

    companion object {
        val ALL: Int = entries.sumOf { it.bit }
    }
}

/**
 * Signature table (GDD 4.5 PROPOSED): 24 recipes, 4 per launch family, keyed by [LaunchContent] IDs. The slice
 * families share every ID with the launch catalog; spear, dagger and staff recipes are simply unreachable there
 * because Forge validation rejects unknown families. Every element, core and catalyst appears at least once.
 */
object SignatureCatalog {
    private val SALT = LaunchContent.BINDING_SALT
    private val RUNE = LaunchContent.RUNESTONE_SHARD
    private val OIL = LaunchContent.DRAGON_OIL
    private val INK = LaunchContent.VOID_INK

    val all: List<SignatureDef> = listOf(
        // Swords
        SignatureDef("dawnbrand", "Dawnbrand", LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, SALT, null, 50,
            listOf(LaunchContent.FLAMING, LaunchContent.KEEN), 4, "A soldier's blade that keeps the morning's heat."),
        SignatureDef("winterwake", "Winterwake", LaunchContent.SWORD, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, null, Risk.SAFE, 60,
            listOf(LaunchContent.FROSTBOUND, LaunchContent.GUARDIANS), 5, "Cooled with patience; it remembers the first frost."),
        SignatureDef("tempest_edge", "Tempest Edge", LaunchContent.SWORD, LaunchContent.SILVER, LaunchContent.STORMGLASS, SALT, Risk.RECKLESS, 70,
            listOf(LaunchContent.STORMCHARGED, LaunchContent.KEEN), 8, "Only a reckless hand can bind a storm in silver."),
        SignatureDef("ashen_vow", "Ashen Vow", LaunchContent.SWORD, LaunchContent.SILVER, LaunchContent.EMBER_RESIN, SALT, null, 55,
            listOf(LaunchContent.FLAMING, LaunchContent.REINFORCED), 6, "The silver fought the resin until the salt made them agree."),
        // Axes
        SignatureDef("hearthcleaver", "Hearthcleaver", LaunchContent.AXE, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.RECKLESS, 55,
            listOf(LaunchContent.FLAMING, LaunchContent.REINFORCED), 5, "Struck hot and fast; it splits shields like kindling."),
        SignatureDef("glacier_maul", "Glacier Maul", LaunchContent.AXE, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, SALT, null, 60,
            listOf(LaunchContent.FROSTBOUND, LaunchContent.REINFORCED), 6, "Heavy as packed ice and twice as patient."),
        SignatureDef("thunderhead", "Thunderhead", LaunchContent.AXE, LaunchContent.SILVER, LaunchContent.STORMGLASS, SALT, Risk.RECKLESS, 70,
            listOf(LaunchContent.STORMCHARGED, LaunchContent.GUARDIANS), 8, "The storm did not want an axe. It got one anyway."),
        SignatureDef("emberfall", "Emberfall", LaunchContent.AXE, LaunchContent.BRONZE, LaunchContent.EMBER_RESIN, SALT, Risk.SAFE, 50,
            listOf(LaunchContent.FLAMING, LaunchContent.GUARDIANS), 4, "Bronze warmed slowly until the resin settled into the grain."),
        // Bows
        SignatureDef("stormsong", "Stormsong", LaunchContent.BOW, LaunchContent.SILVER, LaunchContent.STORMGLASS, SALT, null, 65,
            listOf(LaunchContent.STORMCHARGED, LaunchContent.KEEN), 8, "The string hums before the arrow leaves it."),
        SignatureDef("frostwhisper", "Frostwhisper", LaunchContent.BOW, LaunchContent.IRON, LaunchContent.FROST_BLOOM, SALT, Risk.SAFE, 50,
            listOf(LaunchContent.FROSTBOUND, LaunchContent.KEEN), 4, "Arrows from it land without a sound."),
        SignatureDef("cinder_arc", "Cinder Arc", LaunchContent.BOW, LaunchContent.BRONZE, LaunchContent.EMBER_RESIN, null, Risk.RECKLESS, 55,
            listOf(LaunchContent.FLAMING, LaunchContent.KEEN), 5, "Bent in the fire and never quite cooled."),
        SignatureDef("galewood", "Galewood", LaunchContent.BOW, LaunchContent.BRONZE, LaunchContent.STORMGLASS, SALT, null, 60,
            listOf(LaunchContent.STORMCHARGED, LaunchContent.REINFORCED), 6, "Salt-bound stormglass set in warm bronze; it never warps."),
        // Spears
        SignatureDef("thornwall", "Thornwall", LaunchContent.SPEAR, LaunchContent.IRON, LaunchContent.VERDANT_SAP, SALT, null, 50,
            listOf(LaunchContent.RESONANT, LaunchContent.GUARDIANS), 4, "Green sap set in honest iron; the line it holds does not bend."),
        SignatureDef("hoarfrost_pike", "Hoarfrost Pike", LaunchContent.SPEAR, LaunchContent.MOONSTEEL, LaunchContent.FROST_BLOOM, null, Risk.SAFE, 70,
            listOf(LaunchContent.FROSTBOUND, LaunchContent.REINFORCED), 8, "Cooled slowly in moon-pale metal; the cold goes all the way through."),
        SignatureDef("sunlance", "Sunlance", LaunchContent.SPEAR, LaunchContent.STARSTEEL, LaunchContent.SUN_ASH, OIL, Risk.RECKLESS, 75,
            listOf(LaunchContent.UNDEAD_BANE, LaunchContent.KEEN), 9, "Noon folded into a fallen star; the dead will not stand before it."),
        SignatureDef("gravewarden", "Gravewarden", LaunchContent.SPEAR, LaunchContent.BRONZE, LaunchContent.GRAVE_DUST, RUNE, null, 60,
            listOf(LaunchContent.VAMPIRIC, LaunchContent.GUARDIANS), 6, "The dust wanted to leave the bronze; the rune held it there."),
        // Daggers
        SignatureDef("nightletter", "Nightletter", LaunchContent.DAGGER, LaunchContent.BRONZE, LaunchContent.GRAVE_DUST, INK, null, 60,
            listOf(LaunchContent.VAMPIRIC, LaunchContent.SWIFT), 6, "Written in ink that drinks the light; it cuts what it names."),
        SignatureDef("sparkfang", "Sparkfang", LaunchContent.DAGGER, LaunchContent.SILVER, LaunchContent.STORMGLASS, null, Risk.RECKLESS, 60,
            listOf(LaunchContent.STORMCHARGED, LaunchContent.SWIFT), 6, "Too quick to hold still; it bites before the thunder."),
        SignatureDef("emberneedle", "Emberneedle", LaunchContent.DAGGER, LaunchContent.OBSIDIAN, LaunchContent.EMBER_RESIN, SALT, null, 55,
            listOf(LaunchContent.FLAMING, LaunchContent.KEEN), 5, "Volcanic glass remembers the fire; the salt keeps it from going home."),
        SignatureDef("rimeshard", "Rimeshard", LaunchContent.DAGGER, LaunchContent.IRON, LaunchContent.FROST_BLOOM, RUNE, Risk.SAFE, 50,
            listOf(LaunchContent.FROSTBOUND, LaunchContent.LUCKY), 4, "A plain iron knife that the rune taught to be patient and cold."),
        // Staves
        SignatureDef("noonward", "Noonward", LaunchContent.STAFF, LaunchContent.STARSTEEL, LaunchContent.SUN_ASH, INK, null, 75,
            listOf(LaunchContent.UNDEAD_BANE, LaunchContent.GUARDIANS), 9, "A staff for the long night; it carries noon with it."),
        SignatureDef("greenheart", "Greenheart", LaunchContent.STAFF, LaunchContent.BRONZE, LaunchContent.VERDANT_SAP, null, Risk.BALANCED, 55,
            listOf(LaunchContent.RESONANT, LaunchContent.REINFORCED), 5, "Warm bronze and living sap, worked neither hot nor cold; it grows into the hand."),
        SignatureDef("stormcaller", "Stormcaller", LaunchContent.STAFF, LaunchContent.SILVER, LaunchContent.STORMGLASS, RUNE, null, 65,
            listOf(LaunchContent.STORMCHARGED, LaunchContent.LUCKY), 7, "The rune gives the storm a name, and the storm answers."),
        SignatureDef("mourning_rod", "Mourning Rod", LaunchContent.STAFF, LaunchContent.OBSIDIAN, LaunchContent.GRAVE_DUST, OIL, Risk.SAFE, 65,
            listOf(LaunchContent.VAMPIRIC, LaunchContent.GIANT_SLAYER), 7, "Burned slow in dragon oil until the dust stopped whispering."),
    )

    val byId: Map<String, SignatureDef> = all.associateBy { it.id }
    private val byRecipe: Map<Triple<WeaponFamilyId, MaterialId, MaterialId>, SignatureDef> = all.associateBy { Triple(it.familyId, it.coreId, it.augmentId) }

    /** The signature whose base recipe (family + core + augment) this attempt uses, if any. */
    fun forRecipe(cmd: Command.Forge): SignatureDef? = byRecipe[Triple(cmd.familyId, cmd.coreId, cmd.augmentId)]

    /** The forge a known signature asks for, for "Use this recipe": Advanced when it needs a catalyst, its temper (Balanced when it has none). Quality is the smith's to reach. */
    fun recipe(def: SignatureDef): Command.Forge =
        Command.Forge(if (def.catalystId != null) com.tinyblacksmith.core.model.ForgeMode.ADVANCED else com.tinyblacksmith.core.model.ForgeMode.QUICK, def.familyId, def.coreId, def.augmentId, def.catalystId, def.risk ?: Risk.BALANCED)

    /** The signature this attempt fully qualifies for (exact recipe, conditions met, quality floor reached). */
    fun eligible(cmd: Command.Forge, quality: Int): SignatureDef? = forRecipe(cmd)?.takeIf { it.missing(cmd, quality) == null }
}
