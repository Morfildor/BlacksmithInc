package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.ForgeDraft
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/** UI-only purpose with recorded requirements, copied onto the result independently of subsequent draft edits. */
data class ForgePurpose(val commissionId: CommissionId? = null, val commission: Commission? = null, val heroId: HeroId? = null, val want: Want? = null) {
    fun encode(): ArrayList<String?> = arrayListOf(commissionId?.value, commission?.let { Json.encodeToString(it) }, heroId?.value, want?.let { Json.encodeToString(it) })
    companion object {
        fun decode(values: List<String?>): ForgePurpose? = runCatching {
            ForgePurpose(values[0]?.let(::CommissionId), values[1]?.let { Json.decodeFromString<Commission>(it) }, values[2]?.let(::HeroId), values[3]?.let { Json.decodeFromString<Want>(it) })
        }.getOrNull()
    }
}

fun ForgeDraft.purpose(state: GameState): ForgePurpose? = when {
    commissionId != null -> ForgePurpose(commissionId, state.commissions[commissionId])
    wantHeroId != null -> ForgePurpose(heroId = wantHeroId, want = state.heroes[wantHeroId]?.want)
    else -> null
}

@Immutable
data class PurposeAssessmentUi(val line: String, val details: String, val fits: Boolean? = null)

/** Read-only projections through the commission and market rules. Listing here is a hypothetical copy, never a command. */
fun GameEngine.assessPurpose(state: GameState, weaponId: WeaponId, purpose: ForgePurpose?): PurposeAssessmentUi? {
    purpose ?: return null
    val weapon = state.weapons[weaponId] ?: return null
    fun gone(line: String) = PurposeAssessmentUi(line, "Your craft is complete. Choose what to do with it below.")
    purpose.commissionId?.let { id ->
        val current = state.commissions[id]
        val recorded = purpose.commission ?: return gone("This commission is no longer available.")
        val buyer = state.heroes[recorded.buyerId]
        val name = buyer?.fullName ?: "Customer"
        when {
            current == null -> return gone("$name's commission is no longer available.")
            current.status == CommissionStatus.COMPLETED -> return gone("$name's commission has already been completed.")
            current.status == CommissionStatus.EXPIRED || current.deadlineDay < state.day -> return gone("$name's commission has expired.")
            current.status == CommissionStatus.DECLINED -> return gone("$name's commission was declined.")
            buyer == null || !buyer.isAlive -> return gone("$name is no longer available to collect this commission.")
        }
        val fits = Commissions.fit(weapon, recorded) == Commissions.Fit.OK
        val reason = if (fits) "Meets the commission" else Labels.fit(weapon, recorded, content).replaceFirstChar { it.uppercase() }
        return PurposeAssessmentUi("For $name: $reason.", "Recorded request: ${Labels.request(recorded, content, config)}. " +
            (forgedFor(state, weaponId, id)?.let { "${it.handover} ${it.reserved}" } ?: "Review the commission in the Shop."), fits)
    }
    val hero = state.heroes[purpose.heroId] ?: return gone("This customer is no longer in town.")
    if (!hero.isAlive) return gone("${hero.fullName} is no longer available.")
    val want = purpose.want
    if (want == null || hero.want != want) return gone("${hero.fullName} is no longer waiting for this want.")
    val price = weapon.listedPrice ?: suggestedPrice(weapon)
    val listed = weapon.copy(location = WeaponLocation.Shelf(price))
    val ctx = ResolutionContext(state, content, config)
    val evaluation = Market.evaluate(ctx, hero, ctx.equippedWeapon(hero.id), listed, 0.5)
    val matches = Market.answersWant(ctx, hero, listed)
    val reason = when {
        weapon.familyId != want.familyId -> "Needs a ${content.family(want.familyId).name.lowercase()}"
        weapon.power < want.minPower -> "Power ${weapon.power}, wanted ${want.minPower}+"
        !evaluation.affordable && !evaluation.offered -> "Cannot afford $price gold"
        matches && evaluation.offered -> "Possible match with a lower offer. Sale not guaranteed"
        matches -> "Possible match at $price gold. Sale not guaranteed"
        else -> "May pass on this weapon at $price gold"
    }
    return PurposeAssessmentUi("For ${hero.fullName}: $reason.",
        "Wanted ${content.family(want.familyId).name}, power ${want.minPower}+, last budget ${want.budget} gold. " +
            "At ${if (weapon.isListed) "the listed" else "the suggested"} price: " +
            (if (evaluation.affordable) "affordable today" else if (evaluation.offered) "may make a lower offer" else "beyond today's funds") +
            ". The market also weighs their equipment and preferences. They must visit and choose it. No sale is promised. Not an order. Nothing reserved.",
        matches && weapon.power >= want.minPower)
}
