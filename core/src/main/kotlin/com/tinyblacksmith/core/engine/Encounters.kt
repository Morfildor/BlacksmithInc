package com.tinyblacksmith.core.engine

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.EncounterDef
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream

/**
 * Morning visitors (report section 3): one offer a morning from `depth.encounterFirstDay`, made and stored at the end
 * of End Day, answered in planning by [Command.ResolveEncounter], and expired with its free default at the next End
 * Day. What an encounter is (who is eligible, what is offered, what each answer does) is in `EncounterCatalog`.
 * Everything here draws on the ENCOUNTERS stream only, and only while an offer is being made.
 */
object Encounters {
    /** The answer every encounter has: free, changes nothing, and what an unanswered visitor is taken to have been told. */
    const val PASS = "pass"
    /** Key prefix in `eventCounters` / `eventLastDay` for an encounter that did not replace a world event. */
    const val COUNTER_PREFIX = "enc:"

    /** One answer as the planning screen shows it. [blocked] is why it cannot be chosen now, or null. */
    data class OptionView(val id: String, val label: String, val cost: String, val effect: String, val blocked: String?)

    data class View(
        val instance: EncounterInstance, val name: String, val text: String, val options: List<OptionView>,
        /** What End Day will take the visitor to have been told. */
        val defaultLabel: String,
    )

    /** An answer in the resolver's terms. [apply] performs it after the costs are paid and returns what the Gazette records. */
    internal class Opt(
        val id: String, val label: String, val effect: String,
        val gold: Int = 0, val energy: Int = 0, val material: MaterialId? = null,
        /** A reason the answer is not available that the costs do not cover (its subject is gone, no order slot is free). */
        val blocked: String? = null,
        /** False for an answer that leaves the visitor at the forge (the merchant's inspection). */
        val closes: Boolean = true,
        val apply: (ResolutionContext) -> String,
    )

    fun counterKey(def: EncounterDef): String = def.replacesEvent ?: (COUNTER_PREFIX + def.id)

    /** Read-only: this morning's visitor as it stands now, or null. Draws nothing. */
    fun view(state: GameState, content: ContentCatalog, config: BalanceConfig): View? {
        val inst = state.encounter ?: return null
        val def = content.encounter(inst.defId) ?: return null
        val ctx = ResolutionContext(state, content, config)
        val options = EncounterCatalog.options(ctx, inst)
        return View(
            inst, def.name, EncounterCatalog.text(ctx, inst, def),
            options.map { OptionView(it.id, it.label, costText(ctx, it), it.effect, if (inst.isOpen) reason(ctx, it) else null) },
            options.first { it.id == PASS }.label,
        )
    }

    private fun costText(ctx: ResolutionContext, o: Opt): String =
        listOfNotNull(
            o.gold.takeIf { it > 0 }?.let { "$it gold" }, o.energy.takeIf { it > 0 }?.let { "$it energy" },
            o.material?.let { "1 ${ctx.content.material(it).name}" },
        ).joinToString(", ").ifEmpty { "Free" }

    private fun reason(ctx: ResolutionContext, o: Opt): String? = o.blocked
        ?: (if (ctx.gold < o.gold) "Costs ${o.gold} gold. You have ${ctx.gold}." else null)
        ?: (if (o.energy > 0 && !ctx.canSpendEnergy(o.energy)) "Needs ${o.energy} energy." else null)
        ?: o.material?.takeIf { (ctx.materials[it] ?: 0) < 1 }?.let { "Needs 1 ${ctx.content.material(it).name}." }

    /** Null when accepted. A repeat of the command that already answered is accepted by the engine before it gets here. */
    fun resolve(ctx: ResolutionContext, cmd: Command.ResolveEncounter): GameError? {
        val inst = ctx.encounter ?: return GameError.NoEncounter
        if (inst.id != cmd.instanceId || !inst.isOpen) return GameError.EncounterNotOpen(cmd.instanceId)
        val def = ctx.content.encounter(inst.defId) ?: return GameError.UnknownContent(inst.defId)
        val opt = EncounterCatalog.options(ctx, inst).firstOrNull { it.id == cmd.optionId } ?: return GameError.UnknownContent(cmd.optionId)
        reason(ctx, opt)?.let { return GameError.EncounterOptionBlocked(opt.id, it) }
        ctx.gold -= opt.gold
        if (opt.energy > 0) ctx.spendEnergy(opt.energy)
        opt.material?.let { ctx.materials[it] = ctx.materials.getValue(it) - 1 }
        val told = opt.apply(ctx)
        val now = ctx.encounter ?: inst   // an answer may have written to the instance (the inspection)
        ctx.encounter = now.copy(status = if (opt.closes) EncounterStatus.RESOLVED else EncounterStatus.OPEN, chosen = if (opt.closes) opt.id else null, commandId = cmd.commandId.value)
        if (opt.closes) log(ctx, inst, opt.id, expired = false)
        ctx.emit(EventType.ENCOUNTER_RESOLVED, if (opt.id == PASS) 1 else 4, told, subjects(inst), mapOf("encounter" to def.id, "option" to opt.id, "instance" to inst.id))
        return null
    }

