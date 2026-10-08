package com.example.blacksmithproject.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.R
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.model.BlessingId
import com.tinyblacksmith.core.model.FactionId
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.Weapon

/**
 * Sprite layer for the generated pixel art (tools/pixelart/generate_assets.py, docs/ART_MANIFEST.md).
 * Decorative only: every sprite is scaled with nearest-neighbour filtering, carries no essential text, and never
 * touches gameplay state or RNG (GDD §14, §15.1).
 */
object Sprites {
    fun weapon(w: Weapon): Int = weapon(w.familyId, w.coreId)

    private val weapons: Map<String, Map<String, Int>> = mapOf(
        "sword" to mapOf("iron" to R.drawable.weapon_sword_iron, "bronze" to R.drawable.weapon_sword_bronze, "silver" to R.drawable.weapon_sword_silver, "moonsteel" to R.drawable.weapon_sword_moonsteel, "obsidian" to R.drawable.weapon_sword_obsidian, "starsteel" to R.drawable.weapon_sword_starsteel),
        "axe" to mapOf("iron" to R.drawable.weapon_axe_iron, "bronze" to R.drawable.weapon_axe_bronze, "silver" to R.drawable.weapon_axe_silver, "moonsteel" to R.drawable.weapon_axe_moonsteel, "obsidian" to R.drawable.weapon_axe_obsidian, "starsteel" to R.drawable.weapon_axe_starsteel),
        "bow" to mapOf("iron" to R.drawable.weapon_bow_iron, "bronze" to R.drawable.weapon_bow_bronze, "silver" to R.drawable.weapon_bow_silver, "moonsteel" to R.drawable.weapon_bow_moonsteel, "obsidian" to R.drawable.weapon_bow_obsidian, "starsteel" to R.drawable.weapon_bow_starsteel),
        "dagger" to mapOf("iron" to R.drawable.weapon_dagger_iron, "bronze" to R.drawable.weapon_dagger_bronze, "silver" to R.drawable.weapon_dagger_silver, "moonsteel" to R.drawable.weapon_dagger_moonsteel, "obsidian" to R.drawable.weapon_dagger_obsidian, "starsteel" to R.drawable.weapon_dagger_starsteel),
        "spear" to mapOf("iron" to R.drawable.weapon_spear_iron, "bronze" to R.drawable.weapon_spear_bronze, "silver" to R.drawable.weapon_spear_silver, "moonsteel" to R.drawable.weapon_spear_moonsteel, "obsidian" to R.drawable.weapon_spear_obsidian, "starsteel" to R.drawable.weapon_spear_starsteel),
        "staff" to mapOf("iron" to R.drawable.weapon_staff_iron, "bronze" to R.drawable.weapon_staff_bronze, "silver" to R.drawable.weapon_staff_silver, "moonsteel" to R.drawable.weapon_staff_moonsteel, "obsidian" to R.drawable.weapon_staff_obsidian, "starsteel" to R.drawable.weapon_staff_starsteel),
    )

    /** Also used for the forge preview of a draft that has no Weapon yet; unmapped pairs fall back gracefully. */
    fun weapon(familyId: WeaponFamilyId, coreId: MaterialId): Int {
        val family = weapons[familyId.value] ?: weapons.getValue("sword")
        return family[coreId.value] ?: family.getValue("iron")
    }

    fun overlay(element: Element?): Int? = when (element) {
        Element.FIRE -> R.drawable.overlay_fire
        Element.FROST -> R.drawable.overlay_frost
        Element.STORM -> R.drawable.overlay_storm
        Element.GRAVE -> R.drawable.overlay_grave
        Element.VERDANT -> R.drawable.overlay_verdant
        Element.SUN -> R.drawable.overlay_sun
        null -> null
    }

    private val portraits: Map<String, List<Int>> = mapOf(
        "guardian" to listOf(R.drawable.portrait_guardian_0, R.drawable.portrait_guardian_1, R.drawable.portrait_guardian_2, R.drawable.portrait_guardian_3, R.drawable.portrait_guardian_4),
        "ranger" to listOf(R.drawable.portrait_ranger_0, R.drawable.portrait_ranger_1, R.drawable.portrait_ranger_2, R.drawable.portrait_ranger_3, R.drawable.portrait_ranger_4),
        "duelist" to listOf(R.drawable.portrait_duelist_0, R.drawable.portrait_duelist_1, R.drawable.portrait_duelist_2, R.drawable.portrait_duelist_3, R.drawable.portrait_duelist_4),
        "battlemage" to listOf(R.drawable.portrait_battlemage_0, R.drawable.portrait_battlemage_1, R.drawable.portrait_battlemage_2, R.drawable.portrait_battlemage_3, R.drawable.portrait_battlemage_4),
        "warden" to listOf(R.drawable.portrait_warden_0, R.drawable.portrait_warden_1, R.drawable.portrait_warden_2, R.drawable.portrait_warden_3, R.drawable.portrait_warden_4),
    )

