package com.example.blacksmithproject.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.blacksmithproject.R

/** Body and labels: Fira Sans, a humanist sans with a tall x-height, in the two weights the game uses (SemiBold resolves to Bold). */
val BodyFamily = FontFamily(Font(R.font.fira_sans_medium, FontWeight.Medium), Font(R.font.fira_sans_bold, FontWeight.Bold))

/** Headings: Alegreya Bold, a calligraphic serif for the workshop's voice. Headings only, never running text, and with lining figures (its default figures are old-style, a 0 reads as an o). Licences in assets/licenses. */
val HeadingFamily = FontFamily(Font(R.font.alegreya_bold, FontWeight.Bold))

/**
 * Four working styles (GDD 12: scalable, readable text). Panels use only these:
 *  - titleLarge  serif, panel and section headings
 *  - titleMedium bold, card titles, key numbers, primary buttons
 *  - bodyMedium  body text, never below 16sp
 *  - bodySmall   secondary text, always paired with onSurfaceVariant
 * Body and label weights are Medium (500): Regular reads thin as light text on the dark plates. Headings are the serif, the rest the sans.
 * labelMedium/labelSmall exist for chips and the nav bar only.
 */
val Typography = Typography(
    headlineLarge = TextStyle(fontFamily = HeadingFamily, fontFeatureSettings = "lnum", fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = HeadingFamily, fontFeatureSettings = "lnum", fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    // Dialog titles (Material's AlertDialog reads this slot): the same serif as a panel heading, never the 24 sp Regular default.
    headlineSmall = TextStyle(fontFamily = HeadingFamily, fontFeatureSettings = "lnum", fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = HeadingFamily, fontFeatureSettings = "lnum", fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
