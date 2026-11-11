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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.UUID

/** Etichetta colorata con il tipo di referto. */
@Composable
fun TipoRefertoEtichetta(t: TipoReferto) {
    Text(
        t.nome, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = t.colore,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(t.colore.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun RefertiScreen(m: Model, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    // esami in agenda già fatti, ma senza referto
    val daCompletare = m.appuntamenti
        .filter { it.tipo == Tipo.ESAMI && it.passato() && m.refertiDi(it.id).isEmpty() }
        .sortedByDescending { it.data }
    val tipiPresenti = TipoReferto.entries.filter { t -> m.referti.any { it.tipo == t } }
    // filtro per tipo: chiave del tipo, null = tutti
    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    val filtroValido = filtro?.takeIf { f -> tipiPresenti.size > 1 && tipiPresenti.any { it.chiave == f } }
    val lista = m.referti.filter { filtroValido == null || it.tipo.chiave == filtroValido }

    Scaffold(
        topBar = { BarraSuperiore(m, Sezione.REFERTI.titolo) },
        bottomBar = { BarraSezioni(m) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { m.vai(Schermata.RefModifica(null)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuovo") },
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
            if (m.driveCollegato && m.cartellaFoto != null && m.cartellaRef == null) {
                item(key = "cartella-ref") {
                    AppCard {
                        Text(
                            "I referti restano solo su questo telefono finché non scegli una loro cartella su Google Drive. " +
                                "Con la cartella, i PDF che ci metti compaiono qui da soli.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { m.vai(Schermata.Impostazioni) }, contentPadding = PaddingValues(0.dp)) {
                            Text("Scegli la cartella nelle Impostazioni ›", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            for (a in daCompletare) {
                item(key = "da-" + a.id) {
                    Card(
                        onClick = { m.vai(Schermata.RefModifica(null, a.id)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Da completare", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${a.titolo} del ${dataMedia(a.data)}",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            )
                            Text("Aggiungi l'esito e il PDF del referto ›", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (tipiPresenti.size > 1) {
                item(key = "filtri") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filtroValido == null, onClick = { filtro = null }, label = { Text("Tutti") })
                        for (t in tipiPresenti) {
                            FilterChip(
                                selected = filtroValido == t.chiave,
                                onClick = { filtro = if (filtroValido == t.chiave) null else t.chiave },
                                label = { Text(t.nome) },
                            )
                        }
                    }
                }
            }
            if (m.referti.isEmpty()) {
                item(key = "vuoto") {
                    VuotoSezione(
                        painterResource(R.drawable.ic_referti),
                        "Nessun referto",
                        "Esami del sangue e delle urine, test prenatali, tamponi, impegnative: per ognuno puoi salvare l'esito " +
                            "e il PDF o la foto del foglio. L'IA li legge e te li spiega.",
                    )
                }
            } else {
                items(lista, key = { it.id }) { r -> RefertoCard(m, r) { m.vai(Schermata.RefDettaglio(r.id)) } }
            }
        }
    }
}

@Composable
private fun RefertoCard(m: Model, r: Referto, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colore = r.tipo.colore
    val file = m.fileDi(r.id)
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
                Text(giornoBreve(r.data), style = MaterialTheme.typography.labelSmall, color = colore, fontWeight = FontWeight.Bold)
                Text(r.data.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, color = colore, fontWeight = FontWeight.ExtraBold)
                Text(meseBreve(r.data), style = MaterialTheme.typography.labelSmall, color = colore)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.titolo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val riga = listOfNotNull(
                    r.tipo.nome,
                    Gravidanza.settimana(m.dpp, r.data)?.let { "sett. $it" },
                    if (file.isEmpty()) null else if (file.size == 1) "1 file" else "${file.size} file",
                ).joinToString(" · ")
                Text(riga, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                if (r.esito.isNotBlank()) {
                    Text(r.esito, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (file.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Miniatura(file.first(), Modifier.size(56.dp), onClick = onClick)
            }
        }
    }
}

@Composable
fun RefertoScreen(m: Model, id: String, snack: SnackbarHostState) {
    val r = m.referto(id)
    if (r == null) {
        LaunchedEffect(id) { m.indietro() }
        return
    }
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val aggiungi = rememberAggiuntaMedia(m, null, r.id)
    val file = m.fileDi(r.id)
    val app = r.appuntamento?.let { m.trova(it) }
    var chiediElimina by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Referto") },
                navigationIcon = {
                    IconButton(onClick = { m.indietro() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { m.vai(Schermata.RefModifica(r.id)) }) {
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
            TipoRefertoEtichetta(r.tipo)
            Text(r.titolo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

            AppCard {
                RigaInfo(Icons.Filled.DateRange, dataLunga(r.data).maiuscola(), quando(r.data))
                val dpp = m.dpp
                val g = if (dpp == null) null else Gravidanza.giorni(dpp, r.data)
                if (g != null) {
                    RigaInfo(
                        Icons.Filled.Favorite, "Settimana ${g / 7}+${g % 7}",
                        Gravidanza.settimanaPerEsteso(g) + ", " + Gravidanza.nomeTrimestre(g),
                    )
                }
                if (r.dove.isNotBlank()) {
                    RigaInfo(Icons.Filled.Place, r.dove, azione = "Apri nella mappa") {
                        if (!apriMappa(ctx, r.dove)) m.messaggio = "Non trovo un'app per le mappe."
                    }
                }
                if (app != null) {
                    Text(
                        "Vedi l'appuntamento in agenda ›", style = MaterialTheme.typography.bodyMedium, color = cs.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp))
                            .clickable { m.vai(Schermata.Dettaglio(app.id)) }.padding(vertical = 2.dp),
                    )
                }
            }

            AppCard {
                Text("Esito", style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
                Text(
                    r.esito.ifBlank { "Nessun esito scritto. Tocca la matita in alto per aggiungerlo." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (r.esito.isBlank()) cs.onSurfaceVariant else cs.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (r.esito.isNotBlank() || file.isNotEmpty()) {
                    Text(
                        "Chiedi all'IA di spiegarlo ›", style = MaterialTheme.typography.bodyMedium, color = cs.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).clickable {
                            m.vai(
                                Schermata.Chat(
                                    null,
                                    "Spiegami in parole semplici il referto «${r.titolo}» del ${dataMedia(r.data)}.",
                                )
                            )
                        }.padding(vertical = 2.dp),
                    )
                }
            }

            if (r.note.isNotBlank()) {
                AppCard {
                    Text("Note", style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
                    Text(r.note, modifier = Modifier.padding(top = 4.dp))
                }
            }

            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PDF e foto", style = MaterialTheme.typography.labelLarge, color = cs.primary,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = aggiungi) { Text("Aggiungi") }
                }
                if (m.importazioni > 0) InCopia()
                if (file.isEmpty()) {
                    Text(
                        "Il PDF del laboratorio, oppure le foto del foglio.",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                } else {
                    GrigliaFissa(file) { m.apriFile(it, referto = r.id) }
                }
                if (m.driveCollegato && m.cartellaRef == null) {
                    Text(
                        "Questi file restano solo sul telefono: scegli la cartella dei referti di Google Drive nelle Impostazioni.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }

    if (chiediElimina) {
        AlertDialog(
            onDismissRequest = { chiediElimina = false },
            title = { Text("Eliminare il referto?") },
            text = {
                Text(
                    "Esito e note vengono cancellati" + when {
                        file.isEmpty() -> "."
                        file.size == 1 -> ", insieme al file collegato."
                        else -> ", insieme ai ${file.size} file collegati."
                    } + (if (file.any { it.driveId != null }) " Su Google Drive i file finiscono nel cestino, da cui si recuperano per 30 giorni." else ""),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chiediElimina = false
                    m.eliminaReferto(r.id)
                    m.indietro()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { chiediElimina = false }) { Text("Annulla") } },
        )
    }
}

/** Nuovo referto ([id] null) o modifica; [daAppuntamento] = l'appuntamento in agenda da cui nasce. */
@Composable
fun RefertoModificaScreen(m: Model, id: String?, daAppuntamento: String?, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    val originale = remember(id) { id?.let { m.referto(it) } }
    val app = remember(daAppuntamento) { daAppuntamento?.let { m.trova(it) } }
    val nuovoId = rememberSaveable { UUID.randomUUID().toString() }

    var tipo by rememberSaveable {
        mutableStateOf((originale?.tipo ?: app?.let { TipoReferto.indovina(it.titolo) } ?: TipoReferto.SANGUE).chiave)
    }
    var titolo by rememberSaveable { mutableStateOf(originale?.titolo ?: app?.titolo ?: "") }
    var data by rememberSaveable { mutableStateOf(originale?.data ?: app?.data ?: LocalDate.now()) }
    var dove by rememberSaveable { mutableStateOf(originale?.dove ?: app?.luogo ?: "") }
    var esito by rememberSaveable { mutableStateOf(originale?.esito ?: app?.esito ?: "") }
    var note by rememberSaveable { mutableStateOf(originale?.note ?: "") }
    var sceltaData by remember { mutableStateOf(false) }
    var chiediUscita by remember { mutableStateOf(false) }
    val tipoScelto = TipoReferto.da(tipo) ?: TipoReferto.ALTRO

    fun costruisci(): Referto =
        (originale ?: Referto(id = nuovoId, appuntamento = daAppuntamento)).copy(
            tipo = tipoScelto,
            titolo = titolo.trim().ifEmpty { tipoScelto.suggerimenti.firstOrNull() ?: "Referto" },
            data = data,
            dove = dove.trim(),
            esito = esito.trim(),
            note = note.trim(),
        )

    val iniziale = remember(id, daAppuntamento) { costruisci() }
    val modificato = costruisci() != iniziale

    fun salvaEdEsci() {
        val r = costruisci()
        m.salvaReferto(r)
        m.messaggio = if (originale == null) "Referto salvato." else "Modifiche salvate."
        // un nuovo referto si apre subito, così si possono aggiungere PDF e foto
        if (originale == null) m.sostituisci(Schermata.RefDettaglio(r.id)) else m.indietro()
    }

    BackHandler(enabled = modificato) { chiediUscita = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (originale == null) "Nuovo referto" else "Modifica referto") },
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
            Text("Tipo", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in TipoReferto.entries) {
                    FilterChip(selected = t == tipoScelto, onClick = { tipo = t.chiave }, label = { Text(t.nome) })
                }
            }
            OutlinedTextField(
                value = titolo,
                onValueChange = { titolo = it },
                label = { Text("Titolo") },
                placeholder = { Text(tipoScelto.suggerimenti.firstOrNull() ?: "Per esempio: certificato per il lavoro") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            if (tipoScelto.suggerimenti.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in tipoScelto.suggerimenti) SuggestionChip(onClick = { titolo = s }, label = { Text(s) })
                }
            }
            CampoScelta(
                "Data", dataLunga(data).maiuscola(), icona = Icons.Filled.DateRange,
                sotto = Gravidanza.settimana(m.dpp, data)?.let { "Settimana $it" },
            ) { sceltaData = true }
            OutlinedTextField(
                value = dove,
                onValueChange = { dove = it },
                label = { Text("Laboratorio, ospedale o medico") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = esito,
                onValueChange = { esito = it },
                label = { Text("Esito in breve") },
                placeholder = { Text("Per esempio: tutto nella norma; toxo negativo; ferro un po' basso") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                placeholder = { Text("Valori da tenere d'occhio, cosa ha detto il medico, esami da ripetere…") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (originale == null) "Dopo il salvataggio puoi aggiungere il PDF o le foto del referto."
                else "PDF e foto si aggiungono dalla pagina del referto.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
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
