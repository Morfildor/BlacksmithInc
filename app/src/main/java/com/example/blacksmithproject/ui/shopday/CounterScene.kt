package com.example.blacksmithproject.ui.shopday

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.heroUpgraded
import com.example.blacksmithproject.ui.theme.SceneCream
import com.example.blacksmithproject.ui.theme.SceneDeep
import com.example.blacksmithproject.ui.theme.SceneGold
import com.example.blacksmithproject.ui.theme.SceneInk
import com.example.blacksmithproject.ui.theme.SceneWood1
import com.example.blacksmithproject.ui.theme.SceneWood2
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponSnapshot
import kotlin.math.ceil

/** The whole number of device pixels per art pixel that comes nearest to [hint]; never below 1. Pixel art is only ever drawn at such a factor. */
@Composable
internal fun wholeScale(artPx: Int, hint: Dp): Int = maxOf(1, (with(LocalDensity.current) { hint.toPx() } / artPx + 0.5f).toInt())

/** A sprite at a whole-number scale, nearest-neighbour, in a box of exactly that size. Decorative: no semantics. */
@Composable
internal fun PixelSprite(resId: Int, scale: Int, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val bitmap = ImageBitmap.imageResource(resId)
    val size = with(LocalDensity.current) { (bitmap.width * scale).toDp() }
    Canvas(modifier.size(size)) {
        drawImage(bitmap, dstSize = IntSize(bitmap.width * scale, bitmap.height * scale), alpha = alpha, filterQuality = FilterQuality.None)
    }
}

/**
 * A painted backdrop at the whole-number scale that covers the width (`ceil(width / art width)`), centred and anchored
 * to the bottom (or the top), cropped to the box. Never a fractional scale. [sink] lets a share of the art's height
 * drop below the box when the box is lower than the art (the counter stands in front of the floor).
 */
@Composable
internal fun Backdrop(resId: Int, modifier: Modifier = Modifier, dim: Float = 0f, anchorTop: Boolean = false, sink: Float = 0f) {
    val bitmap = ImageBitmap.imageResource(resId)
    Canvas(modifier.clipToBounds()) {
        val s = maxOf(1, ceil(size.width / bitmap.width).toInt())
        val w = bitmap.width * s
        val h = bitmap.height * s
        drawImage(bitmap, dstOffset = IntOffset(((size.width - w) / 2f).toInt(), if (anchorTop) 0 else (size.height - h).toInt() + minOf((h - size.height).toInt().coerceAtLeast(0), (h * sink).toInt())), dstSize = IntSize(w, h), filterQuality = FilterQuality.None)
        if (dim > 0f) drawRect(SceneInk.copy(alpha = dim))
    }
}

@Composable
private fun faceRes(face: FaceUi): Int? = face.snapshot?.let { Sprites.portrait(it.appearance, it.classId, upgraded = face.hero?.let { h -> heroUpgraded(null, h.kills) } ?: false) } ?: face.hero?.let { Sprites.portrait(it) }

/**
 * A face on its tile: the 64 px portrait at a whole-number [scale] on an ink ground inside a frame ([frame] null draws
 * the bust alone). A regular wears the gold ring. A visitor who is not a hero (the collector) is a purse.
 */
