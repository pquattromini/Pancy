package com.pancy.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.max

/** Modelli OpenAI proposti nelle impostazioni: nome per l'API e descrizione. Se OpenAI li cambia, basta aggiornare qui. */
val MODELLI_IA = listOf(
    "gpt-6.1-terra" to "Equilibrato (consigliato)",
    "gpt-6-luna" to "Economico",
    "gpt-6.1-sol" to "Più accurato",
)
const val MODELLO_PREDEFINITO = "gpt-6.1-terra"

/** Domande pronte da toccare nella schermata "Chiedi all'IA". */
val DOMANDE_PRONTE = listOf(
    "Riassumi come sta andando la gravidanza finora.",
    "Cosa dobbiamo prenotare o fare nelle prossime settimane?",
    "Come sta crescendo il bambino secondo le ecografie?",
    "Prepara le domande da fare alla prossima visita.",
)

// ---------------- Conversazioni ----------------

/** Un messaggio della chat. [allegati] sono nomi di file nella cartella "chat" dell'app. */
data class Messaggio(
    val ia: Boolean,
    val testo: String,
    val data: Long = System.currentTimeMillis(),
    val allegati: List<String> = emptyList(),
    val errore: Boolean = false,
) {
    fun toJson(): JSONObject {
        val al = JSONArray()
        for (a in allegati) al.put(a)
        return JSONObject()
            .put("ia", ia)
            .put("testo", testo)
            .put("data", data)
            .put("allegati", al)
            .put("errore", errore)
    }

    companion object {
        fun daJson(o: JSONObject): Messaggio {
            val al = ArrayList<String>()
            val arr = o.optJSONArray("allegati")
            if (arr != null) for (i in 0 until arr.length()) al.add(arr.getString(i))
            return Messaggio(
                ia = o.optBoolean("ia"),
                testo = o.optString("testo"),
                data = o.optLong("data"),
                allegati = al,
                errore = o.optBoolean("errore"),
            )
        }
    }
}

data class Conversazione(
    val id: String = UUID.randomUUID().toString(),
    val titolo: String = "",
    val creata: Long = System.currentTimeMillis(),
    val aggiornata: Long = System.currentTimeMillis(),
    val messaggi: List<Messaggio> = emptyList(),
) {
    fun toJson(): JSONObject {
        val arr = JSONArray()
        for (msg in messaggi) arr.put(msg.toJson())
        return JSONObject()
            .put("id", id)
            .put("titolo", titolo)
            .put("creata", creata)
            .put("aggiornata", aggiornata)
            .put("messaggi", arr)
    }

    companion object {
        fun daJson(o: JSONObject): Conversazione {
            val msgs = ArrayList<Messaggio>()
            val arr = o.optJSONArray("messaggi")
            if (arr != null) for (i in 0 until arr.length()) msgs.add(Messaggio.daJson(arr.getJSONObject(i)))
            return Conversazione(
                id = o.getString("id"),
                titolo = o.optString("titolo"),
                creata = o.optLong("creata"),
                aggiornata = o.optLong("aggiornata"),
                messaggi = msgs,
            )
        }

        /** Dalla più recente. */
        fun ordina(l: List<Conversazione>): List<Conversazione> = l.sortedByDescending { it.aggiornata }

        /** Titolo dalla prima domanda: le prime parole, al massimo 60 caratteri. */
        fun titoloDa(domanda: String): String {
            val t = domanda.trim().replace("\n", " ")
            if (t.isEmpty()) return "Foto allegate"
            return if (t.length <= 60) t else t.take(57).trimEnd() + "…"
        }
    }
}

// ---------------- Collegamento a OpenAI ----------------

class ErroreIA(messaggio: String, val codice: Int = 0) : Exception(messaggio)

/** Legge un campo di testo trattando il null di JSON come stringa vuota. */
private fun JSONObject.testo(k: String): String = if (isNull(k)) "" else optString(k)

object OpenAI {
    private const val INDIRIZZO = "https://api.openai.com/v1/responses"

