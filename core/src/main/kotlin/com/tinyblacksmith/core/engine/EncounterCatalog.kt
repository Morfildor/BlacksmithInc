package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.EncounterDef
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.engine.Encounters.Opt
import com.tinyblacksmith.core.engine.Encounters.PASS
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.market.QualityBand
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng

/**
 * What each encounter is: who makes it eligible ([eligible], no draw), what exactly is offered ([build], the only place
 * that draws), how it reads ([text]) and what each answer costs and does ([options], shared by the screen and the
 * resolver, always against the run as it is now). Amounts are stored on the instance under the names used here.
 */
internal object EncounterCatalog {

    // ---- shared reads ---------------------------------------------------------------------------------------

    private fun freeHeroes(ctx: ResolutionContext): List<Hero> {
        val named = Market.openCommissions(ctx).flatMap { listOfNotNull(it.buyerId, it.recipientId) }.toSet()
        return ctx.residents().filter { it.id !in named }
    }

    private fun slotFree(ctx: ResolutionContext): Boolean = Market.openCommissions(ctx).size < ctx.config.customers.maxOpenCommissions
    private fun noSlot(ctx: ResolutionContext): String? = if (slotFree(ctx)) null else "The order book is full."
    private fun gone(ctx: ResolutionContext, id: HeroId?): String? = ctx.heroes[id]?.takeIf { it.isAlive }.let { if (it == null) "They're no longer in town." else null }

    /** A blade the smith can still part with: in storage or on the shelf, and not promised to an order. */
    private fun inShop(ctx: ResolutionContext, id: WeaponId?): Weapon? = ctx.weapons[id]?.takeIf { (it.isInStorage || it.isListed) && it.promisedTo == null }

    private fun unitPrice(ctx: ResolutionContext, id: MaterialId): Int = (ctx.content.material(id).price * ctx.config.supplierPriceMultiplier * ctx.world.marketMultiplier).toInt()
    private fun orderReward(ctx: ResolutionContext, minQuality: Int): Int = ctx.config.commissionRewardBase + minQuality * ctx.config.commissionRewardPerQuality

    private fun unarmedOrWorn(ctx: ResolutionContext, h: Hero): Boolean = ctx.equippedWeapon(h.id).let { it == null || it.condition < ctx.config.wornConditionThreshold }
    private fun pledgers(ctx: ResolutionContext): List<Hero> =
        freeHeroes(ctx).filter { it.gold < ctx.config.depth.pledgeThinPurse && unarmedOrWorn(ctx, it) }.sortedWith(compareBy<Hero> { it.gold }.thenBy(IdOrder.numeric) { it.id.value })
    private fun patron(ctx: ResolutionContext, not: HeroId?): Hero? =
        freeHeroes(ctx).filter { it.id != not }.maxWithOrNull(compareBy<Hero> { it.gold }.thenByDescending(IdOrder.numeric) { it.id.value })

    private fun prized(ctx: ResolutionContext): Weapon? =
        ctx.weapons.values.filter { inShop(ctx, it.id) != null && (it.fame > 0 || it.rarity >= Rarity.EPIC) }
            .maxWithOrNull(compareBy<Weapon> { it.fame }.thenBy { it.quality }.thenByDescending(IdOrder.numeric) { it.id.value })

    /** A blade that came back from a fallen hero, and the living hero closest to them: of their line, else of their guild, else a regular of the shop who was in town when they fell. */
    private fun heirloom(ctx: ResolutionContext): Triple<Weapon, Hero, Hero>? {
        val free = freeHeroes(ctx)
        for (w in ctx.weapons.values.filter { it.isInStorage && it.promisedTo == null }.sortedWith(compareBy(IdOrder.numeric) { it.id.value })) {
            val fallen = w.history.lastOrNull { it.kind == "RECOVERED" && it.era == ctx.era }?.subjectIds?.firstOrNull()?.let { ctx.heroes[HeroId(it)] }?.takeIf { it.fate == HeroFate.DEAD } ?: continue
            val heir = free.firstOrNull { fallen.lineageId != null && it.lineageId == fallen.lineageId } ?: free.firstOrNull { fallen.guildId != null && it.guildId == fallen.guildId }
                ?: free.firstOrNull { Market.isRegular(it, ctx.config) && it.arrivedOnDay <= (fallen.diedOnDay ?: 0) } ?: continue
            return Triple(w, heir, fallen)
        }
        return null
    }

