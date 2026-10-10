package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.content.RelicDef
import com.tinyblacksmith.core.content.RelicEffect
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream

/**
 * Run-long workshop relics (report section 4): offered at run start, after chosen sieges and for a won wager; at most
 * `depth.relicSlots` held, each once. A relic changes a rule; its numbers are in `BalanceConfig.depth`.
 */
object Relics {
    const val OFFER_START = "start"

    fun active(ctx: ResolutionContext, effect: RelicEffect): ActiveRelic? = ctx.relics.firstOrNull { ctx.content.relic(it.id)?.effect == effect }
    fun def(ctx: ResolutionContext, effect: RelicEffect): RelicDef? = active(ctx, effect)?.let { ctx.content.relic(it.id) }

    /** A once-a-day relic that has not been used today, or null. */
    fun ready(ctx: ResolutionContext, effect: RelicEffect): ActiveRelic? = active(ctx, effect)?.takeIf { ctx.relicUses[it.id] != ctx.day }

    private fun use(ctx: ResolutionContext, relic: ActiveRelic) { ctx.relicUses[relic.id] = ctx.day }
    private fun update(ctx: ResolutionContext, relic: ActiveRelic) { ctx.relics = ctx.relics.map { if (it.id == relic.id) relic else it } }

    /** A held relic as the planning screens show it: what it does and where it stands today. [ready] is null for a relic without a daily charge. */
    data class View(val id: String, val name: String, val description: String, val status: String, val ready: Boolean?)

    fun views(ctx: ResolutionContext): List<View> = ctx.relics.mapNotNull { r ->
        val def = ctx.content.relic(r.id) ?: return@mapNotNull null
        val used = ctx.relicUses[r.id] == ctx.day
        val cfg = ctx.config.depth
        when (def.effect) {
            RelicEffect.SALVAGE_AUGMENT -> View(r.id, def.name, def.description, if (used) "Used today." else "Ready: the next fine blade you melt down returns its augment.", !used)
            RelicEffect.BELLOWS -> View(r.id, def.name, def.description, if (used) "Used today." else "Ready: one forge today can take ${cfg.bellowsDebt} energy from tomorrow for an extra property.", !used)
            RelicEffect.PREMIUM_SEAL -> View(r.id, def.name, def.description, "Seals: ${r.progress} of ${cfg.sealsPerReward}." + (if (used) " Today's seal is earned." else ""), !used)
            RelicEffect.FAMILY_STREAK -> View(r.id, def.name, def.description,
                if (r.families.isEmpty()) "No streak yet: the next blade starts one."
                else "Streak: ${r.families.joinToString(", ") { ctx.content.family(it).name }}. A family not in it is forged +${minOf(cfg.ledgerMaxBonus, r.families.size * cfg.ledgerQualityPerStep)} quality better.", null)
        }
    }

    fun unowned(ctx: ResolutionContext): List<RelicDef> = ctx.content.relics.filter { d -> ctx.relics.none { it.id == d.id } }

    /** The offers a run is due by where it stands: the opening draft, then one after each siege numbered in `relicOfferSieges`. One at a time. */
    fun offerIfDue(ctx: ResolutionContext) {
        if (ctx.phase == Phase.ENDED || ctx.pendingRelicOffer.isNotEmpty()) return
        val fought = ctx.town.siegesSurvived + ctx.town.siegesLost
        val key = (listOf(OFFER_START) + ctx.config.depth.relicOfferSieges.filter { it <= fought }.map { "siege$it" }).firstOrNull { it !in ctx.relicOffersMade } ?: return
        ctx.relicOffersMade += key
        offer(ctx, if (key == OFFER_START) "A workshop is known by its tools: the smith may take one relic to start with." else "After the siege, the smith may take a relic for the workshop.")
    }

    /** Offers up to `relicOfferSize` relics the workshop does not hold. False when it holds them all or an offer is already waiting. */
    fun offer(ctx: ResolutionContext, text: String): Boolean {
        if (ctx.pendingRelicOffer.isNotEmpty()) return false
        val pool = unowned(ctx).map { it.id }.toMutableList()
        if (pool.isEmpty()) return false
        val rng = ctx.rng(RngStream.ENCOUNTERS)
        val offer = mutableListOf<String>()
        repeat(minOf(ctx.config.depth.relicOfferSize, pool.size)) { val r = rng.pick(pool); offer += r; pool.remove(r) }
        ctx.pendingRelicOffer = offer
        ctx.emit(EventType.RELIC_OFFERED, 3, text, data = mapOf("offer" to offer.joinToString(",")))
        return true
    }

    fun choose(ctx: ResolutionContext, cmd: Command.ChooseRelic): GameError? {
        if (ctx.pendingRelicOffer.isEmpty()) return GameError.NoRelicOffer
        if (cmd.relicId !in ctx.pendingRelicOffer) return GameError.RelicNotOffered(cmd.relicId)
        val def = ctx.content.relic(cmd.relicId) ?: return GameError.UnknownContent(cmd.relicId)
        val full = ctx.relics.size >= ctx.config.depth.relicSlots
        if (cmd.replaceId != null && ctx.relics.none { it.id == cmd.replaceId }) return GameError.RelicNotOwned(cmd.replaceId)
        if (full && cmd.replaceId == null) return GameError.RelicSlotsFull
        // The relic that leaves takes its seals or its streak with it; a bellows debt already taken stays in today's overwork.
        val replaced = cmd.replaceId?.let { ctx.content.relic(it) }
        ctx.relics = ctx.relics.filter { it.id != cmd.replaceId } + ActiveRelic(def.id)
        ctx.pendingRelicOffer = emptyList()
        ctx.emit(EventType.RELIC_CHOSEN, 4, "The workshop gained a relic: ${def.name}." + (replaced?.let { " ${it.name} was put away." } ?: ""), data = mapOf("relic" to def.id) + (replaced?.let { mapOf("replaced" to it.id) } ?: emptyMap()))
        return null
    }

