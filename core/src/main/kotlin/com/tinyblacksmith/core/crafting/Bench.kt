package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.Condition
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.FightEvent
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.FightSetup
import com.tinyblacksmith.core.combat.Objective
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation

/**
 * The forge-side test: one weapon, in the hands of a made-up fighter, against a made-up line, resolved by the same
 * `Fight` a contract uses. It takes no context and returns a report, so it cannot spend the run's RNG, change a save,
 * pay, teach the journal or count as a deed. The seed is a constant and the enemies strike the middle of their range,
 * so a bench fight says nothing about any fight the run will have. Everything made up is named in [Report.synthetic].
 */
object Bench {
    const val SEED = 1L
    const val LEVEL = 3
    const val DEMO_QUALITY = 50

    data class Target(val id: String, val name: String, val about: String, val units: List<String>, val percent: Int = 100)

    data class Ally(val classId: HeroClassId, val name: String, val familyId: WeaponFamilyId, val coreId: MaterialId, val augmentId: MaterialId, val weaponName: String)

    /** [ahead]: the companions stand in front of the wielder. They act first, and the enemy strikes them first. */
    data class Companions(val id: String, val name: String, val about: String, val allies: List<Ally>, val ahead: Boolean = false)

    val targets: List<Target> = listOf(
        Target("armoured", "An armoured line", "Two enemies that raise Guard every round. Shows what gets through Guard and what Guard stops.", listOf("cinder_knight", "bone_warden"), 120),
        Target("backline", "A guarded caster", "A gravecaller mends its dead from behind a bone warden. Shows what reaches the back of the line.", listOf("bone_warden", "hollow_shambler", "gravecaller"), 110),
        Target("attrition", "A long fight", "A warchief and two brutes that hit hard and keep hitting. Shows what Guard, healing and broken Guard are worth.", listOf("ashclaw_brute", "ashclaw_brute", "ashclaw_warchief"), 110),
    )

    val companions: List<Companions> = listOf(
        Companions("alone", "Alone", "Nobody else. Only what the weapon and its wielder do by themselves.", emptyList()),
        Companions("healer", "With a healer", "A Warden with a sapwood staff stands behind the wielder and heals whoever is weakest each round.",
            listOf(Ally(LaunchContent.WARDEN, "Bench Warden", LaunchContent.STAFF, LaunchContent.BRONZE, LaunchContent.VERDANT_SAP, "Sapwood staff"))),
        Companions("frost", "With a frost mage", "A Battlemage with a frost staff acts first and Chills the front enemy twice over. Opens with a Charge and a Mark.",
            listOf(Ally(LaunchContent.BATTLEMAGE, "Bench Battlemage", LaunchContent.STAFF, LaunchContent.BRONZE, LaunchContent.FROST_BLOOM, "Frost staff")), ahead = true),
        Companions("fire", "With a fire mage", "A Battlemage with an ember staff acts first and Burns the front enemy twice over. Opens with a Charge and a Mark.",
            listOf(Ally(LaunchContent.BATTLEMAGE, "Bench Battlemage", LaunchContent.STAFF, LaunchContent.IRON, LaunchContent.EMBER_RESIN, "Ember staff")), ahead = true),
        Companions("archer", "With a marking archer", "A Ranger with a plain storm bow acts first and Marks the enemy that matters every round.",
            listOf(Ally(LaunchContent.RANGER, "Bench Ranger", LaunchContent.BOW, LaunchContent.BRONZE, LaunchContent.STORMGLASS, "Storm bow")), ahead = true),
        Companions("front", "Behind a Guardian", "A Guardian with an obsidian sword holds the front. The sword cracks when the Guardian's Guard breaks.",
            listOf(Ally(LaunchContent.GUARDIAN, "Bench Guardian", LaunchContent.SWORD, LaunchContent.OBSIDIAN, LaunchContent.EMBER_RESIN, "Obsidian sword")), ahead = true),
    )

    /** A rule that fired: how often, and the first chain of events that led to it, cause first. */
    data class Fired(val rule: WeaponRules.Rule, val times: Int, val chain: List<String>)

    /** A rule that did not fire, and the plain reason read off the timeline. */
    data class Silent(val rule: WeaponRules.Rule, val why: String)

    data class Report(
        val title: String, val synthetic: List<String>, val setup: FightSetup, val result: FightResult, val wielderKey: String,
        val fired: List<Fired>, val silent: List<Silent>,
        /** Rules the weapon has only on the town's wall: the bench stands on the road and does not test them. */
        val notTested: List<WeaponRules.Rule>,
    )

    private fun hero(key: String, name: String, classId: HeroClassId) = Hero(HeroId(key), name, "of the bench", classId, LEVEL, 0, 0, 100, emptyList(), null)

