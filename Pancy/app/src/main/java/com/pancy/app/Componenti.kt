@file:OptIn(ExperimentalMaterial3Api::class)

package com.pancy.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Riquadro bianco con bordo sottile; se ha [onClick] si può toccare. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val colori = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val bordo = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colori, border = bordo) {
            Column(Modifier.padding(14.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colori, border = bordo) {
            Column(Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
fun TitoloSezione(t: String) {
    Text(
        t, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

/** Due o più schede affiancate, come un interruttore a più posizioni. */
@Composable
fun Segmenti(opzioni: List<String>, scelta: Int, onScelta: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        opzioni.forEachIndexed { i, t ->
            val sel = i == scelta
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) cs.surface else Color.Transparent)
                    .clickable { onScelta(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) cs.onSurface else cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** Un campo che si tocca per scegliere un valore (data, ora, calendario...). */
@Composable
fun CampoScelta(
    etichetta: String,
    valore: String,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    sotto: String? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icona != null) {
                Icon(icona, contentDescription = null, tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(etichetta, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text(valore, style = MaterialTheme.typography.bodyLarge)
                if (sotto != null) {
                    Text(sotto, style = MaterialTheme.typography.bodySmall, color = cs.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Una riga con icona, testo, eventuale riga sotto e un'eventuale azione toccabile. */
@Composable
fun RigaInfo(icona: ImageVector, testo: String, sotto: String? = null, azione: String? = null, onAzione: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icona, contentDescription = null, tint = cs.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(testo, style = MaterialTheme.typography.bodyLarge)
            if (sotto != null) {
                Text(sotto, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (azione != null && onAzione != null) {
                Text(
                    azione, style = MaterialTheme.typography.bodyMedium, color = cs.primary, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp).clickable(onClick = onAzione),
                )
            }
        }
    }
}

/** Etichetta colorata con il tipo di appuntamento. */
@Composable
fun TipoEtichetta(t: Tipo) {
    Text(
        t.nome, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = t.colore,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(t.colore.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Riga con pallino da scegliere (per elenchi a scelta singola nei dialoghi). */
@Composable
fun Opzione(testo: String, sotto: String?, scelta: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = scelta, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
        Column(Modifier.weight(1f)) {
            Text(testo)
            if (sotto != null) {
                Text(sotto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Scelta di data e ora ----

private fun millisUtc(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
fun SceltaData(iniziale: LocalDate?, titolo: String? = null, onScelta: (LocalDate) -> Unit, onChiudi: () -> Unit) {
    val stato = rememberDatePickerState(initialSelectedDateMillis = iniziale?.let { millisUtc(it) })
    DatePickerDialog(
        onDismissRequest = onChiudi,
        confirmButton = {
            TextButton(onClick = {
                val ms = stato.selectedDateMillis
                if (ms != null) onScelta(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                onChiudi()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
    ) {
        if (titolo != null) {
            DatePicker(
                state = stato,
                title = { Text(titolo, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
            )
        } else {
            DatePicker(state = stato)
        }
    }
}

@Composable
fun SceltaOra(iniziale: LocalTime, onScelta: (LocalTime) -> Unit, onChiudi: () -> Unit) {
    val stato = rememberTimePickerState(initialHour = iniziale.hour, initialMinute = iniziale.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onChiudi,
        confirmButton = {
            TextButton(onClick = {
                onScelta(LocalTime.of(stato.hour, stato.minute))
                onChiudi()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onChiudi) { Text("Annulla") } },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = stato) }
        },
    )
}