    // ---- Tempering Ledger -----------------------------------------------------------------------------------

    /** Quality the ledger adds to a forge of [family] now: nothing when the family is already in the streak. */
    fun ledgerBonus(ctx: ResolutionContext, family: WeaponFamilyId): Int {
        val ledger = active(ctx, RelicEffect.FAMILY_STREAK) ?: return 0
        if (family in ledger.families) return 0
        return minOf(ctx.config.depth.ledgerMaxBonus, ledger.families.size * ctx.config.depth.ledgerQualityPerStep)
    }

    fun noteForge(ctx: ResolutionContext, family: WeaponFamilyId) {
        val ledger = active(ctx, RelicEffect.FAMILY_STREAK) ?: return
        update(ctx, ledger.copy(families = if (family in ledger.families) listOf(family) else ledger.families + family))
    }

    // ---- Ashen Bellows --------------------------------------------------------------------------------------

    /** Why a forge cannot be blown with the bellows now, or null. [overworkNeeded] is what the forge itself already takes from tomorrow. */
    fun bellowsError(ctx: ResolutionContext, energyCost: Int, overworkNeeded: Int): GameError? {
        val bellows = active(ctx, RelicEffect.BELLOWS) ?: return GameError.RelicNotOwned(Depth.ASHEN_BELLOWS)
        if (ctx.relicUses[bellows.id] == ctx.day) return GameError.RelicSpent(bellows.id)
        val debt = ctx.config.depth.bellowsDebt
        val available = ctx.config.maxOverworkPerDay - ctx.overworkToday
        return if (overworkNeeded + debt > available) GameError.NotEnoughEnergy(energyCost + debt, ctx.energy, available) else null
    }

    /** Takes the bellows' debt from tomorrow, through the same overwork the smith's own late hours use, and returns the affix slots gained. */
    fun blow(ctx: ResolutionContext): Int {
        val bellows = active(ctx, RelicEffect.BELLOWS) ?: return 0
        use(ctx, bellows)
        ctx.overworkToday += ctx.config.depth.bellowsDebt
        return ctx.config.depth.bellowsExtraSlots
    }

    // ---- Salvager's Crucible --------------------------------------------------------------------------------

    /** Whether melting [weapon] down now would also give its augment back. */
    fun crucibleKeeps(ctx: ResolutionContext, weapon: Weapon): Boolean = ready(ctx, RelicEffect.SALVAGE_AUGMENT) != null && weapon.quality >= ctx.config.rareMin

    /** Called by a salvage: returns the augment once a day for a blade of fine quality or better. */
    fun onSalvage(ctx: ResolutionContext, weapon: Weapon): Boolean {
        if (!crucibleKeeps(ctx, weapon)) return false
        use(ctx, ready(ctx, RelicEffect.SALVAGE_AUGMENT)!!)
        ctx.materials[weapon.augmentId] = (ctx.materials[weapon.augmentId] ?: 0) + 1
        return true
    }

    // ---- Collector's Seal -----------------------------------------------------------------------------------

    /**
     * Called for a shelf sale to a hero. The first one of a day that is priced at the going rate or above and for which
     * the hero paid at least `sealMinCash` in coin (trade-in and stipend do not count) earns a seal.
     */
    fun onShelfSale(ctx: ResolutionContext, weapon: Weapon, price: Int, cashPaid: Int) {
        val cfg = ctx.config.depth
        val seal = ready(ctx, RelicEffect.PREMIUM_SEAL) ?: return
        if (cashPaid < cfg.sealMinCash || price < Market.askingPrice(weapon, ctx.config)) return
        use(ctx, seal)
        if (seal.progress + 1 < cfg.sealsPerReward) {
            update(ctx, seal.copy(progress = seal.progress + 1))
            ctx.emit(EventType.RELIC_TRIGGERED, 2, "The sale of ${weapon.name} earned a collector's seal (${seal.progress + 1} of ${cfg.sealsPerReward}).", listOf(weapon.id.value), mapOf("relic" to seal.id))
            return
        }
        update(ctx, seal.copy(progress = 0))
        // The limited material the forge holds least of; ties by ID. Never a catalyst: those have their own sources.
        val reward = ctx.content.materials.filter { it.dailySupplierStock != null && it.category != MaterialCategory.CATALYST && it.tier >= cfg.sealMaterialTier }
            .minWithOrNull(compareBy({ ctx.materials[it.id] ?: 0 }, { it.id.value })) ?: return
        ctx.materials[reward.id] = (ctx.materials[reward.id] ?: 0) + 1
        ctx.emit(EventType.RELIC_TRIGGERED, 3, "Three collector's seals brought the forge 1 ${reward.name}.", listOf(weapon.id.value), mapOf("relic" to seal.id, "material" to reward.id.value))
    }
}
