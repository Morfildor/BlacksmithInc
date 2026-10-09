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
 * Debug builds only. Lays out the hero set through the real `Sprites.portrait(...)` call, the two older portrait sets
 * and the backdrops, so the art can be checked in the renderer the game uses.
 * `--ei section N` shows one section at the top of the screen; without it the whole page scrolls. 0..5 are the older
 * sets and the backdrops (5 is their side-by-side at the counter size). 6 is the hero set, base and upgraded face of
 * each hero side by side: `--ei dp N` sets the size (44 and 56 are the Town rows, 85 the counter, 112 a sheet) and
 * `--es cls guardian` keeps one class, `--ez small true` asks for the tiles of small list rows.
 */
class ArtGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val section = intent.getIntExtra("section", -1)
        setContent {
            BlacksmithProjectTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
                        if (section in listOf(-1, 0)) { Backdrops(); Portraits("Sheet-3 busts (no longer used for heroes), 56 dp", PortraitArt.base, 56.dp) }
                        if (section in listOf(-1, 1)) Portraits("Sheet-3 busts (no longer used for heroes), 112 dp", PortraitArt.base, 112.dp)
                        if (section in listOf(-1, 2)) Portraits("Second set (candidates, switched off), 56 dp", PortraitArt.secondSet, 56.dp)
                        if (section in listOf(-1, 3)) Portraits("Second set (candidates, switched off), 112 dp", PortraitArt.secondSet, 112.dp)
                        if (section in listOf(-1, 4)) OtherBackdrops()
                        if (section in listOf(-1, 5)) Gate()
                        if (section in listOf(-1, 6)) Heroes(intent.getIntExtra("dp", 56).dp, intent.getStringExtra("cls"), intent.getBooleanExtra("small", false))
                    }
                }
            }
        }
    }
}

private val classes = listOf("guardian", "ranger", "duelist", "battlemage", "warden")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Portraits(title: String, set: Map<String, PortraitArt.Entry>, size: Dp) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    for (cls in classes) {
        // One row per class on the dark tile a portrait sits on in the game; a wrong class in a row is a wrong mapping.
        FlowRow(Modifier.fillMaxWidth().background(Color(0xFF2B2320)), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((key, entry) in set) if ("_${cls}_" in key) PixelImage(entry.drawable, size, description = key)
        }
    }
}

/** The hero set: per class, each hero's number over its base and upgraded face, both through `Sprites.portrait`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Heroes(size: Dp, only: String?, small: Boolean) {
    Text("Hero set, base | upgraded, ${size.value.toInt()} dp" + (if (small) ", small-row tiles" else ""), style = MaterialTheme.typography.titleMedium)
    for (cls in classes) if (only == null || only == cls) {
        FlowRow(Modifier.fillMaxWidth().background(Color(0xFF2B2320)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (key in PortraitArt.heroFaces.getValue(cls)) Column {
                Text("${key.takeLast(2)} $cls", style = MaterialTheme.typography.labelSmall, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    PixelImage(Sprites.portrait(key, HeroClassId(cls), small = small), size, description = key)
                    PixelImage(Sprites.portrait(key, HeroClassId(cls), upgraded = true, small = small), size, description = "$key upgraded")
                }
            }
        }
    }
}

/** One face per class from each set, on the same tile at the counter size (85 dp): the side-by-side for the mode decision. */
@Composable
private fun Gate() {
    Text("Per class: bust, tile, bust, tile at 85 dp", style = MaterialTheme.typography.titleMedium)
    for (cls in classes) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF2B2320)), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (key in listOf("portrait_${cls}_1", "portrait_v2_${cls}_1", "portrait_${cls}_3", "portrait_v2_${cls}_4")) {
                PixelImage((PortraitArt.base[key] ?: PortraitArt.secondSet.getValue(key)).drawable, 85.dp, description = key)
            }
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
