package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.blacksmithproject.LivingHeroFixtures
import com.example.blacksmithproject.ui.detail.HeroDetailContent
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.example.blacksmithproject.ui.theme.Space

/*
 * Debug builds only: the living heroes' identity on the hero sheet and the roster, over `LivingHeroFixtures` (the longest
 * names of the pools, peoples without portraits, every kind of Hook), at the narrow phone width and three text sizes.
 */

@Composable
private fun SheetOf(pick: (LivingHeroFixtures.Fixture) -> com.tinyblacksmith.core.model.HeroId) {
    val f = remember { LivingHeroFixtures.build() }
    val detail = remember(f) { LivingHeroFixtures.engine.heroDetail(f.state, pick(f))!! }
    BlacksmithProjectTheme { Surface { HeroDetailContent(detail, onOpenHero = {}, onOpenItem = {}, onDismiss = {}) } }
}

@Preview(name = "Member sheet: dwarf, debt, kin, moments", widthDp = 360, heightDp = 1600)
@Composable
private fun MemberSheetPreview() = SheetOf { it.members[0] }

@Preview(name = "Member sheet at 1.3x", widthDp = 360, heightDp = 1800, fontScale = 1.3f)
@Composable
private fun MemberSheetLargePreview() = SheetOf { it.members[0] }

@Preview(name = "Member sheet head at 2.0x", widthDp = 360, heightDp = 900, fontScale = 2f)
@Composable
private fun MemberSheetHugePreview() = SheetOf { it.members.last() }

@Preview(name = "Recruit sheet: halfling with a byname and an oath", widthDp = 360, heightDp = 1400)
@Composable
private fun RecruitSheetPreview() = SheetOf { it.recruits.first() }

@Preview(name = "Townsperson sheet: a family weapon lost", widthDp = 360, heightDp = 1200)
@Composable
private fun TownsfolkSheetPreview() = SheetOf { it.townsfolk }

@Composable
private fun Roster() {
    val f = remember { LivingHeroFixtures.build() }
    val ui = remember(f) { LivingHeroFixtures.engine.guildUi(f.state)!! }
    val actions = GuildActions(enabled = true, openHero = {}, openContract = {}, confirm = {}, assign = {}, send = {})
    BlacksmithProjectTheme { Surface { Column(Modifier.padding(horizontal = Space.md)) { companyRows(ui, actions).forEach { it.content() } } } }
}

@Preview(name = "Roster: long names, silhouettes", widthDp = 360, heightDp = 900)
@Composable
private fun RosterPreview() = Roster()

@Preview(name = "Roster at 1.3x", widthDp = 360, heightDp = 1100, fontScale = 1.3f)
@Composable
private fun RosterLargePreview() = Roster()