    /** One of five hand-made faces per class, chosen by a stable hash of the hero ID (decorative; no RNG). */
    fun portrait(hero: Hero): Int {
        val faces = portraits[hero.classId.value] ?: portraits.getValue("guardian")
        return faces[Math.floorMod(hero.id.value.hashCode(), faces.size)]
    }

    /** Faction sprite for the threat line: the elite variant once pressure is high. Null for factions without art. */
    fun faction(id: FactionId, elite: Boolean = false): Int? = when (id.value) {
        "ashclaw_raiders" -> if (elite) R.drawable.faction_ashclaw_brute else R.drawable.faction_ashclaw_raider
        "hollowbound" -> if (elite) R.drawable.faction_hollowbound_wraith else R.drawable.faction_hollowbound_shade
        "embermaw_brood" -> if (elite) R.drawable.faction_embermaw_drake else R.drawable.faction_embermaw_whelp
        else -> null
    }

    /** Material icon for chips; null for materials without art. */
    fun material(id: MaterialId): Int? = when (id.value) {
        "iron" -> R.drawable.material_iron
        "bronze" -> R.drawable.material_bronze
        "silver" -> R.drawable.material_silver
        "moonsteel" -> R.drawable.material_moonsteel
        "obsidian" -> R.drawable.material_obsidian
        "starsteel" -> R.drawable.material_starsteel
        "ember_resin" -> R.drawable.material_ember_resin
        "frost_bloom" -> R.drawable.material_frost_bloom
        "stormglass" -> R.drawable.material_stormglass
        "binding_salt" -> R.drawable.material_binding_salt
        "dragon_oil" -> R.drawable.material_dragon_oil
        "grave_dust" -> R.drawable.material_grave_dust
        "runestone_shard" -> R.drawable.material_runestone_shard
        "sun_ash" -> R.drawable.material_sun_ash
        "verdant_sap" -> R.drawable.material_verdant_sap
        "void_ink" -> R.drawable.material_void_ink
        else -> null
    }

    fun blessing(id: BlessingId): Int? = when (id.value) {
        "forgefire" -> R.drawable.blessing_forgefire
        "tireless_hands" -> R.drawable.blessing_tireless_hands
        "merchants_favor" -> R.drawable.blessing_merchants_favor
        "stalwart_town" -> R.drawable.blessing_stalwart_town
        "hunters_edge" -> R.drawable.blessing_hunters_edge
        "guild_patronage" -> R.drawable.blessing_guild_patronage
        "lucky_alloy" -> R.drawable.blessing_lucky_alloy
        "runic_insight" -> R.drawable.blessing_runic_insight
        else -> null
    }

