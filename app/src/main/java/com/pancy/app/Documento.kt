@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Un PDF pagina per pagina. Toccando una pagina la si ingrandisce; dal menu si apre con un'altra app. */
@Composable
fun DocumentoScreen(m: Model, id: String, snack: SnackbarHostState) {
    val med = m.mediaPerId(id)
    if (med == null) {
        LaunchedEffect(id) { m.indietro() }
        return
    }
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var file by remember(med.file) { mutableStateOf<File?>(null) }
    // -1 = sto aprendo il documento
    var pagine by remember(med.file) { mutableStateOf(-1) }
    var errore by remember(med.file) { mutableStateOf<String?>(null) }
    var ingrandita by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var rinomina by remember { mutableStateOf(false) }
    var chiediElimina by remember { mutableStateOf(false) }

    LaunchedEffect(med.file) {
        val f = withContext(Dispatchers.IO) { Galleria.fileLocale(ctx, med) }
        if (f == null) {
            errore = "Non riesco a scaricare il documento da Google Drive: controlla la connessione."
            pagine = 0
            return@LaunchedEffect
        }
        val n = withContext(Dispatchers.IO) { Pdf.pagine(f) }
        file = f
        pagine = n
        if (n == 0) errore = "Non riesco a mostrare questo PDF qui (forse è protetto da password). Prova ad aprirlo con un'altra app."
    }

    fun apriFuori() {
        scope.launch {
            val f = withContext(Dispatchers.IO) { Galleria.fileLocale(ctx, med) }
            if (f == null) {
                m.messaggio = "Non riesco a scaricare il documento da Google Drive."
                return@launch
            }
            try {
                Galleria.apriConAltraApp(ctx, med)
            } catch (e: ActivityNotFoundException) {
                m.messaggio = "Non trovo un'app per aprire i PDF."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(med.didascalia.ifBlank { "Documento" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sotto = listOfNotNull(
                            dataMedia(med.data).maiuscola(),
                            if (pagine > 0) (if (pagine == 1) "1 pagina" else "$pagine pagine") else null,
                        ).joinToString(" · ")
                        Text(sotto, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { m.indietro() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Galleria.fileLocale(ctx, med) }
                            if (f == null) {
                                m.messaggio = "Non riesco a scaricare il documento da Google Drive."
                            } else {
                                try {
                                    Galleria.condividi(ctx, med)
                                } catch (e: ActivityNotFoundException) {
                                    m.messaggio = "Non trovo app con cui condividere."
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Condividi")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Altro")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Apri con un'altra app") }, onClick = {
                                menu = false
                                apriFuori()
                            })
                            DropdownMenuItem(text = { Text("Rinomina") }, onClick = {
                                menu = false
                                rinomina = true
                            })
                            DropdownMenuItem(text = { Text("Elimina") }, onClick = {
                                menu = false
                                chiediElimina = true
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = cs.surfaceVariant,
    ) { pad ->
        val f = file
        val e = errore
        when {
            e != null -> Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(e, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                if (f != null) {
                    Button(onClick = { apriFuori() }, modifier = Modifier.padding(top = 16.dp)) { Text("Apri con un'altra app") }
                }
            }
            f == null || pagine < 0 -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    if (med.driveId != null) {
                        Text("Apro il documento…", modifier = Modifier.padding(top = 12.dp), color = cs.onSurfaceVariant)
                    }
                }
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
                val larghezza = with(LocalDensity.current) { (maxWidth - 24.dp).roundToPx() }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(pagine) { i -> PaginaPdf(f, i, larghezza) { ingrandita = i } }
                    item {
                        Text(
                            "Tocca una pagina per ingrandirla.", style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    val f = file
    val i = ingrandita
    if (f != null && i != null) PaginaIngrandita(f, i) { ingrandita = null }
    if (rinomina) {
        NomeDialog(med.didascalia, onSalva = { m.aggiornaMedia(med.copy(didascalia = it)) }, onChiudi = { rinomina = false })
    }
    if (chiediElimina) {
        AlertDialog(
            onDismissRequest = { chiediElimina = false },
            title = { Text("Eliminare il documento?") },
            text = {
                Text(
                    "Viene tolto dall'app" +
                        (if (med.driveId != null) " e su Google Drive finisce nel cestino, da cui si recupera per 30 giorni." else "."),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chiediElimina = false
                    m.eliminaMedia(med.id)
                    m.indietro()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { chiediElimina = false }) { Text("Annulla") } },
        )
    }
}

/** Una pagina del PDF larga quanto lo schermo (si disegna quando compare). */
@Composable
private fun PaginaPdf(f: File, i: Int, larghezza: Int, onClick: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, f, i, larghezza) {
        value = withContext(Dispatchers.IO) { Pdf.pagina(f, i, larghezza) }
    }
    val b = bmp
    val forma = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color.White)
    if (b == null) {
        // finché non è pronta: un foglio A4 vuoto
        Box(forma.aspectRatio(0.707f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp))
        }
    } else {
        val img = remember(b) { b.asImageBitmap() }
        Image(
            img, contentDescription = "Pagina ${i + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = forma.aspectRatio(b.width.toFloat() / b.height.coerceAtLeast(1)).clickable(onClick = onClick),
        )
    }
}

/** La pagina a tutto schermo, disegnata più grande, con lo zoom. */
@Composable
private fun PaginaIngrandita(f: File, i: Int, onChiudi: () -> Unit) {
    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // il doppio dello schermo, così ingrandendo il testo resta nitido
        val larghezza = (LocalContext.current.resources.displayMetrics.widthPixels * 2).coerceIn(1200, 2400)
        val bmp by produceState<Bitmap?>(null, f, i) {
            value = withContext(Dispatchers.IO) { Pdf.pagina(f, i, larghezza) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val b = bmp
            if (b == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else {
                val img = remember(b) { b.asImageBitmap() }
                ImmagineZoomabile(img, "Pagina ${i + 1}")
            }
            IconButton(
                onClick = onChiudi,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Chiudi", tint = Color.White)
            }
            Text(
                "Pagina ${i + 1}", color = Color.White, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp),
            )
        }
    }
}

/** Cambia il nome con cui il documento compare nell'app (e nella descrizione su Drive). */
@Composable
private fun NomeDialog(nome: String, onSalva: (String) -> Unit, onChiudi: () -> Unit) {
    var testo by rememberSaveable { mutableStateOf(nome) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Nome del documento") },
        text = {
            OutlinedTextField(
                value = testo,
                onValueChange = { testo = it },
                singleLine = true,
                placeholder = { Text("Per esempio: esami del sangue di ottobre") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onSalva(testo.trim())
                onChiudi()
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
    )
}