    private fun blade(id: String, name: String, family: WeaponFamilyId, core: MaterialId, augment: MaterialId, catalyst: MaterialId?, content: ContentCatalog, config: BalanceConfig, quality: Int = DEMO_QUALITY): Weapon = Weapon(
        id = WeaponId(id), name = name, familyId = family, coreId = core, augmentId = augment, catalystId = catalyst, mode = if (catalyst != null) ForgeMode.ADVANCED else ForgeMode.QUICK, risk = Risk.BALANCED,
        quality = quality, rarity = Forge.forgedRarity(quality, config), power = Forge.powerOf(content, config, family, core, quality, emptyList()), element = content.material(augment).element,
        affixes = emptyList(), flaws = emptyList(), location = WeaponLocation.Storage, forgedEra = 0, forgedDay = 0, craft = WeaponRules.CRAFT,
    )

    /**
     * A recipe shown as a weapon before it exists: its family, metal, augment and catalyst at [quality] (middling unless the caller knows better), with
     * no buff, no flaw and no signature, because those are rolled and the bench will not pretend one.
     */
    fun demonstration(family: WeaponFamilyId, core: MaterialId, augment: MaterialId, catalyst: MaterialId?, content: ContentCatalog, config: BalanceConfig, quality: Int = DEMO_QUALITY): Weapon =
        blade("bench-demo", "${content.material(core).name} ${content.family(family).name.lowercase()} (recipe)", family, core, augment, catalyst, content, config, quality.coerceIn(1, 100))

    /** The class the bench hands a weapon to when the smith has not chosen: the first the family suits. */
    fun defaultWielder(family: WeaponFamilyId, content: ContentCatalog): HeroClassId =
        content.classes.firstOrNull { (content.family(family).classFit[it.id] ?: 0.0) >= 1.0 }?.id ?: content.classes.first().id

    /** The same company as the capability evaluator sees it. */
    fun company(wielder: HeroClassId, companionsId: String, targetId: String, content: ContentCatalog, config: BalanceConfig): Company {
        val combat = content.combat ?: error("This catalog has no combat content")
        val c = companions.first { it.id == companionsId }
        val t = targets.first { it.id == targetId }
        return Company(
            wielder = combat.kitByClass.getValue(wielder).kit,
            allies = c.allies.map { Fighter(combat.kitByClass.getValue(it.classId).kit, WeaponRules.of(blade("a", it.weaponName, it.familyId, it.coreId, it.augmentId, null, content, config), content)) },
            foes = t.units.map { combat.unit(it) },
        )
    }

    fun run(weapon: Weapon, demonstration: Boolean, wielder: HeroClassId, companionsId: String, targetId: String, content: ContentCatalog, config: BalanceConfig): Report {
        val combat = content.combat ?: error("This catalog has no combat content")
        val c = companions.first { it.id == companionsId }
        val t = targets.first { it.id == targetId }
        val className = content.heroClass(wielder).name
        val me = Loadout.combatant(hero("bench-wielder", "Bench $className", wielder), weapon, content, config)
        val allies = c.allies.mapIndexed { i, a -> Loadout.combatant(hero("bench-ally-$i", a.name, a.classId), blade("bench-ally-weapon-$i", a.weaponName, a.familyId, a.coreId, a.augmentId, null, content, config), content, config) }
        val party = if (c.ahead) allies + me else listOf(me) + allies
        val foes = t.units.mapIndexed { i, id -> Loadout.enemy(combat.unit(id), "bench-foe-$i", t.percent) }.map { val mid = (it.strikeMin + it.strikeMax) / 2; it.copy(strikeMin = mid, strikeMax = mid) }
        val setup = FightSetup("${weapon.name} on the bench", party, foes, emptyList(), Objective(), Posture.RECKLESS, config.guild.fight, SEED, combat.unitById)
        val result = Fight.resolve(setup)

        val composed = WeaponRules.of(weapon, content)
        val byId = result.events.associateBy { it.id }
        val partyKeys = party.map { it.key }.toSet()
        val fired = ArrayList<Fired>()
        val silent = ArrayList<Silent>()
        for (rule in composed.rules(home = false)) {
            val mine = result.events.filter { it.effectId == rule.id && (it.source == me.key || it.target == me.key) }
            if (mine.isEmpty()) silent += Silent(rule, why(rule, result.events, me, partyKeys, foes))
            else fired += Fired(rule, mine.mapNotNull { it.parentId }.distinct().size.coerceAtLeast(1), chain(mine.first(), byId))
        }
        return Report(
            title = "${weapon.name}, ${t.name.lowercase()}, ${c.name.lowercase()}",
            synthetic = listOfNotNull(
                "A made-up level $LEVEL $className carries it. Nobody from your town or guild is here.",
                c.allies.takeIf { it.isNotEmpty() }?.let { a -> "The company is made up too: ${a.joinToString(", ") { "${it.name} with ${it.weaponName.lowercase().let { w -> if (w.first() in "aeiou") "an $w" else "a $w" }}" }}." },
                "The enemies are a fixed line that strikes the same every time. No contract or siege will look like this.",
                "The party fights to the end here. On a contract its posture decides when it leaves.",
                "A recipe, not a weapon you own: shown at middling quality with no buff, flaw or signature, because those are rolled.".takeIf { demonstration },
                "Nothing here is paid, learned or recorded.",
            ),
            setup = setup, result = result, wielderKey = me.key, fired = fired, silent = silent, notTested = composed.rules.filter { it.where == WeaponRules.Where.HOME },
        )
    }

