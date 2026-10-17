package com.example.blacksmithproject.ui

import com.tinyblacksmith.core.combat.Action
import com.tinyblacksmith.core.combat.Amount
import com.tinyblacksmith.core.combat.Condition
import com.tinyblacksmith.core.combat.Cost
import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.Limit
import com.tinyblacksmith.core.combat.Who
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ConstraintKind
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.text.Terms
import com.example.blacksmithproject.ui.replay.STAT_ORDER
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.replay.statColor
import com.example.blacksmithproject.ui.replay.statMeaning

/*
 * What an inspect card says about the things of the Forge (V3). Pure: reads the catalog, the save and the engine's own
 * outlook, never a draw. A rule is shown as an ability (when it fires, what it does, how often) split from the catalog's
 * own sentence, so the words are the game's and nothing is reworded into a promise.
 */

/** A rule's sentence as an ability: "When X, Y. Once per round." gives trigger "When X", text "Y.", limit "Once per round." */
internal data class RuleParts(val trigger: String?, val text: String, val limit: String?)

private val LIMIT = Regex("^(Once|Twice|Three times|Up to [a-z]+ times|At most [a-z0-9]+ times?)\\b.*per (round|fight)\\.?$")
private val TRIGGER = Regex("^(When|Whenever|While|At the start of|At the end of|Before|After|If)\\b[^,]*,\\s+")

internal fun ruleParts(description: String): RuleParts {
    val sentences = description.trim().split(Regex("(?<=\\.)\\s+")).filter { it.isNotBlank() }.toMutableList()
    val limit = sentences.lastOrNull()?.takeIf { sentences.size > 1 && LIMIT.matches(it) }?.also { sentences.removeAt(sentences.lastIndex) }
    val first = sentences.firstOrNull().orEmpty()
    val m = TRIGGER.find(first)
    if (m == null) return RuleParts(null, sentences.joinToString(" "), limit)
    val trigger = first.substring(0, m.range.last + 1).trimEnd().removeSuffix(",")
    val rest = first.substring(m.range.last + 1).replaceFirstChar { it.uppercase() }
    return RuleParts(trigger, (listOf(rest) + sentences.drop(1)).joinToString(" "), limit)
}

/** The statuses a rule reads or gives, from its definition (never from its wording): the keywords its card explains. */
fun EffectDef.statuses(): List<Stat> = buildSet {
    trigger.stat?.let(::add)
    fun amount(a: Amount) { if (a is Amount.OfHolderStat) add(a.stat) }
    actions.forEach { a ->
        when (a) {
            is Action.Give -> { add(a.stat); amount(a.amount) }
            is Action.Take -> { add(a.stat); amount(a.amount) }
            is Action.Guard -> { add(Stat.GUARD); amount(a.amount) }
            is Action.Damage -> amount(a.amount)
            is Action.Heal -> amount(a.amount)
            else -> {}
        }
    }
    conditions.forEach { c ->
        when (c) {
            is Condition.HolderHas -> add(c.stat)
            is Condition.HolderLacks -> add(c.stat)
            is Condition.TargetHas -> add(c.stat)
            is Condition.StatIn -> addAll(c.stats)
            else -> {}
        }
    }
    (cost as? Cost.Spend)?.let { add(it.stat) }
}.sortedBy { STAT_ORDER.indexOf(it) }

/** The statuses named by [stats], once each, with what each does in the fight's own numbers. */
internal fun keywords(stats: Collection<Stat>, config: BalanceConfig): List<TipKeyword> =
    stats.distinct().sortedBy { STAT_ORDER.indexOf(it) }.map { TipKeyword(Terms.stat(it), statMeaning(it, config.guild.fight), statColor(it)) }

/**
 * The stat Attack, said once on a card that shows it, so it is not read as the action "an attack" (docs/WRITING_GUIDE.md
 * section 2.1). [support] adds Support where the card shows that too.
 */
internal fun statKeywords(support: Boolean = false): List<TipKeyword> = listOfNotNull(
    TipKeyword("Attack", "The damage of this fighter's attacks.", Cream),
    TipKeyword("Support", "How much Armor or healing this fighter's own ability gives each time.", Cream).takeIf { support },
)

