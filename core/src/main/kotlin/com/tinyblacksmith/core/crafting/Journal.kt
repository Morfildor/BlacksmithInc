package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.ContentCatalog
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
        ctx.legacy = ctx.legacy.copy(journal = journal.copy(interactions = journal.interactions + (def.journalKey to KnowledgeState.SIGNATURE_DISCOVERED)))
        ctx.discoveriesThisRun += 1
        return true
    }

    /** Right base recipe, wrong condition (or a failed roll): one descriptive clue per signature, never a percentage. */
    fun recordSignatureClue(ctx: ResolutionContext, def: SignatureDef, miss: SignatureDef.Miss?) {
        val journal = ctx.legacy.journal
        if (journal.state(def.journalKey) != KnowledgeState.UNKNOWN) return
        ctx.legacy = ctx.legacy.copy(journal = journal.copy(interactions = journal.interactions + (def.journalKey to KnowledgeState.OBSERVED)))
        val content = ctx.content
        val core = content.material(def.coreId).name
        val augment = content.material(def.augmentId).name
        val tail = when (miss) {
            SignatureDef.Miss.CATALYST -> "something is missing."
            SignatureDef.Miss.RISK -> "it wanted ${riskWish(def.risk)}."
            SignatureDef.Miss.QUALITY -> "it wanted finer work."
            null -> "something greater flickered and faded. Try again."
        }
        ctx.emit(EventType.DISCOVERY, 3, "The $augment sang against the $core — $tail", data = mapOf("key" to def.journalKey))
    }

    private fun riskWish(risk: Risk?): String = when (risk) {
        Risk.SAFE -> "more patience"
        Risk.BALANCED -> "a steadier temper"
        Risk.RECKLESS -> "more daring"
        null -> "nothing more"
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
        return if (key.startsWith("sig:")) signatureHint(state, key) else affinityHint(state, content, key)
    }

    /** Signature clue without numbers: the base recipe is known, the condition is described, never spelled out. */
    private fun signatureHint(state: KnowledgeState, key: String): String {
        val def = SignatureCatalog.byId[key.substring(4)] ?: return "Unknown"
        if (state == KnowledgeState.UNKNOWN) return "Unknown"
        if (state == KnowledgeState.SIGNATURE_DISCOVERED) return "Signature: ${def.name} — ${def.flavor}"
        val wants = listOfNotNull(
            def.catalystId?.let { "a steadying hand" },
            def.risk?.let { riskWish(it) },
            "fine work",
        )
        return "Hides something more; it wants ${wants.joinToString(" and ")}"
    }

    private fun affinityHint(state: KnowledgeState, content: ContentCatalog, key: String): String = when (state) {
        KnowledgeState.UNKNOWN -> "Unknown"
        KnowledgeState.OBSERVED -> {
            val a = affinityFor(content, key)
            if (a > 0) "Seems promising" else if (a < 0) "Seems uneasy" else "Seems ordinary"
        }
        KnowledgeState.UNDERSTOOD, KnowledgeState.SIGNATURE_DISCOVERED -> describeAffinity(affinityFor(content, key)).replaceFirstChar { it.uppercase() }
    }
}
