package com.pancy.app

import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Un evento letto dal calendario del telefono, da importare come appuntamento.
 * [chiave] lo identifica: "calendario:evento", con "@data" in più per gli eventi che si ripetono.
 */
data class EventoEsterno(
    val chiave: String,
    val calId: Long,
    val eventId: Long,
    val titolo: String,
    val luogo: String,
    val descrizione: String,
    val data: LocalDate,
    val ora: LocalTime?,
) {
    /** Cambia se nel calendario cambiano titolo, data, ora o luogo. */
    val firma: String get() = "$titolo|$data|${ora ?: ""}|$luogo".hashCode().toString()
}

/** Le misure che si trovano di solito sul referto di un'ecografia. */
enum class Misura(val chiave: String, val sigla: String, val nome: String, val unita: String, val decimali: Int) {
    PESO("peso", "EFW", "Peso stimato del bambino", "g", 0),
    BATTITO("battito", "FC", "Battito cardiaco", "bpm", 0),
    CRL("crl", "CRL", "Lunghezza vertice-sacro", "mm", 1),
    NT("nt", "NT", "Translucenza nucale", "mm", 2),
    BPD("bpd", "BPD", "Diametro biparietale", "mm", 1),
    HC("hc", "HC", "Circonferenza cranica", "mm", 1),
    AC("ac", "AC", "Circonferenza addominale", "mm", 1),
    FL("fl", "FL", "Lunghezza del femore", "mm", 1);

    fun formatta(v: Double): String = numero(v, decimali) + " " + unita

    companion object {
        fun da(chiave: String): Misura? = entries.firstOrNull { it.chiave == chiave }
    }
}

/** Un'ecografia con le sue misure. [appuntamento] è l'appuntamento in agenda da cui arriva, se c'è. */
data class Ecografia(
    val id: String = UUID.randomUUID().toString(),
    val titolo: String = "",
    val data: LocalDate = LocalDate.now(),
    val appuntamento: String? = null,
    val misure: Map<String, Double> = emptyMap(),
    val note: String = "",
) {
    fun misura(m: Misura): Double? = misure[m.chiave]

    fun toJson(): JSONObject {
        val mis = JSONObject()
        for ((k, v) in misure) mis.put(k, v)
        val o = JSONObject()
            .put("id", id)
            .put("titolo", titolo)
            .put("data", data.toString())
            .put("misure", mis)
            .put("note", note)
        if (appuntamento != null) o.put("appuntamento", appuntamento)
        return o
    }

    companion object {
        fun daJson(o: JSONObject): Ecografia {
            val mis = LinkedHashMap<String, Double>()
            val jm = o.optJSONObject("misure")
            if (jm != null) {
                for (k in jm.keys()) {
                    val v = jm.optDouble(k)
                    if (!v.isNaN()) mis[k] = v
                }
            }
            return Ecografia(
                id = o.getString("id"),
                titolo = o.optString("titolo"),
                data = LocalDate.parse(o.getString("data")),
                appuntamento = o.optString("appuntamento").takeIf { it.isNotEmpty() },
                misure = mis,
                note = o.optString("note"),
            )
        }

        /** Dalla più recente. */
        fun ordina(l: List<Ecografia>): List<Ecografia> = l.sortedByDescending { it.data }
    }
}

/**
 * Una foto o un video. Il file sta nella cartella "media" dell'app ([file] è il nome);
 * per quelli arrivati da Google Drive il file si scarica solo quando serve.
 * [ecografia] è l'ecografia a cui appartiene, se c'è.
 * [driveId] è il file corrispondente su Google Drive; [cartellaDrive] dice in quale cartella era all'ultima
 * sincronizzazione ("foto" o "eco"), per capire se è stato spostato a mano su Drive.
 */
data class Media(
    val id: String = UUID.randomUUID().toString(),
    val file: String,
    val video: Boolean,
    val data: LocalDate,
    val didascalia: String = "",
    val ecografia: String? = null,
    val aggiunto: Long = System.currentTimeMillis(),
    val driveId: String? = null,
    val cartellaDrive: String? = null,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("id", id)
            .put("file", file)
            .put("video", video)
            .put("data", data.toString())
            .put("didascalia", didascalia)
            .put("aggiunto", aggiunto)
        if (ecografia != null) o.put("ecografia", ecografia)
        if (driveId != null) o.put("driveId", driveId)
        if (cartellaDrive != null) o.put("cartellaDrive", cartellaDrive)
        return o
    }

    companion object {
        fun daJson(o: JSONObject): Media = Media(
            id = o.getString("id"),
            file = o.getString("file"),
            video = o.optBoolean("video"),
            data = LocalDate.parse(o.getString("data")),
            didascalia = o.optString("didascalia"),
            ecografia = o.optString("ecografia").takeIf { it.isNotEmpty() },
            aggiunto = o.optLong("aggiunto"),
            driveId = o.optString("driveId").takeIf { it.isNotEmpty() },
            cartellaDrive = o.optString("cartellaDrive").takeIf { it.isNotEmpty() },
        )

        /** Dalla più recente. */
        fun ordina(l: List<Media>): List<Media> =
            l.sortedWith(compareByDescending<Media> { it.data }.thenByDescending { it.aggiunto })
    }
}

/** Una pesata della mamma. */
data class Peso(
    val id: String = UUID.randomUUID().toString(),
    val data: LocalDate,
    val kg: Double,
    val nota: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("data", data.toString())
        .put("kg", kg)
        .put("nota", nota)

    companion object {
        fun daJson(o: JSONObject): Peso = Peso(
            id = o.getString("id"),
            data = LocalDate.parse(o.getString("data")),
            kg = o.getDouble("kg"),
            nota = o.optString("nota"),
        )

        /** Dalla più vecchia. */
        fun ordina(l: List<Peso>): List<Peso> = l.sortedBy { it.data }
    }
}

/** Un consiglio ricevuto da medico, ostetrica o altri esperti. */
data class Consiglio(
    val id: String = UUID.randomUUID().toString(),
    val data: LocalDate = LocalDate.now(),
    val autore: String = "",
    val testo: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("data", data.toString())
        .put("autore", autore)
        .put("testo", testo)

    companion object {
        fun daJson(o: JSONObject): Consiglio = Consiglio(
            id = o.getString("id"),
            data = LocalDate.parse(o.getString("data")),
            autore = o.optString("autore"),
            testo = o.optString("testo"),
        )

        /** Dal più recente. */
        fun ordina(l: List<Consiglio>): List<Consiglio> = l.sortedByDescending { it.data }
    }
}
