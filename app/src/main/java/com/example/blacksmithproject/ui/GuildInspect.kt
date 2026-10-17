package com.example.blacksmithproject.ui

import com.example.blacksmithproject.ui.theme.Ember
import com.tinyblacksmith.core.combat.Action
import com.tinyblacksmith.core.combat.Amount
import com.tinyblacksmith.core.combat.KitDef
import com.tinyblacksmith.core.combat.Cadence
import com.tinyblacksmith.core.combat.Move
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.combat.UnitDef
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ClassKitDef
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.model.Danger
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.text.Terms

/*
 * What an inspect card says about the people and the enemies of a guild run, and about a quest on the board (V3). Pure:
 * reads the save, the catalog and the engine's own reads (`fighterView`, `memberStatus`), never a draw and never a
 * command. The words of a rule are the catalog's own, split into when / what / how often by `ruleParts`.
 */

/** The statuses a move gives, takes or waits for: the keywords an enemy's or a class's card explains. */
internal fun Move.statuses(): List<Stat> = buildSet {
    (cadence as? Cadence.WhileHolding)?.let { add(it.stat) }
    actions.forEach { a ->
        when (a) {
            is Action.Give -> add(a.stat)
            is Action.Take -> add(a.stat)
            is Action.Guard -> add(Stat.GUARD)
            else -> {}
        }
    }
}.toList()

internal fun rarityWord(r: Rarity): String = Terms.rarity(r)

/** Whether an ability of [kit] reads Support (the Guardian's Guard, the Warden's and the Gravecaller's healing): only then is Support shown. */
internal fun readsSupport(kit: KitDef): Boolean = kit.moves.any { m -> m.actions.any { a -> a is Action.Heal && a.amount is Amount.Support || a is Action.Guard && a.amount is Amount.Support } }

/**
 * What a class does every round, as its card's first ability: the kit's moves by name, and the catalog's own line of
 * what the class does ([ClassKitDef.role]). Its passive rules follow as abilities of their own.
 */
internal fun kitLine(kit: ClassKitDef): TipLine = TipLine(kit.role, TipTone.ABILITY, title = kit.kit.moves.joinToString(" and ") { it.name }, source = "class")

/**
 * A hero of a guild run as a card: class and level, the numbers they would enter a fight with today (`fighterView`, as
 * the hero sheet reads them), what their class does and the rules it carries, their trait and speciality as gains, what
 * they cost the guild, their state (laid up, away, held, kept for the wall) and the weapon in hand with the names of its
 * rules. [home]: as they would stand on the town's wall, where some weapon rules differ. [actions] is usually "Full
 * details", the hero's sheet. Null for a hero the save no longer holds.
 */
