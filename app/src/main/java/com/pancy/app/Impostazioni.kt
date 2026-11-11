@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun ImpostazioniScreen(m: Model, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var sceltaDpp by remember { mutableStateOf(false) }
    var sceltaDum by remember { mutableStateOf(false) }
    var sceltaCal by remember { mutableStateOf(false) }
    var chiediTogli by remember { mutableStateOf(false) }
    var sceltaImport by remember { mutableStateOf(false) }
    var dialogoParole by remember { mutableStateOf(false) }
    // dopo il permesso: 1 = scegli dove copiare, 2 = scegli da dove importare
    var dopoPermesso by remember { mutableStateOf(1) }

    val permessi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.isNotEmpty() && r.values.all { it }) {
            if (dopoPermesso == 2) sceltaImport = true else sceltaCal = true
        } else {
            m.messaggio = "Senza il permesso per il calendario non posso usarlo."
        }
    }
    fun apriSceltaCalendario() {
        dopoPermesso = 1
        if (CalendarSync.haPermesso(ctx)) sceltaCal = true else permessi.launch(CalendarSync.PERMESSI)
    }
    fun apriSceltaImport() {
        dopoPermesso = 2
        if (CalendarSync.haPermesso(ctx)) sceltaImport = true else permessi.launch(CalendarSync.PERMESSI)
    }
    val calImport = remember(m.importaDa) { CalendarSync.calendari(ctx).filter { it.id in m.importaDa } }
    val calAttuale = remember(m.calendario) {
        val id = m.calendario
        if (id == null) null else CalendarSync.calendari(ctx).firstOrNull { it.id == id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = { m.indietro() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = cs.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            // ---- Gravidanza ----
            TitoloSezione("Gravidanza")
            val dpp = m.dpp
            CampoScelta(
                "Data presunta del parto",
                if (dpp == null) "Non impostata" else dataLunga(dpp).maiuscola(),
                icona = Icons.Filled.Favorite,
                sotto = if (dpp == null) null else Gravidanza.settimana(dpp, LocalDate.now())?.let { "Oggi: settimana $it" },
            ) { sceltaDpp = true }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { sceltaDum = true }) { Text("Calcolala dall'ultima mestruazione") }
                Spacer(Modifier.weight(1f))
                if (dpp != null) TextButton(onClick = { m.impostaDpp(null) }) { Text("Togli") }
            }
            Text(
                "La data indicata dal ginecologo dopo l'ecografia del primo trimestre è la più precisa: se ve l'hanno già data, usate quella.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )

            // ---- Calendario ----
            TitoloSezione("Calendario del telefono")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Copia gli appuntamenti nel calendario", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Così arrivano i promemoria e li trovi anche in Google Calendar.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = m.calendario != null,
                        onCheckedChange = { on -> if (on) apriSceltaCalendario() else chiediTogli = true },
                    )
                }
                if (m.calendario != null) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cs.outlineVariant)
                    CampoScelta(
                        "Calendario",
                        when {
                            calAttuale == null -> "Calendario non trovato: scegline un altro"
                            calAttuale.nome == calAttuale.account -> calAttuale.nome
                            else -> calAttuale.nome + " · " + calAttuale.account
                        },
                        icona = Icons.Filled.DateRange,
                    ) { apriSceltaCalendario() }
                    Text(
                        "Promemoria", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((minuti, nome) in PROMEMORIA) {
                            FilterChip(
                                selected = m.promemoria == minuti,
                                onClick = { m.impostaPromemoria(minuti) },
                                label = { Text(nome) },
                            )
                        }
                    }
                    Text(
                        "Per gli appuntamenti senza orario il promemoria arriva la sera prima alle 20.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "Se scegli un calendario condiviso con la tua compagna, gli appuntamenti li vede anche lei. " +
                            "Nel calendario vanno titolo, luogo, medico e note; l'esito resta solo nell'app.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Importa gli appuntamenti dal calendario", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Gli appuntamenti segnati nel calendario compaiono anche nell'agenda dell'app.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = m.importaDa.isNotEmpty(),
                        onCheckedChange = { on -> if (on) apriSceltaImport() else m.impostaImport(emptySet()) },
                    )
                }
                if (m.importaDa.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = cs.outlineVariant)
                    CampoScelta(
                        "Da quali calendari",
                        if (calImport.isEmpty()) "Calendari non trovati: scegline altri" else calImport.joinToString(", ") { it.nome },
                        icona = Icons.Filled.DateRange,
                    ) { apriSceltaImport() }
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Solo gli eventi con certe parole", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (m.importaFiltro) "Utile se nel calendario ci sono anche altri impegni."
                                else "Si importano tutti gli eventi dei calendari scelti.",
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = m.importaFiltro, onCheckedChange = { m.impostaFiltroImport(it, m.paroleImport) })
                    }
                    if (m.importaFiltro) {
                        CampoScelta("Parole da cercare", m.paroleImport, modifier = Modifier.padding(top = 8.dp)) { dialogoParole = true }
                        Text(
                            "Per essere sicuri che un evento arrivi nell'app, basta scrivere «Pancy» nel titolo.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = { m.importaDalCalendario(silenzioso = false) },
                        enabled = !m.importInCorso,
                        modifier = Modifier.padding(top = 10.dp),
                    ) { Text(if (m.importInCorso) "Importo…" else "Importa ora") }
                    Text(
                        "L'app guarda il calendario ogni volta che la apri. Se nel calendario cambiano data, ora o luogo, " +
                            "cambiano anche qui; se un appuntamento viene cancellato, sparisce anche dall'app (tranne quelli già " +
                            "fatti o con un esito). Le modifiche fatte nell'app invece non tornano nel calendario, e gli " +
                            "appuntamenti importati non vengono ricopiati. Se ne elimini uno nell'app, non verrà più importato.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // ---- Google Drive ----
            SezioneDrive(m)

            // ---- Chiedi all'IA ----
            SezioneIA(m)

            // ---- Dati ----
            TitoloSezione("Dati")
            Text(
                "Su questo telefono: ${m.appuntamenti.size} appuntamenti, ${m.ecografie.size} ecografie, " +
                    "${m.referti.size} referti, ${m.media.size} file (foto, video e PDF), ${m.pesi.size} pesate, ${m.consigli.size} consigli, " +
                    "${m.conversazioni.size} conversazioni con l'IA. " +
                    "Gli aggiornamenti dell'app li conservano.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            Text(
                "Appuntamenti, ecografie, referti, pesi, consigli e conversazioni rientrano nel backup automatico di Android (se è attivo). " +
                    "Foto, video, PDF e foto allegate alla chat invece occupano troppo spazio per quel backup: " +
                    "con Google Drive collegato tornano da Drive, altrimenti restano solo su questo telefono.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
            )
        }
    }

    if (sceltaDpp) {
        SceltaData(
            m.dpp ?: LocalDate.now().plusMonths(6), titolo = "Data presunta del parto",
            onScelta = { m.impostaDpp(it) }, onChiudi = { sceltaDpp = false },
        )
    }
    if (sceltaDum) {
        SceltaData(
            LocalDate.now().minusWeeks(8), titolo = "Primo giorno dell'ultima mestruazione",
            onScelta = { dum ->
                val d = Gravidanza.dppDaUltimaMestruazione(dum)
                m.impostaDpp(d)
                m.messaggio = "Data presunta del parto: " + dataLunga(d) + "."
            },
            onChiudi = { sceltaDum = false },
        )
    }
    if (sceltaCal) {
        ScegliCalendario(
            ctx, m.calendario,
            onScelto = { id ->
                sceltaCal = false
                m.attivaCalendario(id)
            },
            onChiudi = { sceltaCal = false },
        )
    }
    if (sceltaImport) {
        ScegliCalendariImport(
            ctx, m.importaDa,
            onScelti = { s ->
                sceltaImport = false
                m.impostaImport(s)
            },
            onChiudi = { sceltaImport = false },
        )
    }
    if (dialogoParole) {
        var valore by remember { mutableStateOf(m.paroleImport) }
        AlertDialog(
            onDismissRequest = { dialogoParole = false },
            title = { Text("Parole da cercare") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Separate da virgole. Si cercano nel titolo, nel luogo e nella descrizione dell'evento. " +
                            "Basta un pezzo di parola: «ginecolog» trova sia «ginecologo» sia «ginecologica».",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = valore,
                        onValueChange = { valore = it },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    m.impostaFiltroImport(true, valore)
                    dialogoParole = false
                    m.importaDalCalendario(silenzioso = false)
                }) { Text("Salva") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { valore = PAROLE_PREDEFINITE }) { Text("Predefinite") }
                    TextButton(onClick = { dialogoParole = false }) { Text("Annulla") }
                }
            },
        )
    }
    if (chiediTogli) {
        AlertDialog(
            onDismissRequest = { chiediTogli = false },
            title = { Text("Smettere di copiare nel calendario?") },
            text = { Text("Vuoi togliere dal calendario anche gli appuntamenti già copiati?") },
            confirmButton = {
                TextButton(onClick = {
                    chiediTogli = false
                    m.disattivaCalendario(togli = true)
                    m.messaggio = "Eventi tolti dal calendario."
                }) { Text("Sì, toglili") }
            },
            dismissButton = {
                TextButton(onClick = {
                    chiediTogli = false
                    m.disattivaCalendario(togli = false)
                }) { Text("No, lasciali") }
            },
        )
    }
}

