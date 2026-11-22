package com.vortex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Number of log-spaced frequency bins between 20 Hz and 20 kHz. Real FFT data (stage 4) is resampled to this. */
const val BINS = 600

fun freqOfBin(i: Int): Double = 20.0 * 1000.0.pow(i / (BINS - 1).toDouble())

/**
 * DEMO DATA ONLY. This is a made-up signal so the screen can be tested.
 * Stage 4 replaces it with the real FFT from the audio input.
 */
class DemoSpectrum {
    val cd = FloatArray(BINS)
    val avg = FloatArray(BINS) { Float.NaN }
    var peaks: List<Int> = emptyList()
    private var lastPk = 0L

    /** Returns true when the peak list was recomputed. */
    fun step(tNanos: Long, maxPeaks: Int): Boolean {
        val t = tNanos / 1_000_000.0
        for (i in 0 until BINS) {
            val f = freqOfBin(i)
            val d = -38 - 20 * log10(1 + f / 250) +
                7 * exp(-ln(f / 76).pow(2) * 6) + 6 * exp(-ln(f / 250).pow(2) * 8) + 5 * exp(-ln(f / 2500).pow(2) * 10) +
                3 * sin(f / 300 + t / 400) + Random.nextFloat() * 3
            cd[i] = d.toFloat()
            avg[i] = if (avg[i].isNaN()) cd[i] else avg[i] * 0.92f + cd[i] * 0.08f
        }
        if (lastPk != 0L && tNanos - lastPk < 3_000_000_000L) return false
        lastPk = tNanos
        val w = 12
        val c = ArrayList<Int>()
        for (i in w until BINS - w) {
            var ok = true
            for (j in -w..w) if (j != 0 && avg[i + j] > avg[i]) { ok = false; break }
            if (ok) c.add(i)
        }
        c.sortByDescending { avg[it] }
        val out = ArrayList<Int>()
        for (i in c) if (out.size < maxPeaks && out.all { abs(it - i) > BINS * 0.06 }) out.add(i)
        out.sort()
        peaks = out
        return true
    }

    fun peakText(): String =
        "Peaks: " + peaks.joinToString(" · ") { "${freqOfBin(it).roundToInt()} Hz (${avg[it].roundToInt()} dB)" }
}