@Composable
fun PortraitTile(face: FaceUi, scale: Int, modifier: Modifier = Modifier, frame: Color? = if (face.regular) SceneGold else SceneWood2) {
    val border = with(LocalDensity.current) { (scale * 2).coerceAtLeast(2).toDp() }
    Box(
        modifier.then(if (frame != null) Modifier.background(SceneInk).border(border, frame).padding(border) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val res = faceRes(face)
        if (res != null) PixelSprite(res, scale)
        else Box(Modifier.size(with(LocalDensity.current) { (64 * scale).toDp() }), contentAlignment = Alignment.Center) { PixelSprite(R.drawable.icon_purse, scale) }
    }
}

/**
 * The counter: the forge backdrop, the customer as a framed bust standing behind the plank counter, and the name plate
 * on the counter front. Text is native; the backdrop is decoration and is hidden from the accessibility tree.
 * [customerKey] changes when another customer steps up (the bust slides in, unless [reducedMotion]).
 */
@Composable
fun CounterScene(
    plate: String,
    detail: String?,
    customer: FaceUi?,
    customerKey: Any?,
    reducedMotion: Boolean,
    onOpenHero: (FaceUi) -> Unit,
    modifier: Modifier = Modifier,
    backdropHeight: Dp = 140.dp,
) {
    Column(modifier.fillMaxWidth().background(SceneInk)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(backdropHeight).clipToBounds()) {
            val s = maxOf(1, ceil(constraints.maxWidth / 540f).toInt())
            Backdrop(R.drawable.bg_counter_forge, Modifier.matchParentSize().clearAndSetSemantics {}, sink = 0.2f)
            if (customer != null) {
                val enter = remember(customerKey) { Animatable(if (reducedMotion) 0f else 1f) }
                LaunchedEffect(customerKey) { enter.animateTo(0f, tween(220)) }
                val slide = with(LocalDensity.current) { 40.dp.toPx() }
                val overlap = 8.dp
                // Twice the backdrop's factor, so the bust and the room agree on pixel size; one less where the scene is too low for it.
                val fits = with(LocalDensity.current) { ((backdropHeight + overlap).toPx() / 68f).toInt() }
                val bust = minOf(2 * s, fits).coerceAtLeast(1)
                // About 62 % of the way across, the bottom edge tucked behind the counter's lip.
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().wrapContentWidth(BiasAlignment.Horizontal(0.4f)).offset(y = overlap)) {
                    PortraitTile(
                        customer, scale = bust,
                        modifier = Modifier
                            .graphicsLayer { translationX = enter.value * slide; alpha = 1f - enter.value }
                            .then(
                                if (customer.heroId != null) Modifier.clickable(onClickLabel = "Open ${customer.name}", role = Role.Button) { onOpenHero(customer) }
                                    .semantics { contentDescription = "${customer.name}, at the counter" }.testTag("shopday_face")
                                else Modifier,
                            ),
                    )
                }
            }
        }
        // The counter front: a lit lip over shaded planks, and the name plate set into it.
        Box(
            Modifier.fillMaxWidth().zIndex(1f).drawBehind {
                drawRect(SceneWood1)
                val seam = 56.dp.toPx()
                var x = seam
                while (x < size.width) { drawRect(SceneInk.copy(alpha = 0.35f), Offset(x, 0f), Size(1.dp.toPx(), size.height)); x += seam }
                drawRect(SceneWood2, size = Size(size.width, 5.dp.toPx()))
                drawRect(SceneInk, size = Size(size.width, 1.dp.toPx()))
                drawRect(SceneInk, Offset(0f, 5.dp.toPx()), Size(size.width, 1.dp.toPx()))
            }.padding(start = 8.dp, end = 8.dp, top = 11.dp, bottom = 6.dp),
        ) {
            Column(Modifier.fillMaxWidth().background(SceneDeep).border(1.dp, SceneInk).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(plate, style = MaterialTheme.typography.titleMedium, color = SceneCream, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }.testTag("shopday_plate"))
                if (!detail.isNullOrEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE6D9B8))
            }
        }
    }
}

enum class SlotState { ON_SHELF, LOOKED_AT, SOLD_NOW, GONE }

