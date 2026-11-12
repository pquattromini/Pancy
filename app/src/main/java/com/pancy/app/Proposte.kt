package com.pancy.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Una modifica ai dati proposta dall'IA nella chat. Si applica solo quando l'utente tocca «Salva».
 * [campi] contiene, in JSON, solo i campi da impostare (già controllati); [bersaglio] è l'elemento da modificare
 * (null = nuovo); [risultato] è l'elemento creato o modificato, dopo il salvataggio.
 */
data class Proposta(
    val id: String = UUID.randomUUID().toString(),
    /** "peso", "appuntamento", "ecografia", "referto", "consiglio", "foto", "impostazioni" */
    val tipo: String,
    val bersaglio: String? = null,
    val campi: String,
    /** Una riga che la descrive: la rilegge l'IA nei messaggi successivi. */
    val riassunto: String,
    val stato: String = ATTESA,
    val risultato: String? = null,
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("id", id)
            .put("tipo", tipo)
            .put("campi", campi)
            .put("riassunto", riassunto)
            .put("stato", stato)
        if (bersaglio != null) o.put("bersaglio", bersaglio)
        if (risultato != null) o.put("risultato", risultato)
        return o
    }

    companion object {
        const val ATTESA = "attesa"
        const val SALVATA = "salvata"
        const val ANNULLATA = "annullata"

        fun daJson(o: JSONObject): Proposta = Proposta(
            id = o.getString("id"),
            tipo = o.optString("tipo"),
            bersaglio = o.optString("bersaglio").takeIf { it.isNotEmpty() },
            campi = o.optString("campi").ifEmpty { "{}" },
            riassunto = o.optString("riassunto"),
            stato = o.optString("stato").ifEmpty { ATTESA },
            risultato = o.optString("risultato").takeIf { it.isNotEmpty() },
        )
    }
}

/** I dati dell'app nel momento della domanda, per controllare le proposte fuori dal thread principale. */
class DatiAttuali(
    val appuntamenti: List<Appuntamento>,
    val ecografie: List<Ecografia>,
    val referti: List<Referto>,
    val pesi: List<Peso>,
)

/** Una riga della scheda di conferma: [prima] è il valore attuale, se si sta modificando qualcosa. */
class RigaProposta(val etichetta: String, val valore: String, val prima: String? = null)

/** Le modifiche che l'IA può proporre: strumenti per OpenAI, controlli, descrizione e applicazione dei campi. */
object Modifiche {
    const val MAX_PER_RISPOSTA = 10

    private fun breve(id: String) = id.replace("-", "").take(6)
    fun codice(a: Appuntamento) = "A-" + breve(a.id)
    fun codice(e: Ecografia) = "E-" + breve(e.id)
    fun codice(r: Referto) = "R-" + breve(r.id)

    // ---------------- Strumenti per OpenAI ----------------

    private fun testo(descr: String) = JSONObject().put("type", "string").put("description", descr)
    private fun numeroJ(descr: String) = JSONObject().put("type", "number").put("description", descr)
    private fun scelta(descr: String, valori: List<String>) =
        JSONObject().put("type", "string").put("description", descr).put("enum", JSONArray(valori))
    private fun siNo(descr: String) = JSONObject().put("type", "boolean").put("description", descr)

    private fun strumento(nome: String, descr: String, proprieta: JSONObject, obbligatori: List<String>): JSONObject =
        JSONObject()
            .put("type", "function")
            .put("name", nome)
            .put("description", descr)
            .put(
                "parameters",
                JSONObject().put("type", "object").put("properties", proprieta)
                    .put("required", JSONArray(obbligatori)).put("additionalProperties", false),
            )

    private const val DATA = "Data nel formato AAAA-MM-GG"
    private const val FOTO =
        "true per collegare le foto allegate all'ultimo messaggio dell'utente (per esempio le foto del referto o della stampa)"

