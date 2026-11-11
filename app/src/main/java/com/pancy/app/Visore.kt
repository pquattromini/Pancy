@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Foto e video a schermo intero: si scorre di lato, doppio tocco o due dita per lo zoom. */
@Composable
fun VisoreScreen(m: Model, id: String, ecografia: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lista = if (ecografia != null) m.mediaDi(ecografia) else m.media
    if (lista.isEmpty()) {
        LaunchedEffect(Unit) { m.indietro() }
        return
    }
    val pager = rememberPagerState(initialPage = lista.indexOfFirst { it.id == id }.coerceAtLeast(0)) { lista.size }
    var zoom by remember { mutableStateOf(false) }
    var interfaccia by remember { mutableStateOf(true) }
    var modifica by remember { mutableStateOf(false) }
    var chiediElimina by remember { mutableStateOf(false) }
    val corrente = lista[pager.currentPage.coerceIn(0, lista.size - 1)]

    BarraDiStatoChiara()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoom,
            key = { lista[it].id },
        ) { p ->
            val med = lista[p]
            if (med.video) {
                PaginaVideo(med) { interfaccia = !interfaccia }
            } else {
                PaginaFoto(med, onZoom = { zoom = it }) { interfaccia = !interfaccia }
            }
        }

        if (interfaccia) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { m.indietro() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                }
                Text(
                    "${pager.currentPage + 1} di ${lista.size}", color = Color.White,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val daCondividere = corrente
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { Galleria.fileLocale(ctx, daCondividere) }
                        if (f == null) {
                            m.messaggio = "Non riesco a scaricare il file da Google Drive."
                        } else {
                            try {
                                Galleria.condividi(ctx, daCondividere)
                            } catch (e: ActivityNotFoundException) {
                                m.messaggio = "Non trovo app con cui condividere."
                            }
                        }
                    }
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Condividi", tint = Color.White)
                }
                IconButton(onClick = { modifica = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Modifica data e didascalia", tint = Color.White)
                }
                IconButton(onClick = { chiediElimina = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Elimina", tint = Color.White)
                }
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding().padding(16.dp),
            ) {
                val sett = Gravidanza.settimana(m.dpp, corrente.data)
                Text(
                    dataLunga(corrente.data).maiuscola() + (if (sett != null) " · settimana $sett" else ""),
                    color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                )
                if (corrente.didascalia.isNotBlank()) {
                    Text(corrente.didascalia, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
                val eid = corrente.ecografia
                val eco = if (eid != null && ecografia == null) m.ecografia(eid) else null
                if (eco != null) {
                    Text(
                        "Dall'ecografia: ${eco.titolo} ›", color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp).clickable { m.vai(Schermata.EcoDettaglio(eco.id)) },
                    )
                }
            }
        }
    }

    if (modifica) {
        MediaDialog(corrente, m.dpp, onSalva = { m.aggiornaMedia(it) }, onChiudi = { modifica = false })
    }
    if (chiediElimina) {
        AlertDialog(
            onDismissRequest = { chiediElimina = false },
            title = { Text(if (corrente.video) "Eliminare il video?" else "Eliminare la foto?") },
            text = { Text("Viene tolto dall'app. Se l'avevi scelto dalla galleria, l'originale resta nella galleria del telefono.") },
            confirmButton = {
                TextButton(onClick = {
                    chiediElimina = false
                    m.eliminaMedia(corrente.id)
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { chiediElimina = false }) { Text("Annulla") } },
        )
    }
}

/** Su sfondo nero le icone della barra di stato devono essere chiare; all'uscita torna com'era. */
@Composable
private fun BarraDiStatoChiara() {
    val view = LocalView.current
    DisposableEffect(view) {
        val w = view.context.attivita()?.window
        val c = if (w != null) WindowCompat.getInsetsController(w, view) else null
        val prima = c?.isAppearanceLightStatusBars
        c?.isAppearanceLightStatusBars = false
        onDispose {
            if (c != null && prima != null) c.isAppearanceLightStatusBars = prima
        }
    }
}

