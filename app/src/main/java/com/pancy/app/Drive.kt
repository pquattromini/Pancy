package com.pancy.app

import android.content.Context
import android.webkit.MimeTypeMap
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Un file o una cartella su Google Drive, con i campi che servono all'app. */
data class FileDrive(
    val id: String,
    val nome: String,
    val mime: String,
    val descrizione: String = "",
    /** Quando è stato creato su Drive (RFC 3339). */
    val creato: String = "",
    /** Data di scatto letta da Drive nei dati EXIF ("2026:11:02 10:30:00"). */
    val dataFoto: String = "",
    val md5: String = "",
    /** Data della foto salvata dall'app (proprietà privata "pancyData"). */
    val propData: String = "",
    /** Data dell'ecografia a cui appartiene, salvata dall'app ("pancyEcografia"). */
    val propEco: String = "",
)

class ErroreDrive(messaggio: String, val serveConsenso: Boolean = false, val codice: Int = 0) : Exception(messaggio)

/** Le chiamate all'API di Google Drive. Tutte bloccanti: vanno fatte fuori dal thread principale. */
object Drive {
    /** Accesso completo a Drive: serve per leggere le cartelle esistenti e scriverci dentro. */
    const val AMBITO = "https://www.googleapis.com/auth/drive"
    const val TIPO_CARTELLA = "application/vnd.google-apps.folder"
    private const val API = "https://www.googleapis.com/drive/v3"
    private const val CARICA = "https://www.googleapis.com/upload/drive/v3"
    private const val CAMPI_FILE =
        "id,name,mimeType,description,createdTime,md5Checksum,imageMediaMetadata(time),appProperties"

