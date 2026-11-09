package com.pancy.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Chiaro = lightColorScheme(
    primary = Color(0xFFB83A63), onPrimary = Color.White,
    primaryContainer = Color(0xFFFBD9E3), onPrimaryContainer = Color(0xFF4A1027),
    secondaryContainer = Color(0xFFF6E1E7), onSecondaryContainer = Color(0xFF3B1F28),
    background = Color(0xFFFBF6F4), onBackground = Color(0xFF2A1F24),
    surface = Color.White, onSurface = Color(0xFF2A1F24),
    surfaceVariant = Color(0xFFF3E9EC), onSurfaceVariant = Color(0xFF7A6870),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFDF8F7),
    surfaceContainer = Color(0xFFF9F1F1), surfaceContainerHigh = Color(0xFFF6ECED),
    surfaceContainerHighest = Color(0xFFF1E5E8),
    outline = Color(0xFFB7A3AB), outlineVariant = Color(0xFFEEDFE3),
    error = Color(0xFFC62828),
)

private val Scuro = darkColorScheme(
    primary = Color(0xFFF59AB5), onPrimary = Color(0xFF4A1027),
    primaryContainer = Color(0xFF6E2441), onPrimaryContainer = Color(0xFFFFD9E3),
    secondaryContainer = Color(0xFF4A3540), onSecondaryContainer = Color(0xFFF6E1E7),
    background = Color(0xFF17121A), onBackground = Color(0xFFF3ECEF),
    surface = Color(0xFF221B25), onSurface = Color(0xFFF3ECEF),
    surfaceVariant = Color(0xFF2E2531), onSurfaceVariant = Color(0xFFBBAAB3),
    surfaceContainerLowest = Color(0xFF120E14), surfaceContainerLow = Color(0xFF1C161F),
    surfaceContainer = Color(0xFF221B25), surfaceContainerHigh = Color(0xFF2B232E),
    surfaceContainerHighest = Color(0xFF342B38),
    outline = Color(0xFF8C7A85), outlineVariant = Color(0xFF3A2F3E),
    error = Color(0xFFFF8A80),
)

@Composable
fun PancyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Scuro else Chiaro, content = content)
}

// ---- Date e orari, in italiano ----
private val IT = Locale.ITALY
private val fmtLunga = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", IT)
private val fmtMedia = DateTimeFormatter.ofPattern("EEEE d MMMM", IT)
private val fmtMese = DateTimeFormatter.ofPattern("MMMM yyyy", IT)
private val fmtMeseBreve = DateTimeFormatter.ofPattern("MMM", IT)
private val fmtGiornoBreve = DateTimeFormatter.ofPattern("EEE", IT)
private val fmtOra = DateTimeFormatter.ofPattern("H:mm", IT)

fun String.maiuscola(): String = replaceFirstChar { it.titlecase(IT) }

/** "martedì 12 dicembre 2026" */
fun dataLunga(d: LocalDate): String = d.format(fmtLunga)

/** "martedì 12 dicembre", con l'anno solo se non è quello in corso. */
fun dataMedia(d: LocalDate): String = if (d.year == LocalDate.now().year) d.format(fmtMedia) else d.format(fmtLunga)

/** "Dicembre 2026" */
fun mese(d: LocalDate): String = d.format(fmtMese).maiuscola()

/** "dic" */
fun meseBreve(d: LocalDate): String = d.format(fmtMeseBreve).removeSuffix(".")

/** "mar" */
fun giornoBreve(d: LocalDate): String = d.format(fmtGiornoBreve).removeSuffix(".")

/** "9:30" */
fun orario(t: LocalTime): String = t.format(fmtOra)

/** "Oggi", "Domani", "Tra 5 giorni", "Ieri", "3 giorni fa". */
fun quando(d: LocalDate, oggi: LocalDate = LocalDate.now()): String {
    val n = ChronoUnit.DAYS.between(oggi, d)
    return when {
        n == 0L -> "Oggi"
        n == 1L -> "Domani"
        n == -1L -> "Ieri"
        n > 1 -> "Tra $n giorni"
        else -> "${-n} giorni fa"
    }
}