/** Spectroid-style analyzer: log frequency axis 20 Hz to 20 kHz, dB axis 0 to -100, current (yellow) and average (red). */
@Composable
fun SpectrumView(
    big: Boolean,
    showPeaks: Boolean,
    onPeakText: (String) -> Unit,
    onMarkerText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val sp = remember { DemoSpectrum() }
    var tick by remember { mutableLongStateOf(0L) }
    var mark by remember { mutableStateOf<Float?>(null) }
    val bigS by rememberUpdatedState(big)
    val showS by rememberUpdatedState(showPeaks)
    val peakCb by rememberUpdatedState(onPeakText)
    val markCb by rememberUpdatedState(onMarkerText)

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { t ->
                if (sp.step(t, if (bigS) 6 else 3)) peakCb(if (showS) sp.peakText() else "")
                tick = t
            }
        }
    }

    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { o ->
                val pl = 34.dp.toPx()
                val pw = size.width - pl - 6.dp.toPx()
                val frac = ((o.x - pl) / pw).coerceIn(0f, 1f)
                val idx = (frac * (BINS - 1)).roundToInt()
                mark = frac
                markCb("${freqOfBin(idx).roundToInt()} Hz, ${sp.cd[idx].roundToInt()} dB (demo)")
            }
        },
    ) {
        @Suppress("UNUSED_VARIABLE") val frame = tick // redraw every frame
        val pl = 34.dp.toPx()
        val pb = 14.dp.toPx()
        val pw = size.width - pl - 6.dp.toPx()
        val ph = size.height - pb
        val st = TextStyle(color = Color(0xFF777777), fontSize = if (big) 11.sp else 9.sp)
        fun xOf(f: Double) = pl + (ln(f / 20.0) / ln(1000.0)).toFloat() * pw
        fun yOf(db: Float) = -db / 100f * ph

        drawRect(Color.Black)
        // dB grid and labels (every 10 dB; labelled every 20 dB, every 10 dB when expanded)
        for (d in 0 downTo -100 step 10) {
            val y = yOf(d.toFloat())
            drawLine(if (d % 50 == 0) Color(0xFF3A3A3A) else Color(0xFF1C1C1C), Offset(pl, y), Offset(size.width, y), 1f)
            if (big || d % 20 == 0) {
                val r = tm.measure(d.toString(), st)
                drawText(r, topLeft = Offset(pl - 3.dp.toPx() - r.size.width, (y - r.size.height / 2f).coerceIn(0f, ph - r.size.height)))
            }
        }
        // frequency grid: a line at every 1-9 times each decade
        var m = 10.0
        while (m <= 10000.0) {
            for (i in 1..9) {
                val f = m * i
                if (f < 20 || f > 20000) continue
                drawLine(if (i == 1) Color(0xFF3A3A3A) else Color(0xFF1C1C1C), Offset(xOf(f), 0f), Offset(xOf(f), ph), 1f)
            }
            m *= 10
        }
        // frequency labels: decades first, then the rest if they fit
        val all = if (big) listOf(20, 30, 40, 50, 60, 70, 80, 90, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000,
            2000, 3000, 4000, 5000, 6000, 7000, 8000, 9000, 10000, 20000)
        else listOf(20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000)
        val pri = (listOf(20, 100, 1000, 10000, 20000, 50, 200, 500, 2000, 5000) + all).distinct().filter { it in all }
        val used = ArrayList<ClosedFloatingPointRange<Float>>()
        for (f in pri) {
            val r = tm.measure(if (f >= 1000) "${f / 1000}k" else "$f", st)
            val x = min(xOf(f.toDouble()), size.width - r.size.width / 2f - 1f)
            val l = x - r.size.width / 2f - 3.dp.toPx()
            val rr = x + r.size.width / 2f + 3.dp.toPx()
            if (used.none { rr >= it.start && l <= it.endInclusive }) {
                drawText(r, topLeft = Offset(x - r.size.width / 2f, size.height - r.size.height))
                used.add(l..rr)
            }
        }
        drawText(tm.measure("Hz", st), topLeft = Offset(2f, size.height - 12.dp.toPx()))
        drawText(tm.measure("dB", st), topLeft = Offset(2f, 0f))

        fun curve(arr: FloatArray, col: Color) {
            if (arr[0].isNaN()) return
            val p = Path()
            for (i in 0 until BINS) {
                val x = pl + i / (BINS - 1f) * pw
                if (i == 0) p.moveTo(x, yOf(arr[i])) else p.lineTo(x, yOf(arr[i]))
            }
            drawPath(p, col, style = Stroke(1.5f))
        }
        curve(sp.avg, Color(0xFFC0392B))
        curve(sp.cd, Color(0xFFE0A030))

        if (showPeaks) for (i in sp.peaks) {
            val x = pl + i / (BINS - 1f) * pw
            val y = yOf(sp.avg[i])
            val tri = Path().apply { moveTo(x, y - 2f); lineTo(x - 4f, y - 9f); lineTo(x + 4f, y - 9f); close() }
            drawPath(tri, Color(0xFF56B6F7))
            val r = tm.measure("${freqOfBin(i).roundToInt()} Hz", st.copy(color = Color(0xFF56B6F7)))
            val tx = (x - r.size.width / 2f).coerceIn(0f, size.width - r.size.width)
            drawText(r, topLeft = Offset(tx, (y - 12.dp.toPx() - r.size.height / 2f).coerceAtLeast(0f)))
        }
        mark?.let { fr ->
            val x = pl + fr * pw
            drawLine(Color(0xFF56B6F7), Offset(x, 0f), Offset(x, ph), 1f)
        }
    }
}
