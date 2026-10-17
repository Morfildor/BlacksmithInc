package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.guild.GuildOps
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.MemberStatus
import com.tinyblacksmith.core.model.WeaponLocation

/**
 * The gate between a decoded save and the engine: can this build go on playing that run? A run from older rules,
 * content or balance continues forward under the installed ones; a run this build cannot play is refused with
 * reasons instead of crashing somewhere inside a later command.
 */
object Compatibility {
    sealed interface Result {
        /** [state] is the run with its rules, content and balance versions stamped to the installed ones, and brought within the installed caps. */
        data class Admitted(val state: GameState) : Result
        data class Unsupported(val problems: List<String>) : Result
    }

    /**
     * Pure. Never throws. A run within the installed caps is admitted exactly as it was, stamps aside: no event, history,
     * serial or RNG stream state is touched. A run written under a balance with higher caps (armory, overwork, hero
     * level, champions, relic slots, shelf, the guild's roster, party and wall) is brought within them first, with one
     * line in the day's records that says what changed, so a balance change never discards a run. What remains wrong
     * after that (unknown content, dead owners, broken books) is refused.
     */
    fun admit(state: GameState, content: ContentCatalog, config: BalanceConfig): Result {
        val problems = mutableListOf<String>()
        if (state.rulesVersion > GameEngine.RULES_VERSION) problems += "Run uses rules ${state.rulesVersion}, newer than this build's ${GameEngine.RULES_VERSION}"
        if (state.contentVersion > content.version) problems += "Run uses content ${state.contentVersion}, newer than this build's ${content.version}"
        val clamped = if (problems.isEmpty()) try { clampToCaps(state, content, config) } catch (_: Exception) { state } else state
        // The shelf the engine allows (GameEngine.shelfSlots): a tool can add slots to the configured ones.
        problems += Invariants.check(clamped, config, shelfSlots(clamped, content, config), content)
        if (problems.isNotEmpty()) return Result.Unsupported(problems)
        return Result.Admitted(roadOpened(clamped).copy(rulesVersion = GameEngine.RULES_VERSION, contentVersion = content.version, balanceVersion = config.version))
    }

    private fun shelfSlots(state: GameState, content: ContentCatalog, config: BalanceConfig): Int =
        config.shelfSlots + content.tools.filter { it.effect == ToolEffect.SHELF_SLOTS }.sumOf { it.magnitudePerLevel * (state.tools[it.id] ?: 0) }

    /**
     * Rules 7: a run from before the deep road that has fought a siege had the big ingredients on sale; it keeps them,
     * the road open from the day it is loaded. One that has not keeps its road shut until its first siege's story.
     */
    private fun roadOpened(state: GameState): GameState =
        if (state.rulesVersion < 7 && state.deepRoadOpenDay == null && DeepRoad.fought(state.town) >= 1) state.copy(deepRoadOpenDay = state.day) else state

    /** Every count the installed balance caps, brought within it. Returns [state] itself when nothing is over. */
    private fun clampToCaps(state: GameState, content: ContentCatalog, config: BalanceConfig): GameState {
        val ctx = ResolutionContext(state, content, config)
        val notes = mutableListOf<String>()
        if (ctx.overworkToday > config.maxOverworkPerDay) {
            ctx.overworkToday = config.maxOverworkPerDay
            notes += "Today you can borrow at most ${config.maxOverworkPerDay} energy from tomorrow."
        }
        if (ctx.town.armory > config.armoryMax) {
            ctx.town = ctx.town.copy(armory = config.armoryMax)
            notes += "The town's armory holds at most ${config.armoryMax}."
        }
        val tooSeasoned = ctx.heroes.values.filter { it.level > config.heroMaxLevel }
        if (tooSeasoned.isNotEmpty()) {
            tooSeasoned.forEach { ctx.heroes[it.id] = it.copy(level = config.heroMaxLevel) }
            notes += "Heroes stop at level ${config.heroMaxLevel}."
        }
        val faint = ctx.heroes.values.filter { it.isAlive && it.health <= config.heroDeathHealthFloor }
        if (faint.isNotEmpty() && config.heroDeathHealthFloor < 100) {
            faint.forEach { ctx.heroes[it.id] = it.copy(health = config.heroDeathHealthFloor + 1) }
            notes += "A living hero has at least ${config.heroDeathHealthFloor + 1} health."
        }
        if (ctx.town.championIds.size > config.championCount) {
            ctx.town = ctx.town.copy(championIds = ctx.town.championIds.take(config.championCount))
            notes += "The wall has ${config.championCount} defender places."
        }
        if (ctx.relics.size > config.depth.relicSlots) {
            val gone = ctx.relics.drop(config.depth.relicSlots)
            ctx.relics = ctx.relics.take(config.depth.relicSlots)
            notes += "The workshop has room for ${config.depth.relicSlots} relics, so " + gone.joinToString(" and ") { content.relic(it.id)?.name ?: it.id } + " left it."
        }
        val slots = shelfSlots(state, content, config)
        val listed = state.listedWeapons()
        if (listed.size > slots) {
            listed.drop(slots).forEach { w -> ctx.weapons[w.id] = w.copy(location = WeaponLocation.Storage) }
            notes += "The shelf holds $slots weapons. The rest went back to storage."
        }
        ctx.guild?.let { g ->
            val cfg = config.guild
            val plan = g.planned
            if (plan != null && (plan.heroIds.size > cfg.partyMax || g.members.size > cfg.maxMembers)) {
                val fee = g.offers.firstOrNull { it.id == plan.offerId }?.fee ?: 0
                Missions.cancelPlan(ctx)
                notes += "Today's planned party stays home." + (if (fee > 0) " The $fee gold paid to send it came back." else "")
            }
            if (ctx.guild!!.reserved.size > cfg.defenders) {
                ctx.guild = ctx.guild!!.copy(reserved = ctx.guild!!.reserved.take(cfg.defenders))
                notes += "The guild can reserve at most ${cfg.defenders} members for the wall."
            }
            val over = ctx.guild!!.members.size - cfg.maxMembers
            if (over > 0) {
                val leaving = ctx.guild!!.members.withIndex().filter { it.value.status == MemberStatus.HOME }
                    .sortedWith(compareByDescending<IndexedValue<com.tinyblacksmith.core.model.GuildMember>> { it.value.joinedDay }.thenByDescending { it.index }).take(over).map { it.value.heroId }
                for (id in leaving) GuildOps.release(ctx, id, "Returned to the forge when ${ctx.hero(id).fullName} left the guild.")
                if (leaving.isNotEmpty()) notes += "The guild has room for ${cfg.maxMembers} members, so " + leaving.joinToString(" and ") { ctx.hero(it).fullName } + " left it."
            }
        }
        if (notes.isEmpty()) return state
        ctx.emit(EventType.WORLD_EVENT, 5, "This version of the game has new limits, so your era was adjusted to fit them. " + notes.joinToString(" "), data = mapOf("event" to "balance_limits"))
        return ctx.toState()
    }
}