/** A rule as an ability line; [source] is where it comes from ("element", "metal") when the card holds several. */
/**
 * A rule as an ability row: its name, when it fires and how often beside it, then what it does. When the rule's type says
 * exactly when it fires ([trigger], from [triggerWords]) that short label stands in for the sentence's own "When ..."
 * clause; otherwise the clause is kept whole. How often is the rule's typed limit ([limit], from [limitWords]), left out
 * when the sentence already says it. Nothing is cut: what remains is the rule's own main clause.
 */
internal fun abilityLine(name: String, description: String, source: String? = null, tone: TipTone = TipTone.ABILITY, trigger: String? = null, limit: String? = null): TipLine {
    val p = ruleParts(description)
    val often = OFTEN.containsMatchIn(p.text)
    return TipLine(p.text, tone, title = name, source = source, trigger = p.trigger?.let { trigger ?: it }, limit = if (often) null else limit ?: p.limit?.removeSuffix("."))
}

private val OFTEN = Regex("(per round|per fight|each fight|each round|every round|every fight)", RegexOption.IGNORE_CASE)

/**
 * When a rule fires, in a few words, only where its typed trigger and conditions say exactly that; null where only the
 * rule's own sentence can say it (that sentence is then shown whole).
 */
internal fun triggerWords(e: EffectDef): String? {
    val t = e.trigger
    val c = e.conditions
    return when {
        t.kind == EventKind.DAMAGE && t.who == Who.SELF_SOURCE && c == listOf(Condition.FromAction) -> "On hit"
        t.kind == EventKind.ROUND_START && t.who == Who.ANY && c.isEmpty() && e.limit.perFight == 1 && e.limit.perRound == 0 -> "Fight start"
        t.kind == EventKind.ROUND_START && t.who == Who.ANY && c.isEmpty() && e.limit.perFight == 0 -> "Each round"
        t.kind == EventKind.ROUND_END && t.who == Who.ANY && c.isEmpty() && e.limit.perFight == 0 -> "Round end"
        t.kind == EventKind.GUARD_BROKEN && t.who == Who.SELF_TARGET && c == listOf(Condition.SourceIsFoe) -> "Enemy breaks Armor"
        else -> null
    }
}

/** How often a rule can fire, from its typed limit: "Once per round", "Twice per fight", "Once per round, 3 times per fight". */
internal fun limitWords(l: Limit): String? {
    fun times(n: Int) = when (n) { 1 -> "once"; 2 -> "twice"; else -> "$n times" }
    val parts = listOfNotNull(l.perRound.takeIf { it > 0 }?.let { "${times(it)} per round" }, l.perFight.takeIf { it > 0 }?.let { "${times(it)} per fight" })
    return parts.joinToString(", ").takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() }
}

internal fun EffectDef.ability(source: String? = null) = abilityLine(name, description, source, trigger = triggerWords(this), limit = limitWords(limit))
internal fun BladeRuleUi.ability(withSource: Boolean = true) =
    abilityLine(name, description, if (withSource) source + if (alsoFrom.isEmpty()) "" else ", also ${alsoFrom.joinToString(" and ")}" else null, trigger = trigger, limit = limit)

private fun MaterialCategory.word() = when (this) { MaterialCategory.CORE -> "Metal"; MaterialCategory.AUGMENT -> "Infusion"; MaterialCategory.CATALYST -> "Catalyst" }

/** How a material's caveat reads: a drawback with its "−", a condition with its "◇", a comparison as plain words. */
internal fun ConstraintKind?.tone(): TipTone = when (this) { ConstraintKind.CONDITION -> TipTone.CONDITION; ConstraintKind.CONTEXT -> TipTone.PLAIN; else -> TipTone.DRAWBACK }

/**
 * A metal, an augment or a catalyst as a card: what it is, what the smith holds and pays, the most a metal can reach,
 * how the besieger takes its element, and (a guild run) the rule it gives a weapon and what choosing it costs.
 * [onRestock] adds "Buy more"; [blocked] is why it cannot be placed now, said first.
 */
