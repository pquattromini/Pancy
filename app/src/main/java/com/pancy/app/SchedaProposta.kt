package com.pancy.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.File

/**
 * La scheda di una modifica proposta dall'IA: cosa cambia (con il valore di prima, se c'è),
 * e i pulsanti «Salva» e «Annulla». Dopo il salvataggio, «Apri» porta all'elemento salvato.
 */
@Composable
fun SchedaProposta(m: Model, conv: String, p: Proposta) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val attesa = p.stato == Proposta.ATTESA
    val righe = Modifiche.righe(p, m)
    val foto = remember(p.campi) { Modifiche.fotoDi(try { JSONObject(p.campi) } catch (e: Exception) { JSONObject() }) }
    val testo = if (attesa) cs.onSecondaryContainer else cs.onSurface
    Card(
        modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (attesa) cs.secondaryContainer else cs.surface, contentColor = testo),
        border = if (attesa) null else BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(Modifiche.titolo(p), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            for (r in righe) {
                Row(Modifier.padding(top = 6.dp)) {
                    Text(
                        r.etichetta, style = MaterialTheme.typography.bodySmall, color = testo.copy(alpha = 0.7f),
                        modifier = Modifier.width(104.dp).padding(end = 8.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(r.valore, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        val prima = r.prima
                        if (prima != null) {
                            Text("prima: $prima", style = MaterialTheme.typography.bodySmall, color = testo.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            if (foto.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (n in foto) MiniaturaFile(File(Assistente.cartellaChat(ctx), n), 48.dp)
                }
            }
            when (p.stato) {
                Proposta.ATTESA -> Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { m.annullaProposta(conv, p.id) }) { Text("Annulla") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { m.confermaProposta(conv, p.id) }) { Text("Salva") }
                }
                Proposta.SALVATA -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Salvato", color = cs.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (puoAprire(p, m)) TextButton(onClick = { apri(p, m) }) { Text("Apri") }
                }
                else -> Text(
                    "Annullato", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Vero se l'elemento salvato esiste ancora e si può aprire. */
private fun puoAprire(p: Proposta, m: Model): Boolean {
    val id = p.risultato ?: return false
    return when (p.tipo) {
        "appuntamento" -> m.trova(id) != null
        "ecografia" -> m.ecografia(id) != null
        "referto" -> m.referto(id) != null
        "peso", "consiglio", "foto", "impostazioni" -> true
        else -> false
    }
}

/** Porta all'elemento salvato (o alla sua sezione). */
private fun apri(p: Proposta, m: Model) {
    val id = p.risultato ?: return
    when (p.tipo) {
        "appuntamento" -> m.vai(Schermata.Dettaglio(id))
        "ecografia" -> m.vai(Schermata.EcoDettaglio(id))
        "referto" -> m.vai(Schermata.RefDettaglio(id))
        "peso" -> m.apriSezione(Sezione.STORICO)
        "consiglio" -> m.apriSezione(Sezione.CONSIGLI)
        "foto" -> m.apriSezione(Sezione.FOTO)
        "impostazioni" -> m.vai(Schermata.Impostazioni)
    }
}
