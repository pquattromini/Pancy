package com.pancy.app

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import java.util.UUID

const val MIME_PDF = "application/pdf"

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
 * Una foto, un video o un documento PDF. Il file sta nella cartella "media" dell'app ([file] è il nome);
 * per quelli arrivati da Google Drive il file si scarica solo quando serve.
 * [ecografia] e [referto] sono l'ecografia o il referto a cui appartiene, se c'è.
 * [driveId] è il file corrispondente su Google Drive; [cartellaDrive] dice in quale cartella era all'ultima
 * sincronizzazione ("foto", "eco" o "ref"), per capire se è stato spostato a mano su Drive.
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
    val pdf: Boolean = false,
    val referto: String? = null,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("id", id)
            .put("file", file)
            .put("video", video)
            .put("data", data.toString())
            .put("didascalia", didascalia)
            .put("aggiunto", aggiunto)
        if (pdf) o.put("pdf", true)
        if (ecografia != null) o.put("ecografia", ecografia)
        if (referto != null) o.put("referto", referto)
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
            pdf = o.optBoolean("pdf"),
            referto = o.optString("referto").takeIf { it.isNotEmpty() },
        )

        /** Dalla più recente. */
        fun ordina(l: List<Media>): List<Media> =
            l.sortedWith(compareByDescending<Media> { it.data }.thenByDescending { it.aggiunto })
    }
}

/** I tipi di referto, ognuno con il suo colore e qualche titolo pronto da toccare. */
enum class TipoReferto(val chiave: String, val nome: String, val colore: Color, val suggerimenti: List<String>) {
    SANGUE(
        "sangue", "Esami del sangue", Color(0xFFD0453F),
        listOf("Esami del sangue", "Emocromo", "Toxo test", "Rubeo test", "Curva glicemica", "Gruppo sanguigno e Coombs"),
    ),
    URINE("urine", "Esame delle urine", Color(0xFFA86B00), listOf("Esame delle urine", "Urinocoltura")),
    PRENATALE(
        "prenatale", "Test prenatale", Color(0xFF5B6EE0),
        listOf("Test combinato (bi-test)", "Test del DNA fetale (NIPT)", "Villocentesi", "Amniocentesi"),
    ),
    TAMPONE("tampone", "Tampone", Color(0xFF2E9C8F), listOf("Tampone vaginale-rettale", "Tampone")),
    VISITA("visita", "Referto di visita", Color(0xFFD0457A), listOf("Visita ostetrica", "Visita ginecologica", "Tracciato (CTG)")),
    RICHIESTA("richiesta", "Richiesta o impegnativa", Color(0xFFE0922A), listOf("Impegnativa", "Ricetta", "Certificato")),
    ALTRO("altro", "Altro", Color(0xFF6B7A99), emptyList());

    companion object {
        fun da(chiave: String?): TipoReferto? = entries.firstOrNull { it.chiave == chiave }

        /** Il tipo, indovinato dal titolo o dal nome del file. */
        fun indovina(testo: String): TipoReferto {
            val t = testo.lowercase(Locale.ITALY)
            fun ha(vararg parole: String) = parole.any { t.contains(it) }
            return when {
                ha("impegnativ", "ricetta", "prescrizion", "richiesta", "certificat") -> RICHIESTA
                ha("nipt", "dna fetale", "bi-test", "bitest", "bi test", "combinato", "villo", "amnio", "harmony", "prenatal") -> PRENATALE
                ha("tampone", "gbs", "streptococco") -> TAMPONE
                ha("urin") -> URINE
                ha(
                    "sangue", "emocromo", "toxo", "rubeo", "glicem", "curva", "tsh", "ferritina", "sideremia", "hiv", "hbsag",
                    "hcv", "vdrl", "tpha", "coombs", "gruppo", "citomegalovirus", "cmv", "prelievo", "analisi", "laboratorio",
                ) -> SANGUE
                ha("visita", "ctg", "tracciato", "ostetric", "ginecolog") -> VISITA
                else -> ALTRO
            }
        }
    }
}

/**
 * Un referto o un documento medico (esami, test, impegnative). I file (PDF o foto) sono [Media] con [Media.referto].
 * [dove] è il laboratorio, l'ospedale o il medico; [appuntamento] è l'appuntamento in agenda da cui arriva, se c'è.
 */
data class Referto(
    val id: String = UUID.randomUUID().toString(),
    val tipo: TipoReferto = TipoReferto.SANGUE,
    val titolo: String = "",
    val data: LocalDate = LocalDate.now(),
    val dove: String = "",
    val appuntamento: String? = null,
    val esito: String = "",
    val note: String = "",
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("id", id)
            .put("tipo", tipo.chiave)
            .put("titolo", titolo)
            .put("data", data.toString())
            .put("dove", dove)
            .put("esito", esito)
            .put("note", note)
        if (appuntamento != null) o.put("appuntamento", appuntamento)
        return o
    }

    companion object {
        fun daJson(o: JSONObject): Referto = Referto(
            id = o.getString("id"),
            tipo = TipoReferto.da(o.optString("tipo")) ?: TipoReferto.ALTRO,
            titolo = o.optString("titolo"),
            data = LocalDate.parse(o.getString("data")),
            dove = o.optString("dove"),
            appuntamento = o.optString("appuntamento").takeIf { it.isNotEmpty() },
            esito = o.optString("esito"),
            note = o.optString("note"),
        )

        /** Dal più recente. */
        fun ordina(l: List<Referto>): List<Referto> = l.sortedByDescending { it.data }
    }
}

/** Date e titoli ricavati dal nome di un file ("2026-10-03_esami-sangue.pdf"). */
object NomeFile {
    private val DATA_ISO = Regex("(20\\d{2})[-_.]?(\\d{2})[-_.]?(\\d{2})")
    private val DATA_IT = Regex("(\\d{2})[-_.](\\d{2})[-_.](20\\d{2})")

    /** La data scritta nel nome (anno-mese-giorno o giorno-mese-anno), se c'è e non è nel futuro. */
    fun data(nome: String): LocalDate? {
        val d = DATA_ISO.find(nome)?.let { m -> prova(m.groupValues[1], m.groupValues[2], m.groupValues[3]) }
            ?: DATA_IT.find(nome)?.let { m -> prova(m.groupValues[3], m.groupValues[2], m.groupValues[1]) }
        return d?.takeIf { !it.isAfter(LocalDate.now()) }
    }

    private fun prova(a: String, m: String, g: String): LocalDate? =
        try { LocalDate.of(a.toInt(), m.toInt(), g.toInt()) } catch (e: Exception) { null }

    /** Un titolo leggibile: senza estensione, date e trattini. Vuoto se non resta niente. */
    fun titolo(nome: String): String {
        var t = nome.substringBeforeLast('.', nome)
        t = DATA_ISO.replace(t, " ")
        t = DATA_IT.replace(t, " ")
        t = t.replace(Regex("[_\\-.]+"), " ").replace(Regex("\\s+"), " ").trim()
        return t.replaceFirstChar { it.titlecase(Locale.ITALY) }
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
