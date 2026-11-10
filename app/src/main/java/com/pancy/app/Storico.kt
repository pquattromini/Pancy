@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Asse orizzontale dei grafici: le settimane di gravidanza se c'è la data presunta del parto
 * (e tutte le date cadono in gravidanza), altrimenti i giorni dalla prima data.
 */
private class Asse(date: List<LocalDate>, dpp: LocalDate?) {
    private val primo: LocalDate = date.minByOrNull { it.toEpochDay() } ?: LocalDate.now()
    val settimane = dpp != null && date.all { Gravidanza.giorni(dpp, it) != null }
    private val inizio = if (settimane && dpp != null) Gravidanza.inizio(dpp) else primo
    val passi = if (settimane) listOf(1f, 2f, 4f, 5f, 8f, 10f) else listOf(1f, 2f, 7f, 14f, 28f, 56f, 91f, 182f)
    val titolo = if (settimane) "Settimane di gravidanza" else "Data"

    fun x(d: LocalDate): Float {
        val giorni = ChronoUnit.DAYS.between(inizio, d).toFloat()
        return if (settimane) giorni / 7f else giorni
    }

    fun etichetta(x: Float): String =
        if (settimane) numero(x.toDouble(), 0) else dataBreve(inizio.plusDays(x.toLong()))
}

