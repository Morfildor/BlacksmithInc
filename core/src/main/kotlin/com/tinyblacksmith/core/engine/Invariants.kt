package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.MemberStatus
import com.tinyblacksmith.core.model.Phase
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.rng.RngStream

/** GDD 15.1 invariants, asserted after every accepted command. */
object Invariants {
    /**
     * Never throws; an empty list means sound. With a [content] catalog every content ID the run stores is also
     * resolved against it (`Compatibility.admit` passes one, so a save from other content is refused, not crashed on).
     */
    fun check(state: GameState, config: BalanceConfig, shelfSlots: Int = config.shelfSlots, content: ContentCatalog? = null): List<String> {
        val problems = mutableListOf<String>()
        if (content != null) problems += unknownContent(state, content)
        RngStream.entries.forEach { if (it !in state.rng.streams) problems += "Missing RNG stream $it" }
        // A run ends when the forge falls, or, in a guild run, when the smith closes the chapter with a milestone earned.
        val retired = state.guild?.retired == true
        if (retired && state.guild?.milestones.isNullOrEmpty()) problems += "A run was closed without a milestone"
        if ((state.phase == Phase.ENDED) != (state.town.integrity == 0 || retired)) problems += "Phase ${state.phase} with forge integrity ${state.town.integrity}"
        if (state.gold < 0) problems += "Negative gold ${state.gold}"
        if (state.energy < 0) problems += "Negative energy ${state.energy}"
        if (state.overworkToday < 0 || state.overworkToday > config.maxOverworkPerDay) problems += "Overwork out of bounds ${state.overworkToday}"
        state.materials.forEach { (m, n) -> if (n < 0) problems += "Negative material ${m.value}" }
        if (state.town.integrity < 0) problems += "Negative integrity"
        if (state.town.armory !in 0..config.armoryMax) problems += "Armory out of bounds ${state.town.armory}"
        if (state.listedWeapons().size > shelfSlots) problems += "Shelf overfull"
        state.weapons.values.forEach { w ->
            val loc = w.location
            if (loc is WeaponLocation.Owned) {
                val owner = state.heroes[loc.heroId]
                if (owner == null) problems += "Weapon ${w.id.value} owned by unknown hero"
                else if (!owner.isAlive) problems += "Weapon ${w.id.value} owned by ${owner.fate.name.lowercase()} hero ${owner.fullName}"
            }
            if (loc is WeaponLocation.Shelf && loc.price < 0) problems += "Weapon ${w.id.value} has negative price"
            if (loc is WeaponLocation.Lost && w.isWithMerchant) {
                // GDD 7 merchant resale is bounded: sold or carried off by the last End Day of the merchant's stay.
                val leaves = loc.day + config.weaponFates.merchantDelayDays + maxOf(1, config.weaponFates.merchantStayDays)
                if (state.day > leaves) problems += "Weapon ${w.id.value} still held by a merchant after day $leaves"
            }
            if (w.condition !in 0..100) problems += "Weapon ${w.id.value} condition out of bounds ${w.condition}"
            if (w.quality !in 1..100) problems += "Weapon ${w.id.value} quality out of bounds ${w.quality}"
            if (w.power < 0 || w.kills < 0 || w.victories < 0 || w.siegesDefended < 0 || w.fame < 0) problems += "Weapon ${w.id.value} has a negative power or record"
        }
        state.weapons.values.filter { it.isEquipped }.groupingBy { it.ownerId!! }.eachCount().forEach { (heroId, equipped) ->
            if (equipped > 1) problems += "Hero ${state.heroes[heroId]?.fullName ?: heroId.value} has $equipped equipped weapons"
        }
        state.heroes.values.forEach { h ->
            if (h.gold < 0) problems += "Hero ${h.fullName} has negative gold"
            if (h.health > 100 || h.health < 0 || (h.isAlive && h.health <= config.heroDeathHealthFloor)) problems += "Hero ${h.fullName} health out of bounds ${h.health}"
            if (h.level !in 1..config.heroMaxLevel) problems += "Hero ${h.fullName} level out of bounds ${h.level}"
            if (h.xp < 0 || h.loyalty < 0 || h.fame < 0 || h.kills < 0 || h.victories < 0) problems += "Hero ${h.fullName} has a negative record"
        }
        if (state.town.championIds.size > config.championCount) problems += "More than ${config.championCount} champions"
        if (state.town.championIds.toSet().size != state.town.championIds.size) problems += "A champion is listed twice"
        state.town.championIds.forEach { id ->
            val h = state.heroes[id]
            if (h == null || !h.isAlive) problems += "Dead or missing champion ${id.value}"
        }
        state.factions.values.forEach { f -> if (f.pressure !in 0..100) problems += "Faction pressure out of range" }
        state.town.guilds.forEach { g -> if (g.founderId !in state.heroes) problems += "Guild ${g.id} founded by unknown hero" }
        state.eventCounters.forEach { (id, n) ->
            if (id.startsWith(Encounters.COUNTER_PREFIX)) return@forEach   // a morning visitor without a world event behind it
            if (id == WorldEvents.RUMOUR) return@forEach   // not a pooled event: the run's rumours, capped by `customers.maxRumoursPerRun` where they are told
            val def = WorldEvents.all.firstOrNull { it.id == id }
            if (def == null) problems += "Unknown world event counter $id" else if (n > def.maxPerRun) problems += "World event $id fired $n times (max ${def.maxPerRun})"
        }
        if (state.relics.size > config.depth.relicSlots) problems += "More than ${config.depth.relicSlots} relics"
        if (state.relics.map { it.id }.toSet().size != state.relics.size) problems += "A relic is held twice"
        state.weapons.values.forEach { w ->
            val order = w.promisedTo?.let { state.commissions[it] } ?: return@forEach
            if (!w.isInStorage && !w.isListed) problems += "Weapon ${w.id.value} is promised to an order and not in the shop"
            if (order.status != com.tinyblacksmith.core.model.CommissionStatus.ACCEPTED) problems += "Weapon ${w.id.value} is kept for a closed order"
        }
        state.weapons.values.forEach { w -> if (w.promisedTo != null && w.promisedTo !in state.commissions) problems += "Weapon ${w.id.value} is promised to an unknown order" }
        problems += guild(state, config)
        return problems
    }