    /** Manda una richiesta all'API Responses di OpenAI. Bloccante: va chiamata fuori dal thread principale. */
    fun invia(chiave: String, corpo: JSONObject): JSONObject {
        val c = URL(INDIRIZZO).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = 180_000 // i modelli che "ragionano" possono metterci un po'
            c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer " + chiave.trim())
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }
            val codice = c.responseCode
            val flusso = if (codice in 200..299) c.inputStream else c.errorStream
            val testo = flusso?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (codice !in 200..299) throw ErroreIA(spiegaErrore(codice, testo), codice)
            return JSONObject(testo)
        } finally {
            c.disconnect()
        }
    }

    private fun spiegaErrore(codice: Int, corpo: String): String {
        val err = try { JSONObject(corpo).optJSONObject("error") } catch (e: Exception) { null }
        val tipo = err?.testo("code").orEmpty().ifEmpty { err?.testo("type").orEmpty() }
        val dettaglio = err?.testo("message").orEmpty()
        return when {
            codice == 401 -> "La chiave API non è valida: controllala nelle Impostazioni."
            tipo == "insufficient_quota" ->
                "Il credito OpenAI è esaurito o scaduto. Controlla il saldo su platform.openai.com, alla voce Billing."
            codice == 429 -> "Troppe richieste ravvicinate: riprova tra qualche secondo."
            tipo == "model_not_found" || codice == 404 ->
                "Il modello scelto non è disponibile per il tuo account: scegline un altro nelle Impostazioni."
            codice >= 500 -> "OpenAI ha un problema temporaneo: riprova tra poco."
            else -> "Errore da OpenAI ($codice)" + (if (dettaglio.isNotBlank()) ": $dettaglio" else ".")
        }
    }
}

object Assistente {
    private const val MAX_GIRI = 4
    private const val MAX_IMMAGINI_PER_VOLTA = 6
    private const val LATO_IMMAGINE = 1600

    fun cartellaChat(ctx: Context): File = File(ctx.filesDir, "chat").also { it.mkdirs() }

    fun nuovoAllegato(ctx: Context): File =
        File(cartellaChat(ctx), "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}.jpg")

