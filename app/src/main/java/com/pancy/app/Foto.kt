@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDate

@Composable
fun FotoScreen(m: Model, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    val aggiungi = rememberAggiuntaMedia(m, null)
    Scaffold(
        topBar = { BarraSuperiore(m, Sezione.FOTO.titolo) },
        bottomBar = { BarraSezioni(m) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = aggiungi,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Aggiungi") },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = cs.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            AvvisoDrive(m, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (m.importazioni > 0) InCopia()
            if (m.driveInSync) {
                Text(
                    "Sincronizzo con Google Drive…", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (m.media.isEmpty()) {
                VuotoSezione(
                    painterResource(R.drawable.ic_foto),
                    "Nessuna foto o video",
                    "Il pancione settimana dopo settimana, le ecografie, i momenti da ricordare. " +
                        "Tocca «Aggiungi» per sceglierli dalla galleria o scattarli ora.",
                )
            } else {
                GrigliaMedia(m, m.media) { m.vai(Schermata.Visore(it.id)) }
            }
        }
    }
}

/** Barra che avvisa che foto e video si stanno copiando. */
@Composable
fun InCopia() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Copio foto e video nell'app…", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
    }
}

/** Titolo del gruppo in cui finisce una foto: la settimana di gravidanza o, se non c'è, il mese. */
fun gruppoMedia(d: LocalDate, dpp: LocalDate?): String {
    if (dpp != null) {
        if (d.isBefore(Gravidanza.inizio(dpp))) return "Prima della gravidanza"
        val g = Gravidanza.giorni(dpp, d)
        if (g != null) return "Settimana ${g / 7}"
    }
    return mese(d)
}

/** Griglia a tre colonne, divisa per settimana di gravidanza (o per mese). */
@Composable
fun GrigliaMedia(m: Model, lista: List<Media>, onApri: (Media) -> Unit) {
    val dpp = m.dpp
    val gruppi = remember(lista, dpp) { lista.groupBy { gruppoMedia(it.data, dpp) } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((titolo, gruppo) in gruppi) {
            item(key = "g-$titolo", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(titolo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (gruppo.size == 1) "1 elemento" else "${gruppo.size} elementi",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(gruppo, key = { it.id }) { med -> Miniatura(med) { onApri(med) } }
        }
    }
}

/** Griglia semplice (non scorrevole da sola), da mettere dentro una pagina che scorre già. */
@Composable
fun GrigliaFissa(lista: List<Media>, onApri: (Media) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (riga in lista.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (med in riga) Miniatura(med, Modifier.weight(1f)) { onApri(med) }
                repeat(3 - riga.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Prepara la scelta "galleria / foto / video" e restituisce la funzione che la apre.
 * Le foto e i video scelti vengono copiati nell'app (collegati a [ecografia], se non è null).
 */
@Composable
fun rememberAggiuntaMedia(m: Model, ecografia: String?): () -> Unit {
    val ctx = LocalContext.current
    var dialogo by remember { mutableStateOf(false) }
    // il file in cui la fotocamera sta scrivendo (salvato anche se Android chiude l'app nel frattempo)
    var inAttesa by rememberSaveable { mutableStateOf<String?>(null) }

    val galleria = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        m.importa(uris, ecografia)
    }
    val foto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { _ ->
        val p = inAttesa
        inAttesa = null
        if (p != null) m.dopoScatto(File(p), video = false, ecografia = ecografia)
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { _ ->
        val p = inAttesa
        inAttesa = null
        if (p != null) m.dopoScatto(File(p), video = true, ecografia = ecografia)
    }

    fun scatta(eVideo: Boolean) {
        val f = Galleria.nuovoFile(ctx, if (eVideo) "mp4" else "jpg")
        inAttesa = f.path
        try {
            val u = Galleria.uri(ctx, f)
            if (eVideo) video.launch(u) else foto.launch(u)
        } catch (e: ActivityNotFoundException) {
            inAttesa = null
            m.messaggio = "Non trovo un'app per la fotocamera."
        }
    }

    if (dialogo) {
        AlertDialog(
            onDismissRequest = { dialogo = false },
            title = { Text("Aggiungi") },
            text = {
                Column {
                    SceltaAggiunta(painterResource(R.drawable.ic_foto), "Scegli dalla galleria", "Anche più foto e video insieme") {
                        dialogo = false
                        galleria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                    SceltaAggiunta(painterResource(R.drawable.ic_foto), "Scatta una foto", null) {
                        dialogo = false
                        scatta(eVideo = false)
                    }
                    SceltaAggiunta(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.PlayArrow), "Registra un video", null) {
                        dialogo = false
                        scatta(eVideo = true)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialogo = false }) { Text("Annulla") } },
        )
    }
    return { dialogo = true }
}

@Composable
private fun SceltaAggiunta(icona: Painter, testo: String, sotto: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icona, contentDescription = null, tint = cs.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(testo, style = MaterialTheme.typography.bodyLarge)
            if (sotto != null) Text(sotto, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}
