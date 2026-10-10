package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.content.CombatCatalog
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.MemberStatus
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId

/** One rule a blade follows in a fight: the definition's own [name] and [description], and the part of the blade it comes from ([source]). */
@Immutable data class BladeRuleUi(val id: String, val name: String, val description: String, val source: String)

/**
 * What a blade does in a fight (spec 7.1): how its family delivers a strike ([pattern]), the rules it follows anywhere
 * ([road]) and those it has only on the town's wall ([homeOnly]). Read from the combat catalog; nothing is resolved.
 */
@Immutable data class BladeRulesUi(val pattern: String?, val road: List<BladeRuleUi>, val homeOnly: List<BladeRuleUi>)

/** Where on [weapon] the rule [id] comes from, asked in the order `Loadout.weaponEffects` lists them. */
private fun source(weapon: Weapon, combat: CombatCatalog, id: String): String {
    fun List<EffectDef>?.has() = orEmpty().any { it.id == id }
    fun affix(a: com.tinyblacksmith.core.model.AffixId) = combat.affixEffects[a].has() || combat.affixRoadEffects[a].has() || combat.affixHomeEffects[a].has()
    return when {
        combat.familyById[weapon.familyId]?.effects.has() -> "weapon"
        weapon.element?.let { combat.elementEffects[it] }.has() -> "element"
        weapon.affixes.any(::affix) -> "buff"
        weapon.flaws.any(::affix) -> "flaw"
        weapon.catalystId?.let { combat.catalystEffects[it] }.has() -> "catalyst"
        else -> "signature"
    }
}

/** The rules of [weapon] as `Loadout.weaponEffects` gives them, on the road and at home. */
fun bladeRules(weapon: Weapon, combat: CombatCatalog): BladeRulesUi {
    fun rule(e: EffectDef) = BladeRuleUi(e.id, e.name, e.description, source(weapon, combat, e.id))
    val road = Loadout.weaponEffects(weapon, combat, Loadout.Field.ROAD)
    val home = Loadout.weaponEffects(weapon, combat, Loadout.Field.HOME)
    return BladeRulesUi(combat.familyById[weapon.familyId]?.pattern, road.map(::rule), home.filter { h -> road.none { it.id == h.id } }.map(::rule))
}

/** The "In a fight" section of [weapon]'s card: only in a guild run, where blades are carried into fights the smith sees. */
fun GameEngine.bladeRules(state: GameState, weapon: Weapon): BladeRulesUi? = content.combat?.takeIf { state.guild != null }?.let { bladeRules(weapon, it) }

/**
 * What the draft's augment and catalyst will give the blade in a fight, before it is forged: the element's rule and the
 * catalyst's rules, each under the material's name. Empty in a classic run.
 */
fun draftRules(state: GameState, content: ContentCatalog, augmentId: MaterialId?, catalystId: MaterialId?): List<BladeRuleUi> {
    if (state.guild == null) return emptyList()
    return listOfNotNull(augmentId, catalystId).flatMap { id -> materialRules(content, id).map { BladeRuleUi(it.id, it.name, it.description, content.material(id).name) } }
}

/** The fight rules a material puts on a blade: an augment's element rule, a catalyst's rules; none for a metal. */
fun materialRules(content: ContentCatalog, id: MaterialId): List<EffectDef> {
    val combat = content.combat ?: return emptyList()
    return content.materialById[id]?.element?.let { combat.elementEffects[it] }.orEmpty() + combat.catalystEffects[id].orEmpty()
}

/** A guild member a blade could be lent to. [reason] is why they are not at their best or not here ("Mira is away with the party"); only [enabled] ones can take a loan. */
@Immutable data class LoanTargetUi(val heroId: HeroId, val name: String, val role: String, val carrying: String, val reason: String?, val enabled: Boolean)

/**
 * A blade's standing with the guild. On loan: [holder] has it, and [recallBlocked] says why it cannot be called back
 * today (null: it can). In the shop: [targets] are the members it could go to, and [blocked] why it cannot go at all.
 */
@Immutable data class LoanUi(val holder: String?, val holderId: HeroId?, val recallBlocked: String?, val targets: List<LoanTargetUi>, val blocked: String? = null)

/** A blade out on loan and who holds it, for Storage's "On loan" group. */
@Immutable data class LoanRowUi(val weaponId: WeaponId, val blade: String, val holder: String)

/** The members of the guild as places a blade could go. The engine lends only to a member in town; a wound does not stop a loan. */
fun GameEngine.loanTargets(state: GameState): List<LoanTargetUi> = state.guild?.members.orEmpty().mapNotNull { m ->
    val hero = state.heroes[m.heroId] ?: return@mapNotNull null
    val held = state.loanOf(m.heroId)
    val own = state.equippedWeapon(m.heroId)
    LoanTargetUi(
        m.heroId, hero.fullName, content.classById[hero.classId]?.name ?: hero.classId.value,
        carrying = when { held != null -> "Carries ${held.name}, on loan"; own != null -> "Carries ${own.name}, their own"; else -> "Carries nothing" },
        reason = memberUnavailable(state, m.heroId)?.let { "${hero.name} $it" },
        enabled = hero.isAlive && m.status == MemberStatus.HOME,
    )
}

/** Null in a classic run and for a blade the guild can neither lend nor call back (sold, lost, destroyed). */
fun GameEngine.loanUi(state: GameState, weapon: Weapon): LoanUi? {
    val guild = state.guild ?: return null
    weapon.loanedTo?.let { id ->
        val hero = state.heroes[id]
        val name = hero?.fullName ?: "a member of the guild"
        // The engine takes a loan back from anyone in town, wounded or not; only a holder who is not here keeps it.
        val away = guild.member(id)?.takeIf { it.status != MemberStatus.HOME }?.let { memberUnavailable(state, id) }
        return LoanUi(name, hero?.id, away?.let { "${hero?.name ?: "Its holder"} $it" }, emptyList())
    }
    if (!weapon.isInStorage && !weapon.isListed) return null
    return LoanUi(null, null, null, loanTargets(state), blocked = promisedLine(state, weapon)?.let { "$it: it stays in storage" })
}

/** Every blade out on loan, in the save's order. */
fun GameState.loanRows(): List<LoanRowUi> = if (guild == null) emptyList() else weapons.values.filter { it.isLoaned }.map { w ->
    LoanRowUi(w.id, w.name, heroes[w.loanedTo]?.fullName ?: "a member of the guild")
}