    /** Copia nella cartella della chat una foto scelta dalla galleria. Restituisce il nome del file, o null. */
    fun copiaAllegato(ctx: Context, uri: Uri): String? {
        val dest = nuovoAllegato(ctx)
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            dest.name
        } catch (e: Exception) {
            dest.delete()
            null
        }
    }

    /** L'unico strumento dell'IA: chiedere di vedere alcune foto dell'app. */
    private fun strumenti(): JSONArray = JSONArray().put(
        JSONObject()
            .put("type", "function")
            .put("name", "guarda_immagini")
            .put(
                "description",
                "Mostra le foto indicate nell'elenco FOTO E VIDEO dei dati (stampe o schermate delle ecografie, " +
                    "foto del pancione, referti fotografati). Per i video arriva un fotogramma. " +
                    "Al massimo $MAX_IMMAGINI_PER_VOLTA codici per volta.",
            )
            .put(
                "parameters",
                JSONObject()
                    .put("type", "object")
                    .put(
                        "properties",
                        JSONObject().put(
                            "codici",
                            JSONObject()
                                .put("type", "array")
                                .put("items", JSONObject().put("type", "string"))
                                .put("description", "Codici delle immagini, per esempio F-1a2b3c"),
                        ),
                    )
                    .put("required", JSONArray().put("codici"))
                    .put("additionalProperties", false),
            ),
    )

    /** Foto ridotta (lato lungo al massimo 1600 px) e codificata come data URL JPEG. Null se non si legge. */
    fun immagineDataUrl(f: File, video: Boolean): String? = try {
        val b = (if (video) Galleria.fotogramma(f) else Galleria.foto(f, LATO_IMMAGINE)) ?: return null
        val lungo = max(b.width, b.height)
        val r = if (lungo <= LATO_IMMAGINE) {
            b
        } else {
            val k = LATO_IMMAGINE.toFloat() / lungo
            Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
        }
        val out = ByteArrayOutputStream()
        r.compress(Bitmap.CompressFormat.JPEG, 85, out)
        "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun chiama(chiave: String, corpo: JSONObject): JSONObject = try {
        OpenAI.invia(chiave, corpo)
    } catch (e: ErroreIA) {
        // i modelli che non "ragionano" possono rifiutare il ragionamento cifrato: riprovo senza
        if (e.codice == 400 && corpo.has("include")) {
            corpo.remove("include")
            OpenAI.invia(chiave, corpo)
        } else {
            throw e
        }
    }

    /** Prova veloce della chiave e del modello: una domanda minuscola. Restituisce la risposta. */
    fun prova(chiave: String, modello: String): String {
        val corpo = JSONObject()
            .put("model", modello)
            .put("input", "Rispondi solo con la parola: ok")
            .put("store", false)
        val r = OpenAI.invia(chiave, corpo)
        return testoDi(r.optJSONArray("output") ?: JSONArray()).ifBlank { "(risposta vuota)" }
    }

    private fun testoDi(output: JSONArray): String {
        val sb = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            if (item.optString("type") != "message") continue
            val parti = item.optJSONArray("content") ?: continue
            for (j in 0 until parti.length()) {
                val p = parti.optJSONObject(j) ?: continue
                if (p.optString("type") == "output_text") sb.append(p.optString("text"))
            }
        }
        return sb.toString().trim()
    }

    /**
     * Fa rispondere l'IA all'ultima domanda della conversazione.
     * [istruzioni] contiene le regole e tutti i dati dell'app; [immagini] collega i codici F-… ai file.
     * Bloccante: va chiamata fuori dal thread principale.
     */
    fun rispondi(
        ctx: Context,
        chiave: String,
        modello: String,
        istruzioni: String,
        storia: List<Messaggio>,
        immagini: Map<String, Pair<File, Boolean>>,
    ): String {
        val input = JSONArray()
        // le foto allegate si rimandano solo per gli ultimi 3 messaggi che ne hanno, per non pagarle ogni volta
        val conAllegatiRecenti = storia.filter { !it.ia && !it.errore && it.allegati.isNotEmpty() }.takeLast(3)
        for (msg in storia) {
            if (msg.errore) continue
            if (msg.ia) {
                input.put(JSONObject().put("role", "assistant").put("content", msg.testo))
                continue
            }
            val parti = JSONArray().put(
                JSONObject().put("type", "input_text").put("text", msg.testo.ifBlank { "Guarda le foto allegate." }),
            )
            if (msg.allegati.isNotEmpty()) {
                if (conAllegatiRecenti.any { it === msg }) {
                    for (nome in msg.allegati) {
                        val url = immagineDataUrl(File(cartellaChat(ctx), nome), false) ?: continue
                        parti.put(JSONObject().put("type", "input_image").put("image_url", url).put("detail", "auto"))
                    }
                } else {
                    parti.put(
                        JSONObject().put("type", "input_text")
                            .put("text", "(A questo messaggio erano allegate ${msg.allegati.size} foto, non più incluse.)"),
                    )
                }
            }
            input.put(JSONObject().put("role", "user").put("content", parti))
        }

        for (giro in 1..MAX_GIRI) {
            val corpo = JSONObject()
                .put("model", modello)
                .put("instructions", istruzioni)
                .put("input", input)
                .put("tools", strumenti())
                .put("store", false) // OpenAI non conserva la conversazione
                .put("include", JSONArray().put("reasoning.encrypted_content"))
            // all'ultimo giro l'IA deve rispondere, non chiedere altre foto
            if (giro == MAX_GIRI) corpo.put("tool_choice", "none")
            val r = chiama(chiave, corpo)
            val output = r.optJSONArray("output") ?: JSONArray()

            val chiamate = ArrayList<JSONObject>()
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                if (item.optString("type") == "function_call") chiamate.add(item)
            }
            if (chiamate.isEmpty()) {
                val t = testoDi(output)
                if (t.isEmpty()) throw ErroreIA("L'IA non ha dato una risposta. Riprova, magari con un altro modello.")
                return t
            }

            // l'IA vuole vedere delle foto: rimetto tutto il suo output nel contesto, poi rispondo alle chiamate
            for (i in 0 until output.length()) input.put(output.get(i))
            val viste = JSONArray()
            for (c in chiamate) {
                val codici = try {
                    JSONObject(c.optString("arguments")).optJSONArray("codici")
                } catch (e: Exception) {
                    null
                } ?: JSONArray()
                val trovati = ArrayList<String>()
                val mancanti = ArrayList<String>()
                for (k in 0 until minOf(codici.length(), MAX_IMMAGINI_PER_VOLTA)) {
                    val cod = codici.optString(k).trim()
                    val im = immagini[cod]
                    val url = if (im != null) immagineDataUrl(im.first, im.second) else null
                    if (url != null) {
                        trovati.add(cod)
                        viste.put(JSONObject().put("type", "input_text").put("text", "Immagine $cod:"))
                        viste.put(JSONObject().put("type", "input_image").put("image_url", url).put("detail", "auto"))
                    } else {
                        mancanti.add(cod)
                    }
                }
                val esito = buildString {
                    if (trovati.isNotEmpty()) append("Immagini inviate nel messaggio seguente: ${trovati.joinToString()}.")
                    if (mancanti.isNotEmpty()) append(" Codici non trovati o non leggibili: ${mancanti.joinToString()}.")
                }.trim().ifEmpty { "Nessun codice valido." }
                input.put(
                    JSONObject().put("type", "function_call_output").put("call_id", c.optString("call_id")).put("output", esito),
                )
            }
            if (viste.length() > 0) input.put(JSONObject().put("role", "user").put("content", viste))
        }
        throw ErroreIA("L'IA non è riuscita a completare la risposta. Riprova.")
    }
}