    fun strumenti(): List<JSONObject> = listOf(
        strumento(
            "proponi_peso",
            "Propone di registrare una pesata della mamma nello Storico. Se in quel giorno c'è già una pesata, la sostituisce.",
            JSONObject().put("data", testo(DATA)).put("kg", numeroJ("Peso in kg, per esempio 64.5")).put("nota", testo("Nota facoltativa")),
            listOf("data", "kg"),
        ),
        strumento(
            "proponi_appuntamento",
            "Propone un appuntamento nuovo in agenda, oppure di modificarne uno esistente indicando il suo codice A-…. " +
                "Per un appuntamento nuovo servono tipo, titolo e data. Per modificarlo passa solo i campi da cambiare, con il valore completo nuovo.",
            JSONObject()
                .put("codice", testo("Codice A-… dell'appuntamento da modificare; ometti per uno nuovo"))
                .put("tipo", scelta("Tipo di appuntamento", Tipo.entries.map { it.chiave }))
                .put("titolo", testo("Titolo, per esempio «Visita ostetrica»"))
                .put("data", testo(DATA))
                .put("ora", testo("Ora nel formato HH:MM; stringa vuota per toglierla"))
                .put("luogo", testo("Luogo"))
                .put("medico", testo("Medico o ostetrica"))
                .put("note", testo("Note"))
                .put("esito", testo("Esito, dopo l'appuntamento")),
            emptyList(),
        ),
        strumento(
            "proponi_ecografia",
            "Propone un'ecografia nuova o di modificarne una esistente (codice E-…), per esempio con le misure lette dalla stampa. " +
                "Se in quella data c'è già un'ecografia, viene modificata quella.",
            JSONObject()
                .put("codice", testo("Codice E-… dell'ecografia da modificare; ometti per una nuova"))
                .put("titolo", testo("Titolo, per esempio «Ecografia morfologica»"))
                .put("data", testo(DATA))
                .put(
                    "misure",
                    JSONObject().put("type", "object").put(
                        "description",
                        "Misure da impostare (solo quelle presenti): " +
                            Misura.entries.joinToString("; ") { "${it.chiave} = ${it.nome} in ${it.unita}" },
                    ).put(
                        "properties",
                        JSONObject().also { p -> for (mi in Misura.entries) p.put(mi.chiave, numeroJ("${mi.nome} (${mi.sigla}) in ${mi.unita}")) },
                    ).put("additionalProperties", false),
                )
                .put("note", testo("Note o esito"))
                .put("foto_allegate", siNo(FOTO)),
            emptyList(),
        ),
        strumento(
            "proponi_referto",
            "Propone un referto nuovo nella sezione Referti, o di modificarne uno esistente (codice R-…), per esempio con l'esito letto da un PDF o da una foto. " +
                "Per un referto nuovo servono titolo e data.",
            JSONObject()
                .put("codice", testo("Codice R-… del referto da modificare; ometti per uno nuovo"))
                .put("tipo", scelta("Tipo di referto", TipoReferto.entries.map { it.chiave }))
                .put("titolo", testo("Titolo, per esempio «Esami del sangue»"))
                .put("data", testo(DATA))
                .put("dove", testo("Laboratorio, ospedale o medico"))
                .put("esito", testo("Esito in breve, con i valori importanti"))
                .put("note", testo("Note"))
                .put("appuntamento", testo("Codice A-… dell'appuntamento da cui nasce, se c'è"))
                .put("foto_allegate", siNo(FOTO)),
            emptyList(),
        ),
        strumento(
            "proponi_consiglio",
            "Propone di salvare nella sezione Consigli un consiglio ricevuto da un medico, un'ostetrica o un altro esperto.",
            JSONObject().put("testo", testo("Il consiglio")).put("autore", testo("Chi l'ha dato")).put("data", testo(DATA)),
            listOf("testo"),
        ),
        strumento(
            "proponi_foto",
            "Propone di aggiungere alla sezione Foto le foto allegate all'ultimo messaggio dell'utente.",
            JSONObject().put("data", testo("$DATA (se omessa, oggi)")).put("didascalia", testo("Didascalia facoltativa")),
            emptyList(),
        ),
        strumento(
            "proponi_impostazioni",
            "Propone di impostare la data presunta del parto e/o il peso della mamma prima della gravidanza.",
            JSONObject().put("data_parto", testo(DATA)).put("peso_prima", numeroJ("Peso prima della gravidanza in kg")),
            emptyList(),
        ),
    )

    fun eUnaProposta(nome: String) = nome.startsWith("proponi_")

    // ---------------- Controllo degli argomenti ----------------

    private class Errore(msg: String) : Exception(msg)

    private fun JSONObject.ha(k: String) = has(k) && !isNull(k)