    private val heroFrames: Map<String, List<Int>> = mapOf(  // idle_0, idle_1, attack_0, attack_1
        "guardian" to listOf(R.drawable.hero_guardian_idle_0, R.drawable.hero_guardian_idle_1, R.drawable.hero_guardian_attack_0, R.drawable.hero_guardian_attack_1),
        "ranger" to listOf(R.drawable.hero_ranger_idle_0, R.drawable.hero_ranger_idle_1, R.drawable.hero_ranger_attack_0, R.drawable.hero_ranger_attack_1),
        "duelist" to listOf(R.drawable.hero_duelist_idle_0, R.drawable.hero_duelist_idle_1, R.drawable.hero_duelist_attack_0, R.drawable.hero_duelist_attack_1),
        "battlemage" to listOf(R.drawable.hero_battlemage_idle_0, R.drawable.hero_battlemage_idle_1, R.drawable.hero_battlemage_attack_0, R.drawable.hero_battlemage_attack_1),
        "warden" to listOf(R.drawable.hero_warden_idle_0, R.drawable.hero_warden_idle_1, R.drawable.hero_warden_attack_0, R.drawable.hero_warden_attack_1),
    )
    private val monsterFrames: Map<String, List<Int>> = mapOf(  // idle_0, idle_1, attack_0, attack_1, hit
        "ashclaw_raiders" to listOf(R.drawable.monster_ashclaw_raider_idle_0, R.drawable.monster_ashclaw_raider_idle_1, R.drawable.monster_ashclaw_raider_attack_0, R.drawable.monster_ashclaw_raider_attack_1, R.drawable.monster_ashclaw_raider_hit),
        "ashclaw_raiders:elite" to listOf(R.drawable.monster_ashclaw_brute_idle_0, R.drawable.monster_ashclaw_brute_idle_1, R.drawable.monster_ashclaw_brute_attack_0, R.drawable.monster_ashclaw_brute_attack_1, R.drawable.monster_ashclaw_brute_hit),
        "hollowbound" to listOf(R.drawable.monster_hollowbound_shade_idle_0, R.drawable.monster_hollowbound_shade_idle_1, R.drawable.monster_hollowbound_shade_attack_0, R.drawable.monster_hollowbound_shade_attack_1, R.drawable.monster_hollowbound_shade_hit),
        "hollowbound:elite" to listOf(R.drawable.monster_hollowbound_wraith_idle_0, R.drawable.monster_hollowbound_wraith_idle_1, R.drawable.monster_hollowbound_wraith_attack_0, R.drawable.monster_hollowbound_wraith_attack_1, R.drawable.monster_hollowbound_wraith_hit),
        "embermaw_brood" to listOf(R.drawable.monster_embermaw_whelp_idle_0, R.drawable.monster_embermaw_whelp_idle_1, R.drawable.monster_embermaw_whelp_attack_0, R.drawable.monster_embermaw_whelp_attack_1, R.drawable.monster_embermaw_whelp_hit),
        "embermaw_brood:elite" to listOf(R.drawable.monster_embermaw_drake_idle_0, R.drawable.monster_embermaw_drake_idle_1, R.drawable.monster_embermaw_drake_attack_0, R.drawable.monster_embermaw_drake_attack_1, R.drawable.monster_embermaw_drake_hit),
    )

    enum class Pose { IDLE, ATTACK, HIT }

    fun heroFrame(classId: HeroClassId, pose: Pose, tick: Int): Int {
        val f = heroFrames[classId.value] ?: heroFrames.getValue("guardian")
        return if (pose == Pose.ATTACK) f[2 + tick % 2] else f[tick % 2]
    }

    fun monsterFrame(factionId: FactionId, elite: Boolean, pose: Pose, tick: Int): Int {
        val f = monsterFrames[factionId.value + (if (elite) ":elite" else "")] ?: monsterFrames.getValue("ashclaw_raiders")
        return when (pose) { Pose.IDLE -> f[tick % 2]; Pose.ATTACK -> f[2 + tick % 2]; Pose.HIT -> f[4] }
    }

    fun siegeWall(damaged: Boolean): Int = if (damaged) R.drawable.siege_wall_damaged else R.drawable.siege_wall

    fun marker(fate: HeroFate): Int? = when (fate) { HeroFate.DEAD -> R.drawable.marker_dead; HeroFate.RETIRED -> R.drawable.marker_retired; HeroFate.ALIVE -> null }

    val milestoneFrames = listOf(R.drawable.fx_milestone_0, R.drawable.fx_milestone_1, R.drawable.fx_milestone_2, R.drawable.fx_milestone_3)

    fun badge(rarity: Rarity): Int = when (rarity) {
        Rarity.COMMON -> R.drawable.badge_common
        Rarity.UNCOMMON -> R.drawable.badge_uncommon
        Rarity.RARE -> R.drawable.badge_rare
        Rarity.EPIC -> R.drawable.badge_epic
        Rarity.LEGENDARY -> R.drawable.badge_legendary
    }
}

/**
 * Pixel image: nearest-neighbour when the bitmap is enlarged (keeps pixels square), bilinear when a larger
 * hand-made sprite is shrunk to fit (nearest sampling would drop rows and shimmer).
 */
@Composable
fun PixelImage(resId: Int, size: Dp, description: String?, modifier: Modifier = Modifier) {
    val bitmap = ImageBitmap.imageResource(resId)
    val targetPx = with(LocalDensity.current) { size.toPx() }
    val quality = if (bitmap.width > targetPx || bitmap.height > targetPx) FilterQuality.Low else FilterQuality.None
    Image(
        painter = BitmapPainter(bitmap, filterQuality = quality),
        contentDescription = description,
        modifier = modifier.size(size),
    )
}