// ---------------- Fascicolo: tutti i dati dell'app, in testo ----------------

object Fascicolo {
    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    private const val REGOLE = """Sei l'assistente dell'app Pancy, che una coppia usa per seguire la gravidanza. Chi scrive può essere la futura mamma o il suo compagno.

Come rispondere:
- Rispondi in italiano, in modo chiaro, caldo e concreto. Vai al punto; usa elenchi solo quando aiutano.
- Qui sotto trovi TUTTI i dati inseriti nell'app. Quando la domanda riguarda la loro gravidanza, basati su questi dati e cita date e valori. Se un dato manca o non è aggiornato, dillo chiaramente invece di inventarlo.
- Per le domande generali puoi usare le tue conoscenze, ma distingui sempre cosa viene dai loro dati e cosa è un'informazione generale.
- Se per rispondere ti serve guardare foto o immagini delle ecografie, usa lo strumento guarda_immagini con i codici F-… dell'elenco FOTO E VIDEO. Chiedi solo le immagini che servono davvero.
- Puoi usare il **grassetto** ed elenchi con il trattino, ma niente tabelle.

Limiti importanti:
- Non sei un medico: non fare diagnosi, non dare dosi di farmaci e non suggerire di cambiare terapie. Per ogni decisione clinica rimanda al medico o all'ostetrica, e aiuta a preparare le domande da fare.
- Se la domanda descrive un segnale di allarme (perdite di sangue, perdita di liquido, contrazioni regolari prima della 37a settimana, movimenti del bambino diminuiti o assenti, forte mal di testa con disturbi della vista o gonfiore improvviso, febbre alta, dolore forte alla pancia), di' prima di tutto di chiamare subito il punto nascita o il 112.
- Valori di misure ed ecografie: puoi spiegare cosa misurano e come si leggono in generale, ma il giudizio sul singolo caso spetta a chi ha fatto l'esame."""

    /** Codice breve e stabile di una foto, per lo strumento guarda_immagini. */
    fun codice(med: Media): String = "F-" + med.id.replace("-", "").take(6)

    fun immagini(m: Model, ctx: Context): Map<String, Pair<File, Boolean>> =
        m.media.associate { codice(it) to Pair(Galleria.file(ctx, it), it.video) }

    fun istruzioni(m: Model): String = REGOLE + "\n\n# DATI DELL'APP\n\n" + dati(m)

    private fun riga(vararg parti: String?): String =
        "- " + parti.filterNotNull().map { it.replace("\n", " / ").trim() }.filter { it.isNotEmpty() }.joinToString(" | ")

    private fun sett(m: Model, d: LocalDate): String? = Gravidanza.settimana(m.dpp, d)?.let { "sett. $it" }

