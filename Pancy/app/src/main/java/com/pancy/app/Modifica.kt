@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private val frasi = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/** Nuovo appuntamento ([id] null) o modifica di uno esistente. */
@Composable
fun ModificaScreen(m: Model, id: String?, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    val originale = remember(id) { id?.let { m.trova(it) } }
    val nuovoId = rememberSaveable { UUID.randomUUID().toString() }

    var tipo by rememberSaveable { mutableStateOf(originale?.tipo ?: Tipo.VISITA) }
    var titolo by rememberSaveable { mutableStateOf(originale?.titolo ?: "") }
    var data by rememberSaveable { mutableStateOf(originale?.data ?: LocalDate.now().plusDays(1)) }
    var conOra by rememberSaveable { mutableStateOf(originale == null || originale.ora != null) }
    var oraScelta by rememberSaveable { mutableStateOf(originale?.ora ?: LocalTime.of(9, 0)) }
    var luogo by rememberSaveable { mutableStateOf(originale?.luogo ?: "") }
    var medico by rememberSaveable { mutableStateOf(originale?.medico ?: "") }
    var note by rememberSaveable { mutableStateOf(originale?.note ?: "") }
    var esito by rememberSaveable { mutableStateOf(originale?.esito ?: "") }

    var sceltaData by remember { mutableStateOf(false) }
    var sceltaOra by remember { mutableStateOf(false) }
    var chiediUscita by remember { mutableStateOf(false) }

    fun costruisci(): Appuntamento = (originale ?: Appuntamento(id = nuovoId)).copy(
        tipo = tipo,
        titolo = titolo.trim().ifEmpty { tipo.nome },
        data = data,
        ora = if (conOra) oraScelta else null,
        luogo = luogo.trim(),
        medico = medico.trim(),
        note = note.trim(),
        esito = esito.trim(),
    )

    val modificato =
        if (originale != null) costruisci() != originale
        else titolo.isNotBlank() || luogo.isNotBlank() || medico.isNotBlank() || note.isNotBlank() || esito.isNotBlank()

    fun salvaEdEsci() {
        val a = costruisci()
        m.salva(a)
        if (m.messaggio == null) m.messaggio = if (originale == null) "Appuntamento aggiunto." else "Modifiche salvate."
        if (originale == null) {
            m.scheda = if (a.passato()) 1 else 0
            m.schermata = Schermata.Lista
        } else {
            m.schermata = Schermata.Dettaglio(a.id)
        }
    }

    // con modifiche non salvate, "indietro" chiede prima conferma
    BackHandler(enabled = modificato) { chiediUscita = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (originale == null) "Nuovo appuntamento" else "Modifica") },
                navigationIcon = {
                    IconButton(onClick = { if (modificato) chiediUscita = true else m.indietro() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                    }
                },
                actions = {
                    TextButton(onClick = { salvaEdEsci() }) { Text("Salva", fontWeight = FontWeight.Bold) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = cs.background,
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Tipo", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in Tipo.entries) {
                    FilterChip(
                        selected = tipo == t,
                        onClick = { tipo = t },
                        label = { Text(t.nome) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = t.colore.copy(alpha = 0.18f),
                            selectedLabelColor = t.colore,
                        ),
                    )
                }
            }

            OutlinedTextField(
                value = titolo,
                onValueChange = { titolo = it },
                label = { Text("Titolo") },
                placeholder = { Text(tipo.suggerimenti.first()) },
                singleLine = true,
                keyboardOptions = frasi,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in tipo.suggerimenti) {
                    SuggestionChip(onClick = { titolo = s }, label = { Text(s) })
                }
            }

            CampoScelta(
                "Data", dataLunga(data).maiuscola(), icona = Icons.Filled.DateRange,
                sotto = Gravidanza.settimana(m.dpp, data)?.let { "Settimana $it" },
            ) { sceltaData = true }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Orario", style = MaterialTheme.typography.bodyLarge)
                    if (!conOra) {
                        Text(
                            "Senza orario: nel calendario diventa un evento di tutto il giorno.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = conOra, onCheckedChange = { conOra = it })
            }
            if (conOra) {
                CampoScelta("Ora", orario(oraScelta), icona = Icons.Filled.Notifications) { sceltaOra = true }
            }

            OutlinedTextField(
                value = luogo,
                onValueChange = { luogo = it },
                label = { Text("Luogo") },
                placeholder = { Text("Ospedale, ambulatorio, indirizzo") },
                keyboardOptions = frasi,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = medico,
                onValueChange = { medico = it },
                label = { Text("Medico, ostetrica o reparto") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                placeholder = { Text("Cosa portare, domande da fare, se serve il digiuno…") },
                minLines = 3,
                keyboardOptions = frasi,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = esito,
                onValueChange = { esito = it },
                label = { Text("Esito e appunti (dopo l'appuntamento)") },
                minLines = 3,
                keyboardOptions = frasi,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { salvaEdEsci() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) { Text("Salva") }
        }
    }

    if (sceltaData) SceltaData(data, onScelta = { data = it }, onChiudi = { sceltaData = false })
    if (sceltaOra) SceltaOra(oraScelta, onScelta = { oraScelta = it }, onChiudi = { sceltaOra = false })
    if (chiediUscita) {
        AlertDialog(
            onDismissRequest = { chiediUscita = false },
            title = { Text("Salvare le modifiche?") },
            text = { Text("Se esci adesso, quello che hai scritto va perso.") },
            confirmButton = {
                TextButton(onClick = {
                    chiediUscita = false
                    salvaEdEsci()
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = {
                    chiediUscita = false
                    m.indietro()
                }) { Text("Esci senza salvare") }
            },
        )
    }
}