@Composable
fun WeaponSprite(w: Weapon, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val element = w.element?.name?.lowercase()?.let { ", $it" } ?: ""
    Box(modifier.size(size).semantics { contentDescription = "${w.name}, ${w.rarity.name.lowercase()}$element" }) {
        // Element aura sits behind the weapon so the blade stays readable.
        Sprites.overlay(w.element)?.let { PixelImage(it, size, description = null, modifier = Modifier.alpha(0.85f)) }
        PixelImage(Sprites.weapon(w), size, description = null)
        PixelImage(Sprites.badge(w.rarity), size / 3, description = null, modifier = Modifier.size(size / 3))
    }
}

/** Tiles the parchment sprite behind content at an integer scale (nearest-neighbour), for the Gazette. */
@Composable
fun Modifier.paperBackground(): Modifier {
    val paper = ImageBitmap.imageResource(R.drawable.tile_paper)
    return drawBehind {
        if (paper.width <= 16) {
            // Seamless placeholder tile: repeat at an integer scale.
            val scale = maxOf(1, density.toInt())
            val tile = IntSize(paper.width * scale, paper.height * scale)
            var y = 0
            while (y < size.height) {
                var x = 0
                while (x < size.width) {
                    drawImage(paper, dstOffset = IntOffset(x, y), dstSize = tile, filterQuality = FilterQuality.None)
                    x += tile.width
                }
                y += tile.height
            }
        } else {
            // Hand-made parchment is not seamless: stretch one sheet to cover the surface.
            drawImage(paper, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
        }
    }
}

/** The workshop scene: tiled wall and floor, furnace heat from energy, animated embers unless reduced motion. */
@Composable
fun ForgeScene(heat: Float, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val wall = ImageBitmap.imageResource(R.drawable.tile_wall)
    val floor = ImageBitmap.imageResource(R.drawable.tile_floor)
    val furnace = ImageBitmap.imageResource(
        when {
            heat >= 0.6f -> R.drawable.furnace_hot
            heat > 0.05f -> R.drawable.furnace_warm
            else -> R.drawable.furnace_cold
        },
    )
    val anvil = ImageBitmap.imageResource(R.drawable.anvil)
    val rack = ImageBitmap.imageResource(R.drawable.tool_rack)
    val shelf = ImageBitmap.imageResource(R.drawable.shelf)
    val embers = listOf(R.drawable.ember_0, R.drawable.ember_1, R.drawable.ember_2, R.drawable.ember_3).map { ImageBitmap.imageResource(it) }
    val frame: Int = if (reducedMotion || heat <= 0.05f) 0 else {
        val transition = rememberInfiniteTransition(label = "embers")
        val t by transition.animateFloat(0f, 4f, infiniteRepeatable(tween(720, easing = LinearEasing)), label = "emberFrame")
        t.toInt().coerceIn(0, 3)
    }
    val unit = maxOf(1, wall.width / 16)  // bitmap pixels per scene pixel (1 for placeholders, 4 for hand-made art)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(100.dp)
            .clipToBounds()
            .semantics { contentDescription = "The forge: furnace, anvil, tool rack and shelf" },
    ) {
        // Logical scene is 96 x 48 game pixels; pick an integer scale so pixels stay square.
        val scale = maxOf(1, minOf(size.width / 96f, size.height / 48f).toInt())
        val sceneW = 96 * scale
        val sceneH = 48 * scale
        val ox = ((size.width - sceneW) / 2f).toInt()
        val oy = ((size.height - sceneH) / 2f).toInt()
        val quality = if (scale >= unit) FilterQuality.None else FilterQuality.Low
        fun blit(bmp: ImageBitmap, x: Int, y: Int) = drawImage(
            bmp, dstOffset = IntOffset(ox + x * scale, oy + y * scale),
            dstSize = IntSize(bmp.width * scale / unit, bmp.height * scale / unit), filterQuality = quality,
        )
        /** Place a piece by its bottom-left corner in scene pixels. */
        fun stand(bmp: ImageBitmap, x: Int, bottom: Int) = blit(bmp, x, bottom - bmp.height / unit)
        // Wall and floor tile across the whole canvas; scene pieces sit on a centred 96-pixel stage.
        val leftTiles = (ox / (16 * scale)) + 1
        val rightTiles = ((size.width - ox - sceneW) / (16 * scale)).toInt() + 1
        val topTiles = (oy / (16 * scale)) + 1
        for (ty in -topTiles * 16 until 48 step 16) for (tx in -leftTiles * 16 until 96 + rightTiles * 16 step 16) blit(wall, tx, ty)
        for (tx in -leftTiles * 16 until 96 + rightTiles * 16 step 16) blit(floor, tx, 40)
        for (tx in -leftTiles * 16 until 96 + rightTiles * 16 step 16) if (oy + 48 * scale < size.height) blit(floor, tx, 48)
        // Spread the furniture across the visible width (in scene pixels) instead of the fixed 96-pixel stage.
        val left = -leftTiles * 16 + 2
        val right = 96 + rightTiles * 16 - 2
        val visibleW = (size.width / scale).toInt()
        val l = maxOf(left, (96 - visibleW) / 2 + 2)
        val r = minOf(right, l + visibleW - 4)
        val mid = (l + r) / 2
        stand(furnace, l, 42)
        if (heat > 0.05f) blit(embers[frame], l + 6, 14)
        stand(anvil, mid - anvil.width / unit / 2, 43)
        blit(rack, r - rack.width / unit, 8)
        stand(shelf, r - rack.width / unit - shelf.width / unit - 4, 30)
    }
}

