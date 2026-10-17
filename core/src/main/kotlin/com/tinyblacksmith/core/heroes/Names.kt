package com.tinyblacksmith.core.heroes

import com.tinyblacksmith.core.content.CultureDef
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
     * A living-heroes name from [culture] (final design 6.1), two draws on [rng] like the old pair (one for a descendant, whose
     * surname is the ancestor's), so a classic era moves its stream exactly as before. A given name nobody of the
     * culture has carried this era comes first; the surname is one no living hero, lineage or company founder holds. Only the
     * full name is a hard rule: no two living heroes share one. When a culture's pairs are spent the reservations of the gone
     * are retired, then a byname of residence is used, never a number.
     */
    fun living(ctx: ResolutionContext, rng: Rng, culture: CultureDef, ancestor: LineageAnchor?, kin: Hero?): Pair<String, String> {
        val heroes = ctx.heroes.values
        val alive = heroes.filter { it.isAlive }
        val fixed = ancestor?.surname ?: kin?.surname
        val r1 = rng.nextDouble()
        // The old pair drew a surname for everybody but a descendant: a relative's surname still spends that draw.
        val r2 = if (ancestor == null) rng.nextDouble() else 0.0
        fun at(list: List<String>, r: Double) = list[(r * list.size).toInt().coerceIn(0, list.size - 1)]
        fun free(first: String, last: String) = alive.none { it.name == first && it.surname == last }
        val avoid = setOfNotNull(ancestor?.heroName?.substringBefore(' '), kin?.name)
        val used = heroes.filter { it.cultureId == culture.id }.mapTo(HashSet()) { it.name } + avoid
        val given = culture.given.filter { it !in used }.ifEmpty { culture.given.filter { it !in avoid } }.ifEmpty { culture.given }
        if (fixed != null) {
            val open = given.filter { free(it, fixed) }.ifEmpty { culture.given.filter { free(it, fixed) } }
            if (open.isNotEmpty()) return at(open, r1) to fixed
        } else {
            val first = at(given, r1)
            val reserved = ctx.town.guilds.mapNotNull { ctx.heroes[it.founderId]?.surname }.toSet() + ctx.legacy.lineages.map { it.surname } + alive.filter { it.kinId != null }.map { it.surname }
            val last = culture.family.filter { s -> s !in reserved && alive.none { it.surname == s } && free(first, s) }
                .ifEmpty { culture.family.filter { s -> s !in reserved && free(first, s) } }
                .ifEmpty { culture.family.filter { free(first, it) } }
            if (last.isNotEmpty()) return first to at(last, r2)
            ctx.content.bynames.filter { free(first, it) }.takeIf { it.isNotEmpty() }?.let { return first to at(it, r2) }
        }
        // Every pair of this culture is worn by the living: the first free combination in a fixed order, then a byname.
        val surnames = listOfNotNull(fixed).ifEmpty { culture.family + ctx.content.bynames }
        for (f in culture.given) for (s in surnames) if (free(f, s)) return f to s
        for (f in culture.given) for (s in ctx.content.bynames) if (free(f, s)) return f to s
        error("Every name of ${culture.id} is worn by a living hero")
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
