package com.tinyblacksmith.core.config

/**
 * v17: every number of living heroes (docs/Tiny_Blacksmith_Living_Heroes_Final_Design.md). All PROPOSED designer defaults
 * (final design 3.2), tuned from evidence. One nested group, one slot of [BalanceConfig]. A legacy era reads none of it.
 */
data class LivingConfig(
    /** Significant moments kept per hero (final design 11). */
    val reasonsKept: Int = 10,

    // Hooks (7.3). Relative weights when a hero is generated; a hook that cannot be made valid is skipped for another.
    val hookWeights: Map<String, Int> = mapOf("GRUDGE" to 30, "DEBT" to 20, "LOST_WEAPON" to 15, "OATH" to 20, "KIN" to 15),
    val debtAmount: Int = 40,
    /** A debt falls due this many days after the first siege at the earliest. */
    val debtAfterFirstSiegeDays: Int = 3,
    val debtTermDays: Int = 10,
    /** A grudge with no offer for this many days is resolved with a factual note. */
    val grudgeIdleDays: Int = 10,
    /** A lost family weapon is an ordinary one: this quality, plain metal. */
    val heirloomQuality: Int = 42,
    /** Chance a hook-linked ambition (Avenger, Heirloom) replaces the drawn one, and that a guild ambition (Captain, Name) does. */
    val hookAmbitionChance: Double = 0.5,
    val guildAmbitionChance: Double = 0.25,

    // The counter (8.1, 8.2).
    val memberVisitsPerDay: Int = 1,
    /** Attachment in utility points: per siege held with the weapon (capped), for having recovered it, per point of its fame (capped). */
    val attachmentPerSiege: Double = 0.35,
    val attachmentSiegeCap: Int = 3,
    val attachmentRecovered: Double = 0.7,
    val attachmentPerFame: Double = 0.05,
    val attachmentFameCap: Int = 6,

    // Loan returns (8.3).
    /** The member's own weapon must be worth at least this percent of the loan, in their hands and against the known threat. */
    val returnStrongerPercent: Int = 115,
    /** A loan below this condition is worn enough to hand back for a sound weapon of one's own. */
    val returnWornCondition: Int = 25,

    // Specialities (9.2).
    /** Each evidence family is capped here, so an old path is not destiny. */
    val evidenceCap: Int = 6,
    /** A lead of at least this many points is chosen for certain; a closer one is a bounded draw. */
    val leanSureMargin: Int = 3,

    // Opportunities and concerns (10).
    val offerCooldownDays: Int = 5,
    val sponsoredOnBoard: Int = 1,
    val missesForDeparture: Int = 3,
    val departureNoticeMornings: Int = 3,
    /** A won public deed (opp_reputation_deed) adds this much reputation; a lesson this much experience; a healer for a friend costs this much. */
    val deedReputation: Int = 2,
    val lessonXp: Int = 25,
    val healerFee: Int = 25,

    // Sieges (13.4).
    /** Forge health completed structural hits may add to one siege, on top of the ordinary loss and within its maximum. */
    val structuralCapPerSiege: Int = 12,
    /** Extra counter utility (on the 0.5 purchase threshold's scale) a weapon has for a member who wants one that stops a mending enemy (opp_troll_test, opp_silver_saving). */
    val mendingAnswerUtility: Double = 0.4,
)