    fun dati(m: Model): String = buildString {
        val oggi = LocalDate.now()
        val adesso = LocalDateTime.now()
        appendLine("Oggi è ${dataLunga(oggi)}, ore ${orario(adesso.toLocalTime())}.")
        appendLine()

        appendLine("GRAVIDANZA")
        val dpp = m.dpp
        if (dpp == null) {
            appendLine("- Data presunta del parto: non inserita (la settimana di gravidanza non è nota).")
        } else {
            appendLine("- Data presunta del parto: ${ISO.format(dpp)} (${dataLunga(dpp)}).")
            val g = Gravidanza.giorni(dpp, oggi)
            val mancano = ChronoUnit.DAYS.between(oggi, dpp)
            if (g != null) {
                appendLine("- Oggi: settimana ${g / 7}+${g % 7} (${Gravidanza.settimanaPerEsteso(g)}), ${Gravidanza.nomeTrimestre(g)}.")
            }
            appendLine(if (mancano >= 0) "- Mancano $mancano giorni alla data presunta del parto." else "- La data presunta del parto è passata da ${-mancano} giorni.")
        }
        val pi = m.pesoIniziale
        appendLine("- Peso della mamma prima della gravidanza: " + (if (pi != null) numero(pi) + " kg" else "non inserito") + ".")
        appendLine()

        appendLine("APPUNTAMENTI (${m.appuntamenti.size})")
        if (m.appuntamenti.isEmpty()) appendLine("- Nessuno.")
        for (a in m.appuntamenti) {
            appendLine(
                riga(
                    ISO.format(a.data) + (a.ora?.let { " ore " + orario(it) } ?: ""),
                    a.tipo.nome, a.titolo, sett(m, a.data),
                    if (a.passato()) "già fatto" else "in programma",
                    a.luogo.takeIf { it.isNotBlank() }?.let { "luogo: $it" },
                    a.medico.takeIf { it.isNotBlank() }?.let { "con: $it" },
                    a.note.takeIf { it.isNotBlank() }?.let { "note: $it" },
                    a.esito.takeIf { it.isNotBlank() }?.let { "esito: $it" },
                ),
            )
        }
        appendLine()

        appendLine("ECOGRAFIE (${m.ecografie.size})")
        if (m.ecografie.isEmpty()) appendLine("- Nessuna.")
        for (e in m.ecografie.sortedBy { it.data.toEpochDay() }) {
            val misure = Misura.entries.mapNotNull { mi -> e.misura(mi)?.let { "${mi.nome} (${mi.sigla}) ${mi.formatta(it)}" } }
            val foto = m.mediaDi(e.id).map { codice(it) }
            appendLine(
                riga(
                    ISO.format(e.data), e.titolo, sett(m, e.data),
                    if (misure.isEmpty()) "misure: non inserite" else "misure: " + misure.joinToString("; "),
                    e.note.takeIf { it.isNotBlank() }?.let { "note: $it" },
                    if (foto.isEmpty()) null else "immagini: " + foto.joinToString(", "),
                ),
            )
        }
        appendLine()

        appendLine("PESO DELLA MAMMA (${m.pesi.size} pesate)")
        if (m.pesi.isEmpty()) appendLine("- Nessuna pesata.")
        for (p in m.pesi) {
            appendLine(riga(ISO.format(p.data), sett(m, p.data), numero(p.kg) + " kg", p.nota.takeIf { it.isNotBlank() }))
        }
        appendLine()

        appendLine("CONSIGLI RICEVUTI DA MEDICI, OSTETRICHE O ALTRI ESPERTI (${m.consigli.size})")
        if (m.consigli.isEmpty()) appendLine("- Nessuno.")
        for (c in m.consigli.sortedBy { it.data.toEpochDay() }) {
            appendLine(riga(ISO.format(c.data), c.autore.ifBlank { "autore non indicato" }, c.testo))
        }
        appendLine()

        appendLine("FOTO E VIDEO (${m.media.size}) — usa questi codici con guarda_immagini")
        if (m.media.isEmpty()) appendLine("- Nessuna.")
        for (med in m.media.sortedBy { it.data.toEpochDay() }) {
            val eco = med.ecografia?.let { m.ecografia(it) }
            appendLine(
                riga(
                    codice(med), if (med.video) "video" else "foto", ISO.format(med.data), sett(m, med.data),
                    med.didascalia.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
                    eco?.let { "dall'ecografia del ${ISO.format(it.data)}" },
                ),
            )
        }
        appendLine()

        appendLine("CALENDARIO DEGLI ESAMI GRATUITI (LEA 2017) E COSA C'È IN AGENDA")
        for (es in Guida.esami) {
            val app = m.appuntamenti.firstOrNull { it.esame == es.chiave }
            val stato = when {
                app == null -> "non in agenda"
                app.passato() -> "fatto il ${ISO.format(app.data)}"
                else -> "in agenda il ${ISO.format(app.data)}"
            }
            appendLine(riga(es.titolo, "settimane ${es.da}-${es.a}", es.soloSe, stato))
        }
    }
}
