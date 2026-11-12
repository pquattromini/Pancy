package com.pancy.app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale
import java.util.UUID

/** Durata di un vocale come "1:05". */
fun durataVocale(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:" + (s % 60).toString().padStart(2, '0')
}

/** Registra un messaggio vocale: AAC mono in un file .m4a nella cartella della chat. */
class Registratore(private val ctx: Context) {
    companion object {
        /** Al massimo mezz'ora: basta anche per registrare una visita (circa 7 MB). */
        const val MAX_MS = 30 * 60 * 1000
        private const val MIN_MS = 800L
    }

    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var inizio = 0L
    private var limite = false

    val attivo: Boolean get() = rec != null

    /** Inizia a registrare. [onLimite] arriva se si raggiunge la durata massima. Falso se il microfono non parte. */
    fun avvia(onLimite: () -> Unit): Boolean {
        if (rec != null) return true
        val f = File(Assistente.cartellaChat(ctx), "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(32_000)
            r.setMaxDuration(MAX_MS)
            r.setOutputFile(f.absolutePath)
            r.setOnInfoListener { _, cosa, _ ->
                if (cosa == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    limite = true
                    onLimite()
                }
            }
            r.prepare()
            r.start()
            rec = r
            file = f
            limite = false
            inizio = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            r.release()
            f.delete()
            false
        }
    }

    /** Da quanti millisecondi sta registrando. */
    fun durata(): Long = if (rec == null) 0 else (SystemClock.elapsedRealtime() - inizio).coerceAtMost(MAX_MS.toLong())

    /** Ferma la registrazione: file e durata, oppure null se è troppo corta o non è riuscita. */
    fun ferma(): Pair<File, Long>? {
        val r = rec ?: return null
        val f = file
        val d = durata()
        rec = null
        file = null
        val fermata = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            // se è finito il tempo massimo la registrazione è già chiusa e il file è buono
            limite
        }
        r.release()
        if (f == null) return null
        if (!fermata || d < MIN_MS || f.length() < 1000) {
            f.delete()
            return null
        }
        return Pair(f, d)
    }

    /** Butta via la registrazione in corso. */
    fun annulla() {
        val r = rec ?: return
        val f = file
        rec = null
        file = null
        try { r.stop() } catch (e: RuntimeException) { }
        r.release()
        f?.delete()
    }
}

/** Riascolta i messaggi vocali, uno per volta. */
object Riproduttore {
    /** Nome del file in riproduzione, se c'è. */
    var inCorso by mutableStateOf<String?>(null)
        private set
    private var mp: MediaPlayer? = null

    fun suona(f: File) {
        ferma()
        Voce.ferma()
        val p = MediaPlayer()
        try {
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener { ferma() }
            p.prepare()
            p.start()
            mp = p
            inCorso = f.name
        } catch (e: Exception) {
            p.release()
        }
    }

    fun ferma() {
        val p = mp ?: return
        mp = null
        inCorso = null
        try { p.stop() } catch (e: IllegalStateException) { }
        p.release()
    }
}

/** Legge ad alta voce le risposte dell'IA con la sintesi vocale del telefono (gratis, anche senza internet). */
object Voce {
    /** Chiave del messaggio che si sta leggendo, se c'è. */
    var inLettura by mutableStateOf<String?>(null)
        private set
    private var tts: TextToSpeech? = null
    private var pronta = false
    private var inSospeso: Pair<String, String>? = null
    private val principale = Handler(Looper.getMainLooper())

    private val ascolto = object : UtteranceProgressListener() {
        override fun onStart(id: String?) {}
        override fun onDone(id: String?) {
            if (id != null && id.endsWith("#fine")) principale.post { if (inLettura == id.substringBefore('#')) inLettura = null }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(id: String?) {
            principale.post { inLettura = null }
        }
    }

    /** Legge [testo]; [chiave] identifica il messaggio (per il pulsante "ferma"). */
    fun parla(ctx: Context, chiave: String, testo: String) {
        Riproduttore.ferma()
        val t = tts
        if (t == null) {
            inSospeso = Pair(chiave, testo)
            inLettura = chiave
            tts = TextToSpeech(ctx.applicationContext) { stato ->
                if (stato == TextToSpeech.SUCCESS) {
                    pronta = true
                    tts?.setLanguage(Locale.ITALIAN)
                    tts?.setOnUtteranceProgressListener(ascolto)
                    inSospeso?.let { (k, s) -> leggi(k, s) }
                } else {
                    // nessuna sintesi vocale sul telefono
                    tts = null
                    inLettura = null
                }
                inSospeso = null
            }
            return
        }
        if (!pronta) {
            inSospeso = Pair(chiave, testo)
            inLettura = chiave
            return
        }
        leggi(chiave, testo)
    }

    private fun leggi(chiave: String, testo: String) {
        val t = tts ?: return
        val pezzi = dividi(perVoce(testo), 3000)
        if (pezzi.isEmpty()) {
            inLettura = null
            return
        }
        pezzi.forEachIndexed { i, p ->
            val id = if (i == pezzi.size - 1) "$chiave#fine" else "$chiave#$i"
            t.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
        }
        inLettura = chiave
    }

    fun ferma() {
        inSospeso = null
        inLettura = null
        tts?.stop()
    }

    /** Toglie i segni del Markdown che la voce leggerebbe ("**", "#", trattini degli elenchi). */
    fun perVoce(s: String): String = s.lines().joinToString("\n") { r ->
        var t = r.trim().replace("**", "")
        t = t.trimStart('#').trim()
        if (t.startsWith("- ") || t.startsWith("* ") || t.startsWith("• ")) t = t.substring(2)
        t
    }.trim()

    /** Divide il testo in pezzi non più lunghi di [max] caratteri, tagliando tra i paragrafi o le frasi. */
    fun dividi(s: String, max: Int): List<String> {
        val pezzi = ArrayList<String>()
        var resto = s.trim()
        while (resto.length > max) {
            var taglio = resto.lastIndexOf('\n', max)
            if (taglio < max / 2) taglio = resto.lastIndexOf(". ", max).let { if (it > 0) it + 1 else -1 }
            if (taglio < max / 2) taglio = resto.lastIndexOf(' ', max)
            if (taglio <= 0) taglio = max
            pezzi.add(resto.substring(0, taglio).trim())
            resto = resto.substring(taglio).trim()
        }
        if (resto.isNotEmpty()) pezzi.add(resto)
        return pezzi
    }
}