fun GameEngine.materialTip(state: GameState, id: MaterialId, threat: ThreatUi?, blocked: String? = null, onRestock: (() -> Unit)? = null, tag: String = "inspect"): TipUi {
    val m = content.material(id)
    val guild = state.guild != null
    val owned = state.materials[id] ?: 0
    val mark = m.element?.let { threat?.marks?.get(it) }
    val combat = content.combat
    val trade = combat?.coreById?.get(id)?.let { it.constraint to it.constraintKind } ?: combat?.notes?.get(id)?.let { it.constraint to it.constraintKind }
    val rules = if (guild) materialRules(content, id) else emptyList()
    return TipUi(
        name = m.name, kind = listOfNotNull(m.category.word(), m.element?.let { Terms.element(it) }).joinToString(" · "), icon = Sprites.material(id),
        accent = m.element?.let { com.example.blacksmithproject.ui.detail.elementColor(it) },
        meta = if (owned == 0) "None owned" else "$owned owned",
        stats = listOfNotNull(
            TipStat("Price", "${m.price} gold each"),
            // Ceilings, not the weapon's: the most quality and rarity a forge in this metal can reach without Heartfire.
            TipStat("Quality cap", "${Forge.qualityCap(m.tier, config)}").takeIf { m.category == MaterialCategory.CORE },
            TipStat("Rarity limit", if (Forge.holdsEpic(m.tier, config)) "Up to Epic" else "Up to Rare").takeIf { m.category == MaterialCategory.CORE },
            mark?.let { TipStat("Against the siege", it.label, if (it.counters) FactTone.GOOD else FactTone.WARN) },
        ),
        sections = listOf(
            TipSection(null, rules.map { it.ability() }),
            TipSection(null, listOfNotNull(
                m.element?.let { TipLine("Adds ${Terms.element(it).lowercase()} to the weapon", TipTone.BONUS) }.takeIf { !guild },
                trade?.takeIf { guild }?.let { (text, kind) -> TipLine(text, kind.tone()) },
            )),
        ),
        keywords = keywords(rules.flatMap { it.statuses() }, config),
        warning = blocked,
        // Buying is offered only where it is the way forward: none left.
        actions = listOfNotNull(onRestock?.takeIf { owned == 0 }?.let { TipAction("Buy", "${tag}_restock", it) }),
        tag = tag,
    )
}

/**
 * A weapon type as a card: who it suits, its share of a strike and (a guild run) the rule it gives. Its pattern sentence
 * only repeats those numbers and that rule, so the card leaves it to the details.
 */
fun GameEngine.familyTip(state: GameState, id: WeaponFamilyId, tag: String = "inspect"): TipUi {
    val f = content.family(id)
    val guild = state.guild != null
    val combat = content.combat?.familyById?.get(id)?.takeIf { guild }
    val fans = fans(id)
    return TipUi(
        name = f.name, kind = "Weapon type", icon = Sprites.weapon(id, content.materials(MaterialCategory.CORE).first().id, null),
        stats = listOfNotNull(
            fans.takeIf { it.isNotEmpty() }?.let { TipStat("Suits", it.joinToString(", ")) },
            combat?.let { TipStat("Attack", "${it.strikePercent}%") },
            combat?.let { TipStat("Support", "${it.supportPercent}%") },
        ),
        sections = listOf(TipSection(null, combat?.effects.orEmpty().map { it.ability() })),
        keywords = (if (combat != null) statKeywords(support = true) else emptyList()) + keywords(combat?.effects.orEmpty().flatMap { it.statuses() }, config),
        tag = tag,
    )
}

/** A finishing technique as a card: an Advanced forge only, and what it changes in the forge's own words. */
fun techniqueTip(t: Technique, tag: String = "inspect"): TipUi = TipUi(
    name = Labels.technique(t), kind = "Technique · Advanced forge",
    sections = listOf(TipSection(null, listOf(TipLine(Labels.techniqueExplanation(t), TipTone.PLAIN)))),
    tag = tag,
)

/**
 * The recipe on the anvil as a card (a guild run): its quality range, who it suits, the rules every forge of it carries,
 * what they still need, its trade-off, and what only a roll may add, kept apart and never called certain.
 */
