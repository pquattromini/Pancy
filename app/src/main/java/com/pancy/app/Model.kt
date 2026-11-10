package com.pancy.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Scelte possibili per il promemoria: minuti di preavviso (0 = nessuno) ed etichetta. */
val PROMEMORIA = listOf(
    0 to "Nessuno",
    60 to "1 ora prima",
    120 to "2 ore prima",
    24 * 60 to "Il giorno prima",
)

private const val F_APPUNTAMENTI = "appuntamenti.json"
private const val F_ECOGRAFIE = "ecografie.json"
private const val F_MEDIA = "media.json"
private const val F_PESI = "pesi.json"
private const val F_CONSIGLI = "consigli.json"

/**
 * Stato dell'app. Ogni sezione ha il suo file JSON (vedi [Archivio]), le impostazioni stanno nelle preferenze.
 * C'è un solo Model per tutta l'app, così ruotando il telefono non si perde la schermata aperta.
 */
class Model private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("pancy", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var appuntamenti by mutableStateOf(
        Appuntamento.ordina(Archivio.carica(ctx, F_APPUNTAMENTI, "appuntamenti") { Appuntamento.daJson(it) })
    )
        private set
    var ecografie by mutableStateOf(Ecografia.ordina(Archivio.carica(ctx, F_ECOGRAFIE, "ecografie") { Ecografia.daJson(it) }))
        private set
    var media by mutableStateOf(Media.ordina(Archivio.carica(ctx, F_MEDIA, "media") { Media.daJson(it) }))
        private set
    var pesi by mutableStateOf(Peso.ordina(Archivio.carica(ctx, F_PESI, "pesi") { Peso.daJson(it) }))
        private set
    var consigli by mutableStateOf(Consiglio.ordina(Archivio.carica(ctx, F_CONSIGLI, "consigli") { Consiglio.daJson(it) }))
        private set

    /** Data presunta del parto, se l'hai inserita. */
    var dpp by mutableStateOf(
        prefs.getString("dpp", null)?.let {
            try { LocalDate.parse(it) } catch (e: Exception) { null }
        }
    )
        private set

    /** Peso della mamma prima della gravidanza, se inserito (serve per calcolare l'aumento). */
    var pesoIniziale by mutableStateOf(prefs.getString("pesoIniziale", null)?.toDoubleOrNull())
        private set

    /** Il calendario del telefono in cui copiare gli appuntamenti (null = non li copio). */
    var calendario by mutableStateOf(prefs.getLong("calId", -1L).takeIf { it >= 0 })
        private set

    /** Minuti di preavviso del promemoria nel calendario (0 = nessuno). */
    var promemoria by mutableIntStateOf(prefs.getInt("promemoria", 24 * 60))
        private set

    /** Quante copie di foto e video sono in corso (più di 0 = mostra "sto copiando"). */
    var importazioni by mutableIntStateOf(0)
        private set

    // ---------------- Navigazione ----------------
    // Una pila di schermate: la prima è sempre la sezione aperta, "indietro" toglie l'ultima.

    var pila by mutableStateOf<List<Schermata>>(listOf(Schermata.Radice(Sezione.AGENDA)))
        private set
    val schermata: Schermata get() = pila.last()
    val sezione: Sezione get() = (pila.first() as? Schermata.Radice)?.sezione ?: Sezione.AGENDA
    /** Vero se "indietro" deve restare nell'app (altrimenti il telefono chiude l'app). */
    val puoTornare: Boolean get() = pila.size > 1 || sezione != Sezione.AGENDA

    fun vai(s: Schermata) { pila = pila + s }
    fun sostituisci(s: Schermata) { pila = pila.dropLast(1) + s }
    fun apriSezione(s: Sezione) { pila = listOf(Schermata.Radice(s)) }
    fun indietro() {
        pila = if (pila.size > 1) pila.dropLast(1) else listOf(Schermata.Radice(Sezione.AGENDA))
    }

    /** Agenda: 0 = in programma, 1 = passati. */
    var scheda by mutableIntStateOf(0)
    /** Storico: 0 = mamma, 1 = bambino. */
    var schedaStorico by mutableIntStateOf(0)
    /** Messaggio breve da mostrare in basso (poi torna null). */
    var messaggio by mutableStateOf<String?>(null)

    // ---------------- Appuntamenti ----------------

    fun trova(id: String): Appuntamento? = appuntamenti.firstOrNull { it.id == id }

    /** Salva un appuntamento nuovo o modificato (e, se attivo, lo copia nel calendario). */
    fun salva(a: Appuntamento) {
        val pronto = conCalendario(a) ?: a.also {
            messaggio = "Salvato, ma non sono riuscito a scriverlo nel calendario del telefono."
        }
        appuntamenti = Appuntamento.ordina(appuntamenti.filter { it.id != a.id } + pronto)
        salvaAppuntamenti()
    }

    fun elimina(id: String) {
        val a = trova(id) ?: return
        val ev = a.evento
        if (ev != null) {
            try { CalendarSync.elimina(ctx, ev) } catch (e: Exception) { }
        }
        appuntamenti = appuntamenti.filter { it.id != id }
        salvaAppuntamenti()
        // l'ecografia collegata resta, ma non punta più all'appuntamento
        if (ecografie.any { it.appuntamento == id }) {
            ecografie = ecografie.map { if (it.appuntamento == id) it.copy(appuntamento = null) else it }
            salvaEcografie()
        }
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
        salvaAppuntamenti()
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
        salvaAppuntamenti()
    }

    // ---------------- Ecografie ----------------

    fun ecografia(id: String): Ecografia? = ecografie.firstOrNull { it.id == id }

    /** L'ecografia nata da quell'appuntamento, se c'è. */
    fun ecografiaDi(appuntamento: String): Ecografia? = ecografie.firstOrNull { it.appuntamento == appuntamento }

    fun salvaEcografia(e: Ecografia) {
        ecografie = Ecografia.ordina(ecografie.filter { it.id != e.id } + e)
        salvaEcografie()
    }

    /** Elimina l'ecografia. Le sue immagini restano nella sezione Foto. */
    fun eliminaEcografia(id: String) {
        ecografie = ecografie.filter { it.id != id }
        salvaEcografie()
        if (media.any { it.ecografia == id }) {
            media = media.map { if (it.ecografia == id) it.copy(ecografia = null) else it }
            salvaMedia()
        }
        messaggio = "Ecografia eliminata."
    }

    // ---------------- Foto e video ----------------

    fun mediaDi(ecografia: String): List<Media> = media.filter { it.ecografia == ecografia }

    /** Copia nell'app le foto e i video scelti dalla galleria (in sottofondo: i video possono essere grandi). */
    fun importa(uris: List<Uri>, ecografia: String?) {
        if (uris.isEmpty()) return
        importazioni += 1
        scope.launch {
            val nuovi = withContext(Dispatchers.IO) { uris.mapNotNull { Galleria.copia(ctx, it, ecografia) } }
            aggiungiMedia(nuovi)
            importazioni -= 1
            val falliti = uris.size - nuovi.size
            messaggio = when {
                nuovi.isEmpty() -> "Non sono riuscito a copiare i file scelti."
                falliti > 0 -> "Aggiunti ${nuovi.size} elementi, $falliti non sono riuscito a copiarli."
                nuovi.size == 1 -> "Aggiunto 1 elemento."
                else -> "Aggiunti ${nuovi.size} elementi."
            }
        }
    }

    /** Dopo la fotocamera: tiene il file se è stato scritto davvero, altrimenti lo cancella. */
    fun dopoScatto(f: File, video: Boolean, ecografia: String?) {
        if (f.exists() && f.length() > 0) {
            aggiungiMedia(listOf(Media(file = f.name, video = video, data = LocalDate.now(), ecografia = ecografia)))
            messaggio = if (video) "Video aggiunto." else "Foto aggiunta."
        } else {
            f.delete()
        }
    }

    private fun aggiungiMedia(nuovi: List<Media>) {
        if (nuovi.isEmpty()) return
        media = Media.ordina(media + nuovi)
        salvaMedia()
    }

    fun aggiornaMedia(x: Media) {
        media = Media.ordina(media.map { if (it.id == x.id) x else it })
        salvaMedia()
    }

    fun eliminaMedia(id: String) {
        val x = media.firstOrNull { it.id == id } ?: return
        Galleria.elimina(ctx, x)
        media = media.filter { it.id != id }
        salvaMedia()
        messaggio = if (x.video) "Video eliminato." else "Foto eliminata."
    }

    // ---------------- Pesi ----------------

    fun salvaPeso(p: Peso) {
        pesi = Peso.ordina(pesi.filter { it.id != p.id } + p)
        salvaPesi()
    }

    fun eliminaPeso(id: String) {
        pesi = pesi.filter { it.id != id }
        salvaPesi()
        messaggio = "Peso eliminato."
    }

    fun impostaPesoIniziale(kg: Double?) {
        pesoIniziale = kg
        prefs.edit().putString("pesoIniziale", kg?.toString()).apply()
    }

    // ---------------- Consigli ----------------

    fun salvaConsiglio(c: Consiglio) {
        consigli = Consiglio.ordina(consigli.filter { it.id != c.id } + c)
        salvaConsigli()
    }

    fun eliminaConsiglio(id: String) {
        consigli = consigli.filter { it.id != id }
        salvaConsigli()
        messaggio = "Consiglio eliminato."
    }

    // ---------------- Salvataggio ----------------

    private fun salvaAppuntamenti() = Archivio.salva(ctx, F_APPUNTAMENTI, "appuntamenti", appuntamenti) { it.toJson() }
    private fun salvaEcografie() = Archivio.salva(ctx, F_ECOGRAFIE, "ecografie", ecografie) { it.toJson() }
    private fun salvaMedia() = Archivio.salva(ctx, F_MEDIA, "media", media) { it.toJson() }
    private fun salvaPesi() = Archivio.salva(ctx, F_PESI, "pesi", pesi) { it.toJson() }
    private fun salvaConsigli() = Archivio.salva(ctx, F_CONSIGLI, "consigli", consigli) { it.toJson() }

    companion object {
        @Volatile private var istanza: Model? = null

        fun di(ctx: Context): Model =
            istanza ?: synchronized(this) {
                istanza ?: Model(ctx.applicationContext).also { istanza = it }
            }
    }
}
