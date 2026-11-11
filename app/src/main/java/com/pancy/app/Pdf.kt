package com.pancy.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.max

/**
 * Le pagine dei PDF disegnate come immagini, con il PdfRenderer di Android.
 * Il PdfRenderer apre una pagina per volta: tutte le chiamate passano da un unico blocco. Bloccanti.
 */
object Pdf {
    private val blocco = Any()

    /** Il lato lungo di una pagina non supera questi pixel, per non esaurire la memoria. */
    private const val MAX_LATO = 4096

    /** Quante pagine ha il PDF; 0 se non si apre (rovinato o protetto da password). */
    fun pagine(f: File): Int = synchronized(blocco) {
        try {
            apri(f) { it.pageCount }
        } catch (e: Exception) {
            0
        }
    }

    /** La pagina [i] (da 0) larga circa [larghezza] pixel, su sfondo bianco. Null se non si riesce. */
    fun pagina(f: File, i: Int, larghezza: Int): Bitmap? = synchronized(blocco) {
        try {
            apri(f) { r ->
                if (i < 0 || i >= r.pageCount) {
                    null
                } else {
                    val p = r.openPage(i)
                    try {
                        var w = larghezza.coerceAtLeast(1)
                        var h = (p.height.toLong() * w / p.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                        val lungo = max(w, h)
                        if (lungo > MAX_LATO) {
                            val k = MAX_LATO.toFloat() / lungo
                            w = (w * k).toInt().coerceAtLeast(1)
                            h = (h * k).toInt().coerceAtLeast(1)
                        }
                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(Color.WHITE)
                        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        b
                    } finally {
                        p.close()
                    }
                }
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun <T> apri(f: File, usa: (PdfRenderer) -> T): T {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = try {
            PdfRenderer(pfd)
        } catch (e: Exception) {
            pfd.close()
            throw e
        }
        // chiudendo il PdfRenderer si chiude anche il file
        try {
            return usa(r)
        } finally {
            r.close()
        }
    }
}
