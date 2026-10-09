package com.example.blacksmithproject.ui.theme

import androidx.compose.ui.graphics.Color

// Warm forge palette: ember (primary), iron (secondary), brass (tertiary), parchment/soot surfaces.
val Ember40 = Color(0xFFB4451A)
val Ember80 = Color(0xFFFF9A5C)
val EmberContainerLight = Color(0xFFFFD9C7)
val EmberContainerDark = Color(0xFF7A2F0E)
val Iron40 = Color(0xFF5B6068)
val Iron80 = Color(0xFFB8BEC8)
val IronContainerLight = Color(0xFFDADEE4)
val IronContainerDark = Color(0xFF44494F)
val Brass40 = Color(0xFF7A5C2E)
val Brass80 = Color(0xFFE0B870)
val BrassContainerLight = Color(0xFFF2E1B8)
val BrassContainerDark = Color(0xFF5A4116)

val ParchmentBg = Color(0xFFF3E6CF)
val ParchmentSurface = Color(0xFFFAF1E0)
val ParchmentVariant = Color(0xFFE8D9BE)
val Ink = Color(0xFF2B2118)
val InkMuted = Color(0xFF4A3B2C)  // 7.6:1 on parchment surface; secondary text stays readable
val OutlineLight = Color(0xFF7D6C54)

val SootBg = Color(0xFF1B1512)
val SootSurface = Color(0xFF241C17)
val SootVariant = Color(0xFF3A2F27)
val Parchment = Color(0xFFF1E6D2)
val ParchmentMuted = Color(0xFFCDBFA6)
val OutlineDark = Color(0xFF8C7B66)

/** The Gazette is printed on paper in both themes, so its ink colours are fixed. */
val PaperInk = Ink
val PaperInkMuted = InkMuted
val PaperRule = Color(0xFF7A6448)

// Palette swatches (New folder/palette.gpl) for what Compose draws inside the counter scene; the painted art is not limited to them.
val SceneWood2 = Color(0xFF8C6239)   // counter planks, lit
val SceneWood1 = Color(0xFF6B4B32)   // counter planks, shade
val SceneInk = Color(0xFF1A1210)     // slot and tile ground, edges
val SceneDeep = Color(0xFF2B2320)    // name plate ground
val SceneCream = Color(0xFFFFF0A0)   // price tags, plate text
val SceneGold = Color(0xFFD8A030)    // the ring of a regular, a considered blade