    private fun subjects(inst: EncounterInstance): List<String> = listOfNotNull(inst.heroId?.value, inst.otherHeroId?.value, inst.weaponId?.value)

    private fun log(ctx: ResolutionContext, inst: EncounterInstance, optionId: String, expired: Boolean) {
        ctx.encounterLog += EncounterRecord(inst.id, inst.defId, inst.day, optionId, expired)
        while (ctx.encounterLog.size > ctx.config.depth.encounterLogKept) ctx.encounterLog.removeAt(0)
    }

    /** Top of End Day: a visitor nobody answered is taken to have been told [PASS], which costs and changes nothing. */
    fun expire(ctx: ResolutionContext) {
        val inst = ctx.encounter?.takeIf { it.isOpen } ?: return
        val def = ctx.content.encounter(inst.defId)
        ctx.encounter = inst.copy(status = EncounterStatus.EXPIRED, chosen = PASS)
        log(ctx, inst, PASS, expired = true)
        val label = EncounterCatalog.options(ctx, inst).first { it.id == PASS }.label
        ctx.emit(EventType.ENCOUNTER_EXPIRED, 1, "No answer was given to ${def?.name ?: "the visitor"}. Default choice taken. $label.", subjects(inst), mapOf("encounter" to inst.defId, "instance" to inst.id))
    }

    /**
     * The new morning's visitor, after the day number has advanced. A due follow-up takes the slot. A relic or blessing
     * choice that is still waiting does not keep visitors away: a smith who puts that choice off would otherwise never
     * see one. The screen shows a visitor as a card to open, so nothing opens over anything else.
     */
    fun offerMorning(ctx: ResolutionContext) {
        ctx.encounter = null
        if (ctx.content.encounters.isEmpty()) return
        val cfg = ctx.config.depth
        val followUp = Consequences.dueFollowUp(ctx)
        val inst = followUp ?: run {
            if (ctx.day < cfg.encounterFirstDay) return
            val rng = ctx.rng(RngStream.ENCOUNTERS)
            if (!rng.chance(cfg.encounterChance)) return
            val eligible = ctx.content.encounters.filter { canOffer(ctx, it) }
            if (eligible.isEmpty()) return
            val def = rng.pickWeighted(eligible.map { it to it.weight })
            EncounterCatalog.build(ctx, def, rng, newId(ctx))
        }
        val def = ctx.content.encounter(inst.defId) ?: return
        ctx.encounter = inst
        val key = counterKey(def)
        ctx.eventCounters[key] = (ctx.eventCounters[key] ?: 0) + 1
        ctx.eventLastDay[key] = ctx.day
        ctx.emit(EventType.ENCOUNTER_OFFERED, 3, "A visitor arrived this morning. ${def.name}.", subjects(inst), mapOf("encounter" to def.id, "instance" to inst.id))
    }

    fun newId(ctx: ResolutionContext): String = "n${ctx.nextEncounterSerial++}"

    /** Limits, cooldown and the encounter's own eligibility. Draws nothing. */
    fun canOffer(ctx: ResolutionContext, def: EncounterDef): Boolean {
        if (def.followUp || ctx.day < def.minDay) return false
        val key = counterKey(def)
        if ((ctx.eventCounters[key] ?: 0) >= def.maxPerRun) return false
        val last = ctx.eventLastDay[key]
        if (last != null && ctx.day - last <= def.cooldownDays) return false
        return EncounterCatalog.eligible(ctx, def)
    }

    /** Makes [defId] this morning's visitor regardless of chance and weight: for tests, scenario saves and the simulator's reachability check. Null when it is not eligible. */
    fun force(state: GameState, content: ContentCatalog, config: BalanceConfig, defId: String): GameState? {
        val def = content.encounter(defId) ?: return null
        val ctx = ResolutionContext(state, content, config)
        if (def.followUp || !EncounterCatalog.eligible(ctx, def)) return null
        ctx.encounter = EncounterCatalog.build(ctx, def, ctx.rng(RngStream.ENCOUNTERS), newId(ctx))
        return ctx.toState()
    }

    /** Whether the automatic world event [eventId] has been taken over by an encounter of this catalog. */
    fun replaces(content: ContentCatalog, eventId: String): Boolean = content.encounters.any { it.replacesEvent == eventId }
}