fun GameEngine.heroTip(state: GameState, heroId: HeroId, home: Boolean = false, actions: List<TipAction> = emptyList(), tag: String = "inspect"): TipUi? {
    val h = state.heroes[heroId] ?: return null
    val g = state.guild
    val member = g?.member(heroId)
    val candidate = g?.candidates?.firstOrNull { it.heroId == heroId }?.takeIf { member == null }
    val cat = content.guild
    val kit = content.combat?.kitByClass?.get(h.classId)
    // Only a guild run fights where the smith can see: a classic hero's card has no fight numbers.
    val fighter = if (g != null && h.isAlive) fighterView(state, heroId, home = home) else null
    val weapon = if (h.isAlive) state.loanOf(heroId) ?: state.equippedWeapon(heroId) else null
    val trait = (member?.traitId ?: candidate?.traitId)?.let { cat?.trait(it) }
    val branch = cat?.speciality(member?.specialityId)
    val weaponRules = fighter?.let { f -> f.effects.filter { it.id in f.weaponRules } }.orEmpty()
    val warning = when {
        h.fate == HeroFate.DEAD -> "Fallen" + (h.diedOnDay?.let { " on day $it" } ?: "")
        h.fate == HeroFate.RETIRED -> "Retired" + (h.retiredOnDay?.let { " on day $it" } ?: "")
        member != null && g != null -> memberStatus(state, g, member)
        else -> null
    }
    return TipUi(
        name = h.fullName, kind = "${content.heroClass(h.classId).name} · level ${h.level}", icon = Sprites.portrait(h),
        meta = when { member != null -> "Guild member"; candidate != null -> "For hire"; else -> "Of the town" },
        stats = fighter?.let { f ->
            listOfNotNull(
                // Between fights a hero's health is a percentage; in a fight it is on the fight's own scale.
                TipStat("Health %", "${h.health}%"),
                TipStat("Health", "${f.health}/${f.maxHealth}"),
                TipStat("Attack", if (f.strikeMax > f.strikeMin) "${f.strikeMin} to ${f.strikeMax}" else "${f.strikeMin}"),
                TipStat("Support", "${f.support}").takeIf { kit != null && readsSupport(kit.kit) },
            )
        } ?: listOf(TipStat("Health", warning ?: Labels.health(h).replaceFirstChar { it.uppercase() })),
        sections = listOf(
            TipSection("In a fight", if (fighter == null || kit == null) emptyList() else listOf(kitLine(kit)) + kit.kit.passives.map { it.ability("class") }),
            TipSection(null, listOfNotNull(
                trait?.let { abilityLine(it.name, it.description, "trait", TipTone.BONUS) },
                branch?.let { abilityLine(it.name, it.description, "speciality", TipTone.BONUS) },
                member?.let { TipLine("Keeps ${it.sharePercent}% of the gold from quests they go on", TipTone.DRAWBACK) },
                candidate?.let { TipLine("Recruitment fee ${it.fee} gold. Keeps ${it.sharePercent}% of the gold from quests they go on", TipTone.DRAWBACK) },
                TipLine("Reserved for the wall", TipTone.CONDITION).takeIf { g != null && heroId in g.reserved },
            )),
            TipSection("Weapon", when {
                !h.isAlive -> emptyList()
                weapon == null -> listOf(TipLine("Unarmed", TipTone.DRAWBACK))
                else -> listOfNotNull(
                    TipLine(
                        weaponRules.joinToString(", ") { CombatContent.label(it.id, it.name) }.ifEmpty { "No effect beyond its normal attack" }, TipTone.PLAIN, title = weapon.name,
                        source = rarityWord(weapon.rarity) + if (weapon.loanedTo == heroId) " · on loan" else "",
                    ),
                    TipLine("Worn: condition ${weapon.condition} of 100", TipTone.DRAWBACK).takeIf { weapon.condition < config.wornConditionThreshold },
                )
            }),
        ),
        keywords = if (fighter == null || kit == null) emptyList() else statKeywords(support = readsSupport(kit.kit)) + keywords(
            kit.kit.moves.flatMap { it.statuses() } + (kit.kit.passives + trait?.effects.orEmpty() + branch?.effects.orEmpty() + weaponRules).flatMap { it.statuses() }, config,
        ),
        warning = warning?.takeIf { fighter != null || member != null },
        actions = actions, tag = tag,
    )
}

/** "Summoner", "Leader", "Elite": the tags that decide the priority target (summoner, then leader) and that an effect can answer (Giant Slayer), in words. */
private fun unitKind(unit: UnitDef): String =
    (listOf("Enemy") + listOfNotNull(
        "Summoner".takeIf { CombatContent.TAG_ANCHOR in unit.tags }, "Leader".takeIf { CombatContent.TAG_LEADER in unit.tags }, "Elite".takeIf { CombatContent.TAG_ELITE in unit.tags },
    )).joinToString(" · ")

/** One number of a unit at each of [percents], as `Loadout.enemy` scales it: "12", or "12 to 14" when they differ. */
private fun scaled(percents: List<Int>, of: (Int) -> Pair<Int, Int>): String {
    val all = percents.ifEmpty { listOf(100) }.map(of)
    val low = all.minOf { it.first }
    val high = all.maxOf { it.second }
    return if (low == high) "$low" else "$low to $high"
}

/** A unit's health at each of [percents]: the field's own scaling (`Loadout.enemy`). */
internal fun unitHealth(unit: UnitDef, percents: List<Int>): String = scaled(percents) { p -> Loadout.enemy(unit, "inspect", p).maxHealth.let { it to it } }

/**
 * An enemy unit as a card, from the combat catalog's own definition: its health and strike at [percents] (one per unit
 * on the field, scaled as the fight scales them), the element it is weak to and the one it resists, each move by name
 * with what the board already says of it, and every rule it carries. [count] is how many stand on the field; [note] what
 * changes the numbers before the fight (the watch on the wall).
 */
