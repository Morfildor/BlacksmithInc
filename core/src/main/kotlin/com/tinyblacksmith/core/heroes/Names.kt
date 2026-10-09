package com.tinyblacksmith.core.heroes

import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.LineageAnchor
import com.tinyblacksmith.core.rng.Rng

/**
 * Hero names (plan 4.4). Each name costs exactly one draw on the caller's stream whatever the pools hold, as the plain
 * pick from the whole list did before: avoidance only narrows the list the draw picks from, so naming never moves a
 * later draw. No two living heroes share a first name or a surname (members of a lineage share theirs) and a full name
 * is not given twice in a run.
 */
object Names {

    /** A first name nobody in this run has carried, of any fate; a descendant does not take the ancestor's either. */
    fun first(ctx: ResolutionContext, rng: Rng, ancestor: LineageAnchor?): String {
        val heroes = ctx.heroes.values
        val ancestorFirst = ancestor?.heroName?.substringBefore(' ')
        val fresh = ctx.content.firstNames.filter { n -> n != ancestorFirst && heroes.none { it.name == n } }
        // A descendant's surname is fixed, so the first name alone has to keep the full name new.
        return rng.pick(fresh.ifEmpty {
            reused(ctx.content.firstNames, heroes, { it.name }) { n -> n != ancestorFirst && (ancestor == null || heroes.none { it.name == n && it.surname == ancestor.surname }) }
        })
    }

    /** A surname no living hero carries, no guild of this run is named for and no lineage of the account holds. */
    fun surname(ctx: ResolutionContext, rng: Rng, first: String): String {
        val heroes = ctx.heroes.values
        val reserved = ctx.town.guilds.mapNotNull { ctx.heroes[it.founderId]?.surname } + ctx.legacy.lineages.map { it.surname }
        val unused: (String) -> Boolean = { s -> heroes.none { it.name == first && it.surname == s } }
        val free = ctx.content.surnames.filter { s -> s !in reserved && unused(s) && heroes.none { it.isAlive && it.surname == s } }
        return rng.pick(free.ifEmpty { reused(ctx.content.surnames, heroes, { it.surname }, unused) })
    }

    /**
     * The pool is spent (a very long run, or a small catalog): the names of the heroes gone longest come round again,
     * still never a living hero's. Only when the living hold every allowed name is one of theirs shared.
     */
    private fun reused(names: List<String>, heroes: Collection<Hero>, of: (Hero) -> String, allowed: (String) -> Boolean): List<String> {
        val open = names.filter { n -> allowed(n) && heroes.none { it.isAlive && of(it) == n } }
        val lastLeft = open.associateWith { n -> heroes.filter { of(it) == n }.maxOfOrNull { it.diedOnDay ?: it.retiredOnDay ?: 0 } }.filterValues { it != null }
        val earliest = lastLeft.values.minOfOrNull { it!! } ?: return open.ifEmpty { names.filter(allowed).ifEmpty { names } }
        return lastLeft.filterValues { it == earliest }.keys.toList()
    }
}
