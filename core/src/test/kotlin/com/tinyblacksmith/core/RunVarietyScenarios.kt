package com.tinyblacksmith.core

import com.tinyblacksmith.core.ScenarioSaves.Scenario
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.crafting.Legends
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.sim.GuildPolicy

/**
 * Scenario saves for what rules 6 added to the screens (docs/RUN_VARIETY_LEDGER.md, gate G14): a Heartfire forging that
 * breaks through and one that does not, a siege night with its preparation standing, the supplier's two moods, and
 * the legend lines of a weapon. All under Town's Last Hope. Each says in [Scenario.built] what was set by hand.
 */
object RunVarietyScenarios {
    private val content = engine.content
    private val config = engine.config
    private val legend = config.depth.legend

    /** The forging the two Heartfire saves are made for, as the workbench sends it: Advanced, sword, Starsteel, Sun Ash, balanced, Heartfire on. */
    val FORGING = Command.Forge(ForgeMode.ADVANCED, LaunchContent.SWORD, LaunchContent.STARSTEEL, LaunchContent.SUN_ASH, null, Risk.BALANCED, heartfire = true)

    private fun GameState.edit(f: (ResolutionContext) -> Unit): GameState = ResolutionContext(this, content, config).also(f).toState()

    /** Day 1 of a guild run with two Heartfire, the metal and the gold for the forging. Nothing else is touched: the crafting stream is the seed's own. */
    fun atTheAnvil(seed: Long): GameState = GuildScenarios.fresh(seed).let { s ->
        s.copy(heartfire = 2, gold = 400, materials = s.materials + mapOf(LaunchContent.STARSTEEL to 2, LaunchContent.SUN_ASH to 2))
    }

    /** What [FORGING] makes on [atTheAnvil] of [seed]. */
    fun forged(seed: Long): Weapon = atTheAnvil(seed).let { s -> (engine.handle(s, FORGING) as CommandOutcome.Accepted).state.weapons.values.single { it.id !in s.weapons } }

    /** The first seeds on which that forging breaks through, and on which it reaches the Epic floor and still does not. */
    val BREAKTHROUGH_SEED: Long by lazy { (1L..400L).first { forged(it).legend == LegendOrigin.HEARTFIRE } }
    val MISS_SEED: Long by lazy { (1L..400L).first { forged(it).let { w -> w.legend == null && w.quality >= config.epicMin } } }

    const val SIEGE_SEED = 1L

    /** The morning of the first siege as the supply-only smith reached it: nobody spoiled what the besieger prepared. */
    private fun unspoiledSiege(): GameState = GuildScenarios.morning(SIEGE_SEED, GuildPolicy.NOVICE, config.siegeInterval).let { s -> s.pendingBlessingOffer.firstOrNull()?.let { s.run(Command.ChooseBlessing(it)) } ?: s }

    const val SUPPLIER_SEED = 3L

    /** Day 1 with the supplier's answer set as the guild's own rule sets it: the cart brought through, or lost. */
    private fun supplier(grateful: Boolean): GameState = GuildScenarios.fresh(SUPPLIER_SEED).edit { Missions.supplier(it, grateful, if (grateful) "The supplier got through." else "The supplier's cart was lost.") }.copy(gold = 400)

    /** Three weapons in storage: Legendary by Heartfire (the forging above, by a real command), Legendary by deeds, and one deed short of it. */
    private fun legends(): GameState {
        var s = atTheAnvil(BREAKTHROUGH_SEED).run(FORGING)
        s = s.copy(energy = config.baseDailyEnergy, materials = s.materials + mapOf(LaunchContent.IRON to 4, LaunchContent.EMBER_RESIN to 4))
        val a = s.run(TestSupport.quickSword(Risk.SAFE)).also { s = it }.weapons.keys.last()
        val b = s.run(TestSupport.quickSword(Risk.SAFE)).also { s = it }.weapons.keys.last()
        return s.edit { c ->
            Legends.deed(c, a, legend.renownWarlord, "It felled a warlord on the wall.")
            Legends.deed(c, a, legend.renownDangerous, "It was carried through a deep hall.")
            Legends.deed(c, b, legend.renownDangerous, "It was carried through a rescue.")
        }
    }

    private const val TLH = "Town's Last Hope"
    private const val RECIPE = "Forge, Advanced, a sword of Starsteel and Sun Ash, balanced temper, Heartfire switched on"

    val all: List<Scenario> by lazy { listOf(
        Scenario("heartfire_breakthrough", "Heartfire: a forging that breaks through",
            "Two Heartfire and the metal for it are in the forge. $RECIPE. The workbench shows the chance first. On this save that forging breaks through: the sword leaves the anvil Legendary.",
            "Constructed: $TLH on seed $BREAKTHROUGH_SEED, day 1, the first opening taken. Set by hand: 2 Heartfire, 400 gold, 2 Starsteel and 2 Sun Ash. The seed is the first on which that forging breaks through.", endDay = false) { atTheAnvil(BREAKTHROUGH_SEED) },
        Scenario("heartfire_miss", "Heartfire: a forging that does not break through",
            "The same forge on another morning. $RECIPE. On this save the Heartfire does not take: the sword is Epic with one more property, and the Heartfire and the energy are spent all the same.",
            "Constructed: $TLH on seed $MISS_SEED, day 1, the first opening taken. Set by hand: 2 Heartfire, 400 gold, 2 Starsteel and 2 Sun Ash. The seed is the first on which that forging reaches quality ${config.epicMin} and still does not break through.", endDay = false) { atTheAnvil(MISS_SEED) },
        Scenario("siege_preparation", "Guild: a siege night with their preparation standing",
            "The morning of the first siege and nobody spoiled what they prepared: the wall card names it and the answers to it. Press End Day: the siege card says what decided the night.",
            "Played: $TLH, NOVICE (supply runs only), seed $SIEGE_SEED, stopped on the morning of day ${config.siegeInterval}; a blessing on offer was taken.", endDay = false) { unspoiledSiege() },
        Scenario("supplier_grateful", "Guild: the supplier is grateful",
            "The supplier's cart came through. Open Supplies: the mood is named with its last day, and after End Day every limited material has ${config.guild.supplierGratefulStock} more in stock than usual.",
            "Constructed: $TLH on seed $SUPPLIER_SEED, day 1, the first opening taken. Set by the guild's own rule (`Missions.supplier`): the cart got through. Set by hand: 400 gold.", endDay = false) { supplier(true) },
        Scenario("supplier_shaken", "Guild: the supplier is shaken",
            "The supplier's cart was lost on a job the guild took. Open Supplies: the mood is named with its last day, and after End Day every limited material has ${config.guild.supplierShakenStock} fewer in stock than usual.",
            "Constructed: $TLH on seed $SUPPLIER_SEED, day 1, the first opening taken. Set by the guild's own rule (`Missions.supplier`): the cart was lost. Set by hand: 400 gold.", endDay = false) { supplier(false) },
        Scenario("legend_lines", "Legends: by Heartfire, by deeds, and one deed short",
            "Three weapons in storage. Open each: one is Legendary by Heartfire, one Legendary by its deeds with the deeds in its history, one shows its renown short of a legend.",
            "Constructed: $TLH on seed $BREAKTHROUGH_SEED, day 1. By real commands: the Heartfire forging and two safe iron swords. By the game's own rule (`Legends.deed`): two deeds on one sword, one on the other. Set by hand: Heartfire, gold and materials for the forgings.",
            endDay = false, blade = { s -> s.storedWeapons().firstOrNull { it.legend == LegendOrigin.DEEDS } }) { legends() },
    ) }
}