fun Context.attivita(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun PaginaFoto(med: Media, onZoom: (Boolean) -> Unit, onTap: () -> Unit) {
    val ctx = LocalContext.current
    var fallita by remember { mutableStateOf(false) }
    val bmp by produceState<Bitmap?>(null, med.file) {
        value = withContext(Dispatchers.IO) {
            try {
                val f = Galleria.fileLocale(ctx, med)
                if (f == null) null else Galleria.foto(f, 2048)
            } catch (e: OutOfMemoryError) {
                null
            }
        }
        if (value == null) fallita = true
    }
    var scala by remember { mutableFloatStateOf(1f) }
    var spost by remember { mutableStateOf(Offset.Zero) }
    val zoomato = scala > 1.01f
    LaunchedEffect(zoomato) { onZoom(zoomato) }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scala > 1.01f) {
                            scala = 1f
                            spost = Offset.Zero
                        } else {
                            scala = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // due dita: zoom; con lo zoom attivo un dito sposta la foto. Senza zoom un dito passa alla foto accanto.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val dita = ev.changes.count { it.pressed }
                        if (dita >= 2 || scala > 1.01f) {
                            scala = (scala * ev.calculateZoom()).coerceIn(1f, 5f)
                            val maxX = (scala - 1f) * size.width / 2f
                            val maxY = (scala - 1f) * size.height / 2f
                            val n = spost + ev.calculatePan()
                            spost = Offset(n.x.coerceIn(-maxX, maxX), n.y.coerceIn(-maxY, maxY))
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b == null && fallita) {
            Text(
                "Non riesco ad aprire la foto. Se è su Google Drive, controlla la connessione.",
                color = Color.White, modifier = Modifier.padding(32.dp),
            )
        } else if (b == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            val img = remember(b) { b.asImageBitmap() }
            Image(
                img, contentDescription = med.didascalia.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scala
                    scaleY = scala
                    translationX = spost.x
                    translationY = spost.y
                },
            )
        }
    }
}

@Composable
private fun PaginaVideo(med: Media, onTap: () -> Unit) {
    val ctx = LocalContext.current
    // 0 = anteprima, 1 = scarico da Drive, 2 = riproduco, 3 = non riesco a scaricarlo
    var stato by remember { mutableStateOf(0) }
    val f = remember(med.file) { Galleria.file(ctx, med) }
    LaunchedEffect(stato) {
        if (stato == 1) {
            val ok = withContext(Dispatchers.IO) { Galleria.fileLocale(ctx, med) != null }
            stato = if (ok) 2 else 3
        }
    }
    if (stato == 2) {
        AndroidView(
            factory = { c ->
                FrameLayout(c).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    val v = VideoView(c)
                    addView(
                        v,
                        FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
                    )
                    val mc = MediaController(c)
                    mc.setAnchorView(this)
                    v.setMediaController(mc)
                    v.setVideoPath(f.path)
                    v.setOnPreparedListener { v.start() }
                }
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { (it.getChildAt(0) as? VideoView)?.stopPlayback() },
        )
    } else {
        val bmp by produceState<Bitmap?>(null, med.file) {
            value = withContext(Dispatchers.IO) { Galleria.miniaturaDi(ctx, med, 1080) }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
            contentAlignment = Alignment.Center,
        ) {
            val b = bmp
            if (b != null) {
                val img = remember(b) { b.asImageBitmap() }
                Image(img, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            when (stato) {
                1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Scarico il video da Google Drive…", color = Color.White, modifier = Modifier.padding(top = 12.dp))
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { stato = if (f.exists() && f.length() > 0) 2 else 1 },
                        modifier = Modifier.size(76.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Riproduci", tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                    if (stato == 3) {
                        Text(
                            "Non riesco a scaricare il video: controlla la connessione.",
                            color = Color.White, modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Cambia data e didascalia di una foto o di un video. */
@Composable
fun MediaDialog(med: Media, dpp: java.time.LocalDate?, onSalva: (Media) -> Unit, onChiudi: () -> Unit) {
    var data by rememberSaveable { mutableStateOf(med.data) }
    var didascalia by rememberSaveable { mutableStateOf(med.didascalia) }
    var sceltaData by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(if (med.video) "Video" else "Foto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoScelta(
                    "Data", dataLunga(data).maiuscola(),
                    sotto = Gravidanza.settimana(dpp, data)?.let { "Settimana $it" },
                ) { sceltaData = true }
                OutlinedTextField(
                    value = didascalia,
                    onValueChange = { didascalia = it },
                    label = { Text("Didascalia") },
                    placeholder = { Text("Per esempio: il pancione a 20 settimane") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSalva(med.copy(data = data, didascalia = didascalia.trim()))
                onChiudi()
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
    )
    if (sceltaData) SceltaData(data, onScelta = { data = it }, onChiudi = { sceltaData = false })
}