@Composable
fun StoricoScreen(m: Model, snack: SnackbarHostState) {
    val cs = MaterialTheme.colorScheme
    var modificaPeso by remember { mutableStateOf<Peso?>(null) }
    var nuovoPeso by remember { mutableStateOf(false) }
    var dialogoIniziale by remember { mutableStateOf(false) }
    val mamma = m.schedaStorico == 0

    Scaffold(
        topBar = { BarraSuperiore(m, Sezione.STORICO.titolo) },
        bottomBar = { BarraSezioni(m) },
        floatingActionButton = {
            if (mamma) {
                ExtendedFloatingActionButton(
                    onClick = { nuovoPeso = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Peso") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        containerColor = cs.background,
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "schede") {
                Segmenti(listOf("Peso della mamma", "Peso del bambino"), m.schedaStorico) { m.schedaStorico = it }
            }
            if (mamma) {
                if (m.pesi.isEmpty()) {
                    item(key = "vuoto-mamma") {
                        VuotoSezione(
                            painterResource(R.drawable.ic_storico),
                            "Nessun peso inserito",
                            "Aggiungi il peso quando vuoi: qui vedrai l'andamento settimana dopo settimana.",
                        )
                    }
                    item(key = "iniziale") { PesoInizialeCard(m) { dialogoIniziale = true } }
                } else {
                    item(key = "riepilogo") { RiepilogoPeso(m) { dialogoIniziale = true } }
                    if (m.pesi.size >= 2) {
                        item(key = "grafico-mamma") {
                            val asse = remember(m.pesi, m.dpp) { Asse(m.pesi.map { it.data }, m.dpp) }
                            val punti = m.pesi.map { p ->
                                PuntoGrafico(asse.x(p.data), p.kg.toFloat(), numero(p.kg) + " kg · " + quandoBreve(p.data, m.dpp))
                            }
                            AppCard {
                                Text("Andamento del peso (kg)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Grafico(
                                    punti, asse.passi, listOf(0.5f, 1f, 2f, 5f, 10f, 20f),
                                    etichettaX = { asse.etichetta(it) },
                                    etichettaY = { numero(it.toDouble(), 1) },
                                )
                                Text(
                                    asse.titolo + " · tocca un punto per vedere il valore",
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    item(key = "titolo-pesate") { TitoloSezione("Tutte le pesate") }
                    val dalNuovo = m.pesi.reversed()
                    items(dalNuovo, key = { it.id }) { p ->
                        val i = m.pesi.indexOf(p)
                        val prima = if (i > 0) m.pesi[i - 1] else null
                        PesoRiga(p, prima, m.dpp) { modificaPeso = p }
                    }
                }
            } else {
                bambino(m)
            }
        }
    }

    if (nuovoPeso) {
        PesoDialog(null, m.dpp, onSalva = { m.salvaPeso(it); nuovoPeso = false }, onElimina = null, onChiudi = { nuovoPeso = false })
    }
    val mp = modificaPeso
    if (mp != null) {
        PesoDialog(
            mp, m.dpp,
            onSalva = { m.salvaPeso(it); modificaPeso = null },
            onElimina = { m.eliminaPeso(mp.id); modificaPeso = null },
            onChiudi = { modificaPeso = null },
        )
    }
    if (dialogoIniziale) {
        PesoInizialeDialog(m.pesoIniziale, onSalva = { m.impostaPesoIniziale(it); dialogoIniziale = false }) { dialogoIniziale = false }
    }
}

/** "sett. 20+3" se si sa, altrimenti la data breve. */
private fun quandoBreve(d: LocalDate, dpp: LocalDate?): String =
    Gravidanza.settimana(dpp, d)?.let { "sett. $it" } ?: dataBreve(d)

/** Le righe della scheda "Peso del bambino": il peso stimato preso dalle ecografie. */
private fun androidx.compose.foundation.lazy.LazyListScope.bambino(m: Model) {
    val conPeso = m.ecografie.filter { it.misura(Misura.PESO) != null }.sortedBy { it.data }
    if (conPeso.isEmpty()) {
        item(key = "vuoto-bambino") {
            VuotoSezione(
                painterResource(R.drawable.ic_ecografie),
                "Nessun peso stimato",
                "Il peso stimato del bambino arriva dalle ecografie: inseriscilo tra le misure del referto (voce «Peso stimato»).",
                azione = "Vai alle ecografie",
            ) { m.apriSezione(Sezione.ECOGRAFIE) }
        }
        return
    }
    item(key = "spiega-bambino") {
        Text(
            "Valori presi dalle ecografie. È una stima calcolata dalle misure: il peso reale può essere diverso.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (conPeso.size >= 2) {
        item(key = "grafico-bambino") {
            val asse = remember(conPeso, m.dpp) { Asse(conPeso.map { it.data }, m.dpp) }
            val punti = conPeso.map { e ->
                val g = e.misura(Misura.PESO) ?: 0.0
                PuntoGrafico(asse.x(e.data), g.toFloat(), numero(g, 0) + " g · " + quandoBreve(e.data, m.dpp))
            }
            AppCard {
                Text("Peso stimato del bambino (g)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Grafico(
                    punti, asse.passi, listOf(50f, 100f, 200f, 250f, 500f, 1000f, 2000f),
                    etichettaX = { asse.etichetta(it) },
                    etichettaY = { numero(it.toDouble(), 0) },
                )
                Text(
                    asse.titolo + " · tocca un punto per vedere il valore",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    item(key = "titolo-eco") { TitoloSezione("Dalle ecografie") }
    items(conPeso.reversed(), key = { "b-" + it.id }) { e ->
        val cs = MaterialTheme.colorScheme
        AppCard(onClick = { m.vai(Schermata.EcoDettaglio(e.id)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.titolo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        dataMedia(e.data).maiuscola() + (Gravidanza.settimana(m.dpp, e.data)?.let { " · sett. $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                }
                Text(Misura.PESO.formatta(e.misura(Misura.PESO) ?: 0.0), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun RiepilogoPeso(m: Model, onIniziale: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ultimo = m.pesi.last()
    val iniziale = m.pesoIniziale
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Ultimo peso", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(numero(ultimo.kg) + " kg", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(
                dataMedia(ultimo.data).maiuscola() + (Gravidanza.settimana(m.dpp, ultimo.data)?.let { " · settimana $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (iniziale != null) {
                Text(
                    "Dall'inizio della gravidanza: ${conSegno(ultimo.kg - iniziale)} kg",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                )
            } else if (m.pesi.size >= 2) {
                val primo = m.pesi.first()
                Text(
                    "Dalla prima pesata (${dataBreve(primo.data)}): ${conSegno(ultimo.kg - primo.kg)} kg",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                if (iniziale != null) "Peso prima della gravidanza: ${numero(iniziale)} kg · modifica ›"
                else "Aggiungi il peso prima della gravidanza ›",
                style = MaterialTheme.typography.bodyMedium, color = cs.primary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).clickableTesto(onIniziale),
            )
        }
    }
}

@Composable
private fun PesoInizialeCard(m: Model, onClick: () -> Unit) {
    val iniziale = m.pesoIniziale
    AppCard(onClick = onClick) {
        Text("Peso prima della gravidanza", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            if (iniziale != null) numero(iniziale) + " kg" else "Facoltativo: serve per vedere quanto è aumentato",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PesoRiga(p: Peso, prima: Peso?, dpp: LocalDate?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    dataMedia(p.data).maiuscola() + (Gravidanza.settimana(dpp, p.data)?.let { " · sett. $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (p.nota.isNotBlank()) {
                    Text(p.nota, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(numero(p.kg) + " kg", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                if (prima != null) {
                    Text(conSegno(p.kg - prima.kg) + " kg", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** Nuovo peso ([iniziale] null) o modifica di uno esistente. */
@Composable
fun PesoDialog(iniziale: Peso?, dpp: LocalDate?, onSalva: (Peso) -> Unit, onElimina: (() -> Unit)?, onChiudi: () -> Unit) {
    var data by rememberSaveable { mutableStateOf(iniziale?.data ?: LocalDate.now()) }
    var testo by rememberSaveable { mutableStateOf(iniziale?.let { numero(it.kg) } ?: "") }
    var nota by rememberSaveable { mutableStateOf(iniziale?.nota ?: "") }
    var sceltaData by remember { mutableStateOf(false) }
    val kg = leggiNumero(testo)
    val valido = kg != null && kg in 30.0..250.0

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(if (iniziale == null) "Nuovo peso" else "Modifica peso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoScelta(
                    "Data", dataLunga(data).maiuscola(),
                    sotto = Gravidanza.settimana(dpp, data)?.let { "Settimana $it" },
                ) { sceltaData = true }
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text("Peso") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    isError = testo.isNotBlank() && !valido,
                    supportingText = if (testo.isNotBlank() && !valido) {
                        { Text("Scrivi il peso in kg, per esempio 62,5") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text("Nota (facoltativa)") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (kg != null && valido) {
                        val base = iniziale ?: Peso(data = data, kg = kg)
                        onSalva(base.copy(data = data, kg = kg, nota = nota.trim()))
                    }
                },
                enabled = valido,
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

@Composable
private fun PesoInizialeDialog(attuale: Double?, onSalva: (Double?) -> Unit, onChiudi: () -> Unit) {
    var testo by rememberSaveable { mutableStateOf(attuale?.let { numero(it) } ?: "") }
    val kg = leggiNumero(testo)
    val valido = kg != null && kg in 30.0..250.0
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Peso prima della gravidanza") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Serve solo per calcolare quanto è aumentato il peso.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text("Peso") },
                    suffix = { Text("kg") },
                    singleLine = true,
                    isError = testo.isNotBlank() && !valido,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSalva(kg) }, enabled = valido) { Text("Salva") } },
        dismissButton = {
            Row {
                if (attuale != null) TextButton(onClick = { onSalva(null) }) { Text("Togli") }
                TextButton(onClick = onChiudi) { Text("Annulla") }
            }
        },
    )
}

/** Rende un testo toccabile come un link. */
fun Modifier.clickableTesto(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
