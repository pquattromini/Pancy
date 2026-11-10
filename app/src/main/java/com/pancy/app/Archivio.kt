package com.pancy.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Ogni sezione salva i suoi dati in un file JSON nella memoria dell'app, nel formato
 * { "versione": 1, "<chiave>": [ ... ] }.
 * Il formato è semplice apposta: più avanti potrà leggerlo anche l'assistente con l'IA.
 */
object Archivio {
    fun <T> carica(ctx: Context, nome: String, chiave: String, leggi: (JSONObject) -> T): List<T> {
        val f = File(ctx.filesDir, nome)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONObject(f.readText()).optJSONArray(chiave) ?: JSONArray()
            val out = ArrayList<T>()
            for (i in 0 until arr.length()) {
                try {
                    out.add(leggi(arr.getJSONObject(i)))
                } catch (e: Exception) {
                    // voce non leggibile: la salto
                }
            }
            out
        } catch (e: Exception) {
            // file rovinato: ne tengo una copia, così non si perde nulla al prossimo salvataggio
            try {
                f.copyTo(File(ctx.filesDir, nome.removeSuffix(".json") + "-illeggibile.json"), overwrite = true)
            } catch (x: Exception) {
            }
            emptyList()
        }
    }

    fun <T> salva(ctx: Context, nome: String, chiave: String, lista: List<T>, scrivi: (T) -> JSONObject) {
        val arr = JSONArray()
        for (x in lista) arr.put(scrivi(x))
        val testo = JSONObject().put("versione", 1).put(chiave, arr).toString(2)
        // si scrive prima su un file temporaneo e poi lo si rinomina: se il telefono si spegne a metà, il file buono resta
        val tmp = File(ctx.filesDir, "$nome.tmp")
        val f = File(ctx.filesDir, nome)
        tmp.writeText(testo)
        if (!tmp.renameTo(f)) {
            f.writeText(testo)
            tmp.delete()
        }
    }
}
