@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Apre il luogo nell'app delle mappe (Google Maps o quella che c'è sul telefono). */
fun apriMappa(ctx: Context, luogo: String): Boolean =
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(luogo))))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

@Composable
fun DettaglioScreen(m: Model, id: String, snack: SnackbarHostState) {
    val a = m.trova(id)
    if (a == null) {
        LaunchedEffect(id) { m.indietro() }
        return
    }
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var chiediElimina by remember { mutableStateOf(false) }
    var esito by rememberSaveable(id) { mutableStateOf(a.esito) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(a.tipo.nome) },
                navigationIcon = {
                    IconButton(onClick = { m.indietro() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { m.vai(Schermata.Modifica(a.id)) }) {
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
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TipoEtichetta(a.tipo)
            Text(a.titolo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)

            AppCard {
                RigaInfo(
                    Icons.Filled.DateRange,
                    dataLunga(a.data).maiuscola(),
                    (a.ora?.let { "ore " + orario(it) } ?: "Orario non indicato") + " · " + quando(a.data),
                )
                val dpp = m.dpp
                val g = if (dpp == null) null else Gravidanza.giorni(dpp, a.data)
                if (g != null) {
                    RigaInfo(
                        Icons.Filled.Favorite,
                        "Settimana ${g / 7}+${g % 7}",
                        Gravidanza.settimanaPerEsteso(g) + ", " + Gravidanza.nomeTrimestre(g),
                    )
                }
                if (a.luogo.isNotBlank()) {
                    RigaInfo(Icons.Filled.Place, a.luogo, azione = "Apri nella mappa") {
                        if (!apriMappa(ctx, a.luogo)) m.messaggio = "Non trovo un'app per le mappe."
                    }
                }
                if (a.medico.isNotBlank()) RigaInfo(Icons.Filled.Person, a.medico)
            }

            if (a.tipo == Tipo.ECOGRAFIA) {
                val eco = m.ecografiaDi(a.id)
                AppCard(onClick = {
                    if (eco != null) m.vai(Schermata.EcoDettaglio(eco.id)) else m.vai(Schermata.EcoModifica(null, a.id))
                }) {
                    Text("Risultati dell'ecografia", style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            eco != null -> riassuntoMisure(eco).ifEmpty { "Risultati inseriti." }
                            a.passato() -> "Aggiungi le misure del referto, il peso stimato e le immagini."
                            else -> "Dopo l'ecografia potrai aggiungere misure, peso stimato e immagini."
                        },
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        if (eco != null) "Vedi i risultati ›" else "Aggiungi i risultati ›",
                        style = MaterialTheme.typography.bodyMedium, color = cs.primary, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            val refs = m.refertiDi(a.id)
            // gli esami hanno sempre il riquadro del referto; visite e altro solo dopo, o se ne hanno già uno
            val conReferto = a.tipo == Tipo.ESAMI ||
                ((a.tipo == Tipo.VISITA || a.tipo == Tipo.ALTRO) && (a.passato() || refs.isNotEmpty()))
            if (conReferto) {
                AppCard {
                    Text(
                        if (a.tipo == Tipo.ESAMI) "Referto degli esami" else "Referti e documenti",
                        style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold,
                    )
                    if (refs.isEmpty()) {
                        Text(
                            if (a.passato()) "Aggiungi l'esito e il PDF o la foto del referto: li trovi poi nella sezione Referti."
                            else "Dopo l'appuntamento potrai aggiungere qui il referto.",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    for (r in refs) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(8.dp))
                                .clickable { m.vai(Schermata.RefDettaglio(r.id)) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.titolo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    r.esito.ifBlank { r.tipo.nome }, style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
                        }
                    }
                    Text(
                        if (refs.isEmpty()) "Aggiungi il referto ›" else "Aggiungi un altro referto ›",
                        style = MaterialTheme.typography.bodyMedium, color = cs.primary, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp))
                            .clickable { m.vai(Schermata.RefModifica(null, a.id)) }.padding(vertical = 2.dp),
                    )
                }
            }

            if (a.note.isNotBlank()) {
                AppCard {
                    Text("Note", style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
                    Text(a.note, modifier = Modifier.padding(top = 4.dp))
                }
            }

            AppCard {
                Text("Esito e appunti", style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold)
                Text(
                    if (a.passato()) "Cosa vi hanno detto, valori, prossimi passi: resta tutto qui."
                    else "Da compilare dopo l'appuntamento.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = esito,
                    onValueChange = { esito = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (esito.trim() != a.esito) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                    ) {
                        TextButton(onClick = { esito = a.esito }) { Text("Annulla") }
                        Button(onClick = {
                            m.salva(a.copy(esito = esito.trim()))
                            esito = esito.trim()
                            if (m.messaggio == null) m.messaggio = "Esito salvato."
                        }) { Text("Salva") }
                    }
                }
            }

            if (a.esterno != null) {
                Text(
                    "Importato dal calendario del telefono: se lì cambiano data, ora o luogo, si aggiornano anche qui.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            } else if (m.calendario != null) {
                Text(
                    if (a.evento != null) "Copiato nel calendario del telefono." else "Non è nel calendario del telefono.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
        }
    }

    if (chiediElimina) {
        AlertDialog(
            onDismissRequest = { chiediElimina = false },
            title = { Text("Eliminare l'appuntamento?") },
            text = {
                Text(
                    "«${a.titolo}» verrà tolto dall'app" + when {
                        a.esterno != null -> ". Nel calendario resta, ma non verrà più importato."
                        a.evento != null -> " e dal calendario del telefono."
                        else -> "."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chiediElimina = false
                    m.elimina(a.id)
                    m.indietro()
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { chiediElimina = false }) { Text("Annulla") } },
        )
    }
}