@Composable
fun ScegliCalendario(ctx: Context, attuale: Long?, onScelto: (Long) -> Unit, onChiudi: () -> Unit) {
    val cals = remember { CalendarSync.calendari(ctx).filter { it.scrivibile && it.sincronizzato } }
    var scelto by remember {
        mutableStateOf(attuale?.takeIf { a -> cals.any { it.id == a } } ?: cals.firstOrNull()?.id)
    }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("In quale calendario?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cals.isEmpty()) {
                    Text("Non trovo nessun calendario su cui scrivere. Controlla che Google Calendar sia installato e sincronizzato con il tuo account.")
                }
                for (c in cals) {
                    Opzione(c.nome, if (c.nome == c.account) null else c.account, scelto == c.id) { scelto = c.id }
                }
                Text(
                    "Non vedi il calendario che cerchi? In Google Calendar apri Impostazioni, tocca il tuo account e attiva la sincronizzazione di quel calendario.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            val s = scelto
            TextButton(onClick = { if (s != null) onScelto(s) }, enabled = s != null) { Text("Usa questo") }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
    )
}

/** Scelta (anche multipla) dei calendari da cui importare. Mostra anche quelli in sola lettura, come i calendari condivisi. */
@Composable
fun ScegliCalendariImport(ctx: Context, attuali: Set<Long>, onScelti: (Set<Long>) -> Unit, onChiudi: () -> Unit) {
    val cals = remember { CalendarSync.calendari(ctx).filter { it.sincronizzato } }
    var scelti by remember { mutableStateOf(attuali.filter { a -> cals.any { it.id == a } }.toSet()) }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Da quali calendari?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (cals.isEmpty()) {
                    Text("Non trovo nessun calendario sul telefono. Controlla che Google Calendar sia installato e sincronizzato.")
                }
                for (c in cals) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { scelti = if (c.id in scelti) scelti - c.id else scelti + c.id }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = c.id in scelti, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nome)
                            if (c.nome != c.account) {
                                Text(c.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(
                    "Per importare gli appuntamenti della tua compagna, lei deve condividere con il tuo account Google " +
                        "il suo calendario (oppure create insieme un calendario solo per la gravidanza). " +
                        "Se poi non lo vedi qui, in Google Calendar apri Impostazioni, tocca il calendario e attiva la sincronizzazione.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onScelti(scelti) }, enabled = scelti.isNotEmpty()) { Text("Importa") }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
    )
}