    fun richiesta(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(AMBITO))).build()

    @Volatile private var token: String? = null
    @Volatile private var scadenza = 0L

    /** I token di Google durano circa un'ora: lo tengo per 45 minuti. */
    fun impostaToken(t: String?) {
        token = t
        scadenza = if (t != null) System.currentTimeMillis() + 45 * 60_000L else 0L
    }

    /** Un token valido, chiesto a Google senza mostrare nulla (l'accesso è già stato dato). */
    fun token(ctx: Context): String {
        val t = token
        if (t != null && System.currentTimeMillis() < scadenza) return t
        val r = try {
            Tasks.await(Identity.getAuthorizationClient(ctx).authorize(richiesta()))
        } catch (e: Exception) {
            throw ErroreDrive("Non riesco a collegarmi a Google Drive.")
        }
        if (r.hasResolution()) throw ErroreDrive("Google chiede di confermare l'accesso a Drive: tocca «Ricollega».", serveConsenso = true)
        val nuovo = r.accessToken ?: throw ErroreDrive("Google non ha dato l'accesso a Drive.")
        impostaToken(nuovo)
        return nuovo
    }

    /** Messaggio comprensibile per un errore del collegamento iniziale. */
    fun spiegaErroreAccesso(e: Exception): String =
        if (e is ApiException && e.statusCode == 10) {
            "Google rifiuta il collegamento: controlla nel progetto Google Cloud il nome del pacchetto (com.pancy.app) e l'impronta SHA-1."
        } else {
            "Non riesco a collegarmi a Google: " + (e.message ?: e.javaClass.simpleName)
        }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun leggi(c: HttpURLConnection, codice: Int): String =
        (if (codice in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

    private fun spiega(codice: Int, testo: String): String {
        val msg = try {
            JSONObject(testo).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
        return when (codice) {
            401 -> "Google chiede di confermare l'accesso a Drive: tocca «Ricollega»."
            403 -> "Google Drive ha rifiutato l'accesso" + (if (msg != null) ": $msg" else ".")
            404 -> "Cartella o file non trovato su Google Drive."
            else -> "Errore di Google Drive ($codice)" + (if (msg != null) ": $msg" else ".")
        }
    }

    /** Chiamata con risposta JSON. PATCH passa da POST perché HttpURLConnection non lo conosce. */
    private fun chiama(ctx: Context, metodo: String, url: String, corpo: JSONObject? = null, riprova: Boolean = true): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            if (metodo == "PATCH") {
                c.requestMethod = "POST"
                c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                c.requestMethod = metodo
            }
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            c.setRequestProperty("Authorization", "Bearer " + token(ctx))
            if (corpo != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(corpo.toString().toByteArray(Charsets.UTF_8)) }
            }
            val codice = c.responseCode
            if (codice == 401 && riprova) {
                impostaToken(null)
                return chiama(ctx, metodo, url, corpo, false)
            }
            val testo = leggi(c, codice)
            if (codice !in 200..299) throw ErroreDrive(spiega(codice, testo), codice = codice)
            return if (testo.isBlank()) JSONObject() else JSONObject(testo)
        } finally {
            c.disconnect()
        }
    }

    private fun daJson(o: JSONObject): FileDrive {
        val props = o.optJSONObject("appProperties")
        return FileDrive(
            id = o.getString("id"),
            nome = o.optString("name"),
            mime = o.optString("mimeType"),
            descrizione = o.optString("description"),
            creato = o.optString("createdTime"),
            dataFoto = o.optJSONObject("imageMediaMetadata")?.optString("time").orEmpty(),
            md5 = o.optString("md5Checksum"),
            propData = props?.optString("pancyData").orEmpty(),
            propEco = props?.optString("pancyEcografia").orEmpty(),
        )
    }

    /** Tutti i file che rispondono alla ricerca [q] (sintassi di Drive), pagina dopo pagina. */
    private fun cerca(ctx: Context, q: String): List<FileDrive> {
        val out = ArrayList<FileDrive>()
        var pagina: String? = null
        do {
            var url = "$API/files?q=" + enc(q) + "&fields=" + enc("nextPageToken,files($CAMPI_FILE)") +
                "&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true"
            if (pagina != null) url += "&pageToken=" + enc(pagina)
            val r = chiama(ctx, "GET", url)
            val arr = r.optJSONArray("files") ?: JSONArray()
            for (i in 0 until arr.length()) out.add(daJson(arr.getJSONObject(i)))
            pagina = r.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pagina != null)
        return out
    }

    /** Le sottocartelle di [genitore] ("root" = Il mio Drive), in ordine alfabetico. */
    fun cartelle(ctx: Context, genitore: String): List<FileDrive> =
        cerca(ctx, "'$genitore' in parents and mimeType = '$TIPO_CARTELLA' and trashed = false")
            .sortedBy { it.nome.lowercase() }

    /** Foto e video contenuti nella cartella. */
    fun mediaIn(ctx: Context, cartella: String): List<FileDrive> =
        cerca(ctx, "'$cartella' in parents and trashed = false and (mimeType contains 'image/' or mimeType contains 'video/')")

    /** Nome e tipo di un file o cartella (per esempio da un link incollato). */
    fun info(ctx: Context, id: String): FileDrive =
        daJson(chiama(ctx, "GET", "$API/files/$id?supportsAllDrives=true&fields=" + enc(CAMPI_FILE)))

    private fun scaricaUrl(ctx: Context, url: String, dest: File, riprova: Boolean = true) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.setRequestProperty("Authorization", "Bearer " + token(ctx))
            val codice = c.responseCode
            if (codice == 401 && riprova) {
                impostaToken(null)
                return scaricaUrl(ctx, url, dest, false)
            }
            if (codice !in 200..299) throw ErroreDrive(spiega(codice, leggi(c, codice)), codice = codice)
            dest.parentFile?.mkdirs()
            val tmp = File(dest.path + ".part")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } finally {
            c.disconnect()
        }
    }

    /** Scarica il file intero. */
    fun scarica(ctx: Context, id: String, dest: File) =
        scaricaUrl(ctx, "$API/files/$id?alt=media&supportsAllDrives=true", dest)

    /** Scarica l'anteprima che prepara Drive (600 px). Falso se Drive non ne ha una. */
    fun miniatura(ctx: Context, id: String, dest: File): Boolean {
        val r = chiama(ctx, "GET", "$API/files/$id?supportsAllDrives=true&fields=thumbnailLink")
        val link = r.optString("thumbnailLink").takeIf { it.isNotEmpty() } ?: return false
        scaricaUrl(ctx, link.replace(Regex("=s\\d+$"), "=s600"), dest)
        return true
    }

    private fun proprieta(props: Map<String, String?>): JSONObject {
        val o = JSONObject()
        // null cancella la proprietà su Drive
        for ((k, v) in props) o.put(k, v ?: JSONObject.NULL)
        return o
    }

    /** Carica un file nella cartella (caricamento "riprendibile", adatto anche ai video grandi). Restituisce l'id. */
    fun carica(
        ctx: Context, f: File, nome: String, mime: String, cartella: String,
        descrizione: String, props: Map<String, String?>,
    ): String {
        val meta = JSONObject()
            .put("name", nome)
            .put("parents", JSONArray().put(cartella))
            .put("description", descrizione)
            .put("appProperties", proprieta(props.filterValues { it != null }))
        val apri = URL("$CARICA/files?uploadType=resumable&supportsAllDrives=true&fields=id").openConnection() as HttpURLConnection
        val sessione: String
        try {
            apri.requestMethod = "POST"
            apri.doOutput = true
            apri.connectTimeout = 20_000
            apri.readTimeout = 60_000
            apri.setRequestProperty("Authorization", "Bearer " + token(ctx))
            apri.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            apri.setRequestProperty("X-Upload-Content-Type", mime)
            apri.setRequestProperty("X-Upload-Content-Length", f.length().toString())
            apri.outputStream.use { it.write(meta.toString().toByteArray(Charsets.UTF_8)) }
            val codice = apri.responseCode
            if (codice !in 200..299) throw ErroreDrive(spiega(codice, leggi(apri, codice)), codice = codice)
            sessione = apri.getHeaderField("Location") ?: throw ErroreDrive("Google Drive non ha aperto il caricamento.")
        } finally {
            apri.disconnect()
        }
        val invio = URL(sessione).openConnection() as HttpURLConnection
        try {
            invio.requestMethod = "PUT"
            invio.doOutput = true
            invio.connectTimeout = 20_000
            invio.readTimeout = 120_000
            invio.setFixedLengthStreamingMode(f.length())
            invio.setRequestProperty("Authorization", "Bearer " + token(ctx))
            invio.setRequestProperty("Content-Type", mime)
            invio.outputStream.use { o -> f.inputStream().use { it.copyTo(o) } }
            val codice = invio.responseCode
            val testo = leggi(invio, codice)
            if (codice !in 200..299) throw ErroreDrive(spiega(codice, testo), codice = codice)
            return JSONObject(testo).getString("id")
        } finally {
            invio.disconnect()
        }
    }

    /** Aggiorna didascalia e proprietà dell'app (data, ecografia). */
    fun aggiornaInfo(ctx: Context, id: String, descrizione: String, props: Map<String, String?>) {
        chiama(
            ctx, "PATCH", "$API/files/$id?supportsAllDrives=true&fields=id",
            JSONObject().put("description", descrizione).put("appProperties", proprieta(props)),
        )
    }

    /** Sposta il file da una cartella all'altra. */
    fun sposta(ctx: Context, id: String, da: String, a: String) {
        chiama(ctx, "PATCH", "$API/files/$id?addParents=$a&removeParents=$da&supportsAllDrives=true&fields=id", JSONObject())
    }

    /** Mette il file nel cestino di Drive (si recupera per 30 giorni). */
    fun cestina(ctx: Context, id: String) {
        chiama(ctx, "PATCH", "$API/files/$id?supportsAllDrives=true&fields=id", JSONObject().put("trashed", true))
    }
}

