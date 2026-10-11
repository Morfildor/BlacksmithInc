package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.content.CombatCatalog
import com.tinyblacksmith.core.content.InteractionDef
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.Journal as JournalModel

/**
 * What the notebook holds about fights, in three kinds that never mix. A rule is LEARNED when the smith has forged a
 * weapon that carries it. A path is SUGGESTED when a learned rule takes part in it: the rules say it could happen.
 * A path is WITNESSED only when a real contract or siege recorded it. A preview or the bench writes nothing here,
 * because neither has a context to write to.
 */
object Interactions {
    enum class Evidence { SUGGESTED, WITNESSED }

    /** One path as the notebook may show it: [known] are its rules the smith has forged, in the catalog's order. */
    data class Entry(val def: InteractionDef, val evidence: Evidence, val known: List<EffectDef>)

    /** [learned] in the catalog's order, [entries] for the paths a learned rule opens, [hidden] how many paths are still unseen. */
    data class Page(val learned: List<EffectDef>, val entries: List<Entry>, val hidden: Int)

    /** The paths [fight] shows: every rule of one of a path's sets fired for the party. Pure. */
    fun shownIn(fight: FightResult, combat: CombatCatalog): Set<String> {
        val party = fight.actors.filter { it.side == Side.PARTY }.map { it.key }.toSet()
        val fired = fight.events.filter { it.effectId != null && it.source in party }.mapNotNull { it.effectId }.toSet()
        return combat.interactions.filter { i -> i.shown.any { fired.containsAll(it) } }.map { it.id }.toSet()
    }

    /** A weapon has left the anvil in a guild run: its fight rules are learned. A classic run has no fights that read them. */
    fun learn(ctx: ResolutionContext, weapon: Weapon) {
        if (ctx.guild == null || ctx.content.combat == null) return
        val ids = WeaponRules.of(weapon, ctx.content).rules.map { it.id }
        val journal = ctx.legacy.journal
        if (journal.learnedRules.containsAll(ids)) return
        ctx.legacy = ctx.legacy.copy(journal = journal.copy(learnedRules = journal.learnedRules + ids))
    }

    /** A real fight has been resolved: what it showed for the first time is written down and told. Draws nothing. */
    fun witness(ctx: ResolutionContext, fight: FightResult) {
        val combat = ctx.content.combat ?: return
        val journal = ctx.legacy.journal
        val fresh = shownIn(fight, combat) - journal.witnessed
        if (fresh.isEmpty()) return
        ctx.legacy = ctx.legacy.copy(journal = journal.copy(witnessed = journal.witnessed + fresh))
        for (def in combat.interactions.filter { it.id in fresh }) {
            ctx.emit(EventType.DISCOVERY, 2, "Seen in a fight. ${def.name}. ${def.description}", data = mapOf("interaction" to def.id))
        }
    }

    fun page(journal: JournalModel, combat: CombatCatalog): Page {
        val rules = combat.allEffects.distinctBy { it.id }
        val entries = combat.interactions.mapNotNull { def ->
            val known = rules.filter { it.id in def.pieces && it.id in journal.learnedRules }
            when {
                def.id in journal.witnessed -> Entry(def, Evidence.WITNESSED, known)
                known.isNotEmpty() -> Entry(def, Evidence.SUGGESTED, known)
                else -> null
            }
        }
        return Page(rules.filter { it.id in journal.learnedRules }, entries, combat.interactions.size - entries.size)
    }
}
