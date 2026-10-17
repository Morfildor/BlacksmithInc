package com.example.blacksmithproject.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.blacksmithproject.R

/** Body and labels: Fira Sans, a humanist sans with a tall x-height. Regular for sentences, Medium for titles, labels and actions; SemiBold and Bold only for a number that must be found first. */
val BodyFamily = FontFamily(
    Font(R.font.fira_sans_regular, FontWeight.Normal), Font(R.font.fira_sans_medium, FontWeight.Medium),
    Font(R.font.fira_sans_semibold, FontWeight.SemiBold), Font(R.font.fira_sans_bold, FontWeight.Bold),
)

/** Headings: Alegreya Bold, a calligraphic serif for the workshop's voice. Headings only, never running text, and with lining figures (its default figures are old-style, a 0 reads as an o). Licences in assets/licenses. */
val HeadingFamily = FontFamily(Font(R.font.alegreya_bold, FontWeight.Bold))

/** Tabular lining figures: numbers in a column or beside a label line up and do not jump as they change. */
const val NUMBERS = "tnum, lnum"

/**
 * A heading inside a panel or a sheet ("Champions", "Who is buying"): Fira SemiBold in true small caps with a little
 * tracking. The string stays as written (tests, TalkBack); the font draws the capitals. Gold, over a hairline.
 */
val SectionLabel = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.8.sp, fontFeatureSettings = "smcp, lnum")

/**
 * Four working styles (GDD 12: scalable, readable text). Panels use only these:
 *  - titleLarge  serif, panel and section headings
 *  - titleMedium medium, card titles, key numbers, primary buttons
 *  - bodyMedium  body text, never below 16sp
 *  - bodySmall   secondary text, always paired with onSurfaceVariant
 * Weight is given by role, never by size (V2, the owner's "selective bold"): SemiBold for the names of things (a weapon,
 * a hero, a quest, a material) and for key numbers (gold, prices, stats), always on cream or gold and never below 16 sp;
 * Medium for buttons, tabs and chips; Regular for every sentence and every secondary line. Section headings inside panels
 * are [SectionLabel] small caps; the serif is kept for screen and dialog titles, the day and the big moments.
 * labelMedium/labelSmall exist for chips and the nav bar only.
 */
val Typography = Typography(
    headlineLarge = TextStyle(fontFamily = HeadingFamily, fontFeatureSettings = "lnum", fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = HeadingFamily, fontFeatureSettings = "lnum", fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    // Dialog titles (Material's AlertDialog reads this slot): the same serif as a panel heading, never the 24 sp Regular default.
    headlineSmall = TextStyle(fontFamily = HeadingFamily, fontFeatureSettings = "lnum", fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = HeadingFamily, fontFeatureSettings = "lnum", fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, fontFeatureSettings = NUMBERS),
    titleSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = NUMBERS),
    bodyLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    labelLarge = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp, fontFeatureSettings = NUMBERS),
    labelMedium = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, fontFeatureSettings = NUMBERS),
    labelSmall = TextStyle(fontFamily = BodyFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)