    private fun counterAugment(ctx: ResolutionContext): MaterialId? {
        val weak = Battle.besieger(ctx)?.let { ctx.content.faction(it.id).weakTo } ?: return null
        return ctx.content.materials(MaterialCategory.AUGMENT).filter { it.element == weak }.minByOrNull { it.price }?.id
    }

    private fun observedKeys(ctx: ResolutionContext) = WorldEvents.keysInState(ctx, KnowledgeState.OBSERVED)
    private fun openSignatures(ctx: ResolutionContext) = WorldEvents.forgeableSignatures(ctx).filter {
        ctx.legacy.journal.state(it.journalKey) != KnowledgeState.SIGNATURE_DISCOVERED && Journal.nextRung(ctx.legacy.journal, it) != null
    }

    // ---- eligibility and offer ------------------------------------------------------------------------------

    fun eligible(ctx: ResolutionContext, def: EncounterDef): Boolean = when (def.id) {
        Depth.LAST_CRATE -> ctx.town.nextSiegeDay - ctx.day in 0..ctx.config.depth.crateWindowDays && counterAugment(ctx) != null
        Depth.BLADE_FOR_THE_WALL -> slotFree(ctx) && pledgers(ctx).firstOrNull().let { it != null && patron(ctx, it.id) != null }
        Depth.MASTERS_AFTERNOON -> observedKeys(ctx).isNotEmpty() || openSignatures(ctx).isNotEmpty()
        Depth.COLLECTORS_OFFER -> prized(ctx) != null
        Depth.CRACKED_FAMILY_BLADE -> slotFree(ctx) && heirloom(ctx) != null
        Depth.CROOKED_MERCHANT -> true
        Depth.SMITHS_WAGER -> ctx.consequences.none { it.kind == ConsequenceKind.WAGER }
        Depth.FESTIVAL_CONTRACT -> ctx.consequences.none { it.kind == ConsequenceKind.WATCH_BOUNTY }
        else -> com.tinyblacksmith.core.guild.GuildVisitors.eligible(ctx, def)
    }

