package com.example.blacksmithproject.ui.theme

import androidx.compose.ui.graphics.Color

// Dark forge palette: night-navy grounds, cream text, ember (primary), bronze outlines, gold accents.
val Ember = Color(0xFFFF9A5C)          // 8.3:1 on ForgePanel
val EmberContainer = Color(0xFF6E2C10)
val Iron = Color(0xFFB8BEC8)
val Gold = Color(0xFFE8BC5A)           // headings, title plates, the primary action; 9.8:1 on ForgePanel
val GoldBright = Color(0xFFFFE29A)
val GoldDeep = Color(0xFF8A5E14)
val Bronze = Color(0xFFA8803C)         // frames and outlines; 4.8:1 on ForgePanel
val BronzeDeep = Color(0xFF4A3A1E)     // hairlines, dividers
val BronzeContainer = Color(0xFF45371A)

val ForgeNight = Color(0xFF0C0F15)     // the ground behind every panel
val ForgePanel = Color(0xFF151A23)     // panels, sheets, dialogs
val ForgePanelRaised = Color(0xFF1D2330)
val ForgeSlot = Color(0xFF090B10)      // sprite slots, empty bar segments
val Cream = Color(0xFFF2E8D0)          // body text; 14.3:1 on ForgePanel
val CreamMuted = Color(0xFFC9BFA8)     // secondary text; 9.6:1 on ForgePanel

/** A buff and a flaw, light enough to read as text on a panel (10.6:1 and 8.6:1); always shown with "+" or "−" and words. */
val BuffGreen = Color(0xFF8FDC8C)
val FlawRed = Color(0xFFFF9C8C)

val Ink = Color(0xFF2B2118)
val InkMuted = Color(0xFF4A3B2C)

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
