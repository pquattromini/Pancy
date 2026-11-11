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
import java.time.ZoneId
import java.util.Locale

/** Scelte possibili per il promemoria: minuti di preavviso (0 = nessuno) ed etichetta. */
val PROMEMORIA = listOf(
    0 to "Nessuno",
    60 to "1 ora prima",
    120 to "2 ore prima",
    24 * 60 to "Il giorno prima",
)

/** Parole che di solito ci sono negli appuntamenti della gravidanza: servono per importare solo quelli da un calendario personale. */
const val PAROLE_PREDEFINITE =
    "pancy, ecograf, visita, ginecolog, ostetric, esami, esame, prelievo, analisi, tampone, curva glicemica, " +
        "translucenza, morfologica, tracciato, ctg, preparto, consultorio, gravidanza"

private const val F_APPUNTAMENTI = "appuntamenti.json"
private const val F_ECOGRAFIE = "ecografie.json"
private const val F_MEDIA = "media.json"
private const val F_PESI = "pesi.json"
private const val F_CONSIGLI = "consigli.json"
private const val F_CONVERSAZIONI = "conversazioni.json"

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
    var conversazioni by mutableStateOf(
        Conversazione.ordina(Archivio.carica(ctx, F_CONVERSAZIONI, "conversazioni") { Conversazione.daJson(it) })
    )
        private set

    /** La chiave sta in un file di preferenze a parte, escluso dal backup (vedi res/xml). */
    private val segreti = ctx.getSharedPreferences("segreti", Context.MODE_PRIVATE)

    /** Chiave API di OpenAI, se inserita. */
    var chiaveIA by mutableStateOf(segreti.getString("openai", null))
        private set

    /** Modello OpenAI da usare. */
    var modelloIA by mutableStateOf(prefs.getString("modelloIA", null) ?: MODELLO_PREDEFINITO)
        private set

    /** Conversazioni che aspettano la risposta dell'IA. */
    var inAttesa by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Vero mentre si prova la chiave dalle impostazioni. */
    var verificaInCorso by mutableStateOf(false)
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

    /** Calendari da cui importare gli appuntamenti (vuoto = importazione spenta). */
    var importaDa by mutableStateOf(
        prefs.getString("importaDa", null).orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    )
        private set

    /** Se vero, si importano solo gli eventi che contengono una delle [paroleImport]. */
    var importaFiltro by mutableStateOf(prefs.getBoolean("importaFiltro", true))
        private set

    /** Parole da cercare negli eventi, separate da virgole. */
    var paroleImport by mutableStateOf(prefs.getString("paroleImport", null) ?: PAROLE_PREDEFINITE)
        private set

    var importInCorso by mutableStateOf(false)
        private set

    /** Google Drive collegato (l'utente ha dato l'accesso almeno una volta). */
    var driveCollegato by mutableStateOf(prefs.getBoolean("driveCollegato", false))
        private set
    /** Cartelle di Drive per le foto e per le ecografie: id e nome da mostrare. */
    var cartellaFoto by mutableStateOf(prefs.getString("driveFoto", null))
        private set
    var nomeCartellaFoto by mutableStateOf(prefs.getString("driveFotoNome", null) ?: "")
        private set
    var cartellaEco by mutableStateOf(prefs.getString("driveEco", null))
        private set
    var nomeCartellaEco by mutableStateOf(prefs.getString("driveEcoNome", null) ?: "")
        private set
    var driveInSync by mutableStateOf(false)
        private set
    /** Quando è finita l'ultima sincronizzazione riuscita (millisecondi, 0 = mai). */
    var driveUltima by mutableStateOf(prefs.getLong("driveUltima", 0L))
        private set
    /** Problema da mostrare nelle impostazioni (per esempio "va ricollegato"), null se tutto a posto. */
    var driveProblema by mutableStateOf<String?>(null)
        private set
    /** Il problema si risolve ridando (o confermando) l'accesso a Google: basta «Ricollega». */
    var driveServeConsenso by mutableStateOf(false)
        private set
    /** File di Drive da mettere nel cestino (foto eliminate nell'app quando non c'era rete). */
    private var daCestinare: Set<String> = prefs.getStringSet("driveDaCestinare", null)?.toSet() ?: emptySet()
    private var ultimaSyncAuto = 0L
    private var syncDiNuovo = false

    /** Eventi importati e poi eliminati nell'app: non vanno più importati. */
    private var ignorati: Set<String> = prefs.getStringSet("importIgnorati", null)?.toSet() ?: emptySet()
    private var ultimaImportazione = 0L

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
        val esterno = a.esterno
        if (esterno != null) {
            ignorati = ignorati + esterno
            prefs.edit().putStringSet("importIgnorati", HashSet(ignorati)).apply()
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
        // quelli importati sono già nel calendario: non li ricopio
        if (a.esterno != null) return a
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

    // ---------------- Importazione dal calendario ----------------

    /** Sceglie i calendari da cui importare (insieme vuoto = smette di importare) e importa subito. */
    fun impostaImport(calendari: Set<Long>) {
        importaDa = calendari
        prefs.edit().putString("importaDa", calendari.joinToString(",")).apply()
        if (calendari.isNotEmpty()) {
            ultimaImportazione = 0L
            importaDalCalendario(silenzioso = false)
        }
    }

    fun impostaFiltroImport(attivo: Boolean, parole: String) {
        importaFiltro = attivo
        paroleImport = parole.trim().ifEmpty { PAROLE_PREDEFINITE }
        prefs.edit().putBoolean("importaFiltro", attivo).putString("paroleImport", paroleImport).apply()
    }

    private fun corrisponde(ev: EventoEsterno): Boolean {
        if (!importaFiltro) return true
        val testo = (ev.titolo + " " + ev.luogo + " " + ev.descrizione).lowercase(Locale.ITALY)
        return paroleImport.split(",")
            .map { it.trim().lowercase(Locale.ITALY) }
            .filter { it.isNotEmpty() }
            .any { testo.contains(it) }
    }

    /** Il tipo di appuntamento, indovinato dal titolo dell'evento. */
    private fun indovinaTipo(titolo: String): Tipo {
        val t = titolo.lowercase(Locale.ITALY)
        fun ha(vararg parole: String) = parole.any { t.contains(it) }
        return when {
            ha("ecograf", "morfologica", "translucenza") || t == "eco" || t.startsWith("eco ") -> Tipo.ECOGRAFIA
            ha("esam", "prelievo", "analisi", "tampone", "curva", "sangue", "urine", "test") -> Tipo.ESAMI
            ha("corso", "preparto", "incontro") -> Tipo.CORSO
            ha("visita", "ginecolog", "ostetric", "controllo") -> Tipo.VISITA
            else -> Tipo.ALTRO
        }
    }

    private fun calendarioDi(chiave: String): Long? = chiave.substringBefore(":").toLongOrNull()
    private fun eventoDi(chiave: String): Long? = chiave.substringAfter(":").substringBefore("@").toLongOrNull()

    /**
     * Legge i calendari scelti e aggiunge, aggiorna o toglie gli appuntamenti importati.
     * [silenzioso]: si usa all'apertura dell'app; non scrive nulla se non cambia niente.
     */
    fun importaDalCalendario(silenzioso: Boolean) {
        val calendari = importaDa
        if (calendari.isEmpty() || importInCorso) return
        if (!CalendarSync.haPermesso(ctx)) {
            if (!silenzioso) messaggio = "Serve il permesso per leggere il calendario: riattiva l'importazione nelle Impostazioni."
            return
        }
        val adesso = System.currentTimeMillis()
        if (silenzioso && adesso - ultimaImportazione < 30_000) return
        ultimaImportazione = adesso

        // periodo letto: dall'inizio della gravidanza (o da 6 mesi fa) a poco più di un anno da oggi
        val oggi = LocalDate.now()
        val inizioGravidanza = dpp?.let { Gravidanza.inizio(it) }
        val dal = if (inizioGravidanza != null && inizioGravidanza.isBefore(oggi)) inizioGravidanza else oggi.minusDays(180)
        val al = oggi.plusDays(400)
        val zona = ZoneId.systemDefault()
        val da = dal.atStartOfDay(zona).toInstant().toEpochMilli()
        val a = al.atStartOfDay(zona).toInstant().toEpochMilli()
        // gli appuntamenti importati che dovrebbero comparire in quel periodo
        val daControllare = appuntamenti.mapNotNull { x ->
            val k = x.esterno
            if (k != null && calendarioDi(k) in calendari && !x.data.isBefore(dal) && x.data.isBefore(al)) k else null
        }

        importInCorso = true
        scope.launch {
            val letto = try {
                withContext(Dispatchers.IO) {
                    val eventi = CalendarSync.leggiEventi(ctx, calendari, da, a)
                    val trovati = eventi.map { it.chiave }.toSet()
                    // non più nel periodo: sono spariti davvero solo se l'evento non esiste più
                    val spariti = daControllare.filter { k ->
                        val ev = eventoDi(k)
                        k !in trovati && (ev == null || !CalendarSync.eventoEsiste(ctx, ev))
                    }
                    Pair(eventi, spariti.toSet())
                }
            } catch (e: Exception) {
                null
            }
            importInCorso = false
            if (letto == null) {
                if (!silenzioso) messaggio = "Non sono riuscito a leggere il calendario."
            } else {
                applicaImport(letto.first, letto.second, silenzioso)
            }
        }
    }

    private fun applicaImport(eventi: List<EventoEsterno>, spariti: Set<String>, silenzioso: Boolean) {
        // gli eventi creati dall'app (copia nel calendario) non si importano
        val esportati = appuntamenti.mapNotNull { it.evento }.toSet()
        val lista = appuntamenti.toMutableList()
        var nuovi = 0
        var aggiornati = 0
        var tolti = 0
        val tolte = HashSet<String>()
        val viste = HashSet<String>()
        for (ev in eventi) {
            if (!viste.add(ev.chiave)) continue
            if (ev.eventId in esportati || ev.chiave in ignorati) continue
            val i = lista.indexOfFirst { it.esterno == ev.chiave }
            if (i < 0) {
                if (!corrisponde(ev)) continue
                lista.add(
                    Appuntamento(
                        tipo = indovinaTipo(ev.titolo),
                        titolo = ev.titolo.ifBlank { "Appuntamento" },
                        data = ev.data,
                        ora = ev.ora,
                        luogo = ev.luogo,
                        note = ev.descrizione.take(2000),
                        esterno = ev.chiave,
                        firma = ev.firma,
                    )
                )
                nuovi++
            } else if (lista[i].firma != ev.firma) {
                // cambiato nel calendario: aggiorno titolo, data, ora e luogo; tipo, medico, note ed esito restano quelli dell'app
                val x = lista[i]
                lista[i] = x.copy(titolo = ev.titolo.ifBlank { x.titolo }, data = ev.data, ora = ev.ora, luogo = ev.luogo, firma = ev.firma)
                aggiornati++
            }
        }
        for (k in spariti) {
            val i = lista.indexOfFirst { it.esterno == k }
            if (i < 0) continue
            val x = lista[i]
            if (!x.passato() && x.esito.isBlank()) {
                // annullato nel calendario: lo tolgo anche dall'app
                lista.removeAt(i)
                tolte.add(x.id)
                tolti++
            } else {
                // già fatto o con un esito: lo tengo, ma non è più collegato al calendario
                lista[i] = x.copy(esterno = null, firma = null)
            }
        }
        if (nuovi + aggiornati + tolti > 0 || spariti.isNotEmpty()) {
            appuntamenti = Appuntamento.ordina(lista)
            salvaAppuntamenti()
        }
        if (tolte.isNotEmpty() && ecografie.any { it.appuntamento in tolte }) {
            ecografie = ecografie.map { if (it.appuntamento in tolte) it.copy(appuntamento = null) else it }
            salvaEcografie()
        }
        val parti = listOfNotNull(
            when (nuovi) { 0 -> null; 1 -> "1 appuntamento nuovo"; else -> "$nuovi appuntamenti nuovi" },
            when (aggiornati) { 0 -> null; 1 -> "1 aggiornato"; else -> "$aggiornati aggiornati" },
            when (tolti) { 0 -> null; 1 -> "1 tolto perché annullato"; else -> "$tolti tolti perché annullati" },
        )
        if (parti.isNotEmpty()) {
            messaggio = "Dal calendario: " + parti.joinToString(", ") + "."
        } else if (!silenzioso) {
            messaggio = "Nessuna novità nel calendario."
        }
    }

    // ---------------- Google Drive ----------------

    /** L'utente ha dato l'accesso a Drive: salvo e, se le cartelle sono già scelte, sincronizzo. */
    fun driveAutorizzato(token: String?) {
        Drive.impostaToken(token)
        driveCollegato = true
        driveProblema = null
        driveServeConsenso = false
        prefs.edit().putBoolean("driveCollegato", true).apply()
        if (cartellaFoto != null) sincronizzaDrive(silenzioso = false, subito = true)
        else messaggio = "Google Drive collegato: ora scegli le cartelle."
    }

    /** Smette di sincronizzare. Le foto restano nell'app e su Drive. */
    fun scollegaDrive() {
        driveCollegato = false
        driveProblema = null
        driveServeConsenso = false
        Drive.impostaToken(null)
        prefs.edit().putBoolean("driveCollegato", false).apply()
    }

    /** Sceglie la cartella delle foto ([eco] falso) o delle ecografie ([eco] vero) e sincronizza. */
    fun impostaCartellaDrive(eco: Boolean, id: String, nome: String) {
        if (eco) {
            cartellaEco = id
            nomeCartellaEco = nome
            prefs.edit().putString("driveEco", id).putString("driveEcoNome", nome).apply()
        } else {
            cartellaFoto = id
            nomeCartellaFoto = nome
            prefs.edit().putString("driveFoto", id).putString("driveFotoNome", nome).apply()
        }
        sincronizzaDrive(silenzioso = false, subito = true)
    }

    /**
     * Sincronizza foto ed ecografie con le cartelle di Drive.
     * [silenzioso]: niente messaggi se non arriva nulla di nuovo; [subito]: anche se l'ultima è di pochi secondi fa.
     */
    fun sincronizzaDrive(silenzioso: Boolean, subito: Boolean = false) {
        if (!driveCollegato) return
        val fotoId = cartellaFoto ?: return
        if (driveInSync) {
            syncDiNuovo = true
            return
        }
        val adesso = System.currentTimeMillis()
        if (!subito && adesso - ultimaSyncAuto < 60_000) return
        ultimaSyncAuto = adesso
        val ecoId = cartellaEco
        val istantanea = media
        val ecos = ecografie
        val cestino = daCestinare
        driveInSync = true
        scope.launch {
            try {
                val esito = withContext(Dispatchers.IO) { SincroniaDrive.esegui(ctx, istantanea, ecos, cestino, fotoId, ecoId) }
                applicaSync(esito, silenzioso)
                driveProblema = null
                driveServeConsenso = false
            } catch (e: ErroreDrive) {
                val primaVolta = driveProblema != e.message
                driveProblema = e.message
                driveServeConsenso = e.serveConsenso || e.codice == 401
                if (!silenzioso || primaVolta) messaggio = e.message
            } catch (e: java.io.IOException) {
                if (!silenzioso) messaggio = "Non riesco a raggiungere Google Drive: controlla la connessione."
            } catch (e: Exception) {
                if (!silenzioso) messaggio = "Sincronizzazione con Drive non riuscita: " + (e.message ?: e.javaClass.simpleName)
            }
            driveInSync = false
            if (syncDiNuovo) {
                syncDiNuovo = false
                sincronizzaDrive(silenzioso = true, subito = true)
            }
        }
    }

    private fun applicaSync(es: EsitoSync, silenzioso: Boolean) {
        val ecos = ecografie.toMutableList()
        var ecoCambiate = false
        // l'ecografia di quel giorno; se non c'è la creo (con il titolo dell'appuntamento, se in agenda c'è)
        fun ecografiaPer(d: LocalDate): String {
            val esistente = ecos.firstOrNull { it.data == d }
            if (esistente != null) return esistente.id
            val app = appuntamenti.firstOrNull { a -> a.tipo == Tipo.ECOGRAFIA && a.data == d && ecos.none { it.appuntamento == a.id } }
            val nuova = Ecografia(titolo = app?.titolo ?: "Ecografia", data = d, appuntamento = app?.id)
            ecos.add(nuova)
            ecoCambiate = true
            return nuova.id
        }
        fun dataEco(f: FileDrive): LocalDate? = try {
            if (f.propEco.isEmpty()) null else LocalDate.parse(f.propEco)
        } catch (e: Exception) {
            null
        }

        val suDrive = HashMap<String, Pair<FileDrive, String>>()
        for (f in es.foto) suDrive[f.id] = Pair(f, "foto")
        for (f in es.eco) suDrive[f.id] = Pair(f, "eco")
        val appenaCaricati = es.caricati.values.map { it.first }.toHashSet()

        var tolti = 0
        val lista = ArrayList<Media>()
        for (m0 in media) {
            val car = es.caricati[m0.id]
            if (car != null) {
                lista.add(m0.copy(driveId = car.first, cartellaDrive = car.second))
                continue
            }
            val d = m0.driveId
            if (d == null) {
                lista.add(m0)
                continue
            }
            val trovato = suDrive[d]
            if (trovato == null) {
                if (d in appenaCaricati) {
                    lista.add(m0)
                } else {
                    // non c'è più su Drive: la tolgo anche dall'app
                    Galleria.elimina(ctx, m0)
                    tolti++
                }
                continue
            }
            val cart = es.cartelle[m0.id] ?: trovato.second
            lista.add(
                if (m0.id in es.spostatiSuDrive) {
                    if (cart == "eco") {
                        m0.copy(cartellaDrive = cart, ecografia = m0.ecografia ?: ecografiaPer(dataEco(trovato.first) ?: m0.data))
                    } else {
                        m0.copy(cartellaDrive = cart, ecografia = null)
                    }
                } else {
                    m0.copy(cartellaDrive = cart)
                }
            )
        }

        // file nuovi su Drive
        val conosciuti = lista.mapNotNull { it.driveId }.toHashSet()
        var nuovi = 0
        for ((id, coppia) in suDrive) {
            if (id in conosciuti || id in daCestinare) continue
            val f = coppia.first
            val cart = coppia.second
            val data = SincroniaDrive.dataDi(f)
            val de = dataEco(f)
            val ecografia = when {
                cart == "eco" -> ecografiaPer(de ?: data)
                de != null -> ecografiaPer(de)
                else -> null
            }
            lista.add(
                Media(
                    file = "d-" + id + "." + SincroniaDrive.estensione(f),
                    video = f.mime.startsWith("video"),
                    data = data,
                    didascalia = f.descrizione,
                    ecografia = ecografia,
                    aggiunto = SincroniaDrive.creatoMs(f),
                    driveId = id,
                    cartellaDrive = cart,
                )
            )
            nuovi++
        }

        media = Media.ordina(lista)
        salvaMedia()
        if (ecoCambiate) {
            ecografie = Ecografia.ordina(ecos)
            salvaEcografie()
        }
        if (es.cestinati.isNotEmpty()) {
            daCestinare = daCestinare - es.cestinati
            prefs.edit().putStringSet("driveDaCestinare", HashSet(daCestinare)).apply()
        }
        driveUltima = System.currentTimeMillis()
        prefs.edit().putLong("driveUltima", driveUltima).apply()

        val parti = listOfNotNull(
            when (nuovi) { 0 -> null; 1 -> "1 foto o video nuovo"; else -> "$nuovi foto e video nuovi" },
            when (tolti) { 0 -> null; 1 -> "1 tolto"; else -> "$tolti tolti" },
        )
        messaggio = when {
            es.errori > 0 && !silenzioso -> "Sincronizzato, ma ${es.errori} file non sono passati: ${es.primoErrore ?: ""}"
            parti.isNotEmpty() -> "Da Google Drive: " + parti.joinToString(", ") + "."
            !silenzioso -> "Google Drive è sincronizzato."
            else -> messaggio
        }
    }

    // ---------------- Ecografie ----------------

    fun ecografia(id: String): Ecografia? = ecografie.firstOrNull { it.id == id }

    /** L'ecografia nata da quell'appuntamento, se c'è. */
    fun ecografiaDi(appuntamento: String): Ecografia? = ecografie.firstOrNull { it.appuntamento == appuntamento }

    fun salvaEcografia(e: Ecografia) {
        ecografie = Ecografia.ordina(ecografie.filter { it.id != e.id } + e)
        salvaEcografie()
        // se è cambiata la data, va aggiornata sulle sue immagini in Drive
        if (media.any { it.ecografia == e.id && it.driveId != null }) sincronizzaDrive(silenzioso = true, subito = true)
    }

    /** Elimina l'ecografia. Le sue immagini restano nella sezione Foto. */
    fun eliminaEcografia(id: String) {
        ecografie = ecografie.filter { it.id != id }
        salvaEcografie()
        if (media.any { it.ecografia == id }) {
            media = media.map { if (it.ecografia == id) it.copy(ecografia = null) else it }
            salvaMedia()
            // su Drive le sue immagini passano nella cartella delle foto
            sincronizzaDrive(silenzioso = true, subito = true)
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
            sincronizzaDrive(silenzioso = true, subito = true)
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
            sincronizzaDrive(silenzioso = true, subito = true)
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
        sincronizzaDrive(silenzioso = true, subito = true)
    }

    fun eliminaMedia(id: String) {
        val x = media.firstOrNull { it.id == id } ?: return
        Galleria.elimina(ctx, x)
        media = media.filter { it.id != id }
        salvaMedia()
        // su Drive va nel cestino (anche più tardi, se adesso non c'è rete)
        val did = x.driveId
        if (did != null && driveCollegato) {
            daCestinare = daCestinare + did
            prefs.edit().putStringSet("driveDaCestinare", HashSet(daCestinare)).apply()
            sincronizzaDrive(silenzioso = true, subito = true)
        }
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

    // ---------------- Chiedi all'IA ----------------

    fun conversazione(id: String): Conversazione? = conversazioni.firstOrNull { it.id == id }

    fun impostaChiaveIA(k: String?) {
        val pulita = k?.trim()?.takeIf { it.isNotEmpty() }
        chiaveIA = pulita
        segreti.edit().putString("openai", pulita).apply()
    }

    fun impostaModelloIA(nome: String) {
        val n = nome.trim().ifEmpty { MODELLO_PREDEFINITO }
        modelloIA = n
        prefs.edit().putString("modelloIA", n).apply()
    }

    private fun aggiornaConversazione(c: Conversazione) {
        conversazioni = Conversazione.ordina(conversazioni.filter { it.id != c.id } + c)
        salvaConversazioni()
    }

    private fun aggiungiMessaggio(id: String, msg: Messaggio) {
        val c = conversazione(id) ?: return
        aggiornaConversazione(c.copy(messaggi = c.messaggi + msg, aggiornata = System.currentTimeMillis()))
    }

    /**
     * Manda una domanda (con eventuali foto allegate) nella conversazione [id], o in una nuova se è null.
     * Restituisce l'id della conversazione.
     */
    fun invia(id: String?, testo: String, allegati: List<String>): String {
        val conv = id?.let { conversazione(it) } ?: Conversazione(titolo = Conversazione.titoloDa(testo))
        aggiornaConversazione(
            conv.copy(
                messaggi = conv.messaggi + Messaggio(ia = false, testo = testo.trim(), allegati = allegati),
                aggiornata = System.currentTimeMillis(),
            )
        )
        rispondi(conv.id)
        return conv.id
    }

    /** Se l'ultima risposta è fallita, la toglie e richiede la risposta. */
    fun riprova(id: String) {
        val c = conversazione(id) ?: return
        val ultimo = c.messaggi.lastOrNull()
        if (ultimo != null && ultimo.errore) aggiornaConversazione(c.copy(messaggi = c.messaggi.dropLast(1)))
        rispondi(id)
    }

    private fun rispondi(id: String) {
        val chiave = chiaveIA
        if (chiave.isNullOrBlank()) {
            aggiungiMessaggio(id, Messaggio(ia = true, testo = "Manca la chiave API di OpenAI: aggiungila nelle Impostazioni.", errore = true))
            return
        }
        val conv = conversazione(id) ?: return
        if (id in inAttesa) return
        // il fascicolo si prepara qui, con i dati di adesso; la chiamata a OpenAI va in sottofondo
        val istruzioni = Fascicolo.istruzioni(this)
        val immagini = Fascicolo.immagini(this)
        val modello = modelloIA
        val storia = conv.messaggi
        inAttesa = inAttesa + id
        scope.launch {
            val risposta = try {
                val t = withContext(Dispatchers.IO) { Assistente.rispondi(ctx, chiave, modello, istruzioni, storia, immagini) }
                Messaggio(ia = true, testo = t)
            } catch (e: ErroreIA) {
                Messaggio(ia = true, testo = e.message ?: "Errore da OpenAI.", errore = true)
            } catch (e: java.io.IOException) {
                Messaggio(ia = true, testo = "Non riesco a collegarmi a OpenAI: controlla la connessione a internet.", errore = true)
            } catch (e: Exception) {
                Messaggio(ia = true, testo = "Qualcosa è andato storto: " + (e.message ?: e.javaClass.simpleName), errore = true)
            }
            inAttesa = inAttesa - id
            aggiungiMessaggio(id, risposta)
        }
    }

    fun eliminaConversazione(id: String) {
        val c = conversazione(id) ?: return
        for (msg in c.messaggi) for (nome in msg.allegati) File(Assistente.cartellaChat(ctx), nome).delete()
        conversazioni = conversazioni.filter { it.id != id }
        salvaConversazioni()
        messaggio = "Conversazione eliminata."
    }

    /** Prova chiave, credito e modello con una domanda minuscola. */
    fun verificaIA() {
        val chiave = chiaveIA ?: return
        if (verificaInCorso) return
        val modello = modelloIA
        verificaInCorso = true
        scope.launch {
            messaggio = try {
                withContext(Dispatchers.IO) { Assistente.prova(chiave, modello) }
                "Tutto a posto: $modello ha risposto."
            } catch (e: ErroreIA) {
                e.message ?: "Errore da OpenAI."
            } catch (e: java.io.IOException) {
                "Non riesco a collegarmi a OpenAI: controlla la connessione a internet."
            } catch (e: Exception) {
                "Qualcosa è andato storto: " + (e.message ?: e.javaClass.simpleName)
            }
            verificaInCorso = false
        }
    }

    // ---------------- Salvataggio ----------------

    private fun salvaAppuntamenti() = Archivio.salva(ctx, F_APPUNTAMENTI, "appuntamenti", appuntamenti) { it.toJson() }
    private fun salvaEcografie() = Archivio.salva(ctx, F_ECOGRAFIE, "ecografie", ecografie) { it.toJson() }
    private fun salvaMedia() = Archivio.salva(ctx, F_MEDIA, "media", media) { it.toJson() }
    private fun salvaPesi() = Archivio.salva(ctx, F_PESI, "pesi", pesi) { it.toJson() }
    private fun salvaConsigli() = Archivio.salva(ctx, F_CONSIGLI, "consigli", consigli) { it.toJson() }
    private fun salvaConversazioni() = Archivio.salva(ctx, F_CONVERSAZIONI, "conversazioni", conversazioni) { it.toJson() }

    companion object {
        @Volatile private var istanza: Model? = null

        fun di(ctx: Context): Model =
            istanza ?: synchronized(this) {
                istanza ?: Model(ctx.applicationContext).also { istanza = it }
            }
    }
}
