@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Prepara il collegamento a Google Drive e restituisce la funzione che lo avvia:
 * se serve, mostra la schermata di Google per dare (o confermare) l'accesso.
 */
@Composable
fun rememberCollegaDrive(m: Model): () -> Unit {
    val ctx = LocalContext.current
    val autorizza = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        try {
            val res = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(r.data)
            m.driveAutorizzato(res.accessToken)
        } catch (e: ApiException) {
            m.messaggio = "Collegamento a Google Drive annullato."
        }
    }
    return {
        Identity.getAuthorizationClient(ctx).authorize(Drive.richiesta())
            .addOnSuccessListener { res ->
                val pi = res.pendingIntent
                if (res.hasResolution() && pi != null) {
                    autorizza.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    m.driveAutorizzato(res.accessToken)
                }
            }
            .addOnFailureListener { e -> m.messaggio = Drive.spiegaErroreAccesso(e) }
    }
}

/** C'è un problema con Drive da mostrare in Foto ed Ecografie. */
fun Model.avvisoDrive(): Boolean = driveCollegato && driveProblema != null

/**
 * Avviso in Foto ed Ecografie quando Drive ha un problema. Se Google chiede solo di
 * confermare l'accesso, il pulsante «Ricollega» lo risolve subito; altrimenti porta alle impostazioni.
 */
