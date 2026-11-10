package com.pancy.app

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** Quanto dura un appuntamento, in minuti: serve per il calendario e per capire quando è "passato". */
const val DURATA_MIN = 60

/** I tipi di appuntamento, ognuno con il suo colore e qualche titolo pronto da toccare. */
enum class Tipo(val chiave: String, val nome: String, val colore: Color, val suggerimenti: List<String>) {
    VISITA(
        "visita", "Visita", Color(0xFFD0457A),
        listOf("Visita ostetrica", "Visita ginecologica", "Prima visita", "Visita di controllo"),
    ),
    ECOGRAFIA(
        "ecografia", "Ecografia", Color(0xFF7C6CF2),
        listOf("Ecografia del primo trimestre", "Ecografia morfologica", "Ecografia del terzo trimestre", "Ecografia di accrescimento"),
    ),
    ESAMI(
        "esami", "Esami", Color(0xFF2E9C8F),
        listOf("Esami del sangue", "Esame delle urine", "Test combinato", "Curva glicemica", "Tampone vaginale-rettale"),
    ),
    CORSO(
        "corso", "Corso", Color(0xFFE0922A),
        listOf("Corso preparto", "Incontro in consultorio"),
    ),
    ALTRO(
        "altro", "Altro", Color(0xFF6B7A99),
        listOf("Tracciato (CTG)", "Vaccino", "Visita anestesiologica", "Colloquio in ospedale"),
    );

    companion object {
        fun da(chiave: String?): Tipo = entries.firstOrNull { it.chiave == chiave } ?: ALTRO
    }
}

/**
 * Un appuntamento. [ora] è null se non c'è un orario (per esempio "esami del sangue, al mattino").
 * [evento] è l'id dell'evento nel calendario del telefono, se l'app ce l'ha copiato.
 * [esame] è la voce del calendario degli esami (sezione Consigli) da cui è nato, se c'è.
 */
data class Appuntamento(
    val id: String = UUID.randomUUID().toString(),
    val tipo: Tipo = Tipo.VISITA,
    val titolo: String = "",
    val data: LocalDate = LocalDate.now(),
    val ora: LocalTime? = null,
    val luogo: String = "",
    val medico: String = "",
    val note: String = "",
    val esito: String = "",
    val evento: Long? = null,
    val esame: String? = null,
) {
    /** Passato: dal giorno dopo se non ha orario, altrimenti quando è finito. */
    fun passato(adesso: LocalDateTime = LocalDateTime.now()): Boolean {
        val o = ora ?: return data.isBefore(adesso.toLocalDate())
        return data.atTime(o).plusMinutes(DURATA_MIN.toLong()).isBefore(adesso)
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("id", id)
            .put("tipo", tipo.chiave)
            .put("titolo", titolo)
            .put("data", data.toString())
            .put("luogo", luogo)
            .put("medico", medico)
            .put("note", note)
            .put("esito", esito)
        if (ora != null) o.put("ora", ora.toString())
        if (evento != null) o.put("evento", evento)
        if (esame != null) o.put("esame", esame)
        return o
    }

    companion object {
        fun daJson(o: JSONObject): Appuntamento = Appuntamento(
            id = o.getString("id"),
            tipo = Tipo.da(o.optString("tipo")),
            titolo = o.optString("titolo"),
            data = LocalDate.parse(o.getString("data")),
            ora = o.optString("ora").takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) },
            luogo = o.optString("luogo"),
            medico = o.optString("medico"),
            note = o.optString("note"),
            esito = o.optString("esito"),
            evento = if (o.has("evento")) o.getLong("evento") else null,
            esame = o.optString("esame").takeIf { it.isNotEmpty() },
        )

        /** In ordine di data e ora (quelli senza orario vengono prima, nella loro giornata). */
        fun ordina(lista: List<Appuntamento>): List<Appuntamento> =
            lista.sortedWith(compareBy<Appuntamento> { it.data }.thenBy { it.ora ?: LocalTime.MIN })
    }
}
