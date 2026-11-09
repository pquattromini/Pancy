package com.pancy.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

data class CalInfo(val id: Long, val nome: String, val account: String, val scrivibile: Boolean, val sincronizzato: Boolean)

/**
 * Copia gli appuntamenti nel calendario del telefono (per esempio Google Calendar, che li sincronizza da solo).
 * Se il calendario scelto è condiviso, gli appuntamenti li vede anche chi lo condivide con te.
 */
object CalendarSync {
    val PERMESSI = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    /** Promemoria per gli appuntamenti senza orario: la sera prima alle 20 (4 ore prima della mezzanotte). */
    private const val PROMEMORIA_SENZA_ORA_MIN = 4 * 60

    fun haPermesso(ctx: Context): Boolean =
        PERMESSI.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    /** Tutti i calendari che il telefono conosce. Lista vuota se manca il permesso. */
    fun calendari(ctx: Context): List<CalInfo> {
        if (!haPermesso(ctx)) return emptyList()
        val out = ArrayList<CalInfo>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.SYNC_EVENTS,
        )
        try {
            ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        CalInfo(
                            c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "",
                            c.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                            c.getInt(4) == 1,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return out
    }

    /** Vero se l'evento esiste ancora (non cancellato) ed è proprio in quel calendario. */
    private fun esiste(ctx: Context, eventoId: Long, calId: Long): Boolean {
        val sel = CalendarContract.Events._ID + " = ? AND " +
            CalendarContract.Events.CALENDAR_ID + " = ? AND " +
            CalendarContract.Events.DELETED + " = 0"
        ctx.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID), sel,
            arrayOf(eventoId.toString(), calId.toString()), null,
        )?.use { c -> return c.moveToFirst() }
        return false
    }

    private fun descrizione(a: Appuntamento, dpp: LocalDate?): String {
        val righe = ArrayList<String>()
        val sett = Gravidanza.settimana(dpp, a.data)
        if (sett != null) righe.add("Settimana $sett")
        if (a.medico.isNotBlank()) righe.add("Con: " + a.medico.trim())
        if (a.note.isNotBlank()) righe.add(a.note.trim())
        righe.add("(dall'app Pancy)")
        return righe.joinToString("\n")
    }

    private fun valori(a: Appuntamento, dpp: LocalDate?): ContentValues {
        val v = ContentValues()
        v.put(CalendarContract.Events.TITLE, a.titolo)
        v.put(CalendarContract.Events.EVENT_LOCATION, a.luogo)
        v.put(CalendarContract.Events.DESCRIPTION, descrizione(a, dpp))
        val ora = a.ora
        if (ora == null) {
            // evento di tutto il giorno: il calendario lo vuole a mezzanotte in UTC
            val inizio = a.data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            v.put(CalendarContract.Events.ALL_DAY, 1)
            v.put(CalendarContract.Events.DTSTART, inizio)
            v.put(CalendarContract.Events.DTEND, inizio + 24L * 60 * 60 * 1000)
            v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        } else {
            val zona = ZoneId.systemDefault()
            val inizio = a.data.atTime(ora).atZone(zona).toInstant().toEpochMilli()
            v.put(CalendarContract.Events.ALL_DAY, 0)
            v.put(CalendarContract.Events.DTSTART, inizio)
            v.put(CalendarContract.Events.DTEND, inizio + DURATA_MIN * 60_000L)
            v.put(CalendarContract.Events.EVENT_TIMEZONE, zona.id)
        }
        return v
    }

    /**
     * Crea o aggiorna l'evento dell'appuntamento e ne restituisce l'id.
     * [promemoriaMin]: minuti di preavviso della notifica, 0 o meno = nessuna notifica.
     */
    fun scrivi(ctx: Context, calId: Long, a: Appuntamento, dpp: LocalDate?, promemoriaMin: Int): Long {
        val cr = ctx.contentResolver
        val v = valori(a, dpp)
        val vecchio = a.evento
        val id: Long
        if (vecchio != null && esiste(ctx, vecchio, calId)) {
            cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, vecchio), v, null, null)
            id = vecchio
        } else {
            v.put(CalendarContract.Events.CALENDAR_ID, calId)
            val uri = cr.insert(CalendarContract.Events.CONTENT_URI, v)
                ?: throw IllegalStateException("Evento non creato")
            id = ContentUris.parseId(uri)
        }

        // promemoria: si tolgono quelli vecchi e si mette quello scelto
        cr.delete(CalendarContract.Reminders.CONTENT_URI, CalendarContract.Reminders.EVENT_ID + " = ?", arrayOf(id.toString()))
        if (promemoriaMin > 0) {
            val r = ContentValues()
            r.put(CalendarContract.Reminders.EVENT_ID, id)
            r.put(CalendarContract.Reminders.MINUTES, if (a.ora == null) PROMEMORIA_SENZA_ORA_MIN else promemoriaMin)
            r.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            cr.insert(CalendarContract.Reminders.CONTENT_URI, r)
        }
        return id
    }

    fun elimina(ctx: Context, eventoId: Long) {
        ctx.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventoId), null, null)
    }
}