    private fun JSONObject.stringa(k: String): String? = if (ha(k)) optString(k).trim() else null

    private fun JSONObject.dataDi(k: String): LocalDate? {
        val s = stringa(k)?.takeIf { it.isNotEmpty() } ?: return null
        return try { LocalDate.parse(s.take(10)) } catch (e: Exception) { throw Errore("la data «$s» non è nel formato AAAA-MM-GG") }
    }

    private fun JSONObject.numeroDi(k: String): Double? {
        if (!ha(k)) return null
        val v = opt(k)
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        } ?: throw Errore("«$k» deve essere un numero")
        if (d.isNaN() || d.isInfinite()) throw Errore("«$k» deve essere un numero")
        return d
    }

    private fun trova(codice: String, tutti: List<Pair<String, String>>, cosa: String): String {
        val c = codice.trim().uppercase()
        return tutti.firstOrNull { it.first.equals(c, ignoreCase = true) }?.second
            ?: throw Errore("non trovo $cosa con codice $codice")
    }

    private val ISO = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

    /** Esito del controllo: la proposta pronta, oppure il motivo per cui non va. */
    class Preparata(val proposta: Proposta?, val errore: String?)

    /**
     * Controlla gli argomenti di uno strumento proponi_… e prepara la proposta.
     * [allegati] sono le foto allegate all'ultimo messaggio dell'utente (nomi di file della chat).
     */
    fun prepara(nome: String, argomenti: String, d: DatiAttuali, allegati: List<String>): Preparata = try {
        val o = try { JSONObject(argomenti.ifBlank { "{}" }) } catch (e: Exception) { throw Errore("argomenti non leggibili") }
        val p = when (nome) {
            "proponi_peso" -> peso(o, d)
            "proponi_appuntamento" -> appuntamento(o, d)
            "proponi_ecografia" -> ecografia(o, d, allegati)
            "proponi_referto" -> referto(o, d, allegati)
            "proponi_consiglio" -> consiglio(o)
            "proponi_foto" -> fotoGalleria(o, allegati)
            "proponi_impostazioni" -> impostazioni(o)
            else -> throw Errore("strumento sconosciuto")
        }
        Preparata(p, null)
    } catch (e: Errore) {
        Preparata(null, e.message)
    }

    private fun fotoAllegate(allegati: List<String>): JSONArray {
        if (allegati.isEmpty()) throw Errore("nell'ultimo messaggio dell'utente non ci sono foto allegate")
        val a = JSONArray()
        for (n in allegati) a.put(n)
        return a
    }

    private fun peso(o: JSONObject, d: DatiAttuali): Proposta {
        val data = o.dataDi("data") ?: throw Errore("manca la data")
        if (data.isAfter(LocalDate.now())) throw Errore("la data è nel futuro")
        val kg = o.numeroDi("kg") ?: throw Errore("manca il peso")
        if (kg < 30 || kg > 250) throw Errore("un peso di ${numero(kg)} kg non è plausibile: controlla di averlo letto bene")
        val nota = o.stringa("nota").orEmpty()
        val esistente = d.pesi.firstOrNull { it.data == data }
        val campi = JSONObject().put("data", data.toString()).put("kg", kg).put("nota", nota)
        val riassunto = "peso ${numero(kg)} kg del ${ISO.format(data)}" +
            (if (esistente != null) " (al posto di ${numero(esistente.kg)} kg)" else "")
        return Proposta(tipo = "peso", bersaglio = esistente?.id, campi = campi.toString(), riassunto = riassunto)
    }

    private fun appuntamento(o: JSONObject, d: DatiAttuali): Proposta {
        val base = o.stringa("codice")?.takeIf { it.isNotEmpty() }?.let { c ->
            val id = trova(c, d.appuntamenti.map { codice(it) to it.id }, "un appuntamento")
            d.appuntamenti.first { it.id == id }
        }
        val campi = JSONObject()
        o.stringa("tipo")?.takeIf { it.isNotEmpty() }?.let { t ->
            if (Tipo.entries.none { it.chiave == t }) throw Errore("tipo «$t» sconosciuto")
            campi.put("tipo", t)
        }
        o.stringa("titolo")?.takeIf { it.isNotEmpty() }?.let { campi.put("titolo", it) }
        o.dataDi("data")?.let { campi.put("data", it.toString()) }
        o.stringa("ora")?.let { s ->
            if (s.isEmpty()) {
                campi.put("ora", "")
            } else {
                val t = try { LocalTime.parse(if (s.length == 4) "0$s" else s.take(5)) } catch (e: Exception) {
                    throw Errore("l'ora «$s» non è nel formato HH:MM")
                }
                campi.put("ora", t.toString())
            }
        }
        for (k in listOf("luogo", "medico", "note", "esito")) o.stringa(k)?.let { campi.put(k, it) }
        if (base == null) {
            if (!campi.has("tipo")) throw Errore("per un appuntamento nuovo serve il tipo")
            if (!campi.has("titolo")) throw Errore("per un appuntamento nuovo serve il titolo")
            if (!campi.has("data")) throw Errore("per un appuntamento nuovo serve la data")
        } else if (campi.length() == 0) {
            throw Errore("non c'è niente da modificare")
        }
        val titolo = campi.optString("titolo").ifEmpty { base?.titolo.orEmpty() }
        val data = campi.optString("data").ifEmpty { base?.data?.toString().orEmpty() }
        val riassunto = (if (base == null) "nuovo appuntamento «$titolo» del $data" else "modifica dell'appuntamento ${codice(base)} «${base.titolo}»") +
            " (" + campi.keys().asSequence().joinToString(", ") + ")"
        return Proposta(tipo = "appuntamento", bersaglio = base?.id, campi = campi.toString(), riassunto = riassunto)
    }

    private fun ecografia(o: JSONObject, d: DatiAttuali, allegati: List<String>): Proposta {
        val data = o.dataDi("data")
        val base = o.stringa("codice")?.takeIf { it.isNotEmpty() }?.let { c ->
            val id = trova(c, d.ecografie.map { codice(it) to it.id }, "un'ecografia")
            d.ecografie.first { it.id == id }
        } ?: data?.let { dt -> d.ecografie.firstOrNull { it.data == dt } }
        val campi = JSONObject()
        o.stringa("titolo")?.takeIf { it.isNotEmpty() }?.let { campi.put("titolo", it) }
        if (data != null) campi.put("data", data.toString())
        val mis = o.optJSONObject("misure")
        if (mis != null && mis.length() > 0) {
            val m = JSONObject()
            for (k in mis.keys()) {
                val mi = Misura.da(k) ?: throw Errore("misura «$k» sconosciuta")
                val v = mis.numeroDi(k) ?: continue
                if (v <= 0 || v > mi.massimo()) throw Errore("${mi.nome} = ${numero(v)} ${mi.unita} non è plausibile: controlla di averlo letto bene")
                m.put(mi.chiave, v)
            }
            if (m.length() > 0) campi.put("misure", m)
        }
        o.stringa("note")?.let { campi.put("note", it) }
        if (o.optBoolean("foto_allegate")) campi.put("foto", fotoAllegate(allegati))
        if (base == null && data == null) throw Errore("per un'ecografia nuova serve la data")
        if (base != null && campi.length() == 0) throw Errore("non c'è niente da modificare")
        val riassunto = (if (base == null) "nuova ecografia del $data" else "modifica dell'ecografia ${codice(base)} del ${ISO.format(base.data)}") +
            (campi.optJSONObject("misure")?.let { m -> " con misure " + m.keys().asSequence().joinToString(", ") } ?: "") +
            (if (campi.has("foto")) " e ${allegati.size} foto" else "")
        return Proposta(tipo = "ecografia", bersaglio = base?.id, campi = campi.toString(), riassunto = riassunto)
    }

    /** Valore massimo plausibile di una misura, per scartare letture sbagliate. */
    private fun Misura.massimo(): Double = when (this) {
        Misura.PESO -> 6500.0
        Misura.BATTITO -> 250.0
        Misura.NT -> 15.0
        else -> 500.0
    }

    private fun referto(o: JSONObject, d: DatiAttuali, allegati: List<String>): Proposta {
        val base = o.stringa("codice")?.takeIf { it.isNotEmpty() }?.let { c ->
            val id = trova(c, d.referti.map { codice(it) to it.id }, "un referto")
            d.referti.first { it.id == id }
        }
        val campi = JSONObject()
        o.stringa("tipo")?.takeIf { it.isNotEmpty() }?.let { t ->
            if (TipoReferto.da(t) == null) throw Errore("tipo di referto «$t» sconosciuto")
            campi.put("tipo", t)
        }
        o.stringa("titolo")?.takeIf { it.isNotEmpty() }?.let { campi.put("titolo", it) }
        o.dataDi("data")?.let { campi.put("data", it.toString()) }
        for (k in listOf("dove", "esito", "note")) o.stringa(k)?.let { campi.put(k, it) }
        o.stringa("appuntamento")?.takeIf { it.isNotEmpty() }?.let { c ->
            campi.put("appuntamento", trova(c, d.appuntamenti.map { codice(it) to it.id }, "un appuntamento"))
        }
        if (o.optBoolean("foto_allegate")) campi.put("foto", fotoAllegate(allegati))
        if (base == null) {
            if (!campi.has("titolo")) throw Errore("per un referto nuovo serve il titolo")
            if (!campi.has("data")) throw Errore("per un referto nuovo serve la data")
        } else if (campi.length() == 0) {
            throw Errore("non c'è niente da modificare")
        }
        val riassunto = (
            if (base == null) "nuovo referto «${campi.optString("titolo")}» del ${campi.optString("data")}"
            else "modifica del referto ${codice(base)} «${base.titolo}»"
            ) + " (" + campi.keys().asSequence().joinToString(", ") + ")"
        return Proposta(tipo = "referto", bersaglio = base?.id, campi = campi.toString(), riassunto = riassunto)
    }

    private fun consiglio(o: JSONObject): Proposta {
        val testo = o.stringa("testo")?.takeIf { it.isNotEmpty() } ?: throw Errore("manca il testo del consiglio")
        val data = o.dataDi("data") ?: LocalDate.now()
        val campi = JSONObject().put("testo", testo).put("autore", o.stringa("autore").orEmpty()).put("data", data.toString())
        return Proposta(tipo = "consiglio", campi = campi.toString(), riassunto = "nuovo consiglio: «${testo.take(60)}»")
    }

    private fun fotoGalleria(o: JSONObject, allegati: List<String>): Proposta {
        val data = o.dataDi("data") ?: LocalDate.now()
        val campi = JSONObject().put("data", data.toString()).put("didascalia", o.stringa("didascalia").orEmpty())
            .put("foto", fotoAllegate(allegati))
        return Proposta(tipo = "foto", campi = campi.toString(), riassunto = "${allegati.size} foto nella sezione Foto")
    }

    private fun impostazioni(o: JSONObject): Proposta {
        val campi = JSONObject()
        o.dataDi("data_parto")?.let { dpp ->
            val oggi = LocalDate.now()
            if (dpp.isBefore(oggi.minusDays(60)) || dpp.isAfter(oggi.plusDays(300))) throw Errore("la data presunta del parto $dpp non è plausibile")
            campi.put("data_parto", dpp.toString())
        }
        o.numeroDi("peso_prima")?.let { kg ->
            if (kg < 30 || kg > 250) throw Errore("un peso di ${numero(kg)} kg non è plausibile")
            campi.put("peso_prima", kg)
        }
        if (campi.length() == 0) throw Errore("non c'è niente da impostare")
        val riassunto = listOfNotNull(
            campi.optString("data_parto").takeIf { it.isNotEmpty() }?.let { "data presunta del parto $it" },
            if (campi.has("peso_prima")) "peso prima della gravidanza ${numero(campi.getDouble("peso_prima"))} kg" else null,
        ).joinToString(" e ")
        return Proposta(tipo = "impostazioni", campi = campi.toString(), riassunto = riassunto)
    }

    // ---------------- Applicazione dei campi (al momento del «Salva») ----------------

    private fun JSONObject.dataApp(k: String): LocalDate? = optString(k).takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }

    /** I campi applicati all'appuntamento [base], o a uno nuovo. */
    fun applicaAppuntamento(base: Appuntamento?, c: JSONObject): Appuntamento {
        var a = base ?: Appuntamento()
        if (c.has("tipo")) a = a.copy(tipo = Tipo.da(c.getString("tipo")))
        if (c.has("titolo")) a = a.copy(titolo = c.getString("titolo"))
        c.dataApp("data")?.let { a = a.copy(data = it) }
        if (c.has("ora")) a = a.copy(ora = c.getString("ora").takeIf { it.isNotEmpty() }?.let { LocalTime.parse(it) })
        if (c.has("luogo")) a = a.copy(luogo = c.getString("luogo"))
        if (c.has("medico")) a = a.copy(medico = c.getString("medico"))
        if (c.has("note")) a = a.copy(note = c.getString("note"))
        if (c.has("esito")) a = a.copy(esito = c.getString("esito"))
        return a
    }

    /** I campi applicati all'ecografia [base], o a una nuova (le misure si aggiungono a quelle che c'erano). */
    fun applicaEcografia(base: Ecografia?, c: JSONObject): Ecografia {
        var e = base ?: Ecografia(titolo = "Ecografia")
        if (c.has("titolo")) e = e.copy(titolo = c.getString("titolo"))
        c.dataApp("data")?.let { e = e.copy(data = it) }
        c.optJSONObject("misure")?.let { m ->
            val nuove = HashMap(e.misure)
            for (k in m.keys()) nuove[k] = m.getDouble(k)
            e = e.copy(misure = nuove)
        }
        if (c.has("note")) e = e.copy(note = c.getString("note"))
        return e
    }

    /** I campi applicati al referto [base], o a uno nuovo (il tipo, se manca, si indovina dal titolo). */
    fun applicaReferto(base: Referto?, c: JSONObject): Referto {
        var r = base ?: Referto(tipo = TipoReferto.indovina(c.optString("titolo")))
        TipoReferto.da(c.optString("tipo"))?.let { r = r.copy(tipo = it) }
        if (c.has("titolo")) r = r.copy(titolo = c.getString("titolo"))
        c.dataApp("data")?.let { r = r.copy(data = it) }
        if (c.has("dove")) r = r.copy(dove = c.getString("dove"))
        if (c.has("esito")) r = r.copy(esito = c.getString("esito"))
        if (c.has("note")) r = r.copy(note = c.getString("note"))
        if (c.has("appuntamento")) r = r.copy(appuntamento = c.getString("appuntamento"))
        return r
    }

    /** Le foto della chat da collegare (nomi di file). */
    fun fotoDi(c: JSONObject): List<String> {
        val a = c.optJSONArray("foto") ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    // ---------------- Descrizione per la scheda di conferma ----------------

    fun titolo(p: Proposta): String {
        val nuovo = p.bersaglio == null
        return when (p.tipo) {
            "peso" -> if (p.bersaglio == null) "Aggiungere un peso" else "Correggere il peso"
            "appuntamento" -> if (nuovo) "Nuovo appuntamento" else "Modificare l'appuntamento"
            "ecografia" -> if (nuovo) "Nuova ecografia" else "Aggiornare l'ecografia"
            "referto" -> if (nuovo) "Nuovo referto" else "Aggiornare il referto"
            "consiglio" -> "Salvare un consiglio"
            "foto" -> "Aggiungere alle Foto"
            "impostazioni" -> "Impostazioni della gravidanza"
            else -> "Modifica"
        }
    }

    private fun d(s: String): String = try { dataLunga(LocalDate.parse(s)).maiuscola() } catch (e: Exception) { s }

    /**
     * Le righe della scheda. Per le modifiche, [prima] è il valore attuale nell'app
     * (finché la proposta è da confermare).
     */
    fun righe(p: Proposta, m: Model): List<RigaProposta> {
        val c = try { JSONObject(p.campi) } catch (e: Exception) { JSONObject() }
        // il confronto con i valori attuali ha senso solo finché la proposta è da confermare
        val confronta = p.stato == Proposta.ATTESA && (p.bersaglio != null || p.tipo == "impostazioni")
        val r = ArrayList<RigaProposta>()
        fun riga(etichetta: String, valore: String, prima: String?) {
            r.add(RigaProposta(etichetta, valore, prima?.takeIf { confronta && it != valore }?.ifEmpty { "(vuoto)" }))
        }
        when (p.tipo) {
            "peso" -> {
                val vecchio = p.bersaglio?.let { id -> m.pesi.firstOrNull { it.id == id } }
                riga("Giorno", d(c.optString("data")), null)
                riga("Peso", numero(c.optDouble("kg")) + " kg", vecchio?.let { numero(it.kg) + " kg" })
                c.optString("nota").takeIf { it.isNotBlank() }?.let { riga("Nota", it, vecchio?.nota) }
            }
            "appuntamento" -> {
                val a = p.bersaglio?.let { m.trova(it) }
                if (a != null && !c.has("titolo")) r.add(RigaProposta("Appuntamento", a.titolo + " · " + dataMedia(a.data)))
                if (c.has("tipo")) riga("Tipo", Tipo.da(c.getString("tipo")).nome, a?.tipo?.nome)
                if (c.has("titolo")) riga("Titolo", c.getString("titolo"), a?.titolo)
                if (c.has("data")) riga("Giorno", d(c.getString("data")), a?.data?.let { d(it.toString()) })
                if (c.has("ora")) riga("Ora", c.getString("ora").ifEmpty { "nessuna" }, a?.let { x -> x.ora?.let { orario(it) } ?: "nessuna" })
                if (c.has("luogo")) riga("Luogo", c.getString("luogo"), a?.luogo)
                if (c.has("medico")) riga("Con", c.getString("medico"), a?.medico)
                if (c.has("note")) riga("Note", c.getString("note"), a?.note)
                if (c.has("esito")) riga("Esito", c.getString("esito"), a?.esito)
            }
            "ecografia" -> {
                val e = p.bersaglio?.let { m.ecografia(it) }
                if (e != null && !c.has("titolo")) r.add(RigaProposta("Ecografia", e.titolo + " · " + dataMedia(e.data)))
                if (c.has("titolo")) riga("Titolo", c.getString("titolo"), e?.titolo)
                if (c.has("data")) riga("Giorno", d(c.getString("data")), e?.data?.let { d(it.toString()) })
                c.optJSONObject("misure")?.let { mis ->
                    for (mi in Misura.entries) {
                        if (!mis.has(mi.chiave)) continue
                        riga(mi.nome, mi.formatta(mis.getDouble(mi.chiave)), e?.misura(mi)?.let { mi.formatta(it) } ?: if (e != null) "" else null)
                    }
                }
                if (c.has("note")) riga("Note", c.getString("note"), e?.note)
            }
            "referto" -> {
                val x = p.bersaglio?.let { m.referto(it) }
                if (x != null && !c.has("titolo")) r.add(RigaProposta("Referto", x.titolo + " · " + dataMedia(x.data)))
                TipoReferto.da(c.optString("tipo"))?.let { riga("Tipo", it.nome, x?.tipo?.nome) }
                if (c.has("titolo")) riga("Titolo", c.getString("titolo"), x?.titolo)
                if (c.has("data")) riga("Giorno", d(c.getString("data")), x?.data?.let { d(it.toString()) })
                if (c.has("dove")) riga("Presso", c.getString("dove"), x?.dove)
                if (c.has("esito")) riga("Esito", c.getString("esito"), x?.esito)
                if (c.has("note")) riga("Note", c.getString("note"), x?.note)
                if (c.has("appuntamento")) {
                    val a = m.trova(c.getString("appuntamento"))
                    riga("Appuntamento", a?.let { it.titolo + " · " + dataMedia(it.data) } ?: "non più in agenda", null)
                }
            }
            "consiglio" -> {
                riga("Consiglio", c.optString("testo"), null)
                c.optString("autore").takeIf { it.isNotBlank() }?.let { riga("Da", it, null) }
                riga("Giorno", d(c.optString("data")), null)
            }
            "foto" -> {
                riga("Giorno", d(c.optString("data")), null)
                c.optString("didascalia").takeIf { it.isNotBlank() }?.let { riga("Didascalia", it, null) }
            }
            "impostazioni" -> {
                c.optString("data_parto").takeIf { it.isNotEmpty() }?.let {
                    riga("Data presunta del parto", d(it), m.dpp?.let { x -> d(x.toString()) } ?: if (p.stato == Proposta.ATTESA) "non inserita" else null)
                }
                if (c.has("peso_prima")) {
                    riga(
                        "Peso prima della gravidanza", numero(c.getDouble("peso_prima")) + " kg",
                        m.pesoIniziale?.let { numero(it) + " kg" } ?: if (p.stato == Proposta.ATTESA) "non inserito" else null,
                    )
                }
            }
        }
        val foto = fotoDi(c)
        if (foto.isNotEmpty()) r.add(RigaProposta("Foto", if (foto.size == 1) "1 foto allegata" else "${foto.size} foto allegate"))
        return r
    }
}
