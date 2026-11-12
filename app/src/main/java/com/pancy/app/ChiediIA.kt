@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Elenco delle conversazioni con l'IA, con qualche domanda pronta. */
@Composable
fun ChiediIAScreen(m: Model, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chiedi all'IA", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = { m.indietro() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { m.vai(Schermata.Chat(null)) },
                icon = { Icon(painterResource(R.drawable.ic_ia), contentDescription = null) },
                text = { Text("Nuova domanda") },
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
            item(key = "intro") {
                Text(
                    "Fai una domanda come a un'IA qualsiasi: in più legge tutti i dati dell'app (appuntamenti, ecografie, " +
                        "referti, pesi, consigli, l'elenco di foto e video) e, quando serve, guarda le immagini e legge i PDF dei referti. " +
                        "Può anche preparare modifiche (per esempio un peso letto dalla foto della bilancia): le salvi tu con «Salva».",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            }
            if (m.chiaveIA.isNullOrBlank()) {
                item(key = "chiave") {
                    Card(
                        onClick = { m.vai(Schermata.Impostazioni) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Per iniziare serve la chiave API di OpenAI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("La trovi su platform.openai.com, alla voce API keys.", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Aggiungila nelle Impostazioni ›", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            item(key = "titolo-idee") { TitoloSezione("Idee per iniziare") }
            items(DOMANDE_PRONTE, key = { "d-$it" }) { d ->
                AppCard(onClick = {
                    val id = m.invia(null, d, emptyList())
                    m.vai(Schermata.Chat(id))
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_ia), contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(d, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (m.conversazioni.isNotEmpty()) {
                item(key = "titolo-conv") { TitoloSezione("Conversazioni") }
                items(m.conversazioni, key = { it.id }) { c ->
                    val ultimo = c.messaggi.lastOrNull()
                    AppCard(onClick = { m.vai(Schermata.Chat(c.id)) }) {
                        Text(
                            c.titolo.ifBlank { "Conversazione" }, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        val quando = Instant.ofEpochMilli(c.aggiornata).atZone(ZoneId.systemDefault()).toLocalDate()
                        val stato = when {
                            c.id in m.inAttesa -> "L'IA sta rispondendo…"
                            ultimo != null && ultimo.errore -> "Risposta non arrivata"
                            ultimo != null && ultimo.ia -> ultimo.testo.replace("**", "").lines().firstOrNull { it.isNotBlank() } ?: ""
                            else -> ""
                        }
                        Text(
                            dataMedia(quando).maiuscola() + (if (stato.isNotEmpty()) " · $stato" else ""),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Una conversazione: messaggi, foto allegate e casella per scrivere. [id] null = nuova. */
@Composable
fun ChatScreen(m: Model, id: String?, bozza: String?, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val conv = id?.let { m.conversazione(it) }
    if (id != null && conv == null) {
        LaunchedEffect(id) { m.indietro() }
        return
    }
    val messaggi = conv?.messaggi ?: emptyList()
    val attesa = id != null && id in m.inAttesa
    var testo by rememberSaveable(id) { mutableStateOf(bozza ?: "") }
    var allegati by rememberSaveable(id) { mutableStateOf<List<String>>(emptyList()) }
    var chiediElimina by remember { mutableStateOf(false) }
    val lista = rememberLazyListState()
    val allega = rememberAllegaFoto(m) { nuovi -> allegati = allegati + nuovi }

    // scorre in fondo quando arriva un messaggio
    val elementi = 1 + messaggi.size + (if (attesa) 1 else 0)
    LaunchedEffect(elementi) { lista.animateScrollToItem(elementi - 1) }

    fun invia() {
        val t = testo.trim()
        if (t.isEmpty() && allegati.isEmpty()) return
        val nuovoId = m.invia(id, t, allegati)
        testo = ""
        allegati = emptyList()
        if (id == null) m.sostituisci(Schermata.Chat(nuovoId))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        conv?.titolo?.ifBlank { null } ?: "Nuova domanda",
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { m.indietro() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (conv != null) {
                        IconButton(onClick = { chiediElimina = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina la conversazione")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
        bottomBar = {
            Surface(color = cs.surface, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    if (allegati.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(start = 8.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (nome in allegati) {
                                Box {
                                    MiniaturaFile(File(Assistente.cartellaChat(ctx), nome), 56.dp)
                                    Icon(
                                        Icons.Filled.Close, contentDescription = "Togli la foto", tint = Color.White,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp)
                                            .clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                                            .clickableTesto {
                                                allegati = allegati - nome
                                                File(Assistente.cartellaChat(ctx), nome).delete()
                                            }
                                            .padding(2.dp),
                                    )
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = allega, enabled = !attesa) {
                            Icon(painterResource(R.drawable.ic_foto), contentDescription = "Allega una foto")
                        }
                        OutlinedTextField(
                            value = testo,
                            onValueChange = { testo = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Scrivi una domanda…") },
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        val puoInviare = !attesa && (testo.isNotBlank() || allegati.isNotEmpty())
                        IconButton(onClick = { invia() }, enabled = puoInviare) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send, contentDescription = "Invia",
                                tint = if (puoInviare) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = cs.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            state = lista,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "avviso") {
                Text(
                    "Risponde in base ai dati dell'app e non sostituisce medico e ostetrica. " +
                        "Domande e dati vengono inviati a OpenAI per avere la risposta.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            items(messaggi.size, key = { i -> "msg-$i" }) { i ->
                val msg = messaggi[i]
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Fumetto(msg, ultimo = i == messaggi.size - 1 && !attesa) { if (id != null) m.riprova(id) }
                    // le modifiche ai dati proposte dall'IA, da confermare
                    if (id != null) for (p in msg.proposte) SchedaProposta(m, id, p)
                }
            }
            if (attesa) {
                item(key = "attesa") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Sto leggendo i vostri dati…", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (chiediElimina && conv != null) {
        AlertDialog(
            onDismissRequest = { chiediElimina = false },
            title = { Text("Eliminare la conversazione?") },
            text = { Text("Domande, risposte e foto allegate a questa conversazione vengono cancellate.") },
            confirmButton = {
                TextButton(onClick = {
                    chiediElimina = false
                    m.eliminaConversazione(conv.id)
                    m.indietro()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { chiediElimina = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Fumetto(msg: Messaggio, ultimo: Boolean, onRiprova: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.ia) Arrangement.Start else Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (msg.ia) 4.dp else 16.dp, bottomEnd = if (msg.ia) 16.dp else 4.dp,
            ),
            color = when {
                msg.errore -> cs.errorContainer
                msg.ia -> cs.surface
                else -> cs.primaryContainer
            },
            contentColor = when {
                msg.errore -> cs.onErrorContainer
                msg.ia -> cs.onSurface
                else -> cs.onPrimaryContainer
            },
            border = if (msg.ia && !msg.errore) BorderStroke(1.dp, cs.outlineVariant) else null,
            modifier = Modifier.widthIn(max = 340.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (msg.allegati.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        for (nome in msg.allegati.take(4)) MiniaturaFile(File(Assistente.cartellaChat(ctx), nome), 64.dp)
                    }
                }
                if (msg.testo.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            if (msg.ia && !msg.errore) testoFormattato(msg.testo) else AnnotatedString(msg.testo),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                if (msg.errore && ultimo) {
                    TextButton(onClick = onRiprova, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Riprova") }
                }
            }
        }
    }
}

/** Trasforma il poco Markdown che l'IA usa (**grassetto**, elenchi con "-", titoli con "#") in testo formattato. */
fun testoFormattato(s: String): AnnotatedString = buildAnnotatedString {
    val righe = s.trim().lines()
    righe.forEachIndexed { i, originale ->
        val t = originale.trimStart()
        if (t.startsWith("#")) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.trimStart('#').trim().replace("**", "")) }
        } else {
            val riga = if (t.startsWith("- ") || t.startsWith("* ")) "• " + t.substring(2) else originale
            riga.split("**").forEachIndexed { k, pezzo ->
                if (k % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(pezzo) } else append(pezzo)
            }
        }
        if (i < righe.size - 1) append("\n")
    }
}

/** Miniatura quadrata di un file immagine qualsiasi (per le foto allegate alla chat). */
@Composable
fun MiniaturaFile(f: File, lato: Dp) {
    val cs = MaterialTheme.colorScheme
    val chiave = "chat/" + f.name
    val bmp by produceState<Bitmap?>(Galleria.cache.get(chiave), chiave) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { Galleria.miniatura(f, false, 200)?.also { Galleria.cache.put(chiave, it) } }
        }
    }
    Box(Modifier.size(lato).clip(RoundedCornerShape(8.dp)).background(cs.surfaceVariant)) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(img, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** Prepara la scelta "galleria / fotocamera" per allegare foto a una domanda; restituisce la funzione che la apre. */
@Composable
private fun rememberAllegaFoto(m: Model, onAggiunte: (List<String>) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialogo by remember { mutableStateOf(false) }
    var inAttesa by rememberSaveable { mutableStateOf<String?>(null) }

    val galleria = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val nomi = withContext(Dispatchers.IO) { uris.mapNotNull { Assistente.copiaAllegato(ctx, it) } }
                if (nomi.isNotEmpty()) onAggiunte(nomi)
            }
        }
    }
    val foto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { _ ->
        val p = inAttesa
        inAttesa = null
        if (p != null) {
            val f = File(p)
            if (f.exists() && f.length() > 0) onAggiunte(listOf(f.name)) else f.delete()
        }
    }

    if (dialogo) {
        AlertDialog(
            onDismissRequest = { dialogo = false },
            title = { Text("Allega una foto") },
            text = {
                Column {
                    Text(
                        "Per esempio un referto o la stampa di un'ecografia da far leggere all'IA.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TextButton(onClick = {
                        dialogo = false
                        galleria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Scegli dalla galleria") }
                    TextButton(onClick = {
                        dialogo = false
                        val f = Assistente.nuovoAllegato(ctx)
                        inAttesa = f.path
                        try {
                            foto.launch(Galleria.uri(ctx, f))
                        } catch (e: ActivityNotFoundException) {
                            inAttesa = null
                            m.messaggio = "Non trovo un'app per la fotocamera."
                        }
                    }) { Text("Scatta una foto") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialogo = false }) { Text("Annulla") } },
        )
    }
    return { dialogo = true }
}

/** Sezione delle impostazioni per l'IA: chiave API, modello, verifica. */
@Composable
fun SezioneIA(m: Model) {
    val cs = MaterialTheme.colorScheme
    var dialogoChiave by remember { mutableStateOf(false) }
    var dialogoModello by remember { mutableStateOf(false) }
    val k = m.chiaveIA

    TitoloSezione("Chiedi all'IA (OpenAI)")
    AppCard {
        CampoScelta(
            "Chiave API",
            if (k.isNullOrBlank()) "Non inserita" else "Inserita (finisce con …" + k.takeLast(4) + ")",
            icona = null,
        ) { dialogoChiave = true }
        Text(
            "Modello", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((nome, descrizione) in MODELLI_IA) {
                FilterChip(selected = m.modelloIA == nome, onClick = { m.impostaModelloIA(nome) }, label = { Text(descrizione) })
            }
            FilterChip(
                selected = MODELLI_IA.none { it.first == m.modelloIA },
                onClick = { dialogoModello = true },
                label = { Text("Altro…") },
            )
        }
        Text(
            "In uso: ${m.modelloIA}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!k.isNullOrBlank()) {
            OutlinedButton(
                onClick = { m.verificaIA() },
                enabled = !m.verificaInCorso,
                modifier = Modifier.padding(top = 10.dp),
            ) { Text(if (m.verificaInCorso) "Verifico…" else "Verifica chiave e credito") }
        }
        Text(
            "Le domande e i dati dell'app, comprese le foto e i PDF che l'IA chiede di guardare, vengono inviati a OpenAI. " +
                "Di base OpenAI non usa i dati inviati con l'API per addestrare i suoi modelli. " +
                "La chiave resta solo su questo telefono e non va nel backup.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    if (dialogoChiave) {
        var valore by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogoChiave = false },
            title = { Text("Chiave API di OpenAI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Copiala da platform.openai.com, alla voce API keys, e incollala qui. Inizia con \"sk-\".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = valore,
                        onValueChange = { valore = it },
                        label = { Text(if (k.isNullOrBlank()) "Chiave" else "Nuova chiave") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        m.impostaChiaveIA(valore)
                        dialogoChiave = false
                        m.messaggio = "Chiave salvata."
                    },
                    enabled = valore.isNotBlank(),
                ) { Text("Salva") }
            },
            dismissButton = {
                Row {
                    if (!k.isNullOrBlank()) {
                        TextButton(onClick = {
                            m.impostaChiaveIA(null)
                            dialogoChiave = false
                        }) { Text("Togli") }
                    }
                    TextButton(onClick = { dialogoChiave = false }) { Text("Annulla") }
                }
            },
        )
    }
    if (dialogoModello) {
        var valore by remember { mutableStateOf(m.modelloIA) }
        AlertDialog(
            onDismissRequest = { dialogoModello = false },
            title = { Text("Altro modello") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Scrivi il nome del modello come lo indica OpenAI nella pagina dei modelli (per esempio gpt-6.1-terra). " +
                            "Deve accettare immagini e strumenti.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = valore,
                        onValueChange = { valore = it },
                        label = { Text("Modello") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    m.impostaModelloIA(valore)
                    dialogoModello = false
                }, enabled = valore.isNotBlank()) { Text("Usa questo") }
            },
            dismissButton = { TextButton(onClick = { dialogoModello = false }) { Text("Annulla") } },
        )
    }
}
