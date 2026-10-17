package com.tinyblacksmith.core

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.LaunchContent
import java.lang.reflect.Modifier
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Two guards so a version number cannot be forgotten: the launch catalog and the default balance are hashed and the
 * hash is pinned per version. Changing either without raising its version fails here.
 *
 * After an intended change: raise `LaunchContent.catalog.version` or `BalanceConfig.version`, run the test, and add
 * the hash it prints as a NEW row for the new version (older rows stay as the record). Editing the hash of an
 * existing row is right only when the fingerprint itself changed (this file), never for a changed number.
 *
 * What is hashed: every field of every definition, by field name in sorted order, doubles by their bits, maps by
 * sorted key, lists in their order (the engine picks from them by index). Display text is not content: of the
 * fields in [prose] only presence or count is hashed, so a reworded description needs no version. Name pools are
 * hashed in full, because the names a run generates come from them.
 */
class VersionFingerprintTest {
    private val catalogPins = mapOf(
        2 to "e5e5b96b392777abd63e6f7237fd96f5eee92411942f172035ba0c5c4adb6f9b",
        3 to "d9befd94929b3268c60ecdec67b8de06ec841ef8ec5e34ae6512b74894263db6",  // 120 first names, 96 surnames (T3.2); appearance keys per class (T3.3); Guild Patronage as its own effect (T3.6): one unreleased step, re-pinned from aa0a71b8...
        4 to "283e8d4d3737b81228c15adbbc3ce72468462623c8f303a67c5f640d55dc39fe",
        // 5: the guild (rules 5): combat kits, the rule every element, affix, catalyst, family and six signatures give a blade, enemy units, eight party relics, contracts, charters, member traits. Re-pinned while the update is unreleased.
        5 to "34c5343f991c36e71bae17bbdad7c3819b8dad04a325ff96c7bff4e251c24861",  // morning visitors, workshop relics and siege traits join the catalog (gameplay depth, rules 4)
        // 6: run variety (rules 6): opening packages, siege preparations, contract modifiers, the siege's jobs, two pacts
        // Re-pinned while rules 6 is unreleased (from d047912e...): four contract modifiers that change the decision and their fields, the watch on a rescue, a second class a charter offers, Licensed Bad Ideas, three opening drawbacks in gold, the Hunt's numbers.
        // 6, re-pinned while unreleased: with the deep forging catalog (a fight rule for each metal, ingredient notes, interaction paths, Void Ink and Dragon Oil rewritten for new weapons).
        // 6, re-pinned for the fingerprint only (from 03d44c0f...): an opening's `costNote` is display text, hashed by presence like `pitch`. No number moved.
        // 6, re-pinned for the fingerprint only (from d6a80e4e...): the clarity pass (docs/WRITING_GUIDE.md) reworded a siege preparation's
        // `counters` and an opening's `plan`, so those and the other display fields of both (`sabotageBrief`, `build`, `drawback`) are hashed by presence. No number moved.
        // 6, re-pinned while unreleased (from e921ee0c...): owner playtest, the tier-3-and-up materials priced about 2.5 times higher, the caravan master and the deep road contract (rules 7).
        // 6, re-pinned for the fingerprint only (from dbe74401...): `constraintKind` marks each metal's and ingredient note's
        // caveat as a drawback, a condition or a comparison, so a card signs it truly (UI polish). Display only; no number moved.
        6 to "185758a6a844edd1ea4293637a77648709dc0eb63f5b33d261ec6b26deae4baa",
        // 7: living heroes (the twelve peoples, backgrounds, bynames, portrait peoples). Re-pinned inside the unreleased step as the bestiary lands.
        // 7, re-pinned inside the unreleased step (from 4be970f8...): the peoples, the bestiary types, the combat grammar (channels, Poison, returns, structural hits) and the two living-era bonuses, Disrupting and Purifying.
        7 to "f9f000a698d62f8b101d00fc78c50476801e77169106eb00f8d2bde74836ec0d",
    )
    private val balancePins = mapOf(
        5 to "34c5343f991c36e71bae17bbdad7c3819b8dad04a325ff96c7bff4e251c24861",
        6 to "adb451f9d456482d788d612daefebdbda892de44215fe4a3877152490d6c8e60",
        7 to "daf9ca7290dac60a17921dde7e37676a45dab7db64bdffe5a093af7f94d5f9d7",  // re-pinned inside the unreleased M3 step: T3.1 pinned 66214786..., T3.4 (33af5dfb...) changed the town and pressure numbers, T3.6 (33499217...) added patronageStipend, T3.8 the two rout numbers
        // M4, one unreleased step, re-pinned inside it: standing wants (T4.1, c2e3b145...), the sidegrade gate and siege demand (T4.2, 8c796571...), the two rumour numbers (T4.3, d16456a5...), commission situations (T4.6, c644ab76...).
        // T5.4 re-pinned it for structure only: the resolvers' inline numbers became fields (`combat`, `worldEvents`, more of `heroLife`, `customers`, `legacyTracks`) with the values they had; no outcome moved, so the version did not.
        // T6.3a re-pinned it again (from 3c90f806...) for the four `saveGrowth` numbers: they bound what a save keeps and move no outcome (SaveGrowthTest plays each on and off).
        8 to "364bff16c8f448ea797def569dd3ae5f2aa16ffc828fd62163a48456d2d7d583",
        // 9: `saveGrowth.scrapBladesPerMaterial`, the return of the new bulk Scrap command. No outcome of balance 8 moves and no bot uses it; a player gains an option.
        9 to "77049208be3fb8e8c19ecd2ad2437c9a1f7c42495040dcb192ce0af6205a9ea8",
        // 10: the `depth` group (visitors, relics, siege traits, the committed besieger). All provisional.
        10 to "1368584cbd01a48d96bfd382087d9692ff9efa19540cb57131c85215f9d0cf0d",
        // 11: the `guild` group. A classic run reads none of it (GoldenStateTest holds the rules-4 goldens under rules 5). Re-pinned while the update is unreleased.
        11 to "5439bf6812f0b4526b13fa937b2b3c56accae43135bd9c5d769266d0712072cd",
        // rules 6: Legendary by Heartfire or deeds, risk profiles, the guild's openings, preparations and siege numbers (docs/RUN_VARIETY_LEDGER.md)
        12 to "6f3ad6d6c7cb3aff77b7610a5c3c5964aaec16d97787f451bb4ab7263958a0c3",
        // 13: Epic needs fine metal (`fineCoreTier`), the supplier carries Starsteel and Moonsteel from a later day (`coreTierFromDay`), a classic siege leaves Heartfire from the fourth on, the guild siege base 186.
        13 to "77f40409eeff8e5ec4ec8595d33c3170bf6f60c466dfb1158740325dc122e85e",
        // 14: the guild siege base 190, measured with the metals' fight rules in the catalog.
        14 to "d2bbbd8006abe14140974b640e35f41c2221e346820700bc2aa57addde50271d",
        // 15: review 2026-10-14 fixes, one unreleased step, re-pinned inside it: the blessing offer's days (0ef78325...), the guild's wall work extra (dd718587...), the three gift numbers.
        // 15, re-pinned again inside the unreleased step (from bbbca78a...): offers on a premium listing (`customers.offers`, `offerReputation`, `offerUpToPercent`) and Heartfire from a classic warlord siege (`legend.classicWarlordHeartfire`).
        15 to "11959662224a023af282cb8a4942b96fdf887b16150f76f01908bbe1f80fe1bc",
        // 16: owner playtest. The quality each core metal holds, the deep road replacing the supplier's days (`roadStageByTier`, the wait and the first crate), contract gold three times its card.
        16 to "e8ad9fc5eff7628e31a2c77a140e8470bb5f2172f087828b83b03eaa391dea3f",
        // 17: living heroes (`LivingConfig`). Re-pinned inside the unreleased step as its numbers land.
        // 17, re-pinned inside the unreleased step (from 2c5c9a7f...): `living` (with the mending answer) and `guild.fight` gains Poison (its cap and the two healing numbers). No classic outcome moves.
        17 to "8d0476a06a4405696a7404aed023c2acaaf2dfbcec613f91aff50fc8dedd66bc",
    )