/** One champion or monster standing on the siege stage; poses come from the replay rounds, never from RNG. */
data class StageActor(val sprite: (Sprites.Pose, Int) -> Int, val pose: Sprites.Pose, val flipped: Boolean)

/**
 * Siege diorama for the day report (GDD 11): wall, champions on the left, raiders on the right. It renders the
 * current replay step only; it reads the CombatReplay and never changes outcomes. Idle frames alternate every
 * 240 ms unless reduced motion is on.
 */
@Composable
fun SiegeStage(actorsLeft: List<StageActor>, actorsRight: List<StageActor>, damaged: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val wall = ImageBitmap.imageResource(Sprites.siegeWall(damaged))
    val tick: Int = if (reducedMotion) 0 else {
        val transition = rememberInfiniteTransition(label = "stage")
        val t by transition.animateFloat(0f, 2f, infiniteRepeatable(tween(480, easing = LinearEasing)), label = "stageTick")
        t.toInt().coerceIn(0, 1)
    }
    val left = actorsLeft.map { ImageBitmap.imageResource(it.sprite(it.pose, tick)) to it.flipped }
    val right = actorsRight.map { ImageBitmap.imageResource(it.sprite(it.pose, tick)) to it.flipped }
    Canvas(
        modifier.fillMaxWidth().height(96.dp).clipToBounds()
            .semantics { contentDescription = "Siege at the town wall: ${actorsLeft.size} defenders against ${actorsRight.size} raiders" },
    ) {
        val scale = maxOf(1, minOf(size.width / 96f, size.height / 32f).toInt())
        val ox = ((size.width - 96 * scale) / 2f).toInt()
        val oy = ((size.height - 32 * scale) / 2f).toInt()
        fun blit(bmp: ImageBitmap, x: Int, y: Int, flip: Boolean = false) {
            val w = bmp.width * scale
            if (!flip) drawImage(bmp, dstOffset = IntOffset(ox + x * scale, oy + y * scale), dstSize = IntSize(w, bmp.height * scale), filterQuality = FilterQuality.None)
            else scale(-1f, 1f, pivot = Offset(ox + x * scale + w / 2f, 0f)) {
                drawImage(bmp, dstOffset = IntOffset(ox + x * scale, oy + y * scale), dstSize = IntSize(w, bmp.height * scale), filterQuality = FilterQuality.None)
            }
        }
        blit(wall, 0, 0)
        // Towers occupy the outer 16 px of the wall; the formation stands between them, raiders drawn first.
        right.forEachIndexed { i, (bmp, flip) -> blit(bmp, 80 - bmp.width - i * 11, 32 - bmp.height - 1, flip) }
        left.forEachIndexed { i, (bmp, flip) -> blit(bmp, 16 + i * 11, 32 - bmp.height - 1, flip) }
    }
}

/** Four-frame sparkle drawn over a sprite for milestones (signature discovered, epic or better). Static under reduced motion. */
@Composable
fun MilestoneBurst(size: Dp, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val frame: Int = if (reducedMotion) 3 else {
        val transition = rememberInfiniteTransition(label = "burst")
        val t by transition.animateFloat(0f, 4f, infiniteRepeatable(tween(640, easing = LinearEasing)), label = "burstFrame")
        t.toInt().coerceIn(0, 3)
    }
    PixelImage(Sprites.milestoneFrames[frame], size, description = null, modifier = modifier)
}

@Suppress("unused")
private fun DrawScope.unused() = Unit