    /** The guild's own books (plan D2): one blade, one place, one custodian; nobody listed anywhere who is not there. */
    private fun guild(state: GameState, config: BalanceConfig): List<String> {
        val problems = mutableListOf<String>()
        val g = state.guild
        val loans = state.weapons.values.filter { it.isLoaned }
        if (g == null) {
            if (loans.isNotEmpty()) problems += "A run without a guild has ${loans.size} weapons on loan"
            return problems
        }
        val cfg = config.guild
        if (g.members.size > cfg.maxMembers) problems += "More than ${cfg.maxMembers} guild members"
        if (g.members.map { it.heroId }.toSet().size != g.members.size) problems += "A guild member is listed twice"
        for (m in g.members) {
            val h = state.heroes[m.heroId]
            if (h == null || !h.isAlive) problems += "Guild member ${m.heroId.value} is dead or missing"
            if (m.bonds.any { !g.isMember(it.withHeroId) }) problems += "Guild member ${m.heroId.value} is bonded to somebody outside the guild"
            if ((m.status == MemberStatus.CAPTURED) != g.captives.any { it.heroId == m.heroId }) problems += "Guild member ${m.heroId.value}: status ${m.status} and the captives' list disagree"
        }
        for (w in loans) {
            val holder = g.member(w.loanedTo!!)
            if (holder == null) problems += "Weapon ${w.id.value} is on loan to ${w.loanedTo!!.value}, who is not a member"
            if (w.promisedTo != null) problems += "Weapon ${w.id.value} is on loan and promised to an order"
        }
        loans.groupingBy { it.loanedTo!! }.eachCount().forEach { (id, n) -> if (n > 1) problems += "Member ${id.value} holds $n loans" }
        if (g.reserved.size > cfg.defenders || g.reserved.toSet().size != g.reserved.size) problems += "The wall's reservations are out of bounds"
        g.reserved.forEach { if (!g.isMember(it)) problems += "${it.value} is reserved for the wall and is not a member" }
        g.candidates.forEach { c -> if (g.isMember(c.heroId) || state.heroes[c.heroId]?.isAlive != true) problems += "Candidate ${c.heroId.value} cannot be asked" }
        g.planned?.let { p ->
            if (p.heroIds.isEmpty() || p.heroIds.size > cfg.partyMax) problems += "The planned party has ${p.heroIds.size} members"
            p.heroIds.forEach { if (g.member(it)?.status != MemberStatus.HOME) problems += "${it.value} is planned for a party and is not in town" }
            if (g.offers.none { it.id == p.offerId }) problems += "A party is planned for a contract that is not on the board"
            if (g.mission != null) problems += "A party is planned while another is out"
        }
        val away = g.members.filter { it.status == MemberStatus.AWAY }.map { it.heroId }.toSet()
        val out = g.mission?.party.orEmpty().filter { g.isMember(it) && g.member(it)?.status != MemberStatus.CAPTURED }.toSet() + listOfNotNull(g.mission?.offer?.subjectHeroId?.takeIf { g.member(it)?.status == MemberStatus.AWAY })
        if (away != out.filter { g.member(it)?.status == MemberStatus.AWAY }.toSet() || !out.all { it in away }) problems += "Members away (${away.map { it.value }}) are not the party that is out (${out.map { it.value }})"
        g.mission?.let { m -> if (m.stage > m.offer.stages.size || m.party.isEmpty()) problems += "The party's contract is out of bounds" }
        g.captives.forEach { c -> if (state.heroes[c.heroId]?.isAlive != true) problems += "Captive ${c.heroId.value} is dead or missing" }
        g.nemesis?.let { n -> if ((state.weapons[n.weaponId]?.location as? WeaponLocation.Lost) == null) problems += "The nemesis carries ${n.weaponId.value}, which is not lost" }
        if (g.offers.map { it.id }.toSet().size != g.offers.size) problems += "A contract is on the board twice"
        if (g.milestones.map { it.id }.toSet().size != g.milestones.size) problems += "A milestone is recorded twice"
        return problems
    }

