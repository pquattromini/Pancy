package com.pancy.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** Le schermate dell'app. */
sealed interface Schermata {
    data object Lista : Schermata
    data class Dettaglio(val id: String) : Schermata
    /** [id] null = nuovo appuntamento. */
    data class Modifica(val id: String?) : Schermata
    data object Impostazioni : Schermata
}

/** Scelte possibili per il promemoria: minuti di preavviso (0 = nessuno) ed etichetta. */
val PROMEMORIA = listOf(
    0 to "Nessuno",
    60 to "1 ora prima",
    120 to "2 ore prima",
    24 * 60 to "Il giorno prima",
)

/**
 * Stato dell'app. Gli appuntamenti stanno in appuntamenti.json, le impostazioni nelle preferenze.
 * C'è un solo Model per tutta l'app, così ruotando il telefono non si perde la schermata aperta.
 */
class Model private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("pancy", Context.MODE_PRIVATE)

    var appuntamenti by mutableStateOf(Appuntamento.ordina(Archivio.carica(ctx)))
        private set

    /** Data presunta del parto, se l'hai inserita. */
    var dpp by mutableStateOf(
        prefs.getString("dpp", null)?.let {
            try { LocalDate.parse(it) } catch (e: Exception) { null }
        }
    )
        private set

    /** Il calendario del telefono in cui copiare gli appuntamenti (null = non li copio). */
    var calendario by mutableStateOf(prefs.getLong("calId", -1L).takeIf { it >= 0 })
        private set

    /** Minuti di preavviso del promemoria nel calendario (0 = nessuno). */
    var promemoria by mutableIntStateOf(prefs.getInt("promemoria", 24 * 60))
        private set

    var schermata by mutableStateOf<Schermata>(Schermata.Lista)
    /** Nella lista: 0 = in programma, 1 = passati. */
    var scheda by mutableIntStateOf(0)
    /** Messaggio breve da mostrare in basso (poi torna null). */
    var messaggio by mutableStateOf<String?>(null)

    fun trova(id: String): Appuntamento? = appuntamenti.firstOrNull { it.id == id }

    fun indietro() {
        schermata = when (val s = schermata) {
            is Schermata.Modifica -> if (s.id != null && trova(s.id) != null) Schermata.Dettaglio(s.id) else Schermata.Lista
            else -> Schermata.Lista
        }
    }

    /** Salva un appuntamento nuovo o modificato (e, se attivo, lo copia nel calendario). */
    fun salva(a: Appuntamento) {
        val pronto = conCalendario(a) ?: a.also {
            messaggio = "Salvato, ma non sono riuscito a scriverlo nel calendario del telefono."
        }
        appuntamenti = Appuntamento.ordina(appuntamenti.filter { it.id != a.id } + pronto)
        Archivio.salva(ctx, appuntamenti)
    }

    fun elimina(id: String) {
        val a = trova(id) ?: return
        val ev = a.evento
        if (ev != null) {
            try { CalendarSync.elimina(ctx, ev) } catch (e: Exception) { }
        }
        appuntamenti = appuntamenti.filter { it.id != id }
        Archivio.salva(ctx, appuntamenti)
        messaggio = "Appuntamento eliminato."
    }

    fun impostaDpp(d: LocalDate?) {
        dpp = d
        prefs.edit().putString("dpp", d?.toString()).apply()
        // la settimana è scritta negli eventi del calendario: la aggiorno
        if (calendario != null) sincronizzaFuturi()
    }

    /** Inizia a copiare gli appuntamenti in quel calendario. Se prima usavi un altro calendario, lo libera. */
    fun attivaCalendario(id: Long) {
        val prima = calendario
        if (prima != null && prima != id) togliEventi()
        calendario = id
        prefs.edit().putLong("calId", id).apply()
        val (fatti, errori) = sincronizzaFuturi()
        messaggio = when {
            errori > 0 -> "Copiati $fatti appuntamenti nel calendario, $errori non sono riuscito a copiarli."
            fatti == 1 -> "Copiato 1 appuntamento nel calendario."
            else -> "Copiati $fatti appuntamenti nel calendario."
        }
    }

    /** Smette di copiare. Se [togli] è vero, cancella anche gli eventi già creati. */
    fun disattivaCalendario(togli: Boolean) {
        if (togli) togliEventi()
        calendario = null
        prefs.edit().remove("calId").apply()
    }

    fun impostaPromemoria(minuti: Int) {
        promemoria = minuti
        prefs.edit().putInt("promemoria", minuti).apply()
        if (calendario != null) sincronizzaFuturi()
    }

    /** L'appuntamento con l'id dell'evento aggiornato; lo stesso se il calendario è spento; null se la scrittura fallisce. */
    private fun conCalendario(a: Appuntamento): Appuntamento? {
        val cal = calendario ?: return a
        return try {
            a.copy(evento = CalendarSync.scrivi(ctx, cal, a, dpp, promemoria))
        } catch (e: Exception) {
            null
        }
    }

    /** Copia (o aggiorna) nel calendario gli appuntamenti da oggi in avanti. Restituisce riusciti ed errori. */
    private fun sincronizzaFuturi(): Pair<Int, Int> {
        val oggi = LocalDate.now()
        var fatti = 0
        var errori = 0
        appuntamenti = appuntamenti.map { a ->
            if (a.data.isBefore(oggi)) {
                a
            } else {
                val n = conCalendario(a)
                if (n == null) errori++ else fatti++
                n ?: a
            }
        }
        Archivio.salva(ctx, appuntamenti)
        return Pair(fatti, errori)
    }

    private fun togliEventi() {
        appuntamenti = appuntamenti.map { a ->
            val ev = a.evento
            if (ev != null) {
                try { CalendarSync.elimina(ctx, ev) } catch (e: Exception) { }
                a.copy(evento = null)
            } else {
                a
            }
        }
        Archivio.salva(ctx, appuntamenti)
    }

    companion object {
        @Volatile private var istanza: Model? = null

        fun di(ctx: Context): Model =
            istanza ?: synchronized(this) {
                istanza ?: Model(ctx.applicationContext).also { istanza = it }
            }
    }
}
