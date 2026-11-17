package com.pancy.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un backup letto (da Drive o da un file), con il momento in cui è stato salvato e cosa contiene. */
class InfoBackup(val salvato: Long, val descrizione: String, val dati: JSONObject, val daFile: Boolean)

/**
 * Il backup dei dati: tutti i file JSON delle sezioni e le impostazioni, in un unico JSON.
 * Va su Google Drive (vedi [Drive.trovaBackup]) o in un file scelto dall'utente.
 * Non contiene la chiave di OpenAI né i file di foto, video e PDF (quelli su Drive tornano dalle loro cartelle).
 */
object Backup {
    const val VERSIONE = 1
    const val NOME_SU_DRIVE = "Pancy - backup dei dati (non cancellare).json"
    const val MIME = "application/json"

    /** I file dei dati: nome del file e chiave dell'elenco al suo interno. */
    val FILE = listOf(
        "appuntamenti.json" to "appuntamenti",
        "ecografie.json" to "ecografie",
        "referti.json" to "referti",
        "pesi.json" to "pesi",
        "consigli.json" to "consigli",
        "conversazioni.json" to "conversazioni",
        "media.json" to "media",
    )

    /** Preferenze che riguardano solo questo telefono: non vanno nel backup e non si sovrascrivono. */
    val PREF_LOCALI = setOf("driveCollegato", "driveUltima", "backupNoto", "backupUltimo", "ultimaModifica")

    /** Nomi da mostrare per i conteggi. */
    val NOMI = mapOf(
        "pesi" to "pesate",
        "appuntamenti" to "appuntamenti",
        "ecografie" to "ecografie",
        "referti" to "referti",
        "consigli" to "consigli",
        "conversazioni" to "conversazioni con l'IA",
        "media" to "foto, video e PDF su Drive",
    )

    private fun elenco(dir: File, nome: String, chiave: String): JSONArray {
        val f = File(dir, nome)
        if (!f.exists()) return JSONArray()
        return try {
            JSONObject(f.readText()).optJSONArray(chiave) ?: JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }
    }

    /**
     * Compone il backup con i file in [dir] e le preferenze [prefs]. Dei file di foto, video e PDF tiene solo
     * quelli che sono su Drive: gli altri, dopo una reinstallazione, non ci sarebbero più.
     */
    fun componi(dir: File, prefs: Map<String, *>, salvato: Long, versioneApp: String): JSONObject {
        val dati = JSONObject()
        for ((nome, chiave) in FILE) {
            val arr = elenco(dir, nome, chiave)
            if (chiave == "media") {
                val suDrive = JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optString("driveId").isNotEmpty()) suDrive.put(o)
                }
                dati.put(chiave, suDrive)
            } else {
                dati.put(chiave, arr)
            }
        }
        val imp = JSONObject()
        for ((k, v) in prefs) {
            if (k in PREF_LOCALI || v == null) continue
            val voce = when (v) {
                is String -> JSONObject().put("t", "s").put("v", v)
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray().also { a -> for (x in v) if (x is String) a.put(x) })
                else -> null
            } ?: continue
            imp.put(k, voce)
        }
        return JSONObject()
            .put("pancyBackup", VERSIONE)
            .put("salvato", salvato)
            .put("app", versioneApp)
            .put("dati", dati)
            .put("impostazioni", imp)
    }

    /** Legge un backup; eccezione se il testo non è un backup di Pancy. */
    fun leggi(testo: String): JSONObject {
        val b = try { JSONObject(testo) } catch (e: Exception) { throw IllegalArgumentException("Il file non è un backup di Pancy.") }
        if (b.optInt("pancyBackup") < 1 || b.optJSONObject("dati") == null) throw IllegalArgumentException("Il file non è un backup di Pancy.")
        if (b.optInt("pancyBackup") > VERSIONE) throw IllegalArgumentException("Il backup viene da una versione più nuova dell'app: aggiorna Pancy.")
        return b
    }

    fun salvato(b: JSONObject): Long = b.optLong("salvato")

    /** Quanti elementi ci sono per sezione (solo quelle non vuote). */
    fun conteggi(b: JSONObject): Map<String, Int> {
        val dati = b.optJSONObject("dati") ?: return emptyMap()
        val out = LinkedHashMap<String, Int>()
        for (k in NOMI.keys) {
            val n = dati.optJSONArray(k)?.length() ?: 0
            if (n > 0) out[k] = n
        }
        return out
    }

    /** "34 pesate, 12 appuntamenti, 3 ecografie" */
    fun descrivi(b: JSONObject): String =
        conteggi(b).entries.joinToString(", ") { (k, n) -> "$n ${NOMI[k]}" }.ifEmpty { "nessun dato" }

    /**
     * Scrive in [dir] i dati del backup, unendoli a quelli già presenti: per ogni sezione vince il backup,
     * e si aggiungono gli elementi del telefono che il backup non ha (per esempio un peso inserito dopo la reinstallazione).
     */
    fun ripristinaFile(dir: File, b: JSONObject) {
        val dati = b.optJSONObject("dati") ?: return
        for ((nome, chiave) in FILE) {
            val dalBackup = dati.optJSONArray(chiave) ?: JSONArray()
            val locali = elenco(dir, nome, chiave)
            val ids = HashSet<String>()
            // lo stesso file di Drive o lo stesso evento del calendario, arrivati di nuovo sul telefono con un altro id
            val doppioni = HashSet<String>()
            val campoDoppione = when (chiave) {
                "media" -> "driveId"
                "appuntamenti" -> "esterno"
                else -> null
            }
            val unito = JSONArray()
            for (i in 0 until dalBackup.length()) {
                val o = dalBackup.optJSONObject(i) ?: continue
                ids.add(o.optString("id"))
                if (campoDoppione != null) o.optString(campoDoppione).takeIf { it.isNotEmpty() }?.let { doppioni.add(it) }
                unito.put(o)
            }
            for (i in 0 until locali.length()) {
                val o = locali.optJSONObject(i) ?: continue
                if (o.optString("id") in ids) continue
                if (campoDoppione != null && o.optString(campoDoppione).let { it.isNotEmpty() && it in doppioni }) continue
                unito.put(o)
            }
            val testo = JSONObject().put("versione", 1).put(chiave, unito).toString(2)
            val tmp = File(dir, "$nome.tmp")
            val f = File(dir, nome)
            tmp.writeText(testo)
            if (!tmp.renameTo(f)) {
                f.writeText(testo)
                tmp.delete()
            }
        }
    }

    /** Le impostazioni del backup, già con il loro tipo, da scrivere nelle preferenze. */
    fun impostazioni(b: JSONObject): List<Pair<String, Any>> {
        val imp = b.optJSONObject("impostazioni") ?: return emptyList()
        val out = ArrayList<Pair<String, Any>>()
        for (k in imp.keys()) {
            if (k in PREF_LOCALI) continue
            val voce = imp.optJSONObject(k) ?: continue
            val v: Any = when (voce.optString("t")) {
                "s" -> voce.optString("v")
                "b" -> voce.optBoolean("v")
                "i" -> voce.optLong("v").toInt()
                "l" -> voce.optLong("v")
                "f" -> voce.optDouble("v").toFloat()
                "set" -> {
                    val a = voce.optJSONArray("v") ?: JSONArray()
                    (0 until a.length()).map { a.optString(it) }.toSet()
                }
                else -> null
            } ?: continue
            out.add(k to v)
        }
        return out
    }
}