    fun build(ctx: ResolutionContext, def: EncounterDef, rng: Rng, id: String): EncounterInstance {
        val cfg = ctx.config.depth
        val config = ctx.config
        val base = EncounterInstance(id, def.id, ctx.day)
        return when (def.id) {
            Depth.LAST_CRATE -> {
                val augment = counterAugment(ctx)!!
                val core = ctx.content.materials(MaterialCategory.CORE).minBy { it.price }.id
                base.copy(materialId = augment, otherMaterialId = core, amounts = mapOf(
                    "units" to cfg.crateUnits, "cratePrice" to (unitPrice(ctx, augment) * cfg.crateUnits * cfg.crateCounterPriceFactor).toInt(),
                    "metalPrice" to (unitPrice(ctx, core) * cfg.crateUnits * cfg.crateMetalPriceFactor).toInt()))
            }
            Depth.BLADE_FOR_THE_WALL -> {
                val poor = pledgers(ctx).first()
                val rich = patron(ctx, poor.id)!!
                val decent = QualityBand.DECENT.floor(config)
                val fine = QualityBand.FINE.floor(config)
                base.copy(heroId = poor.id, otherHeroId = rich.id,
                    familyId = rng.pick(ctx.content.heroClass(poor.classId).preferredFamilies), otherFamilyId = rng.pick(ctx.content.heroClass(rich.classId).preferredFamilies),
                    amounts = mapOf("pledgeQuality" to decent, "pledgeReward" to (orderReward(ctx, decent) * cfg.pledgeRewardFactor).toInt(), "owed" to orderReward(ctx, decent) - (orderReward(ctx, decent) * cfg.pledgeRewardFactor).toInt(),
                        "pledgeDeadline" to maxOf(ctx.day + 1, minOf(ctx.town.nextSiegeDay, ctx.day + config.commissionDeadlineDays)),
                        "richQuality" to fine, "richReward" to (orderReward(ctx, fine) * cfg.richOrderRewardFactor).toInt(), "richDeadline" to ctx.day + config.commissionDeadlineDays))
            }
            Depth.MASTERS_AFTERNOON -> base.copy(
                key = observedKeys(ctx).takeIf { it.isNotEmpty() }?.let { rng.pick(it) },
                otherKey = openSignatures(ctx).takeIf { it.isNotEmpty() }?.let { rng.pick(it).journalKey },
                amounts = mapOf("energy" to cfg.masterEnergy, "gold" to cfg.masterGold))
            Depth.COLLECTORS_OFFER -> prized(ctx)!!.let { w -> base.copy(weaponId = w.id, amounts = mapOf("price" to (Market.askingPrice(w, config) * config.collectorPriceMultiplier).toInt())) }
            Depth.CRACKED_FAMILY_BLADE -> heirloom(ctx)!!.let { (w, heir, fallen) ->
                base.copy(weaponId = w.id, heroId = heir.id, otherHeroId = fallen.id, materialId = w.coreId, amounts = mapOf(
                    "energy" to cfg.restoreEnergy, "reward" to (Market.askingPrice(w.copy(condition = 100), config) * cfg.heirloomPriceFactor).toInt(),
                    "deadline" to ctx.day + cfg.heirloomDeadlineDays, "collector" to Market.askingPrice(w, config)))
            }
            Depth.CROOKED_MERCHANT -> {
                val content = ctx.content
                val family = rng.pick(content.families)
                val core = rng.pick(content.materials(MaterialCategory.CORE).filter { it.tier in cfg.merchantCoreTierMin..cfg.merchantCoreTierMax }.ifEmpty { content.materials(MaterialCategory.CORE) })
                val augment = rng.pick(content.materials(MaterialCategory.AUGMENT))
                val quality = rng.nextInt(cfg.merchantQualityMin, cfg.merchantQualityMax)
                val affix = rng.pick(content.affixes.filter { it.kind == AffixKind.BENEFICIAL && it.element == null })
                val flaw = rng.pick(content.affixes.filter { it.kind == AffixKind.FLAW })
                fun blade(flaws: List<AffixId>) = Weapon(
                    id = WeaponId("merchant"), name = Forge.weaponName(content, family.id, core.id, listOf(affix.id)), familyId = family.id, coreId = core.id, augmentId = augment.id,
                    mode = ForgeMode.QUICK, risk = Risk.BALANCED, quality = quality, rarity = Forge.rarityFor(quality, config),
                    power = Forge.powerOf(content, config, family.id, core.id, quality, listOf(affix.id) + flaws), element = augment.element,
                    affixes = listOf(affix.id), flaws = flaws, location = WeaponLocation.Storage, forgedEra = ctx.era, forgedDay = ctx.day,
                )
                base.copy(blade = blade(listOf(flaw.id)), familyId = family.id, materialId = core.id,
                    amounts = mapOf("price" to maxOf((Market.askingPrice(blade(emptyList()), config) * cfg.merchantPriceFactor).toInt(), unitPrice(ctx, core.id) + unitPrice(ctx, augment.id)), "fee" to cfg.inspectionGold))
            }
            Depth.SMITHS_WAGER -> {
                val fine = QualityBand.SUPERB.floor(config)
                val decent = QualityBand.DECENT.floor(config)
                val buyer = freeHeroes(ctx).firstOrNull()
                base.copy(familyId = rng.pick(ctx.content.families).id, otherHeroId = buyer?.id,
                    otherFamilyId = buyer?.let { rng.pick(ctx.content.heroClass(it.classId).preferredFamilies) },
                    amounts = mapOf("stake" to cfg.wagerStake, "quality" to fine, "due" to ctx.day + cfg.wagerDays - 1, "payout" to cfg.wagerStake * cfg.wagerPayoutFactor,
                        "orderQuality" to decent, "orderReward" to orderReward(ctx, decent), "orderDeadline" to ctx.day + config.commissionDeadlineDays))
            }
            Depth.FESTIVAL_CONTRACT -> base.copy(amounts = mapOf("fee" to cfg.stallFee, "bounty" to cfg.watchBounty, "blades" to cfg.watchBountyBlades, "until" to ctx.day + cfg.watchBountyDays - 1))
            else -> com.tinyblacksmith.core.guild.GuildVisitors.build(ctx, def, rng, id)
        }
    }

