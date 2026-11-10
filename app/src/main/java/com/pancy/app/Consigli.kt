@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Primo giorno della finestra di un esame, se si conosce la data presunta del parto. */
private fun inizioFinestra(e: Esame, dpp: LocalDate): LocalDate = Gravidanza.inizio(dpp).plusWeeks(e.da.toLong())
private fun fineFinestra(e: Esame, dpp: LocalDate): LocalDate = Gravidanza.inizio(dpp).plusWeeks(e.a.toLong() + 1).minusDays(1)

/** Apre il modulo del nuovo appuntamento già compilato con l'esame. */
private fun aggiungiInAgenda(m: Model, e: Esame) {
    val dpp = m.dpp
    val oggi = LocalDate.now()
    val inizio = if (dpp != null) inizioFinestra(e, dpp) else null
    val data = if (inizio != null && inizio.isAfter(oggi)) inizio else oggi.plusDays(1)
    m.vai(
        Schermata.Modifica(
            null,
            Appuntamento(tipo = e.tipo, titolo = e.titolo, data = data, note = e.testo, esame = e.chiave),
        )
    )
}

@Composable
fun ConsigliScreen(m: Model, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var nuovo by remember { mutableStateOf(false) }
    var inModifica by remember { mutableStateOf<Consiglio?>(null) }
    val dpp = m.dpp
    val settimana = if (dpp != null) Gravidanza.giorni(dpp, LocalDate.now())?.let { it / 7 } else null

    Scaffold(
        topBar = { BarraSuperiore(m, Sezione.CONSIGLI.titolo) },
        bottomBar = { BarraSezioni(m) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nuovo = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Consiglio") },
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
            item(key = "avviso") {
                Text(
                    "Informazioni generali da fonti ufficiali (ISS e Ministero della Salute). " +
                        "Per ogni decisione vale quello che dicono il vostro medico e la vostra ostetrica.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            item(key = "periodo") { InQuestoPeriodo(m, settimana) }

            item(key = "titolo-esperti") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { TitoloSezione("I consigli dei vostri esperti") }
                    TextButton(onClick = { nuovo = true }) { Text("Aggiungi") }
                }
            }
            if (m.consigli.isEmpty()) {
                item(key = "vuoto-esperti") {
                    AppCard(onClick = { nuovo = true }) {
                        Text(
                            "Qui potete annotare quello che vi dicono medico, ostetrica o altri esperti: " +
                                "indicazioni sulla dieta, esercizi, cose da tenere d'occhio. Così non si perde nulla.",
                            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(m.consigli, key = { it.id }) { c -> ConsiglioCard(c) { inModifica = c } }
            }

            item(key = "titolo-guida") { TitoloSezione("Guida") }
            item(key = "esami") {
                Espandibile("Calendario degli esami", apertoAllInizio = true, sotto = "Gratuiti con il Servizio Sanitario") {
                    Text(Guida.NOTA_ESAMI, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    if (dpp == null) {
                        Text(
                            "Imposta la data presunta del parto per vedere le date di ogni periodo ›",
                            style = MaterialTheme.typography.bodySmall, color = cs.primary, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp).clickable { m.vai(Schermata.Impostazioni) },
                        )
                    }
                    Guida.esami.forEach { e ->
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = cs.outlineVariant)
                        EsameRiga(m, e, settimana)
                    }
                }
            }
            item(key = "vaccini") {
                Espandibile("Vaccini in gravidanza") {
                    Guida.vaccini.forEach { (t, s) -> RigaElenco(t, s) }
                }
            }
            item(key = "stile") {
                Espandibile("Alimentazione e stile di vita") {
                    Guida.stileDiVita.forEach { (t, s) -> RigaElenco(t, s) }
                }
            }
            item(key = "urgenze") {
                Espandibile("Quando chiamare subito", sotto = "Il punto nascita, oppure il 112 (o 118)") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = cs.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Non aspettate il controllo successivo se c'è uno di questi segnali:",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Guida.urgenze.forEach { RigaElenco(null, it) }
                    Text(
                        "Nel dubbio, chiamate: è sempre meglio un controllo in più.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            item(key = "fonti") {
                Espandibile("Fonti") {
                    Guida.fonti.forEach { f ->
                        Text(
                            f.titolo + " ›", style = MaterialTheme.typography.bodyMedium, color = cs.primary,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                .clickable { if (!apriLink(ctx, f.url)) m.messaggio = "Non trovo un browser per aprire il link." }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (nuovo) {
        ConsiglioDialog(null, autoriSuggeriti(m), onSalva = { m.salvaConsiglio(it); nuovo = false }, onElimina = null) { nuovo = false }
    }
    val c = inModifica
    if (c != null) {
        ConsiglioDialog(
            c, autoriSuggeriti(m),
            onSalva = { m.salvaConsiglio(it); inModifica = null },
            onElimina = { m.eliminaConsiglio(c.id); inModifica = null },
        ) { inModifica = null }
    }
}

/** I nomi già scritti negli appuntamenti e nei consigli, da toccare invece di riscriverli. */
private fun autoriSuggeriti(m: Model): List<String> =
    (m.consigli.map { it.autore } + m.appuntamenti.reversed().map { it.medico })
        .map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(6)

/** Cosa c'è da fare in queste settimane, secondo il calendario degli esami. */
@Composable
private fun InQuestoPeriodo(m: Model, settimana: Int?) {
    val cs = MaterialTheme.colorScheme
    val dpp = m.dpp
    if (dpp == null || settimana == null) {
        AppCard(onClick = { m.vai(Schermata.Impostazioni) }) {
            Text("In questo periodo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Inserisci la data presunta del parto: qui vedrai gli esami da fare nelle prossime settimane.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
        }
        return
    }
    val adesso = Guida.esami.filter { settimana in (it.da - 1)..it.a && esameInAgenda(m, it) == null }
    val prossimo = Guida.esami.firstOrNull { it.da > settimana + 1 }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("In questo periodo · settimana $settimana", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (adesso.isEmpty()) {
                Text(
                    "Nessun esame da prenotare in queste settimane.",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            adesso.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.titolo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            e.quando + (e.soloSe?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FilledTonalButton(onClick = { aggiungiInAgenda(m, e) }) { Text("In agenda") }
                }
            }
            if (prossimo != null) {
                Text(
                    "Poi: ${prossimo.titolo.replaceFirstChar { it.lowercase() }}, dal ${dataMedia(inizioFinestra(prossimo, dpp))}.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** L'appuntamento in agenda nato da quell'esame, se c'è. */
private fun esameInAgenda(m: Model, e: Esame): Appuntamento? = m.appuntamenti.firstOrNull { it.esame == e.chiave }

@Composable
private fun EsameRiga(m: Model, e: Esame, settimana: Int?) {
    val cs = MaterialTheme.colorScheme
    val dpp = m.dpp
    val app = esameInAgenda(m, e)
    val passato = settimana != null && settimana > e.a && app == null
    val ora = settimana != null && settimana in e.da..e.a
    Column(Modifier.fillMaxWidth().alpha(if (passato) 0.6f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(e.tipo.colore))
            Spacer(Modifier.width(8.dp))
            Text(e.titolo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (ora) {
                Text(
                    "Adesso", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = cs.onPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(cs.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        val date = if (dpp != null) " · dal ${dataBreve(inizioFinestra(e, dpp))} al ${dataBreve(fineFinestra(e, dpp))}" else ""
        Text(e.quando + date, style = MaterialTheme.typography.bodySmall, color = cs.primary, modifier = Modifier.padding(top = 2.dp))
        if (e.soloSe != null) {
            Text(e.soloSe, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = cs.onSurfaceVariant)
        }
        Text(e.testo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        if (app != null) {
            Text(
                (if (app.passato()) "Fatto: " else "In agenda: ") + dataMedia(app.data) + " ›",
                style = MaterialTheme.typography.bodyMedium, color = cs.primary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp))
                    .clickable { m.vai(Schermata.Dettaglio(app.id)) }.padding(vertical = 4.dp),
            )
        } else {
            TextButton(onClick = { aggiungiInAgenda(m, e) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Aggiungi in agenda")
            }
        }
    }
}

@Composable
private fun ConsiglioCard(c: Consiglio, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(onClick = onClick) {
        Text(
            listOf(c.autore.ifBlank { "Consiglio" }, dataMedia(c.data)).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge, color = cs.primary, fontWeight = FontWeight.Bold,
        )
        Text(
            c.testo, style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Nuovo consiglio ([c] null) o modifica. */
@Composable
private fun ConsiglioDialog(
    c: Consiglio?,
    autori: List<String>,
    onSalva: (Consiglio) -> Unit,
    onElimina: (() -> Unit)?,
    onChiudi: () -> Unit,
) {
    var autore by rememberSaveable { mutableStateOf(c?.autore ?: "") }
    var testo by rememberSaveable { mutableStateOf(c?.testo ?: "") }
    var data by rememberSaveable { mutableStateOf(c?.data ?: LocalDate.now()) }
    var sceltaData by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(if (c == null) "Nuovo consiglio" else "Consiglio") },
        text = {
            Column(Modifier.verticalScrollSicuro(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text("Il consiglio") },
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = autore,
                    onValueChange = { autore = it },
                    label = { Text("Chi l'ha dato") },
                    placeholder = { Text("Per esempio: ostetrica Laura") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (autori.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (a in autori) SuggestionChip(onClick = { autore = a }, label = { Text(a) })
                    }
                }
                CampoScelta("Data", dataLunga(data).maiuscola()) { sceltaData = true }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = c ?: Consiglio()
                    onSalva(base.copy(autore = autore.trim(), testo = testo.trim(), data = data))
                },
                enabled = testo.isNotBlank(),
            ) { Text("Salva") }
        },
        dismissButton = {
            Row {
                if (onElimina != null) TextButton(onClick = onElimina) { Text("Elimina") }
                TextButton(onClick = onChiudi) { Text("Annulla") }
            }
        },
    )
    if (sceltaData) SceltaData(data, onScelta = { data = it }, onChiudi = { sceltaData = false })
}

/** Scorrimento verticale per il contenuto dei dialoghi (se la tastiera lo copre). */
@Composable
private fun Modifier.verticalScrollSicuro(): Modifier =
    this.verticalScroll(rememberScrollState())