    private val prose = setOf("name", "description", "flavor", "siegeName", "warlordName", "encounterNames", "eliteNames", "counsel", "role", "forgeHint", "pattern", "telegraph", "prep", "failure", "pitch", "advantage", "constraint", "play", "rivalNames", "where", "costNote", "counters", "sabotageBrief", "plan", "build", "drawback")

    private fun canonical(value: Any?, field: String? = null): String = when (value) {
        null -> "null"
        is String -> if (field in prose) "text" else "\"$value\""
        is Double -> "d${value.toBits()}"
        is Int, is Long, is Boolean -> value.toString()
        is Enum<*> -> value.name
        is Pair<*, *> -> "(${canonical(value.first)},${canonical(value.second)})"
        is Map<*, *> -> value.entries.map { "${canonical(it.key)}=${canonical(it.value)}" }.sorted().joinToString(",", "{", "}")
        is Set<*> -> value.map { canonical(it) }.sorted().joinToString(",", "<", ">")
        is List<*> -> if (field in prose) "texts${value.size}" else value.joinToString(",", "[", "]") { canonical(it) }
        else -> {
            check(value.javaClass.name.startsWith("com.tinyblacksmith.core.")) { "No canonical form for ${value.javaClass.name}" }
            value.javaClass.declaredFields
                .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic && !it.name.endsWith("ById") }  // the catalog's lookup maps repeat its lists
                .sortedBy { it.name }
                .joinToString(",", "${value.javaClass.simpleName}(", ")") { f -> f.isAccessible = true; "${f.name}=${canonical(f.get(value), f.name)}" }
        }
    }

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Test
    fun catalogFingerprintMatchesItsVersion() {
        val catalog = LaunchContent.catalog
        val first = catalog.materials.first()
        assertNotEquals(canonical(catalog), canonical(catalog.copy(materials = listOf(first.copy(price = first.price + 1)) + catalog.materials.drop(1))), "a changed number is seen")
        assertEquals(canonical(catalog), canonical(catalog.copy(materials = listOf(first.copy(flavor = "reworded")) + catalog.materials.drop(1))), "reworded text is not")
        assertEquals(catalogPins[catalog.version], sha(canonical(catalog)),
            "the launch catalog changed: raise its version and pin the new hash as a new row (content version ${catalog.version})")
    }

    @Test
    fun balanceFingerprintMatchesItsVersion() {
        val config = BalanceConfig.DEFAULT
        assertNotEquals(canonical(config), canonical(config.copy(heroLife = config.heroLife.copy(guildXp = config.heroLife.guildXp + 1))), "a number in a nested group is seen")
        assertEquals(balancePins[config.version], sha(canonical(config)),
            "BalanceConfig changed: raise BalanceConfig.version and pin the new hash as a new row (balance version ${config.version})")
    }
}