@Composable
fun AvvisoDrive(m: Model, modifier: Modifier = Modifier) {
    val problema = m.driveProblema
    if (!m.avvisoDrive() || problema == null) return
    val cs = MaterialTheme.colorScheme
    val collega = rememberCollegaDrive(m)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.errorContainer, contentColor = cs.onErrorContainer),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(problema, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (m.driveServeConsenso) {
                TextButton(onClick = collega) { Text("Ricollega", fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = { m.vai(Schermata.Impostazioni) }) { Text("Impostazioni", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Sezione delle impostazioni per Google Drive: collegamento, cartelle, sincronizzazione. */
@Composable
fun SezioneDrive(m: Model) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var sceltaCartella by remember { mutableStateOf(0) } // 1 = foto, 2 = ecografie, 3 = referti
    var chiediScollega by remember { mutableStateOf(false) }
    val collega = rememberCollegaDrive(m)

    TitoloSezione("Google Drive")
    AppCard {
        if (!m.driveCollegato) {
            Text("Collega le cartelle di Google Drive", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "L'app mostra le foto, le ecografie e i referti che tieni su Drive, e quello che aggiungi nell'app finisce anche lì. " +
                    "Se reinstalli l'app, basta ricollegare Drive per ritrovare tutto.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
            Text(
                "Prima serve il progetto su Google Cloud (vedi la guida nel README del progetto).",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(onClick = collega, modifier = Modifier.padding(top = 10.dp)) { Text("Collega Google Drive") }
        } else {
            val problema = m.driveProblema
            if (problema != null) {
                Text(problema, style = MaterialTheme.typography.bodyMedium, color = cs.error)
                Button(onClick = collega, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)) { Text("Ricollega") }
                if (m.driveServeConsenso) {
                    Text(
                        "Finché l'app su Google Cloud è «in test», Google chiede di confermare l'accesso circa una volta a settimana.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            CampoScelta(
                "Cartella delle foto",
                m.nomeCartellaFoto.ifEmpty { "Da scegliere" },
                icona = null,
            ) { sceltaCartella = 1 }
            Spacer(Modifier.height(8.dp))
            CampoScelta(
                "Cartella delle ecografie",
                m.nomeCartellaEco.ifEmpty { "Da scegliere (facoltativa)" },
                icona = null,
            ) { sceltaCartella = 2 }
            Spacer(Modifier.height(8.dp))
            CampoScelta(
                "Cartella dei referti",
                m.nomeCartellaRef.ifEmpty { "Da scegliere (facoltativa)" },
                icona = null,
            ) { sceltaCartella = 3 }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { m.sincronizzaDrive(silenzioso = false, subito = true) }, enabled = !m.driveInSync && m.cartellaFoto != null) {
                    Text(if (m.driveInSync) "Sincronizzo…" else "Sincronizza ora")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { chiediScollega = true }) { Text("Scollega") }
            }
            if (m.driveUltima > 0) {
                val quando = Instant.ofEpochMilli(m.driveUltima).atZone(ZoneId.systemDefault())
                val giorno = quando.toLocalDate()
                Text(
                    "Ultima sincronizzazione: " + (if (giorno == LocalDate.now()) "oggi" else dataMedia(giorno)) +
                        " alle " + orario(quando.toLocalTime()),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            Text(
                "La sincronizzazione parte da sola quando apri l'app e quando aggiungi o modifichi una foto. " +
                    "Le foto di Drive si scaricano sul telefono solo quando le apri. " +
                    "Se elimini una foto nell'app finisce nel cestino di Drive (si recupera per 30 giorni); " +
                    "se la elimini su Drive sparisce anche dall'app. Immagini e PDF della cartella delle ecografie " +
                    "vengono collegati all'ecografia dello stesso giorno, che l'app crea se non c'è ancora. " +
                    "Nella cartella dei referti ogni PDF diventa un referto, e le foto dello stesso giorno un referto solo. " +
                    "I PDF si leggono solo dalle cartelle delle ecografie e dei referti.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (sceltaCartella != 0) {
        val quale = when (sceltaCartella) {
            2 -> "eco"
            3 -> "ref"
            else -> "foto"
        }
        val attuale = when (quale) {
            "eco" -> m.cartellaEco
            "ref" -> m.cartellaRef
            else -> m.cartellaFoto
        }
        ScegliCartellaDrive(
            titolo = when (quale) {
                "eco" -> "Cartella delle ecografie"
                "ref" -> "Cartella dei referti"
                else -> "Cartella delle foto"
            },
            avviso = if (attuale != null) {
                "Cambiando cartella, i file della cartella di prima escono dall'app (su Drive restano)."
            } else if (quale == "ref") {
                "Scegli una cartella diversa da quelle delle foto e delle ecografie, anche nuova e vuota."
            } else {
                null
            },
            onScelta = { id, nome ->
                sceltaCartella = 0
                m.impostaCartellaDrive(quale, id, nome)
            },
            onChiudi = { sceltaCartella = 0 },
        )
    }
    if (chiediScollega) {
        AlertDialog(
            onDismissRequest = { chiediScollega = false },
            title = { Text("Scollegare Google Drive?") },
            text = { Text("L'app smette di sincronizzare. Le foto restano sia nell'app sia su Drive.") },
            confirmButton = {
                TextButton(onClick = {
                    chiediScollega = false
                    m.scollegaDrive()
                }) { Text("Scollega") }
            },
            dismissButton = { TextButton(onClick = { chiediScollega = false }) { Text("Annulla") } },
        )
    }
}

/** Estrae l'id di una cartella da un link di Drive (…/folders/ID…) o lo accetta così com'è. */
private fun idDaLink(testo: String): String? {
    val t = testo.trim()
    if (t.isEmpty()) return null
    Regex("folders/([A-Za-z0-9_-]+)").find(t)?.let { return it.groupValues[1] }
    Regex("[?&]id=([A-Za-z0-9_-]+)").find(t)?.let { return it.groupValues[1] }
    return if (Regex("^[A-Za-z0-9_-]{10,}$").matches(t)) t else null
}

/** Navigazione tra le cartelle di Drive per sceglierne una; in alternativa si incolla il link. */
@Composable
private fun ScegliCartellaDrive(titolo: String, avviso: String?, onScelta: (String, String) -> Unit, onChiudi: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // percorso: coppie (id, nome); "root" è Il mio Drive
    var percorso by remember { mutableStateOf(listOf(Pair("root", "Il mio Drive"))) }
    var cartelle by remember { mutableStateOf<List<FileDrive>?>(null) }
    var errore by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf("") }
    var cercoLink by remember { mutableStateOf(false) }
    val attuale = percorso.last()

    LaunchedEffect(attuale.first) {
        cartelle = null
        errore = null
        try {
            cartelle = withContext(Dispatchers.IO) { Drive.cartelle(ctx, attuale.first) }
        } catch (e: Exception) {
            errore = e.message ?: "Non riesco a leggere le cartelle di Drive."
        }
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(titolo) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (avviso != null) {
                    Text(avviso, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (percorso.size > 1) {
                        IconButton(onClick = { percorso = percorso.dropLast(1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cartella superiore")
                        }
                    }
                    Text(
                        percorso.joinToString(" › ") { it.second }, style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = cs.outlineVariant)
                val lista = cartelle
                when {
                    errore != null -> Text(errore ?: "", color = cs.error, style = MaterialTheme.typography.bodyMedium)
                    lista == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                    lista.isEmpty() -> Text(
                        "Nessuna sottocartella qui.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    else -> {
                        for (c in lista) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable { percorso = percorso + Pair(c.id, c.nome) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.nome, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = cs.outlineVariant)
                Text(
                    "Oppure incolla il link della cartella (anche di una cartella condivisa con te):",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    singleLine = true,
                    placeholder = { Text("https://drive.google.com/drive/folders/…") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                val idLink = idDaLink(link)
                if (link.isNotBlank()) {
                    TextButton(
                        onClick = {
                            if (idLink == null) return@TextButton
                            cercoLink = true
                            scope.launch {
                                try {
                                    val f = withContext(Dispatchers.IO) { Drive.info(ctx, idLink) }
                                    if (f.mime == Drive.TIPO_CARTELLA) onScelta(f.id, f.nome) else errore = "Il link non è di una cartella."
                                } catch (e: Exception) {
                                    errore = e.message ?: "Non trovo la cartella del link."
                                }
                                cercoLink = false
                            }
                        },
                        enabled = idLink != null && !cercoLink,
                    ) { Text(if (cercoLink) "Cerco…" else "Usa la cartella del link") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onScelta(attuale.first, percorso.drop(1).joinToString(" › ") { it.second }) },
                enabled = percorso.size > 1,
            ) { Text("Scegli «${attuale.second}»", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
    )
}
