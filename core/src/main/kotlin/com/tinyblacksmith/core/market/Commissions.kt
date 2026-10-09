package com.tinyblacksmith.core.market

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.IdOrder
import com.tinyblacksmith.core.model.Weapon

/**
 * The five quality words and the quality each starts at: the rarity thresholds of [BalanceConfig], defined once.
 * A commission always asks for a band floor, so the word on a request and the number the rule checks cannot disagree.
 */
enum class QualityBand(val word: String) {
    CRUDE("crude"), DECENT("decent"), FINE("fine"), SUPERB("superb"), MASTERWORK("masterwork");

    fun floor(config: BalanceConfig): Int = when (this) {
        CRUDE -> 0
        DECENT -> config.uncommonMin
        FINE -> config.rareMin
        SUPERB -> config.epicMin
        MASTERWORK -> config.legendaryMin
    }

    companion object {
        fun of(quality: Int, config: BalanceConfig): QualityBand = entries.lastOrNull { quality >= it.floor(config) } ?: CRUDE
    }
}

/** The one rule for what closes a commission (GDD 5), used by End Day and by every screen that shows a request. */
object Commissions {
    /** [OK], or the first thing the blade lacks: the family, then the element asked for, then the quality. */
    enum class Fit { OK, FAMILY, ELEMENT, QUALITY }

    fun fit(weapon: Weapon, commission: Commission): Fit = when {
        weapon.familyId != commission.familyId -> Fit.FAMILY
        commission.element != null && weapon.element != commission.element -> Fit.ELEMENT
        weapon.quality < commission.minQuality -> Fit.QUALITY
        else -> Fit.OK
    }

    /**
     * The blade End Day hands over: of those in the shop that fit, one from storage before one priced on the shelf, then
     * the least sufficient (lowest quality, then lowest going rate, then ID), so the patron never takes the smith's best work.
     */
    fun pick(weapons: Collection<Weapon>, commission: Commission, config: BalanceConfig): Weapon? =
        weapons.filter { (it.isInStorage || it.isListed) && fit(it, commission) == Fit.OK }
            .minWithOrNull(compareBy<Weapon> { it.isListed }.thenBy { it.quality }.thenBy { Market.askingPrice(it, config) }.thenBy(IdOrder.numeric) { it.id.value })

    /** What a request asks for, in the terms the rule checks: "fine frost Spear (quality 50+)". */
    fun describe(commission: Commission, content: ContentCatalog, config: BalanceConfig): String =
        "${QualityBand.of(commission.minQuality, config).word} ${commission.element?.let { it.name.lowercase() + " " }.orEmpty()}${content.family(commission.familyId).name} (quality ${commission.minQuality}+)"
}
