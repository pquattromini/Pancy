@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Le sezioni della barra in basso, in quest'ordine. Per spostarne una basta spostare la riga. */
enum class Sezione(val etichetta: String, val titolo: String) {
    AGENDA("Agenda", "Appuntamenti"),
    ECOGRAFIE("Ecografie", "Ecografie"),
    FOTO("Foto", "Foto e video"),
    STORICO("Storico", "Storico"),
    CONSIGLI("Consigli", "Consigli"),
}

/** Le schermate dell'app. */
sealed interface Schermata {
    /** La pagina principale di una sezione (con la barra in basso). */
    data class Radice(val sezione: Sezione) : Schermata
    data class Dettaglio(val id: String) : Schermata
    /** [id] null = nuovo appuntamento; [bozza] = valori già compilati (per esempio dal calendario degli esami). */
    data class Modifica(val id: String?, val bozza: Appuntamento? = null) : Schermata
    data object Impostazioni : Schermata
    /** Foto o video a schermo intero; con [ecografia] si scorrono solo le immagini di quell'ecografia. */
    data class Visore(val id: String, val ecografia: String? = null) : Schermata
    data class EcoDettaglio(val id: String) : Schermata
    /** [id] null = nuova ecografia; [daAppuntamento] = l'appuntamento da cui nasce. */
    data class EcoModifica(val id: String?, val daAppuntamento: String? = null) : Schermata
    /** Elenco delle conversazioni con l'IA. */
    data object ChiediIA : Schermata
    /** Una conversazione; [id] null = nuova; [bozza] = domanda già scritta da completare. */
    data class Chat(val id: String?, val bozza: String? = null) : Schermata
}

@Composable
fun PancyApp() {
    val ctx = LocalContext.current
    val m = remember { Model.di(ctx) }
    val snack = remember { SnackbarHostState() }
    // conserva lo scorrimento delle pagine principali quando si apre un dettaglio e si torna indietro
    val stati = rememberSaveableStateHolder()
    val msg = m.messaggio
    LaunchedEffect(msg) {
        if (msg != null) {
            snack.showSnackbar(msg)
            m.messaggio = null
        }
    }
    // "indietro" del telefono: torna alla schermata precedente, poi all'agenda, poi esce dall'app
    BackHandler(enabled = m.puoTornare) { m.indietro() }
    when (val s = m.schermata) {
        is Schermata.Radice -> stati.SaveableStateProvider("radice-" + s.sezione.name) {
            when (s.sezione) {
                Sezione.AGENDA -> ListaScreen(m, snack)
                Sezione.ECOGRAFIE -> EcografieScreen(m, snack)
                Sezione.FOTO -> FotoScreen(m, snack)
                Sezione.STORICO -> StoricoScreen(m, snack)
                Sezione.CONSIGLI -> ConsigliScreen(m, snack)
            }
        }
        is Schermata.Dettaglio -> DettaglioScreen(m, s.id, snack)
        is Schermata.Modifica -> ModificaScreen(m, s.id, s.bozza, snack)
        Schermata.Impostazioni -> ImpostazioniScreen(m, snack)
        is Schermata.Visore -> VisoreScreen(m, s.id, s.ecografia)
        is Schermata.EcoDettaglio -> EcografiaScreen(m, s.id, snack)
        is Schermata.EcoModifica -> EcoModificaScreen(m, s.id, s.daAppuntamento, snack)
        Schermata.ChiediIA -> ChiediIAScreen(m, snack)
        is Schermata.Chat -> ChatScreen(m, s.id, s.bozza, snack)
    }
}

/** Barra in alto delle pagine principali: titolo, settimana di gravidanza e impostazioni. */
@Composable
fun BarraSuperiore(m: Model, titolo: String, settimana: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val dpp = m.dpp
    val g = if (settimana && dpp != null) Gravidanza.giorni(dpp, LocalDate.now()) else null
    TopAppBar(
        title = {
            Column {
                Text(titolo, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (g != null) {
                    Text(
                        "Settimana ${g / 7}+${g % 7} · " + Gravidanza.nomeTrimestre(g),
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            FilledTonalButton(
                onClick = { m.vai(Schermata.ChiediIA) },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Icon(painterResource(R.drawable.ic_ia), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("IA", maxLines = 1, softWrap = false)
            }
            IconButton(onClick = { m.vai(Schermata.Impostazioni) }) {
                Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
    )
}

/** La barra in basso con le sezioni. */
@Composable
fun BarraSezioni(m: Model) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        for (s in Sezione.entries) {
            NavigationBarItem(
                selected = m.sezione == s,
                onClick = { m.apriSezione(s) },
                icon = { Icon(iconaSezione(s), contentDescription = null) },
                label = { Text(s.etichetta, maxLines = 1, softWrap = false) },
            )
        }
    }
}

@Composable
fun iconaSezione(s: Sezione): Painter = when (s) {
    Sezione.AGENDA -> rememberVectorPainter(Icons.Filled.DateRange)
    Sezione.ECOGRAFIE -> painterResource(R.drawable.ic_ecografie)
    Sezione.FOTO -> painterResource(R.drawable.ic_foto)
    Sezione.STORICO -> painterResource(R.drawable.ic_storico)
    Sezione.CONSIGLI -> painterResource(R.drawable.ic_consigli)
}

/** Pagina vuota di una sezione, con una frase che spiega cosa ci andrà. */
@Composable
fun VuotoSezione(icona: Painter, titolo: String, testo: String, azione: String? = null, onAzione: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icona, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(
            titolo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            testo, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
        if (azione != null) {
            TextButton(onClick = onAzione, modifier = Modifier.padding(top = 8.dp)) { Text(azione) }
        }
    }
}

/** Apre un indirizzo web nel browser. Falso se sul telefono non c'è un browser. */
fun apriLink(ctx: Context, url: String): Boolean =
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
