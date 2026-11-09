@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Composable
fun PancyApp() {
    val ctx = LocalContext.current
    val m = remember { Model.di(ctx) }
    val snack = remember { SnackbarHostState() }
    val msg = m.messaggio
    LaunchedEffect(msg) {
        if (msg != null) {
            snack.showSnackbar(msg)
            m.messaggio = null
        }
    }
    // "indietro" del telefono: dalle altre schermate si torna alla lista (dalla lista si esce dall'app)
    BackHandler(enabled = m.schermata != Schermata.Lista) { m.indietro() }
    when (val s = m.schermata) {
        Schermata.Lista -> ListaScreen(m, snack)
        is Schermata.Dettaglio -> DettaglioScreen(m, s.id, snack)
        is Schermata.Modifica -> ModificaScreen(m, s.id, snack)
        Schermata.Impostazioni -> ImpostazioniScreen(m, snack)
    }
}

@Composable
fun ListaScreen(m: Model, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    val adesso = LocalDateTime.now()
    val futuri = m.appuntamenti.filter { !it.passato(adesso) }
    val passati = m.appuntamenti.filter { it.passato(adesso) }.reversed()
    val inProgramma = m.scheda == 0
    val lista = if (inProgramma) futuri else passati

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pancy", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = { m.schermata = Schermata.Impostazioni }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { m.schermata = Schermata.Modifica(null) },
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
            item(key = "gravidanza") { GravidanzaCard(m) }
            item(key = "schede") {
                Segmenti(
                    listOf("In programma (${futuri.size})", "Passati (${passati.size})"),
                    m.scheda,
                ) { m.scheda = it }
            }
            if (lista.isEmpty()) {
                item(key = "vuoto") { Vuoto(inProgramma) }
            } else {
                var meseVisto: String? = null
                lista.forEachIndexed { i, a ->
                    val ms = mese(a.data)
                    if (ms != meseVisto) {
                        meseVisto = ms
                        item(key = "mese-$ms") {
                            Text(
                                ms, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                    item(key = a.id) {
                        AppuntamentoCard(a, m.dpp, prossimo = inProgramma && i == 0) {
                            m.schermata = Schermata.Dettaglio(a.id)
                        }
                    }
                }
            }
        }
    }
}

/** In cima alla lista: a che settimana siete e quanto manca alla data presunta del parto. */
@Composable
fun GravidanzaCard(m: Model) {
    val cs = MaterialTheme.colorScheme
    val dpp = m.dpp
    val oggi = LocalDate.now()
    if (dpp == null) {
        AppCard(onClick = { m.schermata = Schermata.Impostazioni }) {
            Text("A che settimana siete?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Inserisci la data presunta del parto: vedrai la settimana di gravidanza qui e su ogni appuntamento.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            Text(
                "Imposta la data", color = cs.primary, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }
    val g = Gravidanza.giorni(dpp, oggi)
    val mancano = ChronoUnit.DAYS.between(oggi, dpp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (g != null) {
                Text(Gravidanza.nomeTrimestre(g).maiuscola(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("Settimana ${g / 7}+${g % 7}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                val p = (g.toFloat() / Gravidanza.DURATA_GIORNI).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(cs.onPrimaryContainer.copy(alpha = 0.12f)),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(cs.primary))
                }
            }
            Text(
                when {
                    mancano > 1 -> "Mancano $mancano giorni alla data presunta del parto, ${dataMedia(dpp)}."
                    mancano == 1L -> "Domani è la data presunta del parto."
                    mancano == 0L -> "Oggi è la data presunta del parto."
                    else -> "La data presunta del parto era ${dataMedia(dpp)}."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun AppuntamentoCard(a: Appuntamento, dpp: LocalDate?, prossimo: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val passato = a.passato()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(if (prossimo) 2.dp else 1.dp, if (prossimo) cs.primary else cs.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // riquadro con la data, del colore del tipo
            Column(
                Modifier.width(56.dp).clip(RoundedCornerShape(12.dp))
                    .background(a.tipo.colore.copy(alpha = 0.14f)).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(giornoBreve(a.data), style = MaterialTheme.typography.labelSmall, color = a.tipo.colore, fontWeight = FontWeight.Bold)
                Text(a.data.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, color = a.tipo.colore, fontWeight = FontWeight.ExtraBold)
                Text(meseBreve(a.data), style = MaterialTheme.typography.labelSmall, color = a.tipo.colore)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (prossimo) {
                    Text(
                        "Prossimo · " + quando(a.data), style = MaterialTheme.typography.labelMedium,
                        color = cs.primary, fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    a.titolo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val dove = listOfNotNull(
                    a.ora?.let { "ore " + orario(it) },
                    a.luogo.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (dove.isNotEmpty()) {
                    Text(
                        dove, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val altro = listOfNotNull(
                    a.tipo.nome,
                    Gravidanza.settimana(dpp, a.data)?.let { "sett. $it" },
                    if (!passato && !prossimo) quando(a.data) else null,
                ).joinToString(" · ")
                Text(altro, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (passato) {
                    if (a.esito.isNotBlank()) {
                        Text(
                            "Esito: " + a.esito.lines().first(), style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                        )
                    } else {
                        Text(
                            "Aggiungi l'esito", style = MaterialTheme.typography.bodySmall, color = cs.primary,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Vuoto(inProgramma: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.DateRange, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Text(
            if (inProgramma) "Nessun appuntamento in programma" else "Nessun appuntamento passato",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            if (inProgramma) "Tocca «Nuovo» per aggiungere la prossima visita, ecografia o esame."
            else "Qui finiscono gli appuntamenti già fatti, con il loro esito.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