    /** Every content ID a run stores: family, material, affix, class, trait, blessing, tool, upgrade, faction. */
    private fun unknownContent(state: GameState, content: ContentCatalog): List<String> {
        val problems = mutableListOf<String>()
        fun need(known: Boolean, kind: String, id: String, where: String) { if (!known) problems += "Unknown $kind $id ($where)" }
        (state.materials.keys + state.supplierStock.keys).forEach { need(it in content.materialById, "material", it.value, "stock") }
        state.weapons.values.forEach { w ->
            val where = "weapon ${w.id.value}"
            need(w.familyId in content.familyById, "family", w.familyId.value, where)
            listOfNotNull(w.coreId, w.augmentId, w.catalystId).forEach { need(it in content.materialById, "material", it.value, where) }
            (w.affixes + w.flaws + w.dormantAffixes).forEach { need(it in content.affixById, "affix", it.value, where) }
        }
        state.heroes.values.forEach { h ->
            need(h.classId in content.classById, "class", h.classId.value, "hero ${h.id.value}")
            h.traits.forEach { need(it in content.traitById, "trait", it.value, "hero ${h.id.value}") }
        }
        state.commissions.values.forEach { need(it.familyId in content.familyById, "family", it.familyId.value, "commission ${it.id.value}") }
        (state.blessings.map { it.id } + state.pendingBlessingOffer).forEach { need(it in content.blessingById, "blessing", it.value, "blessings") }
        state.tools.keys.forEach { need(content.tool(it) != null, "tool", it, "tools") }
        state.legacy.upgrades.keys.forEach { need(it in content.upgradeById, "upgrade", it.value, "legacy") }
        state.factions.keys.forEach { need(it in content.factionById, "faction", it.value, "factions") }
        // A catalog without the depth content (the slice, a rules-3 comparison) simply ignores what a run stores of it.
        if (content.relics.isNotEmpty()) (state.relics.map { it.id } + state.pendingRelicOffer).forEach { need(content.relic(it) != null, "relic", it, "relics") }
        if (content.encounters.isNotEmpty()) state.encounter?.let { need(content.encounter(it.defId) != null, "encounter", it.defId, "visitor") }
        if (content.siegeTraits.isNotEmpty()) state.siege?.traitId?.let { need(content.siegeTrait(it) != null, "siege trait", it, "siege") }
        return problems
    }
}
