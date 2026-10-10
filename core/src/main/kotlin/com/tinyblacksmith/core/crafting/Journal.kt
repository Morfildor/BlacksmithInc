package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.market.QualityBand
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.model.Journal as JournalModel

/**
 * Experiment Journal (GDD 4.6): UNKNOWN -> OBSERVED -> UNDERSTOOD. Knowledge lives in the legacy profile so it
 * survives death. Repeating a fully understood experiment never grants further discovery credit.
 */
object Journal {
    fun recordExperiment(ctx: ResolutionContext, core: MaterialId, augment: MaterialId, family: WeaponFamilyId, affinity: Int) {
        val keys = listOf(JournalModel.coreAugmentKey(core, augment), JournalModel.augmentFamilyKey(augment, family))
        var journal = ctx.legacy.journal
        for (key in keys) {
            val current = journal.state(key)
            if (current == KnowledgeState.UNDERSTOOD || current == KnowledgeState.SIGNATURE_DISCOVERED) continue
            val experiments = (journal.experiments[key] ?: 0) + 1 + ctx.blessingMagnitude(BlessingEffect.DISCOVERY_BONUS)  // Runic Insight
            val next = when {
                experiments >= ctx.config.experimentsToUnderstand -> KnowledgeState.UNDERSTOOD
                else -> KnowledgeState.OBSERVED
            }
            journal = journal.copy(interactions = journal.interactions + (key to next), experiments = journal.experiments + (key to experiments))
            if (next != current) {
                val label = describeAffinity(affinityFor(ctx.content, key))
                val subject = subjectName(ctx.content, key)
                if (next == KnowledgeState.UNDERSTOOD) {
                    ctx.discoveriesThisRun += 1
                    ctx.emit(EventType.DISCOVERY, 3, "Journal: $subject is now understood — $label.", data = mapOf("key" to key))
                } else {
                    ctx.emit(EventType.DISCOVERY, 1, "Journal: $subject observed — $label.", data = mapOf("key" to key))
                }
            }
        }
        ctx.legacy = ctx.legacy.copy(journal = journal)
    }

    /** Signature transformation succeeded (GDD 4.5): recorded permanently; only the first time counts as a discovery. */
    fun recordSignatureDiscovered(ctx: ResolutionContext, def: SignatureDef): Boolean {
        val journal = ctx.legacy.journal
        if (journal.state(def.journalKey) == KnowledgeState.SIGNATURE_DISCOVERED) return false
        // Found is the whole ladder: name, flavour and the full recipe.
        ctx.legacy = ctx.legacy.copy(journal = journal.copy(
            interactions = journal.interactions + (def.journalKey to KnowledgeState.SIGNATURE_DISCOVERED), signatureClues = journal.signatureClues + (def.journalKey to ClueRung.ALL),
        ))
        ctx.discoveriesThisRun += 1
        return true
    }

    /**
     * The rungs of [def]'s ladder the journal holds. A found signature holds all four; an entry that was only "observed"
     * by a profile older than the ladder holds the first.
     */
    fun rungs(journal: JournalModel, def: SignatureDef): Set<ClueRung> {
        val state = journal.state(def.journalKey)
        val bits = when (state) {
            KnowledgeState.SIGNATURE_DISCOVERED -> ClueRung.ALL
            KnowledgeState.UNKNOWN -> journal.signatureClues[def.journalKey] ?: 0
            else -> (journal.signatureClues[def.journalKey] ?: 0) or ClueRung.RECIPE.bit
        }
        return ClueRung.entries.filterTo(LinkedHashSet()) { bits and it.bit != 0 }
    }

    /**
     * The rung a new clue adds: the base recipe first; then the lowest rung not yet held among the conditions the
     * attempt [missed]; when it missed none of those (a rumour, or a recipe that was exact and did not take), the
     * lowest rung not yet held. Null when the ladder is complete.
     */
    fun nextRung(journal: JournalModel, def: SignatureDef, missed: List<SignatureDef.Miss> = emptyList()): ClueRung? {
        val have = rungs(journal, def)
        if (ClueRung.RECIPE !in have) return ClueRung.RECIPE
        return missed.map { it.rung }.firstOrNull { it !in have } ?: ClueRung.entries.firstOrNull { it !in have }
    }

    /** Writes [rung] into the legacy journal; the entry is at least "observed" from then on. Draws nothing. */
    fun earn(ctx: ResolutionContext, def: SignatureDef, rung: ClueRung) {
        val journal = ctx.legacy.journal
        val state = journal.state(def.journalKey)
        ctx.legacy = ctx.legacy.copy(journal = journal.copy(
            interactions = if (state == KnowledgeState.UNKNOWN) journal.interactions + (def.journalKey to KnowledgeState.OBSERVED) else journal.interactions,
            signatureClues = journal.signatureClues + (def.journalKey to ((journal.signatureClues[def.journalKey] ?: 0) or rungs(journal, def).sumOf { it.bit } or rung.bit)),
        ))
    }