/** One shelf slot: the blade at a whole-number scale with its rarity pip, a flaw pip and the price tag. Opens the blade's sheet. */
@Composable
fun WeaponSlot(blade: WeaponSnapshot, price: Int, state: SlotState, reducedMotion: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scale = wholeScale(56, 64.dp)
    val pip = wholeScale(24, 18.dp)
    val lift by animateDpAsState(if (state == SlotState.LOOKED_AT) 0.dp else 4.dp, tween(if (reducedMotion) 0 else 180), label = "lift")
    val gone = state == SlotState.GONE || state == SlotState.SOLD_NOW
    val what = when (state) {
        SlotState.GONE, SlotState.SOLD_NOW -> "${blade.name}, sold"
        SlotState.LOOKED_AT -> "${blade.name}, $price gold, being looked at"
        SlotState.ON_SHELF -> "${blade.name}, $price gold"
    }
    Box(
        modifier.padding(top = lift, bottom = 4.dp - lift)
            .background(if (state == SlotState.GONE) SceneInk else SceneDeep)
            .border(2.dp, if (state == SlotState.LOOKED_AT || state == SlotState.SOLD_NOW) SceneGold else SceneWood1)
            .clickable(onClickLabel = "Open ${blade.name}", role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = what }
            .defaultMinSize(48.dp, 48.dp)
            .padding(2.dp),
    ) {
        val sprite = Sprites.weapon(blade.familyId, blade.coreId, blade.element, blade.rarity, signature = blade.signatureId != null)
        PixelSprite(sprite, scale, alpha = when (state) { SlotState.GONE -> 0.12f; SlotState.SOLD_NOW -> 0.4f; else -> 1f })
        if (!gone) {
            PixelSprite(Sprites.badge(blade.rarity), pip, Modifier.align(Alignment.TopStart))
            if (blade.flaws.isNotEmpty()) PixelSprite(Sprites.badgeFlaw, pip, Modifier.align(Alignment.TopEnd))
        }
        Text(
            if (gone) "sold" else "$price",
            style = MaterialTheme.typography.labelMedium, maxLines = 1,
            color = if (gone) SceneCream else SceneInk,
            modifier = Modifier.align(Alignment.BottomEnd).background(if (gone) SceneInk else SceneCream).padding(horizontal = 4.dp).clearAndSetSemantics {},
        )
    }
}

/** The shelf under the scene, as it stood at this point of the day: [gone] have left, [looking] lift, [sold] leaves now. */
@Composable
fun ShelfBand(
    shelf: List<BladeUi>,
    gone: Set<WeaponId>,
    looking: Set<WeaponId>,
    sold: WeaponId?,
    reducedMotion: Boolean,
    onOpenBlade: (BladeUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val focus = shelf.indexOfFirst { it.blade.weaponId == sold }.takeIf { it >= 0 } ?: shelf.indexOfFirst { it.blade.weaponId in looking }
    LaunchedEffect(focus) { if (focus >= 0) { if (reducedMotion) list.scrollToItem(focus) else list.animateScrollToItem(focus) } }
    if (shelf.isEmpty()) {
        Text("The shelf is bare.", style = MaterialTheme.typography.bodyMedium, color = SceneCream, modifier = modifier.fillMaxWidth().background(SceneInk).padding(horizontal = 12.dp, vertical = 14.dp))
        return
    }
    LazyRow(
        modifier.fillMaxWidth().background(SceneInk).testTag("shopday_shelf"), state = list,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(shelf, key = { it.blade.weaponId.value }) { b ->
            val id = b.blade.weaponId
            val state = when {
                id == sold -> SlotState.SOLD_NOW
                id in gone -> SlotState.GONE
                id in looking -> SlotState.LOOKED_AT
                else -> SlotState.ON_SHELF
            }
            WeaponSlot(b.blade, b.price, state, reducedMotion, onOpen = { onOpenBlade(b) })
        }
    }
}

/**
 * The strip above the cards that are not at the counter (beyond the door, tomorrow, the fall): art and a title.
 * A [vignette] is a cut-out piece (the art_day_ set) drawn centred on the ink ground at the largest whole scale that fits.
 */
@Composable
fun StageBanner(title: String, art: Int, modifier: Modifier = Modifier, height: Dp = 112.dp, anchorTop: Boolean = true, vignette: Boolean = false) {
    Box(modifier.fillMaxWidth().height(height).background(SceneInk)) {
        if (vignette) {
            val artHeight = ImageBitmap.imageResource(art).height
            val scale = maxOf(1, with(LocalDensity.current) { height.toPx() / artHeight }.toInt())
            PixelSprite(art, scale, Modifier.align(Alignment.Center).clearAndSetSemantics {})
        } else {
            Backdrop(art, Modifier.matchParentSize().clearAndSetSemantics {}, dim = 0.15f, anchorTop = anchorTop)
        }
        Text(
            title, style = MaterialTheme.typography.titleLarge, color = SceneCream, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(SceneDeep).border(1.dp, SceneInk).padding(horizontal = 10.dp, vertical = 4.dp).semantics { heading() }.testTag("shopday_plate"),
        )
    }
}
