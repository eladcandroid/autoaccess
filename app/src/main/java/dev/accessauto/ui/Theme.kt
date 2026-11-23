package dev.accessauto.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.accessauto.R

/** "Cockpit" design tokens: instrument-cluster faces on brushed aluminium. */
object Cockpit {
    val Aluminium = Color(0xFFC6CCD2)
    val Ink = Color(0xFF14171A)
    val Dial = Color(0xFFF4F6F5)
    val Needle = Color(0xFFF25C05)
    val Ok = Color(0xFF0B7A4B)
    val Danger = Color(0xFFC8102E)
    val Muted = Color(0xFF4A5158)

    private val Karantina = FontFamily(Font(R.font.karantina_bold, FontWeight.Bold))
    private val Plex = FontFamily(
        Font(R.font.plex_hebrew_regular, FontWeight.Normal),
        Font(R.font.plex_hebrew_medium, FontWeight.Medium),
        Font(R.font.plex_hebrew_semibold, FontWeight.SemiBold),
    )

    /** Big condensed readout, like the numerals on a dial. */
    val Readout = TextStyle(fontFamily = Karantina, fontWeight = FontWeight.Bold, fontSize = 76.sp, lineHeight = 70.sp, color = Ink)
    val Body = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = Muted)
    val Label = TextStyle(fontFamily = Plex, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Ink)
}