    /**
     * Right base recipe, and the metal did not answer: every such forge earns one rung until the ladder is complete
     * (it used to answer once and then fall silent). Descriptive, never a percentage.
     */
    fun recordSignatureClue(ctx: ResolutionContext, def: SignatureDef, missed: List<SignatureDef.Miss>) {
        if (ctx.legacy.journal.state(def.journalKey) == KnowledgeState.SIGNATURE_DISCOVERED) return
        val rung = nextRung(ctx.legacy.journal, def, missed) ?: return
        earn(ctx, def, rung)
        val core = ctx.content.material(def.coreId).name
        val augment = ctx.content.material(def.augmentId).name
        // The family is named: two signatures can share a core and an augment (an iron and ember sword, an iron and ember axe).
        val family = ctx.content.family(def.familyId).name.lowercase()
        ctx.emit(EventType.DISCOVERY, 3, "The $augment sang against the $core of the $family: ${clue(def, rung, ctx.config)}.", data = mapOf("key" to def.journalKey, "rung" to rung.name))
    }

    /** One authored phrase per catalyst (the catalysts' identity, G07), and one for a recipe that takes none. */
    fun catalystPhrase(catalystId: MaterialId?): String = when (catalystId) {
        null -> "wants nothing added"
        LaunchContent.BINDING_SALT -> "wants something to bind it"
        LaunchContent.RUNESTONE_SHARD -> "wants a word cut into it"
        LaunchContent.DRAGON_OIL -> "wants a hotter fire"
        LaunchContent.VOID_INK -> "wants a rule rewritten"
        else -> "wants something added"
    }

    /** What any catalyst does at the forge today, said plainly for the forge panel. */
    const val CATALYST_EFFECT = "Any catalyst steadies the forge: finer work, more brilliant pieces, fewer flaws. Some recipes ask for one by name."

    /** What one rung says about [def]. */
    fun clue(def: SignatureDef, rung: ClueRung, config: BalanceConfig): String = when (rung) {
        ClueRung.RECIPE -> "it hides something more"
        ClueRung.CATALYST -> "it ${catalystPhrase(def.catalystId)}"
        ClueRung.TEMPER -> when (def.risk) {
            Risk.SAFE -> "it wants more patience"
            Risk.BALANCED -> "it wants a steady temper, neither patient nor daring"
            Risk.RECKLESS -> "it wants more daring"
            null -> "it takes any temper"
        }
        ClueRung.QUALITY -> "it wants finer work: at least ${QualityBand.of(def.minQuality, config).word}"
    }

    fun affinityFor(content: ContentCatalog, key: String): Int {
        if (key.startsWith("sig:")) return 0
        val parts = key.substring(3).split("|")
        return if (key.startsWith("ca:")) content.coreAugmentAffinity[MaterialId(parts[0]) to MaterialId(parts[1])] ?: 0
        else content.augmentFamilyAffinity[MaterialId(parts[0]) to WeaponFamilyId(parts[1])] ?: 0
    }

    fun subjectName(content: ContentCatalog, key: String): String {
        if (key.startsWith("sig:")) {
            val def = SignatureCatalog.byId[key.substring(4)] ?: return "Signature"
            return "${content.material(def.coreId).name} + ${content.material(def.augmentId).name} ${content.family(def.familyId).name}"
        }
        val parts = key.substring(3).split("|")
        return if (key.startsWith("ca:")) "${content.material(MaterialId(parts[0])).name} + ${content.material(MaterialId(parts[1])).name}"
        else "${content.material(MaterialId(parts[0])).name} on ${content.family(WeaponFamilyId(parts[1])).name}s"
    }

    /** Descriptive, never numeric (GDD round 9 disclosure rule). */
    fun describeAffinity(affinity: Int): String = when {
        affinity >= 7 -> "excellent affinity"
        affinity >= 4 -> "promising match"
        affinity >= 1 -> "faint harmony"
        affinity == 0 -> "neutral"
        affinity >= -2 -> "slight friction"
        else -> "poor match"
    }

    /** Hint shown live in the forge panel: hides the real relationship until the journal knows it. */
    fun hint(journal: JournalModel, content: ContentCatalog, key: String): String {
        val state = journal.state(key)
        return if (key.startsWith("sig:")) signatureHint(journal, key) else affinityHint(state, content, key)
    }

    /** The rungs earned and nothing else: "Hides something more; it wants a hotter fire". A found signature is named. No odds, no numbers. */
    private fun signatureHint(journal: JournalModel, key: String, config: BalanceConfig = BalanceConfig.DEFAULT): String {
        val def = SignatureCatalog.byId[key.substring(4)] ?: return "Unknown"
        if (journal.state(key) == KnowledgeState.SIGNATURE_DISCOVERED) return "Signature: ${def.name} — ${def.flavor}"
        val have = rungs(journal, def)
        if (ClueRung.RECIPE !in have) return "Unknown"
        return (listOf("Hides something more") + (have - ClueRung.RECIPE).map { clue(def, it, config) }).joinToString("; ")
    }

    /** The core and augment of a "ca:" journal row, for "Use" on an understood row; null for any other key. */
    fun coreAugmentOf(key: String): Pair<MaterialId, MaterialId>? =
        if (key.startsWith("ca:")) key.substring(3).split("|").takeIf { it.size == 2 }?.let { MaterialId(it[0]) to MaterialId(it[1]) } else null

    private fun affinityHint(state: KnowledgeState, content: ContentCatalog, key: String): String = when (state) {
        KnowledgeState.UNKNOWN -> "Unknown"
        KnowledgeState.OBSERVED -> {
            val a = affinityFor(content, key)
            if (a > 0) "Seems promising" else if (a < 0) "Seems uneasy" else "Seems neutral"
        }
        KnowledgeState.UNDERSTOOD, KnowledgeState.SIGNATURE_DISCOVERED -> describeAffinity(affinityFor(content, key)).replaceFirstChar { it.uppercase() }
    }
}
