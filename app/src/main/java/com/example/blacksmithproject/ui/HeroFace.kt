package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.shopday.PixelSprite
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.ForgeSlot

/**
 * A hero's face as the game draws it everywhere: the portrait [res] at the whole device-pixel scale nearest [hint] for
 * its own width ([nearestWholeScale]), never recoloured, on the slot ground inside a 1 dp bronze line. The footprint is
 * that drawn size plus 2 dp. [marker] (fallen, retired) sits in the frame's corner at the face's scale, a shape cue
 * clear of the face's centre. Decorative: the name beside it carries the meaning.
 */
@Composable
fun HeroFace(res: Int, hint: Dp, modifier: Modifier = Modifier, marker: Int? = null) {
    val density = LocalDensity.current
    val k = nearestWholeScale(ImageBitmap.imageResource(res).width, with(density) { hint.toPx() })
    Box(modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(1.dp)) {
        PixelSprite(res, k)
        // The marker keeps its own size (about 16 dp, as the rows drew it before), whatever the face's scale.
        if (marker != null) PixelSprite(marker, nearestWholeScale(ImageBitmap.imageResource(marker).width, with(density) { 16.dp.toPx() }), Modifier.align(Alignment.BottomEnd))
    }
}