fun recipeTip(o: ForgeOutlookUi, title: String, icon: Int?, config: BalanceConfig, actions: List<TipAction>, tag: String = "forge_inspect", accent: androidx.compose.ui.graphics.Color? = null): TipUi = TipUi(
    name = title, kind = "Recipe · ${o.role}", icon = icon, accent = accent,
    headline = TipStat("Likely quality", if (o.qualityLow == o.qualityHigh) "${o.qualityLow}" else "${o.qualityLow} to ${o.qualityHigh}").takeIf { o.qualityHigh > 0 },
    stats = listOfNotNull(o.suits.takeIf { it.isNotEmpty() }?.let { TipStat("Suits", it.joinToString(", ")) }),
    sections = listOf(
        // What every forge of it carries, as ability rows; where each comes from and how they combine are the details' own.
        TipSection(null, if (o.certain.isEmpty()) listOf(TipLine("No effect beyond its normal attack", TipTone.PLAIN)) else o.certain.map { it.ability(withSource = false) }),
        TipSection(null, o.needs.map { n -> TipLine("${n.rule} needs ${n.needs.joinToString(" or ")}", TipTone.CONDITION) } + listOfNotNull(o.tradeoff?.let { TipLine(it, o.tradeoffKind.tone()) })),
        // What a roll may add, by name only and never as had: when each can come is in the details.
        TipSection(null, o.possible.takeIf { it.isNotEmpty() }?.let { p -> listOf(TipLine("May also get, never certain: ${p.joinToString(", ") { it.name }}", TipTone.CHANCE)) }.orEmpty()),
    ),
    keywords = keywords(o.certain.flatMap { it.stats }, config),
    actions = actions, tag = tag,
)

/**
 * A weapon as a card, from the same [ItemDetail] the weapon sheet reads (so nothing here can disagree with it): its
 * name in the rarity's colour, rarity and kind in words, its numbers, each buff ("+") and flaw ("−") with what it does,
 * and in a guild run the rules it fights by, those it has only on the town's wall apart. [actions] is usually
 * "Full details", which opens the sheet with its story, price and history.
 */
fun weaponTip(d: com.example.blacksmithproject.ui.detail.ItemDetail, config: BalanceConfig, actions: List<TipAction> = emptyList(), tag: String = "inspect"): TipUi {
    val rules = d.rules
    return TipUi(
        name = d.name, nameColor = com.example.blacksmithproject.ui.detail.rarityColor(d.rarity),
        kind = "${Terms.rarity(d.rarity)} · ${d.kind}", icon = d.sprite,
        meta = d.legend ?: d.title,
        // Power is the weapon's headline number, as an item's damage; the rest are its lines.
        headline = d.stats.firstOrNull { it.label == "Power" }?.let { TipStat(it.label, "${it.value}") },
        stats = d.stats.filter { it.label != "Power" }.map { s -> TipStat(s.label, if (s.word != null) "${s.value} · ${s.word}" else "${s.value}") },
        sections = listOf(
            // Where each block holds (docs/TERMINOLOGY_PROPOSAL.md section 8, item 21): a property's numbers are the power
            // formula's (`Power`), its attack bonus also raises Attack in every guild fight (`Loadout.weaponStrike`), and the
            // effects below are the guild fight's own.
            TipSection(if (properties(d)) "On townsfolk expeditions and sieges" else null, d.affixes.map { TipLine(it.description, TipTone.BONUS, title = it.name) } + d.flaws.map { TipLine(it.description, TipTone.DRAWBACK, title = it.name) } +
                listOfNotNull(
                    TipLine(GUILD_PROPERTIES, TipTone.CONDITION).takeIf { rules != null && properties(d) },
                    d.dormant?.let { TipLine(it, TipTone.CONDITION) },
                )),
            TipSection("In guild fights", rules?.road.orEmpty().map { it.ability() }),
            TipSection("Siege only", rules?.homeOnly.orEmpty().map { it.ability() }),
        ),
        keywords = keywords((rules?.road.orEmpty() + rules?.homeOnly.orEmpty()).flatMap { it.stats }, config),
        note = rules?.pattern?.takeIf { it.isNotBlank() },
        actions = actions, tag = tag,
    )
}

private fun properties(d: com.example.blacksmithproject.ui.detail.ItemDetail) = d.affixes.isNotEmpty() || d.flaws.isNotEmpty()

/** In a guild era, what the property numbers above still decide: who stands on the wall, and the wielder's Attack. */
internal const val GUILD_PROPERTIES = "In a guild era the siege is fought with the effects below. There, a property's defense only decides who is picked for the wall, and its attack bonus raises the wielder's Attack in every guild fight, quests and the siege alike."
