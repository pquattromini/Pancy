package com.pancy.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Foto e video dell'app: stanno nella cartella "media" della memoria dell'app.
 * Si copiano lì (e non si legge dalla galleria) così restano anche se nella galleria vengono cancellati.
 */
object Galleria {
    fun cartella(ctx: Context): File = File(ctx.filesDir, "media").also { it.mkdirs() }

    fun file(ctx: Context, m: Media): File = File(cartella(ctx), m.file)

    fun cartellaMiniature(ctx: Context): File = File(ctx.filesDir, "miniature").also { it.mkdirs() }

    /** Il file sul telefono; se la foto sta solo su Google Drive la scarica prima. Bloccante. Null se non ci riesce. */
    fun fileLocale(ctx: Context, m: Media): File? {
        val f = file(ctx, m)
        if (f.exists() && f.length() > 0) return f
        val id = m.driveId ?: return null
        return try {
            Drive.scarica(ctx, id, f)
            f
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Miniatura per le griglie. Per le foto di Drive non ancora scaricate usa l'anteprima di Drive
     * (salvata nella cartella "miniature"), così non serve scaricare tutto. Bloccante.
     */
    fun miniaturaDi(ctx: Context, m: Media, lato: Int = 360): Bitmap? {
        val f = file(ctx, m)
        if (f.exists() && f.length() > 0) return miniatura(f, m.video, lato)
        val id = m.driveId ?: return null
        val mini = File(cartellaMiniature(ctx), "$id.jpg")
        if (!mini.exists()) {
            val ok = try { Drive.miniatura(ctx, id, mini) } catch (e: Exception) { false }
            if (!ok) {
                // niente anteprima: per le foto scarico il file intero, per i video rinuncio
                if (m.video) return null
                val intero = fileLocale(ctx, m) ?: return null
                return miniatura(intero, false, lato)
            }
        }
        return miniatura(mini, false, lato)
    }

    fun nuovoFile(ctx: Context, estensione: String): File =
        File(cartella(ctx), "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}.$estensione")

    /** Indirizzo del file da passare ad altre app (fotocamera, condivisione). */
    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    /** Copia nell'app una foto o un video scelto dalla galleria. Null se non ci riesce. */
    fun copia(ctx: Context, uri: Uri, ecografia: String?): Media? {
        val cr = ctx.contentResolver
        val mime = cr.getType(uri) ?: ""
        val video = mime.startsWith("video")
        val est = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (video) "mp4" else "jpg"
        val dest = nuovoFile(ctx, est)
        return try {
            val input = cr.openInputStream(uri) ?: return null
            input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
            Media(file = dest.name, video = video, data = dataScatto(dest, video) ?: LocalDate.now(), ecografia = ecografia)
        } catch (e: Exception) {
            dest.delete()
            null
        }
    }

    /** Il giorno in cui è stata scattata la foto o girato il video, se il file lo dice. */
    private fun dataScatto(f: File, video: Boolean): LocalDate? = try {
        val testo = if (video) {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(f.path)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            } finally {
                try { r.release() } catch (e: Exception) { }
            }
        } else {
            val exif = ExifInterface(f.path)
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        }
        leggiDataMeta(testo)
    } catch (e: Exception) {
        null
    }

    /** "2026:11:09 10:30:00" (foto) oppure "20261109T103000.000Z" (video). */
    private fun leggiDataMeta(s: String?): LocalDate? {
        if (s == null) return null
        val cifre = s.filter { it.isDigit() }
        if (cifre.length < 8) return null
        val d = try {
            LocalDate.of(cifre.substring(0, 4).toInt(), cifre.substring(4, 6).toInt(), cifre.substring(6, 8).toInt())
        } catch (e: Exception) {
            return null
        }
        // alcuni video hanno date finte (1904, 1970...): le ignoro
        return if (d.year < 2000 || d.isAfter(LocalDate.now())) null else d
    }

    /** Foto ridotta perché il lato più lungo stia intorno a [maxLato] pixel, girata nel verso giusto. */
    fun foto(f: File, maxLato: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= maxLato) s *= 2
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        return ruota(b, orientamento(f))
    }

    /** Un fotogramma del video (a 1 secondo, o il primo). */
    fun fotogramma(f: File): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            r.getFrameAtTime(1_000_000) ?: r.getFrameAtTime()
        } catch (e: Exception) {
            null
        } finally {
            try { r.release() } catch (e: Exception) { }
        }
    }

    /** Miniatura quadrata per le griglie: il lato più corto misura circa [lato] pixel. */
    fun miniatura(f: File, video: Boolean, lato: Int = 360): Bitmap? = try {
        val b = if (video) {
            fotogramma(f)
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) {
                null
            } else {
                var s = 1
                while (min(o.outWidth, o.outHeight) / (s * 2) >= lato) s *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
                    ?.let { ruota(it, orientamento(f)) }
            }
        }
        b?.let { riduci(it, lato) }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun riduci(b: Bitmap, lato: Int): Bitmap {
        val corto = min(b.width, b.height)
        if (corto <= lato) return b
        val k = lato.toFloat() / corto
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true)
    }

    private fun orientamento(f: File): Int =
        try {
            ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun ruota(b: Bitmap, orientamento: Int): Bitmap {
        val gradi = when (orientamento) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return b
        }
        val mx = Matrix().apply { postRotate(gradi) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, mx, true)
    }

    /** Miniature già pronte, in memoria (al massimo 1/8 della memoria dell'app). */
    val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Apre la scelta dell'app con cui condividere (WhatsApp, email...). */
    fun condividi(ctx: Context, m: Media) {
        val i = Intent(Intent.ACTION_SEND)
            .setType(if (m.video) "video/*" else "image/*")
            .putExtra(Intent.EXTRA_STREAM, uri(ctx, file(ctx, m)))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Condividi"))
    }

    fun elimina(ctx: Context, m: Media) {
        file(ctx, m).delete()
        val id = m.driveId
        if (id != null) File(cartellaMiniature(ctx), "$id.jpg").delete()
        cache.remove(m.file)
    }
}

/** Miniatura quadrata di una foto o di un video (con il simbolo "play"). */
@Composable
fun Miniatura(med: Media, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val bmp by produceState<Bitmap?>(Galleria.cache.get(med.file), med.file) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                Galleria.miniaturaDi(ctx, med)?.also { Galleria.cache.put(med.file, it) }
            }
        }
    }
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(cs.surfaceVariant).clickable(onClick = onClick),
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(
                img, contentDescription = med.didascalia.ifBlank { null },
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        }
        if (med.video) {
            Icon(
                Icons.Filled.PlayArrow, contentDescription = "Video", tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(34.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(5.dp),
            )
        }
    }
}
