@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.UUID

/** "Peso stimato 350 g · Battito 148 bpm": le prime misure inserite, per le liste. */
fun riassuntoMisure(e: Ecografia, quante: Int = 2): String =
    Misura.entries.mapNotNull { mi ->
        e.misura(mi)?.let { v ->
            val nome = when (mi) {
                Misura.PESO -> "Peso stimato"
                Misura.BATTITO -> "Battito"
                else -> mi.sigla
            }
            "$nome ${mi.formatta(v)}"
        }
    }.take(quante).joinToString(" · ")

@Composable
fun EcografieScreen(m: Model, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    // ecografie in agenda già fatte, ma senza risultati inseriti
    val daCompletare = m.appuntamenti
        .filter { it.tipo == Tipo.ECOGRAFIA && it.passato() && m.ecografiaDi(it.id) == null }
        .sortedByDescending { it.data }
    val prossima = m.appuntamenti.firstOrNull { it.tipo == Tipo.ECOGRAFIA && !it.passato() }

    Scaffold(
        topBar = { BarraSuperiore(m, Sezione.ECOGRAFIE.titolo) },
        bottomBar = { BarraSezioni(m) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { m.vai(Schermata.EcoModifica(null)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuova") },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = cs.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (m.avvisoDrive()) item(key = "avviso-drive") { AvvisoDrive(m) }
            if (prossima != null) {
                item(key = "prossima") {
                    AppCard(onClick = { m.vai(Schermata.Dettaglio(prossima.id)) }) {
                        Text(
                            "Prossima ecografia · " + quando(prossima.data), style = MaterialTheme.typography.labelMedium,
                            color = cs.primary, fontWeight = FontWeight.Bold,
                        )
                        Text(prossima.titolo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val riga = listOfNotNull(
                            dataMedia(prossima.data).maiuscola(),
                            prossima.ora?.let { "ore " + orario(it) },
                            Gravidanza.settimana(m.dpp, prossima.data)?.let { "sett. $it" },
                        ).joinToString(" · ")
                        Text(riga, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
            for (a in daCompletare) {
                item(key = "da-" + a.id) {
                    Card(
                        onClick = { m.vai(Schermata.EcoModifica(null, a.id)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Da completare", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${a.titolo} del ${dataMedia(a.data)}",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            )
                            Text("Aggiungi misure, peso stimato e immagini ›", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (m.ecografie.isEmpty()) {
                item(key = "vuoto") {
                    VuotoSezione(
                        painterResource(R.drawable.ic_ecografie),
                        "Nessuna ecografia",
                        "Per ogni ecografia puoi salvare le misure del referto, il peso stimato del bambino e le immagini. " +
                            "Il peso stimato finisce anche nello Storico.",
                    )
                }
            } else {
                items(m.ecografie, key = { it.id }) { e -> EcografiaCard(m, e) { m.vai(Schermata.EcoDettaglio(e.id)) } }
            }
        }
    }
}

@Composable
private fun EcografiaCard(m: Model, e: Ecografia, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colore = Tipo.ECOGRAFIA.colore
    val file = m.mediaDi(e.id)
    val immagini = file.filter { !it.pdf }
    val pdf = file.size - immagini.size
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(56.dp).clip(RoundedCornerShape(12.dp)).background(colore.copy(alpha = 0.14f)).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(giornoBreve(e.data), style = MaterialTheme.typography.labelSmall, color = colore, fontWeight = FontWeight.Bold)
                Text(e.data.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, color = colore, fontWeight = FontWeight.ExtraBold)
                Text(meseBreve(e.data), style = MaterialTheme.typography.labelSmall, color = colore)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.titolo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val riga = listOfNotNull(
                    Gravidanza.settimana(m.dpp, e.data)?.let { "Settimana $it" },
                    if (immagini.isEmpty()) null else if (immagini.size == 1) "1 immagine" else "${immagini.size} immagini",
                    if (pdf == 0) null else if (pdf == 1) "1 PDF" else "$pdf PDF",
                ).joinToString(" · ")
                if (riga.isNotEmpty()) Text(riga, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                val mis = riassuntoMisure(e)
                if (mis.isNotEmpty()) Text(mis, style = MaterialTheme.typography.bodySmall)
            }
            val prima = immagini.firstOrNull() ?: file.firstOrNull()
            if (prima != null) {
                Spacer(Modifier.width(8.dp))
                Miniatura(prima, Modifier.size(56.dp), onClick = onClick)
            }
        }
    }
}

@Composable
fun EcografiaScreen(m: Model, id: String, snack: SnackbarHostState) {
    val e = m.ecografia(id)
    if (e == null) {
        LaunchedEffect(id) { m.indietro() }
        return
    }
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val aggiungi = rememberAggiuntaMedia(m, e.id)
    val immagini = m.mediaDi(e.id)
    val pdf = immagini.count { it.pdf }
    val app = e.appuntamento?.let { m.trova(it) }
    var chiediElimina by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ecografia") },
                navigationIcon = {
                    IconButton(onClick = { m.indietro() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { m.vai(Schermata.EcoModifica(e.id)) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifica")
                    }
                    IconButton(onClick = { chiediElimina = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Elimina")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = cs.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TipoEtichetta(Tipo.ECOGRAFIA)
            Text(e.titolo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

            AppCard {
                RigaInfo(Icons.Filled.DateRange, dataLunga(e.data).maiuscola(), quando(e.data))
                val dpp = m.dpp
                val g = if (dpp == null) null else Gravidanza.giorni(dpp, e.data)
                if (g != null) {
                    RigaInfo(
                        Icons.Filled.Favorite, "Settimana ${g / 7}+${g % 7}",
                        Gravidanza.settimanaPerEsteso(g) + ", " + Gravidanza.nomeTrimestre(g),
                    )
                }
                if (app != null) {
                    if (app.luogo.isNotBlank()) {
                        RigaInfo(Icons.Filled.Place, app.luogo, azione = "Apri nella mappa") {
                            if (!apriMappa(ctx, app.luogo)) m.messaggio = "Non trovo un'app per le mappe."
                        }
                    }
                    if (app.medico.isNotBlank()) RigaInfo(Icons.Filled.Person, app.medico)
                    Text(
                        "Vedi l'appuntamento in agenda ›", style = MaterialTheme.typography.bodyMedium, color = cs.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp))
                            .clickable { m.vai(Schermata.Dettaglio(app.id)) }.padding(vertical = 2.dp),
                    )
                }
            }

            AppCard {
                Text("Misure", style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
                val presenti = Misura.entries.filter { e.misura(it) != null }
                if (presenti.isEmpty()) {
                    Text(
                        "Nessuna misura inserita. Tocca la matita in alto per aggiungerle dal referto.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (presenti.isNotEmpty() || immagini.isNotEmpty()) {
                    Text(
                        "Chiedi all'IA di spiegarle ›", style = MaterialTheme.typography.bodyMedium, color = cs.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable {
                            m.vai(
                                Schermata.Chat(
                                    null,
                                    "Spiegami in parole semplici i risultati dell'ecografia «${e.titolo}» del ${dataMedia(e.data)}.",
                                )
                            )
                        }.padding(vertical = 2.dp),
                    )
                }
                presenti.forEachIndexed { i, mi ->
                    if (i > 0) HorizontalDivider(color = cs.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(mi.nome, style = MaterialTheme.typography.bodyLarge)
                            Text(mi.sigla, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Text(mi.formatta(e.misura(mi) ?: 0.0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (e.note.isNotBlank()) {
                AppCard {
                    Text("Esito e note", style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
                    Text(e.note, modifier = Modifier.padding(top = 4.dp))
                }
            }

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Immagini e referto", style = MaterialTheme.typography.labelLarge, color = cs.primary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = aggiungi) { Text("Aggiungi") }
                }
                if (m.importazioni > 0) InCopia()
                if (immagini.isEmpty()) {
                    Text(
                        "Le stampe dell'ecografia, una foto dello schermo, il video del battito o il referto in PDF.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                } else {
                    GrigliaFissa(immagini) { m.apriFile(it, ecografia = e.id) }
                }
            }
        }
    }

    if (chiediElimina) {
        AlertDialog(
            onDismissRequest = { chiediElimina = false },
            title = { Text("Eliminare l'ecografia?") },
            text = {
                Text(
                    "Misure e note vengono cancellate." +
                        (if (immagini.size > pdf) " Le immagini restano nella sezione Foto." else "") +
                        (if (pdf > 0) " I PDF vengono eliminati (su Google Drive finiscono nel cestino)." else ""),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chiediElimina = false
                    m.eliminaEcografia(e.id)
                    m.indietro()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { chiediElimina = false }) { Text("Annulla") } },
        )
    }
}

/** Nuova ecografia ([id] null) o modifica; [daAppuntamento] = l'ecografia in agenda da cui nasce. */
@Composable
fun EcoModificaScreen(m: Model, id: String?, daAppuntamento: String?, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    val originale = remember(id) { id?.let { m.ecografia(it) } }
    val app = remember(daAppuntamento) { daAppuntamento?.let { m.trova(it) } }
    val nuovoId = rememberSaveable { UUID.randomUUID().toString() }

    var titolo by rememberSaveable { mutableStateOf(originale?.titolo ?: app?.titolo ?: "") }
    var data by rememberSaveable { mutableStateOf(originale?.data ?: app?.data ?: LocalDate.now()) }
    var note by rememberSaveable { mutableStateOf(originale?.note ?: app?.esito ?: "") }
    // i valori come li scrivi (con la virgola), per misura
    var valori by rememberSaveable {
        mutableStateOf<Map<String, String>>(
            originale?.misure?.mapValues { (k, v) -> numero(v, Misura.da(k)?.decimali ?: 2) } ?: emptyMap()
        )
    }
    var sceltaData by remember { mutableStateOf(false) }
    var chiediUscita by remember { mutableStateOf(false) }

    fun costruisci(): Ecografia {
        val mis = LinkedHashMap<String, Double>()
        for (mi in Misura.entries) {
            val v = leggiNumero(valori[mi.chiave] ?: "")
            if (v != null) mis[mi.chiave] = v
        }
        return (originale ?: Ecografia(id = nuovoId, appuntamento = daAppuntamento)).copy(
            titolo = titolo.trim().ifEmpty { "Ecografia" },
            data = data,
            misure = mis,
            note = note.trim(),
        )
    }

    val iniziale = remember(id, daAppuntamento) { costruisci() }
    val modificato = costruisci() != iniziale
    val errori = Misura.entries.filter { mi ->
        val t = valori[mi.chiave] ?: ""
        t.isNotBlank() && leggiNumero(t) == null
    }

    fun salvaEdEsci() {
        if (errori.isNotEmpty()) {
            m.messaggio = "Controlla le misure segnate in rosso."
            return
        }
        val e = costruisci()
        m.salvaEcografia(e)
        m.messaggio = if (originale == null) "Ecografia salvata." else "Modifiche salvate."
        // una nuova ecografia si apre subito, così si possono aggiungere le immagini
        if (originale == null) m.sostituisci(Schermata.EcoDettaglio(e.id)) else m.indietro()
    }

    BackHandler(enabled = modificato) { chiediUscita = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (originale == null) "Nuova ecografia" else "Modifica ecografia") },
                navigationIcon = {
                    IconButton(onClick = { if (modificato) chiediUscita = true else m.indietro() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                    }
                },
                actions = { TextButton(onClick = { salvaEdEsci() }) { Text("Salva", fontWeight = FontWeight.Bold) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = cs.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = titolo,
                onValueChange = { titolo = it },
                label = { Text("Titolo") },
                placeholder = { Text(Tipo.ECOGRAFIA.suggerimenti.first()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in Tipo.ECOGRAFIA.suggerimenti) SuggestionChip(onClick = { titolo = s }, label = { Text(s) })
            }
            CampoScelta(
                "Data", dataLunga(data).maiuscola(), icona = Icons.Filled.DateRange,
                sotto = Gravidanza.settimana(m.dpp, data)?.let { "Settimana $it" },
            ) { sceltaData = true }

            TitoloSezione("Misure dal referto")
            Text(
                "Lascia vuote quelle che non ci sono: di solito nel primo trimestre si misurano CRL e translucenza, " +
                    "più avanti BPD, HC, AC, FL e il peso stimato.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            for (mi in Misura.entries) {
                val t = valori[mi.chiave] ?: ""
                val errato = mi in errori
                OutlinedTextField(
                    value = t,
                    onValueChange = { valori = valori + (mi.chiave to it) },
                    label = { Text("${mi.nome} (${mi.sigla})") },
                    suffix = { Text(mi.unita) },
                    singleLine = true,
                    isError = errato,
                    supportingText = if (errato) {
                        { Text("Scrivi solo il numero, per esempio 12,5") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Esito e note") },
                placeholder = { Text("Liquido amniotico, placenta, posizione del bambino, cosa ha detto il medico…") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { salvaEdEsci() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) { Text("Salva") }
        }
    }

    if (sceltaData) SceltaData(data, onScelta = { data = it }, onChiudi = { sceltaData = false })
    if (chiediUscita) {
        AlertDialog(
            onDismissRequest = { chiediUscita = false },
            title = { Text("Salvare le modifiche?") },
            text = { Text("Se esci adesso, quello che hai scritto va perso.") },
            confirmButton = {
                TextButton(onClick = {
                    chiediUscita = false
                    salvaEdEsci()
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = {
                    chiediUscita = false
                    m.indietro()
                }) { Text("Esci senza salvare") }
            },
        )
    }
}
