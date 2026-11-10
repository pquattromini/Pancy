package com.pancy.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Un punto del grafico: [descrizione] è il testo che compare toccandolo. */
data class PuntoGrafico(val x: Float, val y: Float, val descrizione: String)

/** Dove sta l'area dei dati nel disegno (serve anche per capire quale punto si è toccato). */
private class AreaGrafico {
    var sinistra = 0f
    var destra = 1f
    var alto = 0f
    var basso = 1f
    var minX = 0f
    var maxX = 1f
    var minY = 0f
    var maxY = 1f
    fun xa(x: Float): Float = sinistra + (x - minX) / (maxX - minX) * (destra - sinistra)
    fun ya(y: Float): Float = basso - (y - minY) / (maxY - minY) * (basso - alto)
}

/** Tacche "tonde" che coprono da [min] a [max]: sceglie il passo più piccolo che ne dà al massimo [quante] intervalli. */
fun tacche(min: Float, max: Float, passi: List<Float>, quante: Int = 5): List<Float> {
    var lo = min
    var hi = max
    if (hi - lo < 1e-6f) {
        lo -= passi.first()
        hi += passi.first()
    }
    val passo = passi.firstOrNull { ceil(hi / it) - floor(lo / it) <= quante } ?: passi.last()
    val inizio = floor(lo / passo) * passo
    val fine = ceil(hi / passo) * passo
    val out = ArrayList<Float>()
    var v = inizio
    while (v <= fine + passo / 2) {
        out.add(v)
        v += passo
    }
    return out
}

/**
 * Grafico a linea di una sola serie (il titolo del riquadro dice cosa mostra, quindi niente legenda).
 * Toccando il grafico si vede il valore del punto più vicino; senza tocco è etichettato solo l'ultimo punto.
 */
@Composable
fun Grafico(
    punti: List<PuntoGrafico>,
    passiX: List<Float>,
    passiY: List<Float>,
    etichettaX: (Float) -> String,
    etichettaY: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val misuratore = rememberTextMeasurer()
    val stileAssi = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val stileValore = MaterialTheme.typography.labelMedium.copy(color = cs.onSurface, fontWeight = FontWeight.Bold)
    val stileTip = MaterialTheme.typography.labelMedium.copy(color = cs.inverseOnSurface, fontWeight = FontWeight.SemiBold)
    val ordinati = remember(punti) { punti.sortedBy { it.x } }
    var scelto by remember(punti) { mutableStateOf<Int?>(null) }
    val area = remember { AreaGrafico() }
    val coloreLinea = cs.primary
    val coloreGriglia = cs.outlineVariant
    val coloreBase = cs.outline
    val coloreSfondo = cs.surface
    val coloreTip = cs.inverseSurface

    Canvas(
        modifier.fillMaxWidth().height(220.dp).pointerInput(ordinati) {
            detectTapGestures { pos ->
                if (ordinati.isEmpty()) return@detectTapGestures
                var migliore = 0
                var distanza = Float.MAX_VALUE
                ordinati.forEachIndexed { i, p ->
                    val d = abs(area.xa(p.x) - pos.x)
                    if (d < distanza) {
                        distanza = d
                        migliore = i
                    }
                }
                scelto = if (scelto == migliore) null else migliore
            }
        },
    ) {
        if (ordinati.isEmpty()) return@Canvas
        val tX = tacche(ordinati.first().x, ordinati.last().x, passiX)
        val tY = tacche(ordinati.minOf { it.y }, ordinati.maxOf { it.y }, passiY, quante = 4)

        // spazio per le etichette degli assi
        val etY = tY.map { misuratore.measure(AnnotatedString(etichettaY(it)), stileAssi) }
        val etX = tX.map { misuratore.measure(AnnotatedString(etichettaX(it)), stileAssi) }
        val largY = etY.maxOf { it.size.width }.toFloat()
        val altX = etX.maxOf { it.size.height }.toFloat()
        val margine = 8.dp.toPx()
        area.sinistra = largY + margine
        area.destra = size.width - (etX.last().size.width / 2f).coerceAtLeast(margine)
        area.alto = margine * 2
        area.basso = size.height - altX - margine
        area.minX = tX.first()
        area.maxX = tX.last()
        area.minY = tY.first()
        area.maxY = tY.last()

        // griglia orizzontale ed etichette dell'asse verticale
        tY.forEachIndexed { i, v ->
            val y = area.ya(v)
            drawLine(
                if (i == 0) coloreBase else coloreGriglia,
                Offset(area.sinistra, y), Offset(area.destra, y), strokeWidth = 1.dp.toPx(),
            )
            val t = etY[i]
            drawText(t, topLeft = Offset(area.sinistra - margine - t.size.width, y - t.size.height / 2f))
        }
        // etichette dell'asse orizzontale
        tX.forEachIndexed { i, v ->
            val t = etX[i]
            drawText(t, topLeft = Offset(area.xa(v) - t.size.width / 2f, area.basso + margine / 2))
        }

        // linea
        val linea = Path()
        ordinati.forEachIndexed { i, p ->
            val o = Offset(area.xa(p.x), area.ya(p.y))
            if (i == 0) linea.moveTo(o.x, o.y) else linea.lineTo(o.x, o.y)
        }
        drawPath(linea, coloreLinea, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // punti, con un anello del colore dello sfondo
        val sel = scelto
        if (sel != null && sel < ordinati.size) {
            val x = area.xa(ordinati[sel].x)
            drawLine(coloreBase, Offset(x, area.alto), Offset(x, area.basso), strokeWidth = 1.dp.toPx())
        }
        ordinati.forEach { p ->
            val o = Offset(area.xa(p.x), area.ya(p.y))
            drawCircle(coloreSfondo, radius = 6.dp.toPx(), center = o)
            drawCircle(coloreLinea, radius = 4.dp.toPx(), center = o)
        }

        if (sel != null && sel < ordinati.size) {
            // fumetto con il valore del punto toccato
            val p = ordinati[sel]
            val t = misuratore.measure(AnnotatedString(p.descrizione), stileTip)
            val pad = 6.dp.toPx()
            val w = t.size.width + pad * 2
            val h = t.size.height + pad * 2
            val cx = area.xa(p.x)
            val cy = area.ya(p.y)
            val x = (cx - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            val y = if (cy - h - 10.dp.toPx() >= 0f) cy - h - 10.dp.toPx() else cy + 10.dp.toPx()
            drawRoundRect(coloreTip, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(pad, pad))
            drawText(t, topLeft = Offset(x + pad, y + pad))
        } else {
            // etichetta diretta solo sull'ultimo punto
            val p = ordinati.last()
            val t = misuratore.measure(AnnotatedString(etichettaY(p.y)), stileValore)
            val cx = area.xa(p.x)
            val cy = area.ya(p.y)
            val x = (cx - t.size.width / 2f).coerceIn(0f, (size.width - t.size.width).coerceAtLeast(0f))
            val y = (cy - t.size.height - 8.dp.toPx()).coerceAtLeast(0f)
            drawText(t, topLeft = Offset(x, y))
        }
    }
}