    // ---- wording --------------------------------------------------------------------------------------------

    private fun name(ctx: ResolutionContext, id: HeroId?): String = ctx.heroes[id]?.fullName ?: "someone"
    private fun blade(ctx: ResolutionContext, i: EncounterInstance): String = ctx.weapons[i.weaponId]?.name ?: "the weapon"
    private fun family(ctx: ResolutionContext, id: WeaponFamilyId?): String = id?.let { ctx.content.family(it).name } ?: "weapon"
    private fun mat(ctx: ResolutionContext, id: MaterialId?): String = id?.let { ctx.content.material(it).name } ?: "metal"
    private fun describe(ctx: ResolutionContext, w: Weapon): String =
        "${w.name}: ${QualityBand.of(w.quality, ctx.config).word} (quality ${w.quality}), power ${w.power}, " +
            (w.affixes.map { ctx.content.affix(it).name } + w.flaws.map { ctx.content.affix(it).let { f -> "${f.name} (flaw: ${f.description})" } }).joinToString(", ")

    fun text(ctx: ResolutionContext, i: EncounterInstance, def: EncounterDef): String {
        val a = i.amounts
        return when (i.defId) {
            Depth.LAST_CRATE -> "The siege on day ${ctx.town.nextSiegeDay} will close the roads. A carter has one crate left. ${mat(ctx, i.materialId)} is effective against " +
                "${Battle.besieger(ctx)?.let { ctx.content.faction(it.id).name } ?: "the likely attackers"}" + (if (ctx.siege?.factionId == null) ". The attacking faction isn't confirmed yet." else ".")
            Depth.BLADE_FOR_THE_WALL -> "${name(ctx, i.heroId)} has ${ctx.heroes[i.heroId]?.gold ?: 0} gold and ${if (ctx.heroes[i.heroId]?.let { ctx.equippedWeapon(it.id) } == null) "no weapon" else "a worn weapon"}. " +
                "They need a ${family(ctx, i.familyId)} on credit before the siege. ${name(ctx, i.otherHeroId)} can pay more for a separate order. You have room for one commission."
            Depth.MASTERS_AFTERNOON -> "A master smith is visiting for the day. " +
                (i.key?.let { "Spend the afternoon learning ${Journal.subjectName(ctx.content, it)} with her. " } ?: "") +
                (i.otherKey?.let { "Or pay for a recipe clue about ${Journal.subjectName(ctx.content, it)}." } ?: "")
            Depth.COLLECTORS_OFFER -> "A collector offers ${a["price"]} gold for ${blade(ctx, i)}. Sell it and it leaves town. Keep it and a local hero may buy it."
            Depth.CRACKED_FAMILY_BLADE -> "${name(ctx, i.heroId)} asks about ${blade(ctx, i)}. It came back after ${name(ctx, i.otherHeroId)} died. " +
                "Restore it for ${a["reward"]} gold, or sell it as it is to a collector for ${a["collector"]} gold."
            Depth.CROOKED_MERCHANT -> i.blade?.let { b ->
                "A merchant offers a ${mat(ctx, i.materialId)} ${family(ctx, i.familyId)} for ${a["price"]} gold. One flaw, he says. Which one costs extra to find out. " +
                    (if (i.inspected) "Inspection complete. ${describe(ctx, b)}." else "Pay ${a["fee"]} gold to inspect it before buying.")
            } ?: def.description
            Depth.SMITHS_WAGER -> "A visiting smith puts ${a["stake"]} gold on a challenge. Forge a ${QualityBand.of(a["quality"] ?: 0, ctx.config).word} ${family(ctx, i.familyId)} " +
                "with quality ${a["quality"]} or better by the end of day ${a["due"]}. The reward is shown below."
            Depth.FESTIVAL_CONTRACT -> "The council offers two jobs. Pay for a festival stall to bring shoppers in today, or earn gold by arming the watch."
            Depth.DEBT_REPAID -> "${name(ctx, i.heroId)} returned with ${blade(ctx, i)}, the weapon you made on credit" +
                (if (ctx.weapons[i.weaponId]?.isEquipped == true) ". " else ". They're keeping it as a spare. ") +
                (if (a["stood"] == 1) "They defended the walls with it" + (if (a["held"] == 1) ". Emberfall held. " else ". The defenses still fell. ") else "They weren't among the siege champions. ") +
                "They owe ${a["owed"]} gold and have ${ctx.heroes[i.heroId]?.gold ?: 0} to spend."
            else -> com.tinyblacksmith.core.guild.GuildVisitors.text(ctx, i, def)
        }
    }