/** Risultato di una sincronizzazione, da applicare ai dati dell'app sul thread principale. */
class EsitoSync(
    /** Foto dell'app caricate (o ritrovate uguali) su Drive: id foto -> (id Drive, "foto"/"eco"). */
    val caricati: Map<String, Pair<String, String>>,
    /** File messi nel cestino di Drive. */
    val cestinati: Set<String>,
    val foto: List<FileDrive>,
    val eco: List<FileDrive>,
    /** Cartella in cui si trova ora ogni foto già collegata ("foto"/"eco"). */
    val cartelle: Map<String, String>,
    /** Foto spostate a mano su Drive da una cartella all'altra. */
    val spostatiSuDrive: Set<String>,
    val errori: Int,
    val primoErrore: String?,
)

/**
 * Sincronizzazione nei due sensi tra le foto dell'app e le due cartelle di Drive.
 * Regole: ciò che si aggiunge o si elimina da una parte si aggiunge o si elimina anche dall'altra;
 * le foto delle ecografie stanno nella cartella delle ecografie (se è stata scelta).
 */
object SincroniaDrive {
    private val DATA_NEL_NOME = Regex("(20\\d{2})[-_.]?(\\d{2})[-_.]?(\\d{2})")

    /** La data di una foto di Drive: quella salvata dall'app, poi quella di scatto, poi quella nel nome, poi il caricamento. */
    fun dataDi(f: FileDrive): LocalDate {
        prova { LocalDate.parse(f.propData) }?.let { return it }
        if (f.dataFoto.length >= 10) {
            prova { LocalDate.parse(f.dataFoto.substring(0, 10).replace(':', '-')) }?.let { return it }
        }
        DATA_NEL_NOME.find(f.nome)?.let { m ->
            prova { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
                ?.takeIf { !it.isAfter(LocalDate.now()) }
                ?.let { return it }
        }
        prova { Instant.parse(f.creato).atZone(ZoneId.systemDefault()).toLocalDate() }?.let { return it }
        return LocalDate.now()
    }

    fun creatoMs(f: FileDrive): Long = prova { Instant.parse(f.creato).toEpochMilli() } ?: System.currentTimeMillis()

    fun estensione(f: FileDrive): String {
        val dalNome = f.nome.substringAfterLast('.', "").lowercase()
        if (dalNome.length in 2..4) return dalNome
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(f.mime) ?: if (f.mime.startsWith("video")) "mp4" else "jpg"
    }

    private inline fun <T> prova(f: () -> T): T? = try { f() } catch (e: Exception) { null }

    private fun md5(f: File): String {
        val d = MessageDigest.getInstance("MD5")
        f.inputStream().use { i ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = i.read(buf)
                if (n <= 0) break
                d.update(buf, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    private fun mimeDi(f: File, video: Boolean): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase())
            ?: if (video) "video/mp4" else "image/jpeg"

    /** Nome del file su Drive: comincia con la data, così si ritrova anche senza l'app. */
    private fun nomeSuDrive(m: Media, ecografia: Ecografia?, f: File): String {
        val descr = when {
            ecografia != null -> ecografia.titolo
            m.didascalia.isNotBlank() -> m.didascalia
            m.video -> "Video"
            else -> "Foto"
        }.take(50).replace(Regex("[\\\\/:*?\"<>|\\n]"), " ").trim()
        return "${m.data} $descr ${m.id.take(4)}.${f.extension.ifEmpty { if (m.video) "mp4" else "jpg" }}"
    }

    /** Il lavoro lungo: cestino, elenchi, caricamenti, spostamenti. Bloccante. */
    fun esegui(
        ctx: Context,
        media: List<Media>,
        ecografie: List<Ecografia>,
        daCestinare: Set<String>,
        fotoId: String,
        ecoId: String?,
    ): EsitoSync {
        val dueCartelle = ecoId != null && ecoId != fotoId
        fun idCartella(c: String): String = if (c == "eco" && dueCartelle && ecoId != null) ecoId else fotoId
        fun cartellaVoluta(m: Media): String = if (m.ecografia != null && dueCartelle) "eco" else "foto"
        val ecoPerId = ecografie.associateBy { it.id }
        fun props(m: Media): Map<String, String?> = mapOf(
            "pancyData" to m.data.toString(),
            "pancyEcografia" to m.ecografia?.let { ecoPerId[it] }?.data?.toString(),
        )

        var errori = 0
        var primoErrore: String? = null
        fun errore(e: Exception) {
            if (e is ErroreDrive && e.serveConsenso) throw e
            errori++
            if (primoErrore == null) primoErrore = e.message
        }

        // 1. file eliminati nell'app: nel cestino di Drive
        val cestinati = HashSet<String>()
        for (id in daCestinare) {
            try {
                Drive.cestina(ctx, id)
                cestinati.add(id)
            } catch (e: ErroreDrive) {
                if (e.codice == 404) cestinati.add(id) else errore(e)
            } catch (e: IOException) {
                errore(e)
            }
        }

        // 2. cosa c'è su Drive
        val foto = Drive.mediaIn(ctx, fotoId).filter { it.id !in daCestinare }
        val eco = if (dueCartelle && ecoId != null) Drive.mediaIn(ctx, ecoId).filter { it.id !in daCestinare } else emptyList()
        val dove = HashMap<String, String>()
        for (f in foto) dove[f.id] = "foto"
        for (f in eco) dove[f.id] = "eco"
        val perId = (foto + eco).associateBy { it.id }
        val collegati = media.mapNotNull { it.driveId }.toHashSet()
        val perMd5 = HashMap<String, FileDrive>()
        for (f in foto + eco) if (f.md5.isNotEmpty() && f.id !in collegati && f.md5 !in perMd5) perMd5[f.md5] = f

        // 3. foto aggiunte nell'app: se su Drive c'è già lo stesso file lo collego, altrimenti lo carico
        val caricati = HashMap<String, Pair<String, String>>()
        val usati = HashSet<String>()
        for (m in media) {
            if (m.driveId != null) continue
            val f = Galleria.file(ctx, m)
            if (!f.exists() || f.length() == 0L) continue
            try {
                val voluta = cartellaVoluta(m)
                val uguale = perMd5[md5(f)]?.takeIf { it.id !in usati }
                if (uguale != null) {
                    usati.add(uguale.id)
                    caricati[m.id] = Pair(uguale.id, dove[uguale.id] ?: voluta)
                    Drive.aggiornaInfo(ctx, uguale.id, m.didascalia, props(m))
                } else {
                    val ecografia = m.ecografia?.let { ecoPerId[it] }
                    val id = Drive.carica(ctx, f, nomeSuDrive(m, ecografia, f), mimeDi(f, m.video), idCartella(voluta), m.didascalia, props(m))
                    caricati[m.id] = Pair(id, voluta)
                }
            } catch (e: ErroreDrive) {
                errore(e)
            } catch (e: IOException) {
                errore(e)
            }
        }

        // 4. foto già collegate: spostamenti e didascalie
        val cartelle = HashMap<String, String>()
        val spostatiSuDrive = HashSet<String>()
        for (m in media) {
            val d = m.driveId ?: continue
            val f = perId[d] ?: continue
            val attuale = dove[d] ?: continue
            try {
                val nota = m.cartellaDrive
                if (nota != null && nota != attuale) {
                    // spostata a mano su Drive: vince Drive
                    spostatiSuDrive.add(m.id)
                    cartelle[m.id] = attuale
                    continue
                }
                val voluta = cartellaVoluta(m)
                var finale = attuale
                if (voluta != attuale) {
                    // cambiata nell'app (per esempio ecografia eliminata): la sposto su Drive
                    Drive.sposta(ctx, d, idCartella(attuale), idCartella(voluta))
                    finale = voluta
                }
                cartelle[m.id] = finale
                val p = props(m)
                if (f.descrizione != m.didascalia || f.propData != p["pancyData"].orEmpty() || f.propEco != p["pancyEcografia"].orEmpty()) {
                    Drive.aggiornaInfo(ctx, d, m.didascalia, p)
                }
            } catch (e: ErroreDrive) {
                errore(e)
            } catch (e: IOException) {
                errore(e)
            }
        }
        return EsitoSync(caricati, cestinati, foto, eco, cartelle, spostatiSuDrive, errori, primoErrore)
    }
}
