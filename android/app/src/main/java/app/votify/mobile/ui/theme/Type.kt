package app.votify.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.votify.mobile.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Type scale from design/DESIGN-monochrome.md mapped onto Material 3 roles. */
val VotifyTypography = Typography(
    displayLarge = TextStyle(Inter, fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(Inter, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp, letterSpacing = (-0.015).em),
    headlineLarge = TextStyle(Inter, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(Inter, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    headlineSmall = TextStyle(Inter, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleLarge = TextStyle(Inter, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    titleMedium = TextStyle(Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp, letterSpacing = 0.01.em),
    titleSmall = TextStyle(Inter, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.01.em),
    bodyLarge = TextStyle(Inter, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp, letterSpacing = 0.015.em),
    bodyMedium = TextStyle(Inter, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = 0.015.em),
    bodySmall = TextStyle(Inter, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.02.em),
    labelLarge = TextStyle(Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, letterSpacing = 0.04.em),
    labelSmall = TextStyle(Inter, fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, letterSpacing = 0.05.em),
)