    // ---- answers --------------------------------------------------------------------------------------------

    private fun pass(label: String, told: String) = Opt(PASS, label, "Nothing changes.") { told }

    private fun order(ctx: ResolutionContext, buyer: HeroId, family: WeaponFamilyId, quality: Int, reward: Int, deadline: Int, kind: CommissionKind, weaponId: WeaponId? = null): Commission {
        val c = Commission(ctx.newCommissionId(), buyer, family, quality, reward, ctx.day, deadline, CommissionStatus.ACCEPTED, kind = kind, weaponId = weaponId)
        ctx.commissions[c.id] = c
        return c
    }

    /** Every answer of this morning's visitor, with what a guild run adds to a visitor that is older than the guild. */
    fun options(ctx: ResolutionContext, i: EncounterInstance): List<Opt> = com.tinyblacksmith.core.guild.GuildVisitors.extend(ctx, i, ownOptions(ctx, i))

    private fun ownOptions(ctx: ResolutionContext, i: EncounterInstance): List<Opt> {
        val a = i.amounts
        fun n(key: String) = a[key] ?: 0
        val config = ctx.config
        return when (i.defId) {
            Depth.LAST_CRATE -> listOf(
                Opt("crate", "Buy the crate", "${n("units")} ${mat(ctx, i.materialId)}.", gold = n("cratePrice")) { c ->
                    c.materials[i.materialId!!] = (c.materials[i.materialId] ?: 0) + n("units")
                    "Bought the last crate. ${n("units")} ${mat(c, i.materialId)} for ${n("cratePrice")} gold."
                },
                Opt("metal", "Take plain metal instead", "${n("units")} ${mat(ctx, i.otherMaterialId)}.", gold = n("metalPrice")) { c ->
                    c.materials[i.otherMaterialId!!] = (c.materials[i.otherMaterialId] ?: 0) + n("units")
                    "Bought ${n("units")} ${mat(c, i.otherMaterialId)} for ${n("metalPrice")} gold."
                },
                pass("Let the carter go", "The carter left with the last crate unsold."),
            )
            Depth.BLADE_FOR_THE_WALL -> listOf(
                Opt("pledge", "Take ${name(ctx, i.heroId)}'s order on credit",
                    "Make a ${Commissions.describe(Commission(CommissionId(""), i.heroId!!, i.familyId!!, n("pledgeQuality"), 0, 0, 0, CommissionStatus.OFFERED), ctx.content, config)} by day ${n("pledgeDeadline")}. Receive ${n("pledgeReward")} gold on collection. They owe another ${n("owed")} gold afterward.",
                    blocked = gone(ctx, i.heroId) ?: noSlot(ctx)) { c ->
                    val o = order(c, i.heroId, i.familyId, n("pledgeQuality"), n("pledgeReward"), n("pledgeDeadline"), CommissionKind.WALL_PLEDGE)
                    c.consequences += ScheduledConsequence("q${i.id}", ConsequenceKind.WALL_PLEDGE, dueDay = 0, heroId = i.heroId, commissionId = o.id, amounts = mapOf("owed" to n("owed")))
                    "Accepted ${name(c, i.heroId)}'s order on credit. A ${family(c, i.familyId)} by day ${n("pledgeDeadline")}, for ${n("pledgeReward")} gold on collection and ${n("owed")} owed afterward."
                },
                Opt("patron", "Take ${name(ctx, i.otherHeroId)}'s order",
                    "Make a ${Commissions.describe(Commission(CommissionId(""), i.otherHeroId!!, i.otherFamilyId!!, n("richQuality"), 0, 0, 0, CommissionStatus.OFFERED), ctx.content, config)} by day ${n("richDeadline")}. Pays ${n("richReward")} gold.",
                    blocked = gone(ctx, i.otherHeroId) ?: noSlot(ctx)) { c ->
                    order(c, i.otherHeroId, i.otherFamilyId, n("richQuality"), n("richReward"), n("richDeadline"), CommissionKind.ORDINARY)
                    "Accepted ${name(c, i.otherHeroId)}'s order for ${n("richReward")} gold. ${name(c, i.heroId)} left without an order."
                },
                pass("Turn both away", "Declined both orders. ${name(ctx, i.heroId)} and ${name(ctx, i.otherHeroId)} left."),
            )
            Depth.MASTERS_AFTERNOON -> listOf(
                Opt("study", "Spend the afternoon learning", i.key?.let { "Completes your notes on ${Journal.subjectName(ctx.content, it)}." } ?: "Nothing to study.", energy = n("energy"),
                    blocked = if (i.key == null || ctx.legacy.journal.state(i.key) != KnowledgeState.OBSERVED) "No partly learned pairings left to study." else null) { c ->
                    WorldEvents.setKnowledge(c, i.key!!, KnowledgeState.UNDERSTOOD)
                    c.discoveriesThisRun += 1
                    val subject = Journal.subjectName(c.content, i.key)
                    c.emit(EventType.DISCOVERY, 3, "Pairing learned. $subject. ${Journal.describeAffinity(Journal.affinityFor(c.content, i.key)).replaceFirstChar { it.uppercase() }}.", data = mapOf("key" to i.key))
                    "Spent the afternoon learning $subject with the master smith."
                },
                Opt("lesson", "Buy a recipe clue", i.otherKey?.let { "Adds a recipe clue for ${Journal.subjectName(ctx.content, it)}." } ?: "Nothing to tell.", gold = n("gold"),
                    blocked = openSignatures(ctx).firstOrNull { it.journalKey == i.otherKey }.let { if (it == null) "You've already learned all the clues she can offer." else null }) { c ->
                    val sig = openSignatures(c).first { it.journalKey == i.otherKey }
                    val rung = Journal.nextRung(c.legacy.journal, sig)!!
                    Journal.earn(c, sig, rung)
                    val clue = Journal.clue(sig, rung, c.config)
                    c.emit(EventType.DISCOVERY, 3, "The master smith shared a clue about ${Journal.subjectName(c.content, sig.journalKey)}. ${clue.replaceFirstChar { it.uppercase() }}.", data = mapOf("key" to sig.journalKey, "rung" to rung.name))
                    "Paid ${n("gold")} gold for a recipe clue."
                },
                pass("Keep working", "The master smith moved on. You kept working."),
            )
            Depth.COLLECTORS_OFFER -> {
                val w = inShop(ctx, i.weaponId)
                listOf(
                    Opt("sell", "Sell ${blade(ctx, i)}", "+${n("price")} gold, +${config.worldEvents.collectorReputation} reputation. The weapon leaves Emberfall permanently.",
                        blocked = if (w == null) "The weapon is no longer in the shop." else null) { c ->
                        val blade = c.weapon(i.weaponId!!)
                        c.gold += n("price")
                        c.reputation += c.config.worldEvents.collectorReputation
                        c.updateWeapon(blade.copy(location = WeaponLocation.Lost(c.day, "sold to a collector")))
                        c.addWeaponHistory(blade.id, "COLLECTED", "Bought by a collector for ${n("price")} gold. Left Emberfall.")
                        "A collector bought ${blade.name} for ${n("price")} gold and took it out of town."
                    },
                    pass("Keep it for the town", "Kept ${blade(ctx, i)}. The collector left without it."),
                )
            }
            Depth.CRACKED_FAMILY_BLADE -> {
                val w = inShop(ctx, i.weaponId)?.takeIf { it.isInStorage }
                val lost = if (w == null) "The weapon is no longer in storage." else null
                listOf(
                    Opt("restore", "Restore it for ${name(ctx, i.heroId)}", "Restores the weapon to full condition and reserves it for them. They pay ${n("reward")} gold when they collect it by day ${n("deadline")}.",
                        energy = n("energy"), material = i.materialId, blocked = lost ?: gone(ctx, i.heroId) ?: noSlot(ctx)) { c ->
                        val blade = c.weapon(i.weaponId!!)
                        val o = order(c, i.heroId!!, blade.familyId, 1, n("reward"), n("deadline"), CommissionKind.HEIRLOOM, blade.id)
                        c.updateWeapon(blade.copy(condition = 100, promisedTo = o.id))
                        c.addWeaponHistory(blade.id, "RESTORED", "Restored for ${name(c, i.heroId)}, who knew ${name(c, i.otherHeroId)}.", listOf(i.heroId.value))
                        "Restored ${blade.name} for ${name(c, i.heroId)}. It once belonged to ${name(c, i.otherHeroId)}."
                    },
                    Opt("collector", "Sell it to the collector", "+${n("collector")} gold. The weapon leaves Emberfall permanently.", blocked = lost) { c ->
                        val blade = c.weapon(i.weaponId!!)
                        c.gold += n("collector")
                        c.updateWeapon(blade.copy(location = WeaponLocation.Lost(c.day, "sold to a collector")))
                        c.addWeaponHistory(blade.id, "COLLECTED", "Sold to a collector for ${n("collector")} gold. ${name(c, i.heroId)} had wanted it.")
                        "Sold ${blade.name} to a collector for ${n("collector")} gold. It once belonged to ${name(c, i.otherHeroId)}. ${name(c, i.heroId)} left without it."
                    },
                    pass("Keep it as it is", "Kept ${blade(ctx, i)} in storage. ${name(ctx, i.heroId)} left without it."),
                )
            }
            Depth.CROOKED_MERCHANT -> listOfNotNull(
                Opt("buy", if (i.inspected) "Buy it" else "Buy it unseen", if (i.inspected) "Adds the weapon to storage." else "Adds a ${mat(ctx, i.materialId)} ${family(ctx, i.familyId)} with one unknown flaw to storage.", gold = n("price")) { c ->
                    val b = i.blade!!.copy(id = c.newWeaponId(), forgedDay = c.day, history = listOf(HistoryEntry(c.era, c.day, "BOUGHT", "Bought from a crooked merchant for ${n("price")} gold.")))
                    c.updateWeapon(b)
                    "Bought ${b.name} from the merchant for ${n("price")} gold" + (if (i.inspected) "." else " without inspecting it. Its flaw is ${b.flaws.joinToString { f -> c.content.affix(f).name }}.")
                },
                if (i.inspected) null else Opt("inspect", "Inspect the weapon", "Reveals the weapon's stats and flaw. You can then buy it or walk away.", gold = n("fee"), closes = false) { c ->
                    c.encounter = i.copy(inspected = true)
                    "Paid ${n("fee")} gold to inspect the merchant's weapon."
                },
                pass("Walk away", "Declined the merchant's offer."),
            )
            Depth.SMITHS_WAGER -> listOf(
                Opt("wager", "Take the wager", "Stake ${n("stake")} gold. Forge a ${family(ctx, i.familyId)} with quality ${n("quality")} or better by the end of day ${n("due")}. Reward: " +
                    (if (Relics.unowned(ctx).isNotEmpty()) "a relic to choose" else "${n("payout")} gold") + ". Miss the deadline and you lose the stake.", gold = n("stake")) { c ->
                    c.consequences += ScheduledConsequence("q${i.id}", ConsequenceKind.WAGER, n("due"), familyId = i.familyId, amounts = mapOf("serial" to c.nextWeaponSerial, "quality" to n("quality"), "payout" to n("payout"), "stake" to n("stake")))
                    "Staked ${n("stake")} gold on a ${family(c, i.familyId)} with quality ${n("quality")} or better by day ${n("due")}."
                },
                Opt("order", "Take a regular order",
                    i.otherHeroId?.let { "An order from ${name(ctx, it)} for a ${family(ctx, i.otherFamilyId)} of quality ${n("orderQuality")}+ by day ${n("orderDeadline")} at ${n("orderReward")} gold." } ?: "No customer is available.",
                    blocked = if (i.otherHeroId == null) "Nobody in town has an order to place." else gone(ctx, i.otherHeroId) ?: noSlot(ctx)) { c ->
                    order(c, i.otherHeroId!!, i.otherFamilyId!!, n("orderQuality"), n("orderReward"), n("orderDeadline"), CommissionKind.ORDINARY)
                    "Declined the wager and accepted ${name(c, i.otherHeroId)}'s ${n("orderReward")} gold order."
                },
                pass("Refuse", "Declined the visiting smith's wager."),
            )
            Depth.FESTIVAL_CONTRACT -> listOf(
                Opt("stall", "Pay for a festival stall", "The festival crowd comes to the shop today.", gold = n("fee")) { c ->
                    c.worldFlags[WorldEvents.FLAG_FESTIVAL] = c.day
                    "Paid ${n("fee")} gold for a festival stall. More shoppers will visit today."
                },
                Opt("watch", "Arm the watch for the council", "Earn ${n("bounty")} gold for each of the next ${n("blades")} weapons donated to the watch. Offer ends after day ${n("until")}.") { c ->
                    c.consequences += ScheduledConsequence("q${i.id}", ConsequenceKind.WATCH_BOUNTY, n("until"), amounts = mapOf("left" to n("blades"), "gold" to n("bounty")))
                    "The council will pay ${n("bounty")} gold per weapon donated to the watch. Up to ${n("blades")} weapons, through day ${n("until")}."
                },
                pass("Stay out of it", "The forge kept out of the festival."),
            )
            Depth.DEBT_REPAID -> {
                val hero = ctx.heroes[i.heroId]?.takeIf { it.isAlive }
                val pays = minOf(n("owed"), hero?.gold ?: 0)
                listOfNotNull(
                    Opt("collect", "Take what they owe", "Receive $pays gold from ${name(ctx, i.heroId)}" + (if (pays < n("owed")) ". That is all they have." else "."), blocked = gone(ctx, i.heroId)) { c ->
                        val h = c.hero(i.heroId!!)
                        val paid = minOf(n("owed"), h.gold)
                        c.updateHero(h.copy(gold = h.gold - paid))
                        c.gold += paid
                        "${h.fullName} repaid $paid gold for ${blade(c, i)}."
                    },
                    Opt("forgive", "Forgive the debt", "${name(ctx, i.heroId)} becomes a regular and trains the militia. Adds ${config.depth.pledgeMilitia} militia strength.", blocked = gone(ctx, i.heroId)) { c ->
                        val h = c.hero(i.heroId!!)
                        c.updateHero(h.copy(loyalty = maxOf(h.loyalty, c.config.regularLoyaltyThreshold)))
                        c.town = c.town.copy(militia = minOf(maxOf(c.config.militiaMax, c.town.militia), c.town.militia + c.config.depth.pledgeMilitia))
                        "Forgave ${h.fullName}'s debt. They became a regular and trained the militia."
                    },
                    if (n("stood") == 1 && n("held") == 1) Opt("speak", "Have them speak for the forge", "Adds ${config.depth.pledgeReputation} reputation. They tell the town about your weapon.", blocked = gone(ctx, i.heroId)) { c ->
                        c.reputation += c.config.depth.pledgeReputation
                        "${name(c, i.heroId)} praised your work around town. The debt was settled through reputation."
                    } else null,
                    pass("Leave it for now", "Left ${name(ctx, i.heroId)}'s debt unsettled."),
                )
            }
            else -> com.tinyblacksmith.core.guild.GuildVisitors.options(ctx, i)
        }
    }
}
