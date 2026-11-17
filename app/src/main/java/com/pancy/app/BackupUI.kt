package com.pancy.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Sezione delle impostazioni per il backup: su Google Drive (automatico) e in una copia su file. */
@Composable
fun SezioneBackup(m: Model) {
    val cs = MaterialTheme.colorScheme
    val esporta = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri ->
        if (uri != null) m.esportaBackup(uri)
    }
    val recupera = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) m.leggiBackupDaFile(uri)
    }

    TitoloSezione("Backup dei dati")
    AppCard {
        if (m.driveCollegato) {
            Text(
                if (m.backupUltimo > 0) "Ultimo backup su Google Drive: " + m.quandoBackup(m.backupUltimo).removePrefix("del ")
                else "Backup su Google Drive non ancora fatto",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Pesi, appuntamenti, ecografie, referti, consigli, conversazioni con l'IA e impostazioni si salvano da soli su Drive " +
                    "pochi secondi dopo ogni modifica, nel file «${Backup.NOME_SU_DRIVE}». " +
                    "Se reinstalli l'app, basta ricollegare Google Drive: i dati tornano da soli, insieme alle cartelle scelte.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = { m.backupSuDrive(silenzioso = false) },
                enabled = !m.backupInCorso,
                modifier = Modifier.padding(top = 10.dp),
            ) { Text(if (m.backupInCorso) "Salvo…" else "Fai il backup ora") }
        } else {
            Text(
                "Collega Google Drive (qui sopra) per avere il backup automatico: dopo una reinstallazione i dati tornano da soli.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Text(
            "Copia in un file", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Puoi anche salvare una copia dei dati in un file (per esempio in Download) e recuperarla da lì.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
        )
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { esporta.launch("Pancy backup ${LocalDate.now()}.json") }) { Text("Salva una copia") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { recupera.launch(arrayOf(Backup.MIME, "text/plain", "application/octet-stream")) }) {
                Text("Recupera da una copia")
            }
        }
        Text(
            "La chiave di OpenAI non entra nel backup: dopo una reinstallazione va incollata di nuovo. " +
                "Foto, video e PDF tornano dalle loro cartelle di Drive.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * La finestra che chiede cosa fare di un backup: uno più recente trovato su Drive (con dati già presenti sul telefono),
 * oppure una copia scelta da un file.
 */
@Composable
fun DialogoBackup(m: Model) {
    val info = m.backupDaDecidere ?: return
    AlertDialog(
        // su Drive bisogna scegliere; una copia da file si può anche lasciar perdere
        onDismissRequest = { if (info.daFile) m.teniDatiDelTelefono() },
        title = { Text(if (info.daFile) "Recuperare questa copia?" else "C'è un backup più recente") },
        text = {
            Text(
                (if (info.daFile) "La copia è " else "Su Google Drive c'è un backup ") + m.quandoBackup(info.salvato) +
                    " con " + info.descrizione + ". Recuperandolo, i suoi dati tornano nell'app; " +
                    "quello che c'è solo su questo telefono resta." +
                    (if (info.daFile) "" else " Se tieni i dati del telefono, il backup su Drive verrà sostituito."),
            )
        },
        confirmButton = { TextButton(onClick = { m.ripristinaBackup(info) }) { Text("Recupera") } },
        dismissButton = {
            TextButton(onClick = { m.teniDatiDelTelefono() }) { Text(if (info.daFile) "Annulla" else "Tieni quelli del telefono") }
        },
    )
}
