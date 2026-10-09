package com.example.blacksmithproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.PixelImage
import com.example.blacksmithproject.ui.PortraitArt
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.model.HeroClassId

/**
 * Debug builds only. Lays out every portrait through the real `Sprites.portrait(...)` call at 56 dp and 112 dp, and
 * the counter backdrop at full width, so the art can be checked in the renderer the game uses.
 * `--ei section N` (0..4) shows one section at the top of the screen; without it the whole page scrolls.
 */
class ArtGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val section = intent.getIntExtra("section", -1)
        setContent {
            BlacksmithProjectTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
                        if (section in listOf(-1, 0)) { Backdrops(); Portraits("Sheet-3 busts, 56 dp", PortraitArt.base.keys, 56.dp, secondSet = false) }
                        if (section in listOf(-1, 1)) Portraits("Sheet-3 busts, 112 dp", PortraitArt.base.keys, 112.dp, secondSet = false)
                        if (section in listOf(-1, 2)) Portraits("Second set (candidates, switched off), 56 dp", PortraitArt.secondSet.keys, 56.dp, secondSet = true)
                        if (section in listOf(-1, 3)) Portraits("Second set (candidates, switched off), 112 dp", PortraitArt.secondSet.keys, 112.dp, secondSet = true)
                        if (section in listOf(-1, 4)) OtherBackdrops()
                    }
                }
            }
        }
    }
}

private val classes = listOf("guardian", "ranger", "duelist", "battlemage", "warden")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Portraits(title: String, keys: Set<String>, size: Dp, secondSet: Boolean) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    for (cls in classes) {
        // One row per class on the dark tile a portrait sits on in the game; a wrong class in a row is a wrong mapping.
        FlowRow(Modifier.fillMaxWidth().background(Color(0xFF2B2320)), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (key in keys.filter { "_${cls}_" in it }) PixelImage(Sprites.portrait(key, HeroClassId(cls), secondSet), size, description = key)
        }
    }
}

@Composable
private fun Backdrops() {
    Text("bg_counter_forge, full width; atlas icons, 40 dp", style = MaterialTheme.typography.titleMedium)
    FullWidth(R.drawable.bg_counter_forge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (icon in listOf(R.drawable.icon_nav_home, R.drawable.icon_purse, R.drawable.icon_settings)) PixelImage(icon, 40.dp, description = null)
    }
}

@Composable
private fun FullWidth(id: Int) = Image(
    BitmapPainter(ImageBitmap.imageResource(id), filterQuality = FilterQuality.None), contentDescription = null,
    modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth,
)

@Composable
private fun OtherBackdrops() {
    Text("bg_forge_night, bg_title_workshop_night, bg_run_end_fallen_forge", style = MaterialTheme.typography.titleMedium)
    for (id in listOf(R.drawable.bg_forge_night, R.drawable.bg_title_workshop_night, R.drawable.bg_run_end_fallen_forge)) FullWidth(id)
}
