package com.example.blacksmithproject

import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.heroes.Appearance
import com.tinyblacksmith.core.model.Ambition
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.Hook
import com.tinyblacksmith.core.model.HookKind
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.OathKind
import com.tinyblacksmith.core.model.ReasonRecord
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation

/**
 * Debug builds only: a guild era on day 1 whose members and recruits carry the longest names of the content pools and
 * every kind of Hook, for the hero sheet, the roster and the recruit rows (living heroes final design 20.8). Built from a
 * real engine run, then each hero is given a people, a name and a Hook by hand: a constructed case, not natural play.
 * The scenario menu loads it as a save; the previews render the same state.
 */
object LivingHeroFixtures {
    val engine = GameEngine()

    /** Member 1, member 2, recruits in board order: the heroes whose sheets the evidence opens. */
    class Fixture(val state: GameState, val members: List<HeroId>, val recruits: List<HeroId>, val townsfolk: HeroId)

    fun build(seed: Long = 41): Fixture {
        val fresh = engine.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE)
        val s = (engine.handle(fresh, Command.ChooseOpening(fresh.guild!!.openingOffer.first().defId)) as CommandOutcome.Accepted).state
        val g = s.guild!!
        val members = g.members.map { it.heroId }
        val recruits = g.candidates.map { it.heroId }
        val inGuild = (members + recruits).toSet()
        val town = s.heroes.values.filter { it.isAlive && it.id !in inGuild }.sortedBy { it.id.value }
        val creditor = town.first()
        val keeper = town[1]
        val heroes = s.heroes.toMutableMap()
        fun edit(id: HeroId, f: (Hero) -> Hero) { heroes[id] = f(heroes.getValue(id)) }
        fun Hero.silhouette() = copy(appearance = Appearance.silhouette(classId))
        val first = members[0]
        val second = members.getOrElse(1) { members[0] }
        edit(first) {
            it.copy(name = "Sigrin", surname = "Smokechisel", raceId = "dwarf", cultureId = "dwarf", backgroundId = "bg_mason", kinId = "kin-fixture", ambition = Ambition.CAPTAIN,
                hook = Hook(HookKind.DEBT, 1, creditor = creditor.fullName, creditorHeroId = creditor.id, amount = 40, dueDay = s.town.nextSiegeDay + 3),
                reasons = listOf(
                    ReasonRecord(1, "joined", "Joined the guild under the Town's Last Hope charter."),
                    ReasonRecord(1, "kin", "Hilda Smokechisel came to Emberfall looking for work."),
                ),
            ).silhouette()
        }
        if (second != first) edit(second) {
            it.copy(name = "Keshra", surname = "Obsidianhorn", raceId = "dragonkin", cultureId = "dragonkin", backgroundId = "bg_scholar", ambition = Ambition.AVENGER,
                hook = Hook(HookKind.GRUDGE, 1, factionId = s.factions.keys.minBy { k -> k.value }, background = true), reasons = emptyList(),
            ).silhouette()
        }
        recruits.getOrNull(0)?.let { id ->
            edit(id) {
                it.copy(name = "Tansy", surname = "Saffronfield of Willow Crossing", raceId = "halfling", cultureId = "halfling", backgroundId = "bg_cook",
                    hook = Hook(HookKind.OATH, 1, oath = OathKind.PLAIN_WORK)).silhouette()
            }
        }
        recruits.getOrNull(1)?.let { id ->
            edit(id) {
                it.copy(name = "Hilda", surname = "Smokechisel", raceId = "dwarf", cultureId = "dwarf", backgroundId = "bg_militia", kinId = "kin-fixture",
                    hook = Hook(HookKind.KIN, 1, kinHeroId = first)).silhouette()
            }
        }
        recruits.getOrNull(2)?.let { id ->
            edit(id) { h ->
                h.copy(name = "Fizzi", surname = "Cindergoggle", raceId = "gnome", cultureId = "gnome", backgroundId = "bg_apprentice",
                    hook = Hook(HookKind.OATH, 1, oath = OathKind.FAVOURED_FAMILY, familyId = engine.content.heroClass(h.classId).preferredFamilies.first())).silhouette()
            }
        }
        // A townsperson whose family weapon is a real record in Lost: a copy of a weapon of the run, never in anyone's hands.
        val heirloom = s.weapons.values.firstOrNull()?.copy(id = WeaponId("w_fixture_heirloom"), location = WeaponLocation.Lost(1, "lost family weapon of Aelith Twilightreed"), history = emptyList())
        edit(keeper.id) {
            it.copy(name = "Aelith", surname = "Twilightreed", raceId = "elf", cultureId = "elf", backgroundId = "bg_relic_keeper",
                hook = Hook(HookKind.LOST_WEAPON, 1, weaponId = heirloom?.id, background = true))
        }
        val weapons = if (heirloom == null) s.weapons else s.weapons + (heirloom.id to heirloom)
        return Fixture(s.copy(heroes = heroes, weapons = weapons), members, recruits, keeper.id)
    }
}
