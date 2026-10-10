package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.model.*

/**
 * What earlier answers set in motion (report section 6): the smith's wager, the council's bounty for arming the watch,
 * and the wall pledge, whose second stage is read from the run as it stands when it falls due. Draws nothing.
 */
object Consequences {

    private fun serial(id: WeaponId): Int = id.value.drop(1).toIntOrNull() ?: 0

    /** End Day, after the patrons have collected: wagers are judged, an old bounty lapses, a pledge learns what became of its order. */
    fun settle(ctx: ResolutionContext) {
        for (q in ctx.consequences.toList()) when (q.kind) {
            ConsequenceKind.WAGER -> {
                val made = ctx.weapons.values.filter { serial(it.id) >= (q.amounts["serial"] ?: 0) && it.history.firstOrNull()?.kind == "FORGED" && it.familyId == q.familyId && it.quality >= (q.amounts["quality"] ?: 0) && it.forgedDay <= q.dueDay }
                    .minWithOrNull(compareBy(IdOrder.numeric) { it.id.value })
                val family = q.familyId?.let { ctx.content.family(it).name } ?: "weapon"
                if (made != null) {
                    ctx.consequences -= q
                    // The relic is the prize while the workshop lacks one; a workshop that holds them all is paid in coin.
                    if (Relics.offer(ctx, "You won the wager with ${made.name}. Choose your relic reward.")) Unit
                    else {
                        val payout = (q.amounts["payout"] ?: 0)
                        ctx.gold += payout
                        ctx.emit(EventType.RELIC_OFFERED, 3, "You won the wager with ${made.name} and earned $payout gold.", listOf(made.id.value))
                    }
                } else if (ctx.day >= q.dueDay) {
                    ctx.consequences -= q
                    ctx.emit(EventType.ENCOUNTER_RESOLVED, 3, "You missed the $family wager's deadline. Lost the ${q.amounts["stake"] ?: 0} gold stake.", data = mapOf("encounter" to Depth.SMITHS_WAGER, "option" to "lost"))
                }
            }
            ConsequenceKind.WATCH_BOUNTY -> if (ctx.day >= q.dueDay) ctx.consequences -= q
            ConsequenceKind.WALL_PLEDGE -> if (q.weaponId == null) {
                val order = ctx.commissions[q.commissionId]
                when (order?.status) {
                    CommissionStatus.COMPLETED -> {
                        val blade = order.deliveredWeaponId?.let { ctx.weapons[it] }
                        // Due the morning after the siege the blade was made for: delivered tonight, it is on the wall tonight if the siege is tonight.
                        ctx.consequences[ctx.consequences.indexOf(q)] = q.copy(weaponId = blade?.id, dueDay = ctx.town.nextSiegeDay + 1)
                    }
                    CommissionStatus.ACCEPTED -> Unit
                    else -> {
                        ctx.consequences -= q
                        val hero = ctx.heroes[q.heroId]
                        ctx.emit(EventType.PLEDGE_RESOLVED, 3, "The weapon promised to ${hero?.fullName ?: "a defender"} wasn't delivered. The order expired.", listOfNotNull(q.heroId?.value), mapOf("outcome" to "lapsed"))
                    }
                }
            }
        }
    }

    /**
     * The new morning: a pledge that has fallen due either ends with a record of what really happened (the hero is dead
     * or retired, or no longer owns the blade) or brings the hero back to the forge as this morning's visitor.
     */
    fun dueFollowUp(ctx: ResolutionContext): EncounterInstance? {
        var visitor: EncounterInstance? = null
        for (q in ctx.consequences.filter { it.kind == ConsequenceKind.WALL_PLEDGE && it.weaponId != null && it.dueDay <= ctx.day }) {
            if (visitor != null) break   // one visitor a morning; the next pledge waits a day
            ctx.consequences -= q
            val hero = ctx.heroes[q.heroId]
            val blade = ctx.weapons[q.weaponId]
            val subjects = listOfNotNull(q.heroId?.value, q.weaponId?.value)
            val where = when (val l = blade?.location) {
                null -> "has no surviving record"
                is WeaponLocation.Storage, is WeaponLocation.Shelf -> "is back at the forge"
                is WeaponLocation.Owned -> "is carried by ${ctx.heroes[l.heroId]?.fullName ?: "another"}"
                is WeaponLocation.Lost -> "is gone (${l.reason})"
                is WeaponLocation.Destroyed -> "was destroyed"
            }
            when {
                hero == null || hero.fate == HeroFate.DEAD ->
                    ctx.emit(EventType.PLEDGE_RESOLVED, 5, "${hero?.fullName ?: "The defender"} died before repaying the weapon. ${blade?.name ?: "The weapon"} $where.", subjects, mapOf("outcome" to "dead"))
                hero.fate == HeroFate.RETIRED ->
                    ctx.emit(EventType.PLEDGE_RESOLVED, 4, "${hero.fullName} retired before repaying the weapon. The debt is closed. ${blade?.name ?: "The weapon"} $where.", subjects, mapOf("outcome" to "retired"))
                blade == null || blade.ownerId != hero.id ->
                    ctx.emit(EventType.PLEDGE_RESOLVED, 3, "${hero.fullName} no longer owns the weapon bought on credit. ${blade?.name ?: "It"} $where. The debt is closed.", subjects, mapOf("outcome" to "parted"))
                else -> {
                    // The siege the blade was made for, as its own record tells it: who stood on the wall, and whether it held.
                    val siege = ctx.events.lastOrNull { (it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST) && it.era == ctx.era && it.day == q.dueDay - 1 }
                    visitor = EncounterInstance(
                        Encounters.newId(ctx), Depth.DEBT_REPAID, ctx.day, heroId = hero.id, weaponId = blade.id,
                        amounts = mapOf("owed" to (q.amounts["owed"] ?: 0), "stood" to (if (siege != null && hero.id.value in siege.subjectIds) 1 else 0), "held" to (if (siege?.type == EventType.SIEGE_WON) 1 else 0)),
                    )
                }
            }
        }
        return visitor
    }

    /** A blade given to the watch under the council's bounty: the gold it earns now, and one blade fewer left to pay for. */
    fun bounty(ctx: ResolutionContext): Int {
        val q = ctx.consequences.firstOrNull { it.kind == ConsequenceKind.WATCH_BOUNTY && ctx.day <= it.dueDay } ?: return 0
        val left = (q.amounts["left"] ?: 0) - 1
        if (left <= 0) ctx.consequences -= q else ctx.consequences[ctx.consequences.indexOf(q)] = q.copy(amounts = q.amounts + ("left" to left))
        return q.amounts["gold"] ?: 0
    }
}
