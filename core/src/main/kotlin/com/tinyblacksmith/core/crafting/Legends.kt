package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.LegendOrigin
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation

/**
 * How a weapon becomes Legendary under rules 6 (docs/RUN_VARIETY_LEDGER.md, D1 to D4). Quality alone stops at Epic.
 * A Heartfire forging can break through (`Forge`), and a weapon's own recorded deeds can carry it there (here).
 * One ladder: a Legendary weapon is `Rarity.LEGENDARY` whichever way it came, and `Weapon.legend` says which.
 */
object Legends {
    const val HISTORY_DEED = "DEED"
    const val HISTORY_LEGEND = "LEGEND"

    /** Heartfire comes only from dangerous work. [why] is the sentence of the record that earned it. */
    fun gainHeartfire(ctx: ResolutionContext, amount: Int, why: String) {
        val got = minOf(amount, ctx.config.depth.legend.heartfireMax - ctx.heartfire).coerceAtLeast(0)
        ctx.heartfire += got
        ctx.emit(EventType.HEARTFIRE_GAINED, 6, if (got > 0) "$why The forge now holds ${ctx.heartfire} Heartfire." else "$why The forge already holds all the Heartfire it can keep.",
            data = mapOf("gained" to got.toString(), "held" to ctx.heartfire.toString()))
    }

    /**
     * A deed of [weaponId], from a real record: [renown] is added and written into its history. At
     * `renownForLegend` the weapon becomes Legendary by its deeds, once: its power rises by `deedPower`, its quality,
     * properties and name stay what they were. A weapon that is already Legendary only adds the line to its story.
     */
    fun deed(ctx: ResolutionContext, weaponId: WeaponId, renown: Int, text: String, subjects: List<String> = emptyList()) {
        val w = ctx.weapons[weaponId] ?: return
        if (w.location is WeaponLocation.Destroyed || renown <= 0) return
        val cfg = ctx.config.depth.legend
        val total = w.renown + renown
        ctx.updateWeapon(w.copy(renown = total))
        ctx.addWeaponHistory(weaponId, HISTORY_DEED, text, subjects)
        if (w.rarity == Rarity.LEGENDARY || total < cfg.renownForLegend) {
            val earned = if (renown == 1) "a deed" else "$renown deeds"
            val toward = if (w.rarity == Rarity.LEGENDARY) "" else " ($total of ${cfg.renownForLegend} toward Legendary)"
            ctx.emit(EventType.WEAPON_DEED, 5, "${w.name} earned $earned$toward. $text", listOf(weaponId.value) + subjects, mapOf("renown" to total.toString(), "needed" to cfg.renownForLegend.toString()))
            return
        }
        ctx.updateWeapon(ctx.weapon(weaponId).copy(rarity = Rarity.LEGENDARY, legend = LegendOrigin.DEEDS, power = w.power + cfg.deedPower))
        ctx.addWeaponHistory(weaponId, HISTORY_LEGEND, "Became Legendary through its deeds.", subjects)
        ctx.emit(EventType.LEGEND_EARNED, 9, "${w.name} is now Legendary. $text", listOf(weaponId.value) + subjects, mapOf("origin" to LegendOrigin.DEEDS.name, "from" to w.rarity.name))
        ctx.milestone("LEGEND_EARNED", "A weapon from this forge became Legendary through its deeds: ${w.name}.")
    }
}