fun enemyTip(unit: UnitDef, count: Int, percents: List<Int>, config: BalanceConfig, note: String? = null, tag: String = "inspect"): TipUi {
    val rules = (unit.kit.passives + unit.effects).distinctBy { it.id }
    return TipUi(
        name = unit.name, kind = unitKind(unit), meta = if (count > 1) "$count on the field" else null, nameColor = Ember,
        stats = listOfNotNull(
            TipStat("Health", unitHealth(unit, percents)),
            TipStat("Attack", scaled(percents) { p -> Loadout.enemy(unit, "inspect", p).let { it.strikeMin to it.strikeMax } }).takeIf { unit.strikeMax > 0 },
            TipStat("Support", scaled(percents) { p -> Loadout.enemy(unit, "inspect", p).support.let { it to it } }).takeIf { readsSupport(unit.kit) },
            unit.weakTo?.let { TipStat("Weak to", Terms.element(it), FactTone.GOOD) },
            unit.resists?.let { TipStat("Resists", Terms.element(it), FactTone.WARN) },
        ),
        sections = listOf(
            TipSection("Abilities", unit.kit.moves.map { m -> m.telegraph?.let { abilityLine(m.name, it) } ?: TipLine("", TipTone.ABILITY, title = m.name) }),
            TipSection("In a fight", rules.map { it.ability() } + unit.tickResist.map { (stat, percent) -> TipLine("Takes $percent% less damage from ${Terms.stat(stat)}.", TipTone.PLAIN) }),
        ),
        keywords = (if (unit.strikeMax > 0) statKeywords(support = readsSupport(unit.kit)) else emptyList()) + keywords(unit.kit.moves.flatMap { it.statuses() } + rules.flatMap { it.statuses() } + unit.tickResist.keys, config),
        note = note, tag = tag,
    )
}

/**
 * A quest on the board as a card: what it asks, the facts that decide (reward, danger, how long, the fee, the last day to
 * send a party), each stage as a row, what is different about this posting, what it brings besides gold, and what to
 * bring and what failing costs. The missed siege stays on the board's row as well. [onOpen] is the way to the party sheet.
 */
fun offerTip(o: OfferUi, onOpen: (() -> Unit)? = null, tag: String = "inspect"): TipUi = TipUi(
    name = o.title, kind = listOfNotNull("Quest", o.tag, o.modifierName).joinToString(" · "), meta = o.purpose.ifEmpty { null },
    stats = listOfNotNull(
        TipStat("Reward", o.rewardSummary).takeIf { o.rewardSummary.isNotEmpty() },
        TipStat("Danger", dangerWord(o.danger), if (o.danger == Danger.SAFE) FactTone.PLAIN else FactTone.WARN),
        TipStat("Away", if (o.days == 1) "1 day" else "${o.days} days"),
        TipStat("Back", "day ${o.returnDay}"),
        TipStat("Fee", if (o.feeGold > 0) "${o.feeGold} gold" else "None"),
        o.lastDay?.let { TipStat("Send by", it) },
    ),
    sections = listOf(
        // Each stage as its name, danger, objective and reward; how stages pay out and when the party may turn back is
        // the quest sheet's ("Choose a party"), so the card stays a summary.
        TipSection("Stages", o.stages.map { st ->
            TipLine("${st.objective}. Reward: ${st.reward}.", TipTone.PLAIN, title = st.label.substringBefore(": "), source = st.danger.substringBefore(":"))
        }),
        TipSection("What is different", listOfNotNull(o.modifier?.let { m -> TipLine(m.substringAfter(": ", m), TipTone.CONDITION, title = o.modifierName) })),
        TipSection(null, o.specials.map { TipLine(it, TipTone.BONUS) } + listOfNotNull(
            TipLine(dangerWords(o.danger), TipTone.DRAWBACK).takeIf { o.danger != Danger.SAFE },
            o.missesSiege?.let { TipLine(it, TipTone.DRAWBACK) },
            o.stake?.let { TipLine(it, TipTone.DRAWBACK) },
            o.prep.takeIf { it.isNotBlank() }?.let { TipLine("Bring: $it", TipTone.CONDITION) },
            o.failure.takeIf { it.isNotBlank() }?.let { TipLine("If it fails: $it", TipTone.DRAWBACK) },
        )),
    ),
    note = o.timingNote.ifEmpty { null },
    actions = listOfNotNull(onOpen?.let { TipAction(if (o.planned) "Change the party" else "Choose a party", "${tag}_open", it) }),
    tag = tag,
)