    /** The events that led to [e], root first, as the timeline worded them. */
    private fun chain(e: FightEvent, byId: Map<Int, FightEvent>): List<String> {
        val lines = ArrayList<String>()
        var at: FightEvent? = e
        while (at != null) { if (at.text.isNotEmpty()) lines += at.text; at = at.parentId?.let(byId::get) }
        return lines.asReversed().takeLast(4)
    }

    private fun why(rule: WeaponRules.Rule, events: List<FightEvent>, me: Combatant, party: Set<String>, foes: List<Combatant>): String {
        val foeKeys = foes.map { it.key }.toSet()
        fun got(e: FightEvent, any: Set<Res>): Boolean = when (e.kind) {
            EventKind.STAT_GAINED, EventKind.GUARD_GAINED -> e.stat?.let { Res.of(it) in any } == true
            EventKind.HEALED -> Res.HEALING in any
            EventKind.FRACTURED -> Res.FRACTURE in any
            EventKind.HEALTH_PAID -> Res.HEALTH_PAID in any
            else -> false
        }
        fun words(any: Set<Res>) = any.joinToString(" or ") { it.word }
        if (events.none { it.kind == EventKind.ACTION && it.source == me.key && it.target == null }) return "The fight ended before ${me.name} acted."
        val needs = Capabilities.facets(rule.effect, rule.where).filter { !it.engine }.flatMap { it.needs }.distinct()
        for (n in needs) when (n) {
            is Need.Present -> {
                val seen = events.any { e -> got(e, n.any) && when (n.place) { Place.WIELDER -> e.target == me.key; Place.PARTY -> e.target in party; Place.FOE -> e.target in foeKeys } && (!n.byWielder || e.source == me.key) }
                if (!seen) return when {
                    Res.HEALING in n.any && n.place == Place.WIELDER -> "Nobody healed ${me.name}."
                    Res.HEALING in n.any -> "${me.name} healed nobody."
                    Res.FRACTURE in n.any -> "No weapon in the party cracked."
                    Res.HEALTH_PAID in n.any -> "Nobody paid health to a weapon."
                    n.place == Place.WIELDER && Res.GUARD in n.any -> "${me.name} never had Guard for an enemy to break."
                    n.place == Place.WIELDER -> "${me.name} never got ${words(n.any)}."
                    n.place == Place.PARTY -> "Nobody in the party had ${words(n.any)}."
                    n.byWielder -> "${me.name}'s own hit never left ${words(n.any)} on an enemy."
                    else -> "No enemy ever had ${words(n.any)}."
                }
                if (n.beforeHit) return "${words(n.any).replaceFirstChar { it.uppercase() }} was never on the target when ${me.name}'s hit landed."
            }
            is Need.Spent -> if (events.none { it.kind == EventKind.STAT_SPENT && it.stat?.let(Res::of) == n.res && (if (n.byWielder) it.source == me.key else it.source in party) })
                return if (n.res == Res.MARK) "No Mark was there for ${me.name}'s hit to spend." else "Nobody spent ${n.res.word}."
            is Need.FoeIs -> if (foes.none { n.tag in it.tags || n.tag in it.kit.tags }) return when (n.tag) { "elite" -> "None of these enemies is an elite or a leader."; "undead" -> "None of these enemies is one of the Hollowbound."; else -> "None of these enemies is the kind it wants." }
            is Need.HolderIs, is Need.Circumstance -> {}
        }
        // Everything it waits for was there: say which moment never came.
        val t = rule.effect.trigger
        fun mine(kind: EventKind, stat: Stat? = null) = events.any { it.kind == kind && (stat == null || it.stat == stat) && (it.target == me.key || it.source == me.key) }
        return when {
            rule.effect.conditions.any { it is Condition.HolderHealthAtMost } -> "${me.name} was never hurt badly enough."
            t.kind == EventKind.GUARD_BROKEN && !mine(EventKind.GUARD_BROKEN) -> "No enemy broke ${me.name}'s Guard."
            t.kind == EventKind.GUARD_ABSORBED -> "No enemy Guard stopped ${me.name}'s hit."
            t.kind == EventKind.DOWNED -> "No enemy fell in the way it waits for."
            t.kind == EventKind.STAT_GAINED && t.stat != null -> "${me.name} never held enough ${Res.of(t.stat).word} at once."
            me.strikeMin <= 0 -> "${me.name} never landed a hit."
            else -> "Its moment never came in this fight."
        }
    }
}
