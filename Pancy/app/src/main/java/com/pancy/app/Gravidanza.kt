package com.pancy.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Conti sulla gravidanza, a partire dalla data presunta del parto (DPP).
 * La gravidanza dura convenzionalmente 280 giorni (40 settimane) dal primo giorno dell'ultima mestruazione.
 */
object Gravidanza {
    const val DURATA_GIORNI = 280

    /** Primo giorno dell'ultima mestruazione che corrisponde a quella data presunta del parto. */
    fun inizio(dpp: LocalDate): LocalDate = dpp.minusDays(DURATA_GIORNI.toLong())

    /** Data presunta del parto a partire dall'ultima mestruazione (regola di Naegele). */
    fun dppDaUltimaMestruazione(dum: LocalDate): LocalDate = dum.plusDays(DURATA_GIORNI.toLong())

    /** Giorni di gravidanza compiuti in quel giorno, oppure null se è prima dell'inizio o molto dopo il parto. */
    fun giorni(dpp: LocalDate, il: LocalDate): Int? {
        val g = ChronoUnit.DAYS.between(inizio(dpp), il)
        return if (g < 0 || g > 44 * 7) null else g.toInt()
    }

    /** "20+3": settimane compiute più giorni, come lo scrivono medici e ostetriche. */
    fun settimana(dpp: LocalDate?, il: LocalDate): String? {
        if (dpp == null) return null
        val g = giorni(dpp, il) ?: return null
        return "${g / 7}+${g % 7}"
    }

    /** "20 settimane e 3 giorni". */
    fun settimanaPerEsteso(giorni: Int): String {
        val s = giorni / 7
        val d = giorni % 7
        val sett = if (s == 1) "1 settimana" else "$s settimane"
        return when (d) {
            0 -> sett
            1 -> "$sett e 1 giorno"
            else -> "$sett e $d giorni"
        }
    }

    /** 1, 2 o 3. Primo trimestre fino a 13+6, secondo fino a 27+6, poi terzo. */
    fun trimestre(giorni: Int): Int = when {
        giorni < 14 * 7 -> 1
        giorni < 28 * 7 -> 2
        else -> 3
    }

    fun nomeTrimestre(giorni: Int): String = when (trimestre(giorni)) {
        1 -> "primo trimestre"
        2 -> "secondo trimestre"
        else -> "terzo trimestre"
    }
}
